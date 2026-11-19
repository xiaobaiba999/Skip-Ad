package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 开屏倒计时/位置解耦单元测试（v7.5.0 · 用户十四轮实锤：「开屏按钮跳过
 * 不是每个都带倒计时，不是每次每个按钮都是一个位置」——好多其他 App 的
 * 开屏都跳不了的头号死因：① 倒计时数字与「跳过」文案分属两个节点 →
 * parseCountdown(label)=0 → 首击立即出手白点，3 击耐心预算烧完干看广告
 * 走完；② 门控只在 clickAttempts>=1 才生效——首击永远白点；③ 图片跳过键
 * 在左/右缘中部（非顶 14%/底 18% 角落带）零收集）。
 * 与 FeedAdGuard 同规约（纯 Java 零 Android 依赖，ECJ -1.8 编译 JVM 运行）。
 * ─────────────────────────────────────────────────────────────
 * 断言规划（check/checkEq 总数 52）：
 *  S1 独立倒计时数字解析（16）/ S2 门控纯函数（9）
 *  S3 中部边带几何（8）/ S4 walk 兄弟倒计时并入（6）
 *  S5 RuleEngine.windowCountdownHint（4）/ S6 decide L1.5 角标关闭（5）
 *  S7 L4 分层门控 strongOnly（4）。
 * 标准屏 1080×2400；跳过键 {900,80,1030,160}（右上角·可点）。
 */
public final class SplashCountdownTest {

    public static void main(String[] args) {
        /* ════ S1 独立倒计时数字解析（16 项）════ */
        TestHost.suite("SplashCountdown · S1 独立倒计时数字解析（3/5s/3秒 → 3/5/3）");
        TestHost.checkEq(3, AdDecisionCore.standaloneCountdownSeconds("3"), "「3」→3");
        TestHost.checkEq(5, AdDecisionCore.standaloneCountdownSeconds("5s"), "「5s」→5");
        TestHost.checkEq(5, AdDecisionCore.standaloneCountdownSeconds("5S"), "「5S」→5");
        TestHost.checkEq(3, AdDecisionCore.standaloneCountdownSeconds("3秒"), "「3秒」→3");
        TestHost.checkEq(12, AdDecisionCore.standaloneCountdownSeconds("12"), "「12」→12");
        TestHost.checkEq(15, AdDecisionCore.standaloneCountdownSeconds("15"), "「15」→15（COUNTDOWN_MAX_S 上限内）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("16"), "「16」→0（超上限拒）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("0"), "「0」→0（0 非倒计时）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("跳过"), "「跳过」→0（非独立数字）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("3.5"), "「3.5」→0（小数点非单位尾）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("1/3"), "「1/3」→0（页码非倒计时）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("123"), "「123」→0（三位数拒）");
        TestHost.checkEq(3, AdDecisionCore.standaloneCountdownSeconds(" 3 "), "「 3 」→3（trim 归一）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("3秒后"), "「3秒后」→0（含「后」非纯单位尾）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("跳过3"), "「跳过3」→0（带跳过词的走 parseCountdown 老路）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds(null), "null→0");

        /* ════ S2 门控纯函数（v7.6.0 激进模式：首击零门控·重击等将尽·6s 封顶）════ */
        TestHost.suite("SplashCountdown · S2 门控纯函数（v7.6.0 激进：首击零门控）");
        TestHost.checkEq(3880L, AdDecisionCore.countdownUntil(3, 1000L), "countdownUntil(3,1000)=1000+3000-120");
        TestHost.check(!AdDecisionCore.decorativeCountdown(1000L, 1001L), "1ms 锚点新鲜非装饰");
        TestHost.check(AdDecisionCore.decorativeCountdown(1000L, 2700L), "1700ms 锚点未刷新=装饰数字");
        TestHost.check(AdDecisionCore.gateCountdown(3, 1000L, 1000L), "重击门控·锚点新鲜·未到将尽 → 门控等待");
        TestHost.check(AdDecisionCore.gateCountdown(3, 1000L, 1500L), "锚点 500ms 内仍新鲜 → 门控等待（真倒计时每秒跳重置锚点）");
        TestHost.check(!AdDecisionCore.gateCountdown(1, 3000L, 4000L), "到期开闸（锚点 1000ms 新鲜·已到将尽时刻 until=3880）");
        TestHost.check(!AdDecisionCore.gateCountdown(0, 1000L, 1000L), "无倒计时（cdN=0）→ 零门控（用户实锤：不是每个都带倒计时）");
        TestHost.check(!AdDecisionCore.gateCountdown(3, 1000L, 3000L), "锚点 2s 未刷新=装饰数字 → 逃生开闸");
        TestHost.check(!AdDecisionCore.gateCountdown(3, 1000L, 5000L), "陈旧锚点任何时刻 → 开闸出手（装饰逃生兜底）");
        /* v7.6.0 激进模式新断言 */
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(3, 1000L, 1000L, 0),
                "第 0 击·真 3s 倒计时·未到将尽 → 首击零门控立即出手（用户实锤「非常非常慢」根治）");
        TestHost.check(AdDecisionCore.gateCountdownAggressive(3, 1000L, 1000L, 1),
                "第 1 击（首击白点后）·未到将尽 → 重击门控等待（真倒计时按钮命中时机）");
        TestHost.check(AdDecisionCore.gateCountdownAggressive(5, 1000L, 1000L, 2),
                "第 2 击同样门控（耐心预算内等将尽）");
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(0, 1000L, 1000L, 1),
                "无倒计时重击也零门控（cdN=0）");
        TestHost.checkEq(6, AdDecisionCore.cappedRetryCd(15), "cappedRetryCd(15)=6（15s 大数字封顶 6s——误检兜底）");
        TestHost.checkEq(3, AdDecisionCore.cappedRetryCd(3), "cappedRetryCd(3)=3（≤6 不动）");
        TestHost.checkEq(0, AdDecisionCore.cappedRetryCd(0), "cappedRetryCd(0)=0");
        TestHost.check(AdDecisionCore.gateCountdown(15, 5000L, 5000L),
                "15s 倒计时·锚点新鲜（数字刚跳）·封顶后 until=5000+5880=10880 → 仍门控但最多等 6s");
        TestHost.check(!AdDecisionCore.gateCountdown(15, 1000L, 7200L),
                "15s 倒计时过 6s 封顶（capped until=6880<7200）→ 开闸出手（不再无限等）");

