/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "5.3.0";
export const VERSION_CODE = 50300; // major*10000 + minor*100 + patch，随版本单调递增
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

/** 「关于」页仅展示最近三个版本（硬约束：数组只保留最新 3 条，
 *  旧版本内容归档在 GitHub Releases 与 git 历史） */
export const VERSION_HISTORY: VersionEntry[] = [
  {
    version: "5.3.0",
    code: 50300,
    date: "2026-11-18",
    tagline: "李跳跳式三零治理 · 功耗降 50%+ · 聊天乱学根治",
    notes: [
      "宿主窗档案：连续 3 次零候选的窗口自动标记 CLEAN（跨重启保留），只跑探针哨兵零扫描零日志——豆包对话列表/微信排行页/拼多多落地页反复整树扫描根治（真机日志 #57/#58：扫描 2396 次/开机 → 稳态仅剩真广告窗）",
      "探针前置：李跳跳同款 findAccessibilityNodeInfosByText 系统原生搜索（成本≈DFS 的 1/14）先行，文本类广告全流程零树遍历；探针命中即档案复活全量出手，命中率红线零妥协",
      "内容突发预算制：聊天/流式内容不再误开弹窗识别窗（每应用 10 分钟最多 2 窗，空手突发阈值 3→6→12→24 指数升级）——豆包 5 分钟 7 次误报根治",
      "位置学习事后验证：零文本点击须 1.5s 内关窗（窗类切换/树体缩水≥40%）才学习，聊天点击起时丢弃——同秒学两个聊天按钮位置→次启乱点的隐患根除",
    ],
  },
  {
    version: "5.2.0",
    code: 50200,
    date: "2026-11-17",
    tagline: "10 秒警告根治 · 保活体检全品牌 · 两态通知",
    notes: [
      "Redmi 入门机型清理后台被吊销无障碍登记 → 强制看 10 秒警告的三大诱因（自启动/省电策略/清理后台）全部有设置直达，配置一次不再复发",
      "两态通知：登记仍在→点通知自动拉活（15s 自适应）；真被吊销→直达服务开关（文案明示系统 10 秒确认属正常防诈设计）",
      "登记移除遥测：系统吊销瞬间打点，下次日志可直接看到「什么时候被关的」",
    ],
  },
  {
    version: "5.1.1",
    code: 50101,
    date: "2026-11-17",
    tagline: "支付宝 XRiver 小程序容器保护",
    notes: [
      "支付宝 XRiver 小程序容器窗口零扫描零点击零学习（团购券/小程序误拦截根治）",
      "非主流 App 开屏攻坚：AdsActivity/DspActivity/DynamicDialog 窗口指纹扩充",
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
