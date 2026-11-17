/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "5.2.0";
export const VERSION_CODE = 50200; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "5.2.0",
    code: 50200,
    date: "2026-11-18",
    tagline: "10 秒警告根治（issue#56）· 15s 自适应拉活 · 保活体检全品牌直达 · 安全加固",
    notes: [
      "issue#56 根因修复（Redmi 25060RK16C · Android 16 每次退后台/覆盖安装都要看 10 秒系统警告，Xiaomi 15 不用）：HyperOS 清理后台/覆盖安装会把服务从系统「已启用无障碍」登记中移除——登记被吊销后任何应用都无法编程恢复，只能经系统设置重开并撞上厂商防诈的 10 秒倒计时确认。Xiaomi 15（旗舰策略宽松）登记保留、进程重启即自动重绑，所以那位用户从不需要重开",
      "旧版三处痛点放大器全部消灭：①「待恢复」通知点按不再无条件直跳系统设置——改为先在 App 内 15 秒自适应深挖拉活（前 2s 每 300ms 快踢 AMS、后段 500ms，v4.4.0 固定 6s 在低端机冷启动场景不够），真被吊销才直达服务开关详情页；②拉活循环每轮复查登记，HyperOS 中途吊销立即止损返回；③覆盖安装不再预假设「系统必然已关闭」——广播窗口内踢链三轮自愈，能自动恢复的零打扰",
      "新增「保活体检」卡片（防吊销根治）：三项自动检测（服务绑定/通知权限/电池白名单）+ 品牌自适应厂商直达（小米自启动+省电策略、华为/荣耀启动管理、OPPO/一加/realme 自启动、vivo/iQOO 后台高耗电、三星从不休眠、魅族后台管理——近 5 年全品牌候选链逐个 resolveActivity 校验，全部失败回退应用详情页）；通知两态化：登记仍在→「打开卫士自动恢复（无需系统设置、无 10 秒等待）」，吊销→「直达服务开关（明示 10 秒安全确认属正常）」",
      "无障碍登记移除遥测：ContentObserver 监听系统登记变化，吊销瞬间打点日志并排自检——未来日志能直接回答「服务什么时候被谁关的」；同态通知 10 分钟冷却（5 分钟心跳不再反复提醒）",
      "附带修复：服务启动横幅硬编码 v4.5.0 文案陈旧 bug（版本号改为运行时动态注入，永不陈旧）；登记判断改 ':' 逐段精确解析（子串误报绝杀）",
      "安全加固（与功能同步）：KeepAliveReceiver 增加 signature 级私有广播权限保护（第三方无法伪造 SELF_CHECK 广播骚扰通知/耗尽日志上传配额；系统广播与自家拉活闹钟不受影响）；保活深链候选全部为硬编码 OEM 系统包注册表（零任意组件注入面）+ manifest queries 显式声明可见性；427 项 JVM 断言全绿（新增 106 项 RestoreGuard 专项：品牌归一/候选安全性/决策状态机不变量——登记仍在绝不引导去系统设置）",
    ],
  },
  {
    version: "5.1.1",
    code: 50101,
    date: "2026-11-17",
    tagline: "支付宝 XRiver 小程序容器保护 · 非主流开屏攻坚（5.1.0）",
    notes: [
      "支付宝新一代跨端容器 XRiverActivity 胶囊「关闭」被开屏×关闭键代点（用户被踢出小程序，issue#51-55）——xriver 入支付宝容器表 + 胶囊特征树标识双兜底，容器内绝不扫描绝不点击绝不学习；321 项 JVM 断言全绿",
      "（5.1.0）非主流 App 开屏攻坚：美团 DspActivity/DynamicDialog/自建 AdsActivity 指纹接入、零候选退避、支付页零扫描、内置规则扩到 106 款",
    ],
  },
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
];
