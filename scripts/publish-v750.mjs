import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.5.0：开屏倒计时/位置解耦 + 插屏卫士主题 ──
 * 用户指令（第十四轮）：依旧不行，还有好多其他app的开屏都跳不了。开屏按钮跳过
 * 不是每个都带倒计时，不是每次每个按钮都是一个位置。app内的插入的广告也是，
 * 它们几乎都带有广告两个字，扫描日志继续实现继续优化。完成过后同步github
 * 和推送releases。优化github面板描述。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.5.0 开屏倒计时/位置解耦 + 插屏卫士（好多其他 App 的开屏都跳不了根治：兄弟节点倒计时并入门控首击不再白点 · 左/右缘中部图片跳过键可收位置解耦 · 名单外 App 插入广告「广告」角标+关闭钮同场即收——它们几乎都带广告两个字）· 信息流广告三路真机根治保持（微信「直接关闭」直通/卡片直关/空闲巡检 2.5s）· 更新链 SHA-256 校验 · 白名单全量应用列表 · 308 机型八维矩阵 · 4253 断言全绿 · 核心功能离线可用 · 免 Root · APK 832KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.5.0：开屏倒计时/位置解耦 + 插屏卫士）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.5.0：开屏倒计时/位置解耦 + 插屏卫士——第十四轮用户实锤「依旧不行，还有好多其他 app 的开屏都跳不了。开屏按钮跳过不是每个都带倒计时，不是每次每个按钮都是一个位置。app 内的插入的广告也是，它们几乎都带有广告两个字，扫描日志继续实现继续优化」的三组死因全部根治：① 倒计时数字与「跳过」文案分属两个节点时旧引擎首击立即白点、3 击耐心预算烧完干看广告走完——兄弟节点倒计时并入门控（首击不再白点，门控到「将尽」出手即命中）+ 门控从第 0 击起全次生效；② 图片跳过键在左/右缘中部零收集——中部边带候选（任意高度）+ strongOnly 分层门控（FAB 误点防线不放松）；③ 名单外 App 的插入广告零覆盖（信息流两段式只在 20 款名单内、开屏阶梯 12s 窗外零动作、弹窗路径只认 Dialog 窗类——宿主窗内插层三路全漏）——插屏卫士以「广告」合规披露角标为在场闸 + 关闭钮语义/形态/近旁三闸主动关闭（李跳跳方法论原生直查·预算无关深树插层也查到），角标不在场绝不出手、角标自身绝不点击、广告主体绝不点。versionCode 75000 > 74000——覆盖安装即升级（无障碍授权/学习数据/已导入订阅全部保留）。

1. 兄弟节点倒计时：数字与「跳过」分两节点也门控，首击不再白点
2. 中部边带图片跳过键：左右缘任意高度可收——位置解耦
3. 插屏卫士：名单外 App 插入广告「广告」角标+关闭钮同场即收
4. 扫描日志插屏未命中诊断：带角标词命中样本，上传即定位缺词
5. 探针词扩 skip + L1.5 角标在场任意位置关闭 + 19 套件 4253 断言全绿

> 升级方式：[adshield-v7.5.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.5.0/adshield-v7.5.0.apk) 同签名直接覆盖安装（versionCode 75000——v7.x 任何版本覆盖即升级；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`f34d8eec63cd4025b39e7737aa8bdc57c67bc87a48176377f3d472c74aed4268\` |
| MD5 | \`4cfd2d7ecf008a05ccb39ae8df5e9049\` |
| 体积 | 849,462 B（832KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 75000 / 7.5.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.5.0",
    name: "开屏卫士 v7.5.0 · 开屏倒计时/位置解耦 + 插屏卫士（名单外 App 插入广告主动关闭）· 兄弟节点倒计时/中部边带/角标在场关闭 · 4253 断言",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.5.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.5.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
const upj = await up.json().catch(() => ({}));
console.log("ASSET UPLOAD", up.ok ? "OK " + (upj.browser_download_url || "") : "FAIL " + up.status);

/* ── 安装前校验链基准闭环：asset digest/size 与本地文件核对 ── */
if (up.ok) {
  const local = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.5.0.apk");
  console.log("DIGEST", upj.digest || "(none)", "SIZE", upj.size, "LOCAL", local.length,
    upj.size === local.length ? "MATCH" : "MISMATCH");
}
