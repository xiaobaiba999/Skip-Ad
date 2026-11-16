"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AdCreative } from "@/components/ad-creative";
import { SCENARIO_APPS, type ScenarioApp } from "@/lib/scenario-apps";
import { planInstantBlock, AD_TYPE_LABEL, type BlockPlan } from "@/lib/instant-engine";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import {
  ShoppingBag, Rocket, Landmark, Building2, Wallet, Play, Phone, MessageCircle,
  Globe, Camera, Signal, Wifi, BatteryFull, Zap, ShieldCheck, ShieldOff, Vibrate,
  Link2Off, ChevronLeft, Search, ScanLine, ArrowLeftRight, Receipt, TrendingUp,
  PiggyBank, UserRound, FileText, CalendarClock, Heart, ThumbsUp, Share2,
  Flame, Tv, Music2, Activity, Gauge, Timer,
} from "lucide-react";

type Stage = "home" | "launch" | "ad" | "flash" | "main" | "landing";

const APP_ICONS: Record<string, React.ElementType> = {
  "shopping-bag": ShoppingBag,
  rocket: Rocket,
  landmark: Landmark,
  "building-2": Building2,
  wallet: Wallet,
  play: Play,
};

interface SessionLogItem {
  id: number;
  app: string;
  kind: "block" | "miss" | "jump";
  text: string;
  ms: number;
}

