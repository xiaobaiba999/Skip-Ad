# v5.5.1 优化纪要 · issue#62/#64 引擎日志驱动

> 本文记录 v5.5.1 的全部变更。每个修复都锚定真机引擎日志实锤（issue#56~#64），
> 修复与测试同commit落地；`run-tests.sh` 全绿后再提交（700 项断言）。

## 一、Issue 日志证据 → 根因 → 修复对照表

| # | 日志实锤（issue/版本） | 根因定位 | 修复 | 落点 |
|---|---|---|---|---|
| 1 | #64（v5.5.0）：`🚧 动作边界自检拦截（weibo · 内嵌规则·分应用文案命中）：目标非跳过/关闭语义或越界 → 放弃` 与 `❓ 开屏未命中（weibo）树 6 节点 · 窗口 SplashActivity` 成对出现 ×3 次 | 规则引擎已按「跳过广告」文案命中（第0层分应用表 `com.sina.weibo` 行），但微博跳过键是零文本图片键 → 无可点祖先 → `hit.gestureOnly=true` → `Decision.node=null` → 边界自检只能拿到空 label/空 vid → 语义断言必败 → 真命中被自检误杀（「宁可漏拦绝不误触」的闸门吞掉了本已验证过语义的动作） | `Decision` 新增 `label` 字段；规则命中路径 `rd.label = hit.label`；execute 边界自检在控件文本与 vid 双空时回落到命中文案。**零放行面红线**：回落只是语义依据换源——文案本身仍必须是跳过/关闭族才可能通过（ComplianceGuardTest ⑤ 全组验证） | `AdDecisionCore.Decision` / `AdSkipService.execute` / `AdSkipService.tryScan` |
| 2 | #64（v5.5.0）：`❓ 开屏未命中（downloads.ui）` ×4 + `🛡 位置学习已拦截（downloads.ui）`——小米下载管理设置页（`com.android.providers.downloads.ui` · `DownloadSettingActivity`）被整窗扫描 | 零扫描名单只匹配 `.settings`/`systemui`/`securitycenter` 等尾段；`.ui` 通用尾段全部漏网，且事件入口的 `isSystemNoise` 也没有 providers.downloads | `ComplianceGuard.isZeroScanScenario` 增补 `providers.downloads`（下载管理族）与 `documentsui`（系统文件选择器 SAF——纯系统 UI 宿主绝不承载广告，扫描是纯功耗、点击是纯风险面） | `ComplianceGuard` |
| 3 | #62（v5.3.0）：`❓ 开屏未命中（ting.android）` 2 秒内 4 条（窗口 FrameLayout→MainActivity→FrameLayout 抖动）；#64：downloads.ui 同秒 2 条 | Dialog/底部面板弹出动画期窗口类名高频抖动，每次 `TYPE_WINDOW_STATE_CHANGED` 都 `windowAt.put(pkg, now)` 重置 winStart → `pkg|winStart` 去重键全部失效 | `logScanMiss` 增加同包 8s 节流（`missThrottleAt`）——爆发期只记首条（首条样本已足够补规则），其余静默；微博 19 秒 3 次独立启动场景不受影响（>8s 间隔照记） | `AdSkipService.logScanMiss` |
| 4 | #56（v5.1.1）：候选样本 `关闭@(93%,9%) / 关闭@(93%,9%)` 双胞胎；#57（v5.2.0）：`播放列表@(16%,150%)`；#64：`返回@(110%,9%)`、`vid:checkbox@(187%,17%)` | ① collectSamples 不去重（同 label 同坐标重复占 1/4 样本额度）；② 弹窗/对话框 bounds 被裁剪或坐标系差异导致节点中心超屏，误导补规则点位 | ① label+坐标签名 HashSet 去重；② 越界中心剔除——新增纯函数 `AdDecisionCore.sampleOnScreen(cxPct, cyPct)`（±2% 容差，贴边键不算越界），JVM 可测 | `AdSkipService.collectSamples` / `AdDecisionCore.sampleOnScreen` |
| 5 | #64（v5.5.0）：边界自检日志只说「非跳过/关闭语义或越界」——两可含糊，微博案根因定位花了一整轮日志分析 | `actionBoundaryOk` 返回 boolean，拒绝时不知道哪层断言失败、拒绝的是什么目标 | 重构为 `boundaryCheck` 返回层码（`BND_OK` / `BND_FAIL_SCENARIO` / `BND_FAIL_SEMANTIC` / `BND_FAIL_FORM`）+ `boundaryLayerName`；`boundaryGuardLog` 记录失败层 + 目标签要（≤12 字截断）。下次日志直接回答「哪层拒绝、拒绝的是什么」 | `ComplianceGuard.boundaryCheck` / `AdSkipService.boundaryGuardLog` |
| 6 | run-tests.sh 引用 `RuleGovernorTest`/`ComplianceGuardTest` 两个套件，v5.5.0 发版声明「599 项断言全绿（+124：RuleGovernorTest 77 + ComplianceGuardTest 47）」，但两文件从未提交——新克隆环境跑 run-tests.sh 必红（本机验证：ClassNotFoundException ×2） | **破案：`.gitignore` 裸 `test` 模式把 `android-app/tests/com/zai/adshield/test/` 整目录静默吞掉**——作者本地跑测试全绿（文件在），`git add` 静默跳过（模式命中），声明与仓库从此分叉 | ① `.gitignore` 收敛为 `/test`（仅根级临时目录）+ `!android-app/tests/` 显式放行；② 补写两套件并扩展（ComplianceGuardTest 83 断言 / RuleGovernorTest 142 断言，均超声明数） | `.gitignore` / `tests/` |
| 7 | 代码审阅发现：`LearnGovernor.sdkGeneralize` 注释声称「与 AdSkipService SDK 窗口指纹同源」，但 `isAdActivityClass` 认的家族段（`qq.e.ads`/`gms.ads`/`bytedance.sdk`/`kwad`/`admob` 等）在泛化清单里并不存在——GDT 类窗（`com.qq.e.ads.LandingPageActivity`）被 `learnClass` 沉淀为学习规则却拿不到泛化标记 → 白等 72h 新生期 | 两表实际不同源（注释与实现不一致） | `sdkGeneralize` 补齐家族段（qq.e.ads/gms.ads/bytedance.sdk/kwad/admob/miui.zeus/doubleclick/unionad），两表真正同源 | `LearnGovernor.sdkGeneralize` |

