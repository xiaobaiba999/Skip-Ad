# 开屏卫士（AdShield）· 源码与构建工具

移动端「开屏广告 + 跳转广告 + 弹窗广告」智能跳过系统。v3.3.0 APK 约 61KB，零第三方依赖；联网仅两处出口（问题反馈 GitHub Issues / 检查更新 GitHub Releases，v3.3.0 起检查更新带令牌 + 重试 + 列表回退，下载直连失败自动切换镜像通道），学习数据仍全部本地保存。

> **v3.8.0 要点**：手势真实触点首选（performAction 首点被广告 SDK 无声吞掉——「点击 4 次不跳转」根因，GKD clickCenter 方法论）· 假跳过手势直点（「跳过」文案+大容器祖先→手势点标签坐标）· vid 跳过键（skip/tt_splash_skip_btn/count_down，GKD 全局规则同款）· visibleToUser 过滤 · 倒计时门控（「跳过 3」将尽 0.5s 前出手即命中）· 候选排序真键优先 · 角落键先于 SDK 直断（500→700ms）· 无障碍自愈重绑（adb 授权 WRITE_SECURE_SETTINGS 后清理后台/覆盖安装全自动恢复；未授权 Android 11+ 一步直达服务详情页）· 心跳精确闹钟自续期 · JVM 100 用例全绿。
>
> **v3.4.0 要点**：角落图片跳过键识别（650ms 手势点击）· 坐标级点击兜底（标准跳过位 + 六重防误点）· performAction 失效降级 dispatchGesture 真实触点 · 多窗口扫描 · 决策核心 AdDecisionCore 纯 Java JVM 38 用例（scripts/device-tests，javac+java 直跑，发版前必绿）。
>
> **v3.3.0 要点**：SDK 广告窗 className 识别（零文本广告 500ms 直断，喜马拉雅类根因修复）· 全屏热区直断 · 陀螺仪诱导四层防线 · 链式回弹名单扩充（微信/支付宝/浏览器）· 弹窗广告 v2（窗期独立 + 覆盖层突发检测）· 应用内更新链路修复（v3.2.0 曾因发版脚本仓库名拼写错误未发布成功，真机一直停留在 v3.1.0）。

## 一、目录说明

```
android-app/
├── AndroidManifest.xml          # 应用清单（versionCode/versionName 在此注入）
├── src/com/zai/adshield/
│   ├── MainActivity.java        # WebView 壳 + JSBridge（版本/存储/无障碍设置/联网桥）
│   ├── AdSkipService.java       # 无障碍智能跳过服务（调度 + 执行：手势点击/返回键/多窗口）
│   ├── core/AdDecisionCore.java # 决策核心（纯 Java 零 Android 依赖，JVM 可单测）
│   └── Net.java                 # GitHub API 极简客户端（反馈 / 检查更新，零依赖）
├── res/                         # 布局/字符串/主题/无障碍配置/图标
├── assets/www/                  # 离线页面（首页/情报站/学习中心/关于；首页无任何演示模块）
│   （src 下另有 Downloader.java  # 系统下载器封装：应用内页内直更 + 多通道切换）
├── keystore/adshield.keystore   # 签名密钥（密码 adshield2026，请妥善保管）
└── build.sh                     # 一键构建脚本（本地/CI 通用，支持环境变量定位工具链）
```

## 二、安装到手机

1. 手机浏览器打开仓库 Releases 页下载最新 APK（推荐）：https://github.com/xiaobaiba999/Skip-Ad/releases
2. 点击 APK → 系统提示「允许安装未知应用」→ 允许 → 安装
3. 打开「开屏卫士」→ 首页点「开启无障碍服务」→ 设置 → 无障碍 → 已下载的应用 → 开屏卫士·自动跳过服务 → 开启
   - Android 13+ 侧载应用首次开无障碍可能提示「受限设置」：应用信息 → 右上角菜单 → 允许受限设置，再回无障碍页开启

## 三、验证覆盖升级（无需卸载）

1. 先安装 `adshield-v1.0.0.apk`（versionCode=1），随便用一用
2. 直接再安装 `adshield-v1.1.0.apk`（versionCode=2）→ 系统会显示「更新应用」而非要求卸载
3. 打开应用 → 「关于」页应显示 v1.1.0，且学习中心的数据保留

原理：两版使用同一 keystore 签名 + versionCode 递增，满足 Android 覆盖安装条件。

