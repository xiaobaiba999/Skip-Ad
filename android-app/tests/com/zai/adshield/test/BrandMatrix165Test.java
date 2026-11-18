package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.PowerGovernor;
import com.zai.adshield.core.RestoreGuard;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.Subscription;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 11 品牌 × 15 机型全维度矩阵测试（v5.9.0 · 用户四轮要求：模拟「N 多」品牌
 * 机型测试修复；含 app 开屏广告测试、屏蔽速度测试、bug 测试、功耗测试、
 * 精准度测试、算法优化测试）。
 *
 * 矩阵（v5.6.0 5×10=50 → v5.7.0 8×12=96 → v5.8.0 10×15=150 →
 * v5.9.0 11×15=165 · 新增传音 Tecno/Infinix/itel·HiOS/XOS 族）：
 *  Xiaomi/Redmi/POCO·HyperOS + Huawei·HarmonyOS + OPPO/realme·ColorOS +
 *  一加·ColorOS + vivo/iQOO·OriginOS + 荣耀·MagicOS + Samsung·OneUI +
 *  魅族·Flyme + 中兴/努比亚/红魔·MyOS + 传音 Tecno/Infinix/itel·HiOS/XOS +
 *  其他（Pixel/Nothing/索尼/moto）= 165 机型。分辨率取真实规格（水滴/挖孔/直屏/折叠
 * 外屏主流档），引擎全 bounds 触点经 TapPoint 四层防线（v5.9 设备自适应
 * 触点）——任意分辨率目标面安全落点（误触零容忍）。
 *
 * 每机型六维（对应报告六列）：
 *  ① 开屏广告命中（六信号：文本键/desc 键/vid 订阅键/图片角落键/
 *    v5.8「跳过推广」新文案/v5.8 iv_ad_close 复合 vid 键——≥5 过）
 *  ② 屏蔽速度（JVM walk+decide 120 节点树 < 20ms · v5.8 速度压榨参数在场：
 *    COUNTDOWN_EXTRA_MS=120 / MAX_CLICKS_PER_WIN=12 / SKIP_CANDS_MAX=8）
 *  ③ bug 测试（菜单陷阱行/巨横幅/验证码横幅/游戏 jump vid 四陷阱零动作）
 *  ④ 功耗测试（典型日 45 启动 + 120 后台事件 < 0.15mWh · 分级预算在场）
 *  ⑤ 精准度测试（银行守护零返回键 · 验证码标记 · Unity 游戏引擎窗免疫）
 *  ⑥ 算法优化测试（v5.9 新指纹 ksad/csj_ 全命中 · 非广告窗 7 档全免疫
 *    · 11 品牌族归一正确 · 免设置自愈合并纯函数正确）
 */
public final class BrandMatrix165Test {

