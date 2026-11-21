# 版本变更归档（CHANGELOG）

> 首页只保留当前版本要点；全部历史版本详情在本页归档。
> 发布物（APK/报告/文档）见 [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) 与 `docs/test-reports/`。

## v7.9.7（2026-11-21）—— PAT 移除安全轮：APK 零 GitHub 凭据 + 自动日志上传授权（默认关）+ 手动日志本地导出

- **本轮定位（用户指令：自动日志上传系统正式实施 · 第一步安全改造——无公网服务依赖部分）**：内置 PAT 从 Android 端移除是 P0 安全项（与 v7.9.4 Web 端整改同源同标准）；自动日志上传整体架构升级为「Android → HTTPS 中转服务（鉴权 / 24h 滑窗最多 3 次 / batchId 幂等 / 隐私二次过滤）→ Skip-Ad-Logs 私有仓库 Issues」，本版本落地无需公网服务的前置改造，v8.0.0 上线中转服务后接入。
- **P0 · 内置令牌移除（CURRENT_CONFIRMED 生产实锤）**：`Net.builtInToken`（双层 XOR+Base64，与 v7.9.4 已移除的 Web 端 credentials.ts 同源密文）+ `postIssue`（自动/手动日志直发 GitHub Issue）整体删除——混淆不等于加密，公开分发 APK 内任何令牌形态必须视为已泄露凭据（v7.9.6 发布说明中 `GET /user` 实测该令牌仍有效 → 撤销动作仍待仓库所有者执行，docs/SECURITY.md R1）。`latestRelease` 检查更新改匿名请求（v3.3.0 重试 + /releases 列表回退链路不变，仅去 Authorization 头——手动检查频次远低于匿名限频）；新增 `newIssuePrefillUrl` 浏览器预填链接（用户登录态自助提交，零令牌）。
- **自动上传旧通道下线 + 新通道双门结构**：`maybeAutoUploadLog` 重写——授权门（`KEY_AUTO_LOG_UPLOAD` 默认 false）与中转门（`Net.LOG_RELAY_URL` 空串）任一未过即零行为。KeepAliveReceiver 心跳调用点保持（零额外调度开销）；`AUTO_LOG_MS`（8h）保留为 v8.0.0 客户端节拍值（最终限额以服务端 24h 滑窗为准）。
- **手动日志本地导出（替代直发 Issue）**：MainActivity 新桥 `exportLog`（应用外部私有目录 `logs/`，无新权限；Java 侧 `Net.maskSensitive` 二次脱敏——JS `maskText` + Java 双层保持）+ `shareLogText`（ACTION_SEND 系统分享面板）；`netPostIssue` 桥删除；崩溃堆栈自动附带逻辑（读后即删）保持。
- **自动日志上传授权（默认关）**：设置页新增独立开关 + 状态行（「已授权 · 服务器上线前不执行」/「默认关闭」）；与无障碍授权严格分离（开无障碍 ≠ 同意日志上传）；授权 ≠ 上传。
- **WebView 劫持面注释同步**：桥已无任何凭据能力（netPostIssue 已移除），URL 白名单制不变（纵深防御不因单点移除而松绑）。
- **本轮零识别/安全门改动**：不新增关键词、不扩大点击区域、不降 SafetyGuard、不加轮询、不动任务状态机——唯一生产修改面 = 日志通道/凭据/授权（安全 > 正确关闭 > 命中率 > 性能微优化）。
- **测试与闸门**：31 套件 6653 项 JVM 断言全绿（修改前后各跑一轮，零回退）；全源 ECJ 编译通过（android-34 platform）；secret-scan 双闸全绿；check-appjs 语义闸全绿；version-sync（7.9.7/79700）全绿。versionCode 79700（> 79600 覆盖安装即升级；无障碍授权/学习数据/白名单/已导入订阅全部保留）。
- **v8.0.0 展望（自动日志上传完整系统，待中转服务上线）**：Skip-Ad-Logs 私有仓库 + Cloudflare Worker（Durable Objects 原子限流/幂等/恢复）+ GitHub App 最小权限凭据 + LogBatchStore 批次持久化 + 退避重试——方案与中转服务代码已于本轮交付（`log-upload-staging/`，见发布说明）。

## v7.9.6（2026-11-20）—— L3 真实世界验证轮：Bug#001 终局三漏根治（冷却闸绕过→无限重试循环阻断 + MISS 终局记账）· 一个真实 Bug 一个回归测试

- **本轮定位（用户指令：L3 第一批 50 款真实 App 验证——真实 App → 真实广告 → Decision Evidence → SafetyGuard → HumanTap → VERIFY → 真实结果 → 失败样本 → 缺陷修复 → 回归测试）**：不以堆功能为目标。**真机复查仍无设备（adb/emulator/KVM 均不可用）→ REAL_DEVICE_TEST = NOT_AVAILABLE，L3 批次 1 保持 PENDING_REAL_DEVICE（50 款记录字段一律真机回填，绝不预填/不虚报）**。按用户指令第一原则，唯一允许的生产修改 = 「发现明确生产 Bug」——本轮以代码审读实锤 **Bug#001**（v7.9.5 六态闭环接线引入的潜伏终局竞态）并按「一个真实 Bug 一个回归测试」闭环。
- **安全状态检查（用户指令二）**：旧 GitHub PAT 复查——`local-gh-token`（仓库外 · 未入库 · 未提交历史）经 `GET /user` 实测 **仍有效（HTTP 200）→ 未撤销 → SECURITY ACTION REQUIRED**（撤销需账号所有者在 GitHub Settings 操作，本轮继续只作仓库外凭据文件使用，绝不创建新的硬编码 Token）；公开 keystore 风险确认（`android-app/keystore/` 入库为知情接受现状——docs/SECURITY.md R2 已载 v8.0 迁移计划，按用户指令不贸然换钥）；secret-scan 双闸全绿（令牌形态/credentials 结构红线/local-gh-token 未入库/keystore 材料面四道检查）。
- **Bug#001（代码审读实锤 · 生产缺陷 · v7.9.5 引入六态闭环接线时新增）**：`AdSkipService.taskOnScanOutcome` 非零候选路径在「预算耗尽于复验时刻」（最后一击派发后复验仍见候选且 tries ≥ maxClicks）直接置 `S_FAILED`，**三漏**：
  1. **不落 endedAt**：`taskAllowsNew(now, 0)` 因「无终态记录恒放行」直接放行 → **2.2s 冷却闸被绕过** → 立即开新任务且 `resetTaskForNewInstance` 清空 clickTries/gaveUp → **同一存活广告借「新任务重置预算」无限重试循环**（「绝不无限重试/预算梯子」红线失守——DUPLICATE_CLICK 同族风险面；execute 预算闸因预算被重置永不触发，gaveUp MISS 记账路对整流程失效）；
  2. **不记 MISS**：最后一击复验失败被膨胀记成 RETRY——六态把「任务终局 MISS」整类漏记（真实漏检率证据链失真，v7.9.6 起真实数据统计口径被破坏）；
  3. **终局无日志**（用户无从知道本窗已收手）。
- **修复（终局裁决收进纯函数 · 服务侧薄接线 · 零识别/安全门改动）**：新 `AdTaskCore.onVerifyStillStep`（纯 Java · 与 AdTaskCore 同规约）——预算耗尽 → `FAILED + endedAt=now`（冷却闸在岗：新任务须过 2.2s 闸才重置预算——无限重试循环结构性阻断）+ **MISS 终局一次记账**（本击复验不再计 RETRY——严格区分「点击派发成功」与「任务终局」）；预算内首击复验仍记 RETRY（既有行为零变化）；FEEDBACK 复验在场原态保持（面板流程在先，零行为变化）；终态不可逆不重复记账（旧任务绝不复活）；终局补「复验预算用尽」日志行。服务侧 `taskOnScanOutcome` 仅应用步进结果（识别/词表/形状门/SafetyGuard/轮询零改动）。
- **一个真实 Bug 一个回归测试（用户指令十七）**：新套件 `Bug001RegressionTest`（52 断言 · 八段）——S1 预算耗尽终局三件套（FAILED+endedAt=now+MISS，对照预算内 RETRY_WAIT+RETRY）· S2 全生命周期（三击梯子 RETRY=2·MISS=1·FAILED 后点击/派发/回调全拒——第二次点击=0）· **S3 冷却闸闭环（Bug#001 核心——终局后 2.2s 内新任务全拒，+2.2s 放行；endedAt≠0 结构性不变式防回退）** · S4 门语义取证（`taskAllowsNew(now,0)=true`——正是 Bug#001 绕过机制，钉死「终局必须落账」的结构性原因）· S5 终态不重复记账 · S6 FEEDBACK 语义零变化 · S7 RETRY 仅 VERIFYING 首击复验计一次（防同一击多扫膨胀）+ RETRY_WAIT+预算耗尽防御路径同根治 · S8 六态计数端到端（RETRY=2·MISS=1·其余四态 0——FALSE_CLICK 自动化恒 0）。
- **L3 批次与样本库诚实口径（用户指令三/十六）**：`compatibility/l3_batch1.json` meta 版本刷新 + realDeviceTest 增加 v7.9.6 复查记录（仍 NOT_AVAILABLE，记录字段全部保持 null 绝不预填）；`compatibility/samples/` 复查仍空库（0 条）——Bug#001 为代码审读实锤的潜伏竞态（非真机失败样本，无 device/adType 真实字段），按「一个真实 Bug 一个回归测试」直接修复并回归，不入真机样本库（绝不伪造样本字段）。
- **测试（31 套件 6653 项全绿，v7.9.5 为 6601——+52 = Bug001RegressionTest；既有 30 套件零回退逐套件核对）**；secret-scan 双闸 · version-sync（7.9.6/79600 双端统一）· app.js 符号闸三绿；APK versionCode 79600（> 79500 覆盖安装即升级）三目录分发。
- **下一轮 P0**：真机 L3 批次 1 执行（50 款逐款回填）——失败样本入库驱动修复（FALSE_CLICK 最高优先级）；PAT 撤销确认。

## v7.9.5（2026-11-21）—— 真实世界证据层：点击决策证据 + 点击结果六态闭环 + WebView 综合证据路径 + 自适应轮询 + L3 批次定义

- **本轮定位（用户指令：真实 App → 真实广告 → 候选 → Decision Evidence → SafetyGuard → HumanTap → VERIFY → 真实结果 → 样本沉淀）**：本轮不以增加 JVM 断言数量为目标——把「证据层」建进生产链路，为 L3 真机回填与失败样本沉淀铺轨。
- **新核心模块 `core/DecisionEvidence.java`（用户指令第一/第二优先级 · 纯 Java · 零 Android 依赖）**：
  - **点击决策证据（最小结构化）**：每次准备 CLICK 前记录——包/Activity/任务ID（winStart#seq）/扫描代际/任务状态 · 候选来源（text / contentDescription / resourceId / imageCorner 四枚举）· 上下文四格（adContext 广告语境 / sdkContext SDK 窗指纹 / webViewContext WebView 容器 / subscriptionContext 订阅路径）· 归一化几何（中心位置/面积/屏幕区域 TL·TR·BL·BR·MID/可点）· 安全裁决（SafetyGuard 终闸结果）· finalDecision（CLICK_DISPATCHED / SAFE_BLOCK）。
  - **隐私红线（结构性强制——不是约定而是模块能力面）**：不保存完整 Accessibility Tree（模块没有任何树容器/节点引用）· 不上传原始屏幕截图（零位图/字节引用）· 不保存账号/手机号/支付/聊天内容（候选标签白名单闸 `sanitizeToken`——只保留 close/skip 词族成员 ≤12 字符，其余一律空串；resourceId ≤32 字符且资源名形态校验）· 环形缓冲有界（12 条，绝不无限驻留）· 证据链异常全吞（绝不影响点击主链）。
  - **点击结果六态闭环**：CLICK_DISPATCHED → VERIFYING → `VERIFIED_SUCCESS / RETRY / MISS / FALSE_CLICK / SAFE_BLOCK / UNKNOWN`（用户指令词表严格使用）——**dispatchGesture 成功绝不等于广告关闭**（唯一成功证据是验证链：两轮零候选/窗口切换/反馈面板收起三路汇合）。服务侧接线：markTaskSuccess/markTaskWindowChanged → VERIFIED_SUCCESS · taskOnScanOutcome（VERIFYING 仍见候选）→ RETRY · markTaskFailed → MISS · 终闸拒点 → SAFE_BLOCK · FALSE_CLICK 自动化环境恒 0（真机样本回传唯一来源——绝不虚报）· UNKNOWN 诚实占位。上传日志新增「点击结果（六态计数）」与「点击证据（最近一击最小结构化 JSON 行）」两行。
- **GKD 订阅证据链八格（用户指令七——不追求规则加载条数）**：`consulted / matched / candidateAccepted / clicked / verifiedSuccess / clickFail / falseClick / blocked`——consulted：matchSources 被咨询即计数（含未加载/无覆盖——订阅参与度与命中解耦，「内置探针命中导致订阅不再参与」的反饥饿证据面）；candidateAccepted：候选过 SafetyGuard 终闸；verifiedSuccess：本任务出手 reason 为订阅规则且任务 VERIFIED_SUCCESS（点击→广告真关闭闭环，markTaskSuccess/markTaskWindowChanged 双路回报）。订阅页 stateJson 新增 `subStatV2` 段 + 上传日志「订阅证据链」行；七分类 subStat 与既有 S8 断言零破坏。
- **WebView 综合证据路径（用户指令六——真广告+ScrollView/Input/WebView 专项）**：v7.9.4 已知 isAdLikeLoose 对滚动容器/输入框形成过强阻断；WebView 视频容器大树（>150 节点）同被拒——真 H5 广告带跳过文本整窗漏拦。**不能简单取消安全门**：综合判断 = `webviewPresent && (adBadge || sdkAdWin || dialogForm || hotZone)`——WebView 容器在场【且】至少一个广告语境证据同窗才解锁 L1 跳过键与 L1.5/L1.6 关闭键三路。**WebView 单独在场不构成证据**（应用内浏览器普通页零出手——误触红线保持）；候选语义（跳过/关闭词族）+形状门+SafetyGuard 终闸全部在岗。
- **视频开屏自适应轮询（用户指令八——20s ceiling 保持）**：低证据降频——连续 ≥4 针探针零命中且零出手（≈6s 无证据增量）→ 轮询 1.5s→3s（不再以固定高频扫整个 20s 观察窗）；探针命中或出手立即恢复标准节拍（证据增强）；证据消失立即终止（每针头部 videoLike 复核——既有契约保持）；终态 3s 契约（v7.9.1）保持；`adaptivePollGapMs` 纯函数统一裁决三档，既有 pollDue 3 参重载零破坏。
- **L3 真机批次 1 定义（用户指令四——不按列表前 50）**：`compatibility/l3_batch1.json`（scripts/gen-l3-batch.py 可复现生成）——50 款 · **12/12 类配额**（社交 5/短视频 5/电商 5/视频 6/音乐 3/新闻 4/阅读 4/浏览器 4/工具 6/游戏 4/学习 3/金融生活 5/其他 1）· **特殊广告形态强制收录**（video-splash 全收 6 + banner 2 + rewarded-video 2 + feedback-sheet 4）· **引擎覆盖四档交叉**（GKD 快照 43/内置订阅 39/Feed 名单 11/零覆盖 3——零覆盖长尾优先补位诚实测盲区）· P0/P1/P2 混合 · 每款 14 项记录字段（app/package/appVersion/device/androidVersion/date/adType/uiFramework/observedAd/candidate/decision/clicked/verification/finalResult）全部 PENDING_REAL_DEVICE。**REAL_DEVICE_TEST = NOT_AVAILABLE**（本机无真机）——绝不虚报执行结果。
- **失败样本库建库（用户指令十/十一）**：`compatibility/samples/`（index.json + SCHEMA.md）——每条失败样本必填 sampleId/package/appVersion/androidVersion/device/adType/uiFramework/result/decisionEvidence（最小结构化）/failureReason/fixedInVersion；**修复优先级 FALSE_CLICK > DUPLICATE_CLICK > MISS > 命中率**；隐私红线与入库流程成文。当前库为空（0 条）——诚实标记。
- **极简窗口专项批次 + 证据层测试（用户指令五/九——真实缺陷与新安全边界才新增测试，不为数字加测试）**：
  - `MinimalWindowBatchTest`（32 断言 · 八段）：≤60 节点无文本极简窗——8 类交互图标（关闭/更多/返回/收藏/分享/设置/头像/普通按钮）× 四角（TL/TR/BL/BR）在普通 UI（滚动容器在场）**全部零出手**；树极简兜底契约（形态路径——与 PageScenarioTest custom-draw A 同口径）；内容丰富窗（>60 节点）8 类图标全零出手；中部边带 strongOnly（FAB 防线——树极简兜底不越权）；窗级门/时机闸（<300ms 零出手 · 7s 超窗零出手 · 300ms 首过即出手）；vid 语义正信号（iv_skip 走跳过键路径）；对照侧（角标在场照常出手——识别零回退）。
  - `DecisionEvidenceTest`（82 断言 · 六段）：证据模式与 JSON 字段 · 隐私红线（手机号/账号/支付/长文本/非整词全部空串拒收 · resourceId 超长与非资源名形态拒收 · 有界缓冲 24 条记录后仍 12）· 六态计数与非法值拒收 · stateName 映射 · WebView 综合证据路径（真广告+ScrollView 出手 · 真广告+输入框出手 · 真广告+WebView 大树出手 · 应用内浏览器普通页零出手 · 原生滚动孤立跳过词零出手——v7.9.4 契约零回退 · badge_close WebView+角标+滚动出手 · 无第二语境 WebView 窗 × 键零出手）· 自适应轮询节拍三档与到期判定 · 订阅证据链八格计数口径与兼容面。
