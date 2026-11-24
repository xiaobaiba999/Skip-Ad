/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.2：息屏/切回拦截根治（SDK 广告窗直断兜底链 + 锁屏在场总闸）
 * + 8h 自动上传死链复活（维护心跳）+ 插屏倒计时关闭词三族（GKD 同源）。 */
export const APP_VERSION = "7.15.2";
export const VERSION_CODE = 85200; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.2→85200）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.2",
    code: 85200,
    date: "2026-11-24",
    tagline: "息屏/切回拦截根治 + 8h 自动上传死链复活 + 插屏倒计时关闭词（真机日志实锤修复轮）",
    notes: [
      "SDK 广告窗直断兜底链：识别瞬间即排 900/1600/2400/3400ms 四档兜底针（独立于扫描代际护栏）——零动作且无闸拦时直派返回键温和直断；未触发必打原因日志（🛟）——真机实锤：整个开机周期返回键 0，承诺落空的死因根治",
      "锁屏在场总闸（误触红线）：应用在锁屏后面就把返回开屏窗拉起来（事件早于 USER_PRESENT）——出手前统一过 keyguardBlocking（广播优先零 IPC + IPC 定音自愈型）；锁屏在场绝不出手（日志可见 🔒）",
      "8h 自动上传死链复活：维护任务只挂心跳自检分支而服务健康时心跳被撤（#89 孤例是 MiTV：ROM 反复解绑反而救活心跳链）——存活期 30 分钟窗口式维护心跳 + 分档续期（活着 30 分钟/死了 5 分钟）+ 维护任务前置 keepOn 闸外",
      "插屏倒计时关闭词三族（GKD 全局规则同源）：「跳过 4」「定时关闭，剩余24分16…」「5s后关闭」（必须含数字——广告倒计时特异信号）；强窗形（SDK 广告窗/Dialog）免语境闸⓪；JVM 4379 断言全绿（+31）",
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
