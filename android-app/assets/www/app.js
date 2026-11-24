/* 开屏卫士 v7.15.10 · 激进模式重构版（基于 v7.6.0 激进模式基线 · 7.6 之后版本整体召回后的重制版；v7.15.10 三图类型死洞根治（开屏×强广告形态豁免带）+ 系统权限弹窗零扫描 + 探针去重） */
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
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "7.15.10"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "86000"; }

/* ══════════ 吉祥物（v4.1.0 全新形象：白机器人小侠 + 绿披风跳跃） ══════════
 * 与启动器图标同源；state: on（跳跃浮动）/ off（灰度待命） */
function mascotImg(size, state, head) {
  const on = state !== "off";
  return `<img src="${head ? "mascot-head.png" : "mascot.png"}" width="${size}" height="${size}" alt="吉祥物" class="${on ? "on" : "off"}" draggable="false">`;
}

$("#brand-logo").innerHTML = mascotImg(30, "on", true);

/* ═══ v7.15.10 版本记录（三图类型死洞根治 + 整体性能功耗优化）═══ */
const CURRENT_RELEASE = {
  date: "2026-11-25",
  title: "难跳广告三类型根治（H5 上滑互动/视频大树/晚到键）+ 权限弹窗零扫描 + 功耗优化",
  features: [
    "开屏×强广告形态豁免带（三图实锤根治）：①H5/视频广告树 300~800 节点时角落图片跳过键被 150 树上限挡死（泛位候选又排除角落几何 → 完全无处可点）→ 强形态下树上限放宽至 600；②上滑互动广告的滚动容器被 scrollPresent 硬挡 → 开屏×强形态豁免；③视频广告跳过键晚到（5s 倒计时 + 渲染延迟可超 6.5s）→ 强形态时窗延至 8s。非开屏窗与无强形态窗一切照旧（信息流底部导航「我的」Tab 误点红线保持）",
    "系统权限弹窗零扫描（#103 日志实锤）：permissioncontroller 被当开屏扫描+弹窗识别双路径白扫（「开屏未命中…候选样本：拒绝/始终允许」+「弹窗未命中 · 入窗 1.79E9s」双刷屏）——GrantPermissionsActivity 的「拒绝」是全 app 误触代价最高的控件之一。事件入口直接丢弃：零扫描零点击零日志",
    "诊断日志升级：未命中日志新增「图片键候选 角落N+泛位M（未出手：门控/时序）」——图片键在场但被门控/时序挡住时直接可见（三图类型补规则金样本）；「入窗」字段对 winStart=0 场景显示 ? 不再出现 1.79E9 疯值",
    "功耗优化：探针去重（CLEAN 窗 0ms 排针与首个内容事件探针 200ms 内双跑收敛——每窗省一轮全窗枚举+文本搜索）；CLEAN 窗图形探针预算 120→180（H5 大树深层图片键复活面）；权限弹窗类系统窗零扫描",
  ],
  summary: "v7.15.10 三图类型死洞根治（开屏×强广告形态豁免带：大树/滚动/晚到）+ 权限弹窗零扫描 + 图片键候选诊断 + 探针去重（v7.15.9 全部能力保留）",
};

/* v7.0.0 新版功能描述（规范：≤5 个标题、带序号；检查更新发现新版本时就地渲染
 * ——v7.15.7 起检查更新在「版本」页）。
 * 优先用 Java 桥预解析的 headlines[]（Net.parseRelease → core.ReleaseNotes
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
  if (pg === "help") renderHelpDynamic();
  if (pg === "me") { renderLog(); renderReport(); } // v7.2.0：进入我的页静默刷新使用报告（饼图常驻，零 toast）；v7.15.7：版本相关已迁「版本」页
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

/* ══════════ v4.3.0 应用白名单 ══════════ */
let wlCache = null; // {list:Set, known:[{pkg,label}]}
let wlExpanded = false;  // v7.15.6：白名单列表展开态（默认收起只显示 5 款）
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

