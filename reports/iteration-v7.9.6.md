# 迭代报告 · v7.9.6 —— L3 真实世界验证轮：Bug#001 终局三漏根治（2026-11-20）

> 本轮定位（用户指令）：**L3 第一批 50 款真实 Android 应用验证**——真实 App → 真实广告 → Decision Evidence → SafetyGuard → HumanTap → VERIFY → 真实结果 → 失败样本 → 缺陷修复 → 回归测试 → 下一轮。
> 本轮不是「继续堆功能」。按用户指令第一原则（无真实失败样本时不主动修改生产识别算法/规则/词表/点击区域/SafetyGuard/轮询），唯一允许的生产修改 = 「发现明确生产 Bug」。

---

## 一、v7.9.5 基线核对（执行前）

- HEAD = `ea6d615`（v7.9.5 报告补录）+ `95204de`（v7.9.5 主提交）；`origin/main` 与本地一致（fetch 核对）；工作树干净；
- `src/lib/version.ts`：APP_VERSION=7.9.5 / VERSION_CODE=79500；APK `adshield-v7.9.5.apk`（865846B · SHA-256 `118ab150b47149ab83b19ed53ee3c71c1a208b6740d501177b1472900e6c137c`）三目录分发在场；
- 测试基线：30 套件 6601 项全绿（round-46.json）——本轮执行前后各复跑一次。

## 二、安全状态检查（用户指令二 · 执行实录）

| 检查项 | 结果 |
| --- | --- |
| **旧 GitHub PAT 撤销状态** | **实测未撤销**：`local-gh-token`（仓库外 · `git ls-files` 确认未入库 · `git log --all -- local-gh-token` 确认从未提交）凭据经 GitHub API `GET /user` 返回 **HTTP 200**（认证有效，rate limit 5000 档）→ **SECURITY ACTION REQUIRED**：撤销需账号所有者在 GitHub Settings → Developer settings → Tokens 操作，本轮无法代为撤销；本轮继续只作仓库外未入库凭据文件使用（git-askpass 注入内存 URL），**未创建任何新的硬编码 Token** |
| 源码 / Git 历史 / 构建脚本 / CI | secret-scan 双闸（run-tests.sh + build.sh 前置）通过：令牌形态（ghp_/github_pat_/AKIA/私钥头/x-access-token）零命中 · credentials.ts 结构红线（Base64 长串/KEY 数组/XOR 解码器）零复活 · local-gh-token 未入库 · keystore 材料面收敛 |
| APK | 构建产物仅含签名材料（apksigner v1+v2 · Signer SHA-256 `05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79`——与 v7.9.4/v7.9.5 同证书，覆盖安装即升级） |
| **公开 keystore 风险** | 确认 `android-app/keystore/adshield.keystore` 入库且 `build.sh` 内置口令回退（`KS_PASS` 环境变量优先）——**知情接受现状**（docs/SECURITY.md R2 已载 v8.0 迁移新钥计划；按用户指令**不贸然更换签名密钥**——换钥即断全体存量用户的覆盖升级路径）；风险定性：任何人可签同签名 APK 冒充本应用分发，非 Play 渠道分发风险 |

## 三、L3 第一批 50 款执行尝试（用户指令三/四/五/十五）

- 读取 `compatibility/l3_batch1.json`（v7.9.5 建档 · 50 款 · 12/12 类配额 · video-splash 全收 6 · 引擎四档交叉 43/39/11/3 · P0/P1/P2 = 40/8/2）——**不自行替换名单**；
- 执行前置检查：本机 **adb 不可用 · Android emulator 不可用 · /dev/kvm 不存在** → 无真实 Android 设备环境；
- **REAL_DEVICE_TEST = NOT_AVAILABLE**：批次 1 保持 `PENDING_REAL_DEVICE`——50 款 14 项记录字段（app/…/finalResult）**全部保持 null 绝不预填**；`realDeviceTest` 增加 v7.9.6 复查记录（仍 NOT_AVAILABLE）；
- 「10 App → 20 App → 50 App」分阶段推进**未启动**（无设备即无第一批 10 款——诚实记录为 NOT_STARTED_FOR_NO_DEVICE，不伪造任何进展）；
- 每款 App 的测试类型（开屏/视频开屏/插屏/信息流/视频前贴/悬浮/反馈面板）均**未执行**——无设备即无「当前没有广告 → NO_AD_OBSERVED」的合法回填来源（该结果同样只能真机产生）。

## 四、真实结果枚举与数据诚信（用户指令四）

**本轮全部真实统计口径（L3 批次 1 · 50 款）**：

