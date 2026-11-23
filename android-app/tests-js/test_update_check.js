// ═══ v7.9.9 更新检查源链架构 · 真实 app.js 全量加载测试 ═══
// 验证用户指令的 JS 侧面：
//  ① 更新错误十类人话映射（updateErrText——不混为一谈）；
//  ② 静态清单源链（raw 主源 + jsDelivr 四接入点）且零 api.github.com
//     零 github.io（Pages 未启用死源已移除）；
//  ③ 浏览器预览路径 fetchLatest：清单成功/逐类失败降级/全链失败/
//     draft/prerelease/包名不匹配（劫持面）/坏 JSON；
//  ④ mapManifest 字段映射（apkDigest 归一 sha256: 前缀/apkMB 派生）；
//  ⑤ ageText 缓存数据年龄人话；
//  ⑥ 桥接线（源断言）：fetchLatest 走 AndroidApp.updateCheck(!!manual)、
//     手动按钮 fetchLatest(true)、启动检查 fetchLatest(false)、
//     错误显示走 updateErrText、缓存回退提示「以下为缓存数据」。
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
globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.location = { href: "", protocol: "https:", origin: "https://local" };
globalThis.addEventListener = () => {};
globalThis.setInterval = () => 0;
globalThis.setTimeout = () => 0;
globalThis.requestAnimationFrame = () => 0;

const TAIL = `
;globalThis.__T__ = {
  updateErrText: (typeof updateErrText === "function" ? updateErrText : null),
  ageText: (typeof ageText === "function" ? ageText : null),
  mapManifest: (typeof mapManifest === "function" ? mapManifest : null),
  fetchLatest: (typeof fetchLatest === "function" ? fetchLatest : null),
  MANIFEST_URL: (typeof UPDATE_MANIFEST_URL !== "undefined" ? UPDATE_MANIFEST_URL : null),
  MANIFEST_SOURCES: (typeof UPDATE_MANIFEST_SOURCES !== "undefined" ? UPDATE_MANIFEST_SOURCES : null),
};`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T || !T.updateErrText || !T.fetchLatest || !T.mapManifest) {
  console.error("✗ 更新检查符号未导出（app.js 加载失败或符号缺失）");
  process.exit(1);
}

let pass = 0, fail = 0;
function A(name, cond) {
  if (cond) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name); }
}

console.log("═══ ① 更新错误十类人话映射（updateErrText）═══");
const TABLE = {
  UPDATE_NETWORK_ERROR: "网络连接失败",
  UPDATE_DNS_ERROR: "域名解析失败",
  UPDATE_TIMEOUT: "超时",
  UPDATE_TLS_ERROR: "安全连接失败",
  UPDATE_HTTP_ERROR: "返回异常",
  UPDATE_INVALID_MANIFEST: "格式异常",
  UPDATE_PACKAGE_MISMATCH: "不匹配",
  UPDATE_VERSION_INVALID: "版本号异常",
  UPDATE_NO_CACHE: "暂时无法检查更新",
  UPDATE_RATE_LIMITED: "请求过于频繁",
  UPDATE_SERVER_ERROR: "暂时不可用",
};
for (const [code, key] of Object.entries(TABLE)) {
  A(`${code} → 含「${key}」`, T.updateErrText(code).includes(key));
}
A("未知码 → 空串（UI 回落 d.error 原文）", T.updateErrText("UPDATE_WHATEVER") === "");
A("null → 空串安全", T.updateErrText(null) === "");
const texts = Object.values(TABLE).map(k => Object.entries(TABLE).filter(([, v]) => v === k).length);
A("十类文案互不相同（不混为一谈）", new Set(Object.keys(TABLE).map(c => T.updateErrText(c))).size === 11);
A("「GitHub API 限流」文案已根除（不在任何错误文案中）",
  !Object.keys(TABLE).some(c => T.updateErrText(c).includes("GitHub API 限流")));

console.log("═══ ② 静态清单源链 + 零 GitHub REST API ═══");
A("主源 = raw.githubusercontent.com main 分支 latest.json",
  T.MANIFEST_URL === "https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json");
A("源链恰 5 源（raw + jsDelivr 四接入点）", Array.isArray(T.MANIFEST_SOURCES) && T.MANIFEST_SOURCES.length === 5);
A("源链首位 = 主源", T.MANIFEST_SOURCES[0] === T.MANIFEST_URL);
A("jsDelivr 四接入点全部在场（cdn/fastly/gcore/testingcf）",
  ["cdn", "fastly", "gcore", "testingcf"].every(h =>
    T.MANIFEST_SOURCES.includes(`https://${h}.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json`)));
A("github.io 死源已移除（Pages 未启用 + 国内不可达）",
  !T.MANIFEST_SOURCES.some(u => u.includes("github.io")));
A("全源 https + latest.json 路径",
  T.MANIFEST_SOURCES.every(u => u.startsWith("https://") && u.endsWith("update/latest.json")));
