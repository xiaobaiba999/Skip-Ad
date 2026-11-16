/* 由 scripts/corpus-test.ts 自动生成（勿手改）· 2026-11-16T03:57:37 UTC */
export interface TestSummaryRound {
  round: number; name: string; rate: number; weightedRate: number;
  reactionMs: number; p95Ms: number; adVisibleMs: number; energyMWh: number;
  burstTierMWs: number; burstPreemptMWs: number;
}
export interface TestSummary {
  total: number; real: number; generated: number;
  adversarial: { fakeSkip: number; chainJump: number };
  sideload: { hit: number; total: number };
  guardApps: number;
  rounds: TestSummaryRound[];
  latest: {
    rate: number; coldRate: number; sideloadRate: number; reactionMs: number; adVisibleMs: number;
    energyMWh: number; energyDropPct: number; burstDropPct: number; preemptShare: number;
    missInvisible: number; backRecovered: number; dnsRecovered: number;
    deterministic: boolean; guardZeroKills: boolean; ximalayaOk: boolean;
    ximalaya: { r1Ms: number; r4Ms: number; r5Ms: number; steadyMs: number; steadyVisibleMs: number };
  };
}
export const TEST_SUMMARY: TestSummary = {
  "total": 1010,
  "real": 130,
  "generated": 880,
  "adversarial": {
    "fakeSkip": 102,
    "chainJump": 87
  },
  "sideload": {
    "hit": 257,
    "total": 257
  },
  "guardApps": 29,
  "rounds": [
    {
      "round": 1,
      "name": "基线 v3.0.0 + 对抗样本",
      "rate": 57.62,
      "weightedRate": 57.98,
      "reactionMs": 268,
      "p95Ms": 3294,
      "adVisibleMs": 1561,
      "energyMWh": 0.559,
      "burstTierMWs": 42.6,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 2,
      "name": "+学习规则（12s 窗）",
      "rate": 82.48,
      "weightedRate": 84.13,
      "reactionMs": 284,
      "p95Ms": 4958,
      "adVisibleMs": 2420,
      "energyMWh": 0.662,
      "burstTierMWs": 50.8,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 3,
      "name": "+能耗治理（v3.0.0 现状）",
      "rate": 82.48,
      "weightedRate": 84.13,
      "reactionMs": 240,
      "p95Ms": 4908,
      "adVisibleMs": 2376,
      "energyMWh": 0.244,
      "burstTierMWs": 19.2,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 4,
      "name": "+假跳过甄别",
      "rate": 91.58,
      "weightedRate": 92.18,
      "reactionMs": 243,
      "p95Ms": 4913,
      "adVisibleMs": 2427,
      "energyMWh": 0.244,
      "burstTierMWs": 19.2,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 5,
      "name": "+链式二跳直断",
      "rate": 92.38,
      "weightedRate": 92.67,
      "reactionMs": 258,
      "p95Ms": 4871,
      "adVisibleMs": 2205,
      "energyMWh": 0.238,
      "burstTierMWs": 18.7,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 6,
      "name": "+P0 预拦截",
      "rate": 92.38,
      "weightedRate": 92.67,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.04,
      "burstTierMWs": 13.4,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 7,
      "name": "+返回键兜底",
      "rate": 98.22,
      "weightedRate": 98.33,
      "reactionMs": 64,
      "p95Ms": 542,
      "adVisibleMs": 32,
      "energyMWh": 0.04,
      "burstTierMWs": 14.3,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 8,
      "name": "+定向域名阻断 → 100%",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 40,
      "p95Ms": 39,
      "adVisibleMs": 0,
      "energyMWh": 0.028,
      "burstTierMWs": 18.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 9,
      "name": "+定向查找 · 分级调度",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.034,
      "burstTierMWs": 9.5,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 10,
      "name": "全量回归 ×2",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.034,
      "burstTierMWs": 9.5,
      "burstPreemptMWs": 1.6
    }
  ],
  "latest": {
    "rate": 100,
    "coldRate": 100,
    "sideloadRate": 100,
    "reactionMs": 34,
    "adVisibleMs": 0,
    "energyMWh": 0.034,
    "energyDropPct": 86,
    "burstDropPct": 92,
    "preemptShare": 0.9,
    "missInvisible": 0,
    "backRecovered": 59,
    "dnsRecovered": 18,
    "deterministic": true,
    "guardZeroKills": true,
    "ximalayaOk": true,
    "ximalaya": {
      "r1Ms": 9100,
      "r4Ms": 5365,
      "r5Ms": 420,
      "steadyMs": 34,
      "steadyVisibleMs": 0
    }
  }
};
