package li.gkd.app.record

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

/**
 * v8.1.1 跳过结果状态机测试——「已跳过却记为未命中」事故的 12 项回归防线。
 *
 * 状态词汇（2.3 统一事件状态）：
 * · match_miss：确实没有命中任何规则（抑制窗外）；
 * · matched / action_started / action_succeeded：action_performed 行；
 * · action_failed：动作 API 失败；
 * · action_no_effect：同规则再次命中链（EffectTracker，既有）；
 * · splash_dismissed：有充分证据（targetGone / pageSwitched）；
 * · result_unknown：观察窗内无任何后续证据。
 */
class EngineSkipOutcomeTrackerTest {
    private data class Row(val kind: String, val message: String)

    private val rows = mutableListOf<Row>()

    @AfterTest
    fun reset() {
        EngineSkipOutcomeTracker.resetForTest()
        EngineMatchMissRecorder.resetForTest()
        EngineSkipOutcomeTracker.sink =
            { kind, message, level, appId, detail ->
                EngineEventLogWriter.log(kind, message, level, appId, null, detail)
            }
        EngineMatchMissRecorder.sinkForTest = null
    }

    private fun installSink() {
        EngineSkipOutcomeTracker.sink = { kind, message, _, _, _ -> rows.add(Row(kind, message)) }
        EngineMatchMissRecorder.sinkForTest = { kind, message, _, _, _ ->
            if (kind == "match_miss") rows.add(Row(kind, message))
        }
    }

