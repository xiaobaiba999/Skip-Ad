// ═══ v7.10.0 异步更新检查生命周期 · 真实 app.js 全量加载测试 ═══
// 用户指令「更新检查异步化，禁止在 JavaScript 主线程同步执行网络请求」专项：
//  ① 桥调用面：fetchLatest 走 AndroidApp.updateCheckAsync(manual, cb)（回调制），
//     无同步 AndroidApp.updateCheck 调用；
//  ② 90s 回调兜底：桥回调永不抵达 → 按超时失败收尾（手动检查绝不永久卡住）；
//  ③ 过期序号（updSeq）：迟到的旧回调被丢弃——只有最新检查的结果生效；
//  ④ JS 单飞：并发调用共享同一在飞 Promise（恰一次桥调用）；
//  ⑤ 桥异常防御：updateCheckAsync 抛异常 → 立即失败收尾（不悬挂）；
//  ⑥ 回调载荷：对象直收 / JSON 字符串解析 / 非对象防御。
const fs = require("fs");
const APPJS = __dirname + "/../assets/www/app.js";
const src = fs.readFileSync(APPJS, "utf8");

/* ── Stage 1：可控行桥环境加载完整 app.js ── */
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
globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.location = { href: "", protocol: "https:", origin: "https://local" };
globalThis.addEventListener = () => {};
globalThis.setInterval = () => 0;
globalThis.requestAnimationFrame = () => 0;

/* 可控定时器（90s 兜底的行为级驱动） */
const timers = [];
let timerSeq = 0;
globalThis.setTimeout = (fn, ms) => { timerSeq++; timers.push({ id: timerSeq, fn, ms: ms || 0 }); return timerSeq; };
globalThis.clearTimeout = (id) => { const i = timers.findIndex(t => t.id === id); if (i >= 0) timers.splice(i, 1); };
/* 定向触发 90s 兜底定时器（eval 期 init 的 600/800/3000ms 定时器也在
 * 捕获队列里——必须按 ms 值过滤，绝不误触 init 流程） */
function fireFirstTimer() {
  const i = timers.findIndex(t => t.ms === 90 * 1000);
  if (i < 0) return false;
  const t = timers.splice(i, 1)[0];
  t.fn();
  return true;
}

/* 可控行桥（测试逐次决定 updateCheckAsync 行为） */
const bridgeCalls = [];
globalThis.AndroidApp = {
  kvGet: () => null,
  kvSet: () => {},
  versionName: () => "7.10.0",
  versionCode: () => 80000,
  updateCheckAsync: (manual, cb) => { bridgeCalls.push({ manual: !!manual, cb }); },
};

const TAIL = `
;globalThis.__T__ = {
  bridgeUpdateCheck: (typeof bridgeUpdateCheck === "function" ? bridgeUpdateCheck : null),
  fetchLatest: (typeof fetchLatest === "function" ? fetchLatest : null),
  getInFlight: () => (typeof updInFlight !== "undefined" ? updInFlight : null),
  TIMEOUT_MS: (typeof UPD_ASYNC_TIMEOUT_MS !== "undefined" ? UPD_ASYNC_TIMEOUT_MS : 0),
};`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T || !T.bridgeUpdateCheck || !T.fetchLatest) {
  console.error("✗ 异步更新检查符号未导出（app.js 加载失败或符号缺失）");
  process.exit(1);
}

let pass = 0, fail = 0;
function A(name, cond) {
  if (cond) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name); }
}

console.log("═══ S1 桥调用面（异步制）═══");
A("90s 兜底常量在场", T.TIMEOUT_MS === 90 * 1000);
const p1 = T.bridgeUpdateCheck(true);
A("发起后立即返回 Promise（JS 主线程零等待）", p1 instanceof Promise);
A("桥收到 updateCheckAsync(manual=true, cb) 恰一次",
  bridgeCalls.length === 1 && bridgeCalls[0].manual === true && typeof bridgeCalls[0].cb === "string");
