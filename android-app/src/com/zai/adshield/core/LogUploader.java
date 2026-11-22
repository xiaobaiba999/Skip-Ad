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
 * 六项业务（用户 v8.2.2 指令——除此之外零附加机制）：
 *  ① 自动上传 24h 最多成功 3 次（ok-times 只记服务端确认）；
 *  ② 自动上传必须有新日志（pending==0 直接跳过，零网络请求）；
 *  ③ 手动上传随时可用（不查本地额度；服务端 RATE_LIMITED 如实转达）；
 *  ④ 成功 = HTTP 2xx 且 body ok=true（服务端明确确认才消费事件）；
 *  ⑤ 失败保留全部未确认事件（同 batchId 重试——服务端幂等防重复 Issue）；
 *  ⑥ 自动上传开关只管自动（采集常开、手动不受影响）。
 *
 * 删除的 v8.x 机制（不再存在）：六状态机批次文件、20 批容量环、退避表
 * 30s→480s×5、6 次耗尽终态、失败批次复活启发式、8 轮排空循环、守护线程。
 * 失败后的重试节奏 = 下一次心跳（自动）或用户再点一次（手动）——没有更
 * 多的状态要维护。
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

    /** 响应信封（非 2xx 响应体也解析——错误码/issueNumber 全字段可读）。 */
    public static final class Resp {
        public final boolean http2xx;
        public final JSONObject json;
        public final boolean bodyOk;
        public final String code;

        public Resp(int httpCode, JSONObject json) {
            this.http2xx = httpCode >= 200 && httpCode < 300;
            this.json = json;
            this.bodyOk = json != null && json.optBoolean("ok", false);
            this.code = json == null ? null : json.optString("code", null);
        }
    }

    public interface Http {
        Resp post(String url, JSONObject body);

        /** 匿名验证 Issue 编号在公开仓的真实 URL（404/异常 → null）。 */
        String verifyIssueUrl(int issueNumber);
    }

    /** 生产实现（HttpURLConnection——与 Net 同族）。 */
    static final class RealHttp implements Http {
        private static final int CONNECT_TIMEOUT = 15_000;
        private static final int READ_TIMEOUT = 15_000;
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
                return null; // 网络异常——保留重试
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

    /** Android 生产入口。 */
    public static LogUploader get(Context ctx) {
        if (instance == null) {
            synchronized (LogUploader.class) {
                if (instance == null) {
                    Context app = ctx.getApplicationContext();
                    instance = new LogUploader(LogStore.get(app), new RealHttp(),
                            new LogStore.AndroidEnv(app), appVersionOf(app), versionCodeOf(app),
                            androidVersionOf());
                }
            }
        }
        return instance;
    }

    /** JVM 测试入口（真实 runUpload 代码路径 + HTTP 替身）。 */
    public static LogUploader forTest(LogStore store, Http http, LogStore.StoreEnv env,
                                      String appVersion, int versionCode, String androidVersion) {
        return new LogUploader(store, http, env, appVersion, versionCode, androidVersion);
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
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 手动/自动互斥（同一时刻至多一个上传在跑——绝不并发提交同一事件）。 */
    private LogUploader(LogStore store, Http http, LogStore.StoreEnv env,
                        String appVersion, int versionCode, String androidVersion) {
        this.store = store;
        this.http = http;
        this.env = env;
        this.appVersion = appVersion;
        this.versionCode = versionCode;
        this.androidVersion = androidVersion;
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
        public int acked24h;      // 24h 内成功块数（含本次）
        public int pending;       // 剩余待传块数（估算）
        public int pendingEvents; // 剩余事件数
        public long totalCollected;
        public String emptyReason = "";   // acked==0 且无块可传时的四态区分
        public String lastError = "";

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
                o.put("acked24h", acked24h);
                o.put("pending", pending);
                o.put("pendingEvents", pendingEvents);
                o.put("totalCollected", totalCollected);
                o.put("emptyReason", emptyReason);
                o.put("lastError", lastError);
            } catch (Throwable ignored) {
            }
            return o.toString();
        }
    }

    /* ═══ 自动上传（心跳检查点——KeepAliveReceiver 顺带调用）═══ */

    /** 自动上传资格（纯函数·JVM 可回归）：距上次自动尝试 ≥ minInterval 且
     *  存在待传事件且 24h 成功数 <3。失败不耗额度（ok-times 只记成功）。 */
    public static boolean autoEligible(long lastAutoAttemptAt, long now, long minIntervalMs,
                                       int pendingEvents, int okCount24h) {
        if (now - lastAutoAttemptAt < minIntervalMs) return false;
        if (pendingEvents <= 0) return false;  // 没有新日志——零网络请求直接跳过
        return okCount24h < 3;
    }

    /** 自动检查点（后台线程一次性上传——无守护线程无退避表）。 */
    public void requestAutoCheck(long minIntervalMs) {
        try {
            if (com.zai.adshield.Net.LOG_RELAY_URL.length() == 0) return;
            long now = System.currentTimeMillis();
            long last = prefLong("lastAutoAttemptAt", 0L);
            if (!autoEligible(last, now, minIntervalMs,
                    store.pendingEventCount(), store.okCount24h(now))) return;
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
     * 逐块上传直到：事件传完 / 服务端限流 / 网络·瞬时错误（保留同 batchId
     * 下次重试）/ 终态拒收（该块隔离，继续后续块）。STATS 快照仅注入本次
     * 运行的首块（内存注入不落盘——空缓冲绝不产生仅含 STATS 的垃圾 Issue）。
     * public 供 JVM 测试直驱真实代码路径（生产入口仍是 submitNow /
     * requestAutoCheck——两者只是包一层互斥与线程）。
     */
    public Summary runUpload(boolean manual) {
        Summary s = new Summary();
        try {
            JSONObject statsEvent = buildStatsEvent();
            boolean statsPending = statsEvent != null; // 只注入一次（首块）
            while (true) {
                JSONArray events = store.pendingEvents();
                if (events.length() == 0) break;
                int realCount = Math.min(EVENTS_PER_BATCH - (statsPending ? 1 : 0), events.length());
                if (realCount <= 0) break;
                String batchId = store.headBatchIdOrAppend(); // POST 前落盘——同号重试
                if (!isValidUuid(batchId)) {
                    s.lastError = "client-defense: batchId";
                    break;
                }
                JSONArray chunk = new JSONArray();
                if (statsPending) chunk.put(statsEvent);
                for (int i = 0; i < realCount; i++) chunk.put(events.optJSONObject(i));
                JSONObject body = buildBody(batchId, store.installInstanceId(), appVersion,
                        versionCode, androidVersion, isoNow(),
                        LogPrivacyFilter.VERSION, chunk);
                if (body == null) {
                    s.lastError = "client-defense: body";
                    break;
                }
                s.uploaded++;
                Resp res = http.post(com.zai.adshield.Net.LOG_RELAY_URL, body);
                boolean ok = res != null && res.http2xx && res.bodyOk; // 2xx≠成功——body ok=true 才算
                if (ok) {
                    int issue = res.json != null ? res.json.optInt("issueNumber", 0) : 0;
                    store.consumeHead(realCount);
                    store.recordOk(System.currentTimeMillis());
                    s.acked++;
                    if (issue > 0) {
                        s.issueNumbers = s.issueNumbers.length() == 0
                                ? String.valueOf(issue) : s.issueNumbers + "," + issue;
                        String url = http.verifyIssueUrl(issue);
                        s.issueUrls = s.issueUrls.length() == 0 ? (url == null ? "" : url)
                                : s.issueUrls + "," + (url == null ? "" : url);
                        env.prefPut("lastIssueNumber", String.valueOf(issue));
                        if (url != null) env.prefPut("lastIssueUrl", url);
                    }
                    statsPending = false; // STATS 已随首块发出
                    continue;
                }
                String code = res == null || res.json == null ? null : res.code;
                if ("RATE_LIMITED".equals(code)) {
                    s.waited++;
                    s.lastError = "rate-limited";
                    break; // 保留——服务端窗口，稍后自动/手动再试
                }
                if (code != null && isTerminalRejection(code)) {
                    String err = res != null && res.json != null
                            ? res.json.optString("error", "") : "";
                    store.rejectHead(realCount, code, err);
                    s.rejected++;
                    s.lastError = code;
                    statsPending = false; // 该块已了结（含注入的 STATS）
                    continue; // 后续块独立内容——继续
                }
                s.lastError = code == null ? "network" : code;
                break; // 网络/瞬时错误——保留同 batchId，下次心跳或手动重试
            }
            finalizeSummary(s);
        } catch (Throwable t) {
            s.lastError = "loop: " + t.getClass().getSimpleName();
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
            s.acked24h = ov.optInt("acked24h", 0);
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

    /* ── STATS 快照（真实静态计数器——JVM 同路径可测）── */

    private JSONObject buildStatsEvent() {
        try {
            return LogEvents.stats(
                    com.zai.adshield.SubscriptionManager.subStatV2Snapshot(),
                    DecisionEvidence.outcomeSnapshot()[0],
                    DecisionEvidence.outcomeSnapshot()[2],
                    DecisionEvidence.outcomeSnapshot()[5]);
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
