/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "5.8.0";
export const VERSION_CODE = 50800; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "5.8.0",
    code: 50800,
    date: "2026-11-18",
    tagline: "屏蔽速度压榨 · 三轮激进 · 首页极简防呆 · 订阅市场防呆版 · 10×15 机型矩阵",
    notes: [
      "屏蔽速度压榨：点击冷却 420→300ms · 倒计时缓冲 180→120ms（将尽提前 0.38s 开火）· 骨架扫描档 13→18 档（前 4s 密度×2）· 补扫节流 120→80ms · 弹窗冷却 1600→1200ms——从「按钮出现」到「点击命中」全链路压榨，功耗红线保持",
      "命中三轮激进：SDK 指纹 +4 段（Mintegral/Pangle/TopOn/Moloco）·「推广」文案族全量补收（合规敏感 App 以推广代广告）· 分应用表 +10 款（快手/搜狐视频/闲鱼/酷安/虎牙/斗鱼/百度网盘/WiFi万能钥匙/链家/抖音主线）",
      "首页怎么简单怎么来：保活体检压缩为一行状态只列待处理项；免打扰持久模式移入「我的→高级」，首页零极客内容",
      "订阅市场防呆版：只留 3 个高星活跃源（⭐7.4k/⭐935/⭐496），点一下整行即导入，去仓库按钮/去更多订阅/App 内零跳转",
      "我的模块：权限使用审计卡移除；引擎日志旁新增「刷新」按钮；上传成功只保留一个确认弹窗（单一反馈通道防呆）",
      "10 品牌 × 15 机型 = 150 机型测试矩阵六维全绿（开屏命中/屏蔽速度/bug/功耗/精准度/算法）；全仓 1666 项 JVM 断言全绿",
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
