package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.Subscription;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 内置订阅 v5.1 测试（102 款必备应用 + 美团系专项）。
 * 覆盖：可解析性 / 应用数 / 抖音 SplashActivity 限定 /
 * 团购券容器场景规则失效 / 正常开屏规则命中 / 通用安全门 /
 * 美团 DspActivity·DynamicDialog 窗口限定（v5.1.0 issue#46/47）。
 */
public final class SubscriptionBuiltinTest {

    public static void main(String[] args) {
        TestHost.suite("内置订阅 · 解析与规模");

        Subscription sub = null;
        try {
            sub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
        } catch (Exception e) {
            System.out.println("  ❌ BUILTIN_JSON5 解析异常: " + e);
        }
        TestHost.check(sub != null, "BUILTIN_JSON5 可解析");
        if (sub == null) {
            TestHost.done();
            return;
        }
        TestHost.check(sub.apps.size() >= 102, "应用数 ≥102（实际 " + sub.apps.size() + "）");

        // 关键应用在场
        String[] mustHave = {
                "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite",
                "com.smile.gifmaker", "com.kwai.video",
                "com.taobao.taobao", "com.tmall.wireless", "com.xunmeng.pinduoduo",
                "com.sankuai.meituan", "com.jingdong.app.mall", "me.ele",
                "tv.danmaku.bili", "com.qiyi.video", "com.tencent.qqlive",
                "com.ximalaya.ting.android", "com.sina.weibo", "com.xingin.xhs",
                "com.zhihu.android", "net.csdn.csdnplus",
                "com.xiaomi.market", "com.huawei.appmarket", "com.oppo.market", "com.bbk.appstore",
                "com.ctrip.android.view.ctriptrip", "com.baidu.BaiduMap", "com.autonavi.minimap",
                "com.alibaba.android.rimet", "com.sdu.didi.psnger", "com.hpbr.bosszhipin",
                // v5.1.0 新增：银行金融 / 出行 / 阅读漫画 / 工具 / 生活 / 学习 / 音乐社交
                "com.icbc", "com.android.bankabc", "com.chinamworld.main",
                "com.cmbchina.ccd.pluto.spongeactivity", "com.unionpay",
                "com.MobileTicket", "com.umetrip.android.msky.app", "com.jingyao.easybike",
                "com.tencent.weread", "com.qidian.QDReader", "com.kmxs.reader",
                "com.moji.mjweather", "com.snda.wifilocating", "com.baidu.netdisk",
                "com.baidu.tieba", "com.youdao.dict",
                "com.cainiao.wireless", "com.wuba.zhuanzhuan", "com.suning.mobile.ebuy",
                "com.chaoxing.mobile", "com.handsgo.jiakao.android", "com.jiongji.andriod.card",
                "com.tencent.wework", "cn.kuwo.player", "com.ss.android.lark",
                "com.lemon.lvoverseas", "com.mt.mtxx.mtxx",
                "*", // 全局通用组
        };
        java.util.HashSet<String> ids = new java.util.HashSet<String>();
        for (Subscription.App a : sub.apps) ids.add(a.id);
        for (String p : mustHave) {
            TestHost.check(ids.contains(p), "在场: " + p);
        }

        TestHost.suite("内置订阅 · 抖音 SplashActivity 限定（容器双保险）");

        // 找抖音规则并验证 activityIds
        Subscription.App dy = null;
        for (Subscription.App a : sub.apps) {
            if ("com.ss.android.ugc.aweme".equals(a.id)) dy = a;
        }
        TestHost.check(dy != null, "抖音规则组存在");
        if (dy != null) {
            TestHost.check(dy.rules.size() >= 1, "抖音至少 1 条规则");
            boolean splashLimited = false;
            for (Subscription.Rule r : dy.rules) {
                for (String aid : r.activityIds) {
                    if (aid.endsWith("SplashActivity")) splashLimited = true;
                }
            }
            TestHost.check(splashLimited, "抖音规则限定 SplashActivity");
        }

        TestHost.suite("内置订阅 · 团购券场景（小程序容器内规则失效）");

        // 场景 A：抖音开屏窗（SplashActivity）+ 跳过文本 → 命中
        Ctx splashCtx = ctx("com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.splash.SplashActivity");
        SimNode splashTree = adTreeWithSkip();
        Hit hitSplash = sub.match("com.ss.android.ugc.aweme", splashTree, splashCtx, 400);
        TestHost.check(hitSplash != null, "抖音开屏窗跳过键命中（规则正常工作）");

        // 场景 B：抖音小程序容器 + 跳过文本 → activityIds 门拦截（不命中）
        Ctx miniCtx = ctx("com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.miniapp.ui.MiniAppActivity");
        SimNode miniTree = adTreeWithSkip();
        Hit hitMini = sub.match("com.ss.android.ugc.aweme", miniTree, miniCtx, 400);
        TestHost.check(hitMini == null, "团购券容器内订阅规则失效（activityIds 门）");

        // 场景 C：PrivilegeDetailActivity（团购券详情）→ 同样失效
        Ctx privCtx = ctx("com.ss.android.ugc.aweme",
                "com.bytedance.privilege.PrivilegeDetailActivity");
        Hit hitPriv = sub.match("com.ss.android.ugc.aweme", adTreeWithSkip(), privCtx, 400);
        TestHost.check(hitPriv == null, "团购券详情页订阅规则失效");

        TestHost.suite("内置订阅 · 其他必备应用命中");

        // 快手开屏（无 activityIds 限定 → looksSkipLike 门内通过）
        Ctx ksCtx = ctx("com.smile.gifmaker", "com.smile.gifmaker.SplashAdActivity");
        Hit ksHit = sub.match("com.smile.gifmaker", adTreeWithSkip(), ksCtx, 400);
        TestHost.check(ksHit != null, "快手开屏跳过命中");

        // 淘宝开屏
        Ctx tbCtx = ctx("com.taobao.taobao", "com.taobao.tao.welcome.Welcome");
        Hit tbHit = sub.match("com.taobao.taobao", adTreeWithSkip(), tbCtx, 400);
        TestHost.check(tbHit != null, "淘宝开屏跳过命中");

        // vid 路径：淘宝 vid 含 skip；「jump」vid 被 looksSkipLike 门拦（安全门验证）
        SimNode vidTree = new SimNode();
        vidTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skipBtn = new SimNode("", 900, 80, 1060, 150);
        skipBtn.vid = "com.taobao.taobao:id/skip_button";
        skipBtn.clickable = true;
        vidTree.add(skipBtn);
        Hit vidHit = sub.match("com.taobao.taobao", vidTree, tbCtx, 400);
        TestHost.check(vidHit != null, "淘宝 vid skip 键命中");

        SimNode jumpTree = new SimNode();
        jumpTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode jumpBtn = new SimNode("", 900, 80, 1060, 150);
        jumpBtn.vid = "com.taobao.taobao:id/jump_button";
        jumpBtn.clickable = true;
        jumpTree.add(jumpBtn);
        Hit jumpHit = sub.match("com.taobao.taobao", jumpTree, tbCtx, 400);
        TestHost.check(jumpHit == null, "非跳过语义 vid（jump）被安全门拦");

        TestHost.suite("内置订阅 · 美团系专项（v5.1.0 issue#46/47）");

        // 美团 DspActivity 开屏窗：close 族 vid 在 activityIds 限定下放行
        SimNode dspTree = new SimNode();
        dspTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode dspAd = new SimNode("", 0, 0, 1080, 2200);
        dspAd.clickable = true; // 全屏广告热区
        SimNode dspClose = new SimNode("", 940, 70, 1050, 180);
        dspClose.vid = "com.sankuai.meituan:id/iv_close";
        dspClose.clickable = true;
        dspTree.add(dspAd);
        dspTree.add(dspClose);
        Ctx dspCtx = ctx("com.sankuai.meituan", "com.sankuai.meituan.ad.dsp.DspActivity");
        Hit dspHit = sub.match("com.sankuai.meituan", dspTree, dspCtx, 400);
        TestHost.check(dspHit != null, "DspActivity 开屏 close vid 命中");
        if (dspHit != null) {
            boolean onClose = dspHit.tapBounds != null
                    && dspHit.tapBounds[0] >= 940 && dspHit.tapBounds[2] <= 1050;
            TestHost.check(onClose, "DspActivity 目标是 close 键 bounds（非广告热区）");
        }

        // DspActivity 规则在 MainActivity 上失效（close 族不越窗出手）
        Ctx mtMainCtx = ctx("com.sankuai.meituan", "com.sankuai.meituan.activity.MainActivity");
        Hit mtMainHit = sub.match("com.sankuai.meituan", dspTree, mtMainCtx, 400);
        boolean mainCloseLeak = mtMainHit != null
                && mtMainHit.tapBounds != null && mtMainHit.tapBounds[0] >= 940;
        TestHost.check(!mainCloseLeak, "MainActivity 上 close 键不被代点（窗口门）");

        // DynamicDialog 广告弹窗：跳过文本命中
        SimNode ddTree = new SimNode();
        ddTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode ddSkip = TestHost.skipButton("跳过");
        ddTree.add(ddSkip);
        Ctx ddCtx = ctx("com.sankuai.meituan", "com.meituan.robet.landingpage.DynamicDialog$c");
        Hit ddHit = sub.match("com.sankuai.meituan", ddTree, ddCtx, 400);
        TestHost.check(ddHit != null, "DynamicDialog 弹窗跳过键命中");

        // v5.1.0 新增应用抽检：贴吧（日志实锤）/ 12306 / 微信读书
        Hit tiebaHit = sub.match("com.baidu.tieba", adTreeWithSkip(),
                ctx("com.baidu.tieba", "com.baidu.tieba.pb.pbmain.PbMainTabActivity"), 400);
        TestHost.check(tiebaHit != null, "贴吧开屏跳过命中");
        Hit ticketHit = sub.match("com.MobileTicket", adTreeWithSkip(),
                ctx("com.MobileTicket", "com.MobileTicket.ui.foundation.splash.SplashAdActivity"), 400);
        TestHost.check(ticketHit != null, "铁路12306 开屏跳过命中");
        Hit wereadHit = sub.match("com.tencent.weread", adTreeWithSkip(),
                ctx("com.tencent.weread", "com.tencent.weread.ui.LauncherUI"), 400);
        TestHost.check(wereadHit != null, "微信读书开屏跳过命中");

        TestHost.suite("内置订阅 · 安全门不回退");

        // 满宽「跳过」横幅（列表行/横幅形态）——无 activityIds 规则的 looksSkipLike
        // 门 + 主引擎形状门管；这里验证面积门：>35% 屏的巨「跳过」绝不命中
        SimNode bannerTree = new SimNode();
        bannerTree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode banner = new SimNode("跳过广告 查看详情", 0, 1000, 1080, 1400);
        banner.clickable = true;
        bannerTree.add(banner);
        Ctx bannerCtx = ctx("com.smile.gifmaker", "com.smile.gifmaker.MainActivity");
        Hit bannerHit = sub.match("com.smile.gifmaker", bannerTree, bannerCtx, 400);
        TestHost.check(bannerHit == null, "满宽巨横幅绝不命中");

        TestHost.done();
    }

    static Ctx ctx(String pkg, String activity) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = activity;
        ctx.elapsedMs = 800;
        ctx.splashLive = true;
        return ctx;
    }

    /** 开屏广告树：跳过文本键（右上角小按钮）。 */
    static SimNode adTreeWithSkip() {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skip = TestHost.skipButton("跳过");
        skip.clickable = true;
        root.add(skip);
        return root;
    }
}
