import { getGhToken } from "../scripts/gh-token.mjs";

/** 输出令牌供 git 凭据助手使用（令牌不以明文出现在任何命令行参数中）
 *  v7.15.4：令牌源切换为 gh-token.mjs 三级链（env → local-gh-token →
 *  release-token-vault 密文金库）——git 推送链不再依赖 Web 端凭据模块
 *  src/lib/credentials.ts（该模块保留给 API Route 服务端，且明确不含金库
 *  回退：发布令牌绝不进 Web 打包面）。 */
const t = getGhToken();
if (!t) {
  process.exit(1);
}
console.log(t);
