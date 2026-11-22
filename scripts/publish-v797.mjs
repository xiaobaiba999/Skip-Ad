#!/usr/bin/env bun
/**
 * v7.9.7 正式 Release 发布脚本（版本体系收口轮 · 一次性）。
 * - 前置：android-app/build.sh 79700 7.9.7 dist/adshield-v7.9.7.apk 已构建并通过
 *   aapt（versionName=7.9.7/versionCode=79700）+ apksigner（历史签名）核验
 * - 创建正式 Release（非草稿/非预发布，tag v7.9.7 → 当前 main HEAD）
 * - body 顶部含机器可读 versionCode: 79700 元数据行（客户端 versionCode 主判数据源）
 * - 上传 APK 资产 + SHA-256 公布
 * - 核验资产 digest 与本地一致 + latest 指向 v7.9.7 + v7.9.6 完好
 * - 硬闸：本地 APK versionCode 必须 = 79700（绝不发布名码不对应的 APK）
 */
import { readFileSync } from "fs";
import { execSync } from "child_process";
import { createHash } from "crypto";

const TOKEN = readFileSync("/home/z/gh-push/token", "utf8").trim();
const REPO = "xiaobaiba999/Skip-Ad";
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v7.9.7.apk";
const TAG = "v7.9.7";
const EXPECT_VC = 79700;
const EXPECT_VN = "7.9.7";
const H = {
  "Authorization": `token ${TOKEN}`,
  "Accept": "application/vnd.github+json",
  "User-Agent": "skip-ad-release",
};

async function gh(path, init) {
  const r = await fetch(`https://api.github.com${path}`, { ...init, headers: { ...H, ...(init && init.headers) } });
  return r;
}

/* ── 硬闸：构建产物真实版本核验（aapt）──绝不发布名码不对应的 APK */
const badging = execSync(
  `/home/z/android-tools/bt/android-14/aapt dump badging ${APK} 2>/dev/null | head -1`,
).toString();
const vc = /versionCode='(\d+)'/.exec(badging)?.[1];
const vn = /versionName='([^']+)'/.exec(badging)?.[1];
console.log(`[硬闸] APK versionName=${vn} versionCode=${vc}`);
if (vn !== EXPECT_VN || Number(vc) !== EXPECT_VC) {
  console.error(`✗ 拒绝发布：期望 versionName=${EXPECT_VN}/versionCode=${EXPECT_VC}，实际 ${vn}/${vc}`);
  process.exit(1);
}

const apk = readFileSync(APK);
const sha256 = createHash("sha256").update(apk).digest("hex");
console.log(`[硬闸] APK ${apk.length} 字节 · sha256=${sha256}`);

const body = `versionCode: 79700

# 开屏卫士 v7.9.7

版本体系收口 + Bug 反馈一次性提示。从本版起 versionName = Git Tag = Release = APK versionName，versionCode 严格遵循 v7.1.0 起标准公式 major*10000+minor*1000+patch*100（7.9.7 → 79700），彻底告别 7.9.6plus→82300 的名码不对应。

## 本次更新

1. 版本体系收口：versionName 7.9.7 · versionCode 79700 · Tag/Release/APK 同名同码；versionCode 规则经 dist 全量 51 个历史 APK aapt 实测核验（v7.1.0 起 major*10000+minor*1000+patch*100），以后每一版都如此
2. Bug 反馈提示：新版本首次进入一次性轻提示「发现 Bug 或使用不舒服？可到 我的→引擎日志→一键上传 反馈」（广告没跳过/跳过不准确/App 卡顿/误跳/其他异常）；「去上传日志」直达入口（仅导航，绝不代替你点击上传）或「知道了」关闭；只提醒、绝不自动上传、不采集任何新数据、同版本不弹第二次
3. 更新检测保持 versionCode 主判：远端 versionCode 高于本地才提示（79700 = 79700 不自误报）；Draft/Pre-release 不作正式更新推送；网络异常静默进入 App
4. 引擎与日志系统零改动：广告跳过核心/无障碍扫描/日志采集/手动上传/自动上传额度（24 小时最多成功 3 次·只记服务端确认·失败不耗额度）全部保持 v7.9.6plus 实测形态
5. 旧版本升级：v7.9.6/7.9.5/7.9.0/7.8.0/7.6.1 检查更新即可发现本版并覆盖升级（79700 > 各旧版 versionCode，签名一致数据保留）

## 下载

- 官方 APK：adshield-v7.9.7.apk（本页资产 · GitHub Releases 官方源 · HTTPS）
- 文件大小：${apk.length.toLocaleString("en-US")} 字节
- SHA-256：\`${sha256}\`

## 安装说明

- 已安装 v7.9.6 及更早正式版本：直接安装本 APK 覆盖升级（versionCode 79700 > 79600 及以下全部，签名一致，数据保留）
- 已安装 v7.9.6plus（versionCode 82300）：Android 判定 79700 为降级**无法覆盖安装**——可继续使用当前版本（无功能损失），或手动卸载后安装本版（卸载会清学习规则/战绩/日志），或等待未来 versionCode > 82300 的正式版本无缝升级
- 新安装：直接安装即可（Android 8+ 首次安装需允许「安装未知应用」授权）

## 版本信息

- versionName：7.9.7
- versionCode：79700（major*10000 + minor*1000 + patch*100）
- 签名证书 SHA-256：05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79（与历史正式版本一致）
- APK 零凭据（无 GitHub Token/私钥/Secret——写凭据全部只在服务端）`;

