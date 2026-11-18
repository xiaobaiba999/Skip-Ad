/**
 * 统一规则 DSL · v5.4.0 多源规则统一调度核心（GKD 100% 兼容）
 * ─────────────────────────────────────────────────────────────────────
 * 纯函数库：不 import db、不依赖任何服务端 API，可在客户端直接使用。
 *
 * 【GKD 订阅格式兼容要点】
 *  · GKD 订阅是 TS 文件，含 `let categories: Array<Category>`；
 *    Category { id, name, groups }；Group { id, name, key?, matchTime?,
 *    actionMaximum?, rules }；Rule { matches: string | string[], action?,
 *    activityNames? }。
 *  · 快照选择器：属性操作符 `=`（等于）`!=`（不等）`*=`（包含）`^=`（开头）
 *    `$=`（结尾）`~=`（正则）；属性 text/vid/desc/name/clickable 等（旧语法
 *    `id` 视同 `vid`）；关系符 `<<n`（n 层祖先）`<`（父）`>`（子）`>>`/`>>n`
 *    （后代）`-`/`-[n]`（前兄弟）`+`/`+[n]`（后兄弟）`&&`（同节点属性与）。
 *  · 解析策略：先走「宽松 JSON」路径（去注释 → 单引号/反引号转双引号 →
 *    无引号键补引号 → 去尾逗号 → JSON.parse）整体解析 categories 数组；
 *    失败则降级为逐条正则提取 `matches: '...'`——任何一条目无法识别都只
 *    降级 warning 跳过，绝不中断整个导入。
 *  · 简化编译：matches 中的关系选择器只取第一个属性段编译为 DslMatcher
 *    （其余关系段记 warning，标注「简化编译：关系段仅在真机端生效」）；
 *    `@点击目标` 标记、activityNames 快照限定、anyMatches 多选一等无法在
 *    Web 端完整执行的语义一律记 warning 不报错。
 *
 * 【匹配引擎】
 *  一次 DFS 采集全树节点并缓存属性，多 matcher 复用同一份缓存；
 *  复杂度 O(N×M)（N=节点数，M=matcher 数），作为李跳跳式探针前置的补充：
 *  探针负责廉价预筛（findAccessibilityNodeInfosByText），本引擎负责全量
 *  确定性匹配与逐级 trace 溯源（演练场可视化 / 真机日志共用同一结论）。
 */

// ═══════════════ 类型定义 ═══════════════

/** 模拟无障碍节点树（坐标为 0~1000 归一化值，与真机屏幕百分比同构） */
export interface SimNode {
  id: string;
  name?: string; // className
  vid?: string; // viewId resource-id
  text?: string;
  desc?: string;
  x: number;
  y: number;
  w: number;
  h: number; // 0~1000 归一化坐标
  clickable?: boolean;
  scrollable?: boolean;
  editable?: boolean;
  children: SimNode[];
}

export type DslField = "text" | "vid" | "desc" | "name";
export type DslOp = "eq" | "neq" | "contains" | "startsWith" | "endsWith" | "regex";

export interface DslMatcher {
  field: DslField;
  op: DslOp;
  value: string;
  /** 面积/位置守卫（防误触） */
  guard?: { areaMaxPct?: number; corner?: "tl" | "tr" | "bl" | "br" };
}

export interface DslRule {
  id: string;
  name: string;
  source: "preset" | "gkd" | "learned" | "custom";
  appName: string; // "*" 全局
  adType: string; // 对齐 instant-engine AdType 字符串
  matchers: DslMatcher[]; // AND 关系
  action: string; // instant_skip | popup_close | halfsheet_close | decline_tap | preroll_skip | ...
  priority: number; // 调度分（越大越先）
  enabled: boolean;
}

export interface MatchStep {
  step: number;
  label: string;
  passed: boolean;
  detail: string;
}

export interface MatchTrace {
  ruleId: string;
  ruleName: string;
  source: string;
  matched: boolean;
  hitNodeIds: string[];
  steps: MatchStep[];
  nodeCount: number;
}

export interface TreeMatchResult {
  traces: MatchTrace[]; // 按调度顺序
  winner: MatchTrace | null; // 首个全匹配的规则
  order: { id: string; name: string; score: number; reason: string }[]; // 调度顺序说明
}

export interface GkdParseResult {
  ok: boolean;
  ruleCount: number;
  groupCount: number;
  rules: DslRule[];
  errors: string[];
  warnings: string[];
}

/** GKD 导入附加信息：parseGkdSubscription 产出的规则对象会附带这两个字段
 *  （结构上仍是 DslRule，订阅落库路由按需读取，不进入 DSL 公共契约） */
export interface GkdRuleMeta {
  /** 订阅来源标识：订阅名#组名 */
  gkdRef?: string;
  /** 原始 matches 选择器文本（审计 / 回放用） */
  rawSelector?: string;
}

