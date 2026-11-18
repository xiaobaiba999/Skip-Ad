/**
 * 分级动态功耗策略（PowerGovernor · v5.4.0）
 * ─────────────────────────────────────────────────────────────────
 * 【规范源 · 跨端统一】本 TS 实现是跨端规范源：Android 端 Java 类
 * （PowerGovernor.java，package com.xskip.engine.policy）将逐行对齐
 * 本文件的分级表 / 策略矩阵 / 能耗模型；任何阈值改动必须双端同步修改，
 * 并以本文件数值为基准断言（Java 侧 PowerGovernorTest 已有
 * v5.3.0 端到端仿真基线，见 instant-engine.ts POWER_PROFILE）。
 *
 * 本库为纯函数库：不 import 数据库 / React，可在服务端与客户端同源使用。
 *
 * 命中率红线（永不降级，任何档位都不得违反）：
 *   1. probeFirst 探针哨兵前置 —— 所有 16 档恒为 true（探针成本仅扫描的
 *      1/14，是命中率的下限保障，砍探针省不回电却直接掉命中率）；
 *   2. SDK 广告窗指纹（穿山甲/优量汇/快手/宿主 ad.activity 窗口 className
 *      分段精确匹配）、Dialog 形态指纹、学习指纹（P0 预拦截）三个命中通道
 *      永不降级——窗口层指纹判定零树遍历零重试链，成本近探针，砍它毫无意义。
 *      该红线同时写进每档返回 label 的「指纹常开」后缀，UI 看板可见。
 *
 * 能耗模型（estimateEnergyPerBoot，对齐 instant-engine.ts POWER_PROFILE.v530
 * 真机实测口径：Xiaomi 2410DPN6CC 真机日志 #57/#58）：
 *   · 每开机前台事件 4604（事件量由使用习惯决定，治理前后不变）
 *   · 扫描 0.72µWh/次、探针 0.05µWh/次（PROBE_COST_RATIO = 14.4）
 *   · 事件基础处理成本（廉价前滤 + 档案查询 + 调度）由实测双版本反推：
 *       v5.3.0：(1334 − 300×0.72 − 900×0.05) / 4604 ≈ 0.23297 µWh/事件
 *       v5.2.0：(2798 − 2396×0.72 − 0×0.05) / 4604 ≈ 0.23294 µWh/事件
 *     两版本独立反推结果一致（差 <0.02%），模型自洽。
 */

/** 设备分级（内存 GB + 核数 + SoC 性能分 0~100 → 四档） */
export type DeviceTier = "flagship" | "high" | "mid" | "low";

/** 电量状态（level 0~100 + charging + saver → 四态） */
export type BatteryState = "charging" | "normal" | "low" | "critical";

/** 一档完整功耗策略 */
export interface PowerPolicy {
  tier: DeviceTier;
  battery: BatteryState;
  /** 探针哨兵前置（所有档位保持 true——命中率红线，见文件头注释） */
  probeFirst: boolean;
  /** 常规扫描间隔（毫秒；窗口切换首扫恒 0ms 不受此值影响） */
  scanIntervalMs: number;
  /** 前密后疏重试链（毫秒偏移；空链 = 该窗仅首扫） */
  retryChain: number[];
  /** DFS 最大深度（无障碍树层数上限；浅扫少遍历深层节点） */
  maxDepth: number;
  /** 弹窗识别窗（覆盖层内容突发检测；低电与入门档关闭） */
  popupWatch: boolean;
  /** 学习沉淀（零候选指纹学习与本地档案更新；极低电关闭） */
  learningEnabled: boolean;
  /** 保活心跳间隔（秒，0 = 关闭；v5.2.0 两态通知体系的拉活节奏） */
  keepAlivePing: number;
  /** 策略可读名（如「旗舰·充电·全量出手·指纹常开」） */
  label: string;
}

const TIER_LABEL: Record<DeviceTier, string> = {
  flagship: "旗舰",
  high: "高端",
  mid: "中端",
  low: "入门",
};

const BATTERY_LABEL: Record<BatteryState, string> = {
  charging: "充电",
  normal: "正常",
  low: "低电",
  critical: "极低电",
};

/** 重试链常量（与 instant-engine.ts P0.5 极速链同源） */
const CHAIN_FULL: number[] = [0, 120, 240, 400, 650, 1000]; // 全量：覆盖 12s 窗
const CHAIN_MID: number[] = [0, 150, 300, 500]; // 中端砍尾
const CHAIN_ENTRY: number[] = [0, 200, 450]; // 入门

/**
 * 设备分级：内存 GB + 核数 + SoC 性能分（0~100）→ 四档。
 * 规则表（first-match，从高往低判，不满足即降档）：
 *   flagship：socScore ≥ 85 且 ramGb ≥ 12 且 cores ≥ 8   （骁龙 8 系 / 天玑 9 系）
 *   high：    socScore ≥ 70 且 ramGb ≥ 8  且 cores ≥ 8   （骁龙 7 系 / 天玑 8 系）
 *   mid：     socScore ≥ 50 且 ramGb ≥ 6  且 cores ≥ 6   （骁龙 6 系 / 天玑 7 系）
 *   low：     其余（Redmi 入门机 / 老年机 / 3~4GB 机型）
 */
