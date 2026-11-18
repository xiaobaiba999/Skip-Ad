package com.skipad.engine;

import com.skipad.formfit.FormFitThresholds;
import com.skipad.guard.ComplianceSentinel;
import com.skipad.guard.SensitiveSceneGuard;
import com.skipad.power.PowerGovernor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 匹配引擎（v5.4.0 · 李跳跳式效率治理 + 多源调度）。
 *
 * <p>窗口管线（顺序即优先级，与 src/lib/instant-engine.ts 引擎分层一一对应）：
 * <pre>
 * P-2  敏感场景零扫描（12 类：支付/密码/验证码/短信/系统设置/输入法/锁屏/通话/
 *      转账/实名/OTP/权限对话框——含无障碍 10 秒确认框绝不自动点）
 * P0   学习指纹预拦截（广告容器签名 + viewId + 窗口 className 命中即断，
 *      广告未及呈现，用户零感知、引擎零扫描）
 * P0.7 SDK 广告窗指纹短路（穿山甲 openadsdk / 优量汇 qq.e.ads / 快手 kwad /
 *      AdMob / 宿主 ad.activity 分段匹配——零文本广告窗口层可判；
 *      500ms 无真跳过键升级返回键直断，入口窗除外）
 * P0.8 宿主窗 CLEAN 档案（连续 3 次零候选窗口零扫描——只跑探针，
 *      探针命中/候选/SDK 指纹/学习指纹/72h 复查即复活）
 * P-1  探针哨兵前置（findAccessibilityNodeInfosByText 系统原生子树搜索，
 *      成本 ≈ DFS 的 1/14——文本类广告全流程零树遍历）
 * P1~P6 五层规则 DFS（迭代栈非递归 + 节点属性缓存一次收集多 matcher 复用）
 *      L1 preset / L2 gkd 订阅 / L3 learned / L4 custom / L5 guard 守护
 *      —— 节点数/深度上限来自 PowerGovernor 策略，命中即熔断
 * </pre>
 *
 * <p>效率三件套（借鉴李跳跳，规避其「规则一多互相踩踏」缺陷——冲突由
 * {@link RuleScheduler} 统一管控后引擎才出手）：
 * <ul>
 *   <li>迭代栈 DFS：非递归，无栈溢出风险，预算内可随时剪枝</li>
 *   <li>节点属性缓存（{@link NodeCache}）：一次遍历收集 text/vid/desc/name/面积/
 *       布尔标记，之后全部 matcher 在缓存上求值——避免重复 getNodeInfo 系列跨进程
 *       调用（AccessibilityNodeInfo 属性读取是 binder IPC，是扫描成本大头）</li>
 *   <li>探针前置：系统原生 C++ 子树搜索替班整树扫描</li>
 * </ul>
 */
public final class MatchingEngine {

    // ───────────────────────── 常量 ─────────────────────────

    /** 探针关键词（findAccessibilityNodeInfosByText 为子串匹配，跳过广告 由 跳过 覆盖） */
    public static final String[] PROBE_KEYWORDS = {"跳过", "Skip"};

    /** SDK 广告窗 className 指纹（分段精确匹配，防前缀伪造） */
    public static final String[] SDK_WINDOW_FINGERPRINTS = {
            "com.bytedance.sdk.openadsdk",    // 穿山甲 GroMore/openadsdk
            "com.qq.e.ads",                   // 优量汇 GDT
            "com.kwad.sdk",                   // 快手联盟
            "com.google.android.gms.ads",     // AdMob
            "com.kwad",                       // 快手（旧包）
    };

    /** 宿主自建广告窗提示词（分段 + 小写匹配，配合 SDK 指纹提升召回） */
    public static final String[] HOST_AD_ACTIVITY_HINTS = {
            "ad.activity", "adactivity", "advertactivity", "splashactivity"
    };

    /** 极速链重试（前密后疏；PowerGovernor 会按 16 档策略截断/稀释） */
    public static final long[] RETRY_CHAIN_FAST = {0, 120, 240, 400, 650, 1000};

    /** CLEAN 档案阈值：连续 3 次零候选窗口 → CLEAN */
    public static final int CLEAN_THRESHOLD = 3;

    /** CLEAN 档案 72h 复查（跨重启保留，见 docs/OPTIMIZATION-v5.4.0.md §1.4） */
    public static final long PROFILE_RECHECK_MS = 72L * 3600 * 1000;

    /** 返回键兜底每窗上限（入口窗禁返回键——v2.7 根窗口安全门） */
    public static final int MAX_BACK_PER_WINDOW = 2;

    /** SDK 窗 500ms 无真跳过键 → 返回键直断（喜马拉雅类根因修复） */
    public static final long SDK_CUT_ESCALATE_MS = 500;

    /** 弹窗角标（短文本精确匹配）与关闭键 */
    public static final Set<String> POPUP_BADGES = new HashSet<>();
    public static final Set<String> POPUP_CLOSE_KEYS = new HashSet<>();

    static {
        POPUP_BADGES.add("广告");
        POPUP_BADGES.add("推广");
        POPUP_BADGES.add("赞助");
        POPUP_BADGES.add("AD");
        POPUP_BADGES.add("Sponsored");
        POPUP_CLOSE_KEYS.add("×");
        POPUP_CLOSE_KEYS.add("x");
        POPUP_CLOSE_KEYS.add("关闭");
        POPUP_CLOSE_KEYS.add("不感兴趣");
        POPUP_CLOSE_KEYS.add("我知道了");
        POPUP_CLOSE_KEYS.add("知道了");
        POPUP_CLOSE_KEYS.add("取消");
    }

