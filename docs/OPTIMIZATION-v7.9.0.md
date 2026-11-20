# v7.9.0 优化实录 · 源码审计轮：三处接线盲区根治 + 图标关闭钮路径 + 视频画像竞速根治

> 用户指令「先找根因，再改代码 · 先修架构，再加规则」全量落地实录：完整源码审计 → 根因实锤 → 修复 → 编译 → 测试 → 发布闭环。
> 上一轮（v7.8.0）实录见 [OPTIMIZATION-v7.8.0.md](OPTIMIZATION-v7.8.0.md)。

## 一、审计范围与链路追踪

完整审计（本轮从 v7.8.0 源码起步——工作副本 /home/z/skip-ad-repo，基线先立：v7.8.0 全量 6063/6063 断言全绿复跑确认）：

| 层 | 文件 | 行数 | 审计焦点 |
| --- | --- | --- | --- |
| 事件路由 | AdSkipService.onAccessibilityEvent | 1486~1850 | L1 前置过滤（自家包边界/后台内容丢弃/桌面/白名单/主开关/相机/零扫描场景/小程序容器/系统噪声/息屏/短信横幅）→ 事件 switch（state/content/clicked/windows-changed）各分支的扫描拉起接线 |
| 扫描调度 | bumpScanChain / nudgeScan / nudgePopupScan / videoPoll / tryScan | 2080~3360 | 18 档梯子 + 80ms 补扫节流 + 600ms 救回 + 零候选退避 + 内容页早退 + 宿主窗档案 + 视频画像/轮询针 |
| 插屏链路 | maybeInterstitialScan / executeInterstitial / InterstitialGuard | 2813~2966 | 四事件面拉起 → collectRoots → walk（语境/WebView/滚动收集）→ nativeFindTexts 双查询 → decideFromCandidates 闸⓪~③ → interBoundaryCheck 终闸 → execute 既有防线 |
| 决策核心 | AdDecisionCore.walk / decide / visit / isAdActivityClass | 437~1240 | Node Tree 遍历（深度 32/预算 600/去重）→ 候选收集（skipCands/corners/fakeCands/cdDigits）→ 评分排序 → SDK 窗指纹 40+ 段 |
| 出手与安全 | execute / ComplianceGuard | 3709~3810 | 边界自检三重断言（场景/语义/形态）+ 用户让路 + 点击梯子 + 多点防线 |

**审计方法**：沿两条独立链路（Splash 链路 / Interstitial 链路）逐层核对「纯函数 API 的服务侧装配」——纯函数层有 6063 项单测保护，装配层（C tx 字段赋值、分支内调用接线、事件 case 拉起）无断言覆盖，是本轮发现的三处盲区的共同结构性根源。

## 二、根因诊断 → 修复映射（三处接线盲区 + 两处路径缺口）

