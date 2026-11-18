package com.zai.adshield.tests;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;

/**
 * v4.5.0 专项 JVM 单测（发版前必绿）
 * ─────────────────────────────────────────────────────────────
 * 用例来源：issue#42/#43/#44 真机日志实锤 + 李跳跳形状白名单方法论：
 *  · V1 系 乱点根治·抽屉菜单（issue#43/44 原场景复现）：
 *    右侧抽屉（BaseCustomDialogFragment + 播放历史/录音/扫一扫/切换模式
 *    菜单）内「跳过」列表项——rule 路径与 walk→decide 兜底路径双双绝不点击
 *  · V2 系 形状门矩阵：全宽行否决/可点长条按钮放行/SDK 窗豁免/
 *    横排按钮组不误伤（只挡竖列）
 *  · V3 系 isMenuListRow 单元：真抽屉/横排/独行/仅 3 行
 *  · V4 系 回归红线：Keep/喜马拉雅/倒计时/vid 主路径 0ms 命中不受影响
 *  · V5 系 matchText 直调（原生文本探针同路径）：菜单行内精确文案拒绝
 */
public class V450Test {

    static int pass = 0, fail = 0;

    static Ctx splashCtx() {
        Ctx c = new Ctx();
        c.screenW = FakeNode.SW;
        c.screenH = FakeNode.SH;
        c.splashLive = true;
        c.elapsedMs = 0;
        return c;
    }

    /** 弹窗场景（抽屉是 Dialog——popup 路径） */
    static Ctx popupCtx() {
        Ctx c = new Ctx();
        c.screenW = FakeNode.SW;
        c.screenH = FakeNode.SH;
        c.popupLive = true;
        c.elapsedMs = 500;
        return c;
    }

    static Hit match(FakeNode root, Ctx ctx) {
        return RuleEngine.match(root, ctx);
    }

    /** 构造 issue#43/44 原场景：右侧抽屉菜单（4+ 统一行 + 「跳过」陷阱项） */
    static FakeNode buildDrawer(String trapText) {
        FakeNode root = FakeNode.root("androidx.fragment.app.BaseCustomDialogFragment$a");
        FakeNode list = FakeNode.node("android.widget.LinearLayout", 853, 300, 1080, 2200);
        root.add(list);
        String[] items = {"播放历史", "录音", "扫一扫", "切换模式"};
        int top = 300;
        for (String s : items) {
            FakeNode row = FakeNode.node("android.widget.RelativeLayout", 853, top, 1080, top + 120).click();
            FakeNode tv = FakeNode.node("android.widget.TextView", 870, top + 30, 1000, top + 90).txt(s);
            row.add(tv);
            list.add(row);
            top += 120;
        }
        // 陷阱行：菜单里的「跳过」字样项（视频类 App 常见「跳过片头」设置行）
        FakeNode trapRow = FakeNode.node("android.widget.RelativeLayout", 853, top, 1080, top + 120).click();
        trapRow.add(FakeNode.node("android.widget.TextView", 870, top + 30, 940, top + 90).txt(trapText));
        list.add(trapRow);
        return root;
    }

