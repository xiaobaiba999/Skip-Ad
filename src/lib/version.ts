/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "8.1.0";
export const VERSION_CODE = 81000; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "8.1.0",
    code: 81000,
    date: "2026-11-29",
    tagline: "真机反馈修复轮：开屏跳不过六项根因修复（学习开关误门控/CLEAN失明/早退杀链/nudge断流/晚档预算）+ 一键上传日志（真上传取代导出）+ 自动上传 keepOn 连坐修复与 24h/3 本地前置闸",
    notes: [
      "广告链路六项根因修复（真机反馈「很多 App 开屏跳不过」全链路审计定案）：①「习惯学习」开关误门控整个开屏扫描（关闭=全灭且文案无警示）——解除门控，开关只管学习写入；②CLEAN 档案失明（跨重启72h只剩5词探针）——新增关闭/×/✕关闭线索探测（命中即档案复活转全量路径，探针层绝不直接出手）+ 复查期 72h→24h；③零候选退避早杀链——第一方入口窗无订阅覆盖不再提前杀链（梯子跑满12s，CLEAN档案仍兑底功耗）；④ nudge 补扫 4s 断流——4~12s 观察窗内 600ms 宽节流放行（晚出键内容事件救回）；⑤晚档扫描预算 360→600（深树晚出键可达）；⑥名实一致化：learning 开关真正管 learnFromManualClick",
      "一键上传日志（真机实锤「导出≠上传」根治）：「我的」页主按钮改为真上传（点击→强制封批→上传 Worker→回调结果——成功显示 Issue 编号，失败显示原因，服务端限流如实告知）；导出降为次按钮保留（离线场景）；设置页「立即提交」统一同一反馈链路；手动路径不受 20 条阈值限制（轻用户点按钮必有反馈）也不受 24h/3 自动额度限制（服务端 RATE_LIMITED 幂等兑底）",
      "自动上传修复与强化：KeepAliveReceiver keepOn（保活提醒开关）连坐修复——心跳续期/订阅静默更新/自动日志上传三个零打扰系统功能移到开关门之前，关「保活提醒」不再杀自动上传；新增 24h/3 本地前置闸（ackedCount24h>=3 不再发起自动上传——服务端滑窗仍为权威，双保险省电省流量）；失败退避 30s→480s×5+第6次终态 FAIL 保持（绝不无限重试）",
      "测试：38 套件（+2：AdScanReviveTest 扫描复活回归 28 断言 / LogUploadNowTest 一键上传与自动窗口回归 27 断言）；既有 36 套件零回退；S1-S4 分层红线钉死（关闭裸词绝不进跳过词表/手动自动额度分离/空批永不封）",
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
