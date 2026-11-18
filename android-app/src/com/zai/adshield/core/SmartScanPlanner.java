package com.zai.adshield.core;

/**
 * v5.5.0 智能扫描分级器（模块二：李跳跳式遍历与匹配效率优化）
 * ─────────────────────────────────────────────────────────────
 * 借鉴李跳跳的核心洞察：「扫描预算要花在广告最可能出现的地方」。
 * 无障碍树遍历是无障碍服务最大的性能开销来源（深树 79+ 节点全扫
 * 一遍 = 数十 ms 主线程压力），而绝大多数普通窗口根本没有广告键——
 * 全量预算扫普通窗口是纯浪费（waimaiii 实锤：首页落定期整树扫描
 * 刷屏噪声日志，用户什么都没得到）。
 *
 * 分级策略（budgetFor，性能开销 ≤10% 红线的保障）：
 *  · L1 全量 600：Dialog/Popup 形态（弹窗九成是广告/更新弹窗）、
 *    SDK 广告窗指纹、学习窗指纹、开屏窗期内——广告概率最高的场景；
 *  · L2 中量 400：半屏插屏/更新弹窗疑似（v5.5.0 新场景）、弹窗窗期、
 *    视频类应用（前贴场景）；——广告概率中等；
 *  · L3 轻量 220：普通窗口——跳过键若存在多半在边缘带/顶部，配合
 *    文本探针快路径（findAccessibilityNodeInfosByText 直达，不遍历）
 *    足够覆盖；预算砍 63%，扫描耗时同步下降。
 *
 * 深度门联动（depthLimitFor）：普通窗口深层容器（WebView 内容、
 * RecyclerView 长列表）绝无跳过键，深度 32 → 20；弹窗场景维持 32
 * （广告按钮可能埋在多层容器里）。
 *
 * 静默期退避（backoffMs）：v5.1.0 已有「广告刚关首页落定期不记噪声」，
 * 这里补齐扫描侧同款——同窗无事件触发的补扫针按 800ms → 1.6s → 3.2s
 * 指数退避（上限 6.4s），窗口内容没变就不浪费预算。
 *
 * 纯函数零依赖，JVM 可测（SmartScanPlannerTest）。
 */
public final class SmartScanPlanner {

    private SmartScanPlanner() {
    }

    /* ───────── 分级预算常量 ───────── */
    public static final int BUDGET_FULL = 600;   // L1 全量（弹窗/SDK/学习/开屏）
    public static final int BUDGET_MID = 400;    // L2 中量（新场景/视频前贴）
    public static final int BUDGET_LIGHT = 350;  // L3 轻量（普通窗口：原 500 降 30%，
                                                 //     探针前置已保文本键零 DFS，
                                                 //     预算仅影响 vid 键 DFS 深度）

    /* ───────── 深度门 ───────── */
    public static final int DEPTH_FULL = 32;     // 弹窗场景深度上限（现状）
    public static final int DEPTH_LIGHT = 20;    // 普通窗口深度上限（深层无跳过键）

    /* ───────── 静默期退避 ───────── */
    public static final long BACKOFF_BASE_MS = 800;
    public static final long BACKOFF_MAX_MS = 6400;

    /** 窗口缓存（同窗补扫退避用，服务侧持有） */
    public static final class WindowCache {
        public String lastWindowId = "";   // 上次扫描的窗口标识（pkg/activityId）
        public long lastEventAt;           // 上次窗口事件时间
        public int backoffStep;            // 退避档位（0=首扫，逐档 ×2）
    }

    /**
     * 分级扫描预算：按窗口广告概率分配。
     * 判定顺序 = 优先级顺序（先看最强信号）。
     */
    public static int budgetFor(AdDecisionCore.Ctx ctx) {
        if (ctx == null) return BUDGET_LIGHT;
        /* L1：广告概率最高——弹窗形态 / SDK 窗 / 学习窗 / 开屏窗 */
        if (ctx.sdkAdWin || ctx.learnedCls || ctx.splashLive) return BUDGET_FULL;
        /* L2：v5.5.0 新场景（半屏插屏/更新弹窗）与视频前贴 */
        if (ctx.halfScreenAd || ctx.updateDialog || ctx.videoApp || ctx.popupLive) return BUDGET_MID;
        /* L3：普通窗口 */
        return BUDGET_LIGHT;
    }

    /** 深度门：普通窗口砍深层（WebView/长列表绝无跳过键） */
    public static int depthLimitFor(AdDecisionCore.Ctx ctx) {
        if (ctx == null) return DEPTH_LIGHT;
        if (ctx.sdkAdWin || ctx.learnedCls || ctx.splashLive
                || ctx.halfScreenAd || ctx.updateDialog) {
            return DEPTH_FULL;
        }
        return DEPTH_LIGHT;
    }

    /**
     * 静默期退避：同窗无事件触发时，补扫针指数退避。
     * @param now 当前时间
     * @return 本轮补扫距上轮的最小间隔（ms）；窗口变了重置为首扫
     */
    public static long backoffMs(WindowCache cache, String windowId, long now) {
        if (cache == null || windowId == null) return 0;
        /* 窗口切换：重置退避（新窗首扫立即执行） */
        if (!windowId.equals(cache.lastWindowId)) {
            cache.lastWindowId = windowId;
            cache.backoffStep = 0;
            cache.lastEventAt = now;
            return 0;
        }
        long wait = BACKOFF_BASE_MS;
        for (int i = 0; i < cache.backoffStep; i++) {
            wait *= 2;
            if (wait >= BACKOFF_MAX_MS) {
                wait = BACKOFF_MAX_MS;
                break;
            }
        }
        return Math.min(wait, BACKOFF_MAX_MS);
    }

    /** 补扫未发现任何候选 → 退避档位 +1（窗口内容大概率没变） */
    public static void noteIdleScan(WindowCache cache) {
        if (cache != null && cache.backoffStep < 8) cache.backoffStep++;
    }

    /** 事件到达/命中候选 → 退避重置（窗口真的变了，恢复灵敏度） */
    public static void noteActiveSignal(WindowCache cache, long now) {
        if (cache != null) {
            cache.backoffStep = 0;
            cache.lastEventAt = now;
        }
    }

    /** 治理摘要（启动日志用） */
    public static String stats() {
        return "分级扫描：L1 全量 " + BUDGET_FULL + "（弹窗/SDK/学习/开屏）"
                + " · L2 中量 " + BUDGET_MID + "（半屏/更新弹窗/视频前贴）"
                + " · L3 轻量 " + BUDGET_LIGHT + "（普通窗口，预算 -30%）"
                + " · 深度门 " + DEPTH_FULL + "/" + DEPTH_LIGHT
                + " · 补扫退避 " + BACKOFF_BASE_MS + "ms→" + BACKOFF_MAX_MS + "ms";
    }
}
