/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "5.4.0";
export const VERSION_CODE = 50400; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "5.4.0",
    code: 50400,
    date: "2026-11-20",
    tagline: "多源规则统一调度 · 统一 DSL · 质量治理 · 全形态适配",
    notes: [
      "多源规则统一调度体系：官方内置 / GKD 社区订阅 / 本地学习三类规则源统一优先级权重、冲突检测、去重合并；100% 兼容 GKD 订阅语法（快照选择器/逻辑组合/窗口限定）",
      "统一规则 DSL：内置/订阅/学习三类规则整合为同一描述格式与通用解析器，跨端（Java/TS）判定逻辑同源，双端维护成本减半",
      "学习规则质量治理：Wilson 置信下界评分模型 + 噪声样本过滤（同秒多点位/窗类不稳/树体缩水不足全拒学）+ 生命周期管理（生效/降级/淘汰）+ 同 SDK 家族规则泛化",
      "五类新广告场景：半屏插屏（优量汇/穿山甲形态）、更新诱导弹窗（只点消极按钮）、悬浮广告球（零点击引导关权限）、信息流嵌入（标注不代点）、视频前贴（到点即跳）",
      "敏感场景零扫描扩容 12 类：支付/密码/验证码/短信/系统设置/输入法/锁屏/通话/转账/实名/OTP/权限对话框；权限使用审计透明化 + 学习数据全链路脱敏",
      "分级动态功耗：设备四档分级 × 电量四态联动动态降级；全形态自适应阈值：折叠屏/平板/分屏形状门按窗口尺寸动态换算",
      "Web 端：规则调试沙箱（自定义节点树 + 实时匹配 + 单步调试 + 导入导出）、情报站可视化看板（趋势/排行/时段/命中率/功耗）、规则可视化管理（编辑/批量/优先级/启停）",
    ],
  },
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
