package li.gkd.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import java.io.File
import kotlinx.coroutines.launch
import li.gkd.app.development.DesktopProfileExport
import li.gkd.app.permission.PermissionRequests
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.PlatformResult
import li.gkd.app.platform.lifecycle.onCreated
import li.gkd.app.platform.lifecycle.useLogLifecycle
import li.gkd.app.platform.lifecycle.useMainActivityLifecycle
import li.gkd.app.resources.*
import li.gkd.app.service.StatusService
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.storage.saveToDownloads
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.androidState
import li.gkd.app.ui.component.LocalTopBarWindowInsets
import li.gkd.app.ui.createMainViewModel
import li.gkd.app.ui.share.ActivityImeController
import li.gkd.app.ui.share.ActivityResultRequests
import li.gkd.app.ui.theme.installThemedSplashScreen
import li.gkd.app.ui.webui.WebUiBridge
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.mapState
import li.gkd.app.util.tryStartActivity
import org.jetbrains.compose.resources.getString

/**
 * 主界面（v8.0.5 · 7.15.14 UI 基座 × GKD 内核）
 * ─────────────────────────────────────────
 * · UI：WebView 装载离线页面 assets/www（7.15.14 原资产：首页/帮助/版本/我的）
 * · 桥接：AndroidApp JS 桥（WebUiBridge）对接 GKD 功能内核（订阅/规则/日志/更新）
 * · URL 白名单：仅放行 file:///android_asset 内页；http(s) 外链一律系统浏览器
 * · 权限/ActivityResult 宿主与 GKD 服务生命周期（StatusService.autoStart）全保留
 */
class MainActivity : ComponentActivity() {
    val mainVm by
        viewModels<MainViewModel> {
            androidx.lifecycle.viewmodel.viewModelFactory {
                initializer { createMainViewModel() }
            }
        }
    val imeController = ActivityImeController(this)
    private val activityResultHost = ActivityResultRequests.Host(this)
    private val permissionRequestHost = PermissionRequests.Host(this)

    /** 7.15.14 WebView UI 宿主：页面就绪后供 JS 桥回调（netCb/evaluateJavascript） */
    private var webView: WebView? = null

    fun webViewOrNull(): WebView? = webView

    init {
        useMainActivityLifecycle()
        useLogLifecycle()
        onCreated {
            lifecycleScope.launch {
                settings
                    .mapState(lifecycleScope) { s -> s.excludeFromRecents }
                    .collect {
                        app.activityManager.appTasks.forEach { task ->
                            task.setExcludeFromRecents(it)
                        }
                    }
            }
        }
    }

    fun shareFile(file: File, title: String) {
        val uri =
            FileProvider.getUriForFile(
                app,
                "${app.packageName}.provider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        tryStartActivity(Intent.createChooser(intent, title))
    }

    suspend fun saveFileToDownloads(file: File): PlatformResult<Boolean> {
        if (
            !mainVm.androidState.permissionRequests.ensurePermissions(
                PermissionStates.writeExternalStorage,
            )
        ) {
            return PlatformResult.Success(false)
        }
        val result = saveToDownloads(file)
        if (result !is PlatformResult.Success) {
            ToastUtils.show(getString(Res.string.platform_action_unsupported))
            return PlatformResult.Unsupported
        }
        val savedName = result.value
        ToastUtils.show(getString(Res.string.file_saved_to_downloads, savedName))
        return PlatformResult.Success(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installThemedSplashScreen()
        enableEdgeToEdge()
        fixTransparentNavigationBar()
        super.onCreate(savedInstanceState)
        activityResultHost.bind(mainVm.androidState.activityResults)
        permissionRequestHost.bind(mainVm.androidState.permissionRequests)
        mainVm.registerCurrent()
        addOnNewIntentListener {
            if (!DesktopProfileExport.handle(this@MainActivity, it))
                mainVm.androidState.handleIntent(it)
            MainActivityIntents.consume(this)
        }
        StatusService.autoStart()
        setContent {
            // 7.15.14 WebView UI（绿色主题底色与 style.css --bg 对齐）
            AndroidView(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        setBackgroundColor(0xFFF2F7F3.toInt())
                        addJavascriptInterface(WebUiBridge(this@MainActivity), "AndroidApp")
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url.toString()
                                return when {
                                    // 内页：继续加载（唯一允许的本地来源）
                                    url.startsWith("file:///android_asset/") -> false
                                    // 外链：一律系统浏览器（JS 桥零远程暴露）
                                    url.startsWith("http://") || url.startsWith("https://") -> {
                                        try {
                                            app.tryStartActivity(
                                                Intent(Intent.ACTION_VIEW, request.url)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        } catch (_: Throwable) {
                                        }
                                        true
                                    }

                                    else -> true // 其他 scheme 一律拦截
                                }
                            }
                        }
                        loadUrl("file:///android_asset/www/index.html")
                        this@MainActivity.webView = this
                    }
                },
            )
            LaunchedEffect(Unit) {
                MainActivityIntents.pending(intent)?.let {
                    if (!DesktopProfileExport.handle(this@MainActivity, it))
                        mainVm.androidState.handleIntent(it)
                    MainActivityIntents.consume(this@MainActivity)
                }
            }
        }
    }

    override fun onPause() {
        // 后台功耗：退后台暂停 WebView 布局与全局 JS 定时器（首页 5s/3s setInterval 不再唤醒 CPU）
        webView?.onPause()
        webView?.pauseTimers()
        super.onPause()
    }

    override fun onResume() {
        webView?.resumeTimers()
        webView?.onResume()
        super.onResume()
    }

    override fun onDestroy() {
        webView?.let { wv ->
            try {
                wv.removeJavascriptInterface("AndroidApp")
                wv.destroy()
            } catch (_: Throwable) {
            }
        }
        webView = null
        super.onDestroy()
    }

    private fun fixTransparentNavigationBar() {
        // 修复在浅色主题下导航栏背景不透明的问题
        if (AndroidTarget.Q) {
            window.isNavigationBarContrastEnforced = false
        } else {
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
    }
}

private object MainActivityIntents {
    private val consumed = Intent()

    fun pending(intent: Intent?): Intent? = intent?.takeUnless { it === consumed }

    fun consume(activity: MainActivity) {
        activity.intent = consumed
    }
}
