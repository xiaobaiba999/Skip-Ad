# Skip-Ad 迭代报告 · v7.9.4

> 日期：2026-11-21（Asia/Shanghai）· 轮次：round-45
> 主题：FalsePositive 场景层（20 类误触红线）+ AdTask 时序压力（A-H + Case1/2/3）+ 安全部署链整改
> 三层口径：【AUTOMATED】JVM 自动化断言 · 【SIMULATED】JVM 仿真树场景 · 【REAL_DEVICE】真机实测（本轮 **NOT_AVAILABLE**）

---

## 1. Git 基线

| 项 | 值 |
|---|---|
| 迭代前 HEAD | `39e5807` = origin/main（v7.9.3 已提交已推送） |
| 工作树 | 干净（迭代前 git status 零未提交修改） |
| 远程 | `https://github.com/xiaobaiba999/Skip-Ad.git`（origin） |
| 本轮策略 | 正常开发分支直接 commit + push（用户授权持续自主迭代；禁 force push / 禁删分支 / 禁改写历史） |

**基线核实（自行验证，非轻信提示词）**：v7.9.3 存在于 HEAD（`test(v7.9.3): L2页面场景层…`）、已推送 origin/main；versionName/versionCode 由 build.sh 注入（dist v7.9.3 APK = VC79300 实测在库）；CHANGELOG 头部 = v7.9.3；README 面板 = v7.9.3；src/lib/version.ts = 7.9.3/79300；app.js CURRENT_RELEASE = v7.9.3；**基线测试重跑：26 套件 6323/6323 全绿（自行复测确认）**。

## 2. 修改文件与原因

### 生产代码（2 处 · 全部为收紧面）

| 文件 | 修改 | 原因 |
|---|---|---|
| `src/com/zai/adshield/core/AdDecisionCore.java` | L1 跳过键路径出手条件 `R+F>0` → `R+F>0 && (isAdLikeLoose(f) || sdkAdWin || learnedCls)` | **FalsePositiveScenarioTest 实测揭出的真实误触缺陷**：旧版在开屏窗龄（12s）内对聊天窗/内容列表页孤立出现的「跳过」字样（可点小气泡/列表行文案）直接 `skip_text` 出手——零页面形态证据（用户 20 类红线场景 10 + 孤立词红线的直接触发面）。真开屏窗恒满足形态门（静态图+跳过键从不带滚动容器/输入框，≤150 节点）零识别回退；与 L1.6 关闭键路径（v3.7.0 同款门）口径对齐 |
| `src/com/zai/adshield/core/AdTaskCore.java` | 六个状态转移增加 S_NONE 守卫 | 「无任务」时任何事件不得无中生有出状态——服务侧 null 守卫已保证不传 S_NONE，纯契约收紧（无任务面真值表：onVerifyAdStill(S_NONE,…) 旧版会无中生有出 RETRY_WAIT） |

### 测试与工具（新增 2 套件 + 安全链 4 文件 + 闸门接线）

| 文件 | 说明 |
|---|---|
| `tests/…/FalsePositiveScenarioTest.java` | 新套件 · 69 断言 · 九段断言面（S1-S9） |
| `tests/…/AdTaskTimingTest.java` | 新套件 · 95 断言（A-H + Case1/2/3 + 无任务面） |
| `android-app/run-tests.sh` | 套件清单 +2；secret-scan 硬门接线 |
| `android-app/build.sh` | secret-scan 硬门接线；KS_PASS/KEY_PASS 环境变量签名口令注入 |
| `scripts/secret-scan.sh` | 新增密钥泄露扫描硬门（四道检查） |
| `src/lib/credentials.ts` | 重写：内嵌混淆令牌完全移除 → 环境变量/未入库文件凭据链 |
| `scripts/gh-token.mjs` | 推送链凭据源同款整改 |
| `docs/SECURITY.md` | 新建：R1 令牌披露（需撤销）+ R2 keystore 风险与迁移方案 |
| 版本与文档 | version.ts / app.js（符号闸校验通过）/ README / docs/CHANGELOG.md / round-45.json / compatibility/test_matrix.json / coverage_report.md / apps_catalog.json（gen-apps-catalog.py v7.9.4 重跑）/ 本报告 |

### 安全部署链整改（详见 §4）

Token 出库 + 密钥扫描双闸 + keystore 环境变量口令 + SECURITY.md 披露。

## 3. 安全审计（最高优先级 · 实测执行）

