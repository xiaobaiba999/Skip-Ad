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

---

# v8.0.2 轮追加：双 DFS 单遍化专项交付（2026-11-28）

> 轮次：v8.0.2 专项轮 · 基线：本地 main `c137a07`（v8.0.1 本地提交，远端仍为 `9c59f25`）
> 本轮指令：完成 v8.0.1 Git 交付（凭证受限如实处理）+ v8.0.1 APK 产物核验 + 双 DFS 专项（分析→评估→对照测试→实施→基准）+ 其余跨模块项逐项判断。

## A. 第一阶段：Git 交付核验（凭证受限，通道已备好）

| 项 | 结果 | 等级 |
|---|---|---|
| 本地工作区 | 干净，main 领先远端 2 提交（`9f3478c` → `c137a07`），顺序与内容核对无误 | 实测 |
| 远端 main SHA | `9c59f25…`（github.com git 协议超时 → 改用 api.github.com REST 确认，与预期一致——本地两提交可 fast-forward） | 实测（API 通道） |
| 两提交敏感信息扫描 | 通过（diff 全量正则扫描：无令牌/私钥/密码形态；唯一命中为删除行中的公开 workers.dev 端点 URL 与文档中列举的扫描模式名——均非凭据）；无 .keystore/.jks/.pem 类文件 | AUTOMATED |
| gh CLI 浏览器 OAuth | **不可行**：gh 未安装且不可安装（github.com 下载通道超时）；其 OAuth 设备流端点（github.com/login/device）在沙箱网络不可达 | 实测 |
| 系统凭据管理 | 本机无任何存储凭据（.git-credentials/.netrc/gh hosts/credential.helper 均无） | 实测 |
| **SSH Deploy Key 通道（已就绪待用户）** | 沙箱生成 ed25519 密钥对（私钥 600 权限驻留 ~/.ssh，永不打印永不外发）；git→ssh2 包装器经 ssh.github.com:443 全链路验证到认证层（主机密钥指纹固定 GitHub 官方三枚）；**公钥已交用户**（添加为仓库 Deploy Key 即可推送） | AUTOMATED（传输层） |
| push 状态 | **BLOCKED_PENDING_USER_DEPLOY_KEY**——按指令停止无效认证重试，本地提交完整保留 | — |

**唯一用户操作**：GitHub → xiaobaiba999/Skip-Ad → Settings → Deploy keys → Add deploy key（勾选 Allow write access）→ 粘贴本轮给出的公钥。添加后任一轮次执行推送并核验 `ls-remote`。

## B. 第二阶段：v8.0.1 APK 产物核验（全部通过）

| 核验项 | 结果 | 等级 |
|---|---|---|
| v8.0.1 SHA-256 | `ce87b2999db6689f2122c6300d2561500d1c6f9c95b55ba93e42c7673a8ddd3a`（≠ v8.0.0 的 `a6782b06…`——**非同一产物**；同尺寸 878134B 为等长改动巧合：VC 80000→80100 等位数字符串 + 小幅代码增量） | AUTOMATED |
| 字节级比较 | `cmp` 首差异在字节 15——内容确异；classes.dex 319348→319412（+64B） | AUTOMATED |
| versionCode/versionName | **80100 / 8.0.1**（aapt dump badging；单调递增守卫在岗） | AUTOMATED |
| DEX 含本轮修改 | `bumpScanChain(Ljava/lang/String;)V` + 新重载 `(Ljava/lang/String;Z)V` 双在场（v8.0.0 仅单参版） | AUTOMATED |
| 源码一致性（决定性） | **当前源码重建（80101/8.0.1）产出 classes.dex 与产物 DEX 字节级一致**（SHA-256 `19c60347…da68a` 双双吻合；v8.0.0 DEX `79eec1ac…` 不同） | AUTOMATED |
| 签名 | apksigner verify：v2+v3 Verifies，1 signer | AUTOMATED |
| 证书一致性 | SHA-256 `05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79` 与 v8.0.0 完全一致（覆盖安装兼容） | AUTOMATED |

## C. 第三阶段：双 DFS 专项（本轮主体）

语义对照表 / 三方案评估 / 防范清单落实见 `V8-OPTIMIZATION-PLAN.md` v8.0.2 节。要点：

