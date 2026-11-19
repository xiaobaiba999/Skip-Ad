/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.6.0";
export const VERSION_CODE = 76000; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "7.6.0",
    code: 76000,
    date: "2026-11-19",
    tagline: "激进模式（开屏露头即点 + 插屏「广告」语境闸重做）",
    notes: [
      "开屏首击零门控（用户实锤：有的还跳不了了/非常非常慢——v7.5 门控从第 0 击起全次生效让所有带倒计时的开屏全等倒计时走完）：跳过键露头即点 0ms 命中；首击白点后重击才等「将尽」；重击等待 6s 封顶（误检大数字不再无限等）",
      "插屏「广告」语境闸 contains 重做（用户实锤：插入的广告不仅没跳过——旧闸⓪精确等值三闸把「广告·了解详情」等真机变体全拒）：≤10 字可见标签 contains 一网打尽；语境节点只作在场证据绝不点击",
      "插屏终闸语义面收编 + 关闭词扩表：新 interBoundaryCheck 终闸收编「知道了/残忍离开/不再看/暂不需要」族（旧通用终闸一票否决——词表收了也白收）；「取消」激进模式下也保持拒收",
      "插屏位置闸激进放宽 + 新窗首扫提速：非滚动窗零位置闸、滚动窗近旁铁证（|Δcy|≤35% 同排）可出手（名单外信息流卡片广告也关）；窗切换即重置节流——插层出现当帧就扫",
      "多点误点 1:1 仿真：首击唯一立即 + 重击等将尽（>1.6s）天然满足 F 同目标静默窗（人手双击下限）；九品牌×5 槽位触点钳制全绿；扫描日志插屏双样本诊断（语境+关闭钮）；20 套件 4314 项 JVM 断言全绿",
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
