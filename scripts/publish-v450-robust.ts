/** 发布 v4.5.0 Release（健壮版：GitHub 边缘节点 404 缓存不一致重试 + 幂等） */
import { getGithubToken } from "../src/lib/credentials";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "X-GitHub-Api-Version": "2022-11-28",
  "User-Agent": "skip-ad-instant-engine",
};
const OWNER = "xiaobaiba999";
const REPO = "Skip-Ad";
const TAG = "v4.5.0";
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v4.5.0.apk";
const APK_NAME = "adshield-v4.5.0.apk";
const NOTES = readFileSync("/home/z/skip-ad-repo/scripts/release-tmp/v450-notes.md", "utf8");

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** 带重试的 GitHub 请求（东南亚边缘节点间歇 404 缓存不一致） */
async function gh(path: string, init: RequestInit = {}, maxTry = 8): Promise<Response> {
  let last: Response | null = null;
  for (let i = 0; i < maxTry; i++) {
    try {
      const r = await fetch(`https://api.github.com${path}`, {
        ...init,
        headers: { ...H, ...(init.body ? { "Content-Type": "application/json" } : {}) },
        cache: "no-store",
      });
      if (r.status !== 404 || i === maxTry - 1) return r;
      last = r;
      console.log(`  ↻ ${init.method ?? "GET"} ${path} → 404（边缘缓存不一致），1.5s 后重试 ${i + 1}/${maxTry}`);
      await sleep(1500);
    } catch (e) {
      console.log(`  ↻ 网络异常: ${e}，重试 ${i + 1}/${maxTry}`);
      await sleep(2000);
    }
  }
  return last!;
}

async function main() {
  // 0. 探活：仓库可达才继续
  const probe = await gh(`/repos/${OWNER}/${REPO}`);
  if (!probe.ok) {
    console.log("仓库探活失败:", probe.status);
    process.exit(1);
  }
  console.log("仓库可达 ✓");

  // 1. 查是否已有本 tag 的 release（幂等：重跑不重复创建）
  let releaseId: number | null = null;
  let releaseUrl = "";
  const exist = await gh(`/repos/${OWNER}/${REPO}/releases/tags/${TAG}`);
  if (exist.ok) {
    const d = await exist.json();
    releaseId = d.id;
    releaseUrl = d.html_url;
    console.log("Release 已存在（幂等复用）:", releaseUrl);
  } else {
    const cr = await gh(`/repos/${OWNER}/${REPO}/releases`, {
      method: "POST",
      body: JSON.stringify({
        tag_name: TAG,
        name: `开屏卫士 v4.5.0 · 乱点根治 · 系统开关彻底解耦 · 原生探针提速`,
        body: NOTES,
        draft: false,
        prerelease: false,
      }),
    });
    const d = await cr.json().catch(() => ({} as any));
    if (cr.status === 422 && /already_exists/i.test(String(d.message ?? ""))) {
      const again = await gh(`/repos/${OWNER}/${REPO}/releases/tags/${TAG}`);
      if (again.ok) {
        const d2 = await again.json();
        releaseId = d2.id;
        releaseUrl = d2.html_url;
        console.log("Release 已被并发创建，复用:", releaseUrl);
      }
    } else if (!cr.ok) {
      console.log("创建失败:", cr.status, JSON.stringify(d).slice(0, 300));
      process.exit(1);
    } else {
      releaseId = d.id;
      releaseUrl = d.html_url;
      console.log("Release 创建成功:", releaseUrl);
    }
  }
  if (!releaseId) {
    console.log("未取得 releaseId，退出");
    process.exit(1);
  }

  // 2. 上传 APK（幂等：同名资产先删再传）
  const apk = readFileSync(APK);
  const existAssets = await gh(`/repos/${OWNER}/${REPO}/releases/${releaseId}/assets`);
  if (existAssets.ok) {
    const assets = await existAssets.json();
    for (const a of assets ?? []) {
      if (a.name === APK_NAME) {
        const del = await gh(`/repos/${OWNER}/${REPO}/releases/assets/${a.id}`, { method: "DELETE" });
        console.log("旧资产删除:", del.status);
      }
    }
  }
  let uploaded = false;
  for (let i = 0; i < 6 && !uploaded; i++) {
    const up = await fetch(
      `https://uploads.github.com/repos/${OWNER}/${REPO}/releases/${releaseId}/assets?name=${APK_NAME}`,
      {
        method: "POST",
        headers: { ...H, "Content-Type": "application/vnd.android.package-archive" },
        body: apk,
      },
    );
    if (up.ok) {
      uploaded = true;
      console.log("APK 上传成功:", APK_NAME, apk.length, "bytes");
    } else {
      console.log(`APK 上传重试 ${i + 1}/6:`, up.status, (await up.text()).slice(0, 120));
      await sleep(2000);
    }
  }
  console.log(uploaded ? "✅ 全部完成: " + releaseUrl : "❌ APK 上入失败，请重跑本脚本（幂等）");
  process.exit(uploaded ? 0 : 1);
}

main();
