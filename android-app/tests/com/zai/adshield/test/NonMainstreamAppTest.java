package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.Subscription;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 非主流 App 开屏广告显示逻辑模拟测试（v5.1.0 · issue#46/47 日志逐帧复刻）。
 * ─────────────────────────────────────────────────────────────────
 * 六类真实形态（按真机日志还原控件树坐标/节点规模/窗口类名）：
 *  1. 美团 DspActivity——独立 DSP 开屏窗（全屏热区+广告角标+CTA+close vid 键）
 *  2. 美团 DynamicDialog$c——动态广告弹窗（内部类窗口名 + 可滚动变体安全门）
 *  3. school.app 校徽启动页——135 节点噪声树（fl_scan 扫码键/顶栏图标，
 *     零广告信号：绝不能点、退避必须生效）
 *  4. market 商店首页——145 节点（通知/全部频道/搜索 + RecyclerView：
 *     首页控件绝不代点，零候选退避）
 *  5. www AdsActivity——第三方自建开屏窗（vid 跳过键 + AdsActivity 指纹）
 *  6. waimaiii 渐进加载——4→16→79 节点三阶段（占位窗→倒计时键→首页落定）
 * 4 档机型（低端 720p / 主流 1080p / 2K / 水滴屏）× 全 bounds 中心点击——
 * 零固定坐标，任意分辨率目标一致。
 */
public final class NonMainstreamAppTest {

    static final int[][] DEVICES = {
            {1080, 2400}, // 主流 1080p
            {720, 1600},  // 低端 720p（Redmi 入门）
            {1440, 3200}, // 2K 旗舰
            {1080, 2340}, // 水滴屏
    };
    static final String[] DEVICE_NAMES = {"1080p", "720p", "2K", "水滴屏"};

