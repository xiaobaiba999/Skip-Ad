import { getGhToken } from "/home/z/skip-ad-repo/scripts/gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
const body = readFileSync("/home/z/v811-release-notes.md", "utf-8");
const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v8.1.1",
    name: "开屏卫士 v8.1.1 · 日志准确性根治 · 断点续传多源下载 · 意见反馈双入口",
    body,
    draft: false,
    prerelease: false,
    make_latest: "true",
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);
const apk = readFileSync("/home/z/skip-ad-repo/android-app/dist/adshield-v8.1.1.apk");
let ok = false;
for (let i = 1; i <= 4 && !ok; i++) {
  try {
    const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v8.1.1.apk`, {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(apk.length) },
      body: new Uint8Array(apk),
    });
    const asset = await up.json();
    console.log(up.ok ? "ASSET OK " + asset.browser_download_url + " size=" + asset.size + " digest=" + (asset.digest ?? "?") : "ASSET FAIL " + (asset.message ?? up.status));
    ok = up.ok;
  } catch (e) { console.log("attempt", i, e.message); await new Promise((r) => setTimeout(r, 5000)); }
}
if (!ok) process.exit(1);
