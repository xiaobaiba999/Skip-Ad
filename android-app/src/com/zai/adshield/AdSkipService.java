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
import com.zai.adshield.core.HumanTap;
import com.zai.adshield.core.MiniProgramGuard;
import com.zai.adshield.core.PowerGovernor;
import com.zai.adshield.core.RestoreGuard;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.Subscription;
import com.zai.adshield.core.TapPoint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 开屏广告智能跳过服务（无障碍服务实现 · v5.1.0 · 纯跳过引擎）
 * ─────────────────────────────────────────────────────────────
 * v5.1.0（issue#46/47 v5.0.0 日志实锤：非主流 App 噪声扫描刷屏 + 首页
 * 落定期重复扫描）：
 *  · 零候选退避：同窗连续 3 次零候选（微树 ≤6 节点 2 次）且过 2.6s
 *    （微树 1.2s）→ 本窗剩余固定扫描档取消——校园/工具类 App 校徽启动
 *    页（school.app 9 秒 7 条未命中）与商店首页不再被反复整树扫描；
 *    nudge 救回通道不受影响（晚到真跳过键照点不误）；广告窗/学习窗/
 *    Dialog 窗绝不退避（按钮可能 1~2s 晚到）；角落候选须过 L4 广告形态
 *    门才算「有候选」（校徽页扫码键是角落形态但 L4 拒点）；
 *  · 跳过后静默期：复验通过 2.2s 内不记「开屏未命中」（waimaiii 实锤：
 *    广告关闭同一秒又对 79 节点首页整树扫描并记未命中 = 纯噪声日志）；
 *  · 未命中日志微树过滤：≤5 节点加载占位窗（「树 4 节点 FrameLayout」
 *    连环刷屏）不再记录。
 * v3.14.0（issue#21 日志 + 真机截图实锤：喜马拉雅跳过开屏后 10 次有 8 次
 * 弹出「不喜欢/反馈投诉」底部面板，用户每次都要手动收起）：
 *  · 跳过后反馈弹窗自动收起：引擎 12s 内自动跳过过的包里出现反馈面板
 *   （强特征「我为什么会看到广告/反馈投诉」等 + 总特征 ≥2，决策核心
 *    findFeedbackSheet）→ ① 视为广告已关（反馈面板只在广告关闭后弹出，
 *    停用换候选重试——issue#21 实锤复验风暴 ×2~4 次的元凶）；② 自动代点
 *    文案精确为「取消」的控件收起面板（绝不替用户提交任何反馈选项）。
 *  · 露头就秒提速：手势被系统中断（限流/冲突）→ 退冷却 + 350ms 快速补扫
 *    重试（此前要等下轮扫描 1.2s+）；内容突发扫描节流 80→50ms（前 4s）。
 *  · ADB 自愈授权【整体移除】（用户反馈：要电脑执行太麻烦）——
 *    WRITE_SECURE_SETTINGS 检查/自愈重绑/授权状态卡全部删除；开启服务
 *    只需一步直达系统开关（openA11yDetail，Android 11+ 只剩一个开关），
 *    开启一次后前台常驻通知 + onUnbind 保留 + 心跳自检持续守护，退出
 *    App/清理后台后重进无需重新开启（学习数据全部保留）。
 *  · 孤儿代码清理：fuseChain（无调用点）、isLauncherLike 私有包装
 *   （已统一走 AdDecisionCore）删除；弹窗突发日志同包 30s 去重。
 *
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
    /* ═══ v7.15.12 更新存活链（用户实锤：更新安装新版后「日志不记录 + 开屏全不跳」
     * 常现——覆盖安装杀进程后无障碍重绑时机失控，ROM 侧还可能吞/延迟
     * MY_PACKAGE_REPLACED 广播与心跳闹钟）。四件套：
     *  ① preserveStateForUpdate：拉起安装器前【同步 commit】落盘状态
     *    （apply 异步写会被安装杀进程截断——最后 ≤15s 日志/战绩不再丢）
     *    + 预排 +25s 自检闹钟（RTC 闹钟跨 APK 替换存活，ROM 吞广播时
     *    兜底触发 KeepAliveReceiver 踢链梯子）；
     *  ② SmartRevive 终判梯子（KeepAliveReceiver 侧）：覆盖安装发现服务
     *    死了且三轮踢链失败 → +30s 复查并入 5 分钟心跳循环；
     *  ③ deadSince 断线可见化：断线首次检出即记时戳，重连时日志给出
     *    「离线 X 分 Y 秒」——「日志为什么断流/广告为什么没跳」直接在
     *    日志里可回答，不再日志黑洞；
     *  ④ appendLogStatic：服务已死时的日志通道（prefs 直写，重连后
     *    可见——覆盖安装完成/断线检出等关键时刻不再空白）。 */
    private static final String KEY_DEAD_SINCE = "deadSinceMs";
    static volatile long deadSinceMs = 0L;
    /* v4.1.0：App 内主开关（免去看系统设置 10 秒警告的暂停/恢复）。
     * 服务保持存活（保活四件套不受影响），但暂停期间绝不扫描、绝不
     * 点击、绝不学习、绝不突发检测——只维持窗口/前台追踪，恢复即无缝衔接。 */
    private static final String KEY_MASTER = "masterOn";
    static volatile boolean masterOn = true;

    /* ═══ v4.3.0 应用白名单（用户圈定绝不打扰的应用）═══
     * 白名单内应用与桌面黑名单同级：事件入口直接丢弃——零扫描、
     * 零点击、零学习、零突发检测（该应用内卫士完全「不存在」）。
     * 静态 HashSet 快照：服务/页面双端读写，add/remove 原子换快照。 */
    private static final String KEY_WHITELIST = "whitelistCsv";
    static volatile java.util.HashSet<String> whitelistSet = new java.util.HashSet<>();

    private static final int NOTIF_KEEP = 1;   // 前台保活常驻通知
    public static final int NOTIF_ALERT = 2;   // 服务断开提醒（KeepAliveReceiver 发，服务连上即撤）

    private static final long WINDOW_MS = 12000;       // 开屏时间窗
    /* ═══ v7.15.5 开屏观察窗分域：SDK 广告窗 20s ═══
     * 视频开屏（激励视频式开屏）的跳过键常在视频末 5~15s 才露头——12s 硬
     * 封顶 = 字面意义的「不再扫」（旧时间线 v7.8.0 视频开屏卫士的核心
     * 结论，版本线重制时随基线丢失，本轮最小恢复）。只对 SDK 广告窗
     * （isAdActivityClass——窗口本身就是广告，零候选退避本就豁免）放宽；
     * 普通窗 12s 不变（内容页早退/零候选退避防线原样在岗）。梯子侧
     * bumpScanChain 对 SDK 广告窗同步补 14/16.5/19s 三档兜底针。 */
    private static final long VIDEO_SPLASH_WINDOW_MS = 20000;
    /* v7.2.0 feed 窗闸：朋友圈等信息流窗不是开屏窗——旧 12s 保守闸把头几屏
     * feed 扫描全饿死；2.5s 即开扫（开屏链与 feed 同窗时由点击冷却互斥）。 */
    private static final long FEED_START_MS = 2500;
    /* v4.4.0 复验提速：点击冷却 900→600ms（快于人类「发现未关→再点」反应链；
     * 安全不破：复验仍需重新扫到跳过候选才出手，广告关了候选自然消失）
     * v5.6.0 激进化：600→500ms（用户实锤：好多广告没有命中——第一方自绘
     * 广告复验换候选节奏被冷却拖慢，快 0.1s = 广告可见时间短 0.1s。
     * v5.7.0 扫描日志继续激进化：500→420ms——复验链换候选节奏再快
     * 0.08s（复验安全闸不变：重新扫到候选才出手）。
     * v5.8.0 屏蔽速度压榨：420→300ms——复验安全闸不变（重新扫到候选才
     * 出手），轮换节奏再快 0.12s；深树广告窗候选多（SKIP_CANDS_MAX=8），
     * 旧冷却 420ms 会把「换下一个候选」拖成平帧等待，300ms 与补扫节流
     * （80ms）对齐后换候选几乎零等待。 */
    private static final long COOLDOWN_MS = 300;
    private static final long LEARN_WINDOW_MS = 30000; // 手动点击学习宽时间窗
    private static final int MAX_RULES_PER_APP = 8;
    /* v7.0.0 日志留存再扩容（用户实锤：留存时间要更长）：300→400 条——
     * 覆盖完整开屏周期（含订阅拉取失败/自愈/误触防护/离线快照导入等
     * 重点事件；kvSet 256KB 上限内 400 条日志依旧富余） */
    private static final int MAX_LOG = 400;

    /* ═══ v4.2.0 用户意图让路（issue#31-37：「用户点开弹窗广告落地页又被
     * 引擎自动关闭，手动重开又被关」根治）═══
     * 引擎绝不与用户意图对抗：
     *  · 用户真实点击（非引擎代点）后 4s 内，该应用一律不出手（点击/返回键全部让路）；
     *  · 用户点击后 2.5s 内切到的新窗口（非 Dialog）= 用户主动打开（广告落地页/详情页），
     *    30s 内绝不对其任何动作——你打开的页面，绝不替你关。 */
    private static final long USER_YIELD_MS = 4000;
    private static final long USER_OPEN_WIN_MS = 2500;
    private static final long USER_OPEN_GUARD_MS = 30000;
    private final java.util.HashMap<String, Long> userActAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, String> userOpenCls = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> userOpenUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> yieldLogAt = new java.util.HashMap<>();
    /* v4.2.0：弹窗窗期内容变化补扫节流（4s 后 300ms 一次） */
    private final java.util.HashMap<String, Long> popupNudgeAt = new java.util.HashMap<>();
    /* v6.1.0 误触二轮三防线接线：F 同指纹目标静默（指纹→上次派发时刻） +
     * H 结果静默期（包→上次手势派发成功时刻）；窗口切换随点击计数一并清空 */
    private final java.util.HashMap<String, Long> firedTargetAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastGestureOkAt = new java.util.HashMap<>();

    /* v6.1.0：每 8 小时自动提交引擎日志（用户实锤：一天自动提交 3 次、
     * 24 小时均匀 → 12h→8h；成功才计时，服务未运行/息屏跳过等下轮心跳）
     * v7.15.2：v6.1.0 的 8h 从未真正落地——issue 链实锤：唯一一次自动上传
     * 是 v7.8.0 的 MiTV（服务被 ROM 反复解绑 → onUnbind 排的心跳链恰好活着）；
     * 手机上服务健康常驻时 onServiceConnected 直接 cancelHeartbeat()——心跳
     * （自动上传/订阅静默更新/每日导入的唯一触发源）整段死链。修复：
     * 服务活着时改排【维护心跳】（窗口式非精确闹钟，30 分钟一档，Doze
     * 维护窗内批量调度零唤醒）——存活性自检仍归它（发现延迟 5→30 分钟）。 */
    public static final long AUTO_LOG_MS = 8 * 60 * 60 * 1000L;
    /* v7.15.2：服务存活期维护心跳节拍（30 分钟窗口式：自动上传/订阅更新/
     * 每日导入三项维护任务 + 免费存活性自检；对比：服务死亡期心跳 5 分钟
     * （快速自愈提醒）→ KeepAliveReceiver 按 isAlive() 分档续期） */
    public static final long ALIVE_HEARTBEAT_MS = 30 * 60 * 1000L;

    /* 极速链（v4.3.0 功耗收紧：16 档 → 13 档——前 4s 的内容变化补扫已覆盖
       密集相位，固定档只留骨架；露头就秒跳不变（0/80/200ms 三连快扫）
       v5.8.0 屏蔽速度压榨：13→18 档，前 4s 密度×2——骨架档是「内容事件
       不发射」广告窗（部分自绘窗/H5 容器不发 CONTENT_CHANGED）的唯一
       依赖，旧档位 700→1100→1700ms 的间隙即此类窗的命中延迟；前 4s
       改 60/150/320/520/780/1100/1500/1980/2550/3220ms 密档，后续几何
       回归长档。单档扫描为预算内整树 walk（典型树 < 1ms），功耗实测
       走 PowerGovernor 典型日模型仍 < 0.15mWh 红线。） */
    private static final long[] SCAN_AT =
            {0, 60, 150, 320, 520, 780, 1100, 1500, 1980, 2550, 3220, 4000,
                    4900, 5950, 7150, 8550, 10200, 12000};
    /* v7.15.5：SDK 广告窗专用补档（配合 VIDEO_SPLASH_WINDOW_MS=20s——
     * 视频开屏的跳过键常在视频末 5~15s 才露头；仅 isAdActivityClass 窗排）。 */
    private static final long[] VIDEO_SPLASH_SCAN_AT = {14000, 16500, 19000};
    /* 返回键重试：每个窗口最多直断次数（个别广告拦截第一次返回键） */
    private static final int BACK_RETRY_MAX = 2;
    /* 弹窗窗期与冷却（v5.6.0：弹窗冷却 2500→2000ms——弹窗广告复验更快；
     * v5.7.0 扫描日志继续激进化：2000→1600ms——弹窗广告复验换候选
     * 节奏再提速；POPUP_SCAN_THROTTLE 700ms 节流不变，功耗红线不破）
     * v5.8.0 屏蔽速度压榨：1600→1200ms + 节流 700→480ms——弹窗窗期
     * 内容事件密集，节流是补扫延迟上限，480ms 后单窗弹窗命中链路
     * 再快 0.22s；功耗红线（典型日 < 0.15mWh）实测保持。 */
    private static final long POPUP_WINDOW_MS = 10000;
    private static final long POPUP_COOLDOWN_MS = 1200;
    private static final long POPUP_SCAN_THROTTLE_MS = 480;
    private static final int BURST_COUNT = 3;
    private static final long BURST_WIN_MS = 1500;
    /* 多窗口扫描上限（能耗） */
    private static final int MAX_WINDOWS_SCAN = 3;
    /* ═══ v7.15.8【事故修复】多窗扫描节流与微树门 ═══
     * v7.15.7 事故复盘（用户实锤：升级后日志零记录、开屏全不跳——服务级
     * 瘫痪）：旧实现在每个开屏窗的【每轮扫描】里无条件枚举 getWindows()
     * 并对列表里【每个窗口】调 getRoot()——MIUI/HyperOS 窗口列表常 20+ 窗
     * （状态栏/导航条/灵动岛/系统覆盖层全在列）= 每轮 20+ 次 binder 调用；
     * 屏闪型开屏的内容事件经 80ms 节流补扫 → 12.5 轮/秒 × 20+ binder
     * = 每秒 250+ 次跨进程往返打满服务主线程与 AccessibilityManagerService
     * → 无障碍事件派发超时 → 系统杀服务 → 无日志零跳过整体瘫痪。
     * 三重收敛：① 微树门（活动窗 ≤48 节点且零候选——Keep 型「跳过键住在
     * 覆盖层、活动窗只剩 8 节点空壳」铁签名才枚举补窗）；② 400ms 枚举
     * 节流（v7.15.9 起探针/扫描【分账】——共用账时探针前置每 ~400ms 消耗
     * 预算，扫描微树门在真机上永远拿不到枚举机会：#102 实锤 v7.15.8 全程
     * 零🪟，两账各自 400ms 最坏双枚举仍比 v7.15.7 风暴低一个量级）；
     * ③ 类型前置过滤（TYPE_APPLICATION 才 getRoot——parcel 本地字段零
     * binder，状态栏/输入法/系统层全免）。 */
    private static final long MULTI_WIN_ENUM_THROTTLE_MS = 400;
    /* v7.15.9：常量委托核心（SHELL_TREE_NODES）——微树空壳口径与
     * zeroCandBackoff 豁免/多窗补扫门/未命中日志标记四处同源。 */
    private static final int MULTI_WIN_MICRO_TREE_MAX =
            com.zai.adshield.core.AdDecisionCore.SHELL_TREE_NODES;

    /* 能耗治理：内存聚合 + 延迟落盘 */
    private static final long FLUSH_DELAY_MS = 15000;
    private boolean flushScheduled = false;

    /* 能耗画像（开机累计，学习中心展示） */
    public long energyEvents = 0;
    public long energyScans = 0;
    public long energyBacks = 0;
    public long energyGestures = 0;
    public long popupBlocks = 0;

    /* ═══ v5.3.0 功耗治理（PowerGovernor 三零治理 · 李跳跳式）═══
     * 真机日志 #57/#58（v5.2.0）实锤：事件 4604/扫描 2396 每开机——
     * ①宿主主窗误扫（豆包 MainActivity 115 节点/微信 ExdeviceRankInfoUI
     * 110 节点每次启动都被 13 档梯子整树扫描）②聊天流式内容反复触发
     * 突发误报（豆包 5 分钟 7 次开 10s 弹窗识别窗）③零文本手动点击无
     * 验证直接学位置（同秒学了 (17%,82%) 与 (41%,82%) 两处聊天按钮）。
     * 三零治理：CLEAN 档案窗零扫描零调度零日志，探针哨兵（系统原生
     * findAccessibilityNodeInfosByText）前置替班；突发预算制；位置学习
     * 事后验证（真跳过键点击必关窗，聊天点击必不关窗）。 */
    public long energyProbes = 0;       // 原生探针执行次数（成本≈扫描的 1/14）
    public long energyProfileSkips = 0; // 档案省电跳过的整窗扫描次数
    public long energyBursts = 0;       // 开启的突发识别窗次数
    public int posLearnVerified = 0;    // 位置学习验证通过
    public int posLearnDropped = 0;     // 位置学习验证丢弃（聊天点击等）
    /** 宿主窗档案：pkg|cls → 连续零候选次数（≥3 判 CLEAN；持久化） */
    private final java.util.HashMap<String, Integer> winStreak = new java.util.HashMap<>();
    /** 宿主窗档案：pkg|cls → 上次全量扫描时刻（72h 复查闸） */
    private final java.util.HashMap<String, Long> winLastFull = new java.util.HashMap<>();
    /** 突发预算：pkg → {10min 窗口起点, 已开识别窗数} */
    private final java.util.HashMap<String, long[]> burstBudget = new java.util.HashMap<>();
    /** 突发阈值升级等级：pkg → level（空手突发后 3→6→12→24 事件） */
    private final java.util.HashMap<String, Integer> burstEsc = new java.util.HashMap<>();
    /** 突发开窗时的弹窗战果基线：pkg → popupBlocks（判空手升级用） */
    private final java.util.HashMap<String, Long> burstBasePopup = new java.util.HashMap<>();
    /** 探针哨兵补扫节流：pkg → 上次哨兵时刻（CLEAN 窗 250ms 节流） */
    private final java.util.HashMap<String, Long> probeNudgeAt = new java.util.HashMap<>();
    /** v7.15.10 探针去重：pkg → 上次探针执行时刻（200ms——0ms 排针与内容事件探针双跑收敛） */
    private final java.util.HashMap<String, Long> probeLastAt = new java.util.HashMap<>();
    /* v7.15.6 图形探针节流：pkg|winStart → 上次图形探针时刻（1.5s 一档
     * ——CLEAN 档案窗的动态图开屏（屏闪高频内容事件）不能每次都跑浅 DFS） */
    private final java.util.HashMap<String, Long> graphicProbeAt = new java.util.HashMap<>();
    /* v7.15.6 热区开屏窗晚到键补扫：pkg → 上次补扫时刻（500ms 节流）；
     * hotZoneWin：pkg|winStart → 本窗已确认热区（walk 见过全屏可点广告图） */
    private final java.util.HashMap<String, Long> hotNudgeAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Boolean> hotZoneWin = new java.util.HashMap<>();
    /** 档案省电日志去重：pkg → 上次记日志时刻（60s 一条，防刷屏） */
    private final java.util.HashMap<String, Long> profileLogAt = new java.util.HashMap<>();

    /** v5.3.0 位置学习挂起验证：零文本手动点击先挂起，1.5s 内窗类切换/
     * 树体缩水 ≥40% 才提交（真跳过键点击必关窗；聊天点击必不关窗） */
    private static final class PendingPos {
        double x, y;      // 归一化坐标
        String cls;       // 点击时窗口类名
        long at;          // 点击时刻
        int nodes;        // 点击时树规模
        boolean adLike;   // 点击时窗树具备广告窗特征（简单树/无滚动/无输入）
    }
    private final java.util.HashMap<String, PendingPos> pendingPos = new java.util.HashMap<>();
    /** v5.3.0 挂起验证节流：pkg → 上次树体缩水检查时刻 */
    private final java.util.HashMap<String, Long> posVerifyNudgeAt = new java.util.HashMap<>();
    /** v5.3.0 丢弃日志去重：pkg → 上次拦截日志时刻 */
    private final java.util.HashMap<String, Long> posDropLogAt = new java.util.HashMap<>();

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
    /* v7.15.1 真实应用前台追踪：锁屏宿主（systemui/keyguard）会在息屏
     * 期间把 lastFgPkg 顶掉真实应用（零扫描门在息屏门前就登记锁屏窗），
     * 亮屏/解锁 rearm 的目标解析（RearmPlanner）据此校正回真实应用。
     * 本字段只在主路径（真实可扫描应用成为前台）更新——锁屏/桌面/
     * 白名单/零扫描宿主永不写入，天然免疫污染。 */
    private volatile String lastAppPkg = null;
    /* v3.6.0：本窗点击梯子已优雅收手（预算用尽后不再动作，新窗口重置） */
    private final java.util.HashMap<String, Boolean> gaveUp = new java.util.HashMap<>();
    private final java.util.HashMap<String, Integer> backTries = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> popupUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastPopupScanAt = new java.util.HashMap<>();
    /* v3.13.0 日志去重：位置学习拒绝/验证码横幅/heads-up 保护/热区误触 */
    private final java.util.HashMap<String, Long> posRejectLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> codeBannerLogAt = new java.util.HashMap<>();
    /** v5.0.0：小程序容器树标识兜底日志节流（同包 10s 去重） */
    private final java.util.HashMap<String, Long> miniGuardLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> headsUpLogAt = new java.util.HashMap<>();
    /* v5.9.0 覆盖层防误触日志节流（同包 10s 去重——输入法/悬浮窗/他应用 overlay 拦截） */
    private final java.util.HashMap<String, Long> overlayLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> misclickLogAt = new java.util.HashMap<>();
    /* v3.14.0 跳过后反馈弹窗：最近引擎自动跳过时间 / 日志去重 / 代点冷却 */
    private final java.util.HashMap<String, Long> skipAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> feedbackLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> feedbackClickAt = new java.util.HashMap<>();
    /* v3.14.0：弹窗突发日志去重（issue#21：meituan 60s 内 3 条同语刷屏） */
    private final java.util.HashMap<String, Long> burstLogAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, long[]> burstWin = new java.util.HashMap<>();
    /* v3.5.0：点击复验——本窗已点击次数（换候选/真实触点重试/返回键兜底的依据） */
    private final java.util.HashMap<String, Integer> clickTries = new java.util.HashMap<>();
    /* v3.8.0 倒计时门控（issue#10 二轮·喜马拉雅）：跳过文案带数字（「跳过 3」）
     * 时穿山甲类 SDK 的跳过键尚未激活——提前点=白点还占 1.2s 冷却。锚点跟踪
     * 文案数字变化，首击未关 → 门控到「锚点+剩余秒数-0.5s」再出手，出手即命中 */
    private final java.util.HashMap<String, Integer> cdLastN = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> cdAnchorAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> cdWaitUntil = new java.util.HashMap<>();
    /* v4.3.0：假倒计时日志去重（数字 1.6s 不变 = 装饰性数字，不再门控） */
    private final java.util.HashMap<String, Long> cdFakeLogAt = new java.util.HashMap<>();
    /* v4.3.0 功耗三件套：内容页早退（本窗剩余固定扫描全取消）/ 补扫真节流 /
     * 小窗探测缓存（灵岛事件不再每次 getWindows 重 IPC） */
    private final java.util.HashMap<String, Long> chainKilledAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> nudgeAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, long[]> overlayCache = new java.util.HashMap<>();
    /* v4.3.0：扫描未命中诊断日志（每窗一条——用户提交日志能看到为什么
     * 不跳，开发侧据此补规则；issue#38「无法跳过且日志没记录」根治） */
    private final java.util.HashMap<String, Long> missLogAt = new java.util.HashMap<>();
    /* v5.1.0 零候选退避（issue#46/47 实锤）：同窗连续零候选扫描计数
     * （key=pkg|winStart）——满 3 次（微树 2 次）即取消本窗剩余固定档，
     * nudge 救回通道保住晚到跳过键。校园/工具类 App 校徽启动页与商店
     * 首页不再被反复整树扫描（功耗+风险面双降）。 */
    private final java.util.HashMap<String, Integer> zeroCandScans = new java.util.HashMap<>();
    /* v5.1.0 跳过成功静默期：广告关闭时间戳（复验通过时落账）——2.2s 内
     * 不记「开屏未命中」（首页内容落定期的扫描是噪声，waimaiii 实锤：
     * 复验通过同一秒又对 79 节点首页整树扫描并记了一条未命中）。 */
    private final java.util.HashMap<String, Long> adClosedAt = new java.util.HashMap<>();
    /* v4.4.0 早退后救回通道：被杀链窗口的内容事件补扫节流（晚到跳过键仍能
     * 救回，滚动风暴被 900ms 节流挡住——issue#39-41 实锤 H5 开屏广告按钮
     * 1~2s 才渲染，早退杀链后 nudge 全让路 = 「有时候触发不了」元凶） */
    private final java.util.HashMap<String, Long> rescueAt = new java.util.HashMap<>();
    /* v4.3.0：白名单生效提示日志去重 */
    private final java.util.HashMap<String, Long> wlLogAt = new java.util.HashMap<>();
    /* v7.1.0 FeedAdGuard（自 v6.2.0 已验证代码移植）：信息流广告两段式状态
     * （P1 点披露角标出手 → 8s armed 指纹窗 → P2 点中性项收菜单）。
     * 用户第十一轮原话：能不能增加功能实现自动关闭 app 内的广告内容，
     * 例如微信朋友圈就有广告穿插在朋友圈之中——FeedAdGuard 两段式
     * （宁漏勿误四道闸：角标词精确等值/面积门/P2 中性项双闸/18 款名单门）
     * 全部复用既有 execute() 防线（用户让路/耐心预算/F·H 多点防线/人手手势）。 */
    private final java.util.HashMap<String, Long> feedScanAt = new java.util.HashMap<String, Long>();
    private String feedArmedPkg;
    private long feedArmedAt;
    /* v7.2.0 feed 未命中诊断（扫描日志上传未命中问题）：30s 节流计数 */
    private long feedMissAt;
    private long feedMisses;
    /* v7.4.0 P0 面板直通上下文窗：最近一次「信息流窗在场」时刻（按包记账）——
     * 原因面板是 Dialog/FrameLayout 覆盖窗（isFeedWindow 会拒），P0 直通须
     * 「90s 内进过信息流窗」上下文才允许直查（微信聊天主界面零扫描红线由
     * 覆盖窗门 + 上下文窗双闸保持）。 */
    private final java.util.HashMap<String, Long> lastFeedWinAt = new java.util.HashMap<String, Long>();
    /* ═══ v7.5.0 插屏卫士（InterstitialGuard 薄接线状态）：名单外 App 的
     * 插入广告（用户十四轮实锤「几乎都带有广告两个字」）——角标在场 +
     * 关闭钮（×/关闭/跳过族）语义·形态·近旁闸。2.5s 事件节流 + 未命中
     * 30s 包级诊断节流（扫描日志上传可定位缺哪种关闭词）。 */
    private final java.util.HashMap<String, Long> interScanAt = new java.util.HashMap<String, Long>();
    private final java.util.HashMap<String, Long> interMissAt = new java.util.HashMap<String, Long>();
    private final java.util.HashMap<String, Long> interWinAt = new java.util.HashMap<String, Long>(); // v7.6.0：新窗提速（窗切换重置节流）
    private long interMisses;
    /* ═══ v7.15.11 插屏窗级退避（#106 OPPO 实锤功耗）：静态窗内容事件不停
     * （动画/进度条）→ 插屏扫描 2.5s 一轮对同一面窗无限重扫（fenglink66p
     * ASenVaultyk4 一小时 24+ 轮 walk300 + 双词表直查——纯浪费）。同窗连
     * 续未命中递进退避：streak≤2 照常 2.5s、3~5 次 10s、≥6 次 30s 封顶；
     * 窗切换（winAt 变化）/出手成功即清零——新插层照常秒级响应。 */
    private final java.util.HashMap<String, Integer> interMissStreak = new java.util.HashMap<String, Integer>();
    /* ═══ v7.15.11 图片键边界放行日志去重（#108/#109/#105 三台设备实锤：
     * 同一秒 5~13 条「🎯 图片跳过键边界放行」刷屏——每次扫描每轮 execute
     * 都打一条，用户读日志=「点了十几次」的多点观感，实际出手以 ⚡/🔁 为准）
     * ——同包同理由 6s 一条（首条必记），日志量直降一个量级。 */
    private final java.util.HashMap<String, Long> imgBndLogAt = new java.util.HashMap<String, Long>();
    /* ═══ v7.15.11 派发失败连击退避（#108 azukireader 实锤：手势被覆盖层
     * 拒发后 clickNode false，无冷却记账 → 以扫描节奏 ~2.6 次/秒对同一
     * 候选无限重试 7 秒）。连续 ≥3 次派发失败后按 1.5s 人手耐心节流，
     * 成功即清零——首两次快重试保留（按钮动画中 staleBounds 场景需要）。 */
    private final java.util.HashMap<String, Integer> dispatchFailStreak = new java.util.HashMap<String, Integer>();
    private final java.util.HashMap<String, Long> dispatchFailAt = new java.util.HashMap<String, Long>();
    /* ═══ v7.15.11 横幅让路重排（#109 解锁实锤：验证码/短信 heads-up 横幅
     * 把触点/返回键全挡 → 横幅自动消散后无人再扫 = 解锁开屏广告白看到底）。
     * 出手被横幅挡下时排 3 针重查（+2.5/+5/+9s），横幅已散且窗仍在 →
     * 重排扫描链立即补扫。每包 6s 一轮防针堆积。 */
    private final java.util.HashMap<String, Long> bannerRequeueAt = new java.util.HashMap<String, Long>();
    private final java.util.HashMap<String, Long> bannerClearLogAt = new java.util.HashMap<String, Long>();
    /* ═══ v7.15.11 切换app前台回归探针（HyperOS/Android17 无 STATE_CHANGED
     * 回归实锤——v7.12.0 前台竞速晋升在 v7.15.0 版本线重制时遗失）：
     * STATE_CHANGED 竞速被丢弃时登记 fgPending（3s 窗口）；后续该包
     * CONTENT_CHANGED 以活动窗口归属【阳性证据】复核（getRootInActiveWindow
     * 根节点包名==该包才晋升——查询失败绝不误晋升），晋升=完整进入重置。
     * 非竞速场景兜底探针：包级 5s + 全局 1.5s 双节流（binder 红线：
     * 最坏 ~0.67 次/秒）。 */
    private String fgPendingPkg;
    private String fgPendingCls;
    private long fgPendingAt;
    private final java.util.HashMap<String, Long> fgProbeAt = new java.util.HashMap<String, Long>();
    private long fgProbeGlobalAt;
    /* ═══ v7.15.13 探针阴性快重试（app→app 跳转实锤：切屏动画期首探阴性
     * ——根还属旧应用——后，旧 5s 包级节流在 5s 开屏上等于放弃；阴性后
     * 3s 窗内改 800ms 快档，事件仍在流入即连探）═══ */
    private final java.util.HashMap<String, Long> fgProbeNegAt = new java.util.HashMap<String, Long>();
    /* v7.4.0 空闲巡检：用户停下刷屏盯着广告看时 content-changed 零事件 =
     * 零扫描（日志 #79/#80 实锤：广告静止在屏 2 分钟引擎全程盲）。巡检
     * Runnable 2.5s 一扫，只在信息流窗在场时存活（离窗/切包/masterOff 即停
     * ——功耗红线不破：一扫 = 数词 contains 直查，无 DFS）。 */
    private boolean feedPatrolLive;
    private String feedPatrolPkg;
    private String feedPatrolCls;
    private final Runnable feedPatrol = new Runnable() {
        @Override
        public void run() {
            String p = feedPatrolPkg;
            if (p == null || !masterOn || !bound || !p.equals(lastFgPkg)
                    || !com.zai.adshield.core.FeedAdGuard.isFeedWindow(p, winClassOf(p))) {
                feedPatrolLive = false;
                feedPatrolPkg = null;
                return; // 离窗/切包/暂停 → 巡检自停（绝不空转耗电）
            }
            maybeFeedScan(p, System.currentTimeMillis(), true);
            handler.postDelayed(this, com.zai.adshield.core.FeedAdGuard.FEED_PATROL_MS);
        }
    };
    /* v7.2.0 多点防线 I：同指纹目标击数（每窗最多 2 击，第 3 击起静默——
     * 与 firedTargetAt 时间静默互斥成双保险） */
    private final java.util.HashMap<String, Integer> firedTargetCount = new java.util.HashMap<String, Integer>();

    private volatile String lastAutoClickPkg;
    private volatile long lastAutoClickAt;

    /* ═══ v4.4.0 服务真实绑定状态（「恢复防护跳系统页 10 秒」修复地基）═══
     * onUnbind 保留实例后 instance 仍非 null——但此时服务已解绑收不到任何
     * 事件（僵尸态）。UI/心跳此前把它当「守护中」→ 用户看到假状态，最终
     * 只能去系统设置重开（MIUI 强制看 10 秒）。bound 标记真实绑定态：
     * onServiceConnected=true / onUnbind=false / onDestroy=false。 */
    public static volatile boolean bound = false;

    /** v5.2.0：运行时版本号（缓存；启动横幅不再硬编码——issue#56 实锤
     *  v5.1.1 用户日志横幅仍显示 v4.5.0 文案，历次迭代忘改） */
    private static volatile String cachedVersionName;

    static String versionName(Context ctx) {
        String v = cachedVersionName;
        if (v != null) return v;
        try {
            v = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            v = "5.2.0";
        }
        cachedVersionName = v;
        return v;
    }

    /** v5.2.0：本服务是否仍被系统登记为已启用无障碍服务
     *  （':' 分隔精确解析，RestoreGuard.registrationState——子串误报绝纻） */
    public static boolean registrationPresent(Context ctx) {
        try {
            String s = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            android.content.ComponentName cn =
                    new android.content.ComponentName(ctx, AdSkipService.class);
            return RestoreGuard.registrationState(s, cn.flattenToString(), cn.flattenToShortString())
                    == RestoreGuard.REG_REGISTERED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isAlive() {
        return instance != null && bound;
    }

    /* ═══ v5.6.0 零打扰持久模式（免系统设置自愈 · issue#56 终局）═══
     * 用户三度反馈：部分用户退后台再进/覆盖安装后开守护，每次都被要求
     * 进系统设置重开 + 强看 10 秒警告——登记被 ROM 吊销后公开 API 无法
     * 恢复是事实，但 WRITE_SECURE_SETTINGS（用户 ADB 一次授权）允许
     * 直接改 ENABLED_ACCESSIBILITY_SERVICES 把自己写回登记 → 系统自动
     * 重绑 → 零系统设置 · 零 10 秒警告。
     * 安全边界：权限 signature|development 级（商店 App 拿不到）；只并回
     * 自己的组件（RestoreGuard.mergeRegistrationCsv 幂等去重保序）；未
     * 授权设备恒 false（通知流/体检卡照旧）。 */
    public static boolean canSecureSelfHeal(Context ctx) {
        try {
            return ctx.checkPermission("android.permission.WRITE_SECURE_SETTINGS",
                    android.os.Process.myPid(), android.os.Process.myUid())
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 免设置自愈：把自己并回无障碍登记（幂等）并置总开关，踢 AMS 触发重绑。
     *  @return true=已写回登记（调用方继续走重绑轮询）；false=未授权/写失败
     *  （调用方回退保守通知流——绝不该在未授权时静默吞掉恢复引导）。 */
    public static boolean secureSelfHeal(Context ctx) {
        try {
            if (!canSecureSelfHeal(ctx)) return false;
            android.content.ComponentName cn = new android.content.ComponentName(ctx, AdSkipService.class);
            String flat = cn.flattenToString();
            String current = Settings.Secure.getString(
                    ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String merged = RestoreGuard.mergeRegistrationCsv(current, flat);
            if (merged == null) return false;                       // 入参异常 → 保守回退
            if (merged.equals(current)) {                           // 已在登记（幂等）：无需写
                return RestoreGuard.registrationState(current, flat, cn.flattenToShortString())
                        == RestoreGuard.REG_REGISTERED;
            }
            Settings.Secure.putString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, merged);
            // 总开关：少数 ROM 吊销时会把 ACCESSIBILITY_ENABLED 置 0，必须一并扶正
            try {
                String on = Settings.Secure.getString(
                        ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
                if (!"1".equals(on)) {
                    Settings.Secure.putString(ctx.getContentResolver(),
                            Settings.Secure.ACCESSIBILITY_ENABLED, "1");
                }
            } catch (Throwable ignored) {
            }
            AdSkipService s = instance;
            if (s != null) {
                s.appendLog("🛡️ 持久模式自愈：无障碍登记已写回（免系统设置/免 10 秒警告），等待系统重绑…");
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private int screenWidth;
    private int screenHeight;
    /* v6.0.0 1:1 人手模拟：设备像素密度（dp 自适应触点安全域——安全域常量
     * 从固定 px 迁 dp：160dpi 与 480dpi 行为归一，「分辨率原因」的触点
     * 漂移根治；onServiceConnected 时从 DisplayMetrics 缓存，热路径零 IPC） */
    private volatile float screenDensity = 1f;

    /* ═══ v7.15.12 更新存活链（方法面）═══ */

    /** v7.15.12：安装器拉起前调用（Downloader.install）——状态同步落盘 +
     * 预排更新后自检闹钟。commit() 同步写：apply() 的异步落盘会被「安装
     * 替换杀进程」截断（用户实锤：更新装完回来日志断流）；+25s 闹钟跨
     * APK 替换存活，ROM 吞/延迟 MY_PACKAGE_REPLACED 广播时兜底触发
     * KeepAliveReceiver 自检 → SmartRevive 踢链 → +30s 梯子。 */
    public static void preserveStateForUpdate(Context ctx) {
        AdSkipService s = instance;
        if (s != null) {
            try {
                s.flushSync();
            } catch (Throwable ignored) {
            }
        }
        try {
            scheduleNextHeartbeat(ctx, System.currentTimeMillis() + 25_000L);
        } catch (Throwable ignored) {
        }
    }

    /** v7.15.12：服务已死时的日志通道（prefs 直写 commit——重连后可见；
     * 实例活着则路由实例路径防内存态覆盖竞态，仅在 instance == null 时
     * 直写。覆盖安装完成/断线检出等关键时刻不再日志黑洞）。 */
    static void appendLogStatic(Context ctx, String msg) {
        AdSkipService s = instance;
        if (s != null) {
            s.appendLog(msg);
            return;
        }
        try {
            android.content.SharedPreferences sp = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
            String raw = sp.getString(KEY_STATE, null);
            JSONObject st = raw != null ? new JSONObject(raw) : new JSONObject();
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
            sp.edit().putString(KEY_STATE, st.toString()).commit();
        } catch (Throwable ignored) {
        }
    }

    /** v7.15.12：断线时戳记账（静态——接收器/心跳/服务三侧共用；只记
     * 首次检出，重连清零；进程重启间走 prefs 持久——覆盖安装杀进程后
     * 新进程的接收器仍能续上首检时戳）。 */
    static void markDeadSince(Context ctx) {
        try {
            if (deadSinceMs == 0L) {
                long saved = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_DEAD_SINCE, 0L);
                if (saved > 0L) {
                    deadSinceMs = saved;
                    return;
                }
                deadSinceMs = System.currentTimeMillis();
                ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putLong(KEY_DEAD_SINCE, deadSinceMs).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /** v7.15.12：同步落盘（commit）——安装替换杀进程前的最后一搏
     *（onDestroy/安装前专用；热路径仍走 15s 延迟 apply 零卡顿）。 */
    private void flushSync() {
        synchronized (lock) {
            flushScheduled = false;
        }
        org.json.JSONObject st = stateInMemory;
        if (st == null) return;
        try {
            prefs().edit().putString(KEY_STATE, st.toString()).commit();
        } catch (Throwable ignored) {
        }
    }

    /** v7.15.12：离线时长人性化（重连日志用——「日志为什么断流」直接可读） */
    static String downTimeZh(long ms) {
        if (ms < 0) ms = 0;
        long sec = ms / 1000L;
        if (sec < 60) return sec + " 秒";
        long min = sec / 60L;
        if (min < 60) return min + " 分 " + (sec % 60L) + " 秒";
        return (min / 60L) + " 时 " + (min % 60L) + " 分";
    }
    /* v6.0.0 触点轨迹：同窗已出手触点（同一坐标 ±8dp 内绝不重复盲刺——
     * 「多点」根治；窗口切换清空与 clickTries 同步）。key=pkg，
     * value=本窗已出手触点列表） */
    private final java.util.HashMap<String, java.util.ArrayList<int[]>> tapTrail = new java.util.HashMap<>();

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

    /* ═══ v4.2.0 自动提交引擎日志（KeepAliveReceiver 心跳顺带）═══
     * v6.1.0：12h→8h（用户实锤：一天自动提交 3 次，24 小时均匀 → 一日
     * 三次）；成功才计时；服务未运行/暂停/息屏时跳过（等下一轮心跳，
     * 绝不夜间耗电）。与手动上传同一内置令牌通道（GitHub issue），
     * 标题带「自动」标识。 */
    public static void maybeAutoUploadLog(final Context ctx) {
        try {
            final SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long last = sp.getLong("autoLogAt", 0);
            long now = System.currentTimeMillis();
            if (now - last < AUTO_LOG_MS) return;
            final AdSkipService s = instance;
            if (s == null || !masterOn) return;       // 服务活着且在守护才传
            if (!isScreenOn(ctx)) return;              // 息屏不传（夜间功耗红线）
            Thread t = new Thread(() -> {
                try {
                    String state = snapshotState(ctx);
                    org.json.JSONObject st = state != null
                            ? new org.json.JSONObject(state) : new org.json.JSONObject();
                    String ver = "4.3.0";
                    try {
                        ver = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
                    } catch (Throwable ignored) {
                    }
                    String body = buildLogBody(ctx, st, ver, true);
                    String title = "[引擎日志][v" + ver + "] "
                            + android.os.Build.BRAND + " " + android.os.Build.MODEL + "（8 小时自动 · 一日 3 次）";
                    String r = Net.postIssue(title, body);
                    org.json.JSONObject o = new org.json.JSONObject(r);
                    if (o.optBoolean("ok")) {
                        sp.edit().putLong("autoLogAt", System.currentTimeMillis()).apply();
                        AdSkipService sv = instance;
                        if (sv != null) {
                            sv.appendLog("📨 引擎日志已自动提交（每 8 小时一次 · 一日 3 次均匀覆盖全天；手动上传不受影响）");
                        }
                    }
                } catch (Throwable ignored) {
                }
            }, "auto-log");
            t.setDaemon(true);
            t.start();
        } catch (Throwable ignored) {
        }
    }

    /** v4.2.0：日志正文构造（自动/手动共用：状态 + 引擎日志 40 条带时间戳 +
     *  Top5 应用战绩 + 订阅命中 + 能耗画像 + v6.1.0 重点事件留存段）
     *  v6.1.0（用户实锤：日志要更详细、重点类型留存）：
     *   · 条数 16→40（留存 300 条窗口里带时间戳的尾部更长截面）；
     *   · 重点类型统计行：本开机周期命中/误触防护/自愈/订阅拉取四类——
     *     低频重点事件不再被高频命中日志淹没。 */
    static String buildLogBody(Context ctx, org.json.JSONObject st, String ver, boolean auto) {
        try {
            StringBuilder b = new StringBuilder(1024);
            /* v7.0.0 设备画像行（多点/误点归因需要设备类型/分辨率/密度——
             * 用户实锤：部分设备因设备类型、分辨率或其他原因导致多点误点） */
            b.append("## 设备\n- ").append(android.os.Build.BRAND).append(' ')
                    .append(android.os.Build.MODEL).append(" · Android ")
                    .append(android.os.Build.VERSION.RELEASE).append(" · SDK ")
                    .append(android.os.Build.VERSION.SDK_INT).append('\n');
            try {
                android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                b.append("- 屏幕：").append(dm.widthPixels).append('×').append(dm.heightPixels)
                        .append(" · 密度 ").append(Math.round(dm.density * 100) / 100.0).append('×')
                        .append(" · ").append(dm.widthPixels / 360).append("dp 宽\n");
            } catch (Throwable ignored) {
            }
            b.append("\n## 状态\n");
            org.json.JSONObject stats = st.optJSONObject("stats");
            int auto_ = stats == null ? 0 : stats.optInt("auto", 0);
            int popup = stats == null ? 0 : stats.optInt("popup", 0);
            int manual = stats == null ? 0 : stats.optInt("manual", 0);
            int backs = stats == null ? 0 : stats.optInt("back", 0);
            int feedback = stats == null ? 0 : stats.optInt("feedback", 0);
            b.append("- 版本：v").append(ver).append(" · 环境：Android APK")
                    .append(auto ? " · 8h 自动提交（一日 3 次）" : " · 手动上传").append("\n");
            b.append("- 真实引擎：秒跳 ").append(auto_).append(" · 弹窗关闭 ").append(popup)
                    .append(" · 返回键 ").append(backs).append(" · 反馈面板收起 ").append(feedback)
                    .append(" · 手动样本 ").append(manual).append("\n");
            // 学习规则统计
            org.json.JSONObject rules = st.optJSONObject("rules");
            int ruleCount = 0, pkgs = 0;
            if (rules != null) {
                java.util.Iterator<String> it = rules.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    org.json.JSONArray l = rules.optJSONArray(k);
                    if (l != null && l.length() > 0) {
                        ruleCount += l.length();
                        pkgs++;
                    }
                }
            }
            b.append("- 学习规则：").append(ruleCount).append(" 条（").append(pkgs).append(" 个应用）\n");
            // Top5 应用战绩
            org.json.JSONObject byApp = stats == null ? null : stats.optJSONObject("byApp");
            if (byApp != null && byApp.length() > 0) {
                b.append("- 应用战绩 Top5：");
                java.util.ArrayList<String> keys = new java.util.ArrayList<>();
                java.util.Iterator<String> it = byApp.keys();
                while (it.hasNext()) keys.add(it.next());
                keys.sort((x, y) -> {
                    int ax = scoreOf(byApp.optJSONObject(x));
                    int ay = scoreOf(byApp.optJSONObject(y));
                    return ay - ax;
                });
                int n = Math.min(5, keys.size());
                for (int i = 0; i < n; i++) {
                    org.json.JSONObject a = byApp.optJSONObject(keys.get(i));
                    if (a == null) continue;
                    b.append(shortPkgSt(keys.get(i))).append('(')
                            .append(a.optInt("auto", 0)).append('/')
                            .append(a.optInt("popup", 0)).append('/')
                            .append(a.optInt("manual", 0)).append(')');
                    if (i < n - 1) b.append(" · ");
                }
                b.append("\n");
            }
            // 订阅命中
            try {
                if (ctx != null) {
                String subJson = SubscriptionManager.stateJson(ctx);
                org.json.JSONObject sub = new org.json.JSONObject(subJson);
                int bh = sub.optInt("builtinHits", 0);
                b.append("- 订阅：内置 ").append(sub.optInt("builtinApps", 0)).append(" 款/")
                        .append(sub.optInt("builtinRules", 0)).append(" 条（出手 ").append(bh)
                        .append(bh == 0 ? " · 文案引擎优先出手，订阅只补 vid 盲区" : "）");
                if (sub.optBoolean("remoteOk", false)) {
                    b.append(" · 远程「").append(sub.optString("remoteName", "")).append("」")
                            .append(sub.optInt("remoteApps", 0)).append(" 款/")
                            .append(sub.optInt("remoteRules", 0)).append(" 条（命中 ")
                            .append(sub.optInt("remoteHits", 0)).append("）");
                }
                b.append("\n");
                }
            } catch (Throwable ignored) {
            }
            // 能耗画像（v5.3.0 新增探针/省电档案维度：探针成本≈扫描的 1/14）
            AdSkipService sv = instance;
            if (sv != null) {
                b.append("- 能耗画像（本次开机）：事件 ").append(sv.energyEvents)
                        .append(" · 探针 ").append(sv.energyProbes)
                        .append(" · 扫描 ").append(sv.energyScans)
                        .append("（省电档案跳过 ").append(sv.energyProfileSkips).append(" 窗）")
                        .append(" · 手势 ").append(sv.energyGestures)
                        .append(" · 返回键 ").append(sv.energyBacks)
                        .append(" · 估算 ").append(String.format(java.util.Locale.US, "%.1f",
                                PowerGovernor.microWh(sv.energyEvents, sv.energyProbes, sv.energyScans, sv.energyBacks)))
                        .append("µWh\n");
                b.append("- 位置学习：验证通过 ").append(sv.posLearnVerified)
                        .append(" · 拦截误学 ").append(sv.posLearnDropped)
                        .append("（v5.3.0：点击后 1.5s 窗口未关 = 普通点击，不当跳过键学习）\n");
            }
            // v6.1.0 重点事件留存段：低频重点事件不被高频命中日志淹没——
            // 从 300 条留存窗口里反查四类重点事件（误触防护/自愈/订阅/异常），
            // 独立统计行 + 最近 6 条重点事件原文
            try {
                org.json.JSONArray allLog = st.optJSONArray("log");
                int dupGuard = 0, healEvt = 0, subEvt = 0, warnEvt = 0;
                java.util.ArrayList<String> keyRecent = new java.util.ArrayList<>();
                if (allLog != null) {
                    for (int i = Math.max(0, allLog.length() - 300); i < allLog.length(); i++) {
                        org.json.JSONObject e = allLog.optJSONObject(i);
                        if (e == null) continue;
                        String m = e.optString("msg", "");
                        boolean isKey = false;
                        if (m.contains("防重复") || m.contains("同指纹") || m.contains("拒发")
                                || m.contains("覆盖层") || m.contains("位移中")) { dupGuard++; isKey = true; }
                        else if (m.contains("自愈") || m.contains("重绑") || m.contains("拉活")) { healEvt++; isKey = true; }
                        else if (m.contains("订阅") && (m.contains("失败") || m.contains("拉取") || m.contains("镜像"))) { subEvt++; isKey = true; }
                        else if (m.contains("⚠") || m.contains("⛔") || m.contains("梯子") || m.contains("中断")) { warnEvt++; isKey = true; }
                        if (isKey && keyRecent.size() < 6) keyRecent.add(m);
                    }
                }
                b.append("- 重点事件（本留存窗）：误触防护 ").append(dupGuard)
                        .append(" · 自愈/拉活 ").append(healEvt)
                        .append(" · 订阅异常 ").append(subEvt)
                        .append(" · 引擎预警 ").append(warnEvt).append("\n");
                if (!keyRecent.isEmpty()) {
                    b.append("- 重点事件最近 6 条：\n");
                    for (int i = 0; i < keyRecent.size(); i++) {
                        b.append("  - ").append(keyRecent.get(i)).append("\n");
                    }
                }
            } catch (Throwable ignored) {
            }
            // 引擎日志 40 条（v6.1.0：16→40，带时间戳——留存更长截面）
            org.json.JSONArray log = st.optJSONArray("log");
            b.append("- 引擎日志（最近 40 条）：\n");
            if (log != null && log.length() > 0) {
                int from = Math.max(0, log.length() - 40);
                java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
                for (int i = from; i < log.length(); i++) {
                    org.json.JSONObject e = log.optJSONObject(i);
                    if (e == null) continue;
                    long t = e.optLong("t", 0);
                    b.append("  - [").append(df.format(new java.util.Date(t))).append("] ")
                            .append(e.optString("msg", "")).append("\n");
                }
            } else {
                b.append("  - （暂无）\n");
            }
            return b.toString();
        } catch (Throwable t) {
            return "（日志构造异常：" + t + "）";
        }
    }

    static int scoreOf(org.json.JSONObject a) {
        if (a == null) return 0;
        return a.optInt("auto", 0) + a.optInt("popup", 0) + a.optInt("manual", 0);
    }

    /* ═══ v4.1.0 App 内主开关（暂停/恢复，免去看系统设置 10 秒警告）═══
     * 暂停期间服务保持存活（保活四件套不受影响），但绝不扫描、绝不
     * 点击、绝不学习、绝不突发检测；恢复后立即无缝衔接。 */
    public static void setMaster(Context ctx, boolean on) {
        masterOn = on;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MASTER, on).apply();
        AdSkipService s = instance;
        if (s != null) {
            s.appendLog(on ? "▶ 防护已恢复（App 内一键）"
                    : "⏸ 防护已暂停（App 内一键）：服务保持存活，绝不扫描绝不点击");
        }
        refreshKeepNotification(ctx); // v4.5.0：通知栏文案/按钮随开关联动
    }

    public static boolean masterState(Context ctx) {
        AdSkipService s = instance;
        if (s != null) return masterOn; // 服务活着：内存态即时
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MASTER, true);
    }

    /* ═══ v4.5.0 状态竞态根治（「开启弹窗广告屏蔽之后又被 App 自动关闭」）═══
     * 根因：UI 开关走 kvSet("state") 只写 SharedPreferences，而服务持有
     * stateInMemory 旧副本——任何战绩/日志更新触发 flushNow() 都会用旧内存
     * 态【整体覆盖】UI 写入（15s 延迟落盘 = 开关几秒到几分钟内「自动弹回」）。
     * 对策：开关类设置一律走本方法——服务活着就改【服务内存本体】（唯一
     * 数据源，落盘自然带新值）；服务死了直接读写 prefs。UI 侧 kvSet 绝不
     * 再碰 state 开关字段。 */
    public static void setStateBool(Context ctx, String key, boolean value) {
        if (!"popupShield".equals(key) && !"learning".equals(key)) return; // 白名单
        AdSkipService s = instance;
        if (s != null) {
            synchronized (s.lock) {
                try {
                    JSONObject st = s.loadState();
                    st.put(key, value);
                    s.saveState(st);
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY_STATE, null);
            JSONObject st = raw != null ? new JSONObject(raw) : new JSONObject();
            st.put(key, value);
            sp.edit().putString(KEY_STATE, st.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /* ───────── v4.3.0 应用白名单管理（页面 Bridge / 服务门共用，静态） ───────── */

    /** 白名单快照重载（服务连接时 / 增删后调用） */
    public static void reloadWhitelist(Context ctx) {
        try {
            java.util.HashSet<String> s = parseWhitelistCsv(
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WHITELIST, ""));
            whitelistSet = s;
        } catch (Throwable t) {
            whitelistSet = new java.util.HashSet<>();
        }
    }

    static java.util.HashSet<String> parseWhitelistCsv(String csv) {
        java.util.HashSet<String> s = new java.util.HashSet<>();
        if (csv != null) {
            for (String p : csv.split(",")) {
                p = p.trim();
                if (p.length() > 0) s.add(p);
            }
        }
        return s;
    }

    /** 白名单内应用：事件入口直接丢弃（与桌面黑名单同级——零扫描零学习零突发） */
    public static boolean isWhitelisted(String pkg) {
        return pkg != null && whitelistSet.contains(pkg);
    }

    public static boolean whitelistAdd(Context ctx, String pkg) {
        if (pkg == null) return false;
        pkg = pkg.trim();
        if (pkg.length() < 3 || pkg.contains(",") || pkg.contains(" ")) return false;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        java.util.HashSet<String> s = parseWhitelistCsv(sp.getString(KEY_WHITELIST, ""));
        boolean added = s.add(pkg);
        sp.edit().putString(KEY_WHITELIST, joinCsv(s)).apply();
        whitelistSet = s;
        AdSkipService sv = instance;
        if (sv != null && added) {
            sv.appendLog("🤍 已加入白名单（" + sv.shortPkg(pkg) + " " + pkg + "）：该应用零扫描零点击，卫士完全让路");
        }
        return added;
    }

    public static boolean whitelistRemove(Context ctx, String pkg) {
        if (pkg == null) return false;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        java.util.HashSet<String> s = parseWhitelistCsv(sp.getString(KEY_WHITELIST, ""));
        boolean removed = s.remove(pkg.trim());
        sp.edit().putString(KEY_WHITELIST, joinCsv(s)).apply();
        whitelistSet = s;
        AdSkipService sv = instance;
        if (sv != null && removed) {
            sv.appendLog("✅ 已移出白名单（" + sv.shortPkg(pkg) + "）：恢复守护");
        }
        return removed;
    }

    /** 白名单列表（csv——页面展示用） */
    public static String whitelistCsvOf(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WHITELIST, "");
    }

    static String joinCsv(java.util.HashSet<String> s) {
        StringBuilder b = new StringBuilder();
        for (String p : s) {
            if (b.length() > 0) b.append(',');
            b.append(p);
        }
        return b.toString();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        bound = true; // v4.4.0：真实绑定态
        /* v7.15.12 断线时长可见化：重连即回答「日志为什么断流/广告为什么
         * 没跳」——离线时段零扫描零日志（deadSince 由 onUnbind/onDestroy/
         * 接收器首次检出时记入；进程重启间 prefs 持久）。 */
        try {
            long deadAt = deadSinceMs;
            if (deadAt == 0L) {
                deadAt = prefs().getLong(KEY_DEAD_SINCE, 0L);
            }
            if (deadAt > 0L) {
                long offMs = System.currentTimeMillis() - deadAt;
                if (offMs > 20_000L) { // <20s 的解绑重连不刷屏
                    appendLog("🔌 服务重连成功（此前离线 " + downTimeZh(offMs)
                            + "——离线期零扫描零日志，多由覆盖安装/清理后台/厂商省电管控引发；"
                            + "若长时间未自动恢复，检查通知栏「待恢复」提醒）");
                }
                deadSinceMs = 0L;
                prefs().edit().remove(KEY_DEAD_SINCE).apply();
            }
        } catch (Throwable ignored) {
        }
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        try {
            screenDensity = getResources().getDisplayMetrics().density;
        } catch (Throwable t) {
            screenDensity = 1f;
        }
        // v4.4.0 息屏缓存：SCREEN_ON/OFF 广播维护 volatile 标记，替代热路径上
        // 每事件一次的 PowerManager.isInteractive binder IPC（issue#40 能耗画像
        // 实锤 4602 事件 = 4602 次 IPC；广播零开销，屏幕翻转秒级同步）
        registerScreenReceiver();
        // v3.5.0 保活：服务连上即记「用户要求保持开启」；仅用户在 App 内手动
        // 关闭保活提醒（Bridge.setKeepOn(false)）才置 false
        prefs().edit().putBoolean(KEY_KEEPON, true).apply();
        startKeepAliveForeground();
        cancelAlertNotification();
        /* v7.15.2：服务存活期维护心跳——旧设计 cancelHeartbeat() 把心跳
         * 全撤，自动上传/订阅静默更新/每日自动导入三个维护任务（只挂在
         * KeepAliveReceiver 自检分支里）在服务健康时永无触发源（issue
         * #89 后再无自动日志实锤：手机服务常驻 → 链死；MiTV 因 ROM 反复
         * 解绑反而活了）。改为 30 分钟窗口式非精确闹钟：Doze 里批量调度
         * 零额外唤醒，亮屏时排点即校验；同一 PendingIntent（requestCode 8）
         * 天然去重覆盖，不叠加闹钟。 */
        scheduleNextHeartbeat(this, System.currentTimeMillis() + ALIVE_HEARTBEAT_MS, true);
        // v3.6.0：清理 v3.5.0 误学毒指纹（MainActivity/FrameLayout/Dialog 被
        // 学成「广告窗指纹」——用户二次进入被连按返回键退出的根因）
        int purged = purgePoisonFingerprints();
        // v4.4.0：合并为下方一条完整启动日志（此前两条重复刷屏）
        // v3.9.0：清理毒位置规则（issue#12 实锤：桌面 com.miui.home 学出位置
        //(39%,18%)——桌面点击被误学成「跳过键」，下次启动直点桌面图标=乱点）
        int purgedPos = purgeLauncherPosRules();
        if (purgedPos > 0) appendLog("已清理 " + purgedPos + " 条桌面误学位置规则（issue#12 闪退整治）");
        // v3.13.0：清理 OEM 工具包/短信包误学规则（issue#16 实锤：securitycenter
        // 应用详情页被学出位置 (91%,9%)——下次进详情页 500ms 直点=在权限管理页乱点）
        int purgedOem = purgeOemPosRules();
        if (purgedOem > 0) appendLog("已清理 " + purgedOem + " 条系统工具/短信包误学规则（issue#16 整治，安全中心/短信里的手动操作绝不当「跳过键」）");
        // v4.1.0：App 内主开关状态载入（暂停后重启服务/手机仍保持暂停）
        masterOn = prefs().getBoolean(KEY_MASTER, true);
        // v4.3.0：应用白名单快照载入
        reloadWhitelist(this);
        int wlN = whitelistSet.size();
        /* v5.2.0：启动横幅动态版本号（RestoreGuard.startBanner）——
         * 此前硬编码 v4.5.0 文案，v5.x 每次迭代都没改，issue#56 日志实锤 */
        appendLog(RestoreGuard.startBanner(versionName(this), RuleEngine.stats(),
                purged, purgedPos, purgedOem, wlN));
        // v5.2.0：无障碍登记移除遥测（issue#56 根因链证据）——HyperOS
        // 清理后台/覆盖安装吊销登记的瞬间打点，未来日志能直接回答
        // 「服务为什么被关」「什么时候被关」
        registerA11yRegObserver();
        // v4.0.0 GKD 订阅融合：内置订阅 + 远程订阅（用户导入的 GKD 社区规则）
        SubscriptionManager.ensureLoaded(this);
        // v7.15.0 id667 每日后台自动导入（无订阅时自动设默认源——含八通道
        // 镜像 + 离线快照兜底；已有源尊重既有选择零动作）
        SubscriptionManager.maybeAutoImport(this);
        Subscription bi = SubscriptionManager.builtinSub;
        appendLog("🧩 订阅引擎就绪：内置订阅 "
                + (bi == null ? 0 : bi.apps.size()) + " 款应用/"
                + (bi == null ? 0 : bi.ruleCount()) + " 条 GKD 规则"
                + (SubscriptionManager.remoteSub != null
                ? "｜远程订阅已加载（" + SubscriptionManager.remoteSub.name + "）" : ""));
        // v5.3.0 宿主窗档案加载（重启后省电态不清零——CLEAN 窗继续零扫描）
        profileLoad();
        /* ═══ v5.5.0 模块二：学习规则治理清扫 + 三源合并摘要 + 分级扫描 ═══
         * LearnGovernor 生命周期管理：新生观察→活跃→衰退→退休；退休规则
         * （噪声/失效）启动即清；RuleMerge 三源优先级摘要（official >
         * gkd > learned）；SmartScanPlanner 分级预算（性能 ≤10% 红线）。 */
        int retired = learnGovernorSweep();
        if (retired > 0) {
            appendLog("🧹 学习规则治理：退休 " + retired + " 条（新生期未复现/质量分过低/长期失效——噪声不过夜）");
        }
        appendLog("📐 " + com.zai.adshield.core.SmartScanPlanner.stats());
        appendLog(com.zai.adshield.core.RuleMerge.threeSourceSummary(
                bi == null ? 0 : bi.ruleCount(),
                SubscriptionManager.remoteSub == null ? 0 : SubscriptionManager.remoteSub.ruleCount(),
                learnedRuleCount()));
        /* v7.1.0：订阅两源使用顺序治理 + 信息流广告卫士上线（启动日志可见） */
        appendLog(com.zai.adshield.core.RuleRouter.summary());
        appendLog("🛡 " + com.zai.adshield.core.FeedAdGuard.stats());
    }

    /** v5.5.0：学习规则总数（state.rules 全包名累计） */
    private int learnedRuleCount() {
        try {
            JSONObject rules = loadState().optJSONObject("rules");
            if (rules == null) return 0;
            int n = 0;
            JSONArray pkgs = rules.names();
            if (pkgs == null) return 0;
            for (int i = 0; i < pkgs.length(); i++) {
                JSONArray list = rules.optJSONArray(pkgs.optString(i));
                if (list != null) n += list.length();
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    /* ═══ v5.5.0 LearnGovernor 生命周期清扫 ═══
     * 遍历全部学习规则（state.rules）：
     *  · 退休（RETIRED）：新生期 72h 未复现 / 质量分 < 0.35 / 长期失效 →
     *    直接删除（噪声不过夜——v5.1.0 实证「一次误学」是最大噪声源）；
     *  · 衰退（DECAYING）：30 天未命中但历史 ≥3 次 → 标记 dec=true，
     *    不再预拦截（仅候选层高门槛兜底）；
     *  · 泛化（gen）计数：SDK 骨架规则跨应用可复用（学习效率量化）。
     * 返回退休清理条数。 */
    private int learnGovernorSweep() {
        int retired = 0;
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) return 0;
                long now = System.currentTimeMillis();
                JSONArray pkgs = rules.names();
                if (pkgs == null) return 0;
                boolean dirty = false;
                int genCount = 0;
                for (int p = 0; p < pkgs.length(); p++) {
                    String pkg = pkgs.optString(p);
                    JSONArray list = rules.optJSONArray(pkg);
                    if (list == null) continue;
                    JSONArray kept = new JSONArray();
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject r = list.optJSONObject(i);
                        if (r == null) continue;
                        boolean gen = r.optBoolean("gen", false);
                        if (gen) genCount++;
                        /* LearnGovernor.Record 投影：hitCount=auto+manual,
                         * failCount=miss, firstAt=createdAt（无则取 ts），
                         * lastAt=ts */
                        int hits = r.optInt("auto", 0) + r.optInt("manual", 0);
                        com.zai.adshield.core.LearnGovernor.Record rec =
                                new com.zai.adshield.core.LearnGovernor.Record(
                                        pkg, r.optString("cls", ""), hits, r.optInt("miss", 0),
                                        r.has("createdAt") ? r.optLong("createdAt") : r.optLong("ts", now),
                                        r.optLong("ts", now));
                        rec.generalized = gen;
                        int stage = com.zai.adshield.core.LearnGovernor.stage(rec, now);
                        if (stage == com.zai.adshield.core.LearnGovernor.STAGE_RETIRED) {
                            retired++;
                            dirty = true;
                            continue; // 退休 → 删除
                        }
                        if (stage == com.zai.adshield.core.LearnGovernor.STAGE_DECAYING
                                && !r.optBoolean("dec", false)) {
                            r.put("dec", true);
                            dirty = true;
                        }
                        kept.put(r);
                    }
                    if (kept.length() == 0) rules.remove(pkg);
                    else rules.put(pkg, kept);
                }
                if (dirty) saveState(st);
                if (genCount > 0) {
                    appendLog("✨ SDK 泛化学习规则 " + genCount + " 条（跨应用可复用——A 应用学会的 SDK 骨架，B 应用同 SDK 广告直接预拦截）");
                }
            } catch (Exception ignored) {
            }
        }
        return retired;
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
        bound = false; // v4.4.0：解绑即僵尸态（实例保留但收不到事件）
        markDeadSince(this); // v7.15.12：断线首检时戳（重连日志可回答「日志为什么断流」）
        // 系统解绑但非用户主动关闭：保留服务等待 AMS 重绑，并排自检 + 心跳
        //（v7.15.14：去 keepOn 闸——闹钟链永不中断是自愈链的地基；
        //  keepOn 只管通知打扰，不再连坐自愈踢链）
        try {
            appendLog("🔌 服务被系统解绑（非手动关闭）→ 保留实例等待重绑，已排自检与心跳");
            scheduleSelfCheckAlarm(3000);
            scheduleHeartbeat();
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

    /* ═══ v7.15.14 死透快档守望（用户实锤：「卫士挂后台长时间后丢失功能，
     * 要重新退出进一下；没开启自启动状态下」+ #116 日志考古：16:36→
     * 21:58「服务已启动」断流 3 小时 45 分且「自愈/拉活 0」）═══
     * 死因：服务死透后的下一跳闹钟是 setWindow 窗口式——App Standby
     * 深桶/厂商省电管控下被推迟数小时，KeepAliveReceiver 迟迟不触发，
     * 踢链自愈无从谈起。修复：死透+亮屏场景改 setAndAllowWhileIdle
     *（doze 中也准点触发，不受窗口式批量调度约束）；亮屏时设备本就
     * 醒着，2 分钟一档进程拉起+一次 binder 查询开销可忽略；服务一旦
     * 踢活即回 30 分钟存活节奏。息屏维持 30 分钟窗口式（夜间无广告
     * 场景不耗电，v3.13.0 待机功耗治理不变）。 */
    public static final long DEAD_WATCH_MS = 2 * 60 * 1000L;

    /** v7.15.14：死透快档闹钟（与心跳同 requestCode 8——同一 PendingIntent
     *  天然去重覆盖不叠加）。allowWhileIdle 型不受 doze 批量调度推迟；
     *  doze 内每 app 每 9 分钟一次限额兑底（仍远优于窗口式的数小时级）。 */
    static void scheduleDeadWatch(Context ctx, long atMs) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent it = new Intent(ctx, KeepAliveReceiver.class);
            it.setAction(KeepAliveReceiver.ACTION_SELF_CHECK);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    ctx, 8, it,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.setAndAllowWhileIdle(android.app.AlarmManager.RTC, atMs, pi);
        } catch (Throwable t) {
            // 兑底：窗口式也优于不排（部分 ROM 未实现 allowWhileIdle 型）
            scheduleNextHeartbeat(ctx, atMs, false);
        }
    }

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
                // v4.3.0 功耗：亮屏心跳改窗口式（5~8min 系统批量调度，不再精确
                // 闹钟独占唤醒——发现延迟最多 +3min，服务被杀发现仍足够快；
                // 亮屏时设备本就醒着，窗口式不影响时效体感）
                try {
                    am.setWindow(android.app.AlarmManager.RTC, atMs, 3 * 60 * 1000L, pi);
                } catch (Throwable t) {
                    am.set(android.app.AlarmManager.RTC, atMs, pi);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void scheduleHeartbeat() {
        scheduleNextHeartbeat(this, System.currentTimeMillis() + HEARTBEAT_MS, true);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // v3.6.0：后台清理（滑掉最近任务）时，部分 ROM 会连无障碍服务一起杀。
        // ① 若进程幸存：4s 后自检，服务真死了才发「待恢复」提醒（防误报）
        // ② 自检闹钟（15s）：进程被杀但非 force-stop 的 ROM 上仍可触发提醒
        // v3.7.0：追加 15 分钟心跳闹钟——进程被杀后持续自检，服务一旦真死
        //    即提醒恢复
        //（v7.15.14：去 keepOn 闸——闹钟链永不中断；通知打扰由 notifyAlert
        //  内置 keepOn 闸收敛，不再连坐自愈踢链）
        try {
            handler.postDelayed(() -> {
                // v5.2.0：两态通知——按登记状态决定「自动恢复」还是「直达开关」
                if (instance == null) KeepAliveReceiver.notifyAlert(AdSkipService.this,
                        AdSkipService.registrationPresent(AdSkipService.this));
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
        bound = false; // v4.4.0：销毁即非存活
        markDeadSince(this); // v7.15.12：断线首检时戳（重连日志可回答「日志为什么断流」）
        handler.removeCallbacks(feedPatrol); // v7.4.0：巡检链随服务销毁拆除
        feedPatrolLive = false;
        feedPatrolPkg = null;
        try {
            if (screenRx != null) unregisterReceiver(screenRx);
        } catch (Throwable ignored) {
        }
        try {
            if (a11yRegObserver != null) {
                getContentResolver().unregisterContentObserver(a11yRegObserver);
                a11yRegObserver = null;
            }
        } catch (Throwable ignored) {
        }
        flushSync(); // v7.15.12：apply → commit 同步写（安装替换杀进程防截断——最后日志不再丢）
        instance = null;
        super.onDestroy();
    }

    /* ═══ v5.2.0 无障碍登记移除遥测（issue#56：回答「服务为什么被关」）═══
     * 监听 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 变化：本服务
     * 登记被系统移除的瞬间打点日志 + 排 2.5s 自检（自动拉活或提醒）。
     * 静态内部类（规避 d8 8.2 匿名类 NPE bug，同 ScreenStateRx）。 */
    private static final class A11yRegObserver extends android.database.ContentObserver {
        private final AdSkipService svc;

        A11yRegObserver(AdSkipService svc, Handler h) {
            super(h);
            this.svc = svc;
        }

        @Override
        public void onChange(boolean selfChange) {
            try {
                boolean now = registrationPresent(svc);
                boolean before = svc.lastRegState;
                svc.lastRegState = now;
                if (RestoreGuard.analyzeRegChange(before, now) == RestoreGuard.CHANGE_REVOKED) {
                    svc.appendLog("⚠️ 无障碍登记被移除（清理后台/覆盖安装/厂商省电管控或手动关闭所致）——若非本人关闭，重开需经系统 10 秒安全确认（厂商防诈设计）；建议完成首页「保活体检」防复发");
                    // 吊销后 onUnbind/心跳链可能不可靠：立即排自检走两态通知
                    svc.scheduleSelfCheckAlarm(2500);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private android.database.ContentObserver a11yRegObserver;
    private volatile boolean lastRegState = true;

    private void registerA11yRegObserver() {
        try {
            if (a11yRegObserver != null) return; // 已注册（重连复用实例）
            lastRegState = true; // onServiceConnected 时必然登记着
            a11yRegObserver = new A11yRegObserver(this, handler);
            getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                    false, a11yRegObserver);
        } catch (Throwable ignored) {
            a11yRegObserver = null;
        }
    }

    /* ───────── v4.4.0 息屏缓存（功耗：热路径零 IPC）─────────
     * 静态内部类（规避 d8 8.2 匿名类 NPE bug，同 TapGestureCallback）。
     * v7.15.1：亮屏/解锁视作「重新进入」（用户实锤 + 真机日志实锤根治版）
     * ——SCREEN_ON（锁屏在场时按兵不动，等真正解锁）/ USER_PRESENT（解锁
     * 瞬间完整重入）双入口见 rearmForScreenOn(boolean)。 */
    private static final class ScreenStateRx extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            AdSkipService s = instance;
            if (s == null) return;
            String act = i == null ? null : i.getAction();
            s.screenInteractive = Intent.ACTION_SCREEN_ON.equals(act);
            /* v7.15.2 锁屏在场标记：SCREEN_ON 瞬间锁屏大概率在场（无锁设备
             * USER_PRESENT 会毫秒级跟上把标记冲掉）；解锁/息屏都不在场。 */
            if (Intent.ACTION_SCREEN_ON.equals(act)) {
                s.keyguardLikely = true;
            } else if (Intent.ACTION_USER_PRESENT.equals(act)
                    || Intent.ACTION_SCREEN_OFF.equals(act)) {
                s.keyguardLikely = false;
            }
            if (Intent.ACTION_SCREEN_ON.equals(act)
                    || Intent.ACTION_USER_PRESENT.equals(act)) {
                s.rearmForScreenOn(Intent.ACTION_USER_PRESENT.equals(act));
            }
        }
    }

    private android.content.BroadcastReceiver screenRx;
    volatile boolean screenInteractive = true;
    /* 广播注册成功后热路径才用缓存（失败回退逐次查询，功能绝不缩水） */
    private volatile boolean screenRxOk = false;

    /* ═══ v7.15.2 锁屏在场标记（广播维护，零 IPC）═══
     * SCREEN_ON → true（屏亮但锁屏大概率在场）；USER_PRESENT/SCREEN_OFF →
     * false。keyguardBlocking()：广播说“不在场”直接放行（零 IPC 热路径）；
     * 说“在场”再 IPC 一次定音（防 USER_PRESENT 漏播导致永久卡死——自愈型）。 */
    private volatile boolean keyguardLikely = false;

    /* ═══ v7.15.2 SDK 广告窗直断兜底链（息屏回来根治第二件套）═══
     * 真机日志（#96 · 09:13 实锤）：SDK 广告窗识别已打出、「700ms 内无
     * 真跳过键将温和直断」承诺已打出，但整开机周期返回键 0——扫描梯子
     * （唯一承载 L2 sdkCut 的路径）被代际护栏/窗口重入/树异常任意一环
     * 吃掉时，承诺就落空。兜底链独立于 scanGen（不受任何代际冲刷）：
     * 只认「窗口类名仍是那面 SDK 广告窗」——在 900/1600/2400/3400ms
     * 四档直接评估直断，零动作且无闸拦时由 execute() 派返回键。 */
    private final java.util.HashMap<String, Long> sdkEnfLogAt = new java.util.HashMap<>();

    private void registerScreenReceiver() {
        try {
            screenInteractive = isScreenOn(this); // 初值：一次 IPC 换后续零 IPC
            screenRx = new ScreenStateRx();
            android.content.IntentFilter f = new android.content.IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_USER_PRESENT); // v7.15.0：解锁（密钥锁解锁瞬间）同样 rearm
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(screenRx, f, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(screenRx, f);
                }
                screenRxOk = true;
            } catch (Throwable t) { // 部分 ROM 严格校验：无标志回退
                try {
                    registerReceiver(screenRx, f);
                    screenRxOk = true;
                } catch (Throwable t2) {
                    screenRxOk = false; // 极少数 ROM：回退逐次查询
                }
            }
        } catch (Throwable ignored) {
            screenRxOk = false;
        }
    }

    /* ═══ v7.15.1 亮屏/解锁视作「重新进入」（用户口径的根治版：窗龄重置 +
     * 节流清空 + 立即主动补扫）═══
     * v7.15.0 版只认 lastFgPkg——真机日志（issue#94 · Xiaomi 2410DPN6CC）
     * 实锤死因链：息屏期间锁屏窗（com.android.systemui）走零扫描门把
     * lastFgPkg 顶成锁屏宿主 → 「亮屏/解锁补扫」补的是锁屏自己的树
     * （「开屏未命中（systemui）树 73 节点」×6 连发、锁屏时钟被当「倒计时
     * 疑似装饰数字」）→ 真实应用若不伴随 WINDOW_STATE_CHANGED 回前台
     * （HyperOS 常态），内容事件被前台门前置过滤全部吞掉——广告白看。
     * 根治三件套：
     *  · 目标解析（RearmPlanner 纯函数，JVM 可测）：lastFgPkg 是真实
     *    应用直接用；被锁屏宿主污染 → 校正回 lastAppPkg（主路径维护的
     *    「真实可扫描应用前台」）并同步修复 lastFgPkg——解锁后真实应用
     *    的内容事件不再被前台门吞掉（引擎致盲根除点）；桌面/白名单/
     *    设置等真前台 → 无需补扫（用户此刻就在那里）；
     *  · 亮屏而锁屏仍在场（KeyguardManager.isKeyguardLocked，一次 IPC
     *    仅亮屏瞬间用）→ 状态都不动、绝不出手——屏上还是锁屏，坐标点击
     *    会落在锁屏上=误触红线；重入推迟到 USER_PRESENT（每解锁一次
     *    一链的成本语义保持；无锁设备亮屏即完整重入）；
     *  · 解锁重入 = 与真实「进入应用」同语义的完整重置（主路径进入块
     *    逐项镜像）：窗龄 windowAt 重置（旧窗龄不再把扫描门判超窗拒扫）
     *    + gaveUp/clickTries/backTries 收手态清零 + 倒计时锚点/触点轨迹/
     *    学习窗资格清 + 三张包级节流表（interWinAt/nudgeAt/rescueAt 按
     *    目标清）+ bumpScanChain 全梯子重排 + 0ms 内联立即补扫 + 400ms
     *    追加一针（广告窗内容晚到也接得住；CLEAN 档案窗自动走探针哨兵，
     *    功耗红线不破）。
     * 日志可见（用户扫描日志可核验）：「🔓 亮屏/解锁视作重新进入（pkg）：
     * 窗龄重置 + 节流清空 → 立即补扫」——污染校正时附注（前台追踪已校正）。 */
    private long lastFullRearmAt = 0;

    void rearmForScreenOn(boolean fromUnlock) {
        try {
            if (!masterOn) return;
            long now = System.currentTimeMillis();
            if (!fromUnlock) {
                /* 亮屏但锁屏还在场：等 USER_PRESENT（真正解锁）再重入。
                 * 无锁设备（亮屏即桌面/应用可见）走下方完整重入。 */
                if (keyguardBlocking()) return;
            }
            /* 同一解锁周期内（亮屏→解锁毫秒级连发）只完整重入一次 */
            if (now - lastFullRearmAt < 1500) return;
            /* ── 目标解析（RearmPlanner）：前台是真实应用直用；被锁屏
             *    宿主污染则校正回真实应用并修复前台追踪 ── */
            String fg = lastFgPkg;
            String fgCls = fg == null ? "" : winClassOf(fg);
            boolean fgOk = isRearmableApp(fg, fgCls);
            String appCls = lastAppPkg == null ? "" : winClassOf(lastAppPkg);
            boolean appOk = isRearmableApp(lastAppPkg, appCls);
            com.zai.adshield.core.RearmPlanner.Plan plan =
                    com.zai.adshield.core.RearmPlanner.resolve(fg, lastAppPkg, fgOk, appOk, true);
            if (plan.action != com.zai.adshield.core.RearmPlanner.ARM_DIRECT
                    && plan.action != com.zai.adshield.core.RearmPlanner.ARM_REPAIRED) return;
            final String target = plan.target;
            lastFullRearmAt = now;
            /* 前台追踪修复：解锁瞬间键 guard 正在消散，屏上即将可见的就是
             * 真实应用——修复后其内容事件不再被前台门前置过滤吞掉 */
            if (plan.repairFg) lastFgPkg = target;
            /* ── 与真实「进入应用」同语义的完整重置（主路径进入块镜像）── */
            windowAt.put(target, now);        // 窗龄重置——旧窗龄不再把扫描门判超窗拒扫
            gaveUp.put(target, Boolean.FALSE);
            clickTries.put(target, 0);
            backTries.put(target, 0);
            clearTapTrail(target);            // v6.0.0：新窗清空触点轨迹（同窗去重不跨重入生效）
            clearTapGuards(target);           // v6.1.0：清空同指纹目标静默/结果静默
            cdLastN.remove(target);           // 倒计时锚点重置（新广告=新倒计时）
            cdAnchorAt.remove(target);
            cdWaitUntil.remove(target);
            winClsLearned.remove(target);
            chainKilledAt.remove(target);     // 旧窗早退作废
            nudgeAt.remove(target);           // 节流清空：补扫
            rescueAt.remove(target);          // 节流清空：早退救回
            interWinAt.remove(target);        // 节流清空：插屏 2.5s 包级
            adClosedAt.remove(target);        // 静默期落账清除（重入后未命中日志可见）
            bumpScanChain(target);            // 全梯子重排（scanGen 代际护栏自动作废旧链）
            /* 立即主动补扫（0ms 内联——与真实进入应用完全同路径；CLEAN
             * 档案窗跳过内联扫，探针哨兵梯已由 bumpScanChain 排好） */
            if (!profileSuppressScan(target, winClassOf(target))) tryScan(target, now, 0);
            /* 400ms 追加一针：广告窗内容晚到也接得住（幂等：点击冷却/
             * 复验门自然挡住重复出手，窗口已切则前台门自动作废） */
            handler.postDelayed(() -> {
                try {
                    AdSkipService s = instance;
                    if (s == null || !masterOn) return;
                    String fg2 = lastFgPkg;
                    if (fg2 == null || !fg2.equals(target)) return;
                    if (!profileSuppressScan(target, winClassOf(target))) tryScan(target, winAt(target), 0);
                } catch (Throwable ignored) {
                }
            }, 400);
            /* ═══ v7.15.11 解锁晚到键两针（锁屏开屏根治一环 · #109 实锤）═══
             * 解锁瞬间广告窗常是 5 节点空壳（跳过键 4~6s 才渲染），既有链
             * 最晚 3.4s（晚档梯子 3.2s + SDK 兜底 3.4s）够不着晚到键；
             * 横幅在场时全部让路更是链死。+2.5s/+5.5s 两针兜底（幂等：
             * 前台已换/窗口已切自然作废；CLEAN 档案窗不扰——探针梯自理），
             * 每次解锁至多两扫，功耗红线不破。 */
            handler.postDelayed(() -> {
                try {
                    AdSkipService s = instance;
                    if (s == null || !masterOn) return;
                    if (screenRxOk && !screenInteractive) return; // 审核轮 r1：息屏让路（再亮屏重入接管）
                    String fg2 = lastFgPkg;
                    if (fg2 == null || !fg2.equals(target)) return;
                    if (!profileSuppressScan(target, winClassOf(target))) tryScan(target, winAt(target), 0);
                } catch (Throwable ignored) {
                }
            }, 2500);
            handler.postDelayed(() -> {
                try {
                    AdSkipService s = instance;
                    if (s == null || !masterOn) return;
                    if (screenRxOk && !screenInteractive) return; // 审核轮 r1：息屏让路（再亮屏重入接管）
                    String fg2 = lastFgPkg;
                    if (fg2 == null || !fg2.equals(target)) return;
                    if (!profileSuppressScan(target, winClassOf(target))) tryScan(target, winAt(target), 0);
                } catch (Throwable ignored) {
                }
            }, 5500);
            appendLog("🔓 亮屏/解锁视作重新进入（" + shortPkg(target) + "）：窗龄重置 + 节流清空 → 立即补扫"
                    + (plan.repairFg ? "（前台追踪已校正——锁屏污染清除）" : ""));
        } catch (Throwable ignored) {
        }
    }

    /** v7.15.1：rearm 目标资格（与主路径同键闸——桌面/白名单/系统噪声/
     *  零扫描宿主（锁屏·设置·输入法）/相机/聊天主界面/小程序容器都不是
     *  补扫目标；后两者 bumpScanChain 本就拒绝排梯，前置排除避免日志虚报） */
    private boolean isRearmableApp(String pkg, String cls) {
        if (pkg == null || pkg.length() == 0) return false;
        if (AdDecisionCore.isLauncherLike(pkg)) return false;
        if (whitelistSet.contains(pkg)) return false;
        if (isSystemNoise(pkg)) return false;
        if (com.zai.adshield.core.ComplianceGuard.isZeroScanScenario(pkg, cls)) return false;
        if (AdDecisionCore.isCameraPkg(pkg) || AdDecisionCore.isCameraClass(cls)) return false;
        if (AdDecisionCore.isChatMainUi(pkg, cls)) return false;
        if (MiniProgramGuard.isMiniProgramWindow(pkg, cls)) return false;
        return true;
    }

    /** v7.15.1：锁屏在场判定（一次 IPC，仅亮屏/解锁瞬间用——热路径零成本；
     *  查询失败按无锁放行——绝不错杀无锁设备的亮屏重入）
     *  v7.15.2：升级为广播优先 + IPC 定音自愈型（keyguardBlocking）的底层探针。 */
    private boolean keyguardLocked() {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager)
                    getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    /** v7.15.2：锁屏在场总闸（出手前零误触红线的统一入口）。
     *  广播未登记 → 回退逐次 IPC（v7.15.1 原路径）；广播说已不在场 → 零 IPC
     *  直接放行（热路径）；广播怀疑在场 → IPC 一次定音，实测不在场即自愈
     *  冲掉标记（防个别 ROM 漏播 USER_PRESENT 把标记卡成永久锁死）。 */
    private boolean keyguardBlocking() {
        if (!screenRxOk) return keyguardLocked();
        if (!keyguardLikely) return false;
        boolean locked = keyguardLocked();
        if (!locked) keyguardLikely = false;
        return locked;
    }

    /* ═══ v7.15.2 SDK 广告窗直断兜底链（scanGen 无关，窗口类名锚定）═══
     * 挂点①（主路径）：SDK 广告窗识别瞬间（🎯 日志同帧）排 4 档兜底针；
     * 挂点②（重入路径）：v7.15.1 解锁重入本身已重排全梯子，兜底针不受
     * 冲刷继续在岗。每档评估（全部条件独立复检，绝不依赖主路径状态）：
     *  · 窗口类名已换 → 静默退（广告窗已退场，新窗口自己的链接管）；
     *  · 前台已换/主开关停 → 静默退；
     *  · 主路径已有动作（点击梯子出手过/返回键用过/已收手）→ 静默退
     *    （兜底只补「零动作」死局，绝不抢主路径的班）；
     *  · 锁屏在场 → 让路等解锁重入（USER_PRESENT 后在岗的后续档 +
     *    重入全梯子接管）；
     *  · ≥700ms（与决策核心 CLS_BREAK_MS 同口径）且以上全清 → 直派
     *    返回键（execute 走既有安全闸/让路/复验全链）。诊断日志：未触发
     *    打抑制原因（每窗一条，10s 节流）——下轮上传日志直接定位是哪层。 */
    private static final long[] SDK_ENFORCE_AT = {900, 1600, 2400, 3400};

    private void scheduleSdkKillEnforcement(final String pkg, final String cls, final long winStart) {
        for (int i = 0; i < SDK_ENFORCE_AT.length; i++) {
            handler.postDelayed(() -> {
                AdSkipService s = instance;
                if (s == null) return;
                try {
                    s.sdkKillEnforce(pkg, cls, winStart);
                } catch (Throwable ignored) {
                }
            }, SDK_ENFORCE_AT[i]);
        }
    }

    private void sdkKillEnforce(String pkg, String cls, long winStart) {
        if (!masterOn) return;
        /* 窗口类名锚定：已不是那面 SDK 广告窗（广告自退/换窗）→ 静默退 */
        if (!cls.equals(winClassOf(pkg))) return;
        /* 前台门：窗口已不在前台 → 静默退 */
        if (!pkg.equals(lastFgPkg)) return;
        /* 主路径已接管（点击梯子/返回键/收手任一出手过）→ 静默退（不抢班） */
        if (triesOfClick(pkg) > 0 || triesOf(pkg) > 0) return;
        Boolean done = gaveUp.get(pkg);
        if (done != null && done.booleanValue()) return;
        /* 锁屏在场：屏上是锁屏不是广告——坐标/返回键全让路；
         * 解锁重入（USER_PRESENT）全梯子 + 后续兜底档接管 */
        if (keyguardBlocking()) {
            sdkEnfLog(pkg, cls, "锁屏在场——等解锁重入接管（后续档在岗）");
            return;
        }
        long now = System.currentTimeMillis();
        /* 直断时限：与决策核心 CLS_BREAK_MS 同口径（≥700ms） */
        if (now - winStart < 700) return;
        Ctx ctx = new Ctx();
        ctx.screenW = screenWidth;
        ctx.screenH = screenHeight;
        ctx.pkg = pkg;
        ctx.activityId = cls;
        ctx.sdkAdWin = true;
        ctx.rootWindow = cls.equals(rootWinClsOf(pkg));
        ctx.elapsedMs = now - winStart;
        Decision d = new Decision();
        d.action = AdDecisionCore.A_BACK;
        d.reason = "sdk_window_enforce";
        if (execute(pkg, d, null, ctx, true, now)) {
            // 直断成功：主路径复验链（execute 内 600ms 复验）接管后续
        } else {
            sdkEnfLog(pkg, cls, "execute 闸未放行（冷却/让路/验证码横幅——见相邻日志）");
        }
    }

    /** v7.15.2：SDK 直断兜底诊断日志（每包 10s 节流——不刷屏不冲掉 16 条留存量） */
    private void sdkEnfLog(String pkg, String cls, String why) {
        try {
            long now = System.currentTimeMillis();
            Long last = sdkEnfLogAt.get(pkg);
            if (last != null && now - last.longValue() < 10_000L) return;
            sdkEnfLogAt.put(pkg, now);
            if (sdkEnfLogAt.size() > 24) sdkEnfLogAt.clear(); // 防膨胀
            appendLog("🛟 SDK 直断兜底（" + shortPkg(pkg) + " · " + shortCls(cls) + "）：" + why);
        } catch (Throwable ignored) {
        }
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
        if (pkg.equals(getPackageName())) return;
        int et = event.getEventType();
        long now = System.currentTimeMillis();

        /* ═══ v4.4.0 热路径前置过滤（功耗主力，issue#40 事件 4602/开机）═══
         * 非前台包的内容变化事件是事件洪流主体（后台应用动画/刷新/灵岛动画），
         * 且无弹窗识别窗在期 → 一步丢弃（此前要过完整过滤器链+switch 才短路）。
         * 对比 lastFgPkg 是纯内存字符串比较，零 IPC 零哈希写。
         * ═══ v7.15.11 切换app前台回归探针（回切开屏整窗漏拦根治）═══
         * HyperOS/Android17 回切不发 STATE_CHANGED → lastFgPkg 停在旧应用
         * → 本应用内容事件在此被全量丢弃（整窗漏拦）。丢弃前过探针（节流
         * 闸后单次 binder，阳性证据才晋升——详见 maybeResyncForeground）。 */
        if (et == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && !pkg.equals(lastFgPkg)
                && now >= popupUntilGet(pkg)) {
            maybeResyncForeground(pkg, cs(event.getClassName()), now);
            return;
        }

        // v3.9.0 桌面/Launcher 黑名单（issue#12 闪退元凶根治）：桌面包事件
        // 直接丢弃——绝不扫描（桌面简单树被误判广告窗）、绝不学习（桌面图标
        // 位置被学成「跳过键」后下次启动直点图标=乱点）、绝不回弹（「桌面→
        // 微信」被当广告跳转踢出用户）。
        // v4.4.0：仅 WINDOW_STATE_CHANGED 才更新前台追踪（桌面壁纸/图标动画
        // 的内容事件曾把 lastFgPkg 污染成桌面 → 真前台应用的内容事件被前置
        // 过滤误伤 → 「触发不了」帮凶）。
        if (AdDecisionCore.isLauncherLike(pkg)) {
            if (et == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) lastFgPkg = pkg;
            return;
        }

        /* ═══ v4.3.0 应用白名单：用户圈定的应用零扫描零点击零学习零突发
         * （与桌面黑名单同级；仅维持前台包名追踪，白名单应用间切换
         *  不误判「新进入」）═══ */
        if (whitelistSet.contains(pkg)) {
            if (et == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) lastFgPkg = pkg;
            return;
        }

        /* ═══ v4.1.0 App 内主开关（暂停）：只廉价维持窗口/前台追踪，
         * 绝不扫描、绝不点击、绝不学习、绝不突发检测 ═══ */
        if (!masterOn) {
            if (et == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                String mc = cs(event.getClassName());
                if (!isDialogClass(mc)) { // Dialog/悬浮窗不改变前台应用
                    boolean entering = !pkg.equals(lastFgPkg);
                    lastFgPkg = pkg;
                    windowAt.put(pkg, now);
                    winClass.put(pkg, mc);
                    if (entering) rootWinCls.put(pkg, mc);
                }
            }
            return;
        }

        // v4.1.0 相机/拍摄窗硬黑名单（issue#30：微信点「拍摄」进相机即被点「×」强退）
        if (AdDecisionCore.isCameraPkg(pkg) || AdDecisionCore.isCameraClass(cs(event.getClassName()))
                || AdDecisionCore.isCameraClass(winClassOf(pkg))) return;

        /* ═══ v5.5.0 模块四：零扫描场景扩容（系统设置/输入法/锁屏/密码框）═══
         * 在既有短信/来电/相机/验证码横幅/支付页/白名单/聊天主界面之上
         * 再堵四类：设置页开关、输入法候选词/键盘、锁屏紧急呼叫、银行
         * 密码页——【绝不扫描、绝不点击、绝不学习】，仅维持前台追踪。 */
        if (com.zai.adshield.core.ComplianceGuard.isZeroScanScenario(
                pkg, cs(event.getClassName()))) {
            /* v7.15.13 输入法前台劫持修复（#114 Sony 实锤：评论框键盘起落把
             * lastFgPkg 反复劫走 → 每 5s 一轮「切换app回归」晋升 + 全量重扫。
             * 键盘是覆盖在真前台应用上的浮层，不是前台应用——IME 族包只
             * 零扫描返回，绝不写前台追踪；设置/锁屏等真实前台切换照旧。
             * 检测与 ComplianceGuard.ime 同源（.inputmethod/.ime 族）。 */
            if (et == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && !isDialogClass(cs(event.getClassName()))
                    && !AdDecisionCore.isImePkg(pkg)) {
                lastFgPkg = pkg;
            }
            return;
        }

        /* ═══ v5.0.0 小程序/团购容器保护（L0 第一层：事件入口）═══
         * 抖音团购券/微信/支付宝/QQ 小程序容器窗口：事件直接丢弃——
         * 绝不扫描、绝不点击、绝不学习（issue 用户实锤：点团购券拉起
         * 小程序，加载页「跳过广告」文案被当真广告关掉，用户被弹回）。
         * 仅维持窗口/前台追踪（同白名单路径）——容器窗口类必须记录，
         * 否则后续内容事件查不到容器身份；离开容器回主界面即恢复正常。 */
        {
            String evClsMini = cs(event.getClassName());
            String miniWhy = MiniProgramGuard.matchReason(pkg, evClsMini);
            if (miniWhy == null) miniWhy = MiniProgramGuard.matchReason(pkg, winClassOf(pkg));
            if (miniWhy != null) {
                if (et == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    boolean enteringMini = !pkg.equals(lastFgPkg);
                    lastFgPkg = pkg;
                    windowAt.put(pkg, now);
                    winClass.put(pkg, evClsMini);
                    if (enteringMini) rootWinCls.put(pkg, evClsMini);
                }
                return;
            }
        }

        if (isSystemNoise(pkg)) return;

        // v3.13.0 息屏休眠门（夜间功耗治理 + 息屏绝不出手）：息屏 = 用户看不见
        // 任何广告（没有可跳对象），此时唯一可能出现在前台的是来电/闹钟等敏感窗
        // ——扫描/点击/学习全是浪费甚至危险（误点来电挂断/闹钟关闭）。仅廉价维护
        // 前台包名追踪（亮屏后进入应用的入口窗判定仍正确），其余事件全部丢弃。
        // v4.4.0 功耗：改用 SCREEN_ON/OFF 广播维护的缓存标记（零 IPC；
        // 此前每事件一次 PowerManager.isInteractive binder 调用）
        boolean screenOn = screenRxOk ? screenInteractive : isScreenOn(this);
        if (!screenOn) {
            if (et == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                lastFgPkg = pkg;
            }
            return;
        }

        // v3.13.0 验证码横幅防护①：短信/彩信/来电类覆盖层包（mms/smsextra/
        // messaging/incallui）事件入口直接丢弃——绝不扫描（横幅上的小 × 绝不被
        // 当「角落跳过键」点击）、绝不学习（手动点「复制验证码」绝不被学成跳过键）
        if (AdDecisionCore.isSmsOverlayPkg(pkg)) return;

        energyEvents++;

        switch (et) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                String cls = cs(event.getClassName());
                /* ═══ v4.1.0 前台门（灵动岛/悬浮窗根治，issue#30）═══
                 * 非前台包的窗口事件（灵动岛展开/悬浮窗/后台服务弹层）：
                 * 绝不更新前台追踪（灵岛窗口曾被当「新应用进入前台」）、
                 * 绝不重置开屏窗计时、绝不启动开屏扫描链（灵岛小窗控件
                 * 被当「角落跳过键」= 乱点元凶）。只允许走严格门控的弹窗
                 * 识别路径（Dialog 形态 + 角标/营销话术双信号）。
                 * v7.15.11：条件求值缓存化（dialog/mine/small 各一次，IPC
                 * 剖面不变）+ 前台竞速登记（回切开屏整窗漏拦重建——v7.12.0
                 * 晋升机制在 v7.15.0 版本线重制时遗失）：最近任务回切/切换
                 * 动画期活动窗口尚未切换完成 → 归属查询 false → 事件被丢弃
                 * 且不登记 lastFgPkg → 该应用后续内容事件被前台门全量丢弃。
                 * 登记竞速候选（3s 窗口），由后续内容事件的阳性证据探针
                 * （maybeResyncForeground）复核晋升。仅非 Dialog 非小窗的
                 * 普通窗口才有资格（Dialog/小窗不改变前台应用）。 */
                if (!pkg.equals(lastFgPkg)) {
                    boolean dialog = isDialogClass(cls);
                    boolean mine = dialog ? false : isActiveWindowOf(pkg);
                    boolean small = (!dialog && mine) ? isSmallOverlayWindow(pkg) : false;
                    if (dialog || !mine || small) {
                        /* v4.2.0 IPC 重排：活动窗口归属查询（单次 IPC）前置——
                         * 常见路径一次短路，小窗全量遍历（getWindows 重 IPC）仅在
                         * 活动窗口确属该包时才兜底执行（灵动岛小窗包场景） */
                        if (dialog && !AdDecisionCore.isOemUtilityPkg(pkg)) {
                            popupUntil.put(pkg, now + POPUP_WINDOW_MS);
                            nudgePopupScan(pkg, now);
                        } else if (!dialog && !mine && !small) {
                            noteFgRaceCandidate(pkg, cls, now);
                        }
                        return;
                    }
                }
                /* v7.15.11：正常进入处理到场——竞速候选即作废（互不侵蚀） */
                fgPendingPkg = null;
                /* v7.15.11 Toast 类窗零开屏链（#105 实锤白扫；审核轮 r1 前置：
                 * Toast 窗不是窗——绝不重置窗龄/复验计数/不写 winClass，
                 * 否则同包 Toast 出现即断流当前窗的晚到键补扫链 + 该应用
                 * 后续内容事件被 Toast 类名门全量挡住直到下一真实窗口事件）。
                 * 只维持前台追踪（事件已过全部前台门）。 */
                if (isToastClass(cls)) {
                    lastFgPkg = pkg;
                    break;
                }
                // v3.6.0 根窗口追踪：新进入前台的应用，其首个窗口 = 入口窗
                //（冷启动开屏 / 从桌面·其他应用回前台的首窗）——入口窗上
                // 按返回键 = 退出应用，安全门据此禁一切返回键兜底
                boolean entering = !pkg.equals(lastFgPkg);
                lastFgPkg = pkg;
                lastAppPkg = pkg; // v7.15.1：真实应用前台追踪（rearm 目标解析用——锁屏/桌面/白名单永不写入）
                /* v4.2.0 用户主动打开窗口标记：用户真实点击后 2.5s 内切到的
                 * 非 Dialog 新窗口 = 用户主动打开（广告落地页/商品详情页）。
                 * 30s 内引擎对该窗口零动作——「点开又被自动关、手动重开
                 * 又被关」根治。引擎代点（lastAutoClick）后打开的窗口
                 * 不算（那可能是广告自己跳的落地页，但纯跳过引擎也绝不
                 * 对抗任何跳转——v3.12.0 链式拦截红线）。 */
                long uAct = userActAtGet(pkg);
                boolean recentAuto = pkg.equals(lastAutoClickPkg) && (now - lastAutoClickAt) < USER_OPEN_WIN_MS;
                if (!entering && !isDialogClass(cls) && uAct > 0
                        && now - uAct < USER_OPEN_WIN_MS && !recentAuto) {
                    userOpenCls.put(pkg, cls);
                    userOpenUntil.put(pkg, now + USER_OPEN_GUARD_MS);
                }
                // v3.5.0 复验通过：上一窗点过东西且窗口已切走 → 广告已关
                Integer prevTries = clickTries.get(pkg);
                if (prevTries != null && prevTries > 0 && !cls.equals(winClassOf(pkg))) {
                    appendLog("✓ 复验通过（" + shortPkg(pkg) + "）：广告窗已关闭"
                            + (prevTries > 1 ? "（共 " + prevTries + " 次尝试，重试梯子兜底）" : ""));
                    // v3.6.0 毒链① 根治：广告已关，学习窗快断资格同步作废
                    //（防止后续窗口还拿着旧资格按返回键）
                    winClsLearned.remove(pkg);
                    // v5.1.0 静默期落账：首页内容落定期（2.2s）不再记
                    // 「开屏未命中」——同一秒对 79 节点首页整树扫描是纯噪声
                    if (adClosedAt.size() > 64) adClosedAt.clear();
                    adClosedAt.put(pkg, now);
                }
                windowAt.put(pkg, now);
                winClass.put(pkg, cls);
                if (entering) rootWinCls.put(pkg, cls); // v3.6.0：记录入口窗
                backTries.put(pkg, 0);
                clickTries.put(pkg, 0); // v3.5.0：新窗重置复验计数
                clearTapTrail(pkg); // v6.0.0：新窗清空触点轨迹（同坐标去重只在同窗内生效）
                clearTapGuards(pkg); // v6.1.0：新窗清空同指纹目标静默/结果静默（多点防线只在同窗内生效）
                cdLastN.remove(pkg);   // v3.8.0：新窗重置倒计时锚点
                cdAnchorAt.remove(pkg);
                cdWaitUntil.remove(pkg);
                gaveUp.put(pkg, Boolean.FALSE); // v3.6.0：新窗重置收手标记
                winClsLearned.remove(pkg);
                chainKilledAt.remove(pkg); // v4.3.0：新窗重置内容页早退
                nudgeAt.remove(pkg);      // v4.3.0：新窗重置补扫节流
                rescueAt.remove(pkg);     // v4.4.0：新窗重置救回节流（旧窗时间戳不得拖慢新窗首救）
                if (isDialogClass(cls) && !AdDecisionCore.isOemUtilityPkg(pkg)) {
                    /* ═══ v4.5.0 用户打开的弹窗/抽屉守护（乱点根治第二道闸）═══
                     * issue#42-44 实锤：用户点开右侧抽屉（BaseCustomDialogFragment
                     * 菜单），4s 让路期一过引擎就点菜单里的「跳过」列表项。
                     * 用户 2.5s 内真实点击后弹出的 Dialog = 用户主动打开
                     * （菜单/分享面板/设置弹窗）→ 30s 引擎零动作（userYield
                     * 的 userOpened 路径）。与形状门（isMenuListRow）双保险。 */
                    long dAct = userActAtGet(pkg);
                    if (dAct > 0 && now - dAct < USER_OPEN_WIN_MS) {
                        userOpenCls.put(pkg, cls);
                        userOpenUntil.put(pkg, now + USER_OPEN_GUARD_MS);
                    }
                    popupUntil.put(pkg, now + POPUP_WINDOW_MS);
                }
                if (AdDecisionCore.isAdActivityClass(cls)) {
                    appendLog("🎯 SDK 广告窗识别（" + shortPkg(pkg) + "）：" + cls
                            + " → 700ms 内无真跳过键将温和直断");
                    /* v7.15.2：直断兜底链挂点①——识别帧即排（scanGen 无关：
                     * 主路径任何重入/冲刷都不影响四档兜底针的评估与出手） */
                    scheduleSdkKillEnforcement(pkg, cls, now);
                } else if (learnedClassHit(pkg, cls)) {
                    winClsLearned.put(pkg, Boolean.TRUE);
                }
                /* ═══ v5.3.0 位置学习验证钩子①：窗类切换 = 跳过键关窗铁证
                 *（零文本点击挂起中：广告窗退出/Dialog 消散 → 提交学习）═══ */
                posVerifyOnWindowChange(pkg, cls, now);
                bumpScanChain(pkg);
                /* ═══ v4.5.0 露头就秒·内联首扫 ═══
                 * 0ms 档从「postDelayed 排队等下一轮消息循环」改为窗口事件
                 * 现场同步出手（省一跳 handler 约 5~16ms，真·露头就秒）。
                 * 链上 0ms 档随后仍会扫（幂等：点击冷却/复验门自然挡住重复
                 * 出手），窗口已切则代际护栏自动作废，零副作用。
                 * v5.3.0：CLEAN 档案窗跳过内联首扫——bumpScanChain 已排探针
                 * 哨兵，此处再全量扫就把省电档案的意义扫没了。 */
                if (!profileSuppressScan(pkg, cls)) tryScan(pkg, winAt(pkg), 0);
                /* v7.4.0：进入信息流窗（state-changed）即拉起 feed 扫描/巡检——
                 * 只挂在 content-changed 上的旧设计在「用户进朋友圈后静止」时
                 * 零事件零扫描（日志 #79/#80 实锤死因之一）。此处只做窗级门+
                 * 节流判定，非信息流窗零成本直接返回。 */
                maybeFeedScan(pkg, now);
                /* v7.5.0：插屏卫士（名单外 App 的插入广告）——窗切换事件拉起，
                 * 2.5s 包级节流，非名单 App 窗内「角标+关闭钮」签名即收。 */
                maybeInterstitialScan(pkg, now);
                break;
            }
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                // v4.1.0 前台门：非前台包的内容变化（灵岛动画/悬浮窗刷新）
                // 绝不扫描、绝不突发检测——灵岛高频内容变化曾被当「内容
                // 突发→覆盖层弹窗」开启弹窗识别窗 → 乱点灵岛控件
                if (!pkg.equals(lastFgPkg)) {
                    /* v7.15.11：弹窗识别窗期内不探（审核轮 r1 收敛——弹窗
                     * 链自己扫该包的 Dialog；回归探针只走前置过滤主路径） */
                    if (now < popupUntilGet(pkg)) nudgePopupScan(pkg, now);
                    break;
                }
                /* ═══ v5.3.0 位置学习验证钩子②：树体缩水（同窗广告容器
                 * 卸载/Dialog 消散）——零文本点击挂起中，缩水 ≥40% 即提交 ═══ */
                posVerifyOnContentChange(pkg, now);
                /* ═══ v7.1.0 FeedAdGuard：信息流广告（微信朋友圈就业广告等
                 * App 内广告）两段式识别——独立于开屏链（feed 窗常驻、窗计时
                 * 不重置）；名单门 + 微信 sns 窗门 + 1.2s 节流 + 300 节点预算
                 * 双闸（功耗红线）；开屏窗期内让路（开屏路径优先）。 ═══ */
                maybeFeedScan(pkg, now);
                if (now - winAt(pkg) < splashDeadlineMs(winClassOf(pkg))
                        && !isToastClass(winClassOf(pkg))) { // v7.15.11：Toast 窗零开屏链
                    nudgeScan(pkg, now);
                    break;
                }
                if (now < popupUntilGet(pkg)) {
                    nudgePopupScan(pkg, now);
                    break;
                }
                /* v7.5.0：插屏卫士——开屏窗/弹窗窗都不在期时（窗已落定）的
                 * 内容变化拉起（插层滑动入场正是 content-changed 源）；
                 * 2.5s 包级节流，角标不在场零出手。 */
                maybeInterstitialScan(pkg, now);
                detectPopupBurst(pkg, now);
                break;
            }
            case AccessibilityEvent.TYPE_VIEW_CLICKED: {
                if (isOwnAutoClick(pkg, now)) return;
                // v4.2.0 用户意图让路：真实用户点击 → 4s 让路计时开始；
                // 学习与误触观察照常（用户手动跳过仍会被学习）
                userActAt.put(pkg, now);
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
                || pkg.equals("com.android.settings")
                /* v7.15.10【#103 日志实锤】系统权限弹窗（permissioncontroller）
                 * 被当开屏扫描 + 弹窗识别双路径白扫：「开屏未命中
                 * （permissioncontroller）……候选样本：拒绝/始终允许」+
                 * 「弹窗未命中 · 入窗 1.79E9s」（winAt=0 疯值）双日志刷屏。
                 * GrantPermissionsActivity 的「拒绝」按钮是全 app 误触代价
                 * 最高的控件之一（拒绝权限=功能残废）——虽然决策层没点它，
                 * 但扫描面就不该进来：零扫描零点击零学习，同 systemui 口径。 */
                || pkg.contains("permissioncontroller")
                /* v7.15.11【#106 OPPO 日志实锤】securitypermission（OPPO
                 * 权限详情页族 AppBehaviorDetailActivity/PermissionGroupsActivity）
                 * 被当开屏扫描+多窗补扫白扫——与 permissioncontroller 同类
                 * 的系统权限面（误触代价最高控件聚集地），零扫描零点击零学习。 */
                || pkg.contains("securitypermission");
    }

    /** v7.15.11：Toast 类窗口（aweme ToastDialog 等自灭提示窗）——
     *  开屏扫描链零接入（#105 实锤白扫：树 600 节点未命中刷屏；Toast
     *  2~3.5s 自动消散且从无跳过键）。类名含 Toast 即认定（提示窗命名
     *  惯例；开屏广告窗住 Activity 从不叫 Toast）。 */
    private static boolean isToastClass(String cls) {
        return cls != null && cls.contains("Toast");
    }

    /** v3.6.0：厂商系统工具包（安全中心/系统管家类）——弹窗突发检测不适用
     *（真机日志：securitycenter/keep 的系统弹窗被误开弹窗识别窗）。
     *  v3.13.0：上移到决策核心 isOemUtilityPkg（JVM 可单测），本类三处共用。
     *  v7.15.6 冗余清理：纯转发包装移除，调用点直接走 AdDecisionCore。 */

    /** v3.13.0：息屏判定（查询失败按亮屏放行——绝不错杀正常跳过） */
    static boolean isScreenOn(Context ctx) {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    /** v4.1.0：焦点窗口是否属于 pkg（非 Dialog 的真实前台 Activity 切换）。
     *  查询失败按 true 放行（绝不错杀真开屏）。 */
    private boolean isActiveWindowOf(String pkg) {
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            CharSequence rp = r == null ? null : r.getPackageName();
            return rp == null || pkg.contentEquals(rp); // 拿不到根节点时保守放行
        } catch (Throwable t) {
            return true;
        }
    }

    /** v4.1.0：pkg 是否只存在「小窗」形态的窗口（灵动岛/悬浮窗：面积 <45%
     *  屏或高度 <55% 屏）。灵岛小窗控件被当「角落跳过键」= 乱点元凶——
     *  小窗一律不走开屏阶梯。查不到窗口按 false（Activity 启动竞速期
     *  保守放行，绝不错杀真开屏）。
     *  v4.3.0 功耗：结果缓存 500ms（灵岛动画的高频窗口事件不再每次
     *  getWindows 重 IPC——仅窗口形态查询，500ms 内状态不会翻转）。 */
    private boolean isSmallOverlayWindow(String pkg) {
        if (screenWidth <= 0 || screenHeight <= 0) return false;
        long now = System.currentTimeMillis();
        long[] cached = overlayCache.get(pkg);
        if (cached != null && now - cached[0] < 500) {
            return cached[1] == 1;
        }
        boolean r = isSmallOverlayWindowUncached(pkg);
        overlayCache.put(pkg, new long[]{now, r ? 1L : 0L});
        if (overlayCache.size() > 48) overlayCache.clear(); // 防膨胀
        return r;
    }

    private boolean isSmallOverlayWindowUncached(String pkg) {
        try {
            List<AccessibilityWindowInfo> wins = getWindows();
            if (wins == null) return false;
            for (AccessibilityWindowInfo w : wins) {
                if (w == null) continue;
                AccessibilityNodeInfo r = w.getRoot();
                CharSequence rp = r == null ? null : r.getPackageName();
                if (rp == null || !pkg.contentEquals(rp)) continue;
                Rect b = new Rect();
                w.getBoundsInScreen(b);
                if (b.isEmpty()) continue;
                double area = (b.width() * (double) b.height()) / (double) (screenWidth * screenHeight);
                if (area < 0.45 || b.height() < screenHeight * 0.55) return true;
                return false; // 存在全屏尺寸窗口 = 真实 Activity，非灵岛
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isDialogClass(String cls) {
        if (cls == null) return false;
        String c = com.zai.adshield.core.RuleEngine.lowerAscii(cls); // v7.3.0 算法：零分配归一（热点：每窗口事件）
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

    private long userActAtGet(String pkg) {
        Long t = userActAt.get(pkg);
        return t == null ? 0L : t.longValue();
    }

    /* ───────── v4.2.0 用户意图让路（execute 全动作统一门）─────────
     * ① 用户真实点击后 4s 内：该应用一律不出手（点击/返回键全让路）；
     * ② 用户主动打开的窗口（2.5s 内点击后切到的非 Dialog 窗）：30s 保护。
     * 返回 true = 本轮让路（不出手）。日志同包 8s 去重防刷屏。 */
    private boolean userYield(String pkg, long now) {
        long uAct = userActAtGet(pkg);
        boolean recentAct = uAct > 0 && now - uAct < USER_YIELD_MS;
        boolean userOpened = false;
        String uoc = userOpenCls.get(pkg);
        if (uoc != null && uoc.equals(winClassOf(pkg))) {
            Long until = userOpenUntil.get(pkg);
            userOpened = until != null && now < until;
        }
        if (!recentAct && !userOpened) return false;
        Long lastY = yieldLogAt.get(pkg);
        if (lastY == null || now - lastY > 8000) {
            yieldLogAt.put(pkg, now);
            appendLog("🤝 用户操作让路（" + shortPkg(pkg) + "）："
                    + (userOpened ? "这是你主动打开的页面，30s 内绝不自动关闭"
                    : "检测到你的点击，4s 内暂不出手"));
        }
        return true;
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

        /**
         * v6.0.0 1:1 人眼追踪：出手前刷新节点实时状态（bounds/可见性）。
         * 扫描时刻捕获的几何到手势真正落屏有 40~300ms 延迟——广告按钮
         * 入场缩放/位移动画后按「它曾在哪」点击=点空 → 复验 → 多点级联。
         * refresh() 后 bounds()/visible() 读到的就是【现在】的几何——
         * 人眼看到哪就点到哪。失败/已失效 → false（回退决策时几何）。
         */
        public boolean refreshLive() {
            try {
                return n != null && n.refresh();
            } catch (Throwable t) {
                return false;
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
        // v4.2.0 聊天主界面门：微信主界面没有跳过键，16 档定时扫描
        // 一个都不排（零调度零唤醒，功耗最优；Dialog 弹窗不受影响）
        if (AdDecisionCore.isChatMainUi(pkg, winClassOf(pkg))) return;
        // v5.0.0 小程序容器保护：容器内 16 档定时扫描一个都不排
        //（零调度零唤醒；离开容器回主界面重新进入开屏窗即恢复）
        if (MiniProgramGuard.isMiniProgramWindow(pkg, winClassOf(pkg))) return;
        final long winStart = winAt(pkg);
        /* ═══ v5.3.0 宿主窗档案（李跳跳式三零治理 · #57/#58 功耗根治）═══
         * 连续 3 次零候选的 (pkg|cls) 判 CLEAN：
         *  · 探针哨兵 0ms 统一排（多窗口径——覆盖层复活通道）；
         *  · 晚档轻扫梯子：无订阅 {700,1600,3200} · 有订阅 {0,700,1600,3200}
         *    （v7.15.9：netdisk 晚到键救回——见下方 v7.15.9 注释块）；
         *  · Dialog/SDK 广告窗/学习指纹/学习位置/72h 复查期到 → 全量梯子。 */
        final String pCls = winClassOf(pkg);
        if (profileSuppressScan(pkg, pCls)) {
            Integer g0 = scanGen.get(pkg);
            final int gen0 = g0 == null ? 1 : g0 + 1;
            scanGen.put(pkg, gen0);
            energyProfileSkips++;
            boolean subCovered = SubscriptionManager.coversPkg(pkg);
            long[] ladder = PowerGovernor.ladderFor(true, subCovered);
            /* ═══ v7.15.9【netdisk 实锤根治】CLEAN 窗晚到键救回 ═══
             * #102 实锤：netdisk「跳过 4」被用户手动点（手动样本 9→14）——
             * SurfaceView 视频开屏零内容事件（「树静默」）时时间梯子是唯一
             * 希望，而 v7.15.8 的 CLEAN 窗：无订阅 = 纯探针（0ms 一次），
             * 有订阅 = {0,700}——晚到跳过键（1~3s 出现）无人再扫。
             * 对策：① 探针哨兵 0ms 统一排（v7.15.8 只有无订阅窗才排——
             * 订阅窗的 0ms 只有单活动窗口径，多窗探针复活通道缺席）；
             * ② 晚档轻扫梯子（700/1600/3200，PowerGovernor.LADDER_CLEAN_*）
             * ——微树/小树整树 walk ~1ms/档，典型日功耗模型红线不变。 */
            handler.post(() -> probeSentinel(pkg, winStart, gen0));
            Long lastLog = profileLogAt.get(pkg);
            long nowMs = System.currentTimeMillis();
            if (lastLog == null || nowMs - lastLog > 60000) {
                profileLogAt.put(pkg, nowMs);
                appendLog("🔋 省电档案生效（" + shortPkg(pkg) + " · " + shortCls(pCls)
                        + "）：连续 " + PowerGovernor.CLEAN_STREAK_MIN
                        + " 次零候选 → 探针哨兵 + 晚档轻扫（0.7/1.6/3.2s），不再全量梯子");
            }
            for (int i = 0; i < ladder.length; i++) {
                final long at = ladder[i];
                handler.postDelayed(() -> {
                    Integer cur = scanGen.get(pkg);
                    if (cur == null || cur.intValue() != gen0) return;
                    tryScan(pkg, winStart, at);
                }, at);
            }
            return;
        }
        Integer g = scanGen.get(pkg);
        final int gen = g == null ? 1 : g + 1;
        scanGen.put(pkg, gen);
        for (int i = 0; i < SCAN_AT.length; i++) {
            final long at = SCAN_AT[i];
            handler.postDelayed(() -> {
                Integer cur = scanGen.get(pkg);
                if (cur == null || cur.intValue() != gen) return;
                // v4.3.0 功耗：内容页早退后本窗固定扫描档全取消
                Long killed = chainKilledAt.get(pkg);
                if (killed != null && killed.longValue() == winStart) return;
                tryScan(pkg, winStart, at);
            }, at);
        }
        /* ═══ v7.15.5 SDK 广告窗梯子补档（14/16.5/19s）═══
         * 视频开屏观察窗 12s→20s（splashDeadlineMs）的梯子侧配套：骨架档
         * 只到 12s，SurfaceView 视频窗零内容事件（树静默）时 12~20s 段
         * 无人扫。只对 SDK 广告窗补三档（普通窗的功耗红线零变动；广告窗
         * 本就全量梯子 + 零候选退避豁免，三档轻扫的代价可忽略）。 */
        if (AdDecisionCore.isAdActivityClass(pCls)) {
            for (int i = 0; i < VIDEO_SPLASH_SCAN_AT.length; i++) {
                final long at = VIDEO_SPLASH_SCAN_AT[i];
                handler.postDelayed(() -> {
                    Integer cur = scanGen.get(pkg);
                    if (cur == null || cur.intValue() != gen) return;
                    Long killed = chainKilledAt.get(pkg);
                    if (killed != null && killed.longValue() == winStart) return;
                    tryScan(pkg, winStart, at);
                }, at);
            }
        }
    }

    /* ═══ v5.5.0 模块三：设备分级 + 电量缓存（60s sticky 广播查询，热路径零成本）═══ */
    private int deviceTierCache = -1;
    private int batteryPctCache = 100;
    private boolean chargingCache = false;
    private long batteryCheckedAt = 0;

    /** 刷新设备分级与电量（60s 缓存；RAM 分级仅首次）。低端机低电量→预算 ×0.6。 */
    private void refreshPerfTier() {
        long now = System.currentTimeMillis();
        if (now - batteryCheckedAt < 60_000L) return;
        batteryCheckedAt = now;
        try {
            android.content.IntentFilter bf = new android.content.IntentFilter(
                    android.content.Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent bi = registerReceiver(null, bf);
            if (bi != null) {
                int level = bi.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = bi.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                int status = bi.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                batteryPctCache = level >= 0 && scale > 0 ? level * 100 / scale : 100;
                chargingCache = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                        || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Throwable ignored) {
        }
        if (deviceTierCache < 0) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                int ramMb = (int) (mi.totalMem / (1024L * 1024L));
                deviceTierCache = com.zai.adshield.core.ComplianceGuard.deviceTier(ramMb);
                appendLog("🔋 性能档：设备分级 " + ramMb + "MB → "
                        + (deviceTierCache == com.zai.adshield.core.ComplianceGuard.TIER_LOW ? "低端（增益路径降级启用）"
                        : deviceTierCache == com.zai.adshield.core.ComplianceGuard.TIER_MID ? "中端" : "旗舰（零降级）"));
            } catch (Throwable t) {
                deviceTierCache = com.zai.adshield.core.ComplianceGuard.TIER_HIGH; // 默认不降级（保守：能力优先）
            }
        }
    }

    /* ═══ v5.5.0 模块四：动作边界拦截日志（节流：同包 10s 一条，防刷屏）═══
     *  v5.5.1（issue#64 实锤）：按失败层打点 + 目标签要——旧文案「语义或
     *  越界」两可含糊，微博误杀案定位花了一整轮日志分析；新日志直接
     *  回答「哪层拒绝、拒绝的是什么」。 */
    private final java.util.HashMap<String, Long> boundaryLogAt = new java.util.HashMap<String, Long>();

    private void boundaryGuardLog(String pkg, String reason, int layer, String target) {
        long now = System.currentTimeMillis();
        Long last = boundaryLogAt.get(pkg);
        if (last != null && now - last < 10_000L) return;
        boundaryLogAt.put(pkg, now);
        String tgt = target == null ? "" : target.trim();
        if (tgt.length() > 12) tgt = tgt.substring(0, 12) + "…";
        appendLog("🚧 动作边界自检拦截（" + shortPkg(pkg) + " · " + reasonZh(reason) + "）："
                + com.zai.adshield.core.ComplianceGuard.boundaryLayerName(layer)
                + (tgt.length() > 0 ? " · 目标「" + tgt + "」" : "")
                + " → 放弃（宁可漏拦绝不误触）");
    }

    /** v5.3.0 探针哨兵：CLEAN 档案窗口的零 DFS 检查（李跳跳核心路径）。
     *  findAccessibilityNodeInfosByText 是系统侧 C++ 子树搜索（成本≈事件
     *  处理的 1/4、DFS 扫描的 1/14）；命中即档案复活并转入正常出手路径
     *  （tryScan 0ms 档全量：规则/复验/执行全保留）；不中 → 零成本放行。
     *  gen < 0 表示跳过代际护栏（节流补扫路径，探针幂等零副作用）。 */
    private void probeSentinel(final String pkg, final long winStart, final int gen) {
        if (instance == null || !masterOn) return;
        if (pkg == null || !pkg.equals(lastFgPkg)) return;
        if (gen >= 0) {
            Integer cur = scanGen.get(pkg);
            if (cur == null || cur.intValue() != gen) return;
        }
        /* v7.15.10【功耗】探针去重（200ms/包）：bumpScanChain CLEAN 分支的
         * 0ms 探针与首个内容事件探针（probeSentinelThrottled 只记自己的账）
         * 双跑重叠——并窗 0~250ms 内两轮全窗枚举+文本搜索是纯浪费。
         * 入口统一记账（含直连/节流两路）：200ms 内同包直接跳过（探针幂等
         * 零副作用，晚 200ms 不影响命中率——内容事件源持续到达）。 */
        try {
            long pn = System.currentTimeMillis();
            Long lastProbe = probeLastAt.get(pkg);
            if (lastProbe != null && pn - lastProbe.longValue() < 200) return;
            probeLastAt.put(pkg, Long.valueOf(pn));
            if (probeLastAt.size() > 24) probeLastAt.clear(); // 防膨胀
        } catch (Throwable ignored) {
        }
        try {
            /* v7.15.7→v7.15.8【事故修复后口径】：探针多窗化保留（Keep 覆盖层
             * 复活通道——CLEAN 档案窗只有探针在跑，不枚举就永远看不见覆盖层
             * 里的跳过键），但 v7.15.7 的【无节流全量 getRoot】已收敛：
             * collectRoots(allWins) 内部 400ms 枚举节流 + TYPE_APPLICATION
             * 前置过滤（每窗最多 2.5 次枚举、每次只对同包应用窗 getRoot
             * ——与 v7.3.0 回退路径同量级；文本/图形双探针逐根探测，任一
             * 命中即复活；非开屏窗维持单根口径零增量）。 */
            boolean splashProbe = System.currentTimeMillis() - winStart <= splashDeadlineMs(winClassOf(pkg));
            AccessibilityNodeInfo[] roots = collectRoots(pkg, splashProbe);
            if (roots.length == 0) return;
            energyProbes++;
            Ctx ctx = new Ctx();
            ctx.screenW = screenWidth;
            ctx.screenH = screenHeight;
            ctx.pkg = pkg;
            ctx.activityId = winClassOf(pkg);
            ctx.elapsedMs = Math.max(0, System.currentTimeMillis() - winStart);
            /* v7.15.5：与 splashLive/让路同口径（splashDeadlineMs 唯一口径——
             * 旧硬编码 WINDOW_MS 是散落第四处，统一收编；CLEAN 档案窗本身
             * 只对普通内容窗成立，SDK 广告窗豁免 CLEAN，实际行为零变化） */
            ctx.splashLive = ctx.elapsedMs <= splashDeadlineMs(winClassOf(pkg));
            ctx.guard = AdDecisionCore.isGuardPkg(pkg);
            /* v7.15.6 图形探针：文本探针盲区（纯图片跳过键/全屏热区——
             * Keep/CSDN 动态图开屏「识别不到且无日志」头号根因）。仅开屏
             * 窗内跑，1.5s/窗一档（屏闪高频事件不重复扫；一档覆盖全部根）；
             * 命中即档案复活。 */
            boolean gProbeAllowed = false;
            if (ctx.splashLive) {
                String gKey = pkg + "|" + winStart;
                Long gLast = graphicProbeAt.get(gKey);
                long nowMs = System.currentTimeMillis();
                if (gLast == null || nowMs - gLast.longValue() >= 1500) {
                    graphicProbeAt.put(gKey, Long.valueOf(nowMs));
                    if (graphicProbeAt.size() > 48) graphicProbeAt.clear(); // 防膨胀
                    gProbeAllowed = true;
                }
            }
            for (int ri = 0; ri < roots.length; ri++) {
                Hit h = nativeTextProbe(roots[ri], ctx);
                if (h != null) {
                    profileReset(pkg, winClassOf(pkg));
                    appendLog("⚡ 探针哨兵命中（" + shortPkg(pkg) + (roots.length > 1 ? " · 窗口" + (ri + 1) : "") + "）：CLEAN 档案窗出现真「跳过」→ 档案复活，全量出手");
                    tryScan(pkg, winStart, 0); // 复用全量路径（含规则/订阅/复验/执行）
                    return;
                }
                if (gProbeAllowed && graphicProbe(roots[ri], ctx)) {
                    profileReset(pkg, winClassOf(pkg));
                    appendLog("⚡ 图形探针命中（" + shortPkg(pkg) + (roots.length > 1 ? " · 窗口" + (ri + 1) : "") + "）：CLEAN 档案窗出现图片跳过键/全屏热区（动态图开屏）→ 档案复活，全量出手");
                    tryScan(pkg, winStart, 0);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** v5.3.0 探针哨兵节流版（CLEAN 窗内容事件补扫专用：250ms 节流） */
    private void probeSentinelThrottled(final String pkg, final long winStart) {
        long now = System.currentTimeMillis();
        Long last = probeNudgeAt.get(pkg);
        if (last != null && now - last < 250) return;
        probeNudgeAt.put(pkg, now);
        final int g = -1;
        handler.post(() -> probeSentinel(pkg, winStart, g));
    }

    /* ═══ v7.15.6 图形探针（CLEAN 档案盲区根治）═══
     * 用户实锤（Keep/CSDN）：动态图开屏广告「识别不到，且日志里都没记录」
     * 头号根因——(pkg|cls) 连续 3 次零候选判 CLEAN 后只剩文本探针，而动态
     * 图开屏的跳过键是【纯图片】（无文本可探）+ 全屏可点热区 → 探不到 →
     * 不扫不点不记（72h 才复查一次）。本探针在开屏窗内补一次浅 DFS
     * （预算 120/根 ≈ 全量扫描 1/5 成本，多窗一档最多 3 根共 360；
     * 1.5s 节流一档覆盖全部根）：vid 键/角落键/泛位置键/热区/角标
     * 任一在场 = 强广告信号 → 档案复活全量出手。 */
    private boolean graphicProbe(AccessibilityNodeInfo root, Ctx ctx) {
        try {
            /* v7.15.10：预算 120→180——H5/视频广告树 300+ 节点时图片键在
             * 深层，120 预算够不到 → CLEAN 窗图形探针盲区（复活不了）。
             * 180 仍远低于全量扫描（340~600），深两层的覆盖面 vs ~0.2ms
             * 增量，功耗红线不动。 */
            ctx.budgetNodes = 180;
            ScanFind f = AdDecisionCore.walk(new NodeAdapter(root), ctx);
            return AdDecisionCore.hasActionableCand(f, ctx) || f.hotZone || f.adBadge;
        } catch (Throwable t) {
            return false;
        }
    }

    /** v7.15.6：本窗是否已确认热区（walk 见过全屏可点广告图）——nudgeScan
     *  4s 后停扫豁免的依据；窗口一切换 key 即变，天然失效。 */
    private boolean winHotZoneLive(String pkg) {
        Boolean b = hotZoneWin.get(pkg + "|" + winAt(pkg));
        return b != null && b.booleanValue();
    }

    /* ═══ v5.5.0 模块二：更新诱导弹窗双信号普查 ═══
     * 肯定侧（立即更新/立即升级…）+否定侧（以后再说/暂不更新…）
     * 同时在场才置位 ctx.updateDialog——单信号绝不出手（普通页面
     * 「以后再说」文案不点，误触红线）。仅 Dialog 形态窗（半屏/弹窗
     * 期内）调用，弹窗树浅，预算 120 零性能顾虑。 */
    private boolean detectUpdateDialogSigns(AccessibilityNodeInfo root) {
        try {
            boolean[] positive = {false};
            boolean[] negative = {false};
            int[] budget = {120};
            scanUpdateSigns(root, 0, budget, positive, negative);
            return positive[0] && negative[0];
        } catch (Throwable t) {
            return false;
        }
    }

    private void scanUpdateSigns(AccessibilityNodeInfo node, int depth, int[] budget,
                                 boolean[] positive, boolean[] negative) {
        if (node == null || depth > 24 || budget[0] <= 0
                || (positive[0] && negative[0])) return;
        budget[0]--;
        try {
            CharSequence t = node.getText();
            if (t == null || t.length() == 0) t = node.getContentDescription();
            String s = t == null ? "" : t.toString().trim();
            if (s.length() > 0 && s.length() <= 12) {
                if (com.zai.adshield.core.RuleEngine.isUpdatePositive(s)) positive[0] = true;
                if (com.zai.adshield.core.RuleEngine.isUpdateNegative(s)) negative[0] = true;
            }
            for (int i = 0; i < node.getChildCount() && !(positive[0] && negative[0]); i++) {
                scanUpdateSigns(node.getChild(i), depth + 1, budget, positive, negative);
            }
        } catch (Throwable ignored) {
        }
    }

    /* ═════════ v5.3.0 宿主窗档案（持久化：state.winProfile）═════════ */

    /** 档案记账去重：pkg|winStart → 首次记账时刻（每窗实例只记一次） */
    private final java.util.HashMap<String, Long> profileRecordedWin = new java.util.HashMap<>();

    /** 是否抑制本窗全量梯子（CLEAN 且无强信号）。
     *  强信号：Dialog/Popup 窗 · SDK 广告窗指纹 · className 学习指纹 ·
     *  学习位置在场（手动教过的坐标）· 72h 复查期到。 */
    private boolean profileSuppressScan(String pkg, String cls) {
        if (pkg == null || cls == null || cls.length() == 0) return false;
        if (isDialogClass(cls) || AdDecisionCore.isAdActivityClass(cls)) return false;
        String key = pkg + "|" + cls;
        Integer sBox = winStreak.get(key);
        int streak = sBox == null ? 0 : sBox;
        if (streak < PowerGovernor.CLEAN_STREAK_MIN) return false; // ACTIVE：全量
        Boolean learned = winClsLearned.get(pkg);
        if (learned != null && learned.booleanValue()) return false;
        int[] lp = learnedPos(pkg); // 同窗类学习位置在场 → 全量（L1.4 要用）
        if (lp[0] >= 0) return false;
        Long lastFull = winLastFull.get(key);
        long since = lastFull == null ? Long.MAX_VALUE : System.currentTimeMillis() - lastFull;
        return since < PowerGovernor.PROFILE_RECHECK_MS; // 复查期到 → 放行全量
    }

    /** 档案是否已 CLEAN（突发预算门用；与抑制判定同源但不查复查闸） */
    private boolean profileIsClean(String pkg, String cls) {
        if (pkg == null || cls == null || cls.length() == 0) return false;
        Integer sBox = winStreak.get(pkg + "|" + cls);
        return sBox != null && sBox >= PowerGovernor.CLEAN_STREAK_MIN;
    }

    /** 档案复活：探针命中/出现候选/手动教学等强信号 → 回 ACTIVE 全量 */
    private void profileReset(String pkg, String cls) {
        if (pkg == null || cls == null) return;
        String key = pkg + "|" + cls;
        Integer sBox = winStreak.get(key);
        if (sBox != null && sBox > 0) {
            winStreak.put(key, 0);
            profilePersist();
        }
    }

    /** 每窗实例一次的档案记账（streak 递增/归零 + 72h 复查闸刷新） */
    private void profileRecordOutcome(String pkg, String cls, long winStart,
                                      boolean acted, ScanFind f, Ctx ctx) {
        try {
            if (pkg == null || cls == null || cls.length() == 0) return;
            if (isDialogClass(cls) || AdDecisionCore.isAdActivityClass(cls)) return;
            String wKey = pkg + "|" + winStart;
            if (profileRecordedWin.containsKey(wKey)) return; // 每窗只记一次
            profileRecordedWin.put(wKey, System.currentTimeMillis());
            if (profileRecordedWin.size() > 96) profileRecordedWin.clear();
            String key = pkg + "|" + cls;
            winLastFull.put(key, System.currentTimeMillis());
            boolean hasActionable = acted
                    || (f != null && ctx != null && AdDecisionCore.hasActionableCand(f, ctx));
            boolean zeroCand = !hasActionable && f != null;
            Integer sBox = winStreak.get(key);
            int streak = sBox == null ? 0 : sBox;
            int next = PowerGovernor.nextStreak(streak, hasActionable, zeroCand);
            if (next != streak) {
                winStreak.put(key, next);
                boolean was = streak >= PowerGovernor.CLEAN_STREAK_MIN;
                boolean is = next >= PowerGovernor.CLEAN_STREAK_MIN;
                if (was != is) profilePersist(); // 状态翻转才落盘（罕见，零写放大）
            }
        } catch (Throwable ignored) {
        }
    }

    /** 档案持久化（写入 state.winProfile；条目上限 200 防膨胀） */
    private void profilePersist() {
        try {
            synchronized (lock) {
                org.json.JSONObject st = loadState();
                org.json.JSONObject wp = new org.json.JSONObject();
                int n = 0;
                for (java.util.Map.Entry<String, Integer> e : winStreak.entrySet()) {
                    if (e.getValue() == null || e.getValue() < 1) continue;
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("s", e.getValue());
                    Long lf = winLastFull.get(e.getKey());
                    if (lf != null) o.put("f", lf);
                    wp.put(e.getKey(), o);
                    if (++n >= 200) break;
                }
                st.put("winProfile", wp);
                saveState(st);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 档案加载（onServiceConnected：从 state.winProfile 恢复——重启后省电态不清零） */
    private void profileLoad() {
        try {
            org.json.JSONObject wp = loadState().optJSONObject("winProfile");
            if (wp == null) return;
            java.util.Iterator<String> it = wp.keys();
            while (it.hasNext()) {
                String k = it.next();
                org.json.JSONObject o = wp.optJSONObject(k);
                if (o == null) continue;
                int s = o.optInt("s", 0);
                if (s > 0) winStreak.put(k, s);
                long f = o.optLong("f", 0);
                if (f > 0) winLastFull.put(k, f);
            }
        } catch (Throwable ignored) {
        }
    }

    private void nudgeScan(final String pkg, long now) {
        Integer gen = scanGen.get(pkg);
        if (gen == null) {
            bumpScanChain(pkg);
            return;
        }
        final long winStart = winAt(pkg);
        /* v4.4.0 早退救回通道（issue#39-41 实锤「有时候触发不了」元凶）：
         * H5/WebView 开屏广告容器可滚动，首扫（400ms）时按钮还没渲染 →
         * 内容页早退杀链 → 按钮真正出现（1~2s）时 nudge 全让路 = 永远点不到。
         * 对策：被杀链窗口的内容事件以 900ms 节流降频放行（滚动风暴被节流
         * 挡住，晚到跳过键仍能救回；12s 窗口期外本就不扫）。 */
        Long killed = chainKilledAt.get(pkg);
        boolean chainDead = killed != null && killed.longValue() == winStart;
        if (chainDead) {
            Long lastRescue = rescueAt.get(pkg);
            /* v5.8.0 速度压榨：救回节流 900→600ms——被杀链窗口的晚到跳过键
             *（H5 容器 1~2s 才渲染）平均再快 0.15s，滚动风暴仍被挡。 */
            if (lastRescue != null && now - lastRescue < 600) return;
            rescueAt.put(pkg, now);
        } else {
            /* v4.3.0 功耗【关键修复】：此前「前 4s 50ms 高频补扫」的节流比较的是
             * 【窗口起点】而非【上次补扫】——信息流/聊天列表滚动时每个内容变化
             * 事件都直接排一次整树扫描（10~30 次/秒 × 4 秒 = 功耗黑洞元凶）。
             * 改为真节流：同一包两次补扫最少间隔 120ms（露头就秒跳不变——
             * 跳过键出现即点，120ms 只挡滚动风暴）。
             * v5.8.0 屏蔽速度压榨：120→80ms——补扫节流是「按钮出现→点击」
             * 的延迟上限，80ms 后平均等待减半；滚动风暴仍被挡（80ms 内
             * 一档），功耗红线（典型日 < 0.15mWh）实测保持。 */
            long elapsed = now - winStart;
            if (elapsed < 4000) {
                Long last = nudgeAt.get(pkg);
                if (last != null && now - last < 80) return;
                nudgeAt.put(pkg, now);
            } else if (now < popupUntilGet(pkg)) {
                Long last = popupNudgeAt.get(pkg); // 弹窗窗期：240ms 节流补扫（v5.8.0：300→240）
                if (last != null && now - last < 240) return;
                popupNudgeAt.put(pkg, now);
            } else if (winHotZoneLive(pkg) && elapsed <= splashDeadlineMs(winClassOf(pkg))) {
                /* v7.15.6 热区开屏窗晚到键补扫（Keep/CSDN 动态图开屏：跳过
                 * 按钮可晚至 5~12s 出现）——旧版 4s 后全停 = 晚到按钮永远
                 * 等不到扫描。仅限本窗已确认热区（walk 见过全屏可点广告图）
                 * 且仍在开屏观察窗内；500ms 节流（比杀链救回 600ms 略密，
                 * 比前期 80ms 风暴松一个量级——屏闪窗的功耗可控）。 */
                Long last = hotNudgeAt.get(pkg);
                if (last != null && now - last < 500) return;
                hotNudgeAt.put(pkg, now);
            } else {
                return;                            // 非热区开屏窗：内容变化零补扫
            }
        }
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
        if (!popupShieldEnabled() || AdDecisionCore.isOemUtilityPkg(pkg)) return;
        // v4.1.0 前台门：只对前台应用做突发检测（灵岛/悬浮窗动画的高频
        // 内容变化绝不开弹窗识别窗——乱点元凶）
        if (!pkg.equals(lastFgPkg)) return;
        /* ═══ v5.5.0 模块三：设备分级/电量联动——低端机或极低电量突发检测
         * 整体关闭（增益路径降级，事件驱动主路径不受影响）═══ */
        refreshPerfTier();
        if (!com.zai.adshield.core.ComplianceGuard.burstAllowed(
                deviceTierCache, com.zai.adshield.core.ComplianceGuard.batteryTier(
                        batteryPctCache, chargingCache))) {
            return;
        }
        /* ═══ v5.3.0 突发预算三重门（#57/#58 实锤：豆包聊天 5 分钟 7 次
         * 误报开窗——AI 流式回复/播放进度条是正常业务形态）═══
         * ① 档案 CLEAN 窗/聊天主界面的动态内容绝不开识别窗；
         * ② 每应用 10 分钟窗内最多 2 个识别窗（预算制）；
         * ③ 空手识别窗（一个弹窗没关）后阈值指数升级 3→6→12→24 事件，
         *   有战果（真关了弹窗）则立即归零——突发检测从「猜测」变「记账」。 */
        if (profileIsClean(pkg, winClassOf(pkg))) return;
        if (AdDecisionCore.isChatMainUi(pkg, winClassOf(pkg))) return;
        long[] bb = burstBudget.get(pkg);
        if (bb == null || now - bb[0] > 10 * 60 * 1000L) {
            bb = new long[]{now, 0};
            burstBudget.put(pkg, bb);
        }
        Integer escBox = burstEsc.get(pkg);
        int escLv = escBox == null ? 0 : escBox;
        long[] w = burstWin.get(pkg);
        if (w == null || now - w[0] > BURST_WIN_MS) {
            burstWin.put(pkg, new long[]{now, 1});
            return;
        }
        w[1]++;
        if (PowerGovernor.burstGate(false, false, (int) bb[1], (int) w[1], escLv)) {
            burstWin.remove(pkg);
            bb[1] = bb[1] + 1;
            energyBursts++;
            // 空手升级：上一次识别窗开了但一个弹窗没关 → 阈值升级
            Long base = burstBasePopup.get(pkg);
            if (base != null) {
                boolean found = popupBlocks > base.longValue();
                int nextLv = PowerGovernor.nextEscalate(escLv, found);
                if (nextLv != escLv) burstEsc.put(pkg, nextLv);
            }
            burstBasePopup.put(pkg, popupBlocks);
            popupUntil.put(pkg, now + POPUP_WINDOW_MS);
            // v3.14.0：日志同包 30s 去重（issue#21：meituan 60s 内 3 条同语刷屏）
            Long lastB = burstLogAt.get(pkg);
            if (lastB == null || now - lastB > 30000) {
                burstLogAt.put(pkg, now);
                appendLog("🪟 检测到内容突发（" + shortPkg(pkg) + "）：疑似覆盖层弹窗 → 开启弹窗识别窗（本 10 分钟第 "
                        + (int) bb[1] + "/" + PowerGovernor.BURST_BUDGET_PER_10MIN + " 窗 · 阈值 "
                        + (PowerGovernor.BURST_BASE_COUNT << Math.min(escLv, PowerGovernor.BURST_ESCALATE_MAX_LEVEL))
                        + " 事件）");
            }
            nudgePopupScan(pkg, now);
        }
    }

    /* ───────── 扫描与决策执行（逻辑全部在 AdDecisionCore） ───────── */

    /** v4.5.0 原生文本探针：findAccessibilityNodeInfosByText 系统原生子树搜索
     *  （大小写不敏感包含匹配，无节点预算）——DFS 预算耗尽漏掉深树按钮的
     *  兜底通道。复用 RuleEngine.matchText 全套规则 + 形状门（含菜单列表
     *  否决），与 DFS 路径零行为差异，仅搜索方式更快更深。 */
    private Hit nativeTextProbe(AccessibilityNodeInfo root, Ctx ctx) {
        try {
            Hit best = null;
            /* v7.5.0：探针词扩「skip」——英文 Skip 键（国际 App/出海 SDK
             * 开屏）旧版只能等 DFS 预算路径，深树晚到。原生直查无预算，
             * 先到先点（matchText 全套规则+形状门照常逐候选验）。 */
            for (int probe = 0; probe < 3; probe++) {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(
                        probe == 0 ? "跳过" : (probe == 1 ? "跳過" : "skip"));
                if (found == null || found.isEmpty()) continue;
                for (int i = 0; i < found.size() && i < 10; i++) {
                    AccessibilityNodeInfo n = found.get(i);
                    if (n == null) continue;
                    String text = cs(n.getText());
                    if (text.length() == 0) text = cs(n.getContentDescription());
                    if (text.length() == 0) continue;
                    NodeAdapter ad = new NodeAdapter(n);
                    int[] b = ad.bounds();
                    if (b == null || !ad.visible()) continue;
                    Hit h = RuleEngine.matchText(text, b, ad, ctx);
                    if (h != null) return h; // 命中即用（matchText 已含形状门）
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /* ───────── v7.1.0 信息流广告扫描（自 v6.2.0 已验证代码移植，薄接线） ─────────
     * FeedAdGuard 是纯函数核心（名单门/微信 sns 窗门/角标语义形态闸/P2 中性项
     * 双闸/两段式决策），服务侧只做调度与出手——所有既有防线（用户让路/耐心
     * 预算/F·H 多点防线/人手手势派发/边界终闸）经 execute() 免费复用。 */

    /** v7.2.0 信息流广告扫描（独立于开屏链）：三病灶根治后的接线。
     *  · armed（P1 出手 8s 内）直通：不看窗类/窗计时——P1 拉起的关闭菜单是
     *    Dialog/Popup 新窗，winClass 已被覆盖，P2 绝不能再看窗类脸色；
     *  · 非armed：名单门 + 微信 sns 窗门 + 2.5s 窗闸 + 1.2s 节流；
     *  · 原生直查主路径（李跳跳方法论）：findAccessibilityNodeInfosByText
     *    按角标词/中性词直查全树（预算无关——深层卡片角标也能查到），
     *    候选交 FeedAdGuard 语义+形态闸逐个判定；DFS 兜底保持；
     *  · armed 只走 P2（收菜单），绝不再点角标（菜单未及拉起时回落 P1
     *    =角标被连点 = 多点——v7.1.0 隐蔽病灶顺手根治）。 */
    private void maybeFeedScan(String pkg, long now) {
        maybeFeedScan(pkg, now, false);
    }

    /** v7.4.0 巡检来源标记：patrol=true 表示本次扫描由 2.5s 空闲巡检拉起
     *（事件静默期补扫——未命中诊断降频到 5 分钟一条，日志不刷屏）。 */
    private void maybeFeedScan(String pkg, long now, boolean patrol) {
        if (!masterOn) return;
        boolean armed = pkg.equals(feedArmedPkg)
                && now - feedArmedAt <= com.zai.adshield.core.FeedAdGuard.P1_ARM_MS;
        String cls = winClassOf(pkg);
        if (!armed && com.zai.adshield.core.FeedAdGuard.isFeedWindow(pkg, cls)) {
            lastFeedWinAt.put(pkg, now);   // P0 上下文窗记账（90s 内进过信息流窗）
            ensureFeedPatrol(pkg, cls);    // v7.4.0 空闲巡检（事件静默期照扫）
        }
        if (!armed) {
            /* ═══ v7.4.0 P0 面板直通 ═══：覆盖窗（Dialog/FrameLayout 类）+
             * 90s 内进过信息流窗 → 面板特征直查（「直接关闭」+标题/原因族证据
             * 闸）。真机日志 #79 20:15:23 实锤：用户手拉的原因面板节点就在树中
             * （直接关闭@(79%,46%)等候选可见），P1 角标识别失败也收得掉——
             * 「其他 app 内的广告主动关闭不了」的兜底闭环。 */
            if (p0Eligible(pkg, cls, now) && scanP0(pkg, now)) return;
            if (!com.zai.adshield.core.FeedAdGuard.isFeedWindow(pkg, cls)) return; // 名单门+微信 sns 窗门
            if (now - winAt(pkg) <= FEED_START_MS) return; // v7.2.0：12s→2.5s（feed 窗非开屏窗）
        }
        long throttle = armed
                ? com.zai.adshield.core.FeedAdGuard.P2_SCAN_THROTTLE_MS
                : com.zai.adshield.core.FeedAdGuard.FEED_SCAN_THROTTLE_MS;
        Long last = feedScanAt.get(pkg);
        if (last != null && now - last < throttle) return; // 节流（P2 350ms 快节奏复查）
        feedScanAt.put(pkg, now);
        try {
            android.view.accessibility.AccessibilityNodeInfo[] roots = collectRoots(pkg);
            if (roots.length == 0) return;
            String missSamples = null; // v7.4.0 诊断：角标词命中样本（未过闸也可见）
            for (android.view.accessibility.AccessibilityNodeInfo r : roots) {
                if (r == null) continue;
                com.zai.adshield.core.AdDecisionCore.NodeView rootView = new NodeAdapter(r);
                com.zai.adshield.core.FeedAdGuard.FeedDecision fd = null;
                if (armed) {
                    java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> cands =
                            nativeFindTexts(r, com.zai.adshield.core.FeedAdGuard.neutralCloseWords());
                    fd = com.zai.adshield.core.FeedAdGuard.decideP2FromCandidates(
                            cands, screenWidth, screenHeight, true);
                    if (fd == null) fd = com.zai.adshield.core.FeedAdGuard.decideP2(
                            rootView, screenWidth, screenHeight, true); // DFS 兜底
                } else {
                    java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> cands =
                            nativeFindTexts(r, com.zai.adshield.core.FeedAdGuard.BADGE_WORDS);
                    fd = com.zai.adshield.core.FeedAdGuard.decideP1FromCandidates(
                            cands, screenWidth, screenHeight);
                    if (fd == null) fd = com.zai.adshield.core.FeedAdGuard.decideP1(
                            rootView, screenWidth, screenHeight); // DFS 兜底
                    if (fd == null) {
                        /* ═══ v7.4.0 P1X 卡片直关 ═══：角标未中时补一路——
                         * 「×/关闭」直关钮（精确等值小形态）+ 同窗广告上下文证据
                         * （来自广告推荐 contains 或合法披露角标）+ 同行近距门。
                         * 真机日志 #80 20:16:55 实锤：market/ZOL 卡片的「关闭」
                         * 钮此前只能用户手动点（引擎只学到位置）。 */
                        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> ctx =
                                new java.util.ArrayList<>(cands); // 角标候选自带「来自广告推荐」类上下文
                        ctx.addAll(nativeFindTexts(r, com.zai.adshield.core.FeedAdGuard.AD_CONTEXT_WORDS));
                        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> closeCands =
                                nativeFindTexts(r, com.zai.adshield.core.FeedAdGuard.CLOSE_ICON_WORDS);
                        fd = com.zai.adshield.core.FeedAdGuard.decideP1XFromCandidates(
                                closeCands, ctx, screenWidth, screenHeight);
                    }
                    if (fd == null && missSamples == null) {
                        missSamples = candSamples(cands); // 角标词命中但被闸的样本（下轮日志直读真机形态）
                    }
                }
                if (fd != null && executeFeed(pkg, fd, now)) return;
            }
            if (!armed) logFeedMiss(pkg, cls, now, patrol, missSamples); // v7.4.0 诊断增强
        } catch (Throwable ignored) {
        }
    }

    /** v7.2.0 原生文本直查（李跳跳方法论）：findAccessibilityNodeInfosByText
     *  C++ 侧全树 contains 搜索——预算无关，深埋卡片的角标/菜单项也能查到。
     *  命中包装 NodeView 候选交 FeedAdGuard 语义+形态闸逐个判定（宁漏勿误
     *  红线不变）。 */
    private java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> nativeFindTexts(
            android.view.accessibility.AccessibilityNodeInfo root, String[] words) {
        java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> out =
                new java.util.ArrayList<>();
        if (root == null || words == null) return out;
        for (String w : words) {
            try {
                java.util.List<android.view.accessibility.AccessibilityNodeInfo> hits =
                        root.findAccessibilityNodeInfosByText(w);
                if (hits == null) continue;
                for (android.view.accessibility.AccessibilityNodeInfo h : hits) {
                    if (h != null) out.add(new NodeAdapter(h));
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** v7.4.0 空闲巡检管理：信息流窗在场时启动 2.5s 巡检链（同包同窗幂等
     *  ——快照变更才重挂；离窗/切包/masterOn 关 → 巡检自停）。 */
    private void ensureFeedPatrol(String pkg, String cls) {
        if (!masterOn || !bound) return;
        if (!com.zai.adshield.core.FeedAdGuard.isFeedWindow(pkg, cls)) return;
        if (feedPatrolLive && pkg.equals(feedPatrolPkg) && cls.equals(feedPatrolCls)) return;
        feedPatrolPkg = pkg;
        feedPatrolCls = cls;
        handler.removeCallbacks(feedPatrol);
        handler.postDelayed(feedPatrol, com.zai.adshield.core.FeedAdGuard.FEED_PATROL_MS);
        feedPatrolLive = true;
    }

    /** v7.4.0 P0 面板直通资格门：名单 App + 覆盖窗（Dialog/FrameLayout 类——
     *  非 Activity/UI 整窗）+ 90s 内进过信息流窗（微信聊天主界面零扫描红线
     *  由覆盖窗门保持：LauncherUI 是整窗，永不开 P0 直查）。 */
    private boolean p0Eligible(String pkg, String cls, long now) {
        if (!com.zai.adshield.core.FeedAdGuard.isFeedApp(pkg)) return false;
        if (cls == null || cls.length() == 0) return false;
        if (cls.contains("Activity") || cls.endsWith("UI") || cls.contains("Window")) return false; // 整窗 → 非 P0 场
        Long t = lastFeedWinAt.get(pkg);
        return t != null && now - t <= com.zai.adshield.core.FeedAdGuard.P0_FEED_CTX_MS;
    }

    /** v7.4.0 P0 面板直通扫描：面板特征词直查（直接关闭/标题族/原因族）→
     *  decideP0FromCandidates 语义+形态+证据闸 → 命中即点主按钮收面板。 */
    private boolean scanP0(String pkg, long now) {
        try {
            android.view.accessibility.AccessibilityNodeInfo[] roots = collectRoots(pkg);
            if (roots.length == 0) return false;
            for (android.view.accessibility.AccessibilityNodeInfo r : roots) {
                if (r == null) continue;
                java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> cands =
                        nativeFindTexts(r, com.zai.adshield.core.FeedAdGuard.P0_SEARCH_WORDS);
                com.zai.adshield.core.FeedAdGuard.FeedDecision fd =
                        com.zai.adshield.core.FeedAdGuard.decideP0FromCandidates(
                                cands, screenWidth, screenHeight, false);
                if (fd != null && executeFeed(pkg, fd, now)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** v7.4.0 未命中诊断样本：角标词原生直查命中的前 2 个候选（label@(cx%,cy%)）
     *  ——被闸也照登，下轮上传日志直接读到真机角标形态（「根据扫描日志进行
     *  功能实现」的数据闭环）。 */
    private String candSamples(java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> cands) {
        if (cands == null || cands.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        int w = screenWidth > 0 ? screenWidth : 1080;
        int h = screenHeight > 0 ? screenHeight : 2400;
        for (com.zai.adshield.core.AdDecisionCore.NodeView c : cands) {
            if (c == null || n >= 2) break;
            try {
                String text = c.text();
                String desc = c.desc();
                String label = (text != null && text.length() > 0) ? text : desc;
                if (label == null || label.trim().length() == 0) continue;
                int[] b = c.bounds();
                if (b == null || b.length < 4 || b[2] <= b[0] || b[3] <= b[1]) continue;
                String t = label.trim();
                if (t.length() > 12) t = t.substring(0, 12) + "…";
                if (sb.length() > 0) sb.append(" · ");
                sb.append(t).append("@(").append(Math.round(b[0] * 100.0 / w)).append("%,")
                        .append(Math.round(b[1] * 100.0 / h)).append("%)");
                n++;
            } catch (Throwable ignored) {
            }
        }
        return sb.toString();
    }

    /** v7.2.0→v7.4.0 feed 未命中诊断：事件来源 30s 节流 / 巡检来源 5 分钟
     *  节流（巡检常驻期不刷屏）；样本（角标词命中但被闸的候选）随行——
     *  上传日志直接区分「窗内无角标词命中」与「命中被闸（形态/语义）」。 */
    private void logFeedMiss(String pkg, String cls, long now, boolean patrol, String samples) {
        long gap = patrol ? 300000 : 30000;
        if (now - feedMissAt < gap) return;
        feedMissAt = now;
        feedMisses++;
        StringBuilder sb = new StringBuilder("🔍 信息流未命中（").append(shortPkg(pkg));
        if (cls != null && cls.length() > 0) sb.append(" · ").append(cls);
        sb.append("）：本窗无披露角标");
        if (samples != null && samples.length() > 0) {
            sb.append("（词命中样本：").append(samples).append("——均未过闸）");
        }
        sb.append(patrol ? "（空闲巡检）" : "");
        appendLog(sb.toString());
    }

    /** v7.1.0 feed 决策执行：构造 Decision 走既有 execute()（免费获得用户让路/耐心预算/F·H 多点防线/人手手势派发/边界终闸） */
    private boolean executeFeed(String pkg, com.zai.adshield.core.FeedAdGuard.FeedDecision fd, long now) {
        if (fd == null || fd.tapBounds == null || fd.tapBounds.length < 4) return false;
        com.zai.adshield.core.AdDecisionCore.Decision d = new com.zai.adshield.core.AdDecisionCore.Decision();
        d.action = com.zai.adshield.core.AdDecisionCore.A_CLICK_BOUNDS;
        d.reason = fd.action;          // feed_p1_badge / feed_p2_close —— execute() 按此走 feed 终闸
        d.label = fd.label;
        d.bounds = fd.tapBounds;       // 角标/菜单项【自身】bounds——绝不广告主体
        d.node = fd.node;
        Long lastClick = lastClickAt.get(pkg);
        boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
        com.zai.adshield.core.AdDecisionCore.Ctx ctx = new com.zai.adshield.core.AdDecisionCore.Ctx();
        ctx.screenW = screenWidth; ctx.screenH = screenHeight;
        ctx.pkg = pkg; ctx.activityId = winClassOf(pkg) == null ? "" : winClassOf(pkg);
        ctx.elapsedMs = now - winAt(pkg);
        ctx.budgetNodes = com.zai.adshield.core.FeedAdGuard.FEED_NODE_BUDGET;
        boolean ok = execute(pkg, d, null, ctx, clickCoolOK, now);
        if (ok) {
            if (com.zai.adshield.core.FeedAdGuard.ACTION_P1_BADGE.equals(fd.action)) {
                feedArmedPkg = pkg; feedArmedAt = now; // P1 出手 → armed 8s（P2 指纹窗）
                appendLog("🛡 信息流广告 P1（" + shortPkg(pkg) + "）：披露角标「" + fd.label + "」出手 → 等关闭菜单（8s 内）");
            } else if (com.zai.adshield.core.FeedAdGuard.ACTION_P1X_CLOSE.equals(fd.action)) {
                feedArmedPkg = pkg; feedArmedAt = now; // v7.4.0：P1X 直关钮出手 → armed（「×」可能拉起反馈菜单，P2 顺手收）
                bumpStat(pkg, "feed"); // v7.4.0：直关即关——单独记账
                appendLog("🛡 信息流广告 P1X（" + shortPkg(pkg) + "）：卡片直关钮「" + fd.label + "」出手（广告上下文闸通过）→ 本条广告关闭");
            } else if (com.zai.adshield.core.FeedAdGuard.ACTION_P0_PANEL_CLOSE.equals(fd.action)) {
                feedArmedPkg = null; // P0 直通收面板 → 收兵
                bumpStat(pkg, "feed"); // v7.4.0：面板直通记账
                appendLog("🛡 信息流广告 P0（" + shortPkg(pkg) + "）：原因面板「直接关闭」直通 → 本条广告关闭");
            } else {
                feedArmedPkg = null; // P2 完成 → 收兵
                bumpStat(pkg, "feed"); // v7.2.0：信息流关闭单独记账（不虚增 auto）
                appendLog("🛡 信息流广告 P2（" + shortPkg(pkg) + "）：中性项「" + fd.label + "」收起 → 本条广告关闭");
            }
        }
        return ok;
    }

    /* ───────── v7.5.0 插屏卫士（InterstitialGuard 薄接线） ─────────
     * 名单外 App 的插入广告（用户十四轮实锤：「app 内的插入的广告也是，
     * 它们几乎都带有广告两个字」）。原生直查（李跳跳方法论）：角标词 +
     * 关闭词一把直查 → 语义·形态·近旁闸逐候选判定；walk 只取滚动标记
     *（闸④可滚动窗归信息流两段式/开屏阶梯）。所有既有防线经 execute()
     * 免费复用（用户让路/耐心预算/F·H 多点防线/人手手势/通用边界终闸
     * ——×/关闭/跳过族全在「关闭方向」语义面内）。 */

    /** 插屏扫描入口（窗切换 + 内容变化事件驱动，2.5s 包级节流）。
     *  v7.6.0 新窗提速：窗切换（winAt 变化）即重置节流记录——插层出现当帧
     *  就扫（旧设计新窗被旧窗的 2.5s 节流卡住，插层快进快出的场景全错过）。 */
    private void maybeInterstitialScan(String pkg, long now) {
        if (!masterOn) return;
        /* ═══ v7.15.5 开屏窗让路（开屏/插屏严格分域）═══
         * 前台应用的开屏观察窗内插屏卫士零出手——开屏窗归开屏路径独占
         * （主引擎五层规则 + 订阅 + SDK 直断兜底链）；窗落定（观察窗过）
         * 后插屏扫描接管。旧版在开屏窗第一帧就跑插屏扫描：① 插屏闸面
         * （语境词/形态/近旁）对开屏窗并不适配——与开屏路径抢出手；
         * ② 开屏窗的未命中被记成「插屏未命中」——日志归因错位（用户
         * 指令：规则和日志严格区分开屏广告和插屏广告）。与 feed 让路
         * （下方名单门）、content-changed 路径的开屏让路（既有语义）对齐。
         * 例外——Dialog 窗不是开屏窗（开屏广告住 Activity 不住 Dialog）：
         * 中途弹出的 Dialog 插层（v7.15.2 强窗形免语境的主场）不受让路
         * 门影响，插屏卫士照常即时扫描（弹窗路径 10s + 插屏兜底双在岗）。 */
        String wCls0 = winClassOf(pkg);
        if (pkg.equals(lastFgPkg) && !isDialogClass(wCls0)
                && now - winAt(pkg) < splashDeadlineMs(wCls0)) return;
        // 聊天主界面/小程序容器零扫描红线（与 feed 同源门）
        if (AdDecisionCore.isChatMainUi(pkg, winClassOf(pkg))) return;
        if (MiniProgramGuard.isMiniProgramWindow(pkg, winClassOf(pkg))) return;
        String cls = winClassOf(pkg);
        // 名单门：feed 名单 20 款 App 排除（FeedAdGuard 拥有信息流两段式 +
        // P1X 直关——插屏卫士不与两段式同窗竞争出手，双重出手防线）
        if (com.zai.adshield.core.FeedAdGuard.isFeedWindow(pkg, cls)) return;
        // armed 指纹窗内绝不出手（P2 收菜单优先——与 feed 两段式同语义）
        if (pkg.equals(feedArmedPkg)) return;
        /* v7.6.0 新窗提速：窗已切（winAt 变化）→ 清节流记录，新窗首扫立即。
         * v7.15.11 同步清退避计数（新窗新计——新插层照常秒级响应）。 */
        long wAt = winAt(pkg);
        Long lastWin = interWinAt.get(pkg);
        if (lastWin == null || lastWin.longValue() != wAt) {
            interWinAt.put(pkg, wAt);
            interScanAt.remove(pkg);
            interMissStreak.remove(pkg);
            if (interWinAt.size() > 64) interWinAt.clear(); // 防膨胀
        }
        /* v7.15.11 窗级退避（#106 OPPO 实锤功耗：fenglink66p 静态窗内容事件
         * 不停 → 2.5s 一轮无限重扫）：同窗连续未命中 streak≤2 照常 2.5s、
         * 3~5 次 10s、≥6 次 30s 封顶（扫描量降 12~24 倍；插层出现必是
         * 新窗/新事件洪峰——退避不误新插层）。 */
        Integer missStreak = interMissStreak.get(pkg);
        int mst = missStreak == null ? 0 : missStreak.intValue();
        long interThrottleMs = mst >= 6 ? 30_000L : (mst >= 3 ? 10_000L
                : com.zai.adshield.core.InterstitialGuard.INTER_SCAN_THROTTLE_MS);
        Long last = interScanAt.get(pkg);
        if (last != null && now - last < interThrottleMs) return;
        interScanAt.put(pkg, now);
        if (interScanAt.size() > 64) interScanAt.clear(); // 防膨胀
        try {
            android.view.accessibility.AccessibilityNodeInfo[] roots = collectRoots(pkg);
            if (roots.length == 0) return;
            String missSamples = null; // v7.6.0 诊断：语境词+关闭词命中样本（未过闸也可见）
            for (android.view.accessibility.AccessibilityNodeInfo r : roots) {
                if (r == null) continue;
                com.zai.adshield.core.AdDecisionCore.NodeView rootView = new NodeAdapter(r);
                /* 滚动标记（walk 轻量预算——只取 scrollPresent：滚动窗（信息流
                 * 卡片）关闭钮须与广告语境节点近旁（|Δcy|≤35% 同排铁证）。 */
                com.zai.adshield.core.AdDecisionCore.Ctx wctx = new com.zai.adshield.core.AdDecisionCore.Ctx();
                wctx.screenW = screenWidth;
                wctx.screenH = screenHeight;
                wctx.pkg = pkg;
                wctx.activityId = cls == null ? "" : cls;
                wctx.budgetNodes = 300;
                com.zai.adshield.core.AdDecisionCore.ScanFind f =
                        com.zai.adshield.core.AdDecisionCore.walk(rootView, wctx);
                java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> ctxCands =
                        nativeFindTexts(r, com.zai.adshield.core.InterstitialGuard.INTER_CONTEXT_WORDS);
                java.util.ArrayList<com.zai.adshield.core.AdDecisionCore.NodeView> closeCands =
                        nativeFindTexts(r, com.zai.adshield.core.InterstitialGuard.INTER_SEARCH_WORDS);
                /* v7.15.2 强窗形免语境：SDK 广告窗指纹 / Dialog 形态本身就是广告铁证
                 * ——无角标插屏/返回开屏窗不再被「无语境词」一票否决。
                 * v7.15.6 冗余清理：第三项原为 isAdActivityClass(winClassOf(pkg))
                 * ——cls 即 winClassOf(pkg)（上方赋值），与第一项恒等。 */
                boolean strongWinForm = com.zai.adshield.core.AdDecisionCore.isAdActivityClass(cls)
                        || isDialogClass(cls);
                com.zai.adshield.core.FeedAdGuard.FeedDecision fd =
                        com.zai.adshield.core.InterstitialGuard.decideFromCandidates(
                                ctxCands, closeCands, screenWidth, screenHeight, f.scrollPresent, strongWinForm);
                if (fd != null && executeInterstitial(pkg, fd, now)) {
                    interMissStreak.remove(pkg); // v7.15.11：出手成功清退避
                    return;
                }
                if (missSamples == null) {
                    // v7.6.0：语境词与关闭词命中样本都登（被闸也照登）——上传日志
                    // 直接定位「语境不在场」还是「关闭钮被哪道闸拒」
                    String cs = candSamples(ctxCands);
                    String ks = candSamples(closeCands);
                    if (cs.length() > 0 || ks.length() > 0) {
                        missSamples = (cs.length() > 0 ? cs : "无语境词") + " / 关闭钮 " + ks;
                    }
                }
            }
            logInterMiss(pkg, cls, now, missSamples);
            /* v7.15.11：同窗连续未命中计数 +1（退避梯子依据——见上方节流） */
            Integer st0 = interMissStreak.get(pkg);
            interMissStreak.put(pkg, Integer.valueOf(st0 == null ? 1 : st0.intValue() + 1));
            if (interMissStreak.size() > 32) interMissStreak.clear(); // 防膨胀
        } catch (Throwable ignored) {
        }
    }

    /** v7.6.0 插屏决策执行：构造 Decision 走既有 execute()（插屏专用终闸
     *  interBoundaryCheck——「知道了/残忍离开/不再看」等插屏关闭词的语义面
     *  收编；用户让路/耐心/多点防线/人手手势全复用）。 */
    private boolean executeInterstitial(String pkg, com.zai.adshield.core.FeedAdGuard.FeedDecision fd, long now) {
        if (fd == null || fd.tapBounds == null || fd.tapBounds.length < 4) return false;
        com.zai.adshield.core.AdDecisionCore.Decision d = new com.zai.adshield.core.AdDecisionCore.Decision();
        d.action = com.zai.adshield.core.AdDecisionCore.A_CLICK_BOUNDS;
        d.reason = com.zai.adshield.core.InterstitialGuard.ACTION_CLOSE; // inter_close —— 通用终闸
        d.label = fd.label;
        d.bounds = fd.tapBounds;       // 关闭钮【自身】bounds——绝不广告主体、绝不角标
        d.node = fd.node;
        Long lastClick = lastClickAt.get(pkg);
        boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
        com.zai.adshield.core.AdDecisionCore.Ctx ctx = new com.zai.adshield.core.AdDecisionCore.Ctx();
        ctx.screenW = screenWidth; ctx.screenH = screenHeight;
        ctx.pkg = pkg; ctx.activityId = winClassOf(pkg) == null ? "" : winClassOf(pkg);
        ctx.elapsedMs = now - winAt(pkg);
        ctx.budgetNodes = com.zai.adshield.core.FeedAdGuard.FEED_NODE_BUDGET;
        boolean ok = execute(pkg, d, null, ctx, clickCoolOK, now);
        if (ok) {
            bumpStat(pkg, "feed"); // App 内广告关闭口径（与信息流同档——报告饼图第 5 档）
            appendLog("🛡 插屏广告（" + shortPkg(pkg) + "）：「" + fd.label + "」关闭钮出手（「广告」语境在场闸通过——v7.6.0 激进模式）→ 本条广告关闭");
        }
        return ok;
    }

    /** v7.6.0 插屏未命中诊断（30s 包级节流——扫描日志上传可定位：语境不在场/
     *  关闭钮被哪道闸拒）。语境词与关闭词命中样本都登（被闸也照登）——
     *  与 feed 未命中同规约的数据闭环（随一日三次上传）。 */
    private void logInterMiss(String pkg, String cls, long now, String samples) {
        Long last = interMissAt.get(pkg);
        if (last != null && now - last < 30000L) return;
        interMissAt.put(pkg, now);
        if (interMissAt.size() > 32) interMissAt.clear(); // 防膨胀
        interMisses++;
        StringBuilder sb = new StringBuilder("🔍 插屏未命中（").append(shortPkg(pkg));
        if (cls != null && cls.length() > 0) sb.append(" · ").append(shortCls(cls));
        sb.append("）：广告语境/关闭钮未同场过闸");
        if (samples != null && samples.length() > 0) {
            sb.append("（样本：").append(samples).append("——均未过闸）");
        }
        sb.append(" ——上传日志可帮我们补规则");
        appendLog(sb.toString());
    }

    private void tryScan(String pkg, long winStart, long atMs) {
        if (instance == null) return;
        // v4.1.0：主开关暂停 / 相机窗 —— 扫描链里排着的任务一律作废
        if (!masterOn) return;
        // v4.3.0：白名单应用里排着的扫描任务一律作废（纵深防御）
        if (whitelistSet.contains(pkg)) return;
        if (AdDecisionCore.isCameraPkg(pkg) || AdDecisionCore.isCameraClass(winClassOf(pkg))) return;
        // v4.2.0 聊天主界面门：微信 LauncherUI（主界面）没有任何跳过键——
        // 绝不扫描（订阅规则/角落键/热区全让路），聊天列表高频内容变化
        // 也不再触发整树扫描（「进入微信左右乱划」根治 + 功耗大降）。
        // Dialog 弹窗（青少年模式等）是独立窗口类，不受此门影响。
        if (AdDecisionCore.isChatMainUi(pkg, winClassOf(pkg))) return;
        // v5.0.0 小程序容器保护（L0 第二层）：扫描链里排着的任务一律作废
        //（进入容器前排定的扫描针，出手前再验一次窗口身份）
        if (MiniProgramGuard.isMiniProgramWindow(pkg, winClassOf(pkg))) return;
        /* ═══ v5.3.0 档案省电：CLEAN 窗的任何补扫/救回/弹窗补扫请求（atMs<0）
         * 降级为探针哨兵（250ms 节流）——零 DFS；固定梯子档（atMs≥0）只在
         * 非抑制窗或晚档轻扫梯子 {0,700,1600,3200}/{700,1600,3200} 里排到
         * （bumpScanChain 已保证）。 ═══ */
        if (atMs < 0 && profileSuppressScan(pkg, winClassOf(pkg))) {
            probeSentinelThrottled(pkg, winStart);
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - winStart;
        // v4.1.0 前台门：开屏扫描链只对前台应用生效（非前台包的扫描
        // 请求一律降级为弹窗路径——绝不拿灵岛/悬浮窗树走开屏决策阶梯）
        /* ═══ v7.15.5【头号回归根治】解除「习惯学习」开关对 splashLive 的
         * 误门控（用户实锤：很多 App 开屏的广告跳不了，日志也没有记录）═══
         * 死因：v7.15.0 版本线重制基于 v7.6.0 基线重构时，把旧时间线
         * v8.1.0 的本项修复丢了——learningEnabled() 又回来门控整个开屏扫描。
         * 用户关掉「习惯学习」（设置页文案只说学习，无从知晓它管着全部
         * 跳过）= 所有 App 开屏零扫描、零点击、零日志（logScanMiss 也住在
         * splashLive 分支里——「日志也没有记录」与「跳不了」同源同灭）；
         * 而学习写入 learnFromManualClick 反而没人管（名实完全颠倒）。
         * 现在恢复 v8.1.0 语义：开关只管学习写入路径（本方法下方的
         * learnFromManualClick 入口门控），开屏扫描由 masterOn 全局开关管。
         * 观察窗同期分域：普通窗 12s；SDK 广告窗 20s（splashDeadlineMs
         * ——视频开屏的跳过键常在视频末 5~15s 才露头）。 */
        boolean splashLive = elapsed <= splashDeadlineMs(winClassOf(pkg)) && pkg.equals(lastFgPkg);
        boolean popupLive = popupShieldEnabled() && now < popupUntilGet(pkg) && popupCooldownOK(pkg, now);
        if (!splashLive && !popupLive) return;
        // v4.3.0 功耗：内容页早退后本窗固定扫描链已取消（nudge 补扫同步让路；
        // 内容变化里的晚出跳过键若真出现，walk 会重新发现候选并复活扫描）
        Long killed = chainKilledAt.get(pkg);
        boolean chainDead = killed != null && killed.longValue() == winStart;
        if (chainDead && atMs >= 0) return;

        try {
            /* 多窗口扫描：活动窗口优先，其次 getWindows() 同包窗口（弹窗/覆盖层）
             * ═══ v7.15.8【事故修复】两段式多窗扫描（v7.15.7 服务级瘫痪根治）═══
             * v7.15.7 死因：此处曾无条件传 splashLive=true 枚举全部窗口并对每个
             * 窗口 getRoot()——MIUI 20+ 窗 × 屏闪事件 80ms 节流补扫（12.5 轮/秒）
             * + 18 档梯子 = 每秒 250+ 次 binder 往返 → 事件派发超时 → 系统杀
             * 服务 → 「日志零记录、开屏全不跳」整体瘫痪（真机实锤）。
             * 现在首段只扫活动窗（与 v7.15.6 byte-for-byte 同路径，普通应用
             * 零增量零回归）；循环尾部微树门（活动窗 ≤48 节点且零候选——
             * Keep 型「跳过键住覆盖层、活动窗只剩 8 节点空壳」铁签名）才
             * collectExtraRoots 补窗（400ms 节流 + 类型过滤，见常量区复盘）。 */
            AccessibilityNodeInfo[] roots = collectRoots(pkg, false);
            if (roots.length == 0) return;
            java.util.ArrayList<AccessibilityNodeInfo> rootsL =
                    new java.util.ArrayList<>(java.util.Arrays.asList(roots));

            Long lastClick = lastClickAt.get(pkg);
            boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
            // v3.5.0：学习位置指纹（手动教过的坐标）与复验次数
            int[] tapHint = learnedPos(pkg);
            Integer triesBox = clickTries.get(pkg);
            int clickAttempts = triesBox == null ? 0 : triesBox.intValue();
            boolean acted = false;           // v4.3.0：本轮是否出手/等待（未命中日志用）
            ScanFind lastFind = null;        // v4.3.0：最后一个根的扫描画像（同上）
            Ctx lastCtx = null;              // v5.1.0：最后一个根的决策上下文（零候选退避用）
            int nodesSum = 0;                // v7.15.7：多根节点总数（未命中日志「树 8+42 节点」）
            StringBuilder nodesDetail = new StringBuilder(12); // v7.15.8：每根节点明细（多根时「8+42」）

            /* ═══ v3.14.0 跳过后反馈弹窗（issue#21 实锤：喜马拉雅跳过后 10 次
             * 有 8 次弹「不喜欢/反馈投诉」底部面板）——反馈面板在场 = 广告
             * 已关的铁证：① 停用换候选重试（复验风暴元凶）；② 代点「取消」
             * 收起面板。仅 12s 内自动跳过过或本窗复验中的包生效（手动长按
             * 菜单绝不碰）；命中即接管本轮，绝不透过面板点广告残留跳过键 ═══ */
            if (handleFeedbackSheet(pkg, roots, clickAttempts, now)) return;

            for (int ri = 0; ri < rootsL.size(); ri++) {
                // v3.13.0 验证码横幅防护②：树内任一节点带「验证码/校验码/
                // 动态码/动态密码」文案（SystemUI 横幅窗/应用内自绘提示条/
                // 银行登录页）——本窗口绝不扫描、绝不点击、绝不学习。
                // 预算 60：横幅窗树极浅，零性能顾虑
                if (AdDecisionCore.treeHasCodeBannerMarker(new NodeAdapter(rootsL.get(ri)), 60)) {
                    codeBannerGuardLog(pkg, now);
                    continue;
                }
                /* v5.0.0 小程序容器保护（树标识兜底）：类名未知的漏网容器
                 * （树内 miniapp/appbrand/nebula vid 或「小程序」标题）
                 * 同样保护——只跳过本根的决策，不影响其他窗口。 */
                if (MiniProgramGuard.treeHasMiniProgramMarker(new NodeAdapter(rootsL.get(ri)), 80)) {
                    miniGuardLog(pkg, now);
                    continue;
                }
                energyScans++;
                Ctx ctx = new Ctx();
                ctx.screenW = screenWidth;
                ctx.screenH = screenHeight;
                ctx.pkg = pkg; // v3.10.0：分应用规则表
                ctx.activityId = winClassOf(pkg); // v4.2.0：GKD 订阅规则 activityIds 限定
                /* v7.1.0 底层算法优化：分应用规则行预筛（每窗一次）——探针/DFS
                 * 双路径共享，每节点零全表前缀比对（~340 节点 × 50 行 → 一次） */
                ctx.appTextRules = com.zai.adshield.core.RuleEngine.rowsFor(pkg);
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
                /* ═══ v5.5.0 模块二：新场景标志（半屏插屏/更新诱导弹窗/视频前贴）═══
                 * videoApp：视频类应用包名表命中（前贴 vid 键 + L2 预算）；
                 * halfScreenAd：底部半屏 Dialog（高 ≤60% 屏且贴底）——close
                 *   族 vid 放开 SDK 指纹要求（半屏 Dialog 本身即强广告信号）；
                 * updateDialog：肯定+否定侧文案【双信号】在场才置位——否定侧
                 *   （以后再说/暂不更新…）仅此场景出手，误触红线不破。 */
                ctx.videoApp = com.zai.adshield.core.RuleEngine.isVideoApp(pkg);
                try {
                    android.graphics.Rect rootR = new android.graphics.Rect();
                    rootsL.get(ri).getBoundsInScreen(rootR);
                    ctx.halfScreenAd = rootR.height() > 0 && screenHeight > 0
                            && rootR.height() <= screenHeight * 6 / 10
                            && rootR.bottom >= screenHeight * 9 / 10;
                } catch (Throwable ignored) {
                }
                if (splashLive || popupLive || ctx.halfScreenAd) {
                    ctx.updateDialog = detectUpdateDialogSigns(rootsL.get(ri));
                }
                /* ═══ v5.5.0 模块三：设备分级 + 电量联动动态降级 ═══
                 * 低端机低电量 → 扫描预算 ×0.6、突发检测关闭；仅作用于
                 * 增益路径（事件驱动首扫与探针前置永不降级——命中率红线）。
                 * 电量 sticky 广播 60s 缓存，热路径零成本。 */
                refreshPerfTier();
                double perfFactor = com.zai.adshield.core.ComplianceGuard.budgetFactor(
                        deviceTierCache, com.zai.adshield.core.ComplianceGuard.batteryTier(
                                batteryPctCache, chargingCache));
                // v4.3.0 功耗分层 / v4.4.0 修正：早档【固定档 0~400ms】（广告窗
                // 刚渲染树小）340 提速；中档 600（晚出键的深树覆盖）；晚档
                // （固定档 ≥3.6s，真广告早该出手，深树几乎必是内容页）360。
                // ⚠ atMs=-1（nudge 补扫/门控开闸针/快速复验针）必须保持 600：
                // H5 开屏树深 500+，救回通道 340 预算够不到晚到按钮 =
                // 「有时候触发不了」复发（v4.4.0 首版误伤，此为修正）。
                // v5.5.0 非开屏窗口接入 SmartScanPlanner 分级（李跳跳式预算
                // 治理）：SDK/学习窗 600 全量 · 新场景/弹窗期/视频 400 ·
                // 普通窗 350（探针前置已保文本键零 DFS，预算仅影响 vid 键
                // DFS 深度——性能开销 ≤10% 红线的保障）
                ctx.budgetNodes = splashLive
                        ? (atMs >= 0 && atMs <= 400 ? 340 : (atMs >= 3600 ? 360 : 600))
                        : (atMs == -2 ? 280 : com.zai.adshield.core.SmartScanPlanner.budgetFor(ctx));
                /* v7.15.6 冗余修复：低端机低电量预算降级（×0.6，下限 120）此前
                 * 写在主赋值【之前】被无条件覆盖——纯死存储白算。移到主赋值
                 * 之后真正生效（v5.5.0 模块三的设计意图：增益路径降级，而
                 * 事件驱动首扫/探针前置不在此处）。 */
                if (perfFactor < 1.0) {
                    ctx.budgetNodes = Math.max(120, (int) (ctx.budgetNodes * perfFactor));
                }

                /* ═══ v3.9.0 主路径：内嵌规则引擎 0ms 出手 ═══
                 * 规则只认控件属性（text/desc/vid），点击的是控件自身 bounds 中心
                 * ——应用按实际屏幕渲染，适配任意分辨率，绝无固定坐标盲点。
                 * Keep 开屏「跳过」文本键：窗口事件 0ms 首扫即中即点=露头秒跳。
                 * v3.10.0：五层规则（分应用表/全局精确/倒计时形态/短包含+诱导否决/
                 * vid skip 族+SDK 窗 close 族）
                 * v4.3.0 多候选轮换：matchAll 一次收集全部命中，按 clickAttempts
                 * 轮换出手——此前每次重扫都返回同一个首个命中，复验点同一坐标
                 * ×6（issue#38 喜马拉雅实锤「换第 N 个候选」日志是假的）。
                 * 订阅命中按需追加（文本候选用尽才轮到订阅 vid 候选——
                 * SubscriptionManager 的限流记账只在真正出手时消耗）。 */
                if (splashLive || popupLive) {
                    /* ═══ v5.3.0 探针前置（李跳跳核心路径优先）═══
                     * findAccessibilityNodeInfosByText 是系统原生 C++ 子树搜索，
                     * 成本≈DFS 扫描的 1/14——先探针后 DFS：文本类广告全流程
                     * 零树遍历（#57/#58 实测 2396 次扫描的大头就是文本键广告
                     * 上的重复 DFS）；探针不中再走五层规则 DFS（能力不减）。 */
                    java.util.ArrayList<Hit> hits = new java.util.ArrayList<Hit>();
                    energyProbes++;
                    Hit probeHit = nativeTextProbe(rootsL.get(ri), ctx);
                    if (probeHit != null) hits.add(probeHit);
                    if (hits.isEmpty() && splashLive) {
                        hits = RuleEngine.matchAll(new NodeAdapter(rootsL.get(ri)), ctx, 4);
                    }
                    boolean needSub = hits.isEmpty() || clickAttempts >= hits.size();
                    if (needSub) {
                        try {
                            /* v7.1.0 两源订阅候选（RuleRouter 使用顺序：市场优先 →
                             * 内置兑底；各贡献一个候选进点击轮换——单源候选失效
                             * 不再拖死整轮，市场规则不再被内置静态快照短路饿死） */
                            java.util.ArrayList<Hit> sh = SubscriptionManager.matchSources(
                                    pkg, new NodeAdapter(rootsL.get(ri)), ctx);
                            if (!sh.isEmpty()) hits.addAll(sh);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (!hits.isEmpty()) {
                        /* v4.2.0：订阅规则的 label 是规则名（gkd:微信/组-1），
                         * 绝不能走倒计时解析（issue#34-37 日志实锤：规则名尾号
                         * "-1" 被误读成「跳过键 1s 后激活」白等 0.5s+）。倒计时
                         * 门控只对主引擎文案规则的【真实按钮文案】生效。 */
                        int idx = Math.min(clickAttempts, hits.size() - 1);
                        Hit hit = hits.get(idx);
                        int cdHit = "sub_gkd".equals(hit.appId) ? 0 : AdDecisionCore.parseCountdown(hit.label);
                        /* ═══ v7.5.0 兄弟节点倒计时（用户实锤：开屏按钮跳过
                         * 不是每个都带倒计时——数字与「跳过」分属两个节点时
                         * parseCountdown(label)=0，首击白点烧耐心预算）：窗级
                         * 独立数字（3/5s/3秒）在倒计时区或命中键近旁 → 并入
                         * 门控（出手时机=倒计时将尽，出手即命中）。═══ */
                        if (cdHit == 0 && !"sub_gkd".equals(hit.appId)) {
                            cdHit = com.zai.adshield.core.RuleEngine.windowCountdownHint(
                                    new NodeAdapter(rootsL.get(ri)), ctx, hit.tapBounds);
                        }
                        if (!countdownWait(pkg, cdHit, clickAttempts, now)) {
                            Decision rd = new Decision();
                            rd.action = AdDecisionCore.A_CLICK_BOUNDS;
                            rd.node = hit.gestureOnly ? null : hit.clickNode;
                            rd.bounds = hit.tapBounds;
                            /* v7.1.0：订阅命中带源标注（市场优先/内置兑底）——
                             * 日志与 reasonZh 可诊断（订阅页两源命中也分别记账）。
                             * v7.15.5：再携组名（GKD 组「开屏广告」「插屏广告」
                             * 等——reasonZh 解析后日志严格区分广告类型）。 */
                            rd.reason = "sub_gkd".equals(hit.appId)
                                    ? ("rule_sub_gkd_" + (String.valueOf(hit.src).startsWith("市场") ? "remote" : "builtin")
                                        + (hit.group != null && hit.group.length() > 0 ? "·" + hit.group : ""))
                                    : "rule_" + hit.appId;
                            /* v5.5.1（issue#64 微博误杀根治）：命中文案随 Decision
                             * 传递——gestureOnly（无可点祖先，微博图片跳过键常态）
                             * 时边界自检拿不到空 label，凭规则已验证的「跳过广告」
                             * 文案通过语义断言（非跳过文案依旧一票否决，零放行面） */
                            rd.label = hit.label;
                            if (execute(pkg, rd, null, ctx, clickCoolOK, now)) return; // 命中即熔断
                            acted = true; // 冷却/让路暂未出手，但场景已确认
                        } else {
                            acted = true; // 倒计时等待中
                        }
                        // 规则已命中（或倒计时等待中）——本窗就是标准跳过场景，
                        // 不再走决策核心兜底（避免角落键/热区在正常窗乱点）
                        continue;
                    }
                }

                /* ═══ 兜底路径：决策核心（复杂信号）═══ */
                ScanFind f = AdDecisionCore.walk(new NodeAdapter(rootsL.get(ri)), ctx);
                lastFind = f;
                lastCtx = ctx;
                nodesSum += f.nodeCount; // v7.15.7：多根合计（覆盖层窗口的树规模可见）
                if (nodesDetail.length() > 0) nodesDetail.append('+'); // v7.15.8：每根明细
                nodesDetail.append(f.nodeCount);
                /* v7.15.6 热区记账：本窗确认过全屏可点广告图 → nudgeScan
                 * 的 4s 后停扫豁免依据（动态图开屏的晚到跳过键 5~12s
                 * 仍可救回；窗口一切换 key 变化自动失效）。 */
                if (f.hotZone) {
                    String hk = pkg + "|" + winStart;
                    hotZoneWin.put(hk, Boolean.TRUE);
                    if (hotZoneWin.size() > 24) hotZoneWin.clear(); // 防膨胀
                }
                /* v3.8.0 倒计时门控（issue#10 二轮·喜马拉雅）：穿山甲类 SDK 倒计时
                 * 结束前跳过键不响应——「已点击×4 不跳转」真凶之一（提前点全白点）。
                 * v3.9.0 抽取为 countdownWait()（规则路径/兜底路径共用） */
                if (countdownWait(pkg, f.countdownN, clickAttempts, now)) {
                    acted = true;
                    continue;
                }
                Decision d = AdDecisionCore.decide(f, ctx);
                if (!d.action.equals(AdDecisionCore.A_NONE)) {
                    if (execute(pkg, d, f, ctx, clickCoolOK, now)) return; // 命中即熔断
                    acted = true;
                } else if (splashLive && !acted && elapsed >= 2000 && chainCanQuit(pkg, f)) {
                    /* ═══ v4.3.0 功耗：内容页早退（v4.4.0 收紧）═══
                     * 可滚动/有输入框/长文本且零跳过候选 = 正常内容页（信息流/
                     * 聊天/文章）——本窗剩余固定扫描档全部取消（省 10+ 次整树
                     * 遍历/次启动）。
                     * v4.4.0 双重收紧（issue#39-41 实锤 H5 开屏被误杀 =
                     * 「有时候触发不了」元凶）：
                     *  · 400ms → 2000ms 才允许杀（真开屏广告按钮最晚 1~2s
                     *    出现——H5 渲染慢，过早杀链 = 主动放弃跳过）；
                     *  · 广告窗/学习窗/Dialog 窗绝不杀（窗口本身就是广告）；
                     *  · 杀链后内容事件由 900ms 救回通道降频放行（nudgeScan）。 */
                    chainKilledAt.put(pkg, winStart);
                }
                /* ═══ v7.15.8【事故修复后保留】微树多窗补扫（两段式第二段）═══
                 * 门控五重全过才补窗：① 开屏窗内（splashLive）；② 本轮零
                 * 出手（acted=false）；③ 当前根是【最后一根】且树 ≤48 节点
                 * （MULTI_WIN_MICRO_TREE_MAX——Keep 型「活动窗只剩 8 节点
                 * 空壳、跳过键住覆盖层」铁签名；正常窗 100+ 节点永不触发）；
                 * ④ 五类候选全空（真零信号，有候选已在上方正常出手）；
                 * ⑤ 400ms 枚举节流（v7.15.9 起扫描门专用账 microScanEnumOk
                 * ——与探针账分账：v7.15.8 共用账被探针前置饿死，#102 实锤
                 * 全程零🪟，weibo 6 节点/keep 8 节点空壳都没触发补扫）。
                 * 补窗后循环继续（ri 递增自然扫到新根：规则探针/DFS/执行
                 * 全路径复用）；补窗成功打点 multiWinLog（每窗一条，
                 * Keep 型覆盖层问题日志直接可诊断）；无窗可补也打点
                 * microScanEmptyLog（区分「门被饿死」与「无同包覆盖层」）。 */
                if (ri == rootsL.size() - 1 && rootsL.size() < MAX_WINDOWS_SCAN
                        && splashLive && !acted
                        && f.nodeCount > 0 && f.nodeCount <= MULTI_WIN_MICRO_TREE_MAX
                        && f.skipCands.isEmpty() && f.fakeCands.isEmpty()
                        && f.corners.isEmpty() && f.randCands.isEmpty() && !f.foundClose
                        && microScanEnumOk(pkg)) {
                    AccessibilityNodeInfo[] extra = collectExtraRoots(pkg, rootsL);
                    if (extra.length > 0) {
                        /* 审核轮加固：补窗根也要过 v3.14.0 反馈面板终门（喜马拉雅式
                         * 「跳过后反馈面板」若住在覆盖层窗，不重验就会绕过防线）。
                         * 命中即接管本轮返回，绝不透过面板点广告残留跳过键。 */
                        if (handleFeedbackSheet(pkg, extra, clickAttempts, now)) return;
                        int before = rootsL.size();
                        for (AccessibilityNodeInfo e : extra) {
                            if (rootsL.size() >= MAX_WINDOWS_SCAN) break;
                            rootsL.add(e);
                        }
                        multiWinLog(pkg, winStart, f.nodeCount, rootsL.size() - before);
                    } else {
                        /* v7.15.9：门开了但无窗可补也要留痕（每窗一条）——否则
                         * 「饿死」与「无覆盖层」在日志里无从区分，下次排障
                         * 还是盲猜。 */
                        microScanEmptyLog(pkg, winStart, f.nodeCount);
                    }
                }
            }

            /* ═══ v5.1.0 零候选退避（issue#46/47 实锤）═══
             * 校园/工具类 App 校徽启动页（school.app 9 秒 7 条未命中）与
             * 商店首页零候选后仍被固定档反复整树扫描——纯功耗 + 无谓风险面。
             * 同窗连续 3 次零候选（微树 2 次）且过 2.6s（微树 1.2s）→ 本窗
             * 剩余固定档取消；nudge 救回通道不受影响——晚到真跳过键出现
             * 即触发内容事件，照点不误（与 v4.3.0 内容页早退同语义）。
             * 豁免：广告窗/学习窗/Dialog 窗（窗口本身就是广告，按钮可能
             * 晚到 1~2s——绝不退避，H5 开屏教训 v4.4.0 不重蹈）。 */
            if (!acted && splashLive && lastFind != null && lastCtx != null) {
                // v5.1.0 零候选判定：角落候选须过 L4 广告形态门才算数（school.app
                // 实锤：校徽页 fl_scan 扫码键是角落形态但整窗无广告信号，
                // L4 拒点——它不该让退避失灵）
                boolean zeroCand = !AdDecisionCore.hasActionableCand(lastFind, lastCtx);
                String zKey = pkg + "|" + winStart;
                if (zeroCand) {
                    String zCls = winClassOf(pkg);
                    boolean exempt = AdDecisionCore.isAdActivityClass(zCls)
                            || isDialogClass(zCls)
                            || (winClsLearned.get(pkg) != null && winClsLearned.get(pkg).booleanValue());
                    if (!exempt) {
                        if (zeroCandScans.size() > 96) zeroCandScans.clear(); // 防膨胀
                        Integer zBox = zeroCandScans.get(zKey);
                        int zCount = (zBox == null ? 1 : zBox.intValue() + 1);
                        zeroCandScans.put(zKey, zCount);
                        if (AdDecisionCore.zeroCandBackoff(zCount, now - winStart, lastFind.nodeCount)) {
                            chainKilledAt.put(pkg, winStart);
                            zeroCandScans.remove(zKey);
                        }
                    }
                } else {
                    zeroCandScans.remove(zKey); // 出现候选即重置（救回通道复活计数）
                }
            }

            /* ═══ v4.3.0 扫描未命中诊断日志（每窗一条）═══
             * issue#38「许多第三方 App 无法跳过，日志也没记录」根治：开屏窗
             * 扫完零命中零出手时，记一条带窗口类名/树规模/可点控件样本的诊断
             * 日志——用户上传日志即可定位缺哪种规则（此前静默失败无从查起）。 */
            AccessibilityNodeInfo[] rootsFinal = rootsL.toArray(new AccessibilityNodeInfo[0]);
            if (!acted && splashLive) logScanMiss(pkg, rootsFinal, lastFind, nodesSum, nodesDetail.toString(), now, winStart, false);
            else if (!acted && popupLive) logScanMiss(pkg, rootsFinal, lastFind, nodesSum, nodesDetail.toString(), now, winStart, true);

            /* ═══ v5.3.0 宿主窗档案记账（每窗实例一次，CLEAN 判定依据）═══
             * 零候选（无可出手信号）→ 连续计数 +1；出现候选/已出手 → 归零
             * （下次全量梯子）；首次全量扫描刷新 72h 复查闸。Dialog/SDK 广告窗
             * /学习窗不参与（suppressLadder 已永久放行，无需记账）。 */
            profileRecordOutcome(pkg, winClassOf(pkg), winStart, acted, lastFind, lastCtx);
        } catch (Exception ignored) {
        }
    }

    /** v4.4.0：内容页早退判定（零候选 + 内容页特征三选一）。
     *  v4.4.0：广告窗/学习窗/Dialog 窗绝不早退——窗口本身就是广告，
     *  按钮只是还没渲染（H5 开屏 1~2s），杀链 = 主动放弃跳过。 */
    private boolean chainCanQuit(String pkg, ScanFind f) {
        String cls = winClassOf(pkg);
        if (AdDecisionCore.isAdActivityClass(cls) || isDialogClass(cls)) return false;
        Boolean learned = winClsLearned.get(pkg);
        if (learned != null && learned.booleanValue()) return false;
        boolean noCand = f.skipCands.isEmpty() && f.fakeCands.isEmpty()
                && f.corners.isEmpty() && f.randCands.isEmpty() && !f.foundClose;
        if (!noCand) return false;
        return f.scrollPresent || f.editPresent || f.longText || f.nodeCount > 260;
    }

    /* ───────── v4.3.0 扫描未命中诊断日志 ─────────
     * 每窗一条（pkg|winStart 去重）：树规模 + 窗口类名 + 可点小控件样本
     * （text/desc/vid + 归一化坐标），用户上传日志后开发侧据此补规则。
     * v5.1.0 双降噪：① 跳过后静默期（复验通过 2.2s 内）不记——首页内容
     * 落定期的扫描是噪声（waimaiii 实锤：复验通过同一秒记了一条 79 节点
     * 未命中）；② 微树（≤5 节点）不记——加载占位窗（school.app 实锤：
     * 「树 4 节点 · FrameLayout」连环刷屏），无任何诊断价值。
     * v5.5.1（issue#62/#64 实锤）第三重降噪——同包 8s 节流：
     *  Dialog/底部面板弹出动画期窗口类名高频抖动（FrameLayout↔MainActivity
     *  往返），每次窗口事件都重置 winStart → pkg|winStart 去重失效 →
     *  ting.android 2 秒内 4 条「开屏未命中」（播放列表样本 ×4）、
     *  downloads.ui 同秒 2 条。节流保留爆发首条诊断（不同窗口实例的
     *  语义没丢——首条样本已足够补规则），其余静默。 */
    private final java.util.HashMap<String, Long> missThrottleAt = new java.util.HashMap<String, Long>();

    private void logScanMiss(String pkg, AccessibilityNodeInfo[] roots, ScanFind f, int nodesSum, String nodesDetail,
                             long now, long winStart, boolean popup) {
        try {
            // v5.1.0 静默期：广告刚关，首页内容还在落定——不记噪声
            Long closedAt = adClosedAt.get(pkg);
            if (AdDecisionCore.postSkipQuiet(closedAt == null ? 0 : closedAt.longValue(), now)) return;
            // v5.1.0 微树过滤：加载占位窗（≤5 节点）无诊断价值
            //（v7.15.7：多根时看合计——Keep 型活动窗 8 节点 + 覆盖层 42 节点
            // 合计 50，不再是「微树」；单根口径不变）
            int missNodes = nodesSum > 0 ? nodesSum : (f != null ? f.nodeCount : 0);
            if (f != null && roots.length <= 1 && f.nodeCount <= 5) return;
            /* v7.15.6 强广告形态豁免（Keep/CSDN「识别不到且日志没记录」根治）：
             * 热区/角标在场 = 大概率广告窗（动态图开屏全屏可点是常态）——
             * 此前同包 8s 节流（logo 窗先记一条把真广告窗的吞掉）与 H5 大树
             * 过滤（WebView 容器 nodeCount>150 全吞）双杀广告窗的未命中样本
             * = 最高价值诊断恰恰零记录。广告窗的 miss 必记，其余照旧节流。 */
            boolean strongAdForm = f != null && (f.hotZone || f.adBadge);
            String key = pkg + "|" + winStart;
            Long last = missLogAt.get(key);
            if (last != null) return;
            /* v5.5.1 同包 8s 节流：窗口类名抖动期 winStart 每抖一次换新键，
             * 逐窗去重对抖动无效——包级节流才能挡住 2 秒 4 条的爆发
             * （v7.15.6：强广告形态窗豁免——广告 miss 是补规则的黄金样本） */
            Long lastPkg = missThrottleAt.get(pkg);
            if (lastPkg != null && now - lastPkg.longValue() < 8_000L && !strongAdForm) return;
            missThrottleAt.put(pkg, now);
            if (missThrottleAt.size() > 32) missThrottleAt.clear(); // 防膨胀
            missLogAt.put(key, now);
            if (missLogAt.size() > 64) missLogAt.clear(); // 防膨胀
            // 弹窗路径且无画像（未走到 walk）：不记（弹窗识别本就门控严格）
            if (popup && f == null) return;
            // 内容页特征太强（可滚动+输入框）且零候选：不记（不是广告场景，
            // 每个正常应用启动都记一条 = 刷屏）——v7.15.6 强广告形态豁免同上
            if (f != null && (f.scrollPresent || f.editPresent) && f.nodeCount > 150 && !strongAdForm) return;
            StringBuilder sb = new StringBuilder(160);
            sb.append(popup ? "❓ 弹窗未命中（" : "❓ 开屏未命中（").append(shortPkg(pkg)).append("）");
            /* v7.15.7 多根可见化 + v7.15.8 更详细（用户指令）：双根时
             * 「树 8+42 节点 · 根 2」（每根明细直接可见——哪扇窗是空壳、
             * 哪扇是广告窗不再盲猜）；单根维持旧口径「树 N 节点」；
             * 附入窗时长（晚到跳过键的时序诊断——「入窗 3.2s」意味着
             * 按钮可能更晚才出现）。 */
            if (roots.length > 1) {
                sb.append("树 ").append(nodesDetail != null && nodesDetail.length() > 0 ? nodesDetail : (missNodes > 0 ? String.valueOf(missNodes) : "?"))
                        .append(" 节点 · 根 ").append(roots.length);
            } else {
                sb.append(f != null ? "树 " + f.nodeCount + " 节点" : "树 ?");
                /* v7.15.9 微树空壳标记：单根 6~48 节点且五类候选全空——
                 * Keep/weibo 型「跳过键住覆盖层或晚到」铁签名，上传日志
                 * 直接可诊断（不再需要反推树规模）。 */
                if (f != null && f.nodeCount > 5 && f.nodeCount <= MULTI_WIN_MICRO_TREE_MAX
                        && f.skipCands.isEmpty() && f.fakeCands.isEmpty()
                        && f.corners.isEmpty() && f.randCands.isEmpty() && !f.foundClose) {
                    sb.append(" · 微树空壳（跳过键或在覆盖层/晚到）");
                }
            }
            sb.append(" · 入窗 ").append(winStart > 0 ? String.valueOf(Math.max(0, (now - winStart) / 100L) / 10.0) : "?").append("s");
            /* v7.15.10【三图类型诊断】图片键候选可见化：角落/泛位候选在场
             * 但未出手（树上限/滚动/时窗/复验轮换中）——CSDN 左下图圆键/
             * 商店推广页晚到键/上滑互动 H5 胶囊键的补规则金样本（此前只能
             * 从候选样本反推，现在直接可读）。f 为末根画像（多根时末根
             * 候选；已出手场景不会走到 miss 日志）。 */
            if (f != null && (!f.corners.isEmpty() || !f.randCands.isEmpty())) {
                sb.append(" · 图片键候选 角落").append(f.corners.size())
                        .append("+泛位").append(f.randCands.size()).append("（未出手：门控/时序）");
            }
            String cls = winClassOf(pkg);
            if (cls.length() > 0) sb.append(" · 窗口 ").append(shortCls(cls));
            /* v7.15.7：候选样本覆盖全部根（旧版只看 roots[0]——覆盖层窗口里
             * 的真跳过键样本全被吞掉）；总额共享（预算 120·样本 4 条不变）。 */
            String samples = collectNodeSamples(roots);
            if (samples.length() > 0) sb.append(" · 候选样本：").append(samples);
            /* v7.1.0 订阅两源覆盖诊断（使用顺序问题可见化：该应用市场/内置
             * 谁在岗谁缺席——上传日志即可定位「导入了怎么还是不跳」类问题） */
            try {
                String orderLine = SubscriptionManager.coverageLine(pkg);
                if (orderLine.length() > 0) sb.append(" · ").append(orderLine);
            } catch (Throwable ignored) {
            }
            sb.append(" ——上传日志可帮我们补规则");
            appendLog(sb.toString());
        } catch (Throwable ignored) {
        }
    }

    /** 收集树内可点小控件样本（≤4 条：label@(x%,y%)，供补规则用）
     *  v5.5.1 双净化（issue#56/#57/#64 实锤）：
     *  ① 越界中心剔除——样本坐标须过 sampleOnScreen（±2% 容差）：
     *    「播放列表@(16%,150%)」「vid:checkbox@(187%,17%)」类越界点位
     *    是弹窗 bounds 被裁剪/坐标系差异的误导产物，补规则点位不在屏上；
     *  ② label+坐标签名去重——支付宝日志「关闭@(93%,9%) / 关闭@(93%,9%)」
     *    双胞胎占掉 1/4 样本额度，去重后同额度覆盖更多不同控件。
     *  v7.15.7：多根版（活动窗+覆盖层窗全覆盖，预算共享 120·样本 4 条）。 */
    private String collectNodeSamples(AccessibilityNodeInfo[] roots) {
        if (roots == null || roots.length == 0 || screenWidth <= 0 || screenHeight <= 0) return "";
        StringBuilder sb = new StringBuilder(96);
        int[] budget = {120};
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (AccessibilityNodeInfo r : roots) {
            if (r == null || budget[0] <= 0 || countSep(sb) >= 4) break;
            collectSamples(new NodeAdapter(r), budget, sb, seen);
        }
        return sb.toString();
    }

    private void collectSamples(NodeView node, int[] budget, StringBuilder out, java.util.HashSet<String> seen) {
        if (node == null || budget[0] <= 0 || countSep(out) >= 4) return;
        budget[0]--;
        try {
            int[] b = node.bounds();
            if (b != null && b.length >= 4 && b[2] > b[0]) {
                double area = ((double) (b[2] - b[0]) * (b[3] - b[1])) / ((double) screenWidth * screenHeight);
                String text = nz(node.text());
                String desc = nz(node.desc());
                String vid = vidTail(node.viewId());
                String label = text.length() > 0 ? text : (desc.length() > 0 ? desc : vid);
                boolean small = area <= 0.12 && (b[2] - b[0]) <= screenWidth * 0.6;
                if (label.length() > 0 && small && node.visible() && node.clickable()) {
                    int cxPct = (int) Math.round((b[0] + b[2]) / 2.0 / screenWidth * 100);
                    int cyPct = (int) Math.round((b[1] + b[3]) / 2.0 / screenHeight * 100);
                    /* v5.5.1 ①：越界中心不采（纯函数 sampleOnScreen，JVM 可测） */
                    if (AdDecisionCore.sampleOnScreen(cxPct, cyPct)) {
                        /* v5.5.1 ②：label+坐标签名去重 */
                        String sig = clip(label, 12) + "@" + cxPct + "," + cyPct;
                        if (!seen.contains(sig)) {
                            seen.add(sig);
                            if (out.length() > 0) out.append(" / ");
                            out.append(clip(label, 12)).append("@(")
                                    .append(cxPct).append("%,").append(cyPct).append("%)");
                        }
                    }
                }
            }
            int n = node.childCount();
            for (int i = 0; i < n && countSep(out) < 4; i++) {
                collectSamples(node.child(i), budget, out, seen);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int countSep(StringBuilder sb) {
        int c = 0;
        for (int i = 0; i < sb.length(); i++) if (sb.charAt(i) == '/') c++;
        return c + 1;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String vidTail(String vid) {
        if (vid == null || vid.length() == 0) return "";
        int i = vid.lastIndexOf('/');
        return i >= 0 ? "vid:" + vid.substring(i + 1) : "vid:" + vid;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String shortCls(String cls) {
        if (cls == null) return "";
        int i = cls.lastIndexOf('.');
        return i >= 0 && cls.length() - i - 1 > 1 ? cls.substring(i + 1) : cls;
    }

    /* ───────── v3.14.0 跳过后反馈弹窗自动收起 ─────────
     * 喜马拉雅真机截图实锤：跳过开屏后弹出「不喜欢/反馈投诉」底部面板
     * （重复出现/不喜欢此内容/虚假广告/内容不适/侵权违规/其他问题/
     * 我为什么会看到广告/取消），10 次有 8 次，用户每次都要手动收起。
     * 返回 true = 反馈面板在场（本轮接管：停用重试 + 代点取消）。 */
    private boolean handleFeedbackSheet(String pkg, AccessibilityNodeInfo[] roots, int clickAttempts, long now) {
        Long lastSkip = skipAt.get(pkg);
        boolean recentSkip = lastSkip != null && now - lastSkip < AdDecisionCore.FEEDBACK_AFTER_SKIP_MS;
        if (!recentSkip && clickAttempts <= 0) return false; // 未跳过过/非复验中：绝不碰
        for (AccessibilityNodeInfo r : roots) {
            AdDecisionCore.FeedbackFind ff = AdDecisionCore.findFeedbackSheet(new NodeAdapter(r), 80);
            if (!ff.isSheet) continue;
            // ① 广告已关铁证：复验直接通过，停用换候选重试
            clickTries.put(pkg, 0);
            clearTapTrail(pkg); // v6.0.0：同步清空触点轨迹
            winClsLearned.remove(pkg);
            // v4.5.0：反馈面板在场 = 广告已关，本窗固定扫描档全部取消
            //（issue#43-44 实锤：面板收起后 12s 开屏窗仍活着，扫描链继续
            // 在后续界面找「跳过」文案点击 = 广告关了还乱点第二下）
            chainKilledAt.put(pkg, winAt(pkg));
            Long lastLog = feedbackLogAt.get(pkg);
            if (lastLog == null || now - lastLog > 8000) {
                feedbackLogAt.put(pkg, now);
                appendLog("✓ 广告已关闭（" + shortPkg(pkg) + "）：跳过后弹出「不喜欢/反馈」面板 → 已停用重试"
                        + (ff.cancelBounds != null ? "，代点「" + ff.cancelLabel + "」收起（绝不替你提交任何反馈）" : "（未找到取消键，交给你手动收起）"));
            }
            // ② 代点「取消」（同包 2.5s 冷却防抖；手势真实触点首选，
            //    派发失败降级无障碍代点）
            if (ff.cancelBounds != null && !userYield(pkg, now)) {
                // v4.2.0：用户正在操作时绝不代点「取消」（交给用户自己收起）
                Long lastClick = feedbackClickAt.get(pkg);
                if (lastClick == null || now - lastClick >= 2500) {
                    int cx = (ff.cancelBounds[0] + ff.cancelBounds[2]) / 2;
                    int cy = (ff.cancelBounds[1] + ff.cancelBounds[3]) / 2;
                    if (gestureTap(cx, cy) || performActionOn(ff.cancelNode)) {
                        feedbackClickAt.put(pkg, now);
                        lastAutoClickPkg = pkg;
                        lastAutoClickAt = now;
                        bumpStat(pkg, "feedback");
                    }
                }
            }
            return true; // 面板在场：本轮绝不点广告残留跳过键（透过面板=乱点）
        }
        return false;
    }

    /** 无障碍代点 NodeView（反馈面板「取消」键的手势派发失败兑底） */
    private boolean performActionOn(AdDecisionCore.NodeView node) {
        try {
            if (node instanceof NodeAdapter) {
                return ((NodeAdapter) node).n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /* ───────── v3.9.0 倒计时门控（自 tryScan 抽取，规则/兜底两路共用）─────────
     * 穿山甲类 SDK 倒计时结束前跳过键不响应（提前点=白点还占冷却）。
     * 文案数字变化→重新锚定；首击未关→等到「锚点+剩余秒数-0.5s」再出手；
     * 倒计时走完（文案无数字）→立即恢复出手。返回 true=继续等待。
     * v4.3.0 假倒计时识别（issue#38 喜马拉雅「跳过速度过慢」）：真倒计时
     * 每秒跳数字（锚点随数字重置）；装饰性数字（烧在图里的假倒计时）永不
     * 变化——首击未关且数字 1.6s 未变 = 假倒计时，不再门控，按普通冷却
     * 节奏换候选重试（同坐标不再白等 5 秒）。
     * v4.4.0 门控到期即点（issue#39 实锤：门控说「4086ms 后出手」，实际
     * 5.5s 后才点到——门控开闸后要等下一固定档扫描/内容事件才出手，扫描链
     * 空档 3.6s→5s 是喜马拉雅/keep 慢干人工的头号元凶）：设门控时同步排
     * 一针【开闸 +40ms】的精确补扫（代际护栏防窗口已切），开闸瞬间即点。 */
    private boolean countdownWait(String pkg, int cdN, int clickAttempts, long now) {
        if (cdN > 0) {
            Integer lastN = cdLastN.get(pkg);
            if (lastN == null || lastN.intValue() != cdN) {
                cdLastN.put(pkg, cdN);
                cdAnchorAt.put(pkg, now);
            }
            Long anchor = cdAnchorAt.get(pkg);
            long anchorAt = anchor == null ? now : anchor.longValue();
            /* v4.3.0：假倒计时——数字 1.6s 未变（真倒计时每秒跳一次重置锚点）。
             * v7.5.0 纯函数化：对任何击数都放行（含窗内早见晚点场景）。 */
            if (AdDecisionCore.decorativeCountdown(anchorAt, now)) {
                Long lastFake = cdFakeLogAt.get(pkg);
                if (lastFake == null || now - lastFake > 15000) {
                    cdFakeLogAt.put(pkg, now);
                    appendLog("⏱ 倒计时疑似装饰数字（" + shortPkg(pkg) + "）：数字 1.6s 未变化 → 不再等待，按普通节奏出手");
                }
                return false;
            }
            /* ═══ v7.6.0 激进模式：门控仅对重击生效（首击零门控）═══
             * 用户十五轮实锤：「开屏广告现在有的还跳不了了，或者非常
             * 非常慢」——v7.5.0 第 0 击起全次门控让所有带倒计时的开屏
             * 全等倒计时走完才出手（5s 广告白看 4.9s），误检数字更是把
             * 无倒计时按钮按住不动。激进反转（用户原话：宁可漏拦绝不
             * 误触暂时先放弃）：第 0 击零门控露头即点；首击白点后
             *（attempts≥1）重击才等「将尽」（真倒计时按钮命中时机），
             * 且重击等待秒数 6s 封顶（RETRY_GATE_MAX_S——cdN 封顶后才算
             * 开闸时刻，开闸针不会排到 6s 外）。 */
            long until = AdDecisionCore.countdownUntil(
                    AdDecisionCore.cappedRetryCd(cdN), anchorAt);
            if (AdDecisionCore.gateCountdownAggressive(cdN, anchorAt, now, clickAttempts)) {
                Long prevGate = cdWaitUntil.put(pkg, until);
                if (prevGate == null) {
                    appendLog("⏳ 倒计时门控（" + shortPkg(pkg) + "）：跳过键 "
                            + cdN + "s 后才激活（提前点=白点）→ "
                            + (until - now) + "ms 后出手即命中");
                }
                /* v4.4.0 到期即点：开闸瞬间精确补扫（此前要等下一固定档
                 * /内容事件，最慢 5s+ = 慢干人工元凶）。
                 * 数字每秒跳一次 → until 随锚点重算 → 每次变化都重排一针
                 * （旧针的 until 比对自动作废，零冲突零漏扫）。 */
                if (prevGate == null || prevGate.longValue() != until) {
                    scheduleGateOpenScan(pkg, until);
                }
                return true;
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

    /** v4.4.0：门控开闸 +40ms 精确补扫（代际护栏：窗口已切/门控已变则作废） */
    private void scheduleGateOpenScan(final String pkg, final long until) {
        long now = System.currentTimeMillis();
        long delay = Math.max(30L, until - now + 40L);
        Integer g = scanGen.get(pkg);
        final int gen = g == null ? 0 : g.intValue();
        handler.postDelayed(() -> {
            AdSkipService sv = instance;
            if (sv == null) return;
            Integer cur = scanGen.get(pkg);
            if (cur == null || cur.intValue() != gen) return; // 窗口已切
            Long w = cdWaitUntil.get(pkg);
            if (w == null || w.longValue() != until) return;  // 门控已变/已清
            sv.tryScan(pkg, sv.winAt(pkg), -1);
        }, delay);
    }

    /** v4.4.0 快速复验：点击后 450ms / 900ms 两针精确补扫（代际护栏）。
     *  此前复验只能等固定档扫描/内容事件（最慢 1.2s+）——「点了没反应」
     *  场景下人手已经补上第二刀，引擎还在等档。 */
    private void scheduleVerifyScan(final String pkg, long delayMs) {
        Integer g = scanGen.get(pkg);
        final int gen = g == null ? 0 : g.intValue();
        handler.postDelayed(() -> {
            AdSkipService sv = instance;
            if (sv == null) return;
            Integer cur = scanGen.get(pkg);
            if (cur == null || cur.intValue() != gen) return;
            sv.tryScan(pkg, sv.winAt(pkg), -1);
        }, delayMs);
    }

    /** 收集同包可扫描窗口根节点（活动窗口优先，最多 3 个） */
    private AccessibilityNodeInfo[] collectRoots(String pkg) {
        return collectRoots(pkg, false);
    }

    /* ═══ v7.15.7 多窗口开屏扫描（Keep 动态图开屏根治）═══
     * 真机日志实锤（#98/#101）：Keep 难拦型开屏的「跳过 4」按钮住在【同包
     * 覆盖层窗口】（广告 SDK 自建 Window）里，而 rootInActiveWindow 指向的
     * SplashActivity 只有 8 节点——旧版 collectRoots 只要活动窗口属于本包就
     * 短路返回，覆盖层窗口永远不被扫 = 按钮在场却零识别零记录（用户手动点
     * 「跳过 4」被学习观察到，证明节点真实存在）。
     * v7.15.8【事故修复】口径（v7.15.7 无条件全量枚举是服务级瘫痪元凶）
     * ——见常量区事故复盘注释：
     *  · allWins=true 仅由探针路径传入（CLEAN 档案窗的复活通道），且受
     *   400ms 枚举节流 + TYPE_APPLICATION 前置过滤双闸（每窗最多 2.5 次
     *   枚举，每次只对同包应用窗 getRoot——与 v7.3.0 回退路径同量级）；
     *  · 全量扫描路径（tryScan）不再传 allWins：改为两段式——首段只扫
     *   活动窗（与 v7.15.6 byte-for-byte 同路径零增量），微树零候选铁签名
     *   （Keep 型）才由 collectExtraRoots 补窗（见 tryScan 循环尾部门控）。 */
    private AccessibilityNodeInfo[] collectRoots(String pkg, boolean allWins) {
        java.util.ArrayList<AccessibilityNodeInfo> out = new java.util.ArrayList<>();
        int activeWinId = Integer.MIN_VALUE;
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            CharSequence rp = active.getPackageName();
            if (rp != null && pkg.contentEquals(rp)) {
                out.add(active);
                try {
                    activeWinId = active.getWindowId();
                } catch (Throwable ignored) {
                }
            }
        }
        /* v7.3.0 回退路径（活动窗不属于本包时全量枚举）保持原口径不变；
         * v7.15.8 多窗枚举（allWins）才走节流闸——两路径互不影响。 */
        boolean fallback = out.isEmpty();
        if (fallback || (allWins && multiWinEnumOk(pkg))) {
            try {
                List<AccessibilityWindowInfo> wins = getWindows();
                if (wins != null) {
                    for (AccessibilityWindowInfo w : wins) {
                        if (out.size() >= MAX_WINDOWS_SCAN) break;
                        if (w == null) continue;
                        /* v7.15.8 类型前置过滤（仅多窗路径）：非应用窗
                         * （状态栏/导航条/输入法/系统覆盖层）一律免 getRoot
                         * ——parcel 本地字段零 binder，MIUI 20+ 窗列表的
                         * binder 大头直接归零；回退路径保持旧口径不过滤 */
                        if (!fallback && w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                        // v7.3.0 算法：getRoot 单次缓存（旧版判空+取包名+再取 root
                        // 共三次跨进程序调用——窗口收集是每轮扫描的 binder 大户）
                        AccessibilityNodeInfo r0 = w.getRoot();
                        CharSequence wp = r0 == null ? null : r0.getPackageName();
                        if (wp != null && pkg.contentEquals(wp)) {
                            // v7.15.7：活动窗口去重（getWindowId 同窗即同 id）
                            try {
                                if (r0.getWindowId() == activeWinId) continue;
                            } catch (Throwable ignored) {
                            }
                            out.add(r0);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return out.toArray(new AccessibilityNodeInfo[0]);
    }

    /* ═══ v7.15.8 两段式第二段：微树门触发后的同包补窗枚举 ═══
     * 只在 tryScan 尾部门控（微树 ≤48 节点 + 零候选 + 开屏窗 + 400ms 节流
     * 全过）时调用；类型前置过滤同 collectRoots 多窗路径。返回【不含】
     * 当前已在扫描列表里的窗口（审核轮加固：cur 全员 getWindowId 建集
     * 去重——fallback 已带回 2 根时第 2 根不再被重复计入）。 */
    private AccessibilityNodeInfo[] collectExtraRoots(String pkg, java.util.ArrayList<AccessibilityNodeInfo> cur) {
        java.util.ArrayList<AccessibilityNodeInfo> out = new java.util.ArrayList<>();
        try {
            java.util.HashSet<Integer> seenIds = new java.util.HashSet<>();
            if (cur != null) {
                for (AccessibilityNodeInfo c : cur) {
                    try {
                        if (c != null) seenIds.add(Integer.valueOf(c.getWindowId()));
                    } catch (Throwable ignored) {
                    }
                }
            }
            List<AccessibilityWindowInfo> wins = getWindows();
            if (wins == null) return out.toArray(new AccessibilityNodeInfo[0]);
            for (AccessibilityWindowInfo w : wins) {
                int curN = (cur == null ? 0 : cur.size()) + out.size();
                if (curN >= MAX_WINDOWS_SCAN) break;
                if (w == null || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo r0 = w.getRoot();
                CharSequence wp = r0 == null ? null : r0.getPackageName();
                if (wp != null && pkg.contentEquals(wp)) {
                    try {
                        if (!seenIds.add(Integer.valueOf(r0.getWindowId()))) continue; // cur 全员 + 本轮已收双重去重
                    } catch (Throwable ignored) {
                    }
                    out.add(r0);
                }
            }
        } catch (Exception ignored) {
        }
        return out.toArray(new AccessibilityNodeInfo[0]);
    }

    /** v7.15.8 多窗枚举节流（探针 collectRoots(allWins) 专用账——v7.15.9 分账） */
    private final java.util.HashMap<String, Long> multiWinEnumAt = new java.util.HashMap<String, Long>();

    private boolean multiWinEnumOk(String pkg) {
        long now = System.currentTimeMillis();
        Long last = multiWinEnumAt.get(pkg);
        if (last != null && now - last.longValue() < MULTI_WIN_ENUM_THROTTLE_MS) return false;
        multiWinEnumAt.put(pkg, Long.valueOf(now));
        if (multiWinEnumAt.size() > 24) multiWinEnumAt.clear(); // 防膨胀
        return true;
    }

    /* ═══ v7.15.9【分账根治】tryScan 微树门专用枚举账 ═══
     * v7.15.8 的「探针/扫描共用一本账」（multiWinEnumOk）在真机上被探针
     * 前置饿死：CLEAN 档案窗的内容事件 → nudge → tryScan(atMs<0) →
     * probeSentinelThrottled → probeSentinel → collectRoots(allWins=true)
     * 每 ~400ms 消耗一次预算；扫描链梯子档（atMs≥0）的微树门检查时预算
     * 永远是「刚被探针用掉」→ 补扫死代码（#102 实锤：v7.15.8 整份日志
     * 零🪟，weibo 6 节点 / keep 8 节点空壳都没触发）。分账后两路径各自
     * 400ms 节流，最坏同窗双枚举 = 每秒 ~10 次 binder，仍比 v7.15.7
     * 风暴（250+/秒）低一个量级以上。 */
    private final java.util.HashMap<String, Long> microScanEnumAt = new java.util.HashMap<String, Long>();

    private boolean microScanEnumOk(String pkg) {
        long now = System.currentTimeMillis();
        Long last = microScanEnumAt.get(pkg);
        if (last != null && now - last.longValue() < MULTI_WIN_ENUM_THROTTLE_MS) return false;
        microScanEnumAt.put(pkg, Long.valueOf(now));
        if (microScanEnumAt.size() > 24) microScanEnumAt.clear(); // 防膨胀
        return true;
    }

    /* ═══ v7.15.8 多窗补扫启用日志（用户指令：日志更详细）═══
     * 每窗实例仅一条：活动窗微树规模 + 补窗数直接可见——Keep 型覆盖层
     * 问题从「识别不到且无日志」变为日志里直接可诊断。 */
    private final java.util.HashMap<String, Long> multiWinLogFor = new java.util.HashMap<String, Long>();

    private void multiWinLog(String pkg, long winStart, int activeNodes, int extraN) {
        try {
            Long logged = multiWinLogFor.get(pkg);
            if (logged != null && logged.longValue() == winStart) return;
            multiWinLogFor.put(pkg, Long.valueOf(winStart));
            if (multiWinLogFor.size() > 24) multiWinLogFor.clear(); // 防膨胀
            appendLog("🪟 多窗补扫启用（" + shortPkg(pkg) + "）：活动窗仅 " + activeNodes
                    + " 节点且零候选（Keep 型：跳过键疑似在覆盖层窗口）→ 本窗补扫同包窗口 "
                    + extraN + " 个");
        } catch (Throwable ignored) {
        }
    }

    /* ═══ v7.15.9 微树门「无窗可补」打点（每窗实例一条）═══
     * 门开了但 collectExtraRoots 空手而归时留痕——否则「门被饿死」（v7.15.8
     * 分账前常态）与「无同包覆盖层」在日志里无从区分，排障只能盲猜。 */
    private final java.util.HashMap<String, Long> microScanEmptyFor = new java.util.HashMap<String, Long>();

    private void microScanEmptyLog(String pkg, long winStart, int activeNodes) {
        try {
            Long logged = microScanEmptyFor.get(pkg);
            if (logged != null && logged.longValue() == winStart) return;
            microScanEmptyFor.put(pkg, Long.valueOf(winStart));
            if (microScanEmptyFor.size() > 24) microScanEmptyFor.clear(); // 防膨胀
            appendLog("🪟 微树补扫（" + shortPkg(pkg) + "）：无同包覆盖层可补（活动窗 "
                    + activeNodes + " 节点空壳——跳过键若晚到由晚档梯子/探针救回）");
        } catch (Throwable ignored) {
        }
    }


    /** 执行决策；返回 true = 已出手（熔断扫描链） */
    private boolean execute(String pkg, Decision d, ScanFind f, Ctx ctx, boolean clickCoolOK, long now) {
        /* ═══ v5.0.0 小程序容器保护（L0 第五层：出手前终门）═══
         * 任何动作（点击/关闭/返回键/学习位置直点）派发前的最后一道门——
         * 容器内绝不出手（规则引擎/订阅/探针路径不经过决策核心，必须在这里兜住）。 */
        if (MiniProgramGuard.isMiniProgramWindow(pkg, winClassOf(pkg))) {
            appendLog("🧩 小程序保护（" + shortPkg(pkg) + "）：容器内绝不动作");
            return false;
        }
        /* ═══ v7.15.2 锁屏在场不出手（误触红线）═══
         * 真机时序实锤（#96）：应用在锁屏后面就把返回开屏窗拉起来了（事件
         * 在 USER_PRESENT 之前到达）——此时坐标点击/返回键全落在锁屏上。
         * 出手前统一过 keyguardBlocking（广播优先零 IPC + IPC 定音自愈）；
         * 解锁重入（v7.15.1）与 SDK 直断兜底链（本版）会接管解锁后的出手。 */
        if (keyguardBlocking()) {
            try {
                long kgNow = System.currentTimeMillis();
                Long lastKg = kgYieldLogAt.get(pkg);
                if (lastKg == null || kgNow - lastKg.longValue() > 10_000L) {
                    kgYieldLogAt.put(pkg, kgNow);
                    if (kgYieldLogAt.size() > 24) kgYieldLogAt.clear(); // 防膨胀
                    appendLog("🔒 锁屏在场（" + shortPkg(pkg) + "）：屏上是锁屏不是广告——本轮不出手（解锁重入自动接管）");
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
        /* ═══ v5.5.0 模块四：动作边界自检（出手前最后闸）═══
         * 仅代点原生跳过控件：语义（跳过/关闭/否定侧）+场景（零扫描
         * 场景绝不出手）+形态（≤35% 屏）三重断言；学习位置（用户手动
         * 教过=用户意图）豁免语义断言，但场景/形态仍查。任一不过=
         * 放弃（宁可漏拦绝不误触——误触率只降不升红线的最后一道闸）。
         * v5.5.1：① d.node 为空（gestureOnly 规则命中）或控件零文本时，
         * 语义依据回落到 Decision.label（规则引擎已验证的命中文案）——
         * issue#64 微博「内嵌规则·分应用文案命中被自检拦截」误杀根治；
         * ② 拒绝时按层打点（场景/语义/形态）+ 目标签要，日志可直接诊断。 */
        if (d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) || d.action.equals(AdDecisionCore.A_CLOSE)
                || d.action.equals(AdDecisionCore.A_TAP_POINT)) {
            String bl = "", bv = "";
            int[] bb2 = d.bounds != null ? d.bounds
                    : (d.x >= 0 ? new int[]{d.x - 8, d.y - 8, d.x + 8, d.y + 8} : null);
            try {
                if (d.node != null) {
                    bl = cs(d.node.text());
                    if (bl.length() == 0) bl = cs(d.node.desc());
                    bv = d.node.viewId() == null ? "" : d.node.viewId();
                }
            } catch (Throwable ignored) {
            }
            /* v5.5.1：gestureOnly 回落——控件文本与 vid 双空时用规则命中文案
             * （语义依据换源，绝不放宽：文案仍须跳过/关闭族才通过断言）。
             * v7.3.0 安全收紧：回落仅对【主引擎/决策核心/学习位置】命中生效
             * ——其 label 是引擎已验证的真实命中文案（跳过/关闭族）；订阅命中
             * （rule_sub_gkd* 三变体）的 label 是规则名（订阅源可控文本，伪装
             * 「跳过」的恶意规则名不再能作为语义证据——零文本零 vid 目标一律拒点）。 */
            if ((bl.length() == 0 && bv.length() == 0) && d.label != null && d.label.trim().length() > 0
                    && (d.reason == null || !d.reason.startsWith("rule_sub_gkd"))) {
                bl = d.label.trim();
            }
            boolean userTaught = "learned_pos".equals(d.reason) || d.reason.startsWith("learned");
            /* v7.1.0：feed 决策（reason 前缀 feed_）走 feed 专用终闸——语义断言
             * 换源为 FeedAdGuard 角标/中性项语义（feed 路径只点角标/中性关闭项，
             * 绝不广告主体）；非 feed 路径 byte-for-byte 保持原终闸不变。
             * v7.6.0：插屏决策（reason=inter_close）走插屏专用终闸——「知道了/
             * 残忍离开/不再看」等插屏关闭词不含跳过/关闭字面，通用终闸的
             * 语义断言会把它们一票否决（v7.5.0 终闸杀词死因：词表收了也白收，
             * 出手永远被最后一道闸打回）。语义换源
             * InterstitialGuard.isInterstitialCloseWord（决策上游已过广告语境
             * 在场+形态闸，此处复验词表成员身份——只对 inter_close 放行）。 */
            int bnd = (d.reason != null && d.reason.startsWith("feed_"))
                    ? com.zai.adshield.core.ComplianceGuard.feedBoundaryCheck(bl, bb2, screenWidth, screenHeight, pkg, winClassOf(pkg))
                    : (com.zai.adshield.core.InterstitialGuard.ACTION_CLOSE.equals(d.reason)
                    ? com.zai.adshield.core.ComplianceGuard.interBoundaryCheck(bl, bb2, screenWidth, screenHeight, pkg, winClassOf(pkg))
                    : com.zai.adshield.core.ComplianceGuard.boundaryCheck(
                    bl, bv, bb2, screenWidth, screenHeight, pkg, winClassOf(pkg), userTaught));
            /* ═══ v7.15.7 图片跳过键语义换源（CSDN 动态图开屏根治）═══
             * 真机日志实锤（#98/#101）：角落/泛位置图片键被通用终闸的语义断言
             * 一票否决（「角落图片跳过键：语义断言（非跳过/关闭方向）→ 放弃」
             * 反复出现）——纯图键无文本无 vid 语义，永远凑不齐证据。仅当
             * 【零文本零 vid 证据】（bl/bv 双空）且 reason 是图片键路径时，
             * 换源 imageKeyBoundaryCheck（场景+严格形态 ≤6% 屏三重尺寸门）；
             * 有文本/vid 证据但非跳过语义 = 真诱饵，照旧一票否决零放行。 */
            if (bnd == com.zai.adshield.core.ComplianceGuard.BND_FAIL_SEMANTIC
                    && bl.length() == 0 && bv.length() == 0
                    && ("corner_image_skip".equals(d.reason) || "rand_graphic_skip".equals(d.reason))) {
                bnd = com.zai.adshield.core.ComplianceGuard.imageKeyBoundaryCheck(
                        bb2, screenWidth, screenHeight, pkg, winClassOf(pkg));
                if (bnd == com.zai.adshield.core.ComplianceGuard.BND_OK) {
                    /* v7.15.11 日志去重：同包同理由 6s 一条（#108 实锤同一秒
                     * 13 条——每次扫描每轮 execute 都重打，「多点」观感的直接
                     * 来源；实际出手以 ⚡/🔁 日志为准）。首条必记。 */
                    long ibNow = System.currentTimeMillis();
                    String ibKey = pkg + "|" + d.reason;
                    Long ibLast = imgBndLogAt.get(ibKey);
                    if (ibLast == null || ibNow - ibLast.longValue() >= 6_000L) {
                        imgBndLogAt.put(ibKey, ibNow);
                        if (imgBndLogAt.size() > 48) imgBndLogAt.clear(); // 防膨胀
                        appendLog("🎯 图片跳过键边界放行（" + shortPkg(pkg) + " · " + reasonZh(d.reason)
                                + "）：纯图键零文案语义——场景+形态双重门替代（面积 ≤6% 屏）");
                    }
                }
            }
            if (bnd != com.zai.adshield.core.ComplianceGuard.BND_OK) {
                boundaryGuardLog(pkg, d.reason, bnd, bl.length() > 0 ? bl : bv);
                return false;
            }
        }
        // v4.2.0 用户意图让路：任何出手（点击/关闭/返回键/学习位置直点）
        // 前统一校验——用户 4s 内点击过或正浏览自己打开的页面，一律让路。
        // v7.4.0 唯一豁免：P0 面板直通（feed_p0_panel_close）——用户手拉的
        // 「关闭该广告的原因」面板正是「广告关不掉只能手动关」的求救信号，
        // 代点主按钮「直接关闭」与用户意图完全同向（13 轮实锤：用户要的就
        // 是广告消失）；P1/P1X/P2/其他路径让路红线一条不少。
        if (!(d.reason != null && com.zai.adshield.core.FeedAdGuard.ACTION_P0_PANEL_CLOSE.equals(d.reason))
                && userYield(pkg, now)) {
            return false;
        }
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
                /* v7.15.11 横幅让路重排（#109 解锁实锤）：横幅自动消散后
                 * 系统不会再发任何可触发事件——排 3 针重查，横幅已散即
                 * 重排扫描链（否则解锁开屏白看到底）。 */
                scheduleBannerClearRecheck(pkg, System.currentTimeMillis());
                return false;
            }
            // v3.6.0 根窗口安全门（双保险，决策核心已先拦一道）：
            // 入口窗上按返回键 = 退出应用（喜马拉雅「再按一次退出到桌面」事故）
            if (ctx.rootWindow && !ctx.sdkAdWin) {
                appendLog("⛔ 根窗口安全门（" + shortPkg(pkg) + "）：入口窗禁返回键（防退出应用），只走点击梯子");
                return false;
            }
            if (triesOf(pkg) >= BACK_RETRY_MAX || !backAllowed(now)) {
                /* v7.15.2：旧版此处静默 return——「返回键 0」之谜的唯一黑洞；
                 * 现在打点抑制原因（10s 全局节流），下轮日志直接可见 */
                try {
                    long dNow = System.currentTimeMillis();
                    if (dNow - lastBackDiagAt > 10_000L) {
                        lastBackDiagAt = dNow;
                        appendLog("🛡 直断抑制（" + shortPkg(pkg) + "）：返回键预算 "
                                + triesOf(pkg) + "/" + BACK_RETRY_MAX + " 或全局冷却中——本窗收手");
                    }
                } catch (Throwable ignored) {
                }
                return false;
            }
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
            /* v7.15.11 派发失败连击退避：连续 ≥3 次派发失败（覆盖层拦截/按钮
             * 位移中/节点不可见——clickNode false 且无冷却记账）后按 1.5s
             * 人手耐心节流，掐断 #108 azukireader 式 ~2.6 次/秒对同一候选
             * 的无限重试（每轮都烧 getWindows 覆盖层判定）。首两次快重试
             * 保留（动画中 staleBounds 下一轮就好）。 */
            if (dispatchFailPaced(pkg, now)) return false;
            /* ═══ v7.3.0 多点防线 II（nudge 补扫旁路封死）：出手节奏 = 人手耐心 ═══
             * 原冷却仅 300ms（COOLDOWN_MS）——nudge 补扫 80ms 节流只挡扫描频次，
             * 广告动画/进度条密集内容事件仍可 ~300ms 连发换候选出手（H 静默
             * 600ms 只拦首击后第一枪），与 v6.0.0 人手复验节奏（首击 500ms →
             * 1.4/2.4/3.6/5.2/7.0s 渐进放缓）直接矛盾。冷却改
             * max(300ms, verifyDelayMs(attempts))：nudge 补扫与复验扫描同一
             * 节奏出手——同窗连发从源头断流；scheduleVerifyScan 的复验针时刻
             * 恰好在门槛上，照常放行。 */
            Long lcGap = lastClickAt.get(pkg);
            int attemptsPre = triesOfClick(pkg);
            if (lcGap != null && now - lcGap
                    < Math.max(COOLDOWN_MS, HumanTap.verifyDelayMs(attemptsPre))) return false;
            /* v6.1.0 F/H 多点防线（用户实锤：点击已生效但广告跳转动画期
             * 复验看到「按钮还在」再点一次=用户肉眼的多点；机器连发也是
             * 反自动化 SDK 触点检测的直接特征）：
             *  F 同指纹目标静默——SAME_TARGET_QUIET_MS 内同指纹目标绝不再出手；
             *  H 结果静默——手势派发成功后 POST_TAP_QUIET_MS 内同包任何出手让路 */
            String targetKey = d.bounds != null
                    ? HumanTap.sameTargetKey(pkg, (d.bounds[0] + d.bounds[2]) / 2,
                    (d.bounds[1] + d.bounds[3]) / 2, d.reason,
                    HumanTap.dupTolPx(screenDensity)) : null;
            if (targetKey != null && HumanTap.isFiredRecently(firedTargetAt, targetKey, now)) return false;
            if (!HumanTap.postTapQuietOk(lastGestureOkAt.get(pkg), now)) return false;
            /* v7.2.0 多点防线 I：同指纹目标每窗 2 击封顶（第 3 击起静默——
             * 设备触点被吞时的同目标连击从源头掐断；换候选/换窗不受限）。 */
            if (targetKey != null && !HumanTap.targetStrikesOk(firedTargetCount.get(targetKey))) return false;
            int attempts = triesOfClick(pkg);
            clickAttemptNow = attempts; // v5.9.0：复验象限轮换基数（同候选换点防多点盲刺）
            // v3.8.0：手势真实触点【首选】——performAction 首点常被应用无声吞掉
            //（喜马拉雅实锤）；派发失败才降级 performAction 兜底（见 clickNode）
            if (clickNode(d)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                clickTries.put(pkg, attempts + 1);
                skipAt.put(pkg, now); // v3.14.0：记录引擎出手时刻（跳过后反馈弹窗门控用）
                // v6.1.0 F/H 记录：同指纹目标静默表 + 结果静默表（多点防线生效中）
                if (targetKey != null) {
                    firedTargetAt.put(targetKey, now);
                    Integer prevCnt = firedTargetCount.get(targetKey); // v7.2.0 防线 I：同指纹击数
                    firedTargetCount.put(targetKey, prevCnt == null ? 1 : prevCnt + 1);
                }
                lastGestureOkAt.put(pkg, now);
                /* v7.3.0 多点防线 II：超限改按时间淘汰最旧 128 条（原整体 clear
                 * 会瞬间失忆——同窗刚点过的目标指纹丢失 → 1.6s 静默防线与防线 I
                 * 击数计数失配 → 对同目标补一枪的多点观感；逐条淘汰则防线记忆
                 * 完整保留；击数表 firedTargetCount 同步只保留存活键）。 */
                if (firedTargetAt.size() > 256) {
                    try {
                        java.util.ArrayList<java.util.Map.Entry<String, Long>> es =
                                new java.util.ArrayList<>(firedTargetAt.entrySet());
                        es.sort((p, q) -> Long.compare(p.getValue(), q.getValue()));
                        for (int k = 0; k < es.size() - 128; k++) {
                            String deadKey = es.get(k).getKey();
                            firedTargetAt.remove(deadKey);
                            firedTargetCount.remove(deadKey);
                        }
                    } catch (Throwable ignored) {
                        firedTargetAt.clear(); // 排序异常兜底：绝不无限膨胀
                        firedTargetCount.clear();
                    }
                }
                /* v6.0.0 人手复验节奏（替代 v4.4.0 的 450/900 双针机器枪）
                 * ——首击后 500ms 扫一眼（人手 300~500ms 补刀节奏不变），
                 * 第 2/3/4/5/6 击后 1.4s/2.4s/3.6s/5.2s/7.0s 渐进放缓：
                 * 人会越等越慢，机器枪不会——首击失败设备上双针/连针是
                 * 用户看到「多点」的直接观感来源。 */
                scheduleVerifyScan(pkg, HumanTap.verifyDelayMs(attempts));
                if (d.action.equals(AdDecisionCore.A_CLOSE) && f != null) {
                    popupBlocks++;
                    lastPopupAt.put(pkg, now);
                    appendLog("🪟 弹窗广告已关闭（" + shortPkg(pkg) + "）："
                            + (f.adBadge ? "「" + f.badgeLabel + "」角标" : "Dialog+营销话术")
                            + " + 关闭键「" + f.closeLabel + "」");
                    bumpStat(pkg, "popup");
                } else if (d.reason != null
                        && (d.reason.equals(com.zai.adshield.core.InterstitialGuard.ACTION_CLOSE)
                            || d.reason.startsWith("feed_"))) {
                    /* ═══ v7.15.5 专属路径日志/统计独占（开屏/插屏/信息流分域）═══
                     * 旧版此处一律再打一条 ⚡ + auto 档，而插屏路径
                     * （executeInterstitial 的 🛡 插屏广告 + feed 档）与信息流
                     * 路径（executeFeed 的 🛡 信息流广告 P0/P1/P1X/P2 + feed
                     * 档）各自还要再记一次 = 同一次出手双日志双计数（口径
                     * 混流）。现在这两类路径的日志与统计全部由各自的执行器
                     * 独占，本处零动作（复验链 scheduleVerifyScan 照排）。 */
                } else {
                    /* v7.15.5 场景标注（开屏/插屏严格区分）：开屏观察窗内的
                     * 出手记「⚡ 开屏跳过」——与 🛡 插屏广告 / 🪟 弹窗广告 /
                     * feed 三类日志各自成域，用户看日志即知是哪类广告。 */
                    String sceneTag = (ctx != null && ctx.splashLive)
                            ? "⚡ 开屏跳过（" : "⚡ 已点击跳过（";
                    if (attempts == 0) {
                        appendLog(sceneTag + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                                + "（" + (lastGesture ? "真实触点·1:1 人手" : "无障碍代点兜底") + "）→ " + HumanTap.verifyDelayMs(attempts) + "ms 后复验，未关将换候选重试");
                    } else {
                        appendLog("🔁 复验未关闭 → 第 " + (attempts + 1) + " 次出手（" + shortPkg(pkg) + "）："
                                + reasonZh(d.reason) + "（真实触点·1:1 人手·换第 " + (attempts + 1) + " 个候选，同坐标绝不重复盲刺）");
                    }
                    if (d.reason != null && d.reason.startsWith("feed_")) {
                        /* v7.15.6 冗余清理备注：本分支不可达（上方 else-if 已拦截
                         * feed_ 与 inter_close，到这里的 reason 必非两者）——
                         * v7.2.0「feed 不计 auto」语义由该 else-if 独占。 */
                    } else {
                        bumpStat(pkg, "auto");
                    }
                }
                // v3.5.0 关键：点击后【不熔断】扫描链——由后续扫描复验；
                // 广告关掉会触发新窗口事件（计数清零）；仍在则换候选/升级兜底
                dispatchFailStreak.remove(pkg); // v7.15.11：派发成功清零连击
                return true;
            }
            noteDispatchFail(pkg, now); // v7.15.11：失败记账（连击退避 + 横幅重排钩）
            return false;
        }
        if (d.action.equals(AdDecisionCore.A_TAP_POINT)) {
            if (!clickCoolOK) return false;
            if (dispatchFailPaced(pkg, now)) return false; // v7.15.11：派发失败连击退避（同熙）
            boolean isLearned = "learned_pos".equals(d.reason);
            int tapAttempts = triesOfClick(pkg); // v6.0.0：人手节奏基数（学习位置直点同熙）
            /* v7.3.0 多点防线 II：同 CLICK_BOUNDS 分支——人手耐心节奏对学习位置
             * 直点同样生效（nudge 旁路 ~300ms 连发一并封死）。 */
            Long lcGap2 = lastClickAt.get(pkg);
            if (lcGap2 != null && now - lcGap2
                    < Math.max(COOLDOWN_MS, HumanTap.verifyDelayMs(tapAttempts))) return false;
            /* v6.1.0 F/H 多点防线（学习位置直点同熙：同指纹静默 + 结果静默） */
            String targetKey = HumanTap.sameTargetKey(pkg, d.x, d.y, d.reason,
                    HumanTap.dupTolPx(screenDensity));
            if (HumanTap.isFiredRecently(firedTargetAt, targetKey, now)) return false;
            if (!HumanTap.postTapQuietOk(lastGestureOkAt.get(pkg), now)) return false;
            /* v7.2.0 多点防线 I（学习位置直点同熙）：每窗 2 击封顶 */
            if (!HumanTap.targetStrikesOk(firedTargetCount.get(targetKey))) return false;
            if (gestureTap(d.x, d.y)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                clickTries.put(pkg, tapAttempts + 1);
                skipAt.put(pkg, now); // v3.14.0：记录引擎出手时刻（学习位置直点也是跳过出手）
                energyGestures++;
                dispatchFailStreak.remove(pkg); // v7.15.11：派发成功清零连击
                firedTargetAt.put(targetKey, now); // v6.1.0 F：同指纹目标静默生效中
                Integer prevCnt = firedTargetCount.get(targetKey); // v7.2.0 防线 I：同指纹击数
                firedTargetCount.put(targetKey, prevCnt == null ? 1 : prevCnt + 1);
                lastGestureOkAt.put(pkg, now);     // v6.1.0 H：结果静默生效中
                scheduleVerifyScan(pkg, HumanTap.verifyDelayMs(tapAttempts)); // v6.0.0 人手复验节奏（学习位置直点同熙）
                if (isLearned) {
                    appendLog("🎯 学习位置点击（" + shortPkg(pkg) + "）：你手动教过的位置指纹 ("
                            + d.x + "," + d.y + ")（真实触点·1:1 人手·相对你的屏幕渲染）→ "
                            + HumanTap.verifyDelayMs(tapAttempts) + "ms 后复验");
                    bumpPosRule(pkg);
                } else {
                    // v3.9.0：coord_fallback 已移除（绝不盲点固定坐标），此分支仅防御性保留
                    appendLog("🎯 位置点击（" + shortPkg(pkg) + "）：(" + d.x + "," + d.y + ") → "
                            + HumanTap.verifyDelayMs(tapAttempts) + "ms 后复验");
                }
                if (d.reason != null && d.reason.startsWith("feed_")) {
                    /* v7.15.6 冗余清理备注：不可达（feed 只走 A_CLICK_BOUNDS）
                     * ——防御性对称语义收编为注释。 */
                } else {
                    bumpStat(pkg, "auto");
                }
                return true; // 不熔断：交由复验扫描确认
            }
            noteDispatchFail(pkg, now); // v7.15.11：失败记账（连击退避 + 横幅重排钩）
            return false;
        }
        return false;
    }

    /* ═══ v7.15.11 派发失败连击退避（#108 azukireader 实锤）═══
     * clickNode/gestureTap 失败（覆盖层拦截/按钮动画位移/节点不可见）无
     * 冷却记账——旧版以扫描节奏 ~2.6 次/秒对同一候选无限重试（7 秒 18 轮，
     * 每轮烧一次 getWindows 覆盖层判定 + 🎯 日志刷屏 = 用户「多点」观感）。
     * 连续 ≥3 次失败后 1.5s 人手耐心节流；成功即清零；首两次快重试保留
     * （动画中 staleBounds 下一轮扫描即恢复——不能为退避误伤快速恢复场景）。 */
    private static final long DISPATCH_FAIL_PACE_MS = 1500L;

    private boolean dispatchFailPaced(String pkg, long now) {
        try {
            Integer streak = dispatchFailStreak.get(pkg);
            if (streak == null || streak.intValue() < 3) return false;
            Long last = dispatchFailAt.get(pkg);
            return last != null && now - last.longValue() < DISPATCH_FAIL_PACE_MS;
        } catch (Throwable t) {
            return false;
        }
    }

    private void noteDispatchFail(String pkg, long now) {
        try {
            Integer prev = dispatchFailStreak.get(pkg);
            int s = prev == null ? 1 : prev.intValue() + 1;
            dispatchFailStreak.put(pkg, Integer.valueOf(s));
            dispatchFailAt.put(pkg, now);
            if (dispatchFailStreak.size() > 32) dispatchFailStreak.clear(); // 防膨胀
            if (dispatchFailAt.size() > 32) dispatchFailAt.clear(); // 审核轮 r1：同上限（streak 清零时 paced 不读，安全）
            /* 横幅让路重排钩（#109 解锁实锤）：失败因 heads-up 横幅（验证码/
             * 短信）在场时，横幅自动消散后系统不会再发任何可触发事件——
             * 排 3 针重查，横幅已散即重排扫描链（否则解锁开屏白看到底）。 */
            if (headsUpBanner(null) != null) scheduleBannerClearRecheck(pkg, now);
        } catch (Throwable ignored) {
        }
    }

    /* ═══ v7.15.11 横幅消散重排（锁屏/解锁开屏根治一环）═══
     * heads-up 横幅把触点/返回键挡下（绝不点掉横幅——红线保持），但横幅
     * 3~8s 自动消散后无人再扫 = 广告白看到底。3 针重查（+2.5/+5/+9s）：
     * 横幅已散 + 前台未换 + 窗口未切 + 主开关在 → bumpScanChain + 立即补扫。
     * 每包 6s 一轮防针堆积；重排成功打一条日志（10s 节流）。 */
    private static final long[] BANNER_RECHECK_AT = {2500, 5000, 9000};

    private void scheduleBannerClearRecheck(final String pkg, long now) {
        try {
            Long last = bannerRequeueAt.get(pkg);
            if (last != null && now - last.longValue() < 6_000L) return;
            bannerRequeueAt.put(pkg, now);
            if (bannerRequeueAt.size() > 24) bannerRequeueAt.clear(); // 防膨胀
            final String cls = winClassOf(pkg);
            for (int i = 0; i < BANNER_RECHECK_AT.length; i++) {
                handler.postDelayed(() -> {
                    AdSkipService s = instance;
                    if (s == null) return;
                    try {
                        s.bannerClearRecheck(pkg, cls);
                    } catch (Throwable ignored) {
                    }
                }, BANNER_RECHECK_AT[i]);
            }
        } catch (Throwable ignored) {
        }
    }

    private void bannerClearRecheck(String pkg, String cls) {
        if (!masterOn) return;
        if (screenRxOk && !screenInteractive) return;    // 审核轮 r1：息屏让路（与探针对称——解锁重入接管）
        if (!pkg.equals(lastFgPkg)) return;              // 前台已换
        if (cls != null && cls.length() > 0 && !cls.equals(winClassOf(pkg))) return; // 窗口已切
        if (keyguardBlocking()) return;                  // 又锁上了（等解锁重入）
        if (headsUpBanner(null) != null) return;         // 横幅还在——下一针再看
        /* 横幅已散：重排扫描链 + 立即补扫（幂等：冷却/复验门自然挡住重复
         * 出手；代际护栏自动作废旧链）。连击退避同步清零——横幅消散 =
         * 挡点条件已根本变化，旧连击不约束新一轮出手（真实时序上重查针
         * 与末次失败天然隔 ≥1.5s，此处清零是给同帧恢复场景兜底）。 */
        dispatchFailStreak.remove(pkg);
        bumpScanChain(pkg);
        /* 审核轮 r1 口径注：此处 tryScan 不预检 profileSuppressScan——
         * 这是恢复路径（被横幅挡下 = 已知有可处置决策/兜底在等），CLEAN
         * 档案窗的探针梯在横幅期已被消耗，本针是唯一救回通道；量界
         * 3 针/6s/包（仅前台包可达此），与解锁针同量级。 */
        tryScan(pkg, winAt(pkg), 0);
        try {
            long now = System.currentTimeMillis();
            Long lastLog = bannerClearLogAt.get(pkg);
            if (lastLog == null || now - lastLog.longValue() >= 10_000L) {
                bannerClearLogAt.put(pkg, now);
                if (bannerClearLogAt.size() > 24) bannerClearLogAt.clear(); // 防膨胀
                appendLog("🔔 横幅已消散（" + shortPkg(pkg) + "）：恢复开屏处置——重排扫描链立即补扫");
            }
        } catch (Throwable ignored) {
        }
    }

    /* ═══ v7.15.11 切换app前台回归探针（HyperOS/Android17 实锤：回切不发
     * STATE_CHANGED → lastFgPkg 停在旧应用 → 本应用内容事件被前台门全量
     * 丢弃 = 回切开屏整窗漏拦；v7.12.0 前台竞速晋升在 v7.15.0 版本线重制
     * 时遗失，本轮以【阳性证据探针】重建并泛化——竞速与无事件两种形态
     * 一并覆盖）═══
     *  · 竞速登记：STATE_CHANGED 因活动窗口归属未切换被丢弃时记 fgPending
     *    （3s 窗口）——后续该包 CONTENT_CHANGED 走紧急探针（包级 1s）；
     *  · 兜底探针：非竞速场景包级 5s + 全局 1.5s 双节流；
     *  · 晋升判定：getRootInActiveWindow 根节点包名 == 该包才晋升（阳性
     *    证据唯一——查询失败/根为空绝不误晋升，与 isActiveWindowOf 的
     *    fail-open 语义相反，专为本路径设计）；
     *  · 晋升动作 = 与「进入应用」同语义完整重置（解锁重入镜像）+ 日志。 */
    private static final long FG_PENDING_WIN_MS = 3000L;

    void noteFgRaceCandidate(String pkg, String cls, long now) {
        if (pkg == null || pkg.length() == 0) return;
        fgPendingPkg = pkg;
        fgPendingCls = cls == null ? "" : cls;
        fgPendingAt = now;
        /* ═══ v7.15.13 竞速自检梯（app→app 跳转「没触发」根治）═══
         * 跳转动画期 STATE_CHANGED 因活动窗口归属未切换被竞速丢弃后，
         * 静态开屏（WebView/纯图——#115 dkXAy 实锤微树空壳零内容事件）
         * 前 2~3s 常零 CONTENT_CHANGED → 被动等事件晋升 = 整窗漏拦。
         * 三针主动自检（+0.5/+1.2/+2.4s，各一次 binder 根查询，阳性证据
         * 才晋升），竞速窗 3s 内最坏 +3 次 binder ≈ 1 次/秒——红线内。 */
        handler.postDelayed(() -> raceSelfProbe(pkg), 500L);
        handler.postDelayed(() -> raceSelfProbe(pkg), 1200L);
        handler.postDelayed(() -> raceSelfProbe(pkg), 2400L);
    }

    /** v7.15.13 竞速自检针：不等待 CONTENT_CHANGED——主动查根属。过闸
     *  条件与 CONTENT 探针完全同源（阳性证据唯一/资格门/息屏/弹窗链让
     *  路/共享 fgProbeAt 节流防同瞬双探），晋升后与回切同语义完整重置。 */
    private void raceSelfProbe(final String pkg) {
        try {
            if (!masterOn) return;
            if (pkg == null || !pkg.equals(fgPendingPkg)) return; // 已被新竞速取代/已消化
            if (pkg.equals(lastFgPkg)) return;                     // 已进入正常处理
            long now = System.currentTimeMillis();
            if (now - fgPendingAt > FG_PENDING_WIN_MS) return;     // 竞速窗已过
            if (screenRxOk && !screenInteractive) return;          // 息屏走解锁重入
            if (now < popupUntilGet(pkg)) return;                   // 弹窗链自扫该包
            Long last = fgProbeAt.get(pkg);
            if (last != null && now - last.longValue() < 350L) return; // 与 CONTENT 探针共享节流
            if (!isRearmableApp(pkg, fgPendingCls)) return;
            fgProbeAt.put(pkg, now);
            if (fgProbeAt.size() > 32) fgProbeAt.clear(); // 防膨胀
            if (!fgProbeSaysActive(pkg)) return;
            promoteForeground(pkg, fgPendingCls, now,
                    "🔄 跳转接管（" + shortPkg(pkg) + "）：前台竞速自检阳性（跳转动画期窗口事件被丢弃）→ 立即补扫");
        } catch (Throwable ignored) {
        }
    }

    private boolean fgProbeSaysActive(String pkg) {
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r == null) return false; // 阴性证据缺失 ≠ 阳性——绝不误晋升
            CharSequence rp = r.getPackageName();
            return rp != null && pkg.contentEquals(rp);
        } catch (Throwable t) {
            return false;
        }
    }

    /** CONTENT_CHANGED 非前台分支的回归探针入口（节流后单次 binder：包级
     *  竞速 1s / 兜底 5s + 全局 1.5s——最坏 ~0.67 次/秒，红线内）。 */
    void maybeResyncForeground(String pkg, String cls, long now) {
        try {
            if (!masterOn) return; // 暂停期零扫描零晋升（追踪由窗口事件自理）
            if (pkg == null || pkg.equals(lastFgPkg)) return;
            if (screenRxOk && !screenInteractive) return; // 息屏回归走解锁重入——不探
            /* 资格门（与 rearm 同键闸——桌面/白名单/系统噪声/零扫描宿主/
             * 相机/聊天主界面/小程序容器不是回归目标） */
            if (!isRearmableApp(pkg, cls)) return;
            boolean urgent = pkg.equals(fgPendingPkg)
                    && now - fgPendingAt < FG_PENDING_WIN_MS;
            /* v7.15.13 阴性快重试：切屏动画期首探阴性（根还属旧应用）后
             * 事件仍在流入 → 3s 窗内 800ms 快档连探（旧 5s 档在 5s 开屏
             * 上等于放弃）；阳性/晋升即清阴性标记。 */
            Long negAt = fgProbeNegAt.get(pkg);
            boolean fastRetry = !urgent && negAt != null && now - negAt.longValue() < 3_000L;
            long perPkg = urgent ? 1_000L : (fastRetry ? 800L : 5_000L);
            Long last = fgProbeAt.get(pkg);
            if (last != null && now - last.longValue() < perPkg) return;
            if (!urgent && !fastRetry && now - fgProbeGlobalAt < 1_500L) return;
            fgProbeAt.put(pkg, now);
            if (fgProbeAt.size() > 32) fgProbeAt.clear(); // 防膨胀
            if (!urgent && !fastRetry) fgProbeGlobalAt = now;
            if (!fgProbeSaysActive(pkg)) {
                fgProbeNegAt.put(pkg, now);
                if (fgProbeNegAt.size() > 32) fgProbeNegAt.clear(); // 防膨胀
                return;
            }
            promoteForeground(pkg, cls, now,
                    "🔄 切换app回归（" + shortPkg(pkg) + "）：前台追踪已校正（回切未伴随窗口事件）→ 立即补扫");
        } catch (Throwable ignored) {
        }
    }

    /** 阳性证据到手：按「进入应用」同语义完整重置（解锁重入镜像）。
     *  审核轮 r1【致命-1 修复】窗口类语义还原：CONTENT_CHANGED 的
     *  className 是【源 View 类】（TextView/RecyclerView——AOSP
     *  ViewRootImpl 语义），不是窗口类。直接写入会静默绕过聊天主界面/
     *  小程序容器/密码窗三道类名级零扫描门 + 根窗口返回键安全门。
     *  窗口类只从两个真实来源取：①竞速登记捕获的 STATE_CHANGED 窗口类
     *  （fgPendingCls——回切竞速形态，即真实当前窗口）；②该包上次真实
     *  窗口事件登记的 winClass（沿用——回切窗大概率未变，聊天/相机/
     *  小程序门照常生效）。两者皆无（从未见过该包的窗口事件）→ 不写
     *  winClass（winClassOf 空——下游门按保守分支走）。rootWinCls 同步
     *  写入：回切首窗 = 入口窗语义（禁返回键保守向——v3.6.0 毒链防线
     *  在晋升路径同样在岗）。 */
    private void promoteForeground(String pkg, String cls, long now, String viaLog) {
        if (pkg.equals(lastFgPkg)) return;
        lastFgPkg = pkg;
        lastAppPkg = pkg;
        fgProbeNegAt.remove(pkg); // v7.15.13：阳性到手，阴性快重试窗关闭
        String raceCls = pkg.equals(fgPendingPkg) ? fgPendingCls : null;
        String winCls = (raceCls != null && raceCls.length() > 0) ? raceCls : winClassOf(pkg);
        if (winCls.length() > 0) {
            winClass.put(pkg, winCls);
            rootWinCls.put(pkg, winCls); // 回切首窗 = 入口窗（禁返回键保守向）
        }
        windowAt.put(pkg, now);
        gaveUp.put(pkg, Boolean.FALSE);
        clickTries.put(pkg, 0);
        backTries.put(pkg, 0);
        clearTapTrail(pkg);
        clearTapGuards(pkg);
        cdLastN.remove(pkg);
        cdAnchorAt.remove(pkg);
        cdWaitUntil.remove(pkg);
        winClsLearned.remove(pkg);
        chainKilledAt.remove(pkg);
        nudgeAt.remove(pkg);
        rescueAt.remove(pkg);
        interWinAt.remove(pkg);
        adClosedAt.remove(pkg);
        dispatchFailStreak.remove(pkg); // v7.15.11：新窗连击清零
        fgPendingPkg = null;            // 竞速候选已消化
        fgProbeNegAt.remove(pkg);       // v7.15.13：快重试窗随晋升关闭
        bumpScanChain(pkg);
        if (!profileSuppressScan(pkg, winClassOf(pkg))) tryScan(pkg, now, 0);
        appendLog(viaLog);
    }

    private boolean lastGesture = false;

    /** v3.8.0：手势真实触点【首选】（GKD clickCenter 方法论）。
     *  v3.4.0~v3.7.0 以 performAction 优先：无障碍代点返回 true 也可能被应用
     *  无声吞掉——issue#10 二轮实锤「4 次点击成功，广告纹丝不动，手动点同一
     *  按钮立刻生效」。performAction 走无障碍点击通道，广告 SDK/自绘控件普遍
     *  只认真实触点。改为先 dispatchGesture 点【原标签控件】中心（=手动点击的
     *  完全等价物），派发失败（系统手势限流）才降级 performAction 兜底。
     *  假跳过候选（d.node=null）只走手势——大容器祖先 performAction=点开广告
     *  ══ v5.9.0 设备自适应触点（用户实锤：设备不同→多点或无点=误触）═══
     *  原始几何中心 → TapPoint 四层防线：L1 祖先可见交集 + L2 节点窗口钳制
     *  （分屏/折叠另半区拒点）+ L3 安全选点（复验象限轮换/边缘内缩/过小拒点）
     *  + L4 覆盖层判定（gestureTap 内，输入法/悬浮窗拦截拒发）。拒发手势
     *  ≠放弃：自动降级 performAction 无障碍代点（不经屏幕坐标，点得到
     *  语义目标）；再失败由复验扫描换候选——绝不在危险坐标上盲刺。 */
    private boolean clickNode(Decision d) {
        lastGesture = false;
        /* v6.1.0 G 出手前新鲜度复核（误点根治——用户实锤：设备类型/分辨率
         * 原因导致误点）：扫描→决策→派发链路有 26~56ms 人手延迟 + 300ms 冷却，
         * 入场缩放/位移动画中的按钮已位移——人眼按「它现在在哪」点击，机器
         * 不能按「它曾在哪」点：出手时 re-fetch 最新 bounds，中心位移超过
         * 旧矩形短边 50%+24dp = 动画位移中 → 放弃本次出手等下一轮扫描用新
         * bounds（点空→复验→再点的多点级联从源头掐断；盲点旧坐标=误点）；
         * 节点已不可见/已消失 → 绝不按旧坐标盲刺。 */
        if (d.bounds != null && d.node instanceof NodeAdapter) {
            try {
                AccessibilityNodeInfo n = ((NodeAdapter) d.node).n;
                if (n != null) {
                    Rect fresh = new Rect();
                    n.getBoundsInScreen(fresh); // void API：结果写入 fresh
                    if (n.isVisibleToUser() && fresh.width() > 0 && fresh.height() > 0) {
                        if (HumanTap.isStaleBounds(d.bounds,
                                new int[]{fresh.left, fresh.top, fresh.right, fresh.bottom},
                                screenDensity)) {
                            return false; // 按钮位移动画中：让路本轮，下一轮扫描用新 bounds
                        }
                    } else {
                        return false; // 节点已不可见/已消失：绝不按旧坐标盲刺
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (d.bounds != null) {
            int[] pt = adaptiveTapPoint(d); // v6.0.0：实时bounds+dp自适应安全域+轨迹去重
            if (pt != null && gestureTap(pt[0], pt[1])) {
                lastGesture = true;
                energyGestures++;
                recordTapTrail(pt[0], pt[1]); // v6.0.0：同坐标绝不重复盲刺（「多点」根治）
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

    /** v5.9.0 复验象限轮换基数：本窗第几次出手（execute 出手前写入 clickTries
     *  当前值——同候选复验换象限点，不再盲点同一坐标=「多点」根治） */
    private int clickAttemptNow = 0;

    /**
     * v6.0.0 设备自适应触点：Decision → 安全触点 {x,y}；null=拒发手势。
     * 三重升级（用户实锤：设备类型/分辨率原因→多点或误点；1:1 人操作）。
     *  ① 实时几何追踪：出手前 refreshLive() 刷新节点【现在】的 bounds
     *    （入场缩放/位移动画后旧坐标=点空→复验→多点级联的根因之一）；
     *  ② L1 可见交集（含刷新后的可见性）+ L2 节点窗口钷制（不变）；
     *  ③ L3 pickDp dp 自适应安全域（HumanTap 密度归一——固定 px 常量在
     *    160dpi/480dpi 上行为漂移的根治）+ 触点轨迹去重（同坐标 ±8dp
     *    内绝不重复盲刺，换象限轮换而非原地连点）。 */
    private int[] adaptiveTapPoint(Decision d) {
        /* ① 实时几何：节点存活且刷新成功 → 用【现在】的 bounds */
        int[] labelBounds = d.bounds;
        if (d.node instanceof NodeAdapter) {
            try {
                NodeAdapter na = (NodeAdapter) d.node;
                if (na.refreshLive()) {
                    int[] fresh = na.bounds();
                    if (fresh != null && fresh[2] - fresh[0] > 0 && fresh[3] - fresh[1] > 0) {
                        labelBounds = fresh;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        int[] win = nodeWindowBounds(d.node);
        int[] rect = TapPoint.tapRect(d.node, labelBounds, win);
        if (rect == null) {
            // 日志口径：拒发原因可诊断（同包 10s 节流）
            Long last = overlayLogAt.get(lastGesturePkg == null ? "" : lastGesturePkg);
            long now = System.currentTimeMillis();
            if (last == null || now - last > 10000) {
                overlayLogAt.put(lastGesturePkg == null ? "" : lastGesturePkg, now);
                appendLog("🛡 触点拒发（" + shortPkg(lastGesturePkg) + "）：标签不可见/被裁剪/在窗口外"
                        + "（分屏·折叠形态）→ 改用无障碍代点（绝不在危险坐标盲刺）");
            }
            return null;
        }
        /* ②+③：dp 自适应选点 + 轨迹去重轮换 */
        String tp = lastGesturePkg == null ? "" : lastGesturePkg;
        java.util.ArrayList<int[]> trail = tapTrail.get(tp);
        int tol = HumanTap.dupTolPx(screenDensity);
        for (int k = 0; k < TapPoint.ROTATION_SLOTS; k++) {
            int[] pt = TapPoint.pickDp(rect, clickAttemptNow + k, screenDensity);
            if (pt == null) continue;
            if (HumanTap.isDupPoint(trail, pt[0], pt[1], tol)) continue; // 同坐标已刺过：换象限
            return pt;
        }
        // 全轮换档位均已刺过/非法：拒点（换下一候选，绝不原地连点）
        return null;
    }

    /** v6.0.0 触点轨迹登记（clickNode 手势出手成功后调用；窗口切换时清空）。
     *  上限 24 点防超长窗口内存膨胀；包数上限 64 防长会话内存泄漏
     *  （轨迹是同窗去重优化数据，超限整体重置=安全降级，代价仅为
     *  下一窗首轮换重新起算——防护性收敛，功耗画像零恶化）。 */
    private void recordTapTrail(int x, int y) {
        if (tapTrail.size() > 64) tapTrail.clear(); // 长会话防护：包数封顶整体重置
        String tp = lastGesturePkg == null ? "" : lastGesturePkg;
        java.util.ArrayList<int[]> trail = tapTrail.get(tp);
        if (trail == null) {
            trail = new java.util.ArrayList<>();
            tapTrail.put(tp, trail);
        }
        trail.add(new int[]{x, y});
        if (trail.size() > 24) trail.remove(0);
    }

    /** v6.0.0：新窗开启——触点轨迹随 clickTries 同步清零（同包） */
    private void clearTapTrail(String pkg) {
        tapTrail.remove(pkg);
    }

    /** v6.1.0：新窗开启——同指纹目标静默表/结果静默表同步清零（同包：
     *  多点防线只在同一广告窗内生效，跨窗（新广告窗）零残留） */
    private void clearTapGuards(String pkg) {
        String p = pkg == null ? "" : pkg;
        try {
            java.util.Iterator<java.util.Map.Entry<String, Long>> it = firedTargetAt.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getKey().startsWith(p + "|")) it.remove();
            }
        } catch (Throwable ignored) {
        }
        lastGestureOkAt.remove(pkg);
        /* v7.2.0 根治（病灶①的另一半）：此处绝不清 feedArmed——P1 点角标拉起的
         * 关闭菜单【本身就是新窗】（Dialog/Popup → TYPE_WINDOW_STATE_CHANGED →
         * 本方法），armed 一进菜单就被清零 = P2 永远等不到 = 「朋友圈广告全没
         * 拦截」用户实锤的最直接死因。armed 只由 P1_ARM_MS（8s）过期、P2 完成、
         * 或切到【其他应用】复位（maybeFeedScan 的 pkg.equals(feedArmedPkg) 门）。 */
    }

    /** v5.9.0：标签所属窗口 bounds——取节点自身窗口（node.getWindow()，
     *  分屏/多窗/弹窗最精确），拿不到时回退活跃根窗。返回 null=未知
     *  （旧 ROM）→ 钳制跳过（其余防线仍在）。 */
    private int[] nodeWindowBounds(NodeView node) {
        try {
            if (node instanceof NodeAdapter) {
                AccessibilityWindowInfo w = ((NodeAdapter) node).n.getWindow();
                if (w != null) {
                    Rect r = new Rect();
                    w.getBoundsInScreen(r);
                    if (!r.isEmpty()) {
                        return new int[]{r.left, r.top, r.right, r.bottom};
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return activeWindowBounds();
    }

    /** v5.9.0：当前活跃窗（广告窗）bounds 兜底源。 */
    private int[] activeWindowBounds() {
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r == null) return null;
            Rect rr = new Rect();
            r.getBoundsInScreen(rr);
            if (rr.isEmpty()) return null;
            return new int[]{rr.left, rr.top, rr.right, rr.bottom};
        } catch (Throwable t) {
            return null;
        }
    }

    /** v3.7.0 手势结果回调（静态内部类，规避 d8 8.2 匿名类 bug）。
     *  v3.14.0 露头就秒提速：手势被系统中断（限流/冲突）→ ① 退还点击冷却
     *  （触点未触达，冷却白占 900ms）；② 350ms 后快速补扫重试（此前要等
     *  下轮扫描 1.2s+，issue#21 实锤「下轮扫描重试」最慢 4s+） */
    private static final class TapGestureCallback extends GestureResultCallback {
        private final String pkg;

        TapGestureCallback(String pkg) {
            this.pkg = pkg;
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            try {
                AdSkipService s = instance;
                if (s == null) return;
                s.appendLog("⚠ 手势被系统中断（" + s.shortPkg(pkg) + "）：真实触点未触达（系统手势限流/冲突），350ms 后快速重试");
                s.lastClickAt.remove(pkg); // ① 退冷却：未触达的点击不占 900ms
                s.handler.postDelayed(() -> { // ② 快速补扫（同窗扫描链直接重扫）
                    AdSkipService sv = instance;
                    if (sv != null) sv.tryScan(pkg, sv.winAt(pkg), -1);
                }, 350);
            } catch (Throwable ignored) {
            }
        }
    }

    /** dispatchGesture 坐标点击（API 24+；真实触点，performAction 拦不住）。
     *  v3.7.0 根因修复（issue#10：「已点击跳过×4 无效果」）：
     *   ① 路径非零长度——单点路径（只有 moveTo）在部分 ROM 上不触发点击
     *    （业界验证：需 lineTo 形成真实触点轨迹）；
     *   ② 坐标边界钳制——越界坐标（学习位置/旧指纹）直接被系统丢弃；
     *   ③ 结果回调——被系统取消不再静默（诊断日志供下轮迭代）。
     *   ④ 前提：accessibility_service_config 已补 canPerformGestures=true。
     *  ══ v6.0.0 1:1 人手模拟（用户实锤：设备类型/分辨率原因→多点、误点）═══
     *   v3.7~v5.9 的触点签名是「固定 40ms 起始延迟 + 固定 110ms 时长 +
     *   完美水平 1px 轨迹」的千次一模一样机器人指纹——反自动化广告 SDK
     *   触点检测会无声吞掉这种点（点了没反应→复验→机器枪多点级联）。
     *   HumanTap.tapSpec 每次出手生成人手域参数：
     *    · 起始延迟 26~56ms（真人抬指→落指反应散布，替代固定 40ms）；
     *    · 触点时长 95~160ms（首击轻点域 / 重试更用力域，替代固定 110ms）；
     *    · ±2px 微抖轨迹（真人手指落点散布；远小于安全内缩域，绝不出
     *      目标面；轨迹带 1px 微斜——完美水平线也是机器人指纹）；
     *    · 覆盖层 Z 序判定用【抖动后】的最终触点（判的就是落屏点）；
     *    · 抖动后坐标越界回退/钳制（边界防线不因抖动而破）。 */
    private boolean gestureTap(int x, int y) {
        try {
            // v3.13.0 息屏熔断（纵深防御）：息屏时绝无可见广告，真实触点只会
            // 落在来电/闹钟等敏感窗上（误点=挂断电话/关闭闹钟）
            if (!isScreenOn(this)) return false;
            if (x <= 0 || y <= 0) return false;
            if (screenWidth > 0 && x >= screenWidth) x = screenWidth - 2; // 边界钳制
            if (screenHeight > 0 && y >= screenHeight) y = screenHeight - 2;
            // v6.0.0 人手域参数（种子=实时钟——每次出手都不同=真人散布）
            int[] spec = HumanTap.tapSpec(humanSeed(), clickAttemptNow);
            int sx = x + spec[2], sy = y + spec[3];
            // 抖动越界防御：负坐标/出屏回退原点，贴边钳制
            if (sx <= 0 || sy <= 0 || (screenWidth > 0 && sx >= screenWidth - 1)
                    || (screenHeight > 0 && sy >= screenHeight - 1)) {
                sx = x;
                sy = y;
            }
            // v3.13.0 验证码横幅防护③ → v5.9.0 设备差异误触防线泛化 →
            // v6.0.0 判定对象=抖动后的最终落点：触点被 Z 序更高窗口（状态栏/
            // heads-up 横幅/输入法/悬浮球/他应用 overlay/折叠任务栏）覆盖时
            // 拒发手势——真实触点会被覆盖物截走（点掉验证码横幅·键盘上乱打
            // 字·误触他应用悬浮窗的事故路径）。拒发后 clickNode 自动降级
            // performAction 代点（走无障碍通道不经屏幕坐标，绝不碰覆盖物）。
            if (pointCoveredByUpperOverlay(sx, sy)) {
                overlayGuardLog(lastGesturePkg, System.currentTimeMillis());
                return false;
            }
            Path p = new Path();
            p.moveTo(sx, sy);
            p.lineTo(sx + 2, sy + 1 + (spec[3] & 1)); // 非零长度微斜轨迹（真人微动）
            GestureDescription gd = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p, spec[0], spec[1]))
                    .build();
            return dispatchGesture(gd, new TapGestureCallback(lastGesturePkg), null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** v6.0.0 人手种子：实时钟混合（每次出手参数都不同=真人散布；tapSpec
     *  自身纯函数——测试侧用固定种子可精确复现）。 */
    private long humanSeed() {
        return System.nanoTime() ^ (System.currentTimeMillis() << 21) ^ 0x5DEECE66DL;
    }

    /** 手势诊断用包名（gestureTap 调用前由 execute 设置） */
    private volatile String lastGesturePkg = "";

    private static String reasonZh(String r) {
        if (r == null) return "";
        /* v7.15.5：订阅规则 reason 可能携带「·组名」尾（rule_sub_gkd_remote·开屏广告）
         * ——startsWith 匹配 + 尾部组名解析（组名进日志：开屏/插屏严格分域）。 */
        if (r.startsWith("rule_sub_gkd_remote") || r.startsWith("rule_sub_gkd_builtin")) {
            int dot = r.indexOf('·');
            String g = dot >= 0 ? r.substring(dot + 1) : "";
            return "GKD 订阅规则命中（" + (r.startsWith("rule_sub_gkd_remote") ? "市场源优先" : "内置源兑底")
                    + (g.length() > 0 ? "·组「" + g + "」" : "")
                    + "——选择器定位控件）";
        }
        if (r.equals("rule_global_exact")) return "内嵌规则·文案精确命中（0ms 即点）";
        if (r.equals("rule_app_exact")) return "内嵌规则·分应用文案命中";
        if (r.equals("rule_global_pattern")) return "内嵌规则·倒计时形态命中（门控后出手）";
        if (r.equals("rule_global_contains")) return "内嵌规则·文案包含命中";
        if (r.equals("rule_global_vid")) return "内嵌规则·vid 跳过键（零文本图片键）";
        if (r.equals("rule_sdk_close_vid")) return "内嵌规则·SDK 窗图片关闭键";
        if (r.equals("rule_sub_gkd")) return "GKD 订阅规则命中（选择器定位控件）";
        if (r.equals("feed_p1_badge")) return "信息流广告披露角标（v7.1.0·自 v6.2.0 已验证实现移植）"; // feed 决策专用
        if (r.equals("feed_p2_close")) return "信息流广告关闭菜单中性项（v7.1.0·自 v6.2.0 已验证实现移植）";
        if (r.equals("skip_text")) return "文本跳过键";
        if (r.equals("fake_skip_tap")) return "假跳过手势直点（标签坐标）";
        if (r.equals("sdk_window")) return "SDK 广告窗指纹";
        if (r.equals("sdk_window_enforce")) return "SDK 广告窗直断兜底（扫描链失效保险）";
        if (r.equals("sdk_window_learned")) return "学习窗口指纹（快断）";
        if (r.equals("gyro_immediate")) return "翻转/摇一摇文案立即直断";
        if (r.equals("gyro_break")) return "陀螺仪诱导/假跳过直断";
        if (r.equals("corner_image_skip")) return "角落图片跳过键";
        if (r.equals("rand_graphic_skip")) return "泛位置图片跳过键（动态图开屏·位置随机）"; // v7.15.6 Keep/CSDN
        if (r.equals("learned_pos")) return "学习位置（你手动教过）";
        if (r.equals("splash_close")) return "开屏×关闭键";
        if (r.equals("badge_close")) return "角标在场×关闭键";      // v7.15.6 补齐：L1.5 产出旧裸显英文
        if (r.equals("popup_badge")) return "弹窗广告角标关闭键";    // v7.15.6 补齐：弹窗路径产出
        if (r.equals("popup_cta")) return "弹窗 Dialog+营销话术关闭键"; // v7.15.6 补齐：弹窗路径产出
        if (r.equals("hot_zone")) return "全屏热区自绘广告";
        return r;
    }

    /** 返回键全局节流：600ms 内最多一次 */
    private long lastBackAt = 0;
    /* v7.15.2：直断抑制诊断节流（全局 10s 一条）+ 锁屏让路诊断节流（每包） */
    private volatile long lastBackDiagAt = 0;
    private final java.util.HashMap<String, Long> kgYieldLogAt = new java.util.HashMap<>();

    private boolean backAllowed(long now) {
        if (now - lastBackAt < 600) return false;
        lastBackAt = now;
        return true;
    }

    private boolean popupCooldownOK(String pkg, long now) {
        Long t = lastPopupAt.get(pkg);
        return t == null || now - t >= POPUP_COOLDOWN_MS;
    }

    /* ───────── 智能学习：观察手动点击 + className 指纹 ───────── */

    private void learnFromManualClick(String pkg, AccessibilityEvent event, long now) {
        /* v7.15.5：「习惯学习」开关的唯一管辖点（v8.1.0 语义恢复）——只管
         * 学习写入（文本规则 + 位置学习都从本入口进），绝不管扫描/出手。
         * 旧版（v7.15.0~v7.15.4）开关反而在 tryScan 里门控整个开屏扫描、
         * 这里却没人管——名实完全颠倒（见 splashLive 处注释）。 */
        if (!learningEnabled()) return;
        if (now - winAt(pkg) > LEARN_WINDOW_MS) return;
        // v4.1.0：相机/拍摄窗里的手动操作绝不当「跳过键」学习
        //（issue#30：快门/翻转镜头/相册按钮被学成位置 → 下次进相机乱点）
        if (AdDecisionCore.isCameraPkg(pkg) || AdDecisionCore.isCameraClass(winClassOf(pkg))
                || AdDecisionCore.isCameraClass(cs(event.getClassName()))) return;
        // v5.0.0 小程序容器保护：容器内的手动操作绝不当「跳过键」学习
        //（团购券页上的点击位置学了 = 下次进容器乱点）
        if (MiniProgramGuard.isMiniProgramWindow(pkg, winClassOf(pkg))
                || MiniProgramGuard.isMiniProgramWindow(pkg, cs(event.getClassName()))) return;

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
         * 教过的位置下次同窗 500ms 直点，优先于文本候选盲点）。
         * v5.3.0：文案是强信号（「跳过/关闭」字样）——文本路径免挂起验证，
         * 立即提交；零文本路径才走 1.5s 事后验证。 */
        learnPositionImmediateFromText(pkg, srcNode, now);
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
                /* v5.5.0 LearnGovernor：SDK 特征泛化标记——窗口类名命中
                 * SDK 通用骨架（Dialog/Popup/tt_* 等）的学习规则跨应用
                 * 可复用（A 应用学会的 SDK 骨架，B 应用同 SDK 广告直接
                 * 预拦截），新生期豁免（骨架强信号首次即可信）。 */
                r.put("gen", com.zai.adshield.core.LearnGovernor.sdkGeneralize(cls));
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
            /* v7.15.6 口径统一：热区误触观察窗用 splashDeadlineMs（SDK 广告窗
             * 20s）——旧版散落的裸 WINDOW_MS 是 v7.15.5「唯一口径」收编漏网
             * 的第四处（SDK 窗 12~20s 的热区误触观察不到）。 */
            if (now - winAt(pkg) > splashDeadlineMs(winClassOf(pkg))) return;
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

    /** v7.15.5：开屏观察窗截止（普通窗 12s；SDK 广告窗 20s——视频开屏的
     *  跳过键常在视频末 5~15s 才露头；广告窗本身就是广告，放宽只是多等
     *  不是多扫普通窗）。开屏窗判定（splashLive/nudge 让路/插屏让路）的
     *  唯一口径——三处必须同函数，绝不散落。 */
    private static long splashDeadlineMs(String winClass) {
        return AdDecisionCore.isAdActivityClass(winClass) ? VIDEO_SPLASH_WINDOW_MS : WINDOW_MS;
    }

    /** 「习惯学习」开关（v8.1.0/v7.15.5 语义：只管 learnFromManualClick
     *  学习写入路径，绝不门控开屏扫描——误门控即「关学习 = 全 App 开屏
     *  零扫描零点击零日志」回归）。 */
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
        double[] xy = posCandidateFrom(pkg, src, now);
        if (xy == null) return;
        /* ═══ v5.3.0 零文本位置学习改为【挂起 + 事后验证】（#57/#58 实锤）═══
         * 豆包聊天界面手动点击（同秒 (17%,82%) 与 (41%,82%) 两处）被当
         * 「图片跳过键」学走 → 次启同窗 500ms 直点聊天气泡 = 乱点复发。
         * 分界线：真跳过键点击必关窗（窗类切换/树体缩水 ≥40%）；聊天
         * 点击必不关窗。挂起 1.5s：验证通过才提交，超时静默丢弃。
         * 点击时同步取树画像（预算 80）：广告窗 = 简单树无滚动无输入；
         * 内容页 = 有滚动/输入——窗类切换时仅广告窗画像可提交（用户自己
         * 导航到新页绝不举以为是跳过键干的）。 */
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            int nodes = 0;
            boolean scroll = false, edit = false;
            if (root != null) {
                Ctx c2 = new Ctx();
                c2.screenW = screenWidth;
                c2.screenH = screenHeight;
                c2.pkg = pkg;
                c2.budgetNodes = 80;
                ScanFind f2 = AdDecisionCore.walk(new NodeAdapter(root), c2);
                nodes = f2.nodeCount;
                scroll = f2.scrollPresent;
                edit = f2.editPresent;
            }
            PendingPos pp = new PendingPos();
            pp.x = xy[0];
            pp.y = xy[1];
            pp.cls = winClassOf(pkg);
            pp.at = now;
            pp.nodes = nodes;
            pp.adLike = nodes > 0 && nodes <= AdDecisionCore.AD_LIKE_MAX_NODES * 3 && !scroll && !edit;
            pendingPos.put(pkg, pp);
            final String p2 = pkg;
            handler.postDelayed(() -> {
                PendingPos p = pendingPos.get(p2);
                if (p != null && System.currentTimeMillis() - p.at >= PowerGovernor.POS_VERIFY_MS) {
                    pendingPos.remove(p2);
                    posLearnDropped++;
                    Long lastDrop = posDropLogAt.get(p2);
                    long nm = System.currentTimeMillis();
                    if (lastDrop == null || nm - lastDrop > 60000) {
                        posDropLogAt.put(p2, nm);
                        appendLog("🛡 位置学习已拦截（" + shortPkg(p2) + "）：点击后 1.5s 窗口未关闭——普通界面点击，不当跳过键学习");
                    }
                }
            }, PowerGovernor.POS_VERIFY_MS + 60);
        } catch (Throwable ignored) {
        }
    }

    /** v5.3.0 挂起验证结果检查：窗类切换或树体缩水 → 提交；否则丢弃。
     *  由 WINDOW_STATE_CHANGED / CONTENT_CHANGED 钩子调用。 */
    private void posVerifyOnWindowChange(String pkg, String newCls, long now) {
        try {
            PendingPos pp = pendingPos.get(pkg);
            if (pp == null) return;
            long dt = now - pp.at;
            if (dt > PowerGovernor.POS_VERIFY_MS) {
                pendingPos.remove(pkg);
                posLearnDropped++;
                return;
            }
            boolean windowChanged = newCls != null && !newCls.equals(pp.cls);
            // 窗类切换须同时具备广告窗画像（内容页导航 ≠ 跳过键关窗）
            boolean pass = PowerGovernor.posCommit(dt, windowChanged && pp.adLike, false, false);
            if (pass) {
                pendingPos.remove(pkg);
                posLearnVerified++;
                commitPositionLearn(pkg, pp.x, pp.y, pp.cls);
            }
        } catch (Throwable ignored) {
        }
    }

    /** v5.3.0 树体缩水验证：同窗广告容器卸载（Dialog 消散/开屏容器移除）。 */
    private void posVerifyOnContentChange(String pkg, long now) {
        try {
            PendingPos pp = pendingPos.get(pkg);
            if (pp == null) return;
            long dt = now - pp.at;
            if (dt > PowerGovernor.POS_VERIFY_MS) return; // 超时路径由 postDelayed 处理
            Long last = posVerifyNudgeAt.get(pkg);
            if (last != null && now - last < 300) return; // 流式内容节流
            posVerifyNudgeAt.put(pkg, now);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            Ctx c2 = new Ctx();
            c2.screenW = screenWidth;
            c2.screenH = screenHeight;
            c2.pkg = pkg;
            c2.budgetNodes = 80;
            ScanFind f2 = AdDecisionCore.walk(new NodeAdapter(root), c2);
            if (PowerGovernor.treeShrunk(pp.nodes, f2.nodeCount)) {
                pendingPos.remove(pkg);
                posLearnVerified++;
                commitPositionLearn(pkg, pp.x, pp.y, pp.cls);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 位置候选提取（公共门：相机/OEM/短信/6s 窗/面积/形状/越界）；
     *  返回归一化坐标 {x,y}，不合法返回 null。 */
    private double[] posCandidateFrom(String pkg, AccessibilityNodeInfo src, long now) {
        if (src == null || screenWidth <= 0 || screenHeight <= 0) return null;
        // v4.1.0：相机/拍摄窗绝不学位置（快门/翻转/相册键不是跳过键）
        if (AdDecisionCore.isCameraPkg(pkg) || AdDecisionCore.isCameraClass(winClassOf(pkg))) return null;
        // v3.13.0（issue#16 实锤）：OEM 系统工具包（安全中心/系统管家）与短信
        // 类包的手动操作绝不当「跳过键」学习——securitycenter 应用详情页被学出
        // (91%,9%)，下次进详情页 500ms 直点=在权限管理页乱点
        if (AdDecisionCore.isOemUtilityPkg(pkg) || AdDecisionCore.isSmsOverlayPkg(pkg)) return null;
        if (now - winAt(pkg) > 6000) return null; // v3.9.0：仅窗口切换后 6s 内（开屏期收紧，防误学主页面控件）
        try {
            Rect r = new Rect();
            src.getBoundsInScreen(r);
            if (r.isEmpty()) return null;
            int[] b = new int[]{r.left, r.top, r.right, r.bottom};
            double area = AdDecisionCore.areaOf(b, screenWidth, screenHeight);
            if (area > AdDecisionCore.POS_LEARN_AREA_MAX) return null; // 大热区=CTA，不学
            // v3.6.0：可学形状扩至底部（部分应用跳过键在右下角，真机反馈）
            if (!AdDecisionCore.learnableSkipShape(b, screenWidth, screenHeight)) return null;
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
                return null;
            }
            return new double[]{x, y};
        } catch (Exception ignored) {
            return null;
        }
    }

    /** v5.3.0 位置学习提交（挂起验证通过后；文本路径直呼）——存储 + 日志 */
    private void commitPositionLearn(String pkg, double x, double y, String cls) {
        try {
            long now = System.currentTimeMillis();
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
            appendLog("📍 位置已学习（" + shortPkg(pkg) + "）：点击后窗口已关闭，验证通过——已记住位置 ("
                    + Math.round(x * 100) + "%," + Math.round(y * 100) + "%) → 下次启动同窗 500ms 直点");
        } catch (Exception ignored) {
        }
    }

    /** 文本键手动点击同步学位置（文案是强信号：文本路径免挂起验证） */
    private void learnPositionImmediateFromText(String pkg, AccessibilityNodeInfo src, long now) {
        double[] xy = posCandidateFrom(pkg, src, now);
        if (xy == null) return;
        commitPositionLearn(pkg, xy[0], xy[1], winClassOf(pkg));
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

    /** v5.9.0 设备差异误触防线（覆盖层 Z 序判定）：触点是否被 Z 序更高
     *  （在广告窗之上）的窗口覆盖。getWindows() 按 Z 序返回（索引小=层级高）
     *  ——先定位活跃窗（我们的广告窗），其上的任何窗口（SystemUI 状态栏/
     *  heads-up 横幅/输入法/悬浮球/他应用 overlay）包含触点 → 手势必被
     *  截走=点到覆盖物。查不到活跃窗（过渡态/特殊 ROM 顺序异常）退化为
     *  旧 SystemUI 顶条判定（v3.13.0 行为保持兜底）。
     *  IPC 成本：一次 getWindows()（v3.13.0 起 headsUpBanner 本就在此路径
     *  消耗同量调用——成本持平）。 */
    private boolean pointCoveredByUpperOverlay(int x, int y) {
        if (screenWidth <= 0 || screenHeight <= 0) return pointUnderHeadsUp(x, y);
        try {
            List<AccessibilityWindowInfo> wins = getWindows();
            if (wins == null || wins.isEmpty()) return pointUnderHeadsUp(x, y);
            int activeIdx = -1;
            for (int i = 0; i < wins.size(); i++) {
                AccessibilityWindowInfo w = wins.get(i);
                if (w != null && w.isActive()) {
                    activeIdx = i;
                    break;
                }
            }
            if (activeIdx < 0) return pointUnderHeadsUp(x, y); // 找不到活跃窗→旧行为
            for (int i = 0; i < activeIdx; i++) {
                AccessibilityWindowInfo w = wins.get(i);
                if (w == null) continue;
                Rect r = new Rect();
                w.getBoundsInScreen(r);
                if (r.isEmpty()) continue;
                if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) return true;
            }
            return false;
        } catch (Throwable t) {
            return pointUnderHeadsUp(x, y); // 旧 ROM getWindows 异常→旧行为
        }
    }

    /** v5.9.0 覆盖层防误触日志（同包 10s 去重；pkg 可能为空） */
    private void overlayGuardLog(String pkg, long now) {
        String key = pkg == null ? "" : pkg;
        Long last = overlayLogAt.get(key);
        if (last != null && now - last < 10000) return;
        overlayLogAt.put(key, now);
        appendLog("🛡 覆盖层防误触（" + (key.length() == 0 ? "系统" : shortPkg(pkg))
                + "）：触点被更高层窗口覆盖（输入法/悬浮窗/状态栏/横幅）→ 已拒发手势"
                + "（改用无障碍代点，绝不点到覆盖物）");
    }

    /** 验证码横幅保护日志（同包 10s 去重） */
    private void codeBannerGuardLog(String pkg, long now) {
        Long last = codeBannerLogAt.get(pkg);
        if (last != null && now - last < 10000) return;
        codeBannerLogAt.put(pkg, now);
        appendLog("🛡 验证码横幅保护（" + shortPkg(pkg) + "）：检测到验证码/校验码文案 → 本窗口绝不扫描绝不点击");
    }

    /** v5.0.0：小程序容器树标识兜底命中日志（同验证码横幅 10s 节流）。 */
    private void miniGuardLog(String pkg, long now) {
        Long last = miniGuardLogAt.get(pkg);
        if (last != null && now - last < 10000) return;
        miniGuardLogAt.put(pkg, now);
        appendLog("🧩 小程序保护（" + shortPkg(pkg) + "）：检测到小程序容器标识 → 本窗口绝不扫描绝不点击");
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
            /* ═══ v4.5.0 通知栏快捷开关（系统开关与 App 开关彻底解耦）═══
             * 用户核心诉求：系统无障碍【一直开着永不碰】，卫士的开关由
             * 通知栏/App 内一键控制——暂停即全停（零扫描零点击），恢复
             * 即全开，全程绝不去系统设置看 10 秒强制警告。
             * 显式 Intent + exported=false 接收器：只有本应用通知能触发。 */
            Intent tg = new Intent(this, MasterToggleReceiver.class);
            tg.setAction(MasterToggleReceiver.ACTION_TOGGLE_MASTER);
            PendingIntent tgPi = PendingIntent.getBroadcast(this, 5, tg,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            boolean on = masterOn;
            Notification.Action act = new Notification.Action.Builder(
                    null, on ? "暂停防护（再点恢复）" : "恢复防护", tgPi).build();
            b.setContentTitle(on ? "秒跳引擎守护中" : "秒跳引擎已暂停")
                    .setContentText(on ? "广告露头就秒跳 · 点「暂停」立即停手（无需关系统设置）"
                            : "零扫描零点击 · 点「恢复」立即守护（无需去系统设置）")
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setColor(0xFF10B981)
                    .addAction(act);
            Notification notif = b.build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_KEEP, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_KEEP, notif);
            }
        } catch (Throwable ignored) {
        }
    }

    /** v4.5.0：主开关翻转后刷新常驻通知（标题/文案/按钮随 masterOn 联动）。
     *  静态入口：MasterToggleReceiver（进程可能只有僵尸实例）也能调用。 */
    public static void refreshKeepNotification(Context ctx) {
        AdSkipService s = instance;
        if (s != null) {
            try {
                s.startKeepAliveForeground();
            } catch (Throwable ignored) {
            }
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

    void appendLog(String msg) { // v7.15.0：包级可见（SubscriptionManager 自动导入日志回写；原 private）
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

    /* ───────── v3.14.0：WRITE_SECURE_SETTINGS 自愈重绑【已移除】─────────
     * 用户反馈：adb 授权要电脑执行太麻烦，实际几乎无人可用。Android 安全
     * 设计下无障碍服务无法被应用自行开启/绑定（BIND_ACCESSIBILITY_SERVICE
     * 签名级权限），唯一正道就是系统设置开关（openA11yDetail 一步直达）。
     * 开启后持续守护由「前台常驻通知 + onUnbind 保留 + 心跳自检 + 断连提醒」
     * 四件套负责（KeepAliveReceiver），退出 App/清理后台重进无需重新开启。 */

    private String shortPkg(String pkg) {
        return shortPkgSt(pkg);
    }

    /** v4.5.0：包名缩写（修「android」歧义——issue#42-44 战绩榜 android(49/0/10)
     *  实为 me.ele.android 等以 .android 结尾的第三方应用，纯展示歧义导致
     *  误判「引擎在系统包乱点」。末段是通用词（android/app/main/ui/mobile/
     *  phone/client/core/sdk/online/lite）时回退取两段）。 */
    static String shortPkgSt(String pkg) {
        if (pkg == null) return "";
        int i = pkg.lastIndexOf('.');
        if (i < 0 || pkg.length() - i - 1 <= 1) return pkg;
        String last = pkg.substring(i + 1);
        if (GENERIC_TAILS.contains(last)) {
            int j = pkg.lastIndexOf('.', i - 1);
            if (j >= 0 && i - j - 1 > 1) return pkg.substring(j + 1);
        }
        return last;
    }

    private static final java.util.HashSet<String> GENERIC_TAILS = new java.util.HashSet<>(
            java.util.Arrays.asList("android", "app", "main", "ui", "mobile",
                    "phone", "client", "core", "sdk", "online", "lite"));
}