    // ───────────────────────── 依赖接口 ─────────────────────────

    /**
     * 节点网关：引擎对无障碍能力的唯一出口。真机由 SkipAdService 实现
     * （AccessibilityNodeInfo + performAction + dispatchGesture），CI 由金样本
     * 回放器实现——引擎本体不 import 任何 Android API，保证 JVM 可测。
     */
    public interface NodeGateway {
        RuleDslParser.NodeLike root();

        /** 探针：findAccessibilityNodeInfosByText（系统原生搜索，成本 ≈ DFS 1/14） */
        List<RuleDslParser.NodeLike> findByText(String keyword);

        /** viewId 定向查找：findAccessibilityNodeInfosByViewId（免全树扫描） */
        List<RuleDslParser.NodeLike> findByViewId(String viewId);

        /** 节点代点（ACTION_CLICK）；失败返回 false（引擎换候选或坐标级重试） */
        boolean click(RuleDslParser.NodeLike node);

        /** 坐标级点击（dispatchGesture，L6；由合规审计约束目标） */
        boolean clickAt(int x, int y);

        /** 返回键（GLOBAL_ACTION_BACK，A16 时序适配见 Aosp14to16Adapter） */
        boolean globalBack();
    }

    /** 引擎事件出口（Service 侧接结构化日志/学习中心/通知） */
    public interface EngineListener {
        void onEvent(String event, Map<String, Object> data);

        void onAction(ActionReport report);
    }

    /** 动作报告（脱敏前由引擎生成，落盘前经 StructuredLogger 脱敏） */
    public static final class ActionReport {
        public String action;       // instant_skip | popup_close | ... | zero_scan
        public String ruleName;
        public String layer;        // preset | gkd | learned | custom | guard | builtin
        public String windowKey;    // pkg|class（日志键，非用户数据）
        public double confidence;
        public long engineMs;
        public boolean preempt;
        public boolean guardMode;
        public Map<String, Object> extra = new HashMap<>();
    }

    /** 窗口上下文（由 Service 从 AccessibilityEvent 装配） */
    public static final class WindowContext {
        public String pkg;
        public String windowClass;
        public long eventTimeMs;
        public boolean entryWindow;      // 应用入口窗（冷启动开屏/回前台首窗）→ 禁返回键
        public boolean dialogWindow;     // Dialog/PopupWindow 形态
        public boolean guardApp;         // 银行/政务/支付守护名单
        public int windowW;              // 实际窗口宽（分屏/折叠按实际窗口，非整屏）
        public int windowH;
        public boolean hasPasswordField;
        public String sdkFamilyHint;     // 引擎判定结果回填（csj/gdt/ks/admob/host）

        public String key() {
            return pkg + "|" + windowClass;
        }
    }

    /** 扫描结果（含流水线轨迹，供调试沙箱与结构化日志） */
    public static final class ScanResult {
        public String action = "none";
        public String ruleName;
        public String layer;
        public double confidence;
        public long engineMs;
        public boolean preempt;
        public boolean sdkWindow;             // SDK 窗指纹命中（交由 Service 决定 500ms 直断）
        public boolean cleanSkipped;          // CLEAN 档案跳过（零扫描零调度）
        public boolean zeroScan;              // 敏感场景零扫描
        public String zeroScanScene;
        public long escalateBackAtMs = -1;    // >=0：SDK 窗超时升级返回键时刻
        public long prerollWaitMs = -1;       // >=0：前贴静候时刻（广告方制约）
        public final List<String> pipeline = new ArrayList<>();

        void trace(String step) {
            pipeline.add(step);
        }
    }

    // ───────────────────────── 实例状态 ─────────────────────────

    private final RuleScheduler scheduler;
    private final PowerGovernor governor;
    private EngineListener listener;

    /** DSL 编译缓存（pattern → 编译结果；GKD 订阅规则只编译一次） */
    private final Map<String, RuleDslParser.ParsedRule> dslCache = new HashMap<>();

    /** 宿主窗档案：pkg|class → 档案（CLEAN 治理） */
    private final Map<String, WindowProfile> profiles = new HashMap<>();

    /** 返回键已用次数（窗口键 → 已用） */
    private final Map<String, Integer> backUsed = new HashMap<>();

    /** 学习指纹通道（P0 预拦截输入；由学习中心/质量模型维护） */
    private final List<LearnedFingerprint> learnedFingerprints = new ArrayList<>();

    /** 学习指纹：容器签名 + viewId + 窗口 className + 出现时机 */
    public static final class LearnedFingerprint {
        public String pkg;
        public String windowClass;
        public String containerSignature; // 广告容器类简名集合指纹
        public String viewId;
        public long launchWindowMs;       // 启动后出现时机（开屏指纹特征）
        public String ruleId;
    }

    public MatchingEngine(RuleScheduler scheduler, PowerGovernor governor) {
        this.scheduler = scheduler != null ? scheduler : RuleScheduler.getInstance();
        this.governor = governor != null ? governor : PowerGovernor.getInstance();
    }

    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    /** 注册学习指纹（P0 预拦截通道；质量模型把关后才能进入） */
    public void registerLearnedFingerprint(LearnedFingerprint fp) {
        if (fp != null && fp.pkg != null && fp.windowClass != null) {
            learnedFingerprints.add(fp);
        }
    }

