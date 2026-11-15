package com.zai.adshield;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

    /** 暴露给离线页面 www/index.html 的原生桥（静态类 + 外部引用，规避 d8 8.2 内部类 bug） */
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
            try {
                PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                return pi.versionName;
            } catch (Exception e) {
                return "1.1.0";
            }
        }

        @JavascriptInterface
        public int versionCode() {
            try {
                PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                return pi.versionCode;
            } catch (Exception e) {
                return 2;
            }
        }
    }
}
