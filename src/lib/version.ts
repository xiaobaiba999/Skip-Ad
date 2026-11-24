/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.5：下载通道白名单死局根治 + 开屏扫描误门控解除（开屏/插屏
 * 严格分域）+ 订阅规则零命中自动瘦身。 */
export const APP_VERSION = "7.15.5";
export const VERSION_CODE = 85500; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.5→85500）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.5",
    code: 85500,
    date: "2026-11-24",
    tagline: "下载通道死局根治 + 开屏扫描误门控解除 + 订阅规则自动瘦身",
    notes: [
      "下载死局根治（v7.15.1~v7.15.4 实锤：点下载报「下载链接不在允许的发布通道内」）：渠道链首位的 gcore CDN 直链被白名单拒且不换渠道——白名单收编 jsdelivr 四域 + 渠道启动被拒自动换道 + 清单撤 cdnApkUrl 字段（旧版不升级也能恢复，首渠道回到 ghfast.top）",
      "开屏全灭 + 零日志根治：v7.15.0 版本线重制时丢失 v8.1.0 修复，「习惯学习」开关误门控整个开屏扫描——关学习 = 全部 App 开屏零扫描零日志。开关回归只管学习写入；设置页文案同步澄清（关闭不影响已有跳过能力）",
      "开屏/插屏严格分域：开屏观察窗内插屏卫士让路（不再抢跑/不再误记「插屏未命中」）；SDK 广告窗观察窗 12s→20s + 补 14/16.5/19s 扫描档（视频开屏跳过键常在视频末 5~15s 露头）",
      "日志严格区分广告类型：开屏出手记「⚡ 开屏跳过」；插屏/信息流路径取消同次出手双日志双计数；订阅命中携带 GKD 组名（开屏广告/插屏广告——日志可归因）",
      "订阅规则自动瘦身（命中很少的去除）：市场源某应用规则评估 ≥300 次（跨 ≥3 天）仍零命中 → 自动停用；命中过的永不自动停；30 天后自动复评；内置源不受影响；使用报告可见",
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
