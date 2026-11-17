/** 发布 v4.1.0 Release（真机反馈整治：一键暂停 · 相机黑名单 · 灵动岛前台门 · 订阅 404 修复 · 新 Logo） */
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

const TAG = "v4.1.0";
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v4.1.0.apk";
const APK_NAME = "adshield-v4.1.0.apk";

const notes = `# 开屏卫士 v4.1.0 · 真机反馈整治 · 一键暂停 · 三大误触发根治

## 本版主题：把 v4.0.0 真机日志里的问题一次清零

依据 [issue#30](https://github.com/xiaobaiba999/Skip-Ad/issues/30) 引擎日志（\`检测到内容突发（mm）→ 疑似覆盖层弹窗 → 已点击跳过（mm）：开屏×关闭键\`）逐条根因修复。

## 新能力

### 1. 应用内一键暂停/恢复（首页）
- 守护中状态新增「暂停防护」按钮，暂停后首页变「已暂停」态，一键「恢复防护」即回
- **不再需要去系统设置关无障碍**——免除 Android 关闭无障碍服务时的 10 秒警告
- 暂停期间服务保持存活（保活四件套不受影响），但绝不扫描、绝不点击、绝不学习；恢复后无缝衔接

### 2. 相机/拍摄窗硬黑名单（微信「拍摄」被强退 · 根治）
- 根因：相机预览界面 = SurfaceView 简单树 + 顶部「×」关闭键，与开屏广告窗特征雷同，引擎把微信相机的关闭键当成了「开屏×关闭键」
- 修复：包名/窗口类名含 \`camera / capture / shoot / videoedit\` 的窗口**绝不扫描、绝不点击、绝不学习**（微信拍摄、抖音故事相机、扫码页、各厂商相机 App 全覆盖）

### 3. 灵动岛/悬浮窗前台门（灵动岛乱跳乱点 · 根治）
- 根因：灵动岛展开的窗口事件曾被当成「新应用进入前台」——重置开屏计时、启动开屏扫描链，灵岛小窗控件被当「角落跳过键」乱点；灵岛动画的高频内容变化还触发「内容突发→覆盖层弹窗」识别窗
- 修复（三重门）：
  - **前台门**：开屏扫描链只对前台应用生效，非前台包一律降级为严格门控的弹窗路径
  - **窗口形态门**：焦点窗口归属 + 小窗尺寸（面积 <45% 屏或高度 <55%）双重判定，灵岛小窗绝不当 Activity
  - **突发检测门**：内容突发检测只统计前台应用，灵岛/悬浮窗动画绝不开弹窗识别窗

### 4. GKD 订阅导入 404 修复
- 根因：npmmirror 上 \`@gkd-kit/subscription\` 官方订阅包文件路径已从 \`/files/gkd.json5\` 迁移到 \`/files/dist/gkd.json5\`，旧官方链接整体 404
- 修复：拉取失败**自动切换镜像回退链**（npmmirror 路径变体 / GitHub raw → jsdelivr CDN / 官方订阅 → npmmirror 镜像），成功后仍按你填写的原始链接记忆
- 「官方订阅」示例按钮已更新为可用链接

### 5. 全新 Logo
- 白色小机器人侠 + 绿披风跳跃形象（AI 生成 + VLM 三轮评审迭代），替换旧「跳跳侠」
- 全套烘焙：启动器五密度图标（小尺寸专用简化头版）/ PWA 图标 / maskable 安全区实测达标（最大半径 30.5% < 40%）/ favicon / 应用内吉祥物

## 升级说明
- 同签名覆盖升级，学习数据、订阅与设置全保留
- 已导入的远程订阅会自动继续使用（24h 静默更新同样走镜像回退）

<details>
<summary>技术实现（开发者向）</summary>

- \`AdSkipService\`：\`masterOn\` 静态主开关（SharedPreferences 持久化，暂停期间仅维持窗口/前台追踪）；\`isActiveWindowOf\` / \`isSmallOverlayWindow\` 前台与小窗判定；TYPE_WINDOW_STATE_CHANGED 非前台包分流（Dialog 形态才走弹窗识别）
- \`AdDecisionCore\`：\`isCameraPkg\` / \`isCameraClass\` 相机硬黑名单（服务事件入口 / 扫描链 / 位置学习三处拦截）
- \`SubscriptionManager\`：\`mirrorVariants\` 镜像变体生成 + \`fetchOne\` 单址拉取（成功记忆原始 URL，展示用户所填）
- 决策核心 JVM **151/151** + 规则引擎 **60/60** 全绿
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
      name: "开屏卫士 v4.1.0 · 一键暂停 · 相机/灵动岛误触发根治 · 订阅 404 修复",
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
