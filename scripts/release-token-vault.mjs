#!/usr/bin/env bun
/**
 * ─────────────────────────────────────────────────────────────
 *  发布令牌金库（v7.15.4 起 · 仓库所有者指令：令牌密文永久固化入库）
 * ─────────────────────────────────────────────────────────────
 *  · 决策背景：发布链凭据原依赖「环境变量 / .gitignore local-* 管辖区
 *    的 local-gh-token」，环境一换就丢——跨会话发布反复向所有者索要
 *    令牌。所有者明示接受风险等级，指令「把它加密放在源码里，不要
 *    每次都询问」。
 *  · 形态：双层 XOR + Base64 密文（token → xor K1 → b64 → xor KB → b64），
 *    与 Net.java builtInToken 同族编解码、独立密钥（防跨解）。密文只含
 *    Base64 字母表（A-Za-z0-9+/=，无下划线），结构性不可能命中
 *    ghp_/github_pat_ 明文扫描形态。
 *  · 风险定性（沿 docs/SECURITY.md R1 口径）：混淆不等于加密——本金库
 *    与 Net.java 内置令牌同风险等级，入库即视为「所有者背书的可控暴露」。
 *    泄露处置路径 = rotate-token.mjs 同款：GitHub 吊销 → --encode 换新。
 *  · 消费方：scripts/gh-token.mjs 第三优先级（env → local-gh-token →
 *    本金库）——全部发布/管理脚本与 git 推送链自动继承，从此不再向
 *    所有者索要令牌。
 *  · 发布前强制核验：scripts/check-release-readiness.mjs（漏写/写错/
 *    死令牌/权限四重硬闸）+ secret-scan.sh 第 ⑤ 闸（离线互逆性）。
 *  · 令牌本体永不打印：任何日志只出现指纹（前 4 字符 + 长度）。
 *
 *  CLI：
 *    bun scripts/release-token-vault.mjs --check        离线完整性（解码+形态+互逆·零网络）
 *    bun scripts/release-token-vault.mjs --fingerprint  指纹（ghp_… · 40 字符）
 *    bun scripts/release-token-vault.mjs --print        输出本体（仅限凭据管道：git askpass）
 *    bun scripts/release-token-vault.mjs --encode [--stdin]  轮换（硬闸：形态→API 活性→回读互逆）
 */
import { readFileSync, writeFileSync } from "fs";
import { join } from "path";

const SELF_PATH = join(import.meta.dir, "release-token-vault.mjs");
const KB = "SkipVault";
const K1 = "SkipAd::2026::release::vault";
const TOKEN_RE = /^(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})$/;

/* ── 编解码（与 rotate-token.mjs / Net.java 严格同语义的字符级 XOR）── */
const xorWith = (input, key) =>
  Buffer.from(
    [...input].map((c, i) => c.charCodeAt(0) ^ key.charCodeAt(i % key.length)),
  ).toString("binary");

/* 编码（--encode 用）：token → xor K1 → b64 → xor KB → b64 */
function encodeVault(token) {
  const layer1 = Buffer.from(xorWith(token, K1), "binary").toString("base64");
  return Buffer.from(xorWith(layer1, KB), "binary").toString("base64");
}

/* 解码：与编码严格互逆；任何异常返回空串（绝不抛出——凭据链兜底语义） */
export function decodeVault(l2) {
  try {
    const layer1 = xorWith(Buffer.from(l2, "base64").toString("binary"), KB);
    return xorWith(Buffer.from(layer1, "base64").toString("binary"), K1);
  } catch {
    return "";
  }
}

/* ── 密文行（自描述单行——--encode 原位替换的目标）── */
export const VAULT_L2 =
  "HSokKhobOAgQOAMsFjsgMAoiHiMhIxMWPz8lJz0OByUgMCkZAgM8RB8iGgM6NF4KKBAvGAoTblY=";

/** 从源码自身重读密文（防内存态与磁盘态漂移；缺行/多行/形态坏 → 空串） */
export function readVaultCiphertext() {
  try {
    const src = readFileSync(SELF_PATH, "utf-8");
    const hits = [...src.matchAll(/export const VAULT_L2 =\s*"([^"]+)"/g)];
    if (hits.length !== 1) return "";
    return /^[A-Za-z0-9+/=]+$/.test(hits[0][1]) ? hits[0][1] : "";
  } catch {
    return "";
  }
}

/** 解出金库令牌（磁盘密文重读 → 解码 → 形态闸；失败返回空串） */
export function getVaultToken() {
  const ct = readVaultCiphertext();
  if (!ct) return "";
  const token = decodeVault(ct);
  return TOKEN_RE.test(token) ? token : "";
}

/** 指纹（日志安全形态）：ghp_… · 40 字符 */
export function fingerprint(token) {
  if (!token) return "(空)";
  return `${token.slice(0, 4)}… · ${token.length} 字符`;
}