export function classifyDevice(ramGb: number, cores: number, socScore: number): DeviceTier {
  if (socScore >= 85 && ramGb >= 12 && cores >= 8) return "flagship";
  if (socScore >= 70 && ramGb >= 8 && cores >= 8) return "high";
  if (socScore >= 50 && ramGb >= 6 && cores >= 6) return "mid";
  return "low";
}

/**
 * 电量状态：level 0~100 + charging + saver（省电开关）→ 四态。
 * 规则表（优先级从上到下）：
 *   1. charging = true            → charging（插电即充电态，功耗不敏感）
 *   2. level ≤ 15                 → critical（极低电：只留探针）
 *   3. level ≤ 35 或 saver = true → low（低电 / 用户主动省电，至少按低电治理）
 *   4. 其余                        → normal
 */
export function resolveBattery(level: number, charging: boolean, saver: boolean): BatteryState {
  const lv = Math.max(0, Math.min(100, level)); // 防御式 clamp
  if (charging) return "charging";
  if (lv <= 15) return "critical";
  if (lv <= 35 || saver) return "low";
  return "normal";
}

/**
 * 核心策略表：设备四档 × 电量四态 → 完整策略。
 * 参数矩阵（跨端对齐基准，改动需双端同步）：
 *
 *   scanIntervalMs（常规扫描间隔）:
 *              charging  normal  low   critical
 *   flagship      500     600   1200    2000
 *   high          500     600   1200    2000
 *   mid           600     800   1500    2000
 *   low           800    1000   1800    2400
 *
 *   retryChain（重试链）:
 *   flagship/high  charging+normal [0,120,240,400,650,1000]  low [0,240,500]
 *   mid            charging+normal [0,150,300,500]           low [0,300,600]
 *   low            charging+normal [0,200,450]               low [0,450]
 *   critical（全档）[0,800]——探针/指纹主导，仅留首扫 + 一次远距复查
 *
 *   maxDepth（DFS 深度）:
 *              charging normal low critical
 *   flagship     50     50    42    24
 *   high         50     50    42    24
 *   mid          42     42    36    24
 *   low          36     36    30    20
 *
 *   popupWatch：仅 charging/normal 且非入门档开启（低电与入门关）
 *   learningEnabled：仅 critical 关闭（极低电停止学习复扫）
 *   keepAlivePing（秒）:
 *              charging normal  low critical
 *   flagship     30      45    120     0
 *   high         30      45    120     0
 *   mid          45      60    150     0
 *   low          60      90    180     0
 *
 * 极低电（critical）= 静默守护态：探针 + 三指纹通道照常（红线），
 * DFS 深度与重试链大幅收缩、弹窗识别窗关、学习关、保活心跳关。
 */
export function computePolicy(tier: DeviceTier, battery: BatteryState): PowerPolicy {
  // ── 常规扫描间隔 ──
  const scanIntervalMs: number =
    tier === "flagship" || tier === "high"
      ? battery === "charging"
        ? 500
        : battery === "normal"
          ? 600
          : battery === "low"
            ? 1200
            : 2000
      : tier === "mid"
        ? battery === "charging"
          ? 600
          : battery === "normal"
            ? 800
            : battery === "low"
              ? 1500
              : 2000
        : battery === "charging"
          ? 800
          : battery === "normal"
            ? 1000
            : battery === "low"
              ? 1800
              : 2400;

  // ── 前密后疏重试链 ──
  let retryChain: number[];
  if (battery === "critical") {
    retryChain = [0, 800];
  } else if (battery === "low") {
    retryChain = tier === "mid" ? [0, 300, 600] : tier === "low" ? [0, 450] : [0, 240, 500];
  } else {
    retryChain = tier === "mid" ? CHAIN_MID : tier === "low" ? CHAIN_ENTRY : CHAIN_FULL;
  }

  // ── DFS 深度 ──
  const depthBase = tier === "flagship" || tier === "high" ? 50 : tier === "mid" ? 42 : 36;
  const maxDepth: number =
    battery === "critical"
      ? tier === "low"
        ? 20
        : 24
      : battery === "low"
        ? depthBase - 8
        : depthBase;

  // ── 弹窗识别窗 / 学习沉淀 / 保活心跳 ──
  const popupWatch = tier !== "low" && (battery === "charging" || battery === "normal");
  const learningEnabled = battery !== "critical";
  const keepAlivePing: number =
    battery === "critical"
      ? 0
      : battery === "charging"
        ? tier === "mid"
          ? 45
          : tier === "low"
            ? 60
            : 30
        : battery === "normal"
          ? tier === "mid"
            ? 60
            : tier === "low"
              ? 90
              : 45
          : tier === "mid"
            ? 150
            : tier === "low"
              ? 180
              : 120;

  // ── 策略可读名（label 末尾的「指纹常开」= 命中率红线声明，UI 可见）──
  const strategyName: string =
    battery === "critical"
      ? "探针静默"
      : battery === "low"
        ? "省电降级"
        : battery === "charging"
          ? tier === "flagship" || tier === "high"
            ? "全量出手"
            : tier === "mid"
              ? "均衡出手"
              : "轻量出手"
          : tier === "flagship" || tier === "high"
            ? "均衡出手"
            : tier === "mid"
              ? "标准出手"
              : "轻量出手";

  return {
    tier,
    battery,
    probeFirst: true, // 命中率红线：所有档位保持（见文件头注释）
    scanIntervalMs,
    retryChain,
    maxDepth,
    popupWatch,
    learningEnabled,
    keepAlivePing,
    label: `${TIER_LABEL[tier]}·${BATTERY_LABEL[battery]}·${strategyName}·指纹常开`,
  };
}

