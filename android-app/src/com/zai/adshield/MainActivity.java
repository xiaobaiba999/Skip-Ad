package com.zai.adshield;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/**
 * 主界面（v3.0.0）
 * ─────────────────────────────────────────────
 * · WebView 装载离线页面 assets/www（演示场 / 情报站 / 学习中心 / 关于）
 * · 启动即静默检查 GitHub Releases：发现新版 → 页面顶部弹出更新横幅，
 *   页内直接下载（系统 DownloadManager）→ 进度条 → 完成后拉起安装，全程不跳浏览器
 * · 问题反馈使用内置混淆令牌（Net.builtInToken），界面零 Token 输入
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
            return "3.0.0";
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
                return 30000;
            }
        }

        /* ───── 问题反馈（内置令牌，无任何用户配置） ───── */

        /** 创建反馈 Issue；令牌内置混淆存储（Net.builtInToken），同步阻塞在 JSBridge 线程执行 */
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

        /** v3.0.0 能耗画像：本次开机累计（事件/扫描/估算 µWh） */
        @JavascriptInterface
        public String energyStats() {
            AdSkipService s = AdSkipService.instance;
            org.json.JSONObject out = new org.json.JSONObject();
            try {
                if (s != null) {
                    out.put("ok", true);
                    out.put("events", s.energyEvents);
                    out.put("scans", s.energyScans);
                    // 近似模型：0.22µWh/事件 + 0.72µWh/扫描 + 0.56µWh/代点（代点≈扫描数命中场景）
                    double microWh = s.energyEvents * 0.22 + s.energyScans * 0.72;
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