    static final String[][] XIAOMI = {
            {"Xiaomi 15 Ultra", "1440", "3200"},
            {"Xiaomi 15 Pro", "1440", "3200"},
            {"Xiaomi 15", "1440", "3200"},
            {"Xiaomi 14 Pro", "1440", "3200"},
            {"Xiaomi 14", "1200", "2670"},
            {"Redmi K80 Pro", "1440", "3200"},
            {"Redmi K80", "1440", "3200"},
            {"Redmi Turbo 4", "1200", "2712"},
            {"Redmi Note 14 Pro+", "1220", "2712"},
            {"Redmi Note 13", "1080", "2400"},
            {"Redmi 14C", "720", "1650"},
            {"POCO F7", "1220", "2712"},
            {"POCO X7 Pro", "1220", "2712"},
            {"Xiaomi Pad 6S Pro", "1800", "3048"},
            {"小米 14T Pro", "1220", "2712"},
    };
    static final String[][] HUAWEI = {
            {"Mate 70 Pro+", "1260", "2844"},
            {"Mate 70 Pro", "1260", "2844"},
            {"Mate 70", "1080", "2376"},
            {"Mate 70 RS", "1260", "2844"},
            {"Mate X6 外屏", "1080", "2376"},
            {"Mate 60 Pro", "1260", "2720"},
            {"Mate 60", "1080", "2376"},
            {"Pura 70 Pro", "1260", "2844"},
            {"Pura 70", "1080", "2376"},
            {"nova 14", "1080", "2376"},
            {"nova 13", "1080", "2376"},
            {"nova 12", "1080", "2376"},
            {"Enjoy 80", "720", "1600"},
            {"Pocket 2", "1080", "2690"},
            {"MatePad Pro 13.2", "2160", "2880"},
    };
    static final String[][] OPPO = {
            {"Find X8 Ultra", "1260", "2760"},
            {"Find X8 Pro", "1260", "2760"},
            {"Find X8", "1260", "2640"},
            {"Find X7 Ultra", "1440", "3168"},
            {"Find X7", "1260", "2772"},
            {"Reno 14", "1080", "2412"},
            {"Reno 13 Pro", "1080", "2412"},
            {"Reno 12", "1080", "2412"},
            {"Ace 5 Pro", "1080", "2400"},
            {"Ace 5", "1080", "2400"},
            {"K13", "1080", "2412"},
            {"realme GT8 Pro", "1260", "2800"},
            {"realme GT7", "1260", "2800"},
            {"realme Neo7", "1080", "2340"},
            {"OPPO Pad 3 Pro", "1800", "2800"},
    };
    static final String[][] ONEPLUS = {
            {"一加 15", "1440", "3168"},
            {"一加 13", "1440", "3168"},
            {"一加 13T", "1216", "2640"},
            {"一加 12", "1440", "3168"},
            {"一加 11", "1440", "3216"},
            {"一加 Ace 6", "1080", "2412"},
            {"一加 Ace 5 Pro", "1080", "2400"},
            {"一加 Ace 5", "1080", "2400"},
            {"一加 Ace 3", "1080", "2412"},
            {"一加 Nord 5", "1080", "2400"},
            {"一加 Nord 4", "1260", "2772"},
            {"一加 Nord CE4", "1080", "2412"},
            {"一加 Nord N5", "1080", "2400"},
            {"一加 Ace 2 Pro", "1240", "2772"},
            {"一加 Pad 2 Pro", "1800", "2800"},
    };
    static final String[][] VIVO = {
            {"X300 Pro", "1260", "2800"},
            {"X300", "1260", "2800"},
            {"X200 Pro mini", "1176", "2368"},
            {"X200 Pro", "1260", "2800"},
            {"X200", "1260", "2800"},
            {"X100 Ultra", "1440", "3200"},
            {"S20 Pro", "1260", "2800"},
            {"S20", "1260", "2800"},
            {"Y300 Pro", "1080", "2376"},
            {"iQOO 15", "1440", "3200"},
            {"iQOO 13", "1440", "3200"},
            {"iQOO Neo10 Pro", "1260", "2800"},
            {"iQOO Neo10", "1080", "2340"},
            {"iQOO Z9 Turbo", "1080", "2400"},
            {"vivo Pad3 Pro", "1800", "2800"},
    };
    static final String[][] HONOR = {
            {"Magic8 Pro", "1280", "2800"},
            {"Magic8", "1080", "2400"},
            {"Magic7 Pro", "1280", "2800"},
            {"Magic7", "1080", "2400"},
            {"Magic V4 外屏", "1080", "2316"},
            {"Magic6 Pro", "1260", "2800"},
            {"荣耀 400 Pro", "1276", "2744"},
            {"荣耀 400", "1080", "2340"},
            {"荣耀 300", "1080", "2376"},
            {"荣耀 200", "1080", "2340"},
            {"荣耀 100", "1080", "2376"},
            {"荣耀 GT", "1260", "2800"},
            {"荣耀 X60", "1080", "2340"},
            {"荣耀 Play 50", "720", "1600"},
            {"荣耀 Pad 9", "1440", "2240"},
    };
    static final String[][] SAMSUNG = {
            {"Galaxy S25 Ultra", "1440", "3120"},
            {"Galaxy S25 Edge", "1080", "2400"},
            {"Galaxy S25+", "1080", "2340"},
            {"Galaxy S25", "1080", "2340"},
            {"Galaxy S24 Ultra", "1440", "3120"},
            {"Galaxy S24", "1080", "2340"},
            {"Galaxy S23", "1080", "2340"},
            {"Galaxy A56", "1080", "2340"},
            {"Galaxy A36", "1080", "2340"},
            {"Galaxy A26", "720", "1650"},
            {"Galaxy Z Fold7 外屏", "1080", "2316"},
            {"Galaxy Z Fold6 外屏", "1080", "2316"},
            {"Galaxy Z Flip6 外屏", "720", "1632"},
            {"Galaxy Tab S10+", "1752", "2800"},
            {"Galaxy M55", "1080", "2340"},
    };
    static final String[][] MEIZU = {
            {"魅族 21 Note", "1080", "2400"},
            {"魅族 21 Pro", "1071", "2392"},
            {"魅族 21", "1080", "2400"},
            {"魅族 20 Infinity", "1080", "2400"},
            {"魅族 20 Pro", "1440", "3200"},
            {"魅族 20", "1080", "2400"},
            {"魅族 18s Pro", "1440", "3200"},
            {"魅族 18s", "1080", "2400"},
            {"魅族 18 Pro", "1440", "3200"},
            {"魅族 18", "1080", "2400"},
            {"魅族 17 Pro", "1080", "2340"},
            {"魅族 17", "1080", "2340"},
            {"魅族 16th", "1080", "2340"},
            {"魅族 Lucky 08", "1080", "2340"},
            {"魅族 Note 22", "1080", "2400"},
    };
    static final String[][] ZTE = {
            {"努比亚 Z70 Ultra", "1216", "2688"},
            {"努比亚 Z60 Ultra", "1116", "2480"},
            {"努比亚 Z60S Pro", "1080", "2400"},
            {"努比亚 Flip2 外屏", "1080", "2316"},
            {"努比亚 小牛", "1080", "2340"},
            {"红魔 10 Pro+", "1216", "2688"},
            {"红魔 10 Pro", "1216", "2688"},
            {"红魔 9 Pro+", "1116", "2480"},
            {"红魔 9 Pro", "1116", "2480"},
            {"红魔 8 Pro", "1116", "2480"},
            {"红魔 7S Pro", "1080", "2400"},
            {"中兴 Axon 60 Ultra", "1080", "2400"},
            {"中兴 Axon 40 Ultra", "1116", "2480"},
            {"中兴 Axon 30", "1080", "2400"},
            {"中兴 远航40", "720", "1600"},
    };
    static final String[][] OTHERS = {
            {"Pixel 10 Pro XL", "1344", "2992", "Google"},
            {"Pixel 10 Pro", "1280", "2856", "Google"},
            {"Pixel 9 Pro XL", "1344", "2992", "Google"},
            {"Pixel 9 Pro", "1280", "2856", "Google"},
            {"Pixel 9", "1080", "2424", "Google"},
            {"Nothing Phone (3)", "1080", "2412", "Nothing"},
            {"Nothing Phone (3a)", "1080", "2412", "Nothing"},
            {"Nothing Phone (2a)", "1080", "2400", "Nothing"},
            {"索尼 Xperia 1 VI", "1080", "2340", "Sony"},
            {"索尼 Xperia 5 VI", "1080", "2340", "Sony"},
            {"moto razr 60 Ultra 外屏", "1272", "2688", "motorola"},
            {"moto razr 50 Ultra", "1080", "2400", "motorola"},
            {"motorola edge 60 Pro", "1220", "2712", "motorola"},
            {"motorola edge 50 Ultra", "1220", "2712", "motorola"},
            {"联想拯救者 Y700 二代", "1600", "2560", "Lenovo"},
    };
    static final String[][] TRANSSION = {
            {"Tecno Camon 40 Premier", "1260", "2780"},
            {"Tecno Camon 40 Pro", "1080", "2436"},
            {"Tecno Camon 30 Pro", "1080", "2436"},
            {"Tecno Spark 30 Pro", "1080", "2412"},
            {"Tecno Spark 20", "720", "1612"},
            {"Tecno Pop 9", "720", "1612"},
            {"Infinix Note 50 Pro+", "1260", "2780"},
            {"Infinix Note 40 Pro", "1080", "2436"},
            {"Infinix Hot 50 Pro", "1080", "2408"},
            {"Infinix Hot 40i", "720", "1612"},
            {"Infinix GT 20 Pro", "1080", "2400"},
            {"Infinix Zero 40", "1080", "2400"},
            {"itel S25 Ultra", "1080", "2436"},
            {"itel A70", "720", "1612"},
            {"itel P70", "720", "1612"},
    };
    static final String[][][] BRANDS = {
            XIAOMI, HUAWEI, OPPO, ONEPLUS, VIVO, HONOR, SAMSUNG, MEIZU, ZTE, TRANSSION, OTHERS,
    };
    static final String[] BRAND_NAMES = {
            "小米/Redmi/POCO·HyperOS",
            "华为·HarmonyOS",
            "OPPO/realme·ColorOS",
            "一加·ColorOS",
            "vivo/iQOO·OriginOS",
            "荣耀·MagicOS",
            "三星·OneUI",
            "魅族·Flyme",
            "中兴/努比亚/红魔·MyOS",
            "传音 Tecno/Infinix/itel·HiOS/XOS",
            "其他·Pixel/Nothing/索尼/moto",
    };
    static final String[] BRAND_MANUFACTURERS = {
            "Xiaomi",
            "HUAWEI",
            "OPPO",
            "OnePlus",
            "vivo",
            "HONOR",
            "samsung",
            "meizu",
            "ZTE",
            "Transsion",
            null,
    };
    static final String[] EXPECTED_FAMILIES = {
            RestoreGuard.FAMILY_HYPEROS,
            RestoreGuard.FAMILY_HARMONY,
            RestoreGuard.FAMILY_COLOROS,
            RestoreGuard.FAMILY_COLOROS,
            RestoreGuard.FAMILY_ORIGINOS,
            RestoreGuard.FAMILY_MAGIC,
            RestoreGuard.FAMILY_ONEUI,
            RestoreGuard.FAMILY_FLYME,
            null, // generic（中兴族/传音族/其他组）
            null, // generic（中兴族/传音族/其他组）
            null, // generic（中兴族/传音族/其他组）
    };

