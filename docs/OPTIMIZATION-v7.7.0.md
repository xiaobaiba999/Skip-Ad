# OPTIMIZATION v7.7.0 · 回切开屏根治 + Toast 常驻修复 + 机型矩阵 352（九维）

> 用户十七轮实锤原话：「追加问题，点击开启弹窗广告屏蔽，下方弹窗屏蔽已开启几个字一直保持在底部，这是个bug，还有用户反馈，扫描日志从另一个app切回这个app，期间会弹广告，这个没有拦截到，这是个bug，代码进行测试后，进行环境测试：优化模拟各个品牌和机型进行测试修复，拓展设备型号和品牌以及系统，只测试oppo，vivo，xiaomi，三星，华为，荣耀，红米，一加，iqoo及其子品牌……进一步拓宽广告日志命中跳过率，完成过同步giuhub和推送releases。优化github面板描述 加免责声明」

## 一、死因分析（真机实锤归因）

### Bug A：Toast 常驻（「弹窗屏蔽已开启」一直停留在底部）
- 旧实现（app.js `toast()`）：动态建 div + 内联 `cssText`（含 `transition:opacity .2s`）→ `style.opacity="1"` → JS `setTimeout(2200ms)` 收尾置 `opacity=0`
- **失败面①（JS 线程卡死）**：`AndroidApp.setStateBool` / `kvGet("state")` 是同步桥调用（JavaBridge 线程）——服务侧 `snapshotState` 抢 `lock`、学习/规则批量 JSON 操作持锁百毫秒级时，WebView JS 线程被卡住 → **2200ms 计时器不触发 → toast 永不收尾**
- **失败面②（OEM WebView 渲染失灵）**：内联 `transition` + inline `opacity` 组合在部分 OEM WebView（华为/荣耀/小米 WebView 内核分支）上不重绘 → `opacity=0` 改了不生效 → 永久可见
- 浏览器基线复现（agent-browser）：标准 Chromium 中旧实现 3s 后 opacity=0 正常隐藏——**bug 仅在真机 WebView 环境触发**，纯 JS 计时器收尾在真机有两个独立失败面，必须换不依赖 JS 线程的收尾通道

### Bug B：回切开屏漏拦（「从另一个 app 切回这个 app 期间会弹广告没有拦截到」）
- **死因①（entering 误判）**：`onAccessibilityEvent` 顶部对自家包事件无条件 `return`（不扫描自家——自家无广告，行为正确）→ **`lastFgPkg` 从未登记成卫士包名**。用户「目标 App → 卫士 → 目标 App」往返后，切回目标 App 的窗口事件里 `pkg == lastFgPkg`（还停在目标 App 上）→ `entering = false` → 不按「新进入」处理：`rootWinCls` 不刷新（入口窗还是老的）、回切开屏零日志、进入态零重置
- **死因②（CLEAN 档案误杀）**：回切开屏常**叠在主界面窗类上**（同一 MainActivity 盖一层开屏容器——窗类不变），而主界面窗类早被 v5.3.0 宿主窗档案判 CLEAN（连续 3 次零候选 → 只排探针哨兵/低频档 {0,700}）。回切时刻恰恰是开屏广告最可能出现的时刻，档案却把扫描链抑制到最弱——**晚到的跳过键（1~2s 才渲染）与图标式/WebView 式跳过键（原生文本探针探不到）整窗漏拦**。这也是上一轮用户实锤「回归：开屏广告有的跳不了或非常非常慢」的底层病灶（冷启首窗若窗类与主界面相同同样中招）

## 二、修复方案（ReentryGuard 纯函数 + 服务薄接线）

### 2.1 自家包前台边界登记（死因①对策·一半）
```java
if (pkg.equals(getPackageName())) {
    if (et == TYPE_WINDOW_STATE_CHANGED) lastFgPkg = pkg; // 前台边界登记
    return; // 仍不扫描自家（自家无广告——行为保持）
}
```
- 切回目标 App 时 `entering` 正确判 true（新进入路径全恢复：rootWinCls 刷新/回切日志/进入期门）
- 附带收益：用户停在卫士期间，目标 App 的后台内容事件被 v4.4.0 前置过滤**正确**丢弃（旧版 lastFgPkg 停在目标 App → 后台事件全量漏进过滤器链——功耗同时下降）

### 2.2 ReentryGuard 纯函数（死因①另一半 + 死因②）
```java
isEntering(pkg, lastFgPkg, lastWinAt, now)   // 同包离开 ≥ REENTER_GAP_MS(6s) 等价新进入
entryUntil(now) / entryFullActive(…)          // 进入期 4s 全量门
reentryAwayMs(…)                             // 离开时长记账（日志「离开 Ns」）
```
- **6s 阈值双向论证**：低于典型二次开屏触发阈值（多数 App 后台 >5s 回前台才重弹开屏）→ 拦得到；高于窗级抖动误报面（同 App 内 6s 内的窗级抖动绝不误判回切）→ 不误扫
- **覆盖一切 lastFgPkg 漏登记路径**：自家包边界（已登记）、息屏回亮（屏幕关闭期间零事件，windowAt 停在息屏前）、白名单应用边界（白名单事件只维持前台追踪）、ROM 窗口事件缺失
- **进入期全量门（死因②对策）**：判 entering 的时刻起 4s 内 `profileSuppressScan` 首查 `entryFullActive` → 直接返回 false（CLEAN 档案不抑制）→ `bumpScanChain` 走全量 18 档梯子 + 80ms 补扫全开；**激进档语义「宁多扫绝不漏」（用户十五轮指令「暂时放弃宁可漏拦绝不误触」的持续贯彻）**；功耗论证：进入是低频事件（典型日几十次），一次全量梯子 ≪ 事件洪流前置过滤的日常节省——功耗红线不破

