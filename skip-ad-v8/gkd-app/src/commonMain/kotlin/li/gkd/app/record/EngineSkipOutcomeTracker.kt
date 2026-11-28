package li.gkd.app.record

import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.rule.ResolvedRule

/**
 * v8.1.1 事件关联 ID（一次规则触发贯穿匹配→动作→结果确认）。
 *
 * 进程内单调递增 + 时间戳后缀：同一行报告内可直接用 eventId 串联
 * action_performed ↔ splash_dismissed / result_unknown / action_no_effect，
 * 服务端不再需要靠时间戳猜两行日志是否同一次触发。
 */
object EngineEventIds {
    private val seq = AtomicLong(0)

    fun next(now: Long = System.currentTimeMillis()): String {
        val n = seq.incrementAndGet()
        return "E${n.toString(36)}-${(now and 0xFFFFFF).toString(36)}"
    }
}

/**
 * v8.1.1 跳过结果状态机——「已跳过却记为未命中」根治。
 *
 * 事故（v8.1.0 及以前）：广告已命中、动作已执行、广告已消失后，应用自身
 * 内容页的后续 not_matched 评估轮次会开启【新的】miss 窗口（8 秒聚合），
 * 上传报告对该应用同时出现 action_performed 与 match_miss——服务端与用户
 * 读到的最终结论是「未命中」，与事实相反。
 *
 * 本跟踪器补齐三件事：
 * 1. eventId 关联：动作成功行与结果确认行共用同一 eventId；
 * 2. 结果证据（观察窗 8s）：
 *    · 同规则再次评估且未命中（targetGone）→ splash_dismissed；
 *    · 同应用观察到不同 Activity（pageSwitched）→ splash_dismissed；
 *    · 同规则再次命中（rematched）→ 不落结果行（action_no_effect 链负责）；
 *    · 无任何后续证据 → result_unknown（诚实状态，不猜）；
 * 3. 抑制闸：动作成功后 12s 内该应用的 miss 窗口一律不开——
 *    「已经匹配并执行动作的事件绝不回写为 match_miss」。
 *
 * 热路径成本：无近期成功动作时 onRuleEvaluated O(1) 早退（entries.isEmpty）；
 * 全程纯内存、单有界表（32 条），绝不阻塞匹配主链。
 */
object EngineSkipOutcomeTracker {
    /** 结果观察窗：与 EngineActionEffectTracker 对齐。 */
    internal const val OUTCOME_WINDOW_MS = 8_000L

    /** miss 抑制窗：跳过成功后该时间内，同应用不产生新的 match_miss 窗口。 */
    internal const val MISS_SUPPRESS_MS = 12_000L

    private const val MAX_TRACKED = 32

    private val json = Json { encodeDefaults = false }

    /** 测试注入点（生产恒为 EngineEventLogWriter.log）。 */
    @Volatile
    internal var sink: (kind: String, message: String, level: Int, appId: String?, detail: String?) -> Unit =
        { kind, message, level, appId, detail ->
            EngineEventLogWriter.log(kind, message, level, appId, null, detail)
        }

    private class Entry {
        var eventId = ""
        var appId = ""
        var activityId: String? = null
        var groupName = ""
        var ruleName = ""
        var successAt = 0L
        var targetGone = false
        var pageSwitched = false
        var rematched = false
        var emitted = false
    }

    private val entries = LinkedHashMap<Any, Entry>()
    private val lastSkipAt = HashMap<String, Long>()

    /**
     * completed 回调接线：动作成功（API 返回成功）锚点。
     * 注意：action_succeeded ≠ splash_dismissed——最终状态由观察窗内的证据决定。
     */
    fun onActionSuccess(
        eventId: String,
        rule: ResolvedRule,
        appId: String,
        activityId: String?,
        now: Long = System.currentTimeMillis(),
    ) {
        settleExpired(now)
        synchronized(this) {
            if (entries.size >= MAX_TRACKED) {
                val eldest = entries.keys.firstOrNull() ?: return
                emitLocked(entries.remove(eldest)!!, now)
            }
            entries[rule.executionIdentity] =
                Entry().apply {
                    this.eventId = eventId
                    this.appId = appId
                    this.activityId = activityId
                    this.groupName = rule.g.group.name
                    this.ruleName = rule.rule.name ?: ""
                    this.successAt = now
                }
            lastSkipAt[appId] = now
        }
    }

    /**
     * observed 回调接线（result==null 的评估轮次）：证据采集。
     * 无在途条目时 O(1) 早退——正常浏览（无近期跳过）零开销。
     *
     * 证据语义（严格）：
     * · not_matched → 选择器真实跑过且未命中目标 → targetGone；
     * · Cooldown/ActionLimitReached → 状态门拦截（选择器未跑）→ 不是任何证据；
     * · node_unavailable/foreground_conflict → 歧义 → 不是任何证据；
     * · matched（预览/未执行）→ 目标仍在 → rematched。
     * 页面切换证据：同应用任何规则观察到不同 Activity。
     */
    fun onRuleEvaluated(
        rule: ResolvedRule,
        appId: String,
        activityId: String?,
        status: String,
        matched: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        if (entries.isEmpty()) return
        settleExpired(now)
        synchronized(this) {
            if (activityId != null) {
                for (e in entries.values) {
                    if (e.appId == appId && e.activityId != null && activityId != e.activityId) {
                        e.pageSwitched = true
                    }
                }
            }
            val e = entries[rule.executionIdentity] ?: return
            if (e.appId != appId) return
            if (matched) {
                e.rematched = true
            } else if (status == "not_matched") {
                e.targetGone = true
            }
        }
    }