/* ═══ v7.2.0 白名单全量应用列表（用户实锤：搜索不到新装/未被拦截过的应用）═══
 * 后端 whitelistJson 已改为返回全部已安装可启动应用（∪白名单∪战绩，上限 500，
 * 30s 缓存）；前端配合：列表上方加「共 N 款应用」统计行、空态改
 * 「未能读取应用列表」（默认即全量，不再有“暂无记录”态）。
 * v7.15.6：搜索框与手动添加行按用户指令移除——默认只显示 5 款（白名单内
 * 置顶），底部「展开全部 N 款」切换全量（滚动容器仍在，展开后可滚动检索）。 */
function wlTotalRow() {
  if (wlTotalEl && wlTotalEl.parentNode) return wlTotalEl;
  const box = $("#wl-list");
  if (!box || !box.parentNode) return null;
  wlTotalEl = document.createElement("div");
  wlTotalEl.className = "wl-total";
  box.parentNode.insertBefore(wlTotalEl, box);
  return wlTotalEl;
}

const WL_PREVIEW_COUNT = 5; // v7.15.6：默认显示的应用数（用户指令：3-5 款）

function renderWhitelist(force) {
  const box = $("#wl-list");
  if (!box) return;
  if (!wlCache || force) loadWhitelist();
  const wl = wlCache;
  $("#wl-count").textContent = String(wl.list.size);
  // 列表上方统计行——全量基数
  const knownAll = wl.known || [];
  const totalRow = wlTotalRow();
  if (totalRow) totalRow.textContent = "共 " + knownAll.length + " 款应用";
  // 白名单里的排前面，其余中文序（v7.15.6：搜索过滤已随搜索框移除）
  const known = knownAll.slice().sort((a, b) => {
    const wa = wl.list.has(a.pkg) ? 0 : 1, wb = wl.list.has(b.pkg) ? 0 : 1;
    if (wa !== wb) return wa - wb;
    return String(a.label).localeCompare(String(b.label), "zh");
  });
  if (!known.length) {
    box.innerHTML = '<div class="empty">未能读取应用列表</div>';
    return;
  }
  // v7.15.6：默认收起只显示前 5 款；展开后全量渲染（滚动容器收纳）
  const shown = wlExpanded ? known : known.slice(0, WL_PREVIEW_COUNT);
  let html = shown.map(x => {
    const on = wl.list.has(x.pkg);
    return '<div class="wl-row">'
      + '<div class="wl-label"><span class="wl-name">' + esc(x.label) + '</span>'
      + '<span class="wl-pkg">' + esc(x.pkg) + '</span></div>'
      + '<label class="switch"><input type="checkbox" data-wl="' + esc(x.pkg) + '"'
      + (on ? ' checked' : '') + '><span class="slider"></span></label>'
      + '</div>';
  }).join("");
  if (known.length > WL_PREVIEW_COUNT) {
    html += '<div class="wl-toggle" id="wl-toggle" role="button" tabindex="0">'
      + (wlExpanded ? "收起 ▴" : "展开全部 " + known.length + " 款 ▾")
      + '</div>';
  }
  box.innerHTML = html;
  const toggle = $("#wl-toggle");
  if (toggle) {
    toggle.addEventListener("click", () => {
      wlExpanded = !wlExpanded;
      renderWhitelist(false);
    });
    toggle.addEventListener("keydown", e => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggle.click(); }
    });
  }
  box.querySelectorAll('input[data-wl]').forEach(t => {
    t.addEventListener("change", () => {
      const pkg = t.dataset.wl;
      try {
        if (t.checked) AndroidApp.whitelistAdd(pkg);
        else AndroidApp.whitelistRemove(pkg);
      } catch (e) {}
      toast(t.checked ? "已加入白名单：卫士在该应用内完全让路" : "已移出白名单：恢复守护");
      renderWhitelist(true);
    });
  });
}


/* ══════════ v7.15.0 异步桥调用（卫士卡死根治的 JS 侧）═════════
 * Java 桥长 IO 一律后台线程执行 + window.__netCb(name, json) 回调
 * （JavaBridge 线程瞬时返回——同步长 IO 堵塞桥线程把界面「卡死」的
 *  类别已从 Java 侧根除，这里配套 Promise 包装）。 */
