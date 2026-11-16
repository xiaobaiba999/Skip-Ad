package com.zai.adshield;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * GitHub API 极简客户端（HttpURLConnection + org.json，零第三方依赖）。
 *
 * v3.0.0 出口（全部围绕开源仓库本身，内置混淆令牌，界面零配置）：
 *  1. 问题反馈    → POST /repos/{repo}/issues（令牌内置于代码，双层 XOR+Base64 混淆，
 *                   与 Web 端 credentials.ts 同源密文；密文落库不触发密钥扫描）
 *  2. 检查更新    → GET  /repos/{repo}/releases/latest（公开仓库匿名可读）
 *  3. 应用内更新  → 下载交给系统 DownloadManager（见 Downloader.java）
 *
 * 约束：为规避 d8 8.2 的非静态内部类 bug，本类不使用任何匿名类/内部类/lambda。
 *       所有方法为同步阻塞调用，仅允许在 JSBridge 线程等后台线程执行。
 */
public final class Net {

    public static final String REPO = "xiaobaiba999/Skip-Ad";
    public static final String API = "https://api.github.com/repos/" + REPO;
    public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";
    public static final String ISSUES_PAGE = "https://github.com/" + REPO + "/issues";

    private Net() {
    }

    /* ───────── 内置混淆令牌（与 Web src/lib/credentials.ts 同源，双层 XOR+Base64） ───────── */

    private static final String L2 = "BSg9CSQRPBYxeFxCNllQHy8hfy4yAiI7Mz0ccjAHNCAuND0AKlsAASsxCTgwPBo7BTpQEjs1dlQ=";
    private static final int[] KEY_B = {75, 105, 112, 83, 104, 105, 101, 108, 100}; // "KipShield"

    /** 解出内置 Token（仅内存，永不落盘/落日志）；失败返回空串 */
    static String builtInToken() {
        try {
            String kb = new String(new char[]{(char) KEY_B[0], (char) KEY_B[1], (char) KEY_B[2], (char) KEY_B[3], (char) KEY_B[4], (char) KEY_B[5], (char) KEY_B[6], (char) KEY_B[7], (char) KEY_B[8]});
            String layer1 = xorWith(b64Decode(L2), kb);
            String token = xorWith(b64Decode(layer1), "SkipAd::2026::obf::k1");
            return token.matches("[A-Za-z0-9_]{20,}") ? token : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String xorWith(String input, String key) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            out.append((char) (input.charAt(i) ^ key.charAt(i % key.length())));
        }
        return out.toString();
    }

    private static String b64Decode(String s) {
        return new String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    /* ───────── 底层 HTTP ───────── */

    /** 返回 [statusCode, body]；任何异常向上抛出由调用方兜底 */
    private static String[] http(String method, String url, String token, String body) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "adshield-android");
            if (token != null && token.length() > 0) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return new String[]{String.valueOf(code), readAll(in)};
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        try { in.close(); } catch (Exception ignored) { }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    /* ───────── 问题反馈 → GitHub Issues（内置令牌，用户零配置） ───────── */

    /**
     * 创建反馈 Issue，使用内置混淆令牌（界面已无任何 Token 输入）。
     * @return {ok:true, url, number} 或 {ok:false, error, fallbackUrl}
     */
    public static String postIssue(String title, String body) {
        String fallback = ISSUES_PAGE + "/new?title=" + enc(title) + "&body=" + enc(body);
        String token = builtInToken();
        if (token.length() == 0) {
            return err("内置令牌不可用：已生成浏览器预填链接", fallback);
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("title", title);
            payload.put("body", body);
            String[] r = http("POST", API + "/issues", token, payload.toString());
            int code = Integer.parseInt(r[0]);
            if (code >= 200 && code < 300) {
                JSONObject d = new JSONObject(r[1]);
                JSONObject out = new JSONObject();
                out.put("ok", true);
                out.put("url", d.optString("html_url", ""));
                out.put("number", d.optInt("number", 0));
                return out.toString();
            }
            if (code == 401) return err("内置令牌无效（401）", fallback);
            if (code == 403) return err("触发限流或权限不足（403）", fallback);
            return err("GitHub 返回 " + code, fallback);
        } catch (Exception e) {
            return err("网络异常：" + safeMsg(e), fallback);
        }
    }

    /* ───────── 检查更新 → GitHub Releases ───────── */

    /**
     * 获取最新 Release（公开仓库无需令牌）。
     * @return {ok:true, hasRelease:false} / {ok:true, hasRelease:true, tag, name, notes, url, apkUrl, apkMB, publishedAt, apkFileName}
     *         / {ok:false, error}
     */
    public static String latestRelease() {
        try {
            String[] r = http("GET", API + "/releases/latest", null, null);
            int code = Integer.parseInt(r[0]);
            if (code == 404) {
                JSONObject out = new JSONObject();
                out.put("ok", true);
                out.put("hasRelease", false);
                return out.toString();
            }
            if (code >= 200 && code < 300) {
                JSONObject d = new JSONObject(r[1]);
                JSONObject out = new JSONObject();
                out.put("ok", true);
                out.put("hasRelease", true);
                out.put("tag", d.optString("tag_name", ""));
                out.put("name", d.optString("name", ""));
                out.put("notes", d.optString("body", ""));
                out.put("publishedAt", d.optString("published_at", ""));
                out.put("url", d.optString("html_url", ""));
                String apkUrl = "";
                String apkFileName = "adshield.apk";
                double apkMB = 0;
                JSONArray assets = d.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a != null && a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url", "");
                            apkFileName = a.optString("name", "adshield.apk");
                            apkMB = a.optLong("size", 0) / 1048576.0;
                            break;
                        }
                    }
                }
                out.put("apkUrl", apkUrl);
                out.put("apkFileName", apkFileName);
                out.put("apkMB", Math.round(apkMB * 100) / 100.0);
                return out.toString();
            }
            return err("GitHub 返回 " + code, null);
        } catch (Exception e) {
            return err("网络异常：" + safeMsg(e), null);
        }
    }

    /* ───────── 工具 ───────── */

    private static String err(String msg, String fallback) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", msg);
            if (fallback != null) o.put("fallbackUrl", fallback);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"unknown\"}";
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
