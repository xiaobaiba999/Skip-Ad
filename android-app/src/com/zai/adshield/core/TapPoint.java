package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.NodeView;

/**
 * v5.9.0 设备自适应触点引擎（用户实锤：用户设备不同——分辨率/挖孔/刘海/
 * 折叠内外屏/分屏/输入法/悬浮窗千差万别，对同一标签按「原始几何中心」派发
 * 手势会导致「多点」（同一坐标反复盲点无效）或「无点」（点在不可见/被裁剪/
 * 被覆盖区=误触他物——键盘上乱打字、点掉验证码横幅、点到另半屏应用））。
 *
 * ── 旧行为缺陷（三案）───────────────────────────────────────────
 *  ① 原始几何中心：clickNode 对 d.bounds 直接取 (l+r)/2,(t+b)/2——
 *    标签被父容器裁掉一半时，中心落在【不可见区】→ 手势点到旁边内容
 *    = 误触（真机案：720p 低端机开屏按钮被裁剪右半，点中「立即下载」）；
 *  ② 无窗口钳制：分屏/折叠自由窗口形态下，标签 bounds 超出应用自身窗口
 *    → 触点落到【另半屏的他应用】= 误触（真机案：折叠外屏宽标签溢出）；
 *  ③ 无覆盖层 Z 序判定：输入法/悬浮球/他应用 overlay 在 Z 序之上包含触点
 *    → 手势被截走 = 点到覆盖物（真机案：键盘弹出时点中键盘乱输入）。
 *
 * ── v5.9.0 四层防线（纯函数，JVM 可测，服务侧薄接线）──────────────
 *  L1 可见交集：TapPoint.visibleBounds——沿祖先链逐层求交（含 visible()），
 *     不可见祖先/被全裁 → null → 拒发手势（宁可漏拦绝不误触红线）；
 *  L2 窗口钳制：TapPoint.intersect(label, 节点自身窗口 bounds)——
 *     标签在窗外（分屏/折叠另半区/窗口已切换）→ null → 拒发手势；
 *  L3 安全选点：TapPoint.pick——可见矩形中心 + 复验象限轮换（同候选第
 *     N 次复验不再盲点同一坐标=「多点」根治）+ 边缘内缩（圆角/波纹/
 *     刘海死区）+ 过小拒点（可见缝 < MIN_VISIBLE_SIDE 盲刺拒绝）；
 *  L4 覆盖层判定：TapPoint.coveredByUpperWindow——Z 序高于广告窗的任意
 *     窗口（输入法/悬浮球/SystemUI 状态栏·横幅/他应用 overlay）包含触点
 *     → 手势必被截走 → 拒发，降级无障碍 performAction 代点（不经屏幕
 *     坐标，绝不碰覆盖物）。
 *
 * 全部纯函数：服务侧仅做 AccessibilityNodeInfo→NodeView / 窗口 bounds
 * 数组的搬运；TapPointMatrixTest 对 ~24 类设备几何逐一断言。
 * v6.0.0：pick 增设 pickDp 密度自适应重载——安全域常量从固定 px 迁到
 * dp（HumanTap.dpPx 按设备 density 缩放），160dpi 与 480dpi 行为归一
 * （「设备类型、分辨率原因」的触点安全域漂移根治；density=1.0 与
 * 旧 pick 完全一致=兼容基线）。
 */
public final class TapPoint {

    private TapPoint() {
    }

    /* ── 参数（设备几何安全域）── */

    /** 可见矩形最小边（px）：小于此值点击=盲刺（宁可拒点绝不误触） */
    public static final int MIN_VISIBLE_SIDE = 12;
    /** 触点距可见边内缩（px）：避开圆角/水波纹边缘/刘海安全区死区 */
    public static final int EDGE_INSET = 6;
    /** 祖先链遍历上限（防循环引用/超深树；64 层已覆盖最深的无障碍树） */
    public static final int MAX_ANCESTORS = 64;
    /** 复验轮换位置数：中心 + 四象限中心（同候选复验换点，不再盲点同坐标） */
    public static final int ROTATION_SLOTS = 5;

