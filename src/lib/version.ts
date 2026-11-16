/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "2.0.0";
export const VERSION_CODE = 20000; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "2.0.0",
    code: 20000,
    date: "2026-11-16",
    tagline: "秒跳引擎重构：开屏秒跳、摇一摇拦截、守护模式",
    notes: [
      "秒跳引擎：开屏广告毫秒级识别「跳过」控件并代点，任何应用开屏 0.1s 内进入主页",
      "传感器拦截：摇一摇/扭一扭类广告的加速度事件在引擎侧被挂起，晃动不再触发跳转",
      "守护模式：支付/银行/政务类应用照常拦截广告，但绝不终止应用进程与守护服务",
      "内置加密反馈通道：问题反馈自动上报 GitHub Issues，令牌服务端混淆存储，界面零配置",
      "学习中心：按应用/场景/时段沉淀使用习惯，学习规则每晚自动同步 GitHub 供版本迭代参考",
      "界面重构：精简为演练场 / 情报站 / 学习中心 / 关于，移除广告后台等非必要功能",
    ],
  },
  {
    version: "1.3.0",
    code: 10300,
    date: "2026-11-15",
    tagline: "联网能力：问题反馈同步 GitHub Issues、Releases 检查更新",
    notes: [
      "App 内问题反馈一键上报 GitHub Issues，检查更新直连 Releases",
      "APK 体积与发版流水线收尾，历次安装包归档供快速下载",
    ],
  },
  {
    version: "1.2.0",
    code: 10200,
    date: "2026-11-15",
    tagline: "判断引擎与习惯学习",
    notes: [
      "多信号精准区分「广告诱导跳转」与「用户主动操作」（按压/轨迹/反应时间）",
      "无障碍代点服务 + 观察手动点击生成应用专属规则，误判一键纠正",
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
