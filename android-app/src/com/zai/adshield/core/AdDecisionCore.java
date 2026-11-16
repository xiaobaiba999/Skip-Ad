package com.zai.adshield.core;

import java.util.ArrayList;

/**
 * 秒跳决策核心（v3.6.0 · 纯 Java · 零 Android 依赖 · JVM 可单测）
 * ─────────────────────────────────────────────────────────────
 * v3.6.0 设计动机（三次真机反馈：喜马拉雅二次进入被引擎连按两次返回键
 *  「模拟人操作退出」到桌面——v3.5.0 的返回键兑底在第一方开屏窗=退出应用）：
 *  · 返回键只在「返回后仍在应用内」的窗口上安全：SDK 广告窗（独立广告
 *    Activity，下面是应用主页）与应用内窗（主页之上拉起的广告页）。
 *    应用入口窗（rootWindow：冷启动开屏/从后台回前台首窗）上按返回
 *    = 退出应用（喜马拉雅拦截返回弹「再按一次退出」，第二次直接退出）。
 *  · 对策：根窗口禁一切返回键兑底（L3/L4.5/L5），只走点击梯子（文本→
 *    学习位置→×关闭键→角落候选→三位置坐标轮换），用尽优雅收手。
 *
 * v3.5.0 设计动机（二次真机反馈：日志显示「已点击跳过」但广告纹丝不动，
 *  手动点两次也没学下来——微博/喜马拉雅还是得手动关）：
 *  · v3.4.0 点击后立即熔断扫描链，从不复验：performAction 返回 true 但被
 *    应用拦截、手势点偏、按钮尚未可点——点了等于白点，且无第二次机会。
 *  · 手动点击学习只认「跳过/关闭」文本：图片跳过键零文本 → 永远学不到。
 *  · v3.5.0 对策（本类决策阶梯，全部可单测）：
 *    L1   文本跳过键（含 contentDescription）→ 代点（performAction 失效改手势）
 *    L1.5 【新】学习位置点击：手动点过的位置指纹（归一化坐标+窗口类名），
 *         次启同窗 500ms 直点——图片跳过键「教一次就会」
 *    L1.6 【新】开屏×关闭键：顶部小关闭键 + 广告形态树 → 代点
 *         （v3.4.0 发现了关闭键却在开屏窗内从不点它）
 *    L2   SDK/学习 className 窗口指纹 → 返回键直断（500ms / 学习指纹 300ms）
 *    L3 无倒计时翻转/摇一摇文案 → 120ms 立即直断；带倒计时(假跳过) → 800ms 直断
 *    L4   角落无字小可点控件（多候选）：第 N 次复验换第 N 个候选点，
 *         而不是同一个错位置点三遍；v3.6.0 改纯手势点击子控件坐标
 *         （performAction 可能落到大热区祖先=点开广告本体）
 *    L4.5 点击复验失败升级：同一广告窗内点击 ≥3 次仍未关闭 →
 *         返回键兑底【仅应用内窗】（v3.6.0 根窗口豁免，见下）
 *    L5 全屏热区（自绘图片广告三条件）→ 900ms 返回键直断（守护名单豁免）
 *    L6 开屏窗坐标级点击兜底：简单树(无滚动/无输入框/节点少)且无任何
 *        信号 → dispatchGesture 点击标准跳过位，v3.6.0 三位置轮换：
 *         右上(88%,7%) → 右下(90%,92%) → 左上(8%,7%)，第 N 次点第 N 个，
 *         守护名单豁免（防误点；长文本豁免）
 *    P  弹窗窗期：角标强信号 / Dialog+≥2营销话术弱信号 → 代点关闭
 *  · v3.6.0 根窗口返回键安全门（三次真机反馈：喜马拉雅二次进入被
 *    「模拟人操作退出」——引擎在第一方开屏窗用返回键兑底，喜马拉雅拦截
 *    返回键弹「再按一次退出到桌面」，引擎再按 → 真退到桌面）：
 *    rootWindow=应用入口窗（从前台其他应用/桌面切来的首个窗口）时，
 *    返回键=退出应用，L3/L4.5/L5 全部禁用；仅 SDK 广告窗（独立广告
 *    Activity，返回回到应用内页）豁免。根窗口只走点击梯子，用尽即收手。
 *  · 决策输出与执行分离：本类只产出 Decision（动作+坐标+原因），
 *    服务侧负责 performAction / dispatchGesture / GLOBAL_ACTION_BACK，
 *    以及点击后的复验调度（扫描链不熔断，窗未变即换候选/真实触点重试）。
 */
