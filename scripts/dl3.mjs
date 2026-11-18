import { getGhToken } from "./gh-token.mjs";
import { writeFileSync, mkdirSync } from "fs";
import { dirname, join } from "path";
const token = getGhToken();
const root = "/home/z/skip-ad-repo";
const need = ["android-app/src/com/zai/adshield/AdSkipService.java", "android-app/AndroidManifest.xml", "android-app/keystore/adshield.keystore", "android-app/src/com/zai/adshield/Downloader.java"];
for (const path of need) {
  const r = await fetch(`https://api.github.com/repos/xiaobaiba999/Skip-Ad/contents/${encodeURIComponent(path)}?ref=main`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
  });
  if (!r.ok) { console.log("FAIL", path, r.status); continue; }
  const d = await r.json();
  const buf = Buffer.from(d.content, "base64");
  const dest = join(root, path);
  mkdirSync(dirname(dest), { recursive: true });
  writeFileSync(dest, buf);
  console.log("OK", path, buf.length + "B");
}
