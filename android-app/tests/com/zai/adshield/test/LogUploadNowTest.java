package com.zai.adshield.test;

import com.zai.adshield.core.LogUploadDispatcher;

/**
 * v8.1.0 一键上传与 24h/3 自动上传窗口回归套件（真机反馈「导出代替上传」
 * 「要自动上传」两项需求）。
 * ─────────────────────────────────────────────────────────
 * 覆盖（Android 绑定类只测纯静态函数——Context 路径真机验证 NOT_RUN）：
 *  S1 封批决策 shouldSeal：自动路径阈值 20（不凑次数红线）/ 手动路径 >0
 *     即封（一键上传绝不空转——旧版 <20 条静默空转是真机反馈的一部分）；
 *  S2 自动上传资格 autoCheckEligible：8h 节拍 + 本地 24h ACK <3 前置闸
 *     （服务端滑窗仍为权威——双保险）；3 次满额 → 自动路径禁行；
 *  S3 Summary 契约：toJson() 12 键完整（MainActivity evaluateJavascript
 *     回调 → app.js JSON.parse 渲染链路的数据契约）；
 *  S4 手动/自动分离红线：shouldSeal(true) 在 1 条事件即封（手动不受
 *     自动阈值限制）；autoCheckEligible 不作用于手动路径（编译期保证——
 *     requestUploadNow/submitNowBlocking 不调用它，此处钉死语义供回归）。
 */
public class LogUploadNowTest {

    public static void main(String[] args) {
        TestHost.suite("LogUploadNowTest");

        s1_shouldSeal();
        s2_autoCheckEligible();
        s3_summaryContract();
        s4_manualAutoSeparation();

        TestHost.done();
    }

    /* S1：封批决策边界（自动 20 阈值 / 手动 >0 即封 / 空批永不封）。 */
    private static void s1_shouldSeal() {
        TestHost.check(!LogUploadDispatcher.shouldSeal(0, false), "S1a 自动路径 0 条不封（空批红线）");
        TestHost.check(!LogUploadDispatcher.shouldSeal(19, false), "S1b 自动路径 19 条不封（阈值 20 不凑次数）");
        TestHost.check(LogUploadDispatcher.shouldSeal(20, false), "S1c 自动路径 20 条即封（阈值语义不变）");
        TestHost.check(LogUploadDispatcher.shouldSeal(400, false), "S1d 自动路径 400 条即封（缓冲上限场景）");
        TestHost.check(!LogUploadDispatcher.shouldSeal(0, true), "S1e 手动路径 0 条不封（空批红线对手动同样生效）");
        TestHost.check(LogUploadDispatcher.shouldSeal(1, true), "S1f 手动路径 1 条即封（一键上传绝不空转——v8.1.0 修复）");
        TestHost.check(LogUploadDispatcher.shouldSeal(19, true), "S1g 手动路径 19 条即封（轻用户点按钮必有反馈）");
    }

    /* S2：自动上传资格——间隔门 + 本地 24h/3 前置闸（服务端滑窗权威的第二
     * 道本地闸；省电省流量；被拒批次由下一检查点吸收，绝不丢失）。 */
    private static void s2_autoCheckEligible() {
        long h8 = 8L * 3600 * 1000;
        long now = 1795000000000L; // 任意固定基准
        TestHost.check(LogUploadDispatcher.autoCheckEligible(now - h8, now, h8, 0),
                "S2a 间隔 8h 到 + 0 次已传 → 允许（基线）");
        TestHost.check(LogUploadDispatcher.autoCheckEligible(now - h8 - 1, now, h8, 0),
                "S2b 间隔超 8h + 0 次 → 允许（顺延后补传——Doze 语义）");
        TestHost.check(!LogUploadDispatcher.autoCheckEligible(now - h8 + 1, now, h8, 0),
                "S2c 间隔不足 8h → 拒（绝不无限上传）");
        TestHost.check(!LogUploadDispatcher.autoCheckEligible(now - h8, now, h8, 3),
                "S2d 24h 已 ACK 3 次 → 自动路径禁行（24h/3 硬上限——本地前置闸）");
        TestHost.check(LogUploadDispatcher.autoCheckEligible(now - h8, now, h8, 2),
                "S2e 24h 已 ACK 2 次 → 仍允许（第 3 次机会）");
        TestHost.check(LogUploadDispatcher.autoCheckEligible(0, now, h8, 0),
                "S2f 从未上传过（lastAttemptAt=0）→ 允许（首传）");
        TestHost.check(!LogUploadDispatcher.autoCheckEligible(now - h8, now, h8, 99),
                "S2g 异常大计数 → 拒（防御：宁可少传）");
    }

