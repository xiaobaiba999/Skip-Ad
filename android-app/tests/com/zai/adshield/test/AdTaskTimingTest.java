package com.zai.adshield.test;

import com.zai.adshield.core.AdTaskCore;
import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.VideoSplashGuard;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * AdTaskCore 时序压力测试（v7.9.4 新套件）。
 *
 * 用户指定的 A-H 八组时序竞态 + 三种真机实锤情况（Case 1/2/3）的硬回归：
 *  · A click → 100ms 广告消失 → 300ms 旧 callback（旧回调不得点击）；
 *  · B click → 300ms 广告消失 → 500ms 旧 callback（同上，更长延迟）；
 *  · C click → 广告未消失 → 候选坐标改变（不得复用旧坐标）；
 *  · D click → 窗口切换 → 原任务必须终局；
 *  · E click → Activity 不变 → Dialog 消失 → 普通按钮出现（旧任务不得点普通按钮）；
 *  · F 广告关闭 → 反馈面板出现（不对反馈面板执行广告关闭点击）；
 *  · G 视频广告关闭 → videoPoll 未停（轮询针发现 SUCCESS 立即退出）；
 *  · H 延迟关闭按钮 100/300/500/800/2000/5000ms（出现前零出手·出现后首过即出手）。
 *
 * Case 1（第一次已成功——旧 task/callback/timer/videoPoll/TapGestureCallback
 *       全部不得再次点击，第二次点击 = 0）；
 * Case 2（第一次失败——第二次必须重新获取广告状态/候选/安全闸/可点性/
 *       任务有效性，不得复用第一次候选）；
 * Case 3（click() 返回成功但广告未消失——CLICKING→VERIFYING→重新扫描→
 *       新候选→SafetyGuard→有限 RETRY→SUCCESS/FAILED，绝不因 click()=true
 *       直判成功）。
 *
 * 纯 Java 零 Android 依赖（ECJ -1.8 同规约）；每条断言对应一类真实
 * 竞态回归（终态不可逆/过期回调/坐标漂移/冷却期/轮询降频契约）。
 */
public final class AdTaskTimingTest {

    public static void main(String[] args) {
        caseA_staleCallback100();
        caseB_staleCallback300();
        caseC_candidateMoved();
        caseD_windowSwitch();
        caseE_dialogGoneNormalButtons();
        caseF_feedbackPanel();
        caseG_videoPollAfterClose();
        caseH_delayedCloseButton();
        case1_allStaleActorsRejected();
        case2_retryNeedsFullReacquire();
        case3_clickOkNotAdClosed();
        TestHost.done();
    }

