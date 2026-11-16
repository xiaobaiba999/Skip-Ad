import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import {
  ShieldCheck,
  Zap,
  Anchor,
  MousePointerClick,
  Ban,
  Crosshair,
  ListChecks,
  GitBranch,
  Download,
  Smartphone,
  Cpu,
  CheckCircle2,
  XCircle,
  ArrowRight,
  RefreshCw,
  Gauge,
  Layers,
} from "lucide-react";

const stats = [
  { label: "JVM 测试用例", value: "137", sub: "全绿 · 含 25 条规则引擎新用例" },
  { label: "规则引擎出手", value: "0ms", sub: "事件驱动 · 露头就秒跳（Keep 级）" },
  { label: "APK 体积", value: "104KB", sub: "规则全部内嵌 · 零云端依赖" },
  { label: "固定坐标盲点", value: "0 处", sub: "只点控件自身 bounds 中心" },
];

const fixes = [
  {
    icon: Ban,
    tag: "issue #12 整治",
    title: "QQ / 微博 / 抖音闪退根治",
    color: "text-red-600 bg-red-50 border-red-200",
    iconColor: "text-red-600",
    points: [
      { bad: true, text: "毒链（旧版）：桌面被误判为广告窗 → 盲点固定坐标点中桌面图标 → 拉起微信/QQ/抖音 → 回弹狂按返回键把你踢出去" },
      { bad: false, text: "三刀根治：桌面/Launcher 事件入口黑名单（绝不扫描、绝不学习、绝不回弹）" },
      { bad: false, text: "坐标级盲点兜底整体移除——每台手机屏幕大小不一样，固定百分比坐标必然乱点" },
      { bad: false, text: "启动自动清理桌面毒位置规则（真机实锤 com.miui.home 学出位置(39%,18%)）" },
    ],
  },
  {
    icon: Anchor,
    tag: "保活强化",
    title: "无障碍退出后不用频繁重开",
    color: "text-amber-600 bg-amber-50 border-amber-200",
    iconColor: "text-amber-600",
    points: [
      { bad: false, text: "心跳自检 15 分钟缩短到 5 分钟：服务被杀最多 5 分钟内发现并自愈或提醒" },
      { bad: false, text: "adb 一次性授权 WRITE_SECURE_SETTINGS 后，清理后台/覆盖安装导致的服务断开全自动恢复" },
      { bad: false, text: "厂商 ROM 三步指引保留：任务锁定 + 省电无限制 + 自启动（MIUI 的 10 秒确认框是系统安全设计，做好这三步后基本不再触发）" },
      { bad: false, text: "前台常驻通知 + onUnbind 保留实例 + 覆盖安装/重启断连提醒，全部沿用" },
    ],
  },
  {
    icon: Zap,
    tag: "以 Keep 开屏为标杆",
    title: "露头就秒跳 · 速度全面提速",
    color: "text-emerald-600 bg-emerald-50 border-emerald-200",
    iconColor: "text-emerald-600",
    points: [
      { bad: false, text: "扫描链前段加密：0 / 60 / 120 / 200 / 300 / 450 / 650 / 900ms——跳过键渲染一完成即命中" },
      { bad: false, text: "学习位置出手 500ms → 100ms；关闭键 600ms → 250ms；角落键 500ms → 300ms" },
      { bad: false, text: "点击冷却 1.2s → 0.9s；内容变化节流 150ms → 80ms；无障碍事件批量 120ms → 80ms" },
      { bad: false, text: "倒计时门控保留（「跳过 5」类按钮等到快激活时出手，出手即命中，不浪费冷却）" },
    ],
  },
];

const refactorRows = [
  { aspect: "实现方法论", before: "十一层决策阶梯，复杂信号推断", after: "李跳跳式内嵌规则引擎为 主路径，复杂信号退居兜底" },
  { aspect: "规则存储", before: "无内置规则，全靠运行时学习", after: "规则内嵌 APK（全局文案两级匹配 + vid 匹配），零云端零配置" },
  { aspect: "点击方式", before: "固定百分比坐标兜底（88%,7% 等）", after: "只点规则命中的控件自身 bounds 中心，适配任意分辨率" },
  { aspect: "出手时机", before: "文本键即时，角落键 500ms，兜底 900ms", after: "规则命中 0ms；学习位置 100ms；关闭键 250ms" },
  { aspect: "防误点", before: "弹窗内容突发检测常开（美团等大量误报）", after: "弹窗突发默认关闭；角落键加广告形态确认门（五选一）" },
  { aspect: "桌面处理", before: "被当普通应用扫描 → 误判 → 乱点图标", after: "事件入口直接丢弃，绝不扫描/学习/回弹" },
  { aspect: "学习与反馈", before: "学习中心 + GitHub 日志上传", after: "完整保留（文本/位置/指纹学习 + issue 反馈通道）" },
];

