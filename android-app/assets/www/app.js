/* 开屏卫士 v4.1.0 · GKD 订阅融合版 */
"use strict";

/* ══════════ 环境 ══════════ */
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

const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "5.6.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "50600"; }

/* ══════════ 吉祥物（v4.1.0 全新形象：白机器人小侠 + 绿披风跳跃） ══════════
 * 与启动器图标同源；state: on（跳跃浮动）/ off（灰度待命） */
function mascotImg(size, state, head) {
  const on = state !== "off";
  return `<img src="${head ? "mascot-head.png" : "mascot.png"}" width="${size}" height="${size}" alt="吉祥物" class="${on ? "on" : "off"}" draggable="false">`;
}

$("#brand-logo").innerHTML = mascotImg(30, "on", true);

/* ══════════ 版本记录（v5.5.0：仅展示当前安装版本） ══════════
 * 版本号动态读 appVer()（构建时注入，非硬编码）；历史版本全部归档在
 * GitHub Releases，本页不再堆叠多版本记录——只保留当前版本的
 * 版本号 / 核心要点 / 发布时间三项信息。 */
const CURRENT_RELEASE = {
  date: "2026-11-18",
  highlights: [
    "命中激进化：SDK 指纹 +8 档（UnionSplash/插屏/Advertising 等）、倒计时提前 0.2s 开火、同窗预算 6→8、点击冷却 500ms、内置规则库 102→114 款 + 全局 vid 兜底两档——好多广告没命中的根子一刀切断",
    "订阅市场：旧默认订阅已停止维护全面下架，换 gkd-subscription 话题活跃维护源一键导入（含本仓库官方订阅·subscription-template 构建）；topics 列表与自建订阅指引页内直达",
    "10 秒警告终局：前台 3s 看门狗自动深挖拉活（退后台再进零操作）；新增可选「零打扰持久模式」——一次 ADB 授权 WRITE_SECURE_SETTINGS，登记被吊销自动写回重绑（零系统设置 · 零 10 秒警告）",
    "5 品牌 × 10 机型矩阵自测 50 机型六维全绿（开屏命中 / 屏蔽速度 / bug 防线 / 功耗 / 精准度 / 算法优化），总断言 1036 项零回归",
  ],
};

/* ══════════ v5.5.0 模块四：权限使用审计（与 ComplianceGuard.privacyAudit 同源） ══════════
 * 7 项权限逐条展示用途与触发时机；零权限声明（定位/通讯录/存储/相机/
 * 麦克风/剪贴板全部未申请）置顶强调——用户不需要懂技术也能核对。 */
const PRIVACY_AUDIT = [
  { name: "网络（INTERNET）", use: "检查更新 / GKD 订阅拉取 / 日志上报（仅手动触发上传）" },
  { name: "安装未知应用", use: "页内直下 APK 后的安装确认（仅你点「安装」时系统弹窗）" },
  { name: "通知", use: "常驻守护通知 / 恢复提醒 / 上传结果（可在系统设置关闭）" },
  { name: "前台服务（专用类型）", use: "无障碍跳过服务本体（Android 14+ 类型声明）" },
  { name: "开机自启", use: "开机自检提醒（不自动拉起未授权服务）" },
  { name: "电池白名单引导", use: "防省电策略杀服务（你可拒绝，拒绝后仅提醒降频）" },
  { name: "无障碍服务", use: "核心跳过能力（系统设置内授权，App 绝不代开）" },
];
function renderPrivacyAudit() {
  const box = $("#privacy-audit");
  if (!box) return;
  box.innerHTML = "";
  const head = document.createElement("div");
  head.style.cssText = "font-size:11px;line-height:1.6;color:var(--muted,#8a94a0);padding:2px 0 6px";
  head.textContent = "共 7 项，全部最小化。定位 / 通讯录 / 短信读取 / 存储 / 相机 / 麦克风 / 剪贴板——全部未申请、永不需要。学习指纹仅存本机。";
  box.appendChild(head);
  PRIVACY_AUDIT.forEach((p) => {
    const row = document.createElement("div");
    row.className = "ka-row";
    const info = document.createElement("div");
    info.className = "ka-info";
    const nm = document.createElement("div");
    nm.className = "ka-name";
    nm.textContent = p.name;
    const ds = document.createElement("div");
    ds.className = "ka-desc";
    ds.textContent = p.use;
    info.appendChild(nm);
    info.appendChild(ds);
    const dot = document.createElement("span");
    dot.className = "ka-dot ok";
    row.appendChild(dot);
    row.appendChild(info);
    box.appendChild(row);
  });
}
renderPrivacyAudit();

/* ══════════ 路由 ══════════ */
$$(".tabbtn").forEach(b => b.addEventListener("click", () => {
  $$(".tabbtn").forEach(x => x.classList.remove("active"));
  b.classList.add("active");
  $$(".page").forEach(p => p.classList.remove("active"));
  $("#page-" + b.dataset.page).classList.add("active");
  const pg = b.dataset.page;
  if (pg === "home") { refreshHome(); renderWhitelist(true); }
  if (pg === "sub") refreshSub();
  if (pg === "me") { renderLog(); }
}));

/* ══════════ 首页：守护状态 ══════════ */
function getRealState() { return store.get("state", null) || {}; }

/* ═══ v4.5.0 开关专用通道（状态竞态根治）═══
 * 「开启弹窗广告屏蔽之后又被 App 自动关闭」根因：UI 直接写 state 只落
 * prefs，服务内存旧副本落盘时整体覆盖回去 → 开关自动弹回。
 * 开关一律走 AndroidApp.setStateBool（改服务内存本体，唯一数据源）；
 * 浏览器演示环境（无桥）回退旧写入。 */
function bindToggle(id, key, onMsg, offMsg) {
  const el = $("#" + id);
  el.addEventListener("change", () => {
    if (HAS_BRIDGE && AndroidApp.setStateBool) {
      try { AndroidApp.setStateBool(key, el.checked); } catch (e) {}
    } else {
      const st = getRealState();
      st[key] = el.checked;
      setRealState(st);
    }
    toast(el.checked ? onMsg : offMsg);
  });
}

function setRealState(st) { store.set("state", st); }

function refreshHome() {
  const st = getRealState();
  const s = st.stats || {};
  $("#st-auto").textContent = s.auto || 0;
  $("#st-popup").textContent = s.popup || 0;
  $("#st-manual").textContent = s.manual || 0;

  let popupOn = st.popupShield === true;
  $("#toggle-popup").checked = popupOn;
  $("#toggle-learning").checked = st.learning !== false;

  let running = false;
  let enabledInSettings = false;
  let masterOn = true;
  if (HAS_BRIDGE) {
    try { running = !!AndroidApp.isServiceRunning(); } catch (e) {}
    try { enabledInSettings = !!AndroidApp.a11yEnabledInSettings(); } catch (e) {}
    try { masterOn = AndroidApp.isMasterOn() !== false; } catch (e) {}
  }
  renderHero(running, enabledInSettings, masterOn);
  refreshBattery(running);
  try { refreshSecureHeal(); } catch (e) {}
}

