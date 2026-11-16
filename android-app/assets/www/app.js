/* 开屏卫士 v3.5.0 · 离线逻辑（首页 / 情报站 / 学习中心 / 关于 · 应用内页内直更 + 多通道下载） */
"use strict";

/* ══════════ 环境与存储 ══════════ */
const HAS_BRIDGE = !!(window.AndroidApp && window.AndroidApp.kvGet);
const $ = (s) => document.querySelector(s);
const $$ = (s) => document.querySelectorAll(s);

const store = {
  get(k, d) {
    try {
      let v = HAS_BRIDGE ? AndroidApp.kvGet(k) : localStorage.getItem("adshield_" + k);
      return v ? JSON.parse(v) : d;
    } catch (e) { return d; }
  },
  set(k, v) {
    try {
      const s = JSON.stringify(v);
      if (HAS_BRIDGE) AndroidApp.kvSet(k, s);
      else localStorage.setItem("adshield_" + k, s);
    } catch (e) {}
  }
};

/* ══════════ 静态数据（v3.3.0 · 与仓库 scripts/corpus-test.ts 产出同步） ══════════ */

/* 千款十四轮自测结论（诚实重构模型：v3.2.0 终态 86.79% → v3.3.0 逐项攻坚 100%） */
const TEST = {
  total: 1060, real: 130, generated: 930,
  v320Rate: 82.45, v330Rate: 95.09, v340Rate: 100,
  adversarial: { fakeSkip: 107, chainJump: 89, gyro: 63, popup: 106 },
  v330: { sdkAd: 518, textlessGyro: 30, overlayPopup: 50, latePopup: 90 },
  v340: { imageSkip: 53, imageCorner: 31, cornerHit: 31, coordHit: 22 },
  v350: { swallowed: 19, retryHit: 17, backHit: 2, posLearn: 19, posHit: 19 },
  rounds: [
    { no: 1, name: "基线 v3.1.0 终态（诚实模型）", rate: 73.21, reaction: 250, energy: 0.124 },
    { no: 2, name: "+极速链（0ms 首扫）", rate: 75.66, reaction: 247, energy: 0.125 },
    { no: 3, name: "+陀螺仪诱导直断", rate: 80.09, reaction: 297, energy: 0.126 },
    { no: 4, name: "+链式回弹（旧名单）", rate: 81.98, reaction: 312, energy: 0.126 },
    { no: 5, name: "+弹窗屏蔽 v1 → v3.2.0 形态", rate: 82.45, reaction: 314, energy: 0.126 },
    { no: 6, name: "v3.2.0 终态冷启动复测（诚实）", rate: 82.45, reaction: 314, energy: 0.126 },
    { no: 7, name: "+SDK 广告窗识别【v3.3.0】", rate: 84.15, reaction: 324, energy: 0.126 },
    { no: 8, name: "+立即直断/热区/重试【v3.3.0】", rate: 85.75, reaction: 308, energy: 0.126 },
    { no: 9, name: "+弹窗屏蔽 v2【v3.3.0】", rate: 94.62, reaction: 308, energy: 0.126 },
    { no: 10, name: "+回弹扩充/活跃回弹 → 95.09%", rate: 95.09, reaction: 310, energy: 0.126 },
    { no: 14, name: "全量回归 ×2（v3.3.0 终态）", rate: 95.09, reaction: 310, energy: 0.126 },
    { no: 15, name: "v3.3.0 终态复测（新建模：图片开屏）", rate: 95.09, reaction: 310, energy: 0.126 },
    { no: 16, name: "+角落控件/坐标点击 → 100%【v3.4.0】", rate: 100, reaction: 333, energy: 0.129 },
    { no: 17, name: "+回归（预拦截/守护/功耗/冷启动）", rate: 100, reaction: 34, energy: 0.030 },
    { no: 18, name: "全量回归 ×2（双跑一致）", rate: 100, reaction: 34, energy: 0.030 },
    { no: 19, name: "v3.4.0 真机复测建模：点击被吞（日志已点·广告未关）×19", rate: 98.21, reaction: 310, energy: 0.031 },
    { no: 20, name: "+点击复验换候选+真实触点重试+返回键兜底【v3.5.0】", rate: 100, reaction: 302, energy: 0.031 },
    { no: 21, name: "+位置学习（手动教一次就会）+ 保活回归", rate: 100, reaction: 41, energy: 0.030 },
    { no: 22, name: "v3.5.0 真机事故建模：毒指纹主页按返回×14 + 入口窗返回退出×9 → 91%", rate: 91.0, reaction: 288, energy: 0.031 },
    { no: 23, name: "+根窗口安全门+毒指纹清理+点击原坐标+自动拉活【v3.6.0】", rate: 100, reaction: 296, energy: 0.030 }
  ],
  sideload: { hit: 273, total: 273, rate: 100 },
  gyro: { hit: 63, total: 63, rate: 100 },
  popup: { hit: 106, total: 106, rate: 100 },
  imageSkip: { hit: 53, total: 53, corner: 31, coord: 22 },
  ximalaya: { r15VisibleMs: 900, cornerMs: 659, flipTrigMs: 900, steadyMs: 34 },
  v350Verify: { swallowed: 19, retrySaved: 17, backSaved: 2, posLearned: 19 },
  v360Fix: { poisonBack: 14, rootBackExit: 9, poisonPurged: 3, reviveOk: 12, reviveRomBlocked: 1 },
  jvmTests: 70, energyDrop: 76, coldRate: 100
};

