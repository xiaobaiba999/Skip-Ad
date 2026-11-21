import { getGhToken } from "./gh-token.mjs";
import { readFileSync, existsSync, statSync } from "fs";
import { createHash } from "crypto";

/* ── v7.9.7 发布脚本（PAT 移除安全轮）────────────────────────────
 * 与 publish-v796.mjs 同流程（面板描述同步 → Release 创建 → APK 上传 →
 * digest 核对），两点升级：
 *   ① digest/体积改为运行时从 dist/adshield-v7.9.7.apk 现场计算（发布侧
 *      基准永远与实际资产一致——不再硬编码，杜绝抄错位）；
 *   ② dist APK 缺失时立即退出（没有构建产物绝不空跑发布）。
 * 前置：android-app/build.sh 79700 7.9.7 dist/adshield-v7.9.7.apk 已构建。 */
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v7.9.7.apk";
if (!existsSync(APK)) {
  console.error("✗ 未找到构建产物 " + APK + "——先运行 android-app/build.sh 79700 7.9.7 dist/adshield-v7.9.7.apk");
  process.exit(1);
}
const apk = readFileSync(APK);
const sha256 = createHash("sha256").update(apk).digest("hex");
const md5 = createHash("md5").update(apk).digest("hex");
const sizeKB = Math.round(apk.length / 1024);
console.log("APK", apk.length, "bytes ·", sizeKB + "KB ·", sha256.slice(0, 16) + "…");

const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述同步 v7.9.7：PAT 移除安全轮（五段式保持）── */
const desc = `无障碍驱动的安卓开屏广告秒跳引擎 · v7.9.7 安全轮：内置 GitHub PAT 从 APK 移除（混淆≠加密——客户端零写凭据）· 自动日志上传授权默认关 + 中转服务上线前结构性关闭 · 手动日志本地导出 + 系统分享 · 检查更新匿名化 · 31 套件 6653 断言全绿（零回退）· 零识别/安全门改动（安全>正确关闭>命中率）· L3 批次 1 保持 PENDING_REAL_DEVICE（无真机绝不虚报）· 免 Root · APK ${sizeKB}KB · 更新链 SHA-256 校验 · 396 机型十维矩阵（使用前请阅 README 免责声明）`;
console.log("DESC length =", [...desc].length);
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);
if (!dr.ok) { const e = await dr.text(); console.log(e.slice(0, 200)); }

