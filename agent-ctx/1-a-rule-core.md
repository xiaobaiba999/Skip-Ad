# 任务 1-a · v5.4.0 多源规则统一调度核心（rule-core）

- 任务 ID: 1-a
- Agent: rule-core（后端核心库与 API）
- 日期: 2026-11-18
- 项目: /home/z/my-project（Next.js 16 App Router + TS5 + Tailwind4 + Prisma/SQLite）

## 一、交付文件清单

| 文件 | 动作 | 说明 |
| --- | --- | --- |
| src/lib/rule-dsl.ts | 新建 | 统一规则 DSL（GKD 100% 兼容）：SimNode 树、DslMatcher/DslRule、parseGkdSubscription、parseSelector、compileDslLine/serializeDslRule、matchRuleOnTree/matchTree、makeNode。纯函数库（不 import db，客户端可用） |
| src/lib/rule-scheduler.ts | 新建 | 多源统一调度器：SOURCE_BASE_WEIGHT（custom40>learned30>gkd20>preset10）、computeScore、schedule、detectConflicts、dedupeMerge。纯函数库 |
| src/lib/rule-quality.ts | 新建 | 学习规则质量模型：scoreRule（Wilson 下界+稳定性+时效+误触）、isNoiseSample、lifecycleTransition、generalizeRule、SDK_FAMILIES（csj/gdt/ks/admob/host）。纯函数库 |
| src/app/api/rules/route.ts | 升级 | GET 增 priority/quality/status/sdkFamily/generalizedFrom/gkdRef/missCount/falseCount/version/appNameScope + stats{bySource,byStatus,avgQuality}；PATCH 兼容旧 {id,enabled} 并扩展 {id,priority} 与 {op:"batch"}；POST 新建（默认 custom 源）；PUT 编辑（version 自增，无 name 列）；DELETE（preset 拒 403） |
| src/app/api/rules/conflicts/route.ts | 新建 | GET：启用规则 → detectConflicts → high 且新增者按 ruleAId+ruleBId+kind 判重写入 db.ruleConflict → {conflicts, persisted} |
| src/app/api/subscriptions/route.ts | 新建 | GET：订阅列表 + 空表播种 2 条演示订阅（upsert 幂等）；POST：content → parseGkdSubscription → 批量入库（source=gkd、gkdRef=订阅名#组名、confidence0.9、pattern=DSL 行格式）；DELETE：{id} 级联删除该订阅名#前缀的 gkd 规则（读出 JS 过滤规避 SQLite startsWith 限制） |
| src/lib/learning.ts | 升级（只增不改） | BlockEventInput 增 sdkFamily?；recordBlock/recordMiss 写 blockEvent.sdkFamily；命中时 quality/status 联动落库（missCount 不增）；首次沉淀前 isNoiseSample 过滤（拒绝返回 rejectedReason）；含 SDK 家族的应用 generalizeRule 生成 sdk:xxx 泛化规则（返回 generalized）；recordMiss 对已存在学习规则 missCount+1 并重算质量；新增导出 redactSnapshot（recentEvents 应用名→codePoint 求和 hex 别名，统计字段保留）；buildSnapshot meta.version→5.4.0 |

## 二、导出签名确认（逐字对齐任务书）

rule-dsl.ts: SimNode/DslField/DslOp/DslMatcher/DslRule/MatchStep/MatchTrace/TreeMatchResult/GkdParseResult +
parseGkdSubscription(text, sourceName?)/parseSelector(sel)/compileDslLine(line)/serializeDslRule(rule)/
matchRuleOnTree(root, rule)/matchTree(root, rules)/makeNode(partial)。附加导出 GkdRuleMeta（gkdRef/rawSelector，
parseGkdSubscription 产物附带，订阅路由读取，不破坏 DslRule 公共契约）。

rule-scheduler.ts: RuleSourceKind/SchedulerRule/ConflictItem/SOURCE_BASE_WEIGHT/computeScore(r)/
schedule(rules, ctx)/detectConflicts(rules)/dedupeMerge(rules)。

rule-quality.ts: RuleStats{hitCount,missCount,falseCount,lastHitAt,createdDaysAgo}/RuleGrade/RuleLifecycle/
QualityReport{score,grade,noise,lifecycle,wilson,reasons,advice}/scoreRule/isNoiseSample/lifecycleTransition/
generalizeRule/SDK_FAMILIES。

