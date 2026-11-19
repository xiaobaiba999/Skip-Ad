package com.zai.adshield.test;

import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.HumanTap;
import com.zai.adshield.core.TapPoint;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * FeedAdGuard 单元测试（v6.2.0 首发 · v7.2.0 十二轮实锤病灶三根治后回归扩容：
 *  ① armed 直通（P1 拉起的菜单是 Dialog 新窗，窗类被覆盖——P2 只认 armed）
 *  ② 候选式快搜（李跳跳方法论：原生 findByText 直查，预算无关）
 *  ③ P2 armed≥1 即出手（真机微信菜单常只露 1 个中性项）。
 * v7.4.0 十三轮真机实锤（7 张截图 + 引擎日志 #79/#80/#81）三路根治扩容：
 *  ④ 「直接关闭」置顶收编（微信「关闭该广告的原因」面板主按钮真机在树——
 *     日志 #79 20:15:23 候选样本直接实锤）；
 *  ⑤ P0 面板直通（面板特征在场即点主按钮——P1 角标识别失败也收得掉）；
 *  ⑥ P1X 卡片直关（market/ZOL「来自广告推荐」卡片角「×/关闭」直关钮 +
 *     广告上下文闸 + 同行近距闸——日志 #80 20:16:55 用户手动点「关闭」实锤）；
 *  ⑦ 名单 18→20 款（+ZOL 中关村在线 +小米应用商店——两轮真机实锤）。
 * 与 MiniProgramGuard 同规约（纯 Java 零 Android 依赖，ECJ -1.8 编译 JVM 运行）。
 * ─────────────────────────────────────────────────────────────
 * 恰好 131 项断言（check/checkEq 总调用数）：
 *  S1 名单门（22）/ S2 微信窗级门（6）/ S3 P1 角标语义闸①（12）/
 *  S4 P1 面积闸②（8）/ S5 P2 中性项闸③（15）/ S6 两段式决策（12）/
 *  S7 常量在场（11）/ S8 预算闸与 1:1 人手角标触点（6）/
 *  S9 v7.2.0 候选式快搜（6）/
 *  S10 v7.4.0 P0 面板直通真值表（10）/ S11 v7.4.0 P1X 卡片直关真值表（11）/
 *  S12 v7.4.0 真机截图文案重建（截图1/2/3/6 逐字树）（12）。
 * 标准屏 1080×2400；角标 bounds 示例 {990,300,1120,380}（宽 130 高 80
 * ——满足 ≤35% 屏宽 × ≤8% 屏高）。
 */
public final class FeedAdGuardTest {

