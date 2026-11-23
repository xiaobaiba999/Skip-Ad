/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.1 修复说明：v7.15.0 发布会话曾把本文件误写为两万多行重复块且
 * 丢失 APP_VERSION/VERSION_CODE 等导出（脚本事故——Web 编译与
 * scripts/check-version-sync.sh 均会失败）；本轮恢复到单条目规范形态。 */
export const APP_VERSION = "7.15.1";
export const VERSION_CODE = 85100; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.1→85100）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.1",
    code: 85100,
    date: "2026-11-24",
    tagline: "息屏/解锁重入根治：锁屏污染前台追踪 → 补扫走偏 + 引擎致盲（真机日志实锤）",
    notes: [
      "亮屏/解锁视作重新进入（根治版）：窗龄重置 + 节流清空 + 立即主动补扫——扫描日志可直接核验「🔓 亮屏/解锁视作重新进入（应用名）」",
      "补扫目标校正：解锁瞬间不再补扫锁屏自己的树（旧版实锤「开屏未命中（systemui）」六连发），校正回息屏前的真实应用（RearmPlanner 纯函数 + lastAppPkg 真实应用追踪）",
      "前台追踪污染根除：锁屏宿主（systemui/keyguard）顶掉真实应用导致内容事件被前置过滤吞掉——引擎致盲根除点",
      "亮屏而锁屏还在场时绝不出手（坐标点击不会落到锁屏上——误触红线不破），补扫推迟到真正解锁瞬间",
      "重入重置与真实「进入应用」同语义：收手态/倒计时锚点/触点轨迹/三张包级节流表全量镜像重置；JVM 4348 断言全绿（新增 RearmPlannerTest 34 项）",
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
