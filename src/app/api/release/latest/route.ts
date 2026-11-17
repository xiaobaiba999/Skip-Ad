import { NextResponse } from "next/server";
import { getGithubToken } from "@/lib/credentials";
import { GITHUB_API } from "@/lib/version";

export const dynamic = "force-dynamic";

interface GhRelease {
  tag_name?: string;
  name?: string;
  body?: string;
  html_url?: string;
  assets?: { id: number; name: string; size: number; browser_download_url: string }[];
}

/**
 * GET /api/release/latest —— 检查更新（服务端代理）
 * · 内置加密令牌在服务端调用 GitHub API，避开浏览器直连的限流与跳转
 * · 返回最新 Release 与 .apk 附件元信息（下载走 /api/release/download 流式代理）
 */
export async function GET() {
  const token = getGithubToken();
  try {
    const res = await fetch(`${GITHUB_API}/releases/latest`, {
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        Accept: "application/vnd.github+json",
        "User-Agent": "skip-ad-instant-engine",
      },
      cache: "no-store",
    });
    if (res.status === 404) {
      return NextResponse.json({ ok: true, noRelease: true });
    }
    if (!res.ok) {
      return NextResponse.json({ ok: false, error: `GitHub ${res.status}` });
    }
    const d = (await res.json()) as GhRelease;
    const apk = (d.assets ?? []).find((a) => a.name?.endsWith(".apk"));
    return NextResponse.json({
      ok: true,
      tag: d.tag_name ?? "",
      name: d.name ?? "",
      notes: d.body ?? "",
      apk: apk
        ? { name: apk.name, size: apk.size, id: apk.id, version: apk.name.replace(/^adshield-v|\.apk$/g, "") }
        : null,
    });
  } catch (e) {
    return NextResponse.json({ ok: false, error: e instanceof Error ? e.message : "网络异常" });
  }
}
