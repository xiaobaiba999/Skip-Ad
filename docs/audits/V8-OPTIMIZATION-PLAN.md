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
