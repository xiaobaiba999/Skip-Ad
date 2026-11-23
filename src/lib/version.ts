/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.11.0";
export const VERSION_CODE = 81000; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.11.0→81000）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.11.0",
    code: 81000,
    date: "2026-11-23",
    tagline: "检查更新失败 CDN 兜底直连 + 开屏提醒提速 + 帮助页 + 订阅后台自动导入 + 重进状态修复",
    notes: [
      "检查更新失败 CDN 兜底：「查询失败」时出现「直连下载最新版（CDN 兜底）」按钮——jsDelivr CDN 仓库内 APK 直链（每版本随发布归档入库 + adshield-latest.apk 恒指最新版别名），下载后覆盖安装即升级；正常路径新增 cdnApkUrl 兜底镜像（主源失败自动切换，SHA-256/大小闸不降级）",
      "开屏提醒提速：进入 App 即显示（不再等 3 秒）——提醒把卫士固定在后台（分机型操作指引：通用/小米 HyperOS/华为荣耀/OPPO vivo）；「24 小时内不再显示」选项（纯本地时间戳）；「知道了」不耗资格",
      "新增帮助页（底栏）：如何使用（三步）/ 固定后台（重要）/ 订阅规则（自动）/ 检查更新失败怎么办 / 常见问题 / 隐私与权限",
      "订阅模块自动化：底栏订阅页移除——id667 的 GKD 订阅（Lin-arm ⭐7.4k · 日更）后台每日自动导入一次（服务连接 + 5 分钟心跳双触发；八通道镜像链 + APK 内置离线快照双重兜底，失败 1h 冷却重试——永不硬失败）",
      "重进体验修复：冷启动宽限期——刚断开不久（≤10 分钟）时首页显示「服务重连中」而非「未开启」（AMS 重绑慢不再误导向系统设置看 10 秒警告）；服务最近连接时刻持久化，前台看门狗自动恢复「守护中」",
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
