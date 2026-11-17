/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.14.0";
export const VERSION_CODE = 31400; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "3.14.0",
    code: 31400,
    date: "2026-11-17",
    tagline: "跳过后反馈弹窗自动收起 · 秒跳再提速 · ADB 自愈移除",
    notes: [
      "跳过广告后弹出的「不喜欢/反馈」面板（喜马拉雅 10 次有 8 次）自动点「取消」收起，绝不替你提交任何反馈；面板出现即确认广告已关，不再无谓换候选重试",
      "露头就秒再提速：手势被系统中断时退冷却 + 350ms 快速重试（此前最长 4s+）；晚渲染跳过键补扫节流 80→50ms",
      "ADB 自愈授权整体移除（要电脑执行太麻烦）：开启服务一步直达系统开关，开启一次持续守护，退出 App/清理后台重进无需重新开启",
      "界面与代码瘦身：删 ADB 授权卡、内部版本号等冗余字段与孤儿代码，关于页三版描述言简意赅",
    ],
  },
  {
    version: "3.13.0",
    code: 31300,
    date: "2026-11-17",
    tagline: "验证码横幅四重防护 · 息屏零动作 · 夜间心跳降频",
    notes: [
      "验证码/短信/来电横幅四重防护：覆盖层包事件直接丢弃、验证码文案窗口绝不扫描、触点被横幅覆盖时拒发手势、横幅在场绝不派返回键",
      "待机功耗治理：息屏不扫描不点击不学习；夜间心跳改 30~45 分钟窗口式闹钟，绝不唤醒设备",
      "安全中心等系统工具包位置学习永久拒收；误触观察口径修正",
    ],
  },
  {
    version: "3.12.0",
    code: 31200,
    date: "2026-11-17",
    tagline: "纯跳过引擎 · 链式拦截整体移除",
    notes: [
      "链式拦截机制整体移除：只点「跳过/关闭」，绝不对抗你的任何跳转（分享、NFC 支付等用户意图场景无法穷举，任何安全门都有漏网时刻）",
      "系统连接诊断页循环误点归零；界面撤下「跳转回弹」开关与统计",
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
