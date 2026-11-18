# Skip-Ad v5.4.0 全维度优化方案

| 项 | 说明 |
| --- | --- |
| 版本 | v5.4.0（VERSION_CODE 50400） |
| 仓库 | xiaobaiba999/Skip-Ad |
| 范围 | Android 端（com.skipad）核心引擎框架 + Web 端（Next.js 演示管理端）配套能力 |
| 关联文档 | [TEST-PLAN.md](./TEST-PLAN.md)（测试体系） · [GKD-COMPAT.md](./GKD-COMPAT.md)（订阅兼容） |
| 代码索引 | `android/app/src/main/java/com/skipad/`（12 个框架类） · `.github/workflows/`（CI/CD） |
| 基线 | v5.3.0 李跳跳式三零治理（稳态扫描降幅 94%、能耗降幅 58%、命中率 100%） |

v5.4.0 的主线是「多源规则统一调度 + 统一 DSL + 质量治理」：把 v5.3.0 及以前
积累的单点能力（探针前置、CLEAN 档案、P0 预拦截、SDK 窗指纹）升级为一套
可治理、可审计、可跨端复制的规则基础设施，同时补齐五类新广告场景、敏感
场景零扫描扩容、分级动态功耗与全形态自适应。

---

## 一、核心规则引擎深度优化

### 1.1 多源规则统一调度

#### 【设计思路】

v5.3.0 之前规则只有两类来源（预置 preset + 学习 learned），互不感知。v5.4.0
引入 GKD 社区订阅（gkd）与用户自定义（custom）后，四个源在同一窗口内竞争
出手权，若没有统一调度会出现三类事故：

1. 同一跳过键被两条规则先后点击（重复出手，第二条点到已恢复的主页内容）；
2. 返回键类规则抢在代点类规则之前触发（v3.6.0 毒指纹事故的调度层复现）；
3. 一条坏订阅规则（误触率 40%）与官方规则同权重竞争，坏规则经常赢。

因此调度的第一原则是：**任何窗口、任何目标，同一时刻至多一条规则出手，
出手顺序由可解释的分数决定，分数由「来源可信度 × 专属度 × 质量分 × 命中
历史」四因子合成，冲突显式落库而不是隐式覆盖**。

这是对李跳跳类工具最大的缺陷规避——李跳跳社区规则库没有冲突管控，规则
越多互相踩踏越严重，用户只能靠手动禁用规则自愈；本方案把冲突检测、去重
合并、覆盖裁决全部前置到调度层。

#### 【核心逻辑】

**（1）优先级权重体系**（`RuleScheduler.SOURCE_BASE_WEIGHT`，与 Web 端
`src/lib/rule-scheduler.ts` 常量表 1:1）：

| 源 | 基础权重 | 设计依据 |
| --- | --- | --- |
| custom（用户自定义） | 40 | 用户意图最高优先；本地可见可改 |
| learned（本地学习） | 30 | 机器学习但有质量模型四道闸把关 |
| gkd（社区订阅） | 20 | 社区规模大但质量参差，经冲突检测降权参与 |
| preset（官方内置） | 10 | 兜底覆盖，绝不与前三者抢跑 |

调度分公式（`computeScore`）：

```
score = SOURCE_BASE_WEIGHT[source]
      + (appName != "*" ? 25 : 0)          // 应用专属 +25
      + quality / 4                         // 质量分 0~100 → 0~25
      + min(20, hitCount / 10)              // 命中历史，上限 20（防海量命中挤占）
      + (返回键类 ? -15 : 0)                // back_fallback / matchType=backkey
      , 结果 clamp(0, 100)
priority > 0 时人工优先级直通（仍受 clamp 与覆盖逻辑约束，审计留痕）
```

返回键类规则天然 -15：返回键是「最后手段」，任何窗口内它都必须排在代点类
规则之后——这是把 v3.6.0「毒指纹主页按返回 ×14」事故从引擎层修复升级为
调度层制度。

**（2）冲突检测算法**（`detectConflicts`，两两比对 enabled 规则）：

| 冲突类型 | 判定条件 | 严重度 | 处置建议 |
| --- | --- | --- | --- |
| duplicate | `appName + matchType + normalize(pattern) + action` 签名完全一致 | 同源 high / 跨源 low | 合并（保高分，计数并回） |
| overlap | 目标范围重叠（同 adType、同 action、pattern 特征词有交集） | medium | 低分者阴影运行 |
| contradict | 同目标范围 + 动作语义对立（代点类 vs 仅标注类；返回键类 vs 代点类） | high | 双转 shadow + 人工确认 |

伪代码：

```text
for (a, b) in enabledRules 两两组合:
    if signature(a) == signature(b):            emit duplicate(a, b)
    elif sameTargetScope(a, b) && 动作对立(a, b):  emit contradict(a, b)
    elif sameTargetScope(a, b) && a.action == b.action
         && patternTokens(a) ∩ patternTokens(b) ≠ ∅: emit overlap(a, b)
```

特征词提取：DSL 引号字面量（`vid="btn"` → `btn`）+ 非 DSL 文本规则整段
归一化。目标范围：`appName` 一致或其中一方为全局 `*`，且 adType 相同。

**（3）去重合并逻辑**（`dedupeMerge`）：

- duplicate → 保留 `computeScore` 高者；同分按源权重 custom > learned >
  gkd > preset，再比 `version`。败者 `enabled=false, status=retired`（审计
  留痕不删除），hitCount/missCount/falseCount 并回胜者（质量评分连续）。
- overlap → 低分者转 shadow（阴影运行：参与匹配、只记日志、绝不出手，
  供情报站看板对照「如果它出手会不会更糟」）。
