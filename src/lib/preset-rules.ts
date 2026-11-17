/**
 * 预置规则库（v2.0.0）
 * 依据 2026-11 市场调研整理（GKD 等同类开源无障碍代点方案、摇一摇行业
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
  "sdk-window": "SDK 窗口指纹",
  "coord": "角落控件/坐标点击",
  "popup-v2": "弹窗 v2 双信号",
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
      pattern: "链式二跳前置直断：广告容器携带自动重定向 Scheme（落地页→外部应用二跳）→ 返回键温和关闭广告窗，不点任何广告元素；v3.12.0 起跳转已发起后不再弹回（绝不弹回用户已进入的页面）",
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
      pattern: "喜马拉雅专案 v2：无倒计时 + 右上角「跳过广告」+ 图内假「跳过」热区 + 底部「翻转手机，跳转详情页或第三方应用」陀螺仪诱导 + 链式二跳 → 极速链 0ms 首扫直配「跳过广告」；0.8s 无真跳过键返回键直断；v3.12.0 起二跳已发起后不再回弹；次启预拦截",
      action: "gyro_block",
      confidence: 0.98,
    },
  ];
}

/** v3.2.0 六层纵深新增规则（极速链 / 陀螺仪直断 / 链式回弹 / 弹窗广告屏蔽） */
export function presetRulesV32(): PresetRuleSeed[] {
  return [
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "timing",
      pattern: "极速链：窗口切换 0ms 即首扫（原 350ms）+ 前密后疏重试链 [0/120/240/400/650/1000ms…] 覆盖 12s 窗，命中即熔断后续扫描",
      action: "instant_skip",
      confidence: 0.97,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "sensor",
      pattern: "陀螺仪诱导直断：识别「翻转手机/摇一摇」文案后 0.8s 内仍无真跳过键 → GLOBAL_ACTION_BACK 温和直断广告窗（只关窗不碰服务），抢先于翻转触发",
      action: "gyro_block",
      confidence: 0.96,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "any",
      matchType: "chain",
      pattern: "链式二跳前置直断（v3.12.0 修订）：只在广告窗内抢先关闭（抢在二跳触发前）；跳转已发起后不再弹回（原「链式回弹」机制已整体移除，issue#14：NFC 支付链/主动分享被误弹回）",
      action: "chain_block",
      confidence: 0.95,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "popup",
      matchType: "fake-skip",
      pattern: "弹窗广告屏蔽：「广告」角标（短文本精确匹配）+ 关闭键（×/x/关闭/不感兴趣/我知道了）双信号同时在场 → 代点关闭；窗口 10s 内冷却 2.5s，无角标正常弹窗不动",
      action: "popup_close",
      confidence: 0.94,
    },
  ];
}