| 检查面 | 结果 |
|---|---|
| 源码/脚本/JSON/XML/CI | **R1 发现**：`src/lib/credentials.ts` 自 v2.0.0（commit `3f16c1c`，2026-11-16）内嵌「双层 XOR+Base64」混淆 classic PAT（ghp_ 40 字符，被 `print-token.ts`/`gh-token.mjs`/`git-push-once.sh`/auto-log-sync 使用）。**混淆不等于加密，公开仓库=已泄露** → 已处置（§4） |
| Git 历史 | 令牌存在于多个历史提交；**禁 force push → 历史不可原地清除**，历史暴露事实不可撤销 → R1 披露为需所有者撤销 |
| Android 签名 | **R2 发现**：keystore + 明文口令公开（v1.2.0 起）→ 不贸然换钥（直装分发换钥=断更），v8.0 迁移方案立项，KS_PASS/KEY_PASS 注入已支持 |
| APK 产物 | v7.9.4 APK 三面扫描（classes.dex + assets/app.js + 原始二进制）：**零令牌形态命中**（ghp_/github_pat_/AKIA/私钥头/x-access-token 全无）|
| 推送链完整性 | 整改后实测：`gh-token.mjs` 与 `credentials.ts` 均从新凭据链成功解出令牌（40 字符 ghp_）——推送能力保持 |

## 4. 安全整改明细（R1 处置）

1. **令牌出库**：`credentials.ts` / `gh-token.mjs` 内嵌令牌完全移除；凭据链 = 环境变量（`SKIPAD_GH_TOKEN`/`GH_TOKEN`/`GITHUB_TOKEN`）→ 仓库根未入库文件 `local-gh-token`（`.gitignore` `local-*` 放行收集·实测 git check-ignore 通过·权限 600）。
2. **需所有者执行**：撤销原令牌（GitHub → Settings → Developer settings → PAT 删除）。撤销前该令牌视为有效暴露凭据（历史中不可清除）。
3. **防复发闸**：`scripts/secret-scan.sh` 四道检查（①被跟踪文件令牌形态 ②credentials.ts 内嵌凭据形态——Base64 长串/KEY 数组/XOR 解码器 ③local-gh-token 绝不入库 ④keystore 材料散落），接入 run-tests.sh 与 build.sh 双闸，实测本轮测试与打包均先过此门。
4. **新令牌纪律**：绝不写回仓库（任何形态）；CI 用 Actions Secrets；本机用环境变量或未入库文件。

## 5. FalsePositiveScenarioTest（用户 20 场景规格全建齐）

**目的不是证明「该点击」，而是证明「绝对不应该点击」。** 【SIMULATED】仿真树与真实页面结构同构（类名/几何/可点性/可滚性/输入框特征），每场景断言 decide() 恒 NONE + 任务面零副作用；**风险面诚实记录**（角落候选确被收集、CTA 与关闭键诚实在场——断言门闸拒绝而非样本阉割）。

20 场景逐类结果：银行/政务/支付守护名单（守护+内容形态铁证）✅ · 相机（模式条可滚+角落图标在树仍零出手）✅ · **聊天窗口（孤立「跳过/关闭」气泡——实测揭出 L1 缺陷，生产修复后零出手）**✅ · 商品详情/购买弹窗（CTA 永不成为目标）✅ · 收藏/分享/评论 ✅ · 更新提示单信号 ✅ · 权限请求（允许/拒绝均不代点·最坏情形弹窗窗期在场仍零出手）✅ · 会员弹窗（最坏情形只关「关闭」族、绝不点「立即领取」）✅ · 普通 PopupWindow 菜单（孤立「不感兴趣」菜单项不点）✅ · 表单 Dialog ✅ · 悬浮 FAB（strongOnly 无强广告形态）✅ · 多窗口分屏 ✅ · Launcher（Workspace 可滚+图标标签零跳过候选——issue#12 历史红线）✅ · 小程序/支付/团购容器（L0 纵深防御·真广告形态树同拒·三容器）✅ · 反馈面板 ✅。

**孤立词红线**：「关闭/×/取消/跳过/稍后/知道了」①普通页孤立出现零出手 ②开屏窗龄内内容形态页（滚动列表行内孤立词——不靠「过了 12s」兜底）零出手。**对照侧（用户指令八兑现）**：真开屏形态窗「跳过」键照常出手（skip_text）+ 角标在场「×」照常出手（badge_close）——**不为降误触一刀切关闭识别能力**。

## 6. AdTaskTimingTest（A-H + Case1/2/3）

【AUTOMATED】纯函数时序竞态真值表：

