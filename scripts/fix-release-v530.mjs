import { readFileSync } from "fs";
import { getGhToken } from "./gh-token.mjs";
const token = getGhToken();
const H = { Authorization: `Bearer ${token}`, "User-Agent": "skip-ad" };
const REPO = "xiaobaiba999/Skip-Ad";
const REL_ID = 449436112;
const ASSET_ID = 761493465;

// 1) 删除坏 asset
const del = await fetch(`https://api.github.com/repos/${REPO}/releases/assets/${ASSET_ID}`, {
  method: "DELETE", headers: H });
console.log("删除旧 asset:", del.status, del.status === 204 ? "OK" : await del.text());

// 2) 上传修复后的 APK
const apk = readFileSync("/home/z/my-project/android-app/dist/adshield-v5.3.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${REL_ID}/assets?name=adshield-v5.3.0.apk`, {
  method: "POST",
  headers: { ...H, "Content-Type": "application/vnd.android.package-archive", "Content-Length": String(apk.byteLength) },
  body: new Uint8Array(apk),
});
const upj = await up.json();
console.log("上传新 asset:", up.status, upj.id, upj.name, upj.size, "bytes");

// 3) 更新 release 正文（开头插入修复告示）
const cur = await fetch(`https://api.github.com/repos/${REPO}/releases/${REL_ID}`, { headers: H });
const curj = await cur.json();
const notice = [
  "> ## ⚠️ 修复版重发（build 50300）· 2026-11-18",
  ">",
  "> **首发包 versionCode 被误传为 530**（小于 v5.2.0 的 50200），从 v5.2.0 覆盖安装会被系统拒绝，提示「**无法降级安装**」。",
  "> 本 asset 已替换为修复包：**versionCode=50300**，与首发包 **dex 字节级一致（零行为差异）**、同 keystore 签名。",
  ">",
  "> - **v5.2.0 用户**：直接下载本包覆盖安装即可（无需卸载）。",
  "> - **已装首发坏包（versionName 5.3.0 / versionCode 530）的用户**：请先卸载再安装本包（坏包 versionCode 过小无法被覆盖；卸载会清学习数据，重新教一次即可）。",
  "> - 构建脚本已加 versionCode 单调递增守卫，此类事故不会再发生。",
  "",
  "",
].join("\n");
const upd = await fetch(`https://api.github.com/repos/${REPO}/releases/${REL_ID}`, {
  method: "PATCH",
  headers: { ...H, "Content-Type": "application/json" },
  body: JSON.stringify({ body: notice + curj.body }),
});
console.log("更新正文:", upd.status, upd.status === 200 ? "OK" : await upd.text());
