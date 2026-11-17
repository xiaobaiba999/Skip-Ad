package com.zai.adshield;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 开屏广告智能跳过服务（无障碍服务实现 · v3.13.0 · 纯跳过引擎）
 * ─────────────────────────────────────────────────────────────
 * v3.13.0（真机反馈：接收验证码时顶部横幅被点掉 + 夜间待机功耗偏高）：
 *  · 验证码横幅四重防护（不再点掉短信/验证码/来电横幅）：
 *    ① 短信/彩信/来电类覆盖层包（mms/smsextra/messaging/incallui）事件
 *       入口直接丢弃——绝不扫描绝不学习（决策核心 isSmsOverlayPkg）；
 *    ② 树内出现「验证码/校验码/动态码/动态密码」文案的窗口绝不扫描
 *       绝不点击（treeHasCodeBannerMarker，含应用内自绘提示条）；
 *    ③ 手势真实触点派发前检查 SystemUI 顶部小窗（状态栏/heads-up 横幅）
 *       是否覆盖目标坐标——被横幅覆盖即拒发手势（触点会被横幅截走），
 *       降级 performAction 代点（不经过屏幕坐标，绝不碰横幅）；
 *    ④ BACK 兜底同理：SystemUI 横幅在场时绝不派返回键（BACK 会先关横幅）。
 *  · 待机功耗治理（夜间不再每 5 分钟唤醒）：
 *    ① 息屏休眠门——息屏（!PowerManager.isInteractive）时事件只维护
 *       前台包名追踪，不扫描/不点击/不学习（息屏没有可跳的广告，只有
 *       来电/闹钟等敏感窗——此时出手=误点挂断/误关闹钟，纯浪费纯危险）；
 *    ② 手势派发息屏熔断（gestureTap 首行检查，纵深防御）；
 *    ③ 心跳自适应——亮屏 5 分钟精确（发现快，设备本就醒着）；息屏改
 *       30~45 分钟窗口式非精确闹钟（setWindow，系统批量调度进 Doze
 *       维护窗，绝不把设备从 Doze 中唤醒——v3.9.0 的 5 分钟
 *       setExactAndAllowWhileIdle 一晚唤醒 96 次是待机耗电元凶）。
 *  · issue#16 日志实锤两处整治：
 *    ① securitycenter 应用详情页被学出位置 (91%,9%)——OEM 工具包
 *       （miui/securitycenter/systemmanager 族）位置学习永久拒收 +
 *       启动时清理已误学规则（决策核心 isOemUtilityPkg）；
 *    ② 越界位置拒绝日志刷屏（(90%,103%)×6/30s）——同包 30s 去重。
 *  · 误触观察口径修正（issue#16/#17：误触观察 48~60 条几乎全是正常秒跳
 *    被误记——熟练用户等在跳过键上 500ms 内手点完全正常）：「快于人类
 *    反应」启发式移除，仅保留全屏热区观察（真信号），同包 5s 去重。
 *  · 界面：学习中心「分应用统计」卡整体移除（用户反馈看不懂还乱），
 *    引擎日志与能耗面板保留；byApp 统计仍在后台照常累计（仅随日志上传）。
 *
 * v3.12.0（issue#14 日志实锤：链式拦截机制【整体移除】）：
 *  · 事故链（v3.11.0 三重放行安全门仍拦不住）：NFC 碰一碰拉起支付链
 *    （securitycenter→支付组件）与「分享到微信」被 1.5s 内连按 3 次返回键
 *    弹回（日志 ⛓×3）。用户意图场景无法穷举——任何安全门都有漏网时刻。
 *  · 结论：链式回弹是本项目早期自创的危险特性（同类头部应用并无此
 *    功能），三轮事故（#12 闪退/#13 分享被拦/本次 NFC 支付被拦）证明
 *    收益（拦少量真广告二跳）远小于代价（踢用户出正在进行的操作）。
 *    职责回归纯跳过：只点「跳过/关闭」类控件，绝不对抗用户的任何跳转。
 *  · 移除清单：reboundChainJump（跨应用弹回）/ reboundLanding（同包
 *    落地页弹回）/ chainBounceVerdict 三重放行 / isChooserHint /
 *    isAdTarget + AD_TARGET_HINTS 名单 / adFeatureUntil 广告特征追踪 /
 *    用户意图追踪（lastUserActAt/chooserSeenAt）状态全部删除。
 *  · intentresolver 保留在系统噪音过滤（分享面板绝不扫描，防误点）。
 *  · issue#14 日志同时暴露 connectivity 连接检查窗循环误点——并入噪音
 *    过滤（connectivity/captiveportal 家族）。
 *
 * v3.11.0（issue#13 实锤修复：用户主动分享被链式回弹连踢 3 次）：
 *  · 事故链：用户在安全中心点「分享到微信」→ 引擎把 securitycenter→mm
 *    当成「广告拉起微信」→ 返回键弹回 → 用户重试 3 次全被弹回。
 *    参考同类头部应用的安全哲学：回弹绝不与用户意愿对抗——只拦
 *    「无人在场时广告自己拉起」的跳转。三重放行安全门：
 *    ① 用户 2.2s 内真实点击（引擎代点不计）→ 主动跳转，放行；
 *    ② 系统分享选择器（Resolver/Chooser/intentresolver）8s 内出现过
 *       → 用户亲手挑的目标，放行；
 *    ③ 同「宿主→目标」12s 内已弹回过一次又立刻拉起 = 用户重试，放行。
 *  · 同包落地页弹回同步加用户点击门（点商品/文章详情跳 WebView 不再误弹）；
 *  · intentresolver 加入系统噪音过滤（分享面板全屏热区误观察归零）；
 *  · 学习规则明细撤出 App UI（学习引擎照常工作，列表仅进诊断日志）。
 *
 * v3.10.0（规则直接内嵌、覆盖面大扩充、绝不乱点）：
 *  · RuleEngine 五层规则：分应用表（28 款主流应用真实文案）/ 全局精确
 *    文案（19 条，大小写归一）/ 倒计时形态（严格正则，营销话术数学上
 *    无法混入）/ 短包含（≤8 字 + 诱导词 17 词黑名单否决）/ vid（skip 族
 *    任意窗口 + close/exit/dismiss 族仅 SDK 广告窗/学习窗+屏幕边缘带）；
 *  · Ctx 新增 pkg；服务启动日志带规则库统计。
 *
 * v3.9.0（issue#11/#12 真机日志实锤三条毒链整改，内嵌规则引擎主路径化）：
 *  · 毒链①（issue#12 闪退元凶）根治：桌面（com.miui.home）被误判为开屏
 *    广告窗 → 坐标兜底盲点固定坐标点中桌面图标 → 拉起微信/QQ/抖音 →
 *    链式回弹把用户踢出应用「进去就被退出来」。对策三刀：
 *    ① 桌面/Launcher 事件入口直接丢弃（isLauncherLike 黑名单——绝不
 *       扫描/学习/回弹，桌面图标位置永不再被学成「跳过键」）；
 *    ② 坐标级盲点兜底（L6）整体移除——每台手机屏幕大小不一样，固定
 *       百分比坐标必然乱点；只点【规则命中的控件自身 bounds 中心】
 *       （应用按实际屏幕渲染，天然适配任意分辨率）；
 *    ③ 链式回弹加宿主校验：host 必须非桌面且真有广告窗资格。
 *  · 主路径落地（RuleEngine 内嵌规则引擎，规则内嵌 APK 零云端）：
 *    窗口/内容一变 → 按包名查全局规则（「跳过/跳过广告/Skip」精确+短包含
 *    +vid skip）→ 命中即 0ms 手势点控件中心——「露头就秒跳」（Keep 开屏
 *    同级速度）。决策核心退居复杂信号兜底（关闭键/角落键/SDK 窗/陀螺仪）。
 *  · 角落键广告形态确认门：无字小控件须「热区/角标/SDK 窗/学习窗/树≤60」
 *    五选一才出手——普通应用主页角落图标（设置齿轮）绝不误点。
 *  · 弹窗内容突发检测默认关闭（issue#12 大量误报「疑似覆盖层弹窗」），
 *    学习中心可手动开启。
 *  · 速度全面提速（以 Keep 开屏为标杆）：扫描链前段加密（0/60/120/200/
 *    300/450/650/900ms）、学习位置 100ms、关闭键 250ms、角落键 300ms、
 *    点击冷却 1.2s→0.9s、内容变化节流 150→80ms。
 *  · 保活强化：心跳自检闹钟 15 分钟→5 分钟（服务被杀发现更快，配合
 *    WRITE_SECURE_SETTINGS 自愈重绑全自动恢复）。
 *  · 保留：GitHub 引擎日志上传（issue 反馈通道）、学习中心（文本/位置/
 *    指纹学习——用户使用习惯全保留）。
 *
 * v3.8.0（issue#10 二轮根因修复：canPerformGestures 缺失/手势零长度路径/
 *  多候选轮换/假跳过直点/vid 跳过键/倒计时门控/WRITE_SECURE_SETTINGS 自愈）
 * v3.7.0（issue#10 一轮根因修复：onUnbind 保留+心跳+学习位置前置+返回键复验）
 * v3.6.0（根窗口安全门+毒指纹清理；v3.5.0 复验梯子+位置学习+保活三件套）
 * ─────────────────────────────────────────────────────────────
 * v3.7.0（四次真机反馈 issue#10：喜马拉雅「已点击跳过×4」广告不关，用户手点
 * 同一按钮立刻生效；微博陀螺仪窗返回键直断无效果；清理后台服务失效）：
 *  · 根因①（头号）：无障碍配置缺 canPerformGestures=true——dispatchGesture
 *    「手势真实触点」从未真正触达屏幕，复验重试全部无效。已补配置。
 *  · 根因②：手势路径单点零长度（只有 moveTo）——部分 ROM 不触发点击。
 *    已改 lineTo 非零路径 + 40ms 起始延迟 + 边界钳制 + 取消诊断回调。
 *  · 根因③：文本跳过键只点第一个候选，复验 4 次盲点同一坐标——广告图内
 *    常嵌假「跳过」文本。已改多候选收集轮换（决策核心 skipCands）。
 *  · 根因④：学习位置排在文本候选之后，教过也轮不到。已前置到 L1 之前；
 *    且文本键手动点击【同时学位置】（用户手指点通过的位置最可靠）；
 *    位置健全性校验拒学越界坐标（真机日志 (63%,102%) 越界白学）。
 *  · 根因⑤（微博）：陀螺仪文案 120ms 抢先返回键、角落键 500ms 才轮到；
 *    返回键被广告 Activity 拦截后扫描链熔断再无兑底。已改「点击优先、
 *    返回键兑底」+ 返回键后 600ms 复验不熔断；角落/关闭键树上限 40→150。
 *  · 根因⑥（保活）：服务解绑即销毁、无心跳。已改 onUnbind 保留 + 15 分钟
 *    心跳自检闹钟 + 打开 App 自动拉活循环重试（此前仅一次）。
 *  · 兼容：flagIncludeNotImportantViews（部分广告跳过键被标 not-important，
 *    不加此标志无障碍树里根本看不到——微博角落键扫不到的帮凶）。
 *
 * v3.5.0（二次真机反馈驱动）：点击复验梯子 + 位置学习 + 保活三件套。
 * v3.6.0（三次真机反馈：喜马拉雅二次进入被引擎连按两次返回键「模拟人
 * 操作退出」到桌面，上传日志实锤两条毒链）：
 *  · 毒链① 窗口指纹污染：v3.5.0 的 learnClass 在 L2 直断时把【当时窗口】
 *    类名一律沉淀；且广告窗关闭后 SDK 资格 12s 不清，L2 在主页继续出手
 *    → MainActivity/FrameLayout/Dialog 全被学成「广告窗指纹」，此后每次
 *    进入该应用 500ms 内就在主页按返回键 → 喜马拉雅拦返回弹「再按一次
 *    退出」→ 复验扫描再按 → 真退到桌面（用户被「模拟人操作退出」）。
 *    对策：sdkAdWin 只认【当前窗口类名】是 SDK 广告窗（12s TTL 废除）；
 *    learnClass 只沉淀真广告窗类；启动时清理历史毒指纹（只清 cls 规则）。
 *  · 毒链② 根窗口返回键：应用入口窗（冷启动开屏/回前台首窗）按返回
 *    = 退出应用。rootWindow 接线（前台入口窗追踪）：入口窗上 L3/L4.5/L5
 *    返回键全禁（决策核心安全门 + 服务侧双保险），只走点击梯子，用尽
 *    优雅收手（每窗 6 次点击预算）。
 *  · 手势点击改点【原标签控件】坐标（v3.5.0 点可点祖先中心——祖先常是
 *    全屏容器，「已点击跳过但广告纹丝不动」根因之一）。
 *  · 保活新增：onTaskRemoved 即时提醒 + 自检闹钟 + 打开 App 自动拉活重绑。
 *    一个角落候选点并强制 dispatchGesture 真实触点重试；同窗点击 ≥3 次
 *    仍未关 → 返回键兜底（「点了没反应」的最终兜底）。
 *  · 位置学习：手动点击图片跳过键（零文本）也能学——归一化坐标 + 窗口
 *    类名指纹，次启同窗 500ms 直点（教一次就会）。
 *  · 保活：前台常驻通知 + keepOn 持久化 + KeepAliveReceiver 断连提醒——
 *    开启后除非手动关闭不再静默失效（更新/重启后一键恢复）。
 *  · 架构：识别/决策逻辑全部在 AdDecisionCore（纯 Java，JVM 用例必绿）；
 *    本类只负责：
 *  · 事件调度（0ms 首扫 + 前密后疏重试链；点击后转入复验调度，不熔断）
 *  · 决策执行：performAction(ACTION_CLICK) 失效自动降级 dispatchGesture 手势点击
 *    （v3.4.0 关键：许多应用拦截 performAction，但拦不住真实触点；
 *    v3.5.0 复验重试一律直接手势真实触点）
 *  · 多窗口扫描：getRootInActiveWindow 之外遍历 getWindows()（弹窗/覆盖层）
 *  · 坐标级点击兜底执行（L6，决策核心判定后执行）
 *  · 学习 / 统计 / 能耗画像
 *
 * 真机反馈修复（2026-11：微博/喜马拉雅等主流应用仍拦截失败）：
 *  根因：第一方图片开屏——窗口类名是应用自己的 Activity（不匹配 SDK 指纹）、
 *  跳过按钮是纯图片（零文本零 desc）、倒计时烧在图里 → v3.3.0 零信号不出手。
 *  对策：L4 角落无字小可点控件点击（图片跳过键正解）+ L6 坐标级点击兜底
 *  （dispatchGesture 真实触点，点击标准跳过位 88%宽/7%高，防误点六重约束）。
 *
 * 合规边界（沿用）：只代点「跳过/关闭」类控件、返回键温和关闭广告窗，
 * 不碰网络层、不终止任何进程与服务（「拦精灵」案司法边界）。
 */
