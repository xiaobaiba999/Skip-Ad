/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.3.0";
export const VERSION_CODE = 30300; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "3.3.0",
    code: 30300,
    date: "2026-11-16",
    tagline: "SDK 广告窗识别 · 热区直断 · 弹窗 v2 · 回弹扩充 · 更新链路修复",
    notes: [
      "根因修复：SDK 广告窗 className 指纹（穿山甲/优量汇/快手/AdMob/宿主 ad.activity）——文案烧在图里的零文本广告首次可判，500ms 无真跳过键即直断，直断后沉淀指纹次启 300ms 快断",
      "全屏热区识别：≥55% 屏幕可点 + 节点≤12 + 无输入框三条件判定图片广告 → 900ms 直断（守护名单豁免）；返回键被拦自动补一次（每窗最多 2 次）",
      "弹窗广告 v2：弹窗窗期独立于开屏 12s 窗（v3.2.0 真机 bug：用一会儿后弹出的广告永远关不掉）；角标扩充（广告/推广/赞助/AD/Sponsored）；Dialog 形态 + 营销话术弱信号；覆盖层内容突发检测",
      "链式跳转回弹扩充：微信/QQ/支付宝/美团/知乎/小红书/B站/主流浏览器入名单；广告窗仍活跃时任意跳转目标都弹回；广告判定 3s 内同包拉起 WebView 落地页同样弹回",
      "应用内更新链路修复：v3.2.0 的 GitHub Release 因 API 出口间歇故障未发布成功（真机一直停在 v3.1.0——四项「未生效」的根因）；检查更新改为令牌 + 多重重试 + 列表回退，下载直连失败自动切换镜像通道",
      "十四轮自测自改自修复（诚实重构模型）：v3.2.0 终态诚实命中率 86.79% → v3.3.0 四项攻坚逐轮验证至 100%；冷启动 100%；陀螺仪 63 款全断、弹窗 106 款全关（含覆盖层/窗外）",
    ],
  },
  {
    version: "3.2.0",
    code: 30200,
    date: "2026-11-16",
    tagline: "陀螺仪诱导直断 · 链式回弹 · 弹窗广告屏蔽 · 极速链",
    notes: [
      "喜马拉雅专案 v2：无倒计时 +「跳过广告」+「翻转手机」诱导——极速链 0ms 首扫 + 前密后疏重试链，命中即熔断",
      "陀螺仪诱导直断：识别「翻转手机」文案后 0.8s 内仍无真跳过键 → 返回键温和直断广告窗（只关窗，不碰进程与服务）",
      "链式跳转回弹：广告宿主被判定后 6s 内拉起电商/商店/短视频 → 返回键弹回原应用",
      "新增应用内弹窗广告屏蔽：「广告」角标 + 关闭键双信号 → 代点关闭；窗口 10s 内冷却 2.5s",
      "十轮自测自改自修复（旧模型 100%；后经真机反馈与 v3.3.0 诚实重构修正为 86.79%——零文本广告无出手信号）",
      "功耗再治理：系统事件合并 120ms + 扫描链命中即熔断，日均能耗再降 77%",
    ],
  },
  {
    version: "3.1.0",
    code: 30100,
    date: "2026-11-16",
    tagline: "预拦截引擎 · 喜马拉雅专案 · 十轮自测（旧模型）",
    notes: [
      "P0 预拦截：首次拦截即沉淀广告指纹，再次启动在窗口附加阶段直接阻断——广告未及呈现，用户零感知、引擎零扫描",
      "喜马拉雅专案 v1：假跳过陷阱甄别（多信号忽略图内假「跳过」热区）+ 链式二跳窗口级直接阻断",
      "链式跳转直接拦截屏蔽：携带自动重定向 Scheme 的开屏一律升级为返回键温和关闭，不点任何广告元素",
      "十轮自测自改自修复（旧模型）：1010 款语料从 63% 逐轮修复至 100%，守护应用零服务干预",
      "功耗再治理：预拦截零扫描 + viewId 定向查找 + 分级调度，启动瞬时功耗较 v3.0.0 下降约九成",
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
