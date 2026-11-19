package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.NodeView;

/**
 * 信息流广告卫士（v6.2.0 首发·v7.1.0 移植·v7.4.0 十三轮真机实锤三路根治）——
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
 *    即出手（李跳跳式）；非 armed 依旧零动作。
 * 用户实锤（第十三轮原话·v7.4.0）：其他 app 内的广告还是主动关闭不了（
 * 附 7 张真机截图 + 同窗上传的引擎日志 #79/#80/#81）——三路真机证据链：
 *  · 证据A（日志 #79 20:15:23 候选样本实锤）：微信朋友圈「关闭该广告的
 *    原因」原因面板主按钮是「直接关闭」——旧中性项表里【根本没有这词】，
 *    面板就算被拉起也永远收不掉。修复①：「直接关闭」置顶收编（含 armed
 *    P2 与新增 P0 直通两路）；
 *  · 证据B（日志 #79/#80 全程【零】「🛡 信息流广告 P1」行 + 20:15:00
 *    「本窗无披露角标」实锤）：P1 角标识别在真机微信上零开火——且
 *    maybeFeedScan 只挂在 TYPE_WINDOW_CONTENT_CHANGED 上，用户停下刷屏
 *    对着广告看时【零事件 = 零扫描】（截图 20:15~20:17 广告静止在屏 2
 *    分钟，引擎全程盲）。修复②：服务侧新增 2.5s 空闲巡检（事件静默期照
 *    扫）+ P0 面板直通（面板特征在场即点「直接关闭」——不再依赖 P1 开
 *    火，角标识别失败也能收）；
 *  · 证据C（截图2/3 + 日志 #80 20:16:55「已学习：market 中你手动点了
 *    「关闭」」实锤）：中关村在线 ZOL / 小米应用商店等 App 的信息流卡片
 *    广告自带「来自广告推荐」披露 + 卡片角上「×/关闭」直关钮——真机上
 *    是【用户自己手动点关】（引擎只学到位置），且这些 App 根本不在 18 款
 *    名单内零扫描。修复③：名单 18→20 款（+ZOL/+小米应用商店）+ 新增
 *    P1X 卡片直关路（「×/关闭」精确等值小形态 + 同窗广告上下文证据 +
 *    垂直同行近距门 → 直点即关，绝不裸点）。
 *
 * 场景（与开屏广告的本质差异）：开屏广告是「全屏整窗 + 独立倒计时键」——
 * 跳过即离开；信息流广告（朋友圈就业广告/ZOL 评论区推荐卡/应用商店推
 * 广卡）是「嵌在用户正在浏览的内容流里的一张卡片」——没有跳过键，只有
 * 卡片角落的披露角标（「广告/就业/来自广告推荐」）与角标背后的关闭菜单
 * 或卡片角上的「×」直关钮。直接点卡片任何位置都是点进广告主体（误触事
 * 故面），安全路径是三段式：
 *
 *  P0 面板直通（v7.4.0 新增）：「关闭该广告的原因」面板在场 → 点面板
 *    主按钮「直接关闭」收本条——面板特征（标题/原因族 + 直接关闭）是
 *    广告反馈面板独有铁证，与 P1 是否开火无关（角标识别失败也收得掉）；
 *  P1X 卡片直关（v7.4.0 新增）：卡片角「×/关闭」直关钮 + 同窗广告上下
 *    文证据 → 直点即关（真机 market/ZOL 卡片形态）；
 *  P1 点角标 → P2 收菜单（既有两段式保持）：点卡片角上的「广告/就业」
 *    披露角标 → 拉起关闭菜单 → 点「直接关闭/不感兴趣」中性项收起卡片。
 *
 * 四道安全闸（宁漏勿误红线——绝不点广告主体）保持 + v7.4.0 两道新闸：
 *  ① 角标词精确等值：trim 后 equals——招聘广告正文满篇「广告/招聘」字样
 *     contains 即误点广告主体，绝不 contains；带纯符号尾（广告×/广告·/
 *     就业›）的披露徽标单独收编；>6 字必是正文；
 *  ② 角标面积门：宽 ≤35% 屏宽 × 高 ≤8% 屏高（屏幕未知 → 420×126 像素
 *     兜底）——广告主体大图/大容器绝不可能过闸（绝不点广告本体的几何兜底）；
 *  ③ P2 只点中性关闭项：精确等值（直接关闭/关闭该广告/关闭此广告/屏蔽此
 *     广告/关闭广告/不感兴趣）——屏蔽作者/举报/反馈类扩展动作绝不代点
 *     （替用户做超出本条的决策=越权）；且须 P1 出手 8s 指纹窗内（用户自己
 *     长按的「不感兴趣」菜单绝不对抗）；
 *  ④ 名单门：20 个信息流 App 名单内才扫（微信/微博/头条/抖音/快手/
 *     小红书/B站/知乎/ZOL/小米应用商店等）；名单外 App 零 feed 扫描（开
 *     屏/弹窗/插屏路径覆盖——功耗红线不破）；微信窗级门只认 sns 族窗
 *     （聊天主界面零扫描红线保持）；
 *  ⑤ P0 面板证据闸（v7.4.0）：非 armed 直通须「直接关闭」精确等值 +
 *     （面板标题精确等值「关闭该广告的原因/关闭此广告的原因」或原因族
 *     contains 证据「相关广告/相同素材/类似产品」）——裸「关闭」绝不在
 *     P0 路上代点；面板在屏正是用户意图（13 轮实锤：用户要的就是广告消失）；
 *  ⑥ P1X 上下文闸（v7.4.0）：直关钮「×/✕/✖/╳/⨯/关闭」精确等值 + 小
 *     形态（≤35%屏×≤8%屏）+ 同窗广告上下文证据（来自广告推荐/广告推荐/
 *     赞助 contains 或合法披露角标在场）+ 垂直同行近距门（|Δy|≤30% 屏高
 *     ——广告卡同卡同排铁证）——无上下文绝不裸点「关闭」（#80 20:16:46
 *     PhotoViewV16 相册关闭钮就是反例：零上下文 → 零出手）。
 *
 * 功耗双闸：扫描节流 FEED_SCAN_THROTTLE_MS=1.2s + 节点预算
 * FEED_NODE_BUDGET=300（与开屏路径互不挤占；feed 窗口常驻不重置）；
 * v7.4.0 巡检 FEED_PATROL_MS=2.5s（事件静默期空闲补扫——只在信息流窗
 * 在场时存活，离窗即停）。
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
    /** v7.4.0 空闲巡检节流：事件静默期（用户停下刷屏盯着广告看）照扫——
 * 真机日志 #79/#80 实锤：maybeFeedScan 只挂 content-changed，用户静止
 * 2 分钟零事件零扫描（截图 20:15~20:17 广告一直在屏）。2.5s 一扫，只在
 * 信息流窗在场时存活（离窗/切包即停——功耗红线不破）。 */
    public static final long FEED_PATROL_MS = 2500;
    /** v7.4.0 P0 面板直通上下文窗：90s 内进过信息流窗 + 覆盖窗（Dialog/
 * FrameLayout 类）→ 允许 P0 面板特征直查（不经过名单窗门——原因面板
 * 本身就是 Dialog 覆盖窗，isFeedWindow 会拒之门外）。 */
    public static final long P0_FEED_CTX_MS = 90000;
    /** 角标宽上限（≤35% 屏宽——广告主体大图绝不可能过闸）。 */
    public static final double BADGE_W_MAX = 0.35;
    /** 角标高上限（≤8% 屏高）。 */
    public static final double BADGE_H_MAX = 0.08;
    /** 屏幕未知时的像素兜底（420×126 = 35%×1200 宽 / 8%×1580 高 的保守化整数）。 */
    public static final int BADGE_FALLBACK_W_PX = 420;
    public static final int BADGE_FALLBACK_H_PX = 126;
    /** 角标词字符上限（>6 字必是正文）。 */
    public static final int BADGE_MAX_CHARS = 6;
    /** v7.4.0 P1X 同行近距门：直关钮与上下文证据节点垂直距离 ≤30% 屏高
     * （同卡同排铁证——远距离的「关闭」绝不裸点）。 */
    public static final double P1X_NEAR_Y_MAX = 0.30;

    /* ══ 决策动作名（服务侧 Decision.reason 用——execute 走 feed 终闸）══ */
    public static final String ACTION_P1_BADGE = "feed_p1_badge";
    public static final String ACTION_P2_CLOSE = "feed_p2_close";
    /** v7.4.0 P0：广告原因面板「直接关闭」直通（面板特征在场即收——
     * 不依赖 P1 开火，角标识别失败也收得掉）。 */
    public static final String ACTION_P0_PANEL_CLOSE = "feed_p0_panel_close";
    /** v7.4.0 P1X：信息流广告卡片角上「×/关闭」直关钮直点（真机 market/
     * ZOL 卡片形态——上下文证据在场才出手）。 */
    public static final String ACTION_P1X_CLOSE = "feed_p1x_close";

    /* ══ 名单门（闸④）：20 个信息流 App（v7.4.0 +ZOL 中关村在线 +小米应用商店）══
     * 微信加窗级门（只认 sns 族窗——朋友圈 SnsTimeLineUI/ImproveSnsTimeline
     * UI 族（真机日志 #79 实锤窗类）；聊天主界面 LauncherUI 零扫描红线由
     * isChatMainUi 另行保持，AppBrandUI 小程序容器由 MiniProgramGuard 更早
     * 拦截）；其余 19 款任意窗（角标语义+形态门兜底
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
            "com.zol.android",                   // v7.4.0 中关村在线（截图2/3 实锤：评论区信息流卡片广告）
            "com.xiaomi.market",                 // v7.4.0 小米应用商店（日志#80 实锤：用户手动点「关闭」的推荳卡）
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
     * 朋友圈文案）两族真机文案。
     * v7.4.0 「直接关闭」置顶收编（第十三轮真机日志 #79 20:15:23 候选样本
     * 实锤：微信「关闭该广告的原因」面板主按钮就是「直接关闭」——旧表缺
     * 此词 = 面板拉起了也永远收不掉 = 「全都没拦截」直接死因之一）。 */
    static final String[] NEUTRAL_CLOSE_ITEMS = {
            "直接关闭", "关闭该广告", "关闭此广告", "屏蔽此广告", "屏蔽此推广", "关闭广告", "不感兴趣", "减少此类广告",
    };

    /** v7.2.0 中性关闭词表 public 视图（服务侧 P2 原生直查用）。 */
    public static String[] neutralCloseWords() {
        return NEUTRAL_CLOSE_ITEMS.clone();
    }
    /** 菜单强特征（真·广告关闭菜单独有文案）。v7.4.0「直接关闭」置顶。 */
    static final String[] NEUTRAL_STRONG_ITEMS = {
            "直接关闭", "关闭该广告", "关闭此广告", "屏蔽此广告", "屏蔽此推广",
    };
    /** 扩展动作（绝不代点——替用户做超出「关闭本条」的决策=越权）。
     * v7.4.0 收编「复制」（真机 ZOL 反馈菜单「点击复制广告ID」行——复制类
     * 动作绝不代点）。 */
    static final String[] EXTENDED_ACTIONS = {
            "屏蔽作者", "屏蔽该作者", "不看", "拉黑", "举报", "反馈", "投诉", "复制",
    };

    /* ══ v7.4.0 P0 面板直通词表（闸⑤）══
     * 真机证据（截图1/6/7 + 日志 #79 20:15:23）：微信「关闭该广告的原因」
     * 面板 = 标题「关闭该广告的原因」 + 副文案「反馈后将优化广告推荐」 +
     * 主按钮「直接关闭」 + 原因行（汽车厂商相关广告/不喜欢该品牌/重复收到
     * 相同素材/其他原因/不喜欢类似产品）。标题/原因族是广告原因面板独有
     * 铁证——标题精确等值、原因族 contains 只作【在场证据】绝不点击。 */
    static final String[] PANEL_TITLE_WORDS = {
            "关闭该广告的原因", "关闭此广告的原因",
    };
    /** P0 原因族证据词（contains——真机文案「汽车厂商相关广告/重复收到
     * 相同素材/不喜欢类似产品」共享的族尾）。 */
    static final String[] PANEL_REASON_CONTAINS = {
            "相关广告", "相同素材", "类似产品",
    };
    /** P0 原生直查词表（服务侧 findAccessibilityNodeInfosByText 用——「直接
     * 关闭」按钮 + 强项 + 标题族 + 原因族一把直查，命中交语义闸逐候选判定）。 */
    public static final String[] P0_SEARCH_WORDS = {
            "直接关闭", "关闭该广告", "关闭此广告", "相关广告", "相同素材", "类似产品",
    };

    /* ══ v7.4.0 P1X 卡片直关词表（闸⑥）══
     * 直关钮词（精确等值 + 小形态）：「×」家族符号（U+00D7/U+2715/U+2716/
     * U+2573/U+2A2F——卡片角上图标钮的无障碍文案/描述真机常态）与「关闭」
     * （日志 #80 20:16:55 实锤：market 卡片直关钮的无障碍文案就是「关闭」）。
     * 拉丁字母 X 不收（正文/昵称误而险），纯符号家族 + 关闭——零歧义。
     * 广告上下文词（contains——只作「同窗有广告」在场证据，绝不点击）：
     * 「来自广告推荐」真机文案（截图2 实锤）为首，广告推荐/赞助为族。 */
    public static final String[] CLOSE_ICON_WORDS = {
            "×", "✕", "✖", "╳", "⨯", "关闭",
    };
    /** P1X 广告上下文证据词（contains——绝不点击，只证明「同窗有广告」）。 */
    public static final String[] AD_CONTEXT_WORDS = {
            "来自广告推荐", "广告推荐", "赞助",
    };

    /* ══ 决策结果（服务侧薄接线消费）══ */
    public static final class FeedDecision {
        public String action;    // ACTION_P1_BADGE / ACTION_P2_CLOSE
        public String label;     // "广告" / "关闭该广告" …
        public int[] tapBounds;  // 【角标/菜单项自身】bounds——绝不广告主体
        public NodeView node;    // 目标节点（NodeAdapter 包装）
    }

    /* ───────── 闸④：名单门 ───────── */

    /** 是否信息流 App（20 款名单——名单外 App 零 feed 扫描）。 */
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
     * 微信只认 sns 族窗（聊天主界面零扫描红线保持）；其余 19 款任意窗
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

    /* ───────── v7.4.0 P0 面板直通（闸⑤——微信「关闭该广告的原因」真机面板） ─────────
     * 不依赖 P1 开火：面板特征在场（「直接关闭」主按钮 + 标题/原因族证据）
     * 即点主按钮收本条——P1 角标识别在真机微信上零开火（日志 #79/#80 全程
     * 零「🛡 P1」行实锤），面板直通是「其他 app 内的广告主动关闭不了」的
     * 兜底闭环。armed=true（我们 P1/P1X 刚拉起的面板）时「直接关闭」单独
     * 即可；非 armed（用户手拉的面板）须标题精确等值或原因族 contains
     * 在场证据——裸「关闭」绝不在 P0 路上代点（宁可漏拦绝不误触）。 */

    /** 是否 P0 面板标题词（精确等值——「关闭该广告的原因」族）。 */
    public static boolean isPanelTitleWord(String label) {
        if (label == null) return false;
        String t = label.trim();
        for (String k : PANEL_TITLE_WORDS) {
            if (t.equals(k)) return true;
        }
        return false;
    }

    /** 是否 P0 直关钮词（精确等值——「×」符号家族与「关闭」）。 */
    public static boolean isCloseIconWord(String label) {
        if (label == null) return false;
        String t = label.trim();
        for (String k : CLOSE_ICON_WORDS) {
            if (t.equals(k)) return true;
        }
        return false;
    }

    /**
     * P0 候选式决策（v7.4.0 面板直通）：候选里找「直接关闭」主按钮（精确
     * 等值 + 小形态）→ 面板证据在场（armed 或 标题/原因族/强项）即出手。
     * 返回 null = 面板不在场（正常页面零命中——宁漏勿误）。
     * tapBounds = 主按钮【自身】bounds——绝不广告主体。
     */
    public static FeedDecision decideP0FromCandidates(java.util.List<NodeView> candidates,
                                                       int screenW, int screenH, boolean armed) {
        if (candidates == null) return null;
        NodeView btn = null;
        boolean evidence = armed;
        for (NodeView n : candidates) {
            if (n == null) continue;
            try {
                if (!n.visible()) continue;
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                if (label.length() == 0) continue;
                String t = label.trim();
                if ("直接关闭".equals(t)) {
                    /* 主按钮：唯一可点目标——形态闸（≤35% 屏宽 × ≤8% 屏高）；
                     * 巨横幅 CTA 绝不进面板候选。 */
                    int[] b = n.bounds();
                    if (b == null || b.length < 4 || b[2] <= b[0] || b[3] <= b[1]) continue;
                    int w = b[2] - b[0], h = b[3] - b[1];
                    boolean formOk;
                    if (screenW > 0 && screenH > 0) {
                        formOk = w <= (int) (screenW * BADGE_W_MAX) && h <= (int) (screenH * BADGE_H_MAX);
                    } else {
                        formOk = w <= BADGE_FALLBACK_W_PX && h <= BADGE_FALLBACK_H_PX;
                    }
                    if (formOk && btn == null) btn = n; // 主按钮（首个过闸者）
                } else if (!evidence) {
                    /* 证据节点（标题/原因族/强项）：只作在场证据绝不点击——
                     * 无形态闸（面板标题/原因行是全宽行，形态闸会把证据误杀）。 */
                    if (isPanelTitleWord(t)) {
                        evidence = true; // 闸⑤：标题精确等值（面板独有文案）
                    } else {
                        for (String rk : PANEL_REASON_CONTAINS) {
                            if (t.contains(rk)) { evidence = true; break; } // 原因族 contains 证据
                        }
                        if (!evidence && isNeutralStrongItem(t)) evidence = true; // 旧版强项（关闭该广告/屏蔽此推广）
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (btn == null || !evidence) return null;
        return build(ACTION_P0_PANEL_CLOSE, btn, screenW, screenH);
    }

    /* ───────── v7.4.0 P1X 卡片直关（闸⑥——market/ZOL「来自广告推荐」卡片） ─────────
     * 信息流卡片角上的「×/关闭」直关钮直点即关（真机日志 #80 20:16:55 实锤：
     * 用户手动点「关闭」的地方引擎本该先点）。三重闸：①直关钮词精确等值 +
     * 小形态（≤35%屏×≤8%屏）；②同窗广告上下文证据（AD_CONTEXT_WORDS
     * contains 或合法披露角标在场——绝无上下文绝不裸点「关闭」：#80
     * 20:16:46 PhotoViewV16 相册关闭钮就是反例，零上下文零出手）；③同行
     * 近距门（|Δy| ≤30% 屏高——同卡同排铁证，远距「关闭」绝不对号）。 */

    /**
     * P1X 候选式决策：closeCands 直关钮候选 + contextCands 上下文候选 →
     * 双闸全过才出手直点直关钮。返回 null = 无合法直关（宁漏勿误）。
     * tapBounds = 直关钮【自身】bounds——绝不广告主体。
     */
    public static FeedDecision decideP1XFromCandidates(java.util.List<NodeView> closeCands,
                                                        java.util.List<NodeView> contextCands,
                                                        int screenW, int screenH) {
        if (closeCands == null || contextCands == null || closeCands.isEmpty()
                || contextCands.isEmpty()) return null;
        /* 闸②：同窗广告上下文证据（contains 或合法披露角标）——记证据节点
         * 坐标用于闸③同行近距门。 */
        int ctxY = -1;
        boolean ctxOk = false;
        for (NodeView n : contextCands) {
            if (n == null) continue;
            try {
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                if (label.length() == 0) continue;
                int[] b = n.bounds();
                if (b == null || b.length < 4 || b[3] <= b[1]) continue;
                boolean hit = false;
                for (String k : AD_CONTEXT_WORDS) {
                    if (label.contains(k)) { hit = true; break; }
                }
                if (!hit && isDisclosureBadge(label.trim(), b, screenW, screenH)) hit = true;
                if (hit) {
                    ctxOk = true;
                    int y = (b[1] + b[3]) / 2;
                    if (ctxY < 0 || y < ctxY) ctxY = y; // 取最靠上的证据（卡片头一行）
                }
            } catch (Throwable ignored) {
            }
        }
        if (!ctxOk) return null; // 绝无上下文绝不裸点「关闭」（相册/对话框反例闸）
        /* 闸①+③：直关钮词精确等值 + 小形态 + 同行近距。 */
        int nearLimit = (int) ((screenH > 0 ? screenH : 2400) * P1X_NEAR_Y_MAX);
        for (NodeView n : closeCands) {
            if (n == null) continue;
            try {
                if (!n.visible()) continue;
                String text = nz(n.text());
                String desc = nz(n.desc());
                String label = text.length() > 0 ? text : desc;
                if (label.length() == 0) continue;
                if (!isCloseIconWord(label)) continue; // 精确等值（× 家族/关闭）
                int[] b = n.bounds();
                if (b == null || b.length < 4 || b[2] <= b[0] || b[3] <= b[1]) continue;
                int w = b[2] - b[0], h = b[3] - b[1];
                boolean formOk;
                if (screenW > 0 && screenH > 0) {
                    formOk = w <= (int) (screenW * BADGE_W_MAX) && h <= (int) (screenH * BADGE_H_MAX);
                } else {
                    formOk = w <= BADGE_FALLBACK_W_PX && h <= BADGE_FALLBACK_H_PX;
                }
                if (!formOk) continue; // 巨大容器（广告主体）绝不进直关候选
                int cy = (b[1] + b[3]) / 2;
                if (Math.abs(cy - ctxY) > nearLimit) continue; // 同行近距门（同卡同排铁证）
                return build(ACTION_P1X_CLOSE, n, screenW, screenH);
            } catch (Throwable ignored) {
            }
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

    /** 名单/规则统计（启动日志用）。v7.4.0：P0 直通/P1X 直关/巡检三路在场。 */
    public static String stats() {
        return "信息流 App 名单 " + FEED_APPS.length + " 款 · 角标词 " + BADGE_WORDS.length
                + " 族(≤" + BADGE_MAX_CHARS + "字,宽≤" + (int) (BADGE_W_MAX * 100) + "%屏×高≤"
                + (int) (BADGE_H_MAX * 100) + "%屏) · 中性关闭 " + NEUTRAL_CLOSE_ITEMS.length
                + " 项(直接关闭置顶·强特征 " + NEUTRAL_STRONG_ITEMS.length + " + 弱 "
                + (NEUTRAL_CLOSE_ITEMS.length - NEUTRAL_STRONG_ITEMS.length)
                + " · armed≥1即出手) · P0 面板直通(直接关闭+标题/原因族证据闸) · P1X 卡片直关("
                + CLOSE_ICON_WORDS.length + " 直关词+广告上下文闸+同行近距闸) · 扩展动作绝不代点 "
                + EXTENDED_ACTIONS.length + " 类 · 扫描节流 " + FEED_SCAN_THROTTLE_MS
                + "ms(P2 " + P2_SCAN_THROTTLE_MS + "ms) + 巡检 " + FEED_PATROL_MS
                + "ms(事件静默期照扫) + 节点预算 " + FEED_NODE_BUDGET + "(原生直查主路径·DFS兜底)";
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
