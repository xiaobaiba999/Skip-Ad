/* 由 scripts/corpus-test.ts 自动生成（勿手改）· 2026-11-16T04:52:27 UTC */
export interface TestSummaryRound {
  round: number; name: string; rate: number; weightedRate: number;
  reactionMs: number; p95Ms: number; adVisibleMs: number; energyMWh: number;
  burstTierMWs: number; burstPreemptMWs: number;
}
export interface TestSummary {
  total: number; real: number; generated: number;
  adversarial: { fakeSkip: number; chainJump: number; gyro: number; popup: number };
  sideload: { hit: number; total: number };
  guardApps: number;
  rounds: TestSummaryRound[];
  latest: {
    rate: number; coldRate: number; sideloadRate: number; reactionMs: number; adVisibleMs: number;
    energyMWh: number; energyDropPct: number; burstDropPct: number; preemptShare: number;
    gyroAll: boolean; popupAll: boolean;
    missInvisible: number; backRecovered: number; dnsRecovered: number;
    deterministic: boolean; guardZeroKills: boolean; ximalayaOk: boolean;
    ximalaya: { r1Ms: number; r3Ms: number; steadyMs: number; steadyVisibleMs: number };
  };
}
export const TEST_SUMMARY: TestSummary = {
  "total": 1060,
  "real": 130,
  "generated": 930,
  "adversarial": {
    "fakeSkip": 107,
    "chainJump": 89,
    "gyro": 63,
    "popup": 105
  },
  "sideload": {
    "hit": 273,
    "total": 273
  },
  "guardApps": 29,
  "rounds": [
    {
      "round": 1,
      "name": "基线 v3.1.0 终态 + 新对抗样本",
      "rate": 84.81,
      "weightedRate": 83.31,
      "reactionMs": 269,
      "p95Ms": 4823,
      "adVisibleMs": 2084,
      "energyMWh": 0.13,
      "burstTierMWs": 10,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 2,
      "name": "+极速链（0ms 首扫）",
      "rate": 86.51,
      "weightedRate": 85.1,
      "reactionMs": 266,
      "p95Ms": 4811,
      "adVisibleMs": 2066,
      "energyMWh": 0.13,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 3,
      "name": "+陀螺仪诱导直断",
      "rate": 88.96,
      "weightedRate": 88.27,
      "reactionMs": 292,
      "p95Ms": 4804,
      "adVisibleMs": 2023,
      "energyMWh": 0.13,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 4,
      "name": "+链式跳转回弹",
      "rate": 90.09,
      "weightedRate": 89.47,
      "reactionMs": 302,
      "p95Ms": 4804,
      "adVisibleMs": 2009,
      "energyMWh": 0.13,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 5,
      "name": "+弹窗广告屏蔽 → 100%",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 304,
      "p95Ms": 4823,
      "adVisibleMs": 2014,
      "energyMWh": 0.13,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 6,
      "name": "+P0 预拦截回归",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.035,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 7,
      "name": "+守护模式回归",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.035,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 8,
      "name": "+功耗再治理（120ms 合并）",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.03,
      "burstTierMWs": 7.5,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 9,
      "name": "+冷启动全能力回归",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.03,
      "burstTierMWs": 7.5,
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
      "energyMWh": 0.03,
      "burstTierMWs": 7.5,
      "burstPreemptMWs": 1.6
    }
  ],
  "latest": {
    "rate": 100,
    "coldRate": 100,
    "sideloadRate": 100,
    "reactionMs": 34,
    "adVisibleMs": 0,
    "energyMWh": 0.03,
    "energyDropPct": 77,
    "burstDropPct": 84,
    "preemptShare": 0.9,
    "gyroAll": true,
    "missInvisible": 0,
    "backRecovered": 60,
    "dnsRecovered": 12,
    "popupAll": true,
    "deterministic": true,
    "guardZeroKills": true,
    "ximalayaOk": true,
    "ximalaya": {
      "r1Ms": 1600,
      "r3Ms": 847,
      "steadyMs": 34,
      "steadyVisibleMs": 0
    }
  }
};