- **测试（30 套件 6601 项全绿，v7.9.4 为 6487——+114；既有 28 套件零回退逐套件核对）**；APK versionCode 79500（> 79400 覆盖安装即升级）三目录分发。

## v7.9.4（2026-11-21）—— FalsePositive 场景层（20 类误触红线）+ AdTask 时序压力（A-H + Case1/2/3）+ 安全部署链整改

- **本轮定位（用户指令第四/五/六阶段 + 最高优先级安全审计）**：证明「绝对不应该点击」的 20 类误触红线场景 + 时序竞态硬回归 + 部署链密钥整改；**实测揭出并根治一个真实误触缺陷**。
- **生产修复两处（证据质量提升——不是关闭扫描，零识别回退）**：
  1. **L1 跳过键路径广告形态证据门**：`decide()` 出手条件从 `R+F>0` 收紧为 `R+F>0 && (isAdLikeLoose(f) || sdkAdWin || learnedCls)`。**FalsePositiveScenarioTest 实测揭出**：旧版在开屏窗龄（12s）内对聊天窗/内容列表页里孤立出现的「跳过」字样（可点小气泡/列表行）直接 `skip_text` 出手——零页面形态证据。真开屏窗恒满足形态门（静态图+跳过键从不带滚动容器/输入框，≤150 节点）零回退；与 L1.6 关闭键路径（v3.7.0 起同款 isAdLikeLoose 门）口径对齐。
  2. **AdTaskCore 无任务 S_NONE 契约加固**：六个状态转移（onVerifyAdGone / onVerifyAdStill / onVerifyUncertain / onFeedbackSeen / onWindowChanged / onCancelled）增加 S_NONE 守卫——「无任务」时任何事件不得无中生有出状态（服务侧 null 守卫已保证不传 S_NONE，纯契约收紧——AdTaskTimingTest 无任务面真值表）。
- **FalsePositiveScenarioTest 新套件（69 断言 · 九段断言面）**：用户 20 类误触红线场景逐类断言「绝对不点」——银行/政务/支付守护名单页（可滚+长文本+输入框形态铁证；风险面诚实记录：右上消息图标确被收集为角落候选但内容形态闸拒之）· 相机页面（模式条可滚+快门/闪光/翻转角落图标在树仍零出手）· **聊天窗口（孤立「跳过/关闭」气泡零出手——生产修复的实测触发面）**· Launcher 桌面（Workspace 可滚+图标标签零跳过候选——issue#12 桌面盲点毒链历史红线）· 商品详情页/购买确认弹窗（CTA 族永不成为点击目标）· 收藏/分享/评论 · 更新提示单信号 · 权限请求（允许/拒绝均不代点）· 会员弹窗（非弹窗广告窗期零出手；最坏情形只关「关闭」族、绝不对「立即领取」CTA 出手）· 普通 PopupWindow 菜单（孤立「不感兴趣」菜单项不是点击理由）· 表单 Dialog · 悬浮 FAB（strongOnly 无强广告形态不出手）· 多窗口分屏 · 小程序/支付/团购容器 L0 纵深防御（真广告形态树同拒·三容器）· 反馈面板；**孤立词红线**（「关闭/×/取消/跳过/稍后/知道了」普通页孤立出现 + 开屏窗龄内内容形态页孤立出现均零出手）+ **对照侧**（真开屏形态窗「跳过」键照常出手、角标在场「×」照常出手——识别能力零回退，用户指令八兑现）。
- **AdTaskTimingTest 新套件（95 断言）**：**A-H 八组时序竞态**——A/B 旧 callback 100/300ms 两档（两轮零确认 350ms 间隔不提前判关；SUCCESS 后 500/1250/2000ms 全拒收）· C 候选坐标改变（candidateAlive=false 拒旧坐标；新候选五要件重验全真才出手；窗口也变绝不出手）· D 窗口切换终局（旧任务不续命不覆盖不进 FEEDBACK）· E Dialog 消失后普通按钮冷却闸（2.2s 残留期不点普通按钮）· F 反馈面板零补刀（2.2s 边界含尾）· G videoPoll 终态降频 3s 契约（观察在岗零点击）· H 延迟露头关闭键六档 100/300/500/800/2000/5000ms（文本键出现帧即出手；角落图标 300ms 时机闸）；**Case 1/2/3 三种真机实锤硬回归**——Case 1（成功后旧 task/callback/timer/videoPoll/TapGestureCallback 全拒·第二次点击=0）· Case 2（重试五要件逐项缺失全拒——重新获取广告状态/候选/安全闸/可点性/任务有效性）· Case 3（click()=true ≠ 广告关闭：onDispatch 五种非终态源枚举均无 SUCCESS 出口；唯一成功通道是真验证）。
- **安全整改（最高优先级审计产出 · docs/SECURITY.md 披露）**：
  - **R1 GitHub Token 出库**：`src/lib/credentials.ts` 自 v2.0.0 内嵌「双层 XOR+Base64」混淆 classic PAT——公开仓库中混淆令牌=已泄露，**已标记需所有者撤销**。HEAD 完全移除（凭据链改为环境变量 `SKIPAD_GH_TOKEN`/`GH_TOKEN`/`GITHUB_TOKEN` → 仓库根未入库文件 `local-gh-token`，`.gitignore` `local-*` 放行收集，权限 600）；`scripts/gh-token.mjs`（推送链）同款整改；git 历史含令牌（禁 force push 不可原地清除——历史暴露事实不可撤销，撤销动作归仓库所有者）。
  - **新增密钥泄露扫描硬门 `scripts/secret-scan.sh`**（四道检查：被跟踪文件令牌形态 ghp_/github_pat_/AKIA/私钥头/x-access-token URL 凭据对 · credentials.ts 内嵌凭据形态 Base64 长串/KEY 数组/XOR 解码器 · local-gh-token 绝不入库 · keystore 材料散落），接入 `run-tests.sh` 与 `build.sh` **双闸**——测试与打包任一闸命中即拒。
  - **R2 keystore 公开风险**：签名私钥+口令公开（v1.2.0 起）——按发布安全规程**不贸然换钥**（直装分发换钥=断更）；v8.0 迁移方案立项（新钥安全存储+迁移指引+过渡版本）；`build.sh` 签名口令支持 `KS_PASS`/`KEY_PASS` 环境变量注入。
  - **APK 产物扫描**：v7.9.4 APK classes.dex + assets（app.js）+ 原始二进制三面扫描零令牌形态命中。
- **测试（28 套件 6487 项全绿，v7.9.3 为 6323——+164 = FalsePositiveScenarioTest 69 + AdTaskTimingTest 95；既有 26 套件零回退·逐套件计数核对）**；APK 861750B · VC 79400（> 79300 覆盖安装即升级）三目录分发（android-app/dist / download / upload）· 同证书签名（apksigner verify 通过）· MD5 09a9be90 · SHA-256 742cf24c。

## v7.9.3（2026-11-21）—— L2 页面场景层：12 类 UI 独立样本 × 8 项场景行为 + 订阅七分类真实计数器断言

- **本轮定位（第三阶段 L2 规格：不能要求所有 200+ 应用都装在一台手机上——用 12 类 UI 独立样本建页面结构兼容性层）**：本轮零生产逻辑改动——全部是测试与测试钩子（安全红线、点击行为面、性能面全部零变化）。
- **L2 页面结构与场景兼容性套件（PageScenarioTest · 83 断言 · 八段断言面）**：
  - **S1 十二类 UI 独立样本**：native-view（银行简洁原生开屏：文本跳过键可访问+出手）/ WebView 容器（淘宝 TMS 营销窗：WebView 铁证+角标可探+关闭路径出手）/ Compose（semantics 文本跳过键可访问+出手）/ Flutter（desc-only 节点 label 回落可访问+出手）/ ReactNative（文本跳过键）/ 自绘控件三态（树极简角落图标合法出手——极简窗兜底形态；内容丰富的自绘窗角落图标零出手——无强广告形态不乱点；SDK 插屏窗指纹在场图标路径解禁）/ Canvas（SurfaceView 零文本零候选——画像路径承接）/ 视频播放器（控制层「跳过片头」可访问+出手）/ 半屏 Dialog 插屏（角标与关闭键可探+出手）/ PopupWindow（更新诱导单信号零出手——误触红线）/ 底部悬浮 overlay（半屏窗角标与关闭键可探+出手）/ 多窗口分屏（内容窗零出手——另一半不因分屏被误扫出手）。
  - **S2 内容延迟加载与关闭按钮延迟出现**：帧1 未加载零出手 → 帧2 跳过键出现即出手（事件驱动等价表达）；videoLike 画像 / 20s 观察窗 / arm-hint 免杀链（>12s 露头按钮可等到）。
  - **S3 候选消失与任务终态**：DETECTED → READY → 出手（VERIFYING）→ 两轮零确认（第一轮零不确认——树刷新抖动防线；≥350ms 独立两轮才确认）→ SUCCESS 不可逆 → 2.2s 冷却闸内新广告不开新任务 / 冷却过后可开。
  - **S4 广告与反馈面板切换**：面板文案（「我为什么会看到广告」）零跳过候选；FEEDBACK 态不出手不对原广告补刀；面板收起 → SUCCESS（面板只在广告关闭后出现——铁证）不恢复原任务；终态冷却期内残留面板候选不开新任务。
  - **S5 页面内部导航**：同窗身份零出手（tab 切换内容树变化但窗口身份不变）；聊天窗（可滚+输入特征齐全）零出手；微信 LauncherUI 不进 feed 扫描（窗级门）。
  - **S6 窗口切换任务终局**：有出手 → SUCCESS 落账；零出手 → CANCELLED（不诬为成功）；验证中被打断 → 终局（旧任务不续命）。
  - **S7 视频开屏画像调度契约**：videoLike 四因子（窗龄/零候选连环/静默/SDK 指纹——含 streak 前置）· 观察窗 12s/20s 双档 · 轮询节奏 active 1.5s 与终态 3s 降频 · 白点节流 ≥1.5s/击 · 每 3 针一次全量复扫（20s 窗 ≤5 次）· 预算刷新=新文本键（无新键不刷新）。
  - **S8 订阅七分类真实计数器**（SubscriptionManager 生产实现首次纳入 JVM 测试面——run-tests.sh 编译闭包扩至 SubscriptionManager + Net + android.jar，运行期零 Android 运行时类加载）：noCover（未知包应用不匹配 +1）· noMatch（有覆盖页面不匹配 +1——与 noCover 分列）· hit（内置订阅跳过键规则命中 +1，命中列表非空）· notLoaded（已加载零计数——归因诚实）· clickFail/clickOk/blocked（countClickOutcome 三分支）· 非订阅规则路径不计入（不虚标）——**不再把所有未命中都归为订阅未命中**（v7.9.1 七分类统计的 L1 自动化验证闭环）。
- **生产代码改动**：仅 SubscriptionManager 新增两个 JVM 测试钩子（primeForJvm 只加载内置订阅与 ensureLoaded 第一段完全相同、subStatSnapshot 七计数器快照与 stateJson 的 subStat 段同源）——生产永不调用、幂等闸不动、remote 保持 null 基线。
- **测试（26 套件 6323 项全绿，v7.9.2 为 6240——+83 = PageScenarioTest 新套件；既有套件零回退）**；APK 844KB · VC 79300（> 79200 覆盖安装即升级）。

## v7.9.2（2026-11-21）—— 200+ 应用生态兼容性基线：233 款应用目录 + L1 测试体系 + 引擎包名校准五项根治

- **本轮方法论（用户指令「从应用生态整体角度分析问题，不能只围绕微信/淘宝/ting.android 做定向修补」）**：建立覆盖 200+ 常用 Android 应用的兼容性目录与分层测试体系；包名必须经过核实（应用商店上架信息逐条网络核实，检索证据存 `tool-results/pkg-*.json`），宁缺毋滥不虚报。
- **233 款应用兼容性目录（compatibility/apps_catalog.json）**：
  - **包名三层来源严格区分**：引擎自证 115（内置订阅 Subscription.java 实时提取）/ 网络逐条核实 23（sj.qq.com / play.google.com / apkpure / app.xiaomi.com / a.app.qq.com）/ 知识库高置信 95（世界知名应用，待真机 adb 核验）。
  - **12 分类配额全达标**：社交 25 / 短视频 25 / 资讯 20 / 电商 26 / 音乐 10 / 浏览器 11 / 工具 24 / 阅读 15 / 游戏 17 / 学习 15 / 金融生活 35 / 其他 10。
  - **每款应用记录**：appName / packageName / category / source / priority / adTypes / uiFramework / subscriptionCoverage（内置订阅 119 / Feed 名单 19 / GKD 三源快照 148 交叉计算）/ testStatus / lastTestVersion / regressionStatus / notes。
  - **诚实口径**：adTypes 为预期形态（L3 真机验证前的先验标注）；testStatus=l1-tested 仅指规则加载/包名/引擎通用逻辑断言通过，不代表真机广告识别已验证；未核实包名绝不收录（TapTap 国内版/SHEIN/央视频/一点资讯等证据不足者全部拒收）。
- **引擎包名校准五项根治（目录核实直接揭出——全部数据级修复，零点击行为面变化，规则族不变）**：
  1. **中国联通死条目**：旧包名 `com.sinovatech.unicom.ui.welcome.WelcomeActivity` 把 Activity 类名当包名——规则从未命中过任何真实设备（应用商店核实真实包名 `com.sinovatech.unicom.ui`，sj.qq.com + app.xiaomi.com 双源）。
  2. **网易新闻 Feed 名单死值**：FeedAdGuard FEED_APPS 旧值 `com.netease.newsreader` 缺 `.activity` 后缀——信息流护栏对网易新闻全程无效（Google Play 上架 id=com.netease.newsreader.activity 核实）。
  3. **建行/中行名称互换**：`com.chinamworld.main` ↔ 建设银行、`com.chinamworld.BOCMB` ↔ 中国银行（显示级更正，规则按包名路由不受影响）。
  4. **剪映/CapCut 混淆**：`com.lemon.lvoverseas` 实为 CapCut 海外版（旧条目误标「剪映」）；补国内版剪映真实包名 `com.lemon.lv`（sj.qq.com + m.app.mi.com 双源）。
  5. **飞常准/航班管家互标**：`com.flightmanager.view` 实为航班管家（旧条目误标「飞常准」）；补飞常准真实包名 `veryzhun.vzcom`（Google Play 核实）。
