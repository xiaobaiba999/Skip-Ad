# 迭代报告 · v8.0.0 —— 构建与交接轮（2026-11-26）

> 本轮定位（用户指令）：**第一优先级 = 双仓库 GitHub 交接文档（换大模型可无缝恢复）；第二优先级 = v8.0.0 APK 自动化构建、安装包检查与发布准备**。不依赖聊天记录作为项目文档——一切固化进仓库。

---

## 一、执行前基线核对（真实 Git 历史，不信任历史 SHA）

- 主仓 `xiaobaiba999/Skip-Ad`（本地 `/home/z/skip-ad-repo`）：工作树干净、main、HEAD = `cd669aaca32af99bfe69a691dd2ee4dd71e6c51a`（v8.0.0 客户端上传链路提交）——`git ls-remote` 远端一致；零 stash、零未跟踪。
- Worker 仓 `xiaobaiba999/Skip-Ad-Log-Upload`（本地 `/home/z/my-project`——**注意：该仓库克隆根即 Next.js 沙箱项目根**，git 操作必须从根执行）：工作树干净、main、HEAD = `1c1f97da3d8c427694d999be366c2bf6027aeb25`（INTEGRATION-ANDROID STAGED→INTEGRATED——比早前会话记录的 435e47a 更新，以真实历史为准）、远端一致。
- 版本权威：`src/lib/version.ts` = 8.0.0 / 80000；`AndroidManifest.xml` 6.2.0/60200 为构建模板值（build.sh 构建期注入）。
- 网络注意项：GitHub 偶发 GnuTLS 抖动 → `git -c http.version=HTTP/1.1` + token extraheader 稳定复现。

## 二、双仓库交接文档（第一优先级 · 已推送并核验）

| 项 | 内容 |
|---|---|
| 主仓文档 | `docs/PROJECT-HANDOFF-V8.0.0.md`（245 行）：A 项目概况 / B 版本历史（v7.9.1–v8.0.0 真实 CHANGELOG 标题）/ C 五模块（职责·调用关系·真实行号接线点）/ D 上传架构与 12 项行为问答 / E 隐私红线 / F 代码位置全表 / G 测试与证据（上轮报告 + 本轮重跑）/ H 未完成 8 项 / I P0–P5 + 20 项真机验证计划 / J 纪律全文 |
| Worker 文档 | `HANDOFF-V8.0-WORKER.md`（127 行，仓库根）：A 架构与仓库布局特殊性 / B API 契约全表（**以真实代码为准，比史料多出 413/500/502/503/404/405**）/ C 幂等与限流（状态机·墓碑·reconcile）/ D GitHub App（App 5859168 · Installation **184262217**——史料"201"为 HTTP 状态码误抄，已据实修正）/ E 部署测试状态（DEPLOYED_BUT_NOT_E2E_VERIFIED 不升格）/ F Android 集成 / G 待办 |
| 主仓 commit | `76350715de589a0fdab17cc6dc62be950bb308d2`（docs: add v8.0.0 project handoff） |
| 主仓远端 | `git ls-remote` = `7635071` —— **本地 == 远端 核验一致** |
| Worker commit | `5a02f8cc38a10731753552280a24b7e441396070`（docs: add worker project handoff） |
| Worker 远端 | `git ls-remote` = `5a02f8c` —— **本地 == 远端 核验一致** |

## 三、APK v8.0.0 构建（第二优先级 · 真实构建）

- **工具链补齐**：官方 `build-tools_r34-linux.zip`（61,224,257 字节——与 dl.google.com 官方字节一致）经腾讯镜像完整下载（官方源 ~34KB/s 过慢；镜像 1.25MB/s），`unzip -t` 完整性校验通过；解压至 `/home/z/android-tools/bt/android-14`（build.sh 默认路径）。aapt2 2.19-10229193 / d8 8.2.2-dev / apksigner / zipalign 四件实测可用。
- **构建执行**：`cd android-app && ./build.sh 80000 8.0.0` —— 三道前置闸（secret-scan / app.js 符号闸 / versionCode 防降级守卫 80000 > 79600）全过；六步流水线（版本注入→aapt2 compile→link→ECJ 编译→d8→zipalign→apksigner v1+v2）完成。
- **产物**：`android-app/dist/adshield-v8.0.0.apk`（878,134 字节）+ `.idsig`。

## 四、APK 静态检查（全过 · AUTOMATED 级）

| 检查项 | 结果 |
|---|---|
| 存在性/大小 | 878,134 字节（860K） |
| 包名 | `com.zai.adshield` |
| versionName / versionCode | `8.0.0` / `80000`（> dist 历史 79600——覆盖安装即升级） |
| minSdk / targetSdk | 24 / 34 |
| 签名 | `apksigner verify` = **Verifies**（v2 ✓ v3 ✓；v1 块在场）；证书 DN `CN=AdShield`；证书 SHA-256 `05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79` **与 v7.9.6 完全一致**（同 keystore → 覆盖安装兼容）；非 debug 密钥 |
| ZIP 结构 | 22 项：AndroidManifest.xml / res / resources.arsc / assets/www（app.js·index.html·style.css·mascot）/ assets/subsnap×3 / classes.dex（319,348B）/ META-INF v1 签名块 |
| DEX 内容 | 141 类；五 Log 模块（LogPrivacyFilter/LogEvents/LogUploadPolicy/LogBatchStore/LogUploadDispatcher）全部在场；测试类 0 命中；org.json shim 类描述符 0 命中（dexdump 级验证——仅存无害字符串引用） |
| 生产端点 | `skip-ad-log-relay.2927609408.workers.dev/api/logs/receive` 在场（`Net.LOG_RELAY_URL`） |
| 敏感扫描 | dex + app.js 全量：ghp_/github_pat_/BEGIN PRIVATE KEY/xox-/AIza/AKIA 零命中 |
| SHA-256 | `a6782b0696ba447938371977da99802e9add670191b8aaa924c99acc9338235c` |

