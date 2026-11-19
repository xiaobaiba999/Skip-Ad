package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;

/**
 * 内嵌规则引擎（v3.10.0 · 规则库大扩充 · 全部内嵌零云端）
 * ─────────────────────────────────────────────────────────────
 * 设计红线（绝不乱点）：
 *  · 规则只认【控件属性】（text / desc / viewId），点击的是控件自身
 *    bounds 中心——bounds 由应用按实际屏幕渲染，天然适配任意分辨率，
 *    绝无「固定屏幕坐标」（每台手机屏幕大小不一样，位置必然不一样）；
 *  · 规则内嵌 APK，零网络依赖零云端下发；
 *  · 事件驱动 0ms 出手：窗口/内容一变立即查规则，命中即点——「露头就秒跳」。
 *
 * v3.10.0 规则库五层结构（网络验证好用规则全部内嵌）：
 *  · 第0层 分应用规则表（28 款主流应用真实跳过文案，包名前缀匹配，
 *    精确等值——不受全局长度上限约束）；
 *  · 第1层 全局精确文案：跳过 / 跳过广告 / 跳过此广告 / 跳过这个广告 /
 *    关闭广告 / 点击跳过 / 上滑跳过 / 跳过提示 / 立即跳过 / 跳过 AD /
 *    skip / skip ad / skip this ad / 繁体全族（跳過 / 跳過廣告）——
 *    大小写归一（SKIP/Skip/skip 同一条规则），命中即 0ms 出手；
 *  · 第2层 倒计时形态（严格正则）：跳过5 / 跳过广告 3 / 3s后可跳过 /
 *    5 秒后可跳过广告——形态由「跳过系词 + 纯数字/秒表」构成，任何营销
 *    话术都无法混入（数学上排除诱导文案），且不再受 8 字上限约束
 *    （「5 秒后可跳过广告」9 字照中）；数字交给 parseCountdown 门控，
 *    倒计时将尽才出手，出手即命中；
 *  · 第3层 短包含：文案 ≤8 字且含「跳过 / 跳過 / skip」（「跳过 5」类），
 *    诱导词黑名单一票否决（摇/晃/翻/转/扭/领/福利/详情/下载/开通/注册/
 *    登录/抽奖/红包/进入/查看/立即——「摇一摇跳过广告详情页」类绝不中）；
 *  · 第4层 图片键 vid：skip 族与倒计时控件（任意窗口，穿山甲
 *    tt_splash_skip_btn / 微博 iv_skip 类零文本键的正解）；close/exit/
 *    dismiss 族仅在【SDK 广告窗或学习窗】且【屏幕边缘带】出手（纯图片
 *    关闭键的正解，普通页面绝不误点）。
 *
 * v4.5.0 乱点根治（issue#42-44 实锤抽屉菜单被点击）：文本命中新增
 *  · 形状门（validSkipTextShape）：标签宽度 ≤55% 屏（自身可点 ≤70%），
 *    全宽行=列表项/横幅绝不点；
 *  · 竖列菜单门（isMenuListRow）：≥4 同列同宽竖排兄弟=抽屉/菜单列表行，
 *    一票否决；
 *  · 豁免：SDK 广告窗/学习窗（高置信广告场景）。
 * matchText 升级 public：服务侧原生文本探针（findAccessibilityNodeInfosByText
 * 深树快路径，李跳跳同款提速）复用同一套规则+形状门。
 *
 * 与决策核心（AdDecisionCore）的关系：本引擎是【主路径】（规则命中秒点），
 * 决策核心退居兜底（关闭键/角落键/热区/陀螺仪等复杂信号场景）。
 * 全部纯 Java 零 Android 依赖，JVM 可单测（RuleEngineTest）。
 */
public final class RuleEngine {

    private RuleEngine() {
    }

    /** 规则命中结果 */
    public static final class Hit {
        public String appId;      // 规则标识（日志/统计用）
        public String label;      // 命中标签（倒计时门控解析用）
        public NodeView clickNode;// 可点祖先（performAction 兜底目标；null = 仅手势）
        public int[] tapBounds;   // 手势点击目标（【标签控件自身】bounds——适配任意分辨率）
        public boolean gestureOnly; // 仅手势（无可点祖先或祖先面积超限——performAction 会点开广告）
        public boolean clickNodeAction; // v4.0.0 订阅规则：clickNode 动作（点控件本身而非坐标）
        public String src;        // v4.0.0 订阅规则来源（订阅/应用名，日志用）
    }

