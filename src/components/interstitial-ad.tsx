"use client";

import { useEffect, useRef, useState } from "react";
import { AdData, trackAd } from "@/lib/types";
import { AdCreative } from "@/components/ad-creative";
import { usePointerTracker } from "@/lib/pointer-tracker";
import type { JumpSignals, JumpTrigger } from "@/lib/judge";
import { X } from "lucide-react";

/**
 * 跳转广告（插屏）：页面跳转时弹出拦截
 * - 强制观看 N 秒后才可关闭（保护广告主权益）
 * - 点击广告主体进入落地页 —— 但先经判断引擎裁决（广告诱导 or 用户主动）
 * - 关闭后放行原目标页
 */
export function InterstitialAd({
  ad,
  forceSeconds = 3,
  onClose,
  onJump,
}: {
  ad: AdData;
  forceSeconds?: number;
  onClose: () => void;
  onJump: (trigger: JumpTrigger, signals: JumpSignals) => void;
}) {
  const [count, setCount] = useState(forceSeconds);
  const mountTs = useRef(Date.now());
  const tracker = usePointerTracker();

  // 曝光埋点：挂载时上报一次
  useEffect(() => {
    trackAd(ad.id, "view");
  }, [ad.id]);

  // 倒计时（归零后关闭按钮解锁，无需回调父组件）
  useEffect(() => {
    if (count === 0) return;
    const t = setTimeout(() => setCount(count - 1), 1000);
    return () => clearTimeout(t);
  }, [count]);

  const closable = count === 0;

  const jump = () => {
    trackAd(ad.id, "click");
    onJump("tap", tracker.snapshot(mountTs.current));
  };

  return (
    <div
      className="absolute inset-0 z-40 flex items-center justify-center bg-black/70 backdrop-blur-[2px]"
      role="dialog"
      aria-label="插屏广告"
      data-testid="interstitial"
    >
      <div className="relative w-[78%] animate-[ad-pop_.28s_ease-out]" {...tracker.handlers}>
        {/* 关闭按钮：置于裁剪容器之外，倒计时期间禁用 */}
        <button
          type="button"
          data-testid="inter-close"
          onClick={closable ? onClose : undefined}
          disabled={!closable}
          className={`absolute -right-1 -top-10 z-10 flex h-8 w-8 items-center justify-center rounded-full text-white transition ${
            closable ? "cursor-pointer bg-white/25 backdrop-blur active:scale-90" : "cursor-not-allowed bg-white/15"
          }`}
          aria-label={closable ? "关闭广告" : `${count}秒后可关闭`}
        >
          {closable ? <X className="h-4.5 w-4.5" /> : <span className="text-xs font-bold tabular-nums">{count}</span>}
        </button>

        <div className="overflow-hidden rounded-2xl shadow-2xl">
          {/* 广告主体（可点击） */}
          <button
            type="button"
            onClick={jump}
            data-testid="inter-body"
            className="relative block aspect-[3/4] w-full cursor-pointer overflow-hidden focus:outline-none"
            aria-label={`查看广告：${ad.title}`}
          >
            <AdCreative ad={ad} variant="card" />
            <span className="absolute left-3 bottom-3 rounded bg-black/40 px-1.5 py-0.5 text-[10px] text-white/80">
              广告
            </span>
          </button>

          {/* CTA 条 */}
          <button
            type="button"
            onClick={jump}
            data-testid="inter-cta"
            className="block w-full cursor-pointer bg-white py-2.5 text-center text-sm font-bold text-zinc-900 transition active:bg-zinc-100"
          >
            {ad.ctaText} →
          </button>
        </div>
      </div>
    </div>
  );
}
