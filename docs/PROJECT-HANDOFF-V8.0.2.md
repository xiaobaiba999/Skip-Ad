# Skip-Ad 项目交接文档（v8.0.2 · Android 主仓）

> 本文档面向"无聊天上下文的新接手模型"，仅覆盖 v8.0.1→v8.0.2 增量与当前交付状态。
> 项目全貌见 `docs/PROJECT-HANDOFF-V8.0.0.md`（仍有效）；一切以真实代码 / Git 历史 /
> `docs/CHANGELOG.md` 为准，冲突时以代码为准并回改本文。

---

## A. 当前版本与仓库状态

- **当前版本**：v8.0.2（versionName `8.0.2` · versionCode `80200`，权威源 `src/lib/version.ts`）
- **本地 main**：v8.0.1 两提交（`9f3478c` 代码+文档 → `c137a07` 交付报告）+ v8.0.2 一提交（本提交）
- **远端 main**：`9c59f2549be561cb187d3bfff609bdf3e3c84c65`（v8.0.0 收尾，经 api.github.com 核验）
  ——本地领先 3 提交，全部可 fast-forward
- **push 状态**：`BLOCKED_PENDING_USER_DEPLOY_KEY`
  - gh CLI 浏览器 OAuth 在本沙箱不可行（github.com git/下载通道超时，OAuth 端点不可达）
  - 已就绪通道：SSH Deploy Key——沙箱持有 ed25519 密钥对（私钥 `~/.ssh/id_ed25519` 600 权限，
    永不外发），`git-ssh` 包装器（`ssh2` 库，位于 `/home/z/ssh-push-tool/`，固定 GitHub 官方
    主机密钥指纹）已端到端验证到认证层
  - **用户操作**：仓库 Settings → Deploy keys → Add deploy key（勾选 write access）→ 粘贴公钥
  - **推送命令**（Deploy Key 添加后）：
    `cd /home/z/skip-ad-repo && git -c ssh.variant=ssh -c core.sshCommand="node /home/z/ssh-push-tool/git-ssh.js" push ssh://git@ssh.github.com:443/xiaobaiba999/Skip-Ad.git main:main`
    随后 `timeout 45 git ls-remote origin refs/heads/main`（或 api.github.com）核验远端 SHA == 本地 HEAD

## B. v8.0.1（本地未推送）摘要

1. `bumpScanChain(pkg, skipFirstRung)`：窗口切换路径跳过梯子 0ms 档（内联首扫已覆盖——0ms 双扫消除）
2. secret-scan 闸门自断修复（iteration 报告 PEM 头字面 → 中文表述）
3. VideoSplashTest S9 自适应轮询节拍真值表（18 断言）
4. 全链路审计三件套（V8-SYSTEM-AUDIT / V8-OPTIMIZATION-PLAN / V8-SYSTEM-OPTIMIZATION-REPORT）
5. APK：`dist/adshield-v8.0.1.apk`（80100/8.0.1，SHA-256 `ce87b299…ddd3a`，
   重建 DEX 字节级一致实证由当前源码构建）

## C. v8.0.2（本提交）核心：双 DFS 单遍化（P3-1）

- **问题**：零命中扫描（档位大头）时 `RuleEngine.matchAll`（规则 DFS）与
  `AdDecisionCore.walk`（决策 DFS）对同一棵树各走一遍——child() binder 调用×2、
  NodeAdapter 分配×2、text/desc/visible/bounds 双读。
- **方案**：`AdDecisionCore.walkFused(root, ctx, ruleRider, maxHits)` → `FusedScan{find, ruleHits}`
  ——同一次 `visit` 遍历收集规则命中（骑士）与决策信号。骑士门序/去重/tierFull 排序/
  满 4 早停与 matchAll 逐字段等价；`visit(..., rider=null)` 时 byte-for-byte 旧 walk。
  `walk()` 原签名保留（复验针 :5369/:5441、插屏 :3037 等 4 处调用方零变化）。
- **接线**：`AdSkipService.tryScan`——probe 未中时一次 `walkFused`（rider=splashLive，
  与旧路径 matchAll 仅开屏窗跑同构）替代 matchAll+walk 两趟；probe 命中时不跑（规则路径直接走）。
- **三处已声明语义差**（`DualDfsParityTest` S11/S12/S17 钉死，绝不静默）：
  1. 树在两趟间变化：晚到候选顺延下一档（60ms 梯子/nudge 覆盖；refreshLive 出手复核不变）
  2. 瞬时 binder 异常：fused 单次即失（确定性异常两路径等价）
  3. 小程序容器：骑士入口即抑制（旧路 execute L0 拦——出手结果同为零点击）
- **证据**（详见 `V8-OPTIMIZATION-PLAN.md` v8.0.2 节 + `V8-SYSTEM-OPTIMIZATION-REPORT.md` v8.0.2 节）：
  - DualDfsParityTest 21 场景 92 断言（AUTOMATED）
  - ScanBenchmark 三形态×三规模 63 断言：child 调用精确减半、属性读 -36~40%、分配减半（AUTOMATED 计数）
  - JVM 计时仅供参考（SIMULATED）；真机性能/电量 NOT_RUN

## D. 测试与构建（v8.0.2 基线）

- **36 套件 6975 断言 0 失败**（`android-app/run-tests.sh`；新增 DualDfsParityTest + ScanBenchmark）
- **APK**：`android-app/dist/adshield-v8.0.2.apk`（80200/8.0.2，证书 SHA-256 `05ada6e3…6f79`
  与全部历史一致，DEX 含 `walkFused`——v8.0.1 DEX 零命中对照）
- 闸门：secret-scan / check-appjs / check-version-sync / `git diff --check` 全绿
- 工具链：`/home/z/android-tools/`（build-tools r34 + platform-34 + ecj.jar）；
  `./build.sh <code> <name>` 构建防降级守卫在岗

## E. 真机验证清单（v8.0.2 追加项，接 v8.0.0 交接的 P0–P5 链）

1. v8.0.2 覆盖安装（80200 > 80100）→ 广告跳过回归（重点：文本键/vid 键/订阅键/弹窗键四路命中）
2. `energyScans`/扫描日志对照：v8.0.1 起切窗扫描次数应下降；v8.0.2 起单次扫描的
   节点访问计数（如日志可得）应约为旧版一半（JVM 计数结论的真机复核）
3. 日志上传闭环（v8.0.0 链未变）+ batchId 幂等 + 断网/Doze 恢复
4. 全部通过后议 v8.0.x 正式发布节奏（v8.0.0 Draft #453676040 仍不动）

## F. 纪律红线（延续，v8.0.2 实践印证）

- 安全 > FALSE_CLICK > DUPLICATE_CLICK > MISS > 正确关闭 > 命中率 > 性能
- 不缓存已失效 AccessibilityNodeInfo / 不跨窗口复用节点 / 不跨 generation 用扫描结果
- 证据分级：STATIC_REVIEW / AUTOMATED / SIMULATED / REAL_API / REAL_DEVICE / UNKNOWN / NOT_RUN
  ——JVM 计数可作 AUTOMATED，JVM 计时只作 SIMULATED，真机结论绝不虚报
- 不 force push / 不重写提交 / 凭据永不入聊天与日志 / 版本按真实改动定级
- 改动纪律：测试先行 → 生产代码 → 全量回归 → 闸门 → 构建 → 文档 → commit（→ push 通道可用时推送并核验 SHA）
