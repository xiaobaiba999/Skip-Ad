#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""update_manifest.py 校验闸自动化测试（发布链质量闸 · 真实实体矩阵）。

测试实体全部真实（不造假数据）：
  · APK：仓库 android-app/dist/ 历史归档（apk_meta 实测 package/versionName/
    versionCode；apk_cert 实测签名证书指纹）
  · Release JSON：scripts/testdata/release-<tag>.json（GitHub Releases API
    原文固化；assets[].digest/size 与本地 APK 逐字节核验匹配）
  · 篡改用例：仅程序化修改 fixture JSON 字段模拟非法发布场景（tag 错误/
    digest 错误/无资产/Draft/Pre-release 等）——APK 永远真实。

矩阵（对应用户验收要求）：
  合法（应通过/幂等）：
    v7.15.14 → 86400 公式路径（Release 正文无 versionCode 行——公式路径不要求）
    v8.0.7  → 87100 抬码路径（跨 7.15.x 存量段 85000-86400）
    v8.0.8  → 88000 抬码路径
    v8.0.9  → 88100 抬码路径
    幂等重跑 v8.0.9（线上清单已是 v8.0.9）→ exit 3 SKIP
    --version-code 透传 == APK 实际码（工作流新链路）→ 通过
  非法（应拒绝 exit 2）：
    非三段 tag（v8.0 / 8.0.9-rc1）
    APK versionName != tag（v8.0.10 tag 配 8.0.9 APK）
    APK SHA-256 != 资产 digest
    Release 无 .apk 资产
    Draft / Pre-release
    抬码但正文无 versionCode 元数据行
    --version-code 传参 != APK 实际码
    --expect-cert-sha256 错误指纹（签名闸）
  守卫语义：
    旧 Release 不覆盖新清单（v8.0.7 vs 88000 线上）→ exit 3 SKIP（非失败）
    同码不同内容（versionCode 复用）→ exit 4 冲突
  无真实实体的分支（如实标注，不造假）：
    纯降码（vn 一致且 vc < 公式值）：历史不存在此实体——由 versionName
    闸 + 单元断言 version_code_of_tag 覆盖下界逻辑；标注 NOT_APPLICABLE
    v8.1.0 完整合法链路：待真实 v8.1.0 APK 存在后自动可跑（与 v8.0.7/8/9
    同一代码路径）；单元断言公式下界 version_code_of_tag('v8.1.0')==81000

