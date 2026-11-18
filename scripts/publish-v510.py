#!/usr/bin/env python3
"""v5.1.0 · 创建 Tag + Release 并上传 APK（xiaobaiba999/Skip-Ad）。"""
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


TAG = "v5.1.0"
SHA = os.popen("git -C /home/z/skip-ad-repo rev-parse HEAD").read().strip()
APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v5.1.0.apk"

NOTES = """## 开屏卫士 v5.1.0 · 非主流 App 开屏攻坚

issue#46/47 日志逐帧复刻修复（versionCode 50100，同签名覆盖安装）：

- **美团开屏广告窗根治**：`DspActivity` 开屏 DSP 独立窗 / `DynamicDialog$c` 动态广告弹窗 / 第三方自建 `AdsActivity` 此前整窗无指纹（close 键不敢点、返回键不敢按）。SDK 窗口指纹三连扩展 + 美团系专项订阅规则（窗口限定下放行 close 族 vid；`$c` 内部类窗口名按基类名匹配；可滚动运营弹窗绝不返回键）
- **零候选退避**：校园/工具类 App 校徽启动页（school.app 9 秒 7 条未命中）与商店首页不再被反复整树扫描（3 次/2.6s、微树 2 次/1.2s 收手；扫码键等角落控件须过广告形态门；晚到跳过键救回通道保留）
- **跳过后静默期**（复验通过 2.2s 内不记首页落定噪声）+ 未命中日志微树过滤（≤5 节点占位窗不刷屏）
- **内置规则库 54 → 102 款**：银行 9 / 出行 7（12306 等）/ 阅读 6 / 新闻 4 / 工具 7 / 生活 7 / 学习办公 4 / 音乐社交 6（包名精确匹配，零误点面）
- **安全性同步提升**：诱导词黑名单扩容（抢购/秒杀/礼包/优惠等——「跳过领礼包」类诱饵绝不代点）；**支付/资金页零扫描**（支付密码/付款码/收银台/刷脸支付标记）

**质量**：314 项 JVM 断言全绿（新增非主流 App 模拟套件 130 项：4 档机型 × 六类日志实锤形态——美团 Dsp / DynamicDialog 双变体 / 校徽页 135 节点 / 商店首页 145 节点 / www AdsActivity / 外卖渐进加载）。

### 安装 / 升级

| 操作 | 说明 |
| --- | --- |
| 新安装 | 下载 APK → 允许「安装未知应用」→ 安装 → 首页开启无障碍服务 |
| 覆盖升级 | 直接安装（同一 `adshield.keystore` 签名 + versionCode 递增），学习数据保留 |
| 正常卸载 | 系统设置 → 应用管理 → 开屏卫士 → 卸载 |
"""

# 1) tag
try:
    st, _ = call(f"{API}/git/tags", {"tag": TAG, "message": TAG, "object": SHA, "type": "commit"})
    print("tag:", st)
except Exception as e:
    print("tag (可能已存在):", e)

# 2) release
try:
    st, rel = call(f"{API}/releases", {
        "tag_name": TAG, "name": f"开屏卫士 {TAG} · 非主流 App 开屏攻坚",
        "body": NOTES, "draft": False, "prerelease": False,
    })
    print("release:", st, rel.get("id"))
except Exception as e:
    print("release (可能已存在):", e)
    sys.exit(0)

# 3) upload apk
with open(APK, "rb") as f:
    st, _ = call(
        f"{UPLOADS}/releases/{rel['id']}/assets?name=adshield-v5.1.0.apk",
        f.read(), "POST", "application/vnd.android.package-archive")
    print("asset:", st)
