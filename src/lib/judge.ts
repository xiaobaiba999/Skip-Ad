/**
 * 精准判断引擎（2026.11 版）
 * ─────────────────────────────────────────────────────────────
 * 目标：区分「广告诱导跳转」与「用户主动操作」。
 * 设计依据（2026-11 调研）：
 *  1. 澎湃新闻实测 20 款 App：合法“摇一摇”需加速度≥15m/s²、转角≥35°、
 *     持续≥3s（2023 行业标准 / 2025-07《摇一摇广告触发行为安全要求》）；
 *     违规广告把阈值调到“拿起手机即跳转”——传感器触发一律按广告行为处理。
 *  2. “点击任意位置即跳转”的全屏热区：点击落在可见素材之外 → 误触设计。
 *  3. 误触/程序化点击的指纹：按压 <40ms、无移动轨迹、展示后 <150ms 即点、
 *     无 pointer 事件；真人触摸按压 60~300ms 且带轻微轨迹抖动。
 *  4. 学习画像动态调整：主题停留偏好（engage/quickback）、误判纠正
 *     （≥2 次纠正 → 提高拦截门槛，放宽快速点击）。
 *
 * 评分制：≥60 判广告行为；已学习纠正 ≥2 次时阈值升至 74（防误伤）。
 */

export type JumpTrigger =
  | "timer" // 倒计时归零自动跳转
  | "shake" // 传感器触发
  | "tap" // 点击广告素材区
  | "hotzone" // 点击落在隐藏热区（素材之外）
  | "content" // 内容区导航（文章等）
  | "landing-auto" // 落地页倒计时自动唤起外部应用（二跳）
  | "landing-manual"; // 用户点击「立即打开」按钮

export interface JumpSignals {
  pressMs?: number; // 按压时长（pointerdown→up）
  movePx?: number; // 按压期间轨迹长度
  reactionMs?: number; // 广告展示 → 点击的反应时间
  pointerType?: string; // mouse/touch/none
}

export interface JudgeReason {
  label: string;
  weight: number;
}

export interface Judgment {
  isAd: boolean;
  score: number;
  reasons: JudgeReason[];
  headline: string;
}

export interface LearnRules {
  themeBias: Record<string, number>; // -20（放行倾向）~ +20（拦截倾向）
  relaxQuickTap: boolean;
  autoSkipDelayMs: number | null; // 学习到的自动跳过时机
  samples: number;
}

export interface LearnStats {
  blocked: number;
  allowed: number;
  wouldBlock: number;
  corrections: number;
  precision: number; // 0~1
}

export interface LearnSummary {
  stats: LearnStats;
  rules: LearnRules;
}

export interface LearnEventItem {
  id: string;
  ts: number;
  kind: string;
  trigger?: string | null;
  action?: string | null;
  score?: number | null;
  reason0?: string | null;
  adName?: string | null;
  theme?: string | null;
  dwellMs?: number | null;
}

export const BLOCK_THRESHOLD = 60;
export const RELAXED_THRESHOLD = 74;

export const TRIGGER_LABEL: Record<JumpTrigger, string> = {
  timer: "倒计时自动跳转",
  shake: "传感器触发",
  tap: "点击广告素材",
  hotzone: "隐藏热区点击",
  content: "内容区导航",
  "landing-auto": "二跳自动唤起",
  "landing-manual": "点击「立即打开」",
};

export function judgeJump(
  trigger: JumpTrigger,
  signals: JumpSignals = {},
  profile?: LearnRules | null,
  theme?: string,
): Judgment {
  const reasons: JudgeReason[] = [];
  let score = 0;
  const add = (label: string, weight: number) => {
    score += weight;
    reasons.push({ label, weight });
  };
  const { pressMs, movePx, reactionMs, pointerType } = signals;

  switch (trigger) {
    case "content":
      // 内容区导航：语义上就是用户主动操作，仅记录指纹供画像
      add("内容区导航，默认用户意图", -50);
      break;
    case "timer":
      add("倒计时归零自动跳转，无任何用户输入", 88);
      break;
    case "shake":
      add("传感器触发（摇晃/倾斜），非点击行为", 85);
      break;
    case "landing-auto":
      add("落地页倒计时自动唤起外部应用（二跳）", 88);
      break;
    case "hotzone":
      add("点击落在隐藏热区（可见素材之外）", 60);
      add("点击广告素材区", 8);
      break;
    case "landing-manual":
      add("用户点击「立即打开」按钮（明确意图）", 12);
      break;
    case "tap":
      add("点击广告素材区（语义模糊，靠行为指纹裁决）", 22);
      break;
  }

  /* ── 行为指纹（tap / hotzone / landing-manual 适用） ── */
  if (pressMs !== undefined) {
    if (pressMs < 40) add(`按压 ${pressMs}ms（<40ms，快于人手，疑似误触/程序化）`, 25);
    else if (pressMs > 350) add(`按压 ${pressMs}ms，沉稳长按，符合主动兴趣`, -12);
    else add(`按压 ${pressMs}ms，落在人手正常区间`, -5);
  }
  if (movePx !== undefined) {
    if (movePx === 0) add("按压期间无移动轨迹（瞬移点击）", 15);
    else if (movePx < 6) add(`轨迹抖动 ${movePx}px，符合真人触摸`, -8);
    else add(`拖动轨迹 ${movePx}px，主动滑动特征`, -15);
  }
  if (reactionMs !== undefined) {
    if (reactionMs < 150) add(`展示后 ${reactionMs}ms 即点击，快于人类反应`, 20);
    else if (reactionMs > 600) add(`观察 ${(reactionMs / 1000).toFixed(1)}s 后才点击，主动兴趣`, -15);
  }
  if (pointerType === "none") add("无 pointer 事件（纯程序点击）", 30);

  /* ── 学习画像调整 ── */
  if (profile && theme) {
    const bias = profile.themeBias?.[theme] ?? 0;
    if (bias !== 0) {
      add(`学习偏好：该主题历史停留反馈（${bias > 0 ? "快速返回→倾向拦截" : "长时间停留→倾向放行"}）`, bias);
    }
  }

  const threshold = profile?.relaxQuickTap ? RELAXED_THRESHOLD : BLOCK_THRESHOLD;
  const isAd = score >= threshold && trigger !== "content";
  return {
    isAd,
    score: Math.max(0, Math.min(100, score)),
    reasons,
    headline: isAd ? "已拦截：广告诱导跳转" : "放行：用户主动操作",
  };
}

export function topReason(j: Judgment): string {
  const pos = j.reasons.filter((r) => r.weight > 0).sort((a, b) => b.weight - a.weight);
  return pos[0]?.label ?? j.reasons[0]?.label ?? "—";
}