| # | 根因（代码级定位） | 用户场景映射 | 修复 |
| --- | --- | --- | --- |
| 盲区① | `maybeInterstitialScan` 从未给 `wctx.sdkAdWin` 赋值（Ctx 默认 false）——`decideFromCandidates(..., wctx.sdkAdWin)` 恒传 false → v7.8.0 闸⓪第三路「SDK 插屏窗指纹」整路死代码 | 「SDK 动态创建广告窗口」「Activity 不变化但页面内部切换成广告」（原生 SDK 插屏：无语境词、无 WebView） | `wctx.sdkAdWin = AdDecisionCore.isAdActivityClass(cls)`（与 tryScan 主路径同源同判——一行接线） |
| 盲区② | 零候选退避块的豁免窗（广告窗/Dialog/学习窗）跳过整个计数块 → `zeroCandScans` 冻结在 0 → `videoLike` 的 sdkAdWin 路径（streak≥3）在 SDK 广告窗上永远建立不起来 | 「开屏广告出现时间较晚时无法识别」（SDK 视频开屏——按钮常在视频末才挂树，观察窗应延长而不延长） | streak 无条件计数（豁免只挡杀链不挡计数——计数是画像观测源；上限 96 + 候选即清零保持） |
| 盲区③ | 非前台 Dialog 分支只走 nudgePopupScan（弹窗识别路径），插屏卫士从未被拉起；且此分支提前 return 从未登记 winClass → SDK 指纹判定拿到空串 | 「广告 SDK 创建独立 Window」「Dialog 广告」（com.qq.e.ads 家族 Dialog 插窗——事件 pkg 是 SDK 包） | 同分支接线 `maybeInterstitialScan(pkg, now)` + `winClass.put(pkg, cls)` 登记 |
| 缺口④ | 文字候选（nativeFindTexts）对纯图标钮（零文本零 desc）零候选 → decideFromCandidates 返回 null——无文字钮零路径 | 「广告按钮不是普通 TextView」「关闭按钮是图片」「关闭按钮只有图标没有文字」 | `InterstitialGuard.decideIconFromCandidates`（语境闸 SDK 窗/WebView+角标 + 形态闸 + 位置闸 + label=× 零终闸改动） |
| 缺口⑤ | WebView/H5 视频容器树慢速构建（内容事件持续到 ~2s）时退避杀链（streak≥3+2.6s）早于静默≥1.6s 的全画像成立；且轮询探针 5 词探不到图内「×」/拉丁 close | 「开屏广告 UI 变化后无法识别」「广告使用 WebView」（树慢速构建亚型 + 12~20s 段图片钮） | `videoArmHint`（streak≥3+窗龄≥2.2s+静默≥0.8s 免杀链）+ 探针词 5→10 + 每 3 针全量复扫 |

## 三、图标关闭钮决策（纯函数 · InterstitialGuard.decideIconFromCandidates）

```
walk 角落候选（isCornerControl 已把关：无文本 · 24px~35% 宽 · 顶/底带 · 贴缘）
        ↓ iconContextOk（语境闸——比文字路径更严）
SDK 插屏窗指纹在场 ──────────→ 放行（窗类即广告窗——无需角标）
WebView 在场 ∧ 广告角标同窗 ─→ 放行（H5 插屏签名）
仅语境词（无 WebView/无 SDK 窗）→ 拒（普通窗角落菜单/分享图标误触面）
        ↓ 逐候选闸
形态闸：closeFormOk 三路并集 + 面积 ≤2% 屏（图标级）
位置闸：顶带（cy≤25%）优先 > 底带（cy≥75%）> 中部绝不收（tabBar/菜单高发）
strongOnly（中部边带）绝不收
        ↓ FeedDecision(label="×")
execute() 既有防线全量复用（interBoundaryCheck 对 × 本就放行——终闸零改动）
```

安全性设计（宁可漏拦绝不误触——图标路径的误触面天然更大，语境门槛加倍）：
- 仅语境词在场的普通窗**绝不点纯图标**（菜单/分享/设置图标无语义证据）；
- WebView 无角标拒（H5 普通页面顶部导航栏的返回/分享图标与插屏 × 同带同形态——角标是唯一点分证据）；
- 中部图标绝不收（tabBar/底部菜单图标高发区）；
- 图标出手走既有点击梯子/冷却/多点防线（同一坐标去重、人手手势节奏）。

## 四、性能契约（本轮不牺牲功耗换命中率）

| 新增路径 | 功耗边界 |
| --- | --- |
| 图标钮决策 | 只在既有插屏扫描节流内（1.5s/次）走纯函数判定——零新增扫描 |
| streak 无条件计数 | 纯 HashMap 计数（原计数块的同一成本——只是豁免窗也计数） |
| arm-hint 豁免 | 只挡杀链不新增档位；静默 <0.8s 的窗照杀（退避照旧） |
| 外来包 Dialog 接线 | 复用 maybeInterstitialScan 自带 1.5s 节流 |
| 探针词 5→10 | 只在 videoLike 窗 1.5s/针的探针循环内（系统原生 C++ 子树搜索零 DFS） |
| 每 3 针全量复扫 | 只在 videoLike 窗（20s 内 ≤5 次；普通窗零轮询零复扫——pollWalkDue 契约） |
| 事件订阅 | 零变化（typeViewScrolled 评估后不订——滚动高频低收益，内容变化已驱动） |

