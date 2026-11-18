# v5.7.0 优化实录 —— 扫描日志二轮激进 · 8×12 机型矩阵 · GitHub 面板截图

> 触发：用户二轮指令「扫描日志继续优化，好多广告没有命中，可以大胆激进一点；模拟 N 多品牌 × N 多机型测试修复（开屏广告/屏蔽速度/bug/功耗/精准度/算法优化）；完成后同步 GitHub 和推送 releases；优化 GitHub 面板描述，必要时给出 App 截图」。
> 上一轮（v5.6.0）已做第一轮激进化（指纹 +8 档/倒计时 300ms/点击预算 8/内置规则库 v7·114 款/订阅市场/10 秒警告终局/5×10 矩阵）。本轮在全部红线不放松的前提下继续加码。

## 一、命中再激进化（扫描日志继续优化）

### 1.1 SDK 广告窗指纹 +12 档（`AdDecisionCore.isAdActivityClass`）

未命中诊断日志的共性结论：**整窗无指纹 = 零 vid-close 点击 + 零返回键兜底**，只能干看广告走完。v5.6.0 补齐了国内 SDK 高频命名；本轮把国际主流 SDK 漏网主力全部补上：

| 新指纹段 | 真实窗口类名 | 归属 SDK |
| --- | --- | --- |
| `adunit` | `com.unity3d.ads.adunit.AdUnitActivity` | Unity Ads（国际游戏广告主力） |
| `ironsource` | `com.ironsource.sdk.controller.ControllerActivity` | IronSource |
| `chartboost` | `com.chartboost.sdk.CBImpressionActivity` | Chartboost |
| `audiencenetwork` | `com.facebook.ads.AudienceNetworkActivity` | Meta Audience Network |
| `inmobi` | `com.inmobi.ads.InMobiActivity` | InMobi |
| `tapjoy` | `com.tapjoy.TJAdUnitActivity` | Tapjoy |
| `rewardvideo` / `rewardad` | `…RewardVideoActivity` / `…RewardAdDialog` | 激励视频独立窗（游戏 SDK 高频） |
| `expressad` | `…ExpressAdActivity` | 穿山甲模板广告变体 |
| `adpresent` | `…AdPresentActivity` | 广告 present 独立窗 |
| `fullscreenad` / `fullvideo` | `…FullScreenAdActivity` / `…FullVideoActivity` | 全屏视频广告窗 |

**误报面论证（不乱点红线）**：

- 12 段全部是 SDK 侧命名约定，业务 App 窗口绝无此类命名；
- 两个专门设计的免疫对照进断言：`com.app.download.DownloadActivity`（`loadactivity` 内含 `adactivity` 子串——若用 contains 而非精确段就会误杀下载页）与 `com.unity3d.player.UnityPlayerActivity`（Unity **游戏引擎**主窗——`unity3d` 包段与 `adunit` 广告段严格区分，游戏保护红线）；
- 精确等值/包含段混合匹配保持 v5.1.0 以来的分级策略，零放宽。

### 1.2 时序与预算参数（六处）

| 参数 | v5.6.0 | v5.7.0 | 依据 |
| --- | --- | --- | --- |
| `COUNTDOWN_EXTRA_MS`（倒计时将尽提前开火） | 300 | **180** | 广告主最后 0.1~0.3s 普遍插入结算帧，越早开火可见越短；未到仍不点的安全边界不变 |
| `MAX_CLICKS_PER_WIN`（同窗点击预算） | 8 | **10** | 深树广告窗（300+ 节点）复验换候选链常顶满 8 次后干看广告走完 |
| `SKIP_CANDS_MAX`（文本跳过候选） | 4 | **6** | 同文案多候选全收，复验真正换候选（issue#38 教训） |
| `COOLDOWN_MS`（点击冷却） | 500 | **420** | 复验链换候选节奏再提速（复验安全闸不变：重新扫到候选才出手） |
| `POPUP_COOLDOWN_MS`（弹窗冷却） | 2000 | **1600** | 弹窗复验更快；`POPUP_SCAN_THROTTLE` 700ms 节流不变，功耗红线不破 |

