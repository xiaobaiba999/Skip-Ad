package com.zai.adshield;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

    /**
 * 主界面（v3.7.0）
 * ─────────────────────────────────────────────
 * · WebView 装载离线页面 assets/www（首页 / 学习中心 / 关于）
 * · 启动即静默检查 GitHub Releases：发现新版 → 页面顶部弹出更新横幅，
 *   页内直接下载（系统 DownloadManager）→ 进度条 → 完成后拉起安装，全程不跳浏览器
 * · v7.9.7：问题反馈走浏览器预填链接（newIssuePrefillUrl）、引擎日志本地导出
 *   （exportLog 桥 + shareLogText 分享）——内置混淆令牌已随 PAT 移除下线，客户端零凭据
 * · v3.5.0 保活：通知权限引导（Android 13+）、jump_a11y 通知一键恢复直达、
 *   电池优化白名单与 keepOn 状态桥接（配合 KeepAliveReceiver）
 * · v3.6.0 自动拉活：清理后台后服务被杀但系统设置仍登记时，打开 App
 *   自动 startService 拉起进程促系统重绑（tryAutoRevive），多数 ROM 无需手动去设置
 * · v3.7.0：拉活等待加长至 5s + 二次拉起；配合服务侧 onUnbind 保留 + 心跳闹钟
 * · v3.8.0：手势真实触点首选（服务侧）+ 无障碍详情页直达一步恢复；
 *   移除无效的 startService 拉活路径
 * · v3.14.0：ADB 自愈授权（WRITE_SECURE_SETTINGS）整体移除——开启服务
 *   一步直达系统开关，开启一次后保活四件套持续守护，退出 App/清理后台
 *   重进无需重新开启
 * · v4.5.0：系统开关与 App 开关彻底解耦 + 安全加固——系统无障碍开关
 *   永不触碰（零 disableSelf），卫士开关由 App 内/通知栏一键控制（暂停
 *   零扫描、恢复零延迟，绝不去系统设置看 10 秒警告）；WebView URL
 *   白名单（JS 桥零远程暴露）；反馈通道频控+长度上限；开关设置走
 *   setStateBool 专用通道（状态竞态根治）；onResume 自动拉活深挖重绑。
 * · v5.2.0（issue#56：Redmi 25060RK16C 每次退后台/覆盖安装都要看
 *   10 秒警告，Xiaomi 15 不用）：
 *   ① 拉活窗口 6s→15s 自适应（RestoreGuard.pollPlan：前 2s 每 300ms
 *     快踢后段 500ms——低端机冷启动 + HyperOS 3 重绑慢不再误判）；
 *   ② 拉活循环每轮复查登记——中途被吊销立即停止并返回 disabled（不再
 *     俊等满 15s）；
 *   ③ 通知 jump_a11y 不再 500ms 直跳系统设置：改交页面 __autoRestore
 *     先深挖拉活，真被吊销才直达开关页（含 10 秒安全确认预期管理）；
 *     onNewIntent 同样处理（通知点按时 App 已开也能触发恢复）；
 *   ④ 新增保活体检桥：keepAliveItems（近 5 年全品牌自启动/省电策略
 *     直达候选，硬编码系统包零注入面）+ openKeepAliveTarget（逐候选
 *     resolveActivity 校验回退）+ notifPermGranted；
 *   ⑤ 登记判断改 RestoreGuard.registrationState 精确解析（子串误报绝纻）。
 */