public final class AdDecisionCore {

    private AdDecisionCore() {
    }

    /* ── 节点抽象（服务侧用 AccessibilityNodeInfo 适配；测试用 FakeNode） ── */
    public interface NodeView {
        String text();

        String desc();

        String cls();

        boolean clickable();

        boolean scrollable();

        boolean editable(); // EditText 类

        int[] bounds(); // {left, top, right, bottom}，未知返回 null

        int childCount();

        NodeView child(int i);

        NodeView parent(); // 可空
    }

    /* ── 动作常量 ── */
    public static final String A_CLICK_BOUNDS = "click_bounds"; // 点击控件（代点/手势）
    public static final String A_TAP_POINT = "tap_point";       // 坐标级点击（手势兜底）
    public static final String A_BACK = "back";                 // 返回键直断
    public static final String A_CLOSE = "close";               // 弹窗关闭键
    public static final String A_NONE = "none";

    /* ── 时序参数（ms） ── */
    public static final long CLS_BREAK_MS = 500;          // SDK 窗直断时限
    public static final long CLS_LEARNED_BREAK_MS = 300;  // 学习指纹快断
    public static final long GYRO_IMMEDIATE_MS = 120;     // 无倒计时立即直断
    public static final long GYRO_BREAK_MS = 800;         // 带倒计时(假跳过)直断
    public static final long LEARNED_POS_MS = 500;        // 学习位置点击出手（v3.5.0）
    public static final long SPLASH_CLOSE_MS = 600;       // 开屏×关闭键出手（v3.5.0）
    public static final long CORNER_MIN_MS = 500;         // 角落控件最早出手（v3.6.0 600→500）
    public static final long CORNER_MAX_MS = 6500;        // 角落控件最晚（v3.6.0 3500→6500：部分开屏 3-5s 才出跳过键）
    public static final long HOT_ZONE_MS = 900;           // 热区直断
    public static final long COORD_TAP_MS = 900;          // 坐标兜底最早出手
    public static final long COORD_TAP_MAX_MS = 8000;     // 坐标兜底最晚（v3.6.0 4000→8000：覆盖晚出跳过键）

    /* ── v3.5.0 复验与候选参数 ── */
    public static final int MAX_CORNERS = 4;              // 角落候选上限（逐个换着点）
    public static final int CLICK_VERIFY_MAX = 3;         // 同窗点击次数达到即升级返回键（仅应用内窗，v3.6.0）
    public static final int MAX_CLICKS_PER_WIN = 6;       // 同窗点击总预算（用尽优雅收手，服务侧执行）
    public static final double CLOSE_TOP_MAX = 0.35;      // 开屏×关闭键须在顶部 35% 内
    public static final int LONG_TEXT_LEN = 40;           // 长文本（正常内容页特征）

    /* ── 阈值参数 ── */
    public static final double POS_LEARN_AREA_MAX = 0.25; // 位置学习：控件面积上限（v3.6.0 0.20→0.25：手动教学点击事件源可能是稍大的容器）
    public static final double HOT_ZONE_AREA = 0.55;   // 热区：≥55% 屏幕面积
    public static final int HOT_ZONE_MAX_NODES = 12;
    public static final double SKIP_AREA_MAX = 0.35;   // 真跳过键面积上限
    public static final double SKIP_WIDTH_MAX = 0.55;
    public static final double CORNER_TOP_MAX = 0.14;  // 角落：顶部 14% 高内
    public static final double CORNER_RIGHT_MIN = 0.72; // 右缘 ≥72% 宽
    public static final double CORNER_LEFT_MAX = 0.28;  // 或左缘 ≤28% 宽
    public static final int CORNER_MIN_PX = 24;        // 角落控件最小像素
    public static final int AD_LIKE_MAX_NODES = 40;    // 简单树节点上限
    /* v3.6.0 坐标轮换三标准位：右上（最常见）→ 右下（部分应用）→ 左上（少数）；
       第 N 次兜底点第 N 个，三个都点不中即收手（绝不返回键退出应用） */
    public static final double[] COORD_XS = {0.88, 0.90, 0.08};
    public static final double[] COORD_YS = {0.07, 0.92, 0.07};

