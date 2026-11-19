/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "6.0.0";
export const VERSION_CODE = 60000; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "6.0.0",
    code: 60000,
    date: "2026-11-19",
    tagline: "1:1 人手模拟引擎 · 误触/多点根治 · 五轮激进 · 12×15 机型矩阵",
    notes: [
      "1:1 人手模拟引擎（用户实锤：部分用户设备开屏跳过时因设备类型、分辨率或其他原因多点、误点）：人手触点参数域（起始延迟 26~56ms · 触点时长 95~160ms · ±2px 微抖轨迹——替代千次一模一样机器人签名，反自动化广告 SDK 触点指纹检测不再无声吞点）",
      "人手复验节奏：首击 500ms 扫一眼（替代 450/900 双针机器枪），二击后 1.4s/2.4s/3.6s/5.2s/7.0s 渐进放缓（人会越等越慢）· 同窗耐心预算 12→6（6 下没关优雅收手等倒计时——多点级联根治）",
      "实时人眼追踪：出手前 refresh() 刷新节点【现在】的 bounds——广告按钮入场缩放/位移动画后按旧坐标点击=点空的根因根治",
      "dp 密度归一：触点安全域常量从固定 px 迁 dp 按设备密度缩放（160dpi 与 480dpi 行为归一——「设备类型、分辨率原因」的触点漂移根治）· 触点轨迹去重 ±8dp（同坐标绝不重复盲刺）",
      "命中五轮激进：SDK 指纹 +4 段（百度联盟 mobads · Sigmob · AdColony · Vungle）；分应用表 +6 款（微博/腾讯视频/优酷/爱奇艺/芒果TV/酷我音乐）",
      "12 品牌 × 15 机型 = 180 机型矩阵六维全绿 + 人手矩阵 24 几何 × 真实密度配对全绿；全仓 2352 项 JVM 断言全绿（+367）",
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
