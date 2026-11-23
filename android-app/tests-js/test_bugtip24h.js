// ═══ v7.11.0 兜底与状态修复专项 · 真实 app.js 全量加载 + 源码级接线断言 ═══
// 覆盖（用户指令四项的 JS/接线面）：
//  ① 检查更新失败兜底：FALLBACK_CDN_LATEST 直链常量 + doFallbackDirectDownload
//    + mapManifest cdnApkUrl → apkMirrors（CDN 兜底镜像切换面）；
//  ② 开屏提醒：pin_tip_confirmed_at / pin_tip_shown_at 时间戳键（与 Java
//    kvSet 白名单一一对应）+ 更新优先（showUpdateGate 收起 #pin-tip）；
//  ③ 底栏订阅模块移除：index.html 无 page-sub、app.js 无订阅页接线；
//    帮助页 page-help 在场；
//  ④ 冷启动宽限期：serviceGraceActive 真值表（桥桩时间戳驱动）+
//    renderHero「服务重连中」分支在场（重进不再误导向系统设置）；
//  ⑤ 版本判定真值表（v7.10.0 统一规则保持）+ cmpVer 死灰复燃防线；
//  ⑥ Java 侧接线（源码级）：SubscriptionManager.maybeDailyAutoImport /
//    AUTO_SUB_URL（id667·Lin-arm）/ AdSkipService KEY_LAST_CONNECTED_AT /
//    MainActivity kvSet 白名单 / Downloader hostAllowed jsDelivr 面。
const fs = require("fs");
const APPJS = __dirname + "/../assets/www/app.js";
const INDEXHTML = __dirname + "/../assets/www/index.html";
const src = fs.readFileSync(APPJS, "utf8");
const html = fs.readFileSync(INDEXHTML, "utf8");

/* ── Stage 1：catch-all stub 环境加载完整 app.js（桥桩在场版）── */
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

/* 桥桩：宽限期判定输入（serviceLastConnectedAt 可控） */
const br = { lastConnectedAt: "0", serviceRunning: false, a11y: false };
globalThis.AndroidApp = {
  kvGet: (k) => (lsMap.has("adshield_" + k) ? lsMap.get("adshield_" + k) : null),
  kvSet: (k, v) => lsMap.set("adshield_" + k, v),
  versionName: () => "7.11.0",
  versionCode: () => 81000,
  isServiceRunning: () => br.serviceRunning,
  a11yEnabledInSettings: () => br.a11y,
  serviceLastConnectedAt: () => br.lastConnectedAt,
  updateCheckAsync: () => {},
};

const REAL_NOW = Date.now();
let nowMock = null;
Date.now = () => (nowMock === null ? REAL_NOW : nowMock);

const TAIL = `
;globalThis.__T__ = {
  serviceGraceActive, mapManifest, isNewerByCode, appVer, appCode,
  HAS_BRIDGE, FALLBACK_CDN_LATEST,
  openUrls: [],
};`;
(0, eval)(src + TAIL);
const T = globalThis.__T__;
if (!T) { console.error("✗ app.js 顶层加载失败（__T__ 未导出）"); process.exit(1); }

/* doFallbackDirectDownload 的 openUrl 出口拦截（源码级接线即可——openUrl
 * 走 bridge/浏览器 window.open，测试只验证常量与函数在场）。 */

let pass = 0, fail = 0;
function A(name, cond) {
  if (cond) { pass++; console.log("  ✓ " + name); }
  else { fail++; console.log("  ✗ " + name); }
}

console.log("═══ S0 加载与版本兜底 ═══");
A("app.js 全量顶层加载成功（桥桩环境）", true);
A('appVer() 浏览器兜底 = "7.11.0"', T.appVer() === "7.11.0");
A('appCode() 浏览器兜底 = "81000"', T.appCode() === "81000");

console.log("═══ S1 检查更新失败 CDN 兜底 ═══");
A("FALLBACK_CDN_LATEST = jsDelivr 仓库内 adshield-latest.apk 直链（v7.14.0 接入点 gcore 化——cdn 当日对 APK 403 实测定案）",
  T.FALLBACK_CDN_LATEST === "https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-latest.apk");
A("doFallbackDirectDownload 函数在场（失败→直连下载出口）",
  /function doFallbackDirectDownload\s*\(/.test(src));
A("失败分支接线：note 报错 + dlBtn 置为 fallback 相（「直连下载最新版（CDN 兜底）」）",
  src.includes('dlBtn.textContent = "直连下载最新版（CDN 兜底）"') && src.includes('dlBtn.dataset.phase = "fallback"'));
A("page-dl-btn 点击 fallback 相 → doFallbackDirectDownload()",
  /dataset\.phase === "fallback"\) \{ doFallbackDirectDownload\(\); return; \}/.test(src));
