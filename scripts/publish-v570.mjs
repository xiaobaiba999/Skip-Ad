import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
const body = `## 开屏卫士 v5.7.0 · 扫描日志二轮激进 + 8×12 机型矩阵 + GitHub 面板截图

> 用户二轮指令全量落地：**扫描日志继续优化（好多广告没有命中——可以再大胆激进一点）· 模拟 N 多品牌 × N 多机型测试修复 · 完成后同步 GitHub 与推送 Releases · 优化 GitHub 面板描述（含 App 截图）**。

### ① 命中再激进化（SDK 指纹 +12 档 · 国际 SDK 漏网主力）

- 新指纹：Unity Ads \`AdUnitActivity\` · IronSource · Chartboost \`CBImpressionActivity\` · Meta \`AudienceNetworkActivity\` · InMobi · Tapjoy · 激励视频 \`RewardVideo\`/\`RewardAd\` · 穿山甲模板 \`ExpressAd\` · \`AdPresent\` · 全屏视频 \`FullScreenAd\`/\`FullVideo\` —— 整窗无指纹 = 零点击零返回兜底的漏网主力，累计指纹 40+ 段
- 时序：倒计时缓冲 300→**180ms**（将尽提前开火）· 同窗点击预算 8→**10** · 跳过候选 4→**6** · 点击冷却 500→**420ms** · 弹窗冷却 2000→**1600ms**
- 红线不碰：App 自建 \`SplashActivity\` 与 Unity **游戏引擎**主窗 (\`UnityPlayerActivity\`) 双免疫断言；\`DownloadActivity\` 子串陷阱 (\`loadactivity\` 含 \`adactivity\`) 精确段匹配绝不误认

### ② 文案 / vid 双通道激进扩充

- 全局精确文案 +5：\`跳过视频\` / \`跳过片头\` / \`跳過視頻\` / \`skip ads\`（视频类 App 开屏整词标签）
- 分应用表 +12 款：得物 / 什么值得买 / 唯品会 / 苏宁 / 途牛 / 马蜂窝 / 墨迹天气 / 58 同城 / 安居客 / 贝壳找房 / 汽车之家 / 虎扑
- 新 **ADCLOSE 复合 vid 族**：\`adclose\` / \`ad_close\` / \`adcancel\`（第一方自绘广告关闭键命名约定，边缘带 + 小面积门控）；\`jump\` 非跳过语义依旧被安全门拦截（游戏保护红线）
- 内置规则库 v7→**v8**：114→**124 款**（与主引擎分应用表同源对齐）

### ③ 8 品牌 × 12 机型 = 96 机型测试矩阵（六维全 96/96）

小米/华为/OPPO/一加/vivo/荣耀/三星/其他（Pixel·Nothing·魅族·努比亚·索尼·moto）× 各 12 款，覆盖面较 v5.6.0 的 5×10=50 **+92%**：

| 维度 | 结果 |
| --- | --- |
| 开屏广告命中 | 96/96（六信号：文本/desc/vid 订阅/图片角落键/新文案「跳过片头」/新复合 vid 键——全命中） |
| 屏蔽速度 | 96/96（120 节点深树 walk+decide < 20ms · 激进参数在场断言） |
| bug 防线 | 96/96（菜单陷阱/巨横幅/overlay/**游戏 jump 键**四陷阱零动作） |
| 功耗 | 96/96（典型日 ≈ 0.049mWh < 0.15 红线 · 分级预算在场） |
| 精准度 | 96/96（银行守护零返回键 · 验证码标记 · **Unity 游戏引擎窗免疫**） |
| 算法优化 | 96/96（新指纹 12 档全命中 · 非广告窗 7 档全免疫 · **8 品牌族归一每机型全验**） |

**全仓 1330 项 JVM 断言全绿（+282）**，白盒核验 dex 新指纹/新文案/ADCLOSE 全在场。测试报告：[\`docs/test-reports/round-23.json\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/test-reports/round-23.json)，二十三轮轨迹见 [\`summary.md\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/test-reports/summary.md)。

### ④ GitHub 面板描述优化（真实 App 截图）

README 重构：徽章行（Releases / License / APK 297KB / 1330 断言 / 8×12 矩阵 / gkd-subscription）+ 一屏速览表 + **App 真实截图三连**（首页守护 / 订阅市场 / 引擎日志——由 App 实际 WebView UI 渲染截取，非设计稿）：[\`docs/screenshots/\`](https://github.com/xiaobaiba999/Skip-Ad/tree/main/docs/screenshots)。

### 升级方式

**[adshield-v5.7.0.apk 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.7.0/adshield-v5.7.0.apk)**（versionCode 50700 > 50600，同签名直接覆盖安装；无障碍授权/学习数据/白名单全部保留）。旧版 App 内「我的 → 检查更新」也会自动发现本版本并页内直下。

| 校验 | 值 |
| --- | --- |
| MD5 | \`3daa9cde0e99f40607c746fea9614ed0\` |
| 体积 | 304,478 B（297KB） |
| versionCode / versionName | 50700 / 5.7.0 |
| minSdk / targetSdk | 24 / 34 |`;
const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ tag_name: "v5.7.0", name: "开屏卫士 v5.7.0 · 扫描日志二轮激进 · 8×12 机型矩阵 · GitHub 面板截图 · 1330 断言", body }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);
const apk = readFileSync("/home/z/skip-ad/android-app/dist/adshield-v5.7.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v5.7.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(apk.length) },
  body: new Uint8Array(apk),
});
const asset = await up.json();
console.log(up.ok ? "ASSET OK " + asset.browser_download_url + " (id " + asset.id + ")" : "ASSET FAIL " + (asset.message ?? up.status));
