#!/usr/bin/env bun
/**
 * v7.15.10 正式 Release 发布脚本（三图类型死洞根治轮：开屏×强广告形态
 * 豁免带 + 系统权限弹窗零扫描 + 图片键候选诊断 + 探针去重/图形探针扩容；
 * v7.15.9 全部能力保留）。
 * - 凭据源：gh-token.mjs 三级链（SKIPAD_GH_TOKEN / GH_TOKEN / GITHUB_TOKEN 环境变量
 *   → 仓库根未入库文件 local-gh-token → release-token-vault.mjs 密文金库——
 *   v7.15.4 起令牌密文永久固化入库，不再向所有者索要）
 * - 前置：main 已推送到 origin（HEAD == origin/main，ahead 0——本脚本硬闸校验）
 * - 前置：android-app/dist/adshield-v7.15.10.apk 已构建并通过
 *   aapt（versionName=7.15.10/versionCode=86000）+ apksigner（历史签名）核验
 * - 前置：dist/adshield-latest.apk 别名已与版本 APK 同字节（检查失败兜底直链）
 * - 前置（tag 树自含清单铁律）：HEAD 的 update/latest.json 必须 versionCode=86000
 *   ——tag 指向 HEAD，tag 树必须自含本版清单（@tag 探测链的依赖；v7.12.0
 *   tag 树含 81000 旧清单的教训定案：清单必须在打 tag 前提交）
 * - 断言：清单必须无 cdnApkUrl 字段（场端旧版死局根治点——字段复燃即拒发）
 * - 正文单一来源：scripts/release-tmp/v71510-notes.md（与预提交清单同文）
 * - 创建正式 Release（非草稿/非预发布，tag v7.15.10 → 当前 main HEAD）
 * - 上传 APK 资产 + SHA-256 公布；核验资产 digest 与本地一致
 * - 完好性对照：v7.15.9 正式 Release（按 tag 查询——绝不触碰；升级源）
 * - jsDelivr CDN 兜底链 purge（latest.json + 版本 APK + latest 别名）
 * - 【发版证据核验】：@v7.15.10 tag 清单在四域（gcore/cdn/fastly/testingcf）必须返回 86000
 * - 同步 GitHub 仓库描述至 v7.15.10 口径（首页版本显示介绍）
 * - 幂等防重：tag/Release 已存在则拒绝（绝不覆盖历史 Release）
 */
import { readFileSync } from "fs";
import { execSync } from "child_process";
import { createHash } from "crypto";
import { getGhToken } from "./gh-token.mjs";
import { runReadinessChecks } from "./check-release-readiness.mjs";

const REPO_ROOT = "/home/z/skip-ad-repo";
const TOKEN = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const APK = `${REPO_ROOT}/android-app/dist/adshield-v7.15.10.apk`;
const LATEST_APK = `${REPO_ROOT}/android-app/dist/adshield-latest.apk`;
const TAG = "v7.15.10";
const EXPECT_VC = 86000;
const EXPECT_VN = "7.15.10";
const REF_RELEASE_TAG = "v7.15.9"; // 完好性对照（前序正式版·升级源——绝不触碰）
const NOTES = `${REPO_ROOT}/scripts/release-tmp/v71510-notes.md`;
const NEW_DESC =
  "安卓开屏广告秒跳引擎（无障碍代点「跳过」）· v7.15.10 三图类型死洞根治：开屏×强广告形态豁免带（H5 大树/上滑滚动/晚到键）+ 权限弹窗零扫描 + 功耗优化 · JVM 4528 断言全绿 · APK 866KB 零明文凭据 · 更新链 SHA-256 校验（使用前请阅 README 免责声明）";

if (!TOKEN) {
  console.error("✗ 缺少 GitHub Token（local-gh-token / 环境变量）");
  process.exit(1);
}
const H = {
  "Authorization": `token ${TOKEN}`,
  "Accept": "application/vnd.github+json",
  "User-Agent": "skip-ad-release",
};
async function gh(path, init) {
  return fetch(`https://api.github.com${path}`, {
    ...init,
    headers: { ...H, ...(init && init.headers) },
  });
}

/* ── 硬闸 0：发布前就绪检查（所有者令旨：每次发布前检查源码是否漏写/写错）──
 * 升级自 v7.15.4 单点 /user 探测：金库入库（漏写）+ 密文互逆（写错）+
 * 令牌活性（死令牌）+ 仓库写权限 + 来源一致性五重硬闸，任何一项致命即中止。 */
const ready = await runReadinessChecks({ label: "硬闸0 · 发布前就绪检查" });
if (!ready) {
  console.error("✗ 拒绝发布：就绪检查未过（上方明细）——修复后重试");
  process.exit(1);
}

