/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.10：三图类型死洞根治轮（用户实锤三张截图 + #103 日志）——
 * 开屏×强广告形态豁免带（H5 大树/上滑滚动容器/晚到键三重死洞）+
 * 系统权限弹窗零扫描 + 图片键候选诊断日志 + 探针去重/图形探针扩容。
 * v7.15.9 全部能力保留（CLEAN 晚档梯子/微树门分账/退避微树豁免）。 */
export const APP_VERSION = "7.15.10";
export const VERSION_CODE = 86000; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.10→86000）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.10",
    code: 86000,
    date: "2026-11-25",
    tagline: "难跳广告三类型根治（开屏×强广告形态豁免带）+ 权限弹窗零扫描 + 功耗优化（v7.15.9 全部能力保留）",
    notes: [
      "开屏×强广告形态豁免带（用户实锤三张截图与 #103 日志同期）：①CSDN 视频开屏左下圆形图片跳过键（无倒计时）②应用商店推广页纯倒计时徽章（跳过键 1~3s 晚到）③上滑互动 H5 广告左下「跳过 2」胶囊（canvas 渲染零无障碍文本）。公共死洞：H5/视频广告树 300~800 节点 > corner 点击门 150 树上限（泛位候选收集又排除角落几何 → 大树角落图片键完全无处可点）；上滑互动容器 scrollable → L4/L4.7 硬挡；视频晚到键 >6.5s 时窗截断。豁免带仅限【开屏窗 × 强广告形态（热区/角标/SDK 窗/学习窗）】同真：树上限 150→600、scrollPresent 豁免、时窗 6.5s→8s；非开屏窗（信息流底部导航「我的」Tab 误点红线）与无强形态窗（普通页角落图标）一切照旧 byte-for-byte",
      "系统权限弹窗零扫描（#103 日志实锤）：permissioncontroller 的 GrantPermissionsActivity 被当开屏扫描 + 弹窗识别双路径白扫（「开屏未命中……候选样本：拒绝/始终允许」+「弹窗未命中 · 入窗 1.79E9s」双日志刷屏）。「拒绝」是全 app 误触代价最高的控件之一——事件入口直接丢弃（isSystemNoise 收编 permissioncontroller，同 systemui 口径）：零扫描零点击零日志。S9 服务级场景双向验证：旧代码 energyScans 0→20（白扫实锤）→ 新代码 0",
      "诊断日志升级：未命中日志新增「图片键候选 角落N+泛位M（未出手：门控/时序）」——图片键在场但被门控/时序挡住时直接可见（三图类型补规则金样本）；「入窗」字段对 winStart=0 场景显示 ?（弹窗路径 winAt 未初始化时不再出现 1.79E9 疯值）",
      "功耗与性能优化：探针去重 200ms/包（bumpScanChain CLEAN 0ms 排针与首个内容事件探针双跑收敛——每窗省一轮全窗枚举+文本搜索）；CLEAN 窗图形探针预算 120→180（H5 大树 300+ 节点时深层图片键复活面，仍远低于全量 340~600）；权限弹窗类系统窗零扫描。能耗画像主体（手势估算 ~11µWh/次）是跳过动作本身——v7.15.9 秒跳 67 vs v7.15.6 42，多跳的每一次都是产品在干活",
      "测试与验证：SplashCountdownTest S8 豁免带真值表 7 断言（大树出手/滚动出手/7s 晚到出手 + 非开屏大树拒/非开屏滚动拒/零强形态拒/零强形态 7s 拒——误触红线全保持）；RandGraphicSkipTest 断言升级（8s 上限/非开屏滚动红线/零强形态晚到红线）；ServiceHostTest S8 端到端（H5 大树+滚动+左下图片键手势落点验证）+ S9（权限弹窗零扫描零日志）双向验证（旧代码红/新代码绿）；全套 4528 断言 + 服务级 18 项全绿",
    ],
  }];

/** 三段式 tag（vX.Y.Z）→ 项目公式 versionCode（v7.10.0 统一版本规则：
 *  Web 面板更新判定只比 versionCode——versionName/tag 字符串比较已随
 *  compareVersion 删除；非三段 tag（历史 plus 系）→ 0（无效——不判新）。
 *  与 scripts/update_manifest.py version_code_of_tag 同语义。 */
export function versionCodeOfTag(tag: string): number {
  const m = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(String(tag || "").trim());
  if (!m) return 0;
  return Number(m[1]) * 10000 + Number(m[2]) * 1000 + Number(m[3]) * 100;
}