// ═══════════════ 基础工具（引号/括号感知扫描） ═══════════════

/** 按分隔符切分顶层段（分隔符出现在 [] 或引号内时不切分） */
function splitTopLevel(s: string, sep: string): string[] {
  const parts: string[] = [];
  let cur = "";
  let depth = 0;
  let quote: string | null = null;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (quote) {
      cur += c;
      if (c === "\\") {
        cur += s[i + 1] ?? "";
        i++;
      } else if (c === quote) {
        quote = null;
      }
      continue;
    }
    if (c === '"' || c === "'" || c === "`") {
      quote = c;
      cur += c;
      continue;
    }
    if (c === "[") depth++;
    else if (c === "]") depth = Math.max(0, depth - 1);
    if (depth === 0 && c === sep) {
      parts.push(cur);
      cur = "";
      continue;
    }
    cur += c;
  }
  parts.push(cur);
  return parts;
}

/** 关系符（出现在顶层时）：<<n / >>n / < / > / +[n] / -[n] */
const REL_RE = /^(<<\d*|>>\d*|<|>|\+(?:\[\d+\])?|-(?:\[\d+\])?)/;

interface RelSegment {
  text: string;
  rel: string | null; // 与前一段的关系符（首段为 null）
  clickTarget: boolean; // @ 点击目标标记
}

/** 把快照选择器按顶层关系符拆段（`-`/`+` 仅在紧跟 `]` 后视为关系符，避免误切裸标识） */
function splitRelations(sel: string): RelSegment[] {
  const segs: RelSegment[] = [];
  let cur = "";
  let curRel: string | null = null;
  let curAt = false;
  let depth = 0;
  let quote: string | null = null;
  let lastNonWs = "";
  for (let i = 0; i < sel.length; ) {
    const c = sel[i];
    if (quote) {
      cur += c;
      if (c === "\\") {
        cur += sel[i + 1] ?? "";
        i += 2;
        continue;
      }
      if (c === quote) quote = null;
      lastNonWs = c;
      i++;
      continue;
    }
    if (c === '"' || c === "'" || c === "`") {
      quote = c;
      cur += c;
      lastNonWs = c;
      i++;
      continue;
    }
    if (c === "[") {
      depth++;
      cur += c;
      lastNonWs = c;
      i++;
      continue;
    }
    if (c === "]") {
      depth--;
      cur += c;
      lastNonWs = c;
      i++;
      continue;
    }
    if (depth === 0) {
      if (c === "@") {
        curAt = true;
        i++;
        continue;
      }
      const m = REL_RE.exec(sel.slice(i));
      const isRelation =
        m !== null &&
        (m[1].startsWith("<") ||
          m[1].startsWith(">") ||
          ((m[1] === "-" || m[1].startsWith("-[") || m[1] === "+" || m[1].startsWith("+[")) && lastNonWs === "]"));
      if (isRelation && m) {
        segs.push({ text: cur.trim(), rel: curRel, clickTarget: curAt });
        cur = "";
        curRel = m[1];
        curAt = false;
        i += m[1].length;
        while (i < sel.length && /\s/.test(sel[i])) i++;
        continue;
      }
    }
    if (!/\s/.test(c)) lastNonWs = c;
    cur += c;
    i++;
  }
  segs.push({ text: cur.trim(), rel: curRel, clickTarget: curAt });
  return segs.filter((s) => s.text !== "");
}

/** 是否含顶层关系段（用于「简化编译」warning） */
function hasRelation(sel: string): boolean {
  try {
    return splitRelations(sel).some((s) => s.rel !== null);
  } catch {
    return false;
  }
}

/** 提取一段内所有顶层 [...] 属性组的内容 */
function extractBracketGroups(text: string): string[] {
  const groups: string[] = [];
  let depth = 0;
  let cur = "";
  let quote: string | null = null;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quote) {
      cur += c;
      if (c === "\\") {
        cur += text[i + 1] ?? "";
        i++;
      } else if (c === quote) {
        quote = null;
      }
      continue;
    }
    if (c === '"' || c === "'" || c === "`") {
      quote = c;
      if (depth > 0) cur += c;
      continue;
    }
    if (c === "[") {
      depth++;
      if (depth === 1) {
        cur = "";
        continue;
      }
    } else if (c === "]") {
      depth = Math.max(0, depth - 1);
      if (depth === 0) {
        groups.push(cur);
        continue;
      }
    }
    if (depth > 0) cur += c;
  }
  return groups;
}

