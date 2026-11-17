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
function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "4.3.0"; }
function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "40300"; }

/* ══════════ 吉祥物（v4.1.0 全新形象：白机器人小侠 + 绿披风跳跃） ══════════
 * 与启动器图标同源；state: on（跳跃浮动）/ off（灰度待命） */
function mascotImg(size, state, head) {
  const on = state !== "off";
  return `<img src="${head ? "mascot-head.png" : "mascot.png"}" width="${size}" height="${size}" alt="吉祥物" class="${on ? "on" : "off"}" draggable="false">`;
}

$("#brand-logo").innerHTML = mascotImg(30, "on", true);

/* ══════════ 版本记录 ══════════ */
const CHANGELOG = [
  { v: "4.3.0", d: "订阅主力规则真正接入：GKD globalGroups 全量解析（CSDN 等第三方 App 不再漏跳）+ 数字关系符/childCount/text.length 全语法；复验真正换候选（同坐标连点根治）；新增应用白名单（选中的应用零打扰）；喜马拉雅「为什么关闭广告」循环根治 + 跳过提速；未命中会记诊断日志；功耗再降（内容页早退 + 滚动风暴节流修复）。" },
  { v: "4.2.0", d: "用户操作让路（你点开的页面绝不自动关）；微信主界面零扫描（乱划根治）；订阅规则接入弹窗场景 + Activity 限定（导入了就真生效）；日志每 12 小时自动提交；引擎日志翻倍更具体；功耗大降。" },
  { v: "4.1.0", d: "应用内一键暂停/恢复防护（免看系统 10 秒警告）；修复微信拍摄被强退、灵动岛乱点、订阅导入 404；全新 Logo。" },
  { v: "4.0.0", d: "接入 GKD 订阅生态：粘贴链接即可导入 gkd.li 社区规则；内置规则库升级为「文案 + 控件 ID」双引擎；全新界面。" },
  { v: "3.14.0", d: "跳过后弹出的反馈面板自动收起；秒跳提速；开启服务一步直达。" },
];

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