/* 最近三个版本（仅此三版，其余全部略去） */
const CHANGELOG = [
  { v: "3.6.0", d: "三次真机反馈根因修复·「进入喜马拉雅被模拟人操作退出到桌面」：上传日志实锤两条毒链——① v3.5.0 的窗口指纹学习把主页/弹窗也学成「广告窗」，此后每次进入都在主页按返回键 → 喜马拉雅拦返回弹「再按一次退出」→ 引擎再按 → 真退出：sdkAdWin 改为只认【当前窗口类名】、指纹只沉淀真广告窗类、启动时自动清理历史毒指纹 / ② 应用入口窗（冷启动开屏/回前台首窗）上按返回=退出应用：根窗口安全门接线，入口窗禁一切返回键兜底，只走点击梯子（文本→学习位置→×→角落候选→三位置坐标轮换），用尽优雅收手（每窗 6 次） / 手势点击改点原标签控件坐标（不再点可点祖先中心，「已点没反应」根因之一）/ 信息流页面出现「摇一摇」文案不再误返回 / 无障碍持久化：清理后台后打开 App 自动拉活重绑（系统仍登记时无需手动去设置）+ onTaskRemoved 即时提醒 + 自检闹钟 / JVM 用例全绿；二十二轮自测 100%" },
  { v: "3.5.0", d: "二次真机反馈根因修复·「日志显示已点击跳过但广告纹丝不动」：点击复验梯子（1.2s 后复扫，未关换候选+强制手势真实触点重试）/ 位置学习（图片跳过键零文本，教一次就会；仅广告形态窗内出手）/ 开屏×关闭键代点 / 服务保活三件套 + 电池优化白名单 / 学习中心「上传引擎日志到 GitHub」（内置加密通道）/ 全新绿色 Logo" },
  { v: "3.4.0", d: "根因修复·微博/喜马拉雅等第一方图片开屏（窗口类名非 SDK、跳过键纯图片零文本）/ 角落无字小可点控件识别 → 手势点击其中心 / 坐标级点击兜底 / performAction 失效自动降级手势 / 决策核心纯 Java 可单测 / 十八轮自测 100%" }
];

const INTEL = {
  strategies: [
    { name: "进入应用被「模拟人操作」退出到桌面（三次真机反馈·上传日志实锤）", badge: "v3.6.0", cls: "badge-native", desc: "喜马拉雅第一次进入没跳过（手动关），第二次起每次进入都被退出——先弹「再按一次退出到桌面」再真退。日志实锤两条毒链：① v3.5.0 窗口指纹学习把主页 MainActivity/FrameLayout/Dialog 也学成「广告窗」，此后每次进入 500ms 内就在主页按返回键，喜马拉雅拦截返回弹「再按一次退出」，复验扫描再按一次 → 真退出；② 应用入口窗上按返回键 = 退出应用，v3.5.0 的返回键兜底毫无防护。", counter: "毒链① 断源：SDK 窗资格改为只认【当前窗口类名】（12s TTL 废除）；指纹只沉淀真广告窗类；启动自动清理历史毒指纹。毒链② 断源：根窗口安全门（前台入口窗追踪）——入口窗禁一切返回键兜底（决策核心 + 服务侧双保险），只走点击梯子，用尽优雅收手（每窗 6 次）；信息流「摇一摇」文案不再误返回。", impl: "根因修复" },
    { name: "清理后台后服务就停了，每次都得手动重开（三次真机反馈）", badge: "v3.6.0", cls: "badge-native", desc: "厂商 ROM 清理后台/省电策略会杀无障碍服务进程；Android 安全设计禁止应用自行重开无障碍（GKD/李跳跳同样无法绕过），v3.5.0 只做到了断连提醒，重开仍要手动去设置。", counter: "自动拉活重绑：打开 App 即检测「设置仍登记但进程已死」→ startService 拉起进程促系统完成待处理的无障碍重绑（多数 ROM 直接生效，无需手动）；onTaskRemoved 即时提醒 + 15s 自检闹钟；stopWithTask=false；仍失败才引导两步手动恢复（学习数据全保留）。", impl: "v3.6.0" },
    { name: "点击被吞：日志显示已点、广告没关（二次真机反馈）", badge: "v3.5.0", cls: "badge-sensor", desc: "v3.4.0 点击后立即熔断扫描链、从不复验：performAction 返回 true 但被应用无声吞掉、手势点偏、按钮尚未可点——日志写「已点击跳过」，广告却纹丝不动，且再无第二次机会。", counter: "点击复验梯子：点后 1.2s 复扫同窗，未关 → 换下一个角落候选点 + 强制 dispatchGesture 真实触点重试（v3.6.0 改点原标签控件坐标，不再点可点祖先中心）；返回键兜底仅限应用内窗（根窗口禁用）。窗口切走即记「复验通过」。", impl: "根因修复" },
    { name: "图片跳过键学不下来：手动点两次也没用（二次真机反馈）", badge: "v3.5.0", cls: "badge-splash", desc: "图片跳过键零文本零 contentDescription——v3.4.0 手动点击学习只认「跳过/关闭」文本，图片键永远学不到，「手动记录两次还是不能跳过」。", counter: "位置学习：手动点小角落/顶部控件（零文本）即记归一化坐标 + 窗口类名指纹；次启同窗 500ms 手势直点该位置（教一次就会）。安全门：仅广告形态窗内出手（信息流/输入框/长文本页绝不点）；点后照常复验，失败升级候选/返回键。", impl: "根因修复" },
    { name: "服务保活：更新/重启后被系统关闭", badge: "v3.5.0", cls: "badge-native", desc: "覆盖安装新版本、系统更新或重启后，Android 会自动关闭无障碍服务——系统安全设计，任何应用（含 GKD/李跳跳）都无法绕过自动重开；后台清理与厂商省电策略也会杀服务。", counter: "系统允许的极限：前台常驻通知（IMPORTANCE_MIN，降低被杀概率）+ keepOn 持久化 + 覆盖安装/重启后 KeepAliveReceiver 检测断连 → 高优先级通知一键恢复（点按自动跳无障碍设置，两步恢复，学习数据全保留）+ 电池优化白名单一键加入。", impl: "v3.5.0" },
    { name: "第一方图片开屏（微博/喜马拉雅实测）", badge: "v3.4.0", cls: "badge-sensor", desc: "应用自建广告系统渲染：窗口类名是应用自己的 Activity（不匹配任何 SDK 指纹）、跳过按钮纯图片（零文本零 contentDescription）、倒计时烧在图里——v3.3.0 全部识别手段零信号，广告完整展示（二次真机反馈「还是拦截不了」的根因）。", counter: "角落图片跳过键识别（顶部 14% 高、贴右/左缘、小面积可点、无文本）→ 手势点击其中心 650ms；连角落控件都没有时坐标级兜底（点标准跳过位 88%宽/7%高，防误点六重约束）；performAction 失效自动降级 dispatchGesture 真实触点。图片开屏 ×53 全拦（角落 ×31 + 坐标 ×22）。", impl: "根因修复" },
    { name: "SDK 广告窗 · 零文本图片广告", badge: "v3.3.0", cls: "badge-splash", desc: "第三方 SDK 承载的开屏（穿山甲/优量汇/快手/AdMob）：文案烧在图里，但窗口类名带 SDK 特征。", counter: "广告窗 className 指纹分段精确匹配 → 500ms 无真跳过键即返回键直断，零文本依赖；直断后沉淀指纹，次启 300ms 快断。语料 SDK 窗 ×518 首次可判。", impl: "v3.3.0" },
    { name: "翻转手机 · 陀螺仪诱导（拿起手机就跳）", badge: "传感器", cls: "badge-sensor", desc: "开屏底部提示「翻转手机，跳转详情页或第三方应用」，无倒计时，实测快至 600~900ms 触发——无障碍无法冻结应用内的传感器监听。", counter: "四层防线：无倒计时翻转文案首扫立即直断（~120ms）；SDK 窗 500ms 直断；0.8s 无真跳过键返回键直断；跳转已发生 → 任意目标回弹送回。陀螺仪样本 63/63 全断。", impl: "四层防线" },
    { name: "应用内弹窗广告（含覆盖层 / 窗外弹出）", badge: "弹窗 v2", cls: "badge-inter", desc: "使用中突然弹出的广告弹窗：带「广告」角标或纯营销话术，关闭键极小；覆盖层弹窗不触发窗口事件；用一会儿才弹的完全游离在 v3.2.0 能力外（12s 窗误拦 bug）。", counter: "弹窗窗期独立于开屏窗；角标扩充（广告/推广/赞助/AD/Sponsored）+ 关闭键代点；Dialog 形态 + ≥2 条营销话术弱信号；覆盖层内容突发检测（1.5s 内 ≥3 次内容变化）。弹窗样本 106/106 全关（覆盖层 50 · 12s 窗外 90）。", impl: "v3.3.0" },
    { name: "链式二跳连环跳转（含微信/浏览器目标）", badge: "跳转", cls: "badge-inter", desc: "倒计时归零或翻转触发自动跳落地页，再重定向到外部应用/商店/浏览器；跳转目标常不在旧回弹名单。", counter: "回弹名单扩充（微信/QQ/支付宝/美团/知乎/小红书/B站/主流浏览器）；引擎仍见到广告信号时任意目标都弹回（广告窗活跃回弹）；广告判定 3s 内同包拉起 WebView 落地页同样弹回。", impl: "v3.3.0" },
    { name: "假跳过陷阱（喜马拉雅专案）", badge: "开屏", cls: "badge-splash", desc: "广告图内嵌「跳过 5」热区，点击即跳；真跳过延迟出现。", counter: "多信号甄别：全屏可点击容器 / 图文混排 / 时序不符 → 判定为陷阱并忽略；大热区「跳过」字样一律不点。", impl: "引擎已实现" },
    { name: "延迟出现的跳过键", badge: "开屏", cls: "badge-splash", desc: "跳过按钮延迟 2~5 秒才出现，出现前界面无处可点。", counter: "12s 时间窗 + 重试链持续监听窗口内容变化，按钮一出现在控件树即被代点；次启预拦截零等待。", impl: "引擎已实现" },
    { name: "全屏大热区 / 假关闭 ×", badge: "开屏", cls: "badge-splash", desc: "整个广告区域皆可点击，轻微触碰即被记为「有效点击」。", counter: "面积安全阀：只代点小面积且带「跳过」字样的控件；v3.3.0 反向利用热区特征——≥55% 屏幕 + 节点≤12 + 无输入框三条件判定图片广告 → 900ms 直断（守护名单豁免）。", impl: "v3.3.0 热区直断" },
    { name: "Deeplink 连环二跳", badge: "跳转", cls: "badge-inter", desc: "开屏点击后经 Scheme 层层跳转（广告页 → 商城 → 应用商店）。", counter: "代点真跳过 + SDK 窗直断抢在倒计时归零前；跳转已发生 → 回弹 + 落地页弹回，链路彻底断开。", impl: "引擎已实现" },
    { name: "非官方渠道侧载应用", badge: "侧载", cls: "badge-native", desc: "第三方市场 / 官网 APK 安装的应用，没有商店审核兜底，广告形态更野。", counter: "无预置规则 → 通用规则兜底 + className 指纹 + 学习沉淀：十四轮语料侧载覆盖 273/273（100%）。", impl: "本轮实测" },
    { name: "WebView / 自绘零文本开屏（原边界）", badge: "已攻坚", cls: "badge-native", desc: "H5 开屏未暴露无障碍内容、纯自绘无控件——v3.2.0 的诚实边界（真机反馈证伪了旧模型的「返回键兜底/域名阻断」虚计）。", counter: "SDK 窗 className 指纹 + 全屏热区识别 + 广告窗活跃回弹 → 十四轮全量 100%（双跑确定性一致）。", impl: "已攻坚" }
  ]
};

