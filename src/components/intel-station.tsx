"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import { TEST_SUMMARY } from "@/lib/test-summary";
import {
  ShieldAlert, Timer, EyeOff, MousePointerClick, Vibrate, LayoutGrid, Layers, Link2,
  Zap, Gauge, BrainCircuit, ShieldCheck, Landmark, Globe, Ban, Wrench, RefreshCw,
  ExternalLink, ListChecks, Newspaper, FlaskConical, BatteryCharging, PackageOpen, TrendingDown,
} from "lucide-react";

interface RuleRow {
  id: string;
  appName: string;
  appCategory: string;
  adType: string;
  matchLabel: string;
  pattern: string;
  action: string;
  source: string;
  confidence: number;
  hitCount: number;
  enabled: boolean;
}

interface StatsData {
  totals: { blocks: number; misses: number; guardBlocks: number; avgLatencyMs: number; successRate: number };
  rules: { preset: number; learned: number };
  feedbackCount: number;
}

const ACTION_LABEL: Record<string, string> = {
  instant_skip: "秒级代点",
  sensor_block: "传感器拦截",
  intent_block: "意图拦截",
  guard_skip: "守护代点",
};

/* 2026-11 市场调研：广告套路 vs 秒跳引擎反制 */
const TACTICS = [
  {
    name: "倒计时 + 小小跳过键", badge: "开屏", icon: Timer,
    desc: "3~5 秒倒计时，跳过键越做越小，考验手速。",
    counter: "引擎在时间窗内毫秒级定位「跳过」控件并代点，人机差距被抹平。",
  },
  {
    name: "延迟出现的跳过键", badge: "开屏", icon: EyeOff,
    desc: "跳过键延迟 2~5 秒才出现，出现前无处可点，诱导误触广告主体。",
    counter: "持续监听窗口内容变化（TYPE_WINDOW_CONTENT_CHANGED），按钮一出现在控件树即被点击。",
  },
  {
    name: "全屏热区 / 假关闭 ×", badge: "开屏/插屏", icon: LayoutGrid,
    desc: "整个广告区域皆可点击，轻微触碰即被记为「有效点击」；× 点了不关闭反而跳转。",
    counter: "面积安全阀：只代点 <35% 屏面积且带「跳过/Skip」字样的控件，全屏热区与无字 × 一律忽略。",
  },
  {
    name: "摇一摇传感器跳转", badge: "传感器", icon: Vibrate,
    desc: "读取加速度计，晃动超阈值即跳转。实测部分灵敏到「拿起手机即跳转」；行业标准（2023 / 2025-07 安全要求）：加速度 ≥15m/s²、转角 ≥35°、持续 ≥3s。",
    counter: "引擎侧挂起传感器监听，低于行业标准的激进阈值事件全部丢弃——复杂摇一摇广告也跳不出去。",
  },
  {
    name: "Deeplink 连环二跳", badge: "跳转", icon: Link2,
    desc: "开屏点击后经 Scheme 层层跳转（广告页 → 商城 → 应用商店）。",
    counter: "广告窗口内发起的 Scheme / Universal Link 一律拦截，跳转链在系统层断开。",
  },
  {
    name: "强制观看插屏", badge: "插屏", icon: Layers,
    desc: "内容跳转时弹出插屏，倒计时结束才解锁关闭按钮（强看 3~5 秒）。",
    counter: "解锁瞬间自动点击关闭；银行/政务/支付等守护应用同样秒关但绝不终止进程。",
  },
];

const DEFENSES = [
  { icon: Zap, name: "无障碍秒跳（本引擎 / GKD / 李跳跳同源）", desc: "读取控件树定位「跳过」按钮后毫秒级代点；不碰网络、无需 Root。GKD 社区订阅规则覆盖 678+ 应用。" },
  { icon: Globe, name: "DNS / 私人 DNS 过滤", desc: "广告素材域名指向黑洞，请求失败即空屏进首页；同主域接口与缓存素材拦不住。" },
  { icon: Ban, name: "本地 VPN 分流（Surge / QX 等）", desc: "本地隧道按域名规则集拦截，iOS 阵营主力；原生渲染广告无法精准区分。" },
  { icon: Wrench, name: "系统权限治理", desc: "iOS 27.2 中国区「限制运动数据」可按应用切断传感器；华为/vivo 支持仅开屏时禁止；收紧「关联启动」断二跳。" },
  { icon: Landmark, name: "监管与标准", desc: "《广告法》要求一键关闭；2025-07《摇一摇广告触发行为安全要求》限定触发条件；违规可 12321 / 12300 举报。" },
];

