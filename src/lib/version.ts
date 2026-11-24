/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.8：事故修复轮——v7.15.7 多窗扫描 binder 风暴致服务被杀（日志
 * 零记录/开屏全不跳）根治（两段式微树门控 + 400ms 节流 + 类型前置过滤）
 * + 日志记录更详细（每根节点明细/入窗时长/多窗补扫打点）+ 开屏提示强制
 * 观看 5 秒（用户指令，v7.15.7 为 3 秒）。 */
export const APP_VERSION = "7.15.8";
export const VERSION_CODE = 85800; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.8→85800）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.8",
    code: 85800,
    date: "2026-11-25",
    tagline: "v7.15.7 升级后日志零记录/开屏全不跳事故修复 + 日志更详细 + 开屏提示强制观看 5 秒",
    notes: [
      "事故根治（v7.15.7 回归，用户实锤：升级后日志零记录、开屏全不跳——服务级瘫痪）：v7.15.7 的多窗扫描在开屏窗的【每轮扫描】里无条件枚举 getWindows() 并对列表里每个窗口调 getRoot()——MIUI/HyperOS 窗口列表常 20+ 窗（状态栏/导航条/灵动岛/系统覆盖层全在列）= 每轮 20+ 次 binder 调用；屏闪型开屏的内容事件经 80ms 节流补扫（12.5 轮/秒）+ 18 档梯子 = 每秒 250+ 次跨进程往返打满服务主线程与 AccessibilityManagerService → 无障碍事件派发超时 → 系统杀服务 → 无日志零跳过整体瘫痪。现在改为两段式：首段只扫活动窗（与 v7.15.6 byte-for-byte 同路径，普通应用零增量零回归）；循环尾部微树门（活动窗 ≤48 节点且零候选——Keep 型「跳过键住覆盖层、活动窗只剩 8 节点空壳」铁签名）五重门控全过才 collectExtraRoots 补窗（400ms 枚举节流 + TYPE_APPLICATION 前置过滤——parcel 本地字段零 binder）",
      "Keep/CSDN 修复能力全保留：覆盖层窗口里的「跳过」键照常可扫可点（探针复活通道 collectRoots(allWins) 同样收敛为节流+过滤口径 + tryScan 微树补扫双保险）；图片跳过键边界闸 imageKeyBoundaryCheck（零文案纯图键不再被通用终闸语义断言误杀）原样保留；角落/边带候选 vid 否决防线原样保留",
      "日志记录更详细（用户指令）：未命中日志新增每根节点明细（多根时「树 8+42 节点 · 根 2」——哪扇窗是空壳、哪扇是广告窗直接可见）与入窗时长（「入窗 3.2s」——晚到跳过键的时序诊断）；多窗补扫启用时打点「🪟 多窗补扫启用（活动窗仅 N 节点 → 补扫同包窗口 M 个）」（每窗一条）——Keep 型覆盖层问题从「识别不到且无日志」变为日志直接可诊断",
      "开屏提示强制观看 5 秒（用户指令）：至少观看 5 秒才可操作「知道了/去上传日志/12 小时不再提示」（v7.15.7 为 3 秒；三按钮禁用 + 倒计时行 aria-live + 遮罩同期让路，到点自动解锁绝不卡死）",
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
