# 迭代报告 · v7.9.5 —— 真实世界证据层（2026-11-21）

> 本轮定位（用户指令）：**真实 App → 真实广告 → 候选 → Decision Evidence → SafetyGuard → HumanTap → VERIFY → 真实结果 → 样本沉淀 → 失败案例进入下一轮修复**。
> 本轮不以增加 JVM 断言数量为目标——把「证据层」建进生产链路，为 L3 真机回填与失败样本沉淀铺轨。

---

## 一、v7.9.4 基线核对（执行前）

- HEAD = `6f43785`（v7.9.4 迭代报告补录）+ `5d4087b`（v7.9.4 主修复）——与用户预期一致；
- `origin/main` 与本地一致（`git fetch` 核对）；工作树干净；
- `src/lib/version.ts`：APP_VERSION=7.9.4 / VERSION_CODE=79400；APK `adshield-v7.9.4.apk`（861750B · SHA-256 `742cf24c482cc39aacc0f2fbd752ca566c5486132ff6096d7a1fc10e05e91278`）三目录分发在场；
- 测试基线：28 套件 6487 项全绿（round-45.json）。

## 二、点击决策证据（用户指令第一优先级）

新核心模块 `core/DecisionEvidence.java`（纯 Java · 零 Android 依赖 · JVM 可单测）：

**每次准备 CLICK 前记录（最小结构化）**：

| 维度 | 字段 |
| --- | --- |
| 任务身份 | pkg / activity / taskId（winStart#seq）/ generation（扫描代际）/ taskState |
| 候选来源 | text / contentDescription / resourceId / imageCorner（四枚举） |
| 上下文四格 | adContext（角标/Dialog/半屏）/ sdkContext（SDK 窗指纹）/ webViewContext（WebView 容器）/ subscriptionContext（订阅路径） |
| 几何 | 归一化 cx/cy/area + screenRegion（TL/TR/BL/BR/MID）+ clickable |
| 安全 | SafetyGuard 裁决（BND_OK / BND_* 层名）+ finalDecision（CLICK_DISPATCHED / SAFE_BLOCK） |

**隐私红线（结构性强制——不是约定而是模块能力面）**：
- 不保存完整 Accessibility Tree：模块没有任何树容器/节点引用；
- 不上传原始屏幕截图：零位图/字节引用；
- 不保存账号/手机号/支付/聊天内容：候选标签白名单闸 `sanitizeToken`（只保留 close/skip 词族成员 ≤12 字符，其余一律空串——**实测**：手机号 `13800138000`、账号文本、支付文本、长文本、非整词全部空串拒收）；resourceId ≤32 字符且资源名形态校验（含中文/超长一律拒收）；
- 环形缓冲有界 12 条（**实测**：24 条记录后仍 12）；
- 证据链异常全吞——绝不影响点击主链。

## 三、点击结果闭环（用户指令第二优先级）

`CLICK_DISPATCHED → VERIFYING → 六态终局`：

| 六态 | 触发路径 | 语义 |
| --- | --- | --- |
| VERIFIED_SUCCESS | markTaskSuccess / markTaskWindowChanged（有出手） | 唯一成功证据 = 验证链（两轮零候选 ≥350ms / 窗口切换 / 反馈面板收起） |
| RETRY | taskOnScanOutcome（VERIFYING 仍见候选） | 合法重试等待（预算钳制） |
| MISS | markTaskFailed（有出手预算用尽） | 优雅收手 |
| FALSE_CLICK | ——（真机样本回传唯一来源） | 自动化环境恒 0，绝不虚报 |
| SAFE_BLOCK | 终闸 boundaryCheck 拒点 | 宁漏勿误闸在岗证据 |
| UNKNOWN | 诚实占位 | 无法归类不硬凑 |

**红线兑现**：dispatchGesture 成功 ≠ 广告关闭成功（AdTaskTimingTest Case3 契约保持）；上传日志新增「点击结果（六态计数）」与「点击证据（最近一击最小结构化 JSON 行）」两行。

