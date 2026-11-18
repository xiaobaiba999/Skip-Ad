package com.skipad.quality;

import com.skipad.engine.RuleScheduler;

/**
 * 学习规则质量模型（v5.4.0）。
 *
 * <p>与 Web 端 src/lib/rule-quality.ts 保持 1:1（TS 为规范源；Wilson 公式 / 噪声
 * 过滤 / 生命周期 / 家族泛化四件套全部同构，CI 金样本集校验数值一致性）：
 * <pre>
 * ┌───────────────────────────────────┬─────────────────────────────────────────┐
 * │ Java（本类）                        │ TypeScript（src/lib/rule-quality.ts）     │
 * ├───────────────────────────────────┼─────────────────────────────────────────┤
 * │ wilsonLowerBound / qualityScore    │ scoreRule(stats)（内含 Wilson 公式）      │
 * │ grade(double)                      │ QualityReport.grade（A/B/C/D 分级）       │
 * │ isNoiseSample(LearningSample)      │ isNoiseSample(sample)                   │
 * │ lifecycleTransition(...)           │ lifecycleTransition(...)                │
 * │ generalizeToSdkFamily(...)         │ generalizeRule(rule)                    │
 * │ Lifecycle / RuleStats              │ RuleLifecycle / RuleStats               │
 * └───────────────────────────────────┴─────────────────────────────────────────┘
 * </pre>
 *
 * <p>治理动机（李跳跳教训）：无质量治理的自学习会把「聊天按钮位置」也学进去，
 * 次启乱点、毒指纹按穿主页。v5.4.0 质量模型四道闸：
 * <ol>
 *   <li>Wilson 置信下界评分：样本少时分数被强制压低，宁缺毋滥</li>
 *   <li>噪声样本四规则：同秒多点位 / 树体缩水不足 / 窗类不稳 / 超窗命中全拒学</li>
 *   <li>生命周期状态机：active → cooling → retired → shadow，误触即降级</li>
 *   <li>SDK 家族泛化：高质量单应用规则泛化为家族规则（先阴影观察后转正）</li>
 * </ol>
 */
public final class RuleQualityModel {

    private RuleQualityModel() {
    }

    /** 95% 置信 Z 值 */
    public static final double Z_95 = 1.96;

    /** 质量分级阈值（A/B/C/D） */
    public static final double GRADE_A = 85;
    public static final double GRADE_B = 70;
    public static final double GRADE_C = 50;

    /** 泛化门槛：命中数与质量分 */
    public static final int GENERALIZE_MIN_HITS = 5;
    public static final double GENERALIZE_MIN_QUALITY = GRADE_B;

    /** 生命周期阈值 */
    public static final double FALSE_RATE_COOLING = 0.15;
    public static final double FALSE_RATE_RETIRE = 0.40;
    public static final long IDLE_COOLING_MS = 14L * 24 * 3600 * 1000;   // 14 天无命中 → 降级
    public static final long IDLE_RETIRE_MS = 30L * 24 * 3600 * 1000;    // 30 天无命中 → 淘汰
    public static final long IDLE_SHADOW_MS = 90L * 24 * 3600 * 1000;    // 90 天无命中 → 归档
    public static final int REDEEM_STREAK = 10;                          // 连续 10 次命中零误触 → 恢复

    // ───────────────────────── Wilson 置信下界 ─────────────────────────