    public static void main(String[] args) {
        System.out.println("═══ v4.5.0 乱点根治 + 提速 + 解耦 专项单测（屏幕 1080x2400）═══\n");

        /* ───── V1 抽屉菜单乱点根治（issue#43/44 原场景） ───── */
        System.out.println("── V1 抽屉菜单：两条路径都绝不点「跳过」列表项 ──");

        FakeNode drawer = buildDrawer("跳过");
        Hit h = match(drawer, splashCtx());
        check("V1a rule 路径：抽屉「跳过」项不命中", h == null,
                "实际命中: " + (h == null ? "无" : h.appId + "@" + h.label));
        ScanFind f = AdDecisionCore.walk(drawer, splashCtx());
        check("V1b walk 路径：不入 skipCands", f.skipCands.isEmpty(),
                "候选数: " + f.skipCands.size());
        check("V1c walk 路径：不设 foundSkip", !f.foundSkip);
        Decision d = AdDecisionCore.decide(f, splashCtx());
        check("V1d decide 兜底：零动作", d.action.equals(AdDecisionCore.A_NONE),
                "实际: " + d.action + "/" + d.reason);

        FakeNode drawerPop = buildDrawer("跳过");
        Hit hp = match(drawerPop, popupCtx());
        check("V1e 弹窗路径（popupLive）：同样不命中", hp == null);
        ScanFind fp = AdDecisionCore.walk(drawerPop, popupCtx());
        check("V1f 弹窗 walk：零候选", fp.skipCands.isEmpty() && !fp.foundSkip);

        FakeNode drawer2 = buildDrawer("跳过广告 5");
        Hit h2 = match(drawer2, splashCtx());
        check("V1g 抽屉倒计时文案「跳过广告 5」同样拒绝", h2 == null,
                "实际: " + (h2 == null ? "无" : h2.appId));

        FakeNode drawer3 = buildDrawer("跳过片头");
        Hit h3 = match(drawer3, splashCtx());
        check("V1h 「跳过片头」设置项拒绝", h3 == null,
                "实际: " + (h3 == null ? "无" : h3.appId));

        /* ───── V2 形状门矩阵 ───── */
        System.out.println("\n── V2 形状门：全宽行否决 · 长条按钮放行 · SDK 豁免 ──");

        // 全宽行（57% 屏宽、不可点标签——列表项/横幅形态）→ 否决
        FakeNode wide = FakeNode.root("com.app.Splash");
        FakeNode wideRow = FakeNode.node("android.widget.LinearLayout", 0, 2000, 700, 2120).click();
        wideRow.add(FakeNode.node("android.widget.TextView", 30, 2030, 650, 2090).txt("跳过广告"));
        wide.add(wideRow);
        check("V2a 全宽行（57% 宽不可点标签）否决", match(wide, splashCtx()) == null);

        // 自身可点的长条跳过按钮（65% 宽——真实广告底部按钮形态）→ 放行
        FakeNode bar = FakeNode.root("com.app.Splash");
        FakeNode barBtn = FakeNode.node("android.widget.Button", 190, 2050, 890, 2170).txt("跳过广告").click();
        bar.add(barBtn);
        Hit barHit = match(bar, splashCtx());
        check("V2b 可点长条按钮（65% 宽）放行", barHit != null,
                "实际: " + (barHit == null ? "null" : barHit.appId));

        // SDK 广告窗豁免：抽屉形态但在 SDK 窗内 → 仍可命中（广告窗内菜单形态极罕见，
        // 但 SDK 窗上下文已是高置信广告场景，形状门让位于召回率）
        FakeNode sdkDrawer = buildDrawer("跳过");
        Ctx sdkCtx = splashCtx();
        sdkCtx.sdkAdWin = true;
        Hit sdkHit = match(sdkDrawer, sdkCtx);
        check("V2c SDK 广告窗豁免：抽屉内仍命中", sdkHit != null,
                "实际: " + (sdkHit == null ? "null" : sdkHit.appId));

        // 横排按钮组（4 个同宽按钮并排——不是竖列）→ 不误伤
        FakeNode hRow = FakeNode.root("com.app.Splash");
        FakeNode hContainer = FakeNode.node("android.widget.LinearLayout", 0, 100, 1080, 260);
        hRow.add(hContainer);
        int hx = 40;
        for (int i = 0; i < 4; i++) {
            FakeNode btn = FakeNode.node("android.widget.Button", hx, 120, hx + 220, 240)
                    .txt(i == 0 ? "跳过" : "按钮" + i).click();
            hContainer.add(btn);
            hx += 260;
        }
        Hit hRowHit = match(hRow, splashCtx());
        check("V2d 横排按钮组不误伤（「跳过」仍命中）", hRowHit != null,
                "实际: " + (hRowHit == null ? "null" : hRowHit.appId));

        /* ───── V3 isMenuListRow 单元 ───── */
        System.out.println("\n── V3 isMenuListRow 单元 ──");

        FakeNode dr = buildDrawer("跳过");
        FakeNode trapTv = null;
        outer:
        for (FakeNode row : dr.children.get(0).children) {
            for (FakeNode tv : row.children) {
                if ("跳过".equals(tv.text)) { trapTv = tv; break outer; }
            }
        }
        check("V3a 抽屉「跳过」行 → true", AdDecisionCore.isMenuListRow(trapTv));
        check("V3b 抽屉根容器（非行内）→ false", !AdDecisionCore.isMenuListRow(dr));
        check("V3c 横排按钮组 → false", !AdDecisionCore.isMenuListRow(hRow.children.get(0).children.get(0)));
        check("V3d 孤立按钮（无兄弟）→ false", !AdDecisionCore.isMenuListRow(barBtn));

        // 仅 3 行的短列表（不达 4 行阈值）→ false（宁漏勿错杀）
        FakeNode shortList = FakeNode.node("android.widget.LinearLayout", 0, 100, 1080, 700);
        for (int i = 0; i < 3; i++) {
            FakeNode row = FakeNode.node("android.widget.RelativeLayout", 0, 100 + i * 160, 1080, 240 + i * 160).click();
            row.add(FakeNode.node("android.widget.TextView", 40, 140 + i * 160, 300, 200 + i * 160).txt(i == 0 ? "跳过" : "项" + i));
            shortList.add(row);
        }
        check("V3e 3 行短列表（<4 阈值）→ false", !AdDecisionCore.isMenuListRow(shortList.children.get(0).children.get(0)));

        check("V3f null 入参 → false", !AdDecisionCore.isMenuListRow(null));

        /* ───── V4 主路径回归红线（v4.4.0 及之前全部能力不回退） ───── */
        System.out.println("\n── V4 主路径回归：正常开屏 0ms 命中不受形状门影响 ──");

        FakeNode keep = FakeNode.root("com.xingin.keep.splash.SplashActivity");
        FakeNode keepAd = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        keepAd.click();
        FakeNode keepSkip = FakeNode.node("android.widget.TextView", 880, 100, 1030, 180).txt("跳过").click();
        keep.add(keepAd).add(keepSkip);
        Hit k = match(keep, splashCtx());
        check("V4a Keep 右上「跳过」仍 0ms 命中", k != null && "global_exact".equals(k.appId));

        FakeNode xly = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        Ctx xlyCtx = splashCtx();
        xlyCtx.pkg = "com.ximalaya.ting.android";
        FakeNode xlyExact = FakeNode.node("android.widget.TextView", 780, 120, 1040, 200)
                .txt("跳过广告").click();
        xly.add(xlyExact);
        Hit xe = match(xly, xlyCtx);
        check("V4b 喜马拉雅分应用「跳过广告」精确命中", xe != null && "app_exact".equals(xe.appId),
                "实际: " + (xe == null ? "null" : xe.appId));

        FakeNode xly2 = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode xlyCd = FakeNode.node("android.widget.TextView", 780, 120, 1040, 200)
                .txt("跳过广告 5").click();
        xly2.add(xlyCd);
        Hit x = match(xly2, xlyCtx);
        check("V4b2 倒计时形态「跳过广告 5」→ global_pattern（门控后出手）",
                x != null && "global_pattern".equals(x.appId),
                        "实际: " + (x == null ? "null" : x.appId));
        check("V4c 倒计时解析仍为 5s", AdDecisionCore.parseCountdown("跳过广告 5") == 5);

        FakeNode vidTree = FakeNode.root("com.bytedance.sdk.openadsdk.stub.activity.Stub_Std_Splash_Activity");
        FakeNode vidBtn = FakeNode.node("android.widget.ImageView", 900, 80, 1040, 200)
                .vid("com.ss.android.lark:id/tt_splash_skip_btn");
        vidTree.add(vidBtn);
        Hit v = match(vidTree, splashCtx());
        check("V4d 穿山甲 vid skip 键仍命中", v != null && "global_vid".equals(v.appId),
                "实际: " + (v == null ? "null" : v.appId));

        FakeNode engTree = FakeNode.root("com.app.Splash");
        FakeNode engBtn = FakeNode.node("android.widget.TextView", 850, 110, 1050, 190).txt("Skip Ad").click();
        engTree.add(engBtn);
        Hit e = match(engTree, splashCtx());
        check("V4e 英文 Skip Ad 仍命中", e != null && "global_exact".equals(e.appId),
                "实际: " + (e == null ? "null" : e.appId));

        // 底部右下角跳过键（learnableSkipShape 允许的形态）不受伤
        FakeNode bottom = FakeNode.root("com.app.Splash");
        FakeNode bottomBtn = FakeNode.node("android.widget.TextView", 800, 2150, 1040, 2300).txt("跳过").click();
        bottom.add(bottomBtn);
        Hit b = match(bottom, splashCtx());
        check("V4f 右下角「跳过」仍命中（真机反馈位置）", b != null);

        /* ───── V5 matchText 直调（原生文本探针同路径） ───── */
        System.out.println("\n── V5 原生文本探针路径（matchText 直调，与 DFS 零行为差异） ──");

        Ctx probeCtx = splashCtx();
        Hit menuProbe = RuleEngine.matchText("跳过", new int[]{870, 810, 940, 870}, trapTv, probeCtx);
        check("V5a 探针在菜单行内 → null", menuProbe == null,
                "实际: " + (menuProbe == null ? "null" : menuProbe.appId));

        Hit cleanProbe = RuleEngine.matchText("跳过", new int[]{880, 100, 1030, 180}, keepSkip, probeCtx);
        check("V5b 探针在正常位置 → 命中", cleanProbe != null && "global_exact".equals(cleanProbe.appId),
                "实际: " + (cleanProbe == null ? "null" : cleanProbe.appId));

        System.out.println();
        System.out.println("═══ 结果：" + pass + " 通过 / " + fail + " 失败 ═══");
        if (fail > 0) {
            System.out.println("存在 FAIL，禁止发版！");
            System.exit(1);
        }
        System.out.println("全部通过 ✅");
    }

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        }
    }
}
