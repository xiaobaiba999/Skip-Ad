package li.gkd.app.ui.webui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.MainActivity
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.network.DownloadFailures
import li.gkd.app.network.FeedbackUploader
import li.gkd.app.network.KtorDownloadSource
import li.gkd.app.network.NetworkClients
import li.gkd.app.network.UpdateClient
import li.gkd.app.network.UpdateDownloader
import li.gkd.app.network.logNetworkFailure
import li.gkd.app.upload.FeedbackReport
import li.gkd.app.upload.UserFeedback
import li.gkd.app.record.EngineEventLogWriter
import li.gkd.app.service.A11yService
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SubscriptionUpdateInterval
import li.gkd.app.storage.appStorage
import li.gkd.app.util.tryStartActivity
import li.gkd.db.Db

/**
 * 7.15.14 H5 UI × GKD 内核桥接层（v8.0.5）
 * ─────────────────────────────────────────────
 * `addJavascriptInterface(this, "AndroidApp")` —— 方法名/签名/返回 JSON
 * 与 7.15.14 MainActivity.Bridge 完全一致（assets/www/app.js 零猜测对接），
 * 实现全部转发到 GKD 功能内核单例：
 *
 * · 服务状态：A11yService.isRunning / settings.enableAutomator（masterOn）
 * · 开关通道：setStateBool → enableMatch / updateSubsInterval（订阅自动同步）
 * · 订阅状态：WebUiState.subState（内置快照 + 当前库双源）
 * · 战绩快照：WebUiState.snapshotState（RuleTriggerStats/ActionLog 聚合）
 * · 白名单：SettingsRepository.blockMatchAppList（GKD 不匹配应用列表）
 * · 日志上传：FeedbackUploader（GitHub Issues，双重脱敏 + 15s 频控）
 * · 更新链路：UpdateClient（三源清单 + SHA-256 下载校验）→ 页内进度
 * · 保活体检：KeepAliveTargets（7.15.14 厂商直达表移植）
 */
@Suppress("unused")
class WebUiBridge(activity: MainActivity) {
    private val activityRef = WeakReference(activity)

    // ───────── WebView 回调工具 ─────────

    private fun netCb(cb: String, json: String) {
        val a = activityRef.get() ?: return
        a.webViewOrNull()?.let { wv ->
            a.runOnUiThread {
                // v8.0.9：销毁防护——runOnUiThread 排队期间 WebView 可能已被
                // AndroidView.onRelease / onDestroy 兜底销毁，destroy 后
                // evaluateJavascript 抛 IllegalStateException（崩溃源）。
                // 仅当仍是当前注册实例时求值（releaseWebViewSafely 释放后置空
                // MainActivity.webView，webViewOrNull() 即失效）；destroy 后
                // evaluateJavascript 的 IllegalStateException 由 catch 兜底
                // （WebView 无公开 isDestroyed API，以实例身份 + 异常双闸防护）。
                if (a.webViewOrNull() === wv) {
                    try {
                        wv.evaluateJavascript(
                            "window.__netCb && window.__netCb(${WebUiState.jsonStr(cb)}, ${json})",
                            null,
                        )
                    } catch (_: IllegalStateException) {
                    }
                }
            }
        }
    }