- **L1 分层测试体系（AppCatalogTest 新套件 · 43 断言 · 六段断言面）**：
  - S1 目录完整性：规模 ≥200（实际 233）/ 12 类配额 / 包名格式与唯一性 / 字段合法性。
  - S2 内置订阅↔目录双向一致性：零孤儿包（引擎每个包名都有目录应用）+ 零虚标（目录 builtin 标注全部真实）+ v7.9.2 新增四包在场。
  - S3 Feed 名单↔目录一致性：引擎名单全落座目录（网易新闻类死条目缺陷的回归闸）+ 目录 feed 标注与引擎名单零漂移 + 网易新闻真实包名接纳断言。
  - S4 GKD 三源快照：三份 json5 快照全部可解析 + 目录 gkd 覆盖 ≥140（实际 148）。
  - S5 逐应用规则匹配仿真：RuleEngine 分应用规则行路由（11 个已知包全命中）+ 233 款逐包 rowsFor 零 null + 内置订阅 119 款逐包开屏仿真全命中（walk+decide 必须产出动作）+ 同款应用正常主界面窗零出手反例（误触红线）。
  - S6 包名校准五项回归闸：联通死包名不复活 + 五组包名↔名称精确对位断言。
- **诚实口径测试矩阵（compatibility/test_matrix.json）**：L1 规则/引擎自动化（全量 233 款）→ L2 页面结构场景（12 类 UI 独立样本：native/WebView/Compose/Flutter/RN/自定义绘制/Canvas/视频播放器/Dialog/PopupWindow/悬浮覆盖层/多窗口分屏）→ L3 真机实测（分批 50/100/233，结果词汇表 NOT INSTALLED / NO AD OBSERVED / MISS / FALSE CLICK / VERIFIED SUCCESS）；统一指标口径（广告关闭成功率 = 确认成功关闭数/实际执行且符合条件的广告样本数——规则匹配成功 ≠ 广告关闭成功 · 应用数量 ≠ 广告样本数量 · 自动化断言 ≠ 真机兼容性测试）；L3 当前 0（无真机连接——绝不虚报）。
- **24 小时自动日志同步（3 次/天 · scripts/auto-log-sync.mjs）**：学习数据快照刷新 + 当日 history 归档 + git 变更即提交推送（无变更幂等退出——不为凑提交制造噪声）；只 add data/ 目录（绝不提交密钥/Token/用户隐私日志）；推送失败原样报告绝不伪造成功。三项定时 01:20 / 09:20 / 17:20（Asia/Shanghai）。
- **覆盖报告（compatibility/coverage_report.md）**：分类配额达成表 / 分层测试进度（L1 全量✅ · L2 部分🔶 · L3 未开始⛔）/ 本轮引擎数据缺陷五项表 / 未核实项清单（宁缺毋滥）/ 维护规则四条。
- **测试（25 套件 6240 项全绿，v7.9.1 为 6197——+43 = AppCatalogTest 新套件；FeedAdGuardTest S1 名单断言同步更正为网易新闻真实包名）**；既有 24 套件零回退。
- **安全红线零变化**：SafetyGuard 终闸/语义断言/预算封顶/点击坐标面全保持——本轮全部是数据级校准，零点击行为面变化。
- **性能零变化**：零新增扫描 / 零新增事件订阅 / 零新增轮询。
- **APK**：844KB · VC 79200（> 79100 覆盖安装即升级）· MD5 54179018 · SHA-256 14afc65b · 三目录分发（android-app/dist + dist + download）。

## v7.9.1（2026-11-20）—— 任务状态机轮：重复点击/误触/合法重试三难根治 + 订阅命中 0 根治 + 三 app 漏检修复

- **本轮方法论（用户指令「先证明问题在哪里，再修改代码——不用关闭重试掩盖状态机问题、不用加扫描频率掩盖识别问题」）**：完整审计点击执行链路（任务创建 → 候选生成/评分 → SafetyGuard 终闸 → 无障碍代点/真实触点双通道 → 500ms 复验 → 候选轮换 → 重试调度 → 反馈面板 → 状态清理 → 异步取消），并做 v7.3.0/v7.6.0/v7.9.0 三版 git 回归考古。实锤根因四条（均有文件级证据）：
  - **根因①（无任务实体）**：引擎没有「广告任务」概念——状态散落在十余张 per-package HashMap（clickTries/gaveUp/lastClickAt/skipAt/adClosedAt/chainKilledAt/firedTarget*/lastGestureOkAt）。「成功」的唯一信号是 TYPE_WINDOW_STATE_CHANGED 的窗口类切换（AdSkipService:1693）——**对 Activity 不变的插屏/覆盖层/Dialog 广告（用户第五轮强调的核心场景）成功信号永不触发**，状态永不重置、任务永不终止。
  - **根因②（反馈面板对插屏路径完全盲）**：handleFeedbackSheet 只接在 tryScan 内；maybeInterstitialScan（1.5s 节流独立扫描链）从不查反馈面板。真机日志实锤链（18:39:02~18:39:04）：inter_close 首击成功 → 反馈面板弹出（tryScan 路径已「停用重试」）→ 面板/残留 UI 里「我为什么会看到广告」语境词（含「广告」）+ 面板自身「×/关闭」候选再次过闸 → 2 秒后第二击落在普通按钮（情况 A/B 的直接机制）。
  - **根因③（订阅 needSub 饥饿）**：git 考古实锤——v7.5.0/v7.7.0 探针词扩容（+skip/+关闭广告/+關閉廣告）后探针先中 → hits 非空 → 第 0 击 needSub=false → matchSources **整窗不被咨询**（命中计数在收集侧——「0 命中」= 从未被调用）。订阅内核（Subscription/GkdSelector/RuleRouter）v7.3.0→v7.9.0 零改动，回归全在调用方。
  - **根因④（静默语义盲）**：ting.android MainActivity 8 节点零内容事件——contentAt 空 → silenceMs=-1 → videoLike 永不成立 → 20s 观察窗/1.5s 轮询针永不开启 → 长视频开屏（>12s 露头按钮）整窗漏拦。
- **广告任务状态机（新核心模块 core/AdTaskCore.java——纯 Java 零 Android 依赖，与 InterstitialGuard 同规约）**：
  - **九态**：DETECTED / READY / CLICKING / VERIFYING / RETRY_WAIT / FEEDBACK / SUCCESS / FAILED / CANCELLED——终态三态不可逆（isTerminal）。
  - **唯一任务 ID**：每包每窗一个当前任务（winStart 窗代 + seq 序号双因子身份——窗口刷新/探针触发/异步回调/点击通道切换都不产生重复任务）。
  - **成功不可逆**：SUCCESS 后 clickAllowed/callbackAllowed 全 false——该任务后续一切点击请求与过期回调一律拒收；终态落账同步杀链（chainKilledAt）+ 静默期（adClosedAt）+ 新任务冷却（taskAllowsNew 2.2s——广告 detach 残留期/反馈面板期/跳转动画期的残留「广告」语境+「×/关闭」候选不是新广告）。
  - **反馈面板独立流程**：FEEDBACK 态绝不对原广告再执行关闭（只代点「取消」收起——绝不替用户提交反馈）；面板收起 → SUCCESS（面板只在广告关闭后弹出——铁证）。
  - **合法重试保护（情况 C 不误杀）**：RETRY_WAIT/VERIFYING 态照常放行（复验扫描重新见到候选 = 合法重试）；重试前置检查表五要件（广告仍在场/同窗/候选存活/候选可见/关闭语义）任一不满足不得对旧坐标出手；预算用尽 → FAILED（绝不无限重试）。
  - **双通道统一**：真实触点（gestureTap）与无障碍代点（performAction）都经 execute() 唯一入口派发、统一 taskOnDispatch 记账（→ VERIFYING）——通道切换不产生重复任务，两通道绝不并发。
  - **视频新键解禁**：视频末露头的文本新指纹键 = 新按钮（FAILED 任务合法解禁证据，预算刷新=开新任务——v7.8.0 视频开屏能力零回退；角落/手势候选绝不刷新，误触红线）。
- **就地 SUCCESS 验证（第三阶段重设计——500ms 复验保留审计结论：节奏正确但缺判定）**：出手后复验扫描两轮零候选（≥350ms 独立两轮——树刷新瞬间候选抖动不误判）→ 不可逆 SUCCESS；单轮零只观察不补刀（「页面变化但无法确认广告是否关闭」→ 绝不对旧坐标第二击）；候选再现即重置（合法重试保护）。插屏路径 >12s 窗龄场景（tryScan 的 splashLive 早退）由插屏扫描自身承担验证（interSawCandidate 观测面）。500ms 首击复验节奏（HumanTap.verifyDelayMs 人手节奏）零变化——审计结论：不删不改，只补「判定」。
- **过期回调全面验活（第四阶段验收 7）**：复验针 scheduleVerifyScan 带任务 ID（窗代+序号——任务终态/换代一律作废）；TapGestureCallback.onCancelled 的 350ms 补扫带代际+任务双护栏（旧版无任何护栏）；返回键 600ms 复验带代际+任务护栏；视频轮询针终态降频 1.5s→3s（广告已关后「轮询针继续全量复扫再出手」根治——观察在岗出手拒收；新实例冷却过后恢复 1.5s 节奏）。
- **订阅命中 0 根治（第五阶段）**：①反饥饿——每窗首次扫描无条件咨询一次 matchSources（subConsultedWin 每窗一次记账；v7.3.0 行为恢复；成本契约：每窗一次 ≤400 节点 DFS 与 v7.3.0 相同）；②七分类统计——notLoaded（未加载）/ noCover（应用不匹配）/ noMatch（页面选择器不匹配——activityIds 限定/选择器链/形态闸/限流）/ hit（规则命中）/ blocked（被安全闸拦截）/ clickFail（双通道派发全失败）/ clickOk（点击执行成功）——stateJson subStat 字段 + 上传日志「订阅七分类」行（订阅页与远程日志直接可读归因——不再把所有未命中都统计为订阅未命中）。
- **三 app 漏检修复（第六阶段调查结论落地）**：
  - **ting.android**：静默语义归一——零内容事件（SurfaceView/TextureView 视频在放的观测铁证）旧版传 -1 → 归一为窗口全程静默（silence=elapsed，与 logScanMiss「零内容事件」诊断字段同口径）→ videoLike 可成立 → 20s 观察窗 + 1.5s 轮询针对长视频开屏开岗；树持续有事件的窗（静默 <1.6s）不受影响（功耗契约不变——arm-hint 的 silence<0 达标分支与归一后语义一致）。
  - **taobao TMSActivity**：TMS 营销容器收编 SDK 广告窗指纹（isAdActivityClassScan 段级 tmsactivity——阿里系广告/营销落地页约定命名，业务 App 窗口绝无此命名）→ wctx.sdkAdWin=true → 插屏闸⓪第三路在场证据 + 图标关闭钮路径语境闸接通（WebView 无文字窗的纯图标 × 钮可出手）。
  - **微信朋友圈 ImproveSnsTimelineUI**：订阅反饥饿后远程 GKD 订阅（932 款含 mm SnsTimeLineUI 朋友圈广告关闭规则）重新参与匹配——此前被探针命中饿死整窗不咨询；不盲扩角标词表（朋友圈正常内容「图片/评论/头像/实况」绝不当广告——宁漏勿误红线）。
- **插屏出手日志诚实化**：「× 关闭钮出手（语境闸通过）→ 500ms 后复验」——「本条广告关闭」只在复验确认（任务 SUCCESS/窗口切换复验通过）后打出（v7.9.0 出手瞬间宣称已关闭、两秒后第二击同屏出现——用户实锤的口径矛盾根治）。
- **性能契约（本轮改动功耗面全审）**：①任务门/验活全为内存布尔判定零 IPC 零扫描；②两轮零候选确认针每任务最多一针（+1 次扫描/窗——点击已发生）；③订阅每窗一次咨询与 v7.3.0 相同（探针/规则命中路径零变化）；④视频轮询终态降频 1.5s→3s（广告已关后轮询针功耗减半）；⑤反馈面板闸复用既有 roots 收集零新增扫描；⑥静默归一零成本（纯参数归一）。省电档案/事件订阅/CLEAN 探针哨兵全保持。
- **测试（+83 项断言，全绿 24 套件 6197 项——v7.9.0 为 6114）**：新增 AdTaskTest 套件：S1 终态不可逆与点击/回调门（14）/ S2 状态转移真值表（21）/ S3 反馈面板独立流程（7）/ S4 合法重试保护与预算钳制（9——情况 C 完整链仿真：第 1 击失败→RETRY_WAIT→第 2 击→SUCCESS→第 3 击被拒）/ S5 重试前置检查表（8）/ S6 终态冷却闸与视频新键解禁（9）/ S7 两轮零候选就地确认（6）/ S8 轮询终态降频契约（6）。既有 23 套件零改动全绿（回归零破坏）。
- 工程化：ECJ 全量编译零错误（核心 + 测试）；APK 844KB · VC 79100（>79000 覆盖安装即升级）· 三目录分发（android-app/dist · 仓库 dist）；包内资产语义闸（check-appjs.sh）同验通过。

## v7.9.0（2026-11-20）—— 源码审计轮：三处接线盲区根治 + 图标关闭钮路径 + 视频画像竞速根治

- **本轮方法论（用户指令「先找根因再改代码 · 先修架构再加规则」）**：完整审计 AdSkipService（5236 行）/ InterstitialGuard / VideoSplashGuard / AdDecisionCore 全链路（事件路由 → 窗口收集 → Node Tree 遍历 → 候选 → 规则匹配 → 评分 → 安全门 → 点击 → 复验），实锤 **v7.8.0 的三处「纯函数单测全绿、服务侧零接线」盲区**——纯函数层被单测保护得很好，服务侧装配层没有断言覆盖，三处死线全是装配缺陷（不是算法缺陷）：
  - **盲区①（闸⓪第三路 SDK 插屏窗指纹死线）**：`maybeInterstitialScan` 从未给 `wctx.sdkAdWin` 赋值（Ctx 默认 false）→ `decideFromCandidates(..., wctx.sdkAdWin)` 传的永远是 false——v7.8.0 新增的「SDK 插屏窗指纹」在场证据（窗类即广告窗）整路死代码：**无语境词、无 WebView 的原生 SDK 插屏**三路语境全空 → 永不出手。**根治**：`wctx.sdkAdWin = AdDecisionCore.isAdActivityClass(cls)` 接通（与 tryScan 主路径同源同判）。
  - **盲区②（SDK 窗零候选 streak 冻结）**：零候选退避块的豁免窗（广告窗/Dialog/学习窗）**跳过整个计数块** → `zeroCandScans` 冻结在 0——`videoLike` 的 sdkAdWin 路径（streak≥3）在 SDK 广告窗上**永远建立不起来**：SDK 视频开屏（按钮常在视频末才挂树）观察窗不延长、轮询针不排。**根治**：streak **无条件计数**（豁免只挡杀链不挡计数——计数是 videoLike 画像的观测源；防膨胀上限 96 + 候选出现即清零原契约保持）。
  - **盲区③（外来包 Dialog 插屏卫士未拉起）**：非前台 Dialog 分支只走 nudgePopupScan（弹窗识别路径——话术门识别营销弹窗），**插屏卫士从未被拉起**——广告 SDK 独立包的 Dialog 插窗（如 com.qq.e.ads 家族 Dialog 形态，事件 pkg 是 SDK 包而非宿主）整窗漏拦。**根治**：同分支接线 `maybeInterstitialScan(pkg, now)`（自带 1.5s 节流）+ winClass 登记（此分支提前 return 从未登记窗类 → SDK 指纹判定拿到空串恒 false 的连带缺陷一并补齐）。
