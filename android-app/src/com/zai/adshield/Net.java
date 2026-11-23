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
 *   ↑ v7.9.8 历史称谓保留——本类已不再访问 api.github.com（更新检查改静态清单）
 *
 * v7.9.8 更新检查架构改造（用户指令：客户端不再直接依赖 GitHub REST API）：
 *  · 【移除】检查更新 GitHub 匿名直连通道（latestRelease/parseRelease/mirrorsOf/
 *    rateLimited + /releases/latest 重试×2 + /releases 列表回退）——匿名
 *    60 次/小时/IP 出口配额，国内共享出口下所有用户同池互耗，进入更新页即
 *    「GitHub API 限流」。更新元数据改读静态清单（UpdateRepository →
 *    raw.githubusercontent.com main 分支 Fastly CDN，零 REST API 配额）；清单
 *    契约/校验见 UpdateManifest；版本判定纯函数原地保留（下方）。
 *  · 日志上传独立链路（core.LogUploader——含 Issue 核实匿名 GET）不受影响。
 *
 * v7.9.7 安全整改（PAT 移除轮）：
 *  · 【移除】内置混淆令牌（builtInToken，双层 XOR+Base64）——混淆不等于加密，
 *    公开分发的 APK 内的任何令牌形态（明文/混淆/拆分/加密硬编码）都必须视为
 *    已泄露凭据。历史令牌的撤销由仓库所有者在 GitHub Settings 执行
 *    （docs/SECURITY.md R1，撤销前必须视为有效暴露凭据）。
 *  · 【移除】postIssue（问题反馈/日志上传直发 GitHub Issue）——客户端不再
 *    持有任何 GitHub 写凭据：手动日志改为本地导出（MainActivity.exportLog），
 *    问题反馈改走浏览器预填链接（零令牌）。
 *
 * 约束：为规避 d8 8.2 的非静态内部类 bug，本类不使用任何匿名类/内部类/lambda。
 *       所有方法为同步阻塞调用，仅允许在 JSBridge 线程等后台线程执行。
 */
public final class Net {

    public static final String REPO = "xiaobaiba999/Skip-Ad";
    /* v7.9.8：API（api.github.com 基址）与 RELEASES_PAGE 已随 GitHub 直连更新
     * 通道整体移除——更新检查零 GitHub REST API（UpdateRepository 静态清单
     * 架构）；REPO 保留（UpdateManifest 构造 Release 页链接用）。
     * ISSUES_PAGE 与 enc() 已于 v8.2.1 移除（newIssuePrefillUrl 死代码清理；
     * maskSensitive 保留——exportLog 二道脱敏在用） */

    private Net() {
    }

