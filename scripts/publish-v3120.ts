/** v3.12.0 发布：创建 Tag+Release（含 APK 附件），回复 issue#14 与 #13 */
import { getGithubToken } from "../src/lib/credentials";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const REPO = "xiaobaiba999/Skip-Ad";
const API = `https://api.github.com/repos/${REPO}`;
const UPLOADS = `https://uploads.github.com/repos/${REPO}`;
const SHA = readFileSync("/tmp/v3120-sha.txt", "utf8").trim();
const APK_PATH = "/home/z/skip-ad-repo/android-app/dist/adshield-v3.12.0.apk";
const H = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const notes = readFileSync("/tmp/v3120-notes.md", "utf8");

async function main() {
  // 1. Tag v3.12.0
  const tag = await fetch(`${API}/git/refs/tags/v3.12.0`, { headers: H });
  if (tag.status === 404) {
    const t = await fetch(`${API}/git/tags`, {
      method: "POST",
      headers: { ...H, "Content-Type": "application/json" },
      body: JSON.stringify({
        tag: "v3.12.0",
        message: "v3.12.0 纯跳过引擎：链式拦截整体移除（issue#14）",
        object: SHA,
        type: "commit",
      }),
    });
    console.log(t.ok ? "✓ Tag v3.12.0 已创建" : `✗ tag ${t.status}: ${(await t.json()).message}`);
  } else {
    console.log("✓ Tag v3.12.0 已存在");
  }

  // 2. Release（latest）
  const rel = await fetch(`${API}/releases`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: "v3.12.0",
      name: "Skip-Ad v3.12.0 · 纯跳过引擎 · 链式拦截整体移除（issue#14 根治）",
      body: notes,
      draft: false,
      prerelease: false,
      make_latest: "true",
    }),
  });
  const rd = (await rel.json()) as { id?: number; html_url?: string; message?: string };
  console.log(rel.ok ? `✓ Release 已发布：${rd.html_url}` : `✗ release ${rel.status}: ${rd.message}`);
  if (!rel.ok || !rd.id) process.exit(1);

  // 3. 上传 APK 附件
  const apk = readFileSync(APK_PATH);
  const up = await fetch(`${UPLOADS}/releases/${rd.id}/assets?name=adshield-v3.12.0.apk`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/vnd.android.package-archive" },
    body: new Uint8Array(apk),
  });
  console.log(up.ok ? `✓ APK 已上传（${(apk.length / 1024).toFixed(1)}KB）` : `✗ upload ${up.status}: ${(await up.json()).message}`);

  // 4. 回复 issue#14（本次日志提交）
  const c14 = [
    "## 已定位并彻底修复 ✅ v3.12.0（链式拦截整体移除）",
    "",
    "这份日志把最终根因拍死了——先说结论：**v3.11.0 的「三重放行安全门」方向错了，本次直接把链式拦截机制整个删掉**（v3.12.0 已发布）。",
    "",
    "### 日志实锤的三处现场",
    "",
    "```",
    "22:08:43 ⛓ 链式跳转已弹回：securitycenter 的广告试图拉起 mm → 返回键送回原应用（1883ms 内）",
    "22:08:44 ⛓ 链式跳转已弹回：securitycenter 的广告试图拉起 mm → 返回键送回原应用（898ms 内）",
    "22:08:44 ⛓ 链式跳转已弹回：securitycenter 的广告试图拉起 mm → 返回键送回原应用（734ms 内）",
    "```",
    "",
    "1. **1.5 秒内连按 3 次返回键**——这就是你 NFC 碰一碰时支付链被踢回的现场（securitycenter→支付组件被当成「广告拉起」，按返回键弹回）；「分享到微信」（→mm）同样中招。",
    "2. 你升级后新分享的信息被拦截：v3.11.0 的安全门要求「2.2s 内真实点击 / 选择器 8s 内出现 / 同对 12s 内重试」三选一，但 NFC 支付链、系统级跳转根本不走这些路径——**用户主动意图的场景无法穷举，任何安全门都有漏网时刻**。",
    "3. 日志还暴露了另一个问题：`connectivity` 连接检查窗在 2 分钟内被循环误点 ×10（「已点击跳过」×10）——本轮一并并入系统噪音过滤。",
    "",
    "### v3.12.0 的决定：不再修门，直接拆墙",
    "",
    "三轮事故复盘（#12 闪退 → #13 分享被拦 → 本次 NFC 支付被拦）证明：链式回弹是本项目早期自创的危险特性（同类头部应用并没有这个功能），**收益（拦少量真广告二跳）远小于代价（把用户踢出正在进行的操作）**。v3.12.0 整体移除：",
    "",
    "| 移除项 | 说明 |",
    "| --- | --- |",
    "| reboundChainJump（跨应用弹回） | 连按返回键踢用户的元凶，整体删除 |",
    "| reboundLanding（同包落地页弹回） | 点商品/文章详情跳内置页不再被误弹回 |",
    "| 三重放行安全门 / isChooserHint / isAdTarget 名单 | 随机制一并清除 |",
    "| 「跳转回弹」开关与统计 | 界面撤下（能力已不存在） |",
    "",
    "引擎职责回归纯跳过：**只点「跳过/关闭」类控件，绝不对抗你的任何跳转**。分享、NFC 支付、登录跳转、任何链路——引擎从此只做一件事：跳广告。",
    "",
    "### 验证",
    "",
    "- 决策核心 JVM 108 用例 + 规则引擎 60 用例全绿（链式 23 例随机制移除）",
    "- APK 产物 dex 字符串级验证：链式代码零残留；versionCode 31200",
    "",
    "### 升级",
    "",
    "应用内「关于 → 检查更新」直接升级，或下载：[adshield-v3.12.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v3.12.0)（同签名覆盖安装，学习数据保留）。",
    "",
    "升级后再试一次 NFC 支付和分享——这次引擎绝不会再按返回键。如果还有别的误拦截场景，欢迎再传日志，日志通道会继续驱动迭代。",
  ].join("\n");

  const r14 = await fetch(`${API}/issues/14/comments`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ body: c14 }),
  });
  console.log(r14.ok ? "✓ issue#14 已回复" : `✗ ${r14.status}: ${(await r14.json()).message}`);

  // 5. 补充回复 issue#13（诚实交代 v3.11.0 方案被替代）
  const c13 = [
    "## 追进：v3.11.0 的安全门方案已被 v3.12.0 整体移除替代",
    "",
    "后续日志（#14）显示：同样的事故又发生了——NFC 碰一碰拉起的支付链与主动分享仍被 1.5s 内连按 3 次返回键弹回。三重放行安全门覆盖不了系统级跳转（NFC 支付链不走「用户点击」路径），**用户主动意图无法穷举，任何安全门都有漏网时刻**。",
    "",
    "v3.12.0 的最终决定：不再修门，直接拆墙——链式拦截机制整体移除（含本 issue 修复的回弹与 v3.11.0 安全门本体），引擎职责回归纯跳过：只点「跳过/关闭」，绝不对抗你的任何跳转。详见 [v3.12.0 Release](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v3.12.0)。",
  ].join("\n");

  const r13 = await fetch(`${API}/issues/13/comments`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ body: c13 }),
  });
  console.log(r13.ok ? "✓ issue#13 已补充回复" : `✗ ${r13.status}: ${(await r13.json()).message}`);
}

main().catch((e) => {
  console.error("发布失败:", e);
  process.exit(1);
});
