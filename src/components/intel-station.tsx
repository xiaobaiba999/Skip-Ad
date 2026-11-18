"use client";

/**
 * 情报站可视化看板（v5.4.0）
 * ─────────────────────────────────────────────────────────────
 * · KPI 行：累计拦截 / 成功率 / 平均耗时 / 预拦截 / 守护拦截 / 规则总数
 * · 图一 14 日拦截趋势（Area + Line） · 图二 应用排行 Top6（横向 Bar）
 * · 图三 24 小时时段分布（峰值 amber 高亮） · 图四 广告类型构成（donut）
 * · 图五 规则命中率 Top8 · 图六 功耗对照（v5.2.0 vs v5.4.0）
 * · 图七 分级功耗策略矩阵（policyMatrix 16 档 · 设备档位联动过滤）
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ResponsiveContainer, ComposedChart, Area, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip,
} from "recharts";
import type { TooltipProps } from "recharts";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";
import { policyMatrix, estimateEnergyPerBoot } from "@/lib/power-governor";
import type { DeviceTier, PowerPolicy } from "@/lib/power-governor";
import {
  Zap, TrendingUp, Timer, Sparkles, ShieldCheck, ListFilter, RefreshCw,
  BarChart3, Smartphone, Clock, PieChart as PieIcon, Target, BatteryFull, Cpu, Inbox,
} from "lucide-react";

/* ── 数据契约（/api/dashboard + /api/rules） ── */
interface DashTotals {
  blocks: number; misses: number; guardBlocks: number; avgLatencyMs: number;
  successRate: number; events: number;
}
interface DashData {
  totals: DashTotals;
  daily: { date: string; blocks: number; misses: number; preempts: number }[];
  perApp: { appName: string; category: string; count: number; avgMs: number }[];
  hours: number[];
  adTypes: { adType: string; label: string; count: number }[];
  ruleHit: { ruleName: string; count: number; avgMs: number }[];
  power: { v520: PowerSnap; v530: PowerSnap };
  sdkFamily: { family: string; label: string; count: number }[];
}
interface PowerSnap {
  label: string; scansPerBoot: number; microWh: number;
  burstFalseOpen?: number; hitRate: number;
  probesPerBoot?: number; steadyScansPerBoot?: number; steadyMicroWh?: number;
  cleanProfileWindows?: number;
}

/** 图表色板（无 indigo/blue：emerald/violet/amber/rose/teal/fuchsia/cyan/zinc） */
const PALETTE = ["#10b981", "#8b5cf6", "#f59e0b", "#f43f5e", "#14b8a6", "#d946ef", "#06b6d4", "#71717a"];

const TIP_STYLE = {
  fontSize: 11, borderRadius: 12, border: "1px solid #e4e4e7",
  padding: "4px 10px", boxShadow: "0 4px 12px rgba(0,0,0,.06)",
} as const;

const short = (s: string, n = 10) => (s.length > n ? `${s.slice(0, n)}…` : s);

const TIER_TABS: { key: DeviceTier; label: string }[] = [
  { key: "flagship", label: "旗舰" },
  { key: "high", label: "高端" },
  { key: "mid", label: "中端" },
  { key: "low", label: "入门" },
];

const BATTERY_META: Record<string, { label: string; cls: string }> = {
  charging: { label: "充电", cls: "bg-emerald-50 text-emerald-700 ring-emerald-200" },
  normal: { label: "正常", cls: "bg-zinc-50 text-zinc-600 ring-zinc-200" },
  low: { label: "低电", cls: "bg-amber-50 text-amber-700 ring-amber-200" },
  critical: { label: "极低", cls: "bg-rose-50 text-rose-700 ring-rose-200" },
};

/** 应用排行自定义 tooltip（拦截次数 + 平均耗时） */
function AppRankTip({ active, payload }: TooltipProps<number, string>) {
  if (!active || !payload?.length) return null;
  const d = payload[0]?.payload as DashData["perApp"][number] | undefined;
  if (!d) return null;
  return (
    <div className="rounded-lg border border-zinc-200 bg-white px-2.5 py-1.5 text-[11px] shadow-sm">
      <div className="font-bold text-zinc-800">{d.appName}<span className="ml-1 font-normal text-zinc-400">{d.category}</span></div>
      <div className="text-zinc-500">拦截 {d.count} 次 · 平均 {d.avgMs}ms</div>
    </div>
  );
}

