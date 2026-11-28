package li.gkd.app.upload

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.crash.FileCrashStorage
import li.gkd.app.data.currentDeviceInfo
import li.gkd.app.record.EngineEventLogWriter
import li.gkd.app.record.EngineMatchMissRecorder
import li.gkd.app.record.EngineSkipOutcomeTracker
import li.gkd.app.record.MatchLatencyStats
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.EngineRuntimeCounters
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.db.Db
import li.gkd.db.RuleGroupType
import li.gkd.db.SubscriptionIds

/**
 * v8.0.8：统一反馈报告构建器。
 *
 * v8.0.7 的问题（Issue #142/#143/#150/#151 实锤）：
 * · 自动/Compose 手动上传只有 3 段骨架 + 无规则名的触发编号行；
 * · 详细引擎日志只在 WebView 手动上传出现（30 条）——三条上传路径内容不一致；
 * · 匹配失败原因 / 动作失败 / 崩溃堆栈 / 战绩统计缺失，空数据段落无任何说明。
 *
 * 本构建器为唯一事实来源：自动上传、Compose 手动上传、WebView 手动上传全部
 * 走 [build]，结构对齐 7.15.14 的详细日志能力并增强关联（actionLogId）。
 * 空数据与读取失败显式标注；查询经 runCatching，任何段落失败不拖垮整份报告。
 */
object FeedbackReport {
    private const val LATEST_ACTIONS = 30
    private const val DETAIL_ROWS = 120
    private const val ROW_MESSAGE_LIMIT = 200
    private const val TOP_APPS = 5

    private val timestampFormat =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    private val detailJson = Json { ignoreUnknownKeys = true }

    /** 平台状态注入点（androidMain 在 App 启动时接线，JVM 测试保持 null）。 */
    object Hooks {
        @Volatile var engineRunningProvider: (() -> Boolean?)? = null

        @Volatile var foregroundAppIdProvider: (() -> String?)? = null
    }

    suspend fun build(channel: String): String {
        // 冲刷未到期的匹配未命中窗口：上传前必须能看到最近一次未命中
        runCatching { EngineMatchMissRecorder.flushAll() }
        // v8.1.1：跳过结果状态机冲刷（在途 splash_dismissed / result_unknown 先落库）
        runCatching { EngineSkipOutcomeTracker.settleAll() }
        // v8.1.0：落库屏障——确保刚冲刷的窗口（及在途批次）已写库后再查询，
        // 修复「上传报告里缺最近一次未命中」的竞态（最长等待 2s，超时不阻塞上传）。
        runCatching { EngineEventLogWriter.drain() }
        return buildString {
            section1Environment(channel)
            section2LatestActions()
            section3DetailLog()
            section4Exceptions()
            section5Summary()
            section6Notes()
        }
    }

    // ───────── 1. 基本环境 ─────────

    private fun StringBuilder.section1Environment(channel: String) {
        appendLine("## 1. 基本环境")
        appendLine("- 应用: ${META.appName} v${META.versionName} (${META.versionCode}) · 通道 ${META.channel}")
        val device = currentDeviceInfo()
        appendLine("- 设备: ${device.brand} ${device.model} (${device.device}), Android ${device.release} (SDK ${device.sdkInt})")
        appendLine("- 引擎状态: ${engineStatusText()}")
        Hooks.foregroundAppIdProvider?.invoke()?.let { appendLine("- 上传时前台应用: $it") }
        appendLine("- 进程本次启动: 无障碍事件 ${EngineRuntimeCounters.eventCount()} · 规则扫描 ${EngineRuntimeCounters.scanCount()} · 拦截总数(累计) ${SettingsRepository.actionCount.value}")
        appendLine("- 上传通道: $channel")
        appendLine()
    }

    private fun engineStatusText(): String =
        when (Hooks.engineRunningProvider?.invoke()) {
            true -> "无障碍服务运行中"
            false -> "无障碍服务未运行（规则不会执行）"
            null -> "未知（仅移动端可探测）"
        }

    // ───────── 2. 最新触发记录 ─────────