    private fun rule(appId: String = "app.test"): li.gkd.app.rule.AppRule {
        val raw =
            RawSubscriptionParser.parse(
                """{id:1,name:'Test',version:1,
                apps:[{id:'$appId',groups:[{key:1,name:'开屏',rules:[{name:'跳过',matches:'*'}]}]}]}""",
            )
        return RuleSummaryBuilder.build(
                RuleRuntime(now = { 0L }),
                listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), raw)),
                mapOf(appId to AppInfo(appId, "Test", 1, "1", false, 0, false, 0)),
                emptyList(),
                emptyList(),
                emptyList(),
            )
            .appIdToRules
            .getValue(appId)
            .single()
    }

    /** 场景 1：命中且成功跳过（同规则评估不再命中）→ splash_dismissed，且无 match_miss。 */
    @Test
    fun hitAndSkippedYieldsSplashDismissedAndNoMiss() {
        installSink()
        var now = 10_000L
        val r = rule()
        EngineSkipOutcomeTracker.onActionSuccess("E1", r, "app.test", ".Splash", now)
        now += 500
        // 跳过后内容页：同规则再评估未命中（选择器真实跑过）
        EngineSkipOutcomeTracker.onRuleEvaluated(r, "app.test", ".Splash", "not_matched", false, now)
        EngineSkipOutcomeTracker.settleAll(now + 1_000)
        assertEquals(listOf("splash_dismissed"), rows.map { it.kind })
        assertTrue(rows.first().message.contains("E1"))
    }

    /** 场景 1b：抑制闸——跳过成功后 12s 内该应用的未命中轮次绝不产生 match_miss。 */
    @Test
    fun postSkipMissRoundsAreSuppressed() {
        installSink()
        var now = 10_000L
        val r = rule()
        EngineSkipOutcomeTracker.onActionSuccess("E1", r, "app.test", ".Splash", now)
        now += 500
        // 跳过后内容页产生未命中评估轮次（事故场景：v8.1.0 会开新窗口）
        repeat(5) {
            EngineMatchMissRecorder.record(1L, "开屏", "app.test", "not_matched", false, ruleIdentity = 7, nowMs = now)
        }
        now += 8_500
        EngineMatchMissRecorder.flushAll()
        assertEquals(emptyList(), rows.map { it.kind }, "跳过后的内容页评估绝不记为 match_miss")
        // 场景 5：抑制窗过后，真正的未命中恢复记录
        now += 4_000
        EngineMatchMissRecorder.record(1L, "开屏", "app.test", "not_matched", false, ruleIdentity = 8, nowMs = now)
        EngineMatchMissRecorder.flushAll()
        assertEquals(listOf("match_miss"), rows.map { it.kind })
    }

    /** 场景 2：命中但动作失败 → action_failed 行（无 eventId 纠缠），无 splash_dismissed。 */
    @Test
    fun actionFailedDoesNotProduceOutcomeRow() {
        installSink()
        val r = rule()
        // 失败路径不调用 onActionSuccess（completed 仅成功回调）——模拟 v8.1.1 接线
        EngineSkipOutcomeTracker.settleAll(10_000L)
        assertTrue(rows.isEmpty())
    }

    /** 场景 3：命中且动作成功但广告没消失（同规则再次命中）→ 不落结果行（action_no_effect 链负责）。 */
    @Test
    fun rematchSuppressesOutcomeRow() {
        installSink()
        var now = 10_000L
        val r = rule()
        EngineSkipOutcomeTracker.onActionSuccess("E1", r, "app.test", ".Splash", now)
        now += 400
        EngineSkipOutcomeTracker.onRuleEvaluated(r, "app.test", ".Splash", "matched_without_execution", true, now)
        EngineSkipOutcomeTracker.settleAll(now + 9_000)
        assertTrue(rows.isEmpty(), "再次命中走 action_no_effect 链，结果行不重复")
    }

    /** 场景 4：命中后无法确认广告是否消失（无任何后续评估）→ result_unknown。 */
    @Test
    fun noEvidenceYieldsResultUnknown() {
        installSink()
        var now = 10_000L
        val r = rule()
        EngineSkipOutcomeTracker.onActionSuccess("E9", r, "app.test", ".Splash", now)
        EngineSkipOutcomeTracker.settleAll(now + 8_500)
        assertEquals(listOf("result_unknown"), rows.map { it.kind })
        assertTrue(rows.first().message.contains("E9"))
    }

    /** 场景 5（补充）：页面切换证据 → splash_dismissed（不同 Activity 的任何规则评估）。 */
    @Test
    fun pageSwitchIsDismissalEvidence() {
        installSink()
        var now = 10_000L
        val r = rule()
        EngineSkipOutcomeTracker.onActionSuccess("E1", r, "app.test", ".Splash", now)
        now += 600
        // 同应用另一个 Activity 的规则被评估（页面已切走）
        EngineSkipOutcomeTracker.onRuleEvaluated(r, "app.test", ".Main", "not_matched", false, now)
        EngineSkipOutcomeTracker.settleAll(now + 7_500)
        assertEquals(listOf("splash_dismissed"), rows.map { it.kind })
    }

    /** 场景 7：同一事件重复触发（多次成功动作同规则）→ 后一次覆盖前一次，单一在途结果。 */
    @Test
    fun repeatedTriggersReplacePendingOutcome() {
        installSink()
        var now = 10_000L
        val r = rule()
        EngineSkipOutcomeTracker.onActionSuccess("E1", r, "app.test", ".Splash", now)
        now += 100
        EngineSkipOutcomeTracker.onActionSuccess("E2", r, "app.test", ".Splash", now)
        now += 300
        EngineSkipOutcomeTracker.onRuleEvaluated(r, "app.test", ".Splash", "not_matched", false, now)
        EngineSkipOutcomeTracker.settleAll(now + 8_100)
        assertEquals(1, rows.size, "同规则第二次成功覆盖第一次（单在途结果）")
        assertEquals("splash_dismissed", rows.first().kind)
        assertTrue(rows.first().message.contains("E2"))
    }

    /** 场景 8：异步回调晚于上传（settleAll 后新证据）→ 已发射结果不被推翻。 */
    @Test
    fun lateEvidenceDoesNotOverturnSettledOutcome() {
        installSink()
        var now = 10_000L
        val r = rule()
        EngineSkipOutcomeTracker.onActionSuccess("E1", r, "app.test", ".Splash", now)
        EngineSkipOutcomeTracker.settleAll(now + 8_000) // result_unknown
        assertEquals(listOf("result_unknown"), rows.map { it.kind })
        now += 100
        // 迟到的评估（已结算）——不产生新行、不推翻
        EngineSkipOutcomeTracker.onRuleEvaluated(r, "app.test", ".Splash", "not_matched", false, now)
        assertEquals(1, rows.size)
    }

    /** 场景 9：多事件并发（多应用/多规则）→ 各自独立结算，互不串扰。 */
    @Test
    fun concurrentAppsSettleIndependently() {
        installSink()
        var now = 10_000L
        val r1 = rule("app.one")
        val r2 = rule("app.two")
        EngineSkipOutcomeTracker.onActionSuccess("E1", r1, "app.one", ".Splash", now)
        EngineSkipOutcomeTracker.onActionSuccess("E2", r2, "app.two", ".Splash", now)
        now += 400
        // app.one：目标消失；app.two：无后续
        EngineSkipOutcomeTracker.onRuleEvaluated(r1, "app.one", ".Splash", "not_matched", false, now)
        EngineSkipOutcomeTracker.settleAll(now + 8_000)
        assertEquals(2, rows.size)
        val kinds = rows.map { it.kind }.sorted()
        assertEquals(listOf("result_unknown", "splash_dismissed").sorted(), kinds)
        assertTrue(rows.none { it.message.contains("E1") && it.kind == "result_unknown" })
        assertTrue(rows.none { it.message.contains("E2") && it.kind == "splash_dismissed" })
    }

    /** 场景 9b：跨应用证据不串扰（app.two 的页面切换不算 app.one 的证据）。 */
    @Test
    fun crossAppEvidenceIsolation() {
        installSink()
        var now = 10_000L
        val r1 = rule("app.one")
        val r2 = rule("app.two")
        EngineSkipOutcomeTracker.onActionSuccess("E1", r1, "app.one", ".Splash", now)
        now += 200
        // app.two 的规则在另一 Activity 评估——不是 app.one 的页面切换证据
        EngineSkipOutcomeTracker.onRuleEvaluated(r2, "app.two", ".Main", "not_matched", false, now)
        // app.one 同规则 Cooldown（状态门拦截，非证据）
        EngineSkipOutcomeTracker.onRuleEvaluated(r1, "app.one", ".Splash", "Cooldown", false, now)
        EngineSkipOutcomeTracker.settleAll(now + 8_000)
        assertEquals(listOf("result_unknown"), rows.map { it.kind }, "Cooldown 与跨应用页面切换都不是证据")
    }

    /** 场景 12：抑制闸到期后真实未命中恢复（抑制窗边界）。 */
    @Test
    fun suppressionWindowBoundary() {
        var now = 10_000L
        val r = rule()
        EngineSkipOutcomeTracker.onActionSuccess("E1", r, "app.test", ".Splash", now)
        assertTrue(EngineSkipOutcomeTracker.suppressMiss("app.test", now + 11_999), "12s 内抑制")
        assertFalse(EngineSkipOutcomeTracker.suppressMiss("app.test", now + 12_001), "12s 后恢复")
        assertFalse(EngineSkipOutcomeTracker.suppressMiss("app.other", now + 1), "其他应用不受抑制")
    }

    /** 有界性：表满时最旧条目被淘汰（不无界保存事件）。 */
    @Test
    fun boundedEviction() {
        installSink()
        var now = 10_000L
        val rules = (1..40).map { i -> rule("app.$i") }
        rules.forEachIndexed { i, r ->
            EngineSkipOutcomeTracker.onActionSuccess("E$i", r, "app.$i", ".Splash", now + i)
        }
        // 前 8 个被淘汰（40 - 32 上限），淘汰即结算（无证据 → result_unknown）
        val settled = rows.count { it.kind == "result_unknown" }
        assertTrue(settled >= 8, "淘汰条目应结算，实际 $settled（rows=${rows.size}）")
    }
}