learning.ts 既有导出全部保持：BlockEventInput（加可选字段）、todayKey、recordBlock、recordMiss、
LearningProfile、getProfile、buildSnapshot；新增 redactSnapshot。返回值为增量字段（rejectedReason/generalized），
既有消费方（/api/block-events、/api/learning、sync.ts）不受影响。

## 三、调度/质量公式实现要点

- computeScore：源基准 + 专属(appName!=="*")+25 + quality/4(≤25) + min(hit,100)/10(≤10) − 返回键类
  （back_fallback/gyro_block/chain_block/sdk_window_cut）−15；cooling ×0.6；reason 中文分项说明。
- schedule：enabled && status!=="retired" && (精确 appName（忽略大小写/空白）或 "*")；ctx.adType 给定时
  保留同型+any 型（全空回退避免空转）；priority>0 视为手动锁定分直接采用；平分兜底：专属先于全局→源权重→
  hitCount→id 稳定序。
- detectConflicts：duplicate=同 appName+adType+action+pattern 相似（归一化相等或包含，短边≥6）→high；
  overlap=同 appName+adType 不同 action（特征相似 medium，否则 low）；contradict=guard_skip vs 激进类→high、
  decline_tap vs 积极点击类(instant_skip/preempt_block/coord_tap)→medium。规则对按 id 字典序规范化 A/B，可判重。
- scoreRule：Wilson(z=1.96) 下界×60 + min(24, log2(1+hit)×6) + 时效(≥30天−15 / ≥60天−30，lastHitAt 空按
  createdDaysAgo) − false×12，clamp 0~100；grade A≥80/B≥60/C≥40/D<40；噪声=误触占比≥1/2；样本 n<3→shadow。
- lifecycleTransition：shadow 保持 → D 或 ≥30 天→retired（7 天后可清理，策略在调用方）→ C→cooling → A/B→active。
- generalizeRule：无家族或 host 返回 null；否则 appName="sdk:{key}"，pattern 冠【家族泛化】标注，scopeLabel
  含家族包名。

## 四、GKD 兼容策略（完整支持 vs 降级 warning）

完整支持：
- 订阅整体结构：let/var/const categories 数组、Category{id,name,groups}、Group{id,name,key,rules}、
  Rule{matches:string|string[],action?,activityNames?}；宽松 JSON 路径（去注释/单引号反引号转双引号/
  无引号键补引号/去尾逗号后 JSON.parse）。
- 属性选择器全部操作符：= != *= ^= $=~（正则）；属性 text/vid/desc/name（旧语法 id≡vid）；组内 && 与；
  多 bracket 组 AND；裸标识（如 ImageView）按 name 等价；matches 数组多选择器 AND 合并。
- GKD action：click/clickNode/clickCenter/longClick→instant_skip，back→back_fallback。
- adType 按分类/分组名猜测（开屏/半屏/插屏/更新/悬浮/信息流/前贴/摇一摇/弹窗→any）。

降级 warning（绝不中断导入）：
- 关系符段（<<n/</>/>>/>>n/-/+）：只取首个（优先 @ 点击目标）属性段，其余段记
  「简化编译：关系段仅在真机端生效」。
- @点击目标标记 → warning「仅在真机端生效（Web 端按属性匹配）」。
- activityNames 快照限定 / matchTime / actionMaximum / anyMatches 多选一（取首项）→ 各记 warning。
- 整体结构解析失败 → 降级逐条正则提取 matches（规则名自动生成）；单条选择器解析失败 → warning 跳过该条。
- 仅当文本为空或完全无法识别时记 error（ok=false）。

行 DSL：`name | app | adType | action | sel1 ; sel2`（| 与 ; 均括号/引号感知，正则值内 | 不误切）；
守卫扩展段 `#guard:<matcherIndex>:areaMaxPct=35,corner=tr` 可与 serializeDslRule 往返（已验证 recompile
matchers 全等）。

## 五、tsc / lint / 运行时自检结果

