/** v4.5.0 发布 + issue 回复 一体化持久任务（等待 GitHub API 窗口，幂等可重跑） */
import { getGithubToken } from "../src/lib/credentials";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};
const OWNER = "xiaobaiba999";
const REPO = "Skip-Ad";
const BASE = `/repos/${OWNER}/${REPO}`;
const TAG = "v4.5.0";
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v4.5.0.apk";
const APK_NAME = "adshield-v4.5.0.apk";
const NOTES = readFileSync("/home/z/skip-ad-repo/scripts/release-tmp/v450-notes.md", "utf8");
const REPLY_42_44 = readFileSync("/home/z/skip-ad-repo/scripts/release-tmp/v450-issue-reply.md", "utf8");

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const log = (m: string) => console.log(`[${new Date().toISOString().slice(11, 19)}] ${m}`);

/** 单次尝试（不内部重试——由外层窗口循环控制） */
async function tryPublish(): Promise<boolean> {
  // 探活：direct repo GET（窗口判定）
  const probe = await fetch(`https://api.github.com${BASE}`, { headers: H, cache: "no-store" });
  if (!probe.ok) return false;
  log("窗口打开 ✓ 开始发布流程");

  // ① release（幂等）
  let releaseId: number | null = null;
  let releaseUrl = "";
  const exist = await fetch(`https://api.github.com${BASE}/releases/tags/${TAG}`, { headers: H });
  if (exist.ok) {
    const d = await exist.json();
    releaseId = d.id;
    releaseUrl = d.html_url;
    log("release 已存在，复用: " + releaseUrl);
  } else {
    const cr = await fetch(`https://api.github.com${BASE}/releases`, {
      method: "POST",
      headers: { ...H, "Content-Type": "application/json" },
      body: JSON.stringify({
        tag_name: TAG,
        name: `开屏卫士 v4.5.0 · 乱点根治 · 系统开关彻底解耦 · 原生探针提速`,
        body: NOTES,
      }),
    });
    const d = await cr.json().catch(() => ({}) as any);
    if (cr.status === 422 && /already_exists/i.test(String(d.message ?? ""))) {
      const again = await fetch(`https://api.github.com${BASE}/releases/tags/${TAG}`, { headers: H });
      if (again.ok) {
        const d2 = await again.json();
        releaseId = d2.id;
        releaseUrl = d2.html_url;
      }
    } else if (!cr.ok) {
      log("release 创建失败: " + cr.status + " " + String(d.message).slice(0, 80));
      return false;
    } else {
      releaseId = d.id;
      releaseUrl = d.html_url;
      log("release 创建成功: " + releaseUrl);
    }
  }
  if (!releaseId) return false;

  // ② APK 资产（幂等：同名先删）
  const assets = await (await fetch(`https://api.github.com${BASE}/releases/${releaseId}/assets`, { headers: H })).json().catch(() => []);
  let haveApk = false;
  for (const a of assets ?? []) {
    if (a.name === APK_NAME) {
      if ((a.size ?? 0) === readFileSync(APK).length) {
        haveApk = true;
        log("APK 资产已存在且大小一致，跳过上传");
      } else {
        await fetch(`https://api.github.com/repos/${OWNER}/${REPO}/releases/assets/${a.id}`, { method: "DELETE", headers: H });
        log("旧 APK 资产已删除（大小不符）");
      }
    }
  }
  if (!haveApk) {
    const up = await fetch(
      `https://uploads.github.com/repos/${OWNER}/${REPO}/releases/${releaseId}/assets?name=${APK_NAME}`,
      { method: "POST", headers: { ...H, "Content-Type": "application/vnd.android.package-archive" }, body: readFileSync(APK) },
    );
    if (!up.ok) {
      log("APK 上传失败: " + up.status + " " + (await up.text()).slice(0, 80));
      return false;
    }
    log("APK 上传成功");
  }

  // ③ issue 回复 + 关闭（幂等：检查我方评论是否已在场）
  for (const n of [42, 43, 44]) {
    try {
      const cs = await (await fetch(`https://api.github.com${BASE}/issues/${n}/comments?per_page=50`, { headers: H })).json().catch(() => []);
      const replied = (cs ?? []).some((c: any) => String(c.body ?? "").includes("v4.5.0 已发布"));
      if (replied) {
        log(`issue#${n} 已回复过，跳过`);
      } else {
        const rc = await fetch(`https://api.github.com${BASE}/issues/${n}/comments`, {
          method: "POST",
          headers: { ...H, "Content-Type": "application/json" },
          body: JSON.stringify({ body: REPLY_42_44 }),
        });
        if (rc.ok) log(`issue#${n} 回复成功`);
        else log(`issue#${n} 回复失败: ` + rc.status + " " + (await rc.text()).slice(0, 60));
      }
      const patch = await fetch(`https://api.github.com${BASE}/issues/${n}`, {
        method: "PATCH",
        headers: { ...H, "Content-Type": "application/json" },
        body: JSON.stringify({ state: "closed", state_reason: "completed" }),
      });
      if (patch.ok) log(`issue#${n} 已关闭`);
    } catch (e) {
      log(`issue#${n} 异常: ${e}`);
    }
  }
  return true;
}

/** 主循环：每 25s 探一次窗口，最长 40 分钟 */
async function main() {
  const deadline = Date.now() + 40 * 60 * 1000;
  let attempt = 0;
  while (Date.now() < deadline) {
    attempt++;
    try {
      if (await tryPublish()) {
        log("✅ 全部完成（第 " + attempt + " 次尝试）");
        process.exit(0);
      }
    } catch (e) {
      log("尝试异常: " + e);
    }
    if (attempt % 8 === 0) log(`已尝试 ${attempt} 次，窗口未开，继续等待…`);
    await sleep(25000);
  }
  log("❌ 40 分钟内窗口未开，退出（可重跑，幂等）");
  process.exit(1);
}

main();
