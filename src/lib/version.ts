/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "8.2.0";
export const VERSION_CODE = 82000; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "8.2.0",
    code: 82000,
    date: "2026-11-22",
    tagline: "更新检查双通道：中转服务优先 + GitHub 直连回退——彻底解决「GitHub API 限流（匿名 60 次/小时/IP）看不到正式新版本」",
    notes: [
      "更新检查双通道（v8.2.0 真机限流根因修复）：新增通道 0「更新中转」GET /api/update/latest（与日志中转同源 Worker 的独立只读端点——服务端边缘缓存 5~10 分钟 + GitHub App 认证上游（独立配额池），凭据不足自动回退匿名+缓存，绝不要求扩权）；中转失败自动回退 GitHub 匿名直连（v8.0.3 语义逐字保持）——匿名配额耗尽时也能稳定发现正式新版本。Worker 端代码随仓交付（worker/ 目录，含 22 项单元 + 3 项 live 测试与一键部署指南；部署后通道 0 生效，部署前自动回退直连，行为=v8.0.3 零回退）",
      "错误五分类如实呈现：GITHUB_RATE_LIMITED / WORKER_UNAVAILABLE / NETWORK_ERROR / PARSE_ERROR / NO_NEW_VERSION——中转故障绝不误报成 GitHub 限流；双通道皆败时各自归因同时展示（「更新通道均失败：中转…；GitHub…」）；中转失败而直连成功时成功载荷附 workerError 供 UI 提示",
      "下载与安全闸零降低：APK 下载前 SHA-256 + 大小双重校验元数据经 HTTPS 获取、安装前流式逐字节比对不符即删、发布通道 host 白名单、签名兼容全部保持；本轮仅把校验判定提取为可测纯函数 verifyDigest（行为逐字一致）",
      "修复 v8.1.0 遗留：「版本记录」板块内容停留在 v8.0.3（本轮起随版本同步更新；浏览器预览回退版本号一并修正）",
      "测试：39 套件全绿（新增 UpdateChannelTest：双通道五分类真值表/中转载荷契约/版本比较/SHA-256 校验判定/发布通道白名单/镜像合成）+ Worker 更新接口 25 项测试（22 单元 AUTOMATED + 3 live REAL_API 模块级——真实 GitHub 上游取到 v8.1.0 官方 digest 并验证缓存生效）",
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
