import { NextRequest, NextResponse } from "next/server";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { getGithubToken } from "@/lib/credentials";
import { GITHUB_API } from "@/lib/version";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

interface GhRelease {
  tag_name?: string;
  assets?: { id: number; name: string; size: number }[];
}

const MIME_APK = "application/vnd.android.package-archive";

async function resolveAsset(tag?: string | null): Promise<{ id: number; name: string; size: number; version: string } | null> {
  const token = getGithubToken();
  const url = tag ? `${GITHUB_API}/releases/tags/${encodeURIComponent(tag)}` : `${GITHUB_API}/releases/latest`;
  const res = await fetch(url, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      Accept: "application/vnd.github+json",
      "User-Agent": "skip-ad-instant-engine",
    },
    cache: "no-store",
  });
  if (!res.ok) return null;
  const d = (await res.json()) as GhRelease;
  const apk = (d.assets ?? []).find((a) => a.name?.endsWith(".apk"));
  if (!apk) return null;
  return {
    id: apk.id,
    name: apk.name,
    size: apk.size,
    version: apk.name.replace(/^adshield-v|\.apk$/g, ""),
  };
}

/**
 * GET /api/release/download?tag=v3.1.0 —— 应用内 APK 直下（流式代理）
 * · 服务端持内置令牌经 GitHub Assets API（octet-stream）拉取，302 由 fetch 自动跟随
 * · 以附件流式回传：浏览器页内下载、可显示进度，全程不跳转 GitHub
 * · GitHub 不可达时回退仓库本地归档 android-app/dist/adshield-<ver>.apk
 */
export async function GET(req: NextRequest) {
  const tag = req.nextUrl.searchParams.get("tag");
  const headers = (name: string, size: number) => ({
    "Content-Type": MIME_APK,
    "Content-Disposition": `attachment; filename="${name}"`,
    "Content-Length": String(size),
    "X-Apk-Size": String(size),
    "Cache-Control": "no-store",
  });

  try {
    const asset = await resolveAsset(tag);
    if (!asset) {
      return NextResponse.json({ error: "未找到该版本的 APK 附件" }, { status: 404 });
    }
    const token = getGithubToken();
    const upstream = await fetch(`${GITHUB_API}/releases/assets/${asset.id}`, {
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        Accept: "application/octet-stream",
        "User-Agent": "skip-ad-instant-engine",
      },
      redirect: "follow",
      cache: "no-store",
    });
    if (upstream.ok && upstream.body) {
      return new Response(upstream.body, { headers: headers(asset.name, asset.size) });
    }
    throw new Error(`upstream ${upstream.status}`);
  } catch {
    // 本地归档回退（构建产物 android-app/dist/）
    try {
      const ver = (tag ?? "").replace(/^v/, "") || "3.1.0";
      const local = path.join(process.cwd(), "android-app", "dist", `adshield-v${ver}.apk`);
      const buf = await readFile(local);
      return new Response(new Uint8Array(buf), { headers: headers(`adshield-v${ver}.apk`, buf.length) });
    } catch {
      return NextResponse.json({ error: "下载源暂不可用，请稍后重试" }, { status: 502 });
    }
  }
}