export function Arena({ onFeedback }: { onFeedback: () => void }) {
  const [engineOn, setEngineOn] = useState(true);
  const [app, setApp] = useState<ScenarioApp | null>(null);
  const [stage, setStage] = useState<Stage>("home");
  const [plan, setPlan] = useState<BlockPlan | null>(null);
  const [countdown, setCountdown] = useState(0); // 对照模式倒计时（0.1s 精度）
  const [shakeAnim, setShakeAnim] = useState(false);
  const [learnedApps, setLearnedApps] = useState<Set<string>>(new Set());
  const [blocks, setBlocks] = useState(0);
  const [misses, setMisses] = useState(0);
  const [msSum, setMsSum] = useState(0);
  const [log, setLog] = useState<SessionLogItem[]>([]);
  const [toast, setToast] = useState<{ id: number; text: string; sub?: string } | null>(null);
  const adShownAtRef = useRef(0);
  const logIdRef = useRef(0);
  const toastIdRef = useRef(0);

  const pushToast = useCallback((text: string, sub?: string) => {
    const id = ++toastIdRef.current;
    setToast({ id, text, sub });
    setTimeout(() => setToast((t) => (t?.id === id ? null : t)), 3200);
  }, []);

  const pushLog = useCallback((item: Omit<SessionLogItem, "id">) => {
    setLog((cur) => [{ ...item, id: ++logIdRef.current }, ...cur].slice(0, 12));
  }, []);

  /* 学习规则名（命中则秒跳置信度更高） */
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
    async (kind: "block" | "miss", p: BlockPlan | null, latencyMs: number, note: string) => {
      if (!app) return;
      try {
        const r = await fetch("/api/block-events", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            appKey: app.key,
            kind,
            action: kind === "block" ? p?.action ?? "instant_skip" : "miss",
            latencyMs,
            confidence: kind === "block" ? (p?.confidence ?? 95) / 100 : 0.5,
            scenario: p?.scenario ?? "normal",
            adType: app.adType,
            note,
          }),
        });
        if (r.ok) {
          const d = await r.json();
          if (d.learnedCreated) {
            pushToast("学习规则已生成", `${app.name} 专属秒跳规则已沉淀，下次优先秒跳`);
            void refreshLearning();
          }
        }
      } catch { /* 静默 */ }
    },
    [app, pushToast, refreshLearning],
  );

  /* ── 启动应用 ── */
  const launchApp = useCallback(
    (a: ScenarioApp) => {
      setApp(a);
      setPlan(null);
      setStage("launch");
    },
    [],
  );

  /* ── 阶段流转 ── */
  useEffect(() => {
    if (stage === "launch" && app) {
      const t = setTimeout(() => {
        adShownAtRef.current = Date.now();
        setCountdown(app.ad.countdownSec * 10);
        setPlan(
          planInstantBlock({
            name: app.name,
            category: app.category,
            adType: app.adType,
            guard: app.guard,
            ruleName: app.ruleName,
            learnedRuleName: learnedApps.has(app.name) ? `${app.name} 学习规则` : null,
          }),
        );
        setStage("ad");
      }, 850);
      return () => clearTimeout(t);
    }
    if (stage === "flash") {
      const t = setTimeout(() => setStage("main"), 1150);
      return () => clearTimeout(t);
    }
  }, [stage, app, learnedApps]);

  /* 引擎开启：广告出现 → 毫秒级拦截 */
  useEffect(() => {
    if (stage !== "ad" || !app || !engineOn) return;
    const t = setTimeout(() => {
      const p = plan;
      if (p) {
        setBlocks((n) => n + 1);
        setMsSum((s) => s + p.totalMs);
        pushLog({ app: app.name, kind: "block", text: `${p.ruleName} · ${AD_TYPE_LABEL[app.adType]}`, ms: p.totalMs });
        void postEvent("block", p, p.totalMs, p.ruleName);
      }
      setStage("flash");
    }, 700);
    return () => clearTimeout(t);
  }, [stage, app, engineOn, plan, postEvent, pushLog]);

  /* 引擎关闭（对照模式）：倒计时 → 归零自动跳转落地页（变迁全部在定时器回调中执行） */
  useEffect(() => {
    if (stage !== "ad" || !app || engineOn || countdown <= 0) return;
    const t = setTimeout(() => {
      if (countdown <= 1) {
        setStage("landing");
        setMisses((n) => n + 1);
        pushLog({ app: app.name, kind: "jump", text: "倒计时归零自动跳转落地页", ms: app.ad.countdownSec * 1000 });
        void postEvent("miss", null, app.ad.countdownSec * 1000, "对照模式：倒计时归零自动跳转");
      } else {
        setCountdown((c) => c - 1);
      }
    }, 100);
    return () => clearTimeout(t);
  }, [stage, app, engineOn, countdown, postEvent, pushLog]);

  /* ── 对照模式交互 ── */
  const manualSkip = useCallback(() => {
    if (!app) return;
    const ms = Date.now() - adShownAtRef.current;
    setMisses((n) => n + 1);
    pushLog({ app: app.name, kind: "miss", text: "手动点击「跳过」（按钮过小）", ms });
    void postEvent("miss", null, ms, "对照模式：用户手动跳过");
    pushToast("已记录手动跳过", "同一应用累计 2 次将自动生成学习规则");
    setStage("main");
  }, [app, postEvent, pushLog, pushToast]);

  const tryShake = useCallback(() => {
    if (!app || stage !== "ad" || engineOn) return;
    setShakeAnim(true);
    setTimeout(() => setShakeAnim(false), 700);
    setTimeout(() => {
      setStage("landing");
      setMisses((n) => n + 1);
      pushLog({ app: app.name, kind: "jump", text: "摇一摇触发「有效点击」跳转", ms: 0 });
      void postEvent("miss", null, 300, "对照模式：摇一摇触发跳转");
    }, 720);
  }, [app, stage, engineOn, postEvent, pushLog]);

  const backHome = useCallback(() => {
    setApp(null);
    setStage("home");
    setPlan(null);
  }, []);

  const avgMs = blocks > 0 ? Math.round(msSum / blocks) : 0;

  return (
    <div className="flex flex-col gap-5 lg:flex-row lg:items-start">
      {/* ═══ 左：手机模拟器 ═══ */}
      <section className="flex w-full shrink-0 flex-col items-center gap-3 lg:w-[420px]" aria-label="演练场手机模拟器">
        <div
          className={cn(
            "relative w-full max-w-[360px] rounded-[2.6rem] border-[10px] border-zinc-800 bg-zinc-800 shadow-2xl shadow-zinc-900/25 ring-1 ring-zinc-700",
            shakeAnim && "animate-[phone-shake_.7s_ease-in-out]",
          )}
        >
          <div className="overflow-hidden rounded-[2rem] bg-white">
            <div className="relative aspect-[9/19] w-full select-none">
              <PhoneScreen
                stage={stage}
                app={app}
                plan={plan}
                engineOn={engineOn}
                countdown={countdown}
                onLaunch={launchApp}
                onSkip={manualSkip}
                onBack={backHome}
                onBackFromLanding={() => setStage("main")}
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

        {/* 摇一摇触发（仅对照模式 + 摇一摇广告展示中） */}
        {stage === "ad" && app?.adType === "shake" && !engineOn && (
          <Button
            size="sm"
            data-testid="btn-shake"
            variant="outline"
            className="h-8 rounded-lg border-orange-300 text-orange-600 hover:bg-orange-50"
            onClick={tryShake}
          >
            <Vibrate className="mr-1 h-3.5 w-3.5" /> 模拟摇晃手机
          </Button>
        )}
      </section>

      {/* ═══ 右：引擎面板 ═══ */}
      <section className="flex min-w-0 flex-1 flex-col gap-4" aria-label="秒跳引擎面板">
        {/* 引擎开关 */}
        <div className={cn("rounded-2xl border p-4 transition-colors", engineOn ? "border-emerald-200 bg-emerald-50/60" : "border-rose-200 bg-rose-50/60")}>
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <span className={cn("flex h-9 w-9 items-center justify-center rounded-xl text-white", engineOn ? "bg-gradient-to-br from-emerald-500 to-teal-600" : "bg-gradient-to-br from-rose-500 to-red-600")}>
                {engineOn ? <ShieldCheck className="h-4.5 w-4.5" /> : <ShieldOff className="h-4.5 w-4.5" />}
              </span>
              <div>
                <div className="flex items-center gap-2 text-sm font-bold text-zinc-900">
                  秒跳引擎
                  <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-bold", engineOn ? "bg-emerald-600 text-white" : "bg-rose-600 text-white")}>
                    {engineOn ? "运行中" : "对照模式"}
                  </span>
                </div>
                <p className="mt-0.5 text-[11px] text-zinc-500">
                  {engineOn ? "任何应用开屏广告毫秒级识别并代点跳过，传感器与跳转意图同步拦截" : "模拟未装本引擎的手机：小小跳过键 / 摇一摇即跳 / 倒计时归零自动跳转"}
                </p>
              </div>
            </div>
            <Switch data-testid="engine-switch" checked={engineOn} onCheckedChange={setEngineOn} aria-label="秒跳引擎开关" />
          </div>
          {!engineOn && (
            <div className="mt-3 flex items-start gap-1.5 rounded-lg bg-rose-100/80 p-2 text-[11px] leading-relaxed text-rose-700">
              <Flame className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              对照模式下你的每次手动跳过都会被学习中心记录：同一应用累计 2 次即自动生成专属秒跳规则（到「学习中心」查看，可触发反馈与夜间同步）。
            </div>
          )}
        </div>

        {/* 统计 chips */}
        <div className="grid grid-cols-3 gap-2.5">
          {[
            { icon: Zap, label: "本次会话拦截", value: `${blocks} 次`, tone: "text-emerald-600" },
            { icon: Gauge, label: "平均拦截耗时", value: blocks > 0 ? `${avgMs} ms` : "—", tone: "text-violet-600" },
            { icon: Timer, label: "漏拦截 / 手动", value: `${misses} 次`, tone: misses > 0 ? "text-rose-600" : "text-zinc-500" },
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
              <span className="rounded-full bg-violet-50 px-2 py-0.5 text-[10px] font-bold text-violet-700 ring-1 ring-violet-200">
                {plan.ruleName}
              </span>
            )}
          </div>
          {plan ? (
            <>
              <div className="mt-3 flex items-baseline gap-2">
                <span className="text-2xl font-black tabular-nums text-zinc-900">{plan.totalMs}<span className="text-sm">ms</span></span>
                <span className="text-[11px] font-semibold text-zinc-500">总耗时</span>
                <span className="ml-auto text-[11px] font-bold text-emerald-600">置信度 {plan.confidence}%</span>
              </div>
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
              点按左侧任意应用图标启动，观察秒跳引擎的毫秒级拦截过程
            </p>
          )}
        </div>

        {/* 场景速览 + 会话日志 */}
        <div className="grid gap-4 md:grid-cols-2">
          <div className="rounded-2xl border border-zinc-200 bg-white p-4">
            <h3 className="text-sm font-bold text-zinc-900">六大场景</h3>
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
                        {learnedApps.has(a.name) && <span className="rounded bg-violet-100 px-1 text-[9px] font-bold text-violet-700">已学习</span>}
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
                    <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full", l.kind === "block" ? "bg-emerald-500" : "bg-rose-500")} />
                    <span className="font-bold text-zinc-800">{l.app}</span>
                    <span className="min-w-0 flex-1 truncate text-zinc-500">{l.text}</span>
                    <span className={cn("shrink-0 font-bold tabular-nums", l.kind === "block" ? "text-emerald-600" : "text-rose-500")}>
                      {l.kind === "block" ? `${l.ms}ms` : "漏"}
                    </span>
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
  stage, app, plan, engineOn, countdown, onLaunch, onSkip, onBack, onBackFromLanding,
}: {
  stage: Stage;
  app: ScenarioApp | null;
  plan: BlockPlan | null;
  engineOn: boolean;
  countdown: number;
  onLaunch: (a: ScenarioApp) => void;
  onSkip: () => void;
  onBack: () => void;
  onBackFromLanding: () => void;
}) {
  return (
    <div className="relative flex h-full w-full flex-col overflow-hidden bg-zinc-50">
      <StatusBar engineOn={engineOn} />
      {stage === "home" && <HomeScreen onLaunch={onLaunch} />}
      {stage === "launch" && app && <LaunchScreen app={app} />}
      {stage === "ad" && app && (
        <AdStage app={app} engineOn={engineOn} countdown={countdown} onSkip={onSkip} />
      )}
      {stage === "flash" && app && plan && <BlockFlash app={app} plan={plan} />}
      {stage === "main" && app && <MainScreen app={app} onBack={onBack} />}
      {stage === "landing" && app && <LandingScreen app={app} onBack={onBackFromLanding} />}
    </div>
  );
}

