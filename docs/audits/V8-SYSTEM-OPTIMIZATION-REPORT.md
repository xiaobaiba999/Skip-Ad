# Skip-Ad v8.0.1 系统级优化交付报告（V8-SYSTEM-OPTIMIZATION-REPORT）

> 轮次：v8.0.1 审计轮 · 2026-11-27 · 基线：main `9c59f2549be561cb187d3bfff609bdf3e3c84c65`（v8.0.0 RC）
> 配套文档：`V8-SYSTEM-AUDIT.md`（审计全文）· `V8-OPTIMIZATION-PLAN.md`（分级方案）

---

## 1. 本轮真实完成内容

### 1.1 审计了哪些模块（全部源码通读，行号级）

AdSkipService（5932 行：事件入口/前置过滤链/梯子调度/nudge/tryScan 决策梯/execute 出手链/任务状态机接线/学习链/复验链/videoPoll/插屏/信息流/档案/持久化）、AdTaskCore、AdDecisionCore（visit/decide/守护走查）、RuleEngine（matchAll/matchText/rowsFor）、RuleRouter、Subscription（预过滤+dfsMulti）、SubscriptionManager、VideoSplashGuard、FeedAdGuard、InterstitialGuard、MiniProgramGuard、ComplianceGuard、HumanTap/TapPoint、DecisionEvidence、LogPrivacyFilter/LogEvents/LogUploadPolicy/LogBatchStore/LogUploadDispatcher、Net、KeepAliveReceiver、NodeAdapter 适配层。

### 1.2 找到的真实问题

| 编号 | 问题 | 严重度 | 处置 |
|---|---|---|---|
| P0-1 | **secret-scan 闸门在 HEAD 全断**：`reports/iteration-v8.0.0.md:43` 文档字面含 PEM 头形态命中扫描正则，`run-tests.sh`/`build.sh` 全部 exit 1（接管首跑即被阻断实锤） | 仓库卫生·阻断级 | **已修复** |
| P2-1 | **窗口切换 0ms 双重全量扫描**：bumpScanChain 梯子 0ms 档 + 内联首扫对同一棵树全量扫两遍（至多 ~1500 节点访问 ×2/切窗） | 性能 | **已修复** |
| P3-1 | matchAll + walk 双 DFS 同树串行（零命中扫描遍历 ~2×） | 性能 | 入档，下轮专项（前置：JVM 计时基准 + 语义对照测试） |
| P3-2 | videoPoll walkDue 针双重工作（全量扫后同针再 collectRoots+10 词探针；命中再触发第二次全量扫） | 性能 | 入档，**S9 测试已补作前置钉子**，下轮实施 |
| P3-3 | appendEvent 每事件全文件读+写（O(n) IO） | 资源 | 入档不改（低事件率+独立 IO 线程+文件语义承载 ACK 前不删/恢复） |
| P3-4 | 插屏扫描为 3 信号跑整树 walk（预算 300） | 性能 | 入档不改（专用遍历仅省 ~30% 属性读取，维护成本>收益） |
| P4-1 | 防膨胀 clear() 全清模式（8 处，>96 包清掉前台包状态） | 健壮性 | 入档不改（触发罕见，行为方向安全） |

**审计负结果（同样重要）**：安全面/正确性面零问题——九态状态机真值表完备、taskId+generation 全回调贯穿、重复点击断源五道防线在岗、ACK 前不删日志、未授权零磁盘写、上传与无障碍链结构性隔离。**本轮零识别/安全门/状态机/契约改动。**

### 1.3 被否决的优化（理由全记录在审计文档第 3 节）

合并守护走查①②（BFS/DFS 顺序不同——改变安全闸预算截断语义）；缓存 updateDialog 每窗结果（弹窗可中途出现=MISS 回归）；matchSources 降频（vid 键晚出现依赖每扫咨询；无覆盖包已被预过滤挡住零树遍历）；NodeAdapter 跨遍历 memo（违反不缓存失效节点红线）；引入 WorkManager/对象池/响应式库（APK 零依赖红线）。

