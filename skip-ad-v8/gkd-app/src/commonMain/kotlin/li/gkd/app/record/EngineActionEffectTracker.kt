package li.gkd.app.record

import li.gkd.app.rule.ResolvedRule

/**
 * v8.1.0：动作后效果追踪（类11「点击成功但广告没有关闭」的代理信号）。
 *
 * 判定语义（如实标注，非精确检测）：
 * · 某规则成功执行动作后，广告若被关闭，页面通常切换/内容窗口消失——
 *   该规则在同一 Activity 内的后续评估轮次会很快归零（not_matched 或页面切换）。
 * · 若成功动作后短时间内同一 (规则, Activity) 仍持续被评估（观察到
 *   ActionLimitReached/Cooldown/Ready 等状态），说明广告窗口大概率仍在前台——
 *   即「点了但没关掉」。
 *
 * 节流：每 (规则, Activity) 60 秒最多一条；追踪表有界（64 条，淘汰最旧）。
 * 纯内存操作 + 非阻塞写入，绝不影响匹配主链路。
 *
 * v8.1.1：
 * · 键改为 executionIdentity（引用相等）——不再每轮构建 matchIdentity() 字符串
 *   （广告在屏期间的高频命中轮次零字符串分配）；
 * · action_no_effect 行携带 eventId，与 action_performed 行精确关联
 *   （retentionExecution 跨订阅快照保留 state，8 秒窗口内键稳定）。
 */
object EngineActionEffectTracker {
    private const val EFFECT_WINDOW_MS = 8_000L
    private const val LOG_GATE_MS = 60_000L
    private const val MAX_TRACKED = 64

    /** 测试注入点（生产恒为 EngineEventLogWriter.log）。 */
    @Volatile
    internal var sink: (kind: String, message: String, level: Int, appId: String?, detail: String?) -> Unit =
        { kind, message, level, appId, detail ->
            EngineEventLogWriter.log(kind, message, level, appId, null, detail)
        }

    private class Entry {
        var appId = ""
        var activityId: String? = null
        var groupName = ""
        var ruleName = ""
        var eventId = ""
        var successAt = 0L
        var observeCount = 0
        /** 初始化为远过去：首个判定不受节流闸影响（时钟纪元前永不命中）。 */
        var lastLoggedAt = -LOG_GATE_MS
    }

    private val entries = LinkedHashMap<Any, Entry>()

    /** completed 回调接线：动作成功后记录时间锚点。纯内存 O(1)。 */
    fun onActionSuccess(
        rule: ResolvedRule,
        appId: String,
        activityId: String?,
        eventId: String = "",
        now: Long = System.currentTimeMillis(),
    ) {
        // v8.1.1：executionIdentity 引用键（retainExecution 跨快照保留）——
        // 不再每轮 matchIdentity() 字符串构建。
        val key = rule.executionIdentity
        synchronized(this) {
            val e = entries.getOrPut(key) {
                if (entries.size >= MAX_TRACKED) entries.remove(entries.keys.first())
                Entry()
            }
            e.appId = appId
            e.activityId = activityId
            e.groupName = rule.g.group.name
            e.ruleName = rule.rule.name ?: ""
            e.eventId = eventId
            e.successAt = now
            e.observeCount = 0
        }
    }

    /**
     * observed 回调接线：同一 (规则, Activity) 在成功动作后 EFFECT_WINDOW_MS 窗口内
     * 再次被评估 → 计数；累计 3 轮仍未消失 → 落库一条 action_no_effect。
     * 规则/页面切换（Activity 变化）自动失效。
     */
    fun onObserved(
        rule: ResolvedRule,
        appId: String,
        activityId: String?,
        now: Long = System.currentTimeMillis(),
    ) {
        val key = rule.executionIdentity
        synchronized(this) {
            val e = entries[key] ?: return
            if (e.appId != appId || e.activityId != activityId) return
            val since = now - e.successAt
            if (since < 0 || since > EFFECT_WINDOW_MS) {
                entries.remove(key)
                return
            }
            e.observeCount++
            if (e.observeCount < 3) return
            if (now - e.lastLoggedAt < LOG_GATE_MS) return
            e.lastLoggedAt = now
            sink(
                "action_no_effect",
                "🔁 点击后广告疑似仍在（${e.appId} · ${e.groupName} · ${e.ruleName.ifEmpty { "规则" }}）：成功点击后 ${since / 1000}s 内规则仍持续命中评估 ${e.observeCount} 轮" +
                    (if (e.eventId.isNotEmpty()) " · ${e.eventId}" else "") +
                    " ——可能点击未生效或广告未关闭，上传日志可帮我们修正规则",
                1,
                appId,
                null,
            )
        }
    }

    /** 测试复位。 */
    fun resetForTest() {
        synchronized(this) { entries.clear() }
    }
}
