package com.zai.adshield.core;

/**
 * 订阅规则瘦身策略（v7.15.5 · 纯函数 · JVM 可单测）。
 * ─────────────────────────────────────────────────────────────
 * 用户指令（本轮原话）：「订阅的规则命中很少的话就去除掉」。
 * 市场订阅（id667 · 610+ 款应用）里大量应用规则对这台设备的用户零命中
 * （不装/不打开该应用、规则选择器已过期）——「命中很少」的最安全边界
 * 是【零命中】：命中过哪怕 1 次的规则保留（广告低频 ≠ 规则无效，误杀
 * 一次即少跳一条真广告）。
 *
 * 策略（每应用粒度——GKD 订阅的应用规则组是匹配与命中记账的天然单位）：
 *  · 停用条件：市场源规则在该应用上被评估 ≥{@link #MIN_EVALS} 次
 *    （跨 ≥{@link #MIN_AGE_MS}）仍零命中 → 该应用的市场规则停用
 *    （匹配时直接跳过；内置源不受影响——APK 自带 21 款是自家维护面）；
 *  · 复评：停用满 {@link #EXPIRE_MS}（30 天）自动解封并重置计数——
 *    订阅源日更，规则有二次机会；
 *  · 评估口径：matchSources 里市场源真正参与匹配一次记一次（主引擎
 *    文本命中时不消耗订阅评估——needSub 门控的自然语义）。
 *
 * 设计红线：纯 Java 零 Android 依赖（JVM 可单测 SubPruneTest）；
 * 不使用匿名类/内部类/lambda（d8 8.2 规约）。计数与停用表的持久化/
 * 日志由 SubscriptionManager 负责（Android 侧），本类只持策略常量与判定。
 */
public final class SubPrune {

    private SubPrune() {
    }

    /** 停用所需最低评估次数（300 次 ≈ 高频应用 3 天 / 低频应用 2~3 周的真实使用量）。 */
    public static final int MIN_EVALS = 300;

    /** 停用所需最低观察时长（3 天——防同日高频把单日偶发当结论）。 */
    public static final long MIN_AGE_MS = 3L * 24 * 60 * 60 * 1000L;

    /** 停用自动复评周期（30 天——订阅日更，停用规则定期获得二次机会）。 */
    public static final long EXPIRE_MS = 30L * 24 * 60 * 60 * 1000L;

    /**
     * 是否应停用该应用的市场源规则。
     *
     * @param evals    市场源规则在该应用上的累计评估次数
     * @param hits     累计命中次数（>0 一律保留——「命中很少」的最安全边界是零）
     * @param firstAt  首次评估时刻（0 = 尚无记录）
     * @param now      当前时刻
     */
    public static boolean shouldPrune(long evals, long hits, long firstAt, long now) {
        if (firstAt <= 0 || hits != 0) return false;
        if (evals < MIN_EVALS) return false;
        return now - firstAt >= MIN_AGE_MS;
    }

    /** 停用是否到期复评（解封 + 计数重置由调用方执行）。 */
    public static boolean pruneExpired(long pruneAt, long now) {
        return pruneAt > 0 && now - pruneAt >= EXPIRE_MS;
    }

    /** 策略一行摘要（订阅页/使用报告展示用——与实现同源零漂移）。 */
    public static String policyLine() {
        return "市场源规则零命中自动停用（评估 ≥" + MIN_EVALS + " 次 · 跨 ≥3 天），"
                + "停用 " + (EXPIRE_MS / (24 * 60 * 60 * 1000L)) + " 天后自动复评；命中过的规则永不自动停用";
    }
}
