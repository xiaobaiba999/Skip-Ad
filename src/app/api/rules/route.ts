import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import { ensureRulesSeed, matchTypeLabel } from "@/lib/preset-rules";

export const dynamic = "force-dynamic";

/** appName 作用域展示标签（v5.4.0 多源统一调度） */
function appNameScope(appName: string): string {
  if (appName === "*") return "全局";
  if (appName.startsWith("sdk:")) return `SDK 家族（${appName.slice(4)}）`;
  return "应用专属";
}

/** GET /api/rules —— 规则库（预置 + GKD 订阅 + 学习 + 自定义）+ 多源统计 */
export async function GET() {
  try {
    await ensureRulesSeed();
    const rules = await db.rule.findMany({
      orderBy: [{ source: "desc" }, { hitCount: "desc" }, { createdAt: "asc" }],
    });

    const bySource: Record<string, number> = { preset: 0, gkd: 0, learned: 0, custom: 0 };
    const byStatus: Record<string, number> = {};
    let qualitySum = 0;
    let qualityCount = 0; // 预置规则不参与质量评分（未建模），均值只统计可评分源
    for (const r of rules) {
      bySource[r.source] = (bySource[r.source] ?? 0) + 1;
      byStatus[r.status] = (byStatus[r.status] ?? 0) + 1;
      if (r.source !== "preset") {
        qualitySum += r.quality;
        qualityCount += 1;
      }
    }

    return NextResponse.json({
      rules: rules.map((r) => ({
        id: r.id,
        appName: r.appName,
        appCategory: r.appCategory,
        adType: r.adType,
        matchType: r.matchType,
        matchLabel: matchTypeLabel(r.matchType),
        pattern: r.pattern,
        action: r.action,
        source: r.source,
        confidence: r.confidence,
        hitCount: r.hitCount,
        enabled: r.enabled,
        lastHitAt: r.lastHitAt?.getTime() ?? null,
        // ── v5.4.0 多源统一调度新增字段 ──
        priority: r.priority,
        quality: r.quality,
        status: r.status,
        sdkFamily: r.sdkFamily,
        generalizedFrom: r.generalizedFrom,
        gkdRef: r.gkdRef,
        missCount: r.missCount,
        falseCount: r.falseCount,
        version: r.version,
        appNameScope: appNameScope(r.appName),
      })),
      stats: {
        bySource,
        byStatus,
        avgQuality: qualityCount ? Math.round((qualitySum / qualityCount) * 10) / 10 : 0,
      },
    });
  } catch (e) {
    console.error("[rules GET]", e);
    return NextResponse.json({ error: "规则库加载失败" }, { status: 500 });
  }
}

/**
 * PATCH /api/rules —— 单条启停 / 单条优先级调整 / 批量启停
 * Body: { id, enabled }                     兼容旧版：单条启停
 *     | { id, priority }                    优先级直接存库并视为手动锁定分
 *     | { id, enabled?, priority? }         组合更新
 *     | { op: "batch", ids: string[], enabled: boolean }  批量启停
 */
export async function PATCH(req: NextRequest) {
  try {
    const b = await req.json();

    if (b?.op === "batch") {
      const ids = Array.isArray(b.ids) ? b.ids.filter((x: unknown): x is string => typeof x === "string" && x !== "") : [];
      if (!ids.length || typeof b.enabled !== "boolean") {
        return NextResponse.json({ error: "参数非法：batch 需要 ids[] 与 enabled" }, { status: 400 });
      }
      const r = await db.rule.updateMany({ where: { id: { in: ids } }, data: { enabled: b.enabled } });
      return NextResponse.json({ ok: true, updated: r.count, enabled: b.enabled });
    }

    if (typeof b?.id !== "string" || !b.id) {
      return NextResponse.json({ error: "参数非法：缺少 id" }, { status: 400 });
    }
    const data: { enabled?: boolean; priority?: number } = {};
    if (typeof b.enabled === "boolean") data.enabled = b.enabled;
    if (typeof b.priority === "number" && Number.isFinite(b.priority)) {
      data.priority = Math.round(b.priority); // 手动锁定调度分
    }
    if (!Object.keys(data).length) {
      return NextResponse.json({ error: "参数非法：需要 enabled 或 priority 字段" }, { status: 400 });
    }
    const r = await db.rule.update({ where: { id: b.id }, data });
    return NextResponse.json({ ok: true, id: r.id, enabled: r.enabled, priority: r.priority });
  } catch (e) {
    console.error("[rules PATCH]", e);
    return NextResponse.json({ error: "更新失败" }, { status: 500 });
  }
}

