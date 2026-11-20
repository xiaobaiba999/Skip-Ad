import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.9.0：源码审计轮主题
 * 用户指令（第十九轮）：完整版本迭代——源码审计 → 找根因 → 修改 → 编译 →
 * 测试 → 性能检查 → Git 提交 → 推送 GitHub → 生成新版本说明。本轮主题：
 * 三处「纯函数单测全绿、服务侧零接线」盲区根治（闸⓪ SDK 插屏窗指纹死线/
 * SDK 窗 streak 冻结/外来包 Dialog 插屏未拉起）+ 图标关闭钮路径（用户实锤
 * 「关闭按钮只有图标没有文字」）+ 视频画像竞速根治（arm-hint 守链）。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.9.0 源码审计轮：三处接线盲区根治（闸⓪ SDK 插屏窗指纹从未接通/SDK 窗 streak 冻结/外来包 Dialog 插屏未拉起——纯函数全绿服务侧死线全接通）+ 图标关闭钮路径（无文字钮：SDK 插屏窗/WebView+角标强语境闸 + 顶带优先 + 面积 ≤2% 屏 + 中部全拒）+ 视频画像竞速根治（arm-hint 守链 + 探针词 5→10 + 每 3 针全量复扫——12~20s 段图片钮唯一活路）· 23 套件 6114 断言全绿 · 激进模式/视频开屏卫士/插屏重扫路由全部保持 · 更新链 SHA-256 校验 · 396 机型十维矩阵 · 免 Root · APK 836KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.9.0：接线盲区根治 + 图标关闭钮路径 + 视频画像竞速根治）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.9.0：源码审计轮——完整审计 AdSkipService/InterstitialGuard/VideoSplashGuard/AdDecisionCore 全链路（事件路由→窗口收集→Node Tree 遍历→候选→规则匹配→评分→安全门→点击→复验），实锤 v7.8.0 的三处「纯函数单测全绿、服务侧零接线」盲区（纯函数层被 6063 项单测保护得很好，服务侧装配层无断言覆盖——三处死线全是装配缺陷）。①闸⓪第三路「SDK 插屏窗指纹」从未被接通：maybeInterstitialScan 从未给 wctx.sdkAdWin 赋值（Ctx 默认 false）——decideFromCandidates(..., wctx.sdkAdWin) 恒传 false，无语境词无 WebView 的原生 SDK 插屏三路语境全空永不出手（纯函数单测直传 true 全绿，服务侧恒 false——测试盲区实锤）→ wctx.sdkAdWin = isAdActivityClass(窗类) 接通。②SDK 窗零候选 streak 冻结在 0：零候选退避块的豁免窗（广告窗/Dialog/学习窗）跳过整个计数块——videoLike 的 sdkAdWin 路径（streak≥3）在 SDK 广告窗上永远建立不起来（SDK 视频开屏观察窗不延长、轮询针不排）→ streak 无条件计数（豁免只挡杀链不挡计数——计数是画像观测源）。③外来包 Dialog 插屏卫士从未被拉起：非前台 Dialog 分支只走 nudgePopupScan（弹窗识别路径），广告 SDK 独立包的 Dialog 插窗（com.qq.e.ads 家族）整窗漏拦 → 同分支接线 maybeInterstitialScan + winClass 登记（此分支提前 return 从未登记窗类——SDK 指纹判定拿到空串恒 false 的连带缺陷一并补齐）。图标关闭钮路径（用户实锤「关闭按钮是图片/只有图标没有文字」）：文字候选对纯图标钮（零文本零 desc）零候选——decideIconFromCandidates 新路径：语境闸（SDK 插屏窗指纹 / WebView+广告角标——比文字路径更严，仅语境词在场的普通窗角落图标绝不点）+ 形态闸（closeFormOk 三路并集 + 面积 ≤2% 屏图标级）+ 位置闸（顶带优先/底部带次选/中部 tabBar 高发区绝不收）+ label=× 走既有终闸零改动（interBoundaryCheck 对 × 本就放行——语义/形态/场景三重断言全复用）。视频画像竞速根治（树慢速构建窗）：WebView/H5 视频容器树慢速构建（内容事件持续到 ~2s）时零候选退避杀链（streak≥3+窗龄≥2.6s）早于静默≥1.6s 的全画像成立——arm-hint（streak≥3+窗龄≥2.2s+静默≥0.8s）窗免杀链，链连续活到全画像接管（旧版链死等固定档自愈最长 2s 空档；静默持续 <0.8s 的窗照杀——功耗红线）。视频轮询针探针词 5→10（+关闭/close/×/✕/✖——图内纯图标钮与拉丁关闭词旧探针探不到；裸 X/x 不收防 contains 噪声面）+ 每 3 针一次全量复扫（12~20s 段纯图片钮零文字零探针命中——walk 角落候选是唯一路径；20s 窗内 ≤5 次——功耗契约）。性能契约全保持：图标钮零新增扫描（既有 1.5s 节流内纯函数判定）/arm-hint 零新增档位/全量复扫只在 videoLike 窗/事件订阅零变化。23 套件 6114 项 JVM 断言全绿（+51：VideoSplashTest S9 画像竞速 + S10 探针词/全量复扫、InterstitialGuardTest S9 图标钮真值表、InterstitialRoutingTest S10 接线契约——SDK 指纹全族命中 + MainActivity/SplashActivity 红线不破）。versionCode 79000 > 78000——覆盖安装即升级（无障碍授权/学习数据/白名单/已导入订阅全部保留）。

1. 三处接线盲区根治：闸⓪ SDK 插屏窗指纹接通 + SDK 窗 streak 无条件计数 + 外来包 Dialog 插屏接线
2. 图标关闭钮路径：SDK 窗/WebView+角标强语境闸 + 顶带优先 + 面积 ≤2% 屏 + 中部全拒
3. 视频画像竞速根治：arm-hint 免杀链（树慢速构建窗——链连续活到全画像接管）
4. 视频轮询针：探针词 5→10（×/关闭/close 收编）+ 每 3 针全量复扫（图片钮唯一活路）
5. 性能契约全保持（零新增扫描/零新增档位）+ 23 套件 6114 断言全绿（+51）

> 升级方式：[adshield-v7.9.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.0/adshield-v7.9.0.apk) 同签名直接覆盖安装（versionCode 79000——三处接线盲区根治 + 图标关闭钮路径即刻生效；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`c8053c27e37bf1205c84a3ca0e0584452c55606c536eb0f5d24e9a6fda50a5d2\` |
| MD5 | \`b4b7f9e8f5f5200ec269f46e14bc917a\` |
| 体积 | 853,558 B（836KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 79000 / 7.9.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.9.0",
    name: "开屏卫士 v7.9.0 · 源码审计轮：三处接线盲区根治 + 图标关闭钮路径 + 视频画像竞速根治",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/skip-ad-repo/android-app/dist/adshield-v7.9.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.9.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
console.log("ASSET UPLOAD", up.ok ? "OK" : "FAIL " + up.status);

/* digest 核对（App 安装前 SHA-256 校验链——发布侧基准闭环） */
const got = await fetch(`https://api.github.com/repos/${REPO}/releases/${rel.id}`, { headers });
const rel2 = await got.json();
for (const a of rel2.assets ?? []) {
  if (a.name === "adshield-v7.9.0.apk") {
    console.log("ASSET digest", a.digest, "size", a.size, "state", a.state);
    const local = await import("crypto").then(c => c.createHash("sha256").update(apk).digest("hex"));
    console.log("LOCAL sha256", local, "MATCH", a.digest === "sha256:" + local ? "YES" : "NO");
  }
}