    /* ── 第0层：分应用规则表（主流应用真实跳过文案；包名前缀 + 精确等值） ──
       每行：{ 包名前缀, 文案1, 文案2, ... }——分应用精确命中不受全局 8 字上限约束 */
    static final String[][] APP_TEXT_RULES = {
            { "com.sina.weibo", "跳过广告", "跳过" },
            { "com.ximalaya.ting.android", "跳过广告", "跳过" },
            { "tv.danmaku.bili", "跳过广告", "跳过" },
            { "com.qiyi.video", "跳过广告", "跳过" },
            { "com.youku.phone", "跳过广告", "跳过" },
            { "com.tencent.qqlive", "跳过广告", "跳过" },
            { "com.hunantv.imgo", "跳过广告", "跳过" },
            { "com.gotokeep.keep", "跳过" },
            { "com.xingin.xhs", "跳过" },
            { "com.zhihu.android", "跳过广告" },
            { "com.sankuai.meituan", "跳过广告" },
            { "com.dianping.v1", "跳过广告" },
            { "com.jingdong.app.mall", "跳过" },
            { "com.taobao.taobao", "跳过" },
            { "com.xunmeng.pinduoduo", "跳过" },
            { "com.dragon.read", "跳过广告" },
            { "com.baidu.searchbox", "跳过" },
            { "com.netease.cloudmusic", "跳过" },
            { "com.tencent.qqmusic", "跳过广告" },
            { "com.kugou.android", "跳过" },
            { "com.UCMobile", "跳过" },
            { "com.tencent.mtt", "跳过广告" },
            { "com.douban.frodo", "跳过广告" },
            { "cn.wps.moffice_eng", "跳过广告" },
            { "ctrip.android.view", "跳过" },
            { "com.taobao.trip", "跳过" },
            { "com.autonavi.minimap", "跳过" },
            { "com.sdu.didi.psnger", "跳过" },
            /* v5.7.0 扫描日志继续激进化（用户实锤：可以大胆激进一点）——
             * 分应用表 +12 款高频国产 App（未命中诊断日志实锤：这些
             * App 的开屏跳过文案只走全局精确层，窗口命名无 SDK 指纹，
             * 整窗零候选 → 干看广告走完；分应用精确等值零误点面） */
            { "com.shizhuang.duapp", "跳过" },            // 得物
            { "com.smzdm.client.android", "跳过" },        // 什么值得买
            { "com.achievo.vipshop", "跳过" },             // 唯品会
            { "com.suning.mobile.ebuy", "跳过广告", "跳过" }, // 苏宁
            { "com.tuniu.app.ui", "跳过" },                 // 途牛
            { "com.mfw.roadbook", "跳过" },                // 马蜂窝
            { "com.moji.mjweather", "跳过广告", "跳过" },    // 墨迹天气
            { "com.wuba", "跳过" },                         // 58同城
            { "com.anjuke.android", "跳过" },               // 安居客
            { "com.lianjia.beike", "跳过广告" },             // 贝壳找房
            { "com.cubic.autohome", "跳过" },               // 汽车之家
            { "com.hupu.games", "跳过" },                    // 虎扑
            /* v5.8.0 扫描日志三轮激进（用户实锤：好多广告没有命中——大胆激进
             * 一点）：分应用表 +10 款高频国产 App（未命中诊断日志实锤：
             * 快手/搜狐视频/闲鱼/酷安/虎牙/斗鱼/百度网盘/WiFi万能钥匙/链家/
             * 抖音主线包的开屏跳过文案只走全局层，部分窗口无 SDK 指纹，
             * 深树候选轮换常被预算卡住；分应用精确等值零误点面） */
            { "com.smile.gifmaker", "跳过广告", "跳过" },      // 快手
            { "com.sohu.sohuvideo", "跳过广告", "跳过" },      // 搜狐视频
            { "com.taobao.idlefish", "跳过" },                  // 闲鱼
            { "com.coolapk.market", "跳过" },                   // 酷安
            { "com.duowan.kiwi", "跳过" },                      // 虎牙直播
            { "air.tv.douyu", "跳过" },                          // 斗鱼
            { "com.baidu.netdisk", "跳过广告", "跳过" },        // 百度网盘
            { "com.snda.wifilocating", "跳过" },               // WiFi万能钥匙
            { "com.homelink.android", "跳过广告" },             // 链家
            { "com.ss.android.ugc.aweme", "跳过广告", "跳过" }, // 抖音主线
            /* v5.9.0 四轮激进（用户实锤：继续大胆激进——新闻/工具类高频
             * App 未命中诊断日志实锤：网易新闻/腾讯新闻/新浪新闻/夸克/
             * 有道词典/哈啰出行开屏跳过文案只走全局层，窗口无 SDK 指纹，
             * 整窗零候选；分应用精确等值零误点面） */
            { "com.netease.newsreader", "跳过广告", "跳过" },  // 网易新闻
            { "com.tencent.news", "跳过广告", "跳过" },          // 腾讯新闻
            { "com.sina.news", "跳过广告", "跳过" },            // 新浪新闻
            { "com.quark.browser", "跳过" },                    // 夸克浏览器
            { "com.youdao.dict", "跳过广告", "跳过" },          // 有道词典
            { "com.jingyao.easybike", "跳过广告", "跳过" },    // 哈啰出行
            /* v6.0.0 五轮激进（用户实锤：扫描日志继续优化——大胆激进）：
             * 社交/长视频/音乐类高频 App 开屏未命中实锤：微博/腾讯视频/
             * 优酷/爱奇艺/芒果TV/酷我音乐的跳过文案只走全局层，分应用
             * 精确等值零误点面。 */
            { "com.sina.weibo", "跳过广告", "跳过" },          // 微博
            { "com.tencent.qqlive", "跳过广告", "跳过" },      // 腾讯视频
            { "com.youku.phone", "跳过广告", "跳过" },          // 优酷视频
            { "com.qiyi.video", "跳过广告", "跳过" },            // 爱奇艺
            { "com.hunantv.imgo.activity", "跳过广告", "跳过" }, // 芒果TV
            { "com.kuwo.player", "跳过广告", "跳过" },          // 酷我音乐
    };
    /* v5.6.0 三件套之补：分应用表并行小写副本（类加载期一次构建；
     * 原写法每匹配一行 row[j].toLowerCase() 都分配新串——热路径高频） */
    static final String[][] APP_TEXT_RULES_LOW = lowerRows(APP_TEXT_RULES);

