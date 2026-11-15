/* 开屏卫士 · 离线逻辑（原生桥 + 演练场 + 学习引擎演示） */
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

/* ══════════ 静态数据 ══════════ */
const CREATIVES = [
  { id: "ecom", name: "双11狂欢盛典", sub: "全场低至1折起 · 领券再减300", cta: "立即抢购", brand: "聚星商城", cls: "ad-ecom", emoji: "🛒" },
  { id: "game", name: "星际远征 手游", sub: "公测开启 · 登录送限定机甲", cta: "立即下载", brand: "星际远征", cls: "ad-game", emoji: "🚀" },
  { id: "vip", name: "星云VIP 限时5折", sub: "免广告 · 专属内容 · 每月观影券", cta: "开通会员", brand: "星云VIP", cls: "ad-vip", emoji: "👑" },
  { id: "fin", name: "灵活理财 年化3.5%", sub: "100元起投 · 随存随取", cta: "了解详情", brand: "星云钱包", cls: "ad-fin", emoji: "📈" }
];

const STRATEGIES = [
  { id: "std", name: "标准开屏", desc: "跳过按钮立即出现，3秒倒计时", badge: "开屏", skipDelay: 0, cd: 5 },
  { id: "delay", name: "延迟跳过", desc: "跳过按钮延迟2秒才出现", badge: "开屏", skipDelay: 2, cd: 4 },
  { id: "fake", name: "假关闭×", desc: "×点击即跳广告页，真跳过是小字", badge: "开屏", skipDelay: 1.2, cd: 5, fake: true },
  { id: "shake", name: "摇一摇热区", desc: "全屏热区+摇一摇，跳过按钮极小", badge: "传感器", skipDelay: 0, cd: 5, hot: true }
];

/* ═════ v1.2.0 精准判断引擎：区分「广告诱导跳转」与「用户主动操作」 ═════
   依据 2026-11 调研（澎湃 20 款实测 / 2023 摇一摇行业标准）：
   误触/程序化指纹 = 按压<40ms、无移动轨迹、展示后<150ms 即点；
   真人触摸 = 按压 60~300ms + 轻微轨迹抖动 + 观察后点击。评分≥60 判广告行为；
   用户纠正≥2 次后门槛升至 74（防误伤）。 */
const JUDGE_THRESHOLD = 60, JUDGE_RELAXED = 74;

function judgeStats() {
  if (!demo.judge) demo.judge = { blocked: 0, allowed: 0, corrected: 0 };
  return demo.judge;
}

function judgeJump(trigger, sig) {
  sig = sig || {};
  const reasons = [];
  let score = 0;
  const add = (label, w) => { score += w; reasons.push({ label, w }); };
  if (trigger === "hotzone") add("点击落在全屏热区（点击任意位置即跳转）", 68);
  else if (trigger === "fakeclose") add("点击无字样的 × 假关闭按钮", 80);
  else add("点击广告素材区（语义模糊，靠行为指纹裁决）", 22);
  if (sig.pressMs != null) {
    if (sig.pressMs < 40) add(`按压 ${sig.pressMs}ms（<40ms，快于人手）`, 25);
    else if (sig.pressMs > 350) add(`按压 ${sig.pressMs}ms，沉稳长按`, -12);
    else add(`按压 ${sig.pressMs}ms，人工区间`, -5);
  }
  if (sig.movePx != null) {
    if (sig.movePx === 0) add("无移动轨迹（瞬移点击）", 15);
    else if (sig.movePx < 6) add(`轨迹抖动 ${sig.movePx}px，符合真人触摸`, -8);
    else add(`拖动轨迹 ${sig.movePx}px，主动滑动`, -15);
  }
  if (sig.reactionMs != null) {
    if (sig.reactionMs < 150) add(`展示后 ${sig.reactionMs}ms 即点击，快于人类反应`, 20);
    else if (sig.reactionMs > 600) add(`观察 ${(sig.reactionMs / 1000).toFixed(1)}s 后点击，主动兴趣`, -15);
  }
  const threshold = judgeStats().corrected >= 2 ? JUDGE_RELAXED : JUDGE_THRESHOLD;
  return { isAd: score >= threshold, score: Math.max(0, Math.min(100, score)), reasons };
}

/* 行为指纹采集（pointer 事件覆盖触摸与鼠标） */
const FP = { since: 0, downAt: 0, upAt: 0, lastX: 0, lastY: 0, movePx: 0 };
function fpAttach(el) {
  el.addEventListener("pointerdown", (e) => { FP.downAt = Date.now(); FP.upAt = 0; FP.lastX = e.clientX; FP.lastY = e.clientY; FP.movePx = 0; });
  el.addEventListener("pointermove", (e) => { if (!FP.downAt) return; FP.movePx += Math.hypot(e.clientX - FP.lastX, e.clientY - FP.lastY); FP.lastX = e.clientX; FP.lastY = e.clientY; });
  el.addEventListener("pointerup", () => { if (FP.downAt) FP.upAt = Date.now(); });
}
function fpSnapshot() {
  return {
    pressMs: FP.downAt ? (FP.upAt || Date.now()) - FP.downAt : 0,
    movePx: Math.round(FP.movePx),
    reactionMs: FP.since ? Date.now() - FP.since : null,
  };
}

/* 拦截 Toast（含误判纠正按钮） */
function engineToastJ(j) {
  const old = $(".engine-toast"); if (old) old.remove();
  const el = document.createElement("div");
  el.className = "engine-toast block";
  const reasons = j.reasons.filter(r => r.w > 0).slice(0, 2).map(r => "· " + r.label).join("<br>");
  el.innerHTML = `🛡 已拦截广告跳转 ${Math.round(j.score)}分<br>${reasons}<button class="et-action">误判？这是我要的操作</button>`;
  $("#phone-body").appendChild(el);
  el.querySelector(".et-action").addEventListener("click", (ev) => {
    ev.stopPropagation();
    judgeStats().corrected++;
    saveDemo();
    engineToast("已纠正：引擎将放宽同类快速点击判定（纠正≥2 次提高门槛）", 2200);
    el.remove();
    renderDrillStats();
  });
  setTimeout(() => el.remove(), 5200);
}

