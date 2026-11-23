// ═══ v7.10.0 统一版本规则 · 真实 app.js 全量加载测试 ═══
// 用户指令的 JS 侧面专项（统一版本比较规则 + 82300/plus 旧兼容链清理）：
//  ① isNewerByCode 统一规则矩阵：remoteVersionCode > currentVersionCode；
//     任一侧缺失/非法（≤0）→ 不提示；versionName 绝不参与判断；
//  ② 源码死灰复燃防线：cmpVer / versionCodeFromNotes / versionName 回退
//     不得回场；无 82300/plus 特殊放行/拦截/兼容路径；
//  ③ 浏览器预览路径：清单 versionCode 缺失 → remoteVersionCode=0 → 不判新
//     （绝不从 Release 正文 versionCode: 元数据行回退定码）。
const fs = require("fs");
const APPJS = __dirname + "/../assets/www/app.js";
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
globalThis.localStorage = { getItem: () => null, setItem: () => {} };
globalThis.location = { href: "", protocol: "https:", origin: "https://local" };
globalThis.addEventListener = () => {};
globalThis.setInterval = () => 0;
globalThis.setTimeout = () => 0;
globalThis.requestAnimationFrame = () => 0;

const TAIL = `
;globalThis.__T__ = {
  isNewerByCode: (typeof isNewerByCode === "function" ? isNewerByCode : null),
  mapManifest: (typeof mapManifest === "function" ? mapManifest : null),
  fetchLatest: (typeof fetchLatest === "function" ? fetchLatest : null),
};`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T || !T.isNewerByCode || !T.mapManifest || !T.fetchLatest) {
  console.error("✗ 统一规则符号未导出（app.js 加载失败或符号缺失）");
  process.exit(1);
}

let pass = 0, fail = 0;
function A(name, cond) {
  if (cond) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name); }
}

console.log("═══ S1 isNewerByCode 统一规则矩阵（v7.10.0）═══");
// 六场景矩阵（用户指令逐条钉死）
A("① 79900 → 本地 80000：不提示（旧清单）", T.isNewerByCode(79900, 80000) === false);
A("② 80000 → 本地 80000：同码不提示", T.isNewerByCode(80000, 80000) === false);
A("③ 80100 → 本地 80000：正常判新", T.isNewerByCode(80100, 80000) === true);
A("④ 79900 → 本地 80000：绝不提示降级", T.isNewerByCode(79900, 80000) === false);
A("⑤ 远端 0（无效/缺失）→ 本地 80000：不提示（不回退名比较）", T.isNewerByCode(0, 80000) === false);
A("⑥ 远端 80000 → 本地 0（无效）：不提示（不回退名比较）", T.isNewerByCode(80000, 0) === false);
// 升级路径回归
A("79800 → 79700：判新（v7.9.7→v7.9.8 升级路径）", T.isNewerByCode(79800, 79700) === true);
A("79700 → 79800：不提示（陈旧清单防降级）", T.isNewerByCode(79700, 79800) === false);
// plus 命名历史回归（82300 误判史——结构上根治）
A("82300 → 82200：判新（plus 命名无特殊路径）", T.isNewerByCode(82300, 82200) === true);
A("82200 → 82300：不提示（versionName 视角不放行）", T.isNewerByCode(82200, 82300) === false);
A("82300 → 82300：同码不提示", T.isNewerByCode(82300, 82300) === false);
// 边界值
A("负远端 → 不提示（无效输入）", T.isNewerByCode(-1, 80000) === false);
A("负本地 → 不提示（无效输入）", T.isNewerByCode(80000, -1) === false);
A("int 上界远端 → 判新（无溢出面）", T.isNewerByCode(2147483647, 80000) === true);

console.log("═══ S2 源码死灰复燃防线（统一规则专项）═══");
const noComments = src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^[ \t]*\/\/.*$/gm, "");
A("无 function cmpVer（versionName 三段比较已删）", !/function\s+cmpVer\s*\(/.test(src));
A("无 function versionCodeFromNotes（正文元数据行回退已删）", !/function\s+versionCodeFromNotes\s*\(/.test(src));
A("可执行面无 cmpVer 调用", !/cmpVer\s*\(/.test(noComments));
A("可执行面无 versionCodeFromNotes 调用", !/versionCodeFromNotes\s*\(/.test(noComments));
A("isNewerByCode 函数体不含 versionName/名比较输入（纯二参）",
  /function isNewerByCode\(\s*remoteVc\s*,\s*localVc\s*\)\s*\{\s*return\s+remoteVc\s*>\s*0\s*&&\s*localVc\s*>\s*0\s*&&\s*remoteVc\s*>\s*localVc;\s*\}/.test(src));
A("无 82300 特殊常量路径（可执行面）", !/===\s*82300|82300\s*===/.test(noComments));
A("无 7\\.9\\.6plus 字面量特殊路径（可执行面）", !/"7\.9\.6plus"/.test(noComments));
A("版本判定不依赖 appVer()（versionName 仅展示）",
  !/isNewerByCode\([^)]*appVer\(\)/.test(src));
A("isNewer 调用点仅传 versionCode 二参（无 tag/versionName 实参）",
  !/isNewerByCode\([^)]*appVer\(\)[^)]*\)/.test(src)
  && /isNewerByCode\(d\.remoteVersionCode \|\| 0, parseInt\(appCode\(\), 10\) \|\| 0\)/.test(src));

console.log("═══ S3 浏览器预览路径：清单 versionCode 缺失 → 不判新 ═══");
const REALISH = {
  schemaVersion: 1, versionName: "8.0.0", releaseTag: "v8.0.0",
  releaseNotes: "versionCode: 80000\n1. 功能一",  // 正文元数据行在场——必须被无视
  apkUrl: "https://github.com/xiaobaiba999/Skip-Ad/releases/download/v8.0.0/a.apk",
  apkFileName: "a.apk", apkSha256: "", apkSize: 1000, packageName: "com.zai.adshield",
};
const m = T.mapManifest({ ...REALISH });  // 无 versionCode 键
A("mapManifest 缺 versionCode → remoteVersionCode=0（不回退正文定码）",
  m.remoteVersionCode === 0 && m.versionCode === 0);
A("versionName 仅展示（映射保留）", m.versionName === "8.0.0");
A("统一判定：远端 0 → 本地 79700 不判新（正文 versionCode: 行被无视）",
  T.isNewerByCode(m.remoteVersionCode || 0, 79700) === false);
const m2 = T.mapManifest({ ...REALISH, versionCode: "79900" });  // 字符串数字
A("字符串数字 versionCode 容忍映射", m2.remoteVersionCode === 79900);

console.log("");
console.log("══════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("══════════════════════════");
process.exit(fail ? 1 : 0);