## 四、GKD 订阅证据链八格（用户指令七）

`consulted / matched / candidateAccepted / clicked / verifiedSuccess / clickFail / falseClick / blocked`：

- **consulted**：matchSources 被咨询即计数（含未加载/无覆盖——订阅参与度与命中解耦，v7.9.1 反饥饿面的参与证据）；
- **candidateAccepted**：候选过 SafetyGuard 终闸（matched → accepted → clicked 链第二格）；
- **verifiedSuccess**：本任务出手 reason 为订阅规则且任务 VERIFIED_SUCCESS（点击→广告真关闭闭环，markTaskSuccess/markTaskWindowChanged 双路回报）；
- 七分类 subStat 与既有 S8 断言零破坏（`subStatSnapshot` 7 格不变，扩展走 `subStatV2Snapshot`）；订阅页 `stateJson` 新增 `subStatV2` 段；上传日志新增「订阅证据链」行。

## 五、WebView 综合证据路径（用户指令六：真广告 + ScrollView/Input/WebView）

**问题**：v7.9.4 已知 `isAdLikeLoose` 对滚动容器/输入框形成过强阻断；WebView 视频容器大树（>150 节点）同被拒——真 H5 广告带跳过文本整窗漏拦。

**修复（不取消安全门——综合判断）**：

```java
webviewAdEvidence = f.webviewPresent && (f.adBadge || ctx.sdkAdWin || f.dialogForm || f.hotZone)
```

- WebView 容器在场 **且** 至少一个广告语境证据（角标 / SDK 窗指纹 / Dialog 形态 / 热区）同窗才解锁 L1 跳过键与 L1.5/L1.6 关闭键三路；
- **WebView 单独在场不构成证据**（应用内浏览器普通页零出手——误触红线保持）；
- 候选语义（跳过/关闭词族）+ 形状门 + SafetyGuard 终闸全部在岗。

**JVM 证据面（DecisionEvidenceTest S4）**：真广告+ScrollView 出手 · 真广告+输入框出手 · 真广告+WebView 大树出手 · 应用内浏览器普通页零出手 · 原生滚动孤立跳过词零出手（v7.9.4 契约零回退）· badge_close WebView+角标+滚动出手 · 无第二语境 WebView 窗 × 键零出手。

## 六、视频开屏自适应轮询（用户指令八）

- 20s ceiling 保持；禁止固定高频轮询整个 20s；
- **低证据降频**：连续 ≥4 针探针零命中且零出手（≈6s 无证据增量）→ 1.5s→3s（`adaptivePollGapMs` 纯函数三档统一裁决）；
- **证据增强恢复**：探针命中或出手 → miss streak 归零，恢复标准节拍；
- **证据消失立即终止**：每针头部 videoLike 复核（既有契约保持）；
- 终态 3s 契约（v7.9.1）与既有 pollDue 3 参重载零破坏。

## 七、L3 真机批次 1（用户指令四）

`compatibility/l3_batch1.json`（`scripts/gen-l3-batch.py` 可复现生成）：

- **50 款 · 12/12 类配额**（不按列表前 50）：社交 5 / 短视频 5 / 电商 5 / 视频 6 / 音乐 3 / 新闻 4 / 阅读 4 / 浏览器 4 / 工具 6 / 游戏 4 / 学习 3 / 金融生活 5 / 其他 1；
- **特殊广告形态强制收录**：video-splash 全收 6 · banner 2 · rewarded-video 2 · feedback-sheet 4；
- **引擎覆盖四档交叉**：GKD 快照 43 / 内置订阅 39 / Feed 名单 11 / 零覆盖 3（长尾优先补位——诚实测盲区）；
- **P0/P1/P2 混合**：40 / 8 / 2；
- 每款 14 项记录字段（app/package/appVersion/device/androidVersion/date/adType/uiFramework/observedAd/candidate/decision/clicked/verification/finalResult）**全部 PENDING_REAL_DEVICE**；
- **REAL_DEVICE_TEST = NOT_AVAILABLE**（本机无真机）——绝不虚报执行结果。

