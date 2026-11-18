"use client";

/**
 * 规则可视化管理（v5.4.0 多源统一调度）
 * ─────────────────────────────────────────────────────────────
 * · 统计条：四源计数（内置/GKD 订阅/学习/自定义）+ 平均质量分 + 状态分布
 * · 订阅源管理：列表 + GKD 订阅文本导入（POST /api/subscriptions）+ 删除
 * · 规则表格：启停 Switch / pattern 截断展开 / 来源与质量分徽章 /
 *   优先级行内微调 / 编辑与新建 Dialog / 批量启停（op:batch）
 * · 冲突检测：duplicate / overlap / contradict 三类 + 处置建议
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import { ACTION_LABEL, AD_TYPE_LABEL } from "@/lib/instant-engine";
import {
  ScrollText, RefreshCw, Plus, Pencil, Trash2, Search, Rss, ShieldAlert,
  ChevronDown, CheckCircle2, AlertTriangle, Inbox, Layers,
} from "lucide-react";

/* ── 数据契约 ── */
interface RuleRow {
  id: string; appName: string; appCategory: string; adType: string; matchType: string;
  matchLabel: string; pattern: string; action: string; source: string; confidence: number;
  hitCount: number; missCount: number; falseCount: number; enabled: boolean; priority: number;
  quality: number; status: string; sdkFamily: string | null; generalizedFrom: string | null;
  gkdRef: string | null; version: number; lastHitAt: number | null;
}
interface RulesStats {
  bySource: Record<string, number>;
  byStatus: Record<string, number>;
  avgQuality: number;
}
interface SubRow {
  id: string; name: string; url: string; kind: string; version: string | null;
  author: string | null; ruleCount: number; enabled: boolean; status: string;
  message: string | null; lastSyncAt: number | null;
}
interface ConflictRow {
  kind: string; severity: string; ruleAId: string; ruleBId: string;
  ruleAName: string; ruleBName: string; detail: string; suggestion: string;
}

/* ── 标签与配色（preset 锌灰 / gkd 青 / learned 粉 / custom 紫） ── */
const SOURCE_META: Record<string, { label: string; cls: string }> = {
  preset: { label: "内置", cls: "bg-zinc-100 text-zinc-600 ring-zinc-200" },
  gkd: { label: "GKD 订阅", cls: "bg-cyan-50 text-cyan-700 ring-cyan-200" },
  learned: { label: "学习", cls: "bg-rose-50 text-rose-700 ring-rose-200" },
  custom: { label: "自定义", cls: "bg-violet-50 text-violet-700 ring-violet-200" },
};
const STATUS_META: Record<string, { label: string; cls: string }> = {
  active: { label: "生效中", cls: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  cooling: { label: "降温中", cls: "bg-amber-50 text-amber-700 ring-amber-200" },
  retired: { label: "已淘汰", cls: "bg-zinc-100 text-zinc-500 ring-zinc-200" },
  shadow: { label: "阴影运行", cls: "bg-violet-50 text-violet-700 ring-violet-200" },
};
const CONFLICT_META: Record<string, { label: string; cls: string }> = {
  duplicate: { label: "重复规则", cls: "bg-orange-50 text-orange-700 ring-orange-200" },
  overlap: { label: "特征重叠", cls: "bg-yellow-50 text-yellow-700 ring-yellow-200" },
  contradict: { label: "动作矛盾", cls: "bg-rose-50 text-rose-700 ring-rose-200" },
};
const AD_TYPE_OPTIONS: { value: string; label: string }[] = [
  ...(Object.entries(AD_TYPE_LABEL).map(([value, label]) => ({ value, label }))),
  { value: "any", label: "任意" },
];
const ACTION_OPTIONS = ["instant_skip", "popup_close", "halfsheet_close", "decline_tap", "preroll_skip", "guard_skip"] as const;
const MATCH_OPTIONS = [
  { value: "text", label: "控件文本" },
  { value: "viewId", label: "控件 ID" },
  { value: "timing", label: "时间窗" },
  { value: "gkd-selector", label: "GKD 快照选择器" },
];
const adTypeText = (t: string) => AD_TYPE_OPTIONS.find((o) => o.value === t)?.label ?? t;
const actionText = (a: string) => (ACTION_LABEL as Record<string, string>)[a] ?? a;
const sourceMeta = (s: string) => SOURCE_META[s] ?? { label: s, cls: "bg-zinc-100 text-zinc-600 ring-zinc-200" };
const statusMeta = (s: string) => STATUS_META[s] ?? { label: s, cls: "bg-zinc-100 text-zinc-600 ring-zinc-200" };
const conflictMeta = (k: string) => CONFLICT_META[k] ?? { label: k, cls: "bg-zinc-100 text-zinc-600 ring-zinc-200" };
const fmtTime = (t: number | null) =>
  t ? new Date(t).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }) : "—";

