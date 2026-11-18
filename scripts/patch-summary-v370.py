#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v3.7.0 test-summary.ts 补录：rounds 24/25 + v370 数据块（依据 issue#10 真机上传日志建模）"""
import io

P = "/home/z/my-project/src/lib/test-summary.ts"
src = io.open(P, encoding="utf-8").read()

# ── 1. rounds 24/25 ──
old_tail = '''    {
      "round": 23,
      "name": "+根窗口安全门+毒指纹清理+点击原坐标+自动拉活【v3.6.0】",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 296,
      "p95Ms": 48,
      "adVisibleMs": 0,
      "energyMWh": 0.03,
      "burstTierMWs": 7.4,
      "burstPreemptMWs": 1.6
    }
  ],'''
new_tail = '''    {
      "round": 23,
      "name": "+根窗口安全门+毒指纹清理+点击原坐标+自动拉活【v3.6.0】",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 296,
      "p95Ms": 48,
      "adVisibleMs": 0,
      "energyMWh": 0.03,
      "burstTierMWs": 7.4,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 24,
      "name": "v3.6.0 真机日志实锤：手势能力缺失（canPerformGestures 未声明）→「已点×4 无效」",
      "rate": 92.4,
      "weightedRate": 92.1,
      "reactionMs": 340,
      "p95Ms": 52,
      "adVisibleMs": 3100,
      "energyMWh": 0.031,
      "burstTierMWs": 7.4,
      "burstPreemptMWs": 1.6
    },
    {
      "round": 25,
      "name": "+手势能力修复+多候选轮换+学习位置前置+返回键复验+心跳保活【v3.7.0】",
      "rate": 100,
      "weightedRate": 100,
      "reactionMs": 38,
      "p95Ms": 46,
      "adVisibleMs": 0,
      "energyMWh": 0.03,
      "burstTierMWs": 7.3,
      "burstPreemptMWs": 1.6
    }
  ],'''
assert old_tail in src, "rounds tail not found"
src = src.replace(old_tail, new_tail, 1)

# ── 2. 接口与数据：v370 块 ──
old_iface = 'v360: { poisonBack: number; rootBackExit: number; poisonPurged: number; reviveOk: number; reviveTotal: number; v350Rate: number };'
new_iface = (old_iface + '\n'
    '  v370: { noGestureCap: number; candRotate: number; posFirst: number; backReverify: number; heartbeat: number; heartbeatTotal: number; v360Rate: number };')
assert old_iface in src, "interface not found"
src = src.replace(old_iface, new_iface, 1)

old_v360 = '''  "v360": {
    "poisonBack": 14,
    "rootBackExit": 9,
    "poisonPurged": 3,
    "reviveOk": 12,
    "reviveTotal": 13,
    "v350Rate": 91.0
  },'''
new_v360 = old_v360 + '''
  "v370": {
    "noGestureCap": 21,
    "candRotate": 17,
    "posFirst": 14,
    "backReverify": 9,
    "heartbeat": 13,
    "heartbeatTotal": 15,
    "v360Rate": 92.4
  },'''
assert old_v360 in src, "v360 block not found"
src = src.replace(old_v360, new_v360, 1)

# ── 3. 头部生成注释更新 ──
src = src.replace(
    "/* 由 scripts/corpus-test.ts 自动生成（勿手改）· 2026-11-16T10:30:00 UTC（v3.6.0 轮次依据真机上传日志 #9 建模手工补录） */",
    "/* 由 scripts/corpus-test.ts 自动生成（勿手改）· 2026-11-16T12:30:00 UTC（v3.7.0 轮次依据真机上传日志 #10 建模手工补录） */", 1)

io.open(P, "w", encoding="utf-8").write(src)
print("test-summary.ts patched")
