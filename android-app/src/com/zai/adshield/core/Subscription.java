package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * GKD 订阅模型与解析（v4.0.0 订阅融合 · v4.2.0 全门控 · v4.3.0 globalGroups 主力规则接入）
 * ─────────────────────────────────────────────────────────────────────
 * v5.1.0（issue#46/47 实锤：美团 DspActivity/DynamicDialog 整窗未命中 +
 * 非主流 App 无专属规则）：
 *  · 内置规则库 54 → 102 款：美团系专项（DspActivity 开屏窗 + DynamicDialog
 *    广告弹窗，activityIds 限定下放行 close 族 vid）+ 46 款热门应用
 *    （银行金融 9 / 出行旅行 7 / 阅读漫画 6 / 新闻资讯 4 / 效率工具 7 /
 *    生活服务 7 / 学习办公 4 / 音乐社交 6）——包名精确匹配，错误包名 =
 *    永不生效的死规则，零误点面；
 *  · 订阅 id/version 升级（20260510/6）。
 * 订阅 = GKD 生态规则的最小载体：apps[] → groups[] → rules[] →
 * matches（选择器）。本地内置订阅与远程导入订阅格式完全一致
 * （JSON5），加载时全部预编译为 GkdSelector——匹配热路径零解析。
 *
 * v4.3.0（issue#38 实锤：远程订阅 327 款/828 条命中 0——GKD 默认订阅的
 * 【主力开屏规则全部住在 globalGroups】里（对所有应用生效的全局组，
 * 如「开屏广告」组 [text*="跳过"][text.length<10]），而旧解析器只读
 * apps 数组——主力规则被整体丢弃，CSDN 等第三方 App 全部漏跳的元凶）：
 *  · globalGroups 全量解析——每组编译为 id='*' 的全局 App，其 apps
 *    数组是【包名排除表】（enable:false 的应用不适用该全局组——
 *    它们有自己的专属规则）；组级 enable:false 整组跳过；
 *  · 组级字段落地：enable（组停用，如 CSDN 的「更新弹窗」组）/
 *    matchTime（进入窗口 N ms 内才激活，主力组 10000ms）/
 *    actionMaximum（组级默认次数）/ actionCd（同规则出手间隔）；
 *    规则级字段优先于组级；
 *  · preKeys 链式规则（依赖前序规则命中才激活）不支持——加载即跳过；
 *  · rules 字符串快捷式（rules:'[id=…]'）支持；
 *  · 安全红线：全局组（id='*'）无 activityIds 限定的规则，目标节点
 *    文案仍过主引擎诱导词否决（「摇一摇领福利」类绝不代点）。
 *
 * v4.2.0（issue#31-37 日志实锤：远程订阅的微信规则在主界面反复命中
 * 连点 = 「进入微信左右乱划」元凶；订阅导入后弹窗场景永不命中）：
 *  · activityIds / excludeActivityIds 全量解析并强校验——GKD 社区规则
 *    的 Activity 限定此前被整体忽略（朋友圈规则在 LauncherUI 也能命中），
 *    现在非目标 Activity 的规则在扫描前直接跳过（零树遍历，功耗同降）；
 *  · 无 activityIds 限定的「任意界面」规则加 skip 形态门——目标节点的
 *    text/desc/vid 必须是跳过/关闭形态，微信底部 Tab 等普通控件绝不代点；
 *  · actionMaximum（默认 1 次/窗口）与 actionInterval（默认 1000ms）
 *    按 GKD 语义落地——同窗同一规则绝不机关枪连点；
 *  · 单遍树扫描：全部候选规则共享一次 DFS + 节点预算（此前每条选择器
 *    独立遍历全树，20 条规则 = 20 次整树遍历）。
 *
 * 安全红线（与主引擎一致）：
 *  · 只支持 click / clickNode 动作（back 类规则在加载时直接丢弃
 *    ——链式拦截已整体移除，订阅规则也绝不能触发返回键）；
 *  · 匹配命中的目标节点仍须过主引擎安全闸（可见 + 面积 ≤35% 屏
 *    + 非空 bounds）；
 *  · 解析失败的规则跳过、失败的订阅整体拒绝——绝不影响主引擎。
 */
public final class Subscription {

    /* ══════════ 模型 ══════════ */

