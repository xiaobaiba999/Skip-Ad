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

    /* ══ v7.9.0 视频画像竞速根治（树慢速构建窗的退避竞速死因）══ */
    /** v7.9.0 画像预备门槛（静默半阈值）：WebView/H5 视频容器树慢速构建期
     *  （内容事件持续到 ~2s）静默达不到 1.6s 全阈值时，零候选退避已在
     *  streak≥3 + 窗龄≥2.6s 处杀链——链死 → 静默采样断供 → videoLike 永远
     *  建立不起来 → 轮询针永不排 = 视频开屏整窗漏拦（用户实锤「只要是
     *  开屏广告带视频的你都未命中」的树慢速构建亚型）。
     *  arm-hint（streak≥3 + 窗龄≥2.2s + 静默≥0.8s）窗免退避——多守 0.8s
     *  让全画像（静默≥1.6s）有机会成立；全画像成立后既有 videoLike 豁免接管。 */
    public static final long VIDEO_ARM_SILENCE_MIN_MS = 800;
    /** v7.9.0 轮询针探针词（视频窗专用——图内「×/关闭/close」纯图标钮与
     *  拉丁关闭词旧探针 5 词探不到；命中即全量 tryScan 重走完整决策闸）。
     *  顺序即命中率序（跳过族最优先）。裸「X/x」不收：contains 搜索噪声面
     *  大（英文任意含 x 词全命中），交由全量闸处理。 */
    public static final String[] VIDEO_POLL_PROBE_WORDS = {
            "跳过", "跳過", "skip", "关闭广告", "關閉廣告",
            "关闭", "close", "×", "✕", "✖",
    };
    /** v7.9.0 轮询针全量复扫周期：每 3 针一次全量 tryScan（12~20s 段图片
     *  钮无文字零探针命中——图片 × 钮只能靠 walk 角落候选路径；全量复扫
     *  走完整决策闸，预算 600 与 nudge 路径同档）。 */
    public static final int VIDEO_POLL_WALK_EVERY = 3;

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

    /**
     * v7.9.0 画像预备（arm-hint）：零候选退避豁免用——streak 已攒满但静默还
     * 差半步（树慢速构建窗：内容事件持续到 ~2s，静默 0.8s 时退避正要杀链）。
     * arm-hint 成立的窗不退避（轮询针/固定档再守一轮），静默涨满 1.6s 后
     * videoLike 全画像成立接管观察；若树持续有事件（静默 <0.8s）则不豁免
     * ——退避照杀（功耗红线：arm-hint 只多守半步，不无限观察）。
     */
    public static boolean videoArmHint(long elapsedMs, int zeroCandStreak, long silenceMs) {
        if (zeroCandStreak < VIDEO_ZERO_CAND_STREAK) return false;
        if (elapsedMs < VIDEO_PROFILE_MIN_MS) return false;
        return silenceMs < 0 || silenceMs >= VIDEO_ARM_SILENCE_MIN_MS;
    }

    /** 观察窗效：videoLike → 20s（延长档）；普通开屏 → 12s（原 WINDOW_MS）。 */
    public static long windowDeadlineMs(boolean videoLike) {
        return videoLike ? VIDEO_EXT_WINDOW_MS : 12000;
    }

    /** 轮询针是否到期（距上针 ≥VIDEO_POLL_MS；无记录 = 立即到期）。 */
    public static boolean pollDue(Long lastPollAt, long now) {
        return lastPollAt == null || now - lastPollAt.longValue() >= VIDEO_POLL_MS;
    }

    /** v7.9.1 终态降频档：任务已终态（广告已关/预算用尽/作废）后轮询针
     *  3s/针继续观察但不出手（execute 任务门拒收终态点击）——「广告已关
     *  后轮询针全量复扫再出手」根治；新实例冷却过后恢复 1.5s 节奏。 */
    public static final long VIDEO_TERMINAL_POLL_MS = 3000;

    /** v7.9.1：轮询针到期（终态任务降频版——taskTerminal=true 时按 3s 间隔）。 */
    public static boolean pollDue(Long lastPollAt, long now, boolean taskTerminal) {
        long gap = taskTerminal ? VIDEO_TERMINAL_POLL_MS : VIDEO_POLL_MS;
        return lastPollAt == null || now - lastPollAt.longValue() >= gap;
    }

    /** 门控期白点节流：视频窗角落/手势击距上击 ≥VIDEO_STRIKE_GAP_MS 才放行。 */
    public static boolean strikeGapOk(Long lastStrikeAt, long now) {
        return lastStrikeAt == null || now - lastStrikeAt.longValue() >= VIDEO_STRIKE_GAP_MS;
    }

    /** v7.9.0 轮询针全量复扫到期（每 VIDEO_POLL_WALK_EVERY 针一次——图片钮
     *  无文字零探针命中，全量 walk 角落候选是 12~20s 段图片键唯一路径）。 */
    public static boolean pollWalkDue(int pollIdx) {
        return pollIdx > 0 && pollIdx % VIDEO_POLL_WALK_EVERY == 0;
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
