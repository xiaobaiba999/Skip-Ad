# Skip-Ad v8.0.x 优化实施方案（V8-OPTIMIZATION-PLAN）

> 依据：`V8-SYSTEM-AUDIT.md`（同轮）。原则：安全 > FALSE_CLICK > DUPLICATE_CLICK > MISS > 正确关闭 > 命中率 > 性能。
> 每项含：证据 / 位置 / 方案 / 风险 / 回归 / 预期收益 / 验证方法。

---

## P0-1 secret-scan 闸门修复【本轮实施】

- **证据**：`./run-tests.sh` 首跑即断（`reports/iteration-v8.0.0.md:43` 文档字符串命中 PEM 头正则）——AUTOMATED 复现。
- **位置**：`reports/iteration-v8.0.0.md:43`。
- **方案**：改写措辞（PEM 头字面 → 「私钥头」中文表述 + 审计注记）。**扫描脚本零改动**（闸门强度不变）。
- **风险**：无（纯文档措辞；不弱化任何检查）。
- **回归**：secret-scan PASS + 34 套件 6802 断言 0 失败。
- **预期收益**：测试/构建闸门恢复可用（所有后续轮次的前提）。
- **验证**：`./scripts/secret-scan.sh` 退出码 0 + `./run-tests.sh` 全绿。

## P2-1 窗口切换 0ms 双重全量扫描消除【本轮实施】

- **证据**：`AdSkipService:1871` bumpScanChain 排 18 档梯子（含 at=0 档）+ `:1879` 内联 `tryScan(pkg, winAt(pkg), 0)`——同事件内同一棵树全量扫两遍；源码注释 1875-1876 自认冗余（"链上 0ms 档随后仍会扫（幂等…）"）。STATIC_REVIEW 行号级论证。
- **位置**：`AdSkipService.bumpScanChain:2218`（新重载参数）+ 调用点 1871（传 true）/ 2537、4189（保持 false）。
- **方案**：`bumpScanChain(pkg)` → 新增 `bumpScanChain(pkg, boolean skipFirstRung)`；窗口切换路径传 `true` 跳过 at==0 档（内联首扫已覆盖——同一调用栈同一棵树）；nudgeScan/返回键复验两处调用无内联先行，保持完整梯子。
- **风险**：极低。行为差异仅剩"内联扫与 0ms 档之间 ~5-16ms 内树变化"的窗口——60ms 档与 nudge 通道（80ms 节流）覆盖；按钮渲染以百 ms 计，16ms 窗口内的增量无安全意义。安全守护①②/决策梯/任务门全部不变。
- **回归**：全源 ECJ 编译（android-34 platform）+ 34 套件 6802 断言零退化 + 编译产物与基线 diff 核对（仅 bumpScanChain 调用面）。
- **预期收益**：每次应用启动/切窗少一次全量扫描（≈1400 节点访问 + 5 次原生搜索 + 1 次 collectRoots）；按典型用户日均 ~120 次切窗 ≈ 日省 ~120 次全量扫描的 CPU/binder/电量（估算口径，非实测——JVM 环境无真机计时，标 SIMULATED 推理）。
- **验证**：编译 + 既有全量回归 + 下轮真机日志对照（energyScans 计数应下降 ≈ 窗口切换次数）。

## P3-1 matchAll + walk 双 DFS 单遍合并【下轮专项 · 本轮不实施】

- **证据**：`tryScan:3388/3459` 零命中扫描两遍全树。
- **前置条件**（必须全满足才开工）：① 新建 JVM 计时基准（假树 150/400/600 节点 × 三种形态）固化优化前曲线；② 合并遍历的候选收集语义逐字段对照测试（matchAll 的 Hit 序 vs walk 的 ScanFind 字段）；③ 全量 6802+ 断言零退化。
- **否决理由（本轮）**：无基准无真机下重构核心决策遍历违反测试纪律。

## P3-2 videoPoll walkDue 针双重工作【下轮 · 需节拍测试先行】

- **证据**：`videoPoll:3643-3668`。
- **前置条件**：`VideoPollPacingTest`（miss-streak × walkDue × pollIdx 交互真值表）先行落地。
- **方案要点**：walkDue 针跳过自有 10 词探针（tryScan 内部已覆盖全部可达路径）+ 保持 miss-streak 语义（walkDue 针的 streak 处置需测试钉死）。