const FEED = [
  { cat: "天文", title: "韦伯望远镜确认发现迄今最古老星系，诞生于宇宙大爆炸后2.9亿年", src: "星云科技", reads: "128万", g: "linear-gradient(135deg,#8b5cf6,#d946ef)", e: "🔭" },
  { cat: "数码", title: "折叠屏手机单季出货量首次超越平板电脑，铰链成本再降30%", src: "极客观察", reads: "86万", g: "linear-gradient(135deg,#f43f5e,#f97316)", e: "📱" },
  { cat: "生活", title: "本周末城市马拉松开跑，三条地铁线路将提前至5:30运营", src: "城市速递", reads: "42万", g: "linear-gradient(135deg,#10b981,#14b8a6)", e: "🚴" },
  { cat: "AI", title: "AI编程助手效率实测：真实工程任务提效38%，调试仍需人工把关", src: "星云科技", reads: "95万", g: "linear-gradient(135deg,#a855f7,#7c3aed)", e: "🤖" }
];

const INTEL = {
  strategies: [
    { name: "倒计时跳过按钮", badge: "开屏", cls: "badge-splash", desc: "3~5秒倒计时，跳过按钮立即出现但越来越小。用户需等待倒计时结束或精准点击小按钮。", counter: "引擎在开屏时间窗内毫秒级识别「跳过」控件并点击，人机差距被抹平。", impl: "引擎已实现" },
    { name: "延迟出现跳过按钮", badge: "开屏", cls: "badge-splash", desc: "跳过按钮延迟2~5秒才出现，出现前界面无处可点，诱导误触广告主体。", counter: "持续监听窗口内容变化事件，按钮一出现在控件树中即被点击。", impl: "引擎已实现" },
    { name: "假关闭按钮 ×", badge: "开屏/插屏", cls: "badge-inter", desc: "右上角 × 点击后不是关闭而是跳转广告落地页，真正的跳过入口是角落小字。", counter: "只匹配含「跳过/skip/关闭广告」字样的控件，永不盲点无字样的 × 图标。", impl: "引擎已实现" },
    { name: "摇一摇传感器跳转", badge: "传感器", cls: "badge-sensor", desc: "读取加速度计/陀螺仪，晃动超过阈值即跳转。2023年行业标准（工信部指导）已限定：加速度≥15m/s²、转角≥35°、操作≥3秒。", counter: "引擎侧：只点击小面积控件、不碰中央热区；系统侧：可在权限管理关闭该应用的传感器权限。", impl: "引擎侧已防" },
    { name: "全屏大热区", badge: "开屏", cls: "badge-splash", desc: "整个广告区域都可点击，轻微触碰即被记为「有效点击」跳转落地页。", counter: "面积安全阀：只点击小于屏幕面积35%的控件，全屏热区直接忽略。", impl: "引擎已实现" },
    { name: "强制观看插屏", badge: "插屏", cls: "badge-inter", desc: "内容跳转时弹出插屏广告，倒计时结束才解锁关闭按钮（强看3~5秒）。", counter: "解锁瞬间自动点击关闭（等待型反制）；强看时长本身无法缩短，这是与开屏的区别。", impl: "引擎已实现" },
    { name: "连环跳转/二次跳转", badge: "跳转", cls: "badge-inter", desc: "开屏广告点击后经 deeplink 层层跳转（A页面→B页面→应用商店）。", counter: "收紧系统「关联启动 / 打开其他应用」权限，让跳转链条在系统层断掉。", impl: "需系统设置配合" },
    { name: "原生信息流伪装", badge: "内容", cls: "badge-native", desc: "广告伪装成正常内容混入信息流，标注极小。", counter: "不处理。内容类广告属于应用正常运营范畴，代点/屏蔽均有合规风险（参考拦精灵案边界）。", impl: "超出合规范围" }
  ],
  defenses: [
    { name: "无障碍代点（本应用 / GKD / 李跳跳 / 雷跳跳）", desc: "在 UI 层读取控件树，定位「跳过」按钮后模拟点击；不碰网络、不需要 Root。GKD 社区订阅规则已覆盖 678+ 应用。", limit: "局限：自绘控件（游戏引擎/Canvas）与没有跳过按钮的广告无效；李跳跳已于 2023.8 因律师函停更。" },
    { name: "DNS / 私人 DNS 过滤（AdGuard DNS 等）", desc: "把广告素材域名解析到黑洞，让素材请求失败，广告位空手进入首页。", limit: "局限：与正文同域名的接口、已缓存的素材、IP 直连的广告均拦不住。" },
    { name: "本地 VPN 规则分流（Surge / Quantumult X / Stash 等）", desc: "起本地隧道做 DNS 分流 + 域名规则集匹配，iOS 阵营主力方案。", limit: "局限：原生渲染广告与正文走同一主域名时无法精准区分。" },
    { name: "系统权限治理", desc: "关闭可疑应用的传感器/加速度计权限；部分国产 ROM 提供「仅开屏6秒内禁止获取动作与方向」；收紧关联启动；卸载低频应用。", limit: "局限：入口藏得深，且部分开关只管自有广告、不管第三方 SDK。" },
    { name: "替代方案", desc: "用网页版/轻量版替代完整版 App；或付费去广告。", limit: "局限：功能阉割或需要付费。" },
    { name: "监管投诉", desc: "2021年工信部专项整治后开屏广告必须可关闭、点击跳转需用户主动；摇一摇已有量化行业标准。遇违规可向 12321/12300 举报；2025年双11期间杭州余杭区市监局曾就摇一摇集中举报介入调查。", limit: "局限：取证难、单个 App 弹窗次数少，治理始终滞后于变体。" }
  ],
  sources: [
    "中青报·中青网 / 法治日报《手机深处，广告在等你手滑》（2026-11）：开屏广告渲染链路、摇一摇行业标准、「拦精灵」案",
    "江苏省消保委调查（2023）：77.22% 受访者常遇「摇一摇」跳转，94.94% 表示厌恶",
    "GKD 开源项目（gkd.li）与 Adpro-Team 社区订阅规则：无障碍 + 订阅规则模式，覆盖 678+ 应用",
    "李跳跳官网存档：2023-08-24 宣布无限期停更",
    "「拦精灵」案（2024，芒果TV 诉，一审二审均判不正当竞争成立）：法院明确跳过类软件只能代点「跳过」、不得直接屏蔽广告"
  ]
};

/* ══════════ 全局状态 ══════════ */
let demo = store.get("demo", null) || {
  shieldOn: true, creativeIdx: 0,
  per: {},                       // 每策略学习模型 {enc,manual,auto,mis,lat[],conf}
  adStats: { views: 0, clicks: 0, skips: 0, timeouts: 0 },
  log: []
};
let curStrategy = "std";
let simTimers = [];

