package com.zai.adshield.core;

import java.util.List;

/**
 * v6.0.0 1:1 人手模拟引擎（用户实锤：部分用户设备开屏广告跳过时因为
 * 设备类型、分辨率或者其他原因影响，导致多点、误点的情况，体验非常
 * 不好——1 比 1 模拟人在不同设备上操作使用卫士，解决这个 bug）。
 *
 * ── v5.9.0 之后仍然存在的四类残余缺陷（本轮根治）──────────────
 *  ① 机器人手势签名：dispatchGesture 的触点参数是「固定 40ms 起始延迟 +
 *    固定 110ms 时长 + 完美水平 1px 轨迹」的千次一模一样签名——反自动化
 *    广告 SDK 的触点指纹检测（时长零方差 / 轨迹零抖动）会把这种签名
 *    无声吞掉：点了没反应 → 复验 → 再点 → 「多点」级联实锤；
 *  ② 出手用旧坐标：扫描时刻捕获的 bounds 到手势真正落屏有 40~300ms
 *    延迟——广告按钮入场缩放/位移动画后，按「它曾在哪」点击=点空，
 *    人眼是按「它现在在哪」点的（1:1 人眼追踪缺位）；
 *  ③ 触点安全域用固定 px 常量：MIN_VISIBLE_SIDE=12px / EDGE_INSET=6px
 *    在 160dpi 设备上=12dp/6dp（过严——有效小按钮被拒点=「无点」），
 *    在 480dpi 设备上=4dp/2dp（过松——触点距圆角/波纹死区仅 2dp=
 *    触点落在死区=「无点」后复验=「多点」）。同一份代码在不同设备
 *    密度上行为漂移，就是「设备类型、分辨率原因」的根；
 *  ④ 复验节奏是机器枪：点击后 450ms/900ms 双针补扫 + 12 次点击预算，
 *    首点失败设备上最多 12 连点，用户肉眼看到的就是「多点」。
 *
 * ── v6.0.0 人手模型（全部纯函数，JVM 可测，服务侧薄接线）─────────
 *  A tapSpec：真人触点参数域——起始延迟 26~56ms、触点时长 95~160ms
 *    （重试时更用力：115~160ms）、±2px 微抖轨迹（远小于安全内缩域，
 *    绝不越出目标面）。每次出手都不同（确定性 PRNG 种子驱动，可复现）；
 *  B verifyDelayMs：人手复验节奏——首击后 500ms 扫一眼（原双针机器枪
 *    → 单针），第 2/3/4/5/6 击后 1.4s/2.4s/3.6s/5.2s/7.0s 渐进放缓
 *    （人会越等越慢，机器不会）；
 *  C dpPx 密度归一：安全域常量全部从 px 迁到 dp，按设备 density 缩放
 *    （160dpi 与 480dpi 上行为一致——「分辨率原因」根治）；
 *  D isDupPoint 触点轨迹去重：同窗已刺过的坐标 ±8dp 内绝不重复盲刺
 *    （换候选/换象限，而不是在同一坐标上连点——「多点」根治）；
 *  E MAX_CLICKS_PER_WIN 12→6：人的耐心就是 3~6 下，第 6 下后优雅收手
 *    等倒计时走完，绝不机器枪连点（AdDecisionCore 同步收紧）。
 */
public final class HumanTap {

    private HumanTap() {
    }

    /* ── A：人手触点参数域（tapSpec）── */

    /** 起始延迟域：26~56ms（真人抬指→落指的反应散布） */
    public static final int DELAY_MIN = 26;
    public static final int DELAY_SPAN = 31;
    /** 首击时长域：95~130ms（真人轻点） */
    public static final int DUR_FIRST_MIN = 95;
    public static final int DUR_FIRST_SPAN = 36;
    /** 重试时长域：115~160ms（人会点得更用力更久） */
    public static final int DUR_RETRY_MIN = 115;
    public static final int DUR_RETRY_SPAN = 46;
    /** 触点微抖幅度：±2px（远小于最小安全内缩 3px——绝不出目标面） */
    public static final int JITTER_ABS = 2;

    /**
     * 人手触点参数 {delayMs, durationMs, jx, jy}。
     * <p>纯函数：同种子同参数（测试可复现）；不同种子/attempt → 参数必然
     * 散布在人手域内（反指纹：时长方差/位置抖动与真人无异）。
     * attempt&lt;=0=首击（轻点域），attempt&gt;=1=重试（更用力域）。
     */
    public static int[] tapSpec(long seed, int attempt) {
        long r = mix(seed, attempt) & 0x7FFFFFFFFFFFFFFFL;
        int delay = DELAY_MIN + (int) (r % DELAY_SPAN);
        r = r / DELAY_SPAN + 0x2545F4914F6CDD1DL;
        r = mix(r, (int) (r & 0xF)) & 0x7FFFFFFFFFFFFFFFL;
        int dur;
        if (attempt <= 0) {
            dur = DUR_FIRST_MIN + (int) (r % DUR_FIRST_SPAN);
        } else {
            dur = DUR_RETRY_MIN + (int) (r % DUR_RETRY_SPAN);
        }
        r = mix(r, attempt * 31 + 7) & 0x7FFFFFFFFFFFFFFFL;
        int jx = (int) (r % (2 * JITTER_ABS + 1)) - JITTER_ABS;
        r /= 5;
        int jy = (int) (r % (2 * JITTER_ABS + 1)) - JITTER_ABS;
        return new int[]{delay, dur, jx, jy};
    }

