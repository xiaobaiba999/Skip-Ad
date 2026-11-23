/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.12.0";
export const VERSION_CODE = 82000; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.12.0→82000）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.12.0",
    code: 82000,
    date: "2026-11-23",
    tagline: "卫士卡死根治 + 息屏/回切拦截修复 + 上传连接重试 + 开屏三按钮",
    notes: [
      "卡死根治：应用白名单改异步加载——旧版进入卫士/点按钮时同步枚举全机应用（300+ PackageManager IPC，慢设备上 JS 线程被冻结分钟级——「卡死到只能重装」元凶）；现在骨架即时出 + 勾选增删本地即改即显 + 慢机自动重试（10s 看门狗 + 至多 3 次）",
      "回切拦截修复：息屏十几分钟再亮屏、或切去别的应用十几分钟后回来第一条广告拦不住——亮屏/解锁瞬间即时踢 AMS 重绑（不等五分钟心跳）+ 解锁首事件竞速实测放行 + 前台竞速晋升（窗口事件与活动窗口查询赛跑漏登记时，内容事件确认前台后按新进入补扫）",
      "上传连接重试：日志上传连接阶段失败自动重试三次（上传服务间歇可达——单次超时改为三试大幅提成功率）；自动上传失败后 30 分钟即可再试（不再干等 8 小时）；失败提示如实注明已重试",
      "开屏提醒三按钮：进入 App 弹出提醒卡——「知道了」（下次进入仍提醒）/「24 小时内不再提示」/「去上传日志」（直达引擎日志卡，仅在你主动点击时才上传）；每次进入都提醒（60 秒短窗仅防旋转重复弹）；帮助页去冗余精简",
    ],
  },];

/** 三段式 tag（vX.Y.Z）→ 项目公式 versionCode（v7.10.0 统一版本规则：
 *  Web 面板更新判定只比 versionCode——versionName/tag 字符串比较已随
 *  compareVersion 删除；非三段 tag（历史 plus 系）→ 0（无效——不判新）。
 *  与 scripts/update_manifest.py version_code_of_tag 同语义。 */
export function versionCodeOfTag(tag: string): number {
  const m = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(String(tag || "").trim());
  if (!m) return 0;
  return Number(m[1]) * 10000 + Number(m[2]) * 1000 + Number(m[3]) * 100;
}