- **图标关闭钮路径（用户实锤：「关闭按钮是图片」「关闭按钮只有图标没有文字」——纯函数新模块 InterstitialGuard.decideIconFromCandidates）**：文字候选（nativeFindTexts）对纯图标钮（零文本零 desc）零候选——本轮新增无文字钮路径：
  - **语境闸（iconContextOk，门槛比文字路径更严）**：SDK 插屏窗指纹在场，或 WebView 容器在场且广告角标同窗（「广告」语境词可见）→ 放行；**仅语境词在场（无 WebView/无 SDK 窗）绝不点纯图标**——普通窗角落的菜单/分享/设置图标误触面不可控（宁可漏拦绝不误触红线：图标无语义证据，语境门槛必须加倍）。
  - **形态闸**：closeFormOk 三路并集（≤30%×≤12% / 宽扁 ≤55%×≤7% / 小面积 ≤4%）+ 图标级面积 ≤2% 屏（真关闭图标钮是小图标；菜单容器/广告缩略图常 >2%）。
  - **位置闸**：顶部带（cy≤25% 屏高——关闭图标钮常态位）优先、底部带（cy≥75%）次选、**中部图标绝不收**（tabBar/菜单图标高发区）；strongOnly（中部边带候选）绝不收；顶带多候选取最靠上者。
  - **终闸零改动**：FeedDecision.label="×"（图标语义）——interBoundaryCheck 对 "×" 本就放行（与文字 × 钮同一闸），语义/形态/场景三重断言全复用，execute() 既有防线（用户让路/耐心预算/多点防线/人手手势/点击梯子）全量复用。
- **视频画像竞速根治（VideoSplashGuard.videoArmHint——树慢速构建窗）**：WebView/H5 视频容器树慢速构建（内容事件持续到 ~2s）时，零候选退避杀链（streak≥3 + 窗龄≥2.6s）**早于**静默≥1.6s 的全画像成立——链死 → 静默采样断供 → videoLike 建立不起来 → 轮询针永不排。**根治**：arm-hint（streak≥3 + 窗龄≥2.2s + 静默≥0.8s）窗免杀链——多守 0.8s 让全画像有机会成立；树持续有事件的窗（静默 <0.8s）不豁免，退避照杀（功耗红线：arm-hint 只多守半步，不无限观察）。
- **视频轮询针探针词 5→10 + 每 3 针全量复扫（VideoSplashGuard.VIDEO_POLL_PROBE_WORDS / pollWalkDue）**：旧探针 5 词（跳过/跳過/skip/关闭广告/關閉廣告）对图内「×」文本钮、拉丁 close 钮零命中 → 探针不中 → 全量 tryScan 不拉起。**扩容**：+关闭/+close/+×/+✕/+✖（裸「X/x」不收——contains 搜索英文任意含 x 词全命中，噪声面交给全量闸）。**每 3 针一次全量复扫**：12~20s 段纯图片钮零文字零探针命中——walk 角落候选路径是唯一活路（全量闸照常把关，预算 600 与 nudge 同档；20s 观察窗内 ≤5 次全量——功耗红线）。
- **性能契约（不牺牲功耗换命中率——本轮全部新增路径的功耗边界）**：①图标钮路径只在既有插屏扫描节流内决策（1.5s/次——零新增扫描）；②arm-hint 只豁免杀链不新增档位（树持续有事件的窗照杀）；③全量复扫只在 videoLike 窗（普通窗零轮询零复扫）；④探针是系统原生 C++ 子树搜索（零 DFS）；⑤事件面订阅零变化（typeViewScrolled 评估后保持不订——滚动事件高频低收益，内容变化事件已驱动扫描）。
- **测试（+51 项断言，全绿 23 套件 6114 项——v7.8.0 为 6063）**：VideoSplashTest S9 画像竞速（arm-hint 真值表 7 项 + 竞速时间线仿真 4 项：v7.8.0 同窗 2.6s 杀链对照 / arm-hint 守链连续活到 3.4s 全画像成立 / 静默边界精确）/ S10 探针词 + 全量复扫（10 词契约 + pollWalkDue 周期 + 功耗上限 ≤5 次/20s）；InterstitialGuardTest S9 图标钮真值表（语境闸 5 项 + 决策 9 项：顶角钮出手/tapBounds 逐位/中部拒/strongOnly 拒/面积 2% 双向/顶带优先/终闸一致性/零强语境拒/常量契约）；InterstitialRoutingTest S10 接线契约（SDK 指纹全族 4 命中 + MainActivity/SplashActivity 红线不破 + 外来包 Dialog 节流 + streak 契约 + arm-hint 在场）。
- 工程化：ECJ 全量编译零错误（核心 + 测试）；APK 836KB（853,558 B）· VC 79000（>78000 覆盖安装即升级）· MD5 `b4b7f9e8f5f5200ec269f46e14bc917a` · SHA-256 `c8053c27e37bf1205c84a3ca0e0584452c55606c536eb0f5d24e9a6fda50a5d2` 三目录分发（android-app/dist · 仓库 dist · GitHub Release asset——包内资产语义闸同验）；GitHub tag v7.9.0 + Release + 仓库描述同步 v7.9.0 主题 + 免责声明保持。

## v7.8.0（2026-11-20）—— 视频开屏卫士 + 插屏重扫路由根治 + C 语言基准实测（五语言同构）+ 机型矩阵 396（十维）

- **视频开屏卫士（用户十八轮实锤原话：「很多没命中，只要是开屏广告带视频的你都未命中」）——四死因链一起根治**：
  - **死因①（12s 观察窗天花板）**：视频开屏（穿山甲/优量汇/快手视频开屏）跳过钮常在视频末 5~15s 才露头，WINDOW_MS=12000 硬封顶——>12s 露头的按钮引擎已出窗判 CLEAN「不再扫」。**根治**：观察窗自适应延长 `VideoSplashGuard.windowDeadlineMs(videoLike)` → 20s（VIDEO_EXT_WINDOW_MS——晚露头按钮有人扫；普通开屏窗 12s 契约零变化）
  - **死因②（静默事件源）**：视频在 SurfaceView/TextureView 里播放、进度环 Canvas 自绘——零 TYPE_WINDOW_CONTENT_CHANGED → 零候选退避（3 次+2.6s）杀链后零事件零复活。**根治**：视频画像 `videoLike`（窗龄 ≥2.2s + 连续零候选 ≥3 + 树静默 ≥1.6s——SurfaceView 在放的观测面；或 SDK 广告窗 + 零候选 ≥3）+ **1.5s 轮询针**（探针直查零 DFS——静默事件源兜底，12~20s 段 ≥5 针覆盖固定梯子 18 档止于 12s 的盲区）+ 退避豁免（videoLike 窗零候选退避/内容页早退双双豁免）
  - **死因③（预算烧穿）**：图片跳过键（进度环无数字 → cdN=0 → 门控直通）从 300ms 起 3 击连发 1 秒烧光预算，按钮真激活（视频播完）时引擎已 gaveUp 收手。**根治**：白点节流 ≥1.5s/击（VIDEO_STRIKE_GAP_MS）+ 预算 3→5（VIDEO_MAX_CLICKS——预算拉长 ≥7.5s 覆盖门控期）+ **新键预算刷新** `budgetRefresh`（文本键新指纹目标 → 点击梯子重置——视频末露头的真「跳过」键是新按钮不是白点；角落/手势候选永不刷新——防移动目标无限预算）
  - **死因④（L4 形态确认门）**：第一方视频开屏窗无角标/无热区/非 SDK 指纹、树 >60（WebView 视频容器常态 >150）→ 角落候选收集了永不出手；CORNER_MAX_MS=6500 关门。**根治**：videoLike 入强广告形态位 + 树上限 150→260（VIDEO_CORNER_TREE_MAX）+ 时机窗 6.5s→20s
  - 视频开屏键词典：「5s 跳过」「5秒跳过」无「后」变体收编（P_COUNTDOWN_HEAD 后改可选——旧正则「后」必填全拒）；画像/轮询命中/新键三类日志进扫描日志（归因面闭环）
- **插屏重扫路由根治（用户实锤原话：「app内插入广告也不会拦截关闭」第 N 轮——四轮 v7.4~v7.7 全在词表/闸门打转，扫描调度从未动过）——五死因链一起根治**：
  - **死因①（重扫路由黑洞）**：内容事件在开屏窗期（<12s）全部改道 nudgeScan 后 break、弹窗窗期改道 nudgePopupScan 后 break——插屏常在 App 打开后 3~5s 弹出（恰在开屏窗期内！）首扫后同窗再无第二次扫描。**根治**：路由黑洞解除（两个分支内直接加 maybeInterstitialScan——自带 1.5s 节流）+ INTER_SCAN_THROTTLE_MS 2500→1500（插屏生命周期内 4~6 眼）
  - **死因②（首扫太早）**：窗切换帧插层刚创建、文字节点 200~800ms 后才进树——首扫空手后无复扫。**根治**：延后复扫 +450ms（INTER_RETRY_DELAY_MS·清节流记录立即可扫·每窗仅一次 interRetriedWin 守门 + 代际护栏）
  - **死因③（同窗插层无人扫）**：无焦点 Dialog/PopupWindow/overlay 挂载不产生任何被订阅事件。**根治**：补订 typeWindowsChanged（服务配置 + onAccessibilityEvent 新 case——新窗挂载拉起插屏扫描，前台包限定）
  - **死因④（闸⓪语境面窄）**：必须 ≤10 字原生文字节点——H5 插屏（角标在网页内核）、SDK 插屏窗（窗类即铁证）、11 字披露标签全拒。**根治**：语境三路并集（①语境词 contains ≤16 字 + 繁体廣告 ②WebView 容器在场 walk 收集 f.webviewPresent——李跳跳同款 H5 广告关闭面 ③SDK 插屏窗指纹）
  - **死因⑤（闸①②杀变体）**：拉丁 X 单字符/「点击关闭/立即关闭」带帽变体被精确等值拒；宽扁横条钮（残忍离开 40~60% 宽但极扁）被 closeFormOk 双杀。**根治**：词族收编（X/x 单字符 + 后缀族 ≤6 字 + 前缀族 ≤3 字——「取消」仍拒）+ 形态三路并集（原版 + 宽扁 ≤55%×≤7% + 小面积 ≤4% 屏——大容器仍全拒）+ 终闸一致性（双杀解除）+ feed 名单 20 款插屏孤儿化解除（P1X 语境词收编裸「广告/廣告/推广」）
- **C 语言基准实测（用户问：「卫士用c语言写的话会不会更好呢」——五语言同构基准以数据回答）**：bench.c 与 Java/Kotlin/JS/Python 完全同构（xorshift31 同树同规则同命中），**校验和逐位一致（sel_sink=731 · hits=20000 · sink=485562）**。结果：C 稳态 optimized **261.8ns（快 Java 4.1×）**、boot 1.7ms、RSS 1.9MB（常驻优势）；但均为 µs 级——事件面节流 80ms 级，**5 个数量级余量下语言性能差异对命中率零贡献**；linear 模式 C 反比 Java 慢 5×（glibc regcomp 每次 ~13µs 比 Java Pattern.compile 慢——诚实记录）；AccessibilityNodeInfo 是 Java API——**C 必须经 JNI 回 Java 才能摸到无障碍树，重写全引擎换不到任何命中率收益。结论：保持 Java**（本轮命中率修复全在 Java 层完成即明证）
- **机型矩阵 352→396 + 第十维视频开屏（「拓展设备型号和品牌以及系统」）**：11 组 × 36 机型（每品牌 +4 · 2027-2028 世代：小米 19 系/HyperOS 5 · Redmi K130/Turbo 7/Note 19 Pro+ · POCO F12/X12/M8 · Mate 90/Pura 90/HarmonyOS 6 · Magic10/荣耀 500/MagicOS 13 · Find X11/Reno 15/ColorOS 19 · 真我 GT10/Neo8 · vivo X320/S31/OriginOS 9 · iQOO 23/Neo 15/Z15 · S28/A17/Z Fold 8/One UI 11 · 一加 19/Ace 10/Nord 8）；**第十维视频开屏逐机型验证**（4 项：画像真值表/观察窗延长/视频窗角落键 1:1 缩放命中（200 节点树 + 8s 时机——L4 放宽）/白点节流+新键刷新）；品牌面保持用户点名九品牌 + 子品牌（OPPO/vivo/小米/三星/华为/荣耀/红米/一加/iQOO + POCO/真我）
- 工程化：全仓 **23 套件 6063 项 JVM 断言全绿（v7.7.0 为 5099 · +964：BrandFocus396Test 4476（+848）+ VideoSplashTest 51 + InterstitialRoutingTest 56 + InterstitialGuardTest 93→102）**；APK 833KB（853,558 B）· VC 78000（>77000 覆盖安装即升级）· MD5 `5fba91c1a1e34d7dfc96c0b4725bf584` · SHA-256 `59761b7e58df33e1eedae8b3bb5619439f09a529e0708102b51d20a96d2cd96a` 三目录分发（包内资产抽出后语义闸同验）；GitHub tag v7.8.0 + Release + 仓库描述同步 v7.8.0 主题 + 免责声明保持 · 实录见 round-40.json

## v7.7.0（2026-11-19）—— 回切开屏根治 + Toast 常驻修复 + 机型矩阵 352（九维）

- **回切开屏根治（用户十七轮实锤原话：「扫描日志从另一个app切回这个app，期间会弹广告，这个没有拦截到」）——两个死因一起根治**：
  - **死因①（entering 误判）**：卫士自家包事件在 onAccessibilityEvent 顶部被整体丢弃（不扫描自家——自家无广告）→ **lastFgPkg 从未登记成卫士包名**。用户「目标 App → 卫士 → 目标 App」往返后，切回目标 App 的窗口事件里 `pkg == lastFgPkg`（还停在目标 App 上）→ `entering=false` 不按「新进入」处理：rootWinCls 不刷新、回切开屏零日志、不重置进入态。**根治**：自家包 WINDOW_STATE_CHANGED 登记 lastFgPkg（前台边界——切回目标 App 时 entering 正确判 true；附带收益：用户停在卫士期间目标 App 的后台内容事件被前置过滤正确丢弃，功耗同时下降）+ **ReentryGuard.isEntering 纯函数**（同包离开 ≥ REENTER_GAP_MS=6s 判回切等价新进入——息屏回亮/白名单应用边界/ROM 窗口事件缺失漏网路径全兜住；6s 阈值低于典型 App 二次开屏触发阈值（后台 >5s 回前台重弹开屏），高于误报面（同 App 内 6s 内窗级抖动绝不误判））
  - **死因②（CLEAN 档案误杀）**：回切开屏常**叠在主界面窗类上**（同一 MainActivity 盖一层开屏容器，窗类不变），而主界面窗类早被 v5.3.0 宿主窗档案判 CLEAN（连续 3 次零候选）→ `bumpScanChain` 只排探针哨兵/低频档 {0,700} → **晚到的跳过键（1~2s 才渲染）与图标式/WebView 式跳过键（原生文本探针探不到）整窗漏拦**——这正是上一轮「回归：开屏广告有的跳不了或非常非常慢」的底层病灶。**根治**：进入期全量门（ReentryGuard.entryFullActive · ENTRY_FULL_MS=4s）——冷启/回切判 entering 的时刻起 4s 内 `profileSuppressScan` 直接让路（CLEAN 档案不抑制，全量 18 档梯子 + 80ms 补扫全开）；激进档语义「宁多扫绝不漏」（进入是低频事件——典型日几十次 × 一轮全量梯子，功耗红线不破）
  - **回切日志记账**：`🔄 回切开屏（pkg）：离开 Ns 后回前台 → 按新进入处理，进入期全量扫描（CLEAN 档案让路）`——30s 包级节流防切来切去刷屏；扫描日志命中/未命中定位线索拓宽（「拓宽广告日志命中跳过率」）
