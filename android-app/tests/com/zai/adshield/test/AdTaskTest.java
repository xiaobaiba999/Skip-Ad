package com.zai.adshield.test;

import com.zai.adshield.core.AdTaskCore;
import com.zai.adshield.core.VideoSplashGuard;

/**
 * 广告任务状态机单元测试（v7.9.1 · 用户实锤三类真机事故：A 第一次成功
 * 第二次误触普通按钮 / B 第一次成功第二次重复关闭 / C 第一次失败第二次
 * 才成功）。
 * ─────────────────────────────────────────────────────────────
 * 与 InterstitialGuard/HumanTap 同规约：纯 Java 零 Android 依赖，ECJ
 * -1.8 编译 JVM 运行；断言 AdTaskCore 状态机契约的真值表：
 *  · 终态不可逆（SUCCESS/FAILED/CANCELLED 拒收一切点击与回调）；
 *  · 反馈面板独立流程（FEEDBACK 期绝不对原广告再执行关闭）；
 *  · 合法重试保护（RETRY_WAIT 放行、预算钳制 FAILED）；
 *  · 重试前置检查表五要件（任一不满足不得对旧坐标出手）；
 *  · 终态冷却闸（2.2s 残留期新广告误判防护 + 视频新键解禁证据）；
 *  · 就地 SUCCESS 两轮零候选确认（350ms 间隔独立两轮才判关）。
 * 断言规划（check/checkEq 76 项）：
 *  S1 终态与点击门（14）/ S2 状态转移真值表（21）
 *  S3 反馈面板独立流程（7）/ S4 合法重试与预算（9）
 *  S5 重试前置检查表（8）/ S6 冷却闸与新键解禁（9）
 *  S7 两轮零候选就地确认（6）/ S8 轮询终态降频契约（2 汇总断言组）。
 */
public final class AdTaskTest {