const rulePipeline = [
  { icon: Smartphone, title: "窗口/内容事件", desc: "无障碍服务事件驱动（桌面黑名单先过滤）" },
  { icon: Cpu, title: "规则引擎匹配", desc: "精确文案 → 短包含(≤6字) → vid（skip/倒计时），可见 + 面积≤35% + 非诱导" },
  { icon: Crosshair, title: "控件中心点击", desc: "dispatchGesture 点控件自身 bounds 中心（真实触点，任意分辨率）" },
  { icon: RefreshCw, title: "复验与门控", desc: "点后复验未关换候选；带倒计时的等激活时机；同窗预算用尽优雅收手" },
];

export default function Home() {
  return (
    <div className="min-h-screen flex flex-col bg-gradient-to-b from-emerald-50/60 via-white to-white">
      {/* Hero */}
      <header className="w-full">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 pt-12 pb-8 sm:pt-16">
          <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center gap-2">
              <Badge className="bg-emerald-600 hover:bg-emerald-600 text-white">开屏卫士 v3.9.0</Badge>
              <Badge variant="outline" className="border-emerald-300 text-emerald-700">李跳跳方法论重构</Badge>
              <Badge variant="outline">Android 7.0+</Badge>
            </div>
            <h1 className="text-3xl sm:text-4xl lg:text-5xl font-bold tracking-tight text-neutral-900">
              露头就秒跳，
              <span className="text-emerald-600">绝不乱点</span>
            </h1>
            <p className="text-base sm:text-lg text-neutral-600 leading-relaxed max-w-3xl">
              本次按李跳跳最新版实现逻辑一比一重构：规则直接内嵌 APK，只认控件属性、只点控件自身位置（每台手机屏幕大小不一样，
              固定坐标必然乱点）；同时根治 issue#12 的 QQ/微信/抖音闪退毒链，强化无障碍持久化。
              日志上传与学习中心全部保留。
            </p>
            <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-neutral-500">
              <span className="inline-flex items-center gap-1.5"><GitBranch className="w-4 h-4" />xiaobaiba999/Skip-Ad</span>
              <span className="inline-flex items-center gap-1.5"><ListChecks className="w-4 h-4" />决策核心 112 + 规则引擎 25 用例全绿</span>
            </div>
          </div>
        </div>
      </header>

      <main className="flex-1 w-full max-w-5xl mx-auto px-4 sm:px-6 pb-16 w-full">
        {/* 数据概览 */}
        <section aria-label="版本数据概览">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
            {stats.map((s) => (
              <Card key={s.label} className="border-neutral-200 shadow-sm hover:shadow-md transition-shadow">
                <CardContent className="p-4 sm:p-5">
                  <div className="text-2xl sm:text-3xl font-bold text-emerald-600">{s.value}</div>
                  <div className="text-sm font-medium text-neutral-800 mt-1">{s.label}</div>
                  <div className="text-xs text-neutral-500 mt-0.5 leading-snug">{s.sub}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        {/* 三大修复 */}
        <section aria-label="三大核心修复" className="mt-12">
          <h2 className="text-2xl font-bold text-neutral-900 flex items-center gap-2">
            <ShieldCheck className="w-6 h-6 text-emerald-600" />
            三大核心修复
          </h2>
          <p className="text-neutral-600 mt-2 text-sm sm:text-base">来自 issue #11 / #12 真机日志的完整毒链定位与整改</p>
          <div className="grid md:grid-cols-3 gap-4 mt-6">
            {fixes.map((f) => (
              <Card key={f.title} className={`border ${f.color} shadow-sm hover:shadow-md transition-shadow h-full`}>
                <CardHeader className="pb-2">
                  <div className="flex items-center justify-between gap-2">
                    <f.icon className={`w-7 h-7 ${f.iconColor}`} />
                    <span className="text-xs font-medium text-neutral-500">{f.tag}</span>
                  </div>
                  <CardTitle className="text-lg leading-snug mt-2">{f.title}</CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="space-y-2.5">
                    {f.points.map((p, i) => (
                      <li key={i} className="flex gap-2 text-sm leading-relaxed">
                        {p.bad ? (
                          <XCircle className="w-4 h-4 mt-0.5 shrink-0 text-red-500" />
                        ) : (
                          <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0 text-emerald-600" />
                        )}
                        <span className={p.bad ? "text-neutral-500" : "text-neutral-700"}>{p.text}</span>
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        {/* 李跳跳重构对照 */}
        <section aria-label="重构前后对照" className="mt-12">
          <h2 className="text-2xl font-bold text-neutral-900 flex items-center gap-2">
            <Layers className="w-6 h-6 text-emerald-600" />
            一比一模仿李跳跳：重构对照
          </h2>
          <p className="text-neutral-600 mt-2 text-sm sm:text-base">语言同步李跳跳（Java + 无障碍服务），并在此基础上做防误点与保活优化</p>
          <Card className="mt-6 overflow-hidden shadow-sm">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-neutral-50 border-b border-neutral-200 text-left">
                    <th className="px-4 py-3 font-semibold text-neutral-700 w-28">维度</th>
                    <th className="px-4 py-3 font-semibold text-neutral-500">重构前（v3.8.1）</th>
                    <th className="px-4 py-3 font-semibold text-emerald-700">重构后（v3.9.0）</th>
                  </tr>
                </thead>
                <tbody>
                  {refactorRows.map((r) => (
                    <tr key={r.aspect} className="border-b border-neutral-100 last:border-0 hover:bg-emerald-50/40 transition-colors">
                      <td className="px-4 py-3 font-medium text-neutral-800 whitespace-nowrap">{r.aspect}</td>
                      <td className="px-4 py-3 text-neutral-500">{r.before}</td>
                      <td className="px-4 py-3 text-neutral-800">
                        <span className="inline-flex items-start gap-1.5">
                          <ArrowRight className="w-4 h-4 text-emerald-500 mt-0.5 shrink-0" />
                          {r.after}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </section>

        {/* 为什么不乱点 */}
        <section aria-label="防乱点原理" className="mt-12">
          <h2 className="text-2xl font-bold text-neutral-900 flex items-center gap-2">
            <MousePointerClick className="w-6 h-6 text-emerald-600" />
            为什么从此不乱点
          </h2>
          <div className="grid md:grid-cols-2 gap-4 mt-6">
            <Card className="border-red-200 bg-red-50/50 shadow-sm">
              <CardHeader className="pb-2">
                <CardTitle className="text-base flex items-center gap-2 text-red-700">
                  <XCircle className="w-5 h-5" /> 旧方案：固定屏幕坐标（已删除）
                </CardTitle>
              </CardHeader>
              <CardContent className="text-sm text-neutral-600 leading-relaxed">
                <p>盲点「标准跳过位」= 屏幕宽高的固定百分比（如 88% 宽、7% 高）。</p>
                <p className="mt-2">1080P 与 2K 屏、全面屏与普通屏，按钮实际位置完全不同；简单树误判时盲点必然点中无关控件——
                issue#12 里桌面被误判后盲点图标拉起微信，就是闪退元凶。</p>
              </CardContent>
            </Card>
            <Card className="border-emerald-200 bg-emerald-50/50 shadow-sm">
              <CardHeader className="pb-2">
                <CardTitle className="text-base flex items-center gap-2 text-emerald-700">
                  <CheckCircle2 className="w-5 h-5" /> 新方案：控件 bounds 中心（李跳跳同款）
                </CardTitle>
              </CardHeader>
              <CardContent className="text-sm text-neutral-600 leading-relaxed">
                <p>规则命中「跳过」控件后，读取该控件在当前屏幕的实际渲染边界，点击其中心。</p>
                <p className="mt-2">bounds 由应用按真实屏幕布局计算，任何分辨率、任何窗口缩放都天然适配——
                这正是「每个手机屏幕大小不一样，位置肯定不一样」的正解。</p>
              </CardContent>
            </Card>
          </div>
        </section>

        {/* 规则引擎流水线 */}
        <section aria-label="规则引擎流水线" className="mt-12">
          <h2 className="text-2xl font-bold text-neutral-900 flex items-center gap-2">
            <Gauge className="w-6 h-6 text-emerald-600" />
            内嵌规则引擎流水线（0ms 出手）
          </h2>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-6">
            {rulePipeline.map((s, i) => (
              <Card key={s.title} className="relative shadow-sm hover:shadow-md transition-shadow">
                <CardContent className="p-5">
                  <div className="flex items-center gap-2">
                    <s.icon className="w-5 h-5 text-emerald-600" />
                    <span className="text-xs font-mono text-neutral-400">0{i + 1}</span>
                  </div>
                  <div className="font-semibold text-neutral-900 mt-2 text-sm">{s.title}</div>
                  <div className="text-xs text-neutral-500 mt-1.5 leading-relaxed">{s.desc}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        {/* 更新与 FAQ */}
        <section aria-label="更新说明" className="mt-12">
          <h2 className="text-2xl font-bold text-neutral-900 flex items-center gap-2">
            <Download className="w-6 h-6 text-emerald-600" />
            升级与常见问题
          </h2>
          <Card className="mt-6 shadow-sm">
            <CardContent className="p-4 sm:p-6">
              <div className="flex flex-col sm:flex-row sm:items-center gap-4 p-4 rounded-lg bg-emerald-50 border border-emerald-200">
                <Download className="w-8 h-8 text-emerald-600 shrink-0" />
                <div className="min-w-0">
                  <div className="font-semibold text-neutral-900">adshield-v3.9.0.apk（104KB · versionCode 30900）</div>
                  <div className="text-sm text-neutral-600 mt-0.5">
                    已在仓库 <code className="text-xs bg-white px-1.5 py-0.5 rounded border">download/</code> 目录与 GitHub Releases；
                    同签名覆盖安装，学习数据全部保留。
                  </div>
                </div>
              </div>
              <Separator className="my-5" />
              <Accordion type="single" collapsible className="w-full">
                <AccordionItem value="q1">
                  <AccordionTrigger className="text-sm sm:text-base">为什么以前 QQ/微信/抖音一进去就被退出来？</AccordionTrigger>
                  <AccordionContent className="text-sm text-neutral-600 leading-relaxed">
                    旧版的「坐标兜底」把桌面误判成广告窗后，盲点了桌面图标（拉起微信），接着「链式跳转回弹」以为这是广告跳转，
                    连按返回键把你踢出去。v3.9.0 三刀根治：桌面事件入口直接丢弃、坐标盲点整体移除、回弹宿主校验。
                  </AccordionContent>
                </AccordionItem>
                <AccordionItem value="q2">
                  <AccordionTrigger className="text-sm sm:text-base">无障碍服务还是会偶尔断开怎么办？</AccordionTrigger>
                  <AccordionContent className="text-sm text-neutral-600 leading-relaxed">
                    Android 的系统安全设计（覆盖安装/重启/深度清理必关无障碍），任何应用都无法完全绕过。v3.9.0 已把发现延迟从
                    15 分钟缩到 5 分钟；建议完成「任务锁定 + 省电无限制 + 自启动」三步，并在有电脑时执行一次
                    <code className="mx-1 text-xs bg-neutral-100 px-1.5 py-0.5 rounded">adb shell pm grant com.zai.adshield android.permission.WRITE_SECURE_SETTINGS</code>
                    即可全自动恢复，连 MIUI 的 10 秒确认框都不再见到。
                  </AccordionContent>
                </AccordionItem>
                <AccordionItem value="q3">
                  <AccordionTrigger className="text-sm sm:text-base">部分第三方 App 还是不跳？</AccordionTrigger>
                  <AccordionContent className="text-sm text-neutral-600 leading-relaxed">
                    v3.9.0 内嵌规则覆盖所有「跳过/Skip」文案与 vid 图片键，命中即 0ms 出手。个别广告把文案烧在图片里且无控件信号——
                    这类绝不盲点（防误点红线），在 App 学习中心手动教一次，下次同窗 100ms 直点该位置。
                  </AccordionContent>
                </AccordionItem>
                <AccordionItem value="q4">
                  <AccordionTrigger className="text-sm sm:text-base">日志上传和学习习惯还在吗？</AccordionTrigger>
                  <AccordionContent className="text-sm text-neutral-600 leading-relaxed">
                    完整保留：学习中心（文本/位置/指纹学习、能耗画像）与「上传引擎日志到 GitHub Issue」通道原样保留，
                    你教过的规则与统计全部延续。
                  </AccordionContent>
                </AccordionItem>
              </Accordion>
            </CardContent>
          </Card>
        </section>
      </main>

      {/* Sticky Footer */}
      <footer className="mt-auto border-t border-neutral-200 bg-white">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-6 pb-[calc(1.5rem+env(safe-area-inset-bottom))] flex flex-col sm:flex-row items-center justify-between gap-3 text-sm text-neutral-500">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-600" />
            <span>开屏卫士 Skip-Ad · v3.9.0 李跳跳方法论重构</span>
          </div>
          <div className="flex items-center gap-4">
            <span>只代点「跳过/关闭」，不碰网络、不终止进程</span>
            <a
              href="https://github.com/xiaobaiba999/Skip-Ad"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-emerald-700 hover:text-emerald-600 hover:underline"
            >
              <GitBranch className="w-4 h-4" /> GitHub
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