    /* ── 文案特征库（与 2026-11 市场调研同步） ── */
    public static final String[] SKIP_TEXTS = {"跳过", "关闭广告"};
    public static final String[] GYRO_HINTS = {"翻转", "摇一摇", "摇晃", "晃动", "摇动", "扭一扭", "转动手机", "倾斜手机", "拿起手机"};
    public static final String[] CLOSE_TEXTS = {"×", "x", "X", "✕", "✖", "╳", "关闭", "close", "不感兴趣", "我知道了", "知道了", "关闭广告", "跳过", "忽略", "暂不"};
    public static final String[] BADGE_TEXTS = {"广告", "推广", "赞助", "广告推广", "sponsored", "promoted", "sponsor"};
    public static final String[] CTA_TEXTS = {"立即领取", "点击领取", "领取红包", "限时", "秒杀", "恭喜", "马上抢", "立即下载", "立即抢购", "0元领", "0元", "免费领", "点击进入", "立即查看", "开通会员", "领取礼包", "大奖"};
    public static final String[] GUARD_PKG_HINTS = {"bank", "icbc", "cmb", "ccb", "psbc", "abchina", "boc", "unionpay", "gov", "alipay", "955", "12345"};
    public static final String[] AD_TARGET_HINTS = {
            "com.jingdong", "com.taobao", "com.tmall", "com.xunmeng", "com.achievo.vipshop",
            "com.suning.juniper", "com.dangdang", "com.eastmoney", "com.amazon.mShop",
            "com.android.vending", "com.xiaomi.market", "com.huawei.appmarket",
            "com.heytap.market", "com.bbk.appstore", "com.tencent.android.qqdownloader",
            "com.ss.android.ugc", "com.smile.gifmaker", "com.kuaishou", "tv.danmaku.bili",
            "com.zhihu.android", "com.xingin.xhs", "com.dragon.read",
            "com.tencent.mm", "com.tencent.mobileqq", "com.eg.android.AlipayGphone",
            "com.sankuai.meituan", "com.dianping.v1", "com.sdu.didi.psnger",
            "com.android.browser", "com.android.chrome", "com.sec.android.app.sbrowser",
            "com.UCMobile", "com.quark.browser", "com.tencent.mtt", "org.mozilla.firefox",
            "com.microsoft.emmx", "com.heytap.browser", "com.vivo.browser", "com.mi.globalbrowser"
    };

    /* ───────── 扫描结果 ───────── */

    /** 角落候选（v3.5.0 多候选：点击复验失败换下一个候选点） */
    public static final class CornerCand {
        public NodeView clickNode;  // performAction 目标（可点祖先或自身）
        public int[] tapBounds;     // 手势点击边界（原小控件，点得更准）
    }

    public static final class ScanFind {
        public boolean foundSkip;      // 真「跳过」文本控件（小面积可点）
        public NodeView skipNode;
        public int[] skipBounds;
        public String skipLabel;
        public boolean fakeSkip;       // 假跳过热区（「跳过」字样但大面积/不可点）
        public boolean gyroHint;       // 翻转/摇一摇诱导文案
        public boolean adBadge;        // 广告角标
        public String badgeLabel;
        public boolean foundClose;     // 弹窗/开屏关闭键（×/关闭/暂不…）
        public NodeView closeNode;
        public int[] closeBounds;
        public String closeLabel;
        public int ctaCount;           // 营销话术命中数
        public int nodeCount;
        public boolean editPresent;    // 存在输入框（正常业务界面）
        public boolean scrollPresent;  // 存在可滚动容器（信息流/主页）
        public boolean hotZone;        // 全屏大热区
        public boolean longText;       // 存在长文本（>40 字，正常内容页特征，v3.5.0）
        public final java.util.ArrayList<CornerCand> corners = new java.util.ArrayList<CornerCand>(); // 角落候选（v3.5.0 多候选）
        public boolean dialogForm;     // 根节点 Dialog/Popup 形态
    }

