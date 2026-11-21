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
 * v7.9.7 安全整改（PAT 移除轮）：
 *  · 【移除】内置混淆令牌（builtInToken，双层 XOR+Base64）——混淆不等于加密，
 *    公开分发的 APK 内的任何令牌形态（明文/混淆/拆分/加密硬编码）都必须视为
 *    已泄露凭据。历史令牌的撤销由仓库所有者在 GitHub Settings 执行
 *    （docs/SECURITY.md R1，撤销前必须视为有效暴露凭据）。
 *  · 【移除】postIssue（问题反馈/日志上传直发 GitHub Issue）——客户端不再
 *    持有任何 GitHub 写凭据：手动日志改为本地导出（MainActivity.exportLog），
 *    问题反馈改走浏览器预填链接（零令牌）。
 *  · 【保留·匿名】检查更新 → GET /repos/{repo}/releases/latest（v3.3.0 重试 +
 *    /releases 列表回退全保留；仅去掉 Authorization 头——匿名限频 60 次/小时/出口，
 *    手动「检查更新」频次远低于此；间歇 404/超时由既有重试链路吸收）。
 *  · 【预留】LOG_RELAY_URL——自动日志上传的中转服务地址（v8.0.0 上线后填入；
 *    空串 = 自动上传结构性关闭——授权开启也绝不直连 GitHub）。
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

    /* ───────── v7.9.7 预留 / v8.0.0 填入：自动日志上传中转服务地址 ─────────
     * 架构（v8.0.0 已落地）：Android → HTTPS 中转服务（鉴权/24h 滑窗限流/
     * batchId 幂等/二次隐私过滤）→ GitHub 私有仓库 Skip-Ad-Logs 的 Issues。
     * 生产地址（skip-ad-log-relay v1.2.1 · Cloudflare Worker，已部署并经
     * 用户真实浏览器 /healthz 验证：ipHmacConfigured=true · fail-closed）。
     * 服务端凭据（GitHub App 私钥/IP_HMAC 密钥）全部只存 Cloudflare Worker
     * Secret——APK 零凭据红线保持（v7.9.7 达成，本轮不破）；此地址为公开
     * 服务端点（非 Secret）。空串 = 自动上传结构性关闭。 */
    public static final String LOG_RELAY_URL =
            "https://skip-ad-log-relay.2927609408.workers.dev/api/logs/receive";

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
        return http(method, url, token, body, false);
    }

    /** v7.3.0 noRedirect：GitHub API 通道禁自动重定向——api.github.com 正常
     *  永不 3xx；出现 3xx 只可能是被劫持/代理注入。禁跟随并按错误处理，
     *  响应体只能来自原始连接（防「重定向投毒更新元数据」）。订阅拉取
     *  通道（httpGetRaw → SubMirrors 八通道链）保持自动跟随（镜像站存在
     *  合法重定向）。 */
    private static String[] http(String method, String url, String token, String body, boolean noRedirect)
            throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(7000);
            c.setReadTimeout(12000);
            c.setRequestMethod(method);
            if (noRedirect) c.setInstanceFollowRedirects(false);
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

    /** v7.3.0 安全：响应体硬上限 4MB（防超大/无限 body 内存 DoS——订阅拉取与
     *  API 响应全覆盖；GitHub Release JSON ≤ 64KB、订阅 JSON5 ≤ 2MB 量级） */
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            if (bo.size() > MAX_BODY_BYTES) {
                try { in.close(); } catch (Exception ignored) { }
                throw new IllegalStateException("响应体超过 4MB 上限（已中断连接）");
            }
        }
        try { in.close(); } catch (Exception ignored) { }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    /** v6.1.0：CDN/代理站友好浏览器 UA（订阅镜像通道专用；GitHub API 仍用固定 UA） */
    static final String BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36";

    /* ───────── v5.5.0 隐私纵深：本地导出/曾上传正文正则脱敏（纯函数，JVM 可测） ─────────
     * 双端双层：JS 侧 maskText() 先脱敏（收集时），本方法再脱敏一次（提交前）
     * ——纵深防御，任何一层被绕过都兜得住。规则与 Web 端 /api/feedback 对齐：
     *  · 手机号（1[3-9] 开头 11 位，前后非数字）→ 保留前 3 后 4：138****5678
     *  · 身份证（18 位含校验位 X/x，前后非字母数字）→ 保留前 3 后 2
     *    v8.0.0 差一修复：旧正则 ([0-9]{3})[0-9]{12}([0-9]{3}[0-9Xx]) 要求 19 位
     *    ——中国居民身份证实为 18 位，同族实现从未真正脱敏；修正为
     *    ([0-9]{3})[0-9]{12}([0-9]{2}[0-9Xx]) = 18 位全匹配（与 LogPrivacyFilter
     *    /www maskText /服务端 privacy.ts 四端同族同修）。
     *  · 邮箱 → 本地部分整体打码，保留 @域名
     *  · IPv4（独立四段）→ 保留前两段：192.168.*.*
     * 包名/类名/控件 ID/坐标等调试必需信息不受影响。 */
    public static String maskSensitive(String s) {
        if (s == null || s.length() == 0) return "";
        String out = s;
        out = out.replaceAll("(?<![0-9])(1[3-9][0-9])[0-9]{4}([0-9]{4})(?![0-9])", "$1****$2");
        out = out.replaceAll("(?<![0-9A-Za-z])([0-9]{3})[0-9]{12}([0-9]{2}[0-9Xx])(?![0-9A-Za-z])", "$1************$2");
        out = out.replaceAll("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})", "***@$1");
        out = out.replaceAll("(?<![0-9])(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}(?![0-9])", "$1.*.*");
        return out;
    }

    /** v7.9.7：问题反馈/手动日志的零令牌浏览器预填链接（本机拼好 title/body 查询串，
     * 交给系统浏览器打开 GitHub 新建 Issue 预填页——用户在自己登录态里提交，
     * 客户端零凭据零写操作）。 */
    public static String newIssuePrefillUrl(String title, String body) {
        return ISSUES_PAGE + "/new?title=" + enc(maskSensitive(title)) + "&body=" + enc(maskSensitive(body));
    }

    /* ───────── 检查更新 → GitHub Releases（v7.9.7：匿名 + 重试 + 列表回退） ───────── */

    /**
     * 获取最新 Release（三通道：直连 → 重试 → /releases 列表回退；v7.9.7 起匿名）。
     * 背景真机反馈：检查更新常报“仓库尚未发布版本”或直接无反应——移动网络下
     * api.github.com 间歇 404/超时；v3.2.0 及之前匿名单次请求，失败即放弃。
     * @return {ok:true, hasRelease:false} / {ok:true, hasRelease:true, tag, name, notes, url, apkUrl, apkMirrors[], apkFileName, apkMB, publishedAt}
     *         / {ok:false, error}
     */
    public static String latestRelease() {
        String token = ""; // v7.9.7：匿名——http() 对空 token 不发送 Authorization 头（限频 60 次/小时/出口，手动检查更新频次远低）
        // 通道 1/2：/releases/latest，重试两次（间隔 800ms）
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String[] r = http("GET", API + "/releases/latest", token, null, true);
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
                /* v8.0.3 限流即时报错：403（匿名配额 60 次/小时/IP 耗尽）/429
                 * （次级限流）窗口为小时级——800ms 后重试纯浪费同一配额，
                 * 且列表回退同池同限；旧版两趟重试后只报「GitHub 返回 403」
                 * ——用户无从知道是限流还是何时能重试。立即返回明确原因。 */
                if (rateLimited(code)) {
                    return err("GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试", null);
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
            String[] r = http("GET", API + "/releases?per_page=8", token, null, true);
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
            // v8.0.3：限流同因即报（与通道 1/2 同口径——同配额池重试无意义）
            if (rateLimited(code)) {
                return err("GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试", null);
            }
            return err("GitHub 返回 " + code, null);
        } catch (Exception e) {
            return err("网络异常：" + safeMsg(e), null);
        }
    }

    /** v8.0.3：限流类状态码判定（403 = 匿名配额耗尽 / 429 = 次级限流）。
     *  更新检查两通道共用；纯函数 JVM 可测（ReleaseNotesTest 更新链路段）。
     *  404 不算限流（仓库暂无正式 Release 的正常态——继续重试/列表回退）；
     *  3xx 在 noRedirect 语义下按错误处理但非限流（劫持/代理注入另一类）。 */
    public static boolean rateLimited(int code) {
        return code == 403 || code == 429;
    }

    /** 解析单个 Release JSON → 页面可用结构（含镜像通道列表）。
     *  v7.0.0：headlines[] 新版功能标题预解析（core.ReleaseNotes 纯函数——
     *  检查更新发现新版本时页内直接渲染 ≤5 条带序号功能描述，JS 零解析）。
     *  v7.3.0 安全：提取 asset 的 digest（GitHub API 官方 SHA-256，
     *  "sha256:hex" 格式）与 size（字节）——经 HTTPS api.github.com 获取，
     *  交由 Downloader 安装前校验 APK 完整性（镜像代理被投毒/共享盘换包
     *  的 TOCTOU 全部由这道哈希闸拦下）。 */
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
        try {
            String[] heads = com.zai.adshield.core.ReleaseNotes.headlines(d.optString("body", ""));
            JSONArray hl = new JSONArray();
            for (String h : heads) hl.put(h);
            out.put("headlines", hl);
        } catch (Throwable ignored) {
        }
        String apkUrl = "";
        String apkFileName = "adshield.apk";
        double apkMB = 0;
        long apkSize = 0;
        String apkDigest = "";
        JSONArray assets = d.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a != null && a.optString("name", "").endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url", "");
                    apkFileName = a.optString("name", "adshield.apk");
                    apkSize = a.optLong("size", 0);
                    apkMB = apkSize / 1048576.0;
                    apkDigest = a.optString("digest", "");
                    break;
                }
            }
        }
        out.put("apkUrl", apkUrl);
        out.put("apkFileName", apkFileName);
        out.put("apkMB", Math.round(apkMB * 100) / 100.0);
        out.put("apkSize", apkSize);
        out.put("apkDigest", apkDigest);
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
