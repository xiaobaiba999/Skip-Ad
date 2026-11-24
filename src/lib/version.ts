/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.4：开屏提示 12 小时窗（24h→12h）+ 检查更新失败 GitHub 加速器
 * 指引三处落地（失败文案/帮助/当前版本下方醒目小字）。 */
export const APP_VERSION = "7.15.4";
export const VERSION_CODE = 85400; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.4→85400）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.4",
    code: 85400,
    date: "2026-11-24",
    tagline: "开屏提示 12 小时窗 + 检查更新失败 GitHub 加速器指引",
    notes: [
      "开屏提示「不再提示」窗口 24 小时 → 12 小时：弹窗节奏更跟手；升级新版后仍会重新提醒一次（版本钉不变）",
      "检查更新失败文案、帮助页、当前版本下方（醒目橙字）三处新增 GitHub 加速器指引——检查更新失败时开启 GitHub 加速器（如 Watt Toolkit）重试即可，不开梯子也有出路",
      "配合 v7.15.3 检查更新三面全崩根治（jsdelivr 四域梯度 + API 匿名优先 + 令牌轮换）——本轮把「失败了怎么办」送到用户眼前",
      "修复 version.ts 头注释未闭合（v7.15.3 遗留）：块注释吞掉 APP_VERSION/VERSION_CODE 声明——与 v7.6.1 页面资产符号闸同类事故，本轮补上闭合并如实记录",
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