- contradict → 双方转 shadow + `resolution=pending`。**绝不自动二选一**——
  动作对立说明两条规则对该场景的理解互相矛盾，机器裁决的错案不可解释，
  交人工确认（Web 端规则管理页提供一键裁决）。

**（4）生效顺序与覆盖逻辑**（`schedule(pkg, adTypeHint, sdkFamily)`）：

```text
1. 过滤:  enabled && status == active
2. 范围:  appName == pkg | appName == "*" | appName == "sdk:家族" 且当前窗
          SDK 家族指纹命中（泛化规则的第三种作用域）
3. 排序:  computeScore desc → sourceWeight desc → version desc（稳定排序）
4. 覆盖:  同目标键（appName|adType|matchType）只保留最高分，
          落败者标记 shadow——同窗同目标唯一出手人
```

**（5）GKD 100% 兼容策略**：GKD 订阅导入后 `source=gkd`、`gkdRef=categoryId.groupId`
双字段回溯来源；语法经统一 DSL 解析器（§1.4 与 [GKD-COMPAT.md](./GKD-COMPAT.md)）
编译，编译警告（未知属性/正则错误/关系段简化）逐条降级而不中断整包导入；
同 gkdRef + 同 pattern 的重复订阅自动跳过。GKD 规则与原生规则在同一调度器
内竞争，不设独立通道（独立通道 = 冲突不可见）。

#### 【实现路径】

| 层 | 实现 |
| --- | --- |
| Java | `engine/RuleScheduler.java`（computeScore / detectConflicts / dedupeMerge / schedule，@Singleton，纯函数核心可 JVM 直测） |
| TS | `src/lib/rule-scheduler.ts`（规范源，方法映射见类头注释映射表） |
| 数据 | prisma `Rule`（source/priority/quality/status/sdkFamily/gkdRef/version）+ `RuleConflict`（kind/severity/resolution） |
| 门禁 | CI 金样本集：同批规则在两端计算出的 score/冲突/合并结果必须逐条一致 |

### 1.2 学习规则质量治理

#### 【设计思路】

李跳跳的学习规则无质量治理：聊天界面「教一次」会把聊天按钮位置也学进去，
次启乱点；社区反馈的毒规则直接进库。v5.4.0 的立场是**学习规则默认不可信，
必须用统计证据赎身**：Wilson 置信下界保证「样本少时分数必然低」，噪声过滤
保证「坏样本根本进不了库」，生命周期保证「上线后发现是坏规则会被自动
降级淘汰」，家族泛化保证「好规则的收益能扩散到同 SDK 全家族」。

#### 【核心逻辑】

**（1）Wilson 置信下界评分模型**（`RuleQualityModel.wilsonLowerBound`）：

误触的代价远高于漏拦（漏拦用户手点一下，误触用户卸载应用），失败权重：
`fail = 2 × falseCount + 0.5 × missCount`。质量分：

```
quality = 100 × WilsonLB₉₅( hit + 1, fail + 1 )      // Beta(1,1) Laplace 平滑

WilsonLB₉₅(hit, fail):
    n      = hit + fail
    p      = hit / n
    z      = 1.96
    denom  = 1 + z²/n
    centre = p + z²/(2n)
    margin = z · √( p(1-p)/n + z²/(4n²) )
    LB     = (centre - margin) / denom
```

特性：`hit=10, fail=0` 时 LB ≈ 0.74（B 级）；`hit=3, fail=0` 时 LB ≈ 0.47
（C 级）——新规则天然压分，必须靠持续命中赎身；一条 `hit=50, fail=1` 的
规则 LB ≈ 0.91（A 级）。分级：A ≥ 85 / B ≥ 70 / C ≥ 50 / D < 50。

**（2）噪声样本过滤四规则**（`isNoiseSample`，任一命中即拒学）：

| # | 规则 | 判定 | 拒学原因码 |
| --- | --- | --- | --- |
| 1 | 同秒多点位 | `sameSecondHits ≥ 2` | `same_second_multi_hit`（聊天流式内容特征） |
| 2 | 树体缩水不足 | 点击后 1.5s 内既无窗类切换也无节点数缩水 ≥40% | `tree_shrink_insufficient`（窗没真关） |
| 3 | 窗类不稳 | 候选点首次发现的窗口类 ≠ 点击时窗口类 | `window_class_unstable`（指纹时机不可靠） |
| 4 | 超窗命中 | 目标坐标不在窗口 bounds 内 | `hit_out_of_window`（坐标系错乱） |

规则 2 是 v5.3.0「位置学习事后验证」的形式化：真关窗的证据是窗类切换或
树体缩水 ≥40%，二者必有其一，否则点的是内容按钮而非关闭键。

**（3）生命周期状态机**（`lifecycleTransition`，状态写入 `Rule.status`）：

```text
active  ──误触率≥15% / 质量<C / 连续误触≥2 / 14d 无命中──▶ cooling
cooling ──连续 10 次命中零误触（恢复观察期）───────────────▶ active
cooling ──误触率≥40% / 质量<D / 30d 无命中────────────────▶ retired
retired ──90d 无命中──────────────────────────────────────▶ shadow（归档留审计）
任意    ──冲突处置 resolution=shadowed─────────────────────▶ shadow（阴影运行）
```

恢复只走 cooling → active 一条路：先降级观察再恢复，防止误触规则在
active/cooling 之间反复横跳。shadow 规则参与匹配与日志、绝不点击。

**（4）SDK 家族泛化**（`generalizeToSdkFamily`）：

