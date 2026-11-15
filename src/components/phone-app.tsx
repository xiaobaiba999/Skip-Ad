"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AdData } from "@/lib/types";
import { LANDING_META, trackAd } from "@/lib/types";
import { SplashAd } from "@/components/splash-ad";
import { InterstitialAd } from "@/components/interstitial-ad";
import { AdCreative } from "@/components/ad-creative";
import {
  judgeJump, type LearnSummary, type JudgeReason, type JumpSignals, type JumpTrigger,
} from "@/lib/judge";
import {
  Sparkles, Search, Bell, Telescope, Smartphone, Bike, Bot, Car,
  Home, Compass, User, ChevronLeft, X, Signal, Wifi, BatteryFull, Zap, ShieldCheck,
} from "lucide-react";

export type AdFreq = "every" | "half" | "off";
export type ShieldMode = "off" | "learn" | "block";

/* ───────── App 品牌元素 ───────── */
export function NebulaLogo({ className = "h-10 w-10" }: { className?: string }) {
  return (
    <div className={`flex items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500 to-fuchsia-600 shadow-lg shadow-violet-500/25 ${className}`}>
      <Sparkles className="h-1/2 w-1/2 text-white" />
    </div>
  );
}

/* ───────── 模拟资讯内容 ───────── */
const ARTICLES = [
  {
    id: "a1",
    cat: "天文",
    title: "韦伯望远镜确认发现迄今最古老星系，诞生于宇宙大爆炸后 2.9 亿年",
    source: "星云科技",
    time: "12 分钟前",
    reads: "128 万",
    icon: Telescope,
    thumb: "from-violet-500 to-fuchsia-600",
    body: [
      " NASA 官方宣布，詹姆斯·韦伯太空望远镜确认了一例新的最古老星系记录。该星系的光线出发于宇宙大爆炸后仅 2.9 亿年，为星系形成理论提供了关键观测证据。",
      "研究团队利用韦伯望远镜的近红外光谱仪对目标进行了长达 28 小时的曝光，通过红移测量确认了它的距离。此前由哈勃保持的纪录约为大爆炸后 4 亿年。",
      "天文学家表示，这一类星系的亮度和质量都超出了早期宇宙模型的预期，意味着早期星系的 assembly 效率可能远高于理论预测。相关论文已提交《自然·天文》。",
    ],
  },
  {
    id: "a2",
    cat: "数码",
    title: "折叠屏手机单季出货量首次超越平板电脑，供应链消息称铰链成本再降 30%",
    source: "极客观察",
    time: "34 分钟前",
    reads: "86 万",
    icon: Smartphone,
    thumb: "from-rose-500 to-orange-500",
    body: [
      "市场研究机构 Counterpoint 发布的最新报告显示，本季度全球折叠屏手机出货量首次超越平板电脑品类，成为移动设备市场增长最快的细分领域。",
      "报告指出，铰链与柔性面板的成本下降是主要驱动力。供应链消息称，新一代水滴形铰链的物料成本较两年前下降了约 30%，折叠屏旗舰的起售价已下探至 5000 元档位。",
      "分析师预计，随着 UTG 超薄玻璃产能释放，明年折叠屏机型将覆盖 2000-3000 元价位段，渗透率有望突破两位数。",
    ],
  },
  {
    id: "a3",
    cat: "生活",
    title: "本周末城市马拉松开跑，三条地铁线路将提前至 5:30 运营",
    source: "城市速递",
    time: "1 小时前",
    reads: "42 万",
    icon: Bike,
    thumb: "from-emerald-500 to-teal-600",
    body: [
      "本周日上午 7:30，一年一度的城市马拉松将鸣枪开跑，全程 42.195 公里，途经滨江大道、老城商圈等 12 个地标路段。",
      "为方便选手和观众出行，地铁 2 号线、5 号线、10 号线将于当日提前至 5:30 开始运营，涉及赛事路段将分时段实施交通管制。",
      "组委会提醒，参赛者需在周六 18:00 前完成物资领取，比赛当天凭号码布可免费乘坐公共交通。",
    ],
  },
  {
    id: "a4",
    cat: "AI",
    title: "AI 编程助手效率实测：真实工程任务提效 38%，但调试环节仍需人工把关",
    source: "星云科技",
    time: "2 小时前",
    reads: "95 万",
    icon: Bot,
    thumb: "from-purple-500 to-violet-600",
    body: [
      "一项覆盖 2000 名开发者的实测研究显示，在真实工程任务中使用 AI 编程助手，整体交付效率平均提升 38%，其中样板代码编写环节提效最为显著。",
      "不过研究也发现，AI 生成代码在边界条件处理和安全漏洞方面仍有短板，约 17% 的生成结果需要人工重写关键逻辑。",
      "研究团队建议将 AI 助手定位为\"副驾驶\"角色：让人负责架构与审查，让 AI 负责实现与测试用例生成，组合效果最佳。",
    ],
  },
  {
    id: "a5",
    cat: "汽车",
    title: "新能源汽车 10 月销量盘点：插混增速翻倍，30 万元档成为新战场",
    source: "车市前线",
    time: "3 小时前",
    reads: "67 万",
    icon: Car,
    thumb: "from-cyan-500 to-emerald-500",
    body: [
      "乘联会最新数据显示，10 月新能源汽车零售渗透率再创历史新高，其中插电混动车型同比增速接近 100%，成为增长最快的动力形式。",
      "从价格结构看，30 万元档高端市场成为新车投放最密集的区间，本季度共有 9 款重磅车型上市，搭载城市领航辅助驾驶的车型占比超过六成。",
      "业内分析认为，随着电池成本回落与充电网络完善，明年插混与纯电的市场份额差距将进一步收窄。",
    ],
  },
];

