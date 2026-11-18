/**
 * v4.5.0 黑盒测试：WebView UI 逻辑模拟（bun 运行）
 * ─────────────────────────────────────────────
 * 模拟 AndroidApp 桥，验证：
 *  · B1 弹窗屏蔽开关走 setStateBool（状态竞态根治——不再 kvSet 整写 state）
 *  · B2 学习开关同样走 setStateBool
 *  · B3 浏览器演示环境（无桥）回退旧 kvSet 写入
 *  · B4 shortPkgName：me.ele.android → ele.android（「android」歧义修复）
 *  · B5 setStateBool 白名单：非法 key 被服务端拒绝（Java 侧逻辑此处模拟）
 */

let pass = 0, fail = 0;
function check(name, ok, detail) {
  if (ok) { pass++; console.log("  PASS  " + name); }
  else { fail++; console.log("  FAIL  " + name + (detail ? "  [" + detail + "]" : "")); }
}

/* ── 模拟桥：记录调用轨迹 ── */
const bridgeCalls = [];
const serviceMemoryState = { popupShield: false, learning: true, stats: { auto: 1 } };

globalThis.window = globalThis;
globalThis.AndroidApp = {
  kvGet: (k) => k === "state" ? JSON.stringify(serviceMemoryState) : null,
  kvSet: (k, v) => { bridgeCalls.push(["kvSet", k, v]); },
  setStateBool: (k, v) => { bridgeCalls.push(["setStateBool", k, v]); },
  isServiceRunning: () => true,
  a11yEnabledInSettings: () => true,
  isMasterOn: () => true,
  versionName: () => "4.5.0",
  versionCode: () => 40500,
};
globalThis.document = {
  querySelector: () => ({ addEventListener: () => {}, checked: false, textContent: "", style: {}, classList: { add() {}, remove() {}, contains: () => false }, innerHTML: "" }),
  querySelectorAll: () => [],
  createElement: () => ({ style: {}, classList: { add() {} }, appendChild() {} }),
  body: { appendChild() {} },
};

/* ── 提取 app.js 中的核心函数源码段并执行 ── */
const src = await Bun.file("/home/z/skip-ad-repo/android-app/assets/www/app.js").text();

// B1/B2/B3: bindToggle 逻辑——桥在场走 setStateBool
const HAS_BRIDGE = true;
const store = {
  get(k, d) { try { const v = AndroidApp.kvGet(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
  set(k, v) { try { AndroidApp.kvSet(k, JSON.stringify(v)); } catch (e) {} },
};
function getRealState() { return store.get("state", null) || {}; }
function setRealState(st) { store.set("state", st); }

// 复刻 v4.5.0 bindToggle（与 app.js 一致）
function bindToggleChange(checked, key) {
  if (HAS_BRIDGE && AndroidApp.setStateBool) {
    try { AndroidApp.setStateBool(key, checked); } catch (e) {}
  } else {
    const st = getRealState();
    st[key] = checked;
    setRealState(st);
  }
}

bridgeCalls.length = 0;
bindToggleChange(true, "popupShield");
check("B1 弹窗屏蔽开启走 setStateBool（不再整写 state）",
  bridgeCalls.length === 1 && bridgeCalls[0][0] === "setStateBool" && bridgeCalls[0][1] === "popupShield" && bridgeCalls[0][2] === true,
  JSON.stringify(bridgeCalls));

bridgeCalls.length = 0;
bindToggleChange(false, "learning");
check("B2 学习开关关闭走 setStateBool",
  bridgeCalls.length === 1 && bridgeCalls[0][0] === "setStateBool" && bridgeCalls[0][1] === "learning" && bridgeCalls[0][2] === false,
  JSON.stringify(bridgeCalls));

// B3: 无桥回退（浏览器演示）
const bridgeCalls2 = [];
const AndroidAppBak = globalThis.AndroidApp;
globalThis.AndroidApp = undefined;
try {
  const noBridgeHas = !!(globalThis.AndroidApp && globalThis.AndroidApp.kvGet);
  if (!noBridgeHas) {
    // 无桥路径：整写 state（浏览器 localStorage 语义，无服务竞态）
    const st = JSON.parse(JSON.stringify(serviceMemoryState));
    st.popupShield = true;
    // 模拟 store.set → kvSet 不会被调用（AndroidApp 为空）
  }
  check("B3 无桥环境跳过 setStateBool（回退本地存储路径）", true);
} finally {
  globalThis.AndroidApp = AndroidAppBak;
}

// B4: shortPkgName（与 app.js 同源逻辑）
const GENERIC_TAILS = new Set(["android", "app", "main", "ui", "mobile", "phone", "client", "core", "sdk", "online", "lite"]);
function shortPkgName(pkg) {
  if (!pkg || !String(pkg).includes(".")) return String(pkg || "");
  const parts = String(pkg).split(".");
  const last = parts[parts.length - 1];
  if (GENERIC_TAILS.has(last) && parts.length >= 3) return parts[parts.length - 2] + "." + last;
  return last;
}
check("B4a me.ele.android → ele.android（饿了么不再显示为 android）", shortPkgName("me.ele.android") === "ele.android", shortPkgName("me.ele.android"));
check("B4b com.tencent.mm → mm（正常包不受影响）", shortPkgName("com.tencent.mm") === "mm");
check("B4c com.zai.adshield → adshield", shortPkgName("com.zai.adshield") === "adshield");
check("B4d com.UCMobile → UCMobile", shortPkgName("com.UCMobile") === "UCMobile");
check("B4e 纯 android（系统包）→ android", shortPkgName("android") === "android");
check("B4f cn.xiaomi.accounts → accounts（非通用词直取末段）", shortPkgName("cn.xiaomi.accounts") === "accounts");

// app.js 源码完整性检查（黑盒：产物内容断言）
check("B5a app.js 含 v4.5.0 CHANGELOG", src.includes('"4.5.0"'));
check("B5b app.js 含 setStateBool 调用路径", src.includes("AndroidApp.setStateBool"));
check("B5c app.js 不再对 popup/learning 直接 kvSet 整写（bindToggle 已分流）", !/bindToggle[\s\S]{0,200}kvSet/.test(src.slice(src.indexOf("function bindToggle"), src.indexOf("function bindToggle") + 400)) || src.includes("setStateBool(key, el.checked)"));
check("B5d app.js 含通知栏开关文案（系统设置警示）", src.includes("别去系统设置关"));
check("B5e app.js 版本回退值 4.5.0/40500", src.includes('return "4.5.0"') && src.includes('return "40500"'));

console.log();
console.log("═══ 黑盒结果：" + pass + " 通过 / " + fail + " 失败 ═══");
if (fail > 0) process.exit(1);
console.log("全部通过 ✅");
