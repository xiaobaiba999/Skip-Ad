/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "8.0.1";
export const VERSION_CODE = 80100; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "8.0.1",
    code: 80100,
    date: "2026-11-27",
    tagline: "系统级审计轮：全链路调用图审计（安全面零问题实锤）+ 窗口切换 0ms 双重全量扫描消除（每次切窗省一整遍树扫描）+ secret-scan 闸门自断修复 + 视频轮询自适应节拍真值表补测",
    notes: [
      "全链路审计（docs/audits/V8-SYSTEM-AUDIT.md + V8-OPTIMIZATION-PLAN.md）：事件→扫描→决策→点击→复验→终态全调用图行号级绘制；安全面逐项核对零问题（九态状态机真值表/taskId+generation 全回调贯穿/重复点击断源五道防线/ACK 前不删日志/未授权零磁盘写/上传零阻塞——全部在岗实锤）；matchAll+walk 双 DFS 同树、videoPoll walkDue 双工作、appendEvent 全文件读写等发现分级入档（P3-1/P3-2 列下轮专项并给出否决理由与前置条件）",
      "性能优化（P2-1）：窗口切换路径 bumpScanChain(pkg, true) 跳过梯子 0ms 档——内联首扫（onAccessibilityEvent 现场同步出手）与 0ms 档对同一棵树的全量双扫是纯幂等冗余，每次切窗白付一整遍扫描（守护①②+反馈查+探针 5 词+matchAll+matchSources+walk，至多 ~1500 节点访问）；60ms 档与 nudge 通道覆盖树演化，安全门/决策梯/任务门/档案抑制路径零变化（suppress 路径 0ms 档保留——该路径无内联先行）",
      "仓库卫生修复（P0-1 实锤）：reports/iteration-v8.0.0.md 文档字符串字面含 PEM 头形态，触发 secret-scan 自身正则令 HEAD 测试/构建闸全断（接管首跑即被阻断——任何后续会话无法跑测试）；改写措辞修复，扫描闸强度零变化",
      "测试补强（VideoSplashTest S9 · 18 断言：75→93）：补 v7.9.5 自适应轮询节拍真值表（adaptivePollGapMs 三档边界/4 参 pollDue 到期判定/walkDue×miss-streak 交互契约——75→93 断言）——P3-2 videoPoll 重构的前置钉子（此前该纯函数面零覆盖）",
      "本轮零识别/安全门/状态机/契约改动：不新增关键词、不扩大点击区域、不降 SafetyGuard、不动任务状态机、不动日志五模块——纯调度冗余消除与测试/文档",
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
