/**
 * 预置规则库（v2.0.0）
 * 依据 2026-11 市场调研整理（李跳跳/GKD 无障碍代点方案、摇一摇行业
 * 标准、面积安全阀、Deeplink 黑名单、支付银行政务守护名单）。
 * 首次启动播种；学习规则由学习中心动态生成（source = learned）。
 */
import { db } from "./db";
import { SCENARIO_APPS } from "./scenario-apps";

export interface PresetRuleSeed {
  appName: string;
  appCategory: string;
  adType: string;
  matchType: string;
  pattern: string;
  action: string;
  confidence: number;
}

const MATCH_LABEL: Record<string, string> = {
  text: "控件文本",
  viewId: "控件 ID",
  timing: "时间窗",
  area: "面积安全阀",
  sensor: "传感器",
  intent: "Scheme 意图",
  "guard-list": "守护名单",
  learned: "习惯学习",
  preempt: "P0 预拦截",
  "fake-skip": "假跳过甄别",
  chain: "链式二跳",
  backkey: "返回键兜底",
  dns: "定向域名",
};

export function matchTypeLabel(t: string): string {
  return MATCH_LABEL[t] ?? t;
}

/** 全局规则 + 演练应用专属规则 */
export function presetRules(): PresetRuleSeed[] {
  const global: PresetRuleSeed[] = [
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "text",
      pattern: "「跳过 | 跳过广告 | Skip」文本匹配（GKD 快照选择器同源）",
      action: "instant_skip",
      confidence: 0.95,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "timing",
      pattern: "启动 3.5s 时间窗内 + TYPE_WINDOW_CONTENT_CHANGED 持续监听（延迟出现的跳过键一出现即点）",
      action: "instant_skip",
      confidence: 0.92,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "area",
      pattern: "面积安全阀：只代点 <35% 屏面积控件，全屏热区/假关闭 × 直接忽略",
      action: "instant_skip",
      confidence: 0.93,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "shake",
      matchType: "sensor",
      pattern: "加速度阈值 <15m/s²、转角 <35°、持续 <3s 的传感器事件视为激进广告 → 挂起监听",
      action: "sensor_block",
      confidence: 0.96,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "deeplink",
      matchType: "intent",
      pattern: "广告窗口内发起的 Scheme / Universal Link 一律拦截，跳转链系统层断开",
      action: "intent_block",
      confidence: 0.94,
    },
    {
      appName: "守护名单",
      appCategory: "银行/政务/支付",
      adType: "any",
      matchType: "guard-list",
      pattern: "支付、银行、政务类应用：广告照常拦截，但绝不终止进程/服务，仅温和代点",
      action: "guard_skip",
      confidence: 0.99,
    },
  ];

  const perApp: PresetRuleSeed[] = SCENARIO_APPS.map((a) => ({
    appName: a.name,
    appCategory: a.category,
    adType: a.adType,
    matchType: a.adType === "shake" ? "sensor" : a.adType === "deeplink" ? "intent" : "viewId",
    pattern:
      a.adType === "shake"
        ? `${a.name}：加速度计监听冻结（激进阈值确认）`
        : a.adType === "deeplink"
          ? `${a.name}：广告层 Intent 过滤 + 商城 Scheme 黑名单`
          : `${a.name}：viewId 精确定位跳过控件（无障碍快照）`,
    action: a.guard ? "guard_skip" : a.adType === "shake" ? "sensor_block" : a.adType === "deeplink" ? "intent_block" : "instant_skip",
    confidence: a.guard ? 0.98 : 0.95,
  }));

  return [...global, ...perApp];
}

/** v3.1.0 五层纵深新增规则（幂等升级：老库补种，新库全量） */
export function presetRulesV31(): PresetRuleSeed[] {
  return [
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "preempt",
      pattern: "P0 预拦截：学习指纹（广告容器签名 + viewId + 出现时机）命中 → 窗口附加阶段直接阻断，广告未及呈现、引擎零扫描",
      action: "preempt_block",
      confidence: 0.98,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "fake-skip",
      pattern: "假跳过甄别：全屏可点击容器 / 图文混排「跳过」热区 / 出现时序与倒计时不符 → 多信号判定为陷阱并忽略，静候真跳过",
      action: "instant_skip",
      confidence: 0.96,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "chain",
      pattern: "链式二跳直接阻断：广告容器携带自动重定向 Scheme（落地页→外部应用二跳）→ 返回键温和关闭广告窗，不点任何广告元素",
      action: "chain_block",
      confidence: 0.96,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "any",
      matchType: "backkey",
      pattern: "返回键兜底：无控件可见的顽固广告（WebView 不暴露无障碍树 / 纯自绘）→ GLOBAL_ACTION_BACK 温和关闭广告窗口",
      action: "back_fallback",
      confidence: 0.88,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "any",
      matchType: "dns",
      pattern: "定向域名阻断（兜底中的兜底）：返回键也无效的结构性不可见广告 → 学习名单域名热阻断 + 启动窗突发本地过滤，广告请求即断",
      action: "dns_block",
      confidence: 0.9,
    },
    {
      appName: "喜马拉雅",
      appCategory: "音频",
      adType: "splash",
      matchType: "chain",
      pattern: "喜马拉雅专案：广告图内嵌假「跳过」热区 + 真跳过延迟 5.1s + 倒计时归零链式二跳 → 假跳过甄别 + 窗口级直接阻断 + 次启预拦截",
      action: "chain_block",
      confidence: 0.97,
    },
  ];
}

/** 幂等播种：规则库为空时写入全量；已有旧版规则则补种 v3.1.0 新规则 */
export async function ensureRulesSeed(): Promise<void> {
  const count = await db.rule.count();
  if (count === 0) {
    await db.rule.createMany({ data: [...presetRules(), ...presetRulesV31()] });
    return;
  }
  // v3.1.0 升级补种：按 appName + matchType + action 判重
  for (const r of presetRulesV31()) {
    const exists = await db.rule.findFirst({
      where: { appName: r.appName, matchType: r.matchType, action: r.action },
    });
    if (!exists) {
      await db.rule.create({
        data: { ...r, source: "preset" },
      });
    }
  }
}
