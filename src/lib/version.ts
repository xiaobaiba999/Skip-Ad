/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.7.0";
export const VERSION_CODE = 30700; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "3.7.0",
    code: 30700,
    date: "2026-11-16",
    tagline: "手势能力修复 · 多候选轮换 · 学习位置前置 · 返回键复验 · 心跳保活",
    notes: [
      "头号根因修复（四次真机反馈 issue#10：喜马拉雅「已点击跳过×4」广告仍不关，手动点同一按钮立刻生效）：无障碍配置缺 canPerformGestures=true——dispatchGesture「手势真实触点」从未真正触达屏幕，复验重试全部无效。已补声明；另加 flagIncludeNotImportantViews（被标不重要的跳过键从此在无障碍树里可见，微博角落键扫不到的帮凶）",
      "手势点击修复：路径单点零长度（只有 moveTo）→ 部分 ROM 不触发点击——改 lineTo 非零路径 + 40ms 起始延迟 + 坐标边界钳制 + GestureResultCallback 取消诊断（「派发成功但被系统中断」不再静默）",
      "多候选轮换：文本跳过键只点第一个候选、复验 4 次盲点同一坐标（广告图内常嵌假「跳过」文本）→ 收集全部候选（≤4），第 N 次出手点第 N 个；学习位置前置到文本候选之前，且文本键手动点击【同时学位置】（你手指点通过的位置最可靠）+ 越界坐标拒学（真机日志 (63%,102%) 越界白学）",
      "微博直断修复：陀螺仪文案 120ms 抢先返回键改「点击优先、返回键兜底」；返回键被广告 Activity 拦截后 600ms 复验不熔断（同窗仍在则重开扫描链继续点击梯子）；角落/关闭键树上限 40→150（广告窗树常大，真角落键被挡在门外）",
      "无障碍持久化（清理后台重进又要手动开）：onUnbind 保留实例等待重绑（GKD 同款）+ 15 分钟心跳自检闹钟（进程被杀但非 force-stop 的 ROM 上持续触发）+ 打开 App 自动拉活循环重试（每 5s 直到成功，等待加长至 5s + 二次拉起）+ 电池白名单醒目横幅与厂商 ROM 三步指引（任务锁定/省电无限制/自启动）；JVM 83 用例全绿（新增 N 系列 12 例）；二十五轮自测：真机日志建模（手势能力缺失 ×21）92.4% → 全恢复 100%",
    ],
  },
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
      "误报防护：厂商系统工具包（安全中心/系统管家）不再误开弹窗识别窗（真机日志：securitycenter 系统弹窗被误处理）；JVM 70 用例全绿（新增：根窗口安全门 6 例 / 毒指纹断源 3 例 / 坐标轮换 3 例 / 可学形状 5 例）；二十四轮自测：真机事故建模（毒指纹主页按返回 ×14 + 入口窗返回退出 ×9）91% → 根窗口安全门+毒指纹清理全恢复 100%",
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
