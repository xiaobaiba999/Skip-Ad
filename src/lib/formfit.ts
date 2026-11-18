/**
 * 全形态设备自适应阈值（FormFit · v5.4.0）
 * ─────────────────────────────────────────────────────────────────
 * 【规范源 · 跨端统一】本 TS 实现是跨端规范源：Android 端 Java 类
 * （FormFit.java，package com.xskip.engine.policy）将逐行对齐本文件的
 * 基准百分比 / 形态分支 / 换算公式；任何阈值改动必须双端同步修改。
 *
 * 本库为纯函数库：不 import 数据库 / React，可在服务端与客户端同源使用。
 *
 * 三大真实问题的修复（v5.4.0 之前阈值全是固定像素 / 固定整屏占比）：
 *   1. 折叠屏展开态角落键定位失效：外屏 1080px 宽 → 内屏 2208px 宽，
 *      角落区若仍按 28% 窗宽换算，横向扩到 618px（屏幕中部），中部普通
 *      按钮被误判为「右上角跳过键」→ 坐标级点击误点。修复：展开态角落
 *      区收缩为 16% / 10%（角落键始终物理贴角，窄判定区反而更准）。
 *   2. 平板形状门误判：iPad 级平板整窗面积是手机的 2.4 倍，35% 面积上限
 *      把大面积内容图误判为「可点跳过键」。修复：长宽比 < 1.6（越方）
 *      收紧至 28%，折叠展开态（≈1.12 近方形）进一步收紧至 25%。
 *   3. 分屏热区判定不准：面积分母若取整屏，分屏小窗内容永远达不到 55%
 *      热区门（漏判），或半屏插屏占比按整屏算被放大误判（误判）。修复：
 *      一切占比分母强制取「当前实际窗口」（分屏/自由窗 = 窗口而非整屏，
 *      referenceW/H 即当前窗口），分屏小窗热区门再收紧至 60% 防误直断。
 */

/** 折叠状态：folded 折叠外屏 / unfolded 展开内屏 / flat 直板（含平板） */
export type FoldState = "folded" | "unfolded" | "flat";

/** 当前窗口几何（分屏/自由窗时 w/h 为实际窗口而非整屏——这是本库的核心输入约定） */
export interface WindowGeom {
  /** 当前窗口像素宽（分屏/自由窗 = 实际窗口而非整屏） */
  w: number;
  /** 当前窗口像素高 */
  h: number;
  foldState: FoldState;
  /** 分屏 / 自由窗口（影响面积分母与热区收紧） */
  multiWindow: boolean;
  /** 屏幕密度（dpi/160，dp→px 换算系数） */
  density: number;
}

/** 按窗口几何动态换算后的全部判定阈值 */
export interface AdaptiveThresholds {
  /** 角落区宽度（px，按窗口宽百分比换算，基准 28%；展开态收缩 16%） */
  cornerZoneW: number;
  /** 角落区高度（px，基准 14%；展开态收缩 10%） */
  cornerZoneH: number;
  /** 跳过键最大面积占比（基准 35%；平板 28%；展开态 25% 更严） */
  skipAreaMaxPct: number;
  /** 热区直断最小占比（基准 55%；分屏收紧 60%） */
  hotZoneMinPct: number;
  /** 半屏插屏最小高度占比（基准 35%；大屏 30%；分屏 40%） */
  halfScreenMinPct: number;
  /** 半屏插屏最大高度占比（基准 55%；大屏 50%；分屏 60%） */
  halfScreenMaxPct: number;
  /** 最小触点尺寸（px = 48dp × density，Android 触控无障碍标准） */
  minTouchPx: number;
  /** 参考基准宽（当前窗口——占比类阈值的分母，跨端对齐关键） */
  referenceW: number;
  /** 参考基准高（当前窗口） */
  referenceH: number;
  /** 适配说明（折叠态/平板/分屏差异化描述，UI 直接展示） */
  note: string;
}

