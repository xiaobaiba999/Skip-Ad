"use client";

import { useRef } from "react";
import type { JumpSignals } from "@/lib/judge";

/**
 * 指纹采集 hook：挂在广告可点击区域上，采集按压时长 / 轨迹 / 反应时间。
 * 真人触摸：按压 60~300ms + 轻微轨迹抖动；程序化/误触：0ms 瞬移点击。
 */
export function usePointerTracker() {
  const s = useRef({
    pressed: false,
    downAt: 0,
    upAt: 0,
    downX: 0,
    downY: 0,
    lastX: 0,
    lastY: 0,
    movePx: 0,
    type: "none" as string,
  });

  const onPointerDown = (e: React.PointerEvent) => {
    const t = s.current;
    t.pressed = true;
    t.downAt = Date.now();
    t.upAt = 0;
    t.downX = t.lastX = e.clientX;
    t.downY = t.lastY = e.clientY;
    t.movePx = 0;
    t.type = e.pointerType || "mouse";
  };

  const onPointerMove = (e: React.PointerEvent) => {
    const t = s.current;
    if (!t.pressed) return;
    t.movePx += Math.hypot(e.clientX - t.lastX, e.clientY - t.lastY);
    t.lastX = e.clientX;
    t.lastY = e.clientY;
  };

  const onPointerUp = () => {
    const t = s.current;
    if (!t.pressed) return;
    t.pressed = false;
    t.upAt = Date.now();
  };

  /** 在 click 处理器里调用；since 为广告挂载时间戳（用于 reactionMs） */
  const snapshot = (since?: number): JumpSignals => {
    const t = s.current;
    return {
      pressMs: t.downAt ? Math.round((t.upAt || Date.now()) - t.downAt) : 0,
      movePx: Math.round(t.movePx),
      reactionMs: since ? Math.max(0, Date.now() - since) : undefined,
      pointerType: t.type,
    };
  };

  return {
    handlers: { onPointerDown, onPointerMove, onPointerUp },
    snapshot,
  };
}