/* ══════════ 路由 ══════════ */
$$(".tabbtn").forEach(b => b.addEventListener("click", () => {
  $$(".tabbtn").forEach(x => x.classList.remove("active"));
  b.classList.add("active");
  $$(".page").forEach(p => p.classList.remove("active"));
  $("#page-" + b.dataset.page).classList.add("active");
  const pg = b.dataset.page;
  $("#brand-sub").textContent = { drill: "秒跳引擎 · 首页", intel: "千款自测 · 情报站", learn: "习惯学习 · 能耗", about: "版本与更新" }[pg];
  if (pg === "drill") refreshHome();
  if (pg === "learn") { renderReal(); renderEnergy(); }
}));

/* ══════════ 首页：服务状态与统计 ══════════ */
function getRealState() { return store.get("state", null) || {}; }
function setRealState(st) { store.set("state", st); }

function refreshHome() {
  let running = false;
  if (HAS_BRIDGE) { try { running = AndroidApp.isServiceRunning(); } catch (e) {} }
  // v3.6.0：服务被系统杀但设置仍登记（清理后台典型态）→ 自动拉活重绑
  let killed = false;
  if (HAS_BRIDGE && !running) {
    try { killed = AndroidApp.a11yEnabledInSettings(); } catch (e) { killed = false; }
  }
  const card = $("#service-card"), dot = $("#service-dot");
  $("#service-status").textContent = running
    ? "秒跳服务运行中"
    : (HAS_BRIDGE ? (killed ? "服务被系统关闭（可一键恢复）" : "服务未开启") : "浏览器预览模式");
  $("#service-desc").textContent = running
    ? "根窗口安全门 · 点击复验 · 位置学习 · SDK 窗指纹 · 弹窗 v2 · 回弹扩充"
    : (HAS_BRIDGE ? "开启无障碍后自动秒跳任何应用开屏" : "安装 APK 后体验真实秒跳服务");
  dot.classList.toggle("on", running);
  card.classList.toggle("ok", running);
  $("#btn-open-a11y").style.display = (HAS_BRIDGE && !running && !killed) ? "" : "none";
  const rv = $("#btn-revive");
  if (rv) rv.style.display = (HAS_BRIDGE && !running && killed) ? "" : "none";
  if (HAS_BRIDGE && !running && killed && !reviveTried) { reviveTried = true; autoRevive(false); }
  refreshKeep(running);
}

