#!/usr/bin/env bun
/**
 * purge-update-cdn.mjs —— jsDelivr @main 清单缓存主动清洗（发布链固化 + 事故即时修复）
 * ═════════════════════════════════════════════════════════════════════
 * 背景（2026-11-27 事故）：v8.0.8 发布后 update/latest.json 已在 main，但
 * cdn.jsdelivr.net / gcore.jsdelivr.net 对 @main 清单持续返回陈旧缓存
 * （17h+ 仍 87100）——jsDelivr 分支引用边缘缓存 TTL 远超官方口径的 12h；
 * 未主动 purge 时所有旧客户端一律看到「已是最新」。本脚本在发布链
 * 「清单推送 main 之后、验证之前」执行，也可独立用于事故修复。
 *
 * 事实（runbook）：
 *  · purge.jsdelivr.net 免认证公开端点，按路径限频（约 30 分钟/路径）——
 *    限频时返回 throttled:true 与 throttlingReset（秒）；脚本自动等到窗口
 *    重置后重试（上限 retryAfter + 120s）。
 *  · purge 对四接入点（cdn/fastly/gcore/testingcf）共享后端生效，但对
 *    gcore 接入点的生效性不稳定——purge 后必须逐域验证（本脚本内置）。
 *  · @tag 引用不可变、零分支缓存，无需 purge。
 *
 * 用法：
 *   bun scripts/purge-update-cdn.mjs [--skip-kilo] [--expect-versionCode 88100]
 */

const REPOS = ["xiaobaiba999/Skip-Ad", "xiaobaiba999/kilo"];
const JSDELIVR_HOSTS = [
  "cdn.jsdelivr.net",
  "gcore.jsdelivr.net",
  "fastly.jsdelivr.net",
  "testingcf.jsdelivr.net",
];

const args = process.argv.slice(2);
const skipKilo = args.includes("--skip-kilo");
const expectIdx = args.indexOf("--expect-versionCode");
const expectVersionCode =
  expectIdx >= 0 ? Number(args[expectIdx + 1]) : null;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function purgeOnce(repo) {
  const url = `https://purge.jsdelivr.net/gh/${repo}@main/update/latest.json`;
  const res = await fetch(url, { signal: AbortSignal.timeout(60_000) });
  const body = await res.json().catch(() => ({}));
  const entry = body?.paths?.[`/gh/${repo}@main/update/latest.json`];
  return { url, status: res.status, entry, ok: res.ok };
}

async function fetchManifestVersion(host, repo) {
  const url = `https://${host}/gh/${repo}@main/update/latest.json`;
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(20_000) });
    if (!res.ok) return { url, ok: false, error: `HTTP ${res.status}` };
    const json = await res.json();
    return {
      url,
      ok: true,
      versionCode: json.versionCode ?? null,
      versionName: json.versionName ?? null,
    };
  } catch (e) {
    return { url, ok: false, error: String(e?.message || e) };
  }
}

async function purgeWithRetry(repo) {
  const deadline = Date.now() + 30 * 60_000;
  for (;;) {
    const r = await purgeOnce(repo);
    if (r.status === 200 && r.entry && r.entry.throttled === false) {
      console.log(
        `  ✓ purge 生效（${repo}）providers=${JSON.stringify(r.entry.providers ?? {})}`,
      );
      return true;
    }
    if (r.entry && typeof r.entry.throttlingReset === "number") {
      const waitMs = Math.min(
        (r.entry.throttlingReset + 15) * 1000,
        deadline - Date.now(),
      );
      if (waitMs <= 0) break;
      console.log(
        `  ⏳ purge 限频（${repo}），${Math.round(waitMs / 1000)}s 后重试…`,
      );
      await sleep(waitMs);
      continue;
    }
    console.log(`  ⚠ purge 未确认（${repo}）：HTTP ${r.status} ${JSON.stringify(r.entry ?? {})}`);
    await sleep(30_000);
    if (Date.now() >= deadline) break;
  }
  console.log(`  ✗ purge 未在窗口内确认（${repo}）——继续逐域验证以实际生效为准`);
  return false;
}

console.log("═══ jsDelivr 更新清单缓存清洗 ═══");
const repos = skipKilo ? REPOS.slice(0, 1) : REPOS;
for (const repo of repos) {
  console.log(`· purge ${repo}`);
  await purgeWithRetry(repo);
}

console.log("\n═══ 逐域验证（@main 清单实际生效内容）═══");
let failures = 0;
for (const repo of repos) {
  for (const host of JSDELIVR_HOSTS) {
    const r = await fetchManifestVersion(host, repo);
    const stale =
      r.ok && expectVersionCode != null && r.versionCode !== expectVersionCode;
    const mark = !r.ok ? "⚠" : stale ? "✗" : "✓";
    if (!r.ok || stale) failures++;
    console.log(
      `  ${mark} ${host.padEnd(22)} ${repo.padEnd(22)} ${
        r.ok ? `${r.versionName} / versionCode ${r.versionCode}` : r.error
      }${stale ? `（期望 ${expectVersionCode}——陈旧）` : ""}`,
    );
  }
}
if (failures > 0) {
  console.log(
    `\n✗ ${failures} 域未达期望${expectVersionCode ? `（versionCode ${expectVersionCode}）` : "可达"}——失败域为陈旧缓存时：jsDelivr 分支缓存最长 12h 自然过期；@tag 清单（不可变键）已零缓存生效`,
  );
  process.exit(1);
}
console.log("\n✓ 全部域验证通过");
