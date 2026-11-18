import { getGhToken } from "./gh-token.mjs";
import { writeFileSync, mkdirSync } from "fs";
import { dirname, join } from "path";
const token = getGhToken();
const root = "/home/z/skip-ad-repo";
// Get tree
const tr = await fetch("https://api.github.com/repos/xiaobaiba999/Skip-Ad/git/trees/main?recursive=1", {
  headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
});
const tree = (await tr.json()).tree;
// Download android-app source files (skip dist/, keystore keep, tests keep)
const files = tree.filter((f) =>
  f.type === "blob" &&
  (f.path.startsWith("android-app/") && !f.path.includes("/dist/") ||
   f.path.startsWith("docs/test-reports/"))
);
console.log("Files to download:", files.length);
let done = 0;
for (const f of files) {
  const r = await fetch(`https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/${f.path}`, {
    headers: { Authorization: `Bearer ${token}`, "User-Agent": "skip-ad-instant-engine" },
  });
  if (!r.ok) { console.log("FAIL", f.path, r.status); continue; }
  const buf = Buffer.from(await r.arrayBuffer());
  const dest = join(root, f.path);
  mkdirSync(dirname(dest), { recursive: true });
  writeFileSync(dest, buf);
  done++;
}
console.log("Downloaded:", done, "files");
