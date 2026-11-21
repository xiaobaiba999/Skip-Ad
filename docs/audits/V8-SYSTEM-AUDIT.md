# Skip-Ad v8.0.x 系统级深度审计报告（V8-SYSTEM-AUDIT）

> 审计轮：v8.1.0 审计轮（2026-11-27）· 审计者：GLM（无聊天上下文接管）
> 审计对象：`xiaobaiba999/Skip-Ad` main `9c59f2549be561cb187d3bfff609bdf3e3c84c65`
> 方法：全量源码通读（AdSkipService 5932 行逐段 + core 全模块 + 接线点行号级核对）+ 工具链实测（ECJ 3.33 + android-34 platform + 34 套件 JVM 回归）。
> 证据等级口径：STATIC_REVIEW / AUTOMATED / SIMULATED / REAL_API / REAL_DEVICE / UNKNOWN / NOT_RUN。
> Worker 仓（`Skip-Ad-Log-Upload`，私有）：本沙箱无认证 → 全部标 UNKNOWN，不据旧聊天记录假读。

---

## 0. 环境与基线核验（本轮实测）

| 项 | 结果 | 等级 |
|---|---|---|
| 远端 main SHA | `9c59f2549be561cb187d3bfff609bdf3e3c84c65`（与已知基线一致） | 实测（git ls-remote） |
| 本地克隆 HEAD | 同上，工作区干净 | 实测 |
| 交接文档 | `docs/PROJECT-HANDOFF-V8.0.0.md` 在场且与代码逐项吻合（行号/哈希/模块全部核对） | STATIC_REVIEW |
| Worker 仓可达性 | 未认证 API 404（私有仓）→ 无法读取 `HANDOFF-V8.0-WORKER.md` | UNKNOWN |
| 基线 JVM 测试 | **首轮运行被 secret-scan 阻断**（见 P0-1）；修复后 34 套件 · 6802 断言 · 0 失败 | AUTOMATED |
| APK v8.0.0 | 在仓（878134B · SHA-256 `a6782b06…3235c` 与交接一致） | STATIC_REVIEW |
| GitHub 凭证 | 本沙箱无任何 token/凭据 → push 能力 UNKNOWN（本轮末尾实测） | 实测 |

---

## 1. 真实调用链（实测绘制，行号以 HEAD 为准）

### 1.1 开屏主链（窗口切换 → 终态）

```
onAccessibilityEvent (AdSkipService:1592)
 ├─ 前置过滤链（自家包/热路径内容事件/桌面/白名单/主开关/相机/零扫描场景/
 │   小程序容器/系统噪声/息屏/SMS 覆盖层——全部 return 短路，1611-1721）
 ├─ TYPE_WINDOW_STATE_CHANGED (1726)
 │   ├─ 非前台 Dialog/小窗 → nudgePopupScan + maybeInterstitialScan (1739-1757)
 │   ├─ 回切/进入判定 ReentryGuard.isEntering (1772)
 │   ├─ 旧任务窗切终局 markTaskWindowChanged + taskOnWindowReset (1818-1826)
 │   ├─ 逐窗状态重置（14 张 per-pkg 表 remove/put，1827-1847）
 │   ├─ posVerifyOnWindowChange (1870)
 │   ├─ bumpScanChain (1871) —— 排 18 档梯子 SCAN_AT{0..12000ms}（2264-2274）
 │   ├─ 内联首扫 tryScan(pkg, winAt, 0) (1879)  ←★与梯子 0ms 档重复（见 P2-1）
 │   ├─ maybeFeedScan (1884) / maybeInterstitialScan (1887)
 ├─ TYPE_WINDOW_CONTENT_CHANGED (1890)
 │   ├─ contentAt 记账 + posVerifyOnContentChange
 │   ├─ 开屏窗内 → nudgeScan（80ms 真节流，2534）+ maybeInterstitialScan
 │   └─ 弹窗窗期 → nudgePopupScan（240ms）+ maybeInterstitialScan
 ├─ TYPE_VIEW_CLICKED → 学习/误触观察（1938）
 └─ TYPE_WINDOWS_CHANGED → 前台包插屏扫描（1947）

tryScan (3196)
 ├─ 12 道前置门（主开关/白名单/相机/聊天主界面/小程序/档案抑制…）
 ├─ videoLike 画像（VideoSplashGuard.videoLike 纯函数）
 ├─ collectRoots (4015)——活动窗口优先，getWindows 兜底（≤3 窗）
 ├─ handleFeedbackSheet (3850)——近期出手过才走查（80 预算）
 ├─ per-root：
 │   ├─ treeHasCodeBannerMarker BFS ≤60（安全守护①）
 │   ├─ treeHasMiniProgramMarker DFS ≤80（安全守护②）
 │   ├─ nativeTextProbe 5 词（findAccessibilityNodeInfosByText ×5）
 │   ├─ RuleEngine.matchAll DFS（预算 340~600）
 │   ├─ SubscriptionManager.matchSources（无覆盖包零树遍历——预过滤）
 │   ├─ AdDecisionCore.walk DFS（预算 340~600）
 │   └─ detectUpdateDialogSigns DFS ≤120（splash/popup 期）
 ├─ 零候选退避/就地 SUCCESS/videoLike 轮询排针
 └─ logScanMiss 诊断（每窗一条）

execute (4044)
 ├─ L0 小程序终门 → 边界自检三重断言（ComplianceGuard）
 ├─ 用户让路 userYield → 预算梯子 → 返回键 heads-up/根窗安全门
 ├─ 任务门 taskBeginOrCheckClick（AdTaskCore 状态机——终态 2.2s 冷却）
 ├─ 冷却 max(300ms, verifyDelayMs) → F/H/I 多点防线 → 角落白点节流
 ├─ clickNode：refreshLive 新鲜度复核 → gestureTap（1:1 人手触点）→ performAction 兜底
 ├─ taskOnDispatch → VERIFYING + scheduleVerifyScan（人手节奏 500ms/1.4/2.4/3.6/5.2/7.0s）
 └─ 埋点：DecisionEvidence.recordDecision + LogEvents.click

复验链：scheduleVerifyScan → tryScan(-1) → taskOnScanOutcome
 → 两轮零候选 → SUCCESS（就地）/ onVerifyStillStep → RETRY_WAIT|FAILED（MISS 记账）
```

