package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.NodeView;

/**
 * 信息流广告卫士（v6.2.0 首发·v7.1.0 自已验证代码移植回稳基座）——
 * 「App 内广告识别跳过」核心能力。
 * ─────────────────────────────────────────────────────────────
 * 用户实锤（第九轮原话）：扫描日志很多广告命中不了，最好把 app 内的广告也
 * 识别跳过，比如微信朋友圈里就业广告，还有其他 app 也是。
 * 用户实锤（第十一轮原话）：能不能增加功能实现自动关闭 app 内的广告内容，
 * 例如微信朋友圈就有广告穿插在朋友圈之中——v7.1.0 将本模块（v6.2.0
 * 已验证实现·81 项单测全绿）整体移植回 7.0 稳定基座，接线零改动。
 * 用户实锤（第十二轮原话）：app 内穿插的广告全都没拦截，关于这一块的
 * 实现可以参考李跳跳的实现逻辑（李跳跳核心方法论：按文本直查目标节点
 * findAccessibilityNodeInfosByText + 逐节点语义闸确认后点击，不做预算制
 * DFS 爬树）——v7.2.0 三处真机病灶根治：
 *  · 病灶① P2 被窗类覆盖双杀：P1 点角标后拉起的关闭菜单是 Dialog/Popup
 *    新窗 → TYPE_WINDOW_STATE_CHANGED 把 winClass 覆盖成菜单窗类 →
 *    isFeedWindow(微信 sns 窗门) 失败 + winAt 重置 → 12s 开屏窗闸阻断 →
 *    8s armed 指纹窗活活耗尽 →「全都没拦截」。修复：armed 直通（P2 只认
 *    armed 本身 + 中性项精确等值双保险，不再依赖窗类/窗计时）；
 *  · 病灶② 预算制 DFS 够不到深层卡片：300 节点预算在朋友圈大树上只爬到
 *    前几张卡片，深处理广告角标永远扫不到。修复：原生
 *    findAccessibilityNodeInfosByText 按角标词/中性词直查（C++ 侧全树搜，
 *    预算无关），候选节点再过本模块语义+形态闸（宁漏勿误红线不变）；
 *  · 病灶③ P2 双闸过严：真机微信角标菜单常只露出 1 个中性项（如仅
 *    「不感兴趣」）→ 强特征≥1 且总数≥2 双闸全零命中 → 菜单永远不收。
 *    修复：armed（P1 刚出手）本身即最强上下文信号——armed 且中性项≥1
 *    即出手（李跳跳式）；非 armed 依旧零动作（用户自己长拉的菜单绝
 *    不对抗）。
 *
 * 场景（与开屏广告的本质差异）：开屏广告是「全屏整窗 + 独立倒计时键」——
 * 跳过即离开；信息流广告（朋友圈就业广告/微博信息流/B站推荐流）是「嵌在
 * 用户正在浏览的内容流里的一张卡片」——没有跳过键，只有卡片角落的披露
 * 角标（「广告/就业/招聘」）与角标背后的关闭菜单。直接点卡片任何位置都
 * 是点进广告主体（误触事故面），唯一安全路径是两段式：
 *
 *  P1 点角标：点卡片角上的「广告/就业」披露角标 → 拉起关闭菜单；
 *  P2 收菜单：点「关闭该广告/不感兴趣」中性项收起卡片。
 *
 * 四道安全闸（宁漏勿误红线——绝不点广告主体）：
 *  ① 角标词精确等值：trim 后 equals——招聘广告正文满篇「广告/招聘」字样
 *     contains 即误点广告主体，绝不 contains；带纯符号尾（广告×/广告·/
 *     就业›）的披露徽标单独收编；>6 字必是正文；
 *  ② 角标面积门：宽 ≤35% 屏宽 × 高 ≤8% 屏高（屏幕未知 → 420×126 像素
 *     兜底）——广告主体大图/大容器绝不可能过闸（绝不点广告本体的几何兜底）；
 *  ③ P2 只点中性关闭项：精确等值（关闭该广告/关闭此广告/屏蔽此广告/
 *     关闭广告/不感兴趣）——屏蔽作者/举报/反馈类扩展动作绝不代点（替用户
 *     做超出本条的决策=越权）；且须 P1 出手 8s 指纹窗内（用户自己长按的
 *     「不感兴趣」菜单绝不对抗）+ 菜单强特征 ≥1 且中性项总数 ≥2 双闸；
 *  ④ 名单门：18 个信息流 App 名单内才扫（微信/微博/头条/抖音/快手/
 *     小红书/B站/知乎等）；名单外 App 零 feed 扫描（开屏/弹窗/插屏路径
 *     覆盖——功耗红线不破）；微信窗级门只认 sns 族窗（聊天主界面零扫描
 *     红线保持）。
 *
 * 功耗双闸：扫描节流 FEED_SCAN_THROTTLE_MS=1.2s + 节点预算
 * FEED_NODE_BUDGET=300（与开屏路径互不挤占；feed 窗口常驻不重置）。
 *
 * 设计红线：纯 Java 零 Android 依赖纯函数（与 MiniProgramGuard 同规约），
 * JVM 可单测（FeedAdGuardTest）；服务侧接线薄（AdSkipService.maybeFeedScan）。
 */