function StatusBar({ engineOn }: { engineOn: boolean }) {
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
        {engineOn && (
          <span className="flex items-center gap-0.5 text-emerald-600" title="秒跳引擎守护中">
            <ShieldCheck className="h-3 w-3" />
          </span>
        )}
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
        点击任意应用体验开屏秒跳 · 银行/政务/支付为守护模式
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

/* ── 广告展示（引擎开：等待秒跳；引擎关：对照体验） ── */
function AdStage({ app, engineOn, countdown, onSkip }: { app: ScenarioApp; engineOn: boolean; countdown: number; onSkip: () => void }) {
  const isShake = app.adType === "shake";
  return (
    <div className="relative flex-1" data-testid="ad-stage">
      <AdCreative ad={app.ad} variant="splash" />
      {/* 跳过按钮（故意做小，还原真实体验） */}
      <button
        type="button"
        data-testid="btn-skip"
        onClick={onSkip}
        className="absolute right-2.5 top-2.5 z-20 rounded-full bg-black/35 px-2.5 py-1 text-[10px] font-semibold text-white backdrop-blur active:scale-95"
      >
        {engineOn ? "跳过" : `跳过 ${Math.ceil(countdown / 10)}`}
      </button>
      {/* 倒计时进度条（对照模式） */}
      {!engineOn && (
        <div className="absolute inset-x-0 bottom-0 z-20 h-1 bg-black/20">
          <div className="h-full bg-white/90 transition-[width] duration-100" style={{ width: `${(countdown / (app.ad.countdownSec * 10)) * 100}%` }} />
        </div>
      )}
      {/* 摇一摇角标 */}
      {isShake && (
        <div className="absolute bottom-16 left-1/2 z-20 flex -translate-x-1/2 items-center gap-1 rounded-full bg-black/45 px-3 py-1.5 text-[11px] font-bold text-white backdrop-blur">
          <Vibrate className="h-3.5 w-3.5 animate-pulse text-amber-300" />
          {engineOn ? "传感器已被引擎挂起" : "轻微晃动即跳转 · 灵敏度异常"}
        </div>
      )}
      {/* Deeplink 角标 */}
      {app.adType === "deeplink" && (
        <div className="absolute bottom-16 left-1/2 z-20 flex -translate-x-1/2 items-center gap-1 rounded-full bg-black/45 px-3 py-1.5 text-[11px] font-bold text-white backdrop-blur">
          <Link2Off className="h-3.5 w-3.5 text-rose-300" />
          {engineOn ? "跳转意图已断链" : "倒计时归零将唤起外部应用"}
        </div>
      )}
      {engineOn && (
        <div className="absolute inset-0 z-10 flex items-end justify-center pb-6">
          <div className="flex items-center gap-1.5 rounded-full bg-black/40 px-3 py-1 text-[10px] font-semibold text-white/90 backdrop-blur">
            <Zap className="h-3 w-3 animate-pulse text-amber-300" /> 秒跳引擎拦截中…
          </div>
        </div>
      )}
    </div>
  );
}

/* ── 拦截成功闪光层 ── */
function BlockFlash({ app, plan }: { app: ScenarioApp; plan: BlockPlan }) {
  return (
    <div className="relative flex-1 animate-[toast-in_.2s_ease-out]" data-testid="block-flash">
      <div className="absolute inset-0 bg-zinc-950/92" />
      <div className="relative flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
        <span className={cn("flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-lg", app.guard ? "from-emerald-500 to-teal-600" : "from-violet-500 to-fuchsia-600")}>
          {app.guard ? <ShieldCheck className="h-8 w-8" /> : <Zap className="h-8 w-8" />}
        </span>
        <div className="text-[15px] font-black leading-snug text-white">{plan.headline}</div>
        <div className="text-[11px] text-zinc-400">{plan.subline}</div>
        <div className="mt-1 flex items-center gap-2">
          <span className="rounded-lg bg-white/10 px-3 py-1.5 text-lg font-black tabular-nums text-emerald-400 ring-1 ring-emerald-400/30">
            {plan.totalMs}ms
          </span>
          <span className="rounded-lg bg-white/10 px-3 py-1.5 text-[11px] font-bold text-zinc-200 ring-1 ring-white/10">
            置信度 {plan.confidence}%
          </span>
        </div>
        {app.guard && (
          <div className="mt-1 flex items-center gap-1.5 rounded-full bg-emerald-500/15 px-3 py-1 text-[10px] font-bold text-emerald-300 ring-1 ring-emerald-400/30">
            <ShieldCheck className="h-3 w-3" /> 应用进程与服务未受任何影响
          </div>
        )}
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
      <AppHeader app={SCENARIO_APPS[2]} right={<UserRound className="h-4 w-4" />} />
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
      <AppHeader app={SCENARIO_APPS[3]} />
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
      <AppHeader app={SCENARIO_APPS[4]} right={<UserRound className="h-4 w-4" />} />
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
      <AppHeader app={SCENARIO_APPS[5]} right={<Search className="h-4 w-4" />} />
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

/* ── 对照模式：广告落地页（坏结局） ── */
function LandingScreen({ app, onBack }: { app: ScenarioApp; onBack: () => void }) {
  return (
    <div className="relative flex-1" data-testid="landing">
      <AdCreative ad={app.ad} variant="splash" />
      <div className="absolute inset-x-0 top-0 z-20 bg-rose-600/95 px-3 py-1.5 text-center text-[10px] font-bold text-white">
        ⚠ 未开启秒跳引擎：已被自动跳转至「{app.ad.brand}」落地页
      </div>
      <button
        type="button"
        data-testid="btn-back-app"
        onClick={onBack}
        className="absolute left-2.5 top-11 z-20 rounded-full bg-black/40 px-3 py-1.5 text-[10px] font-semibold text-white backdrop-blur active:scale-95"
      >
        <ChevronLeft className="mr-0.5 inline h-3 w-3" /> 返回{app.name}
      </button>
    </div>
  );
}
