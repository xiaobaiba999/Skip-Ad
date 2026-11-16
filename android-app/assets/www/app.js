/* 开屏卫士 v3.0.0 · 离线逻辑（演示场 / 情报站 / 学习中心 / 关于 · 应用内页内直更） */
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

/* 三策略精简版（原 4 策略 → 3：标准 / 延迟跳过 / 摇一摇） */
const STRATEGIES = [
  { id: "std", name: "标准开屏", desc: "跳过按钮立即出现", skipDelay: 0, cd: 5 },
  { id: "delay", name: "延迟跳过", desc: "跳过按钮5秒才出现（时间窗治理的目标形态）", skipDelay: 5, cd: 6 },
  { id: "shake", name: "摇一摇热区", desc: "全屏热区+传感器，跳过按钮极小", skipDelay: 0, cd: 5, hot: true }
];

/* 千款自测结论（与仓库 scripts/corpus-test.ts 产出同步） */
const TEST = {
  total: 1010, real: 130, generated: 880,
  rounds: [
    { no: 1, name: "预置全局规则（3.5s 窗口）", rate: 63.76, reaction: 280, energy: 0.41 },
    { no: 2, name: "+学习规则（viewId / 时机 12s）", rate: 91.58, reaction: 294, energy: 0.82 },
    { no: 3, name: "+仅前台 / 预算260 / 去抖", rate: 91.58, reaction: 249, energy: 0.28 }
  ],
  sideload: { hit: 234, total: 257, rate: 91.1 },
  boundary: 85, energyDrop: 32
};

/* 最近三个版本（仅此三版，其余全部略去） */
const CHANGELOG = [
  { v: "3.0.0", d: "千款兼容自测(1010款·91.58%) / 侧载一视同仁(91.1%) / 能耗治理-32%(0.28mWh/天) / 应用内页内直更(免跳转下载+安装) / 内置加密令牌替代 Token 填写" },
  { v: "2.0.0", d: "秒跳引擎重构：开屏秒跳、摇一摇传感器拦截、Deeplink 断链、守护模式（支付/银行/政务拦广告不终止服务）、学习规则夜间同步 GitHub" },
  { v: "1.3.0", d: "联网能力：问题反馈同步 GitHub Issues、Releases 检查更新" }
];

const FEED = [
  { cat: "天文", title: "韦伯望远镜确认发现迄今最古老星系，诞生于宇宙大爆炸后2.9亿年", src: "星云科技", reads: "128万", g: "linear-gradient(135deg,#8b5cf6,#d946ef)", e: "🔭" },
  { cat: "数码", title: "折叠屏手机单季出货量首次超越平板电脑，铰链成本再降30%", src: "极客观察", reads: "86万", g: "linear-gradient(135deg,#f43f5e,#f97316)", e: "📱" },
  { cat: "生活", title: "本周末城市马拉松开跑，三条地铁线路将提前至5:30运营", src: "城市速递", reads: "42万", g: "linear-gradient(135deg,#10b981,#14b8a6)", e: "🚴" },
  { cat: "AI", title: "AI编程助手效率实测：真实工程任务提效38%，调试仍需人工把关", src: "星云科技", reads: "95万", g: "linear-gradient(135deg,#a855f7,#7c3aed)", e: "🤖" }
];