### 1.4 修改的文件与理由

| 文件 | 改动 | 理由 |
|---|---|---|
| `AdSkipService.java` | bumpScanChain 重载（+14 行含注释）+ 窗口切换调用点传 true | P2-1：消除每切窗一次的全量双扫冗余 |
| `VideoSplashTest.java` | S9 段 +18 断言（75→93） | 补 v7.9.5 自适应节拍纯函数面零覆盖；P3-2 前置钉子 |
| `reports/iteration-v8.0.0.md` | 1 行措辞改写 | P0-1：恢复 secret-scan 闸门可用 |
| `src/lib/version.ts` | 8.0.1 / 80100 + VERSION_HISTORY | 生产调度行为有变（扫描次数变化）→ PATCH 版本号如实反映 |
| `docs/CHANGELOG.md` | v8.0.1 条目 | 项目归档惯例 |
| `docs/audits/`（新增三件套） | 审计/方案/本报告 | 本轮任务交付物 |
| `android-app/dist/adshield-v8.0.1.apk(+idsig)` | 新 APK | 真实构建产物 |

### 1.5 保持不变的模块

AdTaskCore、AdDecisionCore（visit/decide）、RuleEngine 匹配层、SafetyGuard 体系（ComplianceGuard/MiniProgramGuard/InterstitialGuard）、HumanTap/TapPoint、日志五模块、SubscriptionManager、Net——全部零改动（审计确认稳定）。

---

## 2. 性能与质量对照

| 指标 | 优化前 | 优化后 | 测试方式 | 证据等级 |
|---|---|---|---|---|
| 窗口切换 0ms 扫描次数 | 2（内联 + 梯子 0ms 档） | 1（内联） | 源码行号级论证（:1871/:1880/:2279）+ 同参幂等性证明 | STATIC_REVIEW |
| 每切窗全量树访问量 | ~3000 节点访问（两遍） | ~1500（一遍） | 同上（守护①②+反馈+探针+3×DFS+updateDialog 累计） | STATIC_REVIEW + SIMULATED |
| 日扫描次数（典型 ~120 切窗） | ~120 次冗余全量扫描 | 0 | 推理估算（无真机计时——不冒充实测） | SIMULATED |
| 单次页面扫描耗时 | UNKNOWN | UNKNOWN（无真机/无 JVM 计时基准——P3-1 前置项） | — | NOT_RUN |
| 重复点击尝试 | 0（五道防线审计确认） | 0（6820 断言含 AdTaskTiming/Bug001/AggressiveMode 全绿） | JVM 全量回归 | AUTOMATED |
| 日志队列长度 | 有界（400 缓冲/20 批次/逐出策略） | 不变（零改动） | LogBatchStoreTest 47 断言 | AUTOMATED |
| 上传线程阻塞无障碍链 | 否（结构性） | 不变（零改动） | 审计 + 既有断言 | STATIC_REVIEW |
| 视频轮询节拍边界 | 无测试覆盖 | 18 断言钉死三档边界 | VideoSplashTest S9 | AUTOMATED |
| **真机能耗/CPU 实测** | **UNKNOWN** | **UNKNOWN**（本沙箱无设备——真机对照方法已写入：energyScans 计数应下降 ≈ 切窗次数） | — | NOT_RUN |

---

## 3. 测试结果（全部本轮实跑）

