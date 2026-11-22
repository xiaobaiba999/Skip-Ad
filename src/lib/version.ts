/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.9";
export const VERSION_CODE = 79900; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.9.9→79900；82300 历史用户升级路径见 docs/CHANGELOG.md v7.9.8 节）
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
    version: "7.9.9",
    code: 79900,
    date: "2026-11-24",
    tagline: "网络链路热修复：更新检查国内可达（jsDelivr 备用链）+ 日志上传超时阶段化分类",
    notes: [
      "检查更新「网络连接错误」根治（小米 15 Pro 真机反馈）：raw 主源国内被阻断时自动切换 jsDelivr 四接入点备用链（cdn/fastly/gcore/testingcf——订阅通道同族实战链）；GitHub Pages 死源移除（未启用恒 404 且国内不可达）；整链 40s 预算封顶、每源至多一次、防劫持降级（篡改内容不采信也不放弃）",
      "日志上传「上传超时」阶段化修复：连接超时（请求几乎必然未送达——提示切换 Wi-Fi/移动网络）与读取超时（服务端可能已收到甚至已建 Issue——如实提示「结果暂时无法确认，重试将使用原批次，不会重复创建 Issue」）分家；TLS 网络干扰独立分类",
      "上传连接超时 15s→10s（阻断网络快速失败）；读取超时保持 45s（服务端建 Issue 尾延迟实测可超 20s，不盲目调短）；冻结批次/batchId 幂等/ALREADY_ACCEPTED 复用/自动限额 24h/3 全部零改动（全链回归钉死）",
      "发布链路强化：清单发布后自动 purge jsDelivr 缓存（热修复即时可达国内用户）；新增 network-link-verify 端到端验证工作流（清单五源核验 + 中转服务健康检查 + 真实上传 + 同 batchId 幂等不二建 + Issue 存在性核验与测试清理）",
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