- bunx tsc --noEmit：全仓 4 个错误，全部位于 examples/（socket.io 模块声明缺失 ×2）与 skills/
  （image-edit、stock-analysis 存量类型问题）——均为存量错误，与本任务文件无关；本任务 7 个文件 0 错误。
- bunx eslint（7 个新增/修改文件）：0 问题。
- 运行时冒烟（bun 脚本 + curl 端到端）：
  - parseGkdSubscription：3 规则/2 组/gkdRef=订阅名#组名/5 条 warning 全部符合预期；
  - parseSelector 三个任务书示例全部正确；compile/serialize 往返全等；
  - matchTree 调度排序 + winner + guard 面积/角落校验 trace 正确；
  - /api/rules：46 条规则含全部新字段，stats{bySource:{preset40,gkd3,learned6+,custom0},byStatus,avgQuality}；
  - /api/rules/conflicts：80 条（72 low overlap + 8 high contradict），首轮 persisted 8、复调用 persisted 0（判重生效）；
  - /api/subscriptions：空表播种 2 条演示订阅；POST 导入 GKD 内容 3 条规则入库（matchType=gkd-selector、
    pattern 为可复原 DSL 行）；临时订阅 DELETE 级联清掉 2 条（含并发导入）gkd 规则；
  - /api/block-events：优品严选首次拦截沉淀学习规则（status=shadow, quality=18.4, sdkFamily=gdt）并泛化出
    sdk:gdt 全局规则（generalizedFrom=优品严选）；极选商城 miss 后 missCount=1、quality/status 联动更新；
    显式 sdkFamily=csj 透传入库；
  - buildSnapshot：meta.version=5.4.0，recentEvents 已脱敏（app_a49f 等），learnedRules 保留场景名。

## 六、环境运维备注（后续 Agent 必读）

1. Prisma 客户端于 03:57 重新生成（init 脚本），但 dev server（03:23 启动的旧进程）内存中仍是旧客户端，
   导致 API 返回缺新字段。已按系统脚本 .zscripts/dev.sh 重启 dev server（kill 旧进程 6862/6863/6876 →
   nohup bash .zscripts/dev.sh），端口 3000 单实例，全接口 200。若后续再次改动 prisma/schema.prisma，
   务必 bun run db:push + 重启 dev server 再验证。
2. DB（db/custom.db）已含全部 v5.4.0 列与 Subscription/RuleConflict 表（python PRAGMA 验证）。
3. 演示数据现状：gkd 源规则 3 条（「GKD 社区精选」导入，gkdRef 前缀 GKD 社区精选#）；
   学习规则含 优品严选 + sdk:gdt 泛化样例；db.ruleConflict 已留痕 8 条 high 冲突。
4. 已知边界（按任务书实现，非 bug）：PATCH/POST/PUT 对非法 JSON body 返回 500（与仓库既有路由风格一致）；
   PUT 不支持 name（Rule 表无该列，POST 的 name 仅回显）；quality 公式对小样本严格（如 3 命中 1 漏拦
   → D 级 retired），是 Wilson 置信下界的既定行为。
5. dev.log 中一条 [subscriptions POST] SyntaxError 来自冒烟时构造的截断请求体，路由按 500 兜底，非缺陷。

## 七、给后续 Agent 的接口速查

- GET /api/rules → { rules:[{...旧字段, priority, quality, status, sdkFamily, generalizedFrom, gkdRef,
  missCount, falseCount, version, appNameScope}], stats:{ bySource, byStatus, avgQuality } }
- PATCH /api/rules：{id, enabled?} | {id, priority?} | {op:"batch", ids, enabled}
- POST /api/rules：{name?, appName, appCategory?, adType, matchType?, pattern, action, source?="custom"}
- PUT /api/rules：{id, pattern?|action?|adType?|appName?}（version+1）
- DELETE /api/rules：{id}（preset→403）
- GET /api/rules/conflicts → { conflicts: ConflictItem[], persisted }
- GET /api/subscriptions → { subscriptions: [...] }（空表自动播种 2 条演示订阅）
- POST /api/subscriptions：{name?, url?, content?} → { ok, imported, warnings, errors, subscription }
- DELETE /api/subscriptions：{id} → { ok, removedRules }
- 客户端可直接 import '@/lib/rule-dsl'（纯函数）在演练场做 DSL 可视化/规则编辑器。
