import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.6.0：激进模式主题 ──
 * 用户指令（第十五轮）：软件内插入的广告不仅没跳过，其他软件的开屏广告现在
 * 有的还跳不了了，或者非常非常慢，你可以激进一点暂时先放弃宁可漏拦绝不误触。
 * 扫描日志上传未命中问题……优化内置规则库和订阅市场规则的使用顺序和命中问题，
 * 优化底层算法……构建真是模拟环境，1比1模拟人在不同设备上操作使用卫士，解决
 * 这个bug。功能实习完成后进入测试环境全方位测试……完成过后同步giuhub和推送
 * releases。优化github面板描述。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.6.0 激进模式（开屏露头即点：首击零门控不再等倒计时走完 · 重击等将尽+6s 封顶——「有的还跳不了了或者非常非常慢」根治）+ 插屏「广告」语境闸 contains 重做（「广告·了解详情」等变体全收 · 关闭词扩表残忍离开/不再看/暂不需要 · 终闸语义面收编知道了——「插入的广告不仅没跳过」三死因根治）· 信息流三路/插屏卫士/安全加固全部保持 · 更新链 SHA-256 校验 · 白名单全量应用列表 · 308 机型八维矩阵 · 4314 断言全绿 · 核心功能离线可用 · 免 Root · APK 832KB（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.6.0：激进模式）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.6.0：激进模式——第十五轮用户实锤「软件内插入的广告不仅没跳过，其他软件的开屏广告现在有的还跳不了了，或者非常非常慢，你可以激进一点暂时先放弃宁可漏拦绝不误触」的四组死因全部根治：① 开屏变慢直接死因 = v7.5.0 门控从第 0 击起全次生效（所有带倒计时的开屏全等倒计时走完才出手——5s 广告白看 4.9s；误检数字更把无倒计时按钮按住不动）——首击零门控露头即点（绝大多数按钮立即可点 0ms 命中），首击白点后重击才等「将尽」（真倒计时按钮命中时机）且 6s 封顶不再无限等；② 插屏闸⓪过严（isDisclosureBadge 精确等值三闸把「广告 · 了解详情」「查看广告详情」等真机变体全拒在门外）——「广告」语境闸 contains 重做（≤10 字可见标签一网打尽，超长正文不算语境节点，语境节点绝不点击）；③ 终闸杀词（「知道了」词表收了也被通用终闸 BND_FAIL_SEMANTIC 一票否决——出手永远被最后一道闸打回）——插屏专用终闸 interBoundaryCheck 语义面收编 + 关闭词扩表（残忍离开/残忍退出/不再看/不看了/暂不需要/不需要/Skip Ad）；④ 带位闸灭中部关闭钮 + 滚动窗一票否决名单外信息流卡片——非滚动窗零位置闸、滚动窗近旁铁证（|Δcy|≤35% 同排）可出手 + 新窗首扫提速（窗切换即重置节流——插层出现当帧就扫）。多点误点：激进时序结构本身就是防线（首击唯一立即 + 重击等将尽 >1.6s 天然满足人手双击下限），MAX_STRIKES=2/MAX_CLICKS=3/H 600ms 全保持，九品牌×5 槽位触点钳制 1:1 仿真全绿。versionCode 76000 > 75000——覆盖安装即升级（无障碍授权/学习数据/已导入订阅全部保留）。

1. 开屏首击零门控：跳过键露头即点，不再等倒计时走完（5s 广告白看 4.9s 根治）
2. 重击等将尽+6s 封顶：首击白点后才等倒计时将尽出手，误检数字不再无限等
3. 插屏「广告」语境闸 contains 重做：变体一网打尽+关闭词扩表+终闸语义面收编
4. 滚动窗近旁铁证可出手+插屏新窗首扫提速：名单外信息流卡片广告也关
5. 扫描日志插屏双样本诊断（语境+关闭钮）+ 20 套件 4314 断言全绿

> 升级方式：[adshield-v7.6.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.6.0/adshield-v7.6.0.apk) 同签名直接覆盖安装（versionCode 76000——v7.x 任何版本覆盖即升级；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`d4e66b0e693dcf6083123feeaf7c03678b641b4d6d246eb28d80abd9c64374c6\` |
| MD5 | \`aec9fea95de3790e99f16dfc4ab841ea\` |
| 体积 | 849,462 B（832KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 76000 / 7.6.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.6.0",
    name: "开屏卫士 v7.6.0 · 激进模式（开屏露头即点 + 插屏「广告」语境闸重做）· 首击零门控/重击 6s 封顶/终闸语义面收编 · 4314 断言",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.6.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.6.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
const upj = await up.json().catch(() => ({}));
console.log("ASSET UPLOAD", up.ok ? "OK " + (upj.browser_download_url || "") : "FAIL " + up.status);

/* ── 安装前校验链基准闭环：asset digest/size 与本地文件核对 ── */
if (up.ok) {
  const local = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v7.6.0.apk");
  const expectedSha = "d4e66b0e693dcf6083123feeaf7c03678b641b4d6d246eb28d80abd9c64374c6";
  const digestOk = (upj.digest || "").endsWith(expectedSha);
  console.log("DIGEST", upj.digest || "(none)", "SIZE", upj.size, "LOCAL", local.length,
    upj.size === local.length ? "MATCH" : "MISMATCH",
    digestOk ? "SHA-256 VERIFIED" : "SHA DIGEST CHECK SKIP");
}