| finalResult | 计数 | 诚实口径 |
| --- | --- | --- |
| 真实观察广告 | **0**（NOT_AVAILABLE——非「没有广告」） | 无设备 → 无观察 → 无分母 |
| VERIFIED_SUCCESS | 0（NOT_AVAILABLE） | 禁止 RULE_ONLY = VERIFIED_SUCCESS；无真机零虚报 |
| MISS | 0（NOT_AVAILABLE） | 同上 |
| FALSE_CLICK | 0（NOT_AVAILABLE） | 真机样本回传唯一来源 |
| DUPLICATE_CLICK | 0（NOT_AVAILABLE） | 同上 |
| SAFE_BLOCK | 0（NOT_AVAILABLE） | 终闸在岗证据同样只能真机累计 |
| NO_AD_OBSERVED | 0（NOT_AVAILABLE） | **绝不 NO_AD_OBSERVED = SUCCESS**（且无设备时连「无广告」这一观察本身都不存在） |
| NOT_INSTALLED | 50 款全部等效「未在真机安装」——但按词表语义记录为批次级 NOT_STARTED_FOR_NO_DEVICE | 诚实批次状态 |
| CRASH | 0（NOT_AVAILABLE） | 无设备零虚报 |
| UNKNOWN | 0（NOT_AVAILABLE） | 无法归类不硬凑 |

**GKD 八格**（consulted/matched/candidateAccepted/clicked/verifiedSuccess/clickFail/falseClick/blocked）：全部 **0（NOT_AVAILABLE——真机环境不存在）**；matched→candidateAccepted→clicked→verifiedSuccess 证据链已在 v7.9.5 建成（上传日志「订阅证据链」行），等待真机首批真实分布。

**视频开屏真实数据**（首次发现广告证据时间/首次候选时间/首次点击时间/成功率/poll 次数/低证据 miss streak/观察窗实际时长）：全部 **NOT_AVAILABLE**；20s ceiling 保持 · 自适应轮询三档（v7.9.5）零改动——**没有真实数据之前不修改任何参数**（用户指令十二兑现）。

**极简窗口专项**（用户指令十三）：无真机 → 真实 FALSE_CLICK 观察面 NOT_AVAILABLE；v7.9.5 `MinimalWindowBatchTest`（32 断言）继续全绿在岗——8 类交互图标（更多/返回/收藏/分享/设置/头像）× 四角普通 UI 全零出手的既有契约零回退。「在角落 ≠ 是广告关闭按钮」的判定原则未做任何放宽。

**ScrollView/Input 真广告专项**（用户指令十四）：无真机 → 真实 MISS 样本 NOT_AVAILABLE；v7.9.5 WebView 综合证据路径（`webviewPresent && (adBadge || sdkAdWin || dialogForm || hotZone)`）零改动——**不因理论判断再动安全门，等真实样本**。

## 五、本轮唯一生产修改：Bug#001（用户指令一第 1 条「发现明确生产 Bug」· 代码审读实锤）

**发现路径**：按用户指令对 v7.9.5 新增「六态闭环」接线做逐路径审计（非理论推演——`taskAllowsNew`/`clickTries`/`endedAt` 全部记账点核对，`onVerifyAdGone`/`onVerifyAdStill`/`onWindowChanged` 三个终态转移源逐一比对终局三件套：state + endedAt + 六态计数——**其余转移点（markTaskSuccess/markTaskFailed/markTaskWindowChanged）三件套齐全，唯 `taskOnScanOutcome` 非零候选路径三漏**）。

**Bug#001（v7.9.5 引入六态闭环接线时新增的潜伏终局竞态）**：`AdSkipService.taskOnScanOutcome` 在「预算耗尽于复验时刻」（最后一击派发后复验仍见候选且 `triesOfClick ≥ maxClicks`）直接置 `S_FAILED`，**三漏**：

1. **不落 endedAt** → `taskAllowsNew(now, 0)` 因「无终态记录恒放行」（`AdTaskCore.java:244-247` 实测语义）**直接放行 → 2.2s 冷却闸被绕过** → 立即开新任务且 `resetTaskForNewInstance` 清空 `clickTries`/`gaveUp` → **同一存活广告借「新任务重置预算」无限重试循环**：
   - 「绝不无限重试/预算梯子」红线失守——用户指令九 DUPLICATE_CLICK 同族风险面（同一窗同一广告被反复点击，非同任务但同效果）；
   - execute 预算闸（`triesOfClick ≥ maxClicksWin`）因预算被重置**永不触发** → `gaveUp` MISS 记账路对整个流程失效；
   - 该路径是预算耗尽的**常见终点**（末击派发 → 复验针先于新点击尝试到达 → 首个非零复验即落 FAILED）——不是长尾边角而是主流终局路径；