### 1.3 文案/vid 双通道扩充（`RuleEngine`）

- **全局精确文案 +5**：`跳过视频` / `跳过片头` / `跳過視頻` / `skip ads`（视频类 App 开屏「跳过片头」整词独立标签，旧库未收 → walk 分类不进真候选）；`SKIP_TEXTS` 同步 +3（CLOSE 族兼容路径直接放行）；
- **分应用表 +12 款（29→41 款）**：得物 / 什么值得买 / 唯品会 / 苏宁 / 途牛 / 马蜂窝 / 墨迹天气 / 58 同城 / 安居客 / 贝壳找房 / 汽车之家 / 虎扑——未命中诊断日志实锤的「无 SDK 指纹窗 + 专属文案」主力；分应用精确等值零误点面；
- **vid 层新 ADCLOSE 复合族 3 档**：`adclose` / `ad_close` / `adcancel`——第一方自绘广告关闭键的命名约定（`iv_ad_close` / `btn_ad_close`），广告语义直接写进命名；门控 = 边缘带（顶部 25% / 右缘带）+ 小面积（≤35% 屏），非广告窗普通控件绝无此命名；
- **红线保持**：`jump` vid 依旧被安全门拦截（游戏跳跃键非跳过语义——SubscriptionBuiltinTest 既有断言 + 本轮新增游戏陷阱断言双保险）。

### 1.4 内置规则库 v7→v8（114→124 款）

新增 6 款与主引擎分应用表同源对齐：苏宁易购 / 什么值得买 / 贝壳找房 / 虎扑 / 安居客 / 途牛（此前已在库中的得物/唯品会/58 同城/汽车之家/墨迹/马蜂窝不重复收）。全局 `vid*="skip"` / `vid*="countdown"` 兜底两档保持。

## 二、8 品牌 × 12 机型 = 96 机型矩阵（BrandMatrix96Test · 630 断言）

用户要求「N 多品牌 × 每品牌 N 多机型」——矩阵从 5×10=50 扩到 **8×12=96（+92% 覆盖面）**：

| 品牌族（ROM） | 机型（各 12 款） |
| --- | --- |
| 小米/Redmi/POCO · HyperOS | Xiaomi 15 / 15 Pro / 14 / 14 Pro · Redmi K80 / K80 Pro / K70E / Note 14 Pro+ / Note 13 / 14C · POCO X7 Pro · Pad 6S Pro |
| 华为 · HarmonyOS | Mate 70 / 70 Pro / 70 Pro+ / 60 · Pura 70 / 70 Pro · nova 13 / 12 · Enjoy 70 · Pocket 2 · MatePad Pro 13.2 / 11.5S |
| OPPO/realme · ColorOS | Find X8 / X8 Pro / X7 · Reno 13 / 13 Pro / 12 · Ace 5 · realme GT7 / Neo7 / GT Neo6 · A3 Pro · Pad 3 Pro |
| 一加 · ColorOS（独立族） | 一加 13 / 13T / 12 / 11 · Ace 5 Pro / 5 / 3 / 2 Pro · Nord 5 / 4 / CE4 · Pad 2 Pro |
| vivo/iQOO · OriginOS | X200 Pro / X200 / X200 Pro mini / X100 Ultra · S20 Pro / S20 / S19 · iQOO 13 / Neo10 Pro / Neo10 / Z9 · Pad3 Pro |
| 荣耀 · MagicOS（新增族） | Magic7 Pro / 7 / 6 Pro / 6 · 荣耀 300 / 200 / 100 / X60 / X50 / GT / Play 50 · Pad 9 |
| 三星 · OneUI | Galaxy S25 Ultra / S25+ / S25 / S24 Ultra / S24 / S23 / A56 / A26 / Z Fold6 外屏 / Z Flip6 外屏 / Tab S10+ / M55 |
| 其他（新增族） | Pixel 9 Pro XL / 9 Pro / 9 · Nothing Phone (3) / (2a) · 魅族 21 / Lucky 08 · 努比亚 Z70 Ultra · 红魔 10 Pro · 索尼 Xperia 1 VI · moto razr 50 Ultra / edge 50 Ultra |

