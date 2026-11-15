export type AdStrategy = "standard" | "timer" | "shake" | "anyclick";

export const STRATEGY_META: Record<AdStrategy, { label: string; desc: string }> = {
  standard: { label: "标准", desc: "倒计时结束进入首页，点击素材需人工判定" },
  timer: { label: "自动跳转", desc: "倒计时归零自动跳入落地页（2026 激进形态）" },
  shake: { label: "摇一摇", desc: "加速度传感器触发跳转，非点击行为" },
  anyclick: { label: "全屏热区", desc: "素材之外存在隐藏点击热区，误触即跳转" },
};

export interface AdData {
  id: string;
  name: string;
  type: "splash" | "interstitial";
  title: string;
  subtitle: string;
  ctaText: string;
  linkUrl: string;
  theme: string;
  strategy: AdStrategy;
  weight: number;
  active: boolean;
  views: number;
  clicks: number;
}

export interface AdStat {
  id: string;
  name: string;
  type: string;
  strategy: string;
  active: boolean;
  views: number;
  clicks: number;
  ctr: number;
}

export const THEMES = ["ecommerce", "game", "vip", "finance"] as const;
export const STRATEGIES = ["standard", "timer", "shake", "anyclick"] as const;

export const LANDING_META: Record<string, { brand: string; desc: string }> = {
  "promo-1111": { brand: "聚星商城", desc: "双11 全场狂欢 · 低至 1 折" },
  "game-launch": { brand: "星际远征", desc: "年度科幻机甲手游公测" },
  "vip-promo": { brand: "星云 VIP", desc: "限时 5 折 · 免广告畅读" },
  finance: { brand: "星云钱包", desc: "灵活理财 · 100 元起投" },
  default: { brand: "推广落地页", desc: "广告主推广页面" },
};

export async function trackAd(id: string, event: "view" | "click") {
  try {
    await fetch(`/api/ads/${id}/track`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ event }),
    });
  } catch {
    // 埋点失败静默处理，不影响体验
  }
}