1. **方案 B（单遍融合）落地**：`walkFused` 一次遍历同时收集规则命中（骑士）与决策信号——骑士门序/去重/tierFull 排序/满 4 早停与 matchAll 逐字段等价；决策信号与 walk 同一 visit 体（rider=null 时 byte-for-byte 旧路径）。方案 A（属性缓存）踩「跨趟快照复用」红线邻域且不省 binder；方案 C（分阶段决策）改变 SafetyGuard 数据时序——均否决。
2. **三处已声明语义差**（S11/S12/S17 钉死）：树中途变化晚到候选顺延一档（refreshLive 复核不变，零新增误触面）；瞬时 binder 异常单次即失（确定性异常等价）；小程序容器骑士入口即抑制（出手结果同为零点击）。
3. **对照测试**：DualDfsParityTest 21 场景 92 断言——命中集+顺序/决策信号全字段/决策结果/预算耗尽点逐项一致；既有防线（菜单行保护/容器门/订阅合成序）在测试中实锤在岗。
4. **基准证据（SIMULATED）**：ScanBenchmark 三形态×三规模——child 调用（≈生产 getChild binder IPC）**精确减半**（298→149/798→399/1198→599）、属性读总次降 36~40%、NodeAdapter 分配减半；JVM 计时 9 例新路径全部不慢于旧路径（仅供参考——无 binder 成本环境，真机口径 NOT_RUN）。

## D. 第五阶段：其余跨模块项逐项判断（全部维持不改动）

| 项 | 判定 | 理由 |
|---|---|---|
| appendEvent 全文件读写 | 不改 | 低事件率+独立 IO 线程；ACK 前不删/崩溃恢复语义依赖文件结构 |
| LogBatchStore 批次封装成本 | 不改 | 封批频率受 48 上限钳制；成本面与 P3-3 同口径 |
| Dispatcher 并发与 CAS 门 | 不改 | 单循环线程零阻塞已实锤；无竞争面可优化 |
| videoPoll walkDue 探针重复 | 不改（继续搁置） | S9 纯函数真值表已钉；但服务侧接线与 miss-streak 降档耦合（跳探针→streak 恒+1→降档提前→文本键发现延迟 1.5s→3s）——无真机数据不动 |
| nativeTextProbe×视频探针词族 | 不改 | 与上项同一针同一面 |
| maybeInterstitialScan 整树扫描 | 不改 | 专用轻量遍历仅省 ~30% 属性读取且需复制 walk 语义 |
| 页面级状态缓存失效 | 不改 | 96+ 包罕见路径；行为方向安全（P4-1 口径） |
| 高频窗口事件节流 | 已达标 | nudge 80ms/弹窗 240ms/feed 1.2s/插屏 1.5s + v8.0.1 已消 0ms 双扫 |

## E. 本轮测试与构建汇总

- **36 套件 6975 断言 0 失败**（v8.0.1 为 6820；+155 = DualDfsParityTest 92 + ScanBenchmark 63）。
- APK v8.0.2：`./build.sh 80200 8.0.2` → `android-app/dist/adshield-v8.0.2.apk`；DEX 含 `walkFused`（v8.0.1 DEX 零命中对照）；证书与历史一致；防降级守卫 80200 > 80100 在岗。
- 闸门全绿：secret-scan / app.js 符号闸 / version-sync（8.0.2/80200）/ `git diff --check` / 全源 ECJ 编译（R.* 基线错误数不变）。
- 版本裁定：**v8.0.2（PATCH）**——服务侧扫描遍历结构优化、输出等价、无新功能面无契约变化。

## F. 证据分级汇总（本轮）

| 项 | 等级 |
|---|---|
| 双 DFS 语义分析/方案评估 | STATIC_REVIEW |
| 对照测试 92 断言（21 场景逐字段等价 + 3 处已声明差钉死） | AUTOMATED |
| 基准计数（child/属性读/分配减半） | AUTOMATED（确定性计数） |
| 基准计时（µs 级） | SIMULATED（JVM 无 binder，仅供参考） |
| 真机性能/电量/安装回归 | NOT_RUN（无设备） |
| v8.0.1/v8.0.2 APK 静态核验 | AUTOMATED |
| Worker 仓契约 | UNKNOWN（私有不可达） |
| push | BLOCKED_PENDING_USER_DEPLOY_KEY（SSH 通道已验证到认证层） |