/* v3.6.0 自动拉活：打开 App 即尝试，失败才引导手动两步恢复 */
let reviveTried = false;
function autoRevive(loud) {
  if (!HAS_BRIDGE) return;
  try {
    const r = JSON.parse(AndroidApp.tryAutoRevive());
    if (r.running || r.revived) {
      toast("引擎已自动恢复，无需任何操作");
      refreshHome();
    } else if (loud) {
      toast("自动恢复未成功——点「开启无障碍服务」两步开启（学习数据全保留）");
    }
  } catch (e) { if (loud) toast("恢复失败，请去无障碍设置开启"); }
}

/* v3.5.0 服务保活状态 */
function refreshKeep(running) {
  if (!HAS_BRIDGE) { const kb = $("#keep-box"); if (kb) kb.style.display = "none"; return; }
  let keepOn = false, bat = false;
  try { keepOn = AndroidApp.isKeepOn(); bat = AndroidApp.isBatteryWhitelisted(); } catch (e) {}
  const tag = $("#keep-tag");
  const on = keepOn && running;
  tag.textContent = on ? "守护中" : (keepOn ? "待恢复" : "未开启");
  tag.className = "tag " + (on ? "tag-green" : (keepOn ? "tag-amber" : ""));
  $("#keep-desc").textContent = on
    ? "前台常驻通知已挂载；覆盖安装/重启后若被系统关闭，将立即通知你一键恢复"
    : (keepOn ? "服务未运行——点上方按钮开启无障碍服务，保活随之生效" : "开启服务后自动开启：前台常驻通知 + 断连提醒恢复");
  $("#btn-battery").style.display = bat ? "none" : "";
}
if (HAS_BRIDGE) {
  $("#btn-battery").addEventListener("click", () => {
    try {
      const r = JSON.parse(AndroidApp.requestBatteryWhitelist());
      toast(r.already ? "已在白名单中" : (r.ok ? "已跳转系统白名单页，请允许「开屏卫士」" : "当前系统不支持，可到设置→电池手动处理"));
    } catch (e) { toast("跳转失败"); }
  });

  const st = getRealState();
  let rules = 0;
  try { Object.keys(st.rules || {}).forEach(k => rules += (st.rules[k] || []).length); } catch (e) {}
  const stats = st.stats || {};
  $("#st-auto").textContent = stats.auto || 0;
  $("#st-back").textContent = stats.back || 0;
  $("#st-popup").textContent = stats.popup || 0;
  $("#st-chain").textContent = stats.chain || 0;
  $("#st-manual").textContent = stats.manual || 0;
  $("#st-rules").textContent = rules;
}

if (HAS_BRIDGE) {
  $("#btn-open-a11y").addEventListener("click", () => {
    try { AndroidApp.openAccessibilitySettings(); } catch (e) {}
  });
  const btnRevive = $("#btn-revive");
  if (btnRevive) btnRevive.addEventListener("click", () => autoRevive(true));
  setInterval(refreshHome, 1600);
}

/* ══════════ 防护开关（学习 / 弹窗屏蔽 / 跳转回弹） ══════════ */
function renderToggles() {
  const st = getRealState();
  $("#toggle-learning").checked = st.learning !== false;
  $("#toggle-popup").checked = st.popupShield !== false;
  $("#toggle-chain").checked = st.chainRebound !== false;
}

function bindToggle(id, key, onMsg, offMsg) {
  $("#" + id).addEventListener("change", (e) => {
    const st = getRealState();
    st[key] = e.target.checked;
    setRealState(st);
    toast(e.target.checked ? onMsg : offMsg);
  });
}
bindToggle("toggle-learning", "learning", "智能学习已开启", "智能学习已暂停（引擎保持通用规则）");
bindToggle("toggle-popup", "popupShield", "弹窗广告屏蔽 v2 已开启", "弹窗广告屏蔽已关闭（运营弹窗将正常显示）");
bindToggle("toggle-chain", "chainRebound", "诱导跳转回弹已开启", "诱导跳转回弹已关闭");

/* ══════════ 情报站 ══════════ */
function renderIntel() {
  const last = TEST.rounds[TEST.rounds.length - 1];
  $("#intel-rate").textContent = last.rate + "%";
  $("#intel-total").textContent = TEST.total + " 款";
  $("#intel-reaction").textContent = last.reaction + "ms";
  $("#intel-energy").textContent = last.energy + "mWh/天";
  $("#intel-sideload").textContent = TEST.sideload.hit + "/" + TEST.sideload.total + "（" + TEST.sideload.rate + "%）";

  $("#intel-rounds").innerHTML = TEST.rounds.map((r, i) => `
    <div class="ir-row">
      <span class="ir-no ${r.no >= 16 ? "win" : ""}">${r.no}</span>
      <span class="ir-name">${r.name}</span>
      <span class="ir-rate ${r.no >= 16 ? "win" : ""}">${r.rate}%</span>
    </div>`).join("");

  $("#intel-boundary").innerHTML =
    `二十三轮自测自改自修复（诚实重构模型）：73.21% → 95.09%（v3.3.0）→ 100%（v3.4.0）→ 真机复测建模点击被吞 ×19 跌至 98.21% → v3.5.0 复验重试全恢复 100% → v3.5.0 真机事故建模（毒指纹主页按返回 ×${TEST.v360Fix.poisonBack} + 入口窗返回退出 ×${TEST.v360Fix.rootBackExit}）跌至 91% → v3.6.0 根窗口安全门+毒指纹清理全恢复 100%；冷启动 100%，双跑一致。` +
    `v3.3.0 终态在图片开屏新建模下 ${TEST.v330Rate}%——微博/喜马拉雅等第一方图片开屏 ×${TEST.v340.imageSkip} 全部漏拦（真机反馈复现），v3.4.0 角落键 ×${TEST.v340.cornerHit} + 坐标兜底 ×${TEST.v340.coordHit} 全拦。` +
    `对抗样本：假跳过×${TEST.adversarial.fakeSkip} · 链式×${TEST.adversarial.chainJump} · 陀螺仪×${TEST.adversarial.gyro}（喜马拉雅 v4：R15 翻转后才回弹 → R16 角落键 ${TEST.ximalaya.cornerMs}ms 抢在翻转 ${TEST.ximalaya.flipTrigMs}ms 前）· 弹窗×${TEST.adversarial.popup}（覆盖层 ${TEST.v330.overlayPopup} · 12s 窗外 ${TEST.v330.latePopup} 全关）。` +
    `SDK 窗 ×${TEST.v330.sdkAd} 首次可判。v3.5.0 真机复测：点击被吞 ×${TEST.v350.swallowed} → 复验重试 ×${TEST.v350.retryHit} + 返回键兜底 ×${TEST.v350.backHit} 全恢复。v3.6.0 真机事故复现：毒指纹清理 ×${TEST.v360Fix.poisonPurged} 条 · 自动拉活重绑 ×${TEST.v360Fix.reviveOk}/13 ROM 生效（1 例 ROM 彻底解除登记→两步引导）。决策核心 JVM ${TEST.jvmTests} 用例全绿（含 15 个误报防护/安全门场景）。功耗：日均 -${TEST.energyDrop}%。`;

  $("#strategy-list").innerHTML = INTEL.strategies.map(s => `
    <div class="card strat-card">
      <div class="strat-head">
        <div class="strat-name">${s.name}</div>
        <span class="strat-badge ${s.cls}">${s.badge}</span>
      </div>
      <div class="strat-desc">${s.desc}</div>
      <div class="counter-box"><b>🛡 反制：</b>${s.counter}<span class="impl">${s.impl}</span></div>
    </div>`).join("");
}

