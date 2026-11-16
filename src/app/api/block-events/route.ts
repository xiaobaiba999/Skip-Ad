import { NextRequest, NextResponse } from "next/server";
import { recordBlock, recordMiss } from "@/lib/learning";
import type { AdType, BlockAction, ScenarioKind } from "@/lib/instant-engine";

export const dynamic = "force-dynamic";

const AD_TYPES = ["splash", "interstitial", "shake", "deeplink"];
const ACTIONS = ["instant_skip", "sensor_block", "intent_block", "guard_skip", "miss"];
const SCENARIOS = ["normal", "shake", "deeplink", "guard"];

/**
 * POST /api/block-events —— 演练场拦截埋点
 * Body: { appKey, kind: "block" | "miss", action, latencyMs, confidence, scenario, adType, note? }
 * · block：拦截成功 → 流水 + 规则命中累计
 * · miss：对照模式手动跳过 → 流水 + 满 2 次自动生成学习规则
 */
export async function POST(req: NextRequest) {
  try {
    const b = await req.json();
    if (typeof b?.appKey !== "string" || !["block", "miss"].includes(b.kind)) {
      return NextResponse.json({ error: "参数非法" }, { status: 400 });
    }
    const adType = AD_TYPES.includes(b.adType) ? (b.adType as AdType) : "splash";
    const action = (b.kind === "miss" ? "miss" : ACTIONS.includes(b.action) ? b.action : "instant_skip") as BlockAction;
    const scenario = SCENARIOS.includes(b.scenario) ? (b.scenario as ScenarioKind) : "normal";

    const input = {
      appKey: b.appKey,
      kind: b.kind as "block" | "miss",
      action,
      latencyMs: Number(b.latencyMs) || 0,
      confidence: Number(b.confidence) || 0,
      scenario,
      adType,
      note: typeof b.note === "string" ? b.note : undefined,
    };

    const r = b.kind === "miss" ? await recordMiss(input) : await recordBlock(input);
    return NextResponse.json({
      ok: true,
      id: "event" in r ? r.event.id : r.id,
      learnedCreated: "learnedCreated" in r ? r.learnedCreated : false,
    });
  } catch (e) {
    console.error("[block-events POST]", e);
    return NextResponse.json({ error: "写入失败" }, { status: 500 });
  }
}
