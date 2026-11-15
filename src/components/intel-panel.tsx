"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Shield, ShieldAlert, Timer, EyeOff, MousePointerClick, Vibrate,
  LayoutGrid, Layers, Link2, Newspaper, Wrench, Globe, Ban, Landmark,
} from "lucide-react";

/* 基于 2026-11 市场调研整理：广告策略 vs 反制手段（来源见页脚） */
const STRATEGIES: {
  name: string;
  badge: string;
  badgeClass: string;
  icon: React.ElementType;
  desc: string;
  counter: string;
  impl: "已实现" | "部分实现" | "需配合" | "超出范围";
}[] = [
  {
    name: "倒计时跳过按钮", badge: "开屏", badgeClass: "bg-violet-50 text-violet-600 ring-violet-200", icon: Timer,
    desc: "3~5 秒倒计时，跳过按钮立即出现但越来越小，考验手速。",
    counter: "引擎在开屏时间窗内毫秒级识别「跳过」控件并点击，人机差距被抹平。",
    impl: "已实现",
  },
  {
    name: "延迟出现跳过按钮", badge: "开屏", badgeClass: "bg-violet-50 text-violet-600 ring-violet-200", icon: EyeOff,
    desc: "跳过按钮延迟 2~5 秒才出现，出现前无处可点，诱导误触广告主体。",
    counter: "持续监听窗口内容变化事件（TYPE_WINDOW_CONTENT_CHANGED），按钮一出现在控件树即被点击。",
    impl: "已实现",
  },
  {
    name: "假关闭按钮 ×", badge: "开屏/插屏", badgeClass: "bg-fuchsia-50 text-fuchsia-600 ring-fuchsia-200", icon: MousePointerClick,
    desc: "右上角 × 点击后不是关闭而是跳转落地页，真正的跳过入口是角落小字。",
    counter: "只匹配含「跳过/skip/关闭广告」字样的控件，永不盲点无字样的 × 图标。",
    impl: "已实现",
  },
  {
    name: "摇一摇传感器跳转", badge: "传感器", badgeClass: "bg-orange-50 text-orange-600 ring-orange-200", icon: Vibrate,
    desc: "读取加速度计/陀螺仪，晃动超阈值即跳转。澎湃 2026-11 实测 20 款 App：部分灵敏到“拿起手机即跳转”，含体感交互的开屏单日刊例价比普通开屏高 50 万元。2023 行业标准（2025-07 升级为《摇一摇广告触发行为安全要求》）已限定：加速度≥15m/s²、转角≥35°、操作≥3s。",
    counter: "引擎侧只点小面积控件、不碰中央热区；系统侧可关传感器权限——iOS 27.2 已提供中国区独享「限制运动数据」，华为/vivo 支持「仅开屏时禁止」。",
    impl: "部分实现",
  },
  {
    name: "全屏大热区 / 假关闭", badge: "开屏", badgeClass: "bg-violet-50 text-violet-600 ring-violet-200", icon: LayoutGrid,
    desc: "整个广告区域皆可点击，轻微触碰即被记为「有效点击」跳转。2026-04 实测：部分 App 把「跳过」换成「立即进入」，或让「取消」形同虚设——误触后点“取消”仍照转不误。",
    counter: "面积安全阀：只点击小于屏幕面积 35% 的控件，全屏热区直接忽略；只匹配含「跳过/skip」字样的控件，永不盲点无字样的 ×。",
    impl: "已实现",
  },
  {
    name: "强制观看插屏", badge: "插屏", badgeClass: "bg-fuchsia-50 text-fuchsia-600 ring-fuchsia-200", icon: Layers,
    desc: "内容跳转时弹出插屏，倒计时结束才解锁关闭按钮（强看 3~5 秒）。",
    counter: "解锁瞬间自动点击关闭（等待型反制）；强看时长本身无法缩短。",
    impl: "已实现",
  },
  {
    name: "连环跳转/二次跳转", badge: "跳转", badgeClass: "bg-fuchsia-50 text-fuchsia-600 ring-fuchsia-200", icon: Link2,
    desc: "开屏广告点击后经 deeplink 层层跳转（A页面→B页面→应用商店）。",
    counter: "收紧系统「关联启动/打开其他应用」权限，让跳转链在系统层断掉。",
    impl: "需配合",
  },
  {
    name: "原生信息流伪装", badge: "内容", badgeClass: "bg-emerald-50 text-emerald-600 ring-emerald-200", icon: Newspaper,
    desc: "广告伪装成正常内容混入信息流，标注极小。",
    counter: "不处理。内容类广告属于应用正常运营范畴，代点/屏蔽均有合规风险（参考「拦精灵」案边界）。",
    impl: "超出范围",
  },
];

