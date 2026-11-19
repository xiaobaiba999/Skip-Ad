import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.3.0：安全加固主题 ──
 * 用户指令：优化 GitHub 面板描述（本轮叠加安全主题）。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.3.0 安全加固+算法优化（更新包安装前 SHA-256 逐字节校验 · 订阅规则支付/诱导词双否决防注入 · 解析器深度与体量硬闸防 DoS · 域名精确白名单防构造绕过）· 朋友圈信息流广告真机命中（李跳跳方法论）· 白名单全量应用列表 · 9品牌+子品牌 308 机型八维矩阵 · 4077 断言全绿 · 核心功能离线可用 · 免 Root · APK 824KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.3.0：安全加固 + 算法优化专项）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。 */
const body = `开屏卫士 v7.3.0：安全加固 + 算法优化（防投毒/防劫持/防注入）——对全仓做三路安全审计（网络/更新/订阅链 · 规则引擎算法 · WebView/前端/导出组件），把三类「他人可攻击」路径全部焊死：① 更新链投毒（镜像代理被劫持/公共 Downloads 目录换包此前零校验——现安装前流式计算 SHA-256 与 GitHub API 官方 digest 逐字节比对，不符自动删除重下）；② 订阅规则注入（恶意订阅源定向代点「确认支付」类按钮——现支付资金词 19 词 + 营销诱导词双否决对全部规则生效，规则名不再能作为终闸语义证据）；③ 解析器 DoS（深嵌套 StackOverflowError 打崩无障碍服务——现深度 ≤64 + 体量五重上限 + 响应体 4MB 硬闸）。算法侧扫描热路径全面零分配化。versionCode 73000 > 72000——覆盖安装即升级（无障碍授权/学习数据/已导入订阅全部保留）。

1. 更新包安装前 SHA-256 逐字节校验：投毒/换包自动拦截删除
2. 订阅规则注入防线：支付/诱导词双否决 + 规则名不作语义证据
3. 解析器 DoS 硬闸：深度 ≤64 + 体量五重上限 + 响应体 4MB
4. 热路径零分配优化：vid 分段/指纹缓存/后缀预计算/tr-TR 修复
5. 多点防线 II：nudge 旁路按人手节奏封死 + 指纹表按时间淘汰

> 升级方式：[adshield-v7.3.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.3.0/adshield-v7.3.0.apk) 同签名直接覆盖安装（versionCode 73000——v7.x 任何版本覆盖即升级；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`3905dc45a62693dd549604e0a916af4934a90717ce814e36d0f6a5fe1e42c973\` |
| MD5 | \`1577bc2aa220c3fec6af0533930f382a\` |
| 体积 | 841,270 B（824KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 73000 / 7.3.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.3.0",
    name: "开屏卫士 v7.3.0 · 安全加固 + 算法优化（防投毒/防劫持/防注入）· 更新链 SHA-256 校验 · 4077 断言",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.3.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.3.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
const upj = await up.json().catch(() => ({}));
console.log("ASSET UPLOAD", up.ok ? "OK " + (upj.browser_download_url || "") : "FAIL " + up.status);

/* 上传后核对：GitHub API asset digest 必须与本地 SHA-256 一致——
 * 这是本版新增「安装前 SHA-256 校验链」的发布侧基准。 */
const check = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers });
const cj = await check.json().catch(() => ({}));
const asset = (cj.assets || []).find(a => (a.name || "").endsWith(".apk"));
console.log("LATEST:", cj.tag_name, "| digest:", asset ? asset.digest : "N/A",
  "| size:", asset ? asset.size : "N/A",
  "| expect sha256:3905dc45a62693dd549604e0a916af4934a90717ce814e36d0f6a5fe1e42c973");
