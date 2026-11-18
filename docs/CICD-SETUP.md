# CI/CD 流水线部署指南（v5.4.0）

## 文件位置

两条流水线的完整定义已就位于本仓库：

- `docs/ci-workflows/ci.yml` —— 持续集成（push/PR 触发：Web 端 tsc + lint / Android 端 769 项 JVM 单元断言 + lint）
- `docs/ci-workflows/release.yml` —— 自动发布（tag `v*` 触发：签名构建 APK → conventional commits 自动更新日志 → GitHub Release 附产物）

## 为什么没有直接放在 `.github/workflows/`

GitHub 安全策略要求：**通过 git push 创建或更新 `.github/workflows/` 下的文件，推送令牌必须具有 `workflow` scope**。当前用于自动同步的 PAT（Personal Access Token）仅具备 `repo` scope（满足代码推送、Release 发布、Issues 反馈通道），因此 workflow 文件无法随代码推送自动创建。这是一次性限制，不是功能缺失。

## 一次性部署步骤（约 2 分钟）

1. 打开仓库页面 `https://github.com/xiaobaiba999/Skip-Ad`，确保已登录具有**管理员权限**的账号。
2. 依次新建两个文件（Add file → Create new file）：
   - 路径填 `.github/workflows/ci.yml`，内容整体复制 `docs/ci-workflows/ci.yml`
   - 路径填 `.github/workflows/release.yml`，内容整体复制 `docs/ci-workflows/release.yml`
   - 提交信息建议：`ci: 启用 v5.4.0 双流水线`
3. 部署完成。此后每次 push 自动跑 `ci`；打 tag（如 `git tag v5.4.0 && git push origin v5.4.0`）自动触发 `release`。

## 可选：为自动同步令牌补齐 workflow scope

如希望今后 workflow 改动也能由机器人令牌自动推送：

1. GitHub → Settings → Developer settings → Personal access tokens → 选中同步用令牌
2. 勾选 `workflow` scope → Update token
3. 更新服务器端 `src/lib/credentials.ts` 中的混淆令牌（保持双层 XOR 混淆格式），此后 `.github/workflows/` 即可随常规提交自动更新

## Release 签名密钥配置（Secrets）

`release.yml` 需要在仓库 Settings → Secrets → Actions 预置四个变量（均不入库）：

| Secret | 说明 |
| --- | --- |
| `ANDROID_KEYSTORE` | 签名 keystore 文件的 base64 编码串（`base64 -w0 release.jks`） |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名 key 别名 |
| `ANDROID_KEY_PASSWORD` | key 密码 |

无签名密钥时，流水线会自动降级为未签名 debug 构建（产物文件名带 `-unsigned`，供内测）。

## 流水线行为速览

### ci.yml
- **web job**：`bun install` → `prisma generate` → `bunx tsc --noEmit` → `bun run lint`（bun 与 Gradle 缓存加速）
- **android job**：`./gradlew :app:testDebugUnitTest :app:lintDebug`；Android Gradle 脚手架（`settings.gradle`/wrapper）合入前该 job 以 notice 静默通过（渐进合入，不阻塞现有主干）；失败自动归档测试报告 artifact

### release.yml
- tag `v*` 触发 → base64 解码注入签名 → `assembleRelease` → 按 conventional commits 分组生成更新日志（feat/fix/perf/chore 四节）→ `softprops/action-gh-release` 创建 Release 并附 APK → Web 端构建自检（Vercel/静态导出双备选，见 yml 注释）

## 与测试体系的衔接

`ci.yml` 的 Android job 激活后即执行 `docs/TEST-PLAN.md` 定义的 769 项 JVM 断言（427 存量 + 342 新增）；建议后续按 TEST-PLAN §2.1 增加 `golden/dsl-cases.json` 金样本集，将「跨端 DSL 判定一致性」纳入 CI 门禁（TS 与 Java 双端跑同一批用例，结果必须逐字节一致）。
