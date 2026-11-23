#!/usr/bin/env bun
/**
 * v7.10.0 正式 Release 发布脚本（灾后重建轮 · 一次性）。
 * - 凭据源：gh-token.mjs（SKIPAD_GH_TOKEN / GH_TOKEN / GITHUB_TOKEN 环境变量
 *   → 仓库根未入库文件 local-gh-token——绝不入库绝不打印）
 * - 前置：main 已推送到 origin（HEAD == origin/main，ahead 0——本脚本硬闸校验）
 * - 前置：android-app/dist/adshield-v7.10.0.apk 已构建并通过
 *   aapt（versionName=7.10.0/versionCode=80000）+ apksigner（历史签名）核验
 * - 创建正式 Release（非草稿/非预发布，tag v7.10.0 → 当前 main HEAD）
 * - body 顶部含机器可读 versionCode: 80000 元数据行（三段式 tag 公式定码；
 *   元数据行仅作一致性校验——v7.10.0 统一版本规则）
 * - 上传 APK 资产 + SHA-256 公布
 * - 核验资产 digest 与本地一致 + latest 指向 v7.10.0 + v7.9.9 完好
 * - 同步 GitHub 仓库描述至 v7.10.0 口径（首页版本显示介绍）
 * - 硬闸：本地 APK versionCode 必须 = 80000（绝不发布名码不对应的 APK）
 */
import { readFileSync } from "fs";
import { execSync } from "child_process";
import { createHash } from "crypto";
import { getGhToken } from "./gh-token.mjs";

const REPO_ROOT = "/home/z/skip-ad-repo";
const TOKEN = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const APK = `${REPO_ROOT}/android-app/dist/adshield-v7.10.0.apk`;
const TAG = "v7.10.0";
const EXPECT_VC = 80000;
const EXPECT_VN = "7.10.0";
const V799_RELEASE_ID = 454424925; // v7.9.9 正式 Release（完好性对照——绝不触碰）
const NEW_DESC =
  "安卓开屏广告秒跳引擎（无障碍代点「跳过」）· v7.10.0：日志上传零次数限制 + 更新检查异步化（弱网页面冻结根治）+ 开屏提醒解耦 + 统一版本规则 · 7305 JVM 断言全绿 · APK 874KB 零凭据 · 更新链 SHA-256 校验 · 233 款应用兼容目录（使用前请阅 README 免责声明）";

if (!TOKEN) {
  console.error(
    "✗ 缺少 GitHub Token：请设置 SKIPAD_GH_TOKEN/GH_TOKEN/GITHUB_TOKEN 环境变量，" +
      "或在仓库根放置未入库文件 local-gh-token（.gitignore local-* 规则放行——绝不入库）",
  );
  process.exit(1);
}

const H = {
  "Authorization": `token ${TOKEN}`,
  "Accept": "application/vnd.github+json",
  "User-Agent": "skip-ad-release",
};

async function gh(path, init) {
  const r = await fetch(`https://api.github.com${path}`, {
    ...init,
    headers: { ...H, ...(init && init.headers) },
  });
  return r;
}

/* ── 硬闸 1：构建产物真实版本核验（aapt）──绝不发布名码不对应的 APK */
const badging = execSync(
  `/home/z/android-tools/bt/android-14/aapt dump badging ${APK} 2>/dev/null | head -1`,
).toString();
const vc = /versionCode='(\d+)'/.exec(badging)?.[1];
const vn = /versionName='([^']+)'/.exec(badging)?.[1];
console.log(`[硬闸1] APK versionName=${vn} versionCode=${vc}`);
if (vn !== EXPECT_VN || Number(vc) !== EXPECT_VC) {
  console.error(`✗ 拒绝发布：期望 versionName=${EXPECT_VN}/versionCode=${EXPECT_VC}，实际 ${vn}/${vc}`);
  process.exit(1);
}