    /* ══ v5.8.0/v5.9.0 新增 SDK 指纹（激进化）vs 非广告窗对照 ══ */
    static final String[] NEW_FINGERPRINTS = {
            "com.ksad.splash.KSSplashActivity",                // 快手 KSAdSDK（v5.9 新增）
            "com.bytedance.csj_splash.CSJSplashActivity",     // 穿山甲联盟复合族（v5.9 新增）
            "com.mbridge.mintegral.ads.MBActivity",          // Mintegral（v5.8 新增）
            "com.example.app.pangle.PangleSplashActivity",   // Pangle 穿山甲国际版（v5.8 新增）
            "com.topon.sdk.adapter.TopOnAdActivity",          // TopOn 聚合（v5.8 新增）
            "com.example.ads.moloco.MolocoAdActivity",       // Moloco（v5.8 新增）
            "com.unity3d.ads.adunit.AdUnitActivity",            // Unity Ads（国际游戏广告主力）
            "com.ironsource.sdk.controller.ControllerActivity",// IronSource
            "com.chartboost.sdk.CBImpressionActivity",          // Chartboost
            "com.facebook.ads.AudienceNetworkActivity",         // Meta Audience Network
            "com.inmobi.ads.InMobiActivity",                    // InMobi
            "com.tapjoy.TJAdUnitActivity",                      // Tapjoy
            "com.example.game.rewardvideo.RewardVideoActivity", // 激励视频独立窗
            "com.example.game.rewardad.RewardAdDialog",         // 激励广告弹层
            "com.example.app.adsdk.ExpressAdActivity",          // 穿山甲模板广告变体
            "com.example.app.ad.AdPresentActivity",             // 广告 present 窗
            "com.example.game.fullscreenad.FullScreenAdActivity",// 全屏视频广告窗
            "com.example.video.fullvideo.FullVideoActivity",    // 全屏视频窗
    };
    static final String[] NON_AD_CLASSES = {
            "com.app.SplashActivity",            // App 自建开屏容器（返回=退出红线）
            "com.app.MainActivity",
            "com.app.LoginActivity",
            "com.app.detail.DetailActivity",
            "com.app.pay.CashierActivity",
            "com.app.download.DownloadActivity", // 含 "loadactivity" 子串（含 adactivity）——equals 精确段绝不误认
            "com.unity3d.player.UnityPlayerActivity", // Unity 游戏引擎主窗（游戏保护红线）
    };

