package com.zai.adshield.tests;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;

/**
 * 决策核心 JVM 单测（v3.4.0 · 真实机型场景建模 · 发版前必绿）
 * ─────────────────────────────────────────────────────────────
 * 场景来源：2026-11 真机反馈（微博/喜马拉雅等主流应用开屏拦截失败）
 * + 误报防护（正常界面绝不能被误点/误返回）。
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

    /* ═════════ 微博类：第一方图片开屏（真机反馈主场景） ═════════ */

    /** W1：微博开屏——全屏图片广告 + 右上角无字小可点控件（图片跳过键） */
    static void weibo_cornerSkip() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        FakeNode skipBtn = FakeNode.node("android.widget.ImageView", 940, 90, 1030, 180); // 右上角 90px 方块，无文本
        skipBtn.click();
        root.add(adImage).add(skipBtn);

        // 500ms：窗口类名非 SDK、无文本 → v3.3.0 在此返回 none（真机漏拦）
        Decision dOld = run(root, splash(500));
        check("W1a 微博角落控件 500ms 未到时机应等待", dOld.action.equals(AdDecisionCore.A_NONE));

        // 700ms：角落控件命中 → 点击其中心
        Decision d = run(root, splash(700));
        check("W1b 微博图片跳过键 → 点击角落控件",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("corner_image_skip")
                        && d.bounds != null && d.bounds[0] == 940,
                "实际: " + d.action + "/" + d.reason);
    }

    /** W2：微博开屏变体——连角落控件都没有（纯图）→ 坐标级点击兜底 */
    static void weibo_coordFallback() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        root.add(adImage); // 不可点、无字：纯展示图

        Decision d = run(root, splash(1200));
        check("W2 微博纯图开屏 → 坐标级点击标准跳过位",
                d.action.equals(AdDecisionCore.A_TAP_POINT) && d.x == (int) (1080 * 0.88) && d.y == (int) (2400 * 0.07),
                "实际: " + d.action + " @" + d.x + "," + d.y);

        // 已用过一次 → 不再点
        Ctx used = splash(1500);
        used.coordTapUsed = true;
        check("W2b 坐标兜底每次启动只一次", run(root, used).action.equals(AdDecisionCore.A_NONE));
    }

    /** W3：微博开屏带延迟文本跳过（3s 后出现「跳过广告」）→ 前期坐标兜底抢跑 */
    static void weibo_delayedText() {
        FakeNode root = FakeNode.root("com.sina.weibo.SplashActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        root.add(adImage);
        // 900ms 仍无文本 → 坐标兜底出手（不等 5s 文本）
        check("W3a 无信号期坐标兜底", run(root, splash(950)).action.equals(AdDecisionCore.A_TAP_POINT));

        FakeNode skip = FakeNode.node("android.widget.TextView", 880, 100, 1040, 170).txt("跳过广告").click();
        root.add(skip);
        Decision d = run(root, splash(3200));
        check("W3b 文本出现后优先文本代点", d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("skip_text"));
    }

    /* ═════════ 喜马拉雅类：零文本 + 翻转诱导 ═════════ */

    /** X1：喜马拉雅第一方开屏（WelComeActivity）零文本 + 翻转 900ms 即跳 → 角落控件 600ms 抢先 */
    static void ximalaya_cornerBeforeFlip() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode adImage = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        adImage.click();
        FakeNode skipBtn = FakeNode.node("android.widget.ImageView", 950, 80, 1020, 150); // 右上角 70px
        skipBtn.click();
        root.add(adImage).add(skipBtn);

        Decision d = run(root, splash(650));
        check("X1 喜马拉雅图片跳过键 650ms 抢在翻转(900ms)前",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("corner_image_skip"));
    }

    /** X2：CSJ 穿山甲全屏视频广告（SDK 窗口）→ className 直断 */
    static void ximalaya_sdkWindow() {
        FakeNode root = FakeNode.root("com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity");
        root.add(FakeNode.node("android.view.TextureView", 0, 0, 1080, 2400));
        Ctx ctx = splash(400);
        ctx.sdkAdWin = true; // 服务侧已由 isAdActivityClass 判定
        check("X2a SDK 窗 400ms 未到直断时限应等待", run(root, ctx).action.equals(AdDecisionCore.A_NONE));
        ctx.elapsedMs = 520;
        Decision d = run(root, ctx);
        check("X2b SDK 窗 520ms 返回键直断", d.action.equals(AdDecisionCore.A_BACK) && d.reason.equals("sdk_window"));

        Ctx learned = splash(320);
        learned.sdkAdWin = true;
        learned.learnedCls = true;
        check("X2c 学习指纹 300ms 快断", run(root, learned).action.equals(AdDecisionCore.A_BACK));
    }

    /** X3：翻转文案（无倒计时）→ 120ms 立即直断 */
    static void gyro_immediate() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode hint = FakeNode.node("android.widget.TextView", 200, 2000, 880, 2080).txt("翻转手机，跳转详情页或第三方应用");
        root.add(hint);
        check("X3a 翻转文案 60ms 未到时机应等待", run(root, splash(60)).action.equals(AdDecisionCore.A_NONE));
        Decision d = run(root, splash(150));
        check("X3b 翻转文案 150ms 立即直断", d.action.equals(AdDecisionCore.A_BACK) && d.reason.equals("gyro_immediate"));
    }

    /** X4：假跳过陷阱（图内「跳过 5」大热区）→ 800ms 直断（绝不点陷阱） */
    static void fakeSkipTrap() {
        FakeNode root = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode trap = FakeNode.node("android.widget.TextView", 300, 2050, 780, 2160).txt("跳过 5");
        FakeNode trapWrap = FakeNode.node("android.view.View", 0, 1900, 1080, 2400).click();
        trapWrap.add(trap);
        root.add(trapWrap);
        Decision d = run(root, splash(900));
        check("X4 假跳过陷阱 → 返回键直断而非误点",
                d.action.equals(AdDecisionCore.A_BACK) && d.reason.equals("gyro_break"));
    }

    /* ═════════ 误报防护（正常界面绝不误点/误返回） ═════════ */

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

    /** F4：普通 Activity 名含 Download（非广告）→ 不误判 SDK 窗 */
    static void noFalsePositive_download() {
        check("F4 DownloadActivity 非广告窗",
                !AdDecisionCore.isAdActivityClass("com.sina.weibo.DownloadActivity"));
        check("F4b 穿山甲 stub 窗是广告窗",
                AdDecisionCore.isAdActivityClass("com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity"));
        check("F4c 优量汇窗是广告窗", AdDecisionCore.isAdActivityClass("com.qq.e.ads.ADActivity"));
        check("F4d 宿主 ad.activity 是广告窗", AdDecisionCore.isAdActivityClass("com.example.app.ad.activity.AdActivity"));
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
        ctx.elapsedMs = 520;
        check("F5b 守护应用 SDK 窗照拦", run(root, ctx).action.equals(AdDecisionCore.A_BACK));
        check("F5c icbc 是守护包", AdDecisionCore.isGuardPkg("com.icbc"));
        check("F5d 微博非守护包", !AdDecisionCore.isGuardPkg("com.sina.weibo"));
    }

    /** F6：坐标兜底窗口期外（>4s）零动作 */
    static void coordWindow() {
        FakeNode root = FakeNode.root("com.sina.weibo.HomeActivity");
        root.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400));
        check("F6a 4s 内兜底可用", run(root, splash(3900)).action.equals(AdDecisionCore.A_TAP_POINT));
        check("F6b 4.5s 后不再兜底", run(root, splash(4500)).action.equals(AdDecisionCore.A_NONE));
    }

    /* ═════════ 文本跳过键全形态 ═════════ */

    /** S1：普通文本跳过（知乎式「跳过 3」） */
    static void textSkip() {
        FakeNode root = FakeNode.root("com.zhihu.android.app.ui.activity.LauncherActivity");
        root.add(FakeNode.node("android.widget.TextView", 860, 110, 1030, 180).txt("跳过 3").click());
        Decision d = run(root, splash(300));
        check("S1 文本跳过键直接代点", d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.reason.equals("skip_text"));
    }

    /** S2：contentDescription 形态（小红书式 ImageView desc=跳过广告）+ 父容器可点 */
    static void descSkip() {
        FakeNode root = FakeNode.root("com.xingin.xhs.index.IndexActivity");
        FakeNode img = FakeNode.node("android.widget.ImageView", 900, 100, 1010, 210).describe("跳过广告");
        FakeNode wrap = FakeNode.node("android.view.View", 880, 80, 1030, 230).click();
        wrap.add(img);
        root.add(wrap);
        Decision d = run(root, splash(400));
        check("S2 desc 跳过键经父容器代点",
                d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) && d.bounds != null && d.bounds[0] == 880);
    }

    /** S3：SurfaceView 视频广告 + 小文本跳过 → 照常代点 */
    static void videoSkip() {
        FakeNode root = FakeNode.root("com.tencent.qqlive.SplashActivity");
        root.add(FakeNode.node("android.view.SurfaceView", 0, 300, 1080, 2100));
        root.add(FakeNode.node("android.widget.TextView", 870, 120, 1030, 190).txt("跳过").click());
        Decision d = run(root, splash(600));
        check("S3 视频广告文本跳过代点", d.action.equals(AdDecisionCore.A_CLICK_BOUNDS));
    }

    /* ═════════ 弹窗广告 ═════════ */

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

    /* ═════════ 热区 / 回弹 ═════════ */

    /** H1：自绘全屏热区（游戏式，无文本节点≤12）→ 900ms 直断 */
    static void hotZone() {
        FakeNode root = FakeNode.root("com.miHoYo.Genshin");
        root.add(FakeNode.node("android.view.View", 0, 0, 1080, 2400).click());
        check("H1a 热区 800ms 等待", run(root, splash(800)).action.equals(AdDecisionCore.A_NONE));
        Decision d = run(root, splash(950));
        check("H1b 热区 950ms 直断", d.action.equals(AdDecisionCore.A_BACK) && d.reason.equals("hot_zone"));
    }

    /** R1：跳转目标名单 */
    static void reboundTargets() {
        check("R1a 微信在回弹名单", AdDecisionCore.isAdTarget("com.tencent.mm"));
        check("R1b 浏览器在回弹名单", AdDecisionCore.isAdTarget("com.android.chrome"));
        check("R1c 支付宝在回弹名单", AdDecisionCore.isAdTarget("com.eg.android.AlipayGphone"));
        check("R1d 桌面不在名单", !AdDecisionCore.isAdTarget("com.miui.home"));
    }

    /* ═══════ v3.5.0：点击复验 / 学习位置 / 开屏×关闭（二次真机反馈） ═══════ */

    /** V1：复验重试梯子——第 1 个角落候选点完没关 → 换第 2 个候选；≥3 次 → 返回键兑底 */
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

        // 3 次都点不动 → 返回键兑底（「日志显示已点、实际没反应」的最终兑底）
        Ctx giveup = splash(4200);
        giveup.clickAttempts = 3;
        giveup.coordTapUsed = true;
        Decision d3 = run(root, giveup);
        check("V1c 点击 ≥3 次仍同窗 → 返回键兑底",
                d3.action.equals(AdDecisionCore.A_BACK) && d3.reason.equals("click_verify_fail"),
                "实际: " + d3.action + "/" + d3.reason);
    }

    /** V2：开屏×关闭键（v3.4.0 发现了却从不点）→ 顶部小关闭键 + 广告形态树 → 代点 */
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

        // 已点过一次没关（clickAttempts=1）→ 不再点学习位置，转候选/兑底链
        Ctx retry = splash(2200);
        retry.tapHintX = 985;
        retry.tapHintY = 115;
        retry.clickAttempts = 1;
        retry.coordTapUsed = true;
        Decision d2 = run(root, retry);
        check("V4b 学习位置失败不重复点（转入兑底链）",
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

    /* ═════════ 断言工具 ═════════ */

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
        System.out.println("═══ AdDecisionCore JVM 单测（v3.4.0 · 真机场景建模 · 屏幕 1080x2400）═══\n");

        System.out.println("── 微博类：第一方图片开屏（真机反馈主场景）──");
        weibo_cornerSkip();
        weibo_coordFallback();
        weibo_delayedText();

        System.out.println("── 喜马拉雅类：零文本 / SDK 窗 / 翻转诱导 ──");
        ximalaya_cornerBeforeFlip();
        ximalaya_sdkWindow();
        gyro_immediate();
        fakeSkipTrap();

        System.out.println("── 误报防护（正常界面绝不误点）──");
        noFalsePositive_feed();
        noFalsePositive_edit();
        noFalsePositive_privacy();
        noFalsePositive_download();
        guard_protection();
        coordWindow();

        System.out.println("── 文本跳过键全形态 ──");
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

        System.out.println("── v3.5.0：点击复验 / 学习位置 / 开屏×关闭（二次真机反馈）──");
        verify_retryLadder();
        splash_closeKey();
        longText_guard();
        learnedPos_tap();
        learnedPos_safetyGate();
        corner_ancestor();

        System.out.println("\n═══ 结果：" + pass + " PASS / " + fail + " FAIL ═══");
        if (fail > 0) {
            System.out.println("存在失败用例，禁止发版。");
            System.exit(1);
        }
        System.out.println("全部通过，允许构建发版。");
    }
}
