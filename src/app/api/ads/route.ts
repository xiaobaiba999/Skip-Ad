import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import { ensureSeed } from "@/lib/ad-seed";

// GET /api/ads?type=splash&pick=1  —— 广告投放引擎：按权重随机取一条投放中的广告
// GET /api/ads                    —— 管理后台：返回全部广告列表
export async function GET(req: NextRequest) {
  try {
    await ensureSeed();
    const { searchParams } = new URL(req.url);
    const type = searchParams.get("type");
    const pick = searchParams.get("pick");

    if (type && pick) {
      const ads = await db.ad.findMany({ where: { type, active: true } });
      if (ads.length === 0) return NextResponse.json({ ad: null });
      // 加权随机
      const total = ads.reduce((s, a) => s + Math.max(1, a.weight), 0);
      let r = Math.random() * total;
      let chosen = ads[0];
      for (const a of ads) {
        r -= Math.max(1, a.weight);
        if (r <= 0) {
          chosen = a;
          break;
        }
      }
      return NextResponse.json({ ad: chosen });
    }

    const all = await db.ad.findMany({ orderBy: [{ type: "asc" }, { createdAt: "desc" }] });
    return NextResponse.json({ ads: all });
  } catch (e) {
    console.error("[ads GET]", e);
    return NextResponse.json({ error: "加载广告失败" }, { status: 500 });
  }
}

// POST /api/ads —— 新建广告计划
export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { name, type, title, subtitle, ctaText, linkUrl, theme, weight } = body ?? {};
    if (!name || !type || !title) {
      return NextResponse.json({ error: "名称、类型、标题为必填项" }, { status: 400 });
    }
    const ad = await db.ad.create({
      data: {
        name: String(name),
        type: type === "interstitial" ? "interstitial" : "splash",
        title: String(title),
        subtitle: String(subtitle || ""),
        ctaText: String(ctaText || "查看详情"),
        linkUrl: String(linkUrl || "default"),
        theme: String(theme || "ecommerce"),
        strategy: ["standard", "timer", "shake", "anyclick"].includes(body.strategy) ? body.strategy : "standard",
        weight: Math.min(10, Math.max(1, Number(weight) || 5)),
      },
    });
    return NextResponse.json({ ad });
  } catch (e) {
    console.error("[ads POST]", e);
    return NextResponse.json({ error: "创建广告失败" }, { status: 500 });
  }
}
