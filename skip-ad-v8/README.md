# 开屏卫士 (Skip-Ad Guardian)

基于 [gkd](https://github.com/gkd-kit/gkd)（GPL-3.0-only）二次开发的开屏广告自动跳过应用。

通过高级选择器规则，在指定界面自动点击应用自身展示的「跳过 / 关闭」按钮，跳过开屏广告等烦人流程。

## 特性

- **开箱即用**：内置三个活跃维护的社区订阅规则（Lin-arm / aoguai / 甘霖），无需手动添加订阅，规则自动更新
- **gkd 引擎**：完整继承 gkd 的高级选择器引擎、规则分组与按应用管理能力
- **触发统计**：跳过记录与规则触发统计
- **日志报告**：一键上传脱敏诊断报告（GitHub Issues），可选每 8 小时自动上传
- **应用内更新**：多源更新清单（jsDelivr CDN / raw / gcore），APK 下载自动校验 SHA-256

## 免责声明

**本项目遵循 [GPL-3.0-only](/LICENSE) 开源，项目仅供学习交流，禁止用于商业或非法用途。**
本项目基于 [gkd-kit/gkd](https://github.com/gkd-kit/gkd) 二次开发，感谢原开发者 [@lisonge](https://github.com/lisonge) 的开源贡献。

内置订阅规则来自各自的社区维护者（[Lin-arm](https://github.com/Lin-arm/GKD_subscription) / [aoguai](https://github.com/aoguai/subscription) / [ganlinte](https://github.com/ganlinte/GKD-subscription)），规则质量问题请反馈至对应订阅仓库。

## 反馈

- 应用问题：<https://github.com/xiaobaiba999/kilo/issues>
- 规则适配：前往对应订阅仓库反馈

## 开发

- [应用架构与依赖](docs/architecture.md)
- [开发约束](AGENTS.md)
- 构建：`./gradlew :gkd-android:assembleGkdRelease`

## 衍生项目

上游 gkd 的衍生项目见 [gkd README](https://github.com/gkd-kit/gkd)。
