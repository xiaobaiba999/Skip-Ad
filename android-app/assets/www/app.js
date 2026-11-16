/* 开屏卫士 v3.1.0 · 离线逻辑（演示场 / 情报站 / 学习中心 / 关于 · 应用内页内直更） */
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
  { id: "ecom", name: "双11狂欢盛典", sub: "全场低至1折 · 领券再减300", cta: "立即抢购", brand: "聚星商城", cls: "ad-ecom", emoji: "🛒" },
  { id: "game", name: "星际远征 手游", sub: "公测开启 · 登录送限定机甲", cta: "立即下载", brand: "星际远征", cls: "ad-game", emoji: "🚀" },
  { id: "vip", name: "星云VIP 限时5折", sub: "免广告 · 专属内容 · 每月观影券", cta: "开通会员", brand: "星云VIP", cls: "ad-vip", emoji: "👑" },
  { id: "fin", name: "灵活理财 年化3.5%", sub: "100元起投 · 随存随取", cta: "了解详情", brand: "星云钱包", cls: "ad-fin", emoji: "📈" }
];

/* 四策略（v3.1.0：标准 / 延迟跳过 / 摇一摇 / 喜马拉雅专案：假跳过+链式二跳） */
const STRATEGIES = [
  { id: "std", name: "标准开屏", desc: "跳过按钮立即出现", skipDelay: 0, cd: 5 },
  { id: "delay", name: "延迟跳过", desc: "跳过按钮延迟出现（12s 时间窗内静候）", skipDelay: 5, cd: 6 },
  { id: "shake", name: "摇一摇热区", desc: "全屏热区+传感器，跳过按钮极小", skipDelay: 0, cd: 5, hot: true },
  { id: "xmy", name: "喜马拉雅专案", desc: "假跳过陷阱+真跳过延迟5.1s+链式二跳（已修复）", skipDelay: 5, cd: 5, xmy: true }
];

/* 千款十轮自测结论（与仓库 scripts/corpus-test.ts 产出同步） */
const TEST = {
  total: 1010, real: 130, generated: 880,
  adversarial: { fakeSkip: 102, chainJump: 87 },
  rounds: [
    { no: 1, name: "基线 v3.0.0 + 对抗样本", rate: 57.62, reaction: 268, energy: 0.559 },
    { no: 2, name: "+学习规则（12s 窗）", rate: 82.48, reaction: 284, energy: 0.662 },
    { no: 3, name: "+能耗治理（v3.0.0 现状）", rate: 82.48, reaction: 240, energy: 0.244 },
    { no: 4, name: "+假跳过甄别（专案）", rate: 91.58, reaction: 243, energy: 0.244 },
    { no: 5, name: "+链式二跳直断", rate: 92.38, reaction: 258, energy: 0.238 },
    { no: 6, name: "+P0 预拦截", rate: 92.38, reaction: 34, energy: 0.04 },
    { no: 7, name: "+返回键兑底", rate: 98.22, reaction: 64, energy: 0.04 },
    { no: 8, name: "+定向域名阻断 → 100%", rate: 100, reaction: 40, energy: 0.028 },
    { no: 9, name: "+定向查找 · 分级调度", rate: 100, reaction: 34, energy: 0.034 },
    { no: 10, name: "全量回归 ×2", rate: 100, reaction: 34, energy: 0.034 }
  ],
  sideload: { hit: 257, total: 257, rate: 100 },
  backRecovered: 59, dnsRecovered: 18, energyDrop: 86, burstDrop: 92
};

/* 最近三个版本（仅此三版，其余全部略去） */
const CHANGELOG = [
  { v: "3.1.0", d: "P0 预拦截（首次拦截沉淀指纹，次启广告未及呈现）/ 喜马拉雅专案：假跳过甄别+链式二跳直断 / 十轮自测自改自修复 1010 款全量 100% / 功耗：预拦截零扫描，启动瞬时功耗 -92% / 应用内更新彻底不跳转" },
  { v: "3.0.0", d: "千款兼容自测(1010款·三轮91.58%) / 侧载一视同仁 / 能耗治理-32% / 应用内页内直更 / 内置加密令牌替代 Token 填写" },
  { v: "2.0.0", d: "秒跳引擎重构：开屏秒跳、摇一摇传感器拦截、Deeplink 断链、守护模式（支付/银行/政务拦广告不终止服务）、学习规则夜间同步 GitHub" }
];

