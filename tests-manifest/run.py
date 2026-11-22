#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""update-manifest 工作流生成器本地测试（v7.9.8 更新检查架构改造）。

用真实数据 + 变体夹具在本地验证 GitHub Actions 工作流
（.github/workflows/update-manifest.yml）的核心逻辑——
scripts/update_manifest.py + scripts/apk_meta.py：

  S1 真实 v7.9.7 Release JSON + 真实 APK 资产 → 生成清单全字段断言
     （与仓库 update/latest.json 同源生成——真实数据零虚构）。
  S2 apk_meta：真实 APK 元数据解析（aapt 基准真值）。
  S3 校验链拒绝面：Draft / Pre-release / 无 APK 资产 / 包名不匹配 /
     versionName≠tag / versionCode≠tag 公式 / 正文 versionCode 不一致 /
     SHA256 不符 / 大小不符。
  S4 陈旧守卫：仓库清单更新（versionCode 更高）→ SKIP；同码同内容 →
     幂等 SKIP；同码不同内容 → versionCode 冲突 FAIL。
  S5 幂等重跑：同输入两次生成 → 输出完全一致（manifestUpdatedAt 来自
     Release published_at——确定性输出，不引入生成时刻时间戳）。
  S6 workflow YAML 结构断言（触发/权限/并发闸在场）。

