# Issue 引擎日志解析统计（issue-parse）

- 生成时间：2026-11-27T14:23:06.615Z
- 参数：--state open · --since-days 30 · 数据源 xiaobaiba999/Skip-Ad issues API
- 样本：112 个 issue（其中引擎日志报告 111 · 非报告/跳过 1 · 解析失败 0 · 部分解析 0）
- 记录总数：2470 条（含成功/未命中/失败/观察/信息；v8 系双表重复列示已按事件键去重，见第 7 节）

## 1. 失败类别分布（13 类）

计数口径：**评估单元** = v8 gkd 引擎未命中汇总记录按其 statuses 逐状态加权（如 not_matched×77 → 77 单元）+ 原生引擎未命中/动作失败按记录计 1 单元。不可区分的类别显式标注，不参与编造。

| # | 类别 key | 含义 | 评估单元 | 占比 | 数据可区分性 |
|---|---|---|---|---|---|
| 1 | no_matched_rule | 订阅/规则未命中 | 4194 | 83.3% | 现有日志可区分 |
| 2 | rule_disabled | 规则被用户禁用 | 0 | 0.0% | 数据不可区分（置 null） |
| 3 | app_not_in_subscription | 应用不在任何订阅 | 28 | 0.6% | 现有日志可区分 |
| 4 | subscription_incompatible | 订阅格式/版本不兼容 | 0 | 0.0% | 数据不可区分（置 null） |
| 5 | activity_mismatch | 规则存在但 Activity 不匹配 | 0 | 0.0% | 现有日志可区分 |
| 6 | node_not_found | 节点不可达 | 296 | 5.9% | 现有日志可区分 |
| 7 | node_not_visible | 节点不可见/离屏 | 0 | 0.0% | 数据不可区分（置 null） |
| 8 | node_stale | 节点过期/前台冲突 | 100 | 2.0% | 现有日志可区分 |
| 9 | countdown_pending | 倒计时/延迟等待中 | 84 | 1.7% | 现有日志可区分 |
| 10 | action_failed | 动作执行失败 | 0 | 0.0% | 现有日志可区分 |
| 11 | action_no_effect | 动作执行但无效果 | 0 | 0.0% | 数据不可区分（置 null） |
| 12 | rate_limited | 限流/冷却 | 331 | 6.6% | 现有日志可区分 |
| 13 | other_exception | 其他异常 | 0 | 0.0% | 现有日志可区分 |
| — | 合计 | — | **5033** | 100% | — |

### 1.1 原始状态分布（未映射 13 类前）

| 原始状态 | 单元数 | 映射类别 |
|---|---|---|
| MatchTimeout | 3622 | 未映射（见数据质量说明） |
| not_matched | 3507 | #1 no_matched_rule |
| ActionLimitReached | 323 | #12 rate_limited |
| node_unavailable | 256 | #6 node_not_found |
| query_child_unavailable | 100 | #8 node_stale |
| MatchDelay | 84 | #9 countdown_pending |
| query_root_unavailable | 40 | #6 node_not_found |
| Cooldown | 8 | #12 rate_limited |

## 2. 未命中 App 排行 Top 20

排序：未命中评估轮数（v8 gkd 每条未命中汇总含 N 轮评估）优先，其次未命中记录数。v8 gkd 记录为完整包名；v7 原生引擎记录为进程短名（两种口径分行列示，不做合并）。