每机型六维（对应报告六列，全部 96/96）：

1. **开屏广告命中**：六信号（文本键 / desc 键 / vid 订阅键 / 图片角落键@v5.7 `AdUnitActivity` 广告窗 / 新文案「跳过片头」/ 新复合 vid 键 `iv_ad_close`）——六信号全命中；分辨率取真实规格（720p~2.2K 折叠/平板档），bounds 全中心点击任意分辨率一致；
2. **屏蔽速度**：120 节点深树 walk+decide 50 轮均 < 20ms（JVM）· v5.7 激进参数在场断言（180ms / 10 / 6）；
3. **bug 防线**（四陷阱）：抽屉菜单陷阱行零命中 · 巨横幅 CTA 诱饵零动作 · 短信/桌面 overlay 识别 · **游戏 `btn_jump` 居中大按钮零命中零点击**（新增第四陷阱）；
4. **功耗**：典型日（165 事件 + 45 探针 + 12 扫描 + 2 返回键）≈ 0.049mWh < 0.15 红线 · 分级预算在场；
5. **精准度**：银行守护根窗零返回键 · 验证码横幅标记 · **Unity 游戏引擎主窗免疫**（新增第三项）；
6. **算法优化**：v5.7 新指纹 12 档全命中 · 非广告窗 7 档全免疫 · **8 品牌族归一全验**（hyperos/harmony/coloros×2/originos/magic/oneui + 其他组魅族→flyme、其余→generic——每机型都验，不再只验首品牌）· 免设置自愈合并纯函数全对。

## 三、GitHub 面板描述优化 + App 截图

- **README 重构**：居中标题区 + 六徽章行（Releases/License/APK 体积/1330 断言/96 机型矩阵/gkd-subscription topic）+ 「一屏速览」表 + 版本块 + **真实截图三连** + 订阅/能力/技术栈/测试/安装/合规；
- **App 截图（`docs/screenshots/`）**：`app-home.png`（首页：守护状态/保活体检/战绩/开关）、`app-subscription.png`（订阅：内置规则库 v8·124 款/订阅市场 6 活跃源/GKD 导入）、`app-me.png`（我的·全页：版本/权限审计/引擎日志）。由 App 实际 WebView UI（`android-app/assets/www`）渲染截取，**非设计稿**；战绩数字为示例数据（截图说明已注明）。

## 四、测试与发布

- **1330 项 JVM 断言全绿**（+282）：BrandMatrix96Test 630 + 存量十套件 700 + 白盒 13 + 整体 4；详见 [test-reports/round-23.json](test-reports/round-23.json)；
- 白盒核验：dex 含 12 新指纹段/新文案/ADCLOSE 族在场；`assets/www/app.js` 版本串 5.7.0/50700；v1+v2 签名一致（覆盖升级）；
- APK：297KB · versionCode 50700 · 三目录分发 MD5 `3daa9cde0e99f40607c746fea9614ed0`；
- GitHub：全量提交并推送 Releases（tag `v5.7.0` · asset `adshield-v5.7.0.apk`）。

## 五、红线清单（本轮零放宽）

| 红线 | 状态 |
| --- | --- |
| 只点「跳过/关闭」语义控件 | 保持（jump 陷阱断言新增） |
| App 自建 `SplashActivity` 不按返回 | 保持（7 免疫档断言） |
| Unity 游戏引擎窗/游戏 jump 键不碰 | 新增双断言 |
| 倒计时未到不点（只是将尽判定提前） | 保持 |
| 守护名单/验证码/短信/相机/支付页零干预 | 保持（精准度 96/96） |
| 功耗红线 < 0.15mWh | 保持（0.049） |
| `DownloadActivity` 子串陷阱不误认 | 新增断言 |