type Stage = "launch" | "splash" | "main" | "adpage" | "external";
type Tab = "home" | "discover" | "me";

/* 判断引擎 Toast */
interface ToastItem {
  id: number;
  type: "block" | "allow" | "info";
  headline: string;
  score?: number;
  reasons?: JudgeReason[];
  correction?: () => void;
}

export function PhoneApp({
  freq,
  shieldMode = "off",
  shakeSignal = 0,
  onAdEvent,
}: {
  freq: AdFreq;
  shieldMode?: ShieldMode;
  shakeSignal?: number;
  onAdEvent?: () => void;
}) {
  const [stage, setStage] = useState<Stage>("launch");
  const [tab, setTab] = useState<Tab>("home");
  const [splashAd, setSplashAd] = useState<AdData | null>(null);
  const [interstitial, setInterstitial] = useState<AdData | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [adLanding, setAdLanding] = useState<AdData | null>(null);
  const [landingAutoDone, setLandingAutoDone] = useState(false);
  const [profile, setProfile] = useState<LearnSummary | null>(null);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const freqRef = useRef(freq);
  useEffect(() => { freqRef.current = freq; }, [freq]);
  const shieldRef = useRef<ShieldMode>(shieldMode);
  useEffect(() => { shieldRef.current = shieldMode; }, [shieldMode]);
  const profileRef = useRef<LearnSummary | null>(null);
  useEffect(() => { profileRef.current = profile; }, [profile]);
  const landingEnterRef = useRef(0);
  const toastId = useRef(0);
  const profileTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const detail = ARTICLES.find((a) => a.id === detailId) ?? null;
  const mode = shieldMode;

  /* ── 学习画像：拉取 + 防抖刷新 ── */
  const refreshProfile = useCallback(async () => {
    try {
      const r = await fetch("/api/learn");
      if (r.ok) setProfile(await r.json());
    } catch { /* 静默 */ }
  }, []);
  // 初次画像：fetch 回调中 setState（异步回调，非 effect 同步更新）
  useEffect(() => {
    let alive = true;
    fetch("/api/learn")
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => { if (alive && d) setProfile(d); })
      .catch(() => {});
    return () => { alive = false; };
  }, []);

  const scheduleProfileRefresh = useCallback(() => {
    if (profileTimer.current) clearTimeout(profileTimer.current);
    profileTimer.current = setTimeout(() => refreshProfile(), 700);
  }, [refreshProfile]);

  /* ── 事件埋点（关闭防护时不记录） ── */
  const logEvent = useCallback((payload: Record<string, unknown>) => {
    if (shieldRef.current === "off") return;
    fetch("/api/events", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    }).then(() => scheduleProfileRefresh()).catch(() => {});
  }, [scheduleProfileRefresh]);

  const pushToast = useCallback((t: Omit<ToastItem, "id">) => {
    const id = ++toastId.current;
    setToasts((cur) => [...cur.slice(-2), { ...t, id }]);
    setTimeout(() => setToasts((cur) => cur.filter((x) => x.id !== id)), 4800);
  }, []);

  /* ── 中央判断：广告诱导跳转 vs 用户主动操作 ── */
  const judge = useCallback(
    (ad: AdData | null, trigger: JumpTrigger, signals: JumpSignals = {}): "blocked" | "allowed" => {
      if (shieldRef.current === "off") return "allowed";
      const j = judgeJump(trigger, signals, profileRef.current?.rules, ad?.theme);
      const m = shieldRef.current;
      const blockedNow = j.isAd && m === "block";
      const action = blockedNow ? "blocked" : m === "learn" && j.isAd ? "would-block" : "allowed";
      logEvent({
        kind: "decision", adId: ad?.id, adName: ad?.name, theme: ad?.theme,
        trigger, action, score: j.score, reasons: j.reasons,
        pressMs: signals.pressMs, movePx: signals.movePx, reactionMs: signals.reactionMs,
      });
      if (blockedNow) {
        pushToast({
          type: "block", headline: `广告诱导跳转（${trigger === "timer" ? "倒计时自动跳转" : trigger === "shake" ? "传感器触发" : trigger === "hotzone" ? "隐藏热区点击" : trigger === "landing-auto" ? "二跳自动唤起" : "行为指纹异常"}）`,
          score: j.score, reasons: j.reasons,
          correction: () => {
            logEvent({
              kind: "correction", adId: ad?.id, adName: ad?.name, theme: ad?.theme,
              trigger, score: j.score, reasons: j.reasons,
              pressMs: signals.pressMs, movePx: signals.movePx, reactionMs: signals.reactionMs,
            });
            pushToast({ type: "info", headline: "已纠正：引擎将放宽同类快速点击判定" });
          },
        });
      } else if (m === "learn" && j.isAd) {
        pushToast({ type: "info", headline: "学习模式：已记录此类跳转（拦截模式将自动拦截）" });
      } else if (!j.isAd && (trigger === "tap" || trigger === "landing-manual")) {
        pushToast({ type: "allow", headline: `用户主动操作（按压/轨迹符合真人特征）`, score: j.score });
      }
      return blockedNow ? "blocked" : "allowed";
    },
    [logEvent, pushToast],
  );

  /* ── 启动流程：logo 动画 → 拉取开屏广告 ── */
  useEffect(() => {
    if (stage !== "launch") return;
    let cancelled = false;
    (async () => {
      await new Promise((r) => setTimeout(r, 1400));
      if (cancelled) return;
      try {
        const res = await fetch("/api/ads?type=splash&pick=1");
        const { ad } = await res.json();
        if (cancelled) return;
        if (ad) {
          setSplashAd(ad as AdData);
          setStage("splash");
        } else {
          setStage("main");
        }
      } catch {
        if (!cancelled) setStage("main");
      }
    })();
    return () => { cancelled = true; };
  }, [stage]);

  const enterAdPage = useCallback(
    (ad: AdData) => {
      setAdLanding(ad);
      setInterstitial(null); // 点击广告即消耗掉本次插屏，落地页关闭后不再残留
      setLandingAutoDone(false);
      landingEnterRef.current = Date.now();
      setStage("adpage");
      onAdEvent?.();
    },
    [onAdEvent],
  );

  const closeLanding = useCallback(() => {
    const dwell = Date.now() - landingEnterRef.current;
    if (shieldRef.current !== "off" && adLanding) {
      if (dwell < 2500) logEvent({ kind: "quickback", theme: adLanding.theme, adName: adLanding.name, dwellMs: dwell });
      else if (dwell > 6000) logEvent({ kind: "engage", theme: adLanding.theme, adName: adLanding.name, dwellMs: dwell });
    }
    setStage("main");
    onAdEvent?.();
  }, [adLanding, logEvent, onAdEvent]);

  /* ── 打开文章：按频率触发插屏广告（内容区导航 = 用户主动操作） ── */
  const openArticle = useCallback(
    async (id: string) => {
      judge(null, "content", {});
      const f = freqRef.current;
      const hit = f === "every" || (f === "half" && Math.random() < 0.5);
      if (f === "off" || !hit) {
        setDetailId(id);
        setTab("home");
        return;
      }
      try {
        const res = await fetch("/api/ads?type=interstitial&pick=1");
        const { ad } = await res.json();
        if (!ad) {
          setDetailId(id);
          return;
        }
        setInterstitial((ad as AdData));
        setDetailId(id); // 预设目标：关闭广告后进入
      } catch {
        setDetailId(id);
      }
    },
    [judge],
  );

  return (
    <div className="relative flex h-full w-full flex-col overflow-hidden bg-zinc-50 select-none">
      {/* 状态栏 */}
      <StatusBar />

      {/* ── 阶段一：启动动画 ── */}
      {stage === "launch" && (
        <div className="flex flex-1 flex-col items-center justify-center gap-4 bg-white">
          <NebulaLogo className="h-20 w-20 animate-[app-launch_1.4s_ease-out]" />
          <div className="text-sm font-semibold tracking-widest text-zinc-400">星云资讯</div>
        </div>
      )}

      {/* ── 阶段二：开屏广告（判断引擎接管） ── */}
      {stage === "splash" && splashAd && (
        <SplashAd
          ad={splashAd}
          duration={5}
          autoSkipAfterMs={mode === "block" ? profile?.rules.autoSkipDelayMs ?? null : null}
          shakeSignal={shakeSignal}
          onJump={(tr, sig) => {
            const v = judge(splashAd, tr, sig);
            if (v === "blocked") { setStage("main"); onAdEvent?.(); }
            else enterAdPage(splashAd);
          }}
          onSkip={(how, ms) => {
            if (mode !== "off") {
              logEvent({ kind: "skip", adName: splashAd.name, theme: splashAd.theme, trigger: how === "engine" ? "engine" : "manual", dwellMs: Math.round(ms) });
              if (how === "engine") pushToast({ type: "info", headline: `已按你的使用习惯自动跳过（学习均值 ${(ms / 1000).toFixed(1)}s）` });
            }
            setStage("main");
            onAdEvent?.();
          }}
          onEnd={() => { setStage("main"); onAdEvent?.(); }}
        />
      )}

      {/* ── 阶段三：主界面 ── */}
      {stage === "main" && (
        detail ? <ArticleDetail article={detail} onBack={() => setDetailId(null)} /> : <MainTabs tab={tab} setTab={setTab} onOpenArticle={openArticle} />
      )}

      {/* ── 阶段四：广告落地页（含二跳防护） ── */}
      {stage === "adpage" && adLanding && (
        <AdLanding
          ad={adLanding}
          shieldOn={mode === "block"}
          autoDone={landingAutoDone}
          onAuto={() => judge(adLanding, "landing-auto")}
          onManual={() => judge(adLanding, "landing-manual")}
          onCancel={() => logEvent({ kind: "cancel", theme: adLanding.theme, adName: adLanding.name })}
          onExternal={() => { setLandingAutoDone(true); setStage("external"); onAdEvent?.(); }}
          onClose={closeLanding}
        />
      )}

      {/* ── 阶段五：外部应用（URL Scheme 二跳演示） ── */}
      {stage === "external" && adLanding && (
        <ExternalApp ad={adLanding} onBack={() => setStage("adpage")} />
      )}

      {/* ── 插屏广告浮层（拦截文章跳转） ── */}
      {interstitial && (
        <InterstitialAd
          ad={interstitial}
          forceSeconds={3}
          onClose={() => { setInterstitial(null); onAdEvent?.(); }}
          onJump={(tr, sig) => {
            const v = judge(interstitial, tr, sig);
            if (v === "blocked") { setInterstitial(null); onAdEvent?.(); }
            else enterAdPage(interstitial);
          }}
        />
      )}

      {/* ── 判断引擎 Toast ── */}
      <div className="pointer-events-none absolute inset-x-2 top-10 z-[70] space-y-1.5">
        {toasts.map((t) => (
          <div
            key={t.id}
            data-testid={`toast-${t.type}`}
            className={`pointer-events-auto animate-[toast-in_.25s_ease-out] rounded-xl p-2.5 text-[11px] leading-snug text-white shadow-lg backdrop-blur ${
              t.type === "block" ? "bg-rose-600/95 ring-1 ring-rose-300/40" : t.type === "allow" ? "bg-emerald-600/95 ring-1 ring-emerald-300/40" : "bg-zinc-800/95 ring-1 ring-white/15"
            }`}
          >
            <div className="flex items-center gap-1.5 font-bold">
              <ShieldCheck className="h-3.5 w-3.5 shrink-0" />
              <span>{t.type === "block" ? "已拦截" : t.type === "allow" ? "放行" : "引擎"}</span>
              <span>· {t.headline}</span>
              {t.score !== undefined && <span className="ml-auto shrink-0 tabular-nums opacity-80">{Math.round(t.score)}分</span>}
            </div>
            {t.reasons && t.reasons.length > 0 && (
              <ul className="mt-1 space-y-0.5 opacity-90">
                {t.reasons.slice(0, 2).map((r, i) => (
                  <li key={i}>· {r.label}（{r.weight > 0 ? "+" : ""}{r.weight}）</li>
                ))}
              </ul>
            )}
            {t.correction && (
              <button
                type="button"
                data-testid="toast-correct"
                onClick={(e) => { e.stopPropagation(); t.correction?.(); setToasts((cur) => cur.filter((x) => x.id !== t.id)); }}
                className="mt-1.5 w-full rounded-lg bg-white/15 py-1 text-[10px] font-bold ring-1 ring-white/25 active:scale-95"
              >
                误判？这是我要的操作
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/* ───────── 状态栏（hydration 安全的实时时间） ───────── */
function StatusBar() {
  const [time, setTime] = useState("--:--");
  useEffect(() => {
    const tick = () => setTime(new Date().toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }));
    tick();
    const t = setInterval(tick, 15000);
    return () => clearInterval(t);
  }, []);
  return (
    <div className="flex h-9 shrink-0 items-center justify-between bg-inherit px-5 text-[11px] font-semibold text-zinc-800 [&~*]:bg-inherit">
      <span className="tabular-nums">{time}</span>
      <span className="absolute left-1/2 top-2 h-5 w-24 -translate-x-1/2 rounded-full bg-black" aria-hidden />
      <span className="flex items-center gap-1">
        <Signal className="h-3 w-3" /> <Wifi className="h-3 w-3" /> <BatteryFull className="h-3.5 w-3.5" />
      </span>
    </div>
  );
}

/* ───────── 主界面 Tab ───────── */
function MainTabs({ tab, setTab, onOpenArticle }: { tab: Tab; setTab: (t: Tab) => void; onOpenArticle: (id: string) => void }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* 顶部导航 */}
      <div className="flex items-center gap-2.5 border-b border-zinc-100 bg-white px-4 pb-3 pt-1">
        <NebulaLogo className="h-7 w-7 rounded-lg" />
        <span className="text-base font-bold text-zinc-900">星云资讯</span>
        <div className="ml-auto flex items-center gap-3 text-zinc-400">
          <Search className="h-4.5 w-4.5" />
          <span className="relative">
            <Bell className="h-4.5 w-4.5" />
            <span className="absolute -right-0.5 -top-0.5 h-1.5 w-1.5 rounded-full bg-rose-500" />
          </span>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        {tab === "home" && <HomeFeed onOpenArticle={onOpenArticle} />}
        {tab === "discover" && <DiscoverPane />}
        {tab === "me" && <MePane />}
      </div>

      {/* 底部 Tab 栏 */}
      <nav className="flex shrink-0 items-stretch justify-around border-t border-zinc-100 bg-white pb-1 pt-1.5" aria-label="底部导航">
        {[
          { key: "home" as Tab, label: "首页", icon: Home },
          { key: "discover" as Tab, label: "发现", icon: Compass },
          { key: "me" as Tab, label: "我的", icon: User },
        ].map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={`flex min-w-14 flex-col items-center gap-0.5 rounded-lg px-3 py-1 text-[10px] transition ${
              tab === key ? "font-bold text-violet-600" : "text-zinc-400"
            }`}
            aria-label={label}
            aria-current={tab === key}
          >
            <Icon className={`h-5 w-5 ${tab === key ? "stroke-[2.4]" : ""}`} />
            {label}
          </button>
        ))}
      </nav>
    </div>
  );
}