/**
 * POST /api/rules —— 新建规则
 * Body: { name?, appName, appCategory?, adType, matchType?, pattern, action, source? }
 * 默认 custom 源；name 缺省「自定义·adType」（Rule 表无 name 列，name 仅回显不落库）。
 */
export async function POST(req: NextRequest) {
  try {
    const b = await req.json();
    const appName = typeof b?.appName === "string" && b.appName.trim() ? b.appName.trim() : "";
    const adType = typeof b?.adType === "string" && b.adType.trim() ? b.adType.trim() : "";
    const pattern = typeof b?.pattern === "string" && b.pattern.trim() ? b.pattern.trim() : "";
    const action = typeof b?.action === "string" && b.action.trim() ? b.action.trim() : "";
    if (!appName || !adType || !pattern || !action) {
      return NextResponse.json({ error: "参数非法：appName/adType/pattern/action 均为必填" }, { status: 400 });
    }
    const source =
      typeof b?.source === "string" && ["custom", "gkd", "learned"].includes(b.source) ? b.source : "custom";
    const displayName =
      typeof b?.name === "string" && b.name.trim() ? b.name.trim() : `自定义·${adType}`;
    // matchType 缺省：选择器样式（[xxx="yyy"]）自动归为 GKD 快照选择器，否则按文本特征
    const matchType =
      typeof b?.matchType === "string" && b.matchType.trim() ? b.matchType.trim() : pattern.trim().startsWith("[") ? "gkd-selector" : "text";

    const r = await db.rule.create({
      data: {
        appName,
        appCategory: typeof b?.appCategory === "string" && b.appCategory.trim() ? b.appCategory.trim() : "通用",
        adType,
        matchType,
        pattern,
        action,
        source,
        confidence: 0.8,
        priority: 0, // 0 = 按调度公式计算
        status: "active",
      },
    });
    return NextResponse.json({ ok: true, id: r.id, name: displayName, enabled: r.enabled });
  } catch (e) {
    console.error("[rules POST]", e);
    return NextResponse.json({ error: "创建失败" }, { status: 500 });
  }
}

/**
 * PUT /api/rules —— 编辑规则（version 自增）
 * Body: { id, pattern?, action?, adType?, appName? }
 * name 不可编辑：Rule 表无 name 字段（与 POST 的回显策略一致）。
 */
export async function PUT(req: NextRequest) {
  try {
    const b = await req.json();
    if (typeof b?.id !== "string" || !b.id) {
      return NextResponse.json({ error: "参数非法：缺少 id" }, { status: 400 });
    }
    const data: { pattern?: string; action?: string; adType?: string; appName?: string } = {};
    if (typeof b.pattern === "string" && b.pattern.trim()) data.pattern = b.pattern.trim();
    if (typeof b.action === "string" && b.action.trim()) data.action = b.action.trim();
    if (typeof b.adType === "string" && b.adType.trim()) data.adType = b.adType.trim();
    if (typeof b.appName === "string" && b.appName.trim()) data.appName = b.appName.trim();
    if (!Object.keys(data).length) {
      return NextResponse.json({ error: "参数非法：至少提供 pattern/action/adType/appName 之一（name 字段表结构不支持）" }, { status: 400 });
    }
    const r = await db.rule.update({
      where: { id: b.id },
      data: { ...data, version: { increment: 1 }, updatedAt: new Date() },
    });
    return NextResponse.json({ ok: true, id: r.id, version: r.version });
  } catch (e) {
    console.error("[rules PUT]", e);
    return NextResponse.json({ error: "编辑失败" }, { status: 500 });
  }
}

/** DELETE /api/rules —— 删除规则（仅 gkd/learned/custom；preset 拒绝 403） */
export async function DELETE(req: NextRequest) {
  try {
    const b = await req.json();
    if (typeof b?.id !== "string" || !b.id) {
      return NextResponse.json({ error: "参数非法：缺少 id" }, { status: 400 });
    }
    const rule = await db.rule.findUnique({ where: { id: b.id } });
    if (!rule) {
      return NextResponse.json({ error: "规则不存在" }, { status: 404 });
    }
    if (rule.source === "preset") {
      return NextResponse.json({ error: "预置规则不允许删除（只可停用）" }, { status: 403 });
    }
    await db.rule.delete({ where: { id: b.id } });
    return NextResponse.json({ ok: true, id: rule.id, source: rule.source });
  } catch (e) {
    console.error("[rules DELETE]", e);
    return NextResponse.json({ error: "删除失败" }, { status: 500 });
  }
}
