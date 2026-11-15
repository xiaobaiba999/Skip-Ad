#!/usr/bin/env bash
# 开屏卫士 APK 构建脚本（极简工具链：aapt2 + javac + d8 + zipalign + apksigner）
# 用法: ./build.sh <versionCode> <versionName> [输出路径.apk]
# 例:   ./build.sh 3 1.2.0 dist/adshield-v1.2.0.apk
#
# 覆盖安装要求：同 keystore 签名 + versionCode 递增 → 手机上直接安装新版即可覆盖升级，无需卸载。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="${ANDROID_TOOLS:-/home/z/my-project/android-tools}"
BT="$TOOLS/build-tools"
AJ="$TOOLS/android-34/android.jar"
JDK="$TOOLS/jdk"
export PATH="$JDK/bin:$BT:$PATH"

VC="${1:?versionCode 必填，如 1}"
VN="${2:?versionName 必填，如 1.0.0}"
OUT="${3:-$ROOT/dist/adshield-v${VN}.apk}"

BUILD="$ROOT/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$(dirname "$OUT")"

echo "▶ 1/6 注入版本号 versionCode=$VC versionName=$VN"
sed -e "s/android:versionCode=\"[0-9]*\"/android:versionCode=\"${VC}\"/" \
    -e "s/android:versionName=\"[^\"]*\"/android:versionName=\"${VN}\"/" \
    "$ROOT/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"

echo "▶ 2/6 编译资源 (aapt2 compile)"
aapt2 compile --dir "$ROOT/res" -o "$BUILD/res.zip"

echo "▶ 3/6 链接资源 (aapt2 link)"
aapt2 link -o "$BUILD/base.apk" -I "$AJ" \
  --manifest "$BUILD/AndroidManifest.xml" \
  --java "$BUILD/gen" -A "$ROOT/assets" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --auto-add-overlay "$BUILD/res.zip"

echo "▶ 4/6 编译 Java (javac → d8)"
find "$ROOT/src" "$BUILD/gen" -name "*.java" > "$BUILD/sources.txt"
javac --release 8 -nowarn -cp "$AJ" -d "$BUILD/classes" @"$BUILD/sources.txt"
# 注意：内部类文件名含 $，必须用 -print0 / xargs -0 传递，避免 shell 展开为空
find "$BUILD/classes" -name "*.class" -print0 | xargs -0 d8 --release --lib "$AJ" --min-api 24 --output "$BUILD/dex"

echo "▶ 5/6 打包 dex 并对齐 (zip → zipalign)"
(cd "$BUILD/dex" && zip -q -X "$BUILD/base.apk" classes.dex)
zipalign -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

echo "▶ 6/6 签名 (apksigner, v1+v2)"
apksigner sign \
  --ks "$ROOT/keystore/adshield.keystore" \
  --ks-key-alias adshield \
  --ks-pass pass:adshield2026 --key-pass pass:adshield2026 \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT" "$BUILD/aligned.apk"

echo ""
echo "✅ 构建完成: $OUT ($(du -h "$OUT" | cut -f1))"
echo "── 验证 ──"
apksigner verify --print-certs "$OUT" | sed -n '1,4p'
echo ""
aapt dump badging "$OUT" 2>/dev/null | grep -E "^package|^application-label|sdkVersion|targetSdk" || true
