import { NextResponse } from "next/server";
import { APP_VERSION, GITHUB_API, RELEASES_PAGE } from "@/lib/version";

export const dynamic = "force-dynamic";

interface ReleaseAsset { name: string; browser_download_url: string; size: number }

/**
 * GET /api/releases —— 联网检查 GitHub 最新 Release
 * 返回最新版本号、更新说明与 APK 直链，与当前版本（package.json 同步的 APP_VERSION）比较。
 * 公共仓库读取无需令牌；配置 GITHUB_TOKEN 后私有仓库亦可读。
 */
export async function GET() {
  const headers: Record<string, string> = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "adshield-update-check",
  };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;

  try {
    const res = await fetch(`${GITHUB_API}/releases/latest`, { headers, cache: "no-store" });

    if (res.status === 404) {
      // 尚未发布任何版本
      return NextResponse.json({
        ok: true,
        hasRelease: false,
        currentVersion: APP_VERSION,
        releasesPage: RELEASES_PAGE,
      });
    }
    if (!res.ok) {
      return NextResponse.json(
        { ok: false, error: `GitHub 返回 ${res.status}`, currentVersion: APP_VERSION },
        { status: 502 },
      );
    }

    const data = await res.json();
    const apk = (data.assets ?? []).find((a: ReleaseAsset) => a.name?.endsWith(".apk")) as ReleaseAsset | undefined;
    const tag: string = data.tag_name ?? "";
    const latest = tag.replace(/^v/, "");
    const cur = APP_VERSION.split(".");
    const lat = latest.split(".").map((n: string) => parseInt(n, 10) || 0);
    let upToDate = true;
    for (let i = 0; i < 3; i++) {
      const d = (cur[i] ? parseInt(cur[i], 10) : 0) - (lat[i] ?? 0);
      if (d !== 0) { upToDate = d > 0; break; }
    }

    return NextResponse.json({
      ok: true,
      hasRelease: true,
      currentVersion: APP_VERSION,
      tag,
      latest,
      name: data.name ?? tag,
      notes: typeof data.body === "string" ? data.body.slice(0, 2000) : "",
      publishedAt: data.published_at ?? null,
      apkUrl: apk?.browser_download_url ?? null,
      apkName: apk?.name ?? null,
      apkSizeMB: apk ? Math.round(apk.size / 1024 / 10.24) / 100 : null,
      upToDate,
      releasesPage: RELEASES_PAGE,
    });
  } catch {
    return NextResponse.json(
      { ok: false, error: "网络请求失败，请稍后重试", currentVersion: APP_VERSION },
      { status: 502 },
    );
  }
}