function P(id) {
  if (!demo.per[id]) demo.per[id] = { enc: 0, manual: 0, auto: 0, mis: 0, lat: [], conf: 0 };
  return demo.per[id];
}
function saveDemo() { store.set("demo", demo); }
function dlog(msg) {
  const d = new Date();
  const t = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  demo.log.unshift({ t, msg });
  if (demo.log.length > 30) demo.log.length = 30;
  renderDrillLog();
}

/* ══════════ 路由 ══════════ */
$$(".tabbtn").forEach(b => b.addEventListener("click", () => {
  $$(".tabbtn").forEach(x => x.classList.remove("active"));
  b.classList.add("active");
  $$(".page").forEach(p => p.classList.remove("active"));
  $("#page-" + b.dataset.page).classList.add("active");
  if (b.dataset.page === "home") refreshHome();
  if (b.dataset.page === "learn") { renderReal(); renderDemoReport(); }
}));

/* ══════════ 首页 ══════════ */
function refreshHome() {
  let running = false;
  if (HAS_BRIDGE) { try { running = AndroidApp.isServiceRunning(); } catch (e) {} }
  const card = $("#service-card"), dot = $("#service-dot");
  $("#service-status").textContent = running ? "跳过服务运行中" : (HAS_BRIDGE ? "服务未开启" : "浏览器预览模式");
  $("#service-desc").textContent = running
    ? "正在后台观察并自动点击「跳过」"
    : (HAS_BRIDGE ? "需开启无障碍权限后生效" : "安装 APK 后可使用真实跳过服务");
  dot.classList.toggle("on", running);
  card.classList.toggle("ok", running);
  $("#btn-open-a11y").style.display = (HAS_BRIDGE && !running) ? "" : "none";

  const st = getRealState();
  let rules = 0;
  try { Object.keys(st.rules || {}).forEach(k => rules += (st.rules[k] || []).length); } catch (e) {}
  $("#st-auto").textContent = (st.stats && st.stats.auto) || 0;
  $("#st-manual").textContent = (st.stats && st.stats.manual) || 0;
  $("#st-rules").textContent = rules;
}

function getRealState() {
  let st = store.get("state", null);
  if (!st) st = {};
  return st;
}
function setRealState(st) { store.set("state", st); }

if (HAS_BRIDGE) {
  $("#btn-open-a11y").addEventListener("click", () => {
    try { AndroidApp.openAccessibilitySettings(); } catch (e) {}
  });
  setInterval(refreshHome, 1600);
}

/* 学习总开关（真实引擎 + 演练引擎共用） */
function renderLearningToggle() {
  const st = getRealState();
  const on = st.learning !== false;
  $("#toggle-learning").checked = on;
}
$("#toggle-learning").addEventListener("change", (e) => {
  const st = getRealState();
  st.learning = e.target.checked;
  setRealState(st);
  toast(e.target.checked ? "学习引擎已开启" : "学习引擎已暂停（完全静默）");
});

/* ══════════ 演练场 ══════════ */
function renderChips() {
  $("#strategy-chips").innerHTML = STRATEGIES.map(s =>
    `<button class="chip ${s.id === curStrategy ? "active" : ""}" data-s="${s.id}">${s.name}</button>`
  ).join("");
  $$("#strategy-chips .chip").forEach(c => c.addEventListener("click", () => {
    curStrategy = c.dataset.s; renderChips(); renderDrillStats();
  }));
}

$("#btn-launch").addEventListener("click", () => launchSim());
$("#toggle-shield").addEventListener("change", (e) => {
  demo.shieldOn = e.target.checked;
  $("#shield-state").textContent = e.target.checked ? "已开启" : "已暂停";
  saveDemo();
});

function clearTimers() { simTimers.forEach(t => clearTimeout(t)); simTimers = []; }
function after(ms, fn) { simTimers.push(setTimeout(fn, ms)); }

function launchSim() {
  clearTimers();
  const body = $("#phone-body");
  body.innerHTML = `<div class="app-launch"><div class="app-logo">✨</div><div style="font-size:12px;color:#a1a1aa;letter-spacing:2px">星云资讯</div></div>`;
  after(1000, () => showSplash());
}

function engineEnabled() {
  const st = getRealState();
  return demo.shieldOn && st.learning !== false;
}

/* 自动跳过延迟：置信度越高出手越快（模拟真实引擎的规则命中加速） */
function autoLatencyMs(p) { return Math.max(350, 2400 / (1 + p.conf)); }

