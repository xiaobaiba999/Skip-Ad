/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.4.0";
export const VERSION_CODE = 30400; // major*10000 + minor*100 + patch，随版本单调递增
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

/** 「关于」页仅展示最近三个版本 */
export const VERSION_HISTORY: VersionEntry[] = [
  {
    version: "3.4.0",
    code: 30400,
    date: "2026-11-16",
    tagline: "角落图片跳过键 · 坐标级点击兜底 · 手势降级 · 决策核心可单测",
    notes: [
      "根因修复（微博/喜马拉雅等主流应用仍拦截失败）：第一方图片开屏——窗口类名是应用自己的 Activity（非 SDK 指纹）、跳过按钮纯图片零文本、倒计时烧在图里，v3.3.0 全部识别手段零信号",
      "角落无字小可点控件识别（L4）：顶部 14% 高、贴右/左缘、小面积可点、无任何文本 → dispatchGesture 手势点击其中心 650ms（图片跳过键正解）",
      "坐标级点击兜底（L6）：开屏窗内零信号简单树 → 点击标准跳过位（88%宽/7%高），防误点六重约束（开屏窗内/无滚动/无输入框/无关闭键/每次启动一次/守护名单豁免）",
      "点击执行降级链：performAction 失效自动改 dispatchGesture 真实触点（许多应用拦截无障碍代点，但拦不住真实触点）；多窗口扫描（getWindows 遍历，弹窗/覆盖层不再漏扫）；开屏窗扫描预算 280→600",
      "决策核心 AdDecisionCore（纯 Java 零 Android 依赖）：识别/决策逻辑全部下沉，JVM 38 用例发版前必绿——微博 3 场景 / 喜马拉雅 4 场景 / 误报防护 12 场景（信息流/输入框/隐私弹窗/DownloadActivity/守护名单）",
      "十八轮自测（诚实模型）：v3.3.0 终态在新建模下 95.09%（第一方图片开屏 ×53 全漏）→ 角落 ×31 + 坐标 ×22 全拦 → 100%；喜马拉雅 v4：角落跳过键 659ms 抢在翻转 900ms 前",
    ],
  },
  {
    version: "3.3.0",
    code: 30300,
    date: "2026-11-16",
    tagline: "SDK 广告窗识别 · 热区直断 · 弹窗 v2 · 回弹扩充 · 更新链路修复",
    notes: [
      "SDK 广告窗 className 指纹（穿山甲/优量汇/快手/AdMob/宿主 ad.activity）零文本可判 500ms 直断，直断沉淀指纹次启 300ms 快断",
      "全屏热区识别（≥55% 可点 + 节点≤12 + 无输入框）900ms 直断；返回键被拦自动补一次；弹窗广告 v2（窗期独立 + 角标扩充 + Dialog/营销话术 + 覆盖层内容突发检测）",
      "链式跳转回弹扩充（微信/QQ/支付宝/美团/知乎/小红书/B站/浏览器）；更新链路修复（v3.2.0 因发版脚本仓库名拼写错误未发布成功，真机一直停在 v3.1.0）",
      "局限（v3.4.0 修复）：第一方图片开屏（应用自建广告系统）窗口类名非 SDK、跳过键纯图片零文本——本版识别手段零信号",
    ],
  },
  {
    version: "3.2.0",
    code: 30200,
    date: "2026-11-16",
    tagline: "陀螺仪诱导直断 · 链式回弹 · 弹窗广告屏蔽 · 极速链",
    notes: [
      "陀螺仪诱导直断（翻转文案 0.8s 无真跳过键返回键温和直断）；链式跳转回弹；弹窗广告屏蔽 v1（角标+关闭键）；极速链 0ms 首扫",
      "因发版脚本仓库名拼写错误未发布到 GitHub（能力已并入 v3.3.0+）；十轮自测旧模型 100%（v3.3.0 诚实重构修正为 86.79%）",
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
