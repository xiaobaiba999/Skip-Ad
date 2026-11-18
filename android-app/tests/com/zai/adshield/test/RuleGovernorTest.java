package com.zai.adshield.test;

import com.zai.adshield.core.RuleMerge;
import com.zai.adshield.core.LearnGovernor;
import com.zai.adshield.core.SmartScanPlanner;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.test.TestHost.SimNode;

import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════
 *  v5.5.1 规则治理测试（RuleGovernorTest）
 * ═══════════════════════════════════════════════════════════════
 *  ⚠ 本套件在 v5.5.0 的 run-tests.sh 已注册但源码从未提交（发版声明
 *  「599 项断言全绿」实际缺失 124 项）——v5.5.1 补齐并扩展。
 *
 *  覆盖 v5.5.0 模块二全部四个治理器：
 *   · RuleMerge 三源治理：优先级权重 / 等价合并 / 动作冲突裁决 /
 *     学习影子 / 选择器规范化 / 热规则前置排序 / 禁用过滤；
 *   · LearnGovernor 学习规则生命周期：质量评分三构成 / 新生→活跃→
 *     衰退→退休状态机 / 准入门 / SDK 特征泛化；
 *   · SmartScanPlanner 分级扫描：L1/L2/L3 预算 / 深度门 / 指数退避；
 *   · RuleEngine 第5层场景（更新弹窗/视频前贴/悬浮球）+ 第0~3层回归
 *    （含 issue#64 微博「跳过广告」分应用命中锚点——gestureOnly 误杀
 *     修复后该命中必须能通过边界自检语义层）。
 */
public final class RuleGovernorTest {

    private static final long NOW = System.currentTimeMillis();

    public static void main(String[] args) {
        TestHost.suite("① RuleMerge 三源权重与规范化");
        testWeightsAndNormalize();

        TestHost.suite("② RuleMerge 等价合并与冲突裁决");
        testMergeSemantics();

        TestHost.suite("③ RuleMerge 输出排序与过滤");
        testMergeOrdering();

        TestHost.suite("④ LearnGovernor 质量评分");
        testScore();

        TestHost.suite("⑤ LearnGovernor 生命周期状态机");
        testLifecycle();

        TestHost.suite("⑥ LearnGovernor 准入门与 SDK 泛化");
        testAdmitAndGeneralize();

        TestHost.suite("⑦ SmartScanPlanner 分级预算与深度门");
        testScanBudget();

        TestHost.suite("⑧ SmartScanPlanner 指数退避");
        testBackoff();

        TestHost.suite("⑨ RuleEngine 第5层场景（更新/视频前贴/悬浮球）");
        testSceneLayer();

        TestHost.suite("⑩ RuleEngine 第0~3层回归（issue#64 微博锚点）");
        testTextLayers();

        TestHost.done();
    }

    /* ───────── ① 权重与规范化 ───────── */

    private static void testWeightsAndNormalize() {
        TestHost.checkEq(RuleMerge.W_OFFICIAL, RuleMerge.weightOf("official"), "official 权重 3");
        TestHost.checkEq(RuleMerge.W_GKD, RuleMerge.weightOf("gkd"), "gkd 权重 2");
        TestHost.checkEq(RuleMerge.W_LEARNED, RuleMerge.weightOf("learned"), "learned 权重 1");
        TestHost.checkEq(RuleMerge.W_LEARNED, RuleMerge.weightOf("unknown_src"), "未知源 → 最低权重（保守）");

        // 规范化：空白折叠 + 小写 + 尾部游标号剥离（GKD 布局重排 id 差异）
        TestHost.checkEq("@textview", RuleMerge.normalize("@TextView12"), "尾号剥离：@TextView12 → @textview");
        TestHost.checkEq("@close_btn", RuleMerge.normalize("  @close_btn3 "), "去空格+尾号：@close_btn3 → @close_btn");
        TestHost.checkEq("btn skip", RuleMerge.normalize("Btn\n  Skip"), "空白折叠+小写：Btn/Skip → btn skip");
        TestHost.checkEq("", RuleMerge.normalize(null), "null → 空串");
        TestHost.checkEq("tt_splash", RuleMerge.normalize("TT_SPLASH"), "纯小写化：TT_SPLASH → tt_splash");
        // 无尾号的游标不动（≥2 位规则只匹配纯数字结尾——v2 下标属游标形态，
        // 去尾号后与 v3 归一为同一目标，与 GKD 布局重排 id 差异同源语义）
        TestHost.checkEq("@btn_v", RuleMerge.normalize("@btn_v2"), "游标尾号归一：@btn_v2 → @btn_v");
    }

    /* ───────── ② 合并与冲突裁决 ───────── */