A("app.js 无 api.github.com 字符串字面量（可执行面）",
  !/"https:\/\/api\.github\.com/.test(src) && !src.includes("GH_API ="));
A("app.js 无 netGetLatestRelease 调用", !src.includes("netGetLatestRelease()"));
/* v7.10.0 异步化：JS 主线程零同步网络等待——桥调用必须是 updateCheckAsync */
A("app.js 桥调用 = AndroidApp.updateCheckAsync(manual, cb)", src.includes("AndroidApp.updateCheckAsync(!!manual, cbName)"));
A("app.js 无同步桥调用 AndroidApp.updateCheck(（旧同步桥已移除）",
  !/AndroidApp\.updateCheck\(/.test(src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^[ \t]*\/\/.*$/gm, "")));
A("app.js 90s 回调兜底在场（手动检查绝不永久卡住）", src.includes("UPD_ASYNC_TIMEOUT_MS"));
A("app.js JS 单飞在场（并发共享同一在飞检查）", src.includes("updInFlight"));
A("app.js 过期序号防护在场（旧回调丢弃）", src.includes("updSeq"));

console.log("═══ ③ 浏览器预览路径 fetchLatest（fetch 桩）═══");
const REAL_MANIFEST = {
  schemaVersion: 1, versionName: "7.9.7", versionCode: 79700,
  releaseTag: "v7.9.7", releaseName: "v7.9.7",
  releaseNotes: "versionCode: 79700\n1. 功能一\n2. 功能二",
  publishedAt: "2026-11-22T07:55:06Z",
  apkUrl: "https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.7/adshield-v7.9.7.apk",
  apkFileName: "adshield-v7.9.7.apk",
  apkSha256: "2367a2fbb4d7b49c596e7f930383468b7fc310abbd185a2a462d2e9ddf4cd4c7",
  apkSize: 886326, packageName: "com.zai.adshield",
  minSupportedVersionCode: 0, mandatoryUpdate: false,
};
function withFetch(handler, fn) {
  const orig = globalThis.fetch;
  globalThis.fetch = handler;
  return Promise.resolve().then(fn).finally(() => { globalThis.fetch = orig; });
}

// ③a 主源成功（成功即短路——后续源零请求）
let aHits = [];
await withFetch(async (url) => {
  aHits.push(url);
  A("③a 请求的是静态清单主源（非 GitHub API）", url === T.MANIFEST_URL);
  return { ok: true, status: 200, json: async () => REAL_MANIFEST };
}, async () => {
  const d = await T.fetchLatest(true);
  A("③a 主源成功 → ok + hasRelease", d.ok === true && d.hasRelease === true);
  A("③a tag/versionCode 映射", d.tag === "v7.9.7" && d.remoteVersionCode === 79700);
  A("③a apkDigest 归一 sha256:hex", d.apkDigest === "sha256:" + REAL_MANIFEST.apkSha256);
  A("③a 成功即短路（恰 1 请求）", aHits.length === 1);
});

// ③b 主源网络失败 → jsDelivr 备用源成功（链降级核心场景）
await withFetch(async (url) => {
  if (url === T.MANIFEST_URL) throw new Error("connection reset");
  A("③b 主源失败后试备用源", url === T.MANIFEST_SOURCES[1]);
  return { ok: true, status: 200, json: async () => REAL_MANIFEST };
}, async () => {
  const d = await T.fetchLatest(true);
  A("③b 备用源成功 → ok（降级链生效）", d.ok === true && d.tag === "v7.9.7");
});

// ③b2 主源 404（如 CDN 异常）→ 第三源成功
await withFetch(async (url) => {
  if (url === T.MANIFEST_SOURCES[2]) {
    return { ok: true, status: 200, json: async () => REAL_MANIFEST };
  }
  return { ok: false, status: 404, json: async () => ({}) };
}, async () => {
  const d = await T.fetchLatest(true);
  A("③b2 主源/次源 404 → 第三源成功（HTTP 错误也降级）", d.ok === true && d.hasRelease === true);
});

// ③b3 主源被劫持返回包名不匹配 → 备用源有效 → 采信备用源
await withFetch(async (url) => {
  if (url === T.MANIFEST_URL) {
    return { ok: true, status: 200, json: async () => ({ ...REAL_MANIFEST, packageName: "com.evil.x" }) };
  }
  return { ok: true, status: 200, json: async () => REAL_MANIFEST };
}, async () => {
  const d = await T.fetchLatest(true);
  A("③b3 主源包名不匹配（劫持面）→ 备用源有效采信", d.ok === true && d.tag === "v7.9.7");
});

// ③b4 主源返回非法 JSON → 备用源成功
await withFetch(async (url) => {
  if (url === T.MANIFEST_URL) {
    return { ok: true, status: 200, json: async () => { throw new SyntaxError("bad json"); } };
  }
  return { ok: true, status: 200, json: async () => REAL_MANIFEST };
}, async () => {
  const d = await T.fetchLatest(true);
  A("③b4 主源坏 JSON（劫持/损坏面）→ 备用源成功", d.ok === true && d.hasRelease === true);
});

// ③c 全链失败 → 如实网络错误
await withFetch(async () => { throw new Error("no network"); }, async () => {
  const d = await T.fetchLatest(true);
  A("③c 全链失败 → UPDATE_NETWORK_ERROR（不伪装成功）",
    d.ok === false && d.errorCode === "UPDATE_NETWORK_ERROR");
});

// ③d 全链 404 → 失败（首错 UPDATE_HTTP_ERROR 如实报告）
await withFetch(async () => ({ ok: false, status: 404, json: async () => ({}) }), async () => {
  const d = await T.fetchLatest(true);
  A("③d 全链 404 → 失败（非崩溃非伪装）", d.ok === false);
});

// ③e draft/prerelease → 不作正式更新（有效答案即停链）
let eHits = 0;
await withFetch(async () => {
    eHits++;
    return { ok: true, status: 200, json: async () => ({ ...REAL_MANIFEST, prerelease: true }) };
  },
  async () => {
    const d = await T.fetchLatest(true);
    A("③e prerelease → hasRelease=false（不作正式更新）", d.ok === true && d.hasRelease === false);
    A("③e 有效答案即停链（恰 1 请求）", eHits === 1);
  });

// ③f 全链包名不匹配 → 拒绝（首错 UPDATE_PACKAGE_MISMATCH）
await withFetch(async () => ({ ok: true, status: 200, json: async () => ({ ...REAL_MANIFEST, packageName: "com.evil.x" }) }),
  async () => {
    const d = await T.fetchLatest(true);
    A("③f 全链包名不匹配 → UPDATE_PACKAGE_MISMATCH（拒绝）",
      d.ok === false && d.errorCode === "UPDATE_PACKAGE_MISMATCH");
  });

// ③g 坏 JSON（json 解析抛异常）→ 逐源尝试全链 → 首错 INVALID 如实报告
let jsonCalls = 0;
await withFetch(async () => {
  jsonCalls++;
  return { ok: true, status: 200, json: async () => { throw new SyntaxError("bad json"); } };
}, async () => {
  const d = await T.fetchLatest(true);
  A("③g 全链坏 JSON → 失败 + 首错 UPDATE_INVALID_MANIFEST", d.ok === false && d.errorCode === "UPDATE_INVALID_MANIFEST");
  A("③g 五源各试一次（不重试不放弃）", jsonCalls === 5);
});

// ③h 最多五个 URL（每源至多一次——无轮询风暴）
let hits = [];
await withFetch(async (url) => { hits.push(url); throw new Error("down"); }, async () => {
  await T.fetchLatest(true);
  A("③h 一次检查至多 5 个请求（五源各一次）", hits.length === 5);
});

console.log("═══ ④ mapManifest 字段映射 ═══");
const m = T.mapManifest(REAL_MANIFEST);
A("apkMB 派生（886326 字节 → 0.85MB）", m.apkMB === 0.85);
A("apkSha256 大写输入归一", T.mapManifest({ ...REAL_MANIFEST, apkSha256: REAL_MANIFEST.apkSha256.toUpperCase() }).apkDigest === "sha256:" + REAL_MANIFEST.apkSha256);
A("sha256: 前缀输入容忍", T.mapManifest({ ...REAL_MANIFEST, apkSha256: "sha256:" + REAL_MANIFEST.apkSha256 }).apkDigest === "sha256:" + REAL_MANIFEST.apkSha256);
A("apkMirrors 空数组（唯一官方源）", Array.isArray(m.apkMirrors) && m.apkMirrors.length === 0);
A("缺 apkUrl → 空串（不致命）", T.mapManifest({ ...REAL_MANIFEST, apkUrl: "" }).apkUrl === "");
A("notes 字符串化（null 安全）", T.mapManifest({ ...REAL_MANIFEST, releaseNotes: null }).notes === "");

console.log("═══ ⑤ ageText 缓存数据年龄 ═══");
const NOW = Date.now();
A("1 分钟前", T.ageText(NOW - 90 * 1000).includes("分钟前"));
A("小时前", T.ageText(NOW - 3 * 3600 * 1000).includes("小时前"));
A("天前", T.ageText(NOW - 30 * 3600 * 1000).includes("天前"));
A("零值 → 空串", T.ageText(0) === "");
A("非法 → 空串", T.ageText("abc") === "");

console.log("═══ ⑥ 桥接线与 UI 文案（源断言）═══");
A("手动按钮传 manual=true（绕过新鲜缓存受冷却）", src.includes("await fetchLatest(true)"));
A("启动检查传 manual=false（优先缓存静默）", src.includes("await fetchLatest(false)"));
A("错误显示走 updateErrText 映射", src.includes("updateErrText(d.errorCode) || d.error"));
A("缓存回退提示「以下为缓存数据」+ 数据时间", src.includes("以下为缓存数据") && src.includes("ageText(d.cachedAt)"));
A("isNewer 优先原生权威判定（versionCode 主判）", src.includes('typeof d.isNewer === "boolean" ? d.isNewer'));

console.log("");
console.log("══════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("══════════════════════════");
if (fail > 0) process.exit(1);
