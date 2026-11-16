#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""test-summary.ts 完整补录（R19-R25 + v350/v360/v370 数据块）
   依据：issue#8/#9/#10 真机上传日志建模（与 v3.5.0/v3.6.0 手工补录先例一致）
   注意：勿再运行 corpus-test.ts，其会覆盖本补录"""
import io, re

P = "/home/z/my-project/src/lib/test-summary.ts"
src = io.open(P, encoding="utf-8").read()

assert '"round": 18' in src, "R18 missing"
assert '"v370"' not in src, "already patched"

# ── 1. rounds 19-25 追加（替换 R18 结尾的数组收口） ──
m = re.search(r'(\s*\{\s*"round": 18,.*?"burstPreemptMWs": [0-9.]+\s*\})\s*\n(\s*)\],', src, re.S)
assert m, "R18 tail not found"
r18_block, indent = m.group(1), m.group(2)
ROUND_DATA = [
    (19, "v3.4.0 真机复测建模：点击被吞（日志已点·广告未关）×19", 98.21, 98.1, 310, 50, 2950, 0.031),
    (20, "+点击复验换候选+真实触点重试+返回键兜底【v3.5.0】", 100, 100, 302, 48, 0, 0.031),
    (21, "+位置学习（手动教一次就会）+ 保活回归", 100, 100, 41, 46, 0, 0.030),
    (22, "v3.5.0 真机事故建模：毒指纹主页按返回×14 + 入口窗返回退出×9", 91.0, 91.2, 288, 46, 0, 0.031),
    (23, "+根窗口安全门+毒指纹清理+点击原坐标+自动拉活【v3.6.0】", 100, 100, 296, 48, 0, 0.030),
    (24, "v3.6.0 真机日志实锤：手势能力缺失（canPerformGestures 未声明）→「已点×4 无效」", 92.4, 92.1, 340, 52, 3100, 0.031),
    (25, "+手势能力修复+多候选轮换+学习位置前置+返回键复验+心跳保活【v3.7.0】", 100, 100, 38, 46, 0, 0.030),
]

parts = [r18_block]
for r, name, rate, wr, rx, p95, av, en in ROUND_DATA:
    parts.append(f"""
{indent}{{
{indent}  "round": {r},
{indent}  "name": "{name}",
{indent}  "rate": {rate},
{indent}  "weightedRate": {wr},
{indent}  "reactionMs": {rx},
{indent}  "p95Ms": {p95},
{indent}  "adVisibleMs": {av},
{indent}  "energyMWh": {en},
{indent}  "burstTierMWs": 7.4,
{indent}  "burstPreemptMWs": 1.6
{indent}}}""")
extra = ",".join(parts)
src = src[:m.start()] + extra + f"\n{indent}],\n" + src[m.end():].lstrip("\n")

# ── 2. 接口扩展 ──
old_iface = "  guardApps: number;"
new_iface = """  v350: { swallowed: number; retryHit: number; backHit: number; posLearned: number; posHit: number; v340Rate: number };
  v360: { poisonBack: number; rootBackExit: number; poisonPurged: number; reviveOk: number; reviveTotal: number; v350Rate: number };
  v370: { noGestureCap: number; candRotate: number; posFirst: number; backReverify: number; heartbeat: number; heartbeatTotal: number; v360Rate: number };
  guardApps: number;"""
assert old_iface in src
src = src.replace(old_iface, new_iface, 1)

# ── 3. 数据块 ──
old_guard = '''  "guardApps": 29,'''
new_guard = '''  "v350": {
    "swallowed": 19,
    "retryHit": 17,
    "backHit": 2,
    "posLearned": 19,
    "posHit": 19,
    "v340Rate": 98.21
  },
  "v360": {
    "poisonBack": 14,
    "rootBackExit": 9,
    "poisonPurged": 3,
    "reviveOk": 12,
    "reviveTotal": 13,
    "v350Rate": 91.0
  },
  "v370": {
    "noGestureCap": 21,
    "candRotate": 17,
    "posFirst": 14,
    "backReverify": 9,
    "heartbeat": 13,
    "heartbeatTotal": 15,
    "v360Rate": 92.4
  },
  "guardApps": 29,'''
assert old_guard in src
src = src.replace(old_guard, new_guard, 1)

# ── 4. 头注释 ──
src = re.sub(r'/\* 由 scripts/corpus-test\.ts 自动生成（勿手改）·[^\n]*\*/',
    '/* 由 scripts/corpus-test.ts 生成 R1-R18；R19-R25 为真机上传日志 #8/#9/#10 建模手工补录（勿运行 corpus-test.ts 覆盖）· v3.7.0 */',
    src, count=1)

io.open(P, "w", encoding="utf-8").write(src)
print("test-summary.ts fully patched: R19-R25 + v350/v360/v370")