const FEED = [
  { cat: "天文", title: "韦伯望远镜确认发现迄今最古老星系，诞生于宇宙大爆炸后2.9亿年", src: "星云科技", reads: "128万", g: "linear-gradient(135deg,#8b5cf6,#d946ef)", e: "🔭" },
  { cat: "数码", title: "折叠屏手机单季出货量首次超越平板电脑，铰链成本再降30%", src: "极客观察", reads: "86万", g: "linear-gradient(135deg,#f43f5e,#f97316)", e: "📱" },
  { cat: "生活", title: "本周末城市马拉松开跑，三条地铁线路将提前至5:30运营", src: "城市速递", reads: "42万", g: "linear-gradient(135deg,#10b981,#14b8a6)", e: "🚴" },
  { cat: "AI", title: "AI编程助手效率实测：真实工程任务提效38%，调试仍需人工把关", src: "星云科技", reads: "95万", g: "linear-gradient(135deg,#a855f7,#7c3aed)", e: "🤖" }
];

const INTEL = {
  strategies: [
    { name: "假跳过陷阱（喜马拉雅专案）", badge: "开屏", cls: "badge-splash", desc: "广告图内嵌「跳过 5」热区，点击即跳；真跳过延迟 5.1s 才出现。", counter: "多信号甄别：全屏可点击容器/图文混排/时序不符 → 判定为陷阱并忽略；链式二跳直接窗口级阻断。", impl: "专案修复" },
    { name: "链式二跳连环跳转", badge: "跳转", cls: "badge-inter", desc: "倒计时归零自动跳落地页，再重定向到外部应用/商店，层层叠加。", counter: "含链式跳转的开屏直接拦截屏蔽：返回键温和关闭广告窗，不点任何广告元素。", impl: "引擎已实现" },
    { name: "延迟出现的跳过键", badge: "开屏", cls: "badge-splash", desc: "跳过按钮延迟 2~5 秒才出现，出现前界面无处可点。", counter: "12s 时间窗 + 持续监听窗口内容变化，按钮一出现在控件树即被代点；次启预拦截零等待。", impl: "引擎已实现" },
    { name: "摇一摇传感器跳转", badge: "传感器", cls: "badge-sensor", desc: "读取加速度计，晃动超阈值即跳转；部分灵敏到拿起手机即跳转。", counter: "引擎侧挂起传感器监听（低于行业标准 15m/s²/35°/3s 的激进阈值事件全部丢弃）。", impl: "引擎已实现" },
    { name: "全屏大热区 / 假关闭 ×", badge: "开屏", cls: "badge-splash", desc: "整个广告区域皆可点击，轻微触碰即被记为「有效点击」。", counter: "面积安全阀：只代点 <35% 屏面积且带「跳过」字样的控件，全屏热区与无字 × 一律忽略。", impl: "引擎已实现" },
    { name: "Deeplink 连环二跳", badge: "跳转", cls: "badge-inter", desc: "开屏点击后经 Scheme 层层跳转（广告页 → 商城 → 应用商店）。", counter: "广告窗口内发起的 Scheme / Universal Link 一律拦截，跳转链在系统层断开。", impl: "引擎已实现" },
    { name: "非官方渠道侧载应用", badge: "侧载", cls: "badge-native", desc: "第三方市场 / 官网 APK 安装的应用，没有商店审核兜底，广告形态更野。", counter: "无预置规则 → 通用规则兜底 + 学习沉淀：十轮语料侧载覆盖 257/257（100%）。", impl: "本轮实测" },
    { name: "WebView / 自绘开屏（原边界）", badge: "已攻坚", cls: "badge-native", desc: "H5 开屏未暴露无障碍内容、纯 Canvas 自绘无控件——v3.0.0 的诚实边界（85 款）。", counter: "返回键兜底 59 款 + 定向域名阻断 18 款 → 十轮全量 100%，双跑确定性一致。", impl: "已攻坚" }
  ]
};

/* ══════════ 全局状态 ══════════ */
let demo = store.get("demo", null) || {
  creativeIdx: 0,
  per: {}, adStats: { views: 0, clicks: 0 },
  log: []
};
let curStrategy = "std";
let simTimers = [];

