package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import li.gkd.app.error.displayMessage
import li.gkd.app.platform.PackageInstallCapability
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState

    data object Active : UpdateInstallState

    data object Success : UpdateInstallState

    data class Failure(val exception: Exception) : UpdateInstallState
}

sealed interface UpdateDownloadState {
    /**
     * v8.1.1：富下载状态（速度/已下载/当前源/续传/切换中）——旧调用方仅传 progress，
     * 其余字段默认值向后兼容。
     */
    data class Loading(
        val progress: Float,
        val receivedBytes: Long = 0,
        val totalBytes: Long = 0,
        val bytesPerSecond: Long = 0,
        val sourceLabel: String = "",
        val sourceIndex: Int = 0,
        val sourceCount: Int = 0,
        val resuming: Boolean = false,
        val switchingFrom: String? = null,
        val switchingTo: String? = null,
        val switchingReason: String? = null,
    ) : UpdateDownloadState

    /** v8.1.1：正在校验（大小 + SHA-256）——不再伪装成「下载中」。 */
    data object Verifying : UpdateDownloadState

    /** v8.1.1：失败带已分类原因（Connection reset / DNS / 超时 / 校验失败…）。 */
    data class Failure(val exception: Exception, val reason: String? = null) : UpdateDownloadState

    data object Success : UpdateDownloadState
}

