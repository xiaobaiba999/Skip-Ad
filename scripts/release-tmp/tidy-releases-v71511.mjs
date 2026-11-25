// 一次性维护脚本（v7.15.11 后 GitHub 门面整理轮）：
//   ① 56 个 Release 标题统一为「开屏卫士 vX.Y.Z · 主题」；
//   ② v7.15.0~v7.15.10 顶部加「最新版直链」引导块（应用内更新失败的用户
//      落地 Releases 页即可见直链——adshield-latest.apk 别名永不过期）；
//   ③ v7.15.11 顶部加本版四通道直链块；
//   ④ 仓库元数据精简（description/topics——homepage 保持 Releases 落地页）。
// 幂等：标题不符才 PATCH；引导块标记已在场则不重复前置。
// 安全：不动 draft/prerelease；正文除前置块外逐字节保留；
//       update_manifest.py 对 releaseName 无格式校验（rel.get("name") or tag），
//       未来清单再生不受新标题影响；app 客户端不消费 release name（display-only）。
import { getGhToken } from "../gh-token.mjs";

const REPO = "xiaobaiba999/Skip-Ad";
const DRY = process.argv.includes("--dry");
const token = getGhToken();
const H = {
  Authorization: "token " + token,
  Accept: "application/vnd.github+json",
  "X-GitHub-Api-Version": "2022-11-28",
  "Content-Type": "application/json",
};
const API = "https://api.github.com/repos/" + REPO;