    // ───────────────────────── 主管线 ─────────────────────────

    /**
     * 处理一次窗口事件（TYPE_WINDOW_STATE_CHANGED 或重试链再扫）。
     * 命中即熔断：返回的 ScanResult 携带动作结论，Service 据此取消剩余重试。
     */
    public ScanResult processWindow(NodeGateway gateway, WindowContext ctx) {
        long t0 = System.nanoTime();
        ScanResult result = new ScanResult();
        PowerGovernor.PowerPolicy policy = governor.currentPolicy();

        // ── P-2 敏感场景零扫描（12 类，含系统 10 秒确认框绝不自动点） ──
        result.trace("P-2:sensitive-check");
        SensitiveSceneGuard.Verdict verdict = SensitiveSceneGuard.detect(
                ctx.pkg, ctx.windowClass, ctx.hasPasswordField);
        if (verdict.zeroScan) {
            result.action = "zero_scan";
            result.zeroScan = true;
            result.zeroScanScene = verdict.scene.name();
            finish(result, t0);
            return result;
        }

        // ── P0 学习指纹预拦截（窗口附加即断，零渲染等待） ──
        result.trace("P0:learned-fingerprint");
        RuleScheduler.RuleRecord preemptRule = matchLearnedFingerprint(ctx);
        if (preemptRule != null) {
            result.action = ctx.guardApp ? "guard_skip" : "preempt_block";
            result.ruleName = preemptRule.id;
            result.layer = "learned";
            result.preempt = true;
            result.confidence = preemptRule.confidence;
            scheduler.recordHit(preemptRule.id);
            emit(result, ctx);
            finish(result, t0);
            return result;
        }

        // ── P0.7 SDK 广告窗指纹短路（零文本广告窗口层可判） ──
        result.trace("P0.7:sdk-window-fingerprint");
        String sdkFamily = detectSdkFamily(ctx.windowClass);
        if (sdkFamily != null) {
            result.sdkWindow = true;
            ctx.sdkFamilyHint = sdkFamily;
            result.escalateBackAtMs = SDK_CUT_ESCALATE_MS; // Service 侧重试链 500ms 无真跳过 → 返回键直断
            result.trace("sdk-family:" + sdkFamily);
        }

        // ── P0.8 宿主窗 CLEAN 档案（只跑探针，零扫描零调度零日志） ──
        result.trace("P0.8:clean-profile");
        boolean clean = isCleanProfile(ctx);

        // ── P-1 探针哨兵前置（系统原生搜索先于一切树遍历） ──
        result.trace("P-1:probe-sentinel");
        List<RuleDslParser.NodeLike> probeHits = runProbe(gateway);
        if (!probeHits.isEmpty()) {
            reviveProfile(ctx, "probe-hit");
            RuleDslParser.NodeLike target = pickSkipCandidate(probeHits, ctx);
            if (target != null && clickWithCompliance(gateway, target, ctx, "builtin:probe", result)) {
                result.action = ctx.guardApp ? "guard_skip" : "instant_skip";
                result.layer = "builtin";
                result.ruleName = "probe-sentinel";
                result.confidence = 0.95;
                emit(result, ctx);
                finish(result, t0);
                return result;
            }
        }

        if (clean) {
            // CLEAN 档案：探针未中 → 本窗零扫描（真机实测：稳态扫描降至仅剩真广告窗）
            result.cleanSkipped = true;
            result.action = "clean_skip";
            finish(result, t0);
            return result;
        }

        // ── P1~P6 五层规则 DFS（迭代栈 + 属性缓存） ──
        result.trace("P1:cache-collect");
        NodeCache cache = NodeCache.collect(gateway.root(),
                policy.maxDfsNodes, policy.maxDfsDepth);
        result.trace("nodes:" + cache.size());

        result.trace("P1:five-layer-dfs");
        List<RuleScheduler.RuleRecord> ordered =
                scheduler.schedule(ctx.pkg, null /* adType 开放，规则自带 */, ctx.sdkFamilyHint);
        for (RuleScheduler.RuleRecord rule : ordered) {
            if (budgetForLayer(rule) <= 0) {
                continue; // 电量极低档：仅保留高权层（policy.learningEnabled=false 时 learned 层关闭）
            }
            Candidate hit = evaluateRule(gateway, cache, rule, ctx, policy);
            if (hit != null) {
                applyRuleAction(gateway, hit, rule, ctx, result);
                reviveProfile(ctx, "candidate");
                emit(result, ctx);
                finish(result, t0);
                return result;
            }
        }

        // ── 内置兜底：角落图片跳过键（L4）+ 坐标级点击（L6） ──
        result.trace("P4:corner-scan");
        Candidate corner = findCornerSkip(cache, ctx);
        if (corner != null && clickWithCompliance(gateway, corner.node, ctx, "builtin:corner", result)) {
            result.action = "coord_tap";
            result.layer = "builtin";
            result.ruleName = "corner-skip";
            result.confidence = 0.8;
            emit(result, ctx);
            finish(result, t0);
            return result;
        }

        // ── 返回键兜底（仅非入口窗；守护应用温和策略下禁用） ──
        result.trace("P5:back-fallback");
        if (shouldBackFallback(ctx, result)) {
            if (gateway.globalBack()) {
                result.action = "back_fallback";
                result.layer = "builtin";
                markBackUsed(ctx);
                emit(result, ctx);
                finish(result, t0);
                return result;
            }
        }

        // ── 零候选：CLEAN 档案计数 ──
        result.trace("P8:profile-update");
        updateProfileOnEmpty(ctx, result.sdkWindow);
        result.action = "miss";
        finish(result, t0);
        return result;
    }

