package com.zai.adshield.core;

/**
 * 广告任务状态机（v7.9.1 · 纯 Java · 零 Android 依赖 · JVM 可单测）——
 * 「重复点击 / 误触 / 合法重试」三难根治的核心契约。
 * ─────────────────────────────────────────────────────────────
 * 用户实锤（v7.9.0 真机日志三类事故，均已真实出现）：
 *  · 情况 A：第一次点击已成功关闭广告，随后引擎再次执行点击，第二次
 *    点击落到其他普通按钮上（误触）；
 *  · 情况 B：第一次已关闭广告，第二次又执行了一次关闭操作（重复关闭）；
 *  · 情况 C：第一次点击未真正关闭，经复验/重试后第二次才成功（合法
 *    重试——绝不能一刀切关闭）。
 * ─────────────────────────────────────────────────────────────
 * v7.9.0 及之前版本的根因（AdSkipService 全链路审计实锤）：
 *  · 引擎没有「广告任务」实体——所有状态散落在 per-package 的十余张
 *    HashMap（clickTries / gaveUp / lastClickAt / skipAt / adClosedAt /
 *    chainKilledAt / firedTarget* / lastGestureOkAt …）；
 *  · 「成功」的唯一信号是 TYPE_WINDOW_STATE_CHANGED 的窗口类切换
 *    （AdSkipService:1693-1704）——对 Activity 不变的插屏/覆盖层/
 *    Dialog 广告（用户第五轮强调的核心场景），成功信号【永远不触发】，
 *    状态永不重置、任务永不终止；
 *  · 「广告已关」没有任何不可逆终态——反馈面板收起后、广告标签
 *    detach 前的过渡期里，插屏扫描每 1.5s 重扫一次，残留的「广告」
 *    语境词 + 「×/关闭」候选再次过闸 → 第二击落在残留 UI / 反馈
 *    面板 / 普通按钮上（情况 A/B 的直接机制）。
 *
 * ══ 状态机契约 ══
 *  DETECTED   检测到疑似广告（任务开启，尚未出手）；
 *  READY      候选已过安全闸，可执行点击；
 *  CLICKING   正在执行点击（提交中——防双通道并发）；
 *  VERIFYING  已出手，等待并验证点击结果（500ms 复验针）；
 *  RETRY_WAIT 复验未关但候选仍在场 → 合法重试等待（重试前置检查表
 *             全过才允许再次出手——绝不盲点旧坐标）；
 *  FEEDBACK   反馈面板处理中（独立流程——绝不对原广告再执行关闭）；
 *  SUCCESS    终态·不可逆：广告已确认关闭。所有延迟任务/重试全部
 *             作废，该任务后续一切点击请求与过期回调一律拒收；
 *  FAILED     终态·不可逆：点击预算用尽（优雅收手）；
 *  CANCELLED  终态·不可逆：窗口切换零出手 / 任务作废。
 *
 * 终态解禁唯一路径：新任务（同窗新广告须过 taskAllowsNew 冷却闸，
 * 2.2s——广告 detach 过渡期 / 反馈面板期 / 跳转动画期内的残留标签
 * 不得被当成新广告）。
 *
 * ══ 服务侧接线（AdSkipService 薄接线，全部既有防线原样保留）══
 *  · execute() 出手前：任务门 taskAllowsClick（终态/反馈态一律拒）；
 *  · 出手后：VERIFYING + 复验针带 taskId（过期回调验活）；
 *  · 复验两轮零候选（≥350ms 间隔）→ 就地 SUCCESS（杀链 + 冷却）；
 *  · 窗口类切换且本任务有过出手 → SUCCESS（原「复验通过」路径）；
 *  · 反馈面板在场 → FEEDBACK（tryScan / maybeInterstitialScan /
 *    videoPoll 三路全部前置——插屏路径此前完全盲，v7.9.1 根治）；
 *  · 预算刷新（视频新键）→ READY（新任务）——v7.8.0 视频开屏
 *    「视频末露头新按钮」能力零回退。
 *
 * 设计红线：纯函数零依赖（与 InterstitialGuard/VideoSplashGuard 同规约），
 * ECJ -1.8 编译 JVM 可测；绝不放宽任何安全闸——只收紧「不该再点的击」。
 */
public final class AdTaskCore {

    private AdTaskCore() {
    }

    /* ══ 状态常量（public——测试断言在场性与真值表）══ */
    public static final int S_NONE = -1;        // 无任务（本窗未开启）
    public static final int S_DETECTED = 0;     // 检测到疑似广告
    public static final int S_READY = 1;        // 候选过闸，可执行
    public static final int S_CLICKING = 2;     // 点击执行中（双通道互斥锚）
    public static final int S_VERIFYING = 3;    // 出手后等待验证（500ms 复验针）
    public static final int S_RETRY_WAIT = 4;   // 复验未关→合法重试等待
    public static final int S_FEEDBACK = 5;     // 反馈面板处理中（独立流程）
    public static final int S_SUCCESS = 6;     // 终态：广告已关（不可逆）
    public static final int S_FAILED = 7;       // 终态：预算用尽（不可逆）
    public static final int S_CANCELLED = 8;    // 终态：作废（不可逆）

