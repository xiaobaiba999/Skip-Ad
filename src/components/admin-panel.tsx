"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AdData, AdStat, AdStrategy, THEMES, STRATEGIES, STRATEGY_META } from "@/lib/types";
import type { LearnEventItem } from "@/lib/judge";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { Eye, MousePointerClick, Percent, Plus, Trash2, RefreshCw, Megaphone, Brain, ShieldCheck, MessageSquareWarning, CloudDownload, ExternalLink, CheckCircle2 } from "lucide-react";
import { APP_VERSION, RELEASES_PAGE, ISSUES_PAGE } from "@/lib/version";

const THEME_LABEL: Record<string, string> = {
  ecommerce: "电商促销",
  game: "游戏首发",
  vip: "会员特惠",
  finance: "理财推广",
};

const KIND_LABEL: Record<string, string> = {
  decision: "判定",
  correction: "纠正",
  skip: "跳过",
  engage: "长停留",
  quickback: "秒退",
  cancel: "取消二跳",
};

interface LearnPanel {
  stats: { blocked: number; allowed: number; wouldBlock: number; corrections: number; precision: number };
  rules: { themeBias: Record<string, number>; relaxQuickTap: boolean; autoSkipDelayMs: number | null; samples: number };
  recent: LearnEventItem[];
}

