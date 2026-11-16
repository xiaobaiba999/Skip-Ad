import { NextResponse } from "next/server";
import { syncLearningToGithub } from "@/lib/sync";

export const dynamic = "force-dynamic";

/**
 * POST /api/learning/sync —— 手动触发学习数据同步 GitHub
 * （与每日 02:00 夜间自动同步同一条通道：data/learning/*.json）
 */
export async function POST() {
  try {
    const r = await syncLearningToGithub("manual");
    return NextResponse.json(r, { status: r.ok ? 200 : 502 });
  } catch (e) {
    console.error("[learning/sync POST]", e);
    return NextResponse.json({ ok: false, message: "同步异常，请稍后重试" }, { status: 500 });
  }
}
