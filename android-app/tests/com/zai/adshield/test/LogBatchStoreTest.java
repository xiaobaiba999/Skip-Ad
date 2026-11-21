package com.zai.adshield.test;

import com.zai.adshield.core.LogBatchStore;
import com.zai.adshield.core.LogEvents;
import com.zai.adshield.core.LogUploadPolicy;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v8.0.0 上传链路 · 批次存储套件（JVM——StoreEnv seam 注入临时目录，
 * 与 Android 生产实现共用全部状态机代码）。
 *
 * 覆盖面：
 *  · installInstanceId（首启 UUID v4 + 持久稳定 + 清数据自然重置）；
 *  · 采集门（未授权零磁盘写——授权前事件直接丢弃）；
 *  · 事件缓冲 append + 400 上限（关键安全事件最后丢）；
 *  · 封批（batchId 生成/幂等锚点/PENDING 落盘/48 分批上限/隐私拒绝 FAILED
 *    检视记录/空批不封/缓冲清空）；
 *  · dueBatches（到期排序/未到期不取/UPLOADING 中断恢复 PENDING）；
 *  · markState / markAcked（ACK 前绝不删——ACK 后批次删除 + acked-meta 记账
 *    + 24h 计数窗口）；
 *  · 重试不换号（失败重试同一 batchId——服务端幂等客户端半边）；
 *  · clearPending（用户主动清理）+ overview（状态行真数据）。
 */
public final class LogBatchStoreTest {

    /** JVM 测试环境（临时目录 + 内存偏好表）。 */
    static final class TmpEnv implements LogBatchStore.StoreEnv {
        final File dir;
        final Map<String, String> prefs = new HashMap<String, String>();

        TmpEnv() throws Exception {
            dir = Files.createTempDirectory("logbatch-test").toFile();
        }

        public String prefGet(String key) {
            return prefs.get(key);
        }

        public void prefPut(String key, String value) {
            prefs.put(key, value);
        }

        public File filesDir() {
            return dir;
        }
    }

    static TmpEnv env;
    static LogBatchStore store;

    public static void main(String[] args) throws Exception {
        env = new TmpEnv();
        store = LogBatchStore.forEnv(env);

        TestHost.suite("存储 B1 · installInstanceId");
        b1_installId();
        TestHost.suite("存储 B2 · 采集门（未授权零磁盘写）");
        b2_collectGate();
        TestHost.suite("存储 B3 · 缓冲 append 与 400 上限（关键最后丢）");
        b3_bufferCap();
        TestHost.suite("存储 B4 · 封批（batchId/落盘/清缓冲）");
        b4_seal();
        TestHost.suite("存储 B5 · 隐私拒绝 → FAILED 检视记录");
        b5_privacyReject();
        TestHost.suite("存储 B6 · 48 分批上限（余量留缓冲）");
        b6_slice();
        TestHost.suite("存储 B7 · dueBatches（到期/排序/中断恢复）");
        b7_due();
        TestHost.suite("存储 B8 · ACK 前不删 / ACK 后记账 / 24h 窗口");
        b8_ack();
        TestHost.suite("存储 B9 · 重试不换号（同 batchId 幂等重试）");
        b9_retrySameId();
        TestHost.suite("存储 B10 · clearPending + overview 真数据");
        b10_clearOverview();
        TestHost.done();
    }

    static void b1_installId() {
        String id = store.installInstanceId();
        TestHost.check(LogUploadPolicy.isValidUuid(id), "B1a 首启生成 UUID v4");
        TestHost.checkEq(id, store.installInstanceId(), "B1b 重复读取稳定（持久化）");
        // 清数据自然重置（全新环境 = 全新身份——不用任何设备敏感标识）
        try {
            TmpEnv env2 = new TmpEnv();
            LogBatchStore s2 = LogBatchStore.forEnv(env2);
            String id2 = s2.installInstanceId();
            TestHost.check(LogUploadPolicy.isValidUuid(id2) && !id2.equals(id), "B1c 全新环境新身份");
        } catch (Throwable t) {
            TestHost.check(false, "B1c 异常: " + t);
        }
    }

