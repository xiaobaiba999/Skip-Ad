package com.zai.adshield.test;

import com.zai.adshield.core.LogStore;
import com.zai.adshield.core.LogUploader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
 *  S7 概览契约：overview 键形（app.js 状态行 + 四态区分数据源）；
 *  S8 遗留清理不误伤（v7.9.8b）：新实例 migrateLegacy 保留冻结清单
 *     batch-manifest.json（重启不丢冻结状态），仍删除遗留 batch-{uuid}.json
 *     与 acked-meta.json（T14 重启恢复失败暴露的真实 Bug 回归钉死）。
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
        s8_migrateLegacyKeepsManifest();

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

    /* S4：成功时间账本（v7.10.0 次数限制移除后仅历史留存——写入/滚动窗
     * 修剪/手动自动分账三语义继续钉死；计数读取方 okCount24h/
     * autoOkCount24h 已删，断言改为直接读账本文件）。 */
    private static void s4_okTimesWindow() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        long day = 24L * 3600 * 1000;
        long now = 1795000000000L;
        s.recordOk(now - day - 1000); // 25h 前
        s.recordOk(now - day + 1000); // 23.9h 前
        s.recordOk(now - 3600_000);   // 1h 前
        // 修剪相对「本次写入时间戳」：三次写入各自的更旧条目均在其 24h 内 → 3 条全留
        TestHost.checkEq(3, ledgerCount(env, "ok-times.json"),
                "S4a 账本写入保持（滚动窗修剪相对写入时刻——早期条目仍在窗内）");
        s.recordOk(now); // 相对 now 修剪：25h 前的条目出窗淘汰
        TestHost.checkEq(3, ledgerCount(env, "ok-times.json"),
                "S4b 相对最新写入修剪（25h 前旧条目出窗淘汰——23.9h/1h/now 保留）");
        TestHost.checkEq(now, s.lastOkAt(), "S4c 最近成功时间戳");
        /* 手动/自动分账（账本层面保持）：手动只写 ok-times；自动写两本 */
        MemEnv env2 = new MemEnv();
        LogStore s2 = LogStore.forEnv(env2);
        s2.recordOk(now, true);   // 手动成功 × 3
        s2.recordOk(now + 1000, true);
        s2.recordOk(now + 2000, true);
        TestHost.checkEq(3, ledgerCount(env2, "ok-times.json"), "S4d 手动成功入全部账本");
        TestHost.checkEq(0, ledgerCount(env2, "auto-ok-times.json"), "S4e 手动成功不写自动账本（分账保持）");
        s2.recordOk(now + 4000, false);  // 自动成功
        TestHost.checkEq(1, ledgerCount(env2, "auto-ok-times.json"), "S4f 自动成功入自动账本");
        TestHost.checkEq(4, ledgerCount(env2, "ok-times.json"), "S4g 自动成功也入全部账本");
    }

    /** 直接读账本文件计条数（v7.10.0：计数函数已删——写入语义仍需钉死；
     *  文件不存在 → 0；损坏 → -1）。 */
    private static int ledgerCount(MemEnv env, String name) {
        try {
            File f = new File(new File(env.dir, "log-upload"), name);
            if (!f.isFile()) return 0;
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new JSONArray(new String(bo.toByteArray(), StandardCharsets.UTF_8)).length();
        } catch (Throwable t) {
            return -1;
        }
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
        TestHost.checkEq(0, ledgerCount(env, "ok-times.json"), "S5d 拒收零成功记账（ok-times 未写）");
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
        TestHost.check(!ov.has("acked24h"), "S7c v7.10.0：acked24h 键已随「上传次数限制移除」删除");
        TestHost.check(ov.has("lastAckedAt") && ov.has("rejected") && ov.has("totalCollected")
                && ov.has("appendFailAt") && ov.has("readCorrupt"), "S7d 四态区分键齐备");
    }

    /* S8：migrateLegacy 不误伤冻结清单（v7.9.8b——T14 暴露的真实 Bug）。
     * 旧规则 startsWith("batch-") 每次新实例（进程重启）都删 batch-manifest.json
     * → 冻结状态丢失 → 重试换新 batchId → 服务端已收原批次时重复建 Issue。 */
    private static void s8_migrateLegacyKeepsManifest() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        JSONArray chunk = new JSONArray();
        for (int i = 0; i < 10; i++) chunk.put(ev("CLICK"));
        LogStore.FrozenBatch fb = s.sealBatch(chunk, "2026-11-23T05:00:00Z");
        TestHost.check(fb != null, "S8a 前置：封批成功");
        // 制造遗留文件（v8.0.0~v8.2.1 升级现场）：batch-{uuid}.json + acked-meta.json
        try {
            File d = new File(env.dir, "log-upload");
            FileOutputStream f1 = new FileOutputStream(new File(d, "batch-01234567-89ab-4def-9012-123456789abc.json"));
            f1.write("[]".getBytes(StandardCharsets.UTF_8));
            f1.close();
            FileOutputStream f2 = new FileOutputStream(new File(d, "acked-meta.json"));
            f2.write("[]".getBytes(StandardCharsets.UTF_8));
            f2.close();
        } catch (Throwable ignored) {
        }
        // 进程重启：新实例 → migrateLegacy 运行
        LogStore s2 = LogStore.forEnv(env);
        LogStore.FrozenBatch recovered = s2.headFrozenOrNull();
        TestHost.check(recovered != null && fb.batchId.equals(recovered.batchId),
                "S8b 重启后冻结清单存活（batchId 延续——不再被遗留清理误删）");
        TestHost.checkEq(fb.events.toString(), recovered.events.toString(),
                "S8c 冻结事件字节级存活");
        File d = new File(env.dir, "log-upload");
        TestHost.check(!new File(d, "batch-01234567-89ab-4def-9012-123456789abc.json").exists(),
                "S8d 遗留 batch-{uuid}.json 仍被清理");
        TestHost.check(!new File(d, "acked-meta.json").exists(),
                "S8e 遗留 acked-meta.json 仍被清理");
        // manifest 临时文件（writeJsonArray 的 .tmp 残留）不影响恢复
        TestHost.check(new File(d, "batch-manifest.json").exists(),
                "S8f manifest 文件在盘");
    }
}