    /* ═════════ L1：可见交集（祖先裁剪 + 可见性）═════════ */

    /**
     * 节点的可见交集矩形：节点 bounds ∩ 逐层祖先 bounds；
     * 任一祖先（或节点自身）不可见 → null（拒点——陷阱/死区/被裁光）。
     * 祖先 bounds 拿不到（个别 ROM 对部分容器返回空）→ 该层跳过
     *（保守放行该层，下一层窗口钳制仍兜底）。
     */
    public static int[] visibleBounds(NodeView node) {
        if (node == null) return null;
        try {
            if (!node.visible()) return null;
            int[] b = node.bounds();
            if (b == null) return null;
            NodeView p = node.parent();
            int depth = 0;
            while (p != null && depth < MAX_ANCESTORS) {
                if (!p.visible()) return null;
                int[] pb = p.bounds();
                if (pb != null) {
                    b = intersect(b, pb);
                    if (b == null) return null; // 被某层祖先完全裁掉
                }
                p = p.parent();
                depth++;
            }
            return b;
        } catch (Throwable t) {
            return null; // 旧 ROM 反射异常：拒点比误点安全
        }
    }

    /* ═════════ L2：窗口钳制 ═════════ */

    /**
     * 手势目标矩形（服务侧入口）：labelBounds（标签控件自身 bounds，即
     * 决策携带的 d.bounds）经 L1 可见交集 + L2 节点窗口钳制。
     * node 为 null（gestureOnly 假跳过候选）→ 只做 L2。
     * 返回 null = 拒发手势（不可见/窗外/被全裁）。
     */
    public static int[] tapRect(NodeView node, int[] labelBounds, int[] windowBounds) {
        int[] rect = labelBounds;
        if (node != null) {
            int[] vis = visibleBounds(node);
            /* 标签 ∩ 可见祖先链交集：node 是标签自身时=可见部分；
             * node 是可点祖先时=标签必须在祖先可见区内（超出部分裁掉） */
            rect = intersect(rect, vis);
        }
        if (rect == null) return null;
        if (windowBounds != null) {
            rect = intersect(rect, windowBounds);
            if (rect == null) return null; // 标签在窗外（分屏/折叠另半区）
        }
        return rect;
    }

    /* ═════════ L3：安全选点 ═════════ */

    /**
     * 从（可见）矩形内选取安全触点，返回 {x, y}；null = 拒点。
     * <p>
     * 规则：
     * <ul>
     * <li>矩形任一边 &lt; MIN_VISIBLE_SIDE → null（盲刺拒绝——点了大概率
     * 落到旁边的未知物=误触，宁可走无障碍代点/等下轮换候选）；</li>
     * <li>attempt 0 = 可见矩形中心（默认正解：点用户看得见的地方）；</li>
     * <li>attempt 1..4 = 四象限中心轮换（同候选复验换点——设备死区/命中
     * 测试盲区不再重复盲点同一坐标，「多点无效果」根治）；</li>
     * <li>触点一律钳制到 [边+内缩, 边-内缩]（圆角/波纹/刘海死区防线；
     * 小矩形自动收窄内缩=四分之一边长，保证中心合法）；</li>
     * <li>超宽/超扁矩形（宽长横幅状标签）：象限轮换横向铺开，
     * 纵向始终居中——绝不贴上下边（水波纹/圆角死区高危带）。</li>
     * </ul>
     */
    public static int[] pick(int[] rect, int attempt) {
        return pickDp(rect, attempt, 1f);
    }