function renderHero(running, enabledInSettings, masterOn) {
  const card = $("#hero-card");
  const mascot = $("#hero-mascot");
  const title = $("#hero-title");
  const sub = $("#hero-sub");
  const btn = $("#btn-enable");
  const revive = $("#btn-revive");
  const resume = $("#btn-resume");
  const pause = $("#btn-pause");
  const tip = $("#hero-tip");
  btn.style.display = "none";
  revive.style.display = "none";
  resume.style.display = "none";
  pause.style.display = "none";
  if (running && !masterOn) {
    card.classList.remove("on");
    card.classList.add("paused");
    mascot.innerHTML = mascotImg(88, "off");
    title.textContent = "已暂停";
    sub.textContent = "跳过与学习均不生效";
    resume.style.display = "";
    tip.textContent = "通知栏也可一键恢复；系统开关一直开着，无需重投权限";
  } else if (running) {
    card.classList.add("on");
    card.classList.remove("paused");
    mascot.innerHTML = mascotImg(88, "on");
    title.textContent = "守护中";
    sub.textContent = "广告露头就秒跳";
    pause.style.display = "";
    tip.textContent = "关闭防护请用此开关/通知栏按钮（别去系统设置关，重开要看 10 秒警告）";
  } else if (enabledInSettings) {
    card.classList.remove("on", "paused");
    mascot.innerHTML = mascotImg(88, "off");
    title.textContent = "服务待恢复";
    sub.textContent = "系统仍登记，正在/可一键重拉";
    revive.style.display = "";
    tip.textContent = "点「一键恢复」重新拉起（不碰系统设置，无需 10 秒等待）";
  } else {
    card.classList.remove("on", "paused");
    mascot.innerHTML = mascotImg(88, "off");
    title.textContent = "未开启";
    sub.textContent = "无障碍跳过服务";
    btn.style.display = "";
    tip.textContent = "开启时会弹 10 秒安全确认（系统防诈设计），等待后确认即可";
  }
}

/* v4.1.0 App 内主开关：暂停/恢复均不碰系统设置（免 10 秒警告）
 * v4.4.0：恢复后核实服务真活着（防「暂停期间服务被杀」→ 假恢复） */
$("#btn-pause").addEventListener("click", () => {
  if (!HAS_BRIDGE) return;
  try { AndroidApp.setMasterOn(false); toast("已暂停 · 恢复时一键即回"); } catch (e) {}
  setTimeout(refreshHome, 150);
});

$("#btn-resume").addEventListener("click", () => {
  if (!HAS_BRIDGE) return;
  try { AndroidApp.setMasterOn(true); } catch (e) {}
  setTimeout(() => {
    // v4.4.0：恢复后核实服务真在运行（暂停期间被系统杀掉 = 假恢复）
    let alive = true;
    try { alive = !!AndroidApp.isServiceRunning(); } catch (e) {}
    if (!alive) { toast("服务已掉线，正在深度拉活…"); doRevive(); return; }
    toast("防护已恢复");
    refreshHome();
  }, 200);
});

/* v5.2.0 深度拉活（后台 15s 自适应 + 每轮复查登记；成功后暂停自动解除）
 * 绝不自动跳系统设置——10 秒强制警告只剩「开关真被吊销」时才需要
 * （issue#56：Redmi 25060RK16C 冷启动重绑慢，旧版 6s 窗口误判） */
let reviving = false;
function doRevive() {
  if (!HAS_BRIDGE || reviving) return;
  reviving = true;
  toast("正在恢复…（最长 15 秒，不用去系统设置）");
  try {
    AndroidApp.tryAutoReviveDeep("__reviveCb");
  } catch (e) {
    // 旧版同步兑底（理论上不会走到）
    try {
      const r = JSON.parse(AndroidApp.tryAutoRevive());
      if (window.__reviveCb) window.__reviveCb(null, r);
      return;
    } catch (e2) {}
    reviving = false;
    toast("恢复失败，请重试");
  }
}
window.__reviveCb = (name, r) => {
  reviving = false;
  try {
    if (r.ok) {
      const ms = r.reviveMs ? ("（耗时 " + Math.round(r.reviveMs / 100) / 10 + "s）") : "";
      toast((r.resumed ? "已恢复守护（暂停已自动解除）" : "已恢复守护") + ms);
    } else if (r.reason === "need_toggle") {
      // 系统绑定较慢：引导重试，绝不自动跳系统设置看 10 秒警告
      toast("系统绑定较慢：再点一次「一键恢复」通常即可");
    } else if (r.reason === "disabled") {
      toast("服务开关被系统关闭（清理后台/升级所致）：点「开启服务」直达，10 秒安全确认属正常");
    } else {
      toast("恢复失败，请重试");
    }
  } catch (e) {}
  setTimeout(refreshHome, 400);
};

/* ═══ v5.2.0 通知入口自动恢复（issue#56 核心修复）═══
 * 待恢复通知点按 → MainActivity jump_restore/jump_a11y → 本函数：
 *  · 登记仍在（多数场景）→ 自动深挖拉活，全程不去系统设置；
 *  · 登记被吊销 → 直达服务开关页（预期管理：明示 10 秒安全确认属正常）。
 * 旧版 jump_a11y 无条件 500ms 直跳设置，把登记仍在的用户也白送去
 * 看 10 秒警告——已在入口上消灭。 */
window.__autoRestore = function (fromNotification) {
  try { AndroidApp.autoRestoreHandled(); } catch (e) {}
  let running = false, enabled = false;
  try { running = !!AndroidApp.isServiceRunning(); } catch (e) {}
  try { enabled = !!AndroidApp.a11yEnabledInSettings(); } catch (e) {}
  refreshHome();
  if (running) { toast("防护守护中"); return; }
  if (enabled) { doRevive(); return; }
  // 登记被系统吊销：去设置是唯一出路（预期管理 + 直达开关详情页）
  toast("服务开关被系统关闭：即将直达开关页，10 秒安全确认后开启即可");
  setTimeout(() => {
    try {
      // v5.5.0 竞态兜底：预判与跳转之间登记若恢复（AMS 迟到重绑），
      // Java 守卫会返回 still_registered——回落应用内自愈而非无反馈
      const r = JSON.parse(AndroidApp.openA11yDetail() || "{}");
      if (!r.ok && r.reason === "still_registered") doRevive();
    } catch (e) {}
  }, 700);
};

/* v5.5.0 红线补齐：「开启服务」先状态校验再动——
 * ① 服务在跑 → 提示守护中（按钮本不该出现，防御性）；
 * ② 登记仍在 → 应用内自愈 doRevive（绝不送用户去系统设置看 10 秒警告）；
 * ③ 登记真被吊销 → openA11yDetail 直达开关页 + 预期管理文案；
 * ④ Java 守卫若仍拦（still_registered，预判后登记恢复的竞态）→ 回落自愈。
 * 任何路径都不会「点了没反应」。 */
