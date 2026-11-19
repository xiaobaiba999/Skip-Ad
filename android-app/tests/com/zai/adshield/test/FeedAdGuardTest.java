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
 * 与 MiniProgramGuard 同规约（纯 Java 零 Android 依赖，ECJ -1.8 编译 JVM 运行）。
 * ─────────────────────────────────────────────────────────────
 * 恰好 90 项断言（check/checkEq 总调用数）：
 *  S1 名单门（20）/ S2 微信窗级门（6）/ S3 P1 角标语义闸①（12）/
 *  S4 P1 面积闸②（8）/ S5 P2 中性项闸③（12）/ S6 两段式决策（12）/
 *  S7 常量在场（8）/ S8 预算闸与 1:1 人手角标触点（6）/
 *  S9 v7.2.0 候选式快搜（6）。
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

        /* ════ S5 P2 中性项闸③（12 项）════ */
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

        /* ════ S7 常量在场（8 项）════ */
        TestHost.suite("FeedAdGuard · S7 常量在场（安全闸参数）");
        TestHost.checkEq(1200L, FeedAdGuard.FEED_SCAN_THROTTLE_MS, "FEED_SCAN_THROTTLE_MS == 1200（1.2s 节流）");
        TestHost.checkEq(350L, FeedAdGuard.P2_SCAN_THROTTLE_MS, "v7.2.0 P2_SCAN_THROTTLE_MS == 350（armed 快节奏复查）");
        TestHost.checkEq(300, FeedAdGuard.FEED_NODE_BUDGET, "FEED_NODE_BUDGET == 300（DFS 兑底预算）");
        TestHost.checkEq(8000L, FeedAdGuard.P1_ARM_MS, "P1_ARM_MS == 8000（P2 指纹窗）");
        TestHost.check(Math.abs(FeedAdGuard.BADGE_W_MAX - 0.35) < 1e-9, "BADGE_W_MAX == 0.35（≤35% 屏宽）");
        TestHost.check(Math.abs(FeedAdGuard.BADGE_H_MAX - 0.08) < 1e-9, "BADGE_H_MAX == 0.08（≤8% 屏高）");
        TestHost.checkEq(6, FeedAdGuard.BADGE_MAX_CHARS, "BADGE_MAX_CHARS == 6（>6 字必是正文）");
        TestHost.check(FeedAdGuard.stats() != null && FeedAdGuard.stats().contains("18 款")
                        && FeedAdGuard.stats().contains("armed≥1即出手"),
                "stats() 名单 18 款 + v7.2.0 armed≥1 即出手在场");

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