    private suspend fun StringBuilder.section2LatestActions() {
        appendLine("## 2. 最新触发记录（最多 $LATEST_ACTIONS 条）")
        val result =
            runCatching {
                val actions = Db.actionLogDao.queryLatest(LATEST_ACTIONS.toLong())
                if (actions.isEmpty()) {
                    appendLine("（暂无触发记录：引擎尚未成功执行任何规则——结合第 3 节匹配未命中汇总可定位原因）")
                    return@runCatching
                }
                // 详细通道同事务关联：actionLogId → 可读消息（含应用名/组名/规则名/结果/耗时）
                val byActionId =
                    Db.engineEventLogDao.queryLatest(DETAIL_ROWS)
                        .mapNotNull { row -> row.actionLogId?.let { it.toInt() to row } }
                        .toMap()
                val groupNames = buildGroupNameMap()
                actions.forEach { log ->
                    val time = timestampFormat.format(Instant.ofEpochMilli(log.ctime))
                    val engineLine = byActionId[log.id]?.let { row ->
                        "${row.message}${row.detail?.let { d -> " ${detailSuffix(d)}" } ?: ""}"
                    }
                    appendLine(
                        if (engineLine != null) {
                            "- [$time] $engineLine"
                        } else {
                            val appName = appDisplayName(log.appId)
                            val group =
                                groupNames[Triple(log.subsId, log.groupType, log.groupKey)]
                            "- [$time] $appName(${log.appId}) · ${subsLabel(log.subsId)} · 组[${group ?: "key=${log.groupKey}"}] · 规则${log.ruleIndex}"
                        },
                    )
                }
            }
        result.onFailure { appendLine("（读取失败：${it.message ?: it.javaClass.simpleName}）") }
        appendLine()
    }

    // ───────── 3. 详细执行记录 ─────────

    private suspend fun StringBuilder.section3DetailLog() {
        appendLine("## 3. 详细执行记录（引擎日志最近 $DETAIL_ROWS 条，时间正序；表内保留最近 1200 条）")
        val result =
            runCatching {
                val rows = Db.engineEventLogDao.queryLatest(DETAIL_ROWS)
                if (rows.isEmpty()) {
                    appendLine("（暂无详细记录：引擎运行后自动产生——服务连接/规则执行与失败/匹配未命中汇总/订阅与配置状态）")
                    return@runCatching
                }
                rows.asReversed().forEach { row ->
                    val time = timestampFormat.format(Instant.ofEpochMilli(row.ctime))
                    val level = when (row.level) {
                        2 -> "❌"
                        1 -> "⚠️"
                        else -> "ℹ️"
                    }
                    val detail = row.detail?.let { " ${detailSuffix(it)}" } ?: ""
                    appendLine("- [$time]$level ${row.message.take(ROW_MESSAGE_LIMIT)}$detail")
                }
            }
        result.onFailure { appendLine("（读取失败：${it.message ?: it.javaClass.simpleName}）") }
        appendLine()
    }

    // ───────── 4. 异常与失败 ─────────

    private suspend fun StringBuilder.section4Exceptions() {
        appendLine("## 4. 异常与失败")
        val result =
            runCatching {
                val failures =
                    Db.engineEventLogDao.queryLatest(DETAIL_ROWS).filter { it.level >= 1 }
                if (failures.isEmpty()) {
                    appendLine("（窗口内无警告/错误级记录）")
                } else {
                    failures.asReversed().take(40).forEach { row ->
                        val time = timestampFormat.format(Instant.ofEpochMilli(row.ctime))
                        appendLine("- [$time] ${row.message.take(ROW_MESSAGE_LIMIT)}")
                    }
                }
                val crashes = FileCrashStorage.load().take(2)
                if (crashes.isEmpty()) {
                    appendLine("- 上次崩溃堆栈: 无")
                } else {
                    appendLine("- 上次崩溃堆栈(最近 ${crashes.size} 次,截断):")
                    crashes.forEach { crash ->
                        appendLine("```")
                        appendLine(crash.stackTrace.take(4000))
                        appendLine("```")
                    }
                }
            }
        result.onFailure { appendLine("（读取失败：${it.message ?: it.javaClass.simpleName}）") }
        appendLine()
    }

    // ───────── 5. 统计摘要 ─────────