@Composable
fun GkUpgradeDialogs(
    text: String?,
    allowIgnore: Boolean,
    downloadStatus: UpdateDownloadState?,
    onDownload: () -> Unit,
    onDismissVersion: () -> Unit,
    onIgnore: () -> Unit,
    onBackup: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownload: () -> Unit,
    // v8.0.9：下载失败一键重试（保留失败版本，重新走完整镜像链 + SHA-256 校验）。
    onRetryDownload: (() -> Unit)? = null,
    onInstall: () -> Unit,
    installCapability: PackageInstallCapability,
    installState: UpdateInstallState,
    downloadedVersion: String?,
    onInternalInstall: () -> Unit,
) {
    if (text != null) {
        val scrollState = rememberScrollState()
        GkAlertDialog(
            title = {
                UpdateDialogTitle(
                    title = stringResource(Res.string.update_new_version),
                    onClose = onDismissVersion,
                )
            },
            text = {
                Text(
                    text = text,
                    modifier =
                        Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(scrollState),
                )
            },
            onDismissRequest = {},
            buttons = {
                UpdateVersionButtons(
                    allowIgnore = allowIgnore,
                    onBackup = onBackup,
                    onIgnore = onIgnore,
                    onDownload = onDownload,
                )
            },
        )
    }
    downloadStatus?.let { downloadStatusVal ->
        when (downloadStatusVal) {
            is UpdateDownloadState.Loading -> {
                GkAlertDialog(
                    title = { Text(text = stringResource(Res.string.image_downloading)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LinearProgressIndicator(
                                progress = { downloadStatusVal.progress },
                            )
                            val received = downloadStatusVal.receivedBytes
                            val total = downloadStatusVal.totalBytes
                            if (total > 0) {
                                Text(
                                    text = "${formatBytes(received)} / ${formatBytes(total)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            val speed = downloadStatusVal.bytesPerSecond
                            if (speed > 0) {
                                val eta =
                                    if (total > received && speed > 1_024) {
                                        " · 预计剩余 ${formatDuration((total - received) / speed)}"
                                    } else {
                                        ""
                                    }
                                Text(
                                    text = "${formatBytes(speed)}/s$eta",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            // 当前下载源（含序号）与续传标识
                            if (downloadStatusVal.sourceLabel.isNotEmpty()) {
                                val sourceLine =
                                    buildString {
                                        append(downloadStatusVal.sourceLabel)
                                        if (downloadStatusVal.sourceCount > 1) {
                                            append("（${downloadStatusVal.sourceIndex + 1}/${downloadStatusVal.sourceCount}）")
                                        }
                                        if (downloadStatusVal.resuming) append(" · 断点续传中")
                                    }
                                Text(
                                    text = sourceLine,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            // 正在切换源（含分类原因）
                            val switching = downloadStatusVal.switchingTo
                            if (switching != null) {
                                Text(
                                    text =
                                        "正在切换下载源：${downloadStatusVal.switchingFrom.orEmpty()} → $switching\n" +
                                            "原因：${downloadStatusVal.switchingReason.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onCancelDownload()
                            },
                        ) {
                            Text(text = stringResource(Res.string.download_abort))
                        }
                    },
                )
            }

            UpdateDownloadState.Verifying -> {
                GkAlertDialog(
                    title = { Text(text = stringResource(Res.string.download_verifying)) },
                    text = {
                        Text(text = stringResource(Res.string.download_verifying_description))
                    },
                    onDismissRequest = {},
                )
            }

            is UpdateDownloadState.Failure -> {
                GkAlertDialog(
                    title = { Text(text = stringResource(Res.string.download_failed)) },
                    text = {
                        Text(
                            text =
                                downloadStatusVal.reason
                                    ?: downloadStatusVal.exception.let {
                                        it.message ?: it.toString()
                                    },
                        )
                    },
                    onDismissRequest = { onDismissDownload() },
                    dismissButton =
                        onRetryDownload?.let { retry ->
                            {
                                TextButton(onClick = retry) {
                                    Text(text = stringResource(Res.string.download_retry))
                                }
                            }
                        },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDismissDownload()
                            },
                        ) {
                            Text(text = stringResource(Res.string.action_close))
                        }
                    },
                )
            }

            UpdateDownloadState.Success -> {
                val installing = installState is UpdateInstallState.Active
                val installed = installState == UpdateInstallState.Success
                GkAlertDialog(
                    title = {
                        UpdateDialogTitle(
                            title =
                                stringResource(
                                    when (installState) {
                                        UpdateInstallState.Success -> Res.string.update_installed
                                        is UpdateInstallState.Failure ->
                                            Res.string.update_install_failed
                                        UpdateInstallState.Idle,
                                        UpdateInstallState.Active -> Res.string.download_complete
                                    },
                                ),
                            onClose = onDismissDownload,
                            closeEnabled = !installing,
                        )
                    },
                    text = {
                        Column(
                            modifier =
                                Modifier.heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text =
                                    buildString {
                                        downloadedVersion?.let { append("v$it\n\n") }
                                        append(
                                            when (installState) {
                                                UpdateInstallState.Success ->
                                                    stringResource(
                                                        Res.string.update_installed_description,
                                                    )
                                                is UpdateInstallState.Failure ->
                                                    installState.exception.displayMessage()
                                                UpdateInstallState.Idle,
                                                UpdateInstallState.Active ->
                                                    stringResource(
                                                        Res.string.update_ready_to_install,
                                                    )
                                            },
                                        )
                                    },
                            )
                        }
                    },
                    onDismissRequest = { if (!installing) onDismissDownload() },
                    textToButtonsSpacing = if (installed) 0.dp else null,
                    confirmButton = {
                        if (!installed) {
                            TextButton(onClick = onInstall, enabled = !installing) {
                                Text(
                                    text =
                                        stringResource(
                                            if (
                                                installCapability ==
                                                    PackageInstallCapability.Available
                                            )
                                                Res.string.update_system_install
                                            else Res.string.action_install,
                                        ),
                                )
                            }
                            if (installCapability == PackageInstallCapability.Available) {
                                TextButton(onClick = onInternalInstall, enabled = !installing) {
                                    Text(
                                        text =
                                            stringResource(
                                                if (installing) Res.string.update_installing
                                                else Res.string.action_install,
                                            ),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%d KB".format(bytes / 1024)
        else -> "$bytes B"
    }

private fun formatDuration(seconds: Long): String =
    when {
        seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds}秒"
    }

@Composable
private fun UpdateDialogTitle(
    title: String,
    onClose: () -> Unit,
    closeEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        GkIconButton(
            imageVector = GkIcons.Close,
            onClick = onClose,
            modifier = Modifier.size(48.dp),
            enabled = closeEnabled,
            contentDescription = stringResource(Res.string.action_close),
        )
    }
}

@Composable
private fun UpdateVersionButtons(
    allowIgnore: Boolean,
    onBackup: () -> Unit,
    onIgnore: () -> Unit,
    onDownload: () -> Unit,
) {
    val backup = stringResource(Res.string.backup_action)
    val ignore = stringResource(Res.string.action_ignore)
    val download = stringResource(Res.string.update_download)
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    val layoutDirection = LocalLayoutDirection.current
    val buttonWidths =
        with(LocalDensity.current) {
            listOf(
                    backup to ButtonDefaults.TextButtonContentPadding,
                    ignore to ButtonDefaults.TextButtonContentPadding,
                    download to ButtonDefaults.ContentPadding,
                )
                .map { (label, padding) ->
                    maxOf(
                        ButtonDefaults.MinWidth,
                        textMeasurer.measure(label, textStyle).size.width.toDp() +
                            padding.calculateLeftPadding(layoutDirection) +
                            padding.calculateRightPadding(layoutDirection),
                    )
                }
        }
    val visibleWidths = if (allowIgnore) buttonWidths else listOf(buttonWidths[0], buttonWidths[2])
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val singleRow =
            visibleWidths.fold(0.dp) { sum, width -> sum + width } +
                8.dp * (visibleWidths.size - 1) <= maxWidth
        val columns =
            when {
                singleRow -> visibleWidths.size
                visibleWidths.max() * 2 + 8.dp <= maxWidth -> 2
                else -> 1
            }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = columns,
        ) {
            val buttonModifiers = buttonWidths.map {
                if (singleRow) Modifier.width(it) else Modifier.weight(1f)
            }
            TextButton(
                onClick = onBackup,
                modifier = buttonModifiers[0],
            ) {
                Text(backup)
            }
            if (allowIgnore) {
                TextButton(
                    onClick = onIgnore,
                    modifier = buttonModifiers[1],
                ) {
                    Text(ignore)
                }
            }
            if (!singleRow && columns == 2) {
                Spacer(Modifier.weight(1f))
            }
            Button(
                onClick = onDownload,
                modifier = buttonModifiers[2],
            ) {
                Text(download)
            }
        }
    }
}
