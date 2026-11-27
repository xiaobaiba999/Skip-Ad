package li.gkd.app.upload

import kotlin.test.assertTrue
import kotlinx.coroutines.withTimeout
import li.gkd.app.META
import li.gkd.app.record.EngineMatchMissRecorder
import li.gkd.app.record.RuntimeRecordRepository
import li.gkd.app.record.ActionRecordInput
import li.gkd.app.record.EngineEventDetail
import li.gkd.db.Db

/**
 * v8.0.8 日志体系专项检查（需隔离应用数据库，随集成测试运行）：
 * 统一报告六节结构、最新触发与详细日志同时存在、失败可见性、
 * 匹配未命中聚合落库与命中清除。纯函数部分（订阅补丁/节流闸）
 * 见 SubscriptionPatchesTest。
 */
suspend fun assertFeedbackReport() {
    assertUnifiedReportSections()
    assertMatchMissRecorder()
}

private suspend fun assertUnifiedReportSections() {
    // 生产路径种子一条成功动作（action_log + 关联详细日志，同事务）
    RuntimeRecordRepository.recordAction(
        ActionRecordInput(
            appId = "li.gkd.test.report",
            activityId = ".MainActivity",
            subsId = 667L,
            subsVersion = 1,
            groupKey = 0,
            groupType = li.gkd.db.RuleGroupType.App,
            ruleIndex = 1,
            ruleKey = 1,
            time = System.currentTimeMillis(),
            engineDetail =
                EngineEventDetail(
                    kind = "action_performed",
                    level = 0,
                    message = "⚡ 执行动作（report · 开屏广告 · r1）：clickNode · 成功",
                    actionType = "clickNode",
                    result = true,
                    durationMs = 12,
                ),
        ),
    )
    // 一条失败动作（v8.0.8 修复：失败也必须可见）
    RuntimeRecordRepository.recordAction(
        ActionRecordInput(
            appId = "li.gkd.test.report",
            activityId = ".AdActivity",
            subsId = 667L,
            subsVersion = 1,
            groupKey = 0,
            groupType = li.gkd.db.RuleGroupType.App,
            ruleIndex = 0,
            ruleKey = 0,
            time = System.currentTimeMillis(),
            engineDetail =
                EngineEventDetail(
                    kind = "action_failed",
                    level = 2,
                    message = "❌ 动作失败（report · 开屏广告 · r0）：clickNode · action_failed",
                    actionType = "clickNode",
                    result = false,
                    reason = "action_failed",
                ),
        ),
    )

    val title = FeedbackLogs.issueTitle("手动上传")
    assertTrue(title.startsWith("[引擎日志][v${META.versionName}] "), "统一标题格式: $title")
    assertTrue(title.endsWith("（手动上传）"), "标题通道后缀: $title")

    val report = FeedbackReport.build("手动上传")
    // 六节结构齐备
    assertTrue(report.contains("## 1. 基本环境"), "基本环境节")
    assertTrue(report.contains("## 2. 最新触发记录"), "最新触发记录节")
    assertTrue(report.contains("## 3. 详细执行记录"), "详细执行记录节")
    assertTrue(report.contains("## 4. 异常与失败"), "异常与失败节")
    assertTrue(report.contains("## 5. 统计摘要"), "统计摘要节")
    assertTrue(report.contains("## 6. 日志说明"), "日志说明节")
    // 最新触发记录段同时包含成功与失败的关联详细消息（actionLogId 关联生效）
    assertTrue(
        report.contains("⚡ 执行动作（report · 开屏广告 · r1）：clickNode · 成功"),
        "最新触发记录包含成功详细消息",
    )
    assertTrue(
        report.contains("❌ 动作失败（report · 开屏广告 · r0）"),
        "最新触发记录包含失败消息（v8.0.8 修复失败可见性）",
    )
    // 详细执行记录段：级别前缀与耗时字段
    assertTrue(report.contains("ℹ️"), "详细记录含 info 前缀")
    assertTrue(report.contains("❌"), "详细记录含 error 前缀")
    assertTrue(report.contains("\"durationMs\":12"), "耗时字段随详细日志序列化")
    // 异常与失败节包含 error 级记录重列
    assertTrue(report.contains("action_failed"), "失败原因在报告中")
    // 统计摘要与说明
    assertTrue(report.contains("应用战绩"), "统计摘要含应用战绩")
    assertTrue(report.contains("双层脱敏"), "日志说明含脱敏说明")
    assertTrue(report.contains("上传通道: 手动上传"), "通道标注")
}

private suspend fun assertMatchMissRecorder() {
    EngineMatchMissRecorder.record(667L, "开屏广告", "com.example.miss", "not_matched", false)
    EngineMatchMissRecorder.record(667L, "开屏广告", "com.example.miss", "node_unavailable", false)
    EngineMatchMissRecorder.record(667L, "开屏广告", "com.example.hit", "not_matched", false)
    // 命中清除窗口：不应产生 miss 记录
    EngineMatchMissRecorder.record(667L, "开屏广告", "com.example.hit", "matched", true)
    EngineMatchMissRecorder.flushAll()
    val rows =
        withTimeout(5_000) {
            while (true) {
                val found = Db.engineEventLogDao.queryLatest(50).filter { it.kind == "match_miss" }
                if (found.isNotEmpty()) return@withTimeout found
                kotlinx.coroutines.delay(50)
            }
            @Suppress("UNREACHABLE_CODE")
            emptyList<li.gkd.db.EngineEventLog>()
        }
    val missRows = rows.filter { it.appId == "com.example.miss" }
    assertTrue(missRows.isNotEmpty(), "未命中窗口落库")
    val miss = missRows.first()
    assertTrue(miss.message.contains("❓ 规则未命中"), "未命中消息前缀: ${miss.message}")
    assertTrue(miss.message.contains("not_matched×1"), "结果分布计数: ${miss.message}")
    assertTrue(miss.message.contains("订阅667·开屏广告"), "涉及组名: ${miss.message}")
    assertTrue(
        rows.none { it.appId == "com.example.hit" },
        "命中应用不产生未命中记录",
    )
}