    /**
     * miss 抑制闸：该应用在跳过成功后的抑制窗内返回 true。
     * EngineMatchMissRecorder 据此丢弃「跳过后内容页」的未命中轮次。
     */
    fun suppressMiss(appId: String, now: Long = System.currentTimeMillis()): Boolean {
        val at = synchronized(this) { lastSkipAt[appId] } ?: return false
        return now - at < MISS_SUPPRESS_MS
    }

    /**
     * v8.1.1 最近一次跳过快照（意见反馈页自动附带的上下文）：
     * `包名 · 3.2s前 · 已确认广告消失(目标消失)` 或 `包名 · 5.0s前 · 结果未确认`。
     */
    fun lastSkipSnapshot(now: Long = System.currentTimeMillis()): String? =
        synchronized(this) {
            if (lastSkipAt.isEmpty()) return null
            val appId =
                lastSkipAt.maxByOrNull { it.value }?.key ?: return null
            val at = lastSkipAt[appId] ?: return null
            val entry =
                entries.values
                    .filter { it.appId == appId }
                    .maxByOrNull { it.successAt }
            val outcome =
                when {
                    entry == null -> "动作已执行"
                    entry.rematched -> "点击后广告疑似仍在"
                    entry.targetGone && entry.pageSwitched -> "已确认广告消失(目标消失+页面切换)"
                    entry.targetGone -> "已确认广告消失(目标消失)"
                    entry.pageSwitched -> "已确认广告消失(页面切换)"
                    else -> "结果未确认"
                }
            "$appId · ${(now - at) / 1000}s前 · $outcome"
        }

    /** v8.1.1 最近一次跳过的应用包名（开屏提示「反馈」按钮预填用）。 */
    fun lastSkipAppId(): String? =
        synchronized(this) { lastSkipAt.maxByOrNull { it.value }?.key }

    /** 上传前/报告构建前：冲刷所有在途结果（drain 屏障的一部分——状态一致后才能导出）。 */
    fun settleAll(now: Long = System.currentTimeMillis()) {
        settleExpired(now, force = true)
    }

    private fun settleExpired(now: Long, force: Boolean = false) {
        if (entries.isEmpty()) return
        val expired = ArrayList<Entry>()
        synchronized(this) {
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val e = iterator.next().value
                val age = now - e.successAt
                if (age >= OUTCOME_WINDOW_MS || (force && age >= 0)) {
                    iterator.remove()
                    expired.add(e)
                }
            }
        }
        expired.forEach { emitLocked(it, now) }
    }

    /** 必须持锁调用（或已从表中移除后调用）。 */
    private fun emitLocked(e: Entry, now: Long) {
        if (e.emitted || e.rematched) return
        e.emitted = true
        val group = e.groupName.ifEmpty { "组" }
        val rule = e.ruleName.ifEmpty { "规则" }
        val evidence =
            when {
                e.targetGone && e.pageSwitched -> "目标消失+页面切换"
                e.targetGone -> "目标消失(同页规则不再命中)"
                e.pageSwitched -> "页面切换"
                else -> null
            }
        if (evidence != null) {
            sink(
                "splash_dismissed",
                "✅ 跳过结果确认（${e.appId} · $group · $rule）：$evidence · ${maxOf(0, now - e.successAt)}ms 内取得证据 · ${e.eventId}",
                0,
                e.appId,
                runCatching {
                    json.encodeToString(
                        SkipOutcomeDetail.serializer(),
                        SkipOutcomeDetail(
                            eventId = e.eventId,
                            appId = e.appId,
                            activityId = e.activityId,
                            groupName = e.groupName,
                            ruleName = e.ruleName,
                            evidence = evidence,
                            successAt = e.successAt,
                            settleAt = now,
                        ),
                    )
                }.getOrNull(),
            )
        } else {
            sink(
                "result_unknown",
                "❔ 跳过结果未确认（${e.appId} · $group · $rule）：观察窗内无后续评估证据（页面可能已切换但无规则活动）· ${e.eventId}",
                0,
                e.appId,
                runCatching {
                    json.encodeToString(
                        SkipOutcomeDetail.serializer(),
                        SkipOutcomeDetail(
                            eventId = e.eventId,
                            appId = e.appId,
                            activityId = e.activityId,
                            groupName = e.groupName,
                            ruleName = e.ruleName,
                            evidence = "none",
                            successAt = e.successAt,
                            settleAt = now,
                        ),
                    )
                }.getOrNull(),
            )
        }
    }

    /** 测试复位。 */
    fun resetForTest() {
        synchronized(this) {
            entries.clear()
            lastSkipAt.clear()
        }
    }
}

@Serializable
private data class SkipOutcomeDetail(
    val eventId: String,
    val appId: String,
    val activityId: String? = null,
    val groupName: String? = null,
    val ruleName: String? = null,
    val evidence: String,
    val successAt: Long,
    val settleAt: Long,
)
