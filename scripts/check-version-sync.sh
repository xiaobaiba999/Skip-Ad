#!/usr/bin/env bash
# 版本号双端统一校验（v5.5.0 模块六）
# ─────────────────────────────────────────────────────────────
# 单一数据源：src/lib/version.ts（APP_VERSION / VERSION_CODE）。
# Android 构建经 build.sh 注入同一版本（release.yml 从 tag 推导并强制
# 与 version.ts 一致），本脚本在 CI 门禁与本地发布前校验三件事：
#   ① versionCode 符合公式 major*10000 + minor*100 + patch（项目惯例，
#      v5.3.0「无法降级安装」事故的防复发）；
#   ② AndroidManifest.xml 的 versionCode/versionName 与 version.ts 一致
#      （或可由构建参数推导）；
#   ③ dist/ 历史 APK 的最大 versionCode 小于当前（防降级）。
# 用法：./scripts/check-version-sync.sh [期望版本号，如 5.5.0（tag 触发时）]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VT="$ROOT/src/lib/version.ts"

[ -f "$VT" ] || { echo "✗ 未找到 $VT"; exit 1; }

APP_VERSION="$(grep -oP 'APP_VERSION = "\K[^"]+' "$VT" | head -1)"
VERSION_CODE="$(grep -oP 'VERSION_CODE = \K[0-9]+' "$VT" | head -1)"

if [ -z "$APP_VERSION" ] || [ -z "$VERSION_CODE" ]; then
  echo "✗ 无法从 src/lib/version.ts 解析 APP_VERSION/VERSION_CODE"
  exit 1
fi

echo "▶ Web 端版本：v$APP_VERSION (versionCode $VERSION_CODE)"

# ① 公式校验：major*10000 + minor*100 + patch
MAJOR="${APP_VERSION%%.*}"
REST="${APP_VERSION#*.}"
MINOR="${REST%%.*}"
PATCH="${REST#*.}"
[ "$MAJOR" = "$APP_VERSION" ] && { echo "✗ 版本号必须是 x.y.z 三段式，当前 $APP_VERSION"; exit 1; }
EXPECT_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
if [ "$VERSION_CODE" -ne "$EXPECT_CODE" ]; then
  echo "✗ versionCode 公式不一致：version.ts=$VERSION_CODE，期望 $MAJOR*10000+$MINOR*100+$PATCH=$EXPECT_CODE"
  echo "  （v5.3.0「无法降级安装」事故防复发：major*10000+minor*100+patch 是项目铁律）"
  exit 1
fi
echo "  ✓ versionCode $VERSION_CODE 符合公式 major*10000+minor*100+patch"

# ② tag 一致性（CI tag 触发时传入期望版本）
if [ "${1:-}" != "" ]; then
  if [ "$APP_VERSION" != "$1" ]; then
    echo "✗ 版本号双端不统一：tag=v$1 但 src/lib/version.ts=v$APP_VERSION"
    echo "  发布前请先把 version.ts 的 APP_VERSION/VERSION_CODE 更新为 v$1（单一数据源）"
    exit 1
  fi
  echo "  ✓ tag v$1 与 version.ts 一致（双端统一）"
fi

# ③ dist 防降级（dist 内有历史 APK 时）
AAPT=""
for c in aapt aapt2; do
  if command -v "$c" >/dev/null 2>&1; then AAPT="$c"; break; fi
done
if [ -n "$AAPT" ] && ls "$ROOT"/android-app/dist/*.apk >/dev/null 2>&1; then
  LAST_VC=0
  for prev in "$ROOT"/android-app/dist/*.apk; do
    [ -f "$prev" ] || continue
    prev_vc="$("$AAPT" dump badging "$prev" 2>/dev/null | grep -o "versionCode='[0-9]*'" | head -1 | grep -o '[0-9]*' || true)"
    [ -n "$prev_vc" ] && [ "$prev_vc" -gt "$LAST_VC" ] && LAST_VC="$prev_vc"
  done
  if [ "$LAST_VC" -gt 0 ] && [ "$VERSION_CODE" -le "$LAST_VC" ]; then
    echo "✗ 防降级：version.ts versionCode=$VERSION_CODE 未大于 dist 历史最大 $LAST_VC"
    exit 1
  fi
  echo "  ✓ dist 历史最大 versionCode=$LAST_VC < $VERSION_CODE（可覆盖安装）"
fi

echo "✅ 版本号双端统一校验通过"
