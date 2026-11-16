/* 开屏卫士 v3.11.0 · 离线逻辑（首页 / 学习中心 / 关于 · 应用内页内直更 + 多通道下载） */
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

/* ══════════ 静态数据（v3.11.0 · 精简重构：每版一句话） ══════════ */

/* 最近三个版本 */
const CHANGELOG = [
  { v: "3.11.0", d: "链式回弹用户意图安全门（issue#13 实锤修复）：你 2.2s 内刚点过（分享/登录按钮）、系统分享选择器刚出现过、同一目标刚弹回又立刻拉起（你的重试）——三重任一命中即放行，绝不与你对抗；点商品/文章详情跳内置页不再被误弹回；分享面板噪音归零；学习规则明细撤出界面（学习引擎照常工作，界面更清爽）。" },
  { v: "3.10.0", d: "内置规则库大扩充 + 界面精简重构：五层全局规则（19 条精确文案大小写归一 / 倒计时形态严格正则——营销话术数学上无法混入 / 短包含 8 字上限 + 17 诱导词否决 / vid skip 族 + SDK 窗 close 族边缘带）+ 28 款主流应用分应用规则表；桌面黑名单与广告窗指纹扩充；UI 去除大段说明文字，页签精简为三页。" },
  { v: "3.9.0", d: "QQ/微信/抖音「进去就被退出来」根因修复（桌面误判→盲点坐标→回弹踢出，三刀根治）；内嵌规则引擎主路径化，事件驱动 0ms 露头秒跳；扫描链前段加密与点击冷却提速。" },
];

/* 内置规则库概览（首页防护能力卡） */
const RULES_INFO = "五层全局规则 + 28 款分应用表";

