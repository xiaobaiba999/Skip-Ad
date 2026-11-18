"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { APP_VERSION } from "@/lib/version";
import {
  BrainCircuit, Clock, RefreshCw, CloudUpload, CheckCircle2, XCircle, Github,
  Smartphone, ShieldCheck, Zap, Timer, TrendingUp, ExternalLink, Sparkles, ScrollText,
} from "lucide-react";

interface LearningData {
  profile: {
    totals: { blocks: number; misses: number; guardBlocks: number; avgLatencyMs: number; successRate: number };
    perApp: { appName: string; category: string; count: number; avgMs: number }[];
    hours: number[];
    scenarios: Record<string, number>;
    learnedRuleCount: number;
    recent: { id: string; ts: number; appName: string; action: string; adType: string; latencyMs: number; success: boolean; scenario: string }[];
  };
  learnedRules: {
    id: string; appName: string; appCategory: string; adType: string; action: string;
    pattern: string; confidence: number; hitCount: number; enabled: boolean; lastHitAt: number | null;
  }[];
  sync: {
    schedule: string;
    last: { kind: string; ok: boolean; message: string; url?: string; ruleCount: number; blockSum: number; at: number } | null;
  };
}

const SCENARIO_LABEL: Record<string, string> = {
  normal: "常规秒跳",
  shake: "摇一摇拦截",
  deeplink: "跳转拦截",
  guard: "守护模式",
};

