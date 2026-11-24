#!/bin/bash
# 服务级 JVM 回归跑器（v7.15.8 回归基建）
# 用真实 AdSkipService + 桩 Android API 直接执行服务主链路（事件→扫描→执行→日志）。
# 与 run-tests.sh（核心类单测）互补：服务自身的回归从此有防线
# （v7.15.7 事故根因：服务代码从未被任何测试执行过）。
set -e
set -o pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS=/home/z/android-tools
ECJ="$TOOLS/ecj.jar"
OUT="$ROOT/build-service-tests"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

# 编译集：真实核心 + 真实服务 + 桩 Android/同包替身 + 测试驱动
find "$ROOT/src/com/zai/adshield/core" \
     "$ROOT/test-host/android" \
     "$ROOT/test-host/org" \
     -name "*.java" > "$OUT/sources.txt"
echo "$ROOT/src/com/zai/adshield/AdSkipService.java" >> "$OUT/sources.txt"
find "$ROOT/test-host/com" -name "*.java" >> "$OUT/sources.txt"

if command -v javac >/dev/null 2>&1; then
  echo "==> [1/2] javac 编译（服务 + 桩 + 驱动）"
  COMPILE_LOG=$(javac --release 8 -encoding UTF-8 -nowarn -d "$OUT/classes" @"$OUT/sources.txt" 2>&1) || {
    echo "$COMPILE_LOG" | head -40
    echo "✗ 编译失败"
    exit 1
  }
else
  echo "==> [1/2] ECJ 编译（服务 + 桩 + 驱动）"
  COMPILE_LOG=$(java -jar "$ECJ" -1.8 -encoding UTF-8 -warn:none \
    -d "$OUT/classes" @"$OUT/sources.txt" 2>&1) || {
    echo "$COMPILE_LOG" | head -40
    echo "✗ 编译失败"
    exit 1
  }
fi

echo "==> [2/2] 执行服务级回归套件"
java -cp "$OUT/classes" com.zai.adshield.ServiceHostTest
