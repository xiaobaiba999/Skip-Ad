"use client";

import { useState } from "react";
import { PhoneApp, AdFreq, ShieldMode } from "@/components/phone-app";
import { AdminPanel } from "@/components/admin-panel";
import { IntelPanel } from "@/components/intel-panel";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  Smartphone, RotateCcw, Gauge, PanelRight, Smartphone as PhoneIcon, ShieldAlert,
  Vibrate, BrainCircuit, ShieldCheck,
} from "lucide-react";

const FREQ_LABEL: Record<AdFreq, string> = {
  every: "每次跳转",
  half: "50% 概率",
  off: "已关闭",
};

const SHIELD_MODES: { key: ShieldMode; label: string; short: string }[] = [
  { key: "off", label: "关闭防护", short: "原生广告体验，不记录不拦截" },
  { key: "learn", label: "学习模式", short: "只观察记录，积累跳过时机与偏好" },
  { key: "block", label: "拦截模式", short: "拦截广告诱导跳转，放行主动操作" },
];

export default function Home() {
  const [appKey, setAppKey] = useState(0);
  const [refreshSignal, setRefreshSignal] = useState(0);
  const [freq, setFreq] = useState<AdFreq>("every");
  const [shieldMode, setShieldMode] = useState<ShieldMode>("block");
  const [shakeSignal, setShakeSignal] = useState(0);
  const [shakeAnim, setShakeAnim] = useState(false);
  const [mobileTab, setMobileTab] = useState<"demo" | "intel" | "admin">("demo");

  const changeFreq = (f: AdFreq) => setFreq(f);

  const doShake = () => {
    setShakeSignal((s) => s + 1);
    setShakeAnim(true);
    setTimeout(() => setShakeAnim(false), 700);
  };

  const activeShield = SHIELD_MODES.find((s) => s.key === shieldMode)!;

  return (
    <div className="flex min-h-screen flex-col bg-zinc-100/80">
      {/* 顶部说明栏 */}
      <header className="border-b border-zinc-200 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-3 lg:px-6">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-600 text-white shadow-md shadow-violet-500/25">
              <Smartphone className="h-4.5 w-4.5" />
            </span>
            <div>
              <h1 className="text-[15px] font-bold leading-tight text-zinc-900">Skip-Ad · 开屏广告与跳转广告演示系统</h1>
              <p className="text-[11px] text-zinc-500">
                广告侧 2026 全策略（自动跳转/摇一摇/全屏热区） + 防护侧精准判断引擎：区分「广告诱导跳转」与「用户主动操作」
              </p>
            </div>
          </div>
          <div className="ml-auto hidden items-center gap-2 text-[11px] text-zinc-400 sm:flex">
            <span className="rounded-full bg-violet-50 px-2.5 py-1 font-medium text-violet-700 ring-1 ring-violet-200">智能学习引擎</span>
            <span className="rounded-full bg-emerald-50 px-2.5 py-1 font-medium text-emerald-700 ring-1 ring-emerald-200">纯 CSS 素材 · PWA 可安装</span>
          </div>
        </div>
      </header>

      {/* 移动端 Tab */}
      <div className="sticky top-0 z-20 grid grid-cols-3 gap-1 border-b border-zinc-200 bg-white p-1.5 lg:hidden">
        {[
          { key: "demo" as const, label: "体验演示", icon: PhoneIcon },
          { key: "intel" as const, label: "策略情报", icon: ShieldAlert },
          { key: "admin" as const, label: "广告后台", icon: PanelRight },
        ].map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            type="button"
            onClick={() => setMobileTab(key)}
            className={cn(
              "flex items-center justify-center gap-1.5 rounded-lg py-2 text-xs font-medium transition",
              mobileTab === key ? "bg-zinc-900 text-white" : "text-zinc-500",
            )}
          >
            <Icon className="h-3.5 w-3.5" /> {label}
          </button>
        ))}
      </div>

      <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-5 p-4 lg:flex-row lg:items-start lg:p-6">
        {/* ── 左：手机模拟器 ── */}
        <section
          className={cn(
            "flex shrink-0 flex-col items-center gap-4 self-stretch rounded-2xl border border-zinc-200 bg-gradient-to-b from-zinc-50 to-zinc-100/50 p-5 lg:w-[440px]",
            mobileTab !== "demo" && "hidden lg:flex",
          )}
          aria-label="手机广告体验区"
        >
          {/* 手机壳（模拟摇晃时体感晃动） */}
          <div
            className={cn(
              "relative w-full max-w-[375px] rounded-[2.6rem] border-[10px] border-zinc-800 bg-zinc-800 shadow-2xl shadow-zinc-900/25 ring-1 ring-zinc-700",
              shakeAnim && "animate-[phone-shake_.7s_ease-in-out]",
            )}
          >
            <div className="overflow-hidden rounded-[2rem] bg-white">
              <div className="relative aspect-[9/19.2] w-full">
                <PhoneApp key={appKey} freq={freq} shieldMode={shieldMode} shakeSignal={shakeSignal} onAdEvent={() => setRefreshSignal((s) => s + 1)} />
              </div>
            </div>
          </div>

          {/* 演示控制条 */}
          <div className="flex w-full max-w-[375px] flex-col gap-3 rounded-xl bg-white p-3.5 ring-1 ring-zinc-200">
            <div className="flex items-center justify-between">
              <span className="flex items-center gap-1.5 text-xs font-semibold text-zinc-700">
                <RotateCcw className="h-3.5 w-3.5 text-violet-600" /> 重新走一遍广告流程
              </span>
              <Button size="sm" data-testid="btn-restart" className="h-7 rounded-lg bg-zinc-900 hover:bg-zinc-700" onClick={() => setAppKey((k) => k + 1)}>
                重启 App
              </Button>
            </div>

            {/* 防跳转护盾模式 */}
            <div>
              <div className="flex items-center justify-between gap-2">
                <span className="flex items-center gap-1.5 text-xs font-semibold text-zinc-700">
                  <BrainCircuit className="h-3.5 w-3.5 text-violet-600" /> 防跳转护盾
                </span>
                <div className="flex rounded-lg bg-zinc-100 p-0.5" role="group" aria-label="护盾模式">
                  {SHIELD_MODES.map((s) => (
                    <button
                      key={s.key}
                      type="button"
                      data-testid={`shield-${s.key}`}
                      onClick={() => setShieldMode(s.key)}
                      className={cn(
                        "rounded-md px-2.5 py-1 text-[11px] font-medium transition",
                        shieldMode === s.key ? "bg-white text-zinc-900 shadow-sm" : "text-zinc-400",
                      )}
                    >
                      {s.label}
                    </button>
                  ))}
                </div>
              </div>
              <p className="mt-1.5 flex items-center gap-1 text-[11px] leading-relaxed text-zinc-500">
                <ShieldCheck className="h-3 w-3 shrink-0 text-emerald-600" />
                {activeShield.short}；多次手动跳过后引擎会学习你的跳过时机，自动跳过开屏广告
              </p>
            </div>

            {/* 模拟摇晃 */}
            <div className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-1.5 text-xs font-semibold text-zinc-700">
                <Vibrate className="h-3.5 w-3.5 text-violet-600" /> 摇一摇传感器
              </span>
              <Button size="sm" data-testid="btn-shake" variant="outline" className="h-7 rounded-lg" onClick={doShake}>
                模拟摇晃
              </Button>
            </div>

            {/* 插屏频率 */}
            <div className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-1.5 text-xs font-semibold text-zinc-700">
                <Gauge className="h-3.5 w-3.5 text-violet-600" /> 跳转广告触发频率
              </span>
              <div className="flex rounded-lg bg-zinc-100 p-0.5">
                {(Object.keys(FREQ_LABEL) as AdFreq[]).map((f) => (
                  <button
                    key={f}
                    type="button"
                    onClick={() => changeFreq(f)}
                    className={cn(
                      "rounded-md px-2.5 py-1 text-[11px] font-medium transition",
                      freq === f ? "bg-white text-zinc-900 shadow-sm" : "text-zinc-400",
                    )}
                  >
                    {FREQ_LABEL[f]}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* 流程说明 */}
          <ol className="w-full max-w-[375px] space-y-1.5 text-[11px] leading-relaxed text-zinc-500">
            {[
              "「重启 App」后按权重抽取开屏广告：2026 策略（倒计时自动跳转 / 摇一摇 / 全屏隐藏热区 / 标准）随机呈现",
              "摇一摇广告出现时，点「模拟摇晃」（或真机摇动手机）触发传感器跳转，拦截模式下会被判定为广告行为",
              "拦截模式会采集点击的按压时长 / 轨迹 / 反应时间：误触（<40ms 瞬移点击）拦截，真人触摸（带轨迹抖动）放行",
              "落地页含「二跳」演示：倒计时自动唤起外部应用会被拦截；手动点「立即打开」视为用户主动操作放行",
              "误判可点 Toast 上的「这是我要的操作」纠正；右侧后台实时展示学习画像（精准度 / 自动跳过时机 / 主题偏好）",
            ].map((t, i) => (
              <li key={i} className="flex gap-2">
                <span className="mt-px flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-violet-100 text-[10px] font-bold text-violet-600">{i + 1}</span>
                {t}
              </li>
            ))}
          </ol>
        </section>

        {/* ── 右：广告后台（桌面） / 策略情报（移动端独立页） ── */}
        <section
          className={cn(
            "min-h-0 min-w-0 flex-1 rounded-2xl border border-zinc-200 bg-white lg:sticky lg:top-4 lg:h-[calc(100vh-2rem)] lg:self-start",
            mobileTab !== "admin" && "hidden lg:block",
          )}
          aria-label="广告投放后台"
        >
          <AdminPanel refreshSignal={refreshSignal} />
        </section>
      </main>

      {/* ── 全宽：策略情报站（桌面端在下方展示，移动端独立 Tab） ── */}
      <section
        className={cn(
          "mx-auto w-full max-w-7xl scroll-mt-16 px-4 pb-6 lg:px-6",
          mobileTab !== "intel" && "hidden lg:block",
        )}
        id="intel"
        aria-label="策略情报站"
      >
        <div className="rounded-2xl border border-zinc-200 bg-white">
          <IntelPanel />
        </div>
      </section>

      <footer className="mt-auto border-t border-zinc-200 bg-white/60 py-3 text-center text-[11px] leading-relaxed text-zinc-400">
        Skip-Ad · 开屏/跳转广告演示 + 精准判断引擎 · GitHub：
        <a href="https://github.com/xiaobaiba999/Skip-Ad" target="_blank" rel="noreferrer" className="mx-0.5 text-violet-600 hover:underline">
          xiaobaiba999/Skip-Ad
        </a>
        · 真机版「开屏卫士」APK（无障碍智能跳过 + 习惯学习）见 download/开屏卫士APK
      </footer>
    </div>
  );
}