export function LearningCenter() {
  const [data, setData] = useState<LearningData | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [syncMsg, setSyncMsg] = useState<string | null>(null);
  const [logUploading, setLogUploading] = useState(false);
  const [logMsg, setLogMsg] = useState<{ ok: boolean; text: string; url?: string } | null>(null);
  /* v5.3.0 弹屏提醒：日志上传成功后全屏弹窗确认（不再只是角落文字变化） */
  const [logDone, setLogDone] = useState<{ number: number; url: string; blocks: number; rules: number; events: number } | null>(null);

  const load = useCallback(async () => {
    try {
      const r = await fetch("/api/learning");
      if (r.ok) setData(await r.json());
    } catch { /* 静默 */ }
    setLoading(false);
  }, []);

  useEffect(() => {
    let alive = true;
    fetch("/api/learning")
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!alive) return;
        if (d) setData(d);
        setLoading(false);
      })
      .catch(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []);

  const doSync = useCallback(async () => {
    setSyncing(true);
    setSyncMsg(null);
    try {
      const r = await fetch("/api/learning/sync", { method: "POST" });
      const d = await r.json();
      setSyncMsg(d.ok ? `✓ ${d.message}` : `✗ ${d.message ?? "同步失败"}`);
      await load();
    } catch {
      setSyncMsg("✗ 网络异常，请稍后重试");
    }
    setSyncing(false);
  }, [load]);

  /* ── v3.5.0 引擎日志一键上传（内置加密通道，供下版迭代参考） ── */
  const uploadEngineLog = useCallback(async () => {
    if (!data) return;
    setLogUploading(true);
    setLogMsg(null);
    const { profile, learnedRules } = data;
    const eventLines = profile.recent.length
      ? profile.recent
          .slice(0, 40)
          .map((e) => `- ${new Date(e.ts).toLocaleString("zh-CN", { hour12: false })} · ${e.appName} · ${e.action} · ${e.adType} · ${e.success ? `${e.latencyMs}ms` : "手动跳过"}`)
          .join("\n")
      : "- （暂无事件）";
    const ruleLines = learnedRules.length
      ? learnedRules.map((r) => `- ${r.appName}（${r.appCategory}）：${r.pattern} · 命中 ${r.hitCount} 次 · 置信度 ${Math.round(r.confidence * 100)}%${r.enabled ? "" : " · 已停用"}`).join("\n")
      : "- （无学习规则）";
    const context = [
      `### 版本：v${APP_VERSION}（李跳跳式三零治理 · 宿主窗档案 · 探针前置 · 位置学习验证）· 上传时间：${new Date().toLocaleString("zh-CN", { hour12: false })}`,
      "",
      "### 拦截战绩（累计）",
      `- 拦截 ${profile.totals.blocks} 次 · 漏拦截 ${profile.totals.misses} 次 · 成功率 ${Math.round(profile.totals.successRate * 100)}% · 平均耗时 ${profile.totals.avgLatencyMs}ms · 守护拦截 ${profile.totals.guardBlocks} 次 · 已覆盖 ${profile.perApp.length} 款应用`,
      "",
      "### 学习规则（" + learnedRules.length + " 条）",
      ruleLines,
      "",
      "### 学习事件流（最近 40 条）",
      eventLines,
    ].join("\n");
    try {
      const r = await fetch("/api/feedback", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: "engine_log",
          desc: "学习中心手动上传引擎日志（拦截战绩 + 学习规则 + 事件流）",
          context,
        }),
      });
      const d = await r.json();
      if (d.ok) {
        setLogMsg({ ok: true, text: `已上传 Issue #${d.number}，将直接成为下一版迭代的修复依据`, url: d.url });
        // v5.3.0 弹屏提醒：上传成功 → 全屏弹窗确认（带上传内容清单）
        setLogDone({
          number: d.number,
          url: d.url,
          blocks: profile.totals.blocks,
          rules: learnedRules.length,
          events: Math.min(profile.recent.length, 40),
        });
      } else {
        setLogMsg({ ok: false, text: d.error ?? "上传失败" });
      }
    } catch {
      setLogMsg({ ok: false, text: "网络异常，请稍后重试" });
    }
    setLogUploading(false);
  }, [data]);

  if (loading || !data) {
    return (
      <div className="flex items-center justify-center py-20 text-sm text-zinc-400">
        <RefreshCw className="mr-2 h-4 w-4 animate-spin" /> 学习数据加载中…
      </div>
    );
  }

  const { profile, learnedRules, sync } = data;
  const maxHour = Math.max(...profile.hours, 1);

  return (
    <div className="flex flex-col gap-5">
      {/* 概览 */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          { icon: Zap, label: "累计拦截", value: `${profile.totals.blocks}`, sub: "次", tone: "text-emerald-600" },
          { icon: Timer, label: "平均耗时", value: profile.totals.avgLatencyMs ? `${profile.totals.avgLatencyMs}` : "—", sub: "ms", tone: "text-violet-600" },
          { icon: TrendingUp, label: "拦截成功率", value: `${Math.round(profile.totals.successRate * 100)}`, sub: "%", tone: "text-sky-600" },
          { icon: ShieldCheck, label: "守护拦截", value: `${profile.totals.guardBlocks}`, sub: "银行/政务/支付", tone: "text-teal-600" },
        ].map(({ icon: Icon, label, value, sub, tone }) => (
          <div key={label} className="rounded-2xl border border-zinc-200 bg-white p-4">
            <div className="flex items-center gap-1.5 text-[11px] font-semibold text-zinc-400">
              <Icon className="h-3.5 w-3.5" /> {label}
            </div>
            <div className="mt-1.5 flex items-baseline gap-1">
              <span className={cn("text-2xl font-black tabular-nums", tone)}>{value}</span>
              <span className="text-[11px] font-semibold text-zinc-400">{sub}</span>
            </div>
          </div>
        ))}
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        {/* 习惯画像 */}
        <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <BrainCircuit className="h-5 w-5 text-fuchsia-600" /> 使用习惯画像
          </h2>
          <p className="mt-1 text-[11px] text-zinc-400">根据你的应用使用、场景与时段动态沉淀，驱动秒跳策略个性化</p>

          <div className="mt-4">
            {/* v3.13.0：分应用统计明细整体移除（用户反馈看不懂还乱）——仅留一行极简汇总 */}
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold text-zinc-500">应用覆盖</span>
              <span className="text-[11px] tabular-nums text-zinc-400">{profile.perApp.length ? `${profile.perApp.length} 款应用 · 共拦截 ${profile.perApp.reduce((s, a) => s + a.count, 0)} 次` : "先去演练场点几个应用试试"}</span>
            </div>
          </div>

          <div className="mt-5">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold text-zinc-500">使用时段（近 2000 条 · 东八区）</span>
              <span className="text-[10px] text-zinc-400">共 {profile.hours.reduce((s, n) => s + n, 0)} 条</span>
            </div>
            <div className="mt-2 flex h-16 items-end gap-[2px]">
              {profile.hours.map((n, h) => (
                <div key={h} className="group relative flex-1" title={`${h}:00 · ${n} 次`}>
                  <div
                    className={cn("w-full rounded-t-sm transition-all", n > 0 ? "bg-gradient-to-t from-violet-400 to-fuchsia-400" : "bg-zinc-100")}
                    style={{ height: `${Math.max(4, (n / maxHour) * 64)}px` }}
                  />
                </div>
              ))}
            </div>
            <div className="mt-1 flex justify-between text-[9px] text-zinc-300">
              <span>0点</span><span>6点</span><span>12点</span><span>18点</span><span>23点</span>
            </div>
          </div>

          <div className="mt-5">
            <div className="text-[11px] font-bold text-zinc-500">场景分布</div>
            <div className="mt-2 flex flex-wrap gap-2">
              {Object.entries(profile.scenarios).length === 0 && (
                <span className="text-[11px] text-zinc-400">暂无拦截记录</span>
              )}
              {Object.entries(profile.scenarios).map(([k, v]) => (
                <span key={k} className={cn("rounded-full px-2.5 py-1 text-[10px] font-bold ring-1",
                  k === "guard" ? "bg-emerald-50 text-emerald-700 ring-emerald-200"
                    : k === "shake" ? "bg-orange-50 text-orange-700 ring-orange-200"
                      : k === "deeplink" ? "bg-rose-50 text-rose-700 ring-rose-200"
                        : "bg-violet-50 text-violet-700 ring-violet-200")}>
                  {SCENARIO_LABEL[k] ?? k} · {v} 次
                </span>
              ))}
            </div>
          </div>
        </section>

        {/* 学习规则 + 夜间同步 */}
        <div className="flex flex-col gap-5">
          <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
            <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
              <Sparkles className="h-5 w-5 text-fuchsia-600" /> 学习规则
              <span className="rounded-full bg-fuchsia-50 px-2 py-0.5 text-[10px] font-bold text-fuchsia-700">{learnedRules.length} 条</span>
            </h2>
            <p className="mt-1 text-[11px] text-zinc-400">首次拦截即沉淀广告指纹（容器签名 + viewId + 时机），再次启动 P0 预拦截，命中越多置信度越高</p>
            {learnedRules.length === 0 ? (
              <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-6 text-center text-[11px] leading-relaxed text-zinc-400">
                还没有学习规则。到演练场启动任意应用：首次拦截即自动沉淀广告指纹，第二次启动即预拦截（广告未及呈现）。
              </p>
            ) : (
              <ul className="mt-3 space-y-2.5">
                {learnedRules.map((r) => (
                  <li key={r.id} className="rounded-xl border border-fuchsia-100 bg-fuchsia-50/40 p-3">
                    <div className="flex items-center gap-2 text-xs font-bold text-zinc-800">
                      <Smartphone className="h-3.5 w-3.5 text-fuchsia-500" /> {r.appName}
                      <span className="rounded bg-fuchsia-100 px-1.5 py-0.5 text-[9px] font-bold text-fuchsia-700">{r.appCategory}</span>
                      <span className="ml-auto text-[10px] font-semibold tabular-nums text-zinc-500">命中 {r.hitCount} 次</span>
                    </div>
                    <p className="mt-1.5 text-[11px] leading-relaxed text-zinc-500">{r.pattern}</p>
                    <div className="mt-2 flex items-center gap-2">
                      <Progress value={r.confidence * 100} className="h-1.5 flex-1" />
                      <span className="text-[10px] font-bold tabular-nums text-fuchsia-600">{Math.round(r.confidence * 100)}%</span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* 夜间同步 */}
          <section className="rounded-2xl border border-zinc-800 bg-zinc-900 p-4 text-white lg:p-5">
            <h2 className="flex items-center gap-2 text-base font-bold">
              <CloudUpload className="h-5 w-5 text-emerald-400" /> 学习数据夜间同步
            </h2>
            <p className="mt-1 text-[11px] leading-relaxed text-zinc-400">
              每晚 <span className="font-bold text-emerald-400">02:00（北京时间）</span>自动把学习规则与使用画像提交到
              GitHub 仓库 <span className="font-mono text-zinc-200">data/learning/</span> 目录（latest.json + 按日归档），供后续版本迭代参考。
            </p>

            {sync.last && (
              <div className={cn("mt-3 rounded-xl p-3 text-[11px] leading-relaxed", sync.last.ok ? "bg-emerald-500/10 text-emerald-300 ring-1 ring-emerald-400/20" : "bg-rose-500/10 text-rose-300 ring-1 ring-rose-400/20")}>
                <div className="flex items-center gap-1.5 font-bold">
                  {sync.last.ok ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
                  上次同步（{sync.last.kind === "nightly" ? "夜间自动" : "手动"}）· {new Date(sync.last.at).toLocaleString("zh-CN", { hour12: false })}
                </div>
                <div className="mt-1 text-zinc-400">{sync.last.message}</div>
                {sync.last.url && (
                  <a href={sync.last.url} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 font-bold text-emerald-400 hover:underline">
                    查看提交 <ExternalLink className="h-3 w-3" />
                  </a>
                )}
              </div>
            )}

            {syncMsg && (
              <div className="mt-3 rounded-xl bg-white/5 p-3 text-[11px] leading-relaxed text-zinc-200 ring-1 ring-white/10">{syncMsg}</div>
            )}

            <div className="mt-4 border-t border-white/10 pt-4">
              <div className="flex flex-wrap items-center gap-2.5">
                <Button
                  size="sm"
                  data-testid="btn-upload-engine-log"
                  variant="outline"
                  className="h-9 rounded-lg border-emerald-400/40 bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20 hover:text-emerald-200"
                  onClick={() => void uploadEngineLog()}
                  disabled={logUploading || !data || profile.totals.blocks === 0}
                >
                  {logUploading ? <RefreshCw className="mr-1 h-3.5 w-3.5 animate-spin" /> : <ScrollText className="mr-1 h-3.5 w-3.5" />}
                  {logUploading ? "上传中…" : "上传引擎日志到 GitHub"}
                </Button>
                <span className="text-[10px] leading-relaxed text-zinc-500">拦截战绩 + 学习规则 + 事件流一键直达 Issues，上传成功即弹屏提醒确认</span>
              </div>
              {logMsg && (
                <div className={cn("mt-3 rounded-xl p-3 text-[11px] leading-relaxed ring-1",
                  logMsg.ok ? "bg-emerald-500/10 text-emerald-300 ring-emerald-400/20" : "bg-rose-500/10 text-rose-300 ring-rose-400/20")}>
                  <div className="flex items-center gap-1.5 font-bold">
                    {logMsg.ok ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
                    {logMsg.text}
                    {logMsg.url && (
                      <a href={logMsg.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-0.5 text-emerald-400 hover:underline">
                        查看 <ExternalLink className="h-3 w-3" />
                      </a>
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className="mt-4 flex items-center gap-2.5">
              <Button size="sm" data-testid="btn-sync-now" className="h-9 rounded-lg bg-emerald-500 hover:bg-emerald-400" onClick={() => void doSync()} disabled={syncing}>
                {syncing ? <RefreshCw className="mr-1 h-3.5 w-3.5 animate-spin" /> : <CloudUpload className="mr-1 h-3.5 w-3.5" />}
                {syncing ? "同步中…" : "立即同步"}
              </Button>
              <a
                href="https://github.com/xiaobaiba999/Skip-Ad/tree/main/data/learning"
                target="_blank" rel="noreferrer"
                className="flex items-center gap-1 text-[11px] font-semibold text-zinc-400 hover:text-white"
              >
                <Github className="h-3.5 w-3.5" /> data/learning
              </a>
              <span className="ml-auto flex items-center gap-1 text-[10px] text-zinc-500">
                <Clock className="h-3 w-3" /> 每日 02:00 自动执行
              </span>
            </div>
          </section>
        </div>
      </div>

      {/* 学习事件流 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <h2 className="text-base font-bold text-zinc-900">学习事件流</h2>
        {profile.recent.length === 0 ? (
          <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-6 text-center text-xs text-zinc-400">暂无事件</p>
        ) : (
          <ul className="mt-3 max-h-64 space-y-1.5 overflow-y-auto pr-1">
            {profile.recent.map((e) => (
              <li key={e.id} className="flex items-center gap-2.5 rounded-lg bg-zinc-50 px-3 py-2 text-[11px]">
                <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full", e.success ? "bg-emerald-500" : "bg-rose-500")} />
                <span className="w-12 shrink-0 tabular-nums text-zinc-400">
                  {new Date(e.ts).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false })}
                </span>
                <span className="font-bold text-zinc-800">{e.appName}</span>
                <span className="text-zinc-500">{SCENARIO_LABEL[e.scenario] ?? e.scenario}</span>
                <span className="ml-auto shrink-0 font-bold tabular-nums text-zinc-600">
                  {e.success ? `${e.latencyMs}ms` : "手动跳过"}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* ═══ v5.3.0 弹屏提醒：引擎日志上传成功全屏确认 ═══ */}
      <Dialog open={!!logDone} onOpenChange={(v) => { if (!v) setLogDone(null); }}>
        <DialogContent className="max-w-sm rounded-2xl" data-testid="log-upload-dialog">
          <div className="flex flex-col items-center gap-1 py-2 text-center">
            <span className="relative flex h-20 w-20 items-center justify-center" aria-hidden>
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-25" />
              <span className="relative flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-emerald-500 to-teal-600 shadow-lg shadow-emerald-500/30">
                <CloudUpload className="h-9 w-9 text-white" />
              </span>
            </span>
            <DialogHeader className="mt-2 space-y-1">
              <DialogTitle className="text-center text-lg">引擎日志已成功上传</DialogTitle>
              <DialogDescription className="text-center text-[11px] leading-relaxed">
                已通过内置加密通道直达 GitHub Issues，开发者可直接查看并据此迭代
              </DialogDescription>
            </DialogHeader>

            <div className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-zinc-50 px-3 py-2.5 ring-1 ring-zinc-200">
              <Github className="h-4 w-4 shrink-0 text-zinc-700" />
              <span className="text-sm font-black text-zinc-900">Issue #{logDone?.number}</span>
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-700">已创建</span>
            </div>

            <div className="mt-2.5 grid w-full grid-cols-3 gap-2">
              {[
                { label: "拦截战绩", value: `${logDone?.blocks ?? 0} 次` },
                { label: "学习规则", value: `${logDone?.rules ?? 0} 条` },
                { label: "事件流", value: `${logDone?.events ?? 0} 条` },
              ].map((s) => (
                <div key={s.label} className="rounded-xl bg-zinc-50 px-2 py-2 ring-1 ring-zinc-200">
                  <div className="text-[10px] font-semibold text-zinc-400">{s.label}</div>
                  <div className="mt-0.5 text-[13px] font-black tabular-nums text-zinc-800">{s.value}</div>
                </div>
              ))}
            </div>

            <p className="mt-3 flex items-center gap-1.5 text-[11px] leading-relaxed text-zinc-500">
              <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-emerald-600" />
              令牌仅存服务端，全程不出现在你的浏览器
            </p>
          </div>
          <DialogFooter className="flex-row gap-2 sm:justify-center">
            <Button
              size="sm"
              variant="outline"
              className="h-9 flex-1 rounded-lg"
              onClick={() => setLogDone(null)}
            >
              完成
            </Button>
            <Button
              size="sm"
              className="h-9 flex-1 rounded-lg bg-emerald-600 hover:bg-emerald-500"
              onClick={() => logDone?.url && window.open(logDone.url, "_blank", "noopener,noreferrer")}
            >
              <ExternalLink className="mr-1 h-3.5 w-3.5" /> 查看 Issue
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
