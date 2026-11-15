import { NextResponse } from "next/server";
import { db } from "@/lib/db";

/**
 * GET /api/learn —— 学习画像聚合
 * 从 AdEvent 流水实时聚合：
 *  · stats：拦截/放行/纠正/精准度（precision = 1 - 纠正/拦截）
 *  · rules.themeBias：engage(长停留) → 放行倾向；quickback(秒退)/cancel → 拦截倾向
 *  · rules.autoSkipDelayMs：最近 ≥3 次手动跳过样本的均值 → 下次自动跳过时机
 *  · rules.relaxQuickTap：纠正 ≥2 次 → 提高拦截门槛，放宽快速点击
 */
export async function GET() {
  try {
    const events = await db.adEvent.findMany({ orderBy: { ts: "desc" }, take: 500 });

    const decisions = events.filter((e) => e.kind === "decision");
    const blocked = decisions.filter((e) => e.action === "blocked").length;
    const wouldBlock = decisions.filter((e) => e.action === "would-block").length;
    const allowed = decisions.filter((e) => e.action === "allowed").length;
    const corrections = events.filter((e) => e.kind === "correction").length;
    const precision = blocked > 0 ? Math.max(0, 1 - corrections / blocked) : 1;

    // 主题偏好
    const themeBias: Record<string, number> = {};
    const cap = (v: number) => Math.max(-20, Math.min(20, v));
    for (const e of events) {
      if (!e.theme) continue;
      const cur = themeBias[e.theme] ?? 0;
      if (e.kind === "engage") themeBias[e.theme] = cap(cur - 8); // 长停留 → 感兴趣 → 放行
      else if (e.kind === "quickback") themeBias[e.theme] = cap(cur + 8); // 秒退 → 反感 → 拦截
      else if (e.kind === "cancel") themeBias[e.theme] = cap(cur + 4); // 取消二跳 → 不想被跳
    }

    // 自动跳过时机：最近 6 个样本均值（事件按时间倒序）
    const skips = events.filter((e) => e.kind === "skip" && typeof e.dwellMs === "number");
    const recent = skips.slice(0, 6);
    let autoSkipDelayMs: number | null = null;
    if (recent.length >= 3) {
      const avg = recent.reduce((s, e) => s + (e.dwellMs ?? 0), 0) / recent.length;
      autoSkipDelayMs = Math.max(400, Math.min(3500, Math.round(avg)));
    }

    const list = events.slice(0, 20).map((e) => {
      let reason0 = "";
      try {
        reason0 = e.reasons ? (JSON.parse(e.reasons)[0]?.label ?? "") : "";
      } catch {
        reason0 = "";
      }
      return {
        id: e.id,
        ts: e.ts.getTime(),
        kind: e.kind,
        trigger: e.trigger,
        action: e.action,
        score: e.score,
        reason0,
        adName: e.adName,
        theme: e.theme,
        dwellMs: e.dwellMs,
      };
    });

    return NextResponse.json({
      stats: { blocked, allowed, wouldBlock, corrections, precision },
      rules: { themeBias, relaxQuickTap: corrections >= 2, autoSkipDelayMs, samples: skips.length },
      recent: list,
    });
  } catch (e) {
    console.error("[learn GET]", e);
    return NextResponse.json({ error: "画像加载失败" }, { status: 500 });
  }
}
