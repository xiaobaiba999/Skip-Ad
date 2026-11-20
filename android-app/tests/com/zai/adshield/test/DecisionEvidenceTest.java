package com.zai.adshield.test;

import com.zai.adshield.SubscriptionManager;
import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.AdTaskCore;
import com.zai.adshield.core.DecisionEvidence;
import com.zai.adshield.core.VideoSplashGuard;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * L3 前置 · 真实世界证据层测试（v7.9.5 新套件）。
 *
 * 覆盖面（用户指令二/三/六/七/八）：
 *  · DecisionEvidence 证据模式与隐私红线（最小结构化 · 白名单标签闸 ·
 *    有界环形缓冲 · 无树容器/无位图——结构性隐私强制）；
 *  · 点击结果闭环六态计数（VERIFIED_SUCCESS/RETRY/MISS/FALSE_CLICK/
 *    SAFE_BLOCK/UNKNOWN——dispatchGesture 成功 ≠ 广告关闭）；
 *  · WebView 综合证据路径（真广告+ScrollView/Input/WebView 综合判断——
 *    不能简单取消安全门：WebView 单独在场不构成证据）；
 *  · 视频开屏自适应轮询节拍（低证据降频 3s / 终态 3s / 标准 1.5s）；
 *  · 订阅证据链八格计数器（consulted/matched/candidateAccepted/clicked/
 *    verifiedSuccess/clickFail/falseClick/blocked）。
 *
 * 诚实口径：FALSE_CLICK 在自动化环境恒 0（真机样本回传唯一来源）；
 * 本套件零安全门放宽——每条断言对应 v7.9.5 生产契约或既有红线。
 */
public final class DecisionEvidenceTest {

    public static void main(String[] args) {
        s1_evidenceSchemaAndPrivacy();
        s2_outcomeTaxonomy();
        s3_stateNames();
        s4_webviewCompositeGate();
        s5_adaptivePollCadence();
        s6_subscriptionEvidenceChain();
        TestHost.done();
    }

