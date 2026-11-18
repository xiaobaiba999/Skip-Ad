import { getGithubToken } from "../src/lib/credentials";

/** 输出内置令牌供 git 凭据助手使用（令牌不以明文出现在任何命令行参数中） */
const t = getGithubToken();
if (!t) {
  process.exit(1);
}
console.log(t);
