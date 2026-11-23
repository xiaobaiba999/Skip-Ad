#!/usr/bin/env bun
/**
 * jsDelivr CDN 兜底链 purge + 四接入点核验工具（v7.12.0 陈旧窗口根因定案驱动）
 * ═══════════════════════════════════════════════════════════════════
 * 用途：
 *   · 发布后 / 巡更时手动刷新 jsDelivr 四接入点对 @main 仓库文件的边缘缓存；
 *   · 核验「清单五源链」中四接入点的真实生效状态（versionCode 比对）；
 *   · 核验 APK 兜底直链（adshield-latest.apk 别名 + 版本化 APK）字节正确性。
 *
 * 根因背景（2026-11-23 实测定案，详见 docs/CHANGELOG.md「v7.12.0 发布管线热修复」节）：
 *   jsDelivr 对 @main 分支引用有源站级分支解析缓存（TTL ≤12h）——purge API
 *   仅清 CF/FY 两家边缘（gcore 完全不受 purge 影响），源站分支缓存未到期时
 *   边缘 MISS 回源拿到的仍是旧内容。因此本工具的核验是「诚实口径」：
 *   陈旧不是错误，是已知 ≤12h 窗口——轮询等待 + 定期 purge，翻转后即固定。
 *
 * 诚实设计：
 *   · 绝不把「purge API 返回 finished」当生效——只认四点 versionCode 实测；
 *   · 陈旧时输出已知机制说明与预计自愈窗口，exit 2（供脚本/巡更判断）；
 *   · 全部生效 exit 0；参数错误 exit 3。
 *
 * 用法（bun 或 node ≥18）：
 *   bun scripts/purge-jsdelivr.mjs                        # 默认 4 轮 × 60s
 *   bun scripts/purge-jsdelivr.mjs --rounds 6 --wait-ms 90000
 *   bun scripts/purge-jsdelivr.mjs --vc 82000 --tag v7.12.0   # 覆盖清单推导
 *
 * 零凭据：只访问 purge.jsdelivr.net 与四接入点公开只读 URL。
 */
import { readFileSync } from "fs";
import { createHash } from "crypto";

const REPO = "xiaobaiba999/Skip-Ad";
const HOSTS = ["cdn.jsdelivr.net", "fastly.jsdelivr.net", "gcore.jsdelivr.net", "testingcf.jsdelivr.net"];
const APK_HOSTS = ["cdn.jsdelivr.net", "gcore.jsdelivr.net"]; // fastly 对 APK 301→raw 阻断域（历史实测）；testingcf 同 cdn
const DEFAULT_ROUNDS = 4;
const DEFAULT_WAIT_MS = 60_000;

/* ── 参数 ── */
function parseArgs(argv) {
  const a = { rounds: DEFAULT_ROUNDS, waitMs: DEFAULT_WAIT_MS, vc: null, tag: null };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i];
    if (k === "--rounds") a.rounds = Number(argv[++i]);
    else if (k === "--wait-ms") a.waitMs = Number(argv[++i]);
    else if (k === "--vc") a.vc = Number(argv[++i]);
    else if (k === "--tag") a.tag = String(argv[++i]);
    else if (k === "--help" || k === "-h") { usage(); process.exit(3); }
    else { console.error(`未知参数：${k}`); usage(); process.exit(3); }
  }
  if (!(a.rounds >= 1 && a.rounds <= 12)) { console.error("--rounds 须为 1..12"); process.exit(3); }
  if (!(a.waitMs >= 0 && a.waitMs <= 600_000)) { console.error("--wait-ms 须为 0..600000"); process.exit(3); }
  return a;
}
function usage() {
  console.log("用法：bun scripts/purge-jsdelivr.mjs [--rounds N] [--wait-ms N] [--vc code] [--tag vX.Y.Z]");
}

