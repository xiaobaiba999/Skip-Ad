import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
const body = `## 开屏卫士 v5.9.0 · 设备自适应触点（多点/无点=误触根治）· 覆盖层防误触 · 四轮激进 · 11×15=165 机型矩阵

> 用户四轮指令全量落地：**扫描日志继续优化（大胆激进一点）· 用户设备不同就可能导致多点或无点——误触（本轮核心）· 优化怎么简单怎么来 · 模拟 N 多品牌 × N 多机型测试修复（开屏广告/屏蔽速度/bug/功耗/精准度/算法优化）· 完成后同步 GitHub 推送 Releases · 优化 GitHub 面板描述**。

### ① 设备自适应触点引擎（用户实锤：设备不同→多点或无点=误触）

对标签「原始几何中心」派发手势在千差万别的设备几何上不是「点不点得到」而是「点得对不对」。新核心 \`TapPoint\` 四层防线：

| 防线 | 机制 | 根除的事故面 |
| --- | --- | --- |
| L1 祖先可见交集 | 标签 ∩ 逐层祖先 bounds（不可见/被全裁 → 拒点） | 标签被容器裁一半 → 旧几何中心落在不可见区=点到旁边内容（720p 低端机点中「立即下载」） |
| L2 节点窗口钳制 | 标签 ∩ 自身窗口 bounds（\`node.getWindow()\`） | 分屏/折叠另半区 → 触点落到另半屏他应用=误触他 App |
| L3 安全选点 | **复验象限轮换**（同候选第 N 次换象限点）+ 边缘内缩 6px + 可见缝 <12px 盲刺拒绝 + 扁/竖形变恒居中带 | 「多点」=同坐标盲刺反复无效果；圆角/水波纹/刘海死区 |
| L4 覆盖层 Z 序判定 | Z 序高于广告窗的任意窗口包含触点 → 拒发手势 | 输入法弹出乱打字 · 验证码横幅被点掉 · 悬浮窗被误触 |

**拒发手势 ≠ 放弃**：自动降级 \`performAction\` 无障碍代点（不经屏幕坐标，点击直达语义目标，绝不碰覆盖物）；代点失败才由复验扫描换候选。宁可漏拦绝不误触红线保持。

### ② 覆盖层防误触泛化（设备差异真实事故面根除）

v3.13.0 只拦 SystemUI 顶条横幅 → v5.9.0 拦 Z 序高于广告窗的【任意】窗口：**输入法键盘 / 悬浮球 / 他应用 overlay / 状态栏 / heads-up 横幅**。查不到活跃窗（特殊 ROM 过渡态）退化旧行为兜底。

### ③ 命中四轮激进（扫描日志继续优化）

- SDK 指纹 **+2 段**：\`ksad\`（快手 KSAdSDK 广告窗族）· \`csj_\`（穿山甲联盟复合命名族）——累计 46+ 段；**刻意不用裸 \`csj\` 短段**（\`com.csj\` 业务包碰撞 → 误报 → 根窗返回键=退出应用红线）
- 分应用表 **+6 款**：网易新闻 / 腾讯新闻 / 新浪新闻 / 夸克浏览器 / 有道词典 / 哈啰出行（新闻/工具类高频 App 未命中诊断日志实锤）
- LearnGovernor 泛化清单同源对齐（csj_）

### ④ 11 品牌 × 15 机型 = 165 机型测试矩阵（六维全 165/165）

新增**传音 Tecno/Infinix/itel · HiOS/XOS 独立族** 15 款（Camon 40 Premier/40 Pro/30 Pro · Spark 30 Pro/20 · Pop 9 · Note 50 Pro+/40 Pro · Hot 50 Pro/40i · GT 20 Pro · Zero 40 · S25 Ultra · A70 · P70——非洲/东南亚/南亚真实大市场）：

| 维度 | 结果 |
| --- | --- |
| 开屏广告命中 | 165/165（六信号：文本/desc/vid 订阅/图片角落键/「跳过推广」/iv_ad_close 复合键） |
| 屏蔽速度 | 165/165（120 节点深树 walk+decide < 20ms · v5.8 压榨参数在场断言） |
| bug 测试 | 165/165（菜单陷阱/巨横幅/验证码横幅/游戏 jump 键四陷阱零动作） |
| 功耗测试 | 165/165（典型日 53.31µWh < 0.15mWh 红线 · 分级预算在场） |
| 精准度测试 | 165/165（银行守护零返回键 · 验证码标记 · Unity 游戏引擎窗免疫） |
| 算法优化测试 | 165/165（v5.9 新指纹 ksad/csj_ 全命中 · 非广告窗 7 档全免疫 · 11 品牌族归一含传音→generic） |

### ⑤ 设备自适应触点矩阵（新增 · 24 类几何 × 9 场景全绿）

24 类真实设备几何（直板 720p~1440p · 16:9 遗留 · 折叠外屏窄 21.3:9/Flip 超窄 22.5:9/内屏近方三方档 · 平板 16:10/3:2/4:3 · 分屏半窗横竖 · 刘海大缺/挖孔 · 超宽 QHD · 正方形态 · 超长 21:9 · 横屏）× 9 误触场景（半裁剪标签点可见侧 / 窗外拒点 / 盲刺拒绝 / 象限轮换不盲刺同坐标 / 边缘内缩零贴边 / 覆盖层拦截 / 不可见祖先拒点 / 扁标签形变防死区 / 引擎全链路落点安全）。

**全仓 1985 项 JVM 断言全绿（+319）**。测试报告：[\`docs/test-reports/round-25.json\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/test-reports/round-25.json)，二十五轮轨迹见 [\`summary.md\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/test-reports/summary.md)。

### ⑥ GitHub 面板描述

README 同步 v5.9.0（徽章 1985 断言 / 11×15=165 矩阵 / **新增「设备自适应触点」徽章**）；「一屏速览」「核心能力」新增触点防线行；优化实录 [\`docs/OPTIMIZATION-v5.9.0.md\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/OPTIMIZATION-v5.9.0.md)。

### 升级方式

**[adshield-v5.9.0.apk 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.9.0/adshield-v5.9.0.apk)**（versionCode 50900 > 50800，同签名直接覆盖安装；无障碍授权/学习数据/白名单/已导入订阅全部保留）。旧版 App 内「我的 → 检查更新」也会自动发现本版本并页内直下。

| 校验 | 值 |
| --- | --- |
| MD5 | \`ff03f9ec4cba29e2280811243772d116\` |
| 体积 | 304,478 B（297KB） |
| versionCode / versionName | 50900 / 5.9.0 |
| minSdk / targetSdk | 24 / 34 |`;
const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ tag_name: "v5.9.0", name: "开屏卫士 v5.9.0 · 设备自适应触点（多点/无点=误触根治）· 覆盖层防误触 · 四轮激进 · 11×15=165 机型矩阵 · 1985 断言", body }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);
const apk = readFileSync("/home/z/Skip-Ad/android-app/dist/adshield-v5.9.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v5.9.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(apk.length) },
  body: new Uint8Array(apk),
});
const asset = await up.json();
console.log(up.ok ? "ASSET OK " + asset.browser_download_url + " (id " + asset.id + ")" : "ASSET FAIL " + (asset.message ?? up.status));
