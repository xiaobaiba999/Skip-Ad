#!/usr/bin/env bun
/**
 * 内置令牌轮换工具（v7.15.4 起备用 · 令牌已死后的标准复活路径）。
 *
 * 背景：v7.15.3 轮换进去的内置令牌（与发布令牌同一把）实测 401 Bad
 * credentials——问题反馈/手动日志上传/8h 自动上传随之再次全灭（检查更新
 * 不受影响：v7.15.3 起 API 匿名优先）。本工具把新令牌重编码为 Net.java
 * L2 密文（双层 XOR+Base64，与 builtInToken() 严格互逆）并原位替换。
 *
 * 输入优先级（全程不打印令牌本体，只打印来源与指纹前 4 字符）：
 *   1. local-app-token   —— 专用「应用内置」令牌（推荐：fine-grained PAT，
 *                           仅 Skip-Ad 仓库 issues:write · 有有效期）
 *   2. local-gh-token    —— 发布令牌兜底（与 v7.15.3 同源的共享模式）
 *   3. stdin             —— 管道传入
 *
 * 硬闸：
 *   · 令牌形态校验（ghp_ / github_pat_ 前缀 + [A-Za-z0-9_] 组成）
 *   · 轮换前 API /user 实测必须 200（绝不把死令牌轮进去）
 *   · 轮换后从 Net.java 回读解码必须与输入一致（互逆性证明）
 *
 * 用法：bun run scripts/rotate-token.mjs [--stdin]
 */
import { readFileSync, writeFileSync } from "fs";

const NET_PATH = "/home/z/skip-ad-repo/android-app/src/com/zai/adshield/Net.java";
const KB = "KipShield";
const K1 = "SkipAd::2026::obf::k1";

/* 与 Net.java xorWith 同语义（按字符码异或，binary 通道） */
const xorWith = (input, key) =>
  Buffer.from(
    [...input].map((c, i) => c.charCodeAt(0) ^ key.charCodeAt(i % key.length)),
  ).toString("binary");

/* 编码（builtInToken 的逆）：token → xor K1 → base64 → xor KB → base64 = L2 */
function encodeL2(token) {
  const layer1 = Buffer.from(xorWith(token, K1), "binary").toString("base64");
  return Buffer.from(xorWith(layer1, KB), "binary").toString("base64");
}

/* 解码（与 Net.java builtInToken 严格同路径——回读校验用） */
function decodeL2(l2) {
  const layer1 = xorWith(Buffer.from(l2, "base64").toString("binary"), KB);
  return xorWith(Buffer.from(layer1, "base64").toString("binary"), K1);
}

/* ── 取令牌（不打印本体） ── */
let token = "";
let source = "";
if (process.argv.includes("--stdin")) {
  token = readFileSync(0, "utf-8").trim().split(/\r?\n/)[0] || "";
  source = "stdin";
} else {
  for (const [f, name] of [
    ["/home/z/skip-ad-repo/local-app-token", "local-app-token（专用应用令牌）"],
    ["/home/z/skip-ad-repo/local-gh-token", "local-gh-token（发布令牌兜底）"],
  ]) {
    try {
      const v = readFileSync(f, "utf-8").trim();
      if (v) { token = v; source = name; break; }
    } catch (e) { /* 下一来源 */ }
  }
}
if (!token) {
  console.error("✗ 未取得令牌：local-app-token / local-gh-token 均不存在且未走 --stdin");
  process.exit(1);
}
if (!/^(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})$/.test(token)) {
  console.error(`✗ 令牌形态不符（前缀 ${token.slice(0, 4)}…）——仅接受 ghp_ / github_pat_`);
  process.exit(1);
}
console.log(`[输入] 来源：${source} · ${token.slice(0, 4)}…（${token.length} 字符，本体不打印）`);

/* ── 硬闸 1：轮换前 API 实测必须活着 ── */
const probe = await fetch("https://api.github.com/user", {
  headers: { "Authorization": `Bearer ${token}`, "User-Agent": "skip-ad-rotate" },
});
console.log(`[硬闸1] API /user → HTTP ${probe.status}`);
if (probe.status !== 200) {
  console.error("✗ 拒绝轮换：令牌不可用（绝不把死令牌轮进去）——请先换新令牌");
  process.exit(1);
}
const who = await probe.json();
console.log(`[硬闸1] 身份：${who.login}（scope 生效）`);

/* ── 硬闸 2：Net.java L2 行在场且唯一 ── */
const src = readFileSync(NET_PATH, "utf-8");
const lines = src.split("\n");
const idx = lines.findIndex((l) => /^\s*private static final String L2 = "/.test(l));
if (idx < 0) {
  console.error("✗ Net.java 中未找到 L2 声明行（结构变更？请人工核对");
  process.exit(1);
}

/* ── 轮换：原位替换 L2 密文 ── */
const newL2 = encodeL2(token);
lines[idx] = lines[idx].replace(
  /private static final String L2 = "[^"]*"/,
  `private static final String L2 = "${newL2}"`,
);
writeFileSync(NET_PATH, lines.join("\n"));

/* ── 硬闸 3：回读互逆校验（从磁盘重读，防止写坏） ── */
const reread = readFileSync(NET_PATH, "utf-8");
const l2Now = /private static final String L2 = "([^"]*)"/.exec(reread)?.[1] || "";
const decoded = decodeL2(l2Now);
if (decoded !== token) {
  console.error("✗ 回读解码与输入不一致（互逆性破坏）——请立即人工核查 Net.java");
  process.exit(1);
}
console.log(`[硬闸3] Net.java 回读解码一致 ✓（L2 密文 ${l2Now.length} 字符）`);
console.log(`\n✅ 轮换完成——后续：./build.sh 85400 7.15.4 → 重算 SHA-256 → 更新 update/latest.json → 提交推送发版`);
console.log(`\n[提示] 若新令牌同时兼任发布令牌，请同步金库：bun scripts/release-token-vault.mjs --encode`);
console.log(`[提示] 发布前强制核验：bun scripts/check-release-readiness.mjs（金库漏写/写错/死令牌/权限四重硬闸）`);