function showSplash() {
  const s = STRATEGIES.find(x => x.id === curStrategy);
  const ad = CREATIVES[demo.creativeIdx % CREATIVES.length];
  demo.creativeIdx++;
  const p = P(s.id);
  p.enc++;
  demo.adStats.views++;

  const body = $("#phone-body");
  const startedAt = Date.now();

  body.innerHTML = `
  <div class="ad-layer" id="ad-layer">
    <div class="ad-body ${ad.cls}" id="ad-body">
      ${s.hot ? '<div class="shake-banner">📱 晃动手机，立即了解 &gt;&gt;</div>' : ""}
      ${ad.cls === "ad-game" || ad.cls === "ad-vip"
        ? `<div style="font-size:44px">${ad.emoji}</div><div class="ad-title">${ad.name}</div><div class="ad-sub">${ad.sub}</div>`
        : `<div class="ad-title">${ad.name}</div><div class="ad-sub">${ad.sub}</div>`}
      <span class="ad-tag">广告</span>
    </div>
    <div class="ad-brandbar"><div class="mini-logo">✨</div><span>星云资讯 · 看见更大的宇宙</span></div>
    <button class="skip-btn" id="skip-btn" style="display:none">跳过 ${s.cd}s</button>
    ${s.fake ? '<button class="fake-close" id="fake-close">×</button>' : ""}
  </div>`;

  const skipBtn = $("#skip-btn");
  const adLayer = $("#ad-layer");
  const adBody = $("#ad-body");

  const finish = (how, latency) => {
    clearTimers();
    if (how === "manual") { p.manual++; p.conf += 1; }
    else if (how === "auto") { p.auto++; p.conf += 0.4; }
    else if (how === "timeout") { /* 完整播放：引擎未命中 */ }
    if (latency != null) { p.lat.push(Math.round(latency * 10) / 10); if (p.lat.length > 14) p.lat.shift(); }
    saveDemo();
    if (how === "manual") dlog(`🧠 已学习「${s.name}」的跳过控件 → 置信度 ${Math.min(100, Math.round(p.conf * 50))}%`);
    if (how === "auto") dlog(`⚡ 引擎自动跳过「${s.name}」（${(latency || 0).toFixed(1)}s）`);
    if (how === "timeout") dlog(`⏱ 「${s.name}」广告完整播放 ${s.cd}s（未被跳过）`);
    demo.adStats.skips += (how === "manual" || how === "auto") ? 1 : 0;
    if (how === "timeout") demo.adStats.timeouts++;
    saveDemo();
    renderDrillStats();
    showFeed();
  };

  /* 用户手动点跳过 */
  skipBtn.addEventListener("click", () => finish("manual", (Date.now() - startedAt) / 1000));

  /* 广告主体点击 → 精准判断引擎裁决（v1.2.0） */
  FP.since = startedAt;
  fpAttach(adBody);
  const toLanding = (isFake) => {
    demo.adStats.clicks++;
    const trigger = isFake ? "fakeclose" : (s.hot ? "hotzone" : "tap");
    if (engineEnabled()) {
      const j = judgeJump(trigger, fpSnapshot());
      const st = judgeStats();
      if (j.isAd) {
        st.blocked++;
        if (isFake) p.mis++;
        saveDemo();
        dlog(`🛡 已拦截「${s.name}」广告跳转（${Math.round(j.score)}分）：${j.reasons.filter(r => r.w > 0).slice(0, 2).map(r => r.label).join("；")}`);
        engineToastJ(j);
        renderDrillStats();
        return; // 拦截：不进入落地页
      }
      st.allowed++;
      saveDemo();
      engineToast("✓ 放行：用户主动操作（按压/轨迹符合真人特征）", 1800);
    } else if (isFake) { p.mis++; }
    if (isFake) dlog(`⚠️ 误触假关闭 × → 引擎已记入策略：只点含「跳过」字样的控件`);
    else dlog(`⚠️ 点击广告主体进入落地页（广告方计为有效点击）`);
    saveDemo();
    showLanding(ad, () => showFeed());
  };
  adBody.addEventListener("click", () => toLanding(false));
  if (s.fake) {
    const fc = $("#fake-close");
    fpAttach(fc);
    fc.addEventListener("click", (e) => { e.stopPropagation(); toLanding(true); });
  }

  /* 策略1：跳过按钮延迟出现 */
  after(s.skipDelay * 1000, () => {
    skipBtn.style.display = "";
    if (s.hot) skipBtn.classList.add("tiny");
  });

  /* 倒计时（从按钮可见开始） */
  let cd = s.cd;
  const tick = () => {
    cd--;
    if (cd <= 0) { if (skipBtn.style.display !== "none") { skipBtn.textContent = "跳过 0s"; } finish("timeout", s.cd + s.skipDelay); return; }
    if (skipBtn.style.display !== "none") skipBtn.textContent = `跳过 ${cd}s`;
    after(1000, tick);
  };
  after(s.skipDelay * 1000 + 1000, tick);

  /* 引擎：冷启动观察 / 置信后自动出手 */
  if (engineEnabled()) {
    if (p.conf >= 1) {
      const delay = s.skipDelay * 1000 + autoLatencyMs(p);
      after(delay, () => {
        if (!document.body.contains(skipBtn)) return;
        engineToast(`⚡ 引擎已自动点击「跳过」 · ${(delay / 1000).toFixed(1)}s`);
        finish("auto", delay / 1000);
      });
    } else {
      engineToast("👁 引擎观察中：请手动点击跳过（冷启动学习）", 2200);
    }
  } else {
    engineToast("⏸ 跳过引擎已暂停", 1500);
  }
}

/* 插屏广告（点文章触发） */
function openArticle(item) {
  const s = STRATEGIES.find(x => x.id === curStrategy);
  const p = P(s.id);
  const body = $("#phone-body");
  const FORCE = 3;
  body.innerHTML = `
    <div class="feed">${feedHTML()}</div>
    <div class="inter-layer" id="inter">
      <div class="inter-card">
        <button class="inter-close" id="ic" disabled>3</button>
        <div class="inter-box">
          <div class="ad-body ${CREATIVES[2].cls}" style="height:230px;position:relative">
            <div style="font-size:40px">👑</div>
            <div class="ad-title" style="font-size:18px">星云VIP 限时5折</div>
            <div class="ad-sub">免广告 · 专属内容</div>
            <span class="ad-tag">广告</span>
          </div>
          <button class="inter-cta" id="icta">开通会员 →</button>
        </div>
      </div>
    </div>`;

  let closed = false;
  const closeInter = (how) => {
    if (closed) return;
    closed = true;
    if (how === "manual") { p.manual++; p.conf += 1; dlog("🧠 手动关闭插屏 → 已学习（等待型：解锁即点）"); }
    if (how === "auto") { p.auto++; p.conf += 0.4; dlog("⚡ 引擎在解锁瞬间自动关闭插屏"); }
    demo.adStats.views++;
    saveDemo(); renderDrillStats();
    showArticle(item);
  };

  let n = FORCE;
  const t = setInterval(() => {
    n--;
    const ic = $("#ic");
    if (!ic) { clearInterval(t); return; }
    if (n <= 0) {
      ic.disabled = false; ic.textContent = "×"; clearInterval(t);
      if (engineEnabled() && p.conf >= 1) {
        setTimeout(() => { closeInter("auto"); }, 350);
      }
    } else ic.textContent = n;
  }, 1000);
  simTimers.push(t);

  $("#ic").addEventListener("click", () => { if (!$("#ic").disabled) closeInter("manual"); });
  FP.since = Date.now();
  fpAttach($("#phone-body").querySelector(".inter-box"));
  $("#icta").addEventListener("click", () => {
    demo.adStats.clicks++;
    if (engineEnabled()) {
      const j = judgeJump("tap", fpSnapshot());
      const st = judgeStats();
      if (j.isAd) {
        st.blocked++; p.mis++;
        saveDemo();
        dlog(`🛡 已拦截插屏 CTA 误触（${Math.round(j.score)}分）→ 引擎记住：强看期不点击中央区域`);
        engineToastJ(j);
        renderDrillStats();
        return;
      }
      st.allowed++;
      saveDemo();
      engineToast("✓ 放行：用户主动操作", 1600);
    } else { p.mis++; dlog("⚠️ 误触插屏 CTA → 引擎记住：强看期不点击中央区域"); }
    saveDemo();
    showLanding(CREATIVES[2], () => showArticle(item));
  });
}

function feedHTML() {
  return `<div class="feed-tip">💡 点击任意文章将触发「强制观看插屏」（当前策略：${STRATEGIES.find(x => x.id === curStrategy).name}）</div>` +
    FEED.map((f, i) => `
    <div class="feed-item" data-i="${i}">
      <div class="fi-text">
        <span class="fi-cat">${f.cat}</span>
        <div class="fi-title">${f.title}</div>
        <div class="fi-meta">${f.src} · ${f.reads}阅读</div>
      </div>
      <div class="fi-thumb" style="background:${f.g}">${f.e}</div>
    </div>`).join("");
}

