# OPTIMIZATION v7.6.0 · 激进模式（开屏露头即点 + 插屏「广告」语境闸重做）

> 用户十五轮实锤原话：「软件内插入的广告不仅没跳过，其他软件的开屏广告现在有的还跳不了了，或者非常非常慢，你可以激进一点暂时先放弃宁可漏拦绝不误触。扫描日志上传未命中问题，app内穿插的广告全都没拦截，关于这一块的实现可以参考李跳跳的实现逻辑……优化内置规则库和订阅市场规则的使用顺序和命中问题，优化底层算法，部分用户设备开屏广告跳过时因为设备类型、分辨率或者其他原因影响，导致多点，误点的情况，体验非常不好，构建真是模拟环境，1比1模拟人在不同设备上操作使用卫士，解决这个bug。功能实习完成后进入测试环境全方位测试，再根据测试修改不足，不要每次为了测试而测试，不要做没有意义的测试。完成过后同步giuhub和推送releases。优化github面板描述」

## 一、死因分析（真机实锤归因）

### 死因① 开屏变慢（v7.5.0 门控从第 0 击起全次生效）
- v7.5.0 为治「穿山甲具倒计时未到点了白点」把门控扩到第 0 击→ 所有带倒计时的开屏全等倒计时走完才出手（5s 广告白看 4.9s）—— 用户实锤「非常非常慢」的直接死因
- windowCountdownHint 误检数字（「领取3天」类非倒计时数字近旁）把无倒计时按钮也按住不动 → 用户实锤「有的还跳不了了」的死因

### 死因② 插屏闸①过严（isDisclosureBadge 精确等值三闸）
- 真机插屏角标实际形态千姿百态（「广告 · 了解详情」「查看广告详情」「广告×」）—— 精确等值·符号尾·形态三闸全拒→「软件内插入的广告不仅没跳过」持续存在

### 死因③ 终闸杀词（execute 通用语义面一票否决）
- INTER_CLOSE_WORDS 收了「知道了/我知道了」，但 execute() 走 ComplianceGuard.boundaryCheck —— 「知道了」不含跳过/关闭/skip/close/× → BND_FAIL_SEMANTIC（词表收了也白收）；「残忍离开」这类游戏插屏标配关闭词更是根本不在词表

### 死因④ 带位闸 + 滚动窗一票否决
- 插屏 × 常在中部/右下—— 带位闸（顶 30%/底 70%）全灭；名单外信息流卡片广告在可滚动窗里被闸⑤一票否决

## 二、修复与重做（激进模式）

| # | 修复 | 实现 | 文件 |
|---|---|---|---|
| 1 | 首击零门控 | gateCountdownAggressive（attempts=0 → 立即出手）+ 重击等将尽 + cappedRetryCd 6s 封顶 | AdDecisionCore / AdSkipService.countdownWait |
| 2 | 语境在场闸 contains | hasAdContextWord（广告/推广/赞助 contains · ≤10 字——变体一网打尽，超长正文不算节点） | InterstitialGuard |
| 3 | 终闸语义面收编 | ComplianceGuard.interBoundaryCheck（场景+语义 isInterstitialCloseWord+形态双验 closeFormOk/面积）；execute() inter_close 路由分派 | ComplianceGuard / AdSkipService.execute |
| 4 | 关闭词扩表 | 残忍离开/残忍退出/残忍拒绝/不再看/不看了/暂不需要/不需要/Skip Ad；「取消」保持拒收 | InterstitialGuard.INTER_CLOSE_WORDS |
| 5 | 位置闸激进放宽 | 非滚动窗零位置闸；滚动窗近旁铁证（|Δcy|≤35% 与语境节点同排） | InterstitialGuard.decideFromCandidates |
| 6 | 新窗首扫提速 | interWinAt 代陀——窗切换即重置包级节流（插层当帧就扫） | AdSkipService.maybeInterstitialScan |
| 7 | 扫描日志双样本 | logInterMiss 语境+关闭双样本（被闸也照登） | AdSkipService.logInterMiss |

## 三、多点误点 1:1 仿真（设备差异根治）

激进时序结构 = 多点防线本身：
- 首击唯一立即（露头即点，不再白点循环重试）
- 重击等将尽（天然 >1.6s 间隔）→ F 同目标静默窗（SAME_TARGET_QUIET_MS=1600 人手双击下限）天然满足
- MAX_STRIKES_PER_TARGET=2（同指纹每窗≤2 击）/ MAX_CLICKS_PER_WIN=3（耐心预算）/ H 结果静默 600ms / 冷却 max(300ms, verifyDelayMs) 全保持
- 触点钳制：TapPoint L1~L4（可见交集/窗口钳制/象限轮换/边缘内缩/覆盖层 Z 序）· dp 密度归一（clampedDensity [1,4]）· 九品牌×5 槽位×密度矩阵全断言（AggressiveModeTest S4）

## 四、规则顺序/底层算法保持

- RuleRouter 市场优先→内置兜底两源使用顺序不变（v7.1.0 已治理）
- 探针前置（nativeTextProbe）→ 五层规则 DFS → 订阅两源轮换不变；新增 skip 探针与插屏语境/关闭双直查（findAccessibilityNodeInfosByText——李跳跳方法论主路径）
- 关闭方向语义全套防线保持（负面价/验证码/支付/小程序/聊天主界面/零扫描场景全拒）

## 五、测试（全方位·结果驱动·不做无意义测试）

| 套件 | 项数 | 对位 |
|---|---|---|
| AggressiveModeTest（新） | 42 | 首击零门控时间线/重击 6s 封顶/1:1 多点时间线/九品牌触点钳制/决策梯子不变式 |
| InterstitialGuardTest（重做） | 93 | 关闭词 33/优先级/语境 contains/滚动窗近旁/决策真值表/interBoundaryCheck（含死因②复现） |
| SplashCountdownTest（升级） | 61 | 激进门控反转新断言（首击零门控/6s 封顶） |
| 其余 17 套件 | 4118 | 零改动保持（含九品牌九维矩阵 308 机型） |
| **合计 20 套件** | **4314/4314 全绿** | **+61（全部对位本轮死因）** |

## 六、发布

- APK 849,462 B（832KB）· versionCode 76000（>75000 覆盖即升级）· minSdk 24 / targetSdk 34
- MD5 `aec9fea95de3790e99f16dfc4ab841ea` · SHA-256 `d4e66b0e693dcf6083123feeaf7c03678b641b4d6d246eb28d80abd9c64374c6`
- GitHub tag v7.6.0 + Release（1 标题+1 段落 + 5 条带序号功能 + 校验表）+ 仓库描述同步 + README 面板同步