    /** SplitMix64 混淆（确定性、跨 JVM 稳定、无 ThreadLocalRandom 依赖） */
    static long mix(long seed, int salt) {
        long s = seed ^ (0x9E3779B97F4A7C15L * (salt + 1L));
        s ^= s >>> 33;
        s *= 0xff51afd7ed558ccdL;
        s ^= s >>> 33;
        s *= 0xc4ceb9fe1a85ec53L;
        s ^= s >>> 33;
        return s;
    }

    /* ── B：人手复验节奏（verifyDelayMs）── */

    /**
     * 第 attempt 次出手（0 基）后的复验等待：首击 500ms 扫一眼；之后
     * 1.4s/2.4s/3.6s/5.2s/7.0s 渐进放缓——人会越等越慢。替代 v4.4.0
     * 的 450/900 双针机器枪（首击失败设备上双针=肉眼可见的「多点」）。
     */
    public static long verifyDelayMs(int attempt) {
        if (attempt <= 0) return 500L;
        long[] ladder = {1400L, 2400L, 3600L, 5200L, 7000L};
        int i = attempt - 1;
        if (i >= ladder.length) i = ladder.length - 1;
        return ladder[i];
    }

    /* ── C：dp 密度归一（设备分辨率/形态差异根治）── */

    /** 可见矩形最小边（dp）：12dp——低于此值人手也点不稳，盲刺拒绝 */
    public static final int MIN_SIDE_DP = 12;
    /** 触点距边内缩（dp）：6dp——圆角/水波纹/刘海死区安全带 */
    public static final int EDGE_INSET_DP = 6;
    /** 触点轨迹去重容差（dp）：8dp 内视为「同一坐标」绝不重复盲刺 */
    public static final int DUP_TOL_DP = 8;

    /** density 钳制域：[1.0, 4.0]（ldpi 老设备取 1.0 保守，xxxhdpi 取 4.0 封顶） */
    public static float clampDensity(float density) {
        if (density <= 0f || Float.isNaN(density)) return 1f;
        return density < 1f ? 1f : (density > 4f ? 4f : density);
    }

    /** dp → 设备 px（经 density 钳制；1.0 与 v5.9 旧行为完全一致=兼容基线） */
    public static int dpPx(float dp, float density) {
        return Math.round(dp * clampDensity(density));
    }

    /** tapRect/pick 的可见缝最小边（px，按设备密度） */
    public static int minSidePx(float density) {
        return dpPx(MIN_SIDE_DP, density);
    }

    /** tapRect/pick 的边缘内缩（px，按设备密度） */
    public static int edgeInsetPx(float density) {
        return dpPx(EDGE_INSET_DP, density);
    }

    /** 触点轨迹去重容差（px，按设备密度） */
    public static int dupTolPx(float density) {
        return dpPx(DUP_TOL_DP, density);
    }

    /* ── D：触点轨迹去重（isDupPoint）── */

    /**
     * 触点 (x,y) 是否落在已刺过的任一坐标 ±tolPx 邻域内（= 同一坐标
     * 盲刺）。attempted 为本窗已出手触点轨迹（服务侧维护，窗口切换清空）。
     * 命中 → true：换象限/换候选，绝不重复盲刺（「多点」根治）。
     */
    public static boolean isDupPoint(List<int[]> attempted, int x, int y, int tolPx) {
        if (attempted == null || attempted.isEmpty()) return false;
        for (int i = 0; i < attempted.size(); i++) {
            int[] a = attempted.get(i);
            if (a == null || a.length < 2) continue;
            if (Math.abs(a[0] - x) <= tolPx && Math.abs(a[1] - y) <= tolPx) return true;
        }
        return false;
    }

    /* ── E：人手耐心预算（服务侧 MAX_CLICKS_PER_WIN 同步收紧 12→6）── */

    /** 同窗出手预算（人的耐心）：6——与 AdDecisionCore.MAX_CLICKS_PER_WIN 同源 */
    public static final int HUMAN_PATIENCE = 6;