const DEFENSES: { icon: React.ElementType; name: string; desc: string; limit: string }[] = [
  {
    icon: Shield, name: "无障碍代点（GKD / 李跳跳 / 雷跳跳 等）",
    desc: "UI 层读取控件树，定位「跳过」按钮后模拟点击；不碰网络、不需要 Root。GKD 社区订阅规则覆盖 678+ 应用。",
    limit: "局限：自绘控件（游戏引擎/Canvas）与没有跳过按钮的广告无效；李跳跳已于 2023.8 因律师函停更。",
  },
  {
    icon: Globe, name: "DNS / 私人 DNS 过滤",
    desc: "把广告素材域名解析到黑洞，让素材请求失败，广告位空手进入首页。",
    limit: "局限：与正文同域名的接口、已缓存素材、IP 直连广告拦不住。",
  },
  {
    icon: Ban, name: "本地 VPN 规则分流（Surge / Quantumult X 等）",
    desc: "起本地隧道做 DNS 分流 + 域名规则集，iOS 阵营主力方案。",
    limit: "局限：原生渲染广告与正文走同一主域名时无法精准区分。",
  },
  {
    icon: Wrench, name: "系统权限治理",
    desc: "关闭可疑应用传感器权限：iOS 27.2（2026-09）中国区独享「限制运动数据」可按应用切断加速度计/陀螺仪；华为、vivo 等支持「仅开屏时禁止获取动作与方向」；同时收紧「关联启动」权限阻断二跳。",
    limit: "局限：入口深，且部分开关只管自有广告、不管第三方 SDK。",
  },
  {
    icon: Landmark, name: "监管投诉",
    desc: "2023《互联网广告管理办法》要求一键关闭；2025-07 网安标委《摇一摇广告触发行为安全要求》；2026-04 市场监管总局启动互联网广告生态治理；2026-05-31 工信部通报 31 款 APP 及 SDK（7 款涉乱跳转）；2026-06 工信部专题会：约谈、通报、下架处置。遇违规可 12321/12300 举报。",
    limit: "局限：取证难，治理始终滞后于广告变体。",
  },
];

const IMPL_STYLE: Record<string, string> = {
  已实现: "bg-emerald-500",
  部分实现: "bg-amber-500",
  需配合: "bg-zinc-400",
  超出范围: "bg-zinc-300",
};

