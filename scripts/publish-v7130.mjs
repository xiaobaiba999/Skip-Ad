#!/usr/bin/env bun
/**
 * v7.13.0 正式 Release 发布脚本。
 * - 凭据源：gh-token.mjs（SKIPAD_GH_TOKEN / GH_TOKEN / GITHUB_TOKEN 环境变量
 *   → 仓库根未入库文件 local-gh-token——绝不入库绝不打印）
 * - 前置：main 已推送到 origin（HEAD == origin/main，ahead 0——本脚本硬闸校验）
 * - 前置：android-app/dist/adshield-v7.13.0.apk 已构建并通过
 *   aapt（versionName=7.13.0/versionCode=83000）+ apksigner（历史签名）核验
 * - 前置：dist/adshield-latest.apk 别名已与版本 APK 同字节（检查失败兜底直链）
 * - 创建正式 Release（非草稿/非预发布，tag v7.13.0 → 当前 main HEAD）
 * - body 顶部含机器可读 versionCode: 83000 元数据行（三段式 tag 公式定码）
 * - 上传 APK 资产 + SHA-256 公布
 * - 核验资产 digest 与本地一致 + latest 指向 v7.13.0 + v7.12.0 完好
 * - jsDelivr CDN 兜底链 purge（latest.json + 版本 APK + latest 别名）——
 *   v7.13.0 诚实口径（本轮 purge 巡更定案）：purge 只清 CF/FY 边缘、
 *   源站 @main 分支解析缓存 TTL ≤12h 不受 purge 影响——提交 purge 后
 *   单轮快核验，未翻转者如实报告（≤12h 自愈，不发工作流伪绿勾）
 * - 同步 GitHub 仓库描述至 v7.13.0 口径（首页版本显示介绍）
 * - 硬闸：本地 APK versionCode 必须 = 83000（绝不发布名码不对应的 APK）
 */
import { readFileSync } from "fs";
import { execSync } from "child_process";
import { createHash } from "crypto";
import { getGhToken } from "./gh-token.mjs";

const REPO_ROOT = "/home/z/skip-ad-repo";
const TOKEN = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const APK = `${REPO_ROOT}/android-app/dist/adshield-v7.13.0.apk`;
const TAG = "v7.13.0";
const EXPECT_VC = 83000;
const EXPECT_VN = "7.13.0";
const V7120_RELEASE_ID = 455227912; // v7.12.0 正式 Release（完好性对照——绝不触碰）
const NEW_DESC =
  "安卓开屏广告秒跳引擎（无障碍代点「跳过」）· v7.13.0：开屏提醒跨升级修复（进入必弹根治）+ 切回即提醒 + 上传快失败（6s×四试）+ 网络恢复自动补传（切网零手动重试）· JVM 7345 + JS 250 断言全绿（行为级——真机 E2E 待用户实测）· APK 880KB 零凭据 · 更新链 SHA-256 校验（使用前请阅 README 免责声明）";

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

