/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.12：更新存活链根治轮（用户实锤：更新安装新版后「日志不记录 +
 * 开屏全不跳」常现）——安装前同步落盘（commit 防 apply 截断）+ 更新后
 * +25s 预排自检闹钟（ROM 吞 MY_PACKAGE_REPLACED 兕底）+ SmartRevive
 * 终判 +30s 复活梯子（一次失败不再弃）+ 断线时长可见化（重连日志回答
 * 「日志为什么断流」）+ 服务已死时的日志通道（覆盖安装时刻不再黑洞）。
 * v7.15.11 全部能力保留（锁屏/切换app根治/多点治理/插屏退避）。 */
export const APP_VERSION = "7.15.12";
export const VERSION_CODE = 86200; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.12→86200）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.12",
    code: 86200,
    date: "2026-11-25",
    tagline: "更新存活链根治（安装前落盘+复活梯子+断线可见化）+ B站系补全（v7.15.11 全部能力保留）",
    notes: [
      "更新存活链四件套（用户实锤：更新安装新版后「日志不记录 + 开屏广告都跳不过」常现——覆盖安装杀进程后无障碍重绑时机失控，ROM 还可能吞/延迟 MY_PACKAGE_REPLACED 广播与心跳闹钟）：①安装前同步落盘——拉起安装器前把内存态【同步 commit】进 prefs（旧版 apply 异步写会被安装杀进程截断，最后 ≤15s 日志/战绩全部丢失 = 用户看到「日志不记录」）；②更新后 +25s 预排自检闹钟——RTC 闹钟跨 APK 替换存活，ROM 吞/延迟 MY_PACKAGE_REPLACED 广播时兑底触发踢链；③SmartRevive 终判 +30s 复活梯子——覆盖安装踢链三轮失败不再「一次失败即弃」，重绑常在安装后 10~30s 才完成，梯子并入 5 分钟心跳循环持续自愈/提醒（自检路径维持原节奏防密集循环）；④断线时长可见化——断线首次检出即记时戳（进程重启间 prefs 持久），重连日志给出「🔌 服务重连成功（此前离线 X 分 Y 秒）」，「日志为什么断流/广告为什么没跳」在日志里直接可回答",
      "日志黑洞根治：服务已死时的日志通道 appendLogStatic（instance==null 时 prefs 直写 commit，实例活着路由实例路径防内存态覆盖竞态）——「📦 覆盖安装完成（vX.Y.Z）」无论服务死活都入档（日志考古锚点：用户反馈「更新后日志不记录」时能直接看到安装时刻与服务自检轨迹）；onDestroy 落盘 apply → commit 同步写（安装替换杀进程前最后一搏）",
      "B站系补全：分应用文本规则表新增 com.bilibili.app.in（哔哩哔哩国际版，与国内版 tv.danmaku.bili 同源广告 SDK）——视频开屏「跳过 5」胶囊本身已由倒计时正则全局命中（允许空格+数字），分应用精确等值零误点面加固；B站零日志零跳过的主因即上述更新存活链（服务死透期间事件全丢）",
      "测试与验证：ServiceHostTest 新增 S14 更新存活链六项（S14a 安装前 preserveStateForUpdate 同步落盘/S14b 服务已死 appendLogStatic prefs 直写/S14c 重连日志给出离线时长/S14d 断线时戳清零/S14e 复活梯子排针 AlarmManager 记账核验），服务级 35 项全绿；核心 4530 断言零改动全绿；主功能 byte-for-byte（决策核心/订阅零改动，规则引擎仅加分应用规则行）",
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