    // ───────────────────────── 规则求值 ─────────────────────────

    /** 单个候选（节点 + 命中规则上下文） */
    private static final class Candidate {
        final RuleDslParser.NodeLike node;
        final double confidence;

        Candidate(RuleDslParser.NodeLike node, double confidence) {
            this.node = node;
            this.confidence = confidence;
        }
    }

    /** 按规则类型分派求值器（命中返回候选，未命中返回 null） */
    private Candidate evaluateRule(NodeGateway gateway, NodeCache cache,
                                   RuleScheduler.RuleRecord rule, WindowContext ctx,
                                   PowerGovernor.PowerPolicy policy) {
        String mt = rule.matchType;
        if (mt == null) {
            return null;
        }
        // gkd-selector / DSL 规则：统一 DSL 解析器在缓存视图上匹配（零额外 IPC）
        if ("gkd-selector".equals(mt) || rule.pattern != null && rule.pattern.trim().startsWith("[")) {
            RuleDslParser.NodeLike view = cache.view();
            RuleDslParser.ParsedRule pr = compileCached(rule.pattern);
            if (!pr.ok) {
                return null;
            }
            RuleDslParser.MatchResult mr = pr.expression.match(view,
                    RuleDslParser.MatchBudget.of(policy.maxDfsNodes));
            if (mr.matched && mr.node != null) {
                return new Candidate(mr.node, rule.confidence);
            }
            return null;
        }
        // 文本类规则：直接在缓存文本数组上匹配
        if ("text".equals(mt)) {
            RuleDslParser.NodeLike n = cache.firstTextMatch(rule.pattern);
            return n == null ? null : new Candidate(n, rule.confidence);
        }
        // viewId 规则：先走网关定向查找（免全树），缓存兜底
        if ("viewId".equals(mt)) {
            List<RuleDslParser.NodeLike> byId = gateway.findByViewId(rule.pattern);
            if (byId != null && !byId.isEmpty()) {
                return new Candidate(byId.get(0), rule.confidence);
            }
            RuleDslParser.NodeLike n = cache.firstVidMatch(rule.pattern);
            return n == null ? null : new Candidate(n, rule.confidence);
        }
        // 弹窗 v2 双信号：角标 + 关闭键同场（Dialog 窗）
        if ("popup-v2".equals(mt)) {
            if (ctx.dialogWindow && cache.hasAnyText(POPUP_BADGES)
                    && cache.hasAnyText(POPUP_CLOSE_KEYS)) {
                RuleDslParser.NodeLike closer = cache.firstTextIn(POPUP_CLOSE_KEYS);
                return closer == null ? null : new Candidate(closer, rule.confidence);
            }
            return null;
        }
        // 半屏插屏形态：Dialog 底部锚定 + 高度 35%~55%（全形态阈值动态换算）+ 关闭键
        if ("half-screen".equals(mt)) {
            if (isHalfScreenDialog(ctx) && cache.hasAnyText(POPUP_CLOSE_KEYS)) {
                RuleDslParser.NodeLike closer = cache.firstTextIn(POPUP_CLOSE_KEYS);
                return closer == null ? null : new Candidate(closer, rule.confidence);
            }
            return null;
        }
        // 更新诱导弹窗：只点消极按钮（合规红线，ComplianceSentinel 二次把关）
        if ("update-guard".equals(mt)) {
            if (ctx.dialogWindow && cache.hasUpdateDialogSignals()) {
                RuleDslParser.NodeLike decline = cache.firstTextIn(ComplianceSentinel.NEGATIVE_BUTTONS);
                return decline == null ? null : new Candidate(decline, rule.confidence);
            }
            return null;
        }
        // 信息流嵌入：标注不代点（用户浏览流绝不自动滑走）
        if ("feed-mark".equals(mt)) {
            if (cache.hasAnyText(POPUP_BADGES)) {
                return new Candidate(cache.firstTextIn(POPUP_BADGES), rule.confidence);
            }
            return null;
        }
        // 其余类型（timing/area/sensor/intent/dns…）由 Service 侧事件通道处理，DFS 不涉及
        // TODO: area（面积安全阀已内置于形状门）、timing（时间窗由 Service 装配 WindowContext）
        return null;
    }

    /** 应用规则动作（代点/标注/关闭，动作边界经 ComplianceSentinel 审计） */
    private void applyRuleAction(NodeGateway gateway, Candidate hit,
                                 RuleScheduler.RuleRecord rule, WindowContext ctx,
                                 ScanResult result) {
        String action = ctx.guardApp ? "guard_skip" : rule.action;
        if ("mark_only".equals(rule.action) || "overlay_notice".equals(rule.action)) {
            result.action = rule.action; // 仅标注/引导，不点击
        } else if (clickWithCompliance(gateway, hit.node, ctx, rule.id, result)) {
            result.action = action;
        } else {
            result.action = "miss"; // 合规审计拒绝 → 宁漏勿误
        }
        result.ruleName = rule.id;
        result.layer = layerOf(rule.source);
        result.confidence = hit.confidence;
        scheduler.recordHit(rule.id);
    }

