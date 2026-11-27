# update-system-v809-workflow-hardening.patch 应用说明

## 归档原因（与 v807 补丁同因）

发布令牌仅 `repo` scope（`x-oauth-scopes: repo`，2026-11-27 复验）——GitHub
拒绝无 `workflow` scope 的凭据修改 `.github/workflows/`（remote rejected）。
工作流本体更新需所有者网页端操作，或为发布令牌补 `workflow` scope。

## 修复什么（2026-11-27 事故链的两处工作流盲区）

事故：v8.0.7 已安装用户检测不到 v8.0.8（17h+）。

1. **update-manifest.yml 公式闸失败（run 45857895571 / 45974347469 实锤）**：
   生成步骤校验 `versionCode = tag 公式`（major*10000+minor*1000+patch*100），
   而 8.0.x 自 v8.0.7 起显式抬码（87100/88000/88100 ≠ 公式值）→ 生成步骤
   exit 2 失败 → 后续所有步骤（含 purge）被连带跳过。补丁：Release 正文
   行首 `versionCode: N` 元数据行透传 `--version-code`（正文无该行时行为
   与 7.x 历史完全兼容）。
2. **purge 步骤触发面盲区**：旧条件 `skipped != 'true' && COMMITTED == 'true'`
   ——发布链手动提交清单后（发布流程实际路径），工作流陈旧守卫命中 SKIP →
   purge 不执行；工作流幂等重跑 diff 为空 → COMMITTED 未置位 → purge 不
   执行。任一场景下 jsDelivr @main 清单缓存无人清洗 → 全体旧客户端
   「已是最新」。补丁：gen 未失败即执行 purge（无论是否产生新提交），
   并同步清洗 kilo 历史仓清单（8.0.x 旧客户端回退源）。
3. **purge-update-cdn.yml（新增工作流）**：`update-manifest.yml` 的 release
   事件对「清单先行手动提交」场景天然不可达，新增第三道清洗触发面：
   push 触达 `update/latest.json` + 每日定时（与客户端每日检查节奏对齐，
   保证任何一次 purge 失败 ≤24h 收敛）+ workflow_dispatch 手动即修；
   清洗后逐域核验（cdn/gcore/fastly/testingcf × 双仓）与 main 实际内容
   对比，如实呈现。

## 应用方式

```bash
git apply docs/patches/update-system-v809-workflow-hardening.patch
git commit -am "ci: 更新系统工作流加固（公式闸显式透传 + purge 触发面三重化）"
git push
```

或网页端对照补丁手工编辑 `.github/workflows/update-manifest.yml` 并新建
`.github/workflows/purge-update-cdn.yml`。

## 不依赖本补丁的既有防线（v8.0.9 已生效）

- **客户端多源解析**（v8.0.9+）：多源合流取最大 versionCode + @tag 不可变
  键探测 + GitHub API 兜底——单一 CDN 陈旧不再能掩盖新版本（对已安装的
  v8.0.7/v8.0.8 无效，故仍需清洗）。
- **发布链固化清洗**：`scripts/purge-update-cdn.mjs`（发布后必跑，自动
  等待 purge 限频窗口 + 逐域验证）。
- **@tag 清单零缓存**：jsDelivr 对 tag 引用不可变解析，无需清洗。
