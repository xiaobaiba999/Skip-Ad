import { getGhToken } from "./gh-token.mjs";
import { writeFileSync, mkdirSync } from "fs";
import { dirname, join } from "path";
const token = getGhToken();
const root = "/home/z/skip-ad-repo";
const tr = await fetch("https://api.github.com/repos/xiaobaiba999/Skip-Ad/git/trees/main?recursive=1", {
  headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
});
const tree = (await tr.json()).tree;
const files = tree.filter((f) => f.type === "blob" && (f.path.startsWith("android-app/tests/") || f.path.startsWith("docs/")));
console.log("Files:", files.length);
let done = 0;
for (let i = 0; i < files.length; i += 3) {
  await Promise.all(files.slice(i, i + 3).map(async (f) => {
    const r = await fetch(`https://api.github.com/repos/xiaobaiba999/Skip-Ad/contents/${encodeURIComponent(f.path)}?ref=main`, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
    });
    if (!r.ok) { console.log("FAIL", f.path, r.status); return; }
    const d = await r.json();
    const buf = Buffer.from(d.content, "base64");
    const dest = join(root, f.path);
    mkdirSync(dirname(dest), { recursive: true });
    writeFileSync(dest, buf);
    done++;
  }));
}
console.log("Downloaded:", done);
