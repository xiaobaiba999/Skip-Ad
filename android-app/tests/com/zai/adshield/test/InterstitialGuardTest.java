package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.InterstitialGuard;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 插屏卫士单元测试（v7.5.0 首发 · 用户十四轮实锤：「app 内的插入的广告
 * 也是，它们几乎都带有广告两个字」——名单外 App 的插入广告主动关闭）。
 * 与 FeedAdGuard/MiniProgramGuard 同规约（纯 Java 零 Android 依赖，
 * ECJ -1.8 编译 JVM 运行）。
 * ─────────────────────────────────────────────────────────────
 * 断言规划（check/checkEq 总数 83）：
 *  S1 关闭词语义闸（25：17 正例 + 8 反例）
 *  S2 词优先级（5）/ S3 关闭钮形态闸（6）/ S4 近旁/带位闸（6）
 *  S5 决策真值表（12）/ S6 常量在场（7）
 *  S7 九品牌分辨率 1:1 缩放（18 = 9 品牌 × 2 路）
 *  S8 真机插屏文案重建（4）。
 * 标准屏 1080×2400；角标 bounds {60,100,190,180}（宽 130 高 80——
 * 满足 ≤35% 屏宽 × ≤8% 屏高）；关闭钮 bounds {960,100,1060,180}。
 */
public final class InterstitialGuardTest {

