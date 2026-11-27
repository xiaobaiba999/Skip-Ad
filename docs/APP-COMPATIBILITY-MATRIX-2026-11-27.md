# 应用规则兼容性矩阵 · 2026-11-27 · v8.1.0

## 方法论与诚实标注（先读这个）

- **覆盖数据**：解析三套内置订阅（86 奥怪 v89 / 233 甘霖 v72 / 667 Lin-arm v646）真实资产文件，按包名聚合（v8.1.0 内置，1013+ 款并集）。
- **语法兼容 = PASS（真实测试证据）**：667 全部 2547 规则组 + 86/233 全部组已过真实引擎 `RuleGroupCompilation.compile` 编译双闸（`BundledSubscriptionAssetsTest`，JVM 全绿）——规则语法/类型全量验证通过。
- **真实世界状态 = 真实用户日志证据**（111 个开放 Issue 的结构化解析，v5.x~v8.0.9 多代客户端）：PASS=有成功动作记录；FAIL=有未命中记录；BOTH=两者都有；NO_DATA=当前 issue 池无该应用数据（不等于不工作——大多数常用 App 无人上报即无人失败）。
- **真机测试 = NOT_RUN**：本环境无真机。规则命中与否的最终验证依赖真实设备与真实广告场景；本矩阵不把「订阅有规则」冒充「测试通过」。
- **误触**：当前 111 个开放 Issue 零误触投诉（引擎侧「误触防护」计数为拦截成功，无用户报告误点后果）。

## 汇总

- 覆盖应用总数（三订阅并集）：**1065 款**（86 子集 72 · 233 子集 610 · 667 子集 1013）
- 含开屏广告规则组的应用：**237 款**
- 有真实用户日志证据：**28 款**（PASS 15 · FAIL 7 · BOTH 6）
- 无真实数据（NO_DATA）：**1037 款**——等待真实用户日志
- 语法/类型编译兼容：2547+289+1392 = 全部规则组 0 失败（真实 JVM 测试）

## 矩阵（按真实证据优先排序，节选有证据 + 有开屏组的全部应用，其余 NO_DATA 应用按分组统计）

