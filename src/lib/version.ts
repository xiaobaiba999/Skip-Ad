/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "8.2.1";
export const VERSION_CODE = 82100; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "8.2.1",
    code: 82100,
    date: "2026-11-22",
    tagline: "日志提交可靠性专项：手动一键上传可复活耗尽型失败批次 + 有界排空循环 + Issue 编号可点击直达 + TTL 清理实装",
    notes: [
      "手动提交强化（方向调整：聚焦 GitHub Issues 日志闭环——Cloudflare 更新代理部署已取消，日志中转 Worker 为既有部署零变更）：①开跑前自动复活「重试耗尽型」FAILED 批次（同 batchId 重试——服务端幂等兜底绝不二建 Issue；服务端终态拒收型绝不复活）；②有界排空循环（每轮 3 批·最多 8 轮·无进展即停——重度用户一键上传不再只传首批 3 批就停；仅首轮补 STATS 不造垃圾批）；③成功提示 Issue 编号可点击直达 GitHub（openUrl 桥）；④失败提示如实告知「失败批次已保留，重试时一并提交」",
      "FAILED 终态细分（failKind：terminal 服务端明确拒收 / exhausted 网络耗尽）+ 14 天检视窗 TTL 清理实装 + acked-meta 3 天 TTL 裁剪实装（v8.0.0 注释宣称的保留策略此前未落实现）",
      "attemptsMade 契约对齐修复：主路径传「含刚失败的这次」（旧代码少计 1 次实际第 7 次才终态）；异常路径耗尽判定 > 改 >= 与 decide 同口径——第 6 次即终态",
      "安全与整洁：exportLog 补上 Java 侧二道脱敏（app.js 注释一直声称的双层脱敏首次真正落实现）；死代码清理（requestUploadNow/buildLogBody 190 行/newIssuePrefillUrl/enc/ISSUES_PAGE）；secret-scan 私钥模式精度修正（要求完整 PEM 铠甲——零漏报，消除 worker PEM 解析代码的格式名误报）",
      "测试：全量 7151 断言 0 失败（新增 B11 手动复活 12 断言 / B12 TTL 清理 4 断言 / P9 终态拒收判定 12 断言 / S3p revived 契约）",
    ],
  },];

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