const body = `versionCode: 83000

# 开屏卫士 v7.13.0

开屏提醒跨升级修复 + 切回即提醒 + 上传快失败与网络恢复自动补传。

> 本轮测试口径：JVM 42 套件 7345 断言全绿 + JS 行为 7 文件 250 断言全绿。诚实口径：真机/模拟器 E2E NOT_RUN——本轮全部为 JVM/JS 行为级验证（真实代码路径语义级）+ 代码级性能/能耗审计，待用户实测反馈。

## 本次更新

1. **开屏提醒跨升级修复（「进入 app 没有弹出提醒」根因）**：旧版本点过「24 小时内不再提示」的时间戳在覆盖升级后仍生效——旧节流吞掉升级后首次进入的提醒；现在提醒卡记录当前版本，**版本变化即重新提醒，升级后第一次进入必弹**（知道了 / 24 小时不再提醒 / 去上传日志三按钮保持）
2. **切回即提醒**：从后台切回前台也是「进入 App」——冷启动与切回两种场景都评估提醒（60 秒短窗防连弹，24 小时不再提示仍受尊重）
3. **上传快失败（「又慢又成功不了」）**：连接超时 10 秒→6 秒、自动重试三次→四次（退避 0.5/1.2/2.4 秒）——失败等待缩短、间歇路由下成功率提升
4. **网络恢复自动补传**：手动上传失败后 30 分钟内切 Wi-Fi/移动网络、或网络恢复时，自动补传待传日志（无需手动重试；开启自动上传后长期有效，每 8 小时心跳检查保持）

## 下载

- 官方 APK：adshield-v7.13.0.apk（本页资产 · GitHub Releases 官方源 · HTTPS）
- CDN 兜底直链：https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.13.0.apk（与 Release 资产同字节同源 · SHA-256 相同）
- 文件大小：${apk.length.toLocaleString("en-US")} 字节
- SHA-256：\`${sha256}\`

## 安装说明

- 已安装 v7.12.0 及更早正式版本（versionCode ≤ 82000）：直接安装本 APK 覆盖升级（versionCode 83000，签名一致，无障碍授权/学习数据/已导入订阅全部保留）
- 已安装 v7.9.6plus（versionCode 82300）：**本版 versionCode 83000 > 82300——可无缝覆盖升级**（此前 v7.12.0 因 82000 < 82300 无法覆盖安装的遗留局面解除）
- 新安装：直接安装即可（Android 8+ 首次安装需允许「安装未知应用」授权）

## 版本信息

- versionName：7.13.0
- versionCode：83000（major*10000 + minor*1000 + patch*100）
- 签名证书 SHA-256：05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79（与历史正式版本一致）
- APK 零凭据（无 GitHub Token/私钥/Secret——写凭据全部只在服务端）`

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
      name: "Skip-Ad v7.13.0",
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
    `https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.13.0.apk`,
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

  // 5. 核验（latest 指向 + 资产 digest/size + v7.10.0 完好）
  console.log("── 5/7 核验 ──");
  const latestRes = await gh(`/repos/${REPO}/releases/latest`);
  const latest = await latestRes.json();
  const a = (latest.assets || []).find((x) => x.name === "adshield-v7.13.0.apk");
  console.log(`latest tag=${latest.tag_name} draft=${latest.draft} prerelease=${latest.prerelease}`);
  console.log(`asset digest=${a?.digest} size=${a?.size}`);
  const v7120Res = await gh(`/repos/${REPO}/releases/tags/v7.12.0`);
  const v7120 = await v7120Res.json();
  console.log(
    `v7.12.0 完好: id=${v7120.id} draft=${v7120.draft} prerelease=${v7120.prerelease} assets=${(v7120.assets || []).length}`,
  );
  const verifyOk =
    latest.tag_name === TAG && !latest.draft && !latest.prerelease
    && a && a.digest === `sha256:${sha256}` && a.size === apk.length
    && v7120.id === V7120_RELEASE_ID;
  if (!verifyOk) {
    console.error("✗ 核验失败");
    process.exit(1);
  }
  console.log("✓ 核验通过：正式 Release + 资产 digest/size 与本地一致 + v7.12.0 完好");

  // 6. jsDelivr CDN 兜底链 purge + 生效核验（工作流 purge 升级未落库——本轮代执行）
  console.log("── 6/7 jsDelivr CDN 兜底链 purge ──");
  const cdnPaths = [
    "update/latest.json",
    "android-app/dist/adshield-v7.13.0.apk",
    "android-app/dist/adshield-latest.apk",
  ];
  for (const p of cdnPaths) {
    try {
      const pr = await fetch(`https://purge.jsdelivr.net/gh/${REPO}@main/${p}`, {
        method: "GET",
      });
      console.log(`  purge ${p} → HTTP ${pr.status}${pr.ok ? "（已提交刷新）" : "（未确认——12h 自然过期）"}`);
    } catch (e) {
      console.log(`  purge ${p} 网络异常（12h 自然过期——raw 主源不受影响）`);
    }
  }
  // 生效核验：latest.json 看 versionCode；APK 看 sha256（重试至多 9 次×5s）
  let cdnOk = 0;
  for (const p of cdnPaths) {
    for (let i = 1; i <= 3; i++) {
      try {
        const r = await fetch(`https://gcore.jsdelivr.net/gh/${REPO}@main/${p}`);
        if (r.ok) {
          const buf = Buffer.from(await r.arrayBuffer());
          const got = createHash("sha256").update(buf).digest("hex");
          const pass = p.endsWith(".json")
            ? JSON.parse(buf.toString("utf-8")).versionCode === EXPECT_VC
            : got === sha256;
          if (pass) {
            console.log(`  ✓ gcore.jsdelivr.net/${p} 已生效（${buf.length} 字节）`);
            cdnOk++;
            break;
          }
          console.log(`  第 ${i} 次：${p} 内容未更新（sha=${got.slice(0, 12)}…）——源站 @main 分支缓存陈旧窗口内，≤12h 自愈`);
        } else {
          console.log(`  第 ${i} 次：${p} HTTP ${r.status}——5s 后重试`);
        }
      } catch {
        console.log(`  第 ${i} 次：${p} 网络异常——5s 后重试`);
      }
      await new Promise((r) => setTimeout(r, 5000));
    }
  }
  console.log(
    cdnOk === 3
      ? "✓ CDN 兜底链三路全部生效（latest.json 83000 + 版本 APK + latest 别名）"
      : `⚠ CDN 生效 ${cdnOk}/3——未生效者源站 @main 缓存 ≤12h 自愈（诚实口径：purge 只清 CF/FY 边缘；raw 主源与 GitHub Release 已即时生效；版本化 APK 新文件键即时新鲜）`,
  );

  // 7. 仓库描述同步（GitHub 首页版本显示介绍）
  console.log("── 7/7 仓库描述同步 ──");
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
  console.log(`✓ 仓库描述已更新为 v7.13.0 口径（${NEW_DESC.length} 字符）`);

  console.log("\n════ 发布完成 ════");
  console.log(`Release: ${rel.html_url}`);
  console.log(`APK:     ${asset.browser_download_url}`);
  console.log(`SHA-256: ${sha256}`);
}

main();
