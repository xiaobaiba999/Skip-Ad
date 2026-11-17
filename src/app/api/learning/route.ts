import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { getProfile } from "@/lib/learning";
import { NIGHTLY_LABEL } from "@/lib/sync";

export const dynamic = "force-dynamic";

/**
 * GET /api/learning —— 学习中心数据
 * · profile：使用画像（应用/时段/场景分布、平均耗时、成功率）
 * · learnedRules：学习规则明细
 * · sync：夜间同步状态（最近记录 + 调度说明）
 */
export async function GET() {
  try {
    const profile = await getProfile();
    const learnedRules = await db.rule.findMany({
      where: { source: "learned" },
      orderBy: [{ enabled: "desc" }, { hitCount: "desc" }],
    });
    const lastSync = await db.syncLog.findFirst({ orderBy: { createdAt: "desc" } });

    return NextResponse.json({
      profile,
      learnedRules: learnedRules.map((r) => ({
        id: r.id,
        appName: r.appName,
        appCategory: r.appCategory,
        adType: r.adType,
        action: r.action,
        pattern: r.pattern,
        confidence: r.confidence,
        hitCount: r.hitCount,
        enabled: r.enabled,
        lastHitAt: r.lastHitAt?.getTime() ?? null,
      })),
      sync: {
        schedule: NIGHTLY_LABEL,
        last: lastSync
          ? {
              kind: lastSync.kind,
              ok: lastSync.ok,
              message: lastSync.message,
              url: lastSync.url,
              ruleCount: lastSync.ruleCount,
              blockSum: lastSync.blockSum,
              at: lastSync.createdAt.getTime(),
            }
          : null,
      },
    });
  } catch (e) {
    console.error("[learning GET]", e);
    return NextResponse.json({ error: "学习数据加载失败" }, { status: 500 });
  }
}
