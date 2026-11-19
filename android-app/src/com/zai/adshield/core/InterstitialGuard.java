package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.FeedAdGuard.FeedDecision;

/**
 * 插屏卫士（v7.5.0 · 纯 Java · 零 Android 依赖 · JVM 可单测）——「App 内插入
 * 广告主动关闭」的名单外全量兜底。
 * ─────────────────────────────────────────────────────────────
 * 用户实锤（第十四轮原话）：「app 内的插入的广告也是，它们几乎都带有
 * 广告两个字」——合规披露角标（广告/推广/赞助…）是国法要求打的标签，
 * 也是插屏广告最稳定的通用指纹（与 App 名单无关、与窗口形态无关）。
 *
 * 覆盖缺口（v7.4.0 及之前的信息流两段式只在 20 款名单内生效）：
 *  · 名单外 App 的插入广告（文章页间/关卡间/阅读中的插卡、半屏/全屏插层）
 *    ——旧行为零覆盖：feed 扫描被名单门关死，开屏阶梯 12s 窗外零动作，
 *    弹窗路径只认 Dialog 窗类——插层常是宿主窗内覆盖层，三路全漏。
 *  · 本模块（李跳跳方法论）：原生直查（findAccessibilityNodeInfosByText）
 *    角标词 + 关闭词 → 候选交语义+形态+近旁闸逐个判定——预算无关，深树
 *    插层也查得到（与 v7.2.0 feed 主路径同方法论）。
 *
 * 决策（一道在场闸 + 三道安全闸——宁漏勿误红线不变）：
 *  闸⓪ 角标在场（必过）：窗内存在合规披露角标（FeedAdGuard.isDisclosureBadge
 *     语义+形态闸：精确等值或角标词+纯符号尾，≤35% 屏宽 × ≤8% 屏高）。
 *     「广告」两个字是用户实锤的通用指纹——角标不在场绝不出手（普通页面
 *     零命中）；角标自身【绝不点击】（点角标=点进广告主体）。
 *  闸① 关闭语义（精确等值）：× 家族（×/✕/✖/╳/⨯）> 关闭族（关闭/关闭广告/
 *     关闭此广告）> 跳过族（跳过/跳过广告/跳过此广告/跳過/跳過廣告/Skip）
 *     > 知道了族（知道了/我知道了/close）。数组序即优先级（× 永远优先于
 *     知道了——插屏关闭钮是 ×）。
 *  闸② 关闭形态：≤30% 屏宽 × ≤12% 屏高（关闭钮是钮不是横幅；广告主体
 *     大图/大容器绝不可能过闸——绝不点广告本体的几何兜底）。
 *  闸③ 近旁/带位：与角标 |Δcy| ≤35% 屏高（同排铁证）或关闭钮在顶带
 *     （cy ≤30% 屏高）/底带（cy ≥70% 屏高）——插屏 × 常驻地。远距「关闭」
 *     绝不裸点（普通页面的关闭按钮零误触面）。
 *  闸④ 窗不滚动（服务侧 walk 判定后传入）：可滚动窗是信息流/正文页——
 *     由 FeedAdGuard 两段式与开屏阶梯负责，本模块绝不参与（双重出手防线）。
 *
 * 服务侧薄接线（AdSkipService.maybeInterstitialScan）：
 *  · 名单门：feed 名单 20 款 App 排除（FeedAdGuard 拥有——P1X 直关路已在）；
 *  · 事件驱动（窗切换 + 内容变化，2.5s 节流）+ execute() 既有防线全量复用
 *    （用户让路/耐心预算/F·H 多点防线/人手手势派发/边界终闸——语义断言
 *     走通用面：×/关闭/跳过族全在「关闭方向」语义内）。
 * 与 MiniProgramGuard/FeedAdGuard 同规约：纯函数核心，ECJ -1.8 编译 JVM 可测。
 */
public final class InterstitialGuard {

    private InterstitialGuard() {
    }