### 1.2 并行支线

- **信息流**：maybeFeedScan (2709) → nativeFindTexts 角标/中性词直查 → FeedAdGuard 两段式 → executeFeed。
- **插屏**：maybeInterstitialScan (2951) → walk(300) 取 scroll/webview/corners + nativeFindTexts 语境/关闭词 → InterstitialGuard.decideFromCandidates → executeInterstitial。
- **视频轮询**：videoPoll (3599) → pollDue 自适应三档（1.5s/3s/3s）→ 每 3 针一次全量 tryScan + 探针 10 词。
- **日志上传**：tapEvent (313) → LogBatchStore.appendEvent（log-store-io 守护线程）→ LogUploadDispatcher（CAS 单循环线程）→ Net LOG_RELAY_URL。心跳 KeepAliveReceiver → maybeAutoUploadLog (718)。

### 1.3 线程模型（实测确认）

| 线程 | 用途 | 与无障碍主链关系 |
|---|---|---|
| main（Looper） | 事件接收/扫描/点击派发/Handler 队列 | 无障碍热路径本体 |
| log-store-io（守护） | 日志落盘（appendEvent 异步 submit） | 零阻塞（结构性） |
| dispatcher 上传线程（守护） | HTTPS 上传 + 退避 | 零阻塞（CAS 门单循环） |
| binder 池 | getChild/getWindows IPC | 扫描成本主体 |

---

## 2. 安全与正确性审计结论（最高优先级项）

**未发现任何 FALSE_CLICK / DUPLICATE_CLICK / 状态机护栏弱化问题。** 逐项核对：

| 检查面 | 结论 | 证据 |
|---|---|---|
| 九态状态机转移合法性 | AdTaskCore 纯函数真值表完备；非法转移原样返回；终态不可逆 | AdTaskCore.java 全读 + 既有 AdTaskTest/AdTaskTimingTest/Bug001RegressionTest |
| taskId/generation 贯穿 | taskIdOf=winStart#seq 双因子；taskLiveFor 验活；scanGen 代际护栏在全部 18 档梯子/nudge/复验/返回键复验回调 | AdSkipService 2253/2267/2577/4180 逐处核 |
| 重复点击断源 | 冷却 max(300ms,人手节奏) + F 同指纹静默 + H 结果静默 + I 每窗 2 击 + 终态 2.2s 冷却 + gaveUp 收手 | execute 4230-4262 全核 |
| 旧回调污染新任务 | 代际不等即 return（所有 postDelayed 闭包） | 2254/2267/4182 |
| ACK 前不删日志 | LogBatchStore 状态机 PENDING/UPLOADING 保留；FAILED 留检视 | LogBatchStore.java + LogBatchStoreTest B8/B9 |
| 未授权零磁盘写 | collectEnabled 静态门 + appendEvent 首行拒绝 | LogBatchStore:138/164 |
| 上传阻塞无障碍链 | 埋点=非阻塞 submit；上传独立线程；全链 try/catch 吞尽 | AdSkipService:313-321 + Dispatcher |
| 探测/规则/决策三路同闸 | 全部汇入 execute() 统一终闸（边界自检/用户让路/任务门） | execute 4044 入口 |
| APK 零凭据 | secret-scan 四道检查在岗（本轮实测通过——修复 P0-1 后） | scripts/secret-scan.sh |

