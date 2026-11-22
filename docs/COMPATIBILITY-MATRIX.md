# Skip-Ad 应用兼容性回归矩阵（v8.1.0）

> 建立即时点：v8.1.0 真机反馈修复轮（2026-11-29）。
> 纪律：本矩阵**不声称兼容所有 App**；每行标注引擎处理路径与测试状态；
> 测试状态仅四种——`JVM_PASS`（JVM 套件断言覆盖）/ `JVM_REGRESSION`（既有
> 套件回归覆盖）/ `REAL_DEVICE=NOT_RUN`（需真机，未执行）/ `UNKNOWN`。
> 自动测试只证明自动测试覆盖的内容；真机结论一律以实际验证为准。

## 一、广告形态 × 引擎路径 × 测试状态

| # | 广告形态 | 典型场景 | 节点信号 | 引擎处理路径 | v8.1.0 变化 | 测试状态 |
|---|---|---|---|---|---|---|
| 1 | 普通 TextView 跳过键 | 原生开屏「跳过」文本按钮 | text 精确/短包含 | 探针直查 → RuleEngine.matchText → L1 点击 | 无（基线保持） | JVM_PASS（RegressionCoreTest/RuleOrderTest） |
| 2 | 倒计时跳过键 | 「跳过 5」「3s后可跳过」 | 倒计时正则（P1/P2） | 第2层倒计时形态严格正则 → L1 | 无 | JVM_PASS（SplashCountdownTest 61 断言） |
| 3 | ImageView + contentDescription | 无字图片键带「关闭」desc | desc 等值 | walkFused close 收集 → L1.6（角标/顶部区/语境门） | CLEAN 窗新增关闭线索复活（nativeCloseCueProbe） | JVM_PASS（isCloseText 等值门 AdScanReviveTest S4；L1.6 证据门 DecisionGuardTest） |
| 4 | 角落无字图片键（自绘/无语义 vid） | 右上角 X 图标、角落关闭 | bounds+clickable 形态 | L4 角落五选一形态门（热区/角标/SDK/学习/树≤60） | 无（安全门保持） | JVM_PASS（MinimalWindowBatchTest/PageScenarioTest） |
| 5 | WebView/H5 开屏 | 深树容器（500+ 节点）晚出键 | 深树+晚渲染 | 探针（无预算）+ walk 预算 + nudge 救回 | **nudge 4~12s 段 600ms 节流放行**（旧版 4s 断流）+ **晚档预算 360→600** | JVM_PASS（DualDfsParityTest/ScanBenchmark 成本断言）；真机 NOT_RUN |
| 6 | Compose UI | 声明式渲染语义节点 | 同普通树 | 同 1/3/4 路径 | 无 | JVM_REGRESSION（PageScenarioTest 12 类样本含 compose） |
| 7 | 视频开屏（SurfaceView 静默源） | 按钮视频末 5~15s 露头 | 零内容事件 | videoLike 画像（20s 窗）+ 轮询针 | 无（VideoSplashTest 93 断言保持） | JVM_PASS（VideoSplashTest）；真机 NOT_RUN |
| 8 | 广告 SDK 独立 Activity（SplashActivity/DspActivity） | 第三方 SDK 开屏窗 | 窗类指纹 | sdkAdWin 直断 + 订阅 vid 规则 + 返回键 L2 | 无 | JVM_PASS（NonMainstreamAppTest/SubscriptionBuiltinTest） |
| 9 | 广告 SDK Overlay/Dialog 弹层 | 独立包弹窗窗 | Dialog 形态+外来包 | popupUntil + 插屏扫描路由 | 无（InterstitialRoutingTest 保持） | JVM_PASS（InterstitialRoutingTest 67 断言） |
| 10 | 延迟出现按钮（3~12s 渲染） | H5/视频容器晚出键 | 晚渲染+内容事件 | 固定档 + nudge 救回 + 杀链救回 | **根因④修复（nudge 4~12s 放行）+ 根因③修复（入口窗无订阅不早杀链）** | JVM_PASS（豁免真值表 AdScanReviveTest S3）；真机 NOT_RUN |
| 11 | 按钮先出现后变化（轮换候选） | 跳过键变关闭键 | 候选集变化 | clickAttempts 轮换 + 复验升级 L4.5 | 无 | JVM_PASS（AdTaskTimingTest 竞态组） |
| 12 | 全屏可点击广告（点击主体即进落地页） | 整屏 CTA | 大热区祖先 | 假跳过候选手势点标签自身 bounds + 热区证据门 | 无（FAB 防线保持） | JVM_PASS（FalsePositiveScenarioTest 20 类红线） |
| 13 | 带反馈面板的广告（跳过后弹「不喜欢」） | 喜马拉雅类 | 面板特征词 | handleFeedbackSheet 代点「取消」 | 无 | JVM_PASS（AdTaskTest 反馈面板流程） |
| 14 | 第一方开屏与主界面同窗 | 入口窗 rootWindow | rootWindow 语义 | 点击梯子（返回键禁用）+ 证据门 | **根因①修复（学习开关误门控解除）+ 根因③（入口窗豁免）** | JVM_PASS（DecisionGuardTest/FalsePositiveScenarioTest）；真机 NOT_RUN |

