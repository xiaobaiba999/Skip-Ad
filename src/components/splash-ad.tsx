"use client";

import { useEffect, useRef, useState } from "react";
import { AdData, trackAd } from "@/lib/types";
import { AdCreative } from "@/components/ad-creative";
import { NebulaLogo } from "@/components/phone-app";
import { usePointerTracker } from "@/lib/pointer-tracker";
import type { JumpSignals, JumpTrigger } from "@/lib/judge";

/**
 * 开屏广告（2026 策略版）
 * - standard：倒计时结束进入首页；点击素材 → 判断引擎裁决
 * - timer   ：倒计时归零「自动跳转」落地页（澎湃 2026-11 实测的激进形态）
 * - shake   ：加速度传感器触发跳转（真机 devicemotion + 工具栏「模拟摇晃」）
 * - anyclick：全屏隐藏热区，素材之外点击即跳转（误触设计）
 * 行为指纹（按压/轨迹/反应时间）由 usePointerTracker 采集，交给判断引擎。
 */
export function SplashAd({
  ad,
  duration,
  autoSkipAfterMs = null,
  shakeSignal = 0,
  onJump,
  onSkip,
  onEnd,
}: {
  ad: AdData;
  duration: number;
  autoSkipAfterMs?: number | null;
  shakeSignal?: number;
  onJump: (trigger: JumpTrigger, signals: JumpSignals) => void;
  onSkip: (how: "manual" | "engine", elapsedMs: number) => void;
  onEnd: () => void;
}) {
  const [count, setCount] = useState(duration);
  const mountTs = useRef(Date.now());
  const doneRef = useRef(false);
  const tracker = usePointerTracker();
  const strategy = ad.strategy ?? "standard";
  const once = (fn: () => void) => {
    if (doneRef.current) return;
    doneRef.current = true;
    fn();
  };

  // 曝光埋点
  useEffect(() => {
    trackAd(ad.id, "view");
  }, [ad.id]);

  // 倒计时：归零行为按策略分流（沿用 effect 内回调，避免渲染期更新）
  useEffect(() => {
    if (count === 0) {
      if (strategy === "timer") once(() => onJump("timer", {}));
      else once(() => onEnd());
      return;
    }
    const t = setTimeout(() => setCount(count - 1), 1000);
    return () => clearTimeout(t);
  }, [count, ad.id]);

  // 学习到的自动跳过时机（GKD 式：到点自动点跳过）
  useEffect(() => {
    if (autoSkipAfterMs == null || autoSkipAfterMs <= 300) return;
    if (autoSkipAfterMs >= duration * 1000 - 400) return; // 晚于倒计时无意义
    const t = setTimeout(() => once(() => onSkip("engine", autoSkipAfterMs)), autoSkipAfterMs);
    return () => clearTimeout(t);
  }, [autoSkipAfterMs, duration]);

  // 外部「模拟摇晃」信号
  useEffect(() => {
    if (shakeSignal <= 0 || strategy !== "shake") return;
    once(() => onJump("shake", {}));
  }, [shakeSignal]);

  // 真机传感器：剧烈晃动即触发（演示阈值；合法广告须 ≥15m/s²、≥35°、≥3s）
  useEffect(() => {
    if (strategy !== "shake") return;
    const onMotion = (e: DeviceMotionEvent) => {
      const a = e.accelerationIncludingGravity;
      if (!a) return;
      const mag = Math.hypot(a.x ?? 0, a.y ?? 0, a.z ?? 0);
      if (mag > 22 || mag < 6) once(() => onJump("shake", {}));
    };
    window.addEventListener("devicemotion", onMotion);
    return () => window.removeEventListener("devicemotion", onMotion);
  }, [strategy]);

  // 全屏热区策略下，素材任何位置的点击都是“热区触发”（点击任意位置即跳转）；
  // 其他策略下点击素材记为普通 tap，由判断引擎用行为指纹裁决。
  const classify = (): JumpTrigger => (strategy === "anyclick" ? "hotzone" : "tap");

  const clickBody = () => {
    trackAd(ad.id, "click");
    once(() => onJump(classify(), tracker.snapshot(mountTs.current)));
  };

  const clickRoot = (e: React.MouseEvent) => {
    const t = e.target as HTMLElement;
    if (t.closest("[data-safe]")) return; // 跳过按钮 / 品牌区不是热区
    trackAd(ad.id, "click");
    once(() => onJump(classify(), tracker.snapshot(mountTs.current)));
  };

  const anyclick = strategy === "anyclick";
  return (
    <div
      className="absolute inset-0 z-30 flex flex-col bg-black select-none"
      role="dialog"
      aria-label="开屏广告"
      data-testid="splash-ad"
      {...(anyclick ? { onClick: clickRoot, ...tracker.handlers } : {})}
    >
      {/* 广告主体（可点击） */}
      <button
        type="button"
        onClick={clickBody}
        className="relative flex-1 cursor-pointer overflow-hidden text-left focus:outline-none"
        aria-label={`查看广告：${ad.title}`}
        data-testid="splash-body"
        {...(anyclick ? {} : tracker.handlers)}
      >
        <AdCreative ad={ad} variant="splash" />
        {strategy === "shake" && (
          <div className="pointer-events-none absolute inset-x-0 top-16 flex justify-center">
            <span className="animate-[toast-in_.3s_ease-out] rounded-full bg-white/15 px-3.5 py-1.5 text-xs font-bold text-white ring-1 ring-white/30 backdrop-blur-sm">
              摇晃手机 · 立即进入
            </span>
          </div>
        )}
        {/* 广告角标 */}
        <span className="absolute left-3 bottom-3 rounded bg-black/40 px-1.5 py-0.5 text-[10px] text-white/80">
          广告
        </span>
      </button>

      {/* 底部品牌区 */}
      <div className="flex h-24 shrink-0 flex-col items-center justify-center gap-2 bg-white" data-safe>
        <NebulaLogo className="h-9 w-9" />
        <span className="text-xs font-medium text-zinc-500">星云资讯 · 看见更大的宇宙</span>
      </div>

      {/* 跳过按钮 */}
      <button
        type="button"
        data-safe
        data-testid="splash-skip"
        onClick={() => once(() => onSkip("manual", Date.now() - mountTs.current))}
        className="absolute right-4 top-4 z-40 rounded-full bg-black/35 px-3.5 py-1.5 text-xs font-medium text-white backdrop-blur-sm transition active:scale-95"
        aria-label="跳过开屏广告"
      >
        跳过 {count}s
      </button>
    </div>
  );
}