$("#btn-enable").addEventListener("click", () => {
  if (HAS_BRIDGE) {
    let running = false, enabled = false;
    try { running = !!AndroidApp.isServiceRunning(); } catch (e) {}
    try { enabled = !!AndroidApp.a11yEnabledInSettings(); } catch (e) {}
    if (running) { toast("防护守护中"); return; }
    if (enabled) { toast("系统仍登记：应用内自愈中（不用去系统设置）"); doRevive(); return; }
    try {
      const r = JSON.parse(AndroidApp.openA11yDetail() || "{}");
      if (r.ok) { toast("即将直达服务开关页：10 秒安全确认属正常，等待后开启即可"); return; }
      if (r.reason === "still_registered") { doRevive(); return; }
      toast("无法打开系统设置，请重试");
      return;
    } catch (e) {}
  }
  toast("请在系统设置中开启无障碍服务");
});

$("#btn-revive").addEventListener("click", () => {
  doRevive();
});

/* ═══ v5.6.0 自动拉活看门狗（issue 终局：退后台再进绝不该看到「未开启」）═══
 * 旧版：服务死了但登记仍在时，页面只显示「服务待恢复」等用户点按钮——
 * 部分用户以为要重新开启（甚至去系统设置）。现在前台期间每 3s 自查：
 * 登记仍在 + 服务未跑 + 未在恢复中 → 自动静默深挖拉活（15s 自适应），
 * 恢复成功 UI 自动切「守护中」。全程无需用户任何操作。 */
let lastAutoReviveKick = 0;
setInterval(() => {
  if (!HAS_BRIDGE || reviving) return;
  try {
    const running = !!AndroidApp.isServiceRunning();
    if (running) return;
    const enabled = !!AndroidApp.a11yEnabledInSettings();
    if (!enabled) return; // 真吊销：交给持久模式自愈或开启引导
    const now = Date.now();
    if (now - lastAutoReviveKick < 3000) return;
    lastAutoReviveKick = now;
    doRevive(); // 静默深挖（toast 只在完成/失败时出现）
  } catch (e) {}
}, 3000);

/* ═══ v5.6.0 持久守护卡片（零打扰持久模式状态展示）═══ */
let shCmd = "";
function refreshSecureHeal() {
  const card = $("#sh-card");
  if (!card || !HAS_BRIDGE) return;
  let st = null;
  try { st = JSON.parse(AndroidApp.secureHealStatus() || "{}"); } catch (e) {}
  if (!st) return;
  card.style.display = "";
  const chip = $("#sh-chip");
  if (st.granted) {
    chip.className = "chip chip-green";
    chip.textContent = "已授权";
    $("#sh-desc").textContent = "持久模式生效：无障碍被吊销将自动写回登记重绑（零系统设置 · 零 10 秒警告）";
    $("#sh-how").style.display = "none";
  } else {
    chip.className = "chip";
    chip.textContent = "未授权";
    $("#sh-desc").textContent = "一次 ADB 授权后：升级/清理后台吊销无障碍时自动写回登记重绑，不再进系统设置、不看 10 秒警告";
    const how = $("#sh-how");
    how.style.display = "";
    shCmd = st.cmd || "";
    how.textContent = "电脑连接手机执行一次：" + shCmd + "（仅需一次，永久生效；商店安装的应用拿不到此权限）";
  }
}
$("#btn-sh-copy").addEventListener("click", () => {
  if (!shCmd) return;
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(shCmd);
      toast("授权命令已复制，电脑连接手机执行一次即可");
      return;
    }
  } catch (e) {}
  try {
    const ta = document.createElement("textarea");
    ta.value = shCmd;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    document.body.removeChild(ta);
    toast("授权命令已复制，电脑连接手机执行一次即可");
  } catch (e) {
    toast("复制失败，请长按卡片手动复制命令");
  }
});

function refreshBattery(running) {
  const banner = $("#battery-banner");
  if (!HAS_BRIDGE || !running) { banner.style.display = "none"; return; }
  let ok = false;
  try { ok = !!AndroidApp.isBatteryWhitelisted(); } catch (e) {}
  banner.style.display = ok ? "none" : "";
}

$("#btn-battery").addEventListener("click", () => {
  if (HAS_BRIDGE) {
    try { AndroidApp.requestBatteryWhitelist(); return; } catch (e) {}
  }
  toast("请在系统设置中操作");
});

/* ══════════ v5.2.0 保活体检（issue#56：防清理后台/覆盖安装吊销无障碍）══════════
 * 把「防吊销」做进 UI：三项自动检测（服务绑定/通知权限/电池白名单）+
 * 品牌自适应厂商项（自启动/省电策略直达——HyperOS 清理后台吊销登记的
 * 两大诱因；近 5 年全品牌）。厂商项无法自动读取（厂商不开放查询 API），
 * 标记「需手动确认」并提供一键直达。 */
let kaItemsCache = null;
function renderKeepAlive() {
  if (!HAS_BRIDGE) return;
  const card = $("#ka-card");
  if (!card) return;
  let running = false, notif = true, batt = false;
  try { running = !!AndroidApp.isServiceRunning(); } catch (e) {}
  try { notif = !!AndroidApp.notifPermGranted(); } catch (e) {}
  try { batt = !!AndroidApp.isBatteryWhitelisted(); } catch (e) {}
  let d = kaItemsCache;
  if (!d) {
    try { d = JSON.parse(AndroidApp.keepAliveItems()); kaItemsCache = d; } catch (e) { d = null; }
  }
  const rows = [];
  rows.push({ label: "服务绑定", ok: running, desc: running ? "防护引擎在线" : "点上方「一键恢复/开启服务」", action: null });
  rows.push({ label: "通知权限", ok: notif, desc: notif ? "恢复提醒可送达" : "断连提醒收不到", action: notif ? null : "notif" });
  rows.push({ label: "电池白名单", ok: batt, desc: batt ? "厂商省电策略不限制" : "防省电策略杀服务", action: batt ? null : "battery" });
  if (d && d.items && d.items.length) {
    d.items.forEach((it) => {
      rows.push({ label: it.label, ok: null, desc: it.desc, action: "oem:" + it.id, manual: true });
    });
  }
  const list = $("#ka-list");
  if (!list) return;
  list.innerHTML = "";
  rows.forEach((r) => {
    const row = document.createElement("div");
    row.className = "ka-row";
    const dot = document.createElement("span");
    dot.className = "ka-dot " + (r.ok === true ? "ok" : r.ok === false ? "warn" : "manual");
    const info = document.createElement("div");
    info.className = "ka-info";
    const name = document.createElement("div");
    name.className = "ka-name";
    name.textContent = r.label + (r.manual ? "（需手动确认）" : "");
    const desc = document.createElement("div");
    desc.className = "ka-desc";
    desc.textContent = r.desc;
    info.appendChild(name);
    info.appendChild(desc);
    row.appendChild(dot);
    row.appendChild(info);
    if (r.action) {
      const btn = document.createElement("button");
      btn.className = "link-btn";
      btn.textContent = "去设置";
      btn.addEventListener("click", () => {
        if (r.action === "notif") { try { AndroidApp.openNotifSettings(); } catch (e) {} }
        else if (r.action === "battery") { try { AndroidApp.requestBatteryWhitelist(); } catch (e) {} }
        else if (r.action && r.action.indexOf("oem:") === 0) {
          try { AndroidApp.openKeepAliveTarget(r.action.slice(4)); } catch (e) {}
        }
      });
      row.appendChild(btn);
    } else {
      row.appendChild(document.createElement("span"));
    }
    list.appendChild(row);
  });
  const pending = rows.filter((r) => r.ok !== true).length;
  const chip = $("#ka-chip");
  if (chip) {
    chip.textContent = pending === 0 ? "全部通过" : pending + " 项待处理";
    chip.className = "chip " + (pending === 0 ? "ka-chip-ok" : "ka-chip-warn");
  }
  const sub = $("#ka-sub");
  if (sub && d && d.familyLabel && d.family !== "generic") {
    sub.textContent = d.familyLabel + "：完成自启动/省电配置，清理后台/升级不掉防护";
  }
  card.style.display = "";
}

