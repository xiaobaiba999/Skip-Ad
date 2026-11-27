#!/usr/bin/env bun
/**
 * verify-update-sources.mjs —— 全部客户端可见更新源核验 + 历史客户端决策逻辑复验
 * ════════════════════════════════════════════════════════════════════════
 * 核验内容：
 *  1. 全部真实源（Skip-Ad/kilo 双仓 × jsDelivr 四域 + raw + @tag 不可变键 +
 *     GitHub API 匿名 releases/latest）实际返回的 versionName/versionCode。
 *  2. 用【真实历史客户端决策逻辑】复验「能否发现最新版本」：
 *     · v8.0.7 / v8.0.8 客户端（首源成功即返回，无跨源比较——2026-11-27 事故缺陷）
 *     · v7.15.14 客户端（多源合流取最大 versionCode + GitHub API 兜底——修复前基线）
 *     · v8.0.9 客户端（多源合流 + @tag 链 + API 兜底 + 全败必报错）
 *
 * 用法：
 *   bun scripts/verify-update-sources.mjs [--client-versionCode 87100] [--client-versionName 8.0.7]
 */

const args = process.argv.slice(2);
function argVal(name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : fallback;
}
const CLIENT_CODE = Number(argVal("--client-versionCode", "87100"));
const CLIENT_NAME = argVal("--client-versionName", "8.0.7");

const REPO = "xiaobaiba999/Skip-Ad";
const KILO = "xiaobaiba999/kilo";

const V807_SOURCES = [
  // git tag v8.0.7 AppLinks.kt（真实历史顺序）：首个成功源即返回
  `https://cdn.jsdelivr.net/gh/${REPO}@main/update/latest.json`,
  `https://gcore.jsdelivr.net/gh/${REPO}@main/update/latest.json`,
  `https://raw.githubusercontent.com/${REPO}/main/update/latest.json`,
  `https://raw.githubusercontent.com/${KILO}/main/update/latest.json`,
  `https://cdn.jsdelivr.net/gh/${KILO}@main/update/latest.json`,
];

const V71514_JSDELIVR_HOSTS = [
  "gcore.jsdelivr.net",
  "cdn.jsdelivr.net",
  "fastly.jsdelivr.net",
  "testingcf.jsdelivr.net",
];

function parse3(v) {
  const p = String(v).trim().replace(/^v/, "").split(".");
  if (p.length < 2) return null;
  const a = Number(p[0]);
  const b = Number(p[1]);
  const c = p.length >= 3 ? Number(p[2]) : 0;
  if (!Number.isInteger(a) || !Number.isInteger(b) || !Number.isInteger(c)) return null;
  return [a, b, c];
}

function candidateTags(versionName) {
  const cur = parse3(versionName);
  if (!cur) return [];
  const [a, b, c] = cur;
  return [`v${a}.${b}.${c + 1}`, `v${a}.${b + 1}.0`, `v${a}.${b + 1}.1`, `v${a}.${b + 2}.0`];
}

async function fetchJson(url, timeoutMs = 20_000) {
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(timeoutMs) });
    if (!res.ok) return { ok: false, error: `HTTP ${res.status}` };
    const text = await res.text();
    try {
      return { ok: true, json: JSON.parse(text), text };
    } catch {
      return { ok: false, error: "invalid json" };
    }
  } catch (e) {
    return { ok: false, error: String(e?.message || e).slice(0, 60) };
  }
}

/** 清单契约校验（与各客户端 validated()/fetchManifest 同口径）。 */
function validManifest(json) {
  if (!json) return false;
  if (!(json.versionCode > 0) || !json.versionName || !json.releaseTag) return false;
  if (json.apkUrl && !String(json.apkUrl).startsWith("https://")) return false;
  const pkg = json.packageName ?? "";
  if (pkg && pkg !== "com.zai.adshield") return false;
  return true;
}