    /** 任务类别（诊断与冷却口径） */
    public static final int KIND_SPLASH = 0;
    public static final int KIND_INTER = 1;
    public static final int KIND_FEED = 2;
    public static final int KIND_POPUP = 3;

    /** 终态后同窗新任务冷却：2.2s（与 POST_SKIP_QUIET_MS 同源——广告
     * detach / 反馈面板 / 跳转动画的过渡期残留标签不得被当成新广告；
     * 同时 > F 防线 1.6s 同指纹静默——新任务的同位新 × 不被旧防线误杀）。
     * 代价：同窗真·新广告最多晚 2.2s 出手（宁慢勿点第二下）。 */
    public static final long TASK_NEW_COOLDOWN_MS = 2200;

    /** 就地 SUCCESS 的两轮零候选复验最小间隔（第一轮零 → 排确认针，
     * 第二轮仍零才判关——树刷新瞬间的候选抖动不得误判「已关」）。 */
    public static final long VERIFY_CONFIRM_GAP_MS = 350;

    /* ══ 基础判定 ══ */

    /** 终态判定：SUCCESS / FAILED / CANCELLED 三态不可逆。 */
    public static boolean isTerminal(int state) {
        return state == S_SUCCESS || state == S_FAILED || state == S_CANCELLED;
    }

    /** v7.9.5 状态名（决策证据 Evidence.st 用——纯查表，非法值 UNKNOWN）。 */
    public static String stateName(int state) {
        switch (state) {
            case S_NONE: return "NONE";
            case S_DETECTED: return "DETECTED";
            case S_READY: return "READY";
            case S_CLICKING: return "CLICKING";
            case S_VERIFYING: return "VERIFYING";
            case S_RETRY_WAIT: return "RETRY_WAIT";
            case S_FEEDBACK: return "FEEDBACK";
            case S_SUCCESS: return "SUCCESS";
            case S_FAILED: return "FAILED";
            case S_CANCELLED: return "CANCELLED";
            default: return "UNKNOWN";
        }
    }

    /** 成功终态（拒收一切点击与回调——「成功必须不可逆」）。 */
    public static boolean isSuccess(int state) {
        return state == S_SUCCESS;
    }

    /**
     * 点击提交门：仅 READY / RETRY_WAIT / DETECTED / VERIFYING 允许
     * 提交新点击（VERIFYING 放行 = 复验扫描重新看到候选的合法重试路，
     * 转移由 onDispatch/onRetry 统一记）。终态与 FEEDBACK / CLICKING
     * 一律拒——「任务只能有一个有效点击执行者」与「反馈面板期绝不
     * 重新执行广告关闭」。
     */
    public static boolean clickAllowed(int state) {
        return state == S_READY || state == S_RETRY_WAIT || state == S_DETECTED
                || state == S_VERIFYING;
    }

    /** 过期回调验活门：终态任务的延迟回调（复验针/补扫/轮询针）一律作废。 */
    public static boolean callbackAllowed(int state) {
        return !isTerminal(state);
    }

    /* ══ 转移（纯函数——非法转移原样返回当前态，绝不静默升级）══ */

    /** 出手派发成功后 → VERIFYING（含合法重试路：RETRY_WAIT/VERIFYING 再出手
     *  均收敛到 VERIFYING——每击后都重新起复验）。 */
    public static int onDispatch(int state) {
        if (!clickAllowed(state)) return state;
        return S_VERIFYING;
    }

    /**
     * 复验结果①广告已关（候选消失/目标窗变化/反馈面板收起）→ SUCCESS。
     * 不可逆：此后 onRetry/onDispatch 等一切非终态转移全部被
     * clickAllowed/callbackAllowed 拒收。
     * v7.9.4：S_NONE（无任务）同拒——任何复验事件都不得从「无任务」
     * 无中生有出状态（服务侧 null 守卫已保证不传 S_NONE，此处纯契约
     * 加固——AdTaskTimingTest 无任务面真值表）。
     */
    public static int onVerifyAdGone(int state) {
        if (state == S_NONE) return state;          // 无任务：不无中生有
        if (isTerminal(state)) return state;          // 终态不可逆
        if (state == S_FEEDBACK) return S_SUCCESS;    // 反馈面板收起=广告已关铁证
        return S_SUCCESS;
    }

    /**
     * 复验结果②广告仍在场（原候选/同签名候选仍存在）→ RETRY_WAIT
     * （合法有限重试）；预算用尽 → FAILED（优雅收手——绝不无限重试）。
     */
    public static int onVerifyAdStill(int state, int clicks, int maxClicks) {
        if (state == S_NONE) return state;            // v7.9.4 无任务：不无中生有
        if (isTerminal(state)) return state;
        if (state == S_FEEDBACK) return S_FEEDBACK;   // 面板在场：先收面板，不重试广告
        return clicks < maxClicks ? S_RETRY_WAIT : S_FAILED;
    }

