"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import { TEST_SUMMARY } from "@/lib/test-summary";
import {
  ShieldAlert, Timer, EyeOff, MousePointerClick, Vibrate, LayoutGrid, Layers, Link2, Link2Off,
  Zap, Gauge, BrainCircuit, ShieldCheck, Landmark, Globe, Ban, Wrench, RefreshCw,
  ExternalLink, ListChecks, Newspaper, FlaskConical, BatteryCharging,
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
  gyro_block: "陀螺仪直断",
  sdk_window_cut: "SDK 窗直断",
  coord_tap: "角落/坐标点击",
  popup_close: "弹窗关闭",
  back_fallback: "返回键兑底",
  dns_block: "域名阻断",
};

/* 2026-11 市场调研 + 真机反馈：广告套路 vs 秒跳引擎反制（v3.3.0） */
const TACTICS = [
  {
    name: "第一方图片开屏（微博/喜马拉雅实测）", badge: "v3.4.0 根因修复", icon: Zap,
    desc: "应用自建广告系统渲染：窗口类名是应用自己的 Activity（不匹配任何 SDK 指纹）、跳过按钮纯图片（零文本零 contentDescription）、倒计时烧在图里——v3.3.0 全部识别手段零信号，广告完整展示（二次真机反馈「还是拦截不了」的根因）。",
    counter: "角落图片跳过键识别（顶部 14% 高、贴右/左缘、小面积可点、无文本）→ 手势点击其中心 650ms；连角落控件都没有时坐标级兜底（点标准跳过位 88%宽/7%高）；performAction 失效自动降级 dispatchGesture 真实触点；防误点六重约束（信息流/输入框/隐私弹窗等 12 个误报场景 JVM 用例验证）。",
  },
  {
    name: "SDK 广告窗 · 零文本图片广告", badge: "v3.3.0", icon: Layers,
    desc: "第三方 SDK 承载的开屏（穿山甲/优量汇/快手/AdMob）：文案烧在图里，但窗口类名带 SDK 特征。",
    counter: "广告窗 className 指纹分段精确匹配 → 500ms 内无真跳过键即返回键直断，零文本依赖；直断后沉淀指纹，次启 300ms 快断。",
  },
  {
    name: "翻转手机 · 陀螺仪诱导（喜马拉雅实测）", badge: "传感器", icon: Vibrate,
    desc: "开屏底部提示「翻转手机，跳转详情页或第三方应用」，无倒计时，拿起手机就跳（实测快至 600~900ms）——无障碍无法冻结应用内的传感器监听。",
    counter: "四层防线：无倒计时立即直断（~120ms 抢在任何翻转前）；SDK 窗 500ms 直断；0.8s 无真跳过键返回键直断；跳转已发生 → 任意目标回弹送回原应用。",
  },
  {
    name: "链式二跳连环跳转", badge: "跳转", icon: Link2,
    desc: "倒计时归零或翻转触发自动跳落地页，再重定向到外部应用/商店/浏览器，层层叠加防不胜防；跳转目标常不在旧回弹名单（微信/浏览器/未知应用）。",
    counter: "回弹名单扩充至微信/QQ/支付宝/美团/知乎/小红书/B站/主流浏览器；引擎仍见到广告信号时任意跳转目标都弹回（广告窗活跃回弹）；广告判定 3s 内同包拉起 WebView 落地页同样弹回。",
  },
  {
    name: "应用内弹窗广告（含覆盖层）", badge: "弹窗 v2", icon: LayoutGrid,
    desc: "使用中突然弹出的广告弹窗：带「广告」角标或纯营销话术，关闭键极小；覆盖层弹窗不触发窗口事件，用一会儿才弹的更是完全游离在旧引擎能力之外。",
    counter: "弹窗窗期独立于开屏 12s 窗（v3.2.0 真机 bug 修复）；角标扩充（广告/推广/赞助/AD/Sponsored）+ 关闭键代点；Dialog 形态 + ≥2 条营销话术弱信号；覆盖层内容突发检测（1.5s 内 ≥3 次内容变化）。",
  },
  {
    name: "假跳过陷阱（喜马拉雅专案）", badge: "开屏", icon: MousePointerClick,
    desc: "广告图内嵌一个像模像样的「跳过」热区，点击即跳落地页；真跳过延迟 5.1s 才出现。",
    counter: "多信号甄别：全屏可点击容器 / 图文混排 / 出现时序与倒计时不符 → 判定为陷阱并忽略，静候真跳过；链式二跳直接窗口级阻断。",
  },
  {
    name: "无倒计时 · 跳过广告", badge: "开屏", icon: Timer,
    desc: "有的广告带倒计时，有的不带（喜马拉雅这类就没有）——右上角直接是「跳过广告」，但被广告方延迟挂载或深嵌套。",
    counter: "极速链：窗口切换 0ms 即首扫 + 前密后疏重试链 [0/120/240/400/650/1000ms…]，「跳过广告」文案直配，命中即熔断。",
  },
  {
    name: "延迟出现的跳过键", badge: "开屏", icon: EyeOff,
    desc: "跳过键延迟 2~5 秒才出现，出现前无处可点，诱导误触广告主体。",
    counter: "12s 时间窗 + 重试链持续监听窗口内容变化，按钮一出现在控件树即被点击。",
  },
  {
    name: "全屏热区 / 假关闭 ×", badge: "开屏/插屏", icon: LayoutGrid,
    desc: "整个广告区域皆可点击，轻微触碰即被记为「有效点击」；× 点了不关闭反而跳转。",
    counter: "面积安全阀：只代点 <35% 屏面积且带「跳过/Skip」字样的控件；v3.3.0 反向利用热区特征——≥55% 屏幕可点 + 节点≤12 + 无输入框三条件判定图片广告 → 900ms 无真跳过键直断（守护名单豁免）。",
  },
  {
    name: "摇一摇传感器跳转", badge: "传感器", icon: Vibrate,
    desc: "读取加速度计，晃动超阈值即跳转。实测部分灵敏到「拿起手机即跳转」；行业标准（2023 / 2025-07 安全要求）：加速度 ≥15m/s²、转角 ≥35°、持续 ≥3s。",
    counter: "识别摇一摇文案（无倒计时）→ 首扫立即直断（~120ms）；0.8s 无真跳过键返回键直断；跳转已发生则任意目标回弹。",
  },
  {
    name: "Deeplink 连环二跳", badge: "跳转", icon: Link2Off,
    desc: "开屏点击后经 Scheme 层层跳转（广告页 → 商城 → 应用商店）。",
    counter: "代点真跳过 + SDK 窗直断抢在倒计时归零前；跳转已发生 → 回弹 + 落地页弹回，链路彻底断开。",
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

      {/* 十八轮自测 · 诚实重构 · 喜马拉雅专案 v4（v3.4.0） */}
      <section className="rounded-2xl border border-zinc-200 bg-white p-4 lg:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="flex items-center gap-2 text-base font-bold text-zinc-900">
            <FlaskConical className="h-5 w-5 text-emerald-600" /> 千款语料十八轮自测 · 诚实重构 · 自改自修复 ×18
          </h2>
          <span className="flex flex-wrap items-center gap-1.5">
            <span className="rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-600 ring-1 ring-rose-200">
              对抗样本：假跳过×{TEST_SUMMARY.adversarial.fakeSkip} · 链式×{TEST_SUMMARY.adversarial.chainJump} · 陀螺仪×{TEST_SUMMARY.adversarial.gyro} · 弹窗×{TEST_SUMMARY.adversarial.popup}
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
                const mid = r.round >= 10;
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
          R1 为 v3.1.0 引擎在真机反馈建模下的诚实复测；R6 为 v3.2.0 终态诚实命中率 {TEST_SUMMARY.latest.v320Rate}%（旧模型把「返回键兑底/域名阻断」计为能力，
          真机证伪）；R7-R10 为 v3.3.0 四项攻坚（SDK 广告窗 className 指纹 / 立即直断·热区·重试 / 弹窗 v2 / 回弹扩充）逐项验证；
          R10 起全量 100%，R13 冷启动（首次遭遇）100%，R14 双跑逐字段一致。
        </p>

          {/* 喜马拉雅专案 v3 + 陀螺仪/弹窗 + 能耗 */}
        <div className="mt-4 grid gap-2.5 lg:grid-cols-3">
          <div className="flex items-start gap-2.5 rounded-xl border border-rose-100 bg-rose-50/50 p-3.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-orange-500 to-amber-400 text-white">
              <Headphones className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-zinc-800">喜马拉雅专案 v4 · 修复闭环</div>
              <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">
                第一方图片开屏（零文本零类名信号）+ 翻转手机 {TEST_SUMMARY.latest.ximalaya.flipTrigMs}ms 即跳 + 浏览器二跳：
                R15 v3.3.0 零信号 → 翻转触发后才回弹（广告已展示 {TEST_SUMMARY.latest.ximalaya.r15VisibleMs}ms）→
                R16 角落图片跳过键 {TEST_SUMMARY.latest.ximalaya.cornerMs}ms 抢在翻转前 → 稳态预拦截
                <span className="font-bold text-fuchsia-600"> {TEST_SUMMARY.latest.ximalaya.steadyMs}ms · 0ms 可见</span>。
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2.5 rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-rose-500 to-orange-400 text-white">
              <Vibrate className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-zinc-800">图片开屏 × 陀螺仪 × 弹窗全断</div>
              <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">
                第一方图片开屏 {TEST_SUMMARY.v340.imageSkip} 款全拦（角落键 ×{TEST_SUMMARY.v340.cornerHit} + 坐标兜底 ×{TEST_SUMMARY.v340.coordHit}）；
                翻转手机类 {TEST_SUMMARY.adversarial.gyro} 款全断；弹窗 {TEST_SUMMARY.adversarial.popup} 款全关（覆盖层 {TEST_SUMMARY.v330.overlayPopup} · 12s 窗外 {TEST_SUMMARY.v330.latePopup}）；
                零文本 SDK 窗 ×{TEST_SUMMARY.v330.sdkAd} 首次可判。
              </p>
            </div>
          </div>
          <div className="flex items-start gap-2.5 rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-emerald-500 to-teal-600 text-white">
              <BatteryCharging className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <div className="text-xs font-bold text-zinc-800">功耗：能力增强反而更省</div>
              <p className="mt-1 text-[11px] leading-relaxed text-zinc-500">
                日均 {TEST_SUMMARY.latest.energyMWh}mWh（较 R1 -{TEST_SUMMARY.latest.energyDropPct}%）；
                预拦截占比 {Math.round(TEST_SUMMARY.latest.preemptShare * 100)}%（零扫描零渲染等待）；
                手段：事件合并 120ms / 命中即熔断 / className 免文本识别（扫描更少）。
              </p>
            </div>
          </div>
        </div>

        {/* 七层纵深 + 真机反馈闭环 */}
        <div className="mt-3 grid gap-2.5 md:grid-cols-2">
          <div className="rounded-xl border border-zinc-100 bg-zinc-50/60 p-3.5">
            <div className="text-xs font-bold text-zinc-800">八层纵深防线（能力只增不减）</div>
            <ol className="mt-2 space-y-1.5 text-[11px] leading-relaxed text-zinc-500">
              <li><span className="mr-1.5 rounded bg-fuchsia-100 px-1.5 py-0.5 text-[9px] font-bold text-fuchsia-700">P0</span>预拦截：学习指纹命中 → 窗口附加即断，广告未及呈现（日均 {Math.round(TEST_SUMMARY.latest.preemptShare * 100)}% 启动）</li>
              <li><span className="mr-1.5 rounded bg-rose-100 px-1.5 py-0.5 text-[9px] font-bold text-rose-600">P0.7</span>SDK 广告窗识别：className 指纹零文本可判（500ms 直断）+ 指纹学习（次启 300ms 快断）</li>
              <li><span className="mr-1.5 rounded bg-amber-100 px-1.5 py-0.5 text-[9px] font-bold text-amber-700">P0.8</span>【v3.4.0】角落图片跳过键：无字小可点控件 → 手势点击中心 650ms；坐标级兜底：零信号简单树 → 点标准跳过位（防误点六重约束）</li>
              <li><span className="mr-1.5 rounded bg-violet-100 px-1.5 py-0.5 text-[9px] font-bold text-violet-700">P0.5</span>极速链：窗口切换 0ms 首扫 + 前密后疏重试链，命中即熔断</li>
              <li><span className="mr-1.5 rounded bg-violet-100 px-1.5 py-0.5 text-[9px] font-bold text-violet-700">P1</span>控件代点 + 假跳过甄别：文本/viewId 双通道，陷阱热区多信号忽略</li>
              <li><span className="mr-1.5 rounded bg-rose-100 px-1.5 py-0.5 text-[9px] font-bold text-rose-600">P2</span>立即直断/热区直断 + 链式回弹：翻转/二跳零机会，只关窗不碰服务</li>
              <li><span className="mr-1.5 rounded bg-amber-100 px-1.5 py-0.5 text-[9px] font-bold text-amber-700">P2.6</span>弹窗广告 v2：角标/营销话术 + 覆盖层突发检测，开屏窗外弹窗照关</li>
              <li><span className="mr-1.5 rounded bg-sky-100 px-1.5 py-0.5 text-[9px] font-bold text-sky-700">P3</span>返回键重试 + 广告窗活跃回弹：拦返回键的广告补一次，任意目标跳转都弹回</li>
              <li><span className="mr-1.5 rounded bg-emerald-100 px-1.5 py-0.5 text-[9px] font-bold text-emerald-700">S</span>守护模式：支付/银行/政务广告照拦、服务零干预</li>
            </ol>
          </div>
          <div className="flex flex-col justify-center rounded-xl bg-zinc-900 p-3.5 text-[11px] leading-relaxed text-zinc-300">
            <div className="flex items-center gap-1.5 font-bold text-emerald-400">
              <CheckCircle2 className="h-3.5 w-3.5" /> 真机反馈 → 复现 → 修复闭环
            </div>
            <p className="mt-1.5">
              两轮真机反馈逐项复现并修复：① 微博/喜马拉雅第一方图片开屏仍完整展示（v3.4.0 角落键 ×{TEST_SUMMARY.v340.cornerHit} + 坐标兜底 ×{TEST_SUMMARY.v340.coordHit} 全拦，
              决策核心 JVM 38 用例验证含 12 个误报防护场景）；② SDK 窗零文本 ×{TEST_SUMMARY.v330.sdkAd} → className 指纹；
              ③ 手机随便一动就跳（陀螺仪 ×{TEST_SUMMARY.adversarial.gyro} 全断）；④ 链式跳转（回弹扩充）；⑤ 弹窗 ×{TEST_SUMMARY.adversarial.popup} 全关（含覆盖层/窗外）。
              侧载 {TEST_SUMMARY.sideload.hit}/{TEST_SUMMARY.sideload.total}（{TEST_SUMMARY.latest.sideloadRate}%）· 守护 {TEST_SUMMARY.guardApps} 款零干预 · 冷启动 {TEST_SUMMARY.latest.coldRate}%。
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
  return { splash: "开屏", interstitial: "插屏", shake: "摇一摇", deeplink: "跳转", popup: "弹窗", any: "任意" }[t] ?? t;
}