export function AdminPanel({ refreshSignal }: { refreshSignal: number }) {
  const [stats, setStats] = useState<AdStat[]>([]);
  const [totals, setTotals] = useState({ views: 0, clicks: 0, ctr: 0 });
  const [learn, setLearn] = useState<LearnPanel | null>(null);
  const [loading, setLoading] = useState(true);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = useCallback(async () => {
    try {
      const [res1, res2] = await Promise.all([fetch("/api/stats"), fetch("/api/learn")]);
      const data = await res1.json();
      setStats(data.stats ?? []);
      setTotals(data.totals ?? { views: 0, clicks: 0, ctr: 0 });
      if (res2.ok) setLearn(await res2.json());
    } catch { /* 忽略 */ }
    finally { setLoading(false); }
  }, []);

  useEffect(() => {
    load();
    timer.current = setInterval(load, 5000); // 轮询兜底，广告事件即时刷新由 refreshSignal 驱动
    return () => { if (timer.current) clearInterval(timer.current); };
  }, [load, refreshSignal]);

  const toggleActive = async (ad: AdStat) => {
    await fetch(`/api/ads/${ad.id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ active: !ad.active }),
    });
    load();
  };

  const remove = async (id: string) => {
    await fetch(`/api/ads/${id}`, { method: "DELETE" });
    load();
  };

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 overflow-y-auto p-4 lg:p-6">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-bold text-zinc-900">
          <Megaphone className="h-5 w-5 text-violet-600" /> 广告投放后台
        </h2>
        <p className="mt-1 text-xs text-zinc-500">
          手机里的每次曝光与点击都会实时统计到下方看板（每 5 秒自动同步）
        </p>
      </div>

      {/* 数据看板 */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "总曝光", value: totals.views.toLocaleString(), icon: Eye, tint: "text-violet-600 bg-violet-50" },
          { label: "总点击", value: totals.clicks.toLocaleString(), icon: MousePointerClick, tint: "text-rose-600 bg-rose-50" },
          { label: "点击率 CTR", value: `${(totals.ctr * 100).toFixed(1)}%`, icon: Percent, tint: "text-emerald-600 bg-emerald-50" },
        ].map(({ label, value, icon: Icon, tint }) => (
          <Card key={label} className="gap-2 py-4">
            <CardContent className="flex flex-col items-center gap-1.5 px-3">
              <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${tint}`}><Icon className="h-4 w-4" /></span>
              <span className="text-xl font-bold tabular-nums text-zinc-900">{value}</span>
              <span className="text-[11px] text-zinc-500">{label}</span>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 广告列表 */}
      <Card className="min-h-0">
        <CardHeader className="flex flex-row items-center justify-between py-4">
          <CardTitle className="text-sm">广告计划（{stats.length}）</CardTitle>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="icon" className="h-8 w-8" onClick={load} aria-label="刷新数据">
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            </Button>
            <NewAdDialog onCreated={load} />
          </div>
        </CardHeader>
        <CardContent className="px-0 pb-0">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="pl-4">广告计划</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>策略</TableHead>
                <TableHead className="text-right">曝光</TableHead>
                <TableHead className="text-right">点击</TableHead>
                <TableHead className="text-right">CTR</TableHead>
                <TableHead className="text-center">投放</TableHead>
                <TableHead className="pr-4" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {stats.map((s) => (
                <TableRow key={s.id}>
                  <TableCell className="max-w-40 truncate pl-4 font-medium">{s.name}</TableCell>
                  <TableCell>
                    <Badge variant={s.type === "splash" ? "default" : "secondary"} className="text-[10px]">
                      {s.type === "splash" ? "开屏" : "插屏"}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={s.strategy === "standard" ? "outline" : "secondary"}
                      className={s.strategy === "standard" ? "text-[10px] text-zinc-500" : "text-[10px] text-violet-600"}
                    >
                      {STRATEGY_META[(s.strategy as AdStrategy) ?? "standard"]?.label ?? "标准"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{s.views.toLocaleString()}</TableCell>
                  <TableCell className="text-right tabular-nums">{s.clicks.toLocaleString()}</TableCell>
                  <TableCell className="text-right tabular-nums">{(s.ctr * 100).toFixed(1)}%</TableCell>
                  <TableCell className="text-center">
                    <Switch checked={s.active} onCheckedChange={() => toggleActive(s)} aria-label={`启停 ${s.name}`} />
                  </TableCell>
                  <TableCell className="pr-4 text-right">
                    <Button variant="ghost" size="icon" className="h-7 w-7 text-zinc-400 hover:text-rose-500" onClick={() => remove(s.id)} aria-label={`删除 ${s.name}`}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {stats.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} className="py-8 text-center text-xs text-zinc-400">暂无广告，点击右上角新建</TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* 智能学习报告 */}
      <LearningReport learn={learn} />

      {/* 问题反馈（同步 GitHub Issues）与 版本更新（GitHub Releases） */}
      <FeedbackCard learn={learn} />
      <UpdateCard />
    </div>
  );
}

/* ───────── 问题反馈：记录使用中遇到的问题，同步 GitHub Issues ───────── */

interface FbHistoryItem { ts: number; type: string; desc: string; url?: string }

function FeedbackCard({ learn }: { learn: LearnPanel | null }) {
  const [type, setType] = useState("false_block");
  const [desc, setDesc] = useState("");
  const [contact, setContact] = useState("");
  const [attaching, setAttaching] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; url?: string; error?: string; fallbackUrl?: string } | null>(null);
  const [history, setHistory] = useState<FbHistoryItem[]>([]);

  useEffect(() => {
    try { setHistory(JSON.parse(localStorage.getItem("skip-ad-feedback-history") ?? "[]")); } catch { /* 忽略 */ }
  }, []);

  const buildContext = () => {
    const st = learn?.stats;
    return [
      `- 应用版本：v${APP_VERSION}（Web 演示端）`,
      `- 判定引擎：拦截 ${st?.blocked ?? 0} · 放行 ${st?.allowed ?? 0} · 误判纠正 ${st?.corrections ?? 0} · 精准度 ${st ? Math.round(st.precision * 100) : "--"}%`,
      `- 跳过样本：${learn?.rules?.samples ?? 0} · 自动跳过时机：${learn?.rules?.autoSkipDelayMs ? `${(learn.rules.autoSkipDelayMs / 1000).toFixed(1)}s` : "学习中"}`,
      `- 平台：${navigator.userAgent.includes("Mobile") ? "移动端" : "桌面端"} · ${screen.width}×${screen.height}`,
      `- 提交时间：${new Date().toLocaleString("zh-CN")}`,
    ].join("\n");
  };

  const submit = async () => {
    if (desc.trim().length < 5) { setResult({ ok: false, error: "请把问题描述得更具体一点（至少 5 个字）" }); return; }
    setSubmitting(true); setResult(null);
    try {
      const res = await fetch("/api/feedback", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type, desc, contact, context: attaching ? buildContext() : "" }),
      });
      const data = await res.json();
      setResult(data);
      if (data.ok) {
        const item: FbHistoryItem = { ts: Date.now(), type, desc: desc.trim().slice(0, 60), url: data.url };
        const next = [item, ...history].slice(0, 20);
        setHistory(next);
        localStorage.setItem("skip-ad-feedback-history", JSON.stringify(next));
        setDesc(""); setContact("");
      }
    } catch {
      setResult({ ok: false, error: "网络异常，请稍后重试" });
    } finally { setSubmitting(false); }
  };

  const TYPE_OPTIONS = [
    { v: "false_block", l: "误拦截：这是我要的操作却被拦了" },
    { v: "missed", l: "漏拦截：广告诱导跳转没被拦住" },
    { v: "misclick", l: "误触观察有误" },
    { v: "crash", l: "闪退 / 卡顿" },
    { v: "suggestion", l: "功能建议" },
    { v: "other", l: "其他问题" },
  ];

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between py-4">
        <CardTitle className="flex items-center gap-2 text-sm">
          <MessageSquareWarning className="h-4 w-4 text-amber-500" /> 问题反馈 · 同步 GitHub Issues
        </CardTitle>
        <a href={ISSUES_PAGE} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-[11px] text-zinc-400 hover:text-violet-600">
          查看全部 <ExternalLink className="h-3 w-3" />
        </a>
      </CardHeader>
      <CardContent className="space-y-3 px-4 pb-4">
        <p className="text-[11px] text-zinc-500">
          使用中遇到的问题会以 Issue 形式记录到开源仓库，成为下一版本迭代的依据；可自动附带引擎画像便于定位问题。
        </p>
        <div className="grid gap-3 md:grid-cols-2">
          <div className="grid gap-1.5">
            <Label>问题类型</Label>
            <Select value={type} onValueChange={setType}>
              <SelectTrigger className="h-9"><SelectValue /></SelectTrigger>
              <SelectContent>
                {TYPE_OPTIONS.map((o) => (
                  <SelectItem key={o.v} value={o.v}>{o.l}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="fb-contact">联系方式（可选）</Label>
            <Input id="fb-contact" value={contact} onChange={(e) => setContact(e.target.value)} placeholder="如 GitHub / 邮箱，便于回访" />
          </div>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="fb-desc">问题描述 *</Label>
          <Textarea id="fb-desc" value={desc} onChange={(e) => setDesc(e.target.value)} rows={3}
            placeholder="例：某 App 开屏广告倒计时结束自动跳转，拦截模式没有拦住，跳到了应用商店……" />
        </div>
        <label className="flex cursor-pointer items-center gap-2 text-xs text-zinc-600">
          <input type="checkbox" checked={attaching} onChange={(e) => setAttaching(e.target.checked)}
            className="h-3.5 w-3.5 accent-violet-600" />
          附带引擎运行画像（版本 / 拦截放行统计 / 精准度，不含个人数据）
        </label>
        <div className="flex items-center gap-3">
          <Button size="sm" onClick={submit} disabled={submitting} className="bg-violet-600 hover:bg-violet-700">
            {submitting ? "同步中…" : "提交并同步到 GitHub"}
          </Button>
          {result?.ok && result.url && (
            <a href={result.url} target="_blank" rel="noreferrer" className="flex items-center gap-1 text-xs font-medium text-emerald-600 hover:underline">
              <CheckCircle2 className="h-3.5 w-3.5" /> 已记录为 Issue #{result.url.split("/").pop()} <ExternalLink className="h-3 w-3" />
            </a>
          )}
          {!result?.ok && result?.error && <span className="text-xs text-rose-500">{result.error}</span>}
          {!result?.ok && result?.fallbackUrl && (
            <a href={result.fallbackUrl} target="_blank" rel="noreferrer" className="text-xs text-violet-600 hover:underline">在浏览器中手动提交 →</a>
          )}
        </div>
        {history.length > 0 && (
          <div className="space-y-1 border-t border-zinc-100 pt-2">
            <div className="text-[11px] font-semibold text-zinc-500">我的反馈记录（本地保存）</div>
            {history.slice(0, 5).map((h) => (
              <div key={h.ts} className="flex items-center gap-2 text-[11px] text-zinc-500">
                <span className="tabular-nums text-zinc-400">{new Date(h.ts).toLocaleDateString("zh-CN")}</span>
                <span className="min-w-0 flex-1 truncate">{h.desc}</span>
                {h.url && <a href={h.url} target="_blank" rel="noreferrer" className="shrink-0 text-violet-600 hover:underline">#{h.url.split("/").pop()}</a>}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/* ───────── 版本更新：检查 GitHub Releases 最新版本 ───────── */

interface ReleaseInfo {
  ok: boolean; hasRelease?: boolean; currentVersion: string; tag?: string; latest?: string; name?: string;
  notes?: string; publishedAt?: string | null; apkUrl?: string | null; apkName?: string | null;
  apkSizeMB?: number | null; upToDate?: boolean; releasesPage?: string; error?: string;
}

function UpdateCard() {
  const [info, setInfo] = useState<ReleaseInfo | null>(null);
  const [checking, setChecking] = useState(false);

  const check = useCallback(async () => {
    setChecking(true);
    try { setInfo(await (await fetch("/api/releases")).json()); }
    catch { setInfo({ ok: false, currentVersion: APP_VERSION, error: "网络异常，请稍后重试" }); }
    finally { setChecking(false); }
  }, []);

  useEffect(() => { check(); }, [check]);

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between py-4">
        <CardTitle className="flex items-center gap-2 text-sm">
          <CloudDownload className="h-4 w-4 text-violet-600" /> 版本与更新 · GitHub Releases
        </CardTitle>
        <Button variant="ghost" size="sm" className="h-7 px-2 text-[11px]" onClick={check} disabled={checking}>
          <RefreshCw className={`mr-1 h-3 w-3 ${checking ? "animate-spin" : ""}`} /> {checking ? "检查中" : "检查更新"}
        </Button>
      </CardHeader>
      <CardContent className="space-y-2.5 px-4 pb-4">
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="rounded-md bg-zinc-100 px-2 py-1 font-medium text-zinc-700">当前 v{APP_VERSION}</span>
          {info?.ok && info.hasRelease && (
            <span className={`rounded-md px-2 py-1 font-medium ${info.upToDate ? "bg-emerald-50 text-emerald-600" : "bg-amber-50 text-amber-600"}`}>
              最新 {info.tag} {info.upToDate ? "· 已是最新" : "· 有新版本"}
            </span>
          )}
          {info?.ok && !info.hasRelease && <span className="rounded-md bg-zinc-100 px-2 py-1 text-zinc-500">云端暂无发布版本</span>}
          {info && !info.ok && <span className="text-xs text-rose-500">{info.error}</span>}
        </div>
        {info?.ok && info.hasRelease && !info.upToDate && (
          <div className="space-y-2 rounded-xl bg-amber-50/60 p-3 ring-1 ring-amber-100">
            <div className="text-xs font-semibold text-amber-700">发现新版本 {info.tag}（{info.publishedAt ? new Date(info.publishedAt).toLocaleDateString("zh-CN") : ""}）</div>
            {info.notes && (
              <pre className="max-h-28 overflow-y-auto whitespace-pre-wrap break-words font-sans text-[11px] leading-relaxed text-zinc-600">{info.notes}</pre>
            )}
            <div className="flex flex-wrap items-center gap-2">
              {info.apkUrl ? (
                <a href={info.apkUrl} className="inline-flex h-8 items-center gap-1.5 rounded-md bg-violet-600 px-3 text-xs font-medium text-white hover:bg-violet-700">
                  <CloudDownload className="h-3.5 w-3.5" /> 下载 APK{info.apkSizeMB ? `（${info.apkSizeMB} MB）` : ""}
                </a>
              ) : null}
              <a href={info.releasesPage ?? RELEASES_PAGE} target="_blank" rel="noreferrer" className="inline-flex h-8 items-center gap-1 rounded-md border border-zinc-200 bg-white px-3 text-xs font-medium text-zinc-700 hover:bg-zinc-50">
                发布页 <ExternalLink className="h-3 w-3" />
              </a>
            </div>
            <p className="text-[10px] text-amber-600/80">新版 APK 与当前版本同签名，下载后直接安装即可覆盖升级，无需卸载。</p>
          </div>
        )}
        {info?.ok && info.hasRelease && info.upToDate && (
          <p className="text-[11px] text-zinc-500">每次迭代都会发布到 GitHub Releases：手机端「开屏卫士」关于页同样支持一键检查更新与下载。</p>
        )}
      </CardContent>
    </Card>
  );
}

/* ───────── 智能学习报告（判断引擎画像） ───────── */
function LearningReport({ learn }: { learn: LearnPanel | null }) {
  const st = learn?.stats;
  const rules = learn?.rules;
  const recent = learn?.recent ?? [];
  const THEME_L: Record<string, string> = { ecommerce: "电商", game: "游戏", vip: "会员", finance: "理财" };
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between py-4">
        <CardTitle className="flex items-center gap-2 text-sm">
          <Brain className="h-4 w-4 text-violet-600" /> 智能学习报告 · 判断引擎画像
        </CardTitle>
        <span className="text-[11px] tabular-nums text-zinc-400">精准度 {st ? `${Math.round(st.precision * 100)}%` : "--"}</span>
      </CardHeader>
      <CardContent className="space-y-3.5 px-4 pb-4">
        <div className="grid grid-cols-4 gap-2">
          {[
            { label: "拦截", value: st?.blocked ?? 0, tone: "bg-rose-50 text-rose-600" },
            { label: "放行", value: st?.allowed ?? 0, tone: "bg-emerald-50 text-emerald-600" },
            { label: "误判纠正", value: st?.corrections ?? 0, tone: "bg-amber-50 text-amber-600" },
            { label: "跳过样本", value: rules?.samples ?? 0, tone: "bg-violet-50 text-violet-600" },
          ].map(({ label, value, tone }) => (
            <div key={label} className="rounded-xl bg-zinc-50 p-2.5 text-center ring-1 ring-zinc-100">
              <span className={`mx-auto flex h-7 w-7 items-center justify-center rounded-lg text-xs font-bold ${tone}`}>{value}</span>
              <div className="mt-1 text-[10px] text-zinc-500">{label}</div>
            </div>
          ))}
        </div>

        <div className="space-y-1.5">
          <div className="flex items-center gap-2 text-xs">
            <ShieldCheck className="h-3.5 w-3.5 text-violet-600" />
            <span className="font-semibold text-zinc-700">已学习规则（实时聚合 /api/learn）</span>
          </div>
          <div className="grid gap-1.5">
            <div className="flex items-center justify-between rounded-lg bg-zinc-50 px-3 py-2 text-xs">
              <span className="text-zinc-600">自动跳过时机（GKD 式）</span>
              <span className="font-bold tabular-nums text-zinc-800">
                {rules?.autoSkipDelayMs ? `${(rules.autoSkipDelayMs / 1000).toFixed(1)}s 后自动跳过` : `学习中（${rules?.samples ?? 0}/3 样本）`}
              </span>
            </div>
            <div className="flex items-center justify-between rounded-lg bg-zinc-50 px-3 py-2 text-xs">
              <span className="text-zinc-600">快速点击判定（&lt;40ms 视为误触）</span>
              <span className="font-bold text-zinc-800">{rules?.relaxQuickTap ? "已放宽（纠正≥2 次）" : "基准"}</span>
            </div>
            <div className="rounded-lg bg-zinc-50 px-3 py-2 text-xs">
              <div className="text-zinc-600">主题偏好（落地页停留反馈学习）</div>
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                {Object.entries(THEME_L).map(([t, label]) => {
                  const b = rules?.themeBias?.[t] ?? 0;
                  return (
                    <span
                      key={t}
                      className={`rounded-md px-2 py-0.5 text-[10px] font-medium ring-1 ${
                        b > 0
                          ? "bg-rose-50 text-rose-600 ring-rose-200"
                          : b < 0
                            ? "bg-emerald-50 text-emerald-600 ring-emerald-200"
                            : "bg-zinc-100 text-zinc-400 ring-zinc-200"
                      }`}
                    >
                      {label} {b > 0 ? `拦截倾向 +${b}` : b < 0 ? `放行倾向 ${b}` : "中性"}
                    </span>
                  );
                })}
              </div>
            </div>
          </div>
        </div>

        <div>
          <div className="mb-1.5 text-xs font-semibold text-zinc-700">最近判定日志</div>
          <div className="max-h-44 space-y-1 overflow-y-auto rounded-lg bg-zinc-950 p-2 font-mono text-[10px] leading-relaxed text-zinc-300">
            {recent.length === 0 && (
              <div className="text-zinc-500">暂无事件 · 在手机里开启「学习/拦截模式」后，判定 / 跳过 / 停留反馈会实时出现在这里</div>
            )}
            {recent.map((e) => (
              <div key={e.id} className="flex gap-1.5">
                <span className="shrink-0 text-zinc-500">{new Date(e.ts).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false })}</span>
                <span
                  className={`shrink-0 rounded px-1 ${
                    e.kind === "decision"
                      ? e.action === "blocked"
                        ? "bg-rose-500/25 text-rose-300"
                        : e.action === "would-block"
                          ? "bg-amber-500/25 text-amber-300"
                          : "bg-emerald-500/25 text-emerald-300"
                      : "bg-zinc-700/60 text-zinc-300"
                  }`}
                >
                  {KIND_LABEL[e.kind] ?? e.kind}
                </span>
                <span className="min-w-0 flex-1 truncate text-zinc-400">
                  {e.reason0 || (e.dwellMs != null ? `${(e.dwellMs / 1000).toFixed(1)}s` : "") || e.trigger || e.adName || ""}
                </span>
              </div>
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/* ───────── 新建广告对话框 ───────── */
function NewAdDialog({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [form, setForm] = useState({
    name: "", type: "splash", title: "", subtitle: "", ctaText: "查看详情", theme: "ecommerce", strategy: "standard", weight: 5,
  });
  const set = (k: string, v: string | number) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async () => {
    if (!form.name.trim() || !form.title.trim()) {
      setError("请填写广告计划名称与主标题");
      return;
    }
    setSaving(true);
    try {
      const linkUrl = form.type === "splash" ? "promo-1111" : "vip-promo";
      const res = await fetch("/api/ads", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, linkUrl }),
      });
      if (!res.ok) throw new Error();
      setOpen(false);
      setForm({ name: "", type: "splash", title: "", subtitle: "", ctaText: "查看详情", theme: "ecommerce", strategy: "standard", weight: 5 });
      setError("");
      onCreated();
    } catch {
      setError("创建失败，请重试");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" className="h-8 bg-violet-600 hover:bg-violet-700">
          <Plus className="h-3.5 w-3.5" /> 新建广告
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>新建广告计划</DialogTitle>
          <DialogDescription>新建后立即参与投放，在手机里即可看到效果</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-1.5">
              <Label htmlFor="f-name">计划名称 *</Label>
              <Input id="f-name" value={form.name} onChange={(e) => set("name", e.target.value)} placeholder="如：双11大促·开屏C" />
            </div>
            <div className="grid gap-1.5">
              <Label>广告位类型</Label>
              <Select value={form.type} onValueChange={(v) => set("type", v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="splash">开屏广告</SelectItem>
                  <SelectItem value="interstitial">插屏广告</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="f-title">主标题 *</Label>
            <Input id="f-title" value={form.title} onChange={(e) => set("title", e.target.value)} placeholder="如：年货节 狂欢开启" />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="f-sub">副标题</Label>
            <Input id="f-sub" value={form.subtitle} onChange={(e) => set("subtitle", e.target.value)} placeholder="如：全场满 300 减 60" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-1.5">
              <Label>素材主题</Label>
              <Select value={form.theme} onValueChange={(v) => set("theme", v)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {THEMES.map((t) => (
                    <SelectItem key={t} value={t}>{THEME_LABEL[t]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="f-weight">投放权重（1-10）</Label>
              <Input id="f-weight" type="number" min={1} max={10} value={form.weight} onChange={(e) => set("weight", Number(e.target.value))} />
            </div>
          </div>
          <div className="grid gap-1.5">
            <Label>跳转策略（2026，仅开屏生效）</Label>
            <Select value={form.strategy} onValueChange={(v) => set("strategy", v)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {STRATEGIES.map((s) => (
                  <SelectItem key={s} value={s}>{STRATEGY_META[s].label} · {STRATEGY_META[s].desc}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-[11px] text-zinc-400">标准 / 倒计时归零自动跳转 / 摇一摇传感器 / 全屏隐藏热区</p>
          </div>
          {error && <p className="text-xs text-rose-500">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>取消</Button>
          <Button onClick={submit} disabled={saving} className="bg-violet-600 hover:bg-violet-700">
            {saving ? "创建中..." : "创建并投放"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
