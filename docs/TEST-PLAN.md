# Skip-Ad 测试体系与质量保障（v5.5.0）

| 项 | 说明 |
| --- | --- |
| 版本 | v5.5.0（模块七：新模块单测全量落地） |
| 执行入口 | `android-app/run-tests.sh`（纯 javac 零 Gradle；ci.yml android job，599 项断言全量门禁） |
| 断言总量 | v5.3.0 存量 **427** 项 + v5.4.0 功耗治理 **48** 项 + v5.5.0 新增 **124** 项 = **599** 项 |
| 基线数据 | v5.3.0 真机日志 #57/#58（Xiaomi 2410DPN6CC）+ 演练场 27 轮回归 |

---

## 一、单元测试用例清单（按模块）

### 1.1 断言规模总表（v5.5.0 实际套件）

| 套件（tests/com/zai/adshield/test/） | 断言数 | 覆盖被测类 |
| --- | --- | --- |
| MiniProgramGuardTest | 存量 | core/MiniProgramGuard（小程序容器保护） |
| DecisionGuardTest | 存量 | core/AdDecisionCore（决策阶梯/守门） |
| SubscriptionBuiltinTest | 存量 | core/Subscription（GKD 订阅解析金样本） |
| RegressionCoreTest | 存量 | 回归核心（五层规则/复验/兜底） |
| MultiDeviceMatrixTest | 存量 | 多设备矩阵（分辨率/形态自适应） |
| NonMainstreamAppTest | 存量 | 非主流应用（百款包名/SDK 回归） |
| RestoreGuardTest | 存量 | core/RestoreGuard（品牌族自愈/拉活） |
| PowerGovernorTest | 48 | core/PowerGovernor（三零治理/突发预算/位置验证） |
| **RuleGovernorTest（v5.5.0 新增）** | **77** | core/RuleMerge（三源合并）/ LearnGovernor（生命周期）/ SmartScanPlanner（分级预算）/ RuleEngine 第 5 层新场景（更新弹窗双信号/视频前贴/悬浮球/半屏插屏）/ 旧五层零回归 |
| **ComplianceGuardTest（v5.5.0 新增）** | **47** | core/ComplianceGuard（设备分级降级/形态自适应/零扫描扩容/动作边界自检） |
| **总计** | **599** | |

### 1.2 调度器 68 项（抽样）

断言描述抽样（每条对应一个 `expect`，`//` 后为期望值）：

```text
S-01 sourceWeight("custom") == 40        // 四源基础权重
S-02 sourceWeight("learned") == 30
S-03 sourceWeight("gkd") == 20
S-04 sourceWeight("preset") == 10
S-05 sourceWeight("unknown") == 5         // 未知源保守降档
S-06 computeScore(全局preset规则) == 10 + 0 + quality/4 + hit/10
S-07 computeScore(专属规则) 比 同参全局规则 高 25      // 专属加成
S-08 computeScore(quality=100) 质量项 == 25
S-09 computeScore(hitCount=500) 命中项 == 20          // 上限封顶
S-10 computeScore(返回键类) 比同参非返回键类低 15     // back_fallback 降权
S-11 computeScore(priority=88) == 88                   // 人工优先级直通
S-12 computeScore(priority=250) == 100                 // 直通仍被 clamp
S-13 duplicate：同源同签名 → kind=duplicate, severity=high, suggestion=合并
S-14 duplicate：跨源同签名 → severity=low
S-15 overlap：同 adType+同 action+特征词交集 → kind=overlap, severity=medium
S-16 overlap：特征词无交集 → 不报冲突（viewId 型规则交人工）
S-17 contradict：instant_skip vs mark_only 同目标 → kind=contradict, severity=high
S-18 contradict：back_fallback vs instant_skip 同目标 → contradict
S-19 contradict：guard_skip vs preempt_block 同目标 → 不误报（同为代点族）
S-20 dedupeMerge(duplicate)：保留 computeScore 高者
S-21 dedupeMerge 同分：custom > learned > gdt > preset
S-22 dedupeMerge 同分同源：version 高者胜
S-23 dedupeMerge：败者 enabled=false 且 status=retired（留审计）
S-24 dedupeMerge：胜者 hitCount == 双方之和（计数并回）
S-25 dedupeMerge(overlap)：低分者 status=shadow
S-26 dedupeMerge(contradict)：双方 status=shadow 且 resolution=pending
S-27 schedule：结果按 computeScore 降序（稳定排序断言）
S-28 schedule：同目标键（appName|adType|matchType）仅保留最高分
S-29 schedule：appName="sdk:csj" 规则仅在 sdkFamily=csj 窗生效
S-30 schedule：shadow 规则绝不出现在生效列表
S-31 schedule：adTypeHint 与规则 adType 不匹配时过滤（any 例外）
S-32 importGkdRules：同 gkdRef + 同 pattern 跳过，skipped 计数正确
S-33 importGkdRules：不同 gkdRef 同 pattern 视为不同订阅（不跳过）
S-34 seedPresetIfEmpty：空库播种后 allRules 非空
S-35 seedPresetIfEmpty：非空库零改动（旧库自动补种不覆盖存量）
S-36 recordHit：hitCount+1 且 lastHitAt 更新
…（S-37 ~ S-68：normalizePattern 空白归一、patternTokens 引号提取、
   目标范围 "*" 通配、Map 之外 34 项边界与回归断言）
```