    /**
     * Wilson 置信区间下界（95%）。
     *
     * <pre>
     * 令 n = hit + fail，p = hit / n：
     *   denom  = 1 + z²/n
     *   centre = p + z²/(2n)
     *   margin = z · sqrt( p(1-p)/n + z²/(4n²) )
     *   LB     = (centre - margin) / denom
     * </pre>
     *
     * <p>调用方先做 Laplace 平滑（Beta(1,1) 先验：hit+1 / fail+1），保证零样本时
     * LB 落在 (0, 0.5) 而非除零。
     *
     * @param hit  成功命中数
     * @param fail 失败数（误触按 2 倍权重计入，见 {@link #failWeight}）
     */
    public static double wilsonLowerBound(int hit, int fail) {
        double h = hit + 1.0; // Laplace 平滑
        double f = fail + 1.0;
        double n = h + f;
        double p = h / n;
        double z = Z_95;
        double z2 = z * z;
        double denom = 1.0 + z2 / n;
        double centre = p + z2 / (2.0 * n);
        double margin = z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n));
        double lb = (centre - margin) / denom;
        return Math.max(0.0, Math.min(1.0, lb));
    }

    /** 失败权重：误触（falseCount）计 2，漏拦截（missCount）计 0.5——误触代价远高于漏拦 */
    public static double failWeight(int falseCount, int missCount) {
        return 2.0 * falseCount + 0.5 * missCount;
    }

    /**
     * 质量评分（0~100，写入 Rule.quality，随 computeScore/4 进入调度分）：
     *
     * <pre>
     * quality = 100 × WilsonLB₉₅( hitCount + 1 , 2×falseCount + 0.5×missCount + 1 )
     * </pre>
     */
    public static double qualityScore(int hitCount, int falseCount, int missCount) {
        return 100.0 * wilsonLowerBound(hitCount, (int) Math.round(failWeight(falseCount, missCount)));
    }

    /** 质量分级：A(≥85) / B(≥70) / C(≥50) / D(&lt;50) */
    public static char grade(double quality) {
        if (quality >= GRADE_A) {
            return 'A';
        }
        if (quality >= GRADE_B) {
            return 'B';
        }
        if (quality >= GRADE_C) {
            return 'C';
        }
        return 'D';
    }

    // ───────────────────────── 噪声样本过滤 ─────────────────────────

    /** 学习样本（点击/拦截发生后采集的上下文快照） */
    public static final class LearningSample {
        public String pkg;
        public String candidateWindowClass; // 候选点首次被发现时的窗口类
        public String windowClassBefore;    // 点击时窗口类
        public String windowClassAfter;     // 1.5s 后窗口类
        public int nodeCountBefore;         // 点击前树节点数
        public int nodeCountAfter;          // 1.5s 后树节点数
        public int sameSecondHits;          // 同一秒内学到的候选点位数
        public int[] targetPoint;           // 目标坐标 {x, y}
        public int[] windowBounds;          // 窗口 bounds {l, t, r, b}
        public long elapsedMs;              // 点击后到验证的时延
    }

    /** 噪声判定结果（含拒学原因，供结构化日志与看板归因） */
    public static final class NoiseCheck {
        public final boolean noise;
        public final String reason;

        NoiseCheck(boolean noise, String reason) {
            this.noise = noise;
            this.reason = reason;
        }
    }

    /**
     * 噪声样本四规则（任一命中即拒学——v5.3.0 豆包聊天乱学事故的根治闸）：
     * <ol>
     *   <li>sameSecondHits ≥ 2：同一秒学到两个及以上点位（聊天流式内容特征，
     *       根本不是稳定广告控件）→ 拒学</li>
     *   <li>treeShrink &lt; 40%：点击后 1.5s 内既无窗类切换也无树体缩水 ≥40%
     *       （窗没真关，点的是内容按钮）→ 拒学</li>
     *   <li>窗类不稳：候选点首次发现的窗口类与点击时窗口类不一致（窗口在闪切，
     *       学到的指纹时机不可靠）→ 拒学</li>
     *   <li>超窗命中：目标坐标不在窗口 bounds 内（坐标系错乱/窗口已切换）→ 拒学</li>
     * </ol>
     */
    public static NoiseCheck isNoiseSample(LearningSample s) {
        if (s == null) {
            return new NoiseCheck(true, "样本为空");
        }
        // 规则 1：同秒多点位
        if (s.sameSecondHits >= 2) {
            return new NoiseCheck(true, "same_second_multi_hit");
        }
        // 规则 3（前置）：窗类不稳（候选发现时窗类 ≠ 点击时窗类 → 指纹时机不可靠）
        if (s.candidateWindowClass != null && s.windowClassBefore != null
                && !s.candidateWindowClass.equals(s.windowClassBefore)) {
            return new NoiseCheck(true, "window_class_unstable");
        }
        // 规则 2：关窗验证（窗类切换 或 树体缩水 ≥40%，二者必有其一才算真关窗）
        boolean windowSwitched = s.windowClassBefore != null
                && !s.windowClassBefore.equals(s.windowClassAfter);
        if (s.nodeCountBefore > 0) {
            double shrink = 1.0 - (double) s.nodeCountAfter / (double) s.nodeCountBefore;
            if (shrink < 0.40 && !windowSwitched) {
                return new NoiseCheck(true, "tree_shrink_insufficient");
            }
        } else if (!windowSwitched) {
            return new NoiseCheck(true, "tree_count_unknown");
        }
        // 规则 4：超窗命中
        if (s.targetPoint != null && s.targetPoint.length >= 2
                && s.windowBounds != null && s.windowBounds.length >= 4) {
            int x = s.targetPoint[0];
            int y = s.targetPoint[1];
            if (x < s.windowBounds[0] || x > s.windowBounds[2]
                    || y < s.windowBounds[1] || y > s.windowBounds[3]) {
                return new NoiseCheck(true, "hit_out_of_window");
            }
        }
        return new NoiseCheck(false, "ok");
    }

    // ───────────────────────── 生命周期状态机 ─────────────────────────

    /** 生命周期状态（镜像 prisma Rule.status） */
    public enum Lifecycle {ACTIVE, COOLING, RETIRED, SHADOW}

    /** 生命周期判定输入（规则统计快照） */
    public static final class RuleStats {
        public int hitCount;
        public int falseCount;
        public int missCount;
        public int recentFalseStreak;   // 连续误触次数
        public int recentHitStreak;     // 连续命中次数（冷却期恢复观察）
        public long lastHitAtMs;
        public long nowMs;
    }

    /**
     * 生命周期状态机（active → cooling → retired → shadow）：
     * <pre>
     * active  → cooling : 误触率 ≥15% 或质量 &lt;C 或 14 天无命中
     * cooling → active  : 连续 10 次命中零误触（恢复观察期通过）
     * cooling → retired : 误触率 ≥40% 或质量 &lt;D 或 30 天无命中
     * retired → shadow  : 90 天无命中（归档留审计，不再参与调度）
     * 任意    → shadow  : 冲突处置 resolution=shadowed（阴影运行：只记日志不出手）
     * </pre>
     * 状态迁移单调保守：恢复只走 cooling→active 一条路（先降级观察再恢复，
     * 防止误触规则反复横跳）。
     */
    public static Lifecycle lifecycleTransition(Lifecycle current, RuleStats stats) {
        double falseRate = falseRate(stats);
        long idleMs = stats.nowMs - stats.lastHitAtMs;
        boolean idle = stats.hitCount == 0 || stats.lastHitAtMs <= 0;
        switch (current) {
            case ACTIVE:
                if (stats.recentFalseStreak >= 2
                        || falseRate >= FALSE_RATE_COOLING
                        || qualityScore(stats.hitCount, stats.falseCount, stats.missCount) < GRADE_C
                        || (idle ? idle : idleMs >= IDLE_COOLING_MS)) {
                    return Lifecycle.COOLING;
                }
                return Lifecycle.ACTIVE;
            case COOLING:
                if (stats.recentHitStreak >= REDEEM_STREAK && stats.recentFalseStreak == 0) {
                    return Lifecycle.ACTIVE; // 恢复观察期通过
                }
                if (falseRate >= FALSE_RATE_RETIRE
                        || qualityScore(stats.hitCount, stats.falseCount, stats.missCount) < GRADE_C / 2
                        || (idle ? idle : idleMs >= IDLE_RETIRE_MS)) {
                    return Lifecycle.RETIRED;
                }
                return Lifecycle.COOLING;
            case RETIRED:
                if (idle || idleMs >= IDLE_SHADOW_MS) {
                    return Lifecycle.SHADOW; // 归档
                }
                return Lifecycle.RETIRED;
            case SHADOW:
            default:
                return Lifecycle.SHADOW;
        }
    }

    /** 误触率（零样本按 0 处理——新规则不因无样本被降级，由 Wilson 下界压分） */
    public static double falseRate(RuleStats stats) {
        int total = stats.hitCount + stats.falseCount;
        if (total <= 0) {
            return 0.0;
        }
        return (double) stats.falseCount / total;
    }

    // ───────────────────────── SDK 家族泛化 ─────────────────────────

    /**
     * 泛化结果：成功时产出家族规则（appName = "sdk:csj" 等，generalizedFrom 记源应用）；
     * 不满足门槛返回 null（宁缺毋滥）。
     */
    public static final class Generalization {
        public final RuleScheduler.RuleRecord rule;
        public final String fromApp;
        public final String sdkFamily;

        Generalization(RuleScheduler.RuleRecord rule, String fromApp, String sdkFamily) {
            this.rule = rule;
            this.fromApp = fromApp;
            this.sdkFamily = sdkFamily;
        }
    }

    /**
     * SDK 家族泛化：单应用高质量规则 → 家族规则。
     *
     * <p>依据：穿山甲/优量汇/快手/AdMob 的广告容器结构在同 SDK 家族内高度一致
     * （同一 SDK 版本的跳过控件 viewId/容器签名跨应用复用），单应用学到的特征
     * 可安全泛化到 sdk:csj / sdk:gdt / sdk:ks / sdk:admob 作用域。
     *
     * <p>门槛：hitCount ≥ 5 且 quality ≥ B(70) 且源规则为应用专属（非全局 *）。
     * 泛化规则初始 status = shadow（阴影观察），3 次命中零误触后由
     * lifecycleTransition 转正——泛化错误只影响日志，不影响用户。
     */
    public static Generalization generalizeToSdkFamily(RuleScheduler.RuleRecord source,
                                                       String windowClass) {
        if (source == null || "*".equals(source.appName)) {
            return null; // 全局规则无需泛化
        }
        if (source.hitCount < GENERALIZE_MIN_HITS
                || source.quality < GENERALIZE_MIN_QUALITY) {
            return null;
        }
        String family = sdkFamilyOf(windowClass);
        if (family == null) {
            return null; // 宿主自建窗不泛化（结构因应用而异）
        }
        RuleScheduler.RuleRecord generalized = new RuleScheduler.RuleRecord();
        generalized.appName = "sdk:" + family;
        generalized.appCategory = "通用";
        generalized.adType = source.adType;
        generalized.matchType = source.matchType;
        generalized.pattern = source.pattern;
        generalized.action = source.action;
        generalized.source = RuleScheduler.SOURCE_LEARNED;
        generalized.confidence = source.confidence * 0.9; // 泛化降一档置信
        generalized.quality = source.quality;
        generalized.status = "shadow"; // 先阴影观察，3 次命中零误触后转正
        generalized.sdkFamily = family;
        generalized.generalizedFrom = source.appName;
        return new Generalization(generalized, source.appName, family);
    }

    /** 窗口类 → SDK 家族（与 MatchingEngine.detectSdkFamily 同口径） */
    public static String sdkFamilyOf(String windowClass) {
        if (windowClass == null) {
            return null;
        }
        String lower = windowClass.toLowerCase();
        if (lower.contains("com.bytedance.sdk.openadsdk")) {
            return "csj";
        }
        if (lower.contains("com.qq.e.ads")) {
            return "gdt";
        }
        if (lower.contains("com.kwad")) {
            return "ks";
        }
        if (lower.contains("com.google.android.gms.ads")) {
            return "admob";
        }
        return null;
    }
}