## P3-3 appendEvent 全文件读写【记录不实施】

低事件率 + 独立 IO 线程 + ACK 前不删语义依赖文件结构——收益/风险比不佳。

## P3-4 插屏 walk 300 预算【记录不实施】

专用轻量遍历仅省 ~30% 属性读取，需复制 walk 语义——维护成本 > 收益。

## P4-1 clear() 全清模式【记录不实施】

96+ 包触发条件罕见，行为方向安全（静默回退 elapsed 语义偏保守）。

---

## 实施批次与验收线

| 批次 | 内容 | 验收 |
|---|---|---|
| B1 | P0-1 文档修复 | secret-scan PASS + 6802 全绿 |
| B2 | P2-1 bumpScanChain 重载 | ECJ 全源编译 + 6802 全绿 + `git diff --check` + secret-scan |
| B3 | 审计/方案/迭代报告 + CHANGELOG | 文档入库，敏感扫描全绿 |

每批：测试 → `git diff --check` → 敏感检查 → commit → push 尝试 → SHA 核验（push 凭证不可用则如实标 UNKNOWN 并给出唯一必要用户操作）。

## 版本裁定

本轮 = 1 处生产代码优化（P2-1，服务侧调度层）+ 1 处仓库卫生修复 + 审计文档。无识别/安全门/状态机/契约变更，无 APK 行为语义变化（仅去掉一次幂等冗余扫描）。按项目版本规则（性能优化按改动规模）：**服务侧调度行为有变 → 定 PATCH：v8.0.1**（不占用 MINOR——无新功能面；RELEASE_CANDIDATE 状态延续，Draft v8.0.0 保持不动，待真机验证链完成后再议正式发布节奏）。

---

# v8.0.2 专项轮：P3-1 双 DFS 单遍化（本轮实施）

> 前置条件三件（上一轮裁定）全部先行落地：① `ScanBenchmark` JVM 基准（三形态×三规模）；
> ② `DualDfsParityTest` 21 场景逐字段对照（92 断言）；③ 全量 36 套件 6975 断言零退化。

## P3-1.1 两个 DFS 的真实语义（逐项对照表）

| 维度 | RuleEngine.matchAll（collect） | AdDecisionCore.walk（visit） | 融合约束 |
|---|---|---|---|
| 遍历顺序 | 前序 DFS（节点→子 L→R） | 前序 DFS（节点→子 L→R） | 同序共享一趟 |
| 节点过滤 | depth≤32 · budget=ctx.budgetNodes · out.size()<max | depth≤32 · budget=ctx.budgetNodes（无 max） | 同减点同序——同访问集 |
| 属性读取 | text/desc/visible/bounds/childCount（5） | text/desc/cls/visible/bounds/clickable/editable/scrollable/childCount（9） | 单读共享（9 读 + childCount 上提单读） |
| 规则匹配 | matchText 五层（app_exact/global_exact/pattern/contains/update_negative）+ matchVid（零文本叶子） | matchesSkip/isCloseText/isAdBadge/CTA/cdDigits/vid/corner 信号收集 | 骑士独立 try——互不拖累 |
| 候选收集顺序 | 先序收集，命中即 addCand | 先序收集，skipCands/corners 各自封顶（8/4） | 同先序——收集序天然一致 |
| 候选去重 | label+tapBounds 双等（addCand） | 无去重（按排序定优先） | addIfNew 复刻 addCand |
| 节点预算 | 每节点入即减 1 | 每节点入即减 1 | 单计数器同减点 |
| 提前终止 | 满 4 中：入口检查 + 子循环条件同停 | 无（全预算收信号——decide 依赖全集） | 骑士满 4 整树早停（find 必被丢弃） |
| 异常处理 | 每节点 try 吞——子树断 | 每节点 try 吞——子树断 | 骑士独立 try：只丢该节点骑士，不拖决策信号与子树 |
| 节点生命周期 | 每次 child() 新快照新 NodeAdapter | 每次 child() 新快照新 NodeAdapter | 单趟单快照——无缓存无跨趟复用 |
| SafetyGuard 依赖 | Hit.clickNode/gestureOnly（execute 边界自检用） | ScanFind 全信号（decide L1~L5 用） | 同快照产出——execute()/decide() 输入不变 |
| 小程序门 | **无**（命中后 execute() L0 拦） | **有**（入口返空） | 融合随 walk 位（已声明差③） |
| 后处理 | tierFull 降序+短文案排序 | skipCands selfClick/短文案/面积排序 + resolveCdDigits | 两排序逐字段复刻 |

