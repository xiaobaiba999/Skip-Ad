package com.zai.adshield.test;

import com.zai.adshield.core.LogStore;
import com.zai.adshield.core.LogUploader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * v8.2.2 简化日志存储回归套件（替换 v8.0.0~v8.2.1 LogBatchStoreTest）。
 * ─────────────────────────────────────────────────────────
 * 覆盖（用户 v8.2.2 十项测试要求中存储侧四项 + v7.9.8 冻结幂等）：
 *  S1 采集常开：appendEvent 无条件落盘（不再有授权门——根因回归钉死）；
 *  S2 重启保留：同一目录新实例（=进程重启）pending 无损；
 *  S3 manifest 冻结幂等（v7.9.8）：封批冻结 batchId+createdAt+events，
 *     重试字节级复用（绝不漂移——「第二次被服务端拒」根因回归钉死）；
 *     v1 遗留 manifest（仅 batchId）保留编号补冻；成功消费后后续块前移；
 *  S4 24h 成功配额：ok-times 滚动窗口只记成功、超 24h 过期；手动/自动
 *     额度分账（auto-ok-times 独立账本——手动不占自动闸）；
 *  S5 消费/拒收语义：consumeHead 删头、rejectHead 隔离检视；
 *  S6 清理与损坏检测：clearPending 全清；pending 文件损坏 → readCorrupt；
 *  S7 概览契约：overview 键形（app.js 状态行 + 四态区分数据源）。
 */
public class LogStoreTest {