    /* ───────── 决策上下文（服务侧填充） ───────── */

    public static final class Ctx {
        public int screenW;
        public int screenH;
        public long elapsedMs;        // 距窗口切换
        public boolean splashLive;    // 开屏窗内（≤12s）
        public boolean popupLive;     // 弹窗窗期内
        public boolean guard;         // 守护名单（银行/政务/支付）
        public boolean sdkAdWin;      // 窗口类名命中 SDK 指纹
        public boolean learnedCls;    // className 学习指纹命中
        public boolean rootWindow;    // v3.6.0：应用入口窗（冷启动开屏/回前台首窗）——按返回键=退出应用
        public int clickAttempts;     // 本窗已点击次数（v3.5.0 复验：≥1 强制手势真实触点）
        public int tapHintX = -1;     // 学习位置点击（v3.5.0：手动教学指纹，-1 无）
        public int tapHintY = -1;
        public int budgetNodes = 600; // 扫描节点预算
    }

    /* ───────── 决策输出 ───────── */

    public static final class Decision {
        public String action = A_NONE;
        public NodeView node;   // click/close 的目标控件
        public int[] bounds;    // click_bounds 目标边界
        public int x;           // tap_point 坐标
        public int y;
        public String reason = "";

        public static Decision none() {
            return new Decision();
        }
    }

    /* ═════════ 控件树遍历（多信号一次收集） ═════════ */

    public static ScanFind walk(NodeView root, Ctx ctx) {
        ScanFind f = new ScanFind();
        if (root == null) return f;
        String rootCls = nz(root.cls()).toLowerCase();
        f.dialogForm = rootCls.contains("dialog") || rootCls.contains("popup");
        int[] budget = {ctx.budgetNodes};
        visit(root, 0, budget, f, ctx);
        return f;
    }

