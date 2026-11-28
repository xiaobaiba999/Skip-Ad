package li.gkd.app.upload

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.data.currentDeviceInfo
import li.gkd.app.network.FeedbackUploader
import li.gkd.app.record.EngineSkipOutcomeTracker
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.db.SubscriptionIds

/**
 * v8.1.1 用户意见反馈（统一仓库——App 底栏入口与开屏卫士提示入口共用）。
 *
 * 与「引擎日志上传」的关系：
 * · 两条入口都走本仓库 → FeedbackUploader（GitHub Issue，同双重脱敏 + 频控）；
 * · 反馈类型 + 用户描述 + 环境上下文（版本/订阅/最近跳过结果/设备）必带；
 * · 诊断日志【默认不带】：仅当用户明确勾选「附带诊断日志」才附加
 *   FeedbackReport 完整报告（隐私闸：未授权绝不上传完整诊断信息）；
 * · 上传失败保留草稿（H5 层 localStorage），普通意见反馈不依赖日志上传。
 */
object UserFeedback {
    /** 反馈类型（与 H5 反馈页选项一一对应）。 */
    val TYPES =
        listOf(
            "功能建议",
            "广告没跳过",
            "跳过太慢",
            "误触误跳",
            "更新下载问题",
            "界面问题",
            "其他",
        )

    @Serializable
    data class Input(
        val type: String,
        val title: String,
        val description: String,
        val appPackage: String? = null,
        val includeDiagnosticLogs: Boolean = false,
    )

    /** 最近一次跳过快照（开屏提示入口直达反馈页时预填 + 上下文）。 */
    fun lastSkipSnapshot(now: Long = System.currentTimeMillis()): String? =
        EngineSkipOutcomeTracker.lastSkipSnapshot(now)

    /** Issue 标题：`[意见反馈][v8.1.1] 广告没跳过 · 标题（设备）`。 */
    fun issueTitle(input: Input): String {
        val device =
            runCatching { currentDeviceInfo() }.getOrNull()
                ?.let { "${it.brand} ${it.model}".trim() } ?: "未知设备"
        return "[意见反馈][v${META.versionName}] ${input.type.take(12)} · ${input.title.take(40)}（$device）"
    }

    /** Issue 正文：结构化反馈 + 环境上下文 +（可选）完整诊断报告。 */
    suspend fun buildBody(input: Input): String = buildString {
        appendLine("## 用户反馈")
        appendLine("- 类型：${input.type}")
        appendLine("- 标题：${input.title}")
        appendLine()
        appendLine("## 问题描述")
        appendLine(input.description.ifBlank { "（未填写）" })
        appendLine()
        appendLine("## 环境上下文（自动附加）")
        input.appPackage?.takeIf { it.isNotBlank() }?.let { appendLine("- 相关应用：$it") }
        appendLine("- 开屏卫士版本：v${META.versionName} (${META.versionCode})")
        appendLine("- 订阅来源与版本：${subscriptionSummary()}")
        appendLine("- 最近跳过结果：${lastSkipSnapshot() ?: "本进程暂无跳过记录"}")
        val device = runCatching { currentDeviceInfo() }.getOrNull()
        device?.let {
            appendLine("- 设备：${it.brand} ${it.model}，Android ${it.release} (SDK ${it.sdkInt})")
        }
        if (input.includeDiagnosticLogs) {
            appendLine()
            appendLine("## 诊断日志（用户已明确授权附带）")
            appendLine(FeedbackReport.build("意见反馈"))
        }
    }

    /** 订阅来源摘要：本地 667（Lin-arm…）+ 全局 86/233 版本行。 */
    private fun subscriptionSummary(): String {
        val snapshot = SubscriptionRepository.snapshotFlow.value.value ?: return "未知（未加载）"
        return snapshot.subscriptions.values
            .sortedBy { it.id }
            .joinToString(" · ") { sub ->
                val tag = if (SubscriptionIds.isLocal(sub.id)) "本地#${sub.id}" else "订阅#${sub.id}"
                "$tag ${sub.name} v${sub.version}"
            }
            .ifEmpty { "无订阅" }
    }

    /** 提交（双重脱敏 + 15s 频控由 FeedbackUploader 负责；失败不抛异常）。 */
    suspend fun submit(input: Input): FeedbackUploader.Result {
        if (input.title.isBlank()) {
            return FeedbackUploader.Result.Failure("标题不能为空", null)
        }
        return try {
            FeedbackUploader.createIssue(issueTitle(input), buildBody(input))
        } catch (e: Exception) {
            FeedbackUploader.Result.Failure(e.message ?: "提交异常", null)
        }
    }

    private val payloadJson = Json { ignoreUnknownKeys = true }

    /** H5 桥载荷解析（{"type":..,"title":..,"description":..,"appPackage":..,"includeLogs":bool}）。 */
    fun parsePayload(json: String): Input? =
        runCatching { payloadJson.decodeFromString(Input.serializer(), json) }.getOrNull()
}