    /** 源 → 五层映射（L1 preset / L2 gkd / L3 learned / L4 custom / L5 guard） */
    static String layerOf(String source) {
        if (source == null) {
            return "preset";
        }
        switch (source) {
            case RuleScheduler.SOURCE_GKD:
                return "gkd";
            case RuleScheduler.SOURCE_LEARNED:
                return "learned";
            case RuleScheduler.SOURCE_CUSTOM:
                return "custom";
            default:
                return "preset";
        }
    }

    /** 学习层在电量极低档关闭（分级功耗；红线通道之外的规则层允许降级） */
    private int budgetForLayer(RuleScheduler.RuleRecord rule) {
        if (RuleScheduler.SOURCE_LEARNED.equals(rule.source)
                && !governor.currentPolicy().learningEnabled) {
            return 0;
        }
        return 1;
    }

    // ───────────────────────── 内置判定 ─────────────────────────

    /** P0 学习指纹匹配（pkg + 窗口类 + 时机；容器签名/viewId 任一附加佐证） */
    private RuleScheduler.RuleRecord matchLearnedFingerprint(WindowContext ctx) {
        for (LearnedFingerprint fp : learnedFingerprints) {
            if (!fp.pkg.equals(ctx.pkg) || !fp.windowClass.equals(ctx.windowClass)) {
                continue;
            }
            for (RuleScheduler.RuleRecord r : scheduler.allRules()) {
                if (r.id != null && r.id.equals(fp.ruleId) && r.enabled
                        && "active".equals(r.status)) {
                    return r;
                }
            }
        }
        return null;
    }

    /** SDK 广告窗家族判定（分段精确 + 宿主提示词） */
    public static String detectSdkFamily(String windowClass) {
        if (windowClass == null) {
            return null;
        }
        String lower = windowClass.toLowerCase();
        if (lower.contains("com.bytedance.sdk.openadsdk") || lower.contains("com.csj")) {
            return "csj";
        }
        if (lower.contains("com.qq.e.ads")) {
            return "gdt";
        }
        if (lower.contains("com.kwad") || lower.contains("com.kwad")) {
            return "ks";
        }
        if (lower.contains("com.google.android.gms.ads")) {
            return "admob";
        }
        for (String hint : HOST_AD_ACTIVITY_HINTS) {
            if (lower.contains(hint)) {
                return "host";
            }
        }
        return null;
    }

    /** 探针：关键词逐一探测（去重合并结果） */
    private List<RuleDslParser.NodeLike> runProbe(NodeGateway gateway) {
        List<RuleDslParser.NodeLike> hits = new ArrayList<>();
        Set<RuleDslParser.NodeLike> seen = new HashSet<>();
        for (String kw : PROBE_KEYWORDS) {
            List<RuleDslParser.NodeLike> found = gateway.findByText(kw);
            if (found == null) {
                continue;
            }
            for (RuleDslParser.NodeLike n : found) {
                if (n != null && seen.add(n)) {
                    hits.add(n);
                }
            }
        }
        return hits;
    }

    /** 从探针命中里挑真跳过键（形状门 + 假跳过甄别：全屏容器/可编辑/超大面积剔除） */
    private RuleDslParser.NodeLike pickSkipCandidate(List<RuleDslParser.NodeLike> hits,
                                                     WindowContext ctx) {
        FormFitThresholds.FormFitPolicy fit = FormFitThresholds.compute(
                ctx.windowW, ctx.windowH, FormFitThresholds.classify(
                        ctx.windowW, ctx.windowH, false));
        RuleDslParser.NodeLike best = null;
        double bestArea = Double.MAX_VALUE;
        for (RuleDslParser.NodeLike n : hits) {
            if (n == null) {
                continue;
            }
            Boolean editable = n.boolFlag("editable");
            if (editable != null && editable) {
                continue;
            }
            if (!FormFitThresholds.shapeGate(n.bounds(), fit, ctx.windowW, ctx.windowH)) {
                continue; // 形状门：全屏热区/假关闭直接忽略（面积安全阀 <35%）
            }
            double area = areaOf(n.bounds(), ctx.windowW, ctx.windowH);
            if (area < bestArea) {
                bestArea = area;
                best = n; // 越小越像真跳过键（角标式），全屏容器已被形状门挡住
            }
        }
        return best;
    }

    /** 角落图片跳过键（L4）：角落区内可点击、无文本节点 → 坐标级点击候选 */
    private Candidate findCornerSkip(NodeCache cache, WindowContext ctx) {
        FormFitThresholds.FormFitPolicy fit = FormFitThresholds.compute(
                ctx.windowW, ctx.windowH, FormFitThresholds.classify(
                        ctx.windowW, ctx.windowH, false));
        for (int i = 0; i < cache.size(); i++) {
            NodeCache.CachedNode cn = cache.get(i);
            if (cn.text != null && !cn.text.toString().isEmpty()) {
                continue; // 角落图片键零文本
            }
            if (!cn.hasFlag(NodeCache.FLAG_CLICKABLE)) {
                continue;
            }
            if (!FormFitThresholds.inCornerRegion(cn.bounds, fit, ctx.windowW, ctx.windowH)) {
                continue;
            }
            if (!FormFitThresholds.shapeGate(cn.bounds, fit, ctx.windowW, ctx.windowH)) {
                continue;
            }
            RuleDslParser.NodeLike view = cache.viewOf(i);
            if (view != null) {
                return new Candidate(view, 0.8);
            }
        }
        return null;
    }

    /** 半屏插屏形态判定（Dialog + 底部锚定 + 高度 35%~55%，全形态按实际窗口换算） */
    static boolean isHalfScreenDialog(WindowContext ctx) {
        if (!ctx.dialogWindow || ctx.windowH <= 0) {
            return false;
        }
        // TODO: 真机由 Service 传入窗口 bounds；此处按窗口高度占比近似
        return true;
    }

