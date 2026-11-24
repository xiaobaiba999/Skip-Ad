#!/usr/bin/env bun
/**
 * ─────────────────────────────────────────────────────────────
 *  发布前就绪检查（v7.15.4 金库轮 · 所有者令旨固化）
 * ─────────────────────────────────────────────────────────────
 *  「每次发布前都再次检查源码是否漏写或者写错」——本脚本是该指令的
 *  执行体，发布脚本（publish-*.mjs）硬闸 0 与人工发版流程都必须先过
 *  这里。检查面（A–E 致命 / F 警告）：
 *    A. 漏写 —— scripts/release-token-vault.mjs 在场且被 git 跟踪
 *    B. 写错 —— 金库密文可解码 + 形态合法 + 编解码互逆（零网络）
 *    C. 死令牌 —— 金库令牌 API /user 实测必须 200（绝不带死令牌发版）
 *    D. 权限 —— GET /repos/xiaobaiba999/Skip-Ad → 200 且 permissions.push
 *    E. 陈旧 —— env / local-gh-token 若在场必须与金库一致（不一致=轮换未同步）
 *    F.(警) App 内置令牌 Net.java L2 解码 + 活性（反馈通道受损不阻塞发版，
 *         但必须如实报告——rotate-token.mjs 换新）
 *  令牌本体永不打印：任何输出只出现指纹（前 4 字符 + 长度）。
 *
 *  用法：bun scripts/check-release-readiness.mjs [--offline]
 *    --offline  跳过 C/D/F 网络探测（仅 A/B/E——CI 快速门用）
 *  退出码：0 = 就绪；1 = 存在致命项（发布必须中止）。
 */
import { readFileSync } from "fs";
import { execSync } from "child_process";
import { join } from "path";
import {
  checkVaultIntegrity,
  fingerprint,
  decodeVault,
} from "./release-token-vault.mjs";

const REPO_ROOT = "/home/z/skip-ad-repo";
const REPO = "xiaobaiba999/Skip-Ad";
const VAULT_REL = "scripts/release-token-vault.mjs";
const NET_PATH = `${REPO_ROOT}/android-app/src/com/zai/adshield/Net.java`;
const TOKEN_SHAPE = /^[A-Za-z0-9_]{20,}$/;

/* Net.java 内置令牌同款密钥（builtInToken 的逆——探活专用，绝不外传本体） */
const NJ_KB = "KipShield";
const NJ_K1 = "SkipAd::2026::obf::k1";
const njXor = (input, key) =>
  Buffer.from(
    [...input].map((c, i) => c.charCodeAt(0) ^ key.charCodeAt(i % key.length)),
  ).toString("binary");
function njDecode(l2) {
  try {
    const layer1 = njXor(Buffer.from(l2, "base64").toString("binary"), NJ_KB);
    return njXor(Buffer.from(layer1, "base64").toString("binary"), NJ_K1);
  } catch {
    return "";
  }
}

