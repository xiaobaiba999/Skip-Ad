#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""更新清单生成器（update/latest.json）——静态更新清单架构的核心生成端。

客户端（v7.9.8+）检查更新不再调用 GitHub Releases REST API（匿名 60 次/
小时/IP 出口配额），改为拉取仓库 main 分支静态清单：
  https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json

本脚本由 GitHub Actions（.github/workflows/update-manifest.yml）在正式
Release 发布（release.published）或手动触发（workflow_dispatch）时执行，
从【真实 Release + 真实 APK 资产】生成清单——绝不使用虚构的 SHA256/
文件大小/下载地址。

校验链（任何一步失败 → 非零退出，绝不产出清单；exit 码语义见下）：
  1. Release 非 Draft、非 Pre-release（Draft/Pre-release 不作正式更新）
  2. Release 存在 .apk 资产（取首个）
  3. APK 包名 == --expect-package（防错仓/换包）
  4. APK versionName == tag 去掉 v 前缀（三段式同名同码）
  5. APK versionCode == tag 三段公式 major*10000+minor*1000+patch*100
     （v7.1.0 起项目公式；v7.10.0 统一规则：非三段 tag（历史 plus 系）
     一律硬失败拒绝生成——Release 正文 versionCode 元数据行不再作为
     回退定码来源，仅作一致性校验）
  6. APK 实算 SHA256 == 资产 digest（GitHub API 官方值；资产无 digest
     时记录实算值并通过——大小校验仍硬性）
  7. APK 实测文件大小 == 资产 size
  8. 陈旧守卫：仓库现有清单 versionCode >= 新 versionCode → 不覆盖
     （同码同内容 → 幂等 SKIP；同码不同内容 → 冲突失败）

exit 0 = 生成/幂等 SKIP（stdout 标记）；2 = 校验失败；3 = 陈旧 SKIP；
4 = versionCode 冲突。

用法：
  python3 scripts/update_manifest.py --release-json rel.json --apk app.apk \
      [--repo-manifest update/latest.json] [--out update/latest.json] \
      [--expect-package com.zai.adshield]
