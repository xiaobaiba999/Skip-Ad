#!/bin/sh
# 页面资产符号校验闸（v7.6.1 · 热修复防回归闸）
# ─────────────────────────────────────────────────────────────
# 死因（用户实锤原话：「扫描日志，7.6版本记录没了，使用报告也不见」）：
#   v7.6.0 的 assets/www/app.js 版本记录注释未闭合（`/* ═══ v7.6.0 版本记录
#   （…）═══` 尾部丢了 ` */`）——块注释吞掉了后续 17 行：整个
#   `const CURRENT_RELEASE = {...}` 声明被吃进注释，页面渲染到版本记录时抛
#   ReferenceError 中断——扫描日志/版本记录/使用报告三大板块全部消失。
#   node --check 语法层完全通过（注释闭合在别处、语法合法）——纯语法闸
#   拦不住这类「语义级损坏」，坏资产直接进了已发布 APK。
#
# 本闸（语义级三重断言，缺一即拒绝打包）：
#   ① 语法层：node/bun 解析（SyntaxError 直接拒）；
#   ② 执行层：catch-all stub 环境（window/document/localStorage 全代理）
#     顶层执行整份 app.js——ReferenceError（缺标识符=本死因类）直接拒；
#   ③ 符号层：同一执行作用域内断言关键符号在场（CURRENT_RELEASE
#     版本记录 / renderLog 扫描日志 / renderReport 使用报告 /
#     renderReleaseNotes 更新说明 / notesToHeadlines 解析器）——
#     被注释吞掉/被删即拒。
# 运行器：bun → node → 都无则警告跳过（CI 工具缺失不硬阻塞）。
# 用法: ./scripts/check-appjs.sh [app.js 路径（默认 assets/www/app.js）]
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APPJS="${1:-$ROOT/android-app/assets/www/app.js}"
[ -f "$APPJS" ] || { echo "✗ 未找到 $APPJS"; exit 1; }

RUNNER=""
if command -v bun >/dev/null 2>&1; then RUNNER="bun"
elif command -v node >/dev/null 2>&1; then RUNNER="node"
else
  echo "⚠ 无 bun/node——跳过页面资产语义闸（仅警告不阻塞）"
  exit 0
fi

APPJS="$APPJS" "$RUNNER" - <<'CHECK_EOF'
"use strict";
const fs = require("fs");
const src = fs.readFileSync(process.env.APPJS, "utf8");

/* ① 语法层：整份源码先过一遍解析（不执行） */
new Function(src); // SyntaxError 在这里直接抛出

/* ② 执行层：catch-all stub 环境顶层执行 */
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
globalThis.addEventListener = globalThis.addEventListener || (() => {});
/* 定时器强制 no-op：app.js 顶层 init 会排常驻轮询（保活/进度轮询）——
 * bun/node 真实 setInterval 会挂住事件循环使校验进程永不退出（首轮实测
 * 60s 超时挂起的直接原因）。校验只关心同步顶层执行，异步一概取消。 */
globalThis.setInterval = () => 0;
globalThis.setTimeout = () => 0;
globalThis.requestAnimationFrame = globalThis.requestAnimationFrame || (() => 0);
/* AndroidApp 桥缺省 → HAS_BRIDGE=false 走 localStorage 存根路径 */

/* 关键符号表：与「扫描日志/版本记录/使用报告」三大板块一一对应的
 * 顶层声明（v7.6.1 死因类：被未闭合注释吞掉 → 符号消失 → 板块消失） */
const NEED = [
  "CURRENT_RELEASE",     // 版本记录（死因本体）
  "notesToHeadlines",    // 新版功能描述解析器
  "renderReleaseNotes",  // 更新说明渲染
  "renderLog",           // 扫描日志
  "renderReport",        // 使用报告
  "checkUpdateOnLaunch", // v7.9.6plus 启动时新版本检测
  "showUpdateGate",      // v7.9.6plus 全屏更新页
  "versionCodeFromNotes",// v7.9.6plus versionCode 元数据解析
  "isNewerByCode",       // v7.9.6plus versionCode 主判
  "maybeShowBugTip",     // Bug 反馈提示评估入口（v7.9.8 升 24h 节流）
  "showBugTip",          // Bug 反馈提示卡渲染
  "bugTipThrottled",     // v7.9.8 24h 节流判定（时间戳主闸）
  "uploadErrText",       // v7.9.8 上传错误八类人话映射
  "doUploadLog",         // 一键上传主流程（导出下线后唯一日志出口）
  "resetUploadButtons",  // 上传双入口按钮复位
];

let runErr = null;
let syms = {};
try {
  /* 追加一行在同一执行作用域内把符号类型回填到 globalThis——
   * 间接 eval 的 const/let 不出作用域，必须同程序内取 */
  const tail = "\n;globalThis.__APPJS_SYMS__ = {" +
    NEED.map((n) => JSON.stringify(n) + ": typeof " + n).join(", ") + "};";
  (0, eval)(src + tail);
  syms = globalThis.__APPJS_SYMS__ || {};
} catch (e) {
  runErr = e;
}
delete globalThis.__APPJS_SYMS__;

/* ③ 符号层断言（硬失败——被注释吞掉/被删） */
const missing = NEED.filter((n) => syms[n] !== "function" && syms[n] !== "object");
if (missing.length) {
  console.log("❌ [页面资产符号校验] 关键符号未定义（被注释吞掉/被删）: " + missing.join(", "));
  console.log("   —— v7.6.1 死因同类：未闭合注释吞掉声明 → 对应板块整页消失。拒绝打包。");
  process.exit(1);
}

/* 执行层异常分级：ReferenceError/SyntaxError = 真损坏（硬拒）；
 * TypeError 等 = stub 环境形变嫌疑（软警告——stub 无法完美模拟 DOM） */
if (runErr) {
  const name = runErr && runErr.name ? String(runErr.name) : "";
  if (/ReferenceError|SyntaxError/.test(name)) {
    console.log("❌ [页面资产符号校验] 顶层执行 " + name + ": " + (runErr.message || ""));
    process.exit(1);
  }
  console.log("⚠ 顶层执行异常（stub 环境嫌疑，非硬失败）: " + (runErr.message || runErr));
}
console.log("✓ [页面资产符号校验] 语法解析 + 顶层执行 + 关键符号 " + NEED.length + " 项在场（版本记录/扫描日志/使用报告三板块接线完整）");
/* 强制退出：即便 stub 环境里残余了任何已排回调/句柄，结论已出——立即收兵。 */
process.exit(0);
CHECK_EOF
