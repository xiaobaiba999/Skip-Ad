/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.7";
export const VERSION_CODE = 79700; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.9.6plus→82300 名码不对应已于 v7.9.7 收口：versionName = Tag = Release = APK）
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
    date: "2026-11-23",
    tagline: "版本体系收口：versionName 7.9.7 = versionCode 79700（标准三段式同名同码）+ Bug 反馈一次性提示",
    notes: [
      "版本体系收口：彻底告别 7.9.6plus→82300 的名码不对应——从本版起 versionName = Git Tag = Release = APK versionName，versionCode 严格遵循 v7.1.0 起标准公式 major*10000+minor*1000+patch*100（7.9.7→79700），以后每一版都如此",
      "Bug 反馈提示：新版本首次进入一次性轻提示「发现 Bug 或使用不舒服？可到 我的→引擎日志→一键上传 反馈」（广告没跳过/跳过不准确/App 卡顿/误跳/其他异常），可「去上传日志」直达入口或「知道了」关闭；只提醒绝不自动上传、不采集任何新数据、同版本不弹第二次",
      "更新检测保持 versionCode 主判（远端 > 本地才提示，79700=79700 不自误报）+ 全屏更新页 + SHA-256/大小安装前校验全链保持",
      "引擎与日志系统零改动：广告跳过核心/无障碍扫描/日志采集/手动上传/自动上传额度（24 小时最多成功 3 次·只记服务端确认·失败不耗额度）全部保持 v7.9.6plus 实测形态",
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
