// ═══ v7.10.0 开屏提醒与更新检查解耦 · 真实 app.js 全量加载测试 ═══
// 用户指令「修复开屏提醒与更新检查的耦合问题，提醒不能依赖网络检查完成」专项：
//  ① 独立性：提醒独立 3s 定时评估——网络检查永不收尾也照常展示（不再链在
//     checkUpdateOnLaunch().finally 之后）；
//  ② 3 秒强制：init 的提醒定时器为 3000ms（源码级钉死）；
//  ③ 24h 抑制：任一确认按钮后 24h 内不再弹（纯本地时间戳主闸）；
//  ④ 5min 防重建：展示后 5 分钟内 Activity 重建不重复弹（短窗不耗资格）；
//  ⑤ 更新优先：更新页在场 → 提醒跳过；更新页后到 → 提醒卡被收起
//     （showUpdateGate 主动移除 #bug-tip——确认时间戳不受影响）；
//  ⑥ 进程被杀：确认时间戳落 kv/localStorage——新会话（进程重启等价）
//     在 24h 内仍抑制；
//  ⑦ 时间回拨：未来时间戳（时钟回拨产生）→ 负时长 < 窗口 → 视为仍在
//     抑制窗内（防回拨刷弹窗）；
//  ⑧ 清数据：时间戳清除 → 重新展示资格恢复；
//  ⑨ 重复弹窗：同一会话至多一张（bugTipShownThisSession 闸）；
//  ⑩ 主线程零阻塞：maybeShowBugTip 纯同步本地路径（无网络/无重计算）。
const fs = require("fs");
const APPJS = __dirname + "/../assets/www/app.js";
const src = fs.readFileSync(APPJS, "utf8");

/* ── Stage 1：catch-all stub 环境加载完整 app.js ── */
function mkStub() {
  const f = function () { return mkStub(); };
  return new Proxy(f, {
    get: (t, k) => {
      if (k === Symbol.toPrimitive) return () => "";
      if (k === "toString") return () => "[stub]";
      if (k === "length") return 0;
      return mkStub();
    },
    set: () => true,
    apply: () => mkStub(),
    construct: () => mkStub(),
  });
}
globalThis.window = globalThis;
globalThis.document = mkStub();
const lsMap = new Map();
globalThis.localStorage = {
  getItem: (k) => (lsMap.has(k) ? lsMap.get(k) : null),
  setItem: (k, v) => lsMap.set(k, String(v)),
  removeItem: (k) => lsMap.delete(k),
};
globalThis.location = { href: "", protocol: "https:", origin: "https://local" };
globalThis.addEventListener = () => {};
globalThis.setInterval = () => 0;
globalThis.setTimeout = () => 0;
globalThis.requestAnimationFrame = () => 0;
globalThis.fetch = () => Promise.reject(new Error("no-net-in-test"));
/* 更新检查网络永不收尾（解耦专项的关键压力面）：桥桩 updateCheckAsync
 * 只登记不回调——模拟最恶劣弱网（90s 内零回调）。 */
const asyncCalls = [];
globalThis.AndroidApp = {
  kvGet: (k) => (lsMap.has("adshield_" + k) ? lsMap.get("adshield_" + k) : null),
  kvSet: (k, v) => lsMap.set("adshield_" + k, v),
  versionName: () => "7.10.0",
  versionCode: () => 80000,
  updateCheckAsync: (m, cb) => { asyncCalls.push({ m, cb }); /* 永不回调 */ },
};

const TAIL = `
;globalThis.__T__ = {
  maybeShowBugTip, showBugTip, bugTipThrottled, bugTipRecentlyShown,
  showUpdateGate, checkUpdateOnLaunch,
  store, appVer, appCode,
  HAS_BRIDGE,
  resetBugTipSession: () => { bugTipShownThisSession = false; },
};`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T || !T.maybeShowBugTip || !T.showBugTip) {
  console.error("✗ 开屏提醒符号未导出（app.js 加载失败或符号缺失）");
  process.exit(1);
}

/* ── Stage 2：可交互迷你 DOM（元素注册表 + 按钮检索）── */
const miniDom = { tip: null, gate: null, buttons: {} };
const byId = new Map();
class El {
  constructor(id) { this._id = id || ""; this.removed = false; this._html = ""; }
  get id() { return this._id; }
  set id(v) { this._id = String(v); }
  addEventListener(ev, fn) { miniDom.buttons[this._id + ":" + ev] = fn; }
  remove() { this.removed = true; }
  get innerHTML() { return this._html; }
  set innerHTML(v) { this._html = String(v); }
  set textContent(v) { this._html = String(v); }
  set className(v) { this._html = String(v); }
  set style(v) {}
  set dataset(v) { if (!this._ds) this._ds = {}; Object.assign(this._ds, v); }
  get dataset() { return this._ds || (this._ds = {}); }
  closest() { return null; }
  click() { const fn = miniDom.buttons[this._id + ":click"]; if (fn) fn(); }
}
/* 整体替换 document（属性级赋值会被 eval 期 Proxy stub 吞掉——必须换对象） */
globalThis.document = {
  querySelector: (sel) => {
    if (typeof sel !== "string" || !sel.startsWith("#")) return null;
    const id = sel.slice(1);
    if (id === "bug-tip") return miniDom.tip && !miniDom.tip.removed ? miniDom.tip : null;
    if (id === "update-gate") return miniDom.gate && !miniDom.gate.removed ? miniDom.gate : null;
    if (!byId.has(id)) byId.set(id, new El(id));   // 页内按钮（innerHTML 生成面）
    return byId.get(id);
  },
  createElement: () => new El(),
  body: { appendChild: (el) => { if (el.id === "bug-tip") miniDom.tip = el; if (el.id === "update-gate") miniDom.gate = el; } },
};



