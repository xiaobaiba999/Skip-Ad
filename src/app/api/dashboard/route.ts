import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { getProfile } from "@/lib/learning";
import { AD_TYPE_LABEL, POWER_PROFILE } from "@/lib/instant-engine";

export const dynamic = "force-dynamic";

/** 东八区日期键（与 learning.ts 同源逻辑：免依赖时区库） */
function shanghaiDateKey(d: Date): string {
  const t = new Date(d.getTime() + 8 * 3600 * 1000);
  return t.toISOString().slice(0, 10);
}

/** 预拦截事件判定：P0 预拦截动作，或备注带「P0 预拦截」前缀（守护类预拦截 action 为 guard_skip） */
function isPreempt(e: { action: string; note: string | null }): boolean {
  return e.action === "preempt_block" || !!e.note?.startsWith("P0 预拦截");
}

/** 广告 SDK 家族中文标签（BlockEvent.sdkFamily 聚合展示用） */
const SDK_FAMILY_LABEL: Record<string, string> = {
  csj: "穿山甲",
  gdt: "优量汇",
  ks: "快手",
  admob: "AdMob",
  host: "宿主自建",
};

/**
 * GET /api/dashboard —— 情报站可视化看板聚合
 * 汇总拦截流水：总量 KPI / 14 日趋势 / 应用排行 / 24 小时时段 /
 * 广告类型构成 / 规则命中 Top8 / 功耗对照 / SDK 家族分布。
 */
export async function GET() {
  try {
    const [events, profile] = await Promise.all([
      db.blockEvent.findMany({ orderBy: { ts: "desc" } }),
      getProfile(),
    ]);

    /* ── 14 日趋势桶（东八区日期键） ── */
    const now = Date.now();
    const dayKeys: string[] = [];
    const buckets = new Map<string, { blocks: number; misses: number; preempts: number }>();
    for (let i = 13; i >= 0; i--) {
      const key = shanghaiDateKey(new Date(now - i * 86400000));
      dayKeys.push(key);
      buckets.set(key, { blocks: 0, misses: 0, preempts: 0 });
    }
    for (const e of events) {
      const b = buckets.get(shanghaiDateKey(e.ts));
      if (!b) continue;
      if (e.success) b.blocks += 1;
      else b.misses += 1;
      if (isPreempt(e)) b.preempts += 1;
    }
    const daily = dayKeys.map((key) => ({
      date: key.slice(5), // MM-DD
      blocks: buckets.get(key)!.blocks,
      misses: buckets.get(key)!.misses,
      preempts: buckets.get(key)!.preempts,
    }));

    /* ── 广告类型构成（AD_TYPE_LABEL 窄化安全查找，查不到用原字符串） ── */
    const LABELS = AD_TYPE_LABEL as Record<string, string>;
    const adTypeMap = new Map<string, number>();
    for (const e of events) {
      adTypeMap.set(e.adType, (adTypeMap.get(e.adType) ?? 0) + 1);
    }
    const adTypes = [...adTypeMap.entries()]
      .map(([adType, count]) => ({ adType, label: LABELS[adType] ?? adType, count }))
      .sort((a, b) => b.count - a.count);

    /* ── 规则命中率 Top8（note 非空事件按 note 聚合） ── */
    const ruleMap = new Map<string, { count: number; msSum: number }>();
    for (const e of events) {
      if (!e.note) continue;
      const cur = ruleMap.get(e.note) ?? { count: 0, msSum: 0 };
      cur.count += 1;
      cur.msSum += e.latencyMs;
      ruleMap.set(e.note, cur);
    }
    const ruleHit = [...ruleMap.entries()]
      .map(([ruleName, v]) => ({ ruleName, count: v.count, avgMs: Math.round(v.msSum / v.count) }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 8);

    /* ── SDK 家族分布（null 归「未标记」） ── */
    const sdkMap = new Map<string, number>();
    for (const e of events) {
      const key = e.sdkFamily ?? "none";
      sdkMap.set(key, (sdkMap.get(key) ?? 0) + 1);
    }
    const sdkFamily = [...sdkMap.entries()]
      .map(([family, count]) => ({
        family,
        label: family === "none" ? "未标记" : (SDK_FAMILY_LABEL[family] ?? family),
        count,
      }))
      .sort((a, b) => b.count - a.count);

    return NextResponse.json({
      totals: { ...profile.totals, events: events.length },
      daily,
      perApp: profile.perApp,
      hours: profile.hours,
      adTypes,
      ruleHit,
      power: { v520: POWER_PROFILE.v520, v530: POWER_PROFILE.v530 },
      sdkFamily,
    });
  } catch (e) {
    console.error("[dashboard GET]", e);
    return NextResponse.json({ error: "看板数据加载失败" }, { status: 500 });
  }
}
