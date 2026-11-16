# Skip-Ad · 秒跳引擎

> **开屏广告毫秒级秒跳 + 摇一摇/Deeplink 拦截 + 支付/银行/政务守护模式 + 习惯学习夜间同步。**
> v2.0.0 起为 Web App（PWA，可「添加到主屏幕」当独立 App 使用）；历次安卓真机版 APK 归档于 `android-app/dist/` 与 [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases)。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| **开屏秒跳** | 无障碍同源方案（李跳跳 / GKD 一脉）：控件树快照 → 「跳过/Skip」文本 + viewId 双通道定位 → `performAction(ACTION_CLICK)` 毫秒级代点，任何应用开屏 0.1s 内直达主页 |
| **摇一摇拦截** | 依据行业标准（加速度 ≥15m/s²、转角 ≥35°、持续 ≥3s）识别激进广告，传感器事件在引擎侧挂起——拿起手机、走路颠簸都不再触发跳转 |
| **Deeplink 断链** | 广告窗口内发起的 Scheme / Universal Link 一律拦截，连环二跳在系统层断开 |
| **守护模式** | 支付 / 银行 / 政务类应用照常拦截广告，但绝不终止应用进程与守护服务（温和代点 + 服务心跳保活） |
| **习惯学习** | 按应用 / 场景 / 时段沉淀使用画像；同一应用累计 2 次手动跳过自动生成专属秒跳规则，命中越多置信度越高 |
| **夜间同步** | 每晚 02:00（北京时间）自动把学习规则与画像提交到本仓库 `data/learning/`（latest + 按日归档），供后续版本迭代参考 |
| **内置反馈通道** | 演练场 / 情报站一键反馈直达 [Issues](https://github.com/xiaobaiba999/Skip-Ad/issues)；令牌以双层 XOR+Base64 混淆内置于服务端，前端零输入零接触 |

## 界面（v2.0.0 精简为四板块）

- **演练场**：手机模拟器 × 6 大场景（电商标准开屏 / 游戏摇一摇 / 银行·政务·支付守护 / 视频 Deeplink），毫秒级判定流水线可视化，支持「对照模式」体验未防护的原生广告痛点
- **情报站**：拦截统计、市场套路 × 秒跳反制（2026-11 调研）、规则库（预置 + 学习，可启停）、市面防线对比
- **学习中心**：使用习惯画像（应用 / 时段 / 场景）、学习规则、夜间同步状态与手动触发、学习事件流
- **关于**：一句话介绍 + 最近三个版本

## 快速开始

```bash
bun install            # 或 npm install
bunx prisma db push    # 初始化 SQLite（首次启动自动播种 12+ 条预置规则）
bun dev                # http://localhost:3000
```

手机使用：浏览器打开 → 菜单「添加到主屏幕」（PWA，manifest + 图标已内置）。

## 技术栈与结构

- Next.js 16（App Router）+ Prisma + SQLite + Tailwind CSS v4 + shadcn/ui，`bun` 包管理
- 广告素材纯 CSS/SVG（六主题：电商 / 游戏 / 会员 / 理财 / 政务 / 支付），零图片依赖

```
src/
├─ instrumentation.ts        # 启动钩子：规则播种 + 夜间同步调度（每 5 分钟检查，02:00 执行）
├─ lib/
│  ├─ credentials.ts         # 内置令牌（双层 XOR+Base64 混淆，仅服务端）
│  ├─ github.ts              # Issues / Contents / Releases 三出口
│  ├─ instant-engine.ts      # 秒跳引擎：多信号 + 毫秒级流水线规划
│  ├─ scenario-apps.ts       # 六大演练场景
│  ├─ preset-rules.ts        # 预置规则库（市场调研同源）
│  ├─ learning.ts            # 拦截/漏拦截记录、画像聚合、学习规则生成
│  └─ sync.ts                # 夜间同步 GitHub（data/learning/*.json）
├─ components/               # arena / intel-station / learning-center / about / feedback-dialog
└─ app/api/                  # block-events / learning(+sync) / rules / stats / feedback
```

## 合规与边界

- 仅代点应用自带的「跳过」控件，不屏蔽广告内容本身（参考「拦精灵」案司法边界）
- 信息流伪装内容类「软广」不处理
- 本仓库 Web 端为引擎演示与调度环境；真机拦截依赖系统无障碍权限（历史 APK 见 `android-app/`，v1.x 已归档）

## 版本

- **v2.0.0**（2026-11-16）：秒跳引擎重构——开屏秒跳、摇一摇/Deeplink 拦截、守护模式、内置加密反馈、学习夜间同步、界面精简
- v1.3.0：联网能力（Issues 反馈 / Releases 检查更新）
- v1.2.0：判断引擎与习惯学习（按压/轨迹/反应时间多信号）
