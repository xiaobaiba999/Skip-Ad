/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.7.0";
export const VERSION_CODE = 77000; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "7.7.0",
    code: 77000,
    date: "2026-11-19",
    tagline: "回切开屏根治 + Toast 常驻修复 + 机型矩阵 352（九维）",
    notes: [
      "回切开屏根治（用户实锤「从另一个 app 切回这个 app 期间会弹广告没有拦截到」）：两个死因一起根治——①卫士自家包事件被整体丢弃致 lastFgPkg 从未登记自家包名（切回目标 App 时 entering=false 不按新进入处理）：自家包 WINDOW_STATE_CHANGED 登记前台边界 + ReentryGuard 纯函数（同包离开 ≥6s 判回切等价新进入——息屏回亮/白名单边界/ROM 漏网全兜住）+ rootWinCls 刷新 + 回切日志记账（30s 节流）",
      "进入期全量门（死因②）：回切开屏常叠在被宿主窗档案判 CLEAN 的主界面窗类上（同窗类盖开屏层，扫描链被抑制只剩探针哨兵，晚到/图标式跳过键整窗漏拦）——进入期 4s CLEAN 档案让路全量扫描（激进档·宁多扫绝不漏，进入是低频事件功耗代价可忽略）",
      "Toast 常驻修复（用户实锤「点击开启弹窗广告屏蔽后提示一直停在底部」）：旧实现收尾纯靠 JS setTimeout（真机上同步桥调用卡 JS 线程时 2200ms 计时不触发）+ 内联 cssText transition 在部分 OEM WebView 渲染失灵——改 CSS 关键帧动画（合成器线程驱动，JS 阻塞免疫）+ opacity/visibility 双终态 + JS 兜底计时器三重保险",
      "命中跳过率拓宽：探针词扩「关闭广告/關閉廣告」（切回期间插层常以整词作键）+ 繁体词典补收（TEXT_EXACT/SKIP_TEXTS）+ 机型矩阵 308→352（11 组 × 32 · 2027 世代：小米 19 系/Redmi K120/POCO F11/Mate 90/Magic10/Find X10/真我 GT9/X310/iQOO 21/S27/一加 18 系）+ 新增第九维回切开屏逐机型验证",
      "21 套件 5099 项 JVM 断言全绿（v7.6.1 为 4314 · +785）；新增 ReentrySplashTest 回切开屏专项套件（S1~S7：真值表/进入期门/自家包边界时间线/全分辨率回切命中/插层键词典/离开时长记账/九品牌几何投影）",
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
