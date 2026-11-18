#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Skip-Ad 引擎热路径基准测试 —— Python 实现（CPython 3.12）
算法/数据/时序与 Java 参考版逐位一致（xorshift31 PRNG 同一棵树）。
用法： python3 Bench.py boot | python3 Bench.py bench
"""

import json
import re
import sys
import time

APP_TEXT_RULES = [
    ["com.sina.weibo", "跳过广告", "跳过"],
    ["com.ximalaya.ting.android", "跳过广告", "跳过"],
    ["tv.danmaku.bili", "跳过广告", "跳过"],
    ["com.qiyi.video", "跳过广告", "跳过"],
    ["com.youku.phone", "跳过广告", "跳过"],
    ["com.tencent.qqlive", "跳过广告", "跳过"],
    ["com.hunantv.imgo", "跳过广告", "跳过"],
    ["com.gotokeep.keep", "跳过"],
    ["com.xingin.xhs", "跳过"],
    ["com.zhihu.android", "跳过广告"],
    ["com.sankuai.meituan", "跳过广告"],
    ["com.dianping.v1", "跳过广告"],
    ["com.jingdong.app.mall", "跳过"],
    ["com.taobao.taobao", "跳过"],
    ["com.xunmeng.pinduoduo", "跳过"],
    ["com.dragon.read", "跳过广告"],
    ["com.baidu.searchbox", "跳过"],
    ["com.netease.cloudmusic", "跳过"],
    ["com.tencent.qqmusic", "跳过广告"],
    ["com.kugou.android", "跳过"],
    ["com.UCMobile", "跳过"],
    ["com.tencent.mtt", "跳过广告"],
    ["com.douban.frodo", "跳过广告"],
    ["cn.wps.moffice_eng", "跳过广告"],
    ["ctrip.android.view", "跳过"],
    ["com.taobao.trip", "跳过"],
    ["com.autonavi.minimap", "跳过"],
    ["com.sdu.didi.psnger", "跳过"],
]

TEXT_EXACT = [
    "跳过", "跳过广告", "跳过此广告", "跳过这个广告", "关闭广告", "关闭此广告",
    "点击跳过", "上滑跳过", "跳过提示", "立即跳过",
    "跳过 ad", "跳过ad",
    "skip", "skip ad", "skipad", "skip this ad",
    "跳過", "跳過廣告", "跳過此廣告",
]

CONTAINS_SHORT = ["跳过", "skip", "跳過"]
DANGEROUS = ["领", "红包", "下载", "查看详情", "立即", "参与", "抽奖", "领取"]
VID_SKIP_KEYS = ["skip", "tt_splash_skip_btn", "iv_skip", "count_down", "splash"]

# 字符串形式（linear 模式 = 引擎现状：re.match 每次查缓存/重编译）
P_COUNTDOWN_TAIL_STR = r"^(跳过|跳过广告|跳過廣告)\s*[0-9]+(s|秒)?$"
P_COUNTDOWN_HEAD_STR = r"^[0-9]+\s*(s|秒)?(后|後)可?(跳过|跳过广告|跳過廣告)$"
# 预编译形式（optimized 模式 = v5.6.0 优化）
pat_tail = None
pat_head = None

GKD_SELECTORS = [
    '[text*="跳过"]', '[text*="跳过广告"]', '[vid*="skip"]',
    '[vid="tt_splash_skip_btn"]', '[text="跳过广告 3"]',
    '[text*="跳过" && vid*="count_down"]',
    '[vid*="skip" >n2 [text*="广告"]',
    '[text*="跳過廣告" || text*="skip"]',
    'ImageView <n [vid*="splash"]',
    '[text*="关闭广告" && clickable=true]',
    '[vid*="iv_skip"] >n2 TextView',
    '[text*="跳过"] -n [text*="广告"]',
    '[text*="skip" && clickable=true] >n2 *',
    '[vid*="close" && (text*="广告" || vid*="ad")]',
    '[text*="跳过"][9]', '[text*="跳过"][9,10]',
    '* >n [vid*="splash_ad"]',
    '[text*="跳过广告" >n2 [text*="查看详情"]',
    '[vid*="count_down"] <n3 ImageView',
    '[text*="上滑"] || [vid*="slide_skip"]',
    '[text*="跳过" && visibleToUser=true] >n *',
    '[vid*="ad_close" && text=null]',
    '[text*="skip this ad"] >n2 [clickable=true]',
    '[text*="跳過"][3,8]',
]

TEXT_POOL = [
    "跳过广告 5", "跳过广告 3", "跳过", "立即领取红包", "限时秒杀·低至1折",
    "精选推荐", "新人专享礼包", "点击查看详情", "上滑解锁更多内容", "领取优惠券",
    "skip ad 5", "Skip", "广告", "立即下载App", "今日热点推荐", "签到领金币",
    None, None, None, None,
]
VID_POOL = [
    "tt_splash_skip_btn", "iv_skip", "count_down_text", "splash_ad_view",
    "close_btn", "ad_container", "banner_view", None, None, None,
]
CLS_POOL = [
    "android.widget.TextView", "android.widget.ImageView", "android.widget.Button",
    "android.view.ViewGroup", "android.webkit.WebView",
]

# ── xorshift31（与 Java/Kotlin/JS 逐位一致）──
_rng_state = 0


def rnd():
    global _rng_state
    x = _rng_state
    x = (x ^ ((x << 13) & 0xFFFFFFFF)) & 0xFFFFFFFF
    x = (x ^ (x >> 17)) & 0xFFFFFFFF
    x = (x ^ ((x << 5) & 0xFFFFFFFF)) & 0xFFFFFFFF
    _rng_state = x
    return x & 0x7FFFFFFF


def rnd_range(mod):
    return rnd() % mod


class Node:
    __slots__ = ("text", "vid", "cls", "l", "t", "r", "b", "clickable", "parent", "children")

    def __init__(self):
        self.text = None
        self.vid = None
        self.cls = ""
        self.l = 0; self.t = 0; self.r = 0; self.b = 0
        self.clickable = False
        self.parent = None
        self.children = []


def link(p, c):
    c.parent = p
    p.children.append(c)


def build_tree(seed):
    global _rng_state
    _rng_state = seed & 0xFFFFFFFF
    root = Node()
    root.cls = "android.view.ViewGroup"
    root.l = 0; root.t = 0; root.r = 1080; root.b = 2400
    for g in range(6):
        grp = Node()
        grp.cls = CLS_POOL[rnd_range(len(CLS_POOL))]
        grp.l = rnd_range(1000); grp.t = rnd_range(2200)
        grp.r = grp.l + 40 + rnd_range(900)
        grp.b = grp.t + 40 + rnd_range(160)
        grp.clickable = rnd_range(2) == 0
        link(root, grp)
        n = 5 + rnd_range(5)
        for i in range(n):
            c = Node()
            text = TEXT_POOL[rnd_range(len(TEXT_POOL))]
            if text is not None:
                if rnd_range(2) == 0 and len(text) >= 4:
                    text = text + " " + str(1 + rnd_range(9))
                c.text = text
            vid = VID_POOL[rnd_range(len(VID_POOL))]
            if vid is not None:
                c.vid = vid + "_" + str(rnd_range(200))
            c.cls = CLS_POOL[rnd_range(len(CLS_POOL))]
            c.l = grp.l + rnd_range(max(1, grp.r - grp.l - 80))
            c.t = grp.t + rnd_range(max(1, grp.b - grp.t - 30))
            w = 30 + rnd_range(700)
            h = 20 + rnd_range(80)
            c.r = c.l + w; c.b = c.t + h
            c.clickable = rnd_range(4) == 0
            link(grp, c)
    return root


pkg = "com.sankuai.meituan"
app_rule_index = {}
exact_set = set()

hits = 0
sink = 0


def match_tree(root, optimized):
    stack = [root]
    while stack:
        n = stack.pop()
        if n.text is not None:
            low = n.text.strip().lower()
            hit = match_text(n, low, optimized)
            if hit is not None:
                return hit
        if n.vid is not None:
            v = n.vid.lower()
            for k in VID_SKIP_KEYS:
                if k in v:
                    if shape_gate(n, True):
                        return "vid:" + n.vid
        stack.extend(reversed(n.children))
    return None


def match_text(n, low, optimized):
    if optimized:
        row = app_rule_index.get(pkg)
        if row is not None:
            for i in range(1, len(row)):
                if low == row[i]:
                    return "app:" + row[i]
    else:
        for row in APP_TEXT_RULES:
            if pkg.startswith(row[0]):
                for i in range(1, len(row)):
                    if low == row[i]:
                        return "app:" + row[i]
    if optimized:
        if low in exact_set:
            if shape_gate(n, False):
                return "exact:" + low
    else:
        for k in TEXT_EXACT:
            if low == k:
                if shape_gate(n, False):
                    return "exact:" + low
    if optimized:
        if pat_tail.match(low):
            if shape_gate(n, False):
                return "countdown:" + low
        if pat_head.match(low):
            if shape_gate(n, False):
                return "countdown:" + low
    else:
        if re.match(P_COUNTDOWN_TAIL_STR, low):
            if shape_gate(n, False):
                return "countdown:" + low
        if re.match(P_COUNTDOWN_HEAD_STR, low):
            if shape_gate(n, False):
                return "countdown:" + low
    if len(low) <= 8:
        cont = False
        for k in CONTAINS_SHORT:
            if k in low:
                cont = True
                break
        if cont:
            for k in DANGEROUS:
                if k in low:
                    return None
            if shape_gate(n, False):
                return "contains:" + low
    return None


def shape_gate(n, relax):
    w = n.r - n.l
    lim = 0.70 if relax else 0.55
    if w > lim * 1080:
        return False
    if n.parent is None:
        return True
    sib = n.parent.children
    if len(sib) >= 4:
        same = 0
        nw = n.r - n.l
        for s in sib:
            if abs((s.r - s.l) - nw) <= 2 and abs(s.l - n.l) <= 2:
                same += 1
        if same >= 4:
            return False
    return True


def build_opt_index():
    global app_rule_index, exact_set, pat_tail, pat_head
    app_rule_index = {row[0]: row for row in APP_TEXT_RULES}
    exact_set = set(TEXT_EXACT)
    pat_tail = re.compile(P_COUNTDOWN_TAIL_STR)
    pat_head = re.compile(P_COUNTDOWN_HEAD_STR)


# ── 选择器编译（同 Java 版语义）──
def compile_one(src):
    sel = {"raw": src, "conds": [], "rel": "", "relN": 1, "posList": []}
    i = 0
    while i < len(src):
        ch = src[i]
        if ch == '[':
            close = src.find(']', i)
            if close < 0:
                break
            inner = src[i + 1:close]
            for part in split_logical(inner):
                sel["conds"].append(split_cond(part.strip()))
            i = close + 1
        elif ch in ('>', '<', '-'):
            j = i + 1
            while j < len(src) and src[j].isdigit():
                j += 1
            sel["rel"] = ch
            sel["relN"] = int(src[i + 1:j]) if j > i + 1 else 1
            i = j
        elif ch == ' ':
            i += 1
        else:
            j = i
            while (j < len(src) and src[j] != ' ' and src[j] != '['
                   and src[j] != '>' and src[j] != '<' and src[j] != '-'):
                j += 1
            if j > i:
                sel["posList"].append(0)
            i = j
    return sel


def split_logical(s):
    parts = []
    cur = []
    depth = 0
    i = 0
    while i < len(s):
        c = s[i]
        if c == '(':
            depth += 1
        if c == ')':
            depth -= 1
        if depth == 0 and c == '&' and i + 1 < len(s) and s[i + 1] == '&':
            parts.append(''.join(cur)); cur = []; i += 2; continue
        if depth == 0 and c == '|' and i + 1 < len(s) and s[i + 1] == '|':
            parts.append(''.join(cur)); cur = []; i += 2; continue
        cur.append(c); i += 1
    parts.append(''.join(cur))
    return parts


def split_cond(s):
    eq = s.find('=')
    if eq < 0:
        return (s, "exists", "")
    attr = s[:eq].strip()
    rest = s[eq:]
    if rest.startswith("="):
        op = "=="
    elif rest.startswith("*"):
        op = "*="
    elif rest.startswith("!"):
        op = "!="
    else:
        op = "=="
    q1 = s.find('"')
    q2 = s.rfind('"')
    value = s[q1 + 1:q2] if (q1 >= 0 and q2 > q1) else ""
    return (attr, op, value)


def py_hash(s):
    """对齐 Java String.hashCode（31 进制，带符号截断）"""
    h = 0
    for ch in s:
        h = (h * 31 + ord(ch)) & 0xFFFFFFFF
    if h >= 0x80000000:
        h -= 0x100000000
    return h


def rss_kb():
    try:
        with open("/proc/self/status", "r", encoding="ascii") as f:
            for line in f:
                if line.startswith("VmRSS:"):
                    return int(''.join(c for c in line if c.isdigit()))
    except Exception:
        pass
    return -1


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "bench"

    t0 = time.perf_counter_ns()
    sels = [compile_one(s) for s in GKD_SELECTORS]           # 预热 1 轮
    for _ in range(5):
        sels = [compile_one(s) for s in GKD_SELECTORS]     # 预热 5 轮
    m0c = time.perf_counter_ns()
    for _ in range(50):
        sels = [compile_one(s) for s in GKD_SELECTORS]     # 测量 50 轮
    m1c = time.perf_counter_ns()
    sels = [compile_one(s) for s in GKD_SELECTORS]
    t1 = time.perf_counter_ns()
    sel_sink = sum(len(s["raw"]) + len(s["conds"]) + s["relN"] + len(s["posList"]) for s in sels)
    compile_us = (m1c - m0c) // 50 // 1000

    if mode == "boot":
        print("READY")
        return

    global hits, sink
    tree = build_tree(20261118)
    COLD, WARMUP, ITERS = 1000, 2000, 20000

    c0 = time.perf_counter_ns()
    for _ in range(COLD):
        h = match_tree(tree, False)
        if h is not None:
            sink += len(h)
    c1 = time.perf_counter_ns()
    first_ns = (c1 - c0) // COLD

    for _ in range(WARMUP):
        h = match_tree(tree, False)
        if h is not None:
            sink += len(h)
    hits = 0
    s0 = time.perf_counter_ns()
    for _ in range(ITERS):
        h = match_tree(tree, False)
        if h is not None:
            hits += 1
            sink += py_hash(h) + len(h)
    s1 = time.perf_counter_ns()

    build_opt_index()
    for _ in range(WARMUP):
        h = match_tree(tree, True)
        if h is not None:
            sink += len(h)
    hits = 0
    o0 = time.perf_counter_ns()
    for _ in range(ITERS):
        h = match_tree(tree, True)
        if h is not None:
            hits += 1
            sink += py_hash(h) + len(h)
    o1 = time.perf_counter_ns()

    print(json.dumps({
        "lang": "python",
        "compile_us": compile_us,
        "sel_sink": sel_sink,
        "match_linear_first1000_ns": first_ns,
        "match_linear_ns": round((s1 - s0) / ITERS, 1),
        "match_optimized_ns": round((o1 - o0) / ITERS, 1),
        "hits": hits,
        "sink": sink % 1000003,
        "rss_kb": rss_kb(),
    }))


if __name__ == "__main__":
    main()