/* ── Release notes 正文（v7.9.7 · PAT 移除安全轮）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。 */
const body = `开屏卫士 v7.9.7：安全轮——自动日志上传系统第一步（无需公网服务的安全改造）。用户指令主线「GitHub Issues 私有日志仓库 + 轻量 HTTPS 中转服务 + 24 小时最多 3 次 + 幂等与安全」：完整系统（Skip-Ad-Logs 私有仓库 + Cloudflare Worker 中转 + LogBatchStore 批次持久化）为 v8.0.0 落地，本版本先完成 P0 前置——APK 零 GitHub 凭据。P0 安全实锤（CURRENT_CONFIRMED）：Net.builtInToken 内嵌混淆 PAT（双层 XOR+Base64，与 v7.9.4 已移除的 Web 端 credentials.ts 同源密文）随每个公开 APK 分发；v7.9.6 发布说明已实测该令牌仍有效（GET /user 200）→ 撤销待仓库所有者执行（SECURITY.md R1）。移除（三处调用面全清）：builtInToken 令牌机制 + postIssue（自动/手动日志直发 GitHub Issue——APK 内绝不再有任何 GitHub 写凭据）+ latestRelease 鉴权头（检查更新改匿名，v3.3.0 重试 + /releases 列表回退链路不变；新增 newIssuePrefillUrl 浏览器预填链接，用户登录态自助提交零令牌）。自动上传旧通道（v4.2.0 起 KeepAliveReceiver 心跳 8h 直发 Issue）整体下线：maybeAutoUploadLog 重写为双门结构——授权门（KEY_AUTO_LOG_UPLOAD 默认 false，与无障碍授权严格分离：开无障碍 ≠ 同意日志上传）+ 中转门（Net.LOG_RELAY_URL 空串 = 结构性关闭）任一未过即零行为，绝不回退直连 GitHub；8h AUTO_LOG_MS 保留为 v8.0.0 客户端节拍（最终限额以服务端 24h 滑窗最多 3 次为准）。手动日志本地导出：MainActivity 新桥 exportLog（应用外部私有目录 logs/ · 无新权限 · Java 侧 Net.maskSensitive 二次脱敏保持 JS+Java 双层）+ shareLogText 系统分享面板（用户自行投递）；netPostIssue 桥删除；崩溃堆栈自动附带（读后即删）保持。设置页新增自动提交诊断日志开关（默认关 + 状态行「服务器上线前不执行」）。WebView 白名单制不变（桥已无凭据能力，纵深防御不松绑）。本轮零识别/安全门改动：不新增关键词、不扩大点击区域、不降 SafetyGuard、不加轮询、不动任务状态机。测试与闸门：31 套件 6653 项 JVM 断言全绿（修改前后各跑一轮零回退）· 全源 ECJ 编译通过（android-34）· secret-scan/check-appjs/version-sync 全绿。versionCode 79700 > 79600——覆盖安装即升级（无障碍授权/学习数据/白名单/已导入订阅全部保留）。

1. P0：内置 GitHub PAT 从 APK 移除——客户端零写凭据（混淆≠加密）
2. 自动上传旧通道下线：授权默认关 + 中转服务上线前结构性关闭
3. 手动日志本地导出 + 系统分享（替代直发 Issue，双层脱敏保持）
4. 检查更新匿名化 + 浏览器预填链接（零令牌）
5. 31 套件 6653 断言全绿 · 零识别/安全门改动 · versionCode 79700

> 升级方式：[adshield-v7.9.7.apk](https://github.com/xiaobaiza999-placeholder/Skip-Ad/releases/download/v7.9.7/adshield-v7.9.7.apk) 同签名直接覆盖安装（versionCode 79700——无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

> 诚实口径：REAL_DEVICE_TEST = NOT_AVAILABLE（无真机）——L3 批次 1 保持 PENDING_REAL_DEVICE；本版本未部署任何公网中转服务（v8.0.0 前自动上传结构性关闭）。SECURITY ACTION REQUIRED：旧 GitHub PAT 未撤销——需账号所有者在 GitHub Settings 撤销（撤销后 v7.9.6 及更早 APK 内的令牌即刻失效——正是本轮移除的意义）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`${sha256}\` |
| MD5 | \`${md5}\` |
| 体积 | \`${apk.length.toLocaleString("en-US")}\` B（${sizeKB}KB，含三源离线订阅快照） |
| versionCode / versionName | 79700 / 7.9.7 |
| minSdk / targetSdk | 24 / 34 |`;

/* 升级链接占位修正：下载 URL 的 owner 与仓库常量一致（防手工拼错） */
const bodyFixed = body.replace("xiaobaiza999-placeholder/Skip-Ad", REPO);

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.9.7",
    name: "开屏卫士 v7.9.7 · 安全轮：内置 PAT 移除（APK 零 GitHub 凭据）+ 自动日志上传授权（默认关）+ 手动日志本地导出",
    body: bodyFixed,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.9.7.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
console.log("ASSET UPLOAD", up.ok ? "OK" : "FAIL " + up.status);

/* digest 核对（App 安装前 SHA-256 校验链——发布侧基准闭环） */
const got = await fetch(`https://api.github.com/repos/${REPO}/releases/${rel.id}`, { headers });
const rel2 = await got.json();
for (const a of rel2.assets ?? []) {
  if (a.name === "adshield-v7.9.7.apk") {
    console.log("ASSET digest", a.digest, "size", a.size, "state", a.state);
    console.log("LOCAL sha256", sha256, "MATCH", a.digest === "sha256:" + sha256 ? "YES" : "NO");
  }
}

/* 面板描述远端核对（≤350 字符硬限） */
const got2 = await fetch(`https://api.github.com/repos/${REPO}`, { headers });
const repo2 = await got2.json();
console.log("REMOTE DESC CHECK", repo2.description === desc ? "MATCH" : "DRIFT", "len", [...(repo2.description ?? "")].length);