public class AdSkipService extends AccessibilityService {

    public static volatile AdSkipService instance;

    private static final String PREFS = "adshield";
    private static final String KEY_STATE = "state";
    private static final String KEY_KEEPON = "keepOn";

    private static final int NOTIF_KEEP = 1;   // 前台保活常驻通知
    public static final int NOTIF_ALERT = 2;   // 服务断开提醒（KeepAliveReceiver 发，服务连上即撤）

    private static final long WINDOW_MS = 12000;       // 开屏时间窗
    private static final long COOLDOWN_MS = 900;       // 同一应用两次自动点击最小间隔（v3.9.0 1200→900：复验更快）
    private static final long LEARN_WINDOW_MS = 30000; // 手动点击学习宽时间窗
    private static final int MAX_RULES_PER_APP = 8;
    private static final int MAX_LOG = 60;

    /* 极速链（v3.9.0 前段加密：露头就秒跳——Keep 开屏「跳过」键 0ms 首扫即中，
       60ms 内二连扫兜底渲染延迟；后段保持疏节奏覆盖晚出键） */
    private static final long[] SCAN_AT =
            {0, 60, 120, 200, 300, 450, 650, 900, 1300, 1900, 2700, 3800, 5200, 7000, 9500, 12000};
    /* 返回键重试：每个窗口最多直断次数（个别广告拦截第一次返回键） */
    private static final int BACK_RETRY_MAX = 2;
    /* 弹窗窗期与冷却 */
    private static final long POPUP_WINDOW_MS = 10000;
    private static final long POPUP_COOLDOWN_MS = 2500;
    private static final long POPUP_SCAN_THROTTLE_MS = 700;
    private static final int BURST_COUNT = 3;
    private static final long BURST_WIN_MS = 1500;
    /* 多窗口扫描上限（能耗） */
    private static final int MAX_WINDOWS_SCAN = 3;

    /* 能耗治理：内存聚合 + 延迟落盘 */
    private static final long FLUSH_DELAY_MS = 15000;
    private boolean flushScheduled = false;

    /* 能耗画像（开机累计，学习中心展示） */
    public long energyEvents = 0;
    public long energyScans = 0;
    public long energyBacks = 0;
    public long energyGestures = 0;
    public long popupBlocks = 0;

    private final Object lock = new Object();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.HashMap<String, Long> windowAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastClickAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastPopupAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Integer> scanGen = new java.util.HashMap<>();
    private final java.util.HashMap<String, String> winClass = new java.util.HashMap<>();
    private final java.util.HashMap<String, Boolean> winClsLearned = new java.util.HashMap<>();
    /* v3.6.0 根窗口安全门：应用入口窗（冷启动开屏/回前台首窗）类名——
     * 入口窗上按返回键 = 退出应用，返回键决策全部禁用 */
    private final java.util.HashMap<String, String> rootWinCls = new java.util.HashMap<>();
    /* v3.6.0：前台包名追踪（判断应用是否【新进入前台】→ 记录入口窗） */
    private volatile String lastFgPkg = null;
    /* v3.6.0：本窗点击梯子已优雅收手（预算用尽后不再动作，新窗口重置） */
    private final java.util.HashMap<String, Boolean> gaveUp = new java.util.HashMap<>();
    private final java.util.HashMap<String, Integer> backTries = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> popupUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastPopupScanAt = new java.util.HashMap<>();
    /* v3.13.0 日志去重：位置学习拒绝/验证码横幅/heads-up 保护/热区误触 */
    private final java.util.HashMap<String, Long> posRejectLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> codeBannerLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> headsUpLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> misclickLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, long[]> burstWin = new java.util.HashMap<>();
    /* v3.5.0：点击复验——本窗已点击次数（换候选/真实触点重试/返回键兜底的依据） */
    private final java.util.HashMap<String, Integer> clickTries = new java.util.HashMap<>();
    /* v3.8.0 倒计时门控（issue#10 二轮·喜马拉雅）：跳过文案带数字（「跳过 3」）
     * 时穿山甲类 SDK 的跳过键尚未激活——提前点=白点还占 1.2s 冷却。锚点跟踪
     * 文案数字变化，首击未关 → 门控到「锚点+剩余秒数-0.5s」再出手，出手即命中 */
    private final java.util.HashMap<String, Integer> cdLastN = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> cdAnchorAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> cdWaitUntil = new java.util.HashMap<>();

    private volatile String lastAutoClickPkg;
    private volatile long lastAutoClickAt;

    private int screenWidth;
    private int screenHeight;