/** v3.3.0 七层纵深新增规则（SDK 广告窗指纹 / 热区直断 / 弹窗 v2 / 回弹扩充） */
export function presetRulesV33(): PresetRuleSeed[] {
  return [
    {
      appName: "*",
      appCategory: "通用",
      adType: "any",
      matchType: "sdk-window",
      pattern: "SDK 广告窗指纹：窗口 className 分段精确匹配广告 SDK（穿山甲 openadsdk / 优量汇 qq.e.ads / 快手 kwad / AdMob / 宿主 .ad.activity / AdActivity…）→ 500ms 无真跳过键即返回键直断，零文本依赖；直断后沉淀窗口指纹，次启 300ms 快断",
      action: "sdk_window_cut",
      confidence: 0.98,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "area",
      pattern: "全屏热区直断：启动窗内「≥55% 屏幕面积可点热区 + 节点数≤12 + 无输入框」三条件判定自绘图片广告 → 900ms 无真跳过键返回键直断；广告拦截返回键自动补一次（每窗最多 2 次）；银行/政务/支付守护名单豁免（只用高精度信号）",
      action: "gyro_block",
      confidence: 0.95,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "popup",
      matchType: "popup-v2",
      pattern: "弹窗广告 v2：弹窗窗期独立于开屏 12s 窗（覆盖层内容突发检测：1.5s 内 ≥3 次内容变化即开窗）；角标扩充（广告/推广/赞助/AD/Sponsored/Promoted）+ 关闭键代点；Dialog/PopupWindow 形态 + ≥2 条营销话术弱信号组合；关闭键不可点时 Dialog 返回键兜底",
      action: "popup_close",
      confidence: 0.96,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "any",
      matchType: "chain",
      pattern: "广告窗活跃期前置直断（v3.12.0 修订，原「回弹名单扩充」）：引擎识别到广告信号时只做广告窗内抢先关闭，任意跳转目标都不再弹回；同包 WebView 落地页不再弹回（用户点开的商品/文章详情页绝不动）",
      action: "chain_block",
      confidence: 0.97,
    },
    {
      appName: "喜马拉雅",
      appCategory: "音频",
      adType: "splash",
      matchType: "sdk-window",
      pattern: "喜马拉雅专案 v3：SDK 广告窗零文本（文案烧在图里）+ 无倒计时 + 翻转手机 900ms 即跳 + 浏览器二跳 → className 指纹 500ms 直断（抢在翻转前）；30s 后首页覆盖层弹窗内容突发检测关闭；次启预拦截 0ms 可见（v3.12.0：二跳已发起后不再回弹）",
      action: "sdk_window_cut",
      confidence: 0.99,
    },
  ];
}

/** v3.4.0 新增规则（第一方图片开屏：角落控件点击 + 坐标级点击兜底） */
export function presetRulesV34(): PresetRuleSeed[] {
  return [
    {
      appName: "*",
      appCategory: "通用",
      adType: "any",
      matchType: "coord",
      pattern: "角落图片跳过键识别（L4）：顶部 14% 高、贴右缘(≥72%宽)或左缘(≤28%宽)、边长 24px~35%宽、无任何文本的小可点控件 → dispatchGesture 手势点击其屏幕坐标中心（performAction 失效自动降级手势，真实触点应用拦不住）；防误点：仅开屏窗前 3.5s + 简单树（无滚动/无输入框/节点≤40）",
      action: "coord_tap",
      confidence: 0.97,
    },
    {
      appName: "*",
      appCategory: "通用",
      adType: "splash",
      matchType: "coord",
      pattern: "坐标级点击兜底（L6）：开屏窗内零信号简单树（无滚动/无输入框/无关闭键，大热区或节点≤40）→ dispatchGesture 点击标准跳过位（88%宽/7%高），900ms~4s 窗口期，每次启动仅一次；守护名单（银行/政务/支付）豁免——微博等第一方图片开屏的终极兜底",
      action: "coord_tap",
      confidence: 0.93,
    },
    {
      appName: "喜马拉雅",
      appCategory: "音频",
      adType: "splash",
      matchType: "coord",
      pattern: "喜马拉雅专案 v4：第一方图片开屏（WelComeActivity 自建广告渲染，零文本零类名信号）+ 翻转手机 900ms 即跳 → 角落图片跳过键 650ms 手势点击抢在翻转前（R16 验证 659ms < 900ms）；30s 覆盖层弹窗内容突发检测关闭；次启预拦截 0ms 可见（v3.12.0：二跳已发起后不再回弹）",
      action: "coord_tap",
      confidence: 0.99,
    },
  ];
}

/** 幂等播种：规则库为空时写入全量；已有旧版规则则逐版补种 */
export async function ensureRulesSeed(): Promise<void> {
  const count = await db.rule.count();
  if (count === 0) {
    await db.rule.createMany({ data: [...presetRules(), ...presetRulesV31(), ...presetRulesV32(), ...presetRulesV33(), ...presetRulesV34()] });
    return;
  }
  // 逐版升级补种：按 appName + matchType + action 判重
  for (const r of [...presetRulesV31(), ...presetRulesV32(), ...presetRulesV33(), ...presetRulesV34()]) {
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
