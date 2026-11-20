/**
 * ─────────────────────────────────────────────────────────────
 *  服务端凭据（SERVER-ONLY）· v7.9.4 安全整改
 * ─────────────────────────────────────────────────────────────
 *  · 本模块只能被 API Route / 服务端代码导入，严禁出现在任何
 *    client 组件中——令牌永远不进入浏览器。
 *
 *  v7.9.4 整改（原实现为「双层 XOR + Base64」混淆内嵌，已随
 *  FalsePositiveScenarioTest 同轮安全审计判定为不安全——混淆
 *    不等于加密，公开仓库中的混淆令牌必须视为已泄露）：
 *    1. 令牌从仓库代码中【完全移除】——不再内嵌任何形态的令牌
 *       （明文/混淆/拆分/加密硬编码一律禁止）；
 *    2. 凭据源优先级：环境变量 SKIPAD_GH_TOKEN / GH_TOKEN /
 *       GITHUB_TOKEN（CI Secret 与本机安全配置首选）→ 仓库根
 *       【未入库】文件 local-gh-token（.gitignore 的 local-*
 *       规则放行收集，模式 600）；
 *    3. 原 v2.0.0 起内嵌于 git 历史的令牌视为已暴露，标记
 *       【需撤销】——由仓库所有者在 GitHub Settings → Developer
 *       settings → Personal access tokens 中吊销（详见
 *       docs/SECURITY.md）。
 *  · 解密仅在服务端内存中发生，不落日志、不回传前端。
 */
import { readFileSync } from "fs";
import { join } from "path";

/** 令牌格式校验（classic ghp_* / fine-grained github_pat_* / 安装令牌统一宽匹配）。 */
const TOKEN_RE = /^[A-Za-z0-9_]{20,}$/;

function readTokenFile(p: string): string | null {
  try {
    const t = readFileSync(p, "utf-8").trim();
    return t.length > 0 ? t : null;
  } catch {
    return null;
  }
}

/** 凭据候选文件：仓库根 local-gh-token（未入库·模式 600）。 */
function candidateFiles(): string[] {
  const list: string[] = [join(process.cwd(), "local-gh-token")];
  try {
    // scripts/ 下的工具脚本（bun import.meta.dir 可用时的同仓根解析）
    const dir = (import.meta as { dir?: string }).dir;
    if (dir) list.push(join(dir, "..", "local-gh-token"));
  } catch {
    /* 非 bun 运行时忽略 */
  }
  return list;
}

/** 解出 GitHub Token（仅服务端调用；失败返回空串——不抛错不落日志）。 */
export function getGithubToken(): string {
  try {
    const env = process.env.SKIPAD_GH_TOKEN
      || process.env.GH_TOKEN
      || process.env.GITHUB_TOKEN;
    if (env && TOKEN_RE.test(env)) return env;
    for (const p of candidateFiles()) {
      const t = readTokenFile(p);
      if (t && TOKEN_RE.test(t)) return t;
    }
  } catch {
    /* 兜底：返回空串 */
  }
  return "";
}

/** 令牌是否就绪 */
export function hasGithubToken(): boolean {
  return getGithubToken().length > 0;
}
