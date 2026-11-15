import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

const KINDS = ["decision", "correction", "skip", "engage", "quickback", "cancel"];
const NUM = (v: unknown): number | undefined => {
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 ? Math.round(n) : undefined;
};

// POST /api/events —— 判断引擎埋点：拦截/放行/纠正/跳过时机/停留反馈
export async function POST(req: NextRequest) {
  try {
    const b = await req.json();
    if (!b || typeof b.kind !== "string" || !KINDS.includes(b.kind)) {
      return NextResponse.json({ error: "kind 非法" }, { status: 400 });
    }
    const data: Record<string, unknown> = { kind: b.kind };
    if (typeof b.adId === "string") data.adId = b.adId;
    if (typeof b.adName === "string") data.adName = b.adName.slice(0, 60);
    if (typeof b.theme === "string") data.theme = b.theme;
    if (typeof b.trigger === "string") data.trigger = b.trigger.slice(0, 20);
    if (typeof b.action === "string") data.action = b.action.slice(0, 20);
    if (typeof b.score === "number") data.score = Math.max(0, Math.min(100, b.score));
    if (Array.isArray(b.reasons)) data.reasons = JSON.stringify(b.reasons).slice(0, 1500);
    for (const k of ["pressMs", "movePx", "reactionMs", "dwellMs"] as const) {
      const v = NUM(b[k]);
      if (v !== undefined) data[k] = v;
    }
    const ev = await db.adEvent.create({ data: data as never });
    return NextResponse.json({ ok: true, id: ev.id });
  } catch (e) {
    console.error("[events POST]", e);
    return NextResponse.json({ error: "写入失败" }, { status: 500 });
  }
}

// GET /api/events —— 最近 50 条（调试/展示用）
export async function GET() {
  try {
    const events = await db.adEvent.findMany({ orderBy: { ts: "desc" }, take: 50 });
    return NextResponse.json({ events });
  } catch (e) {
    console.error("[events GET]", e);
    return NextResponse.json({ error: "读取失败" }, { status: 500 });
  }
}