    private static void testMergeSemantics() {
        /* 等价规则（同 pkg+selector+action 不同源）→ 合并计数、来源取高 */
        List<RuleMerge.Entry> equiv = new ArrayList<RuleMerge.Entry>();
        equiv.add(new RuleMerge.Entry(RuleMerge.SRC_GKD, "com.sina.weibo", "@textview1", RuleMerge.ACT_CLICK_TARGET, 2));
        equiv.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "com.sina.weibo", "@TextView1", RuleMerge.ACT_CLICK_TARGET, 5));
        RuleMerge.Result r1 = RuleMerge.merge(equiv);
        TestHost.checkEq(1, r1.kept.size(), "等价两源 → 合一");
        TestHost.checkEq(7, r1.kept.get(0).hitCount, "hitCount 累计（5+2）");
        TestHost.checkEq(RuleMerge.SRC_OFFICIAL, r1.kept.get(0).source, "合并后来源取高权重（official）");
        TestHost.checkEq(1, r1.mergedCount, "mergedCount=1");
        TestHost.checkEq("MERGED_SAME", r1.conflicts.get(0).kind, "等价合并记 MERGED_SAME");

        /* 学习影子：learned 与 official 等价 → MERGED_SHADOW（统计不重复计） */
        List<RuleMerge.Entry> shadow = new ArrayList<RuleMerge.Entry>();
        shadow.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "pkg", "sel", RuleMerge.ACT_CLICK_CENTER, 4));
        shadow.add(new RuleMerge.Entry(RuleMerge.SRC_LEARNED, "pkg", "sel", RuleMerge.ACT_CLICK_CENTER, 1));
        RuleMerge.Result r2 = RuleMerge.merge(shadow);
        TestHost.checkEq(1, r2.kept.size(), "学习影子 → 保留高源一条");
        TestHost.checkEq("MERGED_SHADOW", r2.conflicts.get(0).kind, "learned 等价 → MERGED_SHADOW");
        TestHost.checkEq(RuleMerge.SRC_OFFICIAL, r2.kept.get(0).source, "影子合并来源=official");

        /* 动作冲突：高权重胜出，低源被移除并记 CONFLICT_ACTION */
        List<RuleMerge.Entry> conflict = new ArrayList<RuleMerge.Entry>();
        conflict.add(new RuleMerge.Entry(RuleMerge.SRC_LEARNED, "pkg", "sel", RuleMerge.ACT_BACK, 3));
        conflict.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "pkg", "sel", RuleMerge.ACT_CLICK_TARGET, 1));
        RuleMerge.Result r3 = RuleMerge.merge(conflict);
        TestHost.checkEq(1, r3.kept.size(), "冲突组只留胜者");
        TestHost.checkEq(RuleMerge.ACT_CLICK_TARGET, r3.kept.get(0).action, "official 动作胜出（back 弃）");
        TestHost.checkEq("CONFLICT_ACTION", r3.conflicts.get(0).kind, "动作冲突记 CONFLICT_ACTION");
        TestHost.checkEq(1, r3.droppedCount, "droppedCount=1");
        TestHost.checkEq(1, r3.kept.get(0).hitCount, "冲突不吞并 hitCount（败者计数不进胜者）");

        /* 同权重冲突：hitCount 高者胜（组内排序） */
        List<RuleMerge.Entry> tie = new ArrayList<RuleMerge.Entry>();
        tie.add(new RuleMerge.Entry(RuleMerge.SRC_GKD, "pkg", "sel", RuleMerge.ACT_CLICK_CENTER, 2));
        tie.add(new RuleMerge.Entry(RuleMerge.SRC_GKD, "pkg", "sel", RuleMerge.ACT_CLICK_TARGET, 9));
        RuleMerge.Result r4 = RuleMerge.merge(tie);
        TestHost.checkEq(RuleMerge.ACT_CLICK_TARGET, r4.kept.get(0).action, "同权重 → hitCount 高者胜");

        /* 不同 pkg / 不同 selector → 互不干涉 */
        List<RuleMerge.Entry> distinct = new ArrayList<RuleMerge.Entry>();
        distinct.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "pkgA", "selA", RuleMerge.ACT_CLICK_TARGET, 1));
        distinct.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "pkgB", "selA", RuleMerge.ACT_CLICK_TARGET, 1));
        RuleMerge.Result r5 = RuleMerge.merge(distinct);
        TestHost.checkEq(2, r5.kept.size(), "不同目标 → 各自保留（互不合并）");

        /* null / 空选择器 / 禁用条目过滤 */
        List<RuleMerge.Entry> junk = new ArrayList<RuleMerge.Entry>();
        RuleMerge.Entry disabled = new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "pkg", "sel", RuleMerge.ACT_CLICK_TARGET, 1);
        disabled.enabled = false;
        junk.add(disabled);
        junk.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "pkg", null, RuleMerge.ACT_CLICK_TARGET, 1));
        junk.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "pkg", " ", RuleMerge.ACT_CLICK_TARGET, 1));
        junk.add(null);
        RuleMerge.Result r6 = RuleMerge.merge(junk);
        TestHost.checkEq(0, r6.kept.size(), "禁用/空选择器/null 全过滤");
        RuleMerge.Result r7 = RuleMerge.merge(null);
        TestHost.checkEq(0, r7.kept.size(), "null 输入 → 空结果");

        /* sameTarget 判定 */
        RuleMerge.Entry a = new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "pkg", "sel", 0, 1);
        RuleMerge.Entry b = new RuleMerge.Entry(RuleMerge.SRC_GKD, "pkg", "sel", 0, 1);
        RuleMerge.Entry c = new RuleMerge.Entry(RuleMerge.SRC_GKD, "pkg2", "sel", 0, 1);
        TestHost.check(RuleMerge.sameTarget(a, b), "同 pkg+selector → sameTarget");
        TestHost.check(!RuleMerge.sameTarget(a, c), "异 pkg → 非 sameTarget");
    }

    /* ───────── ③ 输出排序 ───────── */

    private static void testMergeOrdering() {
        List<RuleMerge.Entry> all = new ArrayList<RuleMerge.Entry>();
        all.add(new RuleMerge.Entry(RuleMerge.SRC_LEARNED, "p1", "s1", RuleMerge.ACT_CLICK_TARGET, 50));
        all.add(new RuleMerge.Entry(RuleMerge.SRC_GKD, "p2", "s2", RuleMerge.ACT_CLICK_TARGET, 10));
        all.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "p3", "s3", RuleMerge.ACT_CLICK_TARGET, 1));
        all.add(new RuleMerge.Entry(RuleMerge.SRC_OFFICIAL, "p4", "s4", RuleMerge.ACT_CLICK_TARGET, 8));
        RuleMerge.Result r = RuleMerge.merge(all);
        TestHost.checkEq(4, r.kept.size(), "四条互不等价全保留");
        TestHost.checkEq("p4", r.kept.get(0).pkg, "排序：official 高 hit 先（热规则前置）");
        TestHost.checkEq("p3", r.kept.get(1).pkg, "排序：official 低 hit 次之");
        TestHost.checkEq("p2", r.kept.get(2).pkg, "排序：gkd 第三");
        TestHost.checkEq("p1", r.kept.get(3).pkg, "排序：learned 殿后（优先级链）");

        String s = RuleMerge.merge(all).summary();
        TestHost.check(s.contains("保留 4") && s.contains("official 2") && s.contains("gkd 1") && s.contains("learned 1"),
                "summary 三源计数正确");
        String ts = RuleMerge.threeSourceSummary(106, 118, 14);
        TestHost.check(ts.contains("official") && ts.contains("gkd") && ts.contains("learned")
                        && ts.contains("106") && ts.contains("118") && ts.contains("14"),
                "threeSourceSummary 含三源规模");
    }

    /* ───────── ④ 质量评分 ───────── */

    private static void testScore() {
        // 新鲜满分单命中：0.1（命中）+ 0.3（成功）+ 0.2（新鲜）= 0.6
        LearnGovernor.Record fresh = new LearnGovernor.Record("pkg", "cls", 1, 0, NOW, NOW);
        TestHost.check(Math.abs(LearnGovernor.score(fresh, NOW) - 0.6) < 1e-9,
                "新鲜单命中评分 = 0.6（0.1+0.3+0.2）");
        // 5 次命中满分：0.5 + 0.3 + 0.2 = 1.0
        LearnGovernor.Record hot = new LearnGovernor.Record("pkg", "cls", 5, 0, NOW, NOW);
        TestHost.check(Math.abs(LearnGovernor.score(hot, NOW) - 1.0) < 1e-9, "5 命中零失败 → 满分 1.0");
        // 成功率权重：5 命中 1 失败 → 0.5 + 4/5*0.3 + 0.2 = 0.94
        LearnGovernor.Record flaky = new LearnGovernor.Record("pkg", "cls", 5, 1, NOW, NOW);
        TestHost.check(Math.abs(LearnGovernor.score(flaky, NOW) - 0.94) < 1e-9, "5 命中 1 失败 → 0.94");
        // 新鲜度半衰：14 天前命中 → 新鲜项减半
        long d14 = 14L * 24 * 3600 * 1000;
        LearnGovernor.Record stale = new LearnGovernor.Record("pkg", "cls", 1, 0, NOW - d14, NOW - d14);
        TestHost.check(Math.abs(LearnGovernor.score(stale, NOW) - (0.1 + 0.3 + 0.1)) < 1e-9,
                "14 天半衰 → 新鲜项 0.2→0.1（总 0.5）");
        // 零样本（全失败）成功率记 0
        LearnGovernor.Record allFail = new LearnGovernor.Record("pkg", "cls", 2, 2, NOW, NOW);
        TestHost.check(Math.abs(LearnGovernor.score(allFail, NOW) - (0.2 + 0.2)) < 1e-9,
                "全失败样本 → 成功率 0（总 0.4）");
        // null 防御与钳制（评分函数内部钳制——score 输出永在 [0,1]）
        TestHost.checkEq(0.0, LearnGovernor.score(null, NOW), "null 记录 → 0 分");
        LearnGovernor.Record huge = new LearnGovernor.Record("pkg", "cls", 999, -5, NOW, NOW);
        TestHost.check(LearnGovernor.score(huge, NOW) <= 1.0, "超界输入 → 钳制 ≤1.0");
        // 权重和为 1（设计红线）
        TestHost.check(Math.abs(LearnGovernor.W_HIT + LearnGovernor.W_SUCCESS + LearnGovernor.W_FRESH - 1.0) < 1e-9,
                "三权重和 = 1.0");
    }

    /* ───────── ⑤ 生命周期状态机 ───────── */

    private static void testLifecycle() {
        long h72 = LearnGovernor.PROBATION_MS;
        long d30 = LearnGovernor.STALE_MS;
        // 新生期：72h 内单命中 → PROBATION（观察不预拦截）
        LearnGovernor.Record fresh = new LearnGovernor.Record("pkg", "cls", 1, 0, NOW, NOW);
        TestHost.checkEq(LearnGovernor.STAGE_PROBATION, LearnGovernor.stage(fresh, NOW), "72h 单命中 → 新生观察");
        // 新生 + 泛化豁免 → 不进 PROBATION（SDK 骨架强信号）
        LearnGovernor.Record gen = new LearnGovernor.Record("pkg", "cls", 1, 0, NOW, NOW);
        gen.generalized = true;
        TestHost.check(LearnGovernor.stage(gen, NOW) != LearnGovernor.STAGE_PROBATION,
                "新生泛化 → 跳过观察期（SDK 豁免）");
        // 二次命中 → 不再是 PROBATION
        LearnGovernor.Record twice = new LearnGovernor.Record("pkg", "cls", 2, 0, NOW, NOW);
        TestHost.check(LearnGovernor.stage(twice, NOW) == LearnGovernor.STAGE_ACTIVE, "2 命中 → 活跃");
        // 新生期满未复现 → RETIRED（噪声主通道）
        LearnGovernor.Record lonely = new LearnGovernor.Record("pkg", "cls", 1, 0, NOW - h72 - 1000, NOW - h72 - 1000);
        TestHost.checkEq(LearnGovernor.STAGE_RETIRED, LearnGovernor.stage(lonely, NOW), "72h 无复现 → 退休");
        // 30 天未命中但历史 ≥3 → DECAYING（留观察）
        LearnGovernor.Record decay = new LearnGovernor.Record("pkg", "cls", 4, 0, NOW - d30 * 2, NOW - d30 * 2);
        TestHost.checkEq(LearnGovernor.STAGE_DECAYING, LearnGovernor.stage(decay, NOW), "30 天未命中 ≥3 命中 → 衰退观察");
        // 30 天未命中且历史 <3 → RETIRED
        LearnGovernor.Record staleWeak = new LearnGovernor.Record("pkg", "cls", 2, 0, NOW - d30 * 2, NOW - d30 * 2);
        TestHost.checkEq(LearnGovernor.STAGE_RETIRED, LearnGovernor.stage(staleWeak, NOW), "30 天未命中 <3 命中 → 退休");
        // 低分退休：全失败样本
        LearnGovernor.Record bad = new LearnGovernor.Record("pkg", "cls", 2, 2, NOW - d30 * 3, NOW - d30 * 3);
        TestHost.checkEq(LearnGovernor.STAGE_RETIRED, LearnGovernor.stage(bad, NOW), "低分（<0.35）→ 退休");
        // null → RETIRED
        TestHost.checkEq(LearnGovernor.STAGE_RETIRED, LearnGovernor.stage(null, NOW), "null → 退休");
        // 阶段文案
        TestHost.check(LearnGovernor.stageName(LearnGovernor.STAGE_PROBATION).contains("新生"), "PROBATION 文案");
        TestHost.check(LearnGovernor.stageName(LearnGovernor.STAGE_ACTIVE).contains("活跃"), "ACTIVE 文案");
        TestHost.check(LearnGovernor.stageName(LearnGovernor.STAGE_DECAYING).contains("衰退"), "DECAYING 文案");
        TestHost.check(LearnGovernor.stageName(LearnGovernor.STAGE_RETIRED).contains("退休"), "RETIRED 文案");
    }

    /* ───────── ⑥ 准入门与 SDK 泛化 ───────── */

    private static void testAdmitAndGeneralize() {
        // ACTIVE：全量（预拦截+候选层都放行）
        LearnGovernor.Record active = new LearnGovernor.Record("pkg", "cls", 2, 0, NOW, NOW);
        TestHost.check(LearnGovernor.admit(active, NOW, true), "活跃 → 允许预拦截");
        TestHost.check(LearnGovernor.admit(active, NOW, false), "活跃 → 允许候选层");
        // DECAYING：不预拦截，候选层高门槛放行
        long d30 = LearnGovernor.STALE_MS;
        LearnGovernor.Record decaying = new LearnGovernor.Record("pkg", "cls", 4, 0, NOW - d30 * 2, NOW - d30 * 2);
        TestHost.check(!LearnGovernor.admit(decaying, NOW, true), "衰退 → 拒绝预拦截");
        TestHost.check(LearnGovernor.admit(decaying, NOW, false), "衰退 → 候选层兜底放行");
        // PROBATION：仅 SDK 泛化规则放行
        LearnGovernor.Record prob = new LearnGovernor.Record("pkg", "cls", 1, 0, NOW, NOW);
        TestHost.check(!LearnGovernor.admit(prob, NOW, true), "新生非泛化 → 拒绝预拦截");
        TestHost.check(!LearnGovernor.admit(prob, NOW, false), "新生非泛化 → 拒绝候选层");
        LearnGovernor.Record probGen = new LearnGovernor.Record("pkg", "cls", 1, 0, NOW, NOW);
        probGen.generalized = true;
        TestHost.check(LearnGovernor.admit(probGen, NOW, true), "新生泛化 → 预拦截放行（SDK 豁免）");
        TestHost.check(LearnGovernor.admit(probGen, NOW, false), "新生泛化 → 候选层放行");
        // RETIRED：一律不放行
        LearnGovernor.Record retired = new LearnGovernor.Record("pkg", "cls", 1, 0, NOW - d30 * 4, NOW - d30 * 4);
        TestHost.check(!LearnGovernor.admit(retired, NOW, true) && !LearnGovernor.admit(retired, NOW, false),
                "退休 → 一律不放行");
        TestHost.check(!LearnGovernor.admit(null, NOW, false), "null → 不放行");

        // SDK 泛化清单（与官方 SDK 指纹表同源 + v5.5.1 家族段对齐）
        TestHost.check(LearnGovernor.sdkGeneralize("com.qq.e.ads.LandingPageActivity"), "gdt 落地页（qq.e.ads 家族段）→ 泛化");
        TestHost.check(LearnGovernor.sdkGeneralize("com.bytedance.sdk.openadsdk.activity.TtSplashActivity"), "穿山甲开屏 → 泛化");
        TestHost.check(LearnGovernor.sdkGeneralize("com.kwad.sdk.full.KsRewardActivity"), "快手激励（kwad 家族段）→ 泛化");
        TestHost.check(LearnGovernor.sdkGeneralize("com.google.gms.ads.AdMobActivity"), "AdMob（gms.ads 家族段）→ 泛化");
        TestHost.check(LearnGovernor.sdkGeneralize("com.ss.android.adbase SplashAdActivity".replace(" ", ".")), "通用开屏广告窗 → 泛化");
        TestHost.check(LearnGovernor.sdkGeneralize("androidx.appcompat.app.AlertDialog"), "通用 Dialog 骨架 → 泛化");
        TestHost.check(LearnGovernor.sdkGeneralize("com.app.PopupWindowHost"), "Popup 骨架 → 泛化");
        TestHost.check(!LearnGovernor.sdkGeneralize("com.sina.weibo.SplashActivity"), "微博自建开屏 → 非泛化（应用特有）");
        TestHost.check(!LearnGovernor.sdkGeneralize("com.example.MainTabActivity"), "普通主界面 → 非泛化");
        TestHost.check(!LearnGovernor.sdkGeneralize(null), "null → 非泛化");
    }

    /* ───────── ⑦ 分级预算与深度门 ───────── */

    private static void testScanBudget() {
        // L1 全量：弹窗/SDK/学习/开屏（广告概率最高——命中率红线场景）
        TestHost.checkEq(SmartScanPlanner.BUDGET_FULL, budget(ctxWith(true, false, false, false, false)), "SDK 窗 → L1 全量 600");
        TestHost.checkEq(SmartScanPlanner.BUDGET_FULL, budget(ctxWith(false, true, false, false, false)), "学习窗 → L1 全量");
        TestHost.checkEq(SmartScanPlanner.BUDGET_FULL, budget(ctxWith(false, false, true, false, false)), "开屏窗 → L1 全量");
        // L2 中量：新场景/弹窗期/视频前贴
        TestHost.checkEq(SmartScanPlanner.BUDGET_MID, budget(ctxWith(false, false, false, true, false)), "更新弹窗 → L2 中量 400");
        TestHost.checkEq(SmartScanPlanner.BUDGET_MID, budget(ctxWith(false, false, false, false, true)), "视频前贴 → L2 中量");
        // L3 轻量：普通窗口（预算 -30% 红线）
        TestHost.checkEq(SmartScanPlanner.BUDGET_LIGHT, budget(new Ctx()), "普通窗 → L3 轻量 350");
        TestHost.checkEq(SmartScanPlanner.BUDGET_LIGHT, budget(null), "null ctx → 轻量（保守预算）");
        // 深度门：普通窗砍深层
        TestHost.checkEq(SmartScanPlanner.DEPTH_FULL, SmartScanPlanner.depthLimitFor(ctxWith(true, false, false, false, false)), "SDK 窗深度 32");
        TestHost.checkEq(SmartScanPlanner.DEPTH_LIGHT, SmartScanPlanner.depthLimitFor(new Ctx()), "普通窗深度 20");
        TestHost.checkEq(SmartScanPlanner.DEPTH_LIGHT, SmartScanPlanner.depthLimitFor(null), "null ctx 深度 20");
        // 常量红线（调参门禁）
        TestHost.check(SmartScanPlanner.BUDGET_LIGHT < SmartScanPlanner.BUDGET_MID
                && SmartScanPlanner.BUDGET_MID < SmartScanPlanner.BUDGET_FULL, "预算梯度 L3<L2<L1");
        String st = SmartScanPlanner.stats();
        TestHost.check(st.contains("L1") && st.contains("L2") && st.contains("L3"), "stats 含三级预算");
    }

    private static int budget(Ctx ctx) {
        return SmartScanPlanner.budgetFor(ctx);
    }

    private static Ctx ctxWith(boolean sdk, boolean learned, boolean splash, boolean update, boolean video) {
        Ctx c = new Ctx();
        c.sdkAdWin = sdk;
        c.learnedCls = learned;
        c.splashLive = splash;
        c.updateDialog = update;
        c.videoApp = video;
        return c;
    }

    /* ───────── ⑧ 指数退避 ───────── */

    private static void testBackoff() {
        SmartScanPlanner.WindowCache cache = new SmartScanPlanner.WindowCache();
        // 新窗首扫：立即执行
        long w = SmartScanPlanner.backoffMs(cache, "pkg|ActivityA", NOW);
        TestHost.checkEq(0L, w, "新窗首扫 → 0ms");
        TestHost.checkEq("pkg|ActivityA", cache.lastWindowId, "缓存记窗");
        // 同窗无事件：800ms 起步
        TestHost.checkEq(800L, SmartScanPlanner.backoffMs(cache, "pkg|ActivityA", NOW + 1000), "同窗首次补扫 → 800ms");
        // 空转升级：1600 → 3200 → 6400（上限）
        SmartScanPlanner.noteIdleScan(cache);
        TestHost.checkEq(1600L, SmartScanPlanner.backoffMs(cache, "pkg|ActivityA", NOW + 2000), "空转 1 档 → 1600ms");
        SmartScanPlanner.noteIdleScan(cache);
        TestHost.checkEq(3200L, SmartScanPlanner.backoffMs(cache, "pkg|ActivityA", NOW + 3000), "空转 2 档 → 3200ms");
        SmartScanPlanner.noteIdleScan(cache);
        TestHost.checkEq(6400L, SmartScanPlanner.backoffMs(cache, "pkg|ActivityA", NOW + 4000), "空转 3 档 → 6400ms（上限）");
        for (int i = 0; i < 8; i++) SmartScanPlanner.noteIdleScan(cache);
        TestHost.checkEq(6400L, SmartScanPlanner.backoffMs(cache, "pkg|ActivityA", NOW + 5000), "档位封顶 → 6400ms");
        TestHost.check(cache.backoffStep <= 8, "档位上限 8（防溢出）");
        // 事件到达 → 重置退避（窗口真的变了，恢复灵敏度）
        SmartScanPlanner.noteActiveSignal(cache, NOW + 6000);
        TestHost.checkEq(0, cache.backoffStep, "事件信号 → 退避档归零");
        TestHost.checkEq(800L, SmartScanPlanner.backoffMs(cache, "pkg|ActivityA", NOW + 7000), "归零后 → 800ms 起步");
        // 窗口切换 → 首扫重置
        TestHost.checkEq(0L, SmartScanPlanner.backoffMs(cache, "pkg|ActivityB", NOW + 8000), "换窗 → 0ms 首扫");
        // null 防御
        TestHost.checkEq(0L, SmartScanPlanner.backoffMs(null, "x", NOW), "null 缓存 → 0ms");
        TestHost.checkEq(0L, SmartScanPlanner.backoffMs(cache, null, NOW), "null 窗标识 → 0ms");
    }

    /* ───────── ⑨ RuleEngine 第5层场景 ───────── */

    private static void testSceneLayer() {
        // 更新弹窗信号词普查
        TestHost.check(RuleEngine.isUpdatePositive("立即更新"), "肯定侧：立即更新");
        TestHost.check(RuleEngine.isUpdatePositive("马上更新"), "肯定侧：马上更新");
        TestHost.check(RuleEngine.isUpdatePositive("下载安装并体验新版本"), "肯定侧：含下载安装");
        TestHost.check(!RuleEngine.isUpdatePositive("以后再说"), "否定侧不是肯定侧");
        TestHost.check(!RuleEngine.isUpdatePositive(null) && !RuleEngine.isUpdatePositive(""), "null/空 → 非肯定侧");
        TestHost.check(RuleEngine.isUpdateNegative("以后再说"), "否定侧：以后再说");
        TestHost.check(RuleEngine.isUpdateNegative("忽略此版本"), "否定侧：忽略此版本");
        TestHost.check(!RuleEngine.isUpdateNegative("以后再说更多内容"), "否定侧等值精确（长文案不中）");
        TestHost.check(!RuleEngine.isUpdateNegative("立即更新"), "肯定侧不是否定侧");

        // 视频类应用判定（前贴场景）
        TestHost.check(RuleEngine.isVideoApp("tv.danmaku.bili"), "哔哩哔哩 → 视频应用");
        TestHost.check(RuleEngine.isVideoApp("com.ss.android.article.video.home"), "抖音（前缀）→ 视频应用");
        TestHost.check(!RuleEngine.isVideoApp("com.sina.weibo"), "微博 → 非视频应用");
        TestHost.check(!RuleEngine.isVideoApp(null), "null → 非视频应用");

        // matchText 第5a层：仅 ctx.updateDialog 双信号确认后放行
        Ctx upd = ctx(1080, 2400, "com.example.app");
        upd.updateDialog = true;
        Hit neg = RuleEngine.matchText("以后再说", new int[]{100, 1800, 300, 1900}, node("以后再说", null), upd);
        TestHost.check(neg != null && "update_negative".equals(neg.appId), "更新弹窗双信号 → 否定侧命中");
        Ctx updOff = ctx(1080, 2400, "com.example.app");
        updOff.updateDialog = false;
        TestHost.check(RuleEngine.matchText("以后再说", new int[]{100, 1800, 300, 1900}, node("以后再说", null), updOff) == null,
                "无肯定侧同场信号 → 绝不出手（误触红线）");

        // matchVid 5b：视频前贴 vid 键（videoApp 放行）
        // 服务侧真实链路：ctx.videoApp = RuleEngine.isVideoApp(pkg)（前贴场景置位）
        int[] prerollB = {960, 90, 1050, 180};
        Ctx bili = ctx(1080, 2400, "tv.danmaku.bili");
        bili.videoApp = RuleEngine.isVideoApp("tv.danmaku.bili");
        TestHost.check(bili.videoApp, "前置：哔哩哔哩 isVideoApp → ctx.videoApp 置位（真实链路）");
        TestHost.check(RuleEngine.matchVid(vidNode("com.app:id/preroll_close", prerollB), prerollB, bili) != null,
                "videoApp + preroll vid → 命中");
        Ctx weibo = ctx(1080, 2400, "com.sina.weibo");
        weibo.videoApp = RuleEngine.isVideoApp("com.sina.weibo");
        TestHost.check(!weibo.videoApp, "前置：微博 isVideoApp=false（非视频应用）");
        TestHost.check(RuleEngine.matchVid(vidNode("com.app:id/preroll_close", prerollB), prerollB, weibo) == null,
                "非视频应用 + preroll vid → 不命中");

        // matchVid 5c：悬浮球（边缘 + ≤5% 面积 + close/drag 族 vid）
        int[] ball = {1030, 600, 1070, 640}; // 右缘带，约 0.06% 屏
        TestHost.check(RuleEngine.matchVid(vidNode("com.app:id/float_ball_close", ball), ball, ctx(1080, 2400, "com.example.app")) != null,
                "悬浮球（边缘+小面积+close）→ 命中");
        // 非边缘位置 → 不命中（普通控件防误触）
        int[] center = {500, 1100, 540, 1140};
        TestHost.check(RuleEngine.matchVid(vidNode("com.app:id/float_ball_close", center), center, ctx(1080, 2400, "com.example.app")) == null,
                "屏中央同 vid → 不命中（边缘带门）");
    }

    /* ───────── ⑩ 第0~3层回归（issue#64 微博锚点） ───────── */

    private static void testTextLayers() {
        /* issue#64 微博场景回归锚点：「跳过广告」分应用精确命中 →
         * gestureOnly 修复后此命中的文案必须能通过边界自检语义层 */
        Ctx weibo = ctx(1080, 2400, "com.sina.weibo");
        int[] skip = {920, 80, 1050, 150};
        Hit wb = RuleEngine.matchText("跳过广告", skip, node("跳过广告", null), weibo);
        TestHost.check(wb != null && "app_exact".equals(wb.appId), "微博「跳过广告」→ 第0层分应用命中");
        TestHost.checkEq("跳过广告", wb.label, "命中文案随 Hit.label 传递（边界自检语义依据）");
        // label 语义通道验证（ComplianceGuardTest ⑤ 同款红线）
        TestHost.check(com.zai.adshield.core.ComplianceGuard.boundaryCheck(wb.label, "", skip, 1080, 2400,
                        "com.sina.weibo", "com.sina.weibo.SplashActivity", false)
                        == com.zai.adshield.core.ComplianceGuard.BND_OK,
                "gestureOnly 文案过边界自检 → 微博漏拦截端到端根治");

        /* 第0层：分应用不受长度上限（keep 4 字精确命中） */
        TestHost.check(RuleEngine.matchText("跳过", skip, node("跳过", null), ctx(1080, 2400, "com.gotokeep.keep")) != null,
                "keep「跳过」→ 分应用命中");
        /* 包名前缀不匹配 → 第0层不中（落到第1层全局） */
        TestHost.check(RuleEngine.matchText("跳过", skip, node("跳过", null), ctx(1080, 2400, "com.other.app")) != null,
                "他包「跳过」→ 全局精确兜底");

        /* 第1层：全局精确（大小写归一） */
        TestHost.check(RuleEngine.matchText("Skip", skip, node("Skip", null), ctx(1080, 2400, "com.x.app")) != null,
                "「Skip」大小写归一 → 全局精确");
        TestHost.check(RuleEngine.matchText("跳過廣告", skip, node("跳過廣告", null), ctx(1080, 2400, "com.x.app")) != null,
                "繁体「跳過廣告」→ 全局精确");

        /* 第2层：倒计时形态（营销话术数学上无法混入） */
        TestHost.check(RuleEngine.matchText("跳过广告3", skip, node("跳过广告3", null), ctx(1080, 2400, "com.x.app")) != null,
                "「跳过广告3」尾置倒计时 → 形态命中");
        TestHost.check(RuleEngine.matchText("3s后可跳过", skip, node("3s后可跳过", null), ctx(1080, 2400, "com.x.app")) != null,
                "「3s后可跳过」头置倒计时 → 形态命中");

        /* 第3层：短包含 + 诱导词一票否决 */
        TestHost.check(RuleEngine.matchText("跳过 5", skip, node("跳过 5", null), ctx(1080, 2400, "com.x.app")) != null,
                "「跳过 5」短包含 → 命中");
        TestHost.check(RuleEngine.matchText("摇一摇跳过详情", skip, node("摇一摇跳过详情", null), ctx(1080, 2400, "com.x.app")) == null,
                "诱导词「摇/详情」→ 一票否决");
        TestHost.check(RuleEngine.matchText("跳过领礼包", skip, node("跳过领礼包", null), ctx(1080, 2400, "com.x.app")) == null,
                "诱导词「领/礼包」→ 一票否决");

        /* 置信度层码（多候选排序依据） */
        TestHost.checkEq(3, RuleEngine.tierFull("global_exact"), "全局精确 → 定信层 3");
        TestHost.checkEq(3, RuleEngine.tierFull("app_exact"), "分应用 → 定信层 3");
        TestHost.checkEq(3, RuleEngine.tierFull("update_negative"), "更新否定 → 定信层 3");
        TestHost.checkEq(2, RuleEngine.tierFull("global_pattern"), "倒计时形态 → 层 2");
        TestHost.checkEq(1, RuleEngine.tierFull("global_contains"), "短包含 → 层 1");

        /* matchAll 多候选排序：短文案优先（真键精确） */
        SimNode root = new SimNode();
        SimNode a = TestHost.skipButton("跳过广告");
        SimNode b = TestHost.skipButton("跳过");
        root.add(a);
        root.add(b);
        java.util.ArrayList<Hit> hits = RuleEngine.matchAll(root, ctx(1080, 2400, "com.sina.weibo"), 4);
        TestHost.check(hits.size() >= 2, "多候选收集 ≥2");
        TestHost.checkEq("跳过", hits.get(0).label, "同置信度短文案优先（「跳过」先于「跳过广告」）");

        /* 规则库统计 */
        TestHost.check(RuleEngine.stats().contains("分应用表"), "stats 含分应用表计数");
    }

    /* ───────── 工具 ───────── */

    private static Ctx ctx(int w, int h, String pkg) {
        Ctx c = new Ctx();
        c.screenW = w;
        c.screenH = h;
        c.pkg = pkg;
        c.budgetNodes = 600;
        return c;
    }

    private static SimNode node(String text, int[] bounds) {
        SimNode n = bounds == null ? new SimNode() : new SimNode(text, bounds[0], bounds[1], bounds[2], bounds[3]);
        if (bounds == null) n.text = text;
        n.clickable = true;
        return n;
    }

    private static SimNode vidNode(String vid, int[] bounds) {
        SimNode n = new SimNode();
        n.vid = vid;
        n.boundsArr = bounds;
        return n;
    }
}
