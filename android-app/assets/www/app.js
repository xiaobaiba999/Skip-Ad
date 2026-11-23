/* 开屏卫士 v7.13.0 · 提醒跨升级修复 + 切回即提醒 + 网络恢复自动补传 + 上传快失败专项：开屏提醒 24h 节流时间戳跨版本残留根治（旧版本点「不再提示」的时间戳吞掉升级后首次提醒——用户实锤「进入 app 没有弹出提醒」）；切回前台也是「进入 App」（visibilitychange 评估——24h/60s 双闸保护）；日志上传连接超时 10s→6s 快失败 + 四试退避；网络切换/恢复自动补传待传日志（无需手动重试） */
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
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "7.12.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "82000"; }

/* ══════════ 吉祥物（v4.1.0 全新形象：白机器人小侠 + 绿披风跳跃） ══════════
 * 与启动器图标同源；state: on（跳跃浮动）/ off（灰度待命） */
function mascotImg(size, state, head) {
  const on = state !== "off";
  return `<img src="${head ? "mascot-head.png" : "mascot.png"}" width="${size}" height="${size}" alt="吉祥物" class="${on ? "on" : "off"}" draggable="false">`;
}

$("#brand-logo").innerHTML = mascotImg(30, "on", true);

/* ═══ v7.13.0 版本记录（提醒跨升级修复 + 切回即提醒 + 网络恢复补传）═══ */
const CURRENT_RELEASE = {
  date: "2026-11-24",
  title: "开屏提醒跨升级修复 + 切回即提醒 + 上传快失败与网络恢复自动补传",
  features: [
    "开屏提醒跨升级修复：旧版本点过「24 小时不再提示」的时间戳在覆盖升级后仍生效——升级到新版后第一次进入不再被旧节流吞掉（用户实锤「进入 app 没有弹出提醒」根因）；提醒卡记录当前版本，版本变化即重新提醒",
    "切回即提醒：从后台切回前台也是「进入 App」——提醒评估在冷启动与切回两种场景都执行（60 秒短窗防旋转连弹，24 小时不再提示仍受尊重）",
    "上传快失败：连接超时 10 秒→6 秒，自动重试三次→四次（退避 0.5/1.2/2.4 秒）——失败等待大幅缩短、间歇路由下成功率提升",
    "网络恢复自动补传：切换 Wi-Fi/移动网络、网络恢复时自动补试待传日志（无需手动重传）——失败提示同步指引；自动上传保持每 8 小时心跳检查",
  ],
  summary: "v7.13.0：开屏提醒跨升级修复 + 切回即提醒 + 上传快失败与网络恢复自动补传。",
};

/* v7.0.0 新版功能描述（规范：≤5 个标题、带序号；检查更新发现新版本时就地渲染）。
 * 优先用 Java 桥预解析的 headlines[]（UpdateManifest.parse → core.ReleaseNotes
 * 纯函数，格式四代兼容：1. 序号 / ① / #- / - *）；桥字段缺失时回落 JS 解析。 */
