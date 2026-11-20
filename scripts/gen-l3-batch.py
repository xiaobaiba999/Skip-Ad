#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""L3 真机测试批次生成器（v7.9.5 · 用户指令四/五）。

从 compatibility/apps_catalog.json 选出第一批 50 款真机测试 App：
  · 12 类配额（社交/短视频/电商/视频/音乐/新闻/阅读/工具/浏览器/游戏/
    学习/生活服务——不能简单按列表前 50）；
  · 特殊广告形态优先（video-splash 全收 6 款 · banner 2 · rewarded 2 ·
    feedback-sheet 2——视频开屏/插屏/信息流/悬浮全覆盖）；
  · 引擎覆盖交叉（内置订阅/GKD 三源/Feed 名单/零覆盖——四档都进场）；
  · 优先级混合（P0/P1/P2 都在场——高危高频优先，长尾不缺席）。

诚实口径（不可违反）：
  · 本文件只定义「要测什么」——每个 App 的测试字段全部 PENDING_REAL_DEVICE；
  · REAL_DEVICE_TEST = NOT_AVAILABLE（本机无真机）；
  · finalResult 词表严格使用：VERIFIED_SUCCESS / MISS / FALSE_CLICK /
    DUPLICATE_CLICK / SAFE_BLOCK / NO_AD_OBSERVED / NOT_INSTALLED / CRASH /
    UNKNOWN——绝不 NO_AD_OBSERVED=SUCCESS、RULE_ONLY=VERIFIED_SUCCESS。

