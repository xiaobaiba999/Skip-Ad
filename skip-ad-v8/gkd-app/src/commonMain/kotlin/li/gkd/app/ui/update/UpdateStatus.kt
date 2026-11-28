package li.gkd.app.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.backup.BackupManager
import li.gkd.app.error.displayMessage
import li.gkd.app.network.DownloadFailures
import li.gkd.app.network.NewVersion
import li.gkd.app.network.UpdateClient
import li.gkd.app.network.isNetworkRequestAllowed
import li.gkd.app.network.logNetworkFailure
import li.gkd.app.platform.PackageInstallCapability
import li.gkd.app.platform.PlatformResult
import li.gkd.app.platform.installPackagePrivileged
import li.gkd.app.platform.privilegedPackageInstallCapability
import li.gkd.app.platform.requestPackageInstall
import li.gkd.app.platform.verifyApkSignature
import li.gkd.app.resources.*
import li.gkd.app.settings.FileSettingsStorage
import li.gkd.app.storage.FileExports
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.storage.appStorage
import li.gkd.app.storage.canSaveToDownloadsWithoutPermissionRequest
import li.gkd.app.storage.saveToDownloads
import li.gkd.app.ui.component.GkUpgradeDialogs
import li.gkd.app.ui.component.UpdateDownloadState
import li.gkd.app.ui.component.UpdateInstallState
import li.gkd.app.ui.home.importAppBackup
import li.gkd.app.ui.home.saveAppBackup
import li.gkd.app.ui.home.shareAppBackup
import li.gkd.app.ui.page.BackupDialog
import li.gkd.app.ui.page.GkBackupDialogs
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