        /* ════ S3 中部边带几何（8 项）════ */
        TestHost.suite("SplashCountdown · S3 中部边带几何（左/右缘任意高度）");
        Ctx g = new Ctx();
        g.screenW = 1080;
        g.screenH = 2400;
        TestHost.check(AdDecisionCore.midEdgeLike(new int[]{1000, 1200, 1080, 1280}, 1080, 2400),
                "右缘中部 80×80 无字控件 → 中部边带候选");
        TestHost.check(AdDecisionCore.midEdgeLike(new int[]{0, 1200, 80, 1280}, 1080, 2400),
                "左缘中部 80×80 → 中部边带候选");
        TestHost.check(!AdDecisionCore.midEdgeLike(new int[]{1000, 80, 1080, 160}, 1080, 2400),
                "右上角落带控件 → 归角落（非中部——原门控语义保持）");
        TestHost.check(!AdDecisionCore.midEdgeLike(new int[]{500, 1150, 580, 1250}, 1080, 2400),
                "中央控件 → 非边带拒");
        TestHost.check(!AdDecisionCore.midEdgeLike(new int[]{700, 1200, 1080, 1900}, 1080, 2400),
                "380×700 超尺寸 → 拒（35% 宽 × 20% 高门）");
        TestHost.check(!AdDecisionCore.midEdgeLike(new int[]{1050, 1200, 1060, 1240}, 1080, 2400),
                "10px 宽 <24px 最小尺寸拒");
        TestHost.check(AdDecisionCore.learnableSkipShape(new int[]{1000, 1200, 1080, 1280}, 1080, 2400),
                "learnableSkipShape：右缘中部图片跳过键可学习（旧版非角落/顶/底全拒）");
        TestHost.check(!AdDecisionCore.learnableSkipShape(new int[]{300, 1100, 780, 1250}, 1080, 2400),
                "learnableSkipShape：中央大标签（480×150）仍拒");

