/**
 * 多源规则统一调度器 · v5.4.0（纯函数库，不依赖 db，可在客户端使用）
 * ─────────────────────────────────────────────────────────────────────
 * 三类规则源统一进入同一调度体系：
 *  · preset 官方内置（兜底） · gkd GKD 社区订阅 · learned 本地学习
 *  · custom 用户自定义（最优先）
 *
 * 调度分公式（computeScore）：
 *   源基准 + 专属规则加成(25) + 质量加成(quality/4 ≤25) + 命中加成
 *   (min(hitCount,100)/10 ≤10) - 风险扣减(返回键类动作 -15)
 *   · cooling 状态规则降权运行（调度分 ×0.6）
 *   · priority > 0 视为手动锁定分（schedule 中直接采用，不再按公式计算）
 *
 * 冲突模型（detectConflicts）：
 *  · duplicate  同 appName+adType+action 且特征相似（包含关系）→ high
 *  · overlap    同 appName+adType 但 action 不同 → 特征相似 medium / 否则 low
 *  · contradict 动作矛盾对：守护类 guard_skip vs 激进直断类 → high；
 *                decline_tap vs 积极按钮点击类 → medium
 */

export type RuleSourceKind = "preset" | "gkd" | "learned" | "custom";

export interface SchedulerRule {
  id: string;
  name: string;
  source: RuleSourceKind;
  appName: string;
  adType: string;
  action: string;
  priority: number;
  enabled: boolean;
  quality: number;
  hitCount: number;
  pattern: string;
  status: string;
  sdkFamily?: string | null;
}

export interface ConflictItem {
  kind: "duplicate" | "overlap" | "contradict";
  severity: "high" | "medium" | "low";
  ruleAId: string;
  ruleBId: string;
  ruleAName: string;
  ruleBName: string;
  detail: string;
  suggestion: string;
}

/** 源基准权重：custom 40 > learned 30 > gkd 20 > preset 10（用户自定义最优先，社区订阅次之，内置兜底） */
export const SOURCE_BASE_WEIGHT: Record<RuleSourceKind, number> = {
  custom: 40,
  learned: 30,
  gkd: 20,
  preset: 10,
};

/** 依赖 GLOBAL_ACTION_BACK 的激进动作（温和性风险 → 扣减调度分） */
const RISKY_BACK_ACTIONS = new Set(["back_fallback", "gyro_block", "chain_block", "sdk_window_cut"]);

/** 温和动作（守护名单：拦广告但绝不终止服务） */
const GENTLE_ACTIONS = new Set(["guard_skip"]);
/** 激进动作（直断 / 进程级阻断 / 坐标点击） */
const AGGRESSIVE_ACTIONS = new Set([
  "instant_skip",
  "preempt_block",
  "chain_block",
  "gyro_block",
  "sdk_window_cut",
  "back_fallback",
  "dns_block",
  "coord_tap",
]);
/** 消极按钮代点（更新诱导弹窗：只点「以后再说」） */
const DECLINE_ACTIONS = new Set(["decline_tap"]);
/** 积极按钮点击类（与 decline_tap 矛盾：绝不能点「立即更新」） */
const POSITIVE_TAP_ACTIONS = new Set(["instant_skip", "preempt_block", "coord_tap"]);

/**
 * 调度分 = 源基准 + 专属规则加成(25) + 质量加成(quality/4 ≤25)
 *        + 命中加成(min(hitCount,100)/10 ≤10) - 风险扣减(返回键类动作 -15)
 * cooling 状态额外 ×0.6 降权。reason 为人类可读的分值构成说明。
 */
export function computeScore(r: SchedulerRule): { score: number; reason: string } {
  const parts: string[] = [];
  const base = SOURCE_BASE_WEIGHT[r.source] ?? 10;
  let score = base;
  parts.push(`源基准(${r.source}) ${base}`);

  const appScoped = r.appName.trim() !== "" && r.appName !== "*";
  if (appScoped) {
    score += 25;
    parts.push("专属规则 +25");
  }

  const qualityPts = Math.max(0, Math.min(100, r.quality)) / 4; // ≤25
  score += qualityPts;
  parts.push(`质量 +${qualityPts.toFixed(1)}（quality=${r.quality.toFixed(1)}，≤25）`);

  const hitPts = Math.min(Math.max(0, r.hitCount), 100) / 10; // ≤10
  score += hitPts;
  parts.push(`命中 +${hitPts.toFixed(1)}（hit=${r.hitCount}，≤10）`);

  if (RISKY_BACK_ACTIONS.has(r.action)) {
    score -= 15;
    parts.push(`返回键类风险(${r.action}) -15`);
  }

  if (r.status === "cooling") {
    score = score * 0.6;
    parts.push("冷却降权 ×0.6");
  }

  const final = Math.round(score * 10) / 10;
  return { score: final, reason: parts.join("，") };
}

