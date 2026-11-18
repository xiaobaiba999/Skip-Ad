# GKD 订阅兼容性说明（v5.4.0）

| 项 | 说明 |
| --- | --- |
| 版本 | v5.4.0 |
| 解析器 | `android/app/src/main/java/com/skipad/engine/RuleDslParser.java`（与 Web 端 `src/lib/rule-dsl.ts` 1:1） |
| 目标 | GKD 社区订阅 100% 语法兼容（快照选择器子集），无感导入统一调度 |
| 状态字段 | prisma `Subscription`（kind=gkd）+ `Rule.source="gkd"` + `Rule.gkdRef="categoryId.groupId"` |

---

## 一、语法子集支持表

### 1.1 属性选择器

| 语法 | 示例 | 说明 |
| --- | --- | --- |
| `[attr="value"]` | `[vid="btn_skip"]` | 相等 |
| `[attr!="value"]` | `[name!="ImageView"]` | 不等 |
| `[attr*="value"]` | `[text*="跳过"]` | 包含 |
| `[attr^="value"]` | `[text^="跳过"]` | 前缀 |
| `[attr$="value"]` | `[vid$="skip"]` | 后缀 |
| `[attr~="regex"]` | `[text~="跳过.*\\d"]` | 正则（find 语义） |

多条件与：`[vid="ad" && text*="跳过" && clickable=true]`。

**属性操作符共 6 种**（`=` `!=` `*=` `^=` `$=` `~=`），与 GKD 规范一致。

### 1.2 支持的节点属性（16 个）

| 属性 | 来源（无障碍节点） | 备注 |
| --- | --- | --- |
| `vid` | viewIdResourceName 的 entry 段 | `com.a:id/skip` → `skip` |
| `id` | viewIdResourceName 全名 | |
| `name` | className 简名 | `com.a.Widget` → `Widget` |
| `text` | getText | |
| `desc` | getContentDescription | |
| `clickable` / `longClickable` / `enabled` / `focusable` / `checkable` / `checked` / `editable` / `scrollable` / `visibleToUser` | 对应布尔属性 | 值写 `true`/`false`（大小写不敏感） |
| `index` | 兄弟序号 | 数字比较（`[index="0"]` 命中首个子节点） |
| `depth` | 树深度（根 0） | 数字比较 |

### 1.3 关系符（5 种）与逻辑组合

| 关系符 | 语义（右段相对左段的位置） | 示例 |
| --- | --- | --- |
| `<<n` | 右段是左段的第 n 代祖先（n ≥ 1）；`<<` 不带数字 = 任意祖先 | `[text="跳过"] <<2 [vid="ad_root"]` |
| `<` | 父节点（等价 `<<1`） | `[text="跳过"] < [vid="ad"]` |
| `>` | 子节点 | `[vid="ad"] > @[text*="跳过"]` |
| `-` | 下一个兄弟节点（文档序靠后） | `[vid="badge"] - @[vid="close"]` |
| `+` | 上一个兄弟节点（文档序靠前） | `[text="广告"] + @[text="×"]` |

逻辑组合：`&&`（与，优先级高）、`||`（或）。
示例：`[vid="a"] > [text*="跳过"] || [text="Skip"] && clickable` 解析为
`A || (B && C)`。

目标标记 `@`：规则命中后要点击的节点（如 `@[text*="跳过"]`）；**缺省目标 =
链中最后一个选择器节点**。通配段 `*`：匹配任意节点，仅约束关系
（如 `[vid="ad"] < *`）。

---

## 二、降级策略

单条规则的问题绝不影响整包订阅导入（100% 兼容承诺的底线是「坏规则降级
而不是拒收订阅」）：

| 情形 | 处理 | 用户可见 |
| --- | --- | --- |
| 未知属性（如 GKD 新增 `len` 等暂未支持属性） | 该条件恒不成立（保守降级），编译警告 | 订阅详情页「3 条规则含未支持属性，已降级」 |
| 正则语法错误（`~=` 值不合法） | 该条件恒不成立，编译警告 | 同上 |
| 关系段简化（超深 `<<n`、多段长链） | n > 8 或段数 > 6 时编译警告（仍可执行，受 MatchBudget 预算保护） | 「2 条规则关系链较长，已按预算执行」 |
| 硬语法错误（引号未闭合/缺操作符） | 该条 `ok=false` 跳过，导入继续 | 「1 条规则语法错误已跳过（含原文）」 |
| 规则与存量冲突 | 进 RuleConflict（duplicate/overlap/contradict），不直接生效 | 规则管理页冲突面板 |