/**
 * 核心：所有判定阈值按当前窗口几何动态换算（不再固定像素）。
 * 分支表（跨端对齐基准）：
 *   cornerZone    基准 28%/14%   展开态 16%/10%
 *   skipAreaMax   基准 35%       长宽比<1.6（平板）28%   展开态 25%
 *   hotZoneMin    基准 55%       分屏 60%
 *   halfScreen    基准 35%~55%   大屏（展开态/平板）30%~50%   分屏 40%~60%
 *   minTouchPx    48dp × density（恒定）
 */
export function adaptThresholds(geom: WindowGeom): AdaptiveThresholds {
  const { w, h, foldState, multiWindow, density } = geom;
  const longSide = Math.max(w, h);
  const shortSide = Math.min(w, h);
  const aspect = shortSide > 0 ? longSide / shortSide : 1; // 长宽比（≥1，越接近 1 越方）
  const unfolded = foldState === "unfolded";
  const tabletLike = aspect < 1.6; // 平板轮廓（4:3≈1.33 / 16:10=1.6 分界；手机普遍 ≈2.2）
  const bigScreen = unfolded || (tabletLike && foldState === "flat"); // 大屏 = 展开内屏或平板

  // ── 角落区（右上角跳过键判定区）：修复一（见文件头）──
  const cornerWBase = unfolded ? 0.16 : 0.28;
  const cornerHBase = unfolded ? 0.1 : 0.14;
  const cornerZoneW = Math.round(w * cornerWBase);
  const cornerZoneH = Math.round(h * cornerHBase);

  // ── 跳过键面积上限：修复二（展开态最严 25% > 平板 28% > 直板基准 35%）──
  const skipAreaMaxPct = unfolded ? 25 : tabletLike ? 28 : 35;

  // ── 热区直断：修复三（分母恒为当前窗口；分屏再收紧防小窗内容误直断）──
  const hotZoneMinPct = multiWindow ? 60 : 55;

  // ── 半屏插屏高度占比区间：大屏广告绝对高度固定 → 占比下移（下限 30 防漏判）；
  //    分屏小窗内容占比天然偏大 → 收紧（40~60 防误判）；分屏优先于大屏分支 ──
  let halfScreenMinPct = 35;
  let halfScreenMaxPct = 55;
  if (multiWindow) {
    halfScreenMinPct = 40;
    halfScreenMaxPct = 60;
  } else if (bigScreen) {
    halfScreenMinPct = 30;
    halfScreenMaxPct = 50;
  }

  // ── 最小触点：48dp 无障碍触控标准 × 密度 ──
  const minTouchPx = Math.round(48 * density);

  // ── 适配说明（组合描述当前窗口走了哪些差异化分支）──
  const notes: string[] = [];
  if (unfolded) {
    notes.push(
      `折叠屏展开态（${w}×${h} 近方形）：角落区按宽度收缩至 ${cornerWBase * 100}%/${cornerHBase * 100}%、跳过键面积上限收紧至 ${skipAreaMaxPct}%——修复展开态角落键定位失效`,
    );
  } else if (tabletLike && foldState === "flat") {
    notes.push(
      `平板形态（长宽比 ${aspect.toFixed(2)} < 1.6）：跳过键面积上限收紧至 ${skipAreaMaxPct}%、半屏插屏区间 ${halfScreenMinPct}%~${halfScreenMaxPct}%——修复平板形状门把大内容图误判为跳过键`,
    );
  } else if (foldState === "folded") {
    notes.push(`折叠屏折叠态（外屏窄条 ${w}×${h}）：按外屏窗口基准 28%/14% 角落区换算`);
  } else {
    notes.push(`直板手机形态（长宽比 ${aspect.toFixed(2)}）：基准阈值按窗口百分比换算`);
  }
  if (multiWindow) {
    notes.push(
      `分屏/自由窗口：全部占比以实际窗口 ${w}×${h} 为分母（而非整屏），热区直断收紧至 ${hotZoneMinPct}%、半屏插屏 ${halfScreenMinPct}%~${halfScreenMaxPct}%——修复分屏热区判定不准`,
    );
  }

  return {
    cornerZoneW,
    cornerZoneH,
    skipAreaMaxPct,
    hotZoneMinPct,
    halfScreenMinPct,
    halfScreenMaxPct,
    minTouchPx,
    referenceW: w,
    referenceH: h,
    note: notes.join("；") + "。",
  };
}