用法：python3 scripts/gen-l3-batch.py（幂等——目录变化后重跑刷新批次）。
"""
import json
import datetime

ROOT = "/home/z/skip-ad-repo"
CATALOG = f"{ROOT}/compatibility/apps_catalog.json"
OUT = f"{ROOT}/compatibility/l3_batch1.json"

# 12 类配额（合计 50——按生态权重倾斜：社交/短视频/电商/视频/工具/金融生活重配）
CATEGORY_QUOTA = {
    "social-im": 5, "video": 5, "news-feed": 4, "ecommerce": 5,
    "music-audio": 3, "browser-search": 4, "tools-system": 6,
    "reading": 4, "games": 4, "education": 3, "finance-life": 5, "misc": 2,
}
# 特殊广告形态强制收录（形态覆盖优先于类别配额——收进配额内计数）
FORCE_AD_TYPES = {
    "video-splash": 6,   # 全收（用户指令八：视频开屏重点）
    "banner": 2,         # 悬浮横幅
    "rewarded-video": 2, # 激励视频（关闭语义特殊）
    "feedback-sheet": 2, # 反馈面板闭环
}
PRIORITY_RANK = {"P0": 0, "P1": 1, "P2": 2}


def main():
    with open(CATALOG, encoding="utf-8") as fp:
        catalog = json.load(fp)
    apps = catalog["apps"]
    by_pkg = {a["packageName"]: a for a in apps}

    selected = {}  # pkg -> app（去重集合）

    # ① 特殊广告形态强制收录（每形态按优先级+名称稳定排序取前 N）
    for adtype, want in FORCE_AD_TYPES.items():
        pool = sorted(
            [a for a in apps if adtype in a.get("adTypes", [])],
            key=lambda a: (PRIORITY_RANK.get(a.get("priority", "P2"), 2), a["appName"]),
        )
        for a in pool[:want]:
            selected[a["packageName"]] = a

    # ② 类别配额填充（强制收录占位后补足；溢出类别不倒扣——形态优先。
    #     总数锚定 50：全局填充上限 = 50 - 强制收录数；配额类别按序填充，
    #     后序类别让位于先序（总数绝不溢出）。）
    total = sum(CATEGORY_QUOTA.values())
    fill_budget = total - len(selected)
    for cat, quota in CATEGORY_QUOTA.items():
        if fill_budget <= 0:
            break
        cur = [a for a in selected.values() if a["category"] == cat]
        remaining = min(quota - len(cur), fill_budget)
        if remaining <= 0:
            continue
        pool = sorted(
            [a for a in apps if a["category"] == cat and a["packageName"] not in selected],
            key=lambda a: (PRIORITY_RANK.get(a.get("priority", "P2"), 2), a["appName"]),
        )
        for a in pool[:remaining]:
            selected[a["packageName"]] = a
            fill_budget -= 1

    # ③ 补位至 50：先补「零引擎覆盖」长尾（诚实测试引擎盲区），再按优先级
    total = sum(CATEGORY_QUOTA.values())
    if len(selected) < total:
        no_cover = sorted(
            [a for a in apps if a["packageName"] not in selected
             and not a.get("subscriptionCoverage", {}).get("gkd", False)
             and not a.get("subscriptionCoverage", {}).get("builtin", False)
             and not a.get("subscriptionCoverage", {}).get("feed", False)],
            key=lambda a: (PRIORITY_RANK.get(a.get("priority", "P2"), 2), a["appName"]),
        )
        for a in no_cover[: total - len(selected)]:
            selected[a["packageName"]] = a
    if len(selected) < total:
        pool = sorted(
            [a for a in apps if a["packageName"] not in selected],
            key=lambda a: (PRIORITY_RANK.get(a.get("priority", "P2"), 2), a["appName"]),
        )
        for a in pool[: total - len(selected)]:
            selected[a["packageName"]] = a
    assert len(selected) == total, f"批次总数 {len(selected)} != {total}"

    # 统计覆盖面
    cat_cov = {}
    for a in selected.values():
        cat_cov[a["category"]] = cat_cov.get(a["category"], 0) + 1
    adtype_cov = {}
    for a in selected.values():
        for t in a.get("adTypes", []):
            adtype_cov[t] = adtype_cov.get(t, 0) + 1
    cov = a.get("subscriptionCoverage", {})

    def cov_count(key):
        return sum(1 for a in selected.values() if a.get("subscriptionCoverage", {}).get(key))

    engine_cov = {
        "gkdSnapshot": cov_count("gkd"),
        "builtinSub": cov_count("builtin"),
        "feedList": cov_count("feed"),
        "noEngineCoverage": sum(1 for a in selected.values()
                                if not a.get("subscriptionCoverage", {}).get("gkd")
                                and not a.get("subscriptionCoverage", {}).get("builtin")
                                and not a.get("subscriptionCoverage", {}).get("feed")),
    }
    pri_cov = {}
    for a in selected.values():
        pri_cov[a.get("priority", "?")] = pri_cov.get(a.get("priority", "?"), 0) + 1

    # 测试记录字段（全部 PENDING——真机执行时逐项回填）
    RECORD_FIELDS = [
        "app", "package", "appVersion", "device", "androidVersion", "date",
        "adType", "uiFramework", "observedAd", "candidate", "decision",
        "clicked", "verification", "finalResult",
    ]

    batch_apps = []
    for a in sorted(selected.values(), key=lambda x: (x["category"], x["appName"])):
        batch_apps.append({
            "appName": a["appName"],
            "packageName": a["packageName"],
            "category": a["category"],
            "priority": a.get("priority", "P2"),
            "adTypes": a.get("adTypes", []),
            "uiFramework": a.get("uiFramework", "unknown"),
            "subscriptionCoverage": a.get("subscriptionCoverage", {}),
            "catalogSource": a.get("source", ""),
            # ── 真机执行时逐项回填（本轮全部 PENDING——绝不预填结果）──
            "record": {f: (a["appName"] if f == "app" else a["packageName"] if f == "package"
                           else None) for f in RECORD_FIELDS},
            "status": "PENDING_REAL_DEVICE",
        })

    doc = {
        "meta": {
            "batchId": "l3-batch-1",
            "version": "7.9.5",
            "generatedAt": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
            "timezone": "Asia/Shanghai",
            "totalApps": len(batch_apps),
            "selectionRules": [
                "12 类配额（社交5/短视频5/电商5/视频5/工具6/金融生活5/新闻4/阅读4/浏览器4/游戏4/学习3/其他2——不按列表前50）",
                "特殊广告形态强制收录：video-splash 全收 6 款 · banner 2 · rewarded-video 2 · feedback-sheet 2",
                "优先级混合：P0/P1/P2 全在场（高危高频优先，长尾不缺席）",
                "引擎覆盖交叉：GKD 快照/内置订阅/Feed 名单/零覆盖四档全进场（零覆盖长尾优先补位——诚实测盲区）",
                "稳定排序：优先级 → 应用名（批次可复现）",
            ],
            "recordFields": RECORD_FIELDS,
            "finalResultVocabulary": [
                "VERIFIED_SUCCESS", "MISS", "FALSE_CLICK", "DUPLICATE_CLICK",
                "SAFE_BLOCK", "NO_AD_OBSERVED", "NOT_INSTALLED", "CRASH", "UNKNOWN",
            ],
            "honesty": [
                "NO_AD_OBSERVED 绝不等于 SUCCESS（无广告就是无广告——不算命中率分母的命中）",
                "RULE_ONLY（规则匹配成功）绝不等于 VERIFIED_SUCCESS（广告必须真消失）",
                "dispatchGesture 成功绝不等于广告关闭（必须复验）",
                "本文件只定义要测什么——执行结果一律真机回填，绝不预填",
            ],
        },
        "realDeviceTest": {
            "status": "NOT_AVAILABLE",
            "note": "本机无真实 Android 设备（REAL_DEVICE_TEST = NOT_AVAILABLE）——批次定义与记录字段已建齐，执行需真机 adb 环境；绝不虚报执行结果。",
        },
        "categoryCoverage": cat_cov,
        "adTypeCoverage": adtype_cov,
        "engineCoverage": engine_cov,
        "priorityCoverage": pri_cov,
        "apps": batch_apps,
    }

    with open(OUT, "w", encoding="utf-8") as fp:
        json.dump(doc, fp, ensure_ascii=False, indent=2)
        fp.write("\n")

    print(f"✅ L3 批次 1 生成：{len(batch_apps)} 款 → {OUT}")
    print(f"   类别覆盖（12/12）：{cat_cov}")
    print(f"   广告形态覆盖：{adtype_cov}")
    print(f"   引擎覆盖交叉：{engine_cov}")
    print(f"   优先级：{pri_cov}")
    missing = [c for c in CATEGORY_QUOTA if c not in cat_cov]
    if missing:
        print(f"⚠️ 类别缺席：{missing}")
    else:
        print("   12 类全达标")


if __name__ == "__main__":
    main()
