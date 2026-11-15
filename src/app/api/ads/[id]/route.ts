import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

// PATCH /api/ads/:id —— 更新广告（启停 / 编辑权重等）
export async function PATCH(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    const body = await req.json();
    const data: Record<string, unknown> = {};
    if (typeof body.active === "boolean") data.active = body.active;
    if (body.weight !== undefined) data.weight = Math.min(10, Math.max(1, Number(body.weight) || 5));
    if (typeof body.strategy === "string" && ["standard", "timer", "shake", "anyclick"].includes(body.strategy)) data.strategy = body.strategy;
    if (typeof body.name === "string" && body.name) data.name = body.name;
    if (typeof body.title === "string" && body.title) data.title = body.title;
    if (typeof body.subtitle === "string") data.subtitle = body.subtitle;
    const ad = await db.ad.update({ where: { id }, data });
    return NextResponse.json({ ad });
  } catch (e) {
    console.error("[ads PATCH]", e);
    return NextResponse.json({ error: "更新失败" }, { status: 500 });
  }
}

// DELETE /api/ads/:id —— 删除广告
export async function DELETE(_req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    await db.ad.delete({ where: { id } });
    return NextResponse.json({ ok: true });
  } catch (e) {
    console.error("[ads DELETE]", e);
    return NextResponse.json({ error: "删除失败" }, { status: 500 });
  }
}
