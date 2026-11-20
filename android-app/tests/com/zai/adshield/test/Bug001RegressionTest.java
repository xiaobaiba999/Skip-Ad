package com.zai.adshield.test;

import com.zai.adshield.core.AdTaskCore;
import com.zai.adshield.core.AdTaskCore.VerifyStillStep;
import com.zai.adshield.core.DecisionEvidence;

/**
 * Bug#001 回归测试（v7.9.6 · 一个真实 Bug 一个回归测试）。
 *
 * 缺陷（v7.9.6 代码审读实锤——AdSkipService.taskOnScanOutcome 非零候选
 * 路径，v7.9.5 引入六态闭环时新增接线）：
 *  「预算耗尽于复验时刻」的终局直接置 S_FAILED，三漏：
 *   · ① 不落 endedAt → taskAllowsNew(now, 0) 因「无终态记录恒放行」
 *     直接放行 → 2.2s 冷却闸被绕过 → 立即开新任务且
 *     resetTaskForNewInstance 清空 clickTries/gaveUp → 同一存活广告
 *     借「新任务重置预算」无限重试循环（「绝不无限重试/预算梯子」红线
 *     失守——用户指令九 DUPLICATE_CLICK 同族风险面）；
 *   · ② 不记 MISS → 六态计数把任务终局 MISS 整类漏记（最后一击复验
 *     失败被膨胀记成 RETRY——真实漏检率证据链失真，用户指令四/七
 *     「预算用尽 → MISS」口径被破坏）；
 *   · ③ 终局无日志。
 *
 * 修复：终局裁决收进纯函数 AdTaskCore.onVerifyStillStep（服务侧薄接线
 * 只应用结果）——本套件回归其全部契约面：
 *  S1 终局三件套：FAILED + endedAt=now + outcome=MISS（不再计 RETRY）；
 *  S2 全生命周期（真实派发序列 READY→dispatch→verify-still×N）：
 *     预算内 RETRY 记账 / 终局 MISS 记账 / FAILED 不可逆；
 *  S3 冷却闸闭环（Bug#001 核心）：终局后 2.2s 内新任务全拒——
 *     「新任务重置预算」的无限重试循环被结构性阻断；
 *  S4 门语义取证：taskAllowsNew(now, 0)=true——endedAt 必须落账的
 *     结构性原因（正是 Bug#001 的绕过机制，钉死防回退）；
 *  S5 不重复记账：终态复验在场 → outcome=-1（原样返回绝不复活）；
 *  S6 FEEDBACK 语义零变化：面板在场复验仍场 → 原态保持 · 不计 RETRY/MISS；
 *  S7 RETRY 口径：仅 VERIFYING 首击复验计一次（RETRY_WAIT 复验在场
 *     不计——防同一击多扫膨胀）；
 *  S8 六态计数端到端：3 击梯子 → RETRY=2 · MISS=1 · 其余四态 0
 *     （VERIFIED_SUCCESS/FALSE_CLICK/SAFE_BLOCK/UNKNOWN 不被污染）。
 *
 * 纯 Java 零 Android 依赖（ECJ -1.8 同规约）。
 */
public final class Bug001RegressionTest {

    public static void main(String[] args) {
        s1_budgetExhaustedTerminalTriple();
        s2_fullTaskLifecycle();
        s3_cooldownGateClosed();
        s4_gateSemanticsEvidence();
        s5_noDoubleAccountingOnTerminal();
        s6_feedbackSemanticsUnchanged();
        s7_retryCountedOncePerClick();
        s8_sixStateCountersEndToEnd();
        TestHost.done();
    }

