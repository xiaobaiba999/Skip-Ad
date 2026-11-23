package com.zai.adshield.core;

import java.util.ArrayList;

/**
 * 秒跳决策核心（v5.1.0 · 纯 Java · 零 Android 依赖 · JVM 可单测）
 * ─────────────────────────────────────────────────────────────
 * v5.1.0（issue#46/47 v5.0.0 日志实锤：非主流 App 开屏跳不动 + 噪声扫描刷屏）：
 *  · SDK 广告窗指纹扩展：AdsActivity（第三方自建开屏窗最高频命名——旧指纹
 *    只认 "adactivity" 整段，"adsactivity" 漏网）/ DspActivity（美团系开屏
 *    DSP 独立窗）/ DynamicDialog（美团动态广告弹窗）——漏网 = 整窗无指纹：
 *    零 vid-close 点击、零返回键兜底，只能干看广告走完；
 *  · 弹窗安全门：dialogForm 窗（DynamicDialog 类）SDK 直断须再过宽松广告
 *    形态树（无滚动/无输入框）——运营/表单弹窗绝不返回键；
 *  · 零候选退避 zeroCandBackoff：校园/工具类 App 校徽启动页（school.app
 *    9 秒 7 条未命中）与商店首页零候选后继续整树扫描 = 纯功耗 + 无谓风险面。
 *    同窗连续 3 次零候选且过 2.6s（微树 2 次/1.2s）→ 剩余固定档取消，
 *    nudge 救回通道保住晚到真跳过键；
 *  · 跳过后静默期 postSkipQuiet：复验通过同一秒又扫 79 节点首页（waimaiii
 *    实锤）——2.2s 内不记未命中日志（第二帧广告是独立窗口事件不受影响）；
 *  · 安全增补：诱导词 +抢购/秒杀/礼包/优惠/补贴/立减/低价/充值（「跳过领
 *    礼包」类诱饵绝不代点）；支付页零扫描标记 +支付密码/付款码/收银台/
 *    刷脸支付（与验证码横幅同一防线，点错=误触支付流程）。
 * v3.14.0（issue#21 日志 + 真机截图实锤：喜马拉雅跳过开屏后 10 次有 8 次
 * 弹出「不喜欢/反馈投诉」底部面板，用户每次都要手动收起；且引擎误判
 * 「广告未关」连续换候选重试 ×2~4 次）：
 *  · 新增「跳过后反馈弹窗」识别 findFeedbackSheet：广告 SDK 在广告被
 *    关闭后弹出的反馈面板（截图特征：标题「不喜欢」+ 选项「重复出现/
 *    不喜欢此内容」+ 分组「反馈投诉」+「我为什么会看到广告」+ 底部
 *    「取消」）。反馈面板在场 = 广告已关的铁证（服务侧据此停用重试梯子，
 *    不再无谓换候选），并代点「取消」收起面板；
 *  · 安全红线：① 识别需 ≥1 个强特征（「我为什么会看到广告/反馈投诉/
 *    减少此类广告」——信息流长按菜单没有这些词）且总特征 ≥2 个；
 *    ② 只点文案精确等于「取消」的控件——绝不替用户提交任何反馈选项；
 *    ③ 服务侧仅在该包 12s 内自动跳过过或复验中才启用（手动长按菜单
 *    绝不碰）。
 * v3.13.0（真机反馈：接收验证码时顶部横幅被引擎点掉 + 夜间待机功耗高）：
 *  · 验证码横幅三重保护（纯逻辑部分）：
 *    isSmsOverlayPkg——短信/彩信/来电类覆盖层包（com.android.mms /
 *    com.miui.smsextra / messaging 族 / incallui），服务侧事件直接丢弃；
 *    isCodeBannerMarker + treeHasCodeBannerMarker——树内出现
 *    「验证码/校验码/动态码/动态密码」文案的窗口绝不扫描绝不点击
 *    （含应用内自绘的「填入验证码」提示条）；
 *    isOemUtilityPkg 从服务侧上移（issue#16 实锤：securitycenter 应用
 *    详情页被学出位置 (91%,9%)——安全中心里学到的「跳过键」= 下次
 *    进权限管理页 500ms 乱点，位置学习从此拒收 OEM 工具包）。
 *  · 待机功耗治理（纯逻辑部分）：本轮决策零变化——挂起/降频全部在
 *    服务侧（息屏事件门控 + 心跳自适应），决策核心保持可单测。
 * v3.12.0（issue#14 日志实锤：NFC 支付链与主动分享被 v3.11.0 安全门
 *  仍拦截）：链式拦截机制【整体移除】——chainBounceVerdict 三重放行 /
 *  isChooserHint / isAdTarget + AD_TARGET_HINTS 名单全部删除。
 *  用户意图场景无法穷举，任何安全门都有漏网时刻；纯跳过引擎只点
 *  「跳过/关闭」类控件，绝不对抗用户的任何跳转（三轮事故终局：
 *  #12 闪退 → #13 分享被拦 → 本次 NFC 支付被拦）。
 * v3.11.0（历史：曾加三重放行安全门，被 v3.12.0 整体移除）。
 * v3.9.0 设计动机（issue#12 真机日志实锤：QQ/微信/抖音「进去就被退出来」）
 *  ——内嵌规则引擎主路径化（规则只认控件属性+点控件自身 bounds 中心）：
 *  · 【删除】L6 坐标级点击兜底（coord_fallback）：它点的是固定百分比坐标
 *    (88%,7%) 等——每台手机屏幕大小不一样，位置肯定不一样（用户原话）。
 *    桌面简单树被误判后盲点桌面图标拉起微信→回弹狂按返回键踢出用户
 *    =issue#12 闪退完整毒链。主路径改由 RuleEngine（内嵌规则引擎，
 *    控件属性匹配+点控件自身 bounds 中心）接管，本类退居复杂信号兜底；
 *  · 【提速】露头就秒跳（以 Keep 开屏为标杆）：学习位置 500→100ms、
 *    开屏×关闭键 600→250ms、角落键 500→300ms（但增加广告形态确认门，
 *    绝不在正常页面上乱点角落图标）；
 *  · 【收紧】角落无字小控件（L4）须广告形态确认：热区/广告角标/SDK 窗/
 *    学习窗/节点≤60 五选一——普通应用主页右上角的设置齿轮不再被误点；
 *  · 【新增】isLauncherLike：桌面/Launcher 包识别（服务侧黑名单用——
 *    桌面绝不扫描/学习/回弹，issue#12 根治）。
 * v3.10.0：isLauncherLike 扩充（lawnchair/trebuchet 等第三方桌面）；
 *  isAdActivityClass 扩充（小米 zeus/adview/youmi/domob/doubleclick
 *  等广告窗指纹）；Ctx 新增 pkg（分应用规则表用）。
 * v3.8.0 设计动机（issue#10 二轮真机反馈：喜马拉雅点击跳过键 4 次广告不关、
 *  手动点立刻生效；微博找不到可点候选只能返回键，而返回键被广告拦截）——
 *  网络调研 GKD（gkd-kit，同类最佳开源应用）最新全局规则与 API 文档，
 *  三个方法论落地：
 *  · visibleToUser 过滤：GKD 全局规则要求 [visibleToUser=true]——不可见节点
 *    =陷阱/死区，点了白点（文本/vid/关闭/角落候选全部加 vis 门）；
 *  · viewId 匹配：GKD 规则匹配 vid*="skip"（穿山甲 tt_splash_skip_btn /
 *    微博 iv_skip 类零文本图片跳过键）和倒计时控件（vid 含 count+down
 *    非 download）；
 *  · clickCenter 方法论：「跳过」文案+大容器可点祖先时，标签自身坐标仍是
 *    有效触点——手势直点即可（performAction 大容器=点开广告，绝不走）。
 *  配套改造：假跳过手势候选梯子（L1 统一轮换）、walk 内候选排序（自身可点/
 *  短文案/小面积优先）、角落键前置到 SDK 直断之前（CLS_BREAK_MS 500→700：
 *  点击优先直断兜底）、跳过文案倒计时解析 parseCountdown。
 * v3.7.0 设计动机（四次真机反馈 issue#10：喜马拉雅「已点击跳过×4」仍不关，
 *  手动点同一按钮立刻生效；微博陀螺仪窗返回键直断无效果——广告拦截返回键）：
 *  · 喜马拉雅根因链：① 无障碍服务缺 canPerformGestures → 手势真实触点
 *    从未真正触达屏幕（服务侧配置修复）；② 手势路径单点零长度 → 部分
 *    ROM 不触发点击（服务侧路径修复）；③ 决策层只点第一个「跳过」文本
 *    候选，复验重试点同一坐标 4 遍——广告图内常嵌假「跳过」文本，
 *    首选候选可能是陷阱/死区。对策（本类）：多候选收集 + 第 N 次点击
 *    换第 N 个候选（skipCands）；学习位置（用户手指真正点通过的位置）
 *    前置到 L1 之前——教过就优先直点，不再盲点文本候选。
 *  · 微博根因链：L3 陀螺仪文案 120ms 就返回键直断，抢在角落键点击
 *    （500ms）之前；返回键被广告 Activity 拦截后扫描链熔断，无复验无
 *    兜底。对策：L3 降级到 L4 之后（有可点候选先点击，点完/无候选才
 *    返回键）；返回键直断后由服务侧 600ms 复验（不熔断）。
 *  · 角落键/关闭键受简单树 40 节点上限误伤（广告窗树常大）：放宽为
 *    CORNER_TREE_MAX_NODES=150（热区直断/坐标兜底仍保持严格）。
 *
 * v3.6.0 设计动机（三次真机反馈：喜马拉雅二次进入被引擎连按两次返回键
 *  「模拟人操作退出」到桌面——v3.5.0 的返回键兜底在第一方开屏窗=退出应用）：
 *  · 返回键只在「返回后仍在应用内」的窗口上安全：SDK 广告窗（独立广告
 *    Activity，下面是应用主页）与应用内窗（主页之上拉起的广告页）。
 *    应用入口窗（rootWindow：冷启动开屏/从后台回前台首窗）上按返回
 *    = 退出应用（喜马拉雅拦截返回弹「再按一次退出」，第二次直接退出）。
 *  · 对策：根窗口禁一切返回键兜底（L3/L4.5/L5），只走点击梯子（文本→
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
 *         返回键兜底【仅应用内窗】（v3.6.0 根窗口豁免，见下）
 *    L5 全屏热区（自绘图片广告三条件）→ 900ms 返回键直断（守护名单豁免）
 *    L6 开屏窗坐标级点击兜底：简单树(无滚动/无输入框/节点少)且无任何
 *        信号 → dispatchGesture 点击标准跳过位，v3.6.0 三位置轮换：
 *         右上(88%,7%) → 右下(90%,92%) → 左上(8%,7%)，第 N 次点第 N 个，
 *         守护名单豁免（防误点；长文本豁免）
 *    P  弹窗窗期：角标强信号 / Dialog+≥2营销话术弱信号 → 代点关闭
 *  · v3.6.0 根窗口返回键安全门（三次真机反馈：喜马拉雅二次进入被
 *    「模拟人操作退出」——引擎在第一方开屏窗用返回键兜底，喜马拉雅拦截
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

        boolean visible(); // v3.8.0：对用户可见（isVisibleToUser）——不可见=陷阱/死区，不点（GKD 全局规则同款）

        String viewId();   // v3.8.0：viewIdResourceName（无返回空串）——vid 含 skip 的零文本图片跳过键
    }

    /* ── 动作常量 ── */
    public static final String A_CLICK_BOUNDS = "click_bounds"; // 点击控件（代点/手势）
    public static final String A_TAP_POINT = "tap_point";       // 坐标级点击（手势兜底）
    public static final String A_BACK = "back";                 // 返回键直断
    public static final String A_CLOSE = "close";               // 弹窗关闭键
    public static final String A_NONE = "none";

    /* ── 时序参数（ms） ── */
    public static final long CLS_BREAK_MS = 700;          // SDK 窗直断时限（v3.8.0 500→700：先给角落键/vid 键 500ms 点击时机，点击优先直断兜底）
    public static final long CLS_LEARNED_BREAK_MS = 300;  // 学习指纹快断
    public static final long GYRO_IMMEDIATE_MS = 120;     // 无倒计时立即直断
    public static final long GYRO_BREAK_MS = 800;         // 带倒计时(假跳过)直断
    public static final long LEARNED_POS_MS = 100;        // 学习位置点击出手（v3.9.0 500→100：规则引擎未命中才轮到，出手即秒点）
    public static final long SPLASH_CLOSE_MS = 250;       // 开屏×关闭键出手（v3.9.0 600→250：露头就秒跳）
    public static final long CORNER_MIN_MS = 300;         // 角落控件最早出手（v3.9.0 500→300，但须广告形态确认门）
    public static final long CORNER_MAX_MS = 6500;        // 角落控件最晚（v3.6.0 3500→6500：部分开屏 3-5s 才出跳过键）
    public static final long HOT_ZONE_MS = 900;           // 热区直断

    /* ── v3.8.0 参数（issue#10 二轮·GKD 方法论对齐） ── */
    public static final double FAKE_TAP_AREA_MAX = 0.12; // v3.8.0：假跳过手势候选：标签自身面积上限（大标签=CTA 诱饵不点）
    /* v5.6.0 激进化（用户实锤：好多广告没有命中——可以大胆激进）：
     * 倒计时缓冲 500→300ms（穿山甲系倒计时将尽即出手，快点 0.2s）；
     * 出手后缓冲收窄 = 同窗可更快进入复验换候选。安全边界不变：
     * 倒计时未到仍不点（点了白点占冷却），只是「将尽」判定提前。
     * v5.7.0 扫描日志继续优化（用户二轮实锤：可以再大胆激进一点）：
     * 倒计时缓冲 300→180ms——「将尽」判定再提前 0.12s；倒计时将尽时
     * 广告主普遍最后 0.1~0.3s 插入结算帧，越早开火可见时长越短。
     * v5.8.0 屏蔽速度压榨（用户三轮实锤：屏蔽速度太慢了，压榨优化速度
     * 算法）：180→120ms——「将尽」判定再提前 0.06s；配合服务侧骨架档
     * 前 4s 密度×2 与开闸精确针，开火链路全绿。
     * 安全边界依旧：未到仍不点，COUNTDOWN_MAX_S 天花堂不变。 */
    public static final long COUNTDOWN_EXTRA_MS = 120;   // v5.8.0：180→120（速度压榨：倒计时将尽提前 0.38s 开火）
    public static final int COUNTDOWN_MAX_S = 15;        // v3.8.0：可识别的倒计时秒数上限
    /** v7.6.0 激进模式：重击门控秒数封顶——首击白点后最多再等 6s（真倒
     *  计时按钮命中时机），误检大数字（「15」）也不再无限等（用户实锤
     *  「非常非常慢/跳不了了」的兜底闸）。 */
    public static final int RETRY_GATE_MAX_S = 6;

    /* ── v3.5.0 复验与候选参数 ── */
    public static final int MAX_CORNERS = 4;              // 角落候选上限（逐个换着点）
    public static final int CLICK_VERIFY_MAX = 3;         // 同窗点击次数达到即升级返回键（仅应用内窗，v3.6.0）
    /* v5.6.0 激进化：同窗点击总预算 6→8（用户实锤：第一方自绘广告
     * 复验链长——候选轮换+坐标重试同窗常超 6 次即「优雅收手」，
     * 收得太早 = 广告完胜。+2 预算仅在开屏窗期内生效于服务侧计数，
     * 安全闸（面积/形状/守护名单）不变。
     * v5.7.0 继续激进：8→10——扫描日志显示深树广告窗（300+ 节点）
     * 复验轮换链常达 8 次上限后干看广告走完；+2 预算只延长换候选
     * 轮次，不放宽任何安全闸（误点面零变化）。
     * v5.8.0 速度压榨：10→12——点击冷却 420→300ms 后轮换节奏本身就快，
     * 预算同步加宽才不会提前「优雅收手」干看广告走完；安全闸
     * （面积/形状/守护名单）依旧零变化。
     * v6.0.0 人手耐心模型（用户实锤：部分用户设备开屏跳过时多点/误点
     * ——1:1 模拟人操作）：12→6。首点未中场景下 12 连点机器枪是「多点」
     * 的直接观感来源；v6 首击精度已升级（实时 bounds 追踪 + dp 自适应
     * 安全域 + 人手手势参数），首点命中率大幅提升，重试预算从「机器枪」
     * 迁回「人的耐心」：6 下内没关就优雅收手等倒计时走完，与
     * HumanTap.HUMAN_PATIENCE 同源（安全闸依旧零变化）。 */
    public static final int MAX_CLICKS_PER_WIN = 3;       // v7.2.0：6→3（用户十二轮实锤多点观感——人的耐心 2~3 下；与 HumanTap.HUMAN_PATIENCE 同源）
    public static final double CLOSE_TOP_MAX = 0.35;      // 开屏×关闭键须在顶部 35% 内
    public static final int LONG_TEXT_LEN = 40;           // 长文本（正常内容页特征）

    /* ── 阈值参数 ── */
    public static final double POS_LEARN_AREA_MAX = 0.25; // 位置学习：控件面积上限（v3.6.0 0.20→0.25：手动教学点击事件源可能是稍大的容器）
    public static final double POS_MIN_NORM = 0.03;      // v3.7.0：位置学习健全性下限（真机日志 (63%,102%) 越界白学）
    public static final double POS_MAX_NORM = 0.97;      // v3.7.0：位置学习健全性上限（越界坐标点了也是死区）
    public static final int SKIP_CANDS_MAX = 8;          // v5.8.0：6→8（速度压榨：深树同文案候选全收，复验换候选零等待）
    public static final int CORNER_TREE_MAX_NODES = 150; // v3.7.0：角落/关闭键树上限放宽（广告窗树常大，微博根因之一）
    public static final double HOT_ZONE_AREA = 0.55;   // 热区：≥55% 屏幕面积
    public static final int HOT_ZONE_MAX_NODES = 12;
    public static final double SKIP_AREA_MAX = 0.35;   // 真跳过键面积上限
    public static final double SKIP_WIDTH_MAX = 0.55;
    public static final double CORNER_TOP_MAX = 0.14;  // 角落：顶部 14% 高内
    public static final double CORNER_RIGHT_MIN = 0.72; // 右缘 ≥72% 宽
    public static final double CORNER_LEFT_MAX = 0.28;  // 或左缘 ≤28% 宽
    public static final int CORNER_MIN_PX = 24;        // 角落控件最小像素
    public static final int AD_LIKE_MAX_NODES = 40;    // 简单树节点上限
    /* v3.9.0 角落键广告形态确认：无字小控件（疑似图片跳过键）出手前须满足
       「热区/广告角标/SDK 窗/学习窗/节点≤60」五选一——普通主页的角落图标
       （设置齿轮/客服入口）节点树大且无广告信号，绝不误点（不乱点红线） */
    public static final int CORNER_CONFIRM_NODES = 60;
    /* ══ v7.5.0 位置解耦（用户十四轮实锤：「不是每次每个按钮都是一个位置」）══
     * 中部边带（左/右缘 · 任意高度）：部分应用跳过键在右侧中部——旧角落
     * 几何只认顶 14%/底 18% 两带，中部图片跳过键零收集。中部边带候选
     * 仅强广告形态（热区/角标/SDK窗/学习窗）下可出手，树极简兕底不适用
     *（FAB/普通悬浮钮误点防线——宁漏勿误红线）。 */
    public static final double MIDEDGE_MIN_PX = 24;   // 中部边带控件最小像素（与角落同源）
    /* ══ v7.5.0 倒计时解耦（用户十四轮实锤：「开屏按钮跳过不是每个都带倒计时」）══
     * 兄弟节点倒计时：穿山甲族「跳过」文案与倒计时数字分属两个节点——
     * parseCountdown(label)=0 → 首击立即出手（白点）+ 3 击耐心预算烧完
     * → 干看广告走完（并窗内无候选→未命中日志都没有）。独立数字小形态
     *（3/5s/3秒）在倒计时区或命中键近旁 → 并入 f.countdownN 门控。 */
    public static final int CD_STANDALONE_MAX_CHARS = 4; // 独立倒计时文案字符上限
    public static final int CD_DIGIT_CAP = 6;         // 独立数字候选收集上限
    public static final double CD_ZONE_TOP_MAX = 0.35;   // 倒计时数字区：顶部 35% 内
    public static final double CD_ZONE_BOTTOM_MIN = 0.75; // 倒计时数字区：底部 25% 内
    public static final double CD_NEAR_Y_MAX = 0.25;      // 独立数字与跳过键近旁：|Δcy| ≤25% 屏高
    public static final long COUNTDOWN_FAKE_MS = 1600;    // v4.3.0 假倒计时（数字 1.6s 未变）——v7.5.0 纯函数化

    /* ── 文案特征库（与 2026-11 市场调研同步）──
     * v5.6.0 激进化扩充（用户实锤：好多广告没有命中）：
     *  · SKIP_TEXTS 增「跳过广告/跳过此广告/立即跳过/Skip Ad」——matchesSkip
     *    的 contains(跳过/skip) 已覆盖大广度，此处扩充作用于 CLOSE 族
     *    兼容路径与订阅 vid 限定路径（第一方自绘窗常把「跳过广告」整词
     *    当独立标签，旧库只收「跳过/关闭广告」两项，walk 分类不进真候选）；
     *  · GYRO/CLOSE/CTA/BADGE 不变——误报防线（不乱点红线）不放松。 */
    public static final String[] SKIP_TEXTS = {"跳过", "关闭广告", "跳过广告", "跳过此广告", "立即跳过", "跳过领取", "Skip Ad",
            /* v5.7.0 激进化扩充（扫描日志：视频类 App 开屏「跳过片头/跳过视频」
             * 整词标签未入真候选——CLOSE 族兼容路径直接放行） */
            "跳过视频", "跳过片头", "Skip Video",
            /* v5.8.0 扫描日志三轮激进（用户实锤：好多广告没有命中——大胆
             * 激进一点）：电商/资讯类 App 常以「推广」代「广告」作整词标签
             * （合规敏感 App 普遍如此，旧库未收零命中），SKIP 族全量补收 */
            "跳过推广", "关闭推广", "跳過片頭", "跳過推廣",
            /* v7.7.0 回切开屏配套：繁体「關閉廣告」整词键（切回 App 期间
             * 弹的插层/回切开屏以整词作键——含匹配族补收，探针词同步扩） */
            "關閉廣告"};
    public static final String[] GYRO_HINTS = {"翻转", "摇一摇", "摇晃", "晃动", "摇动", "扭一扭", "转动手机", "倾斜手机", "拿起手机"};
    public static final String[] CLOSE_TEXTS = {"×", "x", "X", "✕", "✖", "╳", "关闭", "close", "不感兴趣", "我知道了", "知道了", "关闭广告", "跳过", "忽略", "暂不"};
    public static final String[] BADGE_TEXTS = {"广告", "推广", "赞助", "广告推广", "sponsored", "promoted", "sponsor"};
    public static final String[] CTA_TEXTS = {"立即领取", "点击领取", "领取红包", "限时", "秒杀", "恭喜", "马上抢", "立即下载", "立即抢购", "0元领", "0元", "免费领", "点击进入", "立即查看", "开通会员", "领取礼包", "大奖"};
    public static final String[] GUARD_PKG_HINTS = {"bank", "icbc", "cmb", "ccb", "psbc", "abchina", "boc", "unionpay", "gov", "alipay", "955", "12345"};
    /* v3.12.0：AD_TARGET_HINTS 广告目标名单已删除（链式拦截整体移除，issue#14） */

    /* ═══ v3.14.0 跳过后反馈弹窗特征库（issue#21 + 真机截图：喜马拉雅
     * 「不喜欢/反馈投诉」底部面板）═══ 强特征 = 广告反馈面板独有文案
     *（信息流长按「不感兴趣」菜单没有这些词）；弱特征 = 面板常见但
     * 别处也可能出现的词。判定：≥1 强特征 且 总特征 ≥2（双保险）。
     * v4.3.0（issue#38 实锤：喜马拉雅跳过后弹「为什么关闭广告」选项弹窗、
     * 引擎不识别继续点跳过键 → 循环弹出）：新增喜马拉雅变体强特征，
     * 取消候选扩展「暂不反馈/下次再说」等中性收起键。 */
    public static final String[] FEEDBACK_SHEET_STRONG = {
            "我为什么会看到广告", "反馈投诉", "减少此类广告", "反馈关闭广告",
            "为什么关闭广告", "为什么不想看", "请选择关闭原因", "选择关闭广告的原因",
    };
    public static final String[] FEEDBACK_SHEET_WEAK = {
            "不喜欢", "重复出现", "虚假广告", "内容不适", "其他问题",
            "误触", "看过了", "广告太多", "内容低质", "干扰到我了",
    };
    public static final int FEEDBACK_SHEET_MIN_HITS = 2;
    /** 反馈面板中性收起键（只收起，绝不替用户提交任何反馈选项） */
    public static final String[] FEEDBACK_CANCEL_TEXTS = {"取消", "暂不反馈", "下次再说", "稍后再说", "关闭"};
    /** 服务侧门控：引擎自动跳过后多久内允许把反馈面板当「跳过后弹窗」处理 */
    public static final long FEEDBACK_AFTER_SKIP_MS = 12000;

    /* ───────── 扫描结果 ───────── */

    /** 角落候选（v3.5.0 多候选：点击复验失败换下一个候选点）
     *  v7.5.0 strongOnly：中部边带候选（非顶/底角落带）——仅强广告形态
     *（热区/角标/SDK窗/学习窗）下可出手，树极简兕底不适用。 */
    public static final class CornerCand {
        public NodeView clickNode;  // performAction 目标（可点祖先或自身）
        public int[] tapBounds;     // 手势点击边界（原小控件，点得更准）
        public boolean strongOnly; // v7.5.0：中部边带候选（需强广告形态确认）
    }

    /** v7.5.0 独立倒计时数字候选（兄弟节点倒计时——walk 后并入门控） */
    public static final class CdDigit {
        public int seconds;
        public int[] bounds;
    }

    /** v3.7.0 文本跳过键候选（广告图内常嵌假「跳过」文本，复验轮换点） */
    public static final class SkipCand {
        public NodeView clickNode;  // 可点祖先（performAction 目标）
        public int[] tapBounds;     // 手势点击边界（原标签坐标，v3.6.0）
        public String label;
        public boolean gestureOnly; // v3.8.0：假跳过手势候选——只允许 dispatchGesture 点标签坐标，绝不 performAction 大容器祖先
        public boolean selfClick;   // v3.8.0：节点自身可点（无祖先中转）——候选排序依据
    }

    public static final class ScanFind {
        public boolean foundSkip;      // 真「跳过」文本控件（小面积可点）
        public NodeView skipNode;
        public int[] skipBounds;
        public String skipLabel;
        public final java.util.ArrayList<SkipCand> skipCands = new java.util.ArrayList<SkipCand>(); // v3.7.0 多候选
        public final java.util.ArrayList<SkipCand> fakeCands = new java.util.ArrayList<SkipCand>(); // v3.8.0 假跳过手势候选（「跳过」文案+大容器可点祖先——微博实锤：isSmallClick 判假→从不点击只返回键，而返回键被广告拦截）
        public int countdownN = 0; // v3.8.0：跳过文案中的倒计时秒数（「跳过 3」→3；0 无）
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
        public final java.util.ArrayList<CdDigit> cdDigits = new java.util.ArrayList<CdDigit>(); // v7.5.0 独立倒计时数字（walk 后并入门控）
        public boolean dialogForm;     // 根节点 Dialog/Popup 形态
        public boolean webviewPresent; // v7.8.0：窗内存在 WebView 容器（H5 插屏语境铁证——插屏闸⓪第二在场证据）
    }

    /* ───────── 决策上下文（服务侧填充） ───────── */

    public static final class Ctx {
        public int screenW;
        public int screenH;
        public String pkg;          // v3.10.0：当前应用包名（RuleEngine 分应用规则表用）
        public String activityId = ""; // v4.2.0：当前窗口类名（GKD 订阅规则 activityIds 限定用）
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
        /* ══ v5.5.0 模块二：新场景标志（服务侧扫描前判定填入）══
         *  halfScreenAd 半屏插屏：底部半屏 Dialog（根 bounds 高 ≤60% 屏高且
         *    贴底部）——广告强信号，close 族 vid 放开 SDK 指纹要求；
         *  updateDialog 更新诱导弹窗：肯定侧+否定侧文案双信号确认后置位，
         *    否定侧（以后再说/暂不更新…）文案才允许命中（误触红线：
         *    单信号绝不出手——普通页面「以后再说」文案不点）；
         *  videoApp 视频类应用：前贴场景（倒计时/前贴 vid 键优先级提升）。 */
        public boolean halfScreenAd;
        public boolean updateDialog;
        public boolean videoApp;
        /* v7.8.0 视频开屏画像（VideoSplashGuard.videoLike 服务侧扫描前判定填入）：
         * 观察窗 12s→20s 延长 + 轮询针 + 白点节流 + L4 形态门放宽 +
         * 新键预算刷新——「开屏广告带视频的全部未命中」根治。 */
        public boolean videoLike;
        /* v7.1.0 底层算法优化：分应用规则行预筛结果（服务侧每窗一次
         * RuleEngine.rowsFor(pkg) 填入；null = matchText 回落全表扫描——
         * 探针/DFS 双路径共享，每窗省下 ~340 节点 × 50 行前缀比对）。 */
        public String[][] appTextRules;
    }

    /* ───────── 决策输出 ───────── */

    public static final class Decision {
        public String action = A_NONE;
        public NodeView node;   // click/close 的目标控件
        public int[] bounds;    // click_bounds 目标边界
        public int x;           // tap_point 坐标
        public int y;
        public String reason = "";
        /* v5.5.1（issue#64 实锤）：规则命中文案——gestureOnly 命中（无可点
         * 祖先，d.node=null）时动作边界自检的语义依据。此前规则引擎已按
         * 「跳过广告」文案验证语义，但 label 未随 Decision 传递 → 边界自检
         * 拿到空串 → 语义断言误杀（微博漏拦截元凶）。语义放行面零扩张：
         * 文案本身必须仍是跳过/关闭族才可能通过。 */
        public String label = "";

        public static Decision none() {
            return new Decision();
        }
    }

    /* ═════════ 控件树遍历（多信号一次收集） ═════════ */

    /** 决策核心单树遍历（v8.0.2 起 rider 参数化：rider=null 纯 walk 语义，
     *  行为与历版 byte-for-byte 一致——复验针/插屏扫描等独立调用方继续用本入口）。
     *  主扫描路径改走 walkFused（规则骑士与决策信号单遍收集，双 DFS 消除）。 */
    public static ScanFind walk(NodeView root, Ctx ctx) {
        ScanFind f = new ScanFind();
        if (root == null) return f;
        /* ═══ v5.0.0 小程序/团购容器保护（L0，纵深防御第三层）═══
         * 抖音团购券/微信/支付宝/QQ 小程序容器内：树扫描直接返回空——
         * 任何跳过候选/角落候选/热区信号都不收集（服务侧事件入口已拦，
         * 这里兜住定时扫描/补扫针等迟到路径）。「广告未关」的容器加载页
         * 文案（「跳过广告」字样）绝不触发点击（issue 抖音团购券根治）。 */
        if (MiniProgramGuard.isMiniProgramWindow(ctx.pkg, ctx.activityId)) return f;
        String rootCls = nz(root.cls()).toLowerCase();
        f.dialogForm = rootCls.contains("dialog") || rootCls.contains("popup");
        int[] budget = {ctx.budgetNodes};
        visit(root, 0, budget, f, ctx, null);
        // v3.8.0 候选排序：真键优先——① 自身可点（无祖先中转）② 文案更短（精确「跳过」
        // 优先于「5s后可跳过」类长文案）③ 面积更小。防「第 1 候选是广告图内假文本」
        try {
            java.util.Collections.sort(f.skipCands, (a, b) -> {
                if (a.selfClick != b.selfClick) return a.selfClick ? -1 : 1;
                int la = a.label == null ? 0 : a.label.length();
                int lb = b.label == null ? 0 : b.label.length();
                if (la != lb) return la - lb;
                return Double.compare(areaRatio(a.tapBounds, ctx), areaRatio(b.tapBounds, ctx));
            });
        } catch (Throwable ignored) {
        }
        /* v7.5.0 兄弟节点倒计时并入：独立数字在倒计时区（顶 35%/底 25%）
         * 或任一跳过候选近旁（|Δcy|≤25% 屏高）→ 计入 f.countdownN（服务侧
         * 门控到「倒计时将尽」再出手——首击不再白点烧耐心预算）。 */
        resolveCdDigits(f, ctx);
        return f;
    }

    /* ═════════ v8.0.2 双 DFS 单遍化（P3-1 专项）═════════
     * 旧主扫描路径：RuleEngine.matchAll（规则 DFS）与 walk（决策 DFS）对
     * 同一棵树先后各走一遍——零命中扫描（内容页/无文本规则广告，档位大头）
     * 时 matchAll 整趟白付：同节点 text/desc/visible/bounds/childCount 读
     * 两遍、getChild() binder 调用×2、NodeAdapter 分配×2。
     * walkFused = 一次遍历同时收集【规则命中（骑士）】与【决策信号（walk）】：
     *  · 骑士条件与 matchAll 逐字段等价：text 规则门（vis&&b!=null&&label>0
     *    &&area≤SKIP_AREA_MAX→matchText）/ vid 叶子门（label==0&&childCount==0
     *    →matchVid）/ addCand 去重（label+tapBounds）/ tierFull+短文案排序 /
     *    满 max 中即止（与 collect 的 entry 检查+子循环条件同停点）；
     *  · 决策信号与 walk 逐字段等价：同一 visit 体（rider=null 时 byte-for-byte
     *    旧路径），预算同源同减点，候选排序/倒计时并入/根 Dialog 形态全同；
     *  · 骑士满 max 即止 = collect 早停同点；此时规则路径必走（hits 非空），
     *    find 将被服务侧丢弃——早停不产生任何可观测差异；
     *  · 小程序容器门与 walk 同位（入口即返空）：旧路径 matchAll 无此门、
     *    命中后在 execute() L0 被拦——新路径骑士同被抑制（更早更省，出手
     *    结果同为零点击，差异仅日志量/acted 标志，见 V8-SYSTEM-AUDIT）。
     * 已知语义差（诚实清单，DualDfsParityTest 场景 11/12 钉死）：
     *  ① 树在两趟 DFS 之间变化时，旧 walk 看到第二时刻快照、fused 只看
     *    第一时刻——晚到候选顺延到下一档（60ms）/nudge 通道，与 v8.0.1
     *    P2-1 同类权衡；出手前 refreshLive 复核不变，零新增误触面；
     *  ② 子树瞬时 binder 异常（throw-once）旧 walk 重试可得、fused 单次
     *    即失——确定性异常两者等价（同丢同子树）。 */
    public static final class RuleRiderState {
        public final java.util.ArrayList<RuleEngine.Hit> hits = new java.util.ArrayList<RuleEngine.Hit>();
        final int max;

        RuleRiderState(int max) {
            this.max = max;
        }

        boolean done() {
            return hits.size() >= max;
        }

        /** RuleEngine.addCand 同款去重（label+tapBounds 双等——换了也是白点） */
        void addIfNew(RuleEngine.Hit h) {
            if (hits.size() >= max) return;
            for (int i = 0; i < hits.size(); i++) {
                RuleEngine.Hit o = hits.get(i);
                if (o.label.equals(h.label) && RuleEngine.sameBounds(o.tapBounds, h.tapBounds)) return;
            }
            hits.add(h);
        }
    }

    /** walkFused 输出：决策信号（find，walk 同构）+ 规则命中（ruleHits，matchAll 同构） */
    public static final class FusedScan {
        public final ScanFind find = new ScanFind();
        public final java.util.ArrayList<RuleEngine.Hit> ruleHits = new java.util.ArrayList<RuleEngine.Hit>();
    }

    /** 单遍融合扫描：ruleRider=false 等价 walk()（弹窗窗等无规则路径）；
     *  true 时骑士与决策信号同遍收集。maxHits<=0 视同骑士关闭。 */
    public static FusedScan walkFused(NodeView root, Ctx ctx, boolean ruleRider, int maxHits) {
        FusedScan out = new FusedScan();
        if (root == null) return out;
        if (MiniProgramGuard.isMiniProgramWindow(ctx.pkg, ctx.activityId)) return out;
        RuleRiderState rider = (ruleRider && maxHits > 0) ? new RuleRiderState(maxHits) : null;
        String rootCls = nz(root.cls()).toLowerCase();
        out.find.dialogForm = rootCls.contains("dialog") || rootCls.contains("popup");
        int[] budget = {ctx.budgetNodes};
        visit(root, 0, budget, out.find, ctx, rider);
        // walk 同款候选排序（比较器逐字段复制，不得漂移）
        try {
            java.util.Collections.sort(out.find.skipCands, (a, b) -> {
                if (a.selfClick != b.selfClick) return a.selfClick ? -1 : 1;
                int la = a.label == null ? 0 : a.label.length();
                int lb = b.label == null ? 0 : b.label.length();
                if (la != lb) return la - lb;
                return Double.compare(areaRatio(a.tapBounds, ctx), areaRatio(b.tapBounds, ctx));
            });
        } catch (Throwable ignored) {
        }
        resolveCdDigits(out.find, ctx);
        if (rider != null) {
            out.ruleHits.addAll(rider.hits);
            // matchAll 同款排序（tierFull 降序 + 同级短文案优先）
            try {
                java.util.Collections.sort(out.ruleHits, (a, b) -> {
                    int d = RuleEngine.tierFull(b.appId) - RuleEngine.tierFull(a.appId);
                    if (d != 0) return d;
                    int la = a.label == null ? 0 : a.label.length();
                    int lb = b.label == null ? 0 : b.label.length();
                    return la - lb;
                });
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** v7.5.0：独立倒计时数字解析（倒计时区或跳过候选近旁才计入）。 */
    static void resolveCdDigits(ScanFind f, Ctx ctx) {
        if (f.cdDigits.isEmpty() || ctx.screenH <= 0) return;
        for (int i = 0; i < f.cdDigits.size(); i++) {
            CdDigit d = f.cdDigits.get(i);
            if (d.seconds > f.countdownN && (inCountdownZone(d.bounds, ctx) || nearAnySkip(d.bounds, f, ctx))) {
                f.countdownN = d.seconds;
            }
        }
    }

    static boolean nearAnySkip(int[] b, ScanFind f, Ctx ctx) {
        for (int i = 0; i < f.skipCands.size(); i++) {
            if (nearY(b, f.skipCands.get(i).tapBounds, ctx)) return true;
        }
        for (int i = 0; i < f.fakeCands.size(); i++) {
            if (nearY(b, f.fakeCands.get(i).tapBounds, ctx)) return true;
        }
        return false;
    }

    private static void visit(NodeView node, int depth, int[] budget, ScanFind f, Ctx ctx,
                             RuleRiderState rider) {
        /* v8.0.2 骑士早停：满 max 命中即止（RuleEngine.collect 同停点——
         * 入口不预算不减计数；此时规则路径必走，find 将被服务侧丢弃） */
        if (node == null || depth > 32 || budget[0] <= 0
                || (rider != null && rider.done())) return;
        budget[0]--;
        f.nodeCount++;
        try {
            String text = nz(node.text());
            String desc = nz(node.desc());
            String label = text.length() > 0 ? text : desc;
            String cls = nz(node.cls()).toLowerCase();
            boolean vis = node.visible();
            if (label.length() > LONG_TEXT_LEN) f.longText = true; // 长文本=正常内容页（v3.5.0）
            if (node.editable() || cls.contains("edittext")) f.editPresent = true;
            if (node.scrollable() || cls.contains("scrollview") || cls.contains("recyclerview")
                    || cls.contains("viewpager") || cls.contains("listview")) f.scrollPresent = true;
            /* v7.8.0 WebView 容器铁证（插屏闸⓪扩容配套）：H5 插屏/视频开屏
             * 的文字在网页内核里非原生文本节点（角标探不到）——WebView 在场
             * + 关闭钮在场即插屏签名（李跳跳同款 H5 广告关闭面）。 */
            if (!f.webviewPresent && cls.contains("webview")) f.webviewPresent = true;

            int[] b = node.bounds();
            boolean clickable = node.clickable();
            double areaRatio = areaRatio(b, ctx);
            // v8.0.2 上提：childCount 单读共用（骑士 vid 叶门与子循环同源——
            // 旧版 vid 门与子循环各读一次，纯 getter 语义零变化）
            int n = node.childCount();

            // 全屏热区：可点且 ≥55% 屏幕
            if (!f.hotZone && clickable && areaRatio >= HOT_ZONE_AREA) f.hotZone = true;

            /* ═══ v8.0.2 规则骑士（与决策信号同遍收集——双 DFS 消除）═══
             * 门序与 RuleEngine.collect 逐字段等价：text 门（label>0）优先、
             * else vid 叶子门（label==0 且零子）。异常隔离：骑士自身异常
             * 只丢该节点骑士结果，不拖累决策信号与子树（旧 matchAll 异常
             * 会断子树——新路径异常面只严不减，见 DualDfsParityTest S12）。 */
            if (rider != null && !rider.done()) {
                try {
                    if (vis && b != null && label.length() > 0 && areaRatio <= SKIP_AREA_MAX) {
                        RuleEngine.Hit h = RuleEngine.matchText(label, b, node, ctx);
                        if (h != null) rider.addIfNew(h);
                    } else if (vis && b != null && label.length() == 0 && n == 0
                            && areaRatio <= SKIP_AREA_MAX) {
                        RuleEngine.Hit h = RuleEngine.matchVid(node, b, ctx);
                        if (h != null) rider.addIfNew(h);
                    }
                } catch (Throwable ignored) {
                }
                if (rider.done()) return; // 同 collect：满 max 即止，后续子树不再入
            }

            if (label.length() > 0) {
                if (label.length() <= 14 && matchesSkip(label)) {
                    /* ═══ v4.5.0 乱点根治：兜底路径同款形状门（与 RuleEngine.build
                     * 一致）——菜单/抽屉列表行绝不入候选：issue#42-44 实锤抽屉
                     * 菜单「跳过」项被点（rule 路径已挡，此处堵 walk→decide 兜底
                     * 路径）。被拒节点也不设 fakeSkip（菜单行非陷阱信号）。 ═══ */
                    boolean shapeOk;
                    if (ctx.sdkAdWin || ctx.learnedCls) {
                        shapeOk = true; // 高置信广告窗豁免
                    } else if (b == null || ctx.screenW <= 0) {
                        shapeOk = true; // 形状未知：保守放行（绝不错杀真开屏）
                    } else {
                        double wRatio = (double) (b[2] - b[0]) / ctx.screenW;
                        shapeOk = wRatio <= (clickable ? 0.70 : 0.55) && !isMenuListRow(node);
                    }
                    if (!shapeOk) {
                        // 菜单/全宽行：不入 skipCands、不设 fakeSkip、不进倒计时
                    } else {
                        int cd = parseCountdown(label);
                        if (cd > f.countdownN) f.countdownN = cd; // v3.8.0 倒计时解析（「跳过 3」→3，服务侧门控用）
                        NodeView click = resolveClickable(node, 4);
                        if (click != null && isSmallClick(click, ctx) && vis) {
                            if (!f.foundSkip) {
                                f.foundSkip = true;
                                f.skipNode = click;
                                // v3.6.0：手势点击用【原标签控件】坐标（点得准），
                                // 不再用可点祖先中心（祖先常是全屏容器，点中心=点开广告，
                                // 真机「已点击跳过但广告纹丝不动」根因之一）；performAction 仍走祖先
                                f.skipBounds = (b != null && areaRatio(b, ctx) < SKIP_AREA_MAX) ? b : click.bounds();
                                f.skipLabel = label;
                            }
                            // v3.7.0 多候选收集：广告图内常嵌假「跳过」文本（或同窗多个跳过键），
                            // 复验第 N 次点第 N 个候选——不再同一坐标点四遍（issue#10 根因）
                            if (f.skipCands.size() < SKIP_CANDS_MAX) {
                                SkipCand c = new SkipCand();
                                c.clickNode = click;
                                c.tapBounds = (b != null && areaRatio(b, ctx) < SKIP_AREA_MAX) ? b : click.bounds();
                                c.label = label;
                                c.selfClick = (click == node);
                                f.skipCands.add(c);
                            }
                        } else {
                            f.fakeSkip = true; // 大面积/不可点 = 图内假跳过陷阱
                            // v3.8.0：假跳过≠不可点——大容器可点祖先吞掉 performAction 时，
                            // 标签自身坐标仍是有效触点（GKD clickCenter 方法论）：手势点标签
                            // 位置，由应用按坐标分发到真跳过键。仅收角落形态小标签（防中央诱饵）
                            if (vis && b != null && areaRatio(b, ctx) < FAKE_TAP_AREA_MAX
                                    && learnableSkipShape(b, ctx.screenW, ctx.screenH)
                                    && f.fakeCands.size() < SKIP_CANDS_MAX) {
                                SkipCand c = new SkipCand();
                                c.clickNode = click;
                                c.tapBounds = b;
                                c.label = label;
                                c.gestureOnly = true;
                                f.fakeCands.add(c);
                            }
                        }
                    }
                } else if (label.length() <= 22 && matchesAny(label, GYRO_HINTS)) {
                    f.gyroHint = true;
                } else if (label.length() <= 6 && isCloseText(label)) {
                    NodeView click = resolveClickable(node, 4);
                    if (click != null && isSmallClick(click, ctx) && vis && !f.foundClose) {
                        f.foundClose = true;
                        f.closeNode = click;
                        // v3.6.0：同 L1——手势点原标签坐标，performAction 走可点祖先
                        f.closeBounds = (b != null && areaRatio(b, ctx) < SKIP_AREA_MAX) ? b : click.bounds();
                        f.closeLabel = label;
                    }
                } else if (isAdBadge(label)) {
                    f.adBadge = true;
                    f.badgeLabel = label;
                } else if (label.length() <= 14 && matchesAny(label, CTA_TEXTS)) {
                    f.ctaCount++;
                } else if (label.length() <= CD_STANDALONE_MAX_CHARS && vis && b != null
                        && standaloneCountdownSeconds(label) > 0
                        && f.cdDigits.size() < CD_DIGIT_CAP) {
                    // v7.5.0 独立倒计时数字（兄弟节点）：入候选列表，walk 尾部
                    // 按倒计时区/跳过键近旁门控并入 f.countdownN
                    CdDigit d = new CdDigit();
                    d.seconds = standaloneCountdownSeconds(label);
                    d.bounds = b;
                    f.cdDigits.add(d);
                }
            } else if (b != null && vis && n == 0 && isVidSkip(node)) {
                // v3.8.0：viewId 跳过键（GKD 全局规则同款）：vid 含 skip（穿山甲 tt_splash_skip_btn /
                // 微博 iv_skip 类零文本图片键）或倒计时控件（count+down 非 download）——
                // 零文本图片跳过键的头号信号，此前只能靠角落形态猜，猜不中就只能返回键
                NodeView click = resolveClickable(node, 4);
                if (click != null && isSmallClick(click, ctx)) {
                    if (!f.foundSkip) {
                        f.foundSkip = true;
                        f.skipNode = click;
                        f.skipBounds = b;
                        f.skipLabel = "跳过";
                    }
                    if (f.skipCands.size() < SKIP_CANDS_MAX) {
                        SkipCand c = new SkipCand();
                        c.clickNode = click;
                        c.tapBounds = b;
                        c.label = "跳过";
                        c.selfClick = (click == node);
                        f.skipCands.add(c);
                    }
                } else if (areaRatio(b, ctx) < FAKE_TAP_AREA_MAX
                        && learnableSkipShape(b, ctx.screenW, ctx.screenH)
                        && f.fakeCands.size() < SKIP_CANDS_MAX) {
                    SkipCand c = new SkipCand();
                    c.clickNode = click;
                    c.tapBounds = b;
                    c.label = "跳过";
                    c.gestureOnly = true;
                    f.fakeCands.add(c);
                }
                int cd = parseCountdown(node.viewId());
                if (cd > f.countdownN) f.countdownN = cd;
            } else if (b != null && vis && f.corners.size() < MAX_CORNERS
                    && (isCornerControl(b, ctx) || isMidEdgeControl(b, ctx))
                    && !hasCornerChild(node, ctx)) {
                // 【v3.5.0】角落无字小可点控件（多候选）：疑似图片跳过键
                //（微博/喜马拉雅第一方开屏）；自身不可点时向上找可点祖先；
                // 若还有更小的角落子控件则让位（子控件坐标点得更准）
                //【v7.5.0】中部边带候选（strongOnly）：左/右缘任意高度的
                // 无字小可点控件——仅强广告形态下出手（L4 逐候选门控）
                NodeView click = clickable ? node : resolveClickable(node, 4);
                if (click != null) {
                    CornerCand c = new CornerCand();
                    c.clickNode = click;
                    c.tapBounds = b;
                    c.strongOnly = !isCornerControl(b, ctx);
                    f.corners.add(c);
                }
            }

            for (int i = 0; i < n; i++) {
                // v8.0.2 骑士满额即止（RuleEngine.collect 子循环条件 out.size()<max 同停点）
                if (rider != null && rider.done()) break;
                visit(node.child(i), depth + 1, budget, f, ctx, rider);
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

    /** 角落控件判定：顶部 14% 高内 / v4.3.0 底部 18% 高内（部分应用跳过键
     *  在右下角且位置随机——issue#38「按钮每次出现在不同地方」），
     *  贴右缘(≥72%)或左缘(≤28%)、边长 24px~35% 宽、无任何文本 */
    static boolean isCornerControl(int[] b, Ctx ctx) {
        if (b == null || ctx.screenW <= 0 || ctx.screenH <= 0) return false;
        int w = b[2] - b[0];
        int h = b[3] - b[1];
        if (w < CORNER_MIN_PX || h < CORNER_MIN_PX) return false;
        if (w > ctx.screenW * 0.35 || h > ctx.screenH * 0.20) return false;
        int cy = (b[1] + b[3]) / 2;
        boolean topZone = b[1] <= ctx.screenH * CORNER_TOP_MAX;
        boolean bottomZone = cy >= ctx.screenH * 0.82; // v4.3.0：底部角落（右下/左下）
        if (!topZone && !bottomZone) return false;
        boolean rightEdge = b[2] >= ctx.screenW * CORNER_RIGHT_MIN;
        boolean leftEdge = b[0] <= ctx.screenW * CORNER_LEFT_MAX;
        return rightEdge || leftEdge;
    }

    /** v7.5.0 中部边带控件：左/右缘（≥72%/≤28% 宽）任意高度的无字小可点
     *  控件（尺寸门与角落同源：24px~35% 宽 × 20% 高）。已过角落几何的
     *  （顶/底带）不算中部——strongOnly 分层门控据此分流。 */
    static boolean isMidEdgeControl(int[] b, Ctx ctx) {
        if (b == null || ctx.screenW <= 0 || ctx.screenH <= 0) return false;
        if (isCornerControl(b, ctx)) return false; // 角落带归角落（原门控）
        int w = b[2] - b[0];
        int h = b[3] - b[1];
        if (w < MIDEDGE_MIN_PX || h < MIDEDGE_MIN_PX) return false;
        if (w > ctx.screenW * 0.35 || h > ctx.screenH * 0.20) return false;
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

    /** v7.5.0：是否中部边带形态控件（左/右缘任意高度——服务侧学习与测试用）。 */
    public static boolean midEdgeLike(int[] b, int screenW, int screenH) {
        Ctx c = new Ctx();
        c.screenW = screenW;
        c.screenH = screenH;
        return isMidEdgeControl(b, c);
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
        // v7.5.0 中部边带（左/右缘任意高度）：图片跳过键在右侧中部（用户
        // 实锤「不是每次每个按钮都是一个位置」）——手势候选可学习可代点
        Ctx c = new Ctx();
        c.screenW = screenW;
        c.screenH = screenH;
        if (isMidEdgeControl(b, c)) return true;
        int cy = (b[1] + b[3]) / 2;
        boolean topArea = cy <= screenH * 0.25;
        boolean bottomArea = cy >= screenH * 0.80;
        boolean small = (b[2] - b[0]) <= screenW * 0.5 && (b[3] - b[1]) <= screenH * 0.15;
        return (topArea || bottomArea) && small;
    }

    /** v3.7.0：学习位置健全性校验——归一化坐标必须在屏内有效区（3%~97%）。
     *  真机日志实锤：微信学出 (63%,102%) 越界位置（点击事件源节点 bounds
     *  中心在屏幕外），点了也是死区——此类直接拒学，防止污染规则库 */
    public static boolean saneNormPos(double x, double y) {
        return x >= POS_MIN_NORM && x <= POS_MAX_NORM
                && y >= POS_MIN_NORM && y <= POS_MAX_NORM;
    }

    /* ═════════ v4.5.0 乱点根治：竖列菜单/抽屉列表行检测（李跳跳式形状门）═════════
     * 真机日志实锤（issue#42-44）：应用右侧抽屉（BaseCustomDialogFragment，
     * 播放历史/录音/扫一扫/切换模式 菜单）被引擎当广告窗扫描，菜单内
     * 「跳过」字样列表项被当跳过键点击；用户点开抽屉 4s 让路期满后引擎
     * 连点 2 次 = 「进入 App 乱点」元凶。
     * 判定核心：沿候选祖先链找【列表容器】——候选所在的行与其 ≥3 个
     * 兄弟行【同列同宽竖排】（x 重叠、y 互不重叠）= 垂直菜单列表。
     * ⚠ 比较对象是「候选的祖先行 vs 兄弟行」（行由统一模板渲染必然同宽），
     * 而非「候选自身 vs 行」（文字总比行窄，比不出差异——v4.5.0 首版教训）。
     * 纯 Java 零 Android 依赖，JVM 可单测。 */
    public static boolean isMenuListRow(NodeView node) {
        try {
            if (node == null) return false;
            NodeView self = node;
            NodeView p = node.parent();
            for (int lvl = 0; lvl < 3 && p != null; lvl++) {
                int n = p.childCount();
                if (n >= 4) {
                    int[] sb = self.bounds();
                    if (sb != null && sb[2] > sb[0] && sb[3] > sb[1]) {
                        int sw = sb[2] - sb[0], sh = sb[3] - sb[1];
                        int sameCol = 1; // 含候选所在行自身
                        for (int i = 0; i < n && i < 14; i++) {
                            NodeView c = p.child(i);
                            if (c == null || c == self) continue;
                            int[] cb = c.bounds();
                            if (cb == null || cb[2] <= cb[0] || cb[3] <= cb[1]) continue;
                            int cw = cb[2] - cb[0], ch = cb[3] - cb[1];
                            // ① 同宽（±25%）：菜单行由统一模板渲染，宽度一致
                            if (Math.abs(cw - sw) > Math.max(sw * 0.25, 1)) continue;
                            // ② 同列：x 区间与候选行重叠 ≥ 两者较窄者的 50%
                            int ox = Math.min(sb[2], cb[2]) - Math.max(sb[0], cb[0]);
                            if (ox < Math.min(cw, sw) * 0.5) continue;
                            // ③ 竖排：y 中心间隔 ≥ 两者较高者的 60%（互不重叠的行）
                            int cyS = (sb[1] + sb[3]) / 2, cyC = (cb[1] + cb[3]) / 2;
                            if (Math.abs(cyC - cyS) < Math.max(sh, ch) * 0.6) continue;
                            sameCol++;
                        }
                        if (sameCol >= 4) return true; // 候选行+3 兄弟行同列同宽 = 列表
                    }
                }
                self = p;
                p = p.parent();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** v4.5.0：文本规则命中形状门（RuleEngine.build 后置校验共用）。
     *  真跳过键形态（全分辨率自适应的相对判定，参考李跳跳形状白名单）：
     *   · 标签控件自身宽度 ≤55% 屏宽（全宽行 = 列表项/横幅，绝不点）；
     *   · 自身可点的按钮放宽到 ≤70%（部分广告底部「点击跳过」长条按钮）；
     *   · 竖列菜单行一票否决（isMenuListRow）。
     *  豁免：SDK 广告窗 / 学习窗指纹窗（上下文已是高置信广告场景）。 */
    public static boolean validSkipTextShape(NodeView node, int[] b, Ctx ctx) {
        if (b == null || b.length < 4 || ctx.screenW <= 0) return false;
        int w = b[2] - b[0];
        if (w <= 0) return false;
        boolean selfClickable = false;
        try {
            selfClickable = node != null && node.clickable();
        } catch (Throwable ignored) {
        }
        double wRatio = (double) w / ctx.screenW;
        if (selfClickable ? wRatio > 0.70 : wRatio > 0.55) return false;
        if (isMenuListRow(node)) return false;
        return true;
    }

    /* ═════════ 决策阶梯 ═════════ */

    public static Decision decide(ScanFind f, Ctx ctx) {
        /* ═══ v5.0.0 小程序/团购容器保护（L0，纵深防御第四层）═══
         * 容器上下文里决策恒 NONE——即使 ScanFind 被外层填了候选
         * （规则引擎/原生探针/订阅命中），也绝不在这里兑换成动作。
         * 返回键同样禁止：容器内按返回 = 把用户踢出团购券页。 */
        if (MiniProgramGuard.isMiniProgramWindow(ctx.pkg, ctx.activityId)) {
            return Decision.none();
        }
        /* ── 开屏/插屏窗内 ── */
        if (ctx.splashLive) {
            /* ═══ v7.9.5 WebView 综合证据路径（真广告+ScrollView/Input/WebView 专项）══
             * isAdLikeLoose 对滚动容器/输入框/大树（WebView 视频容器常态
             * >150 节点）形成过强阻断——真 H5 广告带跳过文本时整窗漏拦。
             * 用户指令六红线：不能简单取消安全门——综合判断 = WebView 容器
             * 在场【且】至少一个广告语境证据（角标/SDK 窗指纹/Dialog 形态/
             * 全屏热区）同窗。WebView 单独在场【不】构成证据（应用内浏览器
             * 普通页面不因 WebView 使命中——误触红线）；候选语义（跳过词族）
             * +形状门+SafetyGuard 终闸全部在岗不变。 */
            boolean webviewAdEvidence = f.webviewPresent
                    && (f.adBadge || ctx.sdkAdWin || f.dialogForm || f.hotZone);
            /* v3.7.0 学习位置【前置】到 L1 之前（issue#10 根因）：
             * 用户手动点通过的位置 = 真实手指触点真正生效的坐标——比盲点
             * 文本候选可靠得多（文本候选可能是广告图内假「跳过」文本）。
             * 教过一次，下次同窗优先直点这个位置。 */
            // L1.4 学习位置点击（v3.7.0 前置）：手动点过的位置指纹 → 次启同窗 500ms 直点
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
            /* L1 文本/vid 跳过键多候选轮换（真候选 → 假跳过手势候选统一梯子）
             * v3.8.0（issue#10 二轮·GKD 方法论对齐）：
             *  · 新增假跳过手势候选——「跳过」文案+大容器可点祖先（微博实锤：isSmallClick
             *    判假→从不点击只返回键，返回键又被广告拦截）→ 手势直点标签自身坐标
             *    （clickCenter），performAction 大容器=点开广告绝不走；
             *  · walk 内候选排序：自身可点/短文案/小面积优先，真键排前
             * v7.9.4（FalsePositiveScenarioTest 实锤根治——孤立「跳过」词红线）：
             *  · 旧版 R+F>0 即出手——零页面形态证据。开屏窗龄（12s）内的聊天窗/
             *    内容列表页里孤立出现的「跳过」字样（聊天气泡/列表行文案）会被
             *    直接当成跳过键点击（用户 20 类误触红线场景 10/孤立词红线）。
             *  · 证据质量提升（不是关闭扫描）：出手需页面形态证据——
             *    广告形态树 isAdLikeLoose（无滚动/无输入框/≤150 节点——真开屏
             *    窗恒满足：静态图+跳过键从不带滚动容器或输入框）或 SDK 广告窗
             *    指纹或学习指纹。内容页（可滚/可输入）与聊天窗的孤立词一律拒。
             *    与 L1.6 关闭键路径（v3.7.0 起同款 isAdLikeLoose 门）口径对齐。 */
            int R = f.skipCands.size();
            int F = f.fakeCands.size();
            if (R + F > 0 && (isAdLikeLoose(f) || ctx.sdkAdWin || ctx.learnedCls || webviewAdEvidence)) {
                int idx = Math.min(ctx.clickAttempts, R + F - 1);
                if (idx < R) {
                    SkipCand c = f.skipCands.get(idx);
                    return click(c.clickNode, c.tapBounds, "skip_text");
                }
                SkipCand c = f.fakeCands.get(idx - R);
                return click(null, c.tapBounds, "fake_skip_tap"); // 手势专用：大容器祖先不 performAction
            }
            // L1.6 开屏×关闭键（v3.5.0）：顶部小关闭键 + 广告形态树（非长文本内容页）
            //     v3.7.0 树上限放宽（CORNER_TREE_MAX_NODES）：广告窗树常大，
            //     40 节点上限把真关闭键挡在门外（微博根因之一）
            //     v7.5.0 前置 L1.5（角标在场关闭键）：角标替换「顶部 35%」成为
            //     第二证据——插屏×常在右下/中部，旧 L1.6 位置闸把它们全灭
            if (f.foundClose && f.closeBounds != null && f.adBadge && !f.longText
                    && (isAdLikeLoose(f) || ctx.sdkAdWin || ctx.learnedCls || webviewAdEvidence)
                    && ctx.elapsedMs >= SPLASH_CLOSE_MS) {
                return click(f.closeNode, f.closeBounds, "badge_close");
            }
            if (f.foundClose && f.closeBounds != null && !f.longText
                    && (isAdLikeLoose(f) || ctx.sdkAdWin || ctx.learnedCls || webviewAdEvidence)
                    && inTopArea(f.closeBounds, ctx)
                    && ctx.elapsedMs >= SPLASH_CLOSE_MS) {
                return click(f.closeNode, f.closeBounds, "splash_close");
            }
            // L4 角落无字小可点控件（图片跳过键，多候选）→ 手势点击其中心
            //     v3.5.0：复验失败换下一个候选（第 N 次点击用第 N 个候选点）
            //     v3.6.0：改纯手势（node=null）——performAction 可能落到大热区
            //     祖先（=点开广告本体）；手势点子控件坐标=真实手指点跳过键
            //     v3.7.0：① 前置到 L3 之前（微博根因：陀螺仪文案 120ms 抢先
            //     返回键，角落键 500ms 才轮到——有点击候选先点击，点不到再断）；
            //     ② 树上限放宽 CORNER_TREE_MAX_NODES=150（广告窗树常大）
            //     v3.8.0：前置到 L2 直断之前——角落键 500ms 时机先于 700ms 直断，
            //     有可点目标绝不先按返回键
            if (!f.corners.isEmpty() && ctx.clickAttempts < f.corners.size()
                    && !f.scrollPresent && !f.editPresent
                    && f.nodeCount <= (ctx.videoLike ? com.zai.adshield.core.VideoSplashGuard.VIDEO_CORNER_TREE_MAX : CORNER_TREE_MAX_NODES)
                    && ctx.elapsedMs >= CORNER_MIN_MS
                    && ctx.elapsedMs <= (ctx.videoLike ? com.zai.adshield.core.VideoSplashGuard.VIDEO_EXT_WINDOW_MS : CORNER_MAX_MS)
                    // v3.9.0 广告形态确认门（不乱点红线）：角落无字控件必须
                    // 同时具备广告信号（热区/角标/SDK 窗/学习窗）或树极简
                    //（≤60 节点）才出手——普通主页角落图标绝不误点
                    // v7.5.0 中部边带候选（strongOnly）：树极简兕底不适用——
                    // 仅强广告形态（热区/角标/SDK窗/学习窗）可出手（FAB 防线）。
                    // 逐候选分层门控：从第 clickAttempts 个起取首个过闸的候选
                    //（无 strongOnly 候选时与旧「第 N 击第 N 候选」逐字等价）
                    // v7.8.0 视频画像入强广告形态位（视频开屏窗 + 零候选连环 +
                    // 树静默——非广告窗零候选时轮询针不接管，画像不成立）
                    && (f.hotZone || f.adBadge || ctx.sdkAdWin || ctx.learnedCls || ctx.videoLike
                        || f.nodeCount <= CORNER_CONFIRM_NODES)) {
                boolean strongAdForm = f.hotZone || f.adBadge || ctx.sdkAdWin || ctx.learnedCls || ctx.videoLike;
                for (int ci = ctx.clickAttempts; ci < f.corners.size(); ci++) {
                    CornerCand c = f.corners.get(ci);
                    boolean tierOk = !c.strongOnly || strongAdForm;
                    boolean gateOk = strongAdForm || f.nodeCount <= CORNER_CONFIRM_NODES;
                    if (tierOk && gateOk) {
                        return click(null, c.tapBounds, "corner_image_skip");
                    }
                }
            }
            // L2 SDK / 学习 className 窗口指纹 → 返回键直断
            //    SDK 广告窗是独立 Activity，返回回到应用内页——根窗口也安全。
            //    v3.6.0 学习指纹快断加双保险（真机事故：毒指纹把主页学成广告窗
            //    → 入口窗快断=按返回键退出应用）：① 仅非根窗口或 SDK 窗可快断；
            //    ② 须为广告形态树（信息流等可滚动页面绝不快断）
            //    v5.1.0 弹窗安全门：DynamicDialog 类对话框窗（f.dialogForm）
            //    须再过宽松广告形态树（无滚动/无输入框）——带滚动条的运营
            //    弹窗/表单弹窗绝不返回键，只允许真正的图片广告弹窗直断。
            boolean sdkCut = ctx.sdkAdWin && ctx.elapsedMs >= CLS_BREAK_MS
                    && (!f.dialogForm || isAdLikeLoose(f));
            boolean learnedCut = ctx.learnedCls && ctx.elapsedMs >= CLS_LEARNED_BREAK_MS
                    && (!ctx.rootWindow || ctx.sdkAdWin) && isAdLike(f);
            if (sdkCut || learnedCut) {
                return back(learnedCut && !sdkCut ? "sdk_window_learned" : "sdk_window");
            }
            /* v3.6.0 根窗口返回键安全门 ─────────────────────────────
             * 应用入口窗（冷启动开屏/回前台首窗）上按返回键 = 退出应用。
             * 真机事故（喜马拉雅）：v3.5.0 返回键兜底 → 喜马拉雅拦截返回
             * 弹「再按一次退出到桌面」→ 引擎 600ms 后再按 → 真退到桌面，
             * 用户「第二次进入就被模拟人操作退出来」。
             * 此后 L3/L4.5/L5 的返回键决策仅在应用内窗（返回回到应用内）
             * 或 SDK 广告窗上生效；根窗口只走点击梯子，用尽优雅收手。 */
            boolean backSafe = !ctx.rootWindow || ctx.sdkAdWin;
            // L3 陀螺仪/假跳过：无倒计时立即直断；带倒计时(假跳过) 800ms 直断
            //     v3.6.0 加信息流安全门：可滚动/有输入框的页面出现「摇一摇/翻转」文案
            //     多为互动玩法或 feed 内容（如「摇一摇抽签」），绝不返回键（防主页误退）
            //     v3.7.0 移到 L4 之后（微博根因）：有可点候选先点击，返回键是
            //     「无任何可点目标」时的兜底（广告 Activity 常拦截返回键）；
            //     gyro_immediate 加「无可点候选」门——角落键 500ms 才到时机，
            //     有候选时等它，不 120ms 抢跑返回键
            boolean clickableCand = !f.corners.isEmpty() || !f.skipCands.isEmpty() || !f.fakeCands.isEmpty() || f.foundClose;
            if (backSafe && f.gyroHint && !f.fakeSkip && !clickableCand
                    && !f.scrollPresent && !f.editPresent
                    && ctx.elapsedMs >= GYRO_IMMEDIATE_MS) {
                return back("gyro_immediate");
            }
            if (backSafe && (f.gyroHint || f.fakeSkip) && !f.scrollPresent && !f.editPresent
                    && ctx.elapsedMs >= GYRO_BREAK_MS) {
                return back("gyro_break");
            }
            // L4.5 点击复验失败升级（v3.5.0）：同窗点击 ≥3 次仍未关闭 → 返回键兜底
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
            /* v3.9.0：L6 坐标级点击兜底已【整体移除】——固定百分比坐标
             * 在不同屏幕上位置不同，盲点必然乱点（issue#12 闪退毒链：
             * 桌面被误判→盲点桌面图标→拉起微信→回弹踢出用户）。零信号
             * 场景由学习位置（用户教过）与 RuleEngine 规则接管，绝不盲点。 */
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

    /** v3.7.0 宽松版简单树（角落键/关闭键/坐标兜底用）：广告窗控件树常大
     *  （微博真机：>40 节点把真角落键挡在门外），只要无滚动/无输入框且
     *  节点 ≤150 即认可；热区直断/学习指纹快断仍用严格版 isAdLike 防误判 */
    static boolean isAdLikeLoose(ScanFind f) {
        if (f.scrollPresent || f.editPresent) return false;
        return f.nodeCount <= CORNER_TREE_MAX_NODES;
    }

    /* ───────── v5.1.0 非主流 App 开屏噪声退避（issue#46/47 实锤） ─────────
     * 日志实锤：校园/工具类 App（school.app 9 秒内 7 条「开屏未命中」——
     * 纯校徽启动页无广告可跳）与商店首页（market 145 节点零候选）被反复
     * 整树扫描：纯功耗浪费 + 无谓风险面（多一次扫描就多一次误判机会）。
     * 零候选退避：同窗连续 N 次零候选扫描 + 窗口已过 M ms → 本窗剩余
     * 固定扫描档取消；内容事件救回通道（nudge）不受影响——晚到的真
     * 跳过键出现即触发内容事件，照点不误。服务侧调用，纯逻辑 JVM 可单测。 */
    public static final int ZERO_CAND_SCANS_KILL = 3;   // 常规窗：连续零候选扫描次数门槛
    public static final long ZERO_CAND_KILL_MS = 2600;  // 且窗口已过 2.6s（真广告按钮最晚 1~2s 渲染）
    public static final int TINY_TREE_NODES = 6;        // 微树（加载占位窗：FrameLayout 4 节点类）
    public static final int TINY_TREE_SCANS_KILL = 2;   // 微树 2 次即退（占位窗本就秒级消失）
    public static final long TINY_TREE_KILL_MS = 1200;  // 微树退避最早时机
    public static final long POST_SKIP_QUIET_MS = 2200; // 跳过成功后静默期（首页内容落定）

    /** 零候选退避判定：是否取消本窗剩余固定扫描档（nudge 救回通道不受影响）。
     *  供服务侧在每个根扫描后调用；广告窗/学习窗/Dialog 窗由服务侧先行豁免
     *  （窗口本身就是广告，按钮可能晚到——绝不退避）。微树（≤6 节点）是
     *  加载占位窗，退得更快（school.app 实锤 4 节点 FrameLayout 连环窗）。 */
    public static boolean zeroCandBackoff(int zeroCandScans, long elapsedMs, int nodeCount) {
        if (nodeCount > 0 && nodeCount <= TINY_TREE_NODES) {
            return zeroCandScans >= TINY_TREE_SCANS_KILL && elapsedMs >= TINY_TREE_KILL_MS;
        }
        return zeroCandScans >= ZERO_CAND_SCANS_KILL && elapsedMs >= ZERO_CAND_KILL_MS;
    }

    /* ═══ v8.1.0 零候选退避豁免判定（真机反馈「很多 App 跳不过」根因之三）═══
     * 服务侧原内联五豁免（SDK 广告窗类/Dialog/视频画像/arm-hint/学习指纹）
     * 提取为纯函数（JVM 可回归）+ 新增第六豁免：
     *  · 第一方入口窗（rootWindow）且无订阅覆盖——开屏广告与主界面同窗的
     *    App，其跳过按钮常 3s+ 才渲染，「3 次零候选+2.6s」杀链在按钮出现
     *    前就把本窗剩余固定档全灭（真机实锤：晚出键只能赌内容事件救回，
     *    而 4s 后 nudge 断流 = 整窗失明）。
     *  · 豁免只放宽【单窗内】杀链时机（梯子跑满 12s 观察窗）；跨窗功耗
     *    治理仍由 CLEAN 档案兜底（连续 3 窗零候选 → 纯探针哨兵）——功耗
     *    上限不变，观察覆盖显著提升（18 档全跑 vs 3 档杀链）。
     *  · 有订阅覆盖的窗不豁免（订阅路径 {0,700} 低频档 + 规则直查已保障；
     *    避免订阅覆盖面内功耗回退）。 */
    public static boolean zeroCandExempt(boolean adCls, boolean dialogCls, boolean videoLike,
                                         boolean armHint, boolean learnedCls,
                                         boolean rootWindow, boolean subCovered) {
        if (adCls || dialogCls || videoLike || armHint || learnedCls) return true;
        return rootWindow && !subCovered;
    }

    /** 跳过成功后静默期：广告刚关掉，首页内容还在落定（waimaiii 实锤：复验
     *  通过同一秒又对 79 节点首页整树扫描）——静默期内不记未命中日志。
     *  第二帧广告（DspActivity 链）是独立窗口事件，不受本静默期影响。 */
    public static boolean postSkipQuiet(long adClosedAt, long now) {
        return adClosedAt > 0 && now - adClosedAt < POST_SKIP_QUIET_MS;
    }

    /** v5.5.1（issue#56/#57/#64 实锤）：候选样本「中心在屏内」判定。
     *  日志实锤的越界样本：播放列表@(16%,150%)、vid:checkbox@(187%,17%)、
     *  返回@(110%,9%)——弹窗/对话框 bounds 被裁剪或坐标系差异导致中心
     *  超出屏幕，对补规则零价值还误导（越界点位点了也不在屏上）。
     *  容差 2%：贴边控件（x=100% 的右缘贴边键）不算越界。
     *  纯函数：服务侧 collectSamples 过滤 + JVM 单测共用。 */
    public static boolean sampleOnScreen(double cxPct, double cyPct) {
        return cxPct >= -SAMPLE_TOLERANCE && cxPct <= 100 + SAMPLE_TOLERANCE
                && cyPct >= -SAMPLE_TOLERANCE && cyPct <= 100 + SAMPLE_TOLERANCE;
    }

    /** 样本越界容差（百分比，贴边键不算越界） */
    public static final double SAMPLE_TOLERANCE = 2.0;

    /** v5.1.0：扫描画像是否存在「可出手候选」（零候选退避判定用）。
     *  角落候选须过 L4 广告形态确认门才算数——school.app 实锤：校徽页
     *  fl_scan 扫码键是角落形态（无文本小控件）但整窗无广告信号（135
     *  节点/无角标/无热区/非 SDK 窗），L4 拒点它——若角落形态单独就算
     *  「有候选」，退避永远不触发 = 校徽页继续被扫到天荒地老。 */
    public static boolean hasActionableCand(ScanFind f, Ctx ctx) {
        if (!f.skipCands.isEmpty() || !f.fakeCands.isEmpty() || f.foundClose || f.foundSkip) return true;
        if (f.corners.isEmpty()) return false;
        return f.hotZone || f.adBadge || ctx.sdkAdWin || ctx.learnedCls
                || f.nodeCount <= CORNER_CONFIRM_NODES;
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

    /* v7.3.0 算法：结果缓存——isAdActivityClass 调用点极高频（每个
     * WINDOW_STATE_CHANGED · 每轮 tryScan · 每个退避/链路判定），而窗口
     * 类名集合高度有限（几十个）；缓存后每类名只算一次（原每调用
     * toLowerCase + split("[.$]") 隐式正则编译）。上限 512 封顶重置。 */
    private static final java.util.HashMap<String, Boolean> AD_CLS_CACHE = new java.util.HashMap<>(64);

    public static boolean isAdActivityClass(String cls) {
        if (cls == null || cls.length() < 6) return false;
        synchronized (AD_CLS_CACHE) {
            Boolean hit = AD_CLS_CACHE.get(cls);
            if (hit != null) return hit.booleanValue();
            boolean r = isAdActivityClassScan(cls);
            if (AD_CLS_CACHE.size() > 512) AD_CLS_CACHE.clear();
            AD_CLS_CACHE.put(cls, Boolean.valueOf(r));
            return r;
        }
    }

    private static boolean isAdActivityClassScan(String cls) {
        String c = RuleEngine.lowerAscii(cls);
        if (c.contains("qq.e.ads") || c.contains("gms.ads") || c.contains("bytedance.sdk")
                || c.contains(".ad.activity") || c.contains(".ads.activity")
                || c.contains("baidu.mobads") || c.contains("csjad") || c.contains("unionad")
                || c.contains("miui.zeus") || c.contains("doubleclick")
                || c.contains("adview") || c.contains("youmi") || c.contains("domob")) return true;
        String[] seg = c.split("[.$]");
        for (String s : seg) {
            if (s.length() < 3) continue;
            /* v5.1.0（issue#46/47 实锤）：AdsActivity——第三方 App 自建开屏
             * 广告窗的最高频命名（www 等），旧指纹只认 "adactivity" 整段，
             * "adsactivity" 漏网 = 整窗无指纹（零 vid-close 点击/零返回兜底） */
            if (s.equals("adactivity") || s.equals("advertactivity") || s.equals("adsactivity")) return true;
            /* v5.1.0：DspActivity——美团系开屏 DSP 广告独立窗（返回=回主界面，
             * 安全）；DynamicDialog——美团动态广告弹窗（配合订阅关闭键规则，
             * 返回兜底由 decide() 的 dialogForm 安全门再管一道） */
            if (s.contains("dsp") || s.contains("dynamicdialog")) return true;
            if (s.contains("openadsdk") || s.contains("adsdk") || s.contains("kwad")
                    || s.contains("gdtadv") || s.contains("admob") || s.contains("sigmob")
                    || s.contains("adcolony") || s.contains("applovin") || s.contains("vungle")
                    || s.contains("mbridge") || s.contains("tanx") || s.contains("mobads")
                    || s.contains("splashad") || s.contains("adsplash") || s.contains("landingpage")
                    || s.contains("adpage") || s.contains("adlanding")
                    || s.contains("xiaomi.ad") || s.contains("zeus")) return true;
            /* v5.6.0 激进化指纹扩充（用户实锤：好多广告没有命中——整窗
             * 无指纹 = 零 vid-close 点击 + 零返回键兜底，只能干看广告走完）：
             *  · unionsplash：穿山甲 UnionSplashActivity（SDK 开屏独立窗
             *    最高频漏网命名；绝不把普通 splashactivity 认成广告窗——
             *    App 自建开屏容器按返回=退出应用，红线不碰）；
             *  · interstitial / insertscreen / insertadscreen：插屏广告窗
             *    （AdMob InterstitialActivity / 国内 SDK InsertScreenActivity）；
             *  · advertising / advertad：广告窗高频命名及变体；
             *  · promoad / bonusad：促销/激励弹层窗（游戏 SDK 常见）。
             *    以上全是 SDK 侧命名约定，非 App 业务窗命名——误报面可控。 */
            if (s.contains("unionsplash") || s.contains("interstitial")
                    || s.contains("insertscreen") || s.contains("insertadscreen")
                    || s.contains("advertising") || s.contains("advertad")
                    || s.contains("promoad") || s.contains("bonusad")) return true;
            /* v5.7.0 扫描日志继续激进化（用户二轮实锤：可以大胆激进一点）——
             * 国际主流 SDK 漏网指纹 +12 档（未命中诊断日志实锤：整窗
             * 无指纹 = 零 vid-close 点击 + 零返回键兜底）：
             *  · adunit：Unity Ads AdUnitActivity（国际游戏广告主力）；
             *  · ironsource：IronSource ControllerActivity/AdsActivity；
             *  · chartboost：Chartboost CBImpressionActivity；
             *  · audiencenetwork：Meta Audience Network 独立广告窗；
             *  · inmobi / tapjoy：对应 SDK 广告窗（纯广告 SDK 命名，
             *    业务 App 绝无此类包段——误报面可控）；
             *  · rewardvideo / rewardad：激励视频独立窗（游戏 SDK 高频）；
             *  · expressad：穿山甲模板广告窗变体；
             *  · adpresent：广告 present 独立窗；
             *  · fullscreenad / fullvideo：全屏视频广告窗。
             *  以上全是 SDK 侧命名约定，非 App 业务窗命名；App 自建
             *  SplashActivity/MainActivity 依旧绝不误认（红线不碰）。 */
            if (s.contains("adunit") || s.contains("ironsource")
                    || s.contains("chartboost") || s.contains("audiencenetwork")
                    || s.contains("inmobi") || s.contains("tapjoy")
                    || s.contains("rewardvideo") || s.contains("rewardad")
                    || s.contains("expressad") || s.contains("adpresent")
                    || s.contains("fullscreenad") || s.contains("fullvideo")) return true;
            /* v5.8.0 扫描日志三轮激进（用户实锤：好多广告没有命中——大胆
             * 激进一点）：聚合/国际 SDK 漏网指纹 +4 段：
             *  · mintegral——Mintegral 汇量国际 SDK 广告窗主力命名；
             *  · pangle——穿山甲国际版 Pangle 独立广告窗；
             *  · topon——TopOn 聚合广告窗（聚合器命名约定）；
             *  · moloco——Moloco 国际 SDK 广告窗。
             *  全是 SDK 侧命名约定，非 App 业务窗命名——误报面可控；
             *  App 自建 SplashActivity/MainActivity/UnityPlayerActivity
             *  依旧绝不误认（红线不碰）。 */
            if (s.contains("mintegral") || s.contains("pangle")
                    || s.contains("topon") || s.contains("moloco")) return true;
            /* v7.9.1 淘宝 TMS 营销容器指纹（用户实锤：taobao TMSActivity 12
             * 节点 WebView 容器——广告语境/关闭钮均不在原生树（H5 内核渲染），
             * 三路语境全空 → 插屏整窗漏拦）。TMS = 淘宝营销系统容器窗
             *（TmsActivity/TMSActivity——阿里系广告/营销落地页约定命名，
             * 业务 App 窗口绝无此命名）。收编收益：① wctx.sdkAdWin=true →
             * 插屏闸⓪第三路在场证据接通（WebView+窗指纹双证据）；②
             * 图标关闭钮路径语境闸放行（纯图标 × 钮可出手）；③ videoLike
             * 的 sdkAdWin 路径（零候选 streak≥3 + 窗龄≥3.2s）接通。
             * 误报面：段级 contains（tmsactivity）——普通业务窗命名
             * TmsActivity 的概率极低且此类窗本身即营销容器。 */
            if (s.contains("tmsactivity")) return true;
            /* v5.9.0 四轮激进（用户实锤：继续大胆激进——国内聚合漏网主力）：
             *  · ksad——快手 KSAdSDK 广告窗命名段（com.ksad.* 家族与
             *    com.kwad.* 并行的真实包段，LearnGovernor 泛化表已认、
             *    指纹表此前漏网 = 整窗零 vid-close 点击零返回兜底）；
             *  · csj_——穿山甲巨量引擎联盟窗复合命名段（csj_splash /
             *    csj_reward 等——LearnGovernor 泛化表已认 csj_splash，
             *    指纹表此前只认 csjad 整串漏掉 csj_ 复合族）。刻意不用
             *    裸 csj 短段：com.csj 业务包碰撞面（误报→根窗返回键=
             *    退出应用红线），csj_ + csjad 双形态已全覆盖联盟命名。
             *  两段均为纯广告 SDK 侧命名约定，业务 App 窗口绝无此命名
             *  ——误报面可控；游戏/下载/登录红线对照进测试断言。 */
            if (s.contains("ksad") || s.contains("csj_")) return true;
            /* v6.0.0 五轮激进（用户实锤：扫描日志继续优化——大胆激进）：
             *  · mobads——百度联盟 Baidu Mob Ads 广告窗命名段
             *    （com.baidu.mobads.sdk.* 家族）；
             *  · sigmob——Sigmob 聚合广告 SDK 窗（com.sigmob.* 命名族）；
             *  · adcolony——AdColony 国际视频广告 SDK 窗；
             *  · vungle——Liftoff Vungle 国际激励视频 SDK 窗。
             *  四段均为纯广告 SDK 侧命名约定，业务 App 窗口绝无此命名
             *  ——误报面可控；游戏/下载/登录红线对照进测试断言。 */
            if (s.contains("mobads") || s.contains("sigmob")
                    || s.contains("adcolony") || s.contains("vungle")) return true;
        }
        return false;
    }

    /** v3.9.0：桌面/Launcher 识别（issue#12 闪退根治）——桌面包事件在服务侧
     *  直接丢弃：绝不扫描（防误判广告窗）、绝不学习（防桌面图标位置被学成
     *  「跳过键」，真机实锤 com.miui.home 学出位置(39%,18%)）、绝不回弹
     *  （防「桌面→微信」被当广告跳转踢出用户）。 */
    public static boolean isLauncherLike(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        if (p.contains("launcher")) return true;
        if (p.endsWith(".home") || p.equals("home")) return true;
        if (p.contains("lawnchair") || p.contains("trebuchet")) return true; // 第三方桌面
        return p.equals("com.miui.home") || p.equals("com.google.android.apps.nexuslauncher");
    }

    /** v3.13.0：短信/彩信/来电类覆盖层包识别（真机实锤：接收验证码时顶部
     *  横幅被引擎点掉）。这些包的事件在服务侧直接丢弃——绝不扫描（验证码
     *  横幅的小 ×/可点区域绝不被当「角落跳过键」点击）、绝不学习（手动点
     *  「复制验证码」绝不被学成跳过键位置）。
     *  覆盖：MIUI 短信（com.android.mms）/ 小米短信增强（com.miui.smsextra）/
     *  Google Messages / Samsung Messaging / 来电界面（incallui——点错=挂断电话）。 */
    public static boolean isSmsOverlayPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return p.contains(".mms") || p.contains("smsextra")
                || p.contains("messaging") || p.contains("incallui");
    }

    /** v4.1.0：相机/拍摄类包识别（issue#30 实锤：微信点「拍摄」进相机即被
     *  引擎点「×」强退）。相机预览界面 = SurfaceView 简单树 + 顶部关闭键，
     *  与开屏广告窗特征雷同——但相机界面【绝不该被自动点击】，硬黑名单。 */
    public static boolean isCameraPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return p.contains("camera"); // com.android.camera / GoogleCamera / 各厂商与第三方相机 App
    }

    /** v4.1.0：相机/拍摄/扫码类窗口类名识别——微信拍摄（plugin.camera.ui.
     *  CameraActivity）、抖音故事相机、各 App 拍摄页、扫码 CaptureActivity。
     *  这些窗口绝不扫描、绝不点击、绝不学习。 */
    public static boolean isCameraClass(String cls) {
        if (cls == null || cls.length() == 0) return false;
        String c = cls.toLowerCase();
        return c.contains("camera")       // CameraActivity / CameraUI
                || c.contains("capture")  // CaptureActivity（拍摄/扫码）
                || c.contains("shoot")    // ShootActivity（拍摄页）
                || c.contains("videoedit"); // 视频编辑（紧邻拍摄的敏感操作区）
    }

    /** v4.2.0：聊天主界面门（issue#31-37 日志实锤：微信主界面 LauncherUI 内
     *  GKD 订阅规则反复命中连点 = 「进入微信左右乱划」元凶）。微信主界面
     *  没有任何跳过键（开屏广告从不在 LauncherUI 里出现），主界面期间
     *  开屏扫描链整体让路——绝不扫描绝不点击，聊天列表滚动的高频内容
     *  变化也不再触发整树扫描（功耗同步大降）。Dialog 弹窗（青少年模式等）
     *  是独立窗口事件，不受此门影响。QQ 主界面历史命名 SplashActivity
     *  与开屏窗无法区分，不纳入（仅微信）。 */
    public static boolean isChatMainUi(String pkg, String cls) {
        if (pkg == null || cls == null || cls.length() == 0) return false;
        if (!"com.tencent.mm".equals(pkg)) return false;
        return cls.contains("LauncherUI");
    }

    /** v4.2.0：目标节点是否「跳过/关闭」形态（订阅规则安全门用）——
     *  无 activityIds 限定的社区规则只允许点 skip/close 形态的控件，
     *  微信底部的 Tab/翻页指示器等普通控件绝不代点。 */
    public static boolean looksSkipLike(String text, String desc, String viewId) {
        if (matchesSkip(text) || isCloseText(text)) return true;
        if (matchesSkip(desc) || isCloseText(desc)) return true;
        if (viewId != null) {
            String v = lowerAscii(viewId);
            if (v.contains("skip") || v.contains("close") || v.contains("dismiss")
                    || v.contains("btn_close") || v.contains("ib_close")) return true;
        }
        return false;
    }

    /** v4.3.0：全局组订阅规则（id='*' 无 Activity 限定）的诱导词否决——
     *  目标文案含摇/晃/翻/领/福利/下载等诱导词一律不代点（与主引擎
     *  短包含层同源红线：「摇一摇跳过广告领福利」类绝不中）。 */
    public static boolean safeGlobalLabel(String text, String desc) {
        return !hasInducementWord(text) && !hasInducementWord(desc);
    }

    /** 诱导词黑名单（主引擎 DANGEROUS_WORDS 同源，供订阅全局组门用；
     *  v5.1.0 起 public——测试与诊断日志共用） */
    public static boolean hasInducementWord(String s) {
        if (s == null || s.length() == 0) return false;
        for (String w : INDUCE_WORDS) {
            if (s.contains(w)) return true;
        }
        return false;
    }

    public static final String[] INDUCE_WORDS = {
            "摇", "晃", "翻", "转", "扭", "领", "福利", "详情", "下载", "开通",
            "注册", "登录", "抽奖", "红包", "进入", "查看", "立即",
            // v5.1.0（安全性增补）：营销 CTA 高频词——真跳过键文案数学上
            // 不含这些词（「跳过」「跳过 3」「3s后可跳过」），加进来只会
            // 更严不会误伤；「跳过领礼包」「跳过抢购」类诱饵标签绝不代点
            "抢购", "秒杀", "礼包", "优惠", "补贴", "立减", "低价", "充值",
    };

    /* ═══ v7.3.0 安全：支付/资金词黑名单（订阅规则注入防线）═══
     * 订阅规则能让引擎点击任意命中的控件——恶意/被劫持订阅源可定向
     * 点击「确认支付/立即付款/确认订单」类按钮（规则名伪装「跳过」+
     * activityIds 限定支付页即可绕过旧文本门）。真跳过键文案数学上
     * 不含任何资金词，只紧不松；与 INDUCE_WORDS 一起构成订阅目标的
     * 双重否决（见 Subscription.dfsMulti）。 */
    public static final String[] PAYMENT_WORDS = {
            "支付", "付款", "支付密码", "付款码", "收银", "转账", "充值",
            "确认订单", "提交订单", "立即购买", "马上购买", "确认购买",
            "去支付", "去付款", "借款", "还款", "刷脸", "免密",
    };

    /** v7.3.0：支付/资金词否决（订阅目标文本/描述含任一词 → 绝不代点） */
    public static boolean hasPaymentWord(String s) {
        if (s == null || s.length() == 0) return false;
        for (String w : PAYMENT_WORDS) {
            if (s.contains(w)) return true;
        }
        return false;
    }

    /* ═══ v7.3.0 算法：ASCII 快路径小写归一（零分配 +土耳其语 locale 根治）═══
     * 无 A-Z 时原串直接返回（类名/vid/文案 90% 命中零分配）；有 A-Z 时
     * 单次字符数组改写。与 toLowerCase() 的差异仅限非 ASCII 大小写字
     * （如土耳其 İ）——关键词表全部 ASCII/CJK，匹配语义零变化；顺带
     * 根治「无 Locale 的 toLowerCase 在 tr-TR 设备把 SKIP 写成 skıp」
     * 导致整层精确匹配丢失的 locale bug。 */
    static String lowerAscii(String s) {
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

    /** v3.13.0：验证码横幅文案标记——树内任一节点命中即视为验证码/通知横幅，
     *  该窗口绝不扫描、绝不点击、绝不学习（银行/支付登录页同理放行，本就无广告可跳）。
     *  v5.1.0（安全性增补）：支付/资金页标记——支付密码/付款码/收银台/刷脸支付
     *  文案出现 = 用户正在支付（银行 App 收银台、电商支付页、转账界面），此类
     *  窗口绝不该有任何自动点击（点错=误触支付流程）。与验证码横幅同一防线。 */
    public static boolean isCodeBannerMarker(String s) {
        if (s == null) return false;
        return s.contains("验证码") || s.contains("校验码")
                || s.contains("动态码") || s.contains("动态密码")
                || s.contains("支付密码") || s.contains("付款码")
                || s.contains("收银台") || s.contains("刷脸支付");
    }

    /* ───────── v3.14.0 跳过后反馈弹窗识别 ───────── */

    /** 反馈面板识别结果 */
    public static final class FeedbackFind {
        public boolean isSheet;     // 是「跳过后反馈面板」（强特征 ≥1 且总特征 ≥2）
        public NodeView cancelNode; // 中性收起键（取消/暂不反馈/下次再说…）
        public int[] cancelBounds;  // 收起键边界（手势点击目标）
        public String cancelLabel = ""; // v4.3.0：收起键文案（日志用）
    }

    /** 有界广度优先识别「跳过后反馈面板」：统计特征文案命中数（去重）+
     *  找文案精确等于「取消」的可见控件。底部面板树浅，预算 80 零性能顾虑。
     *  只识别不点击——点不点「取消」由服务侧门控（12s 内自动跳过过）决定。 */
    public static FeedbackFind findFeedbackSheet(NodeView root, int budget) {
        FeedbackFind out = new FeedbackFind();
        if (root == null || budget <= 0) return out;
        java.util.ArrayDeque<NodeView> q = new java.util.ArrayDeque<>();
        q.add(root);
        int seen = 0;
        java.util.HashSet<String> hits = new java.util.HashSet<>();
        boolean strong = false;
        while (!q.isEmpty() && seen < budget) {
            NodeView n = q.poll();
            if (n == null) continue;
            seen++;
            String text = nz(n.text());
            String desc = nz(n.desc());
            String label = text.length() > 0 ? text : desc;
            if (label.length() > 0) {
                for (String m : FEEDBACK_SHEET_STRONG) {
                    if (label.contains(m)) { hits.add(m); strong = true; break; }
                }
                for (String m : FEEDBACK_SHEET_WEAK) {
                    if (label.contains(m)) { hits.add(m); break; }
                }
                if (out.cancelNode == null && n.visible()) {
                    // v4.3.0：中性收起键候选（取消优先，其次暂不反馈/下次再说/
                    //  稍后再说/关闭——只收起，绝不提交任何反馈选项）
                    for (int ci = 0; ci < FEEDBACK_CANCEL_TEXTS.length; ci++) {
                        if (FEEDBACK_CANCEL_TEXTS[ci].equals(text)) {
                            int[] b = n.bounds();
                            if (b != null && b.length >= 4 && b[2] > b[0] && b[3] > b[1]) {
                                out.cancelNode = n;
                                out.cancelBounds = b;
                                out.cancelLabel = FEEDBACK_CANCEL_TEXTS[ci];
                            }
                            break;
                        }
                    }
                }
            }
            for (int i = 0; i < n.childCount(); i++) {
                NodeView c = n.child(i);
                if (c != null) q.add(c);
            }
        }
        out.isSheet = strong && hits.size() >= FEEDBACK_SHEET_MIN_HITS;
        return out;
    }

    /** v3.13.0：有界广度优先找验证码横幅标记（预算内任一节点 text/desc 命中即真）。
     *  预算外节点不展开——横幅窗树极浅，预算 40~60 足够且零性能顾虑。 */
    public static boolean treeHasCodeBannerMarker(NodeView root, int budget) {
        if (root == null || budget <= 0) return false;
        java.util.ArrayDeque<NodeView> q = new java.util.ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen < budget) {
            NodeView n = q.poll();
            if (n == null) continue;
            seen++;
            if (isCodeBannerMarker(nz(n.text())) || isCodeBannerMarker(nz(n.desc()))) return true;
            for (int i = 0; i < n.childCount(); i++) {
                NodeView c = n.child(i);
                if (c != null) q.add(c);
            }
        }
        return false;
    }

    /** v3.13.0：厂商系统工具包（安全中心/系统管家类）——从服务侧私有方法
     *  上移到决策核心（弹窗突发豁免 + 位置学习拒收三处共用，JVM 可单测）。
     *  真机日志（issue#16）实锤：com.miui.securitycenter 的应用详情页被学出
     *  位置(91%,9%)@ApplicationsDetailsActivity——安全中心里的手动操作绝不能
     *  被当「跳过键」学习（下次进详情页 500ms 直点 = 在权限管理页乱点）。 */
    public static boolean isOemUtilityPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return p.contains("miui") || p.contains("securitycenter") || p.contains("systemmanager")
                || p.contains("safecenter") || p.contains("permissionmanager") || p.contains("finddevice");
    }

    public static boolean isGuardPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return matchesAny(p, GUARD_PKG_HINTS);
    }

    /* v3.12.0：isAdTarget / chainBounceVerdict / isChooserHint 已删除
     * （链式拦截整体移除，issue#14：NFC 支付链与主动分享被误弹回）。 */

    /* ═════════ 文案匹配 ═════════ */

    /** v3.8.0：viewId 跳过键判定（GKD 全局规则 -1 同款）：vid 含 skip（大小写不敏感，
     *  穿山甲 tt_splash_skip_btn / 微博 iv_skip 类），或倒计时控件（含 count 与 down 且非 download） */
    static boolean isVidSkip(NodeView node) {
        String vid = node.viewId();
        if (vid == null || vid.length() < 4) return false;
        String v = vid.toLowerCase();
        if (v.contains("skip")) return true;
        return v.contains("count") && v.contains("down") && !v.contains("download");
    }

    /** v3.8.0：解析跳过文案中的倒计时秒数（「跳过 3」「3s后可跳过」「跳过广告 5」→ 3/3/5）。
     *  穿山甲类 SDK 倒计时结束前跳过键不响应点击（点了白点还占冷却）——服务侧据此
     *  门控到「倒计时将尽」再出手，出手即命中。无倒计时返回 0。 */
    public static int parseCountdown(String label) {
        if (label == null) return 0;
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (ch >= '1' && ch <= '9') {
                int v = ch - '0';
                if (i + 1 < label.length()) {
                    char nx = label.charAt(i + 1);
                    if (nx >= '0' && nx <= '9') v = v * 10 + (nx - '0');
                }
                return (v >= 1 && v <= COUNTDOWN_MAX_S) ? v : 0;
            }
            if (ch == '0') return 0; // 「0元领」类营销数字非倒计时
        }
        return 0;
    }

    public static boolean matchesSkip(String s) {
        if (s == null) return false;
        String lower = lowerAscii(s);
        if (lower.contains("skip") || lower.contains("跳过")) return true;
        return matchesAny(s, SKIP_TEXTS);
    }

    /* ═══ 倒计时门控纯函数（v7.6.0 激进模式：首击零门控·重击等将尽·6s 封顶）═══
     * v7.5.0「第 0 击起全次门控」被真机实锤为变慢直接死因（见 gateCountdownAggressive
     * 注释）——门控仅对重击生效（穿山甲真倒计时按钮的命中时机），装饰数字逃生
     * （1.6s 未跳）对任何击数放行的语义保持。 */

    /** 门控到期时刻：锚点 + N 秒 - 将尽提前量（COUNTDOWN_EXTRA_MS）。 */
    public static long countdownUntil(int cdN, long anchorAt) {
        return anchorAt + cdN * 1000L - COUNTDOWN_EXTRA_MS;
    }

    /** 装饰数字判定：锚点 1.6s 未刷新（真倒计时每秒跳一次重置锚点）。 */
    public static boolean decorativeCountdown(long anchorAt, long now) {
        return now - anchorAt > COUNTDOWN_FAKE_MS;
    }

    /** 门控判定（v7.6.0 激进模式：仅重击生效——首击零门控立即出手）：
     *  cdN>0 且未到将尽时刻且非装饰数字。 */
    public static boolean gateCountdown(int cdN, long anchorAt, long now) {
        if (cdN <= 0) return false;
        if (decorativeCountdown(anchorAt, now)) return false;
        return now < countdownUntil(cappedRetryCd(cdN), anchorAt);
    }

    /* ═══ v7.6.0 激进模式（用户第十五轮实锤原话：「你可以激进一点暂时先
     * 放弃宁可漏拦绝不误触」「开屏广告现在有的还跳不了了，或者非常非常慢」）
     * v7.5.0「门控从第 0 击起全次生效」是真机变慢的直接死因——所有带倒
     * 计时的开屏全等倒计时走完才出手（5s 广告=白看 4.9s）；且
     * windowCountdownHint 对非倒计时数字（「领取3天」「第3期」类误检）
     * 的门控让无倒计时按钮也被按住不动 =「跳不了了」。激进模式反转：
     *  · 第 0 击【零门控】——跳过键露头即点（绝大多数按钮可点，0ms 命中）；
     *  · 首击白点后（attempts≥1）重击才等「将尽」（真倒计时按钮的命中时机）；
     *  · 重击等待上限 RETRY_GATE_MAX_MS——误检大数字也不再无限等。═══ */

    /** v7.6.0 激进模式：首击零门控——第 0 击对任何倒计时都立即出手。 */
    public static boolean gateCountdownAggressive(int cdN, long anchorAt, long now, int clickAttempts) {
        if (clickAttempts <= 0) return false; // 首击零门控：露头即点
        return gateCountdown(cdN, anchorAt, now);
    }

    /** v7.6.0 重击等待上限：门控秒数封顶（误检「15」等大数字不再无限等）。 */
    public static int cappedRetryCd(int cdN) {
        if (cdN <= 0) return 0;
        return Math.min(cdN, RETRY_GATE_MAX_S);
    }

    /** v7.5.0 独立倒计时数字解析：「3」「5s」「3秒」「5 S」「12秒」→ 3/5/3/5/12；
     *  非独立数字形态（含其它字符/超 COUNTDOWN_MAX_S/前置 0）一律 0。 */
    public static int standaloneCountdownSeconds(String label) {
        if (label == null) return 0;
        String t = label.trim();
        int n = t.length();
        if (n < 1 || n > CD_STANDALONE_MAX_CHARS) return 0;
        int i = 0, v = 0, digits = 0;
        while (i < n && t.charAt(i) >= '0' && t.charAt(i) <= '9') {
            v = v * 10 + (t.charAt(i) - '0');
            digits++;
            i++;
        }
        if (digits == 0 || digits > 2) return 0;
        if (v < 1 || v > COUNTDOWN_MAX_S) return 0;
        if (i == n) return v; // 纯数字「3」
        String tail = t.substring(i).trim(); // 单位尾（s/S/秒）——其余字符一律拒
        if (tail.equals("s") || tail.equals("S") || tail.equals("秒")) return v;
        return 0;
    }

    /** v7.5.0 倒计时数字区：顶部 35% 或底部 25%（跳过键常驻地）。 */
    static boolean inCountdownZone(int[] b, Ctx ctx) {
        if (b == null || ctx.screenH <= 0) return false;
        int cy = (b[1] + b[3]) / 2;
        return cy <= ctx.screenH * CD_ZONE_TOP_MAX || cy >= ctx.screenH * CD_ZONE_BOTTOM_MIN;
    }

    /** v7.5.0 近旁判定：两控件 |Δcy| ≤ 25% 屏高（同排/同卡铁证）。 */
    static boolean nearY(int[] a, int[] b, Ctx ctx) {
        if (a == null || b == null || ctx.screenH <= 0) return false;
        int cya = (a[1] + a[3]) / 2, cyb = (b[1] + b[3]) / 2;
        return Math.abs(cya - cyb) <= ctx.screenH * CD_NEAR_Y_MAX;
    }

    public static boolean isCloseText(String s) {
        if (s == null) return false;
        String t = s.trim();
        String lower = lowerAscii(t);
        for (String k : CLOSE_TEXTS) {
            if (t.equals(k) || lower.equals(k)) return true;
        }
        return false;
    }

    public static boolean isAdBadge(String label) {
        if (label == null) return false;
        String t = label.trim();
        String lower = lowerAscii(t);
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
