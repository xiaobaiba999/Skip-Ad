#!/usr/bin/env python3
"""为 xiaobaiba999/Skip-Ad 创建 v1.0.0~v1.3.0 的 Tag + Release 并上传 APK。

用法: GITHUB_TOKEN=xxx python3 publish-releases.py
- Tag 经 Git data API 创建（不触发 push 事件）
- Release 按时间顺序创建，v1.3.0 置为 latest
- APK 从 android-app/dist/ 上传为 Release 附件
"""
import json
import os
import sys
import urllib.request

TOKEN = os.environ.get("GITHUB_TOKEN", "")
REPO = "xiaobaiba999/Skip-Ad"
API = f"https://api.github.com/repos/{REPO}"
UPLOADS = f"https://uploads.github.com/repos/{REPO}"

if not TOKEN:
    sys.exit("缺少 GITHUB_TOKEN 环境变量")

# tag → (commit sha, apk 文件名, 是否 latest, 发布说明)
C35778F = os.popen("git -C /home/z/my-project rev-parse c35778f").read().strip()  # 初始导入（v1.2.0 时代的全量提交）
B4CB3A8 = os.popen("git -C /home/z/my-project rev-parse b4cb3a8").read().strip()
HEAD = os.popen("git -C /home/z/my-project rev-parse HEAD").read().strip()

COMMON_INSTALL = """### 安装 / 升级 / 卸载

| 操作 | 说明 |
| --- | --- |
| 新安装 | 下载 APK → 允许「安装未知应用」→ 安装 → 首页开启无障碍服务 |
| 覆盖升级 | 直接安装更高版本（同一 `adshield.keystore` 签名 + versionCode 递增），无需卸载，学习数据保留 |
| 正常卸载 | 系统设置 → 应用管理 → 开屏卫士 → 卸载（普通用户应用，无锁定机制） |
"""

RELEASES = [
    ("v1.0.0", C35778F, "adshield-v1.0.0.apk", False, f"""## 开屏卫士 v1.0.0 · 初版

初版功能：**开屏广告演练场、无障碍自动跳过服务、习惯学习引擎**。

- 冷启动通用规则：开屏 12s 时间窗内定位含「跳过/skip」的小面积可点击控件，毫秒级代点
- 习惯学习：观察手动点击 → 生成应用专属规则（文本 + viewId）→ 下次优先按 viewId 精确命中
- 安全阀：面积 <35% 屏面积 · 点击冷却 1.5s · 时间窗 12s · 只点「跳过」类控件
- 约 45KB，零第三方依赖，零网络权限，数据全部本地

{COMMON_INSTALL}"""),
    ("v1.1.0", C35778F, "adshield-v1.1.0.apk", False, f"""## 开屏卫士 v1.1.0

新增：**策略情报站、学习中心规则导入导出、演练场 4 种对抗策略**（versionCode 10100）。

- 策略情报站：2026-11 市场扫描——8 种广告套路 vs 反制手段 + 六路防线 + 资料来源
- 演练场 4 策略：标准开屏 / 延迟跳过 / 假关闭× / 摇一摇热区（全屏热区 + 极小跳过按钮）
- 学习中心：规则导出/导入 JSON，换机或重装后可恢复学习成果

{COMMON_INSTALL}"""),
    ("v1.2.0", B4CB3A8, "adshield-v1.2.0.apk", False, f"""## 开屏卫士 v1.2.0 · 精准判断引擎

新增：**多信号评分区分「广告诱导跳转」与「用户主动操作」**（versionCode 10200）。

| 信号 | 广告行为（+） | 用户行为（−） |
| --- | --- | --- |
| 触发源 | 全屏热区 +68 / 假关闭按钮 +80 | 语义即用户意图 |
| 按压时长 | <40ms（快于人手）+25 | 40~350ms 人工区间 −5 |
| 移动轨迹 | 0px 瞬移点击 +15 | 轻微抖动 −8 / 明显拖动 −15 |
| 反应时间 | 展示后 <150ms 即点 +20 | 观察 >600ms −15 |

- 总分 ≥60 判「广告诱导跳转」并拦截；误判一键纠正，≥2 次自动放宽门槛 60→74（防误伤）
- 真实服务新增「疑似误触」观察统计（全屏热区 / 窗口切换后 700ms 内异常点击），只记录不拦截
- 与「拦精灵」案裁判边界一致：只替用户点击「跳过」，不屏蔽广告本身

{COMMON_INSTALL}"""),
    ("v1.3.0", HEAD, "adshield-v1.3.0.apk", True, f"""## 开屏卫士 v1.3.0 · 联网能力

新增：**问题反馈同步 GitHub Issues + 检查更新 GitHub Releases**（versionCode 10300）。

> v1.3.0 起申请 INTERNET 权限，但联网仅两处出口；学习数据仍全部保存在本机。

### 问题反馈（记录使用中遇到的问题）

- App 首页「问题反馈」卡片：选择类型（误拦截/漏拦截/误触观察有误/闪退卡顿/功能建议/其他）+ 描述
- 自动附带引擎画像（版本/规则数/最近日志，**不含任何个人数据与屏幕内容**）
- 「高级」中可配置个人 GitHub Token（仅存本机）→ 全自动创建 Issue；未配置则一键跳转浏览器预填页提交
- 浏览器预览模式（PWA/演示页）同样支持反馈与检查更新

### 检查更新（每次迭代快速下载）

- 「关于」页一键查询 GitHub Releases 最新版本 → 版本比对 → 有新版时提供 APK 直链下载
- 与历史版本同一签名（证书 SHA-256 `05ada6e3…`），下载后**直接安装即可覆盖升级，无需卸载**，学习规则与统计数据全部保留
- 本仓库每次迭代都会发布到 Releases，旧版 App 内即可完成升级

### 其他

- 新增 `Net.java` 零依赖 GitHub API 客户端与 JSBridge 联网方法
- `build.sh` 支持 CI 环境变量定位工具链；发版流水线见 `docs/github-actions/release.yml`（网页端一次性启用后，推 `v*` 标签自动构建发版）

{COMMON_INSTALL}"""),
]