    /* ══════════ S1 · 证据模式与隐私红线 ══════════ */
    static void s1_evidenceSchemaAndPrivacy() {
        TestHost.suite("证据层 S1 · 最小结构化证据与隐私红线");
        DecisionEvidence.resetForJvm();
        DecisionEvidence.recordDecision(
                "com.example.app", "com.example.app.SplashActivity", "1700000000#3",
                7, "READY",
                DecisionEvidence.SRC_TEXT, "skip_text", "跳过", "com.example.app:id/iv_skip",
                true, false, false, false,
                0.93, 0.06, 0.002, true, "BND_OK");
        DecisionEvidence.Evidence e = DecisionEvidence.latest();
        TestHost.check(e != null, "证据: 记录后可读（latest 在场）");
        if (e != null) {
            TestHost.checkEq("com.example.app", e.pkg, "证据·pkg: 包名字段保留");
            TestHost.checkEq("com.example.app.SplashActivity", e.act, "证据·act: Activity 字段保留");
            TestHost.checkEq("1700000000#3", e.task, "证据·task: 任务 ID（winStart#seq）");
            TestHost.checkEq(7, e.gen, "证据·gen: 扫描代际");
            TestHost.checkEq("READY", e.st, "证据·st: 派发时任务状态");
            TestHost.checkEq(DecisionEvidence.SRC_TEXT, e.src, "证据·src: 候选来源=text");
            TestHost.checkEq("跳过", e.label, "证据·label: close/skip 词族标签通过白名单闸");
            TestHost.checkEq("com.example.app:id/iv_skip", e.resid, "证据·resid: 开发者资源名保留");
            TestHost.checkEq("TR", e.region, "证据·region: 归一化几何→屏幕区域=右上");
            TestHost.check(e.adCtx && !e.sdkCtx && !e.wvCtx && !e.subCtx, "证据·ctx: 四格上下文如实记录");
            TestHost.checkEq("CLICK_DISPATCHED", e.fin, "证据·fin: finalDecision=CLICK_DISPATCHED");
        }
        String json = DecisionEvidence.toJsonLine();
        TestHost.check(json.contains("\"ev\":\"click_evidence\"") && json.contains("\"reason\":\"skip_text\"")
                        && json.contains("\"reg\":\"TR\"") && json.contains("\"fin\":\"CLICK_DISPATCHED\""),
                "证据·JSON: 最小结构化单行（无树/无位图——只输出最近一条）");
        // 隐私红线：非词族标签一律空串（账号/手机号/支付/聊天内容结构性拒收）
        TestHost.checkEq("", DecisionEvidence.sanitizeToken("13800138000"), "隐私·手机号: 空串拒收");
        TestHost.checkEq("", DecisionEvidence.sanitizeToken("张三的银行账号余额"), "隐私·账号文本: 空串拒收");
        TestHost.checkEq("", DecisionEvidence.sanitizeToken("支付密码验证码"), "隐私·支付文本: 空串拒收");
        TestHost.checkEq("", DecisionEvidence.sanitizeToken("这是一段超长的聊天内容文本记录"), "隐私·长文本: 空串拒收");
        TestHost.checkEq("", DecisionEvidence.sanitizeToken(null), "隐私·null: 空串");
        TestHost.checkEq("跳过", DecisionEvidence.sanitizeToken("跳过"), "隐私·词族: 「跳过」保留");
        TestHost.checkEq("关闭", DecisionEvidence.sanitizeToken("关闭"), "隐私·词族: 「关闭」保留");
        TestHost.checkEq("×", DecisionEvidence.sanitizeToken("×"), "隐私·词族: 「×」保留");
        TestHost.checkEq("", DecisionEvidence.sanitizeToken("跳过领红包再赚88元"), "隐私·非整词: 空串拒收");
        // resourceId 闸：>32 字符 / 含非资源名形态一律拒收
        TestHost.checkEq("", DecisionEvidence.sanitizeResId("com.example.app:id/iv_skip_button_with_very_long_name_over_limit"), "隐私·resid 超长: 空串拒收");
        TestHost.checkEq("", DecisionEvidence.sanitizeResId("含中文的资源名"), "隐私·resid 非资源名形态: 空串拒收");
        TestHost.checkEq("com.example.app:id/iv_skip", DecisionEvidence.sanitizeResId("com.example.app:id/iv_skip"), "隐私·resid 合法: 保留");
        // 有界环形缓冲
        for (int i = 0; i < 24; i++) {
            DecisionEvidence.recordDecision("com.p" + i, "", "", i, "READY",
                    DecisionEvidence.SRC_IMAGE, "corner_image_skip", "", "",
                    false, false, false, false, 0.5, 0.5, 0.001, false, "BND_OK");
        }
        TestHost.checkEq(DecisionEvidence.BUFFER_MAX, DecisionEvidence.bufferSize(),
                "证据·缓冲: 环形有界（24 条记录后仍为 12——绝不无限驻留）");
    }

    /* ══════════ S2 · 点击结果闭环六态 ══════════ */
    static void s2_outcomeTaxonomy() {
        TestHost.suite("闭环 S2 · 六态计数（dispatchGesture 成功 ≠ 广告关闭）");
        DecisionEvidence.resetForJvm();
        int[] base = DecisionEvidence.outcomeSnapshot();
        TestHost.checkEq(0, base[0], "闭环·基线: VERIFIED_SUCCESS=0");
        DecisionEvidence.recordOutcome(DecisionEvidence.OUT_VERIFIED_SUCCESS);
        DecisionEvidence.recordOutcome(DecisionEvidence.OUT_RETRY);
        DecisionEvidence.recordOutcome(DecisionEvidence.OUT_MISS);
        DecisionEvidence.recordOutcome(DecisionEvidence.OUT_SAFE_BLOCK);
        DecisionEvidence.recordOutcome(DecisionEvidence.OUT_UNKNOWN);
        int[] after = DecisionEvidence.outcomeSnapshot();
        TestHost.checkEq(1, after[0], "闭环·VERIFIED_SUCCESS: +1（唯一成功证据=验证链）");
        TestHost.checkEq(1, after[1], "闭环·RETRY: +1（复验仍在场→合法重试等待）");
        TestHost.checkEq(1, after[2], "闭环·MISS: +1（预算用尽未关闭）");
        TestHost.checkEq(0, after[3], "闭环·FALSE_CLICK: 恒 0（真机样本回传唯一来源——绝不虚报）");
        TestHost.checkEq(1, after[4], "闭环·SAFE_BLOCK: +1（宁漏勿误闸在岗证据）");
        TestHost.checkEq(1, after[5], "闭环·UNKNOWN: +1（诚实占位——不硬凑）");
        DecisionEvidence.recordOutcome(99); // 非法值静默拒收
        int[] after2 = DecisionEvidence.outcomeSnapshot();
        TestHost.checkEq(after[0], after2[0], "闭环·非法态: 静默拒收（计数零污染）");
        TestHost.checkEq("VERIFIED_SUCCESS", DecisionEvidence.OUTCOME_NAMES[0], "闭环·词表: 严格使用用户指令词表");
        TestHost.checkEq("FALSE_CLICK", DecisionEvidence.OUTCOME_NAMES[3], "闭环·词表: FALSE_CLICK 术语在场");
    }

