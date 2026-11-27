#!/usr/bin/env bun
/**
 * parse-feedback-reports.mjs — 用户反馈 Issue 日志结构化解析 + 最新统计分析
 *
 * 用途（Task P2-stats）：
 *   1. 分页拉取仓库全部（open/closed/all）Issue（含 body）
 *   2. 解析每份引擎日志报告正文（v8.0.8+ 六节格式 / v8.0.7 自动+手动 / v7.x / v5.x 等历史格式）
 *   3. 每条触发/未命中/失败记录映射到 13 类失败原因枚举（现有数据可区分粒度，不可区分者显式置 null）
 *   4. 输出 reports/issue-parse/{issues.jsonl, records.jsonl, stats.md}
 *
 * 用法：
 *   bun scripts/parse-feedback-reports.mjs [--since-days N] [--state open|closed|all] [--out DIR] [--cache FILE] [--limit N]
 *     --since-days  仅统计最近 N 天创建的 issue（默认 30；0 = 不限）
 *     --state       issue 状态过滤（默认 open）
 *     --out         输出目录（默认 reports/issue-parse）
 *     --cache       调试用：从本地 JSON 数组文件读取 issue（字段 number/title/state/created_at/body），
 *                   不访问 GitHub API；格式与 GitHub issues API 兼容
 *     --limit       调试用：最多解析 N 个 issue
 *
 * 认证：复用 ./gh-token.mjs 三级令牌链（环境变量 → local-gh-token → 金库）。
 * 诚实统计原则：解析不出的字段一律 null；宁可留空不编造；解析失败的 issue 记入数据质量说明。
 */
