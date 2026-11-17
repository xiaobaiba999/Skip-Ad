/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "4.3.0";
export const VERSION_CODE = 40300; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "4.2.0",
    code: 40200,
    date: "2026-11-17",
    tagline: "用户操作让路 · 微信主界面零扫描 · 订阅全门控 · 12h 自动日志",
    notes: [
      "用户操作让路：你点击后 4s 内引擎不出手；你点开的广告落地页/详情页 30s 内绝不自动关闭——「点开又被自动关、手动重开又被关」根治",
      "微信主界面（LauncherUI）零扫描零点击：GKD 订阅规则曾在此反复命中连点（「进入微信左右乱划」元凶），现在整体让路；弹窗不受影响",
      "GKD 订阅规则全门控：activityIds 强校验（社区规则的 Activity 限定此前被忽略）+ 同窗限次（actionMaximum）+ 间隔（actionInterval）+ 无限定规则 skip 形态门；并接入弹窗场景——「导入了却零命中」根治；内置规则优先、订阅接力",
      "引擎日志每 12 小时自动提交（手动上传不受影响）；上传内容更具体：16 条带时间戳日志 + Top5 应用战绩 + 订阅命中 + 能耗画像",
      "功耗整治：订阅规则单遍树扫描（此前每条选择器独立遍历全树）、4s 后内容变化零补扫、前台门 IPC 重排、引擎日志容量翻倍",
    ],
  },
  {
    version: "4.1.0",
    code: 40100,
    date: "2026-11-17",
    tagline: "一键暂停 · 相机/灵动岛误触发根治 · 订阅 404 修复",
    notes: [
      "应用内一键暂停/恢复防护（免看系统 10 秒警告）；相机/拍摄窗硬黑名单（微信「拍摄」被强退根治）；灵动岛/悬浮窗前台门（乱点根治）",
      "GKD 订阅导入 404 修复（官方订阅包路径迁移 + 镜像自动回退）；全新 Logo",
    ],
  },
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
