# v6.2.0 优化实录 —— 信息流广告卫士 · 扫描日志六轮拓宽 · 286 机型八维矩阵

> 触发（用户第九轮原话）：「扫描日志，很多广告命中不了，最好把app内的广告也识别跳过，比如微信朋友圈里就业广告，还有其他app也是，优化版本信息描述，言简意赅有一个标题一个段落，发现新版本时同样显示新版本的功能描述，言简意赅，不要错过5个标题要标序号，1比1模拟人在不同设备上操作使用卫士，解决这个bug。优化模拟各个品牌和机型进行测试修复，拓展设备型号和品牌以及系统，只测试oppo，vivo,xiaomi,三星,华为,荣耀,红米,一加,iqoo及其子品牌，其他品牌就不要测试了……包括app开屏广告测试，屏蔽速度测试，bug测试，项目功耗测试，精准度测试等等，项目优化所有模块的算法进行测试。进一步拓宽广告日志命中跳过率，完成过同步giuhub和推送releases。优化github面板描述 加免责声明」

## 一、FeedAdGuard 信息流广告卫士（本版核心新增）

### 问题定位
扫描日志显示：开屏/弹窗路径已覆盖，但**用户正在浏览的信息流**（朋友圈、微博、B站推荐流等）里的广告卡片无法跳过——信息流广告没有「跳过」键，只有卡片角落的披露角标（「广告/就业/招聘」）与角标背后的关闭菜单。直接点卡片任何位置都是点进广告主体（误触事故面）。

### 两段式方案（唯一安全路径）

| 阶段 | 动作 | 安全闸 |
| --- | --- | --- |
| **P1 点角标** | 点卡片角上的「广告/就业」披露角标 → 拉起关闭菜单 | 角标词【精确等值】（trim 后 equals——招聘广告正文满篇「广告/招聘」字样 contains 即误点广告主体，**绝不 contains**）+ 面积门（宽 ≤35% 屏宽 × 高 ≤8% 屏高——广告主体大图绝不可能过闸；屏幕未知 → 420×126 像素兜底）+ 带纯符号尾（广告×/广告·/就业›）的披露徽标单独收编 + >6 字必是正文 |
| **P2 收菜单** | 点「关闭该广告/关闭此广告/屏蔽此广告/关闭广告/不感兴趣」中性项收起 | 须 P1 出手 **8s 指纹窗**内（用户自己长按的「不感兴趣」菜单绝不对抗）+ 菜单**强特征 ≥1 且中性项总数 ≥2 双闸** + 屏蔽作者/举报/反馈类**扩展动作绝不代点**（替用户做超出「关闭本条」的决策=越权）+ 紧凑行形态闸（巨横幅 CTA 绝不进菜单候选） |

### 四道安全闸（宁漏勿误红线——绝不点广告主体）

1. **角标词精确等值**：trim 后 equals；带纯符号尾单独收编；>6 字必是正文
2. **角标面积门**：宽 ≤35% 屏宽 × 高 ≤8% 屏高（几何兜底——广告主体大图/大容器绝不可能过闸）
3. **P2 只点中性关闭项**：精确等值五项；扩展动作绝不代点
4. **名单门**：18 款信息流 App 名单内才扫（微信/微博/头条/抖音/快手/小红书/B站/知乎/百度/UC/QQ浏览器/腾讯新闻/网易新闻/新浪新闻/夸克/WiFi万能钥匙/虎扑/汽车之家）；名单外 App 零 feed 扫描（开屏/弹窗/插屏路径覆盖——功耗红线不破）；**微信窗级门只认 sns 族窗**（朋友圈 SnsTimeLineUI 族——聊天主界面零扫描红线保持，AppBrandUI 小程序容器由 MiniProgramGuard 更早拦截）

### 功耗双闸与工程接线
- 信息流扫描 **1.2s 节流 + 300 节点预算**（独立于开屏链；feed 窗常驻、窗计时不重置；开屏窗期内让路——开屏路径优先覆盖）
- 服务侧薄接线：`maybeFeedScan()`（名单门+窗级门+节流）→ `decideP1/decideP2`（纯函数决策）→ `executeFeed()`（构造 Decision 走既有 `execute()`——免费获得用户让路 4s/耐心预算 6/F 同指纹去抖 1500ms/H 结果静默 250ms/人手手势派发/HumanTap tapSpec）
- **ComplianceGuard.feedBoundaryCheck**（feed 决策专属终闸：场景（零扫描/短信/相机/守护）+ 语义（P1 角标/P2 中性项——FeedAdGuard 等值）+ 形态（≤35% 屏）；`boundaryCheck` 原样未动——存量语义零变化）；`d.reason.startsWith("feed_")` 路由
- 新窗切换 `clearTapGuards` 同步清零 armed 状态；`reasonZh` 补 feed 词条（日志可读性）
- 纯 Java 零 Android 依赖（与 MiniProgramGuard 同规约），JVM 可单测

## 二、扫描日志六轮拓宽（命中跳过率）

| 通道 | 增量 | 根因 |
| --- | --- | --- |
| SKIP 文案库（RuleEngine.TEXT_EXACT） | +8 条：關閉廣告/跳過福利/跳過引導/跳过引导/跳过福利/跳过动画/跳过抽奖/关闭福利 | 繁体机型整词标签零收录（contains(跳过) 繁简不通）；品牌/会员/引导/动画/抽奖可跳实为开屏变体 |
| matchesSkip 主路径（AdDecisionCore） | contains("跳過") | 主路径繁简双通 |
| CLOSE 族（AdDecisionCore.CLOSE_TEXTS） | +4：跳過/關閉/不感興趣/關閉廣告 | 等值通道繁体四件套 |
| 角标信号（FeedAdGuard.BADGE_WORDS） | +4：就业/求职/招聘/职位 | 朋友圈就业广告披露角标族 |
| SDK 窗指纹（AdDecisionCore.isAdActivityClass） | +4 段：advertisement/offerwall/screenad/adsdialog | AdvertisementActivity 整段旧库漏网（只认 advertising）· OfferWall 积分墙 · SplashScreenAd 复合族 · AdsDialog 弹窗 Activity——LearnGovernor.sdkGeneralize 同源对齐 |

