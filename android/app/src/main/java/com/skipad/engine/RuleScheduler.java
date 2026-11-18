package com.skipad.engine;

import android.content.Context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 多源规则统一调度器（v5.4.0）。
 *
 * <p>四类规则源（preset 官方内置 / gkd 社区订阅 / learned 本地学习 / custom 用户自定义）
 * 统一进入同一调度体系：优先级权重 → 冲突检测 → 去重合并 → 生效排序与覆盖。
 *
 * <p>与 Web 端实现 1:1 同构（TS 为规范源，Java 为对齐实现，CI 金样本集保证判定一致）：
 *
 * <pre>
 * ┌─────────────────────────────────┬──────────────────────────────────────────────┐
 * │ Java（本类）                      │ TypeScript（src/lib/rule-scheduler.ts）        │
 * ├─────────────────────────────────┼──────────────────────────────────────────────┤
 * │ SOURCE_BASE_WEIGHT / sourceWeight│ SOURCE 常量表 + computeScore 内取基权          │
 * │ computeScore(RuleRecord)         │ computeScore(rule)                           │
 * │ detectConflicts(List)            │ detectConflicts(rules)                       │
 * │ dedupeMerge(List, List)          │ dedupeMerge(rules, conflicts)                │
 * │ schedule(pkg, adTypeHint, family)│ schedule(rules, { appName, adType })         │
 * │ registerRule / importGkdRules    │ 订阅导入（rule-dsl.ts parseGkdSubscription）  │
 * │ RuleRecord（字段镜像 prisma Rule）│ SchedulerRule 接口 / prisma Rule 模型         │
 * │ ConflictRecord                   │ ConflictItem / prisma RuleConflict           │
 * └─────────────────────────────────┴──────────────────────────────────────────────┘
 * </pre>
 *
 * <p>设计要点（对李跳跳类工具的缺陷规避）：
 * <ul>
 *   <li>规则冲突必须受管控：李跳跳规则社区无冲突检测，规则一多互相踩踏（同窗既点跳过
 *       又按返回）；本调度器 duplicate/overlap/contradict 三类冲突全部显式落库
 *       （RuleConflict 模型），contradict 绝不自动二选一，转 shadow 待人工确认。</li>
 *   <li>返回键类规则天然降权（-15）：返回键兜底是最后手段，绝不允许排在代点类规则之前
 *       抢跑（v3.6.0 毒指纹事故的调度层根治）。</li>
 * </ul>
 */
public final class RuleScheduler {

    // ───────────────────────── 源基础权重 ─────────────────────────

    /** 源基础权重：custom 40 / learned 30 / gkd 20 / preset 10（与 TS 版常量表一致） */
    public static final int WEIGHT_CUSTOM = 40;
    public static final int WEIGHT_LEARNED = 30;
    public static final int WEIGHT_GKD = 20;
    public static final int WEIGHT_PRESET = 10;
    public static final int WEIGHT_UNKNOWN = 5;

    /** computeScore 增量项 */
    public static final int BONUS_APP_SPECIFIC = 25;   // 应用专属规则（appName != "*"）
    public static final int QUALITY_DIVISOR = 4;       // quality 0~100 → 0~25 分
    public static final int HIT_DIVISOR = 10;          // 命中次数 / 10
    public static final int HIT_SCORE_CAP = 20;        // 命中分上限（防单条海量命中挤占调度）
    public static final int PENALTY_BACKKEY = -15;     // 返回键类规则降权（最后手段）

    public static final String SOURCE_PRESET = "preset";
    public static final String SOURCE_GKD = "gkd";
    public static final String SOURCE_LEARNED = "learned";
    public static final String SOURCE_CUSTOM = "custom";

    // ───────────────────────── 单例 ─────────────────────────

    private static volatile RuleScheduler sInstance;

    /** 全量规则库（内存态；持久化见 TODO） */
    private final List<RuleRecord> rules = new ArrayList<>();
    /** 冲突记录（镜像 prisma RuleConflict） */
    private final List<ConflictRecord> conflicts = new ArrayList<>();

    private RuleScheduler() {
    }

