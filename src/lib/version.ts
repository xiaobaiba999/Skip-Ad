/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.4";
export const VERSION_CODE = 79400; // major*10000 + minor*1000 + patch*100，随版本单调递增
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
    version: "7.9.4",
    code: 79400,
    date: "2026-11-21",
    tagline: "FalsePositive 场景层 + AdTask 时序压力 + 安全整改（令牌出库/密钥扫描硬门）",
    notes: [
      "FalsePositiveScenarioTest 新套件（69 断言）：用户 20 类误触红线场景逐类断言「绝对不点」——银行/政务/支付守护名单页（可滚+长文本+输入框形态铁证）· 相机（模式条可滚+快门/闪光/翻转角落图标在树仍零出手）· 聊天窗（孤立「跳过/关闭」气泡零出手——v7.9.4 L1 证据质量根治面）· Launcher 桌面 · 商品详情/购买/收藏/分享/评论 · 更新提示单信号 · 权限请求（允许/拒绝均不代点）· 会员弹窗（最坏情形只关关闭族、CTA 零出手）· 普通 PopupWindow 菜单/表单 Dialog/悬浮 FAB · 多窗口分屏 · 小程序/团购容器 L0 纵深防御 · 反馈面板——附带孤立词红线（关闭/×/取消/跳过/稍后/知道了）与对照侧（真广告语境同词照常出手——识别能力零回退）",
      "AdTaskTimingTest 新套件（95 断言）：A-H 八组时序竞态（旧回调 100/300ms 两档拒收·候选坐标改变不得复用旧坐标·窗口切换终局·Dialog 消失后普通按钮冷却闸·反馈面板零补刀·videoPoll 终态降频 3s 契约·延迟露头键 100/300/500/800/2000/5000ms 六档出现前零出手出现后首过即出手）+ Case1/2/3 三种真机实锤硬回归（成功后旧 task/callback/timer/videoPoll/TapGestureCallback 全拒第二次点击=0·重试五要件逐项缺失全拒·click()=true ≠ 广告关闭——onDispatch 无 SUCCESS 出口枚举断言）",
      "生产修复两处（证据质量提升非关闭扫描）：① L1 跳过键路径增加广告形态证据门 isAdLikeLoose（无滚动/无输入框/≤150 节点）或 SDK/学习指纹——开屏窗龄内的聊天窗/内容页孤立「跳过」字样不再被当成跳过键点击（真开屏窗恒满足形态门零回退，与 L1.6 关闭键口径对齐）② AdTaskCore 无任务 S_NONE 契约加固——一切状态转移不得从「无任务」无中生有（服务侧 null 守卫已保证，纯契约收紧）",
      "安全整改：GitHub Token 从代码库完全移除（原双层 XOR+Base64 混淆内嵌自 v2.0.0 入库——已标记需撤销·docs/SECURITY.md R1）→ 环境变量/本机未入库文件凭据链；新增 scripts/secret-scan.sh 密钥泄露扫描硬门并接入 run-tests.sh 与 build.sh（令牌形态/内嵌凭据形态/本机凭据入库/keystore 散落四道检查）；APK 准则不变（三目录分发）· 28 套件 6487 项断言全绿（+164；既有 6323 项零回退）· APK 844KB · versionCode 79400（> 79300 覆盖安装即升级）",
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