let netCbSeq = 0;
function netCall(method, ...args) {
  return new Promise(resolve => {
    if (!HAS_BRIDGE) { resolve({ ok: false, error: "仅 APK 内可用" }); return; }
    const cbName = "__netCb" + (++netCbSeq);
    window[cbName] = (name, result) => { try { delete window[name]; } catch (e) {} resolve(result); };
    window.__netCb = (name, result) => {
      const fn = window[name];
      if (fn) fn(name, result);
    };
    try {
      AndroidApp[method](...args, cbName);
    } catch (e) {
      resolve({ ok: false, error: "调用失败" });
    }
  });
}

/* ══════════ v7.15.0 帮助页动态状态行（进入帮助页刷新）═════════
 * 帮助正文是静态指引（index.html）；这里只补一行「当前守护状态 +
 * 订阅自动导入状态」的实时行——用户从帮助页也能一眼知道卫士在不在岗。 */
function renderHelpDynamic() {
  const box = $("#help-status");
  if (!box) return;
  let running = false, masterOn = true;
  if (HAS_BRIDGE) {
    try { running = !!AndroidApp.isServiceRunning(); } catch (e) {}
    try { masterOn = AndroidApp.isMasterOn() !== false; } catch (e) {}
  }
  let subLine = "";
  if (HAS_BRIDGE) {
    try {
      const sub = JSON.parse(AndroidApp.subState());
      if (sub.remoteOk) subLine = "订阅自动导入中：" + (sub.remoteName || "社区订阅")
        + "（每日后台自动，" + (sub.snapshot ? "当前离线快照兜底" : "在线最新版") + "）";
      else subLine = "订阅自动导入：尚未完成首次导入（后台自动重试，内置快照兜底）";
    } catch (e) {}
  } else {
    subLine = "订阅自动导入：每日后台自动（演示环境）";
  }
  const state = running && masterOn ? '<b class="hs-ok">守护中</b>'
    : running ? '<b class="hs-warn">已暂停</b>（首页可一键恢复）'
    : '<b class="hs-warn">服务待恢复</b>（打开卫士首页自动恢复）';
  box.innerHTML = '<div class="hs-row">当前状态：' + state + '</div>'
    + '<div class="hs-row">' + esc(subLine) + '</div>';
}

/* ══════════ v7.15.0 进入 App 开屏提示弹窗（三按钮）═════════
 * 用户指令：开屏提示内容不放帮助里，每次进入 App 时弹出提示；
 * v7.15.4 用户指令：「不再提示」窗口 24 小时 → 12 小时；
 * 升级新版本后重新提醒一次（版本钉 tipPinVer——升级后新能力告知，
 * 与 v7.6.1 时代「页面资产符号闸」同思路的防呆：提示永不过期、
 * 也永不骚扰）。
 * 三按钮：知道了（本次关闭）/ 12 小时不再提示 / 去上传日志（直达
 * 我的页引擎日志并自动发起上传）。
 * v7.15.7 用户指令双落地：
 *  · 亮字承诺——「每周根据日志问题至少迭代版本一次」（tip-weekly
 *   品牌绿高亮行：用户日志驱动迭代节奏的公开承诺）；
 *  · 观闸（v7.15.8 用户指令升级：3 秒 → 强制观看 5 秒）——至少观看
 *   5 秒才可操作三按钮（按钮禁用 + 倒计时行，遮罩点击同期让路；
 *   到点自动解锁，绝不永久卡死）。 */