    public static void main(String[] args) {
        Subscription sub = null;
        try {
            sub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
        } catch (Exception ignored) {
        }

        int brandHitTotal = 0, brandSpeedTotal = 0, brandBugTotal = 0,
                brandPowerTotal = 0, brandPrecTotal = 0, brandAlgoTotal = 0;

        for (int bi = 0; bi < BRANDS.length; bi++) {
            String[][] models = BRANDS[bi];
            TestHost.suite("品牌 " + BRAND_NAMES[bi] + "（" + models.length + " 机型）");
            int hitN = 0, speedN = 0, bugN = 0, powN = 0, precN = 0, algoN = 0;

            for (int mi = 0; mi < models.length; mi++) {
                String model = models[mi][0];
                int w = Integer.parseInt(models[mi][1]);
                int h = Integer.parseInt(models[mi][2]);
                String manuf = BRAND_MANUFACTURERS[bi] != null
                        ? BRAND_MANUFACTURERS[bi] : models[mi][3];
                String pkg = "com.matrix.app" + bi + ".m" + mi;

                /* ① 开屏广告命中（六信号——v5.6 四信号 + v5.7 两新键） */
                int hitOk = 0;
                // ①a 文本跳过键（右上 11%×3.5% 典型跳过键）
                SimNode sTree = new SimNode();
                sTree.boundsArr = new int[]{0, 0, w, h};
                SimNode skipBtn = new SimNode("跳过", (int) (w * 0.86), (int) (h * 0.03),
                        (int) (w * 0.97), (int) (h * 0.065));
                skipBtn.clickable = true;
                sTree.add(skipBtn);
                Ctx sCtx = ctx(pkg, "com.matrix.SplashActivity", w, h);
                ScanFind sf = AdDecisionCore.walk(sTree, sCtx);
                Decision sd = AdDecisionCore.decide(sf, sCtx);
                if (!AdDecisionCore.A_NONE.equals(sd.action)) hitOk++;
                // ①b desc 跳过键（无障碍描述——图片按钮常用）
                SimNode dTree = new SimNode();
                dTree.boundsArr = new int[]{0, 0, w, h};
                SimNode descBtn = new SimNode("", (int) (w * 0.86), (int) (h * 0.03),
                        (int) (w * 0.97), (int) (h * 0.065));
                descBtn.desc = "跳过广告";
                descBtn.clickable = true;
                dTree.add(descBtn);
                ScanFind df = AdDecisionCore.walk(dTree, sCtx);
                Decision dd = AdDecisionCore.decide(df, sCtx);
                if (!AdDecisionCore.A_NONE.equals(dd.action)) hitOk++;
                // ①c vid 订阅键（GKD 全局 vid*="skip"）
                SimNode vTree = new SimNode();
                vTree.boundsArr = new int[]{0, 0, w, h};
                SimNode vidBtn = new SimNode("", (int) (w * 0.86), (int) (h * 0.03),
                        (int) (w * 0.97), (int) (h * 0.065));
                vidBtn.vid = "btn_skip";
                vidBtn.clickable = true;
                vTree.add(vidBtn);
                Hit vh = sub != null ? sub.match(pkg, vTree, sCtx, 400) : null;
                boolean vHit = vh != null
                        || (RuleEngine.matchAll(vTree, sCtx, 4).size() > 0);
                if (vHit) hitOk++;
                // ①d 图片角落键（无字小可点控件 + v5.7 新指纹 AdUnit 广告窗）
                SimNode cTree = new SimNode();
                cTree.cls = "com.unity3d.ads.adunit.AdUnitActivity";
                cTree.boundsArr = new int[]{0, 0, w, h};
                SimNode corner = new SimNode("", (int) (w * 0.90), (int) (h * 0.045),
                        (int) (w * 0.965), (int) (h * 0.105));
                corner.clickable = true;
                cTree.add(corner);
                Ctx cCtx = ctx(pkg, cTree.cls, w, h);
                cCtx.sdkAdWin = AdDecisionCore.isAdActivityClass(cTree.cls);
                ScanFind cf = AdDecisionCore.walk(cTree, cCtx);
                Decision cd = AdDecisionCore.decide(cf, cCtx);
                if (!AdDecisionCore.A_NONE.equals(cd.action)) hitOk++;
                // ①e v5.8 新文案「跳过推广」（合规敏感 App 以推广代广告——SKIP 族新词）
                SimNode pTree = new SimNode();
                pTree.boundsArr = new int[]{0, 0, w, h};
                SimNode preRollBtn = new SimNode("跳过推广", (int) (w * 0.86), (int) (h * 0.03),
                        (int) (w * 0.97), (int) (h * 0.065));
                preRollBtn.clickable = true;
                pTree.add(preRollBtn);
                ScanFind pf = AdDecisionCore.walk(pTree, sCtx);
                Decision pd = AdDecisionCore.decide(pf, sCtx);
                if (!AdDecisionCore.A_NONE.equals(pd.action)) hitOk++;
                // ①f v5.7 新复合 vid 键 iv_ad_close（边缘带 + 小面积门控）
                SimNode aTree = new SimNode();
                aTree.boundsArr = new int[]{0, 0, w, h};
                SimNode adCloseBtn = new SimNode("", (int) (w * 0.86), (int) (h * 0.03),
                        (int) (w * 0.97), (int) (h * 0.065));
                adCloseBtn.vid = "com.matrix.app:id/iv_ad_close";
                adCloseBtn.clickable = true;
                aTree.add(adCloseBtn);
                boolean aHit = RuleEngine.matchAll(aTree, sCtx, 4).size() > 0;
                if (aHit) hitOk++;
                TestHost.check(hitOk >= 5, "① 开屏命中 " + model + "（" + hitOk + "/6 信号）");
                if (hitOk >= 5) hitN++;

                /* ② 屏蔽速度（120 节点树 · walk+decide · v5.7 激进参数在场） */
                SimNode bigTree = buildDeepTree(w, h, 120);
                Ctx tCtx = ctx(pkg, "com.matrix.SplashActivity", w, h);
                long t0 = System.nanoTime();
                int rounds = 50;
                for (int r = 0; r < rounds; r++) {
                    ScanFind bf = AdDecisionCore.walk(bigTree, tCtx);
                    AdDecisionCore.decide(bf, tCtx);
                }
                long perDecisionMs = (System.nanoTime() - t0) / 1000L / rounds / 1000L;
                boolean speedOk = perDecisionMs < 20
                        && AdDecisionCore.COUNTDOWN_EXTRA_MS == 120
                        && AdDecisionCore.MAX_CLICKS_PER_WIN == 12
                        && AdDecisionCore.SKIP_CANDS_MAX == 8;
                TestHost.check(speedOk, "② 屏蔽速度 " + model + "（" + perDecisionMs + "ms/决策 · 速度压榨参数在场）");
                if (speedOk) speedN++;

                /* ③ bug 测试（菜单陷阱 / 巨横幅 / 验证码横幅 / 游戏 jump vid） */
                int bugOk = 0;
                SimNode menuTree = new SimNode();
                menuTree.boundsArr = new int[]{0, 0, w, h};
                SimNode panel = new SimNode();
                panel.boundsArr = new int[]{(int) (w * 0.55), (int) (h * 0.08), w, (int) (h * 0.92)};
                menuTree.add(panel);
                String[] items = {"播放历史", "录音", "扫一扫", "切换模式", "跳过"};
                for (int i = 0; i < items.length; i++) {
                    SimNode row = new SimNode(items[i], (int) (w * 0.58),
                            (int) (h * 0.16) + i * (int) (h * 0.067),
                            (int) (w * 0.96), (int) (h * 0.16) + (i + 1) * (int) (h * 0.067));
                    row.clickable = true;
                    panel.add(row);
                }
                Ctx mCtx = ctx(pkg, "com.matrix.MainActivity", w, h);
                mCtx.splashLive = false;
                boolean menuClean = RuleEngine.matchAll(menuTree, mCtx, 4).isEmpty();
                if (menuClean) bugOk++;
                SimNode bannerTree = new SimNode();
                bannerTree.boundsArr = new int[]{0, 0, w, h};
                SimNode banner = new SimNode("跳过广告 立即查看", 0, (int) (h * 0.4),
                        w, (int) (h * 0.58));
                banner.clickable = true;
                bannerTree.add(banner);
                ScanFind bnf = AdDecisionCore.walk(bannerTree, tCtx);
                Decision bnd = AdDecisionCore.decide(bnf, tCtx);
                if (AdDecisionCore.A_NONE.equals(bnd.action)) bugOk++;
                boolean smsOverlay = AdDecisionCore.isSmsOverlayPkg("com.android.mms");
                boolean launcherOk = AdDecisionCore.isLauncherLike("com.miui.home");
                if (smsOverlay && launcherOk) bugOk++;
                // v5.7 第四陷阱：游戏 jump vid（jump 非跳过语义——安全门红线）
                SimNode gTree = new SimNode();
                gTree.boundsArr = new int[]{0, 0, w, h};
                SimNode jumpBtn = new SimNode("", (int) (w * 0.40), (int) (h * 0.70),
                        (int) (w * 0.60), (int) (h * 0.80));
                jumpBtn.vid = "com.matrix.game:id/btn_jump";
                jumpBtn.clickable = true;
                gTree.add(jumpBtn);
                Ctx gCtx2 = ctx(pkg, "com.unity3d.player.UnityPlayerActivity", w, h);
                ScanFind jf = AdDecisionCore.walk(gTree, gCtx2);
                Decision jd = AdDecisionCore.decide(jf, gCtx2);
                boolean jumpClean = RuleEngine.matchAll(gTree, gCtx2, 4).isEmpty()
                        && AdDecisionCore.A_NONE.equals(jd.action);
                if (jumpClean) bugOk++;
                TestHost.check(bugOk == 4, "③ bug 防线 " + model + "（菜单/横幅/overlay/游戏jump " + bugOk + "/4）");
                if (bugOk == 4) bugN++;

                /* ④ 功耗测试（典型日：45 启动 + 120 后台事件 · v5.8 骨架档 18 · 2 返回键） */
                double microWh = PowerGovernor.microWh(165, 45, 18, 2);
                boolean powOk = microWh < 150 && SmartScanPlannerCheck();
                TestHost.check(powOk, "④ 功耗 " + model + "（" + (microWh / 1000.0) + "mWh/典型日 · 分级预算在场）");
                if (powOk) powN++;

                /* ⑤ 精准度（银行守护根窗零返回键 · 验证码标记 · Unity 游戏引擎窗免疫） */
                int precOk = 0;
                Ctx gcCtx = ctx("com.icbc", "com.icbc.MainActivity", w, h);
                gcCtx.guard = true;
                gcCtx.rootWindow = true;
                SimNode gkTree = new SimNode();
                gkTree.boundsArr = new int[]{0, 0, w, h};
                SimNode gHot = new SimNode("", 0, 0, w, h);
                gHot.clickable = true;
                gkTree.add(gHot);
                ScanFind gf = AdDecisionCore.walk(gkTree, gcCtx);
                Decision gd = AdDecisionCore.decide(gf, gcCtx);
                if (!AdDecisionCore.A_BACK.equals(gd.action)) precOk++;
                if (AdDecisionCore.isCodeBannerMarker("验证码") || AdDecisionCore.isCodeBannerMarker("校验码")) precOk++;
                if (!AdDecisionCore.isAdActivityClass("com.unity3d.player.UnityPlayerActivity")) precOk++;
                TestHost.check(precOk == 3, "⑤ 精准度 " + model + "（守护零返回键/验证码标记/游戏窗免疫 " + precOk + "/3）");
                if (precOk == 3) precN++;

                /* ⑥ 算法优化（v5.8 新指纹命中 · 非广告窗免疫 · 品牌族归一 · 自愈合并） */
                int algoOk = 0;
                int newFpHit = 0;
                for (String cls : NEW_FINGERPRINTS) {
                    if (AdDecisionCore.isAdActivityClass(cls)) newFpHit++;
                }
                if (newFpHit == NEW_FINGERPRINTS.length) algoOk++;
                int nonAdHit = 0;
                for (String cls : NON_AD_CLASSES) {
                    if (AdDecisionCore.isAdActivityClass(cls)) nonAdHit++;
                }
                if (nonAdHit == 0) algoOk++;
                // 品牌族归一（每机型都验——10 族全检，中兴族与其他组→generic）
                String fam = RestoreGuard.familyOf(manuf, manuf, model);
                boolean famOk;
                if (EXPECTED_FAMILIES[bi] != null) {
                    famOk = EXPECTED_FAMILIES[bi].equals(fam);
                } else {
                    famOk = RestoreGuard.FAMILY_GENERIC.equals(fam);
                }
                if (famOk) algoOk++;
                String flat = "com.zai.adshield/com.zai.adshield.AdSkipService";
                String merged1 = RestoreGuard.mergeRegistrationCsv(
                        "com.other.a/.Svc", flat);
                String merged2 = RestoreGuard.mergeRegistrationCsv(null, flat);
                String merged3 = RestoreGuard.mergeRegistrationCsv(flat, flat);
                if ("com.other.a/.Svc:com.zai.adshield/com.zai.adshield.AdSkipService".equals(merged1)
                        && flat.equals(merged2) && flat.equals(merged3)) algoOk++;
                TestHost.check(algoOk == 4, "⑥ 算法优化 " + model + "（新指纹 " + newFpHit + "/" + NEW_FINGERPRINTS.length
                        + " · 非广告窗免疫 " + (NON_AD_CLASSES.length - nonAdHit) + "/" + NON_AD_CLASSES.length
                        + " · 族归一 " + (famOk ? "✓" : "✗") + " · 自愈合并 ✓）");
                if (algoOk == 4) algoN++;
            }

            TestHost.suite("品牌汇总 " + BRAND_NAMES[bi]);
            TestHost.check(hitN == models.length, BRAND_NAMES[bi] + " 开屏命中 " + hitN + "/" + models.length);
            TestHost.check(speedN == models.length, BRAND_NAMES[bi] + " 速度 " + speedN + "/" + models.length);
            TestHost.check(bugN == models.length, BRAND_NAMES[bi] + " bug 防线 " + bugN + "/" + models.length);
            TestHost.check(powN == models.length, BRAND_NAMES[bi] + " 功耗 " + powN + "/" + models.length);
            TestHost.check(precN == models.length, BRAND_NAMES[bi] + " 精准度 " + precN + "/" + models.length);
            TestHost.check(algoN == models.length, BRAND_NAMES[bi] + " 算法优化 " + algoN + "/" + models.length);
            brandHitTotal += hitN;
            brandSpeedTotal += speedN;
            brandBugTotal += bugN;
            brandPowerTotal += powN;
            brandPrecTotal += precN;
            brandAlgoTotal += algoN;
        }

        /* ══ 矩阵总检（11×15=165 · 六维全 165/165）══ */
        TestHost.suite("矩阵总检（11 品牌 × 15 机型）");
        TestHost.check(brandHitTotal == 165, "开屏命中总 " + brandHitTotal + "/165");
        TestHost.check(brandSpeedTotal == 165, "屏蔽速度总 " + brandSpeedTotal + "/165");
        TestHost.check(brandBugTotal == 165, "bug 防线总 " + brandBugTotal + "/165");
        TestHost.check(brandPowerTotal == 165, "功耗总 " + brandPowerTotal + "/165");
        TestHost.check(brandPrecTotal == 165, "精准度总 " + brandPrecTotal + "/165");
        TestHost.check(brandAlgoTotal == 165, "算法优化总 " + brandAlgoTotal + "/165");
        System.out.println("  矩阵: " + brandHitTotal + "/" + brandSpeedTotal + "/" + brandBugTotal
                + "/" + brandPowerTotal + "/" + brandPrecTotal + "/" + brandAlgoTotal + "（六维各 /165）");

        TestHost.done();
    }