public final class FeedAdGuard {

    private FeedAdGuard() {
    }

    /* ══ 安全闸参数（全部 public 常量——测试断言在场性）══ */
    /** 信息流扫描节流（功耗红线：与节点预算构成双闸）。 */
    public static final long FEED_SCAN_THROTTLE_MS = 1200;
    /** v7.2.0 P2 扫描节流（菜单拉起后的快节奏复查——350ms，armed 专用）。 */
    public static final long P2_SCAN_THROTTLE_MS = 350;
    /** 信息流树节点预算（大 feed 树深挖上限——第二道功耗闸）。 */
    public static final int FEED_NODE_BUDGET = 300;
    /** P2 须在 P1 出手 8s 指纹窗内（用户自己长按的菜单绝不对抗）。 */
    public static final long P1_ARM_MS = 8000;
    /** 角标宽上限（≤35% 屏宽——广告主体大图绝不可能过闸）。 */
    public static final double BADGE_W_MAX = 0.35;
    /** 角标高上限（≤8% 屏高）。 */
    public static final double BADGE_H_MAX = 0.08;
    /** 屏幕未知时的像素兜底（420×126 = 35%×1200 宽 / 8%×1580 高 的保守化整数）。 */
    public static final int BADGE_FALLBACK_W_PX = 420;
    public static final int BADGE_FALLBACK_H_PX = 126;
    /** 角标词字符上限（>6 字必是正文）。 */
    public static final int BADGE_MAX_CHARS = 6;

    /* ══ 决策动作名（服务侧 Decision.reason 用——execute 走 feed 终闸）══ */
    public static final String ACTION_P1_BADGE = "feed_p1_badge";
    public static final String ACTION_P2_CLOSE = "feed_p2_close";

    /* ══ 名单门（闸④）：18 个信息流 App ══
     * 微信加窗级门（只认 sns 族窗——朋友圈 SnsTimeLineUI 族；聊天主界面
     * LauncherUI 零扫描红线由 isChatMainUi 另行保持，AppBrandUI 小程序容器
     * 由 MiniProgramGuard 更早拦截）；其余 17 款任意窗（角标语义+形态门兜底
     * ——普通页面零命中，宁漏勿误）。 */
    static final String PKG_WECHAT = "com.tencent.mm";
    static final String[] FEED_APPS = {
            PKG_WECHAT,                          // 微信（朋友圈——窗级门只认 sns 族窗）
            "com.sina.weibo",                    // 微博
            "com.ss.android.article.news",       // 今日头条
            "com.ss.android.ugc.aweme",          // 抖音
            "com.smile.gifmaker",                // 快手
            "com.xingin.xhs",                    // 小红书
            "tv.danmaku.bili",                   // 哔哩哔哩
            "com.zhihu.android",                 // 知乎
            "com.baidu.searchbox",               // 百度
            "com.UCMobile",                      // UC 浏览器
            "com.tencent.mtt",                   // QQ 浏览器
            "com.tencent.news",                   // 腾讯新闻
            "com.netease.newsreader",            // 网易新闻
            "com.sina.news",                     // 新浪新闻
            "com.quark.browser",                 // 夸克浏览器
            "com.snda.wifilocating",             // WiFi 万能钥匙
            "com.hupu.games",                    // 虎扑
            "com.cubic.autohome",                // 汽车之家
    };