## 四、验证正常卸载

系统设置 → 应用管理 → 开屏卫士 → 卸载（或长按桌面图标 → 卸载）。
本应用是普通用户应用，无设备管理器/Profile Owner 等锁定机制，可正常卸载；卸载会清除本地学习数据（建议先在学习中心导出备份）。

## 五、迭代新版本

```bash
# 前置：android-tools/（build-tools 34 + android-34 + jdk）已就位（或用环境变量指向任意工具链）
cd android-app
./build.sh 10400 1.4.0 dist/adshield-v1.4.0.apk   # versionCode 规则：major*10000+minor*100+patch
```

推荐启用 GitHub Actions 自动发版（工作流文件在仓库 `docs/github-actions/`，网页端一次性启用后生效）：

```bash
git tag v1.4.0 && git push origin v1.4.0   # 云端自动构建 + 签名 + 发布 Release 附 APK
```

要点：
- **必须**递增 versionCode（第 1 个参数），否则手机上会提示"无法降级安装"
- **必须**使用同一 `keystore/adshield.keystore` 签名（签名不一致会要求先卸载）
- 改代码位置：Java 在 `src/`，界面在 `assets/www/`（纯 HTML/CSS/JS，无需编译）
- CI 环境变量：`ANDROID_BUILD_TOOLS` / `ANDROID_PLATFORM_JAR` / `ANDROID_JDK`（或 `ANDROID_TOOLS` 根目录）

## 六、真机测试清单

1. 安装 v1.0.0 → 开启无障碍 → 打开 2~3 个带开屏广告的应用（电商/资讯类效果最明显），手动点几次「跳过」
2. 回到开屏卫士 → 学习中心 → 应能看到每个应用的规则（文本/viewId/手动次数）
3. 再次打开同一应用 → 观察是否自动跳过；引擎日志会记录「⚡ 已自动点击…」
4. 演练场：选择 4 种策略体验冷启动学习 → 置信度提升 → 自动出手的完整闭环
5. 安装 v1.1.0 覆盖升级 → 数据保留验证
6. 正常卸载验证

## 七、智能学习引擎工作原理

```
冷启动：通用规则（开屏12s时间窗内，找含「跳过/skip」的小面积可点击控件）
   ↓
观察：捕获你手动点击「跳过」的事件（TYPE_VIEW_CLICKED）
   ↓
学习：记录该应用跳过控件的文本 + viewId → 生成专属规则
   ↓
行动：下次打开该应用，优先按 viewId 精确命中，毫秒级自动点击
   ↓
安全阀：面积<35%屏面积（防摇一摇/全屏热区误触）· 点击冷却1.5s · 时间窗12s · 只点「跳过」类控件
```

v1.2.0 新增「精准判断引擎」：

- 演练场：广告主体点击先经多信号裁决（按压 <40ms？无轨迹？展示后 <150ms 即点？），
  区分「广告诱导跳转」与「用户主动操作」；误判可一键纠正，纠正 ≥2 次自动放宽门槛（60→74）。
- 真实服务：新增「疑似误触」观察统计（全屏热区 / 窗口切换后 700ms 内的异常点击），
  只记录不拦截——与「拦精灵」案边界一致：不屏蔽广告本身。

v1.3.0 新增联网能力（联网仅两出口）：

- **问题反馈**：首页「问题反馈」卡片，选择类型 + 描述 → 自动附带引擎画像（版本/规则数/最近日志，
  不含个人数据）→ 一键同步 GitHub Issue；「高级」里可配置个人 GitHub Token（仅存本机）实现全自动提交，
  未配置则生成浏览器预填页一键手动提交。
- **检查更新**：「关于」页一键查询 GitHub Releases 最新版，版本比对后提醒下载；APK 直链下载，
  同签名直接覆盖升级。历次版本均发布在 Releases，旧版 App 内即可完成升级。

## 八、合规与安全

- 只「替用户点击跳过按钮」，不屏蔽广告本身（与 2024「拦精灵」案法院裁判边界一致）
- 联网仅两处出口（GitHub Issues 反馈 / GitHub Releases 检查更新），学习数据全部本地存储（SharedPreferences），卸载即清除
- 无障碍权限较敏感：使用支付/银行/政务类应用前建议关闭学习总开关
- 仅供个人学习研究；跳过开屏广告会减少开发者收入，请自行权衡使用范围