| # | App 标识 | 口径 | 未命中记录数 | 未命中评估轮数 | 涉及 issue | 涉及设备 |
|---|---|---|---|---|---|---|
| 1 | com.taobao.taobao | 完整包名 | 18 | 2869 | 1（#167） | Redmi 25102RKBEC |
| 2 | com.mci.smagazine | 完整包名 | 22 | 1369 | 1（#168） | samsung SM-S9180 |
| 3 | com.netease.cloudmusic | 完整包名 | 18 | 1305 | 1（#168） | samsung SM-S9180 |
| 4 | com.larus.nova | 完整包名 | 6 | 1125 | 1（#167） | Redmi 25102RKBEC |
| 5 | com.xiaomi.shop | 完整包名 | 6 | 734 | 1（#167） | Redmi 25102RKBEC |
| 6 | com.luna.music | 完整包名 | 4 | 373 | 1（#169） | Redmi 25060RK16C |
| 7 | com.ximalaya.ting.android | 完整包名 | 3 | 165 | 1（#169） | Redmi 25060RK16C |
| 8 | aweme | 进程短名 | 56 | 56 | 13（#65 #69 #73 #74 #104 #105 …） | Xiaomi 2211133C / vivo V2458A / OPPO PJT110 / OPPO PLT140 … |
| 9 | read | 进程短名 | 48 | 48 | 4（#99 #111 #114 #137） | samsung SM-S9180 / Sony XQ-CT72 / vivo V2227A |
| 10 | mm | 进程短名 | 45 | 45 | 17（#73 #76 #77 #78 #79 #80 …） | OPPO PJT110 / vivo V2458A / Xiaomi 2211133C / samsung SM-S9180 … |
| 11 | wireless | 进程短名 | 39 | 39 | 5（#71 #106 #122 #134 #160） | OPPO PJT110 / Redmi 25060RK16C |
| 12 | bili | 进程短名 | 37 | 37 | 13（#67 #68 #73 #83 #91 #95 …） | OPPO PLT140 / vivo V2227A / Xiaomi 2410DPN6CC / vivo V1921A |
| 13 | ting.android | 进程短名 | 35 | 35 | 14（#62 #66 #70 #71 #78 #81 …） | Xiaomi 2410DPN6CC / Redmi 25060RK16C |
| 14 | mobileqq | 进程短名 | 28 | 28 | 10（#62 #73 #74 #79 #80 #84 …） | OPPO PLT140 / Xiaomi 2410DPN6CC / Redmi 25060RK16C |
| 15 | market | 进程短名 | 26 | 26 | 14（#78 #79 #80 #82 #90 #93 …） | Xiaomi 21081111RG / OPPO PLT140 / samsung SM-S9180 / Xiaomi 2410DPN6CC … |
| 16 | sentinels | 进程短名 | 26 | 26 | 1（#138） | OPPO PJT110 |
| 17 | smagazine | 进程短名 | 20 | 20 | 3（#127 #129 #146） | samsung SM-S9180 |
| 18 | weibo | 进程短名 | 19 | 19 | 9（#64 #67 #68 #97 #98 #100 …） | Xiaomi 2211133C / Xiaomi 2410DPN6CC |
| 19 | netdisk | 进程短名 | 19 | 19 | 1（#118） | vivo V2227A |
| 20 | csdnplus | 进程短名 | 19 | 19 | 9（#76 #77 #78 #82 #83 #97 …） | Xiaomi 2410DPN6CC |

## 3. 动作失败清单（result:false / · 失败）

**当前样本内无动作失败记录**（0 条 result:false / ❌ 记录）。v8.0.7/v8.0.9 各报告 §5 统计摘要亦均为「动作失败 0」。

## 4. 版本分布与设备/系统交叉

未命中率口径 = 未命中记录数 ÷（未命中记录数 + 出手成功记录数）。出手成功 = ⚡ 执行动作 result:true / 「App」触发「组」规则 / 自动通道触发行 / 原生 ⚡ 已点击跳过 / ✓ 复验通过 / v5 事件流 instant_skip 等。未命中记录含 v8 gkd 未命中汇总与原生开屏/插屏未命中（不含 🧭 应用暂无规则）。

