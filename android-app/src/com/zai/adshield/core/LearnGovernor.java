package com.zai.adshield.core;

/**
 * v5.5.0 学习规则治理官（模块二核心）——质量评分 · 噪声过滤 · 生命周期 · SDK 泛化
 * ─────────────────────────────────────────────────────────────
 * 设计思路（借鉴李跳跳「学习即资产」+ GKD 社区规则的置信分级）：
 *  学习规则是单设备小样本，天然带噪声（误点一次广告诱导键就被学走）。
 *  给每条学习记录算一个 0..1 的质量分，只有过了质量门才允许：
 *    ① 进入 RuleMerge 三源合并（否则学习规则反而稀释官方规则准确率）；
 *    ② 参与 P0 预拦截（低分规则预拦截 = 误触率上升，红线不允许）。
 *
 * 质量评分（score，0..1，越高越可信）：
 *    命中深度   min(hitCount/5, 1) × 0.5 —— 5 次独立命中即满分半壁
 *    成功率     success/(success+fail) × 0.3 —— 点了真关广告才算数
 *    新鲜度     半衰期 14 天 × 0.2 —— 陈旧规则（应用改版失效）自动降权
 *
 * 生命周期（lifecycleStage）：
 *    PROBATION 新生期：首次学习后 72h 内。只观察不预拦截（除非 SDK 泛化），
 *              72h 内无第二次命中 → 直接退休（噪声过滤主通道——
 *              v5.1.0 已实证「一次误学」是学习噪声的最大来源）；
 *    ACTIVE    活跃期：score ≥ 0.55 且 30 天内有命中。全量参与；
 *    DECAYING  衰退期：30 天未命中但历史 hitCount ≥ 3。保留观察，
 *              预拦截降级为「高门槛二次确认」（候选层最后出手）；
 *    RETIRED   退休期：score < 0.35，或 PROBATION 期满未复现。删除。
 *
 * SDK 特征泛化（sdkGeneralize）：
 *  学习到的窗口类名若命中 SDK 通用形态（Dialog/Popup/tt_splash/ks_/
 *  csj/gdt 等），说明这不是「某应用特有布局」而是「SDK 广告容器通用
 *  骨架」——标记 generalized 后可跨应用复用（A 应用学会的 SDK 骨架，
 *  B 应用同 SDK 广告直接预拦截），学习效率数量级提升。
 *
 * 设计红线：纯 Java 零依赖、纯函数（JVM 可测 LearnGovernorTest）；
 * 所有阈值常量 public，供测试与调参共用。
 */
public final class LearnGovernor {

    private LearnGovernor() {
    }

    /* ───────── 质量评分权重（和为 1.0） ───────── */
    public static final double W_HIT = 0.5;      // 命中深度权重
    public static final double W_SUCCESS = 0.3;  // 成功率权重
    public static final double W_FRESH = 0.2;    // 新鲜度权重

    public static final int HIT_FULL_AT = 5;        // 5 次命中拿满命中分
    public static final long HALF_LIFE_MS = 14L * 24 * 3600 * 1000; // 新鲜度半衰期 14 天

    /* ───────── 生命周期阈值 ───────── */
    public static final long PROBATION_MS = 72L * 3600 * 1000;   // 新生期 72h
    public static final long STALE_MS = 30L * 24 * 3600 * 1000;  // 衰退阈值 30 天
    public static final double ACTIVE_MIN_SCORE = 0.55;          // 活跃最低分
    public static final double RETIRE_MAX_SCORE = 0.35;          // 退休最高分
    public static final int DECAY_KEEP_MIN_HITS = 3;             // 衰退保留最低命中数

    /* ───────── 生命周期阶段 ───────── */
    public static final int STAGE_PROBATION = 0; // 新生（观察，不预拦截）
    public static final int STAGE_ACTIVE = 1;    // 活跃（全量参与）
    public static final int STAGE_DECAYING = 2;  // 衰退（高门槛参与）
    public static final int STAGE_RETIRED = 3;   // 退休（删除）

