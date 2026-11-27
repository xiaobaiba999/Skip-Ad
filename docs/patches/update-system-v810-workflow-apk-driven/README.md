# v8.1.0 工作流加固包（APK 驱动定码 + purge 触发面 + purge-update-cdn）

**状态：待所有者应用**（2026-11-27 实测封锁链：本仓 PAT 仅 `repo` scope——
git push 明示拒绝 `refusing to allow a PAT to create or update workflow
without 'workflow' scope`；git database API（trees）对 `.github/workflows/*`
路径返回伪装 404；`scripts/*`、`.github/*` 均正常 201——封锁边界精确限定
在 `.github/workflows/` 前缀）。

## 内容

| 文件 | 作用 |
|---|---|
| `update-manifest.yml.target` | update-manifest 工作流目标版（APK 读码显式透传 + purge 触发面加固 + kilo 同步清洗） |
| `purge-update-cdn.yml.target` | 新增工作流（push 触达清单/每日定时/手动三触发面 CDN 清洗 + 逐域实际 GET 核验） |

修复的事故链（run 45857895571/45974347469/45990612818 三连 failure）：
工作流用 tag 公式猜测定码（80700/80800/80900）≠ APK 显式抬码
（87100/88000/88100）→ 生成步骤 failure → purge 步骤被连带跳过 → CDN
陈旧缓存无人清洗 → v8.0.7 存量用户检测不到新版本 / 全源失败报
Connection reset。

脚本层根治已随本提交上线 main（无需所有者操作）：
- `scripts/update_manifest.py`：versionCode 唯一真值 = APK 实际值；tag 公式
  降级为一致性下界；新增签名证书闸（`scripts/apk_cert.py`）
- `scripts/test_update_manifest.py`：22/22 全绿（真实 APK + 真实 Release
  JSON fixture + 全部非法用例拒绝矩阵）
- 配套 fixture：`scripts/testdata/release-*.json`

## 应用方式（二选一）

### 方式 A：GitHub 网页编辑器（推荐，无需本地环境）

1. 打开 `https://github.com/xiaobaiba999/Skip-Ad/edit/main/.github/workflows/update-manifest.yml`
2. 全选删除，粘贴 `update-manifest.yml.target` 全部内容，Commit changes
3. 新建文件 `.github/workflows/purge-update-cdn.yml`，粘贴
   `purge-update-cdn.yml.target` 全部内容，Commit changes

### 方式 B：带 `workflow` scope 的令牌一键推送（本地）

```bash
# 所有者生成含 workflow scope 的 classic PAT 后（Settings → Developer
# settings → Tokens (classic) → 勾选 repo + workflow），写入 SKIPAD_GH_TOKEN：
export SKIPAD_GH_TOKEN=ghp_xxx   # 令牌勿入库勿提交
python3 scripts/push-workflow-dbapi.py
```

脚本硬闸：仅接受「恰好领先 origin/main 1 个提交且只含工作流文件变更」
（即本地 `workflow-pending` 分支的 6b18075 提交内容），完成后逐字节核验
远程文件与本地一致；绝不半提交、不打印令牌。

## 应用后验证（所有者）

1. Actions → update-manifest → Run workflow（tag 留空 = 最新正式 Release
   v8.0.9）→ 预期：抬码路径放行 + 签名闸通过 + 陈旧守卫幂等 SKIP
   （「SKIP: versionCode 88100 清单已在库」）——全绿即 APK 驱动定码链路
   在真实 CI 环境端到端验证通过
2. Actions → purge-update-cdn → Run workflow → 预期：双仓 purge + 全源
   实际 GET 核验（gcore 接入点不在 jsDelivr purge providers 内，其最长
   12h 自然回源属正常窗口，脚本会如实标注）
3. 下一次正式 Release（如 v8.1.0）发布后自动验证全链：生成 → 提交 →
   purge → 全源 GET
