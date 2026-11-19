import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.2.0 ── */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.2.0 朋友圈信息流广告真机命中根治（李跳跳方法论：原生文本直查·armed 直通·armed≥1 即出手）+ 白名单全量已安装应用列表（大小写不敏感搜索）+ 报告饼图折叠 + 多点误点防线 I 击数封顶 · 9品牌+子品牌 308 机型八维矩阵 · 4077 断言全绿 · 核心功能离线可用 · 免 Root · APK 817KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.2.0 规范发布体：1 标题 + 1 段落 + ≤5 条带序号功能）──
 * 与 App 内 core.ReleaseNotes 解析器同源：序号行优先解析（App「检查更新」
 * 当场显示这 5 条带序号功能标题）；# 章节名不入列；每条 ≤44 字。 */
const body = `开屏卫士 v7.2.0：信息流广告真机命中根治 + 白名单全量应用列表——用户实锤「app 内穿插的广告全都没拦截，参考李跳跳」四处死因全部根治（armed 指纹窗在菜单弹出的瞬间被窗切换清零——菜单 Dialog 就是新窗，最直接死因 / sns 窗门+12s 开屏窗闸对菜单窗双杀 / 300 节点预算制 DFS 够不到朋友圈大树深层卡片 / P2 双闸在真机单项菜单上零命中）；采用李跳跳方法论原生文本直查主路径（findAccessibilityNodeInfosByText 全树搜索·预算无关，候选仍逐个过语义+形态闸——宁漏勿误红线不变）；白名单改为全部已安装应用 + 浏览器式大小写不敏感搜索；报告改饼图常驻+明细折叠+到页自动刷新；多点误点防线 I 同指纹每窗 2 击封顶。versionCode 72000 > 71000——覆盖安装即升级（无障碍授权/学习数据/已导入订阅全部保留）。

1. 朋友圈信息流广告真机命中：原生直查+armed 直通+armed≥1 即出手
2. 白名单显示全部已安装应用，qqQQ 大小写不敏感搜索
3. 卫士报告饼图常驻+明细折叠，进入页面自动刷新
4. 多点误点防线 I：同目标每窗 2 击封顶，耐心 6→3
5. feed 未命中诊断日志随上传；信息流关闭单独记账

> 升级方式：[adshield-v7.2.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.2.0/adshield-v7.2.0.apk) 同签名直接覆盖安装（versionCode 72000——v7.0.0/v7.1.0 任何版本覆盖即升级；无障碍授权/学习数据/白名单/已导入订阅全部保留）。旧版 App 内「我的 → 检查更新」也会发现本版并页内直下——检查更新当场显示上方 5 条带序号功能描述。

| 校验 | 值 |
| --- | --- |
| MD5 | \`3100d6bd9e0fb4b58ae81a767e2120ad\` |
| 体积 | 837,174 B（817KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 72000 / 7.2.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.2.0",
    name: "开屏卫士 v7.2.0 · 朋友圈信息流广告真机命中根治（李跳跳方法论）· 白名单全量应用列表 · 4077 断言",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/skip-ad/android-app/dist/adshield-v7.2.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.2.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
console.log("APK UPLOAD", up.ok ? "OK " + apk.length + "B" : "FAIL " + up.status);
if (!up.ok) process.exit(1);
