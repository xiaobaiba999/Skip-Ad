# 下一轮规则迭代建议（RULE-ITERATION-2026-11-27）

- 生成时间：2026-11-27 14:33 UTC（Task P2-stats 交付 C）
- 数据基础：`reports/issue-parse/{issues,records}.jsonl`（112 个 open issue · 2470 条解析记录 · 解析器 `scripts/parse-feedback-reports.mjs`）
- 订阅对照：三套内置订阅 86（aoguai v89·72 apps） / 233（ganlinte v72·610 apps） / 667（Lin-arm v646 完整版·1013 apps；本地 667.json5 为截断损坏件，按上游 v646 对照）
- 诚实声明：短名→包名合并仅采用「订阅内唯一后缀匹配」（如 `ting.android`→`com.ximalaya.ting.android`，合并前逐一断言校验），多候选歧义短名不合并、只列候选与语境线索；所有建议动作基于以上可核验数据，未做无据推断。

## 0. 结论速览

1. **v8.0.9 gkd 内核（86+233 内嵌订阅）的开屏覆盖缺口是当前最大失败源**：失败 Top 7 全部来自 v8.0.9 三台设备的 gkd 未命中（合计 9,014 评估轮），其中 **com.mci.smagazine（三星阅读）/ com.larus.nova（豆包）/ com.xiaomi.shop（小米商城）/ com.luna.music（汽水音乐）在 86+233 内均无 App 级开屏组**——只剩全局开屏组兜底，匹配不到这四款 App 的实际开屏布局。
2. **667（Lin-arm v646）已覆盖部分缺口**：三星阅读/汽水音乐有 App 级开屏组；豆包有 2 组但无开屏组；小米商城 4 组无开屏组 → 最优先动作是把内置 667 修复为完整版（本地 667.json5 为 932KiB 截断损坏件，应替换为 v646 完整版 1.37MB/1013 apps）。
3. **淘宝开屏规则疑似过期**：233/667 均有 App 级「开屏广告」组且引擎确实评估了（`组[订阅233·开屏广告 …]`），2,869 评估轮未命中（MatchTimeout×1737 + not_matched×891），仅 1 次成功 → 需真实快照确认新版淘宝开屏选择器。
4. **喜马拉雅不是规则缺失而是节点时序问题**：21 次成功 vs 165 单元未命中，未命中主因 query_child_unavailable×51（子节点查询不可用）→ 引擎侧问题，不建议补规则。
5. **v7.x 原生引擎历史海量未命中（抖音/微信/B站等 500+ 条）将由 gkd 内核订阅组天然接管**（233/667 均有这些 App 的 App 级开屏组）→ 无需新增规则，等 v8 全量迁移即可。

## 1. 失败最多 Top 20 App 订阅覆盖核查

失败评估单元口径：v8.0.9 gkd 未命中按 statuses 轮数加权 + v7.x 原生未命中按记录计 1；唯一后缀匹配的短名已并入主包名（`※含短名` 标注）。「全局组」= 三套订阅均带全局开屏组（86·开屏广告 / 233·开屏广告-全局 / 667·开屏广告-全局），对所有 App 生效，故「无 App 级开屏组」仍会被全局组评估——失败即意味着全局组选择器也没匹配上。

