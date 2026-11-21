# Skip-Ad 项目交接文档（v8.0.0 · Android 主仓）

> 本文档面向"无聊天上下文的新接手模型"。目标：仅凭本仓库即可理解项目全貌、
> 已完成/未完成边界、下一步该做什么、哪些事绝对不能做。
> 姊妹文档（中转服务侧）：`xiaobaiba999/Skip-Ad-Log-Upload` 仓库根 `HANDOFF-V8.0-WORKER.md`。
> 一切以真实代码 / Git 历史 / `docs/CHANGELOG.md` 为准；本文与它们冲突时，以代码为准并回改本文。

---

## A. 项目概况

**Skip-Ad（开屏卫士 / adshield）**：Android 无障碍服务类"自动跳过开屏/插屏广告"应用。
监听无障碍事件 → 规则引擎识别广告窗 → SafetyGuard 安全终闸 → HumanTap 人手化触点
→ 派发点击 → 500ms 复验闭环。附带订阅规则市场（GKD JSON5 兼容）、学习治理、
崩溃捕获、Web 控制台与 GitHub Release 自更新。

- **GitHub**：`xiaobaiba999/Skip-Ad`（本仓库）；本地开发目录 `/home/z/skip-ad-repo`
- **⚠️ 所有者拼写陷阱（历史已两次踩坑，务必字节级核对）**：GitHub 账号真名是 `xiaobaiba999`（**b**——version.ts / Net.java / git remote / Worker wrangler REPO_OWNER 四处字节级验证一致）。史料与聊天记录中曾出现 `xiaobaika999`（k）与 `xiaobaiza999`（z）两种**不存在**的误拼——写入 API 路径即 404。凡引用 owner，以 `src/lib/version.ts` 的 `GITHUB_REPO` 为单一权威源，动手前 `grep -o` 复核字节。
- **当前正式版本**：v8.0.0（versionName `8.0.0` · versionCode `80000`，
  唯一权威源 `src/lib/version.ts`；`AndroidManifest.xml` 中的 6.2.0/60200 是**构建模板值**，
  由 `build.sh` 在构建时 sed 注入真实值——不要误读）
- **本文落笔时 HEAD**：`cd669aaca32af99bfe69a691dd2ee4dd71e6c51a`
  （v8.0.0 客户端上传链路提交；本文档自身提交后请以 `git log -1` 为准）
- **技术栈**：
  - Android 端：**纯手写 Java 8**（无 Gradle / 无 Kotlin / 无第三方依赖），
    `minSdk 24 · targetSdk 34`，WebView 宿主（`assets/www/` 原生 JS UI，零框架）
  - 构建链：`aapt2 + javac/ECJ + d8 + zipalign + apksigner`（见 `android-app/build.sh`）
  - Web 控制台：仓库根 `src/` 为 Next.js 控制台（规则沙箱/面板/发布 API），非 Android 应用本体
  - 中转服务：Cloudflare Worker + Durable Objects（另一仓库）
- **仓库结构**：
  - `android-app/` —— Android 应用本体（`src/com/zai/adshield/` Java 源码、`assets/www/` UI、
    `res/`、`tests/`（JVM 测试）、`build.sh`、`run-tests.sh`、`keystore/`、`dist/` 历史 APK）
  - `src/` —— Next.js Web 控制台（仅 Web，不进 APK）
  - `docs/` —— CHANGELOG / SECURITY / TEST-PLAN / 各版本 OPTIMIZATION 归档
  - `scripts/` —— 发布/校验/补丁脚本（含 `secret-scan.sh`、`check-appjs.sh`、`check-version-sync.sh`）
  - `dist/`、`download/`、`upload/` —— 历史 APK 归档（dist 最大 versionCode 79600 = v7.9.6）
  - `compatibility/`、`data/learning/`、`agent-ctx/` —— 测试矩阵与学习数据
- **当前开发阶段**：v8.0.0 代码已完成（客户端日志上传闭环）；**APK 已构建完成并通过全部静态检查（2026-11-26 构建轮）**；真机尚未回归、上传闭环真机端到端尚未验证（见 H 节）。
- **v8.0.0 APK**：`android-app/dist/adshield-v8.0.0.apk` · 878134 字节 · SHA-256 `a6782b0696ba447938371977da99802e9add670191b8aaa924c99acc9338235c` · 签名证书与 v7.9.6 完全一致（覆盖安装兼容）· RELEASE_CANDIDATE 口径。

