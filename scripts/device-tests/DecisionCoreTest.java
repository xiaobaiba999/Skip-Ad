package com.zai.adshield.tests;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;

/**
 * 决策核心 JVM 单测（v3.8.0 · 真实机型场景建模 · 发版前必绿）
 * ─────────────────────────────────────────────────────────────
 * 场景来源：2026-11 三轮真机反馈（微博/喜马拉雅拦截失败 → 点击被吞 →
 * 二次进入被连按返回键「模拟人操作退出」）+ 误报防护（正常界面绝不误点/误返回）。
 * v3.6.0 新增重点：
 *  · 根窗口安全门：入口窗（冷启动开屏/回前台首窗）上 L3/L4.5/L5 返回键全禁，
 *    只走点击梯子（G/X3/X4/V1d/H1b 用例）
 *  · SDK 广告窗豁免：入口窗上的独立广告 Activity 返回仍安全（X2d）
 *  · L3 信息流安全门：可滚动页面「摇一摇/翻转」文案不返回键（F1b）
 *  · L6 三位置坐标轮换：右上→右下→左上，用尽收手（W2/V1e）
 *  · L1/L1.6 手势点原标签控件坐标（S2）
 *  · learnableSkipShape：底部跳过键形态（G4）
 * v3.8.0 新增重点（issue#10 二轮·GKD 方法论对齐）：
 *  · visibleToUser 过滤：不可见「跳过」候选全过滤（v380-6）
 *  · viewId 跳过键：vid 含 skip / count+down 非 download（v380-3/4/5）
 *  · 假跳过手势直点：clickCenter 点标签自身坐标（v380-1；中央诱饵不点 v380-2）
 *  · 候选排序：selfClick/短文案/小面积优先（v380-9；N1 断言随之调整）
 *  · 角落键先于 SDK 直断：CLS_BREAK_MS 500→700（v380-8；X2b/X2d/F5b 时序随调）
 *  · parseCountdown 倒计时解析（v380-7）
 * 用法：javac 编译后 java 运行；任何 FAIL 退出码 1，禁止发版。
 */
public class DecisionCoreTest {

    static int pass = 0, fail = 0;

    /* ── 上下文工厂 ── */
    static Ctx splash(long elapsedMs) {
        Ctx c = base();
        c.splashLive = true;
        c.elapsedMs = elapsedMs;
        return c;
    }

    /** v3.6.0：入口窗（冷启动开屏/回前台首窗）——按返回键=退出应用 */
    static Ctx rootSplash(long elapsedMs) {
        Ctx c = splash(elapsedMs);
        c.rootWindow = true;
        return c;
    }

    static Ctx base() {
        Ctx c = new Ctx();
        c.screenW = FakeNode.SW;
        c.screenH = FakeNode.SH;
        return c;
    }

    static Decision run(FakeNode root, Ctx ctx) {
        ScanFind f = AdDecisionCore.walk(root, ctx);
        return AdDecisionCore.decide(f, ctx);
    }

    /* ═══════ 微博类：第一方图片开屏（真机反馈主场景） ═══════ */

    /** W1：微博开屏——全屏图片广告 + 右上角无字小可点控件（图片跳过键） */
    static void weibo_cornerSkip() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        FakeNode skipBtn = FakeNode.node("android.widget.ImageView", 940, 90, 1030, 180); // 右上角 90px 方块，无文本
        skipBtn.click();
        root.add(adImage).add(skipBtn);

        // 450ms（v3.6.0 角落最早 500ms）：窗口类名非 SDK、无文本 → 应等待
        check("W1a 微博角落控件 450ms 未到时机应等待", run(root, splash(450)).action.equals(AdDecisionCore.A_NONE));

