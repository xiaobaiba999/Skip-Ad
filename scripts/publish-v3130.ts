/** v3.13.0 发布：创建 Tag+Release（含 APK 附件），回复并关闭 issue#16~#20 */
import { getGithubToken } from "../src/lib/credentials";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const REPO = "xiaobaiba999/Skip-Ad";
const API = `https://api.github.com/repos/${REPO}`;
const UPLOADS = `https://uploads.github.com/repos/${REPO}`;
const SHA = "9df648f3d72e7853468dae307f8d39dbaf699e15";
const APK_PATH = "/home/z/my-project/android-app/dist/adshield-v3.13.0.apk";
const H = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const notes = readFileSync("/home/z/my-project/scripts/release-tmp/v3130-notes.md", "utf8");

async function main() {
  // 1. Tag v3.13.0
  const tag = await fetch(`${API}/git/refs/tags/v3.13.0`, { headers: H });
  if (tag.status === 404) {
    const t = await fetch(`${API}/git/tags`, {
      method: "POST",
      headers: { ...H, "Content-Type": "application/json" },
      body: JSON.stringify({
        tag: "v3.13.0",
        message: "v3.13.0 验证码横幅四重防护+息屏休眠+夜间心跳降频（issue#16-#20）",
        object: SHA,
        type: "commit",
      }),
    });
    console.log(t.ok ? "✓ Tag v3.13.0 已创建" : `✗ tag ${t.status}: ${(await t.json()).message}`);
  } else {
    console.log("✓ Tag v3.13.0 已存在");
  }

  // 2. Release（latest）
  const rel = await fetch(`${API}/releases`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: "v3.13.0",
      name: "Skip-Ad v3.13.0 · 验证码横幅四重防护 · 息屏休眠 · 夜间心跳降频",
      body: notes,
      draft: false,
      prerelease: false,
      make_latest: "true",
    }),
  });
  const rd = (await rel.json()) as { id?: number; html_url?: string; message?: string };
  console.log(rel.ok ? `✓ Release 已发布：${rd.html_url}` : `✗ release ${rel.status}: ${rd.message}`);
  if (!rel.ok || !rd.id) process.exit(1);

  // 3. 上传 APK 附件
  const apk = readFileSync(APK_PATH);
  const up = await fetch(`${UPLOADS}/releases/${rd.id}/assets?name=adshield-v3.13.0.apk`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/vnd.android.package-archive" },
    body: new Uint8Array(apk),
  });
  console.log(up.ok ? `✓ APK 已上传（${(apk.length / 1024).toFixed(1)}KB）` : `✗ upload ${up.status}: ${(await up.json()).message}`);
}

await main();
