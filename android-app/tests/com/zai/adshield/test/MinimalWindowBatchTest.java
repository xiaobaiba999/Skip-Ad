package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * L3 前置 · 极简窗口专项批次（v7.9.5 新套件 · 用户指令五）。
 *
 * 重点测试面：≤60 节点 · 无文本 · 角落图标 · 候选区域（左上/右上/左下/右下）。
 * 交互类型（8 类 vid）：关闭 / 更多 / 返回 / 收藏 / 分享 / 设置 / 头像 / 普通按钮。
 *
 * 目标：证明角落图标路径不会误触普通 UI——
 *  · 普通 UI（可滚动容器 + 内容）中 8 类交互图标 × 四角【全部零出手】；
 *  · 内容丰富窗（>60 节点）无强广告形态 → 角落图标零出手（树极简兜底不适用）；
 *  · 中部边带图标（strongOnly）即使树极简也零出手（v7.5.0 FAB 防线）；
 *  · 窗龄/时机闸（非开屏窗零出手 · <300ms 零出手）；
 *  · 对照侧（真广告形态窗照常出手——识别能力零回退）。
 *
 * 诚实口径：树极简兜底（≤60 节点+无滚动/输入框+角落形态）是【形态路径】，
 * 不区分 vid 语义——vid 语义区分由 isVidSkip 等路径把守；普通 UI 的防误触
 * 证据链 = 滚动容器 + 输入框 + 窗龄 + 节点数四道闸（本套件逐类断言）。
 * 本轮【零安全门放宽】——每条断言对应既有闸的在场性，不为命中率动闸。
 */
public final class MinimalWindowBatchTest {

    /** 8 类交互 vid（关闭/更多/返回/收藏/分享/设置/头像/普通按钮）。 */
    static final String[][] INTERACTION_TYPES = {
            {"iv_close", "关闭"}, {"iv_more", "更多"}, {"iv_back", "返回"},
            {"iv_fav", "收藏"}, {"iv_share", "分享"}, {"iv_set", "设置"},
            {"iv_avatar", "头像"}, {"iv_btn", "普通按钮"},
    };

    /** 四角区域边界（1080×2400：TL/TR 顶带 + BL/BR 底带）。 */
    static final int[][] CORNER_BOUNDS = {
            {24, 24, 120, 120},        // 左上
            {960, 24, 1056, 120},      // 右上
            {24, 2280, 120, 2376},     // 左下
            {960, 2280, 1056, 2376},   // 右下
    };
    /** 中部边带（左缘中部——isMidEdgeControl 面，strongOnly 候选）。 */
    static final int[] MID_EDGE_BOUNDS = {24, 1100, 120, 1196};

    public static void main(String[] args) {
        s1_normalUiEightTypesZeroFire();
        s2_windowGate();
        s3_minimalFallbackContract();
        s4_richWindowZeroFire();
        s5_contrastAdFormFires();
        s6_midEdgeStrongOnly();
        s7_timingGate();
        s8_vidSemanticSkipPath();
        TestHost.done();
    }

    /* ══════════ S1 · 普通 UI：8 类交互图标 × 四角全部零出手 ══════════ */
    static void s1_normalUiEightTypesZeroFire() {
        TestHost.suite("极简批次 S1 · 普通 UI（可滚动容器）8 类交互图标 × 四角零出手");
        for (int t = 0; t < INTERACTION_TYPES.length; t++) {
            String vid = INTERACTION_TYPES[t][0];
            String zh = INTERACTION_TYPES[t][1];
            boolean allZero = true;
            for (int c = 0; c < CORNER_BOUNDS.length; c++) {
                SimNode tree = normalUiTree(vid, CORNER_BOUNDS[c]);
                Ctx ctx = ctxOf("com.example.normalui", true, 600);
                Decision d = AdDecisionCore.decide(AdDecisionCore.walk(tree, ctx), ctx);
                if (d != null && !AdDecisionCore.A_NONE.equals(d.action)) allZero = false;
            }
            TestHost.check(allZero, "普通UI·" + zh + "图标: 四角（TL/TR/BL/BR）全部零出手（滚动容器证据拒收）");
        }
        // 逐角显式断言（iv_close 代表——四角各自零出手）
        for (int c = 0; c < CORNER_BOUNDS.length; c++) {
            SimNode tree = normalUiTree("iv_close", CORNER_BOUNDS[c]);
            Ctx ctx = ctxOf("com.example.normalui", true, 600);
            ScanFind f = AdDecisionCore.walk(tree, ctx);
            Decision d = AdDecisionCore.decide(f, ctx);
            TestHost.check(d != null && AdDecisionCore.A_NONE.equals(d.action),
                    "普通UI·关闭图标·角" + (c + 1) + ": 零出手（scrollPresent 闸在岗）");
        }
    }

