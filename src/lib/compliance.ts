/**
 * 合规边界自检与权限透明化（Compliance · v5.4.0）
 * ─────────────────────────────────────────────────────────────────
 * 【规范源 · 跨端统一】本 TS 实现是跨端规范源：Android 端 Java 类
 * （ComplianceGuard.java，package com.xskip.engine.policy）将逐行对齐
 * 本文件的原则表 / 动作边界 / 积极词库 / 审计规则；任何改动必须双端同步。
 *
 * 本库为纯函数库：不 import 数据库 / React，可在服务端与客户端同源使用。
 *
 * 合规定位：本项目是「无障碍代点」工具，不是自动化作弊工具。所有动作
 * 白名单制（默认拒绝）：白名单外的动作一律不允许执行。四大原则与
 * issue#14（NFC 支付链 / 主动分享被误弹回）等历史教训固化在原则表中。
 */

/** 单条合规检查结果（动作审计输出） */
export interface ComplianceCheck {
  rule: string;
  pass: boolean;
  detail: string;
}

/** 合规原则（情报站看板「合规边界」面板展示） */
export interface CompliancePrinciple {
  key: string;
  title: string;
  detail: string;
}

/**
 * 四大合规原则（任何版本迭代不得违背；UI 与 README 均从此处取文案）。
 */
export const COMPLIANCE_PRINCIPLES: CompliancePrinciple[] = [
  {
    key: "only_native_skip",
    title: "仅代点原生跳过控件",
    detail:
      "只对无障碍树中真实存在、真实可点的原生「跳过 / 关闭」类控件执行 performAction(ACTION_CLICK)。不伪造交互流程、不模拟用户手势轨迹（坐标级兜底仅限标准跳过位且每启动一次）、绝不点击广告内容本身。",
  },
  {
    key: "no_content_modify",
    title: "不修改广告内容",
    detail:
      "不 Hook、不注入、不反射、不修改任何 View 与布局——无障碍通道是只读的（树查询 + 白名单代点），对广告内容零改写。广告该展示还是展示，我们只是替用户按下它自己提供的跳过键。",
  },
  {
    key: "no_user_jump_intercept",
    title: "不拦截用户主动跳转",
    detail:
      "用户亲手点开的页面绝不弹回。v3.12.0 已整体移除「跳转回弹」机制（issue#14：NFC 支付链与主动分享被误弹回）——引擎只抢在广告方触发二跳之前关闭广告窗，跳转已发生则视为用户意图，尊重到底。",
  },
  {
    key: "sensitive_zero_scan",
    title: "敏感场景零扫描",
    detail:
      "支付 / 密码 / 验证码 / 短信 / 系统设置 / 输入法 / 锁屏 / 通话 / 转账 / 实名 / 权限对话框等 12 类敏感场景，探针与 DFS 全挂起、引擎完全静默（详见 sensitive-scenes.ts）——隐私内容不进内存树遍历，从机制上杜绝误触与窥探。",
  },
];

/**
 * 动作白名单边界：每个动作是否允许、约束说明。
 * 白名单制：未登记的动作 = 不允许（默认拒绝）。
 */
export const ACTION_BOUNDARY: Record<string, { allowed: boolean; constraint: string }> = {
  instant_skip: {
    allowed: true,
    constraint: "仅原生「跳过 / Skip」控件：文本与 viewId 双通道核验，假跳过甄别通过后才代点。",
  },
  popup_close: {
    allowed: true,
    constraint: "弹窗广告「角标（广告/推广/AD）+ 关闭键」双信号确认后，仅点关闭键（× / 关闭 / 我知道了）。",
  },
  halfsheet_close: {
    allowed: true,
    constraint: "半屏插屏仅点关闭 / 取消类控件，绝不点弹窗内任何功能按钮。",
  },
  decline_tap: {
    allowed: true,
    constraint: "仅消极按钮（以后再说 / 暂不更新 / 取消）——「立即更新 / 立即抢购」等积极 CTA 一律拒绝。",
  },
  preroll_skip: {
    allowed: true,
    constraint: "视频前贴广告倒计时结束后仅点「跳过」控件，倒计时未结束不提前出手。",
  },
  overlay_notice: {
    allowed: true,
    constraint: "仅悬浮提示引导用户自行操作（悬浮广告球场景），零代点零点击。",
  },
  mark_only: {
    allowed: true,
    constraint: "仅信息流广告角标标注（视觉标记），零点击零干预。",
  },
  back_fallback: {
    allowed: true,
    constraint: "仅应用内广告窗；入口窗（冷启动 / 回前台首窗）禁用——防误退出应用（v3.6.0 根窗口安全门）。",
  },
  coord_tap: {
    allowed: true,
    constraint:
      "仅零信号简单树（无滚动 / 无输入框 / 无关闭键）的标准跳过位坐标点击（dispatchGesture），每次启动仅一次；银行 / 政务 / 支付守护名单豁免不使用——微博等第一方图片开屏的终极兜底。",
  },
  // ── 白名单外 = 拒绝。以下登记典型拒绝示范（供审计与 UI 对照展示）──
  positive_cta: {
    allowed: false,
    constraint:
      "任何对积极 CTA（立即更新 / 立即抢购 / 立即下载 / 点击领取）的点击都不在白名单——这是广告方的转化目标，代点即替广告方作弊，一律拒绝。",
  },
  terminate_app: {
    allowed: false,
    constraint:
      "终止 / 清理应用进程不在白名单——银行 / 政务 / 支付守护名单内服务绝不终止（守护模式红线：广告照拦、服务不动）。",
  },
};

