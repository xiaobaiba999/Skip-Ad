/* 由 scripts/corpus-test.ts 自动生成（勿手改）· 2026-11-16T06:42:03 UTC */
export interface TestSummaryRound {
  round: number; name: string; rate: number; weightedRate: number;
  reactionMs: number; p95Ms: number; adVisibleMs: number; energyMWh: number;
  burstTierMWs: number; burstPreemptMWs: number;
}
export interface TestSummary {
  total: number; real: number; generated: number;
  adversarial: { fakeSkip: number; chainJump: number; gyro: number; popup: number };
  v330: { sdkAd: number; textlessGyro: number; overlayPopup: number; latePopup: number };
  sideload: { hit: number; total: number };
  guardApps: number;
  rounds: TestSummaryRound[];
  latest: {
    rate: number; coldRate: number; v320Rate: number; sideloadRate: number; reactionMs: number; adVisibleMs: number;
    energyMWh: number; energyDropPct: number; preemptShare: number;
    gyroAll: boolean; popupAll: boolean; overlayAll: boolean; latePopupAll: boolean;
    sdkCut: number; hotZoneCut: number; gyroCut: number; rebound: number; autoDismiss: number;
    deterministic: boolean; guardZeroKills: boolean; ximalayaOk: boolean;
    ximalaya: { r1Ms: number; sdkCutMs: number; fullMs: number; steadyMs: number; steadyVisibleMs: number; flipTrigMs: number };
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
    "popup": 106
  },
  "v330": {
    "sdkAd": 518,
    "textlessGyro": 30,
    "overlayPopup": 50,
    "latePopup": 90
  },
  "sideload": {
    "hit": 273,
    "total": 273
  },
  "guardApps": 29,
  "rounds": [
    {
      "round": 1,
      "name": "基线 v3.1.0 终态（诚实模型）",
      "rate": 77.55,
      "weightedRate": 78.66,
      "reactionMs": 250,
      "p95Ms": 5008,
      "adVisibleMs": 2872,
      "energyMWh": 0.127,
      "burstTierMWs": 9.8,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 2,
      "name": "+极速链（0ms 首扫）",
      "rate": 80,
      "weightedRate": 80.57,
      "reactionMs": 247,
      "p95Ms": 5006,
      "adVisibleMs": 2817,
      "energyMWh": 0.128,
      "burstTierMWs": 9.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 3,
      "name": "+陀螺仪诱导直断",
      "rate": 84.43,
      "weightedRate": 83.99,
      "reactionMs": 297,
      "p95Ms": 4993,
      "adVisibleMs": 2705,
      "energyMWh": 0.13,
      "burstTierMWs": 10,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 4,
      "name": "+链式回弹（旧名单）",
      "rate": 86.32,
      "weightedRate": 85.74,
      "reactionMs": 312,
      "p95Ms": 5045,
      "adVisibleMs": 2702,
      "energyMWh": 0.13,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 5,
      "name": "+弹窗屏蔽 v1 → v3.2.0 形态",
      "rate": 86.79,
      "weightedRate": 86.01,
      "reactionMs": 314,
      "p95Ms": 5045,
      "adVisibleMs": 2697,
      "energyMWh": 0.13,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 6,
      "name": "v3.2.0 终态冷启动复测",
      "rate": 86.79,
      "weightedRate": 86.01,
      "reactionMs": 314,
      "p95Ms": 5045,
      "adVisibleMs": 2697,
      "energyMWh": 0.13,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 7,
      "name": "+SDK 广告窗识别【v3.3.0】",
      "rate": 88.49,
      "weightedRate": 87.81,
      "reactionMs": 324,
      "p95Ms": 4945,
      "adVisibleMs": 2490,
      "energyMWh": 0.131,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 8,
      "name": "+立即直断/热区/重试【v3.3.0】",
      "rate": 90.09,
      "weightedRate": 89.24,
      "reactionMs": 308,
      "p95Ms": 4842,
      "adVisibleMs": 2269,
      "energyMWh": 0.13,
      "burstTierMWs": 10.1,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 9,
      "name": "+弹窗 v2【v3.3.0】",
      "rate": 99.62,
      "weightedRate": 99.8,
      "reactionMs": 308,
      "p95Ms": 4848,
      "adVisibleMs": 2280,
      "energyMWh": 0.13,
      "burstTierMWs": 10,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 10,
      "name": "+回弹扩充/活跃回弹 → 100%",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 310,
      "p95Ms": 4848,
      "adVisibleMs": 2274,
      "energyMWh": 0.13,
      "burstTierMWs": 10,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 11,
      "name": "+P0 预拦截回归",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.035,
      "burstTierMWs": 10,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 12,
      "name": "+守护模式回归",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.035,
      "burstTierMWs": 10,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 13,
      "name": "+功耗治理/冷启动回归",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.096,
      "burstTierMWs": 7.5,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 14,
      "name": "全量回归 ×2",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.096,
      "burstTierMWs": 7.5,
      "burstPreemptMWs": 1.6
    }
  ],
  "latest": {
    "rate": 100,
    "coldRate": 100,
    "v320Rate": 86.79,
    "sideloadRate": 100,
    "reactionMs": 34,
    "adVisibleMs": 0,
    "energyMWh": 0.096,
    "energyDropPct": 24,
    "preemptShare": 0,
    "gyroAll": true,
    "popupAll": true,
    "overlayAll": true,
    "latePopupAll": true,
    "sdkCut": 0,
    "hotZoneCut": 53,
    "gyroCut": 89,
    "rebound": 4,
    "autoDismiss": 0,
    "deterministic": true,
    "guardZeroKills": true,
    "ximalayaOk": true,
    "ximalaya": {
      "r1Ms": 900,
      "sdkCutMs": 504,
      "fullMs": 504,
      "steadyMs": 34,
      "steadyVisibleMs": 0,
      "flipTrigMs": 900
    }
  }
};