async function main() {
  console.log(`═══ 更新源全量核验（客户端视角：${CLIENT_NAME} / versionCode ${CLIENT_CODE}）═══\n`);

  const sources = [];
  const addSource = (id, url, kind = "manifest") => sources.push({ id, url, kind });

  // Skip-Ad 仓：jsDelivr 四域 @main + raw
  for (const host of ["cdn.jsdelivr.net", "gcore.jsdelivr.net", "fastly.jsdelivr.net", "testingcf.jsdelivr.net"]) {
    addSource(`skip-ad/${host}@main`, `https://${host}/gh/${REPO}@main/update/latest.json`);
  }
  addSource("skip-ad/raw@main", `https://raw.githubusercontent.com/${REPO}/main/update/latest.json`);
  // kilo 历史仓
  addSource("kilo/cdn@main", `https://cdn.jsdelivr.net/gh/${KILO}@main/update/latest.json`);
  addSource("kilo/gcore@main", `https://gcore.jsdelivr.net/gh/${KILO}@main/update/latest.json`);
  addSource("kilo/raw@main", `https://raw.githubusercontent.com/${KILO}/main/update/latest.json`);
  // @tag 不可变键
  addSource("skip-ad/cdn@v8.0.8", `https://cdn.jsdelivr.net/gh/${REPO}@v8.0.8/update/latest.json`);
  addSource("skip-ad/cdn@v8.0.9", `https://cdn.jsdelivr.net/gh/${REPO}@v8.0.9/update/latest.json`);
  // GitHub API 匿名
  addSource("github-api/releases/latest", `https://api.github.com/repos/${REPO}/releases/latest`, "api");

  const results = new Map();
  for (const s of sources) {
    const r = await fetchJson(s.url);
    let verdict;
    if (!r.ok) {
      verdict = { ok: false, error: r.error, code: null };
    } else if (s.kind === "api") {
      const body = r.json.body ?? "";
      const m = /^\s*versionCode:\s*(\d+)\s*$/m.exec(body);
      const asset = (r.json.assets ?? []).find((a) => String(a.name ?? "").endsWith(".apk"));
      if (!m || !asset) {
        verdict = { ok: false, error: "no versionCode line / no apk asset", code: null };
      } else {
        verdict = {
          ok: true,
          code: Number(m[1]),
          name: (r.json.tag_name ?? "").replace(/^v/, ""),
          apkUrl: asset.browser_download_url,
          digest: (asset.digest ?? "").replace(/^sha256:/, ""),
        };
      }
    } else {
      if (!validManifest(r.json)) {
        verdict = { ok: false, error: "manifest contract violated", code: null };
      } else {
        verdict = {
          ok: true,
          code: r.json.versionCode,
          name: r.json.versionName,
          apkUrl: r.json.apkUrl,
          digest: r.json.apkSha256 ?? "",
          mirrors: r.json.apkMirrors ?? [],
        };
      }
    }
    results.set(s.id, verdict);
    console.log(
      `  ${verdict.ok ? "✓" : "⚠"} ${s.id.padEnd(32)} ${
        verdict.ok ? `${verdict.name} / ${verdict.code}` : verdict.error
      }`,
    );
  }

  console.log(`\n═══ 历史客户端决策逻辑复验（最新版必须被各代客户端发现）═══\n`);

  // ── v8.0.7/v8.0.8 客户端（真实历史逻辑：首源成功即返回）──
  const v807Verdict = await (async () => {
    for (const url of V807_SOURCES) {
      const r = await fetchJson(url);
      if (r.ok && validManifest(r.json)) {
        return { source: url, code: r.json.versionCode, name: r.json.versionName };
      }
    }
    return null;
  })();
  if (v807Verdict) {
    const found = v807Verdict.code > CLIENT_CODE;
    console.log(
      `  ${found ? "✓" : "✗"} v8.0.7 客户端（87100，首源成功即返回）：` +
        `命中 ${v807Verdict.name}/${v807Verdict.code} @ ${v807Verdict.source.replace("https://", "")}` +
        (found ? " → 发现更新" : " → 误判「已是最新」（该代缺陷，需多源客户端）"),
    );
  } else {
    console.log(`  ✗ v8.0.7 客户端：全部源不可达（检查失败，绝不误报「已是最新」）`);
  }

  // ── v7.15.14 客户端（真实历史逻辑：raw → jsdelivr 四域梯度 → @tag → API，合流取最大）──
  const v71514Verdict = await (async () => {
    let best = null;
    let bestCode = CLIENT_CODE;
    const raw = await fetchJson(`https://raw.githubusercontent.com/${REPO}/main/update/latest.json`);
    if (raw.ok && validManifest(raw.json) && raw.json.versionCode > bestCode) {
      best = { source: "raw@main", code: raw.json.versionCode, name: raw.json.versionName };
      bestCode = raw.json.versionCode;
    }
    for (const host of V71514_JSDELIVR_HOSTS) {
      const r = await fetchJson(`https://${host}/gh/${REPO}@main/update/latest.json`);
      if (r.ok && validManifest(r.json) && r.json.versionCode > bestCode) {
        best = { source: `jsdelivr/${host}`, code: r.json.versionCode, name: r.json.versionName };
        bestCode = r.json.versionCode;
        break; // 真实逻辑：首域成功即 sticky（不再试后续域）
      }
    }
    if (best === null || bestCode <= CLIENT_CODE) {
      for (const tag of candidateTags(CLIENT_NAME)) {
        const r = await fetchJson(`https://gcore.jsdelivr.net/gh/${REPO}@${tag}/update/latest.json`);
        if (r.ok && validManifest(r.json) && r.json.versionCode > bestCode) {
          best = { source: `@${tag}`, code: r.json.versionCode, name: r.json.versionName };
          bestCode = r.json.versionCode;
        }
      }
    }
    if (best === null || bestCode <= CLIENT_CODE) {
      const api = await fetchJson(`https://api.github.com/repos/${REPO}/releases/latest`);
      if (api.ok) {
        const m = /^\s*versionCode:\s*(\d+)\s*$/m.exec(api.json.body ?? "");
        const asset = (api.json.assets ?? []).find((a) => String(a.name ?? "").endsWith(".apk"));
        if (m && asset && Number(m[1]) > bestCode) {
          best = { source: "github-api", code: Number(m[1]), name: (api.json.tag_name ?? "").replace(/^v/, "") };
        }
      }
    }
    return best;
  })();
  if (v71514Verdict) {
    console.log(
      `  ${v71514Verdict.code > CLIENT_CODE ? "✓" : "✗"} v7.15.14 客户端（86400，多源合流取最大）：` +
        `最佳 ${v71514Verdict.name}/${v71514Verdict.code} @ ${v71514Verdict.source}` +
        (v71514Verdict.code > CLIENT_CODE ? " → 发现更新" : " → 未发现"),
    );
  } else {
    console.log(`  ⚠ v7.15.14 客户端：全部源不可达`);
  }

  // ── v8.0.9 客户端（新逻辑：全链合流 + @tag + API）──
  const v809Sources = [
    ...V807_SOURCES.slice(0, 4),
    `https://fastly.jsdelivr.net/gh/${REPO}@main/update/latest.json`,
    `https://testingcf.jsdelivr.net/gh/${REPO}@main/update/latest.json`,
    `https://cdn.jsdelivr.net/gh/${KILO}@main/update/latest.json`,
    `https://raw.githubusercontent.com/${REPO}/main/update/latest.json`,
    `https://raw.githubusercontent.com/${KILO}@main/update/latest.json`,
  ];
  let v809Best = null;
  let v809BestCode = CLIENT_CODE;
  for (const url of v809Sources) {
    const r = await fetchJson(url);
    if (r.ok && validManifest(r.json) && r.json.versionCode > v809BestCode) {
      v809Best = { source: url, code: r.json.versionCode, name: r.json.versionName };
      v809BestCode = r.json.versionCode;
    }
  }
  if (!v809Best) {
    for (const tag of candidateTags("8.0.9")) {
      for (const host of ["gcore.jsdelivr.net", "cdn.jsdelivr.net"]) {
        const r = await fetchJson(`https://${host}/gh/${REPO}@${tag}/update/latest.json`);
        if (r.ok && validManifest(r.json) && r.json.versionCode > v809BestCode) {
          v809Best = { source: `@${tag}`, code: r.json.versionCode, name: r.json.versionName };
          v809BestCode = r.json.versionCode;
          break;
        }
      }
    }
  }
  if (!v809Best) {
    const api = await fetchJson(`https://api.github.com/repos/${REPO}/releases/latest`);
    if (api.ok) {
      const m = /^\s*versionCode:\s*(\d+)\s*$/m.exec(api.json.body ?? "");
      const asset = (api.json.assets ?? []).find((a) => String(a.name ?? "").endsWith(".apk"));
      if (m && asset && Number(m[1]) > v809BestCode) {
        v809Best = { source: "github-api", code: Number(m[1]), name: (api.json.tag_name ?? "").replace(/^v/, "") };
      }
    }
  }
  if (v809Best) {
    console.log(
      `  ${v809Best.code > CLIENT_CODE ? "✓" : "✗"} v8.0.9 客户端（多源合流+@tag+API）：` +
        `最佳 ${v809Best.name}/${v809Best.code} @ ${v809Best.source.replace("https://", "")}` +
        (v809Best.code > CLIENT_CODE ? " → 发现更新" : " → 暂无更新"),
    );
  } else {
    console.log(`  ✗ v8.0.9 客户端：全部源不可达（如实报检查失败，绝不误报「已是最新」）`);
  }
}

main();