    /**
     * 复验结果④页面已变化但无法确认广告是否关闭 → 不确定态：回到
     * RETRY_WAIT 观察（下一轮复验重新定位候选——绝不对旧坐标直接补刀）。
     * 预算已尽则 FAILED。
     */
    public static int onVerifyUncertain(int state, int clicks, int maxClicks) {
        if (state == S_NONE) return state;            // v7.9.4 无任务：不无中生有
        if (isTerminal(state)) return state;
        if (state == S_FEEDBACK) return S_FEEDBACK;
        return clicks < maxClicks ? S_RETRY_WAIT : S_FAILED;
    }

    /**
     * 复验结果③反馈面板出现（「不喜欢/反馈」族）→ FEEDBACK 独立流程：
     * 绝不把面板当成原广告仍在场，绝不再执行原广告关闭动作；只允许
     * 「取消」语义收起（绝不替用户提交反馈）。仅非终态可进入。
     */
    public static int onFeedbackSeen(int state) {
        if (state == S_NONE) return state;            // v7.9.4 无任务：不无中生有
        if (isTerminal(state)) return state;
        return S_FEEDBACK;
    }

    /** 面板收起（「取消」生效）→ SUCCESS（面板只在广告关闭后弹出）。 */
    public static int onFeedbackDone(int state) {
        if (state == S_FEEDBACK) return S_SUCCESS;
        return state;
    }

    /**
     * 窗口切换终局：本任务有过出手 → SUCCESS（原「复验通过」语义——
     * 窗口已切走=点击生效）；零出手 → CANCELLED（本窗任务作废）。
     */
    public static int onWindowChanged(int state, boolean hadClicks) {
        if (state == S_NONE) return state;            // v7.9.4 无任务：不无中生有
        if (hadClicks) return S_SUCCESS;
        return S_CANCELLED;
    }

    /** 用户接管/任务作废 → CANCELLED。 */
    public static int onCancelled(int state) {
        if (state == S_NONE) return state;            // v7.9.4 无任务：不无中生有
        if (isTerminal(state)) return state;
        return S_CANCELLED;
    }

    /* ══ 重试前置检查表（情况 C 合法重试的保护门——全 true 才允许再出手）══ */

    /**
     * 重试合法五要件（任何一条不满足都不得对旧坐标/旧候选出手）：
     *  · adStillPresent   本轮复验扫描仍见到广告线索（候选/语境在场）；
     *  · sameWindow       仍是原任务窗口（winStart 一致——页面已变=绝不
     *                      沿用旧坐标重试）；
     *  · candidateAlive    原候选仍在树中（候选消失=换候选或判关，绝不点空）；
     *  · candidateVisible 候选当前可见（服务侧 fresh bounds 复核供给）；
     *  · closeSemantics   候选语义仍指向关闭广告（execute 终闸复核供给）。
     * 返回 false 时：情况④（页面变化无法确认）→ 观察不补刀；
     * 返回 true 时：RETRY_WAIT → 允许有限重试（预算仍受 maxClicks 钳制）。
     */
    public static boolean retryGuardsOk(boolean adStillPresent, boolean sameWindow,
                                         boolean candidateAlive, boolean candidateVisible,
                                         boolean closeSemantics) {
        return adStillPresent && sameWindow && candidateAlive && candidateVisible && closeSemantics;
    }

    /* ══ 新任务冷却闸（情况 A/B 的终态防复活门）══ */

    /**
     * 终态后同窗是否允许开新任务：距终态时刻 ≥TASK_NEW_COOLDOWN_MS。
     * 冷却内：刚关掉的广告标签 detach 残留期 / 反馈面板期 / 跳转动画期
     * 的「广告」语境 + 「×/关闭」候选【不是新广告】——一律拒收。
     * 无终态记录（未关过）恒放行（首任务/合法重试不受影响）。
     */
    public static boolean taskAllowsNew(long now, long taskEndedAt) {
        if (taskEndedAt <= 0) return true;
        return now - taskEndedAt >= TASK_NEW_COOLDOWN_MS;
    }

    /**
     * 视频开屏新键预算刷新（v7.8.0 契约保持）：视频末才露头的【文本键】
     * 新指纹目标是新按钮不是旧键白点——刷新预算=开新任务（READY）。
     * 角落/手势候选（无文本）绝不刷新（误触红线）。
     */
    public static boolean budgetRefreshForNewTarget(boolean videoLike, boolean newTarget,
                                                    boolean textLabel) {
        return videoLike && newTarget && textLabel;
    }

    /** 复验两轮零候选的就地成功判定（第一轮时刻 + 本轮时刻 + 仍零候选）：
     *  轮间隔 ≥VERIFY_CONFIRM_GAP_MS 才算独立两轮（树刷新抖动不误判）。 */
    public static boolean zeroVerifyConfirmed(Long firstZeroAt, long now, boolean stillZero) {
        if (!stillZero) return false;
        return firstZeroAt != null && now - firstZeroAt.longValue() >= VERIFY_CONFIRM_GAP_MS;
    }
}