    /* ── v6.1.0 二轮根治（用户实锤：部分用户设备开屏广告跳过时因为设备类型、
     * 分辨率或其他原因影响，导致多点、误点的情况，体验非常不好）──
     *  v6.0.0 已做 A~E（人手参数散布/复验节奏放缓/耐心预算/dp 归一/轨迹去重），
     *  本轮针对残余「多点/误点」三条真实路径补三道纯函数防线：
     *  F sameTargetKey/isFiredRecently：目标指纹去抖——点击已派发后
     *    SAME_TARGET_QUIET_MS 内同指纹目标（包名+中心±容差+语义标签）
     *    绝不再出手。场景：点击已生效但广告 Activity 跳转有 300~800ms
     *    动画延迟，复验扫描看到「按钮还在」再点一次=用户肉眼的多点；
     *  G isStaleBounds：出手前新鲜度复核——人眼按「它现在在哪」点击；
     *    出手时用点击决策时刻捕获的旧 bounds（扫描→决策→派发有 26~56ms
     *    人手延迟 + 300ms 冷却链路延迟），入场缩放/位移动画中的按钮
     *    已位移 → 中心位移超过旧矩形短边 50%+24dp 判为陈旧坐标，
     *    放弃本次出手等下一轮扫描用新 bounds（点空→复验→再点的
     *    多点级联从源头掐断；位移中盲点旧坐标=误点）；
     *  H postTapQuietOk：结果静默期——手势成功派发后 POST_TAP_QUIET_MS
     *    内同包任何新候选不再出手（真人完成一次点击后手指离屏到下一次
     *    出手物理上不可能 0ms 连续；机器连发是反自动化 SDK 触点检测的
     *    直接特征，也是多点观感的来源之一）。 */

    /** v6.1.0 F：同指纹目标静默期（ms）——点击已派发后此窗内绝不重复出手 */
    public static final long SAME_TARGET_QUIET_MS = 1500;
    /** v6.1.0 G：陈旧 bounds 判定——中心位移 > max(旧矩形短边×50%, 24dp 对应 px) */
    public static final double STALE_DRIFT_RATIO = 0.5;
    public static final int STALE_DRIFT_MIN_DP = 24;
    /** v6.1.0 H：手势派发成功后的全局结果静默期（ms） */
    public static final long POST_TAP_QUIET_MS = 250;

    /**
     * v6.1.0 F：目标指纹（pkg + 中心坐标粗粒度 + 语义标签）。中心坐标粗粒度
     * = 容差网格化（同一按钮的复验扫描坐标偏差 < 网格尺寸 → 同指纹）。
     * <p>纯函数；测试侧固定输入可精确断言。
     *
     * @param tolPx 指纹网格尺寸（px，按设备密度；建议 = dupTolPx(density)）
     */
    public static String sameTargetKey(String pkg, int cx, int cy, String tag, int tolPx) {
        int gx = tolPx > 0 ? cx / tolPx : cx;
        int gy = tolPx > 0 ? cy / tolPx : cy;
        String t = tag == null ? "" : tag;
        return pkg + "|" + gx + "," + gy + "|" + t.length() + ":" + t.hashCode();
    }

    /**
     * v6.1.0 F：同指纹目标是否在静默期内（刚出手过 → true = 拒绝再次出手）。
     * firedAt：指纹→上次派发时刻（服务侧维护，窗口切换清空）。
     */
    public static boolean isFiredRecently(java.util.Map<String, Long> firedAt, String key, long now) {
        if (firedAt == null || key == null) return false;
        Long at = firedAt.get(key);
        return at != null && now - at < SAME_TARGET_QUIET_MS;
    }

    /**
     * v6.1.0 G：出手前新鲜度复核——旧 bounds（决策捕获时刻）中心 vs 新 bounds
     * （出手时刻 re-fetch）中心位移是否判为「陈旧坐标」。true = 按钮正在
     * 入场/位移动画中，放弃本次出手（等下一轮扫描用新 bounds）。
     * <p>位移阈值 = max(旧矩形短边 × 50%, 24dp×density)——小于此为正常
     * 抖动（按钮呼吸/微调不影响安全触点），大于此为动画位移（按旧坐标=点空）。
     *
     * @param oldRect 决策时刻捕获的 bounds [l,t,r,b]（null → 判陈旧：节点已消失）
     * @param newRect 出手时刻 re-fetch 的 bounds [l,t,r,b]（null → 判陈旧）
     */
    public static boolean isStaleBounds(int[] oldRect, int[] newRect, float density) {
        if (oldRect == null || newRect == null || oldRect.length < 4 || newRect.length < 4) return true;
        int oldW = oldRect[2] - oldRect[0], oldH = oldRect[3] - oldRect[1];
        if (oldW <= 0 || oldH <= 0) return true;
        int ocx = (oldRect[0] + oldRect[2]) / 2, ocy = (oldRect[1] + oldRect[3]) / 2;
        int ncx = (newRect[0] + newRect[2]) / 2, ncy = (newRect[1] + newRect[3]) / 2;
        int dx = Math.abs(ocx - ncx), dy = Math.abs(ocy - ncy);
        int shortSide = Math.min(oldW, oldH);
        int limit = Math.max((int) (shortSide * STALE_DRIFT_RATIO), dpPx(STALE_DRIFT_MIN_DP, density));
        return dx > limit || dy > limit;
    }

    /**
     * v6.1.0 H：结果静默期——手势派发成功后同包全局静默是否已过。
     * <p>lastGestureOkAt：该包上次手势派发成功时刻（服务侧维护）。
     */
    public static boolean postTapQuietOk(Long lastGestureOkAt, long now) {
        return lastGestureOkAt == null || now - lastGestureOkAt >= POST_TAP_QUIET_MS;
    }
}
