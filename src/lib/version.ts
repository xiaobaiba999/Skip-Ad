/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.7";
export const VERSION_CODE = 79700; // major*10000 + minor*1000 + patch*100，随版本单调递增
export const GITHUB_REPO = "xiaobaiba999/Skip-Ad";
export const GITHUB_API = `https://api.github.com/repos/${GITHUB_REPO}`;
export const GITHUB_HOME = `https://github.com/${GITHUB_REPO}`;
export const RELEASES_PAGE = `https://github.com/${GITHUB_REPO}/releases`;
export const ISSUES_PAGE = `https://github.com/${GITHUB_REPO}/issues`;

export interface VersionEntry {
  version: string;
  code: number;
  date: string;
  tagline: string;
  notes: string[];
}

/** 「关于」页仅展示当前安装版本（硬约束：数组只保留最新 1 条，
 *  旧版本内容归档在 GitHub Releases 与 git 历史） */
export const VERSION_HISTORY: VersionEntry[] = [
  {
    version: "7.9.7",
    code: 79700,
    date: "2026-11-21",
    tagline: "PAT 移除安全轮：APK 零 GitHub 凭据 + 自动日志上传授权（默认关）+ 手动日志本地导出——旧直发通道整体下线，新中转通道（v8.0.0）就绪前结构性关闭",
    notes: [
      "P0 安全（内置令牌移除）：Net.java 内嵌混淆 PAT（双层 XOR+Base64，与 v7.9.4 已移除的 Web 端 credentials.ts 同源密文）整体移除——混淆不等于加密，公开分发 APK 内任何令牌形态必须视为已泄露凭据；撤销动作仍待仓库所有者执行（docs/SECURITY.md R1）",
      "自动上传旧通道下线：maybeAutoUploadLog 不再经 KeepAliveReceiver 心跳直发 GitHub Issue；新通道双门结构（用户授权默认关 + Net.LOG_RELAY_URL 中转服务地址为空）任一未过即零行为——本版本结构性不自动上传，绝不回退直连 GitHub",
      "自动日志上传授权（默认关）：新增独立开关（AdSkipService.KEY_AUTO_LOG_UPLOAD）+ 设置页 toggle + 状态行——与无障碍授权严格分离，授权≠上传；上线后仍受服务端 24 小时滑窗（最多 3 次）与 batchId 幂等约束（v8.0.0）",
      "手动日志本地导出：MainActivity 新桥 exportLog（应用外部私有目录 logs/，无新权限；Java 侧 maskSensitive 二次脱敏保持双层）+ shareLogText 系统分享面板；netPostIssue 桥移除——客户端零 GitHub 写凭据",
      "检查更新匿名化：latestRelease 改匿名请求（Authorization 头不再发送；v3.3.0 重试 + /releases 列表回退链路不变）；新增 newIssuePrefillUrl 浏览器预填链接（用户登录态自助提交，零令牌）",
      "本轮零识别/安全门改动：不新增关键词、不扩大点击区域、不降 SafetyGuard、不加轮询、不动任务状态机；31 套件 6653 项 JVM 断言全绿（修改前后各跑一轮零回退）· secret-scan/app.js 符号闸/version-sync 全绿",
    ],
  },
];

/** 比较两个 semver（支持 v 前缀）：a<b → -1，a>b → 1，相等 → 0 */
export function compareVersion(a: string, b: string): number {
  const pa = a.replace(/^v/, "").split(".").map((n) => parseInt(n, 10) || 0);
  const pb = b.replace(/^v/, "").split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d > 0 ? 1 : -1;
  }
  return 0;
}
