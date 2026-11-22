package com.zai.adshield;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * GitHub API 极简客户端（HttpURLConnection + org.json，零第三方依赖）。
 *
 * v8.2.0 双通道更新检查（真机限流根因修复）：
 *  · 【新增·通道 0】更新中转 GET /api/update/latest（UPDATE_RELAY_URL，与日志中转
 *    同源派生）——Worker 边缘缓存 5~10 分钟 + GitHub App 认证上游（独立于匿名
 *    配额池），单次尝试不重试；任何失败自动回退 GitHub 直连，绝不把中转故障
 *    误报为 GitHub 限流（双通道各自归因、复合呈现）。
 *  · 【保留·通道 1-3】GitHub 匿名直连（latest 重试 + /releases 列表回退——
 *    v8.0.3 语义逐字保持，仅错误输出增加五分类 errorCode）。
 *  · 错误五分类：GITHUB_RATE_LIMITED / WORKER_UNAVAILABLE / NETWORK_ERROR /
 *    PARSE_ERROR / NO_NEW_VERSION（isNewer 字段——版本比较 Net.cmpVersion）。
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
    /* ISSUES_PAGE 与 enc() 已于 v8.2.1 移除（newIssuePrefillUrl 死代码清理；
     * maskSensitive 保留——exportLog 二道脱敏在用） */

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

    /* ───────── v8.2.0 通道 0：更新检查中转地址（从 LOG_RELAY_URL 同源派生）─────────
     * 架构：设备 → Worker（边缘缓存 600s + GitHub App 认证上游——独立配额池）→
     * GitHub Releases。Worker 侧代码见仓库 worker/ 目录（部署后生效；部署前
     * 404 → 本客户端自动回退 GitHub 直连，行为=v8.0.3 零回退风险）。
     * 单一事实源：改 LOG_RELAY_URL 自动跟随；空串 = 更新中转结构性关闭。 */
    public static final String UPDATE_RELAY_URL = deriveUpdateRelayUrl(LOG_RELAY_URL);

    /** v8.2.0：日志中转地址 → 同源更新接口地址（纯函数，JVM 可测）。
     *  非预期形态（不以 /api/logs/receive 结尾）返回空串 = 结构性关闭——绝不拼出
     *  错误地址打无关端点。 */
    public static String deriveUpdateRelayUrl(String logRelayUrl) {
        final String suffix = "/api/logs/receive";
        if (logRelayUrl == null || logRelayUrl.length() == 0 || !logRelayUrl.endsWith(suffix)) {
            return "";
        }
        return logRelayUrl.substring(0, logRelayUrl.length() - suffix.length())
                + "/api/update/latest";
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
        return http(method, url, token, body, false);
    }

    /** v7.3.0 noRedirect：GitHub API 通道禁自动重定向——api.github.com 正常
     *  永不 3xx；出现 3xx 只可能是被劫持/代理注入。禁跟随并按错误处理，
     *  响应体只能来自原始连接（防「重定向投毒更新元数据」）。订阅拉取
     *  通道（httpGetRaw → SubMirrors 八通道链）保持自动跟随（镜像站存在
     *  合法重定向）。 */
    private static String[] http(String method, String url, String token, String body, boolean noRedirect)
            throws Exception {
        return http(method, url, token, body, noRedirect, 7000, 12000);
    }

    /** v8.2.0：超时可变版——更新中转通道用更短超时（连接 5s/读 8s：Worker 边缘
     *  缓存命中亚秒级，超时快速切 GitHub 直连，最坏等待链不膨胀）。 */
    private static String[] http(String method, String url, String token, String body, boolean noRedirect,
                                 int connectMs, int readMs) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
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

    /* v7.9.7 问题反馈浏览器预填链接（newIssuePrefillUrl）已于 v8.2.1 移除——
     * 反馈链路 v8.0.0 起统一走中转服务（LOG_RELAY_URL）+ Web 反馈通道，
     * 客户端零 GitHub 凭据红线保持。 */

    /* ───────── 检查更新 → 双通道（v8.2.0：中转优先 → GitHub 匿名直连回退） ─────────
     * 真机根因（v8.0.3 实锤）：更新检查唯一通道是 GitHub 匿名 API（60 次/小时/IP，
     * 国内共享出口极易耗尽）——用户看不到已发布的正式新版本。
     * 通道 0（v8.2.0 新增）：更新中转（与日志中转同源 Worker 的独立只读端点）——
     * 服务端边缘缓存 5~10 分钟 + GitHub App 认证上游（独立配额池），单次尝试
     * 不重试（中转失败 ≠ 设备网络失败——留给直连通道判定），失败自动回退。
     * 通道 1-3（v7.9.7 起 / v8.0.3 限流即时报错）：语义逐字保持。 */

    /** 错误分类码（五分类——UI 与诊断用；纯字符串常量避免 ProGuard 形变） */
    public static final String ERR_GITHUB_RATE_LIMITED = "GITHUB_RATE_LIMITED";
    public static final String ERR_WORKER_UNAVAILABLE = "WORKER_UNAVAILABLE";
    public static final String ERR_NETWORK = "NETWORK_ERROR";
    public static final String ERR_PARSE = "PARSE_ERROR";
    public static final String ERR_GITHUB = "GITHUB_ERROR";
    private static final String RATE_LIMIT_TEXT = "GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试";

    /** v8.2.0 通道结果（静态嵌套类——d8 8.2 仅禁非静态内部类/匿名类/lambda；
     *  public 供 JVM 测试构造——纯数据无不变式）。 */
    public static final class ChannelOutcome {
        public boolean attempted = false; // 通道是否实际发起（false=结构性关闭）
        public String json = null;        // 成功载荷（ok:true JSON）；null = 失败
        public String errCode = null;     // 失败分类码
        public String errText = null;     // 人类可读归因短语（复合错误文案用）
    }

    /**
     * 获取最新 Release（兼容入口——无当前版本则不产 isNewer，由 JS 侧比较）。
     * @see #latestRelease(String)
     */
    public static String latestRelease() {
        return latestRelease("");
    }

    /**
     * 获取最新 Release（v8.2.0 双通道）。
     * 通道 0：更新中转（单次尝试，5s/8s 超时；ok:true 即信任返回——含 hasRelease:false）。
     * 通道 1-3：GitHub 匿名直连（v8.0.3 既有语义：latest 重试×2 → /releases 列表回退）。
     * 失败复合原则：双通道各自归因、绝不误报（中转故障 ≠ GitHub 限流）；
     * 中转失败而直连成功 → 成功载荷附 workerError 供 UI 提示（不构成失败）。
     * @param currentVersion 当前 versionName（如 "8.2.0"）——非空时载荷附 isNewer
     * @return {ok:true, hasRelease, tag, …, apkUrl, apkMirrors[], apkDigest, source, isNewer}
     *         / {ok:false, error, errorCode, workerError?, githubError?}
     */
    public static String latestRelease(String currentVersion) {
        ChannelOutcome w = workerChannel(currentVersion);
        if (w.json != null) return w.json;
        ChannelOutcome g = githubChannel(currentVersion);
        if (g.json != null) {
            if (w.attempted) {
                try {
                    JSONObject o = new JSONObject(g.json);
                    o.put("workerError", ERR_WORKER_UNAVAILABLE); // 中转故障归因（成功路径如实标注）
                    return o.toString();
                } catch (Exception ignored) {
                }
            }
            return g.json;
        }
        return composeFailure(w, g);
    }

    /** 通道 0：更新中转（纯函数化外壳——网络单点，逻辑在 normalizeWorkerUpdatePayload）。 */
    private static ChannelOutcome workerChannel(String currentVersion) {
        ChannelOutcome o = new ChannelOutcome();
        if (UPDATE_RELAY_URL.length() == 0) return o; // 结构性关闭（未部署/未配置）
        o.attempted = true;
        try {
            String[] r = http("GET", UPDATE_RELAY_URL, null, null, true, 5000, 8000);
            int code = Integer.parseInt(r[0]);
            if (code >= 200 && code < 300) {
                String norm = normalizeWorkerUpdatePayload(r[1], currentVersion);
                if (norm != null) {
                    o.json = norm;
                    return o;
                }
                o.errCode = ERR_WORKER_UNAVAILABLE;
                o.errText = "中转响应无效";
                return o;
            }
            o.errCode = ERR_WORKER_UNAVAILABLE;
            o.errText = code == 404
                    ? "中转服务未部署更新接口（worker/DEPLOY.md）"
                    : ("中转服务异常（HTTP " + code + "）");
            return o;
        } catch (Exception e) {
            o.errCode = ERR_WORKER_UNAVAILABLE;
            o.errText = "中转服务连接失败";
            return o;
        }
    }

    /** 通道 1-3：GitHub 匿名直连（v8.0.3 语义保持；错误改带分类码）。 */
    private static ChannelOutcome githubChannel(String currentVersion) {
        ChannelOutcome o = new ChannelOutcome();
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
                    try {
                        o.json = parseRelease(r[1], currentVersion);
                        return o;
                    } catch (Exception pe) {
                        o.errCode = ERR_PARSE;
                        o.errText = "GitHub 响应解析失败";
                        return o;
                    }
                }
                if (code == 404) {
                    break; // 两次 404 → 走列表回退再下结论
                }
                /* v8.0.3 限流即时报错：403（匿名配额 60 次/小时/IP 耗尽）/429
                 * （次级限流）窗口为小时级——800ms 后重试纯浪费同一配额，
                 * 且列表回退同池同限；立即返回明确原因（v8.2.0 改带分类码）。 */
                if (rateLimited(code)) {
                    o.errCode = ERR_GITHUB_RATE_LIMITED;
                    o.errText = RATE_LIMIT_TEXT;
                    return o;
                }
                if (attempt == 1) {
                    sleep(800);
                    continue;
                }
                o.errCode = ERR_GITHUB;
                o.errText = "GitHub 返回 " + code;
                return o;
            } catch (Exception e) {
                if (attempt == 2) {
                    o.errCode = ERR_NETWORK;
                    o.errText = "网络异常（无法连接 GitHub）";
                    return o;
                }
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
                    o.json = parseRelease(d.toString(), currentVersion);
                    return o;
                }
                JSONObject none = new JSONObject();
                none.put("ok", true);
                none.put("hasRelease", false);
                none.put("source", "github");
                o.json = none.toString();
                return o;
            }
            // v8.0.3：限流同因即报（与通道 1/2 同口径——同配额池重试无意义）
            if (rateLimited(code)) {
                o.errCode = ERR_GITHUB_RATE_LIMITED;
                o.errText = RATE_LIMIT_TEXT;
                return o;
            }
            o.errCode = ERR_GITHUB;
            o.errText = "GitHub 返回 " + code;
            return o;
        } catch (org.json.JSONException je) {
            o.errCode = ERR_PARSE;
            o.errText = "GitHub 响应解析失败";
            return o;
        } catch (Exception e) {
            o.errCode = ERR_NETWORK;
            o.errText = "网络异常（无法连接 GitHub）";
            return o;
        }
    }

    /** 双通道皆败的复合失败载荷（纯函数——JVM 真值表钉死）。 */
    public static String composeFailure(ChannelOutcome w, ChannelOutcome g) {
        String code = primaryErrorCode(w.attempted, g.errCode);
        String msg = compositeMessage(w.attempted, w.errText, g.errText);
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", msg);
            o.put("errorCode", code);
            if (w.attempted && w.errCode != null) o.put("workerError", w.errCode);
            if (g.errCode != null) o.put("githubError", g.errCode);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"" + msg + "\"}";
        }
    }

    /** 主错误码（纯函数）：github 通道结果优先（可行动性最强——限流=1h 后重试），
     *  github 未失败（理论不可达）时按 worker 归因兜底。WORKER_UNAVAILABLE 只在
     *  「中转失败而直连成功」的成功载荷里出现（workerError 字段）——不构成最终
     *  失败码，绝不把中转故障误报为 GitHub 限流。 */
    public static String primaryErrorCode(boolean workerAttempted, String githubErrCode) {
        if (githubErrCode != null) {
            if (githubErrCode.equals(ERR_GITHUB_RATE_LIMITED)) return ERR_GITHUB_RATE_LIMITED;
            if (githubErrCode.equals(ERR_PARSE)) return ERR_PARSE;
            if (githubErrCode.equals(ERR_GITHUB)) return ERR_GITHUB;
            return ERR_NETWORK;
        }
        return workerAttempted ? ERR_WORKER_UNAVAILABLE : ERR_NETWORK;
    }

    /** 复合错误文案（纯函数）：双通道归因各自呈现；仅直连失败时文案与 v8.0.3 一致。 */
    public static String compositeMessage(boolean workerAttempted, String workerText, String githubText) {
        String g = githubText == null ? "网络异常" : githubText;
        if (workerAttempted && workerText != null && workerText.length() > 0) {
            return "更新通道均失败：" + workerText + "；" + g;
        }
        return g;
    }

    /**
     * v8.2.0：中转更新载荷规范化（纯函数，JVM 可测）。字段与 parseRelease 输出
     * 完全对齐（app.js 下载链零改动），附加 source:"worker"/cached/fetchedAt。
     * @return 规范化 JSON；null = 载荷不可用（ok:false/契约破坏/垃圾）→ 调用方回退。
     */
    public static String normalizeWorkerUpdatePayload(String body, String currentVersion) {
        try {
            JSONObject d = new JSONObject(body);
            if (!d.optBoolean("ok", false)) return null;
            if (!d.has("hasRelease")) return null; // 契约破坏 → 回退直连
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("source", "worker");
            if (d.has("cached")) out.put("cached", d.optBoolean("cached", false));
            if (d.has("fetchedAt")) out.put("fetchedAt", d.optLong("fetchedAt", 0));
            if (!d.optBoolean("hasRelease", false)) {
                out.put("hasRelease", false);
                return out.toString();
            }
            String tag = d.optString("tag", "");
            String apkUrl = d.optString("apkUrl", "");
            long apkSize = d.optLong("apkSize", 0);
            out.put("hasRelease", true);
            out.put("tag", tag);
            out.put("name", d.optString("name", ""));
            out.put("notes", d.optString("notes", ""));
            out.put("publishedAt", d.optString("publishedAt", ""));
            out.put("url", d.optString("url", ""));
            out.put("apkUrl", apkUrl);
            out.put("apkFileName", d.optString("apkFileName", "adshield.apk"));
            out.put("apkMB", Math.round(apkSize / 1048576.0 * 100) / 100.0); // 桩 org.json 无 optDouble——与 parseRelease 同公式本地计算（Worker apkMB 同源可忽略）
            out.put("apkSize", apkSize);
            out.put("apkDigest", d.optString("apkDigest", ""));
            out.put("headlines", headlinesOf(d.optString("notes", "")));
            out.put("apkMirrors", mirrorsOf(apkUrl));
            out.put("isNewer", currentVersion != null && currentVersion.length() > 0
                    && cmpVersion(tag, currentVersion) > 0);
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** v8.2.0 版本比较（纯函数，JVM 可测）——app.js cmpVer 的 Java 镜像：
     *  三段数值比较（非字典序："8.10.0" > "8.9.0"）、v 前缀容忍、缺段补 0。 */
    public static int cmpVersion(String a, String b) {
        int[] pa = versionSegs(a);
        int[] pb = versionSegs(b);
        for (int i = 0; i < 3; i++) {
            int d = pa[i] - pb[i];
            if (d != 0) return d;
        }
        return 0;
    }

    private static int[] versionSegs(String v) {
        int[] out = {0, 0, 0};
        if (v == null) return out;
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        String[] parts = s.split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (Exception ignored) {
            }
        }
        return out;
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
     *  的 TOCTOU 全部由这道哈希闸拦下）。
     *  v8.2.0：source:"github" + currentVersion 非空时附 isNewer（NO_NEW_VERSION
     *  分类的权威判定——Net.cmpVersion 纯函数，JVM 钉死）。 */
    private static String parseRelease(String body, String currentVersion) throws Exception {
        JSONObject d = new JSONObject(body);
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("hasRelease", true);
        out.put("source", "github");
        out.put("tag", d.optString("tag_name", ""));
        out.put("name", d.optString("name", ""));
        out.put("notes", d.optString("body", ""));
        out.put("publishedAt", d.optString("published_at", ""));
        out.put("url", d.optString("html_url", ""));
        out.put("headlines", headlinesOf(d.optString("body", "")));
        if (currentVersion != null && currentVersion.length() > 0) {
            out.put("isNewer", cmpVersion(d.optString("tag_name", ""), currentVersion) > 0);
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
        out.put("apkMirrors", mirrorsOf(apkUrl));
        return out.toString();
    }

    /** v8.2.0：Release 正文 → headlines[]（v7.0.0 语义原样提取——双通道共用）。 */
    private static JSONArray headlinesOf(String notes) {
        JSONArray hl = new JSONArray();
        try {
            String[] heads = com.zai.adshield.core.ReleaseNotes.headlines(notes);
            for (String h : heads) hl.put(h);
        } catch (Throwable ignored) {
        }
        return hl;
    }

    /** v8.2.0：apkUrl → 镜像通道列表（v3.3.0 语义原样提取——双通道共用：
     *  Worker 契约不含镜像——客户端统一合成，下载链零改动）。 */
    public static JSONArray mirrorsOf(String apkUrl) {
        JSONArray mirrors = new JSONArray();
        if (apkUrl != null && apkUrl.length() > 0) {
            mirrors.put("https://ghfast.top/" + apkUrl);
            mirrors.put("https://gh-proxy.com/" + apkUrl);
        }
        return mirrors;
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

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