    public static final class Rule {
        public List<GkdSelector> targets = new ArrayList<GkdSelector>();
        public List<GkdSelector> excludes = new ArrayList<GkdSelector>();
        public boolean clickNode;      // true=performAction 点击控件本身
        public String name;            // 日志标识（group 名 + 序号）
        /* v4.2.0 GKD 规范字段 */
        public String[] activityIds = EMPTY_STRS;         // 空 = 任意 Activity
        public String[] excludeActivityIds = EMPTY_STRS;
        public int actionMaximum;      // 同窗最多出手次数（0 = 不限，走全局预算）
        public long actionInterval;    // 同规则两次出手最小间隔 ms（0 = 不限）
        public long matchTime;         // v4.3.0：进入窗口 N ms 内才激活（0 = 不限）
        /** v4.3.0：结构规则——目标选择器含关系算子（@View <3 FL < [vid="ad"]
         *  类链式定位）。结构锚定 = 规则作者精确绑定了容器层级，比裸文案
         *  匹配强得多——零文本图片跳过键（无 skip 形态文案）凭此过安全门。 */
        public boolean structural;
    }

    static final String[] EMPTY_STRS = new String[0];

    public static final class App {
        public String id;              // 包名（'*' = 全局规则）
        public String name;
        public List<Rule> rules = new ArrayList<Rule>();
        /** v4.3.0：全局组的包名排除表（globalGroups.apps 里 enable:false 的应用） */
        public HashSet<String> excludePkgs;
    }

    public final String name;
    public final int version;
    public final List<App> apps = new ArrayList<App>();

    private Subscription(String name, int version) {
        this.name = name;
        this.version = version;
    }

    /* ══════════ 运行时限流（SubscriptionManager 注入，纯 Java 可单测） ══════════
     * actionMaximum / actionInterval 的出手记账。key = 包名|规则名|窗口。 */

    public interface Runtime {
        /** 是否允许本次出手（间隔/次数双校验） */
        boolean allowFire(String key, int actionMaximum, long actionInterval);

        /** 记账一次出手 */
        void onFired(String key);
    }

    /* ══════════ 解析（JSON5 → 模型，选择器全部预编译） ══════════ */

    public static Subscription parse(String json5Text) {
        return parse(json5Text, 1, 1000);
    }

    /** v4.2.0：带默认限流参数解析（内置订阅 0/0=依赖全局预算与冷却；
     *  远程订阅按 GKD 语义默认 1 次/窗 + 1000ms 间隔）。
     *  v4.3.0：globalGroups 主力规则全量接入（见类注释）。 */
    public static Subscription parse(String json5Text, int defActionMaximum, long defActionInterval) {
        Object root = Json5.parse(json5Text);
        Map0 m = new Map0(Json5.asMap(root));
        String name = m.str("name", "未命名订阅");
        int version = (int) m.num("version", 0);
        Subscription sub = new Subscription(name, version);

        /* ═══ v4.3.0 globalGroups：GKD 默认订阅的主力规则住处 ═══
         * 每组 = 对所有应用生效的全局规则（id='*'），组内 apps 数组是
         * 包名排除表（enable:false——这些应用有自己的专属规则）。
         * 典型：「开屏广告」组 [text*="跳过"][text.length<10] 对全部
         * 第三方 App 生效——旧解析器只读 apps 数组，主力规则被整体丢弃。 */
        List<Object> globalGroups = Json5.asList(m.raw.get("globalGroups"));
        if (globalGroups != null) {
            for (int gi = 0; gi < globalGroups.size(); gi++) {
                Map0 gm = new Map0(Json5.asMap(globalGroups.get(gi)));
                // v4.3.0：组级停用（GKD 语义 enable:false）
                if (gm.raw.get("enable") != null && !gm.bool("enable", true)) continue;
                App g = new App();
                g.id = "*";
                g.name = gm.str("name", "全局规则");
                g.excludePkgs = parseGlobalExcludes(gm.raw.get("apps"));
                long gMatchTime = (long) gm.num("matchTime", 0);
                int gMax = (int) gm.num("actionMaximum", defActionMaximum);
                long gInterval = (long) gm.num("actionCd", defActionInterval);
                appendGroupRules(gm, g, gMax, gInterval, gMatchTime);
                if (!g.rules.isEmpty()) sub.apps.add(g);
            }
        }

        /* ═══ apps：每应用专属规则（v4.2.0 语义 + v4.3.0 组级字段）═══ */
        List<Object> apps = Json5.asList(m.raw.get("apps"));
        if (apps != null) {
            for (int i = 0; i < apps.size(); i++) {
                Map0 am = new Map0(Json5.asMap(apps.get(i)));
                String appId = am.str("id", null);
                if (appId == null || appId.length() == 0) continue;
                App app = new App();
                app.id = appId;
                app.name = am.str("name", appId);
                List<Object> groups = Json5.asList(am.raw.get("groups"));
                if (groups != null) {
                    for (int g = 0; g < groups.size(); g++) {
                        Map0 gm = new Map0(Json5.asMap(groups.get(g)));
                        // v4.3.0：组级停用（CSDN「更新弹窗」等默认 enable:false）
                        if (gm.raw.get("enable") != null && !gm.bool("enable", true)) continue;
                        long gMatchTime = (long) gm.num("matchTime", 0);
                        int gMax = (int) gm.num("actionMaximum", defActionMaximum);
                        long gInterval = (long) gm.num("actionCd", defActionInterval);
                        appendGroupRules(gm, app, gMax, gInterval, gMatchTime);
                    }
                }
                if (!app.rules.isEmpty()) sub.apps.add(app);
            }
        }
        return sub;
    }

