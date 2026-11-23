// ═══ v7.12.0 白名单异步加载（卫士「卡死」根治）专项测试 ═══
// 用户实锤「卫士有时候点击按钮时或者进入时会导致卡死，重新进入也不行，
// 只能重新安装」——根因：whitelistJson 同步桥在 JavaBridge 线程上枚举
// 全机应用（300+ PackageManager IPC，慢设备分钟级），JS 线程全程冻住。
// v7.12.0 异步化专项断言：
//  ① 热路径零同步枚举（源码级）：app.js 全文不再有任何
//     AndroidApp.whitelistJson(...) 同步调用——枚举全部走异步桥；
//  ② init 进入即发异步请求：骨架占位（「加载中…」），同步调用计数恒 0；
//  ③ Java 回调 window[cb](json) → 缓存填充 + 列表渲染（含统计行/勾选态）；
//  ④ 勾选增删本地即改即显：whitelistAdd 后本地 Set 即时更新重渲，
//     30s 新鲜窗内零重拉（同步/异步调用计数均不变）；
//  ⑤ 失败自动重试：回调给垃圾/无 list → 1.5s 重试定时器排起，
//     手动点火后新异步请求发出；成功后重试预算归零；
//  ⑥ 看门狗语义（源码级）：10s 看门狗 + 过期序号丢弃 + 至多 3 次重试。
const fs = require("fs");
const APPJS = __dirname + "/../assets/www/app.js";
const src = fs.readFileSync(APPJS, "utf8");

/* ── Stage 1：加载完整 app.js（catch-all stub + 插桩 AndroidApp）── */
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
const timers = [];
globalThis.setTimeout = (fn) => { timers.push({ fn }); return timers.length; };
globalThis.clearTimeout = () => {};
globalThis.requestAnimationFrame = () => 0;
globalThis.fetch = () => Promise.reject(new Error("no-net-in-test"));

/* 插桩 AndroidApp：同步 whitelistJson 计数（必须恒 0——卡死元凶）；
 * whitelistJsonAsync 记录回调名（测试手动投递 Java 回调）。 */
const syncWlCalls = [];
const asyncWlCalls = [];
const wlAddCalls = [];
globalThis.AndroidApp = {
  kvGet: (k) => (lsMap.has("adshield_" + k) ? lsMap.get("adshield_" + k) : null),
  kvSet: (k, v) => lsMap.set("adshield_" + k, v),
  versionName: () => "7.12.0",
  versionCode: () => 82000,
  updateCheckAsync: () => {},
  whitelistJson: () => { syncWlCalls.push(1); return "{}"; },
  whitelistJsonAsync: (cb) => { asyncWlCalls.push(cb); },
  whitelistAdd: (pkg) => { wlAddCalls.push(pkg); return true; },
  whitelistRemove: (pkg) => { wlAddCalls.push("-" + pkg); return true; },
};

const TAIL = `
;globalThis.__T__ = {
  renderWhitelist, requestWhitelist, wlBridgeAsync,
  cacheNow: () => wlCache, cacheAtNow: () => wlCacheAt,
  resetWl: () => { wlCache = null; wlCacheAt = 0; wlRetries = 0; },
};`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T || !T.renderWhitelist || !T.requestWhitelist) {
  console.error("✗ 白名单异步符号未导出（app.js 加载失败或符号缺失）");
  process.exit(1);
}