def req(url, method="GET", data=None, ctype="application/json", token=TOKEN):
    r = urllib.request.Request(url, method=method)
    if token:
        r.add_header("Authorization", f"Bearer {token}")
    r.add_header("Accept", "application/vnd.github+json")
    r.add_header("User-Agent", "adshield-release-publisher")
    body = None
    if data is not None:
        if ctype == "application/json":
            body = json.dumps(data).encode()
        else:
            body = data
        r.add_header("Content-Type", ctype)
    with urllib.request.urlopen(r, body) as resp:
        raw = resp.read()
        return resp.status, json.loads(raw) if raw and ctype == "application/json" else raw


def get_ref(tag):
    try:
        s, d = req(f"{API}/git/ref/tags/{tag}")
        return d["object"]["sha"]
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


for tag, sha, apk, latest, notes in RELEASES:
    print(f"── {tag} ──")
    # 1) tag（已存在则复用）
    if get_ref(tag) is None:
        try:
            s, d = req(f"{API}/git/refs", "POST", {"ref": f"refs/tags/{tag}", "sha": sha})
            print(f"  tag → {sha[:7]} {'OK' if s == 201 else d}")
        except urllib.error.HTTPError as e:
            print(f"  tag 创建失败 {e.code}: {e.read().decode()[:200]}")
            continue
    else:
        print(f"  tag 已存在，复用")
    # 2) release
    try:
        s, d = req(f"{API}/releases/tags/{tag}")
        rel_id, existed = d["id"], True
        print(f"  release 已存在 #{rel_id}，补充说明")
        req(f"{API}/releases/{rel_id}", "PATCH", {"body": notes, "make_latest": "true" if latest else "false"})
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
        s, d = req(f"{API}/releases", "POST", {
            "tag_name": tag, "name": f"开屏卫士 {tag}", "body": notes,
            "draft": False, "prerelease": False,
            "make_latest": "true" if latest else "false",
        })
        rel_id, existed = d["id"], False
        print(f"  release #{rel_id} 创建 {'OK' if s == 201 else d}")
    # 3) 上传 APK（重名先删）
    path = f"/home/z/my-project/android-app/dist/{apk}"
    if not os.path.exists(path):
        print(f"  !! 缺少 {path}，跳过附件")
        continue
    s, d = req(f"{API}/releases/{rel_id}/assets")
    for a in (d if isinstance(d, list) else []):
        if a["name"] == apk:
            req(f"{API}/releases/assets/{a['id']}", "DELETE")
            print(f"  移除旧附件 {apk}")
    with open(path, "rb") as f:
        raw = f.read()
    s, r = req(f"{UPLOADS}/releases/{rel_id}/assets?name={apk}", "POST", raw,
               ctype="application/vnd.android.package-archive")
    print(f"  附件 {apk} ({len(raw)//1024}KB) 上传 OK")

print("\n全部完成")
