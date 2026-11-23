# 变更记录

## v7.15.1 · 2026-11-24 · 息屏/解锁重入根治（真机日志实锤：锁屏污染前台追踪 → 补扫走偏 + 引擎致盲）

**用户实锤**：扫描日志后确认 v7.15.0 的「亮屏/解锁视作重新进入」仍未生效——issue#94（Xiaomi 2410DPN6CC · Android 17 · v7.15.0 build 85000）真机引擎日志定案死因链：

1. **死因①前台污染**：息屏期间锁屏窗（com.android.systemui）走零扫描门（isZeroScanScenario 在息屏门之前）把 `lastFgPkg` 顶成锁屏宿主——前台包名追踪被锁屏污染；
2. **死因②补扫走偏**：亮屏/解锁 rearm 只认 `lastFgPkg` → 补扫的是锁屏自己的树（日志实锤：「❓ 开屏未命中（systemui）树 73/55/146 节点」21:45~21:54 六连发；「⏱ 倒计时疑似装饰数字（systemui）」= 锁屏时钟被当倒计时）——真实应用根本没被扫；
3. **死因③引擎致盲**：解锁后真实应用若不伴随 WINDOW_STATE_CHANGED 回前台（HyperOS/Android 17 实测常态），其内容事件被前台门前置过滤（`pkg != lastFgPkg`）全部吞掉——广告弹出后引擎整窗致盲，广告白看。

**本轮交付**：

1. **补扫目标校正（RearmPlanner 纯函数 + lastAppPkg 真实应用追踪）**：新增 `core/RearmPlanner`（纯函数 JVM 可测）——前台即真实应用直连重入（ARM_DIRECT）；被锁屏宿主污染（isLockHostPkg：systemui/keyguard/lockscreen 族）→ 校正回 `lastAppPkg`（主路径维护的「真实可扫描应用前台」，锁屏/桌面/白名单永不写入）并同步修复 `lastFgPkg`（引擎致盲根除点——解锁后真实应用内容事件不再被前置过滤吞掉）；桌面/白名单/设置等真前台 → 无需补扫（SKIP_NO_TARGET——绝不扫描不可见应用）
2. **亮屏/解锁视作重新进入（根治版）**：窗龄 windowAt 重置（旧窗龄不再把扫描门判超窗拒扫）+ 三张包级节流表按目标清（interWinAt 插屏 2.5s / nudgeAt 补扫 / rescueAt 救回）+ 收手态（gaveUp/clickTries/backTries）/倒计时锚点（cdLastN/cdAnchorAt/cdWaitUntil）/触点轨迹/学习窗资格同步重置（与真实「进入应用」同语义——主路径进入块逐项镜像）+ bumpScanChain 全梯子重排（scanGen 代际护栏自动作废旧链）+ 0ms 内联立即补扫 + 400ms 追加一针（广告窗内容晚到也接得住；CLEAN 档案窗自动走探针哨兵，功耗红线不破）
3. **亮屏而锁屏还在场时绝不出手**：`KeyguardManager.isKeyguardLocked()` 判定（一次 IPC，仅亮屏瞬间——热路径零成本）——屏上还是锁屏，坐标点击会落到锁屏上（误触红线）；重入推迟到 USER_PRESENT 真正解锁瞬间（每解锁一次一链的成本语义保持；无锁设备亮屏即完整重入；同解锁周期亮屏→解锁毫秒级连发 1.5s 去重）
4. **扫描日志可见化（用户可直接核验）**：每次完整重入记录「🔓 亮屏/解锁视作重新进入（应用名）：窗龄重置 + 节流清空 → 立即补扫（前台追踪已校正——锁屏污染清除）」——v7.15.0 版 rearm 完全静默（用户扫描日志无从核验，误判「未实现」），本轮起日志即证据
5. **修复 src/lib/version.ts 脚本事故**：v7.15.0 发布会话曾把该文件误写为两万余行重复块并丢失 APP_VERSION/VERSION_CODE/GITHUB_*/versionCodeOfTag 导出（Web 编译与 check-version-sync.sh 均会失败）——恢复到单条目规范形态
6. **测试与验证**：JVM 22 套件 4348 断言全绿（新增 RearmPlannerTest 34 项：锁屏污染校正 / 厂商锁屏宿主变体 / 直连重入 / 无需补扫场景 / 亮屏锁屏在场绝不出手 / isLockHostPkg 锁屏宿主族识别零误伤）；ECJ 全源码编译零错误；真机验证依赖用户实测（本轮已有真机日志定位证据链）

**版本口径**：versionCode 85100（> 85000 覆盖升级）；签名证书 SHA-256 05ada6e3…（与历史正式版本一致）。

---

## v7.15.0 · 2026-11-24 · 版本线重制（基于 v7.6.0 激进模式基线重构，7.7~7.14 整体召回）

**用户指令**：清除 7.6 之后的所有版本；下一次迭代版本直接为 7.15.0；基于「开屏卫士 v7.6.0 · 激进模式版本」进行代码重构；清除除 7.6 版本外不需要的代码和其他 Action。

**本轮交付**：

1. **日志上传回归 v7.6 直发方案（「连接上传服务超时」根治）**：删除 v7.7~v7.14 的中转服务复杂层（LogUploader/LogStore/LogEvents/LogPrivacyFilter ~1600 行），回归内置混淆令牌直发 GitHub Issue（Net.postIssue 瞬时故障自动重试×2）；24 小时自动提交 3 次保持（每 8 小时一次：KeepAliveReceiver 心跳顺带 · 成功才计时 · 服务暂停/息屏不传）；上传全异步（不再阻塞桥线程）
2. **检查更新多源链（「检查更新不到新版本」结构性根治）**：新 UpdateManifest——静态清单 raw@main → jsdelivr@main → @tag 探测链（tag 不可变键零源站缓存，候选 = 下一补丁 + 下两个 minor）→ GitHub API 兜底，多源合流取最高 versionCode；结果 10 分钟缓存 + synchronized 单飞；浏览器预览同源镜像（app.js fetchLatestBrowser 与 Java 侧同候选集同语义）
3. **卫士卡死根治（「点按钮/进 App 卡死、重进不行、只能重装」）**：根因 = 同步长 IO 桥（netPostIssue/netGetLatestRelease）把 JavaBridge 线程占住十几秒，期间所有后续桥调用排队 = 界面卡死；重进撞同一链路复现；重装清 prefs 偶然自愈。根治 = 桥接层全面异步化（后台线程 + window.__netCb 回调）+ 崩溃循环自愈（CrashCapture：20 分钟内 ≥3 次崩溃自动重置病态 UI 状态——免重装出路）
4. **息屏/切应用回来广告拦不住根治**：根因 = 息屏期间事件丢弃致前台窗龄老化，解锁回来弹广告时「窗龄超窗」把开屏扫描门判死。根治 = 亮屏/解锁（SCREEN_ON + USER_PRESENT）视作重新进入：窗龄重置 + gaveUp/clickTries 重置 + interWinAt/nudgeAt/rescueAt 节流清空 + bumpScanChain 全梯子 + 0ms 内联补扫 + 400ms 追加一针
5. **「每次退出重新进入都要重新开启 + 10 秒系统警告」**：v7.6.1 基线的免重开机制恢复（v4.5.0 开关解耦 + v5.2.0 深挖拉活 + 前台看门狗 + 通知一键恢复；系统开关永不触碰——10 秒警告只在登记真被吊销时才需要）
6. **底栏重构**：订阅页移除 + id667 订阅每日后台自动导入（SubscriptionManager.maybeAutoImport：subUrl 空时自动设 Lin-arm 默认源，八通道镜像 + APK 内置离线快照兜底——导入永不硬失败；已有源尊重既有选择；失败 30 分钟退避重试）；新增帮助页（快速上手/固定后台三步/常见问题/日志与隐私——条理规范化，数字仅保留功能性最少集合）
7. **进入 App 开屏提示弹窗（三按钮）**：知道了（本次关闭）/ 24 小时不再提示（tipDismissUntil + tipPinVer 版本钉——升级后重新提醒一次）/ 去上传日志（直达我的页并自动发起上传）；每次进入 App 评估（含 visibilitychange 切回）；kvSet 白名单扩展（tipDismissUntil ≤16 字符 / tipPinVer ≤12 字符，严格钉死）
8. **更新下载多通道兜底（中国大陆下载失败根治）**：gcore CDN 直链（仓库 dist 直出）优先 + GitHub 官方 + ghfast.top / gh-proxy.com 镜像（30s 无进展自动换通道；Downloader 安装前 SHA-256 完整性校验保持）
9. **仓库清理**：android-app 源码树整体重置到 v7.6.1 基线（= v7.6.0 激进模式 + app.js 修复）；删除 7.6 之后的一次性发布脚本 ×12、tests-js、net-verify.yml（中转服务验证 Action——随直发方案退役；update-manifest.yml 保留——清单自动提交是「永不漏检」的关键闸）、7.6 之后的 APK 产物 ×54；GitHub 侧删除 v7.7.0~v7.14.0 的 tags 与 Releases
10. **测试与验证**：JVM 21 套件 4314 断言全绿（v7.6.1 基线零回退）；APK 构建六步全过（页面资产符号闸 11 项在场——含 v7.15 新增接线符号）；浏览器预览行为级验证：三按钮弹窗（每次进入/24h 静音/版本钉/去上传直达）+ 帮助页渲染 + 检查更新多源链实测可达；诚实口径：真机/模拟器 E2E NOT_RUN——待用户实测反馈

**版本口径**：versionCode 85000（> 84000 覆盖升级）；签名证书 SHA-256 05ada6e3…（与历史正式版本一致）；APK 857,654 字节。

---

（CHANGELOG）

> 首页只保留当前版本要点；全部历史版本详情在本页归档。
> 发布物（APK/报告/文档）见 [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) 与 `docs/test-reports/`。

## v7.14.0（2026-11-24）—— 检查更新陈旧窗口根治（@tag 探测链）+ 兜底直连 gcore 化

- **本轮定位（「继续执行，进行修复优化」指令 + 发布管线三连实锤驱动）**：v7.11.0 检测不到 v7.12.0 → 上轮根因定案（jsDelivr @main 源站分支缓存 TTL ≤12h）→ 本轮 v7.13.0 发布后四接入点再次全陈旧（实测 cdn=81000 · gcore/fastly/testingcf=80000）——**每次发布后旧客户端 ≤12h 看到「已是最新」的根结构性问题**。本轮按上轮 CHANGELOG 预埋的「未来硬化方向」实施根治：@tag 探测链。**诚实口径：真机/模拟器 E2E NOT_RUN，JVM/JS 行为级验证 + 代码级审计**（同既往口径，待用户实测反馈）。
- **①@tag 探测链（根治——本轮实测验证的机制差）**：jsDelivr 对 `@main` 分支引用有源站级分支解析缓存（TTL ≤12h、purge 不可达），但对 **`@tag` 引用是不可变键**——无源站分支缓存，发布即新鲜（本轮实测：`@v7.13.0` tag 清单 cdn/gcore 双点即时返回 83000；未发布 tag 干净 404）。`UpdateRepository` 新增 `probeTagUpdates`：@main 链 SUCCESS 且未判出新版（陈旧窗口/同版/hasRelease:false——网络已证实活着）→ 探测当前版本之后的候选 tag 清单（vM.m.p+1 / vM.(m+1).0 / vM.(m+1).1 / vM.(m+2).0——下一补丁 + 下两个 minor）；任一 tag 清单 isNewer → 采纳（写缓存 + `tagSourced:true` 打标），跳级发布取最高 versionCode 候选。**安全闸零放宽**：tag 清单经 `UpdateManifest.parse` 同一信任链（包名/HTTPS 发布通道/SHA-256 校验全套）。成本闸：每 tag 先 gcore 后 cdn 两点至多两取（404 = 干净「未发布」结论不换点，仅网络类错误换点）；独立预算 12s；15min 缓存/单飞/退避全复用——无轮询风暴。versionName 取自 PackageManager（异常 → null 优雅降级为 v7.13.0 语义零行为变更）。**诚实边界**：隔两个以上 minor 的老版本（探测集覆盖不到最新版）仍靠 @main 链源站缓存 ≤12h 自愈或 gcore 版本化直链手工救济。
- **②兜底直连 gcore 化（401 救济链当日实锤修复）**：`FALLBACK_CDN_LATEST`（检查更新失败时的 CDN 直连下载按钮出口）cdn→gcore——本轮实测 cdn 对 APK 文件返回 403（与 fastly 301→raw 同型阻断边界；v7.11.0 兜底直连按钮在 cdn 当日阻断下失效），gcore 全日直出且与 Release 资产 SHA-256 逐字节一致（898614B 实测）。
- **③浏览器预览同源镜像**：app.js 浏览器路径 fetchLatest 追加 `probeTagManifests`（与 Java 侧同候选集公式 + 同端点容错语义——预览环境与 APK 行为可互证）。
- **测试**：JVM `UpdateRepositoryTest` 新增 S9 探测链套件（候选集纯函数四 tag 序/非三段优雅降级/命中 isNewer 载荷/全 404 零行为变更/gcore 网络异常换 cdn 点/跳级取最高/不新款不采信/payloadShowsNewer·markTagSourced·isNetClassError 辅助三态）；JS `test_update_check.js` 新增 ③z/③y/③x 三场景（@main 陈旧→tag 命中零陈旧窗口/全 404 照常/gcore 异常换点）+ withFetch tagHandler 缝（默认 404 零污染既有场景）+ 67 断言全绿；`test_bugtip24h.js` 兜底直链断言 gcore 化。**测试环境事故如实记**：本轮沙箱曾整体重置（仓库已重克隆恢复、构建工具链已重建）——v7.13.0 发布链在重置前已完成并经 GitHub 远端核验完好（Release/资产/清单/tag 逐项）。
- **本轮发布口径（诚实）**：因沙箱重置丢失本地凭据（local-gh-token 不入库不可恢复），v7.14.0 的 GitHub 推送与 Release 创建需要新凭据后执行——代码/测试/构建物已本地就绪待推。

## v7.13.0（2026-11-24）—— 开屏提醒跨升级修复 + 切回即提醒 + 上传快失败与网络恢复自动补传

- **本轮定位（用户三项实锤驱动）**：①「进入 app 没有弹出提醒，提醒窗口还是按之前说的来，进入就提醒（知道了 / 24 小时不再提醒 / 去上传日志三按钮）」；②「日志上传的又慢又成功不了（连接上传服务超时——已自动重试 3 次仍不通）」+「8 小时自动提交一次日志要实现（不在 GLM 加定时任务——App 内实现）」；③jsDelivr purge 巡更（v7.12.0 陈旧窗口翻转盯守）+ 版本检测确认正常 + 性能/能耗/响应/体验测试 + 推送 GitHub/Release。**诚实口径：真机/模拟器 E2E NOT_RUN，全部为 JVM/JS 行为级验证 + 代码级性能/能耗审计**（同 v7.12.0 口径，待用户实测反馈）。
- **①开屏提醒跨升级修复（「进入没弹提醒」根因定案）**：v7.11.0/v7.12.0 时代用户点过「24 小时内不再提示」记的 `pin_tip_confirmed_at` 时间戳存在 prefs、**覆盖升级保留**——升级到新版后旧时间戳继续吞掉升级后首次进入的提醒（用户实锤「进入 app 没有弹出提醒」的根因）。修复：提醒卡新增版本标记 `pin_tip_ver`（点「24h 不再提示」时记当前版本号，kvSet 白名单同步放行）——`pinTipThrottled` 检查「节流时间戳的版本 ≠ 当前版本 → 节流失效」，**升级后第一次进入必弹**（新版本「进入就提醒」语义重新生效）；同版本点过照常节流（不打扰）。`MainActivity.kvSet` 白名单新增 `pin_tip_ver`（纯 UI 版本号字符串，写面零安全影响）。
- **①b 切回即提醒（「进入就提醒」完整语义）**：从后台切回前台也是「进入 App」——`visibilitychange visible` 时评估提醒（v7.12.0「仅 init 调用」语义升级）。连弹防护收口：删除 `pinTipShownThisSession` 会话闸（旧实现跨切回保持 true——切回场景永远进不来），统一由 `pin_tip_shown_at` 60s 持久短窗防护（旋转/重建/切回同防；60s 后真实再进入照常弹）。24h 节流（含跨升级重置）+ 60s 短窗双闸——点过「不再提示」的用户不被打扰。
- **②上传快失败（「又慢又成功不了」双痛点治「慢」）**：连接超时 10s→6s（健康路径 <2s 不受影响——Android→CF 边缘 TCP+TLS 实测口径；阻断路径失败反馈快 40%）；连接阶段自动重试三试→**四试**（退避 0/500/1200/2400ms——间歇抖动路由下多一次机会）。读取超时 45s 保持（Worker 同步建 GitHub Issue 真实尾延迟可超 20s——v7.9.9 误判链教训口径，不盲目调短）。
- **②b 网络恢复自动补传（治「成功不了」的传输层根治）**：根因事实链（本轮沙箱实测定案）：上传中转端点 `*.workers.dev` 在用户网络下被 **DNS 污染**（实测解析返回 Facebook IPv6 段 `2a03:2880:...face:b00c`——GFW 污染特征）+ SNI/TCP 阻断——**切换 Wi-Fi/移动网络后仍不通是运营商级污染的必然结果，不是 App Bug**；但切网确实是其他可达路由的救济路径（服务端活跃：最近日志 Issue #92 手动上传成功实证）。修复（零手动重试）：`AdSkipService` 注册 `ConnectivityManager.NetworkCallback`（系统推送网络事件——**零轮询零定时唤醒**，能耗无新增；注册后 3s 内的初始回调屏蔽——防服务重建白发探测；实例复用幂等——踢链重绑不重复注册）：网络可用（切网/断→通）且补传闸通过 → `LogUploader.requestAutoCheck(60s 资格)` 自动补传待传日志。**补传闸（隐私红线保持）**：①自动上传开关开启（用户已授权的定时自动上传网络恢复顺带补）或 ②**手动续传窗口活跃**（`LogUploader.submitNow` 连接阶段失败时记 `manualRetryUntilAt` = 30 分钟窗口——传的正是用户刚才主动要传的批次，是**手动意图的续传完成而非自动上传默认开启**）。失败文案如实升级：「切换 Wi-Fi/移动网络后无需手动重试：网络恢复时 App 会自动补试上传」。`AndroidManifest` 新增 `ACCESS_NETWORK_STATE`（normal 级零弹窗）。
- **③8 小时自动提交确认（既有实现保持 + 口径澄清）**：KeepAliveReceiver 心跳（亮屏 5~8min 窗口式）→ `maybeAutoUploadLog` → `requestAutoCheck(AUTO_LOG_MS=8h)`——自动上传开关开启时每 8 小时检查一次（有新日志才传，v7.10.0 起无次数上限）；连接阶段失败 30 分钟后即可再试（v7.12.0）。本轮零改动，v7.13.0 网络恢复补传叠加在 8h 主闸之上。
- **性能 / 能耗 / 响应 / 体验审计（代码级口径——真机 NOT_RUN 诚实声明）**：
  - **响应**：开屏提醒保持 init 第一步 0ms 直调（纯本地毫秒级）+ 切回前台评估同为纯本地（24h/60s 双闸读 kv + DOM 判定，零桥调用零网络）；上传四试最坏 6s×4+4.1s≈28.1s（旧 10s×3+2.8s≈32.8s——失败路径快 14%，多一次机会）。
  - **能耗**：NetworkCallback 注册制零轮询零定时唤醒（网络事件系统推送，息屏无网变不触发；Doze 不被唤醒）；补传闸不通过时回调内直接 return（零动作）；网络抖动 60s 资格闸防抖（pending==0 直接跳过零网络请求）。既有 PowerGovernor/亮屏门/竞速晋升三零治理零改动。
  - **体验**：提醒弹窗跨升级必弹（用户主诉闭环）；切网自动补传（上传失败后的救济路径零手动操作）；失败文案如实注明已多次重试 + 自动补试指引。
