/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "5.5.0";
export const VERSION_CODE = 50500; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "5.5.0",
    code: 50500,
    date: "2026-11-20",
    tagline: "UI 交互专项 · 版本记录极简 · 日志上传三重反馈 · 10 秒警告红线",
    notes: [
      "版本记录极简化：App「我的」页与 Web 关于页仅展示当前安装版本（版本号 / 核心要点 / 发布时间三项，从版本配置动态读取），历史版本归档 GitHub Releases",
      "日志上传强反馈：上传全程阶段化加载提示（收集→脱敏→提交）；成功后弹窗确认 + 顶部醒目横幅 + 系统通知三重反馈，明确告知「上传成功，感谢反馈」；失败给出明确原因与一键重试",
      "日志内容规范化：应用版本 / 设备信息 / 拦截日志 / 异常堆栈四类核心内容，全链路正则脱敏（手机号 / 身份证 / 邮箱 / IP），敏感信息本地脱敏后再上传",
      "关闭防护强约束（10 秒警告红线）：启停仅走 App 内主开关与通知栏快捷开关；跳转系统设置前强制二次状态判定——只要服务仍在系统无障碍登记中一律走应用内自愈，绝不引导跳转，彻底规避厂商 10 秒安全警告",
      "崩溃采集闭环：进程崩溃堆栈本地持久化，随日志上传自动附带，异常不再靠猜",
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
