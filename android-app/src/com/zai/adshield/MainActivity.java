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
        webView.addJavascriptInterface(new Bridge(this), "AndroidApp");
        webView.setWebViewClient(new WebViewClient());
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
            return "3.8.1";
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
            return AdSkipService.instance != null;
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
            SharedPreferences.Editor e = a.getSharedPreferences("adshield", Context.MODE_PRIVATE).edit();
            if (value == null) e.remove(key);
            else e.putString(key, value);
            e.apply();
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
                return 30801;
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

        /** 自动拉活（v3.14.0 简化：ADB 自愈已移除）：
         *  · 服务已运行 → ok；
         *  · 设置仍登记且服务死 → 进程已随 App 打开拉起，AMS 通常自动重绑，
         *    观察 ≤1.5s；仍未重绑 → need_toggle 由页面引导一步直达服务开关；
         *  · 未登记 → disabled 由页面引导开启。
         *  （无障碍服务受 BIND_ACCESSIBILITY_SERVICE 保护，任何应用都不能
         *   start/bind 它——v3.6.0 的 startService 拉活实为 SecurityException；
         *   v3.8.0 的 WRITE_SECURE_SETTINGS 自愈需 adb 授权太麻烦，v3.14.0 移除）
         *  返回 JSON：{ok, running?, revived?, enabledInSettings?, reason?} */
        @JavascriptInterface
        public String tryAutoRevive() {
            org.json.JSONObject out = new org.json.JSONObject();
            try {
                if (AdSkipService.instance != null) {
                    out.put("ok", true).put("running", true);
                    return out.toString();
                }
                boolean enabled = a11yEnabledInSettings();
                out.put("enabledInSettings", enabled);
                if (!enabled) {
                    out.put("ok", false).put("reason", "disabled");
                    return out.toString();
                }
                // 设置仍登记：进程已拉起，AMS 通常自动重绑——观察 1.5s（不再空转 10s）
                for (int i = 0; i < 6 && AdSkipService.instance == null; i++) {
                    Thread.sleep(250);
                }
                boolean revived = AdSkipService.instance != null;
                out.put("ok", revived).put("revived", revived);
                if (!revived) out.put("reason", "need_toggle"); // 页面引导一步直达服务开关
            } catch (Throwable t) {
                try {
                    out.put("ok", false).put("reason", "error");
                } catch (Exception ignored) {
                }
            }
            return out.toString();
        }

        /* ───── 问题反馈 / 引擎日志上传（内置令牌，无任何用户配置） ───── */

        /** 创建反馈 Issue（引擎日志上传共用此通道）；令牌内置混淆存储（Net.builtInToken） */
        @JavascriptInterface
        public String netPostIssue(String title, String body) {
            return Net.postIssue(title, body);
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

        /** 用系统浏览器打开链接（仅开源仓库等只读入口；更新下载不走这里） */
        @JavascriptInterface
        public void openUrl(String url) {
            try {
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(it);
            } catch (Exception ignored) {
            }
        }
    }
}
