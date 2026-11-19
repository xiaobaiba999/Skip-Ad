package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.FeedAdGuard.FeedDecision;

/**
 * 插屏卫士（v7.6.0 激进版 · 纯 Java · 零 Android 依赖 · JVM 可单测）——
 * 「App 内插入广告主动关闭」的名单外全量兜底。
 * ─────────────────────────────────────────────────────────────
 * 用户实锤（第十五轮原话）：「软件内插入的广告不仅没跳过……你可以激进
 * 一点暂时先放弃宁可漏拦绝不误触」——v7.5.0 版四道闸全开也拦不到真机
 * 插屏（闸⓪ 角标形态过严：「广告 · 了解详情」「查看广告详情」等变体
 * 全被 isDisclosureBadge 精确等值拒之门外；闸③ 带位闸再把中部关闭钮
 * 全灭）。v7.6.0 激进重做：
 *
 *  · 闸⓪【广告语境在场】（contains，非精确等值）：窗内任何可见 label
 *    包含「广告/推广/赞助」→ 语境在场即出手（用户实锤「几乎都带有广告
 *    两个字」——变体千千万，包含匹配一网打尽）。角标自身仍绝不点击。
 *  · 闸① 关闭词精确等值保持 + 扩表（v7.5.0「知道了」族被通用终闸
 *    语义面误杀的根治见 ComplianceGuard.interBoundaryCheck）：
 *    × 家族 > 关闭族 > 跳过族 > 知道了族 > 残忍离开族（游戏插屏标配）。
 *  · 闸② 关闭形态保持（≤30% 屏宽 × ≤12% 屏高）——关闭钮是钮不是横幅，
 *    广告主体大图/大容器绝不可能过闸（绝不点广告本体的几何兜底）。
 *  · 闸③ 位置闸激进放宽：非滚动窗【零位置闸】（关闭词本身已足够特异
 *    ——用户指令激进）；滚动窗（信息流卡片）须与广告语境节点近旁
 *    （|Δcy| ≤35% 屏高同排铁证）才出手。
 *  · 闸④ 滚动窗不再一票否决：名单外 App 的信息流卡片广告（李跳跳在
 *    信息流里也点）——近旁铁证下可出手；Feed 名单 20 款窗由
 *    FeedAdGuard 两段式拥有（服务侧名单门保持，双重出手防线）。
 *
 * 服务侧薄接线（AdSkipService.maybeInterstitialScan）：
 *  · 事件驱动（窗切换 + 内容变化，2.5s 包级节流）+ v7.6.0 新窗首扫
 *    提速（窗切换即重置节流——插层出现当帧就扫，不再等 2.5s）；
 *  · execute() 既有防线全量复用（用户让路/耐心预算/F·H 多点防线/
 *    人手手势派发 + interBoundaryCheck 插屏专用终闸）。
 * 与 MiniProgramGuard/FeedAdGuard 同规约：纯函数核心，ECJ -1.8 编译 JVM 可测。
 */
public final class InterstitialGuard {

    private InterstitialGuard() {
    }

    /* ══ 闸参数（全部 public 常量——测试断言在场性）══ */
    /** 插屏扫描节流（功耗红线：与探针直查构成双闸——非名单 App 常态窗零候选零出手）。 */
    public static final long INTER_SCAN_THROTTLE_MS = 2500;
    /** 关闭钮宽上限（≤30% 屏宽——关闭钮是钮不是横幅）。 */
    public static final double CLOSE_W_MAX = 0.30;
    /** 关闭钮高上限（≤12% 屏高）。 */
    public static final double CLOSE_H_MAX = 0.12;
    /** 滚动窗近旁闸：关闭钮与广告语境节点 |Δcy| ≤35% 屏高（同排铁证）。 */
    public static final double INTER_NEAR_Y_MAX = 0.35;
    /** 屏幕未知时的像素兜底（324×144 = 30%×1080 宽 / 12%×1200 高 的保守化整数）。 */
    public static final int CLOSE_FALLBACK_W_PX = 324;
    public static final int CLOSE_FALLBACK_H_PX = 144;
    /** 关闭词字符上限（>8 字必是正文）。 */
    public static final int CLOSE_MAX_CHARS = 8;
    /** 广告语境词字符上限（「查看广告详情」8 字内；超长正文虽含「广告」也不算语境节点——
     *  正文文本节点常几十上百字，contains 面过大）。 */
    public static final int CONTEXT_MAX_CHARS = 10;

    /** 决策动作名（服务侧 Decision.reason 用——execute 走插屏专用终闸）。 */
    public static final String ACTION_CLOSE = "inter_close";

    /* ══ 闸①关闭词表（精确等值；数组序即优先级：× 家族 > 关闭族 > 跳过族 >
     * 知道了族 > 残忍离开族 > 暂不需要族）══
     * v7.6.0 扩表：残忍离开/残忍退出/残忍拒绝（游戏插屏标配关闭词——
     * 「残忍离开」几乎是游戏插屏唯一关闭钮文案）、不再看/不看了（贴片
     * 常态）、暂不需要/不需要（应用内升级插层）。拉丁 X 不收：正文/昵称
     * 误而险；「取消」不收：普通弹窗通用词，激进模式下也保持拒收
     *（取消 ≠ 关闭广告语义方向，误点面不可控）。 */
    public static final String[] INTER_CLOSE_WORDS = {
            "×", "✕", "✖", "╳", "⨯",
            "关闭", "关闭广告", "关闭此广告",
            "跳过", "跳过广告", "跳过此广告", "跳过领取",
            "跳過", "跳過廣告", "跳過此廣告", "Skip", "Skip Ad",
            "知道了", "我知道了", "close",
            "残忍离开", "残忍退出", "残忍拒绝",
            "不再看", "不看了", "暂不需要", "不需要",
    };

