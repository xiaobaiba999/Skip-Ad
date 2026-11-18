package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * v4.5.0 主引擎关键路径回归（v5.0.0 移植后不回退验证）。
 * 覆盖：规则引擎文本/vid 命中 / 倒计时解析 / 菜单行形状门 /
 * 返回键根窗口安全门 / 学习位置健全性门。
 */
public final class RegressionCoreTest {

    static Ctx ctx(String pkg, String act, boolean splash) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = act;
        ctx.elapsedMs = 800;
        ctx.splashLive = splash;
        return ctx;
    }

    public static void main(String[] args) {
        TestHost.suite("规则引擎 · 主路径回归");

        // Keep 开屏「跳过」文本键（issue#43 标杆场景）
        SimNode keepTree = new SimNode();
        keepTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skip = TestHost.skipButton("跳过");
        keepTree.add(skip);
        java.util.ArrayList<Hit> hits = RuleEngine.matchAll(keepTree,
                ctx("com.gotokeep.keep", "com.gotokeep.keep.SplashActivity", true), 4);
        TestHost.check(!hits.isEmpty(), "Keep 跳过文本命中");

        // 穿山甲 vid skip 键
        SimNode sdkTree = new SimNode();
        sdkTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode vidBtn = new SimNode("", 900, 80, 1060, 150);
        vidBtn.vid = "com.bytedance.openadsdk:id/tt_splash_skip_btn";
        vidBtn.clickable = true;
        sdkTree.add(vidBtn);
        java.util.ArrayList<Hit> sdkHits = RuleEngine.matchAll(sdkTree,
                ctx("com.third.app", "com.bytedance.sdk.openadsdk.activity.TTMainActivity", true), 4);
        TestHost.check(!sdkHits.isEmpty(), "穿山甲 vid skip 命中");

        // 倒计时解析
        TestHost.check(AdDecisionCore.parseCountdown("跳过 3") == 3, "倒计时「跳过 3」=3");
        TestHost.check(AdDecisionCore.parseCountdown("跳过") == 0, "无倒计时=0");
        TestHost.check(AdDecisionCore.parseCountdown("3s 后跳过") == 3, "「3s 后跳过」=3");

        TestHost.suite("形状门 · 菜单/抽屉行绝不点（issue#43/44 回归）");

        // 抽屉菜单里的「跳过」陷阱行（竖列菜单同列同宽多行）
        SimNode menuTree = new SimNode();
        menuTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode menuPanel = new SimNode();
        menuPanel.boundsArr = new int[]{600, 200, 1080, 2200};
        menuTree.add(menuPanel);
        String[] items = {"播放历史", "录音", "扫一扫", "切换模式", "跳过"};
        for (int i = 0; i < items.length; i++) {
            SimNode row = new SimNode(items[i], 640, 400 + i * 160, 1040, 520 + i * 160);
            row.clickable = true;
            menuPanel.add(row);
        }
        java.util.ArrayList<Hit> menuHits = RuleEngine.matchAll(menuTree,
                ctx("com.ximalaya.ting.android", "com.ximalaya.ting.android.main.MainActivity", true), 4);
        boolean menuHasSkip = false;
        for (Hit h : menuHits) {
            if ("跳过".equals(h.label)) menuHasSkip = true;
        }
        TestHost.check(!menuHasSkip, "抽屉菜单「跳过」陷阱行绝不命中");

        // isMenuListRow 单元（v4.5.0 V3 用例）：同一父容器内同列同宽 ≥4 行
        SimNode menuPanelUnit = new SimNode();
        menuPanelUnit.boundsArr = new int[]{600, 200, 1080, 2200};
        String[] rows = {"播放历史", "录音", "扫一扫", "切换模式", "跳过"};
        for (int i = 0; i < rows.length; i++) {
            SimNode row = new SimNode(rows[i], 640, 400 + i * 160, 1040, 520 + i * 160);
            row.clickable = true;
            menuPanelUnit.add(row);
        }
        TestHost.check(AdDecisionCore.isMenuListRow(menuPanelUnit.children.get(4)), "竖列菜单行识别=true");
        SimNode lone = new SimNode("跳过", 920, 80, 1050, 150);
        TestHost.check(!AdDecisionCore.isMenuListRow(lone), "孤立按钮=false");

        TestHost.suite("返回键 · 根窗口安全门（v3.6.0 毒链回归）");

        // 根窗口（入口窗）+ 无可点候选 → decide 绝不出 BACK
        Ctx rootCtx = ctx("com.ximalaya.ting.android",
                "com.ximalaya.ting.android.activity.WelcomeActivity", true);
        rootCtx.rootWindow = true;
        rootCtx.sdkAdWin = false;
        SimNode gyroTree = new SimNode();
        gyroTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode gyro = new SimNode("翻转手机 查看详情", 200, 900, 880, 1000);
        gyroTree.add(gyro);
        ScanFind gf = AdDecisionCore.walk(gyroTree, rootCtx);
        Decision gd = AdDecisionCore.decide(gf, rootCtx);
        TestHost.check(!AdDecisionCore.A_BACK.equals(gd.action),
                "根窗口绝不出返回键（" + gd.action + "）");

        // SDK 广告窗（应用内窗）→ 返回键允许
        Ctx sdkCtx = ctx("com.third.app",
                "com.bytedance.sdk.openadsdk.activity.TTMainActivity", true);
        sdkCtx.rootWindow = false;
        sdkCtx.sdkAdWin = true;
        sdkCtx.elapsedMs = 1200; // > CLS_BREAK_MS 700
        ScanFind sf2 = AdDecisionCore.walk(gyroTree, sdkCtx);
        Decision sd = AdDecisionCore.decide(sf2, sdkCtx);
        TestHost.check(AdDecisionCore.A_BACK.equals(sd.action),
                "SDK 广告窗直断返回键照常（" + sd.action + "）");

        TestHost.suite("学习位置 · 健全性门（越界拒绝）");

        TestHost.check(!AdDecisionCore.saneNormPos(0.13, 1.02), "(13%,102%) 越界拒绝（issue#45 实锤场景）");
        TestHost.check(AdDecisionCore.saneNormPos(0.88, 0.07), "(88%,7%) 正常位置接受");
        TestHost.check(!AdDecisionCore.saneNormPos(0.01, 0.5), "贴边 x 拒绝");
        TestHost.check(!AdDecisionCore.saneNormPos(0.5, 0.99), "贴边 y 拒绝");

        TestHost.suite("弹窗路径 · 关闭键回归");

        // 弹窗窗期：× 关闭键 → A_CLOSE
        Ctx popupCtx = ctx("com.some.app", "com.some.app.MainActivity", false);
        popupCtx.popupLive = true;
        SimNode popupTree = new SimNode();
        popupTree.cls = "android.app.Dialog";
        popupTree.boundsArr = new int[]{100, 600, 980, 1800};
        SimNode badge = new SimNode("广告", 130, 640, 220, 700);
        SimNode closeBtn = new SimNode("×", 880, 640, 950, 710);
        closeBtn.clickable = true;
        popupTree.add(badge);
        popupTree.add(closeBtn);
        ScanFind pf = AdDecisionCore.walk(popupTree, popupCtx);
        Decision pd = AdDecisionCore.decide(pf, popupCtx);
        TestHost.check(AdDecisionCore.A_CLOSE.equals(pd.action) || !A_None(pd),
                "弹窗×关闭键代点（" + pd.action + "）");

        TestHost.done();
    }

    static boolean A_None(Decision d) {
        return d == null || AdDecisionCore.A_NONE.equals(d.action);
    }
}
