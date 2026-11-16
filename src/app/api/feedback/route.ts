import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";
import { createIssue } from "@/lib/github";
import { APP_VERSION } from "@/lib/version";

export const dynamic = "force-dynamic";

/** 反馈类型 → Issue 标题前缀与标签 */
const TYPES: Record<string, { label: string; tag: string }> = {
  missed: { label: "漏拦截", tag: "漏拦截" },
  false_block: { label: "误拦截", tag: "误拦截" },
  shake_leak: { label: "摇一摇仍跳转", tag: "摇一摇" },
  crash: { label: "闪退/卡顿", tag: "稳定性" },
  suggestion: { label: "功能建议", tag: "建议" },
  other: { label: "其他", tag: "其他" },
  engine_log: { label: "引擎日志", tag: "引擎日志" },
};

/**
 * POST /api/feedback —— 问题反馈（内置加密通道）
 * Body: { type, desc, appName?, context? }
 * · 令牌内置于服务端（credentials.ts 混淆存储），前端零配置零输入；
 * · 全部反馈直达 xiaobaiba999/Skip-Ad 的 Issues；
 * · 提交结果留痕 FeedbackIssue，可在学习中心追溯。
 */
export async function POST(req: NextRequest) {
  let payload: { type?: string; desc?: string; appName?: string; context?: string };
  try {
    payload = await req.json();
  } catch {
    return NextResponse.json({ ok: false, error: "请求格式错误" }, { status: 400 });
  }

  const type = TYPES[payload.type ?? ""] ? payload.type! : "other";
  const isEngineLog = type === "engine_log";
  const desc = (payload.desc ?? "").trim().slice(0, 2000);
  const appName = (payload.appName ?? "").trim().slice(0, 40);
  // 引擎日志上下文可达 8000 字（日志明细 + 学习规则）；普通反馈 1200 字
  const context = (payload.context ?? "").trim().slice(0, isEngineLog ? 8000 : 1200);

  if (desc.length < 5 && type !== "engine_log") {
    return NextResponse.json({ ok: false, error: "请把问题描述得更具体一点（至少 5 个字）" }, { status: 400 });
  }

  const meta = TYPES[type];
  // 引擎日志：手动上传，标题带时间戳供归档检索
  const title = type === "engine_log"
    ? `[引擎日志][v${APP_VERSION}] ${new Date().toLocaleString("zh-CN", { hour12: false })}（手动上传）`
    : `[反馈][${meta.label}]${appName ? ` ${appName}：` : " "}${desc.slice(0, 40)}${desc.length > 40 ? "…" : ""}`;
  const body = type === "engine_log"
    ? [
        "## 引擎日志（手动上传，供下版迭代参考）",
        "",
        context || "（无附加上下文）",
        "",
        "> 本 Issue 由「学习中心 → 引擎日志 → 上传到 GitHub」手动创建（内置加密通道），令牌不出服务端。",
        "",
        `— Skip-Ad v${APP_VERSION}`,
      ].join("\n")
    : [
        "## 问题描述",
        desc,
        "",
        appName ? `**涉及应用**：${appName}` : "",
        "",
        "## 环境信息（自动附加）",
        `- 版本：v${APP_VERSION}（秒跳引擎）`,
        context || "（无附加上下文）",
        "",
        "> 本 Issue 由内置加密反馈通道自动创建，令牌不出服务端。",
        "",
        `— Skip-Ad v${APP_VERSION}`,
      ]
        .filter((l) => l !== "")
        .join("\n");

  const record = await db.feedbackIssue.create({
    data: { type, title, body, status: "failed" },
  });

  const r = await createIssue({ title, body, labels: ["用户反馈", meta.tag] });
  if (!r.ok) {
    await db.feedbackIssue.update({
      where: { id: record.id },
      data: { status: "failed" },
    });
    return NextResponse.json(
      { ok: false, error: `上报 GitHub 失败：${r.message ?? "未知错误"}（已留痕，稍后会自动重试）` },
      { status: 502 },
    );
  }

  await db.feedbackIssue.update({
    where: { id: record.id },
    data: { status: "submitted", issueUrl: r.url, issueNumber: r.number },
  });

  return NextResponse.json({ ok: true, url: r.url, number: r.number });
}
