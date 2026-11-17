/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "3.13.0";
export const VERSION_CODE = 31300; // major*10000 + minor*100 + patch，随版本单调递增
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

/** 「关于」页仅展示最近三个版本（硬约束：数组只保留最新 3 条，
 *  旧版本内容归档在 GitHub Releases 与 git 历史） */
export const VERSION_HISTORY: VersionEntry[] = [
  {
    version: "3.13.0",
    code: 31300,
    date: "2026-11-17",
    tagline: "验证码横幅四重防护 · 息屏休眠 · 夜间心跳降频",
    notes: [
      "真机事故修复（接收验证码时顶部 heads-up 横幅被引擎点掉）：四重防护——① 短信/彩信/来电类覆盖层包（mms/smsextra/messaging/incallui）事件入口直接丢弃，绝不扫描绝不学习；② 树内出现「验证码/校验码/动态码/动态密码」文案的窗口绝不扫描绝不点击（含应用内自绘提示条）；③ 手势真实触点派发前探测 SystemUI 顶部小窗，触点被横幅覆盖即拒发手势、自动降级无障碍代点（不经屏幕坐标，绝不碰横幅）；④ 横幅在场时绝不派返回键（BACK 会先关横幅）",
      "待机功耗治理（夜间待机耗电偏高根因：v3.9.0 心跳 5 分钟 setExactAndAllowWhileIdle 一晚把设备从 Doze 唤醒 96 次）：息屏休眠门——息屏时不扫描/不点击/不学习（息屏无广告可跳，来电/闹钟窗零误点，手势派发息屏熔断双保险）；心跳自适应——亮屏 5 分钟精确闹钟（发现快），息屏改 30~45 分钟窗口式非精确闹钟（系统批量调度进 Doze 维护窗，绝不唤醒设备）",
      "issue#16 日志实锤整治：安全中心应用详情页被学出位置 (91%,9%)（下次进详情页 500ms 直点=在权限管理页乱点）——OEM 工具包（miui/securitycenter/systemmanager/safecenter/permissionmanager/finddevice）位置学习永久拒收 + 启动时自动清理已误学规则；越界位置拒绝日志刷屏（(90%,103%) 30 秒 6 条）同包 30s 去重",
      "误触观察口径修正（issue#16/#17：误触观察 48~60 条几乎全是正常秒跳被误记）：「快于人类反应」启发式移除——熟练用户等在跳过键上 500ms 内手点完全正常；仅保留全屏热区观察（真信号）",
      "学习中心「分应用统计」整体移除（用户反馈看不懂还乱）：App 只留引擎日志与能耗面板；byApp 统计仍在后台累计仅随日志上传诊断。关于页修复：版本卡曾累积 7 张违背「只留三版本」红线，已回到恰好 3 张",
      "JVM 139 用例全绿（新增 v3.13.0 三重防护 31 例：短信包 10 + 验证码标记 7 + 树探测 7 + OEM 工具包 7）",
    ],
  },
  {
    version: "3.12.0",
    code: 31200,
    date: "2026-11-17",
    tagline: "纯跳过引擎 · 链式拦截整体移除",
    notes: [
      "issue#14 日志实锤：v3.11.0 三重放行安全门仍拦下了两类用户主动跳转——NFC 碰一碰拉起的支付链（securitycenter→支付组件）与「分享到微信」，被 1.5s 内连按 3 次返回键弹回（日志 ⛓×3）。用户意图场景无法穷举，任何安全门都有漏网时刻",
      "链式拦截机制【整体移除】：reboundChainJump（跨应用弹回）/ reboundLanding（同包落地页弹回）/ chainBounceVerdict 三重放行 / isChooserHint / isAdTarget+AD_TARGET_HINTS 广告目标名单 / adFeatureUntil 广告特征追踪状态全部删除。三轮事故（#12 闪退 → #13 分享被拦 → 本次 NFC 支付被拦）证明：回弹收益远小于代价，同类头部应用并无此功能",
      "引擎职责回归纯跳过（同类头部应用同款哲学）：只点「跳过/关闭」类控件，绝不对抗用户的任何跳转；点商品/文章详情跳内置 WebView 页不再被误弹回",
      "issue#14 日志另一发现：connectivity 连接检查窗 2 分钟内被循环误点 ×10——并入系统噪音过滤（connectivity/captiveportal 家族），系统连接诊断页绝不扫描",
      "界面同步精简：撤下「跳转回弹」开关与统计卡；能耗画像移除回弹字段；反馈类型移除「正常浏览被误弹回」",
    ],
  },
  {
    version: "3.11.0",
    code: 31100,
    date: "2026-11-17",
    tagline: "链式回弹用户意图安全门 · 分享不再被拦",
    notes: [
      "issue#13 实锤修复：用户在安全中心主动「分享到微信」被链式回弹连弹 3 次踢回原应用。参考同类头部应用的安全哲学——回弹绝不与用户意愿对抗，只拦「无人在场时广告自己拉起」的跳转",
      "三重放行安全门：① 用户 2.2s 内真实点击（分享/登录按钮，引擎代点不计）→ 放行；② 系统分享选择器（Resolver/Chooser/intentresolver 全兼容）8s 内出现过 → 用户亲手挑的目标放行；③ 同「宿主→目标」12s 内已弹回过一次又立刻拉起 = 用户重试 → 放行",
      "同包落地页弹回同步加用户点击门：点商品/文章详情跳内置 WebView 不再被误弹回",
      "分享选择器（intentresolver）加入系统噪音过滤：分享面板全屏热区「疑似误触」观察归零",
      "学习规则明细撤出 App UI（用户反馈“太多了很乱”）：学习引擎照常静默工作，首页保留规则计数，明细仅随引擎日志上传进 GitHub 诊断",
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
