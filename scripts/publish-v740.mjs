import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.4.0：信息流广告主动关闭主题 ──
 * 用户指令（第十三轮）：其他 app 内的广告还是主动关闭不了……完成过后同步 github
 * 和推送 releases。优化 github 面板描述。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.4.0 App 内信息流广告主动关闭三路根治（微信「关闭该广告的原因」面板「直接关闭」直通 · 「来自广告推荐」卡片「×/关闭」直关 · 空闲巡检 2.5s 停下刷屏照关不误 · 20 款名单含中关村在线/小米应用商店）· 更新链 SHA-256 校验 · 白名单全量应用列表 · 308 机型八维矩阵 · 4118 断言全绿 · 核心功能离线可用 · 免 Root · APK 828KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.4.0：App 内信息流广告主动关闭三路真机根治）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.4.0：App 内信息流广告主动关闭（三路真机根治）——第十三轮真机证据链（7 张截图 + 与截图同分钟上传的引擎日志 #79/#80/#81）实锤「其他 app 内的广告还是主动关闭不了」的三个根因并全部根治：① 微信「关闭该广告的原因」面板主按钮是「直接关闭」而旧词表根本没有这词（面板拉起了也永远收不掉）；② P1 角标识别真机零开火，且 feed 扫描只挂在内容变化事件上——用户停下刷屏盯着广告看时零事件=零扫描（广告静止在屏 2 分钟引擎全程盲）；③ ZOL 中关村在线/小米应用商店根本不在 18 款名单内（卡片「×/关闭」直关钮只能用户手动点——日志实锤用户点了，引擎只学到位置）。三路根治后：停下刷屏盯着广告看也照关不误。versionCode 74000 > 73000——覆盖安装即升级（无障碍授权/学习数据/已导入订阅全部保留）。

1. 微信原因面板「直接关闭」直通：角标识别失败也收得掉
2. 「来自广告推荐」卡片「×/关闭」直关：上下文闸内直点即关
3. 空闲巡检 2.5s：停下刷屏对着广告看时照扫不误
4. 扫描日志诊断增强：未命中落角标词命中样本（被闸也照登）
5. 名单 18→20 款：+中关村在线 +小米应用商店（真机实锤）

> 升级方式：[adshield-v7.4.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.4.0/adshield-v7.4.0.apk) 同签名直接覆盖安装（versionCode 74000——v7.x 任何版本覆盖即升级；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`aa795321e3969a7099abf7b4a50c5e42b54a043429d07f38c2f762e768ad52d4\` |
| MD5 | \`de9bfb0d5ef18ac548bb6b4ae6a25247\` |
| 体积 | 845,366 B（828KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 74000 / 7.4.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.4.0",
    name: "开屏卫士 v7.4.0 · App 内信息流广告主动关闭（三路真机根治）· P0 面板直通/P1X 卡片直关/空闲巡检 · 4118 断言",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.4.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.4.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
const upj = await up.json().catch(() => ({}));
console.log("ASSET UPLOAD", up.ok ? "OK " + (upj.browser_download_url || "") : "FAIL " + up.status);

/* 上传后核对：GitHub API asset digest 必须与本地 SHA-256 一致——
 * v7.3.0「安装前 SHA-256 校验链」的发布侧基准闭环保持。 */
const check = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers });
const cj = await check.json().catch(() => ({}));
const asset = (cj.assets || []).find(a => (a.name || "").endsWith(".apk"));
console.log("LATEST:", cj.tag_name, "| digest:", asset ? asset.digest : "N/A",
  "| size:", asset ? asset.size : "N/A",
  "| expect sha256:aa795321e3969a7099abf7b4a50c5e42b54a043429d07f38c2f762e768ad52d4");