/** 质量分配色：0 显示「—」，≥80 emerald、60-79 teal、40-59 amber、<40 rose */
function QualityCell({ q }: { q: number }) {
  if (!q) return <span className="text-zinc-300">—</span>;
  const tone = q >= 80 ? "text-emerald-600" : q >= 60 ? "text-teal-600" : q >= 40 ? "text-amber-600" : "text-rose-600";
  return (
    <span className={cn("font-bold tabular-nums", tone)}>{Math.round(q)}</span>
  );
}

export function RuleManager() {
  const { toast } = useToast();

  /* ── 数据态 ── */
  const [rules, setRules] = useState<RuleRow[]>([]);
  const [stats, setStats] = useState<RulesStats | null>(null);
  const [subs, setSubs] = useState<SubRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);

  /* ── 筛选与批量 ── */
  const [fSource, setFSource] = useState("all");
  const [fStatus, setFStatus] = useState("all");
  const [keyword, setKeyword] = useState("");
  const [batchMode, setBatchMode] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  /* ── 订阅导入 / 编辑 / 新建 / 删除 / 冲突 ── */
  const [importOpen, setImportOpen] = useState(false);
  const [importName, setImportName] = useState("");
  const [importContent, setImportContent] = useState("");
  const [importing, setImporting] = useState(false);
  const [editTarget, setEditTarget] = useState<RuleRow | null>(null);
  const [editPattern, setEditPattern] = useState("");
  const [editAction, setEditAction] = useState("instant_skip");
  const [editAdType, setEditAdType] = useState("splash");
  const [savingEdit, setSavingEdit] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [cAppName, setCAppName] = useState("");
  const [cAdType, setCAdType] = useState("splash");
  const [cMatchType, setCMatchType] = useState("text");
  const [cPattern, setCPattern] = useState("");
  const [cAction, setCAction] = useState("instant_skip");
  const [creating, setCreating] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<RuleRow | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [conflicts, setConflicts] = useState<{ list: ConflictRow[]; persisted: boolean } | null>(null);
  const [checking, setChecking] = useState(false);

  /* ── 加载 ── */
  const loadRules = useCallback(async () => {
    try {
      const r = await fetch("/api/rules");
      if (!r.ok) throw new Error("bad status");
      const d = await r.json();
      setRules((d.rules ?? []) as RuleRow[]);
      setStats(d.stats ?? null);
    } catch {
      toast({ title: "规则库加载失败", description: "请稍后重试", variant: "destructive" });
    }
  }, [toast]);

  const loadSubs = useCallback(async () => {
    try {
      const r = await fetch("/api/subscriptions");
      if (!r.ok) throw new Error("bad status");
      const d = await r.json();
      setSubs((d.subscriptions ?? []) as SubRow[]);
    } catch { /* 订阅源服务未就绪时静默（并列任务落地中） */ }
  }, []);

  useEffect(() => {
    void (async () => {
      await Promise.all([loadRules(), loadSubs()]);
      setLoading(false);
    })();
  }, [loadRules, loadSubs]);

  /* ── 统一请求（.ok 检查 + 错误 toast） ── */
  const api = useCallback(async (
    url: string, init: RequestInit, okMsg?: string,
  ): Promise<boolean> => {
    try {
      const r = await fetch(url, {
        headers: { "Content-Type": "application/json" },
        ...init,
      });
      const d = r.ok ? null : await r.json().catch(() => null);
      if (!r.ok) {
        toast({ title: "操作失败", description: d?.error ?? `服务返回 ${r.status}`, variant: "destructive" });
        return false;
      }
      if (okMsg) toast({ title: okMsg });
      return true;
    } catch {
      toast({ title: "网络异常", description: "请稍后重试", variant: "destructive" });
      return false;
    }
  }, [toast]);

  const reload = useCallback(async () => {
    await Promise.all([loadRules(), loadSubs()]);
  }, [loadRules, loadSubs]);

  /* ── 行操作 ── */
  const toggleEnabled = useCallback(async (r: RuleRow, enabled: boolean) => {
    setBusyId(r.id);
    setRules((prev) => prev.map((x) => (x.id === r.id ? { ...x, enabled } : x)));
    const ok = await api("/api/rules", { method: "PATCH", body: JSON.stringify({ id: r.id, enabled }) },
      enabled ? `已启用：${r.appName === "*" ? "全局规则" : r.appName}` : `已停用：${r.appName === "*" ? "全局规则" : r.appName}`);
    if (!ok) await loadRules();
    setBusyId(null);
  }, [api, loadRules]);

  const bumpPriority = useCallback(async (r: RuleRow, delta: 1 | -1) => {
    const next = (r.priority ?? 0) + delta;
    setBusyId(r.id);
    const ok = await api("/api/rules", { method: "PATCH", body: JSON.stringify({ id: r.id, priority: next }) });
    if (ok) await loadRules();
    setBusyId(null);
  }, [api, loadRules]);

  const batchSet = useCallback(async (enabled: boolean) => {
    if (selected.size === 0) return;
    const ok = await api("/api/rules", {
      method: "PATCH",
      body: JSON.stringify({ op: "batch", ids: [...selected], enabled }),
    }, enabled ? `批量启用 ${selected.size} 条规则` : `批量停用 ${selected.size} 条规则`);
    if (ok) {
      setSelected(new Set());
      await loadRules();
    }
  }, [api, loadRules, selected]);

  const saveEdit = useCallback(async () => {
    if (!editTarget) return;
    if (!editPattern.trim()) {
      toast({ title: "匹配特征不能为空", variant: "destructive" });
      return;
    }
    setSavingEdit(true);
    const ok = await api("/api/rules", {
      method: "PUT",
      body: JSON.stringify({ id: editTarget.id, pattern: editPattern.trim(), action: editAction, adType: editAdType }),
    }, "规则已更新");
    if (ok) {
      setEditTarget(null);
      await loadRules();
    }
    setSavingEdit(false);
  }, [api, editTarget, editPattern, editAction, editAdType, loadRules]);

  const createRule = useCallback(async () => {
    if (!cAppName.trim() || !cPattern.trim()) {
      toast({ title: "应用名与匹配特征不能为空", variant: "destructive" });
      return;
    }
    setCreating(true);
    const ok = await api("/api/rules", {
      method: "POST",
      body: JSON.stringify({
        appName: cAppName.trim(), adType: cAdType, matchType: cMatchType,
        pattern: cPattern.trim(), action: cAction,
      }),
    }, "新规则已创建（来源：自定义）");
    if (ok) {
      setCreateOpen(false);
      setCAppName(""); setCPattern("");
      await loadRules();
    }
    setCreating(false);
  }, [api, cAction, cAdType, cAppName, cMatchType, cPattern, loadRules]);

  const doDelete = useCallback(async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    const ok = await api("/api/rules", { method: "DELETE", body: JSON.stringify({ id: deleteTarget.id }) }, "规则已删除");
    if (ok) {
      setDeleteTarget(null);
      await loadRules();
    }
    setDeleting(false);
  }, [api, deleteTarget, loadRules]);

  const importSub = useCallback(async () => {
    if (!importContent.trim()) {
      toast({ title: "请粘贴 GKD 订阅文本", variant: "destructive" });
      return;
    }
    setImporting(true);
    const ok = await api("/api/subscriptions", {
      method: "POST",
      body: JSON.stringify({ name: importName.trim() || undefined, content: importContent.trim() }),
    }, "订阅导入成功，规则已并入统一调度");
    if (ok) {
      setImportOpen(false);
      setImportName(""); setImportContent("");
      await reload();
    }
    setImporting(false);
  }, [api, importContent, importName, reload]);

  const deleteSub = useCallback(async (s: SubRow) => {
    const ok = await api("/api/subscriptions", { method: "DELETE", body: JSON.stringify({ id: s.id }) }, `已删除订阅：${s.name}`);
    if (ok) await reload();
  }, [api, reload]);

  const checkConflicts = useCallback(async () => {
    setChecking(true);
    try {
      const r = await fetch("/api/rules/conflicts");
      if (!r.ok) throw new Error("bad status");
      const d = await r.json();
      setConflicts({ list: (d.conflicts ?? []) as ConflictRow[], persisted: !!d.persisted });
      if (!(d.conflicts ?? []).length) toast({ title: "未检测到冲突", description: "四源规则特征与动作无矛盾" });
    } catch {
      toast({ title: "冲突检测失败", description: "请稍后重试", variant: "destructive" });
    }
    setChecking(false);
  }, [toast]);

  /* ── 派生：统计与筛选 ── */
  const sourceCounts = useMemo(() => {
    const m: Record<string, number> = {};
    for (const r of rules) m[r.source] = (m[r.source] ?? 0) + 1;
    return m;
  }, [rules]);
  const statusCounts = useMemo(() => {
    const m: Record<string, number> = {};
    for (const r of rules) m[r.status ?? "active"] = (m[r.status ?? "active"] ?? 0) + 1;
    return m;
  }, [rules]);
  const avgQuality = useMemo(() => {
    const scored = rules.filter((r) => (r.quality ?? 0) > 0);
    if (stats?.avgQuality != null) return Math.round(stats.avgQuality);
    return scored.length ? Math.round(scored.reduce((s, r) => s + r.quality, 0) / scored.length) : 0;
  }, [rules, stats]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return rules.filter((r) => {
      if (fSource !== "all" && r.source !== fSource) return false;
      if (fStatus !== "all" && (r.status ?? "active") !== fStatus) return false;
      if (kw) {
        const hay = `${r.appName} ${r.pattern} ${r.matchLabel ?? r.matchType} ${r.action}`.toLowerCase();
        if (!hay.includes(kw)) return false;
      }
      return true;
    });
  }, [rules, fSource, fStatus, keyword]);

  const allChecked = filtered.length > 0 && filtered.every((r) => selected.has(r.id));
  const toggleSelectAll = () => {
    setSelected(allChecked ? new Set() : new Set(filtered.map((r) => r.id)));
  };
  const toggleSelect = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };
  const toggleExpand = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true" aria-label="规则管理加载中">
        <Skeleton className="h-20 rounded-2xl" />
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-52 rounded-2xl" />
          <Skeleton className="h-52 rounded-2xl" />
        </div>
        <Skeleton className="h-[420px] rounded-2xl" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {/* 头部 */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <ScrollText className="h-5 w-5 text-violet-600" /> 规则管理 · 多源统一调度
          </h2>
          <p className="mt-0.5 text-[11px] text-zinc-400">内置 / GKD 订阅 / 学习 / 自定义四源规则 · 统一 DSL · 优先级与质量分治理</p>
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" className="h-8 rounded-lg" onClick={() => void reload()}>
            <RefreshCw className="mr-1 h-3.5 w-3.5" /> 刷新
          </Button>
          <Button size="sm" className="h-8 rounded-lg bg-zinc-900 hover:bg-zinc-700" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1 h-3.5 w-3.5" /> 新建规则
          </Button>
        </div>
      </div>

      {/* 统计条 */}
      <section className="flex flex-wrap items-center gap-x-5 gap-y-3 rounded-2xl border border-zinc-200 bg-white p-4" aria-label="规则统计">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 text-[11px] font-bold text-zinc-400">规则来源</span>
          {Object.entries(SOURCE_META).map(([k, m]) => (
            <span key={k} className={cn("rounded-full px-2.5 py-1 text-[11px] font-bold ring-1", m.cls)}>
              {m.label} <span className="tabular-nums">{sourceCounts[k] ?? 0}</span>
            </span>
          ))}
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-[11px] font-bold text-zinc-400">平均质量分</span>
          <QualityCell q={avgQuality} />
          <span className="text-[10px] text-zinc-300">/ 100（Wilson 置信下界）</span>
        </div>
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="mr-1 text-[11px] font-bold text-zinc-400">状态分布</span>
          {Object.entries(STATUS_META).map(([k, m]) => (
            <span key={k} className={cn("rounded-full px-2.5 py-1 text-[11px] font-bold ring-1", m.cls)}>
              {m.label} <span className="tabular-nums">{statusCounts[k] ?? 0}</span>
            </span>
          ))}
        </div>
      </section>

      {/* 订阅源 + 冲突检测 */}
      <div className="grid gap-4 lg:grid-cols-2">
        {/* 订阅源管理 */}
        <section className="rounded-2xl border border-zinc-200 bg-white p-4" aria-label="订阅源管理">
          <div className="flex items-center justify-between">
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
              <Rss className="h-4 w-4 text-cyan-600" /> 订阅源管理
              <span className="rounded-full bg-cyan-50 px-2 py-0.5 text-[10px] font-bold text-cyan-700">{subs.length} 个</span>
            </h3>
            <Collapsible open={importOpen} onOpenChange={setImportOpen}>
              <CollapsibleTrigger asChild>
                <Button size="sm" variant="outline" className="h-8 rounded-lg">
                  导入订阅 <ChevronDown className={cn("ml-1 h-3.5 w-3.5 transition-transform", importOpen && "rotate-180")} />
                </Button>
              </CollapsibleTrigger>
            </Collapsible>
          </div>
          <p className="mt-0.5 text-[10px] text-zinc-400">GKD 社区订阅 100% 语法兼容（快照选择器 / 逻辑组合 / 窗口限定），导入即并入统一调度</p>

          <Collapsible open={importOpen} onOpenChange={setImportOpen}>
            <CollapsibleContent className="mt-3 space-y-2.5 rounded-xl bg-zinc-50 p-3 ring-1 ring-zinc-200">
              <div className="space-y-1.5">
                <Label htmlFor="sub-name" className="text-[11px] font-bold text-zinc-500">订阅名称（可选）</Label>
                <Input id="sub-name" value={importName} onChange={(e) => setImportName(e.target.value)}
                  placeholder="如：GKD 官方订阅 · 李跳跳规则" className="h-8 rounded-lg text-xs" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="sub-content" className="text-[11px] font-bold text-zinc-500">GKD 订阅文本</Label>
                <Textarea id="sub-content" value={importContent} onChange={(e) => setImportContent(e.target.value)}
                  placeholder="粘贴 GKD 订阅 TS 文本（var subscription = { id: ..., groups: [...] }）"
                  className="min-h-[96px] rounded-lg font-mono text-[11px] leading-relaxed" />
              </div>
              <Button size="sm" className="h-8 w-full rounded-lg bg-cyan-600 hover:bg-cyan-500" disabled={importing} onClick={() => void importSub()}>
                {importing ? "导入解析中…" : "解析并导入"}
              </Button>
            </CollapsibleContent>
          </Collapsible>

          {subs.length === 0 ? (
            <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-5 text-center text-[11px] text-zinc-400">
              暂无订阅源。粘贴 GKD 订阅文本导入，即可把社区规则并入统一调度
            </p>
          ) : (
            <ul className="mt-3 max-h-56 space-y-2 overflow-y-auto pr-1 [scrollbar-width:thin] [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-zinc-300">
              {subs.map((s) => (
                <li key={s.id} className="rounded-xl border border-zinc-100 bg-zinc-50/60 p-3">
                  <div className="flex items-center gap-2">
                    <span className="truncate text-xs font-bold text-zinc-800">{s.name}</span>
                    <span className={cn("shrink-0 rounded-full px-2 py-0.5 text-[9px] font-bold ring-1",
                      s.status === "ok" ? "bg-emerald-50 text-emerald-700 ring-emerald-200"
                        : s.status === "syncing" ? "bg-cyan-50 text-cyan-700 ring-cyan-200"
                          : s.status === "error" ? "bg-rose-50 text-rose-700 ring-rose-200"
                            : "bg-violet-50 text-violet-700 ring-violet-200")}>
                      {s.status === "ok" ? "正常" : s.status === "syncing" ? "同步中" : s.status === "error" ? "异常" : "已解析"}
                    </span>
                    <span className="ml-auto shrink-0 text-[10px] font-bold tabular-nums text-zinc-500">{s.ruleCount} 组规则</span>
                    <Button
                      size="sm" variant="ghost" aria-label={`删除订阅 ${s.name}`}
                      className="h-6 w-6 rounded-md p-0 text-zinc-400 hover:bg-rose-50 hover:text-rose-600"
                      onClick={() => void deleteSub(s)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                  <div className="mt-1 flex items-center gap-2 text-[10px] text-zinc-400">
                    <span className="truncate" title={s.url}>{s.url}</span>
                  </div>
                  <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[10px] text-zinc-400">
                    {s.version && <span>版本 {s.version}</span>}
                    {s.author && <span>作者 {s.author}</span>}
                    <span>最近同步 {fmtTime(s.lastSyncAt)}</span>
                    {s.message && <span className="w-full truncate text-zinc-500" title={s.message}>{s.message}</span>}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* 冲突检测 */}
        <section className="rounded-2xl border border-zinc-200 bg-white p-4" aria-label="冲突检测">
          <div className="flex items-center justify-between">
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
              <ShieldAlert className="h-4 w-4 text-amber-600" /> 冲突检测
              {conflicts && conflicts.list.length > 0 && (
                <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-700">{conflicts.list.length} 处</span>
              )}
            </h3>
            <Button size="sm" variant="outline" className="h-8 rounded-lg" disabled={checking} onClick={() => void checkConflicts()}>
              {checking ? "检测中…" : "检测冲突"}
            </Button>
          </div>
          <p className="mt-0.5 text-[10px] text-zinc-400">
            四源规则两两比对：重复（同特征同动作）/ 重叠（特征包含）/ 矛盾（同特征异动作），并给出处置建议
          </p>

          {!conflicts ? (
            <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-5 text-center text-[11px] text-zinc-400">
              点击「检测冲突」扫描全量规则（检测记录会留痕，供统一调度参考）
            </p>
          ) : conflicts.list.length === 0 ? (
            <div className="mt-3 flex items-center gap-2 rounded-lg bg-emerald-50/60 px-3 py-4 text-[11px] font-semibold text-emerald-700 ring-1 ring-emerald-100">
              <CheckCircle2 className="h-4 w-4 shrink-0" /> 未检测到冲突，四源规则特征与动作无矛盾
            </div>
          ) : (
            <ul className="mt-3 max-h-56 space-y-2 overflow-y-auto pr-1 [scrollbar-width:thin] [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-zinc-300">
              {conflicts.list.map((c, i) => {
                const m = conflictMeta(c.kind);
                return (
                  <li key={`${c.ruleAId}-${c.ruleBId}-${i}`} className="rounded-xl border border-zinc-100 bg-zinc-50/60 p-3">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className={cn("rounded-full px-2 py-0.5 text-[9px] font-bold ring-1", m.cls)}>{m.label}</span>
                      <span className={cn("rounded-full px-2 py-0.5 text-[9px] font-bold ring-1",
                        c.severity === "high" ? "bg-rose-50 text-rose-700 ring-rose-200"
                          : c.severity === "medium" ? "bg-amber-50 text-amber-700 ring-amber-200"
                            : "bg-zinc-100 text-zinc-500 ring-zinc-200")}>
                        {c.severity === "high" ? "高" : c.severity === "medium" ? "中" : "低"}
                      </span>
                      <span className="ml-auto text-[9px] text-zinc-300">
                        <AlertTriangle className="mr-0.5 inline h-3 w-3" />#{i + 1}
                      </span>
                    </div>
                    <div className="mt-1.5 text-[11px] font-bold text-zinc-700">
                      {c.ruleAName} <span className="mx-1 font-normal text-zinc-300">vs</span> {c.ruleBName}
                    </div>
                    <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">{c.detail}</p>
                    <p className="mt-1 text-[11px] leading-relaxed text-teal-700">建议：{c.suggestion}</p>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>

      {/* 工具栏 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4" aria-label="规则列表">
        <div className="flex flex-wrap items-center gap-2">
          <Select value={fSource} onValueChange={setFSource}>
            <SelectTrigger size="sm" className="h-8 w-[120px] rounded-lg text-xs" aria-label="来源筛选">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部来源</SelectItem>
              {Object.entries(SOURCE_META).map(([k, m]) => (
                <SelectItem key={k} value={k}>{m.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={fStatus} onValueChange={setFStatus}>
            <SelectTrigger size="sm" className="h-8 w-[120px] rounded-lg text-xs" aria-label="状态筛选">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部状态</SelectItem>
              {Object.entries(STATUS_META).map(([k, m]) => (
                <SelectItem key={k} value={k}>{m.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <div className="relative min-w-[180px] flex-1">
            <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-zinc-400" />
            <Input
              value={keyword} onChange={(e) => setKeyword(e.target.value)}
              placeholder="搜索应用 / 特征 / 匹配方式 / 动作" aria-label="关键词搜索"
              className="h-8 rounded-lg pl-8 text-xs" />
          </div>
          <Button
            size="sm" variant={batchMode ? "default" : "outline"}
            className={cn("h-8 rounded-lg", batchMode ? "bg-zinc-900 hover:bg-zinc-700" : "")}
            onClick={() => { setBatchMode((v) => !v); setSelected(new Set()); }}
          >
            <Layers className="mr-1 h-3.5 w-3.5" /> {batchMode ? "退出批量" : "批量模式"}
          </Button>
          {batchMode && (
            <>
              <Button size="sm" variant="outline" className="h-8 rounded-lg border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 hover:text-emerald-800"
                disabled={selected.size === 0} onClick={() => void batchSet(true)}>
                批量启用（{selected.size}）
              </Button>
              <Button size="sm" variant="outline" className="h-8 rounded-lg border-zinc-200 bg-zinc-50 text-zinc-600 hover:bg-zinc-100"
                disabled={selected.size === 0} onClick={() => void batchSet(false)}>
                批量停用（{selected.size}）
              </Button>
            </>
          )}
          <span className="ml-auto text-[10px] tabular-nums text-zinc-400">
            {filtered.length} / {rules.length} 条
          </span>
        </div>

        {/* 规则表格 */}
        {filtered.length === 0 ? (
          <div className="mt-4 flex flex-col items-center gap-2 rounded-xl border border-dashed border-zinc-200 px-4 py-10 text-center">
            <Inbox className="h-8 w-8 text-zinc-300" />
            <p className="text-xs font-bold text-zinc-500">{rules.length === 0 ? "规则库为空" : "没有符合筛选条件的规则"}</p>
            <p className="text-[10px] text-zinc-400">{rules.length === 0 ? "点击右上角「新建规则」或导入 GKD 订阅开始" : "调整来源 / 状态 / 关键词试试"}</p>
          </div>
        ) : (
          <div className="mt-3 max-h-[560px] overflow-auto rounded-xl ring-1 ring-zinc-200 [scrollbar-width:thin] [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-zinc-300 hover:[&::-webkit-scrollbar-thumb]:bg-zinc-400">
            <table className="w-full min-w-[1040px] text-left text-[11px]">
              <thead className="sticky top-0 z-10 bg-zinc-50 shadow-[0_1px_0_#e4e4e7]">
                <tr className="text-[10px] font-bold text-zinc-500">
                  {batchMode && (
                    <th className="w-9 px-3 py-2.5">
                      <Checkbox aria-label="全选当前筛选结果" checked={allChecked} onCheckedChange={toggleSelectAll} />
                    </th>
                  )}
                  <th className="w-14 px-3 py-2.5">启用</th>
                  <th className="px-3 py-2.5">规则（匹配特征）</th>
                  <th className="px-3 py-2.5">应用</th>
                  <th className="px-3 py-2.5">匹配方式</th>
                  <th className="px-3 py-2.5">广告类型</th>
                  <th className="px-3 py-2.5">动作</th>
                  <th className="px-3 py-2.5">来源</th>
                  <th className="px-3 py-2.5 text-right">质量分</th>
                  <th className="px-3 py-2.5 text-right">命中</th>
                  <th className="px-3 py-2.5 text-center">优先级</th>
                  <th className="px-3 py-2.5 text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((r) => {
                  const sm = sourceMeta(r.source);
                  const isPreset = r.source === "preset";
                  const isOpen = expanded.has(r.id);
                  return (
                    <tr key={r.id} className={cn(
                      "border-b border-zinc-100 last:border-0 align-top hover:bg-zinc-50/60",
                      !r.enabled && "opacity-55",
                    )}>
                      {batchMode && (
                        <td className="px-3 py-3">
                          <Checkbox aria-label={`选择规则 ${r.appName}`} checked={selected.has(r.id)} onCheckedChange={() => toggleSelect(r.id)} />
                        </td>
                      )}
                      <td className="px-3 py-3">
                        <Switch
                          checked={r.enabled} disabled={busyId === r.id} aria-label={`${r.enabled ? "停用" : "启用"}规则 ${r.appName}`}
                          onCheckedChange={(v) => void toggleEnabled(r, v)}
                          className="data-[state=checked]:bg-emerald-500" />
                      </td>
                      <td className="max-w-[300px] px-3 py-3">
                        <button
                          type="button" onClick={() => toggleExpand(r.id)} title="点击展开/收起全文"
                          className={cn("w-full text-left text-zinc-600 transition", isOpen ? "whitespace-pre-wrap break-all leading-relaxed" : "line-clamp-2")}
                        >
                          {r.pattern}
                        </button>
                        {r.gkdRef && <div className="mt-1 truncate font-mono text-[9px] text-cyan-600">{r.gkdRef}</div>}
                      </td>
                      <td className="px-3 py-3">
                        {r.appName === "*" ? (
                          <Badge variant="outline" className="border-emerald-200 bg-emerald-50 text-[10px] font-bold text-emerald-700">全局</Badge>
                        ) : (
                          <span className="font-bold text-zinc-800">{r.appName}</span>
                        )}
                        <div className="mt-0.5 text-[9px] text-zinc-400">
                          {r.sdkFamily ? `SDK ${r.sdkFamily}` : r.appCategory}
                          {r.generalizedFrom && ` · 泛化自 ${r.generalizedFrom}`}
                        </div>
                      </td>
                      <td className="px-3 py-3 text-zinc-600">{r.matchLabel ?? r.matchType}</td>
                      <td className="px-3 py-3 text-zinc-600">{adTypeText(r.adType)}</td>
                      <td className="max-w-[130px] px-3 py-3">
                        <span className="block truncate text-zinc-600" title={actionText(r.action)}>{actionText(r.action)}</span>
                      </td>
                      <td className="px-3 py-3">
                        <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-bold ring-1", sm.cls)}>{sm.label}</span>
                      </td>
                      <td className="px-3 py-3 text-right"><QualityCell q={r.quality ?? 0} /></td>
                      <td className="px-3 py-3 text-right">
                        <span className="font-bold tabular-nums text-zinc-700">{r.hitCount ?? 0}</span>
                        <div className="text-[9px] tabular-nums text-zinc-400" title={`误触 ${r.falseCount ?? 0} 次 · 漏拦截 ${r.missCount ?? 0} 次`}>
                          误{r.falseCount ?? 0} 漏{r.missCount ?? 0}
                        </div>
                      </td>
                      <td className="px-3 py-3">
                        <div className="flex items-center justify-center gap-1">
                          <Button
                            size="sm" variant="outline" aria-label={`降低 ${r.appName} 规则优先级`}
                            className="h-5 w-5 rounded-md p-0" disabled={busyId === r.id}
                            onClick={() => void bumpPriority(r, -1)}
                          >
                            −
                          </Button>
                          <span className="w-6 text-center font-bold tabular-nums text-zinc-700">{r.priority ?? 0}</span>
                          <Button
                            size="sm" variant="outline" aria-label={`提高 ${r.appName} 规则优先级`}
                            className="h-5 w-5 rounded-md p-0" disabled={busyId === r.id}
                            onClick={() => void bumpPriority(r, 1)}
                          >
                            ＋
                          </Button>
                        </div>
                      </td>
                      <td className="px-3 py-3">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            size="sm" variant="ghost" aria-label={`编辑规则 ${r.appName}`}
                            disabled={isPreset || busyId === r.id}
                            title={isPreset ? "内置规则由官方维护，不可编辑" : "编辑规则"}
                            className="h-6 w-6 rounded-md p-0 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 disabled:opacity-30"
                            onClick={() => {
                              setEditTarget(r);
                              setEditPattern(r.pattern);
                              setEditAction(ACTION_OPTIONS.includes(r.action as (typeof ACTION_OPTIONS)[number]) ? r.action : "instant_skip");
                              setEditAdType(AD_TYPE_OPTIONS.some((o) => o.value === r.adType) ? r.adType : "splash");
                            }}
                          >
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          <Button
                            size="sm" variant="ghost" aria-label={`删除规则 ${r.appName}`}
                            disabled={isPreset || busyId === r.id}
                            title={isPreset ? "内置规则由官方维护，不可删除" : "删除规则"}
                            className="h-6 w-6 rounded-md p-0 text-zinc-400 hover:bg-rose-50 hover:text-rose-600 disabled:opacity-30"
                            onClick={() => setDeleteTarget(r)}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* 编辑 Dialog */}
      <Dialog open={!!editTarget} onOpenChange={(v) => { if (!v) setEditTarget(null); }}>
        <DialogContent className="max-w-lg rounded-2xl">
          <DialogHeader>
            <DialogTitle>编辑规则{editTarget && editTarget.appName !== "*" ? ` · ${editTarget.appName}` : " · 全局"}</DialogTitle>
            <DialogDescription className="text-[11px] leading-relaxed">
              修改匹配特征 / 动作 / 广告类型；保存后规则版本号自增（version + 1），进入统一调度审计
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3.5">
            <div className="space-y-1.5">
              <Label htmlFor="edit-pattern" className="text-[11px] font-bold text-zinc-500">匹配特征（pattern）</Label>
              <Textarea id="edit-pattern" value={editPattern} onChange={(e) => setEditPattern(e.target.value)}
                className="min-h-[110px] rounded-lg font-mono text-[11px] leading-relaxed" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-[11px] font-bold text-zinc-500">动作（action）</Label>
                <Select value={editAction} onValueChange={setEditAction}>
                  <SelectTrigger className="h-9 rounded-lg text-xs" aria-label="选择动作"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {ACTION_OPTIONS.map((a) => (
                      <SelectItem key={a} value={a}>{actionText(a)}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label className="text-[11px] font-bold text-zinc-500">广告类型（adType）</Label>
                <Select value={editAdType} onValueChange={setEditAdType}>
                  <SelectTrigger className="h-9 rounded-lg text-xs" aria-label="选择广告类型"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {AD_TYPE_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <DialogFooter className="gap-2 sm:flex-row sm:justify-end">
            <Button size="sm" variant="outline" className="h-9 rounded-lg" onClick={() => setEditTarget(null)}>取消</Button>
            <Button size="sm" className="h-9 rounded-lg bg-zinc-900 hover:bg-zinc-700" disabled={savingEdit} onClick={() => void saveEdit()}>
              {savingEdit ? "保存中…" : "保存修改"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 新建规则 Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg rounded-2xl">
          <DialogHeader>
            <DialogTitle>新建规则</DialogTitle>
            <DialogDescription className="text-[11px] leading-relaxed">
              以「自定义」来源入库，参与统一调度与质量评分；应用名填 * 即全局规则
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3.5">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="c-app" className="text-[11px] font-bold text-zinc-500">应用名（* = 全局）</Label>
                <Input id="c-app" value={cAppName} onChange={(e) => setCAppName(e.target.value)}
                  placeholder="如：喜马拉雅 或 *" className="h-9 rounded-lg text-xs" />
              </div>
              <div className="space-y-1.5">
                <Label className="text-[11px] font-bold text-zinc-500">匹配方式</Label>
                <Select value={cMatchType} onValueChange={setCMatchType}>
                  <SelectTrigger className="h-9 rounded-lg text-xs" aria-label="选择匹配方式"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {MATCH_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-[11px] font-bold text-zinc-500">广告类型</Label>
                <Select value={cAdType} onValueChange={setCAdType}>
                  <SelectTrigger className="h-9 rounded-lg text-xs" aria-label="选择广告类型"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {AD_TYPE_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label className="text-[11px] font-bold text-zinc-500">动作</Label>
                <Select value={cAction} onValueChange={setCAction}>
                  <SelectTrigger className="h-9 rounded-lg text-xs" aria-label="选择动作"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {ACTION_OPTIONS.map((a) => (
                      <SelectItem key={a} value={a}>{actionText(a)}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="c-pattern" className="text-[11px] font-bold text-zinc-500">匹配特征（pattern）</Label>
              <Textarea id="c-pattern" value={cPattern} onChange={(e) => setCPattern(e.target.value)}
                placeholder={'如：[text*="跳过"] || [vid="btn_skip"]'}
                className="min-h-[90px] rounded-lg font-mono text-[11px] leading-relaxed" />
            </div>
          </div>
          <DialogFooter className="gap-2 sm:flex-row sm:justify-end">
            <Button size="sm" variant="outline" className="h-9 rounded-lg" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button size="sm" className="h-9 rounded-lg bg-zinc-900 hover:bg-zinc-700" disabled={creating} onClick={() => void createRule()}>
              {creating ? "创建中…" : "创建规则"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除确认 */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(v) => { if (!v) setDeleteTarget(null); }}>
        <AlertDialogContent className="max-w-sm rounded-2xl">
          <AlertDialogHeader>
            <AlertDialogTitle>删除这条规则？</AlertDialogTitle>
            <AlertDialogDescription className="text-[11px] leading-relaxed">
              {deleteTarget?.appName === "*" ? "全局规则" : deleteTarget?.appName}
              · 来源 {sourceMeta(deleteTarget?.source ?? "").label} · 命中 {deleteTarget?.hitCount ?? 0} 次。删除后不可恢复（可重新创建或导入）。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter className="gap-2 sm:flex-row sm:justify-end">
            <AlertDialogCancel className="h-9 rounded-lg">取消</AlertDialogCancel>
            <AlertDialogAction
              className="h-9 rounded-lg bg-rose-600 hover:bg-rose-500" disabled={deleting}
              onClick={(e) => { e.preventDefault(); void doDelete(); }}
            >
              {deleting ? "删除中…" : "确认删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