        /* ════ S4 walk 兄弟倒计时并入（6 项）════ */
        TestHost.suite("SplashCountdown · S4 walk 兄弟节点倒计时并入");
        Ctx c1 = new Ctx();
        c1.screenW = 1080;
        c1.screenH = 2400;
        c1.splashLive = true;
        c1.budgetNodes = 600;
        // 树①：「跳过」键 + 近旁兄弟数字「3」（同排）
        SimNode t1 = new SimNode();
        t1.boundsArr = new int[]{0, 0, 1080, 2400};
        t1.cls = "android.widget.FrameLayout";
        t1.add(skipNode());
        t1.add(new SimNode("3", 850, 90, 890, 150));
        ScanFind f1 = AdDecisionCore.walk(t1, c1);
        TestHost.checkEq(3, f1.countdownN, "「跳过」+近旁数字「3」→ countdownN=3（首击不再白点）");
        // 树②：「跳过」键 + 顶带数字（远距但在倒计时区）
        SimNode t2 = new SimNode();
        t2.boundsArr = new int[]{0, 0, 1080, 2400};
        t2.cls = "android.widget.FrameLayout";
        t2.add(skipNode());
        t2.add(new SimNode("5", 60, 90, 100, 150));
        ScanFind f2 = AdDecisionCore.walk(t2, c1);
        TestHost.checkEq(5, f2.countdownN, "顶带独立数字「5」（cy=120≤35%）→ 并入 5");
        // 树③：「跳过」键 + 中部远距数字（不在区不近旁）
        SimNode t3 = new SimNode();
        t3.boundsArr = new int[]{0, 0, 1080, 2400};
        t3.cls = "android.widget.FrameLayout";
        t3.add(skipNode());
        t3.add(new SimNode("5", 500, 1200, 540, 1260));
        ScanFind f3 = AdDecisionCore.walk(t3, c1);
        TestHost.checkEq(0, f3.countdownN, "中部远距数字（非区非近旁）→ 不并入（宁漏勿误）");
        // 树④：无数字
        SimNode t4 = new SimNode();
        t4.boundsArr = new int[]{0, 0, 1080, 2400};
        t4.cls = "android.widget.FrameLayout";
        t4.add(skipNode());
        ScanFind f4 = AdDecisionCore.walk(t4, c1);
        TestHost.checkEq(0, f4.countdownN, "无独立数字 → countdownN=0（零门控照常出手）");
        // 树⑤：底带数字「15」
        SimNode t5 = new SimNode();
        t5.boundsArr = new int[]{0, 0, 1080, 2400};
        t5.cls = "android.widget.FrameLayout";
        t5.add(skipNode());
        SimNode d15 = new SimNode("15", 60, 2100, 140, 2160);
        t5.add(d15);
        ScanFind f5 = AdDecisionCore.walk(t5, c1);
        TestHost.checkEq(15, f5.countdownN, "底带独立数字「15」（cy=2130≥75%）→ 并入 15");
        // 树⑥：超上限数字「16」（拒）
        SimNode t6 = new SimNode();
        t6.boundsArr = new int[]{0, 0, 1080, 2400};
        t6.cls = "android.widget.FrameLayout";
        t6.add(skipNode());
        t6.add(new SimNode("16", 850, 90, 920, 150));
        ScanFind f6 = AdDecisionCore.walk(t6, c1);
        TestHost.checkEq(0, f6.countdownN, "独立数字「16」超 COUNTDOWN_MAX_S → 拒");

        /* ════ S5 RuleEngine.windowCountdownHint（4 项）════ */
        TestHost.suite("SplashCountdown · S5 规则路径窗级倒计时提示");
        TestHost.checkEq(3, RuleEngine.windowCountdownHint(t1, c1, new int[]{900, 80, 1030, 160}),
                "近旁命中键 → 提示 3（规则路径首击门控）");
        TestHost.checkEq(5, RuleEngine.windowCountdownHint(t2, c1, new int[]{900, 80, 1030, 160}),
                "顶带数字 → 提示 5");
        TestHost.checkEq(0, RuleEngine.windowCountdownHint(t3, c1, new int[]{900, 80, 1030, 160}),
                "中部远距 → 提示 0（不门控）");
        SimNode empty = new SimNode();
        empty.boundsArr = new int[]{0, 0, 1080, 2400};
        TestHost.checkEq(0, RuleEngine.windowCountdownHint(empty, c1, null),
                "空树 → 提示 0");