    public static void main(String[] args) {
        Subscription sub = null;
        try {
            sub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
        } catch (Exception ignored) {
        }
        TestHost.check(sub != null, "内置订阅可解析（前置）");

        TestHost.suite("SDK 广告窗指纹扩展（AdsActivity / DspActivity / DynamicDialog）");
        TestHost.check(AdDecisionCore.isAdActivityClass("com.x.www.ads.AdsActivity"),
                "AdsActivity 识别（www 实锤）");
        TestHost.check(AdDecisionCore.isAdActivityClass("com.sankuai.meituan.ad.dsp.DspActivity"),
                "DspActivity 识别（美团实锤）");
        TestHost.check(AdDecisionCore.isAdActivityClass("com.meituan.robet.DynamicDialog$c"),
                "DynamicDialog 内部类识别（美团实锤）");
        // 负样本：绝不误认（SplashActivity 返回键=退出应用，绝不能算 SDK 窗）
        TestHost.check(!AdDecisionCore.isAdActivityClass("com.school.app.SplashActivity"),
                "SplashActivity 不算 SDK 广告窗（返回=退出应用）");
        TestHost.check(!AdDecisionCore.isAdActivityClass("com.x.www.MainActivity"),
                "MainActivity 不识别");
        TestHost.check(!AdDecisionCore.isAdActivityClass("com.x.www.ScanActivity"),
                "ScanActivity 不识别（扫码页）");
        TestHost.check(!AdDecisionCore.isAdActivityClass("com.x.www.DownloadActivity"),
                "DownloadActivity 不识别");
        TestHost.check(!AdDecisionCore.isAdActivityClass("com.x.www.AdvancedSettingsActivity"),
                "AdvancedSettingsActivity 不识别");

        for (int di = 0; di < DEVICES.length; di++) {
            int w = DEVICES[di][0];
            int h = DEVICES[di][1];
            String dn = DEVICE_NAMES[di];
            TestHost.suite("机型 " + dn + " (" + w + "×" + h + ") · 美团 DspActivity 开屏");

            // ── 形态 1：DspActivity（广告热区 + 角标 + CTA + close vid 键）──
            SimNode dsp = meituanDspTree(w, h);
            Ctx dspCtx = ctx("com.sankuai.meituan",
                    "com.sankuai.meituan.ad.dsp.DspActivity", w, h, 800);
            ScanFind dspF = AdDecisionCore.walk(dsp, dspCtx);
            Decision dspD = AdDecisionCore.decide(dspF, dspCtx);
            TestHost.check(dspD.action.equals(AdDecisionCore.A_CLICK_BOUNDS),
                    "DspActivity 出手点击（" + dspD.reason + "）");
            int[] closeB = dspCloseBounds(w, h);
            boolean onClose = dspD.bounds != null
                    && dspD.bounds[0] >= closeB[0] - 2 && dspD.bounds[2] <= closeB[2] + 2
                    && dspD.bounds[1] >= closeB[1] - 2 && dspD.bounds[3] <= closeB[3] + 2;
            TestHost.check(onClose, "目标 = close 键 bounds（绝不点广告热区/CTA）");

            // 订阅路径（activityIds 限定下 close vid 放行）
            if (sub != null) {
                Hit dspHit = sub.match("com.sankuai.meituan", dsp, dspCtx, 400);
                TestHost.check(dspHit != null, "订阅规则命中 close 键");
                if (dspHit != null) {
                    boolean subOnClose = dspHit.tapBounds != null
                            && dspHit.tapBounds[0] >= closeB[0] - 2
                            && dspHit.tapBounds[2] <= closeB[2] + 2;
                    TestHost.check(subOnClose, "订阅目标 = close 键 bounds");
                }
            }

            // ── DspActivity 无关闭键变体：SDK 窗返回键兜底（独立窗返回=回主界面）──
            SimNode dspNoClose = meituanDspTree(w, h);
            dspNoClose.children.remove(dspNoClose.children.size() - 1); // 摘掉 close 键
            ScanFind fbF = AdDecisionCore.walk(dspNoClose, dspCtx);
            Decision fbD = AdDecisionCore.decide(fbF, dspCtx);
            TestHost.check(fbD.action.equals(AdDecisionCore.A_BACK),
                    "无关闭键 → SDK 窗返回键兜底（" + fbD.reason + "）");

            TestHost.suite("机型 " + dn + " · 美团 DynamicDialog 弹窗");

            // ── 形态 2a：DynamicDialog 跳过键弹窗（内部类窗口名）──
            SimNode dd = dynamicDialogTree(w, h, true);
            Ctx ddCtx = ctx("com.sankuai.meituan",
                    "com.meituan.robet.landingpage.DynamicDialog$c", w, h, 900);
            ScanFind ddF = AdDecisionCore.walk(dd, ddCtx);
            Decision ddD = AdDecisionCore.decide(ddF, ddCtx);
            TestHost.check(ddD.action.equals(AdDecisionCore.A_CLICK_BOUNDS),
                    "DynamicDialog 跳过键点击（" + ddD.reason + "）");

            // ── 形态 2b：可滚动 DynamicDialog（运营弹窗）——弹窗安全门禁返回键 ──
            SimNode ddScroll = dynamicDialogTree(w, h, false);
            ScanFind dsF = AdDecisionCore.walk(ddScroll, ddCtx);
            Decision dsD = AdDecisionCore.decide(dsF, ddCtx);
            TestHost.check(dsD.action.equals(AdDecisionCore.A_NONE),
                    "可滚动弹窗零动作（安全门，" + dsD.action + "）");

            TestHost.suite("机型 " + dn + " · school.app 校徽启动页（安全 + 退避）");

            // ── 形态 3：校徽页 135 节点噪声树（扫码键/顶栏图标，零广告信号）──
            SimNode school = schoolTree(w, h);
            Ctx scCtx = ctx("cn.school.app", "cn.school.app.SplashActivity", w, h, 800);
            ScanFind scF = AdDecisionCore.walk(school, scCtx);
            Decision scD = AdDecisionCore.decide(scF, scCtx);
            TestHost.check(scF.nodeCount > AdDecisionCore.CORNER_CONFIRM_NODES,
                    "校徽页树规模 >60（实际 " + scF.nodeCount + "）");
            TestHost.check(!scF.corners.isEmpty(), "fl_scan 扫码键被收为角落候选（形态）");
            TestHost.check(scD.action.equals(AdDecisionCore.A_NONE),
                    "校徽页零动作（无广告信号绝不点扫码键，" + scD.action + "）");
            TestHost.check(!AdDecisionCore.hasActionableCand(scF, scCtx),
                    "角落候选未过 L4 门 → 零候选（退避可触发）");

            TestHost.suite("机型 " + dn + " · market 商店首页（防误触 + 退避）");

            // ── 形态 4：商店首页 145 节点（通知/全部频道/搜索 + 滚动容器）──
            SimNode market = marketTree(w, h);
            Ctx mkCtx = ctx("com.xiaomi.market", "com.xiaomi.market.ui.MainActivity", w, h, 800);
            ScanFind mkF = AdDecisionCore.walk(market, mkCtx);
            Decision mkD = AdDecisionCore.decide(mkF, mkCtx);
            TestHost.check(mkD.action.equals(AdDecisionCore.A_NONE),
                    "商店首页零动作（通知/频道/搜索绝不代点）");
            java.util.ArrayList<Hit> mkHits = RuleEngine.matchAll(market, mkCtx, 4);
            TestHost.check(mkHits.isEmpty(), "商店首页规则零命中");
            TestHost.check(!AdDecisionCore.hasActionableCand(mkF, mkCtx),
                    "商店首页零可出手候选（退避可触发）");

            TestHost.suite("机型 " + dn + " · www AdsActivity（自建开屏窗）");

            // ── 形态 5：AdsActivity + vid 跳过键（日志实锤 vid 跳过键命中路径）──
            SimNode www = wwwAdsTree(w, h);
            Ctx wwwCtx = ctx("com.x.www", "com.x.www.ads.AdsActivity", w, h, 800);
            ScanFind wwwF = AdDecisionCore.walk(www, wwwCtx);
            Decision wwwD = AdDecisionCore.decide(wwwF, wwwCtx);
            TestHost.check(wwwD.action.equals(AdDecisionCore.A_CLICK_BOUNDS),
                    "AdsActivity vid 跳过键点击（" + wwwD.reason + "）");
            int[] skipB = wwwSkipBounds(w, h);
            boolean onSkip = wwwD.bounds != null
                    && wwwD.bounds[0] >= skipB[0] - 2 && wwwD.bounds[2] <= skipB[2] + 2;
            TestHost.check(onSkip, "目标 = vid 跳过键 bounds");

            // ── 形态 6：waimaiii 渐进加载三阶段 ──
            TestHost.suite("机型 " + dn + " · waimaiii 渐进加载（4→16→79 节点）");
            // 阶段 1：4 节点占位窗 → 微树退避
            SimNode ph = placeholderTree(w, h);
            Ctx phCtx = ctx("com.waimai.app", "com.waimai.app.MainActivity", w, h, 1300);
            ScanFind phF = AdDecisionCore.walk(ph, phCtx);
            Decision phD = AdDecisionCore.decide(phF, phCtx);
            TestHost.check(phD.action.equals(AdDecisionCore.A_NONE), "占位窗零动作");
            TestHost.check(AdDecisionCore.zeroCandBackoff(2, 1300, phF.nodeCount),
                    "微树 2 次扫描即退避（school.app 实锤连环占位窗）");
            TestHost.check(!AdDecisionCore.zeroCandBackoff(1, 1300, phF.nodeCount),
                    "微树 1 次不退（留一次确认机会）");
            // 阶段 2：16 节点 + 倒计时跳过键「跳过 3」→ 形态命中
            SimNode mid = waimaiMidTree(w, h);
            Ctx midCtx = ctx("com.waimai.app", "com.waimai.app.MainActivity", w, h, 1500);
            java.util.ArrayList<Hit> midHits = RuleEngine.matchAll(mid, midCtx, 4);
            boolean cdHit = false;
            for (Hit mh : midHits) {
                if (AdDecisionCore.parseCountdown(mh.label) == 3) cdHit = true;
            }
            TestHost.check(cdHit, "倒计时形态「跳过 3」命中且解析 3s");
            ScanFind midF = AdDecisionCore.walk(mid, midCtx);
            TestHost.check(midF.countdownN == 3, "walk 倒计时解析 3s（门控依据）");
            // 阶段 3：79 节点首页（美团领券/闪购红包）→ 零动作 + 静默期
            SimNode home = waimaiHomeTree(w, h);
            Ctx homeCtx = ctx("com.waimai.app", "com.waimai.app.MainActivity", w, h, 2600);
            ScanFind homeF = AdDecisionCore.walk(home, homeCtx);
            Decision homeD = AdDecisionCore.decide(homeF, homeCtx);
            TestHost.check(homeD.action.equals(AdDecisionCore.A_NONE),
                    "首页零动作（美团领券/闪购红包绝不代点）");
            TestHost.check(!AdDecisionCore.hasActionableCand(homeF, homeCtx),
                    "首页零可出手候选");
            TestHost.check(AdDecisionCore.zeroCandBackoff(3, 2700, homeF.nodeCount),
                    "常规树 3 次/2.6s 后退避（market/school 实锤）");
            TestHost.check(!AdDecisionCore.zeroCandBackoff(3, 2500, homeF.nodeCount),
                    "2.5s 不退（晚出按钮 1~2s 渲染窗口保留）");
        }

        TestHost.suite("零候选退避边界（纯逻辑）");
        TestHost.check(!AdDecisionCore.zeroCandBackoff(2, 2700, 135), "常规树 2 次不退");
        TestHost.check(AdDecisionCore.zeroCandBackoff(3, 2700, 135), "常规树 3 次/2.7s 退");
        TestHost.check(!AdDecisionCore.zeroCandBackoff(3, 2000, 135), "常规树 3 次但 2.0s 不退");
        TestHost.check(AdDecisionCore.zeroCandBackoff(2, 1300, 4), "微树 2 次/1.3s 退");
        TestHost.check(!AdDecisionCore.zeroCandBackoff(2, 1100, 4), "微树 1.1s 不退");

        TestHost.suite("跳过后静默期（纯逻辑）");
        long t0 = 1000000L;
        TestHost.check(AdDecisionCore.postSkipQuiet(t0, t0 + 800), "关广告后 0.8s 静默");
        TestHost.check(AdDecisionCore.postSkipQuiet(t0, t0 + 2100), "关广告后 2.1s 静默");
        TestHost.check(!AdDecisionCore.postSkipQuiet(t0, t0 + 2300), "关广告后 2.3s 恢复记录");
        TestHost.check(!AdDecisionCore.postSkipQuiet(0, t0), "无关闭记录不静默");

        TestHost.suite("支付/资金页零扫描标记（安全性）");
        TestHost.check(AdDecisionCore.isCodeBannerMarker("请输入支付密码"), "支付密码标记");
        TestHost.check(AdDecisionCore.isCodeBannerMarker("付款码"), "付款码标记");
        TestHost.check(AdDecisionCore.isCodeBannerMarker("收银台"), "收银台标记");
        TestHost.check(AdDecisionCore.isCodeBannerMarker("刷脸支付"), "刷脸支付标记");
        TestHost.check(AdDecisionCore.isCodeBannerMarker("短信验证码"), "验证码标记（回归）");
        TestHost.check(!AdDecisionCore.isCodeBannerMarker("跳过广告"), "跳过文案非标记");
        SimNode payTree = new SimNode();
        payTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode payLabel = new SimNode("请输入支付密码", 200, 1000, 880, 1080);
        SimNode paySkip = TestHost.skipButton("跳过");
        payTree.add(payLabel);
        payTree.add(paySkip);
        TestHost.check(AdDecisionCore.treeHasCodeBannerMarker(payTree, 60),
                "支付页树零扫描（含跳过键也绝不点）");

        TestHost.suite("诱导词否决增补（安全性）");
        SimNode anyNode = TestHost.skipButton("跳过");
        int[] anyB = anyNode.boundsArr;
        Ctx anyCtx = ctx("com.x.app", "com.x.app.AdsActivity", 1080, 2400, 800);
        TestHost.check(RuleEngine.matchText("跳过领礼包", anyB, anyNode, anyCtx) == null,
                "「跳过领礼包」否决");
        TestHost.check(RuleEngine.matchText("跳过抢购", anyB, anyNode, anyCtx) == null,
                "「跳过抢购」否决");
        TestHost.check(RuleEngine.matchText("跳过 3", anyB, anyNode, anyCtx) != null,
                "「跳过 3」照常命中（倒计时形态回归）");
        TestHost.check(RuleEngine.matchText("跳过", anyB, anyNode, anyCtx) != null,
                "「跳过」照常命中（精确层回归）");
        TestHost.check(AdDecisionCore.hasInducementWord("跳过秒杀"), "hasInducementWord 秒杀");

        TestHost.done();
    }

