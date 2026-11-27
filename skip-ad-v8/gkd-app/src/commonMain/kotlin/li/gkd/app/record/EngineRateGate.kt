package li.gkd.app.record

/**
 * v8.0.8：有界节流闸（按 key 记录上次放行时间）。
 *
 * 用途：低频诊断日志（如「应用无规则」）的速率限制——同一 key 在 intervalMs 内只放行
 * 一次，避免高频事件路径把详细日志通道刷爆。追踪 key 数有界（64），超出淘汰最旧，
 * 内存占用恒定。
 */
object EngineRateGate {
    private const val MAX_KEYS = 64

    private val lastAt = LinkedHashMap<String, Long>()

    fun tryAcquire(
        key: String,
        intervalMs: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean =
        synchronized(this) {
            val last = lastAt[key]
            if (last != null && now - last < intervalMs) return false
            if (last == null && lastAt.size >= MAX_KEYS) {
                lastAt.remove(lastAt.keys.first())
            }
            lastAt[key] = now
            true
        }
}
