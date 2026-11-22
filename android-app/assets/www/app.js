/* 开屏卫士 v7.9.9 · 网络链路热修复专项（小米 15 Pro 真机反馈驱动）：检查更新源链重构（raw 主源 + jsDelivr 四接入点国内可达备用链 + 整链 40s 预算 + 劫持面降级）+ 日志上传超时阶段化（连接超时/读取超时/TLS 干扰分家——幂等重试语义如实告知）· 冻结批次/batchId 幂等/额度分账零改动 · 广告跳过核心零改动 */
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
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "7.9.9"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "79900"; }

/* ══════════ 吉祥物（v4.1.0 全新形象：白机器人小侠 + 绿披风跳跃） ══════════
 * 与启动器图标同源；state: on（跳跃浮动）/ off（灰度待命） */
function mascotImg(size, state, head) {
  const on = state !== "off";
  return `<img src="${head ? "mascot-head.png" : "mascot.png"}" width="${size}" height="${size}" alt="吉祥物" class="${on ? "on" : "off"}" draggable="false">`;
}

$("#brand-logo").innerHTML = mascotImg(30, "on", true);

/* ═══ v7.9.9 版本记录（网络链路热修复专项——真机反馈驱动）═══ */
const CURRENT_RELEASE = {
  date: "2026-11-24",
  title: "检查更新国内可达修复 + 日志上传超时阶段化修复",
  features: [
    "检查更新源链重构（小米 15 Pro 真机「网络连接错误」根治）：raw 主源国内被阻断时自动切换 jsDelivr 四接入点备用链（订阅通道同族实战链）；GitHub Pages 死源（未启用恒 404）移除；整链 40 秒预算封顶，每源至多一次请求",
    "更新清单防劫持降级：任一源返回篡改/错误内容（非法 JSON/包名不匹配）不采信也不放弃，自动换下一源验证；网络异常时仍优先展示缓存数据并标注数据时间",
    "日志上传超时阶段化（「上传超时」根治第一步）：连接超时（请求未送达——提示切换网络）与读取超时（服务端可能已收到——如实提示「结果暂时无法确认，重试使用原批次不会重复建 Issue」）分家；TLS 网络干扰独立分类",
    "上传连接超时 15s→10s（阻断网络快速失败不再白等）；读取超时保持 45s（服务端建 Issue 尾延迟实测可超 20s）；冻结批次/batchId 幂等/自动限额 24h/3 全部零改动",
    "发布链路：清单发布后自动 purge jsDelivr 缓存（热修复即时可达国内）+ 网络链路端到端验证工作流（清单五源 + 中转服务健康 + 真实上传 + 同 batchId 幂等不二建 + Issue 核验清理）",
  ],
  summary: "v7.9.9：网络链路热修复轮——更新检查国内可达 + 上传超时如实分类。",

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
  if (pg === "sub") refreshSub();
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
 * 失败检视/最近失败原因）+「立即提交」（同一服务端限额，绝不绕过）
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
  if (!on) {
    note.textContent = "默认关闭 · 每 24 小时最多自动上传 3 次 · 日志采集与手动上传不受影响";
    return;
  }
  let st = null;
  try { st = JSON.parse(bridgeCall("logUploadStatus", null)); } catch (e) {}
  if (!st) {
    note.textContent = "自动上传已开启 · 每 24 小时最多成功 3 次";
    return;
  }
  const agoMin = st.lastAckedAt > 0 ? Math.max(1, Math.round((Date.now() - st.lastAckedAt) / 60000)) : 0;
  let s = "自动上传已开启 · 24h 已成功 " + (st.acked24h || 0) + "/3"
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
    toast(el.checked ? "已开启自动上传（24h 最多 3 次；采集与手动上传不受影响）" : "自动上传已关闭（日志仍会采集，可随时手动上传）");
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

/* ══════════ v4.3.0 应用白名单 ══════════ */
let wlCache = null; // {list:Set, known:[{pkg,label}]}
let wlQuery = "";   // v7.1.0：白名单搜索词（中文名/包名实时过滤）
let wlSearchTimer = null; // v7.2.0：搜索输入 120ms debounce 定时器
let wlTotalEl = null;     // v7.2.0：列表上方「共 N 款应用」统计行（动态创建，避免改 index.html 结构）

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
  if (!wlCache || force) loadWhitelist();
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
/* v5.8.0：推荐订阅按钮已随市场防呆版移除；示例链接保留注释态（Lin-arm 源已在市场一键导入）
const SUB_EXAMPLE_UNUSED = "https://cdn.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5"; */
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
  renderSubMarket(); /* v5.8.0：市场先行渲染（浏览器预览走兜底三源清单） */
  if (!HAS_BRIDGE) {
    $("#sub-builtin-apps").textContent = "21 款";
    $("#sub-builtin-rules").textContent = "21 条";
    return;
  }
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
  box.className = "fb-result"; box.textContent = "正在拉取订阅（失败自动换镜像，全失败时离线快照兑底）…";
  const r = await subCall("subSetUrl", url);
  if (r.ok) {
    box.className = "fb-result ok";
    box.textContent = "已导入「" + (r.name || "订阅") + "」：" + (r.apps || 0) + " 款应用 · " + (r.rules || 0) + " 条规则";
  } else {
    box.className = "fb-result err"; box.textContent = r.error || "导入失败";
  }
  refreshSub();
});