    /* ═══════════════ 形态构造器（按 issue#46/47 日志坐标复刻） ═══════════════ */

    static SimNode node(String text, int l, int t, int r, int b) {
        return new SimNode(text, l, t, r, b);
    }

    static int[] dspCloseBounds(int w, int h) {
        return new int[]{(int) (w * 0.87), (int) (h * 0.025), (int) (w * 0.97), (int) (h * 0.08)};
    }

    /** 形态 1：美团 DspActivity——全屏热区 + 广告角标 + CTA + close vid 键 */
    static SimNode meituanDspTree(int w, int h) {
        SimNode root = node("", 0, 0, w, h);
        root.cls = "android.widget.FrameLayout";
        SimNode ad = node("", 0, 0, w, (int) (h * 0.96));
        ad.clickable = true; // 全屏广告热区（点击=进落地页，绝不能点）
        SimNode badge = node("广告", (int) (w * 0.04), (int) (h * 0.03),
                (int) (w * 0.11), (int) (h * 0.052));
        SimNode cta = node("点击查看详情", (int) (w * 0.30), (int) (h * 0.86),
                (int) (w * 0.70), (int) (h * 0.91));
        cta.clickable = true;
        int[] cb = dspCloseBounds(w, h);
        SimNode close = node("", cb[0], cb[1], cb[2], cb[3]);
        close.vid = "com.sankuai.meituan:id/iv_close";
        close.clickable = true;
        root.add(ad);
        root.add(badge);
        root.add(cta);
        root.add(close);
        return root;
    }

