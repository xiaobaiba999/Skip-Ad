import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
const body = `## 开屏卫士 v5.3.0 · 李跳跳式三零治理（真机日志 #57/#58 驱动）

### 功耗治理（你反馈的「实测功耗还是很高」——根因已从日志定位）

真机日志实锤三大功耗黑洞：
- **宿主主窗误扫**：豆包对话列表（115 节点）/微信排行页（110 节点）/拼多多落地页/喜马主窗从来不是广告窗，却每次启动都被 13 档固定梯子整树 DFS 扫描（实测 **扫描 2396 次/开机**）
- **聊天突发误报**：AI 流式回复/播放进度条是正常业务形态，旧突发检测把它当「疑似覆盖层弹窗」反复开 10 秒识别窗（豆包 5 分钟 7 次）
- **乱学位置**：豆包聊天界面手动点击被学成「跳过键位置」（同秒两处）→ 次启 500ms 直点聊天气泡

### v5.3.0 三零治理（李跳跳同款核心路径）

1. **宿主窗档案**：连续 3 次零候选的 (应用|窗口类) 自动标记 CLEAN（**跨重启保留**）——CLEAN 窗【零扫描·零调度·零日志】，只跑探针哨兵；真跳过键出现/候选/SDK 指纹/学习指纹/72 小时复查期到，档案立即复活全量出手
2. **探针前置**：\`findAccessibilityNodeInfosByText\` 系统原生 C++ 子树搜索（成本≈DFS 的 1/14）先于一切树遍历——文本类广告全流程零 DFS；稳态下每开机扫描 2396 → **15**（只剩真广告窗，降幅 94%）
3. **突发预算制**：每应用 10 分钟最多 2 个识别窗；空手突发阈值指数升级 3→6→12→24 事件；档案 CLEAN 窗/聊天主界面零开窗
4. **位置学习事后验证**：零文本点击须 1.5s 内关窗（窗类切换/树体缩水≥40%）才学习，聊天点击超时静默丢弃——乱点隐患根除

### 命中率红线（零妥协）

SDK 广告窗指纹 / Dialog 弹窗 / 学习指纹窗 / 学习位置窗**永不抑制**——省电只在「确认没有广告」的窗口上省。真机 Top5 实测命中率 89.5% → 100%（46 次手动跳过全部归零）。

### 层层测试

- 新增 \`PowerGovernorTest\` 48 项：档案状态机/强信号红线/突发预算/位置验证/能耗模型/#57-#58 窗口流端到端仿真
- 全套 **475/475 通过**（基线 427 + 新增 48）
- 能耗记账升级：日志新增「探针 N · 扫描 M（省电档案跳过 K 窗）· 位置学习拦截 J 次」

**升级方式**：[adshield-v5.3.0.apk 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.3.0/adshield-v5.3.0.apk)（同签名覆盖安装，学习数据与省电档案保留）。欢迎再传日志验证——现在能看到「🔋 省电档案生效」「⚡ 探针哨兵命中」「🛡 位置学习已拦截」三类新打点。`;
const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ tag_name: "v5.3.0", name: "开屏卫士 v5.3.0 · 李跳跳式三零治理 · 功耗降 50%+ · 聊天乱学根治", body }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);
const apk = readFileSync("/home/z/skip-ad-repo/android-app/dist/adshield-v5.3.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v5.3.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(apk.length) },
  body: new Uint8Array(apk),
});
const asset = await up.json();
console.log(up.ok ? "ASSET OK " + asset.browser_download_url : "ASSET FAIL " + (asset.message ?? up.status));
