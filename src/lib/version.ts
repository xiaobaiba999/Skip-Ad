/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.13.0";
export const VERSION_CODE = 83000; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.13.0→83000）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.13.0",
    code: 83000,
    date: "2026-11-24",
    tagline: "开屏提醒跨升级修复 + 切回即提醒 + 上传快失败与网络恢复自动补传",
    notes: [
      "开屏提醒跨升级修复：旧版本点过「24 小时不再提示」的时间戳在覆盖升级后仍生效——升级到新版后第一次进入不再被旧节流吞掉（用户实锤「进入 app 没有弹出提醒」根因）；提醒卡记录当前版本，版本变化即重新提醒",
      "切回即提醒：从后台切回前台也是「进入 App」——提醒评估在冷启动与切回两种场景都执行（60 秒短窗防旋转/切回连弹，24 小时不再提示仍受尊重）",
      "上传快失败：连接超时 10 秒→6 秒，自动重试三次→四次（退避 0.5/1.2/2.4 秒）——失败等待大幅缩短、间歇路由下成功率提升",
      "网络恢复自动补传：手动上传失败后 30 分钟内切 Wi-Fi/移动网络、或网络恢复时，自动补传待传日志（无需手动重试；开启自动上传后长期有效，保持每 8 小时心跳检查）",
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
