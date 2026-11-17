/** 发布 v4.3.0 Release（订阅主力规则接入 · 复验真换候选 · 应用白名单 · 喜马拉雅循环根治 · 功耗再降） */
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

const TAG = "v4.3.0";
const APK = "/home/z/skip-ad-repo/dist/adshield-v4.3.0.apk";
const APK_NAME = "adshield-v4.3.0.apk";

const notes = `# 开屏卫士 v4.3.0 · 订阅主力规则接入 · 复验真换候选 · 应用白名单 · 功耗再降

依据 [issue#38](https://github.com/xiaobaiba999/Skip-Ad/issues/38) v4.2.0 引擎日志（\`订阅：内置 21 款/21 条（命中 0）· 远程 327 款/828 条（命中 0）\` + \`csdnplus 检测到内容突发后无任何下文\` + \`喜马拉雅同坐标复验连击\`）逐条根因修复。

## 修复

### 1. 订阅规则「导入了却零命中」真正根治（issue#38 + 上轮遗留）
- **根因（本轮实锤）**：GKD 默认订阅的【主力开屏规则全部住在 \`globalGroups\`】（对所有应用生效的全局组，如「开屏广告」组 \`[text*="跳过"][text.length<10]\`）——而旧解析器只读 \`apps\` 数组，主力规则被整体丢弃；CSDN 等第三方 App 全部漏跳
- 同时补齐语法盲区（旧解析「成功」但永不匹配 = 静默死规则）：
  - **数字关系符** \`@View <3 FrameLayout <2 FrameLayout < [vid="fl_third_ad"]\`（CSDN 开屏规则原样命中）——旧解析器把「3」当标签名
  - **childCount**（默认订阅 372 处）、**text.length**（74 处）、**visibleToUser**（93 处）、**括号布尔** \`(A&&B)||C\` 嵌套表达式
  - 组级字段：\`enable\`（CSDN「更新弹窗」等默认停用组跳过）、\`matchTime\`（进入窗口 N ms 内才激活）、\`actionMaximum\`/\`actionCd\`；\`preKeys\` 链式规则跳过；\`rules:'…'\` 字符串快捷式支持
- 安全红线不松：全局组无 Activity 限定的规则目标仍过诱导词否决（「摇一摇领福利」类绝不代点）；结构规则（关系算子链锚定容器）可点零文本图片键

### 2. 复验真正换候选（此前日志「换第 N 个候选」是假的）
- issue#38 实锤：喜马拉雅「复验未关闭 → 第 2 次出手…换第 2 个候选」× 6——实际每次重扫都返回【同一个首个命中】，同一坐标连点 6 次广告不关
- 修复：规则引擎一次扫描收集全部候选（按置信度 + 短文案排序），按复验次数轮换出手；订阅 vid 候选（喜马拉雅 \`main_ad_skip\`）在文本候选用尽后自动接管

### 3. 跳过按钮位置随机变化（issue#38「每次出现在不同地方」）
- 角落候选扩展至底部（右下/左下 18% 带）——部分应用跳过键随机出现在右下角
- GKD 社区结构规则（位置无关的容器锚定）随 globalGroups 接入同步生效

### 4. 喜马拉雅「为什么关闭广告」循环弹窗根治 + 跳过提速
- 新增强特征话术：「为什么关闭广告」「请选择关闭原因」等 8 变体 → 面板识别后停用重试 + 代点中性收起键（取消/暂不反馈/下次再说/稍后再说/关闭，绝不替你提交任何反馈）
- **假倒计时识别**：真倒计时每秒跳数字；烧在图里的装饰数字永不变化——首击未关且数字 1.6s 未变 = 不再门控等 5 秒，立即换候选按普通节奏重试

### 5. 应用白名单（新功能）
- 首页新增「应用白名单」卡片：列出卫士处理过的应用（带应用名），开关直选；也可手动输入包名添加
- 白名单内应用零扫描、零点击、零学习、零突发检测（与桌面黑名单同级）

### 6. 日志更具体（issue#38「无法跳过且日志没记录」）
- 扫描未命中时记诊断日志（每窗一条）：应用名 + 窗口类名 + 树规模 + 可点控件样本（文案/vid + 坐标）——上传日志即可定位缺哪种规则

### 7. 功耗再降（GKD/李跳跳方法论）
- **补扫节流 bug 修复**（功耗黑洞元凶）：此前「前 4s 高频补扫」比较的是窗口起点而非上次补扫——信息流滚动时每个内容变化都排整树扫描（10~30 次/秒）；改为真节流 120ms
- **内容页早退**：可滚动/有输入框/长文本且零候选 = 正常内容页 → 本窗剩余 10+ 个固定扫描档全部取消（信息流 App 启动功耗大降）
- 扫描链 16 → 13 档；节点预算分层（早档 340 提速 / 晚档 600 覆盖）；小窗探测 500ms 缓存（灵动岛事件不再每次 getWindows 重 IPC）；亮屏心跳改窗口式闹钟（系统批量调度）

## 验证
- JVM 33 项断言全绿（真实 GKD 默认订阅 558KB 端到端解析 + CSDN 结构规则命中 + 假倒计时/白名单/角落扩展/诱导词否决单测）
- 覆盖安装（同签名，versionCode 40300）

---

<details>
<summary>历史版本</summary>

- v4.2.0：用户操作让路 · 微信主界面零扫描 · 订阅全门控 · 12h 自动日志 · 功耗整治
- v4.1.0：应用内一键暂停/恢复 · 微信拍摄强退根治 · 灵动岛前台门 · 订阅导入 404 修复 · 全新 Logo
- v4.0.0：GKD 订阅生态接入 · 双引擎规则库 · 全新界面
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
  console.log("▶ 1/3 创建 Release", TAG);
  const rel = await gh(`/repos/${GITHUB_REPO}/releases`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ tag_name: TAG, name: `开屏卫士 ${TAG} · 订阅主力规则接入 · 复验真换候选 · 应用白名单`, body: notes }),
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