function showFeed() {
  clearTimers();
  $("#phone-body").innerHTML = `<div class="feed">${feedHTML()}</div>`;
  $$("#phone-body .feed-item").forEach(el =>
    el.addEventListener("click", () => openArticle(FEED[+el.dataset.i])));
}

function showArticle(item) {
  $("#phone-body").innerHTML = `
    <div class="article">
      <div style="font-size:11px;color:#a1a1aa;margin-bottom:8px">‹ 返回 &nbsp;|&nbsp; ${item.cat} · 详情</div>
      <h3>${item.title}</h3>
      <div style="font-size:10px;color:#a1a1aa;border-bottom:1px solid #f4f4f5;padding-bottom:8px;margin-bottom:10px">${item.src} · ${item.reads} 阅读</div>
      <p>这是演练文章正文。真实场景中，你从信息流点进文章就会被插屏广告拦截，强制观看数秒后才能阅读。</p>
      <p>开启引擎后，解锁瞬间会自动关闭插屏——你几乎感觉不到它的存在。</p>
      <p style="color:#a1a1aa;font-size:11px">（点击下方按钮返回信息流）</p>
      <button class="btn btn-outline" style="margin-top:6px" onclick="window.__backFeed()">← 返回信息流</button>
    </div>`;
  window.__backFeed = showFeed;
}

function showLanding(ad, onClose) {
  clearTimers();
  $("#phone-body").innerHTML = `
    <div class="landing">
      <div class="landing-bar"><span>🔗 ${ad.brand.toLowerCase()}.ad-landing.example</span>
      <button class="landing-close" id="ld-close">✕</button></div>
      <div style="flex:1;overflow-y:auto;padding:18px;text-align:center">
        <div class="ad-body ${ad.cls}" style="height:150px;border-radius:14px;position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center">
          <div style="font-size:34px">${ad.emoji}</div><div style="font-weight:800">${ad.name}</div>
        </div>
        <div style="font-weight:800;margin-top:14px">${ad.brand}</div>
        <div style="font-size:12px;color:#71717a">${ad.sub}</div>
        <p style="font-size:11px;color:#a1a1aa;margin-top:12px;line-height:1.7">这是广告主落地页演示。真实场景中此处为 H5 活动页或应用商店页，误触即完成一次「有效点击」转化。</p>
        <button class="btn btn-primary btn-block" style="margin-top:10px" id="ld-cta">${ad.cta}</button>
      </div>
    </div>`;
  $("#ld-close").addEventListener("click", onClose);
  $("#ld-cta").addEventListener("click", () => {
    demo.adStats.clicks++; saveDemo();
    engineToast("演示环境：此处将跳转广告主页面", 1600);
  });
}

function engineToast(msg, ms) {
  const old = $(".engine-toast"); if (old) old.remove();
  const el = document.createElement("div");
  el.className = "engine-toast"; el.textContent = msg;
  $("#phone-body").appendChild(el);
  setTimeout(() => el.remove(), ms || 2000);
}