- **A/B**（旧回调 100/300ms 两档）：两轮零确认 350ms 间隔不提前判关；SUCCESS 后 500/1250/2000ms 一切旧回调拒收；第二次点击 = 0。
- **C**（候选坐标改变）：candidateAlive=false（旧候选消失）五要件拒 → RETRY_WAIT 观察，绝不盲点旧坐标；新候选五要件重验全真才出手（打在新坐标）；窗口也变绝不出手。
- **D**（窗口切换）：有出手 → SUCCESS 终局；之后一切转移原样（不续命/不被 CANCELLED 覆盖/不进 FEEDBACK）；2.2s 冷却闸。
- **E**（Dialog 消失→普通按钮出现）：两轮零确认（350ms 独立两轮）→ SUCCESS；冷却期内残留「关闭/知道了」普通按钮不开新任务不点击。
- **F**（反馈面板）：面板里的「关闭/×」不得被当成新广告；终态不进 FEEDBACK 不补刀；2.2s 边界含尾（差 1ms 拒/满放行）。
- **G**（videoPoll 终态）：3s 降频契约（6500ms 不排/8000ms 排）；观察针发现 SUCCESS 零点击；残留候选不开新任务；白点节流 ≥1.5s。
- **H**（延迟露头键六档 100/300/500/800/2000/5000ms）：文本键出现帧即出手（露头就跳）；角落图标 300ms 时机闸（早于 300ms 等/过后即出手）；观察窗 12s/20s 双档与 arm-hint 免杀链。
- **Case 1**：第一次已成功 → 旧 task（clickAllowed 拒）/旧 callback（callbackAllowed 拒）/旧 timer（onDispatch 原样）/旧 videoPoll（onVerifyAdStill 原样）/旧 TapGestureCallback（onFeedbackSeen 零响应）**全拒；第二次点击 = 0（全程仅 1 击）**；冷却闸挡残留标签复活。
- **Case 2**：第一次失败 → RETRY_WAIT；五要件逐项缺失全拒（缺广告在场/缺同窗/缺候选存活/缺可见/缺语义各一断言）；全真才允许第二击；成功后第三击拒（重试链闭环）。
- **Case 3**：click()=true ≠ 广告关闭——onDispatch 只到 VERIFYING 绝不直判 SUCCESS；广告仍在 → RETRY_WAIT（API 成功不缩短验证）；3 击预算链 SUCCESS 或 FAILED 优雅收手；**onDispatch 五种非终态源枚举均无 SUCCESS 出口 + onVerifyAdStill/onVerifyUncertain 均无 SUCCESS 出口——唯一成功通道是真验证**。

## 7. AdTaskCore 状态机验证

- 终态不可逆保持：SUCCESS/FAILED/CANCELLED 之后无任何路径回 DETECTED/READY/CLICKING（Case 1/D 全枚举断言；SUCCESS 不能被 onCancelled 覆盖）。
- 每异步路径前置门保持：clickAllowed（提交门）/callbackAllowed（回调门）/retryGuardsOk（五要件）/taskAllowsNew（冷却闸）/zeroVerifyConfirmed（两轮确认）。
- **本轮加固**：S_NONE（无任务）守卫六转移——任何事件不无中生有（新断言面）。
- 服务侧接线核对：taskBeginOrCheckClick/taskOnDispatch/markTaskSuccess/markTaskFailed/markTaskFeedback/markTaskWindowChanged 全部 null 守卫 + 终态幂等（isTerminal 复核）——S_NONE 收紧零服务侧影响（实测 28 套件全绿佐证）。

## 8. SubscriptionManager 验证（反饥饿保持）

- v7.9.3 的 S8 七分类真实计数器断言（noCover/noMatch/hit/notLoaded/clickFail/clickOk/blocked 逐格）**本轮全量重跑通过**（PageScenarioTest 83 项零回退）——订阅统计细分口径保持。
- 反饥饿（needSub 每窗首次扫描无条件咨询因子）与七分类统计的生产实现零改动（本轮未触碰 SubscriptionManager——上次触碰是 v7.9.3 测试钩子，生产永不调用）。
- 内置规则命中 + 订阅规则也可能命中的双通道语义由既有 SubscriptionBuiltinTest 76 项 + SubMirrorTest 35 项 + RuleOrderTest 39 项回归保持（全绿）。

## 9. 233 款应用目录状态