const apk = readFileSync(APK);
const sha256 = createHash("sha256").update(apk).digest("hex");
console.log(`[硬闸1] APK ${apk.length.toLocaleString("en-US")} 字节 · sha256=${sha256}`);

/* ── 硬闸 2：推送状态核验——Release 只能在 main 已推送后创建（tag 目标须在远端在场） */
execSync("git fetch origin", { cwd: REPO_ROOT });
const headSha = execSync("git rev-parse HEAD", { cwd: REPO_ROOT }).toString().trim();
const originMain = execSync("git rev-parse origin/main", { cwd: REPO_ROOT }).toString().trim();
const aheadBehind = execSync("git status -sb", { cwd: REPO_ROOT }).toString().trim();
console.log(`[硬闸2] HEAD=${headSha}`);
console.log(`[硬闸2] origin/main=${originMain}`);
console.log(`[硬闸2] ${aheadBehind}`);
if (headSha !== originMain) {
  console.error("✗ 拒绝发布：本地 HEAD 与 origin/main 不一致——先完成 git push origin main 再发布");
  process.exit(1);
}

const body = `versionCode: 80000

# 开屏卫士 v7.10.0

上传次数限制彻底移除 + 更新检查异步化 + 开屏提醒解耦 + 统一版本规则（82300/plus 旧兼容链清理）。

> 诚实口径：本版源码为灾后可验证重建——原 v7.10.0 工作区提交丢失，本轮从 v7.9.9 基线 + 已分发 v7.10.0 APK dex 符号核验重建并全量回归（Java 7305 + JS 204 + manifest 47 断言全绿，非字节级复原）；真机 E2E 待用户实测反馈。

## 本次更新

1. **日志上传次数限制彻底移除**：自动上传不再有 24h/3 成功额度（仅受心跳间隔 8h 与「有新日志才传」双闸约束）；手动上传零次数/额度/冷却门槛，随时可传、短时连传多批全部放行（batchId 幂等保持——绝不二建 Issue）
2. **更新检查异步化**：新增 updateCheckAsync 异步桥——JS 主线程零网络等待（旧同步桥在弱网下页面冻结最长 40s 的根因根治）；JS 单飞 + 过期序号丢弃 + 90 秒回调兜底三重防线，手动检查绝不永久卡「检查中…」
3. **开屏提醒与更新检查彻底解耦**：反馈提醒独立 3 秒定时评估——网络再慢（五源链预算 40s）也最多 3s 看到提醒；24h 节流/5min 防重建/更新优先语义保持
4. **统一版本判定规则**：全项目统一 remoteVersionCode > currentVersionCode——versionName 仅展示、绝不作为更新判断依据；Release 正文 versionCode 元数据行回退与 82300/plus 旧兼容链彻底清理（客户端/JS/生成器三面同步）
5. **引擎零改动**：广告识别/跳过/无障碍扫描核心零改动——本版全部为上传与更新链路可靠性优化

## 下载

- 官方 APK：adshield-v7.10.0.apk（本页资产 · GitHub Releases 官方源 · HTTPS）
- 文件大小：${apk.length.toLocaleString("en-US")} 字节
- SHA-256：\`${sha256}\`

## 安装说明

- 已安装 v7.9.x 及更早正式版本：直接安装本 APK 覆盖升级（versionCode 80000 > 79900 及以下全部，签名一致，无障碍授权/学习数据/已导入订阅全部保留）
- 已安装 v7.9.6plus（versionCode 82300）：Android 判定 80000 为降级**无法覆盖安装**——可继续使用当前版本（功能完整），或手动卸载后安装本版（卸载会清学习规则/战绩/日志），或等待未来 versionCode > 82300 的正式版本无缝升级
- 新安装：直接安装即可（Android 8+ 首次安装需允许「安装未知应用」授权）

## 版本信息

- versionName：7.10.0
- versionCode：80000（major*10000 + minor*1000 + patch*100）
- 签名证书 SHA-256：05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79（与历史正式版本一致）
- APK 零凭据（无 GitHub Token/私钥/Secret——写凭据全部只在服务端）`;

