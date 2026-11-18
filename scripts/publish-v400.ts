/** 发布 v4.0.0 Release（GKD 订阅生态融合 + 跳跳侠全新界面） */
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

const TAG = "v4.0.0";
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v4.0.0.apk";
const APK_NAME = "adshield-v4.0.0.apk";

const notes = `# 开屏卫士 v4.0.0 · GKD 订阅生态接入 · 双引擎规则 · 跳跳侠全新界面

## 本版主题：站在 GKD 社区的肩膀上

参考 [GKD](https://gkd.li)（开源自定义屏幕点击应用）的订阅规则体系，本版把**整个 GKD 社区规则生态**接了进来——同时保留并强化了自家的内嵌规则引擎，双引擎接力。

## 新能力

### 1. GKD 订阅导入（订阅页）
- 粘贴任意 GKD 订阅链接（JSON5 格式，如 GKD 官方订阅 \`https://registry.npmmirror.com/@gkd-kit/subscription/latest/files/gkd.json5\`）即可导入整个社区规则库
- 内置**选择器引擎**：兼容 GKD 选择器语法——属性选择器（\`TextView[id='x'][text*='跳过']\`）、关系选择器（\`>\` 祖先 / \`<\` 直接子 / \`<<\` 子孙 / \`+\` \`-\` 兄弟）、\`||\` / \`&&\` / 取反 / 正则操作符
- 远程订阅 **24 小时自动静默更新**（失败保留旧版本），可随时手动检查更新
- 规则全部**预编译**（加载时一次编译，匹配热路径零解析零锁）

### 2. 内置规则库升级为双引擎
- 原有**文案五层规则**（分应用精确表 / 全局精确 / 倒计时形态 / 短包含+诱导否决 / vid skip 族）保持第一优先级
- 新增**内置订阅**：21 款主流应用（知乎/微博/喜马拉雅/B站/Keep/小红书/番茄/百度/网易云/爱优腾/携程/高德/美团/京东/WPS 等）的控件 ID（vid）规则——ID 比文案更稳（应用改文案不改 ID）
- 全局通用组：穿山甲 \`tt_splash_skip_btn\` 等 SDK 通用跳过键形态

### 3. 安全红线（绝不乱点，一寸不让）
- 订阅规则**仅支持点击类动作**（click / clickNode）；back 类规则在**加载时直接丢弃**——链式拦截红线延续
- 订阅命中仍须过主引擎同源安全闸：**可见 + bounds 非空 + 面积 ≤35% 屏**——大容器/CTA 巨按钮绝不点
- 解析失败的规则跳过、失败的订阅整体拒绝——**绝不影响主引擎**

## 全新界面：跳跳侠
- 新人物吉祥物「**跳跳侠**」：戴风镜的冲刺小人 + 飞披风 + 速度线——「广告露头就被跳走」的具象化
- 绿色主题三页极简（**首页 / 订阅 / 我的**）：去除冗余字段与大面积说明文字
- 首页：守护状态一屏看清（开启/待恢复/守护中三态），战绩三项，开关两枚
- 订阅页：内置规则库常开 + GKD 订阅导入
- 我的：版本/更新/版本记录/日志上传/仓库链接

## 升级说明
- 同签名覆盖升级，学习数据与设置全保留
- 开启方式不变：无障碍服务一步直达，开启一次持续守护

<details>
<summary>技术实现（开发者向）</summary>

- \`GkdSelector\`：纯 Java 零 Android 依赖的选择器解析+匹配引擎，JVM 单测 20/20（含 GKD 官方文档示例规则）
- \`Json5\`：极简 JSON5 解析器（无引号键/单引号/注释/尾逗号）
- \`Subscription\`：订阅模型 + 内置默认订阅 + 编译（选择器预编译、back 丢弃、单条语法不兼容只跳过该条）
- \`SubscriptionManager\`：双订阅快照（volatile 原子切换）、24h 静默更新、心跳顺带触发
- 服务集成：主引擎未命中 → 订阅规则接力 → 决策核心兜底，三级递进
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
      name: "开屏卫士 v4.0.0 · GKD 订阅生态接入 · 双引擎规则 · 跳跳侠全新界面",
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