穿山甲/优量汇/快手/AdMob 的广告容器结构在同 SDK 家族内高度一致（同一 SDK
版本的跳过控件 viewId、容器签名跨应用复用）。门槛：`hitCount ≥ 5` 且
`quality ≥ 70(B)` 且源规则为应用专属。产出家族规则：`appName="sdk:csj"`、
`sdkFamily=csj`、`generalizedFrom=源应用`、`confidence × 0.9`（泛化降一档
置信）、**初始 status=shadow**——先阴影观察，3 次命中零误触后由生命周期
状态机转正；泛化错误只污染日志，不触碰用户。

#### 【实现路径】

Java：`quality/RuleQualityModel.java`（四件套纯函数实现，JVM 可测 54 项）。
TS：`src/lib/rule-quality.ts`（规范源）。入库通道：引擎命中回写 →
`LearningSample` 采集 → `isNoiseSample` 把关 → `qualityScore` 定级 →
`lifecycleTransition` 流转；Web 端学习中心展示分级与状态流转轨迹。

### 1.3 广告场景覆盖：五类新场景

v5.3.0 已覆盖开屏/插屏/弹窗/摇一摇/Deeplink/SDK 零文本窗六类。v5.4.0 按
主流 SDK 源码特征补齐五类高频新场景（`instant-engine.ts` 的
`half_interstitial / update_dialog / float_ball / feed_native / video_preroll`
五个分支与 Java 端逐一同构）。

#### 1.3.1 半屏插屏（half_interstitial）

- 【设计思路】半屏插屏是「Dialog 底部锚定 + 高度 35%~55%」的形态，误判
  成本极高（平板/折叠屏的正常半屏面板、分享面板全是这个形态），必须
  形态门 + 双信号双重确认才允许出手。
- 【核心逻辑】SDK 源码特征依据：
  - 优量汇 `HalfScreenExpressAd`：Dialog 窗、`bottomAnchor` 布局、容器
    `com.qq.e.ads` 包名家族、「广告」角标（`GDTNativeExprView` 同源）；
  - 穿山甲 `TTHalfScreenAd`：`openadsdk` 半屏容器、`TTRoundProgress`
    关闭控件、同样底部锚定。
  判定链：Dialog 窗 → 高度占比 0.35~0.55（**按实际窗口高度**，分屏/折叠
  换算，见 §2.2）→ 底边贴窗口下沿（±2%）→「广告」角标或 SDK 容器指纹
  → 关闭键（×/关闭/我知道了）→ 代点关闭 → 2.5s 冷却（防连环半屏）。
  动作：`halfsheet_close`（关闭键必为原生控件，代点合规）。
- 【实现路径】Java `MatchingEngine.evaluateRule` 的 `half-screen` 分支 +
  `FormFitThresholds.isHalfScreenAnchored`；命中后走 `ComplianceSentinel`
  审计（目标必须命中关闭键词库）。

#### 1.3.2 更新诱导弹窗（update_dialog）

- 【设计思路】「发现新版本」类 Dialog 属体验治理而非广告拦截，动作风险
  与广告相反：**点错按钮会触发下载安装**。因此按钮语义分级是唯一判据。
- 【核心逻辑】识别：Dialog 窗 + 标题/正文命中「新版本/版本更新/发现新版
  本」+ 主按钮为下载类 CTA。按钮语义分级：
  - 积极按钮（永不触碰）：`立即更新|立即抢购|立即下载|立即开通|马上升级|
    一键安装|立即支付|确认支付|立即领取|立即购买|立即体验|开通会员|马上抢`
  - 消极按钮（唯一允许点击目标）：`以后再说|暂不更新|暂不升级|取消|关闭|
    残忍拒绝|下次再说|暂不`
  无消极按钮时仅提示不动作（宁漏勿误）。每次代点写边界自检留痕（目标语义
  = 消极、面积 <1%、非输入类，结构化日志落盘）。
- 【实现路径】Java `evaluateRule` 的 `update-guard` 分支 +
  `ComplianceSentinel.POSITIVE_CTA` / `NEGATIVE_BUTTONS` 词库与
  `auditAction` 二次把关（引擎层判别 + 审计层拒绝双保险）。

#### 1.3.3 悬浮广告球（float_ball）

- 【设计思路】`TYPE_APPLICATION_OVERLAY` 小窗的球体本身就是广告入口，
  任何点击/滑动都可能触发跳转——**零点击是唯一正确动作**。
- 【核心逻辑】识别：overlay 小尺寸窗（直径 ≤12% 窗宽，`isFloatBallSized`）
  + 窗口白名单校验（系统桌面组件/音乐控件/输入法悬浮条豁免）+ 前台应用
  关联性。处置：只识别（`overlay_notice`）+ 通知栏提示 + 一键直达「悬浮窗
  权限」设置页（收回该应用 overlay 授权，根治而非对抗）；识别留痕夜间
  同步仅含脱敏统计。
- 【实现路径】Java `MatchingEngine` SDK 窗分支前置 overlay 白名单；
  `KeepAliveDoctor.settingsIntent` 泛化为权限直达页工厂。

#### 1.3.4 信息流嵌入（feed_native）

- 【设计思路】用户正在浏览的内容流**绝不自动滑走/点击**——合规底线。
- 【核心逻辑】识别：列表 Item 内「广告/推广/赞助」角标 + 营销容器指纹
  （穿山甲 `NativeExpressView` / 优量汇 `GDTNativeExprView`）→ 标注
  （`mark_only`，引擎面板显示识别结果，浏览零干预）；仅当用户**主动点开**
  Item 关闭菜单时，代点菜单内「不感兴趣/减少此类推荐」（用户意图明确
  后的协助，非自动干预）。信息流扫描走内容管线节流预算（低端机降频）。