---

## B. 版本历史（摘自 docs/CHANGELOG.md，以仓库为准）

| 版本 | 日期 | 主题（CHANGELOG 原题） |
|---|---|---|
| v7.9.1 | 2026-11-20 | 任务状态机轮：重复点击/误触/合法重试三难根治 + 订阅命中 0 根治 + 三 app 漏检修复 |
| v7.9.2 | 2026-11-21 | 200+ 应用生态兼容性基线：233 款应用目录 + L1 测试体系 + 引擎包名校准五项根治 |
| v7.9.3 | 2026-11-21 | L2 页面场景层：12 类 UI 独立样本 × 8 项场景行为 + 订阅七分类真实计数器断言 |
| v7.9.4 | 2026-11-21 | FalsePositive 场景层（20 类误触红线）+ AdTask 时序压力 + 安全部署链整改（实测揭出并根治一个真实误触缺陷） |
| v7.9.5 | 2026-11-21 | 真实世界证据层：点击决策证据（DecisionEvidence）+ 点击结果六态闭环 + WebView 综合证据路径 + 自适应轮询 |
| v7.9.6 | 2026-11-20/21 | L3 轮：Bug#001 终局三漏根治（冷却闸绕过→无限重试循环阻断 + MISS 终局记账）· 一个真实 Bug 一个回归测试 |
| v7.9.7 | 2026-11-21 | PAT 移除安全轮：APK 零 GitHub 凭据 + 自动日志上传授权（默认关）+ 手动日志本地导出 —— 上一阶段稳定版本 |
| **v8.0.0** | 2026-11-25 | **自动日志上传闭环轮：中转服务上线 + 客户端上传链路（授权默认关 · APK 零凭据 · batchId 幂等）** |

完整条目见 `docs/CHANGELOG.md`（v8.0.0 条目对五个模块、幂等机制、隐私四端同族修复、
测试口径有逐条详述——本文 C/D 节为它的结构化摘要 + 代码位置）。

---

## C. v8.0.0 已实现功能（五个 core 模块，全部真实存在于仓库）

代码位置：`android-app/src/com/zai/adshield/core/`（纯 Java，JVM 可测，无 Android 运行时依赖）。

| 模块 | 文件（行数） | 职责 |
|---|---|---|
| LogPrivacyFilter | `LogPrivacyFilter.java`（231） | 字段白名单/枚举校验/整数界/危险键**整批拒绝**/候选词族门/脱敏四正则。与服务端 `validate.ts` 同规约（`privacyFilterVersion` 双端同号递增）。身份证 18 位差一正则已修（四端同族：Net.maskSensitive / www maskText / 本模块 / 服务端 privacy.ts）。safetyReason 先脱敏后截断。 |
| LogEvents | `LogEvents.java`（242） | 八类事件工厂：`AD_TASK`（终局）/`SAFE_BLOCK`（终闸）/`CLICK`（派发）/`VERIFY`（复验）/`STATS`（快照）/`GKD`（拉取异常，errClass 仅类名）/`VIDEO_POLL`（探针命中）/`CRASH`（仅顶层栈帧类名，绝不带堆栈正文）。工厂输出保证过自家过滤器零剥离（测试钉死）。 |
| LogUploadPolicy | `LogUploadPolicy.java`（133） | 响应裁决**纯函数**：`ACK`（200 ACCEPTED / 200 ALREADY_ACCEPTED）/ `WAIT`（429 尊重 retryAfter 钳制 [30s,24h]，**不耗重试计数**）/ `RETRY`（TEMPORARY_ERROR / 网络异常 / 未知 code——退避 30s→60s→120s→240s→480s ×5，同 batchId）/ `FAIL` 终态（PRIVACY_REJECTED / INVALID_REQUEST / UNAUTHORIZED / IDEMPOTENCY_CONFLICT / PAYLOAD_TOO_LARGE / **MANUAL_RECONCILIATION_REQUIRED 立即终态绝不换 batchId 重传**）。另含请求体构造（schemaVersion 1 顶层九键 + UUID 防御门——明知 401 的请求绝不发出）。 |
| LogBatchStore | `LogBatchStore.java`（533） | 批次持久化：`PENDING → UPLOADING → ACKED / FAILED` 状态机；`installInstanceId` 首启 UUID v4（清数据/卸载自然重置）；400 条事件缓冲上限（容量裁剪**关键安全事件最后丢**）；单批 ≤48 事件（服务端契约上限，余量留缓冲分多批）；批次容量 20 最旧先逐出；UPLOADING 中断恢复；**ACK 前绝不删本地批次（红线）**；`setCollectEnabled` 采集门（授权 × 中转上线双条件——未授权设备零新增磁盘写入）。 |
| LogUploadDispatcher | `LogUploadDispatcher.java`（307） | 单后台守护线程（running CAS 门——绝不并发提交同批次）；非 2xx 响应体也解析（错误信封 code/retryAfter 全字段可用）；每轮先发 STATS 快照事件；退避表与裁决表接线。 |

