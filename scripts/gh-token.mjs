import { readFileSync } from "fs";
import { join } from "path";

// v7.9.4 安全整改：凭据源 = 环境变量（SKIPAD_GH_TOKEN / GH_TOKEN / GITHUB_TOKEN）
// → 仓库根【未入库】文件 local-gh-token（.gitignore 的 local-* 规则放行收集）。
// 原实现从 src/lib/credentials.ts 解「双层 XOR + Base64」混淆令牌——公开仓库
// 中的混淆令牌必须视为已泄露（详见 docs/SECURITY.md），已随本轮整改移除。
const TOKEN_RE = /^[A-Za-z0-9_]{20,}$/;

function readTokenFile(p) {
  try {
    const t = readFileSync(p, "utf-8").trim();
    return t.length > 0 ? t : null;
  } catch {
    return null;
  }
}

export function getGhToken() {
  const env = process.env.SKIPAD_GH_TOKEN || process.env.GH_TOKEN || process.env.GITHUB_TOKEN;
  if (env && TOKEN_RE.test(env)) return env;
  const local = readTokenFile(join(import.meta.dir, "..", "local-gh-token"));
  if (local && TOKEN_RE.test(local)) return local;
  return "";
}
