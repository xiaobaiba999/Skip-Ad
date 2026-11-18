# Task 1-b 工作记录：v5.4.0 治理策略同源库（纯函数 5 件套）

任务：为「开屏卫士 Skip-Ad」实现 v5.4.0 Android 端治理策略的 Web 端同源库（TS 版 = 跨端规范源，Android Java 版逐行对齐）。只写生产代码，中文注释，无 emoji，无 indigo/blue。

## 交付文件（5 个纯函数库，零依赖：不 import db / React，双端同源可用）

| 文件 | 职责 | 关键导出 |
|---|---|---|
| `src/lib/sensitive-scenes.ts` | 敏感场景零扫描防护（12 类扩容） | `SENSITIVE_SCENES`（12 类）、`detectSensitiveScene`、`isZeroScanScene`、`SensitiveScene` |
| `src/lib/power-governor.ts` | 分级动态功耗策略（4 档设备 × 4 态电量） | `classifyDevice`、`resolveBattery`、`computePolicy`、`policyMatrix`（16 档）、`estimateEnergyPerBoot`、`PowerPolicy`、`DeviceTier`、`BatteryState`、`EVENTS_PER_BOOT`、`SCAN_UWH`、`PROBE_UWH`、`EVENT_BASE_UWH` |
| `src/lib/formfit.ts` | 全形态设备自适应阈值 | `adaptThresholds`、`describeForm`、`FORM_PRESETS`（12 组）、`WindowGeom`、`AdaptiveThresholds`、`FoldState` |
| `src/lib/compliance.ts` | 合规边界自检与权限透明化 | `COMPLIANCE_PRINCIPLES`（4 条）、`ACTION_BOUNDARY`（11 项）、`auditAction`、`buildPermissionAudit`（6 条）、`POSITIVE_CTA_WORDS`、`GUARD_PKG_PREFIXES`、`ComplianceCheck`、`PermissionAuditEntry` |
| `src/lib/structured-log.ts` | 结构化日志体系（schema 1.0） | `LOG_SCHEMA_VERSION`、`LEVEL_ORDER`、`LOG_MODULES`、`formatLog`、`makeLogger`、`sanitizeLogData`、`LogEntry`、`LogLevel` |

## 关键设计决策（后续 Agent 需知）

### 1. 敏感场景（12 类，检测优先级 DETECT_ORDER 窄→宽）
- 前 4 类 since v3.x：payment（v3.2.0）、password（v3.2.0）、otp（v3.4.0）、sms（v3.4.0）；其余 8 类 v5.4.0：settings / ime / keyguard / incall / transfer / realname / permission / a11y_dialog。
- 信号语法跨端统一：`pkg:`（包名，不区分大小写包含匹配）、`cls:`（窗口类名，区分大小写包含匹配）、`field:password|otp`（控件布尔特征）、`text:` / `win:`（Android 端富上下文，Web 检测不消费，同表维护保证一处改动双端同步）。
- 检测优先级：a11y_dialog > permission > transfer > realname > payment > otp > password > sms > incall > ime > keyguard > settings（窄场景先于宽场景，防 com.android.settings.accessibility 被宽场景 settings 吃掉）。已验证：无障碍 10 秒警告窗命中 a11y_dialog 而非 settings。
- 匹配语义：宁可静默、不可误触（零扫描对体验的代价只是该窗不拦广告）。

### 2. 功耗策略（16 档矩阵，能耗与 POWER_PROFILE.v530 精确对齐）
- 命中率红线（全部 16 档 + label 后缀「指纹常开」）：probeFirst 恒 true；SDK 广告窗指纹 / Dialog 指纹 / 学习指纹三命中通道永不降级。
- 关键数值：旗舰/高端充电态 = 全量 `[0,120,240,400,650,1000]` 重试链 + 500ms 间隔 + 深度 50 + 弹窗窗/学习开 → **300 扫描 + 900 探针 = 1334µWh**（与 instant-engine.ts POWER_PROFILE.v530.microWh 精确一致）；中端充电 `[0,150,300,500]`/600ms/140 扫描/1219µWh；入门充电 `[0,200,450]`/800ms/57 扫描/1159µWh；极低电全档探针静默（`[0,800]` 链、学习关、弹窗窗关、心跳 0，9 扫描/1124µWh）。
- 能耗模型：`microWh = 4604 × 0.23297(事件基础成本) + scans × 0.72 + probes × 0.05`；事件基础成本由 v5.2.0 与 v5.3.0 两组实测独立反推一致（差 <0.02%），双版本自洽。scansPerBoot 由 5 因子（interval/depth/chain/popup/learn）相对全量基准 300 缩放。
- classifyDevice first-match：flagship(soc≥85∧ram≥12∧核≥8) > high(≥70/≥8/≥8) > mid(≥50/≥6/≥6) > low。resolveBattery 优先级：charging > critical(level≤15) > low(≤35 或 saver) > normal。

