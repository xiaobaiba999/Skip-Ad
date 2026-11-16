"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AdCreative } from "@/components/ad-creative";
import { SCENARIO_APPS, type ScenarioApp } from "@/lib/scenario-apps";
import { planInstantBlock, AD_TYPE_LABEL, type BlockPlan } from "@/lib/instant-engine";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  ShoppingBag, ShoppingBasket, Rocket, Landmark, Building2, Wallet, Play, Phone, MessageCircle,
  Globe, Camera, Signal, Wifi, BatteryFull, Zap, ShieldCheck, Headphones,
  Link2Off, ChevronLeft, Search, ScanLine, ArrowLeftRight, Receipt, TrendingUp,
  PiggyBank, UserRound, FileText, CalendarClock, ThumbsUp, Share2,
  Flame, Tv, Music2, Activity, Gauge, Timer, Vibrate, Layers, X, Smartphone, Apple,
} from "lucide-react";

type Stage = "home" | "launch" | "ad" | "flash" | "main";

const APP_ICONS: Record<string, React.ElementType> = {
  "shopping-bag": ShoppingBag,
  "shopping-basket": ShoppingBasket,
  rocket: Rocket,
  landmark: Landmark,
  "building-2": Building2,
  wallet: Wallet,
  play: Play,
  headphones: Headphones,
};

/** 喜马拉雅类广告的甄别阶段（演示节奏，已放慢） */
type FakePhase = "scanning" | "ignored" | "chain";

interface SessionLogItem {
  id: number;
  app: string;
  kind: "block" | "preempt";
  text: string;
  ms: number;
}

