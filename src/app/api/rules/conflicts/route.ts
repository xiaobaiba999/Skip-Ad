import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { matchTypeLabel } from "@/lib/preset-rules";
import { detectConflicts } from "@/lib/rule-scheduler";
import type { RuleSourceKind, SchedulerRule } from "@/lib/rule-scheduler";

export const dynamic = "force-dynamic";

/**
 * GET /api/rules/conflicts —— 多源规则冲突检测
 * 全部启用规则 → detectConflicts → high 严重度且新增的冲突按
 * ruleAId+ruleBId+kind 判重后写入 db.ruleConflict → 返回全量冲突与持久化数。
 */
export async function GET() {
  try {
    const rules = await db.rule.findMany({ where: { enabled: true } });
    const schedulerRules: SchedulerRule[] = rules.map((r) => ({
      id: r.id,
      name: `${r.appName === "*" ? "全局" : r.appName}·${matchTypeLabel(r.matchType)}`,
      source: (["preset", "gkd", "learned", "custom"].includes(r.source) ? r.source : "preset") as RuleSourceKind,
      appName: r.appName,
      adType: r.adType,
      action: r.action,
      priority: r.priority,
      enabled: r.enabled,
      quality: r.quality,
      hitCount: r.hitCount,
      pattern: r.pattern,
      status: r.status,
      sdkFamily: r.sdkFamily,
    }));

    const conflicts = detectConflicts(schedulerRules);

    // high 严重度且未记录过的冲突 → 落库留痕（供规则中心展示与人工处置）
    const existing = await db.ruleConflict.findMany({ select: { ruleAId: true, ruleBId: true, kind: true } });
    const seen = new Set(existing.map((c) => `${c.ruleAId}|${c.ruleBId}|${c.kind}`));
    const toCreate = conflicts.filter(
      (c) => c.severity === "high" && !seen.has(`${c.ruleAId}|${c.ruleBId}|${c.kind}`)
    );
    let persisted = 0;
    if (toCreate.length) {
      const res = await db.ruleConflict.createMany({
        data: toCreate.map((c) => ({
          ruleAId: c.ruleAId,
          ruleBId: c.ruleBId,
          kind: c.kind,
          severity: c.severity,
          detail: c.detail,
          suggestion: c.suggestion,
        })),
      });
      persisted = res.count;
    }

    return NextResponse.json({ conflicts, persisted });
  } catch (e) {
    console.error("[rules/conflicts GET]", e);
    return NextResponse.json({ error: "冲突检测失败" }, { status: 500 });
  }
}