        /* ════ S6 decide L1.5 角标在场关闭（5 项）════ */
        TestHost.suite("SplashCountdown · S6 L1.5 角标在场关闭（任意位置）");
        // 树A：角标「广告」+ 底部右侧「×」关闭（旧 L1.6 顶部闸必拒——L1.5 收）
        SimNode ta = new SimNode();
        ta.boundsArr = new int[]{0, 0, 1080, 2400};
        ta.cls = "android.widget.FrameLayout";
        ta.add(new SimNode("广告", 60, 100, 190, 180));
        SimNode closeA = new SimNode("×", 960, 1800, 1060, 1880);
        closeA.clickable = true;
        ta.add(closeA);
        Ctx ca = new Ctx();
        ca.screenW = 1080;
        ca.screenH = 2400;
        ca.splashLive = true;
        ca.elapsedMs = 1000;
        ca.budgetNodes = 600;
        ScanFind fa = AdDecisionCore.walk(ta, ca);
        Decision da = AdDecisionCore.decide(fa, ca);
        TestHost.check("badge_close".equals(da.reason), "角标+底带× → L1.5 badge_close（旧顶部闸全灭的位置）");
        // 树B：无角标 + 底部右侧「×」（L1.6 顶部闸拒 → 无关闭动作）
        SimNode tb = new SimNode();
        tb.boundsArr = new int[]{0, 0, 1080, 2400};
        tb.cls = "android.widget.FrameLayout";
        SimNode closeB = new SimNode("×", 960, 1800, 1060, 1880);
        closeB.clickable = true;
        tb.add(closeB);
        Ctx cb = new Ctx();
        cb.screenW = 1080;
        cb.screenH = 2400;
        cb.splashLive = true;
        cb.elapsedMs = 1000;
        cb.budgetNodes = 600;
        ScanFind fb = AdDecisionCore.walk(tb, cb);
        Decision db = AdDecisionCore.decide(fb, cb);
        TestHost.check(!"badge_close".equals(db.reason) && !"splash_close".equals(db.reason),
                "无角标+底部× → L1.5/L1.6 双拒（角标是在场闸本体——宁漏勿误）");
        // 树C：角标 + 顶部「×」（L1.5 也收——先于 L1.6）
        SimNode tc = new SimNode();
        tc.boundsArr = new int[]{0, 0, 1080, 2400};
        tc.cls = "android.widget.FrameLayout";
        tc.add(new SimNode("广告", 60, 100, 190, 180));
        SimNode closeC = new SimNode("×", 960, 100, 1060, 180);
        closeC.clickable = true;
        tc.add(closeC);
        Ctx cc = new Ctx();
        cc.screenW = 1080;
        cc.screenH = 2400;
        cc.splashLive = true;
        cc.elapsedMs = 1000;
        cc.budgetNodes = 600;
        ScanFind fc = AdDecisionCore.walk(tc, cc);
        Decision dc = AdDecisionCore.decide(fc, cc);
        TestHost.check("badge_close".equals(dc.reason), "角标+顶部× → L1.5 优先收（先于 L1.6）");
        // 树D：角标+×+长文本（正常内容页特征）→ L1.5 拒
        SimNode td = new SimNode();
        td.boundsArr = new int[]{0, 0, 1080, 2400};
        td.cls = "android.widget.FrameLayout";
        td.add(new SimNode("广告", 60, 100, 190, 180));
        SimNode closeD = new SimNode("×", 960, 1800, 1060, 1880);
        closeD.clickable = true;
        td.add(closeD);
        td.add(new SimNode("这是一篇正常文章的长文本内容判定样本标题栏文字行超过四十个字符的阈值检查样本文案数据行", 60, 400, 1020, 460));
        Ctx cd = new Ctx();
        cd.screenW = 1080;
        cd.screenH = 2400;
        cd.splashLive = true;
        cd.elapsedMs = 1000;
        cd.budgetNodes = 600;
        ScanFind fd = AdDecisionCore.walk(td, cd);
        Decision dd = AdDecisionCore.decide(fd, cd);
        TestHost.check(!"badge_close".equals(dd.reason), "长文本在场 → L1.5 拒（正常内容页红线）");
        // 树E：角标+×+可滚动容器 → L1.5 拒（信息流窗归两段式）
        SimNode te = new SimNode();
        te.boundsArr = new int[]{0, 0, 1080, 2400};
        te.cls = "android.widget.FrameLayout";
        te.add(new SimNode("广告", 60, 100, 190, 180));
        SimNode closeE = new SimNode("×", 960, 1800, 1060, 1880);
        closeE.clickable = true;
        te.add(closeE);
        SimNode scroll = new SimNode("", 0, 200, 1080, 2200);
        scroll.scrollable = true;
        te.add(scroll);
        Ctx ce = new Ctx();
        ce.screenW = 1080;
        ce.screenH = 2400;
        ce.splashLive = true;
        ce.elapsedMs = 1000;
        ce.budgetNodes = 600;
        ScanFind fe = AdDecisionCore.walk(te, ce);
        Decision de = AdDecisionCore.decide(fe, ce);
        TestHost.check(!"badge_close".equals(de.reason), "可滚动窗 → L1.5 拒（信息流两段式领地）");