    private fun startActivitySafe(intent: Intent): Boolean = try {
        activityRef.get()?.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    // ───────── 版本 ─────────

    @JavascriptInterface
    fun versionName(): String = META.versionName

    @JavascriptInterface
    fun versionCode(): Int = META.versionCode

    // ───────── KV（页面本地状态；"state" 走 GKD 实时聚合）─────────

    private fun kvPrefs(): android.content.SharedPreferences =
        app.getSharedPreferences("webui_kv", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun kvGet(key: String?): String? {
        if (key == "state") {
            // 7.15.14 语义：state = 服务内存快照（stats/log/开关）；
            // v8.0.5 数据源 = GKD RuleTriggerStats + ActionLog + Settings 实时聚合
            return runBlocking { WebUiState.snapshotState() }
        }
        return kvPrefs().getString(key, null)
    }

    @JavascriptInterface
    fun kvSet(key: String?, value: String?) {
        kvPrefs().edit().putString(key, value).apply()
    }

    // ───────── 服务状态（hero 卡）─────────

    /** 无障碍服务是否已连接运行 */
    @JavascriptInterface
    fun isServiceRunning(): Boolean = A11yService.isRunning.value

    /** 卫士总开关（masterOn）：GKD enableAutomator——暂停防护=不执行点击 */
    @JavascriptInterface
    fun isMasterOn(): Boolean = SettingsRepository.settings.value.enableAutomator

    @JavascriptInterface
    fun setMasterOn(on: Boolean) {
        SettingsRepository.updateSettings { it.copy(enableAutomator = on) }
        EngineEventLogWriter.log(
            kind = "config_changed",
            message = if (on) "▶ 防护已恢复（App 内一键）" else "⏸ 防护已暂停（暂停期间不执行点击）",
        )
    }

    /**
     * 开关专用通道（7.15.14 状态竞态根治的等价物：设置层为唯一数据源）。
     * popupShield → enableMatch（匹配总开关）；learning → 订阅自动同步
     * （updateSubsInterval：Daily / Paused）。
     */
    @JavascriptInterface
    fun setStateBool(key: String?, value: Boolean) {
        when (key) {
            "popupShield" -> SettingsRepository.updateSettings { it.copy(enableMatch = value) }
            "learning" -> SettingsRepository.updateSettings {
                it.copy(
                    updateSubsInterval = if (value) SubscriptionUpdateInterval.Daily
                    else SubscriptionUpdateInterval.Paused,
                )
            }
        }
        if (key != null) {
            val label =
                when (key) {
                    "popupShield" -> "弹窗广告屏蔽"
                    "learning" -> "订阅自动同步"
                    else -> key
                }
            EngineEventLogWriter.log(
                kind = "config_changed",
                message = "⚙️ $label → ${if (value) "开" else "关"}",
            )
        }
    }

    /** 系统设置中无障碍是否仍登记本服务（精确解析，非 contains） */
    @JavascriptInterface
    fun a11yEnabledInSettings(): Boolean {
        val set = app.getSecureA11yServices()
        return set.contains(A11yService.standardComponent) ||
            set.contains(A11yService.privilegedComponent)
    }

    /** 直达无障碍设置开关页；{ok} / {ok,reason:still_registered}（7.15.14 契约） */
    @JavascriptInterface
    fun openA11yDetail(): String {
        // 预判竞态兜底：预判期间登记恢复（AMS 迟到重绑）→ 回落应用内自愈
        if (a11yEnabledInSettings() && !A11yService.isRunning.value) {
            return "{\"ok\":false,\"reason\":\"still_registered\"}"
        }
        val ok = startActivitySafe(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return if (ok) "{\"ok\":true}" else "{\"ok\":false}"
    }

    /** 同步版一键恢复（旧版兼容兜底，页面主路径走 tryAutoReviveDeep） */
    @JavascriptInterface
    fun tryAutoRevive(): String {
        if (!a11yEnabledInSettings()) return "{\"ok\":false,\"reason\":\"disabled\"}"
        if (A11yService.isRunning.value) return "{\"ok\":true}"
        // 登记仍在：系统会在进程存活期间自动重绑（GKD KeepAlive 机制协同），
        // 短轮询等待 5s
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 5000) {
            try {
                Thread.sleep(300)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            if (A11yService.isRunning.value) {
                return "{\"ok\":true,\"reviveMs\":${System.currentTimeMillis() - start}}"
            }
        }
        return "{\"ok\":false,\"reason\":\"need_toggle\"}"
    }

    /** 深度拉活（后台 15s 自适应轮询；成功后暂停自动解除）。
     *  v8.0.8：原实现在 appScope（主调度器）里 Thread.sleep，最长阻塞主线程 15s
     *  必致 ANR（无障碍掉线后用户打开首页即触发）。改用挂起 delay，逻辑节奏不变。 */
    @JavascriptInterface
    fun tryAutoReviveDeep(cb: String?) {
        val cbName = cb ?: return
        appScope.launch {
            if (!a11yEnabledInSettings()) {
                netCb(cbName, "{\"ok\":false,\"reason\":\"disabled\"}")
                return@launch
            }
            val start = System.currentTimeMillis()
            // 前 2s 每 300ms 快档，后段每 500ms（7.15.14 自适应梯度）
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                if (A11yService.isRunning.value) {
                    netCb(cbName, "{\"ok\":true,\"reviveMs\":$elapsed}")
                    return@launch
                }
                if (elapsed >= 15000) break
                kotlinx.coroutines.delay(if (elapsed < 2000) 300 else 500)
            }
            netCb(cbName, "{\"ok\":false,\"reason\":\"need_toggle\"}")
        }
    }

    /** 通知入口自动恢复已由页面接管（v5.2.0 语义保留） */
    @JavascriptInterface
    fun autoRestoreHandled() {
    }

    // ───────── 订阅状态（帮助页/使用报告/日志画像共用）─────────

    @JavascriptInterface
    fun subState(): String = runBlocking { WebUiState.subState() }

    // ───────── 应用白名单（GKD 不匹配应用列表）─────────

    @JavascriptInterface
    fun whitelistJson(): String = runBlocking { WebUiState.whitelistPayload().second }

    @JavascriptInterface
    fun whitelistAdd(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        runBlocking {
            SettingsRepository.updateBlockMatchAppList { it + pkg }
        }
        WebUiState.invalidateWhitelist()
        return true
    }

    @JavascriptInterface
    fun whitelistRemove(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        runBlocking {
            SettingsRepository.updateBlockMatchAppList { it - pkg }
        }
        WebUiState.invalidateWhitelist()
        return true
    }

    // ───────── 白名单图标（批量 data URL · LRU 缓存 · 负缓存防重试）─────────

    private val iconCacheLock = Any()
    private val iconCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>): Boolean = size > 160
    }