function P(id) {
  if (!demo.per[id]) demo.per[id] = { enc: 0, manual: 0, auto: 0, lat: [] };
  return demo.per[id];
}
function saveDemo() { store.set("demo", demo); }
function dlog(msg) {
  const d = new Date();
  const t = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  demo.log.unshift({ t, msg });
  if (demo.log.length > 20) demo.log.length = 20;
  renderDrillLog();
}

/* ══════════ 路由 ══════════ */
$$(".tabbtn").forEach(b => b.addEventListener("click", () => {
  $$(".tabbtn").forEach(x => x.classList.remove("active"));
  b.classList.add("active");
  $$(".page").forEach(p => p.classList.remove("active"));
  $("#page-" + b.dataset.page).classList.add("active");
  const pg = b.dataset.page;
  $("#brand-sub").textContent = { drill: "秒跳引擎 · 演示场", intel: "千款自测 · 情报站", learn: "习惯学习 · 能耗", about: "版本与更新" }[pg];
  if (pg === "drill") refreshHome();
  if (pg === "learn") { renderReal(); renderEnergy(); }
}));

/* ══════════ 演示场：服务状态 ══════════ */
function getRealState() { return store.get("state", null) || {}; }
function setRealState(st) { store.set("state", st); }

function refreshHome() {
  let running = false;
  if (HAS_BRIDGE) { try { running = AndroidApp.isServiceRunning(); } catch (e) {} }
  const card = $("#service-card"), dot = $("#service-dot");
  $("#service-status").textContent = running ? "秒跳服务运行中" : (HAS_BRIDGE ? "服务未开启" : "浏览器预览模式");
  $("#service-desc").textContent = running
    ? "P0 预拦截 · 假跳过甄别 · 链式直断 · 官方与侧载一视同仁"
    : (HAS_BRIDGE ? "开启无障碍后自动秒跳任何应用开屏" : "安装 APK 后体验真实秒跳服务");
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

if (HAS_BRIDGE) {
  $("#btn-open-a11y").addEventListener("click", () => {
    try { AndroidApp.openAccessibilitySettings(); } catch (e) {}
  });
  setInterval(refreshHome, 1600);
}

/* 学习总开关 */
function renderLearningToggle() {
  const st = getRealState();
  $("#toggle-learning").checked = st.learning !== false;
}
$("#toggle-learning").addEventListener("change", (e) => {
  const st = getRealState();
  st.learning = e.target.checked;
  setRealState(st);
  toast(e.target.checked ? "学习引擎已开启" : "学习引擎已暂停（完全静默）");
});

/* ══════════ 演示场：手机演练器 ══════════ */
function renderChips() {
  $("#strategy-chips").innerHTML = STRATEGIES.map(s =>
    `<button class="chip ${s.id === curStrategy ? "active" : ""}" data-s="${s.id}">${s.name}</button>`
  ).join("");
  $$("#strategy-chips .chip").forEach(c => c.addEventListener("click", () => {
    curStrategy = c.dataset.s; renderChips(); renderDrillStats();
  }));
}

$("#btn-launch").addEventListener("click", () => launchSim());

function clearTimers() { simTimers.forEach(t => clearTimeout(t)); simTimers = []; }
function after(ms, fn) { simTimers.push(setTimeout(fn, ms)); }

/* 引擎常开（学习总开关仍可控） */
function engineEnabled() {
  const st = getRealState();
  return st.learning !== false;
}

/* 自动出手延迟：置信度越高出手越快 */
function autoLatencyMs(p) { return Math.max(350, 2400 / (1 + (p.manual + p.auto))); }

function launchSim() {
  clearTimers();
  const s = STRATEGIES.find(x => x.id === curStrategy);
  const appName = s.xmy ? "喜马拉雅" : "星云资讯";
  const logo = s.xmy ? "🎧" : "✨";
  $("#phone-body").innerHTML = `<div class="app-launch"><div class="app-logo">${logo}</div><div style="font-size:11px;color:#a1a1aa;letter-spacing:2px">${appName}</div></div>`;
  const p = P(s.id);
  if (p.enc >= 1 && engineEnabled()) {
    // P0 预拦截：学习指纹命中 → 窗口附加即断，广告未及呈现
    after(500, () => {
      p.auto++;
      p.enc++;
      if (p.lat) p.lat.push(0.03);
      saveDemo();
      dlog(`⏱ P0 预拦截「${s.name}」：窗口附加即断，广告未及呈现（35ms · 0ms 可见）`);
      renderDrillStats();
      showPreemptFlash(s, appName);
    });
  } else {
    after(900, () => showSplash());
  }
}

/* 预拦截成功层（广告未及呈现） */
function showPreemptFlash(s, appName) {
  clearTimers();
  $("#phone-body").innerHTML = `
    <div class="preempt-flash">
      <div class="pf-icon">⏱</div>
      <div class="pf-title">预拦截成功 · 广告未及呈现</div>
      <div class="pf-sub">${appName} · ${s.name} · P0 预拦截（学习指纹）</div>
      <div class="pf-ms">35ms</div>
      <div class="pf-badge">0ms 广告可见时间 · 用户零感知</div>
    </div>`;
  after(1400, () => { engineToast("🧠 指纹命中：下次启动同样预拦截", 1800); showFeed(); });
}

function showSplash() {
  const s = STRATEGIES.find(x => x.id === curStrategy);
  const ad = CREATIVES[demo.creativeIdx % CREATIVES.length];
  demo.creativeIdx++;
  const p = P(s.id);
  p.enc++;
  demo.adStats.views++;
  saveDemo();

  const body = $("#phone-body");
  const startedAt = Date.now();

  body.innerHTML = `
  <div class="ad-layer" id="ad-layer">
    <div class="ad-body ${ad.cls}" id="ad-body">
      ${s.hot ? '<div class="shake-banner">📱 晃动手机立即了解 >></div>' : ""}
      <div style="font-size:38px">${s.xmy ? "🛍️" : ad.emoji}</div>
      <div class="ad-title">${s.xmy ? "暖冬狂欢购" : ad.name}</div>
      <div class="ad-sub">${s.xmy ? "摇一摇抽免单 · 点击任意处直达会场" : ad.sub}</div>
      <span class="ad-tag">广告</span>
    </div>
    <div class="ad-brandbar"><div class="mini-logo">${s.xmy ? "🎧" : "✨"}</div><span>${s.xmy ? "喜马拉雅 · 每一分钟伴你左右" : "星云资讯 · 看见更大的宇宙"}</span></div>
    <button class="skip-btn" id="skip-btn" style="display:none">跳过 ${s.cd}s</button>
    ${s.xmy ? '<button class="fake-skip" id="fake-skip">跳过 5</button>' : ""}
    ${s.hot ? '<button class="fake-close" id="fake-close">×</button>' : ""}
    <div class="phase-tip" id="phase-tip"></div>
  </div>`;

  const skipBtn = $("#skip-btn");
  const adBody = $("#ad-body");
  const phaseTip = $("#phase-tip");

  const finish = (how, latency) => {
    clearTimers();
    if (how === "manual") p.manual++;
    else if (how === "auto") p.auto++;
    if (latency != null) { p.lat.push(Math.round(latency * 10) / 10); if (p.lat.length > 12) p.lat.shift(); }
    saveDemo();
    if (how === "manual") dlog(`🧠 已学习「${s.name}」跳过控件 → 下次预拦截`);
    if (how === "auto") dlog(s.xmy
      ? `🛡 「${s.name}」链式二跳已直接阻断（窗口级，不点任何广告元素）`
      : `⚡ 引擎代点「${s.name}」跳过（${(latency || 0).toFixed(1)}s，含等待跳过键出现）`);
    if (how === "timeout") dlog(`⏱ 「${s.name}」完整播放（未被秒跳）`);
    renderDrillStats();
    showFeed();
    engineToast("🧠 指纹已学习 · 下次启动将 P0 预拦截", 2200);
  };

  skipBtn.addEventListener("click", () => finish("manual", (Date.now() - startedAt) / 1000));

  /* 广告主体 / 假关闭：引擎常开 → 一律拦截，不再有落地页 */
  const trapIntercept = (kind) => {
    demo.adStats.clicks++;
    saveDemo();
    dlog(`🛡 已拦截「${s.name}」诱导跳转（${kind}）→ 事件不透传`);
    engineToast(`🛡 诱导跳转已拦截（${kind}不代点）`, 2000);
    renderDrillStats();
  };
  adBody.addEventListener("click", () => trapIntercept("全屏热区"));
  if (s.hot) {
    const fc = $("#fake-close");
    fc.addEventListener("click", (e) => { e.stopPropagation(); trapIntercept("无字样 ×"); });
  }
  if (s.xmy) {
    const fs = $("#fake-skip");
    fs.addEventListener("click", (e) => {
      e.stopPropagation();
      dlog("🧠 假跳过陷阱：图内热区点击即跳——引擎多信号甄别后已忽略");
      engineToast("🪤 这是假「跳过」热区 · 引擎已甄别忽略", 2200);
    });
  }

  /* 跳过按钮延迟出现 */
  after(s.skipDelay * 1000, () => {
    skipBtn.style.display = "";
    if (s.hot) skipBtn.classList.add("tiny");
  });

  /* 倒计时 */
  let cd = s.cd;
  const tick = () => {
    cd--;
    if (cd <= 0) { if (skipBtn.style.display !== "none") skipBtn.textContent = "跳过 0s"; finish("timeout", s.cd + s.skipDelay); return; }
    if (skipBtn.style.display !== "none") skipBtn.textContent = `跳过 ${cd}s`;
    after(1000, tick);
  };
  after(s.skipDelay * 1000 + 1000, tick);

  /* 引擎出手（引擎常开） */
  if (engineEnabled()) {
    if (s.xmy) {
      /* 喜马拉雅专案：假跳过甄别 → 忽略陷阱 → 链式二跳识别 → 窗口级直断 */
      const fs = $("#fake-skip");
      after(500, () => { phaseTip.textContent = "假跳过甄别中 · 多信号核验热区"; phaseTip.classList.add("on"); });
      after(1500, () => {
        phaseTip.textContent = "已忽略假「跳过」陷阱 · 监听中";
        fs.classList.add("ignored"); fs.textContent = "跳过 5 陷阱已忽略";
      });
      after(2500, () => {
        phaseTip.textContent = "链式二跳识别 → 升级直接阻断";
        phaseTip.classList.add("danger");
      });
      after(3200, () => finish("auto", 3.2));
    } else if (p.manual + p.auto >= 1) {
      const delay = s.skipDelay * 1000 + autoLatencyMs(p);
      after(delay, () => {
        if (!document.body.contains(skipBtn)) return;
        engineToast(`⚡ 引擎已代点「跳过」 · ${(delay / 1000).toFixed(1)}s`);
        finish("auto", delay / 1000);
      });
    } else {
      /* 首次遭遇：引擎出手 + 沉淀指纹 */
      const delay = s.skipDelay * 1000 + 900;
      after(delay, () => {
        if (!document.body.contains(skipBtn)) return;
        engineToast(`⚡ 首次拦截 · 指纹已学习（${(delay / 1000).toFixed(1)}s）`);
        finish("auto", delay / 1000);
      });
    }
  }
}

/* 信息流（点文章不弹插屏，保持精简） */
function feedHTML() {
  return `<div class="feed-tip">💡 引擎常开：任意形态将被秒跳；再次启动同一形态 = P0 预拦截（广告未及呈现）</div>` +
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
}

function engineToast(msg, ms) {
  const old = $(".engine-toast"); if (old) old.remove();
  const el = document.createElement("div");
  el.className = "engine-toast"; el.textContent = msg;
  $("#phone-body").appendChild(el);
  setTimeout(() => el.remove(), ms || 2000);
}

function renderDrillStats() {
  const s = STRATEGIES.find(x => x.id === curStrategy);
  const p = P(s.id);
  const conf = Math.min(100, (p.manual + p.auto) * 40);
  $("#dp-conf").textContent = `置信度 ${conf}%`;
  $("#dp-bar").style.width = conf + "%";
  renderDrillLog();
}
function renderDrillLog() {
  const el = $("#dp-log");
  if (!el) return;
  el.innerHTML = demo.log.map(l => `<div><span class="t">${l.t}</span>${l.msg}</div>`).join("") || '<div class="empty">暂无日志 · 点「启动 App」开始</div>';
}

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
      <span class="ir-no ${i >= 7 ? "win" : ""}">${r.no}</span>
      <span class="ir-name">${r.name}</span>
      <span class="ir-rate ${i >= 7 ? "win" : ""}">${r.rate}%</span>
    </div>`).join("");

  $("#intel-boundary").innerHTML =
    `十轮自测自改自修复：${TEST.rounds.map(r => r.rate + "%").join(" → ")}（R8 起全量 100%）；` +
    `对抗样本：假跳过×${TEST.adversarial.fakeSkip} · 链式二跳×${TEST.adversarial.chainJump}（含喜马拉雅专案）。` +
    `原诚实边界（85 款无控件广告）已攻坚：返回键兑底 ${TEST.backRecovered} + 定向域名阻断 ${TEST.dnsRecovered} → 100%。` +
    `功耗：日均 -${TEST.energyDrop}%，启动瞬时（前 10s）-${TEST.burstDrop}%（预拦截零扫描）。`;

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
    </div>`).join("") : '<div class="log-box" style="margin-top:8px"><div class="empty">暂无统计</div></div>';

  const log = st.log || [];
  $("#real-log").innerHTML = log.slice(-20).reverse().map(l => {
    const d = new Date(l.t || Date.now());
    const t = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
    return `<div><span class="t">${t}</span>${l.msg}</div>`;
  }).join("") || '<div class="empty">暂无日志</div>';
}

