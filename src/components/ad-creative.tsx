"use client";

import { ShoppingCart, Rocket, Crown, TrendingUp, Gift, Coins, Sparkles, Landmark, Wallet } from "lucide-react";

export interface CreativeData {
  title: string;
  subtitle: string;
  theme: "ecommerce" | "game" | "vip" | "finance" | "gov" | "pay";
}

/**
 * 纯 CSS/SVG 广告素材渲染器 —— 零图片依赖。
 * 六套主题：电商促销 / 游戏首发 / 会员特惠 / 理财推广 / 政务合作 / 支付红包
 */
export function AdCreative({ ad, variant }: { ad: CreativeData; variant: "splash" | "card" }) {
  switch (ad.theme) {
    case "game":
      return <GameCreative ad={ad} variant={variant} />;
    case "vip":
      return <VipCreative ad={ad} variant={variant} />;
    case "finance":
      return <FinanceCreative ad={ad} variant={variant} />;
    case "gov":
      return <GovCreative ad={ad} variant={variant} />;
    case "pay":
      return <PayCreative ad={ad} variant={variant} />;
    default:
      return <EcommerceCreative ad={ad} variant={variant} />;
  }
}

/* ───────── 电商促销：红橙渐变 ───────── */
function EcommerceCreative({ ad, variant }: { ad: CreativeData; variant: "splash" | "card" }) {
  const big = variant === "splash";
  return (
    <div className="relative flex h-full w-full flex-col justify-end overflow-hidden bg-gradient-to-br from-rose-600 via-red-500 to-orange-400 text-white">
      <div className="absolute -left-8 -top-10 h-40 w-40 rounded-full bg-white/10" />
      <div className="absolute right-6 top-16 h-24 w-24 rounded-full bg-amber-300/30 blur-sm" />
      <div className="absolute right-16 top-6 text-amber-200/70">
        <Sparkles className={big ? "h-8 w-8" : "h-5 w-5"} />
      </div>
      <div className="absolute -bottom-6 -right-6 h-32 w-32 rounded-full bg-white/10" />
      <div className="absolute left-6 top-10 rotate-[-8deg] rounded-xl border-2 border-dashed border-white/60 px-3 py-1.5">
        <span className={`font-black tracking-wider ${big ? "text-2xl" : "text-base"}`}>11.11</span>
        <div className="text-[10px] font-bold">狂欢盛典</div>
      </div>
      <div className="absolute right-8 bottom-40 opacity-80">
        <ShoppingCart className={big ? "h-16 w-16 text-white/50" : "h-10 w-10 text-white/50"} strokeWidth={1.2} />
      </div>
      <div className={`relative z-10 ${big ? "p-7 pb-16" : "p-5"}`}>
        <div className={`font-black leading-tight drop-shadow-md ${big ? "text-4xl" : "text-2xl"}`}>{ad.title}</div>
        <div className={`mt-2 font-medium text-white/90 ${big ? "text-base" : "text-xs"}`}>{ad.subtitle}</div>
      </div>
    </div>
  );
}

/* ───────── 游戏首发：深空紫 ───────── */
function GameCreative({ ad, variant }: { ad: CreativeData; variant: "splash" | "card" }) {
  const big = variant === "splash";
  return (
    <div className="relative flex h-full w-full flex-col justify-end overflow-hidden bg-gradient-to-b from-zinc-950 via-purple-950 to-fuchsia-900 text-white">
      <div className="absolute inset-0 opacity-40 [background-image:radial-gradient(white_1px,transparent_1px)] [background-size:28px_28px]" />
      <div className="absolute left-1/2 top-14 -translate-x-1/2">
        <div className={`rounded-full bg-gradient-to-br from-fuchsia-400 to-purple-600 shadow-[0_0_60px_20px_rgba(192,38,211,0.45)] ${big ? "h-28 w-28" : "h-16 w-16"}`}>
          <div className="flex h-full w-full items-center justify-center">
            <Rocket className={`${big ? "h-12 w-12" : "h-7 w-7"} text-white`} strokeWidth={1.5} />
          </div>
        </div>
        <div className={`mx-auto mt-3 rounded-full border border-fuchsia-300/50 ${big ? "h-1 w-44" : "h-0.5 w-28"}`} />
      </div>
      <div className="absolute left-6 top-8 rounded-md bg-fuchsia-500/80 px-2 py-0.5 text-[10px] font-bold tracking-widest">
        公测开启
      </div>
      <div className={`relative z-10 ${big ? "p-7 pb-16" : "p-5"}`}>
        <div className={`font-black leading-tight tracking-wide ${big ? "text-4xl" : "text-2xl"}`}>{ad.title}</div>
        <div className={`mt-2 text-white/80 ${big ? "text-base" : "text-xs"}`}>{ad.subtitle}</div>
      </div>
    </div>
  );
}

/* ───────── 会员特惠：金色暗黑 ───────── */
function VipCreative({ ad, variant }: { ad: CreativeData; variant: "splash" | "card" }) {
  const big = variant === "splash";
  return (
    <div className="relative flex h-full w-full flex-col justify-end overflow-hidden bg-gradient-to-br from-zinc-950 via-zinc-900 to-yellow-800 text-amber-100">
      <div className="absolute -right-10 top-8 h-36 w-36 rounded-full border border-yellow-500/30" />
      <div className="absolute -right-4 top-14 h-24 w-24 rounded-full border border-yellow-500/20" />
      <div className="absolute right-10 top-16 text-yellow-400/70">
        <Crown className={big ? "h-10 w-10" : "h-6 w-6"} />
      </div>
      <div className="absolute left-6 top-9 rounded-full bg-yellow-500/20 px-2.5 py-1 text-[10px] font-bold tracking-widest text-yellow-300 ring-1 ring-yellow-500/40">
        限时 5 折
      </div>
      <div className={`relative z-10 ${big ? "p-7 pb-16" : "p-5"}`}>
        <div className={`font-black leading-tight ${big ? "text-4xl" : "text-2xl"}`}>{ad.title}</div>
        <div className={`mt-2 text-amber-200/80 ${big ? "text-base" : "text-xs"}`}>{ad.subtitle}</div>
      </div>
    </div>
  );
}