    /** 批量图标：入参 JSON 数组包名，返回 {pkg: dataUrl}（缺失=无图标，前端回退字母块） */
    @JavascriptInterface
    fun appIconDataUrls(pkgsJson: String?): String {
        if (pkgsJson.isNullOrBlank()) return "{}"
        val pkgs = try {
            val arr = org.json.JSONArray(pkgsJson)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Throwable) {
            return "{}"
        }
        val snapshot = li.gkd.app.app.AppInfoRepository.snapshot ?: return "{}"
        val out = org.json.JSONObject()
        for (pkg in pkgs) {
            val cached = synchronized(iconCacheLock) { iconCache[pkg] }
            if (cached != null) {
                if (cached.isNotEmpty()) out.put(pkg, cached)
                continue
            }
            val info = snapshot.apps[pkg] ?: continue
            val dataUrl = try {
                WebUiIcons.iconDataUrl(info)
            } catch (_: Throwable) {
                ""
            }
            synchronized(iconCacheLock) { iconCache[pkg] = dataUrl }
            if (dataUrl.isNotEmpty()) out.put(pkg, dataUrl)
        }
        return out.toString()
    }

    // ───────── 保活体检 ─────────

    @JavascriptInterface
    fun keepAliveItems(): String {
        val family = KeepAliveTargets.currentFamily()
        val sb = StringBuilder(256)
        sb.append("{\"family\":").append(WebUiState.jsonStr(family))
            .append(",\"familyLabel\":").append(WebUiState.jsonStr(KeepAliveTargets.familyLabel(family)))
            .append(",\"items\":[")
        var first = true
        for (it in KeepAliveTargets.itemsFor(family)) {
            if (!first) sb.append(",")
            first = false
            sb.append("{\"id\":").append(WebUiState.jsonStr(it.id))
                .append(",\"label\":").append(WebUiState.jsonStr(it.label))
                .append(",\"desc\":").append(WebUiState.jsonStr(it.desc))
                .append("}")
        }
        sb.append("]}")
        return sb.toString()
    }

