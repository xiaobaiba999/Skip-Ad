"use client";

/**
 * v5.4.0 规则调试沙箱（Task D）
 * 自定义节点树构建器 × 9:16 命中高亮画布 × 单步调试 × GKD 订阅导入，
 * 判定逻辑全部走 @/lib/rule-dsl（与真机引擎同源统一 DSL）。
 * 依赖契约见 Task A；模块缺失时由各调用点 try/catch 兜底，沙箱不崩。
 */

import { createElement, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  type DslMatcher,
  type DslRule,
  type MatchStep,
  type MatchTrace,
  type SimNode,
  type TreeMatchResult,
  compileDslLine,
  makeNode,
  matchTree,
  parseGkdSubscription,
  serializeDslRule,
} from "@/lib/rule-dsl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import {
  Activity,
  AlertTriangle,
  AppWindow,
  Boxes,
  Braces,
  Bug,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ClipboardCopy,
  Copy,
  Database,
  Download,
  FileCode2,
  FlaskConical,
  Globe,
  Image as ImageIcon,
  ListOrdered,
  ListTree,
  Loader2,
  MousePointerClick,
  PanelBottom,
  PanelTop,
  Play,
  Plus,
  RectangleHorizontal,
  RotateCcw,
  Rows3,
  ShieldCheck,
  Smartphone,
  Square,
  TextCursorInput,
  Trash2,
  Trophy,
  Type,
  Upload,
  XCircle,
  Zap,
  type LucideIcon,
} from "lucide-react";

/* ══════════════════════ 常量与纯工具 ══════════════════════ */

const NODE_LIMIT = 60;
/** 长列表统一滚动样式：限高 + 细滚动条 */
const SCROLL_THIN = "overflow-y-auto [scrollbar-width:thin] [scrollbar-color:#c9c9ce_transparent]";

let uidSeq = 0;
function genId(prefix = "sbx"): string {
  uidSeq += 1;
  return `${prefix}-${Date.now().toString(36)}-${uidSeq}`;
}

/** 匹配器 → GKD 快照选择器文本（op 符号与 rule-dsl 序列化器对齐：= != *= ^= $= ~=） */
const OP_SYMBOL: Record<string, string> = {
  eq: "=",
  neq: "!=",
  contains: "*=",
  startsWith: "^=",
  endsWith: "$=",
  regex: "~=",
};

function matcherText(m: DslMatcher): string {
  return `[${m.field}${OP_SYMBOL[m.op] ?? "?"}="${m.value}"]`;
}

function guardText(g: NonNullable<DslMatcher["guard"]>): string {
  if (g.corner && g.areaMaxPct != null) return `guard 角=${g.corner} 面积≤${g.areaMaxPct}%`;
  if (g.corner) return `guard 角=${g.corner}`;
  if (g.areaMaxPct != null) return `guard 面积≤${g.areaMaxPct}%`;
  return "guard";
}

function matcherListText(rule: DslRule): string {
  return (rule?.matchers ?? []).map(matcherText).join(" ; ");
}

/** 契约函数兜底序列化（Task A 实现异常时退回本地格式化） */
function safeSerialize(rule: DslRule): string {
  try {
    const s = serializeDslRule(rule);
    return typeof s === "string" ? s : matcherListText(rule);
  } catch {
    return matcherListText(rule);
  }
}

function fmtScore(n: number): string {
  const v = Math.round(n * 10) / 10;
  return Number.isInteger(v) ? String(v) : v.toFixed(1);
}

/* ── 节点树不可变工具（结构化更新，绝不原地改） ── */

function kids(n: SimNode | null | undefined): SimNode[] {
  return n && Array.isArray(n.children) ? n.children : [];
}

function countNodes(root: SimNode): number {
  return 1 + kids(root).reduce((s, c) => s + countNodes(c), 0);
}

function findNode(root: SimNode, id: string): SimNode | null {
  if (root.id === id) return root;
  for (const c of kids(root)) {
    const hit = findNode(c, id);
    if (hit) return hit;
  }
  return null;
}

function mapNode(root: SimNode, id: string, fn: (n: SimNode) => SimNode): SimNode {
  if (root.id === id) return fn(root);
  return { ...root, children: kids(root).map((c) => mapNode(c, id, fn)) };
}

function removeNode(root: SimNode, id: string): SimNode {
  return {
    ...root,
    children: kids(root)
      .filter((c) => c.id !== id)
      .map((c) => removeNode(c, id)),
  };
}

function insertChild(root: SimNode, parentId: string, child: SimNode): SimNode {
  if (root.id === parentId) return { ...root, children: [...kids(root), child] };
  return { ...root, children: kids(root).map((c) => insertChild(c, parentId, child)) };
}

/** 把 node 插入为 targetId 的右邻兄弟（复制节点用） */
function insertSibling(root: SimNode, targetId: string, node: SimNode): SimNode {
  if (kids(root).some((c) => c.id === targetId)) {
    const children: SimNode[] = [];
    for (const c of kids(root)) {
      children.push(c);
      if (c.id === targetId) children.push(node);
    }
    return { ...root, children };
  }
  return { ...root, children: kids(root).map((c) => insertSibling(c, targetId, node)) };
}

function collectIds(root: SimNode, into?: Set<string>): Set<string> {
  const set = into ?? new Set<string>();
  set.add(root.id);
  for (const c of kids(root)) collectIds(c, set);
  return set;
}

/** 深拷贝并保证全树 id 唯一（Task A 的 makeNode 语义未知，这里兜底去重） */
function ensureUniqueIds(node: SimNode, seen: Set<string> = new Set()): SimNode {
  const id = seen.has(node.id) ? genId() : node.id;
  seen.add(id);
  return { ...node, id, children: kids(node).map((c) => ensureUniqueIds(c, seen)) };
}

/** makeNode 防御包装：契约实现异常时退回本地构造 */
function safeMakeNode(partial: Partial<SimNode> & { children?: SimNode[] }): SimNode {
  try {
    return makeNode(partial);
  } catch {
    return { id: genId(), x: 0, y: 0, w: 120, h: 60, children: [], ...partial };
  }
}

function shortName(n: SimNode): string {
  if (n.name && n.name.trim()) {
    const seg = n.name.split(".");
    return seg[seg.length - 1];
  }
  if (n.text) return `「${n.text.slice(0, 8)}」`;
  if (n.vid) return n.vid;
  return "节点";
}

function canvasLabel(n: SimNode): string {
  return n.text ?? n.vid ?? shortName(n);
}

/** 画布渲染序：先序遍历 → 父先画（垫底）、子后画（浮上） */
function collectRenderList(root: SimNode): SimNode[] {
  const out: SimNode[] = [];
  const walk = (n: SimNode) => {
    out.push(n);
    for (const c of kids(n)) walk(c);
  };
  walk(root);
  return out;
}

function iconForNode(n: SimNode): LucideIcon {
  const name = n.name ?? "";
  if (name.includes("WebView")) return Globe;
  if (name.includes("ImageView")) return ImageIcon;
  if (name.includes("EditText")) return TextCursorInput;
  if (name.includes("RecyclerView") || name.includes("ScrollView") || name.includes("ListView")) return Rows3;
  if (name.includes("Button")) return RectangleHorizontal;
  if (name.includes("TextView")) return Type;
  if (name.includes("Dialog")) return PanelTop;
  if (name.includes("LinearLayout") || name.includes("Layout") || name.includes("Activity")) return Boxes;
  return Square;
}

/** 渲染节点类型图标（createElement 引用既有组件，避免组件体内新建组件触发 lint） */
function renderNodeIcon(n: SimNode, className: string) {
  return createElement(iconForNode(n), { className });
}