    /** 形态 2：美团 DynamicDialog（内部类窗口）——带跳过键 / 可滚动变体 */
    static SimNode dynamicDialogTree(int w, int h, boolean withSkip) {
        SimNode root = node("", 0, 0, w, h);
        root.cls = "com.meituan.robet.landingpage.DynamicDialog$c";
        SimNode content = node("", (int) (w * 0.10), (int) (h * 0.28),
                (int) (w * 0.90), (int) (h * 0.78));
        if (withSkip) {
            SimNode skip = node("跳过", (int) (w * 0.80), (int) (h * 0.26),
                    (int) (w * 0.92), (int) (h * 0.30));
            skip.clickable = true;
            root.add(skip);
        } else {
            // 可滚动运营弹窗（ScrollView + 营销内容）——弹窗安全门禁返回键
            SimNode scroll = node("", (int) (w * 0.10), (int) (h * 0.28),
                    (int) (w * 0.90), (int) (h * 0.78));
            scroll.cls = "android.widget.ScrollView";
            scroll.scrollable = true;
            for (int i = 0; i < 5; i++) {
                SimNode row = node("限时优惠 第" + (i + 1) + "档", (int) (w * 0.14),
                        (int) (h * 0.32) + i * (int) (h * 0.08),
                        (int) (w * 0.86), (int) (h * 0.38) + i * (int) (h * 0.08));
                scroll.add(row);
            }
            content.add(scroll);
        }
        SimNode badge = node("广告", (int) (w * 0.12), (int) (h * 0.30),
                (int) (w * 0.19), (int) (h * 0.325));
        root.add(content);
        root.add(badge);
        return root;
    }

