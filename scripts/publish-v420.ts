/** 发布 v4.2.0 Release（用户操作让路 · 微信主界面零扫描 · 订阅全门控 · 12h 自动日志 · 功耗整治） */
import { getGithubToken } from "../src/lib/credentials";
import { GITHUB_API, GITHUB_REPO } from "../src/lib/version";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const H = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const TAG = "v4.2.0";
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v4.2.0.apk";
const APK_NAME = "adshield-v4.2.0.apk";

const notes = `# 开屏卫士 v4.2.0 · 用户操作让路 · 微信乱划根治 · 订阅全门控 · 12h 自动日志

## 本版主题：引擎绝不与你的意图对抗

依据 [issue#31-37](https://github.com/xiaobaiba999/Skip-Ad/issues/31) v4.1.0 引擎日志（\`GKD 订阅规则命中（mm）\` 在微信内反复出现 + \`广告窗直断→返回键复验未关→继续点击梯子\`）逐条根因修复。

## 修复

### 1. 你点开的页面，绝不自动关（issue#31-37 用户反馈：弹窗广告点开后被自动关闭，手动重开又被关）
- 根因：用户点击弹窗广告内容打开落地页后，引擎把新窗口当成「广告未关」，继续换候选点击/按返回键，与用户意图对抗
- 修复（用户意图让路双门）：
  - **4 秒让路门**：检测到你的真实点击（引擎代点不计）后 4s 内，该应用一律不出手（点击/返回键全部让路）
  - **30 秒保护门**：你点击后 2.5s 内打开的新页面（广告落地页/商品详情页）标记为「你主动打开」，30s 内引擎零动作——手动重开也受保护

### 2. 微信左右乱划根治（issue#34-37 日志实锤）
- 根因：远程订阅（含「已停止维护」的默认订阅）的微信规则**没有校验 activityIds**（GKD 规则的 Activity 限定字段被整体忽略）——朋友圈/小程序规则在微信主界面（LauncherUI）也能命中，900ms 冷却连点 + 内容变化 50ms 补扫 = 机关枪循环（日志里 \`GKD 订阅规则命中（mm）\` 刷屏）
- 修复（三层门）：
  - **activityIds 强校验**：GKD 社区规则的 Activity 限定全量解析（全等/后缀匹配 + excludeActivityIds 排除），非目标 Activity 的规则在扫描前直接出局（零树遍历）
  - **skip 形态门**：无 Activity 限定的「任意界面」规则，目标节点的 text/desc/vid 必须是跳过/关闭形态——微信底部 Tab 等普通控件绝不代点
  - **微信主界面门**：LauncherUI 期间开屏扫描链整体让路（16 档定时扫描一个不排，聊天列表滚动的高频内容变化也不再触发整树扫描）
- 附带修复：GKD 规则名（\`gkd:微信/组-1\`）的尾号数字曾被误读成倒计时（日志\`倒计时门控（mm）：跳过键 1s 后才激活\`白等）——倒计时门控现在只对真实按钮文案生效

### 3. 订阅「导入了却零命中」根治
- 根因：订阅规则只接驳开屏路径，弹窗场景从不咨询订阅——而 GKD 社区订阅里大量规则正是关弹窗的（更新弹窗/青少年模式/评分弹窗）
- 修复：**弹窗路径同步接驳订阅规则**；优先级调整为**内置优先、订阅接力**（内置规则经本引擎全量真机验证，远程订阅在其后兜底——陈旧订阅不再抢跑）
- 远程订阅按 GKD 语义限流：\`actionMaximum\` 默认 1 次/窗 + \`actionInterval\` 默认 1000ms（社区规则按此语义编写，同窗同一规则绝不机关枪连点）；订阅页现在同时展示内置/远程命中数

## 新能力

### 4. 日志每 12 小时自动提交
- 除手动上传外，服务心跳顺带检查 12h 定时提交（成功才计时；服务暂停/息屏时自动顺延，绝不夜间耗电）
- 上传内容更具体：**16 条带时间戳引擎日志**（60→120 条容量翻倍）+ Top5 应用战绩 + 内置/远程订阅命中数 + 能耗画像

### 5. 功耗与负载整治
- **订阅单遍树扫描**：此前每条选择器独立遍历全树（20 条规则 = 20 次整树 × 400 节点），现在全部候选规则共享一次 DFS + 节点预算——微信等重规则应用 CPU 大降
- **内容变化补扫收紧**：开屏 4s 后内容变化零补扫（定点扫描链 5.2/7/9.5/12s 仍覆盖晚出键），聊天列表/信息流滚动不再触发整树扫描
- **前台门 IPC 重排**：活动窗口归属查询（单次 IPC）前置短路，小窗全量遍历仅兜底执行
- **界面去冗余**：首页「问题反馈」卡与「我的」页问题反馈区移除（日志通道保留并增强）

## 升级说明
- 同签名覆盖升级，学习数据、订阅与设置全保留
- 已导入的远程订阅自动继续生效（新增 Activity 校验后误命中归零，真实命中照常）

<details>
<summary>技术实现（开发者向）</summary>

- \`AdSkipService\`：\`userActAt\`/\`userOpenCls\`/\`userOpenUntil\` 用户意图追踪（TYPE_VIEW_CLICKED 记账 → execute() 统一让路门）；\`isChatMainUi\` 微信主界面零调度；\`nudgeScan\` 4s 后弹窗窗期外零补扫；\`maybeAutoUploadLog\` 12h 自动提交（KeepAliveReceiver 心跳顺带）；MAX_LOG 60→120
- \`Subscription\`：activityIds/excludeActivityIds/actionMaximum/actionInterval 全量解析；单遍 DFS（\`dfsMulti\` 共享预算）；无 Activity 限定规则 skip 形态门（\`looksSkipLike\`）
- \`SubscriptionManager\`：内置优先/订阅接力；\`RUNTIME\` 限流记账（key 含 Activity，窗口切换自然重置；512 条防膨胀）；远程订阅默认 1 次/窗 + 1000ms，内置不限次（同主引擎，全局 0.9s 冷却 + 同窗 6 次预算管制）
- \`AdDecisionCore\`：\`Ctx.activityId\`；\`isChatMainUi\`；\`looksSkipLike\`
- 决策核心 JVM **151/151** + 规则引擎 **60/60** + 订阅门控烟囱 **12/12** 全绿
</details>
`;

