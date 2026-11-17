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
 * · 问题反馈 / 引擎日志上传使用内置混淆令牌（Net.builtInToken），界面零 Token 输入
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

        // v3.5.0 保活：通知权限（13+）——保活常驻通知与恢复提醒必需
        requestNotifPermission();
        // v3.5.0：从「待恢复」通知点入 → 自动跳无障碍设置（两步恢复的第一步）
        // （注意：匿名内部类会触发 d8 8.2 NPE bug，一律用 lambda）
        if (getIntent() != null && getIntent().getBooleanExtra("jump_a11y", false)) {
            new Handler(Looper.getMainLooper()).postDelayed(this::openA11y, 500);
        }
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
         * （v4.4.0 的 6s 深度拉活）——用户点开卫士那一刻防护就在回来的
         * 路上，无需任何手动操作，更不去系统设置。 */
        try {
            if (!AdSkipService.isAlive() && a11yEnabledInSettings()) {
                new Thread(() -> {
                    try {
                        Bridge.deepReviveOnce(this);
                    } catch (Throwable ignored) {
                    }
                }, "auto-revive").start();
            }
        } catch (Throwable ignored) {
        }
    }

    /** v4.5.0：系统设置是否仍登记本服务（onResume 自动拉活判定用） */
    private boolean a11yEnabledInSettings() {
        try {
            String s = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return false;
            android.content.ComponentName cn =
                    new android.content.ComponentName(this, AdSkipService.class);
            return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString());
        } catch (Throwable t) {
            return false;
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

        @JavascriptInterface
        public void openAccessibilitySettings() {
            try {
                Intent it = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(it);
            } catch (Exception ignored) {
            }
        }

        /** v3.8.0：直达本服务无障碍详情页（Android 11+ 一个开关即恢复；旧系统回退列表页） */
        @JavascriptInterface
        public void openA11yDetail() {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    Intent it = new Intent(ACTION_A11Y_DETAILS);
                    it.putExtra(Intent.EXTRA_COMPONENT_NAME,
                            new android.content.ComponentName(a, AdSkipService.class));
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(it);
                    return;
                }
                throw new IllegalStateException("pre-R");
            } catch (Exception e) {
                openAccessibilitySettings();
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
         *  ——true 表示服务被系统杀掉但没被关闭，可尝试自动拉活重绑 */
        @JavascriptInterface
        public boolean a11yEnabledInSettings() {
            try {
                String s = Settings.Secure.getString(
                        a.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (s == null) return false;
                android.content.ComponentName cn =
                        new android.content.ComponentName(a, AdSkipService.class);
                return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString());
            } catch (Throwable t) {
                return false;
            }
        }

        /** 自动拉活（v4.4.0 深度拉活版）：
         *  · 服务已运行 → ok（若处于暂停自动解除——用户点「恢复」的意图）
         *  · 设置仍登记且服务死 → 后台线程深挖最长 6s（v3.14.0 只等 1.5s，
         *    MIUI/HyperOS 重绑慢的 ROM 不够→被误判 need_toggle→跳系统设置
         *    看 10 秒强制警告，「恢复防护很麻烦」的元凶）；每 500ms 用
         *    AccessibilityManager 查询踢一次系统（促使 AMS 重绑）；
         *  · 未登记 → disabled 由页面引导开启。
         *  （无障碍服务受 BIND_ACCESSIBILITY_SERVICE 保护，任何应用都不能
         *   start/bind 它；设置页 10 秒强制等待仅剩「开关真被关」时才需要）
         *  返回 JSON：{ok, running?, revived?, resumed?, enabledInSettings?, reason?} */
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
                boolean enabled = a11yEnabledInSettings();
                out.put("enabledInSettings", enabled);
                if (!enabled) {
                    out.put("ok", false).put("reason", "disabled");
                    return out.toString();
                }
                // 设置仍登记：进程已拉起，AMS 自动重绑——深度观察 6s
                //（每 500ms 踢一次 AccessibilityManager，重绑慢的 ROM 也能等到）
                long deadline = System.currentTimeMillis() + 6000;
                while (System.currentTimeMillis() < deadline && !AdSkipService.isAlive()) {
                    kickA11yManager();
                    Thread.sleep(500);
                }
                boolean revived = AdSkipService.isAlive();
                if (revived) {
                    boolean wasPaused = !AdSkipService.masterState(a);
                    if (wasPaused) AdSkipService.setMaster(a, true); // 拉活成功 → 暂停自动解除
                    out.put("ok", true).put("revived", true).put("resumed", wasPaused);
                } else {
                    out.put("ok", false).put("reason", "need_toggle"); // 页面提示重试/手动，绝不自动跳系统设置
                }
            } catch (Throwable t) {
                try {
                    out.put("ok", false).put("reason", "error");
                } catch (Exception ignored) {
                }
            }
            return out.toString();
        }

        /** v4.4.0：异步深度拉活（后台线程 6s 深挖，不阻塞 JS Bridge 线程；
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

        /** 设置/清空远程订阅 URL（立即拉取；后台线程执行） */
        @JavascriptInterface
        public void subSetUrl(final String url, final String cb) {
            new Thread(() -> {
                try {
                    final String r = SubscriptionManager.setUrl(a, url);
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

        /** 白名单状态：{list:[当前白名单], known:[{pkg,label} 近期处理过的应用]} */
        @JavascriptInterface
        public String whitelistJson() {
            org.json.JSONObject o = new org.json.JSONObject();
            try {
                java.util.ArrayList<String> list = new java.util.ArrayList<>();
                java.util.HashSet<String> wl = com.zai.adshield.AdSkipService.parseWhitelistCsv(
                        com.zai.adshield.AdSkipService.whitelistCsvOf(a));
                list.addAll(wl);
                o.put("list", new org.json.JSONArray(list));
                // 已知应用：白名单 ∪ 引擎战绩（byApp）——用户识别得到的应用列表
                java.util.TreeMap<String, Integer> known = new java.util.TreeMap<>();
                for (String p : wl) known.put(p, 1);
                try {
                    String snap = AdSkipService.snapshotState(a);
                    org.json.JSONObject st = snap != null ? new org.json.JSONObject(snap) : null;
                    org.json.JSONObject byApp = st != null && st.optJSONObject("stats") != null
                            ? st.optJSONObject("stats").optJSONObject("byApp") : null;
                    if (byApp != null) {
                        java.util.Iterator<String> it = byApp.keys();
                        while (it.hasNext()) known.put(it.next(), 1);
                    }
                } catch (Exception ignored) {
                }
                org.json.JSONArray arr = new org.json.JSONArray();
                int n = 0;
                for (String pkg : known.keySet()) {
                    if (n++ >= 80) break; // 展示上限（防止超长列表）
                    org.json.JSONObject e = new org.json.JSONObject();
                    e.put("pkg", pkg);
                    e.put("label", appLabelOf(pkg));
                    arr.put(e);
                }
                o.put("known", arr);
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        @JavascriptInterface
        public boolean whitelistAdd(String pkg) {
            return AdSkipService.whitelistAdd(a, pkg);
        }

        @JavascriptInterface
        public boolean whitelistRemove(String pkg) {
            return AdSkipService.whitelistRemove(a, pkg);
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

        /* ───── 问题反馈 / 引擎日志上传（内置令牌，无任何用户配置） ───── */

        /** 创建反馈 Issue（引擎日志上传共用此通道）；令牌内置混淆存储（Net.builtInToken）。
         *  v4.5.0 安全：标题/正文长度上限 + 15s 频控（防被滥刷 issue 消耗
         *  配额；WebView URL 劫持防护之外的第二道闸） */
        @JavascriptInterface
        public String netPostIssue(String title, String body) {
            try {
                long now = System.currentTimeMillis();
                if (now - lastPostIssueAt < 15000) {
                    return "{\"ok\":false,\"error\":\"提交太频繁，请稍后再试\"}";
                }
                lastPostIssueAt = now;
                String t = title == null ? "" : (title.length() > 128 ? title.substring(0, 128) : title);
                String b = body == null ? "" : (body.length() > 24000 ? body.substring(0, 24000) : body);
                return Net.postIssue(t, b);
            } catch (Throwable t) {
                return "{\"ok\":false,\"error\":\"内部错误\"}";
            }
        }

        private static volatile long lastPostIssueAt = 0L;

        /** v4.5.0：后台深挖拉活（MainActivity.onResume 自动拉活用；
         *  与页面 tryAutoReviveDeep 同一逻辑，静默版——不打扰 UI） */
        static String deepReviveOnce(Context ctx) {
            try {
                if (AdSkipService.isAlive()) {
                    if (!AdSkipService.masterState(ctx)) AdSkipService.setMaster(ctx, true);
                    return "{\"ok\":true,\"running\":true}";
                }
                long deadline = System.currentTimeMillis() + 6000;
                while (System.currentTimeMillis() < deadline && !AdSkipService.isAlive()) {
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
                    Thread.sleep(500);
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

        /** 查询 GitHub 最新 Release（公共仓库匿名可读） */
        @JavascriptInterface
        public String netGetLatestRelease() {
            return Net.latestRelease();
        }

        /* ───── 应用内更新：页内下载 → 进度 → 安装（不跳浏览器） ───── */

        @JavascriptInterface
        public String startUpdateDownload(String url, String fileName) {
            return Downloader.start(a, url, fileName);
        }

        @JavascriptInterface
        public String updateProgress() {
            return Downloader.progress(a);
        }

        /** v3.2.0 能耗画像：本次开机累计（事件/扫描/返回键 + 弹窗战果） */
        @JavascriptInterface
        public String energyStats() {
            AdSkipService s = AdSkipService.instance;
            org.json.JSONObject out = new org.json.JSONObject();
            try {
                if (s != null) {
                    out.put("ok", true);
                    out.put("events", s.energyEvents);
                    out.put("scans", s.energyScans);
                    out.put("backs", s.energyBacks);
                    out.put("popup", s.popupBlocks);
                    // 近似模型：0.22µWh/事件 + 0.72µWh/扫描 + 0.9µWh/返回键
                    double microWh = s.energyEvents * 0.22 + s.energyScans * 0.72 + s.energyBacks * 0.9;
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
         *  危险 scheme 经出桥透传），且仅限本仓库/GitHub 域名。 */
        @JavascriptInterface
        public void openUrl(String url) {
            try {
                if (url == null) return;
                String u = url.trim();
                if (!u.startsWith("https://") && !u.startsWith("http://")) return;
                String low = u.toLowerCase();
                if (!low.contains("github.com") && !low.contains("xiaobaiba999")) return;
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(it);
            } catch (Exception ignored) {
            }
        }
    }

    /* ═══ v4.5.0 WebView 硬化：URL 劫持防护（攻击面收缩核心）═══
     * 本地离线页装载了带 GitHub 令牌能力的 JS 桥——若页面被诱导跳转到
     * 远程页面（更新横幅里的链接/订阅预览/恶意注入），远程页面将直接
     * 拿到 AndroidApp 桥（netPostIssue 内置令牌、kvSet 任意写）。
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
