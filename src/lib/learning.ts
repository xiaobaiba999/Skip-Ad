/**
 * 学习中心（SERVER · v3.1.0 智能学习）
 * ─────────────────────────────────────────────────────────────
 * · recordBlock：拦截成功 → 拦截流水 + 规则命中累计；首次拦截即
 *   沉淀「广告指纹」学习规则（容器签名 + viewId + 出现时机），
 *   下次启动直接 P0 预拦截（广告未及呈现）
 * · recordMiss：反馈通道兼容接口（对照/反馈场景手动跳过留痕）
 * · getProfile：使用习惯画像（应用分布 / 时段分布 / 场景分布 / 耗时）
 * · buildSnapshot：夜间同步 GitHub 的数据快照（学习规则 + 画像 + 统计）
 */
import { db } from "./db";
import { SCENARIO_APPS } from "./scenario-apps";
import type { AdType, AppCategory, BlockAction, ScenarioKind } from "./instant-engine";

export interface BlockEventInput {
  appKey: string;
  kind: "block" | "miss";
  action: BlockAction;
  latencyMs: number;
  confidence: number;
  scenario: ScenarioKind;
  adType: AdType;
  note?: string;
}

const APP_BY_KEY = new Map(SCENARIO_APPS.map((a) => [a.key, a]));

function shanghaiHour(d: Date): number {
  // UTC+8 小时（用于时段画像，免依赖时区库）
  return (d.getUTCHours() + 8) % 24;
}

function shanghaiDateKey(d: Date): string {
  const t = new Date(d.getTime() + 8 * 3600 * 1000);
  return t.toISOString().slice(0, 10);
}

export function todayKey(d: Date = new Date()): string {
  return shanghaiDateKey(d);
}

/** 拦截成功：写流水 + 命中规则累计；首次拦截即沉淀预拦截指纹 */
export async function recordBlock(input: BlockEventInput) {
  const app = APP_BY_KEY.get(input.appKey);
  if (!app) throw new Error("unknown app");

  const ev = await db.blockEvent.create({
    data: {
      appName: app.name,
      appCategory: app.category,
      adType: input.adType,
      action: input.action,
      latencyMs: Math.max(0, Math.min(60000, Math.round(input.latencyMs))),
      confidence: Math.max(0, Math.min(1, input.confidence)),
      success: true,
      scenario: input.scenario,
      note: input.note?.slice(0, 120) ?? null,
    },
  });

  // 命中应用专属规则（优先学习规则，其次预置）
  const rule = await db.rule.findFirst({
    where: { appName: app.name, source: "learned" },
  });
  let learnedCreated = false;
  if (rule) {
    await db.rule.update({
      where: { id: rule.id },
      data: {
        hitCount: { increment: 1 },
        lastHitAt: new Date(),
        confidence: Math.min(0.995, rule.confidence + 0.004),
      },
    });
  } else {
    await db.rule.updateMany({
      where: { appName: app.name, source: "preset" },
      data: { hitCount: { increment: 1 }, lastHitAt: new Date() },
    });
    // v3.1.0 智能学习：首次拦截即沉淀「广告指纹」→ 下次启动 P0 预拦截
    const preemptAction =
      app.adType === "shake"
        ? "sensor_block"
        : app.adType === "deeplink"
          ? "intent_block"
          : app.guard
            ? "guard_skip"
            : app.traits?.chainJump
              ? "chain_block"
              : "preempt_block";
    await db.rule.create({
      data: {
        appName: app.name,
        appCategory: app.category,
        adType: app.adType,
        matchType: "learned",
        pattern: `首次拦截即沉淀广告指纹（容器签名 + viewId + 出现时机）→ 下次启动 P0 预拦截，广告未及呈现`,
        action: preemptAction,
        source: "learned",
        confidence: 0.78,
      },
    });
    learnedCreated = true;
  }

  return { event: ev, learnedCreated };
}

/** 漏拦截（反馈通道兼容接口）：流水 + 满 2 次自动生成学习规则 */
export async function recordMiss(input: BlockEventInput) {
  const app = APP_BY_KEY.get(input.appKey);
  if (!app) throw new Error("unknown app");

  const ev = await db.blockEvent.create({
    data: {
      appName: app.name,
      appCategory: app.category,
      adType: input.adType,
      action: "miss",
      latencyMs: Math.max(0, Math.min(60000, Math.round(input.latencyMs))),
      confidence: input.confidence,
      success: false,
      scenario: input.scenario,
      note: input.note?.slice(0, 120) ?? "对照模式：用户手动跳过",
    },
  });

  const missCount = await db.blockEvent.count({
    where: { appName: app.name, action: "miss" },
  });

  // 同一应用 ≥2 次手动跳过 → 沉淀学习规则（下次优先按习惯秒跳）
  let learnedCreated = false;
  if (missCount >= 2) {
    const existing = await db.rule.findFirst({
      where: { appName: app.name, source: "learned" },
    });
    if (!existing) {
      await db.rule.create({
        data: {
          appName: app.name,
          appCategory: app.category,
          adType: app.adType,
          matchType: "learned",
          pattern: `观察 ${missCount} 次手动跳过 → 沉淀「${app.name}」专属秒跳规则（跳过时机/控件特征已学习）`,
          action:
            app.adType === "shake"
              ? "sensor_block"
              : app.adType === "deeplink"
                ? "intent_block"
                : app.guard
                  ? "guard_skip"
                  : "instant_skip",
          source: "learned",
          confidence: 0.72,
        },
      });
      learnedCreated = true;
    }
  }

  return { event: ev, learnedCreated, missCount };
}