    private suspend fun StringBuilder.section5Summary() {
        appendLine("## 5. 统计摘要")
        val result =
            runCatching {
                val rows = Db.engineEventLogDao.queryLatest(DETAIL_ROWS)
                val kindCounts = rows.groupingBy { it.kind }.eachCount()
                val matchMiss = kindCounts["match_miss"] ?: 0
                val actionFailed = kindCounts["action_failed"] ?: 0
                val actionOk = kindCounts["action_performed"] ?: 0
                // v8.1.0：新增诊断类的可见性（失败原因分类链）
                val filtered = kindCounts["rule_filtered"] ?: 0
                val noEffect = kindCounts["action_no_effect"] ?: 0
                val gated = kindCounts["query_gated"] ?: 0
                // v8.1.1：跳过结果状态机（已确认消失/无法确认）
                val dismissed = kindCounts["splash_dismissed"] ?: 0
                val unknown = kindCounts["result_unknown"] ?: 0
                appendLine("- 详细窗口($DETAIL_ROWS 条内): 动作成功 $actionOk · 动作失败 $actionFailed · 匹配未命中汇总 $matchMiss · 记录共 ${rows.size} 条")
                if (filtered + noEffect + gated > 0) {
                    appendLine("- 失败原因链: 规则未参与评估 $filtered · 点击后广告疑似仍在 $noEffect · 引擎闸门拦截 $gated")
                }
                if (dismissed + unknown > 0) {
                    appendLine("- 跳过结果链: 已确认广告消失 $dismissed · 结果未确认 $unknown（eventId 关联动作行）")
                }
                // v8.1.1：匹配链路延迟画像（p50/p95，无样本时省略）
                MatchLatencyStats.summaryText()?.let { appendLine("- $it") }
                val actions = Db.actionLogDao.queryLatest(500L)
                if (actions.isEmpty()) {
                    appendLine("- 应用战绩: 暂无触发")
                } else {
                    actions.groupBy { it.appId }
                        .map { (appId, list) -> appId to list.size }
                        .sortedByDescending { it.second }
                        .take(TOP_APPS)
                        .forEach { (appId, count) ->
                            appendLine("- 应用战绩: ${appDisplayName(appId)}($appId) $count 次")
                        }
                }
            }
        result.onFailure { appendLine("（读取失败：${it.message ?: it.javaClass.simpleName}）") }
        appendLine()
    }

    // ───────── 6. 日志说明 ─────────

    private fun StringBuilder.section6Notes() {
        appendLine("## 6. 日志说明")
        appendLine("- 采集范围: 详细引擎日志表(上限 1200 条,本次取最近 $DETAIL_ROWS 条) + 触发记录表(上限 500 条,本次取 $LATEST_ACTIONS 条) + 崩溃存档(≤20 份,非删除式读取)")
        appendLine("- 统计口径: 「进程本次启动」计数为本次进程生命周期累计;「窗口」指本次上传所取日志条数范围;能耗口径与 7.15.14 一致(系数估算,非实测)")
        appendLine("- 失败诊断链: 规则未参与评估(停用/不兼容/版本门槛/页面不适用) · 规则未命中(含首个未命中样本) · 动作失败(细分节点不可见/拒绝点击/手势缺失) · 点击后广告疑似仍在(代理信号) · 引擎闸门拦截")
        appendLine("- 跳过结果状态机(v8.1.1): 动作成功后 8s 观察窗内——同规则不再命中或页面切换 → splash_dismissed；无后续证据 → result_unknown；同规则再次命中 → action_no_effect 链。同一 eventId 贯穿动作行与结果行")
        appendLine("- 性能口径: p50/p95 为最近 64 个样本的分布，事件→动作为「事件到达→动作完成」粗口径（含排队），扫描→命中为单轮扫描内首个命中耗时")
        appendLine("- 脱敏说明: 手机号/身份证/邮箱/IPv4 在提交前后双层脱敏;屏幕内容不会上传;订阅与规则数据仅含包名/类名/选择器文本")
    }

    // ───────── 共用 ─────────

    /** 订阅组名映射：subsId + groupType(App=2/Global=3) + groupKey → 组名（与 WebUiState 同法） */
    private fun buildGroupNameMap(): Map<Triple<Long, Int, Int>, String> {
        val map = HashMap<Triple<Long, Int, Int>, String>()
        val snapshot =
            (SubscriptionRepository.snapshotFlow.value.value ?: return map).subscriptions
        for (sub in snapshot.values) {
            for (g in sub.globalGroups) {
                map[Triple(sub.id, RuleGroupType.Global, g.key)] = g.name
            }
            for (a in sub.apps) {
                for (g in a.groups) {
                    map[Triple(sub.id, RuleGroupType.App, g.key)] = g.name
                }
            }
        }
        return map
    }

    private fun appDisplayName(appId: String): String =
        AppInfoRepository.snapshot?.apps?.get(appId)?.name ?: appId.substringAfterLast('.')

    private fun subsLabel(subsId: Long): String =
        if (SubscriptionIds.isLocal(subsId)) "本地订阅" else "订阅#$subsId"

    /** 引擎日志 detail 列的紧凑摘要（kind/actionType/result/reason/durationMs 等附加字段） */
    private fun detailSuffix(detail: String): String =
        runCatching {
            val raw = detailJson.parseToJsonElement(detail).toString()
            if (raw.length <= 160) raw else raw.take(160) + "…"
        }.getOrDefault("")
}