/* ───────── 理财推广：深蓝金融 ───────── */
function FinanceCreative({ ad, variant }: { ad: CreativeData; variant: "splash" | "card" }) {
  const big = variant === "splash";
  return (
    <div className="relative flex h-full w-full flex-col justify-end overflow-hidden bg-gradient-to-b from-sky-950 via-blue-900 to-indigo-800 text-white">
      <div className="absolute left-1/2 top-12 -translate-x-1/2">
        <div className={`flex items-center justify-center rounded-2xl bg-white/10 ring-1 ring-white/20 ${big ? "h-24 w-24" : "h-14 w-14"}`}>
          <TrendingUp className={`${big ? "h-12 w-12" : "h-7 w-7"} text-emerald-300`} strokeWidth={1.5} />
        </div>
      </div>
      <div className="absolute bottom-40 left-6 opacity-30">
        <Coins className={big ? "h-14 w-14" : "h-8 w-8"} strokeWidth={1.2} />
      </div>
      <div className="absolute bottom-44 right-8 h-16 w-16 rounded-full bg-emerald-400/20 blur-md" />
      <div className="absolute left-6 top-8 rounded-md bg-emerald-500/90 px-2 py-0.5 text-[10px] font-bold tracking-widest">
        新客专享
      </div>
      <div className={`relative z-10 ${big ? "p-7 pb-16" : "p-5"}`}>
        <div className={`font-black leading-tight ${big ? "text-4xl" : "text-2xl"}`}>{ad.title}</div>
        <div className={`mt-2 text-white/80 ${big ? "text-base" : "text-xs"}`}>{ad.subtitle}</div>
      </div>
    </div>
  );
}

/* ───────── 政务合作：民生蓝绿 ───────── */
function GovCreative({ ad, variant }: { ad: CreativeData; variant: "splash" | "card" }) {
  const big = variant === "splash";
  return (
    <div className="relative flex h-full w-full flex-col justify-end overflow-hidden bg-gradient-to-br from-teal-700 via-cyan-700 to-sky-600 text-white">
      <div className="absolute -left-6 top-10 h-32 w-32 rounded-full bg-white/10" />
      <div className="absolute right-8 top-12 opacity-80">
        <Landmark className={big ? "h-14 w-14 text-white/60" : "h-9 w-9 text-white/60"} strokeWidth={1.2} />
      </div>
      <div className="absolute -bottom-8 right-10 h-36 w-36 rounded-full bg-emerald-300/15 blur-sm" />
      <div className="absolute left-6 top-8 flex items-center gap-1 rounded-md bg-white/20 px-2 py-0.5 text-[10px] font-bold tracking-widest ring-1 ring-white/30">
        <Gift className="h-3 w-3" /> 民生服务
      </div>
      <div className={`relative z-10 ${big ? "p-7 pb-16" : "p-5"}`}>
        <div className={`font-black leading-tight ${big ? "text-4xl" : "text-2xl"}`}>{ad.title}</div>
        <div className={`mt-2 text-white/85 ${big ? "text-base" : "text-xs"}`}>{ad.subtitle}</div>
      </div>
    </div>
  );
}

/* ───────── 支付红包：翡翠绿 ───────── */
function PayCreative({ ad, variant }: { ad: CreativeData; variant: "splash" | "card" }) {
  const big = variant === "splash";
  return (
    <div className="relative flex h-full w-full flex-col justify-end overflow-hidden bg-gradient-to-b from-emerald-800 via-emerald-600 to-teal-500 text-white">
      <div className="absolute left-1/2 top-12 -translate-x-1/2">
        <div className={`relative flex items-center justify-center rounded-full bg-red-500 shadow-[0_8px_40px_rgba(239,68,68,0.5)] ${big ? "h-24 w-24" : "h-14 w-14"}`}>
          <span className={`font-black ${big ? "text-3xl" : "text-lg"}`}>¥</span>
          <span className="absolute -right-1 -top-1 flex h-7 w-7 items-center justify-center rounded-full bg-amber-400 text-[10px] font-black text-emerald-900">
            188
          </span>
        </div>
      </div>
      <div className="absolute right-6 bottom-44 opacity-60">
        <Wallet className={big ? "h-12 w-12 text-white/50" : "h-8 w-8 text-white/50"} strokeWidth={1.2} />
      </div>
      <div className="absolute left-6 top-8 rounded-md bg-amber-400 px-2 py-0.5 text-[10px] font-black tracking-widest text-emerald-900">
        摇一摇领红包
      </div>
      <div className={`relative z-10 ${big ? "p-7 pb-16" : "p-5"}`}>
        <div className={`font-black leading-tight ${big ? "text-4xl" : "text-2xl"}`}>{ad.title}</div>
        <div className={`mt-2 text-white/85 ${big ? "text-base" : "text-xs"}`}>{ad.subtitle}</div>
      </div>
    </div>
  );
}
