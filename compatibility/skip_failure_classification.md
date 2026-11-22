# 开屏广告跳过失败分类体系（v7.9.8 · 任务五）

> **诚实口径**：本文档的每一类失败都映射到真实代码路径与本轮代码审计结论。
> 无真机样本支撑的归类一律标注 `PENDING_REAL_DEVICE`，绝不凭猜测把某类失败
> 归因到具体 App。v7.9.8 起 `SCAN_MISS` 结构化事件（白名单事件类型首次接线）
> 为每一类失败提供稳定失败码证据，真机回填后按码归档。

## 一、失败分类 A–J 与代码路径映射

| 类 | 含义 | 引擎代码路径（真实存在） | v7.9.8 结构化证据 | 当前状态 |
|---|---|---|---|---|
| A | 开屏广告完全没有被识别 | 事件入口过滤链（`onAccessibilityEvent` 前置丢弃：非前台内容事件/桌面/白名单/相机/ComplianceGuard 零扫描场景/小程序容器/系统噪声/息屏）；CLEAN 档案抑制（`profileSuppressScan`，3 窗零候选后无订阅覆盖窗只跑探针哨兵） | `SCAN_MISS` + `errClass=NO_RULE_ZERO_CAND`（无规则零候选）/ CLEAN 抑制窗在扫描日志有「省电档案生效」行 | 共性面已被 v7.7.0（回切根治）/v8.1.0（CLEAN 失明修复：24h 复查 + 关闭线索探测 + 第一方入口窗豁免）多轮收口；剩余缺口见「三」 |
| B | 识别到广告但没匹配规则 | `tryScan` 五层规则 + 订阅反饥饿（`needSub`）+ GKD 订阅 `matchSources`；规则条件过时（控件 ID/文案改版）时整窗零命中 | `SCAN_MISS` + `errClass=RULE_MISS_ZERO_CAND`（有规则零命中） | 需真机样本回填（`PENDING_REAL_DEVICE`）；SCAN_MISS 的 `activity` 字段直接给出窗口类供补规则 |
| C | 规则匹配但动作没执行 | `AdDecisionCore.decide` L1–L5 决策阶梯 + `SafetyGuard` 终闸 + `ComplianceGuard` 三重边界闸 + 任务门（`taskBeginOrCheckClick`——终态任务拒收点击） | `SAFE_BLOCK` 事件（`safetyReason` 稳定码）+ `AD_TASK` `outcome=SAFE_BLOCK/FAILED` | 宁漏勿误红线：拒收原因逐层记录（`boundaryGuardLog`），不放松闸门换命中率 |
| D | 跳过动作执行成功但广告没消失 | `execute` 派发 → 500ms 复验（`VERIFY` 事件）→ 任务状态机 `RETRY_WAIT` → 预算梯子（3/5 封顶）→ `MISS` 终局 | `CLICK` `clickResult=DISPATCHED` + `VERIFY` `verifyResult=NOT_CLOSED` + `AD_TASK` `outcome=MISS` | 状态机 v7.9.1 已根治无限重试；复验证据链完整 |
| E | 广告已消失但页面切换导致误判 | 窗口切换终局（`TYPE_WINDOW_STATE_CHANGED` → `markTaskWindowChanged`：有出手=SUCCESS 不可逆/零出手=CANCELLED）；就地 SUCCESS 两轮零候选确认 | `AD_TASK` `taskState=SUCCESS` + 窗口切换终局日志 | v7.9.1 任务状态机根治第二击/误判记账 |
| F | 特殊窗口/特殊控件 | SDK 独立包 Dialog 插屏（v7.9.0 接线 `maybeInterstitialScan`）；`typeWindowsChanged` 补订（v7.8.0 Dialog/PopupWindow 插层挂载）；图片角落键四通道；倒计时门控（穿山甲提前点白点根治） | `SCAN_MISS` `activity` 字段（窗口类）+ `CLICK` `candidateSource=IMAGE_CORNER/VID` | 图片钮路径（walk 角落候选 + videoPoll 每 3 针全量复扫）已多轮加固 |
| G | Android 系统限制 | 手势通道（`dispatchGesture`）失败面：`TapGestureCallback` 代际护栏；无障碍服务被 ROM 杀（KeepAliveReceiver 心跳拉活） | `CLICK` `clickResult=FAILED`（派发失败）+ `CRASH` 事件 | HyperOS 冷启动重绑慢已实测加固（15s 深挖拉活） |
| H | 广告页面加载过快/事件未及时捕获 | 0ms 内联首扫（v8.0.1 双扫消除后仍 0ms 出手）+ 60ms 档 + nudge 补扫（80ms 节流）+ 晚出键复活；视频开屏 20s 观察窗 + 1.5s 轮询针（v7.8.0） | `SCAN_MISS` + `errClass=VIDEO_LIKE_ZERO_CAND`（零内容事件+窗龄≥2s=视频容器静默铁证）+ `VIDEO_POLL` `intervalMs` | 视频开屏「全未命中」归因面 v7.8.0 起可观测 |
| I | 规则存在但条件过时 | 规则五层（分应用/全局精确/倒计时形态/短包含+诱导否决/vid+SDK close 族）；包名校准五项（v7.9.2 目录核实根治死包名） | `SCAN_MISS` + `errClass=RULE_MISS_ZERO_CAND` | 订阅生态（GKD 三源快照+市场日更）是主要修复通道 |
| J | 规则冲突/优先级错误 | `RuleEngine` 分应用行路由 + `RuleOrderTest` 顺序回归 + 订阅使用顺序治理（市场优先→内置兜底→全局组垫底） | `GKD` 七分类计数器（`gkdMatched` 等） | 订阅反饥饿 v7.9.1 根治（每窗首次扫描无条件咨询） |

