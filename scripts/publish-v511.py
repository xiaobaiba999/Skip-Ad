#!/usr/bin/env python3
"""v5.1.1 · 创建 Tag + Release 并上传 APK + 回复关闭 issue#51-55（重复日志合并处理）。"""
import json
import os
import sys
import urllib.request

REPO = "xiaobaiba999/Skip-Ad"
API = f"https://api.github.com/repos/{REPO}"
UPLOADS = f"https://uploads.github.com/repos/{REPO}"

TOKEN = open("/home/z/skip-ad-repo/scripts/.github-token").read().strip()
if not TOKEN:
    sys.exit("缺少 token")


def call(url, data=None, method=None, ctype="application/json", token=TOKEN):
    req = urllib.request.Request(url, method=method or ("POST" if data is not None else "GET"))
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "skip-ad-instant-engine")
    if data is not None:
        req.add_header("Content-Type", ctype)
        data = data if isinstance(data, bytes) else json.dumps(data).encode()
    with urllib.request.urlopen(req, data) as r:
        body = r.read()
        return r.status, json.loads(body) if body else {}


TAG = "v5.1.1"
SHA = os.popen("git -C /home/z/skip-ad-repo rev-parse HEAD").read().strip()
APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v5.1.1.apk"

NOTES = """## 开屏卫士 v5.1.1 · 支付宝 XRiver 小程序容器保护

issue#51-55 日志实锤修复（versionCode 50101，同签名覆盖安装）：

- **根治**：支付宝新一代跨端容器 `XRiverActivity`（会员权益/生活号页面宿主）的胶囊「收藏到我的小程序 / 更多 / **关闭**」被开屏×关闭键路径代点 = 用户被踢出小程序（与 #45 抖音团购券同类事故）。现 `xriver` 标记入支付宝容器表 + 树标识兜底双增补（胶囊特征文案 + `xriver` vid），容器内绝不扫描绝不点击绝不学习
- 附带（v4.4.0 日志中的其余信号，v4.5.0~v5.1.1 已覆盖）：`NfcCodeRouterActivity`/`SchemeLauncherActivity` 等 1~5 节点瞬态窗不再刷未命中日志（v5.1.1 微树过滤 + 零候选退避）；`LivingImageDetailActivity` 内容页重复扫描由零候选退避收手；本日志 39318 事件/开机 的功耗画像主要由 v4.4.0 缺少「非前台内容事件一步丢弃」热路径导致（v4.5.0 已修）

**质量**：321 项 JVM 断言全绿（+7：XRiver 窗口识别/短类名/胶囊树标识/vid 兜底/端到端零动作——胶囊关闭键在树里但引擎恒零动作）。

### 安装 / 升级

| 操作 | 说明 |
| --- | --- |
| 新安装 | 下载 APK → 允许「安装未知应用」→ 安装 → 首页开启无障碍服务 |
| 覆盖升级 | 直接安装（同一 `adshield.keystore` 签名 + versionCode 递增），学习数据保留 |
| 正常卸载 | 系统设置 → 应用管理 → 开屏卫士 → 卸载 |
"""

REPLY = """## 日志已分析（v4.4.0 · 五条为同一份日志的重复上传，已合并处理）

感谢上传！这份日志暴露了一个**真 bug**，已随 [v5.1.1](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v5.1.1) 根治：

1. **支付宝里点「关闭」被代点（实锤：`已点击跳过（开屏×关闭键）` ×3 + `复验通过`）**——根因：支付宝新一代跨端容器 **XRiverActivity**（会员权益/生活号页面的宿主）不在小程序容器保护表里，它右上角胶囊的「收藏到我的小程序 / 更多 / **关闭**」中的「关闭」被「开屏×关闭键」路径当广告关闭键代点 = 你被踢出小程序。这与之前抖音团购券（#45）是同一类事故。修复：`xriver` 入支付宝容器表 + 胶囊特征文案「收藏到我的小程序」树标识兜底——容器内现在**绝不扫描绝不点击绝不学习**。321 项 JVM 断言全绿（新增 7 项 XRiver 专项：胶囊关闭键就在树里，引擎恒零动作）。

2. **本日志里的其他信号（均已覆盖，无需担心）**：`NfcCodeRouterActivity`（4~5 节点瞬态窗）/`SchemeLauncherActivity`（1 节点）反复记「开屏未命中」——v5.1.0/5.1.1 的微树过滤 + 零候选退避已收手；`LivingImageDetailActivity`（生活图片详情页，候选坐标 105%~193% 全在屏幕外）引擎本来就零动作 ✓；39318 事件/开机的功耗主要是 v4.4.0 缺「非前台内容事件一步丢弃」热路径（v4.5.0 已修）——建议直接升到 v5.1.1。

升级方式：[v5.1.1 APK 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.1.1/adshield-v5.1.1.apk)（同签名覆盖安装）。欢迎再传日志验证——XRiver 容器现在会打「🛡 小程序容器」让路日志。"""

# 1) tag + release
try:
    st, _ = call(f"{API}/git/tags", {"tag": TAG, "message": TAG, "object": SHA, "type": "commit"})
    print("tag:", st)
except Exception as e:
    print("tag (可能已存在):", e)

try:
    st, rel = call(f"{API}/releases", {
        "tag_name": TAG, "name": f"开屏卫士 {TAG} · 支付宝 XRiver 小程序容器保护",
        "body": NOTES, "draft": False, "prerelease": False,
    })
    print("release:", st, rel.get("id"))
except Exception as e:
    print("release (可能已存在):", e)
    sys.exit(0)

with open(APK, "rb") as f:
    st, _ = call(
        f"{UPLOADS}/releases/{rel['id']}/assets?name=adshield-v5.1.1.apk",
        f.read(), "POST", "application/vnd.android.package-archive")
    print("asset:", st)

# 2) reply + close #51-55
for n in [51, 52, 53, 54, 55]:
    try:
        st, _ = call(f"{API}/issues/{n}/comments", {"body": REPLY})
        print(f"comment #{n}:", st)
        st, _ = call(f"{API}/issues/{n}", {"state": "closed"}, "PATCH")
        print(f"close #{n}:", st)
    except Exception as e:
        print(f"#{n} error:", e)