| # | App（包名/标识） | 失败单元 | 未命中记录 | 成功出手 | 主导失败状态 | 86 | 233 | 667v646 | 建议动作 |
|---|---|---|---|---|---|---|---|---|---|
| 1 | com.taobao.taobao（※含短名 taobao） | 2875 | 24 | 1 | MatchTimeout×1737 · not_matched×891 · ActionLimitReached×159 | ✓ 8组 | ✓ 14组（开屏×1） | ✓ 18组（开屏×1） | 内嵌订阅已有 App 级开屏组仍未命中 → 选择器疑似过期，需真实快照更新 |
| 2 | com.mci.smagazine（※含短名 smagazine） | 1389 | 42 | 2 | MatchTimeout×719 · not_matched×571 · node_unavailable×56 | ✗ 无 | ✗ 无 | ✓ 1组（开屏×1） | 86+233 无 App 级开屏组，667v646 有 → 优先引入 667 完整版 |
| 3 | com.netease.cloudmusic | 1305 | 18 | 1 | not_matched×953 · MatchTimeout×139 · node_unavailable×129 | ✓ 5组 | ✓ 11组 | ✓ 11组（开屏×1） | 86+233 无 App 级开屏组，667v646 有 → 优先引入 667 完整版 |
| 4 | com.larus.nova（※含短名 nova） | 1126 | 7 | 1 | MatchTimeout×667 · not_matched×278 · ActionLimitReached×164 | ✗ 无 | ✗ 无 | ✓ 2组 | 三套订阅均无 App 级开屏组 → 需快照自建 |
| 5 | com.xiaomi.shop | 734 | 6 | 0 | MatchTimeout×357 · not_matched×347 · node_unavailable×23 | ✗ 无 | ✓ 1组 | ✓ 4组 | 三套订阅均无 App 级开屏组 → 需快照自建 |
| 6 | com.luna.music | 373 | 4 | 6 | not_matched×371 · node_unavailable×2 | ✗ 无 | ✓ 2组 | ✓ 11组（开屏×1） | 86+233 无 App 级开屏组，667v646 有 → 优先引入 667 完整版 |
| 7 | com.ximalaya.ting.android（※含短名 ting.android） | 200 | 38 | 21 | not_matched×96 · query_child_unavailable×51 · node_unavailable×7 | ✗ 无 | ✓ 13组 | ✓ 7组 | 全局开屏组已生效（21 次 gkd 成功），未命中主因节点时序 → 引擎侧问题，不建议补规则 |
| 8 | com.ss.android.ugc.aweme（※含短名 aweme） | 56 | 56 | 0 | —（原生记录级） | ✗ 无 | ✓ 14组 | ✓ 31组（开屏×1） | 原生引擎历史未命中；订阅已有开屏组 → 等 v8 gkd 内核迁移接管，无需新增 |
| 9 | read（短名）（歧义） | 48 | 48 | 2 | —（原生记录级） | ✗ 无候选 | ✓ com.dragon.read、com.phoenix.read | ✓ com.dragon.read（开屏）、com.phoenix.read | 歧义短名：按语境定包后核覆盖（候选见下） |
| 10 | mm（短名）（歧义） | 45 | 45 | 0 | —（原生记录级） | ✓ com.tencent.mm | ✓ com.jc.avatar.mm、com.m17qcc.mm、com.tencent.mm（开屏） | ✓ com.jc.avatar.mm、com.m17qcc.mm、com.tencent.mm（开屏） | 歧义短名：按语境定包后核覆盖（候选见下） |
| 11 | wireless（短名）（歧义） | 39 | 39 | 4 | —（原生记录级） | ✓ com.cainiao.wireless | ✓ com.alibaba.wireless、com.cainiao.wireless（开屏）、com.tmall.wireless | ✓ com.alibaba.wireless、com.cainiao.wireless、com.tmall.wireless | 歧义短名：按语境定包后核覆盖（候选见下） |
| 12 | tv.danmaku.bili（※含短名 bili） | 37 | 37 | 3 | —（原生记录级） | ✓ 13组 | ✓ 12组（开屏×1） | ✓ 34组（开屏×1） | 原生引擎历史未命中；订阅已有开屏组 → 等 v8 gkd 内核迁移接管，无需新增 |
| 13 | com.tencent.mobileqq（※含短名 mobileqq） | 28 | 28 | 0 | —（原生记录级） | ✓ 18组（开屏×2） | ✓ 20组（开屏×2） | ✓ 22组（开屏×2） | 原生引擎历史未命中；订阅已有开屏组 → 等 v8 gkd 内核迁移接管，无需新增 |
| 14 | market（短名）（歧义） | 26 | 26 | 4 | —（原生记录级） | ✓ com.coolapk.market、com.xiaomi.market | ✓ com.byfen.market（开屏）、com.coolapk.market（开屏）、com.heytap.market、com.xiaomi.market、com.yingyonghui.market | ✓ com.aiwu.market、com.byfen.market、com.coolapk.market（开屏）、com.heytap.market（开屏）、com.lion.market、com.xiaomi.market、com.yingyonghui.market（开屏）、zte.com.market | 歧义短名：按语境定包后核覆盖（候选见下） |
| 15 | sentinels（短名）（歧义） | 26 | 26 | 0 | —（原生记录级） | ✗ 无候选 | ✗ 无候选 | ✗ 无候选 | 歧义短名：按语境定包后核覆盖（候选见下） |
| 16 | com.sina.weibo（※含短名 weibo） | 19 | 19 | 3 | —（原生记录级） | ✗ 无 | ✓ 16组 | ✓ 16组 | 原生未命中且三套订阅无开屏组 → 迁移后无规则可用，需快照自建 |
| 17 | com.baidu.netdisk（※含短名 netdisk） | 19 | 19 | 0 | —（原生记录级） | ✓ 3组 | ✓ 10组 | ✓ 7组 | 原生未命中且三套订阅无开屏组 → 迁移后无规则可用，需快照自建 |
| 18 | net.csdn.csdnplus（※含短名 csdnplus） | 19 | 19 | 4 | —（原生记录级） | ✗ 无 | ✓ 6组（开屏×1） | ✓ 6组 | 原生引擎历史未命中；订阅已有开屏组 → 等 v8 gkd 内核迁移接管，无需新增 |
| 19 | com.gotokeep.keep（※含短名 keep） | 18 | 18 | 3 | —（原生记录级） | ✓ 3组 | ✓ 5组 | ✓ 5组 | 原生未命中且三套订阅无开屏组 → 迁移后无规则可用，需快照自建 |
| 20 | fenglink66p（短名）（歧义） | 15 | 15 | 1 | —（原生记录级） | ✗ 无候选 | ✗ 无候选 | ✗ 无候选 | 歧义短名：按语境定包后核覆盖（候选见下） |