/** 对照展示用：全部 16 档组合（4 设备档 × 4 电量态）的策略矩阵，供情报站看板渲染 */
export function policyMatrix(): PowerPolicy[] {
  const tiers: DeviceTier[] = ["flagship", "high", "mid", "low"];
  const batteries: BatteryState[] = ["charging", "normal", "low", "critical"];
  return tiers.flatMap((t) => batteries.map((b) => computePolicy(t, b)));
}

// ──────────────── 能耗估算（对齐 POWER_PROFILE.v530 实测口径） ────────────────

/** 每开机前台事件数（真机日志 #57/#58 实测，治理前后不变） */
export const EVENTS_PER_BOOT = 4604;
/** 探针成本（µWh/次）与扫描成本（µWh/次），成本比 14.4（对齐 PROBE_COST_RATIO） */
export const PROBE_UWH = 0.05;
export const SCAN_UWH = 0.72;
/** 事件基础处理成本（µWh/事件）：由 v5.2.0 / v5.3.0 两组实测独立反推，结果一致 */
export const EVENT_BASE_UWH = (1334 - 300 * SCAN_UWH - 900 * PROBE_UWH) / 4604; // ≈ 0.23297
/** 探针哨兵会话数/开机：探针全档位保持（命中率红线），对齐 v530 probesPerBoot */
const PROBES_PER_BOOT = 900;
/** 全量基准扫描数/开机（v530：冷启动 285 + 稳态 15） */
const BASE_SCANS = 300;
/** 全量基准参数（缩放因子分母） */
const BASE_INTERVAL = 500;
const BASE_DEPTH = 50;
const BASE_CHAIN_LEN = 6;

/**
 * 能耗估算（µWh/日，按单次开机会话建模）。
 * 模型：scansPerBoot 由策略参数相对全量基准的缩放因子推导——
 *   · intervalF = 500 / scanIntervalMs（间隔拉长 → 常规巡视扫描减少）
 *   · depthF    = maxDepth / 50（浅扫 → 单次遍历节点减少）
 *   · chainF    = retryChain.length / 6（重试链短 → 单窗扫描次数减少）
 *   · popupF    = popupWatch ? 1 : 0.85（弹窗识别窗关 → 该巡视通道停）
 *   · learnF    = learningEnabled ? 1 : 0.9（学习关 → 零候选学习复扫停）
 *   微Wh = 4604 × EVENT_BASE_UWH + scans × 0.72 + probes × 0.05。
 *
 * 自洽校验：旗舰/高端充电态（全量）→ 300 扫描 + 900 探针 ≈ 1334µWh，
 * 与 instant-engine.ts POWER_PROFILE.v530.microWh 精确对齐；
 * 其余档位单调下降，极低电下限受事件基础成本（≈1073µWh）托底——
 * 事件必须过廉价前滤（v5.3.0 三零治理的既有结论），这部分不随档位变化。
 */
export function estimateEnergyPerBoot(policy: PowerPolicy): {
  scansPerBoot: number;
  probesPerBoot: number;
  microWh: number;
} {
  const intervalF = BASE_INTERVAL / policy.scanIntervalMs;
  const depthF = policy.maxDepth / BASE_DEPTH;
  const chainF = policy.retryChain.length / BASE_CHAIN_LEN;
  const popupF = policy.popupWatch ? 1 : 0.85;
  const learnF = policy.learningEnabled ? 1 : 0.9;
  const scansPerBoot = Math.max(
    1,
    Math.round(BASE_SCANS * intervalF * depthF * chainF * popupF * learnF),
  );
  // 探针全档位保持（红线）；probeFirst=false 属违规策略，仅作防御展示计 0
  const probesPerBoot = policy.probeFirst ? PROBES_PER_BOOT : 0;
  const microWh = Math.round(
    EVENTS_PER_BOOT * EVENT_BASE_UWH + scansPerBoot * SCAN_UWH + probesPerBoot * PROBE_UWH,
  );
  return { scansPerBoot, probesPerBoot, microWh };
}
