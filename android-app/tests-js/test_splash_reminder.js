// ═══ v7.11.0 开屏提醒提速 · 真实 app.js 全量加载测试 ═══
// 用户指令「开屏提醒速度一定要快，要显示把 app 固定在后台，进入 app 就显示，
// 然后可以选择 24 小时之内不再显示」专项：
//  ① 进入即显：init 直调 maybeShowPinTip（0ms——不再有 3s 定时器）；
//  ② 内容钉死：提醒卡文案包含「固定在后台」引导（源码级断言）；
//  ③ 24h 抑制：「24 小时内不再显示」确认后 24h 内不再弹（纯本地时间戳主闸）；
//  ④ 「知道了」不耗资格：仅本轮收起——下轮评估资格保持；
//  ⑤ 5min 防重建：展示后 5 分钟内 Activity 重建不重复弹（短窗不耗资格）；
//  ⑥ 更新优先：更新页在场 → 提醒跳过；更新页后到 → 提醒卡被收起
//     （showUpdateGate 主动移除 #pin-tip——确认时间戳不受影响）；
//  ⑦ 进程被杀：确认时间戳落 kv/localStorage——新会话在 24h 内仍抑制；
//  ⑧ 时间回拨：未来时间戳 → 负时长 < 窗口 → 视为仍在抑制窗内；
//  ⑨ 清数据：时间戳清除 → 重新展示资格恢复；
//  ⑩ 重复弹窗：同一会话至多一张（pinTipShownThisSession 闸）；
//  ⑪ 主线程零阻塞：maybeShowPinTip 纯同步本地路径（无网络/无重计算）；
//  ⑫ 与更新检查彻底无关：网络检查永不收尾也照常展示（v7.10.0 语义保持）。
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
/* 更新检查网络永不收尾（解耦压力面）：桥桩 updateCheckAsync 只登记不回调。 */
const asyncCalls = [];
globalThis.AndroidApp = {
  kvGet: (k) => (lsMap.has("adshield_" + k) ? lsMap.get("adshield_" + k) : null),
  kvSet: (k, v) => lsMap.set("adshield_" + k, v),
  versionName: () => "7.11.0",
  versionCode: () => 81000,
  updateCheckAsync: (m, cb) => { asyncCalls.push({ m, cb }); /* 永不回调 */ },
};

const TAIL = `
;globalThis.__T__ = {
  maybeShowPinTip, showPinTip, pinTipThrottled, pinTipRecentlyShown,
  showUpdateGate, checkUpdateOnLaunch,
  store, appVer, appCode,
  HAS_BRIDGE,
  resetPinTipSession: () => { pinTipShownThisSession = false; },
};`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T || !T.maybeShowPinTip || !T.showPinTip) {
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
  get classList() { return { add() {}, remove() {}, toggle() {} }; }
  get offsetWidth() { return 0; }
  click() { const fn = miniDom.buttons[this._id + ":click"]; if (fn) fn(); }
}
/* 整体替换 document（属性级赋值会被 eval 期 Proxy stub 吞掉——必须换对象） */
globalThis.document = {
  querySelector: (sel) => {
    if (typeof sel !== "string" || !sel.startsWith("#")) return null;
    const id = sel.slice(1);
    if (id === "pin-tip") return miniDom.tip && !miniDom.tip.removed ? miniDom.tip : null;
    if (id === "update-gate") return miniDom.gate && !miniDom.gate.removed ? miniDom.gate : null;
    if (!byId.has(id)) byId.set(id, new El(id));   // 页内按钮（innerHTML 生成面）
    return byId.get(id);
  },
  createElement: () => new El(),
  body: { appendChild: (el) => { if (el.id === "pin-tip") miniDom.tip = el; if (el.id === "update-gate") miniDom.gate = el; } },
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
  T.resetPinTipSession();
}

console.log("═══ S1 进入即显：提醒不依赖网络、不等定时器 ═══");
lsMap.clear(); asyncCalls.length = 0; freshSession();
T.checkUpdateOnLaunch();          // 发起更新检查（网络永不回调）
A("更新检查已发起（桥桩收到调用）", asyncCalls.length === 1);
T.maybeShowPinTip();              // init 直调评估（进入即显——0ms）
A("网络检查未收尾，提醒照常展示（解耦保持）", !!miniDom.tip && !miniDom.tip.removed);

console.log("═══ S2 进入即显（源码级钉死）═══");
A("init 直调 maybeShowPinTip（无 3s 定时器——进入即显）",
  /try \{ maybeShowPinTip\(\); \} catch \(e\) \{\}/.test(src));
