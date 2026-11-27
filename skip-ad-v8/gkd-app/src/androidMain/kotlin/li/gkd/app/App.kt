package li.gkd.app

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Application
import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process.killProcess
import android.os.Process.myPid
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.crash.CrashMetadata
import li.gkd.app.crash.CrashRecorder
import li.gkd.app.crash.CrashRestartGuard
import li.gkd.app.crash.FileCrashStorage
import li.gkd.app.data.AndroidLogMetadata
import li.gkd.app.data.appinfo.PackageAppCatalog
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.input.initMatchingRuleObservers
import li.gkd.app.matching.input.initScreenEvents
import li.gkd.app.notif.NotificationChannels
import li.gkd.app.platform.lifecycle.MainActivityVisibility
import li.gkd.app.platform.lifecycle.RuntimeStateSynchronizer
import li.gkd.app.platform.overlay.KeepAliveOverlayCoordinator
import li.gkd.app.priv.PrivilegeOwnerLifecycle
import li.gkd.app.priv.gkdPrivilegeUiConfig
import li.gkd.app.priv.initPrivilege
import li.gkd.app.priv.requestPrivilegeRecovery
import li.gkd.app.service.A11yService
import li.gkd.app.service.clearDebugSubscription
import li.gkd.app.settings.syncSystemLanguage
import li.gkd.app.snapshot.initVolumeSnapshotTrigger
import li.gkd.app.subscription.EmbeddedSubscriptions
import li.gkd.app.subscription.SubscriptionDefaults
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.matching.input.topTaskAppId
import li.gkd.app.upload.FeedbackLogs
import li.gkd.app.upload.FeedbackReport
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.AndroidToastUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.launchLogged
import li.gkd.db.Db
import li.gkd.db.initialize
import org.lsposed.hiddenapibypass.HiddenApiBypass
import priv.kit.ui.PrivilegeUi

val appScope by lazy { MainScope() }

private lateinit var innerApp: App
val app: App
    get() = innerApp

// https://github.com/android-cs/16/blob/main/packages/SettingsLib/src/com/android/settingslib/accessibility/AccessibilityUtils.java#L41
private const val ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR = ':'

class App : Application() {
    companion object {
        const val START_WAIT_TIME = 3000L
    }

    init {
        innerApp = this
    }

    val applicationInfoWithMetadata: ApplicationInfo by lazy {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    }

    val notificationSmallIcon: Int by lazy {
        getMetaInt("notificationSmallIcon")
    }

    val splashScreenLightTheme: Int by lazy {
        getMetaInt("splashScreenLightTheme")
    }

    val splashScreenNightTheme: Int by lazy {
        getMetaInt("splashScreenNightTheme")
    }

    fun getMetaString(key: String): String {
        return applicationInfoWithMetadata.metaData?.getString(key)
            ?: error("Missing meta-data: $key")
    }

