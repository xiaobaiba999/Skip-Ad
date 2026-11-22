package com.zai.adshield.core;

/**
 * ═══════════════════════════════════════════════════════════════
 *  v5.3.0 功耗治理器（PowerGovernor）—— 李跳跳式「三零治理」
 * ═══════════════════════════════════════════════════════════════
 *  真机日志 #57/#58（v5.2.0 · Xiaomi 2410DPN6CC · Android 17）实锤：
 *    能耗画像（本次开机）：事件 4604 · 扫描 2396 · 手势估算 2738µWh
 *    —— 实测「功耗还是很高」的三大元凶：
 *
 *  ①【宿主主窗误扫】豆包 MainActivity（115 节点对话列表）/ 微信
 *    ExdeviceRankInfoUI（110 节点排行页）/ 拼多多 NewPageActivity
 *    （23 节点落地页）/ 安全中心 ApplicationsDetailsActivity——它们
 *    从来不是广告窗，却每次启动都被 13 档固定梯子整树 DFS 扫描 +
 *    记「开屏未命中」。同一 (pkg|cls) 零候选几十次，功耗全白烧。
 *
 *  ②【内容突发误报】聊天/播放器类动态内容应用（AI 流式回复/播放
 *    进度条）1.5s 内 ≥3 次内容变化是【正常业务形态】——v5.2.0 的
 *    突发检测把它当「疑似覆盖层弹窗」反复开 10s 弹窗识别窗（豆包
 *    5 分钟 7 次，每次窗内所有内容事件放行 + 300ms 节流补扫）。
 *
 *  ③【位置学习无验证】豆包聊天界面手动点击（同秒 (17%,82%) 与
 *    (41%,82%) 两处）被当「图片跳过键」学走 → 次启同窗 500ms 直点
 *    聊天气泡 = 乱点复发。真跳过键点击必然关窗/换窗，聊天点击
 *    必然不换窗——这就是天然的分界线（事后验证）。
 *
 *  ── 李跳跳（市面功耗标杆）方法论落地 ──
 *  李跳跳整 App 只有一步：findAccessibilityNodeInfosByText("跳过")
 *  系统原生子树搜索（C++ 侧执行，零 DFS 内存遍历），所以它几乎不
 *  耗电。本引擎在保留全部纵深能力的前提下重构执行顺序：
 *
 *    【探针前置】每个窗口 0ms 先跑原生文本探针（成本≈1 次系统调用）：
 *      · 探针命中 → 立即出手（文本类广告全流程零 DFS）；
 *      · 探针不中 + 窗档案 CLEAN → 本窗【零扫描零调度零日志】，
 *        仅保留内容事件探针哨兵（真跳过键一出现立即复活）；
 *      · 探针不中 + 窗档案 ACTIVE → 走原 13 档梯子（能力不减）。
 *
 *  ── 状态机（全部纯函数，JVM 可单测）─────────────────────────
 *  宿主窗档案 HostWindowProfile（持久化，per pkg|cls）：
 *    ACTIVE  ──连续 CLEAN_STREAK_MIN 次零候选──▶  CLEAN（省电态）
 *    CLEAN   ──探针命中/关闭线索/出现候选/订阅命中/SDK 指纹/Dialog/学习规则/
 *              手动教学/复查期到（v8.1.0：72h→24h）──▶ ACTIVE（全量恢复）
 *  突发预算 BurstBudget（per pkg，内存）：
 *    10 分钟窗内突发识别窗 ≤ BURST_BUDGET；空手突发后阈值指数
 *    递增（3→6→12 事件）——聊天流式内容一次都别再开窗。
 *  位置学习验证 PosVerify（per pkg，内存）：
 *    零文本手动点击 → 挂起 1.5s；窗类切换 / 树体缩水 ≥40% / 应用
 *    退后台 → 提交学习；超时无变化 → 静默丢弃（聊天点击白学归零）。
 */
public final class PowerGovernor {

    private PowerGovernor() {
    }

    /* ═════════ 常量（全部 public：测试与服务侧共用）═════════ */

