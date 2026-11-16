package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;

/**
 * 内嵌规则引擎（v3.9.0 · 李跳跳实现方法论一比一对齐）
 * ─────────────────────────────────────────────────────────────
 * v3.9.0 设计动机（issue#11/#12 真机日志实锤三条毒链）：
 *  · 毒链①（issue#12 闪退元凶）：桌面（com.miui.home）被误判为开屏广告窗
 *    → 「坐标级点击兜底」盲点固定坐标 (1267,206) 等标准位 → 点中桌面图标拉起
 *    微信/QQ/抖音 → 「链式跳转回弹」以为广告跳转 → 狂按返回键把用户踢出
 *    —— QQ/微信/抖音「进去就被退出来」的完整灾难链。
 *  · 毒链②：坐标兜底点的是【固定百分比坐标】——每台手机屏幕大小不一样，
 *    位置肯定不一样，盲点必然乱点（用户明确要求：绝不固定坐标）。
 *  · 毒链③：弹窗内容突发检测对正常应用（美团/social/view/health）误报，
 *    大量「疑似覆盖层弹窗」误识别。
 *  对策（李跳跳方法论）：
 *  · 规则只认【控件属性匹配】（text/desc/viewId），点击的是控件自身
 *    bounds 中心——bounds 由应用按实际屏幕渲染，天然适配任意分辨率，
 *    绝无「固定屏幕坐标」；
 *  · 规则内嵌 APK（李跳跳内置订阅同款），零网络依赖零云端下发；
 *  · 事件驱动 0ms 出手：窗口/内容一变立即查规则，命中即点——「露头就秒跳」
 *    （Keep 级速度：Keep 开屏「跳过」文本键本引擎 0ms 首扫即中）；
 *  · 文案两级匹配防误点：精确等于（跳过/跳过广告/Skip…）立即命中；
 *    包含匹配要求文案 ≤6 字（「跳过 5」可中，「摇一摇跳过广告详情页」
 *    等诱导长文案绝不中）+ 面积 ≤35% 屏（CTA 巨型按钮绝不中）
 *    + visibleToUser（不可见=陷阱/死区）；
 *  · vid（viewIdResourceName）匹配零文本图片跳过键（穿山甲 tt_splash_skip_btn /
 *    微博 iv_skip 类），GKD 全局规则同款。
 *
 * 与决策核心（AdDecisionCore）的关系：本引擎是【主路径】（规则命中秒点），
 * 决策核心退居兜底（关闭键/角落键/SDK 窗直断/学习位置等复杂信号场景）。
 * 全部纯 Java 零 Android 依赖，JVM 可单测（RuleEngineTest）。
 */
public final class RuleEngine {

    private RuleEngine() {
    }

    /** 规则命中结果 */
    public static final class Hit {
        public String appId;      // 规则标识（日志/统计用）：global_exact / global_contains / global_vid
        public String label;      // 命中标签（倒计时门控解析用）
        public NodeView clickNode;// 可点祖先（performAction 兜底目标；null = 仅手势）
        public int[] tapBounds;   // 手势点击目标（【标签控件自身】bounds——适配任意分辨率）
        public boolean gestureOnly; // 仅手势（无可点祖先或祖先面积超限——performAction 会点开广告）
    }

    /* ── 全局规则（李跳跳内置订阅同款：所有应用的「跳过」系控件） ──
       精确匹配集：文案完全等于这些词才立即命中（最高置信，0ms 出手） */
    static final String[] TEXT_EXACT = {
            "跳过", "跳过广告", "关闭广告", "点击跳过", "上滑跳过", "跳过提示",
            "跳过此广告", "跳过广告 ", "skip", "Skip", "SKIP", "跳過", "跳過廣告"
    };
    /* 包含匹配集：短文案包含即命中（「跳过 5」「跳过5s」「3 跳过」类倒计时键）。
       CONTAINS_MAX_LEN 保证诱导长文案（摇一摇跳过/点击跳过领取福利等）绝不中 */
    static final String[] TEXT_SHORT = {"跳过", "跳過", "skip"};
    static final int CONTAINS_MAX_LEN = 6;

