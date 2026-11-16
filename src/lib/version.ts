/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.6.0";
export const VERSION_CODE = 30600; // major*10000 + minor*100 + patch，随版本单调递增
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

/** 「关于」页仅展示最近三个版本 */
export const VERSION_HISTORY: VersionEntry[] = [
  {
    version: "3.6.0",
    code: 30600,
    date: "2026-11-16",
    tagline: "根窗口安全门 · 毒指纹清理 · 点击原坐标 · 无障碍自动拉活",
    notes: [
      "根因修复（三次真机反馈：进入喜马拉雅被「模拟人操作」退出到桌面）：上传日志实锤两条毒链——① v3.5.0 窗口指纹学习把主页/弹窗也学成「广告窗」，此后每次进入 500ms 内就在主页按返回键 → 喜马拉雅拦截返回弹「再按一次退出到桌面」→ 复验扫描再按 → 真退出。断源：SDK 窗资格改为只认【当前窗口类名】（12s TTL 废除）；指纹只沉淀真广告窗类；启动自动清理历史毒指纹（只清窗口类规则，位置/文本规则全保留）",
      "根因修复②：应用入口窗（冷启动开屏/回前台首窗）上按返回键 = 退出应用——根窗口安全门接线：入口窗禁一切返回键兜底（决策核心安全门 + 服务侧双保险），只走点击梯子（文本→学习位置→×关闭键→角落候选→三位置坐标轮换），每窗 6 次预算用尽优雅收手；信息流页面出现「摇一摇」文案不再误返回（L3 信息流安全门）",
      "点击修复：「已点击跳过但广告纹丝不动」根因之一是手势点在了可点祖先中心（常为全屏容器）——v3.6.0 改点【原标签控件】坐标（点得准）；学习形状扩至底部跳过键（部分应用在右下角）",
      "无障碍持久化（清理后台后服务就停了）：打开 App 自动检测「系统设置仍登记但进程已死」→ startService 拉起进程促系统完成待处理的无障碍重绑（多数 ROM 无需手动去设置）；onTaskRemoved 后台清理即时提醒 + 15s 自检闹钟 + stopWithTask=false；仍失败才引导两步手动恢复（学习数据全保留）",
      "误报防护：厂商系统工具包（安全中心/系统管家）不再误开弹窗识别窗（真机日志：securitycenter 系统弹窗被误处理）；JVM 70 用例全绿（新增：根窗口安全门 6 例 / 毒指纹断源 3 例 / 坐标轮换 3 例 / 可学形状 5 例）；二十三轮自测：真机事故建模（毒指纹主页按返回 ×14 + 入口窗返回退出 ×9）91% → 根窗口安全门+毒指纹清理全恢复 100%",
    ],
  },
  {
    version: "3.5.0",
    code: 30500,
    date: "2026-11-16",
    tagline: "点击复验梯子 · 位置学习 · 服务保活 · 引擎日志上传 · 全新绿色 Logo",
    notes: [
      "根因修复（二次真机反馈：日志显示「已点击跳过」但广告纹丝不动）：v3.4.0 点击后立即熔断扫描链、从不复验。点击复验梯子：点后 1.2s 复扫同窗，未关 → 换下一个角落候选点 + 强制 dispatchGesture 真实触点重试；窗口切走即记「复验通过」",
      "根因修复（手动点两次也学不下来）：图片跳过键零文本，v3.4.0 学习只认「跳过/关闭」文本。位置学习：手动点小角落/顶部控件即记归一化坐标 + 窗口类名指纹，次启同窗 500ms 手势直点（教一次就会）；安全门：仅广告形态窗内出手",
      "开屏×关闭键代点；角落控件多候选 + 不可点图片经可点父容器命中",
      "服务保活：前台常驻通知 + keepOn 持久化 + KeepAliveReceiver 覆盖安装/重启断连检测 + 电池优化白名单一键加入",
      "学习中心新增「上传引擎日志到 GitHub」；全新绿色 Logo；JVM 49 用例全绿；二十一轮自测 100%",
    ],
  },
  {
    version: "3.4.0",
    code: 30400,
    date: "2026-11-16",
    tagline: "角落图片跳过键 · 坐标级点击兜底 · 手势降级 · 决策核心可单测",
    notes: [
      "根因修复（微博/喜马拉雅等主流应用仍拦截失败）：第一方图片开屏——窗口类名是应用自己的 Activity（非 SDK 指纹）、跳过按钮纯图片零文本、倒计时烧在图里，v3.3.0 全部识别手段零信号",
      "角落无字小可点控件识别（L4）：顶部 14% 高、贴右/左缘、小面积可点、无任何文本 → dispatchGesture 手势点击其中心 650ms（图片跳过键正解）",
      "坐标级点击兜底（L6）：开屏窗内零信号简单树 → 点击标准跳过位，防误点六重约束",
      "点击执行降级链：performAction 失效自动改 dispatchGesture 真实触点；多窗口扫描；开屏窗扫描预算 280→600；决策核心 AdDecisionCore 纯 Java JVM 38 用例；十八轮自测 100%",
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
