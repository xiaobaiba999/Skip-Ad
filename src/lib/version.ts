/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "4.4.0";
export const VERSION_CODE = 40400; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "4.4.0",
    code: 40400,
    date: "2026-11-17",
    tagline: "露头就秒大提速 · 恢复防护零触摸 · 功耗再降",
    notes: [
      "倒计时门控「开闸瞬间即点」：设门控时同步排 +40ms 精确补扫针——此前门控说 4s 后出手、实际要等下一固定档扫描（最慢再拖 5s+），喜马拉雅/keep 实测快 4~5 秒，快于人眼",
      "点击后 450ms/900ms 快速复验针（点击冷却 900→600ms）：「点了没反应」场景人手 300~500ms 就补第二刀，引擎此前要等 1.2s+ 固定档",
      "H5 开屏按钮晚到救回通道：H5 广告按钮 1~2s 才渲染、内容页早退曾把补扫全让路（「有时候触发不了」元凶）——杀链 400→2000ms + 广告/学习/Dialog 窗绝不杀 + 杀链后内容事件 900ms 节流降频放行",
      "恢复防护零触摸：服务真实绑定态判定（僵尸实例不再假报守护中）+ 深度拉活 6s 每半秒踢一次系统重绑 + 打开 App 即自动拉活——「点恢复防护跳系统设置看 10 秒强制警告」根治",
      "功耗再降：息屏状态广播缓存（热路径零 IPC）· 非前台内容事件一步丢弃 · 晚档扫描预算收紧 · 复验换候选提速",
    ],
  },
  {
    version: "4.3.0",
    code: 40300,
    date: "2026-11-17",
    tagline: "订阅主力规则接入 · 应用白名单 · 喜马拉雅反馈循环根治",
    notes: [
      "GKD 默认订阅 globalGroups 全局开屏组全量解析（CSDN 等第三方 App 漏跳元凶）+ 数字关系符/childCount/text.length/括号布尔全语法",
      "复验真正换候选（同坐标 6 连击根治）；新增应用白名单（零扫描零点击零学习）；喜马拉雅「为什么关闭广告」循环弹窗根治 + 假倒计时提速",
      "扫描未命中记诊断日志（窗口类名/树规模/候选样本）；功耗再降（内容页早退 + 补扫节流 bug 修复）",
    ],
  },
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