    /* ── 倒计时文案上限（解析出 1~15s 倒计时时由服务侧门控，提前点=白点） ── */

    /** 规则匹配主入口：遍历控件树，返回最优命中（null=无规则命中，不动作） */
    public static Hit match(NodeView root, Ctx ctx) {
        if (root == null) return null;
        int[] budget = {ctx.budgetNodes};
        return visit(root, 0, budget, ctx, null);
    }

    private static Hit visit(NodeView node, int depth, int[] budget, Ctx ctx, Hit best) {
        if (node == null || depth > 32 || budget[0] <= 0) return best;
        budget[0]--;
        try {
            String text = nz(node.text());
            String desc = nz(node.desc());
            String label = text.length() > 0 ? text : desc;
            boolean vis = node.visible();
            int[] b = node.bounds();
            double area = areaRatio(b, ctx);

            /* 文本/描述规则：可见 + 面积 ≤35% 屏（CTA 巨按钮绝不中） */
            if (vis && b != null && label.length() > 0 && area <= AdDecisionCore.SKIP_AREA_MAX) {
                int exact = exactIdx(label);
                boolean contains = exact == 0
                        && label.length() <= CONTAINS_MAX_LEN && containsShort(label);
                if (exact > 0 || contains) {
                    Hit h = new Hit();
                    h.appId = exact > 0 ? "global_exact" : "global_contains";
                    h.label = label;
                    h.tapBounds = b; // 点标签控件自身中心（非固定屏幕坐标）
                    NodeView click = AdDecisionCore.resolveClickable(node, 4);
                    h.clickNode = click;
                    // 祖先缺失或祖先是巨型容器（performAction=点开广告）→ 仅手势直点标签坐标
                    h.gestureOnly = click == null || !AdDecisionCore.isSmallClick(click, ctx);
                    if (exact > 0) return h;      // 精确命中：最高置信，立即返回
                    if (best == null) best = h;   // 包含命中：先记着，继续找更精确的
                }
            }
            /* vid 规则（零文本图片跳过键——李跳跳/GKD 同款强信号）：
               无文案、叶子节点、viewId 含 skip（或 count+down 非 download） */
            else if (vis && b != null && label.length() == 0 && node.childCount() == 0
                    && AdDecisionCore.isVidSkip(node) && area <= AdDecisionCore.SKIP_AREA_MAX) {
                Hit h = new Hit();
                h.appId = "global_vid";
                h.label = "vid:" + node.viewId();
                h.tapBounds = b;
                NodeView click = AdDecisionCore.resolveClickable(node, 4);
                h.clickNode = click;
                h.gestureOnly = click == null || !AdDecisionCore.isSmallClick(click, ctx);
                return h; // vid 强信号立即返回
            }

            int n = node.childCount();
            for (int i = 0; i < n; i++) {
                best = visit(node.child(i), depth + 1, budget, ctx, best);
                if (best != null && !"global_contains".equals(best.appId)) {
                    return best; // 已握精确/vid 命中，提前收工（速度优先）
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    /** 精确匹配命中下标（0=未命中） */
    static int exactIdx(String label) {
        for (int i = 0; i < TEXT_EXACT.length; i++) {
            if (TEXT_EXACT[i].equals(label)) return i + 1;
        }
        return 0;
    }

    /** 短文案包含匹配 */
    static boolean containsShort(String label) {
        String lower = label.toLowerCase();
        for (String k : TEXT_SHORT) {
            if (lower.contains(k.toLowerCase())) return true;
        }
        return false;
    }

    static double areaRatio(int[] b, Ctx ctx) {
        if (b == null || ctx.screenW <= 0 || ctx.screenH <= 0) return 0;
        double area = (double) (b[2] - b[0]) * (b[3] - b[1]);
        return area / ((double) ctx.screenW * ctx.screenH);
    }

    static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