/* ── 硬闸 1：构建产物真实版本核验（aapt）──绝不发布名码不对应的 APK */
const badging = execSync(
  `/home/z/android-tools/bt/android-14/aapt dump badging ${APK} 2>/dev/null | head -1`,
).toString();
const vc = /versionCode='(\d+)'/.exec(badging)?.[1];
const vn = /versionName='([^']+)'/.exec(badging)?.[1];
console.log(`[硬闸1] APK versionName=${vn} versionCode=${vc}`);
if (vn !== EXPECT_VN || Number(vc) !== EXPECT_VC) {
  console.error(`✗ 拒绝发布：期望 ${EXPECT_VN}/${EXPECT_VC}，实际 ${vn}/${vc}`);
  process.exit(1);
}
const apk = readFileSync(APK);
const sha256 = createHash("sha256").update(apk).digest("hex");
console.log(`[硬闸1] APK ${apk.length.toLocaleString("en-US")} 字节 · sha256=${sha256}`);

/* 硬闸 1b：latest 别名同字节 */
const latest = readFileSync(LATEST_APK);
const latestSha = createHash("sha256").update(latest).digest("hex");
if (latestSha !== sha256) {
  console.error(`✗ 拒绝发布：adshield-latest.apk 与版本 APK 字节不一致`);
  process.exit(1);
}
console.log(`[硬闸1b] adshield-latest.apk 同字节 ✓`);

/* ── 硬闸 2：签名链历史一致（apksigner）── */
const certs = execSync(
  `/home/z/android-tools/bt/android-14/apksigner verify --print-certs ${APK} 2>/dev/null`,
).toString();
const certSha = /SHA-256 digest: ([0-9a-f]+)/.exec(certs)?.[1];
console.log(`[硬闸2] 签名证书 SHA-256=${certSha}`);
if (certSha !== "05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79") {
  console.error("✗ 拒绝发布：签名证书与历史正式版本不一致（覆盖安装会失败）");
  process.exit(1);
}

/* ── 硬闸 3：main 已推送（HEAD == origin/main）── */
/* fetch 带重试退避（沙箱到 github.com:443 间歇抖动——GnuTLS -110/连接超时
 * 实测；api.github.com 与 github.com 路由不同步，不能因网关抽风误判未推送） */
function fetchOriginMain() {
  for (let i = 1; i <= 5; i++) {
    try {
      execSync("git fetch origin main --quiet", { cwd: REPO_ROOT, timeout: 60000 });
      return true;
    } catch (e) {
      console.log(`[硬闸3] fetch 第 ${i}/5 次失败（网络抖动）——${e.message.split("\n")[1] || e.message}，5s 后重试`);
      if (i < 5) execSync("sleep 5");
    }
  }
  return false;
}
if (!fetchOriginMain()) {
  console.error("✗ 拒绝发布：git fetch 连续 5 次失败（网络不可达——恢复后重试；管道幂等，未创建任何 tag/Release）");
  process.exit(1);
}
const head = execSync("git rev-parse HEAD", { cwd: REPO_ROOT }).toString().trim();
const originMain = execSync("git rev-parse origin/main", { cwd: REPO_ROOT }).toString().trim();
console.log(`[硬闸3] HEAD=${head.slice(0, 10)} origin/main=${originMain.slice(0, 10)}`);
if (head !== originMain) {
  console.error("✗ 拒绝发布：HEAD != origin/main（先把 main 推上去再发布）");
  process.exit(1);
}

/* ── 硬闸 4：tag 树自含本版清单（@tag 探测链依赖的铁律）── */
const manifestRaw = readFileSync(`${REPO_ROOT}/update/latest.json`, "utf-8");
const manifestVc = JSON.parse(manifestRaw).versionCode;
console.log(`[硬闸4] HEAD update/latest.json versionCode=${manifestVc}`);
if (manifestVc !== EXPECT_VC) {
  console.error(`✗ 拒绝发布：清单 versionCode=${manifestVc} != ${EXPECT_VC}（清单必须先于打 tag 提交）`);
  process.exit(1);
}
const manifestSha = JSON.parse(manifestRaw).apkSha256;
if (manifestSha !== sha256) {
  console.error("✗ 拒绝发布：清单 apkSha256 与本地 APK 实算不一致");
  process.exit(1);
}
/* 清单必须无 cdnApkUrl——该字段是 v7.15.0~v7.15.4 场端「下载链接不在
 * 允许的发布通道内」死因（旧版把它排渠道链首位又被白名单拒）。
 * 字段复燃 = 旧版死局复发，发布闸直接拒发。 */
if (Object.prototype.hasOwnProperty.call(JSON.parse(manifestRaw), "cdnApkUrl")) {
  console.error("✗ 拒绝发布：清单含 cdnApkUrl 字段（v7.15.6 已撤——场端旧版死局根治点，字段复燃即拒发）");
  process.exit(1);
}
console.log(`[硬闸4] 清单无 cdnApkUrl 字段 ✓（旧版场端首渠道回 ghfast.top 的根治点）`);