/* ───────── 首页信息流 ───────── */
function HomeFeed({ onOpenArticle }: { onOpenArticle: (id: string) => void }) {
  return (
    <div className="space-y-3 p-3">
      <div className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-violet-50 to-fuchsia-50 px-3 py-2.5 text-xs text-violet-500 ring-1 ring-violet-100">
        <Zap className="h-3.5 w-3.5" />
        点击任意文章将触发「跳转广告」，关闭后继续阅读
      </div>
      {ARTICLES.map((a) => (
        <button
          key={a.id}
          type="button"
          onClick={() => onOpenArticle(a.id)}
          className="flex w-full gap-3 rounded-xl bg-white p-3 text-left shadow-sm ring-1 ring-zinc-100 transition active:scale-[0.985] active:bg-zinc-50"
          aria-label={`打开文章：${a.title}`}
        >
          <div className="min-w-0 flex-1">
            <span className="mb-1.5 inline-block rounded bg-zinc-100 px-1.5 py-0.5 text-[10px] font-medium text-zinc-500">{a.cat}</span>
            <div className="line-clamp-2 text-[13px] font-semibold leading-snug text-zinc-900">{a.title}</div>
            <div className="mt-1.5 flex items-center gap-2 whitespace-nowrap text-[10px] text-zinc-400">
              <span className="shrink-0 font-medium text-zinc-500">{a.source}</span>
              <span className="shrink-0">{a.time}</span>
              <span className="shrink-0">{a.reads} 阅读</span>
            </div>
          </div>
          <div className={`flex h-16 w-20 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br ${a.thumb}`}>
            <a.icon className="h-7 w-7 text-white/90" strokeWidth={1.6} />
          </div>
        </button>
      ))}
    </div>
  );
}

