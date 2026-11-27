package li.gkd.app.record

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.RuleScopePolicy
import li.gkd.app.rule.RuleSummary

/**
 * v8.1.0：规则过滤诊断记录器（补齐「广告为什么没跳过」决策链的过滤盲区）。
 *
 * v8.0.8 及以前的缺口：规则在进入评估前就被静默过滤——
 * · 组被停用（用户开关/类别默认关） → RuleSummaryBuilder 不产出，日志无痕；
 * · 订阅规则不兼容（编译/类型校验失败） → 组 invalid 同样不可见；
 * · App 版本不满足 versionCode/versionName 门槛 → AppRule.enable=false 不可见；
 * · Activity 不匹配（activityIds 不含当前页） → ForegroundRuleSnapshot 过滤后不可见。
 * 以上场景用户只看到「广告没跳过」，日志里却连 match_miss 都没有（规则根本没参与评估）。
 *
 * 采集点：AndroidForegroundState.applyTopActivity（前台应用/页面切换或规则快照更新）。
 * 节流：同一 (appId, activityId, 快照) 不重复记录 + 每应用 10 分钟一条（EngineRateGate）。
 * 纯内存计算 + 非阻塞写入，绝不影响匹配主链路。
 */
object EngineRuleFilterRecorder {
    private const val RATE_MS = 10 * 60_000L
    private const val GROUP_NAME_LIMIT = 3

    private val json = Json { encodeDefaults = false }

    /** 测试注入点（生产恒为 EngineEventLogWriter.log）。 */
    @Volatile
    internal var sink: (kind: String, message: String, level: Int, appId: String?, detail: String?) -> Unit =
        { kind, message, level, appId, detail ->
            EngineEventLogWriter.log(kind, message, level, appId, null, detail)
        }

    private var lastKey = ""

    /**
     * 前台快照变化时调用。仅当应用/页面/规则快照变化时计算一次差集；
     * 差集为空（所有规则正常参与评估）时不产生任何日志。
     */
    fun onSnapshot(
        appId: String,
        activityId: String?,
        ruleSummary: RuleSummary,
        appInfo: AppInfo?,
    ) {
        val allGroups = ruleSummary.appIdToAllGroups[appId].orEmpty()
        if (allGroups.isEmpty()) return // 无任何规则组的 App 由 app_no_rules 通道负责
        val snapshotKey = "$appId|$activityId|${System.identityHashCode(ruleSummary)}"
        if (snapshotKey == lastKey) return
        lastKey = snapshotKey
        if (!EngineRateGate.tryAcquire("rule_filtered:$appId", RATE_MS)) return

        val incompatibleGroups = mutableListOf<String>()
        val disabledGroups = mutableListOf<String>()
        allGroups.forEach { g ->
            if (!g.group.valid) {
                incompatibleGroups.add(g.group.name) // 类4：订阅规则不兼容（编译/类型校验失败）
            } else if (!g.enable) {
                disabledGroups.add(g.group.name) // 类2：规则未启用（用户开关/类别默认关）
            }
        }
        // 类4 细分：启用组内因 App 版本不满足 versionCode/versionName 门槛而不参与的规则
        var versionFilteredRules = 0
        allGroups
            .filter { it.group.valid && it.enable }
            .forEach { g ->
                versionFilteredRules +=
                    g.group.rules.count { !RuleScopePolicy.appVersionMatches(g.group, it, appInfo) }
            }
        // 类5：当前 Activity 不匹配而被过滤的规则数（appIdToRules 已只含启用+版本通过的规则）
        val activeRules = ruleSummary.appIdToRules[appId].orEmpty()
        val activityFiltered =
            if (activityId == null) 0
            else activeRules.count { !it.matchActivity(appId, activityId) }

        if (incompatibleGroups.isEmpty() && disabledGroups.isEmpty() &&
            versionFilteredRules == 0 && activityFiltered == 0
        ) return
        val segments = mutableListOf<String>()
        if (incompatibleGroups.isNotEmpty()) {
            segments.add(
                "不兼容组[${incompatibleGroups.take(GROUP_NAME_LIMIT).joinToString("/")}" +
                    (if (incompatibleGroups.size > GROUP_NAME_LIMIT) " 等${incompatibleGroups.size}组" else "") + "]",
            )
        }
        if (disabledGroups.isNotEmpty()) {
            segments.add(
                "已停用组[${disabledGroups.take(GROUP_NAME_LIMIT).joinToString("/")}" +
                    (if (disabledGroups.size > GROUP_NAME_LIMIT) " 等${disabledGroups.size}组" else "") + "]",
            )
        }
        if (versionFilteredRules > 0) segments.add("$versionFilteredRules 条规则因App版本不满足而跳过")
        if (activityFiltered > 0) {
            val act = (activityId ?: "").substringAfterLast('.').take(40).ifEmpty { "?" }
            segments.add("当前页面($act)不适用规则 $activityFiltered 条")
        }
        sink(
            "rule_filtered",
            "🧱 规则未参与评估（$appId）：${segments.joinToString(" · ")} ——该App部分广告场景可能因此未被处理，上传日志可帮我们定位",
            0,
            appId,
            json.encodeToString(
                RuleFilterDetail.serializer(),
                RuleFilterDetail(
                    incompatibleGroups = incompatibleGroups,
                    disabledGroups = disabledGroups,
                    versionFilteredRules = versionFilteredRules,
                    activityFilteredRules = activityFiltered,
                ),
            ),
        )
    }
}

@Serializable
private data class RuleFilterDetail(
    val incompatibleGroups: List<String>,
    val disabledGroups: List<String>,
    val versionFilteredRules: Int,
    val activityFilteredRules: Int,
)
