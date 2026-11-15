import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { ensureSeed } from "@/lib/ad-seed";

// GET /api/stats —— 数据看板：各广告曝光/点击/CTR 汇总
export async function GET() {
  try {
    await ensureSeed();
    const ads = await db.ad.findMany({ orderBy: [{ type: "asc" }, { views: "desc" }] });
    const stats = ads.map((a) => ({
      id: a.id,
      name: a.name,
      type: a.type,
      strategy: a.strategy,
      active: a.active,
      views: a.views,
      clicks: a.clicks,
      ctr: a.views > 0 ? a.clicks / a.views : 0,
    }));
    const totals = {
      views: stats.reduce((s, x) => s + x.views, 0),
      clicks: stats.reduce((s, x) => s + x.clicks, 0),
    };
    return NextResponse.json({
      stats,
      totals: { ...totals, ctr: totals.views > 0 ? totals.clicks / totals.views : 0 },
    });
  } catch (e) {
    console.error("[stats GET]", e);
    return NextResponse.json({ error: "统计加载失败" }, { status: 500 });
  }
}