- 【实现路径】Java `evaluateRule` 的 `feed-mark` 分支（返回候选但不点击，
  `applyRuleAction` 对 mark_only/overlay_notice 强制零点击）。

#### 1.3.5 视频前贴（video_preroll）

- 【设计思路】前贴的「跳过广告」控件由广告方在倒计时结束后才挂载——
  引擎不能轮询硬扫（功耗），必须挂载时序驱动。
- 【核心逻辑】三信号确认前贴形态：播放器容器指纹（VideoView/ExoPlayer/
  ijkplayer className）+ 倒计时控件在场 +「跳过广告」文案。挂载时序：
  广告方约定 `realSkipDelayMs`（默认 5s）后挂载真跳过键——引擎静候
  控件树变化（TYPE_WINDOW_CONTENT_CHANGED 内容管线，学习窗期同类例外
  放行），一挂载即出手（`preroll_skip`）；假倒计时甄别：点了不跳则 1.2s
  后手势重试一次；跳转校验：正片进度条启动确认，误触迹象停手 + 误触
  反思日志。
- 【实现路径】Java `ScanResult.prerollWaitMs`（Service 侧静候调度）+
  内容管线例外放行标记；`engineMs` 与 `waitMs` 分账（等待时间是广告方
  制约，非引擎能力——与 Web 端演练场口径一致）。

#### 1.3.6 借鉴与规避对照

| 借鉴李跳跳 | 规避其缺陷 |
| --- | --- |
| 探针前置（findAccessibilityNodeInfosByText 先于 DFS） | 规则冲突未管控 → 调度器 duplicate/overlap/contradict 三类显式落库 |
| 形状门（面积/宽高比安全阀） | 学习规则无质量治理 → Wilson 下界 + 噪声四规则 + 生命周期 |
| 规则匹配效率意识（一次遍历多规则复用） | 保活激进（闹钟唤醒/双进程互拉）出局 → 体检 + 引导 + 事件驱动自愈（§2.3） |

### 1.4 匹配效率优化

#### 【设计思路】

v5.3.0 用真机日志证明：无差别整树扫描是功耗与误扫的根源（v5.2.0 扫描
2396 次/开机，其中 246 次是豆包/微信/拼多多主窗误扫）。v5.4.0 在此基础上
把「每窗口的匹配成本」进一步压缩：**便宜的判定永远先做，贵的判定只对
幸存窗口做，且贵判定内部把跨进程调用压到一次**。

#### 【核心逻辑】

窗口管线全序（每级的淘汰即短路）：

```text
P-2  敏感场景零扫描        包名/窗口类查表，O(1)，零节点访问
P0   学习指纹预拦截        (pkg, windowClass, 时机) 命中即断，零渲染等待
P0.7 SDK 窗指纹短路        windowClass 分段匹配 5 家 SDK 指纹
P0.8 CLEAN 档案           连续 3 次零候选窗口零扫描（只跑 P-1 探针）
P-1  探针哨兵              findAccessibilityNodeInfosByText("跳过"/"Skip")
                            系统原生 C++ 子树搜索，成本 ≈ DFS 的 1/14
P1~P6 五层规则 DFS         preset/gkd/learned/custom/guard 调度排序后
                            迭代栈 + 属性缓存匹配，命中即熔断
```

- **迭代栈 DFS**：非递归，无栈溢出风险；节点数/深度上限来自 PowerGovernor
  策略（§2.1），预算内可随时剪枝。
- **节点属性缓存**（李跳跳式，`MatchingEngine.NodeCache`）：AccessibilityNodeInfo
  的属性读取（getText/getViewIdResourceName/isClickable/…）是 binder IPC，
  重复读取是扫描慢的根因。一次 BFS 收集全树 `text/vid/desc/name/布尔标记/
  bounds/父子索引`，之后**所有** matcher（含 GKD DSL 关系符 `< > - + <<n`，
  关系在缓存索引上求值）零额外 IPC。多 matcher 复用同一次遍历。
- **多轮调度**：窗口切换 0ms 首扫 + 前密后疏重试链
  `[0/120/240/400/650/1000ms]`（按 16 档策略截断），命中即熔断；SDK 窗
  500ms 无真跳过键升级返回键直断（入口窗除外）。
- **CLEAN 档案**：`连续 3 次零候选 → CLEAN`，跨重启保留，72h 复查；复活
  条件：探针命中/候选出现/SDK 指纹/学习指纹。真机实测（v5.3.0 基线）：
  扫描 2396 → 稳态 15 次/开机（-94%），能耗 2798 → 162 µWh（稳态 -94%）。

成本对照（能耗模型）：

| 操作 | 单次成本 | v5.4.0 角色 |
| --- | --- | --- |
| 探针（findByText） | ≈0.05 µWh | 每窗必经（CLEAN 窗唯一开销） |
| 整树扫描（DFS+缓存） | ≈0.72 µWh | 仅非 CLEAN 窗、重试链内 |
| P0 预拦截 | ≈0（查表） | 学习指纹命中窗口 |

#### 【实现路径】

Java：`engine/MatchingEngine.java`（管线全序 + NodeCache/CacheNodeView）。
与 Web 端 `src/lib/instant-engine.ts` 的引擎分层注释一一对应（P-2/P0/P0.7/
P0.8/P-1 前缀两端一致），CI 金样本回放断言两级引擎对同一窗口流产出相同
动作序列。

---

## 二、Android 性能功耗兼容

### 2.1 分级动态功耗：16 档策略矩阵

#### 【设计思路】

一刀切省电的代价是低端机漏拦率飙升（李跳跳的教训）；一刀切全速的代价是
入门机电量崩盘（v5.2.0 Redmi 真机教训）。v5.4.0 用「设备四档 × 电量四态」
的笛卡尔积分级：设备档决定**能力预算**，电量态决定**节省强度**，二者
正交组合成 16 档策略。

