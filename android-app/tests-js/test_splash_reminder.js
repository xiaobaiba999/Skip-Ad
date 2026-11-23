// ═══ v7.12.0 开屏提醒三按钮 · 真实 app.js 全量加载测试 ═══
// 用户指令「开屏提示内容（24 小时可以不显示）不要放在帮助里，每次进入
// app 时弹出提示，有三个按钮，知道了，24小时不再提示，去上传日志」专项：
//  ① 进入即显：init 直调 maybeShowPinTip（0ms——不再有 3s 定时器）；
//  ② 内容钉死：提醒卡文案包含「固定在后台」引导（源码级断言）；
//  ③ 24h 抑制：「24 小时内不再提示」确认后 24h 内不再弹（纯本地时间戳主闸）；
//  ④ 「知道了」不耗资格：仅本轮收起——下轮评估资格保持；
//  ⑤ 三按钮接线（v7.12.0）：「去上传日志」收起提醒 + 指引文案 +
//     绝不自动上传（隐私红线：仅在你主动点「一键上传」时才上传）；
//  ⑥ 60s 防重建：展示后 60 秒内 Activity 重建/旋转不重复弹（v7.11.0 的
//     5min 窗会把「退出后 1~5 分钟内再进入」也吞掉——用户指令是每次
//     进入都提示，v7.12.0 收紧为 60s）；
//  ⑦ 更新优先：更新页在场 → 提醒跳过；更新页后到 → 提醒卡被收起
//     （showUpdateGate 主动移除 #pin-tip——确认时间戳不受影响）；
//  ⑧ 进程被杀：确认时间戳落 kv/localStorage——新会话在 24h 内仍抑制；
//  ⑨ 时间回拨：未来时间戳 → 负时长 < 窗口 → 视为仍在抑制窗内；
//  ⑩ 清数据：时间戳清除 → 重新展示资格恢复；
//  ⑪ 重复弹窗：同一会话至多一张（pinTipShownThisSession 闸）；
//  ⑫ 主线程零阻塞：maybeShowPinTip 纯同步本地路径（无网络/无重计算）；
//  ⑬ 与更新检查彻底无关：网络检查永不收尾也照常展示（v7.10.0 语义保持）。
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
  goUploadLogFromTip: (typeof goUploadLogFromTip === "function" ? goUploadLogFromTip : null),
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

console.log("═══ S3 内容钉死：固定后台引导 + 三按钮 ═══");
lsMap.clear(); freshSession();
T.maybeShowPinTip();
A("提醒卡已展示", !!miniDom.tip && !miniDom.tip.removed);
A("标题包含「固定在后台」", String(miniDom.tip._html).includes("把开屏卫士固定在后台"));
A("引导包含最近任务/锁定操作", String(miniDom.tip._html).includes("最近任务") && String(miniDom.tip._html).includes("锁定"));
A("包含「24 小时内不再提示」选项（v7.12.0 措辞）", String(miniDom.tip._html).includes("24 小时内不再提示"));
A("包含「去上传日志」按钮（v7.12.0 三按钮）", String(miniDom.tip._html).includes("去上传日志"));
A("卡内文案指引「点下方去上传日志」（隐私措辞见 S6 toast）", String(miniDom.tip._html).includes("点下方「去上传日志」"));

console.log("═══ S4 24h 抑制（「24 小时内不再提示」确认后）═══");
lsMap.clear(); freshSession();
T.maybeShowPinTip();
A("提示已展示（按钮接线面就绪）", !!miniDom.tip && !miniDom.tip.removed);
const snoozeBtn = miniDom.buttons["pin-tip-snooze:click"];
A("「24 小时内不再提示」按钮已接线", typeof snoozeBtn === "function");
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

console.log("═══ S6 「去上传日志」按钮（v7.12.0 三按钮之三）═══");
lsMap.clear(); freshSession();
T.maybeShowPinTip();
const uploadBtn = miniDom.buttons["pin-tip-upload:click"];
A("「去上传日志」按钮已接线", typeof uploadBtn === "function");
uploadBtn();
A("点击后提醒卡收起（跳转不叠弹）", miniDom.tip.removed === true);
A("不写确认时间戳（跳转不耗 24h 资格）", !lsMap.has("adshield_pin_tip_confirmed_at"));
const afterToast = (byId.get("toast") && byId.get("toast")._html) || "";
A("指引文案提醒「仅在你主动点击时才上传」（绝不自动上传）", String(afterToast).includes("仅在你主动点击时才上传"));

console.log("═══ S7 60s 防重建（展示后短窗——每次进入语义）═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_pin_tip_shown_at", String(Date.now()));
A("刚展示过 → 60s 内防旋转/重建重复弹", T.pinTipRecentlyShown() === true);
nowMock = Date.now() + 60 * 1000 + 1;
A("60s+1ms 短窗结束（再进入即弹）", T.pinTipRecentlyShown() === false);
A("源码级：RESHOW_GUARD = 60 * 1000（旧 5min 窗已收紧）",
  /PIN_TIP_RESHOW_GUARD_MS = 60 \* 1000/.test(src));
nowMock = null;

console.log("═══ S8 更新优先 ═══");
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

console.log("═══ S9 进程被杀重启（时间戳持久）═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_pin_tip_confirmed_at", String(Date.now()));
T.resetPinTipSession();   // 等价新进程（会话闸复位——时间戳在 kv/localStorage）
A("新会话 24h 内仍抑制（时间戳跨进程持久）", T.pinTipThrottled() === true);

console.log("═══ S10 时间回拨防御 ═══");
lsMap.clear(); freshSession();
lsMap.set("adshield_pin_tip_confirmed_at", String(Date.now() + 3600 * 1000)); // 未来时间戳（回拨后遗留）
A("未来时间戳（回拨面）→ 负时长仍判抑制（防回拨刷弹窗）", T.pinTipThrottled() === true);

console.log("═══ S11 清数据恢复 ═══");
lsMap.clear(); freshSession();
A("时间戳清除 → 资格恢复（重新可弹）", T.pinTipThrottled() === false);

console.log("═══ S12 同会话单卡 ═══");
lsMap.clear(); freshSession();
T.maybeShowPinTip();
const firstTip = miniDom.tip;
T.maybeShowPinTip();
A("同会话重复评估不二弹（pinTipShownThisSession 闸）", miniDom.tip === firstTip);

console.log("═══ S13 主线程零阻塞 ═══");
A("maybeShowPinTip 纯同步本地路径（函数体无 await/fetch/网络）",
  !/function maybeShowPinTip\s*\(\)\s*\{[\s\S]*?await[\s\S]*?\}/.test(src));

console.log("");
console.log("══════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("══════════════════════════");
process.exit(fail ? 1 : 0);