| 项 | 结果 |
|---|---|
| JVM 测试 | **34 套件 · 6820 断言 · 0 失败**（基线 6802 + S9 新增 18；既有 33 套件零回退） |
| 全源 ECJ 编译（android-34） | 与基线零差异（仅 5 个已知 R.* 构建期生成类错误——aapt2 生成，与 v8.0.0 交接口径一致） |
| APK 构建 | **成功**：`./build.sh 80100 8.0.1` → `adshield-v8.0.1.apk` · 878134B · SHA-256 `ce87b2999db6689f2122c6300d2561500d1c6f9c95b55ba93e42c7673a8ddd3a` |
| APK 静态检查 | 包名 com.zai.adshield ✓ · versionCode 80100>80000（防降级在岗）✓ · minSdk 24/targetSdk 34 ✓ · 签名证书 SHA-256 `05ada6e3…` 与全部历史版本一致（覆盖安装兼容）✓ · ZIP 22 项 ✓ · DEX 五 Log 模块+`skipFirstRung` 在场 ✓ · 生产端点在场 ✓ · dex+app.js 敏感扫描零命中 ✓ |
| secret-scan | PASS（修复 P0-1 后；本轮新文档曾三处复刻同一字面形态——已同步修正，佐证系统性） |
| check-version-sync | PASS（8.0.1 / 80100 双端统一） |
| check-appjs 符号闸 | PASS |
| `git diff --check` | CLEAN |
| Worker 测试（bun test） | **UNKNOWN**（Worker 仓私有不可达——不据旧记录虚报） |

---

## 4. GitHub 交付

| 项 | 状态 |
|---|---|
| 仓库 | `https://github.com/xiaobaiba999/Skip-Ad`（主仓·公开） |
| 分支 | main |
| 本地 commit | `9f3478ceee47f72c83f4a229f53e6c8732109384`（v8.0.1 审计轮全量变更） |
| push 状态 | **BLOCKED**——本沙箱无 GitHub 凭证（`git push` → `could not read Username`；环境无 token/无 git-credentials/无 gh 配置，实测确认） |
| 远端 main SHA | `9c59f2549be561cb187d3bfff609bdf3e3c84c65`（推送前后核验一致——未变） |
| local == remote | **否**（本地领先 1 提交，待推送） |

**唯一必要的用户操作（二选一，均不经过聊天传输凭据）**：
1. 在本沙箱 `/home/z/skip-ad-repo/local-gh-token` 放入 repo scope PAT（`.gitignore` 的 `local-*` 规则已验证保护该文件绝不入库——`git check-ignore` 实测），或设置环境变量 `SKIPAD_GH_TOKEN`；随后一轮我可完成 push + 远端 SHA 核验。
2. 或告知可用的安全注入方式，按项目 J 节纪律执行。

**绝对不做**：要求用户把 Token 发到聊天里 / 代创建凭据 / force push。

---

## 5. 未完成工作（明确清单）

| 项 | 状态 | 原因 |
|---|---|---|
| 真机安装 v8.0.1/v8.0.0 | NOT_RUN | 本沙箱无设备 |
| 真实广告场景回归（L3 批次 1 · 50 款） | PENDING_REAL_DEVICE | 同上 |
| 200+ 应用 L3 验证（10→20→50→100→200+） | 未推进（L1/L2 由 34 套件承载全绿） | 同上 |
| Android → Worker → GitHub 真实闭环 | NOT_RUN | 无设备 + Worker 仓不可达 |
| Worker E2E（e2e.sh/ps1） | UNKNOWN（Worker 仓不可达，脚本不在主仓） | 无认证 |
| Doze / 断网恢复 / 进程被杀 / 重启恢复 | NOT_RUN | 无设备 |
| 正式 Release Publish（v8.0.0 Draft #453676040） | 保持 Draft 不动 | 真机验证链未完成（发布前置七条件一条未满足） |
| GitHub Actions workflow 激活 | 未激活（沿既有状态——需 workflow scope token 或网页创建） | 凭证 |
| P3-1 双 DFS 合并 | 下轮专项（前置：JVM 计时基准 + 候选收集语义对照测试） | 测试纪律 |
| P3-2 videoPoll 重构 | 下轮实施（S9 前置钉子已落地） | 测试纪律 |
| push 到远端 | BLOCKED | 无凭证（见第 4 节） |

---

## 6. 版本裁定说明

v8.0.1（PATCH）：生产调度行为有真实变化（切窗扫描次数 2→1），无新功能面、无契约变化、无安全门变化——PATCH 符合项目版本规则（性能小优化按改动规模定 PATCH）。RELEASE_CANDIDATE 口径延续；v8.0.0 Draft Release 不动。