### 音乐视频（覆盖 9 款 · 有证据 4）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.netease.cloudmusic` | 网易云音乐 | 233+667+86 | 38 | ✓ | BOTH | ✅1次成功 · ❌1305轮未命中 · issue 168 |
| `com.larus.nova` | 豆包 | 667 | 4 | — | BOTH | ✅1次成功 · ❌1125轮未命中 · issue 167 |
| `com.luna.music` | 汽水音乐 | 233+667 | 31 | ✓ | BOTH | ✅6次成功 · ❌373轮未命中 · issue 152,169 |
| `com.ximalaya.ting.android` | 喜马拉雅 | 233+667 | 23 | — | BOTH | ✅21次成功 · ❌165轮未命中 · issue 151,152,164,169 |
| `com.miui.player` | 小米音乐 | 233+667+86 | 23 | ✓ | NO_DATA | 等待真实日志 |
| `com.kugou.android` | 酷狗音乐 | 233+667+86 | 19 | ✓ | NO_DATA | 等待真实日志 |
| `com.tencent.qqmusic` | QQ音乐 | 233+667+86 | 16 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 2 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 短视频（覆盖 6 款 · 有证据 1）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `tv.danmaku.bili` | 哔哩哔哩 | 233+667+86 | 70 | ✓ | PASS | ✅3次成功 · issue 151,164 |
| `com.ss.android.ugc.aweme` | 抖音 | 233+667 | 52 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 4 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 社交聊天（覆盖 6 款 · 有证据 1）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.tencent.mm` | 微信 | 233+667+86 | 103 | ✓ | FAIL | ❌3轮未命中 · issue 167,168 |
| `com.tencent.mobileqq` | QQ | 233+667+86 | 91 | ✓ | NO_DATA | 等待真实日志 |
| `com.xunmeng.pinduoduo` | 拼多多 | 233+667+86 | 61 | ✓ | NO_DATA | 等待真实日志 |
| `com.sankuai.meituan` | 美团 | 233+667 | 35 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 2 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 电商购物（覆盖 6 款 · 有证据 3）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.taobao.taobao` | 淘宝 | 233+667+86 | 58 | ✓ | BOTH | ✅1次成功 · ❌2869轮未命中 · issue 167 |
| `com.xiaomi.shop` | 小米商城 | 233+667 | 5 | — | FAIL | ❌734轮未命中 · issue 167 |
| `com.jingdong.app.mall` | 京东 | 233+667+86 | 32 | ✓ | PASS | ✅1次成功 · issue 164 |
| … | 其余 3 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 新闻资讯（覆盖 5 款 · 有证据 1）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `net.csdn.csdnplus` | CSDN | 233+667 | 10 | ✓ | PASS | ✅4次成功 · issue 151,164 |
| `com.cctv.yangshipin.app.androidp` | 央视频 | 667 | 5 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 3 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 浏览器（覆盖 4 款 · 有证据 0）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.UCMobile` | UC浏览器 | 233+667 | 8 | ✓ | NO_DATA | 等待真实日志 |
| `com.tencent.mtt` | QQ浏览器 | 233+667 | 7 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 2 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 工具应用（覆盖 4 款 · 有证据 1）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.android.vending` | Google Play 商店 | 667 | 9 | — | FAIL | ❌1轮未命中 · issue 167 |
| `com.coolapk.market` | 酷安 | 233+667+86 | 15 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 2 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 金融应用（覆盖 3 款 · 有证据 0）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.eg.android.AlipayGphone` | 支付宝 | 233+667+86 | 65 | ✓ | NO_DATA | 等待真实日志 |
| `com.icbc` | 中国工商银行 | 233+667+86 | 9 | ✓ | NO_DATA | 等待真实日志 |
| `com.chinamworld.main` | 中国建设银行 | 233+667+86 | 6 | ✓ | NO_DATA | 等待真实日志 |

### 系统应用（覆盖 5 款 · 有证据 1）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.miui.securitycenter` | 安全服务/小米手机管家 | 233+667+86 | 14 | ✓ | FAIL | ❌1轮未命中 · issue 167 |
| `com.heytap.market` | OPPO软件商店 | 233+667 | 4 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 3 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 学习教育（覆盖 2 款 · 有证据 0）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| … | 其余 2 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 出行生活（覆盖 5 款 · 有证据 0）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.autonavi.minimap` | 高德地图 | 233+667+86 | 25 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 4 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

### 其他（覆盖 1010 款 · 有证据 16）

| 包名 | 名称 | 覆盖订阅 | 规则组 | 开屏组 | 真实状态 | 证据详情 |
|---|---|---|---|---|---|---|
| `com.mci.smagazine` | 三星阅读 | 667 | 1 | ✓ | BOTH | ✅2次成功 · ❌1369轮未命中 · issue 168 |
| `cn.com.yunma.school.app` | 易校园 | 667 | 5 | — | PASS | ✅11次成功 · issue 151,152,164,169 |
| `com.github.android` | GitHub | 667 | 3 | — | PASS | ✅8次成功 · issue 151 |
| `com.shark.jizhang` | 鲨鱼记账 | 233+667 | 3 | — | PASS | ✅6次成功 · issue 151,164 |
| `com.android.quicksearchbox` | 搜索 | 233+667 | 2 | — | FAIL | ❌4轮未命中 · issue 167,169 |
| `com.chaoxing.mobile` | 学习通 | 233+667 | 10 | ✓ | PASS | ✅4次成功 · issue 152,169 |
| `com.gotokeep.keep` | Keep | 233+667+86 | 13 | — | PASS | ✅3次成功 · issue 164 |
| `com.sina.weibo` | 微博 | 233+667 | 31 | — | PASS | ✅3次成功 · issue 165,169 |
| `com.tencent.qqlive` | 腾讯视频 | 233+667 | 31 | ✓ | PASS | ✅3次成功 · issue 165 |
| `com.baidu.BaiduMap` | 百度地图 | 233+667+86 | 20 | ✓ | PASS | ✅2次成功 · issue 164 |
| `com.greenpoint.android.mc10086.activity` | 中国移动 | 233+667+86 | 17 | ✓ | PASS | ✅2次成功 · issue 151 |
| `com.waimaiii.waimaiii` | 歪麦霸王餐 | 233+667 | 6 | ✓ | PASS | ✅2次成功 · issue 151,164 |
| `com.zhihu.android` | 知乎 | 233+667+86 | 49 | ✓ | PASS | ✅2次成功 · issue 151,164 |
| `com.cainiao.wireless` | 菜鸟 | 233+667+86 | 24 | ✓ | PASS | ✅1次成功 · issue 152 |
| `com.xiaomi.market` | 小米应用商店 | 233+667+86 | 21 | ✓ | FAIL | ❌1轮未命中 · issue 167 |
| `li.songe.gkd` | GKD | 667 | 1 | — | FAIL | ❌1轮未命中 · issue 167 |
| `com.baidu.tieba` | 百度贴吧 | 233+667+86 | 47 | ✓ | NO_DATA | 等待真实日志 |
| `com.dragon.read` | 番茄免费小说 | 233+667 | 46 | ✓ | NO_DATA | 等待真实日志 |
| `com.zmzx.college.search` | 大学搜题酱 | 233+667+86 | 35 | ✓ | NO_DATA | 等待真实日志 |
| `com.google.android.youtube` | YouTube | 233+667+86 | 33 | ✓ | NO_DATA | 等待真实日志 |
| `com.qidian.QDReader` | 起点读书 | 233+667 | 31 | ✓ | NO_DATA | 等待真实日志 |
| `com.xingin.xhs` | 小红书 | 233+667 | 31 | ✓ | NO_DATA | 等待真实日志 |
| `com.douban.frodo` | 豆瓣 | 233+667 | 27 | ✓ | NO_DATA | 等待真实日志 |
| `com.xunlei.downloadprovider` | 迅雷 | 233+667 | 20 | ✓ | NO_DATA | 等待真实日志 |
| `com.baidu.youavideo` | 一刻相册 | 233+667 | 17 | ✓ | NO_DATA | 等待真实日志 |
| `com.ct.client` | 中国电信 | 233+667+86 | 17 | ✓ | NO_DATA | 等待真实日志 |
| `com.dongqiudi.news` | 懂球帝 | 233+667 | 17 | ✓ | NO_DATA | 等待真实日志 |
| `com.cyl.musiccy.ou` | OMOFUN | 233+667 | 15 | ✓ | NO_DATA | 等待真实日志 |
| `com.huawei.android.thememanager` | 华为主题 | 233+667 | 15 | ✓ | NO_DATA | 等待真实日志 |
| `com.hihonor.appmarket` | 荣耀应用市场 | 233+667 | 14 | ✓ | NO_DATA | 等待真实日志 |
| `info.muge.appshare` | AppShare | 233+667 | 14 | ✓ | NO_DATA | 等待真实日志 |
| `com.kuaishou.nebula` | 快手极速版 | 233+667 | 13 | ✓ | NO_DATA | 等待真实日志 |
| `com.kugou.android.lite` | 酷狗概念版 | 667 | 13 | ✓ | NO_DATA | 等待真实日志 |
| `com.tencent.androidqqmail` | QQ邮箱 | 233+667+86 | 13 | ✓ | NO_DATA | 等待真实日志 |
| `cn.soulapp.android` | Soul | 233+667 | 12 | ✓ | NO_DATA | 等待真实日志 |
| `com.duowan.kiwi` | 虎牙直播 | 233+667+86 | 12 | ✓ | NO_DATA | 等待真实日志 |
| `com.huawei.music` | 华为音乐 | 233+667 | 12 | ✓ | NO_DATA | 等待真实日志 |
| `com.paokeji.yiqu` | 喵趣漫画 | 233+667 | 12 | ✓ | NO_DATA | 等待真实日志 |
| `com.firebear.androil` | 小熊油耗 | 233+667 | 11 | ✓ | NO_DATA | 等待真实日志 |
| `gov.pianzong.androidnga` | NGA玩家社区 | 233+667 | 11 | ✓ | NO_DATA | 等待真实日志 |
| … | 其余 970 款无证据且无开屏组应用（NO_DATA，订阅已覆盖，等待真实日志） | | | | | |

## Top 失败应用详情（未命中轮数降序，v8 gkd 通道完整包名口径）

| 排名 | 包名 | 名称 | 覆盖订阅 | 真实状态 | 未命中轮数 | 建议 |
|---|---|---|---|---|---|---|
| 1 | `com.taobao.taobao` | 淘宝 | 233+667+86 | BOTH | 2869 | 三订阅均有规则但未命中最多——需真实快照定位新版开屏节点（列入下轮优先） |
| 2 | `com.mci.smagazine` | 三星阅读 | 667 | BOTH | 1369 | 233/667 已覆盖——规则未命中，需快照比对选择器 |
| 3 | `com.netease.cloudmusic` | 网易云音乐 | 233+667+86 | BOTH | 1305 | 667 已覆盖——未命中 1305 轮，需新版快照 |
| 4 | `com.larus.nova` | 豆包 | 667 | BOTH | 1125 | 667 已覆盖——未命中 1125 轮，需快照 |
| 5 | `com.xiaomi.shop` | 小米商城 | 233+667 | FAIL | 734 | 667 已覆盖——未命中 734 轮，需快照 |
| 6 | `com.luna.music` | 汽水音乐 | 233+667 | BOTH | 373 | 667 已覆盖+补丁加固——已有成功记录同时有未命中轮，下轮比对新版开屏结构 |
| 7 | `com.ximalaya.ting.android` | 喜马拉雅 | 233+667 | BOTH | 165 | 需真实快照比对选择器（列入下轮） |
| 8 | `com.android.quicksearchbox` | 搜索 | 233+667 | FAIL | 4 | 需真实快照比对选择器（列入下轮） |
| 9 | `com.tencent.mm` | 微信 | 233+667+86 | FAIL | 3 | 需真实快照比对选择器（列入下轮） |
| 10 | `com.miui.securitycenter` | 安全服务/小米手机管家 | 233+667+86 | FAIL | 1 | 需真实快照比对选择器（列入下轮） |
| 11 | `com.xiaomi.market` | 小米应用商店 | 233+667+86 | FAIL | 1 | 需真实快照比对选择器（列入下轮） |
| 12 | `li.songe.gkd` | GKD | 667 | FAIL | 1 | 需真实快照比对选择器（列入下轮） |
| 13 | `com.android.vending` | Google Play 商店 | 667 | FAIL | 1 | 需真实快照比对选择器（列入下轮） |

## 数据质量说明

- v7.15.x 客户端日志使用进程短名（如 aweme/mm/bili），与完整包名不可靠对应——为避免编造映射，短名记录不并入矩阵（口径分列见 reports/issue-parse/stats.md）。
- 本矩阵的「真实状态」反映的是**上报用户的设备与版本**（多为 Android 14-17 / Redmi/小米/OPPO/vivo/三星），不代表全部机型。
- 下一轮规则迭代清单见 docs/RULE-ITERATION-2026-11-27.md（含三订阅覆盖核查与需快照场景清单）。