编译警告持久化在 `ParsedRule.warnings`，订阅详情逐条展示，避免「静默降级
= 静默失效」。

---

## 三、导入流程

```text
1. 添加订阅：URL 或本地导入 → Subscription（kind=gkd, status=syncing）
2. 拉取订阅文本（GKD 订阅格式：categories[].groups[].rules[]）
3. 逐条规则编译（RuleDslParser.compile，警告收集，绝不中断）
4. 规则入库：source="gkd"，gkdRef="categoryId.groupId"，
   appName 取订阅组限定包名（无限定则 "*"），action 缺省映射：
   点击类规则 → instant_skip（快照选择器目标即代点目标）
5. 冲突检测 + 去重合并（RuleScheduler.deduMerge）：
   同 gkdRef + 同 pattern 跳过；与存量规则 contradict → 双转 shadow 待确认
6. 调度参与：source=gkd 基础权重 20，与 preset/learned/custom 同池竞争
7. 订阅状态回写：status=parsed，ruleCount/message 展示导入结果
```

向后兼容：订阅重新同步时按 gkdRef + pattern 去重，已入库规则 id 不变
（GKD 订阅无感迁移）；订阅禁用仅置 enabled=false，不删除规则。

---

## 四、与原生 GKD 的互认测试方法

### 4.1 金样本集双向校验（CI 门禁）

```text
样本集 golden/dsl-cases.json（双向维护）：
  { expr, tree, expected: { ast: describe 规范形态, matched, targetVid } }

门禁步骤（ci.yml android job）：
  1. TS 侧（src/lib/rule-dsl.ts）解析全部样本 → describe() 输出
  2. Java 侧（RuleDslParser）解析同批样本 → describe() 输出
  3. 两侧 AST 规范形态逐条字符串相等
  4. 同一 tree（金样本节点树）上匹配结果（matched/target）逐条相等
  5. 任一不等 → CI FAIL（双端判定一致性是发布硬门禁）
```

### 4.2 真实订阅回归

选取 GKD 官方订阅及 3 个高星社区订阅，每个版本发布前：

1. 全量导入 → 断言 ruleCount 与原生 GKD「导入成功规则数」一致
   （对照 GKD 官方 App 的订阅详情计数）；
2. 抽取 50 条规则在金样本树上双端跑分，动作序列一致；
3. 警告清单人工复核：仅允许「未支持属性」「关系段简化」两类预期降级，
   出现硬语法错误即为兼容性回归（P0 冻结发布）。

### 4.3 语义差异清单（与原生 GKD 的已知差异）

| 差异点 | 本项目行为 | GKD 原生 | 处理 |
| --- | --- | --- | --- |
| 目标缺省 | 链中最后一段 | 依据 @；无 @ 时规则仍可匹配 | 兼容（@ 语义一致；无 @ 的标注类规则不代点） |
| `<<` 缺省 | 任意祖先 | 祖先节点 | 兼容（任意祖先语义） |
| `<<n` | 精确第 n 代 | 向上 n 层 | 兼容（n ≥ 1 时语义一致） |
| 未支持属性 | 条件恒不成立 + 警告 | 原生支持 | 降级（清单见 §1.2，随版本扩充） |
| 规则级 `any`/`fastQuery` 等元字段 | 忽略（不解析为选择器） | 支持 | 导入时跳过元字段，仅取 matches 表达式 |
| 快照点击位置（position） | 以目标节点中心代点 | 支持点击位置偏移 | 兼容子集（无偏移场景占绝对多数） |

任何新增差异必须先落本清单再改代码，保证差异可审计、可回归。

---

## 五、常见问题

**Q1：为什么不用 GKD 原生引擎直接嵌入？**
无障碍代点引擎与调度/质量/功耗体系深度耦合（探针前置、五层调度、节点
缓存），嵌入外来引擎会破坏统一调度与 CI 金样本门禁；选择「语法 100%
兼容 + 判定同构」路线，社区订阅可直接消费，工程主权保留在本项目。

**Q2：订阅规则误触谁负责？**
gkd 源基础权重 20（低于 custom/learned），误触经 BlockEvent/反馈通道进入
质量模型（falseCount），生命周期自动 cooling → retired；contradict 冲突
绝不自动生效，人工确认后才转正——订阅规则享受同等质量治理，而非白名单。

**Q3：导入的订阅规则怎么关闭？**
规则管理页按源筛选（source=gkd）批量启停；禁用订阅 = 该 gkdRef 下全部
规则 enabled=false（规则保留，重新启用零等待）。
