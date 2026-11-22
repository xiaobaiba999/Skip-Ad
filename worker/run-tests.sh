#!/bin/sh
# Worker 更新接口测试跑器（v8.2.0）
# 单元（零网络）恒跑；live（真实 GitHub 上游·REAL_API 模块级）仅当 GH_TOKEN 在场。
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo "==> [1/2] 单元测试（AUTOMATED · 零网络）"
bun test test/update.test.ts

if [ -n "$GH_TOKEN" ]; then
  echo "==> [2/2] live 测试（REAL_API 模块级 · 真实 GitHub 上游）"
  RUN_LIVE=1 GH_TOKEN="$GH_TOKEN" bun test test/update.live.test.ts
else
  echo "==> [2/2] 跳过 live 测试（未提供 GH_TOKEN——认证通道留空则匿名通道也不测）"
fi
echo "══ Worker 更新接口测试完成 ══"