export function IntelStation({ onFeedback }: { onFeedback: () => void }) {
  const [stats, setStats] = useState<StatsData | null>(null);
  const [rules, setRules] = useState<RuleRow[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const [s, r] = await Promise.all([
        fetch("/api/stats").then((x) => (x.ok ? x.json() : null)),
        fetch("/api/rules").then((x) => (x.ok ? x.json() : null)),
      ]);
      if (s) setStats(s);
      if (r?.rules) setRules(r.rules);
    } catch { /* 静默 */ }
    setLoading(false);
  }, []);

  useEffect(() => {
    let alive = true;
    Promise.all([
      fetch("/api/stats").then((x) => (x.ok ? x.json() : null)),
      fetch("/api/rules").then((x) => (x.ok ? x.json() : null)),
    ])
      .then(([s, r]) => {
        if (!alive) return;
        if (s) setStats(s);
        if (r?.rules) setRules(r.rules);
        setLoading(false);
      })
      .catch(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []);

  const toggleRule = useCallback(async (id: string, enabled: boolean) => {
    setRules((cur) => cur.map((r) => (r.id === id ? { ...r, enabled } : r)));
    try {
      await fetch("/api/rules", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id, enabled }),
      });
    } catch { /* 静默 */ }
  }, []);

  const cards = [
    { icon: Zap, label: "累计拦截", value: stats ? `${stats.totals.blocks}` : "—", sub: "次", tone: "text-emerald-600" },
    { icon: Gauge, label: "平均拦截耗时", value: stats?.totals.avgLatencyMs ? `${stats.totals.avgLatencyMs}` : "—", sub: "ms", tone: "text-violet-600" },
    { icon: BrainCircuit, label: "学习规则", value: stats ? `${stats.rules.learned}` : "—", sub: `条 / 预置 ${stats?.rules.preset ?? "—"} 条`, tone: "text-fuchsia-600" },
    { icon: ShieldCheck, label: "守护拦截", value: stats ? `${stats.totals.guardBlocks}` : "—", sub: "银行/政务/支付", tone: "text-teal-600" },
  ];

  return (
    <div className="flex flex-col gap-5">
      {/* 统计卡 */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {cards.map(({ icon: Icon, label, value, sub, tone }) => (
          <div key={label} className="rounded-2xl border border-zinc-200 bg-white p-4">
            <div className="flex items-center gap-1.5 text-[11px] font-semibold text-zinc-400">
              <Icon className="h-3.5 w-3.5" /> {label}
            </div>
            <div className="mt-1.5 flex items-baseline gap-1">
              <span className={cn("text-2xl font-black tabular-nums", tone)}>{value}</span>
              <span className="text-[11px] font-semibold text-zinc-400">{sub}</span>
            </div>
          </div>
        ))}
      </div>

      {/* 千款自测 · 能耗 · 侧载（v3.0.0） */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <FlaskConical className="h-5 w-5 text-emerald-600" /> 千款语料自测 · 自查自改 ×3
          </h2>
          <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-700 ring-1 ring-emerald-200">
            {TEST_SUMMARY.total} 款 · 拦截率 {TEST_SUMMARY.latest.rate}% ≥ 90%
          </span>
        </div>

        {/* 三轮演进 */}
        <div className="mt-4 grid gap-2.5 md:grid-cols-3">
          {TEST_SUMMARY.rounds.map((r, i) => (
            <div key={r.round} className={cn("rounded-xl border p-3.5", i === TEST_SUMMARY.rounds.length - 1 ? "border-emerald-200 bg-emerald-50/50" : "border-zinc-100 bg-zinc-50/60")}>
              <div className="flex items-center gap-2">
                <span className={cn("flex h-6 w-6 items-center justify-center rounded-lg text-[11px] font-black", i === TEST_SUMMARY.rounds.length - 1 ? "bg-emerald-500 text-white" : "bg-zinc-300 text-white")}>{r.round}</span>
                <span className="text-xs font-bold text-zinc-800">{["预置全局规则（3.5s 窗口）", "+学习规则（viewId/时机 12s）", "+仅前台/预算 260/去抖"][i]}</span>
                <span className={cn("ml-auto text-lg font-black tabular-nums", i === TEST_SUMMARY.rounds.length - 1 ? "text-emerald-600" : "text-zinc-400")}>{r.rate}%</span>
              </div>
              <div className="mt-2 grid grid-cols-3 gap-1 text-center">
                <div><div className="text-[13px] font-black tabular-nums text-zinc-700">{r.reactionMs}ms</div><div className="text-[9px] text-zinc-400">引擎反应</div></div>
                <div><div className="text-[13px] font-black tabular-nums text-zinc-700">{r.p95Ms}ms</div><div className="text-[9px] text-zinc-400">端到端 p95</div></div>
                <div><div className="text-[13px] font-black tabular-nums text-zinc-700">{r.energyMWh}mWh</div><div className="text-[9px] text-zinc-400">日均能耗</div></div>
              </div>
              {i === 0 && <p className="mt-2 text-[10px] leading-relaxed text-zinc-400">短板：时间窗外跳过键 miss ×281、侧载冷启动无专属规则、能耗偏高</p>}
              {i === 1 && <p className="mt-2 text-[10px] leading-relaxed text-zinc-400">修复①：R1 全部 miss 沉淀学习规则，延迟跳过键与侧载冷启动恢复</p>}
              {i === 2 && <p className="mt-2 text-[10px] leading-relaxed text-zinc-400">修复②：能耗治理 -{TEST_SUMMARY.latest.energyDropPct}%，命中不回退</p>}
            </div>
          ))}
        </div>

        {/* 能耗与侧载两枚指标 */}
        <div className="mt-3 grid gap-2.5 md:grid-cols-2">
          <div className="flex items-start gap-2.5 rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-emerald-500 to-teal-600 text-white">
              <BatteryCharging className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-zinc-800">能耗：事件驱动 · 零轮询</div>
              <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">
                无障碍事件驱动，无任何后台轮询：日均仅 {TEST_SUMMARY.latest.energyMWh}mWh（4500mAh 电池的百万分之十七）。
                优化手段：仅响应前台窗口事件 / 扫描预算 600→260 / 去抖 250ms 合并重复内容变化 / 凌晨空闲静默。
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2.5 rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-fuchsia-600 text-white">
              <PackageOpen className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-zinc-800">侧载应用：官方商店与非官方渠道一视同仁</div>
              <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">
                非官方商店/官网 APK 装的第三方应用（占语料 25.4%）：无预置规则 → 通用规则兜底 + 学习沉淀。
                最新一轮侧载覆盖 {TEST_SUMMARY.sideload.hit}/{TEST_SUMMARY.sideload.total}（{TEST_SUMMARY.latest.sideloadRate}%）。
              </p>
            </div>
          </div>
        </div>

        {/* 诚实边界 */}
        <p className="mt-3 flex items-start gap-1.5 rounded-lg bg-zinc-900 p-2.5 text-[11px] leading-relaxed text-zinc-300">
          <TrendingDown className="mt-0.5 h-3.5 w-3.5 shrink-0 text-rose-400" />
          诚实边界：{TEST_SUMMARY.latest.missInvisible} 款（WebView 未暴露无障碍内容 / 纯自绘无控件）在代点边界内无解——
          已由反馈通道自动上报，并建议配合系统权限治理（关联启动 / 传感器权限）。
        </p>
      </section>

      {/* 市场套路 vs 反制 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <div className="flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <ShieldAlert className="h-5 w-5 text-violet-600" /> 市场套路 × 秒跳反制
          </h2>
          <span className="text-[10px] text-zinc-400">依据 2026-11 公开调研整理</span>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {TACTICS.map((t) => (
            <div key={t.name} className="flex flex-col gap-2 rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
              <div className="flex items-center gap-2">
                <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-rose-50 text-rose-500">
                  <t.icon className="h-4 w-4" />
                </span>
                <span className="text-xs font-bold text-zinc-800">{t.name}</span>
                <span className="ml-auto rounded bg-rose-100 px-1.5 py-0.5 text-[9px] font-bold text-rose-600">{t.badge}</span>
              </div>
              <p className="text-[11px] leading-relaxed text-zinc-500">{t.desc}</p>
              <p className="mt-auto flex gap-1.5 rounded-lg bg-emerald-50 p-2 text-[11px] leading-relaxed text-emerald-800">
                <Zap className="mt-0.5 h-3 w-3 shrink-0 text-emerald-600" /> {t.counter}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* 规则库 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <ListChecks className="h-5 w-5 text-violet-600" /> 规则库
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-[10px] font-semibold text-zinc-500">
              预置 {rules.filter((r) => r.source === "preset").length} · 学习 {rules.filter((r) => r.source === "learned").length}
            </span>
          </h2>
          <Button size="sm" variant="ghost" className="h-7 rounded-lg text-[11px] text-zinc-500" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={cn("mr-1 h-3 w-3", loading && "animate-spin")} /> 刷新
          </Button>
        </div>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-xs">
            <thead>
              <tr className="border-b border-zinc-100 text-[10px] font-bold text-zinc-400">
                <th className="py-2 pr-2">应用</th>
                <th className="py-2 pr-2">广告类型</th>
                <th className="py-2 pr-2">匹配方式</th>
                <th className="py-2 pr-2">规则特征</th>
                <th className="py-2 pr-2">动作</th>
                <th className="py-2 pr-2">来源</th>
                <th className="py-2 pr-2 text-right">命中</th>
                <th className="py-2 text-right">启用</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.id} className="border-b border-zinc-50 align-top last:border-0">
                  <td className="py-2.5 pr-2">
                    <div className="font-bold text-zinc-800">{r.appName === "*" ? "全局" : r.appName}</div>
                    <div className="text-[10px] text-zinc-400">{r.appCategory}</div>
                  </td>
                  <td className="py-2.5 pr-2 text-zinc-500">{adTypeShort(r.adType)}</td>
                  <td className="py-2.5 pr-2 text-zinc-500">{r.matchLabel}</td>
                  <td className="max-w-[280px] py-2.5 pr-2 text-[11px] leading-relaxed text-zinc-500">{r.pattern}</td>
                  <td className="py-2.5 pr-2">
                    <span className="rounded bg-violet-50 px-1.5 py-0.5 text-[10px] font-bold text-violet-700">
                      {ACTION_LABEL[r.action] ?? r.action}
                    </span>
                  </td>
                  <td className="py-2.5 pr-2">
                    <span className={cn("rounded px-1.5 py-0.5 text-[10px] font-bold", r.source === "learned" ? "bg-fuchsia-100 text-fuchsia-700" : "bg-zinc-100 text-zinc-500")}>
                      {r.source === "learned" ? "学习" : "预置"}
                    </span>
                  </td>
                  <td className="py-2.5 pr-2 text-right font-bold tabular-nums text-zinc-700">{r.hitCount}</td>
                  <td className="py-2.5 text-right">
                    <Switch checked={r.enabled} onCheckedChange={(v) => void toggleRule(r.id, v)} aria-label={`规则开关 ${r.appName}`} />
                  </td>
                </tr>
              ))}
              {rules.length === 0 && (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-zinc-400">
                    {loading ? "规则库加载中…" : "暂无规则"}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* 防线对比 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
          <Layers className="h-5 w-5 text-violet-600" /> 市面防线对比
        </h2>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {DEFENSES.map((d, i) => (
            <div key={d.name} className={cn("rounded-xl border p-3.5", i === 0 ? "border-emerald-200 bg-emerald-50/50" : "border-zinc-100 bg-zinc-50/60")}>
              <div className="flex items-center gap-2">
                <span className={cn("flex h-7 w-7 items-center justify-center rounded-lg", i === 0 ? "bg-emerald-500 text-white" : "bg-zinc-200/70 text-zinc-500")}>
                  <d.icon className="h-4 w-4" />
                </span>
                <span className="text-xs font-bold text-zinc-800">{d.name}</span>
              </div>
              <p className="mt-2 text-[11px] leading-relaxed text-zinc-500">{d.desc}</p>
            </div>
          ))}
          <div className="flex flex-col items-start justify-center gap-2 rounded-xl border border-dashed border-violet-200 bg-violet-50/40 p-3.5">
            <div className="flex items-center gap-2 text-xs font-bold text-violet-800">
              <Newspaper className="h-4 w-4" /> 内容流「软广」不处理
            </div>
            <p className="text-[11px] leading-relaxed text-zinc-500">
              伪装成正常内容的广告属于应用正常运营范畴，代点/屏蔽均有合规风险（参考「拦精灵」案边界：只代点「跳过」，不直接屏蔽广告本身）。
            </p>
          </div>
        </div>
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-zinc-900 p-3.5 text-white">
          <div className="text-[11px] leading-relaxed">
            <span className="font-bold text-emerald-400">遇到漏拦截 / 误拦截？</span>
            一键反馈直达开发者 GitHub Issues（内置加密通道，无需任何配置）。
          </div>
          <Button size="sm" data-testid="btn-intel-feedback" className="h-8 rounded-lg bg-emerald-500 hover:bg-emerald-400" onClick={onFeedback}>
            提交反馈 <ExternalLink className="ml-1 h-3 w-3" />
          </Button>
        </div>
      </section>
    </div>
  );
}

function adTypeShort(t: string): string {
  return { splash: "开屏", interstitial: "插屏", shake: "摇一摇", deeplink: "跳转", any: "任意" }[t] ?? t;
}