**结论：核心安全面（安全 > FALSE_CLICK > DUPLICATE_CLICK > MISS）无任何可让渡空间，本轮零安全门改动。**

---

## 3. 发现的问题清单

### P0-1【仓库卫生 · 实锤】secret-scan 闸门在 HEAD 上全断（本轮已修复）

- **文件/方法**：`reports/iteration-v8.0.0.md:43`（文档字符串）× `scripts/secret-scan.sh` 检查①正则。
- **当前行为**：该行文档字面含 PEM 头形态（列举扫描模式清单的文字），命中 `BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY` 正则 → `run-tests.sh` 与 `build.sh` 在 secret-scan 阶段直接 exit 1。
- **产生条件**：上一轮构建轮先跑闸门、后写报告并提交——报告入库后闸门未复跑。
- **实际影响**：**任何后续会话无法运行测试/构建**（本轮接管首跑即被阻断）；交接文档"secret-scan 重跑 PASS"的口径与 HEAD 真实状态不符（时序差）。
- **修复**：改写该行措辞（PEM 头字面 → 「私钥头」中文表述），扫描闸强度零变化。注：本轮新写的三份文档一度复刻同一字面形态（该形态在技术文档中天然高频，佐证系统性）——已同步修正。修复后闸门 PASS + 6802 断言 0 失败（AUTOMATED）。
- **证据等级**：AUTOMATED（阻断现场复现 + 修复后复跑）。

### P2-1【性能 · 实锤】窗口切换 0ms 双重全量扫描

- **文件/方法**：`AdSkipService.onAccessibilityEvent:1871`（bumpScanChain 排梯子含 0ms 档）+ `:1879`（内联首扫 tryScan(…,0)）。
- **调用关系**：同一次 TYPE_WINDOW_STATE_CHANGED 事件里，先排 18 档梯子（第一档 at=0），再内联同步跑一次全量 tryScan；几毫秒后梯子 0ms 档再跑一次全量 tryScan。
- **当前行为**：同一棵刚渲染的树被完整扫描两遍（安全守护①②+ 反馈面板查 + 探针 5 词 + matchAll DFS + matchSources + walk DFS + updateDialog 查 120 预算，至多 ~1500 节点访问 ×2）。
- **问题产生条件**：每次应用启动/切窗（最高频扫描路径）。
- **实际影响**：每次窗口切换浪费一整次全量扫描（CPU/binder/电量）。18 档梯子的第二档 60ms 足以覆盖树演化。
- **优化方案**：bumpScanChain 增加重载参数 skipFirstRung——窗口切换路径（1871）传 true（内联首扫已覆盖 0ms 档）；另两处调用（nudgeScan:2537 无内联先行、返回键复验:4189 600ms 后无先行）保持 false。
- **是否改变业务行为**：否——内联首扫与 0ms 档对同一棵树跑同一决策梯，幂等（源码注释 1875-1876 自认"随后仍会扫（幂等）"）；60ms 档保留，晚到按钮覆盖不变。
- **测试**：服务侧方法不在 JVM 测试面（AdSkipService 依赖 Android 运行时）→ 全源编译验证 + 6802 回归零退化 + 本审计行号级论证。
- **证据等级**：STATIC_REVIEW + AUTOMATED（编译/回归）。

### P3-1【性能 · 实锤但本轮不动】matchAll + walk 双 DFS 串行同树

- **位置**：`tryScan:3388/3459`——零命中扫描时 RuleEngine.matchAll（文本规则 DFS）与 AdDecisionCore.walk（决策核心 DFS）先后对同一棵树各走一遍（预算 340~600 各自独立），同一节点的 text/desc/cls/bounds/clickable 重复读取、重复分配（`NodeAdapter.text()` 每调 `toString().trim()`）。
- **影响**：零命中扫描（大多数档位）遍历成本约 2×。
- **方案（本轮否决）**：单遍多消费者合并遍历。否决原因：两套预算/早退/收集语义深度耦合，合并重构需真机回归兜底——违反"不在没有测试的情况下重构核心状态机"纪律。**列为下轮专项（需先建 JVM 对照测试）。**

