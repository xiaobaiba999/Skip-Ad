package li.gkd.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import li.gkd.app.ui.webui.WebViewRelease
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
            // v8.0.8：首帧底色兑底（与 windowBackground / WebView 底色三色一致 #F2F7F3）——
            // WebView 首个合成帧异步就绪前，Compose 首帧即绘制品牌底色，窗口不再真空闪黑
            Box(
                modifier =
                    androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color(0xFFF2F7F3)),
            ) {
                // 7.15.14 WebView UI（绿色主题底色与 style.css --bg 对齐）
                AndroidView(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            setBackgroundColor(0xFFF2F7F3.toInt())
                            // v8.0.9 首帧门控（移植 WebViewPage.android.kt 模式）：
                            // 创建即 VISIBLE 时，SurfaceView 首个合成帧就绪前会闪现
                            // 旧 buffer（上一会话残留帧）→ 进入残影。先 INVISIBLE，
                            // onPageCommitVisible 置 VISIBLE；门控期间由 v8.0.8 的
                            // Compose Box #F2F7F3 背景兑底，背景色链三色一致无真空帧
                            visibility = View.INVISIBLE
                            addJavascriptInterface(WebUiBridge(this@MainActivity), "AndroidApp")
                            webViewClient = object : WebViewClient() {
                                // v8.0.8：主帧加载失败兑底：本地 asset 加载失败自动重试一次；
                                // 仍失败则展示可读错误页（广告跳过不受影响），不再永久空白
                                private var retried = false

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (!request.isForMainFrame) return
                                    if (!retried) {
                                        retried = true
                                        // v8.0.9：post 排队期间 WebView 可能已被销毁
                                        // （onRelease/退出）——仅当仍是当前存活实例时
                                        // 重试；destroy 后 loadUrl 会抛异常
                                        view.post {
                                            try {
                                                if (this@MainActivity.webViewOrNull() === view) {
                                                    view.loadUrl("file:///android_asset/www/index.html")
                                                }
                                            } catch (_: Throwable) {
                                            }
                                        }
                                        return
                                    }
                                    view.loadDataWithBaseURL(
                                        null,
                                        "<html><body style='background:#F2F7F3;color:#1B4332;" +
                                            "font-family:sans-serif;padding:32px;text-align:center;'>" +
                                            "<h3>页面加载失败</h3><p>卫士界面暂时不可用，广告跳过不受影响。<br/>" +
                                            "请完全退出后重开；若反复出现请上传日志反馈。</p></body></html>",
                                        "text/html",
                                        "utf-8",
                                        null,
                                    )
                                }

                                // v8.0.9 首帧门控解除：主帧首个合成帧已提交，
                                // 此刻置 VISIBLE（进入残影根治的另一半）
                                override fun onPageCommitVisible(view: WebView, url: String?) {
                                    view.visibility = View.VISIBLE
                                }

                                // v8.0.9 首帧门控兜底：个别 WebView 版本/错误页路径
                                // 不回调 onPageCommitVisible——加载完成仍未显式时
                                // 在此解除，防门控卡死
                                override fun onPageFinished(view: WebView, url: String?) {
                                    if (view.visibility != View.VISIBLE) {
                                        view.visibility = View.VISIBLE
                                    }
                                }

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
                    onRelease = { view ->
                        // v8.0.9 销毁协议（组合离开时执行）：先 detach 再 destroy，
                        // 根治退出时 Surface 残留残影；与 onDestroy 兜底共用
                        // releaseWebViewSafely（幂等去重）
                        this@MainActivity.releaseWebViewSafely(view)
                    },
                )
            }
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
        // v8.0.9 兜底销毁：主路径是 AndroidView.onRelease（组合 dispose 时执行
        // 完整销毁协议）；此处仅在真正退出（isFinishing）且 webView 仍存活
        // （onRelease 未跑——组合未及 dispose 的极端时序）时兜底执行同一协议。
        // 重建路径（非 isFinishing）不动 WebView：视树重建自然走 onRelease/
        // 新建 factory，过早 destroy 反而破坏恢复
        if (isFinishing) {
            webView?.let { releaseWebViewSafely(it) }
        }
        super.onDestroy()
    }

    /**
     * v8.0.9 WebView 释放入口（AndroidView.onRelease 主路径 + onDestroy 兜底共用）：
     * · 幂等去重：仅当 view 仍是当前注册实例（webView === view）时执行，
     *   两路径先后到达只释放一次；
     * · 顺序协议见 [WebViewRelease]（先 detach 再 destroy——destroy 时 WebView
     *   仍 attach 会致 Surface 注销时序错乱，退出残影）；
     * · 释放后置空 webView 字段（WebUiBridge.netCb 的存活校验依赖此语义）。
     */
    private fun releaseWebViewSafely(view: WebView) {
        if (webView !== view) return
        try {
            WebViewRelease.run(view, "AndroidApp")
        } catch (_: Throwable) {
        }
        webView = null
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