const TIP_12H_MS = 12 * 60 * 60 * 1000;
const TIP_HOLD_MS = 5000; // v7.15.8（用户指令）：强制观看 5 秒才可操作（v7.15.7 为 3 秒）
function tipDismissUntil() { return +store.get("tipDismissUntil", 0) || 0; }
function tipPinVer() { return String(store.get("tipPinVer", "")); }
function tipDue() {
  if (tipPinVer() !== appVer()) return true; // 升级到新版本 → 重新提醒一次
  return Date.now() >= tipDismissUntil();
}
function closeSplashTip() {
  const m = $("#splash-tip-modal");
  if (m) m.remove();
}
function showSplashTip() {
  if ($("#splash-tip-modal")) return;
  const wrap = document.createElement("div");
  wrap.id = "splash-tip-modal";
  wrap.style.cssText = "position:fixed;inset:0;z-index:120;background:rgba(4,12,8,.62);"
    + "display:flex;align-items:center;justify-content:center;padding:24px;";
  wrap.innerHTML =
    '<div role="dialog" aria-modal="true" aria-label="开屏广告拦截提示" class="tip-card">'
    + '<div class="tip-badge">开屏广告拦截提示</div>'
    + '<ul class="tip-list">'
    + '<li>开屏广告<b>露头即点</b>、软件内弹窗/插屏自动关闭——日常零操作。</li>'
    + '<li>把卫士<b>固定在后台</b>（最近任务加锁 + 电池白名单）：后台被清理会导致息屏或切应用回来时第一条广告漏拦（帮助页有指引）。</li>'
    + '<li>引擎日志每 8 小时自动提交一次（一日 3 次），也可点下方「去上传日志」立即上传。</li>'
    + '</ul>'
    /* v7.15.7 用户指令：亮字描述——每周根据日志问题至少迭代版本一次 */
    + '<div class="tip-weekly">每周根据日志问题<b>至少迭代版本一次</b>——你的日志直接驱动下一版</div>'
    + '<div class="tip-hold" id="tip-hold" aria-live="polite">请阅读提示（5s）</div>'
    + '<div class="tip-btns">'
    + '<button data-act="ok" class="tip-btn" disabled>知道了</button>'
    + '<button data-act="mute" class="tip-btn tip-btn-mute" disabled>12 小时不再提示</button>'
    + '<button data-act="upload" class="tip-btn tip-btn-go" disabled>去上传日志</button>'
    + '</div></div>';
  document.body.appendChild(wrap);
  /* ═══ v7.15.7 观闸 / v7.15.8 强制 5 秒：三按钮禁用 + 倒计时行，到点解锁 ═══
   * 计时器自清（到点 clearInterval），节点移除后回调零副作用
   * （detached 节点上 remove/disable 均无害）；观闸期内遮罩点击
   * 同样让路（所有关闭通道封 5 秒——「强制观看」语义）。 */
  const holdEl = wrap.querySelector("#tip-hold");
  const btns = wrap.querySelectorAll(".tip-btn");
  let holdDone = false;
  let holdLeft = Math.ceil(TIP_HOLD_MS / 1000);
  const holdTimer = setInterval(() => {
    holdLeft--;
    if (holdLeft > 0) {
      if (holdEl) holdEl.textContent = "请阅读提示（" + holdLeft + "s）";
      return;
    }
    clearInterval(holdTimer);
    holdDone = true;
    if (holdEl) holdEl.remove();
    btns.forEach(b => { b.disabled = false; });
  }, 1000);
  wrap.addEventListener("click", (ev) => {
    if (!holdDone) return; // 观闸期（原生 disabled 已挡按钮，此为遮罩/双保险）
    const act = ev.target.getAttribute && ev.target.getAttribute("data-act");
    if (!act) { if (ev.target === wrap) closeSplashTip(); return; }
    if (act === "ok") {
      store.set("tipPinVer", appVer()); // 仅本次关闭，下次进入仍提示
      closeSplashTip();
    } else if (act === "mute") {
      store.set("tipPinVer", appVer());
      store.set("tipDismissUntil", Date.now() + TIP_12H_MS);
      toast("12 小时内进入不再提示（升级新版后会再提醒一次）");
      closeSplashTip();
    } else if (act === "upload") {
      store.set("tipPinVer", appVer());
      closeSplashTip();
      gotoPage("me");
      setTimeout(() => { void doUploadLog(); }, 350);
    }
  });
}

