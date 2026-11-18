/**
 * 学习规则质量模型 · v5.4.0（纯函数库，不依赖 db，可在客户端使用）
 * ─────────────────────────────────────────────────────────────────────
 *  · scoreRule：Wilson 置信下界(0~60 分) + 命中稳定性 log2(1+hit)×6(≤24)
 *    + 时效衰减(30 天未命中 -15、60 天 -30) - 误触惩罚 false×12 → 0~100 分，
 *    A≥80 / B≥60 / C≥40 / D<40 分级；样本不足（<3 次）进入 shadow 阴影观察。
 *  · isNoiseSample：李跳跳式「事后验证」噪声样本过滤——同秒多点位 /
 *    关窗验证不足 / 窗类不稳 / 超窗命中，任一为真即拒绝沉淀。
 *  · lifecycleTransition：A/B→active；C→cooling（降权运行，调度分 ×0.6）；
 *    D 或 30 天零命中→retired（retired 7 天后可自动清理，策略由调用方执行）；
 *    shadow（样本不足）保持阴影运行不参与正常排名。
 *  · generalizeRule：单应用学习规则 → 同 SDK 家族全局规则（appName="sdk:csj"
 *    等）；同 SDK 的 viewId/容器签名可跨应用复用（宿主自建 host 除外）。
 */

export interface RuleStats {
  hitCount: number;
  missCount: number;
  falseCount: number;
  lastHitAt: number | null;
  createdDaysAgo: number;
}

export type RuleGrade = "A" | "B" | "C" | "D";
export type RuleLifecycle = "active" | "cooling" | "retired" | "shadow";

export interface QualityReport {
  score: number; // 0~100
  grade: RuleGrade;
  noise: boolean; // 是否噪声样本
  lifecycle: RuleLifecycle;
  wilson: number; // Wilson 置信下界
  reasons: string[];
  advice: string;
}

/** Wilson 置信下界（z=1.96）：p̂=hit/n，标准公式实现 */
function wilsonLowerBound(hit: number, n: number): number {
  if (n <= 0 || hit < 0) return 0;
  const z = 1.96;
  const z2 = z * z;
  const p = hit / n;
  const denom = 1 + z2 / n;
  const center = (p + z2 / (2 * n)) / denom;
  const margin = (z * Math.sqrt((p * (1 - p)) / n + z2 / (4 * n * n))) / denom;
  return Math.max(0, Math.min(1, center - margin));
}

function round1(v: number): number {
  return Math.round(v * 10) / 10;
}

/**
 * 质量评分：Wilson 下界(0~60) + 命中稳定性 log2(1+hit)×6(≤24)
 * + 时效衰减(30 天未命中 -15、60 天 -30) - 误触惩罚 false×12。
 * 从未命中的规则按创建时长计时效；样本 <3 次进入 shadow（数据不足，阴影观察）。
 */
export function scoreRule(stats: RuleStats): QualityReport {
  const hit = Math.max(0, Math.round(stats.hitCount));
  const miss = Math.max(0, Math.round(stats.missCount));
  const fal = Math.max(0, Math.round(stats.falseCount));
  const n = hit + miss + fal;
  const reasons: string[] = [];

  // 1) Wilson 置信下界 → 0~60 分
  const wilson = wilsonLowerBound(hit, n);
  const wilsonPts = wilson * 60;
  reasons.push(`Wilson 置信下界 ${(wilson * 100).toFixed(1)}%（命中 ${hit}/${n}）→ ${round1(wilsonPts)} 分（≤60）`);

  // 2) 命中稳定性 log2(1+hit)×6，封顶 24
  const stabilityPts = Math.min(24, Math.log2(1 + hit) * 6);
  reasons.push(`命中稳定性 log2(1+${hit})×6 = ${round1(stabilityPts)} 分（≤24）`);

  // 3) 时效衰减：30 天未命中 -15、60 天 -30（lastHitAt 为空按创建时长计）
  const daysSinceLastHit =
    stats.lastHitAt != null ? Math.max(0, (Date.now() - stats.lastHitAt) / 86400000) : Math.max(0, stats.createdDaysAgo);
  let decay = 0;
  if (daysSinceLastHit >= 60) {
    decay = -30;
    reasons.push(`超 60 天未命中（${Math.floor(daysSinceLastHit)} 天）-30`);
  } else if (daysSinceLastHit >= 30) {
    decay = -15;
    reasons.push(`超 30 天未命中（${Math.floor(daysSinceLastHit)} 天）-15`);
  }

  // 4) 误触惩罚 false×12
  const falsePenalty = fal * 12;
  if (fal > 0) reasons.push(`误触 ${fal} 次 ×12 = -${falsePenalty}`);

  let score = Math.max(0, Math.min(100, wilsonPts + stabilityPts + decay - falsePenalty));
  score = round1(score);

  // 噪声样本：误触占比 ≥ 1/2
  const noise = n > 0 && fal * 2 >= n;
  if (noise) reasons.push(`噪声样本：误触 ${fal}/${n} 占比过高，建议弃用并回溯学习来源`);

  const grade: RuleGrade = score >= 80 ? "A" : score >= 60 ? "B" : score >= 40 ? "C" : "D";
  const advice = noise
    ? "噪声样本：建议直接弃用该规则并回溯学习来源（误触占比过高）"
    : grade === "A"
      ? "质量优秀：保持运行，可作为同 SDK 家族泛化候选"
      : grade === "B"
        ? "质量良好：正常运行，持续观察命中/漏拦变化"
        : grade === "C"
          ? "质量偏弱：已进入冷却降权（调度分 ×0.6），排查漏拦与误触来源"
          : "质量不合格：建议退役重建特征（retired 7 天后可自动清理）";

  const draft: QualityReport = {
    score,
    grade,
    noise,
    lifecycle: "active",
    wilson: Math.round(wilson * 10000) / 10000,
    reasons,
    advice,
  };
  // 样本不足（<3 次）：shadow 阴影观察，不进入正常生命周期排名
  const lifecycle = n < 3 ? "shadow" : lifecycleTransition(draft, daysSinceLastHit);
  return { ...draft, lifecycle };
}

