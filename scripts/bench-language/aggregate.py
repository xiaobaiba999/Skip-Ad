#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""聚合四语言基准测试原始数据 → 中位数结果表 + 最终报告 JSON"""

import json
import statistics
from pathlib import Path

OUT = Path("/home/z/bench/results")

FRIENDLY = {
    "java": "Java 8 字节码 (ECJ · OpenJDK 21)",
    "kotlin": "Kotlin 2.0.21 (JVM 1.8 目标)",
    "js": "JavaScript (Node 24 · V8 = WebView 代理)",
    "python": "Python 3.12 (CPython)",
}

def robust_key(recs, key, mode="min"):
    """吞吐/延迟指标：min（噪声只增不减，最小值 = 真实下界）；规模指标：median"""
    vals = [r[key] for r in recs if key in r and r[key] is not None]
    if not vals:
        return None
    return min(vals) if mode == "min" else statistics.median(vals)

langs = ["java", "kotlin", "js", "python"]
raw = {}
for lang in langs:
    p = OUT / f"raw_{lang}.jsonl"
    recs = []
    for line in p.read_text().splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            recs.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    raw[lang] = recs

    # 校验和一致性（跨语言逐位等价证明）
    if recs:
        sinks = {r["sel_sink"] for r in recs}
        hits = {r["hits"] for r in recs}
        sinks_hk = {r["sink"] for r in recs}
        print(f"  {lang}: runs={len(recs)} sel_sink={sinks} hits={hits} sink={sinks_hk}")

# boot 中位数
boots = {}
for lang in langs:
    p = OUT / f"boot_{lang}.txt"
    vals = [int(x) for x in p.read_text().split() if x.strip()]
    boots[lang] = statistics.median(vals) if vals else None

result = {"meta": {
    "runs": 9,
    "aggregation": "throughput/latency=min-of-9 (噪声下界), boot/compile/rss=median",
    "jvm": "OpenJDK 21.0.13",
    "kotlin": "2.0.21 (K2)",
    "node": "24.22.3 (V8)",
    "python": "3.12.16 CPython",
    "tree_nodes": 48,
    "rules": {"app_rows": 28, "text_exact": 19, "gkd_selectors": 24},
    "protocol": "MATCH: cold1000 → warmup2000 → steady20000 ×(linear=引擎现状, optimized=哈希索引+预编译正则)",
}, "languages": {}}

for lang in langs:
    recs = raw[lang]
    if not recs:
        continue
    result["languages"][lang] = {
        "name": FRIENDLY[lang],
        "boot_ms": round(boots[lang] / 1000.0, 1) if boots.get(lang) else None,
        "compile_us_per_set": round(robust_key(recs, "compile_us", "median"), 1),
        "match_linear_first1000_ns": round(robust_key(recs, "match_linear_first1000_ns")),
        "match_linear_ns": round(robust_key(recs, "match_linear_ns"), 1),
        "match_optimized_ns": round(robust_key(recs, "match_optimized_ns"), 1),
        "rss_mb": round(robust_key(recs, "rss_kb", "median") / 1024.0, 1),
    }

(OUT / "bench_results.json").write_text(
    json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

print("\n=== 中位数汇总 ===")
hdr = f"{'lang':<8}{'boot(ms)':>10}{'compile(us/套)':>15}{'冷首1000(ns)':>14}{'稳态linear(ns)':>16}{'稳态opt(ns)':>14}{'RSS(MB)':>10}"
print(hdr)
for lang in langs:
    d = result["languages"].get(lang)
    if d:
        print(f"{lang:<8}{d['boot_ms']:>10}{d['compile_us_per_set']:>15}{d['match_linear_first1000_ns']:>14}"
              f"{d['match_linear_ns']:>16}{d['match_optimized_ns']:>14}{d['rss_mb']:>10}")
print("\n结果已写入", OUT / "bench_results.json")