bindToggle("toggle-popup", "popupShield", "弹窗屏蔽已开启", "弹窗屏蔽已关闭");
bindToggle("toggle-learning", "learning", "习惯学习已开启", "习惯学习已关闭");

/* ══════════ v4.3.0 应用白名单 ══════════ */
let wlCache = null; // {list:Set, known:[{pkg,label}]}

function loadWhitelist() {
  if (!HAS_BRIDGE) {
    wlCache = { list: new Set(), known: [] };
    return wlCache;
  }
  try {
    const d = JSON.parse(AndroidApp.whitelistJson());
    wlCache = { list: new Set(d.list || []), known: d.known || [] };
  } catch (e) {
    wlCache = { list: new Set(), known: [] };
  }
  return wlCache;
}

function renderWhitelist(force) {
  const box = $("#wl-list");
  if (!box) return;
  if (!wlCache || force) loadWhitelist();
  const wl = wlCache;
  $("#wl-count").textContent = String(wl.list.size);
  // 白名单里的排前面，其余按字母
  const known = (wl.known || []).slice().sort((a, b) => {
    const wa = wl.list.has(a.pkg) ? 0 : 1, wb = wl.list.has(b.pkg) ? 0 : 1;
    if (wa !== wb) return wa - wb;
    return String(a.label).localeCompare(String(b.label), "zh");
  });
  if (!known.length) {
    box.innerHTML = '<div class="empty">暂无记录——卫士处理过的应用会出现在这里</div>';
    return;
  }
  box.innerHTML = known.map(x => {
    const on = wl.list.has(x.pkg);
    return '<div class="wl-row">'
      + '<div class="wl-label"><span class="wl-name">' + esc(x.label) + '</span>'
      + '<span class="wl-pkg">' + esc(x.pkg) + '</span></div>'
      + '<label class="switch"><input type="checkbox" data-wl="' + esc(x.pkg) + '"'
      + (on ? ' checked' : '') + '><span class="slider"></span></label>'
      + '</div>';
  }).join("");
  box.querySelectorAll('input[data-wl]').forEach(t => {
    t.addEventListener("change", () => {
      const pkg = t.dataset.wl;
      let ok = false;
      try {
        ok = t.checked ? AndroidApp.whitelistAdd(pkg) : AndroidApp.whitelistRemove(pkg);
      } catch (e) {}
      toast(t.checked ? "已加入白名单：卫士在该应用内完全让路" : "已移出白名单：恢复守护");
      renderWhitelist(true);
    });
  });
}

$("#btn-wl-add").addEventListener("click", () => {
  const inp = $("#wl-input");
  const pkg = inp.value.trim();
  if (!pkg) { toast("请输入应用包名"); return; }
  if (!HAS_BRIDGE) { toast("仅 APK 内可用"); return; }
  let ok = false;
  try { ok = AndroidApp.whitelistAdd(pkg); } catch (e) {}
  if (ok) { inp.value = ""; toast("已加入白名单：" + pkg); renderWhitelist(true); }
  else toast("添加失败：包名格式不正确或已存在");
});

$("#wl-input").addEventListener("keydown", (e) => {
  if (e.key === "Enter") $("#btn-wl-add").click();
});


/* ══════════ 订阅页 ══════════ */
/* v5.6.0：旧默认源 @gkd-kit/subscription（已归档停更）下架；
 * 快速填入换成 Lin-arm 社区事实标准订阅（jsdelivr CDN 国内可达）；
 * 订阅市场卡片一键导入活跃维护源（subRecommend 注册表）。 */
const SUB_EXAMPLE = "https://cdn.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5";
let subCbSeq = 0;

function subCall(method, ...args) {
  return new Promise(resolve => {
    if (!HAS_BRIDGE) { resolve({ ok: false, error: "仅 APK 内可用" }); return; }
    const cbName = "__subCb" + (++subCbSeq);
    window[cbName] = (name, result) => { try { delete window[name]; } catch (e) {} resolve(result); };
    window.__subCb = (name, result) => {
      const fn = window[name];
      if (fn) fn(name, result);
    };
    try {
      if (method === "subSetUrl") AndroidApp.subSetUrl(args[0], cbName);
      else if (method === "subUpdateNow") AndroidApp.subUpdateNow(cbName);
    } catch (e) {
      resolve({ ok: false, error: "调用失败" });
    }
  });
}

function refreshSub() {
  if (!HAS_BRIDGE) {
    $("#sub-builtin-apps").textContent = "21 款";
    $("#sub-builtin-rules").textContent = "21 条";
    return;
  }
  renderSubMarket();
  let sub = null;
  try { sub = JSON.parse(AndroidApp.subState()); } catch (e) {}
  if (!sub) return;
  $("#sub-builtin-apps").textContent = (sub.builtinApps || 0) + " 款";
  $("#sub-builtin-rules").textContent = (sub.builtinRules || 0) + " 条";
  $("#sub-toggle").checked = sub.remoteEnabled !== false;
  if (sub.url) $("#sub-url").value = sub.url;
  const box = $("#sub-status");
  if (sub.remoteOk) {
    box.style.display = "";
    $("#sub-name").textContent = sub.remoteName || "--";
    $("#sub-count").textContent = (sub.remoteApps || 0) + " 款应用 · " + (sub.remoteRules || 0) + " 条规则";
    $("#sub-updated").textContent = sub.updatedAt ? fmtTime(sub.updatedAt) : "--";
    $("#sub-hits").textContent = (sub.remoteHits || 0) + " 次";
  } else {
    box.style.display = "none";
  }
  const err = $("#sub-result");
  if (sub.error) { err.className = "fb-result err"; err.textContent = sub.error; }
  else if (!sub.remoteOk) { err.className = "fb-result"; err.textContent = ""; }
}

function fmtTime(ms) {
  try { return new Date(ms).toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false }); }
  catch (e) { return "--"; }
}