    /**
     * v6.0.0 密度自适应选点：与 {@link #pick(int[], int)} 同一算法，但
     * 安全域（可见缝最小边 / 边缘内缩）按设备 density 以 dp 缩放——
     * 低密度设备不再过严拒点（「无点」），高密度设备不再贴死区（误触后
     * 复验=「多点」）。density 经 HumanTap.clampDensity 钳制到 [1,4]。
     * density=1.0 时与旧 pick 逐字节一致（兼容基线）。
     */
    public static int[] pickDp(int[] rect, int attempt, float density) {
        if (rect == null || rect.length < 4) return null;
        int l = rect[0], t = rect[1], r = rect[2], b = rect[3];
        if (r <= l || b <= t) return null;
        int w = r - l, h = b - t;
        int minSide = HumanTap.minSidePx(density);
        if (w < minSide || h < minSide) return null; // 盲刺拒绝
        int cx = l + w / 2, cy = t + h / 2;
        /* 复验象限轮换：slot 1=左上象限中心, 2=右上, 3=左下, 4=右下
         *（象限中心=子矩形中心，非贴角点——任意 ≥2×MIN 的矩形上都安全）
         * v5.9 防死区形变：扁矩形（宽 ≥2×高）纵向恒居中（横幅状标签的
         * 上下边是水波纹/圆角死区高危带——纵向轮换只会把点推向死区）；
         * 竖窄矩形（高 ≥2×宽）横向恒居中（同理，左右边死区）。 */
        int slot = Math.abs(attempt) % ROTATION_SLOTS;
        if (slot != 0) {
            boolean flat = w >= 2 * h; // 扁矩形：纵向恒居中
            boolean tall = h >= 2 * w; // 竖窄矩形：横向恒居中
            int dx = (slot == 1 || slot == 3) ? -1 : 1;
            int dy = (slot == 1 || slot == 2) ? -1 : 1;
            if (!tall) cx += dx * (w / 4);
            if (!flat) cy += dy * (h / 4);
        }
        /* 边缘内缩钳制（小矩形内缩自动收窄到 w/4，中心恒合法）
         * v6.0.0：内缩基值按设备密度 dp 缩放（HumanTap.edgeInsetPx） */
        int edge = HumanTap.edgeInsetPx(density);
        int inX = Math.min(edge, w / 4);
        int inY = Math.min(edge, h / 4);
        cx = Math.max(l + inX, Math.min(r - inX, cx));
        cy = Math.max(t + inY, Math.min(b - inY, cy));
        if (cx < 0 || cy < 0) return null; // 负坐标防御（异常 bounds）
        return new int[]{cx, cy};
    }

    /* ═════════ L4：覆盖层 Z 序判定 ═════════ */

    /**
     * 触点是否被 Z 序更高（在广告窗之上）的窗口覆盖：覆盖则手势必被
     * 截走 = 点到覆盖物（输入法乱输入/点掉验证码横幅/触发他应用悬浮窗）。
     * upperBounds = 广告窗之上的所有窗口 bounds 数组（服务侧从 getWindows()
     * Z 序表提取：活跃窗之前的全部窗口）。任一包含触点 → true。
     */
    public static boolean coveredByUpperWindow(int x, int y, int[][] upperBounds) {
        if (upperBounds == null) return false;
        for (int[] wb : upperBounds) {
            if (wb == null || wb.length < 4) continue;
            if (x >= wb[0] && x <= wb[2] && y >= wb[1] && y <= wb[3]) return true;
        }
        return false;
    }

    /* ═════════ 基础几何 ═════════ */

    /** 两矩形交集；无交（或输入空）→ null。纯函数。 */
    public static int[] intersect(int[] a, int[] b) {
        if (a == null || b == null) return null;
        if (a.length < 4 || b.length < 4) return null;
        int l = Math.max(a[0], b[0]);
        int t = Math.max(a[1], b[1]);
        int r = Math.min(a[2], b[2]);
        int bo = Math.min(a[3], b[3]);
        if (r <= l || bo <= t) return null;
        return new int[]{l, t, r, bo};
    }

    /** 矩形中心（诊断/测试用；pick 的 slot 0 与此一致） */
    public static int[] centerOf(int[] rect) {
        if (rect == null || rect.length < 4 || rect[2] <= rect[0] || rect[3] <= rect[1]) return null;
        return new int[]{(rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2};
    }

    /** 矩形宽高（测试断言用） */
    public static int width(int[] rect) {
        return rect == null ? -1 : rect[2] - rect[0];
    }

    public static int height(int[] rect) {
        return rect == null ? -1 : rect[3] - rect[1];
    }
}
