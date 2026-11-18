<div align="center">

# 开屏卫士 · Skip-Ad

**无障碍驱动的安卓开屏广告秒跳引擎 —— 零联网 · 免 Root · 0.3MB**

[![Release](https://img.shields.io/github/v/release/xiaobaiba999/Skip-Ad?include_prereleases&style=flat-square&logo=github&color=%2324c49e)](https://github.com/xiaobaiba999/Skip-Ad/releases)
[![APK Size](https://img.shields.io/badge/APK-292KB-24c49e?style=flat-square&logo=android)](https://github.com/xiaobaiba999/Skip-Ad/releases)
[![License](https://img.shields.io/badge/License-个人学习-blue?style=flat-square)](docs/GKD-COMPAT.md)
[![Tests](https://img.shields.io/badge/JVM%E6%96%AD%E8%A8%80-475%20%E2%9C%85-brightgreen?style=flat-square)](android-app/run-tests.sh)

**中文** · [Releases 下载](https://github.com/xiaobaiba999/Skip-Ad/releases) · [版本归档](docs/CHANGELOG.md) · [语言策略报告](docs/LANGUAGE-STRATEGY.md)

</div>

---

## 它做什么

打开任意 Android App，开屏广告出现的瞬间自动点掉「跳过」键——**只点应用自己提供的跳过控件**，不屏蔽广告内容本身。

| | |
| --- | --- |
| **极速链秒跳** | 窗口切换 0ms 首扫 → 文案五层 + viewId 双通道定位 → 毫秒级代点 |
| **零文本广告** | SDK 广告窗指纹（穿山甲/优量汇/快手…）500ms 直断；角落图片跳过键手势直点 |
| **不乱点** | 形状门 + 竖列菜单门 + 危险词黑名单 + 小程序容器五层纵深守卫（抖音团购券/支付宝 XRiver 不误伤） |
| **GKD 生态** | 粘贴 gkd.li 社区订阅链接即导入，选择器语法兼容 |
| **保活自愈** | 全品牌厂商直达体检 + 15s 自适应拉活 + 登记吊销遥测 |
| **能耗治理** | 息屏休眠 / 心跳窗口化 / 学习画像夜间沉淀 |

## 下载安装

1. 从 [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) 下载最新 `adshield-v5.x.x.apk`（**292KB**）
2. 安装并授予无障碍权限（应用内有全品牌直达引导）
3. 完成。同签名覆盖安装即升级，授权无缝保留

## 架构与语言策略（v5.6.0 定版）

> 完整分析（为什么同类产品都用 Kotlin、我们要不要重构、四语言基准数据）见 **[docs/LANGUAGE-STRATEGY.md](docs/LANGUAGE-STRATEGY.md)**

```
┌─ Android 引擎（发布物 = APK, 292KB）──────────────────┐
│  Java --release 8        ← 无障碍服务/规则匹配/决策/保活  │
│  └─ 壳 UI：HTML/CSS/JS（WebView，改 UI 不动引擎）        │
├─ Web 规则台（PWA）───────────────────────────────────┤
│  TypeScript + Next.js 16 ← 演练场/情报站/订阅工具链      │
│                          （与 GKD 订阅生态同构）          │
├─ 流水线（不进任何发布物）─────────────────────────────┤
│  Shell / Python / mjs    ← CI · 发布 · 图标 · 归档        │
└──────────────────────────────────────────────────────┘
```

**一句话：Java 管设备，TypeScript 管规则，脚本管流水线。**

为什么不是 Kotlin？——基准实测（同一棵 48 节点树、四语言校验和逐位一致、9 轮）：

| 语言 | 稳态匹配/树 | 冷启动 | 代价 |
| --- | ---: | ---: | --- |
| **Java（现状）** | **968 ns** | 55 ms | APK 292KB · 零运行时依赖 |
| Kotlin | 904 ns | 78 ms | +40% 冷启动 · stdlib +1.75MB → APK ≈7× |
| JS (V8) | 484 ns | 32 ms | 架构不可行：无障碍事件进不了 WebView |
| Python | 5,575 ns | 30 ms | 慢 6~20× · Android 无运行时 |

**Java ≈ Kotlin 稳态持平、重写零收益** → 不重构；转而把数据驱动的热路径优化做进 Java：整树匹配 **4,748 → 968 ns（4.9×）**。

## 快速开始

**手机用户**：直接装 APK（见上）。

**规则台 / 参与开发**：

```bash
bun install && bunx prisma db push && bun dev   # Web 控制台（PWA，可加到主屏幕）
cd android-app && ./run-tests.sh               # 引擎 475 项 JVM 断言
```

## 文档

- [语言策略与架构决策报告](docs/LANGUAGE-STRATEGY.md) —— Kotlin vs 多语言 · 基准数据 · 组合定版
- [版本归档 CHANGELOG](docs/CHANGELOG.md) —— 30 个版本完整要点
- [GKD 订阅兼容](docs/GKD-COMPAT.md) · [CI/CD 配置](docs/CICD-SETUP.md) · [自测报告](docs/test-reports/summary.md)

## 合规与边界

- 仅代点应用自带的「跳过」控件，不屏蔽广告内容（参考「拦精灵」案司法边界）
- 信息流伪装内容类「软广」不处理；支付/验证码/小程序容器零扫描零点击零学习
- 引擎零联网（订阅更新可选、强制 https、下载源域名白名单）

<div align="center">

**遇到漏拦截 / 误点？** [提个 Issue](https://github.com/xiaobaiba999/Skip-Ad/issues) —— App 内反馈面板一键直达，日志自动附版本与机型。

</div>