function bindToggle(id, key, onMsg, offMsg) {
  const el = $("#" + id);
  el.addEventListener("change", () => {
    const st = getRealState();
    st[key] = el.checked;
    setRealState(st);
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
    tip.textContent = "应用内一键暂停/恢复，无需去系统设置";
  } else if (running) {
    card.classList.add("on");
    card.classList.remove("paused");
    mascot.innerHTML = mascotImg(88, "on");
    title.textContent = "守护中";
    sub.textContent = "广告露头就秒跳";
    pause.style.display = "";
    tip.textContent = "暂停可在应用内一键完成，无需看系统 10 秒警告";
  } else if (enabledInSettings) {
    card.classList.remove("on", "paused");
    mascot.innerHTML = mascotImg(88, "off");
    title.textContent = "服务待恢复";
    sub.textContent = "清理后台后被系统关闭";
    revive.style.display = "";
    tip.textContent = "点「一键恢复」重新拉起";
  } else {
    card.classList.remove("on", "paused");
    mascot.innerHTML = mascotImg(88, "off");
    title.textContent = "未开启";
    sub.textContent = "无障碍跳过服务";
    btn.style.display = "";
    tip.textContent = "开启一次，持续守护";
  }
}

/* v4.1.0 App 内主开关：暂停/恢复均不碰系统设置（免 10 秒警告） */
$("#btn-pause").addEventListener("click", () => {
  if (!HAS_BRIDGE) return;
  try { AndroidApp.setMasterOn(false); toast("已暂停 · 恢复时一键即回"); } catch (e) {}
  setTimeout(refreshHome, 150);
});

$("#btn-resume").addEventListener("click", () => {
  if (!HAS_BRIDGE) return;
  try { AndroidApp.setMasterOn(true); toast("防护已恢复"); } catch (e) {}
  setTimeout(refreshHome, 150);
});

$("#btn-enable").addEventListener("click", () => {
  if (HAS_BRIDGE) {
    try { AndroidApp.openA11yDetail(); return; } catch (e) {}
  }
  toast("请在系统设置中开启无障碍服务");
});

$("#btn-revive").addEventListener("click", async () => {
  if (!HAS_BRIDGE) return;
  toast("正在恢复…");
  try {
    const r = JSON.parse(AndroidApp.tryAutoRevive());
    if (r.ok) toast("已恢复守护");
    else if (r.reason === "need_toggle") {
      toast("请到系统开关打开一次");
      AndroidApp.openA11yDetail();
    } else if (r.reason === "disabled") {
      toast("服务已被关闭，请重新开启");
      AndroidApp.openA11yDetail();
    } else toast("恢复失败，请到系统设置操作");
  } catch (e) { toast("恢复失败"); }
  setTimeout(refreshHome, 800);
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
const SUB_EXAMPLE = "https://registry.npmmirror.com/@gkd-kit/subscription/latest/files/dist/gkd.json5";
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
  toast("已填入 GKD 官方订阅链接");
});

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

$("#changelog").innerHTML = CHANGELOG.map(x =>
  `<div class="log-item"><div class="log-v">v${esc(x.v)}</div><div class="log-d">${esc(x.d)}</div></div>`
).join("");

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

function engineProfile() {
  const st = getRealState();
  const s = st.stats || {};
  let rules = 0, pkgs = 0;
  try { Object.keys(st.rules || {}).forEach(k => { const n = (st.rules[k] || []).length; rules += n; if (n) pkgs++; }); } catch (e) {}
  const lines = [];
  lines.push("- 版本：v" + appVer() + " · 环境：" + (HAS_BRIDGE ? "Android APK" : "浏览器预览") + " · 手动上传");
  lines.push("- 真实引擎：秒跳 " + (s.auto || 0) + " · 弹窗关闭 " + (s.popup || 0) + " · 返回键 " + (s.back || 0)
    + " · 反馈面板收起 " + (s.feedback || 0) + " · 手动样本 " + (s.manual || 0) + " · 学习规则 " + rules + " 条（" + pkgs + " 个应用）");
  // Top5 应用战绩（秒跳/弹窗/手动）
  try {
    const byApp = s.byApp || {};
    const top = Object.keys(byApp).map(k => {
      const a = byApp[k] || {};
      return { k, score: (a.auto || 0) + (a.popup || 0) + (a.manual || 0), a };
    }).filter(x => x.score > 0).sort((x, y) => y.score - x.score).slice(0, 5);
    if (top.length) {
      lines.push("- 应用战绩 Top5：" + top.map(x => {
        const short = x.k.includes(".") ? x.k.slice(x.k.lastIndexOf(".") + 1) : x.k;
        return short + "(" + (x.a.auto || 0) + "/" + (x.a.popup || 0) + "/" + (x.a.manual || 0) + ")";
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
  // 能耗画像
  try {
    const en = JSON.parse(AndroidApp.energyStats());
    if (en && en.ok) lines.push("- 能耗画像（本次开机）：事件 " + en.events + " · 扫描 " + en.scans + " · 手势估算 " + en.microWh + "µWh");
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

$("#btn-upload-log").addEventListener("click", async () => {
  const box = $("#upload-log-result");
  if (!HAS_BRIDGE) { box.className = "fb-result err"; box.textContent = "仅 APK 内可上传"; return; }
  const btn = $("#btn-upload-log");
  box.className = "fb-result"; box.textContent = "上传中…";
  btn.disabled = true;
  let dev = {};
  try { dev = JSON.parse(AndroidApp.deviceInfo()); } catch (e) {}
  const title = "[引擎日志][v" + appVer() + "] " + (dev.brand || "") + " " + (dev.model || "设备") + "（手动上传）";
  const body = "## 设备\n- " + (dev.brand || "") + " " + (dev.model || "") + " · " + (dev.os || "") + "\n\n## 状态\n" + engineProfile();
  let res = null;
  try { res = JSON.parse(AndroidApp.netPostIssue(title, body)); } catch (e) { res = { ok: false, error: "上传异常" }; }
  btn.disabled = false;
  if (res && res.ok) {
    box.className = "fb-result ok";
    box.textContent = "已上传 Issue #" + res.number;
  } else {
    box.className = "fb-result err";
    box.textContent = (res && res.error) || "上传失败";
  }
});

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
    if ($("#page-home").classList.contains("active")) refreshHome();
  }, 5000);
})();