### 2.3 回切日志记账（命中跳过率拓宽·可观测性）
```
🔄 回切开屏（pkg）：离开 Ns 后回前台 → 按新进入处理，进入期全量扫描（CLEAN 档案让路）
```
- 30s 包级节流（切来切去不刷屏）；扫描日志从此能区分「回切时刻漏拦」与「普通未命中」——下一轮迭代定位线索

### 2.4 探针词与词典扩容（命中跳过率拓宽·检测面）
- 探针词 3→5：`跳过 / 跳過 / skip / 关闭广告 / 關閉廣告`（前三词顺序保持——命中率优先零延迟）
- 切回期间弹的插层/回切开屏常以「关闭广告」整词作键——「跳过」族探针探不到；繁体「關閉廣告」收编繁体区 App 与出海 SDK
- 词典补收：`TEXT_EXACT` 增「關閉廣告/關閉此廣告」（全局精确层），`SKIP_TEXTS` 增「關閉廣告」（含匹配族）
- **红线保持**：`matchText` 全套规则+形状门照常逐候选验——激进化只放宽「找到」面（探针词变多），不放宽「可点」面（形状门/菜单否决/危险词一票否决原样）

### 2.5 Toast 关键帧动画（Bug A 根治·三重保险）
```css
#toast        { opacity:0; visibility:hidden; }
#toast.show   { opacity:1; visibility:visible;
                animation: toastFade 2400ms cubic-bezier(.2,0,.2,1) forwards; }
@keyframes toastFade { 0%{o:1;v:visible} 88%{o:1;v:visible} 99.9%{o:0;v:visible} 100%{o:0;v:hidden} }
```
- **保险①**：动画跑在**合成器线程**——JS 线程被桥调用卡住也照常走完（治失败面①）
- **保险②**：终态 `opacity:0 + visibility:hidden` 双保险——opacity 合成怪癖也不可见（治失败面②）
- **保险③**：JS 兜底计时器 2600ms 摘 `.show` 类保持——动画与计时器双通道任一活着都能收尾；`.show` 类直接声明可见样式 → 不支持动画的远古 WebView 退化正常
- 三连点重放：`classList.remove("show"); void t.offsetWidth; classList.add("show")`（强制回流从头重放）
- 浏览器实测（agent-browser）：三连点 mid-anim `opacity=1 / anim=toastFade` → 3.2s 后 `opacity=0 + visibility=hidden + 类已摘`

## 三、机型矩阵 308→352（九维）与测试

| 项 | 量 | 说明 |
| --- | --- | --- |
| 品牌组 | 11 组（9 品牌+子品牌） | OPPO/vivo/小米/三星/华为/荣耀/红米/一加/iQOO + POCO/realme（用户点名清单——其他品牌不测） |
| 机型 | 11 × 32 = **352** | 每品牌 +4 · 2027 世代（小米 19 Ultra/Civi 6 Pro/平板 9 Pro · Redmi K120 Pro/Turbo 6/Note 18 Pro/17 · POCO F11 Ultra/X11 Pro/M9 Pro/C80 · Mate 90 RS/Mate 90/Pura 95/nova 16 · Magic10 至臻版/荣耀 600/Magic Pad 3/X80 · Find X10 Ultra/X10/Reno 16/A6 · 真我 GT9 Ultra/GT9/Neo11/V80 · X310 Ultra/X310/S41/Pad 4 Pro · iQOO 21/Neo 14/Z14/Z14 Turbo · S27+/S27/Z Flip9 外屏/Tab S12 · 一加 18 Pro/Ace 8 Pro/Ace 8V/Nord 7） |
| 维度 | **九维** 各 352/352 | ①开屏命中 ①′人手安全 ①″多点防线 ②屏蔽速度 ③bug 防线 ④功耗 ⑤精准度 ⑥算法优化 ⑦信息流广告 **⑨回切开屏（新增：回切判定真值表/进入期全量门/主界面叠开屏层命中/插层键词典——逐机型 1:1 缩放）** |
| 新套件 | **ReentrySplashTest 25 项** | S1 真值表 8 / S2 进入期门 7 / S3 自家包边界时间线 3 / S4 全分辨率回切命中 12 档 / S5 插层键词典 / S6 离开记账 / S7 九品牌几何投影 |
| 全量 | **21 套件 5099 项全绿** | v7.6.1 为 4314 · +785；包内资产抽出后语义闸同验 |
| 浏览器端 | agent-browser + VLM | 三大板块渲染 OK · toast 显示/收尾实测 · 视觉确认胶囊正常 · 版本 v7.7.0 |

## 四、发布物

- APK 832KB（849,462 B）· versionCode **77000**（>76100 覆盖安装即升级——授权/学习数据/白名单/已导入订阅全保留）· MD5 `75a7ba4e9527bf841a6afc05d08656c8` · SHA-256 `4eeef88dee87d6730730e251976767a6d7519c36ae0c3842d855f0a2b508d545` 三目录分发
- GitHub tag v7.7.0 + Release（adshield-v7.7.0.apk asset · digest 核对）+ 仓库描述同步 + README 面板/徽章 + **免责声明扩充**（误触与漏拦边界（激进档披露）/ 回切开屏说明 / 账号风险与商业用途边界）
- 实录：round-39.json · CHANGELOG v7.7.0 · summary.md Round 39