    @JavascriptInterface
    fun openKeepAliveTarget(id: String?): String {
        val a = activityRef.get() ?: return "{\"ok\":false}"
        val ok = KeepAliveTargets.openTarget(a, id ?: "")
        return if (ok) "{\"ok\":true}" else "{\"ok\":false}"
    }

    // ───────── 电池白名单 / 通知 ─────────

    @JavascriptInterface
    fun isBatteryWhitelisted(): Boolean =
        app.powerManager.isIgnoringBatteryOptimizations(app.packageName)

    @JavascriptInterface
    fun requestBatteryWhitelist(): String {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + app.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = tryStartActivitySafe(intent)
        return if (ok) "\"ok\"" else "\"fail\""
    }

    private fun tryStartActivitySafe(intent: Intent): Boolean {
        try {
            app.tryStartActivity(intent)
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    @JavascriptInterface
    fun notifPermGranted(): Boolean {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }

    @JavascriptInterface
    fun openNotifSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStartActivitySafe(intent)
    }

    /** 简单系统通知（上传失败等罕见场景兜底；成功只有唯一确认弹窗） */
    @JavascriptInterface
    fun postNotify(title: String?, text: String?, success: Boolean): String {
        return try {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_WEBUI, "卫士提醒", NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
            val n = NotificationCompat.Builder(app, CHANNEL_WEBUI)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title ?: META.appName)
                .setContentText(text ?: "")
                .setAutoCancel(true)
                .build()
            nm.notify(200, n)
            "\"ok\""
        } catch (_: Throwable) {
            "\"fail\""
        }
    }

    // ───────── 设备信息 / 崩溃 / 能耗 ─────────

    @JavascriptInterface
    fun deviceInfo(): String = WebUiState.deviceInfo()

    @JavascriptInterface
    fun crashDump(): String = WebUiState.crashDump()

    @JavascriptInterface
    fun energyStats(): String = runBlocking { WebUiState.energyStats() }

    // ───────── 引擎日志（详细通道，v8.0.6）─────────

