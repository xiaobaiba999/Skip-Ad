import { getGhToken } from "./gh-token.mjs";
const token = getGhToken();
const r = await fetch(`https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues/56/comments`, {
  headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
});
const comments = await r.json();
for (const c of comments) {
  console.log(`--- ${c.user.login} @ ${c.created_at} ---`);
  console.log(c.body.slice(0, 1500));
}