## 二、验证

- **JVM 断言**：`./run-tests.sh` **700 通过 · 0 失败**（v5.5.0 基线：475 通过 · 2 失败——失败项正是缺失的套件）。
  - ComplianceGuardTest（83）：设备分级×电量 16 档矩阵 / 形态自适应 / 零扫描（含 v5.5.1 增补 + 反例不误伤）/
    边界三重断言层码 / **label 回落零放行面**（空 label 复现 v5.5.0 误杀形态、「跳过广告」通过、「领取福利」「确认」「立即更新」仍拒）/ 样本越界实锤坐标全拦 / 权限审计。
  - RuleGovernorTest（142）：RuleMerge 三源（权重/等价合并/动作冲突/学习影子/热规则排序/禁用过滤）/
    LearnGovernor（评分三构成/生命周期状态机/准入门/泛化清单）/
    SmartScanPlanner（L1/L2/L3 预算/深度门/指数退避状态机）/
    RuleEngine 第5层（更新双信号/视频前贴真实链路/悬浮球边缘门）+ 第0~3层回归
    （**issue#64 微博「跳过广告」命中 → 边界自检端到端锚点**）。
- **服务层编译**：AdSkipService.java（全部修改所在）经 Android API stub（ECJ）编译**零错误**；
  core 包随 run-tests 全量编译。
- **真机验证建议**：v5.5.1 APK 构建后重点观察——① 微博开屏是否恢复秒跳（日志应从「动作边界自检拦截」
  变为「⚡ 已点击跳过」）；② downloads.ui 类系统页应不再出现任何「开屏未命中」；
  ③ ting.android 弹窗动画期未命中日志应从 4 条/2 秒降到 1 条/8 秒。

## 三、影响面与红线

- **误触率红线（只降不升）**：全部修复不新增任何放行面——label 回落仅对已被规则文案验证过的目标生效
  （非跳过文案在语义层照拒）；零扫描增补只扩大「绝不扫描」集合；样本过滤只收紧诊断输出。
- **命中率红线（零妥协）**：微博误杀修复直接提升命中率（真命中不再被吞）；探针前置/宿主窗档案/分级预算
  等增益路径不受影响。
- **兼容性**：`actionBoundaryOk` 布尔版保留（既有调用方零破坏）；`Decision.label` 默认空串（既有 Decision 构造不受影响）。

## 四、遗留观察项（下版候选）

1. issue#63 的 `sdk:gdt` 泛化规则命中 0 次（置信度 70%）——LearnGovernor 的 PROBATION 复活闸（72h 内
   无第二次命中即退休）对泛化规则豁免，可能需要单独的「泛化规则零命中计数」观察窗口。
2. issue#64 的 `开屏未命中（weibo）树 13 节点 · 窗口 MPDialogActivity`——疑似微博小程序/弹窗容器，
   候选纳入 MiniProgramGuard 容器表（需更多日志证据，本版不动）。
3. weibo `MainTabActivity`（123 节点主界面）首次启动仍全量扫描——宿主窗档案需 3 次零候选才 CLEAN，
   属设计内冷启动成本；探针哨兵已保零 DFS 快路径。