A("旧 3s 提醒定时器已移除（不再等 3000ms）",
  !/setTimeout\(\(\) => \{\n    try \{ maybeShowPinTip[\s\S]*?3000\);/.test(src));
A("提醒不再链在 checkUpdateOnLaunch().finally 之后",
  !/checkUpdateOnLaunch\(\)\.finally\([\s\S]*?maybeShowPinTip/.test(src));

console.log("═══ S3 内容钉死：固定后台引导 ═══");
lsMap.clear(); freshSession();
T.maybeShowPinTip();
A("提醒卡已展示", !!miniDom.tip && !miniDom.tip.removed);
A("标题包含「固定在后台」", String(miniDom.tip._html).includes("把开屏卫士固定在后台"));
A("引导包含最近任务/锁定操作", String(miniDom.tip._html).includes("最近任务") && String(miniDom.tip._html).includes("锁定"));
A("包含「24 小时内不再显示」选项", String(miniDom.tip._html).includes("24 小时内不再显示"));

console.log("═══ S4 24h 抑制（「24 小时内不再显示」确认后）═══");
lsMap.clear(); freshSession();
T.maybeShowPinTip();
A("提示已展示（按钮接线面就绪）", !!miniDom.tip && !miniDom.tip.removed);
const snoozeBtn = miniDom.buttons["pin-tip-snooze:click"];
A("「24 小时内不再显示」按钮已接线", typeof snoozeBtn === "function");
snoozeBtn();  // 确认
A("确认后卡片移除 + 时间戳落库", miniDom.tip.removed === true
  && lsMap.has("adshield_pin_tip_confirmed_at"));
A("确认后 24h 内不再弹", T.pinTipThrottled() === true);
nowMock = Date.now() + 24 * 3600 * 1000 - 1;
A("23:59:59 仍在抑制窗", T.pinTipThrottled() === true);
nowMock = Date.now() + 24 * 3600 * 1000 + 1;
A("24h+1ms 资格恢复", T.pinTipThrottled() === false);
nowMock = null;

console.log("═══ S5 「知道了」不耗资格（进入 app 就显示语义保持）═══");
lsMap.clear(); freshSession();
T.maybeShowPinTip();
const okBtn = miniDom.buttons["pin-tip-ok:click"];
A("「知道了」按钮已接线", typeof okBtn === "function");
okBtn();
A("「知道了」仅本轮收起（卡片移除）", miniDom.tip.removed === true);
A("「知道了」不写确认时间戳（下轮进入仍会提醒）",
  !lsMap.has("adshield_pin_tip_confirmed_at") && T.pinTipThrottled() === false);

console.log("═══ S6 5min 防重建（展示后短窗）═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_pin_tip_shown_at", String(Date.now()));
A("刚展示过 → 5min 内防重建抑制", T.pinTipRecentlyShown() === true);
nowMock = Date.now() + 5 * 60 * 1000 + 1;
A("5min+1ms 短窗结束", T.pinTipRecentlyShown() === false);
nowMock = null;

console.log("═══ S7 更新优先 ═══");
lsMap.clear(); freshSession();
miniDom.gate = new El("update-gate");   // 更新页已在场
T.maybeShowPinTip();
A("更新页在场 → 提醒本轮不弹", miniDom.tip === null);
A("且未消耗资格（确认时间戳未写）", !lsMap.has("adshield_pin_tip_confirmed_at"));
// 反向：提醒先展示，更新页后到 → 提醒卡被收起
freshSession();
T.maybeShowPinTip();
A("提醒先展示", !!miniDom.tip && !miniDom.tip.removed);
T.showUpdateGate({ tag: "v7.11.1", apkMB: 0.9, apkDigest: "sha256:x", remoteVersionCode: 81100, notes: "1. x" });
A("更新页后到 → 提醒卡被收起（showUpdateGate 主动移除 #pin-tip）", miniDom.tip.removed === true);

console.log("═══ S8 进程被杀重启（时间戳持久）═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_pin_tip_confirmed_at", String(Date.now()));
T.resetPinTipSession();   // 等价新进程（会话闸复位——时间戳在 kv/localStorage）
A("新会话 24h 内仍抑制（时间戳跨进程持久）", T.pinTipThrottled() === true);

console.log("═══ S9 时间回拨防御 ═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_pin_tip_confirmed_at", String(Date.now() + 3600 * 1000)); // 未来时间戳（回拨后遗留）
A("未来时间戳（回拨面）→ 负时长仍判抑制（防回拨刷弹窗）", T.pinTipThrottled() === true);

console.log("═══ S10 清数据恢复 ═══");
lsMap.clear(); freshSession();
A("时间戳清除 → 资格恢复（重新可弹）", T.pinTipThrottled() === false);

console.log("═══ S11 同会话单卡 ═══");
lsMap.clear(); freshSession();
T.maybeShowPinTip();
const firstTip = miniDom.tip;
T.maybeShowPinTip();
A("同会话重复评估不二弹（pinTipShownThisSession 闸）", miniDom.tip === firstTip);

console.log("═══ S12 主线程零阻塞 ═══");
A("maybeShowPinTip 纯同步本地路径（函数体无 await/fetch/网络）",
  !/function maybeShowPinTip\s*\(\)\s*\{[\s\S]*?await[\s\S]*?\}/.test(src));

console.log("");
console.log("══════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("══════════════════════════");
process.exit(fail ? 1 : 0);
