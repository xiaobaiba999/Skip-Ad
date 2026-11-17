/**
 * 学习数据同步 GitHub（SERVER）
 * ─────────────────────────────────────────────────────────────
 * · 每晚 02:00（Asia/Shanghai）自动把学习规则 + 使用画像提交到
 *   仓库 data/learning/ 目录，供后续版本迭代参考；
 * · 学习中心亦可手动触发「立即同步」；
 * · 同步产物：latest.json（覆盖更新）+ history/YYYY-MM-DD.json（按日归档）。
 */
import { db } from "./db";
import { putFile } from "./github";
import { buildSnapshot, todayKey } from "./learning";
import { APP_VERSION } from "./version";

export const NIGHTLY_HOUR = 2; // Asia/Shanghai
export const NIGHTLY_LABEL = "每日 02:00";

export interface SyncResult {
  ok: boolean;
  message: string;
  url?: string;
  sha?: string;
  ruleCount: number;
  blockSum: number;
}

export async function syncLearningToGithub(trigger: "nightly" | "manual"): Promise<SyncResult> {
  const snapshot = await buildSnapshot();
  const json = JSON.stringify(snapshot, null, 2);
  const ruleCount = snapshot.learnedRules.length;
  const blockSum = snapshot.stats.totalBlocks;
  const date = todayKey();
  const message = `chore(data): 学习规则${trigger === "nightly" ? "夜间" : "手动"}同步 v${APP_VERSION} · ${date} · 学习规则 ${ruleCount} 条 · 累计拦截 ${blockSum} 次`;

  const latest = await putFile({
    path: "data/learning/latest.json",
    content: json,
    message: `${message}（latest）`,
  });
  if (!latest.ok) {
    await db.syncLog.create({
      data: {
        kind: trigger,
        ok: false,
        message: `latest.json 提交失败：${latest.message ?? "未知错误"}`,
        ruleCount,
        blockSum,
      },
    });
    return { ok: false, message: latest.message ?? "提交失败", ruleCount, blockSum };
  }

  const history = await putFile({
    path: `data/learning/history/${date}.json`,
    content: json,
    message: `${message}（history 归档）`,
  });

  const ok = history.ok;
  const url = latest.url ?? undefined;
  await db.syncLog.create({
    data: {
      kind: trigger,
      ok,
      message: ok
        ? `已提交 data/learning/latest.json 与 history/${date}.json`
        : `latest 成功，history 归档失败：${history.message ?? "未知错误"}`,
      sha: latest.sha,
      url,
      ruleCount,
      blockSum,
    },
  });

  return {
    ok,
    message: ok ? `已同步学习规则 ${ruleCount} 条（累计拦截 ${blockSum} 次）到 GitHub` : history.message ?? "归档失败",
    url,
    sha: latest.sha,
    ruleCount,
    blockSum,
  };
}

/** 是否该跑夜间同步（东八区当日 02:00 后未同步过） */
export async function shouldRunNightly(now = new Date()): Promise<boolean> {
  const cst = new Date(now.getTime() + 8 * 3600 * 1000);
  if (cst.getUTCHours() < NIGHTLY_HOUR) return false;
  const todayStart = new Date(`${todayKey(now)}T02:00:00+08:00`).getTime();
  const last = await db.syncLog.findFirst({
    where: { kind: "nightly", ok: true, createdAt: { gte: new Date(todayStart) } },
  });
  return !last;
}

/** 供 instrumentation 调度器使用：到点且未同步 → 执行 */
export async function maybeRunNightlySync(): Promise<boolean> {
  if (!(await shouldRunNightly())) return false;
  try {
    const r = await syncLearningToGithub("nightly");
    console.log(`[sync] 夜间同步 ${r.ok ? "完成" : "失败"}：${r.message}`);
    return r.ok;
  } catch (e) {
    console.error("[sync] 夜间同步异常", e);
    return false;
  }
}