    /* ══════════ S2 · 窗级门：非开屏窗极简窗也零出手 ══════════ */
    static void s2_windowGate() {
        TestHost.suite("极简批次 S2 · 窗级门（非开屏窗零出手）");
        SimNode tree = minimalTree("iv_close", CORNER_BOUNDS[1]);
        Ctx ctx = ctxOf("com.example.app", false, 600); // 非开屏窗龄
        Decision d = AdDecisionCore.decide(AdDecisionCore.walk(tree, ctx), ctx);
        TestHost.check(d != null && AdDecisionCore.A_NONE.equals(d.action),
                "非开屏窗: 极简树角落图标零出手（角落路径只在开屏窗内）");
    }

    /* ══════════ S3 · 树极简兜底契约（设计行为——形态路径）══════════ */
    static void s3_minimalFallbackContract() {
        TestHost.suite("极简批次 S3 · 树极简兜底契约（≤60 节点 + 无文本 + 角落形态）");
        SimNode tree = minimalTree("iv_close", CORNER_BOUNDS[1]);
        Ctx ctx = ctxOf("com.example.app", true, 600);
        ScanFind f = AdDecisionCore.walk(tree, ctx);
        Decision d = AdDecisionCore.decide(f, ctx);
        TestHost.check(f.nodeCount <= 60, "极简窗: 节点数 ≤60（兜底形态前提）");
        TestHost.check(d != null && "corner_image_skip".equals(d.reason),
                "极简窗: 树极简角落图标合法出手（极简窗兜底形态——与 PageScenarioTest custom-draw A 同口径）");
        // 四角各自兜底（真·极简广告窗不挑角）
        for (int c = 0; c < CORNER_BOUNDS.length; c++) {
            SimNode t2 = minimalTree("iv_close", CORNER_BOUNDS[c]);
            Ctx c2 = ctxOf("com.example.app", true, 600);
            Decision d2 = AdDecisionCore.decide(AdDecisionCore.walk(t2, c2), c2);
            TestHost.check(d2 != null && "corner_image_skip".equals(d2.reason),
                    "极简窗·角" + (c + 1) + ": 兜底出手（位置不挑角——v4.3.0 底部角落契约）");
        }
    }

    /* ══════════ S4 · 内容丰富窗：>60 节点无强广告形态零出手 ══════════ */
    static void s4_richWindowZeroFire() {
        TestHost.suite("极简批次 S4 · 内容丰富窗（70+ 节点）零出手");
        SimNode tree = richTree("iv_close", CORNER_BOUNDS[1], false);
        Ctx ctx = ctxOf("com.example.app", true, 600);
        ScanFind f = AdDecisionCore.walk(tree, ctx);
        Decision d = AdDecisionCore.decide(f, ctx);
        TestHost.check(f.nodeCount > 60, "丰富窗: 节点数 >60（树极简兜底不适用）");
        TestHost.check(d != null && AdDecisionCore.A_NONE.equals(d.action),
                "丰富窗: 无强广告形态（角标/SDK/热区）→ 8 类交互图标所在角落零出手");
        // 8 类全部零出手（强广告形态缺席——兜底不越权）
        boolean allZero = true;
        for (int t = 0; t < INTERACTION_TYPES.length; t++) {
            SimNode tr = richTree(INTERACTION_TYPES[t][0], CORNER_BOUNDS[1], false);
            Ctx c3 = ctxOf("com.example.app", true, 600);
            Decision d3 = AdDecisionCore.decide(AdDecisionCore.walk(tr, c3), c3);
            if (d3 != null && !AdDecisionCore.A_NONE.equals(d3.action)) allZero = false;
        }
        TestHost.check(allZero, "丰富窗·8 类交互图标: 全部零出手（无强形态不出手——不乱点红线）");
    }

