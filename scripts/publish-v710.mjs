import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.1.0 ── */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.1.0 规则使用顺序治理（订阅市场源优先→内置兜底→全局组垫底·两源命中分账）+ 朋友圈信息流广告两段式自动关闭（绝不点广告主体）· 白名单中文名搜索 · 卫士使用报告 · 9品牌+子品牌 308 机型八维矩阵 · 4067 断言全绿 · 核心功能离线可用 · 免 Root · APK 816KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.1.0 规范发布体：1 标题 + 1 段落 + ≤5 条带序号功能）──
 * 与 App 内 core.ReleaseNotes 解析器同源：序号行优先解析（App「检查更新」
 * 当场显示这 5 条带序号功能标题）；# 章节名不入列；每条 ≤44 字。 */
const body = `开屏卫士 v7.1.0：订阅规则使用顺序治理 + 信息流广告卫士——用户实锤「内置规则库和订阅市场规则的使用顺序和命中问题」三处病灶全部根治（市场规则被内置静态快照短路饿死 / 泛化全局规则压住专属精准规则 / 单源单候选拖死整轮）；微信朋友圈就业广告等 App 内信息流广告两段式自动关闭（v6.2 已验证实现整体移植·81 项单测回绿）；底层算法预筛提速；白名单中文名搜索与卫士使用报告落地；机型矩阵 286→308 升八维。versionCode 71000 > 70000——覆盖安装即升级（无障碍授权/学习数据/已导入订阅全部保留）。

1. 订阅规则使用顺序治理：市场源优先、内置兜底、全局组垫底
2. 微信朋友圈信息流广告两段式自动关闭，绝不点广告主体
3. 分应用规则行预筛提速，扫描日志增两源覆盖诊断
4. 白名单中文名搜索；新增卫士使用报告一键生成
5. 机型矩阵286→308八维；4067项断言全绿（+616）

> 升级方式：[adshield-v7.1.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.1.0/adshield-v7.1.0.apk) 同签名直接覆盖安装（versionCode 71000——v7.0.0/v6.0.0 任何版本覆盖即升级；无障碍授权/学习数据/白名单/已导入订阅全部保留）。旧版 App 内「我的 → 检查更新」也会发现本版并页内直下——检查更新当场显示上方 5 条带序号功能描述。

| 校验 | 值 |
| --- | --- |
| MD5 | \`5101b655d2ac993f97a97ddc37a24075\` |
| 体积 | 833,078 B（816KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 71000 / 7.1.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.1.0",
    name: "开屏卫士 v7.1.0 · 订阅规则使用顺序治理（市场优先·内置兜底）· 朋友圈信息流广告两段式关闭 · 4067 断言",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/skip-ad/android-app/dist/adshield-v7.1.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.1.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
console.log("APK UPLOAD", up.ok ? "OK " + apk.length + "B" : "FAIL " + up.status);
if (!up.ok) process.exit(1);