涉及 issue/设备明细（Top 20）：

- 1. **com.taobao.taobao**：4 个 issue（#71 #85 #117 #167）· 设备：OnePlus PJZ110、Redmi 25060RK16C、Redmi 25102RKBEC (myron)、Xiaomi 2410DPN6CC
- 2. **com.mci.smagazine**：4 个 issue（#127 #129 #146 #168）· 设备：samsung SM-S9180、samsung SM-S9180 (dm3q)
- 3. **com.netease.cloudmusic**：1 个 issue（#168）· 设备：samsung SM-S9180 (dm3q)
- 4. **com.larus.nova**：2 个 issue（#121 #167）· 设备：Redmi 25102RKBEC (myron)、samsung SM-S9180
- 5. **com.xiaomi.shop**：1 个 issue（#167）· 设备：Redmi 25102RKBEC (myron)
- 6. **com.luna.music**：1 个 issue（#169）· 设备：Redmi 25060RK16C (dali)
- 7. **com.ximalaya.ting.android**：15 个 issue（#62 #66 #70 #71 #78 #81 #82 #85…）· 设备：Redmi 25060RK16C、Redmi 25060RK16C (dali)、Xiaomi 2410DPN6CC
- 8. **com.ss.android.ugc.aweme**：13 个 issue（#65 #69 #73 #74 #104 #105 #112 #113…）· 设备：OPPO PJT110、OPPO PLT140、Redmi 25060RK16C、Xiaomi 2211133C…
- 9. **read**：4 个 issue（#99 #111 #114 #137）· 设备：Sony XQ-CT72、samsung SM-S9180、vivo V2227A
- 10. **mm**：17 个 issue（#73 #76 #77 #78 #79 #80 #83 #85…）· 设备：OPPO PJT110、Xiaomi 2211133C、Xiaomi 2410DPN6CC、samsung SM-S9180…
- 11. **wireless**：5 个 issue（#71 #106 #122 #134 #160）· 设备：OPPO PJT110、Redmi 25060RK16C
- 12. **tv.danmaku.bili**：13 个 issue（#67 #68 #73 #83 #91 #95 #97 #98…）· 设备：OPPO PLT140、Xiaomi 2410DPN6CC、vivo V1921A、vivo V2227A
- 13. **com.tencent.mobileqq**：10 个 issue（#62 #73 #74 #79 #80 #84 #93 #104…）· 设备：OPPO PLT140、Redmi 25060RK16C、Xiaomi 2410DPN6CC
- 14. **market**：14 个 issue（#78 #79 #80 #82 #90 #93 #97 #98…）· 设备：OPPO PLB110、OPPO PLT140、Xiaomi 21081111RG、Xiaomi 2410DPN6CC…
- 15. **sentinels**：1 个 issue（#138）· 设备：OPPO PJT110
- 16. **com.sina.weibo**：9 个 issue（#64 #67 #68 #97 #98 #100 #101 #102…）· 设备：Xiaomi 2211133C、Xiaomi 2410DPN6CC
- 17. **com.baidu.netdisk**：1 个 issue（#118）· 设备：vivo V2227A
- 18. **net.csdn.csdnplus**：9 个 issue（#76 #77 #78 #82 #83 #97 #98 #101…）· 设备：Xiaomi 2410DPN6CC
- 19. **com.gotokeep.keep**：8 个 issue（#62 #83 #98 #101 #102 #103 #104 #115）· 设备：Xiaomi 2410DPN6CC
- 20. **fenglink66p**：1 个 issue（#106）· 设备：OPPO PJT110

