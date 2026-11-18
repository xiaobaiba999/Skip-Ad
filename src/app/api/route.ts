import { NextResponse } from "next/server";
import { db } from "@/lib/db";
import { getGithubToken } from "@/lib/credentials";
import { APP_VERSION, GITHUB_REPO } from "@/lib/version";
import { NIGHTLY_LABEL } from "@/lib/sync";

export const dynamic = "force-dynamic";

/** 计算下一个夜间同步时刻（东八区次日 02:00 的 ISO 时间；当前未到今日 02:00 则为今日） */
function nextNightlyAt(now = new Date()): string {
  const cst = new Date(now.getTime() + 8 * 3600 * 1000);
  const dayShift = cst.getUTCHours() >= 2 ? 1 : 0;
  const next = new Date(cst);
  next.setUTCHours(2, 0, 0, 0);
  next.setUTCDate(next.getUTCDate() + dayShift);
  return next.toISOString().replace("Z", "+08:00");
}

/**
 * GET /api —— 健康检查 / 服务自描述
 * · db：Prisma 连通性（SELECT 1）
 * · github：内置令牌是否就绪（不出示令牌本体）
 * · nightly：夜间同步计划与下次执行时刻
 */
export async function GET() {
  let dbOk = false;
  try {
    await db.$queryRaw`SELECT 1`;
    dbOk = true;
  } catch (e) {
    console.error("[api health] db check failed", e);
  }

  return NextResponse.json({
    ok: dbOk,
    app: "Skip-Ad · 秒跳引擎",
    version: APP_VERSION,
    repo: GITHUB_REPO,
    db: dbOk ? "ok" : "unreachable",
    github: getGithubToken() ? "token-ready" : "token-missing",
    nightly: { schedule: NIGHTLY_LABEL, nextAt: nextNightlyAt() },
    time: new Date().toISOString(),
  });
}
