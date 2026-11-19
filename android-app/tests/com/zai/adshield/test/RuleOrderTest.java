package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.RuleRouter;
import com.zai.adshield.core.Subscription;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 规则使用顺序与命中测试（v7.1.0 新套件）——用户实锤：优化内置规则库和
 * 订阅市场规则的使用顺序和命中问题。
 * ─────────────────────────────────────────────────────────────
 * 覆盖三块（全部纯 Java 零 Android 依赖）：
 *  S1 RuleRouter 两源使用顺序真值表（市场优先/内置兜底/停用/未导入全分支）；
 *  S2 Subscription 专属规则先于全局组（globalGroups 解析序在 apps 前的
 *     旧病灶——泛化全局规则饿死专属精准规则的根治验证）+ 全局组排除表
 *     + 未覆盖应用全局兜底；
 *  S3 RuleEngine 分应用规则行预筛 memo（rowsFor：命中行收敛/空包名/缓存
 *     同引用回填）+ 预筛与全表两路径命中等价（探针/DFS 双路径共享同一预筛）。
 */
public final class RuleOrderTest {

    /* S2 用合成订阅：globalGroups 在前（解析序还原 GKD 社区订阅真实形态——
     * 主力规则住 globalGroups），专属规则在后；两规则都能命中同一节点。 */
    static final String ORDER_JSON5 = "{\n"
            + "  name: \"顺序验证订阅\",\n"
            + "  version: 3,\n"
            + "  globalGroups: [\n"
            + "    { name: \"全局开屏\", rules: '[vid*=\"skip\"]' },\n"
            + "  ],\n"
            + "  apps: [\n"
            + "    { id: \"com.order.app\", name: \"顺序应用\", groups: [\n"
            + "      { name: \"专属开屏\", rules: '[text=\"跳过广告\"]' },\n"
            + "    ]},\n"
            + "  ],\n"
            + "}";

    /* S2 排除表用：全局组对 com.order.app enable:false（该应用有自己的专属规则） */
    static final String EXCLUDE_JSON5 = "{\n"
            + "  name: \"排除验证订阅\",\n"
            + "  version: 1,\n"
            + "  globalGroups: [\n"
            + "    { name: \"全局开屏\", apps: [ { id: \"com.order.app\", enable: false } ],"
            + " rules: '[vid*=\"skip\"]' },\n"
            + "  ],\n"
            + "  apps: [\n"
            + "    { id: \"com.order.app\", name: \"顺序应用\", groups: [\n"
            + "      { name: \"专属开屏\", rules: '[text=\"跳过广告\"]' },\n"
            + "    ]},\n"
            + "  ],\n"
            + "}";

