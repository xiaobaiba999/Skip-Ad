package com.zai.adshield.core;

/**
 * 视频开屏卫士（v7.8.0 · 纯 Java · 零 Android 依赖 · JVM 可单测）——
 * 「开屏广告带视频的全部未命中」根治（用户十八轮实锤）。
 * ─────────────────────────────────────────────────────────────
 * 用户原话：「很多没命中，只要是开屏广告带视频的你都未命中」。
 * ─────────────────────────────────────────────────────────────
 * 死因链（扫描日志驱动诊断）：
 *  ① 12s 观察窗天花板：WINDOW_MS=12000 硬封顶——视频开屏（穿山甲/优量汇
 *    /快手广告视频开屏）跳过钮常在视频末尾才出现（5~15s），&gt;12s 露头的
 *    按钮引擎已出窗判 CLEAN——字面意义的「不再扫」。
 *  ② 静默事件源：视频在 SurfaceView/TextureView 里播放、进度环是 Canvas
 *    自绘——零 TYPE_WINDOW_CONTENT_CHANGED → 零候选退避（3 次+2.6s）杀链
 *    后零事件零复活（nudge 救回通道彻底静默）。
 *  ③ 预算烧穿：图片跳过键（进度环无数字 → cdN=0 → 门控直通）从 CORNER_MIN
 *    300ms 起 3 击连发（MAX_CLICKS_PER_WIN=3 + 500ms 复验节奏——1 秒内烧
 *    光），按钮真激活（视频播完）时引擎已 gaveUp 优雅收手——永久沉默。
 *  ④ L4 形态确认门：第一方视频开屏窗无角标文本/无热区/非 SDK 指纹、树
 *    &gt;60 节点（WebView 视频容器常态 &gt;150）→ 角落候选收集了永不出手；
 *    CORNER_MAX_MS=6500 也把晚到键关在门外。
 *
 * 修复（画像驱动——引擎观测面内可判定的纯函数）：
 *  · 视频画像 videoLike：窗龄 ≥2.2s（按钮常态 0~2s 渲染完）且连续零候选
 *    ≥3 次且树静默 ≥1.6s（SurfaceView 在放 = 零内容事件）——或 SDK 广告
 *    窗 + 连续零候选 ≥3（csj/ksad 视频开屏按钮常在视频末才挂树）；
 *  · 观察窗自适应延长：videoLike → 窗效 12s→20s（VIDEO_EXT_WINDOW_MS）
 *    ——晚露头按钮有人扫；
 *  · 轮询针：videoLike 窗 1.5s/针探针直查（零 DFS——findAccessibility
 *    NodeInfosByText 5 词——视频播放期零内容事件由轮询兜底）；
 *  · 白点节流：videoLike 窗角落/手势击 ≥1.5s/击（3 击 1s 烧穿 → 预算
 *    拉长到 ≥3s 覆盖门控期）+ 预算 3→5（VIDEO_MAX_CLICKS）；
 *  · 新键预算刷新：视频窗内【文本键】新指纹目标（真「跳过」文字键——
 *    视频末才渲染的激活按钮）→ 点击梯子预算重置——新按钮≠旧按钮的白点；
 *  · 退避豁免：videoLike 窗零候选退避/内容页早退双双豁免（轮询针接管
 *    观察义务——探针直查是零 DFS 的轻量路径，功耗可控）。
 *
 * 与 SplashCountdownTest/AggressiveModeTest 同规约：纯函数核心，ECJ -1.8
 * 编译 JVM 可测；服务侧薄接线（AdSkipService.tryScan/videoPoll）。
 */
public final class VideoSplashGuard {

    private VideoSplashGuard() {
    }

    /* ══ 常量契约（全部 public——测试断言在场性）══ */

