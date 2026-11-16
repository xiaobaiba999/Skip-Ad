/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.1.0";
export const VERSION_CODE = 30100; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "3.1.0",
    code: 30100,
    date: "2026-11-16",
    tagline: "预拦截引擎 · 喜马拉雅专案 · 十轮自测 100%",
    notes: [
      "P0 预拦截：首次拦截即沉淀广告指纹，再次启动在窗口附加阶段直接阻断——广告未及呈现，用户零感知、引擎零扫描",
      "喜马拉雅专案：假跳过陷阱甄别（多信号忽略图内假「跳过」热区）+ 链式二跳窗口级直接阻断，针对该广告特性「测试→优化→再测试」闭环",
      "链式跳转直接拦截屏蔽：携带自动重定向 Scheme 的开屏一律升级为返回键温和关闭，不点任何广告元素",
      "十轮自测自改自修复：1010 款语料（含假跳过/链式二跳对抗样本）从 63% 逐轮修复至 100%，守护应用零服务干预",
      "功耗再治理：预拦截零扫描 + viewId 定向查找 + 分级调度，启动瞬时功耗（前 10 秒）较 v3.0.0 下降约九成，日均能耗再降",
      "应用内更新彻底不跳转：检查更新与 APK 下载全部经内置加密通道页内完成，流式进度条直显",
    ],
  },
  {
    version: "3.0.0",
    code: 30000,
    date: "2026-11-16",
    tagline: "千款兼容自测 · 侧载覆盖 · 能耗治理 · 页内直更",
    notes: [
      "千款语料自测自查自改 ×3：1010 款（130 款真实 + 880 款确定性生成），三轮闭环 63.76% → 91.58%",
      "非官方渠道侧载应用一视同仁：通用规则兜底 + 学习沉淀，侧载覆盖 91.1%",
      "能耗治理：仅前台事件 / 扫描预算 600→260 / 去抖 250ms / 空闲静默",
      "引擎反应耗时与端到端耗时分开统计：跳过键出现后引擎出手均耗 249ms",
    ],
  },
  {
    version: "2.0.0",
    code: 20000,
    date: "2026-11-16",
    tagline: "秒跳引擎重构：开屏秒跳、摇一摇拦截、守护模式",
    notes: [
      "秒跳引擎：开屏广告毫秒级识别「跳过」控件并代点",
      "传感器拦截：摇一摇/扭一扭类广告的加速度事件在引擎侧被挂起",
      "守护模式：支付/银行/政务类应用照常拦截广告，但绝不终止应用进程与守护服务",
      "内置加密反馈通道 + 学习中心夜间自动同步 GitHub",
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
