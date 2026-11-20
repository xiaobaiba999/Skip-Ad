/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.5";
export const VERSION_CODE = 79500; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "7.9.5",
    code: 79500,
    date: "2026-11-21",
    tagline: "真实世界证据层：点击决策证据 + 点击结果六态闭环 + WebView 综合证据路径 + L3 批次定义",
    notes: [
      "点击决策证据（最小结构化——用户指令第一优先级）：每次准备 CLICK 前记录包/Activity/任务ID/代际/任务状态 · 候选来源（text/contentDescription/resourceId/imageCorner）· 上下文四格（adContext/sdkContext/webViewContext/subscriptionContext）· 归一化几何（位置/面积/屏幕区域/可点）· SafetyGuard 裁决/finalDecision。隐私红线结构性强制：不保存完整 Accessibility Tree（模块无树容器）、不上传原始截图（零位图引用）、账号/手机号/支付/聊天内容一律白名单闸拒收（sanitizeToken 只保留 close/skip 词族成员 ≤12 字符）、有界环形缓冲（12 条绝不无限驻留）",
      "点击结果六态闭环（用户指令第二优先级）：CLICK_DISPATCHED → VERIFYING → VERIFIED_SUCCESS / RETRY / MISS / FALSE_CLICK / SAFE_BLOCK / UNKNOWN——dispatchGesture 成功绝不等于广告关闭成功（唯一成功证据是验证链：两轮零候选/窗口切换/反馈面板收起）。六态计数与最近一击证据单行入上传日志（「点击结果」「点击证据」行）。FALSE_CLICK 自动化环境恒 0——真机样本回传唯一来源，绝不虚报",
      "订阅证据链八格（用户指令七）：consulted / matched / candidateAccepted / clicked / verifiedSuccess / clickFail / falseClick / blocked——matchSources 被咨询即计数（含未加载/无覆盖——订阅参与度证据与命中解耦）；候选过 SafetyGuard 终闸计数；订阅出手的任务 VERIFIED_SUCCESS 计数（点击→广告真关闭闭环）。上传日志「订阅证据链」行直接可读；七分类 subStat 兼容保持零破坏",
      "WebView 综合证据路径（用户指令六——真广告+ScrollView/Input/WebView 专项）：isAdLikeLoose 对滚动容器/输入框/大树（WebView 视频容器常态 >150 节点）形成过强阻断——真 H5 广告带跳过文本整窗漏拦。不取消安全门：综合判断 = WebView 容器在场【且】至少一个广告语境证据（角标/SDK 窗指纹/Dialog 形态/热区）同窗；WebView 单独在场不构成证据（应用内浏览器普通页零出手——误触红线）；候选语义+形状门+SafetyGuard 终闸全部在岗。L1 跳过键与 L1.5/L1.6 关闭键三路同步",
      "视频开屏自适应轮询（用户指令八——20s ceiling 保持）：低证据降频（连续 ≥4 针探针零命中且零出手 → 1.5s→3s——不再以固定高频扫整个 20s 观察窗）；探针命中或出手立即恢复标准节拍（证据增强）；证据消失立即终止（每针头部 videoLike 复核既有契约保持）；既有 pollDue 3 参重载与终态 3s 契约零破坏",
      "L3 真机批次 1 定义（用户指令四）：50 款 · 12/12 类配额（不按列表前 50）· video-splash 全收 6 + banner/rewarded-video/feedback-sheet 各 2 强制收录 · 引擎覆盖四档交叉（GKD 43/内置 39/Feed 11/零覆盖 3）· P0/P1/P2 混合 · 全部记录字段 14 项（app/…/finalResult）PENDING_REAL_DEVICE——REAL_DEVICE_TEST = NOT_AVAILABLE 诚实标记（本机无真机），绝不预填结果。失败样本库建库（compatibility/samples/ · 修复优先级 FALSE_CLICK > DUPLICATE_CLICK > MISS > 命中率）",
      "测试（30 套件 6601 项全绿，v7.9.4 为 6487——+114 = DecisionEvidenceTest 82 + MinimalWindowBatchTest 32；既有 28 套件零回退）· 极简窗口专项批次（≤60 节点无文本四角 × 8 类交互图标普通 UI 全零出手 + 树极简兜底契约 + 中部边带 FAB 防线 + 时机闸 + vid 语义正信号）· APK versionCode 79500（> 79400 覆盖安装即升级）",
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
