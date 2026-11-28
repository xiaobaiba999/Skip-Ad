import { getGhToken } from "/home/z/skip-ad-repo/scripts/gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
const body = readFileSync("/home/z/v811-release-notes.md", "utf-8");
// PATCH release body（release id 461333599）
const r = await fetch(`https://api.github.com/repos/${REPO}/releases/461333599`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ body }),
});
const rel = await r.json();
console.log(r.ok ? "BODY OK (" + rel.body.length + " chars, versionCode line: " + (rel.body.includes("\nversionCode: 88300") ? "found" : "NOT FOUND") + ")" : "BODY FAIL " + rel.message);