function sameAppName(a: string, b: string): boolean {
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}

/**
 * 生效顺序：启用 + (appName 匹配：精确包名/名称 > "*") + 状态过滤（retired 不参与）
 * → 按调度分降序（priority > 0 视为手动锁定分直接采用）。
 * ctx.adType 提供时优先保留「同型 + any 型」规则（全空则回退不限型，避免空转）。
 */
export function schedule(rules: SchedulerRule[], ctx: { appName: string; adType?: string }): SchedulerRule[] {
  const enabled = rules.filter((r) => r.enabled && r.status !== "retired");
  let pool = enabled.filter((r) => r.appName === "*" || sameAppName(r.appName, ctx.appName));
  if (ctx.adType) {
    const typed = pool.filter((r) => r.adType === ctx.adType || r.adType === "any");
    if (typed.length > 0) pool = typed;
  }
  const scored = pool.map((r) => ({
    r,
    s: r.priority > 0 ? { score: r.priority, reason: `手动锁定调度分 ${r.priority}` } : computeScore(r),
  }));
  scored.sort((a, b) => {
    if (b.s.score !== a.s.score) return b.s.score - a.s.score;
    // 平分兜底：专属应用先于全局 → 源权重 → 命中数 → id 稳定序
    const globalA = a.r.appName === "*" ? 1 : 0;
    const globalB = b.r.appName === "*" ? 1 : 0;
    if (globalA !== globalB) return globalA - globalB;
    const w = (SOURCE_BASE_WEIGHT[b.r.source] ?? 0) - (SOURCE_BASE_WEIGHT[a.r.source] ?? 0);
    if (w !== 0) return w;
    if (b.r.hitCount !== a.r.hitCount) return b.r.hitCount - a.r.hitCount;
    return a.r.id.localeCompare(b.r.id);
  });
  return scored.map((x) => x.r);
}

// ═══════════════ 冲突检测 ═══════════════

function normalizePattern(p: string): string {
  return p.replace(/\s+/g, "").toLowerCase();
}

/** 特征相似：归一化相等，或存在包含关系（短边 ≥6 字符，避免碎片段误判） */
function patternSimilar(a: string, b: string): boolean {
  const na = normalizePattern(a);
  const nb = normalizePattern(b);
  if (!na || !nb) return false;
  if (na === nb) return true;
  const shorter = na.length < nb.length ? na : nb;
  const longer = na.length < nb.length ? nb : na;
  return shorter.length >= 6 && longer.includes(shorter);
}

function scopeLabel(appName: string): string {
  return appName === "*" ? "全局作用域" : `应用「${appName}」`;
}

function isContradictPair(a: string, b: string): "guard" | "decline" | null {
  if ((GENTLE_ACTIONS.has(a) && AGGRESSIVE_ACTIONS.has(b)) || (GENTLE_ACTIONS.has(b) && AGGRESSIVE_ACTIONS.has(a))) {
    return "guard";
  }
  if ((DECLINE_ACTIONS.has(a) && POSITIVE_TAP_ACTIONS.has(b)) || (DECLINE_ACTIONS.has(b) && POSITIVE_TAP_ACTIONS.has(a))) {
    return "decline";
  }
  return null;
}

/**
 * 冲突检测（仅比较启用中的规则）：
 *  · duplicate  同 appName+adType+action 且 pattern 相似（包含关系）
 *  · overlap    同 appName+adType 但 action 不同
 *  · contradict 动作矛盾对（guard_skip vs 激进直断 / decline_tap vs 积极点击）
 * 规则对按 id 字典序规范化为 A/B，保证检测结果可判重持久化。
 */