/* ═══ v5.8.0 订阅市场（防呆版）：点一下整行即导入 ═══
 * 用户实锤：「点击订阅就直接导入进来，把仓库按钮去掉，再把底部更多
 * 订阅去掉，App 内尽量避免跳转操作，只保留 3-5 个活跃且 star 多的」：
 *  · 只留 3 个高星活跃源（Lin-arm ⭐7.4k / ganlinte ⭐935 /
 *    MengNianxiaoyao ⭐496——gkd-subscription 话题按星数前三）；
 *  · 整行即按钮：点一下直接导入，右侧状态位显示「导入中… → 已导入 ✓」；
 *  · 零跳转：无仓库按钮、无底部链接、无 openUrl。 */
let subMarketData = [];
/* 浏览器预览兜底（无桥环境渲染同一份 3 源清单） */
const MARKET_FALLBACK = [
  { name: "GKD 订阅（id667）", owner: "Lin-arm", badge: "日更",
    desc: "⭐7.4k · 社区事实标准", idx: 0,
    url: "https://cdn.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5" },
  { name: "ganlin 的 GKD 订阅", owner: "ganlinte", badge: "周更",
    desc: "⭐935 · 610 款应用", idx: 1,
    url: "https://cdn.jsdelivr.net/gh/ganlinte/GKD-subscription@main/dist/ganlin_gkd.json5" },
  { name: "小年侠订阅", owner: "MengNianxiaoyao", badge: "活跃",
    desc: "⭐496 · 持续维护", idx: 2,
    url: "https://cdn.jsdelivr.net/gh/MengNianxiaoyao/gkd-subscription@main/dist/gkd.json5" },
];
function renderSubMarket() {
  const box = $("#sub-market");
  if (!box) return;
  let list = subMarketData;
  if (HAS_BRIDGE && !list.length) {
    try { list = subMarketData = JSON.parse(AndroidApp.subRecommend() || "[]"); } catch (e) {}
  }
  if (!HAS_BRIDGE && (!list || !list.length)) list = MARKET_FALLBACK;
  if (!list || !list.length) {
    box.innerHTML = '<div class="sub-note">订阅市场不可用（请更新到最新版）</div>';
    return;
  }
  box.innerHTML = list.map((s, i) =>
    '<button class="market-item market-tap" type="button" data-sub-import="' + i + '"'
    + ' aria-label="一键导入订阅 ' + esc(s.name) + '">'
    + '<div class="market-info">'
    + '<div class="market-name">' + esc(s.name) + '</div>'
    + '<div class="market-desc">' + esc(s.desc) + '</div>'
    + '</div>'
    + '<span class="market-go" data-sub-state="' + i + '">导入</span>'
    + '</button>').join("");
  box.querySelectorAll("[data-sub-import]").forEach((btn) => {
    btn.addEventListener("click", () => { void importMarketSub(+btn.dataset.subImport, list); });
  });
}
async function importMarketSub(idx, list) {
  const s = list[idx];
  if (!s) return;
  const stateEl = $('#sub-market [data-sub-state="' + idx + '"]');
  if (stateEl) stateEl.textContent = "导入中…";
  $("#sub-url").value = s.url;
  if (!HAS_BRIDGE) {
    if (stateEl) stateEl.textContent = "已导入 ✓";
    toast("已导入「" + s.name + "」（浏览器演示环境）");
    return;
  }
  const r = await subCall("subSetUrl", s.url);
  if (r.ok) {
    /* v7.0.0：离线快照兑底导入（八通道全失败时 APK 内置快照直接生效） */
    if (r.snapshot) {
      if (stateEl) stateEl.textContent = "已导入(离线) ✓";
      toast("已导入「" + (r.name || s.name) + "」离线快照：" + (r.apps || 0) + " 款应用（联网后自动更新最新版）");
    } else {
      if (stateEl) stateEl.textContent = "已导入 ✓";
      toast("已导入「" + (r.name || s.name) + "」：" + (r.apps || 0) + " 款应用");
    }
  } else {
    if (stateEl) stateEl.textContent = "再点重试";
    toast(r.error || "导入失败，再点一下重试");
  }
  refreshSub();
}