/**
 * 积极 CTA 词库：目标控件文本命中任一词即拒绝代点。
 * 这是「仅消极按钮」约束的机器可判边界（decline_tap 与所有点击类动作共用）。
 */
export const POSITIVE_CTA_WORDS: string[] = [
  "立即更新",
  "立即抢购",
  "立即下载",
  "立即领取",
  "立即开通",
  "立即体验",
  "立即查看",
  "马上抢",
  "马上购买",
  "马上领取",
  "一键安装",
  "点击领取",
  "去领取",
  "开通会员",
];

/**
 * 守护名单包名前缀（银行 / 政务 / 支付典型包，取自 corpus.ts 真实清单的
 * 高频子集；Android 端使用完整守护名单，此处为审计演示的轻量内置表）。
 * 守护名单内禁用激进点击动作（coord_tap / back_fallback），仅高精度信号出手。
 */
export const GUARD_PKG_PREFIXES: string[] = [
  "com.eg.android.AlipayGphone", // 支付宝
  "com.unionpay", // 云闪付
  "com.sankuai.pay", // 美团支付
  "com.icbc", // 中国工商银行
  "com.chinamworld.main", // 中国建设银行
  "com.android.bankabc", // 中国农业银行
  "com.chinamworld.bocmbci", // 中国银行
  "com.cmbchina.newapp", // 招商银行
  "cn.gov.tax.its", // 个人所得税（政务）
];

/** 守护名单内允许的温和动作集（广告照拦，但只用高精度信号） */
const GUARD_GENTLE_ACTIONS: string[] = [
  "instant_skip",
  "guard_skip",
  "sdk_window_cut",
  "popup_close",
  "halfsheet_close",
  "decline_tap",
  "preroll_skip",
  "overlay_notice",
  "mark_only",
];

/** 面积安全阀：单控件直点允许的最大面积占比（与 formfit 基准 skipAreaMaxPct 同源） */
const AREA_GATE_PCT = 35;

/**
 * 动作审计：拟执行动作 + 目标控件 → 边界检查清单。
 * 四条规则：
 *   1. action.whitelist —— 动作在白名单内且 allowed（未登记 = 默认拒绝）
 *   2. text.negative_only —— 目标文本不命中积极词库（命中即 fail，无论动作是什么）
 *   3. target.area_gate —— 目标控件面积占比合理（0 < areaPct ≤ 35%，
 *      超限须走全屏热区直断通道而非直点单控件）
 *   4. pkg.guard_list —— 守护名单（银行/政务/支付）内仅温和动作可用
 * 全部 pass 才允许执行；UI 上逐条渲染检查明细。
 */
