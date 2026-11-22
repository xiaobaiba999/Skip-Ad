package com.zai.adshield.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

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
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v8.0.0 日志上传调度器（中转服务通道）。
 * ─────────────────────────────────────────────
 * 纪律（与广告主链严格隔离——上传失败绝不影响跳过核心功能）：
 *  · 单后台守护线程（running CAS 门）——同一时刻最多一个上传循环，绝不并发
 *    提交同批次（服务端 batchId 幂等是第二道闸）；
 *  · 绝不在无障碍事件/点击/复验线程上执行网络 IO；
 *  · 一切「下一步」决策走 LogUploadPolicy 纯函数（JVM 可回归）；
 *  · MANUAL_RECONCILIATION_REQUIRED（服务端不确定终局）→ 立即 FAILED 终态，
 *    绝不退避、绝不换 batchId 重传同一内容（服务端三轮红线）；
 *  · RATE_LIMITED：尊重服务端 retryAfter（不消耗本地重试计数——被拒≠失败）；
 *  · 24h/3 上限以服务端滑窗为权威；本地 ackedCount24h 仅供 UI 显示。
 *
 * 触发模型（事件优先 · 时间兜底——不轮询凑次数）：
 *  · requestAutoCheck(minIntervalMs)：KeepAliveReceiver 心跳顺带（8h 客户端
 *    节拍 + 授权门 + RELAY_URL 门 + v8.1.0 本地 24h/3 前置闸）；
 *  · submitNowBlocking()：v8.1.0 一键上传（手动路径强化版——强制封批 +
 *    阻塞执行 + 结果 Summary 回调 UI；手动不受自动 24h/3 额度限制，
 *    服务端 RATE_LIMITED 幂等兑底）；v8.2.1 手动强化：①开跑前复活「重试
 *    耗尽型」FAILED 批次（同 batchId——服务端幂等兜底，终态拒收型绝不
 *    复活）；②有界排空循环（每轮 3 批、最多 8 轮、无进展即停——一键上传
 *    不再只传首批 3 批就停；仅首轮补 STATS，后续轮不造垃圾批）。
 *  · 网络恢复/批次到期由心跳检查点吸收（不另设高频闹钟）。
 */
public final class LogUploadDispatcher {

    /** v8.1.0 一键上传结果摘要（纯数据——MainActivity 回调 app.js 渲染用）。
     *  字段语义：sealed=本次封批数；revived=v8.2.1 手动复活的耗尽型失败批数；
     *  uploaded=尝试上传批数；acked=服务端确认数；waited=服务端限流（24h 窗口
     *  拒收）；failed=终态失败；retried=退避重试中；pending=剩余到期批次；
     *  pendingEvents=剩余缓冲事件；acked24h=24h 已确认批次；busy=因占用跳过；
     *  issueNumbers=逗号分隔 Issue 编号；lastError=末次错误码。toJson() 由
     *  JS 侧直接 JSON.parse。 */
    public static final class Summary {
        public int sealed;
        public int revived;
        public int uploaded;
        public int acked;
        public int waited;
        public int failed;
        public int retried;
        public int pending;
        public int pendingEvents;
        public int acked24h;
        public boolean busy;
        public String issueNumbers = "";
        public String lastError = "";

