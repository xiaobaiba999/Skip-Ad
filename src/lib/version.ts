/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.14：后台丢功能根治轮（用户实锤：「卫士挂后台长时间后容易丢失
 * 其功能作用，还要重新退出进一下；日志还是经常性的不进行记录，开屏
 * 广告跳过的功能也丢失了」）——死透快档闹钟（setAndAllowWhileIdle，
 * App Standby/doze 不再推迟数小时）+ keepOn 解耦（踢链自愈无条件跑，
 * 保活提醒只管通知）+ 自检复活快梯 + QQ 营销 vid 证据（#119「未出手：
 * 门控/时序」根治）+ RichMediaActivity 入 SDK 窗表（#118 网盘插屏）。
 * v7.15.13 及之前全部能力保留。 */
export const APP_VERSION = "7.15.14";
export const VERSION_CODE = 86400; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.14→86400）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.14",
    code: 86400,
    date: "2026-11-25",
    tagline: "后台丢功能根治（死透快档+keepOn 解耦+营销 vid 证据）（v7.15.13 全部能力保留）",
    notes: [
      "后台长时间丢功能根治（用户实锤：「卫士挂后台长时间后容易丢失其功能作用，还要重新退出进一下；没开启自启动状态下」+ #116 日志考古：16:36→21:58「服务已启动」断流 3 小时 45 分且「自愈/拉活 0」）：①死透快档闹钟——服务死透后的下一跳闹钟原为 setWindow 窗口式，App Standby 深桶/厂商省电管控下被推迟数小时（断流 3h45m 根因），现死透+亮屏改 setAndAllowWhileIdle +2 分钟（doze 中也准点触发——用户正在用手机却漏拦广告的每一秒都是伤害）；服务活着 30 分钟窗口式不变；死透+息屏维持 30 分钟窗口式（夜间无广告场景不耗电）；②keepOn 解耦——旧版「保活提醒」开关关闭时 onUnbind/onTaskRemoved 直接跳过排闹钟 + KeepAliveReceiver 直接 return，踢链自愈被连坐闸死（服务死透后零自愈），现闹钟链无条件排、SmartRevive 踢链自愈无条件跑（零打扰：getEnabledAccessibilityServiceList 触发 AMS 重绑无任何用户可见输出），keepOn 只管通知打扰（notifyAlert 内置闸）；③自检踢链终判仍死也排 +30s 复活快梯（此前仅覆盖安装场景排——厂商省电管控下 AMS 重绑滞后不再干等下一档闹钟）",
      "QQ 型纯图开屏根治（#119 用户实锤设备 mobileqq「图片键候选 角落4+泛位3（未出手：门控/时序）」三连）：小米营销纯图开屏 vid:mi_×2+vid:mhh×2、无角标无热区非 SDK 窗类名、树 112>60 把 L4 广告形态确认门挡死——真跳过键就在角落候选里却永远轮不到出手；现开屏窗内营销 vid ≥2 节点 = 广告容器证据（splashLive 限定 + ≥2 门槛双收口：聊天窗等非开屏场景零置位、单个前缀撞车不成立），纳入 strongAdForm/hasActionableCand 四处判定点",
      "百度网盘插屏根治（#118 vivo 实锤：RichMediaActivity 12 分钟 16 轮「插屏未命中」——整窗无指纹=零 vid-close 零返回兜底）：richmedia 入 SDK 广告窗类名表（富媒体插屏通用命名，与 interstitial 同族）",
      "测试与验证：ServiceHostTest 新增 S16 六项（死透快档 allowWhileIdle 排针/keepOn=false 时 onUnbind 仍排闹钟/实例保留待重绑/解绑日志在场/onTaskRemoved 闹钟照排/息屏夜间窗口式口径不回归），服务级 54 项全绿；核心新增 RandGraphicSkipTest S6 营销 vid 证据十一项（QQ 树 1:1 复刻：双 vid 证据成立/L4 放行角落图片键/单 vid 不成立反向钉死/非开屏窗永不置位/零候选退避联动），核心 4540 断言全绿；主功能 byte-for-byte（决策核心仅新增营销 vid 证据与 richmedia 类名，出手安全闸全链不变）",
      "binder 负载核算（红线 <50 次/秒保持）：死透快档 2 分钟一档 = 进程拉起+一次 binder 查询，服务踢活即回 30 分钟节奏；踢链自愈每轮三次 getEnabledAccessibilityServiceList——均为分钟级偶发，红线的 1/1000 量级；营销 vid 检测为节点快照数据零 binder",
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
