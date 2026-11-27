package li.gkd.app.upload

import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.data.currentDeviceInfo
import li.gkd.app.logging.LogUtils
import li.gkd.app.network.FeedbackUploader
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.FileSettingsStorage
import li.gkd.app.storage.appStorage
import li.gkd.db.Db

/** Shared feedback report building and auto-upload bookkeeping. */
object FeedbackLogs {
    const val AUTO_UPLOAD_INTERVAL_MS = 8 * 60 * 60 * 1000L
    private const val STATE_FILE = "auto_log_upload.json"

    /** 自动上传单次结果（「无新内容」跳过可识别：不 POST、不建空 Issue）。 */
    sealed interface AutoUploadOutcome {
        data class Uploaded(val url: String, val number: Int) : AutoUploadOutcome

        data class Skipped(val signature: String) : AutoUploadOutcome

        data class Failed(val message: String) : AutoUploadOutcome
    }

    /**
     * v8.0.8：统一 Issue 标题（修复 v8.0.7 自动通道丢失设备信息的问题，对齐 7.15.14）。
     * 例：`[引擎日志][v8.0.8] Xiaomi 15 Pro（8小时自动）`。
     */
    fun issueTitle(channel: String): String {
        val device = runCatching { currentDeviceInfo() }.getOrNull()
        val deviceText =
            if (device != null) "${device.brand} ${device.model}".trim() else "未知设备"
        return "[引擎日志][v${META.versionName}] $deviceText（$channel）"
    }

    private val storage by lazy { FileSettingsStorage(appStorage().store) }
    private val stateJson = Json { ignoreUnknownKeys = true }

    private fun readAutoUploadState(): AutoUploadState {
        val text = storage.read(STATE_FILE) ?: return AutoUploadState()
        // 兼容旧版纯时间戳内容（升级安装后首次读取即迁移为结构化格式）
        text.toLongOrNull()?.let { return AutoUploadState(at = it) }
        return runCatching { stateJson.decodeFromString(AutoUploadState.serializer(), text) }
            .getOrDefault(AutoUploadState())
    }

    private fun writeAutoUploadState(state: AutoUploadState) {
        storage.writeAtomically(STATE_FILE, stateJson.encodeToString(AutoUploadState.serializer(), state))
    }

    val canAutoUploadNow: Boolean
        get() =
            SettingsRepository.settings.value.enableAutoLogUpload &&
                System.currentTimeMillis() - readAutoUploadState().at >= AUTO_UPLOAD_INTERVAL_MS

    /**
     * v8.0.8：统一完整报告（与手动上传同一构建器同一口径）。
     * 内容：基本环境/最新触发记录/详细执行记录/异常与失败/统计摘要/日志说明 六节。
     */
    suspend fun build(): String = FeedbackReport.build("8小时自动")

    /**
     * 报告内容签名（稳定哈希）：触发总数:最新动作时间:详细日志条数:最新详细时间。
     * 任一变化即视为有新内容；与上次成功上传一致则跳过（不建空 Issue）。
     * v8.0.8：加入详细通道最新时间戳（匹配未命中/服务状态等非动作事件也能触发新内容判定）。
     */
    private suspend fun reportSignature(): String {
        val latestActionTime =
            runCatching { Db.actionLogDao.queryLatest(1L).firstOrNull()?.ctime ?: 0L }
                .getOrDefault(0L)
        val detail = runCatching { Db.engineEventLogDao.queryLatest(1).firstOrNull() }.getOrNull()
        val raw =
            "${SettingsRepository.actionCount.value}:$latestActionTime:${detail?.id ?: 0}:${detail?.ctime ?: 0L}"
        return runCatching {
            MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }.getOrDefault(raw.hashCode().toString())
    }

    /** Best-effort background submission; failures are logged and never rethrown. */
    suspend fun autoUploadOnce(): AutoUploadOutcome {
        val report = build()
        val signature = reportSignature()
        if (readAutoUploadState().lastSignature == signature) {
            LogUtils.i("无新内容,跳过自动上传")
            return AutoUploadOutcome.Skipped(signature)
        }
        val result =
            try {
                FeedbackUploader.createIssue(issueTitle("8小时自动"), report)
            } catch (e: Exception) {
                LogUtils.e("auto feedback upload failed", e)
                return AutoUploadOutcome.Failed(e.message ?: e.toString())
            }
        return when (result) {
            is FeedbackUploader.Result.Success -> {
                // 成功才计时：时间戳与 lastSignature 一起持久化
                writeAutoUploadState(AutoUploadState(System.currentTimeMillis(), signature))
                LogUtils.i("auto feedback uploaded: #${result.number}")
                AutoUploadOutcome.Uploaded(result.url, result.number)
            }
            is FeedbackUploader.Result.Failure -> {
                LogUtils.w("auto feedback upload failed: ${result.message}")
                AutoUploadOutcome.Failed(result.message)
            }
        }
    }
}

/** auto_log_upload.json 持久化结构：上次成功上传的时间戳 + 报告签名。 */
@Serializable
private data class AutoUploadState(
    val at: Long = 0L,
    val lastSignature: String? = null,
)