    /* S3：Summary 数据契约——12 键完整 + 值类型正确（app.js 渲染依赖）。 */
    private static void s3_summaryContract() {
        LogUploadDispatcher.Summary s = new LogUploadDispatcher.Summary();
        s.sealed = 1;
        s.uploaded = 1;
        s.acked = 1;
        s.waited = 0;
        s.failed = 0;
        s.retried = 0;
        s.pending = 0;
        s.pendingEvents = 0;
        s.acked24h = 1;
        s.busy = false;
        s.issueNumbers = "123";
        s.lastError = "";
        String json = s.toJson();
        TestHost.check(json.contains("\"sealed\":1"), "S3a sealed 键");
        TestHost.check(json.contains("\"uploaded\":1"), "S3b uploaded 键");
        TestHost.check(json.contains("\"acked\":1"), "S3c acked 键");
        TestHost.check(json.contains("\"waited\":0"), "S3d waited 键");
        TestHost.check(json.contains("\"failed\":0"), "S3e failed 键");
        TestHost.check(json.contains("\"retried\":0"), "S3f retried 键");
        TestHost.check(json.contains("\"pending\":0"), "S3g pending 键");
        TestHost.check(json.contains("\"pendingEvents\":0"), "S3h pendingEvents 键");
        TestHost.check(json.contains("\"acked24h\":1"), "S3i acked24h 键");
        TestHost.check(json.contains("\"busy\":false"), "S3j busy 键");
        TestHost.check(json.contains("\"issueNumbers\":\"123\""), "S3k issueNumbers 键（字符串）");
        TestHost.check(json.contains("\"lastError\":\"\""), "S3l lastError 键");
        // JSON 可解析性（org.json shim 与生产 org.json 行为一致）
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            TestHost.checkEq(1, o.optInt("acked", -1), "S3m toJson 可被 org.json 解析（回调链路契约）");
            TestHost.checkEq("123", o.optString("issueNumbers", ""), "S3n issueNumbers 字符串值完整");
        } catch (Throwable t) {
            TestHost.check(false, "S3m toJson 解析异常: " + t);
        }
        // 空值 Summary（busy 场景）不抛异常
        try {
            LogUploadDispatcher.Summary b = new LogUploadDispatcher.Summary();
            b.busy = true;
            b.lastError = "relay-offline";
            TestHost.check(b.toJson().contains("\"busy\":true"), "S3o busy Summary 序列化不抛异常");
        } catch (Throwable t) {
            TestHost.check(false, "S3o busy Summary 异常: " + t);
        }
    }

    /* S4：手动/自动额度分离红线（纪律要求：手动上传不受 24h/3 自动额度限制）。
     * 手动路径（submitNowBlocking）不调用 autoCheckEligible——由编译结构
     * 保证；此处钉死语义：autoCheckEligible 的任何拒收组合都不影响
     * shouldSeal(true)（手动封批）决策。 */
    private static void s4_manualAutoSeparation() {
        long h8 = 8L * 3600 * 1000;
        long now = 1795000000000L;
        // 自动路径满额 3 次（拒收自动）
        TestHost.check(!LogUploadDispatcher.autoCheckEligible(now - h8, now, h8, 3),
                "S4a 自动满额 3 次 → 自动路径拒");
        // 同一状态下手动一键上传照常（1 条即封）
        TestHost.check(LogUploadDispatcher.shouldSeal(1, true),
                "S4b 自动满额状态下手动力封批照常（额度分离红线）");
        // 自动间隔不足（拒收自动）不影响手动
        TestHost.check(!LogUploadDispatcher.autoCheckEligible(now, now, h8, 0),
                "S4c 自动间隔不足 → 自动路径拒");
        TestHost.check(LogUploadDispatcher.shouldSeal(5, true),
                "S4d 间隔不足状态下手动力封批照常（额度分离红线）");
    }
}