/* ═══ v7.0.0 订阅导入镜像切换进度（Java 桥实时回报）═══
 * SubscriptionManager 每切换一个镜像通道回调一次：
 * window.__subProgress(attempt, total, host)——导入行状态位
 * 显示「镜像 X/Y」，页内结果框同步显示，等待期间零黑盒。 */
window.__subProgress = function (attempt, total, host) {
  try {
    const stateEl = $('#sub-market [data-sub-state]');
    if (stateEl && String(stateEl.textContent).indexOf("导入") === 0) {
      stateEl.textContent = "镜像 " + attempt + "/" + total;
    }
    const box = $("#sub-result");
    if (box) {
      box.className = "fb-result";
      box.textContent = "正在拉取订阅（镜像 " + attempt + "/" + total + " · " + esc(host) + "）…";
    }
  } catch (e) {}
};

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

/* v7.9.6plus：段内前缀数字解析（"6plus"→6——"7.9.6plus"≈7.9.6 同族；
 * Java Net.segNum 同源同修——防 plus 后缀把第三段吞成 0 误判新版） */
function cmpVer(a, b) {
  const seg = (s) => { const m = /^[0-9]+/.exec(String(s || "")); return m ? parseInt(m[0], 10) : 0; };
  const pa = String(a).replace(/^v/, "").split("."), pb = String(b).replace(/^v/, "").split(".");
  for (let i = 0; i < 3; i++) {
    const x = seg(pa[i]), y = seg(pb[i]);
    if (x !== y) return x - y;
  }
  return 0;
}