非广告窗免疫对照保持全绿（App 自建 SplashActivity/MainActivity/LoginActivity/DetailActivity/CashierActivity/DownloadActivity/UnityPlayerActivity 7 档绝不误认——`downloadactivity` 含 `adactivity` 子串被精确段匹配挡住的既有红线不破）。

## 三、版本描述序号化

- `CURRENT_RELEASE`：1 标题（「App 内广告也能跳过了」）+ 1 段落（保持 v6.1.0 精简格式）
- `notesToHeadlines()`：输出带序号（`1. xxx` `2. xxx` … ≤5 条）——先按内容去重（并行 raw 数组）再按 raw 序号加前缀；44 字截断不影响序号
- 更新横幅 ub-notes 区 + 「我的→检查更新」同源渲染（单一来源，两处展示一致）

## 四、286 机型八维矩阵（拓展设备型号与系统）

- **11 组 × 26 = 286**（220→286，+30%）：每组新增 6 款真实世代机型；系统维度拓展（HyperOS 3/HarmonyOS 5.1/MagicOS 10/ColorOS 16/OriginOS 6/One UI 8/OxygenOS 16 在组内代表）
- 品牌白名单保持（用户指定 9 品牌及子品牌——其他品牌不测）：小米/红米/POCO/华为/荣耀/OPPO/真我/vivo/iQOO/三星/一加
- **八维 = 七维 + ⑦ 信息流广告维**：朋友圈就业广告角标命中（P1 决策 bounds=角标自身）· 正常朋友圈内容零命中（宁漏勿误）· 菜单两段（armed 收起/非 armed 零对抗）· 微信窗级门
- 断言结构：286×9 机型级 + 11×8 品牌汇总 + 8 矩阵总检（第 8 项「六轮拓宽零回归」综合检）= **2670**
- 测试项（用户指定全维度）：开屏广告命中/多点误点/屏蔽速度/bug 防线/功耗/精准度/算法优化/**信息流广告**——八维各 286/286

## 五、全仓测试（3765 断言全绿，+907）

| 套件 | 断言 | 结果 |
| --- | --- | --- |
| BrandFocus286Test（9 品牌×26=286 机型八维矩阵） | 2670 | ✅ |
| FeedAdGuardTest（信息流广告卫士四道安全闸 · 新增） | 81 | ✅ |
| HumanTapMatrixTest（1:1 人手矩阵——A~E + F/G/H 零回归） | 19 | ✅ |
| TapPointMatrixTest（24 几何 × 12 场景触点矩阵） | 295 | ✅ |
| 存量回归（10 套件：MiniProgram 36/Decision 22/SubscriptionBuiltin 76/RegressionCore 15/MultiDevice 42/NonMainstream 130/RestoreGuard 106/PowerGovernor 48/RuleGovernor 142/Compliance 83） | 700 | ✅ |
| **合计** | **3765** | **✅ +907** |

存量回归零语义改动：boundaryCheck 原样未动（feedBoundaryCheck 纯增量）；matchesSkip/CLOSE_TEXTS/TEXT_EXACT/指纹全部纯增量扩充（繁体词与既有简体词零冲突）；FeedAdGuard 不触碰既有决策路径（独立 feed 链）。

## 六、工程化与分发

- APK **305KB（312,670 B）** · VC 60200 · 同 keystore 签名覆盖安装
- 三目录分发 MD5 一致：`e5c86d2bd46081a10752c894619d609d`（android-app/dist · download · download/开屏卫士APK）
- GitHub：tag v6.2.0 + Release（APK asset + 序号化功能描述）+ README 面板 v6.2.0（徽章 3765 断言/286 机型/305KB + FeedAdGuard 徽章 + 信息流广告能力行 + 合规边界行同步 + 免责声明保持）+ 仓库 description 同步

## 修复清单

- **FEED-GUARD（信息流广告识别跳过——本版核心）**：FeedAdGuard.java 新增（两段式 + 四道安全闸 + 名单门 + 功耗双闸）；AdSkipService 接线（maybeFeedScan/executeFeed/clearTapGuards 复位/reasonZh 词条）；ComplianceGuard.feedBoundaryCheck 终闸；FeedAdGuardTest 81 项
- **HIT-WIDEN-6（扫描日志六轮拓宽）**：TEXT_EXACT +8 · matchesSkip 繁简双通 · CLOSE_TEXTS +4 · 角标族 +4 · SDK 指纹 +4（isAdActivityClass + LearnGovernor 同源）
- **NOTES-NUM（版本描述序号化）**：notesToHeadlines 序号前缀（内容去重不受序号影响）；CURRENT_RELEASE v6.2.0（1 标题 + 1 段落）
- **MATRIX-286（测试矩阵拓展）**：BrandFocus220Test → BrandFocus286Test（11 组 × 26 = 286，八维新增 ⑦ 信息流广告维；NEW_FINGERPRINTS 22→26）；run-tests.sh 14 套件（新增 FeedAdGuardTest）
- **RELEASE-SYNC（GitHub 同步推送）**：tag v6.2.0 + Release（APK asset · 序号化描述）+ README 面板 v6.2.0 + 免责声明保持 + 仓库 description 同步
