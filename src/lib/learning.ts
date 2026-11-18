/**
 * 学习中心（SERVER · v5.4.0 智能学习 + 多源统一调度联动）
 * ─────────────────────────────────────────────────────────────
 * · recordBlock：拦截成功 → 拦截流水 + 规则命中累计；首次拦截即
 *   沉淀「广告指纹」学习规则（容器签名 + viewId + 出现时机），
 *   下次启动直接 P0 预拦截（广告未及呈现）。
 *   v5.4.0：沉淀前过 isNoiseSample 噪声过滤（拒绝沉淀返回 rejectedReason）；
 *   命中/漏拦联动 rule-quality 质量模型（quality + status 生命周期落库）；
 *   含 SDK 家族特征的应用同步 generalizeRule 生成跨应用泛化规则。
 * · recordMiss：反馈通道兼容接口（对照/反馈场景手动跳过留痕）。
 *   v5.4.0：学习规则 missCount 累计 + 质量模型联动。
 * · getProfile：使用习惯画像（应用分布 / 时段分布 / 场景分布 / 耗时）
 * · buildSnapshot：夜间同步 GitHub 的数据快照（学习规则 + 画像 + 统计）。
 *   v5.4.0：meta.version 升 5.4.0；recentEvents 应用名经 redactSnapshot 脱敏。
 */
import { db } from "./db";
import { SCENARIO_APPS } from "./scenario-apps";
import type { ScenarioApp } from "./scenario-apps";
import { scoreRule, isNoiseSample, lifecycleTransition, generalizeRule } from "./rule-quality";
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
  /** v5.4.0：命中的广告 SDK 家族（缺省时按场景应用特征推断） */
  sdkFamily?: string;
}

const APP_BY_KEY = new Map(SCENARIO_APPS.map((a) => [a.key, a]));

/** v5.4.0：场景应用 → SDK 家族硬映射（演示用途；真机端由窗口 className 指纹判定） */
const APP_SDK_HINT: Array<[RegExp, string]> = [
  [/喜马拉雅/, "csj"], // 专案 v3：SDK 广告窗（穿山甲系渲染）
  [/优品严选/, "gdt"], // 半屏插屏（优量汇 HalfScreenExpressAd 形态）
  [/趣看点/, "csj"], // 信息流（穿山甲 NativeExpressView 形态）
];

function inferSdkFamily(app: ScenarioApp): string | null {
  for (const [re, fam] of APP_SDK_HINT) {
    if (re.test(app.name)) return fam;
  }
  if (app.traits?.sdkWindow) return "csj";
  return null;
}

function shanghaiHour(d: Date): number {
  // UTC+8 小时（用于时段画像，免依赖时区库）
  return (d.getUTCHours() + 8) % 24;
}

function shanghaiDateKey(d: Date): string {
  const t = new Date(d.getTime() + 8 * 3600 * 1000);
  return t.toISOString().slice(0, 10);
}

/** 创建至今天数（质量评分的时效输入，下限 0） */
function daysSince(from: Date, to: Date = new Date()): number {
  return Math.max(0, Math.round((to.getTime() - from.getTime()) / 86400000));
}

export function todayKey(d: Date = new Date()): string {
  return shanghaiDateKey(d);
}