    /* ══════════ S5 · 对照侧：真广告形态窗照常出手（识别零回退）══════════ */
    static void s5_contrastAdFormFires() {
        TestHost.suite("极简批次 S5 · 对照侧（真广告形态——识别能力零回退）");
        SimNode tree = richTree("iv_close", CORNER_BOUNDS[1], true);
        Ctx ctx = ctxOf("com.example.app", true, 600);
        Decision d = AdDecisionCore.decide(AdDecisionCore.walk(tree, ctx), ctx);
        TestHost.check(d != null && "corner_image_skip".equals(d.reason),
                "对照·丰富窗+角标: 角落关闭图标照常出手（强广告形态在场）");
        SimNode t2 = minimalTree("iv_close", CORNER_BOUNDS[1]);
        SimNode badge = new SimNode("广告", 60, 2140, 180, 2190);
        t2.add(badge);
        Ctx c2 = ctxOf("com.example.app", true, 600);
        Decision d2 = AdDecisionCore.decide(AdDecisionCore.walk(t2, c2), c2);
        TestHost.check(d2 != null && "corner_image_skip".equals(d2.reason),
                "对照·极简窗+角标: 照常出手（角标是第二证据——识别零回退）");
    }

    /* ══════════ S6 · 中部边带：strongOnly 不适用树极简兜底 ══════════ */
    static void s6_midEdgeStrongOnly() {
        TestHost.suite("极简批次 S6 · 中部边带图标（FAB 防线）");
        SimNode tree = minimalTree("iv_fav", MID_EDGE_BOUNDS);
        Ctx ctx = ctxOf("com.example.app", true, 600);
        ScanFind f = AdDecisionCore.walk(tree, ctx);
        Decision d = AdDecisionCore.decide(f, ctx);
        TestHost.check(!f.corners.isEmpty() && f.corners.get(0).strongOnly,
                "中部边带: 候选被收集且标记 strongOnly（左缘中部头像/收藏类图标）");
        TestHost.check(d == null || AdDecisionCore.A_NONE.equals(d.action),
                "中部边带·极简窗: 零出手（strongOnly 需强广告形态——树极简兜底不越权·v7.5.0 FAB 防线在岗）");
        // 对照：同一位置带角标（强广告形态）则出手
        SimNode t2 = minimalTree("iv_fav", MID_EDGE_BOUNDS);
        t2.add(new SimNode("广告", 60, 2140, 180, 2190));
        Ctx c2 = ctxOf("com.example.app", true, 600);
        Decision d2 = AdDecisionCore.decide(AdDecisionCore.walk(t2, c2), c2);
        TestHost.check(d2 != null && "corner_image_skip".equals(d2.reason),
                "中部边带·对照: 角标在场（强广告形态）照常出手（识别零回退）");
    }