    public static void main(String[] args) {
        /* ════ S1 关闭词语义闸（25 项：17 正例 + 8 反例）════ */
        TestHost.suite("InterstitialGuard · S1 关闭词语义闸（精确等值——绝不 contains）");
        String[] closes = {
                "×", "✕", "✖", "╳", "⨯",                       // × 家族（真机插屏关闭钮常态）
                "关闭", "关闭广告", "关闭此广告",                 // 关闭族
                "跳过", "跳过广告", "跳过此广告", "跳過", "跳過廣告", "Skip", // 跳过族（后 12s 窗规则引擎不再扫——本路唯一出手位）
                "知道了", "我知道了", "close",                   // 知道了族（收起弹层）
        };
        for (String w : closes) {
            TestHost.check(InterstitialGuard.isInterstitialCloseWord(w), "关闭词：" + w);
        }
        String[] nonCloses = {
                "进入广告", "查看详情", "立即下载",               // 营销/进入方向——绝不代点
                "不感兴趣",                                     // feed P2 菜单项——归两段式，插屏路不收
                "X",                                            // 拉丁 X（正文/昵称误而险）
                "点击关闭广告查看更多",                            // >8 字必是正文
                "3s",
        };
        for (String w : nonCloses) {
            TestHost.check(!InterstitialGuard.isInterstitialCloseWord(w), "非关闭词：" + w);
        }
        TestHost.check(!InterstitialGuard.isInterstitialCloseWord(null), "非关闭词：null");

        /* ════ S2 词优先级（5 项）════ */
        TestHost.suite("InterstitialGuard · S2 词优先级（× 家族 > 关闭族 > 跳过族 > 知道了族）");
        TestHost.checkEq(0, InterstitialGuard.closeWordPriority("×"), "「×」优先级 0（最优）");
        TestHost.check(InterstitialGuard.closeWordPriority("关闭") > InterstitialGuard.closeWordPriority("×"),
                "关闭族优先级 > × 家族");
        TestHost.check(InterstitialGuard.closeWordPriority("跳过") > InterstitialGuard.closeWordPriority("关闭"),
                "跳过族优先级 > 关闭族");
        TestHost.check(InterstitialGuard.closeWordPriority("知道了") > InterstitialGuard.closeWordPriority("跳过"),
                "知道了族优先级 > 跳过族");
        TestHost.checkEq(-1, InterstitialGuard.closeWordPriority("进入广告"), "未知词优先级 -1");

        /* ════ S3 关闭钮形态闸（6 项）════ */
        TestHost.suite("InterstitialGuard · S3 关闭钮形态闸（≤30% 屏宽 × ≤12% 屏高）");
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{960, 100, 1060, 180}, 1080, 2400),
                "100×80 关闭钮过闸（真机 × 常态）");
        TestHost.check(!InterstitialGuard.closeFormOk(new int[]{100, 100, 500, 400}, 1080, 2400),
                "400×300 大按钮拒（广告主体形态）");
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{0, 0, 324, 288}, 1080, 2400),
                "324×288 边界恰好过闸");
        TestHost.check(!InterstitialGuard.closeFormOk(new int[]{0, 0, 325, 289}, 1080, 2400),
                "325×289 边界越一格拒");
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{0, 0, 300, 140}, 0, 0),
                "屏幕未知兜底 300×140 过（≤324×144）");
        TestHost.check(!InterstitialGuard.closeFormOk(new int[]{0, 0, 330, 150}, 0, 0),
                "屏幕未知兜底 330×150 拒");

        /* ════ S4 近旁/带位闸（6 项）════ */
        TestHost.suite("InterstitialGuard · S4 近旁/带位闸（|Δcy|≤35% 或 顶带/底带）");
        int[] badge = {60, 100, 190, 180};     // 角标（左上）
        int[] closeNear = {960, 100, 1060, 180}; // 同排关闭钮（右上——|Δcy|=0）
        TestHost.check(InterstitialGuard.nearBadge(closeNear, badge, 1080, 2400),
                "同排（|Δcy|=0）近旁过闸");
        TestHost.check(!InterstitialGuard.nearBadge(new int[]{960, 1400, 1060, 1480}, badge, 1080, 2400),
                "|Δcy|≈55% 屏高远距拒");
        TestHost.check(InterstitialGuard.inTopOrBottomBand(new int[]{960, 100, 1060, 180}, 1080, 2400),
                "顶带（cy≤30% 屏高）过闸");
        TestHost.check(!InterstitialGuard.inTopOrBottomBand(new int[]{960, 1000, 1060, 1080}, 1080, 2400),
                "中部（非带位）拒");
        TestHost.check(InterstitialGuard.inTopOrBottomBand(new int[]{960, 1800, 1060, 1880}, 1080, 2400),
                "底带（cy≥70% 屏高）过闸");
        TestHost.check(!InterstitialGuard.nearBadge(closeNear, null, 1080, 2400),
                "角标缺失近旁拒（角标是闸⓪本体）");

        /* ════ S5 决策真值表（12 项）════ */
        TestHost.suite("InterstitialGuard · S5 决策真值表（闸⓪~闸④）");
        java.util.ArrayList<AdDecisionCore.NodeView> b1 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c1 = new java.util.ArrayList<>();
        b1.add(new SimNode("广告", 60, 100, 190, 180));
        c1.add(new SimNode("×", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision hit = InterstitialGuard.decideFromCandidates(b1, c1, 1080, 2400, false);
        TestHost.check(hit != null && InterstitialGuard.ACTION_CLOSE.equals(hit.action)
                        && "×".equals(hit.label) && hit.tapBounds[0] == 960 && hit.tapBounds[2] == 1060,
                "角标+同排× → 点×（bounds=关闭钮自身）");
        TestHost.check(InterstitialGuard.decideFromCandidates(new java.util.ArrayList<>(), c1, 1080, 2400, false) == null,
                "闸⓪：角标不在场 → 绝不出手（宁漏勿误）");
        TestHost.check(InterstitialGuard.decideFromCandidates(b1, c1, 1080, 2400, true) == null,
                "闸④：可滚动窗（信息流/正文页）→ 两段式/开屏阶梯负责");
        java.util.ArrayList<AdDecisionCore.NodeView> c2 = new java.util.ArrayList<>();
        c2.add(new SimNode("×", 960, 1000, 1060, 1080)); // 中部远距（|Δcy|≈38%）
        TestHost.check(InterstitialGuard.decideFromCandidates(b1, c2, 1080, 2400, false) == null,
                "闸③：中部+远距（非带位非近旁）→ 拒");
        java.util.ArrayList<AdDecisionCore.NodeView> c3 = new java.util.ArrayList<>();
        c3.add(new SimNode("关闭", 960, 200, 1060, 280)); // 顶带远角标（角标左上 cy=140，|Δcy|=100 近）→ 直接近旁
        FeedAdGuard.FeedDecision hit3 = InterstitialGuard.decideFromCandidates(b1, c3, 1080, 2400, false);
        TestHost.check(hit3 != null && "关闭".equals(hit3.label), "顶带+近旁「关闭」→ 点关闭");
        java.util.ArrayList<AdDecisionCore.NodeView> c4 = new java.util.ArrayList<>();
        c4.add(new SimNode("关闭", 960, 1800, 1060, 1880)); // 底带（cy=1840 ≥70%）
        FeedAdGuard.FeedDecision hit4 = InterstitialGuard.decideFromCandidates(b1, c4, 1080, 2400, false);
        TestHost.check(hit4 != null && "关闭".equals(hit4.label), "底带「关闭」→ 点关闭（插屏 × 常在底带）");
        java.util.ArrayList<AdDecisionCore.NodeView> c5 = new java.util.ArrayList<>();
        c5.add(new SimNode("×", 960, 100, 1460, 180)); // 宽 500 >30% 屏宽
        TestHost.check(InterstitialGuard.decideFromCandidates(b1, c5, 1080, 2400, false) == null,
                "闸②：500px 宽关闭钮拒（广告主体形态）");
        java.util.ArrayList<AdDecisionCore.NodeView> c6 = new java.util.ArrayList<>();
        c6.add(new SimNode("×", 960, 100, 1060, 180));
        c6.add(new SimNode("知道了", 100, 2200, 400, 2280)); // 底带
        FeedAdGuard.FeedDecision hit6 = InterstitialGuard.decideFromCandidates(b1, c6, 1080, 2400, false);
        TestHost.check(hit6 != null && "×".equals(hit6.label), "× 与 知道了 同场 → × 优先（数组序）");
        java.util.ArrayList<AdDecisionCore.NodeView> b2 = new java.util.ArrayList<>();
        b2.add(new SimNode("广告×", 60, 100, 190, 180)); // 角标词+纯符号尾
        FeedAdGuard.FeedDecision hit7 = InterstitialGuard.decideFromCandidates(b2, c1, 1080, 2400, false);
        TestHost.check(hit7 != null && "×".equals(hit7.label), "「广告×」符号尾角标 → 照样在场过闸⓪");
        java.util.ArrayList<AdDecisionCore.NodeView> b3 = new java.util.ArrayList<>();
        SimNode invBadge = new SimNode("广告", 60, 100, 190, 180);
        invBadge.visible = false;
        b3.add(invBadge);
        TestHost.check(InterstitialGuard.decideFromCandidates(b3, c1, 1080, 2400, false) == null,
                "不可见角标 → 拒（无障碍树里也要看得见才算在场）");
        java.util.ArrayList<AdDecisionCore.NodeView> b4 = new java.util.ArrayList<>();
        b4.add(new SimNode("来自广告推荐", 60, 100, 190, 180)); // feed 上下文词非角标（6 字 contains 族）
        TestHost.check(InterstitialGuard.decideFromCandidates(b4, c1, 1080, 2400, false) == null,
                "「来自广告推荐」是上下文词非角标 → 闸⓪拒（P1X 领地不越界）");
        java.util.ArrayList<AdDecisionCore.NodeView> c7 = new java.util.ArrayList<>();
        c7.add(new SimNode("立即下载", 400, 1100, 700, 1180)); // 只有 CTA
        TestHost.check(InterstitialGuard.decideFromCandidates(b1, c7, 1080, 2400, false) == null,
                "只有 CTA（立即下载）没有关闭钮 → 绝不点广告主体");

        /* ════ S6 常量在场（7 项）════ */
        TestHost.suite("InterstitialGuard · S6 常量在场（契约同步）");
        TestHost.checkEq(2500L, InterstitialGuard.INTER_SCAN_THROTTLE_MS, "插屏扫描节流 2.5s");
        TestHost.checkEq(0.30, InterstitialGuard.CLOSE_W_MAX, "关闭钮宽上限 30% 屏宽");
        TestHost.checkEq(0.12, InterstitialGuard.CLOSE_H_MAX, "关闭钮高上限 12% 屏高");
        TestHost.checkEq(0.35, InterstitialGuard.INTER_NEAR_Y_MAX, "近旁 |Δcy| ≤35% 屏高");
        TestHost.checkEq(0.30, InterstitialGuard.CLOSE_TOP_BAND, "顶带 30% 屏高");
        TestHost.checkEq(0.70, InterstitialGuard.CLOSE_BOTTOM_BAND, "底带 70% 屏高");
        TestHost.checkEq("inter_close", InterstitialGuard.ACTION_CLOSE, "动作名 inter_close（通用终闸路由）");

        /* ════ S7 九品牌分辨率 1:1 缩放（18 项 = 9 品牌 × 2 路）════ */
        TestHost.suite("InterstitialGuard · S7 九品牌分辨率 1:1 缩放（角标/关闭钮几何按机型缩放）");
        int[][] brandGeoms = {
                {1080, 2400}, // OPPO/一加（ColorOS 常见 1080×2400）
                {1260, 2844}, // vivo/iQOO（OriginOS 旗舰 1260×2844）
                {1080, 2400}, // 小米/红米/POCO（HyperOS 主流）
                {1440, 3200}, // 三星（OneUI QHD 旗舰）
                {1080, 2340}, // 华为（HarmonyOS 20:9 挖孔）
                {1220, 2712}, // 荣耀（MagicOS ~460ppi）
                {720, 1650},  // 红米入门（720p 遗留机）
                {1080, 2412}, // 一加窄边（1080×2412）
                {1080, 2316}, // iQOO 折叠外屏形态（21.3:9）
        };
        String[] brandNames = {"OPPO", "vivo", "小米", "三星", "华为", "荣耀", "红米", "一加", "iQOO"};
        for (int i = 0; i < brandGeoms.length; i++) {
            int w = brandGeoms[i][0], h = brandGeoms[i][1];
            java.util.ArrayList<AdDecisionCore.NodeView> bb = new java.util.ArrayList<>();
            java.util.ArrayList<AdDecisionCore.NodeView> cc = new java.util.ArrayList<>();
            bb.add(new SimNode("广告", (int) (w * 0.05), (int) (h * 0.04), (int) (w * 0.05) + 130, (int) (h * 0.04) + 80));
            cc.add(new SimNode("×", (int) (w * 0.89), (int) (h * 0.04), (int) (w * 0.89) + 100, (int) (h * 0.04) + 80));
            FeedAdGuard.FeedDecision bi = InterstitialGuard.decideFromCandidates(bb, cc, w, h, false);
            TestHost.check(bi != null && "×".equals(bi.label),
                    brandNames[i] + " " + w + "×" + h + "：角标+× 按分辨率 1:1 缩放 → 点×");
            TestHost.check(InterstitialGuard.decideFromCandidates(new java.util.ArrayList<>(), cc, w, h, false) == null,
                    brandNames[i] + " " + w + "×" + h + "：无角标反例零出手（宁漏勿误）");
        }

        /* ════ S8 真机插屏文案重建（4 项）════ */
        TestHost.suite("InterstitialGuard · S8 真机插屏文案重建");
        // ① 新闻 App 文章间插屏：角标「广告」+ × 右上 + CTA「立即下载」居中
        java.util.ArrayList<AdDecisionCore.NodeView> nb = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> nc = new java.util.ArrayList<>();
        nb.add(new SimNode("广告", 60, 100, 190, 180));
        nc.add(new SimNode("×", 960, 100, 1060, 180));
        nc.add(new SimNode("立即下载", 300, 1100, 780, 1240));
        FeedAdGuard.FeedDecision n1 = InterstitialGuard.decideFromCandidates(nb, nc, 1080, 2400, false);
        TestHost.check(n1 != null && "×".equals(n1.label) && n1.tapBounds[1] == 100,
                "新闻 App 插屏：点右上×（绝不点「立即下载」CTA 主体）");
        // ② 游戏过关插屏：角标「广告」+「跳过」右上 +「领取奖励」CTA
        java.util.ArrayList<AdDecisionCore.NodeView> gb = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> gc = new java.util.ArrayList<>();
        gb.add(new SimNode("广告", 60, 100, 190, 180));
        gc.add(new SimNode("跳过", 900, 100, 1060, 180));
        gc.add(new SimNode("领取奖励", 300, 1100, 780, 1240));
        FeedAdGuard.FeedDecision g1 = InterstitialGuard.decideFromCandidates(gb, gc, 1080, 2400, false);
        TestHost.check(g1 != null && "跳过".equals(g1.label),
                "游戏过关插屏：点「跳过」（关闭族语义内的跳过钮）");
        // ③ 工具 App 半屏插卡：角标「推广」+「关闭」底带
        java.util.ArrayList<AdDecisionCore.NodeView> tb = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> tc = new java.util.ArrayList<>();
        tb.add(new SimNode("推广", 60, 1800, 190, 1880));
        tc.add(new SimNode("关闭", 960, 1800, 1060, 1880));
        FeedAdGuard.FeedDecision t1 = InterstitialGuard.decideFromCandidates(tb, tc, 1080, 2400, false);
        TestHost.check(t1 != null && "关闭".equals(t1.label),
                "工具 App 半屏插卡：底带「推广」+「关闭」同排 → 点关闭");
        // ④ 相册反例（PhotoView 无角标只有 ×）→ 零出手
        java.util.ArrayList<AdDecisionCore.NodeView> pb = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> pc = new java.util.ArrayList<>();
        pc.add(new SimNode("×", 960, 100, 1060, 180));
        TestHost.check(InterstitialGuard.decideFromCandidates(pb, pc, 1080, 2400, false) == null,
                "相册反例：无「广告」角标在场 → 零出手（宁漏勿误红线）");

        TestHost.done();
    }
}
