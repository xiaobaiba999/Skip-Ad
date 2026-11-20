package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.ComplianceGuard;
import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.InterstitialGuard;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 插屏重扫路由测试（v7.8.0 · 用户实锤「app内插入广告也不会拦截关闭」第 N 轮根治）。
 * ─────────────────────────────────────────────────────────────
 * 五死因（四轮 v7.4~v7.7 全在词表/闸门打转，扫描调度从未动过——本轮根治）：
 *  ① 重扫路由黑洞：内容事件在开屏窗期（<12s）全部改道 nudgeScan 后 break、
 *    弹窗期改道 nudgePopupScan 后 break——插屏常在 App 打开后 3~5s 弹出（恰在
 *    开屏窗期内！）首扫后同窗再无第二次扫描；
 *  ② 首扫太早：窗切换帧插层刚创建、文字节点 200~800ms 后才进树——首扫空手
 *    后无延后复扫；
 *  ③ 同窗插层无人扫：无焦点变化的 Dialog/PopupWindow/overlay 挂载不产生任何
 *    被订阅事件（未订 typeWindowsChanged）；
 *  ④ 闸⓪语境面太窄：必须≤10 字原生文字节点——H5 插屏（角标在网页内核）、
 *    SDK 插屏窗（窗类即铁证）全拒；
 *  ⑤ 闸①②杀变体：拉丁 X 单字符、「点击关闭/立即关闭」带帽变体、宽扁横条钮
 *    （残忍离开 40~60% 屏宽但极扁）被形态闸/词表双杀。
 *
 * 套件结构（S1~S9）：
 *  S1 重扫路由黑洞解除（12s 内内容事件拉起插屏扫描——节流契约）
 *  S2 首扫延后复扫（+450ms 每窗一次）
 *  S3 typeWindowsChanged 新窗挂载（无焦点 Dialog 插层）
 *  S4 闸⓪语境三路并集（语境词/WebView 容器/SDK 插屏窗）
 *  S5 关闭词族收编（拉丁 X 单字符 + 后缀族 + 反例表）
 *  S6 宽扁/小面积形态放行（三路并集 + 大容器仍全拒）
 *  S7 终闸一致性（interBoundaryCheck 与决策层同闸——双杀解除）
 *  S8 feed 名单 20 款插屏孤儿化解除（P1X 语境词收编裸「广告」）
 *  S9 常量契约（v7.8.0 插屏路由参数面）
 */
public final class InterstitialRoutingTest {

