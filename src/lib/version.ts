/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.6.1";
export const VERSION_CODE = 76100; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "7.6.1",
    code: 76100,
    date: "2026-11-19",
    tagline: "热修复：页面板块恢复（扫描日志/版本记录/使用报告）+ 激进模式保持",
    notes: [
      "热修复（用户实锤「扫描日志，7.6版本记录没了，使用报告也不见」）：v7.6.0 发布时页面资产 app.js 版本记录注释尾部丢了 */ 未闭合——块注释吞掉后续 17 行（整个 const CURRENT_RELEASE 声明被吃进注释），页面渲染抛 ReferenceError 中断，三大板块全部消失（node --check 语法层拦不住语义级损坏，坏资产进了已发布 APK）——闭合注释三大板块全部恢复",
      "构建链页面资产符号校验闸（防回归）：scripts/check-appjs.sh + build.sh 打包前硬门——语法解析 + catch-all stub 环境顶层执行（ReferenceError 硬拒·定时器 no-op 防挂起）+ 5 关键符号在场断言（CURRENT_RELEASE/renderLog/renderReport/renderReleaseNotes/notesToHeadlines——版本记录/扫描日志/使用报告三板块接线），缺一即拒绝打包",
      "死因复现验证：坑样本（未闭合注释）该闸 5 符号全灭拒打包退出 1；修复样本 5 项全过退出 0；已打包 APK 的包内资产抽出后该闸同样全过——此类损坏从源头拦下，绝不再进 APK",
      "激进模式全量保持（Java 侧零触碰）：开屏首击零门控（露头即点 0ms 命中·重击等将尽+6s 封顶）/ 插屏「广告」语境闸 contains + 关闭词扩表（残忍离开/不再看/暂不需要）+ 终闸语义面收编 / 滚动窗近旁铁证 + 新窗首扫提速",
      "20 套件 4314 项 JVM 断言全绿保持（零改动）；APK 849,462B · versionCode 76100 > 76000（v7.6.0 坏包直接覆盖即修复）· MD5 d7e4326257a21a5d73d80500cdc6dfec · SHA-256 b74cde33df8f6811f164b68a173bf72ad970d04e0763f56e7fd8517a9a5896a0",
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