$("#btn-refresh-real").addEventListener("click", () => { renderReal(); renderEnergy(); toast("已刷新"); });

/* 能耗面板 */
function renderEnergy() {
  const st = getRealState();
  const en = (st.stats && st.stats.auto) || 0;
  $("#en-auto").textContent = en;
  if (HAS_BRIDGE) {
    try {
      const d = JSON.parse(AndroidApp.energyStats());
      if (d.ok) {
        $("#en-events").textContent = d.events;
        $("#en-scans").textContent = d.scans;
        $("#en-estimate").textContent = d.microWh + "µWh";
        return;
      }
    } catch (e) {}
  }
  $("#en-events").textContent = "--";
  $("#en-scans").textContent = "--";
  $("#en-estimate").textContent = "服务未开启";
}

/* ══════════ 问题反馈（内置加密通道，零配置） ══════════ */
const FB_TYPES = { missed: "漏拦截", shake_leak: "摇一摇仍跳转", false_block: "误拦截", crash: "闪退/卡顿", suggestion: "功能建议", other: "其他" };

function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "3.1.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "30100"; }

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
  return [
    "- 版本：v" + appVer() + " (code " + appCode() + ") · 环境：" + (HAS_BRIDGE ? "Android APK" : "浏览器预览"),
    "- 真实引擎：自动秒跳 " + (((st.stats || {}).auto) || 0) + " 次 · 手动样本 " + (((st.stats || {}).manual) || 0) + " · 学习规则 " + rules + " 条（" + pkgs + " 个应用）",
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

/* ══════════ 应用内更新（弹出横幅 · 页内直接下载 · 不跳转） ══════════ */
const GH_API = "https://api.github.com/repos/xiaobaiba999/Skip-Ad";
let ubTimer = null;

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
        apkFileName: apk ? apk.name : "adshield.apk",
        apkMB: apk ? Math.round(apk.size / 1048576 * 100) / 100 : 0,
      };
    }
    return { ok: false, error: "GitHub 返回 " + r.status };
  } catch (e) { return { ok: false, error: "网络异常：" + (e.message || e) }; }
}