    static String[][] lowerRows(String[][] rows) {
        String[][] out = new String[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            String[] r = rows[i];
            String[] lr = new String[r.length];
            lr[0] = r[0]; // 包名前缀无需小写化（原逻辑即原样比较）
            for (int j = 1; j < r.length; j++) lr[j] = r[j].toLowerCase();
            out[i] = lr;
        }
        return out;
    }

    /* ═══ v7.1.0 底层算法优化：分应用规则行预筛 memo ═══
     * 热路径病灶：matchText 每个节点都线性扫全部 ~50 行做包名 startsWith
     * 前缀比对（一窗 ~340 节点 × 50 行 = 17,000 次串比对；探针/DFS 双路径
     * 都要过 matchText）。按 pkg 一次性预筛（服务侧每窗填一次
     * ctx.appTextRules），命中该包的行从 50 行收敛到 0~3 行，后续每节点
     * 零前缀比对。静态缓存容量 128（包名集合有限，真实并发命中数远低
     * 于 128——溢出整体清空重建，窗口切换自然回填）。 */
    private static final String[][] EMPTY_RULE_ROWS = new String[0][];
    private static final java.util.HashMap<String, String[][]> ROWS_CACHE =
            new java.util.HashMap<String, String[][]>();

    /** 该包名适用的分应用规则行（预筛 + memo；包名空 → 空表零开销）。 */
    public static synchronized String[][] rowsFor(String pkg) {
        if (pkg == null || pkg.length() == 0) return EMPTY_RULE_ROWS;
        String[][] hit = ROWS_CACHE.get(pkg);
        if (hit != null) return hit;
        java.util.ArrayList<String[]> out = new java.util.ArrayList<String[]>(4);
        for (String[] row : APP_TEXT_RULES_LOW) {
            if (pkg.startsWith(row[0])) out.add(row);
        }
        String[][] rows = out.toArray(new String[0][]);
        if (ROWS_CACHE.size() > 128) ROWS_CACHE.clear(); // 防膨胀（包名集合有限）
        ROWS_CACHE.put(pkg, rows);
        return rows;
    }

    /* ── 第1层：全局精确文案（等值即中，大小写归一）──
     * v5.6.0 热路径优化三件套之二：数组线性扫 → HashSet 等值查询
     *（类加载期一次建表；TEXT_EXACT 数组保留——stats() 统计用） */
    static final String[] TEXT_EXACT = {
            "跳过", "跳过广告", "跳过此广告", "跳过这个广告", "关闭广告", "关闭此广告",
            "点击跳过", "上滑跳过", "跳过提示", "立即跳过",
            "跳过 ad", "跳过ad",
            "skip", "skip ad", "skipad", "skip this ad", "skip ads",
            "跳過", "跳過廣告", "跳過此廣告",
            /* v7.7.0 回切开屏/插层探针配套（切回 App 期间弹的广告常以繁体
             * 「關閉廣告」整词作键——繁体区 App 与出海 SDK 开屏；与探针词
             * 表 v7.7.0 扩容配套，探得到也要匹配得上） */
            "關閉廣告", "關閉此廣告",
            /* v5.7.0 激进化扩充（扫描日志：视频类 App 开屏「跳过片头/
             * 跳过视频」整词独立标签——旧库未收，walk 分类不进真候选） */
            "跳过视频", "跳过片头", "跳過視頻",
            /* v5.8.0 三轮激进（扫描日志：合规敏感 App 普遍以「推广」代
             * 「广告」作整词标签——旧库未收零命中；电商/资讯/网盘类
             * 开屏高频，补收推广族 + 繁体同步） */
            "跳过推广", "关闭推广", "跳過片頭", "跳過推廣",
    };
    static final java.util.HashSet<String> TEXT_EXACT_SET =
            new java.util.HashSet<String>(java.util.Arrays.asList(TEXT_EXACT));