    /* ════ S1 · 预算耗尽终局三件套 ════ */
    static void s1_budgetExhaustedTerminalTriple() {
        TestHost.suite("Bug001 S1 · 预算耗尽复验终局三件套（FAILED+endedAt+MISS）");
        long now = 10_000L;
        // 第 3 击（=max）复验仍见候选 → 终局（旧代码三漏的现场）
        VerifyStillStep st = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_VERIFYING, 3, 3, now, 0L);
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) st.state,
                "S1: 预算耗尽复验 → FAILED 终局");
        TestHost.checkEq((Object) Long.valueOf(now), (Object) Long.valueOf(st.endedAt),
                "S1: endedAt 落 now（冷却闸数据源——旧代码漏落 0）");
        TestHost.checkEq((Object) Integer.valueOf(DecisionEvidence.OUT_MISS),
                (Object) Integer.valueOf(st.outcome),
                "S1: 终局记账 = MISS（任务级终局——非 RETRY）");
        // 预算内对照：同态同刻预算未尽 → RETRY_WAIT + RETRY（既有行为零变化）
        VerifyStillStep st2 = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_VERIFYING, 1, 3, now, 0L);
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) st2.state,
                "S1: 预算内复验在场 → RETRY_WAIT（合法重试等待）");
        TestHost.checkEq((Object) Long.valueOf(0L), (Object) Long.valueOf(st2.endedAt),
                "S1: 预算内不落 endedAt（未终局不占冷却闸）");
        TestHost.checkEq((Object) Integer.valueOf(DecisionEvidence.OUT_RETRY),
                (Object) Integer.valueOf(st2.outcome),
                "S1: 预算内记账 = RETRY");
    }

    /* ════ S2 · 全生命周期（真实派发序列）════ */
    static void s2_fullTaskLifecycle() {
        TestHost.suite("Bug001 S2 · 任务全生命周期（派发→复验在场→终局不可逆）");
        final int max = 3;
        int st = AdTaskCore.S_READY;
        int clicks = 0;
        long endedAt = 0L;
        // 三击梯子：每击派发 → VERIFYING → 复验在场步进
        for (int i = 1; i <= max; i++) {
            st = AdTaskCore.onDispatch(st);          // 派发（clickAllowed 全程放行）
            clicks = i;
            VerifyStillStep step = AdTaskCore.onVerifyStillStep(st, clicks, max,
                    1_000L * i, endedAt);
            if (i < max) {
                TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) step.state,
                        "S2: 第 " + i + " 击复验在场 → RETRY_WAIT");
                TestHost.checkEq((Object) Integer.valueOf(DecisionEvidence.OUT_RETRY),
                        (Object) Integer.valueOf(step.outcome),
                        "S2: 第 " + i + " 击记账 RETRY（预算内）");
                st = step.state;
                endedAt = step.endedAt;
                TestHost.check(AdTaskCore.clickAllowed(st),
                        "S2: RETRY_WAIT 重试放行（情况 C 合法重试）");
            } else {
                TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) step.state,
                        "S2: 末击（第 " + i + " 击 = 预算）复验在场 → FAILED 终局");
                TestHost.checkEq((Object) Integer.valueOf(DecisionEvidence.OUT_MISS),
                        (Object) Integer.valueOf(step.outcome),
                        "S2: 末击记账 MISS（不再计 RETRY——旧代码此处漏记）");
                st = step.state;
                endedAt = step.endedAt;
                TestHost.checkEq((Object) Long.valueOf(3_000L), (Object) Long.valueOf(endedAt),
                        "S2: 终局时刻 = 末击复验时刻");
            }
        }
        // FAILED 不可逆：点击/派发/回调全拒（旧任务绝不复活）
        TestHost.check(!AdTaskCore.clickAllowed(st), "S2: FAILED 后点击全拒");
        TestHost.check(!AdTaskCore.callbackAllowed(st), "S2: FAILED 后回调全拒");
        TestHost.checkEq((Object) AdTaskCore.S_FAILED,
                (Object) Integer.valueOf(AdTaskCore.onDispatch(st)),
                "S2: FAILED 后派发原样返回（第二次点击 = 0）");
        TestHost.checkEq((Object) Integer.valueOf(3), (Object) Integer.valueOf(clicks),
                "S2: 全程点击数 = 3（预算梯子在岗——无限重试被钳制）");
    }

    /* ════ S3 · 冷却闸闭环（Bug#001 核心——无限重试循环阻断）════ */
    static void s3_cooldownGateClosed() {
        TestHost.suite("Bug001 S3 · 终局冷却闸闭环（新任务重置预算的循环被阻断）");
        // 末击复验在场 → 终局（修复路径）
        VerifyStillStep st = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_VERIFYING, 3, 3, 10_000L, 0L);
        TestHost.checkEq((Object) Long.valueOf(10_000L), (Object) Long.valueOf(st.endedAt),
                "S3: 终局 endedAt=10000（旧代码 0 → 闸被绕过）");
        // 2.2s 冷却内：一切「新任务」请求全拒（含残留候选/复扫/探针再发现）
        long[] probes = {10_000L, 10_500L, 11_000L, 11_500L, 12_000L};
        for (int i = 0; i < probes.length; i++) {
            TestHost.check(!AdTaskCore.taskAllowsNew(probes[i], st.endedAt),
                    "S3: t=" + probes[i] + " 新任务被冷却闸拒收（<2.2s）");
        }
        // 闸过 → 新任务放行（新广告实例冷却后另议——设计语义保持）
        TestHost.check(AdTaskCore.taskAllowsNew(12_200L, st.endedAt),
                "S3: t=12200（≥2.2s）新任务放行（冷却后合法新实例）");
        // 循环阻断对照：若终局漏落 endedAt（Bug#001 旧态），闸立即放行
        // → resetTaskForNewInstance 清预算 → 同一广告再打一轮 —— 本套件
        // 钉死「FAILED 必带非零 endedAt」的结构性不变式。
        TestHost.check(st.endedAt != 0L,
                "S3: FAILED 终局 endedAt ≠ 0（结构性不变式——防回退）");
    }

    /* ════ S4 · 门语义取证（endedAt 必须落账的结构性原因）════ */
    static void s4_gateSemanticsEvidence() {
        TestHost.suite("Bug001 S4 · taskAllowsNew(now,0)=true——漏落 endedAt 即绕闸");
        // 这正是 Bug#001 的绕过机制：endedAt 缺省（<=0）时门恒放行。
        TestHost.check(AdTaskCore.taskAllowsNew(10_000L, 0L),
                "S4: endedAt=0 时闸恒放行（门的既有语义——因此终局必须落账）");
        TestHost.check(AdTaskCore.taskAllowsNew(10_000L, -1L),
                "S4: endedAt<0 同恒放行（无终态记录=未关过——首任务不受影响）");
        // 对照：有落账的终局 → 闸按 2.2s 关/开（与 S3 同源钉死）
        TestHost.check(!AdTaskCore.taskAllowsNew(10_000L, 9_000L),
                "S4: endedAt=9000 → t=10000 闸关（1s < 2.2s）");
        TestHost.check(AdTaskCore.taskAllowsNew(11_300L, 9_000L),
                "S4: endedAt=9000 → t=11300 闸开（2.3s ≥ 2.2s）");
    }

    /* ════ S5 · 终态不重复记账（不可逆——绝不复活绝不重复）════ */
    static void s5_noDoubleAccountingOnTerminal() {
        TestHost.suite("Bug001 S5 · 终态复验在场不重复记账（不可逆）");
        VerifyStillStep st = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_FAILED, 3, 3, 12_000L, 10_000L);
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) st.state,
                "S5: FAILED 原样返回（绝不复活）");
        TestHost.checkEq((Object) Integer.valueOf(-1), (Object) Integer.valueOf(st.outcome),
                "S5: 终态复验在场 outcome=-1（不重复记 MISS）");
        TestHost.checkEq((Object) Long.valueOf(10_000L), (Object) Long.valueOf(st.endedAt),
                "S5: endedAt 保持原值（终局时刻不被覆盖）");
        VerifyStillStep st2 = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_SUCCESS, 0, 3, 12_000L, 10_000L);
        TestHost.checkEq((Object) AdTaskCore.S_SUCCESS, (Object) st2.state,
                "S5: SUCCESS 原样返回（成功不可逆）");
        TestHost.checkEq((Object) Integer.valueOf(-1), (Object) Integer.valueOf(st2.outcome),
                "S5: SUCCESS 复验在场不计账");
    }

    /* ════ S6 · FEEDBACK 语义零变化（面板流程在先）════ */
    static void s6_feedbackSemanticsUnchanged() {
        TestHost.suite("Bug001 S6 · FEEDBACK 复验在场 → 原态保持（零行为变化）");
        // 面板在场（甚至预算同时耗尽）→ 面板流程优先，不算广告重试/终局
        VerifyStillStep st = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_FEEDBACK, 3, 3, 10_000L, 0L);
        TestHost.checkEq((Object) AdTaskCore.S_FEEDBACK, (Object) st.state,
                "S6: FEEDBACK 复验在场 → 原态保持（面板收起才是终局信号）");
        TestHost.checkEq((Object) Integer.valueOf(-1), (Object) Integer.valueOf(st.outcome),
                "S6: 不计 RETRY/MISS（面板在场不算广告重试证据）");
        TestHost.checkEq((Object) Long.valueOf(0L), (Object) Long.valueOf(st.endedAt),
                "S6: 不落 endedAt（面板流程未终局）");
    }

    /* ════ S7 · RETRY 口径：仅首击复验计一次 ════ */
    static void s7_retryCountedOncePerClick() {
        TestHost.suite("Bug001 S7 · RETRY 仅 VERIFYING 首击复验计一次（防多扫膨胀）");
        // 同一击后：VERIFYING 复验在场 → RETRY 记一次 → RETRY_WAIT
        VerifyStillStep first = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_VERIFYING, 1, 3, 1_000L, 0L);
        TestHost.checkEq((Object) Integer.valueOf(DecisionEvidence.OUT_RETRY),
                (Object) Integer.valueOf(first.outcome), "S7: 首击复验在场 → RETRY");
        // RETRY_WAIT 期间后续复扫仍见候选 → 不再计（同一击多扫不膨胀）
        VerifyStillStep again = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_RETRY_WAIT, 1, 3, 1_350L, 0L);
        TestHost.checkEq((Object) Integer.valueOf(-1), (Object) Integer.valueOf(again.outcome),
                "S7: RETRY_WAIT 期复验在场 → 不计（同一击只记一次）");
        TestHost.checkEq((Object) AdTaskCore.S_RETRY_WAIT, (Object) again.state,
                "S7: 状态原样（等待下一击/候选变化）");
        // 防御面：RETRY_WAIT 且预算耗尽的纯函数防御路径同样走 MISS 终局
        VerifyStillStep def = AdTaskCore.onVerifyStillStep(
                AdTaskCore.S_RETRY_WAIT, 3, 3, 2_000L, 0L);
        TestHost.checkEq((Object) AdTaskCore.S_FAILED, (Object) def.state,
                "S7: 防御路径 RETRY_WAIT+预算耗尽 → FAILED");
        TestHost.checkEq((Object) Integer.valueOf(DecisionEvidence.OUT_MISS),
                (Object) Integer.valueOf(def.outcome),
                "S7: 防御路径同样记 MISS + 落 endedAt（三漏同样根治）");
        TestHost.checkEq((Object) Long.valueOf(2_000L), (Object) Long.valueOf(def.endedAt),
                "S7: 防御路径 endedAt 落 now");
    }

    /* ════ S8 · 六态计数端到端（DecisionEvidence 记账一致性）════ */
    static void s8_sixStateCountersEndToEnd() {
        TestHost.suite("Bug001 S8 · 三击梯子六态计数（RETRY=2 · MISS=1 · 其余 0）");
        DecisionEvidence.resetForJvm();
        final int max = 3;
        int st = AdTaskCore.S_READY;
        for (int i = 1; i <= max; i++) {
            st = AdTaskCore.onDispatch(st);
            VerifyStillStep step = AdTaskCore.onVerifyStillStep(st, i, max, 1_000L * i, 0L);
            if (step.outcome >= 0) {
                DecisionEvidence.recordOutcome(step.outcome); // 服务侧同源接线
            }
            st = step.state;
        }
        int[] snap = DecisionEvidence.outcomeSnapshot();
        TestHost.checkEq((Object) Integer.valueOf(0), (Object) Integer.valueOf(snap[0]),
                "S8: VERIFIED_SUCCESS=0（预算耗尽路径零虚报）");
        TestHost.checkEq((Object) Integer.valueOf(2), (Object) Integer.valueOf(snap[1]),
                "S8: RETRY=2（两击预算内复验在场）");
        TestHost.checkEq((Object) Integer.valueOf(1), (Object) Integer.valueOf(snap[2]),
                "S8: MISS=1（末击终局一次记账——旧代码整类漏记）");
        TestHost.checkEq((Object) Integer.valueOf(0), (Object) Integer.valueOf(snap[3]),
                "S8: FALSE_CLICK=0（自动化环境恒 0——真机样本唯一来源）");
        TestHost.checkEq((Object) Integer.valueOf(0), (Object) Integer.valueOf(snap[4]),
                "S8: SAFE_BLOCK=0（本路径无终闸拦截）");
        TestHost.checkEq((Object) Integer.valueOf(0), (Object) Integer.valueOf(snap[5]),
                "S8: UNKNOWN=0（不硬凑）");
        // 终局后旧任务复验在场不再污染计数
        VerifyStillStep stale = AdTaskCore.onVerifyStillStep(st, 3, max, 5_000L, 3_000L);
        if (stale.outcome >= 0) DecisionEvidence.recordOutcome(stale.outcome);
        int[] snap2 = DecisionEvidence.outcomeSnapshot();
        TestHost.checkEq((Object) Integer.valueOf(1), (Object) Integer.valueOf(snap2[2]),
                "S8: 终局后旧任务复验在场不再记 MISS（不可逆不重复）");
        DecisionEvidence.resetForJvm(); // 环境清理（进程内诊断状态）
    }
}