## 五、实测验证（全部实测 · 无编造）

| 项 | 结果 |
| --- | --- |
| v7.8.0 基线复跑（修改前） | 6063/6063 全绿（23 套件）——工具链 ECJ 3.36.0 + aapt2/d8/zipalign/apksigner（build-tools r34 + platform-34）沙箱重建后复现 |
| P0 修改后全量回归 | 6063/6063 全绿（零回归——五处服务侧接线 + 两处纯函数新增不影响既有断言） |
| 新增断言 | +51（VideoSplashTest S9/S10 + InterstitialGuardTest S9 + InterstitialRoutingTest S10） |
| 终版全量 | **6114/6114 全绿（23 套件 · 总计通过 6114 · 总计失败 0——run-tests.sh 实测输出）** |
| ECJ 编译 | 零错误（核心 src + tests 全量） |
| APK 构建 | adshield-v7.9.0.apk · 853,558 B（836KB）· versionCode 79000 · versionName 7.9.0（aapt dump badging 实测）· 签名证书与 v7.8.0 同一（覆盖安装链保持） |
| 页面资产语义闸 | check-appjs.sh 5 项符号在场（版本记录/扫描日志/使用报告接线完整——CURRENT_RELEASE 已更新 v7.9.0） |
| 摘要 | MD5 `b4b7f9e8f5f5200ec269f46e14bc917a` · SHA-256 `c8053c27e37bf1205c84a3ca0e0584452c55606c536eb0f5d24e9a6fda50a5d2` |

真机侧验证边界（诚实声明）：本轮改动全部为 JVM 可单测的纯函数与服务侧装配接线；图标钮路径/外来包 Dialog 接线/arm-hint 竞速根治在 23 套件断言覆盖内验证（含九品牌 1:1 分辨率缩放与真值表），真机实测（OPPO/vivo/小米/三星/华为/荣耀/红米/一加/iQOO 各机型实装开屏/插屏对照）**当前沙箱无真机连接，未执行**——与既有 21 轮发布同一边界（JVM 断言全绿 + 构建链验证；真机依赖用户实装反馈闭环）。

## 六、修改文件清单

| 文件 | 目的 |
| --- | --- |
| android-app/src/com/zai/adshield/core/InterstitialGuard.java | +iconContextOk/ICON_AREA_MAX/decideIconFromCandidates（图标关闭钮纯函数） |
| android-app/src/com/zai/adshield/core/VideoSplashGuard.java | +VIDEO_ARM_SILENCE_MIN_MS/videoArmHint/VIDEO_POLL_PROBE_WORDS/VIDEO_POLL_WALK_EVERY/pollWalkDue |
| android-app/src/com/zai/adshield/AdSkipService.java | 五处接线：①wctx.sdkAdWin 接通 ②streak 无条件计数 + arm-hint 豁免 ③videoPoll 探针词表化 + pollIdx 递增 + 每 3 针全量复扫 ④外来包 Dialog 分支接线 + winClass 登记 ⑤图标钮决策接线 |
| android-app/tests/.../VideoSplashTest.java | +S9 画像竞速（11 项）/ +S10 探针词 + 全量复扫（11 项） |
| android-app/tests/.../InterstitialGuardTest.java | +S9 图标钮真值表（15 项） |
| android-app/tests/.../InterstitialRoutingTest.java | +S10 接线契约（12 项） |
| android-app/assets/www/app.js | CURRENT_RELEASE v7.9.0 + appVer/appCode 回落值 |
| docs/CHANGELOG.md / docs/OPTIMIZATION-v7.9.0.md | 版本记录 + 本实录 |
| scripts/publish-v790.mjs | v7.9.0 发布脚本（GitHub Release + asset + 仓库描述） |
| android-app/dist/adshield-v7.9.0.apk · dist/adshield-v7.9.0.apk | 三目录分发（本地两目录——第三目录 GitHub Release asset 随发布脚本上传） |