### 1.3 质量模型 54 项（抽样）

```text
Q-01 wilsonLowerBound(0, 0)  ≈ 0.167（Laplace 平滑后下界，非 0 非 0.5）
Q-02 wilsonLowerBound(10, 0) ≈ 0.739（新规则天然压分）
Q-03 wilsonLowerBound(3, 0)  ≈ 0.470（3 次命中不够能到 B）
Q-04 wilsonLowerBound(50, 1) ≈ 0.907（持续命中 A 级）
Q-05 wilsonLowerBound 单调性：hit 递增 → LB 非降（fail 固定）
Q-06 failWeight(2, 0) == 4          // 误触计 2 倍
Q-07 failWeight(0, 4) == 2.0        // 漏拦计 0.5 倍
Q-08 qualityScore(10, 0, 0) == 100 × Q-02（公式闭环）
Q-09 grade(85) == 'A' / grade(84.9) == 'B'（分级边界）
Q-10 grade(70) == 'B' / grade(50) == 'C' / grade(49.9) == 'D'
Q-11 isNoiseSample(sameSecondHits=2) → noise, reason=same_second_multi_hit
Q-12 isNoiseSample(缩水 39% 且窗类未切) → tree_shrink_insufficient
Q-13 isNoiseSample(缩水 40% 且窗类未切) → 通过（边界含等号）
Q-14 isNoiseSample(窗类切换且缩水 0%) → 通过（切换即关窗证据）
Q-15 isNoiseSample(候选窗类 ≠ 点击窗类) → window_class_unstable
Q-16 isNoiseSample(坐标出窗 bounds) → hit_out_of_window
Q-17 isNoiseSample(坐标恰在 bounds 边界) → 通过（闭区间）
Q-18 lifecycle ACTIVE + 误触率 15% → COOLING（边界含等号）
Q-19 lifecycle ACTIVE + 14 天无命中 → COOLING
Q-20 lifecycle COOLING + 连续 10 命中零误触 → ACTIVE（恢复唯一通道）
Q-21 lifecycle COOLING + 误触率 40% → RETIRED
Q-22 lifecycle RETIRED + 90 天无命中 → SHADOW（归档）
Q-23 lifecycle 任意 + resolution=shadowed → SHADOW
Q-24 lifecycle COOLING + 连续 9 命中 → 仍 COOLING（恢复门槛不含 9）
Q-25 generalizeToSdkFamily(hit<5) → null（泛化门槛）
Q-26 generalizeToSdkFamily(hit≥5 且 quality<70) → null
Q-27 generalizeToSdkFamily(全局规则) → null（无需泛化）
Q-28 generalize(穿山甲窗类) → appName="sdk:csj" 且 generalizedFrom=源应用
Q-29 generalize 结果 confidence == 源 × 0.9（泛化降档）
Q-30 generalize 结果 status == "shadow"（先阴影观察后转正）
…（Q-31 ~ Q-54：host 窗不泛化、quality 单调、误触率零样本定义、
   四规则组合拒学优先级等）
```