- **Toast 常驻修复（用户十七轮实锤原话：「点击开启弹窗广告屏蔽，下方弹窗屏蔽已开启几个字一直保持在底部」）**：旧实现收尾纯靠 JS `setTimeout(2200ms)` 改 opacity，两个真机失败面——①同步桥调用（setStateBool/kvGet 走 JavaBridge 线程）卡住 JS 线程时计时器不触发 → 永不收尾；②内联 cssText transition + inline opacity 组合在部分 OEM WebView 渲染失灵 → opacity:0 改了不重绘。**根治（三重保险）**：① CSS 关键帧动画 `toastFade`（animation-fill-mode:forwards——动画跑在合成器线程，JS 线程被卡住也照常走完）；② 终态 `opacity:0 + visibility:hidden` 双保险（opacity 合成怪癖也不可见）；③ JS 兜底计时器 2600ms 摘 `.show` 类保持（动画与计时器双通道任一活着都能收尾；不支持动画的远古 WebView 退化正常——.show 类直接声明可见样式）。三连点重放动画（强制回流 void offsetWidth）真机语义不变；浏览器端实测（agent-browser）：三连点 mid-anim opacity=1/anim=toastFade → 3.2s 后 opacity=0 + visibility=hidden + 类已摘
- **命中跳过率拓宽（「进一步拓宽广告日志命中跳过率」）**：探针词扩「关闭广告/關閉廣告」（切回 App 期间弹的插层/回切开屏常以整词作键——「跳过」族探针探不到）——探针词 3→5（跳过/跳過/skip/关闭广告/關閉廣告，前三词顺序保持命中率优先）；繁体词典补收（TEXT_EXACT 增「關閉廣告/關閉此廣告」、SKIP_TEXTS 增「關閉廣告」——繁体区 App 与出海 SDK 开屏）；matchText 全套规则+形状门照常逐候选验（激进化只放宽「找到」面，不放宽「可点」面）
- **机型矩阵 308→352 + 第九维回切开屏（「优化模拟各个品牌和机型进行测试修复，拓展设备型号和品牌以及系统」）**：11 组 × 32 机型（每品牌 +4 · 2027 世代：小米 19 Ultra/Civi 6 Pro/平板 9 Pro · Redmi K120 Pro/Turbo 6/Note 18 Pro/17 · POCO F11 Ultra/X11 Pro/M9 Pro/C80 · Mate 90 RS/Mate 90/Pura 95/nova 16 · Magic10 至臻版/荣耀 600/Magic Pad 3/X80 · Find X10 Ultra/X10/Reno 16/A6 · 真我 GT9 Ultra/GT9/Neo11/V80 · vivo X310 Ultra/X310/S41/Pad 4 Pro · iQOO 21/Neo 14/Z14/Z14 Turbo · S27+/S27/Z Flip9/Tab S12 · 一加 18 Pro/Ace 8 Pro/Ace 8V/Nord 7）；**第九维回切开屏逐机型验证**（4 项：回切判定真值表/进入期全量门/主界面窗类叠开屏层仍命中/插层键词典——按机型分辨率 1:1 缩放）
- **新增 ReentrySplashTest 专项套件（25 项）**：S1 回切判定真值表（空 lastFgPkg/包名不同/5.999s 抖动/6s 边界含/从未登记/空包名防 NPE 面）/ S2 进入期全量门（4s 内让路·期外档案自治·常量契约 6000/4000）/ S3 自家包前台边界时间线（X → 卫士 → X：边界后 entering=true 死因①真值表 + 息屏 10s 回亮判回切）/ S4 全分辨率回切命中（720p→2K+·3:2 平板 12 档全过）/ S5 插层键词典（关闭广告/關閉廣告）/ S6 离开时长记账（reentryAwayMs 语义）/ S7 九品牌几何投影（1:1 缩放跳过键全命中）
- 工程化：全仓 **21 套件 5099 项 JVM 断言全绿（v7.6.1 为 4314 · +785：BrandFocus352Test 3628（含 352×第九维）+ ReentrySplashTest 25）**；APK 832KB（849,462 B）· VC 77000（>76100 覆盖安装即升级）· MD5 `75a7ba4e9527bf841a6afc05d08656c8` · SHA-256 `4eeef88dee87d6730730e251976767a6d7519c36ae0c3842d855f0a2b508d545` 三目录分发（包内资产抽出后语义闸同验）；GitHub tag v7.7.0 + Release + 仓库描述与 README 面板同步 + 免责声明扩充（误触与漏拦边界/回切开屏说明/账号风险与商业用途边界）· 实录见 round-39.json

## v7.6.1（2026-11-19）—— 热修复（页面板块恢复：扫描日志/版本记录/使用报告）+ 构建链页面资产符号校验闸

- **死因（用户十六轮实锤原话：「扫描日志，7.6版本记录没了，使用报告也不见」）**：v7.6.0 发布时页面资产 assets/www/app.js 的版本记录注释被编辑噮坏——块注释 `/* ═══ v7.6.0 版本记录（…）═══` 尾部丢了 ` */` 未闭合，注释吞掉后续 17 行：整个 `const CURRENT_RELEASE = {...}` 声明被吃进注释（在下一个 `*/` 处才闭合）→ 页面渲染到版本记录时抛 ReferenceError 中断 → 扫描日志/版本记录/使用报告三大板块全部消失。**node --check 语法层完全通过**（语法合法、注释在别处闭合）——纯语法闸拦不住这类「语义级损坏」，坏资产直接进了已发布 APK。
- **修复**：闭合版本记录注释（`/* ═══ v7.6.1 版本记录（热修复：页面板块恢复 + 激进模式保持）═══ */`）—— `CURRENT_RELEASE` 声明回到活代码，三大板块（扫描日志/版本记录/使用报告）全部恢复；版本记录内容升级为 v7.6.1 热修复叙事（激进模式全量保持）。
- **防回归闸（页面资产符号校验： scripts/check-appjs.sh + build.sh 打包前硬门）**：语义级三重断言，缺一即拒绝打包：① 语法层——整份源码先过解析（SyntaxError 直接拒）；② 执行层—— catch-all stub 环境（window/document/localStorage 全代理·定时器 no-op 防轮询挂住事件循环）顶层执行整份 app.js，ReferenceError（缺标识符=本死因类）硬拒；③ 符号层——同一执行作用域内断言 5 项关键符号在场（CURRENT_RELEASE 版本记录 / renderLog 扫描日志 / renderReport 使用报告 / renderReleaseNotes 更新说明 / notesToHeadlines 解析器）——被注释吞掉/被删即拒。**死因复现验证**：坑样本（未闭合注释）该闸 5 符号全灭拒打包退出 1；修复样本 5 项全过退出 0；已打包 APK 的包内资产抽出后该闸同样全过——此类损坏从源头拦下，绝不再进 APK。
- **激进模式全量保持**：开屏首击零门控（露头即点·重击等将尽+6s 封顶）/插屏「广告」语境闸 contains/关闭词扩表/终闸语义面收编/新窗首扫提速零改动（Java 侧零触碰）。
- 工程化：全仓 **20 套件 4314 项 JVM 断言全绿保持**；APK 832KB（849,462 B）· VC 76100（>76000 覆盖安装即升级——v7.6.0 坏包直接覆盖修复）· MD5 `d7e4326257a21a5d73d80500cdc6dfec` · SHA-256 `b74cde33df8f6811f164b68a173bf72ad970d04e0763f56e7fd8517a9a5896a0` 三目录分发；GitHub tag v7.6.1 + Release + 仓库描述与 README 面板同步 · 实录见 round-38.json

## v7.6.0（2026-11-19）—— 激进模式（开屏露头即点 + 插屏「广告」语境闸重做）

- **激进模式总开关（用户十五轮实锤原话：「你可以激进一点暂时先放弃宁可漏拦绝不误触」）——开屏速度与插屏命中率优先于一切保守闸**：
  - **开屏首击零门控（「有的还跳不了了，或者非常非常慢」死因）**：v7.5.0 「门控从第 0 击起全次生效」被真机实锤为变慢直接死因——所有带倒计时的开屏全等倒计时走完才出手（5s 广告白看 4.9s），误检数字更把无倒计时按钮按住不动；激进反转：第 0 击零门控跳过键露头即点（绝大多数按钮立即可点，0ms 命中）；首击白点后（attempts≥1）重击才等「将尽」（真倒计时按钮命中时机）；重击等待 6s 封顶（RETRY_GATE_MAX_S——误检大数字不再无限等）；装饰数字逃生（1.6s 未跳）语义保持；纯函数 gateCountdownAggressive/cappedRetryCd（JVM 可单测）
  - **插屏「广告」语境闸 contains 重做（「软件内插入的广告不仅没跳过」死因①）**：v7.5.0 闸① isDisclosureBadge 精确等值+符号尾+形态三闸把「广告 · 了解详情」「查看广告详情」等真机变体全拒在门外（用户实锤「它们几乎都带有广告两个字」——变体千千万万，contains 一网打尽）；新 hasAdContextWord（语境词 contains · ≤10 字可见标签——超长正文虽含「广告」也不算语境节点，contains 面可控）；语境节点只作在场证据绝不点击
  - **插屏关闭词扩表 + 终闸语义面收编（死因②：终闸杀词）**：v7.5.0 INTER_CLOSE_WORDS 收了「知道了/我知道了」但 execute() 走通用 boundaryCheck——「知道了」不含跳过/关闭/skip/close/× → BND_FAIL_SEMANTIC 一票否决（词表收了也白收，出手永远被最后一道闸打回）；新 ComplianceGuard.interBoundaryCheck（插屏专用终闸：场景断言同源 + 语义换源 isInterstitialCloseWord + 形态双验（closeFormOk per-dimension + 面积≤35%））；关闭词扩表：残忍离开/残忍退出/残忍拒绝（游戏插屏标配——几乎是游戏插屏唯一关闭钮文案）/不再看/不看了/暂不需要/不需要/Skip Ad；「取消」激进模式下也保持拒收（误点面不可控）
  - **插屏位置闸激进放宽（死因③：带位闸灭中部关闭钮）**：非滚动窗零位置闸（关闭词精确等值本身已足够特异——用户指令激进）；滚动窗（信息流卡片）保留近旁铁证（与语境节点 |Δcy|≤35% 屏高同排——死因④同时根治：滚动窗不再一票否决，名单外信息流卡片广告关闭钮近旁可出手）；关闭钮形态闸保持（≤30% 屏宽 × ≤12% 屏高——广告主体几何兜底不动摇）
  - **插屏新窗首扫提速**：窗切换即重置包级节流记录（interWinAt 代际——插层出现当帧就扫，不再被旧窗的 2.5s 节流卡住；插层快进快出的场景全收获）
- **多点误点根治（「部分用户设备……导致多点，误点的情况」1:1 仿真）**：激进首击 + 门控重击的时序结构本身就是多点防线——首击唯一立即，重击等将尽（>1.6s）天然满足 F 同目标静默窗（人手双击下限）；MAX_STRIKES_PER_TARGET=2 / MAX_CLICKS_PER_WIN=3 / H 结果静黙 600ms / 冷却 max(300ms, verifyDelayMs) 全保持；AggressiveModeTest S3 1:1 人手时间线（首击→500ms 复验 F 拦→将尽重击≥1.6s 间隔）逐时刻断言
- **扫描日志双样本诊断（「扫描日志继续实现继续优化」数据闭环）**：logInterMiss 样本升级为语境词 + 关闭词双样本（被闸也照登——上传日志直接区分「语境不在场」还是「关闭钮被哪道闸拒」）；随一日三次自动上传
- 工程化：**AggressiveModeTest 42 项**（S1 首击零门控时间线 8 / S2 重击门控与 6s 封顶 9 / S3 1:1 人手多点时间线 12 / S4 九品牌触点钳制 10 / S5 决策梯子不变式 7）新增 + **InterstitialGuardTest 重做为激进版 93 项**（S1 关闭词 33 / S2 优先级 6 / S3 语境在场闸 9 / S4 滚动窗近旁 5 / S5 决策真值表 16 / S6 interBoundaryCheck 终闸 13（含 v7.5.0 终闸杀词死因复现断言）/ S7 常量 6 / S8 九品牌 1:1 缩放 5） + SplashCountdownTest 61 项（含激进门控反转新断言）；全仓 **20 套件 4314 项 JVM 断言全绿（+61）**；APK 832KB（849,462 B）· VC 76000（>75000 覆盖安装即升级）· MD5 `aec9fea95de3790e99f16dfc4ab841ea` · SHA-256 `d4e66b0e693dcf6083123feeaf7c03678b641b4d6d246eb28d80abd9c64374c6` 三目录分发；GitHub tag v7.6.0 + Release + 仓库描述与 README 面板同步 · 实录见 [OPTIMIZATION-v7.6.0.md](OPTIMIZATION-v7.6.0.md)

## v7.5.0（2026-11-19）—— 开屏倒计时/位置解耦 + 插屏卫士（名单外 App 插入广告主动关闭）

- **开屏「好多其他 App 的开屏都跳不了」三死因根治（用户十四轮实锤原话：「开屏按钮跳过不是每个都带倒计时，不是每次每个按钮都是一个位置」）**：
  - **兄弟节点倒计时**：穿山甲族「跳过」文案与倒计时数字【分属两个节点】——`parseCountdown(label)=0` → 首击立即出手白点，3 击耐心预算（MAX_CLICKS_PER_WIN=3）烧完 → 优雅收手干看广告走完（5s 倒计时：0ms/1.6s/3.2s 三击全白点）。根治：walk 收集独立数字候选（「3/5s/3秒」小形态，CdDigit 表·≤6 条）在倒计时区（顶 35%/底 25%）或跳过候选近旁（|Δcy|≤25% 屏高）→ 并入 `f.countdownN`；规则路径 `RuleEngine.windowCountdownHint`（200 节点预算 DFS + 命中键近旁过滤）双接入——首击不再白点，门控到「将尽」出手即命中
  - **门控第 0 击起全次生效**：旧 `countdownWait` 只在 `clickAttempts>=1` 才门控——首击永远立即出手（白点还占冷却烧耐心预算，且落进广告热区的无效触点可能被计为广告点击）；抽纯函数 `countdownUntil/decorativeCountdown/gateCountdown`（AdDecisionCore·JVM 可单测）从第 0 击起全次门控；装饰数字逃生（锚点 1.6s 未刷新——真倒计时每秒跳重置锚点）对任何击数都放行（含窗内早见晚点场景）
  - **中部边带图片跳过键（位置解耦）**：旧角落几何（isCornerControl）只认顶 14%/底 18% 两带 + 左右缘——左/右缘【中部】（y 20%~80%）的无字小可点控件零收集（图片跳过键真机随机位置）。新增 `isMidEdgeControl`（左/右缘 ≥72%/≤28% 宽 · 任意高度 · 24px~35% 宽 × 20% 高），`CornerCand.strongOnly` 分层门控：中部候选【仅强广告形态】（热区/角标/SDK 窗/学习窗）可出手，树极简 ≤60 兜底不适用（FAB/普通悬浮钮误点防线——宁漏勿误红线不放松）；L4 逐候选分层（无中部候选时与旧「第 N 击第 N 候选」逐字等价）；`learnableSkipShape` 同步收编（手势候选可学习可代点）