    /** 形态 3：school.app 校徽启动页（135 节点噪声树——零广告信号） */
    static SimNode schoolTree(int w, int h) {
        SimNode root = node("", 0, 0, w, h);
        root.cls = "android.widget.FrameLayout";
        SimNode title = node("皖江工学院", (int) (w * 0.36), (int) (h * 0.08),
                (int) (w * 0.64), (int) (h * 0.12));
        SimNode scan = node("", (int) (w * 0.89), (int) (h * 0.08),
                (int) (w * 0.99), (int) (h * 0.13));
        scan.vid = "cn.school.app:id/fl_scan";
        scan.clickable = true; // 扫码键：角落形态但非跳过键——绝不能点
        SimNode top1 = node("", (int) (w * 0.16), (int) (h * 0.16),
                (int) (w * 0.26), (int) (h * 0.20));
        top1.vid = "cn.school.app:id/iv_top_menu_1";
        SimNode top2 = node("", (int) (w * 0.49), (int) (h * 0.16),
                (int) (w * 0.59), (int) (h * 0.20));
        top2.vid = "cn.school.app:id/iv_top_menu_2";
        root.add(title);
        root.add(scan);
        root.add(top1);
        root.add(top2);
        // 校园通知流（把树规模堆到日志实锤的 135 节点量级）
        SimNode listWrap = node("", 0, (int) (h * 0.22), w, h);
        for (int i = 0; i < 130; i++) {
            SimNode row = node("关于 " + (2018 + i % 8) + " 年春季学期第" + ((i % 20) + 1)
                    + "周教学安排的通知", (int) (w * 0.06),
                    (int) (h * 0.26) + i * 22, (int) (w * 0.94),
                    (int) (h * 0.26) + i * 22 + 44);
            listWrap.add(row);
        }
        root.add(listWrap);
        return root;
    }