$("#btn-sub-import").addEventListener("click", async () => {
  const url = $("#sub-url").value.trim();
  const box = $("#sub-result");
  if (!url) { box.className = "fb-result err"; box.textContent = "请先粘贴订阅链接"; return; }
  box.className = "fb-result"; box.textContent = "正在拉取订阅（失败自动换镜像）…";
  const r = await subCall("subSetUrl", url);
  if (r.ok) {
    box.className = "fb-result ok";
    box.textContent = "已导入「" + (r.name || "订阅") + "」：" + (r.apps || 0) + " 款应用 · " + (r.rules || 0) + " 条规则";
  } else {
    box.className = "fb-result err"; box.textContent = r.error || "导入失败";
  }
  refreshSub();
});

$("#btn-sub-example").addEventListener("click", () => {
  $("#sub-url").value = SUB_EXAMPLE;
  toast("已填入 Lin-arm 社区订阅（日更维护）");
});

/* ═══ v5.6.0 订阅市场（活跃维护源一键导入）═══ */
let subMarketData = [];
function renderSubMarket() {
  const box = $("#sub-market");
  if (!box) return;
  let list = subMarketData;
  if (HAS_BRIDGE && !list.length) {
    try { list = subMarketData = JSON.parse(AndroidApp.subRecommend() || "[]"); } catch (e) {}
  }
  if (!list || !list.length) {
    box.innerHTML = '<div class="sub-note">订阅市场不可用（请更新到最新版）</div>';
    return;
  }
  box.innerHTML = list.map(s =>
    '<div class="market-item">'
    + '<div class="market-info">'
    + '<div class="market-name">' + esc(s.name) + ' <span class="market-badge">' + esc(s.badge) + '</span></div>'
    + '<div class="market-desc">' + esc(s.desc) + ' · by ' + esc(s.owner) + '</div>'
    + '</div>'
    + '<div class="market-acts">'
    + '<button class="btn btn-primary btn-sm" data-sub-import="' + s.idx + '">导入</button>'
    + '<button class="btn btn-ghost btn-sm" data-sub-repo="' + s.idx + '">仓库</button>'
    + '</div></div>').join("");
  box.querySelectorAll("[data-sub-import]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const s = list[+btn.dataset.subImport];
      if (!s) return;
      const box2 = $("#sub-result");
      box2.className = "fb-result"; box2.textContent = "正在拉取「" + s.name + "」（失败自动换镜像）…";
      $("#sub-url").value = s.url;
      const r = await subCall("subSetUrl", s.url);
      if (r.ok) {
        box2.className = "fb-result ok";
        box2.textContent = "已导入「" + (r.name || s.name) + "」：" + (r.apps || 0) + " 款应用 · " + (r.rules || 0) + " 条规则";
      } else {
        box2.className = "fb-result err"; box2.textContent = r.error || "导入失败";
      }
      refreshSub();
    });
  });
  box.querySelectorAll("[data-sub-repo]").forEach(btn => {
    btn.addEventListener("click", () => {
      const s = list[+btn.dataset.subRepo];
      if (!s || !HAS_BRIDGE) return;
      try { AndroidApp.openUrl(s.repo); } catch (e) {}
    });
  });
}

/* v5.6.0 市场底部链接：话题列表 + subscription-template 构建指引 */
(function bindSubLinks() {
  const t = $("#link-sub-topics");
  if (t) t.addEventListener("click", (ev) => {
    ev.preventDefault();
    if (HAS_BRIDGE) { try { AndroidApp.openUrl("https://github.com/topics/gkd-subscription"); } catch (e) {} }
    else window.open("https://github.com/topics/gkd-subscription", "_blank");
  });
  const m = $("#link-sub-template");
  if (m) m.addEventListener("click", (ev) => {
    ev.preventDefault();
    if (HAS_BRIDGE) { try { AndroidApp.openUrl("https://github.com/gkd-kit/subscription-template"); } catch (e) {} }
    else window.open("https://github.com/gkd-kit/subscription-template", "_blank");
  });
})();

$("#btn-sub-clear").addEventListener("click", async () => {
  $("#sub-url").value = "";
  const r = await subCall("subSetUrl", "");
  const box = $("#sub-result");
  box.className = "fb-result ok";
  box.textContent = r.ok ? "已移除远程订阅" : (r.error || "操作失败");
  refreshSub();
});

$("#btn-sub-update").addEventListener("click", async () => {
  const box = $("#sub-result");
  box.className = "fb-result"; box.textContent = "正在检查更新…";
  const r = await subCall("subUpdateNow");
  if (r.ok) {
    box.className = "fb-result ok";
    box.textContent = "已更新：" + (r.rules || 0) + " 条规则";
  } else {
    box.className = "fb-result err"; box.textContent = r.error || "更新失败";
  }
  refreshSub();
});

$("#sub-toggle").addEventListener("change", () => {
  const on = $("#sub-toggle").checked;
  if (HAS_BRIDGE) { try { AndroidApp.subSetEnabled(on); } catch (e) {} }
  toast(on ? "远程订阅已启用" : "远程订阅已停用");
});

/* ══════════ 我的：版本与更新 ══════════ */
$("#me-ver").textContent = "v" + appVer();
$("#ver-chip").textContent = "v" + appVer();

/* v5.5.0：版本记录仅展示当前安装版本（版本号动态读取，非硬编码） */
$("#changelog").innerHTML =
  '<div class="log-item"><div class="log-v">v' + esc(appVer())
  + ' · ' + esc(CURRENT_RELEASE.date) + '</div>'
  + CURRENT_RELEASE.highlights.map(h => '<div class="log-d">' + esc(h) + '</div>').join("")
  + '</div>';

const GH_API = "https://api.github.com/repos/xiaobaiba999/Skip-Ad";
let ubTimer = null;
let latestRelease = null;
let dl = { channels: [], idx: 0, fileName: "", prefix: "ub", lastBytes: -1, lastMoveAt: 0 };

function cmpVer(a, b) {
  const pa = String(a).replace(/^v/, "").split("."), pb = String(b).replace(/^v/, "").split(".");
  for (let i = 0; i < 3; i++) {
    const x = +pa[i] || 0, y = +pb[i] || 0;
    if (x !== y) return x - y;
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
        ok: true, hasRelease: true, tag: j.tag_name, url: j.html_url,
        apkUrl: apk ? apk.browser_download_url : "",
        apkMirrors: apk ? ["https://ghfast.top/" + apk.browser_download_url, "https://gh-proxy.com/" + apk.browser_download_url] : [],
        apkFileName: apk ? apk.name : "adshield.apk",
        apkMB: apk ? Math.round(apk.size / 1048576 * 100) / 100 : 0,
      };
    }
    return { ok: false, error: "GitHub " + r.status };
  } catch (e) { return { ok: false, error: "网络异常" }; }
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

function switchChannel(reason) {
  if (dl.idx + 1 < dl.channels.length) {
    dl.idx++;
    toast(reason + "，已切换备用通道");
    beginChannel();
    return true;
  }
  return false;
}