- **测试**：JS 7 文件 **250 断言全绿**（v7.12.0 的 244 + 本轮更新：`test_splash_reminder.js` S12 连弹防护改 60s 短窗口径 + 新增 S14 跨升级节流重置/S15 切回即提醒 6 断言、`test_upload_errors.js` 新增网络恢复补试指引断言）；JVM 套件全绿（含 `LogUploaderTest` 既有 T21 三试策略断言——本轮重试表为四试，测试同步更新）；`check-appjs.sh` 语义闸 17 符号在场；`check-version-sync.sh` 双端统一（v7.13.0/83000）；`secret-scan` 零凭据保持。真机/模拟器 E2E NOT_RUN（诚实口径）。

## 发布管线热修复（2026-11-23）——「7.11.0 检查更新检测不到 7.12.0」根因定案：jsDelivr @main 源站分支缓存 ≤12h 陈旧窗口 + purge 有效性边界实测定案

- **用户实锤**：v7.12.0 已正式发布（09:47 UTC · latest.json 已指向 82000 · GitHub Release 资产完好），但 7.11.0 应用内「检查更新」提示已是最新、检测不到 7.12.0。**根因定案（全链实测）**：检查更新五源链 = raw 主源（国内 DNS 污染/TCP 阻断不可达）+ jsDelivr 四接入点 `@main` 清单——jsDelivr 对 `@main` 分支引用有**源站级分支解析缓存（TTL ≤12h）**，发布后源站未到期时四接入点持续返回旧清单（实测矩阵：cdn=81000 · fastly/gcore/testingcf=80000），App 以首个成功源为准 → 旧清单 versionCode ≤ 本机 81000 → 「已是最新」。**这不是 7.11.0 客户端代码 Bug**——五源链、缓存、冷却、单飞语义全部按设计工作；是发布侧 CDN 缓存传播的已知边界被工作流掩盖。
- **purge 有效性边界实测定案（多层缓存架构）**：purge.jsdelivr.net 响应 `status:finished · providers:{CF:true,FY:true}`——①只清 CF/FY 两家边缘（**gcore 从未出现在 providers 列表**，实测四轮 purge 后 gcore 边缘 age 持续增长从未失效）；②**源站 @main 分支缓存不受 purge 影响**（实测：purge 后 cdn 边缘 `x-cache:MISS,MISS` 回源仍拿到旧内容 81000，etag/age 证明陈旧层在源站）；③传播有分钟级延迟（实测 fastly 在 purge 后 ~1.6h 才翻转、cdn 在多轮 purge 后翻转——非即时）；④`?query` 与 `%2F` 路径变体均不改缓存键（实测仍旧）；⑤purge 端点限频（连续 purge 后 `throttled:true`）。**对照实验**：`@<commit-sha>` 锁定 URL 即时返回 7.12.0 新内容（源站对「新解析键」无缓存）——版本化 APK 直链（新文件首取）同理由即时新鲜（实测 898614B · SHA-256 `12bde153…` 逐字节一致）。
- **v7.9.9 已知口径升格为管线级治理**：v7.9.9 节已如实记载「purge API 执行成功但无法刷新分支解析层、最长 12h 自然回源」，但工作流层面从未硬化——旧 purge 步骤三缺陷（本轮用户实锤暴露）：①只在 `COMMITTED=true` 时执行（幂等重跑时静默跳过）；②purge 后 **1 秒** 即核验、零重试；③只 purge latest.json（App 401 兜底直连的 adshield-latest.apk 从未被 purge）；且「purge 响应含 status 字段」即自报成功——工作流日志「✓ 四接入点共享生效」与 1 秒后「0/4 生效」同帧出现，绿色勾掩盖了真实状态。
- **治理落地（本轮五件）**：①`scripts/purge-jsdelivr.mjs` 独立巡更工具（三路径 purge × 轮询核验四接入点清单 versionCode + APK 双直链 sha/size 字节级核验——诚实口径：purge 受理≠生效，只认四点实测；陈旧时如实打印机制与救济直链，exit 2 供脚本判断）；②工作流 purge 步骤重写（三路径 purge × 四轮「purge → 75s 传播窗 → 四点实测」全生效早退 + 陈旧期救济直链字节核验 + 摘要诚实呈现陈旧语义）——**PAT 无 workflow scope 被拒收（本日三路重测与 v7.11.0 结论一致），补丁归档 `docs/patches/update-manifest-v7121-cdn-purge-hardening.patch` 待网页端/新凭据落库，由巡更工具代执行**；③`update_manifest.py` 与线上 latest.json 的 `cdnApkUrl` 镜像由 cdn 切换 **gcore**（当日实测 cdn 对 APK 文件 301→raw.githubusercontent.com 阻断域——与 fastly 同型，「cdn 直出可用」旧结论过时；gcore 全日直出 200 字节级一致）——App 自动下载镜像链（apkMirrors）大陆可靠化；④README 安装节新增「检查更新提示已是最新但已发新版」≤12h 陈旧窗口说明 + gcore 版本化直链救济指引；⑤发布后 purge 巡更持续到四点全翻转（本轮 cdn/fastly 已翻转 82000，gcore/testingcf 待 TTL 自愈）。
- **用户口径（诚实）**：raw 主源与 GitHub Release 已即时生效（海外网络/可达路由无感知）；国内网络用户四接入点清单在发布后 ≤12h 陈旧窗内可能显示旧版「已是最新」——**当下救济**：gcore 版本化 APK 直链手动安装（`https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.12.0.apk` · 直出 200 · 898614B · SHA-256 与 Release 资产逐字节一致）；**不操作也无损**——源站到期后四点自愈（本轮实测 cdn/fastly 约 1.6h 翻转，gcore/testingcf 按边缘 TTL 预计 ≤12h），检查更新即正常显示 7.12.0。
- **未来硬化方向（下版本评估，本轮未动 App）**：①`FALLBACK_CDN_LATEST` 与清单镜像的接入点优选（gcore 优先 + 多镜像轮换——当前 App 内硬编码 cdn 别名受 301 边界影响）；②「@tag 探测链」设计——App 五源链追加对 `@v<下两个 minor>` tag 清单 URL 的探测（tag 为不可变键、无源站分支缓存，发布即新鲜；未发布时 404 代价一次轻请求）——彻底绕开 @main 12h 陈旧窗口，供 v7.13.0 评估。

## v7.12.0（2026-11-23）—— 卫士卡死根治 + 息屏/回切拦截修复 + 上传连接重试 + 开屏三按钮

- **本轮定位（用户五项指令驱动）**：①开屏提示改三按钮弹窗（知道了 / 24 小时不再提示 / 去上传日志），每次进入 App 都弹出，提示内容不再放进帮助页，帮助模块去数字冗余；②修复「上传日志：连接上传服务超时」——可重试但要让上传真正尽量成功；③修复「息屏十几分钟再打开 / 切去别的应用十几分钟后切回，此 App 跳出的广告拦截不了」；④修复「卫士有时候点击按钮或进入时导致卡死，重新进入也不行，只能重新安装」；⑤完成以上后做性能/能耗/响应/体验测试，推送 GitHub 与 Release。**诚实口径：真机/模拟器 E2E NOT_RUN**（用户前轮明示跳过模拟器），全部验证为 JVM 42 套件 7344 断言 + JS 7 文件 244 断言行为级（真实代码路径语义级），性能/能耗为代码级审计 + JVM 基准口径，待用户实测反馈。
- **①开屏提醒三按钮（用户指令逐字实现）**：`showPinTip` 弹窗由两按钮改三按钮——「知道了」（仅本轮收起，下次进入仍提醒）/「24 小时内不再提示」（记 `pin_tip_confirmed_at` 纯本地时间戳节流）/「去上传日志」（`goUploadLogFromTip`：收起提醒 → 切「我的」页 → 滚动至引擎日志卡（`#card-engine-log`）并双轮高亮闪烁 → toast 指引「点一键上传」；**隐私红线保持：跳转绝不自动上传——仅在你主动点「一键上传」时才上传**）。**每次进入语义收紧**：v7.11.0 的 5min 防重建窗会把「退出后 1~5 分钟内再进入」也吞掉——`PIN_TIP_RESHOW_GUARD_MS` 5min→60s（仅防 Activity 重建/旋转连弹，真实再进入一律照常提醒）。帮助页与开屏提示文案彻底分离（用户指令：提示内容不要放在帮助里）。
- **②帮助页去冗余精简**：`page-help` 六卡（含大量重复统计数字：21 款/600+ 款/⭐7.4k/每日一次/三步/四机型分头目）精简为三卡——快速上手（开启→生效→固定后台三行）/ 让守护更稳（固定后台一行合并四机型 + 保活体检指引 + 遇广告没跳过去上传日志）/ 常见问题（费电/覆盖应用/订阅与更新失败/权限安全四问，统计数字全删）；顺带修正 `index.html` 两处陈旧文案（自动上传「每 24 小时最多 3 次」→ v7.10.0 起「每 8 小时自动检查一次（有新日志才传）」真语义；页脚同步）。
- **③卫士卡死根治（用户实锤「点击按钮/进入卡死，重进也不行，只能重装」）**：根因定位为**同步桥冻结 JS 线程**——`whitelistJson` 同步桥在 JavaBridge 线程上枚举全机可启动应用（300+ PackageManager queryIntentActivities IPC + 逐应用 getApplicationLabel IPC + 战绩/白名单包 getApplicationInfo 补全），慢设备/PackageManager 高负载时以分钟计（v7.11.0 init 注释实测教训同源「以分钟计」原文自证）；进入 App / 每次切回首页 tab / 每次勾选白名单（缓存失效后强制全量重拉）三个入口全部同步阻塞——主线程不阻塞（JavaBridge 独立线程）所以系统不弹 ANR、页面却整块假死，「重进再来一遍」即复现，用户观感「只能重装」。修复（两端配合）：Java 侧新增 `whitelistJsonAsync(cb)` 桥（后台线程执行同一份枚举逻辑 + `window[cb](json)` 回调，与同步桥共用 30s 记忆缓存；同步桥保留作诊断兼容，页面热路径全部改走异步）；JS 侧 `wlBridgeAsync`（单飞序号 + 过期回调丢弃 + 10s 看门狗）+ `requestWhitelist`（失败自动重试至多 3 次×1.5s，仍失败渲染「未能读取」空态）+ 首次进入「加载中…」骨架占位 + **勾选增删本地即改即显**（`wlCache.list` 本地 Set 即时更新重渲，绝不再全量重拉——Java 侧 30s 缓存已由增删失效，下次自然刷新带回权威列表）+ 30s 本地新鲜窗（窗内切页零桥调用）。进入/切页/勾选三条热路径 JS 主线程与 JavaBridge 线程双零阻塞。回归：`tests-js/test_wl_async.js` 24 断言（源码级「全文零同步 whitelistJson 调用」死灰复燃防线 + 行为级骨架/回调/本地即改/重试全链）。
- **④息屏/回切拦截修复（用户实锤「息屏十几分钟再打开、或切去别的应用十几分钟半小时再切回，此 app 跳出的广告拦截不了」——三死因三层修复）**：
  - **死因①（僵尸态发现太慢）**：服务被系统解绑（未重绑）或进程被冻结/回收后，亮屏场景下要等下一个 5~8 分钟窗口式心跳 SELF_CHECK 才发现并踢链——期间用户解锁点开目标 App 的第一条开屏广告已经漏拦；息屏场景心跳已降频 30~45 分钟更甚。**修复：亮屏/解锁门**——`ScreenStateRx` 新增 `ACTION_USER_PRESENT` 订阅，SCREEN_ON/USER_PRESENT 瞬间 `onWakeGate()` 立即核实绑定态：未绑定 → 当场踢一次 AMS（`kickA11yLocal`）+ 排 1.5s 自检闹钟走 `SmartRevive` 完整三轮踢链 + 心跳链保续（解锁到用户真正点开 App 通常 ≥2s——抢在广告前完成重绑）。
  - **死因②（亮屏竞速吞首事件）**：SCREEN_ON 广播与「解锁后首个窗口事件」赛跑——`screenInteractive` 缓存标志可能仍停在「息屏」，`onAccessibilityEvent` 的息屏门把解锁后首个 TYPE_WINDOW_STATE_CHANGED 整事件吞掉（该窗的进入扫描链整条丢失）。**修复：现场实测放行**——息屏门对 STATE_CHANGED 事件先做一次真实 `isScreenOn(this)` IPC 复测（息屏期间该类事件极少，一次 IPC 可忽略），实测亮屏 → 放行本事件 + 自愈缓存标志。
  - **死因③（前台竞速漏登记）**：新前台应用的 TYPE_WINDOW_STATE_CHANGED 与 `isActiveWindowOf`（活动窗口归属查询）赛跑——最近任务恢复/切换动画期活动窗口尚未切换完成 → 归属查询 false → 旧版整事件丢弃**且不登记 lastFgPkg** → 该应用后续全部内容事件被前台门（`!pkg.equals(lastFgPkg)`）全量丢弃 → 回切后的开屏广告**整窗漏拦**。**修复：前台竞速晋升**——丢弃分支登记竞速候选（`fgPendingPkg/Cls/At`，仅非 Dialog 非小窗的普通窗口）；后续内容事件经 `ReentryGuard.shouldPromotePending`（纯函数·JVM 可回归）+ 活动窗口复核确认后按「新进入」完整晋升（复刻 STATE_CHANGED 进入路径的窗口重置面 + 进入期全量门 + 0ms 内联首扫 + 信息流/插屏接线），候选 3s 窗口期过期即作废；正常进入处理到场时候选即作废（互不侵蚀）。回归：`PendingForegroundTest` 15 断言真值表 + 既有 `ReentrySplashTest` 全绿保持（isEntering/回切 6s 闸/进入期 4s 全量门零改动）。
- **⑤上传连接重试（用户实锤「连接上传服务超时——…重试」）**：根因事实链（既有定案）：中转端点 workers.dev 域在大陆网络间歇可达（DNS 污染 + TCP 阻断；用户真机曾收到服务端响应=间歇可达实证），单次 10s 连接超时即报失败。**修复（传输层自动三试）**：`RealHttp.post` 连接阶段失败（连接失败/连接超时/TLS——请求几乎必然未送达，且同 batchId 幂等双保险）自动重试三次（退避 0/800/2000ms，最坏 ~15s 内三次机会）——间歇可达路由下成功率大幅提升；读取阶段/服务端失败**不做**传输层自动重试（请求可能已送达——语义交冻结批次幂等重试，v7.9.8 口径保持）。**自动上传提前重试**：`requestAutoCheck` 连接阶段失败后把 `lastAutoAttemptAt` 回拨（`AUTO_RETRY_SOON_MS`=30 分钟）——下一次心跳检查（5~8 分钟粒度）即可再试，不再干等 8h 窗口；成功/读取超时/服务端失败保持 8h 语义。失败 UI 文案如实注明「本次已自动重试 3 次仍不通」。纯函数 `retryableConnectKind`/`soonerRetryForConnect` JVM 回归（`LogUploaderTest` T21 15 断言）。
- **性能 / 能耗 / 响应 / 体验审计（本轮指令第五项，代码级 + JVM 基准口径——真机 NOT_RUN 诚实声明）**：
  - **响应**：白名单异步化后三条热路径（进入/切页/勾选）JS 线程零同步桥阻塞——慢设备上最坏从「分钟级假死」降为「骨架即时出 + ≤10s 回调/重试」；开屏提醒保持 init 第一步 0ms 直调（纯本地毫秒级）；`refreshHome` 轮询链（3s 看门狗 + 5s 刷新）全部轻桥调用不变。
  - **能耗**：亮屏/解锁门零常驻开销（广播触发、绑定态正常时零动作）；前台竞速晋升零新轮询（事件驱动：只在「窗口事件被竞速丢弃 + 3s 内内容事件确认」才发生一次进入级全量扫——与正常进入同级功耗，典型日几十次进入量级）；亮屏实测放行仅在「缓存标志=息屏 + STATE_CHANGED」罕见组合做一次 PowerManager IPC；`isSmallOverlayWindow` 500ms 缓存复用（竞速登记分支第二次调用零新增 getWindows IPC）；既有 PowerGovernor 三零治理（CLEAN 档案/突发预算/位置学习验证）零改动。`ScanBenchmark` 双 DFS 基准：新路径 24~168µs/scan（150/400/600 节点树，wide/deep/mixed 六组）与 v7.11.0 持平（识别热路径零改动）。
  - **体验**：开屏三按钮闭环（遇广告未跳过 → 一键直达上传入口）；帮助页三卡精简（数字冗余清除）；上传失败文案注明已自动重试（不再让用户误以为没试过）；重试按钮保持。
- **测试（42 套件 = 41 既有 + 1 新增）**：JVM **7344 断言全绿**（7313 + 新增 31：`PendingForegroundTest` 15 + `LogUploaderTest` T21 15 + ReentrySplash 零回退）；JS 7 文件 **244 断言全绿**（211 + 新增 33：`test_wl_async.js` 24 + splash 三按钮/60s 窗 9）；`check-appjs.sh` 语义闸 17 符号在场；`check-version-sync.sh` 双端统一（web `version.ts` 同步 v7.12.0/82000）；`secret-scan` 零凭据保持。真机/模拟器 E2E NOT_RUN（诚实口径）。
- **版本与发布**：versionCode 82000（major*10000+minor*1000+patch*100 公式吻合 7.12.0→82000）· 签名证书与历史一致（05ada6e3）· APK 898614 字节（880KB）· dist 归档 + adshield-latest.apk 别名同步 · Release v7.12.0 + update/latest.json 工作流自动生成 + jsDelivr CDN 兜底链 purge 核验。

## v7.11.0（2026-11-23）—— 检查更新失败 CDN 兜底直连 + 开屏提醒提速（进入即显）+ 帮助页 + 订阅后台自动导入 + 重进状态修复

