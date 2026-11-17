/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "4.0.0";
export const VERSION_CODE = 40000; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "4.0.0",
    code: 40000,
    date: "2026-11-17",
    tagline: "GKD 订阅生态接入 · 双引擎规则 · 全新界面",
    notes: [
      "接入 GKD 订阅生态：粘贴任意 gkd.li 社区订阅链接即可导入规则（选择器引擎兼容 GKD 语法），远程订阅 24 小时自动静默更新",
      "内置规则库双引擎：文案五层规则 + 控件 ID（vid）规则，21 款主流应用内嵌，安装即用零配置",
      "全新绿色主题界面：跳跳侠吉祥物 + 三页极简（首页/订阅/我的），去除冗余字段与说明文字",
      "安全红线不变：订阅规则仅支持点击类动作，back 类规则在加载时直接丢弃（链式拦截红线）",
    ],
  },
  {
    version: "3.14.0",
    code: 31400,
    date: "2026-11-17",
    tagline: "跳过后反馈弹窗自动收起 · 秒跳再提速 · ADB 自愈移除",
    notes: [
      "跳过广告后弹出的「不喜欢/反馈」面板（喜马拉雅 10 次有 8 次）自动点「取消」收起，绝不替你提交任何反馈",
      "露头就秒再提速：手势被系统中断时 350ms 快速重试；ADB 自愈授权移除，开启服务一步直达",
    ],
  },
  {
    version: "3.13.0",
    code: 31300,
    date: "2026-11-17",
    tagline: "验证码横幅四重防护 · 息屏零动作 · 夜间心跳降频",
    notes: [
      "验证码/短信/来电横幅四重防护：横幅上的验证码绝不被点掉",
      "待机功耗治理：息屏不扫描不点击不学习；夜间心跳改 30~45 分钟窗口式闹钟，绝不唤醒设备",
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
