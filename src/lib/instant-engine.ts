/**
 * 秒跳引擎（Instant Skip Engine · v2.0.0）
 * ─────────────────────────────────────────────────────────────
 * 设计基准（2026-11 市场调研）：
 *  · 李跳跳 / GKD：无障碍服务读取控件树 → 文本「跳过|Skip」/ viewId
 *    双通道定位跳过控件 → performAction(ACTION_CLICK) 毫秒级代点；
 *    GKD 社区订阅规则以「快照选择器」覆盖 678+ 应用。
 *  · 摇一摇行业标准（2023 / 2025-07《摇一摇广告触发行为安全要求》）：
 *    合法触发需加速度 ≥15m/s²、转角 ≥35°、持续 ≥3s；低于该标准的
 *    传感器触发一律视为激进广告 → 引擎侧挂起传感器事件。
 *  · 面积安全阀：只代点 <35% 屏面积的小控件，全屏热区直接忽略。
 *  · 守护模式：支付/银行/政务类应用照常拦截广告，但绝不终止
 *    应用进程与守护服务（温和代点 + 服务心跳保活）。
 */

export type AdType = "splash" | "interstitial" | "shake" | "deeplink";
export type AppCategory = "电商" | "游戏" | "银行" | "政务" | "支付" | "视频";
export type BlockAction = "instant_skip" | "sensor_block" | "intent_block" | "guard_skip" | "miss";
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
  totalMs: number;
  confidence: number; // 0~100
  headline: string;
  subline: string;
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
  miss: "漏拦截（用户手动）",
};

const jitter = (base: number, spread: number) =>
  Math.max(1, Math.round(base + (Math.random() - 0.5) * spread));

/**
 * 规划一次秒跳拦截：返回毫秒级流水线与结论。
 * @param app 目标应用（含广告类型与守护标记）
 * @param learnedRuleName 学习中心沉淀的专属规则名（命中则置信度更高）
 */
export function planInstantBlock(
  app: {
    name: string;
    category: AppCategory;
    adType: AdType;
    guard: boolean;
    ruleName: string;
    learnedRuleName?: string | null;
  },
): BlockPlan {
  const isShake = app.adType === "shake";
  const isDeeplink = app.adType === "deeplink";
  const scenario: ScenarioKind = isShake ? "shake" : isDeeplink ? "deeplink" : app.guard ? "guard" : "normal";
  const action: BlockAction = isShake
    ? "sensor_block"
    : isDeeplink
      ? "intent_block"
      : app.guard
        ? "guard_skip"
        : "instant_skip";

  const steps: PipelineStep[] = [
    {
      label: "捕获窗口",
      detail: "TYPE_WINDOW_STATE_CHANGED → 命中开屏时间窗（启动 3.5s 内）",
      atMs: 0,
    },
    {
      label: "控件树快照",
      detail: "AccessibilityNodeInfo 递归遍历，提取 text / viewId / 控件面积",
      atMs: jitter(11, 6),
    },
    {
      label: "规则匹配",
      detail: `「跳过|Skip」文本 + viewId 双通道 · 命中${app.learnedRuleName ? "学习专属规则" : app.ruleName}`,
      atMs: jitter(24, 8),
    },
  ];

  if (app.guard) {
    steps.push({
      label: "守护名单识别",
      detail: `「${app.category}」类应用 → 温和策略：仅代点跳过，进程与服务零干预`,
      atMs: jitter(32, 6),
    });
  }
  if (isShake) {
    steps.push({
      label: "传感器挂起",
      detail: "加速度监听冻结，激进阈值（<15m/s²）事件全部丢弃",
      atMs: jitter(33, 6),
    });
  }
  if (isDeeplink) {
    steps.push({
      label: "意图过滤",
      detail: "广告窗口内 Scheme Intent 拦截，跳转链在系统层断开",
      atMs: jitter(33, 6),
    });
  }

  steps.push(
    {
      label: "模拟点击",
      detail: "performAction(ACTION_CLICK) 命中「跳过」控件中心",
      atMs: jitter(42, 10),
    },
    {
      label: "关闭校验",
      detail: "广告窗口出栈确认，直进应用主页",
      atMs: jitter(61, 14),
    },
  );

  const totalMs = steps[steps.length - 1].atMs;
  const confidence = Math.min(
    99.4,
    (app.learnedRuleName ? 97 : 93) + (app.guard ? 1.5 : 0) + Math.random() * 2,
  );

  const headline = app.guard
    ? `守护模式 · 广告已拦截，${app.category}应用服务保持运行`
    : isShake
      ? "摇一摇企图已拦截 · 传感器事件挂起"
      : isDeeplink
        ? "诱导跳转已拦截 · Deeplink 意图断链"
        : "开屏广告已秒跳 · 直达主页";

  const subline = `${app.name} · ${AD_TYPE_LABEL[app.adType]} · ${ACTION_LABEL[action]}${app.learnedRuleName ? "（学习规则）" : ""}`;

  return {
    action,
    scenario,
    ruleName: app.learnedRuleName ?? app.ruleName,
    steps,
    totalMs,
    confidence: Math.round(confidence * 10) / 10,
    headline,
    subline,
  };
}
