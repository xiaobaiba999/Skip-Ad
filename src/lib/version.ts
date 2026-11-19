/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.4.0";
export const VERSION_CODE = 74000; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "7.1.0",
    code: 71000,
    date: "2026-11-19",
    tagline: "规则使用顺序治理 + 信息流广告卫士 + 白名单中文名搜索 + 使用报告",
    notes: [
      "订阅两源使用顺序根治（用户实锤：内置规则库和订阅市场规则的使用顺序和命中问题）：市场源（用户导入·日更）在该应用有专属规则时优先、内置源兑底、全局组垫底；两源各贡献一个候选进点击轮换；扫描日志新增两源覆盖诊断——「导入了怎么还是不跳」上传日志即可定位",
      "信息流广告卫士（用户实锤：自动关闭 app 内广告内容——微信朋友圈就业广告等）：两段式 P1 点「广告/就业」披露角标 → P2 点「关闭该广告」中性项收菜单；四道安全闸（角标词精确等值/面积门/P2 双闸/18 款名单门+微信 sns 窗级门）——绝不点广告主体（宁漏勿误）",
      "底层算法优化：分应用规则行预筛 memo（每窗一次预筛，探针/DFS 双路径共享——每窗省 ~340 节点 × 50 行前缀比对；与旧全表路径数学等价，RuleOrderTest 39 项锁死）",
      "应用白名单中文名搜索 + 卫士使用报告：白名单搜索栏中文名/包名双通道实时过滤；我的页一键生成使用报告（拦截战绩/订阅两源命中分账/Top5 应用/能耗画像/设备画像）",
      "机型矩阵 286→308 · 七维→八维：每品牌 +2 新世代机型（HyperOS 5/HarmonyOS 8/MagicOS 12/ColorOS 18/OriginOS 8/One UI 10）；新增信息流广告维（角标/菜单几何按机型分辨率 1:1 缩放逐机型验证）；11 组 × 28 = 308 机型八维各 308/308；全仓 17 套件 4067 项 JVM 断言全绿（+616）",
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