    /** 视频画像观察窗延长（12s→20s——视频开屏跳过钮最晚 15s 左右露头）。 */
    public static final long VIDEO_EXT_WINDOW_MS = 20000;
    /** 画像最早评估窗龄（按钮常态 0~2s 渲染——更早的零候选只是还没渲染）。 */
    public static final long VIDEO_PROFILE_MIN_MS = 2200;
    /** 树静默阈值（SurfaceView 视频在放 = 零内容事件 ≥1.6s）。 */
    public static final long VIDEO_SILENCE_MIN_MS = 1600;
    /** 连续零候选次数门槛（与零候选退避同源 3 次）。 */
    public static final int VIDEO_ZERO_CAND_STREAK = 3;
    /** SDK 广告窗画像最早窗龄（SDK 视频开屏：按钮常在视频末才挂树）。 */
    public static final long VIDEO_SDK_MIN_MS = 3200;
    /** 轮询针间隔（探针直查——零 DFS 的轻量路径）。 */
    public static final long VIDEO_POLL_MS = 1500;
    /** 门控期白点节流：视频窗角落/手势击 ≥1.5s/击。 */
    public static final long VIDEO_STRIKE_GAP_MS = 1500;
    /** 视频开屏点击预算（3→5——预算拉长覆盖门控期）。 */
    public static final int VIDEO_MAX_CLICKS = 5;
    /** 视频开屏角落键树上限放宽（150→260——WebView 视频容器常态 >150）。 */
    public static final int VIDEO_CORNER_TREE_MAX = 260;

    /* ══ 画像与门（纯函数）══ */

    /**
     * 视频开屏画像：窗龄 ≥VIDEO_PROFILE_MIN_MS 且连续零候选 ≥3 次且树静默
     * ≥VIDEO_SILENCE_MIN_MS（第一方容器路径——SurfaceView 在放的观测面）；
     * 或 SDK 广告窗 + 连续零候选 ≥3（SDK 视频开屏路径——按钮常在视频末才
     * 挂树，窗本身即广告窗零候选必是视频容器）。
     *
     * @param elapsedMs      距窗口切换的窗龄
     * @param zeroCandStreak 本窗连续零候选扫描次数（含当前）
     * @param silenceMs      距最后一次内容事件的静默时长（无事件 = -1）
     * @param sdkAdWin       当前窗口类命中 SDK 广告指纹
     */
    public static boolean videoLike(long elapsedMs, int zeroCandStreak, long silenceMs, boolean sdkAdWin) {
        if (zeroCandStreak < VIDEO_ZERO_CAND_STREAK) return false;
        if (sdkAdWin) return elapsedMs >= VIDEO_SDK_MIN_MS;
        if (elapsedMs < VIDEO_PROFILE_MIN_MS) return false;
        return silenceMs >= VIDEO_SILENCE_MIN_MS;
    }

    /** 观察窗效：videoLike → 20s（延长档）；普通开屏 → 12s（原 WINDOW_MS）。 */
    public static long windowDeadlineMs(boolean videoLike) {
        return videoLike ? VIDEO_EXT_WINDOW_MS : 12000;
    }

    /** 轮询针是否到期（距上针 ≥VIDEO_POLL_MS；无记录 = 立即到期）。 */
    public static boolean pollDue(Long lastPollAt, long now) {
        return lastPollAt == null || now - lastPollAt.longValue() >= VIDEO_POLL_MS;
    }

    /** 门控期白点节流：视频窗角落/手势击距上击 ≥VIDEO_STRIKE_GAP_MS 才放行。 */
    public static boolean strikeGapOk(Long lastStrikeAt, long now) {
        return lastStrikeAt == null || now - lastStrikeAt.longValue() >= VIDEO_STRIKE_GAP_MS;
    }

    /**
     * 新键预算刷新：视频窗内【文本键】新指纹目标（从未出手过的 target
     * 指纹 + 决策带文本 label——视频末才渲染的激活「跳过」键）→ 点击梯子
     * 预算重置。角落/手势候选（无文本——白点高发面）绝不刷新：移动中的
     * 进度环候选换指纹不换按钮，刷新 = 无限预算（误触红线）。
     */
    public static boolean budgetRefresh(boolean videoLike, boolean newTarget, boolean textLabel) {
        return videoLike && newTarget && textLabel;
    }

    /** 视频窗点击预算：videoLike → VIDEO_MAX_CLICKS；普通窗 → 3（原契约）。 */
    public static int maxClicksFor(boolean videoLike) {
        return videoLike ? VIDEO_MAX_CLICKS : 3;
    }
}
