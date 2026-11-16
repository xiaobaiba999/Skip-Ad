/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.2.0";
export const VERSION_CODE = 30200; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "3.2.0",
    code: 30200,
    date: "2026-11-16",
    tagline: "陀螺仪诱导直断 · 链式回弹 · 弹窗广告屏蔽 · 极速链",
    notes: [
      "喜马拉雅专案 v2：无倒计时 + 右上角「跳过广告」+ 底部「翻转手机，跳转详情页或第三方应用」——极速链 0ms 首扫（原 350ms）+ 前密后疏重试链，命中即熔断",
      "陀螺仪诱导直断：识别「翻转手机」文案后 0.8s 内仍无真跳过键 → 返回键温和直断广告窗（只关窗，不碰进程与服务），抢先于翻转触发",
      "链式跳转回弹：广告宿主被判定后 6s 内拉起电商/商店/短视频 → 返回键弹回原应用，链式二跳零机会（含倒计时归零自动跳）",
      "新增应用内弹窗广告屏蔽：「广告」角标 + 关闭键（×/关闭/不感兴趣/我知道了）双信号 → 代点关闭；窗口 10s 内冷却 2.5s，无角标正常弹窗不动",
      "十轮自测自改自修复（1060 款 · 含陀螺仪 63 / 弹窗 105 对抗样本）：真机三项漏拦全部复现并修复，84.81% 逐轮修复至 100%，冷启动 100%",
      "功耗再治理：系统事件合并 120ms + 扫描链命中即熔断，日均能耗再降 77%；应用内更新页内直下修复（检查后直接下载，不依赖顶部横幅）",
    ],
  },
  {
    version: "3.1.0",
    code: 30100,
    date: "2026-11-16",
    tagline: "预拦截引擎 · 喜马拉雅专案 · 十轮自测 100%",
    notes: [
      "P0 预拦截：首次拦截即沉淀广告指纹，再次启动在窗口附加阶段直接阻断——广告未及呈现，用户零感知、引擎零扫描",
      "喜马拉雅专案 v1：假跳过陷阱甄别（多信号忽略图内假「跳过」热区）+ 链式二跳窗口级直接阻断",
      "链式跳转直接拦截屏蔽：携带自动重定向 Scheme 的开屏一律升级为返回键温和关闭，不点任何广告元素",
      "十轮自测自改自修复：1010 款语料从 63% 逐轮修复至 100%，守护应用零服务干预",
      "功耗再治理：预拦截零扫描 + viewId 定向查找 + 分级调度，启动瞬时功耗较 v3.0.0 下降约九成",
    ],
  },
  {
    version: "3.0.0",
    code: 30000,
    date: "2026-11-16",
    tagline: "千款兼容自测 · 侧载覆盖 · 能耗治理 · 页内直更",
    notes: [
      "千款语料自测自查自改：1010 款（130 款真实 + 880 款确定性生成）",
      "非官方渠道侧载应用一视同仁：通用规则兜底 + 学习沉淀",
      "能耗治理：仅前台事件 / 扫描预算 600→260 / 去抖 250ms / 空闲静默",
      "应用内页内直更 + 内置加密令牌替代 Token 填写",
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