/**
 * 噪声样本过滤（李跳跳式事后验证，任一为真即噪声，拒绝沉淀）：
 *  · sameSecondHits ≥ 2     同秒多点位（疑为误学位置，如聊天流式内容）
 *  · treeShrinkPct < 40     关窗验证不足（点击后节点树收缩不足 40%）
 *  · windowClassStable=false 窗类不稳（关窗前后 className 不一致）
 *  · hitAfterMs > 12000     超窗命中（超出 12s 开屏窗才命中）
 */
export function isNoiseSample(sample: {
  sameSecondHits: number;
  treeShrinkPct: number;
  windowClassStable: boolean;
  hitAfterMs: number;
}): { noise: boolean; reason: string } {
  if (sample.sameSecondHits >= 2) {
    return { noise: true, reason: `同秒多点位（${sample.sameSecondHits} 处同时命中，疑为误学位置）` };
  }
  if (sample.treeShrinkPct < 40) {
    return { noise: true, reason: `关窗验证不足（节点树仅收缩 ${sample.treeShrinkPct}%，需 ≥40%）` };
  }
  if (!sample.windowClassStable) {
    return { noise: true, reason: "窗口类不稳（关窗前后 className 不一致）" };
  }
  if (sample.hitAfterMs > 12000) {
    return { noise: true, reason: `超窗命中（${sample.hitAfterMs}ms > 12000ms 开屏窗）` };
  }
  return { noise: false, reason: "样本通过噪声过滤（单点位 + 关窗验证 + 窗类稳定 + 窗内命中）" };
}

/**
 * 生命周期迁移：A/B→active；C→cooling（降权运行，调度分 ×0.6）；
 * D 或 30 天零命中→retired；retired 7 天后可自动清理（由调用方按
 * daysSinceLastHit ≥ 37 等策略执行删除）；shadow（样本不足）保持阴影观察。
 */
export function lifecycleTransition(report: QualityReport, daysSinceLastHit: number): RuleLifecycle {
  if (report.lifecycle === "shadow") return "shadow"; // 样本不足：继续观察，不参与正常排名
  if (report.grade === "D" || daysSinceLastHit >= 30) return "retired";
  if (report.grade === "C") return "cooling";
  return "active";
}

/**
 * 规则泛化：单应用学习规则 → 同 SDK 家族全局规则（appName="sdk:csj" 等）。
 * 同 SDK 的 viewId/容器签名可跨应用复用；无家族信息或宿主自建（host，
 * 页面逻辑各 App 私有）返回 null 表示不可泛化。
 */
export function generalizeRule(rule: {
  appName: string;
  sdkFamily: string;
  pattern: string;
  action: string;
  adType: string;
}): { appName: string; pattern: string; scopeLabel: string } | null {
  const fam = rule.sdkFamily?.trim();
  if (!fam) return null;
  const family = SDK_FAMILIES.find((f) => f.key === fam);
  if (!family) return null;
  if (family.key === "host") return null; // 宿主自建广告无跨应用复用价值
  return {
    appName: `sdk:${family.key}`,
    pattern: `【${family.label} 家族泛化】源自「${rule.appName}」：${rule.pattern}`,
    scopeLabel: `同 ${family.label}（${family.key}）SDK 家族跨应用复用：${family.packages[0] ?? ""} 同源 viewId/容器签名`,
  };
}

/**
 * SDK 家族指纹表：穿山甲 csj / 优量汇 gdt / 快手 ks / AdMob admob / 宿主自建 host。
 * packages 为 SDK 包名前缀，windowHints 为广告窗 className 指纹分段
 * （P0.7 SDK 广告窗识别用，零文本广告的首次可判依据）。
 */
export const SDK_FAMILIES: { key: string; label: string; packages: string[]; windowHints: string[] }[] = [
  {
    key: "csj",
    label: "穿山甲",
    packages: ["com.bytedance.sdk.openadsdk"],
    windowHints: ["openadsdk", "TTFullScreenExpressActivity", "TTRewardVideoActivity", "TTNativeExpressView"],
  },
  {
    key: "gdt",
    label: "优量汇",
    packages: ["com.qq.e.ads", "com.qq.e"],
    windowHints: ["GDTNativeExprView", "GDTAdActivity", "HalfScreenExpressAd"],
  },
  {
    key: "ks",
    label: "快手",
    packages: ["com.kwad"],
    windowHints: ["kwad", "KsAdActivity", "KsFullScreenVideoActivity"],
  },
  {
    key: "admob",
    label: "AdMob",
    packages: ["com.google.android.gms.ads"],
    windowHints: ["gms.ads", "com.google.android.gms.ads.AdActivity"],
  },
  {
    key: "host",
    label: "宿主自建",
    packages: [],
    windowHints: [".ad.activity", "AdActivity", "WelComeActivity"],
  },
];
