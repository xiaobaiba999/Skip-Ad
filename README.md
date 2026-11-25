<div align="center">

<img src="android-app/assets/www/mascot.png" width="140" alt="开屏卫士吉祥物"/>

# 开屏卫士 · Skip-Ad

**安卓开屏广告「露头就跳」引擎 —— 只点应用自带的「跳过」键**

开屏 · 插屏 · 信息流 · 视频开屏 · 摇一摇诱导，全场景秒级代点；1:1 人手模拟触点，误触与多点根治；870KB 零依赖，冷启动毫秒级。

[![最新发布](https://img.shields.io/github/v/release/xiaobaiba999/Skip-Ad?style=flat-square&label=%E6%9C%80%E6%96%B0%E5%8F%91%E5%B8%83)](https://github.com/xiaobaiba999/Skip-Ad/releases/latest)
[![APK](https://img.shields.io/badge/APK-870KB-orange?style=flat-square)](#-下载最新版)
[![测试](https://img.shields.io/badge/JVM%E6%96%AD%E8%A8%80-4530%20%E2%9C%85-brightgreen?style=flat-square)](#-质量与兼容)
[![机型矩阵](https://img.shields.io/badge/%E6%9C%BA%E5%9E%8B%E7%9F%A9%E9%98%B5-9%E5%93%81%E7%89%8C352%E6%9C%BA%E5%9E%8B-blue?style=flat-square)](#-质量与兼容)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](#-许可)

[Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) · [更新日志](docs/CHANGELOG.md) · [订阅仓库](https://github.com/xiaobaiba999/skip-ad-subscription) · [问题反馈](https://github.com/xiaobaiba999/Skip-Ad/issues)

</div>

---

## ⬇️ 下载最新版

**当前版本 [v7.15.14](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v7.15.14)（2026-11-25）**：后台丢功能根治（死透快档闹钟+keepOn 解耦+营销 vid 证据）。APK `869,942` 字节 · SHA-256 `50779c63…b924a`（[完整值](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v7.15.14)）。

| 下载通道 | 说明 | 链接 |
| --- | --- | --- |
| **jsDelivr CDN 直链** | 国内推荐，直出仓库归档（与 Release 资产同字节） | [`adshield-latest.apk`（始终最新）](https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-latest.apk) · [本版直链](https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.15.14.apk) · [testingcf 备用](https://testingcf.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-latest.apk) |
| **ghfast.top 镜像** | GitHub Release 资产加速 | [下载 adshield-v7.15.14.apk](https://ghfast.top/https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.15.14/adshield-v7.15.14.apk) |
| **gh-proxy.com 镜像** | GitHub Release 资产加速 | [下载 adshield-v7.15.14.apk](https://gh-proxy.com/https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.15.14/adshield-v7.15.14.apk) |
| **GitHub Releases** | 官方主源 | [releases/latest](https://github.com/xiaobaiba999/Skip-Ad/releases/latest) |

- 已装 v7.x 任何正式版：**直接覆盖安装即升级**（签名一致——无障碍授权、学习数据、订阅全部保留）；
- 新安装：下载 → 允许「安装未知应用」→ 安装 → 首页开启无障碍服务；
- 任一通道下载的 APK 与发布资产**同字节、同 SHA-256**，安装前 App 会自动校验。

> **应用内「检查更新」失败？** 不用等——直接用上表任一直链在浏览器下载后覆盖安装即可（详见 [常见问题](#-常见问题)）。

## 🎯 一屏速览

| | |
| --- | --- |
| **怎么工作** | 无障碍服务读屏定位「跳过」控件（文本 / viewId / SDK 窗指纹 / 角落图片键四通道），**0ms 露头即点**；每窗唯一广告任务 + 不可逆成功终态（关掉即封手——重复点击/误触根治）；倒计时将尽提前 0.38s 开火 |
| **拦什么** | 开屏（含视频开屏 20s 观察窗画像）· 插屏/激励弹窗 · 信息流广告三路主动关闭 · 摇一摇/翻转诱导 · 悬浮广告球 · 视频前贴；134 款内置规则 + GKD 订阅每日自动导入（生态覆盖 600+ 款） |
| **不碰什么** | 小程序/支付/团购容器零扫描零点击；银行/政务/相机零干预；只点应用**自带的「跳过」键**（合规红线） |
| **隐私** | 数据仅存本机；零定位/通讯录/存储权限；更新包安装前 SHA-256 逐字节校验；APK 零明文凭据 |
| **成本** | APK 870KB（含三源离线订阅快照）· 冷启动毫秒级 · 典型日功耗 0.05mWh 级 |

## 📱 截图（真实 UI）

<table>
<tr>
<td><img src="docs/screenshots/app-home.png" width="240" alt="首页：守护状态与战绩"/><br><sub><b>首页</b> — 守护状态 / 战绩 / 开关 / 应用白名单</sub></td>
<td><img src="docs/screenshots/app-help.png" width="240" alt="帮助：快速上手与守护加固"/><br><sub><b>帮助</b> — 快速上手 / 让守护更稳 / 常见问题</sub></td>
<td><img src="docs/screenshots/app-me.png" width="240" alt="我的：版本与日志"/><br><sub><b>我的</b> — 检查更新 / 版本记录 / 使用报告 / 引擎日志</sub></td>
</tr>
</table>

<sub>底栏三 Tab：首页 / 帮助 / 我的；订阅后台自动导入，无手动操作。截图为 App 实际 WebView UI 渲染，战绩数字为示例。</sub>

## 🚀 快速上手

1. **安装**：从上方任一通道下载 APK，覆盖安装或全新安装；
2. **开启无障碍**：App 首页一键引导（系统弹 10 秒安全确认属正常防诈设计，开启一次即可）；
3. **完成**：打开任意 App，开屏广告露头即跳。常被清理后台的机型（HyperOS 等）跑一遍首页「保活体检」。

## 🧠 核心能力

| 能力 | 说明 |
| --- | --- |
| 四通道跳过键识别 | 「跳过/Skip/推广族」文本 · viewId · SDK 广告窗 className 指纹（穿山甲/优量汇/Unity/IronSource/Mintegral 等 50+ 段）· 角落图片键（形状门防误点） |
| 1:1 人手模拟触点 | 人手参数域（延迟/时长/微抖散布）+ 复验节奏 + dp 密度归一 + 实时人眼追踪——不同设备/分辨率零误触零多点 |
| 广告任务状态机 | 每窗唯一任务；第一次成功后旧任务拒收一切点击（重复点击·误触根治）；失败仍安全有限重试（重试前重验广告/候选/安全闸） |
| 设备自适应触点 | 可见交集 + 窗口钳制 + 象限轮换 + 覆盖层 Z 序判定——分屏/折叠/键盘/悬浮窗全兼容 |
| 信息流广告三路 | P0 原因面板「直接关闭」直通 · P1X 卡片「×/关闭」直关 · P1 角标 → 中性菜单收口——绝不点广告主体 |
| 视频开屏卫士 | 静默画像归一 → 20s 观察窗 + 轮询针，等倒计时将尽才出手 |
| 恢复与回切 | 锁屏/解锁晚到键两针 + 横幅让路重排 + 前台回归探针（回切不发窗口事件的 ROM 整窗漏拦根治） |
| 功耗治理 | 分级扫描预算 + 探针前置 + 息屏零唤醒 + 插屏窗级退避（2.5s→10s→30s），典型日 < 0.05mWh |
| 习惯学习 | 手动点一次的位置/指纹，次启 P0 预拦截（广告未及呈现） |
| 容器与守护 | 小程序/团购/支付容器零干预；银行/政务/支付：广告照拦、进程绝不杀 |

## 🔗 订阅生态

- **零操作**：id667 的 GKD 订阅（Lin-arm ⭐7.4k · 日更）后台每日自动导入（八通道镜像 + APK 内置离线快照双兜底，失败 1h 冷却重试）；
- **官方订阅**：[gkd.json5](https://cdn.jsdelivr.net/gh/xiaobaiba999/skip-ad-subscription@main/dist/gkd.json5)（100% GKD 兼容，也可直接在 GKD 客户端导入）；
- **自建订阅**：[gkd-kit/subscription-template](https://github.com/gkd-kit/subscription-template) 五分钟构建，自建源在 GKD 客户端与本卫士生态共用。

## ✅ 质量与兼容

- **机型矩阵**：9 品牌 + 子品牌（OPPO/vivo/小米/三星/华为/荣耀/红米/一加/iQOO 及 POCO/realme）11 组 × 32 机型 = **352 机型**，九维各 352/352（开屏命中/多点误点/屏蔽速度/bug/功耗/精准度/算法优化/信息流广告/回切开屏）；
- **JVM 断言**：25 套件 **4530 断言**全绿（决策核心/规则引擎/触点矩阵/人手矩阵/订阅镜像/发布描述规范）+ 服务级 35 项行为场景（锁屏/回切/横幅/退避/更新存活链等真实时序复刻，双向验证：旧代码红/新代码绿）；
- **人手模拟矩阵**：24 类真实设备几何 × 真实密度配对 × 7 档压力条件契约全绿；
- 自测轨迹：[docs/test-reports/summary.md](docs/test-reports/summary.md)（三十五轮，每轮 `round-N.json`）。

## 🏗️ 技术架构

**Java 管设备，TypeScript 管规则，脚本管流水线**——引擎层 Java 8 零依赖（冷启动比 Kotlin 快 40%、APK 小约 7×）；壳 UI 用 JS；规则台/CI 用 TS（与 GKD 订阅生态同构）。选型基准见 [docs/LANGUAGE-STRATEGY.md](docs/LANGUAGE-STRATEGY.md)。

```
android-app/
├─ src/com/zai/adshield/     # Java 无障碍引擎（决策阶梯 L0-L6 · 纯 JVM 可测）
│  └─ core/                   # AdDecisionCore / RuleEngine / PowerGovernor / HumanTap …
├─ assets/www/                # JS 壳 UI（首页/帮助/我的）
├─ assets/subsnap/            # 三源离线订阅快照（导入兜底——永不失败）
├─ tests/                     # JVM 断言套件（BrandFocus308 / HumanTapMatrix / FeedAdGuard …）
└─ run-tests.sh               # 零依赖测试跑器
```

## ❓ 常见问题

<details open>
<summary><b>应用内「检查更新」失败 / 提示多源均不可达？</b></summary>

检查更新走 jsDelivr 四域 + GitHub 多源链，国内运营商对部分域名间歇不可达时应用内会失败。**三个自救路径（任选）**：

1. **直接浏览器下载**：用[上方下载区](#-下载最新版)任一直链（CDN 直链国内通常畅通），下载后覆盖安装；
2. **开 GitHub 加速器**（如 Watt Toolkit）后在应用内重试；
3. 到 [Releases 页](https://github.com/xiaobaiba999/Skip-Ad/releases/latest)手动下载。
</details>

<details>
<summary><b>检查更新显示「已是最新」，但 GitHub 已发新版？</b></summary>

jsDelivr 对 `@main` 分支有源站级缓存（最长 12h），期间备用链可能仍在旧清单——不操作也无损，≤12h 自愈。急用直链：`adshield-latest.apk` 始终指向最新版。
</details>

<details>
<summary><b>v7.15.0~v7.15.4 点下载报「下载链接不在允许的发布通道内」？</b></summary>

已根治：清单已撤 `cdnApkUrl` 字段，旧版重新「检查更新」拉到新清单后点下载即恢复（首渠道回到 ghfast.top 镜像）；仍失败就用直链手动安装。
</details>

<details>
<summary><b>v7.9.5/v7.9.6 检查更新报「GitHub 返回 401」？</b></summary>

这两个版本携带的内置令牌已被吊销（安全整改，旧版无法从服务侧修复）——直接用[下载区](#-下载最新版)任一链接覆盖安装（82000 > 79600 同签名，数据全保留）。
</details>

<details>
<summary><b>会不会误点、多点？</b></summary>

当前运行在「激进优先」档（宁可偶发漏拦保证拦到、快速跳过），误触防线（同指纹静默/结果静默/人手耐心预算/多点防线/派发失败退避）与合规红线（银行/支付/相机/验证码/小程序容器零干预）全部在场。如遇误触，App 内「上传日志」反馈并附场景截图。
</details>

<details>
<summary><b>耗电与保活？</b></summary>

典型日功耗 0.05mWh 级（分级扫描预算 + 息屏零唤醒 + 插屏窗级退避）；常被清理后台的机型用首页「保活体检」逐项确认。各厂商 ROM 策略差异较大，遇到问题上传日志反馈。
</details>

<details>
<summary><b>需要什么权限？</b></summary>

无障碍服务（读屏与代点的唯一技术依赖）+ 网络/通知/前台服务/开机自启/电池白名单。**零定位、零通讯录、零存储**；联网仅日志上传（双端脱敏，可关闭）与更新检查。
</details>

## ⚖️ 合规与边界

- 仅代点应用**自带的「跳过」控件**，不拦截、不修改、不屏蔽广告内容本身，不采集广告数据（参考「拦精灵」案司法边界）；信息流广告只代点平台自带的披露角标、原因面板「直接关闭」主按钮与关闭菜单中性项；
- 自动提交的引擎日志已双端双重脱敏（手机号/身份证/邮箱/IP），仅含引擎运行指标与设备画像，可在设置中关闭；
- 更新链带 SHA-256 完整性校验（镜像投毒/换包自动拦截）；v7.9.4 起 APK 零明文凭据；**请只从本仓库 Releases 或上述官方直链下载**（签名密钥随开源仓库公开为历史知情决策，二次打包渠道无法被技术手段完全排除）。

## 📄 免责声明

- 本工具仅代点应用自带的「跳过」控件以跳过开屏/弹窗广告，需授权「无障碍服务」方能工作——这是唯一的技术依赖；
- 本项目仅供学习交流与个人使用，使用者需遵守当地法律法规及所使用 App 的用户协议（部分 App 的用户协议限制自动化操作，使用本工具带来的账号风险由使用者自行评估）；
- 广告是开发者的合法收入来源，本工具仅为个人跳过体验优化，请勿用于商业用途或批量分发；开源代码 MIT 许可，不提供任何明示或暗示的担保。

## 📜 版本历史

当前版本亮点、每版死因考古与修复明细见 [docs/CHANGELOG.md](docs/CHANGELOG.md) 与 [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases)。

> **版本线说明**：应仓库所有者指令，v7.7.0~v7.14.0 的 Release 与 tag 已从仓库移除（版本线直接跳 v7.15.0），重构基线为 v7.6.0 激进模式版本；历史细节仍可在 CHANGELOG 与 git 历史中查证。

## 许可

MIT · 欢迎自由 fork 与二改
