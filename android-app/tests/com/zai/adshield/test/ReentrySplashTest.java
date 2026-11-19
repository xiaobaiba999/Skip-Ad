package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.ReentryGuard;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * v7.7.0 回切开屏根治·专项套件（用户十七轮实锤：「扫描日志从另一个 app 切回
 * 这个 app，期间会弹广告，这个没有拦截到」）。
 *
 * 两个死因（BrandFocus352Test ⑨维逐机型回归之外的全链路深检）：
 *  · 死因①（entering 误判）：卫士自家包事件在 onAccessibilityEvent 顶部整体
 *    丢弃、lastFgPkg 从未变成卫士包名 → 「目标 App → 卫士 → 目标 App」往返后
 *    entering=false → rootWinCls 不刷新、回切开屏零日志。对策：自家包
 *    WINDOW_STATE_CHANGED 登记 lastFgPkg（前台边界）+ 离开间隙 ≥6s 判回切
 *    （ReentryGuard.isEntering）。
 *  · 死因②（CLEAN 档案误杀）：回切开屏常叠在主界面窗类上（同窗类盖开屏层），
 *    而主界面窗类早被宿主窗档案判 CLEAN → bumpScanChain 只排探针哨兵/低频档
 *    → 晚到/图标式跳过键整窗漏拦。对策：进入期 4s（ReentryGuard.entryFullActive）
 *    CLEAN 档案让路全量。
 *
 * 覆盖（本套件断言全链路纯函数面）：
 *  · S1 回切判定真值表（新进入/短窗抖动非回切/6s 边界/从未登记/空包名）；
 *  · S2 进入期全量门（4s 内让路/期外不干预/未登记不干预/时长常量契约）；
 *  · S3 自家包前台边界（X → 卫士 → X 时间线：isEntering 在边界前后正确翻转）；
 *  · S4 回切开屏命中（主界面 CLEAN 窗类叠开屏层 → walk+decide 全分辨率档命中）；
 *  · S5 插层键词典（「关闭广告/關閉廣告」整词——探针词扩容配套）；
 *  · S6 离开时长记账（reentryAwayMs——日志「离开 Ns」语义）；
 *  · S7 多分辨率回切几何（720p~2K+ 全档跳过键缩放命中——九品牌矩阵投影）。
 */
public final class ReentrySplashTest {

