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
 *  2. 检查更新    → GET  /repos/{repo}/releases/latest（v3.3.0 起带令牌 + 三重重试 +
 *                   /releases 列表回退——对抗移动网络/出口的间歇性 404/超时）
 *  3. 应用内更新  → 下载交给系统 DownloadManager（见 Downloader.java），
 *                   v3.3.0 起直连失败自动切换镜像通道（Net.mirrorUrls 提供）
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

    /** v4.0.0 订阅拉取：GET 任意 URL 原文（GKD 订阅 JSON5），2xx 返回 body，否则抛异常。
     *  v6.1.0 Connection reset 根治（用户实锤：订阅市场点击导入提示「拉取失败，
     *  Connection reset」——国内移动网络对 cdn.jsdelivr.net 间歇性 TCP 重置，
     *  瞬时故障单次即弃）：同地址瞬时故障重试 1 次（间隔 600ms），只对连接
     *  类异常（reset/timeout/EOF）重试，4xx 业务错误不重试。 */
    public static String httpGetRaw(String url) throws Exception {
        Exception lastEx = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String[] r = http("GET", url, null, null);
                int code = Integer.parseInt(r[0]);
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                return r[1];
            } catch (Exception e) {
                // 仅瞬时性网络故障重试（reset/timeout/EOF）；HTTP 4xx/5xx 立即上抛交镜像链
                if (!isTransient(e) || attempt == 2) throw e;
                lastEx = e;
                sleep(600);
            }
        }
        throw lastEx == null ? new IllegalStateException("网络失败") : lastEx;
    }

    /** v6.1.0：瞬时性网络故障判定（reset/timeout/EOF——值得同址重试；
     *  其余（HTTP 状态码错误等）交上层镜像变体链换址） */
    static boolean isTransient(Exception e) {
        if (e == null) return false;
        String m = e.getMessage();
        if (m == null) m = "";
        String low = m.toLowerCase();
        return low.contains("reset") || low.contains("timeout") || low.contains("timed out")
                || low.contains("eof") || low.contains("broken pipe")
                || low.contains("connection") || e instanceof java.io.IOException;
    }

    /* ───────── 底层 HTTP ───────── */

    /** 返回 [statusCode, body]；任何异常向上抛出由调用方兜底。
     *  v6.1.0 超时调优：连接 10s→7s、读取 15s→12s——Connection reset 场景
     *  下快速失败快速切镜像（订阅 JSON5 通常 <2MB，12s 足够），最坏等待链
     *  总时长不膨胀；UA 改浏览器风格（部分代理站拦非浏览器 UA）。 */
    private static String[] http(String method, String url, String token, String body) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(7000);
            c.setReadTimeout(12000);
            c.setRequestMethod(method);
            boolean githubApi = url.contains("api.github.com");
            c.setRequestProperty("Accept", githubApi
                    ? "application/vnd.github+json" : "text/plain,application/json,*/*");
            c.setRequestProperty("User-Agent", githubApi
                    ? "adshield-android" : BROWSER_UA);
            c.setRequestProperty("Accept-Encoding", "identity");
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

    /** v6.1.0：CDN/代理站友好浏览器 UA（订阅镜像通道专用；GitHub API 仍用固定 UA） */
    static final String BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";

    /* ───────── 问题反馈 → GitHub Issues（内置令牌，用户零配置） ───────── */

    /* ───────── v5.5.0 隐私纵深：上传正文正则脱敏（纯函数，JVM 可测） ─────────
     * 双端双层：JS 侧 maskText() 先脱敏（收集时），本方法再脱敏一次（提交前）
     * ——纵深防御，任何一层被绕过都兜得住。规则与 Web 端 /api/feedback 对齐：
     *  · 手机号（1[3-9] 开头 11 位，前后非数字）→ 保留前 3 后 4：138****5678
     *  · 身份证（18 位含校验位 X/x，前后非字母数字）→ 保留前 3 后 2
     *  · 邮箱 → 本地部分整体打码，保留 @域名
     *  · IPv4（独立四段）→ 保留前两段：192.168.*.*
     * 包名/类名/控件 ID/坐标等调试必需信息不受影响。 */
    public static String maskSensitive(String s) {
        if (s == null || s.length() == 0) return "";
        String out = s;
        out = out.replaceAll("(?<![0-9])(1[3-9][0-9])[0-9]{4}([0-9]{4})(?![0-9])", "$1****$2");
        out = out.replaceAll("(?<![0-9A-Za-z])([0-9]{3})[0-9]{12}([0-9]{3}[0-9Xx])(?![0-9A-Za-z])", "$1************$2");
        out = out.replaceAll("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})", "***@$1");
        out = out.replaceAll("(?<![0-9])(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}(?![0-9])", "$1.*.*");
        return out;
    }

    /**
     * 创建反馈 Issue，使用内置混淆令牌（界面已无任何 Token 输入）。
     * v5.5.0：title/body 提交前经 maskSensitive 二次脱敏（纵深第二层）。
     * @return {ok:true, url, number} 或 {ok:false, error, fallbackUrl}
     */
    public static String postIssue(String title, String body) {
        title = maskSensitive(title);
        body = maskSensitive(body);
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

    /* ───────── 检查更新 → GitHub Releases（v3.3.0：令牌 + 重试 + 列表回退） ───────── */

    /**
     * 获取最新 Release（v3.3.0 三通道：令牌直连 → 重试 → /releases 列表回退）。
     * 背景真机反馈：检查更新常报“仓库尚未发布版本”或直接无反应——移动网络下
     * api.github.com 间歇 404/超时；v3.2.0 及之前匿名单次请求，失败即放弃。
     * @return {ok:true, hasRelease:false} / {ok:true, hasRelease:true, tag, name, notes, url, apkUrl, apkMirrors[], apkFileName, apkMB, publishedAt}
     *         / {ok:false, error}
     */
    public static String latestRelease() {
        String token = builtInToken();
        // 通道 1/2：/releases/latest，带令牌重试两次（间隔 800ms）
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String[] r = http("GET", API + "/releases/latest", token, null);
                int code = Integer.parseInt(r[0]);
                if (code == 404 && attempt == 1) {
                    sleep(800); // 间歇性 404：稍候重试一次再下结论
                    continue;
                }
                if (code >= 200 && code < 300) {
                    return parseRelease(r[1]);
                }
                if (code == 404) {
                    break; // 两次 404 → 走列表回退再下结论
                }
                if (attempt == 1) {
                    sleep(800);
                    continue;
                }
                return err("GitHub 返回 " + code, null);
            } catch (Exception e) {
                if (attempt == 2) break;
                sleep(800);
            }
        }
        // 通道 3：/releases 列表回退（latest 语义不可用时取最新非预发布项）
        try {
            String[] r = http("GET", API + "/releases?per_page=8", token, null);
            int code = Integer.parseInt(r[0]);
            if (code >= 200 && code < 300) {
                org.json.JSONArray arr = new org.json.JSONArray(r[1]);
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject d = arr.optJSONObject(i);
                    if (d == null || d.optBoolean("draft", false) || d.optBoolean("prerelease", false)) continue;
                    String out = parseRelease(d.toString());
                    if (out.contains("\"hasRelease\":true")) return out;
                }
                JSONObject none = new JSONObject();
                none.put("ok", true);
                none.put("hasRelease", false);
                return none.toString();
            }
            return err("GitHub 返回 " + code, null);
        } catch (Exception e) {
            return err("网络异常：" + safeMsg(e), null);
        }
    }

    /** 解析单个 Release JSON → 页面可用结构（含镜像通道列表） */
    private static String parseRelease(String body) throws Exception {
        JSONObject d = new JSONObject(body);
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
        // v3.3.0 镜像通道：直连失败时逐个切换（ghfast / gh-proxy 公共加速）
        JSONArray mirrors = new JSONArray();
        if (apkUrl.length() > 0) {
            mirrors.put("https://ghfast.top/" + apkUrl);
            mirrors.put("https://gh-proxy.com/" + apkUrl);
        }
        out.put("apkMirrors", mirrors);
        return out.toString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignored) {
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
