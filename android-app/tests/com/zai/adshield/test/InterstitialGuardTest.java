package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.ComplianceGuard;
import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.InterstitialGuard;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 插屏卫士单元测试（v7.6.0 激进版重做 · 用户十五轮实锤：「软件内插入的
 * 广告不仅没跳过……你可以激进一点暂时先放弃宁可漏拦绝不误触」）。
 * 与 FeedAdGuard/MiniProgramGuard 同规约（纯 Java 零 Android 依赖，
 * ECJ -1.8 编译 JVM 运行）。
 * ─────────────────────────────────────────────────────────────
 * v7.5.0 版死因复现断言（本套件证伪旧实现）：
 *  D1 闸⓪ isDisclosureBadge 精确等值拒「广告 · 了解详情」「查看广告详情」
 *     ——hasAdContextWord contains 一网打尽（S3）；
 *  D2 通用终闸语义面杀「知道了/残忍离开」（词表收了也白收）——
 *     interBoundaryCheck 语义面收编（S6）；
 *  D3 闸③ 带位闸灭中部关闭钮——非滚动窗零位置闸（S4/S5）；
 *  D4 滚动窗一票否决名单外信息流卡片——近旁铁证可出手（S5）。
 * 断言规划（check/checkEq 总数 90）：
 *  S1 关闭词语义闸（33：21 正例 + 12 反例）
 *  S2 词优先级（6）/ S3 广告语境在场闸（9）
 *  S4 滚动窗近旁闸（5）/ S5 决策真值表（16）
 *  S6 interBoundaryCheck 终闸语义面（13）
 *  S7 常量在场（6）/ S8 九品牌分辨率 1:1 缩放（2 汇总断言组）。
 * 标准屏 1080×2400；语境节点 bounds {60,100,190,180}；关闭钮
 * {960,100,1060,180}。
 */
public final class InterstitialGuardTest {

