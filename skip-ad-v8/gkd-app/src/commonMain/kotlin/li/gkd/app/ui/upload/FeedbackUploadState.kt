package li.gkd.app.ui.upload

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.network.AppLinks
import li.gkd.app.network.FeedbackUploader
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.share.launchUi
import li.gkd.app.upload.FeedbackLogs
import li.gkd.app.upload.FeedbackReport
import org.jetbrains.compose.resources.stringResource

/**
 * Manual feedback issue upload, preserving the Guardian log report feature. The report is built
 * by the unified [FeedbackReport] builder (identical to the automatic channel), sanitized on
 * submission, and posted as a GitHub issue of the project repository; automatic uploads run from
 * the application scope.
 */
class FeedbackUploadState(private val scope: CoroutineScope) {
    val uploadingFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val resultFlow = MutableStateFlow<FeedbackUploader.Result?>(null)

    fun upload(@Suppress("UNUSED_PARAMETER") titlePrefix: String = "") {
        if (uploadingFlow.value) return
        uploadingFlow.value = true
        scope.launchUi {
            try {
                val result =
                    FeedbackUploader.createIssue(
                        // v8.0.8：与自动上传同格式的统一标题（含设备信息与版本）
                        FeedbackLogs.issueTitle("手动上传"),
                        FeedbackReport.build("手动上传"),
                    )
                resultFlow.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                resultFlow.value = FeedbackUploader.Result.Failure(e.message ?: "upload failed", null)
            } finally {
                uploadingFlow.value = false
            }
        }
    }

    fun dismissResult() {
        resultFlow.value = null
    }

    @Composable
    fun Render(openUrl: (String) -> Unit) {
        val result by resultFlow.collectAsStateWithLifecycle()
        result?.let { value ->
            val text =
                when (value) {
                    is FeedbackUploader.Result.Success ->
                        stringResource(Res.string.upload_result_success, value.number.toString())
                    is FeedbackUploader.Result.Failure -> value.message
                }
            val url =
                when (value) {
                    is FeedbackUploader.Result.Success -> value.url
                    is FeedbackUploader.Result.Failure -> value.fallbackUrl ?: AppLinks.Issues
                }
            GkAlertDialog(
                onDismissRequest = ::dismissResult,
                title = { Text(text = stringResource(Res.string.upload_result_title)) },
                text = { Text(text = text) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dismissResult()
                            if (url.isNotEmpty()) openUrl(url)
                        },
                    ) {
                        Text(text = stringResource(Res.string.action_open))
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::dismissResult) {
                        Text(text = stringResource(Res.string.action_cancel))
                    }
                },
            )
        }
    }
}
