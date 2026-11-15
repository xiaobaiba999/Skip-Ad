# Skip-Ad · 开屏广告与跳转广告演示 + 智能防跳转引擎

> 移动端「开屏广告 / 跳转广告」全策略演示系统，附带**精准判断引擎**：
> 用多信号评分区分「广告诱导跳转」与「用户主动操作」，并通过智能学习持续优化屏蔽策略。
> 同时包含可直接安装到安卓真机的「开屏卫士」APK（无障碍智能跳过服务，58KB，联网仅用于问题反馈与检查更新）。
>
> 📱 真机安装包与历次迭代版本均在 [GitHub Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) 发布；
> 🐞 使用中遇到的问题可在 App 内一键反馈，自动同步为本仓库 [Issues](https://github.com/xiaobaiba999/Skip-Ad/issues)。

- Web 演示端：Next.js 16 + Prisma + SQLite + Tailwind CSS v4，纯 CSS/SVG 广告素材，零图片依赖
- Android 真机端：`android-app/`，aapt2 + javac + d8 极简工具链构建，无第三方依赖；联网仅两出口（GitHub Issues 反馈 / Releases 检查更新）
- 自动发版：推送 `v*` 标签即触发 GitHub Actions 云端构建 APK 并发布 Release（工作流文件见 `docs/github-actions/`，需网页端一次性启用，见第六节）
- 调研基准：2026-11 最新公开报道（澎湃新闻 20 款 App 实测、工信部 2026 通报与专题会、iOS 27.2 等）

---

## 一、快速开始

```bash
bun install                 # 或 npm install
bunx prisma db push         # 初始化 SQLite（首次访问自动播种 6 条演示广告）
bun dev                     # http://localhost:3000
```

手机安装（无需 Android Studio）：

```bash
# 方式 A（推荐）：手机浏览器打开 GitHub Releases 下载最新 APK
#   https://github.com/xiaobaiba999/Skip-Ad/releases
#   或在旧版 App「关于 → 检查更新」一键下载（同签名直接覆盖升级，无需卸载）

# 方式 B：本地安装真机版「开屏卫士」APK
adb install android-app/dist/adshield-v1.3.0.apk
# 或直接把 APK 传到手机点击安装（允许「安装未知应用」来源）

# 方式 C：浏览器打开演示页 → 菜单「添加到主屏幕」（PWA，manifest + 图标已内置）
```

## 二、功能总览

### Web 端（本仓库 `src/`）

| 模块 | 说明 |
| --- | --- |
| 2026 广告策略 | 开屏广告四种跳转策略：`standard` 标准 / `timer` 倒计时归零自动跳转 / `shake` 摇一摇传感器 / `anyclick` 全屏隐藏热区（「跳过」换成诱导按钮、假关闭按钮等套路见策略情报站） |
| 插屏跳转广告 | 强制观看 3s 后解锁关闭；点击主体同样经判断引擎裁决 |
| 落地页二跳 | 落地页倒计时自动唤起外部应用（URL Scheme），拦截模式下自动二跳被拦截，手动「立即打开」放行 |
| 精准判断引擎 | 按压时长 / 轨迹长度 / 反应时间 / pointer 事件四类行为指纹 + 触发源评分，≥60 分判「广告诱导跳转」 |
| 智能学习 | 跳过时机（≥3 样本→自动跳过）、主题停留偏好（秒退→拦截倾向，长停留→放行倾向）、误判纠正（≥2 次→门槛 60→74） |
| 三种护盾模式 | 关闭防护（原生广告体验）/ 学习模式（只记录）/ 拦截模式（拦截+放行+Toast 说明判定依据） |
| 投放后台 | 曝光/点击/CTR 看板、广告 CRUD、权重投放、**策略列**、**智能学习报告**（精准度、已学习规则、判定日志） |
| 策略情报站 | 2026-11 市场扫描：8 种广告套路 vs 反制手段 + 六路防线 + 资料来源 |

### Android 端（`android-app/`，开屏卫士 v1.3.0）

| 模块 | 说明 |
| --- | --- |
| 无障碍跳过服务 | `AdSkipService`：text/viewId 定位「跳过」控件 → 毫秒级代点；面积安全阀（<35% 屏面积，防全屏热区/摇一摇误触）、点击冷却、12s 时间窗 |
| 习惯学习 | 观察手动点击（TYPE_VIEW_CLICKED）→ 生成应用专属规则（文本 + viewId）→ 下次优先按 viewId 精确命中 |
| v1.2.0 判断引擎 | 演练场：按压/轨迹/反应时间多信号裁决，误判一键纠正（≥2 次自动放宽门槛 60→74）；真实服务新增「疑似误触」观察统计（全屏热区 / 窗口切换后 700ms 内异常点击），只记录不拦截 |
| v1.3.0 问题反馈 | App 内描述问题 → 自动附带引擎画像（版本/规则数/最近日志，不含个人数据）→ 一键同步 GitHub Issue；支持配置个人 Token 全自动提交，未配置则生成浏览器预填页 |
| v1.3.0 检查更新 | 「关于」页查询 GitHub Releases 最新版 → 版本比对提醒 → APK 直链下载（同签名覆盖升级）；浏览器预览模式亦可直接使用 |
| 数据安全 | 联网仅两出口（反馈 / 检查更新），学习数据全部本地 SharedPreferences，卸载即清除；规则可导出/导入 JSON 备份 |

## 三、2026-11 调研摘要

### 广告侧：最新跳转套路（均已在本项目中可复现）

| 套路 | 2026 实测证据 | 本项目对应 |
| --- | --- | --- |
| 高灵敏「摇一摇」 | 澎湃实测 20 款 App：部分灵敏到「拿起手机即跳转」；体感开屏单日刊例价比普通开屏高 50 万元 | `shake` 策略（模拟摇晃 / 真机 devicemotion） |
| 倒计时归零自动跳转 | 「拿起手机，广告自动完成跳转」 | `timer` 策略 |
| 全屏热区 / 跳过键变身 | 2026-04：部分 App 把「跳过/关闭」换成「立即进入/查看详情」；「取消」形同虚设（点取消仍照转） | `anyclick` 策略 + 插屏 CTA 误触 |
| 落地页二跳（URL Scheme 连环跳转） | 误触后经 deeplink 层层跳转 A→B→应用商店 | 落地页「二跳」演示（自动唤起外部应用） |

监管量化标准（2023 行业标准，2025-07 升级为《网络安全标准实践指南——摇一摇广告触发行为安全要求》）：
合法「摇一摇」须 **加速度 ≥15m/s²、双向转角 ≥35°、持续 ≥3s**；《互联网广告管理办法》（2023）要求一键关闭。

### 防护侧：六路防线

1. **无障碍代点**（GKD / 李跳跳 / 本项目）：读取控件树定位「跳过」→ 模拟点击；不碰网络、免 Root。局限：自绘控件与无跳过按钮的广告无效。
2. **系统权限治理**：iOS 27.2（2026-09，中国区独享）「限制运动数据」按应用切断加速度计/陀螺仪；华为、vivo 支持「仅开屏时禁止获取动作与方向」；收紧「关联启动」阻断二跳。
3. **DNS / 私人 DNS 过滤**：广告素材域名解析到黑洞。局限：与正文同域、已缓存素材拦不住。
4. **本地 VPN 分流**（Surge / Quantumult X）：DNS 分流 + 域名规则集。
5. **替代方案**：网页版替代完整 App / 付费去广告。
6. **监管投诉**：2026-04 市场监管总局互联网广告生态治理；2026-05-31 工信部通报 31 款 APP 及 SDK（7 款涉乱跳转）；2026-06 工信部专题会（约谈/通报/下架）。可 12321/12300 举报。

## 四、精准判断引擎设计（核心）

**目标：判断一次跳转是「广告诱导」还是「用户主动操作」。**

### 信号与权重（`src/lib/judge.ts`，Android 端 `assets/www/app.js` 为同规则镜像）

| 信号 | 广告行为（+） | 用户行为（−） |
| --- | --- | --- |
| 触发源 | 倒计时归零自动跳转 +88 / 传感器触发 +85 / 二跳自动唤起 +88 / 全屏热区 +68 / 假关闭按钮 +80 | 内容区导航 −50（语义即用户意图） |
| 按压时长 | <40ms（快于人手）+25 | 40~350ms 人工区间 −5；>350ms 沉稳长按 −12 |
| 移动轨迹 | 0px 瞬移点击 +15 | <6px 轻微抖动（真人触摸）−8；明显拖动 −15 |
| 反应时间 | 展示后 <150ms 即点 +20 | 观察 >600ms 后点击 −15 |
| pointer 事件 | 无 pointer 事件（纯程序点击）+30 | — |
| 学习偏好 | 主题秒退/取消二跳 → +4~+20 | 长时间停留 → −8~−20 |

判定：**总分 ≥60 → 广告诱导跳转（拦截）**；用户在拦截 Toast 上点「误判？这是我要的操作」≥2 次 → 门槛升至 74（防误伤）。

### 智能学习闭环（Web 端）

```
手动跳过开屏（≥3 样本）──→ /api/learn 聚合均值 ──→ 下次启动在「学习到的时机」自动跳过（GKD 式）
落地页停留反馈        ──→ 秒退(+拦截倾向)/长停留(+放行倾向) ──→ 影响后续主题判定分
误判纠正              ──→ 纠正≥2 次 ──→ 拦截门槛 60→74，放宽快速点击
所有判定/跳过/停留事件 → AdEvent 表（/api/events） → 学习报告实时聚合（精准度 = 1 − 纠正/拦截）
```

## 五、目录结构

```
├── prisma/schema.prisma        # Ad（含 2026 策略字段）+ AdEvent（学习事件）
├── src/
│   ├── lib/judge.ts            # 精准判断引擎（多信号评分）
│   ├── lib/pointer-tracker.ts  # 行为指纹采集 hook
│   ├── lib/ad-seed.ts          # 6 条种子广告（按名称 upsert + 策略对齐）
│   ├── app/api/{ads,stats,events,learn}/  # 投放/统计/埋点/画像聚合
│   └── components/             # 手机模拟器、开屏/插屏/落地页、后台、情报站
├── android-app/                # 「开屏卫士」真机版（Java + 离线 H5，58KB APK）
│   ├── src/.../AdSkipService.java  # 无障碍智能跳过 + 误触观察
│   ├── src/.../Net.java            # GitHub API 极简客户端（反馈 / 检查更新）
│   ├── assets/www/             # 演练场 / 情报站 / 学习中心 / 关于（离线页）
│   ├── keystore/adshield.keystore  # 签名密钥（覆盖安装必需，密码 adshield2026）
│   └── build.sh                # 一键构建（aapt2+javac+d8+apksigner，支持 CI 环境变量）
├── docs/github-actions/         # release.yml（标签→自动构建+发版）、ci.yml（lint）· 需网页端一次性启用，见第六节
├── public/manifest.webmanifest # PWA（手机浏览器可直接安装）
└── download/开屏卫士APK/        # v1.0.0 ~ v1.3.0 成品 + 源码zip
```

## 六、APK 构建 / 覆盖安装 / 卸载 / Releases 发版

### 本地构建

```bash
cd android-app
./build.sh 10300 1.3.0 dist/adshield-v1.3.0.apk
# versionCode 规则：major*10000 + minor*100 + patch（如 v1.4.1 → 10401），单调递增即可覆盖安装
# CI 环境用环境变量定位工具链：ANDROID_BUILD_TOOLS / ANDROID_PLATFORM_JAR / ANDROID_JDK
```

- **覆盖安装**：同一 `keystore/adshield.keystore` 签名 + versionCode 递增 → 手机上直接安装新版，无需卸载，学习数据全部保留（v1.0.0→v1.1.0→v1.2.0→v1.3.0 已验证，证书 SHA-256 `05ada6e3…`）
- **正常卸载**：普通用户应用，无设备管理器等锁定机制；卸载清除本地数据（建议先在学习中心导出备份）
- 工具链：`android-tools/`（build-tools 34 + android-34 + JDK）**不入库**（638MB），构建脚本通过 `ANDROID_TOOLS` 环境变量定位；仓库外自行准备即可

### GitHub Actions 自动发版（每次迭代）

```bash
git tag v1.4.0 && git push origin v1.4.0   # 推送 v* 标签即自动：云端构建 → 签名 → 创建 Release 并附 APK
```

- 流水线定义：`docs/github-actions/release.yml`（checkout → JDK 17 → SDK platform-34/build-tools 34.0.0 → 按标签算 versionCode → build.sh → apksigner 验证 → 发布 Release 附 APK）；`ci.yml` 在每次 push/PR 时跑 Web 端 ESLint + Android 离线页 JS 语法检查
- **一次性启用**（网页端操作，不受 token scope 限制）：
  1. 打开仓库 → Add file → Create new file → 文件名填 `.github/workflows/release.yml`
  2. 把 `docs/github-actions/release.yml` 的内容粘贴进去 → Commit（ci.yml 同理）
  3. 之后每次推送 `v*` 标签即自动构建发版；或在 Actions 页面 `workflow_dispatch` 指定标签补发
  > 若你的 PAT 含 `workflow` scope，也可直接 `git mv docs/github-actions/release.yml .github/workflows/ && git commit && git push` 完成启用
- 用户侧：手机「开屏卫士 → 关于 → 检查更新」或直接访问 [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases) 下载，安装即覆盖升级

## 七、路线图

- [ ] 端侧轻量模型（误触指纹特征聚类）替代手写权重
- [ ] Web 端多用户画像隔离（当前演示为单画像）
- [ ] 演练场增加「延迟跳过按钮」「极小关闭按钮」独立策略档位
- [ ] Capacitor 封装打包正式 APK（当前为原生 WebView 壳 + 极简工具链）

## 八、参考来源（2026-11 检索）

1. 澎湃新闻 / 21 世纪经济报道《实测 20 款 APP：体感开屏加价 50 万、误触也算点击，"拿起就跳转"背后藏着怎样的暴利》—— https://finance.sina.com.cn/roll/2026-11-09/doc-iniyazcy6373654.shtml
2. 《2026 年 App 开屏广告新套路事件》（跳过键变身「立即进入」；2026-05-31 工信部通报 31 款）—— https://baike.baidu.com/item/2026%E5%B9%B4App%E5%BC%80%E5%B1%8F%E5%B9%BF%E5%91%8A%E6%96%B0%E5%A5%97%E8%B7%AF%E4%BA%8B%E4%BB%B6/67712843
3. 网经社《腾讯、百度等导航 App "摇一摇"开屏广告险酿车祸，工信部果断出手整治》（2026-06 专题会）—— https://www.100ec.cn/detail--6660578.html
4. 《开屏广告太添堵，怎么治？——"清朗互联网广告生态"》（新华网客户端 / 云南网，2026-11）—— http://society.yunnan.cn/system/2026/11/09/034191973.shtml
5. 《摇一摇广告触发阈值这么低，难道就没人能管管吗？》（含 iOS 27.2「限制运动数据」、华为/vivo 权限管控、工信部 2026-06 会议）—— https://cj.sina.cn/articles/view/8232488626/1eab1ceb207201xr0g
6. 《萤石云视频开屏广告终于能关》（含工信部 2026-06 要求、合法摇一摇阈值、李跳跳/GKD）—— https://post.m.smzdm.com/p/a5rno4d7
7. GKD 开源项目（无障碍 + 社区订阅规则模式）—— https://gkd.li
8. 「拦精灵」案（2024，芒果TV 诉，两审均判不正当竞争成立）：法院明确跳过类软件只能**代点「跳过」**、不得直接屏蔽广告

## 九、合规声明

- 本系统只「替用户点击跳过按钮 / 拦截诱导跳转」，**不屏蔽广告本身、不修改其他应用**（与「拦精灵」案裁判边界一致）
- Android 端 v1.3.0 起申请 INTERNET 权限，但联网仅两出口（GitHub Issues 反馈 / GitHub Releases 检查更新），学习数据全部本地存储；仅供个人学习研究；跳过开屏广告会减少开发者收入，请自行权衡使用范围