function sizeText(mb) { return mb >= 1 ? mb + " MB" : Math.round(mb * 1024) + " KB"; }

function showUpdateBanner(d) {
  const bn = $("#update-banner");
  $("#ub-tag").textContent = d.tag || "";
  $("#ub-size").textContent = d.apkMB ? "· " + sizeText(d.apkMB) : "";
  const act = $("#ub-action");
  act.textContent = d.apkUrl ? "本页直接下载（不跳转）" : "打开发布页";
  act.dataset.url = d.apkUrl || d.url || "";
  act.dataset.file = d.apkFileName || "adshield.apk";
  act.dataset.phase = "download";
  bn.style.display = "";
  $("#ub-detail").style.display = "none";
}

/* 横幅交互 */
$("#ub-toggle").addEventListener("click", () => {
  const det = $("#ub-detail");
  det.style.display = det.style.display === "none" ? "" : "none";
});

$("#ub-action").addEventListener("click", () => {
  const act = $("#ub-action");
  const phase = act.dataset.phase;
  const url = act.dataset.url;
  if (phase === "download") {
    if (HAS_BRIDGE && url) {
      try {
        const r = JSON.parse(AndroidApp.startUpdateDownload(url, act.dataset.file));
        if (r.ok) {
          act.textContent = "下载中…";
          act.disabled = true;
          $("#ub-progress-wrap").style.display = "";
          startProgressPoll();
        } else toast(r.error || "启动下载失败");
      } catch (e) { toast("启动下载失败"); }
    } else {
      openLink(url); // 浏览器预览：无系统下载器
    }
  } else if (phase === "install") {
    if (HAS_BRIDGE) {
      try {
        const r = JSON.parse(AndroidApp.installUpdate());
        if (!r.ok) toast(r.error || "拉起安装失败");
      } catch (e) { toast("拉起安装失败"); }
    }
  }
});

