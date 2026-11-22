#!/bin/sh
# 行为级 JS 测试跑器（v7.9.8b 入库——此前散落沙箱临时目录未跟踪）
# ─────────────────────────────────────────────────────────────
# 与 scripts/check-appjs.sh（语义级三重断言闸）互补：本跑器执行
# tests-js/ 下的行为测试（真实 app.js 全量加载 + 断言用户可感行为）。
#  · test_bugtip24h.js    —— Bug 反馈提示 24h 节流 / 5min 防重建 /
#                           版本判定真值表 / 旧版 cmpVer 兼容（legacy 资产）
#  · test_upload_errors.js —— 上传错误八类人话映射（含 v7.9.8b 裸
#                           401→AUTH_FAILED / 403→FORBIDDEN 分家）
# 用法: ./android-app/tests-js/run.sh   （bun 或 node 任一）
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"

RUNNER=""
if command -v bun >/dev/null 2>&1; then RUNNER="bun"
elif command -v node >/dev/null 2>&1; then RUNNER="node"
else
  echo "✗ 无 bun/node——无法运行 JS 行为测试"; exit 1
fi

FAIL=0
for T in "$ROOT"/test_*.js; do
  [ -f "$T" ] || continue
  echo ""
  echo "══ $(basename "$T") ══"
  if ! "$RUNNER" "$T"; then
    echo "❌ $(basename "$T") 失败"
    FAIL=1
  fi
done
[ "$FAIL" -eq 0 ] && echo "\n✓ 全部 JS 行为测试通过" || { echo "\n❌ 存在失败"; exit 1; }
