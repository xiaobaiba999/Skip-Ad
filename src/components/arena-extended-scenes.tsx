"use client";

/**
 * v5.4.0 五类新广告场景 · 与 instant-engine.ts planInstantBlock 分支一一对应
 * ─────────────────────────────────────────────────────────────
 *  · HalfScreenAdStage  half_interstitial 半屏插屏：Dialog 底部锚定 42% 高度
 *                       +「广告」角标/关闭键双信号 → 代点关闭键
 *  · UpdateDialogStage  update_dialog 更新诱导弹窗：按钮语义分级 → 只代点
 *                       消极按钮（以后再说），积极按钮（立即更新）零触碰
 *  · FloatBallStage     float_ball 悬浮广告球：TYPE_APPLICATION_OVERLAY 识别
 *                       → 零点击零代点 + 通知引导关闭悬浮窗权限
 *  · FeedNativeStage    feed_native 信息流嵌入：角标 + 容器指纹 → 仅识别
 *                       标注，浏览零干预（绝不自动滑走内容）
 *  · PreRollStage       video_preroll 视频前贴：倒计时静候 5s → 真跳过键
 *                       挂载到点即被代点，进度条跳正片起点
 * 所有交互按钮均为视觉演示（无 onClick）；引擎代点动作用 emerald 高亮态表达。
 */

import type { ElementType } from "react";
import type { ScenarioApp } from "@/lib/scenario-apps";
import type { BlockPlan } from "@/lib/instant-engine";
import { cn } from "@/lib/utils";
import {
  BatteryFull, CircleDot, Clapperboard, CreditCard, Download, Flame, Gauge, Gift,
  Lock, MoreHorizontal, Newspaper, PanelBottom, Play, ScanLine, Search, Settings,
  ShieldCheck, Sparkles, Thermometer, Trash2, X, Zap,
} from "lucide-react";

/** 广告甄别演示阶段（scanning 甄别中 → ignored 判定成立/已代点 → chain 链式/引导态）。
 *  由 arena.tsx 状态机共享（原喜马拉雅类甄别阶段，v5.4.0 新场景复用同一节奏）。 */
export type FakePhase = "scanning" | "ignored" | "chain";

/** 五类新场景舞台组件的统一 props */
export interface SceneStageProps {
  app: ScenarioApp;
  plan: BlockPlan;
  phase: FakePhase | null;
}

/** 新场景应用图标映射（key 与 scenario-apps.ts 的 icon 字段对应；
 *  注：lucide 0.525 无 LayoutPanelBottom，以 PanelBottom 表意「底部锚定面板」） */
const SCENE_ICONS: Record<string, ElementType> = {
  "layout-panel-bottom": PanelBottom,
  download: Download,
  "circle-dot": CircleDot,
  newspaper: Newspaper,
  clapperboard: Clapperboard,
};

/* ═══════════ 共享小件 ═══════════ */

/** 引擎状态胶囊（与 arena.tsx 弹窗分支同款样式；判定成立后转 emerald） */
function EnginePill({ phase, scanningText, doneText }: { phase: FakePhase | null; scanningText: string; doneText: string }) {
  const done = phase === "ignored" || phase === "chain";
  return (
    <div
      className={cn(
        "flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[10px] font-semibold backdrop-blur",
        done ? "bg-emerald-600/85 text-white" : "bg-black/45 text-white/90",
      )}
    >
      {done ? <ShieldCheck className="h-3 w-3" /> : <Zap className="h-3 w-3 animate-pulse text-amber-300" />}
      {phase === null ? "秒跳引擎拦截中…" : done ? doneText : scanningText}
    </div>
  );
}

/** 首拦学习指纹提示（下一次启动将 P0 预拦截） */
function LearnBadge({ className }: { className?: string }) {
  return (
    <div className={cn("absolute left-2.5 top-2.5 z-20 rounded-full bg-fuchsia-600/90 px-2 py-0.5 text-[9px] font-bold text-white", className)}>
      首拦学习指纹 · 下次启动预拦截
    </div>
  );
}