/* ── ① 标题表（tag → 新标题）── */
const TITLES = {
  "v7.15.11": "开屏卫士 v7.15.11 · 锁屏/切换app开屏根治与多点治理",
  "v7.15.10": "开屏卫士 v7.15.10 · 难跳三类型广告根治",
  "v7.15.9": "开屏卫士 v7.15.9 · 开屏命中死角根治",
  "v7.15.8": "开屏卫士 v7.15.8 · 扫描风暴收敛与日志强化",
  "v7.15.7": "开屏卫士 v7.15.7 · 动态图开屏二重根治",
  "v7.15.6": "开屏卫士 v7.15.6 · 动态图泛位置识别根治",
  "v7.15.5": "开屏卫士 v7.15.5 · 下载通道死局根治",
  "v7.15.4": "开屏卫士 v7.15.4 · 开屏提示12h窗与加速器指引",
  "v7.15.3": "开屏卫士 v7.15.3 · 检查更新多源链根治",
  "v7.15.2": "开屏卫士 v7.15.2 · 息屏/切回拦截根治",
  "v7.15.1": "开屏卫士 v7.15.1 · 息屏/解锁重入根治",
  "v7.15.0": "开屏卫士 v7.15.0 · 版本线重制（激进模式基线）",
  "v7.6.1": "开屏卫士 v7.6.1 · 热修复：页面板块恢复",
  "v7.6.0": "开屏卫士 v7.6.0 · 激进模式（开屏露头即点）",
  "v7.5.0": "开屏卫士 v7.5.0 · 插屏卫士与开屏倒计时解耦",
  "v7.4.0": "开屏卫士 v7.4.0 · 信息流广告三路主动关闭",
  "v7.3.0": "开屏卫士 v7.3.0 · 安全加固与算法优化",
  "v7.2.0": "开屏卫士 v7.2.0 · 朋友圈信息流广告命中根治",
  "v7.1.0": "开屏卫士 v7.1.0 · 订阅规则使用顺序治理",
  "v7.0.0": "开屏卫士 v7.0.0 · 基座重构与订阅导入永不失败",
  "v6.0.0": "开屏卫士 v6.0.0 · 1:1人手模拟引擎",
  "v5.9.0": "开屏卫士 v5.9.0 · 设备自适应触点",
  "v5.8.0": "开屏卫士 v5.8.0 · 屏蔽速度压榨",
  "v5.7.0": "开屏卫士 v5.7.0 · 扫描日志二轮激进",
  "v5.6.0": "开屏卫士 v5.6.0 · 命中激进化与订阅市场",
  "v5.5.0": "开屏卫士 v5.5.0 · 全维度优化（补建）",
  "v5.3.0": "开屏卫士 v5.3.0 · 三零治理（补建）",
  "v5.2.0": "开屏卫士 v5.2.0 · 10秒警告根治",
  "v5.1.1": "开屏卫士 v5.1.1 · 支付宝小程序容器保护",
  "v5.1.0": "开屏卫士 v5.1.0 · 非主流App开屏攻坚",
  "v5.0.0": "开屏卫士 v5.0.0 · 小程序保护",
  "v4.5.0": "开屏卫士 v4.5.0 · 乱点根治与系统开关解耦",
  "v4.4.0": "开屏卫士 v4.4.0 · 露头就秒大提速",
  "v4.3.0": "开屏卫士 v4.3.0 · 订阅主力规则接入",
  "v4.2.0": "开屏卫士 v4.2.0 · 用户操作让路与订阅全门控",
  "v4.1.0": "开屏卫士 v4.1.0 · 一键暂停与相机误触根治",
  "v4.0.0": "开屏卫士 v4.0.0 · GKD订阅生态接入",
  "v3.14.0": "开屏卫士 v3.14.0 · 反馈弹窗自动收起",
  "v3.13.0": "开屏卫士 v3.13.0 · 验证码横幅四重防护",
  "v3.12.0": "开屏卫士 v3.12.0 · 纯跳过引擎",
  "v3.11.0": "开屏卫士 v3.11.0 · 用户意图安全门",
  "v3.10.0": "开屏卫士 v3.10.0 · 内置规则库大扩充",
  "v3.8.1": "开屏卫士 v3.8.1 · 发布加固",
  "v3.8.0": "开屏卫士 v3.8.0 · 手势触点与倒计时门控",
  "v3.7.0": "开屏卫士 v3.7.0 · 手势修复与多候选轮换",
  "v3.6.0": "开屏卫士 v3.6.0 · 根窗口安全门",
  "v3.5.0": "开屏卫士 v3.5.0 · 点击复验与位置学习",
  "v3.4.0": "开屏卫士 v3.4.0 · 角落图片跳过键",
  "v3.3.0": "开屏卫士 v3.3.0 · SDK广告窗识别",
  "v3.1.0": "开屏卫士 v3.1.0 · 预拦截引擎",
  "v3.0.0": "开屏卫士 v3.0.0 · 千款兼容与页内直更",
  "v2.0.0": "开屏卫士 v2.0.0 · 秒跳引擎重构",
  "v1.3.0": "开屏卫士 v1.3.0 · 联网能力（反馈与检查更新）",
  "v1.2.0": "开屏卫士 v1.2.0 · 精准判断引擎",
  "v1.1.0": "开屏卫士 v1.1.0 · 策略情报站与学习导入导出",
  "v1.0.0": "开屏卫士 v1.0.0 · 初版（自动跳过+习惯学习）",
};

/* ── ②③ 引导块 ── */
const OLD_MARK = "🚀 **已有更新版本";
const CUR_MARK = "⬇️ **应用内更新失败";
const oldBlock =
  "> 🚀 **已有更新版本 [v7.15.11](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v7.15.11)**（锁屏/切换app开屏根治 + 多点治理 + 功耗优化）——应用内更新失败时，直接浏览器下载最新版后覆盖安装（同签名，无障碍授权/学习数据/订阅全保留）：\n" +
  "> - **CDN 直链（国内推荐 · 始终最新）**：<https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-latest.apk>\n" +
  "> - **testingcf 备用**：<https://testingcf.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-latest.apk>\n" +
  "> - **官方 Releases**：<https://github.com/xiaobaiba999/Skip-Ad/releases/latest>\n" +
  ">\n" +
  "> 本页下方资产为本版历史 APK（同签名可覆盖，但建议直接安装最新版）。\n\n";
const curBlock =
  "> ⬇️ **应用内更新失败？直接浏览器下载本版**（与页面资产同字节同 SHA-256，覆盖安装即升级）：\n" +
  "> - **CDN 直链（国内推荐）**：<https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.15.11.apk>（[testingcf 备用](https://testingcf.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.15.11.apk)）\n" +
  "> - **ghfast 镜像**：<https://ghfast.top/https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.15.11/adshield-v7.15.11.apk>\n" +
  "> - **gh-proxy 镜像**：<https://gh-proxy.com/https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.15.11/adshield-v7.15.11.apk>\n\n";