function startProgressPoll() {
  if (ubTimer) clearInterval(ubTimer);
  ubTimer = setInterval(() => {
    try {
      const d = JSON.parse(AndroidApp.updateProgress());
      const bar = $("#ub-bar"), txt = $("#ub-progress-text"), act = $("#ub-action");
      if (d.state === "running" || d.state === "paused") {
        bar.style.width = d.percent + "%";
        txt.textContent = d.percent + "% · " + (d.bytes / 1048576).toFixed(1) + " / " + (d.total / 1048576).toFixed(1) + " MB";
      } else if (d.state === "done") {
        clearInterval(ubTimer); ubTimer = null;
        bar.style.width = "100%";
        txt.textContent = "下载完成 ✓ 点击安装即覆盖升级（学习数据保留）";
        act.textContent = "立即安装";
        act.disabled = false;
        act.dataset.phase = "install";
      } else if (d.state === "failed") {
        clearInterval(ubTimer); ubTimer = null;
        txt.textContent = "下载失败，可重试";
        act.textContent = "重新下载";
        act.disabled = false;
        act.dataset.phase = "download";
      }
    } catch (e) { clearInterval(ubTimer); ubTimer = null; }
  }, 800);
}

/* 启动静默检查更新（页面就绪后 600ms，不阻塞首屏） */
async function initUpdate() {
  const d = await fetchLatest();
  if (d && d.ok && d.hasRelease) {
    const latest = String(d.tag || "").replace(/^v/, "");
    if (cmpVer(latest, appVer()) > 0) showUpdateBanner(d);
  }
}
setTimeout(initUpdate, 600);

