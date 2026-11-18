# CI/CD 流水线部署指南（v5.5.0）

## 文件位置

三条流水线的完整定义已就位于本仓库：

- `docs/ci-workflows/ci.yml` —— 持续集成（push/PR 触发：Web 端 tsc + lint + 版本双端统一门禁 / Android 端 **599 项 JVM 断言**，纯 javac 零 Gradle 依赖）
- `docs/ci-workflows/release.yml` —— 自动发布（tag `v*` 触发：版本门禁 → 全量测试门禁 → `build.sh` 签名构建 APK → conventional commits 自动更新日志 → GitHub Release 附产物 → Web 端构建自检）
- `docs/ci-workflows/sync.yml` —— 规则与数据巡检（每周一东八区 18:00 + 手动触发：内置 GKD 订阅金样本校验 / data/learning 归档完整性 / 版本号巡检 / Web 健康检查）

## 为什么没有直接放在 `.github/workflows/`

GitHub 安全策略要求：**通过 git push 创建或更新 `.github/workflows/` 下的文件，推送令牌必须具有 `workflow` scope**。当前用于自动同步的 PAT（Personal Access Token）仅具备 `repo` scope（满足代码推送、Release 发布、Issues 反馈通道），因此 workflow 文件无法随代码推送自动创建。这是一次性限制，不是功能缺失。

## 一次性部署步骤（约 2 分钟）

1. 打开仓库页面 `https://github.com/xiaobaiba999/Skip-Ad`，确保已登录具有**管理员权限**的账号。
2. 依次新建三个文件（Add file → Create new file）：
   - 路径填 `.github/workflows/ci.yml`，内容整体复制 `docs/ci-workflows/ci.yml`
   - 路径填 `.github/workflows/release.yml`，内容整体复制 `docs/ci-workflows/release.yml`
   - 路径填 `.github/workflows/sync.yml`，内容整体复制 `docs/ci-workflows/sync.yml`
   - 提交信息建议：`ci: 启用 v5.5.0 三流水线（CI/Release/规则巡检）`
3. 部署完成。此后每次 push 自动跑 `ci`；打 tag（如 `git tag v5.5.0 && git push origin v5.5.0`）自动触发 `release`；每周一自动跑 `sync` 巡检。

## 可选：为自动同步令牌补齐 workflow scope

如希望今后 workflow 改动也能由机器人令牌自动推送：

1. GitHub → Settings → Developer settings → Personal access tokens → 选中同步用令牌
2. 勾选 `workflow` scope → Update token
3. 更新服务器端 `src/lib/credentials.ts` 中的混淆令牌（保持双层 XOR 混淆格式），此后 `.github/workflows/` 即可随常规提交自动更新

## Release 流水线行为（v5.5.0 真实工具链）

`release.yml` 完全对齐仓库真实构建链（`android-app/build.sh`：aapt2 + javac + d8 + zipalign + apksigner），无 Gradle 依赖：

1. **版本门禁**：tag 版本 ↔ `src/lib/version.ts`（单一数据源）强制一致；versionCode 由公式 `major*10000+minor*100+patch` 推导并校验（v5.3.0「无法降级安装」事故防复发）
2. **测试门禁**：发布前全量 599 项 JVM 断言，失败即中止发布
3. **签名构建**：`build.sh <code> <version>` 产出 `dist/adshield-v<version>.apk`（同 keystore，用户直接覆盖安装）
4. **更新日志**：conventional commits 分组（feat/fix/perf/chore 四节）
5. **GitHub Release**：附 APK——App 内「我的 → 检查更新」与 Web 端「关于 → 检查更新」接口（`/api/release/latest`）即读此处，页内直下无需跳转

### 签名说明

开源演示项目的 keystore 随仓库分发（`android-app/keystore/adshield.keystore`，密码在 `build.sh` 明文——演示项目透明化取舍）。**正式分发请自行更换**：

1. 生成新 keystore 并放入 CI Secrets（`ANDROID_KEYSTORE_BASE64` 等）
2. 修改 `android-app/build.sh` 签名段从环境变量读取（本地开发可回退仓库 keystore）

## 流水线行为速览

### ci.yml
- **web job**：`bun install` → `prisma generate` → `bunx tsc --noEmit` → `bun run lint` → `scripts/check-version-sync.sh`（版本公式 + dist 防降级门禁；bun 缓存加速）
- **android job**：JDK 17 + `android-app/run-tests.sh`（9 套件 599 断言：小程序防护 / 决策守护 / GKD 订阅 / 回归核心 / 多设备矩阵 / 非主流应用 / 保活自愈 / 功耗治理 / v5.5.0 三源规则治理+新场景+合规守卫）；失败自动归档测试日志 artifact

### sync.yml
- 每周一 18:00（东八区）+ 手动触发：内置订阅金样本（JVM）→ `data/learning` 归档完整性 → 版本巡检 → Web 健康检查；任一环节异常即红灯报警

## 与测试体系的衔接

`ci.yml` Android job 执行 `docs/TEST-PLAN.md` 定义的 599 项 JVM 断言（427 v5.3.0 存量 + 48 功耗治理 + 77 三源规则治理/新场景 + 47 合规与形态守卫）。金样本集（`golden/dsl-cases.json` 跨端一致性）建议按 TEST-PLAN §2.1 后续纳入。
