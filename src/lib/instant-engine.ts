/**
 * 秒跳引擎（Instant Skip Engine · v5.3.0 · 李跳跳式三零治理）
 * ─────────────────────────────────────────────────────────────
 * 设计基准（2026-11 市场调研 + 喜马拉雅专案 v3 复盘 + 真机日志 #57/#58 驱动）：
 *  · P-1 探针哨兵前置（v5.3.0）：findAccessibilityNodeInfosByText 系统
 *    原生 C++ 子树搜索（成本≈DFS 的 1/14）先于一切树遍历——文本类广告
 *    全流程零 DFS；CLEAN 档案窗只跑探针（零扫描零调度零日志）。
 *  · P-0.8 宿主窗档案（v5.3.0）：连续 3 次零候选的 (pkg|窗口类) 标记
 *    CLEAN（跨重启保留）——豆包对话列表/微信排行页/拼多多落地页等
 *    非广告主窗不再被反复整树扫描（真机实测扫描 2396 次/开机 → 稳态
 *    仅剩真广告窗）；探针命中/候选/SDK 指纹/学习指纹/72h 复查即复活。
 *  · P0 预拦截：学习指纹（广告容器签名 + viewId + 窗口 className）命中即
 *    在窗口附加阶段阻断——广告未及呈现，用户零感知、引擎零扫描。
 *  · P0.7 SDK 广告窗识别：广告窗 className 指纹（穿山甲/优量汇/快手/AdMob/
 *    宿主 ad.activity）分段精确匹配——文案烧在图里的零文本广告首次可判，
 *    500ms 无真跳过键即返回键直断（喜马拉雅类根因修复）。
 *  · P0.5 极速链：窗口切换 0ms 即首扫 + 前密后疏重试链，命中即熔断。
 *  · P1 控件代点：「跳过|Skip」文本 + viewId 双通道；假跳过甄别。
 *  · P2 立即直断/热区直断：无倒计时翻转文案首扫即断（~120ms）；自绘零文本
 *    广告全屏热区（≥55% 可点 + 节点≤12 + 无输入框）900ms 直断（守护名单
 *    豁免）；返回键被拦自动补一次（每窗最多 2 次）。
 *  · P2.7 根窗口安全门（v3.6.0）：应用入口窗（冷启动开屏/回前台首窗）上
 *    按返回键 = 退出应用——入口窗禁一切返回键兑底，只走点击梯子，
 *    用尽优雅收手（每窗 6 次）；信息流「摇一摇」文案不误返回。
 *  · P2.5 广告窗抢先直断：链式二跳/陀螺仪诱导类广告在窗口层直接关闭（抢在
 *    跳转触发前）。v3.12.0 起「跳转已发生后的回弹」整体移除（issue#14：
 *    NFC 支付链与主动分享被误弹回）——绝不弹回用户已进入的页面。
 *  · P2.6 弹窗广告 v2：角标（广告/推广/赞助/AD/Sponsored）+ 关闭键双信号；
 *    Dialog 形态 + ≥2 条营销话术弱信号；弹窗窗期独立于开屏窗；覆盖层
 *    内容突发检测带预算制（每应用 10 分钟 2 窗，空手阈值 3→6→12→24 升级）。
 *  · P3 守护模式：支付/银行/政务类应用广告照拦、服务绝不终止。
 */

export type AdType = "splash" | "interstitial" | "shake" | "deeplink" | "popup";
export type AppCategory = "电商" | "游戏" | "银行" | "政务" | "支付" | "视频" | "音频" | "生活";
export type BlockAction =
  | "instant_skip"
  | "sensor_block"
  | "intent_block"
  | "guard_skip"
  | "preempt_block"
  | "chain_block"
  | "gyro_block"
  | "sdk_window_cut"
  | "coord_tap"
  | "popup_close"
  | "back_fallback"
  | "dns_block"
  | "miss";
export type ScenarioKind = "normal" | "shake" | "deeplink" | "guard" | "popup";

export interface PipelineStep {
  label: string;
  detail: string;
  atMs: number;
}

export interface BlockPlan {
  action: BlockAction;
  scenario: ScenarioKind;
  ruleName: string;
  steps: PipelineStep[];
  totalMs: number; // 端到端耗时（含等待广告方显示真跳过）
  engineMs: number; // 引擎出手耗时（去抖 + 扫描/定向查找 + 代点/阻断）
  waitMs: number; // 等待广告方的时间（由广告方制约，非引擎能力）
  confidence: number; // 0~100
  headline: string;
  subline: string;
  preempt: boolean; // P0 预拦截（广告未及呈现）
}

