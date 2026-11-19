# v7.3.0 安全加固 + 算法优化实录（防投毒 · 防劫持 · 防注入）

> 用户指令（本轮原话要点）：**检索源代码，完善安全性修复，进行算法优化，避免遭到他人攻击，完成过后同步 GitHub 和推送 Releases；优化 GitHub 面板描述。**
>
> 本轮以三路并行审计（网络/更新/订阅链 · 规则引擎算法 · WebView/前端/导出组件）对全仓攻击面做了一次完整体检，随后把全部「他人可攻击」路径逐条焊死，并对扫描热路径做了五项零分配算法优化。基座为 v7.2.0（1959c55），全部 4077 项 JVM 断言保持全绿。

---

## ① 审计结论（修复前攻击面全景）

| # | 攻击面 | 风险 | 攻击场景 |
| --- | --- | --- | --- |
| 1 | 更新链零完整性校验 | **高** | 镜像代理（ghfast/gh-proxy 为公共第三方）被劫持投毒、公共 Downloads 目录 TOCTOU 换包、GitHub API 响应被注入假版本——下载到安装之间无任何哈希闸 |
| 2 | 订阅规则注入 | **高** | 恶意/被劫持订阅源用 `activityIds` 限定支付页 + 规则名伪装「跳过」+ `[visible=true]` 匹配任意控件 → 引擎定向代点「确认支付」类按钮 |
| 3 | GitHub 令牌内嵌 | **高** | 双层 XOR+Base64 混淆可被 jadx 一条命令还原（classic PAT · repo 全权）——脱离 App 刷 issue/耗配额 |
| 4 | Json5 无深度上限 | **中** | `[[[[…万层]]]]` 深嵌套 → StackOverflowError（Error 不被 catch(Exception) 接住）→ 服务重绑即崩 = 无障碍掉线 DoS |
| 5 | 订阅体量无上限 | **中** | 超大/无限 body → 内存膨胀；超大规则集 → 扫描风暴 |
| 6 | host 白名单 contains 子串 | **中** | `https://github.com.evil.com/x.apk`、`https://attacker.com/fwd?to=github.com` 均通过旧检查 |
| 7 | kvSet 任意键写 | **低** | XSS 若出现即可写 subCache 投毒规则（JS 桥放大面） |
| 8 | API 重定向未禁 | **低** | api.github.com 正常永不 3xx；出现即劫持信号 |
| 9 | allowBackup=true | **低** | subCache/学习状态随 adb backup 带出与植入 |

已有良好设计（本轮保持）：三层 HTTPS 强制 + 零放宽 TLS、WebView 白名单制 + 回调名校验、文件名净化防穿越、订阅 back 动作丢弃、银行/政务/相机/验证码/支付文案零扫描、运行时限流、双层脱敏、signature 私有广播。

## ② 修复清单（全部落地）

### A. 更新链完整性（防投毒/防换包）
- `Net.parseRelease`：提取 GitHub API asset 的 **digest（官方 SHA-256）与 size**，经 HTTPS 通道随 Release 元数据下发；
- `Downloader.start` 新签名携带 digest/size 与下载任务同源同存；`Downloader.install` 安装前**流式计算 SHA-256 逐字节比对**，digest 缺失回退字节数比对，双缺拒装；不符 → 删文件 + 清任务 + 明确报错；
- `Downloader.hostAllowed`：下载源白名单改 **URL 解析 host 精确/后缀匹配**（github.com · *.github.com · githubusercontent.com 及子域 · ghfast.top · gh-proxy.com；尾点 FQDN 与 userinfo 混淆全拒）；
- `Net.http` noRedirect：GitHub API 三通道（latest / 列表回退 / issues）**禁自动重定向**——3xx 只可能是劫持；订阅拉取链保持自动跟随（镜像站存在合法重定向）；
- `Net.readAll`：响应体 **4MB 硬上限**（超限断连）。

### B. 订阅规则注入防线（防定向代点支付按钮）
- `Subscription.dfsMulti`：**支付资金词（19 词）+ 营销诱导词双否决对全部规则生效**（此前仅全局组/无 activityIds 规则受控——带 activityIds 的专属规则存在绕过口）；
- `AdSkipService.execute`：gestureOnly 语义回落的 `d.label` **不再接受订阅命中（rule_sub_gkd\*）的规则名**作为证据——规则名是订阅源可控文本，伪装「跳过」的恶意规则名失效，零文本零 vid 目标一律拒点；
- `AdDecisionCore.PAYMENT_WORDS/hasPaymentWord` 新增（与 INDUCE_WORDS 同源红线：真跳过键文案数学上不含资金词，只紧不松）。

### C. 解析器 DoS 硬闸
- `Json5`：**嵌套深度 ≤64**（try/finally 递减，订阅语义最深处约 6 层，余量充足）；
- `Subscription.parse`：**apps ≤2000 · 总规则 ≤20000（超限拒收）· 单应用组 ≤100 · 组内规则 ≤200 · 单规则选择器 ≤20**；
- `GkdSelector.compile`：**选择器长度 ≤256**（超限跳过该选择器，其余照常加载）；
- `SubscriptionManager.ensureLoaded/fetch`：**catch Throwable**（坏 subCache 丢弃绝不崩服务；拉取链 Error 不再穿透）。