/** 离线完整性（零网络·可复用）：密文行唯一且纯 Base64 → 解码形态合法 → 重编码互逆。
 *  返回 { ok, token?, reason? }——check-release-readiness.mjs / secret-scan.sh 共用。 */
export function checkVaultIntegrity() {
  const ct = readVaultCiphertext();
  if (!ct) return { ok: false, reason: "密文行缺失/重复/形态坏（VAULT_L2 声明必须唯一且纯 Base64）" };
  const token = decodeVault(ct);
  if (!TOKEN_RE.test(token)) {
    return { ok: false, reason: `密文解码后不符令牌形态（得 ${token.length} 字符——密文损坏或密钥漂移）` };
  }
  if (encodeVault(token) !== ct) {
    return { ok: false, reason: "编解码互逆破坏（re-encode != 密文——文件被改坏）" };
  }
  return { ok: true, token };
}

/* ── CLI（import.meta.main 守卫：被导入时零副作用——防宿主脚本参数误触发）── */
if (import.meta.main) {
  const args = process.argv.slice(2);

  if (args.includes("--check")) {
    const r = checkVaultIntegrity();
    if (!r.ok) {
      console.error(`  ✗ ${r.reason}`);
      process.exit(1);
    }
    console.log(`  ✓ 金库密文可解码 · 形态合法 · 互逆一致（指纹：${fingerprint(r.token)}）`);
    process.exit(0);
  }

  if (args.includes("--fingerprint")) {
    const token = getVaultToken();
    if (!token) {
      console.error("✗ 金库解不出令牌（先 --check 定位）");
      process.exit(1);
    }
    console.log(fingerprint(token));
    process.exit(0);
  }

  if (args.includes("--print")) {
    const token = getVaultToken();
    if (!token) process.exit(1);
    console.log(token); // 仅限凭据管道消费（git askpass）——绝不进日志
    process.exit(0);
  }

  if (args.includes("--encode")) {
    /* ── 轮换：输入优先级 stdin → env → local-gh-token（本体不打印）── */
    let token = "";
    let source = "";
    if (args.includes("--stdin")) {
      token = readFileSync(0, "utf-8").trim().split(/\r?\n/)[0] || "";
      source = "stdin";
    } else {
      const env = process.env.SKIPAD_GH_TOKEN || process.env.GH_TOKEN || process.env.GITHUB_TOKEN;
      if (env) { token = env.trim(); source = "环境变量"; }
      else {
        try {
          const v = readFileSync(join(import.meta.dir, "..", "local-gh-token"), "utf-8").trim();
          if (v) { token = v; source = "local-gh-token"; }
        } catch { /* 落空 */ }
      }
    }
    if (!token) {
      console.error("✗ 未取得新令牌：--stdin / SKIPAD_GH_TOKEN / local-gh-token 均无");
      process.exit(1);
    }
    if (!TOKEN_RE.test(token)) {
      console.error(`✗ 令牌形态不符（前缀 ${token.slice(0, 4)}…）——仅接受 ghp_ / github_pat_`);
      process.exit(1);
    }
    console.log(`[输入] 来源：${source} · ${fingerprint(token)}`);

    /* 硬闸 1：轮换前 API 实测必须活着（绝不把死令牌写进金库） */
    const probe = await fetch("https://api.github.com/user", {
      headers: { "Authorization": `Bearer ${token}`, "User-Agent": "skip-ad-vault" },
    });
    console.log(`[硬闸1] API /user → HTTP ${probe.status}`);
    if (probe.status !== 200) {
      console.error("✗ 拒绝轮换：令牌不可用（401=已失效——先在 GitHub 换新）");
      process.exit(1);
    }
    console.log(`[硬闸1] 身份：${(await probe.json()).login}（scope 生效）`);

    /* 硬闸 2：密文行在场且唯一（原位替换目标） */
    const src = readFileSync(SELF_PATH, "utf-8");
    const hits = [...src.matchAll(/export const VAULT_L2 =\s*"[^"]+"/g)];
    if (hits.length !== 1) {
      console.error(`✗ VAULT_L2 声明行数 ${hits.length}（必须恰为 1——结构变更需人工核对）`);
      process.exit(1);
    }

    /* 写入 + 硬闸 3：磁盘回读互逆（防写坏） */
    const newCt = encodeVault(token);
    writeFileSync(SELF_PATH, src.replace(hits[0][0], `export const VAULT_L2 =\n  "${newCt}";`));
    const reread = getVaultToken();
    if (reread !== token) {
      console.error("✗ 回读解码与输入不一致（互逆性破坏）——立即人工核查本文件");
      process.exit(1);
    }
    console.log(`[硬闸3] 磁盘回读解码一致 ✓（密文 ${newCt.length} 字符）`);
    console.log(`\n✅ 金库轮换完成——后续：提交推送 → check-release-readiness.mjs 复验`);
    process.exit(0);
  }

  console.error("用法：--check | --fingerprint | --print | --encode [--stdin]（缺省=模块导入）");
  process.exit(0);
}