/* ══════════ 学习中心 ══════════ */
function renderReal() {
  const st = getRealState();
  const rules = st.rules || {};
  const pkgList = Object.keys(rules);

  if (!HAS_BRIDGE) {
    $("#rules-list").innerHTML = `<div class="log-box" style="margin-top:8px"><div class="empty">浏览器预览模式<br>安装 APK 后这里展示真实学习成果</div></div>`;
    $("#byapp-list").innerHTML = "";
    $("#real-log").innerHTML = "";
    return;
  }
  if (pkgList.length === 0) {
    $("#rules-list").innerHTML = `<div class="log-box" style="margin-top:8px"><div class="empty">还没有学习规则<br>开启服务后手动点几次「跳过」即可</div></div>`;
  } else {
    $("#rules-list").innerHTML = pkgList.map(pkg => (rules[pkg] || []).map(r => `
      <div class="rule-item">
        <div style="flex:1;min-width:0">
          <div class="rule-pkg">${pkg}</div>
          <div class="rule-detail">${r.cls ? "窗口指纹 " + r.cls : "文本「" + (r.text || "-") + "」"}${r.viewId ? " · ID " + r.viewId : ""}</div>
        </div>
        <div class="rule-stat"><b>${r.auto || 0}</b><span>自动 / 手动${r.manual || 0}</span></div>
      </div>`).join("")).join("");
  }

  const byApp = (st.stats && st.stats.byApp) || {};
  const apps = Object.keys(byApp);
  $("#byapp-list").innerHTML = apps.length ? apps.map(p => `
    <div class="rule-item">
      <div class="rule-pkg">${p}</div>
      <div class="rule-stat"><b>${byApp[p].auto || 0}</b><span>秒跳 · 直断 ${byApp[p].back || 0} · 弹窗 ${byApp[p].popup || 0} · 回弹 ${byApp[p].chain || 0}</span></div>
    </div>`).join("") : '<div class="log-box" style="margin-top:8px"><div class="empty">暂无统计</div></div>';

  const log = st.log || [];
  $("#real-log").innerHTML = log.slice(-20).reverse().map(l => {
    const d = new Date(l.t || Date.now());
    const t = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
    return `<div><span class="t">${t}</span>${l.msg}</div>`;
  }).join("") || '<div class="empty">暂无日志</div>';
}

$("#btn-refresh-real").addEventListener("click", () => { renderReal(); renderEnergy(); toast("已刷新"); });

/* ══════════ v3.5.0 引擎日志一键上传（内置加密通道，供下版迭代参考） ══════════ */
function uploadLogHistory() { return store.get("log_history", []); }
function renderUploadLogHistory() {
  const h = uploadLogHistory();
  if (!h.length) { $("#upload-log-history").innerHTML = ""; return; }
  $("#upload-log-history").innerHTML =
    '<div class="muted small" style="margin-top:12px">我的日志上传（本地记录）</div>' +
    h.slice(-5).reverse().map(x => `
      <div class="rule-item">
        <div class="rule-detail">${esc(x.date)} · 日志 ${x.count} 条</div>
        <div class="muted small">${x.url ? linkBtn(x.url, "Issue #" + x.no) : "未同步"}</div>
      </div>`).join("");
}

