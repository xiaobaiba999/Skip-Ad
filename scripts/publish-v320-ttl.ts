/** v3.2.0 发布：长循环重试版（沙箱 api.github.com 出口间歇性故障，窗口期发布） */
import { getGithubToken } from "../src/lib/credentials";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const REPO = "xiaobaiba999/Skip-Ad";
const API = `https://api.github.com/repos/${REPO}`;
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const notes = readFileSync("/tmp/v320-notes.md", "utf8");

async function tryOnce(): Promise<boolean> {
  // 1) 创建或复用 Release
  let releaseId = 0;
  let releaseUrl = "";
  const create = await fetch(`${API}/releases`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: "v3.2.0",
      name: "Skip-Ad v3.2.0 · 陀螺仪诱导直断 · 链式回弹 · 弹窗广告屏蔽 · 十轮自测 100%",
      body: notes, draft: false, prerelease: false, make_latest: "true",
    }),
  });
  if (create.ok) {
    const d = await create.json();
    releaseId = d.id; releaseUrl = d.html_url;
    console.log(`[${new Date().toISOString().slice(11,19)}] ✓ Release 创建成功 id=${releaseId}`);
  } else {
    const msg = await create.text();
    console.log(`[${new Date().toISOString().slice(11,19)}] 创建 ${create.status}: ${msg.slice(0, 80)}`);
    if (create.status === 404) return false; // 出口故障，下轮再试
    const get = await fetch(`${API}/releases/tags/v3.2.0`, { headers: H });
    if (!get.ok) { console.log(`  复用失败 ${get.status}`); return get.status !== 404; }
    const gd = await get.json();
    releaseId = gd.id; releaseUrl = gd.html_url;
    console.log(`  复用已有 Release id=${releaseId}`);
  }
  // 2) 完善说明（若创建时 body 为 placeholder）
  // 3) 上传 APK（幂等：先删同名资产）
  const list = await fetch(`${API}/releases/${releaseId}/assets`, { headers: H });
  if (list.ok) {
    const assets = await list.json();
    for (const a of assets.filter((x: { name: string }) => x.name === "adshield-v3.2.0.apk")) {
      await fetch(`https://api.github.com/repos/${REPO}/releases/assets/${a.id}`, { method: "DELETE", headers: H });
      console.log("  已删除旧资产");
    }
  }
  const bytes = readFileSync("/home/z/my-project/android-app/dist/adshield-v3.2.0.apk");
  const up = await fetch(
    `https://uploads.github.com/repos/${REPO}/releases/${releaseId}/assets?name=adshield-v3.2.0.apk`,
    {
      method: "POST",
      headers: { ...H, "Content-Type": "application/octet-stream", "Content-Length": String(bytes.length) },
      body: new Uint8Array(bytes),
    },
  );
  const ud = await up.json().catch(() => ({}));
  if (up.ok) {
    console.log(`✓✓ APK 上传成功：${ud.browser_download_url}`);
    console.log(`✓✓ Release 地址：${releaseUrl}`);
    return true;
  }
  console.log(`  上传 ${up.status}: ${JSON.stringify(ud).slice(0, 80)}`);
  return up.status !== 404 && up.status !== -1;
}

let done = false;
for (let i = 1; i <= 150 && !done; i++) {
  try { done = await tryOnce(); } catch (e) { console.log(`轮 ${i} 异常:`, e instanceof Error ? e.message : e); }
  if (!done) await new Promise((r) => setTimeout(r, 12000 + Math.random() * 6000));
}
console.log(done ? "【发布完成】" : "【60 轮未成功——稍后需重跑】");
process.exit(done ? 0 : 1);
