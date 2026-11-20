/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.2";
export const VERSION_CODE = 79200; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "7.9.2",
    code: 79200,
    date: "2026-11-21",
    tagline: "200+ 应用生态兼容性基线：233 款应用目录 + L1 测试体系 + 引擎包名校准五项根治",
    notes: [
      "233 款应用兼容性目录（compatibility/apps_catalog.json · 目标 ≥200 达成）：包名三层来源严格区分（引擎自证 115 · 网络逐条核实 23 · 知识库高置信 95）· 12 分类配额全达标（社交 25 / 短视频 25 / 资讯 20 / 电商 26 / 音乐 10 / 浏览器 11 / 工具 24 / 阅读 15 / 游戏 17 / 学习 15 / 金融生活 35 / 其他 10）· 包名格式/唯一性断言 · 每款记录 adTypes/uiFramework/优先级/引擎覆盖交叉（内置订阅 119 / Feed 名单 19 / GKD 快照 148）",
      "引擎包名校准五项根治（目录核实直接揭出——全部应用商店上架信息实锤）：① 中国联通真实包名 com.sinovatech.unicom.ui（旧条目把 Activity 类名当包名——规则从未命中过任何真实设备）② 网易新闻 FeedAdGuard 名单补 .activity 后缀（旧值从未命中——信息流护栏对网易新闻全程无效）③ 建设银行/中国银行两行名称互换更正 ④ com.lemon.lvoverseas 误标剪映（实为 CapCut 海外版）——补剪映国内版 com.lemon.lv ⑤ com.flightmanager.view 误标飞常准（实为航班管家）——补飞常准真实包名 veryzhun.vzcom",
      "L1 分层测试体系（AppCatalogTest 新套件 · 43 断言 · 六段断言面）：目录完整性（规模/配额/格式/唯一性）· 内置订阅↔目录双向一致性（零孤儿包 + 零虚标）· Feed 名单↔目录全落座（网易新闻类死条目缺陷的回归闸）· GKD 三源快照解析 + 覆盖交叉 · 逐应用规则匹配仿真（内置订阅 119 款逐包开屏仿真全命中 + rowsFor 路由面 + 正常主界面零出手反例）· 包名校准五项回归闸",
      "诚实口径测试矩阵（compatibility/test_matrix.json）：L1 规则/引擎自动化（全量 233 款）→ L2 页面结构场景（12 类 UI 样本规划）→ L3 真机实测（分批 50/100/233）· 统一指标口径（广告关闭成功率/漏检率/误触率——规则匹配成功 ≠ 广告关闭成功 · 应用数量 ≠ 广告样本数量 · 自动化断言 ≠ 真机兼容性测试）· 结果词汇表（NOT INSTALLED / NO AD OBSERVED / MISS / FALSE CLICK / VERIFIED SUCCESS）",
      "24 小时自动日志同步（3 次/天）：scripts/auto-log-sync.mjs 学习数据快照刷新 + 历史归档 + 变更即提交推送（无变更优雅跳过）——三项定时（01:20 / 09:20 / 17:20 Asia/Shanghai）",
      "25 套件 6240 项 JVM 断言全绿（+43 = AppCatalogTest 新套件；既有 6197 项零回退）；安全红线零变化（SafetyGuard 终闸/语义断言/预算封顶/坐标面全保持——本轮全部是数据级校准，零点击行为面变化）；性能零变化（零新增扫描/零新增订阅/零新增轮询）· APK 844KB · VC 79200 覆盖安装即升级",
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
