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
 * v8.1.0 规则过滤诊断测试（类 2 停用 / 类 4 不兼容与版本门槛 / 类 5 Activity 不匹配）。
 *
 * 语义：进入评估前被静默过滤的规则首次可诊断——
 * · 停用组（组级 enable=false） → 「已停用组」段；
 * · 不兼容组（选择器编译失败 valid=false） → 「不兼容组」段；
 * · 版本门槛（versionCode 高于已装版本） → 「N 条规则因App版本不满足」段；
 * · Activity 不匹配（activityIds 不含当前页） → 「当前页面不适用 N 条」段；
 * · 全部正常参与评估 → 不产生任何日志。
 *
 * 注意：记录器内部按 appId 共享 EngineRateGate（10 分钟），各测试使用独立
 * appId 隔离节流状态。
 */
class EngineRuleFilterRecorderTest {
    private val logged = mutableListOf<String>()

    @AfterTest
    fun reset() {
        EngineRuleFilterRecorder.sink =
            { kind, message, level, appId, detail ->
                EngineEventLogWriter.log(kind, message, level, appId, null, detail)
            }
    }

    private fun installSink() {
        EngineRuleFilterRecorder.sink = { kind, message, _, _, _ ->
            logged.add("$kind|$message")
        }
    }

    /** 组按需注入；appId 每测试独立（隔离共享节流闸）。 */
    private fun fixture(
        appId: String,
        groupsJson: String,
    ): li.gkd.app.rule.RuleSummary {
        val raw =
            RawSubscriptionParser.parse(
                """{id:667,name:'Test',version:646,
                apps:[{id:'$appId',groups:[$groupsJson]}]}""",
            )
        return RuleSummaryBuilder.build(
            RuleRuntime(now = { 0L }),
            listOf(UsedSubsEntry(SubsItem(667, enable = true, order = 0), raw)),
            mapOf(appId to AppInfo(appId, "Test", 100, "1.0.0", false, 0, false, 0)),
            emptyList(),
            emptyList(),
            emptyList(),
        )
    }

    @Test
    fun disabledGroupIsReported() {
        installSink()
        val summary =
            fixture("app.disabled", "{key:1,name:'开屏广告',enable:false,rules:[{name:'跳过',matches:'*'}]}")
        EngineRuleFilterRecorder.onSnapshot("app.disabled", null, summary, null)
        assertEquals(1, logged.size)
        assertTrue(logged.first().contains("rule_filtered"), "kind 正确")
        assertTrue(logged.first().contains("已停用组[开屏广告]"), "停用组名可见: ${logged.first()}")
    }

    @Test
    fun invalidGroupIsReportedAsIncompatible() {
        installSink()
        // 非法选择器语法（未闭合括号）→ 组编译失败 valid=false → 类 4
        val summary =
            fixture("app.invalid", "{key:1,name:'开屏广告',rules:[{name:'跳过',matches:'[text=\"跳过\"'}]}")
        EngineRuleFilterRecorder.onSnapshot("app.invalid", null, summary, null)
        assertEquals(1, logged.size)
        assertTrue(logged.first().contains("不兼容组[开屏广告]"), "不兼容组名可见: ${logged.first()}")
    }

    @Test
    fun versionFilteredRuleIsReported() {
        installSink()
        // versionCode 门槛高于已装版本(100) → 该规则不参与（info 为 null 时版本检查放行，须传真实 AppInfo）
        val summary =
            fixture(
                "app.version",
                "{key:1,name:'开屏广告',rules:[{name:'跳过',matches:'*',versionCode:{minimum:900}}]}",
            )
        val info = AppInfo("app.version", "Test", 100, "1.0.0", false, 0, false, 0)
        EngineRuleFilterRecorder.onSnapshot("app.version", null, summary, info)
        assertEquals(1, logged.size)
        assertTrue(logged.first().contains("1 条规则因App版本不满足"), "版本门槛可见: ${logged.first()}")
    }

    @Test
    fun activityMismatchIsReported() {
        installSink()
        // 规则只在 .SplashActivity 生效，当前页 .MainActivity → 类 5
        val summary =
            fixture(
                "app.activity",
                "{key:1,name:'开屏广告',activityIds:['.SplashActivity'],rules:[{name:'跳过',matches:'*'}]}",
            )
        EngineRuleFilterRecorder.onSnapshot("app.activity", ".MainActivity", summary, null)
        assertEquals(1, logged.size)
        assertTrue(
            logged.first().contains("当前页面(MainActivity)不适用规则 1 条"),
            "Activity 过滤可见: ${logged.first()}",
        )
    }

    @Test
    fun healthySnapshotLogsNothing() {
        installSink()
        val summary =
            fixture("app.healthy", "{key:1,name:'开屏广告',rules:[{name:'跳过',matches:'*'}]}")
        EngineRuleFilterRecorder.onSnapshot("app.healthy", ".MainActivity", summary, null)
        assertTrue(logged.isEmpty(), "全部正常参与评估时零日志")
    }

    @Test
    fun sameSnapshotNotRepeated() {
        installSink()
        val summary =
            fixture("app.repeat", "{key:1,name:'开屏广告',enable:false,rules:[{name:'跳过',matches:'*'}]}")
        EngineRuleFilterRecorder.onSnapshot("app.repeat", null, summary, null)
        EngineRuleFilterRecorder.onSnapshot("app.repeat", null, summary, null)
        assertEquals(1, logged.size, "同一快照不重复记录")
    }
}
