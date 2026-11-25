/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.13：app→app 跳转接管轮（用户实锤：从 app 跳转到另一个 app 后
 * 目标 app 开屏「跳不了/没触发」）——竞速自检梯（三针主动晋升，不再
 * 被动等内容事件）+ 探针阴性快重试（切屏动画期首探阴性后 800ms 快档
 * 连探）+ 输入法零前台劫持（#114 Sony 键盘起落晋升循环根治）。
 * v7.15.12 全部能力保留（更新存活链四件套 + B站系补全）。 */
export const APP_VERSION = "7.15.13";
export const VERSION_CODE = 86300; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.13→86300）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.13",
    code: 86300,
    date: "2026-11-25",
    tagline: "app→app 跳转接管（竞速自检梯+阴性快重试+IME 零劫持）（v7.15.12 全部能力保留）",
    notes: [
      "app→app 跳转开屏根治（用户实锤：从 app 跳转到另一个 app 后目标 app 开屏广告「跳转不了或没触发」，#110-#115 六台设备日志考古）：①竞速自检梯——跳转动画期目标 app 的窗口事件因活动窗口归属未切换被竞速丢弃后，静态开屏（WebView/纯图，#115 dkXAy 跳转落地 app 实锤微树空壳零内容事件）前 2~3 秒常零内容事件，旧版被动等事件晋升 = 整窗漏拦；现登记竞速候选即排三针主动自检（+0.5/+1.2/+2.4s，各一次 binder 根查询，阳性证据才晋升），日志「🔄 跳转接管」；②探针阴性快重试——切屏动画期首探阴性（根还属旧应用）后，旧 5s 包级节流在 5s 开屏上等于放弃，现阴性后 3s 窗内改 800ms 快档连探（阳性/晋升即关窗）；③输入法零前台劫持（#114 Sony 实锤：评论框键盘起落把前台追踪反复劫走 → 每 5s 一轮「切换app回归」晋升 + 全量重扫）——键盘是覆盖在真前台应用上的浮层，IME 族包零扫描照旧但绝不写前台追踪；设置/锁屏等真实前台切换照旧",
      "binder 负载：新增最坏 +3 次/跳转（3s 竞速窗 ≈1 次/秒）+ 快重试 ≤4 次/3s（事件流入时 ≈1.25 次/秒）——红线 50 次/秒的 1/40；IME 修复为净减项（晋升循环的周期性探针 + 全量重扫链消失）",
      "测试与验证：ServiceHostTest 新增 S15 app→app 跳转十三项（S15a 竞速自检梯四项：登记/晋升/日志/补扫跳过；S15b 阴性快重试五项：记账/不误晋升/800ms 档放行（旧 5s 档必挂的回归红线）/回归日志/补扫跳过；S15c IME 零劫持四项：基线/不劫持/零晋升日志/设置页负例对照），服务级 48 项全绿；核心 4530 断言零改动全绿；主功能 byte-for-byte（决策核心仅新增 isImePkg 纯函数，订阅/规则引擎零改动）",
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