### P3-2【性能 · 实锤但本轮不动】videoPoll walkDue 针的双重工作

- **位置**：`videoPoll:3643-3668`——每 3 针一次的全量 tryScan 之后，同一针继续 collectRoots + 10 词探针；探针命中再触发第二次全量 tryScan。
- **影响**：每 3 针浪费 10 次原生全树搜索 + 1 次 collectRoots；命中时多一次全量扫描。
- **方案（本轮否决）**：walkDue 针跳过自有探针（tryScan 内部探针+walk 关闭/角落路径已覆盖全部 10 词的目标可达性）。否决原因：与 videoProbeMiss 自适应节拍（1.5s↔3s 降档）语义耦合——跳过探针会使 walkDue 针的 miss streak 恒 +1，降档时机提前，文本按钮在降档态的发现延迟 1.5s→3s。无真机数据前不动。

### P3-3【资源 · 记录】LogBatchStore.appendEvent 每事件全文件读+写

- **位置**：`LogBatchStore.appendEvent:163-178`——每条事件先 readJsonArray(PENDING_FILE)（≤400 事件全量）再 writeJsonArray 全量回写。O(n) IO/事件。
- **影响评估**：事件率低（终局/点击/复验级，非每扫描），IO 线程独立，实际负载小；ACK 前不删/崩溃恢复语义依赖此文件结构。**收益/风险比不佳，记录不改。**

### P3-4【资源 · 记录】maybeInterstitialScan 为 3 个信号跑整树 walk

- **位置**：`maybeInterstitialScan:3021`——预算 300 的 walk 只为 scrollPresent/webviewPresent/corners 三个信号。
- **影响**：非 feed 名单窗的内容事件（1.5s 节流）每次 ~300 节点遍历。专用轻量遍历可省 ~30% 属性读取，但收益有限且需复制 walk 语义。**记录不改。**

### P4-1【健壮性 · 记录】防膨胀 clear() 全清模式

- **位置**：`contentAt:1902`/`zeroCandScans:3518`/`subConsultedWin:3403`/`adTasks:4600`/`firedTargetAt`（有淘汰版）等 8 处——超限整体 clear()。
- **影响**：>96 包时清掉当前前台包的状态（videoLike 静默计算回退 elapsed 语义、zeroCand streak 归零）。触发条件 = 单次服务生命周期内交互 96+ 个包——罕见。**行为可接受，记录不改。**

### 被否决的方案（含理由）

| 方案 | 否决理由 |
|---|---|
| 合并守护走查①②为单遍 | 两者遍历顺序不同（BFS 60 vs DFS 80）——合并改变预算截断语义 = 安全闸行为变化，禁止 |
| 缓存 detectUpdateDialogSigns 每窗结果 | 更新弹窗可中途出现，缓存导致 update_negative 路径漏判（MISS 回归） |
| 跳过早期档位的 updateDialog 查 | 弹窗可为首窗，无安全跳过依据 |
| matchSources 降频（每窗一次） | `needSub=hits.isEmpty()` 使零命中每扫必查——但订阅 vid 键晚出现依赖每扫咨询，降频=MISS 回归；且无覆盖包已被预过滤挡住（零树遍历），实际成本远低于预估 |
| NodeAdapter 属性 memo | 跨遍历复用节点缓存违反"不缓存已失效的 AccessibilityNodeInfo"红线；单遍内 memo 收益 <10% |
| 引入 WorkManager/对象池/响应式库 | 违反"优先 Java 标准能力"；APK 零依赖红线；当前规模无收益 |

---

## 4. 跨模块协作与契约一致性

### 4.1 Android ↔ Worker 契约（客户端侧可核验部分）

客户端契约面全部在 `LogUploadPolicy.java`（133 行，55 断言覆盖）+ `LogPrivacyFilter.java`（47 断言）：schemaVersion 1 顶层九键、48 事件/批上限、终态语义（MANUAL_RECONCILIATION_REQUIRED 立即终态不换号）、429 retryAfter 钳制 [30s,24h]、退避 30s→480s×5。**服务端 `validate.ts`/`LogGate` 一致性：UNKNOWN（Worker 仓不可达）**——双端 `privacyFilterVersion` 同号递增机制在案，真不一致需 Worker 仓核对。

