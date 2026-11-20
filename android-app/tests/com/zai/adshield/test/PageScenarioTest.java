package com.zai.adshield.test;

import com.zai.adshield.SubscriptionManager;
import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.AdTaskCore;
import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.InterstitialGuard;
import com.zai.adshield.core.VideoSplashGuard;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * L2 · 页面结构与场景兼容性测试（v7.9.3 新套件）。
 *
 * 12 类 UI 独立样本 × 引擎行为断言（每类树形同构真实页面结构）：
 *   native-view / webview / compose / flutter / react-native /
 *   custom-draw / canvas / video-player / dialog / popupwindow /
 *   overlay / multi-window
 *
 * 8 项场景行为（用户目录规格）：节点树可访问性 / 内容延迟加载 /
 * 关闭按钮延迟出现 / 候选消失 / 广告与反馈面板切换 / 页面内部导航 /
 * 窗口切换 / 无障碍事件驱动（服务侧调度——纯 JVM 面以「事件到来前后
 * 两次扫描行为差」等价表达）。
 *
 * S8 段：订阅七分类统计真实计数器（SubscriptionManager 生产实现——
 * primeForJvm 后 matchSources/countClickOutcome 的分类面逐格断言）。
 *
 * 诚实口径：JVM 仿真树与真机页面结构同构但非真机验证（L3 另行）。
 */
public final class PageScenarioTest {

    public static void main(String[] args) {
        s1_uiTypeSamples();
        s2_delayedContent();
        s3_candidateDisappear();
        s4_feedbackSheetSwitch();
        s5_innerNavigation();
        s6_windowSwitch();
        s7_videoSplashProfile();
        s8_subStatSevenClasses();
        TestHost.done();
    }