- **插屏卫士 InterstitialGuard（v7.5.0 新模块 · 名单外 App 插入广告主动关闭——用户实锤原话：「app 内的插入的广告也是，它们几乎都带有广告两个字」）**：
  - **覆盖缺口**：信息流两段式（v7.1~v7.4）只在 20 款名单内生效——名单外 App 的插入广告（文章页间/关卡间插卡、半屏/全屏插层）旧行为零覆盖（feed 扫描被名单门关死 · 开屏阶梯 12s 窗外零动作 · 弹窗路径只认 Dialog 窗类——插层常是宿主窗内覆盖层，三路全漏）
  - **「广告」两个字是最稳定的通用指纹**（国法要求的合规披露标签，与 App 名单/窗口形态无关）：闸⓪ 角标在场（`FeedAdGuard.isDisclosureBadge` 语义+形态闸：精确等值或角标词+纯符号尾 · ≤35% 屏宽 × ≤8% 屏高）——角标不在场绝不出手；角标自身绝不点击（点角标=点进广告主体）
  - **关闭钮三闸**：闸① 语义（INTER_CLOSE_WORDS 精确等值·数组序即优先级：×/✕/✖/╳/⨯ > 关闭/关闭广告/关闭此广告 > 跳过/跳过广告/跳过此广告/跳過/跳過廣告/Skip > 知道了/我知道了/close——「进入广告/立即下载」类营销文案绝不中；拉丁 X 不收）；闸② 形态（≤30% 屏宽 × ≤12% 屏高——广告主体大图绝不可能是这个形态）；闸③ 近旁/带位（与角标 |Δcy|≤35% 屏高同排铁证 或 关闭钮在顶带 cy≤30%/底带 cy≥70%——插屏 × 常驻地）；闸④ 可滚动窗拒（信息流/正文页归两段式与开屏阶梯——双重出手防线）
  - **李跳跳方法论主路径**：`findAccessibilityNodeInfosByText` 原生直查（角标词 + 关闭词一把直查·预算无关——深树插层也查得到），候选交语义+形态+近旁闸逐个判定；walk 只取滚动标记（闸④）；宁漏勿误红线与 DFS 路径完全一致
  - **事件驱动接线**（AdSkipService.maybeInterstitialScan）：窗切换 + 内容变化（开屏窗/弹窗窗都不在期的内容变化正是插层滑动入场源）双钩子 · 2.5s 包级节流 · feed 名单 20 款排除（两段式领地）· armed 指纹窗让路 · execute() 既有防线全量复用（用户让路/耐心预算/F·H 多点防线/人手手势派发/通用边界终闸——×/关闭/跳过族全在「关闭方向」语义面内）；关闭记账 `stats.feed` 档（App 内广告关闭口径——报告饼图第 5 档）
- **决策阶梯 L1.5 角标在场关闭（插屏签名）**：`foundClose + adBadge + 广告形态树（≤150 节点·无滚动·无输入框·非长文本）` → 任意位置出手（reason=badge_close）——旧 L1.6 顶部 35% 位置闸把插屏右下/中部 × 全灭（位置不再是证据，角标成为第二证据）；长文本/可滚动窗照旧拒
- **扫描日志插屏未命中诊断（「扫描日志继续实现继续优化」的数据闭环）**：`logInterMiss`（30s 包级节流）带窗类 + 角标词命中样本（`candSamples`——被闸也照登，与 feed 未命中同规约）——「角标/关闭钮未同场过闸」直接读出真机形态，上传日志即可定位缺哪种关闭词
- **开屏探针词扩「skip」**：nativeTextProbe 第三探针（英文 Skip 键旧版只能等 DFS 预算路径深树晚到——国际 App/出海 SDK 开屏补盲；matchText 全套规则+形状门照常逐候选验）
- 工程化：**InterstitialGuardTest 83 项**（S1 关闭词语义 25 / S2 优先级 5 / S3 形态 6 / S4 近旁带位 6 / S5 决策真值表 12 / S6 常量 7 / S7 九品牌分辨率 1:1 缩放 18=9 品牌×2 路 / S8 真机插屏文案重建 4——新闻 App 插屏×CTA 主体拒/游戏过关插屏跳过钮/工具 App 半屏插卡关闭/相册反例零出手）+ **SplashCountdownTest 52 项**（S1 独立数字解析 16 / S2 门控纯函数 9 / S3 中部边带几何 8 / S4 walk 兄弟倒计时 6 / S5 窗级提示 4 / S6 L1.5 角标关闭 5 / S7 strongOnly 分层 4）新增；全仓 **19 套件 4253 项 JVM 断言全绿（+135）**；APK 832KB（849,462 B）· VC 75000（>74000 覆盖安装即升级）· MD5 `4cfd2d7ecf008a05ccb39ae8df5e9049` · SHA-256 `f34d8eec63cd4025b39e7737aa8bdc57c67bc87a48176377f3d472c74aed4268` 三目录分发；GitHub tag v7.5.0 + Release + 仓库描述与 README 面板同步 · 实录见 [OPTIMIZATION-v7.5.0.md](OPTIMIZATION-v7.5.0.md)

## v7.4.0（2026-11-19）—— 信息流广告主动关闭（三路真机根治：P0 面板直通 · P1X 卡片直关 · 空闲巡检）

- **「其他 app 内的广告还是主动关闭不了」三路真机证据链根治（第十三轮实锤：7 张截图 + 同窗上传引擎日志 #79/#80/#81）**：
  - **P0 面板直通（闸⑤）**：微信「关闭该广告的原因」面板主按钮「直接关闭」置顶收编（日志 #79 20:15:23 候选样本实锤在树：直接关闭@(79%,46%)）——旧中性项表【根本没有这词】= 面板拉起了也永远收不掉；面板特征在场（「直接关闭」精确等值 + 标题「关闭该广告的原因/关闭此广告的原因」精确等值 或 原因族「相关广告/相同素材/类似产品」contains 证据）即点主按钮收本条——【不依赖 P1 开火】，角标识别失败也收得掉；覆盖窗门（Dialog/FrameLayout 类非 Activity/UI 整窗）+ 90s 信息流上下文窗（P0_FEED_CTX_MS=90000）；P0 是 userYield 用户让路红线的【唯一豁免】（用户手拉面板正是「广告关不掉只能手动关」的求救信号，代点主按钮与用户意图同向）；裸「关闭」绝不在 P0 路上代点；巨横幅 CTA 形态闸拒
  - **P1X 卡片直关（闸⑥）**：ZOL/小米应用商店「来自广告推荐」卡片角上「×/关闭」直关钮直点即关（日志 #80 20:16:55 实锤：market 中你手动点击了「关闭」——引擎本该先点）；三重闸：直关词精确等值（×/✕/✖/╳/⨯/关闭——拉丁 X 不收）+ 小形态（≤35% 屏宽 × ≤8% 屏高）+ 同窗广告上下文证据（来自广告推荐/广告推荐/赞助 contains 或合法披露角标在场）+ 同行近距门（|Δy| ≤30% 屏高——同卡同排铁证）；无上下文绝不裸点「关闭」（#80 20:16:46 PhotoViewV16 相册关闭钮反例零出手）；ComplianceGuard.feedBoundaryCheck 终闸语义面收编直关词（上游三重闸已守）
  - **空闲巡检（FEED_PATROL_MS=2500）**：根因 = maybeFeedScan 只挂 TYPE_WINDOW_CONTENT_CHANGED——用户停下刷屏盯着广告看时【零事件 = 零扫描】（截图 20:15~20:17 广告静止在屏 2 分钟引擎全程盲）；2.5s 巡检链进信息流窗即拉起（state-changed 亦挂），离窗/切包/服务暂停即自停（功耗红线不破）；巡检未命中诊断降频 5 分钟一条（事件未命中保持 30s 节流）
- **信息流 App 名单 18→20 款**：+com.zol.android 中关村在线（截图2/3 实锤：讨论区信息流卡片广告「今日推荐/来自广告推荐」+「×」直关钮）+ com.xiaomi.market 小米应用商店（日志 #80 实锤）——有真机证据才扩名单（不做无证据的名单扩张）；微信窗级门只认 sns 族窗保持（真机窗类 ImproveSnsTimelineUI 含 sns 过闸实锤）
- **扫描日志诊断增强（「根据扫描日志进行功能实现」的数据闭环）**：未命中日志带窗类 + 角标词命中样本（label@(cx%,cy%) 前两个——【被闸也照登】）——下轮上传日志直接读到真机角标形态，区分「窗内无角标词命中」与「命中被闸（形态/语义）」
- **扩展动作表收编「复制」**：真机 ZOL 反馈菜单「点击复制广告ID」行绝不代点
- 工程化：FeedAdGuardTest 90→131（+41：S10 P0 面板直通真值表 10 / S11 P1X 卡片直关真值表 11 / S12 真机截图文案重建 12——微信原因面板/ZOL 推荐卡/ZOL 反馈菜单逐字树；S1 名单 22 / S5 直接关闭置顶 15 / S7 常量 11）+ BrandFocus308Test ⑦ 信息流广告维 4→6 项（P0 面板几何 + P1X 卡片几何按机型分辨率 1:1 缩放逐机型全过）；全仓 **17 套件 4118 项 JVM 断言全绿（+41）**；APK 828KB（845,366 B）· VC 74000（>73000 覆盖安装即升级）· MD5 `de9bfb0d5ef18ac548bb6b4ae6a25247` · SHA-256 `aa795321e3969a7099abf7b4a50c5e42b54a043429d07f38c2f762e768ad52d4` 三目录分发；GitHub tag v7.4.0 + Release + 仓库描述与 README 面板同步 · 实录见 [OPTIMIZATION-v7.4.0.md](OPTIMIZATION-v7.4.0.md)

## v7.3.0（2026-11-20）—— 安全加固 + 算法优化（防投毒 · 防劫持 · 防注入）

- **更新链 SHA-256 完整性校验（用户指令：完善安全性修复，避免遭到他人攻击）**：GitHub API（HTTPS）asset digest/size 随下载任务同源同存 → 安装前流式计算 SHA-256 逐字节比对（digest 缺失回退字节数比对，双缺拒装）→ 不符自动删文件清任务报错——镜像代理投毒 / 公共 Downloads 目录 TOCTOU 换包 / API 元数据注入全部拦下；GitHub API 三通道禁自动重定向（3xx 只可能是劫持）；响应体 4MB 硬上限
- **订阅规则注入防线**：支付资金词（19 词）+ 营销诱导词双否决对全部规则生效（此前带 activityIds 的专属规则存在绕过口——恶意订阅可定向代点「确认支付」类按钮）；订阅命中规则的「规则名」不再能作为终闸语义证据（零文本零 vid 目标一律拒点）
- **解析器 DoS 硬闸**：Json5 嵌套深度 ≤64（深嵌套 StackOverflowError 根治——Error 不被 catch(Exception) 接住，服务重绑即崩的无障碍掉线 DoS）；订阅体量上限（apps ≤2000 · 总规则 ≤20000 · 组 ≤100/应用 · 规则 ≤200/组 · 选择器 ≤20/条 · 选择器长度 ≤256）；拉取/加载链 catch Throwable
- **网络与桥面加固**：下载源/外链域名 contains 子串 → URL 解析 host 精确/后缀白名单（`github.com.evil.com` 类构造全拒）；kvSet key 白名单（XSS 若出现也无法写 subCache 投毒）；openUrl 仅 https + 精确 host；allowBackup=false
- **算法优化（扫描热路径零分配化）**：matchVid 手写分段扫描（旧 `split("[.$]")` 每叶子节点隐式正则编译）· isAdActivityClass 结果缓存（512 封顶）· GkdSelector 后缀常量预计算 · lowerAscii ASCII 快路径（零分配 + tr-TR locale 下 "SKIP→skıp" 整层丢失根治）· collectRoots getRoot 单次缓存
- **多点防线 II + 既有 bug**：出手冷却改 max(300ms, 人手复验节奏)（nudge 补扫旁路 ~300ms 换候选连发封死）；firedTargetAt 超限改按时间淘汰（原整体 clear 致防线失忆可对同目标补枪）；下载完成后「立即安装」按钮翻转的既有 bug 修复
- 工程化：**17 套件 4077 项 JVM 断言全绿保持**（安全重写全部 byte-for-byte 语义等价）；APK 824KB · VC 73000（>72000 覆盖安装即升级）· MD5 `1577bc2aa220c3fec6af0533930f382a` · SHA-256 `3905dc45a62693dd549604e0a916af4934a90717ce814e36d0f6a5fe1e42c973` 三目录分发；GitHub tag v7.3.0 + Release + 仓库描述与 README 面板同步（免责声明新增安全边界披露段）· 实录见 [OPTIMIZATION-v7.3.0.md](OPTIMIZATION-v7.3.0.md)

## v7.2.0（2026-11-19）—— 信息流广告真机命中根治（李跳跳方法论）+ 白名单全量应用列表 + 报告饼图折叠 + 多点防线 I

- **信息流广告「全都没拦截」四处死因根治（用户实锤：app 内穿插的广告全都没拦截——参考李跳跳实现逻辑）**：v7.1.0 单测全绿但真机零命中的四处病灶——① `clearTapGuards` 在窗切换清 feedArmed（P1 拉起的关闭菜单本身就是 Dialog 新窗，armed 一进菜单即死·最直接死因）② sns 窗门 + 12s 开屏窗闸对菜单窗双杀 ③ 300 节点预算制 DFS 够不到朋友圈大树深层卡片的角标 ④ P2「强特征≥1 且总数≥2」双闸在真机单项菜单（如仅「不感兴趣」）上零命中——全部根治：**armed 直通窗闸**（P2 只认 armed + 中性项精确等值 + 扩展动作排除 + 形态闸；窗闸 12s→2.5s）；**李跳跳方法论主路径**（`findAccessibilityNodeInfosByText` 原生直查全树·预算无关，候选逐个过语义+形态闸——宁漏勿误红线不变，DFS 兜底保持）；**armed≥1 即出手**（armed 期绝不回落 P1——角标连点顺手根治）；收编真机文案「屏蔽此推广/减少此类广告」；feed 关闭单独记账 `stats.feed`（不虚增 auto）；feed 未命中 30s 节流诊断日志随一日三次上传（「全没拦截」类反馈日志可区分「窗内无角标」与「角标文案未收编」）
- **应用白名单全量应用列表（用户实锤：搜索不到，不符合人的使用行为——大小写不敏感或直接全部显示，两方案一起落地）**：根因=列表来源只有引擎战绩∪白名单（新装用户为空）——① 全部已安装可启动应用枚举（`queryIntentActivities(ACTION_MAIN+CATEGORY_LAUNCHER)` · label 取 `getApplicationLabel` · Collator 中文序白名单置顶 · 上限 80→500 · 30s 缓存增删即时失效）② 浏览器式搜索：中文名/包名双通道不区分大小写包含匹配（qqQQ→qq 检索到 QQ）· 120ms debounce 实时过滤 · 460px 滚动区细滚动条
- **卫士使用报告：饼图常驻 + 明细折叠 + 到页自动刷新（用户实锤：报告太长难受）**：纯 CSS conic-gradient 甜甜圈常驻（五段：开屏秒跳/**信息流关闭（新档）**/弹窗/返回键/反馈面板·全 0 灰环·绿色系五档配色零蓝色）+ 五段折叠明细默认收起（拦截战绩/订阅两源命中/Top5/能耗/设备画像·点击展开）+ 进入「我的」页自动静默刷新（零 toast）+ 按钮改「刷新报告」
- **多点误点防线 I + 节奏收紧（用户实锤：设备类型/分辨率原因导致多点误点）**：同指纹击数封顶 `MAX_STRIKES_PER_TARGET=2`（纯函数 `targetStrikesOk`——同一目标指纹每窗最多 2 击，第 3 击起静默；换候选/换窗不受限）；耐心预算 `MAX_CLICKS_PER_WIN` 6→3（人的耐心 2~3 下）；F 同指纹静默 1500→1600ms；H 结果静默 250→600ms（复验扫描 500ms 到点时静默未过不补击，下一轮 1.4s 再评——「点了还没生效就再点」从节奏上根治）
- **版本记录序号化（用户指令：版本记录也要 1.2.3.4 按序号描述功能）**：`CURRENT_RELEASE` = 1 标题 + `features[]` 序号行（1.~5.）逐条渲染；检查更新 ≤5 带序号新版功能保持
- 工程化：FeedAdGuardTest 81→90（S9 候选式快搜含「深树角标 DFS 够不到 vs 原生直查命中」对照断言——旧病灶在场的直接证明）+ HumanTapMatrixTest +1（防线 I 真值表）+ BrandFocus308Test 四防线参数契约同步；全仓 **17 套件 4077 项 JVM 断言全绿（+10）** · 308 机型八维各 308/308；APK 817KB · VC 72000（>71000 覆盖安装即升级）· MD5 `3100d6bd9e0fb4b58ae81a767e2120ad` 三目录分发；GitHub tag v7.2.0 + Release + 仓库描述与 README 面板同步 · 实录见 [OPTIMIZATION-v7.2.0.md](OPTIMIZATION-v7.2.0.md)