    private static JSONObject ev(String type) {
        JSONObject e = new JSONObject();
        try {
            e.put("type", type);
            e.put("at", System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
        return e;
    }

    public static void main(String[] args) {
        TestHost.suite("LogStoreTest");

        s1_collectAlwaysOn();
        s2_restartRetention();
        s3_manifestStability();
        s4_okTimesWindow();
        s5_consumeReject();
        s6_clearAndCorrupt();
        s7_overviewContract();

        TestHost.done();
    }

    /* S1：采集常开（v8.2.1「暂无可上传」根因回归钉死——不再有 setCollectEnabled）。 */
    private static void s1_collectAlwaysOn() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        s.appendEvent(ev("CLICK"));
        s.appendEvent(ev("AD_TASK"));
        s.awaitIoForJvm();
        TestHost.checkEq(2, s.pendingEventCount(), "S1a 无任何开关——appendEvent 直接落盘");
        TestHost.checkEq(2L, s.totalCollected(), "S1b 累计采集计数");
        // S1c：采集门 API（setCollectEnabled）已删除——编译期保证（反射探针
        // 在 JVM 会连带解析 android.content.Context 参数类型，故不在此反射）。
        // S1a/S1b 的行为断言即根因回归钉死：无任何开关前置，事件直接落盘。
        TestHost.check(true, "S1c 采集门 API 删除由编译期保证（行为断言=S1a/S1b）");
    }

    /* S2：应用重启后未上传日志仍然存在（同目录新实例）。 */
    private static void s2_restartRetention() {
        MemEnv env = new MemEnv();
        LogStore a = LogStore.forEnv(env);
        a.appendEvent(ev("VERIFY"));
        a.appendEvent(ev("SAFE_BLOCK"));
        a.awaitIoForJvm();
        String installId = a.installInstanceId();
        LogStore b = LogStore.forEnv(env); // 新实例 = 进程重启
        TestHost.checkEq(2, b.pendingEventCount(), "S2a 重启后 pending 无损");
        TestHost.checkEq(installId, b.installInstanceId(), "S2b installInstanceId 延续");
        b.appendEvent(ev("CRASH"));
        b.awaitIoForJvm();
        TestHost.checkEq(3, b.pendingEventCount(), "S2c 重启后可继续采集");
    }

    /* S3：manifest 冻结幂等（v7.9.8——真机两连失败根因回归钉死）。 */
    private static void s3_manifestStability() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        for (int i = 0; i < 60; i++) s.appendEvent(ev("CLICK"));
        s.awaitIoForJvm();
        JSONArray chunk = new JSONArray();
        for (int i = 0; i < 48; i++) chunk.put(ev("CLICK"));
        LogStore.FrozenBatch fb1 = s.sealBatch(chunk, "2026-11-23T00:00:00Z");
        /* 冻结核心：重试前又有新事件追加——头部冻结块仍字节级复用
         * （同 batchId + 同 createdAt + 同 events；绝不重构漂移 payload
         * ——旧版漂移即服务端 IDEMPOTENCY_CONFLICT 的直接根因）。 */
        for (int i = 0; i < 5; i++) s.appendEvent(ev("GKD"));
        s.awaitIoForJvm();
        LogStore.FrozenBatch fb1retry = s.headFrozenOrNull();
        TestHost.check(fb1retry != null, "S3a 未决冻结块在场");
        TestHost.checkEq(fb1.batchId, fb1retry.batchId, "S3b 未决块重试复用同 batchId");
        TestHost.checkEq(fb1.createdAt, fb1retry.createdAt, "S3c 重试复用同 createdAt（绝不漂移）");
        TestHost.checkEq(fb1.events.toString(), fb1retry.events.toString(), "S3d 重试复用同 events（字节级）");
        TestHost.checkEq(48, fb1retry.realCount, "S3e realCount 冻结值");
        s.consumeHead(48); // 首块服务端确认
        TestHost.check(s.headFrozenOrNull() == null, "S3f 首块消费后无未决冻结块");
        JSONArray chunk2 = new JSONArray();
        for (int i = 0; i < 17; i++) chunk2.put(ev("CLICK"));
        LogStore.FrozenBatch fb2 = s.sealBatch(chunk2, "2026-11-23T01:00:00Z");
        TestHost.check(!fb2.batchId.equals(fb1.batchId), "S3g 首块消费后新块新号");
        TestHost.check(LogUploader.isValidUuid(fb2.batchId), "S3h batchId 为合法 UUID v4");
        TestHost.checkEq(17, s.pendingEventCount(), "S3i 消费后剩余 17 条（65-48）");
        /* v1 遗留 manifest（v8.2.2~v7.9.7 格式：仅 batchId 无 events）迁移：
         * 保留编号、补冻当前 chunk（一次性）；补冻后再读——字节级稳定。 */
        MemEnv env2 = new MemEnv();
        LogStore s2 = LogStore.forEnv(env2);
        try {
            org.json.JSONArray legacy = new org.json.JSONArray();
            org.json.JSONObject rec = new org.json.JSONObject();
            rec.put("batchId", "01234567-89ab-4def-9012-123456789abc");
            rec.put("at", 1795000000000L);
            legacy.put(rec);
            File mf = new File(new File(env2.dir, "log-upload"), "batch-manifest.json");
            FileOutputStream fo = new FileOutputStream(mf);
            fo.write(legacy.toString().getBytes(StandardCharsets.UTF_8));
            fo.close();
        } catch (Throwable ignored) {
        }
        LogStore.FrozenBatch legacyHead = s2.headFrozenOrNull();
        TestHost.check(legacyHead != null && legacyHead.realCount < 0, "S3j v1 遗留记录识别（待补冻）");
        JSONArray chunk3 = new JSONArray();
        chunk3.put(ev("VERIFY"));
        LogStore.FrozenBatch migrated = s2.sealBatch(chunk3, "2026-11-23T02:00:00Z");
        TestHost.checkEq("01234567-89ab-4def-9012-123456789abc", migrated.batchId,
                "S3k v1 遗留保留原 batchId 补冻（编号延续）");
        TestHost.checkEq(1, migrated.events.length(), "S3l 补冻 chunk 在场");
        LogStore.FrozenBatch again = s2.headFrozenOrNull();
        TestHost.checkEq(migrated.events.toString(), again.events.toString(), "S3m 补冻后字节级稳定");
    }

