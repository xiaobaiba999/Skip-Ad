import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { getProfile } from "@/lib/learning";

export const dynamic = "force-dynamic";

/** GET /api/stats —— 情报站数据看板（拦截统计 + 规则库概览） */
export async function GET() {
  try {
    const profile = await getProfile();
    const [presetCount, learnedCount, feedbackCount] = await Promise.all([
      db.rule.count({ where: { source: "preset" } }),
      db.rule.count({ where: { source: "learned" } }),
      db.feedbackIssue.count(),
    ]);
    return NextResponse.json({
      totals: profile.totals,
      perApp: profile.perApp,
      scenarios: profile.scenarios,
      rules: { preset: presetCount, learned: learnedCount },
      feedbackCount,
    });
  } catch (e) {
    console.error("[stats GET]", e);
    return NextResponse.json({ error: "统计加载失败" }, { status: 500 });
  }
}