/** 新场景宿主应用的极简顶栏（图标 + 名称 + 右侧动作图标） */
function HostHeader({ app, right: Right }: { app: ScenarioApp; right?: ElementType }) {
  const Icon = SCENE_ICONS[app.icon] ?? Newspaper;
  return (
    <div className="flex shrink-0 items-center gap-2.5 border-b border-zinc-100 bg-white px-4 pb-2.5 pt-1">
      <span className={cn("flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br text-white", app.gradient)}>
        <Icon className="h-4 w-4" />
      </span>
      <span className="text-[15px] font-bold text-zinc-900">{app.name}</span>
      {Right ? <Right className="ml-auto h-4 w-4 text-zinc-400" /> : null}
    </div>
  );
}

/* ═══════════ 1. 半屏插屏（优品严选 · half_interstitial）═══════════
 * 上层正常内容（电商首页静态简化版）+ 底部 42% 半屏广告层：
 * Dialog 底部锚定 +「广告」角标 + 小号关闭键双信号 → 引擎代点关闭。 */
export function HalfScreenAdStage({ app, plan, phase }: SceneStageProps) {
  const goods = [
    { name: "全能洗地机 X7", price: "¥1799", g: "from-orange-400 to-red-500", tag: "焕新补贴" },
    { name: "轻音破壁机 Pro", price: "¥399", g: "from-amber-400 to-orange-500", tag: "以旧换新" },
    { name: "新一级变频空调", price: "¥2299", g: "from-rose-400 to-pink-500", tag: "立减 300" },
    { name: "滚筒洗衣机 10kg", price: "¥1899", g: "from-teal-400 to-emerald-500", tag: "分期免息" },
  ];
  const done = phase === "ignored" || phase === "chain";
  return (
    <div className="relative min-h-0 flex-1" data-testid="ad-stage">
      {/* 上层：应用正常内容（静态电商首页，参考 GroupBuyScreen 简化：列表卡 + 底部导航） */}
      <div className="flex min-h-0 flex-1 flex-col bg-zinc-50">
        <HostHeader app={app} right={Search} />
        <div className="p-3">
          <div className="mb-3 rounded-xl bg-gradient-to-r from-orange-500 to-red-500 p-3 text-white">
            <div className="text-sm font-black">新春焕新家电节 · 主会场直降</div>
            <div className="text-[10px] opacity-90">以旧换新至高补 2000 元 · 全场包邮</div>
          </div>
          <div className="grid grid-cols-2 gap-2.5">
            {goods.map((g) => (
              <div key={g.name} className="overflow-hidden rounded-xl bg-white ring-1 ring-zinc-100">
                <div className={cn("relative h-16 bg-gradient-to-br", g.g)}>
                  <span className="absolute left-1.5 top-1.5 rounded bg-black/35 px-1 text-[8px] font-bold text-white">{g.tag}</span>
                </div>
                <div className="p-2">
                  <div className="truncate text-[11px] font-bold text-zinc-800">{g.name}</div>
                  <div className="mt-0.5 text-xs font-black text-rose-600">{g.price}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
        {/* 底部导航（静态；被半屏广告层覆盖属真实形态） */}
        <div className="mt-auto grid grid-cols-4 border-t border-zinc-200 bg-white py-1.5 text-center text-[9px] font-semibold text-zinc-400">
          {["首页", "分类", "购物车", "我的"].map((t) => (
            <span key={t}>{t}</span>
          ))}
        </div>
      </div>

      {/* 底部 42%：半屏插屏广告层（Dialog 底部锚定 · 顶部细把手条） */}
      <div className="absolute inset-x-0 bottom-0 z-10 flex h-[42%] flex-col rounded-t-2xl bg-white shadow-2xl ring-1 ring-zinc-200">
        <div className="flex justify-center pb-0.5 pt-1.5">
          <span className="h-1 w-10 rounded-full bg-zinc-200" aria-hidden />
        </div>
        {/* 主内容行：左侧 88px 方图（SDK 素材）+ 标题/副标题/CTA */}
        <div className="flex gap-3 px-4 pt-2.5">
          <div className="relative h-[88px] w-[88px] shrink-0 overflow-hidden rounded-xl bg-gradient-to-br from-orange-400 to-rose-500">
            <Sparkles className="absolute inset-0 m-auto h-8 w-8 text-white/90" />
            <span className="absolute left-1 top-1 rounded bg-zinc-800/80 px-1 text-[8px] font-black text-white">广告</span>
          </div>
          <div className="min-w-0 flex-1 py-0.5 pr-7">
            <div className="text-[13px] font-black leading-snug text-zinc-900">{app.ad.title}</div>
            <div className="mt-1 text-[10px] leading-relaxed text-zinc-500">{app.ad.subtitle}</div>
            <button type="button" className="mt-2.5 rounded-full bg-rose-500 px-4 py-1.5 text-[11px] font-black text-white shadow-sm">
              {app.ad.ctaText}
            </button>
          </div>
        </div>
        {/* 营销券条（静态装饰） */}
        <div className="mt-3 flex gap-2 px-4">
          {["满 1000 减 200", "以旧换新补 8%", "12 期免息"].map((c) => (
            <span key={c} className="flex-1 truncate rounded-lg bg-rose-50 px-1.5 py-1.5 text-center text-[9px] font-bold text-rose-600 ring-1 ring-rose-100">
              {c}
            </span>
          ))}
        </div>
        {/* 抢购热度条（静态装饰） */}
        <div className="mt-2.5 flex items-center gap-2 px-4">
          <div className="h-1 flex-1 rounded-full bg-rose-100">
            <div className="h-full w-[82%] rounded-full bg-rose-400" />
          </div>
          <span className="shrink-0 text-[9px] font-bold text-rose-500">已抢 82%</span>
        </div>
        {/* 底部品牌行 */}
        <div className="mt-auto flex items-center justify-between border-t border-zinc-100 px-4 py-2 text-[9px] text-zinc-400">
          <span className="truncate">{app.ad.brand} · Dialog 底部锚定 · 高度 42%</span>
          <span className="shrink-0 rounded bg-zinc-100 px-1.5 py-0.5 font-semibold">点击 CTA 跳落地页</span>
        </div>
        {/* 右上角小关闭键：双信号成立后引擎代点（emerald 高亮，样式切换同弹窗分支） */}
        <button
          type="button"
          data-testid="btn-halfsheet-close"
          aria-label="关闭半屏插屏广告"
          className={cn(
            "absolute right-2 top-2 flex h-5 w-5 items-center justify-center rounded-full transition",
            done ? "bg-emerald-500 text-white ring-2 ring-emerald-300" : "bg-zinc-200/80 text-zinc-500",
          )}
        >
          <X className="h-3 w-3" />
        </button>
      </div>

      {/* 顶部引擎状态胶囊 */}
      <div className="absolute inset-x-0 top-2 z-30 flex justify-center">
        <EnginePill phase={phase} scanningText="半屏形态扫描中 · 高度 42% 底部锚定 ✓" doneText="双信号成立 · 代点关闭键" />
      </div>
      {plan.preempt === false && <LearnBadge />}
    </div>
  );
}

/* ═══════════ 2. 更新诱导弹窗（快讯头条 · update_dialog）═══════════
 * 按钮语义分级：积极按钮「立即更新」为引擎禁区（任意阶段保持原样、
 * 绝不高亮、永不触碰）；只代点消极按钮「以后再说」（emerald 高亮）。 */
export function UpdateDialogStage({ app, plan, phase }: SceneStageProps) {
  const done = phase === "ignored" || phase === "chain";
  const news = [
    { t: "多地以旧换新细则落地，家电销量环比增 42%", src: "财经前线 · 1 小时前" },
    { t: "冷空气携雨雪来袭，本周局地降温超 10℃", src: "天气速报 · 2 小时前" },
    { t: "国产大飞机再开三条新航线，覆盖 12 城", src: "澎湃资讯 · 3 小时前" },
  ];
  return (
    <div className="relative min-h-0 flex-1" data-testid="ad-stage">
      {/* 背景：资讯内容流（静态） */}
      <div className="flex min-h-0 flex-1 flex-col bg-zinc-50">
        <HostHeader app={app} right={Search} />
        <div className="space-y-2 p-3">
          {news.map((n) => (
            <div key={n.t} className="flex items-center gap-2.5 rounded-xl bg-white p-2.5 ring-1 ring-zinc-100">
              <span className="h-11 w-14 shrink-0 rounded-lg bg-gradient-to-br from-zinc-200 to-zinc-100" aria-hidden />
              <div className="min-w-0">
                <div className="text-[11px] font-bold leading-snug text-zinc-800">{n.t}</div>
                <div className="mt-1 text-[9px] text-zinc-400">{n.src}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Dialog 遮罩 */}
      <div className="absolute inset-0 z-10 bg-zinc-950/35" aria-hidden />

      {/* 中央更新诱导弹窗 */}
      <div className="absolute inset-0 z-20 flex items-center justify-center">
        <div className="w-[80%] rounded-2xl bg-white p-5 shadow-2xl" data-testid="update-dialog-card">
          <div className="flex flex-col items-center">
            {/* 顶部图标圆：scanning 阶段下载动效（营造诱导感） */}
            <span className={cn("flex h-12 w-12 items-center justify-center rounded-full bg-cyan-500 text-white shadow-md", phase === "scanning" && "animate-bounce")}>
              <Download className="h-6 w-6" />
            </span>
            <div className="mt-2.5 text-base font-black text-zinc-900">{app.ad.title}</div>
            <div className="mt-0.5 text-[9px] text-zinc-400">v9.2.1 · 安装包 28.6MB · 今日发布</div>
            {/* 两行更新说明 */}
            <div className="mt-2.5 w-full space-y-1 rounded-xl bg-zinc-50 px-3 py-2.5 text-left text-[10px] leading-relaxed text-zinc-500">
              <div>· {app.ad.subtitle}</div>
              <div>· 优化推送耗电与闪退问题，首页加载提速 40%</div>
            </div>
            {/* 立即更新（积极按钮）：引擎禁区 —— 任意阶段保持原样、绝不高亮、永不触碰 */}
            <div className="relative mt-3.5 w-full">
              <button
                type="button"
                data-testid="btn-update-primary"
                className="flex w-full items-center justify-center gap-1.5 rounded-xl bg-gradient-to-r from-rose-500 to-orange-400 py-2.5 text-[13px] font-black text-white shadow-md"
              >
                <Download className="h-3.5 w-3.5" />
                {app.ad.ctaText}
              </button>
              {/* 引擎禁区标签（tooltip 样式，非交互） */}
              <span className="absolute -top-2 right-2 flex items-center gap-0.5 rounded-md bg-zinc-900/90 px-1.5 py-0.5 text-[8px] font-black text-white shadow">
                <Lock className="h-2.5 w-2.5 text-amber-300" /> 引擎禁区
              </span>
            </div>
            {/* 以后再说（消极按钮）：引擎只代点它（emerald 高亮） */}
            <button
              type="button"
              data-testid="btn-update-decline"
              className={cn(
                "mt-2.5 text-xs transition",
                done ? "rounded-lg px-2 py-0.5 font-bold text-emerald-600 ring-2 ring-emerald-300" : "px-2 py-0.5 text-zinc-400 underline",
              )}
            >
              以后再说
            </button>
          </div>
        </div>
      </div>

      {/* 顶部引擎状态胶囊 */}
      <div className="absolute inset-x-0 top-2 z-30 flex justify-center">
        <EnginePill phase={phase} scanningText="诱导弹窗识别 · 按钮语义分级中" doneText="消极按钮已代点 · 积极按钮零触碰" />
      </div>
      {plan.preempt === false && <LearnBadge />}
    </div>
  );
}

/* ═══════════ 3. 悬浮广告球（极速清理大师 · float_ball）═══════════
 * TYPE_APPLICATION_OVERLAY 悬浮窗：零点击策略 —— 球体即广告入口，引擎
 * 绝不代点/拖动（任何 phase 都不变色不高亮，与关闭键 emerald 高亮形成对比）；
 * 识别后仅发通知引导用户关闭悬浮窗权限。 */
export function FloatBallStage({ app, plan, phase }: SceneStageProps) {
  const tiles = [
    { icon: Trash2, t: "垃圾清理" },
    { icon: Gauge, t: "手机加速" },
    { icon: Thermometer, t: "手机降温" },
    { icon: BatteryFull, t: "超强省电" },
  ];
  return (
    <div className="relative min-h-0 flex-1" data-testid="ad-stage">
      {/* 背景：清理工具正常内容（静态） */}
      <div className="flex min-h-0 flex-1 flex-col bg-gradient-to-b from-zinc-100 to-zinc-50">
        <HostHeader app={app} right={Settings} />
        <div className="flex flex-1 flex-col items-center gap-4 px-6 pt-7">
          <div className="flex h-28 w-28 flex-col items-center justify-center rounded-full bg-white shadow-lg ring-8 ring-emerald-50">
            <span className="text-2xl font-black text-emerald-600">62</span>
            <span className="text-[9px] font-semibold text-zinc-400">内存占用 %</span>
          </div>
          <span className="rounded-full bg-gradient-to-r from-emerald-500 to-teal-500 px-8 py-2 text-[12px] font-black text-white shadow-md shadow-emerald-500/30">
            一键加速
          </span>
          <div className="grid w-full grid-cols-4 gap-2 text-center text-[9px] font-semibold text-zinc-600">
            {tiles.map(({ icon: Icon, t }) => (
              <div key={t} className="rounded-xl bg-white py-2.5 ring-1 ring-zinc-100">
                <Icon className="mx-auto h-4 w-4 text-emerald-600" />
                <div className="mt-1">{t}</div>
              </div>
            ))}
          </div>
          <div className="w-full rounded-xl bg-white p-3 ring-1 ring-zinc-100">
            <div className="text-[10px] font-bold text-zinc-700">昨日清理垃圾 2.3GB · 降温 3.2℃</div>
            <div className="mt-1 text-[9px] text-zinc-400">定期清理可保持手机流畅</div>
          </div>
        </div>
      </div>

      {/* 右侧边缘悬浮广告球：零点击策略 —— 球体即广告入口，引擎任何阶段都
          绝不代点/拖动/变色（与其它场景关闭键被代点后的 emerald 高亮形成对比） */}
      <div
        className="absolute right-3 top-1/3 z-20 h-14 w-14 animate-pulse rounded-full bg-gradient-to-br from-fuchsia-500 to-purple-600 shadow-lg shadow-fuchsia-500/40"
        role="img"
        aria-label="第三方悬浮广告球"
        data-testid="floatball-ad"
      >
        <div className="relative flex h-full w-full items-center justify-center">
          <Gift className="h-6 w-6 text-white" />
          {/* 拖动把手视觉（静态装饰，非交互） */}
          <span className="absolute bottom-1.5 left-1/2 flex -translate-x-1/2 flex-col gap-[3px]" aria-hidden>
            <span className="h-[2px] w-3.5 rounded-full bg-white/70" />
            <span className="h-[2px] w-3.5 rounded-full bg-white/70" />
          </span>
        </div>
        <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 rounded bg-zinc-900/80 px-1 text-[8px] font-black text-white">广告</span>
      </div>

      {/* 引擎通知卡片：甄别 → 零点击声明 → 引导关闭悬浮窗权限（chain 阶段附设置入口） */}
      {phase !== null ? (
        <div
          className="absolute inset-x-3 top-3 z-30 rounded-xl bg-zinc-900/95 p-2.5 text-[10px] leading-relaxed text-white shadow-lg ring-1 ring-white/10 backdrop-blur"
          data-testid="floatball-notice"
        >
          <div className="flex items-center gap-1.5 font-bold">
            {phase === "chain" ? (
              <Settings className="h-3 w-3 text-emerald-300" />
            ) : phase === "ignored" ? (
              <ShieldCheck className="h-3 w-3 text-emerald-300" />
            ) : (
              <Zap className="h-3 w-3 animate-pulse text-amber-300" />
            )}
            {phase === "scanning" && "悬浮窗甄别中 · overlay 窗口 + 白名单校验"}
            {phase === "ignored" && "识别为广告悬浮球 · 零点击"}
            {phase === "chain" && "已发通知：点击直达悬浮窗权限设置"}
          </div>
          {phase === "chain" && (
            <div className="mt-1.5 flex items-center justify-between rounded-lg bg-white/10 px-2 py-1.5">
              <span className="flex min-w-0 items-center gap-1.5 opacity-90">
                <Settings className="h-3 w-3 shrink-0 text-emerald-300" />
                <span className="truncate">悬浮窗权限 · {app.name}</span>
              </span>
              {/* 模拟设置入口（非交互视觉样式） */}
              <span className="shrink-0 rounded-full bg-emerald-500 px-2 py-0.5 text-[9px] font-black text-white">前往关闭</span>
            </div>
          )}
        </div>
      ) : (
        <div className="absolute inset-x-0 top-2 z-30 flex justify-center">
          <EnginePill phase={null} scanningText="" doneText="" />
        </div>
      )}
      {/* 学习指纹提示仅在 phase 未识别阶段展示，避免与全宽通知卡片叠压 */}
      {plan.preempt === false && phase === null && <LearnBadge />}
    </div>
  );
}

/** 普通信息流内容卡（标题 + 缩略图占位 + 来源） */
function FeedCard({ t, src, g, hot }: { t: string; src: string; g: string; hot: string }) {
  return (
    <div className="flex items-center gap-2.5 rounded-xl bg-white p-2.5 ring-1 ring-zinc-100">
      <span className={cn("h-14 w-[72px] shrink-0 rounded-lg bg-gradient-to-br", g)} aria-hidden />
      <div className="min-w-0 flex-1">
        <div className="text-[11px] font-bold leading-snug text-zinc-800">{t}</div>
        <div className="mt-1 flex items-center gap-2 text-[9px] text-zinc-400">
          <span className="truncate">{src}</span>
          <span className="flex shrink-0 items-center gap-0.5">
            <Flame className="h-2.5 w-2.5 text-rose-400" />
            {hot}
          </span>
        </div>
      </div>
    </div>
  );
}

/* ═══════════ 4. 信息流嵌入（趣看点资讯 · feed_native）═══════════
 * 合规红线：用户正在浏览的内容流绝不自动滑走 —— 列表布局完全静态，
 * 引擎仅识别标注（amber 扫描虚线框 → emerald 稳定标注框），绝不代点。 */
export function FeedNativeStage({ app, plan, phase }: SceneStageProps) {
  const done = phase === "ignored" || phase === "chain";
  return (
    <div className="relative min-h-0 flex-1" data-testid="ad-stage">
      {/* 信息流列表：布局完全静态 —— 合规红线：绝不自动滚动/滑走用户正在浏览的内容 */}
      <div className="flex min-h-0 flex-1 flex-col bg-zinc-50">
        <HostHeader app={app} right={Search} />
        {/* 频道页签（静态） */}
        <div className="flex shrink-0 gap-4 border-b border-zinc-100 bg-white px-4 pb-1 pt-0.5 text-[11px] font-bold">
          <span className="-mb-1 border-b-2 border-zinc-900 pb-1 text-zinc-900">推荐</span>
          <span className="pb-1 text-zinc-400">热点</span>
          <span className="pb-1 text-zinc-400">科技</span>
          <span className="pb-1 text-zinc-400">本地</span>
        </div>
        <div className="min-h-0 flex-1 space-y-2.5 overflow-y-auto overscroll-contain p-3">
          <FeedCard t="神舟二十一号船箭组合体转运至发射区" src="新华社 · 12 分钟前" g="from-teal-400 to-cyan-500" hot="3.2 万" />
          <FeedCard t="秋粮收购全面展开，旺季购销两旺" src="央视新闻 · 1 小时前" g="from-amber-400 to-orange-500" hot="1.8 万" />

          {/* 广告卡：amber 底色区分 +「广告」角标 + ⋯ 关闭入口 + 营销内容 + CTA */}
          <div
            className={cn(
              "overflow-hidden rounded-xl bg-amber-50 transition",
              phase === "scanning"
                ? "animate-pulse border-2 border-dashed border-amber-400" // 扫描中：amber 虚线框脉冲
                : done
                  ? "border-2 border-dashed border-emerald-500" // 已标注：稳定 emerald 虚线框
                  : "border border-amber-200",
            )}
            data-testid="feed-ad-card"
          >
            {/* 顶部标注条：引擎识别结果（phase 推进后出现） */}
            {phase !== null && (
              <div className={cn("flex items-center gap-1 px-2 py-1 text-[9px] font-bold", done ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-800")}>
                {done ? <ShieldCheck className="h-3 w-3" /> : <ScanLine className="h-3 w-3 animate-pulse" />}
                {done ? "已标注 · 浏览零干预" : "信息流广告已识别 · 角标+容器指纹"}
              </div>
            )}
            <div className="flex gap-2.5 p-2.5">
              <div className="relative h-16 w-16 shrink-0 overflow-hidden rounded-lg bg-gradient-to-br from-rose-400 to-orange-400">
                <CreditCard className="absolute inset-0 m-auto h-6 w-6 text-white/90" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="rounded bg-zinc-800/80 px-1 text-[8px] font-black text-white">广告</span>
                  <span className="truncate text-[9px] text-zinc-400">穿山甲 · NativeExpressView</span>
                  {/* 右上角 ⋯ 关闭入口（视觉演示；仅当用户主动展开菜单才协助代点「不感兴趣」） */}
                  <button type="button" aria-label="不感兴趣" className="ml-auto flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-zinc-400">
                    <MoreHorizontal className="h-3.5 w-3.5" />
                  </button>
                </div>
                <div className="mt-1 text-[11px] font-bold leading-snug text-zinc-800">{app.ad.title}</div>
                <div className="mt-1.5 flex items-center justify-between gap-2">
                  <span className="truncate text-[9px] text-zinc-400">白金卡 · 最高返 5% 加油金</span>
                  <span className="shrink-0 rounded-full bg-amber-500 px-2.5 py-0.5 text-[9px] font-black text-white">{app.ad.ctaText}</span>
                </div>
              </div>
            </div>
          </div>

          <FeedCard t="寒潮蓝色预警：局地降温可达 10℃" src="气象台 · 2 小时前" g="from-violet-400 to-fuchsia-500" hot="9600" />
        </div>
      </div>

      {/* 底部提示胶囊：仅识别标注不代点（phase 未识别时为默认胶囊） */}
      <div className="absolute inset-x-0 bottom-2 z-30 flex justify-center">
        {phase === null ? (
          <EnginePill phase={null} scanningText="" doneText="" />
        ) : (
          <div className="flex items-center gap-1.5 rounded-full bg-emerald-600/85 px-3 py-1.5 text-[10px] font-semibold text-white backdrop-blur">
            <ShieldCheck className="h-3 w-3" /> 信息流广告 · 仅识别标注不代点
          </div>
        )}
      </div>
      {plan.preempt === false && <LearnBadge />}
    </div>
  );
}

/* ═══════════ 5. 视频前贴（云影剧场 · video_preroll）═══════════
 * 播放器容器 + 倒计时 +「跳过广告」三信号确认 → 5s 倒计时静候
 * （traits.realSkipDelayMs 5000，演示为静态「广告 5」）→ 真跳过键
 * 挂载到点即被代点（emerald ring），进度条跳正片起点。 */
export function PreRollStage({ app, plan, phase }: SceneStageProps) {
  const done = phase === "ignored" || phase === "chain";
  return (
    <div className="relative min-h-0 flex-1 bg-zinc-950" data-testid="ad-stage">
      {/* 广告画面（全屏营销素材） */}
      <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-gradient-to-br from-violet-600 to-purple-700 px-8">
        <span className="flex h-14 w-14 items-center justify-center rounded-full bg-white/15 ring-1 ring-white/25 backdrop-blur">
          <Play className="h-7 w-7 text-white" />
        </span>
        <div className="text-center">
          <div className="text-lg font-black text-white">{app.ad.title}</div>
          <div className="mt-1 text-[11px] text-white/80">{app.ad.subtitle}</div>
        </div>
        <span className="rounded-full bg-gradient-to-r from-amber-400 to-orange-400 px-6 py-2 text-[12px] font-black text-zinc-900 shadow-lg">
          {app.ad.ctaText}
        </span>
        <div className="text-[9px] text-white/60">{app.ad.brand} · 视频前贴</div>
      </div>

      {/* 左上角「广告」标识 */}
      <div className="absolute left-3 top-3 z-10 flex items-center gap-1.5">
        <span className="rounded bg-zinc-800/80 px-1 text-[8px] font-black text-white">广告</span>
        <span className="text-[9px] font-semibold text-white/60">云影前贴</span>
      </div>

      {/* 右上角跳过区：倒计时静候（静态 5）→ 真跳过键挂载即被代点（emerald ring） */}
      <div className="absolute right-3 top-3 z-20">
        {done ? (
          <button
            type="button"
            data-testid="btn-preroll-skip"
            className="flex items-center gap-1.5 rounded-full bg-black/50 px-3 py-1.5 text-[11px] font-bold text-white ring-2 ring-emerald-300 backdrop-blur"
          >
            跳过广告
            <ShieldCheck className="h-3 w-3 text-emerald-300" />
          </button>
        ) : (
          <span className="text-[11px] font-bold tabular-nums text-white/70" data-testid="preroll-countdown">
            广告 5
          </span>
        )}
      </div>

      {/* 底部播放器控制区：amber 短段 = 广告进度（时轴前 8%）· zinc 长段 = 正片；
          代点跳过后播放头跳到正片起点（emerald） */}
      <div className="absolute inset-x-0 bottom-0 z-10 bg-gradient-to-t from-black/70 to-transparent px-4 pb-2.5 pt-10">
        <div className="mb-1.5 flex items-center justify-between text-[9px] font-semibold">
          {done ? (
            <span className="text-emerald-300">已代点跳过 · 正片 00:00</span>
          ) : (
            <span className="text-amber-300">广告中 · 5s 后可跳过</span>
          )}
          <span className="text-white/50">正片 12:24 · 1080P</span>
        </div>
        <div className="relative h-1.5 w-full rounded-full bg-zinc-700/70">
          {/* 广告段（时轴前 8%，amber；跳过后转灰） */}
          <span className={cn("absolute left-0 top-0 h-full w-[8%] rounded-l-full transition-colors", done ? "bg-zinc-600" : "bg-amber-400")} />
          {/* 播放头：跳过后落位正片起点（emerald 发光） */}
          <span
            className={cn(
              "absolute top-1/2 h-2.5 w-2.5 -translate-y-1/2 rounded-full transition-all",
              done ? "left-[8%] bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.85)]" : "left-[3%] bg-amber-300",
            )}
          />
        </div>
      </div>

      {/* 底部引擎状态胶囊（居中，位于播放器控制区上方） */}
      <div className="absolute inset-x-0 bottom-16 z-30 flex justify-center">
        <EnginePill phase={phase} scanningText="播放器容器+倒计时三信号确认" doneText="真跳过键已挂载 · 到点即跳" />
      </div>
      {/* 学习指纹提示置于「广告」标识下方，避免与其叠压 */}
      {plan.preempt === false && <LearnBadge className="top-9" />}
    </div>
  );
}
