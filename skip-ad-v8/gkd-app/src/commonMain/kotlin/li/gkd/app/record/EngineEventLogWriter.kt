package li.gkd.app.record

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.gkd.app.logging.LogUtils
import li.gkd.db.Db
import li.gkd.db.EngineEventLog

/**
 * 详细引擎日志写入器（v8.0.6 双通道日志体系）。
 * · 非阻塞：调用线程（含无障碍事件线程）只做 Channel.trySend，绝不等待
 * · 容量有界：队列 256 条，溢出丢最旧 info 级换位，warn/error 尽力保留
 * · 单协程消费：批 ≤16 条写库 + 定期裁剪（保留最近 800 条）
 * · 失败只进 LogUtils：日志体系绝不允许影响引擎主链/点击速度
 */
object EngineEventLogWriter {
    private const val QUEUE_CAPACITY = 256
    private const val BATCH_SIZE = 16
    private const val TRIM_INTERVAL = 64L

    private val queue = Channel<EngineEventLog>(QUEUE_CAPACITY)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var started = false
    private var written = 0L

    /** v8.1.0：已入队待写计数（drain 屏障用）。 */
    private val pending = java.util.concurrent.atomic.AtomicLong(0)

    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            scope.launch {
                val batch = ArrayList<EngineEventLog>(BATCH_SIZE)
                while (true) {
                    batch.clear()
                    batch.add(queue.receive())
                    while (batch.size < BATCH_SIZE) {
                        val next = queue.tryReceive().getOrNull() ?: break
                        batch.add(next)
                    }
                    try {
                        Db.engineEventLogDao.insert(*batch.toTypedArray())
                        val before = written
                        written += batch.size
                        if (before / TRIM_INTERVAL != written / TRIM_INTERVAL) {
                            Db.engineEventLogDao.deleteKeepLatest()
                        }
                    } catch (e: Exception) {
                        LogUtils.w("engine event log write failed", e)
                    } finally {
                        pending.addAndGet(-batch.size.toLong())
                    }
                }
            }
        }
    }

    /** 线程安全非阻塞。重要事件（warn/error）溢出时保留优先级更高者。 */
    fun log(
        kind: String,
        message: String,
        level: Int = 0,
        appId: String? = null,
        actionLogId: Long? = null,
        detail: String? = null,
    ) {
        start()
        val entry =
            EngineEventLog(
                ctime = System.currentTimeMillis(),
                level = level,
                kind = kind,
                appId = appId,
                actionLogId = actionLogId,
                message = message,
                detail = detail,
            )
        pending.incrementAndGet()
        if (queue.trySend(entry).isSuccess) return
        val dropped = queue.tryReceive().getOrNull()
        if (dropped != null) pending.decrementAndGet()
        if (dropped == null || dropped.level < level) {
            queue.trySend(entry)
        } else {
            pending.decrementAndGet() // 溢出丢弃：同样脱离待写集
        }
    }

    /**
     * v8.1.0：上传前落库屏障——等待队列与在途批次全部写库（或超时）。
     * 修复 v8.0.8 缺口：flushAll 只是入队，报告随后的查库可能读不到刚入队的
     * 最新窗口（消费协程批处理延迟）——「上传前确保最新触发记录已落库」。
     */
    suspend fun drain(timeoutMs: Long = 2000L) {
        start()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (pending.get() > 0) {
            if (System.currentTimeMillis() >= deadline) return
            delay(25)
        }
    }
}