/** 组内按顶层 `&&` 拆分属性表达式 */
function splitAndExprs(g: string): string[] {
  const parts: string[] = [];
  let cur = "";
  let quote: string | null = null;
  for (let i = 0; i < g.length; i++) {
    const c = g[i];
    if (quote) {
      cur += c;
      if (c === "\\") {
        cur += g[i + 1] ?? "";
        i++;
      } else if (c === quote) {
        quote = null;
      }
      continue;
    }
    if (c === '"' || c === "'" || c === "`") {
      quote = c;
      cur += c;
      continue;
    }
    if (c === "&" && g[i + 1] === "&") {
      parts.push(cur);
      cur = "";
      i++;
      continue;
    }
    cur += c;
  }
  parts.push(cur);
  return parts.map((p) => p.trim()).filter(Boolean);
}

const OP_SYMBOL: Record<DslOp, string> = {
  eq: "=",
  neq: "!=",
  contains: "*=",
  startsWith: "^=",
  endsWith: "$=",
  regex: "~=",
};
const SYMBOL_OP: Record<string, DslOp> = {
  "=": "eq",
  "!=": "neq",
  "*=": "contains",
  "^=": "startsWith",
  "$=": "endsWith",
  "~=": "regex",
};
const FIELD_ALIAS: Record<string, DslField> = {
  text: "text",
  vid: "vid",
  id: "vid", // GKD 旧语法 id 等同 vid
  desc: "desc",
  name: "name",
};