### 3. 全形态阈值（三大问题修复写进 note 字段，UI 可直接展示）
- 展开态（unfolded）：角落区 28%/14% → 收缩 16%/10%（修复角落键定位失效）、面积上限 35% → 25%（更严）；平板（长宽比 <1.6）：面积上限 28%（修复形状门误判）；分屏：一切分母 = 实际窗口（referenceW/H），热区 55% → 60%（修复分屏热区判定不准）；大屏半屏插屏 30~50%、分屏 40~60%（基准 35~55%）；minTouchPx = 48dp × density 恒定。
- FORM_PRESETS 12 组：小米折叠屏折叠竖/横、展开竖/横、iPad 级平板竖/横/上下分屏、手机上下/左右分屏、直板竖/横、折叠悬停自由窗。

### 4. 合规审计
- 四原则：only_native_skip / no_content_modify / no_user_jump_intercept（issue#14 教训）/ sensitive_zero_scan。
- ACTION_BOUNDARY 11 项：8 个白名单点击动作（instant_skip/popup_close/halfsheet_close/decline_tap/preroll_skip/overlay_notice 零点击/mark_only 零点击/back_fallback 仅应用内窗）+ coord_tap（真实引擎动作，带强约束登记）+ 2 个拒绝示范（positive_cta / terminate_app）。白名单制：未登记 = 默认拒绝。
- auditAction 四规则：action.whitelist / text.negative_only（14 词积极 CTA 词库）/ target.area_gate（≤35% 面积安全阀）/ pkg.guard_list（9 个真实守护包前缀，禁 coord_tap 与 back_fallback）。
- buildPermissionAudit 6 条：窗口监听/控件查找/代点/手势兜底/学习沉淀/结构化日志，各自声明「仅内存处理、不落盘、不上传」数据边界。

### 5. 结构化日志
- 单行格式与任务示例逐字符一致：`{ISO8601带毫秒} [{LEVEL大写}][{module}] {event} {json}`；data 空时不附 JSON 段。
- makeLogger 出口即自动 sanitizeLogData（pkg/pkgName 保留；text/content/desc/title/query 截 32 字符加省略号；嵌套递归）；低于 minLevel 返回 null。LOG_MODULES 7 模块白名单。

## 验证结果
- `bunx tsc --noEmit`：本次 5 个文件 **0 错误**（项目既有错误：examples/skills 下模块缺失、src/app/page.tsx 引用的 `@/components/rule-sandbox` 组件尚未创建——属并行 UI 任务范围，非本任务交付物）。
- `bun run lint`：本次 5 个文件零告警；唯一 error 在 `src/components/intel-station.tsx:134`（set-state-in-effect，UI 任务遗留，非本任务范围）。
- 数值冒烟（bun -e 一次性验证，未留测试文件）：敏感场景 12 类检测全对；16 档矩阵 probeFirst 全恒真、旗舰/高端充电态精确回放 v530（300/900/1334）；formfit 12 预设换算正确；auditAction 正向全 pass / 积极词 FAIL / 守护名单拒绝 coord_tap；日志格式化与脱敏符合契约。

## 给后续 Agent 的对接提示
- 情报站看板 `src/components/intel-station.tsx` 已引用 `policyMatrix` / `estimateEnergyPerBoot` / `DeviceTier` / `PowerPolicy`，tsc 已通过（契约兼容确认）。
- 看板若需「敏感场景面板」「合规边界面板」「日志示例」可直接 import 本 5 库，全部为纯函数（客户端组件可直接调用，无需 API 路由）。
- Android 端对齐：PowerGovernor.java / SensitiveScenes.java / FormFit.java / ComplianceGuard.java / StructuredLogger.java（package com.xskip.engine.policy / .log），以本 5 文件数值为基准断言。