    /** 详细引擎日志 JSON：[{t,level,kind,msg,app?,aid?,d?}]（时间倒序，默认 200 条上限） */
    @JavascriptInterface
    fun engineLogDetailJson(limit: Int): String {
        val n = if (limit in 1..400) limit else 200
        val rows =
            runBlocking {
                withContext(Dispatchers.IO) {
                    runCatching { Db.engineEventLogDao.queryLatest(n) }.getOrDefault(emptyList())
                }
            }
        val sb = StringBuilder(4096)
        sb.append("[")
        var first = true
        for (r in rows) {
            if (!first) sb.append(",")
            first = false
            sb.append("{\"t\":").append(r.ctime)
                .append(",\"level\":").append(r.level)
                .append(",\"kind\":").append(WebUiState.jsonStr(r.kind))
                .append(",\"msg\":").append(WebUiState.jsonStr(r.message))
            if (r.appId != null) sb.append(",\"app\":").append(WebUiState.jsonStr(r.appId))
            if (r.actionLogId != null) sb.append(",\"aid\":").append(r.actionLogId)
            if (!r.detail.isNullOrBlank()) sb.append(",\"d\":").append(WebUiState.jsonStr(r.detail))
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    // ───────── 日志上传（GitHub Issues，GitHubUploader 双重脱敏）─────────

    /**
     * v8.0.8：统一完整报告构建（与自动上传/Compose 手动上传同一构建器同一口径）。
     * WebView 手动上传经此获取 body，消除三条上传路径内容不一致的问题。
     */
    @JavascriptInterface
    fun buildLogReport(channel: String?): String =
        runBlocking {
            withContext(Dispatchers.IO) {
                runCatching { FeedbackReport.build(channel ?: "手动上传") }
                    .getOrElse { "报告构建失败：${it.message ?: it.javaClass.simpleName}" }
            }
        }

    @JavascriptInterface
    fun netPostIssueAsync(title: String?, body: String?, cb: String?) {
        val cbName = cb ?: return
        val t = (title ?: "").take(128)
        val b = (body ?: "").take(24000)
        appScope.launch {
            val json = try {
                when (val r = FeedbackUploader.createIssue(t, b)) {
                    is FeedbackUploader.Result.Success ->
                        "{\"ok\":true,\"number\":${r.number},\"url\":${WebUiState.jsonStr(r.url)}}"

                    is FeedbackUploader.Result.Failure ->
                        "{\"ok\":false,\"error\":${WebUiState.jsonStr(r.message)}}"
                }
            } catch (e: Throwable) {
                "{\"ok\":false,\"error\":${WebUiState.jsonStr(e.message ?: "上传异常")}}"
            }
            netCb(cbName, json)
        }
    }

    // ───────── v8.1.1 用户意见反馈（底栏入口 + 开屏提示入口共用统一仓库）─────────

    /** 反馈页环境上下文：最近跳过结果 + 可选预填相关应用。 */
    @JavascriptInterface
    fun feedbackContextAsync(cb: String?) {
        val cbName = cb ?: return
        appScope.launch {
            val json =
                runCatching {
                    val snapshot = UserFeedback.lastSkipSnapshot()
                    buildString {
                        append("{\"ok\":true")
                        append(",\"lastSkip\":").append(
                            if (snapshot == null) "null" else WebUiState.jsonStr(snapshot),
                        )
                        append(",\"types\":[")
                        UserFeedback.TYPES.forEachIndexed { i, t ->
                            if (i > 0) append(",")
                            append(WebUiState.jsonStr(t))
                        }
                        append("]")
                        append("}")
                    }
                }.getOrElse { "{\"ok\":false,\"error\":${WebUiState.jsonStr(it.message ?: "上下文异常")}}" }
            netCb(cbName, json)
        }
    }

    /**
     * 提交意见反馈（payload：{type,title,description,appPackage,includeLogs}）。
     * 隐私闸：includeLogs 仅在用户勾选时才附带完整诊断报告（未勾选绝不上传）。
     */
    @JavascriptInterface
    fun submitFeedbackAsync(payload: String?, cb: String?) {
        val cbName = cb ?: return
        val input =
            payload?.let { UserFeedback.parsePayload(it) }
                ?: run {
                    netCb(cbName, "{\"ok\":false,\"error\":\"反馈内容解析失败\"}")
                    return
                }
        appScope.launch {
            val json =
                try {
                    withContext(Dispatchers.IO) { UserFeedback.submit(input) }.let { r ->
                        when (r) {
                            is FeedbackUploader.Result.Success ->
                                "{\"ok\":true,\"number\":${r.number},\"url\":${WebUiState.jsonStr(r.url)}}"

                            is FeedbackUploader.Result.Failure ->
                                "{\"ok\":false,\"error\":${WebUiState.jsonStr(r.message)},\"fallbackUrl\":" +
                                    (r.fallbackUrl?.let { WebUiState.jsonStr(it) } ?: "null") + "}"
                        }
                    }
                } catch (e: Throwable) {
                    "{\"ok\":false,\"error\":${WebUiState.jsonStr(e.message ?: "提交异常")}}"
                }
            netCb(cbName, json)
        }
    }

    /**
     * v8.1.1 开屏卫士提示「反馈」入口直达：MainActivity 收到 open_feedback
     * 深链后经此推送 H5 页切换（JS 侧 window.__onFeedbackEntry 钩子）。
     */
    fun pushFeedbackEntry(appPackage: String?) {
        val a = activityRef.get() ?: return
        a.runOnUiThread {
            a.webViewOrNull()?.let { wv ->
                if (a.webViewOrNull() === wv) {
                    try {
                        wv.evaluateJavascript(
                            "window.__onFeedbackEntry && window.__onFeedbackEntry(" +
                                "{\"app\":${WebUiState.jsonStr(appPackage.orEmpty())}})",
                            null,
                        )
                    } catch (_: IllegalStateException) {
                    }
                }
            }
        }
    }

    // ───────── 应用内更新（三源清单 + SHA-256 校验 + 页内进度）─────────

    /** 检查更新：UpdateClient 多源清单 → 7.15.14 页面格式（含镜像通道）
     *
     * v8.1.1：镜像链改为【清单声明优先】（apkMirrors——服务端可控，改镜像
     * 不必发新版客户端）；清单无镜像时回退 v8.0.9 客户端本地拼装（ghfast/
     * gh-proxy 代理）。全部镜像同字节同源（仓库 dist 归档 / GitHub 代理），
     * 下载侧 SHA-256 + 签名双闸验收。 */
    @JavascriptInterface
    fun netGetUpdateAsync(cb: String?) {
        val cbName = cb ?: return
        appScope.launch {
            val json = try {
                val v = UpdateClient.fetchUpdate()
                val mirrors =
                    if (v.apkMirrors.isNotEmpty()) {
                        buildList {
                            if (v.apkUrl.isNotEmpty()) add(v.apkUrl)
                            addAll(v.apkMirrors)
                        }
                    } else {
                        buildList {
                            if (v.apkUrl.isNotEmpty()) {
                                add(v.apkUrl)
                                add("https://ghfast.top/${v.apkUrl}")
                                add("https://gh-proxy.com/${v.apkUrl}")
                            }
                        }
                    }
                buildString {
                    append("{\"ok\":true,\"hasRelease\":true")
                    append(",\"tag\":").append(WebUiState.jsonStr(v.releaseTag ?: ""))
                    append(",\"notes\":").append(WebUiState.jsonStr(v.releaseNotes ?: ""))
                    append(",\"publishedAt\":\"\"")
                    append(",\"url\":").append(WebUiState.jsonStr("https://github.com/xiaobaiba999/Skip-Ad/releases/tag/${v.releaseTag ?: ""}"))
                    append(",\"versionName\":").append(WebUiState.jsonStr(v.versionName))
                    append(",\"versionCode\":").append(v.versionCode)
                    append(",\"apkUrl\":").append(WebUiState.jsonStr(v.apkUrl))
                    append(",\"apkMirrors\":[")
                    var first = true
                    for (m in mirrors) {
                        if (!first) append(",")
                        first = false
                        append(WebUiState.jsonStr(m))
                    }
                    append("]")
                    append(",\"apkFileName\":").append(WebUiState.jsonStr("adshield-v${v.versionName}.apk"))
                    append(",\"apkMB\":").append(v.apkSize / 1048576.0.let { Math.round(it * 100.0) / 100.0 })
                    append(",\"apkSize\":").append(v.apkSize)
                    append(",\"apkDigest\":").append(
                        if (v.apkSha256.isNullOrEmpty()) "\"\"" else WebUiState.jsonStr("sha256:${v.apkSha256}"),
                    )
                    append("}")
                }
            } catch (e: Throwable) {
                "{\"ok\":false,\"error\":${WebUiState.jsonStr(e.message ?: "多源均不可达——请稍后重试")}}"
            }
            netCb(cbName, json)
        }
    }

    /** 页内下载（v8.1.1：多源断点续传引擎；urls 为 JSON 数组）。 */
    @JavascriptInterface
    fun startUpdateDownload(urlsJson: String?, digest: String?, sizeBytes: Long): String {
        if (urlsJson.isNullOrBlank()) return "{\"ok\":false,\"error\":\"下载链接为空\"}"
        return UpdateDownload.start(urlsJson, digest, sizeBytes)
    }

    @JavascriptInterface
    fun updateProgress(): String = UpdateDownload.progressJson()

    @JavascriptInterface
    fun installUpdate(): String {
        val file = UpdateDownload.targetFile()
        if (!file.exists()) return "{\"ok\":false,\"error\":\"安装包不存在，请重新下载\"}"
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val ok = tryStartActivitySafe(intent)
        return if (ok) "{\"ok\":true}" else "{\"ok\":false,\"error\":\"无法拉起安装（请允许安装未知应用）\"}"
    }

    @JavascriptInterface
    fun openUrl(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.tryStartActivity(intent)
        } catch (_: Throwable) {
        }
    }

    private companion object {
        const val CHANNEL_WEBUI = "webui"
    }
}

/**
 * 页内更新下载状态机（v8.1.1 重构：断点续传多源引擎驱动）。
 * 下载走 UpdateDownloader——Range 续传 + ETag 一致性 + 停滞闸 + 失败自动切换；
 * 全部源同字节同源（Release / 仓库 dist 归档 / GitHub 代理），SHA-256 + 大小
 * 完整性闸全过后原子落位；取消/中断保留 .part 断点，重试从断点继续。
 */
private object UpdateDownload {
    @Volatile var state: String = "idle" // idle/running/verifying/done/failed
    @Volatile var bytes: Long = 0
    @Volatile var total: Long = 0
    @Volatile var speed: Long = 0
    @Volatile var sourceLabel: String = ""
    @Volatile var sourceIndex: Int = 0
    @Volatile var sourceCount: Int = 0
    @Volatile var resuming: Boolean = false
    @Volatile var switching: String = ""
    @Volatile var err: String = ""