### 1.4 DSL 解析 92 项（抽样）

```text
D-01 compile("[vid=\"skip\"]").ok == true                     // 基础属性
D-02 compile("[text*=\"跳过\"]") 六操作符各 1 项解析成功（D-02~D-07）
D-08 compile("[text^=\"跳\"]").op == "^=" 前缀
D-09 compile("[text$=\"过\"]") 后缀
D-10 compile("[text~=\"跳.*过\"]") 正则命中「跳过广告」
D-11 未知属性 [foo="x"] → ok 且 warnings 非空，条件恒 false
D-12 正则语法错误 → warning + 条件恒 false（不抛异常）
D-13 关系符五类解析：[a] < [b] / > / - / + / <<n（D-13~D-17）
D-14 "<<2" 的 n == 2；"<<" 缺省 n == 0（任意祖先）
D-15 "@[text=\"跳过\"]" 目标标记；无 @ 时目标 = 最后一段
D-16 通配段 "*" 匹配任意节点
D-17 逻辑组合优先级：A || B && C 解析为 A || (B && C)
D-18 顶层切分跳过引号内分隔符："a||b" 作为 text 值不被切分
D-19 引号转义：[text="说\"跳过\""] 值含内嵌引号
D-20 单引号值、裸值（true/123）解析
D-21 布尔属性：[clickable=true] 与 isClickable 一致（大小写不敏感）
D-22 index/depth 数字比较：[index="0"] 命中首个子节点
D-23 匹配语义：[vid="ad"] > @[text*="跳过"] 在两树（命中/不命中）验证
D-24 匹配语义：[a] - [b]（下一兄弟）方向正确
D-25 匹配语义：[a] + [b]（上一兄弟）方向正确
D-26 匹配语义：[a] << [b] 任意祖先（深层嵌套命中）
D-27 匹配语义：[a] <<3 [b] 精确第 3 代（第 2 代不命中）
D-28 匹配语义：[a] < [b]（b 是 a 的父）方向正确
D-29 MatchBudget 耗尽 → 立即剪枝返回 miss（深树防卡）
D-30 describe() 规范形态：解析→describe 再解析 → AST 等价（幂等）
D-31 空表达式/null/只有空格 → ok=false + warning
D-32 关系符后缺段 → DslSyntaxException（严格模式）
D-33 编译缓存：同 pattern 二次 compile 复用（Java 侧由引擎缓存断言）
…（D-34 ~ D-92：多条件 &&、or 组合树、目标段在链首/中/尾、
   嵌套树回溯多候选、金样本集 30 条两端一致性镜像断言）
```

### 1.5 敏感防护 36 项（抽样）

