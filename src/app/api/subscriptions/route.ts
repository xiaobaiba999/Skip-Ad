import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import { parseGkdSubscription, serializeDslRule } from "@/lib/rule-dsl";
import type { DslRule, GkdRuleMeta } from "@/lib/rule-dsl";

export const dynamic = "force-dynamic";

/** Prisma 订阅记录类型（序列化时时间戳转毫秒） */
type Subscription = NonNullable<Awaited<ReturnType<typeof db.subscription.findFirst>>>;

function serializeSub(s: Subscription) {
  return {
    id: s.id,
    name: s.name,
    url: s.url,
    kind: s.kind,
    version: s.version,
    author: s.author,
    ruleCount: s.ruleCount,
    enabled: s.enabled,
    status: s.status,
    message: s.message,
    lastSyncAt: s.lastSyncAt?.getTime() ?? null,
    createdAt: s.createdAt.getTime(),
  };
}

/** 演示订阅种子（不真实联网；真实环境每 6h 拉取一次） */
const DEMO_SUBSCRIPTIONS = [
  {
    name: "GKD 官方订阅",
    url: "https://gkd.li/gkd32official",
    kind: "official",
    status: "ok",
    message: "演示数据：真实环境每 6h 拉取一次",
    ruleCount: 3,
  },
  {
    name: "GKD 社区精选",
    url: "https://gkd.li/skipad-demo",
    kind: "gkd",
    status: "parsed",
    message: "演示数据：社区精选跳过规则（粘贴订阅内容可触发真实解析导入）",
    ruleCount: 5,
  },
] as const;

/**
 * GET /api/subscriptions —— 订阅源列表
 * 表为空时播种 2 条演示订阅（upsert 幂等，不真实联网）。
 */
export async function GET() {
  try {
    let subs = await db.subscription.findMany({ orderBy: { createdAt: "desc" } });
    if (!subs.length) {
      for (const d of DEMO_SUBSCRIPTIONS) {
        await db.subscription.upsert({ where: { url: d.url }, create: { ...d }, update: {} });
      }
      subs = await db.subscription.findMany({ orderBy: { createdAt: "desc" } });
    }
    return NextResponse.json({ subscriptions: subs.map(serializeSub) });
  } catch (e) {
    console.error("[subscriptions GET]", e);
    return NextResponse.json({ error: "订阅列表加载失败" }, { status: 500 });
  }
}

/**
 * POST /api/subscriptions —— 登记订阅源 / 导入 GKD 订阅内容
 * Body: { name?, url?, content? }
 *  · content 存在：parseGkdSubscription 解析 → 规则批量入库（source=gkd，
 *    gkdRef=订阅名#组名，confidence 0.9）→ 更新/创建 subscription 记录
 *  · 无 content：仅登记元数据（status ok）
 */
export async function POST(req: NextRequest) {
  try {
    const b = await req.json();
    const name = typeof b?.name === "string" && b.name.trim() ? b.name.trim() : null;
    const url = typeof b?.url === "string" && b.url.trim() ? b.url.trim() : null;
    const content = typeof b?.content === "string" && b.content.trim() ? b.content : null;
    if (!name && !url && !content) {
      return NextResponse.json({ error: "参数非法：至少提供 name / url / content 之一" }, { status: 400 });
    }

    if (content) {
      const subName = name ?? "GKD 本地导入";
      const parsed = parseGkdSubscription(content, subName);

      if (parsed.rules.length) {
        // 批量写入规则库：pattern 用 DSL 行格式序列化（可被 compileDslLine 复原）
        await db.rule.createMany({
          data: parsed.rules.map((r) => {
            const meta = r as DslRule & GkdRuleMeta;
            return {
              appName: r.appName,
              appCategory: "通用",
              adType: r.adType,
              matchType: "gkd-selector",
              pattern: serializeDslRule(r),
              action: r.action,
              source: "gkd",
              confidence: 0.9,
              gkdRef: meta.gkdRef ?? `${subName}#未分组`,
              priority: 0, // 0 = 按调度公式计算
              status: "active",
              enabled: true,
            };
          }),
        });
      }

      const subUrl = url ?? `local:${subName}`;
      const message = `解析完成：导入 ${parsed.ruleCount} 条规则（${parsed.groupCount} 组）；警告 ${parsed.warnings.length} 条；错误 ${parsed.errors.length} 条`;
      const subscription = await db.subscription.upsert({
        where: { url: subUrl },
        create: {
          name: subName,
          url: subUrl,
          kind: url ? "gkd" : "custom",
          ruleCount: parsed.ruleCount,
          status: "parsed",
          message,
          lastSyncAt: new Date(),
        },
        update: { ruleCount: parsed.ruleCount, status: "parsed", message, lastSyncAt: new Date() },
      });
      return NextResponse.json({
        ok: parsed.rules.length > 0,
        imported: parsed.ruleCount,
        warnings: parsed.warnings,
        errors: parsed.errors,
        subscription: serializeSub(subscription),
      });
    }

    // 仅登记元数据（不解析）
    const subName = name ?? url ?? "未命名订阅";
    const subUrl = url ?? `local:${subName}`;
    const subscription = await db.subscription.upsert({
      where: { url: subUrl },
      create: {
        name: subName,
        url: subUrl,
        kind: url ? "gkd" : "custom",
        status: "ok",
        message: "已登记订阅源（未解析规则；粘贴订阅内容可触发导入）",
        ruleCount: 0,
      },
      update: { name: subName, status: "ok" },
    });
    return NextResponse.json({ ok: true, imported: 0, warnings: [], errors: [], subscription: serializeSub(subscription) });
  } catch (e) {
    console.error("[subscriptions POST]", e);
    return NextResponse.json({ error: "订阅处理失败" }, { status: 500 });
  }
}

/**
 * DELETE /api/subscriptions —— 删除订阅及其导入的 gkd 源规则
 * Body: { id }。SQLite/Prisma 不支持 startsWith 过滤，读出后按 gkdRef
 * 前缀（订阅名#）JS 过滤再批量删除。
 */
export async function DELETE(req: NextRequest) {
  try {
    const b = await req.json();
    if (typeof b?.id !== "string" || !b.id) {
      return NextResponse.json({ error: "参数非法：缺少 id" }, { status: 400 });
    }
    const sub = await db.subscription.findUnique({ where: { id: b.id } });
    if (!sub) {
      return NextResponse.json({ error: "订阅不存在" }, { status: 404 });
    }
    const gkdRules = await db.rule.findMany({ where: { source: "gkd" } });
    const prefix = `${sub.name}#`;
    const ids = gkdRules.filter((r) => (r.gkdRef ?? "").startsWith(prefix)).map((r) => r.id);
    if (ids.length) {
      await db.rule.deleteMany({ where: { id: { in: ids } } });
    }
    await db.subscription.delete({ where: { id: sub.id } });
    return NextResponse.json({ ok: true, id: sub.id, removedRules: ids.length });
  } catch (e) {
    console.error("[subscriptions DELETE]", e);
    return NextResponse.json({ error: "删除失败" }, { status: 500 });
  }
}