/* 切换底栏页（供弹窗「去上传日志」直达我的页） */
function gotoPage(pg) {
  const btn = document.querySelector('.tabbtn[data-page="' + pg + '"]');
  if (btn) btn.click();
}

/* ══════════ 版本页（v7.15.7：底栏新增——当前版本/版本记录从我的页抽出）══════════ */
$("#ver-cur").textContent = "v" + appVer();
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

const GH_API = "https://api.github.com/repos/xiaobaiba999/Skip-Ad";
const UM_RAW = "https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json";
const UM_JSD = "https://cdn.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json";
const UM_TAG = (t) => "https://cdn.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@" + t + "/update/latest.json";
let ubTimer = null;
let latestRelease = null;
let dl = { channels: [], idx: 0, fileName: "", digest: "", size: 0, lastBytes: -1, lastMoveAt: 0 };

function cmpVer(a, b) {
  const pa = String(a).replace(/^v/, "").split("."), pb = String(b).replace(/^v/, "").split(".");
  for (let i = 0; i < 3; i++) {
    const x = +pa[i] || 0, y = +pb[i] || 0;
    if (x !== y) return x - y;
  }
  return 0;
}

/* v7.15.0：静态清单 → 页面结构（与 Java UpdateManifest.pageJson 同形；
 * headlines 走既有 notesToHeadlines 解析）
 * v7.15.5：清单 cdnApkUrl 已撤（旧版客户端白名单拒 gcore → 渠道链首位
 * 死局根治）；CDN 兜底直链由 apkFileName 现场合成（仓库 dist 归档直出，
 * 与 Release 资产同字节同源——与 Java 侧 UpdateHosts.cdnApkUrl 同语义）。 */
function manifestToPage(m) {
  const apkUrl = m.apkUrl || "";
  const apkFileName = m.apkFileName || "adshield.apk";
  /* v7.15.5 审核对齐：与 Java 侧 UpdateHosts.cdnApkUrl 同款路径穿越收敛
   * （只收纯 .apk 文件名——含 /\\ 的构造值不合成直链） */
  const cdn = m.cdnApkUrl || (String(apkFileName).endsWith(".apk") && !/[\\/]/.test(apkFileName)
    ? "https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/" + apkFileName
    : "");
  const mirrors = [];
  if (cdn) mirrors.push(cdn);
  if (apkUrl) {
    mirrors.push(apkUrl);
    mirrors.push("https://ghfast.top/" + apkUrl);
    mirrors.push("https://gh-proxy.com/" + apkUrl);
  }
  return {
    ok: true, hasRelease: true,
    tag: m.releaseTag || "", notes: m.releaseNotes || "",
    publishedAt: m.publishedAt || "", url: "https://github.com/xiaobaiba999/Skip-Ad/releases/tag/" + (m.releaseTag || ""),
    versionName: m.versionName || "", versionCode: m.versionCode || 0,
    apkUrl: apkUrl,
    apkMirrors: mirrors,
    apkFileName: m.apkFileName || "adshield.apk",
    apkMB: Math.round((m.apkSize || 0) / 1048576 * 100) / 100,
    apkSize: m.apkSize || 0,
    apkDigest: (m.apkSha256 || "") ? ("sha256:" + m.apkSha256) : "",
  };
}

/* 浏览器预览多源链（与 APK 内 UpdateManifest 同源同形：raw@main →
 * jsdelivr@main → @tag 探测链（下一补丁 + 下两个 minor）→ GitHub API）*/
