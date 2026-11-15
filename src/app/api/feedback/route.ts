import { NextRequest, NextResponse } from "next/server";
import { APP_VERSION, GITHUB_API, NEW_ISSUE_PAGE } from "@/lib/version";

export const dynamic = "force-dynamic";

/** 反馈类型 → Issue 标题前缀 */
const TYPES: Record<string, string> = {
  false_block: "误拦截",
  missed: "漏拦截",
  misclick: "误触观察有误",
  crash: "闪退/卡顿",
  suggestion: "功能建议",
  other: "其他",
};

function fallbackUrl(type: string, desc: string, contact: string, context: string) {
  const label = TYPES[type] ?? "其他";
  const title = `[反馈][${label}] ${desc.slice(0, 40)}`;
  const body = [
    "## 问题描述",
    desc,
    "",
    "## 环境信息",
    context,
    contact ? `联系方式：${contact}` : "",
    "",
    `— 由开屏卫士 v${APP_VERSION} 反馈系统自动创建`,
  ].filter(Boolean).join("\n");
  return `${NEW_ISSUE_PAGE}?title=${encodeURIComponent(title)}&body=${encodeURIComponent(body)}`;
}

/**
 * POST /api/feedback —— 用户问题反馈，同步创建 GitHub Issue
 * Body: { type, desc, contact?, context? }
 * 服务端持有 GITHUB_TOKEN（.env，不入库），客户端不接触令牌。
 * 未配置令牌 / 仓库不可达时返回 fallbackUrl，引导浏览器预填页提交。
 */
export async function POST(req: NextRequest) {
  let payload: { type?: string; desc?: string; contact?: string; context?: string };
  try {
    payload = await req.json();
  } catch {
    return NextResponse.json({ ok: false, error: "请求格式错误" }, { status: 400 });
  }

  const type = TYPES[payload.type ?? ""] ? payload.type! : "other";
  const desc = (payload.desc ?? "").trim().slice(0, 2000);
  const contact = (payload.contact ?? "").trim().slice(0, 100);
  const context = (payload.context ?? "").trim().slice(0, 1500);

  if (desc.length < 5) {
    return NextResponse.json({ ok: false, error: "请把问题描述得更具体一点（至少 5 个字）" }, { status: 400 });
  }

  const label = TYPES[type];
  const title = `[反馈][${label}] ${desc.slice(0, 40)}${desc.length > 40 ? "…" : ""}`;
  const body = [
    "## 问题描述",
    desc,
    "",
    "## 环境信息",
    context || "（用户未附加）",
    contact ? `联系方式：${contact}` : "",
    "",
    `— 由开屏卫士 v${APP_VERSION} 反馈系统自动创建`,
  ].filter(Boolean).join("\n");

  const token = process.env.GITHUB_TOKEN;
  if (!token) {
    return NextResponse.json({
      ok: false,
      error: "服务端未配置 GITHUB_TOKEN，请在浏览器中手动提交",
      fallbackUrl: fallbackUrl(type, desc, contact, context),
    });
  }

  const create = async (withLabels: boolean) =>
    fetch(`${GITHUB_API}/issues`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/vnd.github+json",
        "Content-Type": "application/json",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      body: JSON.stringify({
        title,
        body,
        ...(withLabels ? { labels: ["用户反馈", label] } : {}),
      }),
    });

  try {
    // 先带标签创建；仓库未开启标签权限（422）时降级为无标签
    let res = await create(true);
    if (res.status === 422) res = await create(false);
    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
      return NextResponse.json({
        ok: false,
        error: `GitHub 返回 ${res.status}${data.message ? `：${data.message}` : ""}`,
        fallbackUrl: fallbackUrl(type, desc, contact, context),
      }, { status: 502 });
    }
    return NextResponse.json({ ok: true, url: data.html_url, number: data.number });
  } catch {
    return NextResponse.json({
      ok: false,
      error: "网络请求失败，请稍后重试",
      fallbackUrl: fallbackUrl(type, desc, contact, context),
    }, { status: 502 });
  }
}