/* ═══ v7.9.8 更新检查（静态清单架构——彻底脱离 GitHub 匿名 REST API）════
 * 根因史：v7.9.7 及以前检查更新直连 api.github.com /releases/latest（匿名
 * 60 次/小时/IP 出口配额）——国内共享出口下所有用户同池互耗，进入更新页
 * 即「查询失败：GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试」。
 * 新链路：
 *  APK 内：AndroidApp.updateCheck(manual) → UpdateRepository（静态清单
 *  raw.githubusercontent.com main 分支 + Fastly CDN 零 REST 配额 + 15min
 *  本地缓存 + 单飞并发合并 + 手动 30s 冷却/自动单次 + 失败指数退避 +
 *  ETag 304 + 受控备用源 + 错误十类）→ UpdateManifest（清单契约校验：
 *  包名/版本/APK 地址发布通道/SHA256）。isNewer 权威判定 = versionCode
 *  主判（Net.isNewerRelease——与 v7.9.6plus 起语义完全一致）。
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
    apkMirrors: [],
  };
}

async function fetchLatest(manual) {
  if (HAS_BRIDGE) {
    try { return JSON.parse(AndroidApp.updateCheck(!!manual)); }
    catch (e) { return { ok: false, error: "检查失败", errorCode: "UPDATE_NO_CACHE" }; }
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

/* Release 正文 versionCode 元数据行解析（Java Net.versionCodeOfNotes 镜像）：
 * 匹配首个 versionCode: 开头的行，取前缀连续数字；无/非法 → 0（回退名比较） */
function versionCodeFromNotes(notes) {
  if (!notes) return 0;
  const lines = String(notes).split("\n");
  for (let i = 0; i < lines.length; i++) {
    const t = lines[i].trim();
    if (t.startsWith("versionCode:")) {
      const m = t.slice("versionCode:".length).trim().match(/^[0-9]+/);
      const v = m ? parseInt(m[0], 10) : 0;
      return (v > 0 && v <= 2147483647) ? v : 0;
    }
  }
  return 0;
}

/* 新版本判定（Java Net.isNewerRelease 镜像）：双方 versionCode 在场 →
 * versionCode 主判（≤ 不算——绝不提示降级）；缺失回退 versionName 三段比较。
 * （fetchLatest 已移至上方静态清单版；本函数为浏览器预览 isNewer 兑底保留） */
function isNewerByCode(remoteVc, remoteTag, localVc, localVer) {
  if (remoteVc > 0 && localVc > 0) return remoteVc > localVc;
  return cmpVer(remoteTag, localVer) > 0;
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
    return;
  }
  if (!d.hasRelease) { note.className = "fb-result"; note.style.display = ""; note.textContent = "尚未发布版本"; return; }
  latestRelease = d;
  const latest = String(d.tag || "").replace(/^v/, "");
  /* v7.9.8 数据来源透明化：网络异常回退缓存 → 标注数据更新时间（用户知情） */
  const cacheNote = d.staleCache
    ? "（网络异常，以下为缓存数据 · 更新于 " + (ageText(d.cachedAt) || "未知时间") + "）"
    : (d.fromCache ? "（数据更新于 " + (ageText(d.cachedAt) || "刚刚") + "）" : "");
  /* v7.9.6plus：优先用原生侧权威判定（Net.isNewerRelease——versionCode 主判，
   * 清单 versionCode > 本地 versionCode；缺失回退 versionName 三段比较），
   * 浏览器路径回退 isNewerByCode（Java 镜像） */
  const isNewer = typeof d.isNewer === "boolean" ? d.isNewer
    : isNewerByCode(d.remoteVersionCode || 0, String(d.tag || ""), parseInt(appCode(), 10) || 0, appVer());
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

async function checkUpdateOnLaunch() {
  try {
    const d = await fetchLatest(false);
    if (!d || !d.ok || !d.hasRelease) return; // 检查失败静默——App 正常可用
    const isNewer = typeof d.isNewer === "boolean" ? d.isNewer
      : isNewerByCode(d.remoteVersionCode || 0, String(d.tag || ""), parseInt(appCode(), 10) || 0, appVer());
    if (isNewer) showUpdateGate(d);
  } catch (e) { /* 启动检查绝不阻塞 App */ }
}

/* 全屏更新页渲染（fixed 覆盖全屏 · z-index 最高 · 不影响原页面布局——
 * 关闭即回到原状；移动端优先适配无横向溢出） */