"""
import argparse
import hashlib
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from apk_meta import apk_meta  # noqa: E402

SCHEMA_VERSION = 1
EXIT_OK = 0
EXIT_INVALID = 2
EXIT_STALE_SKIP = 3
EXIT_VC_CONFLICT = 4


def version_code_of_tag(tag):
    """三段式 tag（vX.Y.Z）→ 项目公式 versionCode；非三段 → None。"""
    m = re.match(r"^v?(\d+)\.(\d+)\.(\d+)$", tag or "")
    if not m:
        return None
    major, minor, patch = (int(x) for x in m.groups())
    return major * 10000 + minor * 1000 + patch * 100


def version_code_of_notes(body):
    """Release 正文首个「versionCode: N」元数据行（v7.10.0 统一规则：仅作
    与 APK versionCode 的一致性校验——绝不作为定码回退来源；客户端
    Net.versionCodeOfNotes 已删）。"""
    for line in (body or "").split("\n"):
        t = line.strip()
        if t.startswith("versionCode:"):
            m = re.match(r"^(\d+)", t[len("versionCode:"):].strip())
            if m:
                v = int(m.group(1))
                if 0 < v <= 2147483647:
                    return v
            return 0
    return 0


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def fail(code, msg):
    print("✗ %s" % msg)
    sys.exit(code)


def main(argv):
    ap = argparse.ArgumentParser(description="生成静态更新清单 latest.json")
    ap.add_argument("--release-json", required=True, help="GitHub Release API JSON 路径")
    ap.add_argument("--apk", required=True, help="已下载的 APK 资产路径")
    ap.add_argument("--repo-manifest", default=None, help="仓库现有清单（陈旧守卫；缺省跳过守卫）")
    ap.add_argument("--out", default=None, help="输出路径（缺省只校验不写文件）")
    ap.add_argument("--expect-package", default="com.zai.adshield")
    args = ap.parse_args(argv)

    with open(args.release_json, "r", encoding="utf-8") as f:
        rel = json.load(f)

    # ── 1. Draft / Pre-release 拒绝 ──
    if rel.get("draft"):
        fail(EXIT_INVALID, "Release 为 Draft——不作正式更新清单")
    if rel.get("prerelease"):
        fail(EXIT_INVALID, "Release 为 Pre-release——不覆盖正式清单")
    tag = rel.get("tag_name") or ""
    if not tag:
        fail(EXIT_INVALID, "Release 缺 tag_name")

    # ── 2. .apk 资产在场 ──
    apk_asset = None
    for a in rel.get("assets") or []:
        if isinstance(a, dict) and str(a.get("name") or "").endswith(".apk"):
            apk_asset = a
            break
    if apk_asset is None:
        fail(EXIT_INVALID, "Release 无 .apk 资产——拒绝生成清单")

    # ── 3. APK 元数据（自研 AXML 解析——零 SDK 依赖）──
    meta = apk_meta(args.apk)
    pkg = meta.get("package")
    vn = meta.get("versionName")
    vc = meta.get("versionCode")
    if pkg != args.expect_package:
        fail(EXIT_INVALID, "APK 包名 %r != 期望 %r（错仓/换包）" % (pkg, args.expect_package))
    if not isinstance(vc, int) or vc <= 0:
        fail(EXIT_INVALID, "APK versionCode 缺失/非法: %r" % (vc,))
    if not vn:
        fail(EXIT_INVALID, "APK versionName 缺失")

    # ── 4/5. tag ↔ APK 同名同码（三段公式；plus 系回退正文元数据行）──
    tag_vn = re.sub(r"^v", "", tag)
    derived = version_code_of_tag(tag)
    notes_vc = version_code_of_notes(rel.get("body") or "")
    if derived is not None:
        if vn != tag_vn:
            fail(EXIT_INVALID, "APK versionName %r != tag %r（三段式同名同码红线）" % (vn, tag_vn))
        if vc != derived:
            fail(EXIT_INVALID, "APK versionCode %d != tag 公式值 %d（major*10000+minor*1000+patch*100）"
                 % (vc, derived))
        if notes_vc and notes_vc != vc:
            fail(EXIT_INVALID, "Release 正文 versionCode %d != APK versionCode %d" % (notes_vc, vc))
    else:
        # v7.10.0 统一规则：非三段 tag（历史 plus 系）一律硬失败——绝不从
        # Release 正文 versionCode 元数据行回退定码（客户端侧同名回退链已删）
        fail(EXIT_INVALID, "非三段 tag %r——正式发布必须使用三段式 vX.Y.Z tag"
             "（统一版本规则：versionName/正文元数据行不作定码来源）" % tag)

    # ── 6. SHA256 实算 == 资产 digest ──
    actual_sha = sha256_of(args.apk)
    asset_digest = str(apk_asset.get("digest") or "")
    asset_sha_hex = ""
    if asset_digest.lower().startswith("sha256:"):
        asset_sha_hex = asset_digest[len("sha256:"):].lower()
    if asset_sha_hex and asset_sha_hex != actual_sha:
        fail(EXIT_INVALID, "APK 实算 SHA256 %s != 资产 digest %s" % (actual_sha, asset_sha_hex))

    # ── 7. 文件大小 == 资产 size ──
    actual_size = os.path.getsize(args.apk)
    asset_size = apk_asset.get("size") or 0
    if asset_size and actual_size != asset_size:
        fail(EXIT_INVALID, "APK 实测大小 %d != 资产 size %d" % (actual_size, asset_size))

    # ── 8. 陈旧守卫（旧版本 Release 不覆盖新版本清单）──
    if args.repo_manifest and os.path.isfile(args.repo_manifest):
        try:
            with open(args.repo_manifest, "r", encoding="utf-8") as f:
                cur = json.load(f)
            cur_vc = int(cur.get("versionCode") or 0)
            if cur_vc > vc:
                print("SKIP: 仓库清单 versionCode %d > 新 Release %d——旧版本不覆盖新清单" % (cur_vc, vc))
                sys.exit(EXIT_STALE_SKIP)
            if cur_vc == vc:
                same = (cur.get("apkSha256") == actual_sha
                        and cur.get("versionName") == vn
                        and cur.get("apkUrl") == apk_asset.get("browser_download_url"))
                if same:
                    print("SKIP: versionCode %d 清单已在库（内容一致，幂等）" % vc)
                    sys.exit(EXIT_STALE_SKIP)
                fail(EXIT_VC_CONFLICT, "versionCode %d 与仓库清单冲突且内容不同——versionCode 不得复用" % vc)
        except (ValueError, TypeError):
            print("⚠ 仓库现有清单损坏——按无守卫处理（直接生成）")

    # ── 产出清单（真实数据，零虚构）──
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "versionName": vn,
        "versionCode": vc,
        "releaseTag": tag,
        "releaseName": rel.get("name") or tag,
        "releaseNotes": rel.get("body") or "",
        "publishedAt": rel.get("published_at") or "",
        "apkUrl": apk_asset.get("browser_download_url") or "",
        "apkFileName": apk_asset.get("name") or "adshield.apk",
        "apkSha256": actual_sha,
        "apkSize": actual_size,
        "packageName": pkg,
        "minSupportedVersionCode": int(rel.get("min_supported_version_code") or 0),
        "mandatoryUpdate": False,
        "manifestUpdatedAt": rel.get("published_at") or "",
    }

    if args.out:
        out_dir = os.path.dirname(os.path.abspath(args.out))
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(manifest, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("OK: %s（versionName %s · versionCode %d · SHA256 %s · %d 字节）"
              % (args.out, vn, vc, actual_sha[:16] + "…", actual_size))
    else:
        print("OK: 校验通过（未写文件——缺 --out）versionName %s · versionCode %d" % (vn, vc))
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