    /** 连续零候选实例数 → 窗档案 CLEAN（#57/#58：主窗类误扫 3 次即够） */
    public static final int CLEAN_STREAK_MIN = 3;

    /** CLEAN 档案复查周期：v8.1.0 由 72h 收紧到 24h——真机反馈「很多 App
     *  跳不过」根因之二配套：CLEAN 档案（跨重启持久）期间无订阅覆盖的窗只
     *  剩 5 词文本探针，「关闭/×」类词典外关闭键整窗失明。复查期收紧 +
     *  v8.1.0 关闭线索探测（nativeCloseCueProbe）双管齐下，把 CLEAN 失明的
     *  最坏持续时间从 72h 压到 24h。防广告改版永久漏拦的语义不变。 */
    public static final long PROFILE_RECHECK_MS = 24L * 3600 * 1000;

    /** CLEAN 但订阅覆盖的应用保留的低频梯子（vid 类规则探针够不到） */
    public static final long[] LADDER_CLEAN_SUB = {0L, 700L};

    /** 每应用 10 分钟窗内的突发识别窗预算（超了绝不再开） */
    public static final int BURST_BUDGET_PER_10MIN = 2;

    /** 突发阈值基础值（与 v5.2.0 BURST_COUNT=3 对齐） */
    public static final int BURST_BASE_COUNT = 3;

    /** 空手突发后阈值升级步进：3 → 6 → 12 → 24（指数） */
    public static final int BURST_ESCALATE_MAX_LEVEL = 3;

    /** 位置学习挂起验证窗口（ms） */
    public static final long POS_VERIFY_MS = 1500;

    /** 树体缩水提交阈值（Dialog 消散/广告容器卸载 ≥40% 节点消失） */
    public static final double POS_VERIFY_SHRINK = 0.40;

    /* ═════════ ① 宿主窗档案 ═════════ */

    /**
     * 本窗是否应抑制全量固定梯子（CLEAN 态且无强信号）。
     * 强信号（任一存在即不抑制，全量扫描）：
     *   Dialog/PopupWindow 窗（弹窗识别本就独立门控）· SDK 广告窗指纹 ·
     *   className 学习指纹命中 · 学习位置在场（手动教过）·
     *   72h 复查期到（放行一次全量）。
     * 订阅覆盖 → 抑制但降级为低频档 {0,700}（ladderFor 消费）。
     *
     * @param cleanStreak   该 (pkg|cls) 连续零候选次数（持久化档案）
     * @param dialog        当前窗口是否 Dialog/Popup 形态
     * @param sdkWin        窗口类名是否命中 SDK 广告指纹
     * @param learnedFinger className 学习指纹命中（winClsLearned）
     * @param learnedPos    学习位置在场（manual ≥ 1 同窗类）
     * @param msSinceFull   距上次全量扫描毫秒数（0 = 从未）
     */
    public static boolean suppressLadder(int cleanStreak, boolean dialog, boolean sdkWin,
                                         boolean learnedFinger, boolean learnedPos,
                                         long msSinceFull) {
        if (cleanStreak < CLEAN_STREAK_MIN) return false;   // ACTIVE：全量
        if (dialog || sdkWin || learnedFinger || learnedPos) return false;
        return msSinceFull < PROFILE_RECHECK_MS;             // 复查期到 → 放行全量
    }

    /**
     * 本窗实际使用的梯子档位。
     * 未抑制 → null（服务侧原 13 档全量）；
     * CLEAN + 订阅覆盖 → {0, 700} 低频档（探针 + 一次 vid DFS）；
     * CLEAN 无订阅 → 空数组（纯探针哨兵，零 DFS）。
     */
    public static long[] ladderFor(boolean suppress, boolean subCovered) {
        if (!suppress) return null;                 // null = 用服务侧原 13 档
        return subCovered ? LADDER_CLEAN_SUB : new long[0];
    }

