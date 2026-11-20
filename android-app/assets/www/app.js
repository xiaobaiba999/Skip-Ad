/* 开屏卫士 v7.8.0 · 视频开屏卫士 + 插屏重扫路由根治 + 矩阵 396（十维） */
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
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "7.8.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "78000"; }

/* ══════════ 吉祥物（v4.1.0 全新形象：白机器人小侠 + 绿披风跳跃） ══════════
 * 与启动器图标同源；state: on（跳跃浮动）/ off（灰度待命） */
function mascotImg(size, state, head) {
  const on = state !== "off";
  return `<img src="${head ? "mascot-head.png" : "mascot.png"}" width="${size}" height="${size}" alt="吉祥物" class="${on ? "on" : "off"}" draggable="false">`;
}

$("#brand-logo").innerHTML = mascotImg(30, "on", true);

/* ═══ v7.8.0 版本记录（视频开屏卫士 + 插屏重扫路由根治 + 矩阵 396 十维）═══ */
const CURRENT_RELEASE = {
  date: "2026-11-20",
  title: "视频开屏卫士 + 插屏重扫路由根治 + 机型矩阵 396（十维）",
  features: [
    "视频开屏卫士（用户实锤「只要是开屏广告带视频的你都未命中」）：视频画像（窗龄+零候选连环+树静默）→ 观察窗 12s→20s + 1.5s 轮询针（视频播放期零事件兜底）+ 白点节流 1.5s/击 + 预算 3→5 + 新键预算刷新（视频末露头真按钮 ≠ 白点）+ L4 形态门放宽（WebView 容器 260 树）",
    "插屏重扫路由根治（用户实锤「app 内插入广告也不会拦截关闭」）：重扫路由黑洞解除（开屏窗期/弹窗期内容事件同步拉起插屏扫描）+ 首扫延后复扫 +450ms（树未长成再看一眼）+ 补订 typeWindowsChanged（无焦点 Dialog/overlay 插层挂载事件）",
    "插屏闸扩容：闸⓪语境三路并集（语境词 16 字上限 + WebView 容器铁证 + SDK 插屏窗指纹）+ 关闭词族收编（拉丁 X 单字符/点击关闭/立即关闭）+ 宽扁钮形态放行（≤55%×≤7% 横条钮）+ 终闸双杀解除 + feed 名单 20 款插屏孤儿化解除",
    "C 语言基准实测（用户问「用 C 写会不会更好」）：五语言同构基准（校验和逐位一致）——C 稳态匹配 261.8ns 比 Java 快 4.1×，但都是 µs 级：事件面 80ms 节流下 5 个数量级余量，语言性能对命中率零贡献；AccessibilityNodeInfo 是 Java API，C 必须经 JNI 回 Java——保持 Java",
    "机型矩阵 352→396（每品牌 +4 · 2027-2028 世代）+ 第十维视频开屏逐机型验证 + 23 套件 6063 项断言全绿（+964）· APK 832KB",
  ],
  summary: "用户十八轮实锤两个死症：①开屏广告带视频的全部未命中——四死因链（12s 观察窗天花板：视频开屏跳过钮常在视频末 5~15s 才露头，>12s 按钮引擎已出窗判 CLEAN「不再扫」；静默事件源：SurfaceView 视频播放零内容事件，零候选退避杀链后零事件零复活；预算烧穿：图片键门控期 3 击 1 秒烧光预算，按钮真激活时引擎已优雅收手；L4 形态确认门：WebView 视频容器树 >150 被拒）→ VideoSplashGuard 纯函数根治（画像驱动：观察窗 20s + 轮询针 1.5s + 白点节流 + 新键刷新 + 树上限 260）+ 视频开屏键词典（「5s 跳过」无「后」变体收编）。②app 内插入广告不拦截关闭（第 N 轮）——五死因链（重扫路由黑洞：开屏窗期内容事件全改道 nudgeScan，插屏常在 App 打开后 3~5s 弹出恰在黑洞内，首扫一眼后整个生命周期无人再看；首扫太早：窗切换帧树未长成空手后无复扫；同窗插层无人扫：无焦点 Dialog/overlay 挂载不产生任何被订阅事件；闸⓪语境面窄：H5 插屏角标在网页内核非原生节点、SDK 插屏窗类即铁证全被拒；闸①②杀变体：拉丁 X/点击关闭/宽扁横条钮被双杀）→ 路由黑洞解除 + 延后复扫 + typeWindowsChanged 补订 + 语境三路并集 + 词族收编 + 宽扁放行 + 终闸一致性 + feed 名单孤儿化解除。C 语言问题以五语言同构基准实测回答（校验和逐位一致：sel_sink=731/hits=20000/sink=485562）。机型矩阵 352→396 + 第十维视频开屏 + 23 套件 6063 断言全绿。",
};