## 八、失败样本库（用户指令十/十一）

`compatibility/samples/`（index.json + SCHEMA.md）：

- 每条失败样本必填：sampleId / package / appVersion / androidVersion / device / adType / uiFramework / result / decisionEvidence（最小结构化）/ failureReason / fixedInVersion；
- **修复优先级**：FALSE_CLICK > DUPLICATE_CLICK > MISS > 命中率（安全 > 正确关闭 > 命中率 > 性能微优化）；
- 当前库为空（0 条）——诚实标记（无真机即无真实失败样本，绝不预填）。

## 九、极简窗口专项批次（用户指令五）

`MinimalWindowBatchTest`（32 断言 · 八段）：

- 8 类交互图标（关闭/更多/返回/收藏/分享/设置/头像/普通按钮）× 四角（TL/TR/BL/BR）在普通 UI（滚动容器在场）**全部零出手**；
- 树极简兜底契约（≤60 节点 + 无文本 + 角落形态 → 形态路径出手——与 PageScenarioTest custom-draw A 同口径）；
- 内容丰富窗（>60 节点）8 类图标全零出手（需强广告形态）；
- 中部边带 strongOnly（FAB 防线——树极简兜底不越权）；
- 窗级门 / 时机闸（非开屏窗零出手 · <300ms 零出手 · 7s 超窗零出手 · 300ms 首过即出手）；
- vid 语义正信号（iv_skip 走跳过键路径）；
- 对照侧（角标在场照常出手——识别零回退）。

**诚实口径**：树极简兜底是形态路径（不区分 vid 语义）——普通 UI 的防误触由滚动/输入框/窗龄/节点数四道证据承担；本轮零安全门放宽。

## 十、测试与构建

- **JVM**：30 套件 **6601/6601 全绿**（v7.9.4 为 6487——+114 = DecisionEvidenceTest 82 + MinimalWindowBatchTest 32；既有 28 套件零回退逐套件核对）；
- secret-scan 双闸（run-tests.sh + build.sh）通过；
- version-sync 通过（7.9.5 / 79500 双端统一）；
- app.js 符号校验通过（版本记录/扫描日志/使用报告接线完整）；
- APK 构建：`build.sh 79500 7.9.5` → 三目录分发（android-app/dist / download / upload）+ apksigner verify（Signer SHA-256 `05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79`——与 v7.9.4 同证书，覆盖安装即升级）；
- 产物：865846B（848K）· MD5 `bed29adc43bc1769c44c7c5a588fa86c` · **SHA-256 `118ab150b47149ab83b19ed53ee3c71c1a208b6740d501177b1472900e6c137c`**；
- secret-scan 硬门随构建前置通过（令牌形态/内嵌凭据/本机凭据入库/keystore 面四道检查全绿）。

## 十一、诚实声明

- **REAL_DEVICE_TEST = NOT_AVAILABLE**：本机无真实 Android 设备——L3 批次 1 只定义未执行；真机测试数量 0、广告样本 0、成功关闭 0、漏检 0、误触 0、重复点击 0（**没有真数据就没有结论**）；
- FALSE_CLICK 计数器自动化环境恒 0（真机样本回传唯一来源）；
- 失败样本库空库（0 条）；
- L1/L2/FP/时序/证据层全绿 ≠ 真机验证——严格区分 AUTOMATED / REAL_DEVICE。

## 十二、下一轮 P0

1. 真机 L3 批次 1 执行（50 款逐款回填 14 项字段，finalResult 固定词表）；
2. 失败样本入库驱动修复（FALSE_CLICK 最高优先级 → DUPLICATE_CLICK → MISS——一个真实缺陷一个回归测试）；
3. 真机回传后订阅证据链八格与点击结果六态的首份真实分布报告；
4. 证据驱动选题（不再以断言数量为目标）。