    static void b2_collectGate() {
        LogBatchStore.setCollectEnabled(false);
        append(store, LogEvents.adTask("com.a.b", "com.a.b.M", "SUCCESS", "VERIFIED_SUCCESS", 1, 1, 1));
        store.flushForJvm();
        TestHost.check(store.pendingEventCount() == 0, "B2a 未授权：appendEvent 直接丢弃（零磁盘写）");
        LogBatchStore.setCollectEnabled(true);
        append(store, LogEvents.adTask("com.a.c", "com.a.c.M", "SUCCESS", "VERIFIED_SUCCESS", 1, 1, 1));
        store.flushForJvm();
        TestHost.check(store.pendingEventCount() == 1, "B2b 授权后正常缓冲");
        store.clearPending(); // 清场
    }

    static void b3_bufferCap() throws Exception {
        LogBatchStore.setCollectEnabled(true);
        store.clearPending();
        // 3 条关键（CRASH）+ 450 条普通 → 上限 400，关键存活
        for (int i = 0; i < 3; i++) append(store, LogEvents.crash("java.lang.Error" + i));
        for (int i = 0; i < 450; i++) {
            append(store, LogEvents.adTask("com.p" + i, "com.p" + i + ".M", "SUCCESS",
                    "VERIFIED_SUCCESS", 1, 1, 1));
        }
        store.flushForJvm();
        TestHost.check(store.pendingEventCount() == 400, "B3a 缓冲钳在 400");
        JSONArray pending = readPending();
        int crashCount = 0;
        for (int i = 0; i < pending.length(); i++) {
            JSONObject e = pending.optJSONObject(i);
            if (e != null && "CRASH".equals(e.optString("type"))) crashCount++;
        }
        TestHost.check(crashCount == 3, "B3b 3 条 CRASH 全存活（容量裁剪最后丢关键）");
        store.clearPending();
    }

    static void b4_seal() {
        store.clearPending();
        for (int i = 0; i < 5; i++) {
            append(store, LogEvents.adTask("com.s" + i, "com.s" + i + ".M", "SUCCESS",
                    "VERIFIED_SUCCESS", i, i, i));
        }
        store.flushForJvm();
        String batchId = store.sealBatch("8.0.0", 80000);
        TestHost.check(batchId != null && LogUploadPolicy.isValidUuid(batchId), "B4a 封批生成 UUID v4 batchId");
        TestHost.check(store.pendingEventCount() == 0, "B4b 封批后缓冲清空");
        JSONObject rec = readBatch(batchId);
        TestHost.check(rec != null, "B4c 批次文件落盘");
        TestHost.checkEq("PENDING", rec.optString("state"), "B4d 初始 PENDING");
        TestHost.checkEq("8.0.0", rec.optString("appVersion"), "B4e appVersion 入批");
        TestHost.check(rec.optInt("eventCount") == 5, "B4f 事件计数");
        TestHost.check(rec.optJSONArray("events").length() == 5, "B4g 事件内容入批");
        TestHost.checkEq(store.installInstanceId(), rec.optString("installInstanceId"), "B4h installId 入批");
        // 空批不封（不凑次数）
        TestHost.check(store.sealBatch("8.0.0", 80000) == null, "B4i 空缓冲封批返回 null");
        // 第二批：新 batchId（批次唯一）
        append(store, LogEvents.adTask("com.s9", "com.s9.M", "FAILED", "MISS", 1, 1, 1));
        store.flushForJvm();
        String batchId2 = store.sealBatch("8.0.0", 80000);
        TestHost.check(batchId2 != null && !batchId2.equals(batchId), "B4j 新批次新 batchId（批次唯一）");
        store.clearPending();
    }

    static void b5_privacyReject() {
        store.clearPending();
        JSONObject dangerous = new JSONObject();
        dangerous.put("type", "AD_TASK");
        dangerous.put("screenshotUrl", "http://x"); // 危险键 → 整批拒绝
        append(store, dangerous);
        append(store, LogEvents.adTask("com.ok", "com.ok.M", "SUCCESS", "VERIFIED_SUCCESS", 1, 1, 1));
        store.flushForJvm();
        String r = store.sealBatch("8.0.0", 80000);
        TestHost.check(r == null, "B5a 隐私拒绝封批返回 null（绝不外发已知脏数据）");
        TestHost.check(store.pendingEventCount() == 0, "B5b 危险缓冲整体作废（不得留存）");
        // FAILED 检视记录在场（batch-*.json 中唯一一条 FAILED）
        List<JSONObject> all = store.dueBatches(Long.MAX_VALUE, 100); // FAILED 不进 due
        TestHost.check(all.isEmpty(), "B5c FAILED 记录不进 due（不上传）");
        JSONObject failedRec = findFailedRecord();
        TestHost.check(failedRec != null, "B5d FAILED 检视记录落盘");
        if (failedRec != null) {
            TestHost.check(failedRec.optString("lastError").contains("PRIVACY_REJECTED"),
                    "B5e lastError 带 PRIVACY_REJECTED 语义");
        }
        store.clearPending();
    }