    fun targetFile(): File = File(appStorage().sharedCache, "adshield-update.apk")

    @Synchronized
    fun start(urlsJson: String, digest: String?, sizeBytes: Long): String {
        if (state == "running" || state == "verifying") {
            return "{\"ok\":false,\"error\":\"已有下载任务进行中\"}"
        }
        val urls =
            runCatching {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(urlsJson)
            }.getOrNull().orEmpty().filter { it.startsWith("https://") }
        if (urls.isEmpty()) return "{\"ok\":false,\"error\":\"下载链接为空\"}"
        state = "running"
        bytes = 0
        total = sizeBytes
        speed = 0
        sourceLabel = ""
        sourceIndex = 0
        sourceCount = urls.size
        resuming = false
        switching = ""
        err = ""
        val sha = digest?.removePrefix("sha256:")?.takeIf { it.isNotBlank() }
        val file = targetFile()
        appScope.launch {
            try {
                UpdateDownloader.download(
                    source = KtorDownloadSource(NetworkClients.downloadClient),
                    urls = urls,
                    target = file,
                    fingerprint = "webui:$sha:$sizeBytes",
                    expectedSha256 = sha,
                    expectedSize = sizeBytes,
                    progress = { p ->
                        if (p.verifying) {
                            state = "verifying"
                        } else {
                            state = "running"
                            bytes = p.received
                            total = p.total.takeIf { it > 0 } ?: total
                            speed = p.bytesPerSecond
                            sourceLabel = p.sourceLabel
                            sourceIndex = p.sourceIndex
                            resuming = p.resuming
                            switching =
                                if (p.switchingTo != null) {
                                    "${p.switchingFrom.orEmpty()} → ${p.switchingTo}：${p.switchingReason.orEmpty()}"
                                } else {
                                    ""
                                }
                        }
                    },
                    logFailure = { op, e -> logNetworkFailure(op, e) },
                )
                state = "done"
                bytes = total.takeIf { it > 0 } ?: file.length()
            } catch (e: Throwable) {
                state = "failed"
                err = DownloadFailures.classify(e)
                logNetworkFailure(
                    "Update download failed",
                    (e as? Exception) ?: IllegalStateException(e.message ?: "download failed", e),
                )
            }
        }
        return "{\"ok\":true}"
    }

    fun progressJson(): String {
        val percent = if (total > 0) (bytes * 100 / total).coerceIn(0, 100).toInt() else 0
        return buildString {
            append("{\"state\":\"").append(state).append("\"")
            append(",\"bytes\":").append(bytes)
            append(",\"total\":").append(total)
            append(",\"percent\":").append(percent)
            append(",\"speed\":").append(speed)
            append(",\"source\":").append(WebUiState.jsonStr(sourceLabel))
            append(",\"sourceIndex\":").append(sourceIndex)
            append(",\"sourceCount\":").append(sourceCount)
            append(",\"resuming\":").append(resuming)
            append(",\"switching\":").append(WebUiState.jsonStr(switching))
            append(",\"err\":").append(WebUiState.jsonStr(err))
            append("}")
        }
    }
}