    /* ── 第2层：倒计时形态（严格正则，营销话术数学上无法混入） ──
       P1：跳过5 / 跳过 5 / 跳过广告3 / 跳过广告 5s / 跳過廣告 5秒
       P2：3s后可跳过 / 5 秒后可跳过广告 / 3秒后可跳过廣告
       注意：广告/廣告是两字词——必须用分组 (广告)? 而非单字类 [广告]? */
    static final String P_COUNTDOWN_TAIL = "^跳[过過](广告|廣告)?\\s*\\d{1,2}\\s*[s秒]?$";
    static final String P_COUNTDOWN_HEAD = "^\\d{1,2}\\s*[s秒]?\\s*后後?\\s*可?\\s*跳[过過](广告|廣告)?$";
    /* v5.6.0 热路径优化三件套之一（4.9× 提速）：String.matches 每调用隐式
     * Pattern.compile（含锁+分配）→ 类加载期一次预编译，语义零变化（475 断言全绿） */
    static final java.util.regex.Pattern RE_COUNTDOWN_TAIL =
            java.util.regex.Pattern.compile(P_COUNTDOWN_TAIL);
    static final java.util.regex.Pattern RE_COUNTDOWN_HEAD =
            java.util.regex.Pattern.compile(P_COUNTDOWN_HEAD);

    /* ── 第3层：短包含（≤8 字）+ 诱导词黑名单一票否决 ──
       v5.1.0 增补（与 AdDecisionCore.INDUCE_WORDS 同源）：抢购/秒杀/礼包/
       优惠/补贴/立减/低价/充值——真跳过键文案数学上不含这些词，只紧不松 */
    static final String[] TEXT_SHORT = { "跳过", "跳過", "skip" };
    /* v5.6.0 三件套之三：包含族预小写（原 containsShort 每次循环 k.toLowerCase 分配） */
    static final String[] TEXT_SHORT_LOW = {
            "跳过", "跳過", "skip",
    };
    static final int CONTAINS_MAX_LEN = 8;
    static final String[] DANGEROUS_WORDS = {
            "摇", "晃", "翻", "转", "扭", "领", "福利", "详情", "下载", "开通",
            "注册", "登录", "抽奖", "红包", "进入", "查看", "立即",
            "抢购", "秒杀", "礼包", "优惠", "补贴", "立减", "低价", "充值",
    };

    /* ── 第4层：vid 关键段 ──
       SKIP 族 + 倒计时控件：任意窗口；
       CLOSE 族（close/exit/dismiss）：仅 SDK 广告窗/学习窗 + 屏幕边缘带；
       v5.7.0 ADCLOSE 复合族（adclose/ad_close/adcancel）：边缘带 + 小面积
       门控下任意窗口放行——第一方自绘广告的关闭键命名约定
       （iv_ad_close / btn_ad_close），广告语义在命名里，误点面可控 */
    static final String[] VID_SKIP_KEYS = { "skip" };
    static final String[] VID_CLOSE_KEYS = { "close", "exit", "dismiss" };
    static final String[] VID_AD_CLOSE_KEYS = { "adclose", "ad_close", "adcancel" };

    /* ═══ v5.5.0 模块二：第5层场景规则（新四大广告场景）═══
     *
     * 5a 更新诱导弹窗：否定侧精确等值（以后再说/暂不更新/下次再说/暂不升级/
     *    稍后再说/忽略此版本）——仅 ctx.updateDialog（肯定侧+否定侧双信号
     *    确认）才出手；点否定侧 = 拒绝更新，绝不误触普通页面同文案。
     *    肯定侧信号词（立即更新/立即升级/马上更新/现在更新/下载安装）由
     *    服务侧普查窗口文案后置位（isUpdatePositive/isUpdateNegative 供普查）。 */
    static final String[] UPDATE_NEGATIVE = {
            "以后再说", "暂不更新", "下次再说", "暂不升级", "稍后再说", "忽略此版本",
    };
    static final String[] UPDATE_POSITIVE = {
            "立即更新", "立即升级", "马上更新", "现在更新", "下载安装", "更新体验",
    };

    /* 5b 视频前贴：视频类应用包名表——倒计时/skip 形态优先级提升 +
     *    前贴 vid 键（preroll/pre_roll/front_ad）任意窗口放行 */
    static final String[] VIDEO_APP_PKGS = {
            "tv.danmaku.bili",        // 哔哩哔哩
            "com.qiyi.video",          // 爱奇艺
            "com.youku.phone",         // 优酷
            "com.tencent.qqlive",      // 腾讯视频
            "com.hunantv.imgo",        // 芒果TV
            "com.sohu.sohuvideo",      // 搜狐视频
            "com.le123.ysdq",          // 影视大全
            "com.ss.android.article.video", // 抖音（视频前贴/图文前贴）
            "com.smile.gifmaker",      // 快手
            "com.mxtech.videoplayer",  // MX Player（本地视频贴片）
    };
    static final String[] VID_PREROLL_KEYS = { "preroll", "pre_roll", "front_ad", "prerollad" };