#### 【核心逻辑】

设备档识别（`detectTier`）：`memScore(0~4) + coreScore(0~2) + socScore(0~3)`
→ `<3 入门 | <6 中端 | <9 高端 | ≥9 旗舰`。SoC 识别：API 31+ 用
`Build.VERSION.SOC_MODEL/SOC_MANUFACTURER`（题目所述 Build.SoC 系列字段），
低版本回退 `Build.HARDWARE` 关键词（骁龙 8 系板卡代号/天玑 9 系等）。

电量四态（`readBattery`，粘性广播零注册开销）：充电/满电、正常 >50%、
低电 20%~50%、极低 <20%。

16 档策略矩阵（`PowerGovernor.MATRIX`，数值与代码逐格一致）：

| 设备档 \ 电量态 | 充电 | 正常 | 低电 | 极低 |
| --- | --- | --- | --- | --- |
| 入门 | 节点280/深18/疏链/扫1000ms/预算12/学开 | 240/16/疏链/1200/10/开 | 200/16/最疏/1500/8/**关** | 160/14/最疏/2000/6/**关** |
| 中端 | 450/24/中链/600/30/开 | 400/22/中链/800/24/开 | 320/20/疏链/1000/18/开 | 260/18/最疏/1400/12/**关** |
| 高端 | 650/28/全链/400/60/开 | 600/26/全链/500/48/开 | 480/24/中链/700/36/开 | 380/22/疏链/1000/24/**关** |
| 旗舰 | 800/32/全链/250/90/开 | 750/30/全链/350/72/开 | 600/28/全链/500/54/开 | 480/26/中链/800/36/开 |

（格式：DFS 节点数上限 / 深度上限 / 重试链密度 / 相邻窗最小扫描间隔 ms /
内容事件预算每分钟 / 学习层开关。重试链：全链 [0,120,240,400,650,1000]、
中链 [0,180,400,800]、疏链 [0,240,650]、最疏 [0,650]。）

**红线（永不降级通道）**——哪怕极低档也保持不低于 POLICY_FLOOR（中档
参数）：

1. SDK 广告窗 className 指纹短路（零文本广告唯一判据，降级即漏拦）；
2. Dialog 弹窗双信号识别（角标+关闭键，弹窗窗期不缩水）；
3. 学习指纹 P0 预拦截通道（窗口附加即断，本身就是零成本通道）。

降级只作用于：DFS 节点数/深度、重试链密度、扫描间隔、内容预算、学习层
开关（`redlinePolicy()` 逐字段取当前档与 FLOOR 的较宽者）。

#### 【实现路径】

Java：`power/PowerGovernor.java`（矩阵 + 红线策略 + tier/battery 探测）。
Web 端演练场对照仪表（`POWER_PROFILE` 画像）用同一矩阵离线仿真功耗，
两端数值由 CI 校验。

### 2.2 全形态自适应阈值

#### 【设计思路】

v5.3.0 及以前的几何阈值（角落区、面积安全阀、半屏高度）按整屏像素写死，
在折叠屏展开、平板、分屏上系统性误判。v5.4.0 全部改为**按实际窗口动态
换算**，公式唯一、参数按形态分档。

#### 【核心逻辑】

形态分类：`isSplitScreen → SPLIT_SCREEN`（最优先）；`aspect < 1.35 →
FOLD_UNFOLDED`（方形内屏）；`min(w,h) ≥ 600dp → TABLET`；否则 `PHONE`。

阈值公式（`FormFitThresholds.compute`，minSide = min(w,h)）：

| 阈值 | PHONE | FOLD_UNFOLDED | TABLET | SPLIT_SCREEN |
| --- | --- | --- | --- | --- |
| 角落区 cornerRegionPx | `0.12×minSide` | `0.09×w`（按宽收缩） | `0.10×minSide` | `0.12×minSide`（实际窗口） |
| 跳过键面积上限 | 0.35 | 0.32 | **0.28**（宽高比<1.6 收紧） | 0.35（分母=实际窗口面积） |
| 宽高比形状门 | [0.6, 40] | 同左 | 同左 | 同左 |
| 半屏插屏高度区间 | [0.35, 0.55]×实际窗高 | 同左 | 同左 | 同左（**按实际窗高，分屏防误判关键**） |
| 悬浮球直径上限 | 0.12×实际窗宽 | 同左 | 同左 | 同左 |
| 全屏热区直断下限 | 0.55 可点面积 | 同左 | 同左 | 同左（守护名单豁免） |

折叠屏展开态角落区按宽收缩的依据：方形屏（aspect ≈1.0~1.1）宽≈高，
0.12×minSide 的角落区会吞掉约 1.4% 屏幕的边缘按钮，收至 0.09×w 后与
直板机的视觉占比一致；平板面积上限收紧的依据：4:3 屏同屏面积下营销按钮
普遍更大，0.35 上限会漏过大按钮，0.28 与直板机同「视觉占比」。

#### 【实现路径】

Java：`formfit/FormFitThresholds.java`（纯函数、零 Android 依赖，JVM 44 项
断言）；几何由 `SkipAdService.windowFrame()` 提供（API 30+ `getWindow()
.getBounds()`，分屏/折叠自动得到实际窗口）。

### 2.3 保活体检品牌矩阵与自愈

#### 【设计思路】

保活的行业歧途是「对抗系统」（闹钟唤醒、双进程互拉、FGS 滥用）——李跳跳
出局的直接原因之一。v5.4.0 的立场：**保活只做体检、引导、事件驱动自愈**，
三件事都站在系统规范内；用户被 Redmi 清理后台吊销无障碍登记后强制看 10
秒警告的三大诱因（自启动/省电策略/清理后台）全部有设置页直达，配置一次
不再复发。

#### 【核心逻辑】

品牌矩阵（`KeepAliveDoctor.checklist`，14 品牌族）：

| 品牌 | 关键体检项（critical 级） | 设置页直达 |
| --- | --- | --- |
| 小米/Redmi | 自启动 · 省电策略=无限制 · 清理后台白名单 | `com.miui.securitycenter` 自启动页 / `com.miui.powerkeeper` 省电页 |
| 华为 | 启动管理三连（自启动/关联启动/后台活动） | `com.huawei.systemmanager` 启动管理 |
| 荣耀 | 启动管理三连（MagicOS 同源） | `com.hihonor.systemmanager` |
| OPPO/一加/真我 | 自启动 · **后台弹出界面权限** | `com.coloros.safecenter` 自启动页 |
| vivo/iQOO | 自启动 · **后台弹出界面** · 最近任务加锁 | `com.vivo.permissionmanager` |
| 三星 | 休眠应用=从未 · 电池不受限 | `com.samsung.android.lool` 电池页 |
| 魅族/联想/中兴/努比亚 | 自启动 · 后台管理/白名单 | 厂商安全中心兜底 |
| Wear 轻设备 | 电池优化白名单（扫描策略单独降档：入门档 × 恒 LOW 态） | 标准电池优化页 |

服务吊销自愈（两态通知，`handleUnbind` + `notifyTwoState`）：

```text
onUnbind（断连打点）→ 15s 后探测登记状态（AccessibilityManager）:
  登记仍在（ALIVE）  → 通知「正在自动恢复」，点击打开应用完成拉活
                       （系统会在需要时重绑，服务登记零丢失）
  真被吊销（REVOKED）→ 通知直达无障碍开关页，文案明示：
                       「重新开启需等待 10 秒并手动确认——这是系统的
                         防诈设计，属正常流程，请放心操作」
                       （绝不自动点系统确认按钮）
```

合规红线注释（代码内同名注释）：不用前台服务滥用（无障碍服务由系统绑定
生命周期，无需 FGS 保命）、不用闹钟唤醒、不用双进程互拉。

#### 【实现路径】

Java：`keepalive/KeepAliveDoctor.java`（品牌识别/矩阵/直达 Intent 带
startActivity 失败回退应用详情页/两态通知真实现）；
`platform/Aosp14to16Adapter.hasMiuiNotificationAccess` /
`hasColorOsBackgroundPopup` 提前探测厂商特有权限缺失。

### 2.4 Android 14 ~ 16 适配要点

| 版本 | 适配点 | 实现 |
| --- | --- | --- |
| Android 14（API 34） | `accessibilityTool` 标记：未声明的服务在系统页被标注「非无障碍工具，可能收集数据」 | XML `android:accessibilityTool="true"` 为规范路径；运行时反射 `setAccessibilityTool(true)` 兜底；声明内容真实（只用窗口内容做广告判定，不采集屏幕上传） |
| Android 15（API 35） | 前台服务类型 enforcement：dataSync 类 FGS 6 小时上限被强制停止 | 夜间学习同步服务申报 `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 写明用途；`startSpecialUseForeground` 按 API 分叉 |
| Android 16（API 36） | 预测性返回：`onBackPressed` 不再保证触发，GLOBAL_ACTION_BACK 与用户返回手势时序竞争 | `performBackSafely`：派发前快照窗口键 → 派发后延迟 ≥120ms 复验 → 未切换才允许补发一次（每窗 ≤2 次），防双返回退出应用 |
| 厂商 ROM | MIUI 通知使用权（两态通知可见性）、ColorOS 后台弹出界面权限（吊销自愈通知弹出前提） | `hasMiuiNotificationAccess`（enabled_notification_listeners 检查）+ `hasColorOsBackgroundPopup`（AppOps 反射，失败保守放行 + 体检页引导） |
| 全版本 | 系统无障碍 10 秒确认框（A13+ 防诈设计） | `isSystemConfirmWindow` 统一出口：引擎 + 平台双层冗余识别，全体静默只引导，绝不 auto-click |

---

## 三、安全合规

### 3.1 敏感场景零扫描：12 类信号表

零扫描是隐私红线不是性能优化：命中窗口**不收集节点、不跑探针、不进 DFS、
不学习**（这些窗口可能正显示密码/验证码/身份证号，任何节点读取都不可
接受）。`SensitiveSceneGuard.detect(pkg, windowClass, hasPasswordField[, windowTexts])`
三参快速路径不读任何节点内容，只有包名与窗口类。

| # | 场景 | 信号（任一命中） | 判定优先级 |
| --- | --- | --- | --- |
| 1 | 权限对话框 | pkg ∈ permissioncontroller/packageinstaller/MIUI LBE；窗口类含 grantables；**含无障碍 10 秒确认框（systemConfirm 标记，绝不自动点）** | 1（最高） |
| 2 | 锁屏 | SystemUI + keyguard 窗类 | 2 |
| 3 | 输入法 | 窗口类含 InputMethod | 3 |
| 4 | 密码 | `hasPasswordField = true`（树存在密码框） | 4 |
| 5 | 通话 | 拨号/InCallUI 包名或窗类 | 5 |
| 6 | 短信 | 系统短信应用包名 | 6 |
| 7 | 系统设置 | com.android.settings + 厂商安全中心包名 | 7 |
| 8 | 支付 | 守护应用包名前缀（支付宝/银联/银行系）或 收银台/支付密码/银行卡号 文本 | 8 |
| 9 | 转账 | 转账/汇款/收款人 文本 | 9 |
| 10 | 实名 | 实名认证/身份证/人脸识别 文本 | 10 |
| 11 | OTP | 验证码/校验码/动态码/OTP 文本 | 11 |
| 12 | 验证码 | 图形验证/滑块验证/请完成验证 文本 | 12 |

守护应用（银行/政务/支付）与敏感零扫描的关系：整窗零扫描针对**输入类
敏感窗**；守护应用的广告窗仍然照拦（guard_skip 温和代点，服务零干预）。

### 3.2 权限透明化审计

- 「关于」页展示：申请的全部权限清单 + 每项权限的用途说明 + 最近 7 天
  权限使用次数（结构化日志聚合）；
- 无障碍权限用途声明（A14+ accessibilityTool 语义）：只读窗口内容做广告
  判定，不做屏幕截图、不做输入监控、不上传内容；
- 每次代点的动作审计事件（`compliance_audit`）全量落盘，用户可在设置页
  查看最近 100 条「引擎替你点了什么」。

### 3.3 脱敏全链路

| 环节 | 脱敏规则 |
| --- | --- |
| 结构化日志 | data 内 `text/desc/title/content` 键截断 32 字符；`pkg/package` 保留（统计维度）；其余字符串截断 120 字符 |
| 学习数据 | 学习指纹只含容器签名/viewId/窗口类/时机，不含任何文本内容与坐标 |
| 夜间同步 | 仅同步脱敏统计（规则命中数/画像直方图），不含包名以外的任何用户数据 |
| 反馈通道 | 上报内容经同样截断规则，Issue 模板明示脱敏口径 |

### 3.4 动作边界自检清单（ComplianceSentinel）

1. 动作类型 ∈ ACTION_BOUNDARY 白名单（16 类，白名单外一律拒绝）；
2. 系统确认框（含无障碍 10 秒警告）绝不自动点，只引导用户；
3. 积极按钮（立即更新/立即抢购/下载/开通/支付类 CTA）永不触碰；
4. `decline_tap` 只允许点消极按钮词库内目标；
5. 面积安全阀：普通代点 ≤35% 窗口面积，坐标级 ≤12%（全形态阈值取更严者）；
6. 输入类控件与密码邻近目标一律拒绝；
7. 全部审计事件结构化落盘（四大原则常量见 `ComplianceSentinel` 类头）。

四大原则：最小干预 / 宁漏勿误 / 动作透明 / 数据最小化。

---

## 四、工程架构

### 4.1 跨端统一方案

```text
           ┌─────────────────────────────────────────────┐
           │  TS 规范源（Web 端 src/lib/）                  │
           │  rule-dsl.ts / rule-scheduler.ts /            │
           │  rule-quality.ts / structured-log.ts          │
           └──────────────┬──────────────────────────────┘
                          │ 导出金样本集（表达式 + 节点树 + 期望结果）
                          ▼
           ┌─────────────────────────────────────────────┐
           │  CI 金样本门禁（ci.yml android job）           │
           │  TS describe() 与 Java describe() 逐条一致；  │
           │  同一节点树上匹配结果逐条一致                  │
           └──────────────┬──────────────────────────────┘
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
┌──────────────────────┐      ┌──────────────────────────┐
│ Java 对齐实现          │      │ Web 演练场/调试沙箱        │
│ com.skipad.*（12 类） │      │ instant-engine.ts 回放     │
│ NodeLike 统一节点抽象  │      │ 同一管线前缀 P-2/P0/P0.7…  │
└──────────────────────┘      └──────────────────────────┘
```

关键设计：`RuleDslParser.NodeLike` 是双端同构的节点抽象——Android 侧由
AccessibilityNodeInfo 适配（SkipAdService.AndroidNode，属性懒缓存）、引擎
侧由 NodeCache 视图适配（一次收集零 IPC）、CI 侧由金样本驱动。引擎本体
（MatchingEngine/RuleDslParser）不 import 任何 Android API，JVM 可直测。

### 4.2 统一 DSL 语法规范

详见 [GKD-COMPAT.md](./GKD-COMPAT.md)。速览：

- 属性选择器 6 操作符：`=` `!=` `*=`（包含）`^=`（前缀）`$=`（后缀）`~=`（正则）；
- 属性 16 个：vid/id/name/text/desc + 9 布尔 + index/depth；
- 关系符 5 种：`<<n`（第 n 代祖先，缺省任意祖先）、`<`（父）、`>`（子）、
  `-`（下一兄弟）、`+`（上一兄弟）；
- 逻辑组合：`&&`（优先级高）`||`；目标标记 `@`（缺省最后一段）；通配 `*`；
- 降级策略：未知属性/正则错误 → 条件恒不成立 + 编译警告；关系段简化 →
  编译警告；单条坏规则绝不中断整包订阅导入。

### 4.3 CI/CD 流水线

`ci.yml`（push/PR 触发，concurrency 取消旧跑）：

| Job | 内容 | 缓存 |
| --- | --- | --- |
| web | bun install → prisma generate → `bunx tsc --noEmit` → `bun run lint` | bun.lock 为 key |
| android | `./gradlew :app:testDebugUnitTest`（769 项断言）+ `:app:lintDebug`；gradlew 未合入时 notice 跳过（脚手架渐进合入） | gradle wrapper + caches |

`release.yml`（tag `v*` 触发）：

```text
1. 签名构建:  secrets.ANDROID_KEYSTORE（base64）→ 解码注入 →
              assembleRelease（签名配置全部走环境变量，绝不入库）
2. 更新日志:  conventional commits 分组
              feat→新特性 / fix→修复 / perf→性能 / chore,docs,style,refactor→其他
3. 发布:      GitHub Release 附 Skip-Ad-vX.Y.Z.apk + CHANGELOG
4. Web 同步:  构建自检（Vercel Git 集成自动部署）；
              预留 CLI 直推与静态导出两种备选（workflow 内注释开关）
```

### 4.4 结构化日志规范

单行 JSON 五字段（两端同构，字段顺序固定可逐字节比对）：

```json
{"ts":1732100000000,"level":"info","module":"service","event":"block_action",
 "data":{"action":"instant_skip","rule":"probe-sentinel","layer":"builtin",
         "pkg":"com.ximalaya.ting.lite","confidence":0.95,"engineMs":34,
         "preempt":false,"guard":false}}
```

- 级别：verbose/debug/info/warn/error（release 默认 info）；
- 脱敏：§3.3 规则；
- 滚动文件：单文件 2MB × 3 个（skipad-0.log ~ skipad-2.log 环形覆写）；
- 事件字典（模块.事件）：service.service_connected / service.block_action /
  service.interrupt / engine.compliance_audit / engine.profile_revive /
  keepalive.service_unbind / keepalive.registration_probe /
  keepalive.health_report——新增事件须先登记字典再使用。

---

## 五、Web 端三大新功能

### 5.1 规则调试沙箱

自定义节点树编辑器（JSON/可视化双模式）+ 实时匹配：任选 preset/gkd/learned/
custom 规则在沙箱节点树上试跑，管线逐步视图（P-2 → P0 → P0.7 → P0.8 →
P-1 → 五层 DFS 每级命中/短路状态），单步调试（步进到某个 matcher 查看其
输入输出），导入导出（GKD 订阅片段 / 节点快照 / 命中报告三格式）。

### 5.2 情报站可视化看板

拦截趋势（日/周/月）、应用排行（命中数/平均耗时）、时段分布（24 桶直方图，
东八区）、命中率漏斗（探针/五层/兜底分级归因）、功耗画像（对照
POWER_PROFILE v520/v530/v540 三代仪表）、SDK 家族维度统计（csj/gdt/ks/
admob/host 命中占比，BlockEvent.sdkFamily 聚合）。

### 5.3 规则可视化管理

规则列表（源/状态/质量分级/命中数/优先级多维筛选）+ 编辑器（DSL 语法
高亮 + 即时编译警告）+ 批量操作（启停/删除/导出）+ 优先级拖拽（写回
`priority` 字段，>0 人工直通）+ 冲突裁决面板（RuleConflict pending 列表
一键 合并/阴影/驳回）。

---

## 六、测试体系

完整内容见 [TEST-PLAN.md](./TEST-PLAN.md)。要点：

- 单元断言：v5.3.0 存量 427 项 + v5.4.0 新增 342 项（调度器 68 / 质量模型
  54 / DSL 解析 92 / 敏感防护 36 / 功耗策略 48 / 全形态 44）= **769 项**，
  `./gradlew :app:testDebugUnitTest` 全量执行（ci.yml android job）；
- 自动化场景回归：百款主流应用语料回放框架（语料字段 → 模拟窗口流 →
  引擎离线跑分 → 命中率/误触率/耗时报告）；
- 误触率专项：侧边菜单/抽屉/支付/输入/小程序/聊天/列表浏览正常使用场景
  零误触验证，回归红线：**误触率必须 ≤ v5.3.0 基线**。

---

## 七、向后兼容性保障

| 场景 | 保障措施 |
| --- | --- |
| 旧库自动补种 | `RuleScheduler.seedPresetIfEmpty` 只补缺失预置项，不动存量规则的 hitCount/quality/status——存量命中路径零影响 |
| GKD 订阅无感迁移 | 订阅导入即去重（同 gkdRef + 同 pattern 跳过），已导入规则 id/gkdRef 不变；语法子集 100% 兼容，降级只产生编译警告不产生行为差异 |
| 调度对存量命中路径零影响 | 探针前置/内置兜底规则不在调度器管辖内（builtin 层，优先级高于五层规则）；预置规则 computeScore 下限 = 源权重 10 + quality/4，兜底可出手 |
| 回滚开关 | 设置页「统一调度」总开关：关闭后五层规则按 v5.3.0 顺序（preset → learned）执行，多源特性整体旁路；16 档功耗策略与全形态阈值独立开关，可单独回滚 |
| 数据迁移 | Rule 新增字段全部带默认值（priority=0/quality=0/status=active），旧记录无需迁移脚本；RuleConflict 仅新增表 |

## 八、四平衡声明

v5.4.0 的每个优化都落在四维平衡框架内，任何单维收益不得以牺牲其余三维
为代价：

1. **效果**：五类新场景 + GKD 订阅生态，目标场景覆盖率从 v5.3.0 的六类
   提升到十一类；探针/指纹/兜底全链路命中率红线 100% 不妥协。
2. **性能**：调度 O(n log n) 排序 + DSL 编译缓存 + 节点缓存零重复 IPC；
   单窗引擎耗时（engineMs）红线 ≤ v5.3.0 基线（p95 ≈ 44ms）。
3. **功耗**：16 档策略矩阵按设备与电量分级；红线通道之外全部可降级；
   稳态能耗红线 ≤ 200 µWh/开机（v5.3.0 实测 162）。
4. **合规**：12 类敏感场景零扫描、动作边界七条自检、四大原则常量化、
   10 秒确认框绝不自动点、脱敏全链路、保活零对抗——工具的生命线。

冲突裁决优先级：**合规 > 功耗 > 性能 > 效果**。任何优化提案在 CI 报告中
必须四维全绿方可合入（见 TEST-PLAN.md 回归红线一节）。
