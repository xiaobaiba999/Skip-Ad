/** 完善 v3.1.0 Release：改名 + 完整说明 + 上传 APK */
import { getGithubToken } from "../src/lib/credentials";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const REPO = "xiaobaiba999/Skip-Ad";
const API = `https://api.github.com/repos/${REPO}`;
const RELEASE_ID = 446600220;
const H = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const notes = readFileSync("/tmp/v310-notes.md", "utf8");

async function main() {
  const patch = await fetch(`${API}/releases/${RELEASE_ID}`, {
    method: "PATCH",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({
      name: "Skip-Ad v3.1.0 · 预拦截引擎 · 喜马拉雅专案 · 十轮自测 100%",
      body: notes,
      draft: false,
      prerelease: false,
      make_latest: "true",
    }),
  });
  const pd = (await patch.json()) as { html_url?: string; message?: string };
  console.log(patch.ok ? `✓ Release 已完善：${pd.html_url}` : `✗ PATCH ${patch.status}: ${pd.message}`);

  // 删除旧的测试附件（如有）
  const list = await fetch(`${API}/releases/${RELEASE_ID}/assets`, { headers: H });
  if (list.ok) {
    const assets = (await list.json()) as { id: number; name: string }[];
    for (const a of assets.filter((x) => x.name === "adshield-v3.1.0.apk")) {
      await fetch(`${API}/releases/assets/${a.id}`, { method: "DELETE", headers: H });
    }
  }

  const bytes = readFileSync("/home/z/my-project/android-app/dist/adshield-v3.1.0.apk");
  for (let attempt = 1; attempt <= 4; attempt++) {
    try {
      const up = await fetch(
        `https://uploads.github.com/repos/${REPO}/releases/${RELEASE_ID}/assets?name=adshield-v3.1.0.apk`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${TOKEN}`,
            Accept: "application/vnd.github+json",
            "Content-Type": "application/octet-stream",
            "Content-Length": String(bytes.length),
            "User-Agent": "skip-ad-instant-engine",
          },
          body: new Uint8Array(bytes),
        },
      );
      const d = (await up.json()) as { browser_download_url?: string; message?: string };
      if (up.ok) {
        console.log(`✓ APK 已上传（${(bytes.length / 1024).toFixed(1)}KB）：${d.browser_download_url}`);
        return;
      }
      console.log(`上传返回 ${up.status}：${d.message}（${attempt}/4）`);
    } catch (e) {
      console.log(`上传异常（${attempt}/4）：`, e instanceof Error ? e.message : e);
    }
    await new Promise((r) => setTimeout(r, 4000));
  }
  process.exitCode = 1;
}
main();