/** 去引号 + 基本反转义 */
function unquote(v: string): string {
  const t = v.trim();
  if (t.length >= 2 && ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith("'") && t.endsWith("'")) || (t.startsWith("`") && t.endsWith("`")))) {
    const inner = t.slice(1, -1);
    return inner
      .replace(/\\(['"`])/g, "$1")
      .replace(/\\n/g, "\n")
      .replace(/\\t/g, "\t")
      .replace(/\\\\/g, "\\");
  }
  return t;
}

const ATTR_EXPR_RE = /^\s*([A-Za-z_][\w-]*)\s*(?:(!=|\*=|\^=|\$=|\~=|=)\s*(.*?)\s*)?$/;

/** 单条属性表达式 → DslMatcher（不支持的字段返回 null） */
function parseAttrExpr(e: string): DslMatcher | null {
  const m = ATTR_EXPR_RE.exec(e);
  if (!m) return null;
  const attr = m[1];
  const field = FIELD_ALIAS[attr];
  if (!field) return null; // clickable/width 等属性 Web 端不支持
  if (!m[2]) {
    // 裸属性（存在性判断）→ 不等于空串
    return { field, op: "neq", value: "" };
  }
  const op = SYMBOL_OP[m[2]];
  if (!op) return null;
  return { field, op, value: unquote(m[3] ?? "") };
}

/** 一个节点段（可能含多个 [...] 组或裸类名标识）→ 匹配器集合 */
function parseSegmentAttrs(text: string): { matchers: DslMatcher[]; unsupported: string[] } {
  const matchers: DslMatcher[] = [];
  const unsupported: string[] = [];
  const t = text.trim();
  if (!t) return { matchers, unsupported };
  if (!t.includes("[")) {
    // 裸标识（如 ImageView / @Button）→ 按 className 等价匹配
    if (/^[A-Za-z_$][\w.$]*$/.test(t)) matchers.push({ field: "name", op: "eq", value: t });
    return { matchers, unsupported };
  }
  for (const g of extractBracketGroups(t)) {
    for (const e of splitAndExprs(g)) {
      const mm = parseAttrExpr(e);
      if (mm) matchers.push(mm);
      else unsupported.push(e);
    }
  }
  return { matchers, unsupported };
}

// ═══════════════ 选择器解析（导出） ═══════════════

/**
 * 解析单条 GKD 快照选择器。
 * 完整支持：`[vid="btn_skip"]`、`[text*="跳过"]`、`[name="android.widget.TextView" && text~="跳过|Skip"]`；
 * 关系符 `<<n` `<` `>` `>>` `-` `+`：简化编译——优先取 @ 点击目标段，其次取
 * 第一个能产出受支持属性（text/vid/desc/name）的段，其余关系段由调用方记 warning。
 */
export function parseSelector(sel: string): { ok: boolean; matchers: DslMatcher[]; error?: string } {
  const raw = (sel ?? "").trim();
  if (!raw) return { ok: false, matchers: [], error: "空选择器" };
  let segs: RelSegment[];
  try {
    segs = splitRelations(raw);
  } catch {
    return { ok: false, matchers: [], error: "关系符解析失败" };
  }
  if (!segs.length) return { ok: false, matchers: [], error: "未识别到任何节点段" };
  // @ 点击目标段优先（保住真机端被点击的节点语义），其余段按原顺序兜底
  const ordered = [...segs.filter((s) => s.clickTarget), ...segs.filter((s) => !s.clickTarget)];
  for (const s of ordered) {
    const r = parseSegmentAttrs(s.text);
    if (r.matchers.length > 0) return { ok: true, matchers: r.matchers };
  }
  return { ok: false, matchers: [], error: "无受支持的属性段（仅支持 text/vid/desc/name）" };
}

// ═══════════════ GKD 订阅解析 ═══════════════

interface GkdKRule {
  matches?: string | string[];
  anyMatches?: string[];
  action?: string;
  activityNames?: string[];
}
interface GkdGroup {
  id?: number;
  name?: string;
  key?: number;
  matchTime?: number;
  actionMaximum?: number;
  rules?: GkdKRule[];
}
interface GkdCategory {
  id?: number;
  name?: string;
  groups?: GkdGroup[];
}

/** 去掉 // 与 /* *\/ 注释（引号内不处理） */
function stripComments(s: string): string {
  let out = "";
  let quote: string | null = null;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (quote) {
      out += c;
      if (c === "\\") {
        out += s[i + 1] ?? "";
        i++;
      } else if (c === quote) {
        quote = null;
      }
      continue;
    }
    if (c === '"' || c === "'" || c === "`") {
      quote = c;
      out += c;
      continue;
    }
    if (c === "/" && s[i + 1] === "/") {
      while (i < s.length && s[i] !== "\n") i++;
      out += "\n";
      continue;
    }
    if (c === "/" && s[i + 1] === "*") {
      i += 2;
      while (i < s.length && !(s[i] === "*" && s[i + 1] === "/")) i++;
      i++;
      continue;
    }
    out += c;
  }
  return out;
}

/** 从 categories 赋值处提取数组字面量文本（引号/括号感知找匹配的右括号） */
function extractArrayAfter(src: string): string | null {
  const assignRe = /(?:let|var|const)\s+categories\s*(?::[^=\n]*?)?\s*=/;
  const m = assignRe.exec(src);
  const from = m ? src.indexOf("[", m.index + m[0].length) : -1;
  if (from < 0) return null;
  let depth = 0;
  let quote: string | null = null;
  for (let i = from; i < src.length; i++) {
    const c = src[i];
    if (quote) {
      if (c === "\\") i++;
      else if (c === quote) quote = null;
      continue;
    }
    if (c === '"' || c === "'" || c === "`") {
      quote = c;
      continue;
    }
    if (c === "[") depth++;
    else if (c === "]") {
      depth--;
      if (depth === 0) return src.slice(from, i + 1);
    }
  }
  return null;
}

/** TS 对象字面量 → JSON 文本（单引号/反引号转双引号、无引号键补引号、去尾逗号）；失败返回 null */
function tsLiteralToJson(s: string): string | null {
  let out = "";
  let quote: string | null = null;
  let lastSignificant = "";
  let i = 0;
  while (i < s.length) {
    const c = s[i];
    if (quote) {
      if (c === "\\") {
        const nxt = s[i + 1] ?? "";
        if (quote === "'") {
          // 单引号串内：\' 直接还原为 '；" 需转义；其余转义原样保留
          if (nxt === "'") out += "'";
          else if (nxt === '"') out += '\\"';
          else out += "\\" + nxt;
        } else {
          out += "\\" + nxt;
        }
        i += 2;
        continue;
      }
      if (c === quote) {
        out += '"';
        quote = null;
        i++;
        continue;
      }
      if (c === '"') {
        out += '\\"';
        i++;
        continue;
      }
      out += c;
      i++;
      continue;
    }
    if (c === "'" || c === '"' || c === "`") {
      quote = c;
      out += '"';
      i++;
      continue;
    }
    if (c === ",") {
      // 尾逗号：后面（跳过空白）是 } 或 ] 则丢弃
      let j = i + 1;
      while (j < s.length && /\s/.test(s[j])) j++;
      if (s[j] === "}" || s[j] === "]") {
        i++;
        continue;
      }
      out += c;
      lastSignificant = c;
      i++;
      continue;
    }
    // 无引号键补引号：上一有效字符为 { 或 , 且当前是标识符开头且后随冒号
    if ((lastSignificant === "{" || lastSignificant === ",") && /[A-Za-z_$]/.test(c)) {
      let j = i;
      let ident = "";
      while (j < s.length && /[A-Za-z0-9_$]/.test(s[j])) {
        ident += s[j];
        j++;
      }
      let k = j;
      while (k < s.length && /\s/.test(s[k])) k++;
      if (s[k] === ":") {
        out += '"' + ident + '"';
        lastSignificant = ":";
        i = j;
        continue;
      }
    }
    out += c;
    if (!/\s/.test(c)) lastSignificant = c;
    i++;
  }
  return quote ? null : out;
}

/** 宽松 JSON 整体解析 categories；失败返回 null（由调用方降级） */
function tryParseCategories(text: string): GkdCategory[] | null {
  try {
    const noComments = stripComments(text);
    const arrText = extractArrayAfter(noComments);
    if (!arrText) return null;
    const jsonText = tsLiteralToJson(arrText);
    if (!jsonText) return null;
    const data: unknown = JSON.parse(jsonText);
    if (!Array.isArray(data)) return null;
    return data as GkdCategory[];
  } catch {
    return null;
  }
}

function isStr(v: unknown): v is string {
  return typeof v === "string" && v.trim() !== "";
}

/** GKD 动作 → 引擎动作（点击类统一映射为代点跳过） */
function mapGkdAction(a: unknown): string {
  const s = typeof a === "string" ? a.trim().toLowerCase() : "";
  if (s === "back") return "back_fallback";
  return "instant_skip"; // click / clickNode / clickCenter / longClick 等
}

/** 按分类/分组名猜测广告类型（对齐 instant-engine AdType） */
function guessAdType(...names: string[]): string {
  const s = names.filter(Boolean).join("·");
  if (/开屏|启动/.test(s)) return "splash";
  if (/半屏/.test(s)) return "half_interstitial";
  if (/插屏|全屏/.test(s)) return "interstitial";
  if (/更新|升级/.test(s)) return "update_dialog";
  if (/悬浮/.test(s)) return "float_ball";
  if (/信息流/.test(s)) return "feed_native";
  if (/前贴|贴片/.test(s)) return "video_preroll";
  if (/摇一摇|摇晃|摇一搖/.test(s)) return "shake";
  if (/弹窗/.test(s)) return "popup";
  return "any";
}

function slug(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]+/g, "-").replace(/^-+|-+$/g, "") || "sub";
}