    /** 状态快照（含缓存）：Bridge 读 state 走这里，UI 永远拿最新内存态 */
    public static String snapshotState(Context ctx) {
        AdSkipService s = instance;
        if (s != null) {
            synchronized (s.lock) {
                try {
                    return s.stateInMemory.toString();
                } catch (Exception ignored) {
                }
            }
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        // v3.5.0 保活：服务连上即记「用户要求保持开启」；仅用户在 App 内手动
        // 关闭保活提醒（Bridge.setKeepOn(false)）才置 false
        prefs().edit().putBoolean(KEY_KEEPON, true).apply();
        startKeepAliveForeground();
        cancelAlertNotification();
        cancelHeartbeat(); // 服务活着，心跳自检不再需要
        // v3.6.0：清理 v3.5.0 误学毒指纹（MainActivity/FrameLayout/Dialog 被
        // 学成「广告窗指纹」——用户二次进入被连按返回键退出的根因）
        int purged = purgePoisonFingerprints();
        appendLog("服务已启动 · v3.13.0 验证码横幅四重防护 + 息屏休眠 + 夜间心跳降频（规则 "
                + RuleEngine.stats() + ")"
                + (purged > 0 ? "｜已清理 " + purged + " 条误学窗口指纹" : ""));
        // v3.9.0：清理毒位置规则（issue#12 实锤：桌面 com.miui.home 学出位置
        //(39%,18%)——桌面点击被误学成「跳过键」，下次启动直点桌面图标=乱点）
        int purgedPos = purgeLauncherPosRules();
        if (purgedPos > 0) appendLog("已清理 " + purgedPos + " 条桌面误学位置规则（issue#12 闪退整治）");
        // v3.13.0：清理 OEM 工具包/短信包误学规则（issue#16 实锤：securitycenter
        // 应用详情页被学出位置 (91%,9%)——下次进详情页 500ms 直点=在权限管理页乱点）
        int purgedOem = purgeOemPosRules();
        if (purgedOem > 0) appendLog("已清理 " + purgedOem + " 条系统工具/短信包误学规则（issue#16 整治，安全中心/短信里的手动操作绝不当「跳过键」）");
    }

    /* ───────── v3.7.0 保活：onUnbind 保留 + 心跳自检闹钟 ─────────
     * 「开启后不手动关闭就一直保持开启」（issue#10：清理后台重进又要手动开）：
     *  · onUnbind 返回 true：系统解绑（非用户主动关闭）时服务实例保留，
     *    AMS 重绑无需重建——GKD 同款方案；
     *  · 心跳闹钟（15 分钟 inexact）：进程被杀但非 force-stop 的 ROM 上仍会触发，
     *    服务已死即发恢复提醒；服务连上即自动撤；
     *  · force-stop 后闹钟会被系统清除（任何应用无法绕过）——唯一出路是
     *    电池白名单（App 内醒目引导）+ 打开 App 自动拉活重绑（tryAutoRevive）。 */
    @Override
    public boolean onUnbind(Intent intent) {
        // 系统解绑但非用户主动关闭：保留服务等待 AMS 重绑，并排自检 + 心跳
        try {
            if (prefs().getBoolean(KEY_KEEPON, false)) {
                appendLog("🔌 服务被系统解绑（非手动关闭）→ 保留实例等待重绑，已排自检与心跳");
                scheduleSelfCheckAlarm(3000);
                scheduleHeartbeat();
            }
        } catch (Throwable ignored) {
        }
        return true; // 关键：true = 不销毁服务，下次 AMS 重绑直接复用
    }

    /** v3.9.0：心跳自检闹钟（亮屏 5 分钟：服务被杀后最多 5 分钟内发现并自愈/
     *  提醒恢复——「清理后台重进要手动开」的发现延迟大幅缩短） */
    public static final long HEARTBEAT_MS = 5 * 60 * 1000;

    /** v3.13.0：息屏心跳间隔——30 分钟窗口式非精确闹钟（30~45 分钟内由系统
     *  批量调度进 Doze 维护窗，绝不把设备从 Doze 中唤醒；v3.9.0 的 5 分钟
     *  setExactAndAllowWhileIdle 一晚唤醒 96 次是待机耗电元凶） */
    public static final long NIGHT_HEARTBEAT_MS = 30 * 60 * 1000;
    public static final long NIGHT_HEARTBEAT_WINDOW_MS = 15 * 60 * 1000;

    /** v3.8.0：排下一次心跳（静态：KeepAliveReceiver 收到自检后自续期共用——
     *  心跳链永不中断）。亮屏：精确闹钟优先（Doze 中也能准点触发），未授予
     *  精确闹钟权限（Android 12+）降级 setAndAllowWhileIdle；息屏且允许降频
     *  （nightDeferrable，开机 2s 快查除外）：改窗口式非精确闹钟——系统批量
     *  调度，不唤醒设备（v3.13.0 待机功耗治理） */
    static void scheduleNextHeartbeat(Context ctx, long atMs) {
        scheduleNextHeartbeat(ctx, atMs, false);
    }

    static void scheduleNextHeartbeat(Context ctx, long atMs, boolean nightDeferrable) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent it = new Intent(ctx, KeepAliveReceiver.class);
            it.setAction(KeepAliveReceiver.ACTION_SELF_CHECK);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    ctx, 8, it,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            if (nightDeferrable && !isScreenOn(ctx)) {
                am.setWindow(android.app.AlarmManager.RTC,
                        System.currentTimeMillis() + NIGHT_HEARTBEAT_MS, NIGHT_HEARTBEAT_WINDOW_MS, pi);
            } else {
                try {
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC, atMs, pi);
                } catch (SecurityException se) {
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC, atMs, pi);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void scheduleHeartbeat() {
        scheduleNextHeartbeat(this, System.currentTimeMillis() + HEARTBEAT_MS, true);
    }

    private void cancelHeartbeat() {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            Intent it = new Intent(this, KeepAliveReceiver.class);
            it.setAction(KeepAliveReceiver.ACTION_SELF_CHECK);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    this, 8, it,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // v3.6.0：后台清理（滑掉最近任务）时，部分 ROM 会连无障碍服务一起杀。
        // ① 若进程幸存：4s 后自检，服务真死了才发「待恢复」提醒（防误报）
        // ② 自检闹钟（15s）：进程被杀但非 force-stop 的 ROM 上仍可触发提醒
        // v3.7.0：追加 15 分钟心跳闹钟——进程被杀后持续自检，
        //    服务一旦真死即提醒恢复（用户手动关闭 keepOn 时不排）
        try {
            if (!prefs().getBoolean(KEY_KEEPON, false)) return;
            handler.postDelayed(() -> {
                if (instance == null) KeepAliveReceiver.notifyAlert(AdSkipService.this);
            }, 4000);
            scheduleSelfCheckAlarm(15000);
            scheduleHeartbeat();
        } catch (Throwable ignored) {
        }
    }

    /** v3.6.0：自检闹钟——清理后台后若服务已死（且非 force-stop 状态），发恢复提醒 */
    private void scheduleSelfCheckAlarm(long delayMs) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            Intent it = new Intent(this, KeepAliveReceiver.class);
            it.setAction(KeepAliveReceiver.ACTION_SELF_CHECK);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    this, 7, it,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + delayMs, pi);
        } catch (Throwable ignored) {
        }
    }

    /** v3.6.0：清理误学窗口指纹——只删 cls 规则中非广告窗类的（保留位置/文本规则） */
    private int purgePoisonFingerprints() {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) return 0;
                int purged = 0;
                java.util.Iterator<String> it = rules.keys();
                while (it.hasNext()) {
                    String pkg = it.next();
                    JSONArray list = rules.optJSONArray(pkg);
                    if (list == null) continue;
                    JSONArray kept = new JSONArray();
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject r = list.optJSONObject(i);
                        if (r == null) continue;
                        String cls = r.optString("cls", "");
                        boolean poison = cls.length() > 0
                                && !r.optBoolean("pos", false)
                                && !AdDecisionCore.isAdActivityClass(cls);
                        if (poison) {
                            purged++;
                        } else {
                            kept.put(r);
                        }
                    }
                    if (kept.length() == 0) it.remove();
                    else rules.put(pkg, kept);
                }
                if (purged > 0) saveState(st);
                return purged;
            } catch (Exception e) {
                return 0;
            }
        }
    }

    /** v3.13.0：清理 OEM 系统工具包/短信包误学的规则（issue#16 实锤毒数据：
     *  com.miui.securitycenter 应用详情页被学出位置 (91%,9%)@ApplicationsDetails
     *  Activity——安全中心里的手动操作被当「跳过键」记忆，下次进详情页 500ms
     *  直点=在权限管理页乱点）。OEM 工具包与短信类包规则【整体删除】。 */
    private int purgeOemPosRules() {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) return 0;
                int purged = 0;
                java.util.Iterator<String> it = rules.keys();
                while (it.hasNext()) {
                    String pkg = it.next();
                    if (AdDecisionCore.isOemUtilityPkg(pkg) || AdDecisionCore.isSmsOverlayPkg(pkg)) {
                        JSONArray list = rules.optJSONArray(pkg);
                        if (list != null) purged += list.length();
                        it.remove();
                    }
                }
                if (purged > 0) saveState(st);
                return purged;
            } catch (Exception e) {
                return 0;
            }
        }
    }

    /** v3.9.0：清理桌面/系统误学的位置规则（issue#12 实锤毒数据：
     *  com.miui.home 学出位置(39%,18%)@Launcher、com.autonavi.minimap 学出
     *  (86%,8%)——桌面图标位置被当「跳过键」记忆，下次启动直点图标=乱点）。
     *  桌面包规则整体删除；位置规则的窗口类含 Launcher/home 的一并删除。 */
    private int purgeLauncherPosRules() {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) return 0;
                int purged = 0;
                java.util.Iterator<String> it = rules.keys();
                while (it.hasNext()) {
                    String pkg = it.next();
                    // 桌面包：规则整体删除（绝不扫描绝不学习——黑名单双保险）
                    if (AdDecisionCore.isLauncherLike(pkg)) {
                        JSONArray list = rules.optJSONArray(pkg);
                        if (list != null) purged += list.length();
                        it.remove();
                        continue;
                    }
                    // 位置规则的窗口类含 Launcher/home：删除该条
                    JSONArray list = rules.optJSONArray(pkg);
                    if (list == null) continue;
                    JSONArray kept = new JSONArray();
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject r = list.optJSONObject(i);
                        if (r == null) continue;
                        String cls = r.optString("cls", "").toLowerCase();
                        if (r.optBoolean("pos", false)
                                && (cls.contains("launcher") || cls.contains("home"))) {
                            purged++;
                        } else {
                            kept.put(r);
                        }
                    }
                    if (kept.length() == 0) it.remove();
                    else rules.put(pkg, kept);
                }
                if (purged > 0) saveState(st);
                return purged;
            } catch (Exception e) {
                return 0;
            }
        }
    }

    @Override
    public void onDestroy() {
        flushNow();
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        // v3.9.0 桌面/Launcher 黑名单（issue#12 闪退元凶根治）：桌面包事件
        // 直接丢弃——绝不扫描（桌面简单树被误判广告窗）、绝不学习（桌面图标
        // 位置被学成「跳过键」后下次启动直点图标=乱点）、绝不回弹（「桌面→
        // 微信」被当广告跳转踢出用户）。仅更新前台包名追踪。
        if (AdDecisionCore.isLauncherLike(pkg)) {
            lastFgPkg = pkg;
            return;
        }
        if (pkg.equals(getPackageName())) return;

        long now = System.currentTimeMillis();

        if (isSystemNoise(pkg)) return;

        // v3.13.0 息屏休眠门（夜间功耗治理 + 息屏绝不出手）：息屏 = 用户看不见
        // 任何广告（没有可跳对象），此时唯一可能出现在前台的是来电/闹钟等敏感窗
        // ——扫描/点击/学习全是浪费甚至危险（误点来电挂断/闹钟关闭）。仅廉价维护
        // 前台包名追踪（亮屏后进入应用的入口窗判定仍正确），其余事件全部丢弃。
        if (!isScreenOn(this)) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                lastFgPkg = pkg;
            }
            return;
        }

        // v3.13.0 验证码横幅防护①：短信/彩信/来电类覆盖层包（mms/smsextra/
        // messaging/incallui）事件入口直接丢弃——绝不扫描（横幅上的小 × 绝不被
        // 当「角落跳过键」点击）、绝不学习（手动点「复制验证码」绝不被学成跳过键）
        if (AdDecisionCore.isSmsOverlayPkg(pkg)) return;

        energyEvents++;

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                String cls = cs(event.getClassName());
                // v3.6.0 根窗口追踪：新进入前台的应用，其首个窗口 = 入口窗
                //（冷启动开屏 / 从桌面·其他应用回前台的首窗）——入口窗上
                // 按返回键 = 退出应用，安全门据此禁一切返回键兜底
                boolean entering = !pkg.equals(lastFgPkg);
                lastFgPkg = pkg;
                // v3.5.0 复验通过：上一窗点过东西且窗口已切走 → 广告已关
                Integer prevTries = clickTries.get(pkg);
                if (prevTries != null && prevTries > 0 && !cls.equals(winClassOf(pkg))) {
                    appendLog("✓ 复验通过（" + shortPkg(pkg) + "）：广告窗已关闭"
                            + (prevTries > 1 ? "（共 " + prevTries + " 次尝试，重试梯子兜底）" : ""));
                    // v3.6.0 毒链① 根治：广告已关，学习窗快断资格同步作废
                    //（防止后续窗口还拿着旧资格按返回键）
                    winClsLearned.remove(pkg);
                }
                windowAt.put(pkg, now);
                winClass.put(pkg, cls);
                if (entering) rootWinCls.put(pkg, cls); // v3.6.0：记录入口窗
                backTries.put(pkg, 0);
                clickTries.put(pkg, 0); // v3.5.0：新窗重置复验计数
                cdLastN.remove(pkg);   // v3.8.0：新窗重置倒计时锚点
                cdAnchorAt.remove(pkg);
                cdWaitUntil.remove(pkg);
                gaveUp.put(pkg, Boolean.FALSE); // v3.6.0：新窗重置收手标记
                winClsLearned.remove(pkg);
                if (isDialogClass(cls) && !isOemUtilityPkg(pkg)) {
                    popupUntil.put(pkg, now + POPUP_WINDOW_MS);
                }
                if (AdDecisionCore.isAdActivityClass(cls)) {
                    appendLog("🎯 SDK 广告窗识别（" + shortPkg(pkg) + "）：" + cls
                            + " → 700ms 内无真跳过键将温和直断");
                } else if (learnedClassHit(pkg, cls)) {
                    winClsLearned.put(pkg, Boolean.TRUE);
                }
                bumpScanChain(pkg);
                break;
            }
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                if (now - winAt(pkg) < WINDOW_MS) {
                    nudgeScan(pkg, now);
                    break;
                }
                if (now < popupUntilGet(pkg)) {
                    nudgePopupScan(pkg, now);
                    break;
                }
                detectPopupBurst(pkg, now);
                break;
            }
            case AccessibilityEvent.TYPE_VIEW_CLICKED: {
                if (isOwnAutoClick(pkg, now)) return;
                learnFromManualClick(pkg, event, now);
                observeMisclick(pkg, event, now);
                break;
            }
        }
    }

    /* ───────── 基础判断 ───────── */

    private boolean isSystemNoise(String pkg) {
        return pkg.startsWith("com.android.systemui")
                || pkg.equals("android")
                || pkg.contains("inputmethod")
                || pkg.contains("packageinstaller")
                || pkg.contains("intentresolver") // v3.11.0：分享选择器（分享面板绝不扫描，防误点）
                // v3.12.0（issue#14 日志实锤）：connectivity 连接检查窗被循环
                // 误点（2 分钟内「已点击跳过」×10）——系统连接诊断页绝非广告宿主
                || pkg.contains("connectivity") || pkg.contains("captiveportal")
                || pkg.equals("com.android.settings");
    }

    /** v3.6.0：厂商系统工具包（安全中心/系统管家类）——弹窗突发检测不适用
     *（真机日志：securitycenter/keep 的系统弹窗被误开弹窗识别窗）。
     *  v3.13.0：上移到决策核心 isOemUtilityPkg（JVM 可单测），本类三处共用 */
    private boolean isOemUtilityPkg(String pkg) {
        return AdDecisionCore.isOemUtilityPkg(pkg);
    }

    /** v3.13.0：息屏判定（查询失败按亮屏放行——绝不错杀正常跳过） */
    static boolean isScreenOn(Context ctx) {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean isDialogClass(String cls) {
        if (cls == null) return false;
        String c = cls.toLowerCase();
        return c.contains("dialog") || c.contains("popup");
    }

    private long winAt(String pkg) {
        Long t = windowAt.get(pkg);
        return t == null ? 0L : t;
    }

    private long popupUntilGet(String pkg) {
        Long t = popupUntil.get(pkg);
        return t == null ? 0L : t;
    }

    private boolean isOwnAutoClick(String pkg, long now) {
        return pkg.equals(lastAutoClickPkg) && (now - lastAutoClickAt) < 900;
    }

    private String winClassOf(String pkg) {
        String c = winClass.get(pkg);
        return c == null ? "" : c;
    }

    /** v3.6.0：应用入口窗类名（无记录返回空串 = 非入口窗） */
    private String rootWinClsOf(String pkg) {
        String c = rootWinCls.get(pkg);
        return c == null ? "" : c;
    }

    private int triesOf(String pkg) {
        Integer n = backTries.get(pkg);
        return n == null ? 0 : n.intValue();
    }

    /* ───────── AccessibilityNodeInfo → 决策核心适配 ───────── */

    private static final class NodeAdapter implements NodeView {
        final AccessibilityNodeInfo n;

        NodeAdapter(AccessibilityNodeInfo n) {
            this.n = n;
        }

        public String text() {
            return n.getText() == null ? "" : n.getText().toString().trim();
        }

        public String desc() {
            return n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim();
        }

        public String cls() {
            return n.getClassName() == null ? "" : n.getClassName().toString();
        }

        public boolean clickable() {
            return n.isClickable();
        }

        public boolean scrollable() {
            return n.isScrollable();
        }

        public boolean editable() {
            return n.isEditable();
        }

        public boolean visible() {
            try {
                return n.isVisibleToUser();
            } catch (Exception e) {
                return true; // 旧 ROM 异常时放行
            }
        }

        public String viewId() {
            try {
                CharSequence v = n.getViewIdResourceName();
                return v == null ? "" : v.toString();
            } catch (Exception e) {
                return "";
            }
        }

        public int[] bounds() {
            try {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                return new int[]{r.left, r.top, r.right, r.bottom};
            } catch (Exception e) {
                return null;
            }
        }

        public int childCount() {
            return n.getChildCount();
        }

        public NodeView child(int i) {
            AccessibilityNodeInfo c = n.getChild(i);
            return c == null ? null : new NodeAdapter(c);
        }

        public NodeView parent() {
            AccessibilityNodeInfo p = n.getParent();
            return p == null ? null : new NodeAdapter(p);
        }
    }

    /* ───────── 扫描调度：0ms 首扫 + 前密后疏重试链 ───────── */

    private void bumpScanChain(final String pkg) {
        final long winStart = winAt(pkg);
        Integer g = scanGen.get(pkg);
        final int gen = g == null ? 1 : g + 1;
        scanGen.put(pkg, gen);
        for (int i = 0; i < SCAN_AT.length; i++) {
            final long at = SCAN_AT[i];
            handler.postDelayed(() -> {
                Integer cur = scanGen.get(pkg);
                if (cur == null || cur.intValue() != gen) return;
                tryScan(pkg, winStart, at);
            }, at);
        }
    }

    private void nudgeScan(final String pkg, long now) {
        Integer gen = scanGen.get(pkg);
        if (gen == null) {
            bumpScanChain(pkg);
            return;
        }
        final long winStart = winAt(pkg);
        // v3.9.0：节流 150→80ms（前 4s 内）——开屏「跳过」键晚渲染时更快补扫，
        // 露头就秒跳；4s 后放宽 300ms 控能耗
        if (now - winStart < (now - winStart < 4000 ? 80 : 300)) return;
        final int g = gen.intValue();
        handler.post(() -> {
            Integer cur = scanGen.get(pkg);
            if (cur == null || cur.intValue() != g) return;
            tryScan(pkg, winStart, -1);
        });
    }

    private void nudgePopupScan(final String pkg, long now) {
        Long last = lastPopupScanAt.get(pkg);
        if (last != null && now - last < POPUP_SCAN_THROTTLE_MS) return;
        lastPopupScanAt.put(pkg, now);
        final long winStart = winAt(pkg);
        handler.post(() -> {
            if (instance == null) return;
            tryScan(pkg, winStart, -2);
        });
    }

    private void detectPopupBurst(String pkg, long now) {
        if (!popupShieldEnabled() || isOemUtilityPkg(pkg)) return;
        long[] w = burstWin.get(pkg);
        if (w == null || now - w[0] > BURST_WIN_MS) {
            burstWin.put(pkg, new long[]{now, 1});
            return;
        }
        w[1]++;
        if (w[1] >= BURST_COUNT) {
            burstWin.remove(pkg);
            popupUntil.put(pkg, now + POPUP_WINDOW_MS);
            appendLog("🪟 检测到内容突发（" + shortPkg(pkg) + "）：疑似覆盖层弹窗 → 开启弹窗识别窗");
            nudgePopupScan(pkg, now);
        }
    }

    private void fuseChain(String pkg) {
        Integer g = scanGen.get(pkg);
        scanGen.put(pkg, g == null ? 1 : g + 1);
    }

    /* ───────── 扫描与决策执行（逻辑全部在 AdDecisionCore） ───────── */

    private void tryScan(String pkg, long winStart, long atMs) {
        if (instance == null) return;
        long now = System.currentTimeMillis();
        long elapsed = now - winStart;
        boolean splashLive = learningEnabled() && elapsed <= WINDOW_MS;
        boolean popupLive = popupShieldEnabled() && now < popupUntilGet(pkg) && popupCooldownOK(pkg, now);
        if (!splashLive && !popupLive) return;

        try {
            /* 多窗口扫描：活动窗口优先，其次 getWindows() 同包窗口（弹窗/覆盖层） */
            AccessibilityNodeInfo[] roots = collectRoots(pkg);
            if (roots.length == 0) return;

            Long lastClick = lastClickAt.get(pkg);
            boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
            // v3.5.0：学习位置指纹（手动教过的坐标）与复验次数
            int[] tapHint = learnedPos(pkg);
            Integer triesBox = clickTries.get(pkg);
            int clickAttempts = triesBox == null ? 0 : triesBox.intValue();

            for (int ri = 0; ri < roots.length; ri++) {
                // v3.13.0 验证码横幅防护②：树内任一节点带「验证码/校验码/
                // 动态码/动态密码」文案（SystemUI 横幅窗/应用内自绘提示条/
                // 银行登录页）——本窗口绝不扫描、绝不点击、绝不学习。
                // 预算 60：横幅窗树极浅，零性能顾虑
                if (AdDecisionCore.treeHasCodeBannerMarker(new NodeAdapter(roots[ri]), 60)) {
                    codeBannerGuardLog(pkg, now);
                    continue;
                }
                energyScans++;
                Ctx ctx = new Ctx();
                ctx.screenW = screenWidth;
                ctx.screenH = screenHeight;
                ctx.pkg = pkg; // v3.10.0：分应用规则表
                ctx.elapsedMs = Math.max(0, elapsed - ri * 30);
                ctx.splashLive = splashLive;
                ctx.popupLive = popupLive;
                ctx.guard = AdDecisionCore.isGuardPkg(pkg);
                // v3.6.0 毒链① 根治：sdkAdWin 只认【当前窗口类名】是 SDK 广告窗
                //（v3.5.0 的 12s TTL 让主页还拿着 SDK 资格按返回键 → 退出应用）
                ctx.sdkAdWin = AdDecisionCore.isAdActivityClass(winClassOf(pkg));
                Boolean learned = winClsLearned.get(pkg);
                ctx.learnedCls = learned != null && learned.booleanValue();
                // v3.6.0 毒链② 根治：根窗口（入口窗）标记——返回键=退出应用，
                // L3/L4.5/L5 决策核心禁用 + execute() 双保险拦截
                ctx.rootWindow = winClassOf(pkg).equals(rootWinClsOf(pkg));
                ctx.clickAttempts = clickAttempts;
                ctx.tapHintX = tapHint[0];
                ctx.tapHintY = tapHint[1];
                ctx.budgetNodes = splashLive ? 600 : 280; // 开屏窗预算扩容（重树）

                /* ═══ v3.9.0 主路径：内嵌规则引擎 0ms 出手 ═══
                 * 规则只认控件属性（text/desc/vid），点击的是控件自身 bounds 中心
                 * ——应用按实际屏幕渲染，适配任意分辨率，绝无固定坐标盲点。
                 * Keep 开屏「跳过」文本键：窗口事件 0ms 首扫即中即点=露头秒跳。
                 * v3.10.0：五层规则（分应用表/全局精确/倒计时形态/短包含+诱导否决/
                 * vid skip 族+SDK 窗 close 族） */
                if (splashLive) {
                    Hit hit = RuleEngine.match(new NodeAdapter(roots[ri]), ctx);
                    if (hit != null) {
                        int cdHit = AdDecisionCore.parseCountdown(hit.label);
                        if (!countdownWait(pkg, cdHit, clickAttempts, now)) {
                            Decision rd = new Decision();
                            rd.action = AdDecisionCore.A_CLICK_BOUNDS;
                            rd.node = hit.gestureOnly ? null : hit.clickNode;
                            rd.bounds = hit.tapBounds;
                            rd.reason = "rule_" + hit.appId;
                            if (execute(pkg, rd, null, ctx, clickCoolOK, now)) return; // 命中即熔断
                        }
                        // 规则已命中（或倒计时等待中）——本窗就是标准跳过场景，
                        // 不再走决策核心兜底（避免角落键/热区在正常窗乱点）
                        continue;
                    }
                }

                /* ═══ 兜底路径：决策核心（复杂信号）═══ */
                ScanFind f = AdDecisionCore.walk(new NodeAdapter(roots[ri]), ctx);
                /* v3.8.0 倒计时门控（issue#10 二轮·喜马拉雅）：穿山甲类 SDK 倒计时
                 * 结束前跳过键不响应——「已点击×4 不跳转」真凶之一（提前点全白点）。
                 * v3.9.0 抽取为 countdownWait()（规则路径/兜底路径共用） */
                if (countdownWait(pkg, f.countdownN, clickAttempts, now)) continue;
                Decision d = AdDecisionCore.decide(f, ctx);
                if (d.action.equals(AdDecisionCore.A_NONE)) continue;
                if (execute(pkg, d, f, ctx, clickCoolOK, now)) return; // 命中即熔断
            }
        } catch (Exception ignored) {
        }
    }

    /* ───────── v3.9.0 倒计时门控（自 tryScan 抽取，规则/兜底两路共用）─────────
     * 穿山甲类 SDK 倒计时结束前跳过键不响应（提前点=白点还占冷却）。
     * 文案数字变化→重新锚定；首击未关→等到「锚点+剩余秒数-0.5s」再出手；
     * 倒计时走完（文案无数字）→立即恢复出手。返回 true=继续等待。 */
    private boolean countdownWait(String pkg, int cdN, int clickAttempts, long now) {
        if (cdN > 0) {
            Integer lastN = cdLastN.get(pkg);
            if (lastN == null || lastN.intValue() != cdN) {
                cdLastN.put(pkg, cdN);
                cdAnchorAt.put(pkg, now);
            }
            if (clickAttempts >= 1) {
                Long anchor = cdAnchorAt.get(pkg);
                long until = (anchor == null ? now : anchor.longValue())
                        + cdN * 1000L - AdDecisionCore.COUNTDOWN_EXTRA_MS;
                if (now < until) {
                    if (cdWaitUntil.put(pkg, until) == null) {
                        appendLog("⏳ 倒计时门控（" + shortPkg(pkg) + "）：跳过键 "
                                + cdN + "s 后才激活（提前点=白点）→ "
                                + (until - now) + "ms 后出手即命中");
                    }
                    return true;
                }
            }
            return false;
        }
        Long w = cdWaitUntil.get(pkg);
        if (w != null) {
            cdWaitUntil.remove(pkg);
            appendLog("⏳ 倒计时结束（" + shortPkg(pkg) + "）：跳过键已激活 → 出手");
        }
        return false;
    }

    /** 收集同包可扫描窗口根节点（活动窗口优先，最多 3 个） */
    private AccessibilityNodeInfo[] collectRoots(String pkg) {
        java.util.ArrayList<AccessibilityNodeInfo> out = new java.util.ArrayList<>();
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            CharSequence rp = active.getPackageName();
            if (rp != null && pkg.contentEquals(rp)) out.add(active);
        }
        if (out.isEmpty()) {
            try {
                List<AccessibilityWindowInfo> wins = getWindows();
                if (wins != null) {
                    for (AccessibilityWindowInfo w : wins) {
                        if (out.size() >= MAX_WINDOWS_SCAN) break;
                        CharSequence wp = w == null ? null : w.getRoot() == null ? null : w.getRoot().getPackageName();
                        if (wp != null && pkg.contentEquals(wp)) {
                            AccessibilityNodeInfo r = w.getRoot();
                            if (r != null) out.add(r);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return out.toArray(new AccessibilityNodeInfo[0]);
    }

    /** 执行决策；返回 true = 已出手（熔断扫描链） */
    private boolean execute(String pkg, Decision d, ScanFind f, Ctx ctx, boolean clickCoolOK, long now) {
        String how = d.action + "/" + d.reason;
        lastGesturePkg = pkg; // v3.7.0：手势取消诊断日志用
        // v3.6.0：点击梯子预算——同窗点击 ≥MAX_CLICKS_PER_WIN 即优雅收手
        //（绝不无限重试，更绝不升级返回键；等用户手动跳过，手动点击仍会被学习）
        if ((d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) || d.action.equals(AdDecisionCore.A_CLOSE)
                || d.action.equals(AdDecisionCore.A_TAP_POINT))
                && triesOfClick(pkg) >= AdDecisionCore.MAX_CLICKS_PER_WIN) {
            Boolean done = gaveUp.get(pkg);
            if (done == null || !done.booleanValue()) {
                gaveUp.put(pkg, Boolean.TRUE);
                appendLog("🤲 点击梯子已用尽（" + shortPkg(pkg) + "）：" + AdDecisionCore.MAX_CLICKS_PER_WIN
                        + " 次尝试未关闭广告 → 优雅收手（本窗不再动作；你的手动跳过仍会被学习）");
            }
            return false;
        }
        if (d.action.equals(AdDecisionCore.A_BACK)) {
            // v3.13.0 验证码横幅防护④：SystemUI 顶部横幅（heads-up）在场时
            // 绝不派返回键——BACK 会先关掉横幅（验证码/短信/来电被误关）
            if (headsUpBanner(null) != null) {
                headsUpGuardLog(pkg, System.currentTimeMillis());
                return false;
            }
            // v3.6.0 根窗口安全门（双保险，决策核心已先拦一道）：
            // 入口窗上按返回键 = 退出应用（喜马拉雅「再按一次退出到桌面」事故）
            if (ctx.rootWindow && !ctx.sdkAdWin) {
                appendLog("⛔ 根窗口安全门（" + shortPkg(pkg) + "）：入口窗禁返回键（防退出应用），只走点击梯子");
                return false;
            }
            if (triesOf(pkg) >= BACK_RETRY_MAX || !backAllowed(now)) return false;
            performGlobalAction(GLOBAL_ACTION_BACK);
            energyBacks++;
            backTries.put(pkg, triesOf(pkg) + 1);
            if (d.reason.startsWith("sdk_window")) learnClass(pkg, winClassOf(pkg));
            if ("click_verify_fail".equals(d.reason)) {
                appendLog("🛡 点击复验失败升级（" + shortPkg(pkg) + "）：同窗 " + ctx.clickAttempts
                        + " 次点击仍未关闭 → 返回键兜底温和关闭（不碰进程与服务）");
            } else {
                appendLog("🛡 广告窗直断（" + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                        + " · " + ctx.elapsedMs + "ms → 返回键温和关闭（不碰进程与服务）");
            }
            bumpStat(pkg, "back");
            /* v3.7.0（issue#10 微博根因）：返回键直断后【不熔断】——广告 Activity
             * 常拦截返回键（按了没反应）。600ms 后复验：同包窗口仍在则重开扫描链
             * 继续点击梯子（角落候选/坐标轮换），不再「按完就睡」 */
            handler.postDelayed(() -> {
                if (instance == null) return;
                try {
                    AccessibilityNodeInfo[] roots = collectRoots(pkg);
                    if (roots.length > 0) {
                        appendLog("🔁 返回键复验（" + shortPkg(pkg) + "）：广告窗仍在（返回键被拦截）→ 继续点击梯子");
                        bumpScanChain(pkg);
                    }
                } catch (Throwable ignored) {
                }
            }, 600);
            return true;
        }
        if (d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) || d.action.equals(AdDecisionCore.A_CLOSE)) {
            if (!clickCoolOK) return false;
            int attempts = triesOfClick(pkg);
            // v3.8.0：手势真实触点【首选】——performAction 首点常被应用无声吞掉
            //（喜马拉雅实锤）；派发失败才降级 performAction 兜底（见 clickNode）
            if (clickNode(d)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                clickTries.put(pkg, attempts + 1);
                if (d.action.equals(AdDecisionCore.A_CLOSE) && f != null) {
                    popupBlocks++;
                    lastPopupAt.put(pkg, now);
                    appendLog("🪟 弹窗广告已关闭（" + shortPkg(pkg) + "）："
                            + (f.adBadge ? "「" + f.badgeLabel + "」角标" : "Dialog+营销话术")
                            + " + 关闭键「" + f.closeLabel + "」");
                    bumpStat(pkg, "popup");
                } else {
                    if (attempts == 0) {
                        appendLog("⚡ 已点击跳过（" + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                                + "（" + (lastGesture ? "手势真实触点" : "无障碍代点兜底") + "）→ 1.2s 后复验，未关将换候选重试");
                    } else {
                        appendLog("🔁 复验未关闭 → 第 " + (attempts + 1) + " 次出手（" + shortPkg(pkg) + "）："
                                + reasonZh(d.reason) + "（手势真实触点·换第 " + (attempts + 1) + " 个候选，不再盲点同一坐标）");
                    }
                    bumpStat(pkg, "auto");
                }
                // v3.5.0 关键：点击后【不熔断】扫描链——由后续扫描复验；
                // 广告关掉会触发新窗口事件（计数清零）；仍在则换候选/升级兜底
                return true;
            }
            return false;
        }
        if (d.action.equals(AdDecisionCore.A_TAP_POINT)) {
            if (!clickCoolOK) return false;
            boolean isLearned = "learned_pos".equals(d.reason);
            if (gestureTap(d.x, d.y)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                clickTries.put(pkg, triesOfClick(pkg) + 1);
                energyGestures++;
                if (isLearned) {
                    appendLog("🎯 学习位置点击（" + shortPkg(pkg) + "）：你手动教过的位置指纹 ("
                            + d.x + "," + d.y + ")（手势真实触点·相对你的屏幕渲染）→ 0.9s 后复验");
                    bumpPosRule(pkg);
                } else {
                    // v3.9.0：coord_fallback 已移除（绝不盲点固定坐标），此分支仅防御性保留
                    appendLog("🎯 位置点击（" + shortPkg(pkg) + "）：(" + d.x + "," + d.y + ") → 0.9s 后复验");
                }
                bumpStat(pkg, "auto");
                return true; // 不熔断：交由复验扫描确认
            }
            return false;
        }
        return false;
    }

    private boolean lastGesture = false;

    /** v3.8.0：手势真实触点【首选】（GKD clickCenter 方法论）。
     *  v3.4.0~v3.7.0 以 performAction 优先：无障碍代点返回 true 也可能被应用
     *  无声吞掉——issue#10 二轮实锤「4 次点击成功，广告纹丝不动，手动点同一
     *  按钮立刻生效」。performAction 走无障碍点击通道，广告 SDK/自绘控件普遍
     *  只认真实触点。改为先 dispatchGesture 点【原标签控件】中心（=手动点击的
     *  完全等价物），派发失败（系统手势限流）才降级 performAction 兜底。
     *  假跳过候选（d.node=null）只走手势——大容器祖先 performAction=点开广告 */
    private boolean clickNode(Decision d) {
        lastGesture = false;
        if (d.bounds != null) {
            int cx = (d.bounds[0] + d.bounds[2]) / 2;
            int cy = (d.bounds[1] + d.bounds[3]) / 2;
            if (gestureTap(cx, cy)) {
                lastGesture = true;
                energyGestures++;
                return true;
            }
        }
        if (d.node instanceof NodeAdapter) {
            try {
                if (((NodeAdapter) d.node).n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** v3.7.0 手势结果回调（静态内部类，规避 d8 8.2 匿名类 bug）：
     *  手势被系统中断时记诊断日志——「手势派发成功但被取消」不再静默 */
    private static final class TapGestureCallback extends GestureResultCallback {
        private final String pkg;

        TapGestureCallback(String pkg) {
            this.pkg = pkg;
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            try {
                AdSkipService s = instance;
                if (s != null) {
                    s.appendLog("⚠ 手势被系统中断（" + s.shortPkg(pkg) + "）：真实触点未触达（系统手势限流/冲突），下轮扫描重试");
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** dispatchGesture 坐标点击（API 24+；真实触点，performAction 拦不住）。
     *  v3.7.0 根因修复（issue#10：「已点击跳过×4 无效果」）：
     *   ① 路径非零长度——单点路径（只有 moveTo）在部分 ROM 上不触发点击
     *    （业界验证：需 lineTo 形成真实触点轨迹）；
     *   ② 起始延迟 40ms——给前一帧渲染/事件处理留时间，立即派发易被丢弃；
     *   ③ 坐标边界钳制——越界坐标（学习位置/旧指纹）直接被系统丢弃；
     *   ④ 结果回调——被系统取消不再静默（诊断日志供下轮迭代）。
     *   ⑤ 前提：accessibility_service_config 已补 canPerformGestures=true
     *    （v3.6.0 及之前【缺失】——手势从未真正触达屏幕，喜马拉雅复验重试
     *    全部无效的头号根因）
     *   ⑥ 触点时长 60→110ms——更接近真人触点（80-150ms），防 SDK 触点时长校验 */
    private boolean gestureTap(int x, int y) {
        try {
            // v3.13.0 息屏熔断（纵深防御）：息屏时绝无可见广告，真实触点只会
            // 落在来电/闹钟等敏感窗上（误点=挂断电话/关闭闹钟）
            if (!isScreenOn(this)) return false;
            if (x <= 0 || y <= 0) return false;
            if (screenWidth > 0 && x >= screenWidth) x = screenWidth - 2; // 边界钳制
            if (screenHeight > 0 && y >= screenHeight) y = screenHeight - 2;
            // v3.13.0 验证码横幅防护③：触点被 SystemUI 顶部小窗（状态栏/
            // heads-up 横幅）覆盖时拒发手势——真实触点会被横幅截走（点掉
            // 验证码横幅的事故路径）。拒发后 clickNode 自动降级 performAction
            // 代点（走无障碍通道不经屏幕坐标，绝不碰横幅），学习位置点择机重试
            if (pointUnderHeadsUp(x, y)) {
                headsUpGuardLog(lastGesturePkg, System.currentTimeMillis());
                return false;
            }
            Path p = new Path();
            p.moveTo(x, y);
            p.lineTo(x + 1, y); // 非零长度路径：单点路径部分 ROM 不触发点击
            GestureDescription gd = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p, 40, 110))
                    .build();
            return dispatchGesture(gd, new TapGestureCallback(lastGesturePkg), null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 手势诊断用包名（gestureTap 调用前由 execute 设置） */
    private volatile String lastGesturePkg = "";

    private static String reasonZh(String r) {
        if (r.equals("rule_global_exact")) return "内嵌规则·文案精确命中（0ms 即点）";
        if (r.equals("rule_app_exact")) return "内嵌规则·分应用文案命中";
        if (r.equals("rule_global_pattern")) return "内嵌规则·倒计时形态命中（门控后出手）";
        if (r.equals("rule_global_contains")) return "内嵌规则·文案包含命中";
        if (r.equals("rule_global_vid")) return "内嵌规则·vid 跳过键（零文本图片键）";
        if (r.equals("rule_sdk_close_vid")) return "内嵌规则·SDK 窗图片关闭键";
        if (r.equals("skip_text")) return "文本跳过键";
        if (r.equals("fake_skip_tap")) return "假跳过手势直点（标签坐标）";
        if (r.equals("sdk_window")) return "SDK 广告窗指纹";
        if (r.equals("sdk_window_learned")) return "学习窗口指纹（快断）";
        if (r.equals("gyro_immediate")) return "翻转/摇一摇文案立即直断";
        if (r.equals("gyro_break")) return "陀螺仪诱导/假跳过直断";
        if (r.equals("corner_image_skip")) return "角落图片跳过键";
        if (r.equals("learned_pos")) return "学习位置（你手动教过）";
        if (r.equals("splash_close")) return "开屏×关闭键";
        if (r.equals("hot_zone")) return "全屏热区自绘广告";
        if (r.equals("coord_fallback")) return "坐标级兜底";
        return r;
    }

    /** 返回键全局节流：600ms 内最多一次 */
    private long lastBackAt = 0;

    private boolean backAllowed(long now) {
        if (now - lastBackAt < 600) return false;
        lastBackAt = now;
        return true;
    }

    private boolean popupCooldownOK(String pkg, long now) {
        Long t = lastPopupAt.get(pkg);
        return t == null || now - t >= POPUP_COOLDOWN_MS;
    }

    private boolean isLauncherLike(String pkg) {
        // v3.9.0：统一走 AdDecisionCore.isLauncherLike（黑名单/回弹/学习三处共用）
        return AdDecisionCore.isLauncherLike(pkg);
    }

    /* ───────── 智能学习：观察手动点击 + className 指纹 ───────── */

    private void learnFromManualClick(String pkg, AccessibilityEvent event, long now) {
        if (now - winAt(pkg) > LEARN_WINDOW_MS) return;

        String text = "";
        String viewId = "";
        AccessibilityNodeInfo srcNode = null;
        try {
            StringBuilder sb = new StringBuilder();
            if (event.getText() != null) {
                for (CharSequence c : event.getText()) {
                    if (c != null) sb.append(c);
                }
            }
            text = sb.toString().trim();
            AccessibilityNodeInfo src = event.getSource();
            if (src != null) {
                srcNode = src;
                if (text.length() == 0) text = cs(src.getContentDescription());
                viewId = cs(src.getViewIdResourceName());
            }
        } catch (Exception ignored) {
        }
        if (!AdDecisionCore.matchesSkip(text) && !AdDecisionCore.isCloseText(text)) {
            // v3.5.0 位置学习：图片跳过键零文本也要学（真机痛点：「手动记录两次
            // 还是不能跳过」——v3.4.0 只认文本，图片键永远学不到）
            learnPositionFromManualClick(pkg, srcNode, now);
            return;
        }

        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) {
                    rules = new JSONObject();
                    st.put("rules", rules);
                }
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) {
                    list = new JSONArray();
                    rules.put(pkg, list);
                }
                boolean merged = false;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r == null) continue;
                    boolean sameId = viewId.length() > 0 && viewId.equals(r.optString("viewId", ""));
                    boolean sameText = text.length() > 0 && text.equals(r.optString("text", ""));
                    if (sameId || sameText) {
                        r.put("manual", r.optInt("manual", 0) + 1);
                        r.put("ts", now);
                        if (r.optString("viewId", "").length() == 0 && viewId.length() > 0) {
                            r.put("viewId", viewId);
                        }
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    if (list.length() >= MAX_RULES_PER_APP) return;
                    JSONObject r = new JSONObject();
                    r.put("text", text);
                    r.put("viewId", viewId);
                    r.put("manual", 1);
                    r.put("auto", 0);
                    r.put("ts", now);
                    list.put(r);
                }
                bumpManualStat(st, pkg);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
        /* v3.7.0（issue#10 喜马拉雅根因）：文本键手动点击【同时学位置】——
         * 用户手指点通过的坐标 = 真实触点真正生效的位置（文本候选可能点不中，
         * 教过的位置下次同窗 500ms 直点，优先于文本候选盲点） */
        learnPositionFromManualClick(pkg, srcNode, now);
        appendLog("已学习：" + shortPkg(pkg) + " 中你手动点击了「" + text + "」→ 下次优先直点此位置（" + shortPkg(pkg) + " 同窗）");
    }

    private boolean learnedClassHit(String pkg, String cls) {
        if (cls == null || cls.length() == 0) return false;
        try {
            JSONObject rules = loadState().optJSONObject("rules");
            if (rules == null) return false;
            JSONArray list = rules.optJSONArray(pkg);
            if (list == null) return false;
            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null) continue;
                if (r.optBoolean("pos", false)) continue; // v3.5.0：位置规则走「点位置」而非快断
                if (cls.equals(r.optString("cls", ""))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void learnClass(String pkg, String cls) {
        if (cls == null || cls.length() == 0) return;
        // v3.6.0 毒链① 断源：只沉淀真正的广告窗类（SDK 广告 Activity）。
        // v3.5.0 无此校验 → MainActivity/FrameLayout/Dialog 全被学成
        // 「广告窗指纹」→ 每次进应用都在主页按返回键 → 退出应用
        if (!AdDecisionCore.isAdActivityClass(cls)) return;
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) {
                    rules = new JSONObject();
                    st.put("rules", rules);
                }
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) {
                    list = new JSONArray();
                    rules.put(pkg, list);
                }
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r != null && cls.equals(r.optString("cls", ""))) {
                        r.put("auto", r.optInt("auto", 0) + 1);
                        r.put("ts", System.currentTimeMillis());
                        saveState(st);
                        return;
                    }
                }
                if (list.length() >= MAX_RULES_PER_APP) return;
                JSONObject r = new JSONObject();
                r.put("text", "");
                r.put("viewId", "");
                r.put("cls", cls);
                r.put("manual", 0);
                r.put("auto", 1);
                r.put("ts", System.currentTimeMillis());
                list.put(r);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    /* ───────── 误触观察 ───────── */

    private void observeMisclick(String pkg, AccessibilityEvent event, long now) {
        try {
            if (screenWidth <= 0 || screenHeight <= 0) return;
            if (now - winAt(pkg) > WINDOW_MS) return;
            AccessibilityNodeInfo src = event.getSource();
            if (src == null) return;
            String label = cs(src.getText());
            if (label.length() == 0) label = cs(src.getContentDescription());
            if (AdDecisionCore.matchesSkip(label) || AdDecisionCore.isCloseText(label)) return;
            Rect r = new Rect();
            src.getBoundsInScreen(r);
            if (r.isEmpty()) return;
            double area = (double) r.width() * r.height();
            double screen = (double) screenWidth * screenHeight;
            boolean huge = (area / screen > 0.35) && (r.width() > screenWidth * 0.55);
            // v3.13.0：「快于人类反应」启发式【移除】——真机日志（issue#16/#17）
            // 实锤误触观察 48~60 条几乎全是正常秒跳被误记：熟练用户等在跳过键
            // 上，窗口一切换 500ms 内手点完全正常（这正是本应用的目标体验）。
            // 仅保留全屏热区观察（真信号：点在全屏热区被跳走=真实误触体验）
            if (!huge) return;
            synchronized (lock) {
                JSONObject st = loadState();
                JSONObject stats = st.optJSONObject("stats");
                if (stats == null) { stats = new JSONObject(); st.put("stats", stats); }
                stats.put("misclick", stats.optInt("misclick", 0) + 1);
                saveState(st);
            }
            // v3.13.0：日志同包 5s 去重（统计每条都计，仅防刷屏）
            Long lastM = misclickLogAt.get(pkg);
            if (lastM != null && now - lastM < 5000) return;
            misclickLogAt.put(pkg, now);
            appendLog("⚠️ 热区误触观察（" + shortPkg(pkg) + "）：全屏热区控件，轻微触碰即跳转 → 已记入统计");
        } catch (Exception ignored) {
        }
    }

    /* ───────── 开关 ───────── */

    private boolean learningEnabled() {
        try {
            JSONObject st = loadState();
            return !st.has("learning") || st.optBoolean("learning", true);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean popupShieldEnabled() {
        try {
            // v3.9.0：默认关闭（issue#12 实锤：内容突发检测对正常应用大量误报
            // 「疑似覆盖层弹窗」——美团/social/view/health 全中招）。学习中心
            // 可手动开启；开启后仍受 OEM 工具包豁免与 Dialog 形态双重约束。
            JSONObject st = loadState();
            return st.has("popupShield") && st.optBoolean("popupShield", false);
        } catch (Exception e) {
            return false;
        }
    }

    /* ───────── v3.5.0 位置学习：图片跳过键（零文本）教一次就会 ───────── */

    /**
     * 手动点击小角落/顶部/底部控件（零文本或跳过/关闭文本键）→ 记录归一化坐标 + 窗口类名指纹。
     * 次启同窗 500ms 手势直点该位置（决策核心 L1.4 前置，优先于文本候选盲点）。
     */
    private void learnPositionFromManualClick(String pkg, AccessibilityNodeInfo src, long now) {
        if (src == null || screenWidth <= 0 || screenHeight <= 0) return;
        // v3.13.0（issue#16 实锤）：OEM 系统工具包（安全中心/系统管家）与短信
        // 类包的手动操作绝不当「跳过键」学习——securitycenter 应用详情页被学出
        // (91%,9%)，下次进详情页 500ms 直点=在权限管理页乱点
        if (AdDecisionCore.isOemUtilityPkg(pkg) || AdDecisionCore.isSmsOverlayPkg(pkg)) return;
        if (now - winAt(pkg) > 6000) return; // v3.9.0：仅窗口切换后 6s 内（开屏期收紧，防误学主页面控件）
        try {
            Rect r = new Rect();
            src.getBoundsInScreen(r);
            if (r.isEmpty()) return;
            int[] b = new int[]{r.left, r.top, r.right, r.bottom};
            double area = AdDecisionCore.areaOf(b, screenWidth, screenHeight);
            if (area > AdDecisionCore.POS_LEARN_AREA_MAX) return; // 大热区=CTA，不学
            // v3.6.0：可学形状扩至底部（部分应用跳过键在右下角，真机反馈）
            if (!AdDecisionCore.learnableSkipShape(b, screenWidth, screenHeight)) return;
            final double x = ((b[0] + b[2]) / 2.0) / screenWidth;
            final double y = ((b[1] + b[3]) / 2.0) / screenHeight;
            // v3.7.0：位置健全性校验（真机日志：微信学出 (63%,102%) 越界位置，
            // 点击事件源节点 bounds 中心在屏幕外——点了也是死区，直接拒学）
            if (!AdDecisionCore.saneNormPos(x, y)) {
                // v3.13.0：拒绝日志去重（issue#16：同窗越界坐标 (90%,103%) 30 秒
                // 刷屏 6 条——手势导航条误触会反复触发，同包 30s 只记一条）
                Long lastRj = posRejectLogAt.get(pkg);
                if (lastRj == null || now - lastRj > 30000) {
                    posRejectLogAt.put(pkg, now);
                    appendLog("🚫 位置学习已拒绝（" + shortPkg(pkg) + "）：坐标 ("
                            + Math.round(x * 100) + "%," + Math.round(y * 100) + "%) 越界/贴边，点了也是死区");
                }
                return;
            }
            final String cls = winClassOf(pkg);
            synchronized (lock) {
                try {
                    JSONObject st = loadState();
                    JSONObject rules = st.optJSONObject("rules");
                    if (rules == null) {
                        rules = new JSONObject();
                        st.put("rules", rules);
                    }
                    JSONArray list = rules.optJSONArray(pkg);
                    if (list == null) {
                        list = new JSONArray();
                        rules.put(pkg, list);
                    }
                    boolean merged = false;
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject rr = list.optJSONObject(i);
                        if (rr == null || !rr.optBoolean("pos", false)) continue;
                        double dx = Math.abs(rr.optDouble("x", -1) - x);
                        double dy = Math.abs(rr.optDouble("y", -1) - y);
                        boolean sameCls = cls.length() == 0 || cls.equals(rr.optString("cls", ""));
                        if (dx <= 0.02 && dy <= 0.02 && sameCls) {
                            rr.put("manual", rr.optInt("manual", 0) + 1);
                            rr.put("ts", now);
                            rr.put("cls", cls);
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) {
                        if (list.length() >= MAX_RULES_PER_APP) return;
                        JSONObject rr = new JSONObject();
                        rr.put("pos", 1);
                        rr.put("x", Math.round(x * 1000) / 1000.0);
                        rr.put("y", Math.round(y * 1000) / 1000.0);
                        rr.put("cls", cls);
                        rr.put("text", "");
                        rr.put("viewId", "");
                        rr.put("manual", 1);
                        rr.put("auto", 0);
                        rr.put("ts", now);
                        list.put(rr);
                    }
                    bumpManualStat(st, pkg);
                    saveState(st);
                } catch (Exception ignored) {
                }
            }
            appendLog("📍 位置已学习（" + shortPkg(pkg) + "）：图片跳过键零文本，已记住位置 ("
                    + Math.round(x * 100) + "%," + Math.round(y * 100) + "%) → 下次启动同窗 500ms 直点");
        } catch (Exception ignored) {
        }
    }

    /** 学习位置指纹（当前窗口类名匹配、manual>0、最新优先）；无则 {-1,-1} */
    private int[] learnedPos(String pkg) {
        try {
            String cls = winClassOf(pkg);
            if (cls.length() == 0) return new int[]{-1, -1};
            JSONObject rules = loadState().optJSONObject("rules");
            if (rules == null) return new int[]{-1, -1};
            JSONArray list = rules.optJSONArray(pkg);
            if (list == null) return new int[]{-1, -1};
            JSONObject best = null;
            long bestTs = -1;
            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null || !r.optBoolean("pos", false)) continue;
                if (r.optInt("manual", 0) < 1) continue;
                if (!cls.equals(r.optString("cls", ""))) continue;
                long ts = r.optLong("ts", 0);
                if (ts > bestTs) {
                    bestTs = ts;
                    best = r;
                }
            }
            if (best == null) return new int[]{-1, -1};
            int x = (int) Math.round(best.optDouble("x", -1) * screenWidth);
            int y = (int) Math.round(best.optDouble("y", -1) * screenHeight);
            if (x <= 0 || y <= 0 || x >= screenWidth || y >= screenHeight) return new int[]{-1, -1};
            return new int[]{x, y};
        } catch (Exception ignored) {
            return new int[]{-1, -1};
        }
    }

    /** 学习位置命中沉淀 auto 次数 */
    private void bumpPosRule(String pkg) {
        try {
            String cls = winClassOf(pkg);
            synchronized (lock) {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) return;
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) return;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r == null || !r.optBoolean("pos", false)) continue;
                    if (cls.equals(r.optString("cls", ""))) {
                        r.put("auto", r.optInt("auto", 0) + 1);
                        saveState(st);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private int triesOfClick(String pkg) {
        Integer n = clickTries.get(pkg);
        return n == null ? 0 : n.intValue();
    }

    /* ───────── v3.13.0 验证码横幅保护：SystemUI 顶部小窗探测与守卫日志 ─────────
     * heads-up 横幅（验证码/短信/来电顶部弹窗）由 SystemUI 渲染成独立小窗
     * 覆盖在应用上层——无障碍代点（performAction）直达目标控件不受影响，但
     * 手势真实触点（dispatchGesture）按屏幕坐标派发，落在横幅范围内会被
     * 横幅截走=点掉横幅（真机事故：接收验证码时横幅被点掉）。返回键同理
     * （BACK 先关横幅）。判据：SystemUI/android 包窗口 + 位于屏幕上部
     *（top < 30% 屏高）+ 非全屏（高度 < 35% 屏高）——覆盖状态栏与横幅本体。 */

    /** 探测 SystemUI 顶部小窗；point 非空时仅返回覆盖该点的窗口，否则返回任一 */
    private Rect headsUpBanner(int[] point) {
        if (screenHeight <= 0) return null;
        try {
            List<AccessibilityWindowInfo> wins = getWindows();
            if (wins == null) return null;
            for (AccessibilityWindowInfo w : wins) {
                if (w == null) continue;
                AccessibilityNodeInfo wr = w.getRoot();
                if (wr == null) continue;
                CharSequence wp = wr.getPackageName();
                if (wp == null || !("com.android.systemui".contentEquals(wp) || "android".contentEquals(wp))) continue;
                Rect r = new Rect();
                w.getBoundsInScreen(r);
                if (r.isEmpty() || r.top >= screenHeight * 0.30 || r.height() >= screenHeight * 0.35) continue;
                if (point != null && !(point[0] >= r.left && point[0] <= r.right
                        && point[1] >= r.top && point[1] <= r.bottom)) continue;
                return r;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean pointUnderHeadsUp(int x, int y) {
        return headsUpBanner(new int[]{x, y}) != null;
    }

    /** 验证码横幅保护日志（同包 10s 去重） */
    private void codeBannerGuardLog(String pkg, long now) {
        Long last = codeBannerLogAt.get(pkg);
        if (last != null && now - last < 10000) return;
        codeBannerLogAt.put(pkg, now);
        appendLog("🛡 验证码横幅保护（" + shortPkg(pkg) + "）：检测到验证码/校验码文案 → 本窗口绝不扫描绝不点击");
    }

    /** heads-up 保护日志（同包 10s 去重；pkg 可能为空） */
    private void headsUpGuardLog(String pkg, long now) {
        String key = pkg == null ? "" : pkg;
        Long last = headsUpLogAt.get(key);
        if (last != null && now - last < 10000) return;
        headsUpLogAt.put(key, now);
        appendLog("🛡 顶部横幅保护（" + (key.length() == 0 ? "系统" : shortPkg(pkg))
                + "）：触点/返回键被验证码·短信横幅覆盖 → 已让路（绝不点掉横幅）");
    }

    /* ───────── v3.5.0 保活：前台常驻通知（系统不杀）+ 断连提醒撤销 ───────── */

    private void startKeepAliveForeground() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("keep", "秒跳引擎守护中",
                        NotificationManager.IMPORTANCE_MIN);
                ch.setDescription("服务保持开启，直到你手动关闭");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(this, "keep");
            } else {
                b = new Notification.Builder(this);
            }
            Intent it = new Intent(this, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentTitle("秒跳引擎守护中")
                    .setContentText("保持开启直到你手动关闭 · 点击进入开屏卫士")
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setColor(0xFF10B981);
            Notification notif = b.build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_KEEP, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_KEEP, notif);
            }
        } catch (Throwable ignored) {
        }
    }

    private void cancelAlertNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(NOTIF_ALERT);
        } catch (Exception ignored) {
        }
    }

    /* ───────── 统计与日志 ───────── */

    private void bumpManualStat(JSONObject st, String pkg) {
        try {
            JSONObject stats = st.optJSONObject("stats");
            if (stats == null) {
                stats = new JSONObject();
                st.put("stats", stats);
            }
            stats.put("manual", stats.optInt("manual", 0) + 1);
            JSONObject byApp = stats.optJSONObject("byApp");
            if (byApp == null) {
                byApp = new JSONObject();
                stats.put("byApp", byApp);
            }
            JSONObject a = byApp.optJSONObject(pkg);
            if (a == null) {
                a = new JSONObject();
                byApp.put(pkg, a);
            }
            a.put("manual", a.optInt("manual", 0) + 1);
        } catch (Exception ignored) {
        }
    }

    private void bumpStat(String pkg, String kind) {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject stats = st.optJSONObject("stats");
                if (stats == null) {
                    stats = new JSONObject();
                    st.put("stats", stats);
                }
                stats.put(kind, stats.optInt(kind, 0) + 1);
                JSONObject byApp = stats.optJSONObject("byApp");
                if (byApp == null) {
                    byApp = new JSONObject();
                    stats.put("byApp", byApp);
                }
                JSONObject a = byApp.optJSONObject(pkg);
                if (a == null) {
                    a = new JSONObject();
                    byApp.put(pkg, a);
                }
                a.put(kind, a.optInt(kind, 0) + 1);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    private void appendLog(String msg) {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONArray log = st.optJSONArray("log");
                if (log == null) {
                    log = new JSONArray();
                    st.put("log", log);
                }
                JSONObject entry = new JSONObject();
                entry.put("t", System.currentTimeMillis());
                entry.put("msg", msg);
                log.put(entry);
                while (log.length() > MAX_LOG) {
                    log.remove(0);
                }
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /* ───────── 内存聚合 + 延迟落盘 ───────── */

    private org.json.JSONObject stateInMemory;

    private org.json.JSONObject loadState() {
        synchronized (lock) {
            if (stateInMemory != null) return stateInMemory;
        }
        try {
            String raw = prefs().getString(KEY_STATE, null);
            stateInMemory = raw != null ? new org.json.JSONObject(raw) : new org.json.JSONObject();
        } catch (Exception e) {
            stateInMemory = new org.json.JSONObject();
        }
        return stateInMemory;
    }

    private void saveState(org.json.JSONObject st) {
        stateInMemory = st;
        scheduleFlush();
    }

    private void scheduleFlush() {
        synchronized (lock) {
            if (flushScheduled) return;
            flushScheduled = true;
        }
        handler.postDelayed(() -> flushNow(), FLUSH_DELAY_MS);
    }

    public void flushNow() {
        synchronized (lock) {
            flushScheduled = false;
        }
        org.json.JSONObject st = stateInMemory;
        if (st == null) return;
        try {
            prefs().edit().putString(KEY_STATE, st.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private String cs(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    /* ───────── v3.8.0 WRITE_SECURE_SETTINGS 自愈重绑（可选高级能力）─────────
     * Android 安全设计：无障碍服务受 BIND_ACCESSIBILITY_SERVICE 签名级权限保护，
     * 任何应用（含自己）都不能 startService/bindService 它——v3.6.0/v3.7.0 的
     * 「startService 拉活重绑」实为 SecurityException，自动恢复从未真正生效。
     * 系统提供的唯一正道（GKD 进阶玩法同款）：用户经 adb 一次性授权
     *   adb shell pm grant com.zai.adshield android.permission.WRITE_SECURE_SETTINGS
     * 之后本应用可直接写 Settings.Secure：
     *  · 服务被杀未解绑 → 从 ENABLED_ACCESSIBILITY_SERVICES 摘除再写回 →
     *    系统立刻重新 bind（进程随之拉起）——「清理后台重进要手动开」终结
     *  · 覆盖安装被系统关闭（MY_PACKAGE_REPLACED 必关）→ 直接写回开启状态 */
    public static boolean hasSecureWrite(Context ctx) {
        try {
            return ctx.checkPermission("android.permission.WRITE_SECURE_SETTINGS",
                    android.os.Process.myPid(), android.os.Process.myUid())
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 自愈重绑：true=已发起（重绑异步生效）；未授权返回 false */
    public static boolean selfHealBind(Context ctx) {
        if (!hasSecureWrite(ctx)) return false;
        try {
            final android.content.ContentResolver cr = ctx.getContentResolver();
            final String key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
            final ComponentName cn = new ComponentName(ctx, AdSkipService.class);
            final String flat = cn.flattenToString();
            final String flatShort = cn.flattenToShortString(); // v3.8.1：兼容短格式条目（adb 手动写入场景）
            final String cur = Settings.Secure.getString(cr, key);
            // v3.8.1：登记判断双格式匹配——全格式或短格式条目任一命中即视为已登记，
            // 否则短格式场景会被误判「未登记」再追加产生冗余双条目
            if (cur == null || !(cur.contains(flat) || cur.contains(flatShort))) {
                // 未登记（被系统关闭/覆盖安装后清除）→ 直接写回开启
                String next = (cur == null || cur.trim().isEmpty()) ? flat : cur + ":" + flat;
                Settings.Secure.putString(cr, key, next);
                Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1");
                return true;
            }
            // 已登记但进程已死 → 摘除再写回，强制系统重绑（bind 会拉起进程）
            StringBuilder keep = new StringBuilder();
            for (String s : cur.split(":")) {
                String t = s.trim();
                if (t.length() == 0 || t.equals(flat) || t.equals(flatShort)) continue; // v3.8.1：双格式都摘除
                if (keep.length() > 0) keep.append(':');
                keep.append(t);
            }
            String without = keep.toString();
            Settings.Secure.putString(cr, key, without); // 摘除 → 系统解绑
            Thread.sleep(400);                            // 等解绑落地
            Settings.Secure.putString(cr, key,            // 写回 → 系统 bind（拉起进程）
                    without.isEmpty() ? flat : without + ":" + flat);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private String shortPkg(String pkg) {
        if (pkg == null) return "";
        int i = pkg.lastIndexOf('.');
        return i >= 0 && pkg.length() - i - 1 > 1 ? pkg.substring(i + 1) : pkg;
    }
}