    /* ══════════ S1 · 12 类 UI 样本 ══════════ */
    static void s1_uiTypeSamples() {
        TestHost.suite("L2 · 12 类 UI 样本 · 节点树可访问性与决策");

        // ① native-view（银行类简洁原生开屏：ImageView + 文本跳过键 + 倒计时）
        SimNode t1 = new SimNode();
        t1.cls = "android.widget.FrameLayout";
        t1.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skip1 = TestHost.skipButton("跳过");
        skip1.clickable = true;
        SimNode logo1 = new SimNode("", 0, 0, 1080, 400);
        logo1.cls = "android.widget.ImageView";
        SimNode cd1 = new SimNode("跳过 5", 900, 80, 1060, 150);
        t1.add(logo1); t1.add(skip1); t1.add(cd1);
        Ctx c1 = ctxOf("com.icbc", "com.icbc.MainActivity", true, 600);
        ScanFind f1 = AdDecisionCore.walk(t1, c1);
        TestHost.check(f1.skipCands.size() >= 1, "native-view: 文本跳过键可访问");
        Decision d1 = AdDecisionCore.decide(f1, c1);
        TestHost.check(d1 != null && !AdDecisionCore.A_NONE.equals(d1.action),
                "native-view: 开屏窗跳过键出手");

        // ② webview（淘宝 TMSActivity 营销容器：WebView 容器 + 原生 × 关闭 + 广告角标）
        SimNode t2 = new SimNode();
        t2.cls = "android.widget.FrameLayout";
        t2.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode wv = new SimNode("", 0, 400, 1080, 2000);
        wv.cls = "android.webkit.WebView";
        SimNode close2 = new SimNode("×", 1000, 90, 1050, 140);
        close2.clickable = true;
        SimNode badge2 = new SimNode("广告", 60, 2140, 180, 2190);
        t2.add(wv); t2.add(close2); t2.add(badge2);
        Ctx c2 = ctxOf("com.taobao.taobao", "com.taobao.tms.TMSActivity", true, 6000);
        c2.sdkAdWin = true;
        ScanFind f2 = AdDecisionCore.walk(t2, c2);
        TestHost.check(f2.webviewPresent, "webview: WebView 容器铁证可探");
        TestHost.check(f2.adBadge, "webview: 广告角标可探");
        Decision d2 = AdDecisionCore.decide(f2, c2);
        TestHost.check(d2 != null && !AdDecisionCore.A_NONE.equals(d2.action),
                "webview: TMS 营销窗关闭路径出手（sdkAdWin 指纹在场）");

        // ③ compose（Jetpack Compose semantics：View 节点携带文本/描述）
        SimNode t3 = new SimNode();
        t3.cls = "android.view.View";
        t3.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode s3 = new SimNode("跳过广告", 900, 80, 1050, 150);
        s3.cls = "android.view.View";
        s3.clickable = true;
        t3.add(s3);
        Ctx c3 = ctxOf("com.sankuai.meituan", "com.meituan.android.paizui.home.MainActivity", true, 600);
        ScanFind f3 = AdDecisionCore.walk(t3, c3);
        TestHost.check(f3.skipCands.size() >= 1, "compose: semantics 文本跳过键可访问");
        Decision d3 = AdDecisionCore.decide(f3, c3);
        TestHost.check(d3 != null && !AdDecisionCore.A_NONE.equals(d3.action), "compose: 开屏跳过出手");

        // ④ flutter（SemanticsBinding 无 text 只有 contentDescription）
        SimNode t4 = new SimNode();
        t4.cls = "io.flutter.embedding.android.FlutterView";
        t4.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode s4 = new SimNode("", 900, 80, 1050, 150);
        s4.cls = "io.flutter.semantics.SemanticsNode";
        s4.desc = "跳过广告";
        s4.clickable = true;
        t4.add(s4);
        Ctx c4 = ctxOf("com.example.flutterapp", "com.example.flutterapp.MainActivity", true, 600);
        ScanFind f4 = AdDecisionCore.walk(t4, c4);
        TestHost.check(f4.skipCands.size() >= 1, "flutter: desc-only 节点可访问（label=desc 回落）");
        Decision d4 = AdDecisionCore.decide(f4, c4);
        TestHost.check(d4 != null && !AdDecisionCore.A_NONE.equals(d4.action), "flutter: desc 跳过键出手");

        // ⑤ react-native（ReactTextView 文本）
        SimNode t5 = new SimNode();
        t5.cls = "com.facebook.react.ReactRootView";
        t5.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode s5 = new SimNode("跳过 3", 900, 80, 1060, 150);
        s5.cls = "com.facebook.react.views.text.ReactTextView";
        s5.clickable = true;
        t5.add(s5);
        Ctx c5 = ctxOf("com.example.rnapp", "com.example.rnapp.MainActivity", true, 600);
        ScanFind f5 = AdDecisionCore.walk(t5, c5);
        TestHost.check(f5.skipCands.size() >= 1, "react-native: 文本跳过键可访问");
        Decision d5 = AdDecisionCore.decide(f5, c5);
        TestHost.check(d5 != null && !AdDecisionCore.A_NONE.equals(d5.action), "react-native: 倒计时跳过出手");

        // ⑥ custom-draw（自绘控件零文本：只有图标级 vid 与小面积）
        //   A：树极简（2 节点）角落图标——「树极简兜底」合法出手（极简窗+角落小钮=广告形态）
        SimNode t6 = new SimNode();
        t6.cls = "com.custom.DrawView";
        t6.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode icon6 = new SimNode("", 990, 88, 1046, 144);
        icon6.cls = "android.widget.ImageView";
        icon6.clickable = true;
        icon6.vid = "iv_close";
        t6.add(icon6);
        Ctx c6 = ctxOf("com.example.app", "com.example.app.SplashActivity", true, 600);
        ScanFind f6 = AdDecisionCore.walk(t6, c6);
        Decision d6 = AdDecisionCore.decide(f6, c6);
        TestHost.check(f6.skipCands.isEmpty(), "custom-draw: 零文本零跳过候选");
        TestHost.check(d6 != null && "corner_image_skip".equals(d6.reason),
                "custom-draw·A: 树极简角落图标合法出手（极简窗兜底形态）");
        //   B：内容丰富的自绘窗（>60 节点）无任何广告语境——零出手（误触红线）
        SimNode rich = new SimNode();
        rich.cls = "com.custom.DrawView";
        rich.boundsArr = new int[]{0, 0, 1080, 2400};
        for (int i = 0; i < 70; i++) {
            SimNode seg = new SimNode("", 60, 200 + i * 28, 1020, 220 + i * 28);
            seg.cls = "com.custom.DrawSegment";
            rich.add(seg);
        }
        rich.add(icon6);
        Ctx c6b = ctxOf("com.example.app", "com.example.app.SplashActivity", true, 600);
        ScanFind f6b = AdDecisionCore.walk(rich, c6b);
        Decision d6b = AdDecisionCore.decide(f6b, c6b);
        TestHost.check(d6b == null || AdDecisionCore.A_NONE.equals(d6b.action),
                "custom-draw·B: 内容窗角落图标零出手（无强广告形态不乱点）");
        //   C：同 B 树 + SDK 插屏窗指纹 → 图标路径解禁
        Ctx c6c = ctxOf("com.example.app", "com.example.app.SplashActivity", true, 600);
        c6c.sdkAdWin = true;
        ScanFind f6c = AdDecisionCore.walk(rich, c6c);
        Decision d6c = AdDecisionCore.decide(f6c, c6c);
        TestHost.check(d6c != null && !AdDecisionCore.A_NONE.equals(d6c.action),
                "custom-draw·C: SDK 插屏窗指纹在场图标路径解禁");
        // 图标钮路径纯闸：形态闸 + 语境闸（服务侧 decideIconFromCandidates 的两道纯前置）
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{990, 88, 1046, 144}, 1080, 2400),
                "custom-draw: 图标级形态闸通过（48×56 小面积图标）");
        TestHost.check(!InterstitialGuard.iconContextOk(false, false, false),
                "custom-draw: 普通窗语境闸拒绝（无 WebView/SDK 指纹/角标）");
        TestHost.check(InterstitialGuard.iconContextOk(false, true, false),
                "custom-draw: SDK 插屏窗指纹语境闸放行");

        // ⑦ canvas（SurfaceView 视频开屏：零文本零候选 → videoLike 画像路径）
        SimNode t7 = new SimNode();
        t7.cls = "android.widget.FrameLayout";
        t7.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode sv = new SimNode("", 0, 0, 1080, 2400);
        sv.cls = "android.view.SurfaceView";
        t7.add(sv);
        Ctx c7 = ctxOf("com.example.videoad", "com.example.videoad.SplashActivity", true, 600);
        ScanFind f7 = AdDecisionCore.walk(t7, c7);
        TestHost.check(f7.skipCands.isEmpty(), "canvas: SurfaceView 零文本零候选");
        Decision d7 = AdDecisionCore.decide(f7, c7);
        TestHost.check(d7 == null || AdDecisionCore.A_NONE.equals(d7.action), "canvas: 画像未成立零出手");

        // ⑧ video-player（视频窗控制层：跳过片头族文本）
        SimNode t8 = new SimNode();
        t8.cls = "android.widget.FrameLayout";
        t8.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode sv8 = new SimNode("", 0, 0, 1080, 2400);
        sv8.cls = "android.view.SurfaceView";
        SimNode skip8 = new SimNode("跳过片头", 900, 80, 1060, 150);
        skip8.clickable = true;
        t8.add(sv8); t8.add(skip8);
        Ctx c8 = ctxOf("com.qiyi.video", "com.qiyi.video.MainActivity", true, 600);
        ScanFind f8 = AdDecisionCore.walk(t8, c8);
        TestHost.check(f8.skipCands.size() >= 1, "video-player: 控制层跳过片头文本可访问");
        Decision d8 = AdDecisionCore.decide(f8, c8);
        TestHost.check(d8 != null && !AdDecisionCore.A_NONE.equals(d8.action), "video-player: 跳过片头出手");

        // ⑨ dialog（半屏 Dialog 插屏：角标 + × 关闭 + CTA）
        SimNode t9 = new SimNode();
        t9.cls = "android.app.Dialog";
        t9.boundsArr = new int[]{60, 900, 1020, 2340};
        SimNode badge9 = new SimNode("广告", 80, 930, 150, 975);
        SimNode close9 = new SimNode("×", 960, 920, 1010, 970);
        close9.clickable = true;
        SimNode cta9 = new SimNode("立即领取", 300, 1500, 780, 1600);
        cta9.clickable = true;
        t9.add(badge9); t9.add(close9); t9.add(cta9);
        Ctx c9 = ctxOf("com.example.app", "com.example.app.MainActivity", true, 6000);
        ScanFind f9 = AdDecisionCore.walk(t9, c9);
        TestHost.check(f9.adBadge && f9.foundClose, "dialog: 角标与关闭键可探");
        Decision d9 = AdDecisionCore.decide(f9, c9);
        TestHost.check(d9 != null && !AdDecisionCore.A_NONE.equals(d9.action), "dialog: 半屏插屏关闭路径出手");

        // ⑩ popupwindow（更新诱导弹窗：只有「以后再说」单信号）
        SimNode t10 = new SimNode();
        t10.cls = "android.widget.PopupWindow";
        t10.boundsArr = new int[]{60, 700, 1020, 1800};
        SimNode later = new SimNode("以后再说", 80, 1600, 400, 1700);
        later.clickable = true;
        SimNode ok = new SimNode("立即更新", 560, 1600, 980, 1700);
        ok.clickable = true;
        t10.add(later); t10.add(ok);
        Ctx c10 = ctxOf("com.example.app", "com.example.app.MainActivity", false, 3000);
        c10.updateDialog = false; // 单信号未确认（肯定侧在场但否定侧文案未双确认）
        ScanFind f10 = AdDecisionCore.walk(t10, c10);
        Decision d10 = AdDecisionCore.decide(f10, c10);
        TestHost.check(d10 == null || AdDecisionCore.A_NONE.equals(d10.action),
                "popupwindow: 更新诱导单信号零出手（误触红线）");

        // ⑪ overlay（底部半屏悬浮广告球窗）
        SimNode t11 = new SimNode();
        t11.cls = "android.widget.FrameLayout";
        t11.boundsArr = new int[]{0, 1200, 1080, 2400};
        SimNode badge11 = new SimNode("广告", 40, 1230, 110, 1275);
        SimNode close11 = new SimNode("×", 990, 1215, 1040, 1265);
        close11.clickable = true;
        t11.add(badge11); t11.add(close11);
        Ctx c11 = ctxOf("com.example.app", "com.example.app.MainActivity", true, 6000);
        c11.halfScreenAd = true;
        ScanFind f11 = AdDecisionCore.walk(t11, c11);
        TestHost.check(f11.adBadge && f11.foundClose, "overlay: 半屏窗角标与关闭键可探");
        Decision d11 = AdDecisionCore.decide(f11, c11);
        TestHost.check(d11 != null && !AdDecisionCore.A_NONE.equals(d11.action), "overlay: 半屏悬浮广告关闭出手");

        // ⑫ multi-window（分屏另一半的内容窗：长文本 + 输入框）
        SimNode t12 = contentTree();
        Ctx c12 = ctxOf("com.zhihu.android", "com.zhihu.android.ui.activity.AnswerActivity", false, 5000);
        ScanFind f12 = AdDecisionCore.walk(t12, c12);
        Decision d12 = AdDecisionCore.decide(f12, c12);
        TestHost.check(d12 == null || AdDecisionCore.A_NONE.equals(d12.action),
                "multi-window: 内容窗零出手（另一半不因分屏被误扫出手）");
    }

    /* ══════════ S2 · 内容延迟加载 / 关闭按钮延迟出现 ══════════ */
    static void s2_delayedContent() {
        TestHost.suite("L2 · 场景行为：延迟加载与关闭按钮延迟出现");

        String pkg = "com.example.slowfeed";
        // 帧1（内容未到）：零候选零出手
        SimNode early = new SimNode();
        early.cls = "android.widget.FrameLayout";
        early.boundsArr = new int[]{0, 0, 1080, 2400};
        Ctx cEarly = ctxOf(pkg, "com.example.slowfeed.SplashActivity", true, 300);
        ScanFind fEarly = AdDecisionCore.walk(early, cEarly);
        Decision dEarly = AdDecisionCore.decide(fEarly, cEarly);
        TestHost.check(fEarly.skipCands.isEmpty() && (dEarly == null || AdDecisionCore.A_NONE.equals(dEarly.action)),
                "延迟加载·帧1: 未加载零出手");

        // 帧2（跳过键延迟出现——事件驱动重扫到达）：出手
        SimNode late = new SimNode();
        late.cls = "android.widget.FrameLayout";
        late.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skipL = TestHost.skipButton("跳过");
        skipL.clickable = true;
        late.add(skipL);
        Ctx cLate = ctxOf(pkg, "com.example.slowfeed.SplashActivity", true, 900);
        ScanFind fLate = AdDecisionCore.walk(late, cLate);
        Decision dLate = AdDecisionCore.decide(fLate, cLate);
        TestHost.check(fLate.skipCands.size() >= 1
                && dLate != null && !AdDecisionCore.A_NONE.equals(dLate.action),
                "延迟加载·帧2: 跳过键出现即出手（事件驱动面）");

        // 关闭按钮延迟出现（视频开屏 >12s 露头——观察窗 20s 必须开岗）
        TestHost.check(VideoSplashGuard.videoLike(2500, 4, 2000, false),
                "视频画像: 零候选连环+静默画像成立（延迟露头按钮的观察前提）");
        TestHost.check(VideoSplashGuard.windowDeadlineMs(true) >= 20000,
                "视频画像: 观察窗延长至 20s（>12s 露头按钮可等到）");
        TestHost.check(VideoSplashGuard.videoArmHint(2300, 3, 900),
                "视频画像: arm-hint 免杀链（慢速构建窗——关闭按钮尚未露头不退避）");
    }

    /* ══════════ S3 · 候选消失（任务终态链） ══════════ */
    static void s3_candidateDisappear() {
        TestHost.suite("L2 · 场景行为：候选消失与任务终态");

        // 事件链：DETECTED → READY → 出手(VERIFYING) → 候选消失两轮确认 → SUCCESS（不可逆）
        int st = AdTaskCore.S_DETECTED;
        TestHost.check(AdTaskCore.clickAllowed(st), "候选消失·S3: 初始态可出手");
        st = AdTaskCore.onDispatch(st);
        TestHost.checkEq(AdTaskCore.S_VERIFYING, st, "候选消失·S3: 出手后 VERIFYING");
        Long firstZero = null;
        // 复验扫描1：候选已消失（广告已关）——第一轮零记时刻
        firstZero = 1000L;
        TestHost.check(!AdTaskCore.zeroVerifyConfirmed(firstZero, 1000L, true),
                "候选消失·S3: 第一轮零不确认（树刷新抖动防线）");
        // 350ms 后第二轮仍零 → 确认
        TestHost.check(AdTaskCore.zeroVerifyConfirmed(firstZero, 1400L, true),
                "候选消失·S3: 两轮零确认（≥350ms 独立两轮）");
        st = AdTaskCore.onVerifyAdGone(st);
        TestHost.checkEq(AdTaskCore.S_SUCCESS, st, "候选消失·S3: 广告消失 → SUCCESS");
        TestHost.check(AdTaskCore.isTerminal(st) && !AdTaskCore.clickAllowed(st) && !AdTaskCore.callbackAllowed(st),
                "候选消失·S3: SUCCESS 不可逆（后续点击/回调全拒收）");
        TestHost.check(!AdTaskCore.taskAllowsNew(1500L, 1000L),
                "候选消失·S3: 终态 2.2s 冷却闸（500ms 内新广告不开新任务）");
        TestHost.check(AdTaskCore.taskAllowsNew(3400L, 1000L),
                "候选消失·S3: 冷却过后新广告可开新任务");
    }

    /* ══════════ S4 · 广告与反馈面板切换 ══════════ */
    static void s4_feedbackSheetSwitch() {
        TestHost.suite("L2 · 场景行为：广告与反馈面板切换");

        // 反馈面板树（点击「不感兴趣」后拉起的「我为什么会看到广告」面板）
        SimNode sheet = new SimNode();
        sheet.cls = "androidx.recyclerview.widget.RecyclerView";
        sheet.boundsArr = new int[]{60, 800, 1020, 2200};
        SimNode title = new SimNode("我为什么会看到广告", 100, 840, 900, 910);
        SimNode reason = new SimNode("不感兴趣", 100, 960, 900, 1030);
        SimNode cancel = new SimNode("取消", 100, 2000, 500, 2080);
        cancel.clickable = true;
        SimNode close = new SimNode("×", 960, 820, 1010, 870);
        close.clickable = true;
        sheet.add(title); sheet.add(reason); sheet.add(cancel); sheet.add(close);

        // ① 面板文案绝不产生跳过候选（「我为什么会看到广告」非跳过族）
        Ctx c = ctxOf("com.tencent.mm", "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI", false, 8000);
        ScanFind f = AdDecisionCore.walk(sheet, c);
        TestHost.check(f.skipCands.isEmpty(), "反馈面板: 面板文案零跳过候选（非跳过族）");
        // ② FEEDBACK 态绝不对原广告再执行关闭（AdTaskCore 独立流程）
        int st = AdTaskCore.onFeedbackSeen(AdTaskCore.S_VERIFYING);
        TestHost.checkEq(AdTaskCore.S_FEEDBACK, st, "反馈面板: 广告关闭后面板 → FEEDBACK 态");
        TestHost.check(!AdTaskCore.clickAllowed(st), "反馈面板: FEEDBACK 期不出手（不对原广告补刀）");
        st = AdTaskCore.onFeedbackDone(st);
        TestHost.checkEq(AdTaskCore.S_SUCCESS, st, "反馈面板: 面板收起 → SUCCESS（面板只在广告关后出现）");
        TestHost.check(AdTaskCore.isTerminal(st), "反馈面板: 面板链路落终态（不恢复原任务）");
        // ③ 面板期新候选不算新广告（冷却闸）
        TestHost.check(!AdTaskCore.taskAllowsNew(500L, 100L), "反馈面板: 终态冷却期内残留面板候选不开新任务");
        TestHost.check(AdTaskCore.taskAllowsNew(0L, 0L), "反馈面板: 无任务历史时新窗可开新任务（幂等基线）");
    }

    /* ══════════ S5 · 页面内部导航 ══════════ */
    static void s5_innerNavigation() {
        TestHost.suite("L2 · 场景行为：页面内部导航零出手");

        // 同一 Activity 内 tab 切换（内容树变化但窗口身份不变）
        String pkg = "com.xingin.xhs";
        String act = "com.xingin.xhs.index.IndexActivity";
        Ctx nav1 = ctxOf(pkg, act, false, 4000);
        Decision d1 = AdDecisionCore.decide(AdDecisionCore.walk(contentTree(), nav1), nav1);
        TestHost.check(d1 == null || AdDecisionCore.A_NONE.equals(d1.action), "内部导航·tab1: 内容窗零出手");

        Ctx nav2 = ctxOf(pkg, act, false, 4200);
        Decision d2 = AdDecisionCore.decide(AdDecisionCore.walk(contentTree(), nav2), nav2);
        TestHost.check(d2 == null || AdDecisionCore.A_NONE.equals(d2.action), "内部导航·tab2: 同窗身份零出手");

        // 聊天窗（可滚动 + 输入框——正常界面特征齐全）
        SimNode chat = new SimNode();
        chat.cls = "androidx.recyclerview.widget.RecyclerView";
        chat.scrollable = true;
        chat.boundsArr = new int[]{0, 0, 1080, 2200};
        SimNode msg = new SimNode("你好，这是一条消息，请问今天有空一起吃饭吗？", 60, 300, 1020, 420);
        SimNode input = new SimNode("", 0, 2240, 1080, 2380);
        input.cls = "android.widget.EditText";
        input.editable = true;
        chat.add(msg); chat.add(input);
        Ctx cChat = ctxOf("com.tencent.mm", "com.tencent.mm.ui.LauncherUI", false, 3000);
        ScanFind fChat = AdDecisionCore.walk(chat, cChat);
        TestHost.check(fChat.scrollPresent && fChat.editPresent, "内部导航: 聊天窗特征齐全（可滚+输入）");
        Decision dChat = AdDecisionCore.decide(fChat, cChat);
        TestHost.check(dChat == null || AdDecisionCore.A_NONE.equals(dChat.action), "内部导航: 聊天窗零出手");
        // 微信窗级门：LauncherUI 不进信息流扫描
        TestHost.check(!FeedAdGuard.isFeedWindow("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"),
                "内部导航: 聊天主界面不进 feed 扫描（窗级门）");
    }

    /* ══════════ S6 · 窗口切换（任务终局） ══════════ */
    static void s6_windowSwitch() {
        TestHost.suite("L2 · 场景行为：窗口切换任务终局");

        // 出手后窗口切换 → SUCCESS 落账（有出手 = 广告已关的窗口级铁证）
        int st = AdTaskCore.S_VERIFYING;
        st = AdTaskCore.onWindowChanged(st, true);
        TestHost.checkEq(AdTaskCore.S_SUCCESS, st, "窗口切换: 有出手 → SUCCESS 落账");
        TestHost.check(AdTaskCore.isTerminal(st), "窗口切换: 终态不可逆");

        // 零出手窗口切换 → CANCELLED（观察窗结束没广告）
        int st2 = AdTaskCore.S_DETECTED;
        st2 = AdTaskCore.onWindowChanged(st2, false);
        TestHost.checkEq(AdTaskCore.S_CANCELLED, st2, "窗口切换: 零出手 → CANCELLED（不诬为成功）");

        // 验证中窗口切换（外部跳转打断）→ 终局（不给旧任务续命）
        int st3 = AdTaskCore.S_VERIFYING;
        st3 = AdTaskCore.onWindowChanged(st3, false);
        TestHost.check(AdTaskCore.isTerminal(st3), "窗口切换: 验证中被打断 → 终局（旧任务不续命）");
    }

    /* ══════════ S7 · 视频开屏画像样本（canvas 场景的调度面） ══════════ */
    static void s7_videoSplashProfile() {
        TestHost.suite("L2 · 视频开屏画像与调度契约");

        // 零候选连环 + 树静默 + 窗龄 → videoLike
        TestHost.check(VideoSplashGuard.videoLike(2400, 3, 1700, false), "画像: 主体成立");
        TestHost.check(!VideoSplashGuard.videoLike(2400, 2, 1700, false), "画像: 零候选连环不足不成立");
        TestHost.check(!VideoSplashGuard.videoLike(2400, 3, 900, false), "画像: 静默不足不成立");
        TestHost.check(!VideoSplashGuard.videoLike(2000, 3, 1700, false), "画像: 窗龄不足不成立");
        // SDK 视频窗（3.2s 起步——零候选 streak 无条件计数的修复面）
        TestHost.check(VideoSplashGuard.videoLike(3400, 3, -1, true), "画像: SDK 视频窗指纹路径（streak≥3 + 窗龄≥3.2s）");
        TestHost.check(!VideoSplashGuard.videoLike(3400, 0, -1, true), "画像: SDK 窗 streak 未满不成立");
        // 观察窗 / 轮询针 / 白点节流
        TestHost.check(VideoSplashGuard.windowDeadlineMs(false) <= 12000, "画像: 普通窗 12s 天花板保持");
        TestHost.check(VideoSplashGuard.maxClicksFor(true) >= 5, "画像: 视频窗预算 5（3/5 封顶之内）");
        TestHost.check(VideoSplashGuard.strikeGapOk(0L, 1600L), "画像: 白点节流 ≥1.5s/击");
        TestHost.check(!VideoSplashGuard.strikeGapOk(0L, 1000L), "画像: 1s 内白点连击拒绝（预算保护）");
        // 终态降频契约（1.5s → 3s）
        TestHost.check(VideoSplashGuard.pollDue(0L, 1600L, false), "画像: 非终态 1.5s 节奏到期放行（active 猶1.5s）");
        TestHost.check(!VideoSplashGuard.pollDue(0L, 1600L, true), "画像: 终态 3s 降频未到期拒扫（广告已关后降频）");
        TestHost.check(VideoSplashGuard.pollDue(0L, 3100L, true), "画像: 终态 3s 到期放行（观察在岗不出手）");
        TestHost.check(VideoSplashGuard.pollDue(null, 0L, true), "画像: 首针立即到期（无记录=立即）");
        // 全量复扫每 3 针一次
        TestHost.check(VideoSplashGuard.pollWalkDue(3) && !VideoSplashGuard.pollWalkDue(2),
                "画像: 每 3 针一次全量复扫（20s 窗 ≤5 次）");
        // 预算刷新 = 新文本键（新按钮），图标/角落不刷新
        TestHost.check(VideoSplashGuard.budgetRefresh(true, true, true), "画像: 视频窗新文本键预算刷新");
        TestHost.check(!VideoSplashGuard.budgetRefresh(true, false, false), "画像: 无新键不刷新（旧任务不再执行）");
    }

    /* ══════════ S8 · 订阅七分类统计（真实生产计数器） ══════════ */
    static void s8_subStatSevenClasses() {
        TestHost.suite("L2 · 订阅七分类统计（SubscriptionManager 生产计数器）");

        SubscriptionManager.primeForJvm();
        int[] base = SubscriptionManager.subStatSnapshot();

        // ① noCover：两源都无该包规则（未知包）
        SubscriptionManager.matchSources("com.unknown.app.nothing", contentTree(), ctxOf("com.unknown.app.nothing", "x.MainActivity", true, 600));
        int[] after1 = SubscriptionManager.subStatSnapshot();
        TestHost.checkEq(base[1] + 1, after1[1], "七分类·noCover: 未知包应用不匹配 +1");

        // ② noMatch：有覆盖但页面选择器不匹配（淘宝 + 内容树——无跳过键）
        SubscriptionManager.matchSources("com.taobao.taobao", contentTree(), ctxOf("com.taobao.taobao", "x.MainActivity", true, 600));
        int[] after2 = SubscriptionManager.subStatSnapshot();
        TestHost.checkEq(base[2] + 1, after2[2], "七分类·noMatch: 有覆盖页面不匹配 +1（与 noCover 分列）");

        // ③ hit：规则命中（淘宝 + 跳过键树）
        SimNode tree = new SimNode();
        tree.cls = "android.widget.FrameLayout";
        tree.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skip = TestHost.skipButton("跳过");
        skip.clickable = true;
        tree.add(skip);
        java.util.ArrayList<com.zai.adshield.core.RuleEngine.Hit> hits =
                SubscriptionManager.matchSources("com.taobao.taobao", tree, ctxOf("com.taobao.taobao", "x.SplashActivity", true, 600));
        int[] after3 = SubscriptionManager.subStatSnapshot();
        TestHost.checkEq(base[3] + 1, after3[3], "七分类·hit: 规则命中 +1");
        TestHost.check(!hits.isEmpty(), "七分类·hit: 命中列表非空（内置订阅跳过键规则）");

        // ④ notLoaded 保持 0（prime 后 builtin 在场——绝不把已加载算未加载）
        TestHost.checkEq(base[0], after3[0], "七分类·notLoaded: 已加载零计数（归因诚实）");

        // ⑤ clickFail / clickOk / blocked 三分支（countClickOutcome 生产入口）
        int[] b4 = SubscriptionManager.subStatSnapshot();
        SubscriptionManager.countClickOutcome("rule_sub_gkd", 1);
        SubscriptionManager.countClickOutcome("rule_sub_gkd", 0);
        SubscriptionManager.countClickOutcome("rule_sub_gkd", -1);
        int[] after4 = SubscriptionManager.subStatSnapshot();
        TestHost.checkEq(b4[5] + 1, after4[5], "七分类·clickFail: 双通道全失败 +1");
        TestHost.checkEq(b4[6] + 1, after4[6], "七分类·clickOk: 点击执行成功 +1");
        TestHost.checkEq(b4[4] + 1, after4[4], "七分类·blocked: 被安全闸拦截 +1");
        // 非订阅规则不计
        int[] b5 = SubscriptionManager.subStatSnapshot();
        SubscriptionManager.countClickOutcome("skip_text", 1);
        int[] after5 = SubscriptionManager.subStatSnapshot();
        TestHost.checkEq(b5[6], after5[6], "七分类·口径: 非订阅规则路径不计入（不虚标）");
    }

    /* ══════════ 工具 ══════════ */
    static Ctx ctxOf(String pkg, String activity, boolean splash, long elapsed) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = activity;
        ctx.elapsedMs = elapsed;
        ctx.splashLive = splash;
        return ctx;
    }

    /** 正常内容窗：长文本 + 输入框（零出手反例树）。 */
    static SimNode contentTree() {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode article = new SimNode(
                "这是一段很长的正文内容，用于模拟正常主界面窗。今天的推荐内容已经更新完毕，请享用更多精彩内容。",
                60, 200, 1020, 1900);
        SimNode input = new SimNode("", 60, 2280, 1020, 2380);
        input.cls = "android.widget.EditText";
        input.editable = true;
        root.add(article);
        root.add(input);
        return root;
    }
}
