/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.3：检查更新三面全崩根治（jsdelivr 四域梯度 + GitHub API 匿名优先
 * + 内置令牌轮换——旧令牌 401 失效实锤，日志上传/问题反馈一并恢复）。
export const APP_VERSION = "7.15.3";
export const VERSION_CODE = 85300; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.3→85300）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.3",
    code: 85300,
    date: "2026-11-24",
    tagline: "检查更新三面全崩根治（jsdelivr 四域梯度 + API 匿名优先 + 令牌轮换）",
    notes: [
      "GitHub API 兜底匿名优先：旧版令牌直连，令牌失效（401 Bad credentials）直接把「用户网络里唯一可达的源」堵死——真机实锤「多源均不可达 · GitHub API 返回 401」三面全崩的第三面；令牌任何状态不再阻塞检查更新",
      "jsdelivr 四域梯度（gcore→cdn→fastly→testingcf 逐域探测 + 域偏好 sticky）：cdn.jsdelivr.net 单域写死是国内间歇不可达时清单源的结构性弱点——与 raw@main 被墙叠加后仅剩 API 一条路",
      "内置令牌轮换：旧令牌实测 401 已失效——问题反馈/手动日志上传/8h 自动上传一并恢复（与检查更新同把令牌）",
      "UpdateHostsTest 新增 24 断言（四域梯度/偏好置顶/tag 域收敛/非法域攻击面收敛）",
    ],
  }];

/** 三段式 tag（vX.Y.Z）→ 项目公式 versionCode（v7.10.0 统一版本规则：
 *  Web 面板更新判定只比 versionCode——versionName/tag 字符串比较已随
 *  compareVersion 删除；非三段 tag（历史 plus 系）→ 0（无效——不判新）。
 *  与 scripts/update_manifest.py version_code_of_tag 同语义。 */
export function versionCodeOfTag(tag: string): number {
  const m = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(String(tag || "").trim());
  if (!m) return 0;
  return Number(m[1]) * 10000 + Number(m[2]) * 1000 + Number(m[3]) * 100;
}
