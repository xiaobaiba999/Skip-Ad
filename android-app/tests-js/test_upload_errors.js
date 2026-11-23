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
// v7.13.0：文案升级——「已自动多次重试」+ 网络恢复自动补试指引（切网后无需手动重试）
const c1 = T.uploadErrText({ errKind: "NET_CONNECT_FAIL", lastError: "network" });
A("① NET_CONNECT_FAIL → 「网络连接失败，请检查网络」",
  c1.startsWith("网络连接失败，请检查网络"));
A("① 文案不吞日志去向（已保留声明）", c1.includes("日志已保留"));
A("① v7.13.0 网络恢复自动补试指引（切网无需手动重试）",
  c1.includes("网络恢复时") && c1.includes("自动补试"));

// ② 请求超时（服务端慢/响应未及——真机第一次失败的另一形态）
const c2 = T.uploadErrText({ errKind: "NET_TIMEOUT", lastError: "network" });
A("② NET_TIMEOUT → 「上传超时，请稍后重试」", c2.startsWith("上传超时，请稍后重试"));
A("② 超时文案说明幂等保护（重试不重复建 Issue）", c2.includes("复用原 Issue"));

// ②b-d（v7.9.9 超时阶段分家——用户指令硬性要求）
// 连接阶段超时：请求几乎必然未送达（workers.dev 国内阻断的真实形态）——提示换网络
const c2a = T.uploadErrText({ errKind: "NET_CONNECT_TIMEOUT", lastError: "connect timed out" });
A("②b NET_CONNECT_TIMEOUT → 「连接上传服务超时」", c2a.startsWith("连接上传服务超时"));
A("②b 连接超时提示换网络（可操作建议）", c2a.includes("切换"));
A("②b 连接超时保留日志去向声明", c2a.includes("日志已保留"));
// 读取阶段超时：请求可能已送达甚至已建 Issue——幂等重试语义如实告知
const c2b = T.uploadErrText({ errKind: "NET_READ_TIMEOUT", lastError: "Read timed out" });
A("②c NET_READ_TIMEOUT → 「上传超时，结果暂时无法确认」", c2b.startsWith("上传超时，结果暂时无法确认"));
A("②c 读取超时说明原批次幂等重试（不二建 Issue）",
  c2b.includes("原批次") && c2b.includes("不会重复创建 Issue"));
// TLS 干扰独立分类
const c2c = T.uploadErrText({ errKind: "NET_TLS", lastError: "handshake failure" });
A("②d NET_TLS → 「安全连接失败（网络传输可能被干扰）」", c2c.startsWith("安全连接失败"));
A("②d TLS 干扰提示切换网络", c2c.includes("切换网络"));
// 四态文案互不相同（绝不混为一谈）
A("②e 连接失败/连接超时/读取超时/TLS 四态文案互异",
  new Set([c1, c2a, c2b, c2c]).size === 4);

// ③ 频率限制（429/服务端 RATE_LIMITED）
const c3 = T.uploadErrText({ errKind: "RATE_LIMITED", lastError: "rate-limited" });
A("③ RATE_LIMITED → 「服务端繁忙」（v7.10.0 次数限制文案已清）", c3.includes("服务端繁忙"));

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
