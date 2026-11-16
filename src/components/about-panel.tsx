"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { compareVersion, APP_VERSION, VERSION_CODE, GITHUB_API, GITHUB_HOME, VERSION_HISTORY } from "@/lib/version";
import {
  Zap, Github, ExternalLink, Smartphone, ShieldCheck, Tag, RefreshCw, Download, CheckCircle2, Loader2,
} from "lucide-react";

interface ReleaseInfo {
  ok: boolean;
  tag?: string;
  notes?: string;
  apkUrl?: string;
  apkMB?: number;
  error?: string;
}

/**
 * 关于页：极简 —— 品牌 + 检查更新（页内直下）+ 最近三个版本。
 */
export function AboutPanel() {
  const [checking, setChecking] = useState(false);
  const [rel, setRel] = useState<ReleaseInfo | null>(null);

  const checkUpdate = useCallback(async () => {
    setChecking(true);
    setRel(null);
    try {
      const r = await fetch(`${GITHUB_API}/releases/latest`, { headers: { Accept: "application/vnd.github+json" }, cache: "no-store" });
      if (r.status === 404) {
        setRel({ ok: true, tag: "", notes: "", apkUrl: "", error: "" });
      } else if (r.ok) {
        const d = await r.json();
        const apk = (d.assets ?? []).find((a: { name?: string }) => a.name?.endsWith(".apk"));
        setRel({
          ok: true,
          tag: d.tag_name ?? "",
          notes: d.body ?? "",
          apkUrl: apk?.browser_download_url ?? "",
          apkMB: apk ? Math.round((apk.size / 1048576) * 100) / 100 : 0,
        });
      } else {
        setRel({ ok: false, error: `GitHub 返回 ${r.status}` });
      }
    } catch (e) {
      setRel({ ok: false, error: e instanceof Error ? e.message : "网络异常" });
    }
    setChecking(false);
  }, []);

  const latest = rel?.tag?.replace(/^v/, "") ?? "";
  const hasNewer = rel?.ok && latest && compareVersion(latest, APP_VERSION) > 0;
  const apkSizeText = rel?.apkMB ? (rel.apkMB >= 1 ? `${rel.apkMB} MB` : `${Math.round((rel.apkMB ?? 0) * 1024)} KB`) : "";

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      {/* 品牌 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-6 text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500 to-fuchsia-600 text-white shadow-lg shadow-violet-500/25">
          <Zap className="h-8 w-8" />
        </div>
        <h1 className="mt-4 text-xl font-black text-zinc-900">Skip-Ad · 秒跳引擎</h1>
        <p className="mt-1.5 text-xs leading-relaxed text-zinc-500">
          开屏广告毫秒级秒跳 · 摇一摇/Deeplink 拦截 · 守护模式 · 侧载一视同仁 · 事件驱动零轮询
        </p>
        <div className="mt-3 flex flex-wrap items-center justify-center gap-2 text-[11px]">
          <span className="rounded-full bg-violet-50 px-2.5 py-1 font-bold text-violet-700 ring-1 ring-violet-200">v{APP_VERSION}</span>
          <span className="rounded-full bg-zinc-100 px-2.5 py-1 font-semibold text-zinc-500">versionCode {VERSION_CODE}</span>
          <span className="flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 font-bold text-emerald-700 ring-1 ring-emerald-200">
            <Smartphone className="h-3 w-3" /> 浏览器菜单「添加到主屏幕」即可当 App 用
          </span>
        </div>
        <div className="mt-4 flex items-center justify-center gap-2">
          <Button size="sm" variant="outline" className="h-8 rounded-lg text-[11px]" onClick={() => window.open(GITHUB_HOME, "_blank")}>
            <Github className="mr-1 h-3.5 w-3.5" /> 开源仓库 <ExternalLink className="ml-0.5 h-3 w-3" />
          </Button>
        </div>
      </section>

      {/* 检查更新：页内直接下载，无需跳转 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <Download className="h-5 w-5 text-violet-600" /> 检查更新
          </h2>
          <Button
            size="sm"
            data-testid="btn-check-update"
            className="h-8 rounded-lg bg-zinc-900 hover:bg-zinc-700"
            onClick={() => void checkUpdate()}
            disabled={checking}
          >
            {checking ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="mr-1 h-3.5 w-3.5" />}
            {checking ? "查询中…" : "检查"}
          </Button>
        </div>

        {!rel && (
          <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-4 text-center text-[11px] leading-relaxed text-zinc-400">
            连接 GitHub Releases 检查新版；发现更新时可在本页直接下载 APK，无需跳转浏览器。
          </p>
        )}

        {rel && !rel.ok && (
          <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2.5 text-[11px] font-medium text-rose-600" data-testid="update-error">
            查询失败：{rel.error}
          </p>
        )}

        {rel?.ok && !latest && (
          <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-2.5 text-[11px] text-zinc-500" data-testid="update-empty">
            仓库尚未发布任何版本，发布后这里会第一时间提醒。
          </p>
        )}

        {rel?.ok && latest && (
          <div className="mt-3 space-y-2.5" data-testid="update-result">
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <span className="font-bold text-zinc-800">最新发布 v{latest}</span>
              <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-[10px] font-semibold text-zinc-500">{apkSizeText}</span>
              <span className="text-zinc-300">/</span>
              <span className="text-zinc-500">当前 v{APP_VERSION}</span>
            </div>
            {hasNewer ? (
              <>
                <p className="rounded-lg bg-emerald-50 px-3 py-2.5 text-[11px] leading-relaxed text-emerald-700 ring-1 ring-emerald-200">
                  发现新版本，与当前版本同签名：本页下载后直接安装即可覆盖升级，学习数据全部保留。
                </p>
                {rel.apkUrl && (
                  <Button asChild size="sm" data-testid="btn-dl-apk" className="h-9 w-full rounded-lg bg-emerald-600 hover:bg-emerald-500">
                    <a href={rel.apkUrl} download>
                      <Download className="mr-1.5 h-4 w-4" /> 页内直接下载新版 APK（不跳转）
                    </a>
                  </Button>
                )}
              </>
            ) : (
              <p className="flex items-center gap-1.5 rounded-lg bg-zinc-50 px-3 py-2.5 text-[11px] font-semibold text-zinc-500">
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> 已是最新版本。每次迭代都会发布到 GitHub Releases。
              </p>
            )}
          </div>
        )}
      </section>

      {/* 最近三个版本 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
          <Tag className="h-5 w-5 text-violet-600" /> 最近三个版本
        </h2>
        <ol className="mt-4 space-y-4">
          {VERSION_HISTORY.map((v, i) => (
            <li key={v.version} className={cn("rounded-xl border p-4", i === 0 ? "border-violet-200 bg-violet-50/50" : "border-zinc-100 bg-zinc-50/60")}>
              <div className="flex flex-wrap items-center gap-2">
                <span className={cn("rounded-lg px-2 py-0.5 text-xs font-black", i === 0 ? "bg-violet-600 text-white" : "bg-zinc-200 text-zinc-600")}>
                  v{v.version}
                </span>
                <span className="text-[11px] tabular-nums text-zinc-400">{v.date} · code {v.code}</span>
                {i === 0 && (
                  <span className="ml-auto flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-700">
                    <ShieldCheck className="h-3 w-3" /> 当前版本
                  </span>
                )}
              </div>
              <div className="mt-2 text-[13px] font-bold text-zinc-800">{v.tagline}</div>
              <ul className="mt-2 space-y-1">
                {v.notes.map((n) => (
                  <li key={n} className="flex gap-1.5 text-[11px] leading-relaxed text-zinc-500">
                    <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-violet-400" /> {n}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ol>
      </section>

      {/* 一行免责 */}
      <p className="px-2 text-center text-[10px] leading-relaxed text-zinc-400">
        本站为秒跳引擎的演示与调度环境：以模拟方式呈现无障碍代点、传感器拦截与守护策略；
        真机拦截依赖系统无障碍权限。历次 APK 归档见仓库 android-app/dist 与 Releases。
      </p>
    </div>
  );
}