export function Arena({ onFeedback }: { onFeedback: () => void }) {
  const [app, setApp] = useState<ScenarioApp | null>(null);
  const [stage, setStage] = useState<Stage>("home");
  const [plan, setPlan] = useState<BlockPlan | null>(null);
  const [fakePhase, setFakePhase] = useState<FakePhase | null>(null);
  const [learnedApps, setLearnedApps] = useState<Set<string>>(new Set());
  const [blocks, setBlocks] = useState(0);
  const [preempts, setPreempts] = useState(0);
  const [msSum, setMsSum] = useState(0);
  const [log, setLog] = useState<SessionLogItem[]>([]);
  const [toast, setToast] = useState<{ id: number; text: string; sub?: string } | null>(null);
  const logIdRef = useRef(0);
  const toastIdRef = useRef(0);

  const pushToast = useCallback((text: string, sub?: string) => {
    const id = ++toastIdRef.current;
    setToast({ id, text, sub });
    setTimeout(() => setToast((t) => (t?.id === id ? null : t)), 3600);
  }, []);

  const pushLog = useCallback((item: Omit<SessionLogItem, "id">) => {
    setLog((cur) => [{ ...item, id: ++logIdRef.current }, ...cur].slice(0, 12));
  }, []);

  /* 学习指纹（命中即预拦截资格） */
  const refreshLearning = useCallback(async () => {
    try {
      const r = await fetch("/api/learning");
      if (!r.ok) return;
      const d = await r.json();
      setLearnedApps(new Set<string>((d.learnedRules ?? []).map((x: { appName: string }) => x.appName)));
    } catch { /* 静默 */ }
  }, []);
  useEffect(() => {
    let alive = true;
    fetch("/api/learning")
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!alive || !d) return;
        setLearnedApps(new Set<string>((d.learnedRules ?? []).map((x: { appName: string }) => x.appName)));
      })
      .catch(() => {});
    return () => { alive = false; };
  }, []);

  /* ── 埋点 ── */
  const postEvent = useCallback(
    async (p: BlockPlan, note: string) => {
      if (!app) return;
      try {
        const r = await fetch("/api/block-events", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            appKey: app.key,
            kind: "block",
            action: p.action,
            latencyMs: p.totalMs,
            confidence: p.confidence / 100,
            scenario: p.scenario,
            adType: app.adType,
            note,
          }),
        });
        if (r.ok) {
          const d = await r.json();
          if (d.learnedCreated) {
            pushToast("广告指纹已学习", `${app.name} 下次启动将 P0 预拦截——广告未及呈现`);
            void refreshLearning();
          }
        }
      } catch { /* 静默 */ }
    },
    [app, pushToast, refreshLearning],
  );

  /* ── 启动应用 ── */
  const launchApp = useCallback((a: ScenarioApp) => {
    setApp(a);
    setPlan(null);
    setFakePhase(null);
    setStage("launch");
  }, []);

  /* ── 阶段流转 ── */
  useEffect(() => {
    if (stage === "launch" && app) {
      const isPreempt = learnedApps.has(app.name);
      const t = setTimeout(() => {
        const p = planInstantBlock(
          {
            name: app.name,
            category: app.category,
            adType: app.adType,
            guard: app.guard,
            ruleName: app.ruleName,
            learnedRuleName: learnedApps.has(app.name) ? `${app.name} 学习指纹` : null,
            traits: app.traits ?? null,
          },
          { preempt: isPreempt },
        );
        setPlan(p);
        if (isPreempt) {
          // P0 预拦截：广告未及呈现，直接进入拦截成功层
          setPreempts((n) => n + 1);
          setBlocks((n) => n + 1);
          setMsSum((s) => s + p.totalMs);
          pushLog({ app: app.name, kind: "preempt", text: `P0 预拦截 · ${AD_TYPE_LABEL[app.adType]} · 广告未及呈现`, ms: p.totalMs });
          void postEvent(p, "P0 预拦截（学习指纹）");
          setStage("flash");
        } else {
          setStage("ad");
        }
      }, 850);
      return () => clearTimeout(t);
    }
    if (stage === "flash") {
      const t = setTimeout(() => setStage("main"), 1250);
      return () => clearTimeout(t);
    }
  }, [stage, app, learnedApps, postEvent, pushLog]);

  /* ── 引擎拦截（广告展示中） ── */
  useEffect(() => {
    if (stage !== "ad" || !app || !plan) return;
    const isAdversarial = !!app.traits?.fakeSkip && (!!app.traits?.chainJump || !!app.traits?.gyroFlip);
    const isPopup = app.adType === "popup";
    const timers: ReturnType<typeof setTimeout>[] = [];
    if (isPopup) {
      // 应用内弹窗广告 v2：Dialog 窗口事件 → 角标/营销话术双信号 → 代点关闭
      timers.push(setTimeout(() => setFakePhase("scanning"), 400));
      timers.push(setTimeout(() => setFakePhase("ignored"), 1000));
      timers.push(
        setTimeout(() => {
          setBlocks((n) => n + 1);
          setMsSum((s) => s + plan.totalMs);
          pushLog({ app: app.name, kind: "block", text: `${plan.ruleName} · 弹窗广告已关闭`, ms: plan.totalMs });
          void postEvent(plan, "弹窗 v2 双信号关闭");
          setStage("flash");
        }, 1700),
      );
    } else if (isAdversarial) {
      // 喜马拉雅类：SDK 广告窗 className 识别 → 极速链复核无真跳过键 → 500ms 直断 + 回弹
      timers.push(setTimeout(() => setFakePhase("scanning"), 400));
      timers.push(setTimeout(() => setFakePhase("ignored"), 1100));
      timers.push(setTimeout(() => setFakePhase("chain"), 1900));
      timers.push(
        setTimeout(() => {
          setBlocks((n) => n + 1);
          setMsSum((s) => s + plan.totalMs);
          pushLog({ app: app.name, kind: "block", text: `${plan.ruleName} · ${app.traits?.sdkWindow ? "SDK 广告窗指纹直断 + 回弹" : "陀螺仪诱导直断 + 回弹"}`, ms: plan.totalMs });
          void postEvent(plan, app.traits?.sdkWindow ? "SDK 广告窗指纹直断 + 链式回弹" : "假跳过甄别 + 链式直断");
          setStage("flash");
        }, 2700),
      );
    } else {
      timers.push(
        setTimeout(() => {
          setBlocks((n) => n + 1);
          setMsSum((s) => s + plan.totalMs);
          pushLog({ app: app.name, kind: "block", text: `${plan.ruleName} · ${AD_TYPE_LABEL[app.adType]}`, ms: plan.totalMs });
          void postEvent(plan, plan.ruleName);
          setStage("flash");
        }, 700),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [stage, app, plan, postEvent, pushLog]);

  /* ── 假跳过陷阱教学点按 ── */
  const tapFakeSkip = useCallback(() => {
    pushToast(
      "假跳过陷阱 · 引擎已忽略",
      "这是广告图内嵌的热区：点击即跳落地页并二跳商店。多信号甄别（面积/容器归属/时序）判定为陷阱",
    );
  }, [pushToast]);

  const backHome = useCallback(() => {
    setApp(null);
    setStage("home");
    setPlan(null);
    setFakePhase(null);
  }, []);

  const avgMs = blocks > 0 ? Math.round(msSum / blocks) : 0;

  return (
    <div className="flex flex-col gap-5 lg:flex-row lg:items-start">
      {/* ═══ 左：手机模拟器 ═══ */}
      <section className="flex w-full shrink-0 flex-col items-center gap-3 lg:w-[420px]" aria-label="演练场手机模拟器">
        <div className="relative w-full max-w-[360px] rounded-[2.6rem] border-[10px] border-zinc-800 bg-zinc-800 shadow-2xl shadow-zinc-900/25 ring-1 ring-zinc-700">
          <div className="overflow-hidden rounded-[2rem] bg-white">
            <div className="relative aspect-[9/19] w-full select-none">
              <PhoneScreen
                stage={stage}
                app={app}
                plan={plan}
                fakePhase={fakePhase}
                onLaunch={launchApp}
                onBack={backHome}
                onTapFakeSkip={tapFakeSkip}
              />

              {/* 拦截 Toast */}
              {toast && (
                <div className="pointer-events-none absolute inset-x-3 top-11 z-[70] animate-[toast-in_.25s_ease-out] rounded-xl bg-zinc-900/95 p-2.5 text-[11px] leading-snug text-white shadow-lg ring-1 ring-white/15">
                  <div className="flex items-center gap-1.5 font-bold">
                    <Zap className="h-3.5 w-3.5 shrink-0 text-amber-300" />
                    <span>{toast.text}</span>
                  </div>
                  {toast.sub && <div className="mt-0.5 opacity-80">{toast.sub}</div>}
                </div>
              )}
            </div>
          </div>
        </div>
        <p className="max-w-[360px] text-center text-[10px] leading-relaxed text-zinc-400">
          同一应用第二次启动将触发 <span className="font-bold text-violet-600">P0 预拦截</span>：窗口附加阶段直接阻断，广告未及呈现
        </p>
      </section>

      {/* ═══ 右：引擎面板 ═══ */}
      <section className="flex min-w-0 flex-1 flex-col gap-4" aria-label="秒跳引擎面板">
        {/* 引擎状态 */}
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-4">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 text-white">
              <ShieldCheck className="h-4.5 w-4.5" />
            </span>
            <div className="min-w-0">
              <div className="flex items-center gap-2 text-sm font-bold text-zinc-900">
                秒跳引擎 · 运行中
                <span className="rounded-full bg-emerald-600 px-2 py-0.5 text-[10px] font-bold text-white">十层纵深</span>
              </div>
              <p className="mt-0.5 text-[11px] text-zinc-500">
                P0 预拦截 → 学习位置点击 → 角落键多候选+坐标轮换 → 点击复验换候选重试（原标签坐标） → SDK 窗指纹 → 热区/立即直断 → 链式回弹 → 弹窗 v2 → 返回键兜底（仅应用内窗） → 根窗口安全门（入口窗绝不返回键，防退出应用）；守护应用只拦广告、不碰服务
              </p>
            </div>
            <div className="ml-auto hidden shrink-0 items-center gap-1 text-[10px] font-bold text-emerald-700 sm:flex">
              <Layers className="h-3.5 w-3.5" /> 1060 款语料 100% · 二十三轮
            </div>
          </div>
        </div>

        {/* 统计 chips */}
        <div className="grid grid-cols-3 gap-2.5">
          {[
            { icon: Zap, label: "本次会话拦截", value: `${blocks} 次`, tone: "text-emerald-600" },
            { icon: Gauge, label: "平均拦截耗时", value: blocks > 0 ? `${avgMs} ms` : "—", tone: "text-violet-600" },
            { icon: Timer, label: "预拦截（0ms 可见）", value: `${preempts} 次`, tone: "text-fuchsia-600" },
          ].map(({ icon: Icon, label, value, tone }) => (
            <div key={label} className="rounded-xl border border-zinc-200 bg-white p-3">
              <div className="flex items-center gap-1 text-[10px] font-semibold text-zinc-400">
                <Icon className="h-3 w-3" /> {label}
              </div>
              <div className={cn("mt-1 text-lg font-black tabular-nums", tone)}>{value}</div>
            </div>
          ))}
        </div>

        {/* 判定流水线 */}
        <div className="rounded-2xl border border-zinc-200 bg-white p-4">
          <div className="flex items-center justify-between">
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
              <Activity className="h-4 w-4 text-violet-600" /> 毫秒级判定流水线
            </h3>
            {plan && (
              <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-bold ring-1",
                plan.preempt ? "bg-fuchsia-50 text-fuchsia-700 ring-fuchsia-200" : "bg-violet-50 text-violet-700 ring-violet-200")}>
                {plan.preempt ? "P0 预拦截" : plan.ruleName}
              </span>
            )}
          </div>
          {plan ? (
            <>
              <div className="mt-3 flex items-baseline gap-2">
                <span className="text-2xl font-black tabular-nums text-zinc-900">{plan.totalMs}<span className="text-sm">ms</span></span>
                <span className="text-[11px] font-semibold text-zinc-500">端到端耗时</span>
                <span className="ml-auto text-[11px] font-bold text-emerald-600">置信度 {plan.confidence}%</span>
              </div>
              {plan.waitMs > 0 && (
                <p className="mt-1 text-[10px] leading-relaxed text-amber-600">
                  含等待广告方 { (plan.waitMs / 1000).toFixed(1) }s 显示真跳过（引擎出手仅 {plan.engineMs}ms）——链式二跳广告已升级为直接阻断，无需等待
                </p>
              )}
              <ol className="mt-3 space-y-1.5">
                {plan.steps.map((s, i) => (
                  <li key={i} className="flex items-start gap-2 rounded-lg bg-zinc-50 px-2.5 py-1.5">
                    <span className="mt-px w-11 shrink-0 text-right text-[10px] font-bold tabular-nums text-violet-500">{s.atMs}ms</span>
                    <span className="min-w-0">
                      <span className="text-[11px] font-bold text-zinc-800">{s.label}</span>
                      <span className="ml-1.5 text-[10px] leading-relaxed text-zinc-500">{s.detail}</span>
                    </span>
                  </li>
                ))}
              </ol>
              <p className="mt-2 text-[10px] leading-relaxed text-zinc-400">※ 演示动画已放慢节奏以便观察，引擎真实耗时以上方流水线为准（真机由无障碍服务毫秒级执行）。</p>
            </>
          ) : (
            <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-6 text-center text-xs text-zinc-400">
              点按左侧任意应用图标启动，观察秒跳引擎的拦截过程；再次启动同一应用可体验预拦截
            </p>
          )}
        </div>

        {/* 场景速览 + 会话日志 */}
        <div className="grid gap-4 md:grid-cols-2">
          <div className="rounded-2xl border border-zinc-200 bg-white p-4">
            <h3 className="text-sm font-bold text-zinc-900">八大场景</h3>
            <ul className="mt-2.5 space-y-2">
              {SCENARIO_APPS.map((a) => {
                const Icon = APP_ICONS[a.icon];
                return (
                  <li key={a.key} className="flex items-center gap-2.5">
                    <span className={cn("flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white", a.gradient)}>
                      <Icon className="h-3.5 w-3.5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5 text-xs font-bold text-zinc-800">
                        {a.name}
                        <span className="rounded bg-zinc-100 px-1 text-[9px] font-semibold text-zinc-500">{a.category}</span>
                        {a.guard && <span className="rounded bg-emerald-100 px-1 text-[9px] font-bold text-emerald-700">守护</span>}
                        {a.traits?.fakeSkip && <span className="rounded bg-rose-100 px-1 text-[9px] font-bold text-rose-600">专案</span>}
                        {learnedApps.has(a.name) && <span className="rounded bg-fuchsia-100 px-1 text-[9px] font-bold text-fuchsia-700">指纹</span>}
                      </div>
                      <div className="truncate text-[10px] text-zinc-400">{a.note} · {AD_TYPE_LABEL[a.adType]}</div>
                    </div>
                  </li>
                );
              })}
            </ul>
            <Button size="sm" variant="outline" data-testid="btn-arena-feedback" className="mt-3 h-7 w-full rounded-lg text-[11px]" onClick={onFeedback}>
              遇到漏拦截 / 误拦截？反馈给开发者
            </Button>
          </div>
          <div className="rounded-2xl border border-zinc-200 bg-white p-4">
            <h3 className="text-sm font-bold text-zinc-900">会话拦截日志</h3>
            {log.length === 0 ? (
              <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-6 text-center text-xs text-zinc-400">暂无记录</p>
            ) : (
              <ul className="mt-2.5 max-h-72 space-y-1.5 overflow-y-auto pr-1">
                {log.map((l) => (
                  <li key={l.id} className="flex items-center gap-2 rounded-lg bg-zinc-50 px-2.5 py-1.5 text-[11px]">
                    <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full", l.kind === "preempt" ? "bg-fuchsia-500" : "bg-emerald-500")} />
                    <span className="font-bold text-zinc-800">{l.app}</span>
                    <span className="min-w-0 flex-1 truncate text-zinc-500">{l.text}</span>
                    <span className="shrink-0 font-bold tabular-nums text-emerald-600">{l.ms}ms</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}

/* ═══════════ 手机屏幕（状态机渲染） ═══════════ */
function PhoneScreen({
  stage, app, plan, fakePhase, onLaunch, onBack, onTapFakeSkip,
}: {
  stage: Stage;
  app: ScenarioApp | null;
  plan: BlockPlan | null;
  fakePhase: FakePhase | null;
  onLaunch: (a: ScenarioApp) => void;
  onBack: () => void;
  onTapFakeSkip: () => void;
}) {
  return (
    <div className="relative flex h-full w-full flex-col overflow-hidden bg-zinc-50">
      <StatusBar />
      {stage === "home" && <HomeScreen onLaunch={onLaunch} />}
      {stage === "launch" && app && <LaunchScreen app={app} />}
      {stage === "ad" && app && plan && (
        <AdStage app={app} plan={plan} fakePhase={fakePhase} onTapFakeSkip={onTapFakeSkip} />
      )}
      {stage === "flash" && app && plan && <BlockFlash app={app} plan={plan} />}
      {stage === "main" && app && <MainScreen app={app} onBack={onBack} />}
    </div>
  );
}

function StatusBar() {
  const [time, setTime] = useState("--:--");
  useEffect(() => {
    const tick = () => setTime(new Date().toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }));
    tick();
    const t = setInterval(tick, 15000);
    return () => clearInterval(t);
  }, []);
  return (
    <div className="flex h-8 shrink-0 items-center justify-between bg-inherit px-5 text-[11px] font-semibold text-zinc-800">
      <span className="tabular-nums">{time}</span>
      <span className="absolute left-1/2 top-1.5 h-4 w-20 -translate-x-1/2 rounded-full bg-black" aria-hidden />
      <span className="flex items-center gap-1.5">
        <span className="flex items-center gap-0.5 text-emerald-600" title="秒跳引擎守护中">
          <ShieldCheck className="h-3 w-3" />
        </span>
        <Signal className="h-3 w-3" /> <Wifi className="h-3 w-3" /> <BatteryFull className="h-3.5 w-3.5" />
      </span>
    </div>
  );
}

/* ── 桌面 ── */
function HomeScreen({ onLaunch }: { onLaunch: (a: ScenarioApp) => void }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col bg-gradient-to-b from-violet-100 via-sky-50 to-emerald-50">
      <div className="grid flex-1 grid-cols-3 content-start gap-x-2 gap-y-4 px-5 pt-6">
        {SCENARIO_APPS.map((a) => {
          const Icon = APP_ICONS[a.icon];
          return (
            <button key={a.key} type="button" data-testid={`app-${a.key}`} onClick={() => onLaunch(a)} className="group flex flex-col items-center gap-1.5">
              <span className={cn("flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-md shadow-zinc-300/60 transition group-active:scale-90", a.gradient)}>
                <Icon className="h-6 w-6" />
              </span>
              <span className="text-[11px] font-semibold text-zinc-700">{a.name}</span>
            </button>
          );
        })}
      </div>
      {/* 壁纸提示 */}
      <div className="mx-5 mb-2 rounded-xl bg-white/60 px-3 py-2 text-center text-[10px] leading-relaxed text-zinc-500 backdrop-blur">
        点击应用体验秒跳 · 银行/政务/支付为守护模式 · 喜马拉雅为 SDK 广告窗专案 · 邻里团购为弹窗 v2
      </div>
      {/* Dock */}
      <div className="mx-4 mb-3 flex items-center justify-around rounded-2xl bg-white/70 p-2.5 backdrop-blur">
        {[Phone, MessageCircle, Globe, Camera].map((Icon, i) => (
          <span key={i} className="flex h-10 w-10 items-center justify-center rounded-xl bg-zinc-100 text-zinc-500">
            <Icon className="h-5 w-5" />
          </span>
        ))}
      </div>
    </div>
  );
}

/* ── 启动画面 ── */
function LaunchScreen({ app }: { app: ScenarioApp }) {
  const Icon = APP_ICONS[app.icon];
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3.5 bg-white">
      <span className={cn("flex h-20 w-20 animate-[app-launch_0.85s_ease-out] items-center justify-center rounded-3xl bg-gradient-to-br text-white shadow-lg", app.gradient)}>
        <Icon className="h-9 w-9" />
      </span>
      <div className="text-sm font-semibold tracking-widest text-zinc-400">{app.name}</div>
    </div>
  );
}

/* ── 广告展示（引擎拦截过程演示） ── */
function AdStage({
  app, plan, fakePhase, onTapFakeSkip,
}: {
  app: ScenarioApp;
  plan: BlockPlan;
  fakePhase: FakePhase | null;
  onTapFakeSkip: () => void;
}) {
  const isShake = app.adType === "shake";
  const isAdversarial = !!app.traits?.fakeSkip && (!!app.traits?.chainJump || !!app.traits?.gyroFlip);
  const isPopup = app.adType === "popup";
  const isGyro = !!app.traits?.gyroFlip;
  const isSdkWin = !!app.traits?.sdkWindow;

  const overlayText =
    fakePhase === "scanning"
      ? isPopup
        ? "弹窗多信号扫描中 · 寻找「广告」角标与关闭键"
        : isSdkWin
          ? "SDK 广告窗识别中 · className 指纹比对（零文本可判）"
          : "极速链首扫中 · 假跳过甄别 + 翻转文案识别"
    : fakePhase === "ignored"
      ? isPopup
        ? "已确认「广告」角标 + 关闭键 → 双信号成立"
        : isSdkWin
          ? "SDK 窗指纹命中 · 复核无真跳过键 → 升级直断"
          : isGyro
            ? "已忽略假「跳过」陷阱 · 识别到「翻转手机」文案"
            : "已忽略假「跳过」陷阱 · 监听中"
    : fakePhase === "chain"
      ? isGyro
        ? "陀螺仪诱导 → 返回键直断 + 二跳回弹"
        : "链式二跳识别 → 升级直接阻断"
    : "秒跳引擎拦截中…";

  /* 应用内弹窗广告：主页内容 + 底部弹层广告（角标 + 小号关闭键） */
  if (isPopup) {
    return (
      <div className="relative min-h-0 flex-1" data-testid="ad-stage">
        <GroupBuyScreen compact />
        {/* 弹窗广告层 */}
        <div className="absolute inset-x-4 bottom-4 z-20 overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-zinc-200">
          <div className="flex">
            <div className="h-24 w-24 shrink-0 bg-gradient-to-br from-rose-500 to-orange-400" />
            <div className="min-w-0 flex-1 p-3">
              <div className="flex items-center gap-1.5">
                <span className="rounded bg-zinc-800/80 px-1 text-[8px] font-black text-white">广告</span>
                <span className="truncate text-[11px] font-bold text-zinc-800">{app.ad.brand}</span>
              </div>
              <div className="mt-1 truncate text-[12px] font-black text-zinc-900">{app.ad.title}</div>
              <div className="truncate text-[10px] text-zinc-500">{app.ad.subtitle}</div>
              <button type="button" className="mt-1.5 rounded-full bg-rose-500 px-3 py-1 text-[10px] font-black text-white">
                {app.ad.ctaText}
              </button>
            </div>
            {/* 关闭键：引擎双信号确认后代点 */}
            <button
              type="button"
              data-testid="btn-popup-close"
              className={cn(
                "absolute right-1.5 top-1.5 flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold transition",
                fakePhase === "ignored" || fakePhase === "chain"
                  ? "bg-emerald-500 text-white ring-2 ring-emerald-300"
                  : "bg-zinc-200/80 text-zinc-500",
              )}
              aria-label="关闭广告"
            >
              <X className="h-3 w-3" />
            </button>
          </div>
        </div>
        {/* 引擎状态覆盖层 */}
        <div className="absolute inset-x-0 top-2 z-30 flex justify-center">
          <div className={cn(
            "flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[10px] font-semibold backdrop-blur",
            fakePhase === "ignored" || fakePhase === "chain" ? "bg-emerald-600/85 text-white" : "bg-black/45 text-white/90",
          )}>
            {fakePhase === "ignored" || fakePhase === "chain"
              ? <ShieldCheck className="h-3 w-3" />
              : <Zap className="h-3 w-3 animate-pulse text-amber-300" />}
            {overlayText}
          </div>
        </div>
        {plan.preempt === false && (
          <div className="absolute left-2.5 top-2.5 z-20 rounded-full bg-fuchsia-600/90 px-2 py-0.5 text-[9px] font-bold text-white">
            首拦学习指纹 · 下次启动预拦截
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="relative flex-1" data-testid="ad-stage">
      <AdCreative ad={app.ad} variant="splash" />

      {/* 真跳过按钮（非对抗类：正常跳过键，引擎将代点） */}
      {!isAdversarial && (
        <button
          type="button"
          data-testid="btn-skip"
          className="absolute right-2.5 top-2.5 z-20 rounded-full bg-black/35 px-2.5 py-1 text-[10px] font-semibold text-white backdrop-blur"
        >
          {app.traits?.noCountdown ? "跳过广告" : "跳过"}
        </button>
      )}

      {/* 假跳过陷阱（喜马拉雅类：广告图内嵌的大号假「跳过」，点击即跳转；无倒计时形态文案直配） */}
      {isAdversarial && (
        <button
          type="button"
          data-testid="btn-fake-skip"
          onClick={onTapFakeSkip}
          className={cn(
            "absolute bottom-5 right-3 z-20 flex items-center gap-1.5 rounded-full bg-gradient-to-r from-orange-500 to-amber-400 px-4 py-2 text-[12px] font-black text-white shadow-lg shadow-orange-500/40 transition",
            fakePhase === "ignored" || fakePhase === "chain"
              ? "pointer-events-none opacity-60 ring-2 ring-rose-500 ring-offset-2 ring-offset-black/20"
              : "animate-pulse",
          )}
        >
          {fakePhase === "ignored" || fakePhase === "chain" ? (
            <>
              <span className="line-through decoration-rose-500 decoration-2">{app.traits?.noCountdown ? "跳过广告" : "跳过 5"}</span>
              <span className="rounded bg-rose-500 px-1 text-[9px]">陷阱已忽略</span>
            </>
          ) : (
            <>{app.traits?.noCountdown ? "跳过广告" : "跳过 5"}</>
          )}
        </button>
      )}

      {/* 陀螺仪诱导提示条（喜马拉雅实测：翻转手机，跳转详情页或第三方应用） */}
      {isGyro && (
        <div className={cn(
          "absolute bottom-11 left-1/2 z-20 flex max-w-[92%] -translate-x-1/2 items-center gap-1 rounded-full bg-black/55 px-3 py-1.5 text-[10px] font-bold text-white backdrop-blur transition",
          fakePhase === "chain" && "bg-rose-600/80",
        )} data-testid="gyro-hint">
          <Smartphone className={cn("h-3.5 w-3.5", fakePhase === "chain" ? "text-white" : "animate-pulse text-amber-300")} />
          翻转手机，跳转详情页或第三方应用
        </div>
      )}

      {/* 摇一摇角标 */}
      {isShake && (
        <div className="absolute bottom-16 left-1/2 z-20 flex -translate-x-1/2 items-center gap-1 rounded-full bg-black/45 px-3 py-1.5 text-[11px] font-bold text-white backdrop-blur">
          <Vibrate className="h-3.5 w-3.5 animate-pulse text-amber-300" />
          传感器已被引擎挂起
        </div>
      )}
      {/* Deeplink 角标 */}
      {app.adType === "deeplink" && (
        <div className="absolute bottom-16 left-1/2 z-20 flex -translate-x-1/2 items-center gap-1 rounded-full bg-black/45 px-3 py-1.5 text-[11px] font-bold text-white backdrop-blur">
          <Link2Off className="h-3.5 w-3.5 text-rose-300" />
          跳转意图已断链
        </div>
      )}

      {/* 引擎状态覆盖层 */}
      <div className="absolute inset-x-0 bottom-0 z-10 flex items-center justify-center pb-6">
        <div className={cn(
          "flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[10px] font-semibold backdrop-blur",
          fakePhase === "chain" ? "bg-rose-600/80 text-white" : "bg-black/45 text-white/90",
        )}>
          {fakePhase === "chain"
            ? <Link2Off className="h-3 w-3" />
            : <Zap className="h-3 w-3 animate-pulse text-amber-300" />}
          {overlayText}
        </div>
      </div>

      {/* 首启学习提示（尚未沉淀指纹的应用） */}
      {plan.preempt === false && (
        <div className="absolute left-2.5 top-2.5 z-20 rounded-full bg-fuchsia-600/90 px-2 py-0.5 text-[9px] font-bold text-white">
          首拦学习指纹 · 下次启动预拦截
        </div>
      )}
    </div>
  );
}

/* ── 拦截成功闪光层 ── */
function BlockFlash({ app, plan }: { app: ScenarioApp; plan: BlockPlan }) {
  return (
    <div className="relative flex-1 animate-[toast-in_.2s_ease-out]" data-testid={plan.preempt ? "preempt-flash" : "block-flash"}>
      <div className="absolute inset-0 bg-zinc-950/92" />
      <div className="relative flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
        <span className={cn(
          "flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-lg",
          plan.preempt ? "from-fuchsia-500 to-violet-600" : app.guard ? "from-emerald-500 to-teal-600" : "from-violet-500 to-fuchsia-600",
        )}>
          {plan.preempt ? <Timer className="h-8 w-8" /> : app.guard ? <ShieldCheck className="h-8 w-8" /> : <Zap className="h-8 w-8" />}
        </span>
        <div className="text-[15px] font-black leading-snug text-white">{plan.headline}</div>
        <div className="text-[11px] text-zinc-400">{plan.subline}</div>
        <div className="mt-1 flex items-center gap-2">
          <span className={cn(
            "rounded-lg px-3 py-1.5 text-lg font-black tabular-nums ring-1",
            plan.preempt ? "bg-white/10 text-fuchsia-300 ring-fuchsia-400/30" : "bg-white/10 text-emerald-400 ring-emerald-400/30",
          )}>
            {plan.totalMs}ms
          </span>
          <span className="rounded-lg bg-white/10 px-3 py-1.5 text-[11px] font-bold text-zinc-200 ring-1 ring-white/10">
            置信度 {plan.confidence}%
          </span>
        </div>
        {plan.preempt ? (
          <div className="mt-1 flex items-center gap-1.5 rounded-full bg-fuchsia-500/15 px-3 py-1 text-[10px] font-bold text-fuchsia-300 ring-1 ring-fuchsia-400/30">
            <Timer className="h-3 w-3" /> 0ms 广告可见时间 · 用户零感知
          </div>
        ) : app.guard ? (
          <div className="mt-1 flex items-center gap-1.5 rounded-full bg-emerald-500/15 px-3 py-1 text-[10px] font-bold text-emerald-300 ring-1 ring-emerald-400/30">
            <ShieldCheck className="h-3 w-3" /> 应用进程与服务未受任何影响
          </div>
        ) : null}
      </div>
    </div>
  );
}

/* ── 各应用主页 ── */
function MainScreen({ app, onBack }: { app: ScenarioApp; onBack: () => void }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col bg-zinc-50">
      {app.guard && (
        <div className="flex items-center gap-1.5 bg-emerald-600 px-4 py-1.5 text-[10px] font-bold text-white">
          <ShieldCheck className="h-3 w-3" /> 守护模式：开屏广告已拦截 · 应用服务保持运行
        </div>
      )}
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        {app.screen === "mall" && <MallScreen />}
        {app.screen === "game" && <GameScreen />}
        {app.screen === "bank" && <BankScreen />}
        {app.screen === "gov" && <GovScreen />}
        {app.screen === "pay" && <PayScreen />}
        {app.screen === "video" && <VideoScreen />}
        {app.screen === "audio" && <AudioScreen />}
      </div>
      {/* 底部返回 */}
      <button type="button" data-testid="btn-home" onClick={onBack} className="flex h-9 shrink-0 items-center justify-center gap-1 border-t border-zinc-200 bg-white text-[11px] font-semibold text-zinc-500 active:bg-zinc-50">
        <ChevronLeft className="h-3.5 w-3.5" /> 返回桌面
      </button>
    </div>
  );
}

function AppHeader({ app, right }: { app: ScenarioApp; right?: React.ReactNode }) {
  const Icon = APP_ICONS[app.icon];
  return (
    <div className="sticky top-0 z-10 flex items-center gap-2.5 border-b border-zinc-100 bg-white/95 px-4 pb-2.5 pt-1 backdrop-blur">
      <span className={cn("flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br text-white", app.gradient)}>
        <Icon className="h-4 w-4" />
      </span>
      <span className="text-[15px] font-bold text-zinc-900">{app.name}</span>
      <span className="ml-auto flex items-center gap-2.5 text-zinc-400">{right}</span>
    </div>
  );
}

function MallScreen() {
  const goods = [
    { name: "无线降噪耳机", price: "¥299", g: "from-violet-400 to-fuchsia-500" },
    { name: "智能手环 Pro", price: "¥399", g: "from-sky-400 to-blue-500" },
    { name: "便携咖啡机", price: "¥199", g: "from-amber-400 to-orange-500" },
    { name: "机械键盘 87 键", price: "¥249", g: "from-emerald-400 to-teal-500" },
  ];
  return (
    <div>
      <AppHeader app={SCENARIO_APPS[0]} right={<Search className="h-4 w-4" />} />
      <div className="p-3">
        <div className="mb-3 h-20 rounded-xl bg-gradient-to-r from-rose-500 to-orange-400 p-3 text-white">
          <div className="text-sm font-black">限时秒杀 · 12:00 场</div>
          <div className="text-[10px] opacity-90">全场包邮 · 每 30 分钟上新</div>
        </div>
        <div className="grid grid-cols-2 gap-2.5">
          {goods.map((g) => (
            <div key={g.name} className="overflow-hidden rounded-xl bg-white ring-1 ring-zinc-100">
              <div className={cn("h-24 bg-gradient-to-br", g.g)} />
              <div className="p-2">
                <div className="truncate text-[11px] font-bold text-zinc-800">{g.name}</div>
                <div className="mt-0.5 text-xs font-black text-rose-600">{g.price}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function GameScreen() {
  return (
    <div className="flex min-h-full flex-col bg-gradient-to-b from-zinc-950 to-purple-950 text-white">
      <div className="flex items-center justify-between px-4 pb-2 pt-1 text-[11px] text-zinc-300">
        <span className="font-bold">星际远征</span>
        <span>Lv.42 · 战力 128,600</span>
      </div>
      <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 pb-8">
        <div className="text-center">
          <div className="text-xl font-black tracking-widest text-fuchsia-300">S3 赛季开启</div>
          <div className="mt-1 text-[11px] text-zinc-400">段位重置 · 赛季皮肤限时领取</div>
        </div>
        <button type="button" className="rounded-full bg-gradient-to-r from-fuchsia-500 to-purple-600 px-10 py-3 text-sm font-black shadow-lg shadow-fuchsia-500/40 active:scale-95">
          开始游戏
        </button>
        <div className="grid w-full grid-cols-3 gap-2 pt-2 text-center text-[10px] text-zinc-300">
          {["排位赛", "剧情模式", "军团"].map((t) => (
            <div key={t} className="rounded-xl bg-white/10 py-3 font-bold ring-1 ring-white/10">{t}</div>
          ))}
        </div>
      </div>
    </div>
  );
}

function BankScreen() {
  return (
    <div>
      <AppHeader app={SCENARIO_APPS.find((a) => a.key === "bank")!} right={<UserRound className="h-4 w-4" />} />
      <div className="p-4">
        <div className="rounded-2xl bg-gradient-to-br from-sky-700 to-indigo-700 p-4 text-white shadow-md">
          <div className="text-[10px] opacity-80">储蓄卡（**** 8867）</div>
          <div className="mt-1 text-2xl font-black tabular-nums">¥ 12,345.67</div>
          <div className="mt-2 flex gap-4 text-[10px] opacity-80">
            <span>待收利息 ¥23.40</span>
            <span>积分 4,520</span>
          </div>
        </div>
        <div className="mt-4 grid grid-cols-4 gap-2 text-center text-[10px] font-semibold text-zinc-600">
          {[
            { icon: ArrowLeftRight, t: "转账" },
            { icon: ScanLine, t: "扫一扫" },
            { icon: Receipt, t: "明细" },
            { icon: TrendingUp, t: "理财" },
          ].map(({ icon: Icon, t }) => (
            <div key={t} className="rounded-xl bg-white py-3 ring-1 ring-zinc-100">
              <Icon className="mx-auto h-4.5 w-4.5 text-sky-600" />
              <div className="mt-1">{t}</div>
            </div>
          ))}
        </div>
        <div className="mt-4 space-y-2">
          {["工资到账 +8,420.00", "消费 -128.50 · 便利店"].map((t) => (
            <div key={t} className="flex items-center justify-between rounded-xl bg-white px-3 py-2.5 text-[11px] ring-1 ring-zinc-100">
              <span className="text-zinc-700">{t}</span>
              <PiggyBank className="h-3.5 w-3.5 text-zinc-300" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function GovScreen() {
  const services = [
    { icon: FileText, t: "社保查询", d: "参保信息 · 缴费明细" },
    { icon: PiggyBank, t: "公积金", d: "余额 · 提取预约" },
    { icon: CalendarClock, t: "办事预约", d: "政务大厅取号" },
    { icon: Receipt, t: "电子证照", d: "身份证 · 驾驶证" },
  ];
  return (
    <div>
      <AppHeader app={SCENARIO_APPS.find((a) => a.key === "gov")!} />
      <div className="p-4">
        <div className="rounded-2xl bg-gradient-to-r from-teal-600 to-emerald-500 p-4 text-white">
          <div className="text-sm font-black">一键办事</div>
          <div className="mt-0.5 text-[10px] opacity-90">高频服务 · 免排队 · 全程网办</div>
        </div>
        <div className="mt-4 space-y-2.5">
          {services.map(({ icon: Icon, t, d }) => (
            <div key={t} className="flex items-center gap-3 rounded-xl bg-white px-3.5 py-3 ring-1 ring-zinc-100">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-teal-50 text-teal-600">
                <Icon className="h-4.5 w-4.5" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="text-xs font-bold text-zinc-800">{t}</div>
                <div className="text-[10px] text-zinc-400">{d}</div>
              </div>
              <ChevronLeft className="h-4 w-4 rotate-180 text-zinc-300" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function PayScreen() {
  return (
    <div>
      <AppHeader app={SCENARIO_APPS.find((a) => a.key === "pay")!} right={<UserRound className="h-4 w-4" />} />
      <div className="flex flex-col items-center gap-4 p-6">
        <button type="button" className="flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-emerald-500 to-teal-600 text-white shadow-lg shadow-emerald-500/30 active:scale-95">
          <ScanLine className="h-8 w-8" />
        </button>
        <div className="text-[11px] font-semibold text-zinc-500">扫一扫 · 收付款</div>
        <div className="grid w-full grid-cols-3 gap-2 text-center text-[10px] font-semibold text-zinc-600">
          {["乘车码", "生活缴费", "红包"].map((t) => (
            <div key={t} className="rounded-xl bg-white py-3 ring-1 ring-zinc-100">{t}</div>
          ))}
        </div>
        <div className="w-full space-y-2">
          {["商户消费 -36.00 · 超市", "红包 +8.88 · 好友"].map((t) => (
            <div key={t} className="rounded-xl bg-white px-3 py-2.5 text-[11px] text-zinc-700 ring-1 ring-zinc-100">{t}</div>
          ))}
        </div>
      </div>
    </div>
  );
}

function VideoScreen() {
  const videos = [
    { t: "3 分钟看懂量子计算", a: "科技星说", g: "from-violet-500 to-purple-600", hot: "128 万" },
    { t: "城市夜跑 vlog", a: "跑者小K", g: "from-sky-500 to-cyan-500", hot: "36 万" },
    { t: "深夜食堂 · 一碗葱油面", a: "灶台笔记", g: "from-amber-500 to-orange-500", hot: "89 万" },
  ];
  return (
    <div>
      <AppHeader app={SCENARIO_APPS.find((a) => a.key === "video")!} right={<Search className="h-4 w-4" />} />
      <div className="space-y-3 p-3">
        {videos.map((v) => (
          <div key={v.t} className="overflow-hidden rounded-xl bg-white ring-1 ring-zinc-100">
            <div className={cn("relative flex h-32 items-center justify-center bg-gradient-to-br", v.g)}>
              <Tv className="h-8 w-8 text-white/70" />
              <span className="absolute bottom-1.5 right-2 rounded bg-black/40 px-1.5 py-0.5 text-[9px] font-bold text-white">02:14</span>
            </div>
            <div className="flex items-center justify-between p-2.5">
              <div className="min-w-0">
                <div className="truncate text-xs font-bold text-zinc-800">{v.t}</div>
                <div className="mt-0.5 flex items-center gap-2 text-[10px] text-zinc-400">
                  <span className="flex items-center gap-0.5"><Music2 className="h-2.5 w-2.5" />{v.a}</span>
                  <span className="flex items-center gap-0.5"><Flame className="h-2.5 w-2.5 text-rose-400" />{v.hot}</span>
                </div>
              </div>
              <div className="flex items-center gap-2.5 text-zinc-300">
                <ThumbsUp className="h-4 w-4" />
                <Share2 className="h-4 w-4" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function AudioScreen() {
  const audios = [
    { t: "深夜电台 · 都市情感树洞", a: "夜声Live", d: "12.4 万人在听", g: "from-orange-400 to-amber-500" },
    { t: "三分钟财经早知道", a: "财听天下", d: "早 7 点更新", g: "from-sky-400 to-blue-500" },
    { t: "郭氏相声精选集", a: "曲艺茶馆", d: "完本 · 342 集", g: "from-rose-400 to-pink-500" },
    { t: "枕边名著 · 平凡的世界", a: "声书馆", d: "播到第 96 集", g: "from-emerald-400 to-teal-500" },
  ];
  return (
    <div>
      <AppHeader app={SCENARIO_APPS.find((a) => a.key === "ximalaya")!} right={<Search className="h-4 w-4" />} />
      <div className="p-3">
        <div className="flex items-center gap-3 rounded-2xl bg-gradient-to-r from-orange-500 to-amber-400 p-3.5 text-white">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/20">
            <Play className="h-5 w-5" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm font-black">继续收听 · 夜听电台 第 214 期</div>
            <div className="mt-0.5 text-[10px] opacity-90">已播 18:26 / 42:10</div>
          </div>
        </div>
        <div className="mt-3 space-y-2">
          {audios.map((v) => (
            <div key={v.t} className="flex items-center gap-3 rounded-xl bg-white px-3 py-2.5 ring-1 ring-zinc-100">
              <span className={cn("flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white", v.g)}>
                <Music2 className="h-4.5 w-4.5" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="truncate text-xs font-bold text-zinc-800">{v.t}</div>
                <div className="mt-0.5 flex items-center gap-2 text-[10px] text-zinc-400">
                  <span>{v.a}</span>
                  <span>·</span>
                  <span>{v.d}</span>
                </div>
              </div>
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-orange-50 text-orange-500">
                <Play className="h-3.5 w-3.5" />
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── 邻里团购主页（弹窗广告宿主） ── */
function GroupBuyScreen({ compact = false }: { compact?: boolean }) {
  const goods = [
    { name: "红颜草莓 2 盒", price: "¥19.9", g: "from-rose-400 to-red-500", tag: "热卖" },
    { name: "本地青菜 3 斤", price: "¥6.8", g: "from-lime-400 to-green-500", tag: "今日到货" },
    { name: "土鸡蛋 30 枚", price: "¥25.0", g: "from-amber-400 to-orange-500", tag: "补货" },
    { name: "纯牛奶 12 盒", price: "¥39.9", g: "from-sky-400 to-blue-500", tag: "满减" },
  ];
  return (
    <div className={cn("flex min-h-0 flex-1 flex-col bg-zinc-50", compact && "min-h-0")}>
      <AppHeader app={SCENARIO_APPS.find((a) => a.key === "groupbuy")!} right={<Search className="h-4 w-4" />} />
      <div className={cn("overflow-y-auto", compact ? "flex-1" : "min-h-0 flex-1")}>
        <div className="p-3">
          <div className="mb-3 rounded-xl bg-gradient-to-r from-lime-500 to-green-500 p-3 text-white">
            <div className="text-sm font-black">周末自提团 · 楼下驿站 17:00 开柜</div>
            <div className="text-[10px] opacity-90">2.1 万人参团 · 本小区已拼 342 单</div>
          </div>
          <div className="grid grid-cols-2 gap-2.5">
            {goods.map((g) => (
              <div key={g.name} className="overflow-hidden rounded-xl bg-white ring-1 ring-zinc-100">
                <div className={cn("relative h-20 bg-gradient-to-br", g.g)}>
                  <span className="absolute left-1.5 top-1.5 rounded bg-black/35 px-1 text-[8px] font-bold text-white">{g.tag}</span>
                </div>
                <div className="p-2">
                  <div className="truncate text-[11px] font-bold text-zinc-800">{g.name}</div>
                  <div className="mt-0.5 flex items-center justify-between">
                    <span className="text-xs font-black text-rose-600">{g.price}</span>
                    <span className="rounded-full bg-rose-500 px-2 py-0.5 text-[9px] font-bold text-white">加购</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