    /** 形态 4：market 商店首页（145 节点：通知/全部频道/搜索 + 滚动容器） */
    static SimNode marketTree(int w, int h) {
        SimNode root = node("", 0, 0, w, h);
        root.cls = "android.widget.FrameLayout";
        SimNode banner = node("男士护肤", (int) (w * 0.36), (int) (h * 0.07),
                (int) (w * 0.58), (int) (h * 0.11));
        SimNode notice = node("通知", (int) (w * 0.87), (int) (h * 0.07),
                (int) (w * 0.97), (int) (h * 0.11));
        notice.clickable = true;
        SimNode channels = node("全部频道", (int) (w * 0.84), (int) (h * 0.115),
                (int) (w * 0.99), (int) (h * 0.15));
        channels.clickable = true;
        SimNode search = node("搜索", (int) (w * 0.80), (int) (h * 0.115),
                (int) (w * 0.83), (int) (h * 0.15));
        root.add(banner);
        root.add(notice);
        root.add(channels);
        root.add(search);
        SimNode recycler = node("", 0, (int) (h * 0.16), w, h);
        recycler.cls = "androidx.recyclerview.widget.RecyclerView";
        recycler.scrollable = true;
        for (int i = 0; i < 70; i++) {
            SimNode cell = node("应用榜单 第" + (i + 1) + "名", (int) (w * 0.10),
                    (int) (h * 0.20) + i * 26, (int) (w * 0.90),
                    (int) (h * 0.20) + i * 26 + 48);
            recycler.add(cell);
        }
        root.add(recycler);
        return root;
    }

    static int[] wwwSkipBounds(int w, int h) {
        return new int[]{(int) (w * 0.82), (int) (h * 0.025), (int) (w * 0.96), (int) (h * 0.08)};
    }

    /** 形态 5：www AdsActivity——自建开屏窗 + vid 跳过键（零文本图片键） */
    static SimNode wwwAdsTree(int w, int h) {
        SimNode root = node("", 0, 0, w, h);
        root.cls = "com.x.www.ads.AdsActivity";
        SimNode ad = node("", 0, 0, w, (int) (h * 0.95));
        ad.clickable = true;
        SimNode badge = node("广告", (int) (w * 0.04), (int) (h * 0.03),
                (int) (w * 0.11), (int) (h * 0.052));
        int[] sb = wwwSkipBounds(w, h);
        SimNode skip = node("", sb[0], sb[1], sb[2], sb[3]);
        skip.vid = "com.x.www:id/skip_view";
        skip.clickable = true;
        root.add(ad);
        root.add(badge);
        root.add(skip);
        return root;
    }