export function detectConflicts(rules: SchedulerRule[]): ConflictItem[] {
  const active = rules.filter((r) => r.enabled);
  const items: ConflictItem[] = [];
  for (let i = 0; i < active.length; i++) {
    for (let j = i + 1; j < active.length; j++) {
      const x = active[i];
      const y = active[j];
      if (x.appName !== y.appName || x.adType !== y.adType) continue;
      const [a, b] = x.id <= y.id ? [x, y] : [y, x];
      const similar = patternSimilar(a.pattern, b.pattern);

      if (a.action === b.action && similar) {
        items.push({
          kind: "duplicate",
          severity: "high",
          ruleAId: a.id,
          ruleBId: b.id,
          ruleAName: a.name,
          ruleBName: b.name,
          detail: `${scopeLabel(a.appName)} + ${a.adType} 下「${a.name}」与「${b.name}」动作相同（${a.action}）且匹配特征相似（包含关系）`,
          suggestion: "执行去重合并：保留高调度分规则，hitCount/missCount 累加（dedupeMerge）",
        });
        continue;
      }

      const contra = isContradictPair(a.action, b.action);
      if (contra) {
        items.push({
          kind: "contradict",
          severity: contra === "guard" ? "high" : "medium",
          ruleAId: a.id,
          ruleBId: b.id,
          ruleAName: a.name,
          ruleBName: b.name,
          detail:
            contra === "guard"
              ? `${scopeLabel(a.appName)} 下温和动作（${a.action}）与激进动作（${b.action}）同场：守护场景要求绝不终止服务，激进直断可能违反守护约束`
              : `${scopeLabel(a.appName)} 下消极按钮代点（${a.action}）与积极按钮点击（${b.action}）矛盾：更新类弹窗只应代点消极按钮，积极按钮（立即更新/立即领取）绝不能触碰`,
          suggestion:
            contra === "guard"
              ? "守护场景保留 guard_skip，为激进规则限定应用范围或加白名单，必要时人工确认"
              : "更新类弹窗只保留 decline_tap，禁用会点击积极按钮的规则",
        });
        continue;
      }

      if (a.action !== b.action) {
        items.push({
          kind: "overlap",
          severity: similar ? "medium" : "low",
          ruleAId: a.id,
          ruleBId: b.id,
          ruleAName: a.name,
          ruleBName: b.name,
          detail: `${scopeLabel(a.appName)} + ${a.adType} 下存在不同动作（${a.action} vs ${b.action}）${similar ? "且匹配特征相似，存在竞争出手" : "（特征不同，多为纵深防御分层）"}`,
          suggestion: similar
            ? "同目标多动作竞争：禁用低分项或合并语义，避免重复出手"
            : "纵深分层属正常设计：调度器已按分数排序执行，观察命中率后再精简",
        });
      }
    }
  }
  return items;
}

/**
 * 去重合并：duplicate 判据（同 appName+adType+action 且特征相似）保留高调度分者
 * （平分比 hitCount，再比稳定序），hitCount 累加、quality 取最大值；
 * missCount/falseCount 字段不在 SchedulerRule 契约内，由持久层（API 路由）负责累加。
 */
export function dedupeMerge(rules: SchedulerRule[]): {
  rules: SchedulerRule[];
  merged: { keptId: string; droppedId: string; reason: string }[];
} {
  const kept: SchedulerRule[] = [];
  const merged: { keptId: string; droppedId: string; reason: string }[] = [];

  for (const r of rules) {
    const dupIdx = kept.findIndex(
      (k) => k.appName === r.appName && k.adType === r.adType && k.action === r.action && patternSimilar(k.pattern, r.pattern)
    );
    if (dupIdx < 0) {
      kept.push({ ...r });
      continue;
    }
    const k = kept[dupIdx];
    const ks = computeScore(k);
    const rs = computeScore(r);
    const keepIncoming = rs.score > ks.score || (rs.score === ks.score && r.hitCount > k.hitCount);
    const winner = keepIncoming ? r : k;
    const dropped = keepIncoming ? k : r;
    const mergedHit = winner.hitCount + dropped.hitCount;
    kept[dupIdx] = { ...winner, hitCount: mergedHit, quality: Math.max(winner.quality, dropped.quality) };
    const tieNote =
      ks.score === rs.score ? `（调度分同为 ${ks.score.toFixed(1)}，按命中数 ${winner.hitCount} > ${dropped.hitCount} 定胜负）` : `（${Math.max(ks.score, rs.score).toFixed(1)} 分 > ${Math.min(ks.score, rs.score).toFixed(1)} 分）`;
    merged.push({
      keptId: winner.id,
      droppedId: dropped.id,
      reason: `duplicate（${r.appName}/${r.adType}/${r.action}）：保留「${winner.name}」${tieNote}，hitCount 累加为 ${mergedHit}；missCount/falseCount 由持久层累加`,
    });
  }

  return { rules: kept, merged };
}
