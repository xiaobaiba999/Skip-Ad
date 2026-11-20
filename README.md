<div align="center">

# Skip-Ad · 开屏卫士

**安卓开屏广告秒跳引擎 —— 露头就跳（首击零门控）+ 视频开屏卫士（带视频的开屏广告不再全漏：视频画像 → 观察窗 12s→20s + 1.5s 轮询针 + 白点节流/新键预算刷新）、插屏重扫路由根治（App 内插入广告主动关闭：路由黑洞解除 + 延后复扫 + 语境三路并集）、弹窗自动关、白名单全量应用列表、1:1 人手模拟触点、C 语言基准实测（五语言同构）**

[![Releases](https://img.shields.io/github/v/release/xiaobaiba999/Skip-Ad?style=flat-square&label=%E6%9C%80%E6%96%B0%E5%8F%91%E5%B8%83)](https://github.com/xiaobaiba999/Skip-Ad/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](#许可)
[![APK](https://img.shields.io/badge/APK-832KB-orange?style=flat-square)](https://github.com/xiaobaiba999/Skip-Ad/releases/latest)
[![Tests](https://img.shields.io/badge/JVM%E6%96%AD%E8%A8%80-5099%20%E2%9C%85-brightgreen?style=flat-square)](docs/test-reports/round-39.json)
[![Matrix](https://img.shields.io/badge/%E6%9C%BA%E5%9E%8B%E7%9F%A9%E9%98%B5-9%E5%93%81%E7%89%8C%E5%8F%8A%E5%AD%90%E5%93%81%E7%89%8C36%E6%9C%BA%E5%9E%8B-396-blue?style=flat-square)](android-app/tests/com/zai/adshield/test/BrandFocus396Test.java)
[![HumanTap](https://img.shields.io/badge/1%3A1%E4%BA%BA%E6%89%8B%E6%A8%A1%E6%8B%9F-%E8%AF%AF%E7%82%B9%E5%A4%9A%E7%82%B9%E6%A0%B9%E6%B2%BB-ff7043?style=flat-square)](android-app/tests/com/zai/adshield/test/HumanTapMatrixTest.java)
[![Topic](https://img.shields.io/badge/topic-gkd--subscription-8e8e8e?style=flat-square)](https://github.com/topics/gkd-subscription)

Java 引擎 · GKD 订阅生态兼容 · [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) · [订阅仓库](https://github.com/xiaobaiba999/skip-ad-subscription) · [问题反馈](https://github.com/xiaobaiba999/Skip-Ad/issues)

</div>

---

## 一屏速览

| | |
| --- | --- |
| **怎么工作** | 无障碍服务读屏找到「跳过」控件（文本/描述/控件 ID/图片角落键四通道），0ms 露头即点；视频开屏画像（零候选连环+树静默 → 观察窗 20s + 轮询针）；**1:1 人手模拟触点**（人手参数域+人手复验节奏+dp 密度归一+实时人眼追踪+轨迹去重+同目标/结果双静默防线——不同设备上零误触零多点）；点击冷却 300ms · 倒计时将尽提前 0.38s 开火 |
| **拦截范围** | 开屏广告（含**视频开屏**——v7.8.0 视频卫士）· 插屏/激励弹窗 · 摇一摇诱导 · 悬浮广告球 · 视频前贴 · **App 内广告主动关闭（v7.4 信息流三路 20 款名单 + v7.5~v7.8 插屏卫士：语境三路并集（语境词/WebView 容器/SDK 插屏窗指纹）+ 重扫路由黑洞解除 + 延后复扫 + typeWindowsChanged 新窗挂载，名单外 App 全量兜底 + feed 名单内插屏孤儿化解除）**（134 款内置 + 任意 GKD 订阅扩展） |
| **不碰什么** | 小程序/支付/团购容器零扫描零点击；银行/政务/相机零干预；只点应用自带「跳过」键（合规红线） |
| **隐私与安全** | 数据仅存本机；联网仅日志上传（脱敏）与更新检查；零定位/通讯录/存储权限；更新包安装前 SHA-256 逐字节校验（镜像投毒/换包自动拦截）；订阅规则支付/诱导词双否决（注入防线） |
| **体积** | APK 832KB（含三源离线订阅快照 ~520KB）· 冷启动毫秒级 · 典型日功耗 0.05mWh 级 |

## 当前版本 v7.8.0（2026-11-20）· 视频开屏卫士 + 插屏重扫路由根治 + 机型矩阵 396（十维）

**第十八轮实锤驱动的双死症根治**（用户原话：「扫描最新日志进行修复，很多没命中，只要是开屏广告带视频的你都未命中，且app内插入广告也不会拦截关闭，卫士用c语言写的话会不会更好呢」）。①**视频开屏四死因链**：12s 观察窗天花板（视频开屏跳过钮常在视频末 5~15s 才露头，>12s 按钮引擎已出窗「不再扫」）+ 静默事件源（SurfaceView 视频播放零内容事件，零候选退避杀链后零事件零复活）+ 预算烧穿（图片键门控期 3 击 1 秒烧光预算，按钮真激活时引擎已优雅收手）+ L4 形态确认门（WebView 视频容器树 >150 被拒）——VideoSplashGuard 纯函数根治（画像驱动：观察窗 20s + 1.5s 轮询针 + 白点节流 + 新键预算刷新 + 树上限 260）+ 视频开屏键词典（「5s 跳过」无「后」变体收编）。②**插屏五死因链**：重扫路由黑洞（开屏窗期内容事件全改道 nudgeScan——插屏常在 App 打开后 3~5s 弹出恰在黑洞内，首扫一眼后整个生命周期无人再看）+ 首扫太早（窗切换帧树未长成空手后无复扫）+ 同窗插层无人扫（无焦点 Dialog/overlay 挂载不产生任何被订阅事件）+ 闸⓪语境面窄（H5 插屏角标在网页内核非原生节点、SDK 插屏窗类即铁证全被拒）+ 闸①②杀变体（拉丁 X/点击关闭/宽扁横条钮被双杀）——路由黑洞解除 + 延后复扫 +450ms + typeWindowsChanged 补订 + 语境三路并集 + 词族收编 + 宽扁放行 + 终闸一致性 + feed 名单孤儿化解除。

1. **视频开屏卫士**：视频画像（窗龄 ≥2.2s + 零候选连环 ≥3 + 树静默 ≥1.6s）→ 观察窗 12s→20s + 1.5s 轮询针（探针直查零 DFS——静默事件源兜底，12~20s 段 ≥5 针覆盖固定梯子盲区）+ 退避豁免
2. **视频窗预算根治**：白点节流 ≥1.5s/击 + 预算 3→5（拉长 ≥7.5s 覆盖门控期）+ 新键预算刷新（视频末露头真按钮 ≠ 白点）+ L4 形态门放宽（树上限 260/时机窗 20s）
3. **插屏重扫路由根治**：路由黑洞解除（开屏窗期/弹窗期内容事件同步拉起插屏扫描 · 节流 2.5s→1.5s）+ 延后复扫 +450ms + typeWindowsChanged 新窗挂载事件补订
4. **插屏闸扩容**：闸⓪语境三路并集（语境词 16 字上限 + WebView 容器铁证 + SDK 插屏窗指纹）+ 关闭词族收编（拉丁 X 单字符/点击关闭/立即关闭）+ 宽扁钮形态放行（≤55%×≤7% 横条钮）+ 终闸双杀解除 + feed 名单 20 款插屏孤儿化解除
5. **C 语言基准实测 + 全量测试**：五语言同构基准（校验和逐位一致：sel_sink=731/hits=20000/sink=485562）——C 稳态 261.8ns 快 Java 4.1× 但均为 µs 级（事件面 80ms 节流下 5 个数量级余量，语言性能对命中率零贡献；C 摸不到 AccessibilityNodeInfo 必须 JNI 回 Java——保持 Java）；23 套件 6063 项断言全绿（+964，新增 VideoSplashTest 51 + InterstitialRoutingTest 56 + 第十维视频开屏逐机型验证）

历史版本全部归档在 [docs/CHANGELOG.md](docs/CHANGELOG.md)；本轮实录：[round-40.json](docs/test-reports/round-40.json)（双死症死因链与五语言基准数据）。v7.7.0 回切开屏、v7.6.0 激进模式、v7.5.0 插屏卫士、v7.4.0 信息流三路全部保持。

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
| **信息流广告（v7.4 三路）** | 20 款 App 内广告：P0 原因面板「直接关闭」直通 · P1X 卡片「×/关闭」直关 · P1 角标 → P2 中性项收菜单（「直接关闭」置顶）——绝不点广告主体（宁漏勿误六道闸） |
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
├─ assets/www/                   # JS 壳 UI（订阅市场八通道+离线快照 / 检查更新带序号描述 / 白名单中文名搜索 / 使用报告）
├─ assets/subsnap/               # 三源离线订阅快照（导入兑底——永不失败）
├─ tests/                        # 4067 项 JVM 断言（含 BrandFocus308Test 9品牌+子品牌 308 机型八维矩阵 + FeedAdGuardTest + RuleOrderTest + HumanTapMatrixTest + TapPointMatrixTest + ReleaseNotesTest + SubMirrorTest）
└─ run-tests.sh                  # 零依赖测试跑器
```

## 测试

- **9 品牌 + 子品牌 11 组 × 32 机型 = 352 机型**（只测用户点名的品牌：OPPO/vivo/小米/三星/华为/荣耀/红米/一加/iQOO 及其子品牌 POCO/realme，其他品牌不测）：开屏命中 / 多点误点 / 屏蔽速度 / bug / 功耗 / 精准度 / 算法优化 / **信息流广告（v7.1 新增·v7.2 真机命中·v7.4 三路根治）** / **回切开屏（v7.7 新增：回切判定真值表/进入期全量门/主界面叠开屏层命中/插层键词典——用户实锤「从另一个 app 切回这个 app 期间会弹广告没有拦截到」）** **九维各 352/352**（含 2026-2027 新世代机型与 HyperOS 4~5/HarmonyOS 7~8/One UI 9~10 等新系统；信息流/回切维几何按机型分辨率 1:1 缩放逐机型全过）
- **1:1 人手模拟矩阵**：24 类真实设备几何 × **真实密度配对**（720p 遗留机 1.0~2.0 / QHD 旗舰 3.5~4.0 / 折叠内屏 2.0~2.6——真实世界设备组合）+ 全 7 档压力条件契约（可刺→安全落点 · 亚指腹→拒点）全绿
- **设备自适应触点矩阵**：24 类真实设备几何 × 12 误触场景全绿；**订阅镜像链与快照归一**（SubMirrorTest 35 项）+ **版本描述规范**（ReleaseNotesTest 30 项）+ **规则使用顺序治理**（RuleOrderTest 39 项）+ **信息流广告卫士**（FeedAdGuardTest 131 项·v7.4 三路根治扩容：P0 面板直通/P1X 卡片直关真值表 + 真机截图文案逐字树重建）
- 三十五轮自测轨迹见 [docs/test-reports/summary.md](docs/test-reports/summary.md)
- 每轮报告：`docs/test-reports/round-N.json`（最新：[round-39](docs/test-reports/round-39.json) · 5099 断言全绿 + ReentrySplashTest 回切专项 + 浏览器端 Toast 实测）

## 安装

1. [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases/latest) 下载 APK（832KB）直接安装（v7.x 任何版本覆盖安装即升级 v7.7.0——versionCode 77000，回切开屏根治 + Toast 常驻修复即刻生效）；
2. 开启无障碍服务（系统会弹 10 秒安全确认，属正常防诈设计——开启一次后 App 内/通知栏即可控制，绝不再去系统设置）；
3. 常被清理后台的机型（HyperOS 等）：App 首页「保活体检」只显示待处理项，点「去设置」逐项确认。

同包名同签名覆盖安装即升级，无障碍授权/学习数据/已导入订阅全部保留。

## 合规与边界

- 仅代点应用自带的「跳过」控件，不屏蔽广告内容本身（参考「拦精灵」案司法边界）；信息流广告只代点平台自带的披露角标、原因面板「直接关闭」主按钮、关闭菜单中性项与卡片「×/关闭」直关钮，绝不触碰广告主体
- 权限审计：仅网络/通知/前台服务/开机自启/电池白名单（免打扰持久模式已取消——零 ADB 零电脑）

## 免责声明

- 本工具仅代点应用**自带的「跳过」控件以跳过开屏/弹窗广告**，不拦截、不修改、不屏蔽广告内容本身，不采集广告数据；
- 需授权「无障碍服务」方能工作（用于读取屏幕控件与代点跳过键）——这是唯一的技术依赖，请从官方 Releases 下载本应用，避免二次打包版本滥用无障碍能力；
- 自动提交的引擎日志已双端双重脱敏（手机号/身份证/邮箱/IP），且仅含引擎运行指标与设备画像，不含任何页面内容或个人数据，可在设置中关闭；
- **安全边界（v7.3.0 披露）**：更新链已带 SHA-256 完整性校验（镜像投毒/换包自动拦截）；但为支撑「日志一日三次自动提交」与「检查更新」功能，APK 内嵌了本仓库专用 GitHub 令牌（仅用于 Issues/Releases 读写，混淆存储但可被逆向提取），且签名密钥随开源仓库公开——请只从本仓库 Releases 下载，同签名恶意重打包包络外的分发渠道无法被技术手段完全排除；
- **误触与漏拦边界（v7.5 起激进模式披露）**：当前版本按用户明确指令运行在「激进优先」档（宁可偶发误触也要保证拦到、快速跳过）——极少数非广告控件可能被误点（如个别「跳过」同形文案按钮）；进入期全量门（v7.7）也会在应用切回时刻多扫几轮。误触防线（同指纹静默/结果静默/人手耐心预算/多点防线）与合规红线（银行/支付/相机/验证码/小程序容器零干预）全部在场，如遇误触请通过「上传日志」反馈，附场景截图更便于定位；
- **回切开屏说明（v7.7.0）**：应用后台 ≥6s 再回前台时多数 App 会重弹开屏广告，本版本已按「新进入」全量拦截（含 CLEAN 档案让路）；个别 App 的开屏层若用非标准自绘控件（探针词与规则库均探不到）仍可能漏拦——扫描日志的「回切开屏/未命中」行即定位线索，欢迎反馈补充词库；
- 不同厂商 ROM 对无障碍服务与后台策略差异较大，保活体验因机型而异；遇到问题请通过 App 内「上传日志」反馈，附设备信息更便于定位；
- 本项目仅供学习交流与个人使用，使用者需遵守当地法律法规及所使用 App 的用户协议（部分 App 的用户协议限制自动化操作，使用本工具带来的账号风险由使用者自行评估）；广告是开发者的合法收入来源，本工具仅为个人跳过体验优化，请勿用于商业用途或批量分发；开源代码 MIT 许可，不提供任何明示或暗示的担保。

## 许可

MIT · 欢迎自由 fork 与二改