/**
 * 解析 GKD 订阅 TS 文本（100% 语法兼容；无法识别的条目降级 warning 跳过，绝不中断导入）。
 * @param text GKD 订阅文件全文
 * @param sourceName 订阅名（用于规则 id 与 gkdRef 前缀）
 */
export function parseGkdSubscription(text: string, sourceName?: string): GkdParseResult {
  const rules: DslRule[] = [];
  const warnings: string[] = [];
  const errors: string[] = [];
  const subName = sourceName?.trim() || "GKD";
  const subSlug = slug(subName);
  let groupCount = 0;

  if (!text || !text.trim()) {
    return { ok: false, ruleCount: 0, groupCount: 0, rules, errors: ["订阅内容为空"], warnings };
  }

  const cats = tryParseCategories(text);
  if (cats) {
    cats.forEach((cat, ci) => {
      const catName = isStr(cat?.name) ? cat.name : `分类-${ci + 1}`;
      const groups = Array.isArray(cat?.groups) ? cat.groups : [];
      groups.forEach((grp, gi) => {
        groupCount += 1;
        const grpName = isStr(grp?.name) ? grp.name : `分组-${gi + 1}`;
        // 「全局/通用/任意」分组 → 全局规则，否则视为应用专属
        const appName = /^(全局|通用|任意|所有)/.test(grpName) ? "*" : grpName;
        if (grp?.matchTime != null) warnings.push(`「${grpName}」matchTime=${String(grp.matchTime)} 时间窗仅在真机端生效`);
        if (grp?.actionMaximum != null) warnings.push(`「${grpName}」actionMaximum=${String(grp.actionMaximum)} 次数上限仅在真机端生效`);
        const kRules = Array.isArray(grp?.rules) ? grp.rules : [];
        kRules.forEach((kr, ri) => {
          const ruleName = `${catName}·${grpName} #${ri + 1}`;
          const sels: string[] = [];
          const mm = kr?.matches;
          if (typeof mm === "string") sels.push(mm);
          else if (Array.isArray(mm)) sels.push(...mm.filter(isStr));
          else warnings.push(`${ruleName} 缺少 matches 字段，已跳过`);
          const am = kr?.anyMatches;
          if (Array.isArray(am) && am.length > 0) {
            const first = am.find(isStr);
            warnings.push(`${ruleName} anyMatches 多选一简化为取首项（其余分支仅在真机端生效）`);
            if (first) sels.push(first);
          }
          if (Array.isArray(kr?.activityNames) && kr.activityNames.length > 0) {
            warnings.push(`${ruleName} activityNames=${kr.activityNames.join("/")} 快照限定仅在真机端生效`);
          }
          const matchers: DslMatcher[] = [];
          for (const s of sels) {
            if (s.includes("@")) warnings.push(`「${s}」@点击目标标记仅在真机端生效（Web 端按属性匹配）`);
            if (hasRelation(s)) warnings.push(`「${s}」简化编译：关系段仅在真机端生效（已取首段属性）`);
            const p = parseSelector(s);
            if (!p.ok) {
              warnings.push(`「${s}」解析失败：${p.error}（该条已跳过，不影响其余导入）`);
              continue;
            }
            matchers.push(...p.matchers);
          }
          if (!matchers.length) return;
          const r: DslRule & GkdRuleMeta = {
            id: `gkd-${subSlug}-${ci + 1}-${gi + 1}-${ri + 1}`,
            name: ruleName,
            source: "gkd",
            appName,
            adType: guessAdType(catName, grpName),
            matchers,
            action: mapGkdAction(kr?.action),
            priority: 50,
            enabled: true,
            gkdRef: `${subName}#${grpName}`,
            rawSelector: sels.join(" ; "),
          };
          rules.push(r);
        });
      });
    });
    if (!rules.length) warnings.push("结构解析成功但未产出任何可用规则（所有条目均被跳过）");
  } else {
    // 降级路径：整体结构解析失败 → 逐条正则提取 matches
    warnings.push("整体结构解析失败，已降级为逐条提取 matches 条目（规则名自动生成，分组信息缺失）");
    const re = /matches\s*:\s*(?:'((?:[^'\\]|\\.)*)'|"((?:[^"\\]|\\.)*)")/g;
    let m: RegExpExecArray | null;
    let i = 0;
    while ((m = re.exec(text)) !== null) {
      i += 1;
      const s = (m[1] ?? m[2] ?? "").replace(/\\'/g, "'").replace(/\\"/g, '"');
      if (s.includes("@")) warnings.push(`「${s}」@点击目标标记仅在真机端生效`);
      if (hasRelation(s)) warnings.push(`「${s}」简化编译：关系段仅在真机端生效（已取首段属性）`);
      const p = parseSelector(s);
      if (!p.ok) {
        warnings.push(`「${s}」解析失败：${p.error}（该条已跳过）`);
        continue;
      }
      const r: DslRule & GkdRuleMeta = {
        id: `gkd-${subSlug}-fallback-${i}`,
        name: `GKD 条目 #${i}`,
        source: "gkd",
        appName: "*",
        adType: "any",
        matchers: p.matchers,
        action: "instant_skip",
        priority: 50,
        enabled: true,
        gkdRef: `${subName}#降级提取`,
        rawSelector: s,
      };
      rules.push(r);
    }
    if (!rules.length) errors.push("未能从文本中识别任何 GKD 规则（categories 结构缺失且无 matches 条目）");
  }

  return { ok: rules.length > 0, ruleCount: rules.length, groupCount, rules, errors, warnings };
}