| 版本族 | issue 数 | 记录数 | 出手成功 | 未命中记录 | 未命中率(记录口径) | 未命中评估轮 | 🧭应用暂无规则 |
|---|---|---|---|---|---|---|---|
| v8.0.9 | 3 | 97 | 20 | 77 | 79.4% | 7940 | 28 |
| v8.0.8 | 1 | 0 | 0 | 0 | 0.0% | 0 | 0 |
| v8.0.7 | 18 | 99 | 99 | 0 | 0.0% | 0 | 0 |
| v7.15.x | 56 | 572 | 37 | 535 | 93.5% | 0 | 0 |
| v7.9.x | 6 | 38 | 4 | 34 | 89.5% | 0 | 0 |
| v7.8.x | 2 | 7 | 3 | 4 | 57.1% | 0 | 0 |
| v7.5.x | 1 | 9 | 1 | 8 | 88.9% | 0 | 0 |
| v7.4.x | 1 | 7 | 3 | 4 | 57.1% | 0 | 0 |
| v7.3.x | 1 | 11 | 0 | 11 | 100.0% | 0 | 0 |
| v7.2.x | 2 | 19 | 2 | 17 | 89.5% | 0 | 0 |
| v7.1.x | 1 | 12 | 5 | 7 | 58.3% | 0 | 0 |
| v7.0.x | 2 | 8 | 0 | 8 | 100.0% | 0 | 0 |
| v6.1.x | 1 | 0 | 0 | 0 | 0.0% | 0 | 0 |
| v6.0.x | 1 | 5 | 1 | 4 | 80.0% | 0 | 0 |
| v5.9.x | 1 | 8 | 2 | 6 | 75.0% | 0 | 0 |
| v5.8.x | 5 | 38 | 16 | 22 | 57.9% | 0 | 0 |
| v5.7.x | 2 | 16 | 10 | 6 | 37.5% | 0 | 0 |
| v5.6.x | 1 | 6 | 1 | 5 | 83.3% | 0 | 0 |
| v5.5.x | 2 | 23 | 14 | 9 | 39.1% | 0 | 0 |
| v5.3.x | 3 | 26 | 20 | 6 | 23.1% | 0 | 0 |
| v5.2.x | 1 | 1 | 0 | 1 | 100.0% | 0 | 0 |

注：v8.0.7 两类报告格式（手动 GKD 内核 / 自动通道）均**不含未命中事件记录**，其「0 未命中」为格式所限，不可解读为无失败；v8.0.8 仅 E2E 存档无有效记录。未命中率仅 v8.0.9（六节格式）与 v7.x/v5.x（原生格式）之间可比较：gkd 内核 v8.0.9 未命中率 79.4% 主要来自「评估轮」级重试窗口（每条未命中汇总含最多数百轮评估），与原生引擎记录级口径不同源。

### 4.1 设备 × Android 版本交叉分布（issue 数；型号已去除机型代号括注）

| 设备型号 | Android | issue 数 |
|---|---|---|
| Xiaomi 2410DPN6CC | Android 17 | 34 |
| Redmi 25060RK16C | Android 16 | 13 |
| Xiaomi 2211133C | Android 16 | 9 |
| samsung SM-S9180 | Android 16 | 7 |
| OPPO PJT110 | Android 16 | 7 |
| OPPO PLT140 | Android 16 | 6 |
| Xiaomi 21081111RG | Android 14 | 3 |
| OnePlus OnePlus8Pro | Android 11 | 3 |
| vivo V2227A | Android 16 | 3 |
| vivo V2502A | Android 17 | 2 |
| Sony XQ-CT72 | Android 14 | 2 |
| HONOR MTN-AN00 | Android 16 | 2 |
| vivo V2458A | Android 17 | 2 |
| samsung SM-S942B | Android 16 | 2 |
| Redmi 25102RKBEC | Android 17 | 1 |
| Redmi 24074RPD2C | Android 16 | 1 |
| Redmi 24117RK2CC | Android 16 | 1 |
| Redmi 24117RK2CC | Android 17 | 1 |
| asus ASUS_AI2401_D | Android 16 | 1 |
| Redmi 23013RK75C | Android 15 | 1 |
| vivo V2419A | Android 17 | 1 |
| OnePlus PJZ110 | Android 17 | 1 |
| vivo V1921A | Android 10 | 1 |
| OPPO PLB110 | Android 17 | 1 |
| Xiaomi MiTV-ASSU1 | Android 14 | 1 |

## 5. 成功战绩对照（有成功记录的 App）

用于区分「订阅没覆盖」与「规则失效」：出现在本表 = 订阅/引擎在该 App 上有成功出手；仅出现在第 2 节 = 疑似订阅未覆盖或规则失效。