export interface LearningProfile {
  totals: {
    blocks: number;
    misses: number;
    guardBlocks: number;
    avgLatencyMs: number;
    successRate: number;
  };
  perApp: { appName: string; category: string; count: number; avgMs: number }[];
  hours: number[]; // 24 桶（东八区）
  scenarios: Record<string, number>;
  learnedRuleCount: number;
  recent: {
    id: string;
    ts: number;
    appName: string;
    action: string;
    adType: string;
    latencyMs: number;
    success: boolean;
    scenario: string;
  }[];
}

export async function getProfile(): Promise<LearningProfile> {
  const events = await db.blockEvent.findMany({ orderBy: { ts: "desc" }, take: 2000 });
  const learnedRuleCount = await db.rule.count({ where: { source: "learned" } });

  const blocks = events.filter((e) => e.success);
  const misses = events.filter((e) => !e.success);
  const guardBlocks = blocks.filter((e) => e.scenario === "guard");
  const avgLatencyMs = blocks.length
    ? Math.round(blocks.reduce((s, e) => s + e.latencyMs, 0) / blocks.length)
    : 0;

  const byApp = new Map<string, { category: string; count: number; ms: number }>();
  const hours = new Array(24).fill(0) as number[];
  const scenarios: Record<string, number> = {};
  for (const e of events) {
    const cur = byApp.get(e.appName) ?? { category: e.appCategory, count: 0, ms: 0 };
    cur.count += 1;
    cur.ms += e.latencyMs;
    byApp.set(e.appName, cur);
    hours[shanghaiHour(e.ts)] += 1;
    if (e.success) scenarios[e.scenario] = (scenarios[e.scenario] ?? 0) + 1;
  }

  return {
    totals: {
      blocks: blocks.length,
      misses: misses.length,
      guardBlocks: guardBlocks.length,
      avgLatencyMs,
      successRate: events.length ? blocks.length / events.length : 1,
    },
    perApp: [...byApp.entries()]
      .map(([appName, v]) => ({
        appName,
        category: v.category,
        count: v.count,
        avgMs: Math.round(v.ms / v.count),
      }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 6),
    hours,
    scenarios,
    learnedRuleCount,
    recent: events.slice(0, 14).map((e) => ({
      id: e.id,
      ts: e.ts.getTime(),
      appName: e.appName,
      action: e.action,
      adType: e.adType,
      latencyMs: e.latencyMs,
      success: e.success,
      scenario: e.scenario,
    })),
  };
}

/** 夜间同步快照：学习规则 + 画像 + 统计（供版本迭代参考） */
export async function buildSnapshot() {
  const profile = await getProfile();
  const learnedRules = await db.rule.findMany({
    where: { source: "learned" },
    orderBy: { hitCount: "desc" },
  });
  const topPreset = await db.rule.findMany({
    where: { source: "preset" },
    orderBy: { hitCount: "desc" },
    take: 8,
  });
  return {
    meta: {
      version: "3.1.0",
      generatedAt: new Date().toISOString(),
      timezone: "Asia/Shanghai",
      purpose: "学习中心夜间自动同步——为后续版本迭代提供规则与画像参考",
    },
    stats: {
      totalBlocks: profile.totals.blocks,
      totalMisses: profile.totals.misses,
      guardBlocks: profile.totals.guardBlocks,
      avgLatencyMs: profile.totals.avgLatencyMs,
      successRate: Number(profile.totals.successRate.toFixed(4)),
    },
    learnedRules: learnedRules.map((r) => ({
      appName: r.appName,
      appCategory: r.appCategory,
      adType: r.adType,
      action: r.action,
      pattern: r.pattern,
      confidence: r.confidence,
      hitCount: r.hitCount,
      enabled: r.enabled,
    })),
    habitProfile: {
      perApp: profile.perApp,
      hourHistogram: profile.hours,
      scenarioDistribution: profile.scenarios,
    },
    hotPresetRules: topPreset.map((r) => ({
      appName: r.appName,
      pattern: r.pattern,
      hitCount: r.hitCount,
    })),
    recentEvents: profile.recent.slice(0, 8),
  };
}
