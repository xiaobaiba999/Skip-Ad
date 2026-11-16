/** 重传 v3.3.0 APK 资产（仓库名与令牌均取自项目常量，杜绝手打错误） */
import { getGithubToken } from "../src/lib/credentials";
import { GITHUB_API, GITHUB_REPO } from "../src/lib/version";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const H = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

async function gh(url: string, init?: RequestInit, tries = 8): Promise<Response> {
  for (let i = 1; i <= tries; i++) {
    const r = await fetch(url, { ...init, headers: { ...H, ...(init?.headers ?? {}) } });
    if (r.ok || r.status !== 404 || i === tries) return r;
    await new Promise((x) => setTimeout(x, 1500 + i * 700));
  }
  throw new Error("unreachable");
}

const rel = await (await gh(`${GITHUB_API}/releases/tags/v3.3.0`)).json() as {
  id: number; tag_name: string; assets?: { name: string; id: number }[];
};
if (!rel.id) throw new Error(`取 Release 失败：${JSON.stringify(rel).slice(0, 200)}`);
console.log("release:", rel.id, rel.tag_name);
for (const a of (rel.assets ?? []).filter((a) => a.name.endsWith(".apk"))) {
  const d = await gh(`${GITHUB_API}/releases/assets/${a.id}`, { method: "DELETE" });
  console.log("del old asset:", a.name, d.status);
}
const bytes = readFileSync("/home/z/my-project/android-app/dist/adshield-v3.3.0.apk");
const up = await fetch(
  `https://uploads.github.com/repos/${GITHUB_REPO}/releases/${rel.id}/assets?name=adshield-v3.3.0.apk`,
  {
    method: "POST",
    headers: { ...H, "Content-Type": "application/octet-stream", "Content-Length": String(bytes.length) },
    body: new Uint8Array(bytes),
  },
);
const d = (await up.json()) as { browser_download_url?: string; message?: string };
console.log("upload:", up.status, d.browser_download_url ?? d.message);
if (up.ok) {
  // 终验：latest + 资产大小
  const latest = await (await gh(`${GITHUB_API}/releases/latest`)).json() as {
    tag_name: string; assets?: { name: string; size: number }[];
  };
  console.log("latest:", latest.tag_name, "assets:", JSON.stringify(latest.assets?.map((a) => `${a.name}(${a.size})`)));
}
process.exit(up.ok ? 0 : 1);
