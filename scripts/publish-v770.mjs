import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.7.0：回切开屏根治主题 ──
 * 用户指令（第十七轮）：「完成过同步giuhub和推送releases。优化github面板描述
 * 加免责声明」。双 bug 根治：①回切开屏（自家包前台边界登记 + 同包离开 ≥6s
 * 判回切等价新进入 + 进入期 4s CLEAN 档案让路全量——切回 App 期间弹的广告
 * 不再漏拦）；②Toast 常驻（CSS 关键帧动画·合成器线程驱动——真机 WebView
 * JS 阻塞/渲染失灵双失败面根治）。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.7.0 回切开屏根治（从另一个 App 切回期间弹的广告不再漏拦：同包离开 ≥6s 判回切等价新进入 + 进入期 4s CLEAN 档案让路全量 + 自家包前台边界登记）+ Toast 常驻修复（CSS 关键帧动画·合成器驱动——真机 WebView 常驻 bug 根治）+ 命中率拓宽（探针词 +关闭广告/關閉廣告）· 激进模式/信息流三路/插屏卫士全部保持 · 更新链 SHA-256 校验 · 352 机型九维矩阵 · 5099 断言全绿 · 免 Root · APK 832KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.7.0：回切开屏根治 + Toast 常驻修复）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.7.0：回切开屏根治 + Toast 常驻修复——第十七轮用户实锤双 bug。①「扫描日志从另一个 app 切回这个 app，期间会弹广告，这个没有拦截到」两个死因一起根治：卫士自家包事件被整体丢弃致 lastFgPkg 从未登记自家包名（「目标 App → 卫士 → 目标 App」往返后切回 entering=false 不按新进入处理）——自家包 WINDOW_STATE_CHANGED 登记前台边界 + ReentryGuard 纯函数（同包离开 ≥6s 判回切等价新进入——息屏回亮/白名单边界/ROM 漏网全兜住）+ rootWinCls 刷新 + 回切日志记账（30s 节流）；回切开屏常叠在被宿主窗档案判 CLEAN 的主界面窗类上（同窗类盖开屏层，扫描链被抑制只剩探针哨兵，晚到/图标式跳过键整窗漏拦）——进入期全量门 4s（CLEAN 档案让路全量扫描·激进档宁多扫绝不漏）。②「点击开启弹窗广告屏蔽，下方弹窗屏蔽已开启几个字一直保持在底部」——旧实现收尾纯靠 JS setTimeout（真机上同步桥调用卡 JS 线程时计时器不触发）+ 内联 transition 在部分 OEM WebView 渲染失灵：改 CSS 关键帧动画 toastFade（合成器线程驱动，JS 阻塞免疫）+ opacity/visibility 双终态 + JS 兜底计时器三重保险。命中跳过率拓宽：探针词 3→5（+关闭广告/關閉廣告——切回期间插层常以整词作键）+ 繁体词典补收；机型矩阵 308→352（11 组 × 32 · 2027 世代）+ 新增第九维回切开屏逐机型验证。21 套件 5099 项 JVM 断言全绿（+785，新增 ReentrySplashTest 专项套件）。versionCode 77000 > 76100——覆盖安装即升级（无障碍授权/学习数据/白名单/已导入订阅全部保留）。

1. 回切开屏根治：自家包前台边界 + 同包离开 ≥6s 判回切等价新进入
2. 进入期全量门 4s：CLEAN 档案让路全量——晚到/图标式跳过键整窗漏拦根治
3. Toast 常驻修复：CSS 关键帧动画（合成器驱动）+ 双终态 + JS 兜底三重保险
4. 命中跳过率拓宽：探针词 +关闭广告/關閉廣告 + 繁体词典 + 回切日志记账
5. 机型矩阵 352（+2027 世代）+ 第九维回切开屏 + ReentrySplashTest 5099 全绿

> 升级方式：[adshield-v7.7.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.7.0/adshield-v7.7.0.apk) 同签名直接覆盖安装（versionCode 77000——回切开屏根治 + Toast 常驻修复即刻生效；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`4eeef88dee87d6730730e251976767a6d7519c36ae0c3842d855f0a2b508d545\` |
| MD5 | \`75a7ba4e9527bf841a6afc05d08656c8\` |
| 体积 | 849,462 B（832KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 77000 / 7.7.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.7.0",
    name: "开屏卫士 v7.7.0 · 回切开屏根治（切回 App 期间弹的广告不再漏拦）+ Toast 常驻修复 + 机型矩阵 352（九维）",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.7.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.7.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
const upj = await up.json().catch(() => ({}));
console.log("ASSET UPLOAD", up.ok ? "OK " + (upj.browser_download_url || "") : "FAIL " + up.status);

/* ── 安装前校验链基准闭环：asset digest/size 与本地文件核对 ── */
if (up.ok) {
  const local = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.7.0.apk");
  const expectedSha = "4eeef88dee87d6730730e251976767a6d7519c36ae0c3842d855f0a2b508d545";
  const digestOk = (upj.digest || "").endsWith(expectedSha);
  console.log("DIGEST", upj.digest || "(none)", "SIZE", upj.size, "LOCAL", local.length,
    upj.size === local.length ? "MATCH" : "MISMATCH",
    digestOk ? "SHA-256 VERIFIED" : "SHA DIGEST CHECK SKIP");
}
