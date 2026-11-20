import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.8.0：视频开屏卫士 + 插屏重扫路由根治主题
 * 用户指令（第十八轮）：「完成过同步giuhub和推送releases。优化github面板描述
 * 加免责声明」。双死症根治：①视频开屏四死因（观察窗天花板/静默事件源/
 * 预算烧穿/L4 形态门）——VideoSplashGuard 画像驱动根治；②插屏五死因（重扫
 * 路由黑洞/首扫太早/同窗插层无人扫/闸⓪语境面窄/闸①②杀变体）——路由黑洞
 * 解除 + 延后复扫 + typeWindowsChanged + 语境三路并集。C 语言问题以五语言
 * 同构基准实测回答（校验和逐位一致——µs 级差异对命中率零贡献，保持 Java）。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.8.0 视频开屏卫士（带视频的开屏广告不再全漏：视频画像→观察窗 12s→20s + 1.5s 轮询针 + 白点节流/新键预算刷新 + L4 放宽）+ 插屏重扫路由根治（App 内插入广告不拦截关闭第 N 轮根治：路由黑洞解除 + 延后复扫 +450ms + typeWindowsChanged 补订 + 闸⓪语境三路并集 + 词族收编 + 宽扁放行）+ C 语言基准实测（五语言同构·校验和逐位一致——µs 级差异对命中率零贡献，保持 Java）· 激进模式/回切开屏/信息流三路全部保持 · 更新链 SHA-256 校验 · 396 机型十维矩阵 · 6063 断言全绿 · 免 Root · APK 833KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.8.0：视频开屏卫士 + 插屏重扫路由根治）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.8.0：视频开屏卫士 + 插屏重扫路由根治——第十八轮用户实锤双死症。①「只要是开屏广告带视频的你都未命中」四死因链根治：12s 观察窗天花板（视频开屏跳过钮常在视频末 5~15s 才露头，>12s 按钮引擎已出窗「不再扫」）→ 观察窗自适应延长 12s→20s；静默事件源（SurfaceView 视频播放零内容事件，零候选退避杀链后零事件零复活）→ 视频画像（窗龄 ≥2.2s+零候选连环 ≥3+树静默 ≥1.6s）+ 1.5s 轮询针（探针直查零 DFS——12~20s 段 ≥5 针覆盖固定梯子盲区）；预算烧穿（图片键门控期 3 击 1 秒烧光预算，按钮真激活时引擎已优雅收手）→ 白点节流 ≥1.5s/击 + 预算 3→5 + 新键预算刷新（视频末露头真按钮 ≠ 白点）；L4 形态确认门（WebView 视频容器树 >150 被拒）→ videoLike 入强广告形态位 + 树上限 260 + 时机窗 20s。视频开屏键词典：「5s 跳过」「5秒跳过」无「后」变体收编。②「app内插入广告也不会拦截关闭」第 N 轮根治——五死因链：重扫路由黑洞（开屏窗期内容事件全改道开屏补扫，插屏常在 App 打开后 3~5s 弹出恰在黑洞内，首扫一眼后整个生命周期无人再看）→ 路由黑洞解除 + 节流 2.5s→1.5s；首扫太早（窗切换帧树未长成空手后无复扫）→ 延后复扫 +450ms；同窗插层无人扫（无焦点 Dialog/overlay 挂载不产生任何被订阅事件）→ 补订 typeWindowsChanged；闸⓪语境面窄（H5 插屏角标在网页内核非原生节点、SDK 插屏窗类即铁证全被拒）→ 语境三路并集（语境词 16 字上限 + WebView 容器铁证 + SDK 插屏窗指纹）；闸①②杀变体（拉丁 X/点击关闭/宽扁横条钮被双杀）→ 词族收编 + 宽扁放行 + 终闸一致性 + feed 名单 20 款插屏孤儿化解除。C 语言问题以五语言同构基准实测回答：校验和逐位一致（sel_sink=731/hits=20000/sink=485562）——C 稳态 261.8ns 快 Java 4.1× 但均为 µs 级（事件面 80ms 节流下 5 个数量级余量，语言性能对命中率零贡献；C 摸不到 AccessibilityNodeInfo 必须 JNI 回 Java——保持 Java）。机型矩阵 352→396（11 组 × 36 · 2027-2028 世代）+ 第十维视频开屏逐机型验证。23 套件 6063 项 JVM 断言全绿（+964，新增 VideoSplashTest + InterstitialRoutingTest 专项套件）。versionCode 78000 > 77000——覆盖安装即升级（无障碍授权/学习数据/白名单/已导入订阅全部保留）。

1. 视频开屏卫士：画像→观察窗 12s→20s + 1.5s 轮询针 + 退避豁免（静默事件源兜底）
2. 视频窗预算根治：白点节流 ≥1.5s/击 + 预算 3→5 + 新键刷新 + L4 形态门放宽
3. 插屏重扫路由根治：黑洞解除 + 延后复扫 +450ms + typeWindowsChanged 补订
4. 插屏闸扩容：语境三路并集 + 词族收编（拉丁 X/点击关闭）+ 宽扁放行 + 终闸一致性
5. C 语言基准实测（五语言同构·校验和逐位一致——保持 Java）+ 矩阵 396 + 6063 全绿

> 升级方式：[adshield-v7.8.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.8.0/adshield-v7.8.0.apk) 同签名直接覆盖安装（versionCode 78000——视频开屏卫士 + 插屏重扫路由根治即刻生效；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`59761b7e58df33e1eedae8b3bb5619439f09a529e0708102b51d20a96d2cd96a\` |
| MD5 | \`5fba91c1a1e34d7dfc96c0b4725bf584\` |
| 体积 | 853,558 B（833KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 78000 / 7.8.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.8.0",
    name: "开屏卫士 v7.8.0 · 视频开屏卫士（带视频的开屏广告不再全漏）+ 插屏重扫路由根治 + 机型矩阵 396（十维）",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/skip-ad-repo/android-app/dist/adshield-v7.8.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.8.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
console.log("ASSET UPLOAD", up.ok ? "OK" : "FAIL " + up.status);

/* digest 核对（App 安装前 SHA-256 校验链——发布侧基准闭环） */
const got = await fetch(`https://api.github.com/repos/${REPO}/releases/${rel.id}`, { headers });
const rel2 = await got.json();
for (const a of rel2.assets ?? []) {
  if (a.name === "adshield-v7.8.0.apk") {
    console.log("ASSET digest", a.digest, "size", a.size, "state", a.state);
    const local = await import("crypto").then(c => c.createHash("sha256").update(apk).digest("hex"));
    console.log("LOCAL sha256", local, "MATCH", a.digest === "sha256:" + local ? "YES" : "NO");
  }
}