    private static void visit(NodeView node, int depth, int[] budget, ScanFind f, Ctx ctx) {
        if (node == null || depth > 32 || budget[0] <= 0) return;
        budget[0]--;
        f.nodeCount++;
        try {
            String text = nz(node.text());
            String desc = nz(node.desc());
            String label = text.length() > 0 ? text : desc;
            String cls = nz(node.cls()).toLowerCase();
            if (label.length() > LONG_TEXT_LEN) f.longText = true; // 长文本=正常内容页（v3.5.0）
            if (node.editable() || cls.contains("edittext")) f.editPresent = true;
            if (node.scrollable() || cls.contains("scrollview") || cls.contains("recyclerview")
                    || cls.contains("viewpager") || cls.contains("listview")) f.scrollPresent = true;

            int[] b = node.bounds();
            boolean clickable = node.clickable();
            double areaRatio = areaRatio(b, ctx);

            // 全屏热区：可点且 ≥55% 屏幕
            if (!f.hotZone && clickable && areaRatio >= HOT_ZONE_AREA) f.hotZone = true;

            if (label.length() > 0) {
                if (label.length() <= 14 && matchesSkip(label)) {
                    NodeView click = resolveClickable(node, 4);
                    if (click != null && isSmallClick(click, ctx)) {
                        if (!f.foundSkip) {
                            f.foundSkip = true;
                            f.skipNode = click;
                            f.skipBounds = click.bounds();
                            f.skipLabel = label;
                        }
                    } else {
                        f.fakeSkip = true; // 大面积/不可点 = 图内假跳过陷阱
                    }
                } else if (label.length() <= 22 && matchesAny(label, GYRO_HINTS)) {
                    f.gyroHint = true;
                } else if (label.length() <= 6 && isCloseText(label)) {
                    NodeView click = resolveClickable(node, 4);
                    if (click != null && isSmallClick(click, ctx) && !f.foundClose) {
                        f.foundClose = true;
                        f.closeNode = click;
                        f.closeBounds = click.bounds();
                        f.closeLabel = label;
                    }
                } else if (isAdBadge(label)) {
                    f.adBadge = true;
                    f.badgeLabel = label;
                } else if (label.length() <= 14 && matchesAny(label, CTA_TEXTS)) {
                    f.ctaCount++;
                }
            } else if (b != null && f.corners.size() < MAX_CORNERS && isCornerControl(b, ctx)
                    && !hasCornerChild(node, ctx)) {
                // 【v3.5.0】角落无字小可点控件（多候选）：疑似图片跳过键
                // （微博/喜马拉雅第一方开屏）；自身不可点时向上找可点祖先；
                // 若还有更小的角落子控件则让位（子控件坐标点得更准）
                NodeView click = clickable ? node : resolveClickable(node, 4);
                if (click != null) {
                    CornerCand c = new CornerCand();
                    c.clickNode = click;
                    c.tapBounds = b;
                    f.corners.add(c);
                }
            }

            int n = node.childCount();
            for (int i = 0; i < n; i++) {
                visit(node.child(i), depth + 1, budget, f, ctx);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 顶部区域判定（开屏×关闭键防误点：真广告×在顶部，隐私/业务弹窗按钮在底部） */
    static boolean inTopArea(int[] b, Ctx ctx) {
        if (b == null || ctx.screenH <= 0) return false;
        int cy = (b[1] + b[3]) / 2;
        return cy <= ctx.screenH * CLOSE_TOP_MAX;
    }

    /** 是否存在更小的角落子控件（有则父容器让位：子控件坐标点得更准） */
    static boolean hasCornerChild(NodeView node, Ctx ctx) {
        int n = node.childCount();
        for (int i = 0; i < n; i++) {
            NodeView c = node.child(i);
            if (c == null) continue;
            if (nz(c.text()).length() > 0 || nz(c.desc()).length() > 0) continue;
            int[] cb = c.bounds();
            if (cb != null && isCornerControl(cb, ctx)) return true;
        }
        return false;
    }

    /** 角落控件判定：顶部 14% 高内、贴右缘(≥72%)或左缘(≤28%)、边长 24px~35% 宽、无任何文本 */
    static boolean isCornerControl(int[] b, Ctx ctx) {
        if (b == null || ctx.screenW <= 0 || ctx.screenH <= 0) return false;
        int w = b[2] - b[0];
        int h = b[3] - b[1];
        if (w < CORNER_MIN_PX || h < CORNER_MIN_PX) return false;
        if (w > ctx.screenW * 0.35 || h > ctx.screenH * 0.20) return false;
        if (b[1] > ctx.screenH * CORNER_TOP_MAX) return false;
        boolean rightEdge = b[2] >= ctx.screenW * CORNER_RIGHT_MIN;
        boolean leftEdge = b[0] <= ctx.screenW * CORNER_LEFT_MAX;
        return rightEdge || leftEdge;
    }

    /** 向上找可点击祖先（最多 4 层） */
    static NodeView resolveClickable(NodeView node, int maxUp) {
        NodeView cur = node;
        for (int i = 0; i < maxUp && cur != null; i++) {
            if (cur.clickable()) return cur;
            cur = cur.parent();
        }
        return null;
    }

    static boolean isSmallClick(NodeView n, Ctx ctx) {
        if (n == null || !n.clickable()) return false;
        int[] b = n.bounds();
        if (ctx.screenW <= 0 || b == null) return true; // 无屏幕信息时放宽
        double ratio = areaRatio(b, ctx);
        int w = b[2] - b[0];
        return ratio < SKIP_AREA_MAX && w < ctx.screenW * SKIP_WIDTH_MAX;
    }

    static double areaRatio(int[] b, Ctx ctx) {
        if (b == null || ctx.screenW <= 0 || ctx.screenH <= 0) return 0;
        double area = (double) (b[2] - b[0]) * (b[3] - b[1]);
        return area / ((double) ctx.screenW * ctx.screenH);
    }

    /* ═════════ 服务侧学习辅助（v3.5.0 位置学习 / v3.6.0 放宽） ═════════ */

    /** 控件面积占比（服务侧手动点击位置学习用） */
    public static double areaOf(int[] b, int screenW, int screenH) {
        Ctx c = new Ctx();
        c.screenW = screenW;
        c.screenH = screenH;
        return areaRatio(b, c);
    }

    /** 是否角落形态控件（服务侧手动点击位置学习用） */
    public static boolean cornerLike(int[] b, int screenW, int screenH) {
        Ctx c = new Ctx();
        c.screenW = screenW;
        c.screenH = screenH;
        return isCornerControl(b, c);
    }

    /**
     * v3.6.0 学习形状判定（比扫描用的 isCornerControl 更宽）：
     * 手动点击教学时，跳过键可能是——
     *  · 顶部 25% 区域内的小控件（最常见：右上「跳过」图片键）；
     *  · 底部 20% 区域内的小控件（部分应用跳过键在右下角，真机反馈）；
     *  · 标准角落形态（顶部 14% + 贴左/右缘）。
     * 面积上限由调用方按 POS_LEARN_AREA_MAX 校验（大热区=CTA，不学）。
     */
    public static boolean learnableSkipShape(int[] b, int screenW, int screenH) {
        if (b == null || screenW <= 0 || screenH <= 0) return false;
        if (cornerLike(b, screenW, screenH)) return true;
        int cy = (b[1] + b[3]) / 2;
        boolean topArea = cy <= screenH * 0.25;
        boolean bottomArea = cy >= screenH * 0.80;
        boolean small = (b[2] - b[0]) <= screenW * 0.5 && (b[3] - b[1]) <= screenH * 0.15;
        return (topArea || bottomArea) && small;
    }

    /* ═════════ 决策阶梯 ═════════ */

    public static Decision decide(ScanFind f, Ctx ctx) {
        /* ── 开屏/插屏窗内 ── */
        if (ctx.splashLive) {
            // L1 文本跳过键（文本或 contentDescription）→ 代点
            if (f.foundSkip && f.skipBounds != null) {
                return click(f.skipNode, f.skipBounds, "skip_text");
            }
            // L1.5 学习位置点击（v3.5.0）：手动点过的位置指纹 → 次启同窗 500ms 直点
            //     安全门：仅在广告形态窗（简单树/SDK 窗/学习窗）内出手，
            //     信息流等正常界面（可滚动/有输入框/节点多）绝不点
            if (ctx.tapHintX >= 0 && ctx.tapHintY >= 0 && ctx.clickAttempts == 0
                    && ctx.elapsedMs >= LEARNED_POS_MS
                    && (isAdLike(f) || ctx.sdkAdWin || ctx.learnedCls)) {
                Decision d = new Decision();
                d.action = A_TAP_POINT;
                d.x = ctx.tapHintX;
                d.y = ctx.tapHintY;
                d.reason = "learned_pos";
                return d;
            }
            // L1.6 开屏×关闭键（v3.5.0）：顶部小关闭键 + 广告形态树（非长文本内容页）
            if (f.foundClose && f.closeBounds != null && !f.longText
                    && (isAdLike(f) || ctx.sdkAdWin || ctx.learnedCls)
                    && inTopArea(f.closeBounds, ctx)
                    && ctx.elapsedMs >= SPLASH_CLOSE_MS) {
                return click(f.closeNode, f.closeBounds, "splash_close");
            }
            // L2 SDK / 学习 className 窗口指纹 → 返回键直断
            //    （SDK 广告窗是独立 Activity，返回回到应用内页——根窗口也安全）
            long clsBreak = ctx.learnedCls ? CLS_LEARNED_BREAK_MS : CLS_BREAK_MS;
            if ((ctx.sdkAdWin || ctx.learnedCls) && ctx.elapsedMs >= clsBreak) {
                return back(ctx.learnedCls ? "sdk_window_learned" : "sdk_window");
            }
            /* v3.6.0 根窗口返回键安全门 ─────────────────────────────
             * 应用入口窗（冷启动开屏/回前台首窗）上按返回键 = 退出应用。
             * 真机事故（喜马拉雅）：v3.5.0 返回键兑底 → 喜马拉雅拦截返回
             * 弹「再按一次退出到桌面」→ 引擎 600ms 后再按 → 真退到桌面，
             * 用户「第二次进入就被模拟人操作退出来」。
             * 此后 L3/L4.5/L5 的返回键决策仅在应用内窗（返回回到应用内）
             * 或 SDK 广告窗上生效；根窗口只走点击梯子，用尽优雅收手。 */
            boolean backSafe = !ctx.rootWindow || ctx.sdkAdWin;
            // L3 陀螺仪/假跳过：无倒计时立即直断；带倒计时(假跳过) 800ms 直断
            if (backSafe && f.gyroHint && !f.fakeSkip && ctx.elapsedMs >= GYRO_IMMEDIATE_MS) {
                return back("gyro_immediate");
            }
            if (backSafe && (f.gyroHint || f.fakeSkip) && ctx.elapsedMs >= GYRO_BREAK_MS) {
                return back("gyro_break");
            }
            // L4 角落无字小可点控件（图片跳过键，多候选）→ 手势点击其中心
            //     v3.5.0：复验失败换下一个候选（第 N 次点击用第 N 个候选点）
            //     v3.6.0：改纯手势（node=null）——performAction 可能落到大热区
            //     祖先（=点开广告本体）；手势点子控件坐标=真实手指点跳过键
            if (!f.corners.isEmpty() && ctx.clickAttempts < f.corners.size()
                    && !f.scrollPresent && !f.editPresent
                    && f.nodeCount <= AD_LIKE_MAX_NODES
                    && ctx.elapsedMs >= CORNER_MIN_MS && ctx.elapsedMs <= CORNER_MAX_MS) {
                CornerCand c = f.corners.get(ctx.clickAttempts);
                return click(null, c.tapBounds, "corner_image_skip");
            }
            // L4.5 点击复验失败升级（v3.5.0）：同窗点击 ≥3 次仍未关闭 → 返回键兑底
            //     v3.6.0：仅应用内窗（返回回到应用内）；根窗口绝不返回键（防退出）
            if (ctx.clickAttempts >= CLICK_VERIFY_MAX && backSafe && isAdLike(f)
                    && ctx.elapsedMs >= GYRO_BREAK_MS) {
                return back("click_verify_fail");
            }
            // L5 全屏热区（自绘图片广告）→ 返回键直断（守护名单豁免；v3.6.0 根窗口豁免）
            if (f.hotZone && backSafe && !ctx.guard && !f.editPresent && f.nodeCount <= HOT_ZONE_MAX_NODES
                    && ctx.elapsedMs >= HOT_ZONE_MS) {
                return back("hot_zone");
            }
            // L6 坐标级点击兜底：开屏窗内简单树且无信号 → 点标准跳过位
            //    v3.6.0 三位置轮换：右上(88%,7%) → 右下(90%,92%) → 左上(8%,7%)，
            //    第 N 次兜底点第 N 个（角落候选用尽后接续计数），三个用尽即收手
            int coordIdx = f.corners.isEmpty() ? ctx.clickAttempts
                    : ctx.clickAttempts - f.corners.size();
            if (coordIdx >= 0 && coordIdx < COORD_XS.length && isAdLike(f) && !f.longText
                    && !ctx.guard && ctx.elapsedMs >= COORD_TAP_MS
                    && ctx.elapsedMs <= COORD_TAP_MAX_MS) {
                Decision d = new Decision();
                d.action = A_TAP_POINT;
                d.x = (int) (ctx.screenW * COORD_XS[coordIdx]);
                d.y = (int) (ctx.screenH * COORD_YS[coordIdx]);
                d.reason = "coord_fallback";
                return d;
            }
        }

        /* ── 弹窗窗期（独立于开屏窗） ── */
        if (ctx.popupLive && f.foundClose && f.closeBounds != null) {
            boolean strong = f.adBadge;                    // 角标强信号
            boolean weak = f.dialogForm && f.ctaCount >= 2; // Dialog + ≥2 营销话术
            if (strong || weak) {
                Decision d = click(f.closeNode, f.closeBounds, strong ? "popup_badge" : "popup_cta");
                d.action = A_CLOSE;
                return d;
            }
        }

        return Decision.none();
    }

    /** 简单树判定（广告页特征：无滚动、无输入框，且(大热区 或 节点少)） */
    static boolean isAdLike(ScanFind f) {
        if (f.scrollPresent || f.editPresent) return false;
        return f.hotZone || f.nodeCount <= AD_LIKE_MAX_NODES;
    }

    private static Decision click(NodeView node, int[] bounds, String reason) {
        Decision d = new Decision();
        d.action = A_CLICK_BOUNDS;
        d.node = node;
        d.bounds = bounds;
        d.reason = reason;
        return d;
    }

    private static Decision back(String reason) {
        Decision d = new Decision();
        d.action = A_BACK;
        d.reason = reason;
        return d;
    }

    /* ═════════ SDK 广告窗指纹（className 分段精确匹配） ═════════ */

    public static boolean isAdActivityClass(String cls) {
        if (cls == null || cls.length() < 6) return false;
        String c = cls.toLowerCase();
        if (c.contains("qq.e.ads") || c.contains("gms.ads") || c.contains("bytedance.sdk")
                || c.contains(".ad.activity") || c.contains(".ads.activity")
                || c.contains("baidu.mobads") || c.contains("csjad") || c.contains("unionad")) return true;
        String[] seg = c.split("[.$]");
        for (String s : seg) {
            if (s.length() < 3) continue;
            if (s.equals("adactivity") || s.equals("advertactivity")) return true;
            if (s.contains("openadsdk") || s.contains("adsdk") || s.contains("kwad")
                    || s.contains("gdtadv") || s.contains("admob") || s.contains("sigmob")
                    || s.contains("adcolony") || s.contains("applovin") || s.contains("vungle")
                    || s.contains("mbridge") || s.contains("tanx") || s.contains("mobads")
                    || s.contains("splashad") || s.contains("adsplash") || s.contains("landingpage")
                    || s.contains("adpage") || s.contains("adlanding")) return true;
        }
        return false;
    }

    public static boolean isGuardPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return matchesAny(p, GUARD_PKG_HINTS);
    }

    public static boolean isAdTarget(String pkg) {
        if (pkg == null) return false;
        for (String h : AD_TARGET_HINTS) {
            if (pkg.startsWith(h) || pkg.contains(h)) return true;
        }
        return false;
    }

    /* ═════════ 文案匹配 ═════════ */

    public static boolean matchesSkip(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase();
        if (lower.contains("skip") || lower.contains("跳过")) return true;
        return matchesAny(s, SKIP_TEXTS);
    }

    public static boolean isCloseText(String s) {
        if (s == null) return false;
        String t = s.trim();
        String lower = t.toLowerCase();
        for (String k : CLOSE_TEXTS) {
            if (t.equals(k) || lower.equals(k)) return true;
        }
        return false;
    }

    public static boolean isAdBadge(String label) {
        if (label == null) return false;
        String t = label.trim();
        String lower = t.toLowerCase();
        if (t.equals("广告") || t.equals("推广") || t.equals("赞助") || t.equals("广告推广")) return true;
        if (lower.equals("ad") || lower.equals("sponsored") || lower.equals("promoted")) return true;
        return t.length() <= 8 && (t.startsWith("广告 ") || t.startsWith("广告·") || t.endsWith(" 广告"));
    }

    static boolean matchesAny(String s, String[] keys) {
        if (s == null) return false;
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
