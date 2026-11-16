"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
  ShieldCheck,
  Zap,
  Download,
  Github,
  BrainCircuit,
  MessageSquareWarning,
  Sparkles,
  RefreshCw,
  Ban,
} from "lucide-react";

const stats = [
  { label: "内置规则", value: "五层 + 28 款分应用表", sub: "全部内嵌 APK · 零云端" },
  { label: "出手速度", value: "0ms", sub: "事件驱动 · 露头就秒跳" },
  { label: "JVM 用例", value: "168", sub: "规则引擎 60 + 决策核心 108 全绿" },
  { label: "乱点红线", value: "0 盲点", sub: "只点控件自身 bounds · 适配任意分辨率" },
];

const features = [
  { icon: Zap, title: "开屏秒跳", desc: "「跳过/跳过广告/Skip」命中即点，倒计时门控出手即中" },
  { icon: Ban, title: "绝不乱点", desc: "诱导词否决 + 面积上限 + 广告窗确认门" },
  { icon: Sparkles, title: "SDK 图片键", desc: "vid skip 族 + SDK 窗 close 族（边缘带）" },
  { icon: BrainCircuit, title: "习惯学习", desc: "位置/指纹沉淀，越用越快" },
  { icon: MessageSquareWarning, title: "日志反馈", desc: "一键上传 GitHub Issues，驱动迭代" },
  { icon: RefreshCw, title: "纯跳过引擎", desc: "v3.12.0：链式拦截整体移除，分享/支付跳转不再被误弹回" },
];

const versions = [
  {
    v: "3.12.0",
    d: "纯跳过引擎：链式拦截机制整体移除（issue#14 实锤）——NFC 碰一碰支付链与主动「分享到微信」曾被 1.5s 内连按 3 次返回键弹回；用户意图无法穷举，回弹收益远小于代价。同包落地页弹回一并移除；connectivity 连接检查窗循环误点归零。",
    latest: true,
  },
  {
    v: "3.11.0",
    d: "链式回弹用户意图安全门（三重放行）+ 学习规则明细撤出界面（引擎照常学习）。",
  },
  {
    v: "3.10.0",
    d: "内置规则库大扩充 + 界面精简重构：五层全局规则 + 28 款分应用表；桌面黑名单与广告窗指纹扩充；UI 精简重构（去除大段说明文字）。",
  },
];

export default function Home() {
  return (
    <div className="min-h-screen flex flex-col bg-gradient-to-b from-emerald-50/60 via-white to-white">
      {/* Hero */}
      <header className="w-full">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 pt-10 pb-6 sm:pt-14">
          <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center gap-2">
              <Badge className="bg-emerald-600 hover:bg-emerald-600 text-white">开屏卫士 v3.12.0</Badge>
              <Badge variant="outline" className="border-emerald-300 text-emerald-700">纯跳过引擎</Badge>
              <Badge variant="outline">Android 7.0+</Badge>
            </div>
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-bold tracking-tight text-neutral-900">
              露头就秒跳，
              <span className="text-emerald-600">绝不乱点</span>
            </h1>
            <p className="text-base text-neutral-600 max-w-2xl">
              开屏广告自动跳过引擎。规则全部内嵌，只认控件属性、只点控件自身位置；v3.12.0 起链式拦截整体移除，只点「跳过/关闭」，绝不对抗你的任何跳转。
            </p>
            <div className="flex flex-wrap gap-3">
              <Button
                className="bg-emerald-600 hover:bg-emerald-700 h-10 px-5"
                onClick={() => window.open("https://github.com/xiaobaiba999/Skip-Ad/releases/latest", "_blank")}
              >
                <Download className="mr-1.5 h-4 w-4" /> 下载 APK（GitHub 最新版）
              </Button>
              <Button
                variant="outline"
                className="h-10 px-5 border-neutral-300"
                onClick={() => window.open("https://github.com/xiaobaiba999/Skip-Ad", "_blank")}
              >
                <Github className="mr-1.5 h-4 w-4" /> GitHub
              </Button>
            </div>
          </div>
        </div>
      </header>

      <main className="flex-1 w-full max-w-5xl mx-auto px-4 sm:px-6 pb-14">
        {/* 数据概览 */}
        <section aria-label="数据概览">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
            {stats.map((s) => (
              <Card key={s.label} className="border-neutral-200 shadow-sm hover:shadow-md transition-shadow">
                <CardContent className="p-4 sm:p-5">
                  <div className="text-xl sm:text-2xl font-bold text-emerald-600">{s.value}</div>
                  <div className="text-sm font-medium text-neutral-800 mt-1">{s.label}</div>
                  <div className="text-xs text-neutral-500 mt-0.5 leading-snug">{s.sub}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        {/* 功能特性 */}
        <section aria-label="功能特性" className="mt-10">
          <h2 className="text-xl font-bold text-neutral-900 flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-emerald-600" />
            功能特性
          </h2>
          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4 mt-5">
            {features.map((f) => (
              <Card key={f.title} className="border-neutral-200 shadow-sm hover:shadow-md transition-shadow">
                <CardContent className="p-4 flex gap-3 items-start">
                  <f.icon className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-neutral-900">{f.title}</div>
                    <div className="text-xs text-neutral-500 mt-0.5 leading-relaxed">{f.desc}</div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        {/* 版本迭代 */}
        <section aria-label="版本迭代" className="mt-10">
          <h2 className="text-xl font-bold text-neutral-900 flex items-center gap-2">
            <RefreshCw className="w-5 h-5 text-emerald-600" />
            更新迭代
          </h2>
          <Card className="mt-5 shadow-sm">
            <CardHeader className="pb-2">
              <CardTitle className="text-base">最近三个版本</CardTitle>
            </CardHeader>
            <CardContent className="pt-0">
              <ul className="divide-y divide-neutral-100">
                {versions.map((x) => (
                  <li key={x.v} className="py-3 first:pt-0 last:pb-0">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-sm font-bold text-emerald-700">v{x.v}</span>
                      {x.latest && (
                        <Badge className="bg-emerald-600 hover:bg-emerald-600 text-[10px] px-1.5 py-0">最新</Badge>
                      )}
                    </div>
                    <p className="text-sm text-neutral-600 mt-1 leading-relaxed">{x.d}</p>
                  </li>
                ))}
              </ul>
              <Separator className="my-4" />
              <div className="flex flex-col sm:flex-row sm:items-center gap-3 p-3 rounded-lg bg-emerald-50 border border-emerald-200">
                <Download className="w-6 h-6 text-emerald-600 shrink-0" />
                <div className="min-w-0 text-sm">
                  <span className="font-semibold text-neutral-900">adshield-v3.12.0.apk</span>
                  <span className="text-neutral-500"> · 同签名覆盖升级，学习数据保留 · GitHub Releases 可下载历版</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </section>
      </main>

      {/* Sticky Footer */}
      <footer className="mt-auto border-t border-neutral-200 bg-white">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] flex flex-col sm:flex-row items-center justify-between gap-3 text-sm text-neutral-500">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>开屏卫士 Skip-Ad · v3.12.0</span>
          </div>
          <div className="flex flex-col sm:flex-row items-center gap-1.5 sm:gap-4 text-center">
            <span>只代点「跳过/关闭」，不碰网络、不终止进程</span>
            <a
              href="https://github.com/xiaobaiba999/Skip-Ad"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-emerald-700 hover:text-emerald-600 hover:underline"
            >
              <Github className="w-4 h-4" /> GitHub
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