/* ───────── 发现 / 我的（简洁示意） ───────── */
function DiscoverPane() {
  const topics = ["天文观测", "数码评测", "城市生活", "AI 前沿", "新能源车", "科学健康"];
  return (
    <div className="grid grid-cols-2 gap-3 p-3">
      {topics.map((t, i) => (
        <div key={t} className={`flex h-24 flex-col justify-end rounded-xl p-3 text-white ${["bg-gradient-to-br from-violet-500 to-purple-600", "bg-gradient-to-br from-rose-500 to-orange-500", "bg-gradient-to-br from-emerald-500 to-teal-600", "bg-gradient-to-br from-fuchsia-500 to-purple-600", "bg-gradient-to-br from-cyan-500 to-emerald-500", "bg-gradient-to-br from-amber-500 to-orange-600"][i]}`}>
          <div className="text-sm font-bold">{t}</div>
          <div className="text-[10px] text-white/80">{(i + 3) * 2.4} 万人正在讨论</div>
        </div>
      ))}
    </div>
  );
}

function MePane() {
  return (
    <div className="space-y-3 p-4">
      <div className="flex items-center gap-3 rounded-2xl bg-white p-4 ring-1 ring-zinc-100">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-gradient-to-br from-violet-500 to-fuchsia-600 text-lg font-bold text-white">星</div>
        <div>
          <div className="text-sm font-bold text-zinc-900">星尘旅人</div>
          <div className="text-xs text-zinc-400">Lv.6 · 已连续签到 42 天</div>
        </div>
      </div>
      {[
        ["我的收藏", "28 篇文章"],
        ["浏览历史", "今天看了 12 篇"],
        ["夜间模式", "跟随系统"],
        ["设置", "账号与隐私"],
      ].map(([t, d]) => (
        <div key={t} className="flex items-center justify-between rounded-xl bg-white px-4 py-3 ring-1 ring-zinc-100">
          <span className="text-[13px] font-medium text-zinc-700">{t}</span>
          <span className="text-xs text-zinc-400">{d}</span>
        </div>
      ))}
    </div>
  );
}

