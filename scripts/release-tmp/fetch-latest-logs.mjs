#!/usr/bin/env bun
/**
 * 拉取 Skip-Ad-Logs 仓库最新日志 issue（v7.15.13 轮之后新增的 #116+）。
 * 下载 issue 标题/正文/评论中的日志文本，存 tool-results/log-arch-<n>.md
 * 令牌链与 publish 脚本同源（../gh-token.mjs 三级链）。
 */
import { getGhToken } from "../gh-token.mjs";
import { writeFileSync, mkdirSync, existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const TOKEN = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const OUT_DIR = "/home/z/skip-ad-repo/tool-results";
mkdirSync(OUT_DIR, { recursive: true });

if (!TOKEN) {
  console.error("✗ 缺少 GitHub Token");
  process.exit(1);
}
const H = {
  "Authorization": `token ${TOKEN}`,
  "Accept": "application/vnd.github+json",
  "User-Agent": "skip-ad-log-fetch",
};

async function gh(path) {
  const r = await fetch(`https://api.github.com${path}`, { headers: H });
  if (!r.ok) {
    console.error(`✗ ${path} → ${r.status}`);
    return null;
  }
  return r.json();
}

// 1. 列出全部 open+closed issues（含最新）
const issues = await gh(`/repos/${REPO}/issues?state=all&per_page=100&sort=created&direction=desc`);
if (!issues) process.exit(1);
const logIssues = issues.filter(i => !i.pull_request);
console.log(`共 ${logIssues.length} 个日志 issue，最新编号 #${logIssues[0]?.number}（${logIssues[0]?.created_at}）`);

// 2. 找出本地已存档的最大编号
let maxLocal = 0;
for (const i of logIssues) {
  const f = join(OUT_DIR, `log-arch-${i.number}.md`);
  if (existsSync(f)) maxLocal = Math.max(maxLocal, i.number);
}
console.log(`本地已存档至 #${maxLocal}`);

// 3. 下载新 issue 正文 + 评论
let fetched = 0;
for (const i of logIssues) {
  if (i.number <= maxLocal) continue;
  const f = join(OUT_DIR, `log-arch-${i.number}.md`);
  let body = `# Issue #${i.number} · ${i.title}\n> ${i.created_at} · state=${i.state}\n\n## 正文\n${i.body || "(空)"}\n`;
  const comments = await gh(`/repos/${REPO}/issues/${i.number}/comments?per_page=100`);
  if (comments && comments.length) {
    body += `\n## 评论（${comments.length}）\n`;
    for (const c of comments) body += `\n### ${c.user?.login} @ ${c.created_at}\n${c.body || ""}\n`;
  }
  writeFileSync(f, body);
  console.log(`✓ #${i.number} 已存档（${body.length} 字节）`);
  fetched++;
}
console.log(fetched ? `完成：新拉取 ${fetched} 个` : "无新增 issue");