/* 演练数据渲染 */
function renderDrillStats() {
  const s = STRATEGIES.find(x => x.id === curStrategy);
  const p = P(s.id);
  const conf = Math.min(100, Math.round(p.conf * 50));
  $("#dp-conf").textContent = `置信度 ${conf}%`;
  $("#dp-bar").style.width = conf + "%";
  $("#dp-enc").textContent = p.enc;
  $("#dp-manual").textContent = p.manual;
  $("#dp-auto").textContent = p.auto;

  /* 火花图：耗时曲线 */
  const svg = $("#dp-spark");
  if (p.lat.length < 2) {
    svg.innerHTML = `<text x="150" y="34" text-anchor="middle" font-size="11" fill="#a1a1aa">完成 2 次以上跳过后生成曲线</text>`;
  } else {
    const max = Math.max(...p.lat, 1);
    const W = 300, H = 60, pad = 6;
    const pts = p.lat.map((v, i) => {
      const x = pad + i * (W - pad * 2) / (p.lat.length - 1);
      const y = H - pad - (v / max) * (H - pad * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(" ");
    svg.innerHTML = `<polyline points="${pts}" fill="none" stroke="#7c3aed" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>` +
      p.lat.map((v, i) => {
        const x = pad + i * (W - pad * 2) / (p.lat.length - 1);
        const y = H - pad - (v / max) * (H - pad * 2);
        return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="3" fill="#d946ef"/>`;
      }).join("");
  }

  const recent = p.lat.slice(-5);
  const avg = recent.length ? (recent.reduce((a, b) => a + b, 0) / recent.length).toFixed(1) : "--";
  $("#ad-side").textContent = `曝光 ${demo.adStats.views} · 点击 ${demo.adStats.clicks}`;
  $("#user-side").textContent = `近${recent.length || "-"}次平均耗时 ${avg}s`;
  renderDrillLog();
}

function renderDrillLog() {
  const el = $("#dp-log");
  if (!el) return;
  el.innerHTML = demo.log.map(l => `<div><span class="t">${l.t}</span>${l.msg}</div>`).join("") || '<div class="empty">暂无日志 · 点「启动App」开始演练</div>';
}

/* ══════════ 情报站 ══════════ */
function renderIntel() {
  $("#strategy-list").innerHTML = INTEL.strategies.map(s => `
    <div class="card strat-card">
      <div class="strat-head">
        <div class="strat-name">${s.name}</div>
        <span class="strat-badge ${s.cls}">${s.badge}</span>
      </div>
      <div class="strat-desc">${s.desc}</div>
      <div class="counter-box"><b>🛡 反制：</b>${s.counter}<span class="impl">${s.impl}</span></div>
    </div>`).join("");

  $("#defense-list").innerHTML = INTEL.defenses.map((d, i) => `
    <div class="def-item">
      <div class="def-rank">${i + 1}</div>
      <div><div class="def-name">${d.name}</div>
      <div class="def-desc">${d.desc}</div>
      <div class="def-limit">${d.limit}</div></div>
    </div>`).join("");

  $("#source-list").innerHTML = INTEL.sources.map(s => `<li>${s}</li>`).join("");
}

/* ══════════ 学习中心 ══════════ */
$$(".tab").forEach(t => t.addEventListener("click", () => {
  $$(".tab").forEach(x => x.classList.remove("active"));
  t.classList.add("active");
  $("#tab-real").style.display = t.dataset.tab === "real" ? "" : "none";
  $("#tab-demo").style.display = t.dataset.tab === "demo" ? "" : "none";
}));

function renderReal() {
  const st = getRealState();
  const rules = st.rules || {};
  const pkgList = Object.keys(rules);

  if (!HAS_BRIDGE) {
    $("#rules-list").innerHTML = `<div class="empty">浏览器预览模式：以下为示例数据<br>安装 APK 后这里展示真实学习成果</div>`;
    $("#byapp-list").innerHTML = "";
    $("#real-log").innerHTML = "";
    return;
  }
  if (pkgList.length === 0) {
    $("#rules-list").innerHTML = `<div class="empty">还没有学习到规则<br>开启服务后，在带开屏广告的应用里手动点几次「跳过」</div>`;
  } else {
    $("#rules-list").innerHTML = pkgList.map(pkg => (rules[pkg] || []).map(r => `
      <div class="rule-item">
        <div style="flex:1;min-width:0">
          <div class="rule-pkg">${pkg}</div>
          <div class="rule-detail">文本「${r.text || "-"}」${r.viewId ? " · ID " + r.viewId : ""}</div>
        </div>
        <div class="rule-stat"><b>${r.auto || 0}</b><span>自动 / 手动${r.manual || 0}</span></div>
      </div>`).join("")).join("");
  }

  const byApp = (st.stats && st.stats.byApp) || {};
  const apps = Object.keys(byApp);
  $("#byapp-list").innerHTML = apps.length ? apps.map(p => `
    <div class="rule-item">
      <div class="rule-pkg">${p}</div>
      <div class="rule-stat"><b>${byApp[p].auto || 0}</b><span>自动 · 手动 ${byApp[p].manual || 0}</span></div>
    </div>`).join("") : '<div class="empty">暂无统计</div>';

  const log = st.log || [];
  $("#real-log").innerHTML = log.slice(-30).reverse().map(l => {
    const d = new Date(l.t || Date.now());
    const t = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
    return `<div><span class="t">${t}</span>${l.msg}</div>`;
  }).join("") || '<div class="empty">暂无日志</div>';
}

$("#btn-clear-rules").addEventListener("click", () => {
  const st = getRealState();
  st.rules = {}; st.log = [];
  setRealState(st);
  renderReal(); refreshHome();
  toast("已清空全部学习规则");
});
$("#btn-refresh-real").addEventListener("click", () => { renderReal(); refreshHome(); toast("已刷新"); });

/* 导出 / 导入 */
$("#btn-export").addEventListener("click", () => {
  const st = getRealState();
  const area = $("#io-area");
  area.value = JSON.stringify({ rules: st.rules || {}, stats: st.stats || {} });
  if (area.select) area.select();
  toast("已导出到文本框，请全屏复制保存");
});
$("#btn-import").addEventListener("click", () => {
  try {
    const data = JSON.parse($("#io-area").value);
    if (!data.rules) throw new Error("格式不对");
    const st = getRealState();
    st.rules = data.rules;
    if (data.stats) st.stats = data.stats;
    setRealState(st);
    renderReal(); refreshHome();
    toast("导入成功 ✓");
  } catch (e) { toast("导入失败：请粘贴导出的 JSON"); }
});

function renderDemoReport() {
  const js = demo.judge || { blocked: 0, allowed: 0, corrected: 0 };
  const prec = js.blocked > 0 ? Math.max(0, Math.round((1 - js.corrected / js.blocked) * 100)) : 100;
  let rows = "", tEnc = 0, tMan = 0, tAuto = 0;
  STRATEGIES.forEach(s => {
    const p = P(s.id);
    tEnc += p.enc; tMan += p.manual; tAuto += p.auto;
    const recent = p.lat.slice(-5);
    const avg = recent.length ? (recent.reduce((a, b) => a + b, 0) / recent.length).toFixed(1) + "s" : "--";
    rows += `<div class="rule-item">
      <div><div class="rule-pkg">${s.name}</div>
      <div class="rule-detail">误触 ${p.mis} 次 · ${p.conf >= 1 ? "已进入自动模式" : "冷启动学习中"}</div></div>
      <div class="rule-stat"><b>${p.auto}/${p.enc}</b><span>自动 · 均耗 ${avg}</span></div>
    </div>`;
  });
  $("#demo-report").innerHTML = `
    <div class="stat-grid" style="margin-top:0">
      <div class="stat"><div class="num">${tEnc}</div><div class="lbl">遭遇广告</div></div>
      <div class="stat"><div class="num">${tMan}</div><div class="lbl">手动跳过(学习)</div></div>
      <div class="stat"><div class="num">${tAuto}</div><div class="lbl">自动跳过</div></div>
    </div>
    <div class="muted small" style="margin:8px 0 4px">广告方视角：曝光 ${demo.adStats.views} · 有效点击 ${demo.adStats.clicks} · 完整播放 ${demo.adStats.timeouts}</div>
    <div class="muted small" style="margin:2px 0 4px">🛡 判断引擎：拦截 ${js.blocked} · 放行 ${js.allowed} · 纠正 ${js.corrected} · 精准度 ${prec}%（${js.corrected >= 2 ? "纠正≥2，门槛已放宽至 74" : "基准门槛 60"}）</div>
    ${rows}`;
}
$("#btn-reset-demo").addEventListener("click", () => {
  demo = { shieldOn: demo.shieldOn, creativeIdx: 0, per: {}, adStats: { views: 0, clicks: 0, skips: 0, timeouts: 0 }, log: [] };
  saveDemo(); renderDemoReport(); renderDrillStats();
  toast("演练数据已重置");
});

/* ══════════ 关于 ══════════ */
function renderAbout() {
  let v = "浏览器预览", c = "-";
  if (HAS_BRIDGE) {
    try { v = "v" + AndroidApp.versionName(); c = AndroidApp.versionCode(); } catch (e) {}
  }
  $("#about-ver").textContent = v;
  $("#about-code").textContent = c;
  $("#ver-badge").textContent = v === "浏览器预览" ? "浏览器预览" : v;
  $("#brand-sub").textContent = HAS_BRIDGE ? "广告策略演练 · 智能跳过引擎" : "浏览器预览模式 · 安装APK体验完整功能";
}

/* ══════════ Toast ══════════ */
let toastTimer = null;
function toast(msg) {
  let el = $("#__toast");
  if (!el) {
    el = document.createElement("div");
    el.id = "__toast";
    el.style.cssText = "position:fixed;left:50%;bottom:76px;transform:translateX(-50%);background:rgba(24,24,27,.92);color:#fff;font-size:12.5px;padding:9px 16px;border-radius:10px;z-index:999;max-width:86%;text-align:center";
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.style.opacity = "1";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.style.opacity = "0"; }, 1800);
}

/* ══════════ 状态栏时钟 ══════════ */
function tickClock() {
  const d = new Date();
  $("#sb-time").textContent = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/* ══════════ 初始化 ══════════ */
(function init() {
  renderChips();
  renderIntel();
  renderLearningToggle();
  renderAbout();
  renderDrillStats();
  renderDemoReport();
  refreshHome();
  renderReal();
  showFeed();
  tickClock();
  setInterval(tickClock, 15000);
  $("#toggle-shield").checked = demo.shieldOn;
  $("#shield-state").textContent = demo.shieldOn ? "已开启" : "已暂停";
})();

/* ═════ v1.3.0 联网模块：问题反馈（GitHub Issues）+ 检查更新（GitHub Releases）═════
   联网仅两处出口，且全部围绕开源仓库本身：
   · 反馈：优先原生桥 netPostIssue（Token 存本机 SharedPreferences）；
     浏览器预览时若配置了 Token 则直接 fetch（api.github.com 支持 CORS）；
     无 Token 则生成 Issue 预填链接，由用户在浏览器一键提交。
   · 更新：公共仓库匿名可读，查询最新 Release 并比对版本，提供 APK 直链下载。 */
const REPO = "xiaobaiba999/Skip-Ad";
const GH_API = "https://api.github.com/repos/" + REPO;
const GH_RELEASES = "https://github.com/" + REPO + "/releases";
const GH_NEW_ISSUE = "https://github.com/" + REPO + "/issues/new";
const FB_TYPES = { false_block: "误拦截", missed: "漏拦截", misclick: "误触观察有误", crash: "闪退/卡顿", suggestion: "功能建议", other: "其他" };

function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "1.3.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "10300"; }

function openLink(url) {
  if (HAS_BRIDGE) { try { AndroidApp.openUrl(url); return; } catch (e) {} }
  try { window.open(url, "_blank"); } catch (e) { location.href = url; }
}
/* 全局委托：所有 data-url 链接统一走 openLink（避免内联引号转义问题） */
document.addEventListener("click", (e) => {
  const a = e.target.closest("[data-url]");
  if (a) { e.preventDefault(); openLink(decodeURIComponent(a.dataset.url)); }
});
function linkBtn(url, text) {
  return '<a class="gh-link" data-url="' + encodeURIComponent(url) + '" href="javascript:void(0)">' + esc(text) + "</a>";
}

/* 引擎画像（自动附带，不含任何个人数据 / 屏幕内容） */
function engineProfile() {
  const st = getRealState();
  let rules = 0, pkgs = 0;
  try { Object.keys(st.rules || {}).forEach(k => { const n = (st.rules[k] || []).length; rules += n; if (n) pkgs++; }); } catch (e) {}
  const j = demo.judge || { blocked: 0, allowed: 0, corrected: 0 };
  const log = (st.log || []).slice(-8).map(l => "  - " + (l.msg || ""));
  return [
    "- 版本：v" + appVer() + " (code " + appCode() + ") · 环境：" + (HAS_BRIDGE ? "Android APK" : "浏览器预览"),
    "- 真实引擎：自动跳过 " + (((st.stats || {}).auto) || 0) + " 次 · 手动样本 " + (((st.stats || {}).manual) || 0) + " · 已学规则 " + rules + " 条（" + pkgs + " 个应用）",
    "- 演练引擎：拦截 " + (j.blocked || 0) + " · 放行 " + (j.allowed || 0) + " · 误判纠正 " + (j.corrected || 0) + " 次（判定门槛 " + ((j.corrected || 0) >= 2 ? 74 : 60) + "）",
    "- 最近引擎日志：",
    log.length ? log.join("\n") : "  - （暂无）",
  ].join("\n");
}

/* ───── Token 管理（仅存本机；原生桥读写 SharedPreferences 的 gh_token）───── */
function renderTokenState() {
  const t = store.get("gh_token", "");
  $("#fb-token-state").innerHTML = t
    ? "已保存 Token（" + esc(t.slice(0, 7)) + "…· 仅存本机 · 反馈全自动提交）"
    : "未配置：提交时将生成浏览器预填页链接，一键手动提交";
}
$("#btn-token-save").addEventListener("click", () => {
  const t = $("#fb-token").value.trim();
  if (t.length < 10) { toast("Token 格式不正确（ghp_… / github_pat_…）"); return; }
  store.set("gh_token", t);
  renderTokenState();
  toast("Token 已保存到本机");
});
$("#btn-token-clear").addEventListener("click", () => {
  store.set("gh_token", "");
  $("#fb-token").value = "";
  renderTokenState();
  toast("已清除 Token");
});

/* ───── 反馈历史（本地记录）───── */
function fbHistory() { return store.get("fb_history", []); }
function renderFbHistory() {
  const h = fbHistory();
  if (!h.length) { $("#fb-history").innerHTML = ""; return; }
  $("#fb-history").innerHTML =
    '<div class="muted small" style="margin-top:12px">我的反馈记录（本地保存）</div>' +
    h.slice(-6).reverse().map(x => `
      <div class="rule-item">
        <div style="flex:1;min-width:0">
          <div class="rule-detail">[${esc(x.type)}] ${esc(x.desc)}</div>
          <div class="muted small">${esc(x.date)}${x.url ? " · " + linkBtn(x.url, "Issue #" + x.no) : " · 未同步到 GitHub"}</div>
        </div>
      </div>`).join("");
}

/* ───── 提交反馈 ───── */
function fbPrefillUrl(type, desc) {
  const label = FB_TYPES[type] || "其他";
  const title = "[反馈][" + label + "] " + desc.slice(0, 40);
  const body = "## 问题描述\n" + desc + "\n\n## 环境信息\n" + engineProfile() + "\n\n— 由开屏卫士 v" + appVer() + " 反馈系统自动创建";
  return GH_NEW_ISSUE + "?title=" + encodeURIComponent(title) + "&body=" + encodeURIComponent(body);
}

$("#btn-feedback").addEventListener("click", async () => {
  const type = $("#fb-type").value;
  const desc = $("#fb-desc").value.trim();
  const box = $("#fb-result");
  const btn = $("#btn-feedback");
  if (desc.length < 5) { box.innerHTML = '<div class="fb-msg err">请把问题描述得更具体一点（至少 5 个字）</div>'; return; }

  const label = FB_TYPES[type] || "其他";
  const title = "[反馈][" + label + "] " + desc.slice(0, 40) + (desc.length > 40 ? "…" : "");
  let body = "## 问题描述\n" + desc + "\n\n## 环境信息\n";
  body += $("#fb-attach").checked ? engineProfile() + "\n" : "（用户未附加引擎画像）\n";
  body += "\n— 由开屏卫士 v" + appVer() + " 反馈系统自动创建";

  box.innerHTML = '<div class="fb-msg">正在同步到 GitHub…</div>';
  btn.disabled = true;
  let res = null;
  try {
    if (HAS_BRIDGE) {
      res = JSON.parse(AndroidApp.netPostIssue(title, body));
    } else {
      const token = store.get("gh_token", "");
      if (token) {
        const r = await fetch(GH_API + "/issues", {
          method: "POST",
          headers: { Authorization: "Bearer " + token, Accept: "application/vnd.github+json", "Content-Type": "application/json" },
          body: JSON.stringify({ title, body }),
        });
        const d = await r.json().catch(() => ({}));
        res = r.ok ? { ok: true, url: d.html_url, number: d.number }
                   : { ok: false, error: "GitHub 返回 " + r.status + "：" + (d.message || "未知"), fallbackUrl: fbPrefillUrl(type, desc) };
      } else {
        res = { ok: false, error: "浏览器预览未配置 Token", fallbackUrl: fbPrefillUrl(type, desc) };
      }
    }
  } catch (e) {
    res = { ok: false, error: "提交异常：" + (e.message || e), fallbackUrl: fbPrefillUrl(type, desc) };
  }
  btn.disabled = false;

  const h = fbHistory();
  h.push({ date: new Date().toLocaleString("zh-CN", { hour12: false }), type: label, desc: desc.slice(0, 50), url: res && res.ok ? res.url : "", no: (res && res.number) || 0 });
  store.set("fb_history", h.slice(-30));
  renderFbHistory();

  if (res && res.ok) {
    box.innerHTML = '<div class="fb-msg ok">已同步到 GitHub Issue ' + linkBtn(res.url, "#" + res.number + " · 点击查看") + "，感谢反馈，它会成为下一版迭代的依据</div>";
    $("#fb-desc").value = "";
    toast("反馈已同步到 GitHub Issues");
  } else {
    box.innerHTML = '<div class="fb-msg err">' + esc((res && res.error) || "未知错误") +
      (res && res.fallbackUrl ? "<br>" + linkBtn(res.fallbackUrl, "在浏览器中预填提交 →") : "") + "</div>";
  }
});

/* ───── 检查更新（GitHub Releases）───── */
function cmpVer(a, b) {
  const pa = String(a).replace(/^v/, "").split("."), pb = String(b).replace(/^v/, "").split(".");
  for (let i = 0; i < 3; i++) {
    const d = (parseInt(pa[i], 10) || 0) - (parseInt(pb[i], 10) || 0);
    if (d) return d > 0 ? 1 : -1;
  }
  return 0;
}

function renderUpdate(d) {
  const body = $("#update-body");
  if (!d || !d.ok) {
    body.innerHTML = '<div class="fb-msg err">查询失败：' + esc((d && d.error) || "未知错误") + "<br>" + linkBtn(GH_RELEASES, "手动打开 Releases 页 →") + "</div>";
    return;
  }
  if (!d.hasRelease) {
    body.innerHTML = '<div class="muted small">仓库尚未发布任何版本，发布后这里会第一时间提醒。</div>' + linkBtn(GH_RELEASES, "打开 Releases 页 →");
    return;
  }
  const latest = String(d.tag || "").replace(/^v/, "");
  const cur = appVer();
  const isNewer = cmpVer(latest, cur) > 0;
  const notes = (d.notes || "").split("\n").filter(l => l.trim()).slice(0, 6)
    .map(l => '<div class="muted small">· ' + esc(l.replace(/^#+\s*/, "").replace(/^[-*]\s*/, "").slice(0, 64)) + "</div>").join("");
  body.innerHTML =
    '<div class="kv"><span>最新版本</span><b>' + esc(d.tag || latest) + (d.apkMB ? " · " + (d.apkMB >= 1 ? d.apkMB + " MB" : Math.round(d.apkMB * 1024) + " KB") : "") + "</b></div>" +
    '<div class="kv"><span>当前版本</span><b>v' + esc(cur) + "</b></div>" +
    (isNewer
      ? '<div class="fb-msg ok">发现新版本！与当前版本同一签名，下载后直接安装即可覆盖升级，无需卸载，学习数据全部保留。</div>' +
        (d.apkUrl ? '<button class="btn btn-primary btn-block" id="btn-dl-apk">下载新版 APK</button>' : "") +
        linkBtn(d.url || GH_RELEASES, "查看完整发布说明 →")
      : '<div class="fb-msg ok">已是最新版本。每次迭代都会发布到 GitHub Releases，可随时回来检查。</div>') +
    (notes ? '<div style="margin-top:8px">' + notes + "</div>" : "");
  const dl = $("#btn-dl-apk");
  if (dl) dl.addEventListener("click", () => {
    openLink(d.apkUrl);
    toast("已开始下载，安装时选择「仍要安装」即可覆盖升级");
  });
}

$("#btn-check-update").addEventListener("click", async () => {
  const btn = $("#btn-check-update");
  btn.disabled = true;
  $("#update-body").innerHTML = '<div class="muted small">正在查询 GitHub Releases…</div>';
  let d = null;
  try {
    if (HAS_BRIDGE) {
      d = JSON.parse(AndroidApp.netGetLatestRelease());
    } else {
      const r = await fetch(GH_API + "/releases/latest", { headers: { Accept: "application/vnd.github+json" } });
      if (r.status === 404) d = { ok: true, hasRelease: false };
      else if (r.ok) {
        const j = await r.json();
        const apk = (j.assets || []).filter(a => a && a.name && a.name.endsWith(".apk"))[0];
        d = {
          ok: true, hasRelease: true, tag: j.tag_name, name: j.name, notes: j.body,
          url: j.html_url, publishedAt: j.published_at,
          apkUrl: apk ? apk.browser_download_url : "",
          apkMB: apk ? Math.round(apk.size / 1048576 * 100) / 100 : 0,
        };
      } else d = { ok: false, error: "GitHub 返回 " + r.status };
    }
  } catch (e) { d = { ok: false, error: "网络异常：" + (e.message || e) }; }
  btn.disabled = false;
  renderUpdate(d);
});

/* v1.3.0 模块初始化 */
renderTokenState();
renderFbHistory();