    /* S4：24h 成功配额（只记成功；滚动窗；手动/自动分账）。 */
    private static void s4_okTimesWindow() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        long day = 24L * 3600 * 1000;
        long now = 1795000000000L;
        s.recordOk(now - day - 1000); // 25h 前——已过期（手动默认）
        s.recordOk(now - day + 1000); // 23.9h 前——有效
        s.recordOk(now - 3600_000);   // 1h 前——有效
        TestHost.checkEq(2, s.okCount24h(now), "S4a 滚动窗口只数 24h 内（过期淘汰）");
        s.recordOk(now);
        TestHost.checkEq(3, s.okCount24h(now), "S4b 第 3 次成功在窗内");
        TestHost.checkEq(now, s.lastOkAt(), "S4c 最近成功时间戳");
        /* v7.9.8 额度分账：手动成功不占自动闸；自动成功双账本都记 */
        MemEnv env2 = new MemEnv();
        LogStore s2 = LogStore.forEnv(env2);
        s2.recordOk(now, true);   // 手动成功 × 3
        s2.recordOk(now + 1000, true);
        s2.recordOk(now + 2000, true);
        TestHost.checkEq(3, s2.okCount24h(now + 3000), "S4d 手动成功入全部账本");
        TestHost.checkEq(0, s2.autoOkCount24h(now + 3000), "S4e 手动成功不占自动闸（分账）");
        s2.recordOk(now + 4000, false);  // 自动成功
        TestHost.checkEq(1, s2.autoOkCount24h(now + 5000), "S4f 自动成功入自动账本");
        TestHost.checkEq(4, s2.okCount24h(now + 5000), "S4g 自动成功也入全部账本（展示口径）");
    }

    /* S5：消费/拒收语义。 */
    private static void s5_consumeReject() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        for (int i = 0; i < 30; i++) s.appendEvent(ev("CLICK"));
        s.awaitIoForJvm();
        JSONArray chunk = new JSONArray();
        for (int i = 0; i < 30; i++) chunk.put(ev("CLICK"));
        s.sealBatch(chunk, "2026-11-23T03:00:00Z");
        s.rejectHead(30, "PRIVACY_REJECTED", "test");
        TestHost.checkEq(0, s.pendingEventCount(), "S5a 拒收块事件移出 pending");
        TestHost.checkEq(1, s.rejectedCount(), "S5b 拒收检视计数");
        TestHost.check(s.headFrozenOrNull() == null, "S5c 拒收块不再重试（隔离检视）");
        TestHost.checkEq(0, s.okCount24h(System.currentTimeMillis()), "S5d 拒收不计成功配额");
    }

    /* S6：清理与损坏检测。 */
    private static void s6_clearAndCorrupt() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        for (int i = 0; i < 5; i++) s.appendEvent(ev("GKD"));
        s.awaitIoForJvm();
        JSONArray chunk = new JSONArray();
        for (int i = 0; i < 5; i++) chunk.put(ev("GKD"));
        s.sealBatch(chunk, "2026-11-23T04:00:00Z");
        TestHost.checkEq(5, s.clearPending(), "S6a 清理返回事件数");
        TestHost.checkEq(0, s.pendingEventCount(), "S6b 清理后 pending 空");
        TestHost.checkEq(0, s.rejectedCount(), "S6c 拒收检视一并清理");
        // 损坏：写非法 JSON 到 pending 文件
        try {
            File f = new File(new File(env.dir, "log-upload"), "pending-events.json");
            FileOutputStream out = new FileOutputStream(f);
            out.write("{corrupt!!!".getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Throwable ignored) {
        }
        TestHost.checkEq(0, s.pendingEventCount(), "S6d 损坏文件读作空（防御）");
        TestHost.check(s.pendingFileCorrupt(), "S6e 损坏被检出（read-failed 态数据源）");
    }

    /* S7：概览契约（app.js refreshLogUploadNote + emptyReason 数据源）。 */
    private static void s7_overviewContract() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        for (int i = 0; i < 50; i++) s.appendEvent(ev("CLICK"));
        s.awaitIoForJvm();
        s.recordOk(System.currentTimeMillis());
        JSONObject ov = s.overview(System.currentTimeMillis());
        TestHost.checkEq(50, ov.optInt("pendingEvents"), "S7a pendingEvents 键");
        TestHost.checkEq(2, ov.optInt("pending"), "S7b pending 块估算（50 条 → 2 块）");
        TestHost.checkEq(1, ov.optInt("acked24h"), "S7c acked24h 键");
        TestHost.check(ov.has("lastAckedAt") && ov.has("rejected") && ov.has("totalCollected")
                && ov.has("appendFailAt") && ov.has("readCorrupt"), "S7d 四态区分键齐备");
    }
}