const INTEL = {
  strategies: [
    { name: "延迟出现的跳过键", badge: "开屏", cls: "badge-splash", desc: "跳过按钮延迟 2~5 秒才出现，出现前界面无处可点。", counter: "12s 时间窗 + 持续监听窗口内容变化，按钮一出现在控件树即被代点。", impl: "引擎已实现" },
    { name: "摇一摇传感器跳转", badge: "传感器", cls: "badge-sensor", desc: "读取加速度计，晃动超阈值即跳转；部分灵敏到拿起手机即跳转。", counter: "引擎侧挂起传感器监听（低于行业标准 15m/s²/35°/3s 的激进阈值事件全部丢弃）。", impl: "引擎已实现" },
    { name: "全屏大热区 / 假关闭 ×", badge: "开屏", cls: "badge-splash", desc: "整个广告区域皆可点击，轻微触碰即被记为「有效点击」；× 点了不关闭反而跳转。", counter: "面积安全阀：只代点 <35% 屏面积且带「跳过」字样的控件，全屏热区与无字 × 一律忽略。", impl: "引擎已实现" },
    { name: "Deeplink 连环二跳", badge: "跳转", cls: "badge-inter", desc: "开屏点击后经 Scheme 层层跳转（广告页 → 商城 → 应用商店）。", counter: "广告窗口内发起的 Scheme / Universal Link 一律拦截，跳转链在系统层断开。", impl: "引擎已实现" },
    { name: "非官方渠道侧载应用", badge: "侧载", cls: "badge-native", desc: "第三方市场 / 官网 APK 安装的应用，没有商店审核兜底，广告形态更野。", counter: "无预置规则 → 通用规则兜底 + 学习沉淀：千款语料侧载覆盖 234/257（91.1%），与官方渠道一视同仁。", impl: "本轮实测" },
    { name: "WebView / 自绘开屏", badge: "边界", cls: "badge-native", desc: "H5 开屏未暴露无障碍内容、纯 Canvas 自绘无控件——代点在合规边界内无解（语料中 85 款）。", counter: "自动上报反馈通道 + 建议配合系统权限治理（关联启动 / 传感器权限）。诚实呈现，不虚报。", impl: "诚实边界" }
  ]
};