    /** 形态 6 阶段 1：4 节点加载占位窗（日志实锤「树 4 节点 · FrameLayout」） */
    static SimNode placeholderTree(int w, int h) {
        SimNode root = node("", 0, 0, w, h);
        root.cls = "android.widget.FrameLayout";
        SimNode bar = node("", (int) (w * 0.44), (int) (h * 0.49),
                (int) (w * 0.56), (int) (h * 0.51));
        bar.cls = "android.widget.ProgressBar";
        SimNode loading = node("加载中", (int) (w * 0.40), (int) (h * 0.53),
                (int) (w * 0.60), (int) (h * 0.56));
        SimNode blank = node("", 0, 0, 0, 0);
        blank.visible = false;
        root.add(bar);
        root.add(loading);
        root.add(blank);
        return root;
    }

    /** 形态 6 阶段 2：16 节点 + 倒计时跳过键「跳过 3」（日志实锤倒计时形态命中） */
    static SimNode waimaiMidTree(int w, int h) {
        SimNode root = node("", 0, 0, w, h);
        root.cls = "android.widget.FrameLayout";
        SimNode ad = node("", 0, 0, w, (int) (h * 0.92));
        ad.clickable = true;
        SimNode skip = node("跳过 3", (int) (w * 0.80), (int) (h * 0.025),
                (int) (w * 0.96), (int) (h * 0.075));
        skip.clickable = true;
        root.add(ad);
        root.add(skip);
        for (int i = 0; i < 13; i++) {
            root.add(node("", (int) (w * 0.1 * (i % 9)), (int) (h * 0.2),
                    (int) (w * 0.1 * (i % 9)) + 60, (int) (h * 0.2) + 60));
        }
        return root;
    }

    /** 形态 6 阶段 3：79 节点首页（美团领券/闪购红包——复验通过后的落定期） */
    static SimNode waimaiHomeTree(int w, int h) {
        SimNode root = node("", 0, 0, w, h);
        root.cls = "android.widget.FrameLayout";
        SimNode coupon1 = node("美团领券", (int) (w * 0.04), (int) (h * 0.50),
                (int) (w * 0.30), (int) (h * 0.56));
        coupon1.clickable = true;
        SimNode coupon2 = node("闪购红包", (int) (w * 0.36), (int) (h * 0.50),
                (int) (w * 0.62), (int) (h * 0.56));
        coupon2.clickable = true;
        SimNode search = node("搜索", (int) (w * 0.82), (int) (h * 0.11),
                (int) (w * 0.95), (int) (h * 0.145));
        SimNode top = node("皖江工学院(霍邱校区)", (int) (w * 0.55), (int) (h * 0.07),
                (int) (w * 0.98), (int) (h * 0.10));
        root.add(coupon1);
        root.add(coupon2);
        root.add(search);
        root.add(top);
        SimNode feed = node("", 0, (int) (h * 0.60), w, h);
        feed.cls = "androidx.recyclerview.widget.RecyclerView";
        feed.scrollable = true;
        for (int i = 0; i < 70; i++) {
            feed.add(node("为你推荐 商家第" + (i + 1) + "家", (int) (w * 0.06),
                    (int) (h * 0.64) + i * 24, (int) (w * 0.94),
                    (int) (h * 0.64) + i * 24 + 44));
        }
        root.add(feed);
        return root;
    }

    static Ctx ctx(String pkg, String act, int w, int h, long elapsed) {
        Ctx ctx = new Ctx();
        ctx.screenW = w;
        ctx.screenH = h;
        ctx.pkg = pkg;
        ctx.activityId = act;
        ctx.elapsedMs = elapsed;
        ctx.splashLive = true;
        ctx.sdkAdWin = AdDecisionCore.isAdActivityClass(act);
        return ctx;
    }
}
