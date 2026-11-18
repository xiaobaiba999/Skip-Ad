/**
 * GitHub 管理脚本（bun scripts/gh-admin.ts <cmd>）
 * cmds: verify | ensure-repo | release
 * 令牌经 credentials.ts 混淆解密，命令行与输出均不出现明文。
 */
import { verifyToken, ensureRepo, createRelease, uploadReleaseAsset } from "../src/lib/github";
import { APP_VERSION, VERSION_HISTORY } from "../src/lib/version";
import { readFileSync } from "node:fs";

const cmd = process.argv[2] ?? "verify";

if (cmd === "verify") {
  const v = await verifyToken();
  console.log(JSON.stringify({ ok: v.ok, login: v.login ?? null, message: v.ok ? undefined : v.message }));
  process.exit(v.ok ? 0 : 1);
}

if (cmd === "ensure-repo") {
  const v = await verifyToken();
  if (!v.ok) {
    console.log(JSON.stringify({ ok: false, step: "verify", message: v.message }));
    process.exit(1);
  }
  const r = await ensureRepo();
  console.log(JSON.stringify(r));
  process.exit(r.ok ? 0 : 1);
}

if (cmd === "release") {
  const latest = VERSION_HISTORY[0];
  const body = [
    `## ${latest.tagline}`,
    "",
    ...latest.notes.map((n) => `- ${n}`),
    "",
    "---",
    "### 安装 / 升级 / 卸载",
    "",
    "| 操作 | 说明 |",
    "| --- | --- |",
    "| 新安装 | 下载 APK → 允许「安装未知应用」→ 安装 → 首页开启无障碍服务 |",
    "| 覆盖升级 | 直接安装更高版本（同一 `adshield.keystore` 签名 + versionCode 递增），无需卸载，学习数据保留 |",
    "| 应用内更新 | 老版本打开 App 自动弹更新横幅 → 本页直接下载 → 一键安装，全程不跳浏览器 |",
    "| 正常卸载 | 系统设置 → 应用管理 → 开屏卫士 → 卸载（普通用户应用，无锁定机制） |",
    "",
    "另：浏览器打开演示页 → 「添加到主屏幕」可作为 PWA 使用。历次 APK 归档见 android-app/dist/ 与 Releases。",
  ].join("\n");
  const r = await createRelease({
    tag: `v${APP_VERSION}`,
    name: `Skip-Ad v${APP_VERSION} · ${latest.tagline}`,
    body,
  });
  if (!r.ok || !r.id) {
    console.log(JSON.stringify(r));
    process.exit(1);
  }
  // 上传 APK 附件（应用内页内直更的数据源）
  const apkName = `adshield-v${APP_VERSION}.apk`;
  const apkPath = `android-app/dist/${apkName}`;
  let asset: { ok: boolean; url?: string; message?: string } = { ok: false, message: "APK 未找到：" + apkPath };
  try {
    asset = await uploadReleaseAsset({ releaseId: r.id, name: apkName, bytes: readFileSync(apkPath) });
  } catch (e) {
    asset = { ok: false, message: e instanceof Error ? e.message : String(e) };
  }
  console.log(JSON.stringify({ ...r, apk: asset }));
  process.exit(r.ok && asset.ok ? 0 : 1);
}

console.log("unknown cmd:", cmd);
process.exit(1);
