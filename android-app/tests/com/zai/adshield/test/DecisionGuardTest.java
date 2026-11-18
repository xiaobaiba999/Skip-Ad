package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 决策管线小程序守卫测试（v5.0.0 · walk/decide 纵深防御层）。
 * 容器内：满树「跳过」候选也绝不产生动作；正常开屏窗：引擎照常出手（回归）。
 */
public final class DecisionGuardTest {

    static Ctx ctxOf(String pkg, String activity, boolean splash) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = activity;
        ctx.elapsedMs = 600;
        ctx.splashLive = splash;
        return ctx;
    }

    /** 构造一棵「诱人」的树：跳过文本 + 关闭键 + 大热区（广告窗特征齐全）。 */
    static SimNode juicyAdTree() {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skip = TestHost.skipButton("跳过");
        skip.clickable = true;
        SimNode countdown = new SimNode("跳过 3", 900, 80, 1060, 150);
        SimNode close = new SimNode("×", 1000, 90, 1050, 140);
        close.clickable = true;
        SimNode cta = new SimNode("立即领取", 300, 800, 780, 900);
        cta.clickable = true;
        root.add(skip);
        root.add(countdown);
        root.add(close);
        root.add(cta);
        return root;
    }

    public static void main(String[] args) {
        TestHost.suite("决策管线 · 容器内零动作（纵深防御）");

        String dy = "com.ss.android.ugc.aweme";
        String[] containers = {
                "com.ss.android.ugc.aweme.miniapp.ui.MiniAppActivity",
                "com.bytedance.privilege.PrivilegeDetailActivity",
                "com.ss.android.ugc.aweme.commercialize.CommercializeContainerActivity",
                "com.ss.android.ugc.aweme.lynx.LynxContainerActivity",
        };
        for (String act : containers) {
            Ctx ctx = ctxOf(dy, act, true);
            ScanFind f = AdDecisionCore.walk(juicyAdTree(), ctx);
            TestHost.check(f.skipCands.isEmpty(), "walk 容器内零跳过候选 " + act.substring(act.length() - 30));
            TestHost.check(f.corners.isEmpty(), "walk 容器内零角落候选");
            TestHost.check(!f.hotZone, "walk 容器内不识别热区");
            Decision d = AdDecisionCore.decide(f, ctx);
            TestHost.check(A_None(d), "decide 容器内恒 NONE " + act.substring(act.length() - 30));
        }

        // even with a PRE-FILLED ScanFind (rule engine path), decide must refuse
        Ctx ctxMini = ctxOf(dy, "com.ss.android.ugc.aweme.miniapp.ui.MiniAppActivity", true);
        ScanFind stuffed = AdDecisionCore.walk(juicyAdTree(), ctxOf(dy,
                "com.ss.android.ugc.aweme.splash.SplashActivity", true));
        TestHost.check(!stuffed.skipCands.isEmpty(), "预填前提：正常窗能收到候选");
        Decision d2 = AdDecisionCore.decide(stuffed, ctxMini);
        TestHost.check(A_None(d2), "decide 拒绝兑换预填候选（容器上下文）");

        TestHost.suite("决策管线 · 正常开屏窗照常出手（回归）");

        // 抖音 SplashActivity：跳过键正常决策
        Ctx splashCtx = ctxOf(dy, "com.ss.android.ugc.aweme.splash.SplashActivity", true);
        ScanFind sf = AdDecisionCore.walk(juicyAdTree(), splashCtx);
        TestHost.check(!sf.skipCands.isEmpty(), "回归：正常开屏窗收集到跳过候选");
        Decision dd = AdDecisionCore.decide(sf, splashCtx);
        TestHost.check(!A_None(dd), "回归：正常开屏窗产生动作");
        TestHost.check(AdDecisionCore.A_CLICK_BOUNDS.equals(dd.action)
                        || AdDecisionCore.A_TAP_POINT.equals(dd.action),
                "回归：动作是点击（" + dd.action + "）");

        // 普通第三方 app 开屏（无 activityId 匹配的规则照常）
        Ctx otherCtx = ctxOf("com.unknown.app", "com.unknown.app.SplashAdActivity", true);
        ScanFind of = AdDecisionCore.walk(juicyAdTree(), otherCtx);
        Decision od = AdDecisionCore.decide(of, otherCtx);
        TestHost.check(!A_None(od), "回归：第三方 app 开屏照常出手");

        TestHost.done();
    }

    static boolean A_None(Decision d) {
        return d == null || AdDecisionCore.A_NONE.equals(d.action);
    }
}