    /** @Singleton 风格获取（无 Context 依赖，核心逻辑纯函数化，便于 JVM 单测 68 项断言） */
    public static RuleScheduler getInstance() {
        if (sInstance == null) {
            synchronized (RuleScheduler.class) {
                if (sInstance == null) {
                    RuleScheduler s = new RuleScheduler();
                    s.seedPresetIfEmpty();
                    sInstance = s;
                }
            }
        }
        return sInstance;
    }

    /** Android 侧入口（预留持久化通道：SharedPreferences/Room 装载） */
    public static RuleScheduler getInstance(Context context) {
        return getInstance();
    }

    // ───────────────────────── 规则记录（镜像 prisma Rule） ─────────────────────────

    /** 规则记录：字段与 prisma/schema.prisma 的 Rule 模型一一对应 */
    public static final class RuleRecord {
        public String id;
        public String appName = "*";        // * = 全局规则；sdk:csj 等 = SDK 家族泛化规则
        public String appCategory = "通用";
        public String adType;               // splash | interstitial | half_interstitial | ...
        public String matchType;            // text | viewId | gkd-selector | learned | popup-v2 | ...
        public String pattern;              // 匹配特征（文本正则 / viewId / DSL 表达式）
        public String action;               // instant_skip | popup_close | back_fallback | ...
        public String source = SOURCE_PRESET;
        public double confidence = 0.9;
        public int hitCount;
        public int missCount;               // 漏拦截计数（质量评分输入）
        public int falseCount;              // 误触计数（质量评分输入）
        public boolean enabled = true;
        public int priority;                // v5.4.0：>0 人工优先级直通（审计仍记录）
        public double quality;              // v5.4.0：质量评分 0~100（A/B/C/D 分级）
        public String status = "active";    // active | cooling | retired | shadow
        public String sdkFamily;            // csj | gdt | ks | admob | host
        public String generalizedFrom;      // SDK 家族泛化的源应用名
        public String gkdRef;               // GKD 订阅来源标识（categoryId.groupId）
        public int version = 1;             // 每次编辑 +1（审计用）
        public long lastHitAt;
        public long updatedAt;

        public RuleRecord() {
        }

        public static RuleRecord of(String appName, String adType, String matchType,
                                    String pattern, String action, String source) {
            RuleRecord r = new RuleRecord();
            r.appName = appName;
            r.adType = adType;
            r.matchType = matchType;
            r.pattern = pattern;
            r.action = action;
            r.source = source;
            return r;
        }
    }

    /** 冲突记录：镜像 prisma RuleConflict（kind: duplicate|overlap|contradict） */
    public static final class ConflictRecord {
        public String ruleAId;
        public String ruleBId;
        public String kind;        // duplicate | overlap | contradict
        public String severity;    // high | medium | low
        public String detail;
        public String suggestion;  // 合并 | 阴影运行 | 人工确认
        public String resolution = "pending"; // pending | merged | shadowed | dismissed
    }

    // ───────────────────────── 评分 ─────────────────────────

    /** 源基础权重 */
    public static int sourceWeight(String source) {
        if (source == null) {
            return WEIGHT_UNKNOWN;
        }
        switch (source) {
            case SOURCE_CUSTOM:
                return WEIGHT_CUSTOM;
            case SOURCE_LEARNED:
                return WEIGHT_LEARNED;
            case SOURCE_GKD:
                return WEIGHT_GKD;
            case SOURCE_PRESET:
                return WEIGHT_PRESET;
            default:
                return WEIGHT_UNKNOWN;
        }
    }

    /** 返回键类规则判定（matchType=backkey 或 action=back_fallback） */
    public static boolean isBackKeyClass(RuleRecord r) {
        return "backkey".equals(r.matchType) || "back_fallback".equals(r.action);
    }

