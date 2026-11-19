<div align="center">

# Skip-Ad · 开屏卫士

**安卓广告秒跳引擎 —— 开屏露头就跳、App 内信息流广告两段式关、305KB 零依赖、1:1 人手模拟触点**

[![Releases](https://img.shields.io/github/v/release/xiaobaiba999/Skip-Ad?style=flat-square&label=%E6%9C%80%E6%96%B0%E5%8F%91%E5%B8%83)](https://github.com/xiaobaiba999/Skip-Ad/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](#许可)
[![APK](https://img.shields.io/badge/APK-305KB-orange?style=flat-square)](https://github.com/xiaobaiba999/Skip-Ad/releases/latest)
[![Tests](https://img.shields.io/badge/JVM%E6%96%AD%E8%A8%80-3765%20%E2%9C%85-brightgreen?style=flat-square)](docs/test-reports/round-28.json)
[![Matrix](https://img.shields.io/badge/%E6%9C%BA%E5%9E%8B%E7%9F%A9%E9%98%B5-9%E5%93%81%E7%89%8C%20%C3%97%2026%E6%9C%BA%E5%9E%8B-286-blue?style=flat-square)](android-app/tests/com/zai/adshield/test/BrandFocus286Test.java)
[![FeedAdGuard](https://img.shields.io/badge/%E4%BF%A1%E6%81%AF%E6%B5%81%E5%B9%BF%E5%91%8A%E5%8D%AB%E5%A3%AB-%E6%9C%8B%E5%8F%8B%E5%9C%88%E4%B8%A4%E6%AE%B5%E5%BC%8F-00897b?style=flat-square)](android-app/src/com/zai/adshield/core/FeedAdGuard.java)
[![HumanTap](https://img.shields.io/badge/1%3A1%E4%BA%BA%E6%89%8B%E6%A8%A1%E6%8B%9F-%E8%AF%AF%E7%82%B9%E5%A4%9A%E7%82%B9%E6%A0%B9%E6%B2%BB-ff7043?style=flat-square)](android-app/tests/com/zai/adshield/test/HumanTapMatrixTest.java)
[![TapPoint](https://img.shields.io/badge/%E8%AE%BE%E5%A4%87%E8%87%AA%E9%80%82%E5%BA%94%E8%A7%A6%E7%82%B9-24%E5%87%A0%E4%BD%95%C3%9712%E5%9C%BA%E6%99%AF-9cf5b1?style=flat-square)](android-app/tests/com/zai/adshield/test/TapPointMatrixTest.java)
[![Topic](https://img.shields.io/badge/topic-gkd--subscription-8e8e8e?style=flat-square)](https://github.com/topics/gkd-subscription)

Java 引擎 · GKD 订阅生态兼容 · [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) · [订阅仓库](https://github.com/xiaobaiba999/skip-ad-subscription) · [问题反馈](https://github.com/xiaobaiba999/Skip-Ad/issues)

</div>

---

## 一屏速览

| | |
| --- | --- |
| **怎么工作** | 无障碍服务读屏找到「跳过」控件（文本/描述/控件 ID/图片角落键四通道），0ms 露头即点；**1:1 人手模拟触点**（人手参数域+人手复验节奏+dp 密度归一+实时人眼追踪+轨迹去重+同指纹目标去抖——不同设备上零误触零多点）；点击冷却 300ms · 倒计时将尽提前 0.38s 开火 |
| **拦截范围** | 开屏广告 · 插屏/激励弹窗 · 摇一摇诱导 · 悬浮广告球 · 视频前贴（134 款内置 + 任意 GKD 订阅扩展）· **信息流广告（朋友圈就业广告等 App 内广告——FeedAdGuard 两段式）** |
| **不碰什么** | 小程序/支付/团购容器零扫描零点击；银行/政务/相机零干预；只点应用自带「跳过」键（合规红线） |
| **隐私** | 数据仅存本机；联网仅日志上传（脱敏）与更新检查；零定位/通讯录/存储权限 |
| **体积** | APK 305KB · 冷启动毫秒级 · 典型日功耗 0.05mWh 级 |

## 当前版本 v6.2.0（2026-11-19）

1. **信息流广告卫士（FeedAdGuard · App 内广告识别跳过）**：微信朋友圈就业类广告等 App 内广告两段式关闭——P1 点卡片角上的「广告/就业」披露角标 → P2 点「关闭该广告」中性项收起；四道安全闸（角标词精确等值 · 面积门宽 ≤35% 屏×高 ≤8% 屏 · P2 只点中性项且须 P1 出手 8s 指纹窗 + 强特征≥1且总≥2 双闸 · 18 款信息流 App 名单门）——**绝不点广告主体**；微信窗级门只认 sns 族窗（聊天主界面零扫描红线保持）
2. **扫描日志六轮拓宽（命中跳过率继续提升）**：繁体零命中面（跳過廣告/關閉廣告/跳過福利等 8 条 + 主路径繁简双通）· 引导/福利类开屏变体补收 · SDK 窗指纹 +4 段（advertisement/offerwall/screenad/adsdialog，累计 50+）
3. **版本描述序号化**：版本记录 1 标题 + 1 段落；发现新版本时功能描述 ≤5 条且带序号（1. 2. 3.）展示
4. **9 品牌×26 = 286 机型八维矩阵（拓展设备型号与系统）**：11 组机型表 220→286（新增世代 HyperOS 3/HarmonyOS 5.1/MagicOS 10/ColorOS 16/OriginOS 6/One UI 8/OxygenOS 16）；新增第八维「信息流广告」——全仓 **3765 项 JVM 断言全绿（+907）**
5. **GitHub 同步 + Releases**：tag v6.2.0 · APK 305KB · 三目录分发 MD5 一致 · 面板描述同步更新（免责声明保持）

历史版本全部归档在 [docs/CHANGELOG.md](docs/CHANGELOG.md)；本轮优化实录：[docs/OPTIMIZATION-v6.2.0.md](docs/OPTIMIZATION-v6.2.0.md)。

## App 截图（真实 UI 截取）

<table>
<tr>
<td><img src="docs/screenshots/app-home.png" width="240" alt="首页：守护状态与战绩"/><br><sub><b>首页</b> — 守护状态 / 战绩 / 开关（极简版）</sub></td>
<td><img src="docs/screenshots/app-subscription.png" width="240" alt="订阅：市场与导入"/><br><sub><b>订阅</b> — 订阅市场 3 精选源（点一下即导入）/ GKD 导入</sub></td>
<td><img src="docs/screenshots/app-me.png" width="240" alt="我的：版本与日志"/><br><sub><b>我的</b> — 版本（1 标题 1 段落）/ 引擎日志（刷新+上传）</sub></td>
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
| 文本/vid 跳过键 | 「跳过/Skip/推广族/繁体族」+ viewId 双通道，0ms 首扫毫秒级代点 |
| **信息流广告（v6.2.0）** | **朋友圈就业广告等 App 内广告两段式关闭**：P1 点「广告/就业」角标 → P2 点「关闭该广告」收起；四道安全闸绝不点广告主体（18 款 App 名单 · 微信只认 sns 族窗）|
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
| GKD 订阅 | 粘贴任意 gkd.li 订阅链接导入；订阅市场一键导入活跃源 |
| 功耗治理 | 分级扫描预算 + 探针前置 + 息屏零唤醒，典型日 < 0.05mWh |

## 技术栈（定版）

**Java 管设备，TypeScript 管规则，脚本管流水线**——引擎层 Java 8 零依赖（冷启动比 Kotlin 快 40%、APK 比 Kotlin 方案小约 7×）；壳 UI 用 JS（V8）；规则台/CI 用 TS（与 GKD 订阅生态同构）。基准测试与裁决详见 [docs/LANGUAGE-STRATEGY.md](docs/LANGUAGE-STRATEGY.md)。

```
android-app/
├─ src/com/zai/adshield/        # Java 无障碍引擎（决策阶梯 L0-L6 · 纯 JVM 可测）
│  └─ core/                      # AdDecisionCore / RuleEngine / GkdSelector / RestoreGuard / HumanTap …
├─ assets/www/                   # JS 壳 UI（订阅市场防呆版 / 首页极简 / 版本描述精简版）
├─ tests/                        # 3765 项 JVM 断言（含 BrandFocus286Test 9 品牌×26 机型八维矩阵 + FeedAdGuardTest 信息流卫士 + HumanTapMatrixTest 1:1 人手矩阵 + TapPointMatrixTest 24 几何 × 12 场景触点矩阵）
└─ run-tests.sh                  # 零依赖测试跑器
```

## 测试

- **9 品牌 × 26 机型 = 286 机型（v6.2.0 拓展设备型号与系统）**：oppo/vivo/xiaomi/三星/华为/荣耀/红米/一加/iqoo 及其子品牌（realme/POCO）11 组 × 各 26 款真实机型（含折叠外屏/平板/720p 入门到 2K+ 旗舰，系统覆盖 HyperOS 2-3 · HarmonyOS 4.2-5.1 · MagicOS 8-10 · ColorOS 15-16 · OriginOS 5-6 · One UI 6.1-8 · OxygenOS 15-16）——开屏命中 / 人手触点 / **多点误点专项（同指纹去抖·新鲜坐标复核·结果静默）** / 屏蔽速度 / bug 防线 / 功耗 / 精准度 / 算法优化 / **信息流广告（v6.2.0 新增：朋友圈就业广告两段式）** **八维全 286/286**
- **1:1 人手模拟矩阵**：24 类真实设备几何 × **真实密度配对**（720p 遗留机 1.0~2.0 / QHD 旗舰 3.5~4.0 / 折叠内屏 2.0~2.6——真实世界设备组合）+ 全 7 档压力条件契约（可刺→安全落点 · 亚指腹→拒点）全绿
- **设备自适应触点矩阵**：24 类真实设备几何（直板/16:9 遗留/折叠内外屏/平板/分屏/刘海/挖孔/正方/横屏）× 12 误触场景（半裁剪/窗外/盲刺/轮换/内缩/覆盖层/不可见/形变死区/全链路/dp 安全域/高密度收紧/人手抖动不出界）全绿
- 二十八轮自测轨迹（1060+ 款语料 · 诚实模型）见 [docs/test-reports/summary.md](docs/test-reports/summary.md)
- 每轮报告：`docs/test-reports/round-N.json`（最新：[round-28](docs/test-reports/round-28.json) · 3765 断言全绿）

## 安装

1. [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases/latest) 下载 APK（305KB）直接安装；
2. 开启无障碍服务（系统会弹 10 秒安全确认，属正常防诈设计——开启一次后 App 内/通知栏即可控制，绝不再去系统设置）；
3. 常被清理后台的机型（HyperOS 等）：App 首页「保活体检」只显示待处理项，点「去设置」逐项确认。

同包名同签名覆盖安装即升级，无障碍授权与学习数据全部保留。

## 合规与边界

- 仅代点应用自带的「跳过」控件，不屏蔽广告内容本身（参考「拦精灵」案司法边界）
- 信息流伪装内容类「软广」（无披露角标）不处理——v6.2.0 起仅处理带「广告/就业/招聘」披露角标的信息流广告（两段式关闭，绝不点广告主体）
- 权限审计：仅网络/通知/前台服务/开机自启/电池白名单（v6.1.0 起无任何需 ADB/电脑授权项，全功能纯手机内可完成）

## 免责声明

> **本项目仅供个人学习、研究与合法的自用场景，按「现状」提供，不作任何明示或默示的担保。**

- **使用者责任**：本工具利用系统无障碍能力代点应用界面的「跳过」控件。使用者应为自身使用行为负责，自行确认并遵守所在地法律法规、以及所使用应用的服务条款（部分应用可能将自动化操作视为违规行为）。
- **不作担保**：本项目不担保在任何设备、任何系统版本上的持续可用性、稳定性或适用性；因广告 SDK 更新、系统行为差异等导致的任何失效、误点、数据损失、账号风控或其他直接/间接损失，本项目及贡献者不承担责任。
- **非商业用途**：本项目非商业产品，不以盈利为目的；如你将本代码用于商业用途或二次分发，由此产生的一切风险与责任由你自行承担，并请保留本免责声明。
- **合规红线内置**：项目技术上已内置合规边界（仅点自带「跳过」键、银行/政务/支付零干预、容器零扫描），但不构成对合规性的法律建议。
- **数据与隐私**：所有本地数据仅存本机；日志上传默认脱敏（手机号/身份证/邮箱/IP 四类），如你不希望任何上传，可在「我的 → 引擎日志」中拒绝；关闭日志上传不影响任何拦截功能。

## 许可

MIT · 欢迎自由 fork 与二改（分发时请随附本免责声明）
