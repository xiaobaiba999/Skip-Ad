# Task 2-e 工作记录：v5.4.0 情报站可视化看板 + 规则可视化管理 + 六 Tab 主页集成

任务：为「开屏卫士 Skip-Ad」（/home/z/my-project）开发 v5.4.0「情报站可视化看板」「规则可视化管理」并集成到主页面六 Tab。只写生产代码，中文 UI，无 emoji，无 indigo/blue（图表色板 emerald/violet/amber/rose/teal/fuchsia/cyan/zinc）。

## 交付文件清单

| 文件 | 类型 | 职责 |
|---|---|---|
| `src/app/api/dashboard/route.ts` | 新增 API | 看板聚合：totals(+events) / 14 日趋势 / perApp / 24 小时桶 / 广告类型构成 / 规则命中 Top8 / 功耗对照(POWER_PROFILE v520/v530) / SDK 家族分布 |
| `src/components/intel-station.tsx` | 新增组件 | 情报站看板：KPI×6 + 图一~图七（趋势/排行/时段/类型 donut/规则命中/功耗对照/16 档功耗策略矩阵） |
| `src/components/rule-manager.tsx` | 新增组件 | 规则管理：统计条 / 订阅源导入与删除 / 规则表格（启停/优先级/编辑/删除/批量）/ 冲突检测 |
| `src/app/page.tsx` | 修改 | 三 Tab → 六 Tab（arena/intel/rules/sandbox/learning/about），副标题更新，footer 加 mt-auto，导航按钮加 aria-label |

## API 对接点（按契约消费，相对路径 fetch）

- `/api/dashboard`（本任务新建，GET）：全量读 `db.blockEvent` + `getProfile()`，JS 聚合。
- `/api/rules`（并行任务升级中）：GET `{ rules, stats }`（intel 站取 rules.length 为规则总数；rule-manager 优先用 stats.avgQuality，缺失时本地回退计算）；PATCH 单条 `{ id, enabled | priority }`；PATCH 批量 `{ op: "batch", ids, enabled }`；PUT `{ id, pattern, action, adType }`；POST `{ appName, adType, matchType, pattern, action }`；DELETE body `{ id }`。旧版返回缺 missCount/falseCount/priority/quality/status 字段时 UI 以 `?? 0` 兜底不崩。
- `/api/rules/conflicts`（并行任务）：GET → `{ conflicts, persisted }`。
- `/api/subscriptions`（并行任务）：GET / POST `{ name?, content }`（GKD 订阅文本导入）/ DELETE `{ id }`；加载失败静默（服务未就绪不阻断规则表格）。
- `@/lib/power-governor`（Task 1-b 已落地）：`policyMatrix()`、`estimateEnergyPerBoot()`、`DeviceTier`/`PowerPolicy` 客户端直调（纯函数）。已按 1-b 工作记录核对：keepAlivePing 单位为秒（渲染为 `Ns`/关）、probeFirst 恒 true、旗舰充电态 300 扫描/900 探针/1334µWh。

## 关键实现决策

1. **预拦截判定**（dashboard 日趋势 preempts）：`action === "preempt_block" || note.startsWith("P0 预拦截")` —— 守护类预拦截 action 是 guard_skip，只能靠 note 前缀识别（与 arena.tsx 埋点对齐）。
2. **东八区日期桶**：复制 learning.ts 的 `shanghaiDateKey` 私有逻辑（+8h 再取 ISO 日期），14 桶倒序填充，date 输出 MM-DD。
3. **adType 标签窄化**：`AD_TYPE_LABEL as Record<string,string>` 安全查找，查不到回退原字符串（"any" 等非 AdType 值不炸）。
4. **ruleHit**：note 非空事件按 note 全文聚合 top8（ruleName=note，avgMs=平均 latency），图表 Y 轴客户端截断 12 字符。
5. **SDK 家族**：null 归 `family:"none"/label:"未标记"`；csj 穿山甲/gdt 优量汇/ks 快手/admob/host 宿主自建本地映射；以 chips 形式放在「广告类型构成」卡尾部（看板七图中无独立 SDK 图，数据不浪费）。
6. **图表选型**：趋势用 ComposedChart（Area 拦截 + Line 漏拦截/预拦截混合）；排行/规则命中用纵向 BarChart（layout="vertical"）；时段 BarChart 峰值 Cell 高亮 amber（其余 violet/空档 zinc）；类型 donut + 右侧自定义图例列表。ResponsiveContainer 统一 h-52~h-60。
7. **功耗对照**：四指标卡（v5.2.0 划线 zinc → v5.4.0 三零治理 emerald + 降幅 badge），降幅由 POWER_PROFILE 数值现算（扫描 -87% / 能耗 -52% / 误开窗预算制 / 命中率无 badge）。
8. **策略矩阵**：16 档全量 `useMemo(() => policyMatrix(), [])`，设备档位 Segmented（旗舰/高端/中端/入门，role=radiogroup）过滤出 4 行（每档 × 四态电量），能耗列展示 `microWh + scans/probes` 副文本。
9. **规则表格**：原生 table + shadcn（Switch/Checkbox/Select/Dialog/AlertDialog/Collapsible）；`max-h-[560px] overflow-auto` + 自定义滚动条（`[&::-webkit-scrollbar]` 任意变体 + `scrollbar-width:thin`）；pattern line-clamp-2 点击展开；preset 源禁编辑禁删除（按钮 disabled + title 说明）；质量分 0 显示「—」、>=80 emerald/60-79 teal/40-59 amber/<40 rose；来源徽章 preset 锌/gkd 青/learned 粉/custom 紫。
10. **Toast**：layout 已挂 shadcn `<Toaster />`，统一用 `useToast()`（非 sonner），错误带 variant="destructive"。
11. **lint 规避**：初始加载用 effect + .then 回调 setState（不在 effect 体内同步 setState），手动刷新走独立 `load()`（按钮事件里可同步 setState）。

## 验证结果

- `bunx tsc --noEmit` 过滤 `intel-station|rule-manager|dashboard|page`：仅剩 `src/app/page.tsx(7,29): TS2307 '@/components/rule-sandbox'` —— **属预期**（并行任务未落地，任务说明明确可忽略）。其余 0 错误（examples/skills 既有错误非本项目范围）。
- `bun run lint`：0 error 0 warning（此前 set-state-in-effect 已修复）。
- `bun -e` 直调 `GET /api/dashboard` 冒烟（不留测试文件）：STATUS 200；totals.events=11；当日桶 blocks=10/preempts=4（预拦截判定正确）；adTypes 标签正确；ruleHit 首位「P0 预拦截（学习指纹）」×4；sdkFamily 未标记 11；power.v530.microWh=1334；hours 24 桶。
- dev server 当前因缺 `@/components/rule-sandbox` 全站 500（含既有 /api/learning 等，非本次改动引入）——并行任务落地后 HMR 自愈。

## 给后续 Agent 的对接提示

- `src/app/page.tsx` 已按六 Tab 集成，`{tab === "sandbox" && <RuleSandbox />}` 期望 `src/components/rule-sandbox.tsx` 导出 `export function RuleSandbox()`（无 props）。落地后 tsc/dev server 自动恢复。
- intel 站 KPI「规则总数」直接消费 `/api/rules` 的 `rules.length`，无需额外字段。
- rule-manager 的订阅源/冲突区块在 `/api/subscriptions`、`/api/rules/conflicts` 未就绪时静默/手动触发，不会阻断页面。
- 若给 BlockEvent 埋点补 `sdkFamily`（csj/gdt/ks/admob/host），看板 SDK chips 自动开始分组，无需前端改动。