function startPageDownload(d, prefix) {
  if (!d.apkUrl) { toast("该版本没有 APK 附件"); return; }
  if (!HAS_BRIDGE) { window.open(d.apkUrl, "_blank"); return; }
  dl = { channels: [d.apkUrl].concat(d.apkMirrors || []), idx: 0, fileName: d.apkFileName || "adshield.apk", prefix, lastBytes: -1, lastMoveAt: Date.now() };
  beginChannel();
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
        if (d.bytes !== dl.lastBytes) { dl.lastBytes = d.bytes; dl.lastMoveAt = Date.now(); }
        else if (Date.now() - dl.lastMoveAt > 30000) {
          if (switchChannel("下载长时间无进展")) return;
          dl.lastMoveAt = Date.now();
        }
      } else if (d.state === "done") {
        clearInterval(ubTimer); ubTimer = null;
        bar.style.width = "100%";
        txt.textContent = "下载完成，点击安装即覆盖升级";
        const btn = $("#" + prefix + "-btn");
        if (btn) { btn.textContent = "立即安装"; btn.disabled = false; btn.dataset.phase = "install"; }
      } else if (d.state === "failed") {
        clearInterval(ubTimer); ubTimer = null;
        if (switchChannel("下载失败")) return;
        txt.textContent = "下载失败，可稍后重试";
        const btn = $("#" + prefix + "-btn");
        if (btn) { btn.textContent = "重新下载"; btn.disabled = false; btn.dataset.phase = "download"; }
      }
    } catch (e) { clearInterval(ubTimer); ubTimer = null; }
  }, 800);
}

$("#ub-toggle").addEventListener("click", () => {
  const det = $("#ub-detail");
  det.style.display = det.style.display === "none" ? "" : "none";
});