    /* ══════════ S7 · 时机闸：<300ms 零出手 · ≥300ms 出手 ══════════ */
    static void s7_timingGate() {
        TestHost.suite("极简批次 S7 · 时机闸（候选出现→立即安全判断）");
        SimNode tree = minimalTree("iv_close", CORNER_BOUNDS[1]);
        Ctx early = ctxOf("com.example.app", true, 299);
        Decision dEarly = AdDecisionCore.decide(AdDecisionCore.walk(tree, early), early);
        TestHost.check(dEarly != null && AdDecisionCore.A_NONE.equals(dEarly.action),
                "时机·299ms: 极简窗角落图标零出手（CORNER_MIN_MS 时机闸在岗）");
        Ctx onTime = ctxOf("com.example.app", true, 300);
        Decision dOnTime = AdDecisionCore.decide(AdDecisionCore.walk(tree, onTime), onTime);
        TestHost.check(dOnTime != null && "corner_image_skip".equals(dOnTime.reason),
                "时机·300ms: 首过即出手（候选出现立即安全判断——不空等）");
        Ctx late = ctxOf("com.example.app", true, 7000);
        Decision dLate = AdDecisionCore.decide(AdDecisionCore.walk(tree, late), late);
        TestHost.check(dLate != null && AdDecisionCore.A_NONE.equals(dLate.action),
                "时机·7s: 超窗零出手（CORNER_MAX_MS 上限在岗——不无限等）");
    }

    /* ══════════ S8 · vid 语义正信号（iv_skip 走跳过键路径）══════════ */
    static void s8_vidSemanticSkipPath() {
        TestHost.suite("极简批次 S8 · vid 语义正信号");
        SimNode tree = minimalTree("iv_skip", CORNER_BOUNDS[1]);
        Ctx ctx = ctxOf("com.example.app", true, 600);
        ScanFind f = AdDecisionCore.walk(tree, ctx);
        Decision d = AdDecisionCore.decide(f, ctx);
        TestHost.check(!f.skipCands.isEmpty(), "vid 语义: iv_skip 被收集为跳过键候选（isVidSkip 正信号）");
        TestHost.check(d != null && "skip_text".equals(d.reason),
                "vid 语义: iv_skip 走文本跳过键路径出手（语义优先于兜底形态）");
    }

    /* ══════════ 工具 ══════════ */

    /** 普通业务 UI 窗：可滚动容器 + 内容文本 + 单个角落交互图标（滚动证据在场）。 */
    static SimNode normalUiTree(String vid, int[] iconBounds) {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode scroll = new SimNode("", 0, 0, 1080, 2400);
        scroll.cls = "android.widget.ScrollView";
        scroll.scrollable = true;
        SimNode content = new SimNode("今日推荐内容已更新 · 下滑查看更多精彩", 60, 200, 1020, 1900);
        content.cls = "android.widget.TextView";
        SimNode icon = new SimNode("", iconBounds[0], iconBounds[1], iconBounds[2], iconBounds[3]);
        icon.cls = "android.widget.ImageView";
        icon.clickable = true;
        icon.vid = vid;
        scroll.add(content);
        root.add(scroll);
        root.add(icon);
        return root;
    }

    /** 极简窗：根 + 单个角落图标（≤60 节点 · 无文本）。 */
    static SimNode minimalTree(String vid, int[] iconBounds) {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode icon = new SimNode("", iconBounds[0], iconBounds[1], iconBounds[2], iconBounds[3]);
        icon.cls = "android.widget.ImageView";
        icon.clickable = true;
        icon.vid = vid;
        root.add(icon);
        return root;
    }

    /** 内容丰富窗：70 个无文本填充节点 + 角落图标（withBadge=true 加广告角标）。 */
    static SimNode richTree(String vid, int[] iconBounds, boolean withBadge) {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        for (int i = 0; i < 70; i++) {
            SimNode seg = new SimNode("", 400, 300 + i * 14, 500, 306 + i * 14);
            seg.cls = "android.view.View";
            root.add(seg);
        }
        SimNode icon = new SimNode("", iconBounds[0], iconBounds[1], iconBounds[2], iconBounds[3]);
        icon.cls = "android.widget.ImageView";
        icon.clickable = true;
        icon.vid = vid;
        root.add(icon);
        if (withBadge) {
            root.add(new SimNode("广告", 60, 2140, 180, 2190));
        }
        return root;
    }

    static Ctx ctxOf(String pkg, boolean splash, long elapsed) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = "com.example.app.MainActivity";
        ctx.elapsedMs = elapsed;
        ctx.splashLive = splash;
        return ctx;
    }
}