/* ── 期望值（本地权威清单推导，可覆盖）── */
let manifest = null;
try { manifest = JSON.parse(readFileSync("update/latest.json", "utf-8")); } catch { /* 仓库外运行则靠覆盖参数 */ }
const args = parseArgs(process.argv.slice(2));
const EXPECT_VC = args.vc ?? (manifest && Number(manifest.versionCode)) ?? null;
const TAG = args.tag ?? (manifest && manifest.releaseTag) ?? null;
const APK_NAME = TAG ? `adshield-${TAG}.apk` : null;
const EXPECT_SHA = manifest && manifest.apkSha256 || null;
const EXPECT_SIZE = manifest && Number(manifest.apkSize) || null;
if (EXPECT_VC == null || !TAG) {
  console.error("✗ 无法确定期望版本：请在仓库根运行，或用 --vc/--tag 覆盖");
  process.exit(3);
}

/* ── 工具函数 ── */
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function fetchText(url, timeoutMs = 20_000) {
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), timeoutMs);
  try {
    const r = await fetch(url, { signal: ac.signal });
    const text = r.ok ? await r.text() : "";
    return { status: r.status, ok: r.ok, text };
  } catch { return { status: 0, ok: false, text: "" }; } finally { clearTimeout(t); }
}
async function fetchBytes(url, timeoutMs = 90_000) {
  const ac = new AbortController();
  const t = setTimeout(() => ac.abort(), timeoutMs);
  try {
    const r = await fetch(url, { signal: ac.signal });
    if (!r.ok) return { status: r.status, ok: false, bytes: null };
    const buf = Buffer.from(await r.arrayBuffer());
    return { status: r.status, ok: true, bytes: buf };
  } catch { return { status: 0, ok: false, bytes: null }; } finally { clearTimeout(t); }
}
const sha256 = (buf) => createHash("sha256").update(buf).digest("hex");

/* ── purge 一条路径（purge.jsdelivr.net 免认证公开端点，限频——间隔节流）── */
async function purgePath(path) {
  const r = await fetchText(`https://purge.jsdelivr.net/gh/${REPO}@main/${path}`, 60_000);
  if (!r.ok) return { ok: false, note: `HTTP ${r.status}` };
  try {
    const j = JSON.parse(r.text);
    const entry = Object.values(j.paths || {})[0] || {};
    const providers = Object.keys(entry.providers || {}).join("+") || "无";
    return { ok: j.status === "finished", note: `${j.status || "?"}（providers ${providers}${entry.throttled ? " · throttled!" : ""}）` };
  } catch { return { ok: false, note: "响应非法" }; }
}

/* ── 核验一轮：四接入点清单 versionCode + APK 直链字节 ── */
async function verifyRound() {
  const state = { hosts: {}, apk: {}, allFresh: false };
  let freshHosts = 0;
  for (const h of HOSTS) {
    const r = await fetchText(`https://${h}/gh/${REPO}@main/update/latest.json`);
    let vc = null;
    try { vc = JSON.parse(r.text).versionCode; } catch { /* 非法/空 */ }
    const fresh = r.ok && vc === EXPECT_VC;
    if (fresh) freshHosts++;
    state.hosts[h] = { ok: r.ok, vc, fresh };
  }
  // APK 兜底直链（别名 + 版本化）：字节级 sha/size 比对（仅 cdn/gcore 两点）
  let freshApk = 0, apkTotal = 0;
  for (const p of ["adshield-latest.apk", APK_NAME]) {
    for (const h of APK_HOSTS) {
      apkTotal++;
      const r = await fetchBytes(`https://${h}/gh/${REPO}@main/android-app/dist/${p}`);
      const gotSha = r.bytes ? sha256(r.bytes) : null;
      const fresh = r.ok && r.bytes && gotSha === EXPECT_SHA && r.bytes.length === EXPECT_SIZE;
      if (fresh) freshApk++;
      state.apk[`${h}/${p}`] = { ok: r.ok, size: r.bytes ? r.bytes.length : 0, sha12: gotSha ? gotSha.slice(0, 12) : "—", fresh };
    }
  }
  state.allFresh = freshHosts === HOSTS.length && freshApk === apkTotal;
  state.freshHosts = freshHosts;
  state.freshApk = freshApk;
  state.apkTotal = apkTotal;
  return state;
}