| # | App 标识 | 口径 | 成功出手记录数 | 涉及 issue |
|---|---|---|---|---|
| 1 | com.ximalaya.ting.android | 完整包名 | 21 | 4（#151 #152 #164 #169） |
| 2 | ting.android | App名/进程短名 | 19 | 13（#62 #64 #70 #71 #78 #81 …） |
| 3 | keep | App名/进程短名 | 12 | 9（#62 #78 #82 #97 #98 #101 …） |
| 4 | cn.com.yunma.school.app | 完整包名 | 11 | 4（#151 #152 #164 #169） |
| 5 | 喜马拉雅 | App名/进程短名 | 11 | 4（#59 #60 #63 #147） |
| 6 | weibo | App名/进程短名 | 10 | 6（#67 #68 #73 #78 #100 #135） |
| 7 | com.github.android | 完整包名 | 8 | 1（#151） |
| 8 | 极选商城 | App名/进程短名 | 8 | 3（#59 #60 #63） |
| 9 | GitHub | App名/进程短名 | 7 | 1（#147） |
| 10 | bili | App名/进程短名 | 7 | 4（#67 #68 #79 #133） |
| 11 | com.luna.music | 完整包名 | 6 | 2（#152 #169） |
| 12 | com.google.android.apps.authenticator2 | 完整包名 | 6 | 1（#164） |
| 13 | com.shark.jizhang | 完整包名 | 6 | 2（#151 #164） |
| 14 | wifilocating | App名/进程短名 | 5 | 2（#86 #95） |
| 15 | com.chaoxing.mobile | 完整包名 | 4 | 2（#152 #169） |
| 16 | net.csdn.csdnplus | 完整包名 | 4 | 2（#151 #164） |
| 17 | 易校园 | App名/进程短名 | 4 | 1（#147） |
| 18 | aweme | App名/进程短名 | 4 | 3（#105 #112 #126） |
| 19 | market | App名/进程短名 | 4 | 4（#82 #97 #98 #107） |
| 20 | edifierconnect | App名/进程短名 | 4 | 1（#72） |
| 21 | wireless | App名/进程短名 | 4 | 1（#71） |
| 22 | 邻里团购 | App名/进程短名 | 4 | 2（#60 #63） |
| 23 | com.sina.weibo | 完整包名 | 3 | 2（#165 #169） |
| 24 | com.tencent.qqlive | 完整包名 | 3 | 1（#165） |
| 25 | tv.danmaku.bili | 完整包名 | 3 | 2（#151 #164） |
| 26 | com.gotokeep.keep | 完整包名 | 3 | 1（#164） |
| 27 | android.lite | App名/进程短名 | 3 | 1（#72） |
| 28 | 优品严选 | App名/进程短名 | 3 | 1（#63） |
| 29 | 政务通 | App名/进程短名 | 3 | 3（#59 #60 #63） |
| 30 | 星际远征 | App名/进程短名 | 3 | 3（#59 #60 #63） |
| … | （其余 33 个 App，见 records.jsonl） | | | |

报告头战绩补充（进程本次启动累计，口径见各报告 §6）：#169 拦截累计 12 · #168 拦截累计 2 · #167 拦截累计 2

## 6. gkd 订阅通道专属统计（v8.0.7+ gkd 内核）

- gkd 内核 issue 数：22（v8.0.7 / v8.0.8 / v8.0.9 系列）
- gkd 内核记录数：267（触发 119 · 未命中汇总 77 · 应用暂无规则 28）

### 6.1 gkd 通道 App 出手统计（触发 vs 未命中，按 App）

