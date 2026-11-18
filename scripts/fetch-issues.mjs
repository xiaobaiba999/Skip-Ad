import { getGhToken } from "./gh-token.mjs";
const token = getGhToken();
if (!token) { console.log("NO TOKEN"); process.exit(0); }
const r = await fetch("https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues?state=all&per_page=30", {
  headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
});
console.log("HTTP", r.status);
const issues = await r.json();
for (const i of issues) {
  console.log(`#${i.number} [${i.state}] ${i.title} · comments:${i.comments} · ${i.created_at}`);
}