        // 700ms：角落控件命中 → 点击其中心（入口窗也允许：点击梯子永不豁免）
        Decision d = run(root, rootSplash(700));
        check("W1b 微博图片跳过键 → 点击角落控件（根窗口照点）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("corner_image_skip")
                        && d.bounds != null && d.bounds[0] == 940,
                "实际: " + d.action + "/" + d.reason);
    }

    /** W2：微博开屏变体——纯图零信号 → 坐标级三位置轮换（v3.6.0） */
    static void weibo_coordFallback() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        root.add(adImage); // 不可点、无字：纯展示图

        Decision d0 = run(root, splash(1200));
        check("W2a 纯图开屏第 1 次 → 右上标准位",
                d0.action.equals(AdDecisionCore.A_TAP_POINT) && d0.x == (int) (1080 * 0.88) && d0.y == (int) (2400 * 0.07),
                "实际: " + d0.action + " @" + d0.x + "," + d0.y);

        // v3.6.0 轮换：第 2 次兜底点右下、第 3 次点左上、第 4 次收手
        Ctx second = splash(2000);
        second.clickAttempts = 1;
        Decision d1 = run(root, second);
        check("W2b 第 2 次兜底 → 右下位",
                d1.action.equals(AdDecisionCore.A_TAP_POINT) && d1.x == (int) (1080 * 0.90) && d1.y == (int) (2400 * 0.92),
                "实际: " + d1.action + " @" + d1.x + "," + d1.y);

        Ctx third = splash(3000);
        third.clickAttempts = 2;
        Decision d2 = run(root, third);
        check("W2c 第 3 次兜底 → 左上位",
                d2.action.equals(AdDecisionCore.A_TAP_POINT) && d2.x == (int) (1080 * 0.08) && d2.y == (int) (2400 * 0.07),
                "实际: " + d2.action + " @" + d2.x + "," + d2.y);

        Ctx fourth = rootSplash(4000);
        fourth.clickAttempts = 3;
        check("W2d 入口窗三位置用尽 → 收手不再点击（应用内窗则返回键兜底，见 V1c）",
                run(root, fourth).action.equals(AdDecisionCore.A_NONE),
                "实际: " + run(root, fourth).action);
    }

    /** W3：微博开屏带延迟文本跳过（3s 后出现「跳过广告」）→ 前期坐标兜底抢跑 */
    static void weibo_delayedText() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        root.add(adImage);
        check("W3a 无信号期坐标兜底", run(root, splash(950)).action.equals(AdDecisionCore.A_TAP_POINT));

        FakeNode skip = FakeNode.node("android.widget.TextView", 880, 100, 1040, 170).txt("跳过广告").click();
        root.add(skip);
        Decision d = run(root, splash(3200));
        check("W3b 文本出现后优先文本代点", d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("skip_text"));
    }

    /* ═══════ 喜马拉雅类：零文本 / SDK 窗 / 翻转诱导 / 双返回退出事故 ═══════ */

    /** X1：喜马拉雅第一方开屏（WelComeActivity·入口窗）零文本 → 角落控件抢先 */
    static void ximalaya_cornerBeforeFlip() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        FakeNode skipBtn = FakeNode.node("android.widget.ImageView", 950, 80, 1020, 150); // 右上角 70px
        skipBtn.click();
        root.add(adImage).add(skipBtn);

        Decision d = run(root, rootSplash(650));
        check("X1 喜马拉雅图片跳过键 650ms（入口窗照点）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("corner_image_skip"));
    }

    /** X2：CSJ 穿山甲全屏视频广告（SDK 窗口）→ className 直断（入口窗豁免） */
    static void ximalaya_sdkWindow() {
        FakeNode root = FakeNode.root("com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity");
        root.add(FakeNode.node("android.view.TextureView", 0, 0, 1080, 2400));
        Ctx ctx = splash(400);
        ctx.sdkAdWin = true; // 服务侧由 isAdActivityClass(当前窗口类名) 判定
        check("X2a SDK 窗 400ms 未到直断时限应等待", run(root, ctx).action.equals(AdDecisionCore.A_NONE));
        ctx.elapsedMs = 720; // v3.8.0：CLS_BREAK_MS 500→700，720ms 才直断
        Decision d = run(root, ctx);
        check("X2b SDK 窗 720ms 返回键直断（v3.8.0 500→700 先点击后直断）",
                d.action.equals(AdDecisionCore.A_BACK) && d.reason.equals("sdk_window"));

        Ctx learned = splash(320);
        learned.sdkAdWin = true;
        learned.learnedCls = true;
        check("X2c 学习指纹 300ms 快断", run(root, learned).action.equals(AdDecisionCore.A_BACK));

        // v3.6.0 关键：SDK 广告窗即便是入口窗也豁免（独立广告 Activity，
        // 返回回到应用内页——与第一方入口窗返回=退出应用不同）
        Ctx rootSdk = rootSplash(720); // v3.8.0：同 X2b 500→700
        rootSdk.sdkAdWin = true;
        Decision d2 = run(root, rootSdk);
        check("X2d SDK 窗在入口窗上返回仍安全（豁免）",
                d2.action.equals(AdDecisionCore.A_BACK) && d2.reason.equals("sdk_window"),
                "实际: " + d2.action + "/" + d2.reason);
    }

    /**
     * X3（v3.6.0 核心）：翻转文案在【入口窗】上不再立即直断——
     * 真机事故：入口窗按返回=退出应用（喜马拉雅弹「再按一次退出」→ 引擎再按 → 真退出）。
     * 改走点击梯子（角落候选 / 坐标轮换）；应用内窗（插屏）仍立即直断。
     */
    static void gyro_immediate() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode hint = FakeNode.node("android.widget.TextView", 200, 2000, 880, 2080).txt("翻转手机，跳转详情页或第三方应用");
        root.add(hint);

        check("X3a 翻转文案 60ms 未到时机应等待", run(root, splash(60)).action.equals(AdDecisionCore.A_NONE));
        // 入口窗：绝不返回键（毒链② 根治）
        Decision dRoot = run(root, rootSplash(150));
        check("X3b 入口窗翻转文案 150ms 禁返回键（防退出应用）",
                !dRoot.action.equals(AdDecisionCore.A_BACK),
                "实际: " + dRoot.action + "/" + dRoot.reason);
        // 入口窗 950ms → 坐标梯子（右上标准位）
        Decision dTap = run(root, rootSplash(950));
        check("X3c 入口窗翻转文案 950ms 走坐标梯子",
                dTap.action.equals(AdDecisionCore.A_TAP_POINT),
                "实际: " + dTap.action + "/" + dTap.reason);
        // 应用内窗（插屏/活动页之上）：立即直断保持
        Decision dInApp = run(root, splash(150));
        check("X3d 应用内窗翻转文案 150ms 立即直断",
                dInApp.action.equals(AdDecisionCore.A_BACK) && dInApp.reason.equals("gyro_immediate"),
                "实际: " + dInApp.action + "/" + dInApp.reason);
    }

    /** X4：假跳过陷阱（图内「跳过 5」+ 半屏可点容器）→ v3.8.0 手势直点标签坐标
     *  （GKD clickCenter：标签位于底部小标签形态 learnableSkipShape 命中，isSmallClick
     *  判假只挡 performAction 大容器，标签自身坐标仍是有效触点；中央诱饵形态另见
     *  v380_centerBaitNoTap——那时才不点） */
    static void fakeSkipTrap() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode trap = FakeNode.node("android.widget.TextView", 300, 2050, 780, 2160).txt("跳过 5");
        FakeNode trapWrap = FakeNode.node("android.view.View", 0, 1900, 1080, 2400).click();
        trapWrap.add(trap);
        root.add(trapWrap);

        // v3.8.0：假跳过=手势直点（GKD clickCenter）——「跳过 5」标签位于底部小标签
        // 形态，标签坐标仍是有效触点；应用内窗/入口窗都直点，绝不返回键（被广告拦截）
        Decision dInApp = run(root, splash(900));
        check("X4a 应用内窗假跳过 → 手势直点标签坐标（v3.8.0 fake_skip_tap，GKD clickCenter）",
                dInApp.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && dInApp.reason.equals("fake_skip_tap")
                        && dInApp.bounds != null && dInApp.bounds[0] == 300,
                "实际: " + dInApp.action + "/" + dInApp.reason);
        // 入口窗：同样手势直点（点击梯子不受根窗口安全门限制——安全门只挡返回键）
        Decision dRoot = run(root, rootSplash(900));
        check("X4b 入口窗假跳过 → 手势直点标签坐标（v3.8.0；原 v3.6.0 走坐标梯子）",
                dRoot.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && dRoot.reason.equals("fake_skip_tap"),
                "实际: " + dRoot.action + "/" + dRoot.reason);
    }

    /**
     * G1（v3.6.0 · 真机事故复现）：喜马拉雅二次进入——毒指纹把 MainActivity
     * 学成「广告窗」后，每次进入主页 500ms 内就按返回键 → 退出应用。
     * v3.6.0：sdkAdWin 只认当前窗口类名 + learnClass 只沉淀真广告窗类 +
     * rootWindow 安全门 → 主页上绝不返回键；真跳过文本照点。
     */
    static void g1_mainActivityPoison() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.MainActivity");
        // 主页信息流（可滚动）+ feed 内「摇一摇」互动文案（v3.6.0 L3 信息流安全门）
        FakeNode feed = FakeNode.node("androidx.recyclerview.widget.RecyclerView", 0, 200, 1080, 2300).scroll();
        FakeNode shake = FakeNode.node("android.widget.TextView", 200, 500, 880, 580).txt("摇一摇抽好签，好运连连");
        FakeNode avatar = FakeNode.node("android.widget.ImageView", 950, 120, 1040, 210).click();
        feed.add(shake).add(avatar);
        root.add(feed);

        // 即便老版本毒指纹残留（learnedCls=true），主页（可滚动）也绝不返回键
        Ctx poisoned = rootSplash(600);
        poisoned.learnedCls = true;
        Decision d = run(root, poisoned);
        check("G1a 主页毒指纹 + 摇一摇文案 + 入口窗 → 零返回键（退出事故根治）",
                !d.action.equals(AdDecisionCore.A_BACK),
                "实际: " + d.action + "/" + d.reason);
        // 应用内窗同样：信息流页面 L3 直断被禁（F1b 同源校验）
        Ctx inApp = splash(600);
        inApp.learnedCls = true;
        check("G1b 信息流页面（可滚动）零动作",
                run(root, inApp).action.equals(AdDecisionCore.A_NONE),
                "实际: " + run(root, inApp).action);
    }

    /* ═══════ 误报防护（正常界面绝不误点/误返回） ═══════ */

    /** F1：微博主页信息流（可滚动 RecyclerView）→ 绝不坐标兜底/绝不点角落头像 */
    static void noFalsePositive_feed() {
        FakeNode root = FakeNode.root("com.sina.weibo.HomeActivity");
        FakeNode feed = FakeNode.node("androidx.recyclerview.widget.RecyclerView", 0, 200, 1080, 2300).scroll();
        FakeNode avatar = FakeNode.node("android.widget.ImageView", 950, 120, 1040, 210).click(); // 右上角头像
        feed.add(avatar);
        root.add(feed);
        Decision d = run(root, splash(1200));
        check("F1 信息流界面（可滚动）零动作", d.action.equals(AdDecisionCore.A_NONE), "实际: " + d.action);
    }

    /** F2：搜索页（EditText）→ 零动作 */
    static void noFalsePositive_edit() {
        FakeNode root = FakeNode.root("com.sina.weibo.SearchActivity");
        FakeNode edit = FakeNode.node("android.widget.EditText", 100, 150, 900, 260).edit();
        root.add(edit);
        check("F2 输入框界面零动作", run(root, splash(1200)).action.equals(AdDecisionCore.A_NONE));
    }

    /** F3：首次启动隐私政策弹窗（文本多、有滚动）→ 不当广告处理 */
    static void noFalsePositive_privacy() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode dialog = FakeNode.node("android.app.Dialog", 80, 600, 1000, 1800).click();
        FakeNode body = FakeNode.node("android.widget.ScrollView", 120, 700, 960, 1400).scroll();
        for (int i = 0; i < 30; i++) {
            body.add(FakeNode.node("android.widget.TextView", 130, 710 + i * 20, 950, 726 + i * 20).txt("隐私政策条款第" + i + "条内容示例文本"));
        }
        FakeNode agree = FakeNode.node("android.widget.Button", 380, 1450, 700, 1560).txt("同意并继续").click();
        dialog.add(body).add(agree);
        root.add(dialog);
        Decision d = run(root, splash(1500));
        check("F3 隐私弹窗零动作（不误点同意）", d.action.equals(AdDecisionCore.A_NONE), "实际: " + d.action + "/" + d.reason);
    }

    /** F4：普通 Activity 名含 Download（非广告）→ 不误判 SDK 窗；真广告窗类不漏 */
    static void noFalsePositive_download() {
        check("F4 DownloadActivity 非广告窗",
                !AdDecisionCore.isAdActivityClass("com.sina.weibo.DownloadActivity"));
        check("F4b 穿山甲 stub 窗是广告窗",
                AdDecisionCore.isAdActivityClass("com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity"));
        check("F4c 优量汇窗是广告窗", AdDecisionCore.isAdActivityClass("com.qq.e.ads.ADActivity"));
        check("F4d 宿主 ad.activity 是广告窗", AdDecisionCore.isAdActivityClass("com.example.app.ad.activity.AdActivity"));
        check("F4e 喜马拉雅 SplashAdActivity 是广告窗（真机日志类名）",
                AdDecisionCore.isAdActivityClass("com.ximalaya.ting.android.host.activity.SplashAdActivity"));
        // v3.6.0 毒指纹防线：主页/普通弹窗类绝不认成广告窗（learnClass 断源）
        check("F4f MainActivity 非广告窗（毒指纹断源）",
                !AdDecisionCore.isAdActivityClass("com.ximalaya.ting.android.host.activity.MainActivity"));
        check("F4g FrameLayout 非广告窗", !AdDecisionCore.isAdActivityClass("android.widget.FrameLayout"));
        check("F4h Dialog Fragment 非广告窗",
                !AdDecisionCore.isAdActivityClass("com.ximalaya.ting.android.host.fragment.other.BaseCustomDialogFragment$a"));
    }

    /** F5：守护应用（银行）纯图开屏 → 不热区直断、不坐标兜底（高精度信号除外） */
    static void guard_protection() {
        FakeNode root = FakeNode.root("com.icbc.activity.MainActivity");
        root.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        Ctx ctx = splash(1200);
        ctx.guard = true;
        Decision d = run(root, ctx);
        check("F5 守护应用纯图零动作", d.action.equals(AdDecisionCore.A_NONE), "实际: " + d.action);

        // 但 SDK 窗（高精度）照拦
        ctx.sdkAdWin = true;
        ctx.elapsedMs = 720; // v3.8.0：同 X2b 500→700
        check("F5b 守护应用 SDK 窗照拦", run(root, ctx).action.equals(AdDecisionCore.A_BACK));
        check("F5c icbc 是守护包", AdDecisionCore.isGuardPkg("com.icbc"));
        check("F5d 微博非守护包", !AdDecisionCore.isGuardPkg("com.sina.weibo"));
    }

    /** F6：坐标兜底窗口期（v3.6.0 扩至 8s，覆盖晚出跳过键） */
    static void coordWindow() {
        FakeNode root = FakeNode.root("com.sina.weibo.HomeActivity");
        root.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400));
        check("F6a 3.9s 内兜底可用", run(root, splash(3900)).action.equals(AdDecisionCore.A_TAP_POINT));
        check("F6b 4.5s 仍可兜底（v3.6.0 8s 窗）", run(root, splash(4500)).action.equals(AdDecisionCore.A_TAP_POINT));
        check("F6c 8.5s 后不再兜底", run(root, splash(8500)).action.equals(AdDecisionCore.A_NONE));
    }

    /* ═══════ 文本跳过键全形态（v3.6.0：手势点原标签控件坐标） ═══════ */

    /** S1：普通文本跳过（知乎式「跳过 3」） */
    static void textSkip() {
        FakeNode root = FakeNode.root("com.zhihu.android.app.ui.activity.LauncherActivity");
        root.add(FakeNode.node("android.widget.TextView", 860, 110, 1030, 180).txt("跳过 3").click());
        Decision d = run(root, splash(300));
        check("S1 文本跳过键直接代点", d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("skip_text"));
    }

    /** S2：contentDescription 形态 + 父容器可点 → 手势点【原标签控件】坐标（v3.6.0） */
    static void descSkip() {
        FakeNode root = FakeNode.root("com.xingin.xhs.index.IndexActivity");
        FakeNode img = FakeNode.node("android.widget.ImageView", 900, 100, 1010, 210).describe("跳过广告");
        FakeNode wrap = FakeNode.node("android.view.View", 880, 80, 1030, 230).click();
        wrap.add(img);
        root.add(wrap);
        Decision d = run(root, splash(400));
        check("S2 desc 跳过键：performAction 走父容器，手势点原标签坐标（900 而非父容器 880）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.bounds != null && d.bounds[0] == 900,
                "实际: " + d.action + " @" + (d.bounds != null ? d.bounds[0] : -1));
    }

    /** S3：SurfaceView 视频广告 + 小文本跳过 → 照常代点 */
    static void videoSkip() {
        FakeNode root = FakeNode.root("com.tencent.qqlive.SplashActivity");
        root.add(FakeNode.node("android.view.SurfaceView", 0, 300, 1080, 2100));
        root.add(FakeNode.node("android.widget.TextView", 870, 120, 1030, 190).txt("跳过").click());
        Decision d = run(root, splash(600));
        check("S3 视频广告文本跳过代点", d.action.equals(AdDecisionCore.A_CLICK_BOUNDS));
    }

    /* ═══════ 弹窗广告 ═══════ */

    /** P1：角标弹窗（广告 + ×）→ 关闭 */
    static void popup_badge() {
        FakeNode root = FakeNode.root("androidx.appcompat.app.AlertDialog");
        FakeNode badge = FakeNode.node("android.widget.TextView", 150, 800, 240, 850).txt("广告");
        FakeNode title = FakeNode.node("android.widget.TextView", 160, 860, 900, 960).txt("限时会员特惠");
        FakeNode close = FakeNode.node("android.widget.ImageView", 960, 780, 1030, 850).describe("关闭").click();
        root.add(badge).add(title).add(close);

        Ctx ctx = base();
        ctx.popupLive = true;
        ctx.elapsedMs = 30000; // 开屏窗外 30s（v3.2.0 曾永远关不掉的场景）
        Decision d = run(root, ctx);
        check("P1 开屏窗外 30s 角标弹窗照关",
                d.action.equals(AdDecisionCore.A_CLOSE) && d.reason.equals("popup_badge"));
    }

    /** P2：无角标但 Dialog 形态 + ≥2 营销话术 + 关闭键 → 弱信号关闭 */
    static void popup_ctaOnly() {
        FakeNode root = FakeNode.root("android.app.Dialog");
        root.add(FakeNode.node("android.widget.TextView", 200, 900, 880, 1000).txt("恭喜获得 188 元红包"));
        root.add(FakeNode.node("android.widget.Button", 300, 1050, 780, 1170).txt("立即领取").click());
        root.add(FakeNode.node("android.widget.TextView", 950, 800, 1010, 860).txt("暂不").click());
        Ctx ctx = base();
        ctx.popupLive = true;
        ctx.elapsedMs = 45000;
        Decision d = run(root, ctx);
        check("P2 Dialog+营销话术弱信号关闭", d.action.equals(AdDecisionCore.A_CLOSE) && d.reason.equals("popup_cta"));
    }

    /** P3：无角标、无话术组合的正常业务弹窗 → 不动 */
    static void popup_normal() {
        FakeNode root = FakeNode.root("android.app.Dialog");
        root.add(FakeNode.node("android.widget.TextView", 200, 900, 880, 1000).txt("确认退出登录吗？"));
        root.add(FakeNode.node("android.widget.Button", 300, 1050, 500, 1170).txt("取消").click());
        root.add(FakeNode.node("android.widget.Button", 580, 1050, 780, 1170).txt("确定").click());
        Ctx ctx = base();
        ctx.popupLive = true;
        check("P3 正常业务弹窗零动作", run(root, ctx).action.equals(AdDecisionCore.A_NONE));
    }

    /* ═══════ 热区 / 回弹 ═══════ */

    /** H1：自绘全屏热区（游戏式）→ 应用内窗 900ms 直断；入口窗改走点击梯子 */
    static void hotZone() {
        FakeNode root = FakeNode.root("com.miHoYo.Genshin");
        root.add(FakeNode.node("android.view.View", 0, 0, 1080, 2400).click());
        check("H1a 应用内窗热区 800ms 等待", run(root, splash(800)).action.equals(AdDecisionCore.A_NONE));
        Decision d = run(root, splash(950));
        check("H1b 应用内窗热区 950ms 直断", d.action.equals(AdDecisionCore.A_BACK) && d.reason.equals("hot_zone"));
        // v3.6.0：入口窗（从桌面直接进游戏的开屏）禁返回键 → 坐标梯子
        Decision dRoot = run(root, rootSplash(950));
        check("H1c 入口窗热区 950ms 坐标梯子（禁返回键）",
                dRoot.action.equals(AdDecisionCore.A_TAP_POINT),
                "实际: " + dRoot.action + "/" + dRoot.reason);
    }

    /** R1：跳转目标名单 */
    static void reboundTargets() {
        check("R1a 微信在回弹名单", AdDecisionCore.isAdTarget("com.tencent.mm"));
        check("R1b 浏览器在回弹名单", AdDecisionCore.isAdTarget("com.android.chrome"));
        check("R1c 支付宝在回弹名单", AdDecisionCore.isAdTarget("com.eg.android.AlipayGphone"));
        check("R1d 桌面不在名单", !AdDecisionCore.isAdTarget("com.miui.home"));
    }

    /* ═══════ v3.5.0：点击复验 / 学习位置 / 开屏×关闭 ═══════ */

    /** V1：复验重试梯子——换候选；应用内窗 ≥3 次返回键兜底；入口窗永不返回键 */
    static void verify_retryLadder() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        FakeNode wrongBtn = FakeNode.node("android.widget.ImageView", 940, 90, 1030, 180).click();  // 候选 1：点不动
        FakeNode realBtn = FakeNode.node("android.widget.ImageView", 60, 90, 140, 170).click();   // 候选 2：左上角真跳过
        root.add(adImage).add(wrongBtn).add(realBtn);

        // 第 1 次点击：候选 1（右）
        Decision d1 = run(root, splash(700));
        check("V1a 首次点击候选 1（右上）",
                d1.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d1.bounds[0] == 940,
                "实际: " + d1.action + " @" + (d1.bounds != null ? d1.bounds[0] : -1));

        // 点击后广告没关（同窗 clickAttempts=1）→ 复验换候选 2（左上真跳过）
        Ctx retry = splash(2200);
        retry.clickAttempts = 1;
        Decision d2 = run(root, retry);
        check("V1b 复验失败换候选 2（左上）",
                d2.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d2.bounds[0] == 60,
                "实际: " + d2.action + " @" + (d2.bounds != null ? d2.bounds[0] : -1));

        // 应用内窗：3 次都点不动 → 返回键兜底（「日志显示已点、实际没反应」的最终兜底）
        Ctx giveup = splash(4200);
        giveup.clickAttempts = 3;
        Decision d3 = run(root, giveup);
        check("V1c 应用内窗点击 ≥3 次仍同窗 → 返回键兜底",
                d3.action.equals(AdDecisionCore.A_BACK) && d3.reason.equals("click_verify_fail"),
                "实际: " + d3.action + "/" + d3.reason);

        // v3.6.0：入口窗（冷启动开屏）永不返回键 → 继续点击梯子（三位置轮换）
        Ctx rootGiveup = rootSplash(4200);
        rootGiveup.clickAttempts = 3;
        Decision d4 = run(root, rootGiveup);
        check("V1d 入口窗点击 ≥3 次 → 继续坐标梯子（绝不返回键防退出）",
                d4.action.equals(AdDecisionCore.A_TAP_POINT),
                "实际: " + d4.action + "/" + d4.reason);

        // v3.6.0：点击预算用尽（角落 2 + 坐标 3 = 5 次；第 6 次起服务侧收手）→ 决策核心三位置用尽即静默
        Ctx exhausted = rootSplash(6000);
        exhausted.clickAttempts = 6;
        check("V1e 点击梯子用尽 → 优雅收手（零动作）",
                run(root, exhausted).action.equals(AdDecisionCore.A_NONE),
                "实际: " + run(root, exhausted).action);
    }

    /** V2：开屏×关闭键 → 顶部小关闭键 + 广告形态树 → 代点 */
    static void splash_closeKey() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        FakeNode close = FakeNode.node("android.widget.TextView", 990, 100, 1040, 150).txt("×").click();
        root.add(adImage).add(close);

        Decision d = run(root, splash(700));
        check("V2 开屏×关闭键代点（顶部 + 广告形态树）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("splash_close"),
                "实际: " + d.action + "/" + d.reason);

        // 底部关闭键（隐私/业务弹窗形态）→ 不点
        FakeNode root2 = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode body = FakeNode.node("android.widget.TextView", 100, 900, 980, 1000)
                .txt("发现新版本 8.4.0，更新即刻体验全新首页与更多权益内容");
        FakeNode close2 = FakeNode.node("android.widget.Button", 480, 1800, 600, 1900).txt("暂不").click();
        root2.add(body).add(close2);
        Decision d2 = run(root2, splash(1200));
        check("V2b 底部「暂不」不当广告×点",
                !(d2.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && "splash_close".equals(d2.reason)),
                "实际: " + d2.action + "/" + d2.reason);
    }

    /** V3：长文本内容页（文章/隐私条款）→ 绝不坐标兜底/不点关闭 */
    static void longText_guard() {
        FakeNode root = FakeNode.root("com.sina.weibo.ArticleActivity");
        FakeNode article = FakeNode.node("android.widget.TextView", 60, 200, 1020, 2100)
                .txt("这是一篇很长的正文文章内容，超过四十个字符就会触发长文本豁免，引擎不应把它当广告页处理");
        FakeNode closeTop = FakeNode.node("android.widget.ImageView", 1000, 90, 1060, 150).txt("关闭").click();
        root.add(article).add(closeTop);
        Decision d = run(root, splash(1500));
        check("V3 长文本页零动作（不点顶部关闭、不坐标兜底）",
                d.action.equals(AdDecisionCore.A_NONE), "实际: " + d.action + "/" + d.reason);
    }

    /** V4：学习位置点击（手动教一次就会）→ 500ms 直点；失败不重复点 */
    static void learnedPos_tap() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        root.add(adImage);

        Ctx ctx = splash(560);
        ctx.tapHintX = 985;
        ctx.tapHintY = 115;
        Decision d = run(root, ctx);
        check("V4a 学习位置 500ms 直点",
                d.action.equals(AdDecisionCore.A_TAP_POINT) && d.reason.equals("learned_pos")
                        && d.x == 985 && d.y == 115,
                "实际: " + d.action + "/" + d.reason + " @" + d.x + "," + d.y);

        // 已点过一次没关（clickAttempts=1）→ 不再点学习位置，转坐标梯子
        Ctx retry = splash(2200);
        retry.tapHintX = 985;
        retry.tapHintY = 115;
        retry.clickAttempts = 1;
        Decision d2 = run(root, retry);
        check("V4b 学习位置失败不重复点（转入坐标梯子）",
                !(d2.reason.equals("learned_pos")), "实际: " + d2.action + "/" + d2.reason);

        // 未到时机（450ms）→ 不点
        Ctx early = splash(450);
        early.tapHintX = 985;
        early.tapHintY = 115;
        Decision d3 = run(root, early);
        check("V4c 学习位置 500ms 前不点", d3.action.equals(AdDecisionCore.A_NONE),
                "实际: " + d3.action + "/" + d3.reason);
    }

    /** V6：信息流/正常界面即便有学习位置指纹也绝不点（L1.5 安全门） */
    static void learnedPos_safetyGate() {
        FakeNode root = FakeNode.root("com.sina.weibo.HomeActivity");
        FakeNode feed = FakeNode.node("androidx.recyclerview.widget.RecyclerView", 0, 200, 1080, 2300).scroll();
        FakeNode avatar = FakeNode.node("android.widget.ImageView", 950, 120, 1040, 210).click();
        feed.add(avatar);
        root.add(feed);
        Ctx ctx = splash(800);
        ctx.tapHintX = 995;
        ctx.tapHintY = 165; // 用户在信息流点过头像的位置
        Decision d = run(root, ctx);
        check("V6 信息流界面有位置指纹也零动作（安全门）",
                d.action.equals(AdDecisionCore.A_NONE), "实际: " + d.action + "/" + d.reason);
    }

    /** V5：角落图片自身不可点、父容器可点 → 候选以父为代点目标、以子为点击坐标 */
    static void corner_ancestor() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        FakeNode wrap = FakeNode.node("android.view.View", 930, 80, 1040, 190).click();
        FakeNode icon = FakeNode.node("android.widget.ImageView", 950, 95, 1020, 165); // 不可点
        wrap.add(icon);
        root.add(adImage).add(wrap);

        Decision d = run(root, splash(700));
        check("V5 不可点图片经可点父容器命中，点子控件坐标",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("corner_image_skip")
                        && d.bounds[0] == 950,
                "实际: " + d.action + "/" + d.reason + " @" + (d.bounds != null ? d.bounds[0] : -1));
    }

    /* ═══════ v3.6.0：可学形状（服务侧位置学习安全门） ═══════ */

    /** G4：learnableSkipShape——角落/顶部/底部小控件可学，中央大按钮不学 */
    static void learnableShapes() {
        check("G4a 右上角落控件可学",
                AdDecisionCore.learnableSkipShape(new int[]{940, 90, 1030, 180}, 1080, 2400));
        check("G4b 顶部 25% 内小控件可学",
                AdDecisionCore.learnableSkipShape(new int[]{400, 150, 560, 260}, 1080, 2400));
        check("G4c 底部 20% 内小控件可学（部分应用跳过键在右下，真机反馈）",
                AdDecisionCore.learnableSkipShape(new int[]{950, 2160, 1040, 2260}, 1080, 2400));
        check("G4d 屏幕中央按钮不学（CTA 高危区）",
                !AdDecisionCore.learnableSkipShape(new int[]{400, 1100, 680, 1300}, 1080, 2400));
        check("G4e 半屏宽按钮不学", !AdDecisionCore.learnableSkipShape(new int[]{100, 2100, 980, 2200}, 1080, 2400));
    }

    /* ═══════ v3.7.0：issue#10 根因修复（多候选轮换/学习位置前置/点击优先/大树角落键/位置健全性） ═══════ */

    /** N1：文本跳过键多候选轮换；v3.8.0 walk 内候选排序（selfClick/短文案/小面积）
     *  后小面积真键（右上）排前——防「第 1 候选=图内假文本」；复验仍换第 2 个候选 */
    static void v370_candRotate() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.SplashAdActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        FakeNode fake = FakeNode.node("android.widget.TextView", 60, 1800, 260, 1900).txt("跳过广告").click(); // 图内假跳过（DFS 先出现）
        FakeNode real = FakeNode.node("android.widget.TextView", 880, 90, 1030, 180).txt("跳过广告").click(); // 右上真键
        root.add(adImage).add(fake).add(real);

        Decision d1 = run(root, splash(600));
        check("N1a 首次点候选 1（v3.8.0 排序：小面积真键 880 在前，防首选=图内假文本）",
                d1.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d1.bounds[0] == 880,
                "实际: " + d1.action + " @" + (d1.bounds != null ? d1.bounds[0] : -1));

        Ctx retry = splash(1800);
        retry.clickAttempts = 1;
        Decision d2 = run(root, retry);
        check("N1b 复验第 2 次换候选 2（左下坐标 60，不再盲点同一坐标）",
                d2.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d2.bounds[0] == 60,
                "实际: " + d2.action + " @" + (d2.bounds != null ? d2.bounds[0] : -1));

        Ctx retry2 = splash(3000);
        retry2.clickAttempts = 2;
        Decision d3 = run(root, retry2);
        check("N1c 候选轮换用尽后停在最后一个候选（idx=min(N,size-1)，60）",
                d3.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d3.bounds[0] == 60,
                "实际: " + d3.action + " @" + (d3.bounds != null ? d3.bounds[0] : -1));
    }

    /** N2：学习位置前置到 L1 之前（issue#10 根因④）——教过的位置
     *  （用户手指点通过的坐标）优先于文本候选盲点 */
    static void v370_learnedPosFirst() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.SplashAdActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        FakeNode skip = FakeNode.node("android.widget.TextView", 880, 90, 1030, 180).txt("跳过广告").click();
        root.add(adImage).add(skip);

        Ctx ctx = splash(600);
        ctx.tapHintX = 955;
        ctx.tapHintY = 135;
        Decision d = run(root, ctx);
        check("N2a 学习位置优先于文本候选（教过的位置先直点）",
                d.action.equals(AdDecisionCore.A_TAP_POINT) && d.reason.equals("learned_pos") && d.x == 955,
                "实际: " + d.action + "/" + d.reason + " @" + d.x);

        // 学习位置也点过没关（attempts≥1）→ 回到文本候选轮换
        Ctx retry = splash(1800);
        retry.tapHintX = 955;
        retry.tapHintY = 135;
        retry.clickAttempts = 1;
        Decision d2 = run(root, retry);
        check("N2b 学习位置点过未关 → 复验走文本候选（多候选轮换）",
                d2.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d2.reason.equals("skip_text"),
                "实际: " + d2.action + "/" + d2.reason);
    }

    /** N3：点击优先于陀螺仪返回键（issue#10 微博根因⑤）——
     *  v3.6.0 陀螺仪文案 120ms 抢先返回键、角落键 500ms 才轮到 */
    static void v370_clickBeforeGyro() {
        FakeNode root = FakeNode.root("com.sina.weibo.ad.AdActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        FakeNode gyroHint = FakeNode.node("android.widget.TextView", 240, 2200, 840, 2280).txt("摇一摇或翻转手机查看详情");
        FakeNode corner = FakeNode.node("android.widget.ImageView", 940, 90, 1030, 180).click(); // 角落图片跳过键
        root.add(adImage).add(gyroHint).add(corner);

        // 200ms：角落未到时机（500ms）但有候选 → 等待（v3.6.0 会被 gyro_immediate 120ms 抢先返回键）
        check("N3a 200ms 有角落候选时不抢跑返回键（等点击时机）",
                run(root, splash(200)).action.equals(AdDecisionCore.A_NONE),
                "实际: " + run(root, splash(200)).action + "/" + run(root, splash(200)).reason);

        // 700ms：角落候选命中 → 点击（不是返回键）
        Decision d = run(root, splash(700));
        check("N3b 700ms 角落候选优先于陀螺仪返回键（点击优先）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("corner_image_skip"),
                "实际: " + d.action + "/" + d.reason);

        // 无任何可点候选的纯翻转文案 → 120ms 立即直断仍生效（翻转抢在 ~600ms 触发前）
        FakeNode root2 = FakeNode.root("com.sina.weibo.ad.AdActivity");
        FakeNode ad2 = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        FakeNode g2 = FakeNode.node("android.widget.TextView", 240, 2200, 840, 2280).txt("翻转手机，跳转详情页或第三方应用");
        root2.add(ad2).add(g2);
        Decision d3 = run(root2, splash(150));
        check("N3c 无可点候选的纯翻转文案 → 120ms 立即直断保留",
                d3.action.equals(AdDecisionCore.A_BACK) && d3.reason.equals("gyro_immediate"),
                "实际: " + d3.action + "/" + d3.reason);
    }

    /** N4：大广告树（>40 节点）角落键照点——v3.6.0 被 AD_LIKE_MAX_NODES=40
     *  误伤（issue#10 微博根因：广告窗树常大，真角落键被挡在门外） */
    static void v370_bigTreeCorner() {
        FakeNode root = FakeNode.root("com.sina.weibo.ad.AdActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        FakeNode corner = FakeNode.node("android.widget.ImageView", 940, 90, 1030, 180).click();
        root.add(adImage).add(corner);
        // 塞 60 个零文本不可点装饰节点 → 总节点 63 > 40（v3.6.0 上限）
        for (int i = 0; i < 60; i++) {
            root.add(FakeNode.node("android.view.View", 100 + i, 2000, 160 + i, 2060));
        }
        Decision d = run(root, splash(700));
        check("N4 大树（63 节点 > 40）角落键照点（v3.7.0 放宽至 150）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("corner_image_skip"),
                "实际: " + d.action + "/" + d.reason);
    }

    /** N5：学习位置健全性——越界/贴边坐标拒学（issue#10 真机日志 (63%,102%) 越界白学） */
    static void v370_sanePos() {
        check("N5a 屏内有效位置可学（(63%,5%) 右上跳过键）", AdDecisionCore.saneNormPos(0.63, 0.05));
        check("N5b 越界 Y=102% 拒学（真机日志实锤）", !AdDecisionCore.saneNormPos(0.63, 1.02));
        check("N5c 越界 X=104% 拒学", !AdDecisionCore.saneNormPos(1.04, 0.5));
        check("N5d 贴边 0% 拒学（状态栏/边缘死区）", !AdDecisionCore.saneNormPos(0.0, 0.5));
    }

    /* ═══════ v3.8.0：issue#10 二轮（GKD 方法论对齐） ═══════ */

    /** v380-1：微博实锤——全屏可点大容器 + 不可点「跳过」标签：isSmallClick 判假
     *  （v3.7.0 从不点击只返回键，返回键又被广告拦截）→ 假跳过手势候选：
     *  dispatchGesture 直点标签自身坐标（clickCenter），绝不 performAction 大容器 */
    static void v380_weiboFakeSkipGesture() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click();
        FakeNode label = FakeNode.node("android.widget.TextView", 950, 80, 1050, 160).txt("跳过"); // 不可点
        adImage.add(label);
        root.add(adImage);

        Decision d = run(root, splash(600)); // 非根窗口
        check("v380-1a 微博假跳过 → 手势直点标签坐标（fake_skip_tap）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("fake_skip_tap")
                        && d.bounds != null && d.bounds[0] == 950 && d.bounds[1] == 80,
                "实际: " + d.action + "/" + d.reason);
    }

    /** v380-2：中央「跳过」标签 = CTA 诱饵——不手势直点；fakeSkip=true →
     *  应用内窗 800ms 走 gyro_break 返回键（实际决策），绝无 A_CLICK_BOUNDS */
    static void v380_centerBaitNoTap() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click();
        FakeNode label = FakeNode.node("android.widget.TextView", 400, 1100, 700, 1250).txt("跳过"); // 中央诱饵
        adImage.add(label);
        root.add(adImage);

        Decision d = run(root, splash(900)); // 应用内窗
        check("v380-2a 中央诱饵「跳过」不点（learnableSkipShape 拒收；实际走 gyro_break 返回键）",
                !d.action.equals(AdDecisionCore.A_CLICK_BOUNDS),
                "实际: " + d.action + "/" + d.reason);
    }

    /** v380-3：微博 iv_skip——零文本图片跳过键，vid 含 skip（GKD 全局规则同款） */
    static void v380_vidSkipImage() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        root.add(FakeNode.node("android.widget.ImageView", 950, 80, 1050, 160).click()
                .vid("com.sina.weibo:id/iv_skip")); // 零文本
        Decision d = run(root, splash(600));
        check("v380-3a vid=iv_skip 零文本图片跳过键 → skip_text 点击",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("skip_text"),
                "实际: " + d.action + "/" + d.reason);
    }

    /** v380-4：穿山甲 tt_splash_skip_btn（vid 含 skip 的开屏官方跳过键） */
    static void v380_ttSplashBtn() {
        FakeNode root = FakeNode.root("com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity");
        root.add(FakeNode.node("android.widget.TextView", 950, 80, 1050, 160).click()
                .vid("com.bytedance.sdk.openadsdk:id/tt_splash_skip_btn"));
        Decision d = run(root, splash(600));
        check("v380-4a vid=tt_splash_skip_btn（穿山甲）→ skip_text 点击",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("skip_text"),
                "实际: " + d.action + "/" + d.reason);
    }

    /** v380-5：倒计时控件 vid（count+down 非 download）——vid 跳过键第二形态 */
    static void v380_countdownVid() {
        FakeNode root = FakeNode.root("com.test.app.SplashActivity");
        root.add(FakeNode.node("android.widget.TextView", 950, 80, 1050, 160).click()
                .vid("com.test.app:id/splash_count_down"));
        Decision d = run(root, splash(600));
        check("v380-5a vid=splash_count_down 倒计时控件 → skip_text 点击",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("skip_text"),
                "实际: " + d.action + "/" + d.reason);
    }

    /** v380-6：不可见「跳过」候选（visibleToUser=false）= 陷阱/死区——全过滤 */
    static void v380_invisibleDecoy() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        root.add(FakeNode.node("android.widget.TextView", 950, 80, 1050, 160).txt("跳过").click().invisible());
        Decision d = run(root, splash(600));
        check("v380-6a 不可见「跳过」候选被过滤（GKD [visibleToUser=true] 同款）",
                d.action.equals(AdDecisionCore.A_NONE),
                "实际: " + d.action + "/" + d.reason);
    }

    /** v380-7：parseCountdown——跳过文案倒计时解析（服务侧门控到「倒计时将尽」再出手） */
    static void v380_parseCountdown() {
        check("v380-7a parseCountdown(\"跳过 3\")==3", AdDecisionCore.parseCountdown("跳过 3") == 3);
        check("v380-7b parseCountdown(\"3s后可跳过\")==3", AdDecisionCore.parseCountdown("3s后可跳过") == 3);
        check("v380-7c parseCountdown(\"跳过广告 5\")==5", AdDecisionCore.parseCountdown("跳过广告 5") == 5);
        check("v380-7d parseCountdown(\"跳过\")==0", AdDecisionCore.parseCountdown("跳过") == 0);
        check("v380-7e parseCountdown(\"0元领福利\")==0（营销 0 非倒计时）",
                AdDecisionCore.parseCountdown("0元领福利") == 0);
        check("v380-7f parseCountdown(\"跳过 15\")==15（上限内）",
                AdDecisionCore.parseCountdown("跳过 15") == 15);
        check("v380-7g parseCountdown(\"跳过 16\")==0（超上限拒识）",
                AdDecisionCore.parseCountdown("跳过 16") == 0);
        check("v380-7h parseCountdown(\"满300减50\")==0（满减营销数字）",
                AdDecisionCore.parseCountdown("满300减50") == 0);
        check("v380-7i parseCountdown(\"跳过 10\")==10", AdDecisionCore.parseCountdown("跳过 10") == 10);
    }

    /** v380-8：角落键先于 SDK 直断（v3.8.0 角落块前置到 L2 之前；
     *  v3.7.0 时代 800ms 会先 BACK——「有可点目标绝不先按返回键」） */
    static void v380_cornersBeforeSdkBack() {
        FakeNode root = FakeNode.root("com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity");
        root.add(FakeNode.node("android.widget.ImageView", 950, 80, 1050, 160).click()); // 角落形态无字
        Ctx ctx = rootSplash(800);
        ctx.sdkAdWin = true;   // SDK 窗豁免（backSafe）
        ctx.clickAttempts = 0; // 800ms：已过 500ms 角落时机、也过 700ms 直断线
        Decision d = run(root, ctx);
        check("v380-8a 角落键先于 SDK 直断（800ms 点击而非 BACK）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("corner_image_skip"),
                "实际: " + d.action + "/" + d.reason);
    }

    /** v380-9：候选排序——短文案真键优先于长文案大标签（selfClick/短文案/小面积） */
    static void v380_candidateOrdering() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.SplashAdActivity");
        // 两个小可点「跳过」类 TextView 都在角落：短文案（右上）+ 长文案（左上，面积更大）
        FakeNode shortKey = FakeNode.node("android.widget.TextView", 880, 90, 1030, 180).txt("跳过").click();
        FakeNode longKey = FakeNode.node("android.widget.TextView", 60, 90, 300, 180).txt("跳过广告 5s后可跳过").click();
        root.add(shortKey).add(longKey);

        Decision d = run(root, splash(600));
        check("v380-9a 候选排序：短文案真键（880）优先于长文案大标签（60）",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("skip_text")
                        && d.bounds != null && d.bounds[0] == 880,
                "实际: " + d.action + "/" + d.reason + " @" + (d.bounds != null ? d.bounds[0] : -1));
    }

    /* ═══════ 断言工具 ═══════ */

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        }
    }

    public static void main(String[] args) {
        System.out.println("═══ AdDecisionCore JVM 单测（v3.8.0 · 真机场景建模 · 屏幕 1080x2400）═══\n");

        System.out.println("── 微博类：第一方图片开屏（真机反馈主场景）──");
        weibo_cornerSkip();
        weibo_coordFallback();
        weibo_delayedText();

        System.out.println("── 喜马拉雅类：零文本 / SDK 窗 / 翻转诱导 / 双返回退出事故 ──");
        ximalaya_cornerBeforeFlip();
        ximalaya_sdkWindow();
        gyro_immediate();
        fakeSkipTrap();
        g1_mainActivityPoison();

        System.out.println("── 误报防护（正常界面绝不误点/误返回）──");
        noFalsePositive_feed();
        noFalsePositive_edit();
        noFalsePositive_privacy();
        noFalsePositive_download();
        guard_protection();
        coordWindow();

        System.out.println("── 文本跳过键全形态（v3.6.0 点原标签坐标）──");
        textSkip();
        descSkip();
        videoSkip();

        System.out.println("── 应用内弹窗广告 ──");
        popup_badge();
        popup_ctaOnly();
        popup_normal();

        System.out.println("── 热区 / 回弹 ──");
        hotZone();
        reboundTargets();

        System.out.println("── 点击复验 / 学习位置 / 开屏×关闭 ──");
        verify_retryLadder();
        splash_closeKey();
        longText_guard();
        learnedPos_tap();
        learnedPos_safetyGate();
        corner_ancestor();

        System.out.println("── v3.6.0 可学形状（位置学习安全门）──");
        learnableShapes();

        System.out.println("── v3.7.0：issue#10 根因修复（多候选轮换/学习位置前置/点击优先/大树/位置健全性）──");
        v370_candRotate();
        v370_learnedPosFirst();
        v370_clickBeforeGyro();
        v370_bigTreeCorner();
        v370_sanePos();

        System.out.println("── v3.8.0：issue#10 二轮（GKD 方法论对齐）──");
        v380_weiboFakeSkipGesture();
        v380_centerBaitNoTap();
        v380_vidSkipImage();
        v380_ttSplashBtn();
        v380_countdownVid();
        v380_invisibleDecoy();
        v380_parseCountdown();
        v380_cornersBeforeSdkBack();
        v380_candidateOrdering();

        System.out.println("\n═══ 结果：" + pass + " PASS / " + fail + " FAIL ═══");
        if (fail > 0) {
            System.out.println("存在失败用例，禁止发版。");
            System.exit(1);
        }
        System.out.println("全部通过，允许构建发版。");
    }
}