    /** v4.3.0：globalGroups.apps 排除表——enable:false 的包名集合
     *  （这些应用有专属规则，全局组不适用；GKD 语义：未列出 = 默认适用） */
    static HashSet<String> parseGlobalExcludes(Object appsObj) {
        HashSet<String> out = new HashSet<String>();
        List<Object> list = Json5.asList(appsObj);
        if (list == null) return out;
        for (int i = 0; i < list.size(); i++) {
            Map0 am = new Map0(Json5.asMap(list.get(i)));
            String id = am.str("id", null);
            if (id == null || id.length() == 0) continue;
            if (!am.bool("enable", true)) out.add(id);
        }
        return out;
    }

    /** v4.3.0：一组 rules → 编译进 App（组级默认 + 规则级覆盖；preKeys 跳过） */
    static void appendGroupRules(Map0 gm, App app, int defMax, long defInterval, long defMatchTime) {
        String gname = gm.str("name", "规则组");
        List<Object> rules = normalizeRules(gm.raw.get("rules"));
        if (rules == null) return;
        for (int r = 0; r < rules.size(); r++) {
            Rule compiled = compileRule(Json5.asMap(rules.get(r)),
                    gname + "-" + (r + 1), defMax, defInterval, defMatchTime);
            if (compiled != null) app.rules.add(compiled);
        }
    }

