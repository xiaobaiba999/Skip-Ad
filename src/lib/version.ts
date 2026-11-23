/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.14.0";
export const VERSION_CODE = 84000; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.14.0→84000）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.14.0",
    code: 84000,
    date: "2026-11-24",
    tagline: "检查更新陈旧窗口根治（@tag 探测链）+ 兜底直连 gcore 化",
    notes: [
      "检查更新陈旧窗口根治：每次发布后旧版检查更新最长 12 小时看到「已是最新」（jsDelivr @main 清单源站分支缓存——三连实锤根因）；现在追加 @tag 探测链（tag 引用是不可变键零缓存）：当前版本之后的候选 tag 清单发布即新鲜，命中即拿到新版元数据——发布后第一时间可检测到新版",
      "兜底直连 gcore 化：检查更新失败时的 CDN 直连下载链 cdn→gcore（cdn 当日对 APK 返回 403 实测定案，gcore 全日直出且与 Release 资产字节级一致）",
      "诚实边界：隔两个以上 minor 的老版本探测集覆盖不到最新版——仍靠 @main 链（源站缓存 ≤12h 自愈）或 gcore 版本化直链手工救济",
    ],
  },];

/** 三段式 tag（vX.Y.Z）→ 项目公式 versionCode（v7.10.0 统一版本规则：
 *  Web 面板更新判定只比 versionCode——versionName/tag 字符串比较已随
 *  compareVersion 删除；非三段 tag（历史 plus 系）→ 0（无效——不判新）。
 *  与 scripts/update_manifest.py version_code_of_tag 同语义。 */
export function versionCodeOfTag(tag: string): number {
  const m = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(String(tag || "").trim());
  if (!m) return 0;
  return Number(m[1]) * 10000 + Number(m[2]) * 1000 + Number(m[3]) * 100;
}