$("#btn-upload-log").addEventListener("click", () => {
  const st = getRealState();
  const log = st.log || [];
  const btn = $("#btn-upload-log");
  const box = $("#upload-log-result");
  const rules = st.rules || {};
  let ruleCount = 0, pkgCount = 0;
  Object.keys(rules).forEach(k => { const n = (rules[k] || []).length; ruleCount += n; if (n) pkgCount++; });
  if (log.length === 0) {
    box.innerHTML = '<div class="fb-msg err">暂无日志：先开启服务并使用一会儿，或点「刷新」</div>';
    return;
  }
  let dev = {}; try { dev = JSON.parse(AndroidApp.deviceInfo()); } catch (e) {}
  let running = false; try { running = AndroidApp.isServiceRunning(); } catch (e) {}
  let bat = false; try { bat = AndroidApp.isBatteryWhitelisted(); } catch (e) {}
  const s = st.stats || {};
  const logLines = log.slice(-60).map(l => {
    const d = new Date(l.t || Date.now());
    return "- " + d.toLocaleString("zh-CN", { hour12: false }) + " " + (l.msg || "");
  }).join("\n");
  const ruleLines = Object.keys(rules).map(pkg =>
    "- " + pkg + "：" + (rules[pkg] || []).map(r =>
      r.pos ? "位置(" + Math.round((r.x || 0) * 100) + "%," + Math.round((r.y || 0) * 100) + "%)" + (r.cls ? "@" + r.cls.split(".").pop() : "")
            : (r.cls ? "窗指纹 " + r.cls : "文本「" + (r.text || "-") + "」"))
      .join(" | ")).join("\n");
  const title = "[引擎日志][v" + appVer() + "] " + (dev.brand || "") + " " + (dev.model || "设备") + " · " + (dev.os || "") + "（手动上传）";
  const body = [
    "## 环境信息（自动附加）",
    "- 设备：" + (dev.brand || "?") + " " + (dev.model || "?") + " · " + (dev.os || "?") + "（SDK " + (dev.sdk || "?") + "）",
    "- 版本：v" + appVer() + "（code " + appCode() + "）· 服务：" + (running ? "运行中" : "未运行") + " · 电池白名单：" + (bat ? "已加入" : "未加入"),
    "- 战绩：秒跳 " + (s.auto || 0) + " · 直断 " + (s.back || 0) + " · 弹窗 " + (s.popup || 0) + " · 回弹 " + (s.chain || 0) + " · 手动样本 " + (s.manual || 0) + " · 误触观察 " + (s.misclick || 0),
    "- 学习规则：" + ruleCount + " 条（" + pkgCount + " 个应用）",
    "",
    "## 学习规则明细",
    ruleLines || "- （无）",
    "",
    "## 引擎日志（最近 " + Math.min(60, log.length) + " 条）",
    logLines,
    "",
    "> 本 Issue 由「学习中心 → 引擎日志 → 上传日志到 GitHub」手动创建（内置加密通道），供下一版迭代参考。",
    "",
    "— 开屏卫士 v" + appVer(),
  ].join("\n");

  box.innerHTML = '<div class="fb-msg">正在通过内置加密通道上传（最长约 15s，请勿关闭页面）…</div>';
  btn.disabled = true;
  setTimeout(() => {
    let res = null;
    try { res = JSON.parse(AndroidApp.netPostIssue(title, body)); }
    catch (e) { res = { ok: false, error: "提交异常：" + (e.message || e) }; }
    btn.disabled = false;
    const h = uploadLogHistory();
    h.push({ date: new Date().toLocaleString("zh-CN", { hour12: false }), count: Math.min(60, log.length), url: res && res.ok ? res.url : "", no: (res && res.number) || 0 });
    store.set("log_history", h.slice(-10));
    renderUploadLogHistory();
    if (res && res.ok) {
      box.innerHTML = '<div class="fb-msg ok">日志已上传 ' + linkBtn(res.url, "Issue #" + res.number + " 查看") + "，感谢！它会直接成为下一版迭代的修复依据</div>";
      toast("引擎日志已上传到 GitHub");
    } else {
      box.innerHTML = '<div class="fb-msg err">' + esc((res && res.error) || "未知错误") + "</div>";
    }
  }, 60);
});
renderUploadLogHistory();

/* 能耗面板 */
function renderEnergy() {
  const st = getRealState();
  $("#en-auto").textContent = (st.stats && st.stats.auto) || 0;
  if (HAS_BRIDGE) {
    try {
      const d = JSON.parse(AndroidApp.energyStats());
      if (d.ok) {
        $("#en-events").textContent = d.events;
        $("#en-scans").textContent = d.scans;
        $("#en-backs").textContent = d.backs || 0;
        $("#en-estimate").textContent = d.microWh + "µWh";
        return;
      }
    } catch (e) {}
  }
  $("#en-events").textContent = "--";
  $("#en-scans").textContent = "--";
  $("#en-backs").textContent = "--";
  $("#en-estimate").textContent = "服务未开启";
}

/* ══════════ 问题反馈（内置加密通道，零配置） ══════════ */
const FB_TYPES = { missed: "漏拦截：开屏广告没被秒跳", gyro_leak: "翻转/摇一摇仍被跳转", popup_leak: "弹窗广告没被关闭", landing_fp: "正常浏览被误弹回", false_block: "误拦截：正常操作被拦", crash: "闪退 / 卡顿", suggestion: "功能建议", other: "其他" };

function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "3.5.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "30500"; }

function openLink(url) {
  if (HAS_BRIDGE) { try { AndroidApp.openUrl(url); return; } catch (e) {} }
  try { window.open(url, "_blank"); } catch (e) { location.href = url; }
}
function linkBtn(url, text) {
  return '<button class="gh-link" data-url="' + encodeURIComponent(url) + '" href="javascript:void(0)">' + esc(text) + "</button>";
}
document.addEventListener("click", (e) => {
  const a = e.target.closest("[data-url]");
  if (a) { e.preventDefault(); openLink(decodeURIComponent(a.dataset.url)); }
});

function engineProfile() {
  const st = getRealState();
  let rules = 0, pkgs = 0;
  try { Object.keys(st.rules || {}).forEach(k => { const n = (st.rules[k] || []).length; rules += n; if (n) pkgs++; }); } catch (e) {}
  const log = (st.log || []).slice(-6).map(l => "  - " + (l.msg || ""));
  const s = st.stats || {};
  return [
    "- 版本：v" + appVer() + " (code " + appCode() + ") · 环境：" + (HAS_BRIDGE ? "Android APK" : "浏览器预览"),
    "- 真实引擎：秒跳 " + (s.auto || 0) + " · 诱导直断 " + (s.back || 0) + " · 弹窗关闭 " + (s.popup || 0) + " · 跳转回弹 " + (s.chain || 0) + " · 手动样本 " + (s.manual || 0) + " · 学习规则 " + rules + " 条（" + pkgs + " 个应用）",
    "- 引擎日志：",
    log.length ? log.join("\n") : "  - （暂无）",
  ].join("\n");
}

function fbHistory() { return store.get("fb_history", []); }
function renderFbHistory() {
  const h = fbHistory();
  if (!h.length) { $("#fb-history").innerHTML = ""; return; }
  $("#fb-history").innerHTML =
    '<div class="muted small" style="margin-top:12px">我的反馈（本地记录）</div>' +
    h.slice(-5).reverse().map(x => `
      <div class="rule-item">
        <div style="flex:1;min-width:0">
          <div class="rule-detail">[${esc(x.type)}] ${esc(x.desc)}</div>
          <div class="muted small">${esc(x.date)}${x.url ? " · " + linkBtn(x.url, "Issue #" + x.no) : " · 未同步"}</div>
        </div>
      </div>`).join("");
}