**调用关系**：`AdSkipService.tapEvent()`（第 313 行，单线程 executor 非阻塞 submit——无障碍热路径零同步 IO）→ `LogEvents.*` 工厂造事件 → `LogBatchStore.appendEvent` 落盘/缓冲/封批（生成 UUID v4 batchId）→ `LogUploadDispatcher` 守护线程取 PENDING 批次 → `LogUploadPolicy` 构造请求体 → HTTPS POST `Net.LOG_RELAY_URL` → 响应经 `LogUploadPolicy` 裁决 → `LogBatchStore` 状态机推进（ACKED 后才允许清理）。

**服务侧埋点接线（AdSkipService.java 真实行号）**：
- 306–325：`logStore` 字段 + `tapEvent` + 懒初始化
- 712–733：`maybeAutoUploadLog`（KeepAliveReceiver 心跳调用点，v7.9.7 起为双门结构：授权门 × 中转门）+ `refreshCollectGate`（`setCollectEnabled` 接线）
- 3662 `videoPoll` · 4111 `safeBlock` · 4337/4738 `click`（派发失败/成功两路）· 4766/4788/4821/4881 `adTask`（三路终局 + 预算耗尽）· 4855 `verify`（CLOSED/NOT_CLOSED）
- CrashCapture.save 尽力而为 + readAndClear 幸存补点（CRASH 事件）

---

## D. Android 日志上传架构（v8.0.0 数据流）

```text
AdSkipService（无障碍服务 · 事件埋点薄接线）
      ↓ tapEvent（单线程 executor · 非阻塞 · 全链 try/catch）
DecisionEvidence（内存诊断证据，六态闭环）/ LogEvents（八类事件工厂）
      ↓ appendEvent（异步落盘）
LogBatchStore（批次持久化 · PENDING→UPLOADING→ACKED/FAILED · 400 条缓冲 · 48 分批）
      ↓ 封批生成 batchId（UUID v4）
LogPrivacyFilter（客户端第一道：白名单/枚举/危险键整批拒绝/脱敏）
      ↓
LogUploadDispatcher（单后台守护线程 · 退避 30s→480s×5）
      ↓ HTTPS POST（Net.LOG_RELAY_URL · 三超时 · 不在无障碍线程）
Cloudflare Worker（skip-ad-log-relay · Edge 第一道校验/限流）
      ↓
LogGate Durable Object（24h/3 滑窗 · batchId 幂等状态机 · 墓碑锚点）
      ↓（GlobalGate：IP/全局限流 · 24h Issue 预算 fail-closed）
GitHub Issues（Skip-Ad-Logs 私有仓库 · 每批一个 Issue）
```

关键行为（均可在代码与 CHANGELOG v8.0.0 条目中核实）：

