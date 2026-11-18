import { getGhToken } from "./gh-token.mjs";
const token = getGhToken();
const r = await fetch("https://api.github.com/repos/xiaobaiba999/Skip-Ad/releases?per_page=5", {
  headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
});
const releases = await r.json();
for (const rel of releases) {
  console.log(`${rel.tag_name} · ${rel.name} · assets: ${rel.assets.map(a => a.name + "(" + a.size + "B)").join(", ")}`);
}
