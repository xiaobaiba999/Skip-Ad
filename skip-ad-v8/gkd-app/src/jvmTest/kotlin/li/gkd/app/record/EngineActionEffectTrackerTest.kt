package li.gkd.app.record

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

/**
 * v8.1.0 动作后效果追踪测试（类 11「点击成功但广告没有关闭」代理信号）。
 *
 * 语义：
 * · 成功动作后 8s 窗口内同一 (规则, Activity) 持续被观察到 ≥3 轮 → 落库一次 action_no_effect；
 * · 60s 节流内不重复落库；
 * · Activity 切换或窗口过期 → 追踪失效。
 */
class EngineActionEffectTrackerTest {
    private val logged = mutableListOf<String>()

    @AfterTest
    fun reset() {
        EngineActionEffectTracker.resetForTest()
        EngineActionEffectTracker.sink =
            { kind, message, level, appId, detail ->
                EngineEventLogWriter.log(kind, message, level, appId, null, detail)
            }
    }

    private fun installSink() {
        EngineActionEffectTracker.sink = { kind, _, _, _, _ -> logged.add(kind) }
    }

    private fun rule(): li.gkd.app.rule.AppRule {
        val raw =
            RawSubscriptionParser.parse(
                """{id:1,name:'Test',version:1,
                apps:[{id:'app.test',groups:[{key:1,name:'开屏',rules:[{name:'跳过',matches:'*'}]}]}]}""",
            )
        return RuleSummaryBuilder.build(
                RuleRuntime(now = { 0L }),
                listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), raw)),
                mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
                emptyList(),
                emptyList(),
                emptyList(),
            )
            .appIdToRules
            .getValue("app.test")
            .single()
    }

    @Test
    fun repeatedObservationsAfterSuccessLogNoEffect() {
        installSink()
        var now = 1_000L
        val r = rule()
        EngineActionEffectTracker.onActionSuccess(r, "app.test", ".AdActivity", now)
        repeat(3) { now += 100; EngineActionEffectTracker.onObserved(r, "app.test", ".AdActivity", now) }
        assertEquals(1, logged.size)
        assertEquals("action_no_effect", logged.first())
    }

    @Test
    fun twoObservationsDoNotLog() {
        installSink()
        var now = 1_000L
        val r = rule()
        EngineActionEffectTracker.onActionSuccess(r, "app.test", ".AdActivity", now)
        repeat(2) { now += 100; EngineActionEffectTracker.onObserved(r, "app.test", ".AdActivity", now) }
        assertTrue(logged.isEmpty(), "两轮观察不足以判定")
    }

    @Test
    fun activityChangeInvalidatesTracking() {
        installSink()
        var now = 1_000L
        val r = rule()
        EngineActionEffectTracker.onActionSuccess(r, "app.test", ".AdActivity", now)
        repeat(5) { now += 100; EngineActionEffectTracker.onObserved(r, "app.test", ".Main", now) }
        assertTrue(logged.isEmpty(), "Activity 已切换（广告可能已随页面关闭）")
    }

    @Test
    fun windowExpiryInvalidatesTracking() {
        installSink()
        var now = 1_000L
        val r = rule()
        EngineActionEffectTracker.onActionSuccess(r, "app.test", ".AdActivity", now)
        now += 9_000 // 超过 8s 效果窗口
        repeat(5) { now += 100; EngineActionEffectTracker.onObserved(r, "app.test", ".AdActivity", now) }
        assertTrue(logged.isEmpty(), "窗口外观察不计（页面可能已自然变化）")
    }

    @Test
    fun logGateSuppressesRepeatWithin60s() {
        installSink()
        var now = 1_000L
        val r = rule()
        EngineActionEffectTracker.onActionSuccess(r, "app.test", ".AdActivity", now)
        repeat(5) { now += 100; EngineActionEffectTracker.onObserved(r, "app.test", ".AdActivity", now) }
        assertEquals(1, logged.size)
        // 新一轮成功+观察（60s 节流内）不再落库
        EngineActionEffectTracker.onActionSuccess(r, "app.test", ".AdActivity", now)
        repeat(5) { now += 100; EngineActionEffectTracker.onObserved(r, "app.test", ".AdActivity", now) }
        assertEquals(1, logged.size)
    }
}