// ═══════════════ 人类友好 DSL 行 ═══════════════

function djb2(s: string): string {
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) >>> 0;
  return h.toString(36);
}

/** 应用 #guard:<matcherIndex>:areaMaxPct=35,corner=tr 守卫扩展段 */
function applyGuardSpec(spec: string, matchers: DslMatcher[]): void {
  const m = /^#guard:(\d+):(.+)$/.exec(spec.trim());
  if (!m) return;
  const idx = Number(m[1]);
  const target = matchers[idx];
  if (!target) return;
  const guard: { areaMaxPct?: number; corner?: "tl" | "tr" | "bl" | "br" } = { ...(target.guard ?? {}) };
  for (const kv of m[2].split(",")) {
    const eq = kv.indexOf("=");
    if (eq < 0) continue;
    const k = kv.slice(0, eq).trim();
    const v = kv.slice(eq + 1).trim();
    if (k === "areaMaxPct" && v !== "" && Number.isFinite(Number(v))) guard.areaMaxPct = Number(v);
    if (k === "corner" && ["tl", "tr", "bl", "br"].includes(v)) guard.corner = v as "tl" | "tr" | "bl" | "br";
  }
  target.guard = guard;
}

/**
 * 人类友好 DSL 行文本 → DslRule。
 * 行格式：`name | app | adType | action | selector1 ; selector2`（selector 为 GKD 快照选择器语法；
 * 拆分对 [] 与引号感知，selector 正则值中的 `|` 不会被误切）。
 * 扩展段 `#guard:<序号>:areaMaxPct=35,corner=tr` 用于行内携带防误触守卫（serializeDslRule 可往返）。
 */
export function compileDslLine(line: string): { ok: boolean; rule?: DslRule; error?: string } {
  const raw = (line ?? "").trim();
  if (!raw || raw.startsWith("#") || raw.startsWith("//")) {
    return { ok: false, error: "空行或注释行" };
  }
  const parts = splitTopLevel(raw, "|");
  if (parts.length < 5) {
    return { ok: false, error: `行格式应为「name | app | adType | action | selector1 ; selector2」，当前仅 ${parts.length} 段` };
  }
  const name = parts[0].trim() || "未命名规则";
  const appName = parts[1].trim() || "*";
  const adType = parts[2].trim() || "any";
  const action = parts[3].trim() || "instant_skip";
  const selText = parts.slice(4).join("|");
  const matchers: DslMatcher[] = [];
  for (const seg of splitTopLevel(selText, ";")) {
    const t = seg.trim();
    if (!t) continue;
    if (t.startsWith("#guard:")) {
      applyGuardSpec(t, matchers);
      continue;
    }
    const p = parseSelector(t);
    if (!p.ok) return { ok: false, error: `选择器解析失败「${t}」：${p.error}` };
    matchers.push(...p.matchers);
  }
  if (!matchers.length) return { ok: false, error: "未解析到任何匹配器（selector 不能为空）" };
  return {
    ok: true,
    rule: {
      id: `rule-${djb2(raw)}`,
      name,
      appName,
      adType,
      matchers,
      action,
      priority: 50,
      source: "custom",
      enabled: true,
    },
  };
}

