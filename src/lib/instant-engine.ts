/**
 * 秒跳引擎（Instant Skip Engine · v3.1.0 · 五层纵深）
 * ─────────────────────────────────────────────────────────────
 * 设计基准（2026-11 市场调研 + 喜马拉雅专案复盘）：
 *  · P0 预拦截：学习指纹（广告容器签名 + viewId + 出现时机）命中即
 *    在窗口附加阶段阻断——广告未及呈现，用户零感知、引擎零扫描。
 *  · P1 控件代点：「跳过|Skip」文本 + viewId 双通道（李跳跳 / GKD 同源）；
 *    假跳过甄别：广告图内嵌假「跳过」热区（全屏可点击容器 / 图文混排 /
 *    出现时序与倒计时不符）多信号判定后直接忽略，绝不代点。
 *  · P2 直接阻断：链式二跳广告（落地页自动重定向 / 倒计时归零即跳）
 *    升级为窗口级直接拦截——返回键温和关闭，不点任何广告元素；
 *    无控件可见的顽固广告同样由返回键兜底。
 *  · P3 域名阻断：结构性不可见且返回键无效的极少数，定向域名热名单
 *    + 启动窗突发本地过滤，广告请求即断（广告根本加载不出来）。
 *  · S 传感器守护：摇一摇行业标准（≥15m/s²、≥35°、≥3s）以下的激进
 *    触发一律挂起；支付/银行/政务类应用广告照拦、服务绝不终止。
 */

export type AdType = "splash" | "interstitial" | "shake" | "deeplink";
export type AppCategory = "电商" | "游戏" | "银行" | "政务" | "支付" | "视频" | "音频";
export type BlockAction =
  | "instant_skip"
  | "sensor_block"
  | "intent_block"
  | "guard_skip"
  | "preempt_block"
  | "chain_block"
  | "back_fallback"
  | "dns_block"
  | "miss";
export type ScenarioKind = "normal" | "shake" | "deeplink" | "guard";

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
};

export const ACTION_LABEL: Record<BlockAction, string> = {
  instant_skip: "秒级代点跳过",
  sensor_block: "传感器事件拦截",
  intent_block: "跳转意图拦截",
  guard_skip: "守护模式代点",
  preempt_block: "P0 预拦截",
  chain_block: "链式跳转直接阻断",
  back_fallback: "返回键兜底",
  dns_block: "定向域名阻断",
  miss: "漏拦截（用户手动）",
};

/** 广告对抗特征（喜马拉雅专案沉淀） */
export interface AdTraits {
  /** 广告图内嵌假「跳过」热区（点击即跳转，非真跳过） */
  fakeSkip?: boolean;
  /** 链式二跳（倒计时归零自动跳落地页 → 再跳外部应用/商店） */
  chainJump?: boolean;
  /** 真跳过控件出现延迟（毫秒；假跳过陷阱的陪衬，真跳过往往很晚才出现） */
  realSkipDelayMs?: number;
}

const jitter = (base: number, spread: number) =>
  Math.max(1, Math.round(base + ((base * 7 + spread) % 11 - 5) * 0.6));

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

  /* ── 开屏 / 插屏：按对抗特征分流 ── */
  const steps: PipelineStep[] = [
    { label: "捕获窗口", detail: "TYPE_WINDOW_STATE_CHANGED → 命中开屏时间窗（启动 3.5s 内）", atMs: 0 },
    {
      label: app.learnedRuleName ? "viewId 定向查找" : "控件树快照",
      detail: app.learnedRuleName
        ? `findAccessibilityNodeInfosByViewId 直达跳过控件（学习指纹，免全树扫描）`
        : "AccessibilityNodeInfo 递归遍历，提取 text / viewId / 控件面积",
      atMs: jitter(11, 6),
    },
  ];
  let waitMs = 0;

  if (traits.fakeSkip) {
    steps.push({
      label: "假跳过甄别",
      detail: "广告图内嵌「跳过」热区：全屏可点击容器 / 图文混排 / 出现时序与倒计时不符 → 判定为陷阱并忽略",
      atMs: jitter(26, 8),
    });
  }

  if (traits.chainJump) {
    // 链式二跳广告：直接拦截屏蔽（用户指令：含链式跳转的开屏直接拦截），不等待真跳过
    steps.push(
      {
        label: "链式二跳识别",
        detail: "广告容器携带自动重定向 Scheme（落地页 → 外部应用二跳特征）→ 升级为直接阻断",
        atMs: jitter(38, 8),
      },
      {
        label: app.guard ? "守护·返回键温和关闭" : "窗口级直接阻断",
        detail: app.guard
          ? "GLOBAL_ACTION_BACK 温和关闭广告窗，不点任何广告元素，服务零干预"
          : "GLOBAL_ACTION_BACK 直接关闭广告窗，不点任何广告元素、不给二跳任何机会",
        atMs: jitter(52, 10),
      },
      { label: "链路校验", detail: "广告窗口出栈 + Scheme 队列清空确认，直进主页", atMs: jitter(64, 12) },
    );
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
        atMs: jitter(32, 6) + waitMs,
      });
    }
    steps.push(
      {
        label: "精准代点",
        detail: `performAction(ACTION_CLICK) 命中真「跳过」控件中心${traits.fakeSkip ? "（假跳过热区已忽略）" : ""}`,
        atMs: jitter(42, 10) + waitMs,
      },
      { label: "关闭校验", detail: "广告窗口出栈确认，直进应用主页", atMs: jitter(61, 14) + waitMs },
    );
  }

  const totalMs = steps[steps.length - 1].atMs;
  const action: BlockAction = traits.chainJump
    ? app.guard ? "guard_skip" : "chain_block"
    : app.guard ? "guard_skip" : "instant_skip";
  const confidence = Math.min(
    99.4,
    (app.learnedRuleName ? 97 : 93) + (traits.fakeSkip || traits.chainJump ? 2 : 0) + (app.guard ? 1.5 : 0) + 0.8,
  );

  const headline = traits.chainJump
    ? "链式跳转已直接阻断 · 二跳零机会"
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
