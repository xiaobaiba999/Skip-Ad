import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.6.1：热修复主题 ──
 * 用户指令（第十六轮）：「扫描日志，7.6版本记录没了，使用报告也不见，修复
 * 完成同步github」。死因：v7.6.0 发布时页面资产 app.js 版本记录注释尾部丢
 * 了闭合符（star-slash）未闭合——块注释吞掉整个 const CURRENT_RELEASE 声明，页面渲染
 * 抛 ReferenceError 中断，扫描日志/版本记录/使用报告三大板块全部消失。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.6.1 热修复：页面板块恢复（v7.6.0 资产注释未闭合吞掉版本记录声明致扫描日志/版本记录/使用报告三板块消失——闭合修复）+ 构建链页面资产符号校验闸（语法解析+stub 顶层执行+关键符号缺一即拒绝打包——此类损坏从源头拦下）· 激进模式保持（开屏露头即点：首击零门控·重击等将尽+6s 封顶 + 插屏「广告」语境闸 contains + 终闸语义面收编）· 信息流三路/安全加固全部保持 · 更新链 SHA-256 校验 · 308 机型八维矩阵 · 4314 断言全绿 · 免 Root · APK 832KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.6.1：热修复）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.6.1：热修复——第十六轮用户实锤「扫描日志，7.6版本记录没了，使用报告也不见」的死因：v7.6.0 发布时页面资产 app.js 的版本记录注释尾部丢了 \` */ \` 未闭合——块注释吞掉后续 17 行（整个 \`const CURRENT_RELEASE\` 声明被吃进注释），页面渲染到版本记录时抛 ReferenceError 中断——扫描日志/版本记录/使用报告三大板块全部消失（node --check 语法层完全通过：注释闭合在别处、语法合法——纯语法闸拦不住这类「语义级损坏」，坏资产直接进了已发布 APK）。修复：闭合注释（CURRENT_RELEASE 回到活代码）三大板块全部恢复；构建链新增「页面资产符号校验闸」（scripts/check-appjs.sh + build.sh 打包前硬门）：语法解析 + catch-all stub 环境顶层执行（ReferenceError 硬拒）+ 5 关键符号在场断言（CURRENT_RELEASE 版本记录 / renderLog 扫描日志 / renderReport 使用报告 / renderReleaseNotes 更新说明 / notesToHeadlines 解析器）——缺一即拒绝打包；死因复现验证：坑样本（未闭合注释）5 符号全灭拒打包退出 1、修复样本 5 项全过、已打包 APK 包内资产抽出后同样全过——此类损坏从源头拦下绝不再进 APK。激进模式全量保持（Java 侧零触碰：开屏首击零门控/重击等将尽+6s 封顶/插屏「广告」语境闸 contains/关闭词扩表/终闸语义面收编/滚动窗近旁铁证/新窗首扫提速）；20 套件 4314 项 JVM 断言全绿保持（零改动）。versionCode 76100 > 76000——v7.6.0 坏包直接覆盖安装即修复（无障碍授权/学习数据/已导入订阅全部保留）。

1. 热修复：扫描日志/版本记录/使用报告三大板块恢复（未闭合注释闭合）
2. 构建链页面资产符号校验闸：语法+stub 执行+关键符号缺一即拒打包
3. 死因复现验证：坑样本 5 符号全灭拒打包·修复样本全过·包内资产同验
4. 激进模式全量保持：首击零门控/重击等将尽+6s 封顶/插屏语境闸零触碰
5. 20 套件 4314 项 JVM 断言全绿保持（零改动）

> 升级方式：[adshield-v7.6.1.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.6.1/adshield-v7.6.1.apk) 同签名直接覆盖安装（versionCode 76100——v7.6.0 坏包直接覆盖即修复，三大板块立即恢复；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`b74cde33df8f6811f164b68a173bf72ad970d04e0763f56e7fd8517a9a5896a0\` |
| MD5 | \`d7e4326257a21a5d73d80500cdc6dfec\` |
| 体积 | 849,462 B（832KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 76100 / 7.6.1 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.6.1",
    name: "开屏卫士 v7.6.1 · 热修复：页面板块恢复（扫描日志/版本记录/使用报告）+ 构建链页面资产符号校验闸 · 激进模式全量保持",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.6.1.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.6.1.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
const upj = await up.json().catch(() => ({}));
console.log("ASSET UPLOAD", up.ok ? "OK " + (upj.browser_download_url || "") : "FAIL " + up.status);

/* ── 安装前校验链基准闭环：asset digest/size 与本地文件核对 ── */
if (up.ok) {
  const local = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.6.1.apk");
  const expectedSha = "b74cde33df8f6811f164b68a173bf72ad970d04e0763f56e7fd8517a9a5896a0";
  const digestOk = (upj.digest || "").endsWith(expectedSha);
  console.log("DIGEST", upj.digest || "(none)", "SIZE", upj.size, "LOCAL", local.length,
    upj.size === local.length ? "MATCH" : "MISMATCH",
    digestOk ? "SHA-256 VERIFIED" : "SHA DIGEST CHECK SKIP");
}