function notesToHeadlines(d, maxN) {
  const cap = maxN || 5;
  let heads = [];
  try {
    if (d && Array.isArray(d.headlines)) heads = d.headlines.filter(h => h && h.length >= 2);
  } catch (e) {}
  if (!heads.length) {
    const out = [];
    const lines = String((d && d.notes) || "").split("\n");
    for (let i = 0; i < lines.length; i++) {
      let t = lines[i].trim();
      if (!t) continue;
      let s = null;
      const num = t.match(/^(\d{1,2})[.、)）]\s*(.+)$/);
      if (num) s = num[2];
      else if (t.charAt(0) === "#") s = t.replace(/^#+\s*/, "");
      else if (t.startsWith("- ") || t.startsWith("* ")) s = t.slice(2).trim();
      else if (/^[①-⑳]/.test(t)) s = t.slice(1).trim();
      if (!s || s.length < 2) continue;
      s = s.replace(/\[([^\]]*)\]\([^)]*\)/g, "$1").replace(/[*`>]/g, "").trim();
      if (s.length > 44) s = s.slice(0, 42) + "…";
      if (out.indexOf(s) < 0) out.push(s);
      if (out.length >= cap) break;
    }
    heads = out;
  }
  return heads.slice(0, cap);
}

/* v7.0.0：新版本功能描述渲染（我的页检查更新就地显示，≤5 条带序号）。
 * 顶部更新横幅已整体移除（用户实锤：每次检查更新出两个提示——只保留
 * 手动检查更新这一条路径，下载也只能从这里发起）。 */
function renderReleaseNotes(d) {
  const box = $("#page-notes");
  if (!box) return;
  const heads = notesToHeadlines(d, 5);
  box.innerHTML = heads.length
    ? '<div class="pn-title">新版本功能</div>'
      + heads.map((h, i) => '<div class="pn-item">' + (i + 1) + ". " + esc(h) + "</div>").join("")
    : "";
  box.style.display = heads.length ? "" : "none";
}

/* v5.8.0：权限使用审计卡已移除（用户实测：我的模块不需要展示权限审计——
 * 零权限声明已在 GitHub README 与系统设置里可查，App 内不再堆叠信息）。 */

/* ══════════ 路由 ══════════ */
$$(".tabbtn").forEach(b => b.addEventListener("click", () => {
  $$(".tabbtn").forEach(x => x.classList.remove("active"));
  b.classList.add("active");
  $$(".page").forEach(p => p.classList.remove("active"));
  $("#page-" + b.dataset.page).classList.add("active");
  const pg = b.dataset.page;
  if (pg === "home") { refreshHome(); renderWhitelist(true); }
  /* v7.11.0：订阅页已移除（id667 订阅改为后台每日自动导入——帮助页
   * 「订阅规则（自动）」卡有说明；命中数据见「我的 → 使用报告」）。 */
  if (pg === "me") { renderLog(); renderReport(true); } // v7.2.0：进入我的页静默刷新使用报告（饼图常驻，零 toast）
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
}

/* ═══ v7.11.0 冷启动宽限期：刚断开不久（≤10 分钟）且服务未跑、登记也暂未
 * 读到时——AMS 重绑慢（issue#56 同族场景）极可能只是慢，不是真吊销。
 * 此时首页显示「服务重连中」而非「未开启」；前台看门狗每 3s 复查，登记
 * 一回来就自动拉活，UI 自动切「守护中」——绝不把重绑慢误导向系统设置
 * 看 10 秒警告（用户指令：解决每次退出重进都要重新开启）。 */
const SERVICE_GRACE_MS = 10 * 60 * 1000;
function serviceGraceActive() {
  if (!HAS_BRIDGE) return false;
  try {
    const t = parseInt(AndroidApp.serviceLastConnectedAt(), 10) || 0;
    return t > 0 && Date.now() - t < SERVICE_GRACE_MS;
  } catch (e) { return false; }
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
  } else if (serviceGraceActive()) {
    /* v7.11.0 冷启动宽限期：刚断开不久、登记暂未读到（AMS 重绑慢）——
     * 显示「服务重连中」而非「未开启」；前台看门狗 3s 复查登记，一回来
     * 就自动拉活（看门狗与 5s 刷新循环已覆盖，无需额外定时器）。 */
    card.classList.remove("on", "paused");
    mascot.innerHTML = mascotImg(88, "off");
    title.textContent = "服务重连中";
    sub.textContent = "正在等待系统重绑（通常几秒内）";
    tip.textContent = "刚断开不久，卫士正在自动恢复——无需重新开启、不用去系统设置";
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
    // 旧版同步兜底（理论上不会走到）
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

/* ═══ v6.1.0 免打扰持久模式已取消 ═══
 * 用户实锤：「高级免打扰持久模式不能直接在安装的时候实现，一定要在
 * 电脑上执行的话就取消吧，这样很麻烦」——ADB WRITE_SECURE_SETTINGS
 * 授权必须连电脑，与「零电脑」体验目标冲突，整卡移除。普通用户路径
 * （一键恢复 + 前台看门狗 + 零触摸自愈）覆盖全部免电脑场景。 */

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

/* ══════════ v5.8.0 保活体检（防呆简化版）══════════
 * 用户实锤：「保活体验太多描述一大串，优化怎么简单怎么来」——
 * 一行状态 + 只列待处理项（每项一键直达）：
 *  · 全部通过 → 一行绿字「✓ 全部通过，防护稳了」，零列表零描述；
 *  · 有待处理 → 只列待处理项，标签+一个「去设置」按钮，别的什么都不说。 */
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
  const pend = [];
  if (!running) pend.push({ label: "服务绑定", desc: "点「一键恢复」即可", action: null });
  if (!notif) pend.push({ label: "通知权限", desc: "恢复提醒收不到", action: "notif" });
  if (!batt) pend.push({ label: "电池白名单", desc: "省电策略可能杀服务", action: "battery" });
  if (d && d.items && d.items.length) {
    d.items.forEach((it) => pend.push({ label: it.label, desc: "厂商设置，去确认一下", action: "oem:" + it.id, manual: true }));
  }
  const list = $("#ka-list");
  const chip = $("#ka-chip");
  if (!pend.length) {
    list.innerHTML = '<div class="ka-ok-line">✓ 全部通过，防护稳了</div>';
    if (chip) { chip.textContent = "全部通过"; chip.className = "chip ka-chip-ok"; }
  } else {
    if (chip) { chip.textContent = pend.length + " 项可加固"; chip.className = "chip ka-chip-warn"; }
    list.innerHTML = "";
    pend.forEach((r) => {
      const row = document.createElement("div");
      row.className = "ka-row";
      const info = document.createElement("div");
      info.className = "ka-info";
      const name = document.createElement("div");
      name.className = "ka-name";
      name.textContent = r.label;
      const desc = document.createElement("div");
      desc.className = "ka-desc";
      desc.textContent = r.desc;
      info.appendChild(name);
      info.appendChild(desc);
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
  }
  card.style.display = "";
}

bindToggle("toggle-popup", "popupShield", "弹窗屏蔽已开启", "弹窗屏蔽已关闭");
bindToggle("toggle-learning", "learning", "习惯学习已开启", "习惯学习已关闭");

/* ══════════ v7.9.7 自动提交诊断日志授权（独立开关 · 默认关）→ v8.0.0 中转上线真数据 ══════════
 * 与无障碍授权严格分离；授权≠上传——上传仍受服务端 24h 滑窗（最多 3 次）
 * 与 batchId 幂等约束（中转服务 skip-ad-log-relay 已上线）。
 * v8.0.0：状态行真数据（待传批次/24h 已提交（服务端权威，本地仅展示参考）/
 * 失败检视/最近失败原因）+「立即提交」（v7.10.0：本地与服务端均无次数限制——幂等保护保持）
 * +「清理待上传」（只删本地文件，不影响服务端已建 Issue）。 */
function bridgeCall(fn, fallback) {
  try {
    if (HAS_BRIDGE && AndroidApp[fn]) return AndroidApp[fn]();
  } catch (e) {}
  return fallback;
}
function refreshLogUploadNote() {
  const note = $("#log-upload-note");
  if (!note) return;
  const on = !!bridgeCall("logUploadConsent", false);
  const actions = $("#log-upload-actions");
  if (actions) actions.style.display = on ? "" : "none";
  /* v7.10.0：上传次数限制彻底移除——状态行不再展示 24h 次数额度；自动上传
   * 仅受心跳间隔（8h 检查一次）与「有新日志才传」约束，无任何次数上限。 */
  if (!on) {
    note.textContent = "默认关闭 · 日志采集与手动上传不受影响（自动上传无次数限制）";
    return;
  }
  let st = null;
  try { st = JSON.parse(bridgeCall("logUploadStatus", null)); } catch (e) {}
  if (!st) {
    note.textContent = "自动上传已开启 · 每 8 小时检查一次 · 无次数限制";
    return;
  }
  const agoMin = st.lastAckedAt > 0 ? Math.max(1, Math.round((Date.now() - st.lastAckedAt) / 60000)) : 0;
  let s = "自动上传已开启 · 每 8 小时检查一次 · 无次数限制"
    + " · 待传 " + (st.pendingEvents || 0) + " 条";
  if (agoMin > 0) s += " · 最近成功 " + (agoMin >= 1440 ? Math.round(agoMin / 1440) + " 天前" : agoMin + " 分钟前");
  if ((st.rejected || 0) > 0) s += " · 拒收检视 " + st.rejected + " 批";
  note.textContent = s;
}
(function bindLogUploadToggle() {
  const el = $("#toggle-log-upload");
  if (!el) return;
  try {
    el.checked = !!bridgeCall("logUploadConsent", false);
  } catch (e) {}
  el.addEventListener("change", () => {
    try {
      if (HAS_BRIDGE && AndroidApp.setLogUploadConsent) AndroidApp.setLogUploadConsent(el.checked);
      else store.set("logUploadConsent", el.checked);
    } catch (e) {
      store.set("logUploadConsent", el.checked);
    }
    toast(el.checked ? "已开启自动上传（每 8 小时检查一次，无次数限制；采集与手动上传不受影响）" : "自动上传已关闭（日志仍会采集，可随时手动上传）");
    setTimeout(refreshLogUploadNote, 200);
  });
  refreshLogUploadNote();
})();
(function bindLogUploadActions() {
  const nowBtn = $("#btn-log-upload-now");
  if (nowBtn) nowBtn.addEventListener("click", () => {
    // v8.1.0：统一走 doUploadLog（真上传 + 强制封批 + 结果回调反馈）——
    // 旧版 void 调用零反馈（点完只有一句 toast，成败无从知晓）
    if (HAS_BRIDGE && AndroidApp.logUploadSubmitNow) {
      doUploadLog(true);
    } else {
      toast("当前环境不支持自动上传");
    }
  });
  const clearBtn = $("#btn-log-upload-clear");
  if (clearBtn) clearBtn.addEventListener("click", () => {
    if (HAS_BRIDGE && AndroidApp.logUploadClearPending) {
      let r = null;
      try { r = JSON.parse(AndroidApp.logUploadClearPending()); } catch (e) {}
      toast(r && r.ok ? "已清理 " + (r.cleared || 0) + " 条本地待上传日志" : "清理失败");
      setTimeout(refreshLogUploadNote, 400);
    } else {
      toast("当前环境不支持自动上传");
    }
  });
})();

/* ══════════ v4.3.0 应用白名单（v7.12.0 异步加载——卡死根治）══════════
 * 死因（用户实锤：「卫士有时候点击按钮时或者进入时会导致卡死，重新进入
 * 也不行，只能重新安装」）：旧版进入 App/每次切首页 tab/每次勾选白名单都
 * 同步调 whitelistJson 桥——JavaBridge 线程上 300+ PackageManager IPC，慢
 * 设备上以分钟计（v7.11.0 init 注释实测教训），JS 线程全程冻住＝「卡死」；
 * 重进再来一遍＝「重新进入也不行」。v7.12.0 改异步：
 *  · 桥 whitelistJsonAsync(cb)：后台线程枚举 + window[cb](json) 回调（与
 *    同步桥共用 Java 侧 30s 记忆缓存——绝大多数回调即时命中）；
 *  · 首次进入显示「加载中…」骨架，回调后渲染——JS 主线程零阻塞；
 *  · 10s 看门狗 + 自动重试（至多 3 次）——慢设备也绝无「永久加载」；
 *  · 勾选增删后本地更新缓存立即重渲（绝不再全量重拉——同步枚举是死因）；
 *    30s 缓存过期后的下一次进入/切页自然刷新。 */
let wlCache = null; // {list:Set, known:[{pkg,label}]}
let wlCacheAt = 0;  // v7.12.0：本地缓存时刻（30s 新鲜窗——窗内不重拉）
let wlAsyncSeq = 0;  // v7.12.0：异步请求序号（过期回调丢弃）
let wlRetries = 0;   // v7.12.0：看门狗/失败自动重试计数（至多 3 次）
let wlQuery = "";   // v7.1.0：白名单搜索词（中文名/包名实时过滤）
let wlSearchTimer = null; // v7.2.0：搜索输入 120ms debounce 定时器
let wlTotalEl = null;     // v7.2.0：列表上方「共 N 款应用」统计行（动态创建，避免改 index.html 结构）

/* v7.12.0：异步拉取白名单 JSON（单飞序号 + 10s 看门狗 + 过期回调丢弃）。 */
function wlBridgeAsync() {
  const seq = ++wlAsyncSeq;
  const cbName = "__wlCb" + seq;
  return new Promise((resolve) => {
    let settled = false;
    const done = (v) => {
      if (settled || seq !== wlAsyncSeq) return;   // 过期回调丢弃（新请求已发起）
      settled = true;
      clearTimeout(timer);
      try { delete window[cbName]; } catch (e) {}
      resolve(v);
    };
    const timer = setTimeout(() => done(null), 10 * 1000);  // 10s 看门狗（慢机兑底）
    try {
      window[cbName] = (json) => {
        let v = null;
        if (typeof json === "string") { try { v = JSON.parse(json); } catch (e) { v = null; } }
        else v = json;
        done(v && typeof v === "object" ? v : null);
      };
      AndroidApp.whitelistJsonAsync(cbName);
    } catch (e) { done(null); }
  });
}

/* v7.12.0：请求白名单数据（异步——绝不阻塞 JS 线程）；失败自动重试至多 3
 * 次（1.5s 间隔），仍失败渲染「未能读取」空态（切回本页/下次进入再试）。 */
async function requestWhitelist() {
  if (!HAS_BRIDGE) { wlCache = { list: new Set(), known: [] }; wlCacheAt = Date.now(); return; }
  const d = await wlBridgeAsync();
  if (d && Array.isArray(d.list)) {
    wlCache = { list: new Set(d.list), known: d.known || [] };
    wlCacheAt = Date.now();
    wlRetries = 0;
  } else if (wlRetries < 3) {
    wlRetries++;
    setTimeout(() => { void requestWhitelist(); }, 1500);
    return;
  }
  renderWhitelist(false);
}

/* ═══ v7.1.0 白名单搜索匹配（用户实锤：应用白名单搜索栏实现 app 中文名搜索）═══
 * 匹配面：App 中文名（label，如「微信」「哔哩哔哩」）与应用包名（pkg）
 * 双通道不区分大小写包含匹配；去空格后逐字符匹配（中文名无空格语义）。 */
function wlMatch(item, q) {
  if (!q) return true;
  const label = String(item.label || "").toLowerCase();
  const pkg = String(item.pkg || "").toLowerCase();
  return label.indexOf(q) >= 0 || pkg.indexOf(q) >= 0;
}

/* ═══ v7.2.0 白名单全量应用列表（用户实锤：搜索不到新装/未被拦截过的应用）═══
 * 后端 whitelistJson 已改为返回全部已安装可启动应用（∪白名单∪战绩，上限 500，
 * 30s 缓存）；前端配合：列表上方加「共 N 款应用」统计行、去截断、空态改
 * 「未能读取应用列表」（默认即全量，不再有“暂无记录”态）。 */
function wlTotalRow() {
  if (wlTotalEl && wlTotalEl.parentNode) return wlTotalEl;
  const box = $("#wl-list");
  if (!box || !box.parentNode) return null;
  wlTotalEl = document.createElement("div");
  wlTotalEl.className = "wl-total";
  box.parentNode.insertBefore(wlTotalEl, box);
  return wlTotalEl;
}

function renderWhitelist(force) {
  const box = $("#wl-list");
  if (!box) return;
  /* v7.12.0 异步化（卡死根治）：同步桥 whitelistJson 在 JavaBridge 线程上
   * 枚举全机应用（300+ PackageManager IPC，慢设备分钟级）——进入/切页/
   * 勾选后重拉全部改异步：无新鲜缓存则后台拉取 + 骨架占位，回调后再渲。
   * 30s 本地新鲜窗：窗内直接用缓存（绝大多数场景零桥调用）。 */
  if (force) wlRetries = 0;   // 用户主动进入/切换 → 重试预算重置
  if (HAS_BRIDGE) {
    const fresh = wlCache && (Date.now() - wlCacheAt) < 30000;
    if (!fresh) void requestWhitelist();
    if (!wlCache) {           // 尚无任何数据：骨架占位（绝不同步枚举）
      box.innerHTML = '<div class="empty">加载中…</div>';
      return;
    }
  } else if (!wlCache) {
    wlCache = { list: new Set(), known: [] };
  }
  const wl = wlCache;
  $("#wl-count").textContent = String(wl.list.size);
  // v7.2.0：列表上方统计行——全量基数（known.length，与搜索过滤无关）
  const knownAll = wl.known || [];
  const totalRow = wlTotalRow();
  if (totalRow) totalRow.textContent = "共 " + knownAll.length + " 款应用";
  // 白名单里的排前面，其余中文序；v7.1.0：搜索词实时过滤（中文名/包名，
  // 大小写不敏感）；v7.2.0：去 slice 截断，全量展示（上限由后端 500 控制）
  const q = wlQuery.trim().toLowerCase();
  const known = knownAll.filter(x => wlMatch(x, q)).sort((a, b) => {
    const wa = wl.list.has(a.pkg) ? 0 : 1, wb = wl.list.has(b.pkg) ? 0 : 1;
    if (wa !== wb) return wa - wb;
    return String(a.label).localeCompare(String(b.label), "zh");
  });
  if (!known.length) {
    box.innerHTML = q
      ? '<div class="empty">没有匹配「' + esc(wlQuery.trim()) + '」的应用——可尝试中文名或包名关键字</div>'
      : '<div class="empty">未能读取应用列表</div>';
    return;
  }
  box.innerHTML = known.length
    ? known.map(x => {
    const on = wl.list.has(x.pkg);
    return '<div class="wl-row">'
      + '<div class="wl-label"><span class="wl-name">' + esc(x.label) + '</span>'
      + '<span class="wl-pkg">' + esc(x.pkg) + '</span></div>'
      + '<label class="switch"><input type="checkbox" data-wl="' + esc(x.pkg) + '"'
      + (on ? ' checked' : '') + '><span class="slider"></span></label>'
      + '</div>';
  }).join("")
    : '<div class="empty">未能读取应用列表（设备繁忙或权限受限）——可切回本页重试</div>';
  box.querySelectorAll('input[data-wl]').forEach(t => {
    t.addEventListener("change", () => {
      const pkg = t.dataset.wl;
      let ok = false;
      try {
        ok = t.checked ? AndroidApp.whitelistAdd(pkg) : AndroidApp.whitelistRemove(pkg);
      } catch (e) {}
      /* v7.12.0：本地即改即显——绝不再全量重拉（同步枚举是卡死元凶；
       * Java 侧 30s 缓存已由增删失效，下次自然刷新带回权威列表） */
      if (ok && wlCache) {
        if (t.checked) wlCache.list.add(pkg); else wlCache.list.delete(pkg);
      }
      toast(t.checked ? "已加入白名单：卫士在该应用内完全让路" : "已移出白名单：恢复守护");
      renderWhitelist(false);
    });
  });
}

/* v7.1.0：搜索栏输入即过滤（中文名/包名实时过滤，零按钮防呆）；
 * v7.2.0：输入加 120ms debounce——全量列表渲染量大，逐键直渲会拖慢输入
 * （wlQuery 在防抖回调里更新，渲染用的始终是最终词） */
if ($("#wl-search")) {
  $("#wl-search").addEventListener("input", () => {
    clearTimeout(wlSearchTimer);
    wlSearchTimer = setTimeout(() => {
      wlQuery = $("#wl-search").value || "";
      renderWhitelist(false);
    }, 120);
  });
}

$("#btn-wl-add").addEventListener("click", () => {
  const inp = $("#wl-input");
  const pkg = inp.value.trim();
  if (!pkg) { toast("请输入应用包名"); return; }
  if (!HAS_BRIDGE) { toast("仅 APK 内可用"); return; }
  let ok = false;
  try { ok = AndroidApp.whitelistAdd(pkg); } catch (e) {}
  if (ok) {
    inp.value = "";
    if (wlCache) wlCache.list.add(pkg);   // v7.12.0：本地即改（不再全量重拉）
    toast("已加入白名单：" + pkg);
    renderWhitelist(false);
  }
  else toast("添加失败：包名格式不正确或已存在");
});

$("#wl-input").addEventListener("keydown", (e) => {
  if (e.key === "Enter") $("#btn-wl-add").click();
});


/* ══════════ v7.11.0 订阅模块：页面整体移除（后台自动导入）════
 * 用户指令：去除底栏订阅模块——id667 的 GKD 订阅（Lin-arm ⭐7.4k，
 * 日更社区事实标准）由 SubscriptionManager.maybeDailyAutoImport 在
 * 服务连接 + 心跳时每日后台自动导入一次（八通道镜像链 + APK 内置离线
 * 快照双重兜底，永不硬失败——见 Java 侧）；本页 UI 与全部事件绑定随之
 * 移除（#page-sub DOM 已不在 index.html）。命中与覆盖数据保留在
 * 「我的 → 使用报告」订阅两源命中段（subState 桥仍在）。 */
function fmtTime(ms) {
  try { return new Date(ms).toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false }); }
  catch (e) { return "--"; }
}

/* ══════════ 我的：版本与更新 ══════════ */
$("#me-ver").textContent = "v" + appVer();
$("#ver-chip").textContent = "v" + appVer();

/* v7.2.0：版本记录按序号 1. 2. 3. 4. 描述功能（用户实锤）——1 个标题 +
 * 序号功能行逐条渲染（features 数组）；样式复用 log-v/log-d，历史版本归档
 * GitHub Releases。 */
$("#changelog").innerHTML =
  '<div class="log-item"><div class="log-v">v' + esc(appVer())
  + ' · ' + esc(CURRENT_RELEASE.date) + '</div>'
  + '<div class="log-d"><b>' + esc(CURRENT_RELEASE.title) + '</b></div>'
  + (CURRENT_RELEASE.features || []).map((f, i) =>
      '<div class="log-d">' + (i + 1) + ". " + esc(f) + '</div>').join("")
  + '</div>';

/* GH_API（api.github.com 基址）已于 v7.9.8 移除——更新检查零 GitHub REST API
 * （静态清单见 UPDATE_MANIFEST_URL；根因：匿名 60 次/小时/IP 共享配额限流） */
let ubTimer = null;
let latestRelease = null;
let dl = { channels: [], idx: 0, fileName: "", digest: "", size: 0, prefix: "page", lastBytes: -1, lastMoveAt: 0 };

/* ═══ v7.10.0：cmpVer/versionCodeFromNotes 已删（统一版本规则专项）═══
 * versionName 三段比较函数 cmpVer 与 Release 正文 versionCode 元数据行解析
 * versionCodeFromNotes 连同全部回退链一并移除——全项目统一
 * remoteVersionCode > currentVersionCode；versionName 仅展示。死灰复燃
 * 防线见 test_update_compat.js 源码断言（function cmpVer 不得回场）。 */

/* ═══ v7.9.8 更新检查（静态清单架构——彻底脱离 GitHub 匿名 REST API）════
 * 根因史：v7.9.7 及以前检查更新直连 api.github.com /releases/latest（匿名
 * 60 次/小时/IP 出口配额）——国内共享出口下所有用户同池互耗，进入更新页
 * 即「查询失败：GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试」。
 * 新链路：
 *  APK 内：AndroidApp.updateCheckAsync(manual, cb)（v7.10.0 异步化——JS
 *  主线程零网络等待；旧同步桥 updateCheck 已移除）→ UpdateRepository
 *  （静态清单 raw.githubusercontent.com main 分支 + Fastly CDN 零 REST
 *  配额 + 15min 本地缓存 + 单飞并发合并 + 手动 30s 冷却/自动单次 +
 *  失败指数退避 + ETag 304 + 受控备用源 + 错误十类）→ UpdateManifest
 *  （清单契约校验：包名/版本/APK 地址发布通道/SHA256）。isNewer 权威判定
 *  = versionCode 主判（Net.isNewerRelease——v7.10.0 统一规则：
 *  remoteVersionCode > currentVersionCode，versionName 仅展示）。
 *  浏览器预览：同语义静态清单直连（镜像映射——零 GitHub API）。 */

/* 静态更新清单源链（与 Java UpdateRepository.MANIFEST_SOURCES 同源——
 * v7.9.9 网络专项：raw 主源 + jsDelivr 四接入点备用链。github.io 备用源
 * 已移除：仓库 Pages 未启用（恒 404）且 github.io 域国内被阻断——
 * 小米 15 Pro 真机双源全灭根因之一。jsDelivr 四接入点为订阅通道
 * SubMirrors 同族实战链（国内可达性最佳实践）。 */
const UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json";
const UPDATE_MANIFEST_SOURCES = [
  UPDATE_MANIFEST_URL,
  "https://cdn.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json",
  "https://fastly.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json",
  "https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json",
  "https://testingcf.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json",
];

/* ═══ v7.11.0 检查更新失败兜底通道（用户指令：每版本做兜底——查询失败时
 * 直接由 jsDelivr 最新版链接下载）═══
 * 仓库内 APK 归档（android-app/dist/）+ 恒指向最新版的别名副本
 * （adshield-latest.apk，发布时随同归档入库）——检查失败时无需任何
 * 元数据即可直达最新版；下载后手动覆盖安装（浏览器/系统下载器通道）。
 * 成功检查路径的兜底主通道在清单 cdnApkUrl（apkMirrors 镜像切换，
 * SHA-256/大小闸照常——见 UpdateManifest/Downloader）。 */
const FALLBACK_CDN_LATEST = "https://cdn.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-latest.apk";

/* 更新错误十类 → 人话（Java UpdateManifest 错误码的 UI 侧唯一文案出口；
 * 不再把服务端限流/网络异常混为一谈——每类如实转达） */
function updateErrText(code) {
  const m = {
    UPDATE_NETWORK_ERROR: "网络连接失败，请检查网络后重试",
    UPDATE_DNS_ERROR: "网络域名解析失败，请检查网络或稍后重试",
    UPDATE_TIMEOUT: "网络请求超时，请稍后重试",
    UPDATE_TLS_ERROR: "安全连接失败，请稍后重试",
    UPDATE_HTTP_ERROR: "更新服务返回异常，请稍后重试",
    UPDATE_INVALID_MANIFEST: "更新信息格式异常，已忽略本次结果",
    UPDATE_PACKAGE_MISMATCH: "更新信息与当前应用不匹配，已拒绝",
    UPDATE_VERSION_INVALID: "更新信息版本号异常，已忽略",
    UPDATE_NO_CACHE: "暂时无法检查更新，请稍后重试",
    UPDATE_RATE_LIMITED: "请求过于频繁，请稍后重试",
    UPDATE_SERVER_ERROR: "更新服务暂时不可用，请稍后重试",
  };
  return m[code] || "";
}

/* 缓存数据年龄 → 人话（「网络异常，展示缓存数据 · 3 分钟前」用） */
function ageText(ms) {
  const t = parseInt(ms, 10) || 0;
  if (t <= 0) return "";
  const min = Math.max(1, Math.round((Date.now() - t) / 60000));
  if (min < 60) return min + " 分钟前";
  const hr = Math.round(min / 60);
  if (hr < 24) return hr + " 小时前";
  return Math.round(hr / 24) + " 天前";
}

/* 浏览器预览：静态清单 JSON → 页面可用结构（APK 原生侧 UpdateManifest.parse
 * 的 JS 镜像——字段兼容旧 GitHub Release 通道消费方） */
function mapManifest(j) {
  const remoteVc = parseInt(j.versionCode, 10) || 0;
  const tag = String(j.releaseTag || "");
  return {
    ok: true, hasRelease: true, source: "manifest",
    tag, name: j.releaseName || tag,
    notes: String(j.releaseNotes || ""),
    versionName: j.versionName || "", versionCode: remoteVc, remoteVersionCode: remoteVc,
    apkUrl: j.apkUrl || "", apkFileName: j.apkFileName || "adshield.apk",
    apkMB: j.apkSize ? Math.round(j.apkSize / 1048576 * 100) / 100 : 0,
    apkSize: j.apkSize || 0,
    apkDigest: j.apkSha256 ? "sha256:" + String(j.apkSha256).replace(/^sha256:/i, "").toLowerCase() : "",
    apkMirrors: j.cdnApkUrl ? [j.cdnApkUrl] : [], // v7.11.0：CDN 兜底镜像（清单携带）
  };
}

/* ═══ v7.10.0 异步更新检查（用户指令：禁止在 JavaScript 主线程同步等待网络）═══
 * 原生桥 updateCheckAsync(manual, cb) 后台线程执行 UpdateRepository.check，
 * 结果经 window[cb](json) 回调（旧同步桥 updateCheck 已移除——JS 调用会
 * 阻塞 JS 线程直至网络返回，五源链预算 40s——弱网下页面冻结 40s 的实锤
 * 根因）。三重防线：
 * ① JS 单飞（updInFlight）：同一时刻至多一个在飞检查，并发调用共享同一
 *   Promise（与原生侧 FlightGate 同族语义——绝不请求风暴）；
 * ② 过期序号（updSeq）：每次发起序号 +1，旧序号的回调一律丢弃——快速
 *   连续触发时只有最后一次的结果生效（阻止过期回调覆盖新状态）；
 * ③ 90s 兜底：桥回调永不抵达（线程丢失/进程竞态）时按超时失败收尾——
 *   按钮与状态必然恢复，手动检查绝不永久卡「检查中…」。 */
let updInFlight = null;
let updSeq = 0;
const UPD_ASYNC_TIMEOUT_MS = 90 * 1000;
function bridgeUpdateCheck(manual) {
  const seq = ++updSeq;
  const cbName = "__updCb" + seq;
  return new Promise((resolve) => {
    let settled = false;
    const done = (v) => {
      if (settled) return;
      if (seq !== updSeq) {   // 过期回调丢弃（已有更新一次的检查在飞）
        try { delete window[cbName]; } catch (e) {}
        return;
      }
      settled = true;
      clearTimeout(timer);
      try { delete window[cbName]; } catch (e) {}
      resolve(v);
    };
    const timer = setTimeout(() => {   // ③ 90s 兜底：回调永不抵达也必然收尾
      if (settled) return;
      settled = true;
      try { delete window[cbName]; } catch (e) {}
      resolve({ ok: false, error: "检查超时", errorCode: "UPDATE_TIMEOUT" });
    }, UPD_ASYNC_TIMEOUT_MS);
    try {
      window[cbName] = (json) => {
        let v = json;
        if (typeof v === "string") { try { v = JSON.parse(v); } catch (e) { v = { ok: false, error: "检查失败", errorCode: "UPDATE_NO_CACHE" }; } }
        done(v && typeof v === "object" ? v : { ok: false, error: "检查失败", errorCode: "UPDATE_NO_CACHE" });
      };
      AndroidApp.updateCheckAsync(!!manual, cbName);
    } catch (e) {
      done({ ok: false, error: "检查失败", errorCode: "UPDATE_NO_CACHE" });
    }
  });
}

async function fetchLatest(manual) {
  if (HAS_BRIDGE) {
    /* ① JS 单飞：在飞期间并发调用共享同一 Promise（首个 manual 语义生效） */
    if (updInFlight) return updInFlight;
    updInFlight = bridgeUpdateCheck(manual).finally(() => { updInFlight = null; });
    return updInFlight;
  }
  /* 浏览器预览：静态清单源链直连（与 APK 原生通道同源——零 GitHub API；
   * 每源至多一次，任一源成功即返回；包名不匹配/清单非法也换下一源
   * （疑似劫持/错源——不采信也不放弃）；全链失败 → 首个错误码如实报告） */
  let firstErr = null;
  for (let i = 0; i < UPDATE_MANIFEST_SOURCES.length; i++) {
    const url = UPDATE_MANIFEST_SOURCES[i];
    try {
      const r = await fetch(url, { headers: { Accept: "application/json" } });
      if (!r.ok) { // 404/5xx 等 → 试下一源
        if (!firstErr) firstErr = { errorCode: "UPDATE_HTTP_ERROR" };
        continue;
      }
      let j = null;
      try { j = await r.json(); } catch (e) { j = null; }
      if (!j || typeof j !== "object") { // 坏 JSON → 试下一源
        if (!firstErr) firstErr = { errorCode: "UPDATE_INVALID_MANIFEST" };
        continue;
      }
      if (j.draft || j.prerelease) return { ok: true, hasRelease: false };
      if (j.packageName && j.packageName !== "com.zai.adshield") {
        if (!firstErr) firstErr = { errorCode: "UPDATE_PACKAGE_MISMATCH" };
        continue;
      }
      return mapManifest(j);
    } catch (e) { /* 网络异常 → 试下一源 */
      if (!firstErr) firstErr = { errorCode: "UPDATE_NETWORK_ERROR" };
    }
  }
  const errCode = (firstErr && firstErr.errorCode) || "UPDATE_NETWORK_ERROR";
  return { ok: false, error: updateErrText(errCode) || "网络连接失败，请检查网络后重试", errorCode: errCode };
}

/* ═══ v7.10.0：versionCodeFromNotes 已删（统一版本规则专项）═══
 * Release 正文 versionCode 元数据行解析（Java Net.versionCodeOfNotes 镜像）
 * 随回退链一并移除——清单 versionCode 缺失即不判新，绝不从正文回退定码。 */

/* 新版本判定（Java Net.isNewerRelease 镜像——v7.10.0 统一规则）：
 * remoteVersionCode > currentVersionCode 才算新版；任一侧缺失/非法（≤0）
 * → 不提示（versionName 仅展示，绝不作为更新判断依据）。
 * （fetchLatest 已移至上方静态清单版；本函数为浏览器预览 isNewer 兜底保留） */
function isNewerByCode(remoteVc, localVc) {
  return remoteVc > 0 && localVc > 0 && remoteVc > localVc;
}

/* ═══ v7.9.6plus 下载状态机（prefix = page|gate——「我的」页与全屏更新页同一状态机）════
 * 状态全集（用户指令·更新页下载状态）：
 *  connecting 正在连接下载服务器（DownloadManager 入队）· downloading 正在下载
 *  （百分比+已下载/总大小）· source 当前使用的下载源（host）· retrying 下载中断
 *  正在自动重试（系统断点续传——HTTP Range）· switching 正在切换备用下载源 ·
 *  verifying 正在校验 APK（SHA-256+大小）· verify-ok 校验成功准备安装 ·
 *  failed 下载失败可重新尝试 · all-failed 所有下载源均不可用。
 * 多源机制：channels 数组（当前仅 GitHub 官方源——ghproxy 类代理已移除，
 * 无经所有者确认的备用源）；失败/30s 无进展自动切换下一源；单源时切换
 * 直接落入 failed（提供重新下载入口）。切换源不改变 APK 内容——所有源
 * 同一 Release 资产、同一 digest/size 校验（安装闸绝不降级）。 */
function beginChannel() {
  const url = dl.channels[dl.idx];
  try {
    /* v7.3.0 安全（v7.9.8 措辞更新）：digest/size（静态清单 HTTPS 元数据）
     * 随下载任务交给原生侧——安装前 Downloader 会流式计算 SHA-256 比对；
     * 代理投毒/共享盘换包在安装前被拦下并自动删除。 */
    const r = JSON.parse(AndroidApp.startUpdateDownload(url, dl.fileName, dl.digest, dl.size));
    if (r.ok) {
      const btn = $("#" + dl.prefix + "-dl-btn");
      if (btn) { btn.textContent = "下载中…"; btn.disabled = true; }
      const wrap = $("#" + dl.prefix + "-progress-wrap");
      if (wrap) wrap.style.display = "";
      setDlStateText(dl.prefix, "connecting", 0, 0, 0, hostOfUrl(url));
      dl.lastBytes = -1;
      dl.lastMoveAt = Date.now();
      startProgressPoll(dl.prefix);
    } else { toast(r.error || "启动下载失败"); onAllChannelsFailed(dl.prefix, r.error || "启动下载失败"); }
  } catch (e) { toast("启动下载失败"); onAllChannelsFailed(dl.prefix, "启动下载失败"); }
}

/* URL → host（当前使用的下载源显示） */
function hostOfUrl(u) {
  try { return new URL(u).host; } catch (e) { return ""; }
}

/* 状态文案渲染（状态机的唯一文案出口——两入口一致） */
function setDlStateText(prefix, state, percent, bytes, total, host) {
  const txt = $("#" + prefix + "-progress-text");
  const bar = $("#" + prefix + "-bar");
  if (!txt) return;
  const mb = (n) => (n / 1048576).toFixed(1);
  const src = host ? "（源：" + host + "）" : "";
  if (state === "connecting") txt.textContent = "正在连接下载服务器…" + src;
  else if (state === "running") txt.textContent = "正在下载 " + percent + "% · " + mb(bytes) + " / " + (total > 0 ? mb(total) : "?") + " MB" + src;
  else if (state === "retrying") txt.textContent = "下载中断，正在自动重试（断点续传）…" + src;
  else if (state === "switching") txt.textContent = "正在切换备用下载源…";
  else if (state === "verifying") txt.textContent = "正在校验 APK（SHA-256 + 大小）…";
  else if (state === "verify-ok") txt.textContent = "校验成功，准备安装";
  else if (state === "failed") txt.textContent = "下载失败，可点击重新下载";
  else if (state === "all-failed") txt.textContent = "所有下载源均不可用——请检查网络后重试";
  if (bar && state === "running") bar.style.width = percent + "%";
}

/* 全部源失败（提供重新下载入口——不要求用户反复手动点击切换） */
function onAllChannelsFailed(prefix, reason) {
  setDlStateText(prefix, "all-failed", 0, 0, 0, "");
  const btn = $("#" + prefix + "-dl-btn");
  if (btn) { btn.textContent = "重新下载"; btn.disabled = false; btn.dataset.phase = "download"; }
  const note = $("#" + prefix + "-dl-note");
  if (note) { note.className = "fb-result err"; note.style.display = ""; note.textContent = "下载失败：" + (reason || "网络异常") + "——日志与数据不受影响，可稍后重试"; }
}

function switchChannel(reason) {
  if (dl.idx + 1 < dl.channels.length) {
    dl.idx++;
    setDlStateText(dl.prefix, "switching", 0, 0, 0, "");
    toast(reason + "，已切换备用下载源");
    beginChannel();
    return true;
  }
  return false;
}

function startPageDownload(d, prefix) {
  if (!d.apkUrl) { toast("该版本没有 APK 附件"); return; }
  if (!HAS_BRIDGE) { window.open(d.apkUrl, "_blank"); return; }
  dl = { channels: [d.apkUrl].concat(d.apkMirrors || []), idx: 0, fileName: d.apkFileName || "adshield.apk", digest: d.apkDigest || "", size: d.apkSize || 0, prefix, lastBytes: -1, lastMoveAt: Date.now() };
  const note = $("#" + prefix + "-dl-note");
  if (note) { note.style.display = "none"; note.textContent = ""; }
  beginChannel();
}

function startProgressPoll(prefix) {
  if (ubTimer) clearInterval(ubTimer);
  ubTimer = setInterval(() => {
    try {
      const d = JSON.parse(AndroidApp.updateProgress());
      if (d.state === "running") {
        setDlStateText(prefix, "running", d.percent, d.bytes, d.total, d.sourceHost || "");
        if (d.bytes !== dl.lastBytes) { dl.lastBytes = d.bytes; dl.lastMoveAt = Date.now(); }
        else if (Date.now() - dl.lastMoveAt > 30000) {
          if (switchChannel("下载长时间无进展")) return;
          dl.lastMoveAt = Date.now();
        }
      } else if (d.state === "connecting") {
        setDlStateText(prefix, "connecting", 0, d.bytes, d.total, d.sourceHost || "");
      } else if (d.state === "retrying") {
        setDlStateText(prefix, "retrying", d.percent, d.bytes, d.total, d.sourceHost || "");
        if (d.bytes !== dl.lastBytes) { dl.lastBytes = d.bytes; dl.lastMoveAt = Date.now(); }
        else if (Date.now() - dl.lastMoveAt > 60000) {
          if (switchChannel("下载长时间无进展")) return;
          dl.lastMoveAt = Date.now();
        }
      } else if (d.state === "done") {
        clearInterval(ubTimer); ubTimer = null;
        const bar = $("#" + prefix + "-bar");
        if (bar) bar.style.width = "100%";
        setDlStateText(prefix, "verify-ok", 100, d.bytes, d.total, d.sourceHost || "");
        const btn = $("#" + prefix + "-dl-btn");
        if (btn) { btn.textContent = "立即安装"; btn.disabled = false; btn.dataset.phase = "install"; }
      } else if (d.state === "failed") {
        clearInterval(ubTimer); ubTimer = null;
        if (switchChannel("下载失败")) return;
        onAllChannelsFailed(prefix, "网络异常或下载中断");
      }
    } catch (e) { clearInterval(ubTimer); ubTimer = null; }
  }, 800);
}

/* 安装（校验状态显示——installUpdate 桥内部完成 SHA-256/大小校验后才拉起
 * 系统安装器；校验失败自动删除文件并返回错误，绝不安装不完整/被篡改 APK） */
function doInstallUpdate(prefix) {
  setDlStateText(prefix, "verifying", 100, 0, 0, "");
  setTimeout(() => {
    try {
      const r = JSON.parse(AndroidApp.installUpdate());
      if (r && !r.ok) {
        setDlStateText(prefix, "failed", 0, 0, 0, "");
        toast(r.error || "拉起安装失败");
        const btn = $("#" + prefix + "-dl-btn");
        if (btn && r.needPermission) { btn.textContent = "已去授权，返回后重试"; btn.disabled = false; btn.dataset.phase = "install"; }
      }
    } catch (e) {
      setDlStateText(prefix, "failed", 0, 0, 0, "");
      toast("拉起安装失败");
    }
  }, 60);
}

/* v7.0.0：顶部更新横幅已整体移除（用户实锤：每次检查更新出现两个提示——
 * 只留手动「检查更新」路径，下载也只能从它发起）。ub-* 事件链随之删除。 */

$("#btn-check-update").addEventListener("click", async () => {
  const btn = $("#btn-check-update");
  const dlBtn = $("#page-dl-btn");
  const note = $("#page-dl-note");
  const notesBox = $("#page-notes");
  btn.disabled = true;
  dlBtn.style.display = "none";
  note.style.display = "none";
  if (notesBox) { notesBox.style.display = "none"; notesBox.innerHTML = ""; }
  btn.textContent = "检查中…";
  /* v7.9.8：手动检查（manual=true）——绕过新鲜缓存强制刷新，但受 30s 冷却/
   * 单飞合并约束（原生侧保证——绝不重复请求风暴）。 */
  const d = await fetchLatest(true);
  btn.disabled = false;
  btn.textContent = "检查更新";
  /* v7.9.8 错误如实转达：十类错误码 → 人话（不再一律「稍后再试」；GitHub
   * API 限流类错误已随架构改造根除——静态清单无 REST 配额）。 */
  if (!d.ok) {
    note.className = "fb-result err";
    note.style.display = "";
    note.textContent = "查询失败：" + (updateErrText(d.errorCode) || d.error || "未知");
    /* ═══ v7.11.0 检查失败兜底：直达 jsDelivr CDN 仓库内最新 APK（
     * adshield-latest.apk 恒指向最新版）——下载后覆盖安装即升级。
     * 检查失败不再无路可走；直连下载走系统浏览器/下载器通道。 */
    latestRelease = null;
    fallbackDirectDl = true;
    dlBtn.textContent = "直连下载最新版（CDN 兜底）";
    dlBtn.dataset.phase = "fallback";
    dlBtn.disabled = false;
    dlBtn.style.display = "";
    return;
  }
  fallbackDirectDl = false;
  if (!d.hasRelease) { note.className = "fb-result"; note.style.display = ""; note.textContent = "尚未发布版本"; return; }
  latestRelease = d;
  const latest = String(d.tag || "").replace(/^v/, "");
  /* v7.9.8 数据来源透明化：网络异常回退缓存 → 标注数据更新时间（用户知情） */
  const cacheNote = d.staleCache
    ? "（网络异常，以下为缓存数据 · 更新于 " + (ageText(d.cachedAt) || "未知时间") + "）"
    : (d.fromCache ? "（数据更新于 " + (ageText(d.cachedAt) || "刚刚") + "）" : "");
  /* v7.10.0 统一版本规则：优先用原生侧权威判定（Net.isNewerRelease——
   * remoteVersionCode > currentVersionCode；versionName 仅展示），
   * 浏览器路径回退 isNewerByCode（Java 镜像——同为 versionCode 单一判定） */
  const isNewer = typeof d.isNewer === "boolean" ? d.isNewer
    : isNewerByCode(d.remoteVersionCode || 0, parseInt(appCode(), 10) || 0);
  /* v7.0.0：检查更新当场显示新版本 ≤5 条带序号功能描述（就地渲染——
   * 就在点按钮的地方；不只弹版本号） */
  if (isNewer) renderReleaseNotes(d);
  if (d.apkUrl) {
    dlBtn.textContent = isNewer ? "下载 v" + latest : "重新下载 APK";
    dlBtn.dataset.phase = "download";
    dlBtn.disabled = false;
    dlBtn.style.display = "";
    note.className = "fb-result";
    note.style.display = "";
    note.textContent = (isNewer
      ? "发现新版本 v" + latest + "（versionCode " + (d.remoteVersionCode || "?") + " · 功能描述如上，下载后覆盖安装即升级）"
      : "已是最新版本 · " + CURRENT_RELEASE.title) + cacheNote;
  } else if (cacheNote) {
    note.className = "fb-result";
    note.style.display = "";
    note.textContent = cacheNote.replace(/[（）]/g, "");
  }
});

$("#page-dl-btn").addEventListener("click", () => {
  const btn = $("#page-dl-btn");
  if (btn.dataset.phase === "fallback") { doFallbackDirectDownload(); return; }
  if (btn.dataset.phase === "install") {
    if (HAS_BRIDGE) doInstallUpdate("page");
    return;
  }
  if (latestRelease) startPageDownload(latestRelease, "page");
});

/* ══════════ v7.9.6plus 启动时新版本检测 + 全屏更新页 ══════════
 * 用户每次进入 App：首屏渲染后异步检查 → versionCode 主判（远端 > 本地）
 * → 优先显示全屏更新提示页；无新版本/网络异常/清单暂不可用 → 静默正常
 * 进入 App（绝不因更新检查失败阻塞启动）。v7.9.8 起改读静态清单
 * （fetchLatest(false) 自动路径：优先 15min 新鲜缓存零联网；本进程至多
 * 联网一次；失败指数退避；网络异常回退缓存——versionCode 单调性保证
 * 缓存数据绝不提示降级）。用户选「暂不更新」→ 继续使用当前版本，下次
 * 进入 App 仍会检测。Draft/Pre-release 清单标记不作正式更新。 */
let gateRelease = null;
/* v7.11.0：检查失败兜底态（true = 下载按钮走 jsDelivr CDN 直链而非
 * Release 下载状态机）。 */
let fallbackDirectDl = false;

/* 检查失败兜底直连下载：jsDelivr CDN 仓库内最新 APK（浏览器/系统下载器
 * 通道——无 digest 元数据，不走应用内 SHA-256 安装闸；下载后从通知/文件
 * 管理器点击安装，覆盖安装即升级）。 */
function doFallbackDirectDownload() {
  toast("已转系统下载：下载完成后点击安装（覆盖安装即升级）");
  openUrl(FALLBACK_CDN_LATEST);
}

async function checkUpdateOnLaunch() {
  try {
    const d = await fetchLatest(false);
    if (!d || !d.ok || !d.hasRelease) return; // 检查失败静默——App 正常可用
    const isNewer = typeof d.isNewer === "boolean" ? d.isNewer
      : isNewerByCode(d.remoteVersionCode || 0, parseInt(appCode(), 10) || 0);
    if (isNewer) showUpdateGate(d);
  } catch (e) { /* 启动检查绝不阻塞 App */ }
}

/* 全屏更新页渲染（fixed 覆盖全屏 · z-index 最高 · 不影响原页面布局——
 * 关闭即回到原状；移动端优先适配无横向溢出） */
function showUpdateGate(d) {
  gateRelease = d;
  /* v7.11.0 更新优先于提醒：全屏更新页即将在场——先收起已展示的开屏
   * 提醒卡（两卡叠弹打扰面；用户确认过的 24h 节流时间戳不受影响） */
  const tip = $("#pin-tip");
  if (tip) tip.remove();
  let gate = $("#update-gate");
  if (gate) gate.remove();
  gate = document.createElement("div");
  gate.id = "update-gate";
  const latest = String(d.tag || "").replace(/^v/, "");
  const heads = notesToHeadlines(d, 5);
  const sizeLine = d.apkMB ? "安装包 " + d.apkMB + " MB" : "";
  const digestLine = d.apkDigest ? " · SHA-256 已公布" : "";
  gate.innerHTML =
    '<div class="ug-card" role="dialog" aria-modal="true" aria-label="发现新版本">'
    + '<img class="ug-logo" src="mascot-head.png" width="64" height="64" alt="开屏卫士图标">'
    + '<div class="ug-app-name">开屏卫士</div>'
    + '<div class="ug-badge">发现新版本</div>'
    + '<div class="ug-versions">当前 v' + esc(appVer()) + ' → 最新 <b>v' + esc(latest) + '</b>'
    + (d.remoteVersionCode ? '（versionCode ' + d.remoteVersionCode + '）' : '') + '</div>'
    + (heads.length
        ? '<div class="ug-notes">' + heads.map((h, i) =>
            '<div class="ug-note-item"><span class="ug-note-n">' + (i + 1) + '</span>' + esc(h) + '</div>').join("") + '</div>'
        : '')
    + (sizeLine ? '<div class="ug-size">' + sizeLine + digestLine + ' · 官方源 GitHub Releases</div>' : '')
    + '<div class="ug-progress-wrap" id="gate-progress-wrap" style="display:none">'
    + '<div class="progress"><div class="progress-bar" id="gate-bar"></div></div>'
    + '<div class="progress-text" id="gate-progress-text">准备中</div></div>'
    + '<button class="ug-btn-primary" id="gate-dl-btn">立即更新</button>'
    + '<button class="ug-btn-ghost" id="gate-later-btn">暂不更新</button>'
    + '<div class="fb-result" id="gate-dl-note" style="display:none"></div>'
    + '</div>';
  document.body.appendChild(gate);
  const dlBtn = $("#gate-dl-btn");
  dlBtn.dataset.phase = "download";
  dlBtn.addEventListener("click", () => {
    const b = $("#gate-dl-btn");
    if (b.dataset.phase === "install") { if (HAS_BRIDGE) doInstallUpdate("gate"); return; }
    if (gateRelease) startPageDownload(gateRelease, "gate");
  });
  $("#gate-later-btn").addEventListener("click", hideUpdateGate);
  /* 恢复未完成的下载任务（用户上次退出时下载中/已完成未安装——系统
   * DownloadManager 任务持久，进度查询天然恢复；已完成直接给安装入口） */
  if (HAS_BRIDGE) {
    try {
      const p = JSON.parse(AndroidApp.updateProgress());
      if (p.state === "done") {
        dlBtn.textContent = "立即安装"; dlBtn.dataset.phase = "install";
        $("#gate-progress-wrap").style.display = "";
        setDlStateText("gate", "verify-ok", 100, p.bytes || 0, p.total || 0, p.sourceHost || "");
      } else if (p.state === "running" || p.state === "connecting" || p.state === "retrying") {
        dlBtn.textContent = "下载中…"; dlBtn.disabled = true;
        $("#gate-progress-wrap").style.display = "";
        dl = { channels: [gateRelease.apkUrl].concat(gateRelease.apkMirrors || []), idx: 0,
          fileName: gateRelease.apkFileName || "adshield.apk", digest: gateRelease.apkDigest || "",
          size: gateRelease.apkSize || 0, prefix: "gate", lastBytes: -1, lastMoveAt: Date.now() };
        startProgressPoll("gate");
      }
    } catch (e) {}
  }
}

function hideUpdateGate() {
  const gate = $("#update-gate");
  if (gate) gate.remove();
}

/* ══════════ v7.12.0 开屏提醒：三按钮弹窗（知道了 / 24h 不再提示 / 去上传日志）══════════
 * 用户指令：「开屏提示内容（24 小时可以不显示）不要放在帮助里，每次进入 app
 * 时弹出提示，有三个按钮，知道了，24小时不再提示，去上传日志」。
 *  · 进入 App 即显示（init 直调——0ms；内容与帮助页彻底分离，帮助页不再
 *    重复开屏提示文案）；
 *  · 「知道了」= 仅本轮收起（不记 24h——下次进入仍会提醒）；
 *  · 「24 小时内不再提示」= 记 pin_tip_confirmed_at（epoch 毫秒，kvSet
 *    白名单键 / 浏览器 localStorage 兜底），24h 内不再显示；
 *  · 「去上传日志」= 收起提醒 → 直达「我的 → 引擎日志」卡（滚动 + 高亮）
 *    ——绝不自动上传（隐私红线：仅在你主动点「一键上传」时才上传）；
 *  · 每次进入语义：v7.11.0 的 5min 防重建窗会把「退出后 1~5 分钟内再进入」
 *    也吞掉——v7.12.0 收紧为 60s 短窗（仅防 Activity 重建/旋转连弹），
 *    真实的「再次进入」一律照常提醒；
 *  · 切回前台也评估（v7.13.0：「进入就提醒」完整语义——冷启动与切回
 *    双场景；60s 短窗 + 24h 节流双闸防连弹/防骚扰）；
 *  · 更新提示优先：#update-gate 在场不弹；更新页后到 → showUpdateGate
 *    主动收起提醒卡（24h 时间戳不受影响）。 */
const PIN_TIP_THROTTLE_MS = 24 * 60 * 60 * 1000;  /* 「24h 不再提示」节流 */
const PIN_TIP_RESHOW_GUARD_MS = 60 * 1000;       /* 展示后 60s 防旋转/重建/切回连弹 */
function pinTipThrottled() {
  try {
    const t = parseInt(store.get("pin_tip_confirmed_at", "0"), 10) || 0;
    if (!(t > 0) || (Date.now() - t) >= PIN_TIP_THROTTLE_MS) return false;
    /* v7.13.0 跨升级残留根治（用户实锤「进入 app 没有弹出提醒」根因）：
     * 24h 节流时间戳可能是【旧版本】点「不再提示」记下的——覆盖升级
     * 到新版本后，旧时间戳继续吞掉升级后的首次提醒（prefs 跨升级保留）。
     * 提醒卡记 pin_tip_ver（点按钮时的版本）；版本已变 → 节流视为过期，
     * 升级后第一次进入必弹（新版本的「进入就提醒」语义重新生效）。 */
    const ver = String(store.get("pin_tip_ver", "") || "");
    if (ver && ver !== appVer()) return false;
    return true;
  } catch (e) { return true; }
}
function pinTipRecentlyShown() {
  try {
    const t = parseInt(store.get("pin_tip_shown_at", "0"), 10) || 0;
    return t > 0 && (Date.now() - t) < PIN_TIP_RESHOW_GUARD_MS;
  } catch (e) { return true; }
}
function showPinTip() {
  /* v7.13.0：移除 pinTipShownThisSession 会话闸（旧实现挡住「切回前台
   * 再弹」——会话变量跨切回保持 true，切回场景永远进不来）。防连弹统一
   * 收口到 60s 持久短窗（旋转/重建/切回同防，60s 后真实进入照常弹）。 */
  try { store.set("pin_tip_shown_at", String(Date.now())); } catch (e) {}
  let tip = $("#pin-tip");
  if (tip) tip.remove();
  tip = document.createElement("div");
  tip.id = "pin-tip";
  tip.innerHTML =
    '<div class="ug-card" role="dialog" aria-modal="true" aria-label="固定后台提醒">'
    + '<img class="ug-logo" src="mascot-head.png" width="56" height="56" alt="开屏卫士图标">'
    + '<div class="ug-app-name">把开屏卫士固定在后台</div>'
    + '<div class="ug-badge">守护不中断</div>'
    + '<div class="ug-notes">'
    + '<div class="ug-note-item"><span class="ug-note-n">1</span>打开「最近任务」，找到卫士卡片</div>'
    + '<div class="ug-note-item"><span class="ug-note-n">2</span>点卡片上的「固定 / 图钉 / 锁定」（MIUI：长按卡片选「锁定」）</div>'
    + '<div class="ug-note-item"><span class="ug-note-n">3</span>固定后清理后台不影响卫士——下次进入无需重新开启</div>'
    + '</div>'
    + '<div class="ug-size">不固定的后果：清理后台时卫士被一起杀掉，下次进入会被要求重新开启（并再看一次系统 10 秒确认）。'
    + '若遇开屏广告未跳过等问题，点下方「去上传日志」可帮助定位。</div>'
    + '<button class="ug-btn-primary" id="pin-tip-ok">知道了</button>'
    + '<button class="ug-btn-ghost" id="pin-tip-snooze">24 小时内不再提示</button>'
    + '<button class="ug-btn-link" id="pin-tip-upload">去上传日志</button>'
    + '</div>';
  document.body.appendChild(tip);
  const close = () => { const t = $("#pin-tip"); if (t) t.remove(); };
  /* 「知道了」：仅本轮收起（下次进入仍提醒——进入 app 就显示语义保持） */
  $("#pin-tip-ok").addEventListener("click", close);
  /* 「24 小时内不再提示」：记确认时间戳（24h 节流主闸）+ 当前版本
   * （v7.13.0：版本变化时节流失效——升级后重新提醒，见 pinTipThrottled） */
  $("#pin-tip-snooze").addEventListener("click", () => {
    try {
      store.set("pin_tip_confirmed_at", String(Date.now()));
      store.set("pin_tip_ver", appVer());
    } catch (e) {}
    close();
    toast("24 小时内不再提示（固定后台后守护更稳）");
  });
  /* 「去上传日志」：收起提醒 → 直达引擎日志卡（跳转不等于上传——上传
   * 永远只在你主动点「一键上传」时发生） */
  $("#pin-tip-upload").addEventListener("click", goUploadLogFromTip);
}
/* v7.12.0 「去上传日志」跳转：切「我的」页 → 滚动到引擎日志卡并高亮 →
 * 指引点「一键上传」（隐私红线保持：零自动上传）。 */
function goUploadLogFromTip() {
  const close = () => { const t = $("#pin-tip"); if (t) t.remove(); };
  close();
  try {
    const meBtn = document.querySelector('.tabbtn[data-page="me"]');
    if (meBtn && meBtn.click) meBtn.click();
    setTimeout(() => {
      try {
        const card = document.getElementById("card-engine-log");
        if (card && card.scrollIntoView) card.scrollIntoView({ behavior: "smooth", block: "center" });
        if (card && card.classList) {
          card.classList.remove("flash");
          void card.offsetWidth;
          card.classList.add("flash");
        }
      } catch (e) {}
    }, 160);
    toast("在「引擎日志」点「一键上传」——仅在你主动点击时才上传");
  } catch (e) {}
}
function maybeShowPinTip() {
  try {
    if (pinTipThrottled()) return;         // 24h 内已选「不再提示」——节流
    if (pinTipRecentlyShown()) return;     // 60s 内已展示过——防旋转/重建重复弹
    if ($("#update-gate")) return;         // 更新页在场——升级优先，本轮不打扰
    showPinTip();
  } catch (e) { /* 提示失败静默——绝不影响正常使用 */ }
}

/* ═══ v4.5.0 包名缩写（修「android」歧义；v7.9.8：报告页在用——保留）═══
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

/* ══════════ 我的：日志 ══════════ */
function renderLog(force) {
  const st = getRealState();
  const log = (st.log || []).slice().reverse();
  $("#real-log").innerHTML = log.length
    ? log.map(l => "<div>" + esc(l.msg || "") + "</div>").join("")
    : '<div class="empty">暂无日志</div>';
}
/* v5.8.0：日志刷新按钮（用户实锤：日志上传按钮旁边加一个刷新）——
 * 重读服务内存快照（kvGet("state") 走 snapshotState 实时态） */
$("#btn-refresh-log").addEventListener("click", () => {
  renderLog(true);
  toast("日志已刷新");
});

/* ══════════ v7.9.8 日志上传（导出功能已下线——用户指令）═════════
 * v7.9.7 曾上线的本地导出（导出按钮/收集→脱敏→写文件/分享面板/确认弹窗）
 * 整链下线；保留：日志采集（服务侧常开）/ 查看能力（renderLog+刷新按钮）/
 * 一键上传（doUploadLog）+ 自动上传规则（24h 最多成功 3 次·只记自动账本）
 * 全部零改动。上传错误按真实分类码（errKind）八类人话呈现——绝不把所有
 * 失败统一成「稍后再试」。 */

/* 上传阶段化 loading：按钮转圈 + 阶段文案 */
function setUploadPhase(btn, phase) {
  const SPIN = '<span style="display:inline-block;width:12px;height:12px;border:2px solid rgba(255,255,255,.35);border-top-color:#fff;border-radius:50%;margin-right:6px;vertical-align:-1px;animation:adspin .8s linear infinite"></span>';
  btn.innerHTML = SPIN + esc(phase);
  btn.disabled = true;
}

/* v7.9.8 错误分类码 → 人话提示（用户八类表；基于 LogUploader.classifyError
 * 的真实 HTTP 状态码/服务端码/网络异常类型——绝不猜测原因）。
 * v7.9.8b：裸 401（AUTH_FAILED·认证失败）与裸 403（FORBIDDEN·权限不足）
 * 分家提示——服务端 JSON 码 UNAUTHORIZED 仍按契约透出原码。
 * v7.9.9：超时阶段分家——连接超时（NET_CONNECT_TIMEOUT·请求几乎必然
 * 未送达，可换网络）与读取超时（NET_READ_TIMEOUT·请求可能已送达甚至
 * 已处理——幂等重试语义如实告知）；TLS 干扰独立分类（NET_TLS）。 */
function uploadErrText(res) {
  const k = String((res && res.errKind) || "");
  const raw = String((res && res.lastError) || "");
  /* v7.13.0：连接阶段失败文案注明「已自动多次重试 + 网络恢复自动补试」——
   * Java 侧 RealHttp 连接阶段（连接失败/连接超时/TLS）已自动四试
   * （0/500/1200/2400ms 退避 + 单次 6s 超时快失败）；切网/网络恢复时
   * AdSkipService NetworkCallback 自动补传待传日志（用户无需手动重试）。 */
  if (k === "NET_CONNECT_FAIL") return "网络连接失败，请检查网络——已自动多次重试仍不通；日志已保留（不会丢失）。切换 Wi-Fi/移动网络后无需手动重试：网络恢复时 App 会自动补试上传";
  if (k === "NET_CONNECT_TIMEOUT") return "连接上传服务超时——当前网络可能无法访问上传服务；已自动多次重试仍不通，日志已保留（不会丢失）。切换 Wi-Fi/移动网络后无需手动重试：网络恢复时 App 会自动补试上传";
  if (k === "NET_READ_TIMEOUT") return "上传超时，结果暂时无法确认——若服务端已收到，重试将使用原批次，不会重复创建 Issue";
  if (k === "NET_TLS") return "安全连接失败（网络传输可能被干扰）——可尝试切换网络后重试；日志已保留（不会丢失）";
  if (k === "NET_TIMEOUT") return "上传超时，请稍后重试——若服务端已收到，重试会自动复用原 Issue，不会重复提交";
  if (k === "RATE_LIMITED") return "服务端繁忙，请稍后重试——日志已保留（不会丢失），可立即再次上传";
  if (k === "INVALID_REQUEST" || k === "HTTP_400") return "上传数据格式异常（" + (raw || "400") + "）——该情况请反馈给开发者";
  if (k === "AUTH_FAILED") return "认证失败：服务端未接受本次上传（401），请稍后重试——日志已保留";
  if (k === "FORBIDDEN") return "权限不足：服务端拒绝了本次上传（403），请稍后重试——日志已保留";
  if (k === "UNAUTHORIZED") return "服务端拒绝了本次上传（UNAUTHORIZED），请查看诊断信息";
  if (k === "PRIVACY_REJECTED" || k === "IDEMPOTENCY_CONFLICT" || k === "PAYLOAD_TOO_LARGE"
      || k === "MANUAL_RECONCILIATION_REQUIRED") {
    return "服务端拒绝了本次上传（" + k + "），请查看诊断信息——该批已隔离保留本地检视";
  }
  if (k === "RELAY_SERVER_ERROR" || k === "RELAY_TEMP_ERROR") return "日志接收服务暂时不可用，请稍后重试——日志已保留";
  if (k === "PROTOCOL_UNEXPECTED_BODY") return "上传服务响应异常，请稍后重试";
  if (k === "NET_IO") return "网络传输异常，请稍后重试——日志已保留";
  return "上传失败，请查看错误详情（" + (raw || k || "未知") + "）——日志已保留，可随时再次上传";
}

/* 失败提示（含重试按钮；文案已人话化） */
function showUploadFail(box, text, retryFn) {
  box.className = "fb-result err";
  box.innerHTML = esc(text)
    + ' <button id="btn-upload-retry" style="border:0;border-radius:999px;background:#fff;color:#b91c1c;'
    + 'font-size:12px;font-weight:700;padding:4px 12px;margin-left:6px">重试</button>';
  const rb = $("#btn-upload-retry");
  if (rb) rb.addEventListener("click", () => (retryFn || doUploadLog)());
}

/* ══════ 一键上传主流程（v8.2.2 简化版 · v7.9.8 冻结幂等）══════
 * 用户点一次 → LogUploader.submitNow（分块 ≤48 逐块上传 Worker）→ 回调：
 * 成功显示 Issue 编号与【经 GitHub 匿名 API 核实的真实链接】；失败显示
 * 原因并保留可重试。v7.10.0：上传次数限制彻底移除——手动上传零次数/
 * 额度/冷却门槛，随时可传、短时间连传多批全部放行（同 batchId 幂等保持）；
 * 服务端瞬时限流如实告知（无等待分钟数呈现）。
 * 空队列四态如实区分（未产生/已传完/采集异常/读取异常）。
 * 双入口：「我的」页主按钮 / 设置页「立即提交」——结果统一渲染。 */
function doUploadLog(fromSettings) {
  const box = $("#upload-log-result");
  const btn = fromSettings ? $("#btn-log-upload-now") : $("#btn-upload-log");
  if (!btn) return;
  if (!HAS_BRIDGE || !AndroidApp.logUploadSubmitNow) {
    toast("仅 APK 内可上传（浏览器预览无上传通道）");
    return;
  }
  if (!fromSettings) {
    box.className = "fb-result";
    box.textContent = "";
  }
  setUploadPhase(btn, "上传中…");
  try {
    AndroidApp.logUploadSubmitNow("__logUploadCb");
  } catch (e) {
    resetUploadButtons();
    showUploadFail(box, "上传调用失败", doUploadLog);
  }
}

/* v8.1.0 复位双入口按钮（回调时两个都复位——触发者不确定） */
function resetUploadButtons() {
  const main = $("#btn-upload-log");
  if (main) { main.innerHTML = "一键上传"; main.disabled = false; }
  const now = $("#btn-log-upload-now");
  if (now) { now.innerHTML = "立即提交"; now.disabled = false; }
}

/* v8.2.2 一键上传结果回调（MainActivity evaluateJavascript 回调）——
 * 成功显示 Issue 编号与【经验证的真实链接】（服务端返回 issueNumber 后经
 * GitHub 匿名 API 核实 html_url 才可点击——绝不拼接未经确认的仓库地址）；
 * 无日志时四态如实区分；v7.9.8 失败按 errKind 八类人话呈现（分类依据
 * 真实 HTTP 状态码/服务端码/网络异常类型——绝不统一「稍后再试」）；
 * v7.10.0：次数限制彻底移除——不再展示 24h 成功批数额度行与等待分钟数。 */
window.__logUploadCb = function (res) {
  resetUploadButtons();
  const box = $("#upload-log-result");
  res = res || {};
  if (res.busy) {
    toast("后台正在上传中，请稍候再试");
    showUploadFail(box, "后台正在上传中，请稍候再试（上传中重复点击不会重复提交）", doUploadLog);
    return;
  }
  if ((res.acked || 0) > 0) {
    const nums = String(res.issueNumbers || "").split(",");
    const urls = String(res.issueUrls || "").split(",");
    let anyUrl = false;
    const links = nums.map((n, i) => {
      const num = parseInt(n, 10);
      if (!(num > 0)) return null;
      const u = (urls[i] || "").trim();
      if (u) {
        anyUrl = true;
        return '<a data-issue-url="' + esc(u) + '" href="javascript:void(0)" style="color:#047857;font-weight:700;text-decoration:underline">#' + num + "</a>";
      }
      return "<b>#" + num + "</b>";
    }).filter(Boolean).join("、");
    box.className = "fb-result ok";
    box.innerHTML = "日志上传成功" + (links ? "（Issue " + links + (anyUrl ? "，点击可查看）" : "——编号由服务端确认）") : "");
    const anchors = box.querySelectorAll("a[data-issue-url]");
    for (const a of anchors) {
      a.addEventListener("click", () => openUrl(a.getAttribute("data-issue-url")));
    }
    toast("日志上传成功");
  } else if ((res.waited || 0) > 0) {
    /* v7.10.0：上传次数限制已移除——本分支仅服务服务端瞬时限流
     * （per-minute 防滥用层）；不再呈现「等待 N 分钟」类人为时长，
     * 日志保留、可立即重试。 */
    toast("服务端繁忙，请稍后重试");
    showUploadFail(box, "服务端繁忙，请稍后重试——日志已保留（不会丢失），可立即再次上传", doUploadLog);
  } else if ((res.rejected || 0) > 0) {
    toast("服务端拒收本批日志");
    showUploadFail(box, "服务端拒绝了本次上传（" + (res.lastError || "未知原因")
      + "），请查看诊断信息——该批已隔离保留本地检视，不影响后续新日志上传", doUploadLog);
  } else if (res.lastError) {
    toast("日志上传失败");
    showUploadFail(box, uploadErrText(res), doUploadLog);
  } else if (res.emptyReason === "never-collected") {
    box.className = "fb-result";
    box.textContent = "暂无可上传的新日志——应用运行与跳过广告时会自动记录，产生后即可上传";
    toast("暂无已产生的日志");
  } else if (res.emptyReason === "all-uploaded") {
    box.className = "fb-result ok";
    box.textContent = "已产生的日志均已全部上传——新日志产生后可再次上传";
    toast("所有日志均已上传");
  } else if (res.emptyReason === "append-failed") {
    box.className = "fb-result";
    box.textContent = "日志采集写入异常（本地存储故障）——请重启应用后重试";
    toast("日志采集异常");
  } else if (res.emptyReason === "read-failed") {
    box.className = "fb-result";
    box.textContent = "本地日志文件暂时无法读取——请重启应用后重试";
    toast("日志读取失败");
  } else if (res.emptyReason === "rejected-only") {
    box.className = "fb-result";
    box.textContent = "已采集日志均被服务端拒收（已保留本地检视）——新日志产生后可再次上传";
    toast("日志被服务端拒收");
  } else if (res.emptyReason === "cleared") {
    box.className = "fb-result";
    box.textContent = "本地待上传日志已被清理——新日志产生后可再次上传";
    toast("暂无可上传日志");
  } else if (res.emptyReason === "relay-offline") {
    box.className = "fb-result";
    box.textContent = "上传服务未配置（本版本不可用）";
    toast("上传服务未配置");
  } else {
    toast("日志上传失败");
    showUploadFail(box, uploadErrText(res), doUploadLog);
  }
  refreshLogUploadNote();
};

$("#btn-upload-log").addEventListener("click", () => { void doUploadLog(); });
/* v7.9.8：导出按钮 #btn-export-log 已随导出功能下线（index.html 同步移除） */

/* ══════════ v7.1.0 卫士使用报告 · v7.2.0 饼图常驻 + 明细折叠 ══════════
 * （用户实锤：报告生成后太长看着难受——顶部拦截分布饼图常驻 + 五段明细
 *  默认收起，点标题行展开/收起；进入「我的」页时自动静默刷新）
 * 数据全部来自本机（状态快照/订阅状态/能耗画像/设备信息），零上传零联网：
 *  · 饼图：纯 CSS conic-gradient 甜甜圈（绿色系五档，取自 style.css 既有
 *    色板），五类计数：开屏秒跳 / 信息流关闭 / 弹窗关闭 / 返回键 / 反馈面板
 *    收起；全 0 时灰色整环 +「暂无拦截记录」；旁边图例（色块 + 名称 + 次数）
 *  · 折叠明细：拦截战绩（累计）/ 订阅两源命中 / 应用战绩 Top5 / 能耗画像 /
 *    设备画像（行格式沿用 reportLine；数据源桥调用原样保留，能耗/Top5
 *    仍按有无数据出现）
 *  · feed（信息流关闭）单独记账，stats 缺字段时按 0 兜底
 * 浏览器预览（无桥）渲染演示数据。 */
function reportLine(k, v) {
  return '<div class="rp-line">' + esc(k) + "：" + esc(v) + "</div>";
}

/* v7.2.0 折叠段：<div class="rp-fold"><div class="rp-fold-hd">标题 + 箭头</div>
 * <div class="rp-fold-bd">行…</div></div>——默认收起，点标题行切换 .open */
function reportFold(title, bodyLines) {
  return '<div class="rp-fold">'
    + '<div class="rp-fold-hd"><span class="rp-fold-tt">' + esc(title) + '</span>'
    + '<span class="rp-fold-arrow"></span></div>'
    + '<div class="rp-fold-bd">' + bodyLines.join("") + '</div>'
    + '</div>';
}

/* v7.2.0 饼图配色：只用绿色系五档（深绿/中绿/翠绿/浅绿/灰绿，均取自
 * style.css 既有色板 --brand-deep/--brand/#10B981/#34D399/#C8DCCF），
 * 顺序与 segs 一一对应；空态（全 0）用中性灰 #CBD5D1 整环。 */
const RP_PIE_COLORS = ["#047857", "#059669", "#10B981", "#34D399", "#C8DCCF"];

/* refresh：v7.2.0 刷新语义——进入我的页/点「刷新报告」均传 true；
 * 函数内零 toast（提示一律由调用方决定），每次全量重渲染（折叠态回默认收起）。 */
function renderReport(refresh) {
  const box = $("#report-body");
  if (!box) return;
  let st = { stats: {} }, sub = { builtinApps: 0, builtinRules: 0, remoteHits: 0, builtinHits: 0 };
  let en = null, dev = {};
  if (HAS_BRIDGE) {
    try { st = getRealState(); } catch (e) {}
    try { sub = JSON.parse(AndroidApp.subState()); } catch (e) {}
    try { en = JSON.parse(AndroidApp.energyStats()); } catch (e) {}
    try { dev = JSON.parse(AndroidApp.deviceInfo()); } catch (e) {}
  } else {
    // 浏览器演示数据（无桥环境——面板与真实 APK 内数据结构完全一致；
    // v7.2.0：stats 加 feed: 23，byApp 加一条带 feed 的应用（信息流单独记账））
    st = { stats: { auto: 128, feed: 23, popup: 36, back: 9, feedback: 14, manual: 21, byApp: {
      "com.ss.android.ugc.aweme": { auto: 41, popup: 12 },
      "com.xingin.xhs": { auto: 27 },
      "tv.danmaku.bili": { auto: 19, popup: 8 },
      "com.tencent.qqmusic": { auto: 15 },
      "com.zhihu.android": { auto: 11 },
      "com.tencent.mm": { feed: 12 },
    } } };
    sub = { builtinApps: 21, builtinRules: 21, remoteOk: true, remoteName: "id667 的 GKD 订阅",
      remoteApps: 610, remoteRules: 828, remoteHits: 47, builtinHits: 12 };
    en = { ok: true, events: 312, probes: 88, scans: 41, microWh: 62 };
    dev = { brand: "Xiaomi", model: "小米 17 Pro", os: "HyperOS 3", sdk: 35 };
  }
  const s = st.stats || {};
  // 饼图数据：五类拦截计数（feed 缺字段 → 0 兜底）
  const segs = [
    { k: "开屏秒跳", n: s.auto || 0 },
    { k: "信息流关闭", n: s.feed || 0 },
    { k: "弹窗关闭", n: s.popup || 0 },
    { k: "返回键温和关闭", n: s.back || 0 },
    { k: "反馈面板收起", n: s.feedback || 0 },
  ];
  const total = segs.reduce((a, x) => a + x.n, 0);
  let pieStyle = "background:#CBD5D1", pieNum = "0", pieLbl = "暂无拦截记录"; // 全 0：灰色整环
  if (total > 0) {
    // 各段角度 = count/total*360（JS 里算好角度串拼进 style，纯字符串拼接）
    const stops = [];
    let acc = 0;
    for (let i = 0; i < segs.length; i++) {
      if (!segs[i].n) continue;
      const from = acc / total * 360, to = (acc + segs[i].n) / total * 360;
      stops.push(RP_PIE_COLORS[i] + " " + from.toFixed(2) + "deg " + to.toFixed(2) + "deg");
      acc += segs[i].n;
    }
    pieStyle = "background:conic-gradient(" + stops.join(",") + ")";
    pieNum = String(total);
    pieLbl = "累计拦截";
  }
  const legend = segs.map((x, i) =>
    '<div class="rp-legend-row"><span class="rp-legend-dot" style="background:' + RP_PIE_COLORS[i] + '"></span>'
    + '<span class="rp-legend-name">' + esc(x.k) + '</span>'
    + '<span class="rp-legend-val">' + x.n + ' 次</span></div>').join("");
  const lines = [];
  // 饼图常驻（进入页面即渲染显示，不再是生成后才显示）
  lines.push('<div class="rp-pie-wrap">'
    + '<div class="rp-pie" style="' + pieStyle + '"><div class="rp-pie-hole">'
    + '<div class="rp-pie-num">' + pieNum + '</div><div class="rp-pie-lbl">' + esc(pieLbl) + '</div></div></div>'
    + '<div class="rp-legend">' + legend + '</div>'
    + '</div>');
  // ① 拦截战绩（累计）
  const secStats = [];
  secStats.push(reportLine("开屏秒跳", (s.auto || 0) + " 次"));
  secStats.push(reportLine("信息流关闭", (s.feed || 0) + " 次"));
  secStats.push(reportLine("弹窗关闭", (s.popup || 0) + " 次"));
  secStats.push(reportLine("返回键温和关闭", (s.back || 0) + " 次"));
  secStats.push(reportLine("反馈面板收起", (s.feedback || 0) + " 次"));
  secStats.push(reportLine("手动跳过样本（已被学习）", (s.manual || 0) + " 次"));
  lines.push(reportFold("拦截战绩（累计）", secStats));
  // ② 订阅两源命中（市场优先、内置兜底——v7.1.0 使用顺序治理）
  const secSub = [];
  secSub.push(reportLine("使用顺序", "市场规则优先 → 内置兜底 → 全局组垫底"));
  secSub.push(reportLine("市场源（导入·日更）", (sub.remoteHits || 0) + " 次命中" + (sub.remoteOk ? " ·「" + (sub.remoteName || "订阅") + "」" + (sub.remoteApps || 0) + " 款/" + (sub.remoteRules || 0) + " 条" : " · 未导入（订阅市场一键导入）")));
  secSub.push(reportLine("内置源（APK 快照）", (sub.builtinHits || 0) + " 次命中 · " + (sub.builtinApps || 0) + " 款/" + (sub.builtinRules || 0) + " 条"));
  lines.push(reportFold("订阅两源命中", secSub));
  // ③ 应用战绩 Top5（秒跳/弹窗/手动合计）
  try {
    const byApp = s.byApp || {};
    const top = Object.keys(byApp).map(k => {
      const a = byApp[k] || {};
      return { k, score: (a.auto || 0) + (a.popup || 0) + (a.manual || 0), a };
    }).filter(x => x.score > 0).sort((x, y) => y.score - x.score).slice(0, 5);
    if (top.length) {
      const secTop = [];
      top.forEach(x => {
        secTop.push(reportLine(shortPkgName(x.k), (x.a.auto || 0) + " 秒跳 / " + (x.a.popup || 0) + " 弹窗 / " + (x.a.manual || 0) + " 手动"));
      });
      lines.push(reportFold("应用战绩 Top5", secTop));
    }
  } catch (e) {}
  // ④ 能耗画像（本次开机）
  if (en && en.ok) {
    const secEn = [];
    secEn.push(reportLine("无障碍事件 / 原生探针 / 全量扫描", en.events + " / " + (en.probes || 0) + " / " + en.scans));
    secEn.push(reportLine("手势能耗估算", en.microWh + " µWh（≈" + (en.microWh / 1000).toFixed(2) + " mWh）"));
    lines.push(reportFold("能耗画像（本次开机）", secEn));
  }
  // ⑤ 设备画像
  const secDev = [];
  secDev.push(reportLine("设备", (dev.brand || "--") + " " + (dev.model || "") + " · " + (dev.os || "--") + " · Android " + (dev.sdk || "?")));
  secDev.push(reportLine("多点误点防线", "F 同指纹静默 / G 新鲜坐标复核 / H 结果静默（三重防线已启用）"));
  lines.push(reportFold("设备画像", secDev));
  // 底部生成时间行（折叠区外最底部——全收起/全展开都常驻）
  lines.push('<div class="rp-meta">生成于 ' + new Date().toLocaleString("zh-CN", { hour12: false })
    + " · 数据仅存本机 · 上传日志时本报告要点随引擎画像一并附带</div>");
  box.innerHTML = lines.join("");
  box.style.display = "";
  // 折叠交互：点标题行切换 .open（默认全收起，CSS 控制展示）
  box.querySelectorAll(".rp-fold-hd").forEach(hd => {
    hd.addEventListener("click", () => { hd.parentNode.classList.toggle("open"); });
  });
}

if ($("#btn-report")) {
  $("#btn-report").addEventListener("click", () => { renderReport(true); toast("报告已刷新"); });
}

/* ══════════ 我的：链接 ══════════ */
function openUrl(u) {
  if (HAS_BRIDGE) { try { AndroidApp.openUrl(u); return; } catch (e) {} }
  window.open(u, "_blank");
}
$("#link-repo").addEventListener("click", () => openUrl("https://github.com/xiaobaiba999/Skip-Ad"));

/* ══════════ Toast ══════════
 * v7.7.0 常驻根治（用户实锤：点击开启弹窗广告屏蔽后「弹窗屏蔽已开启」
 * 一直停留在底部）。旧实现两个失败面：
 *   ① 收尾纯靠 JS setTimeout——真机上同步桥调用（setStateBool/kvGet 走
 *     JavaBridge）卡住 JS 线程时，2200ms 计时不触发 → 永不收尾；
 *   ② 内联 cssText transition + inline opacity 的组合在部分 OEM WebView
 *     上渲染失灵 → opacity:0 改了不重绘 → 永久可见。
 * 新实现三重保险：
 *   ① CSS 关键帧动画（toastFade，animation-fill-mode:forwards）——动画
 *     跑在合成器线程，JS 线程被卡住也照常走完，终态 opacity:0 + visibility:
 *     hidden 双保险（opacity 合成怪癖也不可见）；
 *   ② .show 类与基础态都直接声明样式（不支持动画的远古 WebView 也退化
 *     正常：.show=可见，兜底计时器摘类即隐藏）；
 *   ③ JS 兜底计时器（2600ms 摘 .show）保持——动画与计时器双通道任一
 *     活着都能收尾。 */
let toastTimer = null;
function toast(msg) {
  let t = $("#toast");
  if (!t) {
    t = document.createElement("div");
    t.id = "toast";
    document.body.appendChild(t);
  }
  t.textContent = msg;
  t.classList.remove("show");
  void t.offsetWidth; // 强制回流：同一条 toast 连发时关键帧动画从头重放
  t.classList.add("show");
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.classList.remove("show"); }, 2600);
}

/* ══════════ 启动 ══════════ */
(async function init() {
  /* ═══ v7.11.0 开屏提醒提速（用户指令：提醒速度一定要快——进入 app 就显示）═══
   * 提醒评估提到 init 第一步：先于 refreshHome/renderWhitelist/keepAlive
   * 一切桥调用。实测教训（模拟器 TCG 慢环境实锤）：renderWhitelist 的
   * whitelistJson 枚举全机应用（300+ PackageManager IPC）在慢设备上以
   * 分钟计——同步桥阻塞 JS 线程时排在后面的 maybeShowPinTip 迟迟不执行
   * ——「进入即显」被打折扣。现在第一步就评估（纯本地零桥调用——
   * pinTipThrottled/pinTipRecentlyShown 读 kv 时间戳 + DOM 判定，毫秒级），
   * 白名单等慢操作后置，提醒在任何设备上都是首帧即出。
   * v7.12.0：白名单已整体异步化（whitelistJsonAsync 桥——卡死根治），
   * 同步阻塞源已消除；评估仍保持在第一步（首帧即出的语义不变）。 */
  try { maybeShowPinTip(); } catch (e) {}
  refreshHome();
  renderWhitelist(true);   // v7.12.0：异步化——骨架即时出，回调后渲（零阻塞）
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
      /* v7.13.0 切回即提醒：从后台切回前台也是「进入 App」（用户语义
       * 实锤——「进入就提醒」包含切回场景）。提醒评估跑一次：24h 节流
       * （含跨升级重置）+ 60s 短窗双闸保护——点过「不再提示」的用户
       * 不被打扰，60s 内反复切回不连弹。 */
      try { maybeShowPinTip(); } catch (e) {}
    }
  });
  // 通知权限（Android 13+，保活提醒需要）
  // v7.9.6plus：启动时新版本检测（用户指令——首屏渲染后异步检查，
  // versionCode 主判，发现新版本优先显示全屏更新页；检查失败静默
  // 进入 App，绝不阻塞启动。v7.0.0「启动零网络」决策由本指令取代）。
  // v7.10.0 异步化：fetchLatest 走 updateCheckAsync 桥——JS 主线程零网络
  // 等待；结果到达后新版展示全屏更新页（旧版照常进入）。
  setTimeout(() => {
    void checkUpdateOnLaunch();
  }, 800);
  /* v7.11.0 开屏提醒已在 init 第一步评估（见头部——先于一切慢桥调用，
   * 纯本地毫秒级）；此处不再重复调用（pin_tip_shown_at 60s 持久短窗
   * 保证幂等，省一次冗余评估）。更新页先到（800ms 异步检查先回）
   * 则本轮不打扰，更新页后到则主动收起提醒卡（见 showUpdateGate）。 */
  // 每 5s 刷新首页状态（首次进入最关心）
  setInterval(() => {
    if ($("#page-home").classList.contains("active")) {
      refreshHome();
      renderKeepAlive();
    }
  }, 5000);
  // v7.0.0：初始化完成哨兵（index.html 内联保险丝在 5s 后检查——
  // app.js 早期崩溃时不再「检查中/加载中」永久占位，给出可操作指引）
  window.__appBootOk = true;
})();