/* v7.0.0 新版功能描述（规范：≤5 个标题、带序号；检查更新发现新版本时就地渲染）。
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

const GH_API = "https://api.github.com/repos/xiaobaiba999/Skip-Ad";
let ubTimer = null;
let latestRelease = null;
let dl = { channels: [], idx: 0, fileName: "", digest: "", size: 0, prefix: "page", lastBytes: -1, lastMoveAt: 0 };

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
        notes: j.body || "", // v6.1.0：新版功能描述（浏览器预览路径）
        apkUrl: apk ? apk.browser_download_url : "",
        apkMirrors: apk ? ["https://ghfast.top/" + apk.browser_download_url, "https://gh-proxy.com/" + apk.browser_download_url] : [],
        apkFileName: apk ? apk.name : "adshield.apk",
        apkMB: apk ? Math.round(apk.size / 1048576 * 100) / 100 : 0,
        apkDigest: apk ? (apk.digest || "") : "", // v7.3.0：GitHub API SHA-256（安装前校验）
        apkSize: apk ? (apk.size || 0) : 0,
      };
    }
    return { ok: false, error: "GitHub " + r.status };
  } catch (e) { return { ok: false, error: "网络异常" }; }
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
       * prefix+"-btn" 永不命中，下载完成后按钮从不翻转「立即安装」） */
      const btn = $("#" + dl.prefix + "-dl-btn");
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
  dl = { channels: [d.apkUrl].concat(d.apkMirrors || []), idx: 0, fileName: d.apkFileName || "adshield.apk", digest: d.apkDigest || "", size: d.apkSize || 0, prefix, lastBytes: -1, lastMoveAt: Date.now() };
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
        txt.textContent = "下载完成（已验 SHA-256），点击安装即覆盖升级";
        const btn = $("#" + prefix + "-dl-btn");
        if (btn) { btn.textContent = "立即安装"; btn.disabled = false; btn.dataset.phase = "install"; }
      } else if (d.state === "failed") {
        clearInterval(ubTimer); ubTimer = null;
        if (switchChannel("下载失败")) return;
        txt.textContent = "下载失败，可稍后重试";
        const btn = $("#" + prefix + "-dl-btn");
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
  if (!d.ok) { note.className = "fb-result err"; note.style.display = ""; note.textContent = "查询失败：" + (d.error || "未知"); return; }
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
function notifyUploadResult(ok, number) {
  if (!HAS_BRIDGE || ok) return;
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
  let res = null;
  try { res = JSON.parse(AndroidApp.netPostIssue(title, body)); } catch (e) { res = { ok: false, error: "上传异常" }; }
  resetUploadBtn(btn);

  if (res && res.ok) {
    box.className = "fb-result ok";
    box.textContent = "已上传 Issue #" + res.number;
    showUploadDoneModal(res.number);               // 唯一反馈：一个确认弹窗（v5.8.0）
  } else {
    showUploadFail(box, res && res.error);          // 明确原因 + 重试按钮
    notifyUploadResult(false, 0);                  // 失败通知保留（罕见场景兜底）
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
  // v7.0.0：启动静默更新检查已移除（顶部更新横幅整体取消——只有手动
  // 点击「检查更新」才查询/下载，启动零网络零提示）。
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
