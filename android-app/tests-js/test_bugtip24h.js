// ═══ v7.9.8 Bug 反馈提示（24h 节流）· 真实 app.js 全量加载 + 迷你 DOM 交互测试 ═══
// 方式：与 scripts/check-appjs.sh 同源的全量 app.js stub 加载（顶层执行真实代码），
//      加载后换入可交互迷你 DOM，驱动 maybeShowBugTip/点击流/导航流/24h 时间旅行。
//      同时验证版本判定真值表（v7.10.0 统一规则——versionCode 单一判定）与
//      cmpVer/versionCodeFromNotes 死灰复燃防线（统一规则专项）。
const fs = require("fs");
const APPJS = "/home/z/skip-ad-repo/android-app/assets/www/app.js";
const src = fs.readFileSync(APPJS, "utf8");

/* ── Stage 1：catch-all stub 环境（同 check-appjs.sh）加载完整 app.js ── */
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
const pendingTimers = [];
globalThis.setTimeout = (fn) => { pendingTimers.push(fn); return 0; }; // 捕获不执行
globalThis.requestAnimationFrame = () => 0;
const fetchCalls = [];
globalThis.fetch = (...a) => { fetchCalls.push(a); return Promise.reject(new Error("no-net-in-test")); };

/* 时间旅行：可控 Date.now（24h 节流/5min 防抖窗的行为级验证） */
const REAL_NOW = Date.now();
let nowMock = null;
Date.now = () => (nowMock === null ? REAL_NOW : nowMock);

const TAIL = `
;globalThis.__T__ = {
  maybeShowBugTip, showBugTip, bugTipThrottled, bugTipRecentlyShown,
  store, appVer, appCode, isNewerByCode,
  HAS_BRIDGE,
  resetBugTipSession: () => { bugTipShownThisSession = false; },
};`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T) { console.error("✗ app.js 顶层加载失败（__T__ 未导出）"); process.exit(1); }

/* ── Stage 2：可交互迷你 DOM（仅覆盖提示流所需面）── */
class El {
  constructor(id) {
    this.id = id || ""; this.children = []; this.removed = false;
    this.handlers = {}; this._innerHTML = ""; this.dataset = {};
    this.clickCount = 0; this.scrolledIntoView = false;
  }
  set innerHTML(v) { this._innerHTML = String(v); }
  get innerHTML() { return this._innerHTML; }
  appendChild(c) { this.children.push(c); return c; }
  addEventListener(ev, fn) { (this.handlers[ev] = this.handlers[ev] || []).push(fn); }
  fire(ev) { (this.handlers[ev] || []).forEach((f) => f({})); }
  click() { this.clickCount++; this.fire("click"); }
  remove() { this.removed = true; }
  closest() { return miniDom.cards.upload; }
  scrollIntoView() { this.scrolledIntoView = true; }
}
const miniDom = {
  tip: null, buttons: {}, tabs: {}, cards: {},
  body: new El("body"),
};
miniDom.tabs.home = Object.assign(new El("tab-home"), { dataset: { page: "home" } });
miniDom.tabs.sub = Object.assign(new El("tab-sub"), { dataset: { page: "sub" } });
miniDom.tabs.me = Object.assign(new El("tab-me"), { dataset: { page: "me" } });
miniDom.cards.upload = new El("card-engine-log");
const uploadBtn = Object.assign(new El("btn-upload-log"), { uploaded: false });
uploadBtn.addEventListener("click", () => { uploadBtn.uploaded = true; }); // 若被误点即断言失败
miniDom.buttons.upload = uploadBtn;

globalThis.document = {
  querySelector(sel) {
    if (sel === "#update-gate") return null;                 // 本测试无更新页在场
    if (sel === "#bug-tip") return miniDom.tip && !miniDom.tip.removed ? miniDom.tip : null;
    if (sel === "#bug-tip-go" || sel === "#bug-tip-ok") {
      if (!miniDom.buttons[sel.slice(1)] && miniDom.tip && !miniDom.tip.removed) {
        miniDom.buttons[sel.slice(1)] = new El(sel.slice(1)); // innerHTML 渲染出的按钮（懒建）
      }
      return miniDom.buttons[sel.slice(1)] || null;
    }
    if (sel === "#btn-upload-log") return uploadBtn;
    return null; // 其余查询与本测试无关
  },
  querySelectorAll(sel) {
    if (sel === ".tabbtn") return [miniDom.tabs.home, miniDom.tabs.sub, miniDom.tabs.me];
    return [];
  },
  createElement() { const e = new El(); return e; },
  addEventListener() {},
  body: miniDom.body,
};
// createElement 需要能设置 id —— 换成带参构造
globalThis.document.createElement = () => new El();
const origCreate = globalThis.document.createElement;
globalThis.document.createElement = function () {
  const e = origCreate();
  Object.defineProperty(e, "id", {
    set(v) { this._id = v; if (v === "bug-tip") miniDom.tip = e; },
    get() { return this._id || ""; },
  });
  return e;
};

