package li.gkd.app.state

import java.util.concurrent.atomic.AtomicLong

/**
 * 引擎运行期计数器（v8.0.7 能耗画像真实数据源）。
 *
 * 语义：进程本次启动以来（非设备开机以来）的累计值，进程重启归零。
 * 仅内存计数，不落库、不持久化——与旧版 7.15.14 energyStats 的
 * 「本次开机」口径对齐为最接近的真实口径（新引擎无法区分设备开机，
 * 如实按「进程本次启动以来」表述，展示端注明）。
 *
 * 计数点（与旧版 AdSkipService 同位）：
 *  - [addEvent]：AndroidMatchHost.onA11yEvent 中，事件通过基础过滤
 *    （进入匹配管线）后 +1 —— 对应旧版 events（onAccessibilityEvent
 *    息屏/横幅过滤后计数）。
 *  - [addScan]：AndroidMatchHost.queryAction 入口 +1 —— 每轮对活动
 *    窗口的完整规则扫描计一次，对应旧版 scans（五层规则 DFS 扫描）。
 *
 * 只做原子自增（无锁 AtomicLong），零 IO、零锁竞争，不参与任何
 * 匹配决策——纯观测，不影响广告跳过行为。
 */
object EngineRuntimeCounters {
    private val events = AtomicLong(0)
    private val scans = AtomicLong(0)

    fun addEvent(delta: Long = 1) {
        events.addAndGet(delta)
    }

    fun addScan(delta: Long = 1) {
        scans.addAndGet(delta)
    }

    fun eventCount(): Long = events.get()

    fun scanCount(): Long = scans.get()
}