```text
G-01 detect(com.android.settings, *, false).zeroScan == true（系统设置）
G-02 输入法窗口类含 InputMethod → zeroScan（不读节点）
G-03 hasPasswordField=true → PASSWORD 零扫描
G-04 SystemUI keyguard → KEYGUARD
G-05 拨号/InCallUI → IN_CALL
G-06 系统短信包名 → SMS
G-07 permissioncontroller → PERMISSION_DIALOG
G-08 无障碍 10 秒确认框（settings+Dialog）→ systemConfirm=true
G-09 systemConfirm 窗口：ComplianceSentinel 全动作拒绝（红线）
G-10 文本信号：验证码/OTP/转账/实名/支付 五类各 1 项（G-10~G-14）
G-15 isGuardApp(支付宝/银联前缀) == true；普通应用 == false
G-16 三参路径不依赖 windowTexts（未传文本时支付类不误报）
G-17 detect 判定优先级：权限对话框 > 锁屏 > 输入法（构造重叠信号）
G-18 auditAction(白名单外动作) → 拒绝
G-19 auditAction(目标文本含"立即更新") → 拒绝 + 审计落盘
G-20 auditAction(decline_tap 目标不在消极词库) → 拒绝
G-21 auditAction(decline_tap 目标="以后再说") → 放行
G-22 auditAction(面积 35.1%) → 拒绝；35% → 放行（边界）
G-23 auditAction(coord_tap 面积 12.1%) → 拒绝（坐标级更严）
G-24 auditAction(editable 目标) → 拒绝
G-25 auditAction(passwordNearby) → 拒绝
G-26 mark_only/overlay_notice/zero_scan → 直接放行（零点击类）
…（G-27~G-36：四大原则常量存在性、boundaryChecklist 文案、
   审计 sink 回调次数、mask 截断 32 字符）
```

### 1.6 功耗策略 48 项（抽样）

```text
P-01 matrixPolicy 16 格非空（4×4 全覆盖）
P-02 同档内：充电 ≥ 正常 ≥ 低电 ≥ 极低（每档逐字段非递增，重试链长度）
P-03 同电量内：旗舰 ≥ 高端 ≥ 中端 ≥ 入门（节点数/深度）
P-04 入门×极低：learningEnabled == false（学习层关闭）
P-05 旗舰×极低：learningEnabled == true（旗舰不关学习）
P-06~P-09 POLICY_FLOOR 红线四字段 ≥ 中档参数
P-10 redlinePolicy(入门×极低).maxDfsNodes == max(160, 400) == 400（红线托底）
P-11 redlinePolicy().learningEnabled 恒 true
P-12 三条红线通道字段（sdkFingerprint/popupDualSignal/learnedPreempt）恒 true
P-13 detectTier 评分边界：memScore 3/6/8/12GB 四档（Robolectric MemoryInfo）
P-14 socScore："kalama"/"pineapple" 命中旗舰分
P-15 socScore：API<31 回退 Build.HARDWARE
P-16 readBattery 充电态（plugged + status=CHARGING）→ CHARGING
P-17 readBattery 19%/20%/50% 边界 → CRITICAL/LOW/NORMAL
P-18 onBatteryChanged 状态迁移触发 refreshPolicy
P-19 重试链截断：入门档链长 3（疏链）≠ 旗舰档 6（全链）
P-20 内容预算矩阵：入门极低 6/min，旗舰充电 90/min
…（P-21~P-48：矩阵与文档 §2.1 表格逐格一致性 32 项——
   16 格 × 2 字段抽查，数值改一处测试即红）
```

### 1.7 全形态 44 项（抽样）

```text
F-01 classify(1080, 2400, false) == PHONE
F-02 classify(2176, 1812, false) == FOLD_UNFOLDED（aspect 1.20）
F-03 classify(2560, 1600, false) == TABLET（min 1600 ≥ 600）
F-04 classify(1080, 1200, true) == SPLIT_SCREEN（分屏最优先）
F-05 corner：PHONE = round(0.12×minSide)
F-06 corner：FOLD_UNFOLDED = round(0.09×w)（按宽收缩）
F-07 corner：TABLET = round(0.10×minSide)
F-08 skipAreaMax：TABLET 0.28 < FOLD 0.32 < PHONE 0.35
F-09 skipAreaMax：SPLIT 0.35 但分母为实际窗口面积（半屏窗大按钮拒收）
F-10 shapeGate(全屏热区节点) == false（面积超上限）
F-11 shapeGate(零面积/负尺寸节点) == false
F-12 shapeGate(宽高比 0.5 竖条) == false；0.6 通过（边界）
F-13 shapeGate(宽高比 41 横条) == false；40 通过
F-14 inCornerRegion 四角各 1 项（F-14~F-17）+ 中心点 == false
F-18 isHalfScreenAnchored(高度 34.9%) == false；35% == true
F-19 isHalfScreenAnchored(55.1%) == false；55% == true
F-20 isHalfScreenAnchored(高度 50% 但顶部锚定) == false（必须贴底）
F-21 isHalfScreenAnchored(分屏半屏面板) == false（实际窗口换算关键断言）
F-22 isFloatBallSized(直径 12.1% 窗宽) == false；12% == true
F-23 hotZoneEligible(55% 可点面积) == true（守护名单豁免入口）
F-24 防御：compute(0, 0) 不抛异常（零尺寸窗口兜底）
…（F-25~F-44：折叠展开态角落区不吞边缘按钮、平板大按钮被收紧上限
   拒收、分屏全链路 20 项镜像断言）
```