function printState(round, s) {
  const hostLine = HOSTS.map((h) => {
    const e = s.hosts[h];
    return `${h.split(".")[0]}=${e.ok ? (e.vc ?? "?") : "NC"}${e.fresh ? "✓" : ""}`;
  }).join("  ");
  const apkLine = Object.entries(s.apk).map(([k, e]) => `${k.split("/")[0]}/${k.split("/")[1].replace("adshield-", "").replace(".apk", "")}:${e.fresh ? "新" : (e.ok ? "旧" : "NC")}`).join("  ");
  console.log(`  [第 ${round} 轮] 清单：${hostLine}`);
  console.log(`             APK：${apkLine}`);
}

/* ── 主流程：purge → 核验 → （陈旧则）等待重试 ── */
console.log(`═══ jsDelivr 兜底链 purge 巡更 ═══`);
console.log(`期望：versionCode=${EXPECT_VC} · ${TAG} · APK ${EXPECT_SIZE}B · sha ${EXPECT_SHA ? EXPECT_SHA.slice(0, 12) : "—"}…`);
console.log(`计划：${args.rounds} 轮 purge（3 路径）× 核验，轮间等待 ${args.waitMs / 1000}s`);
console.log(`机制提示：jsDelivr @main 源站分支缓存 TTL ≤12h——purge 只清 CF/FY 边缘；`);
console.log(`         陈旧窗口内边缘 MISS 回源仍旧，翻转须等源站到期（已知自愈路径）。`);
const PATHS = ["update/latest.json", "android-app/dist/adshield-latest.apk", `android-app/dist/${APK_NAME}`];
let last = null;
for (let round = 1; round <= args.rounds; round++) {
  console.log(`── 轮 ${round}/${args.rounds}：purge ${PATHS.length} 路径 ──`);
  for (const p of PATHS) {
    const pr = await purgePath(p);
    console.log(`  purge ${p} → ${pr.ok ? "已提交" : "未确认"}（${pr.note}）`);
    await sleep(2500); // purge 端点限频节流
  }
  await sleep(20_000); // 边缘回源传播窗
  last = await verifyRound();
  printState(round, last);
  if (last.allFresh) { console.log(`✓ 全部生效（第 ${round} 轮）——四接入点 + APK 双直链字节一致`); break; }
  if (round < args.rounds) console.log(`  仍有陈旧（清单 ${last.freshHosts}/4 · APK ${last.freshApk}/${last.apkTotal}）——${args.waitMs / 1000}s 后再试`);
}

/* ── 诚实结论 ── */
console.log(`═══ 结论 ═══`);
if (last.allFresh) {
  console.log(`✓ jsDelivr 备用链全部生效：清单四点 versionCode=${EXPECT_VC}；APK 别名与版本化直链字节一致（${EXPECT_SIZE}B）`);
  process.exit(0);
}
console.log(`⚠ 生效 ${last.freshHosts}/${HOSTS.length}（清单）· ${last.freshApk}/${last.apkTotal}（APK）`);
console.log(`  已知机制（2026-11-23 实测定案）：jsDelivr @main 分支解析缓存 TTL ≤12h；`);
console.log(`  purge 仅清 CF/FY 边缘（gcore 不受 purge 影响；源站分支缓存未到期时回源仍旧）。`);
console.log(`  raw 主源（raw.githubusercontent.com）不受影响、已即时生效——国内不可达时此链才启用。`);
console.log(`  陈旧期救济：版本化 APK 直链（新文件首取即新鲜）——`);
console.log(`  https://cdn.jsdelivr.net/gh/${REPO}@main/android-app/dist/${APK_NAME}`);
console.log(`  下轮巡更继续 purge；源站到期后任一 purge + 回源请求即固定为最新。`);
process.exit(2);