| # | App 标识 | gkd 触发(成功出手) | 未命中汇总记录 | 未命中评估轮 | 🧭应用暂无规则 | 涉及 issue |
|---|---|---|---|---|---|---|
| 1 | com.taobao.taobao | 1 | 18 | 2869 | 0 | 1 |
| 2 | com.mci.smagazine | 2 | 22 | 1369 | 0 | 1 |
| 3 | com.netease.cloudmusic | 1 | 18 | 1305 | 0 | 1 |
| 4 | com.larus.nova | 1 | 6 | 1125 | 0 | 1 |
| 5 | com.xiaomi.shop | 0 | 6 | 734 | 0 | 1 |
| 6 | com.luna.music | 6 | 4 | 373 | 0 | 2 |
| 7 | com.ximalaya.ting.android | 21 | 3 | 165 | 0 | 4 |
| 8 | cn.com.yunma.school.app | 11 | 0 | 0 | 0 | 4 |
| 9 | com.github.android | 8 | 0 | 0 | 0 | 1 |
| 10 | GitHub | 7 | 0 | 0 | 0 | 1 |
| 11 | com.google.android.apps.authenticator2 | 6 | 0 | 0 | 0 | 1 |
| 12 | com.shark.jizhang | 6 | 0 | 0 | 0 | 2 |
| 13 | com.chaoxing.mobile | 4 | 0 | 0 | 0 | 2 |
| 14 | net.csdn.csdnplus | 4 | 0 | 0 | 0 | 2 |
| 15 | 易校园 | 4 | 0 | 0 | 0 | 1 |
| 16 | 喜马拉雅 | 4 | 0 | 0 | 0 | 1 |
| 17 | com.sina.weibo | 3 | 0 | 0 | 0 | 2 |
| 18 | com.tencent.qqlive | 3 | 0 | 0 | 0 | 1 |
| 19 | tv.danmaku.bili | 3 | 0 | 0 | 0 | 2 |
| 20 | com.gotokeep.keep | 3 | 0 | 0 | 0 | 1 |
| 21 | com.baidu.BaiduMap | 2 | 0 | 0 | 0 | 1 |
| 22 | com.waimaiii.waimaiii | 2 | 0 | 0 | 0 | 2 |
| 23 | com.zhihu.android | 2 | 0 | 0 | 0 | 2 |
| 24 | com.greenpoint.android.mc10086.activity | 2 | 0 | 0 | 0 | 1 |
| 25 | 中国移动 | 2 | 0 | 0 | 0 | 1 |
| … | （其余 23 个 App） | | | | | |

### 6.2 未命中记录的订阅来源分布（组前缀）

| 订阅来源（组前缀） | 未命中汇总记录数（一条记录可含多订阅组，跨订阅重复计数） |
|---|---|
| 订阅233 | 77 |
| 订阅86 | 73 |

注：动作触发记录（⚡ 执行动作 / 「App」触发「组」规则）的组名不带订阅前缀，故订阅级成功归因不可区分（数据所限，见数据质量说明）。

## 7. 数据质量说明

- 完整解析：111 / 部分解析（存在无法归类的日志行）：0 / 解析失败（格式未识别）：0 / 跳过（非日志报告）：1
  - 跳过清单：#61（非引擎日志报告（用户反馈/问题描述类，无六节日志结构））
- E2E 测试 issue（标题含 E2E-TEST，保留在样本中但已标记）：#163 · #139
- 带时间戳日志行归类率：2569/2569（已识别但无事件语义的服务级信息行 62 条（「服务已启动/已学习…」）与观察类图标行 0 条（🔔/📨/▶）不参与失败/成功统计）
- 13 类未覆盖的原始状态：MatchTimeout×3622 —— 合计 3622 评估单元未计入 13 类分布（MatchTimeout = 匹配总超时，任务 13 类清单未收录，保留原始计数待产品定夺）
- 不可区分类别（#2 规则禁用 / #4 订阅不兼容 / #7 节点不可见 / #11 动作无效果）：现有日志无对应字段，全部置 null，未做任何估算。
- v8 自动上传通道（标题「自动日志 …」）的触发行（`pkg group=N rule=N`）无显式 result 字段，按「触发即出手」计成功，已在记录级 note 标注。
- v7 原生引擎日志只有时分秒，日期按 issue 创建时刻（东八区）近似；v8 六节记录含 MM-dd，年份按 issue 创建年份推断。
- 原生引擎未命中（❓ 开屏未命中 / 🔍 插屏未命中）无状态分布，按记录级 1 单元计入 #1；其 App 标识为进程短名（如 ting.android），与 v8 完整包名不合并。
- v8 系报告存在双表重复列示，已按事件键去重：v8.0.9 六节格式 §2⊂§3 同文案重复（ts+消息前缀去重）；v8.0.7 手动格式「引擎日志（『App』触发）」与「详细引擎日志（⚡ 执行动作）」跨节不同文案重复列示同一事件（#147 实证，按 ts+App+组 去重）。其余格式为单一时间戳列表来源，同一秒内重复行按真实重复事件保留（如自动通道 4 个 App 存在同秒同规则双触发）。
