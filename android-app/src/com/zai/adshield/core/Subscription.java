package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;

import java.util.ArrayList;
import java.util.List;

/**
 * GKD 订阅模型与解析（v4.0.0 订阅融合 · https://gkd.li/guide/subscription）
 * ─────────────────────────────────────────────────────────────────────
 * 订阅 = GKD 生态规则的最小载体：apps[] → groups[] → rules[] →
 * matches（选择器）。本地内置订阅与远程导入订阅格式完全一致
 * （JSON5），加载时全部预编译为 GkdSelector——匹配热路径零解析。
 *
 * 安全红线（与主引擎一致）：
 *  · 只支持 click / clickNode 动作（back 类规则在加载时直接丢弃
 *    ——链式拦截已整体移除，订阅规则也绝不能触发返回键）；
 *  · 匹配命中的目标节点仍须过主引擎安全闸（可见 + 面积 ≤35% 屏
 *    + 非空 bounds），由 SubscriptionManager.match 统一把关；
 *  · 解析失败的规则跳过、失败的订阅整体拒绝——绝不影响主引擎。
 */
public final class Subscription {

    /* ══════════ 模型 ══════════ */

    public static final class Rule {
        public List<GkdSelector> targets = new ArrayList<GkdSelector>();
        public List<GkdSelector> excludes = new ArrayList<GkdSelector>();
        public boolean clickNode;      // true=performAction 点击控件本身
        public String name;            // 日志标识（group 名 + 序号）
    }

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

    /* ══════════ 解析（JSON5 → 模型，选择器全部预编译） ══════════ */

    public static Subscription parse(String json5Text) {
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
                    Rule compiled = compileRule(Json5.asMap(rules.get(r)), gname + "-" + (r + 1));
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

    /** 单条规则编译：matches/anyMatches → 目标选择器；back 动作丢弃 */
    static Rule compileRule(java.util.Map<String, Object> rm, String ruleName) {
        if (rm == null) return null;
        Map0 m = new Map0(rm);
        String action = m.str("action", "click");
        if ("back".equals(action)) return null; // 红线：订阅规则绝不触发返回键
        Rule r = new Rule();
        r.name = ruleName;
        r.clickNode = "clickNode".equals(action);
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
    }

    /* ══════════ 匹配（热路径：全部走预编译选择器） ══════════
     * 安全闸在这里：目标节点须 可见 + 面积 ≤35% 屏 + bounds 非空——
     * 与主引擎 RuleEngine 同一道闸，大容器/CTA 巨按钮绝不点。 */

    /**
     * 在控件树里跑本订阅中指定应用的规则。
     * 返回 null = 无命中。（结果为 RuleEngine.Hit，额外字段见
     * clickNodeAction / src）
     */
    public RuleEngine.Hit match(String pkg, NodeView root, Ctx ctx, int budget) {
        if (root == null) return null;
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            boolean global = "*".equals(app.id);
            if (!global && !pkg.equals(app.id)) continue; // GKD 语义：包名精确匹配
            for (int j = 0; j < app.rules.size(); j++) {
                Rule rule = app.rules.get(j);
                for (int k = 0; k < rule.targets.size(); k++) {
                    NodeView t = rule.targets.get(k).findFirst(root, budget);
                    if (t == null) continue;
                    if (!safeTarget(t, ctx)) continue;
                    if (excluded(rule, root, budget)) break;
                    RuleEngine.Hit h = new RuleEngine.Hit();
                    h.appId = "sub_gkd";
                    h.label = "gkd:" + app.name + "/" + rule.name;
                    h.clickNodeAction = rule.clickNode;
                    h.src = app.name;
                    h.tapBounds = t.bounds();
                    NodeView click = AdDecisionCore.resolveClickable(t, 4);
                    h.clickNode = click;
                    h.gestureOnly = click == null
                            || !AdDecisionCore.isSmallClick(click, ctx);
                    return h;
                }
            }
        }
        return null;
    }

    static boolean excluded(Rule rule, NodeView root, int budget) {
        for (int i = 0; i < rule.excludes.size(); i++) {
            if (rule.excludes.get(i).findFirst(root, budget) != null) return true;
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
     * 全部加 [visible=true] / [clickable=true] 上下文约束。 */

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
