/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.3";
export const VERSION_CODE = 79300; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "7.9.3",
    code: 79300,
    date: "2026-11-21",
    tagline: "L2 页面场景层：12 类 UI 独立样本 × 8 项场景行为 + 订阅七分类真实计数器断言",
    notes: [
      "L2 页面结构与场景兼容性套件（PageScenarioTest 新套件 · 83 断言 · 八段断言面）：12 类 UI 独立样本逐类断言节点树可访问性与决策行为——native-view（银行简洁原生开屏）/ WebView 容器（TMS 营销窗·sdkAdWin 指纹）/ Compose（semantics 文本）/ Flutter（desc-only 节点 label 回落）/ ReactNative（文本跳过键）/ 自绘控件三态（树极简合法出手·内容窗零出手·SDK 指纹解禁）/ Canvas（SurfaceView 零文本零候选）/ 视频播放器（控制层跳过片头）/ 半屏 Dialog 插屏 / PopupWindow（更新诱导单信号零出手——误触红线）/ 底部悬浮 overlay / 多窗口分屏内容窗零出手",
      "8 项场景行为（用户目录规格全建齐）：内容延迟加载（帧1 未到零出手→帧2 跳过键出现即出手）· 关闭按钮延迟出现（videoLike 画像/20s 观察窗/arm-hint 免杀链——>12s 露头按钮可等到）· 候选消失（DETECTED→VERIFYING→两轮零确认→SUCCESS 不可逆→2.2s 冷却闸）· 广告与反馈面板切换（面板文案零跳过候选 + FEEDBACK 态不对原广告补刀 + 面板链落终态不恢复原任务）· 页面内部导航（同窗身份零出手 + 聊天窗特征齐全零出手 + LauncherUI 不进 feed 扫描）· 窗口切换任务终局（有出手→SUCCESS / 零出手→CANCELLED 不诬为成功 / 验证中被打断→终局）· 节点树可访问性（逐类样本 walk 全通过）· 视频开屏画像调度契约（videoLike 四因子/观察窗/轮询节奏/终态降频/白点节流/预算刷新）",
      "订阅七分类真实计数器断言（SubscriptionManager 生产实现首次纳入 JVM 测试面——run-tests.sh 编译闭包扩至 SubscriptionManager+Net+android.jar）：noCover（未知包应用不匹配 +1）· noMatch（有覆盖页面不匹配 +1——与 noCover 分列）· hit（内置订阅跳过键规则命中 +1）· notLoaded（已加载零计数——归因诚实）· clickFail/clickOk/blocked（countClickOutcome 三分支）· 非订阅规则路径不计入（不虚标）——不再把所有未命中都归为订阅未命中",
      "26 套件 6323 项 JVM 断言全绿（+83 = PageScenarioTest 新套件；既有 6240 项零回退）；安全红线零变化（本轮零生产逻辑改动——SubscriptionManager 仅新增两个 JVM 测试钩子 primeForJvm/subStatSnapshot，生产永不调用）；性能零变化（零新增扫描/订阅/轮询）· APK 844KB · VC 79300 覆盖安装即升级",
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
