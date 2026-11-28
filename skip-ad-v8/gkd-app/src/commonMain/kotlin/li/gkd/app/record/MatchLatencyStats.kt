package li.gkd.app.record

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * v8.1.1 匹配链路延迟画像（跳过速度回归的量化依据）。
 *
 * 采集点（全部纯内存原子操作，采样在动作完成时——天然低频，绝不阻塞主链）：
 * · 事件到达 → 动作完成（端到端，用户感知口径）；
 * · 扫描开始 → 规则命中（规则评估耗时）；
 * · 动作开始 → API 返回（动作执行耗时，v8.0.x 已有 durationMs，此处为分布视角）。
 *
 * 每类指标保留最近 64 个样本（环形），报告导出 p50/p95/均值——
 * 「优化前后对比表」从此有真实数字，不再只有主观感受。
 */
object MatchLatencyStats {
    private const val SAMPLES = 64

    private class Ring {
        val data = LongArray(SAMPLES)
        var head = 0
        var size = 0
        val total = AtomicLong(0)

        fun add(value: Long) {
            if (value < 0 || value > 60_000) return // 脏样本防线（时钟跳变/跨进程）
            data[head] = value
            head = (head + 1) % SAMPLES
            if (size < SAMPLES) size++
        }

        fun percentile(p: Double): Long {
            if (size == 0) return -1
            val copy = data.copyOf(size).apply { sort() }
            val idx = min(copy.size - 1, (p * (copy.size - 1)).toInt())
            return copy[idx]
        }

        fun mean(): Long =
            if (size == 0) -1 else data.copyOf(size).sum() / size
    }

    private val eventToAction = Ring()
    private val scanToMatch = Ring()
    private val actionDuration = Ring()

    /** 最近一次事件到达时间锚（AndroidMatchHost 在事件过基础过滤后刷新）。 */
    @Volatile
    private var lastEventArrivalMs: Long = -1

    @Volatile
    private var scanStartMs: Long = -1

    @Volatile
    private var matchAtMs: Long = -1

    fun onEventArrived(nowMs: Long) {
        lastEventArrivalMs = nowMs
    }

    fun onScanStarted(nowMs: Long) {
        scanStartMs = nowMs
        matchAtMs = -1
    }

    fun onMatched(nowMs: Long) {
        if (matchAtMs >= 0) return // 每轮扫描只记首个命中
        matchAtMs = nowMs
        if (scanStartMs in 0..nowMs) scanToMatch.add(nowMs - scanStartMs)
    }

    fun onActionCompleted(nowMs: Long, actionDurationMs: Long) {
        if (actionDurationMs >= 0) actionDuration.add(actionDurationMs)
        if (lastEventArrivalMs in 0..nowMs) eventToAction.add(nowMs - lastEventArrivalMs)
    }

    /** 报告文本（FeedbackReport 第 5 节）；无样本时返回 null（不打印噪音行）。 */
    fun summaryText(): String? {
        if (eventToAction.size == 0 && scanToMatch.size == 0) return null
        val parts = mutableListOf<String>()
        ringLine("事件→动作", eventToAction)?.let { parts.add(it) }
        ringLine("扫描→命中", scanToMatch)?.let { parts.add(it) }
        ringLine("动作API耗时", actionDuration)?.let { parts.add(it) }
        if (parts.isEmpty()) return null
        return "匹配性能窗口(最近样本): ${parts.joinToString(" · ")}（p50/p95, ms）"
    }

    private fun ringLine(label: String, ring: Ring): String? =
        if (ring.size == 0) null else "$label ${ring.percentile(0.5)}/${ring.percentile(0.95)}(n=${ring.size})"

    /** 测试复位。 */
    fun resetForTest() {
        lastEventArrivalMs = -1
        scanStartMs = -1
        matchAtMs = -1
    }
}
