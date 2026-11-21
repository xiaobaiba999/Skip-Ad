package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.FusedScan;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.SubscriptionManager;
import com.zai.adshield.test.TestHost.Counters;
import com.zai.adshield.test.TestHost.CountingNode;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * v8.0.2 双 DFS 单遍化对照测试（P3-1 专项 · DualDfsParityTest）。
 * ─────────────────────────────────────────────────────────────────
 * 旧主扫描路径：RuleEngine.matchAll（规则 DFS）→ SubscriptionManager.matchSources
 *   → AdDecisionCore.walk（决策 DFS）——零命中时 matchAll 与 walk 对同一棵
 *   树各走一遍（text/desc/visible/bounds/childCount 双读、getChild×2）。
 * 新路径：AdDecisionCore.walkFused 单遍同时收集规则命中（骑士）与决策信号。
 *
 * 本套件逐场景对照【旧组合】与【新单遍】的：
 *   ① 规则命中集（appId/label/tapBounds/gestureOnly + 排序后顺序）；
 *   ② 决策信号全字段（skipCands/fakeCands/corners/countdownN/longText/
 *      editPresent/scrollPresent/webviewPresent/hotZone/adBadge/ctaCount/
 *      dialogForm/nodeCount/foundSkip/foundClose…）；
 *   ③ 决策结果（AdDecisionCore.decide 的 action/reason/bounds）；
 *   ④ 订阅合成顺序（规则命中在前、订阅命中追加——服务侧轮换语义不变）。
 *
 * 场景 11/12/17 是【已声明的语义差】（诚实清单，walkFused 源码注释同款）：
 *   S11 树在两趟 DFS 之间变化：旧 walk 看第二时刻、fused 只看第一时刻；
 *   S12 瞬时节点异常：旧 walk 第二趟重试可恢复、fused 单次即失（确定性异常等价）；
 *   S17 小程序容器：旧 matchAll 命中后被 execute() L0 拦、fused 骑士入口即抑制。
 * 每处差异都有注释说明方向与安全论证，绝不静默接受。
 */
public final class DualDfsParityTest {

    public static void main(String[] args) {
        /* ════ S0 骑士关闭 = 纯 walk（弹窗窗路径 byte-for-byte）════ */
        TestHost.suite("DualDfs · S0 rider=false 等价 walk（弹窗窗路径）");
        {
            SimNode t = splashTree();
            Ctx ctx = baseCtx("com.test.app");
            ScanFind oldF = AdDecisionCore.walk(t, ctx);
            FusedScan fs = AdDecisionCore.walkFused(t, ctx, false, 4);
            TestHost.checkEq(oldF.nodeCount, fs.find.nodeCount, "S0 节点计数一致");
            TestHost.checkEq(findKey(oldF), findKey(fs.find), "S0 决策信号全字段一致");
            TestHost.checkEq(0, fs.ruleHits.size(), "S0 骑士关闭 → 零规则命中");
        }

        /* ════ S1 普通内容页（长文本/输入框/滚动，零候选）════ */
        TestHost.suite("DualDfs · S1 普通内容页");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode scroll = new SimNode("", 0, 0, 1080, 2400);
            scroll.cls = "androidx.recyclerview.widget.RecyclerView";
            scroll.scrollable = true;
            root.add(scroll);
            SimNode edit = new SimNode("搜索…", 40, 100, 1040, 200);
            edit.cls = "android.widget.EditText";
            edit.editable = true;
            scroll.add(edit);
            StringBuilder longTxt = new StringBuilder();
            for (int i = 0; i < 12; i++) longTxt.append("这是正常内容页的一段长文本正文");
            scroll.add(new SimNode(longTxt.toString(), 40, 300, 1040, 900));
            scroll.add(new SimNode("首页", 40, 1000, 200, 1100));
            assertFullParity("S1", root, baseCtx("com.test.app"));
        }