async function probeUser(token, ua) {
  try {
    const r = await fetch("https://api.github.com/user", {
      headers: { "Authorization": `Bearer ${token}`, "User-Agent": ua },
    });
    return { status: r.status, login: r.ok ? (await r.json()).login : null };
  } catch (e) {
    return { status: 0, login: null, err: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * 执行就绪检查。返回 { ok, fails, warns }（ok=false 时 fails 非空——
 * 调用方（publish 脚本硬闸 0）必须中止发布）。
 */
export async function runReadinessChecks({ live = true, label = "发布前就绪检查" } = {}) {
  const fails = [];
  const warns = [];
  console.log(`▶ ${label}${live ? "" : "（离线模式：跳过 C/D/F 网络探测）"}`);

  /* A. 漏写：金库文件在场且被 git 跟踪（tag 树/克隆仓必须自带凭据源） */
  try {
    execSync(`git ls-files --error-unmatch ${VAULT_REL}`, { cwd: REPO_ROOT, stdio: "pipe" });
    console.log("  ✓ A 金库在场且已入库（git 跟踪——不漏写）");
  } catch {
    fails.push("A 金库未入库：scripts/release-token-vault.mjs 不在 git 索引（漏写）——git add 后重试");
    console.error("  ✗ A 金库未入库（漏写）");
  }

  /* B. 写错：离线互逆三连（密文行唯一 → 解码形态 → 重编码一致） */
  const vault = checkVaultIntegrity();
  if (!vault.ok) {
    fails.push(`B 金库写错：${vault.reason}`);
    console.error(`  ✗ B 金库写错：${vault.reason}`);
  } else {
    console.log(`  ✓ B 金库密文互逆一致（指纹：${fingerprint(vault.token)}）`);
  }

  const token = vault.ok ? vault.token : "";

  /* E. 陈旧：env / local-gh-token 在场时必须与金库一致（不一致=轮换漂移） */
  const envNames = ["SKIPAD_GH_TOKEN", "GH_TOKEN", "GITHUB_TOKEN"];
  for (const name of envNames) {
    const v = process.env[name];
    if (v && TOKEN_SHAPE.test(v)) {
      if (token && v !== token) {
        fails.push(`E 环境变量 ${name} 与金库不一致（陈旧令牌仍在生效——vault --encode 同步或清掉该变量）`);
        console.error(`  ✗ E ${name} 与金库不一致（陈旧）`);
      } else if (token) {
        console.log(`  ✓ E ${name} 与金库一致`);
      }
    }
  }
  try {
    const local = readFileSync(join(REPO_ROOT, "local-gh-token"), "utf-8").trim();
    if (local && TOKEN_SHAPE.test(local)) {
      if (token && local !== token) {
        fails.push("E local-gh-token 与金库不一致（本地已换新但金库未同步——bun scripts/release-token-vault.mjs --encode");
        console.error("  ✗ E local-gh-token 与金库不一致（陈旧）");
      } else if (token) {
        console.log("  ✓ E local-gh-token 与金库一致");
      }
    }
  } catch {
    console.log("  · E local-gh-token 不在场（金库独立供源——正常）");
  }

  /* C. 死令牌 + D. 权限（live 探测） */
  if (live && token) {
    const me = await probeUser(token, "skip-ad-readiness");
    console.log(`  ${me.status === 200 ? "✓" : "✗"} C 金库令牌 /user → HTTP ${me.status}${me.login ? ` · ${me.login}` : ""}`);
    if (me.status !== 200) {
      fails.push(`C 金库令牌不可用（HTTP ${me.status}${me.status === 401 ? "=已失效" : me.status === 0 ? "=网络不可达" : ""}）——GitHub 换新后 bun scripts/release-token-vault.mjs --encode`);
    }
    if (me.status === 200) {
      try {
        const r = await fetch(`https://api.github.com/repos/${REPO}`, {
          headers: { "Authorization": `Bearer ${token}`, "User-Agent": "skip-ad-readiness" },
        });
        const perm = r.ok ? ((await r.json()).permissions || {}) : null;
        if (r.status === 200 && perm.push) {
          console.log(`  ✓ D 仓库写权限在位（${REPO} · push=${perm.push}）`);
        } else {
          fails.push(`D 仓库权限不足：GET /repos/${REPO} → ${r.status} · push=${perm ? perm.push : "?"}（令牌 scope 不含 repo 写）`);
          console.error(`  ✗ D 仓库写权限缺失（HTTP ${r.status}）`);
        }
      } catch (e) {
        fails.push(`D 权限探测网络异常：${e instanceof Error ? e.message : e}`);
        console.error("  ✗ D 权限探测网络异常");
      }
    }
  } else if (live && !token) {
    fails.push("C/D 无法探测：金库解不出令牌（B 项已失败）");
  }

  /* F.(警) App 内置令牌 Net.java L2 —— 反馈通道探活，不阻塞发版（--offline 跳过） */
  if (!live) {
    console.log("  · F App 内置令牌探活已跳过（离线模式）");
  } else {
  try {
    const net = readFileSync(NET_PATH, "utf-8");
    const l2 = /private static final String L2 = "([^"]+)"/.exec(net)?.[1] || "";
    const appToken = l2 ? njDecode(l2) : "";
    if (!/^[A-Za-z0-9_]{20,}$/.test(appToken)) {
      warns.push("F Net.java 内置令牌解码失败（L2 密文损坏或结构变更）");
      console.warn("  ⚠ F Net.java 内置令牌解码失败（不阻塞发版——App 反馈通道受损）");
    } else {
      const app = await probeUser(appToken, "skip-ad-readiness-app");
      if (app.status === 200) {
        console.log(`  ✓ F App 内置令牌活着（${app.login}——问题反馈/日志上传通道正常）`);
      } else {
        warns.push(`F App 内置令牌 HTTP ${app.status}（反馈/日志上传通道受损——rotate-token.mjs 换新）`);
        console.warn(`  ⚠ F App 内置令牌 HTTP ${app.status}（不阻塞发版——rotate-token.mjs 换新）`);
      }
    }
  } catch (e) {
    warns.push(`F Net.java 探活异常：${e instanceof Error ? e.message : e}`);
    console.warn("  ⚠ F Net.java 探活异常（不阻塞发版）");
  }
  }

  const ok = fails.length === 0;
  if (ok) {
    console.log(`✅ ${label}通过${warns.length ? `（${warns.length} 项警告——见上）` : ""}`);
  } else {
    console.error(`✗ ${label}未过——致命 ${fails.length} 项：`);
    for (const f of fails) console.error(`    · ${f}`);
  }
  return { ok, fails, warns };
}

/* ── CLI（import.meta.main 守卫：被 publish 脚本导入时零副作用）── */
if (import.meta.main) {
  const offline = process.argv.includes("--offline");
  const { ok } = await runReadinessChecks({ live: !offline });
  process.exit(ok ? 0 : 1);
}