/* ═══ 断言集 ═══ */
let pass = 0, fail = 0;
function A(name, cond) {
  if (cond) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name); }
}
function freshSession() {
  miniDom.tip = null; miniDom.buttons = {}; T.resetBugTipSession();
}

console.log("═══ S0 加载与版本兜底 ═══");
A("app.js 全量顶层加载成功（stub 环境）", true);
A('appVer() 浏览器兜底 = "7.10.0"', T.appVer() === "7.10.0");
A('appCode() 浏览器兜底 = "80000"', T.appCode() === "80000");
A("HAS_BRIDGE=false（浏览器路径·localStorage）", T.HAS_BRIDGE === false);

console.log("═══ S1 首次进入：显示 ═══");
lsMap.clear(); fetchCalls.length = 0; nowMock = null; freshSession();
A("bugTipThrottled() 首次（无时间戳）= false", T.bugTipThrottled() === false);
T.maybeShowBugTip();
A("maybeShowBugTip() 后 #bug-tip 在场", !!miniDom.tip && !miniDom.tip.removed);
A("提示卡标题「使用过程中发现问题了吗？」", miniDom.tip.innerHTML.includes("使用过程中发现问题了吗？"));
A("列举五种情况（开屏没跳过/不准确/弹窗无法关/卡顿异常/其他体验）",
  ["开屏广告没有跳过", "广告跳过不准确", "弹窗无法关闭", "App 出现卡顿或异常", "其他使用体验问题"]
    .every((s) => miniDom.tip.innerHTML.includes(s)));
A("指引「我的 → 引擎日志 → 一键上传」", miniDom.tip.innerHTML.includes("我的 → 引擎日志 → 一键上传"));
A("两个按钮：去上传日志 + 知道了",
  miniDom.tip.innerHTML.includes("去上传日志") && miniDom.tip.innerHTML.includes("知道了"));
A("无恐吓/强制/必须/自动上传/否则无法用 字样",
  !["必须上传", "自动上传", "否则无法", "强制"].some((s) => miniDom.tip.innerHTML.includes(s)));
A("展示即记 bug_feedback_shown_at（防重建短窗）",
  lsMap.get("adshield_bug_feedback_shown_at") !== undefined);
A("展示不写确认时间戳（未确认不消耗资格）",
  lsMap.get("adshield_bug_feedback_confirmed_at") === undefined);

console.log("═══ S1b Activity 重建（5min 短窗·未确认）不重复弹 ═══");
freshSession();
nowMock = REAL_NOW + 60 * 1000; // 重建发生在展示后 1 分钟（5min 窗内）
T.maybeShowBugTip();
A("5min 窗内重建不重复弹（shown_at 防抖）", miniDom.tip === null);
A("未确认资格未被消耗（confirmed_at 仍未写）",
  lsMap.get("adshield_bug_feedback_confirmed_at") === undefined);
nowMock = REAL_NOW + 6 * 60 * 1000; // 超过 5min 短窗
freshSession();
T.maybeShowBugTip();
A("超过 5min 再次进入重新显示（未确认不永久消耗）", !!miniDom.tip && !miniDom.tip.removed);

console.log("═══ S2 点击「知道了」：关闭 + 记录确认时间 + 不上传 ═══");
const okBtn = globalThis.document.querySelector("#bug-tip-ok");
nowMock = null;
okBtn.fire("click");
A("#bug-tip 已移除", miniDom.tip.removed === true);
A("bug_feedback_confirmed_at 已记录（epoch 毫秒）",
  Number(JSON.parse(lsMap.get("adshield_bug_feedback_confirmed_at"))) > 1.7e12);
A("确认后 bugTipThrottled() = true（24h 内）", T.bugTipThrottled() === true);
A("上传按钮未被触碰（绝不自动上传）", uploadBtn.uploaded === false);
A("全程零网络请求", fetchCalls.length === 0);

console.log("═══ S3 24h 内重新进入：不显示 ═══");
freshSession();
nowMock = REAL_NOW + 23 * 3600 * 1000; // 确认后 23h
A("23h 后 bugTipThrottled() 仍 = true", T.bugTipThrottled() === true);
T.maybeShowBugTip();
A("24h 内再进不弹（miniDom.tip 未创建）", miniDom.tip === null);

