# skip-ad-update-relay —— 更新检查中转接口（v8.2.0）

## 这是什么

`GET /api/update/latest`——部署到现有 `skip-ad-log-relay` Cloudflare Worker 的
**独立只读**端点，作为 Android v8.2.0 双通道更新检查的第 0 通道：

```
设备 →（通道 0）Worker 边缘缓存 5~10min → GitHub Releases（GitHub App 认证 → 匿名回退）
   └→（通道 1）Worker 失败时：GitHub 匿名 API 直连（v8.0.3 既有语义不变）
```

**解决什么**：v8.0.3 真机实锤——检查更新唯一通道是 GitHub 匿名 API（60 次/小时/IP），
国内共享出口极易耗尽，用户看到「查询失败：GitHub API 限流」而看不到新版本。

**为什么这样设计**：
- 缓存把上游压力压到 ~6 次/小时/边缘节点（<< 60 次/小时）——**部署即修复**，
  不依赖任何 GitHub App 权限变更；
- GitHub App 认证（独立配额池，增强项）凭据只存 Worker Secret，权限不足自动
  回退匿名——**绝不要求扩权**；
- 只读公开 Release 元数据，零用户数据零写操作，与日志上传链路完全隔离。

## 目录

```
worker/
  src/update.ts           自包含模块（接线进现有 Worker 的 fetch handler）
  test/update.test.ts     单元测试（bun test · 22 用例 · 零网络）
  test/update.live.test.ts live 测试（REAL_API 模块级 · RUN_LIVE=1 + GH_TOKEN）
  run-tests.sh            测试跑器
  wrangler.example.toml   配置样例（Secrets 说明）
  DEPLOY.md               部署指南（唯一用户操作：wrangler deploy / Dashboard 粘贴）
```

## 测试

```sh
cd worker
bun test test/update.test.ts                        # 单元（AUTOMATED）
RUN_LIVE=1 GH_TOKEN=… bun test test/update.live.test.ts   # live（REAL_API 模块级）
```

## 状态口径（如实）

- 模块代码 + 单元测试 + live 测试：**已完成并通过**（本仓）。
- 部署实例：**PENDING_DEPLOY**——本沙箱无 Cloudflare 凭据（不索取）；部署后
  Android v8.2.0 自动启用通道 0，部署前自动回退通道 1（行为= v8.0.3，零回退风险）。