export const GUARD_CATEGORIES: AppCategory[] = ["银行", "政务", "支付"];

export const AD_TYPE_LABEL: Record<AdType, string> = {
  splash: "开屏广告",
  interstitial: "插屏广告",
  shake: "摇一摇广告",
  deeplink: "Deeplink 诱导跳转",
  popup: "应用内弹窗广告",
};

export const ACTION_LABEL: Record<BlockAction, string> = {
  instant_skip: "秒级代点跳过",
  sensor_block: "传感器事件拦截",
  intent_block: "跳转意图拦截",
  guard_skip: "守护模式代点",
  preempt_block: "P0 预拦截",
  chain_block: "链式二跳前置直断",
  gyro_block: "陀螺仪诱导直断",
  coord_tap: "角落控件/坐标级点击",
  sdk_window_cut: "SDK 广告窗指纹直断",
  popup_close: "弹窗广告代点关闭",
  back_fallback: "返回键兜底",
  dns_block: "定向域名阻断",
  miss: "漏拦截（用户手动）",
};

/** 广告对抗特征（喜马拉雅专案 v3 沉淀） */
export interface AdTraits {
  /** 广告图内嵌假「跳过」热区（点击即跳转，非真跳过） */
  fakeSkip?: boolean;
  /** 链式二跳（倒计时归零自动跳落地页 → 再跳外部应用/商店） */
  chainJump?: boolean;
  /** 真跳过控件出现延迟（毫秒；假跳过陷阱的陪衬，真跳过往往很晚才出现） */
  realSkipDelayMs?: number;
  /** 陀螺仪诱导：底部提示「翻转手机，跳转详情页或第三方应用」，无倒计时，手机一动就跳 */
  gyroFlip?: boolean;
  /** 无倒计时（喜马拉雅实测：右上角直接是「跳过广告」，无 Xs 倒计时文案） */
  noCountdown?: boolean;
  /** v3.3.0：SDK 广告窗（穿山甲/优量汇/快手/宿主 ad.activity）——文案烧在图里，
   * 无障碍树零文本，仅窗口 className 指纹可判（真机「还是跳不过」的根因） */
  sdkWindow?: boolean;
}

const jitter = (base: number, spread: number) =>
  Math.max(1, Math.round(base + ((base * 7 + spread) % 11 - 5) * 0.6));

/** ═══ v5.3.0 功耗画像（演练场对照仪表用）═══
 * 数据源：真机日志 #57/#58（v5.2.0 · Xiaomi 2410DPN6CC）实测 +
 * PowerGovernorTest 端到端仿真（#57/#58 窗口流建模）。 */
export const POWER_PROFILE = {
  v520: {
    label: "v5.2.0 真机实测",
    eventsPerBoot: 4604,
    scansPerBoot: 2396,
    microWh: 2798,
    burstFalseOpen: 7,       // 豆包聊天流式内容 5 分钟误开突发窗
    hostMisScans: 246,       // 宿主主窗误扫（豆包 115 节点对话列表/微信排行页/拼多多落地页）
    hitRate: 89.5,           // Top5 应用实测（46 次手动跳过 = 真漏拦）
  },
  v530: {
    label: "v5.3.0 三零治理",
    eventsPerBoot: 4604,     // 前台事件必须过廉价前滤（事件量不变）
    probesPerBoot: 900,      // 探针哨兵替班（成本≈扫描的 1/14）
    scansPerBoot: 300,       // 冷启动会话；稳态仅剩真广告窗
    steadyScansPerBoot: 15,
    microWh: 1334,
    steadyMicroWh: 162,      // 稳态（档案跨重启保留）
    cleanProfileWindows: 21, // 档案 CLEAN 跳过的整窗扫描
    posVerifyDrop: 2,        // 事后验证拦截的误学位置（同秒两处聊天按钮）
    hitRate: 100,
  },
} as const;

/** 探针/扫描成本比（能耗模型：探针 0.05µWh/次 vs 扫描 0.72µWh/次） */
export const PROBE_COST_RATIO = 14.4;

/**
 * 规划一次拦截：返回毫秒级流水线与结论。
 * @param app 目标应用（含广告类型与守护标记）
 * @param opts.preempt 学习指纹命中 → P0 预拦截（窗口附加即断，广告未及呈现）
 * @param opts.traits 广告对抗特征（假跳过 / 链式二跳 / 真跳过延迟）
 */