async function copyText(text: string): Promise<boolean> {
  try {
    if (typeof navigator !== "undefined" && navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    /* 降级到 execCommand */
  }
  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand("copy");
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

/* ── 场景 JSON 导入校验（容错 + 上限防御） ── */

function toNode(raw: unknown, depth: number): SimNode | null {
  if (!raw || typeof raw !== "object" || depth > 8) return null;
  const o = raw as Record<string, unknown>;
  const hasShape =
    typeof o.x === "number" ||
    typeof o.w === "number" ||
    Array.isArray(o.children) ||
    typeof o.name === "string" ||
    typeof o.vid === "string";
  if (!hasShape) return null;
  const num = (v: unknown, dflt: number): number =>
    typeof v === "number" && Number.isFinite(v) ? Math.min(1000, Math.max(0, Math.round(v))) : dflt;
  const str = (v: unknown): string | undefined => (typeof v === "string" ? v : undefined);
  const children = Array.isArray(o.children)
    ? o.children.map((c) => toNode(c, depth + 1)).filter((c): c is SimNode => c !== null)
    : [];
  return safeMakeNode({
    id: str(o.id),
    name: str(o.name),
    vid: str(o.vid),
    text: str(o.text),
    desc: str(o.desc),
    x: num(o.x, 0),
    y: num(o.y, 0),
    w: num(o.w, 200),
    h: num(o.h, 80),
    clickable: o.clickable === true,
    scrollable: o.scrollable === true,
    editable: o.editable === true,
    children,
  });
}

function parseSceneJson(text: string): { name: string | null; root: SimNode } | { error: string } {
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch (e) {
    return { error: `JSON 解析失败：${e instanceof Error ? e.message : String(e)}` };
  }
  if (!data || typeof data !== "object") return { error: "顶层必须是 JSON 对象" };
  const obj = data as Record<string, unknown>;
  let rawRoot: unknown = obj;
  let name: string | null = null;
  if (obj.root && typeof obj.root === "object") {
    rawRoot = obj.root;
    if (typeof obj.name === "string" && obj.name.trim()) name = obj.name.trim();
  }
  const root = toNode(rawRoot, 0);
  if (!root) return { error: "根节点非法：需要含 x / y / w / h 数值或 children 数组" };
  const total = countNodes(root);
  if (total > NODE_LIMIT) return { error: `节点数 ${total} 超过沙箱上限 ${NODE_LIMIT}，请精简后重试` };
  return { name, root: ensureUniqueIds(root) };
}

/* ══════════════════════ 内置场景模板 ══════════════════════ */

function tplStandard(): SimNode {
  return safeMakeNode({
    name: "android.widget.FrameLayout",
    desc: "开屏广告根容器",
    x: 0,
    y: 0,
    w: 1000,
    h: 1000,
    children: [
      safeMakeNode({
        name: "android.widget.ImageView",
        desc: "广告素材大图（全屏热区）",
        x: 0,
        y: 0,
        w: 1000,
        h: 1000,
        clickable: true,
      }),
      safeMakeNode({
        name: "android.widget.TextView",
        vid: "btn_skip",
        text: "跳过",
        desc: "右上角跳过键",
        x: 806,
        y: 44,
        w: 100,
        h: 56,
        clickable: true,
        children: [
          safeMakeNode({ name: "android.widget.TextView", text: "3", desc: "倒计时数字", x: 902, y: 46, w: 40, h: 52 }),
        ],
      }),
    ],
  });
}

function tplSdkZeroText(): SimNode {
  return safeMakeNode({
    name: "com.bytedance.sdk.openadsdk.activity.TTFullScreenExpressActivity",
    desc: "穿山甲全屏自渲染广告窗（零文本）",
    x: 0,
    y: 0,
    w: 1000,
    h: 1000,
    children: [
      safeMakeNode({
        name: "android.webkit.WebView",
        desc: "广告落地页大热区",
        x: 0,
        y: 0,
        w: 1000,
        h: 1000,
        clickable: true,
      }),
      safeMakeNode({
        name: "android.widget.ImageView",
        vid: "tt_close_iv",
        desc: "角落小关闭键（无文本）",
        x: 938,
        y: 28,
        w: 34,
        h: 34,
        clickable: true,
      }),
    ],
  });
}

function tplHalfScreen(): SimNode {
  return safeMakeNode({
    name: "android.app.Dialog",
    desc: "半屏插屏弹窗",
    x: 0,
    y: 0,
    w: 1000,
    h: 1000,
    children: [
      safeMakeNode({
        name: "android.widget.LinearLayout",
        desc: "底部锚定半屏面板（h=450）",
        x: 0,
        y: 550,
        w: 1000,
        h: 450,
        children: [
          safeMakeNode({ name: "android.widget.TextView", text: "广告", desc: "角标", x: 20, y: 566, w: 76, h: 40 }),
          safeMakeNode({
            name: "android.widget.Button",
            text: "立即领取",
            desc: "营销主按钮",
            x: 310,
            y: 838,
            w: 380,
            h: 72,
            clickable: true,
          }),
          safeMakeNode({
            name: "android.widget.ImageView",
            vid: "close_btn",
            desc: "关闭键（tr 角）",
            x: 930,
            y: 564,
            w: 40,
            h: 40,
            clickable: true,
          }),
        ],
      }),
    ],
  });
}

function tplContentPage(): SimNode {
  return safeMakeNode({
    name: "android.widget.FrameLayout",
    desc: "正常内容页（防误触对照）",
    x: 0,
    y: 0,
    w: 1000,
    h: 1000,
    children: [
      safeMakeNode({
        name: "androidx.recyclerview.widget.RecyclerView",
        desc: "内容列表",
        x: 0,
        y: 0,
        w: 1000,
        h: 880,
        scrollable: true,
        children: [
          safeMakeNode({ name: "android.widget.TextView", text: "首页推荐 · 第 1 条", x: 24, y: 36, w: 620, h: 44 }),
          safeMakeNode({ name: "android.widget.TextView", text: "首页推荐 · 第 2 条", x: 24, y: 116, w: 620, h: 44 }),
          safeMakeNode({ name: "android.widget.TextView", text: "首页推荐 · 第 3 条", x: 24, y: 196, w: 620, h: 44 }),
        ],
      }),
      safeMakeNode({
        name: "android.widget.EditText",
        desc: "评论输入框",
        x: 20,
        y: 896,
        w: 740,
        h: 84,
        editable: true,
      }),
      safeMakeNode({
        name: "android.widget.Button",
        text: "发表评论",
        x: 780,
        y: 896,
        w: 200,
        h: 84,
        clickable: true,
      }),
    ],
  });
}

interface SceneTemplate {
  key: string;
  name: string;
  hint: string;
  icon: LucideIcon;
  build: () => SimNode;
}

const TEMPLATES: SceneTemplate[] = [
  { key: "standard", name: "标准开屏", hint: "FrameLayout + 全屏大图 + 右上角「跳过 3」vid=btn_skip + 倒计时", icon: Smartphone, build: tplStandard },
  { key: "sdk", name: "SDK 零文本窗", hint: "穿山甲 TTFullScreenExpressActivity + WebView 大热区 + vid=tt_close_iv", icon: AppWindow, build: tplSdkZeroText },
  { key: "half", name: "半屏插屏", hint: "Dialog + 底部锚定 LinearLayout(h=450) + 「广告」角标 + close_btn", icon: PanelBottom, build: tplHalfScreen },
  { key: "content", name: "内容页对照", hint: "RecyclerView 列表 + EditText 输入框 + 发表评论——验证零误触", icon: ShieldCheck, build: tplContentPage },
];

/* ══════════════════════ 演示规则与映射 ══════════════════════ */

function defaultRules(): DslRule[] {
  return [
    {
      id: "demo-text-skip",
      name: "文本跳过 · 「跳过」包含匹配",
      source: "preset",
      appName: "*",
      adType: "splash",
      matchers: [{ field: "text", op: "contains", value: "跳过" }],
      action: "instant_skip",
      priority: 90,
      enabled: true,
    },
    {
      id: "demo-vid-btn-skip",
      name: "viewId 定向 · btn_skip",
      source: "preset",
      appName: "*",
      adType: "splash",
      matchers: [{ field: "vid", op: "eq", value: "btn_skip" }],
      action: "instant_skip",
      priority: 95,
      enabled: true,
    },
    {
      id: "demo-vid-tt-close",
      name: "viewId 定向 · tt_close_iv",
      source: "preset",
      appName: "*",
      adType: "splash",
      matchers: [{ field: "vid", op: "eq", value: "tt_close_iv" }],
      action: "instant_skip",
      priority: 95,
      enabled: true,
    },
    {
      id: "demo-half-close",
      name: "半屏插屏关闭 · close_btn（br 角守护）",
      source: "preset",
      appName: "*",
      adType: "half_interstitial",
      // 注：rule-dsl 的 guard corner 按整屏象限判定（节点中心 vs 屏幕中线），
      // 底部锚定半屏面板的关闭键落在 br 象限，故守卫取 br（面板内视觉上仍是右上角）
      matchers: [{ field: "vid", op: "eq", value: "close_btn", guard: { corner: "br" } }],
      action: "halfsheet_close",
      priority: 85,
      enabled: true,
    },
  ];
}

/** /api/rules 的 matchType → 演示 matcher 映射（真机端为全量特征，此处仅演示 DSL 形态） */
const LIB_DEMO_MATCHERS: Record<string, DslMatcher[]> = {
  text: [{ field: "text", op: "contains", value: "跳过" }],
  viewId: [{ field: "vid", op: "eq", value: "btn_skip" }],
  "sdk-window": [{ field: "name", op: "contains", value: "openadsdk" }],
  coord: [{ field: "vid", op: "eq", value: "tt_close_iv" }],
  "popup-v2": [{ field: "text", op: "eq", value: "广告" }],
};

interface LibRuleRow {
  id: string;
  appName?: string;
  adType?: string;
  matchType: string;
  matchLabel?: string;
  action?: string;
  source?: string;
  confidence?: number;
}

/** 内置 GKD 订阅示例：1 个 Category · 2 个 Group · 3 条 Rule */
const GKD_EXAMPLE = `// GKD 订阅示例 · 通用开屏（可直接导入调试）
let categories: Array<$GkdCategory> = [
  {
    id: 0,
    name: '开屏广告',
    enable: true,
    groups: [
      {
        id: 0,
        name: '通用开屏',
        enable: true,
        rules: [
          { matches: '[text="跳过"]', action: 'click' },
          { matches: '[vid*="skip"]', action: 'click' },
        ],
      },
      {
        id: 1,
        name: '图片关闭键兜底',
        enable: true,
        rules: [
          { matches: '[name="android.widget.ImageView" && vid~="close.*"]', action: 'click' },
        ],
      },
    ],
  },
];`;

/* ══════════════════════ 局部类型 ══════════════════════ */

type CompilePreview = { ok: boolean; rule?: DslRule; error?: string } | null;

/** 输入是否含顶层 |（引号/括号感知）——有则视为完整行格式，无则视为纯选择器列表 */
function hasTopLevelPipe(line: string): boolean {
  let depth = 0;
  let quote: string | null = null;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
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
    else if (c === "]") depth = Math.max(0, depth - 1);
    else if (c === "|" && depth === 0) return true;
  }
  return false;
}

/** 用户输入 → compileDslLine 行文本：纯选择器自动包装为完整行（name | app | adType | action | sels） */
function buildDslLine(raw: string): { line: string; fullLine: boolean } {
  const line = raw.trim();
  if (hasTopLevelPipe(line)) return { line, fullLine: true };
  return { line: `自定义规则 | * | any | instant_skip | ${line}`, fullLine: false };
}

interface GkdImportResult {
  ok: boolean;
  ruleCount: number;
  groupCount: number;
  rules: DslRule[];
  errors: string[];
  warnings: string[];
}

interface OrderRow {
  id: string;
  name: string;
  score: number;
  reason: string;
}

/* ══════════════════════ 通用小组件 ══════════════════════ */

function NoticeBar({ tone, text }: { tone: "ok" | "err" | "info"; text: string }) {
  return (
    <div
      role="status"
      className={cn(
        "mt-3 flex items-start gap-2 rounded-xl px-3 py-2.5 text-[11px] font-semibold leading-relaxed",
        tone === "ok"
          ? "bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200"
          : tone === "err"
            ? "bg-rose-50 text-rose-700 ring-1 ring-rose-200"
            : "bg-amber-50 text-amber-700 ring-1 ring-amber-200",
      )}
      data-testid="sandbox-notice"
    >
      {tone === "ok" ? (
        <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0" />
      ) : tone === "err" ? (
        <XCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
      ) : (
        <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
      )}
      <span>{text}</span>
    </div>
  );
}

const SOURCE_STYLE: Record<string, string> = {
  preset: "bg-zinc-100 text-zinc-600 ring-zinc-200",
  gkd: "bg-teal-50 text-teal-700 ring-teal-200",
  learned: "bg-rose-50 text-rose-600 ring-rose-200",
  custom: "bg-violet-50 text-violet-700 ring-violet-200",
};
const SOURCE_LABEL: Record<string, string> = { preset: "预置", gkd: "GKD", learned: "学习", custom: "自定义" };

function SourceBadge({ source }: { source: string }) {
  return (
    <span className={cn("shrink-0 rounded-full px-1.5 py-0.5 text-[9px] font-bold ring-1", SOURCE_STYLE[source] ?? SOURCE_STYLE.custom)}>
      {SOURCE_LABEL[source] ?? source}
    </span>
  );
}

function MatcherChips({ matchers }: { matchers: DslMatcher[] }) {
  if (!matchers || matchers.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1">
      {matchers.map((m, i) => (
        <span key={i} className="rounded bg-zinc-50 px-1.5 py-0.5 font-mono text-[9px] text-zinc-600 ring-1 ring-zinc-200">
          {matcherText(m)}
        </span>
      ))}
      {matchers.map((m, i) =>
        m.guard ? (
          <span key={`g-${i}`} className="rounded bg-amber-50 px-1.5 py-0.5 font-mono text-[9px] font-bold text-amber-700 ring-1 ring-amber-200">
            {guardText(m.guard)}
          </span>
        ) : null,
      )}
    </div>
  );
}

function NumField({ label, value, onCommit }: { label: string; value: number; onCommit: (v: number) => void }) {
  return (
    <label className="block min-w-0">
      <span className="text-[9px] font-bold text-zinc-400">{label}</span>
      <Input
        type="number"
        min={0}
        max={1000}
        value={String(value)}
        onChange={(e) => {
          const v = parseInt(e.target.value, 10);
          if (!Number.isNaN(v)) onCommit(Math.min(1000, Math.max(0, v)));
        }}
        className="mt-0.5 h-9 rounded-lg px-2 text-[11px] tabular-nums"
      />
    </label>
  );
}

function TextField({
  label,
  value,
  onChange,
  mono,
  testid,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  mono?: boolean;
  testid?: string;
}) {
  return (
    <label className="block min-w-0">
      <span className="text-[9px] font-bold text-zinc-400">{label}</span>
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={cn("mt-0.5 h-9 rounded-lg px-2 text-[11px]", mono && "font-mono")}
        data-testid={testid}
      />
    </label>
  );
}

/* ══════════════════════ 左栏：场景编辑器 ══════════════════════ */

function NodeTreeRow({
  node,
  depth,
  selectedId,
  onSelect,
}: {
  node: SimNode;
  depth: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  const selected = node.id === selectedId;
  return (
    <div>
      <button
        type="button"
        onClick={() => onSelect(node.id)}
        aria-label={`选中节点 ${shortName(node)}`}
        data-testid="sandbox-node-row"
        data-node-id={node.id}
        className={cn(
          "flex min-h-11 w-full items-center gap-1.5 rounded-lg px-2 text-left transition",
          selected ? "bg-violet-50 ring-1 ring-violet-300" : "hover:bg-zinc-50",
        )}
      >
        {renderNodeIcon(node, "h-3.5 w-3.5 shrink-0 text-zinc-400")}
        <span className="shrink-0 text-[11px] font-bold text-zinc-700">{shortName(node)}</span>
        {node.vid ? (
          <span className="max-w-20 shrink-0 truncate rounded bg-zinc-100 px-1 font-mono text-[9px] text-zinc-500">{node.vid}</span>
        ) : null}
        {node.text ? (
          <span className="min-w-0 shrink truncate rounded bg-emerald-50 px-1 text-[9px] font-semibold text-emerald-700">{node.text}</span>
        ) : null}
        <span className="ml-auto shrink-0 text-[9px] tabular-nums text-zinc-400">
          {node.w}×{node.h}
        </span>
        {node.clickable ? <MousePointerClick className="h-3 w-3 shrink-0 text-amber-500" /> : null}
      </button>
      {kids(node).length > 0 && (
        <div className="ml-[15px] space-y-0.5 border-l border-zinc-200 pl-1.5">
          {kids(node).map((c) => (
            <NodeTreeRow key={c.id} node={c} depth={depth + 1} selectedId={selectedId} onSelect={onSelect} />
          ))}
        </div>
      )}
    </div>
  );
}

interface SceneEditorProps {
  sceneName: string;
  templateKey: string | null;
  nodeCount: number;
  root: SimNode;
  selectedNode: SimNode | null;
  selectedId: string | null;
  onSelectNode: (id: string) => void;
  onPatchNode: (patch: Partial<SimNode>) => void;
  onAddChild: () => void;
  onDeleteNode: () => void;
  onDuplicateNode: () => void;
  onLoadTemplate: (key: string) => void;
  jsonText: string;
  onJsonTextChange: (t: string) => void;
  onImportJson: () => void;
  onExportJson: () => void;
  onCopyJson: () => void;
  jsonError: string | null;
}

function SceneEditorCard(p: SceneEditorProps) {
  const node = p.selectedNode;
  const isRootSelected = node != null && node.id === p.root.id;
  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-4" data-testid="sandbox-scene-editor" aria-label="场景编辑器">
      <h3 className="flex items-center gap-2 text-sm font-bold text-zinc-900">
        <ListTree className="h-4 w-4 text-violet-600" /> 场景编辑器
        <span
          className={cn(
            "ml-auto rounded-full px-2 py-0.5 text-[10px] font-bold tabular-nums",
            p.nodeCount > NODE_LIMIT ? "bg-amber-50 text-amber-700 ring-1 ring-amber-200" : "bg-zinc-100 text-zinc-500",
          )}
        >
          节点 {p.nodeCount}/{NODE_LIMIT}
        </span>
      </h3>
      <p className="mt-1 text-[10px] leading-relaxed text-zinc-400">一键载入典型广告形态，或在节点树上增删改，画布与匹配结果实时联动</p>

      {/* 模板场景 */}
      <div className="mt-3 grid grid-cols-2 gap-2" data-testid="sandbox-templates">
        {TEMPLATES.map((t) => {
          const active = t.key === p.templateKey;
          return (
            <Button
              key={t.key}
              type="button"
              size="sm"
              variant={active ? "default" : "outline"}
              className={cn(
                "min-h-11 flex-col gap-0.5 rounded-xl px-1 py-1 text-[10px] font-bold leading-tight",
                active && "bg-zinc-900 hover:bg-zinc-700",
              )}
              onClick={() => p.onLoadTemplate(t.key)}
              title={t.hint}
              data-testid={`sandbox-tpl-${t.key}`}
            >
              <t.icon className="h-4 w-4" /> {t.name}
            </Button>
          );
        })}
      </div>

      {/* 节点树 */}
      <div className={cn("mt-3 rounded-xl bg-zinc-50/60 p-1.5 ring-1 ring-zinc-100", SCROLL_THIN, "max-h-96")} data-testid="sandbox-node-tree">
        <NodeTreeRow node={p.root} depth={0} selectedId={p.selectedId} onSelect={p.onSelectNode} />
      </div>

      {/* 节点操作 */}
      <div className="mt-2 grid grid-cols-3 gap-2">
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="min-h-11 rounded-xl px-1 text-[10px] font-bold"
          onClick={p.onAddChild}
          data-testid="sandbox-btn-add-node"
        >
          <Plus className="h-3.5 w-3.5" /> 加子节点
        </Button>
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="min-h-11 rounded-xl px-1 text-[10px] font-bold"
          onClick={p.onDuplicateNode}
          disabled={!node || isRootSelected}
          data-testid="sandbox-btn-dup-node"
        >
          <Copy className="h-3.5 w-3.5" /> 复制节点
        </Button>
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="min-h-11 rounded-xl px-1 text-[10px] font-bold text-rose-600 hover:bg-rose-50 hover:text-rose-700"
          onClick={p.onDeleteNode}
          disabled={!node || isRootSelected}
          data-testid="sandbox-btn-del-node"
        >
          <Trash2 className="h-3.5 w-3.5" /> 删除节点
        </Button>
      </div>

      {/* 属性编辑面板 */}
      <div className="mt-3 rounded-xl border border-zinc-100 bg-zinc-50/60 p-2.5" data-testid="sandbox-node-props">
        {node ? (
          <>
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-[10px] font-black text-zinc-500">节点属性</span>
              <span className="min-w-0 flex-1 truncate font-mono text-[9px] text-zinc-400" title={node.id}>
                {node.id}
              </span>
              {isRootSelected ? <span className="rounded bg-zinc-200 px-1.5 py-0.5 text-[9px] font-bold text-zinc-600">根节点</span> : null}
            </div>
            <div className="mt-2 grid grid-cols-1 gap-1.5">
              <TextField label="name 控件类型" value={node.name ?? ""} mono onChange={(v) => p.onPatchNode({ name: v })} testid="sandbox-prop-name" />
            </div>
            <div className="mt-1.5 grid grid-cols-2 gap-1.5">
              <TextField label="vid 资源 ID" value={node.vid ?? ""} mono onChange={(v) => p.onPatchNode({ vid: v })} testid="sandbox-prop-vid" />
              <TextField label="text 文本" value={node.text ?? ""} onChange={(v) => p.onPatchNode({ text: v })} testid="sandbox-prop-text" />
            </div>
            <div className="mt-1.5 grid grid-cols-1 gap-1.5">
              <TextField label="desc 内容描述" value={node.desc ?? ""} onChange={(v) => p.onPatchNode({ desc: v })} testid="sandbox-prop-desc" />
            </div>
            <div className="mt-1.5 grid grid-cols-4 gap-1.5">
              <NumField label="x" value={node.x} onCommit={(v) => p.onPatchNode({ x: v })} />
              <NumField label="y" value={node.y} onCommit={(v) => p.onPatchNode({ y: v })} />
              <NumField label="w" value={node.w} onCommit={(v) => p.onPatchNode({ w: v })} />
              <NumField label="h" value={node.h} onCommit={(v) => p.onPatchNode({ h: v })} />
            </div>
            <div className="mt-1.5 grid grid-cols-3 gap-1">
              {(
                [
                  { key: "clickable", label: "可点击", value: !!node.clickable },
                  { key: "editable", label: "可编辑", value: !!node.editable },
                  { key: "scrollable", label: "可滚动", value: !!node.scrollable },
                ] as const
              ).map((f) => (
                <label key={f.key} className="flex min-h-11 items-center justify-between gap-1 rounded-lg bg-white px-2 ring-1 ring-zinc-100">
                  <span className="text-[10px] font-bold text-zinc-500">{f.label}</span>
                  <Switch
                    checked={f.value}
                    onCheckedChange={(v) => p.onPatchNode({ [f.key]: v } as Partial<SimNode>)}
                    aria-label={`${f.label}开关`}
                  />
                </label>
              ))}
            </div>
            <p className="mt-1.5 text-[9px] leading-relaxed text-zinc-400">坐标与尺寸为 0~1000 归一化值，画布按 9:16 屏比例换算渲染</p>
          </>
        ) : (
          <div className="px-1 py-3 text-center">
            <p className="text-[11px] font-semibold text-zinc-500">在上方节点树中点选一个节点</p>
            <p className="mt-1 text-[10px] leading-relaxed text-zinc-400">
              选中后可编辑属性；未选中时「加子节点」将挂到根节点之下。根节点不可删除或复制。
            </p>
          </div>
        )}
      </div>

      {/* JSON 导入 / 导出 */}
      <div className="mt-3 border-t border-zinc-100 pt-3">
        <div className="flex items-center gap-1.5 text-[11px] font-bold text-zinc-600">
          <Braces className="h-3.5 w-3.5 text-violet-500" /> 场景 JSON 导入 / 导出
        </div>
        <Textarea
          value={p.jsonText}
          onChange={(e) => p.onJsonTextChange(e.target.value)}
          placeholder="点「导出」生成当前场景，或粘贴 { name, root } 结构后点「导入」"
          className="mt-2 min-h-20 rounded-xl font-mono text-[10px] leading-relaxed"
          data-testid="sandbox-json-textarea"
          aria-label="场景 JSON 文本"
        />
        {p.jsonError ? (
          <div role="alert" className="mt-1.5 rounded-lg bg-rose-50 px-2.5 py-2 text-[10px] leading-relaxed text-rose-700 ring-1 ring-rose-200" data-testid="sandbox-json-error">
            {p.jsonError}
          </div>
        ) : null}
        <div className="mt-2 grid grid-cols-3 gap-1.5">
          <Button type="button" size="sm" variant="outline" className="min-h-11 rounded-xl px-1 text-[10px] font-bold" onClick={p.onImportJson} data-testid="sandbox-btn-import-json">
            <Upload className="h-3.5 w-3.5" /> 导入JSON
          </Button>
          <Button type="button" size="sm" variant="outline" className="min-h-11 rounded-xl px-1 text-[10px] font-bold" onClick={p.onExportJson} data-testid="sandbox-btn-export-json">
            <Download className="h-3.5 w-3.5" /> 导出JSON
          </Button>
          <Button type="button" size="sm" variant="outline" className="min-h-11 rounded-xl px-1 text-[10px] font-bold" onClick={p.onCopyJson} data-testid="sandbox-btn-copy-json">
            <ClipboardCopy className="h-3.5 w-3.5" /> 复制
          </Button>
        </div>
      </div>
    </section>
  );
}

/* ══════════════════════ 中栏：预览画布 + 单步调试 ══════════════════════ */

interface CanvasCardProps {
  sceneName: string;
  nodeCount: number;
  root: SimNode;
  winner: MatchTrace | null;
  activeRuleCount: number;
  matchError: string | null;
  highlight: { hit: Set<string>; currentId: string | null };
  stepMode: boolean;
  stepIndex: number;
  steps: MatchStep[];
  stepTraceName: string | null;
  onStartDebug: () => void;
  onStepPrev: () => void;
  onStepNext: () => void;
  onResetDebug: () => void;
}

function CanvasCard(p: CanvasCardProps) {
  const renderList = useMemo(() => collectRenderList(p.root), [p.root]);
  const reachedCount = p.stepMode ? Math.min(p.stepIndex + 1, p.steps.length) : p.steps.length;

  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-4" data-testid="sandbox-canvas-card" aria-label="节点树预览与单步调试">
      <h3 className="flex flex-wrap items-center gap-2 text-sm font-bold text-zinc-900">
        <Bug className="h-4 w-4 text-violet-600" /> 节点树预览 × 命中高亮
        <span className="ml-auto flex flex-wrap items-center gap-1.5">
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-[10px] font-bold text-zinc-600">{p.sceneName}</span>
          <span className="rounded-full bg-zinc-50 px-2 py-0.5 text-[10px] font-semibold tabular-nums text-zinc-400 ring-1 ring-zinc-100">
            节点 {p.nodeCount}
          </span>
          {p.matchError ? (
            <span className="rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-600 ring-1 ring-rose-200">匹配引擎异常</span>
          ) : p.winner ? (
            <span className="flex max-w-44 items-center gap-1 truncate rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700 ring-1 ring-emerald-200">
              <Zap className="h-3 w-3 shrink-0" /> <span className="truncate">{p.winner.ruleName}</span>
            </span>
          ) : p.activeRuleCount === 0 ? (
            <span className="rounded-full bg-zinc-50 px-2 py-0.5 text-[10px] font-bold text-zinc-400 ring-1 ring-zinc-100">无启用规则</span>
          ) : (
            <span className="rounded-full bg-zinc-50 px-2 py-0.5 text-[10px] font-bold text-zinc-400 ring-1 ring-zinc-100">静默 · 零命中</span>
          )}
          {p.stepMode ? (
            <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-bold tabular-nums text-amber-700 ring-1 ring-amber-200">
              单步 {p.stepIndex + 1}/{p.steps.length}
            </span>
          ) : null}
        </span>
      </h3>

      {/* 9:16 比例画布（x/y/w/h 归一化 → 百分比） */}
      <div
        className="relative mx-auto mt-3 aspect-[9/16] w-full max-w-[300px] overflow-hidden rounded-xl bg-zinc-100 ring-1 ring-zinc-200"
        role="img"
        aria-label={`模拟屏幕预览：${p.sceneName}，共 ${p.nodeCount} 个节点`}
        data-testid="sandbox-canvas"
      >
        {renderList.map((n) => {
          const isHit = p.highlight.hit.has(n.id);
          const isCurrent = p.highlight.currentId === n.id;
          const showLabel = n.w >= 60 && n.h >= 36;
          return (
            <div
              key={n.id}
              data-testid="sandbox-canvas-node"
              data-node-id={n.id}
              title={`${shortName(n)}${n.vid ? ` · ${n.vid}` : ""}${n.text ? ` · 「${n.text}」` : ""} · (${n.x},${n.y},${n.w}×${n.h})`}
              className={cn(
                "absolute overflow-hidden rounded-[3px] border transition-all duration-300",
                isCurrent
                  ? "border-amber-500 bg-amber-200/70 shadow-[0_0_12px_rgba(245,158,11,0.55)]"
                  : isHit
                    ? "border-emerald-500 bg-emerald-200/60 shadow-[0_0_10px_rgba(16,185,129,0.45)]"
                    : "border-zinc-300/90 bg-zinc-200/50",
              )}
              style={{
                left: `${n.x / 10}%`,
                top: `${n.y / 10}%`,
                width: `${n.w / 10}%`,
                height: `${n.h / 10}%`,
              }}
            >
              {(isHit || isCurrent) && (
                <span
                  className={cn(
                    "pointer-events-none absolute inset-0 animate-pulse rounded-[3px] ring-2",
                    isCurrent ? "ring-amber-400/80" : "ring-emerald-400/70",
                  )}
                />
              )}
              {n.clickable && n.w >= 30 && n.h >= 24 ? (
                <MousePointerClick className="absolute right-0.5 top-0.5 h-2 w-2 text-amber-600" aria-label="可点击" />
              ) : null}
              {showLabel ? (
                <span className="absolute inset-0 flex items-center justify-center gap-0.5 px-0.5">
                  <span className="truncate text-[9px] leading-none text-zinc-600">{canvasLabel(n)}</span>
                </span>
              ) : null}
            </div>
          );
        })}
      </div>

      {/* 单步调试控制条 */}
      <div className="mt-3 flex flex-wrap items-center gap-2" data-testid="sandbox-step-bar">
        <Button
          type="button"
          size="sm"
          className="min-h-11 rounded-xl bg-zinc-900 hover:bg-zinc-700"
          onClick={p.onStartDebug}
          disabled={p.steps.length === 0}
          data-testid="sandbox-btn-start-debug"
        >
          <Play className="h-3.5 w-3.5" /> {p.stepMode ? "重新调试" : "开始调试"}
        </Button>
        <div className="flex items-center gap-1 rounded-xl border border-zinc-200 bg-zinc-50 p-1">
          <Button
            type="button"
            size="sm"
            variant="ghost"
            className="h-11 w-11 p-0"
            onClick={p.onStepPrev}
            disabled={!p.stepMode || p.stepIndex <= 0}
            aria-label="上一步"
            data-testid="sandbox-btn-step-prev"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="min-w-16 text-center text-[11px] font-bold tabular-nums text-zinc-600" data-testid="sandbox-step-counter">
            {p.stepMode ? `${p.stepIndex + 1} / ${p.steps.length}` : `— / ${p.steps.length}`}
          </span>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            className="h-11 w-11 p-0"
            onClick={p.onStepNext}
            disabled={!p.stepMode || p.stepIndex >= p.steps.length - 1}
            aria-label="下一步"
            data-testid="sandbox-btn-step-next"
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
        <Button type="button" size="sm" variant="outline" className="min-h-11 rounded-xl" onClick={p.onResetDebug} data-testid="sandbox-btn-step-reset">
          <RotateCcw className="h-3.5 w-3.5" /> 重置
        </Button>
        {p.stepTraceName ? (
          <span className="ml-auto max-w-full truncate text-[10px] text-zinc-400" title={p.stepTraceName}>
            遍历对象：{p.stepTraceName}
          </span>
        ) : null}
      </div>

      {/* 步骤列表：逐步点亮 */}
      {p.steps.length > 0 ? (
        <ol className={cn("mt-3 space-y-1.5 pr-1", SCROLL_THIN, "max-h-64")} data-testid="sandbox-step-list" aria-label="匹配步骤">
          {p.steps.map((s, i) => {
            const reached = p.stepMode ? i <= p.stepIndex : true;
            const isCurrent = p.stepMode && i === p.stepIndex;
            return (
              <li
                key={i}
                className={cn(
                  "flex items-start gap-2 rounded-xl px-2.5 py-2 ring-1 transition",
                  isCurrent
                    ? "bg-amber-50 ring-amber-300"
                    : !reached
                      ? "bg-zinc-50/40 ring-zinc-100"
                      : s.passed
                        ? "bg-emerald-50/60 ring-emerald-200"
                        : "bg-rose-50/60 ring-rose-200",
                )}
              >
                <span
                  className={cn(
                    "flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[10px] font-black tabular-nums",
                    isCurrent
                      ? "bg-amber-400 text-white"
                      : !reached
                        ? "bg-zinc-200 text-zinc-500"
                        : s.passed
                          ? "bg-emerald-500 text-white"
                          : "bg-rose-500 text-white",
                  )}
                >
                  {i + 1}
                </span>
                <div className="min-w-0 flex-1">
                  <div className={cn("text-[11px] font-bold", reached ? "text-zinc-800" : "text-zinc-400")}>{s.label}</div>
                  <div className="mt-0.5 break-words text-[10px] leading-relaxed text-zinc-500">{s.detail}</div>
                </div>
                {reached ? (
                  s.passed ? (
                    <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-500" />
                  ) : (
                    <XCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-rose-500" />
                  )
                ) : null}
              </li>
            );
          })}
        </ol>
      ) : (
        <p className="mt-3 rounded-xl bg-zinc-50 px-3 py-4 text-center text-[11px] leading-relaxed text-zinc-400" data-testid="sandbox-step-empty">
          暂无匹配步骤——先启用规则并构造场景，匹配轨迹会在此逐步行进
        </p>
      )}

      {p.matchError ? (
        <div role="alert" className="mt-3 rounded-xl bg-rose-50 px-3 py-2.5 text-[11px] leading-relaxed text-rose-700 ring-1 ring-rose-200">
          匹配引擎异常：{p.matchError} —— 规则 DSL 模块异常时沙箱已兜底，画布仍可预览
        </div>
      ) : null}

      <p className="mt-2 text-[9px] leading-relaxed text-zinc-400">
        已行进 {reachedCount} / {p.steps.length} 步 · 命中节点呼吸高亮（emerald），当前扫描节点琥珀色（amber），未命中灰阶
      </p>
    </section>
  );
}

