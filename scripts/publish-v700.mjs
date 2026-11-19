import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.0.0 ── */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.0.0 回退 6.0 稳定基座重构（6.0 之后版本在部分设备不可用——已全部清除）· 订阅导入八通道镜像+离线快照兜底永不失败 · 检查更新当场显示 ≤5 条带序号新版功能 · 日志一日三次自动提交 · 9 品牌+子品牌 286 机型矩阵七维全绿 · 核心功能离线可用 · 免 Root · APK 808KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.0.0 规范发布体：1 标题 + 1 段落 + ≤5 条带序号功能）──
 * 与 App 内 core.ReleaseNotes 解析器同源：序号行优先解析（App「检查更新」
 * 当场显示这 5 条带序号功能标题）；# 章节名不入列；每条 ≤44 字。 */
const body = `开屏卫士 v7.0.0：回到 6.0 已验证基座重新出发——用户实锤 6.0 之后的版本在其设备全部不可用（进入后无法点击/版本号「？」/永久加载），已按指令回退到 6.0 基座重构并把 v6.1-v6.5 的全部 Release 与 Tag 从 GitHub 清除；6.0 基座 + v6.1 实测验证过的特性（订阅镜像自愈/误触三防线/8h 日志/版本描述格式）保持。versionCode 70000 > 60500——任何历史版本直接覆盖安装即回稳定基座，无需卸载。

1. 订阅导入根治：八通道镜像自动切换+离线快照兜底，导入永不失败
2. 检查更新当场显示新版本≤5条带序号功能；顶部更新横幅移除
3. 日志留存翻倍至400条+设备画像，每日8小时自动提交3次
4. 多点误点三重防线+界面初始化保险丝，绝不永久加载白屏
5. 9品牌+子品牌286机型七维全绿；免打扰持久模式取消

> 升级方式：[adshield-v7.0.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.0.0/adshield-v7.0.0.apk) 同签名直接覆盖安装（versionCode 70000——v6.0~v6.5 任何版本覆盖即回稳定基座；无障碍授权/学习数据/白名单/已导入订阅全部保留）。旧版 App 内「我的 → 检查更新」也会发现本版并页内直下——检查更新当场显示上方 5 条带序号功能描述。

| 校验 | 值 |
| --- | --- |
| MD5 | \`db3ae440861280d9000ab2a18fad4eda\` |
| 体积 | 824,886 B（808KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 70000 / 7.0.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.0.0",
    name: "开屏卫士 v7.0.0 · 回退 6.0 基座重构（覆盖安装即回稳定）· 订阅导入永不失败 · 版本描述规范 · 3451 断言",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/skip-ad/android-app/dist/adshield-v7.0.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.0.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
console.log("APK UPLOAD", up.ok ? "OK " + apk.length + "B" : "FAIL " + up.status);
if (!up.ok) process.exit(1);