    public static void main(String[] args) {
        /* ════ S1 RuleRouter 两源使用顺序真值表 ════ */
        TestHost.suite("RuleRouter · S1 两源使用顺序（市场优先→内置兜底）");
        // 市场已导入+启用+该应用有专属规则 → 市场优先、内置兜底
        String[] a = RuleRouter.sourceOrder(true, true, true, true, true);
        TestHost.checkEq(2, a.length, "市场覆盖：两源都在场");
        TestHost.checkEq(RuleRouter.SRC_REMOTE, a[0], "市场覆盖：市场第一");
        TestHost.checkEq(RuleRouter.SRC_BUILTIN, a[1], "市场覆盖：内置兜底第二");
        // 市场可用但该应用无专属规则 → 内置优先（市场全局组仍随后可轮换到）
        String[] b = RuleRouter.sourceOrder(true, true, false, true, true);
        TestHost.checkEq(2, b.length, "市场无覆盖：两源都在场");
        TestHost.checkEq(RuleRouter.SRC_BUILTIN, b[0], "市场无覆盖：内置第一");
        TestHost.checkEq(RuleRouter.SRC_REMOTE, b[1], "市场无覆盖：市场全局组第二");
        // 市场停用 → 仅内置
        String[] c = RuleRouter.sourceOrder(false, true, true, true, true);
        TestHost.checkEq(1, c.length, "市场停用：仅内置");
        TestHost.checkEq(RuleRouter.SRC_BUILTIN, c[0], "市场停用：内置唯一");
        // 市场未导入 → 仅内置
        String[] d = RuleRouter.sourceOrder(true, false, false, true, true);
        TestHost.checkEq(1, d.length, "市场未导入：仅内置");
        TestHost.checkEq(RuleRouter.SRC_BUILTIN, d[0], "市场未导入：内置唯一");
        // 市场在岗但内置未加载（理论上不发生——守恒断言）→ 仅市场
        String[] e = RuleRouter.sourceOrder(true, true, true, false, false);
        TestHost.checkEq(1, e.length, "内置缺席：仅市场");
        TestHost.checkEq(RuleRouter.SRC_REMOTE, e[0], "内置缺席：市场唯一");
        // 两源都不可用 → 空（服务侧跳过订阅路径）
        String[] f = RuleRouter.sourceOrder(false, false, false, false, false);
        TestHost.checkEq(0, f.length, "两源缺席：空序");
        // 市场停用 + 未导入 + 内置在 → 仍仅内置（开关与加载双闸）
        String[] g = RuleRouter.sourceOrder(false, false, false, true, true);
        TestHost.checkEq(1, g.length, "双闸关停：仅内置");
        // 显示名（日志/使用报告）
        TestHost.checkEq("市场", RuleRouter.zh(RuleRouter.SRC_REMOTE), "zh(市场)");
        TestHost.checkEq("内置", RuleRouter.zh(RuleRouter.SRC_BUILTIN), "zh(内置)");
        TestHost.check(RuleRouter.summary().contains("市场源"), "治理摘要含市场源说明");

        /* ════ S2 Subscription 专属规则先于全局组 ════ */
        TestHost.suite("Subscription · S2 专属规则先于全局组（命中顺序）");
        Subscription sub = null;
        try {
            sub = Subscription.parse(ORDER_JSON5, 1, 1000);
        } catch (Exception ex) {
            System.out.println("  ❌ ORDER_JSON5 解析异常: " + ex);
        }
        TestHost.check(sub != null, "合成订阅可解析");
        if (sub != null) {
            TestHost.checkEq(2, sub.apps.size(), "全局组+专属应用各成 App（2 个）");
            TestHost.checkEq(2, sub.ruleCount(), "规则数合计 2（全局 1+专属 1）");
            // 同一节点两规则都可命中：专属精准规则 [text="跳过广告"] 必须先于
            // 全局泛化规则 [vid*="skip"]（旧序：globalGroups 在 apps 之前 →
            // 泛化规则先出手，专属规则被饿死）
            SimNode tree = new SimNode();
            tree.boundsArr = new int[]{0, 0, 1080, 2400};
            SimNode btn = new SimNode("跳过广告", 900, 80, 1050, 150);
            btn.vid = "com.order.app:id/btn_skip";
            btn.clickable = true;
            tree.add(btn);
            Ctx ctx = ctx("com.order.app", tree);
            Hit h = sub.match("com.order.app", tree, ctx, 400);
            TestHost.check(h != null, "专属+全局双命中时非 null");
            TestHost.check(h != null && h.label != null && h.label.contains("顺序应用/专属开屏"),
                    "专属规则先出手（label=" + (h == null ? "null" : h.label) + "——旧序会是「全局开屏」）");
            // 未覆盖应用：全局组兜底生效（GKD 语义：未排除的包名默认适用）
            SimNode tree2 = new SimNode();
            tree2.boundsArr = new int[]{0, 0, 1080, 2400};
            SimNode btn2 = new SimNode("", 900, 80, 1050, 150);
            btn2.vid = "com.other.app:id/iv_skip";
            btn2.clickable = true;
            tree2.add(btn2);
            Ctx ctx2 = ctx("com.other.app", tree2);
            Hit h2 = sub.match("com.other.app", tree2, ctx2, 400);
            TestHost.check(h2 != null && h2.label.contains("全局开屏"),
                    "未覆盖应用走全局组兜底（label=" + (h2 == null ? "null" : h2.label) + "）");
            TestHost.check(!sub.covers("com.other.app"), "covers 不把全局组算覆盖（专属才算）");
            TestHost.check(sub.covers("com.order.app"), "covers 认专属覆盖");
        }
        // 排除表：全局组对 enable:false 的包不适用（该应用专属规则仍命中）
        Subscription ex = null;
        try {
            ex = Subscription.parse(EXCLUDE_JSON5, 1, 1000);
        } catch (Exception exc) {
            System.out.println("  ❌ EXCLUDE_JSON5 解析异常: " + exc);
        }
        TestHost.check(ex != null, "排除表订阅可解析");
        if (ex != null) {
            SimNode tree = new SimNode();
            tree.boundsArr = new int[]{0, 0, 1080, 2400};
            SimNode btn = new SimNode("跳过广告", 900, 80, 1050, 150);
            btn.vid = "com.order.app:id/btn_skip";
            btn.clickable = true;
            tree.add(btn);
            Ctx ctx = ctx("com.order.app", tree);
            Hit h = ex.match("com.order.app", tree, ctx, 400);
            TestHost.check(h != null && h.label.contains("顺序应用/专属开屏"),
                    "排除表生效：专属规则命中（全局组被排除）");
            SimNode vidOnly = new SimNode();
            vidOnly.boundsArr = new int[]{0, 0, 1080, 2400};
            SimNode vb = new SimNode("", 900, 80, 1050, 150);
            vb.vid = "com.order.app:id/btn_skip";
            vb.clickable = true;
            vidOnly.add(vb);
            Hit h3 = ex.match("com.order.app", vidOnly, ctx, 400);
            TestHost.check(h3 == null, "排除表生效：仅 vid 全局规则不再命中该包");
        }

        /* ════ S3 RuleEngine 分应用规则行预筛 memo ════ */
        TestHost.suite("RuleEngine · S3 分应用行预筛 memo（底层算法优化）");
        String[][] weiboRows = RuleEngine.rowsFor("com.sina.weibo");
        TestHost.check(weiboRows.length >= 2, "微博行预筛命中 ≥2 行（v5.6+v6.0 两轮都在场，实际 " + weiboRows.length + "）");
        boolean allPrefix = true;
        for (String[] row : weiboRows) {
            if (!"com.sina.weibo".startsWith(row[0])) { allPrefix = false; break; }
        }
        TestHost.check(allPrefix, "预筛行前缀全部匹配包名（零冗余行）");
        String[][] none = RuleEngine.rowsFor("com.unknown.app");
        TestHost.checkEq(0, none.length, "无覆盖包名 → 空表（每节点零比对）");
        TestHost.checkEq(0, RuleEngine.rowsFor(null).length, "null 包名 → 空表");
        TestHost.checkEq(0, RuleEngine.rowsFor("").length, "空包名 → 空表");
        String[][] again = RuleEngine.rowsFor("com.sina.weibo");
        TestHost.check(again == weiboRows, "memo 缓存命中（同引用回填——每窗只预筛一次）");
        // 预筛路径与全表路径命中等价（探针/DFS 双路径共享同一 ctx 预筛）
        SimNode node = new SimNode("跳过广告", 900, 80, 1050, 150);
        node.clickable = true;
        Ctx pctx = ctx("com.sina.weibo", node);
        pctx.appTextRules = RuleEngine.rowsFor("com.sina.weibo");
        Hit withRows = RuleEngine.matchText("跳过广告", node.boundsArr, node, pctx);
        Ctx fctx = ctx("com.sina.weibo", node);
        Hit withFull = RuleEngine.matchText("跳过广告", node.boundsArr, node, fctx);
        TestHost.check(withRows != null && "app_exact".equals(withRows.appId),
                "预筛路径命中 app_exact（微博「跳过广告」分应用文案）");
        TestHost.check(withFull != null && "app_exact".equals(withFull.appId),
                "全表路径同样命中（两条路径等价）");
        // 无覆盖包名走预筛空表：分应用层零命中 → 全局精确层兜底（语义与
        // 全表路径一致——预筛只旁路分应用层，绝不影响全局层命中面）
        Ctx uctx = ctx("com.unknown.app", node);
        uctx.appTextRules = RuleEngine.rowsFor("com.unknown.app");
        Hit unknown = RuleEngine.matchText("跳过", node.boundsArr, node, uctx);
        Ctx uctxFull = ctx("com.unknown.app", node);
        Hit unknownFull = RuleEngine.matchText("跳过", node.boundsArr, node, uctxFull);
        TestHost.check(unknown != null && "global_exact".equals(unknown.appId),
                "无覆盖包名：分应用层零命中→全局精确层兜底（预筛旁路正确）");
        TestHost.check(unknownFull != null && "global_exact".equals(unknownFull.appId),
                "无覆盖包名：全表路径同样走全局精确（两路径语义一致）");
        TestHost.checkEq(0, RuleEngine.rowsFor("com.unknown.app").length, "无覆盖包名 → 预筛空表");

        TestHost.done();
    }

    static Ctx ctx(String pkg, SimNode tree) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = "com.order.SplashActivity";
        ctx.elapsedMs = 800;
        ctx.splashLive = true;
        return ctx;
    }
}