    /* ══════════ S3 · 任务状态名（证据字段）══════════ */
    static void s3_stateNames() {
        TestHost.suite("闭环 S3 · 任务状态名（AdTaskCore.stateName）");
        TestHost.checkEq("NONE", AdTaskCore.stateName(AdTaskCore.S_NONE), "状态名·NONE");
        TestHost.checkEq("DETECTED", AdTaskCore.stateName(AdTaskCore.S_DETECTED), "状态名·DETECTED");
        TestHost.checkEq("READY", AdTaskCore.stateName(AdTaskCore.S_READY), "状态名·READY");
        TestHost.checkEq("VERIFYING", AdTaskCore.stateName(AdTaskCore.S_VERIFYING), "状态名·VERIFYING");
        TestHost.checkEq("RETRY_WAIT", AdTaskCore.stateName(AdTaskCore.S_RETRY_WAIT), "状态名·RETRY_WAIT");
        TestHost.checkEq("FEEDBACK", AdTaskCore.stateName(AdTaskCore.S_FEEDBACK), "状态名·FEEDBACK");
        TestHost.checkEq("SUCCESS", AdTaskCore.stateName(AdTaskCore.S_SUCCESS), "状态名·SUCCESS");
        TestHost.checkEq("FAILED", AdTaskCore.stateName(AdTaskCore.S_FAILED), "状态名·FAILED");
        TestHost.checkEq("CANCELLED", AdTaskCore.stateName(AdTaskCore.S_CANCELLED), "状态名·CANCELLED");
        TestHost.checkEq("UNKNOWN", AdTaskCore.stateName(77), "状态名·非法值→UNKNOWN");
    }

