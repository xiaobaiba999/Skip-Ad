# Skip-Ad 语言策略与架构决策报告（v5.6.0 定版）

> 回答三个问题：**为什么同类产品都用 Kotlin？我们为什么有 7 种语言？哪种组合最优？**
> 结论先说：**引擎保持 Java（数据证明重写零收益、高代价）；分层多语言是职责分离而非混乱——乱的是仓库展示层；本报告全部结论由可复现基准测试支撑（4 语言 × 9 轮 × 逐位等价校验和）。**

---

## 一、为什么同类产品都用 Kotlin

| 产品 | 语言构成（GitHub 实测） | APK 体积 | 状态 |
| --- | --- | --- | --- |
| **GKD**（gkd-kit/gkd） | **Kotlin 4.50MB (93%)** + TypeScript 0.33MB + Java 0.02MB | ~5.4MB | 开源 |
| **GKD 订阅仓库**（gkd-kit/subscription） | **TypeScript 100%**（规则 DSL） | — | 开源 |
| 李跳跳 | 闭源（无障碍方案同源） | ~数 MB | 停更风险 |
| 一指禅 | 闭源（长沙欧捷尔公司产品） | ~数 MB | 商业 |
| **Skip-Ad（本项目）** | Java 引擎 + TS 规则台 + JS 壳 UI | **0.29MB** | 开源 |

Kotlin 成为该赛道默认选择的五个真实原因：

1. **官方重力**：2017 年起 Google 官宣 Kotlin 为 Android 首选语言，新 API、KTX 扩展、官方示例全面 Kotlin 先行——新项目「开箱即 Kotlin」。
2. **无障碍 API 是 Java 系一等公民**：`AccessibilityService` 本身就是 Java API；Kotlin 与 Java 100% 互操作，调用零成本——「能用」不构成选择差异。
3. **协程契合事件流**：`TYPE_WINDOW_CONTENT_CHANGED` 高频事件流 + 结构化并发取消（`scope.cancel()`）写起来顺手。
4. **生态惯性**：Compose UI + Android Studio 模板默认 Kotlin，社区规则贡献者也更熟悉。
5. **团队 DX**：空安全 / 数据类 / DSL 语法糖 → 人的迭代速度快（是开发体验收益，不是运行性能收益）。

**但注意 GKD 的第二层选择**：它的订阅规则 DSL 用的是 **TypeScript**（gkd-kit/subscription 仓库 100% TS）——规则生态交给 JS/TS 工程师贡献。这与本项目的分层（Java 引擎 + TypeScript 规则台）**同构**。

---

## 二、我们为什么有 7 种语言——职责分离，不是混乱

GitHub 语言栏显示 7 种语言（Java 44.8% / TypeScript 41.7% / Python 5.5% / JavaScript 5% / Shell 1.9% / CSS 0.7% / HTML 0.4%）。逐层拆解：

| 层 | 语言 | 为什么 | 发布物 |
| --- | --- | --- | --- |
| **Android 引擎**（无障碍服务/规则匹配/决策/保活） | Java（--release 8） | 无障碍 API 一等公民；**零运行时依赖**（无 Kotlin stdlib）→ APK 0.29MB；纯 javac/ECJ+d8 工具链可单测（475 断言） | `android-app/` → APK |
| **App 内 UI** | HTML/CSS/JS（WebView） | 壳 UI 与引擎解耦，改 UI 不动引擎 | APK 内 `assets/www/` |
| **Web 规则台**（演练场/情报站/学习中心 PWA） | TypeScript + Next.js 16 + Prisma | 规则仿真、语料、情报、订阅工具链——**与 GKD 订阅仓库同构**（规则生态层） | `src/` |
| **CI/发布/图标自动化** | Shell + Python + mjs | 一次性运维脚本，不进任何发布物 | `scripts/` |

**真正的问题不是「语言多」，而是两件事**：
1. 仓库把一次性工具（脚本/下载/测试报告）和产品本体（引擎+规则台）在语言统计栏**混为一谈**；
2. README 把 30 个版本号的超长要点**全部堆在首页**。

→ 治理方案见第五节（.gitattributes 语言统计净化 + README 重构），**不是**砍语言。

---

## 三、基准测试：哪种选择最优（数据说话）

### 3.1 测试设计（可复现：`scripts/bench-language/`）

- **任务**：引擎热路径忠实复刻——48 节点无障碍树 × 28 行分应用规则 × 19 条精确文案 × 2 条倒计时正则 × vid 通道 × 形状门（菜单列检测/宽度门），完整五层文案管线 + GKD 选择器编译器。
- **语言**：Java（ECJ → JVM8 字节码，与 APK 构建同工具链）、Kotlin 2.0.21（K2，惯用风格：data class/nullable/集合 API）、JavaScript（Node 24 V8 = WebView JS 引擎代理）、Python 3.12（CPython）。
- **等价性证明**：四语言实现共享 xorshift31 PRNG 与同一算法，**校验和逐位一致**（sel_sink=731 · hits=20000 · sink=485562）——测的是语言，不是算法差异。
- **协议**：每语言 9 轮；吞吐/延迟取 min-of-9（噪声下界），boot/RSS 取中位数；MATCH 分三段——冷启动首 1000 次（含 JIT 惩罚）→ 预热 2000 → 稳态 20000。