    /* ══ 安全闸参数（全部 public 常量——测试断言在场性）══ */
    /** 插屏扫描节流（功耗红线：与探针直查构成双闸——非名单 App 常态窗零候选零出手）。 */
    public static final long INTER_SCAN_THROTTLE_MS = 2500;
    /** 关闭钮宽上限（≤30% 屏宽——关闭钮是钮不是横幅）。 */
    public static final double CLOSE_W_MAX = 0.30;
    /** 关闭钮高上限（≤12% 屏高）。 */
    public static final double CLOSE_H_MAX = 0.12;
    /** 关闭钮与角标近旁：|Δcy| ≤35% 屏高（同排铁证）。 */
    public static final double INTER_NEAR_Y_MAX = 0.35;
    /** 关闭钮顶带（cy ≤30% 屏高——插屏 × 常驻地）。 */
    public static final double CLOSE_TOP_BAND = 0.30;
    /** 关闭钮底带（cy ≥70% 屏高）。 */
    public static final double CLOSE_BOTTOM_BAND = 0.70;
    /** 屏幕未知时的像素兜底（324×144 = 30%×1080 宽 / 12%×1200 高 的保守化整数）。 */
    public static final int CLOSE_FALLBACK_W_PX = 324;
    public static final int CLOSE_FALLBACK_H_PX = 144;
    /** 关闭词字符上限（>8 字必是正文）。 */
    public static final int CLOSE_MAX_CHARS = 8;

    /** 决策动作名（服务侧 Decision.reason 用——execute 走通用终闸）。 */
    public static final String ACTION_CLOSE = "inter_close";

    /* ══ 闸①关闭词表（精确等值；数组序即优先级：× 家族 > 关闭族 > 跳过族 > 知道了族）══
     * 「×」家族五符号与 FeedAdGuard.CLOSE_ICON_WORDS 同源（真机插屏关闭钮的
     * 无障碍文案/描述常态——拉丁 X 不收：正文/昵称误而险）；跳过族收编——
     * 插层常带「跳过」钮（后 12s 窗口里规则引擎不再扫，本路是唯一出手位）。 */
    public static final String[] INTER_CLOSE_WORDS = {
            "×", "✕", "✖", "╳", "⨯",
            "关闭", "关闭广告", "关闭此广告",
            "跳过", "跳过广告", "跳过此广告", "跳过领取",
            "跳過", "跳過廣告", "跳過此廣告", "Skip",
            "知道了", "我知道了", "close",
    };

    /** 原生直查词表（服务侧 findAccessibilityNodeInfosByText 用——角标词 + 关闭词
     *  一把直查，命中交语义+形态+近旁闸逐候选判定）。 */
    public static final String[] INTER_SEARCH_WORDS = {
            "广告", "推广", "赞助", "就业", "求职", "招聘", "职位",
            "×", "✕", "✖", "╳", "⨯", "关闭", "跳过", "跳過", "skip", "close", "知道了",
    };

    /* ───────── 闸①：关闭词语义（精确等值——绝不 contains） ───────── */

    /** 是否插屏关闭词（trim 后精确等值——「进入广告详情」类营销文案绝不中）。 */
    public static boolean isInterstitialCloseWord(String label) {
        if (label == null) return false;
        String t = label.trim();
        if (t.length() == 0 || t.length() > CLOSE_MAX_CHARS) return false;
        for (String k : INTER_CLOSE_WORDS) {
            if (t.equals(k)) return true;
        }
        return false;
    }

    /** 关闭词优先级（数组序——× 家族 0 最优；未知词 -1）。 */
    public static int closeWordPriority(String label) {
        if (label == null) return -1;
        String t = label.trim();
        for (int i = 0; i < INTER_CLOSE_WORDS.length; i++) {
            if (INTER_CLOSE_WORDS[i].equals(t)) return i;
        }
        return -1;
    }

    /* ───────── 闸②：关闭钮形态 ───────── */

    /** 关闭钮形态闸：宽 ≤30% 屏宽 × 高 ≤12% 屏高（屏幕未知 → 324×144px 兜底）。 */
    public static boolean closeFormOk(int[] b, int screenW, int screenH) {
        if (b == null || b.length < 4) return false;
        int w = b[2] - b[0], h = b[3] - b[1];
        if (w <= 0 || h <= 0) return false;
        if (screenW > 0 && screenH > 0) {
            return w <= (int) (screenW * CLOSE_W_MAX) && h <= (int) (screenH * CLOSE_H_MAX);
        }
        return w <= CLOSE_FALLBACK_W_PX && h <= CLOSE_FALLBACK_H_PX;
    }