    /* ───────── 自动日志上传中转服务地址（唯一已知服务端端点）─────────
     * 架构：Android → HTTPS 中转服务（鉴权/24h 滑窗限流/batchId 幂等/
     * 二次隐私过滤）→ GitHub Issues。服务端凭据全部只存服务端——APK
     * 零凭据红线保持。
     * 【v7.9.6plus 诚实口径】端点部署状态在开发沙箱不可验证（DNS 解析
     * 正常但 TCP 出站被拦截，无法确认服务存在；Worker 源码仓在 owner
     * 账号不存在）。客户端按契约原样保留此地址（历史会话记录其曾部署
     * 并经用户真实浏览器 /healthz 验证）；上传失败时如实报告「上传服务
     * 不可达，日志已保留」——绝不伪造成功。成功后经 GitHub 匿名 API 核实
     * 公开仓 issues/{n}——核实到才展示可点击链接（LogUploader.verifyIssueUrl）。
     * 此地址为公开服务端点（非 Secret）。空串 = 上传结构性关闭。 */
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
        return http(method, url, token, body, noRedirect, 7000, 12000);
    }

    /** 超时可变版（连接/读取超时由调用方指定——默认 7s/12s）。 */
    private static String[] http(String method, String url, String token, String body, boolean noRedirect,
                                 int connectMs, int readMs) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setRequestMethod(method);
            if (noRedirect) c.setInstanceFollowRedirects(false);
            /* v7.9.8：api.github.com 专用 UA/Accept 分支已随 GitHub 直连更新
             * 通道移除（本类不再访问 api.github.com）；统一 CDN/代理站友好
             * 浏览器 UA（订阅镜像通道语义；更新清单通道见 UpdateRepository）。 */
            c.setRequestProperty("Accept", "text/plain,application/json,*/*");
            c.setRequestProperty("User-Agent", BROWSER_UA);
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

    /* ───────── 检查更新 GitHub 匿名直连通道（v7.9.6plus～v7.9.7）─────────
     * 【v7.9.8 整体移除】latestRelease（/releases/latest 重试×2 + /releases
     * 列表回退）/parseRelease/mirrorsOf/rateLimited/错误四分类常量——匿名
     * 60 次/小时/IP 出口配额下所有用户同池互耗（进入更新页即「GitHub API
     * 限流」的用户实锤根因）。替代：
     *   · 更新元数据 → UpdateRepository（静态清单 raw.githubusercontent.com，
     *     Fastly CDN，零 REST API 配额）+ UpdateManifest（清单契约/校验）；
     *   · 版本判定纯函数 → isNewerRelease（v7.10.0 统一规则：仅
     *     remoteVersionCode > currentVersionCode；versionCodeOfNotes/
     *     cmpVersion/segNum 旧回退链已随 v7.10.0 统一规则专项删除）；
     *   · 限流判定 → UpdateManifest.isRateLimitCode（403/429）。
     * 真机根因史（v8.0.3 实锤）：匿名 60 次/小时/IP，国内共享出口易耗尽。 */

    /* ═══ v7.10.0 统一版本规则（纯函数，JVM 可测）═══ */

    /**
     * 新版本判定（v7.10.0 统一规则——全项目唯一权威语义）：
     * remoteVersionCode > currentVersionCode 才算新版；任一侧缺失/非法
     * （≤0）→ 不提示（绝不因元数据缺失回退 versionName 字符串比较——
     * 旧回退链对 "7.9.6plus" 等非三段命名的误判史见 docs/CHANGELOG.md
     * v7.9.6plus/v7.9.8 节）。versionName 自 v7.10.0 起仅作展示，
     * 绝不参与更新判断（JS 侧 isNewerByCode / 清单侧 UpdateManifest.parse
     * / 生成器侧 update_manifest.py 三面同语义）。
     */
    public static boolean isNewerRelease(int remoteVersionCode, int currentVersionCode) {
        return remoteVersionCode > 0 && currentVersionCode > 0 && remoteVersionCode > currentVersionCode;
    }

    /* ═══ v7.10.0：已删除的 versionName 回退链（统一规则专项）═══
     * versionCodeOfNotes（Release 正文 versionCode 元数据行解析）/
     * cmpVersion（versionName 三段数值比较）/ versionSegs / segNum
     * （plus 后缀段解析——"6plus"→6 的 82300/plus 旧兼容核心）四函数
     * 连同全部调用方一并移除：清单 versionCode 缺失即不判新，绝不从
     * Release 正文回退定码、绝不回退 versionName 名比较。死灰复燃防线
     * 见 UpdateChannelTest S2 反射断言（四符号均不得回场）。 */

    /* 【v7.9.8 已移除】rateLimited（403/429 限流判定）→ 迁移
     * UpdateManifest.isRateLimitCode（语义不变——宿主随静态清单架构换新）。
     * parseRelease（GitHub Release JSON 解析）→ 迁移 UpdateManifest.parse
     * （静态清单契约）；headlinesOf 随之迁移；mirrorsOf（镜像列表）随
     * GitHub 直连通道移除（清单侧固定空数组——唯一官方源语义保持）；
     * err/safeMsg 死代码（唯一调用方已删）一并清理。 */

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignored) {
        }
    }
}
