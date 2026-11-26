package li.gkd.app.upload

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.data.currentDeviceInfo
import li.gkd.app.logging.LogUtils
import li.gkd.app.network.FeedbackUploader
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.FileSettingsStorage
import li.gkd.app.state.EngineRuntimeCounters
import li.gkd.app.storage.appStorage
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.db.Db
import li.gkd.db.SubscriptionIds

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

    private val storage by lazy { FileSettingsStorage(appStorage().store) }
    private val stateJson = Json { ignoreUnknownKeys = true }

    private val timestampFormat =
        DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

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

    /** Diagnostics snapshot; masking runs again on submission as defense in depth. */
    suspend fun build(): String = buildString {
        appendLine("### 设备与应用")
        appendLine("- 应用: ${META.appName} v${META.versionName} (${META.versionCode})")
        appendLine("- 通道: ${META.channel}")
        val device = currentDeviceInfo()
        appendLine(
            "- 设备: ${device.brand} ${device.model} (${device.device}), Android ${device.release} (SDK ${device.sdkInt})",
        )
        appendLine("- 触发总数: ${SettingsRepository.actionCount.value}")
        // 能耗画像（与 WebView 端 engineProfile() 能耗行对齐）：真实计数 + 旧版系数估算
        val events = EngineRuntimeCounters.eventCount()
        val scans = EngineRuntimeCounters.scanCount()
        // 能耗估算系数沿用旧版 7.15.14 PowerGovernor（事件×0.22 + 扫描×0.72 µWh，非实测）
        val microWh = events * 22 / 100 + scans * 72 / 100
        appendLine("- 能耗画像(进程本次启动以来):事件 $events · 扫描 $scans · 手势能耗估算 $microWh µWh(系数估算,非实测)")
        appendLine()
        appendLine("### 订阅状态")
        runCatching {
            val snapshot = SubscriptionRepository.awaitSnapshot()
            snapshot.subscriptions.values
                .sortedBy { it.id }
                .forEach { subs ->
                    val state = if (SubscriptionIds.isLocal(subs.id)) "本地" else "远程"
                    appendLine("- [$state] ${subs.name} v${subs.version} (id=${subs.id})")
                }
        }
        appendLine()
        appendLine("### 最近触发记录（最多 30 条）")
        runCatching {
            Db.actionLogDao.queryLatest(30L).forEach { log ->
                val time = timestampFormat.format(Instant.ofEpochMilli(log.ctime))
                appendLine("- $time ${log.appId} group=${log.groupKey} rule=${log.ruleIndex}")
            }
        }
    }

    /**
     * 报告内容签名（稳定哈希）：触发总数:最新动作日志时间戳:详细日志条数。
     * 任一变化即视为有新内容；与上次成功上传一致则跳过（不建空 Issue）。
     */
    private suspend fun reportSignature(): String {
        val latestActionTime =
            runCatching { Db.actionLogDao.queryLatest(1L).firstOrNull()?.ctime ?: 0L }
                .getOrDefault(0L)
        val detailCount = runCatching { Db.engineEventLogDao.count().first() }.getOrDefault(0)
        val raw = "${SettingsRepository.actionCount.value}:$latestActionTime:$detailCount"
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
                FeedbackUploader.createIssue("自动日志 ${META.appName} v${META.versionName}", report)
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