async function main() {
  // 0. tag 指向核验：v7.9.7 必须指向当前 main HEAD（本轮 commit）
  const headSha = execSync("git rev-parse HEAD", { cwd: "/home/z/skip-ad-repo" }).toString().trim();
  console.log(`[tag] v7.9.7 将指向 main HEAD ${headSha}`);

  // 1. 创建正式 Release
  console.log("── 1/4 创建正式 Release ──");
  const createRes = await gh(`/repos/${REPO}/releases`, {
    method: "POST",
    body: JSON.stringify({
      tag_name: TAG,
      target_commitish: headSha,
      name: "v7.9.7",
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

  // 2. tag 指向核验（API 创建的 tag 是否落在目标 commit）
  console.log("── 2/4 tag 指向核验 ──");
  const refRes = await gh(`/repos/${REPO}/git/ref/tags/${TAG}`);
  const ref = await refRes.json();
  let tagSha = ref.object?.sha;
  if (ref.object?.type === "tag") {
    const ann = await (await gh(`/repos/${REPO}/git/tags/${tagSha}`)).json();
    tagSha = ann.object?.sha;
  }
  console.log(`tag ${TAG} → ${tagSha}（期望 ${headSha}）${tagSha === headSha ? "✓" : "✗"}`);
  if (tagSha !== headSha) { console.error("✗ tag 指向与 main HEAD 不一致"); process.exit(1); }

  // 3. 上传 APK 资产
  console.log("── 3/4 上传 APK 资产 ──");
  const upRes = await fetch(
    `https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.9.7.apk`,
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

  // 4. 核验（重新 GET latest + v7.9.6 完好性）
  console.log("── 4/4 核验 ──");
  const latestRes = await gh(`/repos/${REPO}/releases/latest`);
  const latest = await latestRes.json();
  const a = (latest.assets || []).find((x) => x.name === "adshield-v7.9.7.apk");
  console.log(`latest tag=${latest.tag_name} draft=${latest.draft} prerelease=${latest.prerelease}`);
  console.log(`asset digest=${a?.digest} size=${a?.size}`);
  const v796Res = await gh(`/repos/${REPO}/releases/tags/v7.9.6`);
  const v796 = await v796Res.json();
  console.log(`v7.9.6 完好: id=${v796.id} draft=${v796.draft} prerelease=${v796.prerelease} assets=${(v796.assets || []).length}`);
  const ok = latest.tag_name === TAG && !latest.draft && !latest.prerelease
    && a && a.digest === `sha256:${sha256}` && a.size === apk.length
    && v796.id === 452768731;
  console.log(ok ? "✓ 核验通过：正式 Release + 资产 digest/size 与本地一致 + v7.9.6 完好" : "✗ 核验失败");
  process.exit(ok ? 0 : 1);
}

main();