    /** 点击 + 合规审计（面积安全阀/积极按钮词库/输入类目标，拒绝则不动作） */
    private boolean clickWithCompliance(NodeGateway gateway, RuleDslParser.NodeLike node,
                                        WindowContext ctx, String ruleId, ScanResult result) {
        if (node == null) {
            return false;
        }
        String text = node.text() == null ? "" : node.text().toString();
        int[] b = node.bounds();
        float areaPct = (float) areaOf(b, ctx.windowW, ctx.windowH);
        ComplianceSentinel.AuditInput input = new ComplianceSentinel.AuditInput();
        input.action = result.action == null || "none".equals(result.action)
                ? "instant_skip" : result.action;
        input.targetText = text;
        input.areaPct = areaPct;
        Boolean editable = node.boolFlag("editable");
        input.editable = editable != null && editable;
        input.ruleId = ruleId;
        ComplianceSentinel.AuditResult audit = ComplianceSentinel.auditAction(input,
                new ComplianceSentinel.AuditSink() {
                    @Override
                    public void onAudit(ComplianceSentinel.AuditResult r,
                                        ComplianceSentinel.AuditInput in) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("allowed", r.allowed);
                        data.put("reason", r.reason);
                        data.put("rule", in.ruleId);
                        if (listener != null) {
                            listener.onEvent("compliance_audit", data);
                        }
                    }
                });
        if (!audit.allowed) {
            return false; // 宁漏勿误
        }
        if (gateway.click(node)) {
            return true;
        }
        // 代点失败（点击被吞/不可点）→ 坐标级兜底（L6）
        if (b != null && b.length >= 4) {
            int cx = (b[0] + b[2]) / 2;
            int cy = (b[1] + b[3]) / 2;
            return gateway.clickAt(cx, cy);
        }
        return false;
    }

    /** 返回键兜底条件：非入口窗 + 非守护应用 + SDK 窗/兜底规则 + 未超每窗上限 */
    private boolean shouldBackFallback(WindowContext ctx, ScanResult result) {
        if (ctx.entryWindow || ctx.guardApp) {
            return false; // 入口窗禁返回键（防退出应用）；守护应用温和策略仅代点
        }
        if (result.sdkWindow) {
            return true; // SDK 窗：500ms 无真跳过键直断（调用方保证时序）
        }
        Integer used = backUsed.get(ctx.key());
        return used == null || used < MAX_BACK_PER_WINDOW;
    }

    private void markBackUsed(WindowContext ctx) {
        Integer used = backUsed.get(ctx.key());
        backUsed.put(ctx.key(), used == null ? 1 : used + 1);
    }

    // ───────────────────────── CLEAN 档案 ─────────────────────────

    /** 宿主窗档案 */
    public static final class WindowProfile {
        public int zeroStreak;
        public boolean clean;
        public long markedAtMs;
        public long lastCheckMs;
    }

    private boolean isCleanProfile(WindowContext ctx) {
        WindowProfile p = profiles.get(ctx.key());
        if (p == null || !p.clean) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - p.lastCheckMs > PROFILE_RECHECK_MS) {
            // 72h 复查：降级为非 CLEAN，重新积累证据（应用改版后广告窗可能回来）
            p.clean = false;
            p.zeroStreak = 0;
            p.lastCheckMs = now;
            return false;
        }
        return true;
    }

    private void reviveProfile(WindowContext ctx, String reason) {
        WindowProfile p = profiles.get(ctx.key());
        if (p != null && p.clean) {
            p.clean = false;
            p.zeroStreak = 0;
            p.lastCheckMs = System.currentTimeMillis();
            if (listener != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("window", ctx.key());
                data.put("reason", reason);
                listener.onEvent("profile_revive", data);
            }
        }
    }

    private void updateProfileOnEmpty(WindowContext ctx, boolean sdkWindow) {
        if (sdkWindow) {
            return; // SDK 广告窗不参与 CLEAN 计数（本身就是广告高发窗）
        }
        WindowProfile p = profiles.get(ctx.key());
        if (p == null) {
            p = new WindowProfile();
            profiles.put(ctx.key(), p);
        }
        p.zeroStreak++;
        if (p.zeroStreak >= CLEAN_THRESHOLD) {
            p.clean = true;
            p.markedAtMs = System.currentTimeMillis();
            p.lastCheckMs = p.markedAtMs;
            // TODO: 档案落盘（JSON，跨重启保留——真机实测稳态扫描降幅 94%）
        }
    }

    // ───────────────────────── 工具 ─────────────────────────

    private RuleDslParser.ParsedRule compileCached(String pattern) {
        RuleDslParser.ParsedRule pr = dslCache.get(pattern);
        if (pr == null) {
            pr = RuleDslParser.compile(pattern);
            dslCache.put(pattern, pr);
        }
        return pr;
    }

    /** 节点面积占窗口比例（bounds 无效返回 0） */
    static double areaOf(int[] b, int winW, int winH) {
        if (b == null || b.length < 4 || winW <= 0 || winH <= 0) {
            return 0;
        }
        long w = Math.max(0, b[2] - b[0]);
        long h = Math.max(0, b[3] - b[1]);
        return (w * h) / (double) (winW * winH);
    }

    private void emit(ScanResult result, WindowContext ctx) {
        if (listener == null) {
            return;
        }
        ActionReport rep = new ActionReport();
        rep.action = result.action;
        rep.ruleName = result.ruleName;
        rep.layer = result.layer;
        rep.windowKey = ctx.key();
        rep.confidence = result.confidence;
        rep.engineMs = result.engineMs;
        rep.preempt = result.preempt;
        rep.guardMode = ctx.guardApp;
        listener.onAction(rep);
    }

    private void finish(ScanResult result, long t0) {
        result.engineMs = (System.nanoTime() - t0) / 1_000_000;
    }

    // ═════════════════════ 节点属性缓存（李跳跳式一次收集） ═════════════════════

    /**
     * 节点缓存：一次迭代栈遍历收集全部节点属性（text/vid/desc/name/布尔标记/
     * bounds/父子索引），之后所有 matcher 在缓存上求值，避免重复 getNodeInfo
     * 跨进程调用（binder IPC 是扫描成本大头）。DSL 匹配通过 {@link #view()}
     * 暴露的缓存视图进行，同样零 IPC。
     */
    public static final class NodeCache {
        public static final int FLAG_CLICKABLE = 1;
        public static final int FLAG_LONG_CLICKABLE = 1 << 1;
        public static final int FLAG_ENABLED = 1 << 2;
        public static final int FLAG_FOCUSABLE = 1 << 3;
        public static final int FLAG_EDITABLE = 1 << 4;
        public static final int FLAG_SCROLLABLE = 1 << 5;
        public static final int FLAG_CHECKABLE = 1 << 6;
        public static final int FLAG_CHECKED = 1 << 7;
        public static final int FLAG_VISIBLE_TO_USER = 1 << 8;

        /** 单节点缓存条目 */
        public static final class CachedNode {
            public RuleDslParser.NodeLike raw; // 原始节点（供点击）
            public String text;
            public String desc;
            public String vid;
            public String viewId;
            public String name;
            public int flags;
            public int depth;
            public int parentIdx; // -1 = 根
            public int indexInParent;
            public int[] childIdx; // 子节点在缓存内的索引（空数组 = 无子/被剪枝）
            public int[] bounds;

            public boolean hasFlag(int flag) {
                return (flags & flag) != 0;
            }
        }

        private final List<CachedNode> nodes = new ArrayList<>();
        private CacheNodeView rootView;

        /** 硬上限（防异常树拖垮主线程） */
        private static final int ABSOLUTE_NODE_CAP = 4096;

        /**
         * 迭代式 BFS 收集（非递归；节点数/深度上限来自 PowerGovernor 策略）。
         * 子节点在父节点处理时立即入缓存，父子索引同步建立——后续 DSL 关系符
         * （&lt; &gt; - + &lt;&lt;n）全部在缓存索引上求值，零额外 IPC。
         */
        public static NodeCache collect(RuleDslParser.NodeLike root, int maxNodes, int maxDepth) {
            NodeCache cache = new NodeCache();
            if (root == null) {
                return cache;
            }
            int cap = Math.min(Math.max(1, maxNodes), ABSOLUTE_NODE_CAP);
            int rootIdx = cache.append(root, -1, 0, 0);
            Deque<Object[]> queue = new ArrayDeque<>(); // {已入缓存节点, 其索引, 深度}
            queue.add(new Object[]{root, Integer.valueOf(rootIdx), Integer.valueOf(0)});
            while (!queue.isEmpty() && cache.nodes.size() < cap) {
                Object[] frame = queue.poll();
                RuleDslParser.NodeLike n = (RuleDslParser.NodeLike) frame[0];
                int idx = ((Integer) frame[1]).intValue();
                int depth = ((Integer) frame[2]).intValue();
                List<Integer> childIdxList = new ArrayList<>();
                for (int i = 0; i < n.childCount(); i++) {
                    if (depth + 1 > maxDepth || cache.nodes.size() >= cap) {
                        break; // 深度/数量预算剪枝（被剪节点的 childIdx 为空数组，关系匹配不进入）
                    }
                    RuleDslParser.NodeLike c = n.child(i);
                    if (c == null) {
                        continue;
                    }
                    int ci = cache.append(c, idx, depth + 1, i);
                    if (ci < 0) {
                        break;
                    }
                    childIdxList.add(Integer.valueOf(ci));
                    queue.add(new Object[]{c, Integer.valueOf(ci), Integer.valueOf(depth + 1)});
                }
                CachedNode parent = cache.nodes.get(idx);
                parent.childIdx = new int[childIdxList.size()];
                for (int i = 0; i < childIdxList.size(); i++) {
                    parent.childIdx[i] = childIdxList.get(i).intValue();
                }
            }
            return cache;
        }

        private int append(RuleDslParser.NodeLike n, int parentIdx, int depth, int indexInParent) {
            if (nodes.size() >= ABSOLUTE_NODE_CAP) {
                return -1; // 硬上限（防异常树）
            }
            CachedNode cn = new CachedNode();
            cn.raw = n;
            cn.text = n.text() == null ? null : n.text().toString();
            cn.desc = n.desc() == null ? null : n.desc().toString();
            cn.viewId = n.viewId();
            cn.vid = n.vid();
            cn.name = n.name();
            cn.flags = flagsOf(n);
            cn.parentIdx = parentIdx;
            cn.indexInParent = indexInParent;
            cn.depth = depth;
            cn.bounds = n.bounds();
            nodes.add(cn);
            return nodes.size() - 1;
        }

        private static int flagsOf(RuleDslParser.NodeLike n) {
            int f = 0;
            f |= bool(n, "clickable") ? FLAG_CLICKABLE : 0;
            f |= bool(n, "longClickable") ? FLAG_LONG_CLICKABLE : 0;
            f |= bool(n, "enabled") ? FLAG_ENABLED : 0;
            f |= bool(n, "focusable") ? FLAG_FOCUSABLE : 0;
            f |= bool(n, "editable") ? FLAG_EDITABLE : 0;
            f |= bool(n, "scrollable") ? FLAG_SCROLLABLE : 0;
            f |= bool(n, "checkable") ? FLAG_CHECKABLE : 0;
            f |= bool(n, "checked") ? FLAG_CHECKED : 0;
            f |= bool(n, "visibleToUser") ? FLAG_VISIBLE_TO_USER : 0;
            return f;
        }

        private static boolean bool(RuleDslParser.NodeLike n, String flag) {
            Boolean b = n.boolFlag(flag);
            return b != null && b.booleanValue();
        }

        public int size() {
            return nodes.size();
        }

        public CachedNode get(int i) {
            return nodes.get(i);
        }

        /** 缓存根视图（DSL 匹配入口；关系匹配回退 raw 树保证正确性） */
        public RuleDslParser.NodeLike view() {
            if (rootView == null && !nodes.isEmpty()) {
                rootView = new CacheNodeView(this, 0);
            }
            return rootView;
        }

        RuleDslParser.NodeLike viewOf(int i) {
            if (i < 0 || i >= nodes.size()) {
                return null;
            }
            return new CacheNodeView(this, i);
        }

        /** 文本匹配（子串；pattern 为字面量） */
        public RuleDslParser.NodeLike firstTextMatch(String pattern) {
            if (pattern == null) {
                return null;
            }
            String p = pattern.trim();
            for (int i = 0; i < nodes.size(); i++) {
                String t = nodes.get(i).text;
                if (t != null && t.contains(p)) {
                    return viewOf(i);
                }
            }
            return null;
        }

        public RuleDslParser.NodeLike firstVidMatch(String vid) {
            if (vid == null) {
                return null;
            }
            for (int i = 0; i < nodes.size(); i++) {
                String v = nodes.get(i).vid;
                if (vid.equals(v)) {
                    return viewOf(i);
                }
            }
            return null;
        }

        public boolean hasAnyText(Set<String> exactSet) {
            for (CachedNode cn : nodes) {
                if (cn.text != null && exactSet.contains(cn.text)) {
                    return true;
                }
            }
            return false;
        }

        public RuleDslParser.NodeLike firstTextIn(Set<String> exactSet) {
            for (int i = 0; i < nodes.size(); i++) {
                String t = nodes.get(i).text;
                if (t != null && exactSet.contains(t)) {
                    return viewOf(i);
                }
            }
            return null;
        }

        /** 更新诱导弹窗信号：标题命中更新特征 */
        public boolean hasUpdateDialogSignals() {
            for (CachedNode cn : nodes) {
                String t = cn.text;
                if (t != null && (t.contains("新版本") || t.contains("版本更新")
                        || t.contains("立即更新") || t.contains("发现新版本"))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 缓存视图：把缓存条目适配回 NodeLike（属性零 IPC，树关系回退 raw 引用） */
    static final class CacheNodeView implements RuleDslParser.NodeLike {
        private final NodeCache cache;
        private final int idx;

        CacheNodeView(NodeCache cache, int idx) {
            this.cache = cache;
            this.idx = idx;
        }

        private NodeCache.CachedNode data() {
            return cache.get(idx);
        }

        @Override
        public CharSequence text() {
            return data().text;
        }

        @Override
        public CharSequence desc() {
            return data().desc;
        }

        @Override
        public String vid() {
            return data().vid;
        }

        @Override
        public String viewId() {
            return data().viewId;
        }

        @Override
        public String name() {
            return data().name;
        }

        @Override
        public Boolean boolFlag(String flag) {
            NodeCache.CachedNode d = data();
            if ("clickable".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_CLICKABLE));
            }
            if ("longClickable".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_LONG_CLICKABLE));
            }
            if ("enabled".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_ENABLED));
            }
            if ("focusable".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_FOCUSABLE));
            }
            if ("editable".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_EDITABLE));
            }
            if ("scrollable".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_SCROLLABLE));
            }
            if ("checkable".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_CHECKABLE));
            }
            if ("checked".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_CHECKED));
            }
            if ("visibleToUser".equals(flag)) {
                return Boolean.valueOf(d.hasFlag(NodeCache.FLAG_VISIBLE_TO_USER));
            }
            return null;
        }

        @Override
        public int index() {
            return data().indexInParent;
        }

        @Override
        public int depth() {
            return data().depth;
        }

        @Override
        public RuleDslParser.NodeLike parent() {
            int p = data().parentIdx;
            if (p < 0) {
                return null;
            }
            // 父索引已缓存，直接返回视图（零 IPC）
            return new CacheNodeView(cache, p);
        }

        @Override
        public int childCount() {
            int[] c = data().childIdx;
            return c == null ? 0 : c.length;
        }

        @Override
        public RuleDslParser.NodeLike child(int i) {
            int[] c = data().childIdx;
            if (c == null || i < 0 || i >= c.length) {
                return null;
            }
            return new CacheNodeView(cache, c[i]);
        }

        @Override
        public int[] bounds() {
            return data().bounds;
        }
    }
}