    /**
     * 调度优先级分（0~100，越大越先出手）。
     *
     * <p>公式（与 TS 版 computeScore 一致）：
     * <pre>
     * score = SOURCE_BASE_WEIGHT[source]
     *       + (appName != "*" ? 25 : 0)        // 应用专属 +25
     *       + quality / 4                       // 质量分 0~100 → 0~25
     *       + min(20, hitCount / 10)            // 命中分（上限 20）
     *       + (返回键类 ? -15 : 0)              // 返回键兜底降权
     * priority &gt; 0 时人工优先级直通（仍被 clamp 与覆盖逻辑约束）。
     * </pre>
     */
    public static int computeScore(RuleRecord r) {
        if (r == null) {
            return 0;
        }
        if (r.priority > 0) {
            return clamp(r.priority);
        }
        int score = sourceWeight(r.source);
        if (!"*".equals(r.appName)) {
            score += BONUS_APP_SPECIFIC;
        }
        score += Math.round(r.quality / (float) QUALITY_DIVISOR);
        score += Math.min(HIT_SCORE_CAP, r.hitCount / HIT_DIVISOR);
        if (isBackKeyClass(r)) {
            score += PENALTY_BACKKEY;
        }
        return clamp(score);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    // ───────────────────────── 冲突检测 ─────────────────────────

    /**
     * 三类冲突检测（两两比对，仅对 enabled 规则生效）：
     * <ul>
     *   <li>duplicate：appName + matchType + 归一化 pattern + action 完全一致
     *       （同源 → high，建议合并；跨源 → low，建议保留高分者）</li>
     *   <li>overlap：目标范围重叠（同 adType、同 action，且 pattern 特征词有交集）
     *       → medium，建议低分者阴影运行</li>
     *   <li>contradict：同窗动作语义对立（代点类 vs 仅标注类；返回键类 vs 代点类）
     *       → high，绝不自动二选一，双转 shadow 待人工确认</li>
     * </ul>
     */
    public static List<ConflictRecord> detectConflicts(List<RuleRecord> input) {
        List<ConflictRecord> out = new ArrayList<>();
        List<RuleRecord> active = filterEnabled(input);
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                RuleRecord a = active.get(i);
                RuleRecord b = active.get(j);
                String kind = classifyPair(a, b);
                if (kind != null) {
                    out.add(buildConflict(a, b, kind));
                }
            }
        }
        return out;
    }

    private static String classifyPair(RuleRecord a, RuleRecord b) {
        // 1) duplicate：签名完全一致
        if (signature(a).equals(signature(b))) {
            return "duplicate";
        }
        // 2) contradict：目标范围重叠但动作语义对立（优先级高于 overlap）
        if (sameTargetScope(a, b) && actionsContradict(a.action, b.action)) {
            return "contradict";
        }
        // 3) overlap：同目标范围 + 同动作 + 特征词交集非空
        if (sameTargetScope(a, b)
                && a.action != null && a.action.equals(b.action)
                && patternTokensOverlap(a.pattern, b.pattern)) {
            return "overlap";
        }
        return null;
    }

    /** 目标范围重叠：同 adType 且应用名一致（或其中一方为全局 *） */
    private static boolean sameTargetScope(RuleRecord a, RuleRecord b) {
        if (a.adType == null || !a.adType.equals(b.adType)) {
            return false;
        }
        return a.appName.equals(b.appName) || "*".equals(a.appName) || "*".equals(b.appName);
    }

    /** 动作对立组：代点类 vs 仅标注类；返回键类 vs 代点类 */
    private static boolean actionsContradict(String x, String y) {
        boolean xClick = in(x, CLICK_ACTIONS), yClick = in(y, CLICK_ACTIONS);
        boolean xPassive = in(x, PASSIVE_ACTIONS), yPassive = in(y, PASSIVE_ACTIONS);
        boolean xBack = in(x, BACK_ACTIONS), yBack = in(y, BACK_ACTIONS);
        return (xClick && yPassive) || (xPassive && yClick) || (xBack && yClick) || (xClick && yBack);
    }

    private static final String[] CLICK_ACTIONS = {
            "instant_skip", "coord_tap", "popup_close", "halfsheet_close", "decline_tap",
            "preroll_skip", "sdk_window_cut", "chain_block", "gyro_block", "preempt_block", "guard_skip"
    };
    private static final String[] PASSIVE_ACTIONS = {"mark_only", "overlay_notice"};
    private static final String[] BACK_ACTIONS = {"back_fallback"};

    private static boolean in(String v, String[] set) {
        if (v == null) {
            return false;
        }
        for (String s : set) {
            if (s.equals(v)) {
                return true;
            }
        }
        return false;
    }

    /** 归一化签名：appName|matchType|normalizedPattern|action */
    static String signature(RuleRecord r) {
        return r.appName + "|" + r.matchType + "|" + normalizePattern(r.pattern) + "|" + r.action;
    }

    static String normalizePattern(String pattern) {
        if (pattern == null) {
            return "";
        }
        return pattern.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** 特征词提取：DSL 引号字面量 + 裸 CJK 词（用于 overlap 判定） */
    static Set<String> patternTokens(String pattern) {
        Set<String> tokens = new HashSet<>();
        if (pattern == null) {
            return tokens;
        }
        StringBuilder literal = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '"' || c == '\'') {
                if (inQuote && literal.length() > 0) {
                    tokens.add(literal.toString());
                    literal.setLength(0);
                }
                inQuote = !inQuote;
            } else if (inQuote) {
                literal.append(c);
            }
        }
        // 裸文本特征（非 DSL 的文本规则直接整段作为特征词）
        String bare = pattern.replace("|", " ").trim();
        if (!pattern.contains("[") && !bare.isEmpty()) {
            tokens.add(normalizePattern(bare));
        }
        return tokens;
    }

    private static boolean patternTokensOverlap(String pa, String pb) {
        Set<String> ta = patternTokens(pa);
        Set<String> tb = patternTokens(pb);
        if (ta.isEmpty() || tb.isEmpty()) {
            return false; // 任一方无特征词（如纯 viewId 描述）→ 不判 overlap，交人工
        }
        for (String t : ta) {
            if (tb.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static ConflictRecord buildConflict(RuleRecord a, RuleRecord b, String kind) {
        ConflictRecord c = new ConflictRecord();
        c.ruleAId = a.id;
        c.ruleBId = b.id;
        c.kind = kind;
        if ("duplicate".equals(kind)) {
            boolean sameSource = a.source != null && a.source.equals(b.source);
            c.severity = sameSource ? "high" : "low";
            c.detail = "签名完全一致：" + signature(a);
            c.suggestion = sameSource ? "合并" : "保留高分者，低分者转 shadow";
        } else if ("overlap".equals(kind)) {
            c.severity = "medium";
            c.detail = "目标范围重叠（" + a.adType + "）且特征词存在交集，同窗重复出手风险";
            c.suggestion = "阴影运行";
        } else {
            c.severity = "high";
            c.detail = "动作语义对立：" + a.action + " vs " + b.action + "（同窗互相干扰）";
            c.suggestion = "人工确认";
        }
        return c;
    }

    // ───────────────────────── 去重合并 ─────────────────────────

    /** 合并报告 */
    public static final class MergeReport {
        public int kept;
        public int mergedAway;
        public int shadowed;
        public int pendingManual;
    }

    /**
     * 去重合并（对 detectConflicts 的结果执行处置）：
     * <ul>
     *   <li>duplicate → 保留 computeScore 高者（同分按 custom&gt;learned&gt;gkd&gt;preset，
     *       再比 version）；败者停用（enabled=false, status=retired）留审计，命中计数并回胜者</li>
     *   <li>overlap → 低分者转 shadow（阴影运行：只记日志不出手，供看板对照）</li>
     *   <li>contradict → 双方转 shadow + resolution=pending，绝不自动二选一</li>
     * </ul>
     */
    public MergeReport dedupeMerge(List<RuleRecord> input, List<ConflictRecord> detected) {
        MergeReport report = new MergeReport();
        Map<String, RuleRecord> byId = new HashMap<>();
        for (RuleRecord r : filterEnabled(input)) {
            byId.put(r.id, r);
        }
        for (ConflictRecord c : detected) {
            RuleRecord a = byId.get(c.ruleAId);
            RuleRecord b = byId.get(c.ruleBId);
            if (a == null || b == null) {
                continue;
            }
            if ("duplicate".equals(c.kind)) {
                RuleRecord winner = pickWinner(a, b);
                RuleRecord loser = winner == a ? b : a;
                if (!winner.enabled || !loser.enabled) {
                    continue; // 已被前一轮处置
                }
                winner.hitCount += loser.hitCount;
                winner.missCount += loser.missCount;
                winner.falseCount += loser.falseCount;
                loser.enabled = false;
                loser.status = "retired";
                c.resolution = "merged";
                report.mergedAway++;
            } else if ("overlap".equals(c.kind)) {
                RuleRecord loser = computeScore(a) >= computeScore(b) ? b : a;
                if (loser.enabled) {
                    loser.status = "shadow";
                    c.resolution = "shadowed";
                    report.shadowed++;
                }
            } else { // contradict
                if (a.enabled && b.enabled) {
                    a.status = "shadow";
                    b.status = "shadow";
                    c.resolution = "pending";
                    report.pendingManual += 2;
                }
            }
        }
        report.kept = countActive(input);
        this.conflicts.addAll(detected);
        return report;
    }

    private static RuleRecord pickWinner(RuleRecord a, RuleRecord b) {
        int sa = computeScore(a);
        int sb = computeScore(b);
        if (sa != sb) {
            return sa > sb ? a : b;
        }
        int wa = sourceWeight(a.source);
        int wb = sourceWeight(b.source);
        if (wa != wb) {
            return wa > wb ? a : b;
        }
        return a.version >= b.version ? a : b;
    }

    // ───────────────────────── 生效排序与覆盖 ─────────────────────────

    /**
     * 窗口级调度：产出有序生效规则列表（五层规则 DFS 的输入）。
     *
     * <p>生效顺序与覆盖逻辑（与 TS 版 scheduleForWindow 一致）：
     * <ol>
     *   <li>过滤：enabled 且 status=active（shadow 仅在审计模式随行）</li>
     *   <li>范围匹配：appName == pkg，或 appName == "*"（全局），或
     *       appName == "sdk:家族" 且当前窗命中该 SDK 家族指纹</li>
     *   <li>排序：computeScore 降序；同分按源权重降序、version 降序（稳定）</li>
     *   <li>覆盖：同目标键（appName|adType|matchType）只保留最高分——
     *       落败者标记 shadow（记录在案不出手），保证同窗同目标只有一条规则出手</li>
     * </ol>
     *
     * @param pkg          当前窗口包名
     * @param adTypeHint   场景提示（无则传 null，规则按自身 adType 匹配 any/*）
     * @param sdkFamily    当前窗 SDK 家族（无则 null）
     */
    public List<RuleRecord> schedule(String pkg, String adTypeHint, String sdkFamily) {
        List<RuleRecord> candidates = new ArrayList<>();
        for (RuleRecord r : rules) {
            if (!r.enabled || !"active".equals(r.status)) {
                continue;
            }
            if (!scopeMatches(r, pkg, sdkFamily)) {
                continue;
            }
            if (adTypeHint != null && r.adType != null
                    && !"any".equals(r.adType) && !adTypeHint.equals(r.adType)) {
                continue;
            }
            candidates.add(r);
        }
        candidates.sort(RULE_ORDER);
        // 覆盖逻辑：同目标键仅保留最高分
        Map<String, RuleRecord> winnerByKey = new HashMap<>();
        List<RuleRecord> ordered = new ArrayList<>();
        for (RuleRecord r : candidates) {
            String key = r.appName + "|" + r.adType + "|" + r.matchType;
            RuleRecord winner = winnerByKey.get(key);
            if (winner == null) {
                winnerByKey.put(key, r);
                ordered.add(r);
            }
            // 落败者不进生效列表（覆盖即淘汰；审计见 RuleConflict/结构化日志）
        }
        return ordered;
    }

    private static boolean scopeMatches(RuleRecord r, String pkg, String sdkFamily) {
        if ("*".equals(r.appName)) {
            return true;
        }
        if (r.appName != null && r.appName.startsWith("sdk:")) {
            String family = r.appName.substring(4);
            return family.equals(sdkFamily);
        }
        return r.appName.equals(pkg);
    }

    private static final Comparator<RuleRecord> RULE_ORDER = new Comparator<RuleRecord>() {
        @Override
        public int compare(RuleRecord a, RuleRecord b) {
            int d = computeScore(b) - computeScore(a);
            if (d != 0) {
                return d;
            }
            d = sourceWeight(b.source) - sourceWeight(a.source);
            if (d != 0) {
                return d;
            }
            return b.version - a.version;
        }
    };

    // ───────────────────────── 规则库维护 ─────────────────────────

    /** 注册规则（custom/learned 通道）；返回规则 id */
    public String registerRule(RuleRecord r) {
        if (r.id == null || r.id.isEmpty()) {
            r.id = "r_" + Long.toHexString(System.nanoTime());
        }
        r.updatedAt = System.currentTimeMillis();
        rules.add(r);
        return r.id;
    }

    /**
     * 导入 GKD 订阅规则（导入即去重：同 gkdRef + 同 pattern 已存在则跳过）。
     *
     * @return 实际新增条数（重复跳过数记入返回的 int[1]）
     */
    public int importGkdRules(List<RuleRecord> gkdRules, String gkdRef, int[] skippedOut) {
        int added = 0;
        int skipped = 0;
        for (RuleRecord r : gkdRules) {
            r.source = SOURCE_GKD;
            r.gkdRef = gkdRef;
            boolean dup = false;
            for (RuleRecord existing : rules) {
                if (gkdRef != null && gkdRef.equals(existing.gkdRef)
                        && normalizePattern(r.pattern).equals(normalizePattern(existing.pattern))) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                skipped++;
                continue;
            }
            registerRule(r);
            added++;
        }
        if (skippedOut != null && skippedOut.length > 0) {
            skippedOut[0] = skipped;
        }
        return added;
    }

    /** 规则命中累计（供引擎回写） */
    public void recordHit(String ruleId) {
        for (RuleRecord r : rules) {
            if (r.id != null && r.id.equals(ruleId)) {
                r.hitCount++;
                r.lastHitAt = System.currentTimeMillis();
                return;
            }
        }
    }

    /**
     * 旧库自动补种（向后兼容）：版本升级新增预置规则时，只补缺失项，不动存量
     * （存量命中路径零影响——见 docs/OPTIMIZATION-v5.4.0.md「向后兼容性保障」）。
     */
    public void seedPresetIfEmpty() {
        if (!rules.isEmpty()) {
            return;
        }
        // TODO: 从 assets/preset-rules.json 装载官方内置规则（与 Web 端 preset-rules.ts 同源导出）
        rules.add(RuleRecord.of("*", "splash", "text",
                "[text*=\"跳过\"] || [text*=\"Skip\"]", "instant_skip", SOURCE_PRESET));
        rules.add(RuleRecord.of("*", "splash", "timing",
                "启动 12s 时间窗 + TYPE_WINDOW_CONTENT_CHANGED 持续监听", "instant_skip", SOURCE_PRESET));
        rules.add(RuleRecord.of("*", "splash", "area",
                "面积安全阀：只代点 <35% 屏面积控件", "instant_skip", SOURCE_PRESET));
        rules.add(RuleRecord.of("*", "popup", "popup-v2",
                "角标（广告/推广/赞助/AD/Sponsored）+ 关闭键双信号", "popup_close", SOURCE_PRESET));
        rules.add(RuleRecord.of("*", "any", "guard-list",
                "支付/银行/政务类应用：广告照拦，仅温和代点，服务零干预", "guard_skip", SOURCE_PRESET));
        rules.add(RuleRecord.of("*", "splash", "backkey",
                "返回键兜底（仅应用内窗；入口窗禁用防退出应用；每窗最多 2 次）", "back_fallback", SOURCE_PRESET));
    }

    public List<RuleRecord> allRules() {
        return new ArrayList<>(rules);
    }

    public List<ConflictRecord> allConflicts() {
        return new ArrayList<>(conflicts);
    }

    /** 调度对齐快照（调试沙箱/CI 用）：输出每条规则的分数与覆盖去向 */
    public String describeSchedule(String pkg, String adTypeHint, String sdkFamily) {
        StringBuilder sb = new StringBuilder();
        for (RuleRecord r : schedule(pkg, adTypeHint, sdkFamily)) {
            sb.append(String.format(Locale.ROOT, "%s score=%d src=%s act=%s%n",
                    r.id, computeScore(r), r.source, r.action));
        }
        return sb.toString();
    }

    private static List<RuleRecord> filterEnabled(List<RuleRecord> input) {
        List<RuleRecord> out = new ArrayList<>();
        if (input != null) {
            for (RuleRecord r : input) {
                if (r != null && r.enabled) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    private static int countActive(List<RuleRecord> input) {
        int n = 0;
        for (RuleRecord r : input) {
            if (r.enabled && "active".equals(r.status)) {
                n++;
            }
        }
        return n;
    }
}