export function planInstantBlock(
  app: {
    name: string;
    category: AppCategory;
    adType: AdType;
    guard: boolean;
    ruleName: string;
    learnedRuleName?: string | null;
    traits?: AdTraits | null;
  },
  opts: { preempt?: boolean } = {},
): BlockPlan {
  const traits = app.traits ?? {};
  const isShake = app.adType === "shake";
  const isDeeplink = app.adType === "deeplink";
  const preempt = !!opts.preempt;

  /* ── P0 预拦截：窗口附加即断，零渲染等待 ── */
  if (preempt) {
    const steps: PipelineStep[] = [
      { label: "启动意图捕获", detail: "TYPE_WINDOW_STATE_CHANGED → 广告宿主窗口附加（0 渲染帧）", atMs: 0 },
      { label: "学习指纹匹配", detail: `广告容器签名 + viewId + 出现时机指纹命中${app.learnedRuleName ? `（${app.learnedRuleName}）` : ""}`, atMs: jitter(9, 6) },
      {
        label: app.guard ? "守护·温和阻断" : "窗口级阻断",
        detail: app.guard
          ? "广告视图 inflate 前温和拦截，应用进程与后台服务零干预"
          : "广告视图 inflate 前直接拦截，跳过键 / 素材 / 倒计时全部零渲染",
        atMs: jitter(22, 8),
      },
      { label: "校验直进主页", detail: "广告窗口未入栈确认，用户零感知直达主页", atMs: jitter(34, 10) },
    ];
    const totalMs = steps[steps.length - 1].atMs;
    return {
      action: app.guard ? "guard_skip" : "preempt_block",
      scenario: app.guard ? "guard" : "normal",
      ruleName: app.learnedRuleName ?? `${app.name}·指纹预拦截`,
      steps,
      totalMs,
      engineMs: totalMs,
      waitMs: 0,
      confidence: Math.min(99.6, 98.6 + (app.guard ? 0.6 : 0)),
      headline: app.guard
        ? `守护模式 · 预拦截成功，${app.category}应用服务保持运行`
        : "预拦截成功 · 广告未及呈现",
      subline: `${app.name} · ${AD_TYPE_LABEL[app.adType]} · P0 预拦截（0ms 可见时间）`,
      preempt: true,
    };
  }

  /* ── 传感器 / 意图类：事件层即刻生效 ── */
  if (isShake || isDeeplink) {
    const steps: PipelineStep[] = [
      { label: "捕获窗口", detail: "TYPE_WINDOW_STATE_CHANGED → 命中开屏时间窗（启动 3.5s 内）", atMs: 0 },
      { label: "控件树快照", detail: "AccessibilityNodeInfo 递归遍历，提取 text / viewId / 控件面积", atMs: jitter(11, 6) },
    ];
    if (isShake) {
      steps.push({ label: "传感器挂起", detail: "加速度监听冻结，激进阈值（<15m/s²）事件全部丢弃", atMs: jitter(33, 6) });
    } else {
      steps.push({ label: "意图过滤", detail: "广告窗口内 Scheme Intent 拦截，跳转链在系统层断开", atMs: jitter(33, 6) });
    }
    steps.push({ label: "关闭校验", detail: "广告窗口出栈确认，直进应用主页", atMs: jitter(61, 14) });
    const totalMs = steps[steps.length - 1].atMs;
    return {
      action: isShake ? "sensor_block" : "intent_block",
      scenario: isShake ? "shake" : "deeplink",
      ruleName: app.learnedRuleName ?? app.ruleName,
      steps,
      totalMs,
      engineMs: totalMs,
      waitMs: 0,
      confidence: Math.min(99.4, (app.learnedRuleName ? 97 : 93) + (app.guard ? 1.5 : 0) + 1.2),
      headline: isShake ? "摇一摇企图已拦截 · 传感器事件挂起" : "诱导跳转已拦截 · Deeplink 意图断链",
      subline: `${app.name} · ${AD_TYPE_LABEL[app.adType]} · ${ACTION_LABEL[isShake ? "sensor_block" : "intent_block"]}`,
      preempt: false,
    };
  }

  /* ── 应用内弹窗广告：角标 + 关闭键双信号 → 代点关闭 ── */
  if (app.adType === "popup") {
    const steps: PipelineStep[] = [
      { label: "弹窗窗口捕获", detail: "TYPE_WINDOW_STATE_CHANGED（Dialog/PopupWindow）→ 进入弹窗识别窗（10s）", atMs: 0 },
      { label: "多信号同扫", detail: "一次 DFS 收集：「广告」角标（短文本精确匹配）+ 关闭键（×/关闭/不感兴趣/我知道了）+ 跳过键", atMs: jitter(14, 6) },
      {
        label: app.guard ? "守护·代点关闭" : "双信号确认代点",
        detail: app.guard
          ? "「广告」角标 + 关闭键同时在场 → 代点关闭键；温和策略，进程与服务零干预"
          : "「广告」角标 + 关闭键同时在场 → 代点关闭键（防误伤：无角标的正常弹窗一律不动）",
        atMs: jitter(38, 10),
      },
      { label: "冷却保护", detail: "关闭后 2.5s 冷却，避免连环弹窗被误点；窗口出栈确认", atMs: jitter(52, 12) },
    ];
    const totalMs = steps[steps.length - 1].atMs;
    return {
      action: app.guard ? "guard_skip" : "popup_close",
      scenario: app.guard ? "guard" : "popup",
      ruleName: app.learnedRuleName ?? app.ruleName,
      steps,
      totalMs,
      engineMs: totalMs,
      waitMs: 0,
      confidence: Math.min(99.2, (app.learnedRuleName ? 96 : 92) + (app.guard ? 1.5 : 0) + 2),
      headline: app.guard
        ? `守护模式 · 弹窗广告已关闭，${app.category}应用服务保持运行`
        : "弹窗广告已关闭 · 角标 + 关闭键双信号",
      subline: `${app.name} · ${AD_TYPE_LABEL[app.adType]} · ${ACTION_LABEL[app.guard ? "guard_skip" : "popup_close"]}`,
      preempt: false,
    };
  }

  /* ── 开屏 / 插屏：按对抗特征分流（探针前置 · 极速链 0ms 起步） ── */
  const steps: PipelineStep[] = [
    { label: "捕获窗口", detail: "TYPE_WINDOW_STATE_CHANGED → 命中开屏时间窗（启动 12s 内）", atMs: 0 },
    {
      label: app.learnedRuleName ? "viewId 定向查找" : "探针哨兵首扫（0ms）",
      detail: app.learnedRuleName
        ? `findAccessibilityNodeInfosByViewId 直达跳过控件（学习指纹，免全树扫描）`
        : "findAccessibilityNodeInfosByText 系统原生搜索先行（李跳跳同款，成本≈DFS 的 1/14）——文本类广告零树遍历即中即点；未中再走五层规则 DFS + 前密后疏重试链 [0/120/240/400/650/1000ms…]，命中即熔断",
      atMs: jitter(8, 5),
    },
  ];
  let waitMs = 0;

  if (traits.fakeSkip) {
    steps.push({
      label: "假跳过甄别",
      detail: `广告图内嵌「跳过」热区：全屏可点击容器 / 图文混排 / 出现时序${traits.noCountdown ? "无倒计时可校验" : "与倒计时不符"} → 判定为陷阱并忽略`,
      atMs: jitter(18, 6),
    });
  }

  if (traits.chainJump || traits.gyroFlip) {
    // v3.3.0：SDK 广告窗 className 指纹零文本可判，500ms 直断抢在翻转前（根因修复）
    if (traits.sdkWindow) {
      steps.push(
        {
          label: "SDK 广告窗识别",
          detail: "TYPE_WINDOW_STATE_CHANGED 的窗口 className 命中广告 SDK 指纹（穿山甲/优量汇/快手/宿主 ad.activity）——文案烧在图里、无障碍树零文本也能判",
          atMs: jitter(9, 5),
        },
        {
          label: "极速链复核",
          detail: "0/120/240/400ms 重试链内未发现真跳过键（跳过文案同样烧在图里）——不等 0.8s，直接升级直断",
          atMs: 500,
        },
        {
          label: app.guard ? "守护·返回键温和关闭" : "返回键直断",
          detail: app.guard
            ? "GLOBAL_ACTION_BACK 温和关闭广告窗，不点任何广告元素，服务零干预（抢在翻转触发前）"
            : "GLOBAL_ACTION_BACK 直接关闭广告窗，不点任何广告元素（抢在翻转/二跳触发前）",
          atMs: jitter(522, 8),
        },
        {
          label: "指纹沉淀",
          detail: "沉淀窗口 className 指纹，次启 300ms 快断；若跳转已发起则不再弹回（v3.12.0：绝不对抗用户已进入的页面）",
          atMs: jitter(640, 12),
        },
      );
    } else {
      steps.push(
        {
          label: traits.gyroFlip ? "陀螺仪诱导识别" : "链式二跳识别",
          detail: traits.gyroFlip
            ? "底部提示「翻转手机，跳转详情页或第三方应用」→ 无障碍无法冻结应用内传感器监听 → 升级为抢先直断"
            : "广告容器携带自动重定向 Scheme（落地页 → 外部应用二跳特征）→ 升级为直接阻断",
          atMs: jitter(28, 8),
        },
        {
          label: app.guard ? "守护·返回键温和关闭" : "窗口级直接阻断",
          detail: app.guard
            ? "GLOBAL_ACTION_BACK 温和关闭广告窗，不点任何广告元素，服务零干预（抢在翻转触发前）"
            : "GLOBAL_ACTION_BACK 直接关闭广告窗，不点任何广告元素（抢在翻转/二跳触发前）",
          atMs: jitter(42, 10),
        },
        {
          label: "抢先直断完成",
          detail: "广告窗已关闭，二跳链路提前掐断；若跳转已发起则不再弹回（v3.12.0：绝不弹回用户已进入的页面）",
          atMs: jitter(56, 12),
        },
      );
    }
  } else {
    if (traits.fakeSkip) {
      // 假跳过但无链式：甄别后等待真跳过控件挂载（真跳过通常延迟出现）
      waitMs = traits.realSkipDelayMs ?? 5100;
      steps.push({
        label: "真跳过等待",
        detail: `广告方将真跳过延迟至约 ${(waitMs / 1000).toFixed(1)}s 才挂载——引擎静候控件树变化，一出现即出手`,
        atMs: waitMs,
      });
    }
    if (app.guard) {
      steps.push({
        label: "守护名单识别",
        detail: `「${app.category}」类应用 → 温和策略：仅代点跳过，进程与服务零干预`,
        atMs: jitter(26, 6) + waitMs,
      });
    }
    steps.push(
      {
        label: "精准代点",
        detail: `performAction(ACTION_CLICK) 命中真「跳过」控件中心${traits.fakeSkip ? "（假跳过热区已忽略）" : ""}${traits.noCountdown ? "（无倒计时形态，「跳过广告」文案直配）" : ""}`,
        atMs: jitter(34, 8) + waitMs,
      },
      { label: "关闭校验 + 复验待命", detail: "广告窗口出栈确认，直进应用主页；1.2s 后复验待命——未关即换候选点+真实触点重试（v3.6.0 手势点原标签坐标）；返回键兜底仅限应用内窗，入口窗只走点击梯子优雅收手", atMs: jitter(50, 12) + waitMs },
    );
  }

  const totalMs = steps[steps.length - 1].atMs;
  const action: BlockAction = traits.sdkWindow && (traits.chainJump || traits.gyroFlip)
    ? app.guard ? "guard_skip" : "sdk_window_cut"
    : traits.chainJump || traits.gyroFlip
      ? app.guard ? "guard_skip" : traits.gyroFlip ? "gyro_block" : "chain_block"
      : app.guard ? "guard_skip" : "instant_skip";
  const confidence = Math.min(
    99.4,
    (app.learnedRuleName ? 97 : 93) + (traits.fakeSkip || traits.chainJump || traits.gyroFlip ? 2 : 0) + (app.guard ? 1.5 : 0) + 0.8,
  );

  const headline = traits.sdkWindow && (traits.chainJump || traits.gyroFlip)
    ? "SDK 广告窗已直断 · 零文本广告照样拦"
    : traits.gyroFlip
    ? "陀螺仪诱导已直断 · 翻转跳转零机会"
    : traits.chainJump
      ? "链式二跳已前置直断 · 广告窗提前关闭"
      : traits.fakeSkip
        ? "假跳过陷阱已甄别 · 真跳过秒点"
        : app.guard
          ? `守护模式 · 广告已拦截，${app.category}应用服务保持运行`
          : "开屏广告已秒跳 · 直达主页";

  const subline = `${app.name} · ${AD_TYPE_LABEL[app.adType]} · ${ACTION_LABEL[action]}${app.learnedRuleName ? "（学习规则）" : ""}`;

  return {
    action,
    scenario: app.guard ? "guard" : "normal",
    ruleName: app.learnedRuleName ?? app.ruleName,
    steps,
    totalMs,
    engineMs: totalMs - waitMs,
    waitMs,
    confidence: Math.round(confidence * 10) / 10,
    headline,
    subline,
    preempt: false,
  };
}