/** 拦截成功：写流水 + 命中规则累计；首次拦截即沉淀预拦截指纹 */
export async function recordBlock(input: BlockEventInput) {
  const app = APP_BY_KEY.get(input.appKey);
  if (!app) throw new Error("unknown app");
  const sdkFamily = input.sdkFamily?.trim() || inferSdkFamily(app);

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
      sdkFamily: sdkFamily ?? null,
      note: input.note?.slice(0, 120) ?? null,
    },
  });

  // 命中应用专属规则（优先学习规则，其次预置）
  const rule = await db.rule.findFirst({
    where: { appName: app.name, source: "learned" },
  });
  let learnedCreated = false;
  let rejectedReason: string | undefined;
  let generalized = false;
  if (rule) {
    // v5.4.0：命中累计 + 质量模型联动（missCount 不增；quality/status 由质量模型驱动）
    const report = scoreRule({
      hitCount: rule.hitCount + 1,
      missCount: rule.missCount,
      falseCount: rule.falseCount,
      lastHitAt: Date.now(),
      createdDaysAgo: daysSince(rule.createdAt),
    });
    await db.rule.update({
      where: { id: rule.id },
      data: {
        hitCount: { increment: 1 },
        lastHitAt: new Date(),
        confidence: Math.min(0.995, rule.confidence + 0.004),
        quality: report.score,
        status: lifecycleTransition(report, 0), // 刚命中，daysSinceLastHit = 0
      },
    });
  } else {
    await db.rule.updateMany({
      where: { appName: app.name, source: "preset" },
      data: { hitCount: { increment: 1 }, lastHitAt: new Date() },
    });

    // v5.4.0 噪声样本过滤（李跳跳式事后验证）：同秒多点位 / 关窗验证不足 /
    // 窗类不稳 / 超窗命中 → 拒绝沉淀，防误学聊天按钮等伪规则
    const noiseCheck = isNoiseSample({
      sameSecondHits: 1,
      treeShrinkPct: 65,
      windowClassStable: true,
      hitAfterMs: app.traits?.realSkipDelayMs ?? 800,
    });
    if (noiseCheck.noise) {
      rejectedReason = `噪声样本拒绝沉淀：${noiseCheck.reason}`;
    } else {
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
      const initReport = scoreRule({
        hitCount: 1,
        missCount: 0,
        falseCount: 0,
        lastHitAt: Date.now(),
        createdDaysAgo: 0,
      });
      const learnedPattern = `首次拦截即沉淀广告指纹（容器签名 + viewId + 出现时机）→ 下次启动 P0 预拦截，广告未及呈现`;
      await db.rule.create({
        data: {
          appName: app.name,
          appCategory: app.category,
          adType: app.adType,
          matchType: "learned",
          pattern: learnedPattern,
          action: preemptAction,
          source: "learned",
          confidence: 0.78,
          sdkFamily: sdkFamily ?? null,
          quality: initReport.score,
          status: lifecycleTransition(initReport, 0),
        },
      });
      learnedCreated = true;

      // v5.4.0 规则泛化：同 SDK 家族的 viewId/容器签名可跨应用复用
      // （appName="sdk:csj" 等，generalizedFrom 记录源应用；幂等：同家族同型只泛化一次）
      if (sdkFamily) {
        const gen = generalizeRule({
          appName: app.name,
          sdkFamily,
          pattern: learnedPattern,
          action: preemptAction,
          adType: app.adType,
        });
        if (gen) {
          const dup = await db.rule.findFirst({
            where: { appName: gen.appName, source: "learned", adType: app.adType },
          });
          if (!dup) {
            await db.rule.create({
              data: {
                appName: gen.appName,
                appCategory: "通用",
                adType: app.adType,
                matchType: "learned",
                pattern: gen.pattern,
                action: preemptAction,
                source: "learned",
                confidence: 0.7,
                generalizedFrom: app.name,
                sdkFamily,
              },
            });
            generalized = true;
          }
        }
      }
    }
  }

  return { event: ev, learnedCreated, rejectedReason, generalized };
}

/** 漏拦截（反馈通道兼容接口）：流水 + 满 2 次自动生成学习规则 */
export async function recordMiss(input: BlockEventInput) {
  const app = APP_BY_KEY.get(input.appKey);
  if (!app) throw new Error("unknown app");
  const sdkFamily = input.sdkFamily?.trim() || inferSdkFamily(app);

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
      sdkFamily: sdkFamily ?? null,
      note: input.note?.slice(0, 120) ?? "对照模式：用户手动跳过",
    },
  });

  const missCount = await db.blockEvent.count({
    where: { appName: app.name, action: "miss" },
  });

  let learnedCreated = false;
  const existing = await db.rule.findFirst({
    where: { appName: app.name, source: "learned" },
  });
  if (existing) {
    // v5.4.0：漏拦截计数（missCount）累计 + 质量模型联动（quality/status 落库）
    const report = scoreRule({
      hitCount: existing.hitCount,
      missCount: existing.missCount + 1,
      falseCount: existing.falseCount,
      lastHitAt: existing.lastHitAt?.getTime() ?? null,
      createdDaysAgo: daysSince(existing.createdAt),
    });
    await db.rule.update({
      where: { id: existing.id },
      data: {
        missCount: { increment: 1 },
        quality: report.score,
        status: lifecycleTransition(report, existing.lastHitAt ? daysSince(existing.lastHitAt) : daysSince(existing.createdAt)),
      },
    });
  } else if (missCount >= 2) {
    // 同一应用 ≥2 次手动跳过 → 沉淀学习规则（下次优先按习惯秒跳）
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
        sdkFamily: sdkFamily ?? null,
      },
    });
    learnedCreated = true;
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

/** 确定性哈希别名：名称字符 codePoint 求和 → hex（recentEvents 脱敏用） */
function hashAlias(name: string): string {
  let sum = 0;
  for (const ch of name) {
    sum = (sum + (ch.codePointAt(0) ?? 0)) % 0xffff;
  }
  return `app_${sum.toString(16)}`;
}

/**
 * v5.4.0 快照脱敏：移除 recentEvents 中的应用名 → 哈希别名（确定性，
 * 同名恒得同别名，可跨日聚合）；统计字段（画像/分布/计数）全部保留。
 */
export function redactSnapshot<T extends { recentEvents?: { appName: string }[] }>(snapshot: T): T {
  if (Array.isArray(snapshot.recentEvents)) {
    snapshot.recentEvents = snapshot.recentEvents.map((e) => ({ ...e, appName: hashAlias(e.appName) }));
  }
  return snapshot;
}

/** 夜间同步快照：学习规则 + 画像 + 统计（供版本迭代参考）
 *  注意：导出的 learnedRules 保留应用名（本演示仓库场景名为自有虚构名，
 *  非真实隐私）；真机端将统一走 redactSnapshot 别名通道。 */
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
  return redactSnapshot({
    meta: {
      version: "5.4.0",
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
  });
}