## v7.1.0（2026-11-19）—— 规则使用顺序治理 + 信息流广告卫士 + 白名单中文名搜索 + 使用报告 + 308 机型八维矩阵

- **内置规则库与订阅市场规则使用顺序根治（用户指令：扫描日志信息，优化使用顺序和命中问题）**：① 跨源——市场源（用户导入·日更）在该应用有专属规则时【优先】、内置源（APK 静态快照）兜底（RuleRouter 纯函数唯一裁决处）；② 源内——应用专属规则先于 globalGroups 全局组（修复 parse 序让泛化规则饿死专属精准规则的旧病灶）；③ 轮换——两源各贡献一个候选进点击轮换（单源候选失效不再拖死整轮）；④ 扫描日志新增订阅两源覆盖诊断（`订阅[市场覆盖+内置覆盖]`——使用顺序问题上传日志即可定位）；订阅命中日志带源标注（市场优先/内置兜底）
- **信息流广告卫士（用户指令：自动关闭 app 内广告内容——微信朋友圈就业广告等）**：v6.2.0 已验证 FeedAdGuard（81 项单测）整体移植回 7.0 稳定基座——两段式（P1 点「广告/就业」披露角标 → 8s 指纹窗内 P2 点「关闭该广告」中性项收菜单）· 四道安全闸（角标词精确等值/面积门 ≤35%屏×≤8%屏/P2 双闸/18 款名单门+微信 sns 窗级门）——绝不点广告主体（宁漏勿误）；既有 execute() 防线（用户让路/耐心预算/F·H 多点防线/人手手势/feed 终闸）免费复用
- **底层算法优化**：分应用规则行预筛 memo（RuleEngine.rowsFor——matchText 每节点 ~50 行前缀比对 → 每窗一次预筛，探针/DFS 双路径共享；未预筛调用方回落 rowsFor 与旧全表+前缀检查数学等价）；首版回落路径丢前缀检查被 RuleOrderTest 当场抓红后修复（39 项断言锁死）
- **应用白名单中文名搜索（用户指令）**：搜索栏中文名/包名双通道不区分大小写包含匹配·输入即过滤·空态指引
- **卫士使用报告（用户点名测试项落地为 App 功能）**：我的页一键生成——拦截战绩/订阅两源命中分账（市场优先·内置兜底）/应用战绩 Top5/能耗画像/设备画像/多点误点防线状态；数据全本机零上传
- **机型矩阵 286→308·七维→八维（用户指令：拓展设备型号和品牌以及系统）**：每品牌 +2 新世代机型（HyperOS 5/HarmonyOS 8/MagicOS 12/ColorOS 18/OriginOS 8/One UI 10）；新增 ⑦ 信息流广告维（角标/菜单几何按机型分辨率 1:1 缩放逐机型验证全链）；11 组 × 28 = 308 机型【八维】各 308/308（只测点名 9 品牌+子品牌）
- 工程化：新增 RuleOrderTest 39 项 + FeedAdGuardTest 81 项移植回绿 + BrandFocus308Test 2868 项；全仓 **17 套件 4067 项 JVM 断言全绿（+616）**；APK 816KB · VC 71000（>70000 覆盖安装即升级）· MD5 `5101b655d2ac993f97a97ddc37a24075` · 三目录分发；GitHub tag v7.1.0 + Release 正文按规范（1 标题 1 段落 + 5 条带序号功能·每条 ≤44 字）+ 仓库描述与 README 面板同步 · 实录见 [OPTIMIZATION-v7.1.0.md](OPTIMIZATION-v7.1.0.md)

## v7.0.0（2026-11-19）—— 回退 6.0 基座重构 · 订阅导入永不失败 · 版本描述规范 · 一日三次自动日志 · 286 机型矩阵