- **事件从哪里产生**：AdSkipService 扫描/点击/复验/终局路径 + CrashCapture；全部一行式埋点。
- **哪些事件被记录**：八类（C 节）；FALSE_CLICK 在自动化环境恒 0（真机样本回传唯一来源）。
- **如何缓冲**：LogBatchStore 内存队列 + 磁盘批次文件；400 条上限，容量裁剪时关键安全事件最后丢。
- **何时封批**：UPLOADING 批次之外新事件达 48 上限 / 心跳周期（AUTO_LOG_MS 8h 节拍，最终以服务端 24h/3 为准）触发 dispatcher 轮询。
- **batchId 如何生成**：封批时 UUID v4。
- **网络超时如何处理 / 重试如何保证同一 batchId**：HTTP 超时按 TEMPORARY_ERROR 走 RETRY 退避（同 batchId）；"重试不换号"是**结构性保证**——dispatcher 只读已存批次文件，无任何重封包路径。目标场景：客户端发送成功但响应丢失 → 重试必然得到服务端 `ALREADY_ACCEPTED` 复用原 issueNumber，绝不二建 Issue。
- **ACK 前为什么不能删除数据**：服务端可能在响应丢失前已建 Issue；本地提前删除会失去幂等锚点与失败检视证据。LogBatchStore 状态机 PENDING/UPLOADING 在未 ACK 前一律保留。
- **失败队列如何保留**：FAILED 批次保留于本地（设置页可见"失败检视"计数）；"清理待上传"只删本地文件、绝不绕过服务端。
- **用户如何授权**：设置页独立开关（`KEY_AUTO_LOG_UPLOAD`，默认 false）；与无障碍授权严格分离。授权 = 允许采集 + 允许调度上传，但仍受服务端限额。
- **用户如何手动提交**：设置页"立即提交"按钮（触发 dispatcher 一次轮询；同一服务端 24h/3 限额绝不绕过）；另有手动本地导出通道（logs/ 目录 + 系统分享）并存。
- **用户如何查看上传状态**：设置页上传状态行——待传批次数 / 24h 已提交（服务端权威，本地仅展示参考）/ 失败检视数 / 最近提交时间 / 最近失败原因。
- **未授权状态下零新增日志持久化**：`LogBatchStore.setCollectEnabled(授权 × 中转上线)` 采集门——未同意上传的设备零新增磁盘写入（内存内 DecisionEvidence 诊断照旧）。

---

## E. 隐私与安全（红线）

- **客户端零凭据**：APK 内无 GitHub Token / GitHub App Private Key / Cloudflare OAuth / 任何服务器 Secret（v7.9.7 达成 PAT 移除，v8.0.0 不破；secret-scan 闸验证）。`Net.LOG_RELAY_URL` 只是公开服务端点。
- **不上传**：完整 Accessibility Tree（LogPrivacyFilter 字段白名单结构性排除）、截图（无位图/字节引用）、账号/手机号/支付/聊天内容（危险键整批拒绝 + 脱敏正则）。
- **双重防护**：客户端 PrivacyGuard（LogPrivacyFilter）+ Worker 服务端校验（validate.ts / privacy.ts 同规约）——任何一端收紧都安全。
- **上传低优先级**：上传失败**绝不影响广告跳过**——埋点异步落盘、上传独立线程、全链 try/catch 吞尽；无障碍热路径零新增同步 IO、零网络 IO。
- 白名单与字段约束以 `LogPrivacyFilter.java` + 服务端 `validate.ts` 为共同权威，双端 `privacyFilterVersion` 同号递增。
- 历史已知项：`docs/SECURITY.md` R1（旧 PAT 撤销待仓库所有者执行）/ R2（keystore 公开为知情接受现状，v8.0 起迁移计划）。

---

## F. 重要代码位置（真实路径）

