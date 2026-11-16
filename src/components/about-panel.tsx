"use client";

import { useCallback, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import { compareVersion, APP_VERSION, VERSION_CODE, GITHUB_HOME, VERSION_HISTORY } from "@/lib/version";
import {
  Zap, Github, ExternalLink, Smartphone, ShieldCheck, Tag, RefreshCw, Download,
  CheckCircle2, Loader2, HardDriveDownload, RotateCcw,
} from "lucide-react";

interface ReleaseInfo {
  ok: boolean;
  noRelease?: boolean;
  tag?: string;
  notes?: string;
  apk?: { name: string; size: number; id: number; version: string } | null;
  error?: string;
}

interface DownloadState {
  phase: "downloading" | "done" | "error";
  pct: number;
  gotMB: number;
  totalMB: number;
  speedKBs: number;
  fileName: string;
  error?: string;
}

/** Android 壳内注册的桥（存在则委托系统 DownloadManager 下载并拉起安装） */
interface AndroidBridge {
  startUpdateDownload: (url: string, file: string) => string;
  updateProgress: () => string;
  installUpdate: () => string;
}
declare global {
  interface Window {
    AndroidApp?: AndroidBridge;
  }
}

const fmtMB = (bytes: number) => Math.round((bytes / 1048576) * 100) / 100;

/**
 * 关于页：极简 —— 品牌 + 检查更新（页内流式直下，不跳转 GitHub）+ 最近三个版本。
 */
export function AboutPanel() {
  const [checking, setChecking] = useState(false);
  const [rel, setRel] = useState<ReleaseInfo | null>(null);
  const [dl, setDl] = useState<DownloadState | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const checkUpdate = useCallback(async () => {
    setChecking(true);
    setRel(null);
    setDl(null);
    try {
      const r = await fetch("/api/release/latest", { cache: "no-store" });
      setRel(await r.json());
    } catch (e) {
      setRel({ ok: false, error: e instanceof Error ? e.message : "网络异常" });
    }
    setChecking(false);
  }, []);

  const latest = rel?.tag?.replace(/^v/, "") ?? "";
  const hasNewer = rel?.ok && !!latest && compareVersion(latest, APP_VERSION) > 0;
  const apkSizeText = rel?.apk
    ? rel.apk.size >= 1048576
      ? `${fmtMB(rel.apk.size)} MB`
      : `${Math.round(rel.apk.size / 1024)} KB`
    : "";

  /* ── 页内流式下载：进度直显，全程不离开本页 ── */
  const downloadApk = useCallback(async () => {
    if (!rel?.apk || !rel.tag) return;
    const tag = rel.tag;
    const fileName = rel.apk.name;
    const total = rel.apk.size;
    const url = `/api/release/download?tag=${encodeURIComponent(tag)}`;

    // Android 壳环境：委托系统 DownloadManager（省电，完成后拉起安装）
    if (typeof window !== "undefined" && window.AndroidApp?.startUpdateDownload) {
      try {
        const res = JSON.parse(
          window.AndroidApp.startUpdateDownload(new URL(url, window.location.origin).toString(), fileName),
        );
        if (res.ok) {
          setDl({ phase: "downloading", pct: 6, gotMB: 0, totalMB: fmtMB(total), speedKBs: 0, fileName });
          return;
        }
        setDl({ phase: "error", pct: 0, gotMB: 0, totalMB: fmtMB(total), speedKBs: 0, fileName, error: res.error ?? "系统下载服务不可用" });
        return;
      } catch { /* 桥异常则回落到页内流式 */ }
    }

    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;
    setDl({ phase: "downloading", pct: 0, gotMB: 0, totalMB: fmtMB(total), speedKBs: 0, fileName });
    try {
      const res = await fetch(url, { signal: ac.signal });
      if (!res.ok) throw new Error(`下载源返回 ${res.status}`);
      const totalBytes = Number(res.headers.get("X-Apk-Size") ?? res.headers.get("Content-Length") ?? total) || total;
      const reader = res.body?.getReader();
      if (!reader) throw new Error("浏览器不支持流式下载");
      const chunks: Uint8Array[] = [];
      let got = 0;
      const startedAt = performance.now();
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        chunks.push(value);
        got += value.length;
        const elapsed = Math.max(0.001, (performance.now() - startedAt) / 1000);
        setDl({
          phase: "downloading",
          pct: Math.min(99, Math.round((got / totalBytes) * 100)),
          gotMB: fmtMB(got),
          totalMB: fmtMB(totalBytes),
          speedKBs: Math.round(got / 1024 / elapsed),
          fileName,
        });
      }
      // 页内保存（blob 下载，无任何跳转）
      const blob = new Blob(chunks as BlobPart[], { type: "application/vnd.android.package-archive" });
      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = objectUrl;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(objectUrl), 4000);
      setDl({ phase: "done", pct: 100, gotMB: fmtMB(got), totalMB: fmtMB(totalBytes), speedKBs: 0, fileName });
    } catch (e) {
      if (ac.signal.aborted) return;
      setDl({
        phase: "error", pct: 0, gotMB: 0, totalMB: fmtMB(total), speedKBs: 0, fileName,
        error: e instanceof Error ? e.message : "下载中断",
      });
    }
  }, [rel]);

  const cancelDownload = useCallback(() => {
    abortRef.current?.abort();
    setDl(null);
  }, []);

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      {/* 品牌 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-6 text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500 to-fuchsia-600 text-white shadow-lg shadow-violet-500/25">
          <Zap className="h-8 w-8" />
        </div>
        <h1 className="mt-4 text-xl font-black text-zinc-900">Skip-Ad · 秒跳引擎</h1>
        <p className="mt-1.5 text-xs leading-relaxed text-zinc-500">
          P0 预拦截（广告未及呈现）· SDK 广告窗识别 · 假跳过甄别 · 链式二跳前置直断 · 守护模式 · 十四轮自测 100%
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

      {/* 检查更新：页内流式直下，全程不跳转 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5" data-testid="update-section">
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
            经内置加密通道连接 GitHub Releases；发现更新时在本页流式下载 APK（进度直显），无需跳转浏览器。
          </p>
        )}

        {rel && !rel.ok && (
          <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2.5 text-[11px] font-medium text-rose-600" data-testid="update-error">
            查询失败：{rel.error}
          </p>
        )}

        {rel?.ok && rel.noRelease && (
          <p className="mt-3 rounded-lg bg-zinc-50 px-3 py-2.5 text-[11px] text-zinc-500" data-testid="update-empty">
            仓库尚未发布任何版本，发布后这里会第一时间提醒。
          </p>
        )}

        {rel?.ok && !rel.noRelease && latest && (
          <div className="mt-3 space-y-2.5" data-testid="update-result">
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <span className="font-bold text-zinc-800">最新发布 v{latest}</span>
              {apkSizeText && <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-[10px] font-semibold text-zinc-500">{apkSizeText}</span>}
              <span className="text-zinc-300">/</span>
              <span className="text-zinc-500">当前 v{APP_VERSION}</span>
              {hasNewer && (
                <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-700">有新版</span>
              )}
            </div>

            {hasNewer ? (
              <p className="rounded-lg bg-emerald-50 px-3 py-2.5 text-[11px] leading-relaxed text-emerald-700 ring-1 ring-emerald-200">
                发现新版本，与当前版本同签名：本页下载后直接安装即可覆盖升级，学习指纹全部保留。
              </p>
            ) : (
              <p className="flex items-center gap-1.5 rounded-lg bg-zinc-50 px-3 py-2.5 text-[11px] font-semibold text-zinc-500">
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> 已是最新版本。
              </p>
            )}

            {/* 下载按钮（新版直下 / 已是最新也可重下当前版） */}
            {rel.apk && (
              <div className="space-y-2">
                {(!dl || dl.phase === "error") && (
                  <Button
                    size="sm"
                    data-testid="btn-dl-apk"
                    className={cn("h-9 w-full rounded-lg", hasNewer ? "bg-emerald-600 hover:bg-emerald-500" : "bg-zinc-800 hover:bg-zinc-700")}
                    onClick={() => void downloadApk()}
                  >
                    <Download className="mr-1.5 h-4 w-4" />
                    {hasNewer ? `页内直接下载新版 APK（不跳转）` : `重新下载当前版本 APK（页内直下）`}
                  </Button>
                )}
                {dl?.phase === "error" && (
                  <p className="rounded-lg bg-rose-50 px-3 py-2 text-[11px] text-rose-600" data-testid="dl-error">
                    下载失败：{dl.error}
                  </p>
                )}
              </div>
            )}

            {/* 流式下载进度 */}
            {dl && dl.phase === "downloading" && (
              <div className="rounded-xl border border-violet-100 bg-violet-50/60 p-3.5" data-testid="dl-progress">
                <div className="flex items-center justify-between text-[11px] font-bold text-zinc-700">
                  <span className="flex items-center gap-1.5">
                    <Loader2 className="h-3.5 w-3.5 animate-spin text-violet-600" /> 正在页内下载 {dl.fileName}
                  </span>
                  <span className="tabular-nums">{dl.pct}%</span>
                </div>
                <Progress value={dl.pct} className="mt-2 h-2" />
                <div className="mt-1.5 flex items-center justify-between text-[10px] tabular-nums text-zinc-500">
                  <span>{dl.gotMB} / {dl.totalMB} MB{dl.speedKBs > 0 ? ` · ${dl.speedKBs} KB/s` : ""}</span>
                  <button type="button" className="font-semibold text-zinc-400 hover:text-zinc-600" onClick={cancelDownload}>
                    取消
                  </button>
                </div>
              </div>
            )}
            {dl?.phase === "done" && (
              <div className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3.5 text-[11px] leading-relaxed text-emerald-800" data-testid="dl-done">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
                <div className="min-w-0 flex-1">
                  <div className="font-bold">下载完成 · 已保存 {dl.fileName}（{dl.totalMB} MB）</div>
                  <div className="mt-0.5 text-emerald-700">
                    浏览器已保存到「下载」目录，点按即可安装覆盖升级（同签名，学习指纹保留）；全程未离开本页。
                  </div>
                </div>
                <button
                  type="button"
                  className="flex shrink-0 items-center gap-1 rounded-lg bg-white px-2 py-1 text-[10px] font-bold text-zinc-600 ring-1 ring-zinc-200 hover:bg-zinc-50"
                  onClick={() => setDl(null)}
                  aria-label="关闭提示"
                >
                  <RotateCcw className="h-3 w-3" /> 重新下载
                </button>
              </div>
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
      <p className="flex items-start justify-center gap-1 px-2 text-center text-[10px] leading-relaxed text-zinc-400">
        <HardDriveDownload className="mt-0.5 h-3 w-3 shrink-0" />
        本站为秒跳引擎的演示与调度环境：以模拟方式呈现无障碍代点、预拦截与守护策略；
        真机拦截依赖系统无障碍权限。历次 APK 归档见仓库 android-app/dist 与 Releases。
      </p>
    </div>
  );
}