async function fetchJsonTimed(url, ms) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), ms);
  try {
    const r = await fetch(url, { cache: "no-store", signal: ctl.signal });
    if (!r.ok) return null;
    return await r.json();
  } catch (e) { return null; } finally { clearTimeout(t); }
}
function tagCandidatesOf(v) {
  const p = String(v).replace(/^v/, "").split(".");
  const M = +p[0] || 7, m = +p[1] || 0, q = +p[2] || 0;
  return ["v" + M + "." + m + "." + (q + 1), "v" + M + "." + (m + 1) + ".0",
    "v" + M + "." + (m + 1) + ".1", "v" + M + "." + (m + 2) + ".0"];
}
async function fetchLatestBrowser() {
  let best = null;
  const tryManifest = async (url, ms) => {
    const m = await fetchJsonTimed(url, ms);
    if (m && m.versionCode > 0 && (m.versionName || "").length && (m.releaseTag || "").length
        && (!m.apkUrl || String(m.apkUrl).startsWith("https://"))
        && (!m.packageName || m.packageName === "com.zai.adshield")) {
      if (!best || m.versionCode > best.versionCode) best = m;
    }
  };
  await tryManifest(UM_RAW, 6000);
  await tryManifest(UM_JSD, 6000);
  if (!best || best.versionCode <= +appCode()) {
    for (const t of tagCandidatesOf(appVer())) {
      await tryManifest(UM_TAG(t), 4000);
    }
  }
  if (best) return manifestToPage(best);
  // GitHub API 兜底
  try {
    const r = await fetchJsonTimed(GH_API + "/releases/latest", 8000);
    if (r && r.tag_name) {
      const apk = (r.assets || []).filter(a => a && a.name && a.name.endsWith(".apk"))[0];
      return {
        ok: true, hasRelease: true, tag: r.tag_name, notes: r.body || "",
        publishedAt: r.published_at || "", url: r.html_url || "",
        apkUrl: apk ? apk.browser_download_url : "",
        apkMirrors: apk ? ["https://ghfast.top/" + apk.browser_download_url, "https://gh-proxy.com/" + apk.browser_download_url] : [],
        apkFileName: apk ? apk.name : "adshield.apk",
        apkMB: apk ? Math.round(apk.size / 1048576 * 100) / 100 : 0,
        apkSize: apk ? (apk.size || 0) : 0,
        apkDigest: apk ? (apk.digest || "") : "",
      };
    }
  } catch (e) {}
  return { ok: false, error: "多源均不可达——请稍后重试，也可到 GitHub Releases 页手动查看" };
}

/* 检查更新统一入口：APK 走异步桥（Java 多源链），浏览器走 fetch 链 */
async function fetchLatest() {
  if (HAS_BRIDGE) {
    try { return await netCall("netGetUpdateAsync"); } catch (e) { return { ok: false, error: "解析失败" }; }
  }
  return fetchLatestBrowser();
}