public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = (WebView) findViewById(R.id.webview);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        /* ═══ v4.5.0 WebView 安全加固（攻击面收缩）═══
         * 本地离线页不需要：跨文件/跨域访问、自动开窗、地理定位、表单保存。
         * 全部显式关闭（默认值随 ROM 版本漂移，显式声明才可靠）。 */
        try {
            ws.setAllowFileAccessFromFileURLs(false);
            ws.setAllowUniversalAccessFromFileURLs(false);
            ws.setJavaScriptCanOpenWindowsAutomatically(false);
            ws.setGeolocationEnabled(false);
            ws.setSaveFormData(false);
            ws.setSupportZoom(false);
            WebView.setWebContentsDebuggingEnabled(false); // 绝不开放远程调试
        } catch (Throwable ignored) {
        }
        webView.addJavascriptInterface(new Bridge(this), "AndroidApp");
        webView.setWebViewClient(new HardenedWebViewClient());
        webView.loadUrl("file:///android_asset/www/index.html");

        // v5.5.0 崩溃采集：进程崩溃堆栈写本地（8KB 截断），下次日志上传自动附带
        CrashCapture.install(this);

        // v3.5.0 保活：通知权限（13+）——保活常驻通知与恢复提醒必需
        requestNotifPermission();
        // v3.5.0：从「待恢复」通知点入 → 自动跳无障碍设置（两步恢复的第一步）
        // （注意：匿名内部类会触发 d8 8.2 NPE bug，一律用 lambda）
        /* v5.2.0：待恢复通知点入（jump_a11y / jump_restore）不再直接跳
         * 系统设置——旧版无条件 500ms openA11y，把「登记仍在、只是重绑慢」
         * 的用户也白送去看 10 秒警告（issue#56 痛点放大器）。现改为：
         * 页面加载后交 __autoRestore 先深挖拉活；真被吊销才直达开关页。 */
        if (getIntent() != null && (getIntent().getBooleanExtra("jump_a11y", false)
                || getIntent().getBooleanExtra("jump_restore", false))) {
            pendingAutoRestore = true;
        }
    }

    /** v5.2.0：待执行的自动恢复（onCreate/onNewIntent 置位，页面就绪后触发） */
    private boolean pendingAutoRestore = false;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // v5.2.0：通知点按时 App 已在前台（SINGLE_TOP）也要触发恢复流程
        if (intent != null && (intent.getBooleanExtra("jump_a11y", false)
                || intent.getBooleanExtra("jump_restore", false))) {
            pendingAutoRestore = true;
            triggerAutoRestore();
        }
    }

    /** v5.2.0：驱动页面 __autoRestore（页面未就绪时由 onResume 重试触发） */
    private void triggerAutoRestore() {
        if (webView == null) return;
        try {
            webView.evaluateJavascript(
                    "window.__autoRestore && window.__autoRestore(true)", null);
        } catch (Throwable ignored) {
        }
    }

    /** v5.2.0：页面接管后撤销重试（页面回调后不再重复触发） */
    private void autoRestoreHandled() {
        pendingAutoRestore = false;
    }

    private void requestNotifPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        } catch (Throwable ignored) {
        }
    }

    /* v3.8.0：Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS（API 30+）的实际值。
     *  构建环境 android-34 stub jar 未收录该符号，这里用与 SDK 常量完全等价的
     *  字面值（平台 action 字符串属稳定公共 API，运行时行为与常量引用一致） */
    private static final String ACTION_A11Y_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS";

    /** v3.8.0：直达本服务无障碍【详情页】（Android 11+ 只剩一个开关，一步恢复；
     *  旧系统回退无障碍列表页） */
    private void openA11y() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent it = new Intent(ACTION_A11Y_DETAILS);
                it.putExtra(Intent.EXTRA_COMPONENT_NAME,
                        new android.content.ComponentName(this, AdSkipService.class));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                return;
            }
            throw new IllegalStateException("pre-R");
        } catch (Exception e) {
            try {
                Intent it = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        /* v4.5.0 打开 App 即自动拉活（用户核心诉求：打开就是打开了）
         * 但凡系统设置里还登记着服务，打开 App 的瞬间就后台深挖重绑
         * （v5.2.0 的 15s 深度拉活）——用户点开卫士那一刻防护就在回来的
         * 路上，无需任何手动操作，更不去系统设置。 */
        try {
            if (!AdSkipService.isAlive() && AdSkipService.registrationPresent(this)) {
                new Thread(() -> {
                    try {
                        Bridge.deepReviveOnce(this);
                    } catch (Throwable ignored) {
                    }
                }, "auto-revive").start();
            }
        } catch (Throwable ignored) {
        }
        /* v5.2.0：通知入口自动恢复——页面可能还在加载（或刚被拉起），
         * 多次重试直到页面 __autoRestore 接管（autoRestoreHandled 撤销） */
        if (pendingAutoRestore) {
            for (int i = 1; i <= 6; i++) {
                final int delayMs = i * 700;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (pendingAutoRestore) triggerAutoRestore();
                }, delayMs);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    static String versionNameOf(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "5.1.1";
        }
    }

    /** 暴露给离线页面的原生桥（静态类 + 外部引用，规避 d8 8.2 内部类 bug） */
    static class Bridge {

        private final MainActivity a;

        Bridge(MainActivity activity) {
            this.a = activity;
        }

        @JavascriptInterface
        public boolean isServiceRunning() {
            return AdSkipService.isAlive(); // v4.4.0：真实绑定态（僵尸实例不再假报「守护中」）
        }

        /* ───── v4.1.0 App 内主开关（暂停/恢复，免系统 10 秒警告） ───── */

        @JavascriptInterface
        public boolean isMasterOn() {
            return AdSkipService.masterState(a);
        }

        @JavascriptInterface
        public void setMasterOn(boolean on) {
            AdSkipService.setMaster(a, on);
        }

        /* ═══ v5.5.0 10 秒警告红线：系统设置跳转最终守卫 ═══
         * 所有「去系统无障碍设置」的路径都必须过这道闸：只要服务仍在系统
         * 登记列表中，一律返回 still_registered 拒绝跳转——由页面改走应用内
         * 自愈（doRevive），绝不把登记仍在的用户送去系统设置看 10 秒强制
         * 警告。系统设置只剩一种合法到达场景：登记真被系统吊销（未授权）。
         * 本闸是 Java 侧最后一道（JS 侧 btn-enable/__autoRestore 已有各自
         * 预判），任何一层被绕过都兜得住。
         * @return {"ok":true} 已跳转 / {"ok":false,"reason":"still_registered"} 登记在、未跳转 */
        @JavascriptInterface
        public String openA11yDetail() {
            try {
                if (AdSkipService.registrationPresent(a)) {
                    return "{\"ok\":false,\"reason\":\"still_registered\"}";
                }
                a.openA11y();
                return "{\"ok\":true}";
            } catch (Exception e) {
                return "{\"ok\":false,\"reason\":\"error\"}";
            }
        }

        /* ───── v3.5.0 保活与诊断桥 ───── */

        /** 设备信息（引擎日志上传时自动附带，供迭代定位问题） */
        @JavascriptInterface
        public String deviceInfo() {
            org.json.JSONObject o = new org.json.JSONObject();
            try {
                o.put("model", Build.MODEL);
                o.put("brand", Build.BRAND);
                o.put("os", "Android " + Build.VERSION.RELEASE);
                o.put("sdk", Build.VERSION.SDK_INT);
                o.put("app", versionNameOf(a));
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        /* ───── v5.5.0 日志上传强反馈配套 ───── */

        /** 上传结果系统通知（三重反馈之三：弹窗 + 页内横幅 + 本通知）。
         *  独立 "upload" 通道 IMPORTANCE_DEFAULT（有状态栏横幅，无声音）；
         *  无通知权限（Android 13+ 未授权）时降级返回 nof，页面已有弹窗/
         *  横幅两重反馈，不打扰用户补授权。
         * @return {"ok":true} / {"ok":false,"reason":"no_perm"} */
        @JavascriptInterface
        public String postNotify(String title, String text, boolean success) {
            try {
                android.app.NotificationManager nm = (android.app.NotificationManager)
                        a.getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= 33
                        && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    return "{\"ok\":false,\"reason\":\"no_perm\"}";
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    android.app.NotificationChannel ch = new android.app.NotificationChannel(
                            "upload", "上传结果通知",
                            android.app.NotificationManager.IMPORTANCE_DEFAULT);
                    ch.setDescription("日志上传成功/失败的结果通知");
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
                android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
                        ? new android.app.Notification.Builder(a, "upload")
                        : new android.app.Notification.Builder(a);
                b.setContentTitle(title)
                        .setContentText(text)
                        .setSmallIcon(R.drawable.ic_stat)
                        .setColor(success ? 0xFF10B981 : 0xFFDC2626)
                        .setAutoCancel(true)
                        .setShowWhen(true)
                        .setWhen(System.currentTimeMillis());
                nm.notify(3001, b.build());
                return "{\"ok\":true}";
            } catch (Throwable t) {
                return "{\"ok\":false,\"reason\":\"error\"}";
            }
        }

        /** 上次进程崩溃堆栈（v5.5.0 崩溃采集：日志上传自动附带，异常不再靠猜）。
         *  崩溃时 CrashCapture 已写入 cacheDir/last_crash.txt（截 8KB）；
         *  读后即删（一次性消费，不长期留存隐私）。无崩溃返回空串。 */
        @JavascriptInterface
        public String crashDump() {
            return CrashCapture.readAndClear(a);
        }

        /** keepOn：开启后除非手动关闭，更新/重启断连都会提醒恢复 */
        @JavascriptInterface
        public boolean isKeepOn() {
            return a.getSharedPreferences("adshield", Context.MODE_PRIVATE)
                    .getBoolean("keepOn", false);
        }

        @JavascriptInterface
        public void setKeepOn(boolean on) {
            a.getSharedPreferences("adshield", Context.MODE_PRIVATE)
                    .edit().putBoolean("keepOn", on).apply();
        }

        /** 电池优化白名单状态（厂商省电策略是杀服务主因之一） */
        @JavascriptInterface
        public boolean isBatteryWhitelisted() {
            try {
                android.os.PowerManager pm = (android.os.PowerManager)
                        a.getSystemService(Context.POWER_SERVICE);
                return pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName());
            } catch (Exception e) {
                return false;
            }
        }

        /** 申请加入电池优化白名单（失败则跳白名单列表页） */
        @JavascriptInterface
        public String requestBatteryWhitelist() {
            try {
                if (isBatteryWhitelisted()) {
                    return "{\"ok\":true,\"already\":true}";
                }
                Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + a.getPackageName()));
                a.startActivity(it);
                return "{\"ok\":true}";
            } catch (Exception e) {
                try {
                    a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    return "{\"ok\":true,\"fallback\":true}";
                } catch (Exception e2) {
                    return "{\"ok\":false}";
                }
            }
        }

        @JavascriptInterface
        public String kvGet(String key) {
            // state（学习数据）：优先读服务内存态（延迟落盘机制下保证 UI 拿到最新值）
            if ("state".equals(key)) {
                String snap = AdSkipService.snapshotState(a);
                if (snap != null) return snap;
            }
            return a.getSharedPreferences("adshield", Context.MODE_PRIVATE).getString(key, null);
        }

        @JavascriptInterface
        public void kvSet(String key, String value) {
            // v4.5.0 安全：value 长度上限（防 prefs 膨胀/DoS；正常页面状态远小于此）
            if (value != null && value.length() > 262144) return;
            /* v7.3.0 安全：key 白名单——JS 侧（app.js store.set）合法写入的键
             * 只有 "state"（页面状态/战绩/开关）。其余键（subUrl、subCache、
             * dl 系列与曾用的 autoLogAt 等）是服务/订阅/下载链路的持久化字段：一旦
             * 前端出现注入（XSS），「kvSet 写 subCache 恶意规则」即可让服务
             * 重启时静默加载被投毒的自动点击规则——白名单把这个通道焊死。 */
            if (!"state".equals(key)) return;
            SharedPreferences.Editor e = a.getSharedPreferences("adshield", Context.MODE_PRIVATE).edit();
            if (value == null) e.remove(key);
            else e.putString(key, value);
            e.apply();
        }

        /* ═══ v4.5.0 开关类设置专用通道（状态竞态根治）═══
         * 「开启弹窗广告屏蔽之后又被 App 自动关闭」根因：UI 的 kvSet("state")
         * 只写 prefs，服务的 stateInMemory 旧副本在战绩更新落盘时整体覆盖
         * 回去（开关自动弹回）。开关一律走这里——服务活着就改服务内存本体
         * （唯一数据源），UI 绝不再直接写 state 开关字段。 */
        @JavascriptInterface
        public void setStateBool(String key, boolean value) {
            AdSkipService.setStateBool(a, key, value);
        }

        @JavascriptInterface
        public String versionName() {
            return versionNameOf(a);
        }

        @JavascriptInterface
        public int versionCode() {
            try {
                PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                return pi.versionCode;
            } catch (Exception e) {
                return 40500;
            }
        }

        /* ───── v3.6.0 自动拉活重绑（清理后台后服务被杀的修复） ───── */

        /** 无障碍服务在系统设置里是否仍登记为本应用（读取无需权限）
         *  ——true 表示服务被系统杀掉但没被关闭，可尝试自动拉活重绑
         *  v5.2.0：改 RestoreGuard 精确解析（子串误报绝纻） */
        @JavascriptInterface
        public boolean a11yEnabledInSettings() {
            return AdSkipService.registrationPresent(a);
        }

        /** 自动拉活（v5.2.0 自适应深挖版，RestoreGuard.pollPlan/decideInAppRevive）：
         *  · 服务已运行 → ok（若处于暂停自动解除——用户点「恢复」的意图）
         *  · 设置仍登记且服务死 → 深挖最长 15s（v4.4.0 固定 6s 在低端
         *    HyperOS 上不够，issue#56 实锤 Redmi 25060RK16C 冷启动 +
         *    AMS 重绑慢 → 被误判 need_toggle 跳系统设置看 10 秒警告）；
         *    前 2s 每 300ms 快踢后段 500ms，每轮复查登记（中途被吊销
         *    立即返回 disabled，不再傻等）；
         *  · 未登记 → disabled 由页面引导开启。
         *  （无障碍服务受 BIND_ACCESSIBILITY_SERVICE 保护，任何应用都不能
         *   start/bind 它；设置页 10 秒强制等待仅剩「开关真被关」时才需要）
         *  返回 JSON：{ok, running?, revived?, resumed?, reviveMs?, enabledInSettings?, reason?} */
        @JavascriptInterface
        public String tryAutoRevive() {
            org.json.JSONObject out = new org.json.JSONObject();
            try {
                if (AdSkipService.isAlive()) {
                    boolean wasPaused = !AdSkipService.masterState(a);
                    if (wasPaused) AdSkipService.setMaster(a, true); // 恢复即全开
                    out.put("ok", true).put("running", true).put("resumed", wasPaused);
                    return out.toString();
                }
                boolean enabled = AdSkipService.registrationPresent(a);
                out.put("enabledInSettings", enabled);
                if (!enabled) {
                    /* v5.6.0 零打扰持久模式：登记被吊销先尝试免设置自愈（仅
                     * ADB 授权过 WRITE_SECURE_SETTINGS 的设备生效）。写回成功
                     * → 继续走 15s 深挖重绑（页面提示「持久模式自愈中」）；
                     * 未授权/失败 → 回退保守 disabled 引导（照旧）。 */
                    if (AdSkipService.canSecureSelfHeal(a)
                            && AdSkipService.secureSelfHeal(a)) {
                        enabled = true;
                        out.put("selfHealed", true);
                    } else {
                        out.put("ok", false).put("reason", "disabled");
                        return out.toString();
                    }
                }
                // 设置仍登记：进程已拉起，AMS 自动重绑——自适应深挖 15s
                long started = System.currentTimeMillis();
                long[] plan = com.zai.adshield.core.RestoreGuard.pollPlan(REVIVE_WINDOW_MS);
                for (long at : plan) {
                    long wait = at - (System.currentTimeMillis() - started);
                    if (wait > 0) Thread.sleep(Math.min(wait, 500));
                    if (AdSkipService.isAlive()) break;
                    // v5.2.0：每轮复查登记——HyperOS 若在拉活期间吊销了登记，
                    // 立即停止（傻等满 15s 只会让用户觉得「转半天没反应」）
                    if (!AdSkipService.registrationPresent(a)) {
                        out.put("ok", false).put("reason", "disabled")
                                .put("enabledInSettings", false);
                        return out.toString();
                    }
                    kickA11yManager();
                }
                boolean revived = AdSkipService.isAlive();
                if (revived) {
                    boolean wasPaused = !AdSkipService.masterState(a);
                    if (wasPaused) AdSkipService.setMaster(a, true); // 拉活成功 → 暂停自动解除
                    out.put("ok", true).put("revived", true).put("resumed", wasPaused)
                            .put("reviveMs", System.currentTimeMillis() - started);
                } else {
                    out.put("ok", false).put("reason", "need_toggle") // 页面引导重试，绝不自动跳系统设置
                            .put("reviveMs", System.currentTimeMillis() - started);
                }
            } catch (Throwable t) {
                try {
                    out.put("ok", false).put("reason", "error");
                } catch (Exception ignored) {
                }
            }
            return out.toString();
        }

        /** v5.2.0：拉活窗口（6s → 15s；低速设备冷启动 + AMS 重绑覆盖） */
        static final long REVIVE_WINDOW_MS = 15000;

        /** v4.4.0：异步深度拉活（后台线程 15s 深挖，不阻塞 JS Bridge 线程；
         *  结果经 window.__reviveCb(name, json) 回调） */
        @JavascriptInterface
        public void tryAutoReviveDeep(final String cb) {
            new Thread(() -> {
                final String r = tryAutoRevive();
                a.runOnUiThread(() -> {
                    try {
                        if (a.webView != null) {
                            a.webView.evaluateJavascript(
                                    "window.__reviveCb && window.__reviveCb("
                                            + JSON.stringifyCb(cb) + "," + r + ")", null);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }, "revive-deep").start();
        }

        /** v4.4.0：查询已启用无障碍服务列表——促使 AMS 校对绑定状态
         *  （进程存活但服务未绑的窗口期，这次查询常触发系统重绑） */
        private void kickA11yManager() {
            try {
                android.view.accessibility.AccessibilityManager am =
                        (android.view.accessibility.AccessibilityManager)
                                a.getSystemService(Context.ACCESSIBILITY_SERVICE);
                if (am != null) {
                    am.getEnabledAccessibilityServiceList(
                            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                }
            } catch (Throwable ignored) {
            }
        }

        /* ───── v4.0.0 GKD 订阅管理（订阅页） ───── */

        /** 订阅状态（内置 + 远程统计） */
        @JavascriptInterface
        public String subState() {
            return SubscriptionManager.stateJson(a);
        }

        /** v5.6.0 订阅市场：活跃维护订阅列表（内置注册表，零网络） */
        @JavascriptInterface
        public String subRecommend() {
            return SubscriptionManager.recommendJson();
        }

        /** 设置/清空远程订阅 URL（立即拉取；后台线程执行）。
         *  v7.0.0：镜像切换进度实时回报页内（window.__subProgress(attempt,total,host)
         *  ——「正在切换镜像 X/Y · host」反馈；离线快照兜底由 SubscriptionManager
         *  内部完成，导入路径永不硬失败）。 */
        @JavascriptInterface
        public void subSetUrl(final String url, final String cb) {
            new Thread(() -> {
                final String r;
                try {
                    SubscriptionManager.ProgressSink sink = (attempt, total, host) -> a.runOnUiThread(() -> {
                        try {
                            if (a.webView != null) {
                                a.webView.evaluateJavascript(
                                        "window.__subProgress && window.__subProgress("
                                                + attempt + "," + total + "," + JSON.stringifyCb(host) + ")", null);
                            }
                        } catch (Throwable ignored) {
                        }
                    });
                    r = SubscriptionManager.setUrl(a, url, sink);
                } catch (final Throwable t) {
                    a.runOnUiThread(() -> {
                        try {
                            if (a.webView != null) {
                                a.webView.evaluateJavascript(
                                        "window.__subCb && window.__subCb(" + JSON.stringifyCb(cb) + ",{ok:false,error:'内部错误'})", null);
                            }
                        } catch (Throwable ignored) {
                        }
                    });
                    return;
                }
                a.runOnUiThread(() -> {
                    try {
                        if (a.webView != null) {
                            a.webView.evaluateJavascript(
                                    "window.__subCb && window.__subCb(" + JSON.stringifyCb(cb) + "," + r + ")", null);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }, "sub-set-url").start();
        }

        /** 立即更新远程订阅（后台线程） */
        @JavascriptInterface
        public void subUpdateNow(final String cb) {
            new Thread(() -> {
                try {
                    final String r = SubscriptionManager.updateNow(a);
                    a.runOnUiThread(() -> {
                        try {
                            if (a.webView != null) {
                                a.webView.evaluateJavascript(
                                        "window.__subCb && window.__subCb(" + JSON.stringifyCb(cb) + "," + r + ")", null);
                            }
                        } catch (Throwable ignored) {
                        }
                    });
                } catch (final Throwable t) {
                    a.runOnUiThread(() -> {
                        try {
                            if (a.webView != null) {
                                a.webView.evaluateJavascript(
                                        "window.__subCb && window.__subCb(" + JSON.stringifyCb(cb) + ",{ok:false,error:'内部错误'})", null);
                            }
                        } catch (Throwable ignored) {
                        }
                    });
                }
            }, "sub-update-now").start();
        }

        @JavascriptInterface
        public void subSetEnabled(boolean on) {
            SubscriptionManager.setEnabled(a, on);
        }

        /* ───── v4.3.0 应用白名单（白名单内应用零扫描零点击零学习） ───── */

        /* ═══ v7.2.0 白名单全量应用列表（用户实锤）═══
         * 用户原话：「应用白名单搜索不到，不符合人的使用行为，搜索应该像
         * 浏览器那样，我输入qqQQ无论是大写小写都能检索到，或者直接把所有
         * 应用显示出来」。
         * 根因：旧 known 列表 = 白名单 ∪ 引擎战绩 byApp（上限 80）——新装
         * 用户/从未被拦截过的 App（QQ、微信等）根本不在列表里，搜索必然找不到。
         * 修复（两端配合）：本桥 known 改为枚举全部已安装可启动应用
         * （ACTION_MAIN + CATEGORY_LAUNCHER）∪ 白名单 ∪ 引擎战绩，上限
         * 80→500；白名单内排最前，其余 Collator(CHINA) 中文序（与 JS 端
         * 排序语义一致）；搜索大小写不敏感在 app.js 侧（v7.2.0 debounce）。
         * 另加 30s 静态内存缓存——app.js 每次切到首页 tab 都会调
         * renderWhitelist(true) → whitelistJson()，避免高频 PackageManager IPC。 */
        /** v7.2.0：whitelistJson 结果 30s 内存缓存（结果 JSON + 时间戳）；
         *  增删白名单后置空失效，绝不回吐旧 list */
        private static volatile String wlMemoJson = null;
        private static volatile long wlMemoAt = 0L;

        /** 白名单状态：{list:[当前白名单], known:[{pkg,label} 全量应用]}（v7.2.0
         *  起为全部已安装可启动应用 ∪ 白名单 ∪ 引擎战绩，上限 500 条） */
        @JavascriptInterface
        public String whitelistJson() {
            org.json.JSONObject o = new org.json.JSONObject();
            try {
                // v7.2.0：30 秒内直返缓存（高频桥调用防抖，免 PackageManager IPC）
                String memo = wlMemoJson;
                if (memo != null && System.currentTimeMillis() - wlMemoAt < 30000L) return memo;
                java.util.ArrayList<String> list = new java.util.ArrayList<>();
                java.util.HashSet<String> wl = com.zai.adshield.AdSkipService.parseWhitelistCsv(
                        com.zai.adshield.AdSkipService.whitelistCsvOf(a));
                list.addAll(wl);
                o.put("list", new org.json.JSONArray(list));
                // 全量应用三源合并（pkg → label；LinkedHashMap 保插入序）
                java.util.LinkedHashMap<String, String> known = new java.util.LinkedHashMap<>();
                // 源③：引擎战绩 byApp（保留旧逻辑——用户实际处理过的应用确保在列）
                try {
                    String snap = AdSkipService.snapshotState(a);
                    org.json.JSONObject st = snap != null ? new org.json.JSONObject(snap) : null;
                    org.json.JSONObject byApp = st != null && st.optJSONObject("stats") != null
                            ? st.optJSONObject("stats").optJSONObject("byApp") : null;
                    if (byApp != null) {
                        java.util.Iterator<String> it = byApp.keys();
                        while (it.hasNext()) known.put(it.next(), null); // label 稍后统一补全
                    }
                } catch (Exception ignored) {
                }
                // 源②：白名单里的包（即使已卸载也保留显示，勾选态不丢）
                for (String p : wl) {
                    if (!known.containsKey(p)) known.put(p, null);
                }
                // 源①（主力）：全部已安装可启动应用——新装/未被拦截过的 App
                // （QQ、微信）也能被 qqQQ 搜到的前提
                try {
                    java.util.HashSet<String> seen = new java.util.HashSet<>();
                    java.util.List<android.content.pm.ResolveInfo> ris =
                            a.getPackageManager().queryIntentActivities(
                                    new android.content.Intent(android.content.Intent.ACTION_MAIN)
                                            .addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0);
                    for (android.content.pm.ResolveInfo ri : ris) {
                        if (ri == null || ri.activityInfo == null
                                || ri.activityInfo.applicationInfo == null) continue;
                        String pkg = ri.activityInfo.applicationInfo.packageName;
                        if (pkg == null || pkg.isEmpty() || !seen.add(pkg)) continue; // 同包多入口去重
                        String label = null;
                        try {
                            CharSequence l = a.getPackageManager()
                                    .getApplicationLabel(ri.activityInfo.applicationInfo);
                            if (l != null && l.length() > 0) label = l.toString();
                        } catch (Throwable ignored) {
                        }
                        // 已在列（战绩/白名单）则仅补 label；新应用追加尾部
                        known.put(pkg, label);
                    }
                } catch (Throwable ignored) {
                }
                // label 兜底补全（一次到位，避免排序比较器里反复 PM IPC）
                for (java.util.Map.Entry<String, String> e : known.entrySet()) {
                    if (e.getValue() == null || e.getValue().isEmpty()) {
                        e.setValue(appLabelOf(e.getKey()));
                    }
                }
                // 排序：白名单内最前（与 JS 端一致），其余 Collator 中文序、同名按包名
                java.util.ArrayList<java.util.Map.Entry<String, String>> es =
                        new java.util.ArrayList<>(known.entrySet());
                final java.text.Collator coll = java.text.Collator.getInstance(java.util.Locale.CHINA);
                java.util.Collections.sort(es, (x, y) -> {
                    boolean wx = wl.contains(x.getKey()), wy = wl.contains(y.getKey());
                    if (wx != wy) return wx ? -1 : 1;
                    int c = coll.compare(x.getValue(), y.getValue());
                    return c != 0 ? c : x.getKey().compareTo(y.getKey());
                });
                org.json.JSONArray arr = new org.json.JSONArray();
                int n = 0;
                for (java.util.Map.Entry<String, String> e : es) {
                    if (n++ >= 500) break; // v7.2.0 展示上限 80→500（一般手机 100~400 款应用）
                    org.json.JSONObject e2 = new org.json.JSONObject();
                    e2.put("pkg", e.getKey());
                    e2.put("label", e.getValue());
                    arr.put(e2);
                }
                o.put("known", arr);
                String out = o.toString();
                wlMemoJson = out; // 记入缓存（30s TTL）
                wlMemoAt = System.currentTimeMillis();
                return out;
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        @JavascriptInterface
        public boolean whitelistAdd(String pkg) {
            boolean ok = AdSkipService.whitelistAdd(a, pkg);
            if (ok) wlMemoJson = null; // v7.2.0：白名单已变更，缓存失效（否则 30s 内回吐旧 list）
            return ok;
        }

        @JavascriptInterface
        public boolean whitelistRemove(String pkg) {
            boolean ok = AdSkipService.whitelistRemove(a, pkg);
            if (ok) wlMemoJson = null; // v7.2.0：白名单已变更，缓存失效
            return ok;
        }

        /** 应用名（PackageManager 查询；失败回退包名末段） */
        private String appLabelOf(String pkg) {
            try {
                android.content.pm.ApplicationInfo ai = a.getPackageManager().getApplicationInfo(pkg, 0);
                CharSequence l = a.getPackageManager().getApplicationLabel(ai);
                if (l != null && l.length() > 0) return l.toString();
            } catch (Throwable ignored) {
            }
            int i = pkg.lastIndexOf('.');
            return i >= 0 && pkg.length() - i - 1 > 1 ? pkg.substring(i + 1) : pkg;
        }

        /** 回调名安全包装（仅允许合法 JS 标识符） */
        private static final class JSON {
            static String stringifyCb(String s) {
                if (s == null || !s.matches("[A-Za-z_$][A-Za-z0-9_$]*")) return "null";
                return "\"" + s + "\"";
            }
        }

        /* ───── v7.9.7：日志本地导出 + 自动上传授权桥（PAT 移除轮） ─────
         * 旧 netPostIssue（内置令牌直发 GitHub Issue）已移除——客户端零 GitHub
         * 写凭据。手动日志 = 本地导出（应用外部私有目录，无任何新权限）+
         * 系统分享面板（ACTION_SEND 纯文本，用户自行投递：邮件/网盘/仓库
         * Issue 浏览器预填）。自动上传授权 = 独立开关（默认关，见
         * AdSkipService.KEY_AUTO_LOG_UPLOAD；v7.9.7 中转服务未上线结构性关闭）。 */

        /** 导出引擎日志到本地文件（应用外部私有目录 logs/，无新权限）。
         *  @return {ok:true, path, bytes} 或 {ok:false, error} */
        @JavascriptInterface
        public String exportLog(String title, String body) {
            try {
                long now = System.currentTimeMillis();
                String t = title == null ? "" : (title.length() > 128 ? title.substring(0, 128) : title);
                String b = body == null ? "" : (body.length() > 24000 ? body.substring(0, 24000) : body);
                String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date(now));
                java.io.File dir = a.getExternalFilesDir("logs");
                if (dir == null) dir = a.getFilesDir();
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                java.io.File f = new java.io.File(dir, "adshield-log-" + stamp + ".md");
                java.io.OutputStream os = new java.io.FileOutputStream(f);
                os.write(("# " + t + "\n\n" + b + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.close();
                org.json.JSONObject out = new org.json.JSONObject();
                out.put("ok", true);
                out.put("path", f.getAbsolutePath());
                out.put("bytes", f.length());
                return out.toString();
            } catch (Throwable t) {
                try {
                    org.json.JSONObject out = new org.json.JSONObject();
                    out.put("ok", false);
                    out.put("error", "导出失败：" + t.getClass().getSimpleName());
                    return out.toString();
                } catch (Throwable ignored) {
                    return "{\"ok\":false,\"error\":\"导出失败\"}";
                }
            }
        }

        /** 系统分享面板分享纯文本日志（用户自行选择投递渠道；零凭据零上传）。 */
        @JavascriptInterface
        public void shareLogText(String text) {
            try {
                final String safe = text == null ? "" : (text.length() > 24000 ? text.substring(0, 24000) : text);
                a.runOnUiThread(() -> {
                    try {
                        Intent it = new Intent(Intent.ACTION_SEND);
                        it.setType("text/plain");
                        it.putExtra(Intent.EXTRA_TEXT, safe);
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        a.startActivity(Intent.createChooser(it, "分享引擎日志"));
                    } catch (Exception ignored) {
                    }
                });
            } catch (Throwable ignored) {
            }
        }

        /** v7.9.7：自动日志上传授权状态（默认关）。 */
        @JavascriptInterface
        public boolean logUploadConsent() {
            try {
                return AdSkipService.autoLogUploadEnabled(a);
            } catch (Throwable t) {
                return false;
            }
        }

        /** v7.9.7：设置自动日志上传授权（仅写授权位与采集门；上传行为另受中转服务
         *  配置与服务端 24h 限额双重门控——授权 ≠ 上传）。 */
        @JavascriptInterface
        public void setLogUploadConsent(boolean enabled) {
            try {
                AdSkipService.setAutoLogUploadEnabled(a, enabled);
            } catch (Throwable ignored) {
            }
        }

        /** v8.0.0：上传状态行真数据（待传批次数 / 24h 已提交（服务端权威，本地
         *  仅展示参考）/ 失败检视数 / 最近失败原因）。 */
        @JavascriptInterface
        public String logUploadStatus() {
            try {
                return com.zai.adshield.core.LogBatchStore.get(a)
                        .overview(System.currentTimeMillis()).toString();
            } catch (Throwable t) {
                return "{\"pending\":0,\"failed\":0,\"acked24h\":0,\"lastAckedAt\":0,\"lastError\":\"\"}";
            }
        }

        /** v8.0.0：手动「立即提交」（用户主动操作——同一服务端 24h/3 限额与
         *  batchId 幂等，绝不绕过；单后台守护线程执行，绝不阻塞页面）。 */
        @JavascriptInterface
        public void logUploadSubmitNow() {
            try {
                com.zai.adshield.core.LogUploadDispatcher.get(a).requestUploadNow();
            } catch (Throwable ignored) {
            }
        }

        /** v8.0.0：清理本地待上传日志（用户主动操作——ACK 前批次一并清理；
         *  只删本地文件，不影响服务端已建 Issue）。 */
        @JavascriptInterface
        public String logUploadClearPending() {
            try {
                int n = com.zai.adshield.core.LogBatchStore.get(a).clearPending();
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("ok", true);
                o.put("cleared", n);
                return o.toString();
            } catch (Throwable t) {
                return "{\"ok\":false,\"cleared\":0}";
            }
        }

        /** v4.5.0：后台深挖拉活（MainActivity.onResume 自动拉活用；
         *  与页面 tryAutoReviveDeep 同一逻辑，静默版——不打扰 UI）。
         *  v5.2.0：15s 自适应 + 每轮复查登记（吊销即退）。 */
        static String deepReviveOnce(Context ctx) {
            try {
                if (AdSkipService.isAlive()) {
                    if (!AdSkipService.masterState(ctx)) AdSkipService.setMaster(ctx, true);
                    return "{\"ok\":true,\"running\":true}";
                }
                long started = System.currentTimeMillis();
                long[] plan = com.zai.adshield.core.RestoreGuard.pollPlan(REVIVE_WINDOW_MS);
                for (long at : plan) {
                    long wait = at - (System.currentTimeMillis() - started);
                    if (wait > 0) Thread.sleep(Math.min(wait, 500));
                    if (AdSkipService.isAlive()) break;
                    if (!AdSkipService.registrationPresent(ctx)) {
                        /* v5.6.0：吊销先免设置自愈（持久模式授权设备）；
                         * 未授权 → 交回 onResume 后续通知流引导 */
                        if (!(AdSkipService.canSecureSelfHeal(ctx)
                                && AdSkipService.secureSelfHeal(ctx))) {
                            return "{\"ok\":false,\"reason\":\"disabled\"}";
                        }
                    }
                    kickA11yManagerOf(ctx);
                }
                if (AdSkipService.isAlive()) {
                    if (!AdSkipService.masterState(ctx)) AdSkipService.setMaster(ctx, true);
                    return "{\"ok\":true,\"revived\":true}";
                }
                return "{\"ok\":false,\"reason\":\"need_toggle\"}";
            } catch (Throwable t) {
                return "{\"ok\":false,\"reason\":\"error\"}";
            }
        }

        /** v5.2.0：静态版踢 AMS（deepReviveOnce 用，不依赖 Activity 实例） */
        private static void kickA11yManagerOf(Context ctx) {
            try {
                android.view.accessibility.AccessibilityManager am =
                        (android.view.accessibility.AccessibilityManager)
                                ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
                if (am != null) {
                    am.getEnabledAccessibilityServiceList(
                            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                }
            } catch (Throwable ignored) {
            }
        }

        /* ═══ v5.2.0 保活体检（issue#56：防「清理后台/覆盖安装吊销无障碍」）═══
         * 近 5 年全品牌自启动/省电策略直达——候选全部来自 RestoreGuard
         * 硬编码系统包注册表（id 枚举，零任意组件注入面），逐候选
         * resolveActivity 校验（manifest queries 已声明可见性），
         * 全部失败回退应用详情页（永远有下一步）。 */

        /** v5.2.0：直达本应用通知设置页（保活体检「通知权限」项；<8.0 回退应用详情页） */
        @JavascriptInterface
        public void openNotifSettings() {
            try {
                Intent it = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                it.putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(it);
            } catch (Throwable t) {
                try {
                    Intent it = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + a.getPackageName()));
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(it);
                } catch (Throwable ignored) {
                }
            }
        }

        /** 通知权限是否已授予（Android 13+；以下恒 true） */
        @JavascriptInterface
        public boolean notifPermGranted() {
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    return a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED;
                }
            } catch (Throwable ignored) {
            }
            return true;
        }

        /** 保活体检项（品牌自适应）：{family, familyLabel, items:[{id,label,desc}]} */
        @JavascriptInterface
        public String keepAliveItems() {
            org.json.JSONObject o = new org.json.JSONObject();
            try {
                String family = com.zai.adshield.core.RestoreGuard.familyOf(
                        Build.MANUFACTURER, Build.BRAND, Build.MODEL);
                o.put("family", family);
                o.put("familyLabel", com.zai.adshield.core.RestoreGuard.familyLabel(family));
                org.json.JSONArray arr = new org.json.JSONArray();
                com.zai.adshield.core.RestoreGuard.KeepAliveItem[] items =
                        com.zai.adshield.core.RestoreGuard.itemsFor(family);
                for (com.zai.adshield.core.RestoreGuard.KeepAliveItem it : items) {
                    org.json.JSONObject e = new org.json.JSONObject();
                    e.put("id", it.id);
                    e.put("label", it.label);
                    e.put("desc", it.desc);
                    arr.put(e);
                }
                o.put("items", arr);
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        /** 直达体检项对应厂商设置页（逐候选 resolveActivity 回退；
         *  最终回退应用详情页）。返回 {ok, fallback?} */
        @JavascriptInterface
        public String openKeepAliveTarget(String id) {
            org.json.JSONObject out = new org.json.JSONObject();
            try {
                String family = com.zai.adshield.core.RestoreGuard.familyOf(
                        Build.MANUFACTURER, Build.BRAND, Build.MODEL);
                com.zai.adshield.core.RestoreGuard.KeepAliveItem target = null;
                for (com.zai.adshield.core.RestoreGuard.KeepAliveItem it
                        : com.zai.adshield.core.RestoreGuard.itemsFor(family)) {
                    if (it.id.equals(id)) {
                        target = it;
                        break;
                    }
                }
                if (target != null) {
                    android.content.pm.PackageManager pm = a.getPackageManager();
                    for (String[] cand : target.candidates) {
                        try {
                            Intent it = new Intent();
                            it.setComponent(new android.content.ComponentName(cand[0], cand[1]));
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            if ("miui_pkg".equals(target.extra)) {
                                it.putExtra("package_name", a.getPackageName());
                            }
                            if (pm.resolveActivity(it, 0) != null) {
                                a.startActivity(it);
                                out.put("ok", true);
                                return out.toString();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                // 全部失败/未知 id：应用详情页（永远可用，权限/电池入口都在）
                try {
                    Intent it = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + a.getPackageName()));
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(it);
                    out.put("ok", true).put("fallback", true);
                } catch (Throwable t) {
                    out.put("ok", false);
                }
            } catch (Throwable t) {
                try {
                    out.put("ok", false);
                } catch (Exception ignored) {
                }
            }
            return out.toString();
        }

        /** v5.2.0：通知入口自动恢复已被页面接管（撤销 MainActivity 重试链） */
        @JavascriptInterface
        public void autoRestoreHandled() {
            a.autoRestoreHandled();
        }

        /* ═══ v5.6.0 零打扰持久模式（免设置自愈）状态桥 ═══
         * 返回 {granted, cmd, desc}——页面「持久守护」卡片渲染用。 */
        @JavascriptInterface
        public String secureHealStatus() {
            org.json.JSONObject o = new org.json.JSONObject();
            try {
                o.put("granted", AdSkipService.canSecureSelfHeal(a));
                o.put("cmd", com.zai.adshield.core.RestoreGuard.secureGrantCmd(a.getPackageName()));
                o.put("desc", com.zai.adshield.core.RestoreGuard.secureModeDesc());
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        /** 查询 GitHub 最新 Release（公共仓库匿名可读） */
        @JavascriptInterface
        public String netGetLatestRelease() {
            return Net.latestRelease();
        }

        /* ───── 应用内更新：页内下载 → 进度 → 安装（不跳浏览器） ───── */

        /** v7.3.0 安全：digest/size 从 GitHub API（HTTPS）元数据透传给
         *  Downloader——安装前 SHA-256 完整性校验的期望值。 */
        @JavascriptInterface
        public String startUpdateDownload(String url, String fileName, String digest, long sizeBytes) {
            return Downloader.start(a, url, fileName, digest, sizeBytes);
        }

        @JavascriptInterface
        public String updateProgress() {
            return Downloader.progress(a);
        }

        /** v3.2.0 能耗画像：本次开机累计（事件/扫描/返回键 + 弹窗战果）
         *  v5.3.0：新增探针/省电档案/突发窗计数；估算模型换用 PowerGovernor
         *  （探针 0.05µWh/次 ≈ 扫描成本的 1/14） */
        @JavascriptInterface
        public String energyStats() {
            AdSkipService s = AdSkipService.instance;
            org.json.JSONObject out = new org.json.JSONObject();
            try {
                if (s != null) {
                    out.put("ok", true);
                    out.put("events", s.energyEvents);
                    out.put("probes", s.energyProbes);
                    out.put("scans", s.energyScans);
                    out.put("profileSkips", s.energyProfileSkips);
                    out.put("bursts", s.energyBursts);
                    out.put("backs", s.energyBacks);
                    out.put("popup", s.popupBlocks);
                    out.put("posVerified", s.posLearnVerified);
                    out.put("posDropped", s.posLearnDropped);
                    // 近似模型：0.22µWh/事件 + 0.05µWh/探针 + 0.72µWh/扫描 + 0.9µWh/返回键
                    double microWh = com.zai.adshield.core.PowerGovernor.microWh(
                            s.energyEvents, s.energyProbes, s.energyScans, s.energyBacks);
                    out.put("microWh", Math.round(microWh * 10) / 10.0);
                } else {
                    out.put("ok", false);
                }
            } catch (Exception ignored) {
            }
            return out.toString();
        }

        @JavascriptInterface
        public String installUpdate() {
            String r = Downloader.install(a);
            // Android 8+ 未授权「安装未知应用」→ needPermission=true，由页面引导授权后重试
            try {
                JSONObject d = new JSONObject(r);
                if (!d.optBoolean("ok") && d.optBoolean("needPermission", false)) {
                    Downloader.openInstallPermission(a);
                }
            } catch (Exception ignored) {
            }
            return r;
        }

        /** 用系统浏览器打开链接（仅开源仓库等只读入口；更新下载不走这里）。
         *  v4.5.0 安全：仅允许 http/https（拒绝 file/intent/javascript 等
         *  危险 scheme 经出桥透传），且仅限本仓库/GitHub 域名。
         *  v7.3.0 加固：仅 https + URL 解析后 host 精确/后缀匹配（
         *  "https://github.com.evil.com/" 类构造 URL 拒绝）。 */
        @JavascriptInterface
        public void openUrl(String url) {
            try {
                if (url == null) return;
                String u = url.trim();
                if (!u.startsWith("https://")) return;
                if (!Downloader.hostAllowed(u)) return;
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(it);
            } catch (Exception ignored) {
            }
        }
    }

    /* ═══ v4.5.0 WebView 硬化：URL 劫持防护（攻击面收缩核心）═══
     * 本地离线页装载了带能力的 JS 桥——若页面被诱导跳转到远程页面（更新
     * 横幅里的链接/订阅预览/恶意注入），远程页面将直接拿到 AndroidApp 桥
     * （kvSet 任意写、shareLogText 分享等）。v7.9.7 起 netPostIssue（内置
     * 令牌直发 GitHub Issue）已随 PAT 移除下线——桥已无任何凭据能力，
     * 劫持面进一步收缩，但白名单制保持不变（纵深防御不因单点移除而松绑）。
     * 对策：shouldOverrideUrlLoading 白名单制——只允许本应用 asset 目录
     * 内导航；http(s) 链接一律转交系统浏览器（无桥）；其余 scheme 全部
     * 拦截。返回 true = 本应用接管，WebView 绝不加载。 */
    private static final class HardenedWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                if (url == null) return true;
                String u = url.trim();
                if (u.startsWith("file:///android_asset/")) return false; // 本地页面放行
                if (u.startsWith("about:")) return false;                  // 页内占位
                if (u.startsWith("https://") || u.startsWith("http://")) {
                    try {
                        // 外部链接 → 系统浏览器打开（无任何 JS 桥暴露）
                        Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        view.getContext().startActivity(it);
                    } catch (Exception ignored) {
                    }
                }
                // file:// 其他路径 / intent:// / javascript: / 自定义 scheme：全部拦截
                return true;
            } catch (Throwable t) {
                return true;
            }
        }
    }
}