    public static void main(String[] args) {
        int[] badge = new int[]{990, 300, 1120, 380}; // 宽 130 高 80（角标形态示例）

        /* ════ S1 名单门（20 项）════ */
        TestHost.suite("FeedAdGuard · S1 名单门（v7.4.0 20 款信息流 App）");
        String[] apps = new String[]{
                "com.tencent.mm", "com.sina.weibo", "com.ss.android.article.news",
                "com.ss.android.ugc.aweme", "com.smile.gifmaker", "com.xingin.xhs",
                "tv.danmaku.bili", "com.zhihu.android", "com.baidu.searchbox",
                "com.UCMobile", "com.tencent.mtt", "com.tencent.news",
                "com.netease.newsreader", "com.sina.news", "com.quark.browser",
                "com.snda.wifilocating", "com.hupu.games", "com.cubic.autohome",
                "com.zol.android", "com.xiaomi.market",
        };
        String[] names = new String[]{
                "微信", "微博", "今日头条", "抖音", "快手", "小红书", "哔哩哔哩", "知乎",
                "百度", "UC 浏览器", "QQ 浏览器", "腾讯新闻", "网易新闻", "新浪新闻",
                "夸克浏览器", "WiFi 万能钥匙", "虎扑", "汽车之家",
                "v7.4.0 中关村在线（截图2/3 实锤）", "v7.4.0 小米应用商店（日志#80 实锤）",
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

        /* ════ S5 P2 中性项闸③（15 项 · v7.4.0 直接关闭置顶）════ */
        TestHost.suite("FeedAdGuard · S5 P2 中性关闭项闸（精确等值；扩展动作绝不代点）");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("关闭该广告"), "中性项「关闭该广告」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("关闭此广告"), "中性项「关闭此广告」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("屏蔽此广告"), "中性项「屏蔽此广告」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("屏蔽此推广"), "v7.2.0 中性项「屏蔽此推广」（旧版朋友圈真机文案）");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("减少此类广告"), "v7.2.0 中性项「减少此类广告」（微信菜单变体）");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("关闭广告"), "中性项「关闭广告」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("不感兴趣"), "中性项「不感兴趣」");
        TestHost.check(FeedAdGuard.isNeutralCloseItem(" 不感兴趣 "), "中性项 trim 后等值（「 不感兴趣 」）");
        TestHost.check(!FeedAdGuard.isNeutralCloseItem("屏蔽作者"), "扩展动作「屏蔽作者」绝不代点（越权）");
        TestHost.check(!FeedAdGuard.isNeutralCloseItem("举报"), "扩展动作「举报」绝不代点");
        TestHost.check(!FeedAdGuard.isNeutralCloseItem("反馈"), "扩展动作「反馈」绝不代点");
        TestHost.check(FeedAdGuard.isExtendedActionItem("屏蔽作者"), "isExtendedActionItem 在场（屏蔽作者属扩展表）");
        TestHost.check(FeedAdGuard.isNeutralCloseItem("直接关闭"),
                "v7.4.0 中性项「直接关闭」（微信「关闭该广告的原因」面板主按钮真机文案）");
        TestHost.check(FeedAdGuard.isNeutralCloseItem(" 直接关闭 "),
                "v7.4.0 中性项「直接关闭」trim 后等值");
        TestHost.checkEq("直接关闭", FeedAdGuard.neutralCloseWords()[0],
                "v7.4.0 「直接关闭」优先级置顶（数组序即优先级——真机面板主按钮最先收）");

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
        FeedAdGuard.FeedDecision p2Weak = FeedAdGuard.decideP2(menuWeakOnly, 1080, 2400, true);
        TestHost.check(p2Weak != null && FeedAdGuard.ACTION_P2_CLOSE.equals(p2Weak.action)
                        && "不感兴趣".equals(p2Weak.label),
                "v7.2.0 病灶③根治：仅「不感兴趣」单项菜单 armed 即出手（真机微信菜单常态；旧双闸拒收=全都没拦截）");
        SimNode menuStrongOnly = new SimNode();
        menuStrongOnly.boundsArr = new int[]{200, 700, 880, 1500};
        menuStrongOnly.add(new SimNode("关闭该广告", 220, 760, 580, 840));
        FeedAdGuard.FeedDecision p2Strong = FeedAdGuard.decideP2(menuStrongOnly, 1080, 2400, true);
        TestHost.check(p2Strong != null && "关闭该广告".equals(p2Strong.label),
                "v7.2.0 病灶③根治：仅「关闭该广告」单项菜单 armed 即出手（旧双闸拒收）");
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
        // 巨横幅菜单：形态闸拒全宽 CTA 行（armed 下仅收紧凑中性项——CTA 绝不进候选）
        SimNode bannerMenu = new SimNode();
        bannerMenu.boundsArr = new int[]{0, 700, 1080, 1500};
        bannerMenu.add(new SimNode("关闭该广告", 0, 760, 1080, 840)); // 宽 100% 屏巨横幅
        bannerMenu.add(new SimNode("不感兴趣", 220, 860, 580, 940));
        FeedAdGuard.FeedDecision pBanner = FeedAdGuard.decideP2(bannerMenu, 1080, 2400, true);
        TestHost.check(pBanner == null || (!"关闭该广告".equals(pBanner.label)
                        && "不感兴趣".equals(pBanner.label) && pBanner.tapBounds[0] == 220
                        && pBanner.tapBounds[2] - pBanner.tapBounds[0] <= 378),
                "P2 形态闸拒巨横幅（全宽「关闭该广告」CTA 绝不进候选——armed 仅收紧凑中性项）");

        /* ════ S7 常量在场（11 项 · v7.4.0 巡检/P0/P1X 常量契约）════ */
        TestHost.suite("FeedAdGuard · S7 常量在场（安全闸参数）");
        TestHost.checkEq(1200L, FeedAdGuard.FEED_SCAN_THROTTLE_MS, "FEED_SCAN_THROTTLE_MS == 1200（1.2s 节流）");
        TestHost.checkEq(350L, FeedAdGuard.P2_SCAN_THROTTLE_MS, "v7.2.0 P2_SCAN_THROTTLE_MS == 350（armed 快节奏复查）");
        TestHost.checkEq(300, FeedAdGuard.FEED_NODE_BUDGET, "FEED_NODE_BUDGET == 300（DFS 兑底预算）");
        TestHost.checkEq(8000L, FeedAdGuard.P1_ARM_MS, "P1_ARM_MS == 8000（P2 指纹窗）");
        TestHost.check(Math.abs(FeedAdGuard.BADGE_W_MAX - 0.35) < 1e-9, "BADGE_W_MAX == 0.35（≤35% 屏宽）");
        TestHost.check(Math.abs(FeedAdGuard.BADGE_H_MAX - 0.08) < 1e-9, "BADGE_H_MAX == 0.08（≤8% 屏高）");
        TestHost.checkEq(6, FeedAdGuard.BADGE_MAX_CHARS, "BADGE_MAX_CHARS == 6（>6 字必是正文）");
        TestHost.check(FeedAdGuard.stats() != null && FeedAdGuard.stats().contains("20 款")
                        && FeedAdGuard.stats().contains("armed≥1即出手")
                        && FeedAdGuard.stats().contains("P0 面板直通") && FeedAdGuard.stats().contains("P1X 卡片直关"),
                "stats() 名单 20 款 + v7.4.0 P0/P1X 在场");
        TestHost.checkEq(2500L, FeedAdGuard.FEED_PATROL_MS,
                "v7.4.0 FEED_PATROL_MS == 2500（空闲巡检——事件静默期照扫）");
        TestHost.checkEq(90000L, FeedAdGuard.P0_FEED_CTX_MS,
                "v7.4.0 P0_FEED_CTX_MS == 90000（面板直通 90s 信息流上下文窗）");
        TestHost.check(Math.abs(FeedAdGuard.P1X_NEAR_Y_MAX - 0.30) < 1e-9,
                "v7.4.0 P1X_NEAR_Y_MAX == 0.30（同行近距门 ≤30% 屏高）");

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

        /* ════ S9 v7.2.0 候选式快搜（李跳跳方法论：原生直查主路径）════ */
        TestHost.suite("FeedAdGuard · S9 候选式快搜（decideP?FromCandidates）");
        // ① 候选列表含角标 + 噪声（正文/扩展动作/巨横幅）→ 只收角标
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> p1c =
                new java.util.ArrayList<>();
        p1c.add(new SimNode("高薪招聘急招月入过万", 0, 300, 1080, 380));  // contains 但非角标
        p1c.add(new SimNode("广告", 990, 300, 1120, 380));               // 真角标
        p1c.add(new SimNode("广告位出租", 990, 300, 1120, 380));          // 词+汉字非纯符号尾
        p1c.add(new SimNode("广告", 0, 0, 972, 960));                     // 巨大容器（形态闸拒）
        FeedAdGuard.FeedDecision p1cHit = FeedAdGuard.decideP1FromCandidates(p1c, 1080, 2400);
        TestHost.check(p1cHit != null && FeedAdGuard.ACTION_P1_BADGE.equals(p1cHit.action)
                        && "广告".equals(p1cHit.label) && p1cHit.tapBounds[0] == 990,
                "P1 候选式：噪声中只收真角标（语义+形态闸逐候选不变）");
        TestHost.check(FeedAdGuard.decideP1FromCandidates(null, 1080, 2400) == null,
                "P1 候选式 null 列表安全");
        // ② P2 候选式：armed 单项即出手；非 armed 零对抗
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> p2c =
                new java.util.ArrayList<>();
        p2c.add(new SimNode("不感兴趣", 220, 860, 580, 940));
        p2c.add(new SimNode("屏蔽作者", 220, 960, 580, 1040)); // 扩展动作绝不代点
        FeedAdGuard.FeedDecision p2cHit = FeedAdGuard.decideP2FromCandidates(p2c, 1080, 2400, true);
        TestHost.check(p2cHit != null && FeedAdGuard.ACTION_P2_CLOSE.equals(p2cHit.action)
                        && "不感兴趣".equals(p2cHit.label),
                "P2 候选式 armed：单项「不感兴趣」即出手（真机菜单常态）");
        TestHost.check(FeedAdGuard.decideP2FromCandidates(p2c, 1080, 2400, false) == null,
                "P2 候选式非 armed 零对抗（用户自己长拉的菜单绝不对抗）");
        // ③ 病灶②根治证明：深树 600+ 节点·角标在 ≈400 节点处——DFS 预算够不到，
        //    原生直查（模拟）候选能拿到 → 候选式决策非 null
        SimNode deepLate2 = deepFeedTree(new SimNode("就业", 990, 820, 1120, 900), 19);
        TestHost.check(FeedAdGuard.decideP1(deepLate2, 1080, 2400) == null,
                "对照组：DFS 300 预算仍够不到 ≈400 节点处角标（旧病灶在场的证明）");
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> deepCands =
                new java.util.ArrayList<>();
        deepCands.add(new SimNode("就业", 990, 820, 1120, 900)); // 原生直查命中即拿到（预算无关）
        FeedAdGuard.FeedDecision deepHit = FeedAdGuard.decideP1FromCandidates(deepCands, 1080, 2400);
        TestHost.check(deepHit != null && "就业".equals(deepHit.label),
                "病灶②根治：同深树角标经原生直查候选 → 命中（预算无关——深层卡片也能关）");

        /* ════ S10 v7.4.0 P0 面板直通真值表（9 项）════ */
        TestHost.suite("FeedAdGuard · S10 P0 面板直通（闸⑤：直接关闭 + 标题/原因族证据）");
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> p0a =
                new java.util.ArrayList<>();
        p0a.add(new SimNode("关闭该广告的原因", 200, 740, 700, 800));   // 面板标题（精确等值证据）
        p0a.add(new SimNode("直接关闭", 790, 1090, 1060, 1150));       // 主按钮（真机 #79 @79%,46% 形态）
        p0a.add(new SimNode("汽车厂商相关广告", 220, 1290, 700, 1350)); // 原因行（原因族 contains 证据）
        p0a.add(new SimNode("反馈后将优化广告推荐", 220, 810, 700, 860));
        FeedAdGuard.FeedDecision p0hit = FeedAdGuard.decideP0FromCandidates(p0a, 1080, 2400, false);
        TestHost.check(p0hit != null && FeedAdGuard.ACTION_P0_PANEL_CLOSE.equals(p0hit.action)
                        && "直接关闭".equals(p0hit.label) && p0hit.tapBounds[0] == 790,
                "P0 标题+主按钮+原因行全在场（非 armed）→ 点「直接关闭」收面板");
        // armed（我们 P1 拉起的面板）→ 主按钮单独即可
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> p0b =
                new java.util.ArrayList<>();
        p0b.add(new SimNode("直接关闭", 790, 1090, 1060, 1150));
        FeedAdGuard.FeedDecision p0armed = FeedAdGuard.decideP0FromCandidates(p0b, 1080, 2400, true);
        TestHost.check(p0armed != null && "直接关闭".equals(p0armed.label),
                "P0 armed：主按钮单独在场即出手（P1 拉起的面板）");
        // 非 armed 只有主按钮、无标题/原因族证据 → 拒（宁漏勿误）
        TestHost.check(FeedAdGuard.decideP0FromCandidates(p0b, 1080, 2400, false) == null,
                "P0 非 armed 裸「直接关闭」无面板证据 → 拒（普通页面的直接关闭绝不裸点）");
        // 原因族 contains 证据（无标题）也放行
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> p0c =
                new java.util.ArrayList<>();
        p0c.add(new SimNode("直接关闭", 790, 1090, 1060, 1150));
        p0c.add(new SimNode("重复收到相同素材", 220, 1350, 700, 1410));
        FeedAdGuard.FeedDecision p0reason = FeedAdGuard.decideP0FromCandidates(p0c, 1080, 2400, false);
        TestHost.check(p0reason != null && "直接关闭".equals(p0reason.label),
                "P0 原因族 contains 证据（相同素材）在场 → 放行（真机菜单标题缺场常态）");
        // 只有标题、无主按钮 → null（点了标题也没用）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> p0d =
                new java.util.ArrayList<>();
        p0d.add(new SimNode("关闭该广告的原因", 200, 740, 700, 800));
        TestHost.check(FeedAdGuard.decideP0FromCandidates(p0d, 1080, 2400, false) == null,
                "P0 只标题无主按钮 → null（标题不是可点目标）");
        // 巨横幅「直接关闭」CTA（100% 屏宽）→ 形态闸拒
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> p0e =
                new java.util.ArrayList<>();
        p0e.add(new SimNode("关闭该广告的原因", 200, 740, 700, 800));
        p0e.add(new SimNode("直接关闭", 0, 1090, 1080, 1150)); // 全宽巨横幅
        TestHost.check(FeedAdGuard.decideP0FromCandidates(p0e, 1080, 2400, true) == null,
                "P0 形态闸拒巨横幅「直接关闭」CTA（绝不进面板候选）");
        TestHost.check(FeedAdGuard.decideP0FromCandidates(null, 1080, 2400, true) == null,
                "P0 null 候选安全");
        TestHost.check(FeedAdGuard.isPanelTitleWord("关闭该广告的原因")
                        && !FeedAdGuard.isPanelTitleWord("关闭广告"),
                "isPanelTitleWord 精确等值（「关闭广告」短词不是面板标题）");
        TestHost.check(FeedAdGuard.isCloseIconWord("×") && FeedAdGuard.isCloseIconWord("关闭")
                        && !FeedAdGuard.isCloseIconWord("X") && !FeedAdGuard.isCloseIconWord("关闭广告"),
                "isCloseIconWord 精确等值（拉丁 X 不收 · 「关闭广告」非直关词）");
        TestHost.check(FeedAdGuard.P0_SEARCH_WORDS.length >= 6
                        && FeedAdGuard.P0_SEARCH_WORDS[0].equals("直接关闭"),
                "P0_SEARCH_WORDS 直查词表在场（直接关闭置首）");

        /* ════ S11 v7.4.0 P1X 卡片直关真值表（12 项）════ */
        TestHost.suite("FeedAdGuard · S11 P1X 卡片直关（闸⑥：×/关闭 + 广告上下文 + 同行近距）");
        // ZOL/market 真机卡片形态（截图2）：卡片头「今日推荐/来自广告推荐」+ 右上角 ×
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> zolClose =
                new java.util.ArrayList<>();
        zolClose.add(new SimNode("×", 1000, 700, 1060, 760));          // 卡片右上角直关钮
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> zolCtx =
                new java.util.ArrayList<>();
        zolCtx.add(new SimNode("来自广告推荐", 60, 700, 300, 760));    // 卡片头披露（同行同排）
        FeedAdGuard.FeedDecision zolHit = FeedAdGuard.decideP1XFromCandidates(zolClose, zolCtx, 1080, 2400);
        TestHost.check(zolHit != null && FeedAdGuard.ACTION_P1X_CLOSE.equals(zolHit.action)
                        && "×".equals(zolHit.label) && zolHit.tapBounds[0] == 1000,
                "P1X ZOL 卡片：× 直关钮 + 来自广告推荐上下文（同行）→ 直点即关");
        // 「关闭」文案钮（日志 #80 20:16:55 真机实锤形态）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> mkClose =
                new java.util.ArrayList<>();
        mkClose.add(new SimNode("关闭", 950, 680, 1060, 760));
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> mkCtx =
                new java.util.ArrayList<>();
        mkCtx.add(new SimNode("广告推荐", 60, 700, 300, 760));
        FeedAdGuard.FeedDecision mkHit = FeedAdGuard.decideP1XFromCandidates(mkClose, mkCtx, 1080, 2400);
        TestHost.check(mkHit != null && "关闭".equals(mkHit.label),
                "P1X market 卡片：「关闭」钮 + 广告推荐上下文 → 直点即关（#80 实锤场景）");
        // 无广告上下文 → 绝不裸点（#80 20:16:46 PhotoViewV16 相册关闭钮反例）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> pvClose =
                new java.util.ArrayList<>();
        pvClose.add(new SimNode("关闭", 950, 680, 1060, 760));
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> pvCtx =
                new java.util.ArrayList<>();
        pvCtx.add(new SimNode("图片第1张共9张", 60, 300, 700, 360));    // 相册内容（无广告语义）
        TestHost.check(FeedAdGuard.decideP1XFromCandidates(pvClose, pvCtx, 1080, 2400) == null,
                "P1X 无广告上下文绝不裸点「关闭」（相册/普通对话框反例闸）");
        // 合法披露角标也作上下文证据（角标在场=广告在场）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> badgeCtx =
                new java.util.ArrayList<>();
        badgeCtx.add(new SimNode("广告", 60, 700, 130, 760));           // 合法角标（小形态）
        FeedAdGuard.FeedDecision badgeCtxHit = FeedAdGuard.decideP1XFromCandidates(zolClose, badgeCtx, 1080, 2400);
        TestHost.check(badgeCtxHit != null && "×".equals(badgeCtxHit.label),
                "P1X 合法披露角标作上下文证据（角标在场=广告在场）→ 直关");
        // 巨大容器「×」（广告主体大图）→ 形态闸拒
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> bigClose =
                new java.util.ArrayList<>();
        bigClose.add(new SimNode("×", 0, 0, 972, 960));
        TestHost.check(FeedAdGuard.decideP1XFromCandidates(bigClose, zolCtx, 1080, 2400) == null,
                "P1X 形态闸拒广告主体大容器「×」（绝不点广告本体）");
        // 同行近距门：上下文在页头（y=4%），× 在页脚（y=90%）→ 远距拒
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> farCtx =
                new java.util.ArrayList<>();
        farCtx.add(new SimNode("来自广告推荐", 60, 80, 300, 140));
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> farClose =
                new java.util.ArrayList<>();
        farClose.add(new SimNode("×", 1000, 2140, 1060, 2200));
        TestHost.check(FeedAdGuard.decideP1XFromCandidates(farClose, farCtx, 1080, 2400) == null,
                "P1X 同行近距门拒远距「×」（Δy≈86% 屏高——非同卡同排铁证）");
        // null/空安全
        TestHost.check(FeedAdGuard.decideP1XFromCandidates(null, zolCtx, 1080, 2400) == null
                        && FeedAdGuard.decideP1XFromCandidates(zolClose, null, 1080, 2400) == null
                        && FeedAdGuard.decideP1XFromCandidates(zolClose, zolCtx, 1080, 2400) != null,
                "P1X null/空候选安全 + 完整路径命中对照");
        // 直关词族契约
        boolean closeWordsOk = true;
        for (String w : FeedAdGuard.CLOSE_ICON_WORDS) {
            if (!FeedAdGuard.isCloseIconWord(w)) closeWordsOk = false;
        }
        TestHost.check(closeWordsOk && FeedAdGuard.CLOSE_ICON_WORDS.length == 6,
                "CLOSE_ICON_WORDS 6 词全过精确等值闸（× 家族 5 + 关闭）");
        boolean ctxWordsOk = FeedAdGuard.AD_CONTEXT_WORDS.length == 3
                && FeedAdGuard.AD_CONTEXT_WORDS[0].equals("来自广告推荐");
        TestHost.check(ctxWordsOk, "AD_CONTEXT_WORDS 3 词在场（来自广告推荐真机文案置首）");
        // 不可见直关钮拒
        SimNode invX = new SimNode("×", 1000, 700, 1060, 760);
        invX.visible = false;
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> invClose =
                new java.util.ArrayList<>();
        invClose.add(invX);
        TestHost.check(FeedAdGuard.decideP1XFromCandidates(invClose, zolCtx, 1080, 2400) == null,
                "P1X 不可见直关钮拒（visible=false）");
        // P2 armed 对「×」零动作（× 不是中性项——直关归 P1X，收菜单归 P2）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> xOnly =
                new java.util.ArrayList<>();
        xOnly.add(new SimNode("×", 1000, 700, 1060, 760));
        TestHost.check(FeedAdGuard.decideP2FromCandidates(xOnly, 1080, 2400, true) == null,
                "「×」不是中性项（P2 零动作——分工：P1X 直关 / P2 收菜单）");

        /* ════ S12 v7.4.0 真机截图文案重建（截图1/2/3/6 逐字树）（15 项）════ */
        TestHost.suite("FeedAdGuard · S12 真机截图文案重建（微信原因面板 / ZOL 推荐卡 / ZOL 反馈菜单）");
        // 截图1/6 微信「关闭该广告的原因」面板（候选=原生直查模拟：直接关闭+标题+5 原因行）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> wxPanel =
                new java.util.ArrayList<>();
        wxPanel.add(new SimNode("关闭该广告的原因", 200, 740, 700, 800));
        wxPanel.add(new SimNode("反馈后将优化广告推荐", 220, 810, 700, 860));
        wxPanel.add(new SimNode("直接关闭", 790, 1090, 1060, 1150));
        wxPanel.add(new SimNode("汽车厂商相关广告", 220, 1290, 700, 1350));
        wxPanel.add(new SimNode("不喜欢该品牌", 220, 1350, 700, 1410));
        wxPanel.add(new SimNode("重复收到相同素材", 220, 1410, 700, 1470));
        wxPanel.add(new SimNode("其他原因", 220, 1470, 700, 1530));
        wxPanel.add(new SimNode("不喜欢类似产品", 220, 1530, 700, 1590));
        FeedAdGuard.FeedDecision wxP0 = FeedAdGuard.decideP0FromCandidates(wxPanel, 1080, 2400, false);
        TestHost.check(wxP0 != null && FeedAdGuard.ACTION_P0_PANEL_CLOSE.equals(wxP0.action)
                        && "直接关闭".equals(wxP0.label),
                "S12 微信面板逐字树（截图1/6）：P0 直通「直接关闭」（P1 角标识别失败也收得掉）");
        TestHost.check(wxP0 != null && wxP0.tapBounds[0] == 790 && wxP0.tapBounds[1] == 1090,
                "S12 微信面板：tapBounds==主按钮自身（绝不面板容器/原因行）");
        // 同面板 armed P2：直接关闭优先级最高（数组序第一）
        FeedAdGuard.FeedDecision wxP2 = FeedAdGuard.decideP2FromCandidates(wxPanel, 1080, 2400, true);
        TestHost.check(wxP2 != null && FeedAdGuard.ACTION_P2_CLOSE.equals(wxP2.action)
                        && "直接关闭".equals(wxP2.label),
                "S12 微信面板 armed P2：「直接关闭」优先级置顶（v7.4.0 修复——旧表缺此词=收不掉）");
        // 原因行/副文案绝不在中性项（替用户选原因=越权）
        TestHost.check(!FeedAdGuard.isNeutralCloseItem("汽车厂商相关广告")
                        && !FeedAdGuard.isNeutralCloseItem("不喜欢该品牌")
                        && !FeedAdGuard.isNeutralCloseItem("重复收到相同素材")
                        && !FeedAdGuard.isNeutralCloseItem("其他原因")
                        && !FeedAdGuard.isNeutralCloseItem("不喜欢类似产品")
                        && !FeedAdGuard.isNeutralCloseItem("反馈后将优化广告推荐"),
                "S12 原因行/副文案绝不代点（替用户选反馈原因=越权）");
        // 截图2 ZOL 推荐卡（候选=直查模拟：今日推荐/来自广告推荐/×/立即打开 CTA）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> zolCard =
                new java.util.ArrayList<>();
        zolCard.add(new SimNode("来自广告推荐", 60, 700, 300, 760));
        zolCard.add(new SimNode("今日推荐", 60, 640, 220, 700));
        zolCard.add(new SimNode("×", 1000, 700, 1060, 760));
        zolCard.add(new SimNode("看书伤眼，不如试一试听书", 60, 800, 900, 860));   // 广告正文（绝不点）
        zolCard.add(new SimNode("立即打开", 60, 1100, 300, 1160));                  // CTA（绝不点）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> zolCloseC =
                new java.util.ArrayList<>();
        zolCloseC.add(new SimNode("×", 1000, 700, 1060, 760));
        zolCloseC.add(new SimNode("立即打开", 60, 1100, 300, 1160)); // CTA 混进候选也不中（非直关词）
        FeedAdGuard.FeedDecision zolP1X = FeedAdGuard.decideP1XFromCandidates(zolCloseC, zolCard, 1080, 2400);
        TestHost.check(zolP1X != null && "×".equals(zolP1X.label) && zolP1X.tapBounds[0] == 1000,
                "S12 ZOL 推荐卡逐字树（截图2）：P1X 直点「×」（CTA 混入候选不中——非直关词）");
        TestHost.check(!FeedAdGuard.isCloseIconWord("立即打开") && !FeedAdGuard.isCloseIconWord("看书伤眼"),
                "S12 广告正文/CTA 绝不是直关词（×/关闭精确等值闸）");
        // P1 角标路径对 ZOL 卡片：无合法角标（来自广告推荐 6 字 contains 不中）→ null
        TestHost.check(FeedAdGuard.decideP1FromCandidates(zolCard, 1080, 2400) == null,
                "S12 ZOL 卡片 P1 路零命中（「来自广告推荐」非精确等值角标——P1X 才是正路）");
        // 截图3 ZOL 反馈菜单（无法关闭/不感兴趣/举报广告/原因标签/复制广告ID）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> zolMenu =
                new java.util.ArrayList<>();
        zolMenu.add(new SimNode("无法关闭", 200, 700, 500, 760));
        zolMenu.add(new SimNode("不感兴趣", 200, 780, 500, 840));
        zolMenu.add(new SimNode("举报广告", 200, 860, 500, 920));
        zolMenu.add(new SimNode("虚假欺诈", 200, 940, 500, 1000));
        zolMenu.add(new SimNode("点击复制广告ID", 200, 1220, 700, 1280));
        FeedAdGuard.FeedDecision zolP2 = FeedAdGuard.decideP2FromCandidates(zolMenu, 1080, 2400, true);
        TestHost.check(zolP2 != null && "不感兴趣".equals(zolP2.label),
                "S12 ZOL 反馈菜单逐字树（截图3）：armed P2 只收「不感兴趣」");
        TestHost.check(!FeedAdGuard.isNeutralCloseItem("无法关闭")
                        && !FeedAdGuard.isNeutralCloseItem("举报广告")
                        && !FeedAdGuard.isNeutralCloseItem("点击复制广告ID")
                        && !FeedAdGuard.isNeutralCloseItem("虚假欺诈"),
                "S12 「无法关闭/举报广告/复制广告ID/原因标签」绝不代点（非中性项）");
        TestHost.check(FeedAdGuard.isExtendedActionItem("举报广告")
                        && FeedAdGuard.isExtendedActionItem("点击复制广告ID"),
                "S12 举报广告/复制广告ID 在扩展动作表（绝不代点闸）");
        // 微信朋友圈「广告」角标（截图4/5）P1 路命中（候选式）
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> wxBadge =
                new java.util.ArrayList<>();
        wxBadge.add(new SimNode("小鹏汽车： 12.38万元起", 60, 640, 900, 700));  // 广告正文（噪声）
        wxBadge.add(new SimNode("广告∨", 990, 640, 1120, 700));                // 真机角标（纯符号尾∨）
        FeedAdGuard.FeedDecision wxP1 = FeedAdGuard.decideP1FromCandidates(wxBadge, 1080, 2400);
        TestHost.check(wxP1 != null && FeedAdGuard.ACTION_P1_BADGE.equals(wxP1.action)
                        && "广告∨".equals(wxP1.label),
                "S12 微信角标「广告∨」（截图4/5 纯符号尾∨）P1 命中——正文噪声不中");
        // 三路分工总检：P0 收面板 / P1X 直关卡 / P1角标→P2收菜单；同窗全绿
        TestHost.check(wxP0 != null && zolP1X != null && wxP1 != null,
                "S12 三路分工全绿总检（P0 面板直通 + P1X 卡片直关 + P1 角标两段式）");

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