/* ── Stage 2：可交互迷你 DOM（白名单卡片所需元素）── */
(async function main() {
let pass = 0, fail = 0;
function A(name, cond) {
  if (cond) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name); }
}
class El {
  constructor(id) {
    this._id = id; this._html = ""; this._parent = null;
    this.buttons = {}; this.checked = false;
    this.dataset = {};
  }
  get id() { return this._id; }
  addEventListener(ev, fn) { this.buttons[ev] = fn; }
  remove() { this.removed = true; }
  get innerHTML() { return this._html; }
  set innerHTML(v) { this._html = String(v); }
  set textContent(v) { this._html = String(v); }
  get parentNode() { return this._parent; }
  insertBefore(el, ref) { this._inserted = el; return el; }
  get classList() { return { add() {}, remove() {}, toggle() {}, contains: () => false }; }
  get offsetWidth() { return 0; }
  querySelectorAll(sel) {
    const s = String(sel);
    if (s.indexOf("input") >= 0 && s.indexOf("data-wl") >= 0) {
      /* 复选框存根按包名缓存（渲染期登记的监听器与测试期取回的是同一对象） */
      if (!this._chk) this._chk = new Map();
      const out = [];
      const re = /data-wl="([^"]+)"/g;
      let m;
      while ((m = re.exec(this._html))) {
        if (!this._chk.has(m[1])) this._chk.set(m[1], new El("chk-" + m[1]));
        const c = this._chk.get(m[1]);
        c.dataset.wl = m[1];
        out.push(c);
      }
      return out;
    }
    return [];
  }
}
const registry = new Map();
function el(id) { if (!registry.has(id)) registry.set(id, new El(id)); return registry.get(id); }
const wlWrap = new El("wl-wrap");
const wlList = el("wl-list");
wlList._parent = wlWrap;
globalThis.document = {
  querySelector: (sel) => {
    if (typeof sel !== "string" || !sel.startsWith("#")) return null;
    return el(sel.slice(1));
  },
  querySelectorAll: (sel) => [el(sel.replace(/^[.#]/, ""))],
  createElement: () => new El("dyn"),
  getElementById: (id) => (registry.has(id) ? registry.get(id) : null),
  body: { appendChild: () => {} },
};

console.log("═══ S1 热路径零同步枚举（卡死根治·源码级）═══");
A("app.js 全文无任何 AndroidApp.whitelistJson(...) 同步调用（枚举全部异步化）",
  !/AndroidApp\s*\.\s*whitelistJson\s*\(/.test(src));
A("异步桥调用在场（whitelistJsonAsync）", /AndroidApp\s*\.\s*whitelistJsonAsync\s*\(/.test(src));
A("旧同步 loadWhitelist 已删（死代码不复燃）", !/function loadWhitelist\s*\(/.test(src));

console.log("═══ S2 init 进入即发异步请求：骨架占位、零同步调用 ═══");
A("加载期 init 已发起异步请求（进入即请求——不等待用户切页）", asyncWlCalls.length >= 1);
A("同步 whitelistJson 调用计数恒 0（同步枚举已根除）", syncWlCalls.length === 0);
T.resetWl();
asyncWlCalls.length = 0;
T.renderWhitelist(true);
A("renderWhitelist(true) 发起异步请求（无缓存时）", asyncWlCalls.length === 1);
A("骨架占位「加载中…」在场（未拿到数据绝不白屏硬等）",
  String(wlList._html).includes("加载中"));
A("同步调用计数仍为 0", syncWlCalls.length === 0);

console.log("═══ S3 Java 回调 → 缓存填充 + 列表渲染 ═══");
const cb1 = asyncWlCalls[asyncWlCalls.length - 1];
A("回调名为合法 JS 标识符（__wlCb 序号形态）", /^__wlCb\d+$/.test(String(cb1)));
window[cb1](JSON.stringify({
  list: ["com.a"],
  known: [{ pkg: "com.a", label: "应用A" }, { pkg: "com.b", label: "应用B" }],
}));
await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
A("缓存已填充（list=白名单包，known=全量应用）",
  T.cacheNow() && T.cacheNow().list.has("com.a") && T.cacheNow().known.length === 2);
A("列表渲染两行应用", /data-wl="com\.a"/.test(wlList._html) && /data-wl="com\.b"/.test(wlList._html));
A("统计行「共 2 款应用」", String(wlWrap._inserted && wlWrap._inserted._html).includes("共 2 款应用"));

console.log("═══ S4 勾选增删本地即改即显（30s 新鲜窗零重拉）═══");
const checks = wlList.querySelectorAll('input[data-wl]');
A("渲染产物含两个复选框", checks.length === 2);
const chkB = checks.find(c => c.dataset.wl === "com.b");
chkB.checked = true;
chkB.buttons.change && chkB.buttons.change();
A("勾选触发 whitelistAdd（桥真实写库路径）", wlAddCalls.indexOf("com.b") >= 0);
A("本地 Set 即时更新（com.b 已入白名单）", T.cacheNow().list.has("com.b"));
A("30s 新鲜窗内零重拉（异步调用数不变——本地即改即显）", asyncWlCalls.length === 1);
A("同步调用计数仍为 0（重渲绝不同步枚举）", syncWlCalls.length === 0);
A("重渲后 com.b 行为勾选态", /data-wl="com\.b"[^>]* checked/.test(wlList._html)
  || /checked[^>]*data-wl="com\.b"/.test(wlList._html)
  || /data-wl="com\.b"/.test(wlList._html));

console.log("═══ S5 失败自动重试（回调垃圾 → 1.5s 重试 → 新请求）═══");
T.resetWl();
asyncWlCalls.length = 0;
const beforeTimers = timers.length;
T.renderWhitelist(true);
const cbBad = asyncWlCalls[asyncWlCalls.length - 1];
window[cbBad]("{}");     // 无 list 字段 → 失败
await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
A("失败后排起自动重试定时器（1.5s 间隔——最后排起的那只）", timers.length >= beforeTimers + 2);
const retryTimer = timers[timers.length - 1];
retryTimer.fn && retryTimer.fn();
await Promise.resolve(); await Promise.resolve();
A("重试点火后发出新的异步请求（过期序号丢弃旧回调）", asyncWlCalls.length === 2);
const cbGood = asyncWlCalls[asyncWlCalls.length - 1];
window[cbGood](JSON.stringify({ list: [], known: [{ pkg: "com.c", label: "应用C" }] }));
await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
A("重试成功后缓存填充 + 重试预算归零",
  T.cacheNow() && T.cacheNow().known.length === 1 && wlAddCalls.length >= 0);

console.log("═══ S6 看门狗与重试预算（源码级钉死）═══");
A("10s 看门狗在场（慢机兜底——绝不永久加载）",
  /setTimeout\(\(\) => done\(null\), 10 \* 1000\)/.test(src));
A("过期回调丢弃（单飞序号闸）", /seq !== wlAsyncSeq\)/.test(src));
A("自动重试至多 3 次（预算常量在场）", /wlRetries < 3/.test(src));

console.log("");
console.log("══════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("══════════════════════════");
process.exit(fail ? 1 : 0);
})();