---

## 二、自动化场景回归框架设计

### 2.1 框架结构

```text
语料库（corpus/）
  ├─ apps/<appName>.json        百款主流应用语料（见 2.2 字段）
  └─ golden/<appName>.json      期望动作序列（人工标注 + 真机日志回放双源）
回放器（ReplayHarness，JVM）
  ├─ CorpusLoader               语料 → 窗口流（WindowContext 序列）
  ├─ MockNodeGateway            语料节点树 → NodeLike（实现 NodeGateway 接口）
  ├─ EngineRunner               MatchingEngine.processWindow 逐窗跑分
  └─ ReportBuilder              命中率/误触率/耗时/功耗估算四表
门禁（ci.yml android job 附加步骤）
  └─ 与 golden 期望序列 diff，任何偏差即 FAIL（确定性回放）
```

### 2.2 语料字段（单应用）

```json
{
  "app": "喜马拉雅", "pkg": "com.ximalaya.ting.lite", "category": "音频",
  "deviceProfile": { "tier": "MID", "battery": "NORMAL" },
  "windows": [
    {
      "seq": 0, "eventTimeMs": 0, "pkg": "com.ximalaya.ting.lite",
      "windowClass": "com.ximalaya.ting.lite.host.ad.SplashAdActivity",
      "entryWindow": true, "dialogWindow": false,
      "sdkFamily": "host",
      "nodes": [
        { "vid": "ad_container", "name": "FrameLayout", "bounds": [0,0,1080,2400],
          "children": [
            { "vid": "countdown_text", "text": "跳过广告 3", "clickable": true,
              "bounds": [890,120,1040,196] }
          ]}
      ]
    }
  ],
  "expect": [
    { "seq": 0, "action": "instant_skip", "engineMsMax": 60 }
  ]
}
```

语料来源三通道：真机无障碍日志回放（logs #57/#58 同源方法）、GKD 社区
快照转换、人工建模（对抗场景：假跳过/链式二跳/陀螺仪/零文本 SDK 窗）。

### 2.3 离线跑分输出

| 报告 | 字段 | 红线 |
| --- | --- | --- |
| 命中率 | 全量/分场景（开屏/半屏/弹窗/前贴/信息流/悬浮球） | 100%（v5.3.0 基线） |
| 误触率 | 误动作数 / 总动作数 | ≤ v5.3.0（专项见第三章） |
| 耗时 | engineMs p50/p95 | p95 ≤ 44ms（v5.3.0 基线） |
| 功耗 | 探针/扫描次数 → µWh 估算（0.05/0.72 µWh 模型） | 稳态 ≤ 200 µWh/开机 |
| 管线归因 | 各级命中分布（P-2/P0/P0.7/P0.8/P-1/五层/兜底） | 回归不劣化 |

百款清单分层：Top30 高频（微信/抖音/淘宝/拼多多/喜马拉雅…）全量语料 +
Top31~100 抽样语料（每季轮换 20%），守护名单应用（银行/政务/支付）独立
分组——守护组断言「服务零干预 + 广告照拦」双指标。

---

## 三、误触率专项测试方案

### 3.0 v5.5.0 专项落地（JVM 断言已入库）