| 文件 | 作用 |
|---|---|
| `android-app/src/com/zai/adshield/AdSkipService.java`（5932 行） | 无障碍主服务：扫描/识别/任务状态机/点击/复验/保活/埋点接线 |
| `android-app/src/com/zai/adshield/core/AdDecisionCore.java` `AdTaskCore.java` `RuleEngine.java` `RuleRouter.java` `RuleMerge.java` `SmartScanPlanner.java` | 决策核心 / 任务状态机 / 规则三件套 / 扫描计划 |
| `android-app/src/com/zai/adshield/core/DecisionEvidence.java`（306 行） | 点击决策证据 + 结果六态闭环（CLICK_DISPATCHED→VERIFIED_SUCCESS/RETRY/MISS/FALSE_CLICK/SAFE_BLOCK/UNKNOWN） |
| `android-app/src/com/zai/adshield/core/HumanTap.java` `TapPoint.java` | 人手化触点（防检测/防误触）与点击坐标 |
| `android-app/src/com/zai/adshield/core/SafetyGuard` 相关 | 安全终闸（在 AdDecisionCore/RuleEngine 体系中） |
| `android-app/src/com/zai/adshield/SubscriptionManager.java` | 订阅管理（GKD JSON5 拉取/合并/镜像） |
| `android-app/src/com/zai/adshield/CrashCapture.java` | 崩溃捕获（读后即删 + v8.0.0 CRASH 事件补点） |
| `android-app/src/com/zai/adshield/Net.java` | 网络层：检查更新 / 订阅拉取 / `LOG_RELAY_URL`（第 52–53 行）/ `maskSensitive` 脱敏 |
| `android-app/src/com/zai/adshield/MainActivity.java` | WebView 宿主 + JS 桥（授权/设置/导出/分享） |
| `android-app/assets/www/app.js` + `index.html` + `style.css` | 设置页 UI（原生 JS；上传状态行/立即提交/清理待上传/授权开关/本地导出） |
| `android-app/src/com/zai/adshield/core/LogPrivacyFilter.java` | v8.0.0 模块①：客户端隐私过滤（C 节） |
| `android-app/src/com/zai/adshield/core/LogEvents.java` | v8.0.0 模块②：事件工厂 |
| `android-app/src/com/zai/adshield/core/LogUploadPolicy.java` | v8.0.0 模块③：裁决纯函数 + 请求体构造 |
| `android-app/src/com/zai/adshield/core/LogBatchStore.java` | v8.0.0 模块④：批次持久化 |
| `android-app/src/com/zai/adshield/core/LogUploadDispatcher.java` | v8.0.0 模块⑤：上传守护线程 |
| `android-app/AndroidManifest.xml` | 清单（版本号是模板值，构建时注入；权限：INTERNET/INSTALL/NOTIFY/FOREGROUND/BOOT/电池白名单等） |
| `android-app/keystore/adshield.keystore` | 签名密钥（alias `adshield`；口令可经 KS_PASS/KEY_PASS 环境变量注入，未注入回退内置——见 docs/SECURITY.md R2 知情接受现状） |
| `android-app/build.sh` | APK 构建六步（见 A/G 节；用法 `./build.sh 80000 8.0.0`） |
| `android-app/run-tests.sh` | JVM 测试跑器（ECJ 编译 core + tests + SubscriptionManager/Net，逐套件运行） |
| `android-app/tests/` | 34 个 JVM 测试套件 + `TestHost` + `org/json` JVM shim（**绝不进 APK**，build.sh 只编译 src/） |
| `src/lib/version.ts` | 版本唯一权威（APP_VERSION 8.0.0 / VERSION_CODE 80000 + VERSION_HISTORY） |
| `docs/CHANGELOG.md` | 全版本归档（v8.0.0 条目最详尽） |
| `scripts/secret-scan.sh` / `scripts/check-appjs.sh` / `scripts/check-version-sync.sh` | 三道构建/测试前置闸 |
| `scripts/publish-v*.mjs|ts|py` | 历史各版本 GitHub Release 发布脚本（v8.0.0 沿用此模式，待写 publish-v800） |
| `docs/ci-workflows/*.yml` | 历史归档的 CI 样例（仓库当前**无** `.github/workflows/`——见 H/I 节） |

---

## G. 测试与证据

### 上一轮报告（来源：v8.0.0 CHANGELOG + 上一轮 GLM 报告，2026-11-25，非本轮重跑）

- 35 套件 · 6802 项 JVM 断言通过 · 0 失败（新增 3 套件 149 断言：LogPrivacyFilterTest 47 / LogUploadPolicyTest 55 / LogBatchStoreTest 47）
- 全源 ECJ 合编与 v7.9.7 基线零差异（5 个 R.* 基线错误不变——R 类由 aapt2 构建期生成）
- version-sync / secret-scan / app.js 符号闸 / git diff --check 全绿

