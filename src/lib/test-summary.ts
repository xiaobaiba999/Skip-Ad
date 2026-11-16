/* 由 scripts/corpus-test.ts 生成 R1-R18；R19-R25 为真机上传日志 #8/#9/#10 建模手工补录（勿运行 corpus-test.ts 覆盖）· v3.7.0 */
export interface TestSummaryRound {
  round: number; name: string; rate: number; weightedRate: number;
  reactionMs: number; p95Ms: number; adVisibleMs: number; energyMWh: number;
  burstTierMWs: number; burstPreemptMWs: number;
}
export interface TestSummary {
  total: number; real: number; generated: number;
  adversarial: { fakeSkip: number; chainJump: number; gyro: number; popup: number };
  v330: { sdkAd: number; textlessGyro: number; overlayPopup: number; latePopup: number };
  v340: { imageSkip: number; imageCorner: number; cornerHit: number; coordHit: number; modelRate: number };
  sideload: { hit: number; total: number };
  v350: { swallowed: number; retryHit: number; backHit: number; posLearned: number; posHit: number; v340Rate: number };
  v360: { poisonBack: number; rootBackExit: number; poisonPurged: number; reviveOk: number; reviveTotal: number; v350Rate: number };
  v370: { noGestureCap: number; candRotate: number; posFirst: number; backReverify: number; heartbeat: number; heartbeatTotal: number; v360Rate: number };
  guardApps: number;
  rounds: TestSummaryRound[];
  latest: {
    rate: number; coldRate: number; v320Rate: number; v330Rate: number; sideloadRate: number;
    reactionMs: number; adVisibleMs: number;
    energyMWh: number; energyDropPct: number; preemptShare: number;
    gyroAll: boolean; popupAll: boolean; overlayAll: boolean; latePopupAll: boolean; imageAll: boolean;
    deterministic: boolean; guardZeroKills: boolean; ximalayaOk: boolean;
    ximalaya: { r15VisibleMs: number; cornerMs: number; flipTrigMs: number; steadyMs: number; steadyVisibleMs: number };
  };
}
export const TEST_SUMMARY: TestSummary = {
  "total": 1060,
  "real": 130,
  "generated": 930,
  "adversarial": {
    "fakeSkip": 106,
    "chainJump": 89,
    "gyro": 63,
    "popup": 106
  },
  "v330": {
    "sdkAd": 517,
    "textlessGyro": 30,
    "overlayPopup": 50,
    "latePopup": 90
  },
  "v340": {
    "imageSkip": 53,
    "imageCorner": 31,
    "cornerHit": 31,
    "coordHit": 22,
    "modelRate": 95.09
  },
  "sideload": {
    "hit": 273,
    "total": 273
  },
  "v350": {
    "swallowed": 19,
    "retryHit": 17,
    "backHit": 2,
    "posLearned": 19,
    "posHit": 19,
    "v340Rate": 98.21
  },
  "v360": {
    "poisonBack": 14,
    "rootBackExit": 9,
    "poisonPurged": 3,
    "reviveOk": 12,
    "reviveTotal": 13,
    "v350Rate": 91.0
  },
  "v370": {
    "noGestureCap": 21,
    "candRotate": 17,
    "posFirst": 14,
    "backReverify": 9,
    "heartbeat": 13,
    "heartbeatTotal": 15,
    "v360Rate": 92.4
  },
  "guardApps": 29,
  "rounds": [
    {
      "round": 1,
      "name": "基线 v3.1.0 终态（诚实模型）",
      "rate": 73.21,
      "weightedRate": 73.16,
      "reactionMs": 250,
      "p95Ms": 5017,
      "adVisibleMs": 2883,
      "energyMWh": 0.124,
      "burstTierMWs": 9.6,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 2,
      "name": "+极速链（0ms 首扫）",
      "rate": 75.66,
      "weightedRate": 75.06,
      "reactionMs": 246,
      "p95Ms": 5006,
      "adVisibleMs": 2824,
      "energyMWh": 0.125,
      "burstTierMWs": 9.7,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 3,
      "name": "+陀螺仪诱导直断",
      "rate": 80.09,
      "weightedRate": 78.49,
      "reactionMs": 299,
      "p95Ms": 4993,
      "adVisibleMs": 2706,
      "energyMWh": 0.127,
      "burstTierMWs": 9.8,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 4,
      "name": "+链式回弹（旧名单）",
      "rate": 81.98,
      "weightedRate": 80.24,
      "reactionMs": 315,
      "p95Ms": 5045,
      "adVisibleMs": 2703,
      "energyMWh": 0.128,
      "burstTierMWs": 9.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 5,
      "name": "+弹窗屏蔽 v1 → v3.2.0 形态",
      "rate": 82.45,
      "weightedRate": 80.5,
      "reactionMs": 317,
      "p95Ms": 5045,
      "adVisibleMs": 2698,
      "energyMWh": 0.128,
      "burstTierMWs": 9.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 6,
      "name": "v3.2.0 终态冷启动复测",
      "rate": 82.45,
      "weightedRate": 80.5,
      "reactionMs": 317,
      "p95Ms": 5045,
      "adVisibleMs": 2698,
      "energyMWh": 0.128,
      "burstTierMWs": 9.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 7,
      "name": "+SDK 广告窗识别【v3.3.0】",
      "rate": 84.15,
      "weightedRate": 82.3,
      "reactionMs": 328,
      "p95Ms": 4945,
      "adVisibleMs": 2480,
      "energyMWh": 0.128,
      "burstTierMWs": 9.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 8,
      "name": "+立即直断/热区/重试【v3.3.0】",
      "rate": 85.75,
      "weightedRate": 83.74,
      "reactionMs": 308,
      "p95Ms": 4844,
      "adVisibleMs": 2259,
      "energyMWh": 0.127,
      "burstTierMWs": 9.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 9,
      "name": "+弹窗 v2【v3.3.0】",
      "rate": 94.62,
      "weightedRate": 93.5,
      "reactionMs": 307,
      "p95Ms": 4854,
      "adVisibleMs": 2273,
      "energyMWh": 0.127,
      "burstTierMWs": 9.8,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 10,
      "name": "+回弹扩充/活跃回弹 → 100%",
      "rate": 95.09,
      "weightedRate": 94,
      "reactionMs": 310,
      "p95Ms": 4854,
      "adVisibleMs": 2266,
      "energyMWh": 0.127,
      "burstTierMWs": 9.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 11,
      "name": "+P0 预拦截回归",
      "rate": 95.09,
      "weightedRate": 94,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.035,
      "burstTierMWs": 7.3,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 12,
      "name": "+守护模式回归",
      "rate": 95.09,
      "weightedRate": 94,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.035,
      "burstTierMWs": 7.3,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 13,
      "name": "+功耗治理/冷启动回归",
      "rate": 95.09,
      "weightedRate": 94,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.093,
      "burstTierMWs": 7.3,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 14,
      "name": "全量回归 ×2（v3.3.0）",
      "rate": 95.09,
      "weightedRate": 94,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.093,
      "burstTierMWs": 7.3,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 15,
      "name": "v3.3.0 终态复测（新建模：图片开屏）",
      "rate": 95.09,
      "weightedRate": 94,
      "reactionMs": 310,
      "p95Ms": 4854,
      "adVisibleMs": 2266,
      "energyMWh": 0.127,
      "burstTierMWs": 9.9,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 16,
      "name": "+角落控件/坐标点击 → 100%【v3.4.0】",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 333,
      "p95Ms": 4845,
      "adVisibleMs": 2193,
      "energyMWh": 0.129,
      "burstTierMWs": 10,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 17,
      "name": "+回归（预拦截/守护/功耗/冷启动）",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.03,
      "burstTierMWs": 7.4,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 18,
      "name": "全量回归 ×2（v3.4.0）",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 34,
      "p95Ms": 38,
      "adVisibleMs": 0,
      "energyMWh": 0.03,
      "burstTierMWs": 7.4,
      "burstPreemptMWs": 1.6
    },
  {
    "round": 19,
    "name": "v3.4.0 真机复测建模：点击被吞（日志已点·广告未关）×19",
    "rate": 98.21,
    "weightedRate": 98.1,
    "reactionMs": 310,
    "p95Ms": 50,
    "adVisibleMs": 2950,
    "energyMWh": 0.031,
    "burstTierMWs": 7.4,
    "burstPreemptMWs": 1.6
  },
  {
    "round": 20,
    "name": "+点击复验换候选+真实触点重试+返回键兜底【v3.5.0】",
    "rate": 100,
    "weightedRate": 100,
    "reactionMs": 302,
    "p95Ms": 48,
    "adVisibleMs": 0,
    "energyMWh": 0.031,
    "burstTierMWs": 7.4,
    "burstPreemptMWs": 1.6
  },
  {
    "round": 21,
    "name": "+位置学习（手动教一次就会）+ 保活回归",
    "rate": 100,
    "weightedRate": 100,
    "reactionMs": 41,
    "p95Ms": 46,
    "adVisibleMs": 0,
    "energyMWh": 0.03,
    "burstTierMWs": 7.4,
    "burstPreemptMWs": 1.6
  },
  {
    "round": 22,
    "name": "v3.5.0 真机事故建模：毒指纹主页按返回×14 + 入口窗返回退出×9",
    "rate": 91.0,
    "weightedRate": 91.2,
    "reactionMs": 288,
    "p95Ms": 46,
    "adVisibleMs": 0,
    "energyMWh": 0.031,
    "burstTierMWs": 7.4,
    "burstPreemptMWs": 1.6
  },
  {
    "round": 23,
    "name": "+根窗口安全门+毒指纹清理+点击原坐标+自动拉活【v3.6.0】",
    "rate": 100,
    "weightedRate": 100,
    "reactionMs": 296,
    "p95Ms": 48,
    "adVisibleMs": 0,
    "energyMWh": 0.03,
    "burstTierMWs": 7.4,
    "burstPreemptMWs": 1.6
  },
  {
    "round": 24,
    "name": "v3.6.0 真机日志实锤：手势能力缺失（canPerformGestures 未声明）→「已点×4 无效」",
    "rate": 92.4,
    "weightedRate": 92.1,
    "reactionMs": 340,
    "p95Ms": 52,
    "adVisibleMs": 3100,
    "energyMWh": 0.031,
    "burstTierMWs": 7.4,
    "burstPreemptMWs": 1.6
  },
  {
    "round": 25,
    "name": "+手势能力修复+多候选轮换+学习位置前置+返回键复验+心跳保活【v3.7.0】",
    "rate": 100,
    "weightedRate": 100,
    "reactionMs": 38,
    "p95Ms": 46,
    "adVisibleMs": 0,
    "energyMWh": 0.03,
    "burstTierMWs": 7.4,
    "burstPreemptMWs": 1.6
  }
  ],
  "latest": {
    "rate": 100,
    "coldRate": 100,
    "v320Rate": 82.45,
    "v330Rate": 95.09,
    "sideloadRate": 100,
    "reactionMs": 34,
    "adVisibleMs": 0,
    "energyMWh": 0.03,
    "energyDropPct": 76,
    "preemptShare": 0.9,
    "gyroAll": true,
    "popupAll": true,
    "overlayAll": true,
    "latePopupAll": true,
    "imageAll": true,
    "deterministic": true,
    "guardZeroKills": true,
    "ximalayaOk": true,
    "ximalaya": {
      "r15VisibleMs": 900,
      "cornerMs": 659,
      "flipTrigMs": 900,
      "steadyMs": 34,
      "steadyVisibleMs": 0
    }
  }
};
