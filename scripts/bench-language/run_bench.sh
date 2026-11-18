#!/usr/bin/env bash
# Skip-Ad 语言基准测试统一运行器
# 协议：每种语言 boot×5（外测进程冷启动）+ bench×5（JIT 稳态），中位数聚合
set -uo pipefail

SRC="$(cd "$(dirname "$0")" && pwd)/bench-src"
OUT="$(cd "$(dirname "$0")" && pwd)/results"
KT_CP="/home/z/bench-tools/kotlin-compiler.jar:/home/z/bench-tools/kotlinx-coroutines-core-jvm.jar:/home/z/bench-tools/trove4j.jar:/home/z/bench-tools/annotations.jar"
mkdir -p "$OUT"

# ── 编译 ──
echo "[compile] Java (ECJ → Java 8 bytecode)"
rm -rf "$SRC/classes"; mkdir -p "$SRC/classes"
java -jar /home/z/android-tools/ecj.jar -1.8 -warn:none -d "$SRC/classes" "$SRC/Bench.java" >/dev/null 2>&1 || { echo "Java compile failed"; exit 1; }

echo "[compile] Kotlin (K2 → JVM 1.8 bytecode)"
rm -rf "$SRC/classes-kt"; mkdir -p "$SRC/classes-kt"
java -cp "$KT_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -jvm-target 1.8 -no-stdlib \
  -cp /home/z/bench-tools/kotlin-stdlib.jar -d "$SRC/classes-kt" "$SRC/BenchKt.kt" >/dev/null 2>&1 || { echo "Kotlin compile failed"; exit 1; }

# ── 运行配置 ──
declare -A RUN_BENCH RUN_BOOT
RUN_BOOT[java]="java -cp $SRC/classes Bench boot"
RUN_BENCH[java]="java -cp $SRC/classes Bench bench"
RUN_BOOT[kotlin]="java -cp $SRC/classes-kt:/home/z/bench-tools/kotlin-stdlib.jar Bench boot"
RUN_BENCH[kotlin]="java -cp $SRC/classes-kt:/home/z/bench-tools/kotlin-stdlib.jar Bench bench"
RUN_BOOT[js]="node $SRC/bench.js boot"
RUN_BENCH[js]="node $SRC/bench.js bench"
RUN_BOOT[python]="python3 $SRC/bench.py boot"
RUN_BENCH[python]="python3 $SRC/bench.py bench"

RUNS=9
rm -f "$OUT"/raw_*.jsonl

for lang in java kotlin js python; do
  echo "── $lang ──"
  # boot 外测（进程冷启动：spawn → READY）
  for i in $(seq 1 $RUNS); do
    t0=$(date +%s%N)
    eval "${RUN_BOOT[$lang]}" >/dev/null 2>&1
    t1=$(date +%s%N)
    echo "$(( (t1 - t0) / 1000 ))" >> "$OUT/boot_${lang}.txt"
  done
  # bench（内部计时 JSON）
  for i in $(seq 1 $RUNS); do
    eval "${RUN_BENCH[$lang]}" >> "$OUT/raw_${lang}.jsonl" 2>/dev/null
  done
done

echo "── 聚合 ──"
python3 "$(dirname "$0")/aggregate.py"