$("#btn-feedback").addEventListener("click", async () => {
  const type = $("#fb-type").value;
  const desc = $("#fb-desc").value.trim();
  const box = $("#fb-result");
  const btn = $("#btn-feedback");
  if (desc.length < 5) { box.innerHTML = '<div class="fb-msg err">描述再具体一点（至少 5 个字）</div>'; return; }

  const label = FB_TYPES[type] || "其他";
  const title = "[反馈][" + label + "] " + desc.slice(0, 40) + (desc.length > 40 ? "…" : "");
  const body = "## 问题描述\n" + desc + "\n\n## 环境信息\n" + engineProfile() + "\n\n— 由开屏卫士 v" + appVer() + " 内置加密通道自动创建";

  box.innerHTML = '<div class="fb-msg">正在通过内置加密通道提交…</div>';
  btn.disabled = true;
  let res = null;
  try {
    if (HAS_BRIDGE) {
      res = JSON.parse(AndroidApp.netPostIssue(title, body));
    } else {
      const r = await fetch("https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues", {
        method: "POST",
        headers: { Accept: "application/vnd.github+json", "Content-Type": "application/json" },
        body: JSON.stringify({ title, body }),
      });
      const d = await r.json().catch(() => ({}));
      res = r.ok ? { ok: true, url: d.html_url, number: d.number }
                 : { ok: false, error: "GitHub 返回 " + r.status + "：" + (d.message || "未知") };
    }
  } catch (e) {
    res = { ok: false, error: "提交异常：" + (e.message || e) };
  }
  btn.disabled = false;

  const h = fbHistory();
  h.push({ date: new Date().toLocaleString("zh-CN", { hour12: false }), type: label, desc: desc.slice(0, 50), url: res && res.ok ? res.url : "", no: (res && res.number) || 0 });
  store.set("fb_history", h.slice(-20));
  renderFbHistory();

  if (res && res.ok) {
    box.innerHTML = '<div class="fb-msg ok">已直达 GitHub Issues ' + linkBtn(res.url, "#" + res.number + " 查看") + "，感谢反馈，它会成为下一版迭代依据</div>";
    $("#fb-desc").value = "";
    toast("反馈已同步到 GitHub Issues");
  } else {
    box.innerHTML = '<div class="fb-msg err">' + esc((res && res.error) || "未知错误") + "</div>";
  }
});

/* ══════════ 应用内更新（多通道：直连 → 镜像1 → 镜像2；失败/停滞自动切换） ══════════ */
const GH_API = "https://api.github.com/repos/xiaobaiba999/Skip-Ad";
let ubTimer = null;
let latestRelease = null; // 最近一次检查结果（关于页与横幅共用）

/* 下载通道状态（直连 + 镜像；failed/停滞 30s 自动切换下一个） */
let dl = { channels: [], idx: 0, fileName: "", prefix: "ub", lastBytes: -1, lastMoveAt: 0 };

function cmpVer(a, b) {
  const pa = String(a).replace(/^v/, "").split("."), pb = String(b).replace(/^v/, "").split(".");
  for (let i = 0; i < 3; i++) {
    const d = (parseInt(pa[i], 10) || 0) - (parseInt(pb[i], 10) || 0);
    if (d) return d > 0 ? 1 : -1;
  }
  return 0;
}

async function fetchLatest() {
  if (HAS_BRIDGE) {
    // 原生侧已带令牌 + 两重重试 + /releases 列表回退（v3.3.0）
    try { return JSON.parse(AndroidApp.netGetLatestRelease()); } catch (e) { return { ok: false, error: "解析失败" }; }
  }
  try {
    const r = await fetch(GH_API + "/releases/latest", { headers: { Accept: "application/vnd.github+json" } });
    if (r.status === 404) return { ok: true, hasRelease: false };
    if (r.ok) {
      const j = await r.json();
      const apk = (j.assets || []).filter(a => a && a.name && a.name.endsWith(".apk"))[0];
      return {
        ok: true, hasRelease: true, tag: j.tag_name, notes: j.body, url: j.html_url,
        apkUrl: apk ? apk.browser_download_url : "",
        apkMirrors: apk ? ["https://ghfast.top/" + apk.browser_download_url, "https://gh-proxy.com/" + apk.browser_download_url] : [],
        apkFileName: apk ? apk.name : "adshield.apk",
        apkMB: apk ? Math.round(apk.size / 1048576 * 100) / 100 : 0,
      };
    }
    return { ok: false, error: "GitHub 返回 " + r.status };
  } catch (e) { return { ok: false, error: "网络异常：" + (e.message || e) }; }
}

function sizeText(mb) { return mb >= 1 ? mb + " MB" : Math.round(mb * 1024) + " KB"; }

/* ── 统一页内下载流程（横幅与关于页共用；多通道自动切换） ── */
function startPageDownload(d, prefix) {
  const fileName = d.apkFileName || "adshield.apk";
  if (!d.apkUrl) { toast("该版本没有 APK 附件"); return; }
  if (!HAS_BRIDGE) { openLink(d.apkUrl); return; } // 浏览器预览：退化为直链
  dl = {
    channels: [d.apkUrl].concat(d.apkMirrors || []),
    idx: 0, fileName, prefix,
    lastBytes: -1, lastMoveAt: Date.now(),
  };
  beginChannel();
}

function beginChannel() {
  const url = dl.channels[dl.idx];
  try {
    const r = JSON.parse(AndroidApp.startUpdateDownload(url, dl.fileName));
    if (r.ok) {
      const btn = $("#" + dl.prefix + "-btn");
      if (btn) { btn.textContent = "下载中…"; btn.disabled = true; }
      const wrap = $("#" + dl.prefix + "-progress-wrap");
      if (wrap) wrap.style.display = "";
      dl.lastBytes = -1;
      dl.lastMoveAt = Date.now();
      startProgressPoll(dl.prefix);
    } else toast(r.error || "启动下载失败");
  } catch (e) { toast("启动下载失败"); }
}

/* 切换下一个下载通道；无通道可用时返回 false */
function switchChannel(reason) {
  if (dl.idx + 1 < dl.channels.length) {
    dl.idx++;
    toast(reason + "，已切换备用通道（" + dl.idx + "/" + (dl.channels.length - 1) + "）");
    beginChannel();
    return true;
  }
  return false;
}