### 4.2 状态归属盘点（无冲突双源）

per-pkg 状态分布在 40+ HashMap，经逐表核对**无同一语义双表记账**；任务层（AdTask）与点击层（clickTries/lastClickAt）职责分离且有任务门统一仲裁。winStart 代际（windowAt）+ scanGen 双护栏覆盖全部延迟回调。

### 4.3 日志与核心解耦（实测确认）

埋点三特征全部满足：① 非阻塞（executor submit，AdSkipService:313-321）；② 独立线程（log-store-io / dispatcher）；③ 全链吞异常（Throwable ignored 包裹）。**广告跳过不依赖日志系统任何返回值——结构性独立。**

---

## 5. 测试覆盖缺口

| 缺口 | 影响 | 建议 |
|---|---|---|
| AdSkipService/MainActivity 不在 JVM 编译面 | 服务侧改动仅全源 ECJ 编译兜底（build.sh）| 下轮把可抽取的调度决策（梯子/节流参数）下放 core 纯函数 |
| videoPoll 节拍与 miss-streak 交互无专项 JVM 测试 | P3-2 类改动无安全网 | 补 VideoPollPacingTest（纯函数面已有 adaptivePollGapMs 可测边界） |
| 双 DFS 合并无对照基准 | P3-1 无法安全实施 | 先建 ScanCostBenchmark（JVM 假树计时）再动 |
| L3 真机 50 款 | 全部 PENDING_REAL_DEVICE | 需真机环境（本沙箱 NOT_AVAILABLE） |

---

## 6. 200+ 应用兼容性现状

- `compatibility/` 目录：apps_catalog.json（233 款 L1 基线）+ l3_batch1.json（50 款 12/12 类配额）+ samples/ 空库（0 条，诚实）。
- L1 = RULE_ONLY（静态规则断言）；L2 = SIMULATED（场景真值表）；L3 = PENDING_REAL_DEVICE（未预填）。
- **分级口径正确，无升格伪造**。本轮不推进 L3（无设备），L1/L2 由既有 34 套件承载（AppCatalogTest/PageScenarioTest 等）。

---

## 7. 外部技术方案评估（按需引入判据）

| 技术 | 对应本项目问题 | 引入判定 |
|---|---|---|
| 事件驱动架构（Android a11y 已是） | 已是事件驱动 + 梯子补扫混合——切纯事件驱动会丢静默视频窗（videoPoll 存在的理由） | 不引入 |
| 单遍多消费者遍历（DOM 增量思想） | P3-1 双 DFS | **方向正确**，需专项轮 + JVM 基准 |
| 背压（Reactive Streams） | 日志缓冲已有容量上限 + 关键事件最后丢策略 | 已有等价机制，不引入库 |
| WorkManager | 上传重试已有退避状态机 + 心跳节拍；WorkManager 引入 Google Play 服务依赖面 | 不引入 |
| OkHttp 重试语义 | LogUploadPolicy 裁决表已覆盖（ACK/WAIT/RETRY/FAIL 可穷举） | 已有等价设计 |
| Redis 幂等 / DO 串行化 | batchId 幂等已由服务端 LogGate + 客户端"只读已存批次"结构性保证 | 已有等价设计 |
| 隐私优先采集 | 白名单工厂 + 双端过滤 + 授权门已是该范式 | 已达标 |

---

## 8. 优化优先级汇总（进 V8-OPTIMIZATION-PLAN）

- **P0**：P0-1 secret-scan 闸门修复（本轮完成）
- **P2**：P2-1 窗口切换 0ms 双重扫描消除（本轮完成）
- **P3**：P3-1 双 DFS 合并（专项轮）/ P3-2 videoPoll 双工作（需节拍测试先行）/ P3-3 appendEvent IO（低收益）/ P3-4 插屏 walk（低收益）
- **P4**：P4-1 clear() 全清模式（罕见路径）
- **不改**：全部安全闸/状态机/决策核心（第 2 节结论）

## 9. 不建议修改的稳定模块

AdTaskCore（九态真值表）、AdDecisionCore.visit/decide（决策核心，经 7 轮实战回归）、SafetyGuard 体系（ComplianceGuard/MiniProgramGuard/InterstitialGuard 边界闸）、HumanTap/TapPoint（触点防线）、LogPrivacyFilter/LogUploadPolicy（双端契约）、RuleEngine 匹配层（v7.1.0 算法轮已优化：零分配归一/HashSet/预编译/预筛缓存）。
