#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""workflow 文件专用推送（git database API 通道）。

背景：仓库 PAT 仅 repo scope（x-oauth-scopes: repo 实测），git push 含
.github/workflows/* 变更的提交被服务端拒绝（refusing to allow a PAT
to create or update workflow ... without `workflow` scope）。本脚本把
【仅工作流文件】的更新经 git database API（blobs → trees → commits →
refs）落到 origin/main：

  1. GET  /git/ref/heads/main                      → 远程 head sha + 树
  2. POST /git/blobs（工作流文件内容，utf-8）        → blob shas
  3. POST /git/trees（base_tree=远程 head 树 + 替换工作流路径）
  4. POST /git/commits（parent=远程 head，message=本地提交原文）
  5. PATCH /git/refs/heads/main                    → 前移 main
  6. 核验：GET contents API 逐字节比对远程文件 == 本地文件

硬闸：
  - 源分支 workflow-pending 必须恰好领先 origin/main 1 个提交且仅含
    工作流文件变更（待所有者应用的工作流修复包，见
    docs/patches/update-system-v810-workflow-apk-driven/README.md）；
  - 每步 API 非成功立即中止（绝不半提交）；
  - 完成后远程 blob 与本地逐字节一致性核验；
  - 不打印令牌。

令牌（需含 workflow scope——2026-11-27 实测：仅 repo scope 的 PAT 对
.github/workflows/* 的一切写通道全部被封：git push 明示拒绝、
git database API trees 对该前缀返回伪装 404）：
  环境变量 SKIPAD_GH_TOKEN / GH_TOKEN / GITHUB_TOKEN，或 /tmp/.ghtoken 兜底。

用法：python3 push-workflow-dbapi.py   （在仓库工作区根运行）
"""
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

REPO_API = "https://api.github.com/repos/xiaobaiba999/Skip-Ad"
PENDING_BRANCH = "workflow-pending"
WF_FILES = [
    ".github/workflows/update-manifest.yml",
    ".github/workflows/purge-update-cdn.yml",
]


def read_token():
    for k in ("SKIPAD_GH_TOKEN", "GH_TOKEN", "GITHUB_TOKEN"):
        v = os.environ.get(k, "").strip()
        if v:
            return v
    try:
        with open("/tmp/.ghtoken") as f:
            return f.read().strip()
    except FileNotFoundError:
        return ""


TOKEN = read_token()


def api(method, path, payload=None, expect=(200, 201)):
    url = REPO_API + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, method=method, data=data, headers={
        "Authorization": "Bearer " + TOKEN,
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
    })
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            body = r.read()
            if r.status not in expect:
                raise SystemExit("✗ %s %s → HTTP %d: %s" % (method, path, r.status, body[:400]))
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        raise SystemExit("✗ %s %s → HTTP %d: %s" % (method, path, e.code, e.read()[:400]))


def git(*args):
    r = subprocess.run(["git"] + list(args), capture_output=True, text=True, check=True)
    return r.stdout.strip()


def main():
    if not TOKEN:
        raise SystemExit("✗ 未提供令牌（SKIPAD_GH_TOKEN / GH_TOKEN / GITHUB_TOKEN 环境变量）——需含 workflow scope")
    # ── 硬闸：待应用分支恰好领先 origin/main 1 个提交且仅含工作流文件 ──
    git("fetch", "origin", "main")
    pending = PENDING_BRANCH
    ahead = git("rev-list", "--count", "origin/main.." + pending)
    if ahead != "1":
        raise SystemExit("✗ %s 领先 origin/main %s 个提交（要求恰好 1 个）" % (pending, ahead))
    changed = [l for l in git("diff", "--name-only", "origin/main.." + pending).splitlines() if l]
    illegal = [p for p in changed if p not in WF_FILES]
    if illegal:
        raise SystemExit("✗ 提交含非工作流文件变更（本通道仅限工作流）：%s" % illegal)

    local_sha = git("rev-parse", pending)
    parent_msg = git("log", "-1", "--pretty=%B", pending)

    # 文件内容取自待应用提交的树（而非当前工作区）
    for path in WF_FILES:
        if path not in changed:
            raise SystemExit("✗ 待应用提交缺少 %s" % path)

    # ── 1. 远程 head ──
    ref = api("GET", "/git/ref/heads/main")
    remote_head = ref["object"]["sha"]
    if remote_head != git("rev-parse", "origin/main"):
        raise SystemExit("✗ 远程 main 与本地 origin/main 不一致（并发写入），中止")
    head_commit = api("GET", "/git/commits/" + remote_head)
    base_tree = head_commit["tree"]["sha"]
    print("① 远程 main head: %s（tree %s）" % (remote_head[:10], base_tree[:10]))

    # ── 2. blobs（内容取自待应用提交的树）──
    tree_entries = []
    for path in WF_FILES:
        content = git("show", "%s:%s" % (pending, path))
        blob = api("POST", "/git/blobs", {"content": content, "encoding": "utf-8"})
        tree_entries.append({"path": path, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        print("② blob: %s ← %s" % (blob["sha"][:10], path))

    # ── 3. tree ──
    tree = api("POST", "/git/trees", {"base_tree": base_tree, "tree": tree_entries})
    print("③ tree: %s" % tree["sha"][:10])

    # ── 4. commit ──
    commit = api("POST", "/git/commits", {
        "message": parent_msg.rstrip("\n"),
        "tree": tree["sha"],
        "parents": [remote_head],
    })
    print("④ commit: %s" % commit["sha"][:10])

    # ── 5. PATCH refs ──
    api("PATCH", "/git/refs/heads/main", {"sha": commit["sha"], "force": False}, expect=(200,))
    print("⑤ main → %s" % commit["sha"][:10])

    # ── 6. 逐字节核验 ──
    ok = True
    for path in WF_FILES:
        remote = api("GET", "/contents/" + path + "?ref=main", expect=(200,))
        remote_bytes = base64.b64decode(remote["content"])
        local_bytes = git("show", "%s:%s" % (pending, path)).encode()
        if remote_bytes == local_bytes:
            print("⑥ ✓ 远程 %s == 本地（%d 字节逐字节一致）" % (path, len(local_bytes)))
        else:
            ok = False
            print("⑥ ✗ %s 远程/本地不一致！远程 %d 字节 vs 本地 %d 字节" % (path, len(remote_bytes), len(local_bytes)))
    if not ok:
        raise SystemExit("✗ 核验失败")
    print("\n=== workflow 文件已落 origin/main（git database API 通道）===")
    print("本地提交 %s 的内容已复制为远程提交 %s" % (local_sha[:10], commit["sha"][:10]))


if __name__ == "__main__":
    main()
