import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

// POST /api/ads/:id/track —— 埋点上报 { event: "view" | "click" }
export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    const body = await req.json().catch(() => ({}));
    if (body?.event === "click") {
      await db.ad.update({ where: { id }, data: { clicks: { increment: 1 } } });
    } else {
      await db.ad.update({ where: { id }, data: { views: { increment: 1 } } });
    }
    return NextResponse.json({ ok: true });
  } catch (e) {
    console.error("[ads track]", e);
    return NextResponse.json({ error: "上报失败" }, { status: 500 });
  }
}