    /* ══ 闸①：披露角标词（精确等值——绝不 contains）══
     * 朋友圈就业广告披露角标族（就业/求职/招聘/职位）与通用披露角标
     * （广告/推广）同表；带纯符号尾的变体（广告×/广告·/就业›）单独收编。
     * v7.2.0 public：服务侧原生直查（李跳跳方法论）按词逐个直查。 */
    public static final String[] BADGE_WORDS = {
            "广告", "推广", "就业", "求职", "招聘", "职位",
    };

    /* ══ 闸③：P2 中性关闭项（精确等值；优先级即数组序）。
     * v7.2.0 收编「减少此类广告」（微信菜单变体）与「屏蔽此推广」（旧版
     * 朋友圈文案）两族真机文案。 */
    static final String[] NEUTRAL_CLOSE_ITEMS = {
            "关闭该广告", "关闭此广告", "屏蔽此广告", "屏蔽此推广", "关闭广告", "不感兴趣", "减少此类广告",
    };

    /** v7.2.0 中性关闭词表 public 视图（服务侧 P2 原生直查用）。 */
    public static String[] neutralCloseWords() {
        return NEUTRAL_CLOSE_ITEMS.clone();
    }
    /** 菜单强特征（真·广告关闭菜单独有文案）。 */
    static final String[] NEUTRAL_STRONG_ITEMS = {
            "关闭该广告", "关闭此广告", "屏蔽此广告", "屏蔽此推广",
    };
    /** 扩展动作（绝不代点——替用户做超出「关闭本条」的决策=越权）。 */
    static final String[] EXTENDED_ACTIONS = {
            "屏蔽作者", "屏蔽该作者", "不看", "拉黑", "举报", "反馈", "投诉",
    };

    /* ══ 决策结果（服务侧薄接线消费）══ */
    public static final class FeedDecision {
        public String action;    // ACTION_P1_BADGE / ACTION_P2_CLOSE
        public String label;     // "广告" / "关闭该广告" …
        public int[] tapBounds;  // 【角标/菜单项自身】bounds——绝不广告主体
        public NodeView node;    // 目标节点（NodeAdapter 包装）
    }

    /* ───────── 闸④：名单门 ───────── */

    /** 是否信息流 App（18 款名单——名单外 App 零 feed 扫描）。 */
    public static boolean isFeedApp(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        for (String p : FEED_APPS) {
            if (p.equals(pkg)) return true;
        }
        return false;
    }

    /** 微信 sns 族窗（朋友圈窗——类名含 "sns" 如 SnsTimeLineUI）。 */
    public static boolean isSnsWindow(String cls) {
        if (cls == null || cls.length() == 0) return false;
        return cls.toLowerCase().contains("sns");
    }

    /**
     * 当前窗口是否信息流扫描窗（名单门 + 微信窗级门）：
     * 微信只认 sns 族窗（聊天主界面零扫描红线保持）；其余 17 款任意窗
     * （普通页面无角标零命中——语义+形态门兜底）。
     */
    public static boolean isFeedWindow(String pkg, String cls) {
        if (!isFeedApp(pkg)) return false;
        if (PKG_WECHAT.equals(pkg)) return isSnsWindow(cls);
        return true;
    }

    /* ───────── 闸①②：P1 披露角标（语义 + 形态） ───────── */

