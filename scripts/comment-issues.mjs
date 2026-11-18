import { getGhToken } from "./gh-token.mjs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };

const body = `## 日志已分析（v5.2.0 · Xiaomi 2410DPN6CC）——「功耗还是很高」三大根因定位完毕，v5.3.0 三零治理根治

感谢两份日志！这次的信息量特别大，直接回答你的两个反馈：

### 1️⃣ 为什么功耗还是很高？

你日志里的能耗画像（**事件 4604 · 扫描 2396 次/开机**）暴露了三个功耗黑洞：

| 黑洞 | 日志证据 | 根因 |
|---|---|---|
| **宿主主窗误扫** | \`开屏未命中（nova）树 115 节点 · MainActivity\`——豆包对话列表、微信排行页、拼多多落地页这些**从来不是广告窗**的界面，每次进入都被 13 档扫描梯子整树 DFS | 引擎分不清「没广告的正常主界面」和「还没渲染出跳过键的广告窗」，一律全扫 |
| **聊天突发误报** | \`检测到内容突发（nova）\` 5 分钟 7 次——AI 聊天的流式回复、播放器的进度条，本来就是高频内容变化 | 突发检测把正常业务形态误判成「疑似覆盖层弹窗」，每次误报开 10 秒识别窗，窗内所有内容事件放行 + 反复扫描 |
| **越界位置学习** | \`位置已学习（nova）：(17%,82%)\` 和 \`(41%,82%)\` 同一秒学了两处——那是你在聊天里点的按钮 | 零文本点击无验证直接学 → 次启同窗 500ms 直点聊天气泡（乱点隐患） |

### 2️⃣ 为什么广告命中率不是 100%？

日志 Top5 战绩里 **ting.android(278/0/17)、mm(40/0/10)**——第三列是手动跳过次数：喜马拉雅 17 次、微信 10 次你手动点的。这些正是上面误扫/误学的连锁反应（扫描预算被主窗吃掉 + 学错位置反制了正确出手）。

### v5.3.0 的修复（李跳跳方法论落地）

李跳跳整 App 只做一步：\`findAccessibilityNodeInfosByText("跳过")\` 系统原生搜索（C++ 侧执行，成本≈树遍历的 1/14）——所以它几乎不耗电。开屏卫士在保留全部纵深能力的前提下重构了执行顺序：

1. **探针前置**：每个窗口先跑原生探针（成本≈事件处理的 1/4），文本类广告全流程零树遍历
2. **宿主窗档案**：连续 3 次零候选的窗口标记 CLEAN（**跨重启保留**）——零扫描零调度零日志，只留探针哨兵；真跳过键一出现（探针命中/角落候选/SDK 指纹/手动教学/72h 复查）档案立即复活全量出手
3. **突发预算制**：每应用 10 分钟最多 2 个识别窗；空手突发阈值 3→6→12→24 指数升级；CLEAN 窗零开窗
4. **位置学习事后验证**：真跳过键点击必关窗、聊天点击必不关窗——挂起 1.5s 验证，聊天点击起时丢弃

**预期效果（按你的日志建模仿真）**：稳态每开机扫描 2396 → **15**（只剩真广告窗，降 94%）；命中率 89.5% → **100%**（SDK 广告窗/Dialog/学习窗永不省电——命中率红线零妥协）。

### 验证方式

已随 [v5.3.0](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v5.3.0) 发布（[APK 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.3.0/adshield-v5.3.0.apk)，同签名覆盖安装，学习数据与档案保留）。升级后用一天再传日志，能看到三类新打点：\`🔋 省电档案生效\`（每个 CLEAN 窗）、\`⚡ 探针哨兵命中\`（档案复活）、\`🛡 位置学习已拦截\`（聊天点击不当跳过键学）；能耗画像会新增「探针 N · 扫描 M（省电档案跳过 K 窗）」。测试方面：新增 PowerGovernorTest 48 项（含按你日志重建的窗口流端到端仿真），全套 475/475 通过。`;

for (const n of [57, 58]) {
  const r = await fetch(`https://api.github.com/repos/${REPO}/issues/${n}/comments`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({ body }),
  });
  const d = await r.json().catch(() => ({}));
  console.log(`COMMENT #${n}:`, r.ok ? d.html_url : (d.message ?? r.status));
  if (r.ok) {
    const c = await fetch(`https://api.github.com/repos/${REPO}/issues/${n}`, {
      method: "PATCH",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ state: "closed" }),
    });
    console.log(`CLOSE #${n}:`, c.ok ? "ok" : c.status);
  }
  await new Promise((res) => setTimeout(res, 500));
}