    public static void main(String[] args) {

        /* ════ S1 终态与点击/回调门（14 项）════ */
        TestHost.suite("AdTaskCore · S1 终态不可逆与点击/回调门");
        TestHost.check(AdTaskCore.isTerminal(AdTaskCore.S_SUCCESS), "SUCCESS 是终态");
        TestHost.check(AdTaskCore.isTerminal(AdTaskCore.S_FAILED), "FAILED 是终态");
        TestHost.check(AdTaskCore.isTerminal(AdTaskCore.S_CANCELLED), "CANCELLED 是终态");
        TestHost.check(!AdTaskCore.isTerminal(AdTaskCore.S_READY), "READY 非终态");
        TestHost.check(!AdTaskCore.isTerminal(AdTaskCore.S_VERIFYING), "VERIFYING 非终态");
        TestHost.check(!AdTaskCore.isTerminal(AdTaskCore.S_RETRY_WAIT), "RETRY_WAIT 非终态");
        TestHost.check(!AdTaskCore.isTerminal(AdTaskCore.S_FEEDBACK), "FEEDBACK 非终态");
        // 点击提交门：终态 + FEEDBACK + CLICKING 一律拒；合法路径放行
        TestHost.check(!AdTaskCore.clickAllowed(AdTaskCore.S_SUCCESS), "A1 成功后第二次点击请求被拒（情况 A/B 断源）");
        TestHost.check(!AdTaskCore.clickAllowed(AdTaskCore.S_FAILED), "预算用尽后点击请求被拒");
        TestHost.check(!AdTaskCore.clickAllowed(AdTaskCore.S_CANCELLED), "任务作废后点击请求被拒");
        TestHost.check(!AdTaskCore.clickAllowed(AdTaskCore.S_FEEDBACK), "反馈面板处理中不对原广告再执行关闭（验收 5）");
        TestHost.check(!AdTaskCore.clickAllowed(AdTaskCore.S_CLICKING), "CLICKING 态无并发提交（同一任务一个有效执行者）");
        TestHost.check(AdTaskCore.clickAllowed(AdTaskCore.S_READY), "READY 可提交");
        TestHost.check(AdTaskCore.clickAllowed(AdTaskCore.S_RETRY_WAIT), "RETRY_WAIT 可提交（情况 C 合法重试）");
        TestHost.check(AdTaskCore.clickAllowed(AdTaskCore.S_VERIFYING), "VERIFYING 复验再候选可提交（换候选重试路）");

        /* ════ S2 状态转移真值表（21 项）════ */
        TestHost.suite("AdTaskCore · S2 状态转移真值表");
        // onDispatch：合法态 → VERIFYING；非法态原样
        TestHost.checkEq((Object) AdTaskCore.S_VERIFYING, (Object) AdTaskCore.onDispatch(AdTaskCore.S_READY), "READY 派发 → VERIFYING");
        TestHost.checkEq((Object) AdTaskCore.S_VERIFYING, (Object) AdTaskCore.onDispatch(AdTaskCore.S_RETRY_WAIT), "RETRY_WAIT 派发 → VERIFYING");
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onDispatch(AdTaskCore.S_SUCCESS), "SUCCESS 派发被拒（不可逆）");
        TestHost.checkEq((Object) AdTaskCore.S_FEEDBACK, (Object) AdTaskCore.onDispatch(AdTaskCore.S_FEEDBACK), "FEEDBACK 派发被拒");
        // onVerifyAdGone：非终态 → SUCCESS（不可逆）
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onVerifyAdGone(AdTaskCore.S_VERIFYING), "复验广告已关 → SUCCESS");
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onVerifyAdGone(AdTaskCore.S_FEEDBACK), "反馈面板收起 → SUCCESS");
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onVerifyAdGone(AdTaskCore.S_SUCCESS), "SUCCESS 再验证仍 SUCCESS（幂等不可逆）");
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) AdTaskCore.onVerifyAdGone(AdTaskCore.S_FAILED), "FAILED 再验证仍 FAILED（不可逆）");
        // onVerifyAdStill：预算内 → RETRY_WAIT；预算尽 → FAILED；FEEDBACK 保持
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) AdTaskCore.onVerifyAdStill(AdTaskCore.S_VERIFYING, 1, 3), "复验广告仍在（1<3 击）→ RETRY_WAIT（合法重试）");
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) AdTaskCore.onVerifyAdStill(AdTaskCore.S_VERIFYING, 3, 3), "预算用尽（3≥3）→ FAILED（优雅收手）");
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) AdTaskCore.onVerifyAdStill(AdTaskCore.S_RETRY_WAIT, 5, 5), "RETRY_WAIT 预算尽 → FAILED");
        TestHost.checkEq((Object) AdTaskCore.S_FEEDBACK, (Object) AdTaskCore.onVerifyAdStill(AdTaskCore.S_FEEDBACK, 0, 3), "FEEDBACK 期广告仍在不转 RETRY（先收面板）");
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onVerifyAdStill(AdTaskCore.S_SUCCESS, 0, 3), "SUCCESS 不因复验回退（不可逆）");
        // onVerifyUncertain：不直接对旧坐标补刀——预算内回 RETRY_WAIT 观察
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) AdTaskCore.onVerifyUncertain(AdTaskCore.S_VERIFYING, 1, 3), "页面变化无法确认 → 观察不补刀（RETRY_WAIT）");
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) AdTaskCore.onVerifyUncertain(AdTaskCore.S_VERIFYING, 3, 3), "不确定+预算尽 → FAILED");
        // onFeedbackSeen / onFeedbackDone
        TestHost.checkEq((Object) AdTaskCore.S_FEEDBACK, (Object) AdTaskCore.onFeedbackSeen(AdTaskCore.S_VERIFYING), "复验见反馈面板 → FEEDBACK");
        TestHost.checkEq((Object) AdTaskCore.S_FEEDBACK, (Object) AdTaskCore.onFeedbackSeen(AdTaskCore.S_RETRY_WAIT), "重试等待见面板 → FEEDBACK");
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onFeedbackDone(AdTaskCore.S_FEEDBACK), "面板收起 → SUCCESS");
        TestHost.checkEq((Object) AdTaskCore.S_VERIFYING, (Object) AdTaskCore.onFeedbackDone(AdTaskCore.S_VERIFYING), "非 FEEDBACK 态收面板调用无效（原样）");
        // onWindowChanged / onCancelled
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onWindowChanged(AdTaskCore.S_VERIFYING, true), "窗口切换且有过出手 → SUCCESS（复验通过）");
        TestHost.checkEq((Object) AdTaskCore.S_CANCELLED, (Object) AdTaskCore.onWindowChanged(AdTaskCore.S_DETECTED, false), "窗口切换零出手 → CANCELLED");
        TestHost.checkEq((Object) AdTaskCore.S_CANCELLED, (Object) AdTaskCore.onCancelled(AdTaskCore.S_VERIFYING), "用户接管 → CANCELLED");
        TestHost.checkEq((Object) AdTaskCore.S_CANCELLED, (Object) AdTaskCore.onCancelled(AdTaskCore.S_CANCELLED), "CANCELLED 幂等");

        /* ════ S3 反馈面板独立流程（7 项）════ */
        TestHost.suite("AdTaskCore · S3 反馈面板独立流程");
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onFeedbackSeen(AdTaskCore.S_SUCCESS), "终态不进 FEEDBACK（成功后面板=已是终态，代点取消由面板流程处理）");
        TestHost.check(!AdTaskCore.clickAllowed(AdTaskCore.onFeedbackSeen(AdTaskCore.S_VERIFYING)), "面板在场期原广告关闭动作被拒");
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) AdTaskCore.onFeedbackDone(AdTaskCore.onFeedbackSeen(AdTaskCore.S_VERIFYING)), "面板收起链路 → SUCCESS（面板只在广告关闭后弹出）");
        TestHost.checkEq((Object) AdTaskCore.S_FEEDBACK, (Object) AdTaskCore.onVerifyAdStill(
                AdTaskCore.onFeedbackSeen(AdTaskCore.S_VERIFYING), 0, 3), "面板在场时「广告仍在场」不触发广告重试（保持 FEEDBACK）");
        TestHost.check(AdTaskCore.callbackAllowed(AdTaskCore.S_FEEDBACK), "FEEDBACK 态回调放行（收面板针有效）");
        TestHost.check(!AdTaskCore.callbackAllowed(AdTaskCore.S_SUCCESS), "SUCCESS 态过期回调一律作废（延迟任务取消）");
        TestHost.check(AdTaskCore.isSuccess(AdTaskCore.S_SUCCESS) && !AdTaskCore.isSuccess(AdTaskCore.S_RETRY_WAIT), "isSuccess 口径");

        /* ════ S4 合法重试与预算（9 项）════ */
        TestHost.suite("AdTaskCore · S4 合法重试保护与预算钳制");
        int clicks = 0;
        int st = AdTaskCore.S_READY;
        // 模拟：第 1 击失败 → 广告仍在 → RETRY_WAIT → 第 2 击 → 验证已关（情况 C 完整链）
        st = AdTaskCore.onDispatch(st);                    // 第 1 击派发 → VERIFYING
        clicks = 1;
        st = AdTaskCore.onVerifyAdStill(st, clicks, 3);    // 复验仍在 → RETRY_WAIT
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) st, "C 链路：第 1 击未关 → RETRY_WAIT");
        TestHost.check(AdTaskCore.clickAllowed(st), "C 链路：RETRY_WAIT 允许第 2 击");
        st = AdTaskCore.onDispatch(st);                    // 第 2 击派发 → VERIFYING
        clicks = 2;
        st = AdTaskCore.onVerifyAdGone(st);                // 第 2 击后广告关 → SUCCESS
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st, "C 链路：第 2 击关闭 → SUCCESS");
        TestHost.check(!AdTaskCore.clickAllowed(st), "C 链路：SUCCESS 后第 3 击请求被拒（防情况 A/B）");
        // 预算钳制：视频窗预算 5、普通窗 3（VideoSplashGuard.maxClicksFor 同源）
        TestHost.checkEq((Object) 3, (Object) com.zai.adshield.core.VideoSplashGuard.maxClicksFor(false), "普通窗预算 3");
        TestHost.checkEq((Object) 5, (Object) com.zai.adshield.core.VideoSplashGuard.maxClicksFor(true), "视频画像窗预算 5");
        // 预算尽链路：RETRY_WAIT 预算用尽 → FAILED → 拒收
        int fb = AdTaskCore.onVerifyAdStill(AdTaskCore.S_RETRY_WAIT, 3, 3);
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) fb, "预算用尽 → FAILED（绝不无限重试）");
        TestHost.check(!AdTaskCore.clickAllowed(fb), "FAILED 后点击拒收（新键解禁前不得复活）");

        /* ════ S5 重试前置检查表（8 项）════ */
        TestHost.suite("AdTaskCore · S5 重试前置检查表（五要件）");
        TestHost.check(AdTaskCore.retryGuardsOk(true, true, true, true, true), "五要件全过 → 允许重试");
        TestHost.check(!AdTaskCore.retryGuardsOk(false, true, true, true, true), "广告已不在场 → 不得出手（判关路径）");
        TestHost.check(!AdTaskCore.retryGuardsOk(true, false, true, true, true), "页面已变（窗口代换）→ 不得沿用旧坐标（验收 3）");
        TestHost.check(!AdTaskCore.retryGuardsOk(true, true, false, true, true), "原候选已消失 → 不得点旧候选（验收 3）");
        TestHost.check(!AdTaskCore.retryGuardsOk(true, true, true, false, true), "候选不可见 → 不得盲刺");
        TestHost.check(!AdTaskCore.retryGuardsOk(true, true, true, true, false), "候选语义非关闭方向 → 不得出手（安全红线）");
        TestHost.check(!AdTaskCore.retryGuardsOk(false, false, false, false, false), "全不满足 → 拒");
        TestHost.check(AdTaskCore.retryGuardsOk(true, true, true, true, true)
                != AdTaskCore.retryGuardsOk(true, false, true, true, true), "单因子翻转改变结论（无恒真捷径）");

        /* ════ S6 冷却闸与新键解禁（9 项）════ */
        TestHost.suite("AdTaskCore · S6 终态冷却闸与视频新键解禁");
        long ended = 1000000L;
        TestHost.check(!AdTaskCore.taskAllowsNew(ended + 500, ended), "终态后 0.5s：冷却未过——残留标签不当新广告（A/B 断源）");
        TestHost.check(!AdTaskCore.taskAllowsNew(ended + 2199, ended), "终态后 2.199s：冷却临界未过");
        TestHost.check(AdTaskCore.taskAllowsNew(ended + 2200, ended), "终态后 2.2s：冷却过——同窗真·新广告可开新任务");
        TestHost.check(AdTaskCore.taskAllowsNew(ended + 100000, ended), "冷却远过 → 放行");
        TestHost.check(AdTaskCore.taskAllowsNew(ended + 500, 0), "无终态记录（未关过）恒放行（首任务/合法重试不受影响）");
        TestHost.checkEq((Object) 2200L, (Object) AdTaskCore.TASK_NEW_COOLDOWN_MS, "冷却常量 2.2s（与 POST_SKIP_QUIET_MS 同源）");
        // 视频新键预算刷新契约（v7.8.0 能力零回退）
        TestHost.check(AdTaskCore.budgetRefreshForNewTarget(true, true, true), "视频窗+新指纹+文本键 → 预算刷新（新任务）");
        TestHost.check(!AdTaskCore.budgetRefreshForNewTarget(false, true, true), "非视频窗不刷新");
        TestHost.check(!AdTaskCore.budgetRefreshForNewTarget(true, false, true)
                && !AdTaskCore.budgetRefreshForNewTarget(true, true, false), "旧指纹目标/无文本候选绝不刷新（误触红线）");

        /* ════ S7 两轮零候选就地确认（6 项）════ */
        TestHost.suite("AdTaskCore · S7 就地 SUCCESS 两轮零候选确认");
        TestHost.check(!AdTaskCore.zeroVerifyConfirmed(null, 1000L, true), "无首轮记录 → 不判关（先观察）");
        TestHost.check(!AdTaskCore.zeroVerifyConfirmed(1000L, 1100L, true), "轮间隔 100ms <350ms → 树刷新抖动不判关");
        TestHost.check(!AdTaskCore.zeroVerifyConfirmed(1000L, 1349L, true), "轮间隔 349ms → 临界未过不判关");
        TestHost.check(AdTaskCore.zeroVerifyConfirmed(1000L, 1350L, true), "轮间隔 ≥350ms 且仍零 → 就地 SUCCESS（Activity 不变的成功信号）");
        TestHost.check(AdTaskCore.zeroVerifyConfirmed(1000L, 5000L, true), "远距两轮零 → SUCCESS");
        TestHost.check(!AdTaskCore.zeroVerifyConfirmed(1000L, 5000L, false), "第二轮候选再现 → 不判关（合法重试保护）");

        /* ════ S8 轮询终态降频契约（2 汇总断言组）════ */
        TestHost.suite("AdTaskCore · S8 视频轮询终态降频契约");
        Long last = 1000000L;
        TestHost.check(!com.zai.adshield.core.VideoSplashGuard.pollDue(last, 1000000L + 1499L, false), "活任务 1.5s 节奏照常（未到期）");
        TestHost.check(com.zai.adshield.core.VideoSplashGuard.pollDue(last, 1000000L + 1500L, false), "活任务 1.5s 到期");
        TestHost.check(!com.zai.adshield.core.VideoSplashGuard.pollDue(last, 1000000L + 1500L, true), "终态任务 1.5s 不到期（降频观察不出手）");
        TestHost.check(com.zai.adshield.core.VideoSplashGuard.pollDue(last, 1000000L + 3000L, true), "终态任务 3s 到期（观察在岗）");
        TestHost.check(com.zai.adshield.core.VideoSplashGuard.pollDue(last, 1000000L + 2000L, false), "2s 处活任务已到期（新实例冷却后恢复 1.5s 节奏）");
        TestHost.check(!com.zai.adshield.core.VideoSplashGuard.pollDue(last, 1000000L + 2000L, true), "2s 处终态任务未到期（终态降频在岗）");
        TestHost.checkEq((Object) 3000L, (Object) com.zai.adshield.core.VideoSplashGuard.VIDEO_TERMINAL_POLL_MS, "终态降频常量 3s");

        TestHost.done();
    }
}