### 本轮（2026-11-26 接管重跑，AUTOMATED 级）

- `./run-tests.sh` 重跑结果：**34 套件 · 6802 断言 · 0 失败**（实跑汇总行"总计通过 6802 · 总计失败 0"；套件数按本次实跑日志产物 34 计——CHANGELOG 记"35 套件 6802 断言"，断言数与实跑吻合，套件计数为口径差异（35 含 TestHost 口径），以实跑产物为准）
- `scripts/check-version-sync.sh` 重跑：**PASS**（Web 端 v8.0.0 / 80000 双端统一）
- `scripts/secret-scan.sh` 重跑：**PASS**（无令牌形态·无内嵌凭据·本机凭据未入库·keystore 面收敛）
- Worker 仓 `bun test` 重跑：**53 测试 0 失败 / 424 expect()**（8 文件，2.77s；详见 Worker 仓 HANDOFF-V8.0-WORKER.md E 节）
- 证据等级口径（贯穿本项目）：`STATIC_REVIEW`（代码审读）/ `AUTOMATED`（JVM/脚本自动测试）/ `REAL_API`（真实 API 调用验证）/ `REAL_DEVICE`（真机实测）/ `UNKNOWN`（未验证）。
- 本轮如实口径：JVM 测试 = AUTOMATED；Worker `/healthz` 用户真实浏览器验证 = REAL_API（用户侧）；**真机 = NOT_AVAILABLE（本沙箱无设备/模拟器）**；**APK 构建 = 已完成（2026-11-26 · 本地官方工具链 build-tools r34 · `./build.sh 80000 8.0.0` · 六项静态检查全过，详见 CHANGELOG v8.0.0 构建轮小节与 reports/iteration-v8.0.0.md）**。

---

## H. 当前尚未完成事项（截至 2026-11-26 构建轮后）

1. ~~APK v8.0.0 尚未构建~~ **已完成（2026-11-26）**：`./build.sh 80000 8.0.0` 官方工具链构建成功；静态检查全过（包名/版本/签名/ZIP/DEX/敏感扫描——详见 CHANGELOG「v8.0.0 构建与交接轮」）。**APK 安装测试仍未执行（无设备）**。
2. **APK 安装测试尚未完成**（真机/模拟器均未执行；本地已可复现构建：`/home/z/android-tools/bt/android-14` 已补齐官方 build-tools r34）
3. **Android 真机回归尚未完成**（L3 批次 1 · 50 款 App 仍 PENDING_REAL_DEVICE）
4. **Android → Worker → GitHub Issues 真实上传闭环尚未完成**（客户端与服务端代码均已就绪，缺真机端到端）
5. **Worker 的 E2E 尚未执行**（e2e.sh / e2e.ps1 已交付待执行；状态 `DEPLOYED_BUT_NOT_E2E_VERIFIED`，不得升格）
6. **Doze、断网恢复、进程被杀恢复、重启恢复等真机场景尚未完成**
7. **GitHub Release v8.0.0 正式发布尚未完成**——但**草稿已就绪**（2026-11-26 创建：draft #453676040 · 标题含【RELEASE_CANDIDATE 草稿——真机验证未完成，暂不发布】· APK 已作为 release 资产上传 · 正文含校验表与诚实口径）。正式发布（publish 仪式 + 仓库描述同步）待真机验证通过后执行——发布入口：GitHub Releases 页的该草稿一键 Publish，或按 scripts/publish-v*.mjs 惯例写 publish-v800 脚本。
8. **GitHub Actions 构建 workflow 已写入待激活**：`.github/workflows/build-apk.yml` 已在本地工作树（未跟踪）+ 远端归档副本 `docs/ci-workflows/build-apk.yml`（内容一致）；首次推送 .github/workflows 需 token 具备 `workflow` scope——本轮 PAT 仅 repo scope，push 实测被拒收（已 soft-reset 摘提交保持仓库可推送）。激活二选一：用户网页创建该文件，或换 workflow-scope token 推送（`cp docs/ci-workflows/build-apk.yml .github/workflows/build-apk.yml && git add .github && git commit -m 'ci: add build workflow' && git push`）。

