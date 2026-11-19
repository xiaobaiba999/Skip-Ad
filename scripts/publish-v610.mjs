import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };

/* ── 面板描述（GitHub 仓库 description 字段）同步 v6.1.0 ──
 * 用户实锤：优化 GitHub 面板描述 + 加免责声明（免责声明正文在 README 专章）。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · 1:1 人手模拟触点 + 误触三重防线（防重复点击·新鲜坐标·结果静默）· 订阅导入 5 镜像自愈（Connection reset 根治）· 零联网 · 免 Root · APK 301KB · Java 引擎（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes：前 5 个 ## 标题 = App 内「新版亮点」卡片展示内容
 *（v6.1.0 notesToHeadlines() 解析 ≤5 标题——与「发现新版本时言简意赅展示
 *  新版本功能描述」1:1 呼应）── */
const body = `## 订阅拉取自愈：导入失败自动换 5 个镜像通道
## 误触根治：防重复点击 · 按最新位置点击 · 点击后静默
## 日志每天自动提交 3 次（留存翻倍 · 重点事件独立留存）
## 免打扰持久模式取消：全程零电脑
## 9 品牌 × 20 机型 = 220 测试矩阵七维全绿

> 用户实锤（第八轮逐字）：**订阅市场点击导入时提示拉取失败 Connection reset** · 优化版本信息描述言简意赅一个标题一个段落（发现新版本时同样显示新版本功能描述，不超过 5 个标题）· 高级免打扰持久模式必须电脑执行就取消 · 新增自动提交日志（一天 3 次 24 小时均匀、留存更多、重点类型留存）· 设备类型/分辨率原因导致多点误点——**1 比 1 模拟人在不同设备上操作使用卫士** · 只测试 oppo/vivo/xiaomi/三星/华为/荣耀/红米/一加/iqoo 及其子品牌（每品牌 N 多机型，开屏广告/屏蔽速度/bug/功耗/精准度等全模块算法）· 完成同步 GitHub 推送 releases · 优化 GitHub 面板描述加免责声明。

### ① 订阅拉取 Connection reset 根治（五镜像通道 + 同址重试）

| 层 | 修复 |
| --- | --- |
| 根因 | 订阅市场 3 源全在 \`cdn.jsdelivr.net\`，国内运营商间歇性 TCP 重置；旧镜像链对 jsdelivr 本身零回退——单源被重置即「拉取失败：Connection reset」 |
| 变体链 | jsdelivr → fastly.jsdelivr（同 CDN 另一接入点）→ ghfast.top 代理 → gh-proxy.com 代理 → raw.githubusercontent 直连，**5 变体任一成功即导入成功** |
| 同址重试 | 瞬时故障（reset/timeout/EOF）600ms 重试 1 次；HTTP 4xx/5xx 立即换址——最坏 10 次出手 |
| 超时/UA | 连接 10s→7s · 读取 15s→12s（快速失败快速切镜像）；CDN/代理站用浏览器 UA |
| 人话化 | 「Connection reset」→「网络被运营商/CDN 重置，稍后重试或换网络通常可恢复」（5 类错误翻译，普通用户零术语） |

### ② 误触二轮三重防线（多点/误点残余路径根治 · HumanTap F/G/H）

| 防线 | 机制 | 根除的多点/误点路径 |
| --- | --- | --- |
| F 同指纹目标去抖 | 点击派发后 **1500ms** 内同指纹目标（包名+中心容差网格+语义标签）绝不再出手 | 点击已生效但广告跳转有 300~800ms 动画延迟，复验扫描看到「按钮还在」再点一次 = 用户肉眼的多点 |
| G 出手前新鲜度复核 | 出手时 re-fetch 最新 bounds，中心位移 > max(旧矩形短边×50%, 24dp) 判动画位移中 → 放弃本次出手等下一轮 | 按钮入场缩放/位移动画中按旧坐标点空（「它曾在哪」vs「它现在在哪」）；节点已消失绝不盲刺 |
| H 结果静默期 | 手势派发成功后 **250ms** 内同包任何新候选不再出手 | 真人完成一次点击后物理上不可能 0ms 连续再点；机器连发是反自动化 SDK 触点检测的直接特征 |

三防线全部纯函数 JVM 可测；新窗切换同步清零（防线只在同一广告窗内生效，跨窗零残留）；\`firedTargetAt\` 256 条防膨胀兜底。

### ③ 日志增强（一天 3 次 · 留存翻倍 · 重点类型留存）

- **自动提交 12h → 8h**：每天 3 次、24 小时均匀（8h/16h/24h 时点），成功才计时，息屏/服务未运行跳过（绝不夜间耗电）
- **留存 120 → 300 条**：订阅拉取失败/自愈/误触防护等低频重点事件不再被高频命中日志挤出留存窗
- **上报携带 16 → 40 条**带时间戳引擎日志
- **重点事件留存段**（「什么类型要重点留存」的答案）：误触防护 · 自愈/拉活 · 订阅异常 · 引擎预警四类独立统计行 + 最近 6 条重点事件原文

### ④ 版本信息描述精简 + 免打扰持久模式取消

- 版本记录 5 条长要点 → **1 个标题 + 1 个段落**（「更快、更稳、更省心」）
- 发现新版本时展示**新版本功能描述 ≤5 标题**（更新横幅「新版亮点」+ 我的页检查更新同源渲染——即本页前 5 个标题）
- **免打扰持久模式取消**（用户实锤：不能在安装时实现、必须连电脑执行太麻烦）：ADB 极客入口整体移除——一键恢复 + 前台看门狗 + 零触摸自愈覆盖全部免电脑场景；**v6.1.0 起无任何需 ADB/电脑授权项**

### ⑤ 9 品牌 × 20 机型 = 220 测试矩阵（收窄 + 扩容 · 七维全绿）

用户指定品牌范围：**oppo / vivo / xiaomi / 三星 / 华为 / 荣耀 / 红米 / 一加 / iqoo 及其子品牌（realme / POCO）**——11 组 × 20 机型 = 220（魅族/中兴努比亚红魔/传音/摩托罗拉/其他五组按要求移除）；系统覆盖 HyperOS 2-3 · HarmonyOS 4.2-5.1 · MagicOS 8-10 · ColorOS 15-16 · OriginOS 5-6 · One UI 6.1-8 · OxygenOS 15；分辨率覆盖 720p 入门 → 2K+ 旗舰、折叠外屏窄幅、平板。

| 维度 | 结果 | 新增验证点 |
| --- | --- | --- |
| 开屏广告命中 | 220/220 | 六信号全命中（文本/desc/vid/角落/推广文案/复合vid） |
| 多点误点专项（v6.1.0 新增维度） | 220/220 | 同指纹连点 10 次仅 1 次出手 9 次拦截 · 位移 60% 拒绝/微抖 2px 放行/null 拒绝 · 250ms 结果静默 · 常量在场 |
| 人手触点安全 | 220/220 | dp 自适应密度五轮换点全落跳过键可见域 · 抖动 ±2px 不出界 |
| 屏蔽速度 | 220/220 | 120 节点深树 < 20ms/决策 · 人手引擎参数在场 |
| bug 测试 | 220/220 | 菜单陷阱/巨横幅/验证码横幅/游戏 jump 四陷阱零动作 |
| 功耗测试 | 220/220 | 典型日 < 0.15mWh 红线 · 分级预算在场 |
| 精准度测试 | 220/220 | 银行守护零返回键 · 验证码标记 · Unity 游戏窗免疫 |
| 算法优化 | 220/220 | 50+ 段 SDK 指纹全命中 · 非广告窗全免疫 · 11 品牌族归一全对 |

**全仓 2858 项 JVM 断言全绿（+506）**。测试报告：[\`docs/test-reports/round-27.json\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/test-reports/round-27.json)，二十七轮轨迹见 [\`summary.md\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/test-reports/summary.md)，本轮实录：[\`docs/OPTIMIZATION-v6.1.0.md\`](https://github.com/xiaobaiba999/Skip-Ad/blob/main/docs/OPTIMIZATION-v6.1.0.md)。

### ⑥ GitHub 面板描述 + 免责声明

README v6.1.0（徽章 2858 断言 / 9 品牌×20=220 矩阵 / APK 301KB）；**新增「免责声明」专章**（使用者责任 · 不作担保 · 非商业用途 · 合规红线内置 · 数据与隐私五条）；仓库 description 字段同步。

### 升级方式

**[adshield-v6.1.0.apk 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v6.1.0/adshield-v6.1.0.apk)**（versionCode 60100 > 60000，同签名直接覆盖安装；无障碍授权/学习数据/白名单/已导入订阅全部保留）。旧版 App 内「我的 → 检查更新」也会自动发现本版本并页内直下；更新横幅展开即见 ≤5 标题新版亮点。

| 校验 | 值 |
| --- | --- |
| MD5 | \`a2bd1027736739c81780d4cbd3617bdc\` |
| 体积 | 308,574 B（301KB） |
| versionCode / versionName | 60100 / 6.1.0 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v6.1.0",
    name: "开屏卫士 v6.1.0 · 订阅拉取自愈（Connection reset 根治）· 误触三重防线 · 日志一日三次 · 9 品牌×20=220 机型矩阵 · 2858 断言",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/skip-ad-repo/android-app/dist/adshield-v6.1.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v6.1.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(apk.length) },
  body: new Uint8Array(apk),
});
const asset = await up.json();
if (!up.ok) { console.log("ASSET FAIL", asset.message); process.exit(1); }
console.log("ASSET OK #" + asset.id, asset.browser_download_url, asset.size + "B");