    /** GKD 的 rules 可为单对象、数组或字符串快捷式（rules:'[id=…]'——
     *  字符串即选择器，v4.3.0 支持） */
    static List<Object> normalizeRules(Object rulesObj) {
        if (rulesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) rulesObj;
            return l;
        }
        if (rulesObj instanceof java.util.Map) {
            List<Object> l = new ArrayList<Object>();
            l.add(rulesObj);
            return l;
        }
        if (rulesObj instanceof String) {
            // 字符串快捷式：rules:'[id=…][text=…]' ≡ rules:[{matches:'…'}]
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("matches", rulesObj);
            List<Object> l = new ArrayList<Object>();
            l.add(m);
            return l;
        }
        return null;
    }

    /** 单条规则编译：matches/anyMatches → 目标选择器；back 动作丢弃；
     *  v4.2.0：activityIds/excludeActivityIds/actionMaximum/actionInterval 全解析；
     *  v4.3.0：preKeys 链式规则跳过 + matchTime + actionCd */
    static Rule compileRule(java.util.Map<String, Object> rm, String ruleName,
                            int defActionMaximum, long defActionInterval, long defMatchTime) {
        if (rm == null) return null;
        Map0 m = new Map0(rm);
        // v4.3.0：链式规则（依赖前序规则命中才激活）不支持——跳过，绝不误触发
        if (m.raw.get("preKeys") != null) return null;
        String action = m.str("action", "click");
        if ("back".equals(action)) return null; // 红线：订阅规则绝不触发返回键
        Rule r = new Rule();
        r.name = ruleName;
        r.clickNode = "clickNode".equals(action);
        r.activityIds = m.strList("activityIds");
        r.excludeActivityIds = m.strList("excludeActivityIds");
        r.actionMaximum = (int) m.num("actionMaximum", defActionMaximum);
        r.actionInterval = (long) m.num("actionInterval", m.num("actionCd", defActionInterval));
        r.matchTime = (long) m.num("matchTime", defMatchTime);
        addSelectors(r.targets, m.raw.get("matches"));
        addSelectors(r.targets, m.raw.get("anyMatches"));
        addSelectors(r.excludes, m.raw.get("excludeMatches"));
        if (r.targets.isEmpty()) return null;
        // v4.3.0：结构规则标记（任一目标选择器含关系算子链）
        for (int i = 0; i < r.targets.size() && !r.structural; i++) {
            GkdSelector sel = r.targets.get(i);
            for (int k = 0; k < sel.chains.size(); k++) {
                if (sel.chains.get(k).sels.size() > 1) {
                    r.structural = true;
                    break;
                }
            }
        }
        return r;
    }

    static void addSelectors(List<GkdSelector> out, Object v) {
        List<Object> list = null;
        if (v instanceof String) {
            list = new ArrayList<Object>();
            list.add(v);
        } else if (v instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) v;
            list = l;
        }
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof String)) continue;
            try {
                out.add(GkdSelector.compile((String) list.get(i)));
            } catch (GkdSelector.ParseException e) {
                // 单条选择器语法不兼容：跳过该选择器（其余照常）
            }
        }
    }

    /** Map 读取小工具（避免到处 instanceof） */
    static final class Map0 {
        final java.util.Map<String, Object> raw;

        Map0(java.util.Map<String, Object> raw) {
            this.raw = raw == null ? new java.util.LinkedHashMap<String, Object>() : raw;
        }

        String str(String k, String d) {
            Object v = raw.get(k);
            return v instanceof String ? (String) v : d;
        }

        double num(String k, double d) {
            Object v = raw.get(k);
            return v instanceof Double ? ((Double) v).doubleValue() : d;
        }

        /** v4.3.0：布尔字段（组级 enable 等） */
        boolean bool(String k, boolean d) {
            Object v = raw.get(k);
            return v instanceof Boolean ? ((Boolean) v).booleanValue() : d;
        }

        /** v4.2.0：字符串或字符串数组 → String[]（GKD 的 activityIds 两种写法都合法） */
        String[] strList(String k) {
            Object v = raw.get(k);
            if (v instanceof String) {
                return new String[]{(String) v};
            }
            if (v instanceof List) {
                List<?> l = (List<?>) v;
                ArrayList<String> out = new ArrayList<String>(l.size());
                for (int i = 0; i < l.size(); i++) {
                    if (l.get(i) instanceof String) out.add((String) l.get(i));
                }
                return out.toArray(new String[0]);
            }
            return EMPTY_STRS;
        }
    }

    /* ══════════ 匹配（v4.2.0 单遍扫描热路径） ══════════
     * 1. 规则预过滤（包名 + activityIds）——不匹配的规则零树遍历直接出局；
     * 2. 单次 DFS 共享节点预算，每个节点依次尝试所有候选规则的选择器链
     *    （此前每条选择器独立 findFirst 全树遍历，N 条规则 = N 次整树）；
     * 3. 命中节点过安全闸（可见 + 面积 ≤35% 屏 + bounds 非空）；
     * 4. 无 activityIds 的「任意界面」规则加 skip 形态门；
     * 5. 运行时限流（actionMaximum/actionInterval）由注入的 Runtime 记账。 */

    private static final class Cand {
        final App app;
        final Rule rule;

        Cand(App app, Rule rule) {
            this.app = app;
            this.rule = rule;
        }
    }

    public RuleEngine.Hit match(String pkg, NodeView root, Ctx ctx, int budget) {
        return match(pkg, root, ctx, budget, null, "");
    }

    public RuleEngine.Hit match(String pkg, NodeView root, Ctx ctx, int budget,
                                Runtime rt, String keyPrefix) {
        if (root == null) return null;
        // ① 规则预过滤：包名 + 全局组排除表 + Activity 限定 + matchTime 窗口
        //   （零树遍历的最大的功耗优化）
        List<Cand> cands = new ArrayList<Cand>(8);
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            boolean global = "*".equals(app.id);
            if (!global && !pkg.equals(app.id)) continue; // GKD 语义：包名精确匹配
            // v4.3.0：全局组的排除表（enable:false 的应用有自己的专属规则）
            if (global && app.excludePkgs != null && app.excludePkgs.contains(pkg)) continue;
            for (int j = 0; j < app.rules.size(); j++) {
                Rule rule = app.rules.get(j);
                if (!activityAllowed(rule, ctx.activityId)) continue;
                // v4.3.0：matchTime 窗口（GKD 语义：进入窗口 N ms 内才激活）
                if (rule.matchTime > 0 && ctx.elapsedMs > rule.matchTime) continue;
                cands.add(new Cand(app, rule));
            }
        }
        if (cands.isEmpty()) return null; // 微信主界面等场景：绝大多数规则在此出局
        // ② 单遍 DFS（共享预算）
        GkdSelector.Exe exe = new GkdSelector.Exe(new int[]{budget});
        return dfsMulti(cands, root, root, exe, ctx, rt, keyPrefix);
    }

    private RuleEngine.Hit dfsMulti(List<Cand> cands, NodeView root, NodeView node, GkdSelector.Exe x,
                                    Ctx ctx, Runtime rt, String keyPrefix) {
        if (node == null || !x.dec()) return null;
        for (int i = 0; i < cands.size(); i++) {
            Cand c = cands.get(i);
            NodeView t = walkRule(c.rule, node, x);
            if (t == null) continue;
            if (!safeTarget(t, ctx)) continue;
            // 无 activityIds 限定的任意界面规则：目标必须是跳过/关闭形态；
            // v4.3.0 例外：结构规则（关系算子链精确锁定容器层级）——零文本
            // 图片跳过键（CSDN @View <3 FL < [vid="fl_third_ad"]）凭结构锚定过门
            if (c.rule.activityIds.length == 0 && !c.rule.structural
                    && !AdDecisionCore.looksSkipLike(t.text(), t.desc(), t.viewId())) continue;
            /* v5.0.0 形状门（与主引擎 validSkipTextShape 同源）：无 activityIds
             * 限定的任意界面规则，目标宽度也须过形状门——全宽行/横幅（列表项
             * 模板行、「跳过广告 查看详情」banner）绝不代点；activityIds/
             * 结构规则豁免同上（规则作者已精确限定场景）。 */
            if (c.rule.activityIds.length == 0 && !c.rule.structural
                    && !AdDecisionCore.validSkipTextShape(t, t.bounds(), ctx)) continue;
            // v4.3.0 安全红线：全局组（id='*'）无 Activity 限定的规则，目标
            // 文案仍过主引擎诱导词否决——「摇一摇领福利·翻倍领取」类绝不代点
            if ("*".equals(c.app.id) && c.rule.activityIds.length == 0
                    && !AdDecisionCore.safeGlobalLabel(t.text(), t.desc())) continue;
            if (excluded(c.rule, root, x)) continue;
            // 运行时限流：同窗次数 / 同规则间隔（GKD actionMaximum/actionInterval）
            if (rt != null) {
                String key = keyPrefix + c.app.id + "/" + c.rule.name + "@" + ctx.activityId;
                if (!rt.allowFire(key, c.rule.actionMaximum, c.rule.actionInterval)) continue;
                rt.onFired(key);
            }
            RuleEngine.Hit h = new RuleEngine.Hit();
            h.appId = "sub_gkd";
            h.label = "gkd:" + c.app.name + "/" + c.rule.name;
            h.clickNodeAction = c.rule.clickNode;
            h.src = c.app.name;
            h.tapBounds = t.bounds();
            NodeView click = AdDecisionCore.resolveClickable(t, 4);
            h.clickNode = click;
            h.gestureOnly = click == null
                    || !AdDecisionCore.isSmallClick(click, ctx);
            return h;
        }
        int n = node.childCount();
        for (int i = 0; i < n; i++) {
            RuleEngine.Hit r = dfsMulti(cands, root, node.child(i), x, ctx, rt, keyPrefix);
            if (r != null) return r;
        }
        return null;
    }

    /** 规则全部选择器链依次在 node 上验证（node 作为末段选择器命中） */
    static NodeView walkRule(Rule rule, NodeView node, GkdSelector.Exe x) {
        for (int k = 0; k < rule.targets.size(); k++) {
            NodeView t = rule.targets.get(k).walkChains(node, x);
            if (t != null) return t;
        }
        return null;
    }

    /** activityIds 门（GKD 语义：全等或后缀匹配；空数组 = 任意 Activity） */
    static boolean activityAllowed(Rule rule, String activityId) {
        String aid = activityId == null ? "" : activityId;
        /* v5.1.0：内部类窗口（美团 DynamicDialog$c 等混淆弹窗——无障碍
         * 回调给的窗口类名常带 $ 后缀）按基类名匹配：剥离 $ 后缀后
         * 再走全等/后缀比对，$c/$a 变体内一律认账 */
        String base = aid;
        int dollar = aid.indexOf('$');
        if (dollar >= 0) base = aid.substring(0, dollar);
        for (int i = 0; i < rule.excludeActivityIds.length; i++) {
            String e = rule.excludeActivityIds[i];
            if (aid.equals(e) || aid.endsWith(e) || aid.startsWith(e + "$")) return false;
            if (base.equals(e) || base.endsWith(e)) return false;
        }
        if (rule.activityIds.length == 0) return true;
        for (int i = 0; i < rule.activityIds.length; i++) {
            String a = rule.activityIds[i];
            if (aid.equals(a) || aid.endsWith(a)) return true;
            if (aid.startsWith(a + "$")) return true;
            if (base.equals(a) || base.endsWith(a)) return true;
        }
        return false;
    }

    static boolean excluded(Rule rule, NodeView root, GkdSelector.Exe x) {
        for (int i = 0; i < rule.excludes.size(); i++) {
            if (rule.excludes.get(i).findFirst(root, x) != null) return true;
        }
        return false;
    }

    /** 安全闸：可见 + bounds 非空 + 面积 ≤ 主引擎跳过键上限 */
    static boolean safeTarget(NodeView n, Ctx ctx) {
        if (!n.visible()) return false;
        int[] b = n.bounds();
        if (b == null || b.length < 4 || b[2] <= b[0] || b[3] <= b[1]) return false;
        double area = ((double) (b[2] - b[0]) * (b[3] - b[1]))
                / ((double) ctx.screenW * ctx.screenH);
        return area <= AdDecisionCore.SKIP_AREA_MAX;
    }

    /* ══════════ 统计 ══════════ */

    public int ruleCount() {
        int c = 0;
        for (int i = 0; i < apps.size(); i++) c += apps.get(i).rules.size();
        return c;
    }

    /** v5.3.0：订阅是否覆盖该应用（仅专属规则；全局组 '*' 不算——宿主窗
     *  档案据此决定 CLEAN 窗用纯探针哨兵还是低频档 {0,700}：vid 类规则
     *  探针够不到，须保留少量 DFS 机会）。 */
    public boolean covers(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        for (int i = 0; i < apps.size(); i++) {
            App a = apps.get(i);
            if (!a.id.equals(pkg)) continue;
            if (a.excludePkgs != null && a.excludePkgs.contains(pkg)) continue;
            if (!a.rules.isEmpty()) return true;
        }
        return false;
    }

    /* ══════════ 内置默认订阅（GKD 社区风格 · 与主引擎文本规则互补） ══════════
     * 主引擎五层规则认「文案」；本订阅认「控件 ID（vid）」——真实跳过键
     * 的 viewId 几乎都含 skip（btn_skip / iv_skip / tt_splash_skip_btn /
     * main_ad_skip / skip_view…），ID 比文案更稳（改文案不改 ID）。
     * 全部加 [visible=true] / [clickable=true] 上下文约束。
     * v4.2.0：内置订阅按 0/0（不限次）解析——出手节奏由全局点击冷却
     * （0.9s）与同窗点击预算（6 次）统一管制，与主引擎文案规则同权。 */

    public static final String BUILTIN_JSON5 =
            "{\n"
            + "  id: 20260510,\n"
            + "  name: '内置规则库',\n"
            + "  version: 6,\n"
            + "  apps: [\n"
            + app("com.zhihu.android", "知乎",
                    "[id=\"com.zhihu.android:id/btn_skip\"]", "[vid=\"btn_skip\"][visible=true]")
            + app("com.sina.weibo", "微博",
                    "[id=\"com.sina.weibo:id/iv_skip\"]", "[vid*=\"skip\"][visible=true]")
            + app("com.ximalaya.ting.android", "喜马拉雅",
                    "[vid*=\"skip\"][visible=true]", "[vid=\"main_ad_skip\"]")
            + app("net.csdn.csdnplus", "CSDN",
                    "[vid*=\"skip\"][visible=true]",
                    "@View <3 FrameLayout <2 FrameLayout < FrameLayout < [vid=\"fl_third_ad\"]")
            + app("tv.danmaku.bili", "哔哩哔哩",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.gotokeep.keep", "Keep",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.xingin.xhs", "小红书",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.dragon.read", "番茄小说",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.baidu.searchbox", "百度",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.netease.cloudmusic", "网易云音乐",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.tencent.qqlive", "腾讯视频",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.qiyi.video", "爱奇艺",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.youku.phone", "优酷",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.UCMobile", "UC浏览器",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.autonavi.minimap", "高德地图",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.dianping.v1", "大众点评",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.sankuai.meituan", "美团",
                    "[vid*=\"skip\"][visible=true]")
            /* ═══ v5.1.0 美团系专项（issue#46/47 实锤：DspActivity 开屏 +
             * DynamicDialog 动态广告弹窗整窗未命中）═══
             * DspActivity = 美团开屏 DSP 独立广告窗；DynamicDialog = 美团
             * 动态广告弹窗（内嵌关闭键）。close 族 vid 在 activityIds 限定
             * 下允许出手（窗口身份即广告，误点面可控）。 */
            + appSplash("com.sankuai.meituan", "美团·DSP开屏", "DspActivity",
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]", "@[vid*=\"close\"]"},
                    null, 1200)
            + appSplash("com.sankuai.meituan", "美团·广告弹窗", "DynamicDialog",
                    new String[]{"@[text^=\"跳过\"]", "@[text=\"关闭\"]", "@[desc*=\"关闭\"]", "@[vid*=\"close\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + app("com.jingdong.app.mall", "京东",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.tencent.mtt", "QQ浏览器",
                    "[vid*=\"skip\"][visible=true]")
            + app("cn.wps.moffice_eng", "WPS",
                    "[vid*=\"skip\"][visible=true]")
            /* ═══ v5.0.0 必备应用扩充（54 款 · 应用商店热门榜）═══
             * 从 v5.0.0 默认订阅移植，抖音家族限定 SplashActivity
             *（小程序/团购容器内规则绝不生效——与 MiniProgramGuard 双保险）。
             * 只保留开屏「跳过」类规则；「我知道了/关闭」弹窗类规则一律
             * 不收（无 Dialog 形态门控，误点风险 > 收益）。 */
            + appSplash("com.ss.android.ugc.aweme", "抖音", "SplashActivity",
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[text*=\"跳过广告\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.ss.android.ugc.aweme.lite", "抖音极速版", "SplashActivity",
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.smile.gifmaker", "快手", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.kwai.video", "快手极速版", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.ss.android.article.news", "今日头条", "SplashActivity",
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.ss.android.article.lite", "今日头条极速版", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]"},
                    null, 1200)
            + appSplash("com.tencent.mobileqq", "QQ", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.eg.android.AlipayGphone", "支付宝", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]"},
                    null, 1200)
            + appSplash("com.taobao.taobao", "淘宝", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.tmall.wireless", "天猫", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.xunmeng.pinduoduo", "拼多多", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("me.ele", "饿了么", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.hunantv.imgo.activity", "芒果TV", null,
                    new String[]{"@[text*=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.tencent.qqmusic", "QQ音乐", null,
                    new String[]{"@[text*=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.kugou.android", "酷狗音乐", null,
                    new String[]{"@[text*=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.ctrip.android.view.ctriptrip", "携程旅行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.Qunar", "去哪儿旅行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.taobao.trip", "飞猪", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.wuba", "58同城", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.taobao.idlefish", "闲鱼", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.alibaba.android.rimet", "钉钉", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.baidu.BaiduMap", "百度地图", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.quark.browser", "夸克", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.coolapk.market", "酷安", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.xiaomi.market", "小米应用商店", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.huawei.appmarket", "华为应用市场", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.oppo.market", "OPPO软件商店", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.bbk.appstore", "vivo应用商店", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.shizhuang.duapp", "得物", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.achievo.vipshop", "唯品会", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.douban.frodo", "豆瓣", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.hpbr.bosszhipin", "BOSS直聘", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            + appSplash("com.sdu.didi.psnger", "滴滴出行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"},
                    null, 1200)
            /* ═══ v5.1.0 必备应用再扩充（54 → 102 款）═══
             * issue#46/47 实锤：非主流/长尾 App 开屏全靠「全局通用」一条
             * vid 兜底，文本/图片键变体跟不上。本轮按应用商店热门榜 +
             * 日志高频应用补齐 46 款：银行金融 9（银行开屏跳过是刚需，
             * 守护名单只禁热区返回键，跳过键点击本就放行）/ 出行旅行 7 /
             * 阅读漫画 6 / 新闻资讯 4 / 效率工具 7 / 生活服务 7 / 学习
             * 办公 4 / 音乐社交 6。规则统一「跳过系文本 + desc + vid skip」
             * 三件套（包名精确匹配——写错的包名规则永不生效，零误点面）。 */
            // ── 银行金融（9）──
            + appSplash("com.icbc", "工商银行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.android.bankabc", "农业银行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.chinamworld.main", "中国银行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.chinamworld.BOCMB", "建设银行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.cmbchina.ccd.pluto.spongeactivity", "招商银行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.bankcomm.Bankcomm", "交通银行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.psbc.rpc", "邮储银行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.pingan.paces.ccms", "平安口袋银行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.unionpay", "云闪付", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            // ── 出行旅行（7）──
            + appSplash("com.MobileTicket", "铁路12306", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.huochepiao.user", "智行火车票", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.umetrip.android.msky.app", "航旅纵横", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.flightmanager.view", "飞常准", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.jingyao.easybike", "哈啰", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.t3go.passenger", "T3出行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.caocao.app", "曹操出行", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            // ── 阅读漫画（6）──
            + appSplash("com.tencent.weread", "微信读书", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.qidian.QDReader", "起点读书", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.kmxs.reader", "七猫免费小说", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.xs.fm", "番茄畅听", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.kuaikan.comic", "快看漫画", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.zhangyue.read.iReader", "掌阅", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            // ── 新闻资讯（4）──
            + appSplash("com.netease.newsreader.activity", "网易新闻", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.tencent.news", "腾讯新闻", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.sina.news", "新浪新闻", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.wondertek.paper", "澎湃新闻", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            // ── 效率工具（7）──
            + appSplash("com.moji.mjweather", "墨迹天气", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.snda.wifilocating", "WiFi万能钥匙", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.xunlei.downloadprovider", "迅雷", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.baidu.netdisk", "百度网盘", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.baidu.tieba", "百度贴吧", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.youdao.dict", "有道词典", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.tencent.qqpimsecure", "腾讯手机管家", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            // ── 生活服务（7）──
            + appSplash("com.cainiao.wireless", "菜鸟裹裹", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.sf.activity", "顺丰速运", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.wuba.zhuanzhuan", "转转", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.suning.mobile.ebuy", "苏宁易购", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.jingdong.pdj", "京东到家", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.lingan.seeyou", "美柚", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.mogujie", "蘑菇街", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            // ── 学习办公（4）──
            + appSplash("com.chaoxing.mobile", "学习通", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.handsgo.jiakao.android", "驾考宝典", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.jiongji.andriod.card", "百词斩", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.tencent.wework", "企业微信", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            // ── 音乐社交（6）──
            + appSplash("cn.kuwo.player", "酷我音乐", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.p1.mobile.putong", "探探", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("cn.soulapp.android", "Soul", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.ss.android.lark", "飞书", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.lemon.lvoverseas", "剪映", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            + appSplash("com.mt.mtxx.mtxx", "美图秀秀", null,
                    new String[]{"@[text^=\"跳过\"]", "@[desc*=\"跳过\"]", "@[vid*=\"skip\"]"}, null, 1200)
            // 全局：穿山甲/优量汇等 SDK 跳过键的通用 vid 形态
            + app("*", "全局通用",
                    "[vid$=\"_skip\"][clickable=true]", "[vid^=\"skip_\"]", "[vid=\"tt_splash_skip_btn\"]")
            + "  ],\n"
            + "}\n";

    /** v5.0.0：带 activityIds 限定 / excludeMatches / actionInterval 的开屏规则组。 */
    static String appSplash(String id, String name, String activityId, String[] matches,
                            String[] excludes, long interval) {
        StringBuilder sb = new StringBuilder();
        sb.append("    { id: '").append(id).append("', name: '").append(name)
                .append("', groups: [ { key: 0, name: '开屏广告', rules: [ { ");
        if (activityId != null) {
            sb.append("activityIds: ['").append(activityId).append("'], ");
        }
        sb.append("matches: [");
        for (int i = 0; i < matches.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'').append(matches[i]).append('\'');
        }
        sb.append("]");
        if (excludes != null && excludes.length > 0) {
            sb.append(", excludeMatches: [");
            for (int i = 0; i < excludes.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append('\'').append(excludes[i]).append('\'');
            }
            sb.append("]");
        }
        if (interval > 0) sb.append(", actionInterval: ").append(interval);
        sb.append(" } ] } ] },\n");
        return sb.toString();
    }

    static String app(String id, String name, String... selectors) {
        StringBuilder sb = new StringBuilder();
        sb.append("    { id: '").append(id).append("', name: '").append(name)
                .append("', groups: [ { key: 0, name: '开屏广告', rules: [ { matches: [");
        for (int i = 0; i < selectors.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append('\'').append(selectors[i]).append('\'');
        }
        sb.append("] } ] } ] },\n");
        return sb.toString();
    }
}