    public static void main(String[] args) {
        /* ════ S1 关闭词语义闸（33 项：21 正例 + 12 反例）════ */
        TestHost.suite("InterstitialGuard · S1 关闭词语义闸（精确等值——绝不 contains）");
        String[] closes = {
                "×", "✕", "✖", "╳", "⨯",                       // × 家族（真机插屏关闭钮常态）
                "关闭", "关闭广告", "关闭此广告",                 // 关闭族
                "跳过", "跳过广告", "跳过此广告", "跳過", "跳過廣告", "Skip", "Skip Ad", // 跳过族
                "知道了", "我知道了", "close",                   // 知道了族（收起弹层）
                "残忍离开", "残忍退出", "残忍拒绝",               // v7.6.0：游戏插屏标配（「残忍离开」几乎唯一关闭词）
                "不再看", "不看了", "暂不需要", "不需要",         // v7.6.0：贴片/升级插层常态
        };
        for (String w : closes) {
            TestHost.check(InterstitialGuard.isInterstitialCloseWord(w), "关闭词：" + w);
        }
        String[] nonCloses = {
                "进入广告", "查看详情", "立即下载",               // 营销/进入方向——绝不代点
                "不感兴趣",                                     // feed P2 菜单项——归两段式，插屏路不收
                "X",                                            // 拉丁 X（正文/昵称误而险）
                "x",                                            // 小写拉丁 x 同拒
                "取消",                                         // 通用弹窗词——激进模式下也保持拒收（误点面不可控）
                "点击关闭广告查看更多",                            // >8 字必是正文
                "残忍离开下一页",                                // 长变体拒（>8 字）
                "3s",
                "领取奖励",
                "残忍",                                         // 词根单独出现非关闭词（正文可能含）
        };
        for (String w : nonCloses) {
            TestHost.check(!InterstitialGuard.isInterstitialCloseWord(w), "非关闭词：" + w);
        }
        TestHost.check(!InterstitialGuard.isInterstitialCloseWord(null), "非关闭词：null");

        /* ════ S2 词优先级（6 项）════ */
        TestHost.suite("InterstitialGuard · S2 词优先级（× > 关闭 > 跳过 > 知道了 > 残忍离开族）");
        TestHost.checkEq(0, InterstitialGuard.closeWordPriority("×"), "「×」优先级 0（最优）");
        TestHost.check(InterstitialGuard.closeWordPriority("关闭") > InterstitialGuard.closeWordPriority("×"),
                "关闭族优先级 > × 家族");
        TestHost.check(InterstitialGuard.closeWordPriority("跳过") > InterstitialGuard.closeWordPriority("关闭"),
                "跳过族优先级 > 关闭族");
        TestHost.check(InterstitialGuard.closeWordPriority("知道了") > InterstitialGuard.closeWordPriority("跳过"),
                "知道了族优先级 > 跳过族");
        TestHost.check(InterstitialGuard.closeWordPriority("残忍离开") > InterstitialGuard.closeWordPriority("知道了"),
                "残忍离开族 > 知道了族（数组序保持）");
        TestHost.checkEq(-1, InterstitialGuard.closeWordPriority("进入广告"), "未知词优先级 -1");

        /* ════ S3 广告语境在场闸（9 项）════ */
        TestHost.suite("InterstitialGuard · S3 广告语境在场闸（contains——v7.5.0 死因①根治）");
        TestHost.check(InterstitialGuard.hasAdContextWord("广告"), "「广告」过闸");
        TestHost.check(InterstitialGuard.hasAdContextWord("广告 · 了解详情"), "「广告 · 了解详情」过闸（v7.5.0 isDisclosureBadge 精确等值拒——死因①）");
        TestHost.check(InterstitialGuard.hasAdContextWord("查看广告详情"), "「查看广告详情」过闸");
        TestHost.check(InterstitialGuard.hasAdContextWord("推广"), "「推广」过闸");
        TestHost.check(InterstitialGuard.hasAdContextWord("广告推荐"), "「广告推荐」过闸");
        TestHost.check(InterstitialGuard.hasAdContextWord(" 赞助 "), "「赞助」trim 后过闸");
        TestHost.check(!InterstitialGuard.hasAdContextWord("就业"), "「就业」非语境词（招聘 App 正文误点面——不收）");
        TestHost.check(!InterstitialGuard.hasAdContextWord("广告文案策划全解析从入门到精通的完整指南手册"), "超长正文虽含「广告」不算语境（>10 字）");
        TestHost.check(!InterstitialGuard.hasAdContextWord(null), "null 拒");

        /* ════ S4 滚动窗近旁闸（5 项）════ */
        TestHost.suite("InterstitialGuard · S4 滚动窗近旁闸（|Δcy|≤35% 同排铁证）");
        int[] ctx = {60, 100, 190, 180};       // 广告语境节点（左上）
        int[] closeNear = {960, 100, 1060, 180}; // 同排关闭钮（右上——|Δcy|=0）
        TestHost.check(InterstitialGuard.nearContext(closeNear, ctx, 2400), "同排（|Δcy|=0）近旁过闸");
        TestHost.check(InterstitialGuard.nearContext(new int[]{960, 700, 1060, 780}, ctx, 2400),
                "|Δcy|≈25% 屏高（cy 140 vs 740 → 600≤840）近旁过闸");
        TestHost.check(!InterstitialGuard.nearContext(new int[]{960, 1400, 1060, 1480}, ctx, 2400),
                "|Δcy|≈55% 屏高远距拒（信息流上一卡不是本卡）");
        TestHost.check(!InterstitialGuard.nearContext(closeNear, null, 2400), "语境节点缺失近旁拒");
        TestHost.check(!InterstitialGuard.nearContext(closeNear, ctx, 0), "屏幕未知拒（保守）");

        /* ════ S5 决策真值表（16 项）════ */
        TestHost.suite("InterstitialGuard · S5 决策真值表（闸⓪~闸③激进版）");
        java.util.ArrayList<AdDecisionCore.NodeView> b1 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c1 = new java.util.ArrayList<>();
        b1.add(new SimNode("广告", 60, 100, 190, 180));
        c1.add(new SimNode("×", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision hit = InterstitialGuard.decideFromCandidates(b1, c1, 1080, 2400, false, false);
        TestHost.check(hit != null && InterstitialGuard.ACTION_CLOSE.equals(hit.action)
                        && "×".equals(hit.label) && hit.tapBounds[0] == 960 && hit.tapBounds[2] == 1060,
                "广告语境+同排 × → 出手（tapBounds=×自身）");
        // v7.5.0 死因③：非滚动窗中部关闭钮（带位闸灭）——激进版零位置闸
        java.util.ArrayList<AdDecisionCore.NodeView> b2 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c2 = new java.util.ArrayList<>();
        b2.add(new SimNode("查看广告详情", 500, 1100, 780, 1180));
        c2.add(new SimNode("残忍离开", 400, 1300, 680, 1380));
        FeedAdGuard.FeedDecision mid = InterstitialGuard.decideFromCandidates(b2, c2, 1080, 2400, false, false);
        TestHost.check(mid != null && "残忍离开".equals(mid.label),
                "非滚动窗·中部「残忍离开」+ 广告语境 → 出手（旧带位闸全灭中部——死因③根治）");
        // 语境变体：广告 · 了解详情
        java.util.ArrayList<AdDecisionCore.NodeView> b3 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c3 = new java.util.ArrayList<>();
        b3.add(new SimNode("广告 · 了解详情", 60, 2100, 420, 2180));
        c3.add(new SimNode("×", 960, 2100, 1060, 2180));
        FeedAdGuard.FeedDecision var = InterstitialGuard.decideFromCandidates(b3, c3, 1080, 2400, false, false);
        TestHost.check(var != null && "×".equals(var.label),
                "「广告 · 了解详情」语境变体（v7.5.0 isDisclosureBadge 拒）+ 底带 × → 出手");
        // 语境不在场 → 拒
        java.util.ArrayList<AdDecisionCore.NodeView> b4 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c4 = new java.util.ArrayList<>();
        c4.add(new SimNode("×", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision noCtx = InterstitialGuard.decideFromCandidates(b4, c4, 1080, 2400, false, false);
        TestHost.check(noCtx == null, "无广告语境 → 绝不出手（普通页面零误点面）");
        // 关闭词缺 → 拒
        java.util.ArrayList<AdDecisionCore.NodeView> b5 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c5 = new java.util.ArrayList<>();
        b5.add(new SimNode("广告", 60, 100, 190, 180));
        FeedAdGuard.FeedDecision noClose = InterstitialGuard.decideFromCandidates(b5, c5, 1080, 2400, false, false);
        TestHost.check(noClose == null, "语境在场无关闭钮 → 拒");
        // 关闭钮超大形态（广告主体）→ 拒
        java.util.ArrayList<AdDecisionCore.NodeView> b6 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c6 = new java.util.ArrayList<>();
        b6.add(new SimNode("广告", 60, 100, 190, 180));
        c6.add(new SimNode("跳过", 100, 400, 500, 700));
        FeedAdGuard.FeedDecision big = InterstitialGuard.decideFromCandidates(b6, c6, 1080, 2400, false, false);
        TestHost.check(big == null, "关闭钮 400×300 超形态（>30%×12%）→ 拒（绝不点广告主体）");
        // 营销词冒充关闭词 → 拒
        java.util.ArrayList<AdDecisionCore.NodeView> b7 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c7 = new java.util.ArrayList<>();
        b7.add(new SimNode("广告", 60, 100, 190, 180));
        c7.add(new SimNode("进入广告", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision fake = InterstitialGuard.decideFromCandidates(b7, c7, 1080, 2400, false, false);
        TestHost.check(fake == null, "「进入广告」营销词 → 精确等值拒（绝不点进广告）");
        // 不可见关闭钮 → 拒
        SimNode hidden = new SimNode("×", 960, 100, 1060, 180);
        hidden.visible = false;
        java.util.ArrayList<AdDecisionCore.NodeView> b8 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c8 = new java.util.ArrayList<>();
        b8.add(new SimNode("广告", 60, 100, 190, 180));
        c8.add(hidden);
        FeedAdGuard.FeedDecision invis = InterstitialGuard.decideFromCandidates(b8, c8, 1080, 2400, false, false);
        TestHost.check(invis == null, "不可见关闭钮 → 拒");
        // 滚动窗近旁铁证 → 出手（v7.5.0 一票否决——死因④根治：名单外信息流卡片）
        java.util.ArrayList<AdDecisionCore.NodeView> b9 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c9 = new java.util.ArrayList<>();
        b9.add(new SimNode("广告", 60, 100, 190, 180));
        c9.add(new SimNode("关闭", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision scrollNear = InterstitialGuard.decideFromCandidates(b9, c9, 1080, 2400, true, false);
        TestHost.check(scrollNear != null && "关闭".equals(scrollNear.label),
                "滚动窗·同排近距关闭钮 → 出手（近旁铁证）");
        // 滚动窗远距 → 拒（上一卡的关闭钮不代点）
        java.util.ArrayList<AdDecisionCore.NodeView> b10 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c10 = new java.util.ArrayList<>();
        b10.add(new SimNode("广告", 60, 100, 190, 180));
        c10.add(new SimNode("关闭", 960, 1400, 1060, 1480));
        FeedAdGuard.FeedDecision scrollFar = InterstitialGuard.decideFromCandidates(b10, c10, 1080, 2400, true, false);
        TestHost.check(scrollFar == null, "滚动窗远距（|Δcy|≈55%）→ 拒（上一卡不代点）");
        // 多关闭候选取最优优先级
        java.util.ArrayList<AdDecisionCore.NodeView> b11 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c11 = new java.util.ArrayList<>();
        b11.add(new SimNode("广告", 60, 100, 190, 180));
        c11.add(new SimNode("知道了", 960, 100, 1060, 180));
        c11.add(new SimNode("×", 900, 100, 1000, 180));
        FeedAdGuard.FeedDecision best = InterstitialGuard.decideFromCandidates(b11, c11, 1080, 2400, false, false);
        TestHost.check(best != null && "×".equals(best.label), "多关闭候选取最优（× > 知道了——数组序优先级）");
        // 长文案语境节点（>10 字）不算在场
        java.util.ArrayList<AdDecisionCore.NodeView> b12 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c12 = new java.util.ArrayList<>();
        b12.add(new SimNode("广告文案策划全解析从入门到精通", 60, 100, 190, 180));
        c12.add(new SimNode("×", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision longCtx = InterstitialGuard.decideFromCandidates(b12, c12, 1080, 2400, false, false);
        TestHost.check(longCtx == null, "超长正文语境节点不算在场 → 拒（contains 面可控）");
        // desc-only 语境节点也认
        SimNode descOnly = new SimNode("", 60, 100, 190, 180);
        descOnly.desc = "广告";
        java.util.ArrayList<AdDecisionCore.NodeView> b13 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c13 = new java.util.ArrayList<>();
        b13.add(descOnly);
        c13.add(new SimNode("×", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision descHit = InterstitialGuard.decideFromCandidates(b13, c13, 1080, 2400, false, false);
        TestHost.check(descHit != null, "desc-only 语境节点（contentDescription）→ 也认");
        // 屏幕未知兜底形态闸
        java.util.ArrayList<AdDecisionCore.NodeView> b14 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c14 = new java.util.ArrayList<>();
        b14.add(new SimNode("广告", 60, 100, 190, 180));
        c14.add(new SimNode("×", 60, 100, 360, 240));
        FeedAdGuard.FeedDecision fallback = InterstitialGuard.decideFromCandidates(b14, c14, 0, 0, false, false);
        TestHost.check(fallback != null, "屏幕未知 → 324×144px 兜底形态闸过（300×140 关闭钮）");

        /* ════ S6 interBoundaryCheck 终闸语义面（13 项——v7.5.0 死因②根治）════ */
        TestHost.suite("InterstitialGuard · S6 interBoundaryCheck 终闸（知道了/残忍离开收编）");
        int[] cb = {960, 100, 1060, 180};
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("知道了", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「知道了」过插屏终闸（v7.5.0 通用终闸 BND_FAIL_SEMANTIC 一票否决——死因②）");
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("残忍离开", cb, 1080, 2400, "com.example.game", "android.widget.FrameLayout"),
                "「残忍离开」过插屏终闸（游戏插屏标配）");
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("不再看", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「不再看」过插屏终闸");
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("暂不需要", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「暂不需要」过插屏终闸");
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("×", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「×」过插屏终闸");
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("跳过", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「跳过」过插屏终闸");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC, ComplianceGuard.interBoundaryCheck("进入广告", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「进入广告」终闸拒（营销方向）");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC, ComplianceGuard.interBoundaryCheck("立即下载", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「立即下载」终闸拒");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC, ComplianceGuard.interBoundaryCheck("领取奖励", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「领取奖励」终闸拒");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_FORM, ComplianceGuard.interBoundaryCheck("跳过", new int[]{100, 400, 500, 700}, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "超大形态（400×300=41% 屏宽）终闸拒");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_FORM, ComplianceGuard.interBoundaryCheck("跳过", null, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "null bounds 终闸拒");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SCENARIO, ComplianceGuard.interBoundaryCheck("跳过", cb, 1080, 2400, "com.android.settings", "android.widget.FrameLayout"),
                "系统设置零扫描场景终闸拒");
        // 对比断言：通用终闸杀「知道了」（v7.5.0 死因复现）
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC, ComplianceGuard.boundaryCheck("知道了", "", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout", false),
                "死因复现：通用终闸对「知道了」BND_FAIL_SEMANTIC（v7.5.0 词表收了也白收）");

        /* ════ S7 常量在场（6 项）════ */
        TestHost.suite("InterstitialGuard · S7 常量在场（激进模式参数契约）");
        TestHost.checkEq(2500L, InterstitialGuard.INTER_SCAN_THROTTLE_MS, "扫描节流 2.5s 契约保持");
        TestHost.check(InterstitialGuard.CLOSE_W_MAX == 0.30, "关闭钮宽上限 30% 契约保持");
        TestHost.check(InterstitialGuard.CLOSE_H_MAX == 0.12, "关闭钮高上限 12% 契约保持");
        TestHost.check(InterstitialGuard.INTER_NEAR_Y_MAX == 0.35, "滚动窗近旁 |Δcy|≤35% 契约保持");
        TestHost.checkEq(8, InterstitialGuard.CLOSE_MAX_CHARS, "关闭词字符上限 8 保持");
        TestHost.checkEq(10, InterstitialGuard.CONTEXT_MAX_CHARS, "语境词字符上限 10（v7.6.0）");

        /* ════ S8 九品牌分辨率 1:1 缩放（插屏几何按机型分辨率逐机型全过）════ */
        TestHost.suite("InterstitialGuard · S8 九品牌分辨率 1:1 缩放");
        int[][] brandRes = {
                {1080, 2400}, {720, 1600}, {1440, 3200}, {1080, 2340},
                {1260, 2800}, {1220, 2712}, {1176, 2400}, {1080, 2412}, {1200, 2652},
        };
        String[] brands = {"OPPO", "vivo", "三星", "小米", "一加", "iQOO", "荣耀", "华为", "红米"};
        int brandOk = 0;
        for (int bi = 0; bi < brandRes.length; bi++) {
            int w = brandRes[bi][0], h = brandRes[bi][1];
            // 按分辨率 1:1 缩放：语境节点（左上）+ 关闭钮（右上同排）
            java.util.ArrayList<AdDecisionCore.NodeView> bb = new java.util.ArrayList<>();
            java.util.ArrayList<AdDecisionCore.NodeView> cc = new java.util.ArrayList<>();
            bb.add(new SimNode("广告", (int) (w * 0.05), (int) (h * 0.04), (int) (w * 0.17), (int) (h * 0.075)));
            cc.add(new SimNode("×", (int) (w * 0.88), (int) (h * 0.04), (int) (w * 0.98), (int) (h * 0.075)));
            FeedAdGuard.FeedDecision d = InterstitialGuard.decideFromCandidates(bb, cc, w, h, false, false);
            boolean pass = d != null && "×".equals(d.label)
                    && d.tapBounds[0] >= 0 && d.tapBounds[2] <= w;
            if (pass) brandOk++;
        }
        TestHost.checkEq(9, brandOk, "九品牌分辨率 1:1 缩放（语境+关闭钮几何逐机型全过）");
        // 反例：各品牌同几何无语境 → 零出手（误点面红线）
        int brandReject = 0;
        for (int bi = 0; bi < brandRes.length; bi++) {
            int w = brandRes[bi][0], h = brandRes[bi][1];
            java.util.ArrayList<AdDecisionCore.NodeView> cc = new java.util.ArrayList<>();
            cc.add(new SimNode("×", (int) (w * 0.88), (int) (h * 0.04), (int) (w * 0.98), (int) (h * 0.075)));
            FeedAdGuard.FeedDecision d = InterstitialGuard.decideFromCandidates(new java.util.ArrayList<AdDecisionCore.NodeView>(), cc, w, h, false, false);
            if (d == null) brandReject++;
        }
        TestHost.checkEq(9, brandReject, "九品牌无语境反例零出手（宁漏勿误基线保持）");

        /* ════ S9 v7.15.2 倒计时型关闭词族（GKD 全局规则同源）════
         * 真机日志实锤（#96 02:22 / #95 03:18）：「定时关闭，剩余24分16…」
         * 「跳过 4」被闸①精确等值全拒——子串直查找得到、词闸拒之门外。 */
        TestHost.suite("InterstitialGuard · S9 v7.15.2 倒计时关闭词族（跳过4/定时关闭倒计时/N后关闭）");
        String[] cdCloses = {
                "跳过 4", "跳过 3", "跳过5秒", "4s 跳过", "跳過 2",     // countdownSkip（GKD text*="跳过" 同源）
                "定时关闭 5s", "定时关闭，剩余24分16…",                // scheduledClose（真机实锤样本；带数字）
                "5s后关闭", "3秒后可关闭", "15秒后关闭",                  // delayedClose
        };
        for (String w : cdCloses) {
            TestHost.check(InterstitialGuard.isInterstitialCloseWord(w), "倒计时关闭词：" + w);
        }
        String[] cdNonCloses = {
                "跳过啊",                                     // 无数字（纯词走精确表，变体不算）
                "定时关闭",                                    // 无数字——播放器功能钮形态（非开头带尾不算，歧义拒）
                "定时关闭广告后自动跳转领取福利大礼包超级加倍版",     // >16 字长文案拒
                "明日定时关闭",                                // 非「定时关闭」开头
                "领取3天", "第3期", "24分16",                   // 有数字无倒计时关闭语义
                "后关闭",                                      // 无数字
        };
        for (String w : cdNonCloses) {
            TestHost.check(!InterstitialGuard.isCountdownCloseForm(w), "非倒计时关闭形态：" + w);
        }
        TestHost.check(!InterstitialGuard.isCountdownCloseForm(null), "null 拒（倒计时族）");
        // 倒计时族优先级：劣于全部精确词（CD_CLOSE_PRIORITY=100 > 精确表全长）
        TestHost.checkEq(100, InterstitialGuard.closeWordPriority("跳过 4"), "「跳过 4」倒计时族优先级 100");
        TestHost.check(InterstitialGuard.closeWordPriority("跳过 4") > InterstitialGuard.closeWordPriority("知道了"),
                "倒计时族劣于「知道了」（精确词优先出手）");
        // 终闸语义面收编（v7.5.0 死因②同款防线——倒计时词也过 interBoundaryCheck）
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("跳过 4", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「跳过 4」过插屏终闸（倒计时族语义面收编）");
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("定时关闭，剩余24分16…", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「定时关闭，剩余24分16…」过插屏终闸（真机实锤样本）");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC, ComplianceGuard.interBoundaryCheck("明日定时关闭", cb, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「明日定时关闭」终闸拒（非开头变体）");

        /* ════ S10 v7.15.2 强窗形免语境（SDK 广告窗/Dialog——窗口形态即铁证）════ */
        TestHost.suite("InterstitialGuard · S10 v7.15.2 强窗形免语境（无角标插屏根治）");
        // 强窗形 + 倒计时关闭钮 + 无语境 → 出手（#96 实锤死因：无语境词一票否决）
        java.util.ArrayList<AdDecisionCore.NodeView> k1 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> k1c = new java.util.ArrayList<>();
        k1c.add(new SimNode("定时关闭，剩余24分16…", 940, 2000, 1060, 2080));
        FeedAdGuard.FeedDecision sdkCd = InterstitialGuard.decideFromCandidates(k1, k1c, 1080, 2400, false, true);
        TestHost.check(sdkCd != null && sdkCd.label.startsWith("定时关闭"),
                "SDK 广告窗 + 「定时关闭，剩余24分16…」+ 无语境 → 出手（返回开屏窗真机实锤）");
        // 强窗形 + 精确词「知道了」+ 无语境 → 出手
        java.util.ArrayList<AdDecisionCore.NodeView> k2 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> k2c = new java.util.ArrayList<>();
        k2c.add(new SimNode("知道了", 480, 1100, 600, 1180));
        FeedAdGuard.FeedDecision dialogKnow = InterstitialGuard.decideFromCandidates(k2, k2c, 1080, 2400, false, true);
        TestHost.check(dialogKnow != null && "知道了".equals(dialogKnow.label),
                "Dialog 形态 + 「知道了」+ 无语境 → 出手（无角标弹层插屏）");
        // 强窗形 + 滚动容器 + 倒计时关闭 → 出手（闸③近旁基准缺失同步豁免）
        java.util.ArrayList<AdDecisionCore.NodeView> k3 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> k3c = new java.util.ArrayList<>();
        k3c.add(new SimNode("跳过 4", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision sdkScroll = InterstitialGuard.decideFromCandidates(k3, k3c, 1080, 2400, true, true);
        TestHost.check(sdkScroll != null && "跳过 4".equals(sdkScroll.label),
                "SDK 广告窗 + 滚动容器 + 「跳过 4」→ 出手（H5 容器常态）");
        // 强窗形 + 超大形态关闭钮 → 拒（闸②形态保持——绝不点广告主体）
        java.util.ArrayList<AdDecisionCore.NodeView> k4c = new java.util.ArrayList<>();
        k4c.add(new SimNode("跳过 4", 100, 400, 500, 700));
        FeedAdGuard.FeedDecision sdkBig = InterstitialGuard.decideFromCandidates(new java.util.ArrayList<AdDecisionCore.NodeView>(), k4c, 1080, 2400, false, true);
        TestHost.check(sdkBig == null, "强窗形 + 400×300 超形态 → 拒（绝不点广告主体）");
        // 强窗形 + 营销词 → 拒（闸①语义保持）
        java.util.ArrayList<AdDecisionCore.NodeView> k5c = new java.util.ArrayList<>();
        k5c.add(new SimNode("进入广告", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision sdkFake = InterstitialGuard.decideFromCandidates(new java.util.ArrayList<AdDecisionCore.NodeView>(), k5c, 1080, 2400, false, true);
        TestHost.check(sdkFake == null, "强窗形 + 「进入广告」营销词 → 拒（绝不点进广告）");
        // 普通窗（strongWinForm=false）无语境 → 仍拒（S5 基线不破——宁漏勿误红线）
        java.util.ArrayList<AdDecisionCore.NodeView> k6c = new java.util.ArrayList<>();
        k6c.add(new SimNode("定时关闭，剩余24分16…", 940, 2000, 1060, 2080));
        FeedAdGuard.FeedDecision plainCd = InterstitialGuard.decideFromCandidates(new java.util.ArrayList<AdDecisionCore.NodeView>(), k6c, 1080, 2400, false, false);
        TestHost.check(plainCd == null, "普通窗无语境（播放器定时关闭功能钮）→ 拒（误触红线保持）");
        // 普通窗 + 语境在场 + 倒计时关闭 → 出手（闸⓪过 + 倒计时族过）
        java.util.ArrayList<AdDecisionCore.NodeView> k7 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> k7c = new java.util.ArrayList<>();
        k7.add(new SimNode("广告", 60, 2000, 190, 2080));
        k7c.add(new SimNode("定时关闭，剩余24分16…", 940, 2000, 1060, 2080));
        FeedAdGuard.FeedDecision plainAdCd = InterstitialGuard.decideFromCandidates(k7, k7c, 1080, 2400, false, false);
        TestHost.check(plainAdCd != null && plainAdCd.label.startsWith("定时关闭"),
                "普通窗 + 广告语境 + 倒计时关闭钮 → 出手（VIP 试用插屏真机形态）");

        TestHost.done();
    }
}