    static void b6_slice() {
        store.clearPending();
        for (int i = 0; i < 50; i++) {
            append(store, LogEvents.adTask("com.d" + i, "com.d" + i + ".M", "SUCCESS",
                    "VERIFIED_SUCCESS", i, i, i));
        }
        store.flushForJvm();
        String bid = store.sealBatch("8.0.0", 80000);
        TestHost.check(bid != null, "B6a 超 48 事件封批成功");
        TestHost.check(readBatch(bid).optJSONArray("events").length() == 48,
                "B6b 单批恰 48（服务端契约上限）");
        TestHost.check(store.pendingEventCount() == 2, "B6c 余量 2 留缓冲（下轮再封——不截尾丢数据）");
        store.clearPending();
    }

    static void b7_due() {
        store.clearPending();
        append(store, LogEvents.adTask("com.e1", "com.e1.M", "SUCCESS", "VERIFIED_SUCCESS", 1, 1, 1));
        store.flushForJvm();
        String bid = store.sealBatch("8.0.0", 80000);
        // 未到期（nextRetryAt 未设 → 0 → 立即可到期）
        List<JSONObject> due = store.dueBatches(1000L, 10);
        TestHost.check(due.size() == 1 && bid.equals(due.get(0).optString("batchId")),
                "B7a 到期批次可取（nextRetryAt=0）");
        // 未来到期不取
        long future = System.currentTimeMillis() + 60_000L;
        store.markState(bid, LogBatchStore.STATE_PENDING, future, "waiting");
        due = store.dueBatches(System.currentTimeMillis(), 10);
        TestHost.check(due.isEmpty(), "B7b 未到期（RATE_LIMITED retryAfter）不取");
        // UPLOADING 中断恢复（进程死亡重启）：视为 PENDING 重排。
        // 语义口径：恢复保留原 nextRetryAt——先设到期值（epoch 1 = 永远过去），
        // 恢复后立即可重试；未来值（RATE_LIMITED 等待中被打断）不提前。
        store.markState(bid, LogBatchStore.STATE_PENDING, 1L, "ready");
        store.markState(bid, LogBatchStore.STATE_UPLOADING, 0, "posting");
        due = store.dueBatches(System.currentTimeMillis(), 10);
        TestHost.check(due.size() == 1, "B7c UPLOADING 中断恢复 → PENDING（ACK 前不删红线不破）");
        TestHost.check(due.get(0).optString("lastError").contains("recovered"), "B7d 恢复痕迹可见");
        TestHost.checkEq(LogBatchStore.STATE_PENDING, readBatch(bid).optString("state"), "B7e 恢复后落盘 PENDING");
        store.clearPending();
    }

    static void b8_ack() {
        store.clearPending();
        append(store, LogEvents.adTask("com.f1", "com.f1.M", "SUCCESS", "VERIFIED_SUCCESS", 1, 1, 1));
        store.flushForJvm();
        String bid = store.sealBatch("8.0.0", 80000);
        // 模拟 dispatcher：UPLOADING → ACK
        store.markState(bid, LogBatchStore.STATE_UPLOADING, 0, null);
        TestHost.check(readBatch(bid).optInt("retryCount") == 1, "B8a UPLOADING 记尝试数（1 次已 POST）");
        TestHost.check(readBatch(bid) != null, "B8b ACK 前批次在盘（未经确认绝不删本地日志）");
        long now = System.currentTimeMillis();
        store.markAcked(bid, 42, now);
        TestHost.check(readBatch(bid) == null, "B8c ACK 后批次文件删除");
        TestHost.check(store.ackedCount24h(now) == 1, "B8d acked-meta 记账（24h 内 1）");
        TestHost.check(store.ackedCount24h(now + 25L * 3600 * 1000) == 0, "B8e 24h 窗口外不计");
        // overview 真数据
        JSONObject ov = store.overview(now);
        TestHost.check(ov.optInt("acked24h") == 1, "B8f overview acked24h");
        TestHost.check(ov.optLong("lastAckedAt") == now, "B8g overview lastAckedAt");
        store.clearPending();
    }