    /**
     * 是否披露角标（闸①语义 + 闸②面积的完整判定）：
     *  · 语义：trim 后与角标词【精确等值】，或 角标词+纯符号尾
     *    （广告×/广告·/就业›——尾部绝无字母/数字/汉字）；>6 字必是正文；
     *  · 形态：宽 ≤35% 屏宽 且 高 ≤8% 屏高（屏幕未知 → 420×126px 兜底）。
     */
    public static boolean isDisclosureBadge(String label, int[] b, int screenW, int screenH) {
        if (label == null || b == null || b.length < 4) return false;
        String t = label.trim();
        if (t.length() == 0 || t.length() > BADGE_MAX_CHARS) return false;
        if (!badgeWordOrSymbolTail(t)) return false;
        int w = b[2] - b[0], h = b[3] - b[1];
        if (w <= 0 || h <= 0) return false;
        if (screenW > 0 && screenH > 0) {
            return w <= (int) (screenW * BADGE_W_MAX) && h <= (int) (screenH * BADGE_H_MAX);
        }
        return w <= BADGE_FALLBACK_W_PX && h <= BADGE_FALLBACK_H_PX;
    }

    /** 角标词精确等值 或 角标词+纯符号尾（广告×/广告·/就业›）。 */
    static boolean badgeWordOrSymbolTail(String t) {
        for (String word : BADGE_WORDS) {
            if (t.equals(word)) return true;
            if (t.startsWith(word) && isPureSymbolTail(t.substring(word.length()))) return true;
        }
        return false;
    }