/* ───────── 文章详情 ───────── */
function ArticleDetail({ article, onBack }: { article: (typeof ARTICLES)[number]; onBack: () => void }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex shrink-0 items-center gap-2 border-b border-zinc-100 bg-white px-2 py-2">
        <button type="button" onClick={onBack} className="rounded-full p-1.5 text-zinc-600 active:bg-zinc-100" aria-label="返回首页">
          <ChevronLeft className="h-5 w-5" />
        </button>
        <span className="line-clamp-1 text-sm font-semibold text-zinc-800">{article.cat} · 详情</span>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto bg-white p-4">
        <h1 className="text-lg font-bold leading-snug text-zinc-900">{article.title}</h1>
        <div className="mt-2 flex items-center gap-2 border-b border-zinc-100 pb-3 text-[11px] text-zinc-400">
          <span className="font-medium text-zinc-600">{article.source}</span>
          <span>{article.time}</span>
          <span>{article.reads} 阅读</span>
        </div>
        {article.body.map((p, i) => (
          <p key={i} className="mt-3 text-[13px] leading-6 text-zinc-700">{p}</p>
        ))}
        <div className="mt-5 rounded-xl bg-zinc-50 p-3 text-center text-[11px] text-zinc-400">已阅读完毕 · 返回首页继续浏览</div>
      </div>
    </div>
  );
}

