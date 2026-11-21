/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "8.0.3";
export const VERSION_CODE = 80300; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "8.0.3",
    code: 80300,
    date: "2026-11-28",
    tagline: "更新链路修复：GitHub API 限流（403/429）即时报明确错误不再浪费重试 + 下载完成文案如实描述（SHA-256 安装时校验）+ 关于页版本记录同步 v8.0.1→v8.0.3",
    notes: [
      "更新检查链路审计（用户指令：先完整追踪再最小修复）：入口=设置页手动「检查更新」按钮（v7.0.0 已移除自动横幅）；请求=GitHub Releases API（/releases/latest 主通道 + /releases?per_page=8 回退，均匿名、noRedirect、7s/12s 超时）；Draft Release 两层正确过滤（GitHub 匿名 API 不返回草稿 + 回退通道显式跳过 draft/prerelease）；tag_name 解析→JS cmpVer 三段式数字比较（vs AndroidApp.versionName）；APK 下载取首个 .apk 资产 browser_download_url + 双镜像；安装前 SHA-256/字节数双校验 + Android 8+ 安装未知应用授权引导",
      "限流修复（Net.rateLimited 纯函数 + 两通道接线）：旧版 403/429 先浪费一次 800ms 重试（限流窗口小时级，重试必败再烧同一配额）再报「GitHub 返回 403」——用户无从知道是限流还是何时能重试；新版即时报「GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试」（ReleaseNotesTest 更新链路段 7 断言钉死判定边界：403/429=限流，404/2xx/3xx/5xx/0=非限流）",
      "文案诚实性修复：下载完成态旧文案「下载完成（已验 SHA-256）」不实——SHA-256 实际在点击安装时才校验（Downloader.install→verifyDownloadedFile），改为「下载完成，点击安装（安装前自动校验 SHA-256）」如实描述；appVer/appCode 浏览器预览回落值 8.0.0/80000→8.0.3/80300；关于页 CURRENT_RELEASE 从 v8.0.0 过期内容同步为 v8.0.1→v8.0.3 三轮实绩",
      "「用户看不到新版本」根因结论（非代码缺陷）：更新接口只读 GitHub 正式 Releases——最新正式发布仍是 v7.9.6（2026-11-20）；v8.0.0 仅存 Draft（匿名 API 不可见）、v8.0.1/v8.0.2 从未建 Release 且代码未推送。用户看到 v7.9.6 是机制正确行为——不能绕过正式发布状态；修复路径=推送代码→建 v8.0.3 Draft→真机验证后 Publish",
      "本轮零识别/安全门/状态机/契约改动：仅更新链路错误提示与页面文案；既有防线（draft 过滤/版本比较/下载源白名单/SHA-256 安装闸/未知应用授权引导）全部保持",
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