    /* ══════════ S4 · WebView 综合证据路径（真广告+ScrollView/Input/WebView）══════════ */
    static void s4_webviewCompositeGate() {
        TestHost.suite("WebView S4 · 综合证据路径（不取消安全门——综合判断）");

        // ① 真广告 + ScrollView：WebView 容器 + 广告角标 + 滚动容器 + 真跳过键 → 出手
        SimNode t1 = webviewAdTree(true, false, 0);
        Ctx c1 = ctxOf("com.example.webad", true, 600);
        ScanFind f1 = AdDecisionCore.walk(t1, c1);
        TestHost.check(f1.webviewPresent && f1.adBadge && f1.scrollPresent, "WebView·①: WebView+角标+滚动三证据可探");
        Decision d1 = AdDecisionCore.decide(f1, c1);
        TestHost.check(d1 != null && "skip_text".equals(d1.reason),
                "WebView·真广告+ScrollView: 跳过键出手（WebView+角标综合判断——isAdLikeLoose 过强阻断的合法出口）");

        // ② 真广告 + 输入框：WebView 容器 + SDK 窗指纹 + EditText + 真跳过键 → 出手
        SimNode t2 = webviewAdTree(false, true, 0);
        Ctx c2 = ctxOf("com.example.webad", true, 600);
        c2.sdkAdWin = true;
        ScanFind f2 = AdDecisionCore.walk(t2, c2);
        TestHost.check(f2.webviewPresent && f2.editPresent, "WebView·②: WebView+输入框证据可探");
        Decision d2 = AdDecisionCore.decide(f2, c2);
        TestHost.check(d2 != null && "skip_text".equals(d2.reason),
                "WebView·真广告+输入框: 跳过键出手（SDK 窗指纹综合判断）");

        // ③ 真广告 + WebView 大树（>150 节点）：WebView + 角标 + 真跳过键 → 出手
        SimNode t3 = webviewAdTree(true, false, 160);
        Ctx c3 = ctxOf("com.example.webad", true, 600);
        ScanFind f3 = AdDecisionCore.walk(t3, c3);
        TestHost.check(f3.nodeCount > 150, "WebView·③: 大树（WebView 视频容器常态 >150——isAdLikeLoose 拒收面）");
        Decision d3 = AdDecisionCore.decide(f3, c3);
        TestHost.check(d3 != null && "skip_text".equals(d3.reason),
                "WebView·真广告+大树: 跳过键出手（WebView+角标综合证据——旧版整窗漏拦的根治面）");

        // ④ 反例红线：应用内浏览器普通页（WebView 单独在场 + 无第二广告语境 + 大树）→ 零出手
        SimNode t4 = webviewPlainTree(160);
        Ctx c4 = ctxOf("com.browser.example", true, 600);
        ScanFind f4 = AdDecisionCore.walk(t4, c4);
        Decision d4 = AdDecisionCore.decide(f4, c4);
        TestHost.check(f4.webviewPresent && f4.nodeCount > 150,
                "WebView·④: WebView 在场且大树（无角标/SDK/Dialog/热区）");
        TestHost.check(d4 == null || AdDecisionCore.A_NONE.equals(d4.action),
                "WebView·反例: 应用内浏览器普通页「跳过」零出手（WebView 单独在场不构成证据——误触红线）");

        // ⑤ 反例红线（v7.9.4 契约保持）：原生滚动页孤立「跳过」→ 零出手
        SimNode t5 = nativeScrollTree();
        Ctx c5 = ctxOf("com.example.chat", true, 6000);
        Decision d5 = AdDecisionCore.decide(AdDecisionCore.walk(t5, c5), c5);
        TestHost.check(d5 == null || AdDecisionCore.A_NONE.equals(d5.action),
                "WebView·反例: 原生滚动页孤立跳过词零出手（v7.9.4 证据门零回退）");

        // ⑥ badge_close 路径：WebView + 角标 + 滚动 + × 关闭键 → 出手
        SimNode t6 = webviewCloseTree(true);
        Ctx c6 = ctxOf("com.example.webad", true, 600);
        ScanFind f6 = AdDecisionCore.walk(t6, c6);
        TestHost.check(f6.foundClose && f6.adBadge && f6.scrollPresent, "WebView·⑥: ×关闭键+角标+滚动可探");
        Decision d6 = AdDecisionCore.decide(f6, c6);
        TestHost.check(d6 != null && "badge_close".equals(d6.reason),
                "WebView·badge_close: WebView+角标+滚动的 × 关闭键出手（L1.5 综合判断）");

        // ⑦ 反例：WebView + 滚动 + × 关闭键（无角标/无 SDK/无 Dialog 形态/无热区）→ 零出手
        SimNode t7 = webviewCloseTree(false);
        Ctx c7 = ctxOf("com.browser.example", true, 600);
        Decision d7 = AdDecisionCore.decide(AdDecisionCore.walk(t7, c7), c7);
        TestHost.check(d7 == null || AdDecisionCore.A_NONE.equals(d7.action),
                "WebView·反例: 无第二语境的 WebView 窗 × 键零出手（不因 WebView 使命中——宁漏勿误）");
    }