### D. 桥面与配置加固
- `MainActivity.kvSet`：**key 白名单 = {"state"}**（JS 侧唯一合法写入键）——XSS 若出现也无法写 subCache/dl 系列投毒持久化字段；
- `MainActivity.openUrl`：仅 https + hostAllowed 精确匹配；
- `AndroidManifest`：**allowBackup=false**（备份通道关闭，零功能损失——本应用零账号零云端）。

### E. 算法优化（扫描热路径零分配化）
| 优化点 | 旧热点 | 新实现 |
| --- | --- | --- |
| `RuleEngine.matchVid` | 每零文本叶子节点 `split("[.$]")` 隐式 Pattern.compile + String[] 分配 | 手写字符级分段扫描（零正则零数组），语义 byte-for-byte 等价 |
| `AdDecisionCore.isAdActivityClass` | 每调用 toLowerCase + split（每窗口事件/每轮扫描） | **结果缓存**（512 封顶；窗口类名集合有限，每类名只算一次） |
| `lowerAscii`（RuleEngine/AdDecisionCore 共用） | `toLowerCase()` 每次分配 + tr-TR locale 把 "SKIP" 写成 "skıp" 整层丢失 | ASCII 快路径零分配（无 A-Z 原串直接用）+ locale bug 顺带根治 |
| `GkdSelector.Cond` | testName/testId 每节点每规则拼接 `"."+sv`/`":"+sv`/`"/"+sv` | 编译期预计算后缀常量（匹配期零拼接零分配） |
| `AdSkipService.collectRoots` | 判空+取包名+再取 root 共三次跨进程调用 | getRoot 单次缓存（binder 调用减半） |

### F. 多点防线 II + 既有 bug 修复
- `execute` 出手冷却改 **max(COOLDOWN_MS, HumanTap.verifyDelayMs(attempts))**：nudge 补扫旁路（80ms 节流只挡扫描不挡出手）的 ~300ms 换候选连发封死——出手节奏与人手复验梯（500ms→1.4s→2.4s→…）对齐，复验针照常放行；
- `firedTargetAt` 超限改**按时间淘汰最旧 128 条**（原整体 clear 瞬间失忆 → 同窗刚点过的目标补枪；击数表同步清理）；
- app.js 下载完成按钮选择器修复（`prefix+"-btn"` 永不命中 `#page-dl-btn` → 改 `prefix+"-dl-btn"`——下载完成后「立即安装」翻转恢复）。

## ③ 测试（有意义的验证，非为测而测）

- **全量回归**：17 套件 **4077 项 JVM 断言全绿**（v7.2 基座能力零回退——含 FeedAdGuard 90 项 / RuleOrder 39 项 / BrandFocus308 2868 项等，tokenizer/缓存/lowerAscii 等重写均保持 byte-for-byte 语义）；
- **构建链**：APK 824KB · VC 73000 > 72000（覆盖安装即升级）· v1+v2 双签名；
- **安全链实测**：GitHub API 确认 asset digest 字段真实可用（`sha256:…` 下发）；APK 内 dex 含 `dl_sha`/`nesting too deep` 标记（校验链与深度闸已入包）；assets 内 app.js 为 4 参下载调用（digest/size 透传）；
- **发布校验**：MD5 `1577bc2aa220c3fec6af0533930f382a` · SHA-256 `3905dc45a62693dd549604e0a916af4934a90717ce814e36d0f6a5fe1e42c973`（三目录分发一致）。

## ④ 残余风险披露（诚实边界）

- 内嵌 GitHub 令牌（classic PAT · repo scope）为「日志一日三次自动提交 + 检查更新」功能所必需，混淆可被逆向提取；无法在客户端侧根治（已写入 README 免责声明，建议仓库所有者择期轮换为最小 scope fine-grained PAT）；
- 签名密钥随开源仓库公开（覆盖安装连续性所系无法静默更换）；同签名恶意重打包在「官方 Releases + SHA-256 校验」通道外无法被技术手段排除；
- 更新下载仍落公共 Downloads 目录（依赖本项目零依赖工具链无 FileProvider）——TOCTOU 已由安装前 SHA-256 闸覆盖，窗口为毫秒级；
- WebView CSP 未加：file:// 域下 CSP 行为跨 WebView 版本不确定，静默破坏风险大于收益（转由 kvSet 白名单 + openUrl/hostAllowed 精确白名单承担同层防御）。

## ⑤ 同步与发布

- 三目录分发（`android-app/dist` · `download` · `download/开屏卫士APK`）+ README 面板 v7.3.0（徽章/一屏速览/当前版本/免责声明安全边界段）+ 本实录 + CHANGELOG；
- GitHub 面板 description 字段同步 v7.3.0 安全主题；
- Release v7.3.0：APK 附件 + MD5/SHA-256 校验表 + ≤5 条带序号功能描述（App 内「检查更新」就地渲染同源）。
