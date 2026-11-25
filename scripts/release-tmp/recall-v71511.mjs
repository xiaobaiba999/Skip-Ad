#!/usr/bin/env bun
/**
 * v7.15.11 召回脚本（用户指令：召回删除 15.11 版本）。
 * - 删除 GitHub Release（tag v7.15.11 对应——发布时记为 #457581752）
 * - 删除远端 tag refs/tags/v7.15.11
 * - 幂等：Release/tag 已不存在则报告并正常退出
 * - 不触碰其他任何 Release/tag（升级源完好性对照铁律）
 * 令牌链与 publish 脚本同源（gh-token.mjs 三级链）。
 */
import { getGhToken } from "../gh-token.mjs";

const TOKEN = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const TAG = "v7.15.11";

if (!TOKEN) {
  console.error("✗ 缺少 GitHub Token");
  process.exit(1);
}
const H = {
  "Authorization": `token ${TOKEN}`,
  "Accept": "application/vnd.github+json",
  "User-Agent": "skip-ad-recall",
};

async function gh(path, init) {
  return fetch(`https://api.github.com${path}`, {
    ...init,
    headers: { ...H, ...(init && init.headers) },
  });
}

// 1) 按 tag 查 Release
const relRes = await gh(`/repos/${REPO}/releases/tags/${TAG}`);
if (relRes.status === 404) {
  console.log("· Release（tag " + TAG + "）已不存在——幂等通过");
} else if (relRes.ok) {
  const rel = await relRes.json();
  console.log("· 找到 Release #" + rel.id + " 「" + (rel.name || "") + "」 tag=" + rel.tag_name);
  const del = await gh(`/repos/${REPO}/releases/${rel.id}`, { method: "DELETE" });
  if (del.status === 204) {
    console.log("✓ Release #" + rel.id + " 已删除");
  } else {
    console.error("✗ Release 删除失败 HTTP " + del.status);
    process.exit(1);
  }
} else {
  console.error("✗ 查询 Release 失败 HTTP " + relRes.status);
  process.exit(1);
}

// 2) 删除远端 tag
const tagDel = await gh(`/repos/${REPO}/git/refs/tags/${TAG}`, { method: "DELETE" });
if (tagDel.status === 204) {
  console.log("✓ 远端 tag " + TAG + " 已删除");
} else if (tagDel.status === 422 || tagDel.status === 404) {
  console.log("· 远端 tag " + TAG + " 已不存在——幂等通过");
} else {
  console.error("✗ 远端 tag 删除失败 HTTP " + tagDel.status);
  process.exit(1);
}

// 3) 核验：按 tag 查 Release 应 404
const verify = await gh(`/repos/${REPO}/releases/tags/${TAG}`);
console.log(verify.status === 404
  ? "✓ 召回核验通过（" + TAG + " Release 视角 404）"
  : "⚠ 召回核验异常 HTTP " + verify.status);