    /** 学习规则记录（AdSkipService 学习侧的最小字段投影） */
    public static final class Record {
        public String pkg;         // 目标应用
        public String fingerprint; // 窗口类名 / 位置指纹 / viewId 键
        public int hitCount;       // 累计命中
        public int failCount;      // 点了没关（成功率的分母之一）
        public long firstAt;       // 首次学习时间（epoch ms）
        public long lastAt;        // 最近命中时间
        public boolean generalized; // SDK 特征泛化标记（跨应用可复用）

        public Record(String pkg, String fingerprint, int hitCount, int failCount,
                      long firstAt, long lastAt) {
            this.pkg = pkg == null ? "" : pkg;
            this.fingerprint = fingerprint == null ? "" : fingerprint;
            this.hitCount = hitCount;
            this.failCount = failCount;
            this.firstAt = firstAt;
            this.lastAt = lastAt;
        }

        public int successCount() {
            return Math.max(0, hitCount - failCount);
        }

        /** 生命周期键（RuleMerge 学习条目选择器 = pkg\u0000fingerprint） */
        public String key() {
            return this.pkg + "\u0000" + this.fingerprint;
        }
    }

    /* ───────── 质量评分（0..1） ───────── */

    /** 质量分：命中深度 + 成功率 + 新鲜度加权。now=当前 epoch ms */
    public static double score(Record r, long now) {
        if (r == null) return 0;
        /* 命中深度：min(hit/5,1)×0.5 */
        double hitScore = Math.min((double) r.hitCount / HIT_FULL_AT, 1.0) * W_HIT;
        /* 成功率：success/(success+fail)×0.3，零样本记 0（宁可保守） */
        int total = r.successCount() + r.failCount;
        double succScore = total > 0
                ? ((double) r.successCount() / total) * W_SUCCESS : 0;
        /* 新鲜度：指数半衰 exp(-ln2 · Δt/14d)，×0.2 */
        long age = Math.max(0, now - r.lastAt);
        double fresh = Math.pow(0.5, (double) age / HALF_LIFE_MS) * W_FRESH;
        return clamp(hitScore + succScore + fresh);
    }