function escapeSelValue(v: string): string {
  return v.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

/** 导出为行格式：`name | app | adType | action | sel1 ; sel2`（守卫以 #guard 扩展段往返） */
export function serializeDslRule(rule: DslRule): string {
  const sels = rule.matchers.map((m) => `[${m.field}${OP_SYMBOL[m.op]}"${escapeSelValue(m.value)}"]`);
  const guards = rule.matchers
    .map((m, i): string | null => {
      if (!m.guard) return null;
      const kv: string[] = [];
      if (m.guard.areaMaxPct != null) kv.push(`areaMaxPct=${m.guard.areaMaxPct}`);
      if (m.guard.corner) kv.push(`corner=${m.guard.corner}`);
      return kv.length ? `#guard:${i}:${kv.join(",")}` : null;
    })
    .filter((x): x is string => x !== null);
  const selPart = [...sels, ...guards].join(" ; ");
  return `${rule.name} | ${rule.appName} | ${rule.adType} | ${rule.action} | ${selPart}`;
}

// ═══════════════ 匹配引擎 ═══════════════

let nodeSeq = 0;

/** 便捷：构建 SimNode（自动生成 id） */
export function makeNode(partial: Partial<SimNode> & { children?: SimNode[] }): SimNode {
  nodeSeq += 1;
  return {
    id: partial.id ?? `node-${nodeSeq}`,
    name: partial.name,
    vid: partial.vid,
    text: partial.text,
    desc: partial.desc,
    x: partial.x ?? 0,
    y: partial.y ?? 0,
    w: partial.w ?? 200,
    h: partial.h ?? 80,
    clickable: partial.clickable,
    scrollable: partial.scrollable,
    editable: partial.editable,
    children: partial.children ?? [],
  };
}

/** 迭代式 DFS 采集全树节点（避免深树递归溢出；根在前、子节点按声明序） */
function flattenTree(root: SimNode): SimNode[] {
  const out: SimNode[] = [];
  const stack: SimNode[] = [root];
  while (stack.length) {
    const n = stack.pop();
    if (!n) break;
    out.push(n);
    for (let i = n.children.length - 1; i >= 0; i--) stack.push(n.children[i]);
  }
  return out;
}

function nodeSummary(n: SimNode): string {
  const label =
    n.text != null && n.text !== ""
      ? `text="${n.text.length > 12 ? n.text.slice(0, 12) + "…" : n.text}"`
      : n.vid != null && n.vid !== ""
        ? `vid=${n.vid}`
        : n.name ?? "无属性";
  return `${n.id}(${label})`;
}

function fieldValue(n: SimNode, field: DslField): string | null {
  switch (field) {
    case "text":
      return n.text ?? null;
    case "vid":
      return n.vid ?? null;
    case "desc":
      return n.desc ?? null;
    case "name":
      return n.name ?? null;
  }
}

const OP_LABEL: Record<DslOp, string> = {
  eq: "等于",
  neq: "不等于",
  contains: "包含",
  startsWith: "开头",
  endsWith: "结尾",
  regex: "正则",
};

function labelOfMatcher(m: DslMatcher): string {
  return `${m.field} ${OP_LABEL[m.op]} "${m.value.length > 16 ? m.value.slice(0, 16) + "…" : m.value}"`;
}

/** 属性操作符求值（正则带编译缓存；属性缺失时仅 neq 成立） */
function applyOp(val: string | null, op: DslOp, value: string, regexCache: Map<string, RegExp | null>): boolean {
  switch (op) {
    case "eq":
      return val !== null && val === value;
    case "neq":
      return val === null || val !== value;
    case "contains":
      return val !== null && val.includes(value);
    case "startsWith":
      return val !== null && val.startsWith(value);
    case "endsWith":
      return val !== null && val.endsWith(value);
    case "regex": {
      if (val === null) return false;
      let re = regexCache.get(value);
      if (re === undefined) {
        try {
          re = new RegExp(value);
        } catch {
          re = null;
        }
        regexCache.set(value, re);
      }
      return re !== null && re.test(val);
    }
  }
}

function cornerOf(n: SimNode): "tl" | "tr" | "bl" | "br" {
  const cx = n.x + n.w / 2;
  const cy = n.y + n.h / 2;
  if (cy < 500) return cx < 500 ? "tl" : "tr";
  return cx < 500 ? "bl" : "br";
}

/** 守卫校验：面积 = w×h / 1000² 百分比；角落 = 节点中心所在象限 */
function guardCheck(node: SimNode, guard: { areaMaxPct?: number; corner?: "tl" | "tr" | "bl" | "br" }): { ok: boolean; detail: string } {
  const areaPct = (node.w * node.h) / 10000; // (w*h)/(1000*1000)*100
  const parts: string[] = [];
  let ok = true;
  if (guard.areaMaxPct != null) {
    const pass = areaPct <= guard.areaMaxPct;
    parts.push(`面积 ${areaPct.toFixed(1)}% ${pass ? "≤" : ">"} 上限 ${guard.areaMaxPct}%${pass ? "" : "，超限拦截"}`);
    ok = ok && pass;
  }
  if (guard.corner) {
    const c = cornerOf(node);
    const pass = c === guard.corner;
    parts.push(`位置 ${c}（期望 ${guard.corner}）${pass ? "" : "，不符拦截"}`);
    ok = ok && pass;
  }
  return { ok, detail: parts.join("；") || "无守卫条件" };
}

/**
 * 匹配引擎：单规则 vs 节点树（含单步 trace；命中节点需过 guard 校验）。
 * rule.matchers 全部命中（每个 matcher 至少一个节点满足且守卫通过）→ matched。
 */
export function matchRuleOnTree(root: SimNode, rule: DslRule): MatchTrace {
  const nodes = flattenTree(root); // 一次 DFS 采集，属性缓存复用（O(N×M)）
  const regexCache = new Map<string, RegExp | null>();
  const steps: MatchStep[] = [
    {
      step: 1,
      label: "节点采集（DFS）",
      passed: true,
      detail: `共 ${nodes.length} 个节点；根节点 ${nodeSummary(root)}`,
    },
  ];
  const hitNodeIds: string[] = [];
  let matched = rule.matchers.length > 0;

  rule.matchers.forEach((m, idx) => {
    const hits: SimNode[] = [];
    const guardRejected: string[] = [];
    for (const n of nodes) {
      const val = fieldValue(n, m.field);
      if (!applyOp(val, m.op, m.value, regexCache)) continue;
      if (m.guard) {
        const g = guardCheck(n, m.guard);
        if (!g.ok) {
          guardRejected.push(`${n.id}（${g.detail}）`);
          continue;
        }
      }
      hits.push(n);
    }
    const passed = hits.length > 0;
    if (!passed) matched = false;
    for (const h of hits) {
      if (!hitNodeIds.includes(h.id)) hitNodeIds.push(h.id);
    }
    const detailParts: string[] = [];
    if (passed) {
      const shown = hits.slice(0, 3).map(nodeSummary).join("、");
      detailParts.push(`命中 ${hits.length} 节点：${shown}${hits.length > 3 ? " 等" : ""}`);
    } else {
      detailParts.push(`0 节点满足（${labelOfMatcher(m)}）`);
    }
    if (guardRejected.length > 0) {
      detailParts.push(`守卫拦截 ${guardRejected.length} 个：${guardRejected.slice(0, 2).join("、")}`);
    }
    steps.push({
      step: idx + 2,
      label: `M${idx + 1} ${labelOfMatcher(m)}`,
      passed,
      detail: detailParts.join("；"),
    });
  });

  steps.push({
    step: rule.matchers.length + 2,
    label: "结论",
    passed: matched,
    detail: matched
      ? `全部 ${rule.matchers.length} 个匹配器命中 → 执行动作 ${rule.action}`
      : "存在未命中的匹配器，规则放行",
  });

  return {
    ruleId: rule.id,
    ruleName: rule.name,
    source: rule.source,
    matched,
    hitNodeIds,
    steps,
    nodeCount: nodes.length,
  };
}

const SOURCE_TIE_ORDER: Record<string, number> = { custom: 4, learned: 3, gkd: 2, preset: 1 };

/** 多规则匹配：先按调度分排序（大者先；平分按源权重 custom>learned>gkd>preset 再按稳定序），返回全部 trace + winner */
export function matchTree(root: SimNode, rules: DslRule[]): TreeMatchResult {
  const scored = rules.map((r, idx) => ({
    r,
    idx,
    score: r.priority,
    reason: `调度分 ${r.priority}（源 ${r.source}${r.enabled ? "" : "，已停用不参与匹配"}）`,
  }));
  scored.sort(
    (a, b) =>
      b.score - a.score ||
      (SOURCE_TIE_ORDER[b.r.source] ?? 0) - (SOURCE_TIE_ORDER[a.r.source] ?? 0) ||
      a.idx - b.idx
  );
  const order = scored.map((s) => ({ id: s.r.id, name: s.r.name, score: s.score, reason: s.reason }));
  const traces = scored.filter((s) => s.r.enabled).map((s) => matchRuleOnTree(root, s.r));
  const winner = traces.find((t) => t.matched) ?? null;
  return { traces, winner, order };
}
