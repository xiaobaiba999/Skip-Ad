<div align="center">

# Skip-Ad · 开屏卫士

**安卓开屏广告秒跳引擎 —— 露头就跳、弹窗自动关、订阅导入永不失败、1:1 人手模拟触点**

[![Releases](https://img.shields.io/github/v/release/xiaobaiba999/Skip-Ad?style=flat-square&label=%E6%9C%80%E6%96%B0%E5%8F%91%E5%B8%83)](https://github.com/xiaobaiba999/Skip-Ad/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](#许可)
[![APK](https://img.shields.io/badge/APK-808KB-orange?style=flat-square)](https://github.com/xiaobaiba999/Skip-Ad/releases/latest)
[![Tests](https://img.shields.io/badge/JVM%E6%96%AD%E8%A8%80-3451%20%E2%9C%85-brightgreen?style=flat-square)](docs/test-reports/round-32.json)
[![Matrix](https://img.shields.io/badge/%E6%9C%BA%E5%9E%8B%E7%9F%A9%E9%98%B5-9%E5%93%81%E7%89%8C%E5%8F%8A%E5%AD%90%E5%93%81%E7%89%8C%286%E6%9C%BA%E5%9E%8B-286-blue?style=flat-square)](android-app/tests/com/zai/adshield/test/BrandFocus286Test.java)
[![HumanTap](https://img.shields.io/badge/1%3A1%E4%BA%BA%E6%89%8B%E6%A8%A1%E6%8B%9F-%E8%AF%AF%E7%82%B9%E5%A4%9A%E7%82%B9%E6%A0%B9%E6%B2%BB-ff7043?style=flat-square)](android-app/tests/com/zai/adshield/test/HumanTapMatrixTest.java)
[![Topic](https://img.shields.io/badge/topic-gkd--subscription-8e8e8e?style=flat-square)](https://github.com/topics/gkd-subscription)

Java 引擎 · GKD 订阅生态兼容 · [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) · [订阅仓库](https://github.com/xiaobaiba999/skip-ad-subscription) · [问题反馈](https://github.com/xiaobaiba999/Skip-Ad/issues)

</div>

---

## 一屏速览

| | |
| --- | --- |
| **怎么工作** | 无障碍服务读屏找到「跳过」控件（文本/描述/控件 ID/图片角落键四通道），0ms 露头即点；**1:1 人手模拟触点**（人手参数域+人手复验节奏+dp 密度归一+实时人眼追踪+轨迹去重+同目标/结果双静默防线——不同设备上零误触零多点）；点击冷却 300ms · 倒计时将尽提前 0.38s 开火 |
| **拦截范围** | 开屏广告 · 插屏/激励弹窗 · 摇一摇诱导 · 悬浮广告球 · 视频前贴（134 款内置 + 任意 GKD 订阅扩展） |
| **不碰什么** | 小程序/支付/团购容器零扫描零点击；银行/政务/相机零干预；只点应用自带「跳过」键（合规红线） |
| **隐私** | 数据仅存本机；联网仅日志上传（脱敏）与更新检查；零定位/通讯录/存储权限 |
| **体积** | APK 808KB（含三源离线订阅快照 ~520KB）· 冷启动毫秒级 · 典型日功耗 0.05mWh 级 |

## 当前版本 v7.0.0（2026-11-19）· 回退 6.0 基座重构

**回到 6.0 已验证基座重新出发**：用户实锤 6.0 之后的版本在部分设备全部不可用（进入后无法点击/版本号「？」/永久加载）——已按指令回退到 6.0 基座重构，v6.1 之后的所有 GitHub Release 与 Tag 全部清除；6.0 基座 + 6.1 实测验证的全部特性（订阅镜像自愈/误触三防线/8h 日志）保持。v7.0.0（versionCode 70000 > 60500）直接覆盖安装即回稳定基座。

- **订阅市场导入永不失败（Connection reset 终根治）**：八通道镜像自动切换（jsdelivr 四接入点→ghfast/gh-proxy 代理→raw 直连，进度实时可见）；全通道失败时 APK 内置离线快照兑底（三个市场源最新内容随包发行），联网后 1 小时自动换真源
- **版本信息规范（言简意赅）**：当前版本 1 标题 + 1 段落；检查更新发现新版本当场显示新版本 ≤5 条【带序号】功能描述；顶部更新横幅移除（只有手动「检查更新」能查询/下载）
- **自动提交日志一日 3 次**（24 小时均匀每 8 小时一次，成功才计时）+ 日志留存 300→400 条 + 设备画像行（分辨率/密度——多点误点归因所需）+ 重点事件段（误触防护/自愈/订阅/预警）
- **多点误点三重防线保持与验证**（F 同指纹目标静默 · G 新鲜坐标复核 · H 结果静默）+ 界面初始化保险丝（JS 早期崩溃时给可操作指引，绝不「永久加载」白屏）
- **9 品牌+子品牌 11 组 × 26 = 286 机型七维全 286/286**（开屏命中/多点误点/屏蔽速度/bug/功耗/精准度/算法；只测 OPPO/vivo/小米/三星/华为/荣耀/红米/一加/iQOO 及子品牌，新增 2026-2027 世代机型与系统）· 全仓 **3451 项 JVM 断言全绿**

历史版本全部归档在 [docs/CHANGELOG.md](docs/CHANGELOG.md)；本轮回退决策与重构实录：[docs/OPTIMIZATION-v7.0.0.md](docs/OPTIMIZATION-v7.0.0.md)。

## App 截图（真实 UI 截取）

<table>
<tr>
<td><img src="docs/screenshots/app-home.png" width="240" alt="首页：守护状态与战绩"/><br><sub><b>首页</b> — 守护状态 / 战绩 / 开关（极简版）</sub></td>
<td><img src="docs/screenshots/app-subscription.png" width="240" alt="订阅：市场与导入"/><br><sub><b>订阅</b> — 订阅市场 3 精选源（点一下即导入）/ GKD 导入</sub></td>
<td><img src="docs/screenshots/app-me.png" width="240" alt="我的：版本与日志"/><br><sub><b>我的</b> — 版本 / 高级折叠 / 引擎日志（刷新+上传）</sub></td>
</tr>
</table>

<sub>截图由 App 实际 WebView UI（`android-app/assets/www`）渲染截取，非设计稿；战绩数字为示例数据。</sub>

## 订阅

- **订阅市场**（App 内点一下即导入，仅 3 个高星活跃源）：Lin-arm（⭐7.4k 日更）/ ganlinte（⭐935 · 610 APP）/ MengNianxiaoyao（⭐496）
- **官方订阅**：`https://cdn.jsdelivr.net/gh/xiaobaiba999/skip-ad-subscription@main/dist/gkd.json5`（100% GKD 兼容，也可直接在 GKD 客户端导入）
- **更多订阅**：[github.com/topics/gkd-subscription](https://github.com/topics/gkd-subscription) 第三方订阅列表
- **自建订阅**：[gkd-kit/subscription-template](https://github.com/gkd-kit/subscription-template) 五分钟构建自己的远程订阅

## 核心能力

| 能力 | 一句话 |
| --- | --- |
| 文本/vid 跳过键 | 「跳过/Skip/推广族」+ viewId 双通道，0ms 首扫毫秒级代点 |
| 第一方图片开屏 | 无字图片跳过键 → 角落小可点控件手势点击（形状门防误点） |
| SDK 广告窗 | className 指纹 50+ 段精确匹配（穿山甲/优量汇/Unity/IronSource/Mintegral/Pangle/快手 ksad/联盟 csj_/百度 mobads/Sigmob/AdColony/Vungle …）→ 点击梯子 + 返回键直断兜底 |
| 1:1 人手模拟触点 | 人手参数域（延迟/时长/微抖散布）· 人手复验节奏（500ms→1.4~7s 渐进）· dp 密度归一 · 实时人眼追踪 · 轨迹去重 ±8dp——不同设备类型/分辨率上零误触零多点 |
| 设备自适应触点 | 可见交集 + 窗口钳制 + 象限轮换 + 覆盖层 Z 序判定——分屏/折叠/键盘/悬浮窗千差万别的设备上零误触 |
| 屏蔽速度 | 点击冷却 300ms · 倒计时将尽提前 0.38s 开火 · 骨架扫描前 4s 密度×2 · 补扫节流 80ms——全链路压榨 |
| 弹窗广告 | 弹窗窗期独立、角标+关闭键、Dialog 形态安全门 |
| 摇一摇/翻转诱导 | 文案识别抢先关闭（不对抗用户真实跳转） |
| 容器保护 | 小程序/团购/支付容器（抖音/微信/支付宝 XRiver）内零扫描零点击 |
| 守护模式 | 银行/政务/支付：广告照拦，进程绝不杀 |
| 习惯学习 | 手动点一次的位置/指纹，次启 P0 预拦截（广告未及呈现） |
| 订阅 | 粘贴任意 gkd.li 订阅链接导入；订阅市场一键导入（八通道镜像+离线快照兑底） |
| 功耗治理 | 分级扫描预算 + 探针前置 + 息屏零唤醒，典型日 < 0.05mWh |

## 技术栈（定版）

**Java 管设备，TypeScript 管规则，脚本管流水线**——引擎层 Java 8 零依赖（冷启动比 Kotlin 快 40%、APK 比 Kotlin 方案小约 7×）；壳 UI 用 JS（V8）；规则台/CI 用 TS（与 GKD 订阅生态同构）。基准测试与裁决详见 [docs/LANGUAGE-STRATEGY.md](docs/LANGUAGE-STRATEGY.md)。

```
android-app/
├─ src/com/zai/adshield/        # Java 无障碍引擎（决策阶梯 L0-L6 · 纯 JVM 可测）
│  └─ core/                      # AdDecisionCore / RuleEngine / GkdSelector / RestoreGuard …
├─ assets/www/                   # JS 壳 UI（订阅市场八通道+离线快照 / 检查更新带序号描述 / 首页极简）
├─ assets/subsnap/               # 三源离线订阅快照（导入兑底——永不失败）
├─ tests/                        # 3451 项 JVM 断言（含 BrandFocus286Test 9品牌+子品牌 286 机型矩阵 + HumanTapMatrixTest + TapPointMatrixTest + ReleaseNotesTest + SubMirrorTest）
└─ run-tests.sh                  # 零依赖测试跑器
```

## 测试

- **9 品牌 + 子品牌 11 组 × 26 机型 = 286 机型**（只测用户点名的品牌：OPPO/vivo/小米/三星/华为/荣耀/红米/一加/iQOO 及其子品牌 POCO/realme，其他品牌不测）：开屏命中 / 多点误点 / 屏蔽速度 / bug / 功耗 / 精准度 / 算法优化 **七维各 286/286**（含 2026-2027 世代机型与 HyperOS 4/HarmonyOS 7/One UI 9 等新系统）
- **1:1 人手模拟矩阵**：24 类真实设备几何 × **真实密度配对**（720p 遗留机 1.0~2.0 / QHD 旗舰 3.5~4.0 / 折叠内屏 2.0~2.6——真实世界设备组合）+ 全 7 档压力条件契约（可刺→安全落点 · 亚指腹→拒点）全绿
- **设备自适应触点矩阵**：24 类真实设备几何 × 12 误触场景全绿；**订阅镜像链与快照归一**（SubMirrorTest 35 项）+ **版本描述规范**（ReleaseNotesTest 30 项）新增
- 三十二轮自测轨迹见 [docs/test-reports/summary.md](docs/test-reports/summary.md)
- 每轮报告：`docs/test-reports/round-N.json`（最新：[round-32](docs/test-reports/round-32.json) · 3451 断言全绿）

## 安装

1. [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases/latest) 下载 APK（808KB）直接安装（v6.1-v6.5 用户覆盖安装即回稳定基座——versionCode 70000）；
2. 开启无障碍服务（系统会弹 10 秒安全确认，属正常防诈设计——开启一次后 App 内/通知栏即可控制，绝不再去系统设置）；
3. 常被清理后台的机型（HyperOS 等）：App 首页「保活体检」只显示待处理项，点「去设置」逐项确认。

同包名同签名覆盖安装即升级，无障碍授权/学习数据/已导入订阅全部保留。

## 合规与边界

- 仅代点应用自带的「跳过」控件，不屏蔽广告内容本身（参考「拦精灵」案司法边界）
- 信息流伪装内容类「软广」不处理
- 权限审计：仅网络/通知/前台服务/开机自启/电池白名单（免打扰持久模式已取消——零 ADB 零电脑）

## 免责声明

- 本工具仅代点应用**自带的「跳过」控件以跳过开屏/弹窗广告**，不拦截、不修改、不屏蔽广告内容本身，不采集广告数据；
- 需授权「无障碍服务」方能工作（用于读取屏幕控件与代点跳过键）——这是唯一的技术依赖，请从官方 Releases 下载本应用，避免二次打包版本滥用无障碍能力；
- 自动提交的引擎日志已双端双重脱敏（手机号/身份证/邮箱/IP），且仅含引擎运行指标与设备画像，不含任何页面内容或个人数据，可在设置中关闭；
- 不同厂商 ROM 对无障碍服务与后台策略差异较大，保活体验因机型而异；遇到问题请通过 App 内「上传日志」反馈，附设备信息更便于定位；
- 本项目仅供学习交流与个人使用，使用者需遵守当地法律法规及所使用 App 的用户协议；开源代码 MIT 许可，不提供任何明示或暗示的担保。

## 许可

MIT · 欢迎自由 fork 与二改