async function gh(url: string, init?: RequestInit) {
  return fetch(url, { ...init, headers: { ...H, ...(init?.headers as Record<string, string>) } });
}

async function main() {
  // 1. 创建 Release
  let releaseId = 0;
  let releaseUrl = "";
  const create = await gh(`${GITHUB_API}/releases`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: TAG,
      name: "开屏卫士 v4.2.0 · 用户操作让路 · 微信乱划根治 · 订阅全门控 · 12h 自动日志",
      body: notes,
      draft: false,
      prerelease: false,
      make_latest: "true",
    }),
  });
  const cd = (await create.json()) as { id?: number; html_url?: string; message?: string };
  if (create.ok) {
    releaseId = cd.id!;
    releaseUrl = cd.html_url!;
    console.log(`✓ Release 已创建：${releaseUrl}`);
  } else {
    console.log(`创建返回 ${create.status}：${cd.message}（尝试复用已有 Release）`);
    const get = await gh(`${GITHUB_API}/releases/tags/${TAG}`);
    if (!get.ok) throw new Error(`无法创建/获取 Release（${get.status}）`);
    const gd = (await get.json()) as { id: number; html_url: string };
    releaseId = gd.id;
    releaseUrl = gd.html_url;
    console.log(`✓ 复用已有 Release：${releaseUrl}`);
  }

  // 2. 清理旧资产并上传 APK
  const listAssets = await gh(`${GITHUB_API}/releases/${releaseId}/assets`);
  if (listAssets.ok) {
    const assets = (await listAssets.json()) as { name: string; id: number }[];
    for (const a of assets.filter((x) => x.name.endsWith(".apk"))) {
      await gh(`${GITHUB_API}/releases/assets/${a.id}`, { method: "DELETE" });
      console.log(`✓ 清理旧资产：${a.name}`);
    }
  }
  const bytes = readFileSync(APK);
  const up = await gh(`https://uploads.github.com/repos/${GITHUB_REPO}/releases/${releaseId}/assets?name=${APK_NAME}`, {
    method: "POST",
    headers: { "Content-Type": "application/vnd.android.package-archive" },
    body: new Uint8Array(bytes),
  });
  if (!up.ok) throw new Error(`APK 上传失败（${up.status}）：${await up.text()}`);
  const ud = (await up.json()) as { browser_download_url: string; size: number };
  console.log(`✓ APK 已上传：${ud.browser_download_url}（${(ud.size / 1024).toFixed(0)} KB）`);

  // 3. 终验 latest 与资产 MD5
  const latest = await gh(`${GITHUB_API}/releases/latest`);
  const ld = (await latest.json()) as { tag_name: string; assets?: { name: string; size: number }[] };
  const md5 = createHash("md5").update(bytes).digest("hex");
  const assetOk = ld.assets?.some((a) => a.name === APK_NAME && a.size === bytes.length) ?? false;
  console.log(`✓ latest = ${ld.tag_name} · 资产校验 ${assetOk ? "通过" : "失败"} · MD5 ${md5}`);
  if (ld.tag_name !== TAG || !assetOk) throw new Error("终验失败");
  console.log("发布完成。");
}

main().catch((e) => {
  console.error("发布失败：", e);
  process.exit(1);
});
