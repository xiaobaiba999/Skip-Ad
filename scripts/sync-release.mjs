import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };

async function putFile(path, localPath, msg) {
  const content = readFileSync(localPath).toString("base64");
  let url = `https://api.github.com/repos/${REPO}/contents/${encodeURIComponent(path)}?ref=main`;
  let cur = await fetch(url, { headers });
  let sha = null;
  if (cur.ok) { const d = await cur.json(); sha = d.sha; }
  else if (cur.status !== 404) { console.log("GET FAIL", path, cur.status); return false; }
  const r = await fetch(`https://api.github.com/repos/${REPO}/contents/${encodeURIComponent(path)}`, {
    method: "PUT",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({ message: msg, content, branch: "main", ...(sha ? { sha } : {}) }),
  });
  const d = await r.json().catch(() => ({}));
  console.log(r.ok ? "OK  " : "FAIL", path, r.ok ? "" : (d.message ?? r.status));
  return r.ok;
}

const files = [
  ["android-app/src/com/zai/adshield/core/PowerGovernor.java", "/home/z/skip-ad-repo/android-app/src/com/zai/adshield/core/PowerGovernor.java"],
  ["android-app/src/com/zai/adshield/AdSkipService.java", "/home/z/skip-ad-repo/android-app/src/com/zai/adshield/AdSkipService.java"],
  ["android-app/src/com/zai/adshield/MainActivity.java", "/home/z/skip-ad-repo/android-app/src/com/zai/adshield/MainActivity.java"],
  ["android-app/src/com/zai/adshield/SubscriptionManager.java", "/home/z/skip-ad-repo/android-app/src/com/zai/adshield/SubscriptionManager.java"],
  ["android-app/src/com/zai/adshield/core/Subscription.java", "/home/z/skip-ad-repo/android-app/src/com/zai/adshield/core/Subscription.java"],
  ["android-app/tests/com/zai/adshield/test/PowerGovernorTest.java", "/home/z/skip-ad-repo/android-app/tests/com/zai/adshield/test/PowerGovernorTest.java"],
  ["android-app/run-tests.sh", "/home/z/skip-ad-repo/android-app/run-tests.sh"],
  ["android-app/assets/www/app.js", "/home/z/skip-ad-repo/android-app/assets/www/app.js"],
  ["android-app/dist/adshield-v5.3.0.apk", "/home/z/skip-ad-repo/android-app/dist/adshield-v5.3.0.apk"],
  ["android-app/dist/adshield-v5.3.0.apk.idsig", "/home/z/skip-ad-repo/android-app/dist/adshield-v5.3.0.apk.idsig"],
  ["src/lib/version.ts", "/home/z/my-project/src/lib/version.ts"],
  ["src/lib/instant-engine.ts", "/home/z/my-project/src/lib/instant-engine.ts"],
  ["src/lib/test-summary.ts", "/home/z/my-project/src/lib/test-summary.ts"],
  ["src/components/arena.tsx", "/home/z/my-project/src/components/arena.tsx"],
  ["src/components/learning-center.tsx", "/home/z/my-project/src/components/learning-center.tsx"],
];
let ok = 0;
for (const [p, l] of files) {
  if (await putFile(p, l, `v5.3.0 李跳跳式三零治理：${p.split("/").pop()}`)) ok++;
  await new Promise((r) => setTimeout(r, 400));
}
console.log(`\n同步完成 ${ok}/${files.length}`);