第三章的 12 场景清单中，以下已转化为可回归的 JVM 断言（任一回归即 CI FAIL）：

| 场景 | 落地断言（套件·断言名） |
| --- | --- |
| 8 更新弹窗只点消极 | RuleGovernorTest「单信号以后再说不命中」「双信号后否定侧命中」 |
| 9 权限/系统弹窗零动作 | ComplianceGuardTest「系统设置页零扫描」「锁屏零扫描」「PIN 码页零扫描」 |
| 3 支付页零扫描 | ComplianceGuardTest 动作边界「密码页跳过键也拒绝」（场景门） |
| 4 输入场景零扫描 | ComplianceGuardTest「Gboard/搜狗输入法零扫描」 |
| 2 半屏面板形态门 | RuleGovernorTest「普通窗 close 不命中（SDK 指纹门不变）」 |
| 12 分屏窗口换算 | ComplianceGuardTest「宽屏归一不严重低估（分屏保护）」 |
| 启停红线（不跳系统设置） | Java 守卫 registrationPresent→still_registered（MainActivity.openA11yDetail）+ JS 侧 btn-enable 四路径状态校验（app.js）；构建验证：APK 打包 + app.js 语法检查 |

### 3.1 正常使用场景清单（零误触验证）

| # | 场景 | 语料构造要点 | 断言 |
| --- | --- | --- | --- |
| 1 | 侧边菜单展开 | 抽屉控件（含「跳过」文案的菜单项） | 全程零动作 |
| 2 | 底部抽屉/半屏面板 | 高度 45% 底部锚定（形似半屏插屏）+ 无广告角标 | 零动作（形态门+双信号拒绝） |
| 3 | 支付页面 | 收银台/支付密码/键盘 | 零扫描（敏感场景） |
| 4 | 输入场景 | 聊天输入框/搜索框聚焦 + IME 窗 | 零扫描（输入法/密码） |
| 5 | 小程序 | 微信小程序开屏与内页切换 | 仅真广告窗动作 |
| 6 | 聊天流 | 流式内容（豆包式连续 CONTENT_CHANGED 5 分钟） | 突发窗 ≤ 预算（10 分钟 2 窗） |
| 7 | 列表浏览 | 长列表滚动（信息流含「广告」角标 Item） | 仅标注（mark_only），零滑动零点击 |
| 8 | 更新弹窗 | 积极按钮大而醒目、消极按钮小 | 只点消极（decline_tap），积极零触碰 |
| 9 | 权限弹窗 | 系统授权 Dialog | 零扫描 + 零动作 |
| 10 | 入口窗返回 | 冷启动开屏窗内按返回手势 | 返回键兜底禁用（入口窗安全门） |
| 11 | 折叠屏折叠/展开 | 展开态边缘按钮（角落区内正常控件） | 形状门+面积阀拒绝 |
| 12 | 分屏操作 | 半屏高度正常应用 | 按实际窗口换算，零误判 |

### 3.2 回归红线

- **误触率必须 ≤ v5.3.0 基线**（v5.3.0 误触率 = 0，即上述 12 场景全量
  零误触是硬门禁：任何一场景出现动作即 CI FAIL，禁止以「提升命中率」
  为由豁免）；
- 命中率不得低于 v5.3.0（100%）；引擎耗时 p95 不得高于 44ms；稳态功耗
  不得高于 200 µWh/开机；
- 四维任何一维劣化都需要在 PR 描述中给出权衡说明并人工批准，CI 报告
  四维全绿方可合并（对应 OPTIMIZATION-v5.4.0.md「四平衡声明」）。

### 3.3 真机抽检流程（每版本发布前）

1. 三档真机各一台（入门 Redmi / 中端 / 旗舰），连续使用 48 小时；
2. 结构化日志回传（脱敏口径 §3.3），对照离线跑分四表核对偏差；
3. 误触即 P0：冻结发布、语料入库（新增 #N 场景）、回归补录断言。