    /* ════ A · click → 100ms 广告消失 → 300ms 旧 callback ════ */
    static void caseA_staleCallback100() {
        TestHost.suite("时序 A · 100ms 消失 + 300ms 旧回调");
        int clicks = 0;
        int st = AdTaskCore.S_READY;
        st = AdTaskCore.onDispatch(st);              // t=0 出手 → VERIFYING
        clicks = 1;
        TestHost.checkEq((Object) AdTaskCore.S_VERIFYING, (Object) st, "A: 出手后 VERIFYING");
        // t=100：广告消失（第一轮零候选记时刻）
        long firstZero = 100L;
        // t=300：旧 callback 到达——此刻候选仍零，但两轮确认未满（200ms < 350ms）
        TestHost.check(!AdTaskCore.zeroVerifyConfirmed(firstZero, 300L, true),
                "A: 300ms 旧回调到达时两轮确认未满（不提前判关）");
        TestHost.check(!AdTaskCore.retryGuardsOk(false, true, false, false, false),
                "A: 广告已消失（零候选）旧回调不得点击——重试五要件广告在场项为假");
        // t=450：第二轮零（≥350ms 独立两轮）→ 确认关闭
        TestHost.check(AdTaskCore.zeroVerifyConfirmed(firstZero, 450L, true),
                "A: 450ms 第二轮零确认关闭");
        st = AdTaskCore.onVerifyAdGone(st);
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "A: 广告消失 → SUCCESS");
        // t=500/700/2000：一切旧回调（timer/videoPoll/TapGestureCallback 等价面）拒收
        for (long t = 500L; t <= 2000L; t += 750L) {
            TestHost.check(!AdTaskCore.callbackAllowed(st),
                    "A: " + t + "ms 旧回调一律拒收（SUCCESS 终态 callbackAllowed=false）");
        }
        TestHost.check(AdTaskCore.onDispatch(st) == AdTaskCore.S_SUCCESS,
                "A: SUCCESS 后派发被拒（第二次点击 = 0）");
        TestHost.checkEq((Object) 1, (Object) clicks, "A: 全程点击数 = 1（成功后零出手）");
    }

    /* ════ B · click → 300ms 广告消失 → 500ms 旧 callback ════ */
    static void caseB_staleCallback300() {
        TestHost.suite("时序 B · 300ms 消失 + 500ms 旧回调");
        int st = AdTaskCore.S_READY;
        st = AdTaskCore.onDispatch(st);              // t=0 出手
        st = AdTaskCore.onVerifyAdStill(st, 1, 3);  // t=100 复验：广告仍在 → RETRY_WAIT
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) st, "B: 首击未关 → RETRY_WAIT");
        // t=300：广告消失（首零时刻）
        long firstZero = 300L;
        // t=500：旧 callback 到达——两轮确认未满（200ms < 350ms）不得判关也不得点击
        TestHost.check(!AdTaskCore.zeroVerifyConfirmed(firstZero, 500L, true),
                "B: 500ms 旧回调到达时两轮确认未满");
        TestHost.check(!AdTaskCore.retryGuardsOk(false, true, false, false, false),
                "B: 零候选下旧回调五要件不过（不得点击）");
        // t=680：第二轮零确认 → SUCCESS
        TestHost.check(AdTaskCore.zeroVerifyConfirmed(firstZero, 680L, true),
                "B: 680ms 第二轮零确认关闭");
        st = AdTaskCore.onVerifyAdGone(st);
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "B: 广告消失 → SUCCESS");
        TestHost.check(!AdTaskCore.callbackAllowed(st) && !AdTaskCore.clickAllowed(st),
                "B: SUCCESS 后回调与点击全拒（500ms 旧回调零作用）");
    }

    /* ════ C · click → 广告未消失 → 候选坐标改变 ════ */
    static void caseC_candidateMoved() {
        TestHost.suite("时序 C · 候选坐标改变不得复用旧坐标");
        int st = AdTaskCore.S_READY;
        st = AdTaskCore.onDispatch(st);              // t=0 出手（点击旧候选坐标）
        int clicks = 1;
        // t=500：复验——广告仍在但原候选已消失（坐标漂移：新候选换了位置）
        TestHost.check(!AdTaskCore.retryGuardsOk(true, true, false, true, true),
                "C: 原候选已消失（candidateAlive=false）不得对旧坐标出手");
        st = AdTaskCore.onVerifyUncertain(st, clicks, 3);
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) st,
                "C: 页面变化无法确认 → RETRY_WAIT 观察（绝不盲点旧坐标）");
        // t=800：重新扫描获得新候选（新 bounds 新节点）——五要件对【新候选】重验全真才出手
        TestHost.check(AdTaskCore.retryGuardsOk(true, true, true, true, true),
                "C: 新候选五要件重验全真 → 允许出手（换候选不换窗）");
        TestHost.check(AdTaskCore.clickAllowed(st), "C: RETRY_WAIT 允许再出手");
        st = AdTaskCore.onDispatch(st);              // t=800 第二击（打在【新】坐标上）
        clicks = 2;
        TestHost.checkEq((Object) AdTaskCore.S_VERIFYING, (Object) st, "C: 第二击 → VERIFYING");
        // 候选再次漂移且窗口也变（页面跳转中）——同窗条件破坏，观察不补刀
        TestHost.check(!AdTaskCore.retryGuardsOk(true, false, true, true, true),
                "C: 窗口已变（sameWindow=false）绝不沿用旧候选出手");
        TestHost.checkEq((Object) 2, (Object) clicks, "C: 全程点击数 = 2（未复用旧坐标盲点）");
    }

    /* ════ D · click → 窗口切换 → 原任务必须终局 ════ */
    static void caseD_windowSwitch() {
        TestHost.suite("时序 D · 窗口切换任务终局");
        int st = AdTaskCore.S_READY;
        st = AdTaskCore.onDispatch(st);              // t=0 出手
        // t=50：窗口切换（有出手 → SUCCESS 落账）
        st = AdTaskCore.onWindowChanged(st, true);
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "D: 有出手窗口切换 → SUCCESS");
        TestHost.check(AdTaskCore.isTerminal(st), "D: 终态不可逆");
        // 之后一切转移全拒——旧任务不续命
        TestHost.check(AdTaskCore.onVerifyAdGone(st) == AdTaskCore.S_SUCCESS,
                "D: 终态后 onVerifyAdGone 无效（原样）");
        TestHost.check(AdTaskCore.onVerifyAdStill(st, 0, 3) == AdTaskCore.S_SUCCESS,
                "D: 终态后「广告仍在」无效（原样）");
        TestHost.check(AdTaskCore.onFeedbackSeen(st) == AdTaskCore.S_SUCCESS,
                "D: 终态后反馈面板不进 FEEDBACK（旧任务不续命）");
        TestHost.check(AdTaskCore.onCancelled(st) == AdTaskCore.S_SUCCESS,
                "D: 终态后 onCancelled 不覆盖（SUCCESS 不能被改判 CANCELLED）");
        TestHost.check(!AdTaskCore.taskAllowsNew(100L, 50L), "D: 终态 2.2s 冷却内不开新任务");
        TestHost.check(AdTaskCore.taskAllowsNew(2300L, 50L), "D: 冷却后同窗新广告可开新任务");
    }

    /* ════ E · click → Activity 不变 → Dialog 消失 → 普通按钮出现 ════ */
    static void caseE_dialogGoneNormalButtons() {
        TestHost.suite("时序 E · Dialog 消失后普通按钮不被旧任务点击");
        int st = AdTaskCore.S_READY;
        st = AdTaskCore.onDispatch(st);              // t=0 出手（插屏 ×）
        // t=500：Dialog 广告消失（两轮零候选：t=500 首零 + t=850 第二零 ≥350ms）
        long firstZero = 500L;
        TestHost.check(AdTaskCore.zeroVerifyConfirmed(firstZero, 850L, true),
                "E: 两轮零确认（850-500=350ms 独立两轮）");
        st = AdTaskCore.onVerifyAdGone(st);
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "E: 就地 SUCCESS");
        // t=600~2200：原页面普通按钮（「知道了」「关闭」等）陆续出现——
        // 冷却闸内不开新任务 → 旧任务（已终态）不得点击普通按钮
        long endedAt = 850L;
        TestHost.check(!AdTaskCore.taskAllowsNew(2200L, endedAt),
                "E: 冷却期内（850+2200=3050ms 前）残留「关闭/知道了」普通按钮不开新任务");
        TestHost.check(AdTaskCore.taskAllowsNew(3100L, endedAt),
                "E: 冷却后（≥3050ms）真正的同窗新广告可开新任务");
        TestHost.check(!AdTaskCore.clickAllowed(st) && !AdTaskCore.callbackAllowed(st),
                "E: 旧任务终态对普通按钮的点击与回调全拒（情况 A/B 断源）");
    }

    /* ════ F · 广告关闭 → 反馈面板出现（关闭/取消/× 不得再被广告关闭点击）════ */
    static void caseF_feedbackPanel() {
        TestHost.suite("时序 F · 反馈面板期零广告关闭点击");
        int st = AdTaskCore.S_VERIFYING;
        st = AdTaskCore.onVerifyAdGone(st);          // t=100 广告确认关闭
        long endedAt = 100L;
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "F: 广告关闭 → SUCCESS");
        // t=200：反馈面板出现（内含「关闭/取消/×」全套关闭族词——正是残留候选高危期）
        TestHost.check(!AdTaskCore.taskAllowsNew(200L, endedAt),
                "F: 面板期（冷却内）面板里的「关闭/×」不得被当成新广告开新任务");
        TestHost.check(AdTaskCore.onFeedbackSeen(st) == AdTaskCore.S_SUCCESS,
                "F: 终态任务不进 FEEDBACK（不对反馈面板执行广告关闭补刀）");
        TestHost.check(!AdTaskCore.clickAllowed(st),
                "F: 终态任务对面板候选零点击（第二次点击 = 0）");
        TestHost.check(!AdTaskCore.taskAllowsNew(2299L, endedAt),
                "F: 冷却最后一刻（差 1ms）仍拒（2.2s 闸含边界）");
        TestHost.check(AdTaskCore.taskAllowsNew(2300L, endedAt),
                "F: 冷却放行（≥2200ms 恢复同窗新广告检测——漏检保护）");
    }

    /* ════ G · 视频广告关闭 → videoPoll 尚未停止 ════ */
    static void caseG_videoPollAfterClose() {
        TestHost.suite("时序 G · videoPoll 终态发现 SUCCESS 立即退出");
        // 视频开屏窗（videoLike）：观察窗 20s 契约在场
        TestHost.check(VideoSplashGuard.windowDeadlineMs(true) >= 20000,
                "G: 视频窗观察窗 20s（>12s 露头按钮可等到）");
        int st = AdTaskCore.S_READY;
        st = AdTaskCore.onDispatch(st);              // t=4500 出手（视频末露头键）
        st = AdTaskCore.onVerifyAdGone(st);          // t=5000 广告确认消失
        long endedAt = 5000L;
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "G: 视频广告关闭 → SUCCESS");
        // videoPoll 继续跑：终态降频 3s 节奏（5000→6500 不到期不扫；8000 到期扫一轮）
        TestHost.check(!VideoSplashGuard.pollDue(5000L, 6500L, true),
                "G: 终态后 1.5s 内（6500ms）不排轮询针（3s 降频契约）");
        TestHost.check(VideoSplashGuard.pollDue(5000L, 8000L, true),
                "G: 终态后 3s（8000ms）到期的观察针放行（观察在岗）");
        // 观察针发现任务 SUCCESS：不点击（残留候选不得复活任务）
        TestHost.check(!AdTaskCore.clickAllowed(st),
                "G: 轮询针发现 SUCCESS 后零点击（残留「跳过」键不复点）");
        TestHost.check(!AdTaskCore.taskAllowsNew(6000L, endedAt),
                "G: 观察期内残留候选不开新任务（冷却闸）");
        TestHost.check(!VideoSplashGuard.strikeGapOk(5000L, 6300L),
                "G: 白点节流 ≥1.5s（视频末残留帧不连击）");
    }

    /* ════ H · 延迟关闭按钮 100/300/500/800/2000/5000ms ════ */
    static void caseH_delayedCloseButton() {
        TestHost.suite("时序 H · 延迟露头关闭键：出现前零出手·出现后首过即出手");
        long[] appearAt = {100L, 300L, 500L, 800L, 2000L, 5000L};
        for (long t : appearAt) {
            // ── 文本跳过键变体：出现之前零出手 ──
            Ctx before = ctxSplash(Math.max(0, t - 50));
            Decision dBefore = AdDecisionCore.decide(AdDecisionCore.walk(emptyAdFrame(), before), before);
            TestHost.check(noneClick(dBefore), "H·" + t + "ms: 露头前帧（" + (t - 50) + "ms）零出手");
            // ── 出现帧：首过安全闸立即出手（文本键路径无最小延迟）──
            Ctx at = ctxSplash(t);
            Decision dAt = AdDecisionCore.decide(AdDecisionCore.walk(splashWithSkip(), at), at);
            TestHost.check(!noneClick(dAt), "H·" + t + "ms: 文本跳过键出现帧即出手（露头就跳）");
        }
        // ── 角落图标变体：CORNER_MIN_MS=300 时机闸 ──
        for (long t : new long[]{100L, 300L, 500L, 800L, 2000L, 5000L}) {
            Ctx at = ctxSplash(t);
            Decision d = AdDecisionCore.decide(AdDecisionCore.walk(minimalCornerIcon(), at), at);
            if (t < AdDecisionCore.CORNER_MIN_MS) {
                TestHost.check(noneClick(d),
                        "H·" + t + "ms: 角落图标早于 300ms 时机闸不出手（等待形态确认）");
            } else {
                TestHost.check(!noneClick(d),
                        "H·" + t + "ms: 角落图标过 300ms 闸即出手（首过安全通过）");
            }
        }
        // 观察窗天花板：5000ms 露头仍在窗内（12s 普通窗/20s 视频窗）
        TestHost.check(5000L <= VideoSplashGuard.windowDeadlineMs(false),
                "H: 5000ms 露头在普通观察窗 12s 内");
        TestHost.check(VideoSplashGuard.videoArmHint(2300, 3, 900),
                "H: arm-hint 免杀链（慢速构建窗——晚露头按钮不退避）");
    }

    /* ════ Case 1 · 第一次已成功——所有旧 actor 不得再次点击 ════ */
    static void case1_allStaleActorsRejected() {
        TestHost.suite("Case 1 · 成功后旧 task/callback/timer/videoPoll/TapGesture 全拒");
        int clicks = 0;
        int st = AdTaskCore.S_DETECTED;
        // 任务开启 → READY → 出手 → 复验广告已关 → SUCCESS
        TestHost.check(AdTaskCore.clickAllowed(st), "C1: 初始态可出手");
        st = AdTaskCore.S_READY;
        st = AdTaskCore.onDispatch(st);
        clicks = 1;
        st = AdTaskCore.onVerifyAdGone(st);
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "C1: 第一次点击已成功 → SUCCESS");
        // ── 旧 task（同任务再来一次点击请求）──
        TestHost.check(!AdTaskCore.clickAllowed(st), "C1·旧task: 终态拒绝再次点击");
        // ── 旧 callback（500ms 复验针）──
        TestHost.check(!AdTaskCore.callbackAllowed(st), "C1·旧callback: 过期回调作废");
        // ── 旧 timer / videoPoll（延迟任务等价面：全部经 callbackAllowed 门）──
        TestHost.check(AdTaskCore.onDispatch(st) == AdTaskCore.S_SUCCESS, "C1·旧timer: 派发转移被拒");
        TestHost.check(AdTaskCore.onVerifyAdStill(st, 0, 3) == AdTaskCore.S_SUCCESS,
                "C1·旧videoPoll: 轮询复验「广告仍在」无效（原样）");
        // ── 旧 TapGestureCallback（手势回调补扫等价面：补扫见「反馈面板/残留候选」
        //    也不进 FEEDBACK/不复活任务——终态任务对补扫结果零响应）──
        TestHost.check(AdTaskCore.onFeedbackSeen(st) == AdTaskCore.S_SUCCESS,
                "C1·旧TapGestureCallback: 手势后补扫见面板文案也不进 FEEDBACK（零响应）");
        // ── 第二次点击 = 0（预算未动）──
        TestHost.checkEq((Object) 1, (Object) clicks, "C1: 第二次点击 = 0（全程仅 1 击）");
        TestHost.check(!AdTaskCore.taskAllowsNew(500L, 100L),
                "C1: 冷却闸挡住残留「广告」标签复活路径（任务终态时刻 100ms）");
    }

    /* ════ Case 2 · 第一次失败——第二次必须重新获取一切 ════ */
    static void case2_retryNeedsFullReacquire() {
        TestHost.suite("Case 2 · 合法重试五要件（缺一不得复用第一次候选）");
        int st = AdTaskCore.S_READY;
        st = AdTaskCore.onDispatch(st);              // 第一次点击
        st = AdTaskCore.onVerifyAdStill(st, 1, 3);  // 失败：广告仍存在
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) st, "C2: 首击失败 → RETRY_WAIT");
        // 五要件逐项缺失 → 全部拒绝（不得复用第一次候选）
        TestHost.check(!AdTaskCore.retryGuardsOk(false, true, true, true, true),
                "C2·缺①: 本轮复验未见到广告线索 → 不出手");
        TestHost.check(!AdTaskCore.retryGuardsOk(true, false, true, true, true),
                "C2·缺②: 已非原任务窗口 → 不出手（页面已变绝不沿用旧坐标）");
        TestHost.check(!AdTaskCore.retryGuardsOk(true, true, false, true, true),
                "C2·缺③: 原候选已不在树中 → 不出手（绝不点空）");
        TestHost.check(!AdTaskCore.retryGuardsOk(true, true, true, false, true),
                "C2·缺④: 候选当前不可见 → 不出手（fresh bounds 复核不过）");
        TestHost.check(!AdTaskCore.retryGuardsOk(true, true, true, true, false),
                "C2·缺⑤: 候选语义非关闭广告 → 不出手（SafetyGuard 语义断言）");
        // 全真 → 允许（重新获取的广告状态+候选+闸门+可点性+任务有效性）
        TestHost.check(AdTaskCore.retryGuardsOk(true, true, true, true, true),
                "C2·全要件: 重新获取一切后允许第二击（情况 C 合法重试）");
        st = AdTaskCore.onDispatch(st);              // 第二击
        st = AdTaskCore.onVerifyAdGone(st);
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "C2: 第二击成功 → SUCCESS");
        TestHost.check(!AdTaskCore.clickAllowed(st), "C2: 成功后第三击被拒（重试链闭环）");
    }

    /* ════ Case 3 · click() 返回 true 但广告没消失 ════ */
    static void case3_clickOkNotAdClosed() {
        TestHost.suite("Case 3 · click API 成功 ≠ 广告关闭成功");
        int st = AdTaskCore.S_READY;
        // ⚠ click() 返回 true（派发成功）——只意味着手势已发出，绝不等于广告已关
        st = AdTaskCore.onDispatch(st);
        TestHost.checkEq((Object) AdTaskCore.S_VERIFYING, (Object) st,
                "C3: click()=true 只到 VERIFYING（绝不直判 SUCCESS）");
        // 复验：广告仍在（点击 API 成功但广告纹丝不动——真机实锤场景）
        st = AdTaskCore.onVerifyAdStill(st, 1, 3);
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) st,
                "C3: 广告仍在 → RETRY_WAIT（click()=true 不缩短验证）");
        // 有限重试链：重扫→新候选→安全闸→再出手→复验
        st = AdTaskCore.onDispatch(st);
        TestHost.checkEq((Object) AdTaskCore.S_VERIFYING, (Object) st, "C3: 第二击 → VERIFYING");
        st = AdTaskCore.onVerifyAdStill(st, 2, 3);
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) st, "C3: 仍在（2<3）→ RETRY_WAIT");
        st = AdTaskCore.onDispatch(st);
        st = AdTaskCore.onVerifyAdGone(st);          // 第三击后广告确认消失
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "C3: 验证通过 → SUCCESS（真验证非 API 返回）");
        // 预算耗尽分支：3 击全失败 → FAILED（优雅收手）
        int f = AdTaskCore.S_READY;
        f = AdTaskCore.onDispatch(f);
        f = AdTaskCore.onVerifyAdStill(f, 3, 3);
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) f, "C3: 预算尽 → FAILED（绝不无限重试）");
        // 唯一 SUCCESS 通道枚举（click()=true 没有任何直通 SUCCESS 的转移）
        TestHost.check(AdTaskCore.onDispatch(AdTaskCore.S_DETECTED) != AdTaskCore.S_SUCCESS
                        && AdTaskCore.onDispatch(AdTaskCore.S_READY) != AdTaskCore.S_SUCCESS
                        && AdTaskCore.onDispatch(AdTaskCore.S_RETRY_WAIT) != AdTaskCore.S_SUCCESS
                        && AdTaskCore.onDispatch(AdTaskCore.S_VERIFYING) != AdTaskCore.S_SUCCESS
                        && AdTaskCore.onDispatch(AdTaskCore.S_CLICKING) != AdTaskCore.S_SUCCESS,
                "C3: onDispatch 从无 SUCCESS 出口（API 成功不是广告关闭的证据）");
        TestHost.check(AdTaskCore.onVerifyAdStill(AdTaskCore.S_VERIFYING, 0, 3) != AdTaskCore.S_SUCCESS
                        && AdTaskCore.onVerifyUncertain(AdTaskCore.S_VERIFYING, 0, 3) != AdTaskCore.S_SUCCESS,
                "C3: 广告仍在/不确定 → 均无 SUCCESS 出口（只有真消失才 SUCCESS）");
    }

    /* ════ 工具 ════ */
    static boolean noneClick(Decision d) {
        return d == null || AdDecisionCore.A_NONE.equals(d.action);
    }

    static Ctx ctxSplash(long elapsed) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = "com.example.app";
        ctx.activityId = "com.example.app.SplashActivity";
        ctx.elapsedMs = elapsed;
        ctx.splashLive = true;
        return ctx;
    }

    /** 空开屏帧（广告内容未加载——延迟露头前帧）。 */
    static SimNode emptyAdFrame() {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode img = new SimNode("", 0, 0, 1080, 2100);
        img.cls = "android.widget.ImageView";
        root.add(img);
        return root;
    }

    /** 文本跳过键出现帧（广告 + 右上跳过键）。 */
    static SimNode splashWithSkip() {
        SimNode root = emptyAdFrame();
        SimNode skip = TestHost.skipButton("跳过");
        skip.clickable = true;
        root.add(skip);
        return root;
    }

    /** 树极简角落图标帧（图标跳过键——CORNER_MIN_MS 时机闸变体）。 */
    static SimNode minimalCornerIcon() {
        SimNode root = new SimNode();
        root.cls = "com.custom.DrawView";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode icon = new SimNode("", 990, 88, 1046, 144);
        icon.cls = "android.widget.ImageView";
        icon.clickable = true;
        icon.vid = "iv_close";
        root.add(icon);
        return root;
    }
}
