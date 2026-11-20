/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.6";
export const VERSION_CODE = 79600; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "7.9.6",
    code: 79600,
    date: "2026-11-20",
    tagline: "L3 真实世界验证轮：Bug#001 终局三漏根治（冷却闸绕过→无限重试循环阻断 + MISS 终局记账）——代码审读实锤 · 一个真实 Bug 一个回归测试",
    notes: [
      "Bug#001（代码审读实锤的生产缺陷，非理论修补）：taskOnScanOutcome 非零候选路径在「预算耗尽于复验时刻」直接置 S_FAILED，三漏——① 不落 endedAt：taskAllowsNew(now, 0) 因「无终态记录恒放行」直接放行 → 2.2s 冷却闸被绕过 → 立即开新任务且 resetTaskForNewInstance 清空 clickTries/gaveUp → 同一存活广告借「新任务重置预算」无限重试循环（绝不无限重试/预算梯子红线失守——DUPLICATE_CLICK 同族风险面）；② 不记 MISS：最后一击复验失败被膨胀记成 RETRY——六态把任务终局 MISS 整类漏记（真实漏检率证据链失真）；③ 终局无日志",
      "修复（终局裁决收进纯函数，服务侧薄接线）：AdTaskCore.onVerifyStillStep——预算耗尽 → FAILED + endedAt=now（冷却闸在岗：新任务须过 2.2s 闸才重置预算）+ MISS 终局一次记账（本击复验不再计 RETRY——严格区分「点击派发成功」与「任务终局」）；预算内首击复验仍记 RETRY（既有行为零变化）；FEEDBACK 复验在场原态保持（面板流程在先，零行为变化）；终态不可逆不重复记账（旧任务绝不复活）",
      "一个真实 Bug 一个回归测试：Bug001RegressionTest（52 断言 · 八段）——S1 终局三件套 · S2 全生命周期（三击梯子 RETRY=2·MISS=1·FAILED 不可逆）· S3 冷却闸闭环（无限重试循环结构性阻断）· S4 门语义取证（endedAt=0 即绕闸——防回退钉死）· S5 终态不重复记账 · S6 FEEDBACK 语义零变化 · S7 RETRY 仅首击复验计一次（防多扫膨胀）· S8 六态计数端到端",
      "本轮零识别/安全门改动：不新增关键词、不扩大点击区域、不降 SafetyGuard、不加轮询——唯一生产修改是终局记账/冷却闸 bookkeeping（安全 > 正确关闭 > 命中率 > 性能微优化）；L3 批次 1 保持 PENDING_REAL_DEVICE（REAL_DEVICE_TEST = NOT_AVAILABLE——v7.9.6 复查仍无真机，50 款记录字段一律真机回填绝不预填）",
      "测试 31 套件 6653 项全绿（+52 = Bug001RegressionTest；既有 6601 项零回退逐套件核对）· APK versionCode 79600（> 79500 覆盖安装即升级）",
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