A("回调名登记在 window（__updCb1）", typeof globalThis[bridgeCalls[0].cb] === "function");
bridgeCalls[0].resolve = null;
// 交付结果（对象直收）
globalThis[bridgeCalls[0].cb]({ ok: true, hasRelease: false, source: "manifest" });
p1.then((d) => {
  A("回调对象直收（Promise resolve 原对象）", d && d.ok === true && d.hasRelease === false);

  console.log("═══ S2 回调载荷形态 ═══");
  const p2 = T.bridgeUpdateCheck(false);
  globalThis[bridgeCalls[1].cb](JSON.stringify({ ok: false, errorCode: "UPDATE_NETWORK_ERROR" }));
  return p2;
}).then((d2) => {
  A("JSON 字符串回调 → 解析为对象", d2 && d2.ok === false && d2.errorCode === "UPDATE_NETWORK_ERROR");
  A("回调后 window 回调名已清理", globalThis[bridgeCalls[1].cb] === undefined);

  console.log("═══ S3 90s 兜底（回调永不抵达）═══");
  const p3 = T.bridgeUpdateCheck(true);
  const cb3 = bridgeCalls[2].cb;
  A("在飞期间不交付任何回调", typeof globalThis[cb3] === "function");
  fireFirstTimer();
  return p3;
}).then((d3) => {
  A("90s 定时器触发 → UPDATE_TIMEOUT 失败收尾（不悬挂）",
    d3 && d3.ok === false && d3.errorCode === "UPDATE_TIMEOUT");
  A("超时收尾后回调名清理", globalThis[bridgeCalls[2].cb] === undefined);

  console.log("═══ S4 过期序号（迟到的旧回调丢弃）═══");
  const p4 = T.bridgeUpdateCheck(true);
  const cb4 = bridgeCalls[3].cb;
  fireFirstTimer();                    // A 检查按超时收尾（此时序号 4 已 settle）
  return p4.then((d4a) => {
    A("A 检查先按超时收尾", d4a.errorCode === "UPDATE_TIMEOUT");
    const p5 = T.bridgeUpdateCheck(true);   // B 检查（新序号）
    const lateCb = cb4;               // A 的迟到回调（window 已清理 → 无操作面）
    A("A 的迟到回调名已被清理（无操作面）", globalThis[lateCb] === undefined);
    if (typeof globalThis[lateCb] === "function") globalThis[lateCb]({ ok: true, injected: "EVIL" });
    globalThis[bridgeCalls[4].cb]({ ok: true, hasRelease: true, tag: "v7.10.1" });
    return p5;
  }).then(async (d5) => {
    A("B 检查不受迟到旧回调污染（结果为 B 自己的）",
      d5 && d5.ok === true && d5.tag === "v7.10.1" && !d5.injected);

    console.log("═══ S5 JS 单飞（并发共享）═══");
    const a = T.fetchLatest(true);
    const b = T.fetchLatest(true);
    A("在飞期间恰一次桥调用（并发两次 fetchLatest 单飞）", bridgeCalls.length === 6);
    globalThis[bridgeCalls[5].cb]({ ok: true, hasRelease: false });
    const da = await a, db = await b;
    A("两次调用共享同一结果（值级一致——async 包装下单飞语义）",
      JSON.stringify(da) === JSON.stringify(db) && da.ok === true);
    return da;
  });
}).then(async (d6) => {
  A("单飞共享结果一致", d6 && d6.ok === true && d6.hasRelease === false);
  A("收尾后单飞位清空", T.getInFlight() === null);

  console.log("═══ S6 桥异常防御 ═══");
  const origAsync = globalThis.AndroidApp.updateCheckAsync;
  globalThis.AndroidApp.updateCheckAsync = () => { throw new Error("bridge-dead"); };
  const p7 = T.fetchLatest(true);
  globalThis.AndroidApp.updateCheckAsync = origAsync;
  const d7 = await p7;
  A("桥抛异常 → 立即失败收尾（UPDATE_NO_CACHE）",
    d7 && d7.ok === false && d7.errorCode === "UPDATE_NO_CACHE");

  console.log("");
  console.log("══════════════════════════");
  console.log(`  通过 ${pass} · 失败 ${fail}`);
  console.log("══════════════════════════");
  process.exit(fail ? 1 : 0);
}).catch((e) => {
  console.error("✗ 测试驱动异常: " + e);
  process.exit(1);
});