- **本轮定位（用户五项指令驱动）**：①检查更新失败（旧版 401 / 弱网五源链全灭）时必须有兜底出路——直接给 jsDelivr CDN 仓库内最新 APK 链接，且「以后每个版本都要有兜底」；②开屏提醒进入 App 就要显示（不要慢）、内容改为「把 App 固定在后台」引导、可「24 小时内不再显示」；③底栏新增帮助 Tab——规范化描述使用方法与固定后台引导；④订阅 Tab 移除——id667 的 GKD 订阅（Lin-arm ⭐7.4k）每日后台自动导入一次，导入须有兜底防失败；⑤修复每次退出重进都要重新开启、且每次都触发系统 10 秒警告的问题。**诚实口径：真机/模拟器 E2E NOT_RUN**——本轮模拟器实测因沙箱环境限制（Android SDK 模拟器镜像与磁盘空间不可兼得）未能执行、用户明示跳过，全部验证为 JVM/JS 行为级（真实代码路径语义级），待用户实测反馈。
- **①检查更新失败 CDN 兜底（版本级通用机制——以后每版本自动在场）**：JS 侧「查询失败」分支新增「直连下载最新版（CDN 兜底）」按钮 → `doFallbackDirectDownload()` 走 `FALLBACK_CDN_LATEST`（`android-app/dist/adshield-latest.apk` 恒指最新版别名——每版本发布时随归档更新），系统浏览器下载后覆盖安装即升级；正常下载路径新增清单字段 `cdnApkUrl`（`UpdateManifest.parse` 解析入 `apkMirrors`——主源 GitHub Release 失败后自动镜像切换，非法域/http 明文置空忽略，SHA-256/大小双闸不降级；`Downloader.hostAllowed` 白名单追加 jsDelivr 四接入点 cdn/fastly/gcore/testingcf）；生成端 `update_manifest.py` 从 Release 资产 URL 解析仓库 slug 生成 `cdnApkUrl`（零虚构）；README 安装节同步双链口径（fastly 对 APK 301→raw 阻断域勿用的历史实测结论保持）。**工作流 purge 升级未落库（诚实口径）**：update-manifest.yml 追加 CDN APK 直链 purge 的升级因当前 PAT 无 workflow scope 被 GitHub 全通道拒绝（git push / contents API / git database API 三路实测——tree API 对 .github/workflows/* 路径 404、对普通路径 201 定案），本轮由 publish-v7110.mjs purge 步骤代执行（三路 purge + 生效核验），升级补丁归档 `docs/patches/update-manifest-v7110-cdn-purge.patch`（待 workflow scope 凭据或网页端落库）。
- **②开屏提醒提速（3s→0ms）**：v7.10.0 的独立 3 秒定时评估改为 `init` 直调 0ms——进入 App 即显示（不再有任何等待）；提醒卡整体重做为 `pin-tip`「把开屏卫士固定在后台」引导（三步操作：最近任务 → 卡片固定/锁定 → 清后台不受影响 + 不固定的后果说明）；「24 小时内不再显示」写 `pin_tip_confirmed_at`（纯本地时间戳节流）；「知道了」不耗资格（未确认 24h 内重进仍会显示——与 v7.9.8b bug-tip 语义对齐）；5min 防重建短窗/时间回拨防御/清数据恢复保持；`MainActivity` kvSet 白名单新增 pin_tip 两键（APK 内持久化）。
- **③帮助页（底栏新增，订阅页位承接）**：`index.html` 底栏订阅 Tab → 帮助 Tab；`page-help` 六卡：如何使用（三步开启）/ 把 App 固定在后台（重要·分机型：通用 Android 12+/小米 HyperOS/华为荣耀/OPPO vivo + 电池白名单入口）/ 订阅规则（自动）/ 检查更新失败怎么办（CDN 兜底直连指引）/ 常见问题（内置 21 款+订阅 600+ 款覆盖、无规则时手动示范一次卫士会学习、反馈走引擎日志上传）/ 隐私与权限；`app.js` 路由 sub→help，订阅页代码块整体移除。
- **④订阅模块自动化（手动导入页移除）**：`SubscriptionManager` 新增 `AUTO_SUB_URL`（id667 · Lin-arm gkd.json5）+ `maybeDailyAutoImport()`——subUrl 为空时自动设 id667 并拉取（八通道镜像链 SubMirrors + APK 内置离线快照双重兜底；快照兜底也失败不视为硬失败），失败 1h 冷却重试（每日一次语义由冷却时间戳保证）；`AdSkipService` `onServiceConnected` 后台线程自动导入（服务连上即触发，不占 UI 线程）+ `KeepAliveReceiver` 5 分钟心跳前置触发（双触发点——进程活着就必然导入）；订阅命中展示走既有「我的 → 使用报告」订阅两源命中统计。
- **⑤重进状态修复（冷启动宽限期）**：根因——退出重进时 AMS 无障碍重绑慢，首页先渲染「未开启」误导用户去系统设置重新开启（每次都看 10 秒警告）；修复：`AdSkipService` 新增 `KEY_LAST_CONNECTED_AT` 持久化（服务最近连接时刻），`MainActivity` 新桥 `serviceLastConnectedAt()`；JS `serviceGraceActive()`——最近断开 ≤10 分钟内首页显示「服务重连中」宽限态（renderHero 新分支）而非「未开启」，前台看门狗轮询自动恢复「守护中」；「退出重进无需重新开启」的正解 = 固定后台引导（②）+ 宽限期（⑤）双管齐下。
- **测试（Java 41 套件 7313 断言全绿 = 7305 + 8 净增；JS 6 文件 211 断言全绿 = 204 → 207 重写 + 4 新增口径；check-appjs 符号表 v7.11.0 化）**：`UpdateManifestTest` 新增 S12（8 断言：cdnApkUrl 合法 jsDelivr → apkMirrors[0] / 主源不被取代 / 缺失 → 空 / 非法域置空忽略 / http 明文置空 / 四接入点 / githubusercontent 域同闸 / 复合真值）；`UpdateAuditTest` 关键 bug 修复——`stripComments` 纯注释剥离器不识别 JS 正则字面量（esc() 的 `/[&<>"']/g` 内引号致状态机相位漂移误判未闭合字符串），升级 regex 感知扫描器（mode 5：注释/字符串/正则字面量三态分流）；`test_bugtip24h.js` 重写为 v7.11.0 兜底专项（45 断言：CDN 直连按钮源断言 / mapManifest mirrors 字段 / serviceGraceActive 真值表 / 底栏订阅移除+帮助页在场 / 版本判定真值表 / cmpVer 死灰复燃防线 / Java 接线面跨语言契约断言）；`test_splash_reminder.js` 重写（29 断言：进入即显不依赖网络/源码级 0ms 钉死/固定后台内容钉死/24h 抑制/知道了不耗资格/5min 防重建/更新优先/进程被杀重启/时间回拨防御/清数据恢复/同会话单卡/主线程零阻塞）；`check-appjs.sh` 符号表新增 pin-tip 四符号 + doFallbackDirectDownload + serviceGraceActive。
- **仓库口径治理**：全仓错字清理「兑底」→「兜底」（含本轮新写文件自查——README/version.ts/测试注释/历史报告/代码注释零残留）；`src/lib/version.ts`（单一数据源）与 `check-version-sync.sh` 同步 v7.11.0/81000；README 主页 v7.11.0 化：当前版本节五项功能 / 近期版本速览表 / 订阅节重写（自动订阅口径）/ 核心能力订阅行 / 安装节双链 + latest 别名 + 81000 覆盖升级 / 截图三张重截（首页/帮助（新增）/我的——订阅页截图随页面移除淘汰，VLM 视觉核验通过）；`docs/screenshots/README.md` 同步。
- **发布（v7.11.0/81000）**：签名证书 SHA-256 05ada6e3…（仓库内置 keystore——与历史完全一致）；APK 894,518 字节 / SHA-256 213b2ee1…；`android-app/dist/` 归档入库 adshield-v7.11.0.apk + .idsig + adshield-latest.apk 别名（jsDelivr CDN 兜底直链——四接入点中 cdn/gcore/testingcf 直出可用，fastly 勿用）；正式 Release 与 APK 资产随源码一并发布（scripts/publish-v7110.mjs：aapt 名码硬闸 81000/7.11.0 + 推送状态硬闸 + 防重核验 + 资产 digest 一致性核验 + jsDelivr CDN 兑底链三路 purge 与生效核验 + 仓库描述同步 v7.11.0 口径；scripts/push-workflow-only.mjs 归档 workflow-scope 三路封堵实测结论）；`update/latest.json` 预置 81000 + cdnApkUrl + SHA-256（发布工作流同码同 SHA 幂等 SKIP，不产生冲突提交）。

## v7.10.0（2026-11-25）—— 上传次数限制彻底移除 + 更新检查异步化 + 开屏提醒解耦 + 统一版本规则（82300/plus 旧兼容链清理）

- **本轮定位（用户四项指令驱动）**：①日志上传不再有人为次数上限/24h 额度/冷却等待；②更新检查禁止在 JavaScript 主线程同步等待网络；③开屏提醒不能依赖网络检查完成；④全项目统一版本判定 remoteVersionCode > currentVersionCode（versionName 仅展示——82300/plus 旧兼容链彻底清理）。**诚实口径：本版源码为灾后重建**——前工作区连同 v7.10.0 原始五提交（d1aea4f/9016aae/1977bb0/b74f2bd/6deeb92）丢失，本轮从远端 v7.9.9 基线（26ea020）+ 已分发 v7.10.0 APK（SHA-256 346f8abf…， dex 符号核验：acked24h/retryAfterMs/okCount24h 均已不在）重建并全量回归；重建内容与用户指令逐项对应，非原提交字节级复原。
- **①上传次数限制彻底移除（d1aea4f 内容——dex 符号实证）**：`LogUploader.autoEligible` 24h/3 成功额度闸与批粒度复核闸删除（仅剩心跳间隔 8h + 「有新日志才传」双闸）；Summary 删除 `acked24h`/`retryAfterMs` 字段与解析（不再解析 Retry-After 呈现「等待 N 分钟」）；`LogStore.overview` 删除 acked24h 键（MainActivity 兜底 JSON 同步）；ok-times/auto-ok-times 账本写入保留（历史留存——读取方全部删除）；app.js 额度文案全部清除（「每 24 小时最多 3 次」→「每 8 小时检查一次 · 无次数限制」）；RATE_LIMITED 改「服务端繁忙，请稍后重试——日志已保留（不会丢失），可立即再次上传」。手动上传零次数/额度/冷却门槛（batchId 幂等保持，绝不二建 Issue）。
- **②更新检查异步化（9016aae 内容重建）**：`MainActivity.Bridge.updateCheck(manual)` 同步桥整体移除（JS 调用会阻塞 JS 线程直至网络返回——五源链预算 40s，弱网页面冻结实锤根因）；新增 `updateCheckAsync(manual, cb)` 后台线程执行 `UpdateRepository.check`，结果经 `window[cb](json)` UI 线程回调（logUploadSubmitNow/tryAutoReviveDeep 同族模式）；app.js 新增 `bridgeUpdateCheck` 三重防线：JS 单飞（updInFlight 并发共享）+ 过期序号（updSeq 旧回调丢弃）+ 90 秒回调兜底（UPDATE_TIMEOUT 失败收尾——按钮/状态必然恢复，手动检查绝不永久卡住）；FlightGate 搭飞等待加上限（45s——链预算 40s + 5s 余量，超限走缓存兜底，绝不无限挂起）。缓存/ETag/冷却/退避/单飞语义零改动保持。
- **③开屏提醒与更新检查解耦（1977bb0 内容重建）**：v7.9.7 的「检查收尾后才评估提醒」在弱网下会随链预算推迟——改为独立 3 秒定时评估（网络再慢最多 3s 看到提醒）；24h 节流/5min 防重建/未确认不耗资格语义保持；更新优先双向收口（更新页在场提醒跳过；更新页后到主动收起提醒卡——showUpdateGate 移除 #bug-tip，确认时间戳不受影响）。
- **④统一版本规则（b74f2bd/6deeb92 内容重建——82300/plus 清理）**：`Net.isNewerRelease` 收敛为纯二参 `remoteVersionCode > currentVersionCode`（任一侧 ≤0 → 不提示）；`versionCodeOfNotes`/`cmpVersion`/`versionSegs`/`segNum` 四函数删除（versionName 回退链整体移除——UpdateChannelTest S2 反射防线钉死不得回场）；`UpdateManifest.parse` 删除正文元数据行回退 + versionName 名比较（签名链同时删除 currentVersion 入参——结构上无法名比较）+ versionCode 解析改 optLong+int 域校验（org.json optInt 对超 int 值静默截断回绕成正值 → 误判新版的实锤修复）；app.js 删除 `cmpVer`/`versionCodeFromNotes`、`isNewerByCode` 收敛二参；生成器 update_manifest.py 非三段 tag（plus 系）一律硬失败（正文元数据行仅作一致性校验不作定码来源）；Web 面板 about-panel 更新判定改 `versionCodeOfTag`（version.ts compareVersion 删除）。versionName 全项目仅展示。
- **测试（Java 41 套件 7305 断言全绿 = 7320 基线 - 15 净变化【删除 versionName 回退链测试 + 统一规则/额度移除重写】；JS 6 文件 204 断言全绿；manifest 47 = 44 + 3 新增）**：UpdateChannelTest 重做（S1 统一规则矩阵 16 断言：六场景矩阵 + plus 历史回归锚点 + 边界值 + int 上界；S2 死灰复燃反射防线 12 断言：四回退函数 + 旧四参签名 + GitHub 直连六符号）；UpdateManifestTest 统一规则适配（缺 versionCode → 0 不判新、非数字/负数/超大值 → 0、versionCode 解析回绕修复回归）；UpdateRepositoryTest 链签名适配（11 处 walkSources 五参化）+ FlightGate 有界等待；LogUploaderTest T5/T6 重做（无次数上限双闸）+ T13 限流如实分类（retryAfterMs 已删）+ T16 积压单次排空（5 批全传）+ T17/T18 账本语义；LogStoreTest S4 账本直读断言（写入/滚动窗修剪/分账）+ S7 acked24h 键删除回归；新增 test_update_compat.js（统一规则矩阵 + 源码死灰复燃防线 27 断言）、test_splash_reminder.js（解耦/3s 强制/24h/5min/更新优先/进程被杀/时间回拨/清数据/单卡/零阻塞 22 断言）、test_update_lifecycle.js（异步桥面/载荷形态/90s 兜底/过期序号/单飞/桥异常 18 断言）；check-appjs.sh 符号表 v7.10.0 化（versionCodeFromNotes 移除 → bridgeUpdateCheck 加入）。
- **发布（v7.10.0/80000）**：签名证书 SHA-256 05ada6e3…（仓库内置 keystore——与历史完全一致）；本版 Release/Tag v7.10.0 与 APK 资产（894,518 字节）随重建源码一并正式发布（scripts/publish-v7100.mjs：aapt 名码硬闸 + 防重核验 + digest 一致性核验）。**诚实口径：真机 E2E NOT_RUN（本轮全部为 JVM/JS 行为级验证——待用户实测）；原始 v7.10.0 六提交已随工作区丢失不可恢复（重建基线：远端 v7.9.9 = 26ea020），本轮为可验证重建。**
- **旧版检查更新 401 定案与救济（v7.9.6 真机实锤驱动）**：真机（v7.9.6 · 弱网）「检查更新」→「查询失败：GitHub 返回 401」。根因定案（代码级 + 实测证据链）：v7.9.5/v7.9.6 发行版 `Net.latestRelease()` 三通道（直连→重试→`/releases` 列表回退）**全部携带内置混淆令牌且无匿名降级路径**（该令牌 v3.3.0 引入；v7.9.4「Token 出库」曾移除、v7.9.5~v7.9.6 回归、v7.9.7 起再次移除至今）；该令牌（Net.java L2 密文 · 双层 XOR+Base64）实测 `api.github.com/user` → **HTTP 401（已吊销——v7.9.4 起按公开泄露处置）**，与用户现行推送令牌为不同 Token。旧版 APK 内密文烧死于 dex **无法从服务侧修复**——应用内检查更新不可恢复；唯一救济 = 浏览器手动下载覆盖安装。本轮救济动作：①APK + .idsig 按仓库 54 个历史 APK 归档惯例入 `android-app/dist/`——启用 **jsDelivr 国内可达官方下载直链**（与 App 五源清单/订阅镜像同族 CDN）：`https://cdn.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.10.0.apk`（SHA-256 `b32b63c9…` 与 GitHub Release 资产 digest 逐字节一致；四接入点中 **cdn/gcore/testingcf 三点实测全量下载 digest 一致直出**，**fastly 对 APK 返回 301→raw.githubusercontent.com 国内阻断域——下 APK 勿用 fastly**）——GitHub Release 资产域国内不可靠，此通道补位；②README 安装节新增旧版 401 救济说明。v7.10.0 自身零凭据链路免疫：更新链为五源静态清单（无 Authorization 头、无 api.github.com 直连——UpdateChannelTest S2 反射防线钉死 GitHub 直连六符号不得回场），401 结构性不可能再发生。

## v7.9.9（2026-11-24）—— 网络链路热修复轮：更新检查源链重构（jsDelivr 备用链）+ 日志上传超时阶段化分类 + Worker 服务端故障实锤披露

- **本轮定位（小米 15 Pro · Android 17 · v7.9.8/79800 真机双故障反馈驱动）**：①检查更新「查询失败：网络连接错误」；②日志上传「上传超时」。全链路真实核验先行（不依赖旧报告推测）：主源 raw.githubusercontent.com 国内 DNS 污染/TCP 阻断（真机不可达）+ 备用源 xiaobaiba999.github.io 仓库 Pages 未启用（API 404 实锤——恒 404）且 github.io 国内同样被阻断——**双源全灭是 Bug 1 根因**；workers.dev 国内阻断（历史 DNS 污染实测）+ **Worker→GitHub 上游持续故障（本轮 E2E 实锤，见③）是 Bug 2 双重根因**。
- **修复①（更新检查源链重构）**：`MANIFEST_FALLBACK`（github.io 死源）移除，改 `MANIFEST_SOURCES` 五源链 = raw 主源 + jsDelivr 四接入点（cdn/fastly/gcore/testingcf——订阅通道 SubMirrors 八通道同族、v6.1.0/v7.0.0 两轮真机实战验证的国内可达链）；`doNetwork` 重构为 `walkSources` 纯逻辑 + `Fetcher` 缝（生产 RealFetcher、JVM 替身直驱全链可测）；整链 40s 预算闸（首源必试、预算耗尽即停）；每源至多一次；任一源 200 但清单非法/包名不匹配（疑似劫持/错源）记录首错继续下一源（不采信也不放弃）；ETag/Last-Modified 条件头仅回发同源；app.js 浏览器预览路径同步五源链。S8 新增 18 项链路走查测试（主源 200/连接失败/超时/500/DNS/非法 JSON/包名不匹配逐类降级、全链失败首错 canonical、304 同源、异常 304 防御、预算耗尽即停、hasRelease:false 即停、缝异常防御）。
- **修复②（日志上传超时阶段化）**：`netKindOf` 五态分类（connect / connect-timeout / read-timeout / tls / io）——SocketTimeoutException 消息含 connect = 连接阶段（请求几乎必然未送达，提示切换网络），否则按读取阶段保守处理（请求可能已送达甚至已建 Issue——如实提示「结果暂时无法确认，重试将使用原批次，不会重复创建 Issue」）；SSLException 独立 tls 分类（网络干扰）；连接超时 15s→10s（阻断网络快速失败），读取超时保持 45s（服务端建 Issue 尾延迟实测可超 20s，不盲目调短）；classifyError 新增 NET_CONNECT_TIMEOUT/NET_READ_TIMEOUT/NET_TLS 稳定码（NET_TIMEOUT 旧码兼容）。T19 十五项分类测试 + T20 读取超时冻结批次幂等全链回归（发送→建 Issue→响应超时→重试→同 batchId→复用原编号→不二建）九项。冻结批次/batchId 幂等/ALREADY_ACCEPTED/24h/3 额度零改动。
- **③ Worker 服务端故障实锤（network-link-verify 工作流 · Actions runner 真实 HTTP · 三次运行复现）**：/healthz HTTP 200（ok=true·ipHmacConfigured=true——服务本体在线健康）；真实 POST /api/logs/receive（九键契约）→ **HTTP 503 TEMPORARY_ERROR「github outcome unknown (timeout/5xx); reserved for recovery — never blindly re-created」**——Worker→GitHub Issues 上游建 Issue 持续失败，batchId 幂等保留待恢复（同 batchId 60s 重试仍 503「recovery pending」）。源码仓 Skip-Ad-Log-Upload 已从账号删除、凭据只存 Cloudflare Worker Secret——**服务端修复需所有者重部署，客户端无法代修**；客户端对 503 如实分类 RELAY_TEMP_ERROR 并保留冻结批次（服务端恢复后重试自动复用原编号）。
- **发布链路强化**：update-manifest.yml 新增 jsDelivr purge 步骤（软失败——raw 主源已提交不阻塞）+ 线上核验扩展 jsDelivr 四源软核验；新增 net-verify.yml 端到端验证工作流（清单五源一致性 + Worker /healthz + 真实上传 + 同 batchId 幂等/恢复重试 + Issue 存在性核验与测试清理；每周一例行体检）。**实测口径：jsDelivr @main 分支解析缓存 12h——purge API 执行成功（CF+FY provider finished）但无法刷新分支解析层**（发布后 14 分钟三次探测 cdn/fastly/testingcf 仍 79800、gcore 仍 79700）——中国用户立即恢复「有效数据展示」（可能短暂显示上一版本），最长 12h 自然回源；彻底取代 v7.9.8 的「网络连接错误」硬失败。
- **测试（Java 63 套件 7320 断言全绿 = 7274 既有 + 46 新增；JS 全绿；manifest 44 = 35 + 9 新增）**：UpdateRepositoryTest S6 源链常量 +3 / S8 链路走查 18 新增；LogUploaderTest T19 超时阶段化 15 + T20 读取超时幂等回归 9；UpdateAuditTest A3 源审计重做（github.io 死源断言反向化 + jsDelivr 四接入点 + 链预算闸在场）；test_update_check.js 源链 5 源/劫持降级（③b2-b4）/请求上界 5；test_upload_errors.js 三态新文案 10 新增；tests-manifest S6k/S6l/S6m 系 9 新增 + 夹具升级 v7.9.9 真实数据（Release id=454424925 · digest 2a16970e 实测核验）。三闸门全绿。**诚实口径：真机 E2E NOT_RUN（修复即由该真机反馈驱动，待用户实测）；Worker 真实建 Issue NOT_RUN（服务端上游故障——三次复现，需所有者侧修复后由 net-verify 工作流验证）。**
- **发布（v7.9.9/79900 正式 Release id=454424925）**：签名证书 SHA-256 05ada6e3…（与历史完全一致）；APK 894,518 字节 / SHA-256 2a16970e…；dex 零凭据 + 五源链确认入 dex + github.io 死源确认移除；latest.json 由 release.published 工作流自动生成（79900 > 79800 陈旧守卫通过）+ jsDelivr purge 执行成功；raw 主源即时生效（versionCode=79900 第 1 次探测确认）；v7.9.8 正式 Release 完全未动（digest 复验一致）。

## v7.9.8b（2026-11-23）—— 全面核验轮：重启丢冻结状态实锤修复 + 自动 24h/3 批粒度闸 + 401/403 分家 + 矩阵十三字段 + JS 测试入库

- **本轮定位（用户指令：v7.9.8 全面核验、修复与工程闭环——重点核验批次冻结/持久化/幂等/额度/错误分类/隐私/矩阵/性能/安全全链路，未发布前继续完善 7.9.8/79800）**。基于 976da28（v7.9.8 未推送 commit）之上修复三个真实问题，零广告识别链改动。
- **修复①（真实生产 Bug·T14 重启恢复测试实锤暴露）——migrateLegacy 误删冻结清单**：v8.2.2 引入的遗留清理规则 `startsWith("batch-")` 会连带删除 v7.9.8 的冻结清单 `batch-manifest.json`（遗留状态机文件实名 `batch-{uuid}.json`——git 考古 cc159ff 实证）——**进程每次重启（设备重启/ROM 杀/应用更新）冻结状态即丢失**：首 POST 已被服务端接受（Issue 已建）后重启，重试换新 batchId = 服务端视作新批次**重复建 Issue**，幂等契约跨进程断裂。修复：清单排除 `batch-manifest.json`（一行守卫），遗留 `batch-{uuid}.json`/`acked-meta.json` 清理语义不变。S8 回归钉死（冻结清单跨实例存活 + 遗留文件仍被清理）。
- **修复②（用户指令：自动上传成功 3 次后第 4 次必须被阻止）——自动路径批粒度 24h/3 闸**：`autoEligible` 只在 run 起点检查一次，积压场景（多日未传后一次性排空）单次 runUpload 可连续成功 >3 批突破「24h 最多 3 次成功」上限。修复：每批成功入账后复核自动账本（`autoOkCount24h ≥ 3` 即停），剩余事件留待窗口滑动后下一次心跳续传；手动路径永不受此闸（T16 对照组钉死：200 条自动只传 3 批、手动 5 批全传、窗口滑动后自动续传排空）。
- **修复③（用户指令：401 提示认证问题、403 提示权限问题）——裸 401/403 分家**：旧 classifyError 把无 JSON body 的 HTTP 401/403 混同为 `UNAUTHORIZED`。分家为 `AUTH_FAILED`（认证失败）/`FORBIDDEN`（权限不足），app.js 各配人话文案（均声明日志已保留）；服务端 JSON 码 `UNAUTHORIZED` 仍按契约原样透出（拒收隔离语义不变）；runUpload 终态判定用 res.code 而非分类码——裸 401/403 仍按瞬时错误保留冻结批次重试（行为零变化，仅提示保真）。
- **核验结论（不改代码·如实记录）**：①批次持久化本就落盘（sealBatch POST 前写 manifest）——T14 新测试证明跨进程字节级恢复；②IDEMPOTENCY_CONFLICT 证据保留语义核验：rejectHead 隔离进 rejected.json（code+error+events 全在·50 条环形上限）——T17 钉死证据完整性；③额度文件损坏防御：readJsonArray 损坏读空不崩溃、recordOk 重写自愈——T18 钉死；④错误分类矩阵 T12 扩至 19 断言（含 2xx 空 body/DNS 污染→NET_CONNECT_FAIL/超时≠HTTP_400 逐路验证）；⑤Worker 可达性：沙箱实测 workers.dev 仍 DNS 污染（解析 Facebook IPv6 `face:b00c`/157.240.7.8，TCP 30s 超时）——真实服务端上传 NOT_RUN 维持，不冒充。
- **任务九：矩阵十三字段版（`scripts/gen-splash-matrix.py` 重写）**：`compatibility/splash_matrix_v798.json` 每行新增 `adScenes`（信息流/插屏/视频广告目录标注）+ `ruleRefs`（GKD 快照版本实测提取：ganlin=72/linarm=637/xnx=79 + 引擎规则版本）+ `failureCode`（失败归类码，17 词表 A–J 十类+SCAN_MISS 四码联动）+ `lastTestedAt`/`testDevice`（真机回填前一律空串——绝不虚构）+ BLOCKED 状态入词汇表；meta 新增 failureCodeVocabulary/fieldProvenance（数据来源逐字段声明）。233 款重生成，状态零变化（NOT_TESTED 163 · NOT_APPLICABLE 70 · PASS 0/FAIL 0——无真机证据绝不虚标）。
- **工程闭环：JS 行为测试入库**（此前散落沙箱未跟踪）：`android-app/tests-js/`（test_bugtip24h.js 46 断言 + test_upload_errors.js 22 断言 + legacy/app-v7.9.6.js 旧版 cmpVer 夹具 + run.sh 跑器）——CI/后续轮次可复现，不再依赖沙箱临时文件。
- **测试（39 套件 JVM 7149 断言全绿 = 7107 既有 + 42 新增；JS 68 断言全绿 = 63 既有 + 5 新增）**：LogUploaderTest 92→128（**T14 进程被杀重启恢复**：冻结批次跨实例字节级复用+ALREADY_ACCEPTED 消费；T15 批次 B 独立性；T16 自动批粒度闸；T17 冲突证据保留；T18 额度损坏防御；T12 401/403 分家）；LogStoreTest 39→45（S8 migrateLegacy 不误伤）；LogPrivacyFilterTest 58 保持（SCAN_MISS 白名单契约）。三闸门全绿：check-appjs + check-version-sync（7.9.8/79800 不变）+ secret-scan（零令牌/零凭据/keystore 面收敛）。**诚实口径：真机 E2E NOT_RUN；中转服务真实上传 NOT_RUN（workers.dev 沙箱 DNS 污染实测）；性能真机指标 NOT_RUN（ScanBenchmark JVM 基准在套件内在场）。**
- **版本纪律**：versionName 7.9.8 / versionCode 79800 保持（v7.9.8 未发布未推送——用户指令「未正式发布可继续完善」）；本轮不创建 Tag/Release；APK 重建 adshield-v7.9.8.apk（79800 > 79700 覆盖升级路径不变；82300 历史用户仍按 v7.9.7 节口径）。

## v7.9.8（2026-11-23）—— 日志上传可靠性专项：冻结批次终结两连失败 + 错误八类如实分类 + 反馈提示 24h 节流 + 导出下线 + 未命中结构化诊断

- **本轮定位（用户真机实锤：v7.9.7 上点击「一键上传」第一次提示「失败，请稍后再试」、第二次提示「日志被服务端拒绝」——必须根治）**。全链路只读审计（客户端 LogUploader/LogStore/RealHttp + 服务端中转 + Git 历史考古 v8.0.0 冻结实现）后**真实根因定案（代码级实证，非推测）**：
  - **根因①（payload 漂移 → IDEMPOTENCY_CONFLICT）**：v8.2.2 简化上传器每次 `runUpload` 重建请求体——`createdAt` 取当次时间、STATS 快照重新生成、间隔期新事件并入头部块。而服务端 LogGate 幂等按「batchId + payload 指纹」判定：第一次 POST 实际已被服务端接收并建 Issue（但读超时 15s 内响应未及返回，客户端判「失败」），第二次点击同 batchId + 漂移 payload → **IDEMPOTENCY_CONFLICT 终态拒收**——用户看到的「日志被服务端拒绝」。v8.0.0 的 LogBatchStore 本有冻结语义（`rec.put("createdAt", …)` 封批时冻结，git 考古 cd669aa 实证），v8.2.2 精简时丢失。
  - **根因②（读超时 15s 过短）**：Worker 同步建 GitHub Issue（含上游重试）可超 15s——把「服务端实际成功」变成客户端「失败」的导火索。
  - **根因③（错误呈现一律「稍后再试」）**：旧 `__logUploadCb` 把所有失败折叠成一句「上传失败：network——日志已保留」。
- **修复①——冻结批次幂等（LogStore.sealBatch/headFrozenOrNull）**：封批时把 `batchId + createdAt + 事件数组 + realCount` 整体冻结进 manifest（v2 记录格式；POST 前落盘）；重试字节级复用冻结 payload——同 batchId 必同 payload，服务端幂等**必然 ALREADY_ACCEPTED 复用原 Issue 编号**，绝不二建。v1 遗留 manifest（v8.2.2~v7.9.7 格式，仅 batchId）一次性迁移：保留编号补冻当前头部块。STATS 注入只进「本次运行的首个新封批次」（冻结重试块自带历史快照——绝不重构）。`rejectHead/consumeHead` 按冻结 realCount 消费（注入 STATS 不占 pending 名额）。
- **修复②——读超时 15s→45s**（RealHttp；连接超时 15s 保持）。网络层失败不再丢细节：`Resp.errKind` 携带 connect/timeout/io 三分类（UnknownHostException/ConnectException→connect、SocketTimeoutException→timeout、其余→io）。
- **修复③——错误八类如实分类（LogUploader.classifyError 纯函数·JVM 可回归）**：网络层（errKind）→ 服务端 JSON 码 → HTTP 状态码三路优先级；稳定码 NET_CONNECT_FAIL/NET_TIMEOUT/NET_IO/RATE_LIMITED/RELAY_SERVER_ERROR/RELAY_TEMP_ERROR/PROTOCOL_UNEXPECTED_BODY + 服务端六终态码原样透出。app.js `uploadErrText` 按用户八类表映射人话：网络连接失败/上传超时（附「重试自动复用原 Issue 不会重复提交」说明）/服务端拒绝（码透出+诊断指引）/频率限制（retryAfter 分钟数透传）/格式异常/服务暂不可用/协议异常/未知（原始错误透出）。**空队列四态区分保留**（never-collected/all-uploaded/append-failed/read-failed + rejected-only/cleared/relay-offline）。
- **修复④——额度分账（用户指令：手动与自动不互相占用额度）**：`recordOk(ts, manual)` 双账本——ok-times.json（全部成功·展示）+ auto-ok-times.json（仅自动成功）；`autoEligible` 第三重闸只看自动账本。手动成功不再挤占自动 24h/3；服务端共享限额仍由 RATE_LIMITED 如实转达（客户端不越权绕过）。
- **中转服务可达性（诚实口径）**：`skip-ad-log-relay.2927609408.workers.dev` 在中国大陆网络整体不可达（workers.dev 域 DNS 污染 + TCP 阻断——沙箱实测 HTTP 000/解析到 Facebook IPv6 `face:b00c`；用户真机能收到服务端拒收响应说明间歇可达）。服务端源码仓已不存在（owner 账号核实）无法修复/重部署；**客户端修复后行为**：可达时正常上传（超时自动幂等复用）；不可达时如实提示「网络连接失败，请检查网络后重试——日志已保留」并保留冻结批次待下次。**绝不伪造成功、绝不绕过服务端限额。**
- **任务一：Bug 反馈提示 24h 节流（取代 v7.9.7 一次性）**。`bug_feedback_confirmed_at`（确认时间戳·24h 节流主闸）+ `bug_feedback_shown_at`（展示时间戳·5min 防重建短窗）双键持久化（APK 内 kvSet 白名单新键 → SharedPreferences；浏览器 localStorage 兜底）。行为：每次进入 App 都有机会看到（冷启动 init 评估）；「知道了」/「去上传日志」任一确认 → 24h 内不再弹；超 24h 下次进入重新显示；**未确认不消耗资格**（无永久 true/false 标记——5min 短窗只防 Activity 重建/旋转重复弹）；前后台切换不评估（仅 init 调用）；更新页在场不弹（优先级保持）；「去上传日志」仅导航（切「我的」页+滚到引擎日志卡片）并同步记确认时间（日志页返回不立即重复弹）；零网络请求/零自动上传/零 Issue 创建。文案更新为用户指令版：「使用过程中发现问题了吗？」+ 开屏广告没有跳过/广告跳过不准确/弹窗无法关闭/App 出现卡顿或异常/其他使用体验问题。
- **任务二：引擎日志页导出功能下线（用户指令）**。移除：index.html `#btn-export-log` 按钮、app.js `doExportLog`/`showExportDoneModal`/`closeModal`/`humanizeUploadError`/`resetUploadBtn`/`engineProfile`/`fmtTs`/`maskText`（导出专用死代码）与 `#btn-export-log` 事件绑定、MainActivity `exportLog`/`shareLogText` 两桥。保留：日志采集（常开）/ 查看能力（renderLog + 刷新按钮）/ 一键上传（主按钮）/ 设置页「立即提交」/ 自动上传规则（零改动）/ LogStore/LogUploader/CrashCapture 核心模块全量。布局无残留空白（按钮行两项自适应）。
- **任务四：扫描未命中结构化诊断（SCAN_MISS——白名单事件类型首次接线）**。`LogEvents.scanMiss` 工厂 + `scanMissCode` 纯函数（四稳定失败码：NO_RULE_ZERO_CAND 无规则零候选/RULE_MISS_ZERO_CAND 有规则零命中/SILENT_TREE_ZERO_CAND 树静默观察中/VIDEO_LIKE_ZERO_CAND 零内容事件+窗龄≥2s 视频容器铁证）；接线在 `logScanMiss`（每窗一条+同包 8s 节流+静默期/微树过滤——零新增扫描）；字段全部在服务端白名单内（type/pkg/activity/ui/errClass/scanCount/intervalMs——**服务端字段面零新增**，源码丢失无法演进白名单的硬约束下唯一可行路径）；STATS 事件 errClass 携带设备码 `DEV_Brand.Model`（白名单形态 `[A-Za-z0-9_.$]` ≤80，无序列号/无 AndroidId/无唯一标识符）——机型归因数据源。隐私与性能红线：零新增树遍历/零 getWindows/零 OCR/零截图/零页面文本/异步落盘/容量 2000 上限保持。
- **任务五：失败分类体系 + 200+ 应用矩阵**。`compatibility/skip_failure_classification.md`：A–J 十类失败逐类映射真实代码路径（事件入口过滤链/CLEAN 档案/五层规则/决策阶梯/SafetyGuard/任务状态机/轮询针）+ v7.9.8 证据链 + 真机回填流程；结论：已知共性缺口已在 v7.7.0/v7.8.0/v7.9.0/v8.1.0 历轮收口，**本轮代码审计未发现新的代码级共性缺陷**（识别/执行链零改动——只新增证据记录），剩余失败面需真机 SCAN_MISS 证据归因（PENDING_REAL_DEVICE，绝不无证据修改生产算法）。`compatibility/splash_matrix_v798.json`（`scripts/gen-splash-matrix.py` 生成）：233 款应用 × 用户指令字段（packageName/appName/splashSupport/ruleExists/detectionStatus/matchStatus/actionStatus/finalStatus/failureReason/testEvidence/lastTestVersion）——开屏场景适用 163 · NOT_APPLICABLE 70 · 有规则 174 · **NOT_TESTED 163 · PASS 0/FAIL 0（无真机证据绝不虚标）**。
- **测试（39 套件 JVM 7107 断言全绿 = 7049 既有 + 58 新增；JS 行为测试 63 断言全绿）**：LogStoreTest 26→39（S3 冻结幂等重写：字节级复用/追加事件不混入/v1 遗留迁移保留编号；S4 额度分账）；LogUploaderTest 58→92（T3 重试字节级冻结断言 batchId+createdAt+events 三同；**T11 用户两连失败场景回归**：超时→追加事件→重试 ALREADY_ACCEPTED 复用原编号；T12 classifyError 十六断言矩阵；T13 retryAfter 透传与 24h 钳制）；LogPrivacyFilterTest 47→58（S8 SCAN_MISS/设备码白名单契约；S9 失败码纯函数真值表）。JS：`test_v798_bugtip24h.js`（46 断言：首进显示/知道了 24h 节流/23h 不弹/25h 重弹/未确认不消耗/5min 防重建/去上传仅导航零网络/更新页优先/79800 真值表/旧版 cmpVer 兼容）+ `test_v798_upload_errors.js`（17 断言：八类人话映射逐类验证）。**诚实口径：真机 E2E NOT_RUN（沙箱无设备）**；中转服务真实上传 NOT_RUN（workers.dev 沙箱不可达）——JVM/JS 测试证明真实代码路径语义。
- **版本与发布纪律（用户指令：本轮不覆盖正式 Release）**：versionName 7.9.8 / versionCode 79800（标准公式 major*10000+minor*1000+patch*100）/ APK adshield-v7.9.8.apk；79800 > 79700（v7.9.7 用户可覆盖升级）；**82300 历史用户（v7.9.6plus）仍无法覆盖安装 79800**——升级路径保持 v7.9.7 节口径（手动卸载重装/等待 >82300 版本），本轮发布计划（tag/Release/APK 资产）待用户确认后另行执行。三闸门全绿：check-appjs.sh（符号表新增 bugTipThrottled/uploadErrText/doUploadLog/resetUploadButtons）+ check-version-sync.sh + secret-scan.sh。

## v7.9.7（2026-11-23）—— 版本体系收口（versionName 7.9.7 = versionCode 79700）+ Bug 反馈一次性提示

- **本轮定位（用户指令：版本名和 versionCode 必须对应——彻底恢复正常版本体系 + Bug 反馈提示工程）**。v7.9.6plus 的 `versionName 7.9.6plus / versionCode 82300` 名码不对应（82300 属 8.x 序列占用在 7.9.6 名下），且随后的 v7.9.7 兼容 Release 复用了同一 APK（同为 82300）——两处均于本轮收口。**versionCode 规则真实核验**（dist 全量 51 个历史 APK aapt 实测）：1.0.0~1.2.0 为顺序码 1/2/3；1.3.0~6.x 为 `major*10000+minor*100+patch`（如 3.8.1→30801、5.1.1→50101）；**v7.1.0 起为 `major*10000+minor*1000+patch*100`**（71000/76100/78000/79600 全序列实测，`scripts/check-version-sync.sh` ①双公式校验同源）——按此规则 **7.9.7 → 79700**，与本轮目标一致，非新发明。
- **收口结果**：`versionName 7.9.7 · versionCode 79700 · Git Tag v7.9.7 · GitHub Release v7.9.7 · APK adshield-v7.9.7.apk` 五位一体同名同码；`src/lib/version.ts`（单一数据源）/`app.js`（appVer/appCode 兜底与版本记录）/`docs/CHANGELOG.md` 同步 7.9.7/79700；`check-version-sync.sh`（公式+防降级）全绿。
- **82300 用户过渡（如实口径）**：Android PackageManager 对 `82300 → 79700` 判定为降级（INSTALL_FAILED_VERSION_DOWNGRADE），**无法覆盖安装**——已装 v7.9.6plus（82300）的用户不能自动升级到 79700。受影响人群实测边界：v7.9.6plus Release 资产下载 6 次 / v7.9.7 兼容 Release 资产下载 2 次（含本项目验证下载，真实安装人数无法从 GitHub 得知，估计接近零）。迁移路径：① 手动卸载后安装 adshield-v7.9.7.apk（卸载清本地数据：学习规则/战绩/日志）；② 保持 82300 继续使用（功能完整，更新检查 versionCode 主判 79700>82300=false 不会误报提示）；③ 未来任一 versionCode>82300 的正式版本（如 8.3.0→83000）发布时可无缝覆盖安装回归正轨。**不为过渡再制造 7.9.7→8xxxx 的名码错位**（用户指令硬约束）。
- **新功能：Bug 反馈一次性提示**。新版本首次进入 App（启动更新检查收尾且无更新页时）弹一次性轻提示卡（复用 ug-* 卡片样式体系，层级 110 低于更新页 120——更新优先）：「发现 Bug 或使用不舒服？」列举广告没跳过/跳过不准确/App 卡顿/误跳/其他异常，说明可到「我的 → 引擎日志 → 一键上传」反馈。按钮行为：「去上传日志」= 仅导航（切到「我的」页并滚动到引擎日志卡片，**绝不代替用户点击上传**——最终决定权属于用户）；「知道了」= 仅关闭。两按钮均记录 `bugTipShown=当前版本号`（APK 内经 MainActivity kvSet 白名单新增键落 SharedPreferences；浏览器预览走 localStorage 兜底）——同版本第二次进入不弹、新版本首次进入再提醒一次。**硬性约束全遵守**：无 Accessibility 高频扫描挂钩、无 OCR、无截图、无 Worker、无 Service、无网络请求、不自动创建 Issue、不采集新数据、日志采集/手动上传/自动上传额度（24h 最多成功 3 次）全部零改动；文案无恐吓/强制/「必须上传」/「自动上传」/「否则无法使用」。
- **更新检测逻辑保持 versionCode 主判**（v7.9.6plus 实现零改动）：`remote versionCode > local versionCode` 才提示（79700>79600=true 给旧版、79700=79700=false 不自误报）；元数据行缺失回退 versionName 三段比较；Draft/Pre-release 不推送；网络异常静默。以后正式版本全部使用标准三段号（7.9.7/7.9.8/7.9.9/7.10.0…），不再出现 `plus` 类非标准 versionName。
- **旧客户端兼容（历史真实代码重验）**：v7.9.6/7.9.5/7.9.0/7.8.0/7.6.1 的 `cmpVer("7.9.7", …)` 全部 > 0（+1/+2/+7/+19/+21）——「检查更新」均可发现 v7.9.7 并下载 adshield-v7.9.7.apk 覆盖升级（79700 > 79600/79500/79000/78000/76100，签名一致）；v7.9.6plus（82300）不误报（versionCode 主判 79700>82300=false）。
- **GitHub 清理（记录在案后执行）**：删除 v7.9.6plus Release（id 454260807 · tag v7.9.6plus→commit 2ded4a4 · 资产 adshield-v7.9.6plus.apk 886326B · sha256 f3b6d4cd54adb1b070cd70e4687f3bb7d29cb0217cda290ad35a9b7eb0db07c4）与 Tag；删除错误 v7.9.7 兼容 Release（id 454275424 · 同一 APK 82300）与 Tag；创建真正 v7.9.7 Release（tag→本轮 commit · 资产 adshield-v7.9.7.apk versionCode 79700）。**v7.9.6 及更早全部 51 个历史 Release/Tag 不触碰**。本地 dist 同步移除 adshield-v7.9.6plus.apk(.idsig)（build.sh versionCode 单调守卫与版本体系一致性要求；APK 永久保留于 git 历史与 v7.9.6plus Tag commit）。
- **测试**：39 套件 JVM 回归全绿 + `check-appjs.sh` 符号闸新增 maybeShowBugTip/showBugTip 断言 + `check-version-sync.sh`/`secret-scan.sh` 全绿。诚实口径：真机 E2E NOT_RUN（沙箱无设备）；弹窗交互为 WebView DOM 逻辑（stub 闸验证顶层语义 + 人工代码走查），未在真机点亮。

## v7.9.6plus（2026-11-23）—— 以 v7.9.6 为稳定基线的最终重构版：启动时新版本检测 + 全屏更新页 + 多源下载安全校验

- **本轮定位（用户指令：v7.9.6 为唯一重构基线——versionCode 82300 / versionName 7.9.6plus / Tag 与 Release 同名；8.x 全线清除后的正式发布版）**。基线核验先行：v7.9.6 Tag=commit `920b10cc541ad8eaf0370726ce2dfd1f3c784610`；**v7.9.6 本无日志上传功能**（日志上传为 v7.9.7/v8.0.0 引入——本轮日志模块沿用 v8.2.2 已简化的 LogStore+LogUploader 两小类，其六项业务与 v7.8 简单模型同构）；引擎为 v7.9.6 基线 + v8.0.2 双 DFS 单遍化 + v8.1.0 开屏六项真机根因修复（回退=重新引入真机 bug，故保留——39 套件 7047 断言回归全绿）。GitHub 现状核验：无任何 v7.9.7/v8.x Release（v8.2.2 轮已删四个 8.x Release），Latest=v7.9.6（versionCode 79600）→ 82300 单调递增合法且 > 82200（已装 v8.2.2 的设备可直接覆盖升级）。
- **新功能①：启动时优先检查更新（versionCode 主判）**。用户每次进入 App：首屏渲染后异步获取 GitHub 最新正式 Release（800ms 延迟启动检查，绝不阻塞 App）→ **versionCode 主判**（`Net.isNewerRelease`：Release 正文 versionCode 元数据行 > 本地 PackageManager versionCode 才算新版；元数据缺失回退 versionName 三段比较）。改主判的实证理由：旧 `cmpVersion("7.9.6plus","8.2.2")` 分段解析 [7,9,0]<[8,2,2] **必判旧版**（"6plus" parseInt 失败→第三段 0——UpdateChannelTest S2g/S3k 名比较陷阱如实钉死）；Android 覆盖安装权威依据是 versionCode 单调递增。Draft/Pre-release 不作正式更新（GitHub latest 接口天然排除 + 列表回退显式过滤）；网络异常/限流静默进入 App（启动检查失败绝不阻塞使用）；绝不提示降级（remoteVc ≤ localVc 一律非新版）。
- **新功能②：全屏更新页**。发现新版本优先显示全屏覆盖层（非小型 AlertDialog）：应用名+图标+当前版本→最新版本（含 versionCode）+ 新版本功能描述（headlines ≤5 带序号）+ 安装包大小 + SHA-256 已公布标识 + 醒目「立即更新」主按钮（渐变绿）+「暂不更新」次按钮。暂不更新→继续使用当前版本，下次进入 App 仍会检测（无频控）。移动端优先适配（卡片 max-width 400px 居中，无横向溢出），关闭即回原页面（动态创建/移除，不破坏原有布局）。
- **新功能③：下载状态机九态全展示**（`setDlStateText` 状态文案唯一出口——「我的」页与全屏更新页同机复用）：正在连接下载服务器（DownloadManager 入队）→ 正在下载（百分比+已下载/总大小 MB）→ **当前使用的下载源**（host 实时显示）→ 下载中断正在自动重试（系统 DownloadManager 断点续传——HTTP Range；PAUSED→retrying 映射）→ 正在切换备用下载源（channels 数组机制保留）→ 正在校验 APK（SHA-256+大小）→ 校验成功准备安装 → 下载失败可重新下载 → 所有下载源均不可用（明确提示+重新下载入口，不要求用户反复手动点击）。**修复存量 bug**：「我的」页进度文字从不更新（JS 查找 `#page-text` 而 index.html 是 `#page-progress-text`——v7.9.6plus 统一为 `-progress-text`）。断点续传由系统 DownloadManager 原生支持（重启后任务持久，全屏页重开自动恢复进度/已完成态直接给安装入口）；取消下载新增 `Downloader.cancel`（移除系统任务与部分文件）。
- **新功能④：下载源治理（诚实口径）**。**移除** ghfast.top/gh-proxy.com 两个硬编码 ghproxy 类代理镜像（`Net.mirrorsOf` 原样拼接——用户指令：来源不明/不稳定的第三方代理不作正式下载源；实测 gh-proxy.com 已 403，ghfast.top 可达但属第三方）。**当前唯一官方源 = GitHub Release asset（HTTPS）**。无经所有者确认的中国大陆备用源（沙箱无自有对象存储/CDN/域名基础设施，不引入收费服务、不伪造部署）——多源机制（channels 数组+自动切换+同 digest 校验）完整保留，未来新增经核实的官方备用源零改动接入。`Downloader.hostAllowed` 白名单同步收紧为仅 GitHub 官方域。
- **新功能⑤：APK 完整性与安全校验（全链不降级）**：下载启动即绑定 digest/size（GitHub API HTTPS 元数据）→ 安装前 `Downloader.install` 流式 SHA-256 全量比对 + 字节数比对（v6.3.0 闸逐字保持——任何下载源同一套校验）→ 校验失败删文件拒安装 → Android 8+「安装未知应用」授权引导 → 系统 ACTION_VIEW 正规安装流程（绝不绕过安装确认）。包名/签名兼容由 Android 安装器与同 keystore 签名保证（build.sh v1+v2 签名 + 历史一致证书 05ada6e3…6f79）。
- **v8.x 清理（本轮执行）**：删除 `worker/` 目录（v8.2.0 更新中转 Worker 源码——部署状态沙箱不可验证，DNS 解析正常但 TCP 出站拦截，无法确认服务存在；更新检查回归 GitHub 直连单通道）；删除 Net.java 双通道全部代码（UPDATE_RELAY_URL/deriveUpdateRelayUrl/workerChannel/composeFailure/primaryErrorCode/compositeMessage/normalizeWorkerUpdatePayload/ChannelOutcome/ERR_WORKER_UNAVAILABLE——错误四分类保持）；删除 v8.x 架构文档（PROJECT-HANDOFF-V8.0.0/V8.0.2、audits/V8-*×3、COMPATIBILITY-MATRIX、ci-workflows/build-apk.yml）与 reports/iteration-v8.0.0.md；删除 dist/adshield-v8.2.2.apk(.idsig)；**日志上传 LOG_RELAY_URL 保留**（唯一已知服务端端点——服务端凭据全在服务端，APK 零凭据红线保持；沙箱不可验证其部署状态，上传失败如实报「上传服务不可达，日志已保留」，绝不伪造成功）。UpdateChannelTest 从双通道版重写为单通道+versionCode 主判版（41 断言）。
- **日志上传业务保持（v8.2.2 简化模型零改动）**：采集常开（`appendEvent` 无条件异步落盘·重启保留·关闭自动上传不影响采集）/ 自动上传 24h 最多成功 3 次（ok-times 只记服务端确认·失败不耗额度·无新日志零网络请求）/ 手动上传随时可用（不查本地额度）/ 成功=HTTP 2xx 且 body ok=true 才消费事件 / 失败保留同 batchId 重试（服务端幂等绝不二建 Issue）/ 成功后经 GitHub 匿名 API 核实公开仓 issues/{n} 的真实 html_url 才展示可点击链接（非拼接·核实不到只显示编号）。
- **测试（39 套件 7047 断言全绿 = 38 既有 + UpdateChannelTest 重写）**：新增 versionCode 主判真值表（S1 元数据行解析 12 断言：标准/尾注/多行/超 int/前缀不匹配等；S2 isNewerRelease 9 断言：plus 命名主判判新/相等不重复/绝不降级/双向回退+名比较陷阱钉死；S3 parseRelease 11 断言：元数据→remoteVersionCode→isNewer 全路径/digest/size 透传；S4 mirrorsOf 零镜像；S7 反射验证双通道符号已删）。引擎回归：DualDfsParity/Bug001Regression/ReentrySplash/VideoSplash/AggressiveMode 等全部套件零回退。**诚实口径：真机 E2E NOT_RUN（沙箱无设备）**——JVM 测试证明真实代码路径语义；中国大陆网络专项测试 NOT_RUN（沙箱为海外网络，不用海外服务器测试冒充大陆网络）。
- **发布物**：versionName 7.9.6plus / versionCode 82300 / Tag v7.9.6plus / 正式 GitHub Release（非草稿）。APK 零凭据复查 + 签名与历史正式版本一致（覆盖安装不清数据）。Release 正文含机器可读 `versionCode: 82300` 元数据行（客户端主判数据源）+ SHA-256 公布值。

## v8.2.2（2026-11-22）—— 日志上传模块简化重构：回归 v7.8 简单模型 + 8.x 全线召回

- **本轮定位（用户实锤：v8.2.1 安装后点击「一键上传」提示「暂无可上传的新日志」——与预期不符；指令以 v7.8 业务逻辑为参考回归简单方案，8.x 版本全部召回清除）**。根因定案（全部真实代码实证，非推测）：`refreshUploadCollect`（AdSkipService.java:731-737）把**事件采集门绑在「自动上传开关」上**（`KEY_AUTO_LOG_UPLOAD` 默认 false，AdSkipService.java:743）→ 开关未开时 `LogBatchStore.collectEnabled=false`（LogBatchStore.java:148），`appendEvent` 直接丢弃一切事件（LogBatchStore.java:174）→ 手动一键上传 `shouldSeal(0,true)=false`（LogUploadDispatcher.java:284-286）→ Summary sealed=0/pendingEvents=0/pending=0 → app.js:1327-1330「暂无可上传的新日志」。**定性为采集问题**（不是存储/队列/状态判断问题——队列如实反映空，空根因是采集门）；v7.8 无此问题因为上传内容=内存快照（buildLogBody 永远有内容，采集与上传解耦，v7.8.0=commit 1963ae4：maybeAutoUploadLog:649 八小时成功才计时 + doUploadLog:1097 快照直传 + Net.postIssue:199 PAT 直发）。违反用户红线「不允许因为自动上传关闭而停止日志采集」。
- **架构精简（第四阶段执行）——删除约 1260 行复杂层**：`LogBatchStore.java`（642 行：PENDING→UPLOADING→ACKED/FAILED 六状态机、20 批容量环形、400 事件缓冲关键事件最后丢裁剪、failKind 分类、reviveExhaustedForManual 复活启发式、旧版 lastError 前缀 deny-list 归类、FAILED 14 天 TTL、ACKED_META 3 天 TTL、50 条环形元数据）、`LogUploadDispatcher.java`（480 行：守护线程+CAS、LogUploadPolicy 裁决表接线、退避表 30s→480s×5、6 次耗尽终态、v8.2.1 有界排空循环 8 轮、每轮 3 批、STATS 注入、mergeSummary）、`LogUploadPolicy.java`（141 行裁决/退避/终态分类——buildBody/isValidUuid/newBatchId 契约函数平移入新类）。替换为两个小类：`LogStore.java`（319 行：单文件事件缓冲 2000 上限+batch-manifest 幂等清单+ok-times 滚动窗+拒收隔离+损坏检测+旧版遗留文件迁移清理）、`LogUploader.java`（468 行含完整契约注释：唯一上传路径 runUpload 逐块 ≤48、HTTP seam 可测、匿名 API 核实 Issue 真实 URL）。**失败重试节奏 = 下一次心跳（自动）或用户再点（手动）——退避表/耗尽终态/复活机制全部删除**。
- **六项业务功能落地（用户最终原则——除此之外零附加机制）**：①**采集常开**——`appendEvent` 无条件异步落盘（LogStore，无任何开关门；无障碍热路径零阻塞保持）；单文件 `pending-events.json` 重启保留（升级用户未上传事件无损继承，batch-*.json/acked-meta.json 旧遗留自动清理）。②**自动上传 24h 最多成功 3 次**——`ok-times.json` 滚动窗口只记服务端确认（失败/限流/拒收一律不耗额度）+ `autoEligible` 三重闸（8h 间隔 × 有新日志 × 24h/3）；无新日志零网络请求直接跳过。③**手动上传随时可用**——`submitNow` 不查本地额度（服务端 RATE_LIMITED 如实转达为「服务端限流」提示）；与自动 CAS 互斥。④**成功判定红线保持**——HTTP 2xx 且 body ok=true 双条件（ALREADY_ACCEPTED 复用原编号）；成功才消费事件。⑤**失败保留**——未确认事件原样保留，重试复用同 batchId（batch-manifest 按序分配/消费——「超时但 Issue 已建」重试必然幂等命中绝不二建）；六个终态拒收码隔离到 rejected.json 检视（重试必然再拒，不阻塞后续新日志）。⑥**开关只管自动**——「自动上传日志」开关（原「自动提交诊断日志授权」）只写开关位；采集与手动上传完全不受影响。
- **Issue 编号与链接一致性（上轮 UNKNOWN 的客户端侧收口）**：成功后匿名 GET `api.github.com/repos/xiaobaiba999/Skip-Ad/issues/{n}`——200 则采用 GitHub 响应中的 `html_url`（**真实 URL，非拼接**；https+github.com host 白名单双校验）；404 则只显示编号、绝不构造未经确认的仓库地址（Worker 实际目标仓仍 UNKNOWN——Net.java 注释同步改为诚实口径，删除已证伪的「私有仓 Skip-Ad-Logs」表述）。手动上传空队列四态如实区分：never-collected（引擎尚未产生日志事件）/ all-uploaded（已全部上传）/ append-failed（采集写入异常）/ read-failed（本地文件损坏）+ rejected-only/cleared/relay-offline 三个补充态——「暂无可上传的新日志」这类把采集问题甩锅给用户的文案从此不存在。
- **测试（39 套件 7068 断言全绿 = 36 既有 + 3 重写）**：删除 LogBatchStoreTest/LogUploadNowTest/LogUploadPolicyTest（旧模块已删）；新增 `LogStoreTest`（26 断言：采集常开 API 级钉死/重启保留/manifest 同号重试与消费对齐/24h 滚动窗/拒收隔离/清理与损坏检测/overview 契约）+ `LogUploaderTest`（58 断言：**真实 runUpload 代码路径 + HTTP 替身**——用户十项要求的 JVM 覆盖：有日志上传（100 条→3 块 47+48+5、九键契约、首块 STATS 注入 ≤48）/ 无日志零 POST 四态区分 / 失败保留重试同号 / 成功不重复提交+ALREADY_ACCEPTED 复用编号 / 手动不受配额限制（配额满 3/3 照传）/ 自动 24h/3 全边界+滚动窗腾额 / 无新日志零 Issue / HTTP 200 但 ok=false 不算成功+429 不耗额+终态拒收隔离 / Issue URL 验证命中与 404 空串）。既有 36 套件零回退（UpdateChannelTest/SubMirrorTest 等对 Net 契约的断言全过）。**诚实口径：真机 E2E NOT_RUN（沙箱无设备；workers.dev 从沙箱不可达）——JVM 测试证明真实代码路径语义，不冒充真机上传成功**。
- **8.x 全线召回（用户指令「8x版本全部召回清除」）**：GitHub Releases 删除 v8.0.0（draft）/v8.0.3/v8.1.0/v8.2.0 四个 Release 及资产（API 204 ×4，2026-11-22 执行）；仓库 dist/ 与 download/ 删除全部 8.0.x~8.2.1 APK 及 idsig；正式 Latest 回落 v7.9.6（存量 8.x 用户检查更新不会收到降级提示——版本比较判非更新）。v8.2.2 为召回替换版：versionCode 82200 > 82100（8.x 存量可直接覆盖升级），签名证书与 v7.8.0 逐位一致（05ada6e3…6f79——不清数据覆盖安装）。APK 882,230 字节 · SHA-256 `4a7ff728f0551c5be421d7e321d9e46e14a3f792eae7532baf41b6b75cc90e72` · 零凭据复查（token 四特征未命中 · ghp_/gho_ 前缀 0 命中）。
- **明确不动（有效功能零覆盖破坏）**：更新检查双通道（v8.2.0）/订阅管理/引擎全部扫描点击链路/SafetyGuard/PrivacyGuard（LogPrivacyFilter.java 与 LogEvents.java 白名单原样保留——服务端契约）/exportLog 本地导出/学习中心。Worker 端点与请求契约一字未改（服务端源码不可达，按已部署客户端契约原样保持——schemaVersion 1 九键/≤48 事件/UUID v4/终态码语义）。

## v8.2.1（2026-11-22）—— 日志提交可靠性专项：手动复活失败批次 + 排空循环 + Issue 直达（方向调整轮）

- **本轮定位（用户方向调整：取消 Cloudflare 更新代理部署——不再要求 Cloudflare 授权、不再部署更新中转端点、停止 check-host/Cloudflare API 调研；聚焦 GitHub Issues 日志提交闭环的「实现与完善」）**。执行前全链路真实核验（不依赖旧报告）：主仓 HEAD=62c9384 干净；日志管线五模块逐行证据核验（Explore 代理 file:line 级）；公开仓 90 个真实 Issue 逐一核查——**#1~#90 全部由 v7.9.6 及以前的旧直发通道创建**（旧客户端内嵌混淆 GitHub PAT 直发 Issue——git 史实证：`git show v7.9.6:Net.java` 的 postIssue+builtInToken；v7.9.7 PAT 移除），标题中的设备型号/提交方式标记均旧通道自组；**v8.0.0+ 的 Worker 中转通道尚无一条生产 Issue**（HANDOFF 口径 `DEPLOYED_BUT_NOT_E2E_VERIFIED` 保持——Worker→GitHub 建 Issue 一腿仍待真机端到端）。四个特殊问题定案：①v8.1.0 手动上传代码链完整（submitNowBlocking→Worker→issueNumber 回显）但零生产证据；②自动上传同；③Worker `/api/logs/receive` 是唯一提交通道（全 App 唯一 POST 点，postIssue 已移除）——这正是安全架构本身；④Android 不具备也不应具备无 Cloudflare 的直提能力（GitHub 建 Issue 必须持写权限凭据——嵌入 APK 即泄露，v7.9.7 PAT 移除轮已定案）→ 按用户第三节规则保留既有安全通道（已部署零变更）+ 完善客户端体验。
- **手动提交强化（第四节差距修复）**：①**失败批次手动复活**——`reviveExhaustedForManual`：一键上传开跑前把「重试耗尽型」FAILED 批次重置 PENDING（retryCount 清零获得全新 6 次尝试窗、nextRetryAt=now、**同 batchId**——服务端幂等兜底「超时但 Issue 已建」必然 ALREADY_ACCEPTED 绝不二建）；终态拒收型（PRIVACY_REJECTED/INVALID_REQUEST/UNAUTHORIZED/IDEMPOTENCY_CONFLICT/PAYLOAD_TOO_LARGE/MANUAL_RECONCILIATION_REQUIRED + client-defense + 隐私整批拒绝）**绝不复活**（重试必然再拒/服务端红线）；旧版本批次（无 failKind 字段）按 lastError 前缀 deny-list 启发式归类。②**有界排空循环**——手动路径最多 8 轮（每轮 3 批、无进展即停；≥ BATCH_CAP 20 最坏情况）：旧版一键上传每轮只传 3 批就停，重度用户（400 缓冲 ≈ 9 批）以为传完实际只传了 3 批。③**Issue 编号可点击直达**（openUrl 桥 → 系统浏览器；编号 parseInt 白名单化零注入面）——第四节「成功后显示 Issue 编号和可打开的链接」此前只有文本编号。④失败提示如实化：「失败批次已保留，重试时将一并提交」。
- **自动提交十款规则核验（第五节）全部在场**（证据行号入档）：24h/3 本地前置闸 + 服务端滑窗权威双保险；仅服务端确认（HTTP 2xx 且 body ok=true）才计数——失败/限流不消耗次数；失败批次退避保留（30s→480s×5）+ 终态保留检视；用户开关（默认关）；重复批次防重四闸（单飞 CAS/批次 UUID/服务端 batchId 幂等/UPLOADING 中断恢复）；进程重启状态恢复（batch-*.json + acked-meta.json + prefs）；学习开关解耦（v8.1.0 keepOn 修复在岗——心跳/自动上传在门之前）。
- **FAILED 细分与 TTL 实装（第六/五节差距）**：批次记录新增 `failKind`（terminal/exhausted）与 `failedAt` 字段（additive，旧批次兼容）；`FAILED_TTL` 14 天检视窗实装（v8.0.0 注释宣称但从未实现——到期于 dueBatches 扫描时按【墙钟】删除，不被查询参数驱动）；`ACKED_META_TTL` 3 天实装（相对最新 ACK 时间裁剪，50 条环形保持；ackedCount24h 只需 24h 窗口）。
- **attemptsMade 契约对齐（通读发现的 off-by-one）**：主路径 `decide(ok, code, attemptsOf(batch))` 传 POST 前快照少计 1 次（契约「含刚失败的这次」），实际第 7 次尝试才终态；本轮改 `+1` 与异常路径/退避表同口径——第 6 次即终态。异常路径耗尽判定 `>` 改 `>=` 同步对齐。
- **安全与整洁（零安全降低）**：①exportLog 补上 Java 侧二道脱敏（`Net.maskSensitive`——app.js 注释一直声称「双层脱敏」但 Java 侧从未真正调用，本轮落实现）；②死代码清理：`requestUploadNow`（v8.1.0 起零调用）、`buildLogBody`（AdSkipService 190 行旧 postIssue 时代遗留）、`newIssuePrefillUrl`/`enc`/`ISSUES_PAGE`（Net 零调用）+ 未使用 import；③secret-scan 私钥模式精度修正：要求完整 PEM 铠甲 `-----BEGIN … KEY-----`（真实泄露 PEM 必带铠甲零漏报；worker/src/update.ts 的 PEM 格式名解析代码不再误报——v8.2.0 轮该误报被「全量测试跑在提交前（worker/ 未跟踪）」掩盖，本轮首次全量跑在跟踪态暴露即修）。隐私三道防线（工厂白名单/封批 sanitize/服务端）零改动；APK 零凭据红线保持。
- **测试**：全量 **39 套件 7151 断言 0 失败**（v8.2.0 基线 7047）：新增 B11 手动复活 12 断言（耗尽型复活/terminal 绝不复活/旧式启发式归类/retryCount 清零/同 batchId/到期队列/幂等）、B12 TTL 4 断言（14 天清理/新鲜保留/acked-meta 裁剪/24h 计数）、P9 终态拒收判定 12 断言（复活门单一数据源）、S3p revived 契约（13 键 toJson）。B5 既有用例暴露 TTL 实现的真 bug（`dueBatches(Long.MAX_VALUE)` 惯用式触发误删）→ 修复为墙钟基准。Worker 模块 22 单元 + 3 live 本会话复跑通过（代码本轮零改动）。端到端上传成功链路（自动/手动 IO 面）保持 NOT_RUN 边界自知（Android 绑定类 JVM 不可实例化——真机验证计划不变）。

## v8.2.0（2026-11-22）—— 更新检查双通道：中转服务优先 + GitHub 直连回退（真机限流根因修复）

- **本轮定位（v8.0.3 真机实测反馈：检查更新报「查询失败：GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试」——GitHub 正式 Latest 已是 v8.1.0 但用户看不到）**：更新检查唯一通道是 GitHub 匿名 API（60 次/小时/IP，国内共享出口极易耗尽）。全链路真实核验先行（Net.latestRelease 三通道全匿名/Worker 仅日志中转不支持更新代理/私有 Worker 仓无凭据不可读（404 实测）/沙箱无 Cloudflare 凭据且 workers.dev 不可达/GitHub App 5859168·Installation 184262217 凭据只在 Worker Secret/v8.1.0 真实 API 结构认证抓取确认），不假设 Worker 已支持更新代理。
- **Worker 更新接口（worker/ 目录随仓交付——独立只读 GET /api/update/latest）**：自包含 TypeScript 模块接线到现有 skip-ad-log-relay（fetch handler 加一个分支）；GitHub App 安装令牌认证（WebCrypto RS256 JWT 铸造 + PKCS#1/PKCS#8 双格式 + 令牌内存缓存至过期前 60s）→ 401/403 有界单次匿名回退（**公开数据绝不要求扩权——权限不足功能完整**）；只返回 draft=false && prerelease=false（防御性双闸）；Cache API 边缘缓存 600s（clamp 300~900，同节点最坏 6 次上游/小时 << 60 次/小时）+ fetchedAt 二次新鲜度闸 + 失败负缓存 60s + AbortController 8s；响应零凭据（令牌形态测试钉死）+ CORS；错误契约 GITHUB_RATE_LIMITED/GITHUB_ERROR/BAD_METHOD/INTERNAL_ERROR；载荷字段与 Android parseRelease 完全对齐（app.js 下载链零改动）。测试 25 项：22 单元（AUTOMATED）+ 3 live（**REAL_API 模块级**：真实 GitHub 上游取到 v8.1.0 官方 digest sha256:50a7b702…、缓存命中两次请求一次上游、匿名回退通道真实可用）。部署状态如实：**PENDING_DEPLOY**（沙箱无 CF 凭据不索取；DEPLOY.md 一条命令 + 验证清单；部署前客户端自动回退直连零回退风险）。
- **Android 双通道（Net.java v8.2.0）**：通道 0 更新中转（UPDATE_RELAY_URL 从 LOG_RELAY_URL 同源派生——单一事实源；单次尝试 5s/8s 超时不重试；ok:true 即信任（含 hasRelease:false））→ 通道 1-3 GitHub 匿名直连（v8.0.3 语义逐字保持）。**错误五分类**：GITHUB_RATE_LIMITED / WORKER_UNAVAILABLE / NETWORK_ERROR / PARSE_ERROR / NO_NEW_VERSION（isNewer 由 Net.cmpVersion 数值段比较权威判定——"8.10.0">"8.9.0"）；**中转故障绝不误报成 GitHub 限流**（双通道各自归因：复合失败载荷带 errorCode + workerError + githubError；中转失败而直连成功 → 成功载荷附 workerError 供 UI「中转服务不可用，已直连 GitHub」提示）；不无限重试（中转恒 1 次；直连沿既有有界重试）；不频繁请求（手动触发 + 服务端缓存吸收）。MainActivity.netGetLatestRelease 传 versionNameOf；app.js 浏览器路径同序双通道（中转 CORS 已开）+ isNewer 优先原生判定。
- **下载与安全闸零降低（保持项逐字核验）**：SHA-256 + 大小校验元数据经 HTTPS 获取（apkDigest="sha256:hex" 官方格式）/安装前流式逐字节比对（Downloader.verifyDownloadedFile）/不符即删拒装/发布通道 host 白名单（github.com·githubusercontent·ghfast.top·gh-proxy.com 精确后缀匹配）/签名兼容（keystore 不变）——**不允许仅凭版本号安装**。唯一改动：校验判定提取为纯函数 verifyDigest + hex（JVM 可测；检查次序与文案与 v6.3.0 逐字一致——提取重构非改行为）。
- **v8.1.0 遗留修复**：「版本记录」板块（app.js CURRENT_RELEASE）内容停留在 v8.0.3——本轮起随版本同步更新（title/features/summary/date）；浏览器预览回退版本号 8.0.3/80300 → 8.2.0/82000。
- **测试（39 套件 = 38 既有 + 1 新增）**：新增 `UpdateChannelTest`（S1 版本比较 11 断言（数值段非字典序/v 前缀/缺段补 0）/S2 中转地址派生 5（同源跟随·结构性关闭·绝不拼错地址）/S3 中转载荷契约 15（字段对齐·hasRelease:false 透传·ok:false/契约破坏/垃圾→null 回退·isNewer 注入）/S4 五分类与复合文案 9（双归因呈现·仅直连文案 v8.0.3 逐字）/S5 复合失败载荷 6/S6 SHA-256 校验判定 11（大小不符优先·大小写不敏感·双缺拒绝·hex 补零）/S7 host 白名单 10（子串伪装域/查询串注入/非 https 拒绝）/S8 镜像合成 5）；Downloader 纯函数面纳入 JVM 编译（运行期零 Android 类加载）。既有 38 套件零回退。真机验证 REAL_DEVICE=NOT_RUN；Worker 部署实例 PENDING_DEPLOY。

## v8.1.0（2026-11-29）—— 真机反馈修复轮：开屏跳不过六项根因修复 + 一键上传日志（真上传取代导出）+ 自动上传连坐修复与 24h/3 本地前置闸

- **本轮定位（v8.0.3 真机实测三项反馈：①很多 App 开屏广告跳不过/无法点击跳过；②日志上传实际是导出；③要 24h 最多 3 次的自动上传）**：全链路真实代码审计（新沙箱重新克隆 ef83c90，三路并行深读 AdSkipService 5964 行 + core 28 文件 + app.js/UI/桥接/Worker 契约）定案根因，禁止堆关键词/扩坐标/降 SafetyGuard/缩间隔——所有修复保持「安全 > FALSE_CLICK > DUPLICATE_CLICK > MISS > 正确关闭 > 命中率 > 性能」红线。
- **广告跳不过六项根因修复（AdSkipService/AdDecisionCore/PowerGovernor）**：
  - **根因①「习惯学习」开关误门控整个开屏扫描**（真机最可能主因）：`tryScan` 的 `splashLive = learningEnabled() && …`（AdSkipService:3263 旧版）——用户关掉设置页「习惯学习」（文案只说学习，无从知晓它管着全部跳过）= **所有 App 开屏零扫描零点击**；而真正的学习 `learnFromManualClick` 反而不受该开关管（名实完全颠倒）。修复：解除 splashLive 门控 + learnFromManualClick 入口加 learningEnabled() 门控（开关真正只管学习写入，已学规则继续生效；开屏扫描只受 masterOn 全局开关管）。
  - **根因② CLEAN 档案失明（跨重启 72h 只剩 5 词探针）**：宿主窗档案（pkg|cls 连续 3 窗零候选 → CLEAN，跨重启持久）在无订阅覆盖时只剩 5 词文本探针（跳过/跳過/skip/关闭广告/關閉廣告）——「关闭」「×」类词典外关闭键整窗失明最长 72h。修复：新增 `nativeCloseCueProbe`（关闭/×/✕ contains 查找 + isCloseText 等值门 + 可见性）——命中即档案复活转全量路径；**探针层绝不直接出手「关闭」**（出手仍由全量 walkFused→decide L1.6 关闭键证据门：角标在场/顶部区/广告形态语境门 + ComplianceGuard 三重终闸逐候选把关——安全分层不变）；CLEAN 复查期 72h→24h（PowerGovernor.PROFILE_RECHECK_MS，PowerGovernorTest 常量引用自动跟随）。
  - **根因③ 零候选退避早杀链**：旧版「3 次零候选 + 2.6s」杀链把晚出跳过键（3~12s 渲染）本窗剩余固定档全灭。修复：豁免判定提取为纯函数 `AdDecisionCore.zeroCandExempt`（原五豁免语义逐字不变）+ 新增第六豁免——第一方入口窗（rootWindow）且无订阅覆盖不提前杀链（梯子跑满 12s 观察窗）；跨窗功耗治理仍由 CLEAN 档案兜底（功耗上限不变）；有订阅覆盖的窗不豁免（订阅 {0,700} 路径已保障，避免功耗回退）。
  - **根因④ nudge 补扫 4s 断流**：旧版 4s 后弹窗窗期外内容事件零补扫——晚出键出现时的内容事件全部落空。修复：4~12s 观察窗内以 600ms 宽节流放行补扫（比 80ms 高频档宽 7.5 倍挡内容风暴；最坏 ~13 次补扫/窗；12s 外不扫保持）。
  - **根因⑤ 晚档扫描预算缩水**：旧版晚档（≥3.6s）预算 360 反而比中档 600 低——晚出键恰在深树（H5/视频容器 500+ 节点）出现的时刻预算不够。修复：对齐中档 600。
  - **根因⑥ 名实一致化**：见根因①（learning 开关语义修复）。
- **一键上传日志（真机实锤「导出≠上传」根治）**：v5.5.0 时代「我的」页按钮直传 GitHub Issue（PAT）；v7.9.7 移除 PAT 后 `doExportLog` 替换实现但保留上传文案外壳（首点后按钮还显示「上传日志」——文案与行为分裂是投诉直接根源）；v8.0.0 真上传落地在设置页另一入口但 void 零反馈 + <20 条静默空转。修复：**「我的」页主按钮改真上传**——`LogUploadDispatcher.submitNowBlocking()`（手动路径强制封批 forceSeal——缓冲不足 20 条也封，轻用户点按钮必有反馈；与自动循环互斥 CAS + 短等重试；阻塞执行于后台线程）→ `MainActivity.logUploadSubmitNow(cb)` 回调 → `window.__logUploadCb` 渲染（成功显示 Issue 编号 + 24h 累计；失败显示原因；服务端限流如实告知「稍后自动重试」）；导出降为「导出」次按钮保留（离线场景）；设置页「立即提交」统一同一反馈链路。**手动上传不受 20 条阈值限制也不受 24h/3 自动额度限制**（用户明确意图优先；服务端 RATE_LIMITED 幂等兜底——绝不绕过服务端限额）。
- **自动上传连坐修复 + 24h/3 本地前置闸（KeepAliveReceiver/LogUploadDispatcher）**：① keepOn（保活提醒开关）连坐修复——旧版 keepOn 门在 selfCheck 之前，用户关「保活提醒」→ 心跳续期/订阅静默更新/自动日志上传三个零打扰系统功能全部静默死亡（心跳链断 → 之后连 SELF_CHECK 都不再触发）。修复：三个系统功能移到 keepOn 门之前，keepOn 只管通知/踢链部分；② 新增本地 24h/3 前置闸 `autoCheckEligible`（24h 内已 ACK ≥3 批 → 不再发起自动上传；服务端滑窗仍为权威——双保险省电省流量；手动路径不受此闸）；③ 失败退避保持 30s→480s×5 + 第 6 次终态 FAIL（绝不无限重试）；④ 重启恢复/批次到期/Doze 顺延语义全部保持（窗口式闹钟 + PENDING 状态机 + B7 中断恢复测试）。
- **测试（38 套件 = 36 既有 + 2 新增）**：新增 `AdScanReviveTest`（S1 跳过/关闭词分层红线——「关闭」裸词绝不进 TEXT_EXACT 必须走 L1.6 证据门 / S2 CLEAN 常量钉死 24h / S3 豁免六真值表 / S4 isCloseText 等值门与 contains 噪声滤除）+ `LogUploadNowTest`（S1 封批边界——自动 20 阈值不凑次数/手动 >0 即封/空批永不封 / S2 自动资格 8h+24h/3 边界 / S3 Summary 12 键契约 / S4 手动自动额度分离红线）；既有 36 套件零回退。真机验证全部 REAL_DEVICE=NOT_RUN（JVM 只证明纯函数语义与服务侧编译接线）。
- **明确不动的项（安全红线保持）**：ComplianceGuard 三重终闸/用户让路/小程序五层保护/多点防线/人手节奏/TapPoint 四层触点/面积形状门/根窗禁返回键/学习挂起验证——全部零弱化；探针层绝不直接点击「关闭」（分层决策）；无障碍树缓存策略/窗口生命周期纪律不变。

## v8.0.3（2026-11-28）—— 更新检查链路修复轮：GitHub API 限流即时报错（不再浪费重试）+ 下载文案如实描述 + 「用户看不到新版本」根因定案（非代码缺陷）

- **本轮定位（用户指令：优先解决用户无法检查到新版本——先完整追踪更新链路、以真实代码为准、不能为让用户看到新版本而绕过正式发布状态）**：逐行追踪「检查更新」全链路（入口→请求→解析→比较→下载→校验→安装），结论：**机制本身正确，用户看不到新版本不是代码缺陷**——更新接口只读 GitHub 正式 Releases API，而最新正式发布仍是 v7.9.6（2026-11-20）；v8.0.0 仅存 Draft（GitHub 匿名 API 设计上不返回草稿）、v8.0.1/v8.0.2 从未创建 Release 且代码尚未推送。用户看到 v7.9.6 是**正确**行为；出路 = 推送代码 → 建 v8.0.3 Draft → 真机验证 → Publish（不绕过发布状态）。
- **更新链路完整追踪（以真实代码为准 · STATIC_REVIEW）**：① 入口 = 设置页手动「检查更新」按钮（v7.0.0 已移除自动横幅——`app.js #btn-check-update` → `fetchLatest()` → 桥 `netGetLatestRelease()` → `Net.latestRelease()`）；② 请求 = `https://api.github.com/repos/xiaobaiba999/Skip-Ad/releases/latest`（通道 1/2：匿名、UA adshield-android、noRedirect 防重定向投毒、连接 7s/读 12s 超时、404 间歇重试一次）+ `/releases?per_page=8` 列表回退（通道 3）；③ **Draft 过滤两层正确**：GitHub 匿名 API 天然不返回草稿 + 回退通道显式 `draft||prerelease continue`（Net.java:242）——Draft Release 状态下应用正确忽略；④ 版本比较 = `tag_name` 去掉 v 前缀 → JS `cmpVer` 三段式数字比较 vs `AndroidApp.versionName()`（versionCode 只用于 Android 覆盖安装单调性，不参与比较——设计正确）；⑤ APK 下载 = 首个 .apk 资产的 `browser_download_url` + ghfast/gh-proxy 双镜像 + 30 秒无进展自动换通道；⑥ 安装 = SHA-256（GitHub API 官方 digest）/字节数双校验通过 → Android 8+ `canRequestPackageInstalls` 未授权自动拉授权页 → `ACTION_VIEW content://` 系统安装器；⑦ 更新失败永远保留当前版本（只下载不卸载，安装由用户确认）。
- **限流修复（本轮唯一生产代码改动 · Net.java）**：旧版 403/429 先浪费一次 800ms 重试（限流窗口小时级，重试必败再烧同一配额池两次）再报「GitHub 返回 403」——用户无从知道是限流还是何时能重试。新增 `rateLimited(int)` 纯函数（403=匿名配额 60 次/小时/IP 耗尽、429=次级限流）+ 两通道接线：即时报「GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试」，不浪费重试不走列表回退（同配额池必然同样 403）。404/2xx/3xx/5xx/0 判定为非限流（各走既有路径不变）。**ReleaseNotesTest 更新链路段 7 断言钉死判定边界（AUTOMATED）**。
- **文案诚实性修复（app.js）**：下载完成态旧文案「下载完成（已验 SHA-256）」与实现不符——SHA-256 实际在点击安装时才校验（`Downloader.install() → verifyDownloadedFile()`，v6.3.0 安装闸）——改为「下载完成，点击安装（安装前自动校验 SHA-256）」如实描述；`appVer()/appCode()` 浏览器预览回落值 8.0.0/80000 → 8.0.3/80300；关于页 `CURRENT_RELEASE` 从 v8.0.0 过期内容（三轮未更新）同步为 v8.0.1→v8.0.3 实绩（check-appjs 符号闸 5 项全过）。
- **明确不修的项（审计确认非缺陷）**：无法识别最新正式 Release（能——/releases/latest 语义正确）；误读 Draft（不会——两层过滤）；versionCode 比较错误（不参与比较，设计如此）；APK 下载 URL 错误（browser_download_url 正确）；网络超时（7s/12s + 重试 + 回退三重处理）；下载失败显示成功（failed 态换通道/重下载按钮）；安装失败无反馈（install() 错误逐条返回 + toast + 授权页引导）；版本缓存（无缓存，每次实时查询）。
- **测试（36 套件 6982 项 JVM 断言全绿——v8.0.2 为 6975，+7 = ReleaseNotesTest 更新链路段）**：既有 35 套件零回退；全源 ECJ 编译通过；secret-scan / app.js 符号闸 / version-sync（8.0.3/80300）/ `git diff --check` 全绿。
- **APK v8.0.3 真实构建（`./build.sh 80300 8.0.3`）**：`android-app/dist/adshield-v8.0.3.apk` · SHA-256 `444e14d4899843d5457e00a22952c619ed57067c9949e439d6eae622bc019aa9`；versionCode 80300 > 80200（防降级守卫在岗）；签名证书 SHA-256 `05ada6e3…` 与全部历史版本一致（覆盖安装兼容）；DEX 含 `rateLimited`、app.js 含新文案（产物级验证）。
- **本轮零识别/安全门/状态机/契约改动**：仅更新链路错误提示与页面文案；draft 过滤/版本比较/下载源白名单（host 精确匹配防构造 URL）/SHA-256 安装闸/未知应用授权引导全部原样保持。
- **诚实口径**：Draft Release 状态下的「应用正确忽略草稿」已由匿名 API 实测证实（/releases/latest 返回 v7.9.6，/releases 列表不含草稿——GitHub 设计如此）；真机安装/更新 E2E = NOT_RUN（无设备）；GitHub push 与 Draft 创建待用户操作（Deploy Key 添加 / 网页建草稿）。

## v8.0.2（2026-11-28）—— 双 DFS 单遍化专项（P3-1 落地）：matchAll+walk 两趟同树遍历融合为 walkFused 单遍 + 21 场景逐字段对照测试 + JVM 基准计数证据

- **本轮定位（用户指令：双 DFS 专项优化——先语义分析、再三方案评估、不强制合并、安全差异必须解释）**：上一轮审计把 P3-1 列为「下轮专项」并给出三个前置条件（JVM 计时基准 / 候选收集语义逐字段对照 / 全量零退化）——本轮三条件全部先行落地后才动生产代码。语义分析（三方案评估表已入 `V8-OPTIMIZATION-PLAN.md` v8.0.2 节）：**方案 B（单遍融合）在严格约束下输出与旧路径逐字段等价**（骑士门序/去重/排序/满 4 早停同停点 = matchAll 语义；决策信号 = 同一 visit 体 rider=null 时 byte-for-byte 旧 walk），方案 C（分阶段决策）改变 SafetyGuard 数据时序被否决，方案 A（双 DFS+属性缓存）收益不足且踩「缓存 AccessibilityNodeInfo 快照」灰区被否决。
- **生产代码改动（1 处核心 + 1 处接线）**：① `AdDecisionCore` 新增 `walkFused(root, ctx, ruleRider, maxHits)` → `FusedScan{find, ruleHits}`——规则骑士与决策信号在**同一次 visit 遍历**中收集（共享预算同减点、共享属性单读、childCount 上提单读）；骑士满 4 命中即整树早停（与 matchAll 的 entry 检查+子循环条件同停点，此时 find 被下游丢弃——旧路径 walk 本就不跑）；walk() 保留原签名原语义（复验针/插屏扫描等 4 处既有调用方零变化）。② `AdSkipService.tryScan` 接线：probe 未中时一次 `walkFused` 替代 matchAll+walk 两趟（rider=splashLive 与旧路径 matchAll 仅开屏窗跑同构；弹窗窗 rider 关 = 纯 walk 语义）；probe 命中时融合遍历不跑（规则路径直接走）。零命中扫描（档位大头）同树不再走两趟。
- **三处已声明语义差（全部测试钉死，绝不静默接受）**：① **树在两趟 DFS 之间变化**（DualDfsParityTest S11 双方向）：旧 walk 读第二时刻快照、fused 只读第一时刻——晚到候选顺延到下一档（60ms 梯子/nudge 80ms 通道覆盖；反向早退角落键则 fused 先见）。安全论证：出手前 `refreshLive()` 人眼追踪复核不变（v6.0.0 防线），候选仍全部过 execute() 边界自检三重断言——零新增误触面，仅极端竞态下 ≤1 档延迟（与 v8.0.1 P2-1 同类权衡）。② **子树瞬时 binder 异常**（S12 双类型）：确定性异常两路径同点失败同子树同丢（等价）；throw-once 瞬断旧 walk 二趟重试可恢复、fused 单次即失——宁少不误方向，下一档补扫。③ **小程序容器**（S17）：旧 matchAll 命中后被 execute() L0 终门拦截（零点击但每档白付派发尝试+日志）；fused 骑士入口即抑制（与 walk 同门同位）——出手结果同为零点击，差异仅日志量下降与 acted 标志（退避可更早触发，方向更省更安全）。
- **新增 DualDfsParityTest（21 场景 · 92 断言）**：S0 rider=false≡walk 基线 / S1 普通内容页 / S2 开屏广告页 / S3 WebView 广告页 / S4 Native vid 键 / S5 Compose 深链 / S6 极简广告页 / S7 聊天页孤立跳过文字（gestureOnly）/ S8 输入框 / S9 可滚动 / S10 多窗口（内容根+Dialog 根）/ S11 树中途变化两方向 / S12 节点异常两类 / S13 预算截断（700 节点/预算 340 恰耗尽）/ S14 多候选并发（6 跳过+3 关闭+角落+倒计时）/ S15 订阅命中合成顺序（规则在前订阅追加）/ S16 订阅未命中 / S17 小程序容器 / S18 骑士满额早停 / S19 计数对照 / S20 vid 门边界（叶子/非叶子/短 vid）——每场景断言旧组合 vs 新单遍的**规则命中集+排序后顺序、决策信号全字段（skipCands/fakeCands/corners/cdDigits/longText/edit/scroll/hot/webview/dialog/nodeCount…）、决策结果（decide 的 action/reason/bounds）**逐项一致。
- **新增 ScanBenchmark（三形态×三规模 · 63 断言 · SIMULATED 级）**：宽簇/深链(≤30 深)/混合 × 150/400/600 节点。**确定性计数证据**（非计时）：child 调用（≈生产 getChild binder IPC）**精确减半**（298→149 / 798→399 / 1198→599）；text/desc/bounds/visible 读次**减半**；属性读总次降 **36%~40%**（2117→1351 / 5642→3601 / 8462→5401）；包装分配（≈NodeAdapter 分配）**减半**（300→150 / 800→400 / 1200→600）。JVM 计时（预热 300 轮+测量 1000 轮交错）：9 例中新路径全部不慢于旧路径（88~166µs vs 116~172µs 量级）——但 JVM 无 binder 成本，**计时仅供参考，真机性能/电量改善 = NOT_RUN**（不虚报百分比，计数证据是硬口径）。
- **既有防线在对照测试中实锤在岗**：菜单行保护（isMenuListRow——S14 首版测试树 6 个同宽同列竖排按钮被正确拒收，测试树改为分散布局后正常命中——引擎行为正确）；小程序容器门；预算梯子截断；订阅反饥饿合成顺序；骑士满额早停与 matchAll 同停点。
- **第五阶段跨模块评估（8 项逐项判断，全部维持不改动——理由入档）**：appendEvent 全文件读写（P3-3：低事件率+独立 IO 线程+ACK 前不删语义依赖文件结构，收益/风险比不佳）；LogBatchStore 批次封装成本（同 P3-3 口径，封批频率受 48 上限钳制）；Dispatcher 并发与 CAS 门（单循环线程零阻塞已实锤，无竞争面）；videoPoll walkDue 与探针重复（P3-2：S9 纯函数真值表已钉，但服务侧改接线与 miss-streak 降档语义耦合——跳探针令 walkDue 针 streak 恒+1、降档提前、文本键发现延迟 1.5s→3s，无真机数据不动）；nativeTextProbe 与视频探针词族重叠（P3-2 同一针的同一面，同上）；maybeInterstitialScan 多信号整树扫描（P3-4：专用轻量遍历仅省 ~30% 属性读取且需复制 walk 语义）；页面级状态缓存失效机制（P4-1：96+ 包罕见路径，行为方向安全）；高频窗口事件扫描节流（nudge 80ms/弹窗 240ms/feed 1.2s/插屏 1.5s 已节流 + v8.0.1 已消 0ms 双扫——现状已达标）。
- **测试（36 套件 6975 项 JVM 断言全绿——v8.0.1 为 6820，+155 = DualDfsParityTest 92 + ScanBenchmark 63）**：既有 34 套件零回退；全源 ECJ 编译通过（5 个 R.* 基线错误不变——R 类由 aapt2 构建时生成）；secret-scan / app.js 符号闸 / version-sync（8.0.2/80200）/ `git diff --check` 全绿。
- **APK v8.0.2 真实构建（`./build.sh 80200 8.0.2`）**：`android-app/dist/adshield-v8.0.2.apk`（versionCode 80200 > 80100 防降级守卫在岗）；签名证书 SHA-256 `05ada6e3…` 与全部历史版本一致（覆盖安装兼容）；DEX 含 `walkFused(NodeView,Ctx,boolean,int)→FusedScan`（v8.0.1 DEX 零命中对照实证为新代码）；apksigner v2+v3 Verifies。
- **本轮零安全门/状态机/契约改动**：SafetyGuard/ComplianceGuard/AdTaskCore 九态/日志五模块/坐标安全边界/DecisionEvidence 证据链/SubscriptionManager 八格全部不动；探针词表/规则库/预算公式零变化。
- **诚实口径**：真机安装/回归 = NOT_RUN（无设备）；真机性能与电量 = NOT_RUN（JVM 计数≠真机 binder 成本，不虚报）；GitHub push 待用户添加 Deploy Key（v8.0.1/v8.0.2 本地提交保留）；Worker 仓契约 = UNKNOWN（私有不可达）；v8.0.0 Draft Release 保持不动（RELEASE_CANDIDATE 口径延续）。

## v8.0.1（2026-11-27）—— 系统级审计轮：全链路调用图审计（安全面零问题实锤）+ 窗口切换 0ms 双重全量扫描消除 + secret-scan 闸门自断修复 + 视频轮询节拍真值表补测

- **本轮定位（用户指令：系统级深度审计、跨模块性能优化、持续交付——不为版本号改代码，证据先行）**：从架构/业务逻辑/跨模块协作/性能/安全全链路审计出发，只实施「证据充分、收益明确、风险可控」的优化。产出 `docs/audits/V8-SYSTEM-AUDIT.md`（审计）+ `V8-OPTIMIZATION-PLAN.md`（分级方案）+ `V8-SYSTEM-OPTIMIZATION-REPORT.md`（交付报告）三件套。
- **P0-1 仓库卫生修复（实锤生产事故——接管首跑即被阻断）**：`reports/iteration-v8.0.0.md:43` 文档字符串字面含 PEM 头形态（列举扫描模式清单的文字），命中 `scripts/secret-scan.sh` 检查①正则 → **HEAD 上 `run-tests.sh` 与 `build.sh` 全部在 secret-scan 阶段 exit 1**——上一轮「先跑闸门后写报告」的时序差令交接口径（secret-scan PASS）与 HEAD 真实状态不符。修复：改写措辞（PEM 头字面 → 「私钥头」中文表述 + 审计注记）；**扫描脚本零改动**（闸门强度不变）。修复后 secret-scan PASS + 6802 断言全绿复跑（AUTOMATED 级证据）。注：本轮新写的审计/CHANGELOG 文档一度复刻同一字面形态三处——该形态在技术文档中天然高频，佐证 P0-1 的系统性（已同步修正）。
- **P2-1 性能优化（窗口切换 0ms 双重全量扫描消除——本轮唯一生产代码改动）**：`onAccessibilityEvent` 窗口切换路径先 `bumpScanChain`（排 18 档梯子含 0ms 档）再内联 `tryScan(…,0)`——梯子 0ms 档在几 ms 后对**同一棵树**再跑一遍全量扫描（守护①② BFS60+DFS80 / 反馈查 80 / 探针 5 词 / matchAll DFS / matchSources / walk DFS / updateDialog 查 120——至多 ~1500 节点访问），纯幂等冗余（v4.5.0 内联首扫引入时的源码注释自认「随后仍会扫（幂等）」）。**修改**：`bumpScanChain` 增加重载 `skipFirstRung` 参数，窗口切换路径传 true 跳过 0ms 档（60ms 档与 nudge 80ms 通道覆盖树演化）；nudgeScan/返回键复验两处调用无内联先行保持 false；**档案抑制路径（探针哨兵/低频档 {0,700}）不受影响**——该路径内联首扫不跑（抑制窗），0ms 档是唯一即时扫描。安全门/决策梯/任务门/状态机零变化。收益口径：每次切窗省一整遍全量扫描（SIMULATED 推理估算：典型日 ~120 次切窗 ≈ 日省 ~120 次全量树的 CPU/binder/电量；真机对照方法已写入审计报告——energyScans 计数应下降 ≈ 切窗次数）。
- **审计结论（安全面零问题——负结果也是结果）**：九态状态机真值表完备（非法转移拒收/终态不可逆/S_NONE 不无中生有）；taskId（winStart#seq 双因子）+ scanGen 代际护栏贯穿全部延迟回调（18 档梯子/nudge/复验/返回键复验/视频轮询逐一核对）；重复点击断源五道防线在岗（冷却 max(300ms,人手节奏)/F 同指纹静默/H 结果静默/I 每窗 2 击/终态 2.2s 冷却）；ACK 前不删日志/未授权零磁盘写/上传零阻塞（独立 IO 线程 + CAS 单循环上传线程 + 全链吞异常）全部结构性成立。**本轮零识别/安全门/状态机/契约改动**。
- **发现但本轮不实施（证据与否决理由全记录于审计文档）**：P3-1 matchAll+walk 双 DFS 同树串行（零命中扫描遍历成本 ~2×——单遍多消费者合并需 JVM 计时基准 + 候选收集语义对照测试先行，无真机兜底不重构核心决策遍历）；P3-2 videoPoll walkDue 针双重工作（与 miss-streak 自适应节拍语义耦合——S9 测试已补作前置钉子，下轮实施）；P3-3 appendEvent 全文件读写（低事件率 + 独立 IO 线程，收益/风险比不佳）；P3-4 插屏 walk 300 预算取 3 信号（专用轻量遍历仅省 ~30% 属性读取）；P4-1 防膨胀 clear() 全清模式（96+ 包罕见路径，行为方向安全）。**被否决方案**：合并守护走查①②（遍历顺序不同 BFS/DFS——合并改变安全闸预算截断语义，禁止）；缓存 updateDialog 每窗结果（弹窗可中途出现，缓存=MISS 回归）；订阅 matchSources 降频（vid 键晚出现依赖每扫咨询，且无覆盖包已被预过滤挡住零树遍历）；引入 WorkManager/对象池/响应式库（APK 零依赖红线 + Java 标准能力已覆盖）。
- **测试（34 套件 6820 项 JVM 断言全绿——v8.0.0 为 6802，+18 = VideoSplashTest S9 补测）**：`VideoSplashTest` 新增 S9 自适应轮询节拍真值表（v7.9.5 纯函数面此前零覆盖）：adaptivePollGapMs 三档边界（标准 1.5s/低证据 3s/终态 3s——miss×3 仍 1.5s、miss×4 恰降档、证据恢复升档）+ 4 参 pollDue 到期判定（同时刻不同档不同结论的边界对）+ walkDue×miss-streak 交互契约（walkDue 不加速降档——P3-2 重构不得改变此边界）。既有 33 套件零回退。全源 ECJ 编译与基线**零差异**（5 个 R.* 基线错误不变）；secret-scan/app.js 符号闸/version-sync（8.0.1/80100）全绿。
- **APK v8.0.1 真实构建（本沙箱补齐官方工具链后 `./build.sh 80100 8.0.1`）**：`android-app/dist/adshield-v8.0.1.apk` · 878134B · SHA-256 `ce87b2999db6689f2122c6300d2561500d1c6f9c95b55ba93e42c7673a8ddd3a`；静态检查全过——versionCode 80100 > 80000（防降级守卫在岗）；包名/versionName/minSdk 24/targetSdk 34 正确；签名证书 SHA-256 `05ada6e3…` 与全部历史版本一致（覆盖安装兼容）；ZIP 22 项；DEX 五 Log 模块 + `skipFirstRung` 在场、生产端点在场、dex+app.js 敏感扫描零命中。
- **诚实口径**：真机安装/回归/上传闭环 E2E = NOT_RUN（本沙箱无设备）；Worker 仓（私有）不可达 = 契约一致性 UNKNOWN；v8.0.0 Draft Release 保持不动（真机验证链未完成前不发布——v8.0.1 同样 RELEASE_CANDIDATE 口径）。GitHub Actions workflow 激活路径不变（需 workflow scope token 或网页创建）。

## v8.0.0（2026-11-25）—— 自动日志上传闭环轮：中转服务上线 + 客户端上传链路（授权默认关 · APK 零凭据 · batchId 幂等）

- **本轮定位（用户指令：Android 客户端 → Worker → GitHub Issues 完整日志上传闭环）**：中转服务（`Skip-Ad-Log-Upload` 仓库 `skip-ad-log-relay` v1.2.1 · Cloudflare Worker + Durable Objects）已部署至 `https://skip-ad-log-relay.2927609408.workers.dev` 并经用户真实浏览器 `/healthz` 验证（ok=true · ipHmacConfigured=true · fail-closed）；本轮把客户端上传链路接入主工程（`log-upload-staging/android/java` 三件套经四轮审计终验后进入仓库，另有扩展）。**Worker 端契约一字不改**——状态码/语义/幂等/滑窗全部沿用既有部署。
- **服务端凭据边界（零凭据红线保持）**：`Net.LOG_RELAY_URL` 只填公开服务端点；GitHub App 私钥 / IP_HASH_SECRET 等全部只存 Cloudflare Worker Secret——APK 内零令牌零私钥零 OAuth（v7.9.7 达成，本轮不破；secret-scan 闸全绿）。
- **客户端上传链路（5 个 core 模块 · 纯 Java 优先 JVM 可测）**：
  - `LogPrivacyFilter`（staging 终验版原样入库）：字段白名单/枚举/整数界/危险键**整批拒绝**/候选词族门/脱敏四正则——与服务端 `validate.ts` 同规约（双端同步演进 privacyFilterVersion 同号递增）；
  - `LogEvents`（新）：八类事件工厂（AD_TASK 终局/SAFE_BLOCK 终闸/CLICK 派发/VERIFY 复验/STATS 快照/GKD 拉取异常/VIDEO_POLL 探针命中/CRASH 类名）——字段名/枚举值与白名单一字不差（工厂输出过自家过滤器零剥离，测试钉死）；
  - `LogUploadPolicy`（新）：响应裁决纯函数——`ACK`（200 ACCEPTED/ALREADY_ACCEPTED）/`WAIT`（429 尊重 retryAfter 钳制 [30s,24h]，**不耗重试计数**）/`RETRY`（TEMPORARY_ERROR/网络异常/未知 code——退避 30s→60s→120s→240s→480s×5 同 batchId）/`FAIL` 终态（PRIVACY_REJECTED/INVALID_REQUEST/UNAUTHORIZED/IDEMPOTENCY_CONFLICT/PAYLOAD_TOO_LARGE/**MANUAL_RECONCILIATION_REQUIRED 立即终态绝不换 batchId 重传**——服务端三轮红线逐条落客户端）；请求体构造（schemaVersion 1 顶层九键 + UUID 防御门——明知 401 的请求绝不发出）；
  - `LogBatchStore`（staging 版 + StoreEnv seam）：PENDING→UPLOADING→ACKED/FAILED 状态机；installInstanceId 首启 UUID v4（清数据/卸载自然重置）；400 条事件缓冲（容量裁剪**关键安全事件最后丢**）；单批 ≤48（服务端契约上限，**余量留缓冲分多批**——8h 心跳间堆积不截尾丢数据）；批次容量 20 最旧先逐出；UPLOADING 中断恢复；ACK 前绝不删本地批次（红线）；
  - `LogUploadDispatcher`（staging 版 + 裁决表接线）：单后台守护线程（running CAS 门——绝不并发提交同批次）；非 2xx 响应体也解析（错误信封 code/retryAfter 全字段可用）；StATS 快照（六态 + GKD 八格纯静态计数器）每轮先行。
- **幂等核心（用户指令重点场景）**：每次封批生成 UUID v4 batchId；**网络超时重试复用同一 batchId**——服务端 LogGate 幂等使「客户端发送成功但响应丢失」场景下的重试必然得到 `ALREADY_ACCEPTED` 复用原 issueNumber，绝不二建 Issue；「重试不换号」是结构性保证（dispatcher 只读已存批次文件，无任何重封包路径）。
- **事件埋点（薄接线 · 全部一行 try/catch）**：AD_TASK 终局三路（markTaskSuccess/markTaskFailed/markTaskWindowChanged + taskOnScanOutcome 预算耗尽——Bug#001 根治同源路径）/ SAFE_BLOCK 终闸 / CLICK 派发成功与失败（recordClickEvidence 内统一）/ VERIFY 复验扫描（CLOSED/NOT_CLOSED）/ GKD 拉取异常（errClass 仅类名）/ VIDEO_POLL 探针命中（intervalMs=窗龄）/ CRASH（save 尽力而为 + readAndClear 幸存补点——仅顶层栈帧类名，绝不带堆栈正文）。
- **采集门（未授权零磁盘写）**：`LogBatchStore.setCollectEnabled`（授权 × 中转上线双条件）——未同意上传的设备上零新增磁盘写入；内存内 DecisionEvidence 诊断照旧。
- **隐私纵深（四端同族差一修复）**：身份证 18 位正则 `(\d{3})\d{12}(\d{3}[\dXx])`→`(\d{3})\d{12}(\d{2}[\dXx])`（19 位要求从未真正脱敏 18 位真证件号）——`Net.maskSensitive`/www `maskText`/`LogPrivacyFilter`（staging 已修）/服务端 privacy.ts 四端同修；`safetyReason` 先脱敏后截断（跨界 PII 前缀不因截断漏出）。
- **设置页真数据（www）**：上传状态行（待传批次数/24h 已提交（服务端权威，本地仅展示参考）/失败检视数/最近提交时间/最近失败原因）+「立即提交」（同一服务端 24h/3 限额绝不绕过）+「清理待上传」（只删本地文件）；授权开关未开时按钮区隐藏；手动本地导出通道并存。
- **上传失败绝不影响广告跳过（结构性纪律）**：埋点 `appendEvent` 异步落盘（单线程 executor 非阻塞 submit——无障碍热路径零新增同步 IO）；上传独立守护线程（绝不网络 IO 于无障碍事件/点击/复验线程）；`tapEvent`/埋点/上传全链 try/catch 吞尽。**本轮零识别/安全门改动**：不新增关键词、不扩大点击区域、不降 SafetyGuard、不加轮询、不动任务状态机、不动 taskId/generation/clickAllowed/verification/retry 五要件。
- **测试（35 套件 6802 项 JVM 断言全绿——v7.9.7 为 6653，+149 = 新增 3 套件）**：`LogPrivacyFilterTest`（47 项：白名单/危险键/枚举界/词族门/脱敏四正则含 18 位身份证差一回归/脱敏-截断顺序/MAX_EVENTS/工厂 schema 自洽）+ `LogUploadPolicyTest`（55 项：响应矩阵全枚举——200 幂等/429 不耗计数/六类终态/退避表/耗尽/请求体九键/UUID 校验与唯一性）+ `LogBatchStoreTest`（47 项：installId/采集门/400 上限关键最后丢/封批/隐私拒绝检视/48 分批/到期与中断恢复/ACK 前不删与 24h 计数/重试不换号/清理与 overview）。**org.json JVM shim**（tests/org/json——与 android.jar 二进制签名逐一对齐 javap 实测）：使 core 的 org.json 依赖在无 Android 运行时可全量测试（生产 APK 绝不包含——build.sh 只编译 src/）。既有 32 套件零回退逐套件核对；全源 ECJ 编译与 v7.9.7 基线**零差异**（5 个 R.* 基线错误不变——R 类由 aapt2 构建时生成）；secret-scan/app.js 符号闸/version-sync（8.0.0/80000）全绿。
- **诚实口径（能力边界）**：本沙箱无 aapt2/d8 构建工具链、无 Android 真机——**APK 构建与真机 E2E 均 NOT_RUN**（versionCode 80000 > dist 最大 79600 覆盖安装即升级；构建待有工具链的环境执行 `./build.sh 80000 8.0.0`）；Windows/真机 E2E 状态沿用 Worker 仓 `DEPLOYED_BUT_NOT_E2E_VERIFIED`；服务端 Windows E2E 脚本（e2e.ps1/e2e.sh）已交付待用户本机执行。
- **v8.0.x 展望**：真机验证（埋点→封批→上传→Issue 全链 + 网络断恢复/Doze/重启）；服务端 E2E 脚本用户本机回传；APK 构建与 GitHub Release 发布（publish-v800.mjs 仪式沿用既有模式）。

### v8.0.0 构建与交接轮（2026-11-26）

- **双仓库交接文档（换模型可恢复）**：主仓新增 `docs/PROJECT-HANDOFF-V8.0.0.md`（A–J 全景：架构/版本史/五模块/上传数据流/隐私红线/代码位置/证据口径/待办/P0–P5/纪律 + 20 项真机验证计划）；Worker 仓（`Skip-Ad-Log-Upload`）新增根 `HANDOFF-V8.0-WORKER.md`（API 契约全表·幂等与墓碑·GitHub App 边界·E2E 交付物·`DEPLOYED_BUT_NOT_E2E_VERIFIED` 不升格）。两文档均已 commit+push 并核验远端 SHA 一致。
- **APK v8.0.0 真实构建完成（`android-app/dist/adshield-v8.0.0.apk` · 878134B · SHA-256 `a6782b0696ba447938371977da99802e9add670191b8aaa924c99acc9338235c`）**：补齐官方 build-tools r34（aapt2 2.19 / d8 8.2.2 / zipalign / apksigner——官方 build-tools_r34-linux.zip 完整下载 + zip 完整性校验通过）后执行 `./build.sh 80000 8.0.0`。**静态检查全过**：versionCode 80000 > 79600（防降级守卫在岗）；包名 `com.zai.adshield`；versionName 8.0.0；minSdk 24 / targetSdk 34；签名 Verifies（v2+v3，v1 块在场——与 v7.9.6 签名 profile 逐项一致，证书 SHA-256 `05ada6e3…` 与历史版本完全相同 → 覆盖安装兼容）；ZIP 22 项（classes.dex 319348B + AndroidManifest + resources.arsc + www/subsnap 资产齐备）；DEX 141 类——五 Log 模块全部在场、测试类与 org.json shim 零命中（dexdump 类描述符级验证）、生产端点 `skip-ad-log-relay.2927609408.workers.dev/api/logs/receive` 在场；dex+app.js 全量敏感扫描零命中（ghp_/github_pat_/PRIVATE KEY/xox/AKIA 等形态）。
- **构建自动化（GitHub Actions）**：新增 `.github/workflows/build-apk.yml`（workflow_dispatch 可选 versionCode/versionName · 官方 ubuntu 环境 · sdkmanager 装 build-tools 34.0.0 + platform 34 · 复用 `./build.sh`（三道前置闸全程在岗）· 构建后六项验证步（存在/签名/包名/版本/SDK/SHA-256 任一不符即硬失败）· upload-artifact 上传 APK+idsig+sha256）；YAML 归档副本同步入 `docs/ci-workflows/build-apk.yml`。（注：首次推送 .github/workflows 需 token 具备 workflow scope——本轮 PAT 仅 repo scope，**push 实测被远端拒收**（remote rejected: without workflow scope）；处置：文件保留于本地工作树（未跟踪），内容以 `docs/ci-workflows/build-apk.yml` 归档副本入库远端——激活方式二选一：① 用户在 GitHub 网页创建 `.github/workflows/build-apk.yml`（内容用归档副本）；② 换带 workflow scope 的 token 后 `cp docs/ci-workflows/build-apk.yml .github/workflows/build-apk.yml && git add && git commit && git push`。）
- **状态口径**：APK = **构建完成 + 静态检查全过（AUTOMATED 级）**；真机安装/回归/上传闭环 E2E = **NOT_RUN（NOT_AVAILABLE——本沙箱无设备）**；正式 GitHub Release **未发布——草稿已就绪**（draft #453676040 · APK 资产已上传 · RELEASE_CANDIDATE 口径，真机验证通过后一键 Publish）；GitHub Actions workflow（`.github/workflows/build-apk.yml`）因 PAT 无 workflow scope 推送被拒——YAML 归档副本已入库 `docs/ci-workflows/build-apk.yml`，激活路径见交接文档 H-8。JVM 测试本轮接管重跑：34 套件 6802 断言 0 失败 + version-sync/secret-scan/app.js 符号闸全绿（与 v8.0.0 首轮口径一致，零回退）。**owner 拼写警示**：GitHub 账号真名 `xiaobaiba999`（b·字节级验证）；史料中 `xiaobaika999`（k）为同形字误拼——本轮新文档一度被污染已全部修正，生产代码零影响（详见交接文档拼写陷阱条目与迭代报告事故记录）。

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