console.log("═══ S3b 超过 24h 重新进入：重新显示 ═══");
freshSession();
nowMock = REAL_NOW + 25 * 3600 * 1000; // 确认后 25h
A("25h 后 bugTipThrottled() = false", T.bugTipThrottled() === false);
T.maybeShowBugTip();
A("超过 24h 下次进入重新显示", !!miniDom.tip && !miniDom.tip.removed);

console.log("═══ S4 点「去上传日志」：仅导航不代传 + 同步记录确认时间 ═══");
lsMap.clear(); freshSession(); miniDom.tabs.me.clickCount = 0;
miniDom.cards.upload.scrolledIntoView = false;
nowMock = null;
T.maybeShowBugTip();
A("重新弹出场（新装/清数据场景）", !!miniDom.tip && !miniDom.tip.removed);
const goBtn = globalThis.document.querySelector("#bug-tip-go");
/* 丢弃加载期捕获的启动定时器（800ms checkUpdateOnLaunch 本就含网络请求——
 * 与提示弹窗无关；本断言只验证「去上传日志」流自身零网络） */
pendingTimers.splice(0);
goBtn.fire("click");
/* 冲洗点击后新增的定时器（scrollIntoView 在 setTimeout(80) 内——真实浏览器自动执行） */
const flushTimers = () => { const t = pendingTimers.splice(0); t.forEach((fn) => { try { fn(); } catch (e) {} }); };
flushTimers();
A("提示卡关闭", miniDom.tip.removed === true);
A("已切换到「我的」页（me tab click=1）", miniDom.tabs.me.clickCount === 1);
A("滚动到引擎日志卡片", miniDom.cards.upload.scrolledIntoView === true);
A("上传按钮未被点击（最终决定权在用户）", uploadBtn.uploaded === false);
A("确认时间已同步记录（日志页返回 App 不立即重复弹）", T.bugTipThrottled() === true);
A("全程零网络请求（去上传日志 ≠ 上传）", fetchCalls.length === 0);

console.log("═══ S5 更新页在场时：本轮不弹（更新优先·新会话）═══");
lsMap.clear(); freshSession();
const gateEl = new El("update-gate");
const qsBackup = globalThis.document.querySelector;
globalThis.document.querySelector = function (sel) {
  if (sel === "#update-gate") return gateEl; // 模拟更新页在场
  return qsBackup.call(this, sel);
};
T.maybeShowBugTip();
A("更新页在场 → 提示本轮不弹", miniDom.tip === null);
A("且未消耗资格（确认时间戳未写）", lsMap.get("adshield_bug_feedback_confirmed_at") === undefined);
globalThis.document.querySelector = qsBackup;

console.log("═══ S6 版本判定真值表（v7.10.0 统一规则——versionCode 单一判定）═══");
A("isNewerByCode(79800 → 本地 79700) = true（v7.9.7 检测到 v7.9.8）",
  T.isNewerByCode(79800, 79700) === true);
A("isNewerByCode(79800 → 本地 79800) = false（不自误报）",
  T.isNewerByCode(79800, 79800) === false);
A("isNewerByCode(79800 → 本地 82300) = false（82300 用户不误报）",
  T.isNewerByCode(79800, 82300) === false);
A("isNewerByCode(0 → 本地 79700) = false（v7.10.0：远端无码不判新——名比较回退已删）",
  T.isNewerByCode(0, 79700) === false);
A("isNewerByCode(79800 → 本地 0) = false（v7.10.0：本地无码不判新——名比较回退已删）",
  T.isNewerByCode(79800, 0) === false);
A("isNewerByCode(79700 → 本地 79800) = false（绝不提示降级）",
  T.isNewerByCode(79700, 79800) === false);

console.log("═══ S7 cmpVer/versionCodeFromNotes 死灰复燃防线（统一规则专项）═══");
A("app.js 源无 function cmpVer（versionName 比较函数已删）",
  !/function cmpVer\s*\(/.test(src));
A("app.js 源无 function versionCodeFromNotes（正文元数据行回退已删）",
  !/function versionCodeFromNotes\s*\(/.test(src));
const noComments = src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^[ \t]*\/\/.*$/gm, "");
A("app.js 可执行面无 cmpVer 调用点", !/cmpVer\s*\(/.test(noComments));
A("app.js 可执行面无 versionCodeFromNotes 调用点", !/versionCodeFromNotes\s*\(/.test(noComments));
A("旧版资产仍在库（legacy/app-v7.9.6.js——历史对照物，非运行时依赖）",
  (() => { try { fs.readFileSync(__dirname + "/legacy/app-v7.9.6.js", "utf8"); return true; } catch (e) { return false; } })());

console.log("\n════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("════════════════════════");
process.exit(fail ? 1 : 0);