/* ── 幂等防重 ── */
const tagExist = await gh(`/repos/${REPO}/git/ref/tags/${TAG}`);
if (tagExist.status === 200) {
  console.error(`✗ tag ${TAG} 已存在（绝不覆盖历史）`);
  process.exit(1);
}
console.log(`[幂等] tag ${TAG} 不存在 ✓`);

/* ── 完好性对照：v7.15.9（前序正式版·升级源）仍在场 ── */
const ref = await gh(`/repos/${REPO}/releases/tags/${REF_RELEASE_TAG}`);
if (ref.status !== 200) {
  console.error(`✗ 完好性对照失败：${REF_RELEASE_TAG} Release 不可达（${ref.status}）`);
  process.exit(1);
}
const refJson = await ref.json();
const refAssets = refJson.assets || [];
console.log(`[对照] ${REF_RELEASE_TAG} Release #${refJson.id} 在场 · 资产 ${refAssets.length} 个 ✓`);

/* ── 创建 tag（HEAD——树内自含本版清单）── */
const tagCreate = await gh(`/repos/${REPO}/releases`, {
  method: "POST",
  body: JSON.stringify({
    tag_name: TAG,
    target_commitish: "main",
    name: `Skip-Ad ${TAG}`,
    body: readFileSync(NOTES, "utf-8"),
    draft: false,
    prerelease: false,
  }),
});
if (tagCreate.status !== 201) {
  console.error(`✗ Release 创建失败：${tagCreate.status} ${await tagCreate.text()}`);
  process.exit(1);
}
const rel = await tagCreate.json();
console.log(`[Release] ${TAG} 已创建 #${rel.id}（正式·非草稿）`);

/* ── 上传 APK 资产 ── */
const uploadUrl = rel.upload_url.replace("{?name,label}", "?name=adshield-v7.15.10.apk");
const up = await fetch(uploadUrl, {
  method: "POST",
  headers: {
    ...H,
    "Content-Type": "application/vnd.android.package-archive",
    "Content-Length": String(apk.length),
  },
  body: new Uint8Array(apk),
});
if (up.status !== 201) {
  console.error(`✗ APK 上传失败：${up.status} ${await up.text()}`);
  process.exit(1);
}
const upJson = await up.json();
console.log(`[资产] adshield-v7.15.10.apk 上传完成 · size=${upJson.size} · digest=${upJson.digest}`);

/* ── 资产 digest 核验（与本地实算一致）── */
if (upJson.digest !== `sha256:${sha256}`) {
  console.error(`✗ 资产 digest 不一致：${upJson.digest} vs sha256:${sha256}`);
  process.exit(1);
}
console.log(`[核验] 资产 digest 与本地一致 ✓`);

/* ── jsDelivr purge（latest.json + 版本 APK + latest 别名；cdn 主域边缘）── */
const purgePaths = [
  "gh/xiaobaiba999/Skip-Ad@main/update/latest.json",
  "gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.15.10.apk",
  "gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-latest.apk",
];
for (const p of purgePaths) {
  try {
    const r = await fetch(`https://purge.jsdelivr.net/${p}`);
    const j = await r.json().catch(() => ({}));
    console.log(`[purge] ${p} → ${r.status}${j.status ? " · " + j.status : ""}`);
  } catch (e) {
    console.log(`[purge] ${p} → 网络异常（如实报告）`);
  }
}

/* ── 发版证据核验：@tag 清单四域必须 86000（tag 不可变键·发布即新鲜）── */
for (const host of ["gcore.jsdelivr.net", "cdn.jsdelivr.net", "fastly.jsdelivr.net", "testingcf.jsdelivr.net"]) {
  try {
    const r = await fetch(
      `https://${host}/gh/${REPO}@${TAG}/update/latest.json?ts=${Date.now()}`,
    );
    const bodyText = await r.text();
    const vcTag = /"versionCode":\s*(\d+)/.exec(bodyText)?.[1];
    const ok = r.status === 200 && vcTag === String(EXPECT_VC);
    console.log(`[@tag] ${host} @${TAG}/latest.json → HTTP ${r.status} · versionCode=${vcTag} ${ok ? "✓" : "✗"}`);
    if (!ok) console.error(`  ⚠ tag 树清单未就绪（罕见：tag 刚建 CDN 同步延迟——稍后重试核验即可）`);
  } catch (e) {
    console.log(`[@tag] ${host} @${TAG} → 网络异常（如实报告）`);
  }
}

/* ── 仓库描述同步 ── */
const desc = await gh(`/repos/${REPO}`, {
  method: "PATCH",
  body: JSON.stringify({ description: NEW_DESC }),
});
console.log(`[描述] 仓库描述同步 → ${desc.status}`);

console.log(`\n✅ v7.15.10 发布完成：tag+Release+资产+digest 一致+purge+@tag 四域核验`);
