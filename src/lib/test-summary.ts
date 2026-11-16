/* 由 scripts/corpus-test.ts 自动生成（勿手改）· 2026-11-16T03:03:41 UTC */
export interface TestSummary {
  total: number; real: number; generated: number;
  sideload: { hit: number; total: number };
  rounds: { round: number; rate: number; weightedRate: number; reactionMs: number; p95Ms: number; energyMWh: number }[];
  latest: { rate: number; sideloadRate: number; reactionMs: number; energyMWh: number; energyDropPct: number; missInvisible: number };
}
export const TEST_SUMMARY: TestSummary = {
  "total": 1010,
  "real": 130,
  "generated": 880,
  "sideload": {
    "hit": 234,
    "total": 257
  },
  "rounds": [
    {
      "round": 1,
      "rate": 63.76,
      "weightedRate": 63.65,
      "reactionMs": 280,
      "p95Ms": 3318,
      "energyMWh": 0.41
    },
    {
      "round": 2,
      "rate": 91.58,
      "weightedRate": 92.7,
      "reactionMs": 294,
      "p95Ms": 4963,
      "energyMWh": 0.82
    },
    {
      "round": 3,
      "rate": 91.58,
      "weightedRate": 92.7,
      "reactionMs": 249,
      "p95Ms": 4913,
      "energyMWh": 0.28
    }
  ],
  "latest": {
    "rate": 91.58,
    "sideloadRate": 91.1,
    "reactionMs": 249,
    "energyMWh": 0.28,
    "energyDropPct": 32,
    "missInvisible": 85
  }
};
