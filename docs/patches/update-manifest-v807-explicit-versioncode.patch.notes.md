# update-manifest 工作流显式抬码透传补丁(v8.0.7+)

## 背景

v8.0.7 起 versionCode 与 7.x 公式(major*10000+minor*1000+patch*100)显式解耦
(versionCode 87100,跨 7.15.x 存量段 85000-86400 的兼容抬码——旧版客户端
只按 versionCode 数值比较,公式码 80700 < 86400 会让 v7.15.x 存量永远发现
不了新版本)。`scripts/update_manifest.py` 已支持 `--version-code` 四重校验
闸(显式必传/等于 APK 实际码/高于公式值/高于 86400),Release 正文携带行首
裸「versionCode: N」元数据行作为显式确认记录。

v8.0.7 发布时(update-manifest run 45857895571)工作流仍是旧公式闸 → exit 2
失败。当次清单由发布链用同一脚本同一校验链手动生成并逐域核验(raw@main +
jsDelivr 三域 + 资产 digest 一致),未绕过任何校验。

## 本补丁

从 Release 正文提取行首「versionCode: N」元数据行,存在时透传
`--version-code N`;正文无该行时行为完全不变(公式闸,7.x 历史兼容)。
应用后 workflow_dispatch 可重放 v8.0.7 清单生成验证(应幂等 SKIP)。

## 应用方式

```bash
git apply docs/patches/update-manifest-v807-explicit-versioncode.patch
git commit -am "ci: update-manifest 工作流支持显式抬码透传(v8.0.7+)"
git push   # 需要带 workflow scope 的凭据(网页端编辑或 workflow scope PAT)
```

## 令牌限制说明

当前发布令牌仅有 repo scope——GitHub 策略禁止无 workflow scope 的 PAT
修改 `.github/workflows/` 下文件(push 会被 remote rejected)。工作流文件
的更新需仓库所有者经网页端编辑或换用带 workflow scope 的令牌推送。
