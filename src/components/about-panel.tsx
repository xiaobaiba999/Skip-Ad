"use client";

import { Button } from "@/components/ui/button";
import { APP_VERSION, VERSION_CODE, GITHUB_HOME, VERSION_HISTORY } from "@/lib/version";
import { cn } from "@/lib/utils";
import { Zap, Github, ExternalLink, Smartphone, ShieldCheck, Tag } from "lucide-react";

/** 关于页：极简 —— 一句话介绍 + 最近三个版本 + 仓库入口 */
export function AboutPanel() {
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      {/* 品牌 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-6 text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500 to-fuchsia-600 text-white shadow-lg shadow-violet-500/25">
          <Zap className="h-8 w-8" />
        </div>
        <h1 className="mt-4 text-xl font-black text-zinc-900">Skip-Ad · 秒跳引擎</h1>
        <p className="mt-1.5 text-xs leading-relaxed text-zinc-500">
          开屏广告毫秒级秒跳 · 摇一摇/Deeplink 拦截 · 支付/银行/政务守护模式 · 习惯学习夜间同步
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
