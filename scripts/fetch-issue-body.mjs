import { getGhToken } from "./gh-token.mjs";
const token = getGhToken();
const nums = process.argv.slice(2).map(Number);
for (const n of nums) {
  const r = await fetch(`https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues/${n}`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
  });
  const i = await r.json();
  console.log(`\n${"=".repeat(80)}\nISSUE #${n}: ${i.title}\n${"=".repeat(80)}`);
  console.log(i.body);
}