/** 规则命中自定义 tooltip（次数 + 平均耗时） */
function RuleHitTip({ active, payload }: TooltipProps<number, string>) {
  if (!active || !payload?.length) return null;
  const d = payload[0]?.payload as DashData["ruleHit"][number] | undefined;
  if (!d) return null;
  return (
    <div className="max-w-[220px] rounded-lg border border-zinc-200 bg-white px-2.5 py-1.5 text-[11px] shadow-sm">
      <div className="font-bold text-zinc-800">{d.ruleName}</div>
      <div className="text-zinc-500">命中 {d.count} 次 · 平均 {d.avgMs}ms</div>
    </div>
  );
}

export function IntelStation() {
  const { toast } = useToast();
  const [data, setData] = useState<DashData | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [ruleTotal, setRuleTotal] = useState<number | null>(null);
  const [tier, setTier] = useState<DeviceTier>("flagship");

  /** 拉取看板聚合数据（纯取数，不含 setState） */
  const fetchDash = useCallback(async (): Promise<DashData | null> => {
    try {
      const r = await fetch("/api/dashboard");
      if (r.ok) return (await r.json()) as DashData;
    } catch { /* 落到失败分支 */ }
    return null;
  }, []);

  /** 手动刷新（按钮触发） */
  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    const d = await fetchDash();
    if (d) setData(d);
    else {
      setFailed(true);
      toast({ title: "看板数据加载失败", description: "请稍后重试，或到演练场产生拦截数据后再来看看", variant: "destructive" });
    }
    try {
      const r2 = await fetch("/api/rules");
      if (r2.ok) {
        const j = await r2.json();
        if (Array.isArray(j?.rules)) setRuleTotal(j.rules.length);
      }
    } catch { /* 规则总数非关键，静默 */ }
    setLoading(false);
  }, [fetchDash, toast]);

  useEffect(() => {
    let alive = true;
    fetchDash().then(async (d) => {
      if (!alive) return;
      if (d) setData(d);
      else setFailed(true);
      try {
        const r2 = await fetch("/api/rules");
        if (r2.ok) {
          const j = await r2.json();
          if (Array.isArray(j?.rules)) setRuleTotal(j.rules.length);
        }
      } catch { /* 规则总数非关键，静默 */ }
      if (alive) setLoading(false);
    });
    return () => { alive = false; };
  }, [fetchDash]);

  /* ── 分级功耗策略矩阵（纯函数，客户端直算） ── */
  const matrix = useMemo(() => policyMatrix(), []);
  const tierPolicies = useMemo(
    () => matrix.filter((p) => p.tier === tier),
    [matrix, tier],
  );
  const energyOf = useCallback((p: PowerPolicy) => estimateEnergyPerBoot(p), []);

  if (loading) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true" aria-label="看板加载中">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-[76px] rounded-2xl" />)}
        </div>
        <Skeleton className="h-[300px] rounded-2xl" />
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-[280px] rounded-2xl" />
          <Skeleton className="h-[280px] rounded-2xl" />
        </div>
      </div>
    );
  }

  if (failed || !data) {
    return (
      <div className="flex flex-col items-center gap-4 rounded-2xl border border-zinc-200 bg-white px-6 py-16 text-center">
        <Inbox className="h-10 w-10 text-zinc-300" />
        <div>
          <p className="text-sm font-bold text-zinc-700">看板数据加载失败</p>
          <p className="mt-1 text-[11px] text-zinc-400">网络或服务异常，稍后重试</p>
        </div>
        <Button size="sm" className="h-9 rounded-lg bg-zinc-900 hover:bg-zinc-700" onClick={() => void load()}>
          <RefreshCw className="mr-1 h-3.5 w-3.5" /> 重试
        </Button>
      </div>
    );
  }

  const { totals, daily, perApp, hours, adTypes, ruleHit, power, sdkFamily } = data;
  const preemptSum = daily.reduce((s, d) => s + d.preempts, 0);
  const hoursData = hours.map((n, h) => ({ hour: h, label: `${h}`, count: n }));
  const maxHour = Math.max(...hours, 0);
  const totalSdk = sdkFamily.reduce((s, x) => s + x.count, 0);
  const empty = totals.events === 0;

  const kpis = [
    { icon: Zap, label: "累计拦截", value: `${totals.blocks}`, sub: "次", tone: "text-emerald-600" },
    { icon: TrendingUp, label: "拦截成功率", value: `${Math.round(totals.successRate * 100)}`, sub: "%", tone: "text-teal-600" },
    { icon: Timer, label: "平均拦截耗时", value: totals.avgLatencyMs ? `${totals.avgLatencyMs}` : "—", sub: "ms", tone: "text-violet-600" },
    { icon: Sparkles, label: "预拦截（0ms 可见）", value: `${preemptSum}`, sub: "次", tone: "text-fuchsia-600" },
    { icon: ShieldCheck, label: "守护拦截", value: `${totals.guardBlocks}`, sub: "银行/政务/支付", tone: "text-rose-600" },
    { icon: ListFilter, label: "规则总数", value: ruleTotal !== null ? `${ruleTotal}` : "—", sub: "四源统一调度", tone: "text-cyan-600" },
  ];

  /* ── 功耗对照四指标（v5.2.0 真机实测 → v5.4.0 三零治理） ── */
  const powerMetrics = [
    {
      label: "每开机扫描次数",
      old: `${power.v520.scansPerBoot}`,
      now: `${power.v530.scansPerBoot}${power.v530.steadyScansPerBoot != null ? `（稳态 ${power.v530.steadyScansPerBoot}）` : ""}`,
      drop: Math.round((1 - power.v530.scansPerBoot / power.v520.scansPerBoot) * 100),
    },
    {
      label: "能耗估算 µWh",
      old: `${power.v520.microWh}`,
      now: `${power.v530.microWh}${power.v530.steadyMicroWh != null ? `（稳态 ${power.v530.steadyMicroWh}）` : ""}`,
      drop: Math.round((1 - power.v530.microWh / power.v520.microWh) * 100),
    },
    {
      label: "聊天突发误开窗",
      old: `${power.v520.burstFalseOpen ?? 0} 次/5分钟`,
      now: "预算制 2 次/10分钟",
      drop: 100,
    },
    {
      label: "广告命中率",
      old: `${power.v520.hitRate}%`,
      now: `${power.v530.hitRate}%（探针哨兵替班）`,
      drop: null as number | null,
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      {/* 头部 */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <BarChart3 className="h-5 w-5 text-emerald-600" /> 情报站 · 可视化看板
          </h2>
          <p className="mt-0.5 text-[11px] text-zinc-400">拦截趋势 / 应用排行 / 时段画像 / 类型构成 / 规则命中 / 功耗对照（东八区统计）</p>
        </div>
        <Button size="sm" variant="outline" className="h-8 rounded-lg" onClick={() => void load()}>
          <RefreshCw className="mr-1 h-3.5 w-3.5" /> 刷新
        </Button>
      </div>

      {/* KPI 行 */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {kpis.map(({ icon: Icon, label, value, sub, tone }) => (
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

      {empty ? (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-zinc-300 bg-white px-6 py-14 text-center">
          <Inbox className="h-10 w-10 text-zinc-300" />
          <p className="text-sm font-bold text-zinc-600">暂无拦截数据，去演练场试试</p>
          <p className="text-[11px] leading-relaxed text-zinc-400">
            到「演练场」启动任意应用触发几次拦截，这里会自动沉淀趋势、排行与时段画像
          </p>
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {/* 图一：14 日拦截趋势 */}
          <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:col-span-2" aria-label="14 日拦截趋势">
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
              <TrendingUp className="h-4 w-4 text-emerald-600" /> 14 日拦截趋势
            </h3>
            <p className="mt-0.5 text-[10px] text-zinc-400">emerald 面积 = 拦截 · rose 线 = 漏拦截 · violet 线 = 预拦截（东八区按日）</p>
            <div className="mt-3 h-60">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={daily} margin={{ top: 6, right: 8, bottom: 0, left: -18 }}>
                  <defs>
                    <linearGradient id="gBlocks" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#10b981" stopOpacity={0.32} />
                      <stop offset="100%" stopColor="#10b981" stopOpacity={0.04} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f4f4f5" />
                  <XAxis dataKey="date" tick={{ fontSize: 10, fill: "#a1a1aa" }} tickLine={false} axisLine={{ stroke: "#e4e4e7" }} interval="preserveStartEnd" />
                  <YAxis tick={{ fontSize: 10, fill: "#a1a1aa" }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip contentStyle={TIP_STYLE} itemStyle={{ fontSize: 11 }} labelStyle={{ fontSize: 11, fontWeight: 700, color: "#3f3f46" }} />
                  <Area type="monotone" dataKey="blocks" name="拦截" stroke="#10b981" strokeWidth={2} fill="url(#gBlocks)" />
                  <Line type="monotone" dataKey="misses" name="漏拦截" stroke="#f43f5e" strokeWidth={1.8} dot={{ r: 2, fill: "#f43f5e" }} />
                  <Line type="monotone" dataKey="preempts" name="预拦截" stroke="#8b5cf6" strokeWidth={1.8} dot={{ r: 2, fill: "#8b5cf6" }} />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          </section>

          {/* 图二：应用拦截排行 Top6 */}
          <section className="rounded-2xl border border-zinc-200 bg-white p-4" aria-label="应用拦截排行">
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
              <Smartphone className="h-4 w-4 text-teal-600" /> 应用拦截排行 Top6
            </h3>
            <p className="mt-0.5 text-[10px] text-zinc-400">按拦截次数降序 · 悬停查看平均耗时</p>
            <div className="mt-3 h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={perApp} layout="vertical" margin={{ top: 4, right: 24, bottom: 0, left: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f4f4f5" horizontal={false} />
                  <XAxis type="number" tick={{ fontSize: 10, fill: "#a1a1aa" }} tickLine={false} axisLine={{ stroke: "#e4e4e7" }} allowDecimals={false} />
                  <YAxis type="category" dataKey="appName" width={76} tick={{ fontSize: 10, fill: "#52525b" }} tickLine={false} axisLine={false} />
                  <Tooltip content={<AppRankTip />} cursor={{ fill: "rgba(20,184,166,0.06)" }} />
                  <Bar dataKey="count" name="拦截次数" fill="#14b8a6" radius={[0, 6, 6, 0]} barSize={14} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          {/* 图三：24 小时时段分布 */}
          <section className="rounded-2xl border border-zinc-200 bg-white p-4" aria-label="24 小时时段分布">
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
              <Clock className="h-4 w-4 text-violet-600" /> 24 小时时段分布
            </h3>
            <p className="mt-0.5 text-[10px] text-zinc-400">
              峰值时段 amber 高亮{maxHour > 0 ? ` · 峰值 ${maxHour} 点档` : ""}
            </p>
            <div className="mt-3 h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={hoursData} margin={{ top: 6, right: 8, bottom: 0, left: -18 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f4f4f5" />
                  <XAxis dataKey="label" tick={{ fontSize: 9, fill: "#a1a1aa" }} tickLine={false} axisLine={{ stroke: "#e4e4e7" }} interval={1} />
                  <YAxis tick={{ fontSize: 10, fill: "#a1a1aa" }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip
                    contentStyle={TIP_STYLE} itemStyle={{ fontSize: 11 }} labelStyle={{ fontSize: 11, fontWeight: 700, color: "#3f3f46" }}
                    formatter={(v: number | string) => [`${v} 次`, "事件数"]}
                    labelFormatter={(l) => `${l}:00 时段`}
                    cursor={{ fill: "rgba(139,92,246,0.06)" }}
                  />
                  <Bar dataKey="count" name="事件数" radius={[4, 4, 0, 0]}>
                    {hoursData.map((d) => (
                      <Cell key={d.hour} fill={maxHour > 0 && d.count === maxHour ? "#f59e0b" : d.count > 0 ? "#8b5cf6" : "#e4e4e7"} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          {/* 图四：广告类型构成 + SDK 家族 */}
          <section className="rounded-2xl border border-zinc-200 bg-white p-4" aria-label="广告类型构成">
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
              <PieIcon className="h-4 w-4 text-fuchsia-600" /> 广告类型构成
            </h3>
            <p className="mt-0.5 text-[10px] text-zinc-400">全部拦截事件按广告形态分布</p>
            <div className="mt-3 flex flex-col items-center gap-3 sm:flex-row">
              <div className="h-52 w-full flex-1">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={adTypes} dataKey="count" nameKey="label"
                      innerRadius="52%" outerRadius="82%" paddingAngle={2} strokeWidth={0}
                    >
                      {adTypes.map((_, i) => <Cell key={i} fill={PALETTE[i % PALETTE.length]} />)}
                    </Pie>
                    <Tooltip
                      contentStyle={TIP_STYLE} itemStyle={{ fontSize: 11 }}
                      formatter={(v: number | string, n: string) => [`${v} 次`, n]}
                    />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <ul className="grid w-full grid-cols-2 gap-x-4 gap-y-1.5 sm:w-48 sm:grid-cols-1">
                {adTypes.map((a, i) => (
                  <li key={a.adType} className="flex items-center gap-1.5 text-[11px]">
                    <span className="h-2 w-2 shrink-0 rounded-full" style={{ background: PALETTE[i % PALETTE.length] }} />
                    <span className="truncate text-zinc-600" title={a.label}>{a.label}</span>
                    <span className="ml-auto shrink-0 font-bold tabular-nums text-zinc-800">{a.count}</span>
                  </li>
                ))}
              </ul>
            </div>
            {totalSdk > 0 && (
              <div className="mt-3 border-t border-zinc-100 pt-2.5">
                <div className="text-[10px] font-bold text-zinc-400">SDK 家族分布</div>
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {sdkFamily.map((s) => (
                    <span key={s.family} className="rounded-full bg-zinc-50 px-2 py-0.5 text-[10px] font-semibold text-zinc-600 ring-1 ring-zinc-200">
                      {s.label} · {s.count}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </section>

          {/* 图五：规则命中率 Top8 */}
          <section className="rounded-2xl border border-zinc-200 bg-white p-4" aria-label="规则命中率 Top8">
            <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
              <Target className="h-4 w-4 text-cyan-600" /> 规则命中率 Top8
            </h3>
            <p className="mt-0.5 text-[10px] text-zinc-400">按命中规则（备注）聚合 · 悬停查看平均耗时</p>
            <div className="mt-3 h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={ruleHit.map((r) => ({ ...r, name: short(r.ruleName, 12) }))}
                  layout="vertical" margin={{ top: 4, right: 28, bottom: 0, left: 8 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="#f4f4f5" horizontal={false} />
                  <XAxis type="number" tick={{ fontSize: 10, fill: "#a1a1aa" }} tickLine={false} axisLine={{ stroke: "#e4e4e7" }} allowDecimals={false} />
                  <YAxis type="category" dataKey="name" width={110} tick={{ fontSize: 10, fill: "#52525b" }} tickLine={false} axisLine={false} />
                  <Tooltip content={<RuleHitTip />} cursor={{ fill: "rgba(6,182,212,0.06)" }} />
                  <Bar dataKey="count" name="命中次数" fill="#06b6d4" radius={[0, 6, 6, 0]} barSize={13} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          {/* 图六：功耗对照 */}
          <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:col-span-2" aria-label="功耗对照">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
                <BatteryFull className="h-4 w-4 text-emerald-600" /> 功耗对照
                <span className="rounded bg-emerald-50 px-1.5 py-0.5 text-[9px] font-bold text-emerald-700 ring-1 ring-emerald-200">三零治理</span>
              </h3>
              <div className="flex items-center gap-3 text-[10px] font-bold">
                <span className="text-zinc-400 line-through decoration-rose-300/70">{power.v520.label}</span>
                <span className="text-emerald-600">v5.4.0 三零治理</span>
              </div>
            </div>
            <p className="mt-1 text-[10px] text-zinc-400">真机日志 #57/#58（Xiaomi · Android 17）实测 → 治理后模型推算；稳态 = 宿主窗 CLEAN 档案跨重启保留后</p>
            <div className="mt-3 grid grid-cols-2 gap-3 md:grid-cols-4">
              {powerMetrics.map((m) => (
                <div key={m.label} className="rounded-xl bg-zinc-50 p-3 ring-1 ring-zinc-200">
                  <div className="text-[10px] font-semibold text-zinc-400">{m.label}</div>
                  <div className="mt-1 text-[13px] font-bold tabular-nums text-zinc-400 line-through decoration-rose-300/70">{m.old}</div>
                  <div className="mt-0.5 flex flex-wrap items-baseline gap-1">
                    <span className="text-[15px] font-black tabular-nums text-emerald-600">{m.now}</span>
                    {m.drop !== null && m.drop > 0 && (
                      <span className="rounded bg-emerald-50 px-1 py-0.5 text-[9px] font-black text-emerald-700 ring-1 ring-emerald-200">-{m.drop}%</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
            <p className="mt-2.5 text-[10px] leading-relaxed text-zinc-400">
              治理三零：CLEAN 档案窗【零扫描 · 零调度 · 零日志】（探针哨兵 findAccessibilityNodeInfosByText 替班，成本 ≈ DFS 的 1/14）；命中率红线：SDK 广告窗 / Dialog / 学习指纹窗永不抑制。
            </p>
          </section>
        </div>
      )}

      {/* 图七：分级功耗策略矩阵 */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4" aria-label="分级功耗策略矩阵">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="flex items-center gap-1.5 text-sm font-bold text-zinc-900">
            <Cpu className="h-4 w-4 text-amber-600" /> 分级功耗策略矩阵
            <span className="rounded bg-amber-50 px-1.5 py-0.5 text-[9px] font-bold text-amber-700 ring-1 ring-amber-200">16 档</span>
          </h3>
          {/* 设备档位 Segmented 选择器 */}
          <div className="flex items-center rounded-xl bg-zinc-100 p-1" role="radiogroup" aria-label="设备档位">
            {TIER_TABS.map((t) => (
              <button
                key={t.key}
                type="button"
                role="radio"
                aria-checked={tier === t.key}
                aria-label={`设备档位：${t.label}`}
                onClick={() => setTier(t.key)}
                className={cn(
                  "rounded-lg px-3 py-1.5 text-[11px] font-bold transition",
                  tier === t.key ? "bg-white text-zinc-900 shadow-sm" : "text-zinc-500 hover:text-zinc-800",
                )}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>
        <p className="mt-0.5 text-[10px] text-zinc-400">
          设备四档分级 × 电量四态联动动态降级 · 能耗为单次开机的扫描/探针/能耗估算 · 命中率红线（SDK 广告窗 / Dialog / 学习指纹窗）永不抑制
        </p>
        <div className="mt-3 overflow-x-auto rounded-xl ring-1 ring-zinc-200 [&::-webkit-scrollbar]:h-1.5 [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-zinc-300">
          <table className="w-full min-w-[860px] text-left text-[11px]">
            <thead>
              <tr className="border-b border-zinc-200 bg-zinc-50 text-[10px] font-bold text-zinc-500">
                <th className="px-3 py-2.5">档位</th>
                <th className="px-3 py-2.5">电量态</th>
                <th className="px-3 py-2.5">探针</th>
                <th className="px-3 py-2.5">扫描间隔</th>
                <th className="px-3 py-2.5">重试链</th>
                <th className="px-3 py-2.5">遍历深度</th>
                <th className="px-3 py-2.5">弹窗窗</th>
                <th className="px-3 py-2.5">学习</th>
                <th className="px-3 py-2.5">保活</th>
                <th className="px-3 py-2.5 text-right">能耗估算</th>
              </tr>
            </thead>
            <tbody>
              {tierPolicies.map((p) => {
                const e = energyOf(p);
                const bm = BATTERY_META[p.battery] ?? { label: p.battery, cls: "bg-zinc-50 text-zinc-600 ring-zinc-200" };
                return (
                  <tr key={`${p.tier}-${p.battery}`} className="border-b border-zinc-100 last:border-0 hover:bg-zinc-50/60">
                    <td className="px-3 py-2.5 font-bold text-zinc-800">{p.label}</td>
                    <td className="px-3 py-2.5">
                      <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-bold ring-1", bm.cls)}>{bm.label}</span>
                    </td>
                    <td className={cn("px-3 py-2.5 font-semibold", p.probeFirst ? "text-emerald-600" : "text-zinc-400")}>
                      {p.probeFirst ? "前置" : "—"}
                    </td>
                    <td className="px-3 py-2.5 tabular-nums text-zinc-600">{p.scanIntervalMs}ms</td>
                    <td className="px-3 py-2.5 font-mono text-[10px] text-zinc-600">{p.retryChain.join("/")}</td>
                    <td className="px-3 py-2.5 tabular-nums text-zinc-600">{p.maxDepth}</td>
                    <td className={cn("px-3 py-2.5 font-semibold", p.popupWatch ? "text-teal-600" : "text-zinc-400")}>
                      {p.popupWatch ? "监听" : "关闭"}
                    </td>
                    <td className={cn("px-3 py-2.5 font-semibold", p.learningEnabled ? "text-fuchsia-600" : "text-zinc-400")}>
                      {p.learningEnabled ? "开" : "停"}
                    </td>
                    <td className={cn("px-3 py-2.5 tabular-nums", p.keepAlivePing > 0 ? "text-zinc-600" : "text-zinc-400")}>
                      {p.keepAlivePing > 0 ? `${p.keepAlivePing}s` : "关"}
                    </td>
                    <td className="px-3 py-2.5 text-right">
                      <span className="font-black tabular-nums text-amber-600">{Math.round(e.microWh * 10) / 10}µWh</span>
                      <span className="ml-1 text-[9px] tabular-nums text-zinc-400">{e.scansPerBoot} 扫描 / {e.probesPerBoot} 探针</span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