- `compatibility/apps_catalog.json`：v7.9.4 重跑 `gen-apps-catalog.py`（meta 版本与 lastTestVersion 刷新）；233 款不变；引擎覆盖交叉不变（内置订阅 119 / Feed 名单 19 / GKD 三源快照并集 148）；12 分类配额全达标。
- `compatibility/test_matrix.json`：新增 **L2-FP**（FalsePositiveScenarioTest 69 断言·20 场景·孤立词红线·对照侧）与 **L2-Timing**（AdTaskTimingTest 95 断言）两层；L3 保持「待执行·REAL_DEVICE_TEST = NOT_AVAILABLE」。
- `compatibility/coverage_report.md`：分层进度表更新（L2-FP/L2-Timing ✅ 新增；L3 ⛔）。
- **诚实口径重申**：200 个规则测试 ≠ 200 个真机兼容；未验证数量 233（全量未真机验证，绝不虚报）。

## 10. JVM 测试结果【AUTOMATED】

```
密钥泄露扫描（secret-scan）: 通过（四道检查全绿——run-tests.sh 与 build.sh 双闸）
编译: ECJ -1.8（core + tests + SubscriptionManager + Net）零错误
总计: 28 套件 · 通过 6487 · 失败 0   （v7.9.3 为 26 套件 6323 项；+164 = FP 69 + Timing 95）
逐套件: AdTaskTest 83 / AdTaskTimingTest 95 / AggressiveModeTest 42 / AppCatalogTest 43 /
  BrandFocus396Test 4476 / ComplianceGuardTest 83 / DecisionGuardTest 22 /
  FalsePositiveScenarioTest 69 / FeedAdGuardTest 131 / HumanTapMatrixTest 20 /
  InterstitialGuardTest 118 / InterstitialRoutingTest 67 / MiniProgramGuardTest 36 /
  MultiDeviceMatrixTest 42 / NonMainstreamAppTest 130 / PageScenarioTest 83 /
  PowerGovernorTest 48 / ReentrySplashTest 25 / RegressionCoreTest 15 / ReleaseNotesTest 30 /
  RestoreGuardTest 106 / RuleGovernorTest 142 / RuleOrderTest 39 / SplashCountdownTest 61 /
  SubMirrorTest 35 / SubscriptionBuiltinTest 76 / TapPointMatrixTest 295 / VideoSplashTest 75
既有 26 套件计数逐项与 v7.9.3 基线一致——零回退零删除断言零降低标准
```

## 11. 构建结果

```
build.sh 79400 7.9.4 → ✅ 构建完成（844K）
aapt badging: package com.zai.adshield versionCode='79400' versionName='7.9.4'
sdkVersion 24 / targetSdk 34 / application-label 开屏卫士
apksigner verify: 通过 · Signer SHA-256 05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79
                 （与 v7.9.3 同证书——覆盖安装即升级，签名链连续）
versionCode 单调守卫: 79400 > 79300（历史最大）✓
```

## 12. APK 摘要

| 项 | 值 |
|---|---|
| 路径 | `android-app/dist/adshield-v7.9.4.apk`（三目录分发：android-app/dist / download / upload） |
| 大小 | 861750 B（844KB） |
| MD5 | `09a9be9090f4e058f8f4b95269942e05` |
| SHA-256 | `742cf24c1b56a44e9e5d581b827e1c88…`（前 16 位，完整值见 round-45.json） |
| 敏感字符串扫描 | classes.dex + assets/app.js + 原始二进制三面零命中 |

## 13. Git Commit

```
commit: 5d4087b（24 files changed · 1887 insertions · 309 deletions）
fix(v7.9.4): 误触红线与时序硬回归轮——FalsePositiveScenarioTest(20场景+孤立词红线+对照侧)
  +AdTaskTimingTest(A-H+Case1/2/3) +L1跳过键形态证据门根治(实测揭出) +S_NONE契约加固
  +安全部署链整改(Token出库/secret-scan双闸/SECURITY.md)
```

## 14. Git Push 状态

```
PUSH: SUCCESS
  git-push-once.sh main → https://github.com/xiaobaiba999/Skip-Ad.git
  39e5807..5d4087b  main -> main
  推送后核对: git fetch origin → origin/main = 5d4087b ✓（本地与远程一致，无分叉）
```

## 15. 真机测试状态【REAL_DEVICE】

**REAL_DEVICE_TEST = NOT_AVAILABLE**（本环境无 Android 真机连接）。

