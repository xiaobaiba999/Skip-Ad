/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.7：Keep/CSDN 动态图开屏二重根治（同包覆盖层多窗扫描 + 图片键
 * 边界闸语义换源）+ 底栏「版本」页 + 开屏提示 3 秒观闸与亮字承诺。 */
export const APP_VERSION = "7.15.7";
export const VERSION_CODE = 85700; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.7→85700）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.7",
    code: 85700,
    date: "2026-11-25",
    tagline: "Keep/CSDN 动态图开屏二重根治（多窗扫描+图片键边界闸）+ 底栏「版本」页 + 开屏提示 3 秒观闸",
    notes: [
      "同包覆盖层窗口多窗扫描（Keep 根治，v7.15.6 泛位置识别仍不中续修）：真机日志实锤 Keep 难拦型开屏的「跳过」键住在同包覆盖层窗口（广告 SDK 自建 Window），而 rootInActiveWindow 指向的 SplashActivity 只有 8 节点——旧版 collectRoots 只要活动窗口属于本包就短路返回，覆盖层窗口永远不被扫 = 按钮在场却零识别（用户手动点「跳过 4」被学习观察到即节点真实存在的铁证）。现在开屏窗内扫描/文本探针/图形探针全量枚举 getWindows() 同包窗口（活动窗去重：getWindowId 对比；弹窗/信息流/插屏路径零变化）",
      "图片跳过键边界闸语义换源（CSDN 根治）：真机日志实锤「角落图片跳过键：语义断言（非跳过/关闭方向）→ 放弃」反复出现——纯图键无文本无 vid 语义，通用终闸语义断言永远凑不齐证据 = 收得到候选却出不了手。新增 imageKeyBoundaryCheck：仅当零文本零 vid 证据（bl/bv 双空）且 reason 是 corner_image_skip/rand_graphic_skip 时换源（场景断言同源 + 严格形态三重尺寸门 面积≤6%·宽≤30%·高≤12% 与泛位置候选同源）；有文案/vid 证据但非跳过语义的真诱饵照旧一票否决零放行",
      "底栏新增「版本」页（用户指令）：当前版本 + 检查更新 + 版本记录从「我的」抽出独立成页（四底栏：首页/帮助/版本/我的），并新增「迭代节奏」亮字承诺卡（每周根据日志问题至少迭代版本一次）；「我的」页专注卫士使用报告与引擎日志（用户指令：留在我的模块）",
      "开屏提示双升级（用户指令）：亮字承诺「每周根据日志问题至少迭代版本一次」（品牌绿高亮行）；至少观看 3 秒才可操作「知道了/去上传日志/12 小时不再提示」（三按钮禁用 + 倒计时行 + 遮罩同期让路，到点自动解锁绝不卡死）",
      "未命中日志多根可见化：双根时显示「树 N 节点 · 根 2」（旧版只显示最后根的节点数），候选样本覆盖全部窗口（旧版只采 roots[0]——覆盖层里的真跳过键样本全被吞）——上传日志补规则不再盲猜",
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
