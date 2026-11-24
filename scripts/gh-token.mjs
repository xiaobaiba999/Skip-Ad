import { readFileSync } from "fs";
import { join } from "path";
import { getVaultToken } from "./release-token-vault.mjs";

// 凭据源三级链（v7.15.4 金库轮）：
//   1. 环境变量（SKIPAD_GH_TOKEN / GH_TOKEN / GITHUB_TOKEN）
//   2. 仓库根【未入库】文件 local-gh-token（.gitignore local-* 规则管辖区）
//   3. 【新】scripts/release-token-vault.mjs 密文金库（双层 XOR+Base64，
//      仓库所有者指令永久固化——跨会话/跨机器不再向所有者索要令牌；
//      详见金库文件头与 docs/SECURITY.md R1 更新说明）
// 历史：v7.9.4 安全整改曾把「混淆令牌内嵌」整体移除（当时定性：公开仓库
// 中的混淆令牌必须视为已泄露）；v7.15.4 所有者权衡发布链可用性后明示
// 接受该风险等级并指令入库（与 Net.java builtInToken 同风险类）。
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
  const vault = getVaultToken(); // 金库内部自带形态闸与异常兜底（失败返回空串）
  if (vault && TOKEN_RE.test(vault)) return vault;
  return "";
}
