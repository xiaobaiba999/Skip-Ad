package com.zai.adshield.test;

import com.zai.adshield.core.SubPrune;

/**
 * 订阅规则瘦身策略单元测试（v7.15.5 · 用户指令「命中很少的订阅规则去除掉」）。
 * ─────────────────────────────────────────────────────────────
 * 策略语义：市场源某应用规则评估 ≥300 次（跨 ≥3 天）仍【零命中】→ 停用；
 * 命中过哪怕 1 次一律保留（广告低频 ≠ 规则无效——「命中很少」的最安全
 * 边界是零）；停用满 30 天自动复评（订阅日更的规则有二次机会）。
 * 断言规划（check 总数 22）：
 *  S1 零命中停用真值表（7）/ S2 命中豁免（4）
 *  S3 观察窗与评估量双门（6）/ S4 复评到期（5）。
 * 与 core 包同规约（纯 Java 零 Android 依赖，ECJ -1.8 编译 JVM 运行）。
 */
public final class SubPruneTest {

    public static void main(String[] args) {
        final long DAY = 24L * 60 * 60 * 1000L;
        final long T0 = 1_700_000_000_000L; // 任意锚点

        /* ════ S1 零命中停用真值表（7 项）════ */
        TestHost.suite("SubPrune · S1 零命中停用真值表");
        TestHost.check(SubPrune.shouldPrune(300, 0, T0, T0 + 3 * DAY),
                "评估 300 次 · 零命中 · 满 3 天 → 停用（边界正好达标）");
        TestHost.check(SubPrune.shouldPrune(1000, 0, T0, T0 + 30 * DAY),
                "评估 1000 次 · 零命中 · 满 30 天 → 停用（长期零命中）");
        TestHost.check(!SubPrune.shouldPrune(299, 0, T0, T0 + 3 * DAY),
                "评估 299 次（差一次）→ 不停（评估量门）");
        TestHost.check(!SubPrune.shouldPrune(300, 0, T0, T0 + 3 * DAY - 1),
                "差 1ms 满 3 天 → 不停（观察窗门）");
        TestHost.check(!SubPrune.shouldPrune(0, 0, T0, T0 + 100 * DAY),
                "零评估（不用该应用）→ 不停（无结论不惩罚）");
        TestHost.check(!SubPrune.shouldPrune(300, 0, 0, T0),
                "firstAt=0（无记录）→ 不停（哨兵）");
        TestHost.check(SubPrune.MIN_EVALS == 300, "评估量常量 300（文档口径）");

        /* ════ S2 命中豁免（4 项）════ */
        TestHost.suite("SubPrune · S2 命中豁免（命中过的一律不停）");
        TestHost.check(!SubPrune.shouldPrune(300, 1, T0, T0 + 3 * DAY),
                "命中 1 次 → 不停（「命中很少」的零红线：低频 ≠ 无效）");
        TestHost.check(!SubPrune.shouldPrune(100000, 1, T0, T0 + 365 * DAY),
                "评估 10 万次仅命中 1 次 → 仍不停（宁可保留绝不误杀）");
        TestHost.check(!SubPrune.shouldPrune(1000, 57, T0, T0 + 30 * DAY),
                "命中 57 次 → 不停");
        TestHost.check(!SubPrune.shouldPrune(300, -1, T0, T0 + 3 * DAY),
                "负命中计数（异常输入）→ 不停（保守面）");

        /* ════ S3 观察窗与评估量双门（6 项）════ */
        TestHost.suite("SubPrune · S3 双门独立性");
        TestHost.check(!SubPrune.shouldPrune(5000, 0, T0, T0 + 1 * DAY),
                "评估量远超但观察仅 1 天 → 不停（单日高频不当结论）");
        TestHost.check(!SubPrune.shouldPrune(100, 0, T0, T0 + 300 * DAY),
                "观察半年但仅 100 次评估 → 不停（样本量不足）");
        TestHost.check(SubPrune.shouldPrune(301, 0, T0, T0 + 3 * DAY + 1),
                "两门同时达标 +1 余量 → 停用");
        TestHost.check(SubPrune.MIN_AGE_MS == 3 * DAY, "观察窗常量 3 天（文档口径）");
        TestHost.check(SubPrune.EXPIRE_MS == 30 * DAY, "复评周期常量 30 天（文档口径）");
        TestHost.check(SubPrune.policyLine().contains("300") && SubPrune.policyLine().contains("30"),
                "策略摘要与常量同源（≥300 次 · 30 天复评）");

        /* ════ S4 复评到期（5 项）════ */
        TestHost.suite("SubPrune · S4 停用复评到期");
        TestHost.check(SubPrune.pruneExpired(T0, T0 + 30 * DAY),
                "停用满 30 天 → 到期复评（边界正好）");
        TestHost.check(SubPrune.pruneExpired(T0, T0 + 60 * DAY),
                "停用满 60 天 → 到期（远超）");
        TestHost.check(!SubPrune.pruneExpired(T0, T0 + 30 * DAY - 1),
                "差 1ms 满 30 天 → 未到期");
        TestHost.check(!SubPrune.pruneExpired(T0, T0 + 29 * DAY),
                "停用 29 天 → 未到期");
        TestHost.check(!SubPrune.pruneExpired(0, T0),
                "pruneAt=0（异常输入）→ 未到期（哨兵）");

        TestHost.done();
    }
}
