package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.MiniProgramGuard;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * MiniProgramGuard 单元测试（v5.0.0 · 核心新增能力）。
 * 覆盖：抖音团购容器全家桶 / 微信支付宝QQ 小程序 / 通用标识 /
 * issue#45 实锤的 MiniAppChildProcessMultiInsActivity0 /
 * 树标识兜底 / 负例（开屏/主界面绝不误保护）。
 */
public final class MiniProgramGuardTest {

    public static void main(String[] args) {
        TestHost.suite("MiniProgramGuard · 容器识别（快路径）");

        String dy = "com.ss.android.ugc.aweme";
        // 用户实锤的抖音团购券/小程序容器
        TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy,
                "com.ss.android.ugc.aweme.miniapp.ui.MiniAppActivity"), "抖音 MiniAppActivity");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy,
                "com.ss.android.ugc.aweme.commercialize.CommercializeContainerActivity"),
                "抖音 CommercializeContainerActivity（直播团购容器）");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy,
                "com.bytedance.privilege.PrivilegeDetailActivity"), "抖音 PrivilegeDetailActivity（团购券详情）");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy,
                "com.ss.android.ugc.aweme.ecom.EcPromotionActivity"), "抖音 EcPromotionActivity");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy,
                "com.ss.android.ugc.aweme.ecom.UnionPromotionActivity"), "抖音 UnionPromotionActivity");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy,
                "com.ss.android.ugc.aweme.lynx.LynxContainerActivity"), "抖音 LynxContainerActivity（团购落地页）");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy,
                "com.bytedance.bae.ui.BaeActivity"), "抖音 bae 小程序运行时");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy,
                "com.ss.android.ugc.aweme.web.WebBrowserActivity"), "抖音内置浏览器");

        // 抖音极速版（不同包名）
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.ss.android.ugc.aweme.lite",
                "com.ss.android.ugc.aweme.lite.miniapp.MiniAppActivity"), "抖音极速版 miniapp");

        // issue#45 实锤：MIUI lifeservices 里出现的多实例小程序窗
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.miui.lifeservices",
                "MiniAppChildProcessMultiInsActivity0"), "issue#45 MiniAppChildProcessMultiInsActivity0");

        // 快手
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.smile.gifmaker",
                "com.smile.gifmaker.miniapp.MiniAppActivity"), "快手小程序");

        // 微信小程序（AppBrandUI）；主界面 LauncherUI 明确放行
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.tencent.mm",
                "com.tencent.mm.plugin.appbrand.ui.AppBrandUI"), "微信 AppBrandUI");
        TestHost.check(!MiniProgramGuard.isMiniProgramWindow("com.tencent.mm",
                "com.tencent.mm.ui.LauncherUI"), "微信主界面不误保护（弹窗路径保留）");

        // 支付宝 Nebula
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.eg.android.AlipayGphone",
                "com.alipay.mobile.nebulacore.ui.H5Activity"), "支付宝 Nebula H5Activity");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.eg.android.AlipayGphone",
                "com.alipay.mobile.tinycloud.ui.TinyCloudActivity"), "支付宝 tinycloud");

        // QQ 小程序
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.tencent.mobileqq",
                "com.tencent.qqminiapp.MiniAppActivity"), "QQ 小程序");

        // 任意包的通用容器标识
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.unknown.app",
                "com.unknown.app.miniprogram.MainActivity"), "通用 miniprogram 标识");
        TestHost.check(MiniProgramGuard.isMiniProgramWindow("com.some.vendor",
                "com.some.vendor.mini_app.Container"), "通用 mini_app 标识");

        TestHost.suite("MiniProgramGuard · 负例（广告窗绝不误放行）");
        // 抖音开屏广告窗（合法跳过目标——绝不能被保护）
        TestHost.check(!MiniProgramGuard.isMiniProgramWindow(dy,
                "com.ss.android.ugc.aweme.splash.SplashActivity"), "抖音 SplashActivity 不保护（开屏广告在跳）");
        TestHost.check(!MiniProgramGuard.isMiniProgramWindow(dy,
                "com.ss.android.ugc.aweme.main.MainActivity"), "抖音 MainActivity 不保护");
        // 穿山甲 SDK 广告窗（合法跳过目标）
        TestHost.check(!MiniProgramGuard.isMiniProgramWindow("com.ss.android.ugc.aweme",
                "com.bytedance.sdk.openadsdk.activity.TTMainActivity"), "穿山甲广告窗不保护");
        // null 安全
        TestHost.check(!MiniProgramGuard.isMiniProgramWindow(null, "com.x.MiniAppActivity"), "null 包安全");
        TestHost.check(!MiniProgramGuard.isMiniProgramWindow(dy, null), "null 类名安全");
        TestHost.check(!MiniProgramGuard.isMiniProgramWindow(dy, ""), "空类名安全");

        TestHost.suite("MiniProgramGuard · 树标识兜底（漏网容器）");
        // 树内 miniapp vid → 容器
        SimNode root = new SimNode();
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode container = new SimNode();
        container.vid = "com.ss.android.ugc.aweme:id/miniapp_container";
        container.boundsArr = new int[]{0, 0, 1080, 2000};
        root.add(container);
        TestHost.check(MiniProgramGuard.treeHasMiniProgramMarker(root, 80), "树内 miniapp vid 兜底");

        // 树内「小程序」标题 → 容器
        SimNode root2 = new SimNode();
        root2.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode title = new SimNode("小程序", 400, 100, 680, 180);
        root2.add(title);
        TestHost.check(MiniProgramGuard.treeHasMiniProgramMarker(root2, 80), "树内「小程序」标题兜底");

        // 普通广告树 → 不兜底（绝不误伤正常开屏）
        SimNode root3 = new SimNode();
        root3.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skip = TestHost.skipButton("跳过");
        SimNode ad = new SimNode("广告", 0, 0, 1080, 2200);
        ad.vid = "com.app:id/ad_view";
        root3.add(ad);
        root3.add(skip);
        TestHost.check(!MiniProgramGuard.treeHasMiniProgramMarker(root3, 80), "普通广告树不误报");

        // 预算与 null 安全
        TestHost.check(!MiniProgramGuard.treeHasMiniProgramMarker(null, 80), "null 树安全");
        TestHost.check(!MiniProgramGuard.treeHasMiniProgramMarker(root, 0), "零预算安全");

        TestHost.done();
    }
}
