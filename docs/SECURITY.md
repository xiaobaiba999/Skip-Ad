# 安全策略与披露（SECURITY）

> 适用仓库：[xiaobaiba999/Skip-Ad](https://github.com/xiaobaiba999/Skip-Ad)
> 本文档由 v7.9.4 安全审计轮建立（FalsePositiveScenarioTest 同轮产出）。

## 一、已披露风险与整改状态

### R1 · GitHub Token 曾以内嵌混淆形态存在于公开仓库（Web 端 v7.9.4 / Android 端 v7.9.7 已移除）【仍需撤销】

- **暴露形态（双端）**：`src/lib/credentials.ts` 自 v2.0.0（commit `3f16c1c`，
  2026-11-16）起，以「双层 XOR + Base64」混淆形态内嵌一枚 classic PAT（`ghp_`
  前缀 40 字符），用于仓库发布脚本与推送链鉴权；**同一令牌同源密文亦内嵌于
  Android 端 `Net.java`（builtInToken，v4.2.0 起随每个公开发布的 APK 分发，用于
  自动/手动日志直发 GitHub Issue 与带鉴权的更新检查）——两端暴露面同源。**
- **风险定性**：**混淆不等于加密**。APK/源码公开环境下，攻击者最终可以提取；
  公开 git 仓库中的混淆令牌必须视为**已泄露**。该文件自首次入库起即随公开仓库
  对全网可见。
- **git 历史**：令牌存在于多个历史提交（`3f16c1c` / `0f20f4a` / `69ec409` 等）。
  本仓库**禁止 force push / 禁止改写历史**（发布链红线），因此历史无法原地清除——
  历史中的暴露事实**不可撤销地存在**。
- **处置状态**：
  - [x] **Android 端内嵌令牌已移除（v7.9.7）**：`Net.java` 的 `builtInToken`
    （双层 XOR+Base64 同源密文）与 `postIssue`（自动/手动日志直发 GitHub Issue）
    整体移除；`latestRelease` 改匿名请求（Authorization 头不再发送）；手动日志
    改为本地导出 + 系统分享；新增 `LOG_RELAY_URL` 预留常量（空串 = 自动上传
    结构性关闭——客户端从此零 GitHub 写凭据，绝不回退直连）。secret-scan 双闸
    复跑全绿。
  - [x] HEAD 代码已完全移除内嵌令牌（v7.9.4，Web 端）：`credentials.ts` 重写为
    「环境变量（`SKIPAD_GH_TOKEN` / `GH_TOKEN` / `GITHUB_TOKEN`）→ 仓库根
    未入库文件 `local-gh-token`（`.gitignore` `local-*` 规则放行收集，权限 600）」
    的凭据链；`scripts/gh-token.mjs`（git-push-once / auto-log-sync 推送链）同款整改。
  - [x] 新增密钥泄露扫描硬门 `scripts/secret-scan.sh`，并接线到
    `run-tests.sh` 与 `build.sh`：任何令牌形态（`ghp_…` / `github_pat_…` /
    `AKIA…` / 私钥头 / `x-access-token:` URL 凭据对）或 credentials.ts 内嵌凭据
    形态（Base64 长串 / KEY 数组 / XOR 解码器）进入被跟踪文件时，测试与打包
    双闸直接拒绝。
  - [ ] **【需仓库所有者执行】撤销该令牌**：GitHub → Settings → Developer
    settings → Personal access tokens → 删除/吊销该 classic PAT。**撤销前该令牌
    必须视为有效暴露凭据**。撤销后用新令牌仅通过环境变量或本机未入库文件
    注入，绝不写回仓库。
  - [ ] CI（如后续启用）使用 GitHub Actions Secrets 注入，禁止任何形式的入库。

#### R1-2026-11-24 更新 · 发布令牌密文金库重新入库【所有者明示决策 · 接受风险】

- **决策**：v7.15.4 起仓库所有者指令「把发布令牌加密放在源码里，不要每次
  都询问」——`scripts/release-token-vault.mjs` 以「双层 XOR + Base64」密文
  （独立密钥，与 `Net.java` builtInToken 同族编解码、防跨解）永久固化发布
  令牌，作为 `scripts/gh-token.mjs` 凭据链第三优先级（环境变量 →
  `local-gh-token` → 金库）。跨会话/跨机器的发布链不再向所有者索要令牌。
- **风险定性（不变）**：混淆不等于加密——密钥与密文同库，提取只是时间
  问题；本形态与 `Net.java` 内置令牌（v7.15.3 起所有者同类决策复活）同
  风险等级，属「所有者背书的可控暴露」。**明文/拆分/其他混淆形态仍零容忍**
  （secret-scan ①② 红线不变，⑤ 新增金库互逆防写坏闸）。
- **配套防线**：
  1. `scripts/check-release-readiness.mjs`——每次发布前强制核验（publish-*.mjs
     硬闸 0）：金库入库（漏写）· 密文互逆（写错）· API 活性（死令牌）·
     仓库写权限 · env/local 与金库一致性（陈旧轮换）五重硬闸；
  2. `secret-scan.sh` ⑤——测试/构建双闸口的离线金库完整性；
  3. 轮换路径：GitHub 换新 → `bun scripts/release-token-vault.mjs --encode`
     （硬闸：形态 → API 活性 → 磁盘回读互逆）→ 提交推送 → 就绪检查复验。
- **实测注记**：本轮落地时实测发现 2026-11-24 02:37（v7.15.3 发布时刻）仍
  存活的令牌于同日 03:46 前被 GitHub 侧吊销（`/user` → 401 Bad credentials；
  仓库历史/工作区/Issues 三面均无明文暴露——非本仓库泄露所致）。就绪检查
  C 闸当场拦截「带死令牌发版」，防复发机制的首个实战案例。

### R2 · Android 签名私钥与口令公开【保持现状 · 迁移方案已立项】

- **暴露形态**：`android-app/keystore/adshield.keystore`（v1.2.0 起入库）与
  `build.sh` 中的明文口令（`--ks-pass pass:adshield2026`）均在公开仓库中。
- **风险定性**：任何人都可用该私钥签名恶意 APK，并以「同签名覆盖安装」方式
  冒充本应用升级（供应链投毒面）。
- **处置约束（按发布安全规程）**：
  - **不得贸然更换 release 签名密钥**：本项目为直装 APK 分发（无应用商店
    签名代管）。更换签名 = 已装机用户无法覆盖升级，只能卸载重装——
    在未确认升级路径（迁移公告/双包并行期/装机量评估）之前换钥会造成
    断更事故。**v7.x 全系继续使用现钥**。
  - 迁移方案（立项，未执行）：v8.0 起启用新签名密钥（安全存储，不入库），
    发布前在应用内面板与 Release 说明提供「卸载重装」迁移指引，并保留一个
    现签名过渡版本作为迁移跳板。
  - 后续所有发布签名必须使用安全存储的 keystore（口令经环境变量注入，
    `build.sh` 已改为支持 `KS_PASS` / `KEY_PASS` 环境变量覆盖——见 v7.9.4
    build.sh 签名段）。
- **风险接受声明**：现钥公开的现状由仓库所有者知情并接受（换取覆盖升级
  连续性）；`scripts/secret-scan.sh` 已确保 keystore 材料不再散落到
  `android-app/keystore/` 之外的路径。

## 二、产品隐私红线（长期不变）

- 不申请定位/通讯录/相册/非必要存储权限；
- 禁止后台上传原始屏幕截图；禁止上传完整 Accessibility Tree；
- 日志上传必须脱敏、最小化、用户可控；
- 诊断图片仅用户主动开启诊断模式时保存本机。

## 三、报告安全漏洞

请通过 GitHub Issues（附复现步骤与脱敏日志）报告，勿公开张贴任何令牌或
签名材料。