    /* ══════════ S5 · 视频开屏自适应轮询节拍 ══════════ */
    static void s5_adaptivePollCadence() {
        TestHost.suite("轮询 S5 · 自适应节拍（20s ceiling 保持 · 低证据降频）");
        TestHost.checkEq(1500L, VideoSplashGuard.adaptivePollGapMs(false, 0), "节拍·标准: 证据在场 1.5s");
        TestHost.checkEq(1500L, VideoSplashGuard.adaptivePollGapMs(false, 3), "节拍·证据增量中: 3 针零命中仍 1.5s");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(false, 4), "节拍·低证据: ≥4 针零命中降频 3s");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(false, 9), "节拍·低证据尾段: 持续 3s（不恢复高频）");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(true, 0), "节拍·终态: 3s（v7.9.1 契约保持）");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(true, 4), "节拍·终态优先: 任务终态恒 3s");
        // 4 参 pollDue：低证据时段到期判定
        TestHost.check(VideoSplashGuard.pollDue(1000L, 2500L, false, 0), "到期·标准: 1.5s 到期即查（证据在场）");
        TestHost.check(!VideoSplashGuard.pollDue(1000L, 2500L, false, 4), "到期·低证据: 1.5s 未满 3s 间隔不到期");
        TestHost.check(VideoSplashGuard.pollDue(1000L, 4000L, false, 4), "到期·低证据: 3s 间隔到期即查");
        TestHost.check(!VideoSplashGuard.pollDue(1000L, 2500L, true, 0), "到期·终态: 3s 间隔不到期");
        // 20s ceiling 与既有 3 参重载零破坏
        TestHost.checkEq(20000L, VideoSplashGuard.VIDEO_EXT_WINDOW_MS, "节拍·ceiling: 20s 观察窗上限保持（禁无限 poll）");
        TestHost.check(VideoSplashGuard.pollDue(null, 0L, false), "节拍·兼容: 既有 3 参重载零破坏");
    }

    /* ══════════ S6 · 订阅证据链八格 ══════════ */
    static void s6_subscriptionEvidenceChain() {
        TestHost.suite("订阅 S6 · GKD 证据链八格（consulted/matched/...）");
        SubscriptionManager.primeForJvm();
        int[] base = SubscriptionManager.subStatV2Snapshot();
        TestHost.checkEq(8, base.length, "订阅·八格: 快照长度（consulted/matched/candidateAccepted/clicked/verifiedSuccess/clickFail/falseClick/blocked）");
        TestHost.checkEq(0, base[6], "订阅·falseClick: 恒 0 基线（真机样本回传唯一来源）");
        // consulted：matchSources 被咨询即计数（含 noCover——订阅参与证据）
        SubscriptionManager.matchSources("com.unknown.app.nothing", plainTree(),
                ctxOf("com.unknown.app.nothing", true, 600));
        int[] a1 = SubscriptionManager.subStatV2Snapshot();
        TestHost.checkEq(base[0] + 1, a1[0], "订阅·consulted: matchSources 咨询 +1（含未覆盖——参与度证据）");
        // candidateAccepted / clicked / verifiedSuccess / clickFail / blocked 计数口径
        SubscriptionManager.countCandAccepted("rule_sub_gkd1");
        SubscriptionManager.countCandAccepted("skip_text"); // 非订阅不计
        int[] a2 = SubscriptionManager.subStatV2Snapshot();
        TestHost.checkEq(base[1] + 0, a2[1], "订阅·口径: 非订阅 reason 的 candidateAccepted 不计（不虚标）");
        TestHost.checkEq(base[2] + 1, a2[2], "订阅·candidateAccepted: 订阅候选过闸 +1");
        SubscriptionManager.countClickOutcome("rule_sub_gkd1", 1);
        SubscriptionManager.countClickOutcome("rule_sub_gkd1", 0);
        SubscriptionManager.countClickOutcome("rule_sub_gkd1", -1);
        SubscriptionManager.countVerifiedSuccess("rule_sub_gkd1");
        SubscriptionManager.countVerifiedSuccess("skip_text"); // 非订阅不计
        int[] a3 = SubscriptionManager.subStatV2Snapshot();
        TestHost.checkEq(base[3] + 1, a3[3], "订阅·clicked: 点击执行成功 +1");
        TestHost.checkEq(base[5] + 1, a3[5], "订阅·clickFail: 双通道全失败 +1");
        TestHost.checkEq(base[7] + 1, a3[7], "订阅·blocked: 安全闸拦截 +1");
        TestHost.checkEq(base[4] + 1, a3[4], "订阅·verifiedSuccess: 任务 VERIFIED_SUCCESS +1（出手→真关闭闭环）");
        TestHost.checkEq(base[6], a3[6], "订阅·falseClick: 自动化环境恒 0（绝不虚报）");
        // 七分类快照零破坏（既有 S8 断言位置索引）
        int[] legacy = SubscriptionManager.subStatSnapshot();
        TestHost.checkEq(7, legacy.length, "订阅·兼容: 七分类快照保持 7 格（既有断言零破坏）");
        // stateJson 扩展段（真实计数器同源）
        String v2Json = DecisionEvidence.outcomeJson();
        TestHost.check(v2Json.contains("\"VERIFIED_SUCCESS\"") && v2Json.contains("\"UNKNOWN\""),
                "订阅·JSON: 点击结果六态 JSON 行字段在场");
    }

    /* ══════════ 工具 ══════════ */

    /** WebView 广告窗：WebView 容器 + 跳过键 + （badge? 角标 : 输入框）+ （filler 大树）+ （scroll? 滚动容器）。 */
    static SimNode webviewAdTree(boolean withBadge, boolean withEdit, int fillerCount) {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode wv = new SimNode("", 0, 400, 1080, 2000);
        wv.cls = "android.webkit.WebView";
        root.add(wv);
        SimNode skip = TestHost.skipButton("跳过");
        skip.clickable = true;
        root.add(skip);
        if (withBadge) root.add(new SimNode("广告", 60, 2140, 180, 2190));
        if (withEdit) {
            SimNode edit = new SimNode("", 60, 2280, 1020, 2380);
            edit.cls = "android.widget.EditText";
            edit.editable = true;
            root.add(edit);
        }
        if (withBadge) { // ① 的滚动容器（真广告+ScrollView 专项）
            SimNode scroll = new SimNode("", 0, 400, 1080, 2000);
            scroll.cls = "android.widget.ScrollView";
            scroll.scrollable = true;
            root.add(scroll);
        }
        for (int i = 0; i < fillerCount; i++) {
            SimNode seg = new SimNode("", 400, 300 + i * 8, 500, 306 + i * 8);
            seg.cls = "android.view.View";
            root.add(seg);
        }
        return root;
    }

    /** 应用内浏览器普通页：WebView + 跳过字样 + 大树（无角标/SDK/Dialog/热区）。 */
    static SimNode webviewPlainTree(int fillerCount) {
        return webviewAdTree(false, false, fillerCount);
    }

    /** WebView 广告窗 × 关闭键：WebView + × + （badge? 角标 : 无）+ 滚动容器。 */
    static SimNode webviewCloseTree(boolean withBadge) {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode wv = new SimNode("", 0, 400, 1080, 2000);
        wv.cls = "android.webkit.WebView";
        root.add(wv);
        SimNode close = new SimNode("×", 1000, 90, 1050, 140);
        close.clickable = true;
        root.add(close);
        if (withBadge) root.add(new SimNode("广告", 60, 2140, 180, 2190));
        SimNode scroll = new SimNode("", 0, 400, 1080, 2000);
        scroll.cls = "android.widget.ScrollView";
        scroll.scrollable = true;
        root.add(scroll);
        return root;
    }

    /** 原生滚动页：ScrollView + 孤立跳过词（无 WebView/角标/SDK）。 */
    static SimNode nativeScrollTree() {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode scroll = new SimNode("", 0, 0, 1080, 2400);
        scroll.cls = "android.widget.ScrollView";
        scroll.scrollable = true;
        SimNode article = new SimNode("这是一段很长的正文内容，用于模拟正常信息流页面。推荐内容持续更新中。", 60, 200, 1020, 1900);
        scroll.add(article);
        SimNode skipWord = new SimNode("跳过", 900, 80, 1050, 150);
        skipWord.clickable = true;
        scroll.add(skipWord);
        root.add(scroll);
        return root;
    }

    /** 简单纯树（订阅咨询计数用）。 */
    static SimNode plainTree() {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode content = new SimNode("普通内容页", 60, 200, 1020, 1900);
        root.add(content);
        return root;
    }

    static Ctx ctxOf(String pkg, boolean splash, long elapsed) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = pkg + ".MainActivity";
        ctx.elapsedMs = elapsed;
        ctx.splashLive = splash;
        return ctx;
    }
}