**必须保持完全一致的行为**（对照测试钉死）：命中集+排序后顺序；决策信号全字段；决策结果（action/reason/bounds）；预算耗尽点；订阅合成顺序（规则在前订阅追加）；弹窗窗 rider 关路径。

## P3-1.2 三方案评估

| 维度 | A：双 DFS + 属性读缓存 | **B：单遍融合（本轮采用）** | C：单遍 + 分阶段决策 |
|---|---|---|---|
| 正确性 | 快照跨趟复用=「旧快照参与第二引擎」灰区（用户红线邻域） | **输出逐字段等价**（对照测试 92 断言）；三处已声明差全部钉死 | 改变 SafetyGuard 数据时序（证据先收、规则后判）——安全判断面变化 |
| 性能 | 只省 getter（本地调用，非 binder）——child() 双趟不减 | **child()/分配/属性读全面减半**（计数实证） | 同 B 量级 |
| 内存开销 | 需持有全树快照缓存（~600 节点/扫） | 无缓存（同旧路径） | 需中间证据结构 |
| 对象分配 | 双趟 NodeAdapter 不减 | **减半**（800→400 @400 节点） | 同 B |
| 代码复杂度 | 低（包装层） | 中（rider 参数化单 visit 体——新旧同一代码路径） | 高（决策流程重构） |
| 兼容性 | walk/matchAll 保留 | walk() 原签名原语义保留（4 处既有调用方零变化） | 全调用方重排 |
| 回归风险 | 中（跨趟快照一致性未定义） | **低（rider=null 时 byte-for-byte 旧 walk）** | 高（安全门时序变化） |

**裁定**：B。否决 A（收益不足 + 跨趟快照复用踩红线邻域）；否决 C（安全判断面变化——用户铁律「绝对不能直接合并」中唯一真正触碰安全时序的方案）。

## P3-1.3 防范清单落实（逐条对照）

- ✗ 缓存已失效 AccessibilityNodeInfo → 融合单趟单快照，零缓存零复用
- ✗ 跨窗口复用节点 → 每次 tryScan 全新 NodeAdapter 链（与旧路径同构）
- ✗ 改变候选优先级 → 两套排序比较器逐字段复刻（S14/S18 断言顺序一致）
- ✗ 改变规则匹配顺序 → 骑士门序 = collect 门序（text 门优先/else vid 叶门）
- ✗ 改变 SafetyGuard → execute() 边界自检/让路/预算梯子零改动
- ✗ 改变坐标安全边界 → tapBounds 产出路径不变（同 b 同 build）
- ✗ 丢失 DecisionEvidence → execute() 输入（Decision.node/bounds/label）不变
- ✗ 旧快照参与新页面点击 → 出手前 refreshLive() 复核不变（v6.0.0 防线）
- ✗ 扫描结果跨 generation → scanGen 代际护栏不动（本改动在单 tryScan 内部）

## P3-1.4 实施与验收

- 生产代码：`AdDecisionCore.walkFused/FusedScan/RuleRiderState`（新增）+ `visit(..., rider)` 参数化（rider=null 旧路径）+ `AdSkipService.tryScan` 接线（1 处）。
- 测试：DualDfsParityTest 21 场景 92 断言 + ScanBenchmark 63 断言；全量 36 套件 6975 断言 0 失败（v8.0.1 为 6820）。
- 构建：`./build.sh 80200 8.0.2` → APK v8.0.2；DEX 含 walkFused（v8.0.1 DEX 零命中对照）；证书 SHA-256 与历史一致。
- 闸门：secret-scan / app.js 符号闸 / version-sync / git diff --check 全绿。
- 诚实口径：JVM 计数=SIMULATED（child 调用精确减半为确定性证据）；JVM 计时仅供参考；真机性能/电量 NOT_RUN。
