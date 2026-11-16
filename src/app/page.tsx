"use client";

import { useCallback, useState } from "react";
import { Arena } from "@/components/arena";
import { IntelStation } from "@/components/intel-station";
import { LearningCenter } from "@/components/learning-center";
import { AboutPanel } from "@/components/about-panel";
import { FeedbackDialog } from "@/components/feedback-dialog";
import { Button } from "@/components/ui/button";
import { APP_VERSION, GITHUB_HOME } from "@/lib/version";
import { cn } from "@/lib/utils";
import {
  Zap, Swords, Radar, BrainCircuit, Info, MessageSquareWarning, Github, ShieldCheck,
} from "lucide-react";

const TABS = [
  { key: "arena", label: "演练场", icon: Swords },
  { key: "intel", label: "情报站", icon: Radar },
  { key: "learning", label: "学习中心", icon: BrainCircuit },
  { key: "about", label: "关于", icon: Info },
] as const;

type TabKey = (typeof TABS)[number]["key"];

export default function Home() {
  const [tab, setTab] = useState<TabKey>("arena");
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const openFeedback = useCallback(() => setFeedbackOpen(true), []);

  return (
    <div className="flex min-h-screen flex-col bg-zinc-100/70">
      {/* 顶栏 */}
      <header className="sticky top-0 z-30 border-b border-zinc-200 bg-white/85 backdrop-blur">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-2.5 lg:px-6">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-600 text-white shadow-md shadow-violet-500/25">
              <Zap className="h-4.5 w-4.5" />
            </span>
            <div>
              <h1 className="flex items-center gap-2 text-[15px] font-bold leading-tight text-zinc-900">
                Skip-Ad · 秒跳引擎
                <span className="rounded-full bg-violet-50 px-1.5 py-0.5 text-[10px] font-bold text-violet-700 ring-1 ring-violet-200">
                  v{APP_VERSION}
                </span>
              </h1>
              <p className="text-[11px] text-zinc-500">SDK 广告窗指纹 · 极速链 0ms 首扫 · 热区直断 · 链式回弹 · 弹窗 v2 · 十四轮自测 100%</p>
            </div>
          </div>

          <div className="ml-auto flex items-center gap-2">
            <span className="hidden items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-[11px] font-bold text-emerald-700 ring-1 ring-emerald-200 sm:flex">
              <span className="relative flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-60" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
              </span>
              <ShieldCheck className="h-3 w-3" /> 守护服务运行中
            </span>
            <Button size="sm" data-testid="btn-feedback" className="h-8 rounded-lg bg-zinc-900 hover:bg-zinc-700" onClick={openFeedback}>
              <MessageSquareWarning className="mr-1 h-3.5 w-3.5" /> 反馈
            </Button>
          </div>
        </div>

        {/* 主导航 */}
        <nav className="mx-auto flex max-w-7xl gap-1 overflow-x-auto px-3 pb-2 lg:px-5" aria-label="主导航">
          {TABS.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              type="button"
              data-testid={`tab-${key}`}
              onClick={() => setTab(key)}
              className={cn(
                "flex shrink-0 items-center gap-1.5 rounded-xl px-3.5 py-2 text-[13px] font-bold transition",
                tab === key ? "bg-zinc-900 text-white shadow-sm" : "text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800",
              )}
            >
              <Icon className="h-4 w-4" /> {label}
            </button>
          ))}
        </nav>
      </header>

      {/* 内容区 */}
      <main className="mx-auto w-full max-w-7xl flex-1 p-4 lg:p-6">
        {tab === "arena" && <Arena onFeedback={openFeedback} />}
        {tab === "intel" && <IntelStation onFeedback={openFeedback} />}
        {tab === "learning" && <LearningCenter />}
        {tab === "about" && <AboutPanel />}
      </main>

      {/* 页脚 */}
      <footer className="border-t border-zinc-200 bg-white/60 py-3 text-center text-[11px] leading-relaxed text-zinc-400">
        Skip-Ad 秒跳引擎 · 学习规则每晚 02:00 自动同步 GitHub ·
        <a href={GITHUB_HOME} target="_blank" rel="noreferrer" className="mx-1 inline-flex items-center gap-0.5 text-violet-600 hover:underline">
          <Github className="h-3 w-3" /> xiaobaiba999/Skip-Ad
        </a>
      </footer>

      <FeedbackDialog open={feedbackOpen} onOpenChange={setFeedbackOpen} />
    </div>
  );
}
