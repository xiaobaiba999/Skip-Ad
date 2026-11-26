package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.resources.*
import li.gkd.app.storage.createLogArchive
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.saveArchive
import li.gkd.app.ui.share.shareArchive
import org.jetbrains.compose.resources.getString
import li.gkd.db.MatchDiagnosticRecord

class ShareLogState(
    private val scope: CoroutineScope,
) {
    private val diagnostics = MutableStateFlow<List<MatchDiagnosticRecord>?>(null)

    fun show(diagnostics: List<MatchDiagnosticRecord> = emptyList()) {
        this.diagnostics.value = diagnostics.toList()
    }

    private fun dismiss() {
        diagnostics.value = null
    }

    @Composable
    fun Render(host: UiHost) {
        val requestedDiagnostics by diagnostics.collectAsStateWithLifecycle()
        requestedDiagnostics?.let { targets ->
            GkShareLogDialog(
                onDismiss = ::dismiss,
                onShare = {
                    dismiss()
                    scope.launchUi {
                        host.shareArchive(getString(Res.string.logs_share_file)) {
                            createLogArchive(targets)
                        }
                    }
                },
                onSave = {
                    dismiss()
                    scope.launchUi { host.saveArchive { createLogArchive(targets) } }
                },
            )
        }
    }
}