## 五、本轮接管重跑（历史结果不作新证据 · 全部现跑现报）

- Android JVM：`run-tests.sh` = **34 套件 · 6802 断言 · 0 失败**（CHANGELOG 记"35 套件 6802 断言"——断言数吻合；套件数为口径差（35 含 TestHost），以实跑产物 34 计并已如实注记）
- `check-version-sync.sh` = PASS（8.0.0 / 80000 双端统一）
- `secret-scan.sh` = PASS
- `check-appjs.sh` = PASS（语法+顶层执行+五符号在场）
- Worker：`bun test` = **53 pass / 0 fail / 424 expect()**（8 文件，2.77s）
- `git diff --check` 双仓全过

## 六、构建自动化与发布准备

- **GitHub Actions**：新增 `.github/workflows/build-apk.yml`（workflow_dispatch 输入 versionCode/versionName · ubuntu-latest + JDK17 + sdkmanager build-tools;34.0.0/platforms;android-34 · 复用 `./build.sh`（同版本重建时临时挪开 dist 同版本包防误拒，守卫对降级全程在岗）· 构建后验证步六项（存在/签名/包名/版本/SDK/SHA-256 任一不符硬失败）· upload-artifact v4 上传 APK+idsig+sha256）；归档副本 `docs/ci-workflows/build-apk.yml` 同步入库。
- **已知事实（如实）**：本轮 push 所用 PAT scopes = `read:org, repo`（无 `workflow` scope）——workflow 文件推送**实测被远端拒收**（`refusing to allow ... without workflow scope`）。处置已完成：APK/文档/归档副本一 commit 先推成功（`33eb80e`）；workflow 提交已 soft-reset 摘除、文件保留本地工作树（未跟踪，内容与远端归档副本逐字一致）——仓库回到与远端同步状态，**不影响后续轮次推送**。激活路径（二选一，一分钟操作）：① GitHub 网页直接创建 `.github/workflows/build-apk.yml`（内容用 `docs/ci-workflows/build-apk.yml`）；② 用户换带 workflow scope 的 token 重推本地文件。
- **发布口径**：本轮**不正式发布** GitHub Release；**草稿已就绪**（draft #453676040：APK 资产 878134B 已上传、正文含校验表与 RELEASE_CANDIDATE 诚实口径——真机验证未完成前保持 draft，发布即一键 Publish）；v8.0.0 = **RELEASE_CANDIDATE**（构建+静态检查全过；真机验证未完成——不伪装成稳定发布）。

## 七、诚实状态（证据等级）

| 项 | 等级 |
|---|---|
| 双仓交接文档推送+SHA 核验 | REAL_API（git push + ls-remote 实测） |
| APK 构建与静态检查 | AUTOMATED（本地官方工具链实构建实检查） |
| JVM 测试 6802/0、Worker 53/0/424 | AUTOMATED（本轮现跑） |
| Worker /healthz | REAL_API（用户真实浏览器，上轮） |
| 真机安装/回归/上传闭环/Doze/重启 | **NOT_AVAILABLE（本沙箱无设备）** |
| Worker E2E | NOT_RUN（状态保持 DEPLOYED_BUT_NOT_E2E_VERIFIED） |

## 八、本轮事故与修正（如实记录）

- **同形字误读事故（已修正·零生产影响）**：会话史料中 GitHub owner 被记作 `xiaobaika999`（k）——字节级核验证实为不存在的误拼（真实账号 `xiaobaiba999`，b）。本轮受其污染：新写的三份交接文档一度写入 (k) 拼写、草稿 Release 脚本用 (k) 路径导致 "Not Found"。**生产代码从未受影响**（version.ts / Net.java / git remote / Worker wrangler REPO_OWNER 四处字节级验证从来都是正确的 (b)——无账户改名、无配置失效、Worker 上传闭环不受影响）。已处置：三文档 9 处拼写全部修正 + 双仓交接文档加入「所有者拼写陷阱」警示条目（该项目历史上已有 (z) 拼写 404 事故先例——wrangler.toml 注释在案）。教训固化：凡引用 owner 先 `grep -o` 字节级复核，以 `src/lib/version.ts` 为单一权威源。

## 九、下一步（供接手模型）

P2 真机安装回归 → P3 上传闭环 20 项验证 → P4 Doze/断网/重启专项 → P5 publish-v800 正式发布（真机验证通过后）。全部细节见 `docs/PROJECT-HANDOFF-V8.0.0.md` H/I 节。
