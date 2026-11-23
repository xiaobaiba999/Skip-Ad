package com.zai.adshield.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v8.2.2 简化日志上传器（替换 v8.0.0~v8.2.1 的 LogUploadDispatcher 守护线程
 * + LogUploadPolicy 裁决表/退避表——真机实锤复杂链路是「一键上传无日志」
 * 根因链的一环，本版回归 v7.8 的直白模型：点按钮就传、心跳就查、失败保留）。
 * ─────────────────────────────────────────────
 * 六项业务（用户 v8.2.2 指令——除此之外零附加机制；v7.10.0 次数限制
 * 彻底移除修订：①③ 的人工额度闸全部删除）：
 *  ① 自动上传无次数上限（v7.10.0：仅受心跳间隔 8h 与「有新日志才传」
 *     约束——24h/3 成功额度闸与批粒度复核闸已删；ok-times 账本仅作
 *     历史留存，不再被任何闸门读取）；
 *  ② 自动上传必须有新日志（pending==0 直接跳过，零网络请求）；
 *  ③ 手动上传随时可用（v7.10.0：零次数/额度/冷却门槛——短时连传多批
 *     全部放行；服务端 per-minute 瞬时防爆层 RATE_LIMITED 如实转达）；
 *  ④ 成功 = HTTP 2xx 且 body ok=true（服务端明确确认才消费事件）；
 *  ⑤ 失败保留全部未确认事件（同 batchId 重试——服务端幂等防重复 Issue）；
 *  ⑥ 自动上传开关只管自动（采集常开、手动不受影响）。
 *
 * 删除的 v8.x 机制（不再存在）：六状态机批次文件、20 批容量环、退避表
 * 30s→480s×5、6 次耗尽终态、失败批次复活启发式、8 轮排空循环、守护线程。
 * 失败后的重试节奏 = 下一次心跳（自动）或用户再点一次（手动）——没有更
 * 多的状态要维护。
 *
 * ═══ v7.9.8 专项修复（真机实锤「第一次失败、第二次被服务端拒」）═══
 *  · 冻结批次：runUpload 封批时把 payload 整体冻结进 manifest（LogStore
 *    sealBatch），重试字节级复用——同 batchId 必同 payload，服务端幂等
 *    必然 ALREADY_ACCEPTED 复用原编号，绝不二建 Issue；
 *  · 读超时 15s→45s：Worker→GitHub 建 Issue 可超 15s，旧值把「服务端
 *    实际成功但响应未及」变成客户端「失败」——正是两连失败的导火索；
 *  · 错误分类（classifyError 纯函数·JVM 可回归）：网络层区分连接失败/
 *    超时/IO，HTTP 层区分 429/400/401/403/5xx，服务端码原样透出——
 *    UI 按八类人话呈现，不再一律「稍后再试」；
 *  · 额度分账（v7.10.0 起仅历史账本）：手动成功记 ok-times、自动成功
 *    另记 auto-ok-times——两账本留存但不再被任何闸门读取（原 24h/3
 *    自动闸已随「上传次数限制彻底移除」专项删除；服务端瞬时限流仍由
 *    RATE_LIMITED 如实转达——客户端不越权也不人为设限）。
 *
 * 服务端契约（skip-ad-log-relay 源码不可读——按已部署客户端契约原样保持）：
 * 请求体九键 schemaVersion 1 / 每批 ≤48 事件 / batchId UUID v4 稳定复用 /
 * X-Skip-Ad-Client 头；成功 = 2xx + ok=true（issueNumber）；RATE_LIMITED +
 * retryAfter；六个终态拒收码（隔离检视，绝不重试同一内容）。
 *
 * Issue 链接真实性（v8.2.2 新增）：成功后匿名 GET 公开仓 issues/{n}——
 * 200 则采用 GitHub 响应里的 html_url（真实 URL，非拼接）；404 则只展示
 * 编号不造链接（Worker 目标仓未知——绝不给未经确认的仓库地址）。
 */
public final class LogUploader {

    /* ═══ 服务端契约常量（自 LogUploadPolicy 平移——一字不差）═══ */