- **回退 6.0 基座重构（用户实锤：6.0 之后的版本全部不可用——回退到 6.0 重构，之后的版本 GitHub 全部清除）**：本地 main 重置到 v6.0.0；GitHub releases v6.1.0-v6.5.0 与对应 tags 全部删除；v6.0 基座 + v6.1 在用户设备实测验证过的全部特性（订阅镜像自愈/误触三防线/8h 日志/版本描述格式/免打扰持久模式取消）经逐文件筛选保持，v6.2 之后引入的一切运行时改动（FeedAdGuard 服务侧扩散/描述特性 UI/异步桥/迁移引擎）全部不进入 v7；versionCode 70000（> 60500——任何历史版本直接覆盖安装即回稳定基座）
- **订阅市场导入永不失败（Connection reset 终根治）**：八通道镜像链（SubMirrors 纯函数：原址→fastly→gcore→testingcf 四接入点→ghfast.top→gh-proxy.com 代理→raw 直连，每通道同址瞬时故障重试 600ms；进度实时回报页内「正在切换镜像 X/Y · host」）+ **离线快照兜底**（三个市场精选源最新内容随 APK 内置 assets/subsnap/*.json5——全通道失败时直接导入快照并标注「已导入(离线) ✓」，联网恢复 1 小时心跳自动换真源；快照归一化匹配任意 jsdelivr 接入点变体）；APK 310KB→808KB（快照压缩后 ~520KB）
- **版本信息描述规范（用户指令：言简意赅 1 标题 1 段落；新版本 ≤5 带序号标题）**：当前版本 CURRENT_RELEASE = 1 标题 + 1 段落；检查更新发现新版本时就地渲染 ≤5 条【带序号】功能描述（ReleaseNotes 纯函数四代格式兼容：序号行优先→①/-/* 回落；# 章节名绝不入列；Net.parseRelease 桥通道 headlines[] 预解析 + JS 回落双保险）；**顶部更新横幅整体移除**（只有手动「检查更新」可查询/下载，启动零网络零提示）
- **自动提交日志一日 3 次**（8h 均匀 · 成功才计时 · 心跳顺带）+ **留存 300→400 条** + **设备画像行**（分辨率/密度/SDK——多点误点归因所需）+ 重点事件段（误触防护/自愈拉活/订阅异常/引擎预警四类统计 + 最近 6 条原文）；日志正文 40 条带时间戳
- **多点误点防线保持**（v6.1 实测验证：F 同指纹目标静默 1.5s · G 新鲜坐标复核 · H 结果静默 250ms——设备类型/分辨率自适应容差）+ **界面初始化保险丝**（index.html 内联 5s 哨兵：app.js 早期崩溃时首页占位换成可操作指引，绝不出现「检查中/加载中」永久白屏——v6.3-v6.5 症状的兜底防线）
- **9 品牌+子品牌 11 组 × 26 = 286 机型七维全 286/286**（用户指令：只测 OPPO/vivo/小米/三星/华为/荣耀/红米/一加/iQOO 及子品牌）：每品牌 20→26 机型（新增 2026-2027 世代：小米 18 系/HyperOS 4 · Redmi K100/Turbo 5 · POCO F9 · Mate 80/HarmonyOS 7 · Magic9/MagicOS 11 · Find X9/ColorOS 17 · 真我 GT8 · X300/OriginOS 7 · iQOO 19/Neo 12 · Galaxy S26/One UI 9 · 一加 17/Ace 7/Nord 6）；新增 ReleaseNotesTest 30 项 + SubMirrorTest 35 项；全仓 **3451 项 JVM 断言全绿（+1099）**
- 工程化：APK 808KB · VC 70000 · 三目录分发（MD5 `db3ae440861280d9000ab2a18fad4eda`）· 免责声明入 README · GitHub 面板描述同步

## v6.0.0（2026-11-19）——1:1 人手模拟引擎（设备类型/分辨率→多点、误点根治）· 五轮激进 · 12×15 机型矩阵

- **1:1 人手模拟引擎（用户实锤：部分用户设备开屏跳过时因设备类型、分辨率或其他原因多点、误点——1 比 1 模拟人在不同设备上操作复现并解决）**：新核心 `HumanTap` 五要素——① `tapSpec` 人手触点参数域（起始延迟 26~56ms · 触点时长 95~160ms 首击轻点/重试更用力 · ±2px 微抖轨迹——替代「固定 40ms+固定 110ms+完美水平 1px」千次一模一样机器人签名，反自动化广告 SDK 触点指纹检测不再无声吞点）② `verifyDelayMs` 人手复验节奏（首击 500ms 扫一眼，替代 450/900 双针机器枪；二击后 1.4s/2.4s/3.6s/5.2s/7.0s 渐进放缓——人会越等越慢）③ dp 密度归一（安全域常量 px→dp 按设备 density 缩放——160dpi 与 480dpi 行为归一，「分辨率原因」根治）④ `isDupPoint` 触点轨迹去重（同窗 ±8dp 内绝不重复盲刺，换象限/换候选）⑤ `HUMAN_PATIENCE=6` 人手耐心预算（同窗点击 12→6——6 下没关优雅收手等倒计时，多点级联根治）
- **实时人眼追踪**：出手前 `refreshLive()` 刷新节点【现在】的 bounds——广告按钮入场缩放/位移动画后按旧坐标点击=点空的根因根治（人眼看到哪就点到哪）；覆盖层 Z 序判定用抖动后的最终落点；抖动越界回退/钳制（边界防线不因抖动而破）
- **`TapPoint.pickDp` 密度自适应选点**：安全域（可见缝最小边/边缘内缩）按设备 density 以 dp 缩放——低密度不再过严拒点（「无点」）、高密度不再贴死区（复验=「多点」）；`density=1.0` 与旧 `pick` 逐字节一致（兼容基线）；`tapTrail` 轨迹登记（24 点/窗 · 64 包封顶长会话内存防护）
- **命中五轮激进（扫描日志继续优化·大胆激进）**：SDK 指纹 **+4 段**（`mobads` 百度联盟 · `sigmob` Sigmob · `adcolony` AdColony · `vungle` Liftoff Vungle——累计 50+ 段）；分应用表 **+6 款**（微博/腾讯视频/优酷/爱奇艺/芒果TV/酷我音乐——社交/长视频/音乐类高频 App 开屏未命中实锤）
- **12 品牌 × 15 机型 = 180 机型矩阵（BrandMatrix180Test · 六维全 180/180）**：165→180（+9%——「其他」族扩容 Pixel/Nothing/索尼/联想 15 款国际市场真实机型）；另**设备自适应触点矩阵升级 24 几何 × 12 场景**（新增 S10 dp 自适应安全域 · S11 高密度收紧 · S12 人手抖动不出界——真实密度配对 + 7 档压力条件契约）+ **1:1 人手矩阵（HumanTapMatrixTest · 新增 19 项）**；全仓 **2352 项 JVM 断言全绿（+367）**
- **诚实修正三处测试断言语义错位**（引擎行为正确、断言算术错位）：真实密度配对表（720p×xxxhdpi 真实世界不存在——1:1 模拟=几何 × 该档真实密度）+ 7 档压力条件契约（亚指腹必须拒点=防误触）+ S4c 切比雪夫算术修正（轮换耗尽全判重→拒点换候选的最终防线显式断言）——断言更严而非放松
- 工程化：APK 297KB · VC 60000 · 三目录分发（MD5 `130554c71ad380cdd3f15858cbd94511`）

## v5.9.0（2026-11-18）——设备自适应触点（多点/无点=误触根治）· 覆盖层防误触 · 四轮激进 · 11×15 机型矩阵

- **设备自适应触点引擎（用户四轮实锤：用户设备不同就可能导致多点或无点——误触）**：原始几何中心 → `TapPoint` 四层防线（新核心纯函数类）——① 祖先可见交集（标签被父容器裁掉一半时，触点必落【可见侧】——旧几何中心落在不可见区=点到旁边内容误触）② 节点窗口钳制（`node.getWindow()`——分屏/折叠另半区零盲点，绝不把触点派到另半屏他应用）③ 安全选点（**复验象限轮换**：同候选第 N 次复验换象限点，不再盲刺同一坐标=「多点无效果」根治；边缘内缩 6px 防圆角/水波纹/刘海死区；可见缝 <12px **盲刺拒绝**——宁可无障碍代点绝不乱刺）④ 形变防死区（扁矩形纵向恒居中·竖窄矩形横向恒居中——横幅状标签上下边死区零接触）。拒发手势 ≠ 放弃：自动降级 `performAction` 无障碍代点（不经屏幕坐标，点得到语义目标）
- **覆盖层防误触泛化（设备差异真实事故面根除）**：v3.13.0 只拦 SystemUI 顶条 → v5.9.0 拦 Z 序高于广告窗的【任意】窗口（**输入法/悬浮球/他应用 overlay/状态栏/heads-up 横幅**）——键盘弹出时触点被截=乱打字、验证码横幅被点掉、悬浮窗被误触三类事故面全根除；查不到活跃窗（特殊 ROM）退化旧行为兜底
- **命中四轮激进（扫描日志继续优化·大胆激进）**：SDK 指纹 **+2 段**（`ksad` 快手 KSAdSDK 广告窗族 · `csj_` 穿山甲联盟复合族——刻意不用裸 `csj` 防 `com.csj` 业务包碰撞误报，LearnGovernor 泛化表两段均已在册、指纹表此前漏网）；分应用表 +6 款（**网易新闻/腾讯新闻/新浪新闻/夸克/有道词典/哈啰出行**——新闻/工具类高频 App 未命中诊断日志实锤）
- **11 品牌 × 15 机型 = 165 机型矩阵（BrandMatrix165Test · 六维全 165/165）**：150→165（+10%——新增**传音 Tecno/Infinix/itel·HiOS/XOS 族** 15 款：Camon 40 Premier/40 Pro/30 Pro · Spark 30 Pro/20 · Pop 9 · Note 50 Pro+/40 Pro · Hot 50 Pro/40i · GT 20 Pro · Zero 40 · S25 Ultra · A70 · P70——非洲/东南亚/南亚真实大市场）
- **设备自适应触点矩阵（TapPointMatrixTest · 24 类几何 × 9 场景全绿 · 新增）**：24 类真实设备几何（直板 720p~1440p/16:9 遗留/折叠外屏窄·Flip 超窄·内屏近方·方屏三档/平板 16:10·3:2·4:3/分屏半窗横竖/刘海/挖孔/超宽 QHD/正方/超长 21:9/横屏）× 9 场景（半裁剪标签点可见侧/窗外拒点/盲刺拒绝/象限轮换不盲刺/边缘内缩零贴边/覆盖层拦截/不可见祖先拒点/扁标签形变防死区/引擎全链路落点安全）
- **全仓 1985 项 JVM 断言全绿（+319）**；工程化：APK 297KB · VC 50900 · 三目录分发（MD5 `ff03f9ec4cba29e2280811243772d116`）

## v5.8.0（2026-11-18）—— 屏蔽速度压榨 · 三轮激进 · 首页极简 · 订阅市场防呆版 · 10×15 机型矩阵

- **屏蔽速度压榨（用户三轮实锤：屏蔽速度太慢了，压榨优化速度算法）**：点击冷却 420→**300ms** · 倒计时缓冲 180→**120ms**（将尽提前 0.38s 开火）· 骨架扫描档 13→**18 档**（前 4s 密度×2——「内容事件不发射」广告窗的唯一依赖，旧 700→1100→1700ms 间隙即命中延迟）· 补扫节流 120→**80ms**（「按钮出现→点击」延迟上限减半）· 救回节流 900→**600ms** · 弹窗冷却 1600→**1200ms** · 弹窗补扫节流 700→**480ms** · 同窗点击预算 10→**12** · 跳过候选 6→**8**——全链路压榨，功耗红线（典型日 < 0.15mWh）实测保持
- **命中三轮激进（扫描日志继续优化·大胆激进）**：SDK 指纹 **+4 段**（Mintegral/Pangle/TopOn/Moloco——聚合与国际 SDK 漏网主力，累计 44+ 段）；「推广」文案族全量补收（`跳过推广`/`关闭推广`/`跳過推廣`——合规敏感 App 普遍以「推广」代「广告」作整词标签，旧库未收零命中）；分应用表 +10 款（快手/搜狐视频/闲鱼/酷安/虎牙直播/斗鱼/百度网盘/WiFi万能钥匙/链家/抖音主线）；LearnGovernor 泛化清单同步对齐
- **首页怎么简单怎么来（用户实锤：保活体验太多描述一大串）**：保活体检压缩为一行状态——全部通过一行绿字，有待处理只列待处理项（标签+去设置按钮，零长描述）；「持久守护·零系统设置」ADB 卡从首页移除，降级为「我的→高级」折叠卡（普通用户路径零系统设置零电脑：一键恢复 + 前台看门狗自动拉活）
- **订阅市场防呆版（用户实锤：点击订阅就直接导入进来）**：6→**3 个高星活跃源**（Lin-arm ⭐7.4k / ganlinte ⭐935 / MengNianxiaoyao ⭐496——gkd-subscription 话题星数前三）；**点一下整行即导入**（右侧状态位「导入中…→已导入 ✓」，失败「再点重试」）；仓库按钮、底部更多订阅、App 内跳转**全部移除**
- **我的模块防呆（用户实锤：不用显示权限使用审计）**：权限使用审计卡移除（零权限声明留在 README 与系统设置可查）；引擎日志**「刷新」按钮**（上传日志旁，重读服务内存实时态）；上传成功**只保留一个确认弹窗**（顶部横幅与成功通知移除，单一反馈通道；失败通知保留兜底）
- **10 品牌 × 15 机型 = 150 机型矩阵（BrandMatrix150Test · 六维全 150/150）**：96→**150 机型**（+56%——新增魅族·Flyme 独立族、中兴/努比亚/红魔·MyOS 独立族，每族 12→15 款）；六维全 150/150（开屏命中六信号/屏蔽速度/bug 四陷阱/功耗/精准度/算法优化）；全仓 **1666 项 JVM 断言全绿**（+336）
- 工程化：APK 296KB · VC 50800 · 三目录分发

## v5.7.0（2026-11-18）—— 扫描日志二轮激进 · 8×12 机型矩阵 · GitHub 面板截图

- **命中再激进化（扫描日志继续优化·用户二轮实锤）**：SDK 指纹 **+12 档**（Unity Ads `AdUnitActivity` / IronSource / Chartboost `CBImpressionActivity` / Meta `AudienceNetworkActivity` / InMobi / Tapjoy / `RewardVideo` / `RewardAd` / `ExpressAd` / `AdPresent` / `FullScreenAd` / `FullVideo`——国际 SDK 漏网主力；App 自建 `SplashActivity` 与 Unity 游戏引擎 `UnityPlayerActivity` 绝不误认）；倒计时缓冲 300→**180ms**；同窗点击预算 8→**10**；文本跳过候选 4→**6**；点击冷却 500→**420ms**；弹窗冷却 2000→**1600ms**
- **文案/vid 双通道扩充**：全局精确文案 +5（`跳过视频`/`跳过片头`/`跳過視頻`/`skip ads`——视频类 App 开屏整词标签）；分应用表 +12 款（得物/什么值得买/唯品会/苏宁/途牛/马蜂窝/墨迹天气/58 同城/安居客/贝壳找房/汽车之家/虎扑）；vid 层新 **ADCLOSE 复合族** 3 档（`adclose`/`ad_close`/`adcancel`——第一方自绘广告关闭键命名约定，边缘带 + 小面积门控下任意窗口放行；`jump` 非跳过语义依旧被安全门拦截，红线保持）
- **内置规则库 v7→v8**：114→**124 款**（苏宁易购/什么值得买/贝壳找房/虎扑/安居客/途牛 6 款与主引擎分应用表同源对齐）
- **8 品牌 × 12 机型矩阵（BrandMatrix96Test · 630 断言）**：50→**96 机型**（+92% 覆盖面——新增荣耀·MagicOS 族、一加独立族、「其他」族 Pixel/Nothing/魅族/努比亚/红魔/索尼/moto）；六维全 96/96（开屏命中六信号/屏蔽速度/bug 四陷阱/功耗/精准度/算法优化）；全仓 **1330 项 JVM 断言全绿**（+282）
- **GitHub 面板描述优化**：README 重构（徽章行 + 一屏速览 + **真实 App 截图三连**——`docs/screenshots/`，App 实际 WebView UI 渲染截取，非设计稿）
- 工程化：APK 297KB · VC 50700 · 三目录分发（MD5 `3daa9cde0e99f40607c746fea9614ed0`）

## v5.6.0（2026-11-18）—— 命中激进化 · 订阅市场 · 10 秒警告终局 · 5×10 机型矩阵 · 语言定版

- **命中激进化（好多广告没有命中·一刀切断）**：SDK 指纹 +8 档（穿山甲 `UnionSplashActivity` / 优量汇-AdMob `Interstitial` / `InsertScreen` / `Advertising` / `PromoAd` / `BonusAd`——整窗无指纹=零点击零返回兜底的漏网主力，同时 App 自建 `SplashActivity` 绝不误认）；倒计时缓冲 500→300ms；同窗点击预算 6→8；点击冷却 600→500ms；弹窗冷却 2500→2000ms；SKIP 文案库扩充；内置规则库 v6→v7（102→114 款 + 全局 `vid*="skip"` / `vid*="countdown"` 兜底两档）
- **订阅市场（默认订阅停止维护·根治）**：@gkd-kit/subscription（官方归档停更）与 AIsouler（2026-02 停更）下架；gkd-subscription 话题活跃源一键导入 6 源；官方订阅仓库 [skip-ad-subscription](https://github.com/xiaobaiba999/skip-ad-subscription)（subscription-template 结构、与内置规则库同源导出 v7·113 款 121 条）；页内直达 topics 列表与自建订阅指引
- **10 秒警告终局**：前台 3s 看门狗自动深挖拉活；可选「零打扰持久模式」——一次 ADB 授权 `WRITE_SECURE_SETTINGS` 后，三链路（SmartRevive/tryAutoRevive/deepReviveOnce）在登记吊销分支幂等写回登记自动重绑（零系统设置·零 10 秒警告；未授权设备行为不变）
- **5 品牌 × 10 机型矩阵（BrandMatrix50Test·336 断言）**：六维全 50/50（开屏命中/屏蔽速度/bug 防线/功耗/精准度/算法优化）；全仓 1048 JVM 断言全绿
- **语言策略定版**：Java 引擎保持（重写否决）；热路径三件套优化（Pattern 预编译 / HashSet 精确层 / 并行小写表——语义零变化）；`android/` 死代码删除；`.gitattributes` 语言栏净化；详见 [LANGUAGE-STRATEGY.md](LANGUAGE-STRATEGY.md)
- 工程化：APK 296KB · VC 50600 · 三目录分发（MD5 `c9d25ba3e642d66a282a4e72c961da43`）

## v5.5.1（2026-11-18）—— issue#62/#64 引擎日志双案根治

**微博 gestureOnly 误杀（漏拦截元凶）**：v5.5.0 日志实锤「🚧 动作边界自检拦截（weibo · 内嵌规则·分应用文案命中）」——规则引擎已按「跳过广告」文案命中（无可点祖先 = 微博图片跳过键常态），但 Decision 不携带命中文案 → 边界自检拿到空 label → 语义断言误杀。修复：`Decision.label` 全链路传递 + execute 回落（非跳过文案依旧一票否决，ComplianceGuardTest ⑤ 零放行面验证）。**小米下载管理设置页整窗扫描 4 次 + 位置学习误触发**：`com.android.providers.downloads.ui` 有「.ui」通用尾段，.settings/systemui/securitycenter 匹配全漏网 → 零扫描名单增补 providers.downloads / documentsui。**动作边界自检可诊断化**：拆层码 BND_FAIL_SCENARIO/SEMANTIC/FORM + 目标签要打点。**未命中日志三重降噪补第三重**：Dialog 动画期类名抖动重置 winStart → 同包 8s 节流。**候选样本双净化**：越界中心剔除 + label+坐标去重。**工程化补课**：run-tests.sh 引用的两套件被 .gitignore 裸 `test` 模式静默吞掉（本地全绿、git add 静默跳过）→ gitignore 收敛放行 + 补写并扩展。**LearnGovernor 泛化清单同源对齐**：isAdActivityClass 认的家族段补进 sdkGeneralize。700 项 JVM 断言全绿（+225）。详见 [OPTIMIZATION-v5.5.1.md](OPTIMIZATION-v5.5.1.md)。

## v5.5.0（2026-11-18）—— 全维度优化

UI 交互专项（版本记录极简化/日志上传三重反馈/10 秒警告红线——系统设置跳转前强制二次判定，登记仍在一律应用内自愈）；三源规则引擎（官方/GKD 社区/学习规则统一调度、优先级权重与冲突裁决）；性能功耗兼容（SmartScanPlanner 分级预算、折叠屏/平板/分屏形态自适应）；安全合规（WebView 硬化、权限审计）；崩溃采集闭环；日志内容四类齐备（全链路正则脱敏）。453 项 JVM 断言全绿。详见 [OPTIMIZATION-v5.5.1.md](OPTIMIZATION-v5.5.1.md) 与 round-20/21 报告。

## v5.2.0（2026-11-18）—— 「每次退后台/覆盖安装都要看 10 秒系统警告」根治（issue#56）

根因分层：HyperOS 清理后台/覆盖安装吊销无障碍登记（BIND_ACCESSIBILITY_SERVICE 签名级保护，只能经系统设置重开撞 10 秒防诈确认）；旧版三处放大器（通知点按无条件直跳设置/拉活窗口 6s 不够/覆盖安装直接假设已关闭）。修复：两态通知 + 15s 自适应拉活（前 2s 每 300ms 快踢）+ 广播窗口踢链自愈 + 保活体检卡（近 5 年全品牌自启动/省电策略直达，候选硬编码 OEM 系统包）+ 登记移除 ContentObserver 遥测 + 同态通知 10 分钟冷却；安全同步（signature 级私有广播权限 + 深链零注入面）。427 项 JVM 断言全绿（+106 RestoreGuard 专项）。

## v5.1.1（2026-11-18）—— 支付宝 XRiver 容器保护（issue#51-55）

XRiverActivity 胶囊「关闭」被代点 = 用户被踢出小程序（与 #45 抖音团购券同类）。修复：xriver 标记入支付宝容器表 + 树标识兜底（胶囊特征文案「收藏到我的小程序」+ vid 标识）。容器内绝不扫描绝不点击绝不学习。321 项断言全绿（+7）。

## v5.1.0（2026-11-18）—— 非主流 App 开屏攻坚（issue#46/47）

美团 DspActivity/DynamicDialog、第三方 AdsActivity 整窗无指纹 → SDK 指纹三连（adsactivity/dsp/dynamicdialog）；弹窗安全门（可滚动运营弹窗绝不返回键）；零候选退避（校徽启动页不再反复整树扫描）；跳过后静默期；内置规则库 54→102 款；诱导词黑名单增补；支付/资金页零扫描。314 项断言全绿。

## v5.0.0（2026-11-17）—— 小程序/团购容器保护（用户实锤根治）

抖音团购券小程序/电商容器（MiniAppActivity 等）内「跳过广告」文案被当真广告关掉 → 新增 L0 守卫 MiniProgramGuard（抖音全部容器 + 微信 AppBrandUI + 支付宝 Nebula + QQ 小程序 + 通用 miniapp 标识 + 树标识兜底）；五层纵深防御；内置规则库 54 款必备应用；订阅规则形状门（全宽行/横幅绝不代点）。150 项断言全绿。

## v4.5.0（2026-11-17）—— 乱点根治 + 系统开关解耦 + 提速 + 安全加固

乱点根治（李跳跳式形状门三重防线：文本命中形状门/竖列菜单一票否决/你点开的弹窗 30s 零动作——issue#42-44）；系统开关与 App 开关彻底解耦（零 disableSelf，App 内/通知栏一键控制，暂停恢复绝不去系统设置）；原生文本探针提速；WebView/下载源/订阅源安全加固。279 项断言全绿。

## v4.4.0 —— 露头就秒大提速

倒计时门控开闸瞬间即点（+40ms 精确补扫针）；点击后 450/900ms 快速复验；H5 开屏按钮晚到救回通道（issue#39-41）；恢复防护零触摸；功耗再降（点击冷却 900→600ms）。

## v4.3.0 —— 订阅主力规则真正接入

GKD globalGroups 全局开屏组全量解析（CSDN 漏跳元凶）+ 数字关系符/childCount/text.length/括号布尔全语法；复验真正换候选（issue#38 同坐标 6 连击根治）；应用白名单；喜马拉雅「为什么关闭广告」循环弹窗根治；未命中诊断日志。

## v4.2.0 —— 用户意图让路 + 订阅全门控

用户真实点击 4s 让路 + 30s 窗口保护；微信主界面零扫描（issue#31-37）；GKD 订阅规则全门控并接弹窗场景；引擎日志 12h 自动提交；功耗整治。

## v4.1.0 —— 真机反馈整治（issue#30）

应用内一键暂停/恢复（免系统 10 秒警告）；相机/拍摄窗硬黑名单；灵动岛前台门；GKD 订阅 404 修复（官方包路径迁移 + 镜像回退）；全新 Logo。

## v4.0.0 —— GKD 订阅生态接入

粘贴 gkd.li 社区订阅链接即导入（选择器引擎兼容 GKD 语法，24h 静默更新）；内置规则库双引擎；安全红线（订阅仅点击类，back 规则加载即弃）。

## v3.14.0 —— ADB 自愈整体移除（用户反馈：要电脑执行太麻烦）

开启服务一步直达系统开关；保活四件套持续守护。（v5.6.0 以可选「零打扰持久模式」形式按用户需求回归。）

## v3.12.0 —— 链式拦截整体移除（issue#14：NFC 支付链被误弹回）

引擎职责回归纯跳过：只点「跳过/关闭」，绝不对抗用户任何跳转。

## v3.10.0 —— 内置规则库大扩充（五层全局 + 28 款分应用表，规则内嵌 APK 零云端）

## v3.8.0 —— 手势真实触点首选 + 假跳过手势直点 + vid 跳过键 + 倒计时门控

performAction 被广告 SDK 无声吞掉 → dispatchGesture 真实触点（GKD clickCenter 方法论）。

## v3.4.0 —— 角落图片跳过键（第一方图片开屏根因修复）

图片跳过键（零文本零 desc）识别：顶部 14% 高 + 贴边 + 小面积可点 + 无文本 → 手势点击中心 650ms；v3.9.0 收紧：须广告形态确认（热区/角标/SDK 窗/学习窗/节点≤60 五选一）。~~坐标级点击兜底~~已于 v3.9.0 整体移除（固定百分比坐标必然乱点——只点规则命中的控件自身 bounds 中心）。

## v3.3.0 —— SDK 广告窗识别 + 全屏热区直断 + 陀螺仪防线 + 弹窗 v2 + 守护模式

## v3.2.0 —— 弹窗窗期独立于开屏 12s 窗

## v2.0.0（2026-11-16）—— 秒跳引擎重构（Web 端）

开屏秒跳、摇一摇/Deeplink 拦截、守护模式、内置加密反馈、学习夜间同步。

## v1.x —— 判断引擎与习惯学习（Web PWA）

- v1.3.0：联网能力（Issues 反馈 / Releases 检查更新）
- v1.2.0：判断引擎与习惯学习（按压/轨迹/反应时间多信号）
- v1.0.0：Web App（PWA，可「添加到主屏幕」）