2. **不记 MISS**：最后一击复验失败被膨胀记成 RETRY——六态把「任务终局 MISS」**整类漏记**（用户指令四/七「预算用尽 → MISS」口径被破坏；v7.9.6 起真实漏检率证据链失真——这正是 L3 真实数据统计最依赖的口径）；
3. **终局无日志**（用户无从知道本窗已收手）。

**修复（终局裁决收进纯函数 · 服务侧薄接线 · 零识别/安全门改动）**：

- 新 `AdTaskCore.onVerifyStillStep`（纯 Java · 与 AdTaskCore 同规约 · 返回 `VerifyStillStep{state, endedAt, outcome}` 三件套）：
  - **预算耗尽 → `S_FAILED + endedAt=now`（冷却闸在岗：新任务须过 2.2s 闸才重置预算——无限重试循环结构性阻断）+ `outcome=MISS`（任务级终局一次记账——本击复验不再计 RETRY，严格区分「点击派发成功」与「任务终局」）**；
  - 预算未尽且原态 VERIFYING → RETRY_WAIT + outcome=RETRY（**既有行为零变化**）；
  - FEEDBACK 复验在场 → 原态保持（面板流程在先，零行为变化）；
  - 终态原态 → 原样返回不计账（SUCCESS/FAILED 不可逆——旧任务绝不复活、不重复记账）；
- 服务侧 `taskOnScanOutcome` 仅应用步进结果 + 终局补「复验预算用尽」日志行；
- **本轮零识别改动**：不新增关键词、不扩大点击区域、不降 SafetyGuard、不加轮询——唯一生产修改是终局记账/冷却闸 bookkeeping（安全 > 正确关闭 > 命中率 > 性能微优化）。

## 六、一个真实 Bug 一个回归测试（用户指令十七/十八）

`Bug001RegressionTest`（新套件 · 52 断言 · 八段 · 已入 run-tests.sh 注册表）：

| 段 | 断言面 |
| --- | --- |
| S1 终局三件套 | FAILED + endedAt=now + MISS（对照预算内 RETRY_WAIT + RETRY） |
| S2 全生命周期 | 三击梯子：RETRY=2 · MISS=1 · FAILED 后点击/派发/回调全拒（第二次点击 = 0 · 预算梯子在岗） |
| S3 冷却闸闭环 | 终局后 t+0/500/1000/1500/2000ms 新任务全拒 · +2.2s 放行 · **endedAt≠0 结构性不变式（防回退钉死）** |
| S4 门语义取证 | `taskAllowsNew(now,0)=true` —— **正是 Bug#001 的绕过机制**，钉死「终局必须落账」的结构性原因 |
| S5 终态不重复记账 | FAILED/SUCCESS 原样返回 · outcome=-1 · endedAt 不被覆盖 |
| S6 FEEDBACK 语义零变化 | 面板在场（含预算同时耗尽）原态保持 · 不计 RETRY/MISS · 不落 endedAt |
| S7 RETRY 口径 | 仅 VERIFYING 首击复验计一次（RETRY_WAIT 期复验在场不计——防同一击多扫膨胀）+ RETRY_WAIT+预算耗尽防御路径同根治 |
| S8 六态计数端到端 | RETRY=2 · MISS=1 · VERIFIED_SUCCESS/FALSE_CLICK/SAFE_BLOCK/UNKNOWN 全 0 · 终局后旧任务复验不污染计数 |

## 七、失败样本库（用户指令十六）

`compatibility/samples/` 复查仍**空库（0 条）**：无真机即无真实失败样本，绝不预填。**Bug#001 为代码审读实锤的潜伏竞态（非真机失败样本——无 device/adType 等真实字段可填），按「一个真实 Bug 一个回归测试」直接修复并回归，不入真机样本库**（样本 schema 要求真机字段，伪造字段即违反数据诚信）；已在 index.json 记录归因说明。修复优先级 FALSE_CLICK > DUPLICATE_CLICK > MISS > 命中率保持。

## 八、测试与构建（用户指令十八）

- **JVM**：31 套件 **6653/6653 全绿**（v7.9.5 为 6601——**+52 = Bug001RegressionTest**；既有 30 套件零回退逐套件核对；修改前后各跑一轮全绿）；
- secret-scan 双闸（run-tests.sh + build.sh）通过；version-sync 通过（7.9.6 / 79600 双端统一）；app.js 符号校验通过（版本记录/扫描日志/使用报告/更新说明/解析器五符号在场）；
- 不得删除旧测试 / 不降低测试标准：30 个既有套件全部原样在册并全绿；
- **APK 构建**：`build.sh 79600 7.9.6` → 三目录分发（android-app/dist / download / upload）+ apksigner verify（Signer SHA-256 `05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79`——与 v7.9.4/v7.9.5 同证书，覆盖安装即升级）；
- 产物：865846B（848KB）· MD5 `432f1668b37831edb9699f22a4c99cd6` · **SHA-256 `bea90869273080e42c7f42f5c57ed8d7ffd9c31f1c2e8519d96801ddc2d88529`**。