/* ───────── 广告落地页（模拟 App 内 WebView + 二跳防护演示） ───────── */
function AdLanding({
  ad, shieldOn, autoDone, onAuto, onManual, onCancel, onExternal, onClose,
}: {
  ad: AdData;
  shieldOn: boolean;
  autoDone: boolean;
  onAuto: () => "blocked" | "allowed";
  onManual: () => "blocked" | "allowed";
  onCancel: () => void;
  onExternal: () => void;
  onClose: () => void;
}) {
  const meta = LANDING_META[ad.linkUrl] ?? LANDING_META.default;
  const [toast, setToast] = useState(false);
  const [sec, setSec] = useState(3);
  const [secState, setSecState] = useState<"run" | "blocked" | "cancelled">("run");

  useEffect(() => {
    if (autoDone || secState !== "run") return;
    if (sec === 0) {
      // 异步回调中处理，避免 effect 内同步 setState
      const z = setTimeout(() => {
        const v = onAuto(); // 二跳自动唤起 → 判断引擎
        if (v === "blocked") setSecState("blocked");
        else onExternal();
      }, 0);
      return () => clearTimeout(z);
    }
    const t = setTimeout(() => setSec(sec - 1), 1000);
    return () => clearTimeout(t);
  }, [sec, secState, autoDone]);

  const effective: "run" | "blocked" | "cancelled" | "done" = autoDone ? (shieldOn ? "blocked" : "done") : secState;

  return (
    <div className="absolute inset-0 z-50 flex flex-col bg-white" data-testid="ad-landing">
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-zinc-100 px-3">
        <div className="flex items-center gap-2 text-xs text-zinc-500">
          <span className="flex h-5 w-5 items-center justify-center rounded bg-zinc-200 text-[10px] font-bold text-zinc-600">G</span>
          {ad.linkUrl}.ad-landing.example
        </div>
        <button type="button" onClick={onClose} data-testid="landing-close" className="rounded-full p-1.5 text-zinc-600 active:bg-zinc-100" aria-label="关闭落地页">
          <X className="h-4.5 w-4.5" />
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="flex aspect-[4/3] w-full flex-col justify-end p-6">
          <AdCreative ad={ad} variant="card" />
        </div>
        <div className="p-5 pt-0 text-center">
          <div className="text-lg font-bold text-zinc-900">{meta.brand}</div>
          <div className="mt-1 text-sm text-zinc-500">{meta.desc}</div>
          <p className="mt-4 text-xs leading-6 text-zinc-400">
            这是广告主落地页演示。真实场景中，此处为广告主的 H5 活动页或应用商店下载页，
            用户停留与转化数据将回传广告平台用于效果归因。
          </p>
          <button
            type="button"
            onClick={() => { setToast(true); setTimeout(() => setToast(false), 1800); trackAd(ad.id, "click"); }}
            className="mt-5 w-full rounded-xl bg-gradient-to-r from-rose-500 to-orange-500 py-3 text-sm font-bold text-white shadow-lg shadow-rose-500/30 active:scale-[0.98]"
          >
            {ad.ctaText}
          </button>
          {toast && (
            <div className="mt-3 rounded-lg bg-zinc-100 px-3 py-2 text-xs text-zinc-500">演示环境：此处将跳转广告主页面</div>
          )}

          {/* 二跳演示：自动唤起外部应用（URL Scheme） */}
          <div className="mx-0 mt-4 rounded-xl border border-zinc-200 p-3.5 text-left" data-testid="second-jump">
            {effective === "run" && (
              <>
                <div className="flex items-center gap-2 text-xs font-semibold text-zinc-700">
                  <span className="h-2 w-2 animate-pulse rounded-full bg-amber-500" />
                  {sec}s 后将自动唤起外部应用「{meta.brand}」
                </div>
                <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-zinc-100">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-amber-400 to-rose-500 transition-all duration-1000 ease-linear"
                    style={{ width: `${(sec / 3) * 100}%` }}
                  />
                </div>
                <div className="mt-2.5 flex gap-2">
                  <button
                    type="button"
                    data-testid="landing-open"
                    onClick={() => { const v = onManual(); if (v === "blocked") setSecState("blocked"); else onExternal(); }}
                    className="flex-1 rounded-lg bg-zinc-900 py-2 text-xs font-bold text-white active:scale-[0.98]"
                  >
                    立即打开
                  </button>
                  <button
                    type="button"
                    data-testid="landing-cancel"
                    onClick={() => { onCancel(); setSecState("cancelled"); }}
                    className="flex-1 rounded-lg bg-zinc-100 py-2 text-xs font-bold text-zinc-600 active:scale-[0.98]"
                  >
                    取消
                  </button>
                </div>
                <p className="mt-2 text-[10px] leading-relaxed text-zinc-400">
                  模拟「二跳」：落地页倒计时结束后通过 URL Scheme 唤起外部 App。拦截模式下，自动唤起被判定为广告行为而拦截；手动点击「立即打开」则视为用户主动操作放行。
                </p>
              </>
            )}
            {effective === "blocked" && (
              <div className="flex items-start gap-2 text-xs text-zinc-700">
                <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-rose-100 text-[11px] font-bold text-rose-600">停</span>
                <div>
                  <b>已拦截：自动唤起外部应用（二跳）</b>
                  <div className="mt-1 text-[10px] leading-relaxed text-zinc-400">
                    判定依据：倒计时自动触发，无用户输入（澎湃 2026-11 实测的“连环跳转”形态）。系统级防护还可收紧「关联启动 / 打开其他应用」权限，在系统层截断跳转链。
                  </div>
                </div>
              </div>
            )}
            {effective === "cancelled" && (
              <div className="text-xs text-zinc-500">已按你的要求取消自动唤起（记为负向反馈：此类跳转将更倾向拦截）</div>
            )}
            {effective === "done" && (
              <div className="text-xs text-zinc-500">已自动唤起外部应用（当前未开启拦截；学习模式下已记录）</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ───────── 外部应用（URL Scheme 二跳目标页演示） ───────── */
function ExternalApp({ ad, onBack }: { ad: AdData; onBack: () => void }) {
  const meta = LANDING_META[ad.linkUrl] ?? LANDING_META.default;
  return (
    <div className="absolute inset-0 z-[60] flex flex-col bg-white" data-testid="external-app">
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-zinc-100 bg-emerald-50 px-3">
        <div className="flex items-center gap-2 text-xs font-medium text-emerald-700">
          <span className="flex h-5 w-5 items-center justify-center rounded bg-emerald-600 text-[10px] font-bold text-white">S</span>
          {meta.brand} · 外部应用（URL Scheme 唤起）
        </div>
        <button
          type="button"
          onClick={onBack}
          data-testid="external-back"
          className="rounded-full bg-white px-3 py-1 text-[11px] font-medium text-zinc-700 ring-1 ring-zinc-200 active:scale-95"
        >
          返回演示
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-4">
        <div className="rounded-xl bg-zinc-50 p-3 text-[11px] leading-relaxed text-zinc-500">
          真实场景：广告落地页通过 URL Scheme 直接唤起外部 App（二跳），用户被动离开当前应用。
          系统级防护可收紧「关联启动 / 打开其他应用」权限，在系统层截断跳转链。
        </div>
        <div className="mt-3 grid grid-cols-2 gap-3">
          {["限时秒杀", "新人礼包", "品牌闪购", "以旧换新"].map((t, i) => (
            <div key={t} className="rounded-xl bg-white p-3 ring-1 ring-zinc-100">
              <div className={`h-16 rounded-lg bg-gradient-to-br ${["from-rose-500 to-orange-500", "from-violet-500 to-fuchsia-600", "from-emerald-500 to-teal-600", "from-amber-500 to-orange-600"][i]}`} />
              <div className="mt-2 text-xs font-bold text-zinc-800">{t}</div>
              <div className="text-[10px] text-zinc-400">{meta.brand} · 演示商品模块</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