/* ══════════════════════ 右栏 A：规则编辑器 ══════════════════════ */

interface RulesCardProps {
  rules: DslRule[];
  onToggleRule: (id: string, enabled: boolean) => void;
  onRemoveRule: (id: string) => void;
  dslInput: string;
  onDslInputChange: (v: string) => void;
  compilePreview: CompilePreview;
  onAddRule: () => void;
  libLoading: boolean;
  onImportLib: () => void;
  gkdText: string;
  onGkdTextChange: (v: string) => void;
  onFillGkdExample: () => void;
  onImportGkd: () => void;
  gkdResult: GkdImportResult | null;
  gkdWarnOpen: boolean;
  onToggleGkdWarn: () => void;
}

function RulesCard(p: RulesCardProps) {
  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-4" data-testid="sandbox-rules" aria-label="规则编辑器">
      <h3 className="flex items-center gap-2 text-sm font-bold text-zinc-900">
        <FileCode2 className="h-4 w-4 text-violet-600" /> 规则编辑器
        <span className="ml-auto rounded-full bg-violet-50 px-2 py-0.5 text-[10px] font-bold text-violet-700">{p.rules.length} 条</span>
      </h3>

      {/* 规则列表 */}
      {p.rules.length === 0 ? (
        <p className="mt-3 rounded-xl bg-zinc-50 px-3 py-5 text-center text-[11px] leading-relaxed text-zinc-400">
          暂无规则——用下方 DSL 编辑器添加，或从规则库 / GKD 订阅导入
        </p>
      ) : (
        <ul className={cn("mt-3 space-y-2 pr-1", SCROLL_THIN, "max-h-96")} data-testid="sandbox-rule-list">
          {p.rules.map((r) => (
            <li key={r.id} className="rounded-xl border border-zinc-100 bg-zinc-50/50 p-2.5" data-testid="sandbox-rule-item" data-rule-id={r.id}>
              <div className="flex items-start gap-2">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="truncate text-[11px] font-bold text-zinc-800">{r.name}</span>
                    <SourceBadge source={r.source} />
                  </div>
                  <div className="mt-1">
                    <MatcherChips matchers={r.matchers} />
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-1.5 text-[9px] text-zinc-400">
                    <span className="rounded bg-zinc-100 px-1 py-0.5 font-mono">{r.action}</span>
                    <span className="tabular-nums">优先级 {r.priority}</span>
                    <span className="tabular-nums">{r.appName}</span>
                  </div>
                </div>
                <div className="flex shrink-0 flex-col items-center gap-1">
                  <Switch
                    checked={r.enabled}
                    onCheckedChange={(v) => p.onToggleRule(r.id, v)}
                    aria-label={`启停规则 ${r.name}`}
                    data-testid="sandbox-rule-switch"
                  />
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="h-8 w-8 p-0 text-zinc-400 hover:bg-rose-50 hover:text-rose-600"
                    onClick={() => p.onRemoveRule(r.id)}
                    aria-label={`删除规则 ${r.name}`}
                    data-testid="sandbox-btn-del-rule"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
              <div className="mt-1.5 truncate rounded-lg bg-zinc-900/95 px-2 py-1 font-mono text-[9px] text-emerald-300" title={safeSerialize(r)}>
                {safeSerialize(r)}
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* DSL 编辑区 */}
      <div className="mt-4 border-t border-zinc-100 pt-3">
        <div className="flex flex-wrap items-center gap-1.5 text-[11px] font-bold text-zinc-600">
          <FileCode2 className="h-3.5 w-3.5 text-violet-500" /> DSL 规则编辑
          <span className="font-normal text-zinc-400">GKD 快照选择器语法</span>
        </div>
        <Input
          value={p.dslInput}
          onChange={(e) => p.onDslInputChange(e.target.value)}
          placeholder='如 [text*="跳过"] ; [vid="btn_skip"]，多匹配器用 " ; " 分隔'
          className="mt-2 h-11 rounded-xl font-mono text-[11px]"
          data-testid="sandbox-dsl-input"
          aria-label="GKD 快照选择器 DSL 输入"
        />
        <div className="mt-1.5 min-h-4 text-[10px]" data-testid="sandbox-dsl-feedback">
          {p.compilePreview == null ? (
            <span className="text-zinc-400">实时校验：输入即编译，无需点击运行</span>
          ) : p.compilePreview.ok ? (
            <span className="font-semibold text-emerald-600">
              语法有效 · {p.compilePreview.rule?.matchers?.length ?? 0} 个匹配器
            </span>
          ) : (
            <span className="font-semibold text-rose-600">{p.compilePreview.error ?? "语法错误"}</span>
          )}
        </div>
        <p className="mt-1 text-[9px] leading-relaxed text-zinc-400">
          支持完整行格式：name | app | adType | action | 选择器；守卫扩展段如 #guard:0:corner=br（与规则列表导出行互通往返）
        </p>
        <Button
          type="button"
          size="sm"
          className="min-h-11 w-full rounded-xl bg-violet-600 hover:bg-violet-700"
          disabled={!p.compilePreview?.ok}
          onClick={p.onAddRule}
          data-testid="sandbox-btn-add-rule"
        >
          <Plus className="h-3.5 w-3.5" /> 添加规则
        </Button>
      </div>

      {/* 从规则库导入 */}
      <div className="mt-4 border-t border-zinc-100 pt-3">
        <div className="flex items-center gap-1.5 text-[11px] font-bold text-zinc-600">
          <Database className="h-3.5 w-3.5 text-teal-600" /> 从规则库导入
        </div>
        <p className="mt-1 text-[10px] leading-relaxed text-zinc-400">
          拉取线上规则库（/api/rules），按 matchType 生成演示 matcher：text / viewId / sdk-window / coord / popup-v2 五类可映射
        </p>
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="mt-2 min-h-11 w-full rounded-xl"
          onClick={p.onImportLib}
          disabled={p.libLoading}
          data-testid="sandbox-btn-import-lib"
        >
          {p.libLoading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Database className="h-3.5 w-3.5" />}
          {p.libLoading ? "加载中…" : "从规则库导入"}
        </Button>
      </div>

      {/* 导入 GKD 订阅 */}
      <div className="mt-4 border-t border-zinc-100 pt-3">
        <div className="flex items-center gap-1.5 text-[11px] font-bold text-zinc-600">
          <Braces className="h-3.5 w-3.5 text-teal-600" /> 导入 GKD 订阅
        </div>
        <Textarea
          value={p.gkdText}
          onChange={(e) => p.onGkdTextChange(e.target.value)}
          placeholder="粘贴 GKD 订阅 TS 片段（let categories = [...]），或点「填充示例」"
          className="mt-2 min-h-24 rounded-xl font-mono text-[10px] leading-relaxed"
          data-testid="sandbox-gkd-textarea"
          aria-label="GKD 订阅文本"
        />
        <div className="mt-2 grid grid-cols-2 gap-2">
          <Button type="button" size="sm" variant="outline" className="min-h-11 rounded-xl" onClick={p.onFillGkdExample} data-testid="sandbox-btn-gkd-example">
            填充示例
          </Button>
          <Button type="button" size="sm" className="min-h-11 rounded-xl bg-teal-600 hover:bg-teal-700" onClick={p.onImportGkd} data-testid="sandbox-btn-gkd-import">
            导入订阅
          </Button>
        </div>

        {p.gkdResult ? (
          <div className="mt-2 space-y-1.5">
            {p.gkdResult.errors.length > 0 ? (
              <div role="alert" className="rounded-lg bg-rose-50 px-2.5 py-2 text-[10px] leading-relaxed text-rose-700 ring-1 ring-rose-200">
                {p.gkdResult.errors.map((e, i) => (
                  <div key={i}>· {e}</div>
                ))}
              </div>
            ) : null}
            {p.gkdResult.warnings.length > 0 ? (
              <div className="rounded-lg bg-amber-50 px-2.5 py-2 ring-1 ring-amber-200">
                <button
                  type="button"
                  className="flex min-h-9 w-full items-center gap-1 text-left text-[10px] font-bold text-amber-700"
                  onClick={p.onToggleGkdWarn}
                  aria-expanded={p.gkdWarnOpen}
                  data-testid="sandbox-gkd-warn-toggle"
                >
                  {p.gkdWarnOpen ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
                  {p.gkdWarnOpen ? "收起" : "展开"} {p.gkdResult.warnings.length} 条警告
                </button>
                {p.gkdWarnOpen ? (
                  <ul className="mt-1 space-y-0.5 text-[10px] leading-relaxed text-amber-700/90" data-testid="sandbox-gkd-warnings">
                    {p.gkdResult.warnings.map((w, i) => (
                      <li key={i}>· {w}</li>
                    ))}
                  </ul>
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
}

/* ══════════════════════ 右栏 B：匹配结果 ══════════════════════ */

interface ResultsCardProps {
  order: OrderRow[];
  traces: MatchTrace[];
  winner: MatchTrace | null;
  winnerRule: DslRule | null;
  matchError: string | null;
  ruleTotal: number;
  enabledCount: number;
}

function ResultsCard(p: ResultsCardProps) {
  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-4" data-testid="sandbox-results" aria-label="匹配结果">
      <h3 className="flex items-center gap-2 text-sm font-bold text-zinc-900">
        <Activity className="h-4 w-4 text-emerald-600" /> 匹配结果
        <span className="ml-auto text-[10px] font-semibold text-zinc-400">实时计算 · 输入即刷新</span>
      </h3>

      {p.matchError ? (
        <div role="alert" className="mt-3 rounded-xl bg-rose-50 px-3 py-2.5 text-[11px] leading-relaxed text-rose-700 ring-1 ring-rose-200">
          匹配引擎异常：{p.matchError}
        </div>
      ) : p.ruleTotal === 0 ? (
        <p className="mt-3 rounded-xl bg-zinc-50 px-3 py-5 text-center text-[11px] leading-relaxed text-zinc-400">
          暂无规则参与匹配——先在上方添加或导入规则
        </p>
      ) : p.enabledCount === 0 ? (
        <p className="mt-3 rounded-xl bg-zinc-50 px-3 py-5 text-center text-[11px] leading-relaxed text-zinc-400">
          所有规则均已停用——打开任一规则的启停开关即可恢复匹配
        </p>
      ) : (
        <>
          {/* 调度顺序 */}
          <div className="mt-3">
            <div className="flex items-center gap-1.5 text-[11px] font-bold text-zinc-500">
              <ListOrdered className="h-3.5 w-3.5 text-violet-500" /> 调度顺序
              <span className="font-normal text-zinc-400">（统一优先级权重）</span>
            </div>
            {p.order.length === 0 ? (
              <p className="mt-2 text-[10px] text-zinc-400">无调度记录</p>
            ) : (
              <div className={cn("mt-2 space-y-1 pr-1", SCROLL_THIN, "max-h-56")} data-testid="sandbox-order-table">
                {p.order.map((o, i) => (
                  <div
                    key={o.id}
                    className={cn(
                      "flex items-center gap-2 rounded-lg px-2 py-1.5",
                      p.winner?.ruleId === o.id ? "bg-emerald-50 ring-1 ring-emerald-200" : "bg-zinc-50",
                    )}
                  >
                    <span className="w-4 shrink-0 text-center text-[10px] font-black tabular-nums text-zinc-400">{i + 1}</span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-baseline gap-1.5">
                        <span className="truncate text-[11px] font-bold text-zinc-700">{o.name}</span>
                        <span className="shrink-0 text-[10px] font-bold tabular-nums text-violet-600">{fmtScore(o.score)}</span>
                      </div>
                      <div className="truncate text-[9px] text-zinc-400" title={o.reason}>
                        {o.reason}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* 逐规则 trace 摘要 */}
          <div className="mt-4">
            <div className="flex items-center gap-1.5 text-[11px] font-bold text-zinc-500">
              <Activity className="h-3.5 w-3.5 text-teal-600" /> 逐规则 Trace
            </div>
            {p.traces.length === 0 ? (
              <p className="mt-2 text-[10px] text-zinc-400">无匹配轨迹</p>
            ) : (
              <ul className={cn("mt-2 space-y-1.5 pr-1", SCROLL_THIN, "max-h-72")} data-testid="sandbox-trace-list">
                {p.traces.map((t) => {
                  const failIdx = t.steps.findIndex((s) => !s.passed);
                  return (
                    <li key={t.ruleId} className="flex items-start gap-2 rounded-xl border border-zinc-100 px-2.5 py-2">
                      {t.matched ? (
                        <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
                      ) : (
                        <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-zinc-300" />
                      )}
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="truncate text-[11px] font-bold text-zinc-700">{t.ruleName}</span>
                          <SourceBadge source={t.source} />
                        </div>
                        <div className={cn("mt-0.5 text-[10px] leading-relaxed", t.matched ? "font-semibold text-emerald-600" : "text-zinc-400")}>
                          {t.matched
                            ? `✓ 命中 ${t.hitNodeIds.length} 个节点 · 共 ${t.nodeCount} 个候选`
                            : failIdx >= 0
                              ? `✗ 未命中 · 失败在第 ${failIdx + 1} 步（${t.steps[failIdx].label}）`
                              : "✗ 未命中 · 树中无满足节点"}
                        </div>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {/* Winner 大卡片 */}
          {p.winner ? (
            <div className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50/70 p-3.5" data-testid="sandbox-winner-card">
              <div className="flex items-center gap-1.5 text-[10px] font-black tracking-wide text-emerald-600">
                <Trophy className="h-3.5 w-3.5" /> WINNER · 调度胜出
              </div>
              <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                <span className="text-sm font-black text-zinc-900">{p.winner.ruleName}</span>
                <SourceBadge source={p.winner.source} />
              </div>
              <div className="mt-2 grid grid-cols-2 gap-2">
                <div className="rounded-xl bg-white/80 px-2.5 py-2 ring-1 ring-emerald-100">
                  <div className="text-[9px] font-bold text-zinc-400">执行动作</div>
                  <div className="mt-0.5 truncate font-mono text-[11px] font-bold text-zinc-800">{p.winnerRule?.action ?? "—"}</div>
                </div>
                <div className="rounded-xl bg-white/80 px-2.5 py-2 ring-1 ring-emerald-100">
                  <div className="text-[9px] font-bold text-zinc-400">命中节点</div>
                  <div className="mt-0.5 text-[11px] font-black tabular-nums text-emerald-600">{p.winner.hitNodeIds.length} 个</div>
                </div>
              </div>
              {p.winnerRule ? (
                <div className="mt-2 break-all rounded-lg bg-zinc-900/95 px-2 py-1.5 font-mono text-[9px] leading-relaxed text-emerald-300">
                  {safeSerialize(p.winnerRule)}
                </div>
              ) : null}
            </div>
          ) : (
            <div className="mt-4 rounded-2xl border border-dashed border-zinc-300 bg-zinc-50/60 p-4 text-center" data-testid="sandbox-winner-empty">
              <ShieldCheck className="mx-auto h-5 w-5 text-zinc-400" />
              <p className="mt-1.5 text-[11px] font-bold text-zinc-500">无规则命中 · 引擎静默</p>
              <p className="mt-1 text-[10px] leading-relaxed text-zinc-400">
                若当前场景是「内容页对照」，这正是防误触验证的预期结果——零代点、零误触
              </p>
            </div>
          )}
        </>
      )}
    </section>
  );
}

/* ══════════════════════ 主组件 ══════════════════════ */

export function RuleSandbox() {
  /* ── 场景（模拟节点树） ── */
  const [sceneName, setSceneName] = useState("标准开屏");
  const [templateKey, setTemplateKey] = useState<string | null>("standard");
  const [root, setRoot] = useState<SimNode>(() => ensureUniqueIds(tplStandard()));
  const [selectedId, setSelectedId] = useState<string | null>(null);

  /* ── JSON 导入 / 导出 ── */
  const [jsonText, setJsonText] = useState("");
  const [jsonError, setJsonError] = useState<string | null>(null);

  /* ── 规则 ── */
  const [rules, setRules] = useState<DslRule[]>(defaultRules);
  const [dslInput, setDslInput] = useState("");
  const [gkdText, setGkdText] = useState("");
  const [gkdResult, setGkdResult] = useState<GkdImportResult | null>(null);
  const [gkdWarnOpen, setGkdWarnOpen] = useState(false);
  const [libLoading, setLibLoading] = useState(false);

  /* ── 单步调试 ── */
  const [stepMode, setStepMode] = useState(false);
  const [stepIndex, setStepIndex] = useState(0);

  /* ── 内联通知条（sonner Toaster 未挂载，按规范用内联提示条） ── */
  const [notice, setNotice] = useState<{ tone: "ok" | "err" | "info"; text: string } | null>(null);
  const noticeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const notify = useCallback((tone: "ok" | "err" | "info", text: string) => {
    setNotice({ tone, text });
    if (noticeTimer.current) clearTimeout(noticeTimer.current);
    noticeTimer.current = setTimeout(() => setNotice(null), 4500);
  }, []);
  useEffect(
    () => () => {
      if (noticeTimer.current) clearTimeout(noticeTimer.current);
    },
    [],
  );

  /* ── 派生数据 ── */
  const nodeCount = useMemo(() => countNodes(root), [root]);
  const selectedNode = useMemo(() => (selectedId ? findNode(root, selectedId) : null), [root, selectedId]);
  const activeRules = useMemo(() => rules.filter((r) => r.enabled), [rules]);

  /** 匹配引擎调用：契约异常时兜底为错误条，不崩页面 */
  const matchState = useMemo<{ result: TreeMatchResult | null; error: string | null }>(() => {
    if (activeRules.length === 0) return { result: null, error: null };
    try {
      return { result: matchTree(root, activeRules), error: null };
    } catch (e) {
      return { result: null, error: e instanceof Error ? e.message : String(e) };
    }
  }, [root, activeRules]);

  const winner = matchState.result?.winner ?? null;
  const order: OrderRow[] = matchState.result?.order ?? [];
  const traces: MatchTrace[] = matchState.result?.traces ?? [];
  const winnerRule = useMemo(() => (winner ? rules.find((r) => r.id === winner.ruleId) ?? null : null), [rules, winner]);

  /** 单步遍历对象：winner 优先，否则第一条 trace（观察失败路径同样有价值） */
  const stepTrace: MatchTrace | null = winner ?? (traces.length > 0 ? traces[0] : null);
  const steps: MatchStep[] = useMemo(() => stepTrace?.steps ?? [], [stepTrace]);

  /** DSL 实时校验：纯选择器自动包装为完整行格式后交给 compileDslLine */
  const compilePreview = useMemo<CompilePreview>(() => {
    const line = dslInput.trim();
    if (!line) return null;
    try {
      return compileDslLine(buildDslLine(line).line);
    } catch (e) {
      return { ok: false, error: `解析器异常：${e instanceof Error ? e.message : String(e)}` };
    }
  }, [dslInput]);

  /** 画布高亮：非单步 = winner 全量命中（emerald 呼吸）；单步 = 逐步揭示，当前步节点琥珀色 */
  const highlight = useMemo(() => {
    if (stepMode && stepTrace) {
      const revealed = stepTrace.hitNodeIds.slice(0, Math.min(stepIndex + 1, stepTrace.hitNodeIds.length));
      const currentId = revealed.length > 0 ? revealed[revealed.length - 1] : null;
      return { hit: new Set(revealed.slice(0, -1)), currentId };
    }
    return { hit: new Set(winner?.hitNodeIds ?? []), currentId: null as string | null };
  }, [stepMode, stepTrace, stepIndex, winner]);

  /** 场景或规则集变化 → 退出单步模式（步数已失效） */
  useEffect(() => {
    setStepMode(false);
    setStepIndex(0);
  }, [root, activeRules]);

  /* ── 场景操作 ── */
  const loadTemplate = useCallback((key: string) => {
    const t = TEMPLATES.find((x) => x.key === key);
    if (!t) return;
    setRoot(ensureUniqueIds(t.build()));
    setSceneName(t.name);
    setTemplateKey(t.key);
    setSelectedId(null);
    setJsonError(null);
  }, []);

  const patchSelected = useCallback(
    (patch: Partial<SimNode>) => {
      if (!selectedId) return;
      setRoot((cur) => mapNode(cur, selectedId, (n) => ({ ...n, ...patch })));
    },
    [selectedId],
  );

  const addChild = useCallback(() => {
    const parentId = selectedNode?.id ?? root.id;
    if (nodeCount >= NODE_LIMIT) {
      notify("err", `节点数已达上限 ${NODE_LIMIT}，无法继续添加`);
      return;
    }
    const child = ensureUniqueIds(
      safeMakeNode({ name: "android.widget.TextView", text: "新节点", x: 120, y: 120, w: 200, h: 64, clickable: true }),
      collectIds(root),
    );
    setRoot((cur) => insertChild(cur, parentId, child));
    setSelectedId(child.id);
  }, [selectedNode, root, nodeCount, notify]);

  const deleteSelected = useCallback(() => {
    if (!selectedId || selectedId === root.id) return;
    setRoot((cur) => removeNode(cur, selectedId));
    setSelectedId(null);
  }, [selectedId, root.id]);

  const duplicateSelected = useCallback(() => {
    if (!selectedNode || selectedNode.id === root.id) return;
    if (nodeCount + countNodes(selectedNode) > NODE_LIMIT) {
      notify("err", `复制后将超过节点上限 ${NODE_LIMIT}，请先精简节点树`);
      return;
    }
    const copy = ensureUniqueIds(selectedNode, collectIds(root));
    setRoot((cur) => insertSibling(cur, selectedNode.id, copy));
    setSelectedId(copy.id);
  }, [selectedNode, root, nodeCount, notify]);

  /* ── JSON 导入 / 导出 / 复制 ── */
  const exportJson = useCallback(() => {
    setJsonText(JSON.stringify({ name: sceneName, root }, null, 2));
    setJsonError(null);
    notify("ok", "已导出当前场景 JSON 到文本框");
  }, [sceneName, root, notify]);

  const importJson = useCallback(() => {
    const r = parseSceneJson(jsonText);
    if ("error" in r) {
      setJsonError(r.error);
      return;
    }
    setJsonError(null);
    setRoot(r.root);
    if (r.name) setSceneName(r.name);
    setTemplateKey(null);
    setSelectedId(null);
    notify("ok", `场景导入成功：${countNodes(r.root)} 个节点`);
  }, [jsonText, notify]);

  const copyJson = useCallback(async () => {
    const text = jsonText.trim() || JSON.stringify({ name: sceneName, root }, null, 2);
    if (!jsonText.trim()) setJsonText(text);
    const ok = await copyText(text);
    if (ok) notify("ok", "已复制到剪贴板");
    else notify("err", "复制失败：请手动选中文本复制");
  }, [jsonText, sceneName, root, notify]);

  /* ── 规则操作 ── */
  const toggleRule = useCallback((id: string, enabled: boolean) => {
    setRules((cur) => cur.map((r) => (r.id === id ? { ...r, enabled } : r)));
  }, []);

  const removeRule = useCallback((id: string) => {
    setRules((cur) => cur.filter((r) => r.id !== id));
  }, []);

  const addRuleFromDsl = useCallback(() => {
    const raw = dslInput.trim();
    if (!raw) return;
    const { line, fullLine } = buildDslLine(raw);
    let parsed: { ok: boolean; rule?: DslRule; error?: string };
    try {
      parsed = compileDslLine(line);
    } catch (e) {
      notify("err", `解析器异常：${e instanceof Error ? e.message : String(e)}`);
      return;
    }
    if (!parsed.ok || !parsed.rule) {
      notify("err", parsed.error ?? "DSL 语法错误");
      return;
    }
    const base = parsed.rule;
    const taken = new Set(rules.map((r) => r.id));
    const fallbackName = fullLine
      ? base.name && base.name.trim()
        ? base.name
        : line
      : raw.length > 22
        ? `${raw.slice(0, 22)}…`
        : raw;
    const rule: DslRule = {
      ...base,
      id: taken.has(base.id) ? genId("custom") : base.id,
      name: fallbackName,
      source: "custom",
    };
    setRules((cur) => [...cur, rule]);
    setDslInput("");
    notify("ok", `规则已添加：${rule.name}`);
  }, [dslInput, rules, notify]);

  const fillGkdExample = useCallback(() => {
    setGkdText(GKD_EXAMPLE);
    notify("info", "已填充内置 GKD 订阅示例（1 类别 · 2 分组 · 3 规则）");
  }, [notify]);

  const importGkd = useCallback(() => {
    if (!gkdText.trim()) {
      notify("err", "请先粘贴 GKD 订阅片段（可点「填充示例」）");
      return;
    }
    let parsed: { ok: boolean; ruleCount: number; groupCount: number; rules: DslRule[]; errors: string[]; warnings: string[] };
    try {
      parsed = parseGkdSubscription(gkdText, "手动订阅");
    } catch (e) {
      setGkdResult({
        ok: false,
        ruleCount: 0,
        groupCount: 0,
        rules: [],
        errors: [`解析器异常：${e instanceof Error ? e.message : String(e)}`],
        warnings: [],
      });
      return;
    }
    setGkdResult({
      ok: parsed.ok,
      ruleCount: parsed.ruleCount,
      groupCount: parsed.groupCount,
      rules: parsed.rules,
      errors: parsed.errors,
      warnings: parsed.warnings,
    });
    if (!parsed.ok) {
      setGkdWarnOpen(parsed.warnings.length > 0);
      notify("err", `订阅解析失败：${parsed.errors[0] ?? parsed.warnings[0] ?? "未识别到任何规则"}`);
      return;
    }
    if (parsed.rules.length === 0) {
      notify("info", `解析成功但未提取到规则（${parsed.groupCount} 个分组）`);
      return;
    }
    const taken = new Set(rules.map((r) => r.id));
    const incoming: DslRule[] = parsed.rules.map((r, i) => ({
      ...r,
      id: taken.has(r.id) ? `${r.id}-gkd${i}` : r.id,
      source: "gkd",
      enabled: true,
    }));
    setRules((cur) => [...cur, ...incoming]);
    setGkdWarnOpen(parsed.warnings.length > 0);
    notify(
      "ok",
      `导入成功：${incoming.length} 条规则 · ${parsed.groupCount} 个分组${parsed.warnings.length ? ` · ${parsed.warnings.length} 条警告` : ""}`,
    );
  }, [gkdText, rules, notify]);

  /** 从 /api/rules 导入：matchType → 演示 matcher 映射（真机端为全量特征） */
  const importLib = useCallback(async () => {
    setLibLoading(true);
    try {
      const r = await fetch("/api/rules");
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const d = await r.json();
      const list: LibRuleRow[] = Array.isArray(d?.rules) ? d.rules : [];
      const picked = new Map<string, LibRuleRow>();
      let skipped = 0;
      for (const row of list) {
        if (!row || typeof row !== "object" || typeof row.matchType !== "string") continue;
        if (LIB_DEMO_MATCHERS[row.matchType]) {
          if (!picked.has(row.matchType)) picked.set(row.matchType, row);
        } else {
          skipped += 1;
        }
      }
      const mapped: DslRule[] = [...picked.entries()].map(([mt, row]) => ({
        id: `lib-${row.id ?? mt}`,
        name: `${row.appName === "*" ? "通用" : row.appName || "未知应用"} · ${row.matchLabel ?? mt}（演示映射）`,
        source: row.source === "learned" ? "learned" : "preset",
        appName: row.appName ?? "*",
        adType: row.adType ?? "any",
        matchers: LIB_DEMO_MATCHERS[mt],
        action: row.action ?? "instant_skip",
        priority: Math.round((typeof row.confidence === "number" ? row.confidence : 0.9) * 100),
        enabled: true,
      }));
      setRules((cur) => [...cur.filter((x) => !x.id.startsWith("lib-")), ...mapped]);
      notify(
        "ok",
        `已导入 ${mapped.length} 条演示规则${skipped > 0 ? `，跳过 ${skipped} 条不可映射类型` : ""} · 演示映射：真机端为全量特征`,
      );
    } catch (e) {
      notify("err", `规则库加载失败：${e instanceof Error ? e.message : String(e)}`);
    }
    setLibLoading(false);
  }, [notify]);

  /* ── 单步调试控制 ── */
  const startDebug = useCallback(() => {
    if (steps.length === 0) {
      notify("info", "当前无可遍历的匹配步骤——先启用规则或换一个场景");
      return;
    }
    setStepMode(true);
    setStepIndex(0);
  }, [steps.length, notify]);

  const stepPrev = useCallback(() => setStepIndex((i) => Math.max(0, i - 1)), []);
  const stepNext = useCallback(() => setStepIndex((i) => Math.min(steps.length - 1, i + 1)), [steps.length]);
  const resetDebug = useCallback(() => {
    setStepMode(false);
    setStepIndex(0);
  }, []);

  /* ── 渲染 ── */
  return (
    <div className="flex flex-col gap-4" data-testid="rule-sandbox">
      {/* 头部 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5" data-testid="sandbox-header">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-600 text-white shadow-md shadow-violet-500/25">
            <FlaskConical className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <h2 className="flex flex-wrap items-center gap-2 text-base font-bold text-zinc-900">
              规则调试沙箱
              <span className="rounded-full bg-violet-50 px-1.5 py-0.5 text-[10px] font-bold text-violet-700 ring-1 ring-violet-200">
                v5.4.0 · 统一 DSL
              </span>
            </h2>
            <p className="mt-0.5 text-[11px] leading-relaxed text-zinc-400">
              自定义节点树 × GKD 快照选择器 × 实时匹配与单步调试——判定逻辑与真机引擎同源，输入即刷新
            </p>
          </div>
          <div className="ml-auto hidden shrink-0 flex-wrap items-center gap-1.5 md:flex">
            <span className="rounded-full bg-zinc-100 px-2.5 py-1 text-[10px] font-bold text-zinc-600">场景：{sceneName}</span>
            <span className="rounded-full bg-zinc-100 px-2.5 py-1 text-[10px] font-bold tabular-nums text-zinc-600">
              节点 {nodeCount}/{NODE_LIMIT}
            </span>
            <span className="rounded-full bg-zinc-100 px-2.5 py-1 text-[10px] font-bold tabular-nums text-zinc-600">
              启用规则 {activeRules.length}/{rules.length}
            </span>
          </div>
        </div>
        {notice ? <NoticeBar tone={notice.tone} text={notice.text} /> : null}
      </section>

      {/* 三栏：场景编辑器 / 画布 / 规则（移动端纵向堆叠） */}
      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-[280px_minmax(0,1fr)_320px]">
        <SceneEditorCard
          sceneName={sceneName}
          templateKey={templateKey}
          nodeCount={nodeCount}
          root={root}
          selectedNode={selectedNode}
          selectedId={selectedId}
          onSelectNode={setSelectedId}
          onPatchNode={patchSelected}
          onAddChild={addChild}
          onDeleteNode={deleteSelected}
          onDuplicateNode={duplicateSelected}
          onLoadTemplate={loadTemplate}
          jsonText={jsonText}
          onJsonTextChange={(t) => {
            setJsonText(t);
            if (jsonError) setJsonError(null);
          }}
          onImportJson={importJson}
          onExportJson={exportJson}
          onCopyJson={copyJson}
          jsonError={jsonError}
        />

        <CanvasCard
          sceneName={sceneName}
          nodeCount={nodeCount}
          root={root}
          winner={winner}
          activeRuleCount={activeRules.length}
          matchError={matchState.error}
          highlight={highlight}
          stepMode={stepMode}
          stepIndex={stepIndex}
          steps={steps}
          stepTraceName={stepTrace ? stepTrace.ruleName : null}
          onStartDebug={startDebug}
          onStepPrev={stepPrev}
          onStepNext={stepNext}
          onResetDebug={resetDebug}
        />

        <div className="flex flex-col gap-4">
          <RulesCard
            rules={rules}
            onToggleRule={toggleRule}
            onRemoveRule={removeRule}
            dslInput={dslInput}
            onDslInputChange={setDslInput}
            compilePreview={compilePreview}
            onAddRule={addRuleFromDsl}
            libLoading={libLoading}
            onImportLib={importLib}
            gkdText={gkdText}
            onGkdTextChange={setGkdText}
            onFillGkdExample={fillGkdExample}
            onImportGkd={importGkd}
            gkdResult={gkdResult}
            gkdWarnOpen={gkdWarnOpen}
            onToggleGkdWarn={() => setGkdWarnOpen((v) => !v)}
          />
          <ResultsCard
            order={order}
            traces={traces}
            winner={winner}
            winnerRule={winnerRule}
            matchError={matchState.error}
            ruleTotal={rules.length}
            enabledCount={activeRules.length}
          />
        </div>
      </div>
    </div>
  );
}
