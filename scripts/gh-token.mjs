import { readFileSync } from "fs";
import { join } from "path";

// 凭证源优先级：本仓库 src/lib/credentials.ts → 沙箱主项目 /home/z/my-project/src/lib/credentials.ts
// （历史会话中仓库曾位于主项目目录，两种布局都兼容）
function firstExisting(paths) {
  for (const p of paths) {
    try { readFileSync(p); return p; } catch {}
  }
  return paths[0];
}
const src = readFileSync(firstExisting([
  join(import.meta.dir, "../src/lib/credentials.ts"),
  "/home/z/my-project/src/lib/credentials.ts",
]), "utf-8");

const m = src.match(/const L2 = "([^"]+)"/);
const kbm = src.match(/const KEY_B = \[([^\]]+)\]/);
function xorWith(input, key) {
  let out = "";
  for (let i = 0; i < input.length; i++) out += String.fromCharCode(input.charCodeAt(i) ^ key.charCodeAt(i % key.length));
  return out;
}
export function getGhToken() {
  const kb = kbm[1].split(",").map((c) => String.fromCharCode(parseInt(c.trim(), 10))).join("");
  const layer1 = xorWith(Buffer.from(m[1], "base64").toString("utf-8"), kb);
  return xorWith(Buffer.from(layer1, "base64").toString("utf-8"), "SkipAd::2026::obf::k1");
}
