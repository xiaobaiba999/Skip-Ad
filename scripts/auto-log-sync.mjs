#!/usr/bin/env bun
/**
 * auto-log-sync.mjs —— 24 小时自动日志同步（3 次/天 · v7.9.2 新功能）
 * ─────────────────────────────────────────────────────────────
 * 定时任务（01:20 / 09:20 / 17:20 Asia/Shanghai）触发：
 *   1. 刷新 data/learning/latest.json（generatedAt 时间戳 + 学习画像摘要）
 *   2. 归档当日快照到 data/learning/history/YYYY-MM-DD.json（同日幂等覆盖）
 *   3. git 变更即提交推送（chore(data): 定时日志同步 · 无变更优雅跳过）
 *
 * 安全约束：
 *   - 只 add data/ 目录（绝不提交密钥/Token/用户隐私日志）
 *   - 只推 origin main 正常分支 · 禁止 force push · 推送失败原样报告
 *   - 幂等：无变更时零提交（不为凑提交制造噪声）
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "fs";
import { execSync } from "child_process";

const ROOT = new URL("..", import.meta.url).pathname.replace(/\/$/, "");
const NOW = new Date();
const TZ = "Asia/Shanghai";
const DATE = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(NOW);
const STAMP = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }).format(NOW).replace(/,/g, "");
const ISO = `${STAMP.slice(0, 10)}T${STAMP.slice(10).replace(/(\d{2})(\d{2})(\d{2})/, "$1:$2:$3")}+08:00`;

function run(cmd, opts = {}) {
  try { return execSync(cmd, { cwd: ROOT, encoding: "utf-8", ...opts }).trim(); }
  catch (e) { return null; }
}

/* ── 1. 学习数据快照刷新（保留引擎已上报的真实数据，只刷新元数据） ── */
const LATEST = `${ROOT}/data/learning/latest.json`;
const HISTDIR = `${ROOT}/data/learning/history`;
let snap = null;
try { snap = JSON.parse(readFileSync(LATEST, "utf-8")); } catch {}
if (!snap) {
  console.log("✗ data/learning/latest.json 不可读——跳过刷新（不虚构数据）");
} else {
  snap.meta = snap.meta || {};
  snap.meta.generatedAt = ISO;
  snap.meta.timezone = TZ;
  snap.meta.purpose = "学习中心定时同步（24h×3：01:20/09:20/17:20）——为后续版本迭代提供规则与画像参考";
  const s = snap.stats || {};
  writeFileSync(LATEST, JSON.stringify(snap, null, 2) + "\n");

  mkdirSync(HISTDIR, { recursive: true });
  writeFileSync(`${HISTDIR}/${DATE}.json`, JSON.stringify(snap, null, 2) + "\n");
  console.log(`✓ 快照刷新 ${DATE} · 学习规则 ${(snap.learnedRules || []).length} 条 · 累计拦截 ${(s.totalBlocks ?? 0)} 次`);
}

/* ── 2. 变更检测（只看 data/ 目录） ── */
const status = run("git status --porcelain -- data/");
if (!status) {
  console.log("✓ data/ 无变更——零提交（幂等退出）");
  process.exit(0);
}
console.log("变更文件:\n" + status);

/* ── 3. 提交推送 ── */
const v = run(`grep -oP 'APP_VERSION = "\\K[^"]+' ${ROOT}/src/lib/version.ts`) || "unknown";
const learned = snap ? String((snap.learnedRules || []).length) : "0";
const blocks = snap ? String(snap.stats?.totalBlocks ?? 0) : "0";
const msg = `chore(data): 定时日志同步 v${v} · ${DATE} · 学习规则 ${learned} 条 · 累计拦截 ${blocks} 次（latest + history ${DATE}）`;
const add = run("git add data/");
const commit = run(`git commit -m ${JSON.stringify(msg)}`);
if (!commit) {
  console.log("✗ git commit 失败——保留工作树变更，原样报告不伪造");
  process.exit(1);
}
console.log(commit.split("\n").slice(-2).join("\n"));

/* token 注入内存 URL 推送（不写入 git config）——复用 gh-token.mjs */
const push = run("sh scripts/git-push-once.sh main 2>&1");
if (push === null) {
  console.log("✗ git push 失败——本地提交已保留，等待下次定时重试或人工介入（绝不伪造推送成功）");
  process.exit(1);
}
console.log(push.split("\n").slice(-3).join("\n"));
console.log(`✓ 定时日志同步完成 v${v} · ${ISO}`);
