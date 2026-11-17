import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import { ensureRulesSeed, matchTypeLabel } from "@/lib/preset-rules";

export const dynamic = "force-dynamic";

/** GET /api/rules —— 规则库（预置 + 学习） */
export async function GET() {
  try {
    await ensureRulesSeed();
    const rules = await db.rule.findMany({
      orderBy: [{ source: "desc" }, { hitCount: "desc" }, { createdAt: "asc" }],
    });
    return NextResponse.json({
      rules: rules.map((r) => ({
        id: r.id,
        appName: r.appName,
        appCategory: r.appCategory,
        adType: r.adType,
        matchType: r.matchType,
        matchLabel: matchTypeLabel(r.matchType),
        pattern: r.pattern,
        action: r.action,
        source: r.source,
        confidence: r.confidence,
        hitCount: r.hitCount,
        enabled: r.enabled,
        lastHitAt: r.lastHitAt?.getTime() ?? null,
      })),
    });
  } catch (e) {
    console.error("[rules GET]", e);
    return NextResponse.json({ error: "规则库加载失败" }, { status: 500 });
  }
}

/** PATCH /api/rules —— 启停规则 Body: { id, enabled } */
export async function PATCH(req: NextRequest) {
  try {
    const b = await req.json();
    if (typeof b?.id !== "string" || typeof b?.enabled !== "boolean") {
      return NextResponse.json({ error: "参数非法" }, { status: 400 });
    }
    const r = await db.rule.update({
      where: { id: b.id },
      data: { enabled: b.enabled },
    });
    return NextResponse.json({ ok: true, id: r.id, enabled: r.enabled });
  } catch (e) {
    console.error("[rules PATCH]", e);
    return NextResponse.json({ error: "更新失败" }, { status: 500 });
  }
}
