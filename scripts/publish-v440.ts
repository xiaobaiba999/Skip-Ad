/** 发布 v4.4.0 Release（露头就秒大提速 · 恢复防护零触摸 · H5 晚到按钮救回 · 功耗再降） */
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

const TAG = "v4.4.0";
const APK = "/home/z/skip-ad-repo/dist/adshield-v4.4.0.apk";
const APK_NAME = "adshield-v4.4.0.apk";

const notes = `# 开屏卫士 v4.4.0 · 露头就秒大提速 · 恢复防护零触摸 · 功耗再降

依据 [issue#39](https://github.com/xiaobaiga999/Skip-Ad/issues/39) / [#40](https://github.com/xiaobaiba999/Skip-Ad/issues/40) / [#41](https://github.com/xiaobaiga999/Skip-Ad/issues/41) v4.3.0 引擎日志（\`门控 4086ms 后出手 → 实际 5.5s 后才点到\` + \`事件 4602/开机\` 能耗画像 + H5 开屏按钮晚到实锤）逐条根因修复。**本轮主题：比人眼快，露头就秒。**

## 修复与提速

### 1. 倒计时门控「开闸瞬间即点」（喜马拉雅/keep 慢干人工根治 · issue#39 实锤）
- **根因**：门控日志说「4086ms 后出手即命中」，实际 5.5s 后才点到——门控开闸后引擎只能等【下一固定档扫描】或【内容事件】才出手，扫描链空档 3.6s→5s 是喜马拉雅/keep 慢于人工的头号元凶（人看到倒计时走完 200~300ms 就出手，引擎却在等档）
- **修复**：设门控的瞬间同步排一针【开闸 +40ms】的精确补扫（数字每秒跳动 → until 随锚点重算 → 自动重排，零冲突零漏扫；代际护栏防窗口已切）。开闸后 40ms 内出手，**快于人眼反应**
- 点击冷却 900ms → 600ms（快于人类「发现未关 → 再点」反应链；安全不破：复验仍需重新扫到跳过候选才出手）

### 2. 点击后 450ms/900ms 快速复验针（快于人手补刀）
- 此前复验只能等固定档扫描/内容事件（最慢 1.2s+）——「点了没反应」场景人手 300~500ms 已补第二刀，引擎还在等档
- 现在每次出手后自动排 450ms / 900ms 两针精确补验（代际护栏），未关立即换候选重试

### 3. H5 开屏按钮晚到也能救回（「有时候触发不了」根治 · issue#39-41 实锤）
- **根因**：H5/WebView 开屏广告容器可滚动，首扫（400ms）时按钮还没渲染（1~2s 才出现）→ 内容页早退把扫描链杀了 → 按钮真正出现时补扫全让路 = 永远点不到
- **修复（三重）**：
  - 杀链延迟 400ms → 2000ms（真开屏广告按钮最晚 1~2s 出现，过早杀链 = 主动放弃跳过）
  - 广告窗 / 学习窗 / Dialog 窗【绝不早退】（窗口本身就是广告，按钮只是还没渲染）
  - 杀链后的内容事件以 900ms 节流降频放行（滚动风暴被节流挡住，晚到跳过键仍能救回）

### 4. 恢复防护零触摸（「点恢复防护跳系统设置看 10 秒强制警告」根治）
- **服务真实绑定态判定**：\`onServiceConnected/onUnbind/onDestroy\` 维护 bound 标记——僵尸实例（解绑未销毁、收不到任何事件）不再假报「守护中」，这是「打开 App 看着是开的、实际早死了 → 只能去系统设置重开（MIUI 强制看 10 秒）」的元凶
- **深度拉活 6 秒**：后台线程每 500ms 用 AccessibilityManager 查询踢一次系统（促使 AMS 重绑）——MIUI/HyperOS 重绑慢的 ROM 也能等到（此前只等 1.5s，被误判需要去系统设置）
- **打开 App 即自动拉活**：检测到服务掉线（设置仍登记）自动深度拉活，连「一键恢复」都不用点
- 拉活成功后暂停自动解除；\`need_toggle\` 时引导重试，**绝不自动跳系统设置**

### 5. 功耗再降（对照 GKD/李跳跳事件策略）
- **息屏状态广播缓存**：SCREEN_ON/OFF 广播维护 volatile 标记，替代热路径上【每事件一次】的 PowerManager.isInteractive binder IPC（issue#40 实锤 4602 事件 = 4602 次 IPC）
- **非前台内容事件一步丢弃**：事件洪流主体（后台应用动画/刷新/灵岛动画）此前要过完整过滤器链才短路，现在纯内存字符串比较即丢弃（零 IPC 零哈希写）
- **前台追踪修正**：仅 WINDOW_STATE_CHANGED 更新前台包名（桌面壁纸动画的内容事件曾把前台包污染成桌面 → 真前台应用被前置过滤误伤 = 「触发不了」帮凶）
- 晚档固定扫描（≥3.6s）预算 600→360（真广告早该出手，深树几乎必是内容页）

## 验证
- JVM 211 项断言全绿（决策核心 151 + 规则引擎 60，真实机型场景建模）
- 覆盖安装（同签名，versionCode 40400）

---

<details>
<summary>历史版本</summary>

- v4.3.0：订阅主力规则接入（globalGroups 全量解析）· 复验真换候选 · 应用白名单 · 喜马拉雅循环根治
- v4.2.0：用户操作让路 · 微信主界面零扫描 · 订阅全门控 · 12h 自动日志 · 功耗整治
- v4.1.0：应用内一键暂停/恢复 · 微信拍摄强退根治 · 灵动岛前台门 · 订阅导入 404 修复 · 全新 Logo
</details>
`;

async function gh(path, init?) {
  const r = await fetch(`${GITHUB_API}${path}`, { ...init, headers: { ...H, ...(init?.headers || {}) } });
  const t = await r.text();
  let j: any = null;
  try { j = JSON.parse(t); } catch {}
  return { status: r.status, json: j, text: t } as any;
}

async function main() {
  console.log("▶ 1/3 创建 Release", TAG, "");
  const rel = await gh(`/releases`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ tag_name: TAG, name: `开屏卫士 ${TAG} · 露头就秒大提速 · 恢复防护零触摸`, body: notes }),
  });
  if (rel.status !== 201) {
    console.log("创建失败", rel.status, rel.text?.slice(0, 300));
    process.exit(1);
  }
  const aid = rel.json.id;
  console.log("  Release id:", aid, rel.json.html_url);

  console.log("▶ 2/3 上传 APK", APK_NAME);
  const apk = readFileSync(APK);
  const up = await fetch(`https://uploads.github.com/repos/${GITHUB_REPO}/releases/${aid}/assets?name=${APK_NAME}`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/vnd.android.package-archive" },
    body: new Uint8Array(apk),
  });
  if (up.status !== 201) {
    console.log("上传失败", up.status, (await up.text()).slice(0, 300));
    process.exit(1);
  }
  const sha256 = createHash("sha256").update(apk).digest("hex");
  console.log("  ✓", APK_NAME, (apk.length / 1024).toFixed(0) + "KB", "sha256:", sha256.slice(0, 16) + "…");

  console.log("▶ 3/3 完成");
  console.log("Release:", rel.json.html_url);
}

main();