/* ══════════ 全局状态 ══════════ */
let demo = store.get("demo", null) || {
  shieldOn: true, creativeIdx: 0,
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
    ? "官方渠道与侧载应用一视同仁 · 事件驱动零轮询"
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
$("#toggle-shield").addEventListener("change", (e) => {
  demo.shieldOn = e.target.checked;
  $("#shield-state").textContent = e.target.checked ? "已开启" : "已暂停";
  saveDemo();
});

function clearTimers() { simTimers.forEach(t => clearTimeout(t)); simTimers = []; }
function after(ms, fn) { simTimers.push(setTimeout(fn, ms)); }

function engineEnabled() {
  const st = getRealState();
  return demo.shieldOn && st.learning !== false;
}

/* 自动出手延迟：置信度越高出手越快 */
function autoLatencyMs(p) { return Math.max(350, 2400 / (1 + (p.manual + p.auto))); }

function launchSim() {
  clearTimers();
  $("#phone-body").innerHTML = `<div class="app-launch"><div class="app-logo">✨</div><div style="font-size:11px;color:#a1a1aa;letter-spacing:2px">星云资讯</div></div>`;
  after(900, () => showSplash());
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
      <div style="font-size:38px">${ad.emoji}</div>
      <div class="ad-title">${ad.name}</div>
      <div class="ad-sub">${ad.sub}</div>
      <span class="ad-tag">广告</span>
    </div>
    <div class="ad-brandbar"><div class="mini-logo">✨</div><span>星云资讯 · 看见更大的宇宙</span></div>
    <button class="skip-btn" id="skip-btn" style="display:none">跳过 ${s.cd}s</button>
    ${s.hot ? '<button class="fake-close" id="fake-close">×</button>' : ""}
  </div>`;

  const skipBtn = $("#skip-btn");
  const adBody = $("#ad-body");

  const finish = (how, latency) => {
    clearTimers();
    if (how === "manual") p.manual++;
    else if (how === "auto") p.auto++;
    if (latency != null) { p.lat.push(Math.round(latency * 10) / 10); if (p.lat.length > 12) p.lat.shift(); }
    saveDemo();
    if (how === "manual") dlog(`🧠 已学习「${s.name}」跳过控件 → 下次优先秒跳`);
    if (how === "auto") dlog(`⚡ 引擎代点「${s.name}」跳过（${(latency || 0).toFixed(1)}s，含等待跳过键出现）`);
    if (how === "timeout") dlog(`⏱ 「${s.name}」完整播放（未被秒跳）`);
    renderDrillStats();
    showFeed();
  };

  skipBtn.addEventListener("click", () => finish("manual", (Date.now() - startedAt) / 1000));

  /* 广告主体 / 假关闭：引擎侧拦截（对照模式则跳落地页） */
  const toLanding = (isFake) => {
    demo.adStats.clicks++;
    saveDemo();
    if (engineEnabled()) {
      dlog(`🛡 已拦截「${s.name}」诱导跳转（${isFake ? "假关闭 ×" : "全屏热区"}）→ 摇一摇/热区事件不透传`);
      engineToast(`🛡 诱导跳转已拦截（${isFake ? "无字样 × 不代点" : "全屏热区不代点"}）`, 2200);
      renderDrillStats();
      return;
    }
    if (isFake) dlog("⚠️ 对照模式：误触假关闭 × → 跳转落地页");
    else dlog("⚠️ 对照模式：点击广告主体 → 被计为「有效点击」");
    showLanding(ad, () => showFeed());
  };
  adBody.addEventListener("click", () => toLanding(false));
  if (s.hot) {
    const fc = $("#fake-close");
    fc.addEventListener("click", (e) => { e.stopPropagation(); toLanding(true); });
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

  /* 引擎：学习置信后自动出手（对照模式观察） */
  if (engineEnabled()) {
    if (p.manual + p.auto >= 1) {
      const delay = s.skipDelay * 1000 + autoLatencyMs(p);
      after(delay, () => {
        if (!document.body.contains(skipBtn)) return;
        engineToast(`⚡ 引擎已代点「跳过」 · ${(delay / 1000).toFixed(1)}s`);
        finish("auto", delay / 1000);
      });
    } else {
      engineToast("👁 冷启动观察中：手动点一次跳过即学习", 2200);
    }
  } else {
    engineToast("⏸ 引擎已暂停（对照模式）", 1600);
  }
}

/* 信息流（点文章不弹插屏，保持精简） */
function feedHTML() {
  return `<div class="feed-tip">💡 对照模式体验真实广告；引擎开启时同形态将被秒跳</div>` +
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
function showLanding(ad, onClose) {
  clearTimers();
  $("#phone-body").innerHTML = `
    <div class="landing">
      <div class="landing-bar"><span>🔗 ${ad.brand.toLowerCase()}.ad-landing.example</span><button class="landing-close" id="ld-close">✕</button></div>
      <div style="flex:1;overflow-y:auto;padding:16px;text-align:center">
        <div class="ad-body ${ad.cls}" style="height:130px;border-radius:14px;position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center">
          <div style="font-size:30px">${ad.emoji}</div><div style="font-weight:800">${ad.name}</div>
        </div>
        <div style="font-weight:800;margin-top:12px">${ad.brand}</div>
        <div style="font-size:11px;color:#71717a">${ad.sub}</div>
        <button class="btn btn-primary" style="margin-top:12px" id="ld-cta">${ad.cta}</button>
      </div>
    </div>`;
  $("#ld-close").addEventListener("click", onClose);
  $("#ld-cta").addEventListener("click", () => engineToast("演示环境：此处将跳转广告主页", 1500));
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
  $("#intel-rate").textContent = TEST.rounds[2].rate + "%";
  $("#intel-total").textContent = TEST.total + " 款";
  $("#intel-reaction").textContent = TEST.rounds[2].reaction + "ms";
  $("#intel-energy").textContent = TEST.rounds[2].energy + "mWh/天";
  $("#intel-sideload").textContent = TEST.sideload.hit + "/" + TEST.sideload.total + "（" + TEST.sideload.rate + "%）";

  $("#intel-rounds").innerHTML = TEST.rounds.map((r, i) => `
    <div class="ir-row">
      <span class="ir-no ${i === 2 ? "win" : ""}">${r.no}</span>
      <span class="ir-name">${r.name}</span>
      <span class="ir-rate ${i === 2 ? "win" : ""}">${r.rate}%</span>
    </div>`).join("");

  $("#intel-boundary").innerHTML =
    `三轮自查自改：${TEST.rounds[0].rate}% → ${TEST.rounds[1].rate}% → ${TEST.rounds[2].rate}%（≥90% ✅）；能耗治理 -${TEST.energyDrop}%。` +
    `诚实边界：${TEST.boundary} 款 WebView/自绘开屏在代点合规边界内无解，自动上报反馈通道。`;

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
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "3.0.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "30000"; }

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
  $("#toggle-shield").checked = demo.shieldOn;
  $("#shield-state").textContent = demo.shieldOn ? "已开启" : "已暂停";
})();