运行：python3 scripts/test_update_manifest.py（unittest，零第三方依赖）
"""
import copy
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DIST = os.path.join(ROOT, "android-app", "dist")
TESTDATA = os.path.join(HERE, "testdata")
SCRIPT = os.path.join(HERE, "update_manifest.py")

# 实测值（apk_meta/apk_cert 于 2026-11-27 核验；测试内会再断言）
HISTORICAL_CERT = "05ada6e3f7a4fc6bf77449611c24f51e1ef4caa0a91a5cdd02c4d877738e6f79"
LEGACY_MAX_VC = 86400  # v7.15.14（公式路径终点 / 抬码必须跨过的存量段上界）

EXIT_OK, EXIT_INVALID, EXIT_STALE_SKIP, EXIT_VC_CONFLICT = 0, 2, 3, 4


def apk_path(name):
    p = os.path.join(DIST, name)
    if not os.path.isfile(p):
        raise FileNotFoundError(p)
    return p


def load_release(tag):
    p = os.path.join(TESTDATA, "release-%s.json" % tag)
    if not os.path.isfile(p):
        raise FileNotFoundError(p)
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)


def write_json(obj, path):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False)


def make_repo_manifest(version_code, version_name="8.0.8", sha="0" * 64, url="x"):
    """构造 --repo-manifest 输入（模拟某时点的线上清单基线）。"""
    return {
        "schemaVersion": 1, "versionName": version_name, "versionCode": version_code,
        "releaseTag": "v" + version_name, "releaseName": version_name,
        "releaseNotes": "", "publishedAt": "", "apkUrl": url,
        "apkFileName": "adshield-%s.apk" % version_name, "apkSha256": sha,
        "apkSize": 0, "packageName": "com.zai.adshield",
        "manifestUpdatedAt": "",
    }


class BaseCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="um-test-")
        self.addCleanup(lambda: __import__("shutil").rmtree(self.tmp, True))

    def run_gen(self, release, apk, repo_manifest=None, extra_args=None, out=None):
        """跑真实 scripts/update_manifest.py，返回 (exit, stdout)。"""
        rel_path = os.path.join(self.tmp, "rel.json")
        write_json(release, rel_path)
        out_path = out or os.path.join(self.tmp, "latest.json")
        rm_path = None
        if repo_manifest is not None:
            rm_path = os.path.join(self.tmp, "repo-latest.json")
            write_json(repo_manifest, rm_path)
        cmd = [sys.executable, SCRIPT,
               "--release-json", rel_path, "--apk", apk, "--out", out_path]
        if rm_path:
            cmd += ["--repo-manifest", rm_path]
        cmd += (extra_args or [])
        r = subprocess.run(cmd, capture_output=True, text=True)
        return r.returncode, (r.stdout + r.stderr)


class TestLegitimateVersions(BaseCase):
    """合法版本：真实 APK + 真实 Release → 生成通过，产出与资产一致。"""

    def _assert_manifest_fields(self, out_path, rel, expect_vc, expect_vn, apk):
        self.assertTrue(os.path.isfile(out_path), "应产出清单文件")
        with open(out_path, encoding="utf-8") as f:
            m = json.load(f)
        asset = [a for a in rel["assets"] if a["name"].endswith(".apk")][0]
        self.assertEqual(m["versionCode"], expect_vc)
        self.assertEqual(m["versionName"], expect_vn)
        self.assertEqual(m["releaseTag"], rel["tag_name"])
        self.assertEqual(m["apkSha256"], asset["digest"].split(":", 1)[1])
        self.assertEqual(m["apkSize"], asset["size"])
        self.assertEqual(m["apkUrl"], asset["browser_download_url"])
        self.assertEqual(m["packageName"], "com.zai.adshield")

    def test_v7_15_14_formula_path(self):
        """v7.15.14 → 86400：公式路径（vc==公式值；正文无 versionCode 行也放行）。"""
        rel, apk = load_release("v7.15.14"), apk_path("adshield-v7.15.14.apk")
        out = os.path.join(self.tmp, "out-715.json")
        rc, log = self.run_gen(rel, apk, repo_manifest=make_repo_manifest(85000, "7.15.9"), out=out)
        self.assertEqual(rc, EXIT_OK, log)
        self.assertIn("公式定码", log)
        self._assert_manifest_fields(out, rel, 86400, "7.15.14", apk)

    def test_v8_0_7_lifted_path_crosses_legacy_segment(self):
        """v8.0.7 → 87100：抬码跨 7.15.x 存量段（vs 86400 线上基线）。"""
        rel, apk = load_release("v8.0.7"), apk_path("adshield-v8.0.7.apk")
        out = os.path.join(self.tmp, "out-807.json")
        rc, log = self.run_gen(rel, apk, repo_manifest=make_repo_manifest(LEGACY_MAX_VC, "7.15.14"), out=out)
        self.assertEqual(rc, EXIT_OK, log)
        self.assertIn("显式抬码", log)
        self._assert_manifest_fields(out, rel, 87100, "8.0.7", apk)

    def test_v8_0_8_lifted_path(self):
        """v8.0.8 → 88000：抬码路径（vs 87100 线上基线）。"""
        rel, apk = load_release("v8.0.8"), apk_path("adshield-v8.0.8.apk")
        out = os.path.join(self.tmp, "out-808.json")
        rc, log = self.run_gen(rel, apk, repo_manifest=make_repo_manifest(87100, "8.0.7"), out=out)
        self.assertEqual(rc, EXIT_OK, log)
        self._assert_manifest_fields(out, rel, 88000, "8.0.8", apk)

    def test_v8_0_9_lifted_path(self):
        """v8.0.9 → 88100：抬码路径（vs 88000 线上基线）——run 31 失败场景根治复验。"""
        rel, apk = load_release("v8.0.9"), apk_path("adshield-v8.0.9.apk")
        out = os.path.join(self.tmp, "out-809.json")
        rc, log = self.run_gen(rel, apk, repo_manifest=make_repo_manifest(88000, "8.0.8"), out=out)
        self.assertEqual(rc, EXIT_OK, log)
        self.assertIn("显式抬码", log)
        self.assertIn("签名闸", log)
        self._assert_manifest_fields(out, rel, 88100, "8.0.9", apk)

    def test_v8_0_9_with_explicit_version_code_passthrough(self):
        """工作流新链路：apk_meta 读码 --version-code 显式透传（==APK 实际码）。"""
        rel, apk = load_release("v8.0.9"), apk_path("adshield-v8.0.9.apk")
        meta = json.loads(subprocess.run(
            [sys.executable, os.path.join(HERE, "apk_meta.py"), "--json", apk],
            capture_output=True, text=True, check=True).stdout)
        self.assertEqual(meta["versionCode"], 88100)
        out = os.path.join(self.tmp, "out-passthrough.json")
        rc, log = self.run_gen(rel, apk, repo_manifest=make_repo_manifest(88000, "8.0.8"),
                               extra_args=["--version-code", str(meta["versionCode"])], out=out)
        self.assertEqual(rc, EXIT_OK, log)

    def test_idempotent_rerun_same_version_skips(self):
        """幂等：线上已是 v8.0.9（同码同内容）→ exit 3 SKIP 而非失败。"""
        rel, apk = load_release("v8.0.9"), apk_path("adshield-v8.0.9.apk")
        asset = [a for a in rel["assets"] if a["name"].endswith(".apk")][0]
        same = make_repo_manifest(88100, "8.0.9",
                                  sha=asset["digest"].split(":", 1)[1],
                                  url=asset["browser_download_url"])
        rc, log = self.run_gen(rel, apk, repo_manifest=same)
        self.assertEqual(rc, EXIT_STALE_SKIP, log)
        self.assertIn("幂等", log)


class TestIllegalRejections(BaseCase):
    """非法发布：真实 APK + 篡改 fixture → 全部拒绝（exit 2）。"""

    def test_non_three_segment_tag(self):
        """非三段 tag（v8.0 两段 / 8.0.9-rc1 预发布式）→ 拒绝。"""
        for bad in ("v8.0", "8.0.9-rc1"):
            rel = load_release("v8.0.9")
            rel["tag_name"] = bad
            rc, log = self.run_gen(rel, apk_path("adshield-v8.0.9.apk"))
            self.assertEqual(rc, EXIT_INVALID, "tag=%s 应拒绝：%s" % (bad, log))

    def test_version_name_tag_mismatch(self):
        """APK versionName != tag（tag v8.0.10 配 8.0.9 APK）→ 拒绝。"""
        rel = load_release("v8.0.9")
        rel["tag_name"] = "v8.0.10"
        rc, log = self.run_gen(rel, apk_path("adshield-v8.0.9.apk"))
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("versionName", log)

    def test_sha256_digest_mismatch(self):
        """APK 实算 SHA-256 != 资产 digest → 拒绝。"""
        rel = load_release("v8.0.9")
        for a in rel["assets"]:
            if a["name"].endswith(".apk"):
                a["digest"] = "sha256:" + "ab" * 32
        rc, log = self.run_gen(rel, apk_path("adshield-v8.0.9.apk"))
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("SHA256", log)

    def test_size_mismatch(self):
        """APK 实测大小 != 资产 size → 拒绝。"""
        rel = load_release("v8.0.9")
        for a in rel["assets"]:
            if a["name"].endswith(".apk"):
                a["size"] = a["size"] + 1
        rc, log = self.run_gen(rel, apk_path("adshield-v8.0.9.apk"))
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("大小", log)

    def test_no_apk_asset(self):
        """Release 无 .apk 资产 → 拒绝。"""
        rel = load_release("v8.0.9")
        rel["assets"] = []
        rc, log = self.run_gen(rel, apk_path("adshield-v8.0.9.apk"))
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("无 .apk 资产", log)

    def test_draft_release(self):
        """Draft Release → 拒绝。"""
        rel = load_release("v8.0.9")
        rel["draft"] = True
        rc, log = self.run_gen(rel, apk_path("adshield-v8.0.9.apk"))
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("Draft", log)

    def test_prerelease(self):
        """Pre-release → 拒绝（不覆盖正式清单）。"""
        rel = load_release("v8.0.9")
        rel["prerelease"] = True
        rc, log = self.run_gen(rel, apk_path("adshield-v8.0.9.apk"))
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("Pre-release", log)

    def test_lifted_without_notes_metadata_line(self):
        """抬码但 Release 正文无 versionCode 元数据行 → 拒绝（可考古红线）。"""
        rel = load_release("v8.0.9")
        rel["body"] = "\n".join(
            line for line in (rel.get("body") or "").split("\n")
            if not line.strip().startswith("versionCode:"))
        self.assertNotIn("versionCode:", rel["body"])
        rc, log = self.run_gen(rel, apk_path("adshield-v8.0.9.apk"))
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("元数据行", log)

    def test_explicit_version_code_mismatch(self):
        """--version-code 传参 != APK 实际码 → 拒绝（防静默偏差）。"""
        rel, apk = load_release("v8.0.9"), apk_path("adshield-v8.0.9.apk")
        rc, log = self.run_gen(rel, apk, extra_args=["--version-code", "88101"])
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("显式码必须与 APK 一致", log)

    def test_wrong_signature(self):
        """签名证书指纹 != 历史正式签名 → 拒绝（错签覆盖升级必被拒）。"""
        rel, apk = load_release("v8.0.9"), apk_path("adshield-v8.0.9.apk")
        rc, log = self.run_gen(rel, apk, extra_args=["--expect-cert-sha256", "cd" * 32])
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("签名证书指纹", log)

    def test_wrong_package(self):
        """APK 包名 != 期望包名 → 拒绝。"""
        rel, apk = load_release("v8.0.9"), apk_path("adshield-v8.0.9.apk")
        rc, log = self.run_gen(rel, apk, extra_args=["--expect-package", "com.other.app"])
        self.assertEqual(rc, EXIT_INVALID, log)
        self.assertIn("包名", log)


class TestGuardSemantics(BaseCase):
    """守卫语义：陈旧 SKIP（3）与 versionCode 复用冲突（4）。"""

    def test_stale_release_does_not_overwrite(self):
        """旧 Release（v8.0.7/87100）不覆盖新清单（88000）→ exit 3 SKIP。"""
        rel, apk = load_release("v8.0.7"), apk_path("adshield-v8.0.7.apk")
        rc, log = self.run_gen(rel, apk, repo_manifest=make_repo_manifest(88000, "8.0.8"))
        self.assertEqual(rc, EXIT_STALE_SKIP, log)
        self.assertIn("不覆盖", log)

    def test_version_code_reuse_conflict(self):
        """同 versionCode 不同内容（88100 复用 + 不同 SHA）→ exit 4 冲突。"""
        rel, apk = load_release("v8.0.9"), apk_path("adshield-v8.0.9.apk")
        rc, log = self.run_gen(rel, apk, repo_manifest=make_repo_manifest(88100, "8.0.9", sha="ee" * 32))
        self.assertEqual(rc, EXIT_VC_CONFLICT, log)
        self.assertIn("不得复用", log)


class TestUnitLevelBounds(unittest.TestCase):
    """单元级下界断言（无真实实体的分支如实覆盖逻辑，不造数据）。"""

    def test_formula_lower_bounds(self):
        sys.path.insert(0, HERE)
        import update_manifest as um
        self.assertEqual(um.version_code_of_tag("v8.1.0"), 81000)  # 未来版公式下界
        self.assertEqual(um.version_code_of_tag("v8.0.9"), 80900)
        self.assertEqual(um.version_code_of_tag("v7.15.14"), 86400)
        self.assertIsNone(um.version_code_of_tag("v8.0"))  # 非三段 → None → 拒绝
        # 抬码闸关键常数（跨 7.15.x 存量段）
        self.assertEqual(um.LEGACY_MAX_VC, 86400)

    def test_historical_cert_constant(self):
        sys.path.insert(0, HERE)
        import update_manifest as um
        self.assertEqual(um.DEFAULT_EXPECT_CERT_SHA256, HISTORICAL_CERT)


class TestRealApkMetadata(unittest.TestCase):
    """真实 APK 元数据/签名批量断言（发布链基准真值固化）。"""

    def test_dist_apks_metadata_and_signature(self):
        sys.path.insert(0, HERE)
        from apk_meta import apk_meta
        from apk_cert import apk_cert_sha256
        expect = {
            "adshield-v7.15.14.apk": ("7.15.14", 86400),
            "adshield-v8.0.7.apk": ("8.0.7", 87100),
            "adshield-v8.0.8.apk": ("8.0.8", 88000),
            "adshield-v8.0.9.apk": ("8.0.9", 88100),
        }
        for name, (vn, vc) in expect.items():
            p = apk_path(name)
            m = apk_meta(p)
            self.assertEqual(m["package"], "com.zai.adshield", name)
            self.assertEqual(m["versionName"], vn, name)
            self.assertEqual(m["versionCode"], vc, name)
            certs = apk_cert_sha256(p)
            self.assertTrue(certs, "%s 应有 v2+ 签名块" % name)
            for scheme, fps in certs.items():
                for fp in fps:
                    self.assertEqual(fp, HISTORICAL_CERT,
                                     "%s %s 签名与历史不一致" % (name, scheme))


if __name__ == "__main__":
    unittest.main(verbosity=2)