A("「查询失败」错误转达语义保持（updateErrText 出口）",
  src.includes('note.textContent = "查询失败：" + (updateErrText(d.errorCode) || d.error || "未知")'));

console.log("═══ S2 mapManifest：清单 cdnApkUrl → apkMirrors ═══");
const m1 = T.mapManifest({ versionCode: 81000, releaseTag: "v7.11.0", apkUrl: "https://github.com/x/x.apk", cdnApkUrl: "https://cdn.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.11.0.apk", apkSha256: "a".repeat(64), apkSize: 100 });
A("清单携带 cdnApkUrl → apkMirrors[0] = cdnApkUrl", m1.apkMirrors.length === 1 && m1.apkMirrors[0].includes("cdn.jsdelivr.net"));
const m2 = T.mapManifest({ versionCode: 81000, releaseTag: "v7.11.0", apkUrl: "https://github.com/x/x.apk", apkSha256: "a".repeat(64), apkSize: 100 });
A("清单无 cdnApkUrl → apkMirrors = []（主源单一不虚挂）", m2.apkMirrors.length === 0);

console.log("═══ S3 冷启动宽限期 serviceGraceActive 真值表 ═══");
br.lastConnectedAt = String(REAL_NOW - 60 * 1000);   // 1 分钟前连接过
A("刚断开 1 分钟（宽限窗内）→ true", T.serviceGraceActive() === true);
br.lastConnectedAt = String(REAL_NOW - 11 * 60 * 1000); // 11 分钟前
A("断开 11 分钟（超出 10min 窗）→ false", T.serviceGraceActive() === false);
br.lastConnectedAt = "0";                              // 从未连接
A("从未连接（0）→ false（全新安装不误显重连中）", T.serviceGraceActive() === false);
br.lastConnectedAt = String(REAL_NOW + 3600 * 1000);   // 未来时间戳（时钟回拨遗留）
A("未来时间戳（回拨面）→ 负时长仍判宽限中（防误导向系统设置）", T.serviceGraceActive() === true);
br.lastConnectedAt = String(REAL_NOW - 60 * 1000);

console.log("═══ S4 renderHero 宽限分支与时间戳键 ═══");
A("renderHero「服务重连中」分支在场（源码级）",
  src.includes('title.textContent = "服务重连中"') && src.includes("无需重新开启、不用去系统设置"));
A("pin_tip_confirmed_at / pin_tip_shown_at 时间戳键在场（与 Java kvSet 白名单对应）",
  src.includes('store.get("pin_tip_confirmed_at"') && src.includes('store.set("pin_tip_confirmed_at"'));
A("pin-tip 两个按钮：知道了（不耗资格）+ 24 小时内不再显示（写 confirmed_at）",
  src.includes('id="pin-tip-ok"') && src.includes('id="pin-tip-snooze"'));
A("showUpdateGate 收起 #pin-tip（更新优先）", src.includes('$("#pin-tip")'));

console.log("═══ S5 底栏订阅模块移除 / 帮助页在场 ═══");
A("index.html 无 #page-sub（订阅页 DOM 移除）", !html.includes('id="page-sub"'));
A("index.html 无订阅市场/导入按钮残留", !html.includes('id="sub-market"') && !html.includes('id="btn-sub-import"') && !html.includes('id="sub-url"'));
A("index.html 底栏 = 首页 / 帮助 / 我的（data-page 三枚）",
  html.includes('data-page="home"') && html.includes('data-page="help"') && html.includes('data-page="me"') && !html.includes('data-page="sub"'));
A("index.html 帮助页关键卡在场（v7.12.0 精简版：快速上手/让守护更稳/常见问题）",
  html.includes("快速上手") && html.includes("让守护更稳") && html.includes("常见问题"));
A("v7.12.0 帮助页去冗余：开屏提示文案不再在帮助页重复（无「固定在后台（重要）」旧卡）",
  !html.includes("把 App 固定在后台（重要）") && !html.includes("订阅规则（自动）") && !html.includes("⭐7.4k"));
A("帮助页常见问题含「遇到广告没跳过→去上传日志」指引（v7.12.0 三按钮闭环）",
  html.includes("开屏提醒里点「去上传日志」"));
A("app.js 无订阅页接线残留（refreshSub/sub-market/导入按钮）",
  !/refreshSub|sub-market|btn-sub-import|renderSubMarket|__subProgress/.test(src));

