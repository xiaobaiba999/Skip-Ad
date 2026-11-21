/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "8.0.2";
export const VERSION_CODE = 80200; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "8.0.2",
    code: 80200,
    date: "2026-11-28",
    tagline: "双 DFS 单遍化专项：matchAll+walk 两趟同树遍历融合为 walkFused 单遍（零命中扫描 getChild/分配/属性读减半）+ 21 场景逐字段对照测试 + JVM 基准证据入档",
    notes: [
      "双 DFS 单遍化（P3-1 专项落地）：新增 AdDecisionCore.walkFused（FusedScan：规则骑士+决策信号同遍收集）——零命中扫描（档位大头）不再对同一棵树走 matchAll+walk 两趟独立 DFS；骑士门序/去重/排序/满 4 早停与 matchAll 逐字段等价，决策信号与 walk 同一 visit 体（rider=null 时 byte-for-byte 旧路径）；探针命中/弹窗窗路径零变化",
      "三处语义差全部声明并测试钉死（DualDfsParityTest S11/S12/S17）：①树在两趟间变化时晚到候选顺延下一档（60ms 梯子/nudge 覆盖，出手前 refreshLive 复核不变）；②子树瞬时 binder 异常单次即失（确定性异常两者等价）；③小程序容器骑士入口即抑制（旧路 execute L0 拦，同为零点击）",
      "新增 DualDfsParityTest（21 场景 92 断言）：内容页/开屏/WebView/Native/Compose/极简/聊天孤立跳过/输入框/滚动/多窗口/树中途变化两方向/节点异常两类/预算截断/多候选/订阅命中合成顺序/订阅未命中/小程序容器/骑士满额早停/计数对照/vid 门边界——旧组合 vs 新单遍的命中集+顺序、决策信号全字段、决策结果逐项断言",
      "新增 ScanBenchmark（三形态×三规模 63 断言，SIMULATED）：确定性计数证据——child 调用（≈生产 getChild binder）精确减半（298→149/798→399/1198→599）、text/desc/bounds 读次减半、属性读总次降约 36%（2117→1351/5642→3601/8462→5401）、包装分配（≈NodeAdapter）减半；JVM 计时仅供参考，真机性能/电量 NOT_RUN",
      "本轮零安全门/状态机/契约改动：SafetyGuard/ComplianceGuard/AdTaskCore/日志五模块/坐标安全边界/DecisionEvidence 全部不动；小程序保护/菜单行保护/预算梯子在对照测试中实锤在岗",
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
