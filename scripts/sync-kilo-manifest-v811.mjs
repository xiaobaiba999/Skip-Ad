import { getGhToken } from "/home/z/skip-ad-repo/scripts/gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
const REPO = "xiaobaiba999/kilo";
// 读取 kilo 现有文件 sha
const get = await fetch(`https://api.github.com/repos/${REPO}/contents/update/latest.json?ref=main`, { headers });
const cur = await get.json();
const content = readFileSync("/home/z/skip-ad-repo/update/latest.json", "utf-8");
const body = {
  message: "manifest: v8.1.1 → latest.json（同步 Skip-Ad 正式清单 · versionCode 88300）",
  content: Buffer.from(content, "utf-8").toString("base64"),
  branch: "main",
};
if (cur && cur.sha) body.sha = cur.sha;
const put = await fetch(`https://api.github.com/repos/${REPO}/contents/update/latest.json`, {
  method: "PUT",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
const res = await put.json();
console.log(put.ok ? "KILO SYNC OK commit=" + (res.commit?.sha?.slice(0, 7)) : "KILO SYNC FAIL " + put.status + " " + (res.message ?? ""));
