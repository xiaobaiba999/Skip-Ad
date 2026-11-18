import { getGhToken } from "./gh-token.mjs";
import { writeFileSync, mkdirSync, existsSync } from "fs";
import { dirname, join } from "path";
const token = getGhToken();
const root = "/home/z/skip-ad-repo";
const need = [
  "android-app/src/com/zai/adshield/AdSkipService.java",
  "android-app/src/com/zai/adshield/KeepAliveReceiver.java",
  "android-app/src/com/zai/adshield/MainActivity.java",
  "android-app/src/com/zai/adshield/MasterToggleReceiver.java",
  "android-app/src/com/zai/adshield/Net.java",
  "android-app/src/com/zai/adshield/SubscriptionManager.java",
  "android-app/src/com/zai/adshield/core/AdDecisionCore.java",
  "android-app/src/com/zai/adshield/core/GkdSelector.java",
  "android-app/src/com/zai/adshield/core/Json5.java",
  "android-app/src/com/zai/adshield/core/MiniProgramGuard.java",
  "android-app/src/com/zai/adshield/core/RestoreGuard.java",
  "android-app/src/com/zai/adshield/core/RuleEngine.java",
  "android-app/src/com/zai/adshield/core/Subscription.java",
  "android-app/AndroidManifest.xml",
  "android-app/keystore/adshield.keystore",
];
const missing = need.filter((p) => !existsSync(join(root, p)));
console.log("missing:", missing.length);
async function dl(path) {
  const r = await fetch(`https://api.github.com/repos/xiaobaiba999/Skip-Ad/contents/${encodeURIComponent(path)}?ref=main`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" },
  });
  if (!r.ok) { console.log("FAIL", path, r.status); return false; }
  const d = await r.json();
  const buf = Buffer.from(d.content, "base64");
  const dest = join(root, path);
  mkdirSync(dirname(dest), { recursive: true });
  writeFileSync(dest, buf);
  console.log("OK", path, buf.length + "B");
  return true;
}
// batch of 4 concurrent
for (let i = 0; i < missing.length; i += 4) {
  await Promise.all(missing.slice(i, i + 4).map(dl));
}