运行：python3 tests-manifest/run.py   （exit 0 = 全绿）
"""
import json
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "scripts", "update_manifest.py")
META = os.path.join(ROOT, "scripts", "apk_meta.py")

PASS = 0
FAIL = 0


def A(name, cond):
    global PASS, FAIL
    if cond:
        PASS += 1
        print("  ✓ " + name)
    else:
        FAIL += 1
        print("  ✗ " + name)


def run_gen(release, apk, repo_manifest=None, out=None, expect_pkg="com.zai.adshield"):
    cmd = [sys.executable, GEN, "--release-json", release, "--apk", apk,
           "--expect-package", expect_pkg]
    if repo_manifest:
        cmd += ["--repo-manifest", repo_manifest]
    if out:
        cmd += ["--out", out]
    r = subprocess.run(cmd, capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def real_release():
    """真实 v7.9.7 Release JSON（沙箱实测抓取的原样载荷——资产已替换后的当前态）。"""
    return {
        "tag_name": "v7.9.7",
        "name": "v7.9.7",
        "draft": False,
        "prerelease": False,
        "published_at": "2026-11-22T07:55:06Z",
        "body": "versionCode: 79700\n\n# 开屏卫士 v7.9.7\n\n1. 版本体系收口\n2. Bug 反馈一次性提示",
        "assets": [{
            "name": "adshield-v7.9.7.apk",
            "size": 886326,
            "digest": "sha256:2367a2fbb4d7b49c596e7f930383468b7fc310abbd185a2a462d2e9ddf4cd4c7",
            "browser_download_url": "https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.7/adshield-v7.9.7.apk",
        }],
    }


REAL_APK = "/tmp/adshield-v7.9.7.apk"  # 真实 Release 资产原样下载（沙箱已核验 SHA）


def write_json(obj, path):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False)


def main():
    if not os.path.isfile(REAL_APK):
        print("✗ 缺真实 APK 夹具 %s（先下载 Release 资产）" % REAL_APK)
        return 2
    tmp = tempfile.mkdtemp(prefix="upd-manifest-test-")
    rel_path = os.path.join(tmp, "rel.json")
    out_path = os.path.join(tmp, "latest.json")
    write_json(real_release(), rel_path)

    print("═══ S1 真实 Release + 真实 APK → 清单生成 ═══")
    rc, out = run_gen(rel_path, REAL_APK, out=out_path)
    A("S1a 生成成功（exit 0）", rc == 0)
    A("S1b 输出含真实 versionName/versionCode", "versionName 7.9.7" in out and "versionCode 79700" in out)
    with open(out_path, encoding="utf-8") as f:
        m = json.load(f)
    repo_manifest = json.load(open(os.path.join(ROOT, "update/latest.json"), encoding="utf-8"))
    A("S1c schemaVersion=1", m["schemaVersion"] == 1)
    A("S1d 真实字段：tag/publishedAt/apkUrl", m["releaseTag"] == "v7.9.7"
      and m["publishedAt"] == "2026-11-22T07:55:06Z"
      and m["apkUrl"].endswith("/v7.9.7/adshield-v7.9.7.apk"))
    A("S1e 真实 SHA256（实算=API digest）",
      m["apkSha256"] == "2367a2fbb4d7b49c596e7f930383468b7fc310abbd185a2a462d2e9ddf4cd4c7")
    A("S1f 真实大小 886326", m["apkSize"] == 886326)
    A("S1g packageName 真实", m["packageName"] == "com.zai.adshield")
    A("S1h mandatoryUpdate=false / minSupported=0", m["mandatoryUpdate"] is False and m["minSupportedVersionCode"] == 0)
    A("S1i 与仓库 update/latest.json 同源一致（versionCode/SHA/URL）",
      m["versionCode"] == repo_manifest["versionCode"] and m["apkSha256"] == repo_manifest["apkSha256"]
      and m["apkUrl"] == repo_manifest["apkUrl"])

    print("═══ S2 apk_meta（aapt 基准真值）═══")
    r = subprocess.run([sys.executable, META, "--json", REAL_APK], capture_output=True, text=True)
    meta = json.loads(r.stdout)
    A("S2a 包名", meta.get("package") == "com.zai.adshield")
    A("S2b versionName", meta.get("versionName") == "7.9.7")
    A("S2c versionCode", meta.get("versionCode") == 79700)

    print("═══ S3 校验链拒绝面 ═══")

    def bad_release(mutate):
        rel = real_release()
        mutate(rel)
        p = os.path.join(tmp, "rel_bad.json")
        write_json(rel, p)
        return p

    p = bad_release(lambda r: r.update(draft=True))
    rc, out = run_gen(p, REAL_APK)
    A("S3a Draft → 拒绝(2)", rc == 2 and "Draft" in out)

    p = bad_release(lambda r: r.update(prerelease=True))
    rc, out = run_gen(p, REAL_APK)
    A("S3b Pre-release → 拒绝(2)", rc == 2 and "Pre-release" in out)

    p = bad_release(lambda r: r.update(assets=[]))
    rc, out = run_gen(p, REAL_APK)
    A("S3c 无 APK 资产 → 拒绝(2)", rc == 2 and "无 .apk" in out)

    rc, out = run_gen(rel_path, REAL_APK, expect_pkg="com.other.app")
    A("S3d 包名闸（expect-package 错配 → 拒绝）", rc == 2 and "包名" in out)

    p = bad_release(lambda r: r.update(tag_name="v7.9.8"))
    rc, out = run_gen(p, REAL_APK)
    A("S3e tag≠APK versionName/公式 → 拒绝", rc == 2 and ("versionName" in out or "versionCode" in out))

    p = bad_release(lambda r: r.update(body="versionCode: 79900\n正文"))
    rc, out = run_gen(p, REAL_APK)
    A("S3f 正文 versionCode 不一致 → 拒绝", rc == 2 and "正文 versionCode" in out)

    p = bad_release(lambda r: r["assets"][0].update(digest="sha256:" + "0" * 64))
    rc, out = run_gen(p, REAL_APK)
    A("S3g SHA256 实算≠资产 digest → 拒绝", rc == 2 and "SHA256" in out)

    p = bad_release(lambda r: r["assets"][0].update(size=123))
    rc, out = run_gen(p, REAL_APK)
    A("S3h 大小实测≠资产 size → 拒绝", rc == 2 and "大小" in out)

    print("═══ S4 陈旧守卫 ═══")
    newer_repo = os.path.join(tmp, "repo_newer.json")
    write_json({"schemaVersion": 1, "versionName": "7.9.8", "versionCode": 79800,
                "apkSha256": "0" * 64}, newer_repo)
    rc, out = run_gen(rel_path, REAL_APK, repo_manifest=newer_repo)
    A("S4a 旧 Release 不覆盖新清单 → SKIP(3)", rc == 3 and "不覆盖" in out)

    same_repo = os.path.join(tmp, "repo_same.json")
    write_json(m, same_repo)
    rc, out = run_gen(rel_path, REAL_APK, repo_manifest=same_repo)
    A("S4b 同码同内容 → 幂等 SKIP(3)", rc == 3 and "幂等" in out)

    conflict_repo = os.path.join(tmp, "repo_conflict.json")
    bad = dict(m)
    bad["apkSha256"] = "9" * 64
    write_json(bad, conflict_repo)
    rc, out = run_gen(rel_path, REAL_APK, repo_manifest=conflict_repo)
    A("S4c 同码不同内容 → versionCode 冲突(4)", rc == 4 and "冲突" in out)

    older_repo = os.path.join(tmp, "repo_older.json")
    write_json({"schemaVersion": 1, "versionName": "7.9.6", "versionCode": 79600,
                "apkSha256": "0" * 64}, older_repo)
    out2 = os.path.join(tmp, "latest2.json")
    rc, out = run_gen(rel_path, REAL_APK, repo_manifest=older_repo, out=out2)
    A("S4d 新 Release 覆盖旧清单 → 正常生成", rc == 0)

    print("═══ S5 幂等重跑 ═══")
    out3 = os.path.join(tmp, "latest3.json")
    rc1, _ = run_gen(rel_path, REAL_APK, out=out3)
    first = open(out3, encoding="utf-8").read()
    rc2, _ = run_gen(rel_path, REAL_APK, out=out3)
    second = open(out3, encoding="utf-8").read()
    A("S5 同输入两次生成 → 字节级一致（确定性输出）", rc1 == 0 and rc2 == 0 and first == second)

    print("═══ S6 工作流结构 ═══")
    yml = open(os.path.join(ROOT, ".github/workflows/update-manifest.yml"), encoding="utf-8").read()
    A("S6a release.published 触发", "types: [published]" in yml)
    A("S6b workflow_dispatch 手动重生成入口", "workflow_dispatch:" in yml)
    A("S6c contents:write（清单提交）", "contents: write" in yml)
    A("S6d 并发闸（防重复生成竞态）", "concurrency:" in yml and "update-manifest" in yml)
    A("S6e GITHUB_TOKEN（零 PAT 红线）", "secrets.GITHUB_TOKEN" in yml and "github_pat" not in yml)
    A("S6f 线上核验步骤（不伪造部署成功）", "线上核验" in yml)

    print()
    print("══════════════════════════")
    print("  通过 %d · 失败 %d" % (PASS, FAIL))
    print("══════════════════════════")
    return 0 if FAIL == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