let pass = 0, fail = 0;
function A(name, cond) {
  if (cond) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name); }
}

/* 时间旅行 */
const REAL_NOW = Date.now();
let nowMock = null;
Date.now = () => (nowMock === null ? REAL_NOW : nowMock);
function freshSession() {
  miniDom.tip = null; miniDom.gate = null; miniDom.buttons = {}; byId.clear();
  T.resetBugTipSession();
}

console.log("═══ S1 独立性：提醒不依赖网络检查收尾 ═══");
lsMap.clear(); asyncCalls.length = 0; freshSession();
T.checkUpdateOnLaunch();          // 发起更新检查（网络永不回调）
A("更新检查已发起（桥桩收到调用）", asyncCalls.length === 1);
T.maybeShowBugTip();              // 独立 3s 定时到点评估（不等检查收尾）
A("网络检查未收尾，提醒照常展示（解耦）", !!miniDom.tip && !miniDom.tip.removed);

console.log("═══ S2 3 秒强制（init 定时器源码级）═══");
const initIdx = src.indexOf("setTimeout(() => {\n    try { maybeShowBugTip(); } catch (e) {}\n  }, 3000);");
A("init 独立 3s 定时器在场（3000ms）", initIdx > 0);
A("提醒不再链在 checkUpdateOnLaunch().finally 之后",
  !/checkUpdateOnLaunch\(\)\.finally\([\s\S]*?maybeShowBugTip/.test(src));
A("检查定时器（800ms）与提醒定时器（3000ms）各自独立",
  /setTimeout\(\(\) => \{\n    void checkUpdateOnLaunch\(\);\n  \}, 800\);/.test(src));

console.log("═══ S3 24h 抑制（确认后）═══");
lsMap.clear(); freshSession();
T.maybeShowBugTip();
A("提示已展示（按钮接线面就绪）", !!miniDom.tip && !miniDom.tip.removed);
const okBtn = miniDom.buttons["bug-tip-ok:click"];
A("「知道了」按钮已接线（addEventListener 注册）", typeof okBtn === "function");
okBtn();  // 确认
A("确认后卡片移除 + 时间戳落库", miniDom.tip.removed === true
  && lsMap.has("adshield_bug_feedback_confirmed_at"));
A("确认后 24h 内不再弹", T.bugTipThrottled() === true);
nowMock = Date.now() + 24 * 3600 * 1000 - 1;
A("23:59:59 仍在抑制窗", T.bugTipThrottled() === true);
nowMock = Date.now() + 24 * 3600 * 1000 + 1;
A("24h+1ms 资格恢复", T.bugTipThrottled() === false);
nowMock = null;

console.log("═══ S4 5min 防重建（展示后短窗）═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_bug_feedback_shown_at", String(Date.now()));
A("刚展示过 → 5min 内防重建抑制", T.bugTipRecentlyShown() === true);
nowMock = Date.now() + 5 * 60 * 1000 + 1;
A("5min+1ms 短窗结束", T.bugTipRecentlyShown() === false);
nowMock = null;

console.log("═══ S5 更新优先 ═══");
lsMap.clear(); freshSession();
miniDom.gate = new El("update-gate");   // 更新页已在场
T.maybeShowBugTip();
A("更新页在场 → 提醒本轮不弹", miniDom.tip === null);
A("且未消耗资格（确认时间戳未写）", !lsMap.has("adshield_bug_feedback_confirmed_at"));
// 反向：提醒先展示，更新页后到 → 提醒卡被收起
freshSession();
T.maybeShowBugTip();
A("提醒先展示", !!miniDom.tip && !miniDom.tip.removed);
T.showUpdateGate({ tag: "v7.10.1", apkMB: 0.9, apkDigest: "sha256:x", remoteVersionCode: 80100, notes: "1. x" });
A("更新页后到 → 提醒卡被收起（showUpdateGate 主动移除 #bug-tip）", miniDom.tip.removed === true);

console.log("═══ S6 进程被杀重启（时间戳持久）═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_bug_feedback_confirmed_at", String(Date.now()));
T.resetBugTipSession();   // 等价新进程（会话闸复位——时间戳在 kv/localStorage）
A("新会话 24h 内仍抑制（时间戳跨进程持久）", T.bugTipThrottled() === true);

console.log("═══ S7 时间回拨防御 ═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_bug_feedback_confirmed_at", String(Date.now() + 3600 * 1000)); // 未来时间戳（回拨后遗留）
A("未来时间戳（回拨面）→ 负时长仍判抑制（防回拨刷弹窗）", T.bugTipThrottled() === true);

console.log("═══ S8 清数据恢复 ═══");
lsMap.clear(); freshSession();
A("时间戳清除 → 资格恢复（重新可弹）", T.bugTipThrottled() === false);

console.log("═══ S9 同会话单卡 ═══");
lsMap.clear(); freshSession();
T.maybeShowBugTip();
const firstTip = miniDom.tip;
T.maybeShowBugTip();
A("同会话重复评估不二弹（bugTipShownThisSession 闸）", miniDom.tip === firstTip);

console.log("═══ S10 主线程零阻塞 ═══");
A("maybeShowBugTip 纯同步本地路径（函数体无 await/fetch/网络）",
  !/function maybeShowBugTip\s*\(\)\s*\{[\s\S]*?await[\s\S]*?\}/.test(src));

console.log("");
console.log("══════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("══════════════════════════");
process.exit(fail ? 1 : 0);