    static void b9_retrySameId() {
        store.clearPending();
        append(store, LogEvents.adTask("com.g1", "com.g1.M", "SUCCESS", "VERIFIED_SUCCESS", 1, 1, 1));
        store.flushForJvm();
        String bid = store.sealBatch("8.0.0", 80000);
        // 模拟网络失败链：UPLOADING(尝试 1) → PENDING(退避) → UPLOADING(尝试 2) → PENDING …
        for (int attempt = 1; attempt <= 3; attempt++) {
            store.markState(bid, LogBatchStore.STATE_UPLOADING, 0, null);
            long next = System.currentTimeMillis() - 1; // 退避已到期
            store.markState(bid, LogBatchStore.STATE_PENDING, next, "retry " + attempt + "/6: network");
            List<JSONObject> due = store.dueBatches(System.currentTimeMillis(), 10);
            TestHost.check(due.size() == 1 && bid.equals(due.get(0).optString("batchId")),
                    "B9-" + attempt + " 第 " + attempt + " 次失败后重试：同一 batchId（绝不换号——服务端幂等兜底超时-已建场景）");
        }
        TestHost.check(readBatch(bid).optInt("retryCount") == 3, "B9d 尝试计数累计 3（1 首发 + 2 重试）");
        TestHost.check(readBatch(bid).optJSONArray("events").length() == 1, "B9e 事件内容原封不动（重试不重封包）");
        store.clearPending();
    }

    static void b10_clearOverview() {
        store.clearPending();
        for (int i = 0; i < 2; i++) {
            append(store, LogEvents.adTask("com.h" + i, "com.h" + i + ".M", "SUCCESS",
                    "VERIFIED_SUCCESS", 1, 1, 1));
            store.flushForJvm();
            store.sealBatch("8.0.0", 80000);
        }
        store.markState(readAnyBatchId(0), LogBatchStore.STATE_FAILED, 0, "PRIVACY_REJECTED: x");
        JSONObject ov = store.overview(System.currentTimeMillis());
        TestHost.check(ov.optInt("pending") == 1, "B10a overview 待传 1");
        TestHost.check(ov.optInt("failed") == 1, "B10b overview 失败检视 1");
        TestHost.check(String.valueOf(ov.optString("lastError")).contains("PRIVACY_REJECTED"),
                "B10c overview 最近失败原因");
        int cleared = store.clearPending();
        TestHost.check(cleared == 2, "B10d 清理 2 批（ACK 前批次一并删）");
        TestHost.check(store.pendingEventCount() == 0 && store.dueBatches(Long.MAX_VALUE, 10).isEmpty(),
                "B10e 清理后零待传");
    }

    /* ── 辅助 ── */

    static void append(LogBatchStore s, JSONObject ev) {
        s.appendEvent(ev);
    }

    static JSONArray readPending() {
        try {
            return new JSONArray(readText(new File(env.filesDir(), "log-upload/pending-events.json")));
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    static JSONObject readBatch(String batchId) {
        try {
            return new JSONObject(readText(new File(env.filesDir(), "log-upload/batch-" + batchId + ".json")));
        } catch (Throwable t) {
            return null;
        }
    }

    static String readAnyBatchId(int idx) {
        File[] fs = new File(env.filesDir(), "log-upload").listFiles();
        if (fs == null) return null;
        java.util.Arrays.sort(fs);
        int n = 0;
        for (File f : fs) {
            if (f.getName().startsWith("batch-") && f.getName().endsWith(".json")) {
                if (n++ == idx) return f.getName().substring("batch-".length(), f.getName().length() - ".json".length());
            }
        }
        return null;
    }

    static JSONObject findFailedRecord() {
        File[] fs = new File(env.filesDir(), "log-upload").listFiles();
        if (fs == null) return null;
        for (File f : fs) {
            if (!f.getName().startsWith("batch-") || !f.getName().endsWith(".json")) continue;
            try {
                JSONObject rec = new JSONObject(readText(f));
                if (LogBatchStore.STATE_FAILED.equals(rec.optString("state"))) return rec;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static String readText(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
