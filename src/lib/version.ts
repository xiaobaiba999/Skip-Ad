/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.5.0";
export const VERSION_CODE = 30500; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "3.5.0",
    code: 30500,
    date: "2026-11-16",
    tagline: "点击复验梯子 · 位置学习 · 服务保活 · 引擎日志上传 · 全新绿色 Logo",
    notes: [
      "根因修复（二次真机反馈：日志显示「已点击跳过」但广告纹丝不动）：v3.4.0 点击后立即熔断扫描链、从不复验——performAction 返回 true 也可能被应用无声吞掉。点击复验梯子：点后 1.2s 复扫同窗，未关 → 换下一个角落候选点 + 强制 dispatchGesture 真实触点重试；同窗点击 ≥3 次未关 → 返回键兜底；窗口切走即记「复验通过」",
      "根因修复（手动点两次也学不下来）：图片跳过键零文本，v3.4.0 学习只认「跳过/关闭」文本。位置学习：手动点小角落/顶部控件即记归一化坐标 + 窗口类名指纹，次启同窗 500ms 手势直点（教一次就会）；安全门：仅广告形态窗内出手，信息流/输入框/长文本页绝不误点",
      "开屏×关闭键代点：顶部小关闭键 + 广告形态树 → 代点（v3.4.0 发现了关闭键却在开屏窗内从不点的遗漏）；角落控件多候选 + 不可点图片经可点父容器命中（点子控件坐标更准）",
      "服务保活（更新/重启后被系统关闭的真机痛点）：前台常驻通知（IMPORTANCE_MIN）+ keepOn 持久化 + KeepAliveReceiver 覆盖安装/重启断连检测 → 高优先级通知一键恢复（点按自动跳无障碍设置，两步恢复）+ 电池优化白名单一键加入；Android 安全设计无法自动重开，已做到系统允许的极限",
      "学习中心新增「上传引擎日志到 GitHub」：60 条日志 + 学习规则 + 设备信息一键直达 Issues（内置加密通道，令牌不出服务端），成为下一版迭代修复依据；Web 端同步上线",
      "全新绿色 Logo：翡翠渐变盾牌 + 深绿闪电（APK 启动器/通知图标 + 应用内 + Web 全端统一）；JVM 49 用例全绿；二十一轮自测：真机复测建模点击被吞 ×19 → 复验重试 ×17 + 返回键兜底 ×2 全恢复 → 100%",
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
      "坐标级点击兜底（L6）：开屏窗内零信号简单树 → 点击标准跳过位（88%宽/7%高），防误点六重约束（开屏窗内/无滚动/无输入框/无关闭键/每次启动一次/守护名单豁免）",
      "点击执行降级链：performAction 失效自动改 dispatchGesture 真实触点；多窗口扫描；开屏窗扫描预算 280→600；决策核心 AdDecisionCore 纯 Java JVM 38 用例；十八轮自测 100%",
    ],
  },
  {
    version: "3.3.0",
    code: 30300,
    date: "2026-11-16",
    tagline: "SDK 广告窗识别 · 热区直断 · 弹窗 v2 · 回弹扩充 · 更新链路修复",
    notes: [
      "SDK 广告窗 className 指纹（穿山甲/优量汇/快手/AdMob/宿主 ad.activity）零文本可判 500ms 直断，直断沉淀指纹次启 300ms 快断",
      "全屏热区识别（≥55% 可点 + 节点≤12 + 无输入框）900ms 直断；弹窗广告 v2（窗期独立 + 角标扩充 + Dialog/营销话术 + 覆盖层内容突发检测）",
      "链式跳转回弹扩充（微信/QQ/支付宝/美团/知乎/小红书/B站/浏览器）；更新链路修复（v3.2.0 因发版脚本仓库名拼写错误未发布成功）",
      "局限（v3.4.0 修复）：第一方图片开屏（应用自建广告系统）窗口类名非 SDK、跳过键纯图片零文本——本版识别手段零信号",
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
