package li.gkd.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.logging.LogUtils
import li.gkd.app.logging.logUrl
import li.gkd.app.model.WebViewEnvironment
import li.gkd.app.network.AppLinks
import li.gkd.app.network.NetworkClients
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkWebViewErrorContent
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.page.WebViewScreen
import li.gkd.app.ui.platform.SystemActionFeedback
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.copyText
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun WebViewPage(
    route: WebViewRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val initUrl = route.initUrl
    val state = remember(initUrl) { BrowserState() }
    val dark = LocalDarkTheme.current
    val webView = state.view
    val background = MaterialTheme.colorScheme.background.toArgb()
    val reload = {
        webView?.let { view ->
            val failedUrl = state.failure?.url
            state.failure = null
            state.loading = true
            state.contentVisible = false
            state.title = ""
            if (failedUrl != null) view.loadUrl(failedUrl) else view.reload()
        }
        Unit
    }
    val openExternal = {
        SystemActionFeedback.openExternal(state.failure?.url ?: webView?.url ?: initUrl)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(webView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // v8.0.9：补 pauseTimers/resumeTimers（对齐 MainActivity 语义：
                // 退后台冻结全局 JS 定时器省电，回前台恢复；两调用需成对，
                // Chromium 内部为全局计数）
                Lifecycle.Event.ON_RESUME -> {
                    webView?.resumeTimers()
                    webView?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    webView?.onPause()
                    webView?.pauseTimers()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // The toolbar leaves the route; system Back first traverses browser history.
    BackHandler(state.canGoBack) { state.view?.goBack() }
    WebViewScreen(
        title =
            if (state.failure != null) stringResource(Res.string.webview_load_failed)
            else state.title,
        loading = state.loading,
        onBack = mainVm.navigator::pop,
        onReload = reload,
        onCopyLink = { copyText(state.failure?.url ?: webView?.url ?: initUrl) },
        onOpenExternal = openExternal,
        onCompatibilityNotice =
            if (chromeVersion in 1..<MINI_CHROME_VERSION) {
                mainVm.scope.launchUiAction {
                    mainVm.dialogRequests.showMessage(
                        title = getString(Res.string.compatibility_notice),
                        text = getString(Res.string.webview_outdated_notice, chromeVersion),
                    )
                }
            } else null,
    ) { contentPadding ->
        Box(
            Modifier.fillMaxSize().scaffoldPadding(contentPadding),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        state.view = this
                        state.dark = dark
                        setBackgroundColor(background)
                        visibility = View.INVISIBLE
                        webViewClient = GkdWebViewClient(state)
                        webChromeClient =
                            object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView, title: String?) {
                                    if (state.failure == null) state.title = title.orEmpty()
                                }
                            }
                        addJavascriptInterface(GkdWebViewJsApi(state), "gkd")
                        settings.apply {
                            @SuppressLint("SetJavaScriptEnabled")
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            if (AndroidTarget.TIRAMISU) {
                                setAlgorithmicDarkeningAllowed(false)
                            }
                        }
                        // Load once when creating the native view, never on recomposition.
                        loadUrl(initUrl)
                    }
                },
                update = { view ->
                    if (state.dark != dark) {
                        state.dark = dark
                        view.evaluateJavascript(
                            "window.dispatchEvent(new Event('gkd:themechange'));",
                            null,
                        )
                    }
                    view.setBackgroundColor(background)
                    view.visibility = if (state.contentVisible) View.VISIBLE else View.INVISIBLE
                },
                onRelease = { view ->
                    state.view = null
                    state.canGoBack = false
                    view.stopLoading()
                    view.removeJavascriptInterface("gkd")
                    view.webChromeClient = null
                    view.webViewClient = WebViewClient()
                    // v8.0.9：先从视图树 detach 再 destroy（对齐 MainActivity/
                    // WebViewRelease 销毁协议——destroy 时仍 attach 会致 Surface
                    // 注销时序错乱、退出残影。当前 Android 上此页不可达，防未来
                    // 接回时复发）
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                },
            )
            state.failure?.let { failure ->
                GkWebViewErrorContent(
                    onRetry = reload,
                    onOpenExternal = openExternal,
                    url = failure.url,
                    errorText = failure.code,
                )
            }
        }
    }
}

@Suppress("unused")
private class GkdWebViewJsApi(private val state: BrowserState) {
    @JavascriptInterface fun isDarkTheme(): Boolean = state.dark

    @JavascriptInterface fun getEnvironment(): String = EnvironmentCache.json
}

private object EnvironmentCache {
    val json by lazy {
        WebViewEnvironment(
                platform = "android",
                appId = META.appId,
                appName = META.appName,
                versionCode = META.versionCode,
                versionName = META.versionName,
                channel = META.channel,
                debuggable = META.debuggable,
            )
            .toJson()
    }
}

private const val MINI_CHROME_VERSION = 107
private val chromeVersion by lazy {
    WebView.getCurrentWebViewPackage()?.versionName?.run {
        splitToSequence('.').first().toIntOrNull()
    } ?: 0
}

private data class BrowserFailure(val url: String, val code: String)

private class BrowserState {
    @Volatile var dark = false
    var view by mutableStateOf<WebView?>(null)
    var title by mutableStateOf("")
    var loading by mutableStateOf(true)
    var canGoBack by mutableStateOf(false)
    var failure by mutableStateOf<BrowserFailure?>(null)
    var contentVisible by mutableStateOf(false)

    fun fail(view: WebView, url: String, code: String) {
        failure = BrowserFailure(url, code)
        loading = false
        contentVisible = false
        canGoBack = view.canGoBack()
        // Hide Chromium's error document immediately, before the next Compose frame.
        view.visibility = View.INVISIBLE
    }
}

private class GkdWebViewClient(private val state: BrowserState) : WebViewClient() {
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        state.loading = true
        state.failure = null
        state.title = ""
        state.contentVisible = false
        view.visibility = View.INVISIBLE
        state.canGoBack = view.canGoBack()
    }

    override fun onPageCommitVisible(view: WebView, url: String?) {
        if (state.failure == null) state.contentVisible = true
    }

    override fun onPageFinished(view: WebView, url: String?) {
        state.loading = false
        if (state.failure == null) {
            state.title = view.title.orEmpty()
            state.contentVisible = true
        }
        state.canGoBack = view.canGoBack()
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        state.canGoBack = view.canGoBack()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            state.fail(
                view,
                request.url.toString(),
                error.description.toString().ifBlank { "ERROR_CODE: ${error.errorCode}" },
            )
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame) {
            state.fail(
                view,
                request.url.toString(),
                "HTTP ${errorResponse.statusCode} ${errorResponse.reasonPhrase}".trim(),
            )
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url
        if (uri != null) {
            if (uri.scheme == "gkd") {
                (view?.context as? MainActivity)?.mainVm?.androidState?.handleGkdUri(uri)
            } else {
                SystemActionFeedback.openExternal(uri.toString())
            }
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }


}