## 2. 需要真实快照才能新增规则的 App 清单

判定标准：存在未命中记录，且三套订阅（86/233/667v646）均无该包名的 App 级开屏组、且无成功出手记录——补规则只能靠真实界面快照（无现成选择器可抄）。

### 2.1 v8.0.9 gkd 通道（完整包名，优先级最高）

| 包名 | 失败单元 | 未命中记录 | 现有覆盖情况 | 建议动作 |
|---|---|---|---|---|
| com.larus.nova | 1126 | 7 | 有 App 条目但无开屏组（667） | 快照新增 App 级开屏组 |
| com.xiaomi.shop | 734 | 6 | 有 App 条目但无开屏组（233、667） | 快照新增 App 级开屏组 |

排除说明：com.ximalaya.ting.android（gkd 成功 21 次，全局组已生效）不在此列；已有 App 级开屏组但失效者（淘宝等）归 §1「疑似过期」类。

### 2.1b 🧭 应用暂无规则清单（v8.0.9 gkd 通道，无任何未命中记录——多为系统组件误入前台，不建议补规则）

| 包名 | 🧭 记录数 | 三套订阅 | 处置建议 |
|---|---|---|---|
| com.android.quicksearchbox | 4 | 有条目（233、667） | 引擎忽略名单（系统组件无开屏广告场景） |
| com.tencent.mm | 3 | 有条目（86、233、667） | 引擎忽略名单（系统组件无开屏广告场景） |
| android | 1 | 有条目（667） | 引擎忽略名单（系统组件无开屏广告场景） |
| com.miui.securitycenter | 1 | 有条目（86、233、667） | 引擎忽略名单（系统组件无开屏广告场景） |
| li.songe.gkd | 1 | 有条目（667） | 引擎忽略名单（系统组件无开屏广告场景） |
| com.android.vending | 1 | 有条目（667） | 引擎忽略名单（系统组件无开屏广告场景） |
| com.xiaomi.market | 1 | 有条目（86、233、667） | 引擎忽略名单（系统组件无开屏广告场景） |

### 2.2 v7.x 原生通道（进程短名，订阅内无任何匹配候选）

机械后缀匹配在订阅内无候选（真空白，非歧义），需要用户回传快照或提供完整包名。按未命中记录数排序（≥3 条）：