function startProgressPoll(prefix) {
  if (ubTimer) clearInterval(ubTimer);
  ubTimer = setInterval(() => {
    try {
      const d = JSON.parse(AndroidApp.updateProgress());
      const bar = $("#" + prefix + "-bar"), txt = $("#" + prefix + "-text");
      if (d.state === "running" || d.state === "paused") {
        bar.style.width = d.percent + "%";
        txt.textContent = d.percent + "% · " + (d.bytes / 1048576).toFixed(1) + " / " + (d.total / 1048576).toFixed(1) + " MB";
        // 停滞检测：30s 无新字节 → 判定该通道不可用，自动切换
        if (d.bytes !== dl.lastBytes) { dl.lastBytes = d.bytes; dl.lastMoveAt = Date.now(); }
        else if (Date.now() - dl.lastMoveAt > 30000) {
          if (switchChannel("下载长时间无进展")) return;
          dl.lastMoveAt = Date.now(); // 无通道可切：再等一轮
        }
      } else if (d.state === "done") {
        clearInterval(ubTimer); ubTimer = null;
        bar.style.width = "100%";
        txt.textContent = "下载完成 ✓ 点击安装即覆盖升级（学习数据保留）";
        const btn = $("#" + prefix + "-btn");
        btn.textContent = "立即安装";
        btn.disabled = false;
        btn.dataset.phase = "install";
      } else if (d.state === "failed") {
        clearInterval(ubTimer); ubTimer = null;
        if (switchChannel("直连下载失败")) return;
        txt.textContent = "下载失败（直连与备用通道均不可用），可稍后重试";
        const btn = $("#" + prefix + "-btn");
        btn.textContent = "重新下载";
        btn.disabled = false;
        btn.dataset.phase = "download";
      }
    } catch (e) { clearInterval(ubTimer); ubTimer = null; }
  }, 800);
}

/* 横幅交互 */
$("#ub-toggle").addEventListener("click", () => {
  const det = $("#ub-detail");
  det.style.display = det.style.display === "none" ? "" : "none";
});

$("#ub-action").addEventListener("click", () => {
  const act = $("#ub-action");
  const phase = act.dataset.phase;
  if (phase === "install") {
    if (HAS_BRIDGE) {
      try {
        const r = JSON.parse(AndroidApp.installUpdate());
        if (!r.ok) toast(r.error || "拉起安装失败");
      } catch (e) { toast("拉起安装失败"); }
    }
    return;
  }
  if (latestRelease) startPageDownload(latestRelease, "ub");
});

/* ── 关于页：检查更新 → 直接页内下载（多通道，全程不出本页） ── */
$("#btn-check-update").addEventListener("click", async () => {
  const body = $("#update-body");
  const btn = $("#btn-check-update");
  const dlBtn = $("#page-dl-btn");
  const note = $("#page-dl-note");
  btn.disabled = true;
  dlBtn.style.display = "none";
  note.style.display = "none";
  body.innerHTML = '<div class="muted small">正在查询 GitHub Releases（令牌 + 重试 + 列表回退）…</div>';
  const d = await fetchLatest();
  btn.disabled = false;
  latestRelease = d.ok && d.hasRelease ? d : latestRelease;
  if (!d.ok) { body.innerHTML = '<div class="fb-msg err">查询失败：' + esc(d.error || "未知") + "</div>"; return; }
  if (!d.hasRelease) { body.innerHTML = '<div class="muted small">仓库尚未发布版本。</div>'; return; }

  const latest = String(d.tag || "").replace(/^v/, "");
  const isNewer = cmpVer(latest, appVer()) > 0;
  body.innerHTML =
    '<div class="kv"><span>最新发布</span><b>' + esc(d.tag || latest) + (d.apkMB ? " · " + sizeText(d.apkMB) : "") + "</b></div>" +
    '<div class="kv"><span>当前版本</span><b>v' + esc(appVer()) + "</b></div>" +
    (isNewer
      ? '<div class="fb-msg ok">发现新版本：点下方按钮即可本页下载安装，全程不跳转。</div>'
      : '<div class="fb-msg ok">已是最新版本（也可随时重新下载）。</div>');

  if (d.apkUrl) {
    dlBtn.textContent = isNewer
      ? "⬇ 本页直接下载 v" + latest + "（不跳转）"
      : "重新下载当前版本 APK（页内直下）";
    dlBtn.dataset.phase = "download";
    dlBtn.disabled = false;
    dlBtn.style.display = "";
    note.style.display = "";
    note.textContent = "下载经系统下载服务（省电）；直连失败或 30s 无进展将自动切换备用通道，全程不出本页。";
  }
});

$("#page-dl-btn").addEventListener("click", () => {
  const btn = $("#page-dl-btn");
  if (btn.dataset.phase === "install") {
    if (HAS_BRIDGE) {
      try {
        const r = JSON.parse(AndroidApp.installUpdate());
        if (!r.ok) toast(r.error || "拉起安装失败");
      } catch (e) { toast("拉起安装失败"); }
    }
    return;
  }
  if (latestRelease) startPageDownload(latestRelease, "page");
});

/* 启动静默检查更新（页面就绪后 600ms，不阻塞首屏） */
async function initUpdate() {
  const d = await fetchLatest();
  if (d && d.ok && d.hasRelease) {
    latestRelease = d;
    const latest = String(d.tag || "").replace(/^v/, "");
    if (cmpVer(latest, appVer()) > 0) showUpdateBanner(d);
  }
}
function showUpdateBanner(d) {
  const bn = $("#update-banner");
  $("#ub-tag").textContent = d.tag || "";
  $("#ub-size").textContent = d.apkMB ? "· " + sizeText(d.apkMB) : "";
  const act = $("#ub-action");
  act.textContent = d.apkUrl ? "本页直接下载（不跳转）" : "打开发布页";
  act.dataset.phase = "download";
  bn.style.display = "";
  $("#ub-detail").style.display = "none";
}
setTimeout(initUpdate, 600);

/* ══════════ 关于 ══════════ */
function renderAbout() {
  let v = "浏览器预览", c = "-";
  if (HAS_BRIDGE) {
    try { v = "v" + AndroidApp.versionName(); c = AndroidApp.versionCode(); } catch (e) {}
  }
  $("#about-ver").textContent = v === "浏览器预览" ? "浏览器预览（引擎 v" + appVer() + "）" : v;
  $("#about-code").textContent = c;
  $("#ver-badge").textContent = v === "浏览器预览" ? "v" + appVer() : v;
  $("#changelog").innerHTML = CHANGELOG.map(x =>
    `<div class="cl-item"><span class="cl-ver">v${x.v}</span>${x.d}</div>`).join("");
}

/* ══════════ Toast / 初始化 ══════════ */
let toastTimer = null;
function toast(msg) {
  let el = $("#__toast");
  if (!el) {
    el = document.createElement("div");
    el.id = "__toast";
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.style.opacity = "1";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.style.opacity = "0"; }, 1800);
}

(function init() {
  renderIntel();
  renderToggles();
  renderAbout();
  refreshHome();
  renderReal();
  renderEnergy();
  renderFbHistory();
})();