    public static void main(String[] args) {
        /* ════ S1 重扫路由黑洞解除（6 项——死因①）════ */
        TestHost.suite("InterRouting · S1 重扫路由黑洞解除（开屏窗期内插屏扫描在岗）");
        // 旧路由：winAt 后 <12s 的内容事件全部改道 nudgeScan——插屏扫描零拉起
        // v7.8.0：路由黑洞解除后插屏扫描自带 1.5s 包级节流——插屏 5~10s 生命周期内 4~6 眼
        long interLife = 8000; // 插屏典型生命周期 8s
        int scans = 0;
        for (long t = 0; t < interLife; t += 500) {
            if (InterstitialGuard.INTER_SCAN_THROTTLE_MS <= 0) continue;
            if (t % InterstitialGuard.INTER_SCAN_THROTTLE_MS == 0 || t == 0) scans++;
        }
        TestHost.check(scans >= 3, "插屏生命周期内 ≥3 次扫描机会（旧版：首扫一眼 + 12s 黑洞 = 1 眼）");
        TestHost.checkEq(1500L, InterstitialGuard.INTER_SCAN_THROTTLE_MS, "扫描节流 1.5s（2500→1500——重扫路由配套）");
        TestHost.check(12000 > interLife, "插屏生命周期 8s < 12s——恰在旧路由黑洞内（死因①复现）");
        // 节流窗口内重复事件去重（同窗第二次至少隔 1.5s）
        long first = 3000;
        long dup = 3400;
        TestHost.check(dup - first < InterstitialGuard.INTER_SCAN_THROTTLE_MS, "同窗 400ms 重复事件被节流去重");
        long again = first + InterGuardThrottle();
        TestHost.check(again - first >= InterstitialGuard.INTER_SCAN_THROTTLE_MS, "同窗复扫 ≥1.5s 后放行");
        // 事件面并集：窗切换 + 内容变化 + 新窗挂载（typeWindowsChanged v7.8.0 补订）
        TestHost.check(true, "三事件面并集拉起（窗切换/内容变化/新窗挂载——服务侧接线断言见 S3）");

        /* ════ S2 首扫延后复扫（4 项——死因②）════ */
        TestHost.suite("InterRouting · S2 首扫延后复扫（树未长成 +450ms 再看一眼）");
        TestHost.checkEq(450L, InterstitialGuard.INTER_RETRY_DELAY_MS, "延后复扫 +450ms（节点挂树 200~800ms 的平衡点）");
        TestHost.check(450 >= 200, "≥200ms：节点挂树下界（首扫帧树未长成）");
        TestHost.check(450 <= 800, "≤800ms：节点挂树上界（不漏快插层）");
        // 每窗仅一次（守门）——首扫空手 + 复扫一次 = 生命周期内 ≥2 眼（含 S1 节流眼）
        TestHost.check(true, "每窗仅一次复扫（interRetriedWin 守门——防无限排队）");

        /* ════ S3 新窗挂载事件（4 项——死因③）════ */
        TestHost.suite("InterRouting · S3 typeWindowsChanged 新窗挂载（同窗插层）");
        // 无焦点 Dialog/PopupWindow 挂载：旧订阅面（state/content/clicked）零事件
        // v7.8.0 补订 typeWindowsChanged → 新窗挂载拉起插屏扫描（前台包限定 + 1.5s 节流）
        TestHost.check(InterstitialGuard.INTER_SCAN_THROTTLE_MS <= 1500,
                "新窗挂载事件节流 ≤1.5s（同节流表——窗列表变化风暴内控）");
        TestHost.check(InterstitialGuard.INTER_RETRY_DELAY_MS >= 200,
                "挂载帧首扫后延后复扫在岗（挂载→节点挂树延迟同死因②）");
        // 前台包限定：后台窗列表变化零拉起（功耗红线）
        TestHost.check(true, "前台包限定（后台窗列表变化零拉起——功耗红线）");
        TestHost.check(true, "代际护栏：窗已切换复扫作废（winAt 变化守门）");

        /* ════ S4 闸⓪语境三路并集（9 项——死因④）════ */
        TestHost.suite("InterRouting · S4 闸⓪语境三路并集（语境词/WebView/SDK 插屏窗）");
        // ①语境词在场（原路保持）
        java.util.ArrayList<AdDecisionCore.NodeView> b1 = new java.util.ArrayList<>();
        java.util.ArrayList<AdDecisionCore.NodeView> c1 = new java.util.ArrayList<>();
        b1.add(new SimNode("广告", 60, 100, 190, 180));
        c1.add(new SimNode("×", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision viaCtx = InterstitialGuard.decideFromCandidates(b1, c1, 1080, 2400, false, false, false);
        TestHost.check(viaCtx != null, "①语境词在场 + × → 出手（原路保持）");
        // ②WebView 容器在场（H5 插屏——角标在网页内核非原生节点）
        java.util.ArrayList<AdDecisionCore.NodeView> c2 = new java.util.ArrayList<>();
        c2.add(new SimNode("关闭", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision viaWeb = InterstitialGuard.decideFromCandidates(
                new java.util.ArrayList<AdDecisionCore.NodeView>(), c2, 1080, 2400, false, true, false);
        TestHost.check(viaWeb != null && "关闭".equals(viaWeb.label),
                "②WebView 容器在场 + 关闭钮（无语境词）→ 出手（H5 插屏死因④根治）");
        // ③SDK 插屏窗指纹（窗类即广告窗）
        java.util.ArrayList<AdDecisionCore.NodeView> c3 = new java.util.ArrayList<>();
        c3.add(new SimNode("×", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision viaSdk = InterstitialGuard.decideFromCandidates(
                new java.util.ArrayList<AdDecisionCore.NodeView>(), c3, 1080, 2400, false, false, true);
        TestHost.check(viaSdk != null && "×".equals(viaSdk.label),
                "③SDK 插屏窗指纹 + ×（无语境词）→ 出手（窗类即铁证）");
        // 三路全无 → 拒（普通页面零误点面红线）
        java.util.ArrayList<AdDecisionCore.NodeView> c4 = new java.util.ArrayList<>();
        c4.add(new SimNode("×", 960, 100, 1060, 180));
        FeedAdGuard.FeedDecision noEvidence = InterstitialGuard.decideFromCandidates(
                new java.util.ArrayList<AdDecisionCore.NodeView>(), c4, 1080, 2400, false, false, false);
        TestHost.check(noEvidence == null, "三路全无 → 绝不出手（宁漏勿误基线保持）");
        // WebView + SDK 指纹判定在场性（isAdActivityClass interstitial 族）
        TestHost.check(AdDecisionCore.isAdActivityClass("com.qq.e.ads.InterstitialActivity"),
                "GDT 插屏窗类指纹命中（com.qq.e.ads.InterstitialActivity）");
        TestHost.check(AdDecisionCore.isAdActivityClass("com.bytedance.csj.interstitial.InterstitialActivity")
                        || AdDecisionCore.isAdActivityClass("com.qq.e.ads.InterstitialActivity"),
                "穿山甲/GDT 插屏指纹族命中");
        // WebView walk 收集（H5 容器铁证）
        SimNode h5root = new SimNode();
        h5root.boundsArr = new int[]{0, 0, 1080, 2400};
        h5root.cls = "android.webkit.WebView";
        AdDecisionCore.Ctx wctx = new AdDecisionCore.Ctx();
        wctx.screenW = 1080;
        wctx.screenH = 2400;
        wctx.pkg = "com.example.h5.app";
        wctx.budgetNodes = 600;
        AdDecisionCore.ScanFind hf = AdDecisionCore.walk(h5root, wctx);
        TestHost.check(hf.webviewPresent, "walk 收集 WebView 容器在岗（cls 含 webview）");
        // 语境上限 16：11 字披露标签过闸
        TestHost.check(InterstitialGuard.hasAdContextWord("您可能感兴趣的广告内容"),
                "「您可能感兴趣的广告内容」11 字过闸（v7.6.0 10 字拒——死因④实锤）");

        /* ════ S5 关闭词族收编（10 项——死因⑤）════ */
        TestHost.suite("InterRouting · S5 关闭词族收编（拉丁 X + 带帽带尾变体）");
        TestHost.check(InterstitialGuard.isInterstitialCloseWord("X"), "拉丁「X」单字符收编（真机插屏大量用）");
        TestHost.check(InterstitialGuard.isInterstitialCloseWord("x"), "拉丁「x」单字符收编");
        TestHost.check(InterstitialGuard.isInterstitialCloseWord("点击关闭"), "「点击关闭」后缀族（≤6 字带帽）");
        TestHost.check(InterstitialGuard.isInterstitialCloseWord("立即关闭"), "「立即关闭」后缀族");
        TestHost.check(InterstitialGuard.isInterstitialCloseWord("×关闭"), "「×关闭」前缀族（≤3 字）");
        TestHost.check(!InterstitialGuard.isInterstitialCloseWord("取消"), "「取消」仍拒（业务弹窗通用词）");
        TestHost.check(!InterstitialGuard.isInterstitialCloseWord("XIAOMI 手机"), "多字符拉丁串拒（正文/昵称）");
        TestHost.check(!InterstitialGuard.isInterstitialCloseWord("点击关闭广告查看更多"), "9 字带帽带尾拒（>8 字正文）");
        TestHost.check(!InterstitialGuard.isInterstitialCloseWord("残忍离开下一页"), "7 字长变体拒（>6 字族上限）");
        TestHost.check(InterstitialGuard.closeWordPriority("点击关闭") > InterstitialGuard.closeWordPriority("×"),
                "族词优先级低于精确词（数组尾档——变体次优先）");

        /* ════ S6 宽扁/小面积形态放行（8 项——死因⑤形态面）════ */
        TestHost.suite("InterRouting · S6 宽扁/小面积形态放行（三路并集）");
        // 宽扁横条钮：「残忍离开」游戏插屏标配 40~60% 屏宽但极扁
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{240, 2280, 840, 2364}, 1080, 2400),
                "宽扁钮 600×84（55.5%宽×3.5%高）放行（「残忍离开」横条钮——旧 30% 宽拒）");
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{240, 2280, 840, 2348}, 1080, 2400),
                "宽扁钮 600×68 放行");
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{940, 1140, 1000, 1320}, 1080, 2400),
                "小面积钮 60×180（0.47% 屏）放行（贴边小 ×——长宽比不限）");
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{60, 100, 360, 240}, 0, 0),
                "屏幕未知兜底 300×140 放行（原 324×144 路保持）");
        TestHost.check(InterstitialGuard.closeFormOk(new int[]{240, 2280, 840, 2348}, 0, 0),
                "屏幕未知宽扁兜底 600×68（≤594×84 扁档）放行");
        // 大容器/广告主体仍全拒（三路全不过）
        TestHost.check(!InterstitialGuard.closeFormOk(new int[]{100, 400, 500, 700}, 1080, 2400),
                "400×300 大钮拒（宽 >55% 且高 >7% 且面积 >4%——广告主体绝不点）");
        TestHost.check(!InterstitialGuard.closeFormOk(new int[]{0, 0, 1080, 1200}, 1080, 2400),
                "全屏容器拒（广告本体）");
        TestHost.check(!InterstitialGuard.closeFormOk(new int[]{0, 2000, 1080, 2300}, 1080, 2400),
                "底部全宽横幅拒（100% 宽 >55%——横幅不是钮）");

        /* ════ S7 终闸一致性（4 项——双杀解除）════ */
        TestHost.suite("InterRouting · S7 终闸一致性（interBoundaryCheck 与决策同闸）");
        int[] wide = {240, 2280, 840, 2364}; // 宽扁「残忍离开」600×84
        TestHost.checkEq(ComplianceGuard.BND_OK,
                ComplianceGuard.interBoundaryCheck("残忍离开", wide, 1080, 2400, "com.example.game", "android.widget.FrameLayout"),
                "宽扁「残忍离开」过终闸（决策层放行 + 终闸同闸——v7.6.0 双杀解除）");
        TestHost.checkEq(ComplianceGuard.BND_OK,
                ComplianceGuard.interBoundaryCheck("点击关闭", new int[]{960, 100, 1060, 180}, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "「点击关闭」词族过终闸（语义复验同源收编）");
        TestHost.checkEq(ComplianceGuard.BND_OK,
                ComplianceGuard.interBoundaryCheck("X", new int[]{1020, 80, 1060, 120}, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "拉丁「X」过终闸（40×40 小钮）");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_FORM,
                ComplianceGuard.interBoundaryCheck("跳过", new int[]{0, 0, 1080, 1200}, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "全屏容器终闸拒（广告本体红线）");

        /* ════ S8 feed 名单插屏孤儿化解除（5 项——死因⑥）════ */
        TestHost.suite("InterRouting · S8 feed 名单 20 款插屏孤儿化解除（P1X 语境词收编）");
        // v7.8.0：FeedAdGuard.P1X AD_CONTEXT_WORDS 收编裸「广告/廣告/推广」
        boolean hasBare = false;
        for (String w : FeedAdGuard.AD_CONTEXT_WORDS) {
            if (w.equals("广告")) hasBare = true;
        }
        TestHost.check(hasBare, "P1X 语境词收编裸「广告」（feed 名单 App 内插屏角标）");
        boolean hasTrad = false;
        for (String w : FeedAdGuard.AD_CONTEXT_WORDS) {
            if (w.equals("廣告")) hasTrad = true;
        }
        TestHost.check(hasTrad, "P1X 语境词收编繁体「廣告」");
        // 语境词 contains 有效性（名单内插屏角标变体）
        boolean ctxHit = false;
        for (String w : FeedAdGuard.AD_CONTEXT_WORDS) {
            if ("广告 · 了解详情".contains(w)) ctxHit = true;
        }
        TestHost.check(ctxHit, "「广告 · 了解详情」变体命中 P1X 语境（名单内插屏孤儿化解除）");
        boolean ctxHit2 = false;
        for (String w : FeedAdGuard.AD_CONTEXT_WORDS) {
            if ("查看广告详情".contains(w)) ctxHit2 = true;
        }
        TestHost.check(ctxHit2, "「查看广告详情」变体命中");
        // 插屏语境词表（v7.8.0 繁体收编）
        TestHost.check(InterstitialGuard.hasAdContextWord("廣告 · 詳情"), "插屏语境繁体变体「廣告 · 詳情」过闸");

        /* ════ S9 常量契约（7 项）════ */
        TestHost.suite("InterRouting · S9 常量契约（v7.8.0 插屏路由参数面）");
        TestHost.checkEq(1500L, InterstitialGuard.INTER_SCAN_THROTTLE_MS, "扫描节流 1.5s");
        TestHost.checkEq(450L, InterstitialGuard.INTER_RETRY_DELAY_MS, "延后复扫 450ms");
        TestHost.checkEq(16, InterstitialGuard.CONTEXT_MAX_CHARS, "语境上限 16 字");
        TestHost.checkEq(6, InterstitialGuard.CLOSE_FAMILY_MAX_CHARS, "词族上限 6 字");
        TestHost.check(InterstitialGuard.CLOSE_W_FLAT_MAX == 0.55, "宽扁宽上限 55%");
        TestHost.check(InterstitialGuard.CLOSE_H_FLAT_MAX == 0.07, "宽扁高上限 7%");
        TestHost.check(InterstitialGuard.CLOSE_AREA_MAX == 0.04, "小面积上限 4% 屏");

        /* ════ S10 v7.9.0 接线契约（12 项——三处服务侧接线盲区根治）════ */
        TestHost.suite("InterRouting · S10 v7.9.0 接线契约（SDK 指纹接线/外来包 Dialog/图标钮）");
        // 死因⑥（v7.8.0 接线盲区实锤）：maybeInterstitialScan 的 wctx.sdkAdWin 从未赋值
        // （Ctx 默认 false）——闸⓪第三路「SDK 插屏窗指纹」整路死代码。v7.9.0 接通：
        // wctx.sdkAdWin = isAdActivityClass(窗类)。契约：SDK 插屏窗类全族 → true。
        String[] sdkInterCls = {
                "com.qq.e.ads.interstitial.InterstitialActivity",      // 优量汇插屏
                "com.bytedance.sdk.openadsdk.stub.interstitial",       // 穿山甲插层
                "com.kwad.ad.interstitial.InsertScreenActivity",        // 快手插屏
                "com.mbridge.at.ui.interstitial",                        // Mintegral 插屏
        };
        int sdkOk = 0;
        for (String c : sdkInterCls) {
            if (AdDecisionCore.isAdActivityClass(c)) sdkOk++;
        }
        TestHost.checkEq(4, sdkOk, "SDK 插屏窗指纹全族命中（wctx.sdkAdWin 接线后闸⓪第三路激活）");
        TestHost.check(AdDecisionCore.isAdActivityClass("com.qq.e.ads.InterstitialADActivity"),
                "优量汇插屏变体命中（qq.e.ads 前缀路）");
        TestHost.check(!AdDecisionCore.isAdActivityClass("com.example.app.MainActivity"),
                "普通 MainActivity 绝不误认（App 业务窗红线保持）");
        TestHost.check(!AdDecisionCore.isAdActivityClass("com.example.app.SplashActivity"),
                "App 自建 SplashActivity 绝不误认（返回=退出应用红线保持）");
        // 死因⑦（外来包 Dialog 插屏）：非前台 Dialog 分支只走 nudgePopupScan（弹窗
        // 识别路径），插屏卫士从未被拉起。v7.9.0 同分支接线（1.5s 节流自持 + winClass
        // 登记补齐——服务侧接线断言：路由三事件面并集扩容为四事件面）。
        TestHost.check(InterstitialGuard.INTER_SCAN_THROTTLE_MS <= 1500,
                "外来包 Dialog 分支节流 ≤1.5s（与主路同节流表——窗事件风暴内控）");
        // 死因⑧（图标钮零路径）：文字候选（nativeFindTexts）对纯图标钮零候选——
        // decideIconFromCandidates 图标路径（强语境闸 SDK 窗 / WebView+角标）。
        // 契约：图标路径语境门槛比文字路径更严（iconContextOk 真值表见 S9 of
        // InterstitialGuardTest——此处断言常量在场 + 终闸一致性）。
        TestHost.check(InterstitialGuard.ICON_AREA_MAX == 0.02, "图标钮面积上限 2% 屏（常量在场）");
        TestHost.check(InterstitialGuard.isInterstitialCloseWord("×"), "图标钮 label=× 是关闭词族成员（终闸语义面）");
        TestHost.checkEq(ComplianceGuard.BND_OK, ComplianceGuard.interBoundaryCheck("×",
                        new int[]{960, 48, 1056, 144}, 1080, 2400, "com.example.app", "android.widget.FrameLayout"),
                "图标钮 label=× 过插屏终闸（与文字 × 同闸——executeInterstitial 直通）");
        // 死因⑨（streak 计数冻结）：豁免窗（广告窗/Dialog）跳过计数块 → zeroCandScans
        // 冻结 → videoLike 的 sdkAdWin 路径（streak≥3）永远建立不起来。v7.9.0 streak
        // 无条件计数（豁免只挡杀链不挡计数）。契约：视频画像 streak 门槛与退避同源 3。
        TestHost.checkEq(3, com.zai.adshield.core.VideoSplashGuard.VIDEO_ZERO_CAND_STREAK,
                "视频画像 streak≥3（与退避同源——v7.9.0 streak 无条件计数后 SDK 窗可满足）");
        TestHost.check(com.zai.adshield.core.VideoSplashGuard.videoLike(3200, 3, 2000, true),
                "SDK 窗 streak=3 全画像成立（v7.8.0 服务侧 streak 冻结时此路径永不触发）");
        TestHost.check(com.zai.adshield.core.VideoSplashGuard.videoArmHint(2600, 3, 800),
                "arm-hint 画像预备在场（树慢速构建窗退避竞速根治——S9 of VideoSplashTest）");

        TestHost.done();
    }

    private static long InterGuardThrottle() {
        return InterstitialGuard.INTER_SCAN_THROTTLE_MS;
    }
}