function showUpdateGate(d) {
  gateRelease = d;
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

/* ══════════ v7.9.8 Bug 反馈提示（24h 节流 · 纯本地 · 绝不自动上传）══════════
 * 用户指令（取代 v7.9.7 一次性提示）：
 *  · 每次进入 App 都有机会看到提示（冷启动 init 评估，非前后台切换）；
 *  · 「知道了」/「去上传日志」任一确认 → 记录 bug_feedback_confirmed_at
 *    （epoch 毫秒，SharedPreferences kvSet 白名单键 / 浏览器 localStorage
 *    兑底），24h 内不再显示；超过 24h 下次进入 App 重新显示；
 *  · 用户未确认不永久消耗资格（无任何永久 true/false 标记——只看时间戳）；
 *  · Activity 重建/旋转防护：展示时记 bug_feedback_shown_at，5 分钟短窗内
 *    重建不再重复弹（短窗只防抖不耗资格）；
 *  · 前后台切换不重复弹（visibilitychange 不评估——仅 init 调 maybeShowBugTip）；
 *  · 更新提示优先：#update-gate 在场不弹（z-index 110 < 更新页 120）；
 *  · 「去上传日志」只做导航（切「我的」页 + 滚到引擎日志卡片），绝不代替
 *    用户点击上传、绝不自动建 Issue、绝不消耗上传次数；同时记确认时间戳
 *    （从日志页返回 App 不立即重复弹）。 */
const BUG_TIP_THROTTLE_MS = 24 * 60 * 60 * 1000;  /* 确认后 24h 节流 */
const BUG_TIP_RESHOW_GUARD_MS = 5 * 60 * 1000;    /* 展示后 5min 防重建重复弹 */
let bugTipShownThisSession = false;
function bugTipThrottled() {
  try {
    const t = parseInt(store.get("bug_feedback_confirmed_at", "0"), 10) || 0;
    return t > 0 && (Date.now() - t) < BUG_TIP_THROTTLE_MS;
  } catch (e) { return true; }
}
function bugTipRecentlyShown() {
  try {
    const t = parseInt(store.get("bug_feedback_shown_at", "0"), 10) || 0;
    return t > 0 && (Date.now() - t) < BUG_TIP_RESHOW_GUARD_MS;
  } catch (e) { return true; }
}
function showBugTip() {
  if (bugTipShownThisSession) return;
  bugTipShownThisSession = true;
  try { store.set("bug_feedback_shown_at", String(Date.now())); } catch (e) {}
  let tip = $("#bug-tip");
  if (tip) tip.remove();
  tip = document.createElement("div");
  tip.id = "bug-tip";
  tip.innerHTML =
    '<div class="ug-card" role="dialog" aria-modal="true" aria-label="反馈提示">'
    + '<img class="ug-logo" src="mascot-head.png" width="56" height="56" alt="开屏卫士图标">'
    + '<div class="ug-app-name">使用过程中发现问题了吗？</div>'
    + '<div class="ug-badge">帮助改进开屏卫士</div>'
    + '<div class="ug-notes">'
    + '<div class="ug-note-item">• 开屏广告没有跳过</div>'
    + '<div class="ug-note-item">• 广告跳过不准确</div>'
    + '<div class="ug-note-item">• 弹窗无法关闭</div>'
    + '<div class="ug-note-item">• App 出现卡顿或异常</div>'
    + '<div class="ug-note-item">• 其他使用体验问题</div>'
    + '</div>'
    + '<div class="ug-size">可以进入「我的 → 引擎日志 → 一键上传」上传日志，'
    + '帮助开发者定位和改进问题。仅在你主动点击时才会上传。</div>'
    + '<button class="ug-btn-primary" id="bug-tip-go">去上传日志</button>'
    + '<button class="ug-btn-ghost" id="bug-tip-ok">知道了</button>'
    + '</div>';
  document.body.appendChild(tip);
  const confirm = () => {
    /* 两个按钮都记确认时间（24h 节流主闸）——不自动上传任何东西 */
    try { store.set("bug_feedback_confirmed_at", String(Date.now())); } catch (e) {}
    const t = $("#bug-tip");
    if (t) t.remove();
  };
  /* 「去上传日志」：确认节流 + 只做导航——切到「我的」页并滚到引擎日志
   * 卡片，绝不代替用户点击上传（最终决定权属于用户） */
  $("#bug-tip-go").addEventListener("click", () => {
    confirm();
    try {
      const me = Array.from($$(".tabbtn")).find((b) => b.dataset && b.dataset.page === "me");
      if (me) me.click();
      const btn = $("#btn-upload-log");
      const card = btn && btn.closest ? btn.closest(".card") : null;
      if (card) setTimeout(() => { try { card.scrollIntoView({ behavior: "smooth", block: "center" }); } catch (e) {} }, 80);
    } catch (e) {}
  });
  /* 「知道了」：只记确认时间，不上传任何东西 */
  $("#bug-tip-ok").addEventListener("click", confirm);
}
function maybeShowBugTip() {
  try {
    if (bugTipThrottled()) return;         // 24h 内已确认——节流
    if (bugTipRecentlyShown()) return;     // 5min 内已展示过——防 Activity 重建重复弹
    if ($("#update-gate")) return;         // 更新页在场——升级优先，本轮不打扰
    showBugTip();
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
  if (k === "NET_CONNECT_FAIL") return "网络连接失败，请检查网络后重试——日志已保留（不会丢失）";
  if (k === "NET_CONNECT_TIMEOUT") return "连接上传服务超时——当前网络可能无法访问上传服务，可尝试切换 Wi-Fi/移动网络后重试；日志已保留（不会丢失）";
  if (k === "NET_READ_TIMEOUT") return "上传超时，结果暂时无法确认——若服务端已收到，重试将使用原批次，不会重复创建 Issue";
  if (k === "NET_TLS") return "安全连接失败（网络传输可能被干扰）——可尝试切换网络后重试；日志已保留（不会丢失）";
  if (k === "NET_TIMEOUT") return "上传超时，请稍后重试——若服务端已收到，重试会自动复用原 Issue，不会重复提交";
  if (k === "RATE_LIMITED") return "上传次数已达到限制，请稍后重试";
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
 * 原因并保留可重试；服务端限流如实告知。手动上传不受 24h/3 额度限制。
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
 * 真实 HTTP 状态码/服务端码/网络异常类型——绝不统一「稍后再试」）。 */
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
    box.innerHTML = "日志上传成功" + (links ? "（Issue " + links + (anyUrl ? "，点击可查看）" : "——编号由服务端确认）") : "")
      + "<br>24 小时内已成功上传 " + (res.acked24h || 0) + " 批（自动限额，手动上传不受限）";
    const anchors = box.querySelectorAll("a[data-issue-url]");
    for (const a of anchors) {
      a.addEventListener("click", () => openUrl(a.getAttribute("data-issue-url")));
    }
    toast("日志上传成功");
  } else if ((res.waited || 0) > 0) {
    const mins = res.retryAfterMs ? Math.ceil(Number(res.retryAfterMs) / 60000) : 0;
    toast("上传次数已达到限制");
    showUploadFail(box, "上传次数已达到限制，请稍后重试"
      + (mins > 0 ? "（约 " + mins + " 分钟后可再试）" : "")
      + "——日志已保留，稍后自动/手动重试均可", doUploadLog);
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
  // v7.9.6plus：启动时新版本检测（用户指令——首屏渲染后异步检查，
  // versionCode 主判，发现新版本优先显示全屏更新页；检查失败静默
  // 进入 App，绝不阻塞启动。v7.0.0「启动零网络」决策由本指令取代）。
  // v7.9.7：检查收尾后（无更新页在场时）评估 Bug 反馈一次性提示——
  // 更新优先于提醒；提示为纯本地状态，网络检查失败也照常评估。
  setTimeout(() => {
    void checkUpdateOnLaunch().finally(() => {
      try { maybeShowBugTip(); } catch (e) {}
    });
  }, 800);
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