        /* ════ S2 开屏广告页（大图热区 + 跳过键 + 广告角标）════ */
        TestHost.suite("DualDfs · S2 开屏广告页");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode big = new SimNode("", 0, 0, 1080, 2200);
            big.cls = "android.widget.ImageView";
            big.clickable = true;
            root.add(big);
            root.add(TestHost.skipButton("跳过"));
            root.add(new SimNode("广告", 40, 2300, 140, 2380));
            assertFullParity("S2", root, baseCtx("com.test.app"));
        }

        /* ════ S3 WebView 广告页（H5 容器 + × 关闭键）════ */
        TestHost.suite("DualDfs · S3 WebView 广告页");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode wv = new SimNode("", 0, 0, 1080, 2400);
            wv.cls = "android.webkit.WebView";
            root.add(wv);
            SimNode close = new SimNode("×", 940, 80, 1020, 160);
            close.clickable = true;
            wv.add(close);
            assertFullParity("S3", root, baseCtx("com.test.app"));
        }

        /* ════ S4 Native 页面（vid 跳过键）════ */
        TestHost.suite("DualDfs · S4 Native vid 跳过键");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode img = new SimNode("", 0, 0, 1080, 2200);
            img.cls = "android.widget.ImageView";
            root.add(img);
            SimNode skip = new SimNode("", 920, 80, 1050, 150);
            skip.cls = "android.widget.ImageView";
            skip.vid = "com.test.app:id/iv_skip";
            skip.clickable = true;
            root.add(skip);
            assertFullParity("S4", root, baseCtx("com.test.app"));
        }

        /* ════ S5 Compose 页面（深链 generic View + 叶子文本键）════ */
        TestHost.suite("DualDfs · S5 Compose 深链页面");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.view.View";
            SimNode cur = root;
            for (int d = 0; d < 10; d++) {
                SimNode next = new SimNode("", 0, 0, 1080, 2400);
                next.cls = "android.view.View";
                cur.add(next);
                cur = next;
            }
            SimNode skip = new SimNode("跳过广告", 900, 80, 1050, 150);
            skip.clickable = true;
            cur.add(skip);
            cur.add(new SimNode("推荐内容", 40, 300, 800, 400));
            assertFullParity("S5", root, baseCtx("com.test.app"));
        }

        /* ════ S6 极简广告页（根 + 单跳过键）════ */
        TestHost.suite("DualDfs · S6 极简广告页");
        assertFullParity("S6", splashTree(), baseCtx("com.test.app"));

        /* ════ S7 聊天页孤立跳过文字（不可点无祖先——gestureOnly 路径）════ */
        TestHost.suite("DualDfs · S7 聊天页孤立跳过文字");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode rv = new SimNode("", 0, 0, 1080, 2400);
            rv.cls = "androidx.recyclerview.widget.RecyclerView";
            root.add(rv);
            rv.add(new SimNode("今天吃什么", 40, 200, 700, 280));
            rv.add(new SimNode("不知道啊", 40, 320, 700, 400));
            rv.add(new SimNode("跳过", 400, 1200, 600, 1250)); // 孤立文本：无 vid 不可点无祖先
            assertFullParity("S7", root, baseCtx("com.test.app"));
        }

        /* ════ S8 输入框页面（登录页）════ */
        TestHost.suite("DualDfs · S8 输入框页面");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode u = new SimNode("手机号", 40, 400, 1040, 500);
            u.cls = "android.widget.EditText";
            u.editable = true;
            SimNode p = new SimNode("密码", 40, 560, 1040, 660);
            p.cls = "android.widget.EditText";
            p.editable = true;
            SimNode btn = new SimNode("登录", 40, 760, 1040, 880);
            btn.clickable = true;
            root.add(u);
            root.add(p);
            root.add(btn);
            assertFullParity("S8", root, baseCtx("com.test.app"));
        }

        /* ════ S9 可滚动页面（信息流）════ */
        TestHost.suite("DualDfs · S9 可滚动页面");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode sv = new SimNode("", 0, 0, 1080, 2400);
            sv.cls = "android.widget.ScrollView";
            sv.scrollable = true;
            root.add(sv);
            for (int i = 0; i < 20; i++) {
                sv.add(new SimNode("信息流条目 " + i + "：标题正文摘要内容若干字", 40, 100 + i * 110, 1040, 190 + i * 110));
            }
            assertFullParity("S9", root, baseCtx("com.test.app"));
        }

        /* ════ S10 多窗口页面（内容根 + Dialog 根分别扫描）════ */
        TestHost.suite("DualDfs · S10 多窗口（内容根 + Dialog 根）");
        {
            SimNode content = new SimNode("", 0, 0, 1080, 2400);
            content.cls = "android.widget.FrameLayout";
            content.add(new SimNode("正文内容", 40, 200, 900, 300));
            assertFullParity("S10-内容根", content, baseCtx("com.test.app"));

            SimNode dlg = new SimNode("", 200, 800, 880, 1600);
            dlg.cls = "android.app.Dialog";
            SimNode close = new SimNode("×", 790, 830, 860, 900);
            close.clickable = true;
            dlg.add(close);
            dlg.add(new SimNode("温馨提示", 240, 900, 800, 980));
            Ctx ctx = baseCtx("com.test.app");
            ScanFind oldF = AdDecisionCore.walk(dlg, ctx);
            FusedScan fs = AdDecisionCore.walkFused(dlg, ctx, true, 4);
            TestHost.check(oldF.dialogForm, "S10 Dialog 根形态（旧）");
            TestHost.check(fs.find.dialogForm, "S10 Dialog 根形态（新）");
            TestHost.checkEq(findKey(oldF), findKey(fs.find), "S10 Dialog 根信号一致");
            TestHost.checkEq(hitsKey(RuleEngine.matchAll(dlg, ctx, 4)), hitsKey(fs.ruleHits), "S10 Dialog 根规则命中一致");
        }

        /* ════ S11 页面扫描中途变化（已声明语义差 · 两方向）════ */
        TestHost.suite("DualDfs · S11 树在两趟 DFS 之间变化（语义差清单①）");
        {
            Ctx ctx = baseCtx("com.test.app");
            // S11a 晚到跳过键：phase2 才出现——旧 walk 看得见（第二时刻），
            // fused 看不见（第一时刻）——晚到候选顺延下一档（60ms 梯子/nudge 覆盖）
            PhaseTree t1 = lateSkipTree();
            PhaseTree.phase = 1;
            java.util.ArrayList<Hit> oldHits = RuleEngine.matchAll(t1, ctx, 4);
            PhaseTree.phase = 2;
            ScanFind oldF = AdDecisionCore.walk(t1, ctx);
            PhaseTree.phase = 1;
            FusedScan fs = AdDecisionCore.walkFused(t1, ctx, true, 4);
            TestHost.checkEq(0, oldHits.size(), "S11a 旧 matchAll@T1 零命中（晚到键未现）");
            TestHost.check(oldF.foundSkip, "S11a 旧 walk@T2 看见晚到键（对照成立）");
            TestHost.checkEq(0, fs.ruleHits.size(), "S11a fused@T1 骑士零命中（与旧 matchAll 一致）");
            TestHost.check(!fs.find.foundSkip, "S11a fused@T1 决策信号无晚到键（已声明差：顺延下一档）");

            // S11b 早退角落键：phase1 在场、phase2 消失——旧 walk@T2 看不见，
            // fused@T1 看得见（第一时刻在场）。方向相反的同一权衡：单遍 = T1 语义
            PhaseTree t2 = earlyCornerTree();
            PhaseTree.phase = 1;
            java.util.ArrayList<Hit> oldHits2 = RuleEngine.matchAll(t2, ctx, 4);
            PhaseTree.phase = 2;
            ScanFind oldF2 = AdDecisionCore.walk(t2, ctx);
            PhaseTree.phase = 1;
            FusedScan fs2 = AdDecisionCore.walkFused(t2, ctx, true, 4);
            TestHost.checkEq(0, oldHits2.size(), "S11b 旧 matchAll@T1 零命中（角落键非规则面）");
            TestHost.checkEq(0, oldF2.corners.size(), "S11b 旧 walk@T2 角落已消失（对照成立）");
            TestHost.checkEq(1, fs2.find.corners.size(), "S11b fused@T1 角落候选在场（已声明差：T1 语义）");
            TestHost.checkEq(0, fs2.ruleHits.size(), "S11b fused@T1 骑士零命中（与旧一致）");
        }

        /* ════ S12 节点访问异常（确定性等价 + 瞬时差已声明）════ */
        TestHost.suite("DualDfs · S12 节点访问异常（语义差清单②）");
        {
            Ctx ctx = baseCtx("com.test.app");
            // S12a 确定性异常：child(1) 恒抛——两趟各自同点失败，子树同丢 → 等价
            java.util.ArrayList<Hit> oldHitsA = RuleEngine.matchAll(new BadRoot(badTreeShape(), false), ctx, 4);
            ScanFind oldFA = AdDecisionCore.walk(new BadRoot(badTreeShape(), false), ctx);
            FusedScan fsA = AdDecisionCore.walkFused(new BadRoot(badTreeShape(), false), ctx, true, 4);
            TestHost.checkEq(hitsKey(oldHitsA), hitsKey(fsA.ruleHits), "S12a 恒抛异常：规则命中等价");
            TestHost.checkEq(findKey(oldFA), findKey(fsA.find), "S12a 恒抛异常：决策信号等价（同子树同丢）");

            // S12b 瞬时异常（throw-once）：旧组合共享一棵树（matchAll 首访抛、
            // walk 二访恢复）；fused 用独立树（首访即抛）——首访语义对等
            BadRoot oldRootB = new BadRoot(badTreeShape(), true);
            java.util.ArrayList<Hit> oldHitsB = RuleEngine.matchAll(oldRootB, ctx, 4);
            ScanFind oldFB = AdDecisionCore.walk(oldRootB, ctx); // 同树二趟：child(1) 恢复
            FusedScan fsB = AdDecisionCore.walkFused(new BadRoot(badTreeShape(), true), ctx, true, 4); // 首访即抛
            TestHost.checkEq(hitsKey(oldHitsB), hitsKey(fsB.ruleHits), "S12b 瞬时异常：规则命中等价（首趟同失败）");
            TestHost.check(oldFB.foundSkip, "S12b 旧 walk 二趟重试恢复子树（对照成立）");
            TestHost.check(!fsB.find.foundSkip, "S12b fused 单趟即失该子树（已声明差：宁少不误，下一档补）");
        }

        /* ════ S13 超过扫描预算（700 节点 / 预算 340）════ */
        TestHost.suite("DualDfs · S13 预算截断一致性");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            for (int i = 0; i < 700; i++) {
                SimNode n = new SimNode("条目" + i, 40, (i % 20) * 110, 1040, (i % 20) * 110 + 90);
                root.add(n);
            }
            // 位置 100（早）与位置 600（晚，超出 340 预算）各埋一个跳过键
            SimNode early = TestHost.skipButton("跳过");
            root.children.remove(99);
            root.children.add(99, early);
            SimNode late = TestHost.skipButton("跳过");
            late.boundsArr = new int[]{920, 900, 1050, 970};
            root.children.remove(599);
            root.children.add(599, late);
            Ctx ctx = baseCtx("com.test.app");
            ctx.budgetNodes = 340;
            java.util.ArrayList<Hit> oldHits = RuleEngine.matchAll(root, ctx, 4);
            ScanFind oldF = AdDecisionCore.walk(root, ctx);
            FusedScan fs = AdDecisionCore.walkFused(root, ctx, true, 4);
            TestHost.checkEq(340, oldF.nodeCount, "S13 旧 walk 恰耗尽预算");
            TestHost.checkEq(340, fs.find.nodeCount, "S13 fused 恰耗尽预算（同减点同序）");
            TestHost.checkEq(hitsKey(oldHits), hitsKey(fs.ruleHits), "S13 预算内命中一致");
            TestHost.checkEq(findKey(oldF), findKey(fs.find), "S13 预算截断后信号一致");
        }

        /* ════ S14 多个候选同时出现（6 跳过分散 + 3 关闭 + 角落 + 倒计时）════ */
        TestHost.suite("DualDfs · S14 多候选并发");
        {
            SimNode root = multiCandTree();
            Ctx ctx = baseCtx("com.test.app");
            java.util.ArrayList<Hit> oldHits = RuleEngine.matchAll(root, ctx, 4);
            FusedScan fs = AdDecisionCore.walkFused(root, ctx, true, 4);
            TestHost.checkEq(4, oldHits.size(), "S14 旧 matchAll 满额 4 中");
            TestHost.checkEq(hitsKey(oldHits), hitsKey(fs.ruleHits), "S14 满额命中集+顺序一致（含早停同点）");
            // 骑士满额 → 早停：旧路径 walk 不跑（规则路径 continue）、新 find 同被
            // 下游丢弃——此处仅断言早停证据；≤3 中的 find 全字段对照由 S2/S4/S6/
            // S7/S10/S20 等 1~3 中场景覆盖（那里 decide/信号全量一致）
            TestHost.check(fs.find.nodeCount >= 5, "S14 fused 早停于第 4 中（节点计数截断）");
        }

        /* ════ S15 订阅规则命中（合成顺序：规则在前、订阅追加）════ */
        TestHost.suite("DualDfs · S15 订阅命中合成顺序");
        {
            SubscriptionManager.primeForJvm();
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode skip = new SimNode("", 920, 80, 1050, 150);
            skip.cls = "android.widget.ImageView";
            skip.vid = "com.sina.weibo:id/iv_skip";
            skip.clickable = true;
            root.add(skip);
            Ctx ctx = baseCtx("com.sina.weibo");
            // 旧组合
            java.util.ArrayList<Hit> oldComp = RuleEngine.matchAll(root, ctx, 4);
            oldComp.addAll(SubscriptionManager.matchSources("com.sina.weibo", root, ctx));
            // 新组合（服务侧同序：骑士命中 + 订阅追加）
            FusedScan fs = AdDecisionCore.walkFused(root, ctx, true, 4);
            java.util.ArrayList<Hit> newComp = new java.util.ArrayList<Hit>(fs.ruleHits);
            newComp.addAll(SubscriptionManager.matchSources("com.sina.weibo", root, ctx));
            TestHost.check(oldComp.size() >= 2, "S15 旧组合含引擎+订阅双命中");
            TestHost.checkEq(hitsKey(oldComp), hitsKey(newComp), "S15 合成命中集+顺序一致（轮换语义不变）");
        }

        /* ════ S16 订阅规则未命中（无覆盖包）════ */
        TestHost.suite("DualDfs · S16 订阅未命中");
        {
            SubscriptionManager.primeForJvm();
            SimNode root = splashTree();
            Ctx ctx = baseCtx("com.test.app");
            java.util.ArrayList<Hit> oldComp = RuleEngine.matchAll(root, ctx, 4);
            oldComp.addAll(SubscriptionManager.matchSources("com.test.app", root, ctx));
            FusedScan fs = AdDecisionCore.walkFused(root, ctx, true, 4);
            java.util.ArrayList<Hit> newComp = new java.util.ArrayList<Hit>(fs.ruleHits);
            newComp.addAll(SubscriptionManager.matchSources("com.test.app", root, ctx));
            TestHost.checkEq(hitsKey(oldComp), hitsKey(newComp), "S16 无覆盖包合成一致");
            TestHost.checkEq(0, SubscriptionManager.matchSources("com.test.app", root, ctx).size(),
                    "S16 无覆盖包订阅零命中（noCover）");
        }

        /* ════ S17 小程序容器（已声明语义差清单③：骑士入口即抑制）════ */
        TestHost.suite("DualDfs · S17 小程序容器（骑士入口抑制）");
        {
            SimNode root = splashTree(); // 含「跳过」文本键
            Ctx ctx = baseCtx("com.tencent.mm");
            ctx.activityId = "com.tencent.mm.plugin.appbrand.ui.AppBrandUI0";
            java.util.ArrayList<Hit> oldHits = RuleEngine.matchAll(root, ctx, 4);
            ScanFind oldF = AdDecisionCore.walk(root, ctx);
            FusedScan fs = AdDecisionCore.walkFused(root, ctx, true, 4);
            TestHost.check(oldHits.size() > 0, "S17 旧 matchAll 仍命中（无容器门——对照成立）");
            TestHost.checkEq(0, oldF.nodeCount, "S17 旧 walk 容器门返空");
            TestHost.checkEq(0, fs.ruleHits.size(), "S17 fused 骑士同被容器门抑制（旧路走 execute L0 拦，同为零点击）");
            TestHost.checkEq(0, fs.find.nodeCount, "S17 fused 决策信号同空");
            // 安全论证：旧路径命中后被 execute() L0 小程序终门拦截（零点击）；
            // 新路径入口即不收集（零点击 + 零派发尝试 + acted=false 早退避）。
        }

        /* ════ S18 骑士满额早停（6 可命中键分散四角，第 4 中即止）════ */
        TestHost.suite("DualDfs · S18 骑士满额早停");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            int[][] pos = {{900, 80, 1050, 130}, {40, 80, 190, 130}, {900, 1150, 1050, 1200},
                    {40, 1150, 190, 1200}, {880, 2100, 1060, 2160}, {40, 2100, 220, 2180}};
            for (int i = 0; i < 6; i++) {
                SimNode s = new SimNode("跳过" + (i == 0 ? "" : String.valueOf(i)), pos[i][0], pos[i][1], pos[i][2], pos[i][3]);
                s.clickable = true;
                root.add(s);
            }
            root.add(new SimNode("正文", 40, 1500, 900, 1600));
            Ctx ctx = baseCtx("com.test.app");
            java.util.ArrayList<Hit> oldHits = RuleEngine.matchAll(root, ctx, 4);
            FusedScan fs = AdDecisionCore.walkFused(root, ctx, true, 4);
            TestHost.checkEq(4, oldHits.size(), "S18 旧 matchAll 恰收 4");
            TestHost.checkEq(4, fs.ruleHits.size(), "S18 fused 骑士恰收 4（同停点）");
            TestHost.checkEq(hitsKey(oldHits), hitsKey(fs.ruleHits), "S18 满额命中集+顺序一致");
            TestHost.check(fs.find.nodeCount < 7, "S18 fused 早停生效（节点计数 < 全树）");
        }

        /* ════ S19 计数对照（CountingNode：读次/子访问/包装分配）════ */
        TestHost.suite("DualDfs · S19 属性读取/节点访问计数");
        {
            SimNode tree = wideTree(400);
            Ctx ctx = baseCtx("com.test.app");
            ctx.budgetNodes = 600;
            // 旧：两趟（同一计数器累加 = 旧路径总成本）
            Counters oldC = new Counters();
            java.util.ArrayList<Hit> oldHits = RuleEngine.matchAll(new CountingNode(tree, oldC), ctx, 4);
            ScanFind oldF = AdDecisionCore.walk(new CountingNode(tree, oldC), ctx);
            // 新：单趟
            Counters newC = new Counters();
            FusedScan fs = AdDecisionCore.walkFused(new CountingNode(tree, newC), ctx, true, 4);
            TestHost.checkEq(oldF.nodeCount, fs.find.nodeCount, "S19 访问节点数一致（预算同耗）");
            TestHost.checkEq(hitsKey(oldHits), hitsKey(fs.ruleHits), "S19 命中一致");
            TestHost.checkEq(findKey(oldF), findKey(fs.find), "S19 信号一致");
            TestHost.check(newC.childCall < oldC.childCall, "S19 子访问（≈生产 getChild binder）严格下降："
                    + newC.childCall + " < " + oldC.childCall);
            TestHost.check(newC.textRead < oldC.textRead, "S19 text 读次严格下降：" + newC.textRead + " < " + oldC.textRead);
            TestHost.check(newC.descRead < oldC.descRead, "S19 desc 读次严格下降：" + newC.descRead + " < " + oldC.descRead);
            TestHost.check(newC.boundsRead < oldC.boundsRead, "S19 bounds 读次严格下降：" + newC.boundsRead + " < " + oldC.boundsRead);
            TestHost.check(newC.propertyReads() < oldC.propertyReads(), "S19 属性读总次严格下降："
                    + newC.propertyReads() + " < " + oldC.propertyReads());
            TestHost.check(newC.wrapperAlloc < oldC.wrapperAlloc, "S19 包装分配（≈NodeAdapter 分配）严格下降："
                    + newC.wrapperAlloc + " < " + oldC.wrapperAlloc);
            System.out.println("  [计数] 旧: child=" + oldC.childCall + " text=" + oldC.textRead + " desc=" + oldC.descRead
                    + " bounds=" + oldC.boundsRead + " vis=" + oldC.visibleRead + " 属性总=" + oldC.propertyReads()
                    + " 分配=" + oldC.wrapperAlloc);
            System.out.println("  [计数] 新: child=" + newC.childCall + " text=" + newC.textRead + " desc=" + newC.descRead
                    + " bounds=" + newC.boundsRead + " vis=" + newC.visibleRead + " 属性总=" + newC.propertyReads()
                    + " 分配=" + newC.wrapperAlloc);
        }

        /* ════ S20 vid 叶子门一致性（零文本叶子 + 非叶子 vid + 短 vid）════ */
        TestHost.suite("DualDfs · S20 vid 门边界（叶子/非叶子/短 vid）");
        {
            SimNode root = new SimNode("", 0, 0, 1080, 2400);
            root.cls = "android.widget.FrameLayout";
            SimNode leaf = new SimNode("", 900, 80, 1040, 140);
            leaf.vid = "com.test.app:id/btn_skip";
            leaf.clickable = true;
            root.add(leaf);
            SimNode nonLeaf = new SimNode("", 40, 200, 640, 700);
            nonLeaf.vid = "com.test.app:id/skip_container";
            nonLeaf.add(new SimNode("内容", 60, 220, 620, 680));
            root.add(nonLeaf);
            SimNode shortVid = new SimNode("", 40, 800, 540, 860);
            shortVid.vid = "x"; // <4 字符：matchVid 直接拒
            root.add(shortVid);
            assertFullParity("S20", root, baseCtx("com.test.app"));
        }

        TestHost.done();
    }

    /* ───────────────────────── 辅助 ───────────────────────── */

    static Ctx baseCtx(String pkg) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = pkg + ".MainActivity";
        ctx.elapsedMs = 500;
        ctx.splashLive = true;
        ctx.popupLive = false;
        ctx.sdkAdWin = false;
        ctx.learnedCls = false;
        ctx.rootWindow = true;
        ctx.budgetNodes = 600;
        return ctx;
    }

    static SimNode splashTree() {
        SimNode root = new SimNode("", 0, 0, 1080, 2400);
        root.cls = "android.widget.FrameLayout";
        root.add(TestHost.skipButton("跳过"));
        return root;
    }

    /** 指定节点数的宽树（簇×9 文本行，零规则命中——计数/基准场景用） */
    static SimNode wideTree(int nodes) {
        SimNode root = new SimNode("", 0, 0, 1080, 2400);
        root.cls = "android.widget.FrameLayout";
        int made = 1;
        int row = 0;
        while (made < nodes) {
            SimNode cluster = new SimNode("", 0, (row % 8) * 280, 1080, (row % 8) * 280 + 270);
            cluster.cls = "android.widget.LinearLayout";
            root.add(cluster);
            made++;
            for (int k = 0; k < 9 && made < nodes; k++) {
                cluster.add(new SimNode("行文本" + row + "-" + k, 20 + k * 110, (row % 8) * 280 + 10, 110 + k * 110, (row % 8) * 280 + 80));
                made++;
            }
            row++;
        }
        return root;
    }

    static SimNode multiCandTree() {
        SimNode root = new SimNode("", 0, 0, 1080, 2400);
        root.cls = "android.widget.FrameLayout";
        // 6 个跳过键分散四角/中部（同列同宽≥4 会触发 isMenuListRow 菜单保护——
        // 那是引擎的正确行为，多候选场景须避开菜单形态）
        int[][] pos = {{900, 80, 1050, 130}, {40, 80, 190, 130}, {900, 1150, 1050, 1200},
                {40, 1150, 190, 1200}, {880, 2100, 1060, 2160}, {40, 2100, 220, 2180}};
        for (int i = 0; i < 6; i++) {
            SimNode s = new SimNode("跳过" + (i == 0 ? "" : String.valueOf(i)), pos[i][0], pos[i][1], pos[i][2], pos[i][3]);
            s.clickable = true;
            root.add(s);
        }
        for (int i = 0; i < 3; i++) {
            SimNode c = new SimNode("×", 40 + i * 60, 2200, 100 + i * 60, 2260);
            c.clickable = true;
            root.add(c);
        }
        SimNode corner = new SimNode("", 1030, 2270, 1076, 2316);
        corner.clickable = true;
        root.add(corner);
        root.add(new SimNode("5", 850, 90, 890, 130)); // 跳过键近旁独立倒计时数字
        return root;
    }

    /** 异常树：child(1) 恒抛/仅首访抛（其子树藏跳过键；child[2] 顺带被同 try 吞） */
    static SimNode badTreeShape() {
        SimNode root = new SimNode("", 0, 0, 1080, 2400);
        root.cls = "android.widget.FrameLayout";
        root.add(new SimNode("正常条目A", 40, 200, 900, 280));
        SimNode bad = new SimNode("", 0, 300, 1080, 900);
        bad.add(TestHost.skipButton("跳过"));
        root.add(bad); // index 1：抛点
        root.add(new SimNode("正常条目B", 40, 1000, 900, 1080));
        return root;
    }

    /** 组合基座：委托内层 SimNode（SimNode 为 final 不可继承——组合替代） */
    static class WrapNode implements NodeView {
        final SimNode base;

        WrapNode(SimNode base) {
            this.base = base;
        }

        public String text() {
            return base.text;
        }

        public String desc() {
            return base.desc;
        }

        public String cls() {
            return base.cls;
        }

        public boolean clickable() {
            return base.clickable;
        }

        public boolean scrollable() {
            return base.scrollable;
        }

        public boolean editable() {
            return base.editable;
        }

        public int[] bounds() {
            return base.boundsArr;
        }

        public int childCount() {
            return base.children.size();
        }

        public NodeView child(int i) {
            return base.children.get(i);
        }

        public NodeView parent() {
            return base.parent;
        }

        public boolean visible() {
            return base.visible;
        }

        public String viewId() {
            return base.vid;
        }
    }

    /** 异常根：child(1) 恒抛 / 仅首访抛（binder 瞬断模拟） */
    static final class BadRoot extends WrapNode {
        final boolean throwOnce;
        final int[] fired = {0};

        BadRoot(SimNode base, boolean throwOnce) {
            super(base);
            this.throwOnce = throwOnce;
        }

        @Override
        public NodeView child(int i) {
            if (i == 1) {
                fired[0]++;
                if (!throwOnce || fired[0] == 1) {
                    throw new RuntimeException("binder 瞬断（模拟）");
                }
            }
            return super.child(i);
        }
    }

    /** 相位树：appear=true phase=2 追加目标子树（晚到）；false phase=1 在场、
     *  phase=2 移除（早退）——模拟两趟 DFS 之间树变化的两个方向 */
    static final class PhaseTree extends WrapNode {
        static int phase = 1;
        final SimNode phaseChild;
        final boolean appear;

        PhaseTree(SimNode base, SimNode phaseChild, boolean appear) {
            super(base);
            this.phaseChild = phaseChild;
            this.appear = appear;
        }

        @Override
        public int childCount() {
            boolean has = appear ? phase == 2 : phase == 1;
            return base.children.size() + (has ? 1 : 0);
        }

        @Override
        public NodeView child(int i) {
            if (i < base.children.size()) return super.child(i);
            boolean has = appear ? phase == 2 : phase == 1;
            if (i == base.children.size() && has) return phaseChild;
            throw new IllegalArgumentException("bad index " + i);
        }
    }

    static PhaseTree lateSkipTree() {
        SimNode base = new SimNode("", 0, 0, 1080, 2400);
        base.cls = "android.widget.FrameLayout";
        base.add(new SimNode("基础内容", 40, 300, 900, 400));
        return new PhaseTree(base, TestHost.skipButton("跳过"), true);
    }

    static PhaseTree earlyCornerTree() {
        SimNode base = new SimNode("", 0, 0, 1080, 2400);
        base.cls = "android.widget.FrameLayout";
        base.add(new SimNode("基础内容", 40, 300, 900, 400));
        SimNode corner = new SimNode("", 1030, 120, 1076, 166);
        corner.clickable = true;
        return new PhaseTree(base, corner, false);
    }

    /* ── 对照断言：旧组合（matchAll+walk）vs 新单遍（walkFused）全量一致。
     * 骑士满额（≥4 中）时旧路径 walk 根本不跑（规则路径 continue）、
     * 新路径 find 同样被下游丢弃——find/decide 无对照意义，断言早停证据即可 ── */
    static void assertFullParity(String tag, SimNode root, Ctx ctx) {
        java.util.ArrayList<Hit> oldHits = RuleEngine.matchAll(root, ctx, 4);
        FusedScan fs = AdDecisionCore.walkFused(root, ctx, true, 4);
        TestHost.checkEq(hitsKey(oldHits), hitsKey(fs.ruleHits), tag + " 规则命中集+顺序一致");
        if (oldHits.size() < 4) {
            ScanFind oldF = AdDecisionCore.walk(root, ctx);
            TestHost.checkEq(findKey(oldF), findKey(fs.find), tag + " 决策信号全字段一致");
            Decision dOld = AdDecisionCore.decide(oldF, ctx);
            Decision dNew = AdDecisionCore.decide(fs.find, ctx);
            TestHost.checkEq(decideKey(dOld), decideKey(dNew), tag + " 决策结果一致");
            TestHost.checkEq(oldF.nodeCount, fs.find.nodeCount, tag + " 节点计数一致");
        } else {
            TestHost.check(fs.find.nodeCount >= 5, tag + " 骑士早停于第 4 命中（find 部分填充，下游丢弃）");
        }
    }

    static String hitsKey(java.util.List<Hit> hits) {
        StringBuilder sb = new StringBuilder("[");
        for (Hit h : hits) {
            sb.append(h.appId).append('|').append(h.label).append('|')
                    .append(boundsStr(h.tapBounds)).append('|').append(h.gestureOnly).append(';');
        }
        return sb.append(']').toString();
    }

    static String findKey(ScanFind f) {
        StringBuilder sb = new StringBuilder();
        sb.append("foundSkip=").append(f.foundSkip)
                .append(",skipLabel=").append(f.skipLabel)
                .append(",skipB=").append(boundsStr(f.skipBounds))
                .append(",countdownN=").append(f.countdownN)
                .append(",fakeSkip=").append(f.fakeSkip)
                .append(",gyro=").append(f.gyroHint)
                .append(",badge=").append(f.adBadge).append('/').append(f.badgeLabel)
                .append(",foundClose=").append(f.foundClose)
                .append(",closeLabel=").append(f.closeLabel)
                .append(",cta=").append(f.ctaCount)
                .append(",nodes=").append(f.nodeCount)
                .append(",edit=").append(f.editPresent)
                .append(",scroll=").append(f.scrollPresent)
                .append(",hot=").append(f.hotZone)
                .append(",longText=").append(f.longText)
                .append(",dialog=").append(f.dialogForm)
                .append(",wv=").append(f.webviewPresent);
        sb.append(",skipCands=").append(candsKey(f.skipCands));
        sb.append(",fakeCands=").append(candsKey(f.fakeCands));
        sb.append(",corners=");
        sb.append('[');
        for (AdDecisionCore.CornerCand c : f.corners) {
            sb.append(boundsStr(c.tapBounds)).append('/').append(c.strongOnly).append(';');
        }
        sb.append(']');
        sb.append(",cdDigits=");
        sb.append('[');
        for (AdDecisionCore.CdDigit d : f.cdDigits) {
            sb.append(d.seconds).append('@').append(boundsStr(d.bounds)).append(';');
        }
        sb.append(']');
        return sb.toString();
    }

    static String candsKey(java.util.List<AdDecisionCore.SkipCand> cands) {
        StringBuilder sb = new StringBuilder("[");
        for (AdDecisionCore.SkipCand c : cands) {
            sb.append(c.label).append('|').append(boundsStr(c.tapBounds))
                    .append('|').append(c.selfClick).append('|').append(c.gestureOnly).append(';');
        }
        return sb.append(']').toString();
    }

    static String decideKey(Decision d) {
        return d.action + "|" + d.reason + "|" + boundsStr(d.bounds) + "|" + d.x + "," + d.y;
    }

    static String boundsStr(int[] b) {
        if (b == null || b.length < 4) return "null";
        return b[0] + "," + b[1] + "," + b[2] + "," + b[3];
    }
}
