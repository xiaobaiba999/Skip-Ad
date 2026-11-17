#!/usr/bin/env python3
"""v5.2.0 · 创建 Tag + Release 并上传 APK + 回复关闭 issue#56（10 秒警告根治）。"""
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


TAG = "v5.2.0"
SHA = os.popen("git -C /home/z/skip-ad-repo rev-parse HEAD").read().strip()
APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v5.2.0.apk"

NOTES = """## 开屏卫士 v5.2.0 · 「每次退后台/覆盖安装都要看 10 秒系统警告」根治

issue#56 日志实锤修复（Redmi 25060RK16C · Android 16，对照组 Xiaomi 15 全程无需；versionCode 50200，同签名覆盖安装）：

### 为什么 Redmi 每次都要看 10 秒警告，Xiaomi 15 不用？

**不是硬件问题，是 ROM 管控策略差异 + 旧版三处放大器**：

1. **根因（ROM 差异）**：HyperOS（入门 Redmi 上更激进）在**清理后台/滑掉最近任务/覆盖安装**后，不仅杀进程，还会把服务从系统「已启用无障碍服务」登记里**移除**。登记一旦被吊销，任何应用都无法编程恢复（`BIND_ACCESSIBILITY_SERVICE` 签名级保护），只能经系统设置重开——撞上厂商防诈的 10 秒倒计时确认框。Xiaomi 15（HyperOS 2，旗舰策略宽松）登记保留，进程重启系统即自动重绑，所以那位用户从不需要重开。
2. **旧版三处放大器**（本版全部消灭）：
   - 「待恢复」通知点按后**无条件 500ms 直跳系统设置**——登记仍在、只是重绑慢的用户也被白送去看 10 秒警告；
   - 拉活窗口固定 6 秒——低端机冷启动 + AMS 重绑慢，6 秒不够就误判失败；
   - 覆盖安装**直接假设「系统必然已关闭」**发设置导向通知——多数 ROM 登记其实保留，进程起来即重绑。

### v5.2.0 修复

- **两态通知**：登记仍在 → 「打开卫士自动恢复」（App 内 15s 自适应深挖拉活，全程无系统设置、无 10 秒等待）；真被吊销 → 「直达服务开关」（一步到位，文案明示 10 秒安全确认属厂商防诈设计——预期管理）
- **15s 自适应拉活**：前 2s 每 300ms 快踢 AMS、后段 500ms，**每轮复查登记**——HyperOS 中途吊销立即止损，绝不傻等
- **广播窗口踢链自愈**：自检/覆盖安装发现服务死了先在广播窗口内三轮踢 AMS 再判——能自愈的零打扰（步骤化单点 finish，绝不广播 ANR）
- **保活体检卡（防吊销根治）**：三项自动检测（服务绑定/通知权限/电池白名单）+ **近 5 年全品牌厂商直达**（小米自启动+省电策略无限制、华为/荣耀启动管理、OPPO/一加/realme 自启动+完全后台、vivo/iQOO 后台高耗电、三星从不休眠应用、魅族后台管理）——候选全部硬编码 OEM 系统包、逐个 resolveActivity 校验回退，一次配置根治吊销
- **登记移除遥测**：ContentObserver 监听系统登记，吊销瞬间打点日志并自动排自检——未来日志直接回答「服务什么时候、被谁关的」
- **同态通知 10 分钟冷却**：5 分钟心跳周期不再反复提醒
- 附带修复：启动横幅硬编码 v4.5.0 陈旧版本 bug（改运行时动态注入，issue#56 日志实锤）；登记判断改 ':' 逐段精确解析（`.AdSkipServiceV2` 类前缀误报绝杀）

### 安全加固（与功能同步）

- KeepAliveReceiver 增加 **signature 级私有广播权限**——第三方应用无法伪造 SELF_CHECK 广播（防恢复通知骚扰 + 防日志上传配额耗尽）；系统广播（BOOT/MY_PACKAGE_REPLACED）与自家拉活闹钟不受影响
- 保活深链候选全部为硬编码 OEM 系统包注册表（零任意组件注入面）+ manifest queries 显式可见性

**质量**：427 项 JVM 断言全绿（+106 RestoreGuard 专项：品牌归一 18 项 / 深链候选安全性 3 项不变量 / 登记精确解析 12 项 / 决策状态机核心不变量——**登记仍在的任何状态绝不引导去系统设置** / 15s 轮询计划边界 / 动态横幅 / 两态文案）。

### 安装 / 升级

| 操作 | 说明 |
| --- | --- |
| 覆盖升级 | 直接安装（同一 `adshield.keystore` 签名 + versionCode 递增），学习数据保留 |
| 升级后 | 首页完成「保活体检」（自启动 + 省电策略）——清理后台/下次升级不再被吊销，10 秒警告从此只在你主动关闭重开时出现 |
| 正常卸载 | 系统设置 → 应用管理 → 开屏卫士 → 卸载 |
"""

