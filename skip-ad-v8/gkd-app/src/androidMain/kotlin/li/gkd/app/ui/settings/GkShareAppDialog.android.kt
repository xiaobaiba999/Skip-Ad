package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import li.gkd.app.META
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.page.GkShareAppDialog
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.ApkUtils
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun UiHost.GkShareAppDialog(
    visible: Boolean,
    dialogs: DialogRequests,
    scope: CoroutineScope,
    onOpenUrl: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = this
    if (visible) {
        val exportPlayTipText =
            stringResource(Res.string.apk_google_services_required) +
                stringResource(Res.string.apk_download_official_link) +
                stringResource(Res.string.apk_continue_suffix)
        GkShareAppDialog(
            onDismiss = onDismissRequest,
            onShare =
                scope.launchUiAction(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (
                            !dialogs.confirm(
                                title = Res.string.share_notice.getSync(),
                                text = exportPlayTipText,
                                confirmText = Res.string.action_continue.getSync(),
                            )
                        )
                            return@launchUiAction
                    }
                    context.shareFile(ApkUtils.createShareFile(), Res.string.apk_share.getSync())
                },
            onSave =
                scope.launchUiAction(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (
                            !dialogs.confirm(
                                title = Res.string.save_notice.getSync(),
                                text = exportPlayTipText,
                                confirmText = Res.string.action_continue.getSync(),
                            )
                        )
                            return@launchUiAction
                    }
                    context.saveFileToDownloads(ApkUtils.createShareFile())
                },
            onPlay = {
                onOpenUrl(AppLinks.Releases)
            },
        )
    }
}