function beginChannel() {
  const url = dl.channels[dl.idx];
  try {
    /* v7.3.0 安全：digest/size（GitHub API HTTPS 元数据）随下载任务交给原生侧
     * ——安装前 Downloader 会流式计算 SHA-256 比对；镜像通道投毒/共享盘换包
     * 在安装前被拦下并自动删除。 */
    const r = JSON.parse(AndroidApp.startUpdateDownload(url, dl.fileName, dl.digest, dl.size));
    if (r.ok) {
      /* v7.3.0 修复：按钮选择器与真实按钮 id 对齐（#page-dl-btn——旧
       * 前缀拼装选择器永不命中，下载完成后按钮从不翻转「立即安装」） */
      const btn = $("#page-dl-btn");
      if (btn) { btn.textContent = "下载中…"; btn.disabled = true; }
      const wrap = $("#page-progress-wrap");
      if (wrap) wrap.style.display = "";
      dl.lastBytes = -1;
      dl.lastMoveAt = Date.now();
      startProgressPoll();
    } else {
      /* ═══ v7.15.5 渠道启动被拒自动换道（死局根治）═══
       * 死因（用户实锤：v7.15.1/v7.15.3 点下载报「下载链接不在允许的
       * 发布通道内」）：旧版此处只 toast 不换道——首渠道被白名单拒/
       * 系统下载器拒后整条链直接死局，后面的 ghfast/gh-proxy/github
       * 全部没机会上场。现在与「下载失败/无进展」同一换道语义：
       * 启动被拒 → 自动切下一渠道；全部渠道都拒才如实报错。 */
      if (!switchChannel("渠道启动被拒（" + (r.error || "未知") + "）")) {
        toast(r.error || "启动下载失败");
      }
    }
  } catch (e) {
    if (!switchChannel("渠道启动失败")) toast("启动下载失败");
  }
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

/* v7.15.6 冗余清理：prefix 参数移除（v7.0.0 顶部更新横幅删除后只剩
 * "page" 一个下载位，双下载位遗迹收编为硬编码 id）。 */
function startPageDownload(d) {
  if (!d.apkUrl) { toast("该版本没有 APK 附件"); return; }
  if (!HAS_BRIDGE) { window.open(d.apkUrl, "_blank"); return; }
  /* v7.15.0 中国下载兜底：gcore CDN 直链优先（全日可达），GitHub 官方与
 * ghfast/gh-proxy 镜像随后自动切换（30s 无进展即换通道——既有机制） */
  dl = { channels: (d.apkMirrors || []).concat(d.apkUrl ? [d.apkUrl] : []), idx: 0, fileName: d.apkFileName || "adshield.apk", digest: d.apkDigest || "", size: d.apkSize || 0, lastBytes: -1, lastMoveAt: Date.now() };
  beginChannel();
}

function startProgressPoll() {
  if (ubTimer) clearInterval(ubTimer);
  ubTimer = setInterval(() => {
    try {
      const d = JSON.parse(AndroidApp.updateProgress());
      const bar = $("#page-bar"), txt = $("#page-progress-text");
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
        txt.textContent = "下载完成（已验 SHA-256），点击安装即覆盖升级";
        const btn = $("#page-dl-btn");
        if (btn) { btn.textContent = "立即安装"; btn.disabled = false; btn.dataset.phase = "install"; }
      } else if (d.state === "failed") {
        clearInterval(ubTimer); ubTimer = null;
        if (switchChannel("下载失败")) return;
        txt.textContent = "下载失败，可稍后重试";
        const btn = $("#page-dl-btn");
        if (btn) { btn.textContent = "重新下载"; btn.disabled = false; btn.dataset.phase = "download"; }
      }
    } catch (e) { clearInterval(ubTimer); ubTimer = null; }
  }, 800);
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
  const d = await fetchLatest();
  btn.disabled = false;
  btn.textContent = "检查更新";
  if (!d.ok) { note.className = "fb-result err"; note.style.display = ""; note.textContent = "查询失败：" + (d.error || "未知") + "。检查更新失败？开启 GitHub 加速器（如 Watt Toolkit）后重试即可"; return; }
  if (!d.hasRelease) { note.className = "fb-result"; note.style.display = ""; note.textContent = "尚未发布版本"; return; }
  latestRelease = d;
  const latest = String(d.tag || "").replace(/^v/, "");
  const isNewer = cmpVer(latest, appVer()) > 0;
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
    note.textContent = isNewer
      ? "发现新版本 v" + latest + "（功能描述如上，下载后覆盖安装即升级）"
      : "已是最新版本 · " + CURRENT_RELEASE.title;
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
  if (latestRelease) startPageDownload(latestRelease);
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
/* v5.8.0：日志刷新按钮（用户实锤：日志上传按钮旁边加一个刷新）——
 * 重读服务内存快照（kvGet("state") 走 snapshotState 实时态） */
$("#btn-refresh-log").addEventListener("click", () => {
  renderLog();
  toast("日志已刷新");
});

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

/* v5.8.0：顶部成功横幅已移除（用户实锤：上传成功后顶部不要再出弹窗——
 * 只要一个确认弹窗就够用了，防呆单一反馈通道）。 */

/* 三重反馈之一：全屏弹窗确认 */
/* v5.8.0 唯一确认弹窗：一个 ✓、一行结果、一个「好的」——零跳转零其他反馈 */
function showUploadDoneModal(number) {
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
    + '<div style="font-size:13px;color:#5b6b60;margin:8px 0 14px">日志已提交 GitHub Issue #' + esc(number) + '</div>'
    + '<button data-act="close" style="width:100%;border:0;border-radius:10px;background:#10B981;'
    + 'color:#fff;font-size:14px;font-weight:600;padding:11px 0">好的</button></div>';
  document.body.appendChild(wrap);
  wrap.addEventListener("click", (ev) => {
    const act = ev.target.getAttribute && ev.target.getAttribute("data-act");
    if (act === "close" || ev.target === wrap) closeModal();
  });
}
function closeModal() {
  const m = $("#upload-done-modal");
  if (m) m.remove();
}

/* v5.8.0：系统通知仅保留失败路径（成功已有唯一确认弹窗——单一反馈通道防呆；
 * 失败通知在用户离开 App 时仍可召回，是罕见场景兜底） */
/* v7.15.6 冗余清理：仅剩失败路径（成功通知已随单一反馈通道移除），
 * ok/number 形参随之移除（唯一调用点只走失败分支）。 */
function notifyUploadResult() {
  if (!HAS_BRIDGE) return;
  try {
    AndroidApp.postNotify(
      "日志上传失败",
      "本次上传未完成，可回到「我的 → 引擎日志」重试",
      false);
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
  /* v7.15.0：异步桥（后台线程直发 GitHub Issue——卡死根治 + 超时快失败） */
  let res = null;
  try { res = await netCall("netPostIssueAsync", title, body); } catch (e) { res = { ok: false, error: "上传异常" }; }
  if (!res || typeof res !== "object") { try { res = JSON.parse(res); } catch (e) { res = { ok: false, error: "上传异常" }; } }
  resetUploadBtn(btn);

  if (res && res.ok) {
    box.className = "fb-result ok";
    box.textContent = "已上传 Issue #" + res.number;
    showUploadDoneModal(res.number);               // 唯一反馈：一个确认弹窗（v5.8.0）
  } else {
    showUploadFail(box, res && res.error);          // 明确原因 + 重试按钮
    notifyUploadResult();                          // 失败通知保留（罕见场景兜底）
  }
}

$("#btn-upload-log").addEventListener("click", () => { void doUploadLog(); });

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

/* v7.2.0 刷新语义：每次全量重渲染（折叠态回默认收起）；函数内零 toast
 * （提示一律由调用方决定）。v7.15.6 冗余清理：refresh 形参从未使用，移除。 */
function renderReport() {
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
  /* v7.15.5 订阅瘦身：市场源零命中规则自动停用（使用报告可见——命中过
   * 的永不自动停用；30 天复评给日更规则二次机会） */
  secSub.push(reportLine("订阅瘦身（自动）", (sub.prunedApps || 0) > 0
    ? "已自动停用 " + (sub.prunedApps || 0) + " 款零命中应用的市场规则（内置规则不受影响；30 天后自动复评）"
    : "未触发（市场规则评估 ≥300 次仍零命中才停用；命中过的不停）"));
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
  $("#btn-report").addEventListener("click", () => { renderReport(); toast("报告已刷新"); });
}

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
  // v7.15.0：切回 App（进入）时也重新评估开屏提示弹窗（12h 窗/版本钉——v7.15.4 由 24h 收紧）
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      refreshHome();
      renderKeepAlive();
      if (tipDue()) showSplashTip();
    }
  });
  // 通知权限（Android 13+，保活提醒需要）
  // v7.0.0：启动静默更新检查已移除（顶部更新横幅整体取消——只有手动
  // 点击「检查更新」才查询/下载，启动零网络零提示）。
  // 每 5s 刷新首页状态（首次进入最关心）
  setInterval(() => {
    if ($("#page-home").classList.contains("active")) {
      refreshHome();
      renderKeepAlive();
    }
  }, 5000);
  // v7.15.0：进入 App 即弹开屏提示（三按钮——12 小时窗口 + 升级重新提醒）
  setTimeout(() => { if (tipDue()) showSplashTip(); }, 450);
  // v7.0.0：初始化完成哨兵（index.html 内联保险丝在 5s 后检查——
  // app.js 早期崩溃时不再「检查中/加载中」永久占位，给出可操作指引）
  window.__appBootOk = true;
})();