## 九、版本与文档

- 版本策略（用户指令十九）：本轮**有生产代码修改（Bug#001）** → v7.9.5 → **v7.9.6**，versionCode 79500 → **79600**（> 79500 覆盖安装即升级）；
- 更新面：`src/lib/version.ts`（单一数据源）· `android-app/assets/www/app.js`（页头/版本记录/检查更新回落）· `docs/CHANGELOG.md`（v7.9.6 全条目）· `README.md`（当前版本区 + 体积行）· 本报告 · `docs/test-reports/round-47.json` · `compatibility/l3_batch1.json`（meta 复查记录）· `compatibility/samples/index.json`（复查归因）· `compatibility/test_matrix.json`（Bug#001 回归层登记）。

## 十、Git 与发布（用户指令二十 · 实测补录）

- **commit**：`920b10c`（fix(v7.9.6)：Bug#001 终局三漏根治——预算耗尽于复验时刻的记账/冷却闸统一裁决 + 一个真实 Bug 一个回归测试）· 19 文件（源 2 · 测试 1 · 文档 6 · compatibility 3 · 版本 2 · APK 三目录分发 3 + idsig 2）——diff 复查无 Token/密钥/keystore 密文/隐私/原始截图/完整 Accessibility Tree 入库；
- **push**：`ea6d615..920b10c main -> main` **SUCCESS**（git-push-once.sh token 注入内存 URL，无 force push；post-push fetch 核对 origin/main = 920b10c 一致）。诚实记录：首次 push 因脚本用法错误（`git-push-once.sh origin main`——脚本已内嵌远程 URL，正确用法仅传 refspec `main`）报 `fatal: refs/remotes/origin/HEAD cannot be resolved to branch` 且未推出任何提交；修正用法后成功——非网络/凭据故障，未产生任何远端变更；
- **Release**：`v7.9.6` **#452768731** https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v7.9.6 （draft=false · prerelease=false · tag 指向 920b10c）· APK 资产上传成功（865846B · state=uploaded · GitHub digest `sha256:bea90869273080e42c7f42f5c57ed8d7ffd9c31f1c2e8519d96801ddc2d88529` 与本地 SHA-256 比对 **MATCH=YES**——发布侧校验闭环）；
- **面板描述**：PATCH 成功（**304 字符 ≤ 350 限** · 远端 GET 核对与本地完全一致 MATCH=YES——v7.9.6 L3 真实世界验证轮主题 + 真机诚实口径入文案）；`scripts/publish-v796.mjs` 入库（发布过程可复现）。

## 十一、诚实声明（用户指令最终原则）

- **REAL_DEVICE_TEST = NOT_AVAILABLE**：本机无真实 Android 设备（adb/emulator/KVM 均不可用）——L3 批次 1 只定义未执行（50 款记录字段全部 null 绝不预填）；真机测试 0 · 真实广告观察 0 · 成功关闭 0 · 漏检 0 · 误触 0 · 重复点击 0 · 安全拦截 0——**没有真数据就没有结论**（0 = NOT_AVAILABLE 计数，非「测了且为零」）；
- FALSE_CLICK 计数器自动化环境恒 0（真机样本回传唯一来源）；
- Bug#001 修复效果在真机上的最终确认仍待 L3 批次回填（JVM 全生命周期回归已绿——严格区分 AUTOMATED 与 REAL_DEVICE）；
- **SECURITY ACTION REQUIRED**：旧 GitHub PAT 未撤销（HTTP 200 实测）——需账号所有者撤销；
- 优先级兑现：安全 > FALSE_CLICK > DUPLICATE_CLICK > MISS > 正确关闭 > 命中率 > 性能微优化——本轮只处理了「无限重试循环」（DUPLICATE_CLICK 同族）与「MISS 终局记账」两类问题，未动任何识别/安全门。

## 十二、下一轮 P0

1. **真机 L3 批次 1 执行**（需真机 adb 环境——批次定义与记录字段已建齐；10 App → 汇总 → 20 → 50 分阶段推进；P0 FALSE_CLICK 立即暂停扩张走修复流程）；
2. 失败样本入库驱动修复（FALSE_CLICK 最高优先级 → DUPLICATE_CLICK → MISS——一个真实缺陷一个回归测试）；
3. 真机回传后：订阅八格 / 点击六态 / 视频开屏真实时间线的**首份真实分布报告**（口径已在 v7.9.5/v7.9.6 建成）；
4. PAT 撤销确认（账号所有者操作）+ keystore v8.0 迁移计划保持。