    static double clamp(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /* ───────── 生命周期阶段判定 ───────── */

    /** 生命周期阶段（纯函数：由记录与当前时间决定） */
    public static int stage(Record r, long now) {
        if (r == null) return STAGE_RETIRED;
        long sinceFirst = now - r.firstAt;
        long sinceLast = now - r.lastAt;
        /* 新生期：72h 内且命中 <2（无复现证据）。SDK 泛化规则豁免——
         * SDK 骨架是跨应用强信号，首次即可信（官方指纹同源验证过）。 */
        boolean inProbation = sinceFirst <= PROBATION_MS && r.hitCount < 2;
        if (inProbation && !r.generalized) return STAGE_PROBATION;
        /* 退休：低分，或新生期满未复现（噪声主通道），或超衰退下限 */
        double s = score(r, now);
        if (s < RETIRE_MAX_SCORE) return STAGE_RETIRED;
        if (sinceFirst > PROBATION_MS && r.hitCount < 2) return STAGE_RETIRED;
        /* 衰退：30 天未命中但历史 ≥3 次（有真实价值，留观察） */
        if (sinceLast > STALE_MS) {
            return r.hitCount >= DECAY_KEEP_MIN_HITS ? STAGE_DECAYING : STAGE_RETIRED;
        }
        /* 活跃：分数达标 */
        return s >= ACTIVE_MIN_SCORE ? STAGE_ACTIVE : STAGE_DECAYING;
    }

    /* ───────── 噪声过滤（准入门槛） ───────── */

    /**
     * 是否允许进入三源合并 / P0 预拦截：
     *  · ACTIVE 全量参与；DECAYING 高门槛参与（不预拦截，仅候选层兜底）
     *  · PROBATION 仅 SDK 泛化规则放行（骨架强信号）
     *  · RETIRED 一律不放行（应删除）
     */
    public static boolean admit(Record r, long now, boolean forPreIntercept) {
        int st = stage(r, now);
        if (st == STAGE_ACTIVE) return true;
        if (st == STAGE_DECAYING) return !forPreIntercept; // 衰退规则不预拦截
        if (st == STAGE_PROBATION) return r.generalized;   // 新生仅泛化放行
        return false;
    }

    /* ───────── SDK 特征泛化 ───────── */

    /**
     * 学习到的窗口类名是否 SDK 广告容器通用形态。
     * 命中 → generalized=true，可跨应用复用（学习效率数量级提升）。
     * 清单与 AdSkipService SDK 窗口指纹同源（穿山甲/优量汇/快手/百青藤
     * 四大主流 + 通用 Dialog/Popup 骨架——官方指纹已验证的强信号）。
     * v5.5.1 同源对齐：注释一直声称「与 SDK 窗口指纹同源」，但
     * isAdActivityClass 认的家族段（qq.e.ads/gms.ads/bytedance.sdk/kwad/
     * admob 等）在这里并不认识——GDT 类窗（com.qq.e.ads.LandingPageActivity）
     * 学到规则却拿不到泛化标记 → 白等 72h 新生期。补齐家族段，两表真正同源。
     */
    public static boolean sdkGeneralize(String windowClass) {
        if (windowClass == null) return false;
        String c = windowClass.toLowerCase();
        /* 通用骨架：Dialog/Popup 类（无障碍树里的广告弹窗九成挂这两骨架） */
        if (c.contains("dialog") || c.contains("popup")) return true;
        /* 主流 SDK 广告容器类名段（与官方 SDK 指纹表同源维护） */
        String[] sdkSegs = {
                "tt_splash", "tt_fullery", "tt_reward", "tt_feed",     // 穿山甲
                "openadsdk", "stub_standard_portrait", "stub_activity", // 穿山甲 stub 容器
                "gdt_splash", "gdt_adv", "adnet",                      // 优量汇
                "ksad", "ks_splash", "ks_reward",                      // 快手
                "csj_splash", "csjad", "baidumobads",                  // 百青藤/百度
                "splashadactivity", "adactivity", "ad.landingpage",    // 通用广告落地页
                /* v5.5.1 同源对齐：isAdActivityClass 认的家族段（此前漏网） */
                "qq.e.ads", "gms.ads", "bytedance.sdk", "kwad", "admob",
                "miui.zeus", "doubleclick", "unionad",
                /* v5.8.0 同源对齐：isAdActivityClass v5.8 新认的聚合/国际段 */
                "mintegral", "pangle", "topon", "moloco",
                /* v5.9.0 同源对齐：isAdActivityClass v5.9 新认的国内聚合段
                 *（ksad 原已在表——快手族；csj_ 为穿山甲联盟复合族补齐） */
                "csj_",
                /* v6.2.0 同源对齐：isAdActivityClass v6.2 新认的四段
                 *（扫描日志六轮拓宽：advertisement——AdvertisementActivity
                 * 整段 / offerwall——OfferWall 积分墙 / screenad——
                 * SplashScreenAd 复合族 / adsdialog——AdsDialog 弹窗） */
                "advertisement", "offerwall", "screenad", "adsdialog",
        };
        for (String seg : sdkSegs) {
            if (c.contains(seg)) return true;
        }
        return false;
    }

    /* ───────── 阶段文案（诊断日志用） ───────── */

    public static String stageName(int st) {
        switch (st) {
            case STAGE_PROBATION: return "新生观察";
            case STAGE_ACTIVE: return "活跃";
            case STAGE_DECAYING: return "衰退观察";
            default: return "退休";
        }
    }
}