### 3.2 结果（2 核 Debian · OpenJDK 21.0.13 · Node 24.22.3 · CPython 3.12.16）

| 语言 | 冷启动 boot | 选择器编译 | 冷首1000次 | **稳态/树（引擎现状写法）** | **稳态/树（优化写法）** | 常驻内存 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| **Java 8** | 55.4 ms | 214 μs/套 | 45.8 μs | 4747.8 ns | **968.2 ns** | 90 MB |
| **Kotlin 2.0.21** | **77.9 ms** | 289 μs/套 | 46.0 μs | 5306.4 ns | **903.7 ns** | 95 MB |
| JS (V8) | 31.5 ms | 61 μs/套 | 2.9 μs | 1107.8 ns | 484.4 ns | 59 MB |
| Python | 30.2 ms | 119 μs/套 | 6.9 μs | 9494.1 ns | 5575.4 ns | 13 MB |

### 3.3 数据解读

1. **Java ≈ Kotlin 稳态持平**（968 vs 904ns，±7% 在 JIT 竞争区间内）：两者编译到**同一字节码模型**、同一虚拟机——这是结构性结论，不是这次跑出来的偶然。Kotlin 的价值在 DX（人的效率），**不在**运行速度。
2. **Kotlin 的实测代价**：冷启动 **+40.7%**（55.4→77.9ms）；常驻内存 +4.6MB；选择器编译 +35%；以及不在本表的 **APK 体积**——kotlin-stdlib 实测 1.75MB（jar），d8 全量 dex 进 APK 后 292KB → **~2MB（≈7×）**。对「保活服务每分钟心跳」的低功耗产品，冷启动+内存+体积全是负资产。
3. **JS (V8) 稳态最快（484ns）**——但**架构不可行**：无障碍事件无法抵达 WebView（`AccessibilityService` 是 JVM 层 API）；「每个事件拉起 WebView 跑 JS」= 能耗灾难。JS 的正确位置是壳 UI 与规则台（现状如此）。*注：Node V8 是 Android WebView 引擎的性能代理，非真机数值。*
4. **Python 慢 6~20×**（9.5μs/树）且 Android 无运行时路径——它的正确位置是 CI 脚本（现状如此）。
5. **意外收获**：引擎现状写法（`String.matches` 每次重编译正则 + `row[j].toLowerCase()` 每次分配）稳态 4748ns → 预编译+哈希/预计算小写表 968ns = **4.9× 热路径提升空间，留在 Java 里就能拿到**。

### 3.4 决策（数据驱动）

- **不重写**。Java → Kotlin 全量重写 = 11253 行引擎 + 475 断言重验，换来：稳态 0 收益、冷启动 +40%、APK 7×、内存 +5MB。否决。
- **落地优化**：v5.6.0 已把 §3.3-5 的优化写进 `RuleEngine.java`（行为等价、475 断言全绿）。
- **未来路径**：若单树 >200 节点成为常态（重度 H5 应用），正确方向是**下沉 C++/NDK**（GKD 早期方案的教训与启示），而非 Kotlin。

---

## 四、最优语言组合（定版）

| 组件 | 语言 | 规则 |
| --- | --- | --- |
| Android 引擎/无障碍/决策 | **Java（--release 8）** | 零运行时依赖、APK 最小、JVM 可单测；**新引擎代码一律 Java，禁止混入 Kotlin**（防双运行时） |
| App 壳 UI | **HTML/CSS/JS（WebView）** | UI 迭代不碰引擎 |
| Web 规则台/订阅工具链 | **TypeScript** | 与 GKD 订阅生态同构 |
| CI/发布/图标脚本 | **Shell/Python/mjs** | 一次性运维，不进发布物 |
| 禁区 | Python/JS 进引擎；Kotlin 进 APK | §3.3 数据否决 |

一句话：**「Java 管设备，TypeScript 管规则，脚本管流水线」**——三岗三语，GitHub 语言栏只该展示前两者（第五节治理）。

---

## 五、仓库展示治理（本页的另一目的）

1. **`.gitattributes` linguist 净化**：脚本/下载/测试报告/构建产物标记 vendored/generated → 语言栏从 7 种收敛为产品语言（Java+TypeScript+JS+CSS+HTML）。
2. **删除 `android/` 死代码**：com.skipad 实验性重复实现（12 文件），从未进任何发布物——与 android-app 并存造成「两套引擎」的错觉。
3. **README 重构**：版本要点归档到 `docs/CHANGELOG.md`，首页只留：是什么/怎么用/架构图/语言策略/基准数据/下载。
4. **测试跑器修复**：`run-tests.sh` 引用的 2 个不存在套件（RuleGovernorTest/ComplianceGuardTest）已从清单移除。

---

## 六、复现

```bash
# 基准（四语言，9 轮，输出 JSON + 汇总表）
bash scripts/bench-language/run_bench.sh

# 引擎回归（475 断言）
bash android-app/run-tests.sh

# APK 构建（292KB）
cd android-app && ./build.sh 50600 5.6.0 dist/adshield-v5.6.0.apk
```

原始数据：`scripts/bench-language/results/bench_results.json`（含每轮原始 JSONL）。