    /** v7.6.0 广告语境词（contains）：用户实锤「它们几乎都带有广告两个字」——
     *  广告法合规披露标签的千姿百态（广告/广告 ·/查看广告详情/广告推荐）。 */
    public static final String[] INTER_CONTEXT_WORDS = {
            "广告", "推广", "赞助",
    };

    /** 原生直查词表（服务侧 findAccessibilityNodeInfosByText 用——关闭词一把
     *  直查；语境词单独直查（INTER_CONTEXT_WORDS），双查询不重复收集）。 */
    public static final String[] INTER_SEARCH_WORDS = {
            "×", "✕", "✖", "╳", "⨯", "关闭", "跳过", "跳過", "skip", "close",
            "知道了", "残忍", "不再看", "暂不需要", "不需要",
    };

    /* ───────── 闸⓪：广告语境在场（contains——v7.6.0 激进版） ───────── */

    /** v7.6.0 广告语境在场：label 包含任一语境词（≤CONTEXT_MAX_CHARS 字的
     *  可见标签——超长正文不算语境节点，contains 面可控）。
     *  v7.5.0 isDisclosureBadge 精确等值+符号尾+形态三闸把「广告 · 了解详情」
     *  「查看广告详情」等真机变体全拒——用户实锤「不仅没跳过」的死因①。 */
    public static boolean hasAdContextWord(String label) {
        if (label == null) return false;
        String t = label.trim();
        if (t.length() == 0 || t.length() > CONTEXT_MAX_CHARS) return false;
        for (String w : INTER_CONTEXT_WORDS) {
            if (t.contains(w)) return true;
        }
        return false;
    }

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
     * 插屏关闭决策（v7.6.0 激进版）：语境候选 + 关闭候选（原生直查命中）
     * → 闸⓪~闸③逐候选判定。返回 null = 无插屏签名。
     * tapBounds = 关闭钮【自身】bounds——绝不广告主体、绝不语境节点。
     *
     * @param ctxCands   语境词直查命中（「广告」contains——闸⓪在场证据）
     * @param closeCands 关闭词直查命中（含未过闸的——闸①~③内逐个判定）
     * @param scrollPresent 窗内存在可滚动容器（闸③：滚动窗须近旁铁证）
     */
    public static FeedDecision decideFromCandidates(java.util.List<NodeView> ctxCands,
                                                     java.util.List<NodeView> closeCands,
                                                     int screenW, int screenH,
                                                     boolean scrollPresent) {
        if (ctxCands == null || ctxCands.isEmpty() || closeCands == null || closeCands.isEmpty()) return null;

        /* 闸⓪：广告语境在场（contains——用户实锤「几乎都带有广告两个字」）。
         * 语境节点只作在场证据，绝不点击。收集语境节点中心（滚动窗近旁闸用）。 */
        int[] ctxB = null;
        for (int i = 0; i < ctxCands.size(); i++) {
            NodeView n = ctxCands.get(i);
            if (n == null) continue;
            try {
                if (!n.visible()) continue;
                String label = nz(n.text());
                if (label.length() == 0) label = nz(n.desc());
                if (!hasAdContextWord(label)) continue;
                int[] b = n.bounds();
                if (b != null && b.length >= 4 && b[2] > b[0] && b[3] > b[1]) {
                    if (ctxB == null) ctxB = b; // 首个语境节点（近旁判定基准）
                }
            } catch (Throwable ignored) {
            }
        }
        if (ctxB == null) return null; // 语境不在场 → 绝不出手

        /* 闸①~③：关闭候选逐个判定（词优先级序收集最优）。
         * v7.6.0 激进：非滚动窗零位置闸（关闭词本身已足够特异——用户指令
         * 「激进一点暂时先放弃宁可漏拦绝不误触」）；滚动窗（信息流卡片）
         * 须与广告语境节点近旁（|Δcy|≤35% 屏高同排铁证）才出手。 */
        NodeView bestNode = null;
        int[] bestB = null;
        String bestLabel = null;
        int bestPri = Integer.MAX_VALUE;
        for (int i = 0; i < closeCands.size(); i++) {
            NodeView n = closeCands.get(i);
            if (n == null) continue;
            try {
                if (!n.visible()) continue;
                String label = nz(n.text());
                if (label.length() == 0) label = nz(n.desc());
                if (!isInterstitialCloseWord(label)) continue; // 闸①语义（精确等值）
                int[] b = n.bounds();
                if (!closeFormOk(b, screenW, screenH)) continue; // 闸②形态
                int pri = closeWordPriority(label);
                if (pri < 0) continue;
                if (pri >= bestPri) continue; // 只收更优（或首个）
                if (scrollPresent && !nearContext(b, ctxB, screenH)) continue; // 闸③滚动窗近旁铁证
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

    /** 闸③滚动窗近旁：关闭钮与广告语境节点 |Δcy| ≤35% 屏高（同排铁证——
     *  信息流卡片「广告」角标与 ×/关闭钮同卡的几何铁证）。 */
    public static boolean nearContext(int[] b, int[] ctx, int screenH) {
        if (b == null || ctx == null || screenH <= 0) return false;
        int cy = (b[1] + b[3]) / 2, cyc = (ctx[1] + ctx[3]) / 2;
        return Math.abs(cy - cyc) <= screenH * INTER_NEAR_Y_MAX;
    }

    static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
