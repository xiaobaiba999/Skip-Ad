/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.1";
export const VERSION_CODE = 79100; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "7.9.1",
    code: 79100,
    date: "2026-11-20",
    tagline: "任务状态机轮：重复点击/误触/合法重试三难根治 + 订阅反饥饿 + 视频静默画像归一",
    notes: [
      "广告任务状态机（用户实锤三类真机事故根治：A 第一次成功后第二击误触普通按钮 / B 第一次成功后重复关闭 / C 第一次失败第二次才成功——C 是合法重试绝不误杀）：每包每窗唯一任务 ID（窗代+序号）· SUCCESS/FAILED/CANCELLED 不可逆终态（拒收该任务后续一切点击请求与过期回调）· 终态后同窗新广告须过 2.2s 冷却闸（广告 detach 残留期/反馈面板期/跳转动画期的「广告」语境+「×/关闭」候选不是新广告——第二击断源）· 反馈面板期 FEEDBACK 独立流程（绝不对原广告再执行关闭）· 视频新键预算刷新 = 开新任务（能力零回退）",
      "就地 SUCCESS 验证（Activity 不变的插屏/覆盖层/Dialog 广告旧版成功信号永不触发）：出手后两轮零候选复验（≥350ms 独立两轮——树刷新抖动不误判）→ 不可逆 SUCCESS；插屏路径 >12s 窗龄场景由插屏扫描自身承担验证；单轮零只观察不补刀（页面变化无法确认 → 绝不对旧坐标第二击）",
      "反馈面板前置闸插屏路径接线（v7.9.0 实锤链 18:39:02~18:39:04 根治：inter_close 首击成功 → 面板弹出 → 面板/残留 UI 的「广告」语境词 + 面板自身「×/关闭」候选再次过闸 → 2 秒后第二击落在普通按钮）：maybeInterstitialScan/videoPoll 双路接入 handleFeedbackSheet——面板在场期绝不出手只代点「取消」，插屏路径此前完全盲",
      "过期回调全面验活（旧任务/旧坐标/过期回调三限制）：复验针带任务 ID（窗代+序号双因子）· 手势取消回调补扫带代际+任务验活（旧版无任何护栏）· 返回键 600ms 复验带代际+任务验活 · 视频轮询针终态降频 1.5s→3s（广告已关后继续全量复扫再出手的根治——观察在岗出手拒收）",
      "订阅命中 0 根治（反饥饿 + 七分类统计）：needSub 饥饿断源（v7.5.0/v7.7.0 探针词扩容后探针先中 → hits 非空 → matchSources 整窗不被咨询——命中计数在收集侧故为 0）→ 每窗首次扫描无条件咨询一次订阅（v7.3.0 行为恢复，成本每窗一次 ≤400 节点 DFS）；七分类统计（未加载/应用不匹配/页面选择器不匹配/规则命中/被安全闸拦截/点击失败/点击成功——订阅页与上传日志直接可读归因，不再把所有未命中都统计为订阅未命中）",
      "三 app 漏检根治：ting.android（MainActivity 8 节点零内容事件——静默语义归一：零事件 = 窗口全程静默（SurfaceView 视频铁证），旧版传 -1 → videoLike 永不成立 → 20s 观察窗/轮询针永不开启 → 长视频开屏 >12s 露头按钮整窗漏拦）· taobao（TMSActivity 营销容器收编 SDK 广告窗指纹——WebView+窗指纹双证据接通图标钮路径/闸⓪第三路）· 微信朋友圈（订阅反饥饿后远程 GKD 规则重新参与——mm SnsTimeLineUI 朋友圈广告关闭规则不再被探针命中饿死）",
      "24 套件 6197 项 JVM 断言全绿（+83：AdTaskTest 新套件——终态不可逆/状态转移真值表/反馈面板独立流程/合法重试保护/重试前置五要件/冷却闸与视频新键解禁/两轮零候选就地确认/轮询终态降频契约）；性能契约全保持（零新增扫描档/零新增事件订阅/终态降频反而降功耗——每窗一次订阅咨询与 v7.3.0 相同）· APK 844KB · VC 79100 覆盖安装即升级",
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