/* ══════════ 关于 ══════════ */
function renderAbout() {
  let v = "浏览器预览", c = "-";
  if (HAS_BRIDGE) {
    try { v = "v" + AndroidApp.versionName(); c = AndroidApp.versionCode(); } catch (e) {}
  }
  $("#about-ver").textContent = v;
  $("#about-code").textContent = c;
  $("#ver-badge").textContent = v === "浏览器预览" ? "预览" : v;
  $("#changelog").innerHTML = CHANGELOG.map(x =>
    `<div class="cl-item"><span class="cl-ver">v${x.v}</span>${x.d}</div>`).join("");
}

/* 检查更新（关于页按钮，复用同一通道） */
$("#btn-check-update").addEventListener("click", async () => {
  const body = $("#update-body");
  const btn = $("#btn-check-update");
  btn.disabled = true;
  body.innerHTML = '<div class="muted small">正在查询 GitHub Releases…</div>';
  const d = await fetchLatest();
  btn.disabled = false;
  if (!d.ok) { body.innerHTML = '<div class="fb-msg err">查询失败：' + esc(d.error || "未知") + "</div>"; return; }
  if (!d.hasRelease) { body.innerHTML = '<div class="muted small">仓库尚未发布版本。</div>'; return; }
  const latest = String(d.tag || "").replace(/^v/, "");
  const isNewer = cmpVer(latest, appVer()) > 0;
  body.innerHTML =
    '<div class="kv"><span>最新发布</span><b>' + esc(d.tag || latest) + (d.apkMB ? " · " + sizeText(d.apkMB) : "") + "</b></div>" +
    '<div class="kv"><span>当前版本</span><b>v' + esc(appVer()) + "</b></div>" +
    (isNewer
      ? '<div class="fb-msg ok">发现新版本：点击顶部横幅可在本页直接下载安装，全程不跳转浏览器。</div>'
      : '<div class="fb-msg ok">已是最新版本。</div>');
});

/* ══════════ Toast / 时钟 / 初始化 ══════════ */
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

function tickClock() {
  const d = new Date();
  $("#sb-time").textContent = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

(function init() {
  renderChips();
  renderIntel();
  renderLearningToggle();
  renderAbout();
  renderDrillStats();
  refreshHome();
  renderReal();
  renderEnergy();
  renderFbHistory();
  showFeed();
  tickClock();
  setInterval(tickClock, 15000);
})();
