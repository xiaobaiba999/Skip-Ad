#!/bin/bash
# 开屏卫士 JVM 测试跑器（v5.5.0）——纯 Java 核心零依赖可单测
# 用法: ./run-tests.sh
# v5.5.0：新增 RuleGovernorTest（三源合并/学习治理/分级扫描/新四大场景）套件。
# v7.1.0：新增 FeedAdGuardTest（信息流广告卫士移植回归）+ RuleOrderTest（规则使用顺序治理）
#  + BrandFocus286Test → BrandFocus308Test（11 组×28=308 机型·八维含信息流广告维）。
# v7.7.0：新增 ReentrySplashTest（回切开屏根治·用户十七轮实锤「从另一个 app 切回
#  这个 app 期间会弹广告没有拦截到」——ReentryGuard 真值表/进入期全量门/主界面
#  CLEAN 档案叠开屏层命中/插层键词典/自家包前台边界/离开时长记账全链路）
#  + 品牌矩阵 308→352（每品牌 +4 · 2027 世代机型）+ 第九维回切开屏逐机型验证。
# v7.5.0：新增 InterstitialGuardTest（插屏卫士·用户十四轮实锤「几乎都带有广告两个字」
#  ——名单外 App 插入广告角标+关闭钮真值表/九品牌 1:1 缩放/相册反例零出手）
#  + SplashCountdownTest（兄弟节点倒计时/门控第 0 击起全次生效/中部边带图片跳过键
#  /L1.5 角标在场关闭——用户实锤「不是每个都带倒计时·不是每次每个按钮都是一个位置」）。
# v7.6.0：新增 AggressiveModeTest（激进模式·用户十五轮实锤「你可以激进一点暂时先
#  放弃宁可漏拦绝不误触」——首击零门控时间线/重击 6s 封顶/1:1 人手多点防线时间线
#  /九品牌触点钳制/决策梯子不变式）+ InterstitialGuardTest 重做为激进版
#  （广告语境 contains 在场闸/关闭词扩表/interBoundaryCheck 终闸语义面收编）。
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
for SUITE in MiniProgramGuardTest DecisionGuardTest SubscriptionBuiltinTest RegressionCoreTest MultiDeviceMatrixTest NonMainstreamAppTest RestoreGuardTest PowerGovernorTest RuleGovernorTest ComplianceGuardTest TapPointMatrixTest HumanTapMatrixTest BrandFocus396Test ReentrySplashTest ReleaseNotesTest SubMirrorTest FeedAdGuardTest RuleOrderTest InterstitialGuardTest SplashCountdownTest AggressiveModeTest VideoSplashTest InterstitialRoutingTest AdTaskTest AppCatalogTest; do
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