/**
 * 形态识别：宽高比 + 折叠状态 → 设备形态描述（情报站看板与调试沙箱展示用）。
 * 叠加横竖屏与分屏标注。
 */
export function describeForm(geom: WindowGeom): string {
  const { w, h, foldState, multiWindow } = geom;
  const longSide = Math.max(w, h);
  const shortSide = Math.min(w, h);
  const aspect = shortSide > 0 ? longSide / shortSide : 1;
  const parts: string[] = [];
  if (foldState === "folded") {
    parts.push("折叠屏折叠态（外屏窄条）");
  } else if (foldState === "unfolded") {
    parts.push("折叠屏展开态（内屏近方形）");
  } else {
    parts.push(
      aspect < 1.6
        ? `平板形态（长宽比 ${aspect.toFixed(2)}）`
        : `直板手机形态（长宽比 ${aspect.toFixed(2)}）`,
    );
  }
  if (w > h) parts.push("横屏");
  if (multiWindow) parts.push("分屏/自由窗口（按实际窗口判定）");
  return parts.join(" · ");
}

/**
 * 演示预设：12 组典型设备形态（情报站看板「全形态适配」面板与调试沙箱用）。
 * 覆盖：小米折叠屏折叠/展开（竖/横）、iPad 级平板竖/横/分屏、
 * 手机分屏上下/左右、直板手机竖/横、自由窗口。
 * density = dpi/160（换算系数，非 dpi 原值）。
 */
export const FORM_PRESETS: { key: string; label: string; geom: WindowGeom }[] = [
  {
    key: "fold_folded_portrait",
    label: "小米折叠屏 · 折叠外屏竖屏",
    geom: { w: 1080, h: 2650, foldState: "folded", multiWindow: false, density: 3.0 },
  },
  {
    key: "fold_folded_landscape",
    label: "小米折叠屏 · 折叠外屏横屏",
    geom: { w: 2650, h: 1080, foldState: "folded", multiWindow: false, density: 3.0 },
  },
  {
    key: "fold_unfolded_portrait",
    label: "小米折叠屏 · 展开内屏竖屏",
    geom: { w: 2208, h: 2480, foldState: "unfolded", multiWindow: false, density: 2.75 },
  },
  {
    key: "fold_unfolded_landscape",
    label: "小米折叠屏 · 展开内屏横屏",
    geom: { w: 2480, h: 2208, foldState: "unfolded", multiWindow: false, density: 2.75 },
  },
  {
    key: "tablet_portrait",
    label: "iPad 级平板 · 竖屏",
    geom: { w: 2048, h: 2732, foldState: "flat", multiWindow: false, density: 2.0 },
  },
  {
    key: "tablet_landscape",
    label: "iPad 级平板 · 横屏",
    geom: { w: 2732, h: 2048, foldState: "flat", multiWindow: false, density: 2.0 },
  },
  {
    key: "tablet_split_v",
    label: "iPad 级平板 · 上下分屏（上半窗）",
    geom: { w: 2048, h: 1320, foldState: "flat", multiWindow: true, density: 2.0 },
  },
  {
    key: "phone_split_v",
    label: "直板手机 · 上下分屏（上半窗）",
    geom: { w: 1080, h: 1140, foldState: "flat", multiWindow: true, density: 3.0 },
  },
  {
    key: "phone_split_h",
    label: "直板手机 · 左右分屏（左半窗）",
    geom: { w: 532, h: 2340, foldState: "flat", multiWindow: true, density: 3.0 },
  },
  {
    key: "phone_portrait",
    label: "直板手机 · 竖屏（基准形态）",
    geom: { w: 1080, h: 2400, foldState: "flat", multiWindow: false, density: 3.0 },
  },
  {
    key: "phone_landscape",
    label: "直板手机 · 横屏（视频前贴场景）",
    geom: { w: 2400, h: 1080, foldState: "flat", multiWindow: false, density: 3.0 },
  },
  {
    key: "freeform_window",
    label: "折叠屏悬停 · 自由窗口",
    geom: { w: 1200, h: 1600, foldState: "unfolded", multiWindow: true, density: 2.75 },
  },
];
