#!/usr/bin/env bun
/**
 * v7.9.6plus 正式 Release 发布脚本（一次性）。
 * - 创建正式 Release（非草稿/非预发布）
 * - body 顶部含机器可读 versionCode: 82300 元数据行（客户端主判数据源）
 * - 上传 APK 资产 + SHA-256 公布
 * - 核验资产 digest 与本地一致
 */
import { readFileSync } from "fs";

const TOKEN = readFileSync("/home/z/gh-push/token", "utf-8").trim();
const REPO = "xiaobaiba999/Skip-Ad";
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v7.9.6plus.apk";
const H = {
  "Authorization": `token ${TOKEN}`,
  "Accept": "application/vnd.github+json",
  "User-Agent": "skip-ad-release",
};

async function gh(path, init) {
  const r = await fetch(`https://api.github.com${path}`, { ...init, headers: { ...H, ...(init && init.headers) } });
  return r;
}

const body = `versionCode: 82300

# 开屏卫士 v7.9.6plus

以 v7.9.6 为稳定基线的最终重构版——启动时新版本检测 + 全屏更新页 + 多源下载安全校验 + 日志上传业务保持。

## 本次更新

1. 启动时优先检查更新：进入 App 自动获取 GitHub 最新正式 Release，远端 versionCode 高于本地（versionCode 主判）时优先显示全屏更新提示页；无新版本或网络异常静默进入 App，绝不阻塞使用；Draft/Pre-release 不作为正式更新推送，绝不提示降级安装
2. 全屏更新页：应用名/图标/当前版本/最新版本（含 versionCode）/更新内容/安装包大小一目了然；立即更新醒目主按钮 + 暂不更新随时继续使用当前版本，下次进入 App 仍会检测
3. 下载状态全展示：正在连接/下载进度（百分比与已下载大小）/当前使用的下载源/下载中断自动重试（系统断点续传）/正在切换备用下载源/正在校验 APK/校验成功准备安装/失败可重新下载；下载完成前绝不把不完整文件交给安装器；支持取消下载，重开更新页自动恢复未完成任务
4. APK 安全校验不降级：无论哪个下载源，安装前统一核对文件大小 + SHA-256（与本页公布值比对），任何一项不符拒绝安装并删除文件；正规系统安装流程确认，绝不绕过
5. 日志上传业务保持：正常采集（重启保留）/ 自动上传 24 小时最多成功 3 次（只记服务端确认，失败不耗额度，可关闭且不影响采集）/ 随时手动上传 / 成功创建 GitHub Issue 并显示真实编号与链接

## 下载

- 官方 APK：adshield-v7.9.6plus.apk（本页资产 · GitHub Releases 官方源 · HTTPS）
- 文件大小：886,326 字节
- SHA-256：\`f3b6d4cd54adb1b070cd70e4687f3bb7d29cb0217cda290ad35a9b7eb0db07c4\`

## 安装说明

- 已安装任何历史版本（含 v8.2.2）：直接安装本 APK 覆盖升级（versionCode 82300 > 82200，签名一致，数据保留）
- 新安装：直接安装即可（Android 8+ 首次安装需允许「安装未知应用」授权）

## 版本信息

- versionName：7.9.6plus
- versionCode：82300
- 签名证书 SHA-256：05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79（与历史正式版本一致）
- APK 零凭据（无 GitHub Token/私钥/Secret——写凭据全部只在服务端）`;

async function main() {
  // 1. 创建正式 Release
  console.log("── 1/3 创建正式 Release ──");
  const createRes = await gh(`/repos/${REPO}/releases`, {
    method: "POST",
    body: JSON.stringify({
      tag_name: "v7.9.6plus",
      name: "v7.9.6plus",
      body,
      draft: false,
      prerelease: false,
    }),
  });
  if (!createRes.ok) {
    console.error("创建失败:", createRes.status, await createRes.text());
    process.exit(1);
  }
  const rel = await createRes.json();
  console.log(`✓ Release #${rel.id} · ${rel.html_url} · draft=${rel.draft} prerelease=${rel.prerelease}`);

  // 2. 上传 APK 资产
  console.log("── 2/3 上传 APK 资产 ──");
  const apk = readFileSync(APK);
  const upRes = await fetch(
    `https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.9.6plus.apk`,
    {
      method: "POST",
      headers: {
        ...H,
        "Content-Type": "application/vnd.android.package-archive",
        "Content-Length": String(apk.length),
      },
      body: apk,
    },
  );
  if (!upRes.ok) {
    console.error("上传失败:", upRes.status, await upRes.text());
    process.exit(1);
  }
  const asset = await upRes.json();
  console.log(`✓ 资产 ${asset.name} · ${asset.size} 字节 · digest=${asset.digest}`);
  console.log(`  下载: ${asset.browser_download_url}`);

  // 3. 核验（重新 GET Release，确认资产在场 + digest + 正式状态）
  console.log("── 3/3 核验 ──");
  const verifyRes = await gh(`/repos/${REPO}/releases/latest`);
  const latest = await verifyRes.json();
  const a = (latest.assets || []).find((x) => x.name === "adshield-v7.9.6plus.apk");
  console.log(`latest tag=${latest.tag_name} draft=${latest.draft} prerelease=${latest.prerelease}`);
  console.log(`asset digest=${a?.digest} size=${a?.size}`);
  const expectSha = "sha256:f3b6d4cd54adb1b070cd70e4687f3bb7d29cb0217cda290ad35a9b7eb0db07c4";
  const ok = latest.tag_name === "v7.9.6plus" && !latest.draft && !latest.prerelease
    && a && a.digest === expectSha && a.size === 886326;
  console.log(ok ? "✓ 核验通过：正式 Release + 资产 digest/size 与本地一致" : "✗ 核验失败");
  process.exit(ok ? 0 : 1);
}

main();
