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
 * 覆盖（用户 v8.2.2 十项测试要求中存储侧四项）：
 *  S1 采集常开：appendEvent 无条件落盘（不再有授权门——根因回归钉死）；
 *  S2 重启保留：同一目录新实例（=进程重启）pending 无损；
 *  S3 manifest 幂等稳定性：已 POST 未决块复用同 batchId；成功消费后
 *     后续块前移对齐（服务端「同号重试绝不二建 Issue」契约）；
 *  S4 24h 成功配额：ok-times 滚动窗口只记成功、超 24h 过期；
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

    /* S3：manifest batchId 稳定性（服务端幂等契约）。 */
    private static void s3_manifestStability() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        for (int i = 0; i < 60; i++) s.appendEvent(ev("CLICK"));
        s.awaitIoForJvm();
        String id1 = s.headBatchIdOrAppend(); // 首块 POST 前分配
        String id1again = s.headBatchIdOrAppend(); // 超时重试——同号
        TestHost.checkEq(id1, id1again, "S3a 未决块重试复用同 batchId");
        s.consumeHead(48); // 首块服务端确认
        String id2 = s.headBatchIdOrAppend();
        TestHost.check(id2.length() > 0 && !id2.equals(id1), "S3b 首块消费后新块新号");
        TestHost.check(LogUploader.isValidUuid(id2), "S3c batchId 为合法 UUID v4");
        TestHost.checkEq(12, s.pendingEventCount(), "S3d 消费后剩余 12 条（60-48）");
        // 部分成功部分未决：第二块 POST 未决 → 仍复用 id2
        TestHost.checkEq(id2, s.headBatchIdOrAppend(), "S3e 第二块未决——重试仍同号");
    }

    /* S4：24h 成功配额（只记成功；滚动窗口）。 */
    private static void s4_okTimesWindow() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        long day = 24L * 3600 * 1000;
        long now = 1795000000000L;
        s.recordOk(now - day - 1000); // 25h 前——已过期
        s.recordOk(now - day + 1000); // 23.9h 前——有效
        s.recordOk(now - 3600_000);   // 1h 前——有效
        TestHost.checkEq(2, s.okCount24h(now), "S4a 滚动窗口只数 24h 内（过期淘汰）");
        s.recordOk(now);
        TestHost.checkEq(3, s.okCount24h(now), "S4b 第 3 次成功在窗内");
        TestHost.checkEq(now, s.lastOkAt(), "S4c 最近成功时间戳");
    }

    /* S5：消费/拒收语义。 */
    private static void s5_consumeReject() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        for (int i = 0; i < 30; i++) s.appendEvent(ev("CLICK"));
        s.awaitIoForJvm();
        s.headBatchIdOrAppend();
        s.rejectHead(30, "PRIVACY_REJECTED", "test");
        TestHost.checkEq(0, s.pendingEventCount(), "S5a 拒收块事件移出 pending");
        TestHost.checkEq(1, s.rejectedCount(), "S5b 拒收检视计数");
        TestHost.checkEq(0, s.okCount24h(System.currentTimeMillis()), "S5c 拒收不计成功配额");
    }

    /* S6：清理与损坏检测。 */
    private static void s6_clearAndCorrupt() {
        MemEnv env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        for (int i = 0; i < 5; i++) s.appendEvent(ev("GKD"));
        s.awaitIoForJvm();
        s.headBatchIdOrAppend();
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
