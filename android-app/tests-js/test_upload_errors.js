// ═══ v7.9.8 上传错误八类人话映射 · 真实 app.js 全量加载测试 ═══
// 验证 uploadErrText(res) 按 errKind 稳定分类码映射用户指令八类表——
// 不再统一「稍后再试」；文案与 LogUploader.classifyError 一一对应。
const fs = require("fs");
const APPJS = "/home/z/skip-ad-repo/android-app/assets/www/app.js";
const src = fs.readFileSync(APPJS, "utf8");

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
globalThis.localStorage = {
  getItem: () => null, setItem: () => {},
};
globalThis.location = { href: "", protocol: "https:", origin: "https://local" };
globalThis.addEventListener = () => {};
globalThis.setInterval = () => 0;
globalThis.setTimeout = () => 0;
globalThis.requestAnimationFrame = () => 0;

const TAIL = `
;globalThis.__T__ = { uploadErrText: (typeof uploadErrText === "function" ? uploadErrText : null) };`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T || !T.uploadErrText) {
  console.error("✗ uploadErrText 未导出（app.js 加载失败或符号缺失）");
  process.exit(1);
}

let pass = 0, fail = 0;
function A(name, cond) {
  if (cond) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name); }
}

console.log("═══ 用户八类错误表 → uploadErrText 映射 ═══");

// ① 网络连接失败（workers.dev 被阻断/DNS 污染/无网——真机第一次失败的真实形态）
const c1 = T.uploadErrText({ errKind: "NET_CONNECT_FAIL", lastError: "network" });
A("① NET_CONNECT_FAIL → 「网络连接失败，请检查网络后重试」",
  c1.startsWith("网络连接失败，请检查网络后重试"));
A("① 文案不吞日志去向（已保留声明）", c1.includes("日志已保留"));

// ② 请求超时（服务端慢/响应未及——真机第一次失败的另一形态）
const c2 = T.uploadErrText({ errKind: "NET_TIMEOUT", lastError: "network" });
A("② NET_TIMEOUT → 「上传超时，请稍后重试」", c2.startsWith("上传超时，请稍后重试"));
A("② 超时文案说明幂等保护（重试不重复建 Issue）", c2.includes("复用原 Issue"));

// ③ 频率限制（429/服务端 RATE_LIMITED）
const c3 = T.uploadErrText({ errKind: "RATE_LIMITED", lastError: "rate-limited" });
A("③ RATE_LIMITED → 「上传次数已达到限制」", c3.startsWith("上传次数已达到限制"));

// ④ 服务端拒绝（六个终态码）
for (const code of ["PRIVACY_REJECTED", "UNAUTHORIZED", "IDEMPOTENCY_CONFLICT",
                    "PAYLOAD_TOO_LARGE", "MANUAL_RECONCILIATION_REQUIRED"]) {
  const c4 = T.uploadErrText({ errKind: code, lastError: code });
  A(`④ ${code} → 「服务端拒绝了本次上传（码透出）」`,
    c4.startsWith("服务端拒绝了本次上传") && c4.includes(code));
}

// ⑤ 请求格式错误
const c5 = T.uploadErrText({ errKind: "INVALID_REQUEST", lastError: "INVALID_REQUEST" });
A("⑤ INVALID_REQUEST → 「上传数据格式异常」", c5.startsWith("上传数据格式异常"));

// ⑤b 认证与权限分家（v7.9.8b——裸 401/403 不再混同为 UNAUTHORIZED）
const c5a = T.uploadErrText({ errKind: "AUTH_FAILED", lastError: "401" });
A("⑤b AUTH_FAILED → 「认证失败」（401 提示认证问题）", c5a.startsWith("认证失败"));
A("⑤b AUTH_FAILED 文案保留日志去向", c5a.includes("日志已保留"));
const c5b = T.uploadErrText({ errKind: "FORBIDDEN", lastError: "403" });
A("⑤c FORBIDDEN → 「权限不足」（403 提示权限问题）", c5b.startsWith("权限不足"));
A("⑤c FORBIDDEN 文案保留日志去向", c5b.includes("日志已保留"));
A("⑤d 服务端 JSON 码 UNAUTHORIZED 仍按契约透出（不被 AUTH_FAILED 吞）",
  T.uploadErrText({ errKind: "UNAUTHORIZED", lastError: "UNAUTHORIZED" }).includes("UNAUTHORIZED"));

// ⑥ GitHub API/中转上游异常
for (const code of ["RELAY_SERVER_ERROR", "RELAY_TEMP_ERROR"]) {
  const c6 = T.uploadErrText({ errKind: code, lastError: code });
  A(`⑥ ${code} → 「日志接收服务暂时不可用」`, c6.startsWith("日志接收服务暂时不可用"));
}

// ⑦ 协议面异常（2xx 但无 ok=true）
const c7 = T.uploadErrText({ errKind: "PROTOCOL_UNEXPECTED_BODY", lastError: "200" });
A("⑦ PROTOCOL_UNEXPECTED_BODY → 「上传服务响应异常」", c7.startsWith("上传服务响应异常"));

// ⑧ 未知错误（兜底——原始错误透出，绝不假装成功）
const c8 = T.uploadErrText({ errKind: "NET_IO", lastError: "io-detail" });
A("⑧ NET_IO → 网络传输异常", c8.startsWith("网络传输异常"));
const c9 = T.uploadErrText({ errKind: "WEIRD_CODE", lastError: "weird" });
A("⑧ 未知分类 → 兜底文案含原始错误（weird）", c9.includes("上传失败") && c9.includes("weird"));
const c10 = T.uploadErrText({});
A("⑧ 空对象 → 兜底文案含「未知」", c10.includes("未知"));

console.log("\n════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("════════════════════════");
process.exit(fail ? 1 : 0);