        public String toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("sealed", sealed);
                o.put("revived", revived);
                o.put("uploaded", uploaded);
                o.put("acked", acked);
                o.put("waited", waited);
                o.put("failed", failed);
                o.put("retried", retried);
                o.put("pending", pending);
                o.put("pendingEvents", pendingEvents);
                o.put("acked24h", acked24h);
                o.put("busy", busy);
                o.put("issueNumbers", issueNumbers);
                o.put("lastError", lastError);
            } catch (Throwable ignored) {
            }
            return o.toString();
        }
    }

    /** 客户端标识头（公开常量——非秘密；服务端形态闸过滤非客户端流量）。 */
    public static final String CLIENT_HEADER = "com.zai.adshield";
    private static final int CONNECT_TIMEOUT = 15_000;
    private static final int READ_TIMEOUT = 15_000;
    private static final int MAX_BATCHES_PER_RUN = 3;
    /** 封批阈值（缓冲事件数——不凑次数：空批不传）。 */
    static final int SEAL_THRESHOLD = 20;

    private static volatile LogUploadDispatcher instance;

    public static LogUploadDispatcher get(Context ctx) {
        if (instance == null) {
            synchronized (LogUploadDispatcher.class) {
                if (instance == null) instance = new LogUploadDispatcher(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context ctx;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private LogUploadDispatcher(Context ctx) {
        this.ctx = ctx;
    }

    /* ── 对外入口 ── */

    /** 自动检查点（KeepAliveReceiver 心跳顺带调用；门卫：地址+最小间隔+本地
     *  24h/3 前置闸——授权门在上游）。v8.1.0 新增本地前置闸：24h 内已 ACK
     *  ≥3 批则不再发起自动上传（省电省流量；服务端滑窗仍为权威——双保险）。
     *  手动路径（submitNowBlocking）【不】受此闸（用户明确
     *  意图优先，服务端 RATE_LIMITED 幂等兜底）。 */
    public void requestAutoCheck(long minIntervalMs) {
        try {
            if (com.zai.adshield.Net.LOG_RELAY_URL.length() == 0) return; // 中转未上线：结构性关闭
            long last = ctx.getSharedPreferences("logupload", Context.MODE_PRIVATE).getLong("lastAttemptAt", 0L);
            long now = System.currentTimeMillis();
            if (!autoCheckEligible(last, now, minIntervalMs,
                    LogBatchStore.get(ctx).ackedCount24h(now))) return;
            dispatch();
        } catch (Throwable ignored) {
        }
    }

    /** v8.1.0 自动上传资格判定（提取为纯函数——JVM 可回归）：间隔达标 +
     *  本地 24h ACK 计数 <3（服务端滑窗为权威的第二道本地闸）。 */
    public static boolean autoCheckEligible(long lastAttemptAt, long now, long minIntervalMs, int acked24h) {
        if (now - lastAttemptAt < minIntervalMs) return false;
        return acked24h < 3;
    }

    /** 手动「立即提交」入口已统一到 submitNowBlocking（v8.1.0 起两个 UI
     *  入口都走 logUploadSubmitNow 桥；旧 requestUploadNow 已于 v8.2.1 移除）。 */

    /** v8.1.0 一键上传（阻塞执行——调用方【必须】已在后台线程；与自动循环
     *  互斥：同一 running CAS 门，拿不到短等 500ms 重试一次仍占用则返回 busy）。
     *  手动路径强制封批（forceSeal——缓冲不足 20 条也封，轻用户点按钮不再
     *  静默空转）；手动不受自动 24h/3 额度限制（服务端 RATE_LIMITED 幂等
     *  兑底——被拒即 WAIT 退避，绝不绕过）。返回 Summary 供 UI 回调。 */
    public Summary submitNowBlocking() {
        Summary s = new Summary();
        try {
            if (com.zai.adshield.Net.LOG_RELAY_URL.length() == 0) {
                s.lastError = "relay-offline";
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
                Summary total = null;
                // v8.2.1 有界排空循环：每轮最多传 3 批（服务端友好节拍不变），
                // 本轮有进展且仍有到期批次则继续（上限 8 轮 ≥ BATCH_CAP 20 批
                // 最坏情况；无进展/无剩余即停——绝不无限循环）。仅首轮补 STATS
                // 与复活（后续轮再补 STATS 会造出仅含 STATS 的垃圾批）。
                for (int round = 0; round < 8; round++) {
                    Summary r = runLoop(true, round == 0);
                    if (total == null) total = r;
                    else mergeSummary(total, r);
                    boolean didWork = r.acked + r.failed + r.waited + r.retried > 0;
                    if (!didWork || r.pending <= 0) break;
                }
                return total != null ? total : s;
            } finally {
                running.set(false);
            }
        } catch (Throwable t) {
            s.lastError = "internal: " + t.getClass().getSimpleName();
            return s;
        }
    }

    /** v8.2.1 排空轮次合并（计数累加、快照字段取末轮、issueNumbers 拼接、
     *  lastError 保留末次非空）。 */
    private static void mergeSummary(Summary total, Summary r) {
        total.sealed += r.sealed;
        total.revived += r.revived;
        total.uploaded += r.uploaded;
        total.acked += r.acked;
        total.waited += r.waited;
        total.failed += r.failed;
        total.retried += r.retried;
        total.pending = r.pending;
        total.pendingEvents = r.pendingEvents;
        total.acked24h = r.acked24h;
        if (r.issueNumbers.length() > 0) {
            total.issueNumbers = total.issueNumbers.length() == 0
                    ? r.issueNumbers : total.issueNumbers + "," + r.issueNumbers;
        }
        if (r.lastError.length() > 0) total.lastError = r.lastError;
    }

    private void dispatch() {
        if (!running.compareAndSet(false, true)) return; // 已有循环在跑：本检查点跳过
        Thread t = new Thread(() -> {
            try {
                runLoop();
            } catch (Throwable ignored) {
            } finally {
                running.set(false);
            }
        }, "log-upload");
        t.setDaemon(true);
        t.start();
    }

    /* ── 主循环 ── */

    private void runLoop() {
        runLoop(false, true);
    }

    /** v8.1.0：runLoop(forceSeal) 返回结果摘要（一键上传 UI 反馈用）；
     *  forceSeal=true（手动一键上传）：缓冲不足 20 条也封批（>0 即封）——
     *  旧版手动路径继承自动路径的 20 条阈值，轻度用户可能数周攒不满 →
     *  点「立即提交」静默空转零反馈（真机反馈「导出代替上传」体验的一部分）。
     *  自动路径 forceSeal=false：20 条阈值语义不变（不凑次数红线保持）。
     *  v8.2.1：appendStats——排空循环仅首轮补 STATS；forceSeal 先复活耗尽型
     *  FAILED 批次（手动用户明确意图；终态拒收型绝不复活——重试必然再拒）。 */
    private Summary runLoop(boolean forceSeal, boolean appendStats) {
        Summary s = new Summary();
        try {
            LogBatchStore store = LogBatchStore.get(ctx);
            // STATS 快照先行（六态 + GKD 八格——纯静态计数器读取，开机周期累计口径）
            if (appendStats) {
                try {
                    store.appendEvent(com.zai.adshield.core.LogEvents.stats(
                            com.zai.adshield.SubscriptionManager.subStatV2Snapshot(),
                            snapshot6(0), snapshot6(2), snapshot6(5)));
                } catch (Throwable ignored) {
                }
            }
            // v8.2.1 手动路径：先复活耗尽型 FAILED（同 batchId 重试——服务端幂等兑底）
            if (forceSeal) {
                try {
                    s.revived = store.reviveExhaustedForManual(System.currentTimeMillis());
                } catch (Throwable ignored) {
                }
            }
            // 封批（自动：阈值 20 不凑次数；手动：>0 即封；单批 ≤48——堆积
            // 分多批，guard 防御环；空批/隐私拒收即止）
            int sealGuard = 0;
            while (shouldSeal(store.pendingEventCount(), forceSeal) && sealGuard++ < 24) {
                if (store.sealBatch(appVersion(), versionCode()) == null) break;
                s.sealed++;
            }
            long now = System.currentTimeMillis();
            List<JSONObject> due = store.dueBatches(now, MAX_BATCHES_PER_RUN);
            s.uploaded = due.size();
            for (JSONObject batch : due) {
                uploadOne(store, batch, s);
            }
            try {
                s.pending = store.dueBatches(System.currentTimeMillis(), Integer.MAX_VALUE).size();
                s.pendingEvents = store.pendingEventCount();
                s.acked24h = store.ackedCount24h(System.currentTimeMillis());
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            s.lastError = "loop: " + t.getClass().getSimpleName();
        }
        return s;
    }

    /** v8.1.0 封批决策（提取为纯函数——JVM 可回归）：自动路径阈值 20（不凑
     *  次数）；手动路径 >0 即封（一键上传绝不空转）。空批永不封（红线）。 */
    public static boolean shouldSeal(int pendingCount, boolean forceSeal) {
        return forceSeal ? pendingCount > 0 : pendingCount >= SEAL_THRESHOLD;
    }

    /** DecisionEvidence 六态快照取格（[VERIFIED_SUCCESS, RETRY, MISS, FALSE_CLICK, SAFE_BLOCK, UNKNOWN]）。 */
    private static int snapshot6(int idx) {
        try {
            int[] snap = com.zai.adshield.core.DecisionEvidence.outcomeSnapshot();
            if (idx < snap.length) return snap[idx];
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private void uploadOne(LogBatchStore store, JSONObject batch, Summary s) {
        String batchId = batch.optString("batchId", "");
        if (!LogUploadPolicy.isValidUuid(batchId)) return; // 防御：坏记录绝不外发
        try {
            String installId = batch.optString("installInstanceId", store.installInstanceId());
            JSONObject body = LogUploadPolicy.buildBody(batchId, installId,
                    batch.optString("appVersion", appVersion()),
                    batch.optInt("versionCode", versionCode()),
                    String.valueOf(Build.VERSION.RELEASE), isoNow(),
                    batch.optInt("privacyFilterVersion", LogPrivacyFilter.VERSION),
                    batch.optJSONArray("events"));
            if (body == null) { // batchId/installId 非法（防御）——终态保留检视
                store.markFailed(batchId, "client-defense: invalid ids", LogBatchStore.FAILKIND_TERMINAL);
                return;
            }
            store.markState(batchId, LogBatchStore.STATE_UPLOADING, 0, null);
            ctx.getSharedPreferences("logupload", Context.MODE_PRIVATE).edit()
                    .putLong("lastAttemptAt", System.currentTimeMillis()).apply();

            Response res = post(com.zai.adshield.Net.LOG_RELAY_URL, body);
            // ok 语义：HTTP 2xx 且 body ok=true（服务端确认族——错误码全部 !ok）
            boolean ok = res != null && res.http2xx && res.bodyOk;
            String code = res == null || res.json == null ? null : res.code;

            // v8.2.1：attemptsMade 含刚失败的这次（契约对齐——旧代码传 POST 前
            // 快照少计 1 次，实际第 7 次才终态；与异常路径/退避表同一口径）
            int attempt = attemptsOf(batch) + 1;
            int action = LogUploadPolicy.decide(ok, code, attempt);
            switch (action) {
                case LogUploadPolicy.ACK:
                    int issue = res != null && res.json != null ? res.json.optInt("issueNumber", 0) : 0;
                    store.markAcked(batchId, issue, System.currentTimeMillis());
                    s.acked++;
                    if (issue > 0) {
                        s.issueNumbers = s.issueNumbers.length() == 0
                                ? String.valueOf(issue) : s.issueNumbers + "," + issue;
                    }
                    return;
                case LogUploadPolicy.WAIT: {
                    long ra = res != null && res.json != null ? res.json.optLong("retryAfter", 0) : 0;
                    store.markState(batchId, LogBatchStore.STATE_PENDING,
                            System.currentTimeMillis() + LogUploadPolicy.waitDelayMs(ra),
                            "RATE_LIMITED (server window)");
                    s.waited++;
                    s.lastError = "rate-limited";
                    return;
                }
                case LogUploadPolicy.FAIL: {
                    // v8.2.1：终态细分落盘——服务端明确拒收 = terminal（绝不
                    // 复活）；网络/瞬时码耗尽 = exhausted（手动可复活）。
                    String failMsg = (code == null ? "network" : code) + ": "
                            + (res != null && res.json != null ? res.json.optString("error", "") : "");
                    boolean terminal = code != null && LogUploadPolicy.isTerminalRejection(code);
                    store.markFailed(batchId, failMsg,
                            terminal ? LogBatchStore.FAILKIND_TERMINAL : LogBatchStore.FAILKIND_EXHAUSTED);
                    s.failed++;
                    s.lastError = code == null ? "network" : code;
                    return;
                }
                default: { // RETRY——同 batchId 退避（服务端幂等兜底超时-已建场景）
                    store.markState(batchId, LogBatchStore.STATE_PENDING,
                            System.currentTimeMillis() + LogUploadPolicy.retryDelayMs(attempt),
                            "retry " + attempt + "/" + LogUploadPolicy.MAX_ATTEMPTS
                                    + (code == null ? ": network" : ": " + code));
                    s.retried++;
                    s.lastError = code == null ? "network" : code;
                }
            }
        } catch (Throwable t) {
            // 异常兜底也走退避（同 batchId——绝不因异常换号）；v8.2.1：耗尽
            // 判定与 decide 同口径（>= MAX_ATTEMPTS——含本次，第 6 次即终态）
            try {
                int attempt = attemptsOf(batch) + 1;
                if (attempt >= LogUploadPolicy.MAX_ATTEMPTS) {
                    store.markFailed(batchId, "retry-exhausted: " + t.getClass().getSimpleName(),
                            LogBatchStore.FAILKIND_EXHAUSTED);
                } else {
                    store.markState(batchId, LogBatchStore.STATE_PENDING,
                            System.currentTimeMillis() + LogUploadPolicy.retryDelayMs(attempt),
                            "network: " + t.getClass().getSimpleName());
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static int attemptsOf(JSONObject batch) {
        return batch.optInt("retryCount", 0);
    }

    /* ── 请求构造（schemaVersion 1——与中转服务规约一字不差；构造见 LogUploadPolicy）── */

    private String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    /* ── HTTP（HttpURLConnection——与 Net 同族；非 2xx 响应体也解析——服务端错误信封全字段可用）── */

    private static final class Response {
        final boolean http2xx;
        final JSONObject json; // 永远尝试解析（错误信封 code/retryAfter/error 同样要读）
        final boolean bodyOk;
        final String code;

        Response(int httpCode, JSONObject json) {
            this.http2xx = httpCode >= 200 && httpCode < 300;
            this.json = json;
            this.bodyOk = json != null && json.optBoolean("ok", false);
            this.code = json == null ? null : json.optString("code", null);
        }
    }

    private Response post(String urlStr, JSONObject body) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(CONNECT_TIMEOUT);
            c.setReadTimeout(READ_TIMEOUT);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("X-Skip-Ad-Client", CLIENT_HEADER);
            c.setDoOutput(true);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(payload.length);
            OutputStream os = c.getOutputStream();
            os.write(payload);
            os.close();
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String respText = readAll(in);
            JSONObject parsed = null;
            try {
                if (respText != null && respText.trim().startsWith("{")) {
                    parsed = new JSONObject(respText);
                }
            } catch (Throwable ignored) {
            }
            return new Response(code, parsed);
        } catch (Throwable t) {
            return null; // 网络异常——调用方走退避
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String readAll(InputStream in) {
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

    private String appVersion() {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "0.0.0";
        } catch (Throwable t) {
            return "0.0.0";
        }
    }

    private int versionCode() {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }
}