        /* ════ S7 L4 分层门控 strongOnly（4 项）════ */
        TestHost.suite("SplashCountdown · S7 L4 中部边带分层门控（strongOnly）");
        // 树F：右缘中部图片跳过键（无字可点）+ 无任何广告信号 → 不出手（FAB 防线）
        SimNode tf = new SimNode();
        tf.boundsArr = new int[]{0, 0, 1080, 2400};
        tf.cls = "android.widget.FrameLayout";
        SimNode imgF = new SimNode("", 1000, 1200, 1080, 1280);
        imgF.clickable = true;
        tf.add(imgF);
        Ctx cf = new Ctx();
        cf.screenW = 1080;
        cf.screenH = 2400;
        cf.splashLive = true;
        cf.elapsedMs = 1000;
        cf.budgetNodes = 600;
        ScanFind ff = AdDecisionCore.walk(tf, cf);
        Decision df = AdDecisionCore.decide(ff, cf);
        TestHost.check(AdDecisionCore.A_NONE.equals(df.action),
                "右缘中部图片键·零广告信号 → 零出手（树极简兜底不适用——FAB 误点防线）");
        // 树G：同上 + 角标「广告」在场（强广告形态）→ corner_image_skip 出手
        SimNode tg = new SimNode();
        tg.boundsArr = new int[]{0, 0, 1080, 2400};
        tg.cls = "android.widget.FrameLayout";
        tg.add(new SimNode("广告", 60, 100, 190, 180));
        SimNode imgG = new SimNode("", 1000, 1200, 1080, 1280);
        imgG.clickable = true;
        tg.add(imgG);
        Ctx cg = new Ctx();
        cg.screenW = 1080;
        cg.screenH = 2400;
        cg.splashLive = true;
        cg.elapsedMs = 1000;
        cg.budgetNodes = 600;
        ScanFind fg = AdDecisionCore.walk(tg, cg);
        Decision dg = AdDecisionCore.decide(fg, cg);
        TestHost.check("corner_image_skip".equals(dg.reason),
                "右缘中部图片键+角标在场（强广告形态）→ 手势点跳过键（位置解耦）");
        // 树H：右上角落带图片键（原角落带）+ 无信号 + 树极简 ≤60 → 原语义出手保持
        SimNode th = new SimNode();
        th.boundsArr = new int[]{0, 0, 1080, 2400};
        th.cls = "android.widget.FrameLayout";
        SimNode imgH = new SimNode("", 1000, 80, 1080, 160);
        imgH.clickable = true;
        th.add(imgH);
        Ctx ch = new Ctx();
        ch.screenW = 1080;
        ch.screenH = 2400;
        ch.splashLive = true;
        ch.elapsedMs = 1000;
        ch.budgetNodes = 600;
        ScanFind fh = AdDecisionCore.walk(th, ch);
        Decision dh = AdDecisionCore.decide(fh, ch);
        TestHost.check("corner_image_skip".equals(dh.reason),
                "右上角落带图片键·树极简 → 原语义保持（≤60 兜底照常出手）");
        // 树I：右缘中部图片键 + SDK 窗指纹 → 强形态出手
        SimNode ti = new SimNode();
        ti.boundsArr = new int[]{0, 0, 1080, 2400};
        ti.cls = "android.widget.FrameLayout";
        SimNode imgI = new SimNode("", 1000, 1200, 1080, 1280);
        imgI.clickable = true;
        ti.add(imgI);
        Ctx ci = new Ctx();
        ci.screenW = 1080;
        ci.screenH = 2400;
        ci.splashLive = true;
        ci.sdkAdWin = true;
        ci.elapsedMs = 1000;
        ci.budgetNodes = 600;
        ScanFind fi = AdDecisionCore.walk(ti, ci);
        Decision di = AdDecisionCore.decide(fi, ci);
        TestHost.check("corner_image_skip".equals(di.reason),
                "右缘中部图片键+SDK 窗指纹 → 强形态出手");

        TestHost.done();
    }

    /** 右上角「跳过」键（可点、小形态——walk 真候选形态）。 */
    private static SimNode skipNode() {
        SimNode n = new SimNode("跳过", 900, 80, 1030, 160);
        n.clickable = true;
        return n;
    }
}
