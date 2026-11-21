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
 *    节拍 + 授权门 + RELAY_URL 门）；
 *  · requestUploadNow()：用户手动「立即提交」（同服务端限额，不绕过 24h/3）；
 *  · 网络恢复/批次到期由心跳检查点吸收（不另设高频闹钟）。
 */
public final class LogUploadDispatcher {

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

    /** 自动检查点（KeepAliveReceiver 心跳顺带调用；门卫：地址+最小间隔——授权门在上游）。 */
    public void requestAutoCheck(long minIntervalMs) {
        try {
            if (com.zai.adshield.Net.LOG_RELAY_URL.length() == 0) return; // 中转未上线：结构性关闭
            long last = ctx.getSharedPreferences("logupload", Context.MODE_PRIVATE).getLong("lastAttemptAt", 0L);
            long now = System.currentTimeMillis();
            if (now - last < minIntervalMs) return;
            dispatch();
        } catch (Throwable ignored) {
        }
    }

    /** 手动「立即提交」（用户主动操作；同样走服务端限额与幂等——不绕 24h/3）。 */
    public void requestUploadNow() {
        try {
            if (com.zai.adshield.Net.LOG_RELAY_URL.length() == 0) return; // 服务未上线：无行为
            dispatch();
        } catch (Throwable ignored) {
        }
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
        try {
            LogBatchStore store = LogBatchStore.get(ctx);
            // STATS 快照先行（六态 + GKD 八格——纯静态计数器读取，开机周期累计口径）
            try {
                store.appendEvent(com.zai.adshield.core.LogEvents.stats(
                        com.zai.adshield.SubscriptionManager.subStatV2Snapshot(),
                        snapshot6(0), snapshot6(2), snapshot6(5)));
            } catch (Throwable ignored) {
            }
            // 心跳检查点封批（阈值 20；单批 ≤48——堆积分多批，guard 防御环；
            // 空批/隐私拒收即止——绝不凑次数）
            int sealGuard = 0;
            while (store.pendingEventCount() >= SEAL_THRESHOLD && sealGuard++ < 24) {
                if (store.sealBatch(appVersion(), versionCode()) == null) break;
            }
            long now = System.currentTimeMillis();
            List<JSONObject> due = store.dueBatches(now, MAX_BATCHES_PER_RUN);
            for (JSONObject batch : due) {
                uploadOne(store, batch);
            }
        } catch (Throwable ignored) {
        }
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

    private void uploadOne(LogBatchStore store, JSONObject batch) {
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
                store.markState(batchId, LogBatchStore.STATE_FAILED, 0, "client-defense: invalid ids");
                return;
            }
            store.markState(batchId, LogBatchStore.STATE_UPLOADING, 0, null);
            ctx.getSharedPreferences("logupload", Context.MODE_PRIVATE).edit()
                    .putLong("lastAttemptAt", System.currentTimeMillis()).apply();

            Response res = post(com.zai.adshield.Net.LOG_RELAY_URL, body);
            // ok 语义：HTTP 2xx 且 body ok=true（服务端确认族——错误码全部 !ok）
            boolean ok = res != null && res.http2xx && res.bodyOk;
            String code = res == null || res.json == null ? null : res.code;

            int action = LogUploadPolicy.decide(ok, code, attemptsOf(batch));
            switch (action) {
                case LogUploadPolicy.ACK:
                    store.markAcked(batchId,
                            res != null && res.json != null ? res.json.optInt("issueNumber", 0) : 0,
                            System.currentTimeMillis());
                    return;
                case LogUploadPolicy.WAIT: {
                    long ra = res != null && res.json != null ? res.json.optLong("retryAfter", 0) : 0;
                    store.markState(batchId, LogBatchStore.STATE_PENDING,
                            System.currentTimeMillis() + LogUploadPolicy.waitDelayMs(ra),
                            "RATE_LIMITED (server window)");
                    return;
                }
                case LogUploadPolicy.FAIL:
                    store.markState(batchId, LogBatchStore.STATE_FAILED, 0,
                            (code == null ? "network" : code) + ": "
                                    + (res != null && res.json != null ? res.json.optString("error", "") : ""));
                    return;
                default: { // RETRY——同 batchId 退避（服务端幂等兜底超时-已建场景）
                    int attempts = attemptsOf(batch) + 1; // 本次 POST 已计入 markState(UPLOADING)
                    store.markState(batchId, LogBatchStore.STATE_PENDING,
                            System.currentTimeMillis() + LogUploadPolicy.retryDelayMs(attempts),
                            "retry " + attempts + "/" + LogUploadPolicy.MAX_ATTEMPTS
                                    + (code == null ? ": network" : ": " + code));
                }
            }
        } catch (Throwable t) {
            // 异常兜底也走退避（同 batchId——绝不因异常换号）
            try {
                int attempts = attemptsOf(batch) + 1;
                if (attempts > LogUploadPolicy.MAX_ATTEMPTS) {
                    store.markState(batchId, LogBatchStore.STATE_FAILED, 0,
                            "retry-exhausted: " + t.getClass().getSimpleName());
                } else {
                    store.markState(batchId, LogBatchStore.STATE_PENDING,
                            System.currentTimeMillis() + LogUploadPolicy.retryDelayMs(attempts),
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
