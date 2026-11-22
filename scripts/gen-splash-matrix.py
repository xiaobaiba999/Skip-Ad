#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v7.9.8 开屏跳过兼容性矩阵生成器（任务五——200+ 应用矩阵）。

从 compatibility/apps_catalog.json（233 款·三层来源包名目录）生成
splash_matrix_v798.json：按用户指令字段组织（packageName/appName/
splashSupport/ruleExists/detectionStatus/matchStatus/actionStatus/
finalStatus/failureReason/testEvidence/lastTestVersion）。

诚实口径（硬约束）：
 · 状态词汇表只允许 PASS / FAIL / NOT_TESTED / NOT_APPLICABLE；
 · PASS = 真机实测成功——绝不把「仅有规则」「L1 仿真命中」标为 PASS；
 · 本轮无真机 → detection/match/action/final 全部 NOT_TESTED（如实）；
 · 无开屏广告场景（splashSupport=false）→ NOT_APPLICABLE；
 · 规则存在性（ruleExists）来自引擎内置订阅/Feed 名单/GKD 三源快照的
   数据级交叉（L1 可验证），只陈述「有没有规则」，绝不冒充「跳过成功」。

用法：python3 scripts/gen-splash-matrix.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
CATALOG = os.path.join(ROOT, "compatibility", "apps_catalog.json")
OUT = os.path.join(ROOT, "compatibility", "splash_matrix_v798.json")
ENGINE_VERSION = "7.9.8"

def main():
    with open(CATALOG, encoding="utf-8") as f:
        catalog = json.load(f)
    apps = catalog["apps"]
    rows = []
    stats = {"total": 0, "splashApplicable": 0, "ruleExists": 0,
             "notTested": 0, "notApplicable": 0, "passCount": 0, "failCount": 0}
    for a in apps:
        pkg = a["packageName"]
        splash = "splash" in (a.get("adTypes") or [])
        cov = a.get("subscriptionCoverage") or {}
        rule_exists = bool(cov.get("builtin") or cov.get("feed") or cov.get("gkd"))
        # 检测/匹配/动作/最终四态：本轮无真机——一律 NOT_TESTED（绝不虚标）
        det = "NOT_TESTED"
        match = "NOT_TESTED"
        action = "NOT_TESTED"
        final = "NOT_TESTED"
        reason = ""
        if not splash:
            # 目录标注无开屏广告场景 → 该应用无开屏可跳（NOT_APPLICABLE）
            det = match = action = final = "NOT_APPLICABLE"
            reason = "catalog-no-splash-scene"
        evidence = "l1-rule-existence" if rule_exists else "none"
        rows.append({
            "packageName": pkg,
            "appName": a["appName"],
            "category": a.get("category", ""),
            "splashSupport": splash,
            "ruleExists": rule_exists,
            "ruleSources": {
                "builtinSubscription": bool(cov.get("builtin")),
                "feedList": bool(cov.get("feed")),
                "gkdSnapshot": bool(cov.get("gkd")),
            },
            "detectionStatus": det,
            "matchStatus": match,
            "actionStatus": action,
            "finalStatus": final,
            "failureReason": reason,
            "testEvidence": evidence,
            "lastTestVersion": a.get("lastTestVersion", ""),
            "statusVocabulary": ["PASS", "FAIL", "NOT_TESTED", "NOT_APPLICABLE"],
        })
        stats["total"] += 1
        if splash:
            stats["splashApplicable"] += 1
        else:
            stats["notApplicable"] += 1
        if rule_exists:
            stats["ruleExists"] += 1
        if final == "NOT_TESTED":
            stats["notTested"] += 1

    out = {
        "meta": {
            "version": ENGINE_VERSION,
            "generatedAt": "2026-11-23T12:00:00+08:00",
            "purpose": "开屏广告跳过兼容性矩阵（任务五）：字段按用户指令组织，状态如实——真机实测前一律 NOT_TESTED，绝不把仅有规则的应用标为 PASS",
            "statusSemantics": {
                "PASS": "真机实测跳过成功（需真机证据回填）",
                "FAIL": "真机实测跳过失败（需真机证据回填）",
                "NOT_TESTED": "尚未真机实测（本轮全部 detection/match/action/final 如实标注）",
                "NOT_APPLICABLE": "该应用目录标注无开屏广告场景",
            },
            "honestNotes": [
                "ruleExists 仅陈述规则数据级存在（内置订阅/Feed 名单/GKD 快照交叉），不代表跳过成功",
                "真机 E2E 本轮 NOT_RUN（沙箱无设备）——detection/match/action/final 四态零虚标",
                "v7.9.8 新增 SCAN_MISS 结构化事件后，真机回填将携带失败码证据（NO_RULE_ZERO_CAND/RULE_MISS_ZERO_CAND/SILENT_TREE_ZERO_CAND/VIDEO_LIKE_ZERO_CAND）",
            ],
            "stats": stats,
        },
        "apps": rows,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"✓ 生成 {OUT}")
    print(f"  总应用 {stats['total']} · 开屏场景适用 {stats['splashApplicable']} · "
          f"NOT_APPLICABLE {stats['notApplicable']} · 有规则 {stats['ruleExists']} · "
          f"NOT_TESTED {stats['notTested']} · PASS {stats['passCount']} · FAIL {stats['failCount']}")
    assert stats["total"] >= 200, "矩阵必须覆盖 200+ 应用"
    assert stats["passCount"] == 0 and stats["failCount"] == 0, "无真机证据时禁止 PASS/FAIL"

if __name__ == "__main__":
    main()
