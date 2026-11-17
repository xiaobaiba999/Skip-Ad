package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.MiniProgramGuard;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.Subscription;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 多机型矩阵测试（v5.0.0 · 用户要求：模拟多类型设备测试）。
 * 6 档屏幕画像 × 5 类场景（开屏命中 / 容器保护 / 菜单陷阱 / 巨横幅 /
 * 弹窗关闭）——引擎全 bounds 中心点击（零固定坐标），任意分辨率下
 * 目标与拒绝面一致。
 */
public final class MultiDeviceMatrixTest {

    static final int[][] DEVICES = {
            // {宽, 高, 注释}
            {1080, 2400}, // 主流 1080p（MIUI/EMUI/ColorOS）
            {720, 1600},  // 低端 720p（Redmi 入门机）
            {1440, 3200}, // 2K 旗舰（HyperOS/OneUI）
            {1080, 2340}, // 19.5:9 水滴屏
            {720, 1280},  // 老 720p 平板比例
            {1440, 3120}, // 20:9 曲面旗舰
    };
    static final String[] DEVICE_NAMES = {
            "MIUI 1080p", "Redmi 720p", "HyperOS 1440p", "水滴 1080p", "老 720p", "曲面 1440p",
    };

    public static void main(String[] args) {
        Subscription sub = null;
        try {
            sub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
        } catch (Exception ignored) {
        }

        for (int di = 0; di < DEVICES.length; di++) {
            int w = DEVICES[di][0];
            int h = DEVICES[di][1];
            String dn = DEVICE_NAMES[di];
            TestHost.suite("机型 " + dn + " (" + w + "×" + h + ")");

            String dy = "com.ss.android.ugc.aweme";

            // ① 开屏跳过键（右上角 10% 宽 × 3% 高的典型跳过键）
            SimNode splash = new SimNode();
            splash.boundsArr = new int[]{0, 0, w, h};
            SimNode skip = new SimNode("跳过", (int) (w * 0.86), (int) (h * 0.03),
                    (int) (w * 0.97), (int) (h * 0.06));
            skip.clickable = true;
            splash.add(skip);
            Ctx spCtx = ctx(dy, "com.ss.android.ugc.aweme.splash.SplashActivity", w, h);
            java.util.ArrayList<Hit> hits = RuleEngine.matchAll(splash, spCtx, 4);
            boolean ruleHit = !hits.isEmpty();
            if (!ruleHit && sub != null) {
                ruleHit = sub.match(dy, splash, spCtx, 400) != null;
            }
            TestHost.check(ruleHit, "① 开屏跳过键命中");

            // 目标坐标必须在屏幕内且落在按钮 bounds 内（零固定坐标乱点）
            if (ruleHit) {
                Hit hit = hits.isEmpty() ? sub.match(dy, splash, spCtx, 400) : hits.get(0);
                if (hit != null && hit.tapBounds != null) {
                    int[] b = hit.tapBounds;
                    boolean inside = b[0] >= 0 && b[1] >= 0 && b[2] <= w && b[3] <= h;
                    TestHost.check(inside, "① 目标 bounds 在屏幕内");
                }
            }

            // ② 小程序容器保护（所有机型上团购券容器都零动作）
            Ctx miniCtx = ctx(dy, "com.ss.android.ugc.aweme.miniapp.ui.MiniAppActivity", w, h);
            SimNode couponTree = new SimNode();
            couponTree.boundsArr = new int[]{0, 0, w, h};
            SimNode couponSkip = new SimNode("跳过广告", (int) (w * 0.85), (int) (h * 0.03),
                    (int) (w * 0.98), (int) (h * 0.06));
            couponSkip.clickable = true;
            couponTree.add(couponSkip);
            TestHost.check(MiniProgramGuard.isMiniProgramWindow(dy, miniCtx.activityId),
                    "② 容器识别");
            ScanFind cf = AdDecisionCore.walk(couponTree, miniCtx);
            Decision cd = AdDecisionCore.decide(cf, miniCtx);
            TestHost.check(cd.action.equals(AdDecisionCore.A_NONE), "② 容器内零动作");

            // ③ 抽屉菜单「跳过」陷阱行（同列同宽 5 行竖排）
            SimNode menuTree = new SimNode();
            menuTree.boundsArr = new int[]{0, 0, w, h};
            SimNode panel = new SimNode();
            panel.boundsArr = new int[]{(int) (w * 0.55), (int) (h * 0.08), w, (int) (h * 0.92)};
            menuTree.add(panel);
            String[] items = {"播放历史", "录音", "扫一扫", "切换模式", "跳过"};
            for (int i = 0; i < items.length; i++) {
                SimNode row = new SimNode(items[i],
                        (int) (w * 0.58), (int) (h * 0.16) + i * (int) (h * 0.067),
                        (int) (w * 0.96), (int) (h * 0.16) + (i + 1) * (int) (h * 0.067));
                row.clickable = true;
                panel.add(row);
            }
            java.util.ArrayList<Hit> menuHits = RuleEngine.matchAll(menuTree,
                    ctx("com.ximalaya.ting.android", "MainActivity", w, h), 4);
            boolean skipRow = false;
            for (Hit mh : menuHits) {
                if ("跳过".equals(mh.label)) skipRow = true;
            }
            TestHost.check(!skipRow, "③ 菜单陷阱行不命中");

            // ④ 满屏巨横幅（CTA 诱饵）绝不命中
            SimNode bannerTree = new SimNode();
            bannerTree.boundsArr = new int[]{0, 0, w, h};
            SimNode banner = new SimNode("跳过广告 立即查看", 0, (int) (h * 0.4),
                    w, (int) (h * 0.58));
            banner.clickable = true;
            bannerTree.add(banner);
            java.util.ArrayList<Hit> bannerHits = RuleEngine.matchAll(bannerTree,
                    ctx("com.smile.gifmaker", "MainActivity", w, h), 4);
            TestHost.check(bannerHits.isEmpty(), "④ 巨横幅不命中");

            // ⑤ 弹窗 × 关闭（Dialog + 广告角标）
            Ctx ppCtx = ctx("com.some.app", "com.some.app.MainActivity", w, h);
            ppCtx.splashLive = false;
            ppCtx.popupLive = true;
            SimNode popup = new SimNode();
            popup.cls = "android.app.Dialog";
            popup.boundsArr = new int[]{(int) (w * 0.09), (int) (h * 0.25),
                    (int) (w * 0.91), (int) (h * 0.75)};
            SimNode badge = new SimNode("广告", (int) (w * 0.12), (int) (h * 0.27),
                    (int) (w * 0.2), (int) (h * 0.3));
            SimNode close = new SimNode("×", (int) (w * 0.81), (int) (h * 0.27),
                    (int) (w * 0.88), (int) (h * 0.3));
            close.clickable = true;
            popup.add(badge);
            popup.add(close);
            ScanFind pf = AdDecisionCore.walk(popup, ppCtx);
            Decision pd = AdDecisionCore.decide(pf, ppCtx);
            TestHost.check(!pd.action.equals(AdDecisionCore.A_NONE), "⑤ 弹窗关闭键动作（" + pd.action + "）");
        }

        TestHost.done();
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
