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
 * v1.3.0 联网能力：仅两处出口——
 *  1. 问题反馈 → POST /repos/{repo}/issues（同步记录用户使用中遇到的问题）
 *  2. 检查更新 → GET  /repos/{repo}/releases/latest（公共仓库匿名可读）
 *
 * 约束：为规避 d8 8.2 的非静态内部类 bug，本类不使用任何匿名类/内部类/lambda。
 * 所有方法为同步阻塞调用，仅允许在 JSBridge 线程等后台线程执行。
 */
public final class Net {

    public static final String REPO = "xiaobaiba999/Skip-Ad";
    public static final String API = "https://api.github.com/repos/" + REPO;
    public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";
    public static final String ISSUES_PAGE = "https://github.com/" + REPO + "/issues";

    private Net() {
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

    /* ───────── 问题反馈 → GitHub Issues ───────── */

    /**
     * 创建反馈 Issue。
     * @param token 用户在高级设置中填写的 GitHub Token（可为空）
     * @return {ok:true, url, number} 或 {ok:false, error, fallbackUrl}
     */
    public static String postIssue(String token, String title, String body) {
        String fallback = ISSUES_PAGE + "/new?title=" + enc(title) + "&body=" + enc(body);
        if (token == null || token.trim().length() < 10) {
            return err("未配置 GitHub Token：已生成浏览器预填链接，可手动提交", fallback);
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("title", title);
            payload.put("body", body);
            String[] r = http("POST", API + "/issues", token.trim(), payload.toString());
            int code = Integer.parseInt(r[0]);
            if (code >= 200 && code < 300) {
                JSONObject d = new JSONObject(r[1]);
                JSONObject out = new JSONObject();
                out.put("ok", true);
                out.put("url", d.optString("html_url", ""));
                out.put("number", d.optInt("number", 0));
                return out.toString();
            }
            if (code == 401) return err("Token 无效或已过期，请检查高级设置", fallback);
            if (code == 403) return err("Token 权限不足（需要 issues 写权限）或触发限流", fallback);
            return err("GitHub 返回 " + code, fallback);
        } catch (Exception e) {
            return err("网络异常：" + safeMsg(e), fallback);
        }
    }

    /* ───────── 检查更新 → GitHub Releases ───────── */

    /**
     * 获取最新 Release（公共仓库无需令牌）。
     * @return {ok:true, hasRelease:false} / {ok:true, hasRelease:true, tag, name, notes, url, apkUrl, apkMB, publishedAt}
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
                double apkMB = 0;
                JSONArray assets = d.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a != null && a.optString("name", "").endsWith(".apk")) {
                            apkUrl = a.optString("browser_download_url", "");
                            apkMB = a.optLong("size", 0) / 1048576.0;
                            break;
                        }
                    }
                }
                out.put("apkUrl", apkUrl);
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
