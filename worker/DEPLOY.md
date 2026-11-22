# 更新中转接口部署指南（GET /api/update/latest）

> 目标：给现有 `skip-ad-log-relay` Worker（`https://skip-ad-log-relay.2927609408.workers.dev`）
> 加一个**独立只读**更新检查端点，解决「检查更新依赖 GitHub 匿名 API（60 次/小时/IP，
> 国内共享出口极易耗尽）」的真机问题。
>
> **本沙箱无 Cloudflare 凭据（也不索取）——部署是唯一用户操作，一条命令完成。**

## 部署前须知（安全与权限边界）

1. **APK 零凭据红线不破**：APK 只含公开端点 URL；GitHub App 私钥等只存 Worker Secret。
2. **绝不要求扩权**：GitHub App（App 5859168 · Installation 184262217）若权限不足
   （Contents:read 未开或安装范围不含公开仓 `xiaobaiba999/Skip-Ad`），接口**自动回退
   匿名 + 10 分钟缓存**——功能完整（缓存把上游压到 ~6 次/小时/节点 << 60 次/小时），
   不需要改任何 GitHub App 权限。要启用认证通道才按需自查权限（见下节）。
3. **只读**：本接口零写操作、零用户数据，与日志上传端点（`/api/logs/receive`）完全隔离。

## GitHub App 权限自查（可选——认证通道增强项）

认证通道需要两个条件（均为 GitHub 设置页自助核验，**不擅自扩权，缺省即回退匿名**）：

| 条件 | 位置 | 要求 |
| --- | --- | --- |
| App 权限 | GitHub Settings → Developer settings → GitHub App 5859168 → Permissions | Contents: **Read-only** |
| 安装范围 | GitHub App 安装设置（Installation 184262217） | Repository access 含 `xiaobaiba999/Skip-Ad`（公开仓；All repositories 亦可） |

部署后用响应里的 `source` 字段即可判定实际形态：`"auth"` = 认证通道；`"anonymous"` = 匿名回退。

## 方式一：wrangler CLI（推荐）

```sh
# 1) 在私有仓 Skip-Ad-Log-Upload 工作区（或本目录接好线后）
#    把本模块接入现有 fetch handler（见下方「接线」）
# 2) Secrets（若日志中转已配 GitHub App，沿用同名；否则补三个 Secret）
wrangler secret put APP_ID            # 5859168
wrangler secret put APP_PRIVATE_KEY   # GitHub App 私钥 PEM（粘贴整段含 BEGIN/END 行）
wrangler secret put INSTALLATION_ID   # 184262217
#    ⚠ 私钥文件末尾保留换行再粘贴；名称与现有 Worker 不一致时改 env 读取名或加同名 Secret
# 3) 部署
wrangler deploy
# 4) 验证（应返回 ok:true + tag + source 字段）
curl -s https://skip-ad-log-relay.2927609408.workers.dev/api/update/latest | head -c 400
```

## 方式二：Cloudflare Dashboard（无 CLI 场景）

1. Workers & Pages → `skip-ad-log-relay` → Settings → Variables and Secrets：
   确认/新增 `APP_ID`、`APP_PRIVATE_KEY`、`INSTALLATION_ID`（Type = Secret）。
2. Edit code → 在 `fetch` 路由处加入「接线」分支 → Deploy。

## 接线（现有 Worker 的 fetch handler 加一个分支）

```ts
import { handleUpdateLatest } from "./update";   // 本目录 src/update.ts 原样拷入

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    // ── 新增分支（放在 /api/logs/receive 之前/之后均可——路径互斥）──
    if (url.pathname === "/api/update/latest") {
      return handleUpdateLatest(request, env);
    }
    // ……现有 /api/logs/receive、/healthz 路由保持不变……
  },
} satisfies ExportedHandler<Env>;
```

`src/update.ts` 是**自包含模块**（零依赖、零对现有代码的引用、不碰日志链路的状态），
直接拷贝进现有 Worker 的 `src/` 即可。

## 验证清单（部署后）

```sh
# ① 正式版数据（ok:true / tag / apkDigest:"sha256:…" / source）
curl -s https://skip-ad-log-relay.2927609408.workers.dev/api/update/latest
# ② 缓存生效（两次请求 fetchedAt 相同、第二次 "cached":true）
curl -s …/api/update/latest | python3 -c "import json,sys;print(json.load(sys.stdin)['fetchedAt'])"
curl -s …/api/update/latest | python3 -c "import json,sys;print(json.load(sys.stdin)['cached'])"
# ③ 错误形态（POST → 405 BAD_METHOD）
curl -s -X POST …/api/update/latest
# ④ 回退验证：App 私钥故意不配（或权限不足）→ source:"anonymous" 仍 ok:true
```

## 回滚

删除接线分支那一行 → `wrangler deploy`（本模块无任何持久化/迁移，删除即彻底回滚）。

## 行为契约（冻结——Android v8.2.0 按此对接，双侧测试钉死）

| 项 | 值 |
| --- | --- |
| 方法/路径 | `GET /api/update/latest`（OPTIONS → 204；其他 → 405 `BAD_METHOD`） |
| 成功 | `200` `{ok:true, hasRelease:true, tag,name,notes,publishedAt,url, apkUrl,apkFileName,apkSize,apkMB,apkDigest("sha256:hex"), source:"auth"\|"anonymous", cached:bool, fetchedAt:epochMs}` |
| 无正式版 | `200` `{ok:true, hasRelease:false, source, cached, fetchedAt}` |
| 上游限流 | `502` `{ok:false, error:"GITHUB_RATE_LIMITED"}` |
| 上游其他失败/超时 | `502` `{ok:false, error:"GITHUB_ERROR"}` |
| 内部异常 | `500` `{ok:false, error:"INTERNAL_ERROR"}` |
| 缓存 | Cache API 600s（clamp 300~900）+ 失败负缓存 60s + 令牌缓存至过期前 60s |
| 凭据 | 响应**绝不含** Token/私钥；Secrets 仅存 Cloudflare；APK 零凭据 |
| draft/prerelease | 防御性双闸——绝不透传（`/releases/latest` 语义 + 显式再拒） |
