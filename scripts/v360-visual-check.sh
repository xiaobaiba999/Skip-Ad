#!/usr/bin/env bash
# v3.6.0 Web 端视觉自测（修正版 v2：sed 提取 ref）
set -uo pipefail
OUT=/tmp/v360-shots
rm -rf "$OUT" && mkdir -p "$OUT"

get_ref() { # $1=按钮文字 → 输出形如 @e42
  agent-browser snapshot -i -c 2>/dev/null | grep "button \"$1\"" | head -1 | sed -n 's/.*\[ref=\(e[0-9]*\)\].*/@\1/p'
}

# ── 演示场首页 ──
agent-browser open http://localhost:3000/ >/dev/null 2>&1
sleep 2.5
agent-browser screenshot "$OUT/01-arena-home.png" --full >/dev/null 2>&1
echo "✓ 01 演示场首页"

# ── 启动喜马拉雅 ──
R=$(get_ref "喜马拉雅"); echo "  喜马拉雅 ref=$R"
agent-browser click "$R" >/dev/null 2>&1
sleep 1.6
agent-browser screenshot "$OUT/02-ximalaya-ad.png" >/dev/null 2>&1
sleep 4
agent-browser screenshot "$OUT/03-ximalaya-after.png" >/dev/null 2>&1
echo "✓ 02/03 喜马拉雅启动流"

# ── 情报站 ──
agent-browser open http://localhost:3000/ >/dev/null 2>&1
sleep 2
R=$(get_ref "情报站"); echo "  情报站 ref=$R"
agent-browser click "$R" >/dev/null 2>&1
sleep 2.5
agent-browser screenshot "$OUT/04-intel-top.png" --full >/dev/null 2>&1
agent-browser press End >/dev/null 2>&1
sleep 1
agent-browser screenshot "$OUT/05-intel-bottom.png" >/dev/null 2>&1
echo "✓ 04/05 情报站"

# ── 关于 ──
agent-browser open http://localhost:3000/ >/dev/null 2>&1
sleep 2
R=$(get_ref "关于"); echo "  关于 ref=$R"
agent-browser click "$R" >/dev/null 2>&1
sleep 2.5
agent-browser screenshot "$OUT/06-about.png" --full >/dev/null 2>&1
echo "✓ 06 关于页"

ls -la "$OUT" | awk '{print $9, $5}'