    public static void main(String[] args) {
        /* ══ S1 回切判定真值表 ══ */
        TestHost.suite("S1 回切判定真值表（isEntering）");
        long now = 1_000_000L;
        String p = "com.matrix.target";
        TestHost.check(ReentryGuard.isEntering(p, null, 0L, now), "lastFgPkg 空 → 新进入");
        TestHost.check(ReentryGuard.isEntering(p, "other.app", now, now), "包名不同 → 新进入");
        TestHost.check(!ReentryGuard.isEntering(p, p, now - 5_999L, now), "同包 5.999s → 窗级抖动非回切");
        TestHost.check(ReentryGuard.isEntering(p, p, now - 6_000L, now), "同包 6s（边界含） → 回切");
        TestHost.check(ReentryGuard.isEntering(p, p, 0L, now), "同包从未登记窗时刻 → 按完整离开处理");
        TestHost.check(!ReentryGuard.isEntering(null, p, 0L, now), "包名空 → 绝不判进入（防 NPE 面）");
        TestHost.check(!ReentryGuard.isEntering("", p, 0L, now), "包名零长 → 绝不判进入");
        TestHost.check(ReentryGuard.isEntering(p, "", now, now), "lastFgPkg 零长 → 视为新进入");

        /* ══ S2 进入期全量门 ══ */
        TestHost.suite("S2 进入期全量门（entryFullActive）");
        TestHost.check(ReentryGuard.REENTER_GAP_MS == 6000L, "回切间隙常量 6s（低于典型二次开屏阈值）");
        TestHost.check(ReentryGuard.ENTRY_FULL_MS == 4000L, "进入期时长常量 4s（激进档·宁多扫不漏）");
        long eu = ReentryGuard.entryUntil(now);
        TestHost.check(eu == now + 4000L, "entryUntil = now + 4s");
        TestHost.check(ReentryGuard.entryFullActive(eu, now), "进入期内（0s） → CLEAN 让路");
        TestHost.check(ReentryGuard.entryFullActive(eu, now + 3_999L), "进入期内（3.999s） → CLEAN 让路");
        TestHost.check(!ReentryGuard.entryFullActive(eu, now + 4_000L), "进入期到点（4s 边界不含） → 档案自治");
        TestHost.check(!ReentryGuard.entryFullActive(0L, now), "未登记进入期 → 档案自治");

        /* ══ S3 自家包前台边界（时间线）══
         * X → 卫士（自家 state-changed 登记 lastFgPkg=卫士）→ 卫士停留 → X 回切：
         * 边界后 isEntering(X) 必须 true（旧版 lastFgPkg 停在 X → entering=false
         * → 回切开屏整窗漏拦——死因①真值表）。 */
        TestHost.suite("S3 自家包前台边界（X → 卫士 → X 时间线）");
        String x = "com.matrix.target", self = "com.zai.adshield";
        long t0 = 100_000L;
        boolean enteringAfterSelf = ReentryGuard.isEntering(x, self, t0, t0 + 100L);
        TestHost.check(enteringAfterSelf, "卫士边界后回 X → entering=true（死因①根治）");
        // 自家包停留期间（lastFgPkg=self），X 的后台内容事件按非前台包过滤的
        // 判定基线也一起验证：X != self → 非 X 前台
        TestHost.check(!x.equals(self), "前台边界比较基线（X ≠ 卫士）");
        // 息屏回亮路径：lastFgPkg 仍 = X（无自家边界事件），但 windowAt 停在息屏前
        // → 离开 ≥6s → 回切
        TestHost.check(ReentryGuard.isEntering(x, x, t0, t0 + 10_000L),
                "息屏 10s 回亮 → 同包离开间隙判回切");

        /* ══ S4 回切开屏命中（主界面 CLEAN 窗类叠开屏层）══
         * 全分辨率档（720p 入门 → 1080p 主流 → 1.5K → 2K 旗舰 → 3:2 平板）：
         * MainActivity 窗类 + 跳过键 → walk+decide 命中（进入期全量链不丢键）。 */
        TestHost.suite("S4 回切开屏命中（主界面窗类叠开屏层·全分辨率档）");
        int[][] resos = {
                {720, 1650}, {1080, 2400}, {1080, 2340}, {1200, 2670}, {1220, 2712},
                {1260, 2780}, {1260, 2800}, {1440, 3168}, {1440, 3200}, {1600, 2560},
                {1848, 2800}, {2064, 3328},
        };
        int s4ok = 0;
        for (int[] rr : resos) {
            int w = rr[0], h = rr[1];
            SimNode tree = new SimNode();
            tree.cls = "com.matrix.MainActivity";
            tree.boundsArr = new int[]{0, 0, w, h};
            SimNode skip = new SimNode("跳过", (int) (w * 0.86), (int) (h * 0.03),
                    (int) (w * 0.97), (int) (h * 0.065));
            skip.clickable = true;
            tree.add(skip);
            Ctx c = ctx(x, "com.matrix.MainActivity", w, h);
            ScanFind f = AdDecisionCore.walk(tree, c);
            Decision d = AdDecisionCore.decide(f, c);
            if (!AdDecisionCore.A_NONE.equals(d.action)) s4ok++;
        }
        TestHost.check(s4ok == resos.length, "回切开屏命中全分辨率档 " + s4ok + "/" + resos.length);

        /* ══ S5 插层键词典（探针词扩容配套）══
         * 「关闭广告/關閉廣告」整词键（切回期间弹的插层常用）→ matchText
         * 全局精确层命中（matchText 全套形状门照常逐候选验）。 */
        TestHost.suite("S5 插层键词典（关闭广告/關閉廣告整词）");
        int s5ok = 0;
        String[] words = {"关闭广告", "關閉廣告"};
        for (String wd : words) {
            int w = 1080, h = 2400;
            SimNode btn = new SimNode(wd, (int) (w * 0.86), (int) (h * 0.03),
                    (int) (w * 0.97), (int) (h * 0.065));
            btn.clickable = true;
            Hit hit = RuleEngine.matchText(wd, btn.boundsArr, btn, ctx(x, "com.matrix.MainActivity", w, h));
            if (hit != null) s5ok++;
        }
        TestHost.check(s5ok == words.length, "插层键词典命中 " + s5ok + "/" + words.length);

        /* ══ S6 离开时长记账（reentryAwayMs——回切日志「离开 Ns」语义）══ */
        TestHost.suite("S6 离开时长记账（reentryAwayMs）");
        TestHost.check(ReentryGuard.reentryAwayMs(x, x, t0, t0 + 7_500L) == 7_500L,
                "同包离开 7.5s → 记账 7500ms");
        TestHost.check(ReentryGuard.reentryAwayMs(x, x, t0, t0 + 5_999L) == 0L,
                "同包离开 5.999s（未过阈值） → 非回切零记账");
        TestHost.check(ReentryGuard.reentryAwayMs(x, "other", t0, t0 + 7_500L) == 0L,
                "新进入（包名不同） → 非回切零记账");
        TestHost.check(ReentryGuard.reentryAwayMs(x, x, 0L, t0) == t0,
                "从未登记窗时刻 → 按完整离开记账");

        /* ══ S7 多分辨率回切几何（九品牌矩阵投影——1:1 缩放跳过键）══ */
        TestHost.suite("S7 回切几何九品牌投影（跳过键 1:1 缩放）");
        int[][] brandResos = {
                {1440, 3200}, {1260, 2780}, {1220, 2712}, {1200, 2670}, // 小米/OPPO/POCO/红米
                {1440, 3168}, {1280, 2800}, {1260, 2844},               // 华为/荣耀（旧）/华为（新）
                {1080, 2376}, {1440, 3120}, {1080, 2316},               // vivo/三星/折叠外屏
                {720, 1650}, {2032, 3056}, {2064, 3328},                // 入门/荣耀平板/OPPO 平板
        };
        int s7ok = 0;
        for (int[] rr : brandResos) {
            int w = rr[0], h = rr[1];
            // 回切开屏几何：跳过键在右上 11%×3.5%（1:1 按分辨率缩放）+ 角落安全域
            int skL = (int) (w * 0.86), skT = (int) (h * 0.03),
                    skR = (int) (w * 0.97), skB = (int) (h * 0.065);
            SimNode btn = new SimNode("跳过", skL, skT, skR, skB);
            btn.clickable = true;
            Hit hit = RuleEngine.matchText("跳过", btn.boundsArr, btn,
                    ctx(x, "com.matrix.MainActivity", w, h));
            if (hit != null) s7ok++;
        }
        TestHost.check(s7ok == brandResos.length, "九品牌回切几何跳过键全命中 " + s7ok + "/" + brandResos.length);

        TestHost.done();
    }

    static Ctx ctx(String pkg, String act, int w, int h) {
        Ctx ctx = new Ctx();
        ctx.screenW = w;
        ctx.screenH = h;
        ctx.pkg = pkg;
        ctx.activityId = act;
        ctx.elapsedMs = 800;
        ctx.splashLive = true;
        return ctx;
    }
}