| 短名 | 未命中记录 | 涉及 issue | 语境线索（日志窗口活动） |
|---|---|---|---|
| sentinels | 26 | #138 | 窗口活动 NewIpcDetailActivityV3/DCUniMPActivity0（摄像头/uni 小程序）→ 三套订阅内无匹配候选，需用户快照定包 |
| fenglink66p | 15 | #106 | 三套订阅内无匹配候选 |
| azukireader.app | 13 | #108 | 三套订阅内无匹配候选 |
| clockpackage | 9 | #121 #137 | 系统时钟组件，非广告场景，建议加入忽略名单 |
| screenshot | 9 | #79 #80 #88 #103 #104 | 系统截图进程，非广告场景（引擎误监听），建议加入忽略名单 |
| echoloop | 6 | #160 | — |
| fashiongallery | 6 | #136 #156 #158 | — |
| list | 5 | #148 | — |
| dv | 5 | #148 | — |
| reeden | 4 | #160 | — |
| meinc | 4 | #148 | — |
| emag | 4 | #110 #116 #130 | — |
| codebook | 4 | #128 | — |
| icebox | 3 | #106 #134 | — |
| securitypermission | 3 | #90 #106 | — |

歧义短名（订阅内多候选，不做机械合并）：`mm`（3 候选，语境指向微信）、`market`（8 候选，语境指向应用市场类）、`wireless`（3 候选，阿里系容器）、`read`（2 候选，语境指向番茄小说）、`browser`（7 候选）、`chat`（2 候选）——定包后按 §1 核覆盖。

## 3. 误触风险观察（⚠/❌ 记录分析）

### 3.1 ⚠️ 热区误触观察（62 条，v7.x 原生引擎「全屏热区控件，轻微触碰即跳转」观察记录）

| App（短名） | 观察次数 | 涉及 issue | 涉及设备 |
|---|---|---|---|
| aweme | 35 | #65 #74 #75 #81 #82 #99 | OPPO PLT140、Redmi 25060RK16C、Xiaomi 2211133C |
| smagazine | 10 | #129 #146 | samsung SM-S9180 |
| netdisk | 10 | #118 | vivo V2227A |
| read | 2 | #114 #137 | Sony XQ-CT72、samsung SM-S9180 |
| weibo | 2 | #135 | Xiaomi 2211133C |
| market | 2 | #79 #80 | Xiaomi 2410DPN6CC |
| xhs | 1 | #157 | OPPO PLT140 |

解读：这是引擎对「用户误触全屏热区跳转广告」的观察统计（⚠ 观察对象是广告主诱导热区，不是引擎误触）。**当前样本 0 条 ❌ 记录、0 条 result:false 动作失败、0 条 action_no_effect**，未发现引擎自身误触证据；v7.x 防护记录（🛡 覆盖层防误触/🚧 动作边界自检拦截/🤝 用户操作让路）在原生日志中大量存在，说明误触防护链在工作。热区观察集中于抖音（35 次/13 个 issue）——抖音开屏热区误触是用户侧真实痛点，下版可对 aweme 用户提示「热区跳转属广告主诱导，建议升级 v8 gkd 内核」。

### 3.2 其他 ⚠️ 记录（服务级，非误触）

- ⚠️ 无障碍登记被移除：7 条（#158/#136/#132×3/#125×2，OPPO/vivo 设备为主）——厂商省电管控清后台所致，属保活问题，与规则迭代无关；建议下版强化保活体检提示。
- ⚠️ 手势被系统中断：2 条（#114 read、#64 ting.android）——系统手势限流/冲突，引擎已带 350ms 重试，非规则问题。

## 附：方法与数据质量

- 解析质量：111/112 完整解析（1 个非日志反馈类跳过 #61，2 个 E2E 存档已标记）；记录级口径与局限见 `reports/issue-parse/stats.md` §7。
- 本表「成功出手」含 v8.0.7 自动通道触发行（无显式 result 字段，按『触发即出手』口径，已在 records.jsonl note 字段标注）。
- gkd 内核 issue 共 22 个；667 对照采用上游 Lin-arm v646 完整版（1013 apps），因本地 667.json5 为 932KiB 截断损坏件（详见 /home/z/gkd-research/REPORT.md）。
- 未合并的歧义短名未计入 Top 20 失败单元（避免重复计数）；其记录明细保留在 records.jsonl。
- Top 20 中 v8.0.9 设备仅 3 台（#167 Redmi 25102RKBEC / #168 samsung SM-S9180 / #169 Redmi 25060RK16C），评估轮数被「单设备长时间停留 App（商城/阅读类）」放大，解读需注意样本设备偏差；v8.0.9 未命中率 79.4% 亦同因。
