#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v7.9.8b 开屏跳过兼容性矩阵生成器（任务五——200+ 应用矩阵 · 用户十三字段版）。

从 compatibility/apps_catalog.json（233 款·三层来源包名目录）生成
splash_matrix_v798.json，字段覆盖用户指令十三项：
  应用名称(appName) / 包名(packageName) / 开屏广告(splashSupport) /
  信息流·插屏·视频广告(adScenes.*) / 已有规则(ruleExists) /
  规则版本(ruleRefs) / 测试状态(detection/match/action/final 四态) /
  失败类型(failureReason + failureCode) / 最近测试时间(lastTestedAt) /
  测试设备(testDevice) / 测试证据(testEvidence) / 最近测试版本(lastTestVersion)。

诚实口径（硬约束）：
 · 状态词汇表只允许 PASS / FAIL / NOT_TESTED / NOT_APPLICABLE / BLOCKED；
 · PASS = 真机实测成功——绝不把「仅有规则」「L1 仿真命中」标为 PASS；
 · 本轮无真机 → detection/match/action/final 全部 NOT_TESTED（如实）；
 · 无开屏广告场景（splashSupport=false）→ NOT_APPLICABLE；
 · lastTestedAt / testDevice 留真机回填前一律空串（绝不虚构设备与日期）；
 · 规则存在性（ruleExists）来自引擎内置订阅/Feed 名单/GKD 三源快照的
   数据级交叉（L1 可验证），只陈述「有没有规则」，绝不冒充「跳过成功」；
 · adScenes 来自目录标注（apps_catalog.json adTypes——L1 数据级），
   与「跳过是否成功」无关；
 · 规则版本（ruleRefs）= GKD 订阅快照的 version 字段实测值
   （ganlin=72 / linarm=637 / xnx=79，从 assets/subsnap/*.json5 头部提取）。

用法：python3 scripts/gen-splash-matrix.py
"""
import json
import os
import re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
CATALOG = os.path.join(ROOT, "compatibility", "apps_catalog.json")
OUT = os.path.join(ROOT, "compatibility", "splash_matrix_v798.json")
SUBSNAP = os.path.join(ROOT, "android-app", "assets", "subsnap")
ENGINE_VERSION = "7.9.8"

# 失败分类词表（compatibility/skip_failure_classification.md A–J + v7.9.8 SCAN_MISS 失败码）
FAILURE_CODE_VOCABULARY = {
    "A_NO_DETECTION": "开屏广告完全没有被识别（事件入口过滤/CLEAN 档案抑制——见分类文档 A）",
    "B_RULE_MISS": "识别到广告但规则未匹配（SCAN_MISS errClass=RULE_MISS_ZERO_CAND）",
    "C_ACTION_BLOCKED": "规则匹配但动作被安全闸拒收（SAFE_BLOCK——宁漏勿误红线）",
    "D_CLICK_NO_EFFECT": "动作执行但广告未消失（CLICK DISPATCHED + VERIFY NOT_CLOSED + AD_TASK MISS）",
    "E_FALSE_JUDGE": "广告已消失但误判（状态机终局证据——v7.9.1 已根治类）",
    "F_SPECIAL_WINDOW": "特殊窗口/控件（Dialog 插屏 SDK 独立包/图片角落键）",
    "G_SYSTEM_LIMIT": "系统限制（手势派发失败/无障碍服务被 ROM 杀）",
    "H_EVENT_TOO_FAST": "事件过快/加载延迟（晚出键复活/视频观察窗）",
    "I_RULE_STALE": "规则过时（订阅生态修复通道）",
    "J_PRIORITY_CONFLICT": "规则优先级冲突（RuleOrderTest 回归面）",
    "NO_RULE": "无任何规则/订阅覆盖（SCAN_MISS errClass=NO_RULE_ZERO_CAND）",
    "NO_CANDIDATE": "扫描成功但零候选节点",
    "WINDOW_UNAVAILABLE": "窗口不可用",
    "SCAN_THROTTLED": "扫描被节流/过滤",
    "VIDEO_AD": "视频开屏（SCAN_MISS errClass=VIDEO_LIKE_ZERO_CAND——观察窗+轮询针路径）",
    "AD_TYPE_UNSUPPORTED": "广告类型不支持",
    "UNKNOWN": "其他未知问题（PENDING_REAL_DEVICE——无证据不归因）",
}

def gkd_snapshot_versions():
    """从 assets/subsnap/*.json5 头部提取订阅快照 version（规则版本口径）。"""
    versions = {}
    try:
        for name in os.listdir(SUBSNAP):
            if not name.endswith(".json5"):
                continue
            path = os.path.join(SUBSNAP, name)
            with open(path, encoding="utf-8") as f:
                head = f.read(2048)
            m = re.search(r"name:'([^']+)'", head)
            v = re.search(r"version:(\d+)", head)
            key = name[:-6]  # 去 .json5
            versions[key] = {
                "name": m.group(1) if m else key,
                "version": int(v.group(1)) if v else 0,
            }
    except (OSError, ValueError):
        pass
    return versions

def main():
    with open(CATALOG, encoding="utf-8") as f:
        catalog = json.load(f)
    apps = catalog["apps"]
    snapshots = gkd_snapshot_versions()
    rows = []
    stats = {"total": 0, "splashApplicable": 0, "ruleExists": 0,
             "notTested": 0, "notApplicable": 0, "passCount": 0,
             "failCount": 0, "blockedCount": 0}
    for a in apps:
        pkg = a["packageName"]
        ad_types = a.get("adTypes") or []
        splash = "splash" in ad_types
        cov = a.get("subscriptionCoverage") or {}
        rule_exists = bool(cov.get("builtin") or cov.get("feed") or cov.get("gkd"))
        # 检测/匹配/动作/最终四态：本轮无真机——一律 NOT_TESTED（绝不虚标）
        det = "NOT_TESTED"
        match = "NOT_TESTED"
        action = "NOT_TESTED"
        final = "NOT_TESTED"
        reason = ""
        failure_code = ""
        last_tested_at = ""   # 真机回填前空串（绝不虚构日期）
        test_device = ""      # 真机回填前空串（绝不虚构设备）
        if not splash:
            # 目录标注无开屏广告场景 → 该应用无开屏可跳（NOT_APPLICABLE）
            det = match = action = final = "NOT_APPLICABLE"
            reason = "catalog-no-splash-scene"
        evidence = "l1-rule-existence" if rule_exists else "none"
        gkd_sources = cov.get("gkdSources") or []
        rows.append({
            "packageName": pkg,
            "appName": a["appName"],
            "category": a.get("category", ""),
            "splashSupport": splash,
            # 广告场景维度（目录数据级标注——与跳过成败无关）
            "adScenes": {
                "splash": splash,
                "interstitial": "interstitial" in ad_types,
                "feed": "feed" in ad_types,
                "video": ("video-splash" in ad_types) or ("rewarded-video" in ad_types),
            },
            "ruleExists": rule_exists,
            "ruleSources": {
                "builtinSubscription": bool(cov.get("builtin")),
                "feedList": bool(cov.get("feed")),
                "gkdSnapshot": bool(cov.get("gkd")),
            },
            # 规则版本口径：GKD 订阅快照 version 实测值（内置规则随引擎版本走）
            "ruleRefs": {
                "gkdSnapshotVersions": {s: snapshots[s]["version"]
                                        for s in gkd_sources if s in snapshots},
                "engineRuleVersion": ENGINE_VERSION,
            },
            "detectionStatus": det,
            "matchStatus": match,
            "actionStatus": action,
            "finalStatus": final,
            "failureReason": reason,
            "failureCode": failure_code,
            "testEvidence": evidence,
            "lastTestVersion": a.get("lastTestVersion", ""),
            "lastTestedAt": last_tested_at,
            "testDevice": test_device,
            "statusVocabulary": ["PASS", "FAIL", "NOT_TESTED", "NOT_APPLICABLE", "BLOCKED"],
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
                "BLOCKED": "存在明确测试阻塞（如设备不可用/系统限制——需注明阻塞原因）",
            },
            "failureCodeVocabulary": FAILURE_CODE_VOCABULARY,
            "fieldProvenance": {
                "adScenes": "apps_catalog.json adTypes（L1 数据级目录标注——与跳过成败无关）",
                "ruleRefs.gkdSnapshotVersions": "assets/subsnap/*.json5 头部 version 字段实测提取",
                "lastTestedAt/testDevice": "真机回填前一律空串（绝不虚构设备与日期）",
            },
            "honestNotes": [
                "ruleExists 仅陈述规则数据级存在（内置订阅/Feed 名单/GKD 快照交叉），不代表跳过成功",
                "真机 E2E 本轮 NOT_RUN（沙箱无设备）——detection/match/action/final 四态零虚标",
                "v7.9.8 新增 SCAN_MISS 结构化事件后，真机回填将携带失败码证据（NO_RULE_ZERO_CAND/RULE_MISS_ZERO_CAND/SILENT_TREE_ZERO_CAND/VIDEO_LIKE_ZERO_CAND）",
                "失败归类请用 failureCode（词表见 failureCodeVocabulary——A–J 十类 + SCAN_MISS 失败码联动），无证据不归因（UNKNOWN/PENDING_REAL_DEVICE）",
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
    print(f"  GKD 快照版本: " + ", ".join(f"{k}=v{v['version']}" for k, v in snapshots.items()))
    assert stats["total"] >= 200, "矩阵必须覆盖 200+ 应用"
    assert stats["passCount"] == 0 and stats["failCount"] == 0, "无真机证据时禁止 PASS/FAIL"
    for r in rows:
        assert r["lastTestedAt"] == "" and r["testDevice"] == "", "真机回填前禁止虚构测试时间/设备"
        assert r["finalStatus"] in r["statusVocabulary"], "状态必须取自词汇表"

if __name__ == "__main__":
    main()