/* ── ④ 仓库元数据 ── */
const NEW_DESC =
  "安卓开屏广告「露头就跳」引擎——无障碍代点应用自带「跳过」键 · 开屏/插屏/信息流/视频开屏全场景 · 1:1 人手模拟 · 870KB 零依赖 · GKD 订阅生态（使用前请阅 README 免责声明）";
const NEW_TOPICS = [
  "android", "ad-blocker", "adblock", "ad-skip", "skip-ad", "splash-ad",
  "accessibility", "accessibility-service", "gkd", "gkd-subscription",
  "open-source", "android-app",
];

async function main() {
  /* 列全量 releases */
  let rels = [];
  for (let p = 1; p <= 5; p++) {
    const r = await fetch(API + "/releases?per_page=100&page=" + p, { headers: H });
    if (!r.ok) throw new Error("list releases HTTP " + r.status);
    const batch = await r.json();
    rels = rels.concat(batch);
    if (batch.length < 100) break;
  }
  console.log("releases:", rels.length, "· mode:", DRY ? "DRY-RUN" : "EXECUTE");

  let renamed = 0, blocked = 0, skipped = 0, missing = [];
  for (const rel of rels) {
    if (rel.draft || rel.prerelease) { skipped++; continue; }
    const tag = rel.tag_name;
    const title = TITLES[tag];
    if (!title) { missing.push(tag); continue; }

    let body = rel.body || "";
    let bodyChanged = false;
    if (tag === "v7.15.11") {
      if (!body.includes(CUR_MARK)) { body = curBlock + body; bodyChanged = true; }
    } else if (tag.startsWith("v7.15.")) {
      if (!body.includes(OLD_MARK)) { body = oldBlock + body; bodyChanged = true; }
    }

    if (rel.name === title && !bodyChanged) { skipped++; continue; }

    console.log(`· ${tag}: "${rel.name}" → "${title}"${bodyChanged ? " +引导块" : ""}`);
    if (DRY) { renamed++; continue; }

    const r = await fetch(API + "/releases/" + rel.id, {
      method: "PATCH",
      headers: H,
      body: JSON.stringify({ name: title, body }),
    });
    if (!r.ok) {
      const t = await r.text();
      throw new Error(`PATCH ${tag} HTTP ${r.status}: ${t.slice(0, 200)}`);
    }
    renamed++;
    await new Promise((res) => setTimeout(res, 350)); // 轻限速
  }
  console.log(`titles+blocks: ${renamed} · untouched: ${skipped} · no-title-map: ${missing.join(",") || "无"}`);

  /* 仓库元数据 */
  const cur = await fetch(API, { headers: H }).then((r) => r.json());
  console.log("\n当前 description:", JSON.stringify(cur.description));
  if (DRY) {
    console.log("DRY: 将更新 description →", JSON.stringify(NEW_DESC));
    console.log("DRY: 将更新 topics →", JSON.stringify(NEW_TOPICS));
    return;
  }
  if (cur.description !== NEW_DESC) {
    const r = await fetch(API, {
      method: "PATCH",
      headers: H,
      body: JSON.stringify({ description: NEW_DESC, homepage: "https://github.com/xiaobaiba999/Skip-Ad/releases" }),
    });
    console.log("PATCH description HTTP", r.status);
    if (!r.ok) throw new Error(await r.text());
  }
  const tr = await fetch(API + "/topics", {
    method: "PUT",
    headers: { ...H, Accept: "application/vnd.github.mercy-preview+json" },
    body: JSON.stringify({ names: NEW_TOPICS }),
  });
  console.log("PUT topics HTTP", tr.status, tr.ok ? "" : await tr.text());
  if (!tr.ok) throw new Error("topics failed");
  console.log("\n✅ 全部完成");
}

main().catch((e) => { console.error("FATAL:", e.message); process.exit(1); });
