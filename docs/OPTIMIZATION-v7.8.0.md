# v7.8.0 优化实录 · 视频开屏卫士 + 插屏重扫路由根治 + C 语言基准

> 第十八轮用户指令全量落地实录。死因诊断 → 修复映射 → 实测验证 → 发布闭环。
> 上一轮（v7.7.0）实录见 [OPTIMIZATION-v7.7.0.md](OPTIMIZATION-v7.7.0.md)。

## 一、用户十八轮原话 → 死因诊断 → 修复映射

| 用户原话 | 死因（代码级定位） | 修复 |
| --- | --- | --- |
| 「很多没命中，只要是开屏广告带视频的你都未命中」 | ①12s 观察窗天花板（视频开屏跳过钮常在视频末 5~15s 才露头——WINDOW_MS=12000 硬封顶，>12s 按钮引擎已出窗判 CLEAN「不再扫」）②静默事件源（SurfaceView 视频播放零内容事件——零候选退避 3 次+2.6s 杀链后零事件零复活）③预算烧穿（图片键 cdN=0 门控直通——3 击 1 秒烧光 MAX_CLICKS_PER_WIN=3，按钮真激活时引擎已 gaveUp 收手）④L4 形态确认门（WebView 视频容器树 >150 > CORNER_TREE_MAX_NODES；CORNER_MAX_MS=6500 关门） | VideoSplashGuard 纯函数（v7.8.0 新模块）：画像 videoLike → 观察窗 12s→20s + 1.5s 轮询针（探针直查零 DFS）+ 白点节流 ≥1.5s/击 + 预算 3→5 + 新键预算刷新 + L4 放宽（树 260/时机 20s/形态位） |
| 「且app内插入广告也不会拦截关闭」（第 N 轮） | ①重扫路由黑洞（内容事件开屏窗期全改道 nudgeScan 后 break——插屏常在 App 打开后 3~5s 弹出恰在黑洞内，首扫一眼后整个生命周期无人再看；四轮 v7.4~v7.7 全在词表/闸门打转，扫描调度从未动过）②首扫太早（窗切换帧插层刚创建、节点 200~800ms 后才进树）③同窗插层无人扫（无焦点 Dialog/overlay 挂载不产生任何被订阅事件）④闸⓪语境面窄（≤10 字原生文字节点——H5 插屏角标在网页内核/SDK 插屏窗类即铁证全拒）⑤闸①②杀变体（拉丁 X/点击关闭 精确等值拒；宽扁横条钮 closeFormOk 双杀） | 路由黑洞解除（两分支内加 maybeInterstitialScan）+ 节流 2500→1500 + 延后复扫 +450ms + 补订 typeWindowsChanged + 语境三路并集（词 16 字/WebView/SDK 指纹）+ 词族收编 + 宽扁/小面积三路放行 + 终闸一致性 + feed 名单孤儿化解除 |
| 「卫士用c语言写的话会不会更好呢」 | 历史四语言基准（Java/Kotlin/JS/Python）无 C 参照——语言问题缺实测数据 | bench.c 五语言同构基准（xorshift31 同树同规则同命中——校验和逐位一致）以数据回答 |
| 「优化模拟各个品牌和机型进行测试修复，拓展设备型号和品牌以及系统」 | 机型矩阵 352（11 组 × 32）不含 2027-2028 世代 | 352→396（每品牌 +4）+ 第十维视频开屏逐机型验证 |

## 二、C 语言基准实测（五语言同构 · 数据回答）

协议与四语言基准完全同源（BENCH: cold1000 → warmup2000 → steady20000 × linear/optimized；
COMPILE: 24 条 GKD 选择器 50 轮；BOOT: 进程冷启动；min-of-9 聚合）。
C 版与 Java/JS/Python 现场 9 轮全跑 + Kotlin 历史值合并（编译器不在场）。

**校验和逐位一致（五语言等价证明）**：sel_sink=731 · hits=20000 · sink=485562

| lang | boot(ms) | compile(µs/套) | 冷首1000(ns) | 稳态linear(ns) | 稳态opt(ns) | RSS(MB) |
| --- | --- | --- | --- | --- | --- | --- |
| java | 58.7 | 219 | 48901 | 5051.5 | 1070.9 | 89.2 |
| kotlin | 77.9 | 289 | 45971 | 5306.4 | 903.7 | 94.7 |
| js | 28.9 | 52 | 2649 | 1050.6 | 476.9 | 56.8 |
| python | 29.6 | 119 | 6842 | 9501.9 | 5542.7 | 12.8 |
| **c** | **1.7** | **7** | 24976 | 26758.7 | **261.8** | **1.9** |

**结论（三条）**：
1. C 稳态 optimized 261.8ns——快 Java 4.1×，boot 快 34×，RSS 省 47×（常驻场景优势）；
2. 但都是 **µs 级**：引擎事件面节流 80ms 级，**5 个数量级余量下语言性能差异对命中率零贡献**（命中率在词表/画像/调度——本轮全 Java 层修复即明证）；linear 模式 C 反比 Java 慢 5×（glibc regcomp 每次 ~13µs 比 Java Pattern.compile 慢——诚实记录，若 C 手写匹配器可更快但与引擎正则语义不可比）；
3. **AccessibilityNodeInfo 是 Java API**——C 必须经 JNI 回 Java 才能摸到无障碍树：改 C = 重写全引擎换不到任何命中率收益。**保持 Java**。

## 三、实测验证

- `./run-tests.sh`：**6063/6063 全绿**（23 套件；v7.7.0 为 5099 · +964）
- 新增 VideoSplashTest 51 + InterstitialRoutingTest 56；BrandFocus352Test→BrandFocus396Test 3628→4476（+44 机型 × 10 维 + 第十维 396）；InterstitialGuardTest 93→102（闸扩容适配）
- 五语言基准校验和逐位一致（sel_sink=731/hits=20000/sink=485562）
- ECJ 全量编译（src + tests）零错误；build.sh 构建 APK 853,558 B（833KB）VC 78000
- 页面资产语义闸（check-appjs.sh）5 符号全过；版本同步闸（check-version-sync.sh）双端统一
- APK：MD5 5fba91c1a1e34d7dfc96c0b4725bf584 · SHA-256 59761b7e58df33e1eedae8b3bb5619439f09a529e0708102b51d20a96d2cd96a

## 四、下轮数据闭环提示

- 视频开屏画像日志（🎞 视频开屏画像/轮询命中/新键）样本驱动 VideoSplashGuard 参数微调
- 插屏未命中日志的「窗龄」字段直接定位首扫时机/重扫路由问题；「样本」继续驱动 INTER_CLOSE_WORDS/INTER_SEARCH_WORDS 补词
