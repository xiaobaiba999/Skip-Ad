/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.8";
export const VERSION_CODE = 79800; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.9.8→79800；82300 历史用户升级路径见 docs/CHANGELOG.md v7.9.8 节）
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
    version: "7.9.8",
    code: 79800,
    date: "2026-11-23",
    tagline: "日志上传可靠性专项：冻结批次终结「第一次失败、第二次被服务端拒」+ 错误八类如实分类 + 反馈提示 24h 节流 + 导出下线 + 未命中结构化诊断",
    notes: [
      "日志上传根治「第一次失败、第二次被服务端拒」：上传批次冻结（同 batchId 必同 payload——服务端幂等必然命中复用原 Issue）+ 读超时 15s→45s，失败按真实原因八类如实提示（网络连接失败/上传超时/服务端拒绝/频率限制/格式异常/服务暂不可用——不再一律「稍后再试」）",
      "手动与自动上传额度分账：手动成功不再占用自动 24 小时 3 次限额；服务端限流 retryAfter 如实透传",
      "Bug 反馈提示升级 24h 节流：确认后 24h 内不再弹、超 24h 下次进入重新提醒、未确认不消耗资格；「去上传日志」仍只导航不代传",
      "引擎日志页移除「导出」按钮（采集/查看/一键上传全保留）；扫描未命中新增 SCAN_MISS 结构化诊断（无规则/规则未命中/视频容器静默四稳定失败码 + 设备码）——一次上传即可定位「为什么没跳过」",
    ],
  },];

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