    /** 每批事件上限（服务端 validate.ts 硬界）。 */
    public static final int EVENTS_PER_BATCH = LogStore.EVENTS_PER_BATCH;

    /** UUID v4 校验（batchId / installInstanceId——服务端 401 面最后防线）。 */
    public static boolean isValidUuid(String s) {
        return s != null && s.matches("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    }

    /** 新 batchId（UUID v4）。 */
    public static String newBatchId() {
        return UUID.randomUUID().toString();
    }

    /** 请求体构造（schemaVersion 1 顶层九键——与中转服务 validate.ts 一字不差；
     *  非法 UUID 返回 null=绝不外发明知 401 的请求）。 */
    public static JSONObject buildBody(String batchId, String installInstanceId, String appVersion,
                                       int versionCode, String androidVersion, String createdAt,
                                       int privacyFilterVersion, JSONArray events) {
        if (!isValidUuid(batchId) || !isValidUuid(installInstanceId)) return null;
        try {
            JSONObject b = new JSONObject();
            b.put("schemaVersion", 1);
            b.put("batchId", batchId);
            b.put("installInstanceId", installInstanceId);
            b.put("appVersion", appVersion == null ? "0.0.0" : appVersion);
            b.put("versionCode", versionCode);
            b.put("androidVersion", androidVersion == null ? "" : androidVersion);
            b.put("createdAt", createdAt);
            b.put("privacyFilterVersion", privacyFilterVersion);
            b.put("events", events == null ? new JSONArray() : events);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 服务端终态拒收码（重试必然再拒——隔离检视）。 */
    public static boolean isTerminalRejection(String code) {
        return "PRIVACY_REJECTED".equals(code) || "INVALID_REQUEST".equals(code)
                || "UNAUTHORIZED".equals(code) || "IDEMPOTENCY_CONFLICT".equals(code)
                || "PAYLOAD_TOO_LARGE".equals(code)
                || "MANUAL_RECONCILIATION_REQUIRED".equals(code);
    }

    /* ═══ HTTP seam（真实实现 + JVM 测试替身同接口——真实代码路径可测）═══ */

    /** 响应信封（非 2xx 响应体也解析——错误码/issueNumber 全字段可读）。
     * v7.9.8：网络层失败不再丢细节——errKind 携带 connect/timeout/io
     * 分类（httpCode=0 无 HTTP 响应；errKind=null 表示有 HTTP 响应）。 */
    public static final class Resp {
        public final int httpCode;      // 0 = 网络层失败（未重到 HTTP 响应）
        public final boolean http2xx;
        public final JSONObject json;
        public final boolean bodyOk;
        public final String code;
        public final String errKind;   // connect / timeout / io（HTTP 响应在场时 null）

        public Resp(int httpCode, JSONObject json) {
            this(httpCode, json, null);
        }

        public Resp(int httpCode, JSONObject json, String errKind) {
            this.httpCode = httpCode;
            this.http2xx = httpCode >= 200 && httpCode < 300;
            this.json = json;
            this.bodyOk = json != null && json.optBoolean("ok", false);
            this.code = json == null ? null : json.optString("code", null);
            this.errKind = errKind;
        }

        /** 网络层失败（无 HTTP 响应）——kind: connect/timeout/io。 */
        public static Resp netError(String kind) {
            return new Resp(0, null, kind);
        }
    }

    public interface Http {
        Resp post(String url, JSONObject body);

        /** 匿名验证 Issue 编号在公开仓的真实 URL（404/异常 → null）。 */
        String verifyIssueUrl(int issueNumber);
    }

    /** 生产实现（HttpURLConnection——与 Net 同族；public：阶段化分类纯函数
     *  供 JVM 回归直测）。 */
    public static final class RealHttp implements Http {
        /* v7.9.9 连接超时 15s→10s：上传服务端点（Cloudflare workers.dev）
         * 在国内网络被 DNS 污染/TCP 阻断时表现为连接阶段悬挂——15s 让用户
         * 白等；健康路径下 Android→CF 边缘 TCP+TLS 通常 <2s，10s 已极为宽裕。 */
        private static final int CONNECT_TIMEOUT = 10_000;
        /* v7.9.8：15s→45s——Worker 同步建 GitHub Issue（含重试）可超 15s；
         * 旧值把「服务端实际成功但响应未及」变成客户端「失败」，重试
         * 又因旧版 payload 漂移命中 IDEMPOTENCY_CONFLICT（真机两连失败
         * 的完整链条——冻结批次 + 长超时双修复）。v7.9.9 保持 45s：
         * Worker→GitHub 建 Issue 真实尾延迟可超 20s，盲目调短会重演
         * 修复前的误判链（用户指令：按实际耗时设超时，不盲目增减）。 */
        private static final int READ_TIMEOUT = 45_000;
        /** 公开仓 Issue API（匿名 60 次/小时——上传频次远低于此）。 */
        private static final String ISSUE_API = "https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues/";
        /** URL 白名单：仅接受 https + github.com 域的真实链接。 */
        private static final String URL_ALLOWED_PREFIX = "https://github.com/";

        @Override
        public Resp post(String urlStr, JSONObject body) {
            HttpURLConnection c = null;
            try {
                URL url = new URL(urlStr);
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(CONNECT_TIMEOUT);
                c.setReadTimeout(READ_TIMEOUT);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("X-Skip-Ad-Client", "com.zai.adshield");
                c.setDoOutput(true);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(payload.length);
                OutputStream os = c.getOutputStream();
                os.write(payload);
                os.close();
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                return new Resp(code, parseJson(readAll(in)));
            } catch (Throwable t) {
                return Resp.netError(netKindOf(t)); // v7.9.9 阶段化分类（见 netKindOf）
            } finally {
                if (c != null) c.disconnect();
            }
        }

        @Override
        public String verifyIssueUrl(int issueNumber) {
            if (issueNumber <= 0) return null;
            HttpURLConnection c = null;
            try {
                URL url = new URL(ISSUE_API + issueNumber);
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(10_000);
                c.setReadTimeout(10_000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "skip-ad-log-verify");
                int code = c.getResponseCode();
                if (code != 200) return null; // 404=不在公开仓（Worker 写别处/编号无效）——不造链接
                JSONObject d = parseJson(readAll(c.getInputStream()));
                String u = d == null ? null : d.optString("html_url", "");
                return (u != null && u.startsWith(URL_ALLOWED_PREFIX)) ? u : null;
            } catch (Throwable t) {
                return null;
            } finally {
                if (c != null) c.disconnect();
            }
        }

        private static JSONObject parseJson(String text) {
            try {
                if (text != null && text.trim().startsWith("{")) return new JSONObject(text);
            } catch (Throwable ignored) {
            }
            return null;
        }

        /* ═══ v7.9.9 网络异常阶段化分类（纯函数·JVM 可回归）═══
         * 用户指令：必须区分连接超时与读取超时——两者对用户的含义完全不同：
         * · connect-timeout：TCP/TLS 连接阶段超时——请求几乎必然未送达
         *   服务端（网络不可达/DNS 污染后连接悬挂），换网络重试才有意义；
         * · read-timeout：请求已发出、等待响应阶段超时——服务端可能已
         *   收到甚至已建 Issue（Worker→GitHub 同步建 Issue 可超 20s），
         *   重试必须携带原 batchId（冻结批次保证）——服务端幂等必复用
         *   原 Issue，绝不二建。
         * 判定依据：HttpURLConnection 的 SocketTimeoutException 消息含
         * 「connect」（连接阶段）/「Read timed out」（读取阶段）；消息
         * 缺失/未知 → 按读取超时保守处理（宁可提示「结果无法确认」
         * 也绝不误导用户「服务端未收到」）。 */
        public static boolean isConnectTimeout(java.net.SocketTimeoutException te) {
            String m = te.getMessage();
            return m != null && m.toLowerCase(java.util.Locale.ROOT).contains("connect");
        }

        /** 网络层异常 → 阶段化 kind（connect / connect-timeout / read-timeout /
         *  tls / io——classifyError 的稳定输入）。 */
        public static String netKindOf(Throwable t) {
            if (t instanceof java.net.SocketTimeoutException) {
                return isConnectTimeout((java.net.SocketTimeoutException) t)
                        ? "connect-timeout" : "read-timeout";
            }
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException) {
                return "connect"; // 连接拒绝/不可达/DNS 解析失败（域名污染/无网）
            }
            if (t instanceof javax.net.ssl.SSLException) {
                return "tls"; // TLS 握手失败——网络传输被干扰的典型形态
            }
            return "io"; // 其余 IO/协议异常——保留重试
        }

        private static String readAll(InputStream in) {
            if (in == null) return "";
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return new String(bo.toByteArray(), StandardCharsets.UTF_8);
            } catch (Throwable t) {
                return "";
            } finally {
                try { in.close(); } catch (Throwable ignored) { }
            }
        }
    }

    /* ═══ 实例 ═══ */

    private static volatile LogUploader instance;

    /** Android 生产入口。v7.9.8：deviceCode（Build 品牌型号脱敏码——随
     *  STATS 事件 errClass 字段上行，服务端白名单内；JVM 测试传 null）。 */
    public static LogUploader get(Context ctx) {
        if (instance == null) {
            synchronized (LogUploader.class) {
                if (instance == null) {
                    Context app = ctx.getApplicationContext();
                    instance = new LogUploader(LogStore.get(app), new RealHttp(),
                            new LogStore.AndroidEnv(app), appVersionOf(app), versionCodeOf(app),
                            androidVersionOf(), deviceCodeOf());
                }
            }
        }
        return instance;
    }

    /** JVM 测试入口（真实 runUpload 代码路径 + HTTP 替身）。 */
    public static LogUploader forTest(LogStore store, Http http, LogStore.StoreEnv env,
                                      String appVersion, int versionCode, String androidVersion) {
        return new LogUploader(store, http, env, appVersion, versionCode, androidVersion, null);
    }

    /** 设备码：Build.BRAND_MODEL 清洗为服务端 errClass 白名单形态
     *  （[A-Za-z0-9_.$] ≤80），前缀 DEV_ 标识语义（如 DEV_Xiaomi.2410DPN6CC）。
     *  隐私口径：仅品牌与型号——无序列号/无 AndroidId/无任何设备标识符。 */
    static String deviceCodeOf() {
        try {
            String raw = String.valueOf(android.os.Build.BRAND) + "."
                    + String.valueOf(android.os.Build.MODEL);
            StringBuilder sb = new StringBuilder("DEV_");
            for (int i = 0; i < raw.length() && sb.length() < 80; i++) {
                char ch = raw.charAt(i);
                boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                        || (ch >= '0' && ch <= '9') || ch == '.' || ch == '_' || ch == '$';
                sb.append(ok ? ch : '_');
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String appVersionOf(Context ctx) {
        try {
            String v = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
            return v == null ? "0.0.0" : v;
        } catch (Throwable t) {
            return "0.0.0";
        }
    }

    private static int versionCodeOf(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String androidVersionOf() {
        try {
            return android.os.Build.VERSION.RELEASE == null ? "" : android.os.Build.VERSION.RELEASE;
        } catch (Throwable t) {
            return "";
        }
    }

    private final LogStore store;
    private final Http http;
    private final LogStore.StoreEnv env;
    private final String appVersion;
    private final int versionCode;
    private final String androidVersion;
    private final String deviceCode;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 手动/自动互斥（同一时刻至多一个上传在跑——绝不并发提交同一事件）。 */
    private LogUploader(LogStore store, Http http, LogStore.StoreEnv env,
                        String appVersion, int versionCode, String androidVersion, String deviceCode) {
        this.store = store;
        this.http = http;
        this.env = env;
        this.appVersion = appVersion;
        this.versionCode = versionCode;
        this.androidVersion = androidVersion;
        this.deviceCode = deviceCode;
    }

    /* ═══ 结果摘要（UI 回调契约）═══ */

    public static final class Summary {
        public boolean busy;
        public int uploaded;      // 本次 POST 块数
        public int acked;         // 服务端确认块数（成功）
        public int rejected;      // 服务端终态拒收块数（已隔离检视）
        public int waited;        // 服务端限流块数
        public String issueNumbers = "";  // 逗号分隔（与 acked 对应顺序）
        public String issueUrls = "";     // 逗号分隔（未验证到真实 URL 的位置为空串）
        public int pending;       // 剩余待传块数（估算）
        public int pendingEvents; // 剩余事件数
        public long totalCollected;
        public String emptyReason = "";   // acked==0 且无块可传时的四态区分
        public String lastError = "";     // 原始错误（服务端码/HTTP 状态）
        public String errKind = "";       // v7.9.8 稳定分类码（UI 八类人话映射依据）
        /* v7.10.0：acked24h/retryAfterMs 已随上传次数限制移除删除——
         * Summary 不再回传 24h 成功额度与限流等待毫秒（UI 额度文案已清）。 */

        public String toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("busy", busy);
                o.put("uploaded", uploaded);
                o.put("acked", acked);
                o.put("rejected", rejected);
                o.put("waited", waited);
                o.put("issueNumbers", issueNumbers);
                o.put("issueUrls", issueUrls);
                o.put("pending", pending);
                o.put("pendingEvents", pendingEvents);
                o.put("totalCollected", totalCollected);
                o.put("emptyReason", emptyReason);
                o.put("lastError", lastError);
                o.put("errKind", errKind);
            } catch (Throwable ignored) {
            }
            return o.toString();
        }
    }

    /* ═══ v7.9.8 错误分类（稳定码 · 纯函数 · JVM 可回归）═══
     * 依据真实 HTTP 状态码 / 服务端错误码 / 网络层异常类型三路判定，
     * 绝不猜測。UI 按用户八类表映射人话提示。 */

    public static final String ERR_NET_CONNECT = "NET_CONNECT_FAIL";   // 网络连接失败（拒绝/不可达/DNS）
    public static final String ERR_NET_CONNECT_TIMEOUT = "NET_CONNECT_TIMEOUT"; // 连接阶段超时（请求几乎必然未送达）
    public static final String ERR_NET_READ_TIMEOUT = "NET_READ_TIMEOUT";   // 读取阶段超时（请求可能已送达——幂等重试语义）
    public static final String ERR_NET_TLS = "NET_TLS";               // TLS 握手失败（网络传输被干扰）
    public static final String ERR_NET_TIMEOUT = "NET_TIMEOUT";         // 超时（旧版兼容分类——v7.9.9 起细分三态）
    public static final String ERR_NET_IO = "NET_IO";                   // 网络传输异常
    public static final String ERR_RATE_LIMITED = "RATE_LIMITED";       // 频率限制（429/服务端码）
    public static final String ERR_RELAY_5XX = "RELAY_SERVER_ERROR";    // 中转/GitHub 上游异常
    public static final String ERR_RELAY_TEMP = "RELAY_TEMP_ERROR";     // 服务端临时错误（可重试）
    public static final String ERR_PROTOCOL = "PROTOCOL_UNEXPECTED_BODY"; // 2xx 但无 ok=true
    public static final String ERR_UNKNOWN = "UNKNOWN";                 // 未知

    /** 错误分类：网络层（errKind·v7.9.9 阶段化五态）→ 服务端 JSON 码 →
     *  HTTP 状态码三路优先级。连接超时与读取超时分家（用户指令）：前者
     *  请求几乎必然未送达（换网络）；后者请求可能已送达甚至已处理
     *  （幂等重试必复用原 Issue——冻结批次保证）。 */
    public static String classifyError(Resp res) {
        if (res == null) return ERR_NET_IO;
        if (res.errKind != null) {
            if ("connect".equals(res.errKind)) return ERR_NET_CONNECT;
            if ("connect-timeout".equals(res.errKind)) return ERR_NET_CONNECT_TIMEOUT;
            if ("read-timeout".equals(res.errKind)) return ERR_NET_READ_TIMEOUT;
            if ("tls".equals(res.errKind)) return ERR_NET_TLS;
            if ("timeout".equals(res.errKind)) return ERR_NET_TIMEOUT; // 旧替身兼容
            return ERR_NET_IO;
        }
        String code = res.code;
        if (code != null && code.length() > 0) {
            if ("RATE_LIMITED".equals(code)) return ERR_RATE_LIMITED;
            if (isTerminalRejection(code)) return code;      // 服务端终态码原样透出
            if ("TEMPORARY_ERROR".equals(code)) return ERR_RELAY_TEMP;
            return code;                                     // 其余服务端码透出
        }
        if (res.http2xx && !res.bodyOk) return ERR_PROTOCOL; // 2xx 但 body 无 ok=true
        if (res.httpCode == 429) return ERR_RATE_LIMITED;
        if (res.httpCode == 400) return "INVALID_REQUEST";
        /* v7.9.8b 认证与权限分家（用户指令：401 提示认证问题、403 提示权限
         * 问题——不再混同为 UNAUTHORIZED）。仅作用于「裸状态码」（服务端
         * 未给出 JSON body 的网关层拦截）；服务端 JSON 码 UNAUTHORIZED
         * 仍按其契约原样透出（isTerminalRejection 拒收隔离语义不变）。
         * 裸 401/403 无服务端解释——按瞬时错误保留冻结批次重试（runUpload
         * 终态判定用 res.code 而非本分类码，行为不变，仅提示文案分家）。 */
        if (res.httpCode == 401) return "AUTH_FAILED";
        if (res.httpCode == 403) return "FORBIDDEN";
        if (res.httpCode >= 500) return ERR_RELAY_5XX;
        if (res.httpCode > 0) return "HTTP_" + res.httpCode;
        return ERR_UNKNOWN;
    }

    /* ═══ 自动上传（心跳检查点——KeepAliveReceiver 顺带调用）═══ */

    /** 自动上传资格（纯函数·JVM 可回归；v7.10.0 次数限制彻底移除版）：
     *  距上次自动尝试 ≥ minInterval 且存在待传事件——仅此两闸（心跳间隔
     *  8h + 「有新日志才传」）。24h/3 成功额度闸已随「上传次数限制彻底
     *  移除」专项删除：用户想传多少次就传多少次（服务端 per-minute 瞬时
     *  防爆层 RATE_LIMITED 如实转达，本地绝不人为设限）。 */
    public static boolean autoEligible(long lastAutoAttemptAt, long now, long minIntervalMs,
                                       int pendingEvents) {
        if (now - lastAutoAttemptAt < minIntervalMs) return false;
        return pendingEvents > 0;  // 没有新日志——零网络请求直接跳过
    }

    /** 自动检查点（后台线程一次性上传——无守护线程无退避表）。 */
    public void requestAutoCheck(long minIntervalMs) {
        try {
            if (com.zai.adshield.Net.LOG_RELAY_URL.length() == 0) return;
            long now = System.currentTimeMillis();
            long last = prefLong("lastAutoAttemptAt", 0L);
            if (!autoEligible(last, now, minIntervalMs, store.pendingEventCount())) return;
            env.prefPut("lastAutoAttemptAt", String.valueOf(now));
            if (!running.compareAndSet(false, true)) return;
            Thread t = new Thread(() -> {
                try {
                    runUpload(false);
                } catch (Throwable ignored) {
                } finally {
                    running.set(false);
                }
            }, "log-upload-auto");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
        }
    }

    /* ═══ 手动上传（一键上传——随时可用，不查额度）═══ */

    /** 手动一键上传（阻塞执行——调用方必须已在后台线程；与自动互斥 CAS，
     *  拿不到即 busy）。不受 24h/3 本地额度限制（服务端限额如实转达）。 */
    public Summary submitNow() {
        Summary s = new Summary();
        try {
            if (com.zai.adshield.Net.LOG_RELAY_URL.length() == 0) {
                s.lastError = "relay-offline";
                s.emptyReason = "relay-offline";
                return s;
            }
            if (!running.compareAndSet(false, true)) {
                try { Thread.sleep(500); } catch (Throwable ignored) { }
                if (!running.compareAndSet(false, true)) {
                    s.busy = true;
                    return s;
                }
            }
            try {
                return runUpload(true);
            } finally {
                running.set(false);
            }
        } catch (Throwable t) {
            s.lastError = "internal: " + t.getClass().getSimpleName();
            return s;
        }
    }

    /* ═══ 上传主循环（唯一执行路径——手动/自动同一份代码）═══ */

    /**
     * v7.9.8 冻结循环：头部已有未决冻结批次 → 字节级复用（同 batchId 同
     * payload——服务端必然 ALREADY_ACCEPTED 复用原编号）；否则从 pending
     * 头部封批并冻结后 POST。逐块推进直到：事件传完 / 服务端限流 /
     * 网络·瞬时错误（保留冻结批次下次重试）/ 终态拒收（该块隔离，继续
     * 后续块）。STATS 快照注入本次运行的【首个新封批次】（冻结重试块
     * 自带历史快照——绝不重构漂移 payload）。
     * public 供 JVM 测试直驱真实代码路径（生产入口仍是 submitNow /
     * requestAutoCheck——两者只是包一层互斥与线程）。
     */
    public Summary runUpload(boolean manual) {
        Summary s = new Summary();
        try {
            JSONObject statsEvent = buildStatsEvent();
            boolean statsPending = statsEvent != null; // 注入本次运行首个新封批次
            while (true) {
                LogStore.FrozenBatch fb = store.headFrozenOrNull();
                if (fb == null || fb.realCount < 0) {
                    // 无冻结头部（或 v1 遗留待补冻）→ 从 pending 头部封批
                    JSONArray pending = store.pendingEvents();
                    if (pending.length() == 0 && fb == null) break;
                    int realCount = Math.min(EVENTS_PER_BATCH - (statsPending ? 1 : 0),
                            pending.length());
                    if (realCount <= 0) break;
                    JSONArray chunk = new JSONArray();
                    if (statsPending) chunk.put(statsEvent);
                    for (int i = 0; i < realCount; i++) chunk.put(pending.optJSONObject(i));
                    fb = store.sealBatch(chunk, isoNow()); // POST 前落盘冻结
                    statsPending = false;
                } else {
                    statsPending = false; // 首个新封批次顺延（冻结块自带历史快照）
                }
                if (fb == null || !isValidUuid(fb.batchId)) {
                    s.lastError = "client-defense: batchId";
                    s.errKind = ERR_UNKNOWN;
                    break;
                }
                JSONObject body = buildBody(fb.batchId, store.installInstanceId(), appVersion,
                        versionCode, androidVersion, fb.createdAt,
                        LogPrivacyFilter.VERSION, fb.events);
                if (body == null) {
                    s.lastError = "client-defense: body";
                    s.errKind = ERR_UNKNOWN;
                    break;
                }
                s.uploaded++;
                Resp res = http.post(com.zai.adshield.Net.LOG_RELAY_URL, body);
                boolean ok = res != null && res.http2xx && res.bodyOk; // 2xx≠成功——body ok=true 才算
                if (ok) {
                    int issue = res.json != null ? res.json.optInt("issueNumber", 0) : 0;
                    store.consumeHead(Math.max(0, fb.realCount));
                    store.recordOk(System.currentTimeMillis(), manual);
                    s.acked++;
                    /* v7.10.0：批粒度 24h/3 自动闸已随「上传次数限制彻底移除」
                     * 专项删除——积压场景单次运行可连续排空全部批次（每批
                     * 独立 batchId 幂等保护保持，绝不二建 Issue）。 */
                    if (issue > 0) {
                        s.issueNumbers = s.issueNumbers.length() == 0
                                ? String.valueOf(issue) : s.issueNumbers + "," + issue;
                        String url = http.verifyIssueUrl(issue);
                        s.issueUrls = s.issueUrls.length() == 0 ? (url == null ? "" : url)
                                : s.issueUrls + "," + (url == null ? "" : url);
                        env.prefPut("lastIssueNumber", String.valueOf(issue));
                        if (url != null) env.prefPut("lastIssueUrl", url);
                    }
                    continue;
                }
                String code = res == null || res.json == null ? null : res.code;
                if ("RATE_LIMITED".equals(code)) {
                    s.waited++;
                    s.lastError = "rate-limited";
                    s.errKind = ERR_RATE_LIMITED;
                    /* v7.10.0：服务端 per-minute 瞬时防爆层如实分类转达——
                     * 不再解析 retryAfter 呈现「等待 N 分钟」（次数限制文案
                     * 已清；日志保留可立即重试）。 */
                    break; // 保留冻结批次——服务端瞬时限流，稍后自动/手动再试
                }
                if (code != null && isTerminalRejection(code)) {
                    String err = res != null && res.json != null
                            ? res.json.optString("error", "") : "";
                    store.rejectHead(Math.max(0, fb.realCount), code, err);
                    s.rejected++;
                    s.lastError = code;
                    s.errKind = code;
                    continue; // 后续块独立内容——继续
                }
                s.lastError = code == null
                        ? (res == null ? "network" : String.valueOf(res.httpCode)) : code;
                s.errKind = classifyError(res);
                break; // 网络/瞬时错误——保留冻结批次，下次心跳或手动重试
            }
            finalizeSummary(s);
        } catch (Throwable t) {
            s.lastError = "loop: " + t.getClass().getSimpleName();
            s.errKind = ERR_UNKNOWN;
            try {
                finalizeSummary(s);
            } catch (Throwable ignored) {
            }
        }
        return s;
    }

    private void finalizeSummary(Summary s) {
        try {
            long now = System.currentTimeMillis();
            JSONObject ov = store.overview(now);
            s.pending = ov.optInt("pending", 0);
            s.pendingEvents = ov.optInt("pendingEvents", 0);
            s.totalCollected = ov.optLong("totalCollected", 0L);
            if (s.acked == 0 && s.uploaded == 0 && s.waited == 0 && s.lastError.length() == 0) {
                s.emptyReason = emptyReasonOf(System.currentTimeMillis(), ov);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 手动上传四态区分（无块可传且无错误时）：读取失败 / 采集失败 /
     *  从未产生 / 已全部上传（或被清理/被拒收）。纯函数·JVM 可回归。 */
    static String emptyReasonOf(long now, JSONObject overview) {
        try {
            if (overview.optBoolean("readCorrupt", false)) return "read-failed";
            long appendFailAt = overview.optLong("appendFailAt", 0L);
            if (appendFailAt > 0 && now - appendFailAt < 24L * 3600 * 1000) return "append-failed";
            if (overview.optLong("totalCollected", 0L) == 0) return "never-collected";
            if (overview.optInt("rejected", 0) > 0 && overview.optLong("lastAckedAt", 0L) == 0) {
                return "rejected-only";
            }
            if (overview.optLong("lastAckedAt", 0L) > 0) return "all-uploaded";
            return "cleared";
        } catch (Throwable t) {
            return "read-failed";
        }
    }

    /* ── STATS 快照（真实静态计数器——JVM 同路径可测）──
     * v7.9.8：errClass 携带设备码（DEV_Brand.Model——白名单形态，无任何
     * 设备标识符），分析侧可区分机型归因（用户指令：设备型号入诊断）。 */
    private JSONObject buildStatsEvent() {
        try {
            return LogEvents.stats(
                    com.zai.adshield.SubscriptionManager.subStatV2Snapshot(),
                    DecisionEvidence.outcomeSnapshot()[0],
                    DecisionEvidence.outcomeSnapshot()[2],
                    DecisionEvidence.outcomeSnapshot()[5],
                    deviceCode);
        } catch (Throwable t) {
            return null;
        }
    }

    /* ── 内部 ── */

    private long prefLong(String key, long def) {
        try {
            String v = env.prefGet(key);
            return v == null ? def : Long.parseLong(v);
        } catch (Throwable t) {
            return def;
        }
    }

    private String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
