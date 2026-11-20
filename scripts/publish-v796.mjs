import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.9.6：L3 真实世界验证轮
 * 用户指令：完成过后同步 GitHub 和推送 Releases + 优化 GitHub 面板描述。
 * 本轮主题：Bug#001 终局三漏根治（冷却闸绕过→无限重试循环阻断 + MISS 终局
 * 记账）· 一个真实 Bug 一个回归测试。面板描述保持五段式（一句定位 + 本轮
 * 主题 + 测试口径 + 真机诚实口径 + 常青能力）——信息密度优先，真机诚实口径
 * （REAL_DEVICE_TEST=NOT_AVAILABLE）入文案。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.9.6 L3 真实世界验证轮：代码审读实锤 Bug#001 终局三漏根治——预算耗尽于复验时刻不落 endedAt（冷却闸被绕过→无限重试循环）+ MISS 整类漏记，纯函数统一裁决 + 52 断言回归 · 31 套件 6653 断言全绿 · 零识别/安全门改动（安全>正确关闭>命中率）· L3 批次 1 保持 PENDING_REAL_DEVICE（REAL_DEVICE_TEST=NOT_AVAILABLE 诚实标记）· 免 Root · APK 848KB · 更新链 SHA-256 校验 · 396 机型十维矩阵（使用前请阅 README 免责声明）";
console.log("DESC length =", [...desc].length);
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);
if (!dr.ok) { const e = await dr.text(); console.log(e.slice(0, 200)); }