- 真机指标全部为 0：广告样本 0 / 成功关闭 0 / 漏检 0 / 误触 0 / 重复点击 0 / 安全拦截 0；未验证 233。
- **绝不把 NO_AD_OBSERVED 写成 SUCCESS；绝不把规则覆盖写成真实兼容；绝不把自动化断言写成真机测试。**
- 待真机日志回传后按固定枚举录入：VERIFIED_SUCCESS / MISS / FALSE_CLICK / DUPLICATE_CLICK / SAFE_BLOCK / NO_AD_OBSERVED / NOT_INSTALLED / CRASH / UNKNOWN（test_matrix.json L3 结果词汇表已建）。
- 建议批次：第一批 50 高频 → 第二批 100 → 第三批 233。

## 16. 已知问题与已知限制（诚实披露）

1. **L1 形态门的残余风险（宁漏勿误的另一面）**：带滚动容器/输入框的真开屏窗（极罕见形态）会被新形态门挡住——按用户最终原则接受（宁可漏掉不确定广告不误点普通按钮）。
2. **树极简兜底残余风险（v7.9.3 已披露·本轮保持）**：≤60 节点无滚动无输入的普通窗（如极简相机界面无模式条变体）角落图标仍可能过树极简闸——真实页面通常带滚动容器已被挡；L3 真机批次中重点验证该类。
3. **原 GitHub Token 需所有者撤销**（docs/SECURITY.md R1）——撤销前视为有效暴露凭据。
4. **keystore 公开风险保持**（R2）——v8.0 迁移轮处置，不贸然换钥（直装分发换钥=断更）。
5. **GKD 远程订阅 932 款逐款覆盖交叉**未完成（当前三源快照并集 148 款）。
6. **知识库高置信包名 95 款**待真机 adb 核验。

## 17. 下一轮建议（P0 优先级）

1. **【P0】真机 L3 第一批 50 高频应用**（待设备连接——含 FP 残余风险类：极简窗角落图标真机验证）。
2. **【P0】点击证据记录**（decision evidence 结构化：text/description/resourceId/imageCorner/adContext/sdkContext/webViewContext/geometry/clickable/safetyGuard/taskState——本机脱敏存储，FALSE_CLICK 归因闭环）。
3. **【P1】视频开屏自适应观察窗**（初始快速观察·零事件树静默降耗·广告画像成立延长·证据消失即止——20s ceiling 不变，禁固定高频轮询 20s）。
4. **【P1】无障碍事件驱动调度面 L2 补充**（探针节流/窗口代际/事件订阅纯函数抽取断言）。
5. **【P2】keystore 迁移轮预研**（新钥生成+安全存储+迁移指引文案）。

## 18. 三层口径汇总

| 层 | 本轮产出 |
|---|---|
| 【AUTOMATED】 | 28 套件 6487 项 JVM 断言全绿（+164）；secret-scan 密钥扫描双闸；version-sync 双端统一校验；app.js 符号闸 |
| 【SIMULATED】 | 20 类误触红线仿真场景（与真实页面结构同构）；12 类 UI 样本 × 8 项场景行为（v7.9.3 保持重跑） |
| 【REAL_DEVICE】 | **NOT_AVAILABLE**（零真机数据；绝不虚报——待日志回传后录入） |

---

## 附：本轮最终输出格式

【版本】v7.9.4（VC79400 > 79300 覆盖安装即升级）

【本轮核心目标】证明「绝对不应该点击」——20 类误触红线场景 + A-H 时序竞态 + Case1/2/3 硬回归 + 安全部署链整改

【安全问题】R1 令牌出库（需所有者撤销）· R2 keystore 披露（不贸然换钥·迁移立项）· secret-scan 双闸上线 · APK 三面扫描零命中

【生产代码修改】2 处收紧（L1 跳过键形态证据门——实测揭出的真实误触缺陷根治；AdTaskCore S_NONE 契约加固）

【测试新增】FalsePositiveScenarioTest 69 + AdTaskTimingTest 95 = 164

【测试结果】28 套件 6487/6487 全绿（既有 6323 项零回退·逐套件计数核对）

【JVM】✅ 全绿（含密钥扫描闸、版本同步闸、页面资产符号闸）

【真机】NOT_AVAILABLE（零数据·绝不虚报）

【233 App】目录 233 款保持（引擎覆盖交叉 119/19/148 不变）；L2-FP/L2-Timing 新层建齐；L3 未开始

【APK】861750B · MD5 09a9be90 · SHA-256 742cf24c… · 同证书 verify 通过 · 三目录分发

【Commit】见 §13（本轮提交含全部变更与完整提交信息）

【Push】SUCCESS / FAILED —— 见 §14 实测

【已知问题】见 §16（6 项诚实披露）

【下一轮 P0】真机 L3 第一批 50 高频应用 · 点击证据记录
