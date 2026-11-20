# 失败样本库（compatibility/samples/）· v7.9.5 建库

> 真实世界证据层的沉淀侧：L3 真机测试的失败案例逐条入库，驱动下一轮修复。
> 本目录当前为空库（0 条）——本机无真机（`REAL_DEVICE_TEST = NOT_AVAILABLE`），
> **绝不预填或伪造样本**。

## 修复优先级（用户指令十一 · 严格排序）

1. `FALSE_CLICK`（误触——最高优先级）
2. `DUPLICATE_CLICK`（重复点击）
3. `MISS`（漏检）
4. 命中率优化（最后才考虑）

原则：**安全 > 正确关闭 > 命中率 > 性能微优化**。

## 样本字段（每条失败样本必填）

| 字段 | 说明 |
| --- | --- |
| `sampleId` | 唯一 ID：`S-YYYYMMDD-NNN` |
| `package` | 应用包名 |
| `appVersion` | 应用真实安装版本 |
| `androidVersion` | Android 系统版本 |
| `device` | 设备型号（如 `Pixel 7` / `Redmi K60`） |
| `adType` | 广告形态：`splash` / `interstitial` / `feed` / `video-splash` / `banner` / `rewarded-video` |
| `uiFramework` | UI 框架：`Native` / `WebView` / `Compose` / `Flutter` / `ReactNative` / `Canvas` / `SurfaceView` |
| `result` | finalResult 词表（见下） |
| `decisionEvidence` | `DecisionEvidence` 最小结构化证据（引擎「上传日志 → 点击证据」JSON 行） |
| `failureReason` | 失败归因（人工/日志诊断结论，一句话） |
| `fixedInVersion` | 修复版本；未修复为 `null`（驱动下一轮 P0） |

## result 词表（与 L3 批次口径严格一致）

`VERIFIED_SUCCESS` · `MISS` · `FALSE_CLICK` · `DUPLICATE_CLICK` · `SAFE_BLOCK` ·
`NO_AD_OBSERVED` · `NOT_INSTALLED` · `CRASH` · `UNKNOWN`

**红线**：`NO_AD_OBSERVED` ≠ `SUCCESS`；`RULE_ONLY` ≠ `VERIFIED_SUCCESS`；
`dispatchGesture 成功` ≠ `广告关闭成功`（必须复验）。

## 隐私红线（结构性强制）

- 不保存账号、手机号、支付信息、聊天内容；
- 不保存原始屏幕截图；
- 不保存完整 Accessibility Tree——只允许 `DecisionEvidence` 的最小结构化证据
  （决策 reason / 候选来源 / 上下文四格 / 归一化几何 / 安全裁决）；
- 候选标签只允许 close/skip 词族成员（`DecisionEvidence.sanitizeToken` 白名单闸）。

## 入库流程

1. 真机执行 L3 批次（`compatibility/l3_batch1.json`）时失败（FALSE_CLICK /
   DUPLICATE_CLICK / MISS）；
2. 从引擎「上传日志」复制「点击证据」JSON 行（最小结构化——已过隐私闸）；
3. 按 `index.json` 的 `sampleSchema` 填写，追加到 `samples` 数组；
4. 下一轮迭代以最高优先级修复，修复后回填 `fixedInVersion`。

## 维护规则

- 每条样本必须可追溯到真实设备会话（device/androidVersion/date 齐全）；
- 修复后的样本**不删除**（回归防线——同形态缺陷复发即对照）；
- 每轮真实失败优先修复（用户指令十一），JVM 回归测试按
  「一个真实缺陷 → 一个回归测试」原则新增，不为数字加测试。
