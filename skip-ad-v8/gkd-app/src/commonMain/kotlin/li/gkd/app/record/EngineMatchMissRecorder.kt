package li.gkd.app.record

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.db.SubscriptionIds

/**
 * v8.0.8：匹配未命中聚合记录器（补齐「广告为什么没跳过」的诊断缺口）。
 *
 * 数据来源：RuleQuery.observed 回调（引擎每轮规则评估的结果分布）。
 *
 * 节流策略（对齐 7.15.14 logScanMiss 的降噪思想，速率有界）：
 * · 按 appId 聚合为 8 秒窗口，每个应用每个窗口最多落库 1 条汇总；
 * · 窗口内只统计结果分布（not_matched / node_unavailable / 前台冲突 / 状态跳过…）
 *   与涉及的规则组，绝不逐规则逐事件落库；
 * · 任一规则命中（matched=true）→ 该应用窗口直接丢弃（成功路径由
 *   action_performed / action_log 记录，不产生 miss 噪音）；
 * · 追踪的应用数有界（24），超出淘汰最旧；
 * · flushAll() 在报告构建前调用，保证最近一个未到期窗口也能进上传。
 *
 * 写入经 EngineEventLogWriter（非阻塞有界队列），绝不影响匹配主链路。
 */
object EngineMatchMissRecorder {
    private const val WINDOW_MS = 8_000L
    private const val MAX_TRACKED_APPS = 24

    private val json = Json { encodeDefaults = false }

    private class Window {
        var startMs = 0L
        var scans = 0
        var lastAppId: String? = null
        val statuses = LinkedHashMap<String, Int>()
        val groups = LinkedHashSet<String>()

        fun reset(now: Long, appId: String?) {
            startMs = now
            scans = 0
            lastAppId = appId
            statuses.clear()
            groups.clear()
        }
    }

    private val windows = LinkedHashMap<String, Window>()
    private var lastSubsName: Long = -1
    private var lastSubsNameValue: String = ""

    /** 引擎每轮规则评估结果回传（同步、纯内存、O(1)）。matched=true 表示有规则命中。 */
    fun record(
        ruleSubsId: Long,
        groupName: String?,
        appId: String?,
        status: String,
        matched: Boolean,
    ) {
        val key = appId ?: return
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (matched) {
                // 命中：丢弃该应用窗口，成功由 action 通道记录
                windows.remove(key)
                return
            }
            if (windows.size > MAX_TRACKED_APPS) {
                val oldest = windows.keys.firstOrNull() ?: return
                flushLocked(oldest, windows.remove(oldest)!!)
            }
            val window =
                windows.getOrPut(key) {
                    Window().also { it.reset(now, key) }
                }
            if (now - window.startMs >= WINDOW_MS) {
                flushLocked(key, window)
                window.reset(now, key)
            }
            window.scans++
            window.statuses.merge(status, 1, Int::plus)
            window.groups.add("${subsName(ruleSubsId)}·${groupName ?: "?"}")
        }
    }

    /** 报告构建/上传前调用：把未到期窗口也落库（最近一次未命中最重要）。 */
    fun flushAll() {
        synchronized(this) {
            val iterator = windows.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                iterator.remove()
                flushLocked(entry.key, entry.value)
            }
        }
    }

    private fun flushLocked(appId: String, w: Window) {
        if (w.scans <= 0) return
        val distribution =
            w.statuses.entries.joinToString(" · ") { (status, count) -> "$status×$count" }
        val groups = w.groups.joinToString(" / ").take(200)
        EngineEventLogWriter.log(
            kind = "match_miss",
            message = "❓ 规则未命中（$appId）：${w.scans} 轮评估未命中 · 组[$groups] · $distribution ——上传日志可帮我们补规则",
            level = 0,
            appId = appId,
            detail = json.encodeToString(
                MatchMissDetail.serializer(),
                MatchMissDetail(
                    windowStartMs = w.startMs,
                    scans = w.scans,
                    statuses = w.statuses,
                    groups = w.groups.toList(),
                ),
            ),
        )
    }

    private fun subsName(subsId: Long): String {
        if (subsId == lastSubsName) return lastSubsNameValue
        val name =
            if (SubscriptionIds.isLocal(subsId)) "本地" else "订阅$subsId"
        lastSubsName = subsId
        lastSubsNameValue = name
        return name
    }
}

@Serializable
private data class MatchMissDetail(
    val windowStartMs: Long,
    val scans: Int,
    val statuses: Map<String, Int>,
    val groups: List<String>,
)
