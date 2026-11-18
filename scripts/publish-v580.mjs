import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
const body = `## 开屏卫士 v5.8.0 · 屏蔽速度压榨 · 三轮激进 · 首页极简防呆 · 10×15=150 机型矩阵

> 用户三轮指令全量落地：**扫描日志继续优化（好多广告没有命中——大胆激进一点）· 屏蔽速度太慢了（压榨优化速度算法）· 首页保活太多描述一大串（怎么简单怎么来）+ 持久守护太复杂还连接电脑 · 订阅市场点击订阅直接导入（去仓库按钮/去底部更多订阅/App 内避免跳转/只留 3-5 个活跃 star 多的源）· 我的模块不显示权限审计 + 上传旁加刷新 + 上传成功只留一个确认弹窗（防呆）· 模拟 N 多品牌 × N 多机型测试修复 · 同步 GitHub 推送 Releases · 优化 GitHub 面板描述**。

### ① 屏蔽速度压榨（「按钮出现 → 点击命中」全链路）

| 参数 | v5.7.0 | v5.8.0 | 效果 |
| --- | --- | --- | --- |
| 点击冷却 | 420ms | **300ms** | 复验换候选节奏再快 0.12s |
| 倒计时缓冲 | 180ms | **120ms** | 将尽提前 0.38s 开火 |
| 骨架扫描档 | 13 档 | **18 档**（前 4s 密度×2） | 补齐「内容事件不发射」广告窗命中延迟 |
| 补扫节流 | 120ms | **80ms** | 「按钮出现→点击」延迟上限减半 |
| 弹窗冷却 | 1600ms | **1200ms** | 弹窗复验换候选提速 |
| 同窗点击预算 | 10 | **12** | 深树复验链不再提前收手 |
| 跳过候选 | 6 | **8** | 换候选零等待 |

功耗红线保持：典型日 53.31µWh（< 0.15mWh 红线，实测口径见测试报告）。

### ② 命中三轮激进

- SDK 指纹 **+4 段**：Mintegral / Pangle（穿山甲国际版）/ TopOn（聚合）/ Moloco——聚合与国际 SDK 漏网主力，累计指纹 44+ 段
- 「推广」文案族全量补收：\`跳过推广\` / \`关闭推广\` / \`跳過推廣\`——合规敏感 App 普遍以「推广」代「广告」作整词标签，旧库未收零命中
- 分应用表 +10 款：快手 / 搜狐视频 / 闲鱼 / 酷安 / 虎牙直播 / 斗鱼 / 百度网盘 / WiFi万能钥匙 / 链家 / 抖音主线
- 内置规则库 v8→**v9**；LearnGovernor 泛化清单同源对齐

### ③ 首页极简（怎么简单怎么来）

- **保活体检一行状态化**：全部通过 → 一行绿字「✓ 全部通过，防护稳了」；有待处理 → 只列待处理项（标签 + 去设置按钮），长描述全删
- **持久守护移出首页** → 「我的 → 高级」（可选极客玩法，普通用户路径零系统设置零电脑：一键恢复 + 前台看门狗自动拉活）

### ④ 订阅市场防呆版

- 只留 **3 个高星活跃源**：Lin-arm ⭐7.4k（日更）/ ganlinte ⭐935（610 款应用）/ MengNianxiaoyao ⭐496
- **点一下整行即导入**：右侧状态位「导入中… → 已导入 ✓」（失败「再点重试」）
- 仓库按钮 / 底部更多订阅 / App 内跳转**全部移除**

### ⑤ 我的模块防呆

- 权限使用审计卡移除（零权限声明在 README 与系统设置可查）
- 引擎日志旁新增**「刷新」按钮**（重读服务内存实时态）
- 上传成功**只保留一个确认弹窗**（顶部横幅与成功通知移除——单一反馈通道；失败通知保留兜底）

### ⑥ 10 品牌 × 15 机型 = 150 机型测试矩阵（六维全 150/150）

新增**魅族·Flyme 独立族**、**中兴/努比亚/红魔·MyOS 独立族**（各族 12→15 款），覆盖面较 v5.7.0 的 8×12=96 **+56%**：

| 维度 | 结果 |
| --- | --- |
| 开屏广告命中 | 150/150（六信号：文本/desc/vid 订阅/图片角落键/新文案「跳过推广」/复合 vid 键——全命中） |
| 屏蔽速度 | 150/150（120 节点深树 walk+decide < 20ms · 压榨参数在场断言） |
| bug 测试 | 150/150（菜单陷阱/巨横幅/overlay/游戏 jump 键四陷阱零动作） |
| 功耗测试 | 150/150（典型日 53.31µWh < 0.15mWh 红线 · 分级预算在场） |
| 精准度测试 | 150/150（银行守护零返回键 · 验证码标记 · Unity 游戏引擎窗免疫） |
| 算法优化测试 | 150/150（新指纹 4 段全命中 · 非广告窗 7 档全免疫 · 10 品牌族归一每机型全验） |

**全仓 1666 项 JVM 断言全绿（+336）**。测试报告：[\`docs/test-reports/round-24.json\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/test-reports/round-24.json)，二十四轮轨迹见 [\`summary.md\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/test-reports/summary.md)。

### ⑦ GitHub 面板描述

README 同步 v5.8.0（徽章 1666 断言 / 10×15 矩阵 / 速度压榨行）+ App 截图三连重摄（首页极简版 / 订阅市场防呆版 / 我的清爽版）：[\`docs/screenshots/\`](https://github.com/xiaobaiba999/Skip-Ad/tree/main/docs/screenshots)。

### 升级方式

**[adshield-v5.8.0.apk 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.8.0/adshield-v5.8.0.apk)**（versionCode 50800 > 50700，同签名直接覆盖安装；无障碍授权/学习数据/白名单/已导入订阅全部保留）。旧版 App 内「我的 → 检查更新」也会自动发现本版本并页内直下。

| 校验 | 值 |
| --- | --- |
| MD5 | \`2e093296e2ded4e68ff65649cbc73563\` |
| 体积 | 300,382 B（296KB） |
| versionCode / versionName | 50800 / 5.8.0 |
| minSdk / targetSdk | 24 / 34 |`;
const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ tag_name: "v5.8.0", name: "开屏卫士 v5.8.0 · 屏蔽速度压榨 · 三轮激进 · 首页极简防呆 · 10×15=150 机型矩阵 · 1666 断言", body }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);
const apk = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v5.8.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v5.8.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(apk.length) },
  body: new Uint8Array(apk),
});
const asset = await up.json();
console.log(up.ok ? "ASSET OK " + asset.browser_download_url + " (id " + asset.id + ")" : "ASSET FAIL " + (asset.message ?? up.status));
