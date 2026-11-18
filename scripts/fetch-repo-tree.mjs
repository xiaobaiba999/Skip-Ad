import { getGhToken } from "./gh-token.mjs";
const token = getGhToken();
async function tree(sha) {
  const r = await fetch(`https://api.github.com/repos/xiaobaiba999/Skip-Ad/git/trees/${sha}?recursive=1`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
  });
  return r.json();
}
const t = await tree("main");
if (t.tree) {
  for (const f of t.tree) {
    if (f.type === "blob") console.log(f.path, `(${f.size}B)`);
  }
} else {
  console.log("ERR", t.message);
}