## 二、特殊依赖面（非广告形态）

| 依赖 | 引擎行为 | 测试状态 |
|---|---|---|
| CLEAN 档案窗（3 窗零候选·跨重启） | 纯探针哨兵 + **v8.1.0 关闭线索复活 + 复查期 24h** | JVM_PASS（PowerGovernorTest + AdScanReviveTest S2） |
| 无内置订阅的长尾 App | 通用信号层（全局精确/倒计时/短包含/close 族）+ 学习 | JVM_REGRESSION（AppCatalogTest 233 款目录一致） |
| 内置订阅 App（124 款） | 包名精确 → 三件套规则（text/desc/vid） | JVM_PASS（SubscriptionBuiltinTest） |
| GKD 远程订阅 | GkdSelector 属性+关系选择器（不支持 preKeys——加载即丢） | JVM_PASS（RuleGovernorTest） |
| 息屏/Doze | 心跳窗口式闹钟顺延；扫描零执行 | JVM_REGRESSION（PowerGovernorTest 三零） |

## 三、本轮修复与形态矩阵的对应关系

| 根因 | 影响形态行 | 修复 | 安全性论证 |
|---|---|---|---|
| ① 学习开关误门控全开屏扫描 | 全部 14 行（总闸） | 解除门控；开关只管学习写入 | 扫描回归 masterOn 全局开关语义；已学规则读路径不变 |
| ② CLEAN 失明 | 3/4/10/14 行 | 关闭线索复活 + 复查期 24h | 探针绝不出手「关闭」——出手仍走 L1.6 证据门 + ComplianceGuard 终闸（AdScanReviveTest S1 钉死「关闭」裸词不进跳过词表） |
| ③ 早退杀链 | 5/10/14 行 | 入口窗+无订阅豁免（梯子跑满 12s） | 跨窗功耗治理仍由 CLEAN 档案兜底；有订阅窗不豁免（功耗不回退） |
| ④ nudge 4s 断流 | 5/10 行 | 4~12s 段 600ms 宽节流 | 比高频档宽 7.5 倍挡内容风暴；12s 观察窗外不扫保持 |
| ⑤ 晚档预算缩水 | 5/7/10 行 | 360→600 对齐中档 | 仅开屏窗内生效；SmartScanPlanner 分级红线保持 |
| ⑥ 名实一致化 | 14 行 | learning 开关管 learnFromManualClick | 相机/小程序容器等学习红线全部保持 |

## 四、验证缺口（诚实声明）

- **REAL_DEVICE = NOT_RUN**（全部 14 行）：沙箱无真机。晚出键救回/关闭线索复活
  /入口窗豁免的真实效果、功耗回归、覆盖安装升级——均需真机验证后回填本矩阵。
- Worker 端到端（Android→Worker→Issues）DEPLOYED_BUT_NOT_E2E_VERIFIED 保持
  （一键上传的 UI 链路 JVM 层已钉死 Summary 契约；真机联网上传未执行）。
- 兼容性矩阵的「典型场景」列来自 issue 真机日志与既有测试样本复刻
  （NonMainstreamAppTest/BrandFocus396Test 396 机型×十维），非本轮新增真机数据。
