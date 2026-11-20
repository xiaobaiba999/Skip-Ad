#!/usr/bin/env bash
# 开屏卫士 APK 构建脚本（极简工具链：aapt2 + javac + d8 + zipalign + apksigner）
# 用法: ./build.sh <versionCode> <versionName> [输出路径.apk]
# 例:   ./build.sh 3 1.2.0 dist/adshield-v1.2.0.apk
#
# 覆盖安装要求：同 keystore 签名 + versionCode 递增 → 手机上直接安装新版即可覆盖升级，无需卸载。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

# v7.9.4：密钥泄露扫描硬门——令牌/私钥形态进入被跟踪文件时直接拒绝打包
if [ -x "$ROOT/../scripts/secret-scan.sh" ]; then
  "$ROOT/../scripts/secret-scan.sh" || exit 1
fi

# 工具链定位：本地默认 android-tools/ 目录；CI（GitHub Actions）通过环境变量逐项覆盖。
#   ANDROID_TOOLS        工具链根目录（build-tools/、android-34/、jdk/ 相对它解析）
#   ANDROID_BUILD_TOOLS  aapt2/d8/zipalign/apksigner 所在目录（如 $SDK/build-tools/34.0.0）
#   ANDROID_PLATFORM_JAR android.jar 完整路径（如 $SDK/platforms/android-34/android.jar）
#   ANDROID_JDK          JDK 根目录（bin/javac；仅 JRE 环境自动回退 ECJ）
#   ANDROID_ECJ          ECJ 编译器 jar 路径（javac 不可用时回退用）
BASE="${ANDROID_TOOLS:-/home/z/android-tools}"
BT="${ANDROID_BUILD_TOOLS:-$BASE/bt/android-14}"
AJ="${ANDROID_PLATFORM_JAR:-$BASE/pf/android-34/android.jar}"
JDK="${ANDROID_JDK:-}"
ECJ_JAR="${ANDROID_ECJ:-$BASE/ecj.jar}"
if [ -n "$JDK" ]; then export PATH="$JDK/bin:$BT:$PATH"; else export PATH="$BT:$PATH"; fi

# 编译器选择：优先 javac，无 javac（仅 JRE）时回退 ECJ
if command -v javac >/dev/null 2>&1; then
  COMPILE() { javac --release 8 -nowarn -cp "$AJ" -d "$BUILD/classes" @"$BUILD/sources.txt"; }
else
  COMPILE() { java -jar "$ECJ_JAR" -1.8 -warn:none -cp "$AJ" -d "$BUILD/classes" @"$BUILD/sources.txt"; }
fi

VC="${1:?versionCode 必填，如 1}"
VN="${2:?versionName 必填，如 1.0.0}"
OUT="${3:-$ROOT/dist/adshield-v${VN}.apk}"

# ── versionCode 单调递增守卫（v5.3.0 事故防复发）────────────────────────────
# 事故：v5.3.0 构建时误传 versionCode=530（< v5.2.0 的 50200），用户覆盖安装
# 被系统拒绝，提示「无法降级安装」。守卫扫描 dist 历史包，一旦新 VC 不大于
# 历史最大 VC 立即终止构建，从源头杜绝此类事故。
LAST_VC=0
LAST_REF=""
for prev in "$ROOT"/dist/*.apk; do
  [ -f "$prev" ] || continue
  prev_vc="$(aapt dump badging "$prev" 2>/dev/null | grep -o "versionCode='[0-9]*'" | head -1 | grep -o '[0-9]*')"
  [ -n "$prev_vc" ] || continue
  if [ "$prev_vc" -gt "$LAST_VC" ]; then LAST_VC="$prev_vc"; LAST_REF="$(basename "$prev")"; fi
done
if [ "$LAST_VC" -gt 0 ] && [ "$VC" -le "$LAST_VC" ]; then
  echo "✗ 拒绝构建：versionCode=$VC 未大于历史最大 versionCode=$LAST_VC（来自 dist/$LAST_REF）"
  echo "  Android 系统会拒绝覆盖安装并提示「无法降级安装」。"
  echo "  请使用更大的 versionCode（当前版本号建议 major*10000+minor*100+patch）。"
  exit 1
fi

BUILD="$ROOT/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$(dirname "$OUT")"

echo "▶ 1/6 注入版本号 versionCode=$VC versionName=$VN"
sed -e "s/android:versionCode=\"[0-9]*\"/android:versionCode=\"${VC}\"/" \
    -e "s/android:versionName=\"[^\"]*\"/android:versionName=\"${VN}\"/" \
    "$ROOT/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"

# ── v7.6.1 页面资产符号校验闸（打包前硬门）──────────────────────────────
# 死因（用户实锤「扫描日志，7.6版本记录没了，使用报告也不见」）：v7.6.0 的
# assets/www/app.js 版本记录注释未闭合——块注释吞掉 const CURRENT_RELEASE
# 声明（纯语法闸拦不住，坏资产直接进了已发布 APK）。此闸语义级三重断言：
# 语法解析 + stub 环境顶层执行 + 关键符号（版本记录/扫描日志/使用报告接线）
# 在场——缺一即拒绝打包（详见 scripts/check-appjs.sh）。
if [ -f "$ROOT/../scripts/check-appjs.sh" ]; then
  "$ROOT/../scripts/check-appjs.sh" "$ROOT/assets/www/app.js"
fi

echo "▶ 2/6 编译资源 (aapt2 compile)"
aapt2 compile --dir "$ROOT/res" -o "$BUILD/res.zip"

echo "▶ 3/6 链接资源 (aapt2 link)"
aapt2 link -o "$BUILD/base.apk" -I "$AJ" \
  --manifest "$BUILD/AndroidManifest.xml" \
  --java "$BUILD/gen" -A "$ROOT/assets" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --auto-add-overlay "$BUILD/res.zip"

echo "▶ 4/6 编译 Java (javac/ECJ → d8)"
find "$ROOT/src" "$BUILD/gen" -name "*.java" > "$BUILD/sources.txt"
COMPILE
# 注意：内部类文件名含 $，必须用 -print0 / xargs -0 传递，避免 shell 展开为空
find "$BUILD/classes" -name "*.class" -print0 | xargs -0 d8 --release --lib "$AJ" --min-api 24 --output "$BUILD/dex"

echo "▶ 5/6 打包 dex 并对齐 (zip → zipalign)"
(cd "$BUILD/dex" && zip -q -X "$BUILD/base.apk" classes.dex)
zipalign -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

echo "▶ 6/6 签名 (apksigner, v1+v2)"
# v7.9.4：签名口令经环境变量优先注入（KS_PASS/KEY_PASS——安全存储与 CI 用）；
# 未注入时回退内置口令（见 docs/SECURITY.md R2：现钥公开为知情接受现状，
# v8.0 起迁移新钥）。
KS_PASS_VAL="${KS_PASS:-adshield2026}"
KEY_PASS_VAL="${KEY_PASS:-adshield2026}"
apksigner sign \
  --ks "$ROOT/keystore/adshield.keystore" \
  --ks-key-alias adshield \
  --ks-pass "pass:$KS_PASS_VAL" --key-pass "pass:$KEY_PASS_VAL" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT" "$BUILD/aligned.apk"

echo ""
echo "✅ 构建完成: $OUT ($(du -h "$OUT" | cut -f1))"
echo "── 验证 ──"
apksigner verify --print-certs "$OUT" | sed -n '1,4p'
echo ""
aapt dump badging "$OUT" 2>/dev/null | grep -E "^package|^application-label|sdkVersion|targetSdk" || true
