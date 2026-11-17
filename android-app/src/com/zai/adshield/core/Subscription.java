package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;

import java.util.ArrayList;
import java.util.List;

/**
 * GKD 订阅模型与解析（v4.0.0 订阅融合 · v4.2.0 全门控重构）
 * ─────────────────────────────────────────────────────────────────────
 * 订阅 = GKD 生态规则的最小载体：apps[] → groups[] → rules[] →
 * matches（选择器）。本地内置订阅与远程导入订阅格式完全一致
 * （JSON5），加载时全部预编译为 GkdSelector——匹配热路径零解析。
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
    }

    static final String[] EMPTY_STRS = new String[0];

    public static final class App {
        public String id;              // 包名（'*' = 全局规则）
        public String name;
        public List<Rule> rules = new ArrayList<Rule>();
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
     *  远程订阅按 GKD 语义默认 1 次/窗 + 1000ms 间隔） */
    public static Subscription parse(String json5Text, int defActionMaximum, long defActionInterval) {
        Object root = Json5.parse(json5Text);
        Map0 m = new Map0(Json5.asMap(root));
        String name = m.str("name", "未命名订阅");
        int version = (int) m.num("version", 0);
        Subscription sub = new Subscription(name, version);
        List<Object> apps = Json5.asList(m.raw.get("apps"));
        if (apps == null) return sub;
        for (int i = 0; i < apps.size(); i++) {
            Map0 am = new Map0(Json5.asMap(apps.get(i)));
            String appId = am.str("id", null);
            if (appId == null || appId.length() == 0) continue;
            App app = new App();
            app.id = appId;
            app.name = am.str("name", appId);
            List<Object> groups = Json5.asList(am.raw.get("groups"));
            if (groups == null) continue;
            for (int g = 0; g < groups.size(); g++) {
                Map0 gm = new Map0(Json5.asMap(groups.get(g)));
                String gname = gm.str("name", "规则组");
                Object rulesObj = gm.raw.get("rules");
                List<Object> rules = normalizeRules(rulesObj);
                if (rules == null) continue;
                for (int r = 0; r < rules.size(); r++) {
                    Rule compiled = compileRule(Json5.asMap(rules.get(r)),
                            gname + "-" + (r + 1), defActionMaximum, defActionInterval);
                    if (compiled != null) app.rules.add(compiled);
                }
            }
            if (!app.rules.isEmpty()) sub.apps.add(app);
        }
        return sub;
    }

    /** GKD 的 rules 可为单对象或数组（还有 string 快捷式——跳过） */
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
        return null;
    }

    /** 单条规则编译：matches/anyMatches → 目标选择器；back 动作丢弃；
     *  v4.2.0：activityIds/excludeActivityIds/actionMaximum/actionInterval 全解析 */
    static Rule compileRule(java.util.Map<String, Object> rm, String ruleName,
                            int defActionMaximum, long defActionInterval) {
        if (rm == null) return null;
        Map0 m = new Map0(rm);
        String action = m.str("action", "click");
        if ("back".equals(action)) return null; // 红线：订阅规则绝不触发返回键
        Rule r = new Rule();
        r.name = ruleName;
        r.clickNode = "clickNode".equals(action);
        r.activityIds = m.strList("activityIds");
        r.excludeActivityIds = m.strList("excludeActivityIds");
        r.actionMaximum = (int) m.num("actionMaximum", defActionMaximum);
        r.actionInterval = (long) m.num("actionInterval", defActionInterval);
        addSelectors(r.targets, m.raw.get("matches"));
        addSelectors(r.targets, m.raw.get("anyMatches"));
        addSelectors(r.excludes, m.raw.get("excludeMatches"));
        if (r.targets.isEmpty()) return null;
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
        // ① 规则预过滤：包名 + Activity 限定（零树遍历的最大的功耗优化）
        List<Cand> cands = new ArrayList<Cand>(8);
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            boolean global = "*".equals(app.id);
            if (!global && !pkg.equals(app.id)) continue; // GKD 语义：包名精确匹配
            for (int j = 0; j < app.rules.size(); j++) {
                Rule rule = app.rules.get(j);
                if (!activityAllowed(rule, ctx.activityId)) continue;
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
            // 无 activityIds 限定的任意界面规则：目标必须是跳过/关闭形态
            if (c.rule.activityIds.length == 0
                    && !AdDecisionCore.looksSkipLike(t.text(), t.desc(), t.viewId())) continue;
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
        for (int i = 0; i < rule.excludeActivityIds.length; i++) {
            String e = rule.excludeActivityIds[i];
            if (aid.equals(e) || aid.endsWith(e)) return false;
        }
        if (rule.activityIds.length == 0) return true;
        for (int i = 0; i < rule.activityIds.length; i++) {
            String a = rule.activityIds[i];
            if (aid.equals(a) || aid.endsWith(a)) return true;
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

    /* ══════════ 内置默认订阅（GKD 社区风格 · 与主引擎文本规则互补） ══════════
     * 主引擎五层规则认「文案」；本订阅认「控件 ID（vid）」——真实跳过键
     * 的 viewId 几乎都含 skip（btn_skip / iv_skip / tt_splash_skip_btn /
     * main_ad_skip / skip_view…），ID 比文案更稳（改文案不改 ID）。
     * 全部加 [visible=true] / [clickable=true] 上下文约束。
     * v4.2.0：内置订阅按 0/0（不限次）解析——出手节奏由全局点击冷却
     * （0.9s）与同窗点击预算（6 次）统一管制，与主引擎文案规则同权。 */

    public static final String BUILTIN_JSON5 =
            "{\n"
            + "  id: 20260400,\n"
            + "  name: '内置规则库',\n"
            + "  version: 4,\n"
            + "  apps: [\n"
            + app("com.zhihu.android", "知乎",
                    "[id=\"com.zhihu.android:id/btn_skip\"]", "[vid=\"btn_skip\"][visible=true]")
            + app("com.sina.weibo", "微博",
                    "[id=\"com.sina.weibo:id/iv_skip\"]", "[vid*=\"skip\"][visible=true]")
            + app("com.ximalaya.ting.android", "喜马拉雅",
                    "[vid*=\"skip\"][visible=true]", "[vid=\"main_ad_skip\"]")
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
            + app("ctrip.android.view", "携程",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.autonavi.minimap", "高德地图",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.dianping.v1", "大众点评",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.sankuai.meituan", "美团",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.jingdong.app.mall", "京东",
                    "[vid*=\"skip\"][visible=true]")
            + app("com.tencent.mtt", "QQ浏览器",
                    "[vid*=\"skip\"][visible=true]")
            + app("cn.wps.moffice_eng", "WPS",
                    "[vid*=\"skip\"][visible=true]")
            // 全局：穿山甲/优量汇等 SDK 跳过键的通用 vid 形态
            + app("*", "全局通用",
                    "[vid$=\"_skip\"][clickable=true]", "[vid^=\"skip_\"]", "[vid=\"tt_splash_skip_btn\"]")
            + "  ],\n"
            + "}\n";

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