export function IntelPanel() {
  return (
    <div className="flex flex-col gap-4 p-4 lg:p-6">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-bold text-zinc-900">
          <ShieldAlert className="h-5 w-5 text-violet-600" /> 策略情报站 · 2026-11 市场扫描
        </h2>
        <p className="mt-1 text-xs text-zinc-500">
          基于 2026-11 最新公开报道整理（澎湃新闻 20 款 App 实测、工信部 2026 通报与专题会、市场监管总局生态治理、GKD/李跳跳社区、中青报·中青网、法治日报）。完整的对抗演练与真机自动跳过已打包进「开屏卫士」APK。
        </p>
      </div>

      {/* 广告策略 vs 反制 */}
      <div className="grid gap-3 md:grid-cols-2">
        {STRATEGIES.map((s) => (
          <Card key={s.name} className="gap-3 border-l-4 border-l-violet-500 py-4">
            <CardHeader className="flex-row items-center justify-between gap-2 py-0">
              <CardTitle className="flex items-center gap-2 text-sm">
                <s.icon className="h-4 w-4 shrink-0 text-violet-500" /> {s.name}
              </CardTitle>
              <span className={`shrink-0 rounded-md px-2 py-0.5 text-[10px] font-bold ring-1 ${s.badgeClass}`}>{s.badge}</span>
            </CardHeader>
            <CardContent className="space-y-2.5 px-4">
              <p className="text-xs leading-relaxed text-zinc-600">{s.desc}</p>
              <div className="rounded-lg bg-emerald-50 p-2.5 text-xs leading-relaxed text-zinc-700">
                <b className="text-emerald-600">🛡 反制：</b>{s.counter}
                <span className={`ml-2 inline-block rounded px-1.5 py-px text-[10px] font-bold text-white ${IMPL_STYLE[s.impl]}`}>
                  {s.impl}
                </span>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 六路防线 */}
      <Card>
        <CardHeader className="py-4">
          <CardTitle className="text-sm">🛡️ 防开屏广告方法全景（五路防线）</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 px-4">
          {DEFENSES.map((d, i) => (
            <div key={d.name} className="flex gap-3 border-b border-zinc-100 pb-3 last:border-0 last:pb-0">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-violet-50 text-violet-600">
                <d.icon className="h-4 w-4" />
              </span>
              <div className="min-w-0">
                <div className="flex items-baseline gap-2">
                  <span className="text-xs font-bold text-zinc-500">{i + 1}</span>
                  <span className="text-[13px] font-bold text-zinc-800">{d.name}</span>
                </div>
                <p className="mt-1 text-xs leading-relaxed text-zinc-600">{d.desc}</p>
                <p className="mt-0.5 text-[11px] text-zinc-400">{d.limit}</p>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      {/* 来源与数据 */}
      <Card>
        <CardHeader className="py-4">
          <CardTitle className="text-sm">📎 资料来源</CardTitle>
        </CardHeader>
        <CardContent className="px-4">
          <ul className="list-disc space-y-1.5 pl-4 text-[11px] leading-relaxed text-zinc-500">
            <li>中青报·中青网 / 法治日报《手机深处，广告在等你手滑》（2026-11）：开屏广告渲染链路、摇一摇行业标准、「拦精灵」案</li>
            <li>江苏省消保委调查（2023）：77.22% 受访者常遇「摇一摇」跳转，94.94% 表示厌恶</li>
            <li>GKD 开源项目（gkd.li）与 Adpro-Team 社区订阅规则：无障碍 + 订阅规则模式，覆盖 678+ 应用</li>
            <li>李跳跳官网存档：2023-08-24 宣布无限期停更（收到互联网大厂律师函）</li>
            <li>「拦精灵」案（2024，芒果TV 诉，两审均判不正当竞争成立、赔 9 万）：法院明确跳过类软件只能代点「跳过」、不得直接屏蔽广告</li>
            <li>澎湃新闻 / 21 世纪经济报道《实测 20 款 APP：体感开屏加价 50 万、误触也算点击》（2026-11-09）：拿起即跳转、假取消按钮、体感开屏刊例价</li>
            <li>《2026 年 App 开屏广告新套路事件》（2026-04，跳过键变身“立即进入”）与工信部《侵害用户权益行为的 APP(SDK) 通报》（2026-05-31，31 款、7 款涉乱跳转）</li>
            <li>网经社《导航 App “摇一摇”开屏广告险酿车祸，工信部出手整治》（2026-06）：高速颠簸触发跳转、专题会议与下架处置；新华网《开屏广告太添堵，怎么治》（2026-11）：市场监管总局生态治理与民生场景监管清单</li>
            <li>苹果 iOS 27.2 Beta「限制运动数据」（2026-09，中国区独享）：按应用切断加速度计/陀螺仪，从传感器源头废掉摇一摇广告</li>
          </ul>
          <div className="mt-3 rounded-lg bg-zinc-50 p-3 text-[11px] leading-relaxed text-zinc-500">
            ⚖️ 合规边界：本系统严格遵循「替用户点击跳过」的实现方式，不做网络层屏蔽、不修改其他应用。
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