$("#ub-action").addEventListener("click", () => {
  const act = $("#ub-action");
  if (act.dataset.phase === "install") {
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

$("#btn-check-update").addEventListener("click", async () => {
  const btn = $("#btn-check-update");
  const dlBtn = $("#page-dl-btn");
  const note = $("#page-dl-note");
  btn.disabled = true;
  dlBtn.style.display = "none";
  note.style.display = "none";
  btn.textContent = "检查中…";
  const d = await fetchLatest();
  btn.disabled = false;
  btn.textContent = "检查更新";
  if (!d.ok) { note.className = "fb-result err"; note.style.display = ""; note.textContent = "查询失败：" + (d.error || "未知"); return; }
  if (!d.hasRelease) { note.className = "fb-result"; note.style.display = ""; note.textContent = "尚未发布版本"; return; }
  latestRelease = d;
  const latest = String(d.tag || "").replace(/^v/, "");
  const isNewer = cmpVer(latest, appVer()) > 0;
  if (d.apkUrl) {
    dlBtn.textContent = isNewer ? "下载 v" + latest : "重新下载 APK";
    dlBtn.dataset.phase = "download";
    dlBtn.disabled = false;
    dlBtn.style.display = "";
    note.className = "fb-result";
    note.style.display = "";
    note.textContent = isNewer ? "发现新版本" : "已是最新版本";
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

/* ══════════ 引擎画像（日志上传正文 · v4.2.0 更具体） ══════════ */
function fmtTs(t) {
  try {
    const d = new Date(t);
    const p = (n) => String(n).padStart(2, "0");
    return p(d.getHours()) + ":" + p(d.getMinutes()) + ":" + p(d.getSeconds());
  } catch (e) { return "--:--:--"; }
}

/* ═══ v4.5.0 包名缩写（修「android」歧义）═══
 * 末段是通用词（android/app/main/ui/...）时取两段——否则 me.ele.android
 * （饿了么）等应用在日志/战绩里全部显示为「android」，误导排查方向。 */
const GENERIC_TAILS = new Set(["android", "app", "main", "ui", "mobile", "phone", "client", "core", "sdk", "online", "lite"]);
function shortPkgName(pkg) {
  if (!pkg || !String(pkg).includes(".")) return String(pkg || "");
  const parts = String(pkg).split(".");
  const last = parts[parts.length - 1];
  if (GENERIC_TAILS.has(last) && parts.length >= 3) return parts[parts.length - 2] + "." + last;
  return last;
}

function engineProfile() {
  const st = getRealState();
  const s = st.stats || {};
  let rules = 0, pkgs = 0;
  try { Object.keys(st.rules || {}).forEach(k => { const n = (st.rules[k] || []).length; rules += n; if (n) pkgs++; }); } catch (e) {}
  const lines = [];
  lines.push("- 版本：v" + appVer() + " · 环境：" + (HAS_BRIDGE ? "Android APK" : "浏览器预览") + " · 手动上传");
  lines.push("- 真实引擎：秒跳 " + (s.auto || 0) + " · 弹窗关闭 " + (s.popup || 0) + " · 返回键 " + (s.back || 0)
    + " · 反馈面板收起 " + (s.feedback || 0) + " · 手动样本 " + (s.manual || 0) + " · 学习规则 " + rules + " 条（" + pkgs + " 个应用）");
  // Top5 应用战绩（秒跳/弹窗/手动）——v4.5.0：shortPkgName 修「android」歧义
  try {
    const byApp = s.byApp || {};
    const top = Object.keys(byApp).map(k => {
      const a = byApp[k] || {};
      return { k, score: (a.auto || 0) + (a.popup || 0) + (a.manual || 0), a };
    }).filter(x => x.score > 0).sort((x, y) => y.score - x.score).slice(0, 5);
    if (top.length) {
      lines.push("- 应用战绩 Top5：" + top.map(x => {
        return shortPkgName(x.k) + "(" + (x.a.auto || 0) + "/" + (x.a.popup || 0) + "/" + (x.a.manual || 0) + ")";
      }).join(" · "));
    }
  } catch (e) {}
  // 订阅命中（内置 + 远程）
  let subLine = "- 订阅：";
  try {
    const sub = JSON.parse(AndroidApp.subState());
    subLine += "内置 " + (sub.builtinApps || 0) + " 款/" + (sub.builtinRules || 0) + " 条（命中 " + (sub.builtinHits || 0) + "）";
    if (sub.remoteOk) subLine += " · 远程「" + (sub.remoteName || "") + "」" + (sub.remoteApps || 0) + " 款/" + (sub.remoteRules || 0) + " 条（命中 " + (sub.remoteHits || 0) + "）";
  } catch (e) { subLine += "内置"; }
  lines.push(subLine);
  // 能耗画像（v5.3.0：探针/省电档案/位置学习拦截维度）
  try {
    const en = JSON.parse(AndroidApp.energyStats());
    if (en && en.ok) {
      let el = "- 能耗画像（本次开机）：事件 " + en.events + " · 探针 " + (en.probes || 0) + " · 扫描 " + en.scans;
      if (en.profileSkips) el += "（省电档案跳过 " + en.profileSkips + " 窗）";
      el += " · 手势估算 " + en.microWh + "µWh";
      lines.push(el);
      if (en.posDropped) lines.push("- 位置学习验证：拦截误学 " + en.posDropped + " 次（点击后 1.5s 窗口未关 = 普通点击）");
    }
  } catch (e) {}
  // 引擎日志：最近 16 条（带时间戳）
  const log = (st.log || []).slice(-16).map(l => "  - [" + fmtTs(l.t || 0) + "] " + (l.msg || ""));
  lines.push("- 引擎日志（最近 16 条）：");
  lines.push(log.length ? log.join("\n") : "  - （暂无）");
  return lines.join("\n");
}

/* ══════════ 我的：日志 ══════════ */
function renderLog() {
  const st = getRealState();
  const log = (st.log || []).slice().reverse();
  $("#real-log").innerHTML = log.length
    ? log.map(l => "<div>" + esc(l.msg || "") + "</div>").join("")
    : '<div class="empty">暂无日志</div>';
}

/* ══════════ v5.5.0 日志上传强反馈 ══════════
 * 三重成功反馈（避免用户不确定是否提交成功）：
 *  ① 全屏弹窗确认（含 Issue 编号 + 「上传成功，感谢反馈」+ 查看链接）
 *  ② 顶部醒目横幅（绿色，滑入动画，8s 自动收起 / 手动关闭）
 *  ③ 系统通知（AndroidApp.postNotify，独立 upload 通道）
 * 上传全程阶段化 loading（收集 → 脱敏 → 提交）；失败给明确原因 + 一键重试。
 * 日志四类内容：应用版本 / 设备信息 / 拦截日志 / 异常堆栈，提交前
 * maskText 正则脱敏（Java 侧 Net.maskSensitive 再脱敏一次，双端双层）。 */

/* 脱敏（第一层，与 Net.maskSensitive 同规则）：手机号 / 身份证 / 邮箱 / IP */
function maskText(s) {
  return String(s == null ? "" : s)
    .replace(/(?<![0-9])(1[3-9]\d)\d{4}(\d{4})(?![0-9])/g, "$1****$2")
    .replace(/(?<![0-9A-Za-z])(\d{3})\d{12}(\d{3}[\dXx])(?![0-9A-Za-z])/g, "$1************$2")
    .replace(/[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})/g, "***@$1")
    .replace(/(?<![0-9])(\d{1,3}\.\d{1,3})\.\d{1,3}\.\d{1,3}(?![0-9])/g, "$1.*.*");
}

/* 上传阶段化 loading：按钮转圈 + 阶段文案 */
function setUploadPhase(btn, phase) {
  const SPIN = '<span style="display:inline-block;width:12px;height:12px;border:2px solid rgba(255,255,255,.35);border-top-color:#fff;border-radius:50%;margin-right:6px;vertical-align:-1px;animation:adspin .8s linear infinite"></span>';
  btn.innerHTML = SPIN + esc(phase);
  btn.disabled = true;
}
function resetUploadBtn(btn) {
  btn.innerHTML = "上传日志";
  btn.disabled = false;
}

/* 三重反馈之二：顶部醒目成功横幅 */
let uploadBannerTimer = null;
function showUploadBanner(number, url) {
  let b = $("#upload-success-banner");
  if (!b) {
    b = document.createElement("div");
    b.id = "upload-success-banner";
    b.style.cssText = "position:fixed;top:0;left:0;right:0;z-index:120;display:flex;align-items:center;gap:10px;"
      + "background:linear-gradient(90deg,#0c2015,#123d26);color:#fff;padding:12px 16px;"
      + "box-shadow:0 4px 18px rgba(6,40,23,.45);transform:translateY(-110%);"
      + "transition:transform .35s cubic-bezier(.22,1,.36,1);"
      + "padding-top:calc(12px + env(safe-area-inset-top));";
    b.innerHTML =
      '<span style="flex:0 0 22px;width:22px;height:22px;border-radius:50%;background:#10B981;'
      + 'display:inline-flex;align-items:center;justify-content:center;font-size:13px;font-weight:700;">✓</span>'
      + '<span style="flex:1;min-width:0;font-size:13.5px;line-height:1.45">'
      + '<b>上传成功，感谢反馈</b><br>'
      + '<span style="opacity:.85;font-size:12px">日志已提交 GitHub Issue，将直接成为下一版迭代的修复依据</span></span>'
      + '<button data-act="link" style="flex:0 0 auto;background:#10B981;border:0;color:#fff;'
      + 'border-radius:999px;padding:7px 14px;font-size:12.5px;font-weight:600">查看</button>'
      + '<button data-act="close" aria-label="关闭" style="flex:0 0 auto;background:none;border:0;'
      + 'color:#fff;opacity:.7;font-size:18px;line-height:1;padding:4px 6px">×</button>';
    document.body.appendChild(b);
    b.addEventListener("click", (ev) => {
      const act = ev.target.getAttribute && ev.target.getAttribute("data-act");
      if (act === "close") hideUploadBanner();
      if (act === "link" && url) openUrl(url);
    });
  }
  b.querySelector('[data-act="link"]').style.display = url ? "" : "none";
  requestAnimationFrame(() => { b.style.transform = "translateY(0)"; });
  if (uploadBannerTimer) clearTimeout(uploadBannerTimer);
  uploadBannerTimer = setTimeout(hideUploadBanner, 8000);
}
function hideUploadBanner() {
  const b = $("#upload-success-banner");
  if (b) b.style.transform = "translateY(-110%)";
  if (uploadBannerTimer) { clearTimeout(uploadBannerTimer); uploadBannerTimer = null; }
}

/* 三重反馈之一：全屏弹窗确认 */
function showUploadDoneModal(number, url) {
  closeModal();
  const wrap = document.createElement("div");
  wrap.id = "upload-done-modal";
  wrap.style.cssText = "position:fixed;inset:0;z-index:130;background:rgba(4,12,8,.6);"
    + "display:flex;align-items:center;justify-content:center;padding:24px;";
  wrap.innerHTML =
    '<div role="dialog" aria-modal="true" aria-label="上传成功" style="background:#fff;border-radius:16px;'
    + 'max-width:320px;width:100%;padding:22px 20px 16px;text-align:center;box-shadow:0 18px 50px rgba(0,0,0,.28)">'
    + '<div style="width:56px;height:56px;margin:0 auto 12px;border-radius:50%;background:#10B981;'
    + 'display:flex;align-items:center;justify-content:center;font-size:28px;color:#fff;font-weight:700">✓</div>'
    + '<div style="font-size:17px;font-weight:700;color:#0c2015">上传成功，感谢反馈</div>'
    + '<div style="font-size:13px;color:#5b6b60;margin:8px 0 2px">日志已提交 GitHub Issue #' + esc(number) + '</div>'
    + '<div style="font-size:12px;color:#8a978d;margin-bottom:14px">四类数据（版本 / 设备 / 拦截日志 / 异常堆栈）已脱敏上传</div>'
    + (url ? '<button data-act="link" style="width:100%;margin-bottom:8px;border:0;border-radius:10px;'
      + 'background:#10B981;color:#fff;font-size:14px;font-weight:600;padding:11px 0">在 GitHub 查看</button>' : "")
    + '<button data-act="close" style="width:100%;border:0;border-radius:10px;background:#eef2ef;'
    + 'color:#0c2015;font-size:14px;font-weight:600;padding:11px 0">好的</button></div>';
  document.body.appendChild(wrap);
  wrap.addEventListener("click", (ev) => {
    const act = ev.target.getAttribute && ev.target.getAttribute("data-act");
    if (act === "close" || ev.target === wrap) closeModal();
    if (act === "link" && url) { closeModal(); openUrl(url); }
  });
}
function closeModal() {
  const m = $("#upload-done-modal");
  if (m) m.remove();
}

/* 三重反馈之三：系统通知（独立 upload 通道；无权限时静默降级，前两重已足够） */
function notifyUploadResult(ok, number) {
  if (!HAS_BRIDGE) return;
  try {
    AndroidApp.postNotify(
      ok ? "日志上传成功" : "日志上传失败",
      ok ? "感谢反馈！日志已提交 GitHub Issue #" + number + "，将成为下一版迭代的修复依据"
         : "本次上传未完成，可回到「我的 → 引擎日志」重试",
      ok);
  } catch (e) {}
}

/* 失败原因 → 人话提示 */
function humanizeUploadError(err) {
  const e = String(err || "");
  if (e.includes("401")) return "内置令牌无效（401）——请到 GitHub 仓库 Issues 手动反馈";
  if (e.includes("403")) return "触发 GitHub 限流（403）——请稍后重试";
  if (e.includes("网络")) return "网络异常——请检查网络后重试";
  if (e.includes("令牌")) return e;
  return e || "上传失败（未知原因）";
}

/* 失败提示（含重试按钮） */
function showUploadFail(box, err) {
  box.className = "fb-result err";
  box.innerHTML = esc(humanizeUploadError(err))
    + ' <button id="btn-upload-retry" style="border:0;border-radius:999px;background:#fff;color:#b91c1c;'
    + 'font-size:12px;font-weight:700;padding:4px 12px;margin-left:6px">重试</button>';
  const rb = $("#btn-upload-retry");
  if (rb) rb.addEventListener("click", () => doUploadLog());
}

/* 上传主流程：收集（四类内容）→ 脱敏 → 提交 → 三重反馈 */
async function doUploadLog() {
  const box = $("#upload-log-result");
  const btn = $("#btn-upload-log");
  if (!HAS_BRIDGE) { box.className = "fb-result err"; box.textContent = "仅 APK 内可上传（浏览器预览请用 Web 端学习中心）"; return; }

  hideUploadBanner();
  closeModal();
  box.className = "fb-result";
  box.textContent = "";
  setUploadPhase(btn, "收集日志…");

  // ① 四类内容收集（应用版本 / 设备信息 / 拦截日志 / 异常堆栈）
  let dev = {}, crash = "";
  try { dev = JSON.parse(AndroidApp.deviceInfo()); } catch (e) {}
  await new Promise(r => setTimeout(r, 250)); // 阶段可感知（收集<->脱敏间隔）
  try { crash = AndroidApp.crashDump() || ""; } catch (e) { crash = ""; }
  setUploadPhase(btn, "本地脱敏…");

  const title = "[引擎日志][v" + appVer() + "] " + (dev.brand || "") + " " + (dev.model || "设备") + "（手动上传）";
  const bodyLines = [
    "## 设备",
    "- " + (dev.brand || "") + " " + (dev.model || "") + " · " + (dev.os || "") + " · SDK " + (dev.sdk || "?"),
    "- 应用版本：v" + appVer() + "（build " + appCode() + "） · 环境：Android APK · 手动上传",
    "",
    "## 状态",
    engineProfile(),
  ];
  if (crash) {
    bodyLines.push("", "## 上次崩溃堆栈（自动附带，已读后即删）", "```", crash.trim(), "```");
  } else {
    bodyLines.push("", "## 上次崩溃堆栈", "（无——本次开机周期内进程未崩溃）");
  }
  // ② 本地脱敏（第一层；Java Net.postIssue 提交前还会再脱敏一次）
  const body = maskText(bodyLines.join("\n"));

  setUploadPhase(btn, "提交中…");
  let res = null;
  try { res = JSON.parse(AndroidApp.netPostIssue(title, body)); } catch (e) { res = { ok: false, error: "上传异常" }; }
  resetUploadBtn(btn);

  if (res && res.ok) {
    box.className = "fb-result ok";
    box.textContent = "已上传 Issue #" + res.number;
    showUploadDoneModal(res.number, res.url);      // ① 弹窗确认
    showUploadBanner(res.number, res.url);          // ② 顶部横幅
    notifyUploadResult(true, res.number);           // ③ 系统通知
  } else {
    showUploadFail(box, res && res.error);          // 明确原因 + 重试按钮
    notifyUploadResult(false, 0);
  }
}

$("#btn-upload-log").addEventListener("click", () => { void doUploadLog(); });

/* ══════════ 我的：链接 ══════════ */
function openUrl(u) {
  if (HAS_BRIDGE) { try { AndroidApp.openUrl(u); return; } catch (e) {} }
  window.open(u, "_blank");
}
$("#link-repo").addEventListener("click", () => openUrl("https://github.com/xiaobaiba999/Skip-Ad"));

/* ══════════ Toast ══════════ */
let toastTimer = null;
function toast(msg) {
  let t = $("#toast");
  if (!t) {
    t = document.createElement("div");
    t.id = "toast";
    t.style.cssText = "position:fixed;left:50%;bottom:96px;transform:translateX(-50%);background:#0c2015;color:#fff;padding:9px 20px;border-radius:999px;font-size:13.5px;z-index:99;opacity:0;transition:opacity .2s;pointer-events:none;max-width:80vw;";
    document.body.appendChild(t);
  }
  t.textContent = msg;
  t.style.opacity = "1";
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.style.opacity = "0"; }, 2200);
}

/* ══════════ 启动 ══════════ */
(async function init() {
  refreshHome();
  renderWhitelist(true);
  renderKeepAlive();
  // v4.4.0 零触摸自愈：打开 App 即发现服务掉线（设置仍登记）→ 自动深度拉活，
  // 用户连「一键恢复」都不用点（点按钮跳系统设置看 10 秒警告的历史遗留痛点
  // 从入口上消灭）。每次页面加载最多自动尝试一次，不循环打扰。
  setTimeout(() => {
    try {
      if (!AndroidApp.isServiceRunning() && AndroidApp.a11yEnabledInSettings()) {
        doRevive();
      }
    } catch (e) {}
  }, 600);
  // v5.2.0：从系统设置返回（开完开关/配完自启动）即刻刷新——
  // 用户回 App 的瞬间就能看到「守护中」，不用等 5s 轮询
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      refreshHome();
      renderKeepAlive();
    }
  });
  // 通知权限（Android 13+，保活提醒需要）
  // 更新检查（静默）
  try {
    const d = await fetchLatest();
    if (d && d.ok && d.hasRelease) {
      const latest = String(d.tag || "").replace(/^v/, "");
      if (cmpVer(latest, appVer()) > 0) {
        $("#ub-tag").textContent = "v" + latest;
        $("#update-banner").style.display = "";
      }
    }
  } catch (e) {}
  // 每 5s 刷新首页状态（首次进入最关心）
  setInterval(() => {
    if ($("#page-home").classList.contains("active")) {
      refreshHome();
      renderKeepAlive();
    }
  }, 5000);
})();
