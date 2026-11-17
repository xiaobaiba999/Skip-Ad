/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "5.0.0";
export const VERSION_CODE = 50000; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "5.0.0",
    code: 50000,
    date: "2026-11-17",
    tagline: "小程序保护（抖音团购券被拦截根治）· 内置规则扩到 54 款 · 订阅形状门",
    notes: [
      "小程序/团购容器保护（用户实锤：抖音点团购券拉起小程序，加载页「跳过广告」文案被当真广告关掉、用户被弹回）：新增 L0 守卫 MiniProgramGuard——抖音全部小程序/电商容器（MiniAppActivity / CommercializeContainerActivity / PrivilegeDetailActivity / EcPromotionActivity / UnionPromotionActivity / LynxContainer / bae / ecom）+ 微信 AppBrandUI + 支付宝 Nebula + QQ 小程序 + 通用 miniapp 标识，容器内绝不扫描绝不点击绝不学习；树标识兑底（树内 miniapp/appbrand/nebula vid 或「小程序」标题）接住类名未知的漏网容器；issue#45 实锤的 MiniAppChildProcessMultiInsActivity0 同样命中",
      "五层纵深防御：事件入口 → 扫描链调度 → 扫描执行 → 决策核心 walk/decide → 出手前终门，全部验容器身份——规则引擎/订阅/探针路径不经决策核心，由出手前终门兑底",
      "内置规则库扩到 54 款必备应用（应用商店热门榜全覆盖：抖音/快手/淘宝/天猫/拼多多/美团/饿了么/四大厂商应用商店/滴滴/钉钉等），抖音家族限定 SplashActivity（与容器守卫双保险）",
      "订阅规则形状门（与主引擎同源）：无 activityIds 限定的任意界面规则，目标宽度也须过形状门——全宽行/横幅（列表项模板行、「跳过广告 查看详情」banner）绝不代点，内置与社区 GKD 订阅同受益",
      "升级零门槛：同包名（com.zai.adshield）同签名（adshield.keystore），覆盖安装即升级，无障碍授权无缝保留（服务组件名不变）；150 项 JVM 断言全绿（守卫 29 + 决策管线 22 + 订阅 42 + 回归 15 + 6 机型矩阵 42）",
    ],
  },
  {
    version: "4.5.0",
    code: 40500,
    date: "2026-11-17",
    tagline: "乱点根治（李跳跳式形状门）· 系统开关彻底解耦 · 原生探针提速 · 安全加固",
    notes: [
      "乱点根治三重防线（issue#42-44 实锤抽屉菜单被点）：① 文本命中形状门——标签宽 ≤55% 屏（自身可点放宽 70%），全宽行/横幅绝不点；② 竖列菜单检测 isMenuListRow——候选祖先行与 ≥3 兄弟行同列同宽竖排 = 抽屉/菜单列表行一票否决（李跳跳形状白名单方法论，rule 与 walk→decide 两条路径全覆盖）；③ 你点开的弹窗/抽屉 30s 零动作（2.5s 意图窗口）",
      "系统开关与 App 开关彻底解耦（用户核心诉求）：系统无障碍【一直开着永不碰】（零 disableSelf），卫士的开关由 App 内/通知栏一键控制——暂停即全停（零扫描零点击）、恢复即全开，全程绝不去系统设置看 10 秒强制确认；打开 App 自动深挖拉活",
      "弹窗屏蔽开关「自动弹回」竞态根治：UI 写 prefs 与服务内存态落盘互相覆盖（15s 延迟落盘）——开关改走 setStateBool 专用通道（服务内存本体唯一数据源）",
      "提速：原生文本探针（findAccessibilityNodeInfosByText 系统原生搜索，无节点预算——H5/WebView 深树 500+ 节点 DFS 预算耗尽漏扫的兑底，李跳跳同款）+ 窗口事件内联首扫（省一跳 handler 5~16ms）；「跳过/跳過」双探针复用全套规则+形状门，零新乱点面",
      "日志「android」歧义修复：me.ele.android（饿了么）等包不再显示为 android（战绩榜不再误导）；反馈面板收起后杀本窗扫描链（广告关了不点第二下）；安全加固：WebView URL 白名单（JS 桥零远程暴露）/ 下载源域名白名单 / 订阅源强制 https / 反馈频控",
    ],
  },
  {
    version: "4.4.0",
    code: 40400,
    date: "2026-11-17",
    tagline: "露头就秒大提速 · 恢复防护零触摸 · 功耗再降",
    notes: [
      "倒计时门控「开闸瞬间即点」：设门控时同步排 +40ms 精确补扫针——此前门控说 4s 后出手、实际要等下一固定档扫描（最慢再拖 5s+），喜马拉雅/keep 实测快 4~5 秒，快于人眼",
      "点击后 450ms/900ms 快速复验针（点击冷却 900→600ms）：「点了没反应」场景人手 300~500ms 就补第二刀，引擎此前要等 1.2s+ 固定档",
      "H5 开屏按钮晚到救回通道：H5 广告按钮 1~2s 才渲染、内容页早退曾把补扫全让路（「有时候触发不了」元凶）——杀链 400→2000ms + 广告/学习/Dialog 窗绝不杀 + 杀链后内容事件 900ms 节流降频放行",
      "恢复防护零触摸：服务真实绑定态判定（僵尸实例不再假报守护中）+ 深度拉活 6s 每半秒踢一次系统重绑 + 打开 App 即自动拉活——「点恢复防护跳系统设置看 10 秒强制警告」根治",
      "功耗再降：息屏状态广播缓存（热路径零 IPC）· 非前台内容事件一步丢弃 · 晚档扫描预算收紧 · 复验换候选提速",
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