/* ── Release notes 正文（v7.9.6：L3 真实世界验证轮 · Bug#001 终局三漏根治）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.9.6：L3 真实世界验证轮——用户指令主线「真实 App → 真实广告 → 候选 → Decision Evidence → SafetyGuard → HumanTap → VERIFY → 真实结果 → 失败样本 → 缺陷修复 → 回归测试」。本轮不以堆功能为目标：按第一原则（无真实失败样本不主动修改生产识别算法/规则/词表/点击区域/SafetyGuard/轮询），唯一生产修改 = 代码审读实锤的明确生产 Bug。Bug#001（v7.9.5 六态闭环接线引入的潜伏终局竞态）：AdSkipService.taskOnScanOutcome 非零候选路径在「预算耗尽于复验时刻」（最后一击派发后复验仍见候选且 tries ≥ maxClicks）直接置 S_FAILED，三漏——① 不落 endedAt：taskAllowsNew(now, 0) 因「无终态记录恒放行」直接放行 → 2.2s 冷却闸被绕过 → 立即开新任务且 resetTaskForNewInstance 清空 clickTries/gaveUp → 同一存活广告借「新任务重置预算」无限重试循环（「绝不无限重试/预算梯子」红线失守——DUPLICATE_CLICK 同族风险面；execute 预算闸因预算被重置永不触发，gaveUp MISS 记账路对整流程失效；该路径是预算耗尽常见终点非长尾边角）；② 不记 MISS：最后一击复验失败被膨胀记成 RETRY——六态把「任务终局 MISS」整类漏记（真实漏检率证据链失真）；③ 终局无日志。发现路径：对 v7.9.5 新增六态闭环接线做逐路径审计——三个终态转移源（markTaskSuccess/markTaskFailed/markTaskWindowChanged）终局三件套齐全，唯 taskOnScanOutcome 非零候选路径三漏。修复（终局裁决收进纯函数 · 服务侧薄接线 · 零识别/安全门改动）：新 AdTaskCore.onVerifyStillStep（纯 Java · 与 AdTaskCore 同规约）——预算耗尽 → FAILED + endedAt=now（冷却闸在岗：新任务须过 2.2s 闸才重置预算——无限重试循环结构性阻断）+ MISS 终局一次记账（本击复验不再计 RETRY——严格区分「点击派发成功」与「任务终局」）；预算内首击复验仍记 RETRY（既有行为零变化）；FEEDBACK 复验在场原态保持（面板流程在先，零行为变化）；终态不可逆不重复记账（旧任务绝不复活）；终局补「复验预算用尽」日志行。一个真实 Bug 一个回归测试：Bug001RegressionTest（52 断言 · 八段）——S1 预算耗尽终局三件套（FAILED+endedAt=now+MISS）· S2 全生命周期（三击梯子 RETRY=2·MISS=1·FAILED 后点击/派发/回调全拒——第二次点击 = 0）· S3 冷却闸闭环（终局后 2.2s 内新任务全拒、+2.2s 放行；endedAt≠0 结构性不变式防回退）· S4 门语义取证（taskAllowsNew(now,0)=true——正是 Bug#001 绕过机制，钉死「终局必须落账」）· S5 终态不重复记账 · S6 FEEDBACK 语义零变化 · S7 RETRY 仅 VERIFYING 首击复验计一次（防同一击多扫膨胀）+ RETRY_WAIT+预算耗尽防御路径同根治 · S8 六态计数端到端（FALSE_CLICK 自动化恒 0 不被污染）。安全状态检查（用户指令二）：旧 GitHub PAT 实测未撤销（GET /user HTTP 200 → SECURITY ACTION REQUIRED——撤销需账号所有者操作；本轮只作仓库外未入库凭据文件使用，未创建任何新硬编码 Token）；公开 keystore 风险确认（知情接受现状 · docs/SECURITY.md R2 已载 v8.0 迁移计划 · 不贸然换钥）；secret-scan 双闸全绿。L3 批次 1 诚实口径：真机复查仍无设备（adb/emulator/KVM 均不可用）→ REAL_DEVICE_TEST = NOT_AVAILABLE——批次保持 PENDING_REAL_DEVICE（50 款 14 项记录字段全部 null 绝不预填，分阶段推进诚实记录 NOT_STARTED_FOR_NO_DEVICE）；GKD 八格/视频开屏真实时间线/极简窗口/ScrollView-Input 真实数据全部 NOT_AVAILABLE；20s ceiling 与三档自适应轮询零改动（没有真实数据之前不修改参数）；失败样本库复查仍空库（0 条——Bug#001 为代码审读实锤非真机样本，不入真机样本库绝不伪造字段）。本轮零识别改动：不新增关键词、不扩大点击区域、不降 SafetyGuard、不加轮询（安全 > 正确关闭 > 命中率 > 性能微优化）。31 套件 6653 项 JVM 断言全绿（+52 = Bug001RegressionTest；既有 6601 项零回退逐套件核对；修改前后各跑一轮）。versionCode 79600 > 79500——覆盖安装即升级（无障碍授权/学习数据/白名单/已导入订阅全部保留）。

1. Bug#001 实锤：预算耗尽终局不落 endedAt——冷却闸绕过→无限重试循环
2. 终局三漏之二：MISS 整类漏记（最后一击复验失败被膨胀记成 RETRY）
3. 修复：onVerifyStillStep 纯函数统一裁决（FAILED+endedAt+MISS 三件套）
4. 一个真实 Bug 一个回归测试：Bug001RegressionTest（52 断言 · 八段）
5. 31 套件 6653 断言全绿 · L3 批次 1 保持 PENDING（无真机绝不虚报）

> 升级方式：[adshield-v7.9.6.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.6/adshield-v7.9.6.apk) 同签名直接覆盖安装（versionCode 79600——终局记账与冷却闸修复即刻生效；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

> 诚实口径：REAL_DEVICE_TEST = NOT_AVAILABLE（本机无真机——adb/emulator/KVM 均不可用）——L3 批次 1 保持 PENDING_REAL_DEVICE；真机测试/真实广告观察/成功关闭/漏检/误触/重复点击当前全部 NOT_AVAILABLE（没有真数据就没有结论——0 为 NOT_AVAILABLE 计数而非「测了为零」）；失败样本库空库（0 条）。L3 真机数据必须回传日志后录入，绝不虚报。SECURITY ACTION REQUIRED：旧 GitHub PAT 未撤销——需账号所有者在 GitHub Settings 撤销。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`bea90869273080e42c7f42f5c57ed8d7ffd9c31f1c2e8519d96801ddc2d88529\` |
| MD5 | \`432f1668b37831edb9699f22a4c99cd6\` |
| 体积 | 865,846 B（848KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 79600 / 7.9.6 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.9.6",
    name: "开屏卫士 v7.9.6 · L3 真实世界验证轮：Bug#001 终局三漏根治（冷却闸绕过→无限重试循环阻断 + MISS 终局记账）",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/skip-ad-repo/android-app/dist/adshield-v7.9.6.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.9.6.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
console.log("ASSET UPLOAD", up.ok ? "OK" : "FAIL " + up.status);

/* digest 核对（App 安装前 SHA-256 校验链——发布侧基准闭环） */
const got = await fetch(`https://api.github.com/repos/${REPO}/releases/${rel.id}`, { headers });
const rel2 = await got.json();
for (const a of rel2.assets ?? []) {
  if (a.name === "adshield-v7.9.6.apk") {
    console.log("ASSET digest", a.digest, "size", a.size, "state", a.state);
    const local = await import("crypto").then(c => c.createHash("sha256").update(apk).digest("hex"));
    console.log("LOCAL sha256", local, "MATCH", a.digest === "sha256:" + local ? "YES" : "NO");
  }
}

/* 面板描述远端核对（≤350 字符硬限） */
const got2 = await fetch(`https://api.github.com/repos/${REPO}`, { headers });
const repo2 = await got2.json();
console.log("REMOTE desc length =", repo2.description ? [...repo2.description].length : "null",
  "| MATCH =", repo2.description === desc ? "YES" : "NO");
