package com.zai.adshield.core;

/**
 * v7.7.0 回切开屏根治（用户实锤：从另一个 app 切回目标 app 期间弹的广告
 * 没有拦截到——扫描日志实锤）。两个死因一起根治：
 *
 *  · 死因①（entering 误判）：切到卫士再切回目标 App 时，卫士自家包的事件
 *    在 onAccessibilityEvent 顶部整体丢弃、lastFgPkg 从未变成卫士包名 →
 *    切回目标 App 时 pkg == lastFgPkg → entering=false → 不按「新进入」
 *    处理：rootWinCls 不刷新（入口窗还是老的）、回切开屏零日志。
 *
 *  · 死因②（CLEAN 档案误杀）：回切开屏常叠在主界面窗类上（同一
 *    MainActivity 盖一层开屏容器，窗类不变），而主界面窗类早被宿主窗
 *    档案判 CLEAN（连续 3 次零候选）→ bumpScanChain 只排探针哨兵/
 *    低频档 {0,700} → 晚到的跳过键（1~2s 才渲染）与图标式/WebView 式
 *    跳过键（原生文本探针探不到）整窗漏拦——这正是「回归：开屏广告
 *    有的跳不了、或非常非常慢」的底层病灶。
 *
 * 对策（纯函数，JVM 全真值表可测；服务侧薄接线）：
 *  · isEntering(pkg, lastFgPkg, lastWinAt, now)：包与 lastFgPkg 不同 =
 *    新进入（原语义保持）；相同但离开间隙 ≥ REENTER_GAP_MS = 回切，
 *    等价新进入——息屏回亮、白名单应用边界、卫士自家包边界（顶部
 *    登记 lastFgPkg）之外的一切漏网路径全兜住；
 *  · entryUntil(now) / entryFullActive(entryUntil, now)：进入期
 *    ENTRY_FULL_MS=4s 内 CLEAN 档案让路全量扫描——开屏/回切开屏最可能
 *    出现在进入头几秒，宁多扫绝不漏（用户十五轮指令：暂时放弃「宁可
 *    漏拦绝不误触」，激进优先；进入期是低频事件，功耗代价可忽略——
 *    典型日几十次进入 × 一次全量梯子）。
 *  · reentryAwayMs(lastWinAt, now)：回切离开时长（日志记账用）。
 */
public final class ReentryGuard {

    /** 离开该时长后再回到同一包 = 回切（等价新进入）。6s：低于典型开屏
     * 二次触发阈值（多数 App 后台 >5s 回前台才重弹开屏），高于误报面
     * （同 App 内 6s 内的窗级抖动绝不误判回切）。 */
    public static final long REENTER_GAP_MS = 6000L;

    /** 进入期全量门时长：进入（冷启/回切）后该时长内 CLEAN 档案让路。 */
    public static final long ENTRY_FULL_MS = 4000L;

    private ReentryGuard() {
    }

    /** 是否按「新进入」处理（新进入 ∪ 回切）。 */
    public static boolean isEntering(String pkg, String lastFgPkg, long lastWinAt, long now) {
        if (pkg == null || pkg.length() == 0) return false;
        if (lastFgPkg == null || lastFgPkg.length() == 0 || !pkg.equals(lastFgPkg)) return true;
        return lastWinAt <= 0L || now - lastWinAt >= REENTER_GAP_MS;
    }

    /** 进入期（全量扫描门）到期时刻。 */
    public static long entryUntil(long now) {
        return now + ENTRY_FULL_MS;
    }

    /** 进入期是否仍在（CLEAN 档案是否让路全量）。 */
    public static boolean entryFullActive(long entryUntil, long now) {
        return entryUntil > 0L && now < entryUntil;
    }

    /** 回切离开时长（仅日志记账；非回切场景返回 0）。 */
    public static long reentryAwayMs(String pkg, String lastFgPkg, long lastWinAt, long now) {
        if (pkg == null || lastFgPkg == null || !pkg.equals(lastFgPkg)) return 0L;
        if (lastWinAt <= 0L) return now; // 从未登记过窗时刻：按完整离开处理
        long away = now - lastWinAt;
        return away >= REENTER_GAP_MS ? away : 0L;
    }

    /* ═══ v7.12.0 前台竞速晋升（回切拦截修复·死因③）═══
     * 窗口事件与「活动窗口归属查询」的竞速：新前台应用的
     * TYPE_WINDOW_STATE_CHANGED 到达时活动窗口尚未切换完成（最近任务
     * 恢复/应用切换动画期）→ isActiveWindowOf=false → 旧版整事件丢弃且
     * 不更新 lastFgPkg → 该应用后续全部内容事件被前台门丢弃 → 回切后
     * 开屏广告整窗漏拦（用户实锤「切去别的应用十几分钟回来，广告拦截
     * 不了」）。服务侧在丢弃时登记竞速候选（fgPendingPkg）；后续内容
     * 事件经本判定 + 活动窗口复核确认后按新进入补扫。纯函数·JVM 可测。 */

    /** v7.12.0：竞速候选是否值得晋升（事件包一致 + 在窗口期内）。
     *  调用方还需自行复核 isActiveWindowOf(eventPkg)（服务侧 Android API，
     *  不可纯函数化）。窗口期 3s：竞速只发生在切换瞬间，过期即作废。 */
    public static boolean shouldPromotePending(String pendingPkg, long pendingAt,
                                                String eventPkg, long now, long windowMs) {
        if (pendingPkg == null || eventPkg == null || eventPkg.length() == 0) return false;
        if (pendingAt <= 0L || now - pendingAt > windowMs) return false; // 过期/未登记
        return pendingPkg.equals(eventPkg);
    }
}