    /** 尾部是否纯符号（绝无字母/数字/汉字——「广告×」过 ·「广告位」不过）。 */
    static boolean isPureSymbolTail(String s) {
        if (s == null || s.length() == 0) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || isCjk(c)) return false;
        }
        return true;
    }

    static boolean isCjk(char c) {
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS;
    }

    /* ───────── 闸③：P2 中性关闭项 ───────── */

    /** 是否中性关闭项（trim 后精确等值——关闭该广告/关闭此广告/屏蔽此广告/关闭广告/不感兴趣）。 */
    public static boolean isNeutralCloseItem(String label) {
        if (label == null) return false;
        String t = label.trim();
        for (String k : NEUTRAL_CLOSE_ITEMS) {
            if (t.equals(k)) return true;
        }
        return false;
    }

    /** 是否菜单强特征项（真·广告关闭菜单独有——双闸之一）。 */
    public static boolean isNeutralStrongItem(String label) {
        if (label == null) return false;
        String t = label.trim();
        for (String k : NEUTRAL_STRONG_ITEMS) {
            if (t.equals(k)) return true;
        }
        return false;
    }

    /** 是否扩展动作（屏蔽作者/举报/反馈类——绝不代点）。 */
    public static boolean isExtendedActionItem(String label) {
        if (label == null) return false;
        String t = label.trim();
        for (String k : EXTENDED_ACTIONS) {
            if (t.contains(k)) return true;
        }
        return false;
    }

    /* ───────── 决策（纯函数 · 预算制 DFS） ───────── */

    /**
     * P1 决策：树内找披露角标（闸①②逐节点判定）。
     * 返回 null = 无角标（正常内容流零命中——宁漏勿误）。
     * tapBounds = 角标【自身】bounds——绝不广告主体。
     */
    public static FeedDecision decideP1(NodeView root, int screenW, int screenH) {
        if (root == null) return null;
        int[] budget = {FEED_NODE_BUDGET};
        NodeView hit = findBadge(root, 0, budget, screenW, screenH);
        if (hit == null) return null;
        return build(ACTION_P1_BADGE, hit, screenW, screenH);
    }

    /**
     * P2 决策（DFS 兜底路径）：armed（P1 出手 8s 指纹窗内）才找中性关闭项
     * 收菜单；非 armed 一律 null（用户自己长按的「不感兴趣」菜单绝不对抗）。
     * v7.2.0 病灶③根治：armed 本身即最强上下文（角标刚被我们点开）——
     * 中性项 ≥1 即出手（真机微信菜单常只露 1 个中性项，旧「强特征≥1 且
     * 总数≥2」双闸在真机上零命中）；精确等值 + 扩展动作排除原样保持。
     */
    public static FeedDecision decideP2(NodeView root, int screenW, int screenH, boolean armed) {
        if (!armed || root == null) return null;
        int[] budget = {FEED_NODE_BUDGET};
        java.util.LinkedHashMap<String, NodeView> found =
                new java.util.LinkedHashMap<String, NodeView>();
        collectNeutral(root, 0, budget, found, screenW, screenH);
        if (found.isEmpty()) return null; // v7.2.0：中性项 ≥1（armed 即确认信号）
        /* 优先级即数组序 */
        for (String pri : NEUTRAL_CLOSE_ITEMS) {
            NodeView n = found.get(pri);
            if (n != null) return build(ACTION_P2_CLOSE, n, screenW, screenH);
        }
        return null;
    }

    /* ───────── v7.2.0 候选式快搜（李跳跳方法论主路径） ─────────
     * 服务侧用 AccessibilityNodeInfo.findAccessibilityNodeInfosByText(词)
     * 原生直查（C++ 侧全树 contains 搜索·预算无关——300 节点 DFS 够不到
     * 的深层卡片也能查到），命中节点包装成 NodeView 候选传入；本模块仍逐
     * 候选过完整语义（精确等值）+ 形态（≤35%屏×≤8%屏）闸——宁漏勿误红线
     * 与 DFS 路径完全一致，只是「爬树」换成「直查」。 */

    /**
     * P1 候选式决策：角标词原生直查命中节点里找披露角标（闸①②逐候选
     * 判定）。返回 null = 无角标（正常内容流零命中——宁漏勿误）。
     * tapBounds = 角标【自身】 bounds——绝不广告主体。
     */
    public static FeedDecision decideP1FromCandidates(java.util.List<NodeView> candidates,
                                                       int screenW, int screenH) {
        if (candidates == null) return null;
        for (NodeView n : candidates) {
            if (n == null) continue;
            try {
                if (!n.visible()) continue;
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                int[] b = n.bounds();
                if (label.length() > 0 && isDisclosureBadge(label, b, screenW, screenH)) {
                    return build(ACTION_P1_BADGE, n, screenW, screenH);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * P2 候选式决策：中性词原生直查命中节点里找关闭项收菜单（armed 才出手
     * ——与 DFS 路径同闸；精确等值 + 扩展动作排除 + 形态闸逐候选不变）。
     * 优先级即数组序（关闭该广告 > … > 不感兴趣 > 减少此类广告）。
     */
    public static FeedDecision decideP2FromCandidates(java.util.List<NodeView> candidates,
                                                        int screenW, int screenH, boolean armed) {
        if (!armed || candidates == null) return null;
        java.util.LinkedHashMap<String, NodeView> found =
                new java.util.LinkedHashMap<String, NodeView>();
        for (NodeView n : candidates) {
            if (n == null) continue;
            try {
                if (!n.visible()) continue;
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                int[] b = n.bounds();
                if (label.length() == 0 || b == null || b.length < 4
                        || b[2] <= b[0] || b[3] <= b[1]) continue;
                if (!isNeutralCloseItem(label) || isExtendedActionItem(label)) continue;
                int w = b[2] - b[0], h = b[3] - b[1];
                boolean formOk;
                if (screenW > 0 && screenH > 0) {
                    formOk = w <= (int) (screenW * BADGE_W_MAX) && h <= (int) (screenH * BADGE_H_MAX);
                } else {
                    formOk = w <= BADGE_FALLBACK_W_PX && h <= BADGE_FALLBACK_H_PX;
                }
                if (formOk && !found.containsKey(label)) found.put(label, n);
            } catch (Throwable ignored) {
            }
        }
        if (found.isEmpty()) return null;
        for (String pri : NEUTRAL_CLOSE_ITEMS) {
            NodeView n = found.get(pri);
            if (n != null) return build(ACTION_P2_CLOSE, n, screenW, screenH);
        }
        return null;
    }

    /** P1 深度遍历（预算制；可见节点 + 语义/形态双闸逐节点）。 */
    private static NodeView findBadge(NodeView n, int depth, int[] budget, int screenW, int screenH) {
        if (n == null || depth > 32 || budget[0] <= 0) return null;
        budget[0]--;
        try {
            if (n.visible()) {
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                int[] b = n.bounds();
                if (label.length() > 0 && isDisclosureBadge(label, b, screenW, screenH)) {
                    return n;
                }
            }
            for (int i = 0; i < n.childCount(); i++) {
                NodeView hit = findBadge(n.child(i), depth + 1, budget, screenW, screenH);
                if (hit != null) return hit;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** P2 收集（预算制；中性项精确等值 + 形态 ≤35% 屏防巨横幅行）。 */
    private static void collectNeutral(NodeView n, int depth, int[] budget,
                                       java.util.LinkedHashMap<String, NodeView> found,
                                       int screenW, int screenH) {
        if (n == null || depth > 32 || budget[0] <= 0) return;
        budget[0]--;
        try {
            if (n.visible()) {
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                int[] b = n.bounds();
                if (label.length() > 0 && b != null && b.length >= 4 && b[2] > b[0] && b[3] > b[1]
                        && isNeutralCloseItem(label) && !isExtendedActionItem(label)) {
                    /* 形态闸：菜单项是紧凑行（≤35% 屏宽 × ≤8% 屏高——与角标同门），
                     * 巨横幅 CTA 绝不进菜单候选 */
                    int w = b[2] - b[0], h = b[3] - b[1];
                    boolean formOk;
                    if (screenW > 0 && screenH > 0) {
                        formOk = w <= (int) (screenW * BADGE_W_MAX) && h <= (int) (screenH * BADGE_H_MAX);
                    } else {
                        formOk = w <= BADGE_FALLBACK_W_PX && h <= BADGE_FALLBACK_H_PX;
                    }
                    if (formOk && !found.containsKey(label)) found.put(label, n);
                }
            }
            for (int i = 0; i < n.childCount(); i++) {
                collectNeutral(n.child(i), depth + 1, budget, found, screenW, screenH);
            }
        } catch (Throwable ignored) {
        }
    }

    private static FeedDecision build(String action, NodeView n, int screenW, int screenH) {
        FeedDecision d = new FeedDecision();
        d.action = action;
        String text = nz(n.text());
        String desc = nz(n.desc());
        d.label = text.length() > 0 ? text.trim() : desc.trim();
        d.tapBounds = n.bounds();
        d.node = n;
        return d;
    }

    /** 名单/规则统计（启动日志用）。 */
    public static String stats() {
        return "信息流 App 名单 " + FEED_APPS.length + " 款 · 角标词 " + BADGE_WORDS.length
                + " 族(≤" + BADGE_MAX_CHARS + "字,宽≤" + (int) (BADGE_W_MAX * 100) + "%屏×高≤"
                + (int) (BADGE_H_MAX * 100) + "%屏) · 中性关闭 " + NEUTRAL_CLOSE_ITEMS.length
                + " 项(强特征 " + NEUTRAL_STRONG_ITEMS.length + " + 弱 " + (NEUTRAL_CLOSE_ITEMS.length - NEUTRAL_STRONG_ITEMS.length)
                + " · armed≥1即出手) · 扩展动作绝不代点 " + EXTENDED_ACTIONS.length
                + " 类 · 扫描节流 " + FEED_SCAN_THROTTLE_MS + "ms(P2 " + P2_SCAN_THROTTLE_MS
                + "ms) + 节点预算 " + FEED_NODE_BUDGET + "(原生直查主路径·DFS兜底)";
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