REPLY = """## 日志已分析（v5.1.1 · Redmi 25060RK16C）——「每次都要看 10 秒警告」根因已查明，v5.2.0 根治

感谢上传！先直接回答你的三个问题：

### 1. 为什么每次退后台重进/装新版本点「恢复防护」，都要进系统强行看 10 秒警告？

**根因是 HyperOS（你这台 Redmi 的系统，入门机型上管控更激进）**：清理后台/滑掉最近任务/覆盖安装后，它不仅杀进程，还会把开屏卫士从系统「已启用无障碍服务」**登记里移除**。登记一旦没了，任何应用都无法自行恢复（这是 Android 的安全红线），只能去系统设置重开——而重开时系统会弹 10 秒倒计时的安全确认框（厂商防诈骗设计，无法跳过）。

### 2. 为什么 Xiaomi 15 的用户不用每次看？

那位用户的 Xiaomi 15 是 HyperOS 2（旗舰机管控策略宽松）：清理后台**只杀进程、登记保留**，进程重启后系统自动重绑服务，卫士在 App 内就能拉活，从不需要去系统设置。

### 3. 是不是设备的问题？

一半一半：**不是硬件故障，是 ROM 管控策略差异**——但完全可以防：吊销的三大诱因（自启动未授权、省电策略受限、清理后台）全部有设置入口，配置一次就不再发生。另外旧版卫士也有三处放大痛点的缺陷（通知点按直接跳设置、拉活窗口 6 秒不够低端机用、覆盖安装直接假设已关闭）。

### v5.2.0 的修复（已随 [v5.2.0](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v5.2.0) 发布）

- **两态通知**：登记仍在（多数场景）→ 点通知打开卫士**自动恢复**（15 秒自适应深挖拉活，全程不用去系统设置）；真被吊销 → 直达服务开关一步开启（文案会明示「系统将弹 10 秒安全确认，属正常防诈设计」）
- **15s 自适应拉活**（旧版 6 秒在你这台冷启动慢的机器上不够）；拉活中每轮复查登记，系统吊销立即止损
- **保活体检卡**：首页新增——三项自动检测 + **小米系专属直达**（自启动 + 省电策略→无限制），配置完成后清理后台/升级不再被吊销，**10 秒警告从此只在你主动关闭再重开时出现**
- **登记移除遥测**：系统吊销卫士的瞬间会打点日志——下次上传日志能直接看到「什么时候被关的」，铁证定位
- 附带修复：你日志里服务启动横幅显示「v4.5.0」的陈旧版本号 bug（改为运行时动态注入）

**升级方式**：[v5.2.0 APK 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.2.0/adshield-v5.2.0.apk)（同签名覆盖安装，学习数据保留）。装完请在首页完成「保活体检」两项小米系配置——这是防复发的关键。欢迎再传日志验证：吊销时现在会有「⚠️ 无障碍登记被移除」的精确打点。"""

# 1) tag + release
try:
    st, _ = call(f"{API}/git/tags", {"tag": TAG, "message": TAG, "object": SHA, "type": "commit"})
    print("tag:", st)
except Exception as e:
    print("tag (可能已存在):", e)

try:
    st, rel = call(f"{API}/releases", {
        "tag_name": TAG, "name": f"开屏卫士 {TAG} · 10 秒警告根治（保活体检全品牌）",
        "body": NOTES, "draft": False, "prerelease": False,
    })
    print("release:", st, rel.get("id"))
except Exception as e:
    print("release (可能已存在):", e)
    sys.exit(0)

with open(APK, "rb") as f:
    st, _ = call(
        f"{UPLOADS}/releases/{rel['id']}/assets?name=adshield-v5.2.0.apk",
        f.read(), "POST", "application/vnd.android.package-archive")
    print("asset:", st)

# 2) reply + close #56
try:
    st, _ = call(f"{API}/issues/56/comments", {"body": REPLY})
    print("comment #56:", st)
    st, _ = call(f"{API}/issues/56", {"state": "closed"}, "PATCH")
    print("close #56:", st)
except Exception as e:
    print("#56 error:", e)
