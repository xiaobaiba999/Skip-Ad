# 200+ 应用生态兼容性覆盖报告（v7.9.4）

- 生成时间：2026-11-21T12:45:00+08:00（Asia/Shanghai）· v7.9.4 刷新（apps_catalog meta 版本随 gen-apps-catalog.py 重跑）
- 应用总量：**233 款**（目标 ≥200 ✅）
- 包名来源：内置订阅自证 115 · 网络核实 23 · 知识库高置信 95
- 引擎覆盖：内置订阅 119/233 · FeedAdGuard 名单 19/233 · GKD 快照（三源并集）148/233
- 优先级分布：P0 63 款 · P1/P2 合计 170 款

## 分类配额达成

| 分类 | 目录数 | 配额 | 状态 |
|---|---:|---:|---|
| 社交与即时通讯 | 25 | 25+ | ✅ |
| 短视频与视频播放 | 25 | 25+ | ✅ |
| 新闻资讯与信息流 | 20 | 20+ | ✅ |
| 电商与购物 | 26 | 25+ | ✅ |
| 音乐与音频 | 10 | 10+ | ✅ |
| 浏览器与搜索 | 11 | 10+ | ✅ |
| 工具与系统应用 | 24 | 20+ | ✅ |
| 阅读与小说 | 15 | 15+ | ✅ |
| 游戏与游戏中心 | 17 | 15+ | ✅ |
| 学习与教育 | 15 | 15+ | ✅ |
| 金融与生活服务 | 35 | 15+ | ✅ |
| 其他常用 | 10 | 10+ | ✅ |

## 分层测试进度（诚实口径）

| 层级 | 状态 | 说明 |
|---|---|---|
| L1 规则/引擎自动化 | ✅ 全量 233 款（AppCatalogTest，随每轮测试跑） | 包名/分类/订阅加载/引擎一致性断言 |
| L2 页面结构场景 | ✅ v7.9.3 建齐 | PageScenarioTest 83 断言：12 类 UI 独立样本 × 8 项场景行为（含订阅七分类真实计数器） |
| L2-FP 误触红线场景 | ✅ v7.9.4 新增 | FalsePositiveScenarioTest 69 断言：20 类「绝对不应该点击」场景 + 孤立词红线 + 对照侧（真广告语境同词照常出手）——实测根治 L1 跳过键零形态证据缺陷 |
| L2-Timing 时序竞态 | ✅ v7.9.4 新增 | AdTaskTimingTest 95 断言：A-H 八组时序竞态 + Case1/2/3 三种真机实锤硬回归（旧 actor 全拒·重试五要件·click()=true ≠ 广告关闭） |
| L3 真机实测 | ⛔ 未开始 | 无真机连接（REAL_DEVICE_TEST = NOT_AVAILABLE）；待日志回传后录入，绝不虚报 |

## 本轮（v7.9.2）由目录核实直接揪出的引擎数据缺陷（全部已修复）

| # | 缺陷 | 证据 | 修复 |
|---|---|---|---|
| 1 | 中国联通包名误带 Activity 类名后缀（规则从未命中） | sj.qq.com/appdetail/com.sinovatech.unicom.ui | Subscription.java 包名更正 |
| 2 | FeedAdGuard 网易新闻包名少 .activity 后缀（信息流护栏全程无效） | play.google.com id=com.netease.newsreader.activity | FEED_APPS 更正 |
| 3 | 建设银行/中国银行两行名称互换 | 银行业常识核对 | 显示名互换 |
| 4 | com.lemon.lvoverseas 误标「剪映」（实为 CapCut 海外版） | sj.qq.com/appdetail/com.lemon.lv | 名称更正+补国内版条目 |
| 5 | com.flightmanager.view 误标「飞常准」（实为航班管家） | play.google.com id=veryzhun.vzcom | 名称更正+补 veryzhun.vzcom 条目 |

## 已知未核实项（宁缺毋滥，不虚报）

- TapTap 国内版包名：多轮检索无法核实 → 只收国际版 com.taptap.global
- SHEIN 包名：apkpure 结果均为仿冒名，无法核实 → 不收录
- 央视频/一点资讯/咪咕阅读/中国大学MOOC/QQ邮箱(国产通道)/微博轻量版：包名证据不足 → 不收录
- 知识库高置信包名 95 款：待真机 `adb shell pm list packages` 一次性核验后升级为 verified

## 维护规则

1. 新增应用必须三选一来源：网络核实（附 URL 证据）/ 引擎自证 / 知识库高置信（标注待核验）
2. 严禁为凑数收录无人使用应用；严禁把名称相似的不同产品当同一应用
3. testStatus 字段语义：l1-tested 仅指 JVM 断言；真机结论只来自 L3 日志
4. 每轮迭代后重跑 `python3 scripts/gen-apps-catalog.py` 刷新覆盖交叉计算