/* ══════════ 路由 ══════════ */
$$(".tabbtn").forEach(b => b.addEventListener("click", () => {
  $$(".tabbtn").forEach(x => x.classList.remove("active"));
  b.classList.add("active");
  $$(".page").forEach(p => p.classList.remove("active"));
  $("#page-" + b.dataset.page).classList.add("active");
  const pg = b.dataset.page;
  $("#brand-sub").textContent = { drill: "秒跳引擎 · 首页", learn: "习惯学习 · 能耗", about: "版本与更新" }[pg];
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
  if (running) { reviveStopRetry = false; reviveHealing = false; } // v3.8.0：已恢复即清除待恢复标记
  let statusText = "浏览器预览模式";
  if (running) statusText = "秒跳服务运行中";
  else if (HAS_BRIDGE) {
    if (!killed) statusText = "服务未开启";
    else if (reviveStopRetry) statusText = "服务待恢复 · 一步直达开关";       // v3.8.0 need_toggle
    else if (reviveHealing) statusText = "服务被系统关闭（自愈重绑进行中…）"; // v3.8.0 heal_pending
    else statusText = "服务被系统关闭（自动恢复中…）";
  }
  $("#service-status").textContent = statusText;
  $("#service-desc").textContent = running
    ? "内嵌规则引擎 · 露头就秒跳 · 控件中心精准点击 · 桌面黑名单 · 自愈重绑"
    : (HAS_BRIDGE ? "开启无障碍后自动秒跳任何应用开屏（规则内嵌，零云端）" : "安装 APK 后体验真实秒跳服务");
  dot.classList.toggle("on", running);
  card.classList.toggle("ok", running);
  /* v3.8.0：need_toggle（仍登记但进程重绑未成）时也展示「一步直达」按钮，隐藏不会自愈的重试按钮 */
  $("#btn-open-a11y").style.display = (HAS_BRIDGE && !running && (!killed || reviveStopRetry)) ? "" : "none";
  const rv = $("#btn-revive");
  if (rv) rv.style.display = (HAS_BRIDGE && !running && killed && !reviveStopRetry) ? "" : "none";
  /* v3.7.0：自动拉活改为循环重试（每 5s 一次直到成功）——此前仅尝试一次，
   * 部分 ROM 的 AMS 重绑有延迟，一次失败就放弃导致「又要手动去设置」
   * v3.8.0：need_toggle 后停止自动重试（重绑不成不会自愈，别空转）——
   * 1.6s 周期仅刷新 isServiceRunning 状态，不再自动调 tryAutoRevive */
  if (HAS_BRIDGE && !running && killed && !reviveStopRetry) {
    const now = Date.now();
    if (!reviveLastTry || now - reviveLastTry > 5000) {
      reviveLastTry = now;
      autoRevive(false);
    }
  }
  refreshKeep(running);
  refreshBatteryBanner(running);
  refreshAdbGrant();
  const rt = $("#rules-tag");
  if (rt) rt.textContent = "v" + appVer() + " · " + RULES_INFO;
}

/* v3.7.0 电池白名单醒目横幅（issue#10 日志实锤「电池白名单：未加入」） */
function refreshBatteryBanner(running) {
  const banner = $("#battery-banner");
  if (!banner) return;
  if (!HAS_BRIDGE) { banner.style.display = "none"; return; }
  let bat = false;
  try { bat = AndroidApp.isBatteryWhitelisted(); } catch (e) {}
  banner.style.display = (!bat && running) ? "" : "none";
}

/* v3.6.0 自动拉活：打开 App 即尝试，失败每 5s 重试（v3.7.0 循环版；v3.8.0 按 reason 分流） */
let reviveLastTry = 0;
let reviveStopRetry = false; // v3.8.0 need_toggle：仍登记但进程重绑未成——不会自愈，停止自动重试免空转
let reviveHealing = false;   // v3.8.0 heal_pending：自愈重绑已发起待生效——保持每 5s 自动重试
function autoRevive(loud) {
  if (!HAS_BRIDGE) return;
  try {
    const r = JSON.parse(AndroidApp.tryAutoRevive());
    if (r.running || r.revived) {
      reviveLastTry = 0;
      reviveStopRetry = false;
      reviveHealing = false;
      toast(r.revived || r.selfHeal ? "引擎已自动恢复（系统设置自愈），无需任何操作" : "引擎已自动恢复，无需任何操作");
      refreshHome();
    } else if (r.reason === "need_toggle") {
      /* 仍登记但进程重绑未成：自动重试不会自愈——引导一步直达服务开关（学习数据全保留） */
      if (loud || !reviveStopRetry) toast("点下方按钮一步直达服务开关（Android 11+），学习数据全保留");
      reviveStopRetry = true;
      reviveHealing = false;
      refreshHome();
    } else if (r.reason === "heal_pending") {
      /* WRITE_SECURE_SETTINGS 自愈已发起待生效：保持自动重试直至恢复 */
      if (loud || !reviveHealing) toast("自愈重绑进行中…");
      reviveHealing = true;
      reviveStopRetry = false;
      refreshHome();
    } else if (loud) {
      toast(r.reason === "disabled"
        ? "服务未在系统登记——点「开启无障碍服务（一步直达）」开启（学习数据全保留）"
        : "自动恢复未成功——点「开启无障碍服务（一步直达）」（学习数据全保留）");
    }
  } catch (e) { if (loud) toast("恢复失败，请去无障碍设置开启"); }
}

/* v3.8.0 ADB 自愈授权状态（WRITE_SECURE_SETTINGS：授权后清理后台/覆盖安装的无障碍关闭全自动恢复） */
function refreshAdbGrant() {
  const box = $("#adb-box");
  if (!box) return;
  if (!HAS_BRIDGE || typeof AndroidApp.hasSecureWrite !== "function") { box.style.display = "none"; return; }
  let granted = false;
  try { granted = !!AndroidApp.hasSecureWrite(); } catch (e) {}
  if (granted && reviveStopRetry) reviveStopRetry = false; // 刚授权：恢复自动重试，自愈即刻生效
  const tag = $("#adb-tag");
  if (tag) {
    tag.textContent = granted ? "已授权 · 全自动自愈" : "未授权";
    tag.className = "tag " + (granted ? "tag-green" : "");
  }
  const okDesc = $("#adb-ok-desc"), gbody = $("#adb-grant-body");
  if (okDesc) okDesc.style.display = granted ? "" : "none";
  if (gbody) gbody.style.display = granted ? "none" : "";
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
  const bindBattery = (id) => {
    const el = $("#" + id);
    if (!el) return;
    el.addEventListener("click", () => {
      try {
        const r = JSON.parse(AndroidApp.requestBatteryWhitelist());
        toast(r.already ? "已在白名单中" : (r.ok ? "已跳转系统白名单页，请允许「开屏卫士」" : "当前系统不支持，可到设置→电池手动处理"));
      } catch (e) { toast("跳转失败"); }
    });
  };
  bindBattery("btn-battery");
  bindBattery("btn-battery-big"); // v3.7.0 醒目横幅大按钮

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
    try {
      /* v3.8.0：优先直达本服务无障碍详情页（Android 11+ 一个开关一步恢复；老 APK 无此方法或旧系统自动回退列表页） */
      if (typeof AndroidApp.openA11yDetail === "function") AndroidApp.openA11yDetail();
      else AndroidApp.openAccessibilitySettings();
    } catch (e) { try { AndroidApp.openAccessibilitySettings(); } catch (e2) {} }
  });
  const btnRevive = $("#btn-revive");
  if (btnRevive) btnRevive.addEventListener("click", () => autoRevive(true));
  setInterval(refreshHome, 1600);
}

/* ══════════ 防护开关（学习 / 弹窗屏蔽 / 跳转回弹） ══════════ */
function renderToggles() {
  const st = getRealState();
  $("#toggle-learning").checked = st.learning !== false;
  $("#toggle-popup").checked = st.popupShield === true; // v3.9.0 默认关闭（issue#12 内容突发误报整治），学习中心可开启
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
bindToggle("toggle-popup", "popupShield", "弹窗广告屏蔽已开启（默认关闭防误判，可随时关）", "弹窗广告屏蔽已关闭（默认状态：只跳开屏，绝不乱点弹窗）");
bindToggle("toggle-chain", "chainRebound", "诱导跳转回弹已开启", "诱导跳转回弹已关闭");

/* ══════════ 学习中心（v3.11.0：学习规则明细撤出界面——引擎照常学习，
   界面只留分应用统计与引擎日志；明细仅随日志上传进 GitHub 诊断） ══════════ */
function renderReal() {
  const st = getRealState();

  if (!HAS_BRIDGE) {
    $("#byapp-list").innerHTML = `<div class="log-box" style="margin-top:8px"><div class="empty">浏览器预览模式<br>安装 APK 后这里展示真实统计与日志</div></div>`;
    $("#real-log").innerHTML = "";
    return;
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
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "3.11.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "31100"; }

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
  renderToggles();
  renderAbout();
  refreshHome();
  renderReal();
  renderEnergy();
  renderFbHistory();
})();