    /* ───────── 决策（纯函数 · 候选式——李跳跳方法论） ───────── */

    /**
     * 插屏关闭决策：角标候选 + 关闭候选（原生直查命中）→ 闸⓪~闸④逐候选判定。
     * 返回 null = 无插屏签名（角标不在场 / 关闭钮未过闸——宁漏勿误）。
     * tapBounds = 关闭钮【自身】bounds——绝不广告主体、绝不角标。
     *
     * @param badgeCands  角标词直查命中（含未过闸的——闸⓪内逐个判定）
     * @param closeCands  关闭词直查命中（含未过闸的——闸①~③内逐个判定）
     * @param scrollPresent 窗内存在可滚动容器（闸④：信息流/正文页绝不参与）
     */
    public static FeedDecision decideFromCandidates(java.util.List<NodeView> badgeCands,
                                                    java.util.List<NodeView> closeCands,
                                                    int screenW, int screenH,
                                                    boolean scrollPresent) {
        if (scrollPresent) return null; // 闸④：可滚动窗归信息流两段式/开屏阶梯
        if (badgeCands == null || badgeCands.isEmpty() || closeCands == null) return null;

        /* 闸⓪：角标在场（语义+形态完整判定）——用户实锤「几乎都带有广告
         * 两个字」的通用指纹。角标只作在场证据，绝不点击。 */
        int[] badgeB = null;
        for (int i = 0; i < badgeCands.size(); i++) {
            NodeView n = badgeCands.get(i);
            if (n == null) continue;
            try {
                if (!n.visible()) continue;
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                int[] b = n.bounds();
                if (label.length() > 0 && FeedAdGuard.isDisclosureBadge(label, b, screenW, screenH)) {
                    badgeB = b;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        if (badgeB == null) return null; // 角标不在场 → 绝不出手（宁漏勿误）

        /* 闸①~③：关闭候选逐个判定（词优先级序收集最优）。
         * 近旁/带位闸：与角标 |Δcy|≤35% 屏高 或 关闭钮在顶带/底带。 */
        NodeView bestNode = null;
        int[] bestB = null;
        String bestLabel = null;
        int bestPri = Integer.MAX_VALUE;
        for (int i = 0; i < closeCands.size(); i++) {
            NodeView n = closeCands.get(i);
            if (n == null) continue;
            try {
                if (!n.visible()) continue;
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                if (!isInterstitialCloseWord(label)) continue; // 闸①语义（精确等值）
                int[] b = n.bounds();
                if (!closeFormOk(b, screenW, screenH)) continue; // 闸②形态
                int pri = closeWordPriority(label);
                if (pri < 0) continue;
                if (pri >= bestPri) continue; // 只收更优（或首个）
                boolean near = nearBadge(b, badgeB, screenW, screenH);
                boolean band = inTopOrBottomBand(b, screenW, screenH);
                if (!near && !band) continue; // 闸③近旁/带位
                bestNode = n;
                bestB = b;
                bestLabel = label;
                bestPri = pri;
            } catch (Throwable ignored) {
            }
        }
        if (bestNode == null || bestB == null) return null;

        FeedDecision fd = new FeedDecision();
        fd.action = ACTION_CLOSE;
        fd.label = bestLabel;
        fd.tapBounds = bestB; // 关闭钮【自身】bounds——绝不广告主体
        fd.node = bestNode;
        return fd;
    }

    /** 闸③近旁：关闭钮与角标 |Δcy| ≤35% 屏高（同排铁证——P1X 同源判定）。 */
    public static boolean nearBadge(int[] b, int[] badge, int screenW, int screenH) {
        if (b == null || badge == null || screenH <= 0) return false;
        int cy = (b[1] + b[3]) / 2, cyb = (badge[1] + badge[3]) / 2;
        return Math.abs(cy - cyb) <= screenH * INTER_NEAR_Y_MAX;
    }

    /** 闸③带位：关闭钮在顶带（cy ≤30% 屏高）或底带（cy ≥70% 屏高）。 */
    public static boolean inTopOrBottomBand(int[] b, int screenW, int screenH) {
        if (b == null || screenH <= 0) return false;
        int cy = (b[1] + b[3]) / 2;
        return cy <= screenH * CLOSE_TOP_BAND || cy >= screenH * CLOSE_BOTTOM_BAND;
    }

    static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