## 二、v7.9.8 新增证据链（为什么没跳过——一次上传即可归因）

### SCAN_MISS 结构化事件（白名单事件类型首次接线）

```json
{"type":"SCAN_MISS","at":1795348800000,"pkg":"example.app",
 "activity":"example.app.MainActivity","ui":"SPLASH",
 "errClass":"NO_RULE_ZERO_CAND","scanCount":3,"intervalMs":4200}
```

- **稳定失败码四枚**（`LogEvents.scanMissCode` 纯函数·JVM 可回归）：
  - `NO_RULE_ZERO_CAND` —— 无任何规则/订阅覆盖（补规则或导订阅可解）
  - `RULE_MISS_ZERO_CAND` —— 有规则但零命中（规则过时/形态不匹配——看 activity 窗口类补规则）
  - `SILENT_TREE_ZERO_CAND` —— 树零内容事件且窗龄尚短（观察中）
  - `VIDEO_LIKE_ZERO_CAND` —— 树零内容事件且窗龄 ≥2s（SurfaceView/TextureView 视频容器静默铁证）
- **频率**：与扫描日志同频（每窗一条 + 同包 8s 节流 + 静默期/微树过滤）——零性能新增。
- **隐私**：字段全部在服务端白名单内（type/pkg/activity/ui/errClass/scanCount/intervalMs），
  零新增扫描、零页面文本、零设备标识符。

### 设备画像（STATS 事件 errClass 字段）

`{"type":"STATS","errClass":"DEV_Xiaomi.2410DPN6CC",...}` —— 品牌型号白名单形态码
（`[A-Za-z0-9_.$]` ≤80），随机身请求体 appVersion/versionCode/androidVersion 一起，
分析侧可按机型归因（无序列号/无 AndroidId/无任何唯一标识符）。

### 既有证据面（v8.0.0 起在场，v7.9.8 全保留）

`AD_TASK`（taskState×outcome 八枚举 + retryCount/scanCount/generation）、`CLICK`
（candidateSource 五通道 + clickResult）、`VERIFY`（CLOSED/NOT_CLOSED/UNCERTAIN）、
`SAFE_BLOCK`（safetyReason 稳定码）、`GKD` 七分类计数器、`VIDEO_POLL`、`CRASH`。

## 三、本轮审计结论（共性根因——真实代码证据）

1. **上传链路（非跳过链）的根因修复**：v8.2.2 简化上传器丢失了 v8.0.0 冻结批次
   语义——同 batchId 重试时 payload 必然漂移（createdAt 每次重取 + STATS 重生成 +
   新事件并入），服务端幂等判定为 IDEMPOTENCY_CONFLICT 终态拒收。这正是用户真机
   「第一次失败、第二次被服务端拒」的完整链条（第一次读超时 15s 太短 + 服务端实际
   已接收；第二次漂移 payload 命中冲突）。**修复：冻结批次 + 读超时 45s**（T11
   回归测试钉死）。广告跳过链路本身零改动。
2. **跳过链路共性面**：经完整代码审计（事件入口 → 扫描链 → 决策 → 执行 → 终局），
   A–J 十类中已知共性缺口均已在 v7.7.0/v7.8.0/v7.9.0/v8.1.0 历轮修复中收口
   （回切开屏/视频开屏/外来包 Dialog/CLEAN 失明/晚档预算）。**本轮未发现新的
   代码级共性缺陷**；剩余失败面需要真机 SCAN_MISS 证据回填后按码归因
   （`PENDING_REAL_DEVICE`——绝不无证据修改生产识别算法）。
3. **识别/执行红线保持**：本轮零改动 `AdDecisionCore`/`RuleEngine`/`SafetyGuard`/
   `ComplianceGuard`/`HumanTap`/`TapPoint` 识别与执行链——只新增证据记录
   （`logScanMiss` 内接线，零新增扫描/遍历/getWindows 调用）。

## 四、真机回填流程（小米 2410DPN6CC · Android 17 · SDK 37）

1. 安装 v7.9.8（versionCode 79800 > 79700 可覆盖安装；82300 用户见版本说明）；
2. 正常使用触发开屏广告（矩阵中 163 款开屏场景适用 App 任选）；
3. 「我的 → 引擎日志 → 一键上传」；
4. 开发者按 Issue 内 SCAN_MISS/AD_TASK/CLICK/VERIFY 事件链归因到 A–J 类，
   将结果回填 `compatibility/splash_matrix_v798.json` 对应行
   （detectionStatus/matchStatus/actionStatus/finalStatus + failureReason +
   testEvidence=real-device-issue-N）。

## 五、矩阵位置

- 233 款应用矩阵：`compatibility/splash_matrix_v798.json`
  （生成器 `scripts/gen-splash-matrix.py`——目录/订阅变化后重跑刷新）；
- 本轮统计：开屏场景适用 163 · NOT_APPLICABLE 70 · 有规则 174 ·
  NOT_TESTED 163 · **PASS 0 / FAIL 0（无真机证据绝不虚标）**。
