package com.zai.adshield.test;

import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.HumanTap;
import com.zai.adshield.core.TapPoint;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * FeedAdGuard 单元测试（v6.2.0 · 核心新增能力：App 内信息流广告识别跳过——
 * 微信朋友圈就业广告等）。与 MiniProgramGuard 同规约（纯 Java 零 Android
 * 依赖，ECJ -1.8 编译 JVM 运行）。
 * ─────────────────────────────────────────────────────────────
 * 恰好 81 项断言（check/checkEq 总调用数）：
 *  S1 名单门（20）/ S2 微信窗级门（6）/ S3 P1 角标语义闸①（12）/
 *  S4 P1 面积闸②（8）/ S5 P2 中性项闸③（10）/ S6 两段式决策（12）/
 *  S7 常量在场（7）/ S8 预算闸与 1:1 人手角标触点（6）。
 * 标准屏 1080×2400；角标 bounds 示例 {990,300,1120,380}（宽 130 高 80
 * ——满足 ≤35% 屏宽 × ≤8% 屏高）。
 */
public final class FeedAdGuardTest {

    public static void main(String[] args) {
        int[] badge = new int[]{990, 300, 1120, 380}; // 宽 130 高 80（角标形态示例）

        /* ════ S1 名单门（20 项）════ */
        TestHost.suite("FeedAdGuard · S1 名单门（18 款信息流 App）");
        String[] apps = new String[]{
                "com.tencent.mm", "com.sina.weibo", "com.ss.android.article.news",
                "com.ss.android.ugc.aweme", "com.smile.gifmaker", "com.xingin.xhs",
                "tv.danmaku.bili", "com.zhihu.android", "com.baidu.searchbox",
                "com.UCMobile", "com.tencent.mtt", "com.tencent.news",
                "com.netease.newsreader", "com.sina.news", "com.quark.browser",
                "com.snda.wifilocating", "com.hupu.games", "com.cubic.autohome",
        };
        String[] names = new String[]{
                "微信", "微博", "今日头条", "抖音", "快手", "小红书", "哔哩哔哩", "知乎",
                "百度", "UC 浏览器", "QQ 浏览器", "腾讯新闻", "网易新闻", "新浪新闻",
                "夸克浏览器", "WiFi 万能钥匙", "虎扑", "汽车之家",
        };
        for (int i = 0; i < apps.length; i++) {
            TestHost.check(FeedAdGuard.isFeedApp(apps[i]),
                    "名单内：" + names[i] + "（" + apps[i] + "）");
        }
        TestHost.check(!FeedAdGuard.isFeedApp("com.unknown.app"),
                "名单外 App 零 feed 扫描（功耗红线：开屏/弹窗路径已覆盖）");
        TestHost.check(!FeedAdGuard.isFeedApp(null), "null 包安全");

        /* ════ S2 微信窗级门（6 项）════ */
        TestHost.suite("FeedAdGuard · S2 微信窗级门（只认 sns 族窗）");
        TestHost.check(FeedAdGuard.isFeedWindow("com.tencent.mm",
                "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI"),
                "微信朋友圈 SnsTimeLineUI（sns 族窗 → 扫）");
        TestHost.check(FeedAdGuard.isFeedWindow("com.tencent.mm",
                "com.tencent.mm.plugin.sns.ui.SnsEditText"),
                "微信 SnsEditText（sns 族窗 → 扫）");
        TestHost.check(!FeedAdGuard.isFeedWindow("com.tencent.mm",
                "com.tencent.mm.ui.LauncherUI"),
                "微信聊天主界面零扫描红线（LauncherUI 不扫）");
        TestHost.check(!FeedAdGuard.isFeedWindow("com.tencent.mm",
                "com.tencent.mm.plugin.appbrand.ui.AppBrandUI"),
                "微信小程序容器不扫（AppBrandUI 归 MiniProgramGuard 保护）");
        TestHost.check(FeedAdGuard.isFeedWindow("com.sina.weibo",
                "com.sina.weibo.MainActivity"),
                "微博任意窗（名单内非微信 → 扫，语义+形态门兜底）");
        TestHost.check(!FeedAdGuard.isFeedWindow("com.unknown.app", "x"),
                "名单外 App 任意窗不扫");

        /* ════ S3 P1 角标语义闸①（12 项）════ */
        TestHost.suite("FeedAdGuard · S3 P1 披露角标语义闸（精确等值——绝不 contains）");
        TestHost.check(FeedAdGuard.isDisclosureBadge("广告", badge, 1080, 2400), "角标词「广告」精确等值");
        TestHost.check(FeedAdGuard.isDisclosureBadge("就业", badge, 1080, 2400), "角标词「就业」精确等值");
        TestHost.check(FeedAdGuard.isDisclosureBadge("求职", badge, 1080, 2400), "角标词「求职」精确等值");
        TestHost.check(FeedAdGuard.isDisclosureBadge("招聘", badge, 1080, 2400), "角标词「招聘」精确等值");
        TestHost.check(FeedAdGuard.isDisclosureBadge("职位", badge, 1080, 2400), "角标词「职位」精确等值");
        TestHost.check(FeedAdGuard.isDisclosureBadge("推广", badge, 1080, 2400), "角标词「推广」精确等值");
        TestHost.check(FeedAdGuard.isDisclosureBadge(" 广告 ", badge, 1080, 2400),
                "前后空格 trim 后等值（「 广告 」→「广告」）");
        TestHost.check(FeedAdGuard.isDisclosureBadge("广告×", badge, 1080, 2400),
                "纯符号尾变体「广告×」单独收编");
        TestHost.check(FeedAdGuard.isDisclosureBadge("就业›", badge, 1080, 2400),
                "纯符号尾变体「就业›」单独收编");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("招聘广告文案内容超六字", badge, 1080, 2400),
                ">6 字必是正文（长度闸）");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("广告位出租", badge, 1080, 2400),
                "词后接汉字不是纯符号尾（「广告位」不过）");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("高薪招聘急招", badge, 1080, 2400),
                "contains 绝不中（「高薪招聘急招」含「招聘」但非角标）");

        /* ════ S4 P1 面积闸②（8 项）════ */
        TestHost.suite("FeedAdGuard · S4 P1 角标面积闸（宽 ≤35% 屏 × 高 ≤8% 屏）");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("广告", new int[]{0, 0, 400, 80}, 1080, 2400),
                "宽 37% 屏拒（400px > 378px 上限）");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("广告", new int[]{0, 0, 130, 240}, 1080, 2400),
                "高 10% 屏拒（240px > 192px 上限）");
        TestHost.check(FeedAdGuard.isDisclosureBadge("广告", new int[]{0, 0, 367, 168}, 1080, 2400),
                "宽 34%×高 7% 贴边通过（367×168 ≤ 378×192）");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("广告", new int[]{0, 0, 972, 960}, 1080, 2400),
                "广告主体大图（宽 90%×高 40%）绝不过闸");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("广告", new int[]{0, 0, 500, 80}, 0, 0),
                "屏幕未知（W=0/H=0）：宽 500px > 420px 兜底拒");
        TestHost.check(FeedAdGuard.isDisclosureBadge("广告", new int[]{0, 0, 400, 120}, 0, 0),
                "屏幕未知：400×120 ≤ 420×126 兜底通过");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("广告", null, 1080, 2400),
                "null bounds 拒");
        TestHost.check(!FeedAdGuard.isDisclosureBadge("广告", new int[]{100, 100, 100, 180}, 1080, 2400),
                "宽 0 空矩形拒");

        /* ════ S5 P2 中性项闸③（10 项）════ */
        TestHost.suite("FeedAdGuard · S5 P2 中性关闭项闸（精确等值；扩展动作绝不代点）");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("关闭该广告"), "中性项「关闭该广告」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("关闭此广告"), "中性项「关闭此广告」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("屏蔽此广告"), "中性项「屏蔽此广告」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("关闭广告"), "中性项「关闭广告」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("不感兴趣"), "中性项「不感兴趣」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem(" 不感兴趣 "), "中性项 trim 后等值（「 不感兴趣 」）");
        TestHost.check(!FeedAdGuard.isNeutralCloseItem("屏蔽作者"), "扩展动作「屏蔽作者」绝不代点（越权）");
        TestHost.check(!FeedAdGuard.isNeutralCloseItem("举报"), "扩展动作「举报」绝不代点");
        TestHost.check(!FeedAdGuard.isNeutralCloseItem("反馈"), "扩展动作「反馈」绝不代点");
        TestHost.check(FeedAdGuard.isExtendedActionItem("屏蔽作者"), "isExtendedActionItem 在场（屏蔽作者属扩展表）");

        /* ════ S6 两段式决策（12 项）════ */
        TestHost.suite("FeedAdGuard · S6 两段式决策（P1 点角标 → P2 收菜单）");
        // 朋友圈广告树：root → 广告主体大图（空 text 全宽）→ 其内披露角标「就业」
        SimNode moments = new SimNode();
        moments.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode adCard = new SimNode("", 0, 800, 1080, 1600); // 广告主体大图节点
        int[] badgeInCard = new int[]{990, 820, 1120, 900};
        SimNode badgeNode = new SimNode("就业", 990, 820, 1120, 900);
        adCard.add(badgeNode);
        moments.add(adCard);
        FeedAdGuard.FeedDecision p1 = FeedAdGuard.decideP1(moments, 1080, 2400);
        TestHost.check(p1 != null && FeedAdGuard.ACTION_P1_BADGE.equals(p1.action)
                        && "就业".equals(p1.label),
                "P1 朋友圈广告树：非 null · action=feed_p1_badge · label=「就业」");
        TestHost.check(p1 != null && p1.tapBounds != null && p1.tapBounds.length == 4
                        && p1.tapBounds[0] == badgeInCard[0] && p1.tapBounds[1] == badgeInCard[1]
                        && p1.tapBounds[2] == badgeInCard[2] && p1.tapBounds[3] == badgeInCard[3],
                "P1 tapBounds 逐元素==角标【自身】bounds（绝不广告主体 0,800,1080,1600）");
        // 正常朋友圈内容树：零命中（宁漏勿误）
        SimNode normalFeed = new SimNode();
        normalFeed.boundsArr = new int[]{0, 0, 1080, 2400};
        normalFeed.add(new SimNode("今天天气真好出来走走", 0, 300, 1080, 380));
        normalFeed.add(new SimNode("周末和朋友聚餐", 0, 500, 1080, 580));
        TestHost.check(FeedAdGuard.decideP1(normalFeed, 1080, 2400) == null,
                "P1 正常朋友圈内容零命中（宁漏勿误）");
        TestHost.check(FeedAdGuard.decideP1(null, 1080, 2400) == null, "P1 null 树安全");
        // 关闭菜单树（两个紧凑行）：armed 才收
        SimNode menu = new SimNode();
        menu.boundsArr = new int[]{200, 700, 880, 1500};
        menu.add(new SimNode("关闭该广告", 220, 760, 580, 840));
        menu.add(new SimNode("不感兴趣", 220, 860, 580, 940));
        FeedAdGuard.FeedDecision p2 = FeedAdGuard.decideP2(menu, 1080, 2400, true);
        TestHost.check(p2 != null && FeedAdGuard.ACTION_P2_CLOSE.equals(p2.action)
                        && "关闭该广告".equals(p2.label),
                "P2 armed：非 null · action=feed_p2_close · label=「关闭该广告」（优先级最高项）");
        TestHost.check(FeedAdGuard.decideP2(menu, 1080, 2400, false) == null,
                "P2 非 armed 零对抗（用户自己长按的菜单绝不对抗）");
        SimNode menuWeakOnly = new SimNode();
        menuWeakOnly.boundsArr = new int[]{200, 700, 880, 1500};
        menuWeakOnly.add(new SimNode("不感兴趣", 220, 760, 580, 840));
        TestHost.check(FeedAdGuard.decideP2(menuWeakOnly, 1080, 2400, true) == null,
                "P2 双闸①：仅「不感兴趣」强特征 0 → 拒收");
        SimNode menuStrongOnly = new SimNode();
        menuStrongOnly.boundsArr = new int[]{200, 700, 880, 1500};
        menuStrongOnly.add(new SimNode("关闭该广告", 220, 760, 580, 840));
        TestHost.check(FeedAdGuard.decideP2(menuStrongOnly, 1080, 2400, true) == null,
                "P2 双闸②：仅「关闭该广告」总数 1 → 拒收");
        SimNode menu3 = new SimNode();
        menu3.boundsArr = new int[]{200, 700, 880, 1500};
        menu3.add(new SimNode("关闭该广告", 220, 760, 580, 840));
        menu3.add(new SimNode("不感兴趣", 220, 860, 580, 940));
        menu3.add(new SimNode("屏蔽作者", 220, 960, 580, 1040));
        FeedAdGuard.FeedDecision p3 = FeedAdGuard.decideP2(menu3, 1080, 2400, true);
        TestHost.check(p3 != null && "关闭该广告".equals(p3.label),
                "P2 三项菜单（含「屏蔽作者」）：label 仍「关闭该广告」（扩展动作绝不代点）");
        TestHost.check(FeedAdGuard.decideP2(null, 1080, 2400, true) == null, "P2 null 树安全");
        TestHost.check(FeedAdGuard.decideP1(moments, 1080, 2400) != null,
                "P1 出手后同角标仍可识别（复验/重试路径）");
        // 巨横幅菜单：形态闸拒全宽 CTA 行
        SimNode bannerMenu = new SimNode();
        bannerMenu.boundsArr = new int[]{0, 700, 1080, 1500};
        bannerMenu.add(new SimNode("关闭该广告", 0, 760, 1080, 840)); // 宽 100% 屏巨横幅
        bannerMenu.add(new SimNode("不感兴趣", 220, 860, 580, 940));
        TestHost.check(FeedAdGuard.decideP2(bannerMenu, 1080, 2400, true) == null,
                "P2 形态闸拒巨横幅（全宽「关闭该广告」绝不进菜单候选）");

        /* ════ S7 常量在场（7 项）════ */
        TestHost.suite("FeedAdGuard · S7 常量在场（安全闸参数）");
        TestHost.checkEq(1200L, FeedAdGuard.FEED_SCAN_THROTTLE_MS, "FEED_SCAN_THROTTLE_MS == 1200（1.2s 节流）");
        TestHost.checkEq(300, FeedAdGuard.FEED_NODE_BUDGET, "FEED_NODE_BUDGET == 300（节点预算双闸之二）");
        TestHost.checkEq(8000L, FeedAdGuard.P1_ARM_MS, "P1_ARM_MS == 8000（P2 指纹窗）");
        TestHost.check(Math.abs(FeedAdGuard.BADGE_W_MAX - 0.35) < 1e-9, "BADGE_W_MAX == 0.35（≤35% 屏宽）");
        TestHost.check(Math.abs(FeedAdGuard.BADGE_H_MAX - 0.08) < 1e-9, "BADGE_H_MAX == 0.08（≤8% 屏高）");
        TestHost.checkEq(6, FeedAdGuard.BADGE_MAX_CHARS, "BADGE_MAX_CHARS == 6（>6 字必是正文）");
        TestHost.check(FeedAdGuard.stats() != null && FeedAdGuard.stats().contains("18 款"),
                "stats() 名单 18 款在场（FEED_APPS 包私有，以 stats 断言）");

        /* ════ S8 预算闸与 1:1 人手角标触点（6 项）════ */
        TestHost.suite("FeedAdGuard · S8 预算闸与 1:1 人手角标触点");
        // ① 600+ 节点深链、角标在 ≈第 400 个节点处 → 300 预算闸拦下（功耗红线）
        SimNode deepLate = deepFeedTree(new SimNode("就业", 990, 820, 1120, 900), 19);
        TestHost.check(FeedAdGuard.decideP1(deepLate, 1080, 2400) == null,
                "深树 600+ 节点·角标在 ≈400 节点处：300 节点预算守住功耗闸（不可达即不扫）");
        // ② 同构深树、角标在前 50 节点内 → 预算内可发现
        SimNode deepEarly = deepFeedTree(new SimNode("就业", 990, 820, 1120, 900), 1);
        TestHost.check(FeedAdGuard.decideP1(deepEarly, 1080, 2400) != null,
                "同一深树·角标在前 50 节点内：预算内可发现（可达角标不误伤）");
        // ③ 不可见角标不出手
        SimNode invRoot = new SimNode();
        invRoot.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode invBadge = new SimNode("就业", 990, 820, 1120, 900);
        invBadge.visible = false;
        invRoot.add(invBadge);
        TestHost.check(FeedAdGuard.decideP1(invRoot, 1080, 2400) == null,
                "不可见角标不出手（visible=false 拒）");
        // ④⑤ TapPoint 密度自适应落点（2.0 / 3.0 各一）：域内 + 距边余量
        int[] bRect = new int[]{990, 820, 1120, 900};
        int[] pt2 = TapPoint.pickDp(bRect, 0, 2.0f);
        TestHost.check(pt2 != null && pt2[0] > 990 && pt2[0] < 1120 && pt2[1] > 820 && pt2[1] < 900
                        && pt2[0] - 990 >= TapPoint.EDGE_INSET && 1120 - pt2[0] >= TapPoint.EDGE_INSET
                        && pt2[1] - 820 >= TapPoint.EDGE_INSET && 900 - pt2[1] >= TapPoint.EDGE_INSET,
                "TapPoint.pickDp 密度 2.0：落点在角标可见域内且距边有余量");
        int[] pt3 = TapPoint.pickDp(bRect, 0, 3.0f);
        TestHost.check(pt3 != null && pt3[0] > 990 && pt3[0] < 1120 && pt3[1] > 820 && pt3[1] < 900
                        && pt3[0] - 990 >= TapPoint.EDGE_INSET && 1120 - pt3[0] >= TapPoint.EDGE_INSET
                        && pt3[1] - 820 >= TapPoint.EDGE_INSET && 900 - pt3[1] >= TapPoint.EDGE_INSET,
                "TapPoint.pickDp 密度 3.0：落点在角标可见域内且距边有余量");
        // ⑥ HumanTap.tapSpec ±2px 微抖不出角标面（首击/重试两域 × 6 种子）
        int bcx = (990 + 1120) / 2, bcy = (820 + 900) / 2; // 角标中心（1055,860）
        boolean jitterOk = true;
        for (long seed = 1; seed <= 6; seed++) {
            int[] spec = HumanTap.tapSpec(seed, (int) (seed % 2));
            if (spec == null || spec.length < 4) {
                jitterOk = false;
                break;
            }
            if (Math.abs(spec[2]) > HumanTap.JITTER_ABS || Math.abs(spec[3]) > HumanTap.JITTER_ABS) {
                jitterOk = false;
            }
            if (bcx + spec[2] < 990 || bcx + spec[2] > 1120
                    || bcy + spec[3] < 820 || bcy + spec[3] > 900) {
                jitterOk = false;
            }
            if (spec[0] < HumanTap.DELAY_MIN || spec[0] > HumanTap.DELAY_MIN + HumanTap.DELAY_SPAN - 1) {
                jitterOk = false;
            }
            if (spec[1] < HumanTap.DUR_FIRST_MIN
                    || spec[1] > HumanTap.DUR_RETRY_MIN + HumanTap.DUR_RETRY_SPAN - 1) {
                jitterOk = false;
            }
        }
        TestHost.check(jitterOk && (1120 - 990) / 2 >= 30 && (900 - 820) / 2 >= 30,
                "HumanTap.tapSpec ±2px 微抖不出角标面（人手参数域 6 种子·角标半宽 65 ≥ 30px）");

        TestHost.done();
    }

    /**
     * 600+ 节点深链树：root → 30 层 carrier（各挂 20 个内容行填充，总节点 632）。
     * badgeCarrierIdx 指定层把角标挂为该层首子（DFS 序 = 第 2+21×idx+1 个节点）；
     * idx=19 → 角标在 ≈第 400 个节点处（300 预算外）；idx=1 → 前 50 节点内。
     */
    private static SimNode deepFeedTree(SimNode badgeNode, int badgeCarrierIdx) {
        SimNode root = new SimNode();
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode cur = root;
        for (int i = 0; i < 30; i++) {
            SimNode carrier = new SimNode("", 0, 0, 1080, 2400);
            cur.add(carrier);
            if (i == badgeCarrierIdx && badgeNode != null) carrier.add(badgeNode);
            for (int j = 0; j < 20; j++) {
                carrier.add(new SimNode("内容行" + i + "-" + j, 0, 0, 200, 60));
            }
            cur = carrier;
        }
        return root;
    }
}