    fun getMetaInt(key: String): Int {
        val resourceId = applicationInfoWithMetadata.metaData?.getInt(key) ?: 0
        check(resourceId != 0) { "Missing resource meta-data: $key" }
        return resourceId
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        installCrashHandler()
        if (AndroidTarget.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    fun registerObserver(
        uri: Uri,
        observer: ContentObserver,
    ) {
        contentResolver.registerContentObserver(uri, false, observer)
    }

    fun unregisterObserver(observer: ContentObserver) {
        contentResolver.unregisterContentObserver(observer)
    }

    fun getSecureString(name: String): String? = Settings.Secure.getString(contentResolver, name)

    fun putSecureString(name: String, value: String?): Boolean {
        return Settings.Secure.putString(contentResolver, name, value)
    }

    fun putSecureInt(name: String, value: Int): Boolean {
        return Settings.Secure.putInt(contentResolver, name, value)
    }

    fun getSecureA11yServices(): MutableSet<ComponentName> {
        val value = getSecureString(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (value.isNullOrEmpty()) return mutableSetOf()
        return value
            .split(
                ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR,
            )
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .toHashSet()
    }

    fun putSecureA11yServices(services: Set<ComponentName>): Boolean =
        putSecureString(
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services.joinToString(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR.toString()) {
                it.flattenToShortString()
            },
        )

    fun resolveAppId(intent: Intent): String? {
        return intent.resolveActivity(packageManager)?.packageName
    }

    fun getPkgInfo(appId: String): PackageInfo? =
        try {
            packageManager.getPackageInfo(appId, PackageAppCatalog.packageFlags)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    fun resolveAppId(action: String, category: String? = null): String? {
        val intent = Intent(action)
        if (category != null) {
            intent.addCategory(category)
        }
        return resolveAppId(intent)
    }

    fun startLaunchActivity() {
        val intent = packageManager.getLaunchIntentForPackage(META.appId)!!
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )
        startActivity(intent)
    }

    fun checkGrantedPermission(permission: String) =
        ContextCompat.checkSelfPermission(
            this,
            permission,
        ) == PackageManager.PERMISSION_GRANTED

    val startTime = System.currentTimeMillis()
    var justStarted: Boolean = true
        get() {
            if (field) {
                field = System.currentTimeMillis() - startTime < START_WAIT_TIME
            }
            return field
        }

    val activityManager by lazy { app.getSystemService(ACTIVITY_SERVICE) as ActivityManager }
    val appOpsManager by lazy { app.getSystemService(APP_OPS_SERVICE) as AppOpsManager }
    val inputMethodManager by lazy {
        app.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    }
    val inputManager by lazy { app.getSystemService(INPUT_SERVICE) as InputManager }
    val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val windowManager by lazy { app.getSystemService(WINDOW_SERVICE) as WindowManager }
    val displayManager by lazy { app.getSystemService(DISPLAY_SERVICE) as DisplayManager }
    val keyguardManager by lazy { app.getSystemService(KEYGUARD_SERVICE) as KeyguardManager }
    val clipboardManager by lazy { app.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager }
    val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }
    val a11yManager by lazy { getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager }
    val launcherApps by lazy { getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps }

    val compatDisplay: Display
        get() =
            if (AndroidTarget.R) {
                displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            } else {
                @Suppress("DEPRECATION") windowManager.defaultDisplay
            }

    override fun onCreate() {
        super.onCreate()
        AndroidStorage.initialize()
        syncSystemLanguage()
        AndroidToastUtils.init()
        Db.initialize(this, AndroidStorage.storage.database.absolutePath)
        AppInfoRepository.initialize()
        LogUtils.i()
        initializeRuntimeComponents()
    }

    private fun installCrashHandler() {
        val recorder =
            CrashRecorder(
                CrashMetadata(
                    AndroidLogMetadata.deviceDescription,
                    Build.VERSION.SDK_INT,
                    Build.VERSION.RELEASE,
                    META.versionCode,
                    META.versionName,
                ),
            ) { record ->
                // Do not depend on storage migration or application initialization here.
                listOf<() -> java.io.File?>(
                        { filesDir.resolve("crash") },
                        { filesDir.resolve("crash/temp") },
                        { getExternalFilesDir(null)?.resolve("crash-export") },
                    )
                    .forEach { directory ->
                        try {
                            directory()?.let { FileCrashStorage.save(record, it) }
                        } catch (failure: Exception) {
                            Log.e("SkipGuard", "Cannot save crash report", failure)
                        }
                    }
            }
        val restartGuard = CrashRestartGuard(filesDir.resolve("crash-restart-history"))
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            recorder.record(t, e)
            val allowRestart =
                try {
                    restartGuard.recordCrash(System.currentTimeMillis())
                } catch (failure: Exception) {
                    Log.e(
                        "SkipGuard",
                        "Cannot persist crash restart history; automatic restart disabled",
                        failure,
                    )
                    false
                }
            if (!allowRestart) {
                PrivilegeOwnerLifecycle.blockCrashRestart()
                Log.e("SkipGuard", "Automatic restart disabled for this crash")
            }
            ToastUtils.show(e.message ?: e.toString())
            LogUtils.e("UncaughtExceptionHandler", t, e)
            appScope.launchLogged(Dispatchers.IO) {
                try {
                    // v8.0.9：去掉崩溃自愈处理器的 delay(1500ms) 冻帧展示
                    // （v8.0.7 GKD 内核重构继承的时序）——异常捕获后立即走重启
                    // 流程。根因：崩溃 Activity 最后一帧冻屏 1.5 秒后才以
                    // CLEAR_TASK 新任务窗口切入，旧帧与新窗口叠加 → 崩溃后
                    // 「残影/叠帧」。崩溃报告落盘（recorder.record）与重启闸
                    // （CrashRestartGuard 60 秒 3 次上限）均在进入协程前已同步
                    // 完成，无等待日志落盘的需求；文件日志由 finally 里的
                    // LogUtils.close() 同步落盘后才 killProcess，时序不变
                    if (allowRestart && MainActivityVisibility.isVisible) {
                        startLaunchActivity()
                        PrivilegeOwnerLifecycle.prepareAppRestart()
                    }
                } finally {
                    try {
                        withContext(NonCancellable + Dispatchers.IO) { LogUtils.close() }
                    } finally {
                        killProcess(myPid())
                    }
                }
            }
        }
    }

    private fun initializeRuntimeComponents() {
        KeepAliveOverlayCoordinator.initialize()
        NotificationChannels.initialize()
        PackageAppCatalog.initialize()
        initMatchingRuleObservers()
        initVolumeSnapshotTrigger()
        initScreenEvents()
        initPrivilege()
        // v8.0.8：统一反馈报告的平台状态注入（引擎运行态/前台应用，见 FeedbackReport.Hooks）
        FeedbackReport.Hooks.engineRunningProvider = { A11yService.isRunning.value }
        FeedbackReport.Hooks.foregroundAppIdProvider = { topTaskAppId }
        appScope.launchLogged(Dispatchers.IO) {
            PrivilegeUi.prepareExternalShellScript(gkdPrivilegeUiConfig.manualShellScriptPath)
        }
        requestPrivilegeRecovery()
        appScope.launchLogged(Dispatchers.IO) {
            SubscriptionRepository.initialize(
                SubscriptionDefaults.fromResources().copy(
                    embeddedContents = readEmbeddedSubscriptionContents(),
                ),
            )
            clearDebugSubscription()
        }
        appScope.launchLogged(Dispatchers.IO) {
            while (true) {
                delay(30 * 60 * 1000L)
                // 亮屏门（对齐旧版 7.15.14）：息屏/Doze 状态不做自动上传网络请求
                if (!powerManager.isInteractive) continue
                if (FeedbackLogs.canAutoUploadNow && A11yService.isRunning.value) {
                    runCatching { FeedbackLogs.autoUploadOnce() }
                }
            }
        }
        RuntimeStateSynchronizer.requestSync()
    }

    /** Reads packaged subscription seed content from APK assets. */
    private fun readEmbeddedSubscriptionContents(): Map<Long, String> {
        val contents = mutableMapOf<Long, String>()
        EmbeddedSubscriptions.seeds.forEach { seed ->
            try {
                assets.open("subscriptions/${seed.id}.json5").use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }.let { contents[seed.id] = it }
            } catch (e: Exception) {
                LogUtils.e("Read embedded subscription failed: ${seed.id}", e)
            }
        }
        return contents
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncSystemLanguage()
    }
}
