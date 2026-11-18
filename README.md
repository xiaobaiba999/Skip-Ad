# Skip-Ad · 开屏卫士

> 无障碍秒跳引擎：开屏广告露头就跳、弹窗广告自动关、296KB APK 零依赖。
> Java 引擎（22 轮自测全绿）· GKD 订阅生态兼容 · 官方订阅同源同步。

**[下载 APK](https://github.com/xiaobaiba999/Skip-Ad/releases/latest) · [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) · [订阅仓库](https://github.com/xiaobaiba999/skip-ad-subscription) · [问题反馈](https://github.com/xiaobaiba999/Skip-Ad/issues)**

## 当前版本 v5.6.0（2026-11-18）

- **命中激进化**：SDK 广告窗指纹 +8 档（穿山甲 UnionSplash / 插屏 Interstitial / InsertScreen / Advertising…）、倒计时提前 0.2s 开火、同窗点击预算 6→8、内置规则库 102→**114 款** + 全局 vid 兜底
- **订阅市场**：默认停更订阅全面下架，换 **gkd-subscription 话题活跃源一键导入**；官方订阅 [skip-ad-subscription](https://github.com/xiaobaiba999/skip-ad-subscription)（subscription-template 构建，与内置规则库同源 v7 · 113 款 121 条）
- **10 秒警告终局**：前台看门狗自动拉活（退后台再进零操作）；可选「零打扰持久模式」——一次 ADB 授权后登记被吊销自动写回重绑，**不再进系统设置、不看 10 秒警告**
- **质量**：5 品牌 × 10 机型矩阵六维全绿（开屏命中/速度/bug/功耗/精准度/算法优化）· **1048 项 JVM 断言零回归** · 热路径 4.9× 提速

历史版本全部归档在 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

## 核心能力

| 能力 | 一句话 |
| --- | --- |
| 文本/vid 跳过键 | 「跳过/Skip」+ viewId 双通道，0ms 首扫毫秒级代点 |
| 第一方图片开屏 | 无字图片跳过键 → 角落小可点控件手势点击（形状门防误点） |
| SDK 广告窗 | className 指纹 30+ 段精确匹配 → 点击梯子 + 返回键直断兜底 |
| 弹窗广告 | 弹窗窗期独立、角标+关闭键、Dialog 形态安全门 |
| 摇一摇/翻转诱导 | 文案识别抢先关闭（不对抗用户真实跳转） |
| 容器保护 | 小程序/团购/支付容器（抖音/微信/支付宝 XRiver）内零扫描零点击 |
| 守护模式 | 银行/政务/支付：广告照拦，进程绝不杀 |
| 习惯学习 | 手动点一次的位置/指纹，次启 P0 预拦截（广告未及呈现） |
| GKD 订阅 | 粘贴任意 gkd.li 订阅链接导入；订阅市场一键导入活跃源 |
| 功耗治理 | 分级扫描预算 + 探针前置 + 息屏零唤醒，典型日 < 0.05mWh |

## 订阅

- **订阅市场**（App 内）：官方自维护订阅 + Lin-arm（⭐7382 日更）/ ganlinte（610 APP）/ MengNianxiaoyao / GK-Lemon-D / Ling033 一键导入
- **官方订阅**：`https://cdn.jsdelivr.net/gh/xiaobaiba999/skip-ad-subscription@main/dist/gkd.json5`（100% GKD 兼容，也可直接在 GKD 客户端导入）
- **更多订阅**：[github.com/topics/gkd-subscription](https://github.com/topics/gkd-subscription) 第三方订阅列表
- **自建订阅**：[gkd-kit/subscription-template](https://github.com/gkd-kit/subscription-template) 五分钟构建自己的远程订阅

## 技术栈（定版）

**Java 管设备，TypeScript 管规则，脚本管流水线**——引擎层 Java 8 零依赖（冷启动比 Kotlin 快 40%、APK 比 Kotlin 方案小约 7×）；壳 UI 用 JS（V8）；规则台/CI 用 TS（与 GKD 订阅生态同构）。基准测试与裁决详见 [docs/LANGUAGE-STRATEGY.md](docs/LANGUAGE-STRATEGY.md)。

```
android-app/
├─ src/com/zai/adshield/        # Java 无障碍引擎（决策阶梯 L0-L6 · 纯 JVM 可测）
│  └─ core/                      # AdDecisionCore / RuleEngine / GkdSelector / RestoreGuard …
├─ assets/www/                   # JS 壳 UI（订阅市场 / 持久守护 / 保活体检）
├─ tests/                        # 1048 项 JVM 断言（含 BrandMatrix50Test 5×10 机型矩阵）
└─ run-tests.sh                  # 零依赖测试跑器
```

## 测试

- **5 品牌 × 10 机型**（小米/华为/OPPO/vivo/三星 × 各 10 款）：开屏命中 / 屏蔽速度 / bug 防线 / 功耗 / 精准度 / 算法优化 **六维各 50/50**
- 二十二轮自测轨迹（1060 款语料 · 诚实模型）见 [docs/test-reports/summary.md](docs/test-reports/summary.md)
- 每轮报告：`docs/test-reports/round-N.json`

## 安装

1. [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases/latest) 下载 APK（296KB）直接安装；
2. 开启无障碍服务（系统会弹 10 秒安全确认，属正常防诈设计——开启一次后 App 内/通知栏即可控制，绝不再去系统设置）；
3. 常被清理后台的机型（HyperOS 等）：完成 App 首页「保活体检」三件套；追求零打扰可开启「持久守护」（一次 ADB 授权）。

同包名同签名覆盖安装即升级，无障碍授权与学习数据全部保留。

## 合规与边界

- 仅代点应用自带的「跳过」控件，不屏蔽广告内容本身（参考「拦精灵」案司法边界）
- 信息流伪装内容类「软广」不处理
- 权限审计：仅网络/通知/前台服务/开机自启/电池白名单（可选 WRITE_SECURE_SETTINGS 需用户 ADB 明示授权）

## 许可

MIT · 欢迎自由 fork 与二改