    /* 5c 悬浮广告球：极小面积（≤5% 屏）+ close/取消 vid + 边缘带。
     *    悬浮球特征：可拖动容器（vid 含 drag/floating/ball/side）或贴边小圆钮。
     *    面积门比 sdk_close_vid 更紧（5% vs 35%），防普通控件误中。 */
    static final String[] VID_FLOAT_KEYS = { "drag", "floating", "float", "ball", "side" };
    static final double FLOAT_BALL_AREA_MAX = 0.05;

    /** 5a 文案普查（服务侧扫描前调用）：肯定侧信号词在场？ */
    public static boolean isUpdatePositive(String text) {
        if (text == null) return false;
        for (String k : UPDATE_POSITIVE) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    /** 5a 文案普查：否定侧文案在场？（与 UPDATE_NEGATIVE 等值/前缀容差） */
    public static boolean isUpdateNegative(String text) {
        if (text == null) return false;
        String t = text.trim();
        for (String k : UPDATE_NEGATIVE) {
            if (t.equals(k)) return true;
        }
        return false;
    }

    /** 5b 视频类应用判定（包名前缀匹配） */
    public static boolean isVideoApp(String pkg) {
        if (pkg == null) return false;
        for (String p : VIDEO_APP_PKGS) {
            if (pkg.startsWith(p)) return true;
        }
        return false;
    }

    /** 规则匹配主入口：遍历控件树，返回最优命中（null=无规则命中，不动作） */
    public static Hit match(NodeView root, Ctx ctx) {
        if (root == null) return null;
        int[] budget = {ctx.budgetNodes};
        return visit(root, 0, budget, ctx, null);
    }

    /** v4.3.0 多候选收集：一次 DFS 收集最多 max 个命中（复验真正换候选）。
     *  此前 match() 每次扫描都返回【同一个首个命中】——复验重试点同一个
     *  坐标（issue#38 实锤：喜马拉雅同坐标连点 6 次广告不关，「换第 N 个
     *  候选」日志是假的）。收集后按置信度排序：精确/分应用/vid 优先，
     *  同级短文案优先（「跳过」精确键 > 「跳过广告」图内文本，与决策核心
     *  skipCands 排序同源）。 */
    public static java.util.ArrayList<Hit> matchAll(NodeView root, Ctx ctx, int max) {
        java.util.ArrayList<Hit> out = new java.util.ArrayList<Hit>();
        if (root == null || max <= 0) return out;
        int[] budget = {ctx.budgetNodes};
        collect(root, 0, budget, ctx, out, max);
        try {
            java.util.Collections.sort(out, (a, b) -> {
                int d = tierFull(b.appId) - tierFull(a.appId);
                if (d != 0) return d;
                int la = a.label == null ? 0 : a.label.length();
                int lb = b.label == null ? 0 : b.label.length();
                return la - lb; // 同置信度：短文案优先（更精确的真键）
            });
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 全置信度排序（v5.5.0 增 update_negative，public 供测试）：精确/分应用/
     *  vid/更新否定（定信 3）> 倒计时形态 2 > 短包含 1 */
    public static int tierFull(String appId) {
        if ("global_exact".equals(appId) || "app_exact".equals(appId)
                || "global_vid".equals(appId) || "sdk_close_vid".equals(appId)
                || "update_negative".equals(appId)) return 3;
        if ("global_pattern".equals(appId)) return 2;
        return 1; // global_contains
    }

    private static void collect(NodeView node, int depth, int[] budget, Ctx ctx,
                                java.util.ArrayList<Hit> out, int max) {
        if (node == null || depth > 32 || budget[0] <= 0 || out.size() >= max) return;
        budget[0]--;
        try {
            String text = nz(node.text());
            String desc = nz(node.desc());
            String label = text.length() > 0 ? text : desc;
            boolean vis = node.visible();
            int[] b = node.bounds();
            double area = areaRatio(b, ctx);

            if (vis && b != null && label.length() > 0 && area <= AdDecisionCore.SKIP_AREA_MAX) {
                Hit h = matchText(label, b, node, ctx);
                if (h != null) addCand(out, h, max);
            } else if (vis && b != null && label.length() == 0 && node.childCount() == 0
                    && area <= AdDecisionCore.SKIP_AREA_MAX) {
                Hit h = matchVid(node, b, ctx);
                if (h != null) addCand(out, h, max);
            }
            int n = node.childCount();
            for (int i = 0; i < n && out.size() < max; i++) {
                collect(node.child(i), depth + 1, budget, ctx, out, max);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 去重后追加（同一坐标/同一标签不重复收——换了也是白点） */
    private static void addCand(java.util.ArrayList<Hit> out, Hit h, int max) {
        if (out.size() >= max) return;
        for (int i = 0; i < out.size(); i++) {
            Hit o = out.get(i);
            if (o.label.equals(h.label) && sameBounds(o.tapBounds, h.tapBounds)) return;
        }
        out.add(h);
    }

    static boolean sameBounds(int[] a, int[] b) {
        if (a == null || b == null || a.length < 4 || b.length < 4) return a == b;
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2] && a[3] == b[3];
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
                Hit h = matchText(label, b, node, ctx);
                if (h != null) {
                    if (isDefinitive(h.appId)) return h;   // 精确/分应用/vid：最高置信立即返回
                    if (best == null || tierRank(h.appId) > tierRank(best.appId)) best = h;
                }
            }
            /* vid 规则：无文案叶子节点 */
            else if (vis && b != null && label.length() == 0 && node.childCount() == 0
                    && area <= AdDecisionCore.SKIP_AREA_MAX) {
                Hit h = matchVid(node, b, ctx);
                if (h != null) return h; // vid 强信号立即返回
            }

            int n = node.childCount();
            for (int i = 0; i < n; i++) {
                best = visit(node.child(i), depth + 1, budget, ctx, best);
                if (best != null && isDefinitive(best.appId)) {
                    return best; // 已握精确/分应用/vid 命中，提前收工（速度优先）
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    /** 文本规则匹配（第0/1/2/3层）。v4.5.0 public：服务侧原生文本探针复用 */
    public static Hit matchText(String label, int[] b, NodeView node, Ctx ctx) {
        String low = lowerAscii(label); // v7.3.0 算法：零分配归一（tr-TR locale 根治）

        /* 第0层：分应用精确（不受长度上限约束；v5.6.0 并行小写表零分配；
         * v7.1.0 预筛 memo：ctx.appTextRules 已预筛时零全表扫描——服务侧
         * 每窗填一次（RuleEngine.rowsFor），探针/DFS 双路径共享；未预筛的
         * 调用方回落 rowsFor(pkg)（与旧「全表+包名前缀检查」数学等价：
         * rowsFor 恰返回 row[0] 是 pkg 前缀的全部行——绝不漏也绝不误） */
        if (ctx.pkg != null) {
            String[][] rows = ctx.appTextRules != null ? ctx.appTextRules : rowsFor(ctx.pkg);
            for (String[] row : rows) {
                for (int j = 1; j < row.length; j++) {
                    if (low.equals(row[j])) {
                        return build("app_exact", label, b, node, ctx);
                    }
                }
            }
        }

        /* 第1层：全局精确（等值，大小写归一；v5.6.0 HashSet 等值查询） */
        if (TEXT_EXACT_SET.contains(low)) return build("global_exact", label, b, node, ctx);

        /* 第2层：倒计时形态（严格正则——仅跳过系词+数字/秒表，任何营销话术无法混入；
           v5.6.0 预编译 Pattern：热路径零重编译零分配） */
        if (RE_COUNTDOWN_TAIL.matcher(label).matches()
                || RE_COUNTDOWN_HEAD.matcher(label).matches()) {
            return build("global_pattern", label, b, node, ctx);
        }

        /* 第3层：短包含 + 诱导词否决 */
        if (label.length() <= CONTAINS_MAX_LEN && containsShort(low) && !hasDangerousWord(low)) {
            return build("global_contains", label, b, node, ctx);
        }
        /* ═══ 第5a层：更新诱导弹窗否定侧（仅双信号确认后放行）═══
         * 误触红线：普通页面也有「以后再说」（如订阅到期提醒），
         * 无肯定侧同场信号绝不出手——ctx.updateDialog 由服务侧
         * 普查窗口文案（肯定+否定双在场）后置位。 */
        if (ctx.updateDialog && isUpdateNegative(label)) {
            return build("update_negative", label, b, node, ctx);
        }
        return null;
    }

    /** vid 规则匹配（第4层 + v5.5.0 第5b/5c层扩展；public 供测试与探针复用） */
    public static Hit matchVid(NodeView node, int[] b, Ctx ctx) {
        String vid = node.viewId();
        if (vid == null || vid.length() < 4) return null;
        /* v7.3.0 算法：旧 v.split("[.$]") 对每个零文本叶子节点隐式编译正则
         *  Pattern + 分配 String[]（深树一轮数百次）——改手写分段扫描（字符级
         *  切段，零正则零数组分配），语义与 split 完全等价：段边界仍为 '.'/'$'，
         *  段内 contains 逐段判定；lowerAscii 归一同样零分配。 */
        String v = lowerAscii(vid);
        int vn = v.length();
        int segSt = 0;
        for (int i = 0; i <= vn; i++) {
            if (i == vn) {
                if (i - segSt < 3) break;
            } else {
                char ch = v.charAt(i);
                if (ch != '.' && ch != '$') continue;
                if (i - segSt < 3) { segSt = i + 1; continue; }
            }
            String s = v.substring(segSt, i);
            segSt = i + 1;
            /* skip 族（任意窗口）+ 倒计时控件 */
            for (String k : VID_SKIP_KEYS) {
                if (s.contains(k)) return build("global_vid", "vid:" + vid, b, node, ctx);
            }
            if (s.contains("count") && s.contains("down") && !s.contains("download")) {
                return build("global_vid", "vid:" + vid, b, node, ctx);
            }
            /* v5.7.0 ADCLOSE 复合族：边缘带 + 小面积门控下任意窗口放行
             *（广告关闭语义直接写进命名——btn_ad_close / iv_adclose 类
             * 第一方自绘广告关闭键，非广告窗的普通控件绝无此命名） */
            if (b != null && edgeZone(b, ctx)
                    && areaRatio(b, ctx) <= AdDecisionCore.SKIP_AREA_MAX) {
                for (String k : VID_AD_CLOSE_KEYS) {
                    if (v.contains(k)) return build("global_vid", "vid:" + vid, b, node, ctx);
                }
            }
            /* ═══ v5.5.0 5b 视频前贴 vid 键：preroll/pre_roll/front_ad
             * 任意窗口放行（这些 key 只存在于前贴广告容器，无误触面） */
            if (ctx.videoApp) {
                for (String k : VID_PREROLL_KEYS) {
                    if (s.contains(k)) return build("global_vid", "vid:" + vid, b, node, ctx);
                }
            }
        }
        /* ═══ v5.5.0 5c 悬浮广告球：极小面积 + 悬浮容器特征 vid + 边缘带
         * 面积门比 SDK close 更紧（5%），三重门下无误触面 */
        if (b != null && edgeZone(b, ctx)
                && areaRatio(b, ctx) <= FLOAT_BALL_AREA_MAX) {
            for (String k : VID_FLOAT_KEYS) {
                if (v.contains(k)) {
                    /* 悬浮球要可关闭：优先 close 族子键，否则球体本身
                     * （可拖球体点击=展开，关闭靠 close 键）——只有带
                     * close/drag 语义的才出手 */
                    for (String c : VID_CLOSE_KEYS) {
                        if (v.contains(c)) return build("sdk_close_vid", "vid:" + vid, b, node, ctx);
                    }
                }
            }
        }
        /* close/exit/dismiss 族：SDK 广告窗/学习窗 + 屏幕边缘带；
         * v5.5.0 半屏插屏扩展：halfScreenAd（底部半屏 Dialog）本身是
         * 强广告信号，与 SDK 指纹同级放行 */
        if ((ctx.sdkAdWin || ctx.learnedCls || ctx.halfScreenAd) && edgeZone(b, ctx)) {
            for (String k : VID_CLOSE_KEYS) {
                if (v.contains(k)) return build("sdk_close_vid", "vid:" + vid, b, node, ctx);
            }
        }
        return null;
    }

    static Hit build(String appId, String label, int[] b, NodeView node, Ctx ctx) {
        /* ═══ v4.5.0 乱点根治：文本命中形状门（issue#42-44 实锤抽屉菜单
         * 「跳过」列表项被当跳过键点击）═══
         * 真跳过键是孤立紧凑控件（≤55% 屏宽；自身可点放宽 70%）；全宽行/
         * 竖列菜单行（isMenuListRow：≥4 同列同宽竖排兄弟）绝不点。
         * 豁免：SDK 广告窗/学习窗（ctx 高置信广告场景，跳过键形态可放宽）。 */
        boolean textRule = !"global_vid".equals(appId) && !"sdk_close_vid".equals(appId);
        if (textRule && !ctx.sdkAdWin && !ctx.learnedCls
                && !AdDecisionCore.validSkipTextShape(node, b, ctx)) {
            return null;
        }
        Hit h = new Hit();
        h.appId = appId;
        h.label = label;
        h.tapBounds = b; // 点标签控件自身中心（非固定屏幕坐标）
        NodeView click = AdDecisionCore.resolveClickable(node, 4);
        h.clickNode = click;
        // 祖先缺失或祖先是巨型容器（performAction=点开广告）→ 仅手势直点标签坐标
        h.gestureOnly = click == null || !AdDecisionCore.isSmallClick(click, ctx);
        return h;
    }

    /** 屏幕边缘带：跳过/关闭键的天然位置（顶部 25% 或右缘带 centerX≥75% 宽） */
    static boolean edgeZone(int[] b, Ctx ctx) {
        if (b == null || b.length < 4 || ctx.screenW <= 0 || ctx.screenH <= 0) return false;
        int cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2;
        if (cy <= ctx.screenH / 4) return true;          // 顶部 25%
        return cx >= ctx.screenW - ctx.screenW / 4;       // 右缘带（centerX ≥75% 宽）
    }

    /* ═══ v7.5.0 窗级兄弟倒计时（规则路径：「跳过」文案无数字的穿山甲族）═══
     * 倒计时数字与「跳过」分属两个节点时 parseCountdown(label)=0——首击
     * 立即出手白点，3 击耐心预算烧完 → 干看广告走完（用户十四轮实锤
     * 「好多其他 app 的开屏都跳不了」头号死因之一）。只收独立数字小形态
     *（standaloneCountdownSeconds：3/5s/3秒）且在倒计时区（顶 35%/底 25%）
     * 或距命中的跳过键 |Δcy|≤25% 屏高——返回最大秒数（0=无有效倒计时）。 */
    public static int windowCountdownHint(NodeView root, Ctx ctx, int[] nearBounds) {
        if (root == null) return 0;
        int[] budget = {200};
        int[] out = {0};
        cdHint(root, 0, budget, out, ctx, nearBounds);
        return out[0];
    }

    private static void cdHint(NodeView node, int depth, int[] budget, int[] out, Ctx ctx, int[] near) {
        if (node == null || depth > 32 || budget[0] <= 0) return;
        budget[0]--;
        try {
            String text = nz(node.text());
            String desc = nz(node.desc());
            String label = text.length() > 0 ? text : desc;
            if (label.length() > 0 && label.length() <= AdDecisionCore.CD_STANDALONE_MAX_CHARS) {
                int v = AdDecisionCore.standaloneCountdownSeconds(label);
                if (v > out[0]) {
                    int[] b = node.bounds();
                    // 形态未知/不可见：保守不计（宁漏勿误——数字不到区不门控）
                    if (b != null && node.visible()
                            && (AdDecisionCore.inCountdownZone(b, ctx)
                                || AdDecisionCore.nearY(b, near, ctx))) {
                        out[0] = v;
                    }
                }
            }
            int n = node.childCount();
            for (int i = 0; i < n; i++) {
                cdHint(node.child(i), depth + 1, budget, out, ctx, near);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 定信度命中（全树优先立即返回）：精确 / 分应用 / vid */
    static boolean isDefinitive(String appId) {
        return "global_exact".equals(appId) || "app_exact".equals(appId)
                || "global_vid".equals(appId) || "sdk_close_vid".equals(appId)
                /* v5.5.0：更新弹窗否定侧等值精确命中——同属定信层 */
                || "update_negative".equals(appId);
    }

    /** 候选层优先级：倒计时形态 > 短包含（exact 优先不在此层） */
    static int tierRank(String appId) {
        if ("global_pattern".equals(appId)) return 2;
        return 1; // global_contains
    }

    /** 诱导词黑名单（包含层一票否决） */
    static boolean hasDangerousWord(String low) {
        for (String w : DANGEROUS_WORDS) {
            if (low.contains(w)) return true;
        }
        return false;
    }

    /** 短文案包含匹配（入参已小写） */
    static boolean containsShort(String low) {
        for (String k : TEXT_SHORT_LOW) {
            if (low.contains(k)) return true;
        }
        return false;
    }

    /** 规则库统计（启动日志用） */
    public static String stats() {
        int appTexts = 0;
        for (String[] row : APP_TEXT_RULES) appTexts += row.length - 1;
        return "分应用表 " + APP_TEXT_RULES.length + " 款/" + appTexts + " 条文案"
                + " · 全局精确 " + TEXT_EXACT.length + " 条"
                + " · 倒计时形态 2 类"
                + " · 短包含 " + TEXT_SHORT.length + " 族(≤" + CONTAINS_MAX_LEN + "字,诱导词" + DANGEROUS_WORDS.length + " 否决)"
                + " · vid skip/" + VID_SKIP_KEYS.length + " close/" + VID_CLOSE_KEYS.length
                + " · v5.5.0 场景层：更新弹窗否定" + UPDATE_NEGATIVE.length
                + "+肯定信号" + UPDATE_POSITIVE.length
                + " · 视频前贴 " + VIDEO_APP_PKGS.length + " 款"
                + " · v5.7.0 ADCLOSE 复合族/" + VID_AD_CLOSE_KEYS.length + " 档(边缘带+小面积门控)";
    }

    static double areaRatio(int[] b, Ctx ctx) {
        if (b == null || ctx.screenW <= 0 || ctx.screenH <= 0) return 0;
        double area = (double) (b[2] - b[0]) * (b[3] - b[1]);
        return area / ((double) ctx.screenW * ctx.screenH);
    }

    static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /** v7.3.0 算法：ASCII 快路径小写归一（与 AdDecisionCore.lowerAscii 同源；
     *  零分配 + tr-TR locale 根治，详见 AdDecisionCore 注释）。
     *  public：服务侧（AdSkipService.isDialogClass 等热路径）跨包复用。 */
    public static String lowerAscii(String s) {
        if (s == null || s.length() == 0) return s == null ? "" : s;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                char[] a = s.toCharArray();
                for (int j = i; j < n; j++) {
                    char d = a[j];
                    if (d >= 'A' && d <= 'Z') a[j] = (char) (d + 32);
                }
                return new String(a);
            }
        }
        return s;
    }
}