import { mkdirSync, writeFileSync, existsSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(SCRIPT_DIR, "..");
import { getGhToken } from "./gh-token.mjs";

// ---------------------------------------------------------------------------
// CLI 参数
// ---------------------------------------------------------------------------
const args = process.argv.slice(2);
function argVal(name, def) {
  const i = args.indexOf(name);
  if (i === -1) return def;
  const v = args[i + 1];
  return v === undefined || String(v).startsWith("--") ? def : v;
}
const OPT = {
  sinceDays: Number(argVal("--since-days", "30")),
  state: argVal("--state", "open"),
  out: argVal("--out", join(REPO_ROOT, "reports", "issue-parse")),
  cache: argVal("--cache", null),
  limit: Number(argVal("--limit", "0")),
};

// ---------------------------------------------------------------------------
// 13 类失败原因枚举（产品下一版分类标准；解析器按现有数据可区分粒度映射）
// ---------------------------------------------------------------------------
const CATEGORIES = [
  { id: 1, key: "no_matched_rule", label: "订阅/规则未命中", statuses: ["not_matched"], distinguishable: true },
  { id: 2, key: "rule_disabled", label: "规则被用户禁用", statuses: [], distinguishable: false },
  { id: 3, key: "app_not_in_subscription", label: "应用不在任何订阅", statuses: ["app_no_rules"], distinguishable: true },
  { id: 4, key: "subscription_incompatible", label: "订阅格式/版本不兼容", statuses: [], distinguishable: false },
  { id: 5, key: "activity_mismatch", label: "规则存在但 Activity 不匹配", statuses: ["no_rules_for_activity"], distinguishable: true },
  { id: 6, key: "node_not_found", label: "节点不可达", statuses: ["node_unavailable", "query_root_unavailable"], distinguishable: true },
  { id: 7, key: "node_not_visible", label: "节点不可见/离屏", statuses: [], distinguishable: false },
  { id: 8, key: "node_stale", label: "节点过期/前台冲突", statuses: ["foreground_conflict", "query_child_unavailable"], distinguishable: true },
  { id: 9, key: "countdown_pending", label: "倒计时/延迟等待中", statuses: ["MatchDelay", "ActionDelay"], distinguishable: true },
  { id: 10, key: "action_failed", label: "动作执行失败", statuses: ["action_failed"], distinguishable: true },
  { id: 11, key: "action_no_effect", label: "动作执行但无效果", statuses: [], distinguishable: false },
  { id: 12, key: "rate_limited", label: "限流/冷却", statuses: ["Cooldown", "ActionLimitReached"], distinguishable: true },
  { id: 13, key: "other_exception", label: "其他异常", statuses: ["event_consume_failed", "subscription_update_failed"], distinguishable: true },
];
// 原始状态 → 类别 id（未列入映射表的状态保留原始计数，不计入 13 类，见 stats 数据质量说明）
const STATUS_TO_CAT = {};
for (const c of CATEGORIES) for (const s of c.statuses) STATUS_TO_CAT[s] = c.id;

// ---------------------------------------------------------------------------
// 工具函数
// ---------------------------------------------------------------------------
const CST_OFFSET_MS = 8 * 3600 * 1000; // 报告时间戳为东八区本地时间

function cstYearAndDate(isoCreated) {
  const d = new Date(new Date(isoCreated).getTime() + CST_OFFSET_MS);
  return { year: d.getUTCFullYear(), ymd: d.toISOString().slice(0, 10) };
}
function toIsoTs(year, md, hms, ymdFallback) {
  // md: "MM-dd" 或 null；hms: "HH:mm:ss"
  try {
    if (md) {
      return new Date(`${year}-${md}T${hms}+08:00`).toISOString();
    }
    // 旧格式日志只有时分秒，日期按 issue 创建时刻（东八区）近似
    return new Date(`${ymdFallback}T${hms}+08:00`).toISOString();
  } catch {
    return null;
  }
}
function pct(n, d) {
  if (!d) return "0.0%";
  return ((n / d) * 100).toFixed(1) + "%";
}
function fmtStatuses(st) {
  return Object.entries(st)
    .sort((a, b) => b[1] - a[1])
    .map(([k, v]) => `${k}×${v}`)
    .join(" · ");
}

// ---------------------------------------------------------------------------
// GitHub 拉取（分页，含 body）
// ---------------------------------------------------------------------------
async function fetchIssues(state) {
  const token = getGhToken();
  if (!token) {
    console.error("✗ 缺少 GitHub Token（gh-token.mjs 三级链均未命中）");
    process.exit(1);
  }
  const H = {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "User-Agent": "skip-ad-parse-feedback-reports",
  };
  const all = [];
  let page = 1;
  while (true) {
    const url = `https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues?state=${state}&per_page=100&page=${page}`;
    const r = await fetch(url, { headers: H });
    if (!r.ok) {
      console.error(`✗ GitHub API ${r.status} @ ${url}`);
      process.exit(1);
    }
    const arr = await r.json();
    if (!Array.isArray(arr) || arr.length === 0) break;
    for (const i of arr) {
      if (i.pull_request) continue; // 排除 PR
      all.push({
        number: i.number, title: i.title || "", state: i.state,
        created_at: i.created_at, body: i.body || "",
      });
    }
    if (arr.length < 100) break;
    page++;
  }
  return all;
}

// ---------------------------------------------------------------------------
// 报告格式识别 + 报告头解析
// ---------------------------------------------------------------------------
function detectFormat(body, title) {
  if (title.includes("[反馈]") || body.includes("## 问题描述")) return "non_report";
  if (/^## 1\. 基本环境/m.test(body)) return "v8_six_section";
  if (/^### 设备与应用/m.test(body)) return "v8_auto_gkd";
  if (/^### 设备信息/m.test(body) && /^### 引擎日志/m.test(body)) return "v8_e2e";
  if (/^## 引擎日志/m.test(body)) return "v5_stream";
  if (/^## 设备/m.test(body) && /^## 状态/m.test(body)) return "v7_status";
  if (body.trim().length === 0) return "empty";
  return "unknown";
}

function extractVersion(title, body) {
  const m =
    title.match(/\[v(\d+\.\d+\.\d+)\]/) ||
    body.match(/(?:应用|应用版本|版本)[：:]\s*[「]?[\w ]*?v(\d+\.\d+\.\d+)/) ||
    body.match(/v(\d+\.\d+\.\d+) \(\d+\)/) ||
    body.match(/^### 版本：v(\d+\.\d+\.\d+)/m) ||
    title.match(/v(\d+\.\d+\.\d+)/); // 兜底：标题裸版本号（如 E2E 测试 issue）
  if (!m) return null;
  return m[1];
}
function versionFamily(v) {
  if (!v) return null;
  const [a, b, c] = v.split(".");
  // v8 系按小版本单列（v8.0.7/v8.0.8/v8.0.9 分别统计）；v7 及以下按 minor 归族
  if (Number(a) >= 8) return `v${a}.${b}.${c}`;
  return `v${a}.${b}.x`;
}

function detectUploadChannel(title, body) {
  if (/上传通道[：:]\s*手动上传/.test(body) || /·\s*手动上传/.test(body) || title.includes("手动上传") || title.includes("手动通道")) return "manual";
  if (/上传通道[：:]\s*8小时自动/.test(body) || /8h\s*自动提交/.test(body) || title.includes("8 小时自动")) return "8h_auto";
  if (/^自动日志/.test(title)) return "gkd_auto"; // v8.0.7 gkd 构建自动上传通道（标题「自动日志 …」）
  return null;
}
function detectEngineChannel(body, version) {
  if (/通道[：:]?\s*gkd/.test(body) || /GKD 内核/.test(body) || /「.+」触发「.+」规则/.test(body)) return "gkd";
  if (version) {
    const major = Number(version.split(".")[0]);
    if (major >= 8) return "gkd"; // v8.x 系列均为 gkd 内核
    return "native"; // v7.x/v5.x 为自研原生引擎
  }
  return null;
}

function parseDeviceInfo(body) {
  // v8: "- 设备: samsung SM-S9180 (dm3q), Android 16 (SDK 36)"
  let m = body.match(/^-\s*设备[：:]\s*(.+?),\s*Android\s*(\d+)(?:\s*\(SDK\s*(\d+)\))?/m);
  if (m) return { model: m[1].trim(), android: m[2], sdk: m[3] || null };
  // v7/v5: "## 设备" 下第一行 "- OPPO PLT140 · Android 16 · SDK 36"（或无 SDK）
  const sec = body.split(/^## 设备\s*$/m)[1];
  if (sec) {
    const first = (sec.split("\n").find((l) => l.trim().startsWith("-")) || "").replace(/^-\s*/, "").trim();
    if (first) {
      const mm = first.match(/^(.+?)\s*·\s*Android\s*(\d+)(?:\s*·\s*SDK\s*(\d+))?/);
      if (mm) return { model: mm[1].trim(), android: mm[2], sdk: mm[3] || null };
      return { model: first.split("·")[0].trim(), android: null, sdk: null };
    }
  }
  return { model: null, android: null, sdk: null };
}

function collectSubscriptions(body) {
  const subs = [];
  // v8 自动日志："### 订阅状态" 下的 "- [本地] 本地订阅 v0 (id=-2)"
  const sec = body.split(/^### 订阅状态\s*$/m)[1];
  if (sec) {
    for (const line of sec.split("\n")) {
      const m = line.match(/^-\s*\[(本地|远程)\]\s*(.+?)\s+v(\d+)\s*\(id=(-?\d+)\)/);
      if (m) subs.push({ scope: m[1], name: m[2], version: m[3], id: m[4] });
    }
  }
  // v8 六节 §3 内嵌订阅启用行（从记录里也会统计，这里只做订阅清单收集）
  for (const m of body.matchAll(/内嵌订阅已启用[：:]\s*(.+?)（id=(\d+) · (\d+) 款应用）/g)) {
    subs.push({ scope: "内嵌", name: m[1], id: m[2], apps: m[3] });
  }
  // v7:"订阅：内置 124 款/124 条（出手 5） · 远程「id667的GKD订阅🚀」935 款/2890 条（命中 28）"
  const subLines = body.match(/^-\s*订阅[：:].*$/m);
  return { subscriptions: subs, subscriptionRaw: subLines ? subLines[0].replace(/^-\s*/, "") : null };
}

function parseHeaderStats(body) {
  const out = {};
  let m;
  if ((m = body.match(/^-\s*真实引擎（?GKD 内核）?[：:]\s*(.+)$/m))) out.engineRaw = m[1];
  if ((m = body.match(/^-\s*真实引擎[：:]\s*(.+)$/m))) out.engineRaw = m[1];
  if ((m = body.match(/秒跳\s*(\d+)/))) out.instaSkip = Number(m[1]);
  if ((m = body.match(/弹窗关闭\s*(\d+)/))) out.popupClose = Number(m[1]);
  if ((m = body.match(/返回键\s*(\d+)/))) out.backKey = Number(m[1]);
  if ((m = body.match(/拦截总数\(累计\)\s*(\d+)/))) out.blockTotal = Number(m[1]);
  if ((m = body.match(/^-\s*触发总数[：:]\s*(\d+)/m))) out.triggerTotal = Number(m[1]);
  // v8 §5 详细窗口统计
  if ((m = body.match(/动作成功\s*(\d+)\s*·\s*动作失败\s*(\d+)\s*·\s*匹配未命中汇总\s*(\d+)\s*·\s*记录共\s*(\d+)\s*条/))) {
    out.window = { actionOk: Number(m[1]), actionFail: Number(m[2]), missSummary: Number(m[3]), records: Number(m[4]) };
  }
  // v8 §5 应用战绩（成功对照）："- 应用战绩: 喜马拉雅(com.ximalaya.ting.android) 5 次"
  out.successByApp = [];
  for (const mm of body.matchAll(/^-\s*应用战绩[：:]\s*(.+?)\(([\w.]+)\)\s*(\d+)\s*次/gm)) {
    out.successByApp.push({ app: mm[1], pkg: mm[2], count: Number(mm[3]) });
  }
  // v7 头部 Top5 原样保留（三数字语义未文档化，不猜测）
  if ((m = body.match(/^-\s*应用战绩 Top5[：:]\s*(.+)$/m))) out.top5Raw = m[1];
  return out;
}

// ---------------------------------------------------------------------------
// 单条日志记录解析
// 已知类型：
//   v8 gkd 引擎记录（六节/手动）：
//     - [MM-dd HH:mm:ss](ℹ️)? ⚡ 执行动作（App · 组 · …）：clickNode · 成功 {…result,durationMs…}
//     - [MM-dd HH:mm:ss](ℹ️)? ❓ 规则未命中（pkg）：N 轮评估未命中 · 组[订阅86·开屏广告 / …] · not_matched×N · … {…statuses…}
//     - [MM-dd HH:mm:ss](ℹ️)? 🧭 应用暂无规则（pkg）：…
//     - [MM-dd HH:mm:ss](ℹ️)? 🧩/🔌/… 信息行
//   v8.0.7 gkd 触发行：- [HH:mm:ss] 「易校园」触发「开屏广告-全局」规则
//   v8.0.7 gkd 自动通道触发行：- 11-27 16:54:17 com.tencent.qqlive group=0 rule=3
//   v7 原生引擎：- [HH:mm:ss] ❓ 开屏未命中（app）树 N 节点 · 窗口 X · 订阅[…] ——上传日志…
//                - [HH:mm:ss] 🔍 插屏未命中（app · Activity · 窗龄 Nms）：…
//                - [HH:mm:ss] ⚡ 已点击跳过（app）：… / ✓ 复验通过（app）：…
//                - [HH:mm:ss] ⚠️ 热区误触观察（app）：… / 其他 ⚠️
//   v5 学习事件流：- 2026/11/18 02:07:44 · 政务通 · guard_skip · interstitial · 53ms
// ---------------------------------------------------------------------------
const RE_TS_LINE = /^[-–]\s*\[(?:(\d{2}-\d{2})\s+)?(\d{2}:\d{2}:\d{2})\]\s*(.*)$/;
const RE_AUTO_TRIGGER = /^[-–]\s*(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})\s+([\w.]+)\s+group=(\d+)\s+rule=(\d+)$/;
const RE_V5_EVENT = /^[-–]\s*(\d{4}\/\d{2}\/\d{2}\s+\d{2}:\d{2}:\d{2})\s*·\s*(.+?)\s*·\s*(\w+)\s*·\s*(\w+)\s*·\s*(\d+)ms$/;
const LEVEL_MARKS = ["ℹ️", "⚠️", "❗", "❌"];
const KNOWN_ICONS = [
  "⚡", "❓", "❌", "🧭", "🧩", "🔌", "🔋", "⏱", "⏳", "🪟", "🔄", "🛡", "🎯", "🤝",
  "🚫", "🚧", "📐", "🔗", "🔓", "🔒", "📍", "🔁", "✓", "🔍", "🧠", "💡", "⏸", "🚨",
  "🔔", "📨", "▶", // 服务/观察类图标（非事件，归 info）
];

function parseLogLine(rawLine) {
  // 返回 null = 非日志记录行；返回 {…} = 记录
  const line = rawLine.trim();
  if (!line.startsWith("-") && !line.startsWith("–")) return null;

  // v8 自动通道触发行：- 11-27 16:54:17 pkg group=N rule=N
  let m = line.match(RE_AUTO_TRIGGER);
  if (m) {
    return {
      md: m[1], hms: m[2], icon: null, kind: "gkd_trigger_compact",
      app: null, pkg: m[3], group: `group=${m[4]}`, ruleIdx: m[5],
      outcome: "triggered", category: null, note: "自动通道触发行（无显式 result 字段，按『触发即出手』口径计成功）",
      msg: line.replace(/^[-–]\s*/, ""), json: null,
    };
  }
  // v5 学习事件流：- 2026/11/18 02:07:44 · 政务通 · guard_skip · interstitial · 53ms
  m = line.match(RE_V5_EVENT);
  if (m) {
    const kind = m[3];
    const ok = ["instant_skip", "preempt_block", "guard_skip"].includes(kind);
    return {
      isoTs: new Date(m[1].replace(/\//g, "-") + "+08:00").toISOString(), icon: null,
      kind: "v5_event", app: m[2], pkg: null, event: kind, scene: m[4], durationMs: Number(m[5]),
      outcome: ok ? "success" : "unknown", category: null,
      msg: line.replace(/^[-–]\s*/, ""), json: null,
    };
  }
  // 时间戳行：- [MM-dd HH:mm:ss] …
  m = line.match(RE_TS_LINE);
  if (!m) return null;
  const md = m[1] || null, hms = m[2];
  let rest = m[3].trim();
  let level = null;
  for (const lv of LEVEL_MARKS) {
    if (rest.startsWith(lv)) { level = lv; rest = rest.slice(lv.length).trim(); break; }
  }
  let icon = null;
  for (const ic of KNOWN_ICONS) {
    if (rest.startsWith(ic)) { icon = ic; rest = rest.slice(ic.length).trim(); break; }
  }
  if (icon === null && rest.startsWith("⚠")) { icon = "⚠️"; rest = rest.replace(/^⚠️?\s*/, ""); }
  const bodyTxt = rest;
  const base = { md, hms, level, icon, msg: bodyTxt, json: null };

  // 尾部 JSON（可能被报告生成端截断，尽力解析）
  const jm = bodyTxt.match(/\{.*\}\s*$/);
  let tailJson = null, jsonTruncated = false;
  if (jm) {
    try { tailJson = JSON.parse(jm[0]); } catch { jsonTruncated = true; }
  }

  // —— v8 gkd：⚡ 执行动作（App · 组 · …）：clickNode · 成功/失败 {…}
  m = bodyTxt.match(/^执行动作（(.+?)）：(\w+)\s*·\s*(成功|失败)/);
  if (m) {
    const parts = m[1].split("·").map((s) => s.trim()).filter(Boolean);
    const result = tailJson && typeof tailJson.result === "boolean" ? tailJson.result : m[3] === "成功";
    return {
      ...base, kind: "action", app: parts[0] || null,
      group: parts.slice(1).join(" · ") || null, pkg: null,
      actionType: m[2] || (tailJson && tailJson.actionType) || null,
      result, durationMs: tailJson && tailJson.durationMs != null ? tailJson.durationMs : null,
      outcome: result ? "success" : "fail",
      category: result ? null : 10,
      json: tailJson, jsonTruncated,
    };
  }
  // —— v8 gkd：❓ 规则未命中（pkg）：N 轮评估未命中 · 组[...] · 状态×N …（statuses 从消息文本与尾部 JSON 双路提取）
  m = bodyTxt.match(/^规则未命中（([\w.]+)）：(\d+)\s*轮评估未命中/);
  if (m) {
    const pkg = m[1], scans = Number(m[2]);
    let statuses = {};
    if (tailJson && tailJson.statuses && typeof tailJson.statuses === "object") {
      statuses = tailJson.statuses;
    } else {
      for (const sm of bodyTxt.matchAll(/([A-Za-z_]+)×(\d+)/g)) statuses[sm[1]] = Number(sm[2]);
    }
    const gm = bodyTxt.match(/组\[([^\]]*)\]/);
    const groups = gm ? gm[1].split("/").map((s) => s.trim()).filter(Boolean) : [];
    return {
      ...base, kind: "not_matched_summary", pkg, app: null, scans, statuses,
      groups, group: groups[0] || null,
      outcome: "miss", category: null, // 类别按 statuses 逐状态加权映射（见 mapStatuses）
      json: tailJson, jsonTruncated,
    };
  }
  // —— v8 gkd：🧭 应用暂无规则（pkg）
  m = bodyTxt.match(/^应用暂无规则（([\w.]+)）/);
  if (m) {
    return { ...base, kind: "app_no_rules", pkg: m[1], app: null, outcome: "miss", category: 3, json: tailJson, jsonTruncated };
  }
  // —— v8.0.7 gkd 触发：「易校园」触发「开屏广告-全局」规则
  m = bodyTxt.match(/^「(.+?)」触发「(.+?)」规则/);
  if (m) {
    return { ...base, kind: "gkd_trigger", app: m[1], group: m[2], pkg: null, result: true, outcome: "success", category: null, json: tailJson, jsonTruncated };
  }
  // —— v7 原生：❓ 开屏未命中（app）树 N 节点 · 窗口 X · …订阅[...]
  m = bodyTxt.match(/^开屏未命中（(.+?)）/);
  if (m) {
    const wm = bodyTxt.match(/窗口\s*(\S+)/);
    const sub = bodyTxt.match(/订阅\[([^\]]*)\]/);
    return {
      ...base, kind: "splash_miss", app: m[1], pkg: null,
      windowActivity: wm ? wm[1] : null, subCoverage: sub ? sub[1] : null,
      outcome: "miss", category: 1, // 原生引擎未命中无状态分布，按记录级计 1 单元 no_matched_rule
      json: null,
    };
  }
  // —— v7 原生：🔍 插屏未命中（app · Activity · 窗龄 …）
  m = bodyTxt.match(/^插屏未命中（(.+?)）/);
  if (m) {
    const parts = m[1].split("·").map((s) => s.trim()).filter(Boolean);
    return {
      ...base, kind: "interstitial_miss", app: parts[0], pkg: null,
      windowActivity: parts[1] || null,
      outcome: "miss", category: 1, json: null,
    };
  }
  // —— v7/v5 原生：⚡ 已点击跳过（app）
  m = bodyTxt.match(/^已点击跳过（(.+?)）/);
  if (m) {
    return { ...base, kind: "action_click", app: m[1], pkg: null, result: true, outcome: "success", category: null, json: null };
  }
  // —— ✓ 复验通过（app）
  m = bodyTxt.match(/^复验通过（(.+?)）/);
  if (m) {
    return { ...base, kind: "verify_pass", app: m[1], pkg: null, outcome: "success", category: null, json: null };
  }
  // —— ⚠️ 热区误触观察（app）（误触风险观察，供误触分析，不计失败类别）
  m = bodyTxt.match(/^热区误触观察（(.+?)）/);
  if (m) {
    return { ...base, kind: "mis_touch_watch", app: m[1], pkg: null, outcome: "observation", category: null, json: null };
  }
  // —— ⚠️ 无障碍登记被移除（服务级警告）
  if (level === "⚠️" || icon === "⚠️") {
    return { ...base, kind: "warn", pkg: null, app: null, outcome: "warn", category: null, json: null };
  }
  // —— ❌ 错误级
  if (level === "❌" || icon === "❌") {
    return { ...base, kind: "error", pkg: null, app: null, outcome: "fail", category: 13, json: tailJson, jsonTruncated };
  }
  // —— 其余信息行（🧩 订阅就绪 / 🔌 服务连接 / 📐 分级扫描 / 🔔📨▶ / 无图标「服务已启动 · 已学习…」）
  return {
    ...base, kind: "info", outcome: "info", category: null, json: tailJson, jsonTruncated,
    recognized: icon !== null,
    iconUnknown: icon !== null && !KNOWN_ICONS.includes(icon),
  };
}

// ---------------------------------------------------------------------------
// 单个 Issue 解析
// ---------------------------------------------------------------------------
function parseIssue(iss) {
  const { year, ymd } = cstYearAndDate(iss.created_at);
  const format = detectFormat(iss.body, iss.title);
  const version = extractVersion(iss.title, iss.body);
  const device = parseDeviceInfo(iss.body);
  const subs = collectSubscriptions(iss.body);
  const header = parseHeaderStats(iss.body);
  const isE2E = /E2E/i.test(iss.title);

  const out = {
    number: iss.number,
    title: iss.title,
    state: iss.state,
    createdAt: iss.created_at,
    format,
    isE2E,
    version,
    versionFamily: versionFamily(version),
    uploadChannel: detectUploadChannel(iss.title, iss.body),
    engineChannel: detectEngineChannel(iss.body, version),
    device: device.model,
    android: device.android,
    sdk: device.sdk,
    subscriptions: subs.subscriptions,
    subscriptionRaw: subs.subscriptionRaw,
    header,
    parseStatus: "ok",
    parseIssues: [],
    counts: null,
    categoryUnits: null,
  };

  if (format === "non_report") {
    out.parseStatus = "skipped";
    out.parseIssues.push("非引擎日志报告（用户反馈/问题描述类，无六节日志结构）");
    out.counts = emptyCounts();
    out.categoryUnits = emptyCats();
    return { issue: out, records: [] };
  }
  if (format === "empty") {
    out.parseStatus = "skipped";
    out.parseIssues.push("正文为空");
    out.counts = emptyCounts();
    out.categoryUnits = emptyCats();
    return { issue: out, records: [] };
  }
  if (format === "unknown") {
    out.parseStatus = "failed";
    out.parseIssues.push("未识别的报告格式（无已知节结构标记）");
    out.counts = emptyCounts();
    out.categoryUnits = emptyCats();
    return { issue: out, records: [] };
  }

  const records = [];
  const seen = new Set(); // v8 六节格式 §2/§3 双表去重：ts + 归一化消息前缀
  let unparsedLogLines = 0, totalLogLines = 0, infoPlainLines = 0, iconUnknownLines = 0;

  for (const raw of iss.body.split("\n")) {
    const t = raw.trim();
    if (!t.startsWith("-") && !t.startsWith("–")) continue;
    // 只处理「日志记录形态」的列表行（时间戳 / 自动触发行 / v5 事件流），其余是头信息
    const looksLikeLog =
      RE_TS_LINE.test(t) || RE_AUTO_TRIGGER.test(t) || RE_V5_EVENT.test(t);
    if (!looksLikeLog) continue;
    totalLogLines++;
    const rec = parseLogLine(t);
    if (!rec) { unparsedLogLines++; continue; }
    if (rec.kind === "info" && !rec.recognized) {
      // 无图标信息行（如「服务已启动 · v7.8.0 …」「已学习：…」）：服务级信息，无事件语义，
      // 不作为记录保留，也不计入解析失败
      infoPlainLines++;
      continue;
    }
    if (rec.iconUnknown) iconUnknownLines++;
    // 时间戳归一
    if (!rec.isoTs) {
      rec.isoTs = toIsoTs(year, rec.md, rec.hms, ymd);
      rec.tsApproximate = !rec.md; // 旧格式只有时分秒，日期为近似
    }
    // 去重（仅限已证实存在双表重复列示的格式）：
    //   · v8.0.8/v8.0.9 六节格式：§2（最新触发记录）⊂ §3（详细执行记录），同文案重复 → 按 ts+消息前缀去重
    //   · v8.0.7 手动格式：「引擎日志（『App』触发…）」与「详细引擎日志（⚡ 执行动作…）」跨节
    //     以不同文案重复列示同一事件（#147 实证）→ 按 ts+App+组 事件键去重
    //   其余格式均为单一时间戳列表来源，不做去重（同一秒内重复行视为真实重复事件保留）
    const isSix = format === "v8_six_section";
    const hasDetailDup = /详细引擎日志/.test(iss.body) && (rec.kind === "action" || rec.kind === "gkd_trigger");
    if (isSix || hasDetailDup) {
      const normGroup = (rec.group || "").replace(/\s*·\s*规则$/, "");
      let key;
      if (hasDetailDup) key = `${rec.isoTs}|${rec.app || rec.pkg || ""}|${normGroup}`;
      else key = `${rec.isoTs}|${(rec.msg || "").replace(/\s*\{.*$/, "").slice(0, 80)}`;
      if (seen.has(key)) continue;
      seen.add(key);
    }
    records.push(rec);
  }

  // pkg 反查：v8 动作记录只有 App 显示名，用 §5 应用战绩行（App(pkg) N 次）补 pkg
  const nameToPkg = {};
  for (const s of out.header.successByApp || []) nameToPkg[s.app] = s.pkg;
  for (const r of records) {
    if (!r.pkg && r.app && nameToPkg[r.app]) r.pkg = nameToPkg[r.app];
  }

  // 计数与类别加权
  const counts = emptyCounts();
  const cats = emptyCats();
  for (const r of records) {
    switch (r.kind) {
      case "action":
      case "gkd_trigger":
      case "gkd_trigger_compact":
      case "action_click":
      case "verify_pass":
        if (r.outcome === "success") counts.success++;
        else if (r.outcome === "fail") { counts.actionFail++; cats[10]++; }
        else counts.triggerNoResult++;
        break;
      case "v5_event":
        if (r.outcome === "success") counts.success++;
        else counts.unknownOutcome++;
        break;
      case "not_matched_summary":
        counts.missSummary++;
        counts.missScans += r.scans || 0;
        for (const [st, n] of Object.entries(r.statuses || {})) {
          counts.rawStatuses[st] = (counts.rawStatuses[st] || 0) + n;
          const c = STATUS_TO_CAT[st];
          if (c) { cats[c] += n; counts.mappedUnits += n; }
          else counts.unmappedStatusUnits += n;
        }
        break;
      case "splash_miss":
      case "interstitial_miss":
        counts.missLegacy++;
        cats[1] += 1;
        counts.mappedUnits += 1;
        break;
      case "app_no_rules":
        counts.appNoRules++;
        cats[3] += 1;
        counts.mappedUnits += 1;
        break;
      case "mis_touch_watch":
        counts.misTouchWatch++;
        break;
      case "warn":
        counts.warn++;
        break;
      case "error":
        counts.error++;
        cats[13] += 1;
        counts.mappedUnits += 1;
        break;
      default:
        counts.info++;
    }
  }
  counts.records = records.length;
  counts.totalLogLines = totalLogLines;
  counts.unparsedLogLines = unparsedLogLines;
  counts.infoPlainLines = infoPlainLines;
  counts.iconUnknownLines = iconUnknownLines;
  counts.failureRecords = counts.missSummary + counts.missLegacy + counts.actionFail + counts.error;

  if (unparsedLogLines > 0) {
    out.parseStatus = "partial";
    out.parseIssues.push(`${unparsedLogLines}/${totalLogLines} 条带时间戳的日志行无法归类（保留计数于 counts.unparsedLogLines）`);
  }
  out.counts = counts;
  out.categoryUnits = cats;
  return { issue: out, records };
}

function emptyCounts() {
  return {
    records: 0, success: 0, triggerNoResult: 0, unknownOutcome: 0,
    actionFail: 0, missSummary: 0, missScans: 0, missLegacy: 0,
    appNoRules: 0, misTouchWatch: 0, warn: 0, error: 0, info: 0,
    mappedUnits: 0, unmappedStatusUnits: 0, rawStatuses: {},
    failureRecords: 0, totalLogLines: 0, unparsedLogLines: 0,
    infoPlainLines: 0, iconUnknownLines: 0,
  };
}
function emptyCats() {
  const o = {};
  for (const c of CATEGORIES) o[c.id] = 0;
  return o;
}

// ---------------------------------------------------------------------------
// 统计汇总（stats.md）
// ---------------------------------------------------------------------------
function buildStats(issues, records, opt) {
  const now = new Date();
  const L = [];
  const totalIssues = issues.length;
  const reportIssues = issues.filter((i) => i.parseStatus !== "skipped");
  const failed = issues.filter((i) => i.parseStatus === "failed");
  const partial = issues.filter((i) => i.parseStatus === "partial");
  const skipped = issues.filter((i) => i.parseStatus === "skipped");
  const e2e = issues.filter((i) => i.isE2E);

  const totUnits = issues.reduce((a, i) => a + Object.values(i.categoryUnits || {}).reduce((x, y) => x + y, 0), 0);

  L.push(`# Issue 引擎日志解析统计（issue-parse）`);
  L.push("");
  L.push(`- 生成时间：${now.toISOString()}`);
  L.push(`- 参数：--state ${opt.state} · --since-days ${opt.sinceDays === 0 ? "不限" : opt.sinceDays} · 数据源 xiaobaiba999/Skip-Ad issues API`);
  L.push(`- 样本：${totalIssues} 个 issue（其中引擎日志报告 ${reportIssues.length} · 非报告/跳过 ${skipped.length} · 解析失败 ${failed.length} · 部分解析 ${partial.length}）`);
  L.push(`- 记录总数：${records.length} 条（含成功/未命中/失败/观察/信息；v8 系双表重复列示已按事件键去重，见第 7 节）`);
  L.push("");

  // ---- 1. 失败类别分布 ----
  L.push(`## 1. 失败类别分布（13 类）`);
  L.push("");
  L.push(`计数口径：**评估单元** = v8 gkd 引擎未命中汇总记录按其 statuses 逐状态加权（如 not_matched×77 → 77 单元）+ 原生引擎未命中/动作失败按记录计 1 单元。不可区分的类别显式标注，不参与编造。`);
  L.push("");
  L.push(`| # | 类别 key | 含义 | 评估单元 | 占比 | 数据可区分性 |`);
  L.push(`|---|---|---|---|---|---|`);
  for (const c of CATEGORIES) {
    const n = issues.reduce((a, i) => a + (i.categoryUnits?.[c.id] || 0), 0);
    L.push(`| ${c.id} | ${c.key} | ${c.label} | ${n} | ${pct(n, totUnits)} | ${c.distinguishable ? "现有日志可区分" : "数据不可区分（置 null）"} |`);
  }
  L.push(`| — | 合计 | — | **${totUnits}** | 100% | — |`);
  // 原始状态分布
  const rawAgg = {};
  for (const i of issues) for (const [k, v] of Object.entries(i.counts?.rawStatuses || {})) rawAgg[k] = (rawAgg[k] || 0) + v;
  const rawSorted = Object.entries(rawAgg).sort((a, b) => b[1] - a[1]);
  L.push("");
  L.push(`### 1.1 原始状态分布（未映射 13 类前）`);
  L.push("");
  L.push(`| 原始状态 | 单元数 | 映射类别 |`);
  L.push(`|---|---|---|`);
  for (const [k, v] of rawSorted) {
    const c = STATUS_TO_CAT[k];
    L.push(`| ${k} | ${v} | ${c ? `#${c} ${CATEGORIES[c - 1].key}` : "未映射（见数据质量说明）"} |`);
  }
  L.push("");

  // ---- 2. 未命中 App Top 20 ----
  // 未命中记录聚合（v8: pkg；v7 原生: app 短名）
  const missAgg = new Map(); // key -> {id, type, missRecords, missScans, issues:Set, devices:Set, apps:Set}
  for (const r of records) {
    if (r.kind !== "not_matched_summary" && r.kind !== "splash_miss" && r.kind !== "interstitial_miss") continue;
    const key = r.pkg || r.app || "（未知）";
    if (!missAgg.has(key)) missAgg.set(key, { id: key, isPkg: !!r.pkg, missRecords: 0, missScans: 0, issues: new Set(), devices: new Set() });
    const e = missAgg.get(key);
    e.missRecords++;
    e.missScans += r.scans || (r.kind === "not_matched_summary" ? 0 : 1);
    e.issues.add(r.issue);
    if (r.device) e.devices.add(r.device.replace(/\s*\([^)]*\)\s*$/, "").trim());
  }
  const missTop = [...missAgg.values()].sort((a, b) => b.missScans - a.missScans || b.missRecords - a.missRecords).slice(0, 20);
  L.push(`## 2. 未命中 App 排行 Top 20`);
  L.push("");
  L.push(`排序：未命中评估轮数（v8 gkd 每条未命中汇总含 N 轮评估）优先，其次未命中记录数。v8 gkd 记录为完整包名；v7 原生引擎记录为进程短名（两种口径分行列示，不做合并）。`);
  L.push("");
  L.push(`| # | App 标识 | 口径 | 未命中记录数 | 未命中评估轮数 | 涉及 issue | 涉及设备 |`);
  L.push(`|---|---|---|---|---|---|---|`);
  missTop.forEach((e, idx) => {
    L.push(`| ${idx + 1} | ${e.id} | ${e.isPkg ? "完整包名" : "进程短名"} | ${e.missRecords} | ${e.missScans} | ${e.issues.size}（${[...e.issues].sort((a, b) => a - b).slice(0, 6).map((n) => "#" + n).join(" ")}${e.issues.size > 6 ? " …" : ""}） | ${[...e.devices].slice(0, 4).join(" / ")}${e.devices.size > 4 ? " …" : ""} |`);
  });
  L.push("");

  // ---- 3. 动作失败清单 ----
  L.push(`## 3. 动作失败清单（result:false / · 失败）`);
  L.push("");
  const fails = records.filter((r) => r.kind === "action" && r.outcome === "fail" || r.kind === "error");
  if (fails.length === 0) {
    L.push(`**当前样本内无动作失败记录**（0 条 result:false / ❌ 记录）。v8.0.7/v8.0.9 各报告 §5 统计摘要亦均为「动作失败 0」。`);
  } else {
    const agg = new Map();
    for (const r of fails) {
      const key = `${r.app || r.pkg || "（未知）"}×${r.group || r.actionType || "-"}`;
      agg.set(key, (agg.get(key) || 0) + 1);
    }
    L.push(`| App × 规则/组 | 失败次数 |`);
    L.push(`|---|---|`);
    for (const [k, v] of [...agg.entries()].sort((a, b) => b[1] - a[1])) L.push(`| ${k} | ${v} |`);
  }
  L.push("");

  // ---- 4. 版本分布 + 设备×Android 交叉 ----
  L.push(`## 4. 版本分布与设备/系统交叉`);
  L.push("");
  L.push(`未命中率口径 = 未命中记录数 ÷（未命中记录数 + 出手成功记录数）。出手成功 = ⚡ 执行动作 result:true / 「App」触发「组」规则 / 自动通道触发行 / 原生 ⚡ 已点击跳过 / ✓ 复验通过 / v5 事件流 instant_skip 等。未命中记录含 v8 gkd 未命中汇总与原生开屏/插屏未命中（不含 🧭 应用暂无规则）。`);
  L.push("");
  L.push(`| 版本族 | issue 数 | 记录数 | 出手成功 | 未命中记录 | 未命中率(记录口径) | 未命中评估轮 | 🧭应用暂无规则 |`);
  L.push(`|---|---|---|---|---|---|---|---|`);
  const byFam = new Map();
  for (const i of issues) {
    if (i.parseStatus === "skipped") continue;
    const f = i.versionFamily || "未知版本";
    if (!byFam.has(f)) byFam.set(f, { issues: 0, success: 0, miss: 0, missScans: 0, appNoRules: 0 });
    const e = byFam.get(f);
    e.issues++;
    e.success += i.counts.success + i.counts.triggerNoResult;
    e.miss += i.counts.missSummary + i.counts.missLegacy;
    e.missScans += i.counts.missScans;
    e.appNoRules += i.counts.appNoRules;
  }
  const famOrder = ["v8.0.9", "v8.0.8", "v8.0.7", "v7.15.x", "v7.9.x", "v7.8.x", "v7.5.x", "v7.4.x", "v7.3.x", "v7.2.x", "v7.1.x", "v7.0.x", "v6.1.x", "v6.0.x", "v5.9.x", "v5.8.x", "v5.7.x", "v5.6.x", "v5.5.x", "v5.3.x", "v5.2.x", "v5.1.x", "未知版本"];
  for (const f of famOrder) {
    if (!byFam.has(f)) continue;
    const e = byFam.get(f);
    L.push(`| ${f} | ${e.issues} | ${e.success + e.miss} | ${e.success} | ${e.miss} | ${pct(e.miss, e.miss + e.success)} | ${e.missScans} | ${e.appNoRules} |`);
  }
  for (const [f, e] of byFam) {
    if (famOrder.includes(f)) continue;
    L.push(`| ${f} | ${e.issues} | ${e.success + e.miss} | ${e.success} | ${e.miss} | ${pct(e.miss, e.miss + e.success)} | ${e.missScans} | ${e.appNoRules} |`);
  }
  L.push("");
  L.push(`注：v8.0.7 两类报告格式（手动 GKD 内核 / 自动通道）均**不含未命中事件记录**，其「0 未命中」为格式所限，不可解读为无失败；v8.0.8 仅 E2E 存档无有效记录。未命中率仅 v8.0.9（六节格式）与 v7.x/v5.x（原生格式）之间可比较：gkd 内核 v8.0.9 未命中率 79.4% 主要来自「评估轮」级重试窗口（每条未命中汇总含最多数百轮评估），与原生引擎记录级口径不同源。`);
  L.push("");
  L.push(`### 4.1 设备 × Android 版本交叉分布（issue 数；型号已去除机型代号括注）`);
  L.push("");
  const devAgg = new Map(); // "device|android" -> count
  for (const i of issues) {
    if (i.parseStatus === "skipped" || !i.device) continue;
    const norm = i.device.replace(/\s*\([^)]*\)\s*$/, "").trim(); // 去机型代号 (dali)/(dm3q) 等
    const k = `${norm} | Android ${i.android || "?"}`;
    devAgg.set(k, (devAgg.get(k) || 0) + 1);
  }
  L.push(`| 设备型号 | Android | issue 数 |`);
  L.push(`|---|---|---|`);
  for (const [k, v] of [...devAgg.entries()].sort((a, b) => b[1] - a[1])) L.push(`| ${k} | ${v} |`);
  L.push("");

  // ---- 5. 成功战绩对照 ----
  L.push(`## 5. 成功战绩对照（有成功记录的 App）`);
  L.push("");
  L.push(`用于区分「订阅没覆盖」与「规则失效」：出现在本表 = 订阅/引擎在该 App 上有成功出手；仅出现在第 2 节 = 疑似订阅未覆盖或规则失效。`);
  L.push("");
  const succAgg = new Map(); // key -> {id, isPkg, n, issues:Set}
  for (const r of records) {
    const okKinds = ["action", "gkd_trigger", "gkd_trigger_compact", "action_click", "verify_pass", "v5_event"];
    if (!okKinds.includes(r.kind)) continue;
    if (r.kind === "action" && r.outcome === "fail") continue;
    const key = r.pkg || r.app || "（未知）";
    if (!succAgg.has(key)) succAgg.set(key, { id: key, isPkg: !!r.pkg, n: 0, issues: new Set() });
    const e = succAgg.get(key);
    e.n++;
    e.issues.add(r.issue);
  }
  const succTop = [...succAgg.values()].sort((a, b) => b.n - a.n);
  L.push(`| # | App 标识 | 口径 | 成功出手记录数 | 涉及 issue |`);
  L.push(`|---|---|---|---|---|`);
  succTop.slice(0, 30).forEach((e, idx) => {
    L.push(`| ${idx + 1} | ${e.id} | ${e.isPkg ? "完整包名" : "App名/进程短名"} | ${e.n} | ${e.issues.size}（${[...e.issues].sort((a, b) => a - b).slice(0, 6).map((n) => "#" + n).join(" ")}${e.issues.size > 6 ? " …" : ""}） |`);
  });
  if (succTop.length > 30) L.push(`| … | （其余 ${succTop.length - 30} 个 App，见 records.jsonl） | | | |`);
  L.push("");
  L.push(`报告头战绩补充（进程本次启动累计，口径见各报告 §6）：${issues.filter((i) => i.header?.blockTotal != null).map((i) => `#${i.number} 拦截累计 ${i.header.blockTotal}`).join(" · ") || "（无）"}`);
  L.push("");

  // ---- 6. gkd 订阅通道统计 ----
  L.push(`## 6. gkd 订阅通道专属统计（v8.0.7+ gkd 内核）`);
  L.push("");
  const gkdIssues = issues.filter((i) => i.engineChannel === "gkd" && i.parseStatus !== "skipped");
  const gkdRecords = records.filter((r) => r.engineChannel === "gkd");
  L.push(`- gkd 内核 issue 数：${gkdIssues.length}（v8.0.7 / v8.0.8 / v8.0.9 系列）`);
  L.push(`- gkd 内核记录数：${gkdRecords.length}（触发 ${gkdRecords.filter((r) => ["action", "gkd_trigger", "gkd_trigger_compact"].includes(r.kind)).length} · 未命中汇总 ${gkdRecords.filter((r) => r.kind === "not_matched_summary").length} · 应用暂无规则 ${gkdRecords.filter((r) => r.kind === "app_no_rules").length}）`);
  L.push("");
  L.push(`### 6.1 gkd 通道 App 出手统计（触发 vs 未命中，按 App）`);
  L.push("");
  const gApp = new Map();
  for (const r of gkdRecords) {
    if (!["action", "gkd_trigger", "gkd_trigger_compact", "not_matched_summary", "app_no_rules"].includes(r.kind)) continue;
    const key = r.pkg || r.app || "（未知）";
    if (!gApp.has(key)) gApp.set(key, { id: key, isPkg: !!r.pkg, trig: 0, miss: 0, missScans: 0, noRules: 0, issues: new Set() });
    const e = gApp.get(key);
    e.issues.add(r.issue);
    if (["action", "gkd_trigger", "gkd_trigger_compact"].includes(r.kind)) e.trig++;
    else if (r.kind === "not_matched_summary") { e.miss++; e.missScans += r.scans || 0; }
    else e.noRules++;
  }
  const gArr = [...gApp.values()].sort((a, b) => b.missScans - a.missScans || b.trig - a.trig);
  L.push(`| # | App 标识 | gkd 触发(成功出手) | 未命中汇总记录 | 未命中评估轮 | 🧭应用暂无规则 | 涉及 issue |`);
  L.push(`|---|---|---|---|---|---|---|`);
  gArr.slice(0, 25).forEach((e, idx) => {
    L.push(`| ${idx + 1} | ${e.id} | ${e.trig} | ${e.miss} | ${e.missScans} | ${e.noRules} | ${e.issues.size} |`);
  });
  if (gArr.length > 25) L.push(`| … | （其余 ${gArr.length - 25} 个 App） | | | | | |`);
  L.push("");
  L.push(`### 6.2 未命中记录的订阅来源分布（组前缀）`);
  L.push("");
  const subSrc = new Map();
  for (const r of gkdRecords) {
    if (r.kind !== "not_matched_summary") continue;
    const srcs = (r.groups || []).length ? [...new Set((r.groups || []).map((g) => g.split("·")[0]))] : ["（组信息缺失）"];
    for (const s of srcs) subSrc.set(s, (subSrc.get(s) || 0) + 1);
  }
  L.push(`| 订阅来源（组前缀） | 未命中汇总记录数（一条记录可含多订阅组，跨订阅重复计数） |`);
  L.push(`|---|---|`);
  for (const [k, v] of [...subSrc.entries()].sort((a, b) => b[1] - a[1])) L.push(`| ${k} | ${v} |`);
  L.push("");
  L.push(`注：动作触发记录（⚡ 执行动作 / 「App」触发「组」规则）的组名不带订阅前缀，故订阅级成功归因不可区分（数据所限，见数据质量说明）。`);
  L.push("");

  // ---- 7. 数据质量 ----
  L.push(`## 7. 数据质量说明`);
  L.push("");
  const unmappedUnits = issues.reduce((a, i) => a + (i.counts?.unmappedStatusUnits || 0), 0);
  const unparsedLines = issues.reduce((a, i) => a + (i.counts?.unparsedLogLines || 0), 0);
  const totalLines = issues.reduce((a, i) => a + (i.counts?.totalLogLines || 0), 0);
  const infoPlain = issues.reduce((a, i) => a + (i.counts?.infoPlainLines || 0), 0);
  const iconUnknown = issues.reduce((a, i) => a + (i.counts?.iconUnknownLines || 0), 0);
  L.push(`- 完整解析：${issues.filter((i) => i.parseStatus === "ok").length} / 部分解析（存在无法归类的日志行）：${partial.length} / 解析失败（格式未识别）：${failed.length} / 跳过（非日志报告）：${skipped.length}`);
  if (skipped.length) L.push(`  - 跳过清单：${skipped.map((i) => `#${i.number}（${i.parseIssues[0] || "非报告"}）`).join(" · ")}`);
  if (failed.length) L.push(`  - 失败清单：${failed.map((i) => `#${i.number}（${i.parseIssues[0] || "?"}）`).join(" · ")}`);
  if (partial.length) L.push(`  - 部分解析清单：${partial.map((i) => `#${i.number}（${i.counts.unparsedLogLines} 行）`).join(" · ")}`);
  L.push(`- E2E 测试 issue（标题含 E2E-TEST，保留在样本中但已标记）：${e2e.map((i) => "#" + i.number).join(" · ") || "无"}`);
  L.push(`- 带时间戳日志行归类率：${totalLines - unparsedLines}/${totalLines}（已识别但无事件语义的服务级信息行 ${infoPlain} 条（「服务已启动/已学习…」）与观察类图标行 ${iconUnknown} 条（🔔/📨/▶）不参与失败/成功统计）`);
  L.push(`- 13 类未覆盖的原始状态：${Object.entries(rawAgg).filter(([k]) => !STATUS_TO_CAT[k]).map(([k, v]) => `${k}×${v}`).join(" · ") || "无"} —— 合计 ${unmappedUnits} 评估单元未计入 13 类分布（MatchTimeout = 匹配总超时，任务 13 类清单未收录，保留原始计数待产品定夺）`);
  L.push(`- 不可区分类别（#2 规则禁用 / #4 订阅不兼容 / #7 节点不可见 / #11 动作无效果）：现有日志无对应字段，全部置 null，未做任何估算。`);
  L.push(`- v8 自动上传通道（标题「自动日志 …」）的触发行（\`pkg group=N rule=N\`）无显式 result 字段，按「触发即出手」计成功，已在记录级 note 标注。`);
  L.push(`- v7 原生引擎日志只有时分秒，日期按 issue 创建时刻（东八区）近似；v8 六节记录含 MM-dd，年份按 issue 创建年份推断。`);
  L.push(`- 原生引擎未命中（❓ 开屏未命中 / 🔍 插屏未命中）无状态分布，按记录级 1 单元计入 #1；其 App 标识为进程短名（如 ting.android），与 v8 完整包名不合并。`);
  L.push(`- v8 系报告存在双表重复列示，已按事件键去重：v8.0.9 六节格式 §2⊂§3 同文案重复（ts+消息前缀去重）；v8.0.7 手动格式「引擎日志（『App』触发）」与「详细引擎日志（⚡ 执行动作）」跨节不同文案重复列示同一事件（#147 实证，按 ts+App+组 去重）。其余格式为单一时间戳列表来源，同一秒内重复行按真实重复事件保留（如自动通道 4 个 App 存在同秒同规则双触发）。`);
  return L.join("\n");
}

// ---------------------------------------------------------------------------
// 主流程
// ---------------------------------------------------------------------------
async function main() {
  let rawIssues;
  if (OPT.cache) {
    rawIssues = JSON.parse(readFileSync(OPT.cache, "utf-8"));
    console.log(`✓ 从缓存读取 ${rawIssues.length} 个 issue（${OPT.cache}）`);
  } else {
    rawIssues = await fetchIssues(OPT.state);
    console.log(`✓ 拉取 ${rawIssues.length} 个 issue（state=${OPT.state}）`);
  }

  // --since-days 过滤（按创建时间）
  let pool = rawIssues;
  if (OPT.sinceDays > 0) {
    const cutoff = Date.now() - OPT.sinceDays * 86400_000;
    pool = pool.filter((i) => new Date(i.created_at).getTime() >= cutoff);
    console.log(`✓ --since-days ${OPT.sinceDays} 过滤后 ${pool.length} 个`);
  }
  // state 过滤（缓存模式下兜底）
  if (OPT.cache && OPT.state !== "all") pool = pool.filter((i) => i.state === OPT.state);
  if (OPT.limit > 0) pool = pool.slice(0, OPT.limit);

  // 解析
  const issues = [];
  const records = [];
  for (const iss of pool) {
    const { issue, records: recs } = parseIssue(iss);
    // 记录级附加上下文（自包含，便于下游分析）
    for (const r of recs) {
      r.issue = issue.number;
      r.version = issue.version;
      r.versionFamily = issue.versionFamily;
      r.uploadChannel = issue.uploadChannel;
      r.engineChannel = issue.engineChannel;
      r.device = issue.device;
      r.android = issue.android;
      r.format = issue.format;
      // 压缩字段：删除内部字段
      delete r.md; delete r.hms; delete r.level; delete r.recognized;
      records.push(r);
    }
    issues.push(issue);
  }

  // 输出
  mkdirSync(OPT.out, { recursive: true });
  const issuesPath = join(OPT.out, "issues.jsonl");
  const recordsPath = join(OPT.out, "records.jsonl");
  const statsPath = join(OPT.out, "stats.md");
  writeFileSync(issuesPath, issues.map((i) => JSON.stringify(i)).join("\n") + "\n");
  writeFileSync(recordsPath, records.map((r) => JSON.stringify(r)).join("\n") + "\n");
  writeFileSync(statsPath, buildStats(issues, records, OPT) + "\n");

  console.log(`✓ 输出：${issuesPath}（${issues.length} 行）`);
  console.log(`✓ 输出：${recordsPath}（${records.length} 行）`);
  console.log(`✓ 输出：${statsPath}`);
  const q = {
    ok: issues.filter((i) => i.parseStatus === "ok").length,
    partial: issues.filter((i) => i.parseStatus === "partial").length,
    failed: issues.filter((i) => i.parseStatus === "failed").length,
    skipped: issues.filter((i) => i.parseStatus === "skipped").length,
  };
  console.log(`  数据质量：完整 ${q.ok} · 部分 ${q.partial} · 失败 ${q.failed} · 跳过 ${q.skipped}`);
}

await main();