---

## I. 下一步任务优先级

- **P0**：APK 自动化构建 —— **已完成（2026-11-26）**：本地官方工具链构建成功 + GitHub Actions workflow 已写入（激活见 H-8）
- **P1**：APK 静态检查 —— **已完成（2026-11-26）**：存在性/大小/包名/versionName/versionCode/minSdk/targetSdk/签名（v2+v3 + 证书与历史一致）/ZIP 结构/DEX（五模块在场·测试类缺席·shim 零命中）/敏感扫描全过
- **P2**：真机安装与基本功能回归（覆盖升级 79600→80000；本沙箱无设备 → NOT_AVAILABLE，待有真机环境执行）
- **P3**：真实日志上传闭环验证（下方 20 项计划）
- **P4**：断网恢复 / Doze / 重启恢复专项
- **P5**：GitHub Release 发布（RELEASE_CANDIDATE 口径直到真机验证通过）

### Android 真机验证计划（20 项 · 供有真机环境执行）

设备记录：Xiaomi 2410DPN6CC（Android 17 / SDK 37）——**须重新确认当前连接，不得假定**。

1. 安装 v8.0.0；2. 覆盖升级安装（7.9.6 → 8.0.0）；3. 启动应用；4. 授权日志上传；
5. 未授权状态下检查无新增持久化日志；6. 手动提交日志；7. 检查上传状态；
8. Worker 收到请求（服务端日志/Issue）；9. GitHub Issues 出现真实日志；
10. 检查 Issue 内容脱敏；11. 相同 batchId 重试（ALREADY_ACCEPTED 不二建 Issue）；
12. 网络断开后恢复；13. 应用进程被杀后恢复；14. 手机重启后恢复；15. Doze 模式测试；
16. 429 限流测试；17. 401 身份验证失败处理；18. 409 幂等冲突处理；
19. 上传失败不影响广告跳过；20. 确认无 FALSE_CLICK / DUPLICATE_CLICK 回归。

（不能只验证上传成功——必须同时确认广告跳过核心功能零退化。）

---

## J. 不可违反的开发纪律（长期有效）

**安全优先级（不许倒置）**：

```text
安全 > FALSE_CLICK > DUPLICATE_CLICK > MISS > 正确关闭 > 命中率 > 性能微优化
```

- 不为命中率降低 SafetyGuard；不扩大点击坐标区域；不添加宽泛跳过关键词；
- 不无依据增加轮询；不在无障碍线程做网络请求；不让上传阻塞广告跳过；
- 每轮修改：先查真实代码 → 明确目标 → 最小改动 → 测试 → 查 diff → 敏感扫描 →
  commit → push → **核验本地 SHA == 远端 SHA**；
- push 遇网络错误重试（`git -c http.version=HTTP/1.1`，认证用仓库外 token 文件，
  绝不写进代码/文档/聊天）；**禁止 force push / 删除远端分支 / 空提交 /
  伪造测试 / 把 JVM 测试说成真机测试 / 输出任何 Secret、Token、Private Key、OAuth Code**；
- 不要求用户反复重新授权；优先复用现有架构；不做无必要的大重构；
- 不为凑版本号修改生产代码；不把构建模板值当最终 APK 版本值；
- 用户希望尽可能一次性连续完成任务，避免每小步等待确认——
  只有必须用户亲自授权/提供设备/不可替代人机交互时才暂停。

---

## 附：中转服务速览（详见 Worker 仓 HANDOFF-V8.0-WORKER.md）

- 端点：`https://skip-ad-log-relay.2927609408.workers.dev/api/logs/receive`（`Net.LOG_RELAY_URL` 第 52–53 行）
- 状态：`DEPLOYED_BUT_NOT_E2E_VERIFIED`（`/healthz` 经用户真实浏览器验证 ok=true · ipHmacConfigured=true）
- 客户端必须遵守的终态语义：`MANUAL_RECONCILIATION_REQUIRED`（HTTP 422 + manualReview:true）=
  立即终态、绝不换 batchId 重传；`429` 尊重 retryAfter 不耗重试计数；`200 ALREADY_ACCEPTED` 复用原 issueNumber。
