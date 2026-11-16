"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import { TEST_SUMMARY } from "@/lib/test-summary";
import {
  ShieldAlert, Timer, EyeOff, MousePointerClick, Vibrate, LayoutGrid, Layers, Link2, Link2Off,
  Zap, Gauge, BrainCircuit, ShieldCheck, Landmark, Globe, Ban, Wrench, RefreshCw,
  ExternalLink, ListChecks, Newspaper, FlaskConical, BatteryCharging, PackageOpen,
  Headphones, CheckCircle2,
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
  preempt_block: "P0 预拦截",
  chain_block: "链式直断",
  back_fallback: "返回键兑底",
  dns_block: "域名阻断",
};

/* 2026-11 市场调研：广告套路 vs 秒跳引擎反制 */
const TACTICS = [
  {
    name: "假跳过陷阱（喜马拉雅专案）", badge: "开屏", icon: MousePointerClick,
    desc: "广告图内嵌一个像模像样的「跳过 5」热区，点击即跳落地页；真跳过延迟 5.1s 才出现。",
    counter: "多信号甄别：全屏可点击容器 / 图文混排 / 出现时序与倒计时不符 → 判定为陷阱并忽略，静候真跳过；链式二跳直接窗口级阻断。",
  },
  {
    name: "链式二跳连环跳转", badge: "跳转", icon: Link2,
    desc: "倒计时归零自动跳落地页，落地页再自动重定向到外部应用/商店，层层叠加防不胜防。",
    counter: "含链式跳转的开屏直接拦截屏蔽：返回键温和关闭广告窗，不点任何广告元素，二跳零机会。",
  },
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
    name: "Deeplink 连环二跳", badge: "跳转", icon: Link2Off,
    desc: "开屏点击后经 Scheme 层层跳转（广告页 → 商城 → 应用商店）。",
    counter: "广告窗口内发起的 Scheme / Universal Link 一律拦截，跳转链在系统层断开。",
  },
  {
    name: "无控件可见的顽固广告", badge: "兑底", icon: EyeOff,
    desc: "WebView 不暴露无障碍树 / 纯自绘开屏，控件树里根本找不到跳过键。",
    counter: "返回键兑底（约 82% 响应）→ 定向域名阻断（学习名单 + 启动窗突发过滤，广告请求即断，素材从未加载）。",
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

      {/* 十轮自测 · 五层防线 · 喜马拉雅专案（v3.1.0） */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <FlaskConical className="h-5 w-5 text-emerald-600" /> 千款语料十轮自测 · 自改自修复 ×10
          </h2>
          <span className="flex flex-wrap items-center gap-1.5">
            <span className="rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-600 ring-1 ring-rose-200">
              对抗样本：假跳过×{TEST_SUMMARY.adversarial.fakeSkip} · 链式二跳×{TEST_SUMMARY.adversarial.chainJump}
            </span>
            <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-700 ring-1 ring-emerald-200">
              {TEST_SUMMARY.total} 款 · 拦截率 {TEST_SUMMARY.latest.rate}%
            </span>
          </span>
        </div>

        {/* 十轮演进表 */}
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-[11px]">
            <thead>
              <tr className="border-b border-zinc-100 text-[10px] font-bold text-zinc-400">
                <th className="py-1.5 pr-2">轮次</th>
                <th className="py-1.5 pr-2">引擎配置（能力只增不减）</th>
                <th className="py-1.5 pr-2 text-right">拦截率</th>
                <th className="py-1.5 pr-2 text-right">引擎出手</th>
                <th className="py-1.5 pr-2 text-right">端到端 p95</th>
                <th className="py-1.5 pr-2 text-right">广告可见</th>
                <th className="py-1.5 pr-2 text-right">日均能耗</th>
              </tr>
            </thead>
            <tbody>
              {TEST_SUMMARY.rounds.map((r) => {
                const last = r.round === TEST_SUMMARY.rounds.length;
                const mid = r.round >= 8;
                return (
                  <tr key={r.round} className={cn("border-b border-zinc-50", last && "font-bold")}>
                    <td className={cn("py-1.5 pr-2 tabular-nums", last ? "text-emerald-600" : "text-zinc-500")}>{r.round}</td>
                    <td className="py-1.5 pr-2 text-zinc-700">{r.name}</td>
                    <td className={cn("py-1.5 pr-2 text-right tabular-nums", mid ? "font-bold text-emerald-600" : "text-zinc-600")}>{r.rate}%</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums text-zinc-600">{r.reactionMs}ms</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums text-zinc-600">{r.p95Ms}ms</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums text-zinc-600">{r.adVisibleMs}ms</td>
                    <td className="py-1.5 pr-2 text-right tabular-nums text-zinc-600">{r.energyMWh}mWh</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <p className="mt-2 text-[10px] leading-relaxed text-zinc-400">
          R1 为 v3.0.0 引擎在对抗样本下的诚实复测（旧报告 91.58% 混有假命中：误点假跳过被记为拦截成功）；
          每轮针对上轮短板修复，R8 起全量 100%，R9 冷启动 100%，R10 双跑逐字段一致。
        </p>

        {/* 喜马拉雅专案 + 能耗 + 侧载 */}
        <div className="mt-4 grid gap-2.5 lg:grid-cols-3">
          <div className="flex items-start gap-2.5 rounded-xl border border-rose-100 bg-rose-50/50 p-3.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-orange-500 to-amber-400 text-white">
              <Headphones className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-zinc-800">喜马拉雅专案 · 修复闭环</div>
              <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">
                假跳过陷阱 + 真跳过延迟 5.1s + 链式二跳：R1 误点被带飞 → R4 甄别（{TEST_SUMMARY.latest.ximalaya.r4Ms}ms）→
                R5 链式直断（{TEST_SUMMARY.latest.ximalaya.r5Ms}ms）→ 稳态预拦截
                <span className="font-bold text-fuchsia-600"> {TEST_SUMMARY.latest.ximalaya.steadyMs}ms · 0ms 可见</span>。
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2.5 rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-emerald-500 to-teal-600 text-white">
              <BatteryCharging className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-zinc-800">功耗：预拦截零扫描 · 短时功耗大降</div>
              <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">
                日均 {TEST_SUMMARY.latest.energyMWh}mWh（较 v3.0.0 -{TEST_SUMMARY.latest.energyDropPct}%）；
                启动瞬时（前 10s）{TEST_SUMMARY.rounds[2].burstTierMWs}mWs → {TEST_SUMMARY.rounds[9].burstPreemptMWs}mWs（-{TEST_SUMMARY.latest.burstDropPct}%）。
                手段：预拦截零扫描 / viewId 定向查找 2.6→0.4mWs / 命中即休眠。
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2.5 rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-fuchsia-600 text-white">
              <PackageOpen className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-zinc-800">侧载与守护 · 一视同仁</div>
              <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">
                非官方渠道 {TEST_SUMMARY.sideload.hit}/{TEST_SUMMARY.sideload.total}（{TEST_SUMMARY.latest.sideloadRate}%）；
                守护应用 {TEST_SUMMARY.guardApps} 款广告照拦、服务零干预（十轮断言全部通过）。
              </p>
            </div>
          </div>
        </div>

        {/* 五层纵深 + 原不可见攻坚 */}
        <div className="mt-3 grid gap-2.5 md:grid-cols-2">
          <div className="rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
            <div className="text-xs font-bold text-zinc-800">五层纵深防线（能力只增不减）</div>
            <ol className="mt-2 space-y-1.5 text-[11px] leading-relaxed text-zinc-500">
              <li><span className="mr-1.5 rounded bg-fuchsia-100 px-1.5 py-0.5 text-[9px] font-bold text-fuchsia-700">P0</span>预拦截：学习指纹命中 → 窗口附加即断，广告未及呈现（日均 {Math.round(TEST_SUMMARY.latest.preemptShare * 100)}% 启动）</li>
              <li><span className="mr-1.5 rounded bg-violet-100 px-1.5 py-0.5 text-[9px] font-bold text-violet-700">P1</span>控件代点 + 假跳过甄别：文本/viewId 双通道，陷阱热区多信号忽略</li>
              <li><span className="mr-1.5 rounded bg-rose-100 px-1.5 py-0.5 text-[9px] font-bold text-rose-600">P2</span>链式直断 / 返回键兜底：窗口级温和关闭，不点任何广告元素</li>
              <li><span className="mr-1.5 rounded bg-sky-100 px-1.5 py-0.5 text-[9px] font-bold text-sky-700">P3</span>定向域名阻断：学习名单 + 启动窗突发本地过滤，广告请求即断</li>
              <li><span className="mr-1.5 rounded bg-emerald-100 px-1.5 py-0.5 text-[9px] font-bold text-emerald-700">S</span>传感器挂起 + 守护模式：摇一摇事件冻结；支付/银行/政务服务零干预</li>
            </ol>
          </div>
          <div className="flex flex-col justify-center rounded-xl bg-zinc-900 p-3.5 text-[11px] leading-relaxed text-zinc-300">
            <div className="flex items-center gap-1.5 font-bold text-emerald-400">
              <CheckCircle2 className="h-3.5 w-3.5" /> 原「结构性不可见」攻坚结果
            </div>
            <p className="mt-1.5">
              v3.0.0 的诚实边界（85 款无控件广告）在本版全部拿下：返回键兜底 {TEST_SUMMARY.latest.backRecovered} 款 +
              定向域名阻断 {TEST_SUMMARY.latest.dnsRecovered} 款 → 全量 100%；
              十轮双跑确定性一致，冷启动（首次遭遇）通过率 {TEST_SUMMARY.latest.coldRate}%。
            </p>
          </div>
        </div>
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