    /**
     * 扫描结束后档案更新：返回新的连续零候选计数。
     * 零候选（无任何可出手信号）→ streak + 1；出现候选/出手 → 归零（ACTIVE）。
     * CLEAN 态复查全量扫描不增不减（保持 CLEAN，除非真有候选 → 归零）。
     */
    public static int nextStreak(int curStreak, boolean hasActionable, boolean zeroCand) {
        if (hasActionable) return 0;
        if (zeroCand) return curStreak + 1;
        return curStreak; // 非零候选但未出手（冷却/让路等）：不奖不罚
    }

    /* ═════════ ② 突发预算 ═════════ */

    /**
     * 突发检测是否放行（计数达到阈值时）。
     *
     * @param profileClean 本窗档案是否 CLEAN（CLEAN 窗的动态内容 = 业务
     *                     形态，绝不开弹窗识别窗——豆包/微信聊天根治）
     * @param chatMainUi   是否聊天主界面（微信 LauncherUI 等已知动态窗）
     * @param burstsInWin  本 10 分钟窗内已开的突发识别窗数
     * @param burstEvents  本轮滑动窗内内容变化事件数
     * @param escalateLv   阈值升级等级（0=3 事件，1=6，2=12，3=24）
     */
    public static boolean burstGate(boolean profileClean, boolean chatMainUi,
                                    int burstsInWin, int burstEvents, int escalateLv) {
        if (profileClean || chatMainUi) return false;
        if (burstsInWin >= BURST_BUDGET_PER_10MIN) return false;
        int threshold = BURST_BASE_COUNT << Math.min(escalateLv, BURST_ESCALATE_MAX_LEVEL);
        return burstEvents >= threshold;
    }

    /** 空手突发（识别窗结束一个弹窗都没关）→ 阈值升级；有战果 → 归零。 */
    public static int nextEscalate(int curLevel, boolean popupFound) {
        if (popupFound) return 0;
        return Math.min(curLevel + 1, BURST_ESCALATE_MAX_LEVEL);
    }

    /* ═════════ ③ 位置学习事后验证 ═════════ */

    /**
     * 挂起中的零文本位置学习是否可提交。
     *
     * @param dtMs           距手动点击毫秒数
     * @param windowChanged  窗口类已切换（广告窗退出/Dialog 消散的铁证）
     * @param treeShrunk     树体节点数较点击时缩水 ≥ POS_VERIFY_SHRINK
     *                       （同窗广告容器原地卸载形态）
     * @param appLeft        应用已退后台（冷门形态：跳过即退的场景）
     */
    public static boolean posCommit(long dtMs, boolean windowChanged, boolean treeShrunk, boolean appLeft) {
        if (dtMs < 0 || dtMs > POS_VERIFY_MS) return false; // 超时：聊天点击，丢弃
        return windowChanged || treeShrunk || appLeft;
    }

    /** 树体是否显著缩水（Dialog 消散 / 广告容器卸载）。nodeCount<=0 视为树已消失。 */
    public static boolean treeShrunk(int nodesAtClick, int nodesNow) {
        if (nodesAtClick <= 0) return nodesNow <= 0;
        return nodesNow <= nodesAtClick * (1.0 - POS_VERIFY_SHRINK);
    }

    /* ═════════ ④ 能耗记账（v5.3.0 新增探针/档案维度）═════════ */

    /**
     * 近似能耗模型（µWh，量级对照用）：
     *   事件 0.22 · 探针 0.05 · 扫描 0.72 · 返回键 0.9
     * 探针是系统侧 C++ 子树搜索，成本≈事件处理的 1/4、DFS 扫描的 1/14。
     */
    public static double microWh(long events, long probes, long scans, long backs) {
        return events * 0.22 + probes * 0.05 + scans * 0.72 + backs * 0.9;
    }

    /**
     * 位置学习健康度快照（日志/上报用）：验证通过才计入「已学习」，
     * 拒绝/丢弃单独计数——用户能看到引擎替他挡了多少次「白学」。
     */
    public static final class PosLearnStats {
        public int verified;   // 验证通过并学习
        public int dropped;    // 超时丢弃（聊天点击等）
        public int rejected;   // 越界/形状拒绝（v3.7.0 既有路径）
    }
}