/** App-owned update workflow; only package installation belongs to the platform. */
class UpdateStatus(
    private val scope: CoroutineScope,
) {
    private val storage by lazy { FileSettingsStorage(appStorage().store) }
    private val directory
        get() = appStorage().sharedCache

    val checkUpdatingFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    private val newVersion = MutableStateFlow<NewVersion?>(null)
    private val downloadStatus = MutableStateFlow<UpdateDownloadState?>(null)
    private var downloadJob: Job? = null
    private var downloaded: File? = null
    private var downloadedVersion: NewVersion? = null
    private var manualBackupSaved = false
    private val installState = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    private val installCapability = privilegedPackageInstallCapability()

    private var lastManual = false
    private var lastCheckTime = 0L
    private val ignoreMutex = Mutex()

    // v8.0.9：下载失败时保留目标版本——失败对话框「重试」直接重走完整镜像链。
    private var retryableVersion: NewVersion? = null

    private fun readIgnored(): Set<Int> =
        storage
            .read("ignore_version_list.json")
            ?.let { Json.decodeFromString<Set<Int>>(it) }
            .orEmpty()

    val canRecheck
        get() = System.currentTimeMillis() - lastCheckTime > 86_400_000L

    fun checkUpdate(manual: Boolean = false) {
        if (checkUpdatingFlow.value) return
        checkUpdatingFlow.value = true
        scope.launch {
            try {
                lastCheckTime = System.currentTimeMillis()
                val ignored =
                    if (manual) emptySet()
                    else ignoreMutex.withLock { withContext(Dispatchers.IO) { readIgnored() } }
                if (!isNetworkRequestAllowed()) {
                    if (manual) ToastUtils.show(getString(Res.string.network_unavailable))
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { UpdateClient.fetchUpdate() }
                if (result.versionCode <= META.versionCode) {
                    if (manual) ToastUtils.show(getString(Res.string.updates_none))
                } else if (manual || result.versionCode !in ignored) {
                    lastManual = manual
                    manualBackupSaved = false
                    newVersion.value = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logNetworkFailure("Update check failed", e)
                // v8.0.9：多源全败时明确报「检查更新失败」（带原因），绝不伪装成
                // 「已是最新版本」；手动入口随时可重试。
                if (manual) {
                    ToastUtils.show(getString(Res.string.update_check_failed, e.displayMessage()))
                }
            } finally {
                checkUpdatingFlow.value = false
            }
        }
    }

    private fun startDownload(version: NewVersion) {
        if (installState.value is UpdateInstallState.Active) return
        if (downloadJob?.isActive == true) return
        newVersion.value = null
        retryableVersion = version
        downloadStatus.value = UpdateDownloadState.Loading(0f)
        downloaded = null
        downloadedVersion = null
        installState.value = UpdateInstallState.Idle
        val file = directory.resolve("adshield-v${version.versionCode}.apk")
        downloadJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    directory.mkdirs()
                    val transfer = currentCoroutineContext()
                    // v8.1.1：断点续传多源下载（Range + ETag + 停滞闸 + 失败自动切换；
                    // 取消保留断点，重试从断点继续；完整性全过才落位）。
                    UpdateClient.downloadResumable(version, file) { p ->
                        if (transfer.isActive) {
                            if (p.verifying) {
                                downloadStatus.value = UpdateDownloadState.Verifying
                                return@downloadResumable
                            }
                            val fraction =
                                if (p.total > 0) {
                                    (p.received.toFloat() / p.total).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            downloadStatus.value =
                                UpdateDownloadState.Loading(
                                    progress = fraction,
                                    receivedBytes = p.received,
                                    totalBytes = p.total,
                                    bytesPerSecond = p.bytesPerSecond,
                                    sourceLabel = p.sourceLabel,
                                    sourceIndex = p.sourceIndex,
                                    sourceCount = p.sourceCount,
                                    resuming = p.resuming,
                                    switchingFrom = p.switchingFrom,
                                    switchingTo = p.switchingTo,
                                    switchingReason = p.switchingReason,
                                )
                        }
                    }
                }
                ensureActive()
                downloaded = file
                downloadedVersion = version
                retryableVersion = null
                downloadStatus.value = UpdateDownloadState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // v8.1.1：失败原因分类（Connection reset / DNS / 超时 / 校验失败…）
                downloadStatus.value =
                    UpdateDownloadState.Failure(e, DownloadFailures.classify(e))
            }
        }
    }

    private fun ignoreVersion() {
        val version = newVersion.value ?: return
        scope.launch {
            try {
                ignoreMutex.withLock {
                    withContext(Dispatchers.IO) {
                        val next = readIgnored() + version.versionCode
                        storage.writeAtomically(
                            "ignore_version_list.json",
                            Json.encodeToString(next),
                        )
                    }
                    if (newVersion.value == version) newVersion.value = null
                    ToastUtils.show(getString(Res.string.update_version_ignored))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastUtils.show(e.displayMessage())
            }
        }
    }

    private fun install(host: UiHost, privileged: Boolean) {
        if (installState.value is UpdateInstallState.Active) return
        val file = downloaded ?: return
        val version = downloadedVersion ?: return
        installState.value = UpdateInstallState.Active
        scope.launch {
            try {
                // v8.0.9：安装前签名预校验——APK 签名证书必须与当前运行版一致
                // （系统安装器最终也会拒签，但提前明确报错优于晦涩的系统失败；
                // 不一致的包直接删档，避免坏档滞留共享缓存）。
                try {
                    verifyApkSignature(file)
                } catch (e: SecurityException) {
                    withContext(NonCancellable + Dispatchers.IO) { file.delete() }
                    downloaded = null
                    throw e
                }
                if (!manualBackupSaved && canSaveToDownloadsWithoutPermissionRequest()) {
                    try {
                        FileExports.withTemporaryFile(
                            create = BackupManager::exportData,
                            delete = { StorageMaintenance.deleteSharedFile(appStorage(), it) },
                            consume = { backup ->
                                when (val saved = saveToDownloads(backup)) {
                                    is PlatformResult.Success -> {
                                        ToastUtils.show(
                                            getString(
                                                Res.string.file_saved_to_downloads,
                                                saved.value,
                                            ),
                                        )
                                    }
                                    PlatformResult.Unsupported -> Unit
                                }
                            },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Backup failure must not prevent installing the update.
                    }
                }
                val result =
                    if (privileged) {
                        installPackagePrivileged(file, version.versionCode, host)
                    } else {
                        requestPackageInstall(file)
                    }
                when (result) {
                    is PlatformResult.Success ->
                        installState.value =
                            if (privileged) UpdateInstallState.Success else UpdateInstallState.Idle
                    PlatformResult.Unsupported -> {
                        installState.value = UpdateInstallState.Idle
                        ToastUtils.show(getString(Res.string.platform_action_unsupported))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                installState.value = UpdateInstallState.Failure(e)
            }
        }
    }

    @Composable
    fun UpgradeDialog() {
        val host = LocalUiHost.current
        val version by newVersion.collectAsStateWithLifecycle()
        var backupDialog by remember(version) { mutableStateOf<BackupDialog?>(null) }
        val status by downloadStatus.collectAsStateWithLifecycle()
        val installation by installState.collectAsStateWithLifecycle()
        val capability by
            installCapability.collectAsStateWithLifecycle(PackageInstallCapability.Checking)
        val text = version?.let { value ->
            // v8.1.1：下载前明确文件大小（大文件决策依据；未知时不误导）
            val sizeLine =
                if (value.apkSize > 0) "\n\nAPK 大小：${"%.1f MB".format(value.apkSize / (1024.0 * 1024))}" else ""
            "v${META.versionName} -> v${value.versionName}$sizeLine\n\n${value.releaseNotes.orEmpty()}"
                .trimEnd()
        }
        GkUpgradeDialogs(
            text,
            !lastManual,
            status,
            onDownload = { version?.let(::startDownload) },
            onDismissVersion = { newVersion.value = null },
            onIgnore = ::ignoreVersion,
            onBackup = { backupDialog = BackupDialog.Export },
            onCancelDownload = {
                downloadJob?.cancel()
                downloadStatus.value =
                    UpdateDownloadState.Failure(Exception(Res.string.download_abort.getSync()))
            },
            onRetryDownload = {
                // v8.0.9：下载失败一键重试（保留的目标版本重走完整镜像链 + 校验）。
                retryableVersion?.let(::startDownload)
            },
            onDismissDownload = { downloadStatus.value = null },
            installCapability = capability,
            installState = installation,
            downloadedVersion = downloadedVersion?.versionName,
            onInternalInstall = { install(host, privileged = true) },
            onInstall = { install(host, privileged = false) },
        )
        GkBackupDialogs(
            dialog = backupDialog,
            onDismiss = { backupDialog = null },
            onExport = { backupDialog = BackupDialog.Export },
            onImport = host::importAppBackup,
            onShare = host::shareAppBackup,
            onSaveDownloads = {
                host.saveAppBackup {
                    manualBackupSaved = true
                }
            },
        )
    }
}