async function main() {
  console.log(`[tag] ${TAG} 将指向 main HEAD ${headSha}`);

  // 1. 幂等防重：tag/Release 已存在则拒绝（绝不覆盖历史 Release）
  console.log("── 1/6 防重核验 ──");
  const existRes = await gh(`/repos/${REPO}/releases/tags/${TAG}`);
  if (existRes.status !== 404) {
    const ex = await existRes.json().catch(() => ({}));
    console.error(`✗ 拒绝发布：${TAG} Release 已存在（id=${ex.id}）——绝不覆盖，人工核查后再处置`);
    process.exit(1);
  }
  console.log(`✓ ${TAG} 不存在（干净发布位）`);

  // 2. 创建正式 Release
  console.log("── 2/6 创建正式 Release ──");
  const createRes = await gh(`/repos/${REPO}/releases`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: TAG,
      target_commitish: headSha,
      name: "Skip-Ad v7.10.0",
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

  // 3. tag 指向核验（API 创建的 tag 是否落在目标 commit）
  console.log("── 3/6 tag 指向核验 ──");
  const refRes = await gh(`/repos/${REPO}/git/ref/tags/${TAG}`);
  const ref = await refRes.json();
  let tagSha = ref.object?.sha;
  if (ref.object?.type === "tag") {
    const ann = await (await gh(`/repos/${REPO}/git/tags/${tagSha}`)).json();
    tagSha = ann.object?.sha;
  }
  console.log(`tag ${TAG} → ${tagSha}（期望 ${headSha}）${tagSha === headSha ? "✓" : "✗"}`);
  if (tagSha !== headSha) {
    console.error("✗ tag 指向与 main HEAD 不一致");
    process.exit(1);
  }

  // 4. 上传 APK 资产
  console.log("── 4/6 上传 APK 资产 ──");
  const upRes = await fetch(
    `https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.10.0.apk`,
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

  // 5. 核验（latest 指向 + 资产 digest/size + v7.9.9 完好）
  console.log("── 5/6 核验 ──");
  const latestRes = await gh(`/repos/${REPO}/releases/latest`);
  const latest = await latestRes.json();
  const a = (latest.assets || []).find((x) => x.name === "adshield-v7.10.0.apk");
  console.log(`latest tag=${latest.tag_name} draft=${latest.draft} prerelease=${latest.prerelease}`);
  console.log(`asset digest=${a?.digest} size=${a?.size}`);
  const v799Res = await gh(`/repos/${REPO}/releases/tags/v7.9.9`);
  const v799 = await v799Res.json();
  console.log(
    `v7.9.9 完好: id=${v799.id} draft=${v799.draft} prerelease=${v799.prerelease} assets=${(v799.assets || []).length}`,
  );
  const verifyOk =
    latest.tag_name === TAG && !latest.draft && !latest.prerelease
    && a && a.digest === `sha256:${sha256}` && a.size === apk.length
    && v799.id === V799_RELEASE_ID;
  if (!verifyOk) {
    console.error("✗ 核验失败");
    process.exit(1);
  }
  console.log("✓ 核验通过：正式 Release + 资产 digest/size 与本地一致 + v7.9.9 完好");

  // 6. 仓库描述同步（GitHub 首页版本显示介绍）
  console.log("── 6/6 仓库描述同步 ──");
  const descRes = await gh(`/repos/${REPO}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ description: NEW_DESC }),
  });
  const dd = await descRes.json().catch(() => ({}));
  if (!descRes.ok) {
    console.error("描述更新失败:", descRes.status, dd.message);
    process.exit(1);
  }
  console.log(`✓ 仓库描述已更新为 v7.10.0 口径（${NEW_DESC.length} 字符）`);

  console.log("\n════ 发布完成 ════");
  console.log(`Release: ${rel.html_url}`);
  console.log(`APK:     ${asset.browser_download_url}`);
  console.log(`SHA-256: ${sha256}`);
}

main();