export function auditAction(
  action: string,
  target: { className: string; text: string; areaPct: number; pkg: string },
): ComplianceCheck[] {
  const checks: ComplianceCheck[] = [];

  // 1. 动作白名单
  const entry = ACTION_BOUNDARY[action];
  checks.push({
    rule: "action.whitelist",
    pass: Boolean(entry && entry.allowed),
    detail: entry
      ? entry.allowed
        ? `「${action}」在动作白名单内：${entry.constraint}`
        : `「${action}」被白名单拒绝：${entry.constraint}`
      : `「${action}」未登记在动作白名单 = 默认拒绝（白名单制，未列出即不允许）`,
  });

  // 2. 积极 CTA 词库
  const hitPositive = POSITIVE_CTA_WORDS.find((w) => target.text.includes(w));
  checks.push({
    rule: "text.negative_only",
    pass: !hitPositive,
    detail: hitPositive
      ? `目标文本命中积极 CTA 词库「${hitPositive}」——拒绝执行（绝不替广告方做转化）`
      : "目标文本未命中积极 CTA 词库（仅消极 / 中性跳过类文案）",
  });

  // 3. 面积安全阀
  const areaOk = target.areaPct > 0 && target.areaPct <= AREA_GATE_PCT;
  checks.push({
    rule: "target.area_gate",
    pass: areaOk,
    detail: areaOk
      ? `目标控件面积占比 ${target.areaPct}% 在安全阀内（≤${AREA_GATE_PCT}%）`
      : `目标控件面积占比 ${target.areaPct}% 超出面积安全阀（≤${AREA_GATE_PCT}%）——单控件直点被拒，须走全屏热区直断通道（含守护名单豁免校验）`,
  });

  // 4. 守护名单
  const guarded = GUARD_PKG_PREFIXES.some((p) => target.pkg.startsWith(p));
  const gentle = GUARD_GENTLE_ACTIONS.includes(action);
  checks.push({
    rule: "pkg.guard_list",
    pass: !guarded || gentle,
    detail: guarded
      ? gentle
        ? `守护名单应用（${target.pkg}）内「${action}」属温和高精度动作——广告照拦、服务不动`
        : `守护名单应用（${target.pkg}）禁用激进动作「${action}」（坐标点击 / 返回兜底等一律不用）`
      : "非守护名单应用，常规白名单边界适用",
  });

  return checks;
}

/** 权限使用审计记录（透明化展示用：无障碍权限到底被用来做了什么） */
export interface PermissionAuditEntry {
  /** 发生时间（毫秒时间戳） */
  at: number;
  /** 使用场景 */
  scene: string;
  /** 引擎行为 */
  behavior: string;
  /** 触碰的数据边界 */
  dataTouched: string;
}

/**
 * 权限使用审计：返回典型审计条目（「设置 → 权限使用说明」与情报站
 * 「权限透明化」面板渲染）。核心声明：所有数据处理仅内存、不落盘、
 * 不上传——落盘的只有脱敏后的学习指纹与截断后的结构化日志。
 * at 取相对当前时间的偏移（最近一次开机会话内），便于演示「最近使用」。
 */
export function buildPermissionAudit(): PermissionAuditEntry[] {
  const now = Date.now();
  const min = 60_000;
  return [
    {
      at: now - 2 * min,
      scene: "窗口监听（无障碍事件 TYPE_WINDOW_STATE_CHANGED / CONTENT_CHANGED）",
      behavior: "监听前台窗口切换与内容变化，驱动扫描流水线与宿主窗档案查询",
      dataTouched: "包名 + 窗口类名——仅内存处理、不落盘、不上传；敏感场景零扫描（事件即静默）",
    },
    {
      at: now - 5 * min,
      scene: "控件查找（探针 findAccessibilityNodeInfosByText / DFS 树遍历）",
      behavior: "定位「跳过 / 关闭」类原生控件；探针前置，文本类广告零树遍历",
      dataTouched: "节点文本 / viewId / 坐标——仅内存判定，不落盘、不上传；12 类敏感场景全挂起",
    },
    {
      at: now - 12 * min,
      scene: "代点执行（performAction ACTION_CLICK）",
      behavior: "对白名单内原生跳过控件执行单次点击（动作边界见 auditAction 四条规则）",
      dataTouched: "目标节点句柄——仅本次点击使用，不留存、不上传；绝不点击积极 CTA",
    },
    {
      at: now - 35 * min,
      scene: "手势兜底（dispatchGesture 坐标点击）",
      behavior: "零信号简单树（无滚动 / 无输入框 / 无关闭键）时点击标准跳过位，每次启动仅一次",
      dataTouched: "屏幕坐标——不采集触控轨迹、不记录输入内容；守护名单（银行/政务/支付）豁免不使用",
    },
    {
      at: now - 2 * 60 * min,
      scene: "学习沉淀（本地指纹档案）",
      behavior: "广告容器签名 + viewId + 窗口 className 指纹本地沉淀（跨重启保留，用于 P0 预拦截）",
      dataTouched: "窗口指纹（不含文本内容、不含用户输入）——仅本地文件，绝不联网同步",
    },
    {
      at: now - 6 * 60 * min,
      scene: "结构化日志（本地日志文件）",
      behavior: "窗口事件与拦截决策记录（schema 1.0，点分事件名）",
      dataTouched: "pkg 保留、text 截断 32 字符——仅本地日志，用户明确操作后才导出，不上传",
    },
  ];
}
