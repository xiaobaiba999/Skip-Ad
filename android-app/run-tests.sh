#!/bin/bash
# 开屏卫士 JVM 测试跑器（v5.5.0）——纯 Java 核心零依赖可单测
# 用法: ./run-tests.sh
# v5.5.0：新增 RuleGovernorTest（三源合并/学习治理/分级扫描/新四大场景）套件。
set -e
set -o pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS=/home/z/android-tools
ECJ="$TOOLS/ecj.jar"
OUT="$ROOT/build-tests"

rm -rf "$OUT"
mkdir -p "$OUT/classes"

find "$ROOT/src/com/zai/adshield/core" "$ROOT/tests" -name "*.java" > "$OUT/sources.txt"

if command -v javac >/dev/null 2>&1; then
  echo "==> [1/3] javac 编译（核心 + 测试）"
  COMPILE_LOG=$(javac --release 8 -encoding UTF-8 -nowarn -d "$OUT/classes" @"$OUT/sources.txt" 2>&1) || true
else
  echo "==> [1/3] ECJ 编译（核心 + 测试）"
  COMPILE_LOG=$(java -jar "$ECJ" -1.8 -encoding UTF-8 -warn:none \
    -d "$OUT/classes" @"$OUT/sources.txt" 2>&1) || true
fi
echo "$COMPILE_LOG" | head -30

if [ ! -f "$OUT/classes/com/zai/adshield/test/TestHost.class" ] \
   || [ "$(echo "$COMPILE_LOG" | grep -c 'ERROR in\|error:')" -gt 0 ]; then
  echo "❌ 编译失败"; exit 1
fi
echo "    编译产出: $OUT/classes"

echo "==> [2/3] 运行测试套件"
TOTAL_PASS=0
TOTAL_FAIL=0
for SUITE in MiniProgramGuardTest DecisionGuardTest SubscriptionBuiltinTest RegressionCoreTest MultiDeviceMatrixTest NonMainstreamAppTest RestoreGuardTest PowerGovernorTest RuleGovernorTest ComplianceGuardTest BrandMatrix96Test; do
  echo ""
  echo "══ $SUITE ══"
  if java -cp "$OUT/classes" "com.zai.adshield.test.$SUITE" | tee "$OUT/$SUITE.log"; then
    P=$(grep -oP '通过 \K[0-9]+' "$OUT/$SUITE.log" | tail -1)
    F=$(grep -oP '失败 \K[0-9]+' "$OUT/$SUITE.log" | tail -1)
    TOTAL_PASS=$((TOTAL_PASS + ${P:-0}))
    TOTAL_FAIL=$((TOTAL_FAIL + ${F:-0}))
  else
    echo "❌ $SUITE 运行失败（非零退出）"
    TOTAL_FAIL=$((TOTAL_FAIL + 1))
  fi
done

echo ""
echo "==> [3/3] 汇总"
echo "══════════════════════════════════════"
echo "  总计通过 $TOTAL_PASS · 总计失败 $TOTAL_FAIL"
echo "══════════════════════════════════════"
[ "$TOTAL_FAIL" -eq 0 ] || exit 1
