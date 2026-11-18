/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "5.9.0";
export const VERSION_CODE = 50900; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "5.9.0",
    code: 50900,
    date: "2026-11-18",
    tagline: "设备自适应触点 · 四轮激进 · 覆盖层防误触 · 11×15 机型矩阵",
    notes: [
      "设备自适应触点引擎（用户实锤：设备不同→多点或无点=误触）：原始几何中心 → TapPoint 四层防线——祖先可见交集（半裁剪标签点可见侧）· 节点窗口钳制（分屏/折叠另半区拒点）· 安全选点（复验象限轮换不再盲刺同坐标 + 边缘内缩防圆角/波纹死区 + 可见缝<12px 盲刺拒绝）",
      "覆盖层防误触（泛化）：触点被 Z 序更高窗口（输入法/悬浮球/他应用 overlay/状态栏/横幅）覆盖 → 拒发手势改用无障碍代点——键盘弹出不再乱打字、验证码横幅不再被点掉",
      "形变防死区：扁矩形纵向恒居中（横幅状标签不贴上下边死区）· 竖窄矩形横向恒居中——水波纹/圆角死区零接触",
      "命中四轮激进：SDK 指纹 +2 段（快手 KSAdSDK ksad · 穿山甲联盟 csj_ 复合族——刻意不用裸 csj 防业务包碰撞）；分应用表 +6 款（网易新闻/腾讯新闻/新浪新闻/夸克/有道词典/哈啰出行）；LearnGovernor 泛化清单同源对齐",
      "11 品牌 × 15 机型 = 165 机型矩阵六维全绿（新增传音 Tecno/Infinix/itel·HiOS/XOS 族）+ 设备自适应触点矩阵 24 类几何 × 9 场景全绿；全仓 1985 项 JVM 断言全绿（+319）",
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