console.log("═══ S6 版本判定真值表（v7.10.0 统一规则保持）═══");
A("isNewerByCode(81100 → 本地 81000) = true（检测到新版）", T.isNewerByCode(81100, 81000) === true);
A("isNewerByCode(81000 → 本地 81000) = false（不自误报）", T.isNewerByCode(81000, 81000) === false);
A("isNewerByCode(81000 → 本地 82300) = false（82300 用户不误报）", T.isNewerByCode(81000, 82300) === false);
A("isNewerByCode(0 → 本地 81000) = false（远端无码不判新）", T.isNewerByCode(0, 81000) === false);
A("isNewerByCode(81000 → 本地 0) = false（本地无码不判新）", T.isNewerByCode(81000, 0) === false);
A("isNewerByCode(79900 → 本地 81000) = false（绝不提示降级）", T.isNewerByCode(79900, 81000) === false);

console.log("═══ S7 cmpVer/versionCodeFromNotes 死灰复燃防线 ═══");
A("app.js 源无 function cmpVer", !/function cmpVer\s*\(/.test(src));
A("app.js 源无 function versionCodeFromNotes", !/function versionCodeFromNotes\s*\(/.test(src));
const noComments = src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^[ \t]*\/\/.*$/gm, "");
A("app.js 可执行面无 cmpVer 调用点", !/cmpVer\s*\(/.test(noComments));
A("app.js 可执行面无 versionCodeFromNotes 调用点", !/versionCodeFromNotes\s*\(/.test(noComments));

console.log("═══ S8 Java 侧接线（源码级——跨语言契约面）═══");
const SUBMGR = fs.readFileSync(__dirname + "/../../android-app/src/com/zai/adshield/SubscriptionManager.java", "utf8");
A("SubscriptionManager.maybeDailyAutoImport 在场（ID667 每日自动导入入口）",
  /public static void maybeDailyAutoImport/.test(SUBMGR));
A("AUTO_SUB_URL = Lin-arm GKD_subscription（id667 · ⭐7.4k）",
  SUBMGR.includes("Lin-arm/GKD_subscription@main/dist/gkd.json5"));
A("失败重试闸 subAutoImportNextAt（1h 冷却防风暴）", SUBMGR.includes("subAutoImportNextAt"));
const KAR = fs.readFileSync(__dirname + "/../../android-app/src/com/zai/adshield/KeepAliveReceiver.java", "utf8");
A("心跳 selfCheck 调 maybeDailyAutoImport（保底覆盖）", /maybeDailyAutoImport\(ctx\);\s*SubscriptionManager\.maybeSilentUpdate/.test(KAR));
const SVC = fs.readFileSync(__dirname + "/../../android-app/src/com/zai/adshield/AdSkipService.java", "utf8");
A("服务连接时调用 maybeDailyAutoImport（后台线程）", /sub-auto-import/.test(SVC));
A("KEY_LAST_CONNECTED_AT = lastServiceConnectedAt（宽限期数据源）",
  SVC.includes('"lastServiceConnectedAt"'));
const MA = fs.readFileSync(__dirname + "/../../android-app/src/com/zai/adshield/MainActivity.java", "utf8");
A("kvSet 白名单含 pin_tip_confirmed_at / pin_tip_shown_at（JS 键 ↔ Java 闸）",
  MA.includes('"pin_tip_confirmed_at"') && MA.includes('"pin_tip_shown_at"'));
A("serviceLastConnectedAt 桥在场", /public String serviceLastConnectedAt/.test(MA));
const DL = fs.readFileSync(__dirname + "/../../android-app/src/com/zai/adshield/Downloader.java", "utf8");
A("hostAllowed 允许 jsDelivr 四接入点（CDN 兜底下载域）",
  DL.includes('"cdn.jsdelivr.net"') && DL.includes('"testingcf.jsdelivr.net"'));
const UM = fs.readFileSync(__dirname + "/../../android-app/src/com/zai/adshield/UpdateManifest.java", "utf8");
A("UpdateManifest 解析 cdnApkUrl + 校验（非法兜底字段不污染清单）",
  UM.includes('d.optString("cdnApkUrl"') && UM.includes("hostAllowed(cdnApkUrl)"));
A("非法 cdnApkUrl → 置空忽略（不清真伪绝不拒整份清单）", UM.includes('cdnApkUrl = ""'));
A("cdnApkUrl 入 apkMirrors（下载状态机镜像切换面）", UM.includes("mirrors.put(cdnApkUrl)"));

console.log("\n════════════════════════");
console.log(`  通过 ${pass} · 失败 ${fail}`);
console.log("════════════════════════");
process.exit(fail ? 1 : 0);
