/**
 * GitHub 管理脚本（bun scripts/gh-admin.ts <cmd>）
 * cmds: verify | ensure-repo | release
 * 令牌经 credentials.ts 混淆解密，命令行与输出均不出现明文。
 */
import { verifyToken, ensureRepo, createRelease } from "../src/lib/github";
import { APP_VERSION, VERSION_HISTORY } from "../src/lib/version";

const cmd = process.argv[2] ?? "verify";

if (cmd === "verify") {
  const v = await verifyToken();
  console.log(JSON.stringify({ ok: v.ok, login: v.login ?? null, message: v.ok ? undefined : v.message }));
  process.exit(v.ok ? 0 : 1);
}

if (cmd === "ensure-repo") {
  const v = await verifyToken();
  if (!v.ok) {
    console.log(JSON.stringify({ ok: false, step: "verify", message: v.message }));
    process.exit(1);
  }
  const r = await ensureRepo();
  console.log(JSON.stringify(r));
  process.exit(r.ok ? 0 : 1);
}

if (cmd === "release") {
  const latest = VERSION_HISTORY[0];
  const body = [
    `## ${latest.tagline}`,
    "",
    ...latest.notes.map((n) => `- ${n}`),
    "",
    "---",
    "安装方式：浏览器打开演示页 → 「添加到主屏幕」即可作为独立 App 使用（PWA）。",
    "历次 APK 归档见仓库 android-app/dist/ 与 Releases。",
  ].join("\n");
  const r = await createRelease({
    tag: `v${APP_VERSION}`,
    name: `Skip-Ad v${APP_VERSION} · ${latest.tagline}`,
    body,
  });
  console.log(JSON.stringify(r));
  process.exit(r.ok ? 0 : 1);
}

console.log("unknown cmd:", cmd);
process.exit(1);