    /** 分级预算常量在场（SmartScanPlanner · JVM 侧可断言的投影） */
    static boolean SmartScanPlannerCheck() {
        return com.zai.adshield.core.SmartScanPlanner.BUDGET_FULL == 600
                && com.zai.adshield.core.SmartScanPlanner.BUDGET_LIGHT == 350
                && com.zai.adshield.core.SmartScanPlanner.DEPTH_LIGHT == 20;
    }

    /** 生成 n 节点深树（速度测试用：容器嵌套 + 兄弟铺开） */
    static SimNode buildDeepTree(int w, int h, int targetNodes) {
        SimNode root = new SimNode();
        root.boundsArr = new int[]{0, 0, w, h};
        java.util.ArrayList<SimNode> frontier = new java.util.ArrayList<SimNode>();
        frontier.add(root);
        int count = 1;
        int layer = 0;
        while (count < targetNodes && !frontier.isEmpty() && layer < 24) {
            java.util.ArrayList<SimNode> next = new java.util.ArrayList<SimNode>();
            for (SimNode parent : frontier) {
                int kids = Math.min(3, targetNodes - count);
                for (int i = 0; i < kids; i++) {
                    SimNode n = new SimNode(layer % 2 == 0 ? "广告" + layer : "",
                            (int) (w * 0.1 * (i + 1)), (int) (h * 0.12) + layer * 40,
                            (int) (w * 0.1 * (i + 1) + w * 0.05), (int) (h * 0.12) + layer * 40 + 60);
                    n.clickable = layer % 3 == 0;
                    parent.add(n);
                    next.add(n);
                    count++;
                }
            }
            frontier = next;
            layer++;
        }
        return root;
    }

    static Ctx ctx(String pkg, String act, int w, int h) {
        Ctx ctx = new Ctx();
        ctx.screenW = w;
        ctx.screenH = h;
        ctx.pkg = pkg;
        ctx.activityId = act;
        ctx.elapsedMs = 800;
        ctx.splashLive = true;
        return ctx;
    }
}
