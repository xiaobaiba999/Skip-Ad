#!/usr/bin/env python3
"""APK app.js v3.5.0 数据更新：CHANGELOG / TEST 轮次 / 版本默认值 / 策略条目"""
import io, sys

J = "/home/z/my-project/android-app/assets/www/app.js"
j = io.open(J, encoding="utf-8").read()

def rep(old, new, tag, count=1):
    global j
    n = j.count(old)
    if n != count:
        print(f"FAIL [{tag}] 出现 {n} 次（期望 {count}）"); sys.exit(1)
    print(f"OK   [{tag}]")
    j = j.replace(old, new)

# 1. 文件头版本注释
rep('/* 开屏卫士 v3.4.0 · 离线逻辑（首页 / 情报站 / 学习中心 / 关于 · 应用内页内直更 + 多通道下载） */',
    '/* 开屏卫士 v3.5.0 · 离线逻辑（首页 / 情报站 / 学习中心 / 关于 · 应用内页内直更 + 多通道下载） */',
    "文件头")

# 2. TEST 数据：v3.5.0 轮次 + 指标
rep('''const TEST = {
  total: 1060, real: 130, generated: 930,
  v320Rate: 82.45, v330Rate: 95.09,
  adversarial: { fakeSkip: 107, chainJump: 89, gyro: 63, popup: 106 },
  v330: { sdkAd: 518, textlessGyro: 30, overlayPopup: 50, latePopup: 90 },
  v340: { imageSkip: 53, imageCorner: 31, cornerHit: 31, coordHit: 22 },''',
    '''const TEST = {
  total: 1060, real: 130, generated: 930,
  v320Rate: 82.45, v330Rate: 95.09, v340Rate: 100,
  adversarial: { fakeSkip: 107, chainJump: 89, gyro: 63, popup: 106 },
  v330: { sdkAd: 518, textlessGyro: 30, overlayPopup: 50, latePopup: 90 },
  v340: { imageSkip: 53, imageCorner: 31, cornerHit: 31, coordHit: 22 },
  v350: { swallowed: 19, retryHit: 17, backHit: 2, posLearn: 19, posHit: 19 },''',
    "TEST v350")

rep('''    { no: 18, name: "全量回归 ×2（双跑一致）", rate: 100, reaction: 34, energy: 0.030 }
  ],''',
    '''    { no: 18, name: "全量回归 ×2（双跑一致）", rate: 100, reaction: 34, energy: 0.030 },
    { no: 19, name: "v3.4.0 真机复测建模：点击被吞（日志已点·广告未关）×19", rate: 98.21, reaction: 310, energy: 0.031 },
    { no: 20, name: "+点击复验换候选+真实触点重试+返回键兜底【v3.5.0】", rate: 100, reaction: 302, energy: 0.031 },
    { no: 21, name: "+位置学习（手动教一次就会）+ 保活回归", rate: 100, reaction: 41, energy: 0.030 }
  ],''',
    "TEST 轮次19-21")

rep('''  ximalaya: { r15VisibleMs: 900, cornerMs: 659, flipTrigMs: 900, steadyMs: 34 },
  jvmTests: 38, energyDrop: 76, coldRate: 100
};''',
    '''  ximalaya: { r15VisibleMs: 900, cornerMs: 659, flipTrigMs: 900, steadyMs: 34 },
  v350Verify: { swallowed: 19, retrySaved: 17, backSaved: 2, posLearned: 19 },
  jvmTests: 49, energyDrop: 76, coldRate: 100
};''',
    "TEST jvm49")

# 3. CHANGELOG：只保留最近三版（3.5.0 / 3.4.0 / 3.3.0）
rep('''const CHANGELOG = [
  { v: "3.4.0", d: "根因修复·微博/喜马拉雅等第一方图片开屏（窗口类名非 SDK、跳过键纯图片零文本、倒计时烧在图里——v3.3.0 零信号）/ 角落无字小可点控件识别 → dispatchGesture 手势点击其中心 650ms（图片跳过键正解）/ 坐标级点击兜底：开屏窗内零信号简单树 → 点标准跳过位（88%宽/7%高），防误点六重约束 / performAction 失效自动降级手势点击（真实触点应用拦不住）/ 多窗口扫描 + 开屏窗预算 280→600 / 决策核心 AdDecisionCore 纯 Java 可单测：JVM 38 用例发版前必绿（微博 3 + 喜马拉雅 4 + 误报防护 12 场景）/ 十八轮自测：v3.3.0 终态新建模下 95.09%（图片开屏 ×53 全漏）→ 100%；喜马拉雅 v4 角落键 659ms 抢在翻转 900ms 前" },''',
    '''const CHANGELOG = [
  { v: "3.5.0", d: "二次真机反馈根因修复·「日志显示已点击跳过但广告纹丝不动」：v3.4.0 点完即熔断、从不复验 → 点击复验梯子（1.2s 后复扫，未关换下一个角落候选点 + 强制手势真实触点重试；≥3 次未关返回键兜底）/ 手动点两次也学不下来的根因：图片跳过键零文本，v3.4.0 学习只认文本 → 位置学习（记住你点过的坐标+窗口指纹，次启同窗 500ms 直点，教一次就会；仅广告形态窗内出手，信息流绝不误点）/ 开屏×关闭键代点（发现了却从不点的遗漏）/ 服务保活：前台常驻通知 + 更新/重启断连即通知一键恢复（Android 安全设计无法自动重开，已做到系统允许的极限）+ 电池优化白名单一键加入 / 学习中心新增「上传引擎日志到 GitHub」：60 条日志+学习规则+设备信息一键直达 Issues（内置加密通道），成为下版迭代依据 / 全新绿色盾牌+闪电 Logo / JVM 49 用例全绿；二十一轮自测：真机复测建模点击被吞 ×19 → 复验重试 ×17 + 返回键兜底 ×2 全恢复 → 100%" },''',
    "CHANGELOG 3.5.0")

rep('''  { v: "3.2.0", d: "陀螺仪诱导直断 / 链式跳转回弹 / 弹窗广告屏蔽 v1 / 极速链 0ms 首扫（因发版脚本仓库名拼写错误未发布到 GitHub，能力已并入后续版本）" }
];''',
    ''';''',
    "CHANGELOG 删 3.2.0")

# 4. 版本默认值
rep('function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "3.4.0"; }',
    'function appVer() { if (HAS_BRIDGE) { try { return AndroidApp.versionName(); } catch (e) {} } return "3.5.0"; }',
    "appVer 默认")
rep('function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "30400"; }',
    'function appCode() { if (HAS_BRIDGE) { try { return String(AndroidApp.versionCode()); } catch (e) {} } return "30500"; }',
    "appCode 默认")

# 5. 情报站边界文案：v3.5.0 复盘
rep('''  $("#intel-boundary").innerHTML =
    `十八轮自测自改自修复（诚实重构模型）：73.21% → 95.09%（v3.3.0）→ 100%（v3.4.0）；冷启动 100%，双跑一致。` +''',
    '''  $("#intel-boundary").innerHTML =
    `二十一轮自测自改自修复（诚实重构模型）：73.21% → 95.09%（v3.3.0）→ 100%（v3.4.0）→ 真机复测建模点击被吞 ×19 跌至 98.21% → v3.5.0 复验重试全恢复 100%；冷启动 100%，双跑一致。` +''',
    "情报站边界")

rep('''    `SDK 窗 ×${TEST.v330.sdkAd} 首次可判。决策核心 JVM ${TEST.jvmTests} 用例全绿（含 12 个误报防护场景）。功耗：日均 -${TEST.energyDrop}%。`;''',
    '''    `SDK 窗 ×${TEST.v330.sdkAd} 首次可判。v3.5.0 真机复测：点击被吞 ×${TEST.v350.swallowed}（日志已点·广告未关）→ 复验换候选+真实触点重试 ×${TEST.v350.retryHit} + 返回键兜底 ×${TEST.v350.backHit} 全恢复；手动位置学习 ×${TEST.v350.posLearn} 条全部次启直点命中。决策核心 JVM ${TEST.jvmTests} 用例全绿（含 13 个误报防护场景）。功耗：日均 -${TEST.energyDrop}%。`;''',
    "情报站边界2")

# 6. 策略列表：新增「点击被吞·复验重试」条目（放最前）
rep('''const INTEL = {
  strategies: [
    { name: "第一方图片开屏（微博/喜马拉雅实测）", badge: "v3.4.0", cls: "badge-sensor",''',
    '''const INTEL = {
  strategies: [
    { name: "点击被吞：日志显示已点、广告没关（二次真机反馈）", badge: "v3.5.0", cls: "badge-sensor", desc: "v3.4.0 点击后立即熔断扫描链、从不复验：performAction 返回 true 但被应用无声吞掉、手势点偏、按钮尚未可点——日志写「已点击跳过」，广告却纹丝不动，且再无第二次机会。", counter: "点击复验梯子：点后 1.2s 复扫同窗，未关 → 换下一个角落候选点 + 强制 dispatchGesture 真实触点重试；≥3 次未关 → 返回键兜底温和关闭。窗口切走即记「复验通过」。真机复测建模 ×19 全恢复（重试 ×17 + 兜底 ×2）。", impl: "根因修复" },
    { name: "图片跳过键学不下来：手动点两次也没用（二次真机反馈）", badge: "v3.5.0", cls: "badge-splash", desc: "图片跳过键零文本零 contentDescription——v3.4.0 手动点击学习只认「跳过/关闭」文本，图片键永远学不到，「手动记录两次还是不能跳过」。", counter: "位置学习：手动点小角落/顶部控件（零文本）即记归一化坐标 + 窗口类名指纹；次启同窗 500ms 手势直点该位置（教一次就会）。安全门：仅广告形态窗内出手（信息流/输入框/长文本页绝不点）；点后照常复验，失败升级候选/返回键。", impl: "根因修复" },
    { name: "服务保活：更新/重启后被系统关闭", badge: "v3.5.0", cls: "badge-native", desc: "覆盖安装新版本、系统更新或重启后，Android 会自动关闭无障碍服务——系统安全设计，任何应用（含 GKD 同类工具）都无法绕过自动重开；后台清理与厂商省电策略也会杀服务。", counter: "系统允许的极限：前台常驻通知（IMPORTANCE_MIN，降低被杀概率）+ keepOn 持久化 + 覆盖安装/重启后 KeepAliveReceiver 检测断连 → 高优先级通知一键恢复（点按自动跳无障碍设置，两步恢复，学习数据全保留）+ 电池优化白名单一键加入。", impl: "v3.5.0" },
    { name: "第一方图片开屏（微博/喜马拉雅实测）", badge: "v3.4.0", cls: "badge-sensor",''',
    "策略条目 v3.5.0")

# 7. 首页状态卡副标题更新
rep('''  $("#service-desc").textContent = running
    ? "SDK 广告窗识别 · 热区直断 · 弹窗 v2 · 回弹扩充 · 官方与侧载一视同仁"
    : (HAS_BRIDGE ? "开启无障碍后自动秒跳任何应用开屏" : "安装 APK 后体验真实秒跳服务");''',
    '''  $("#service-desc").textContent = running
    ? "点击复验 · 位置学习 · SDK 窗指纹 · 弹窗 v2 · 回弹扩充 · 官方与侧载一视同仁"
    : (HAS_BRIDGE ? "开启无障碍后自动秒跳任何应用开屏" : "安装 APK 后体验真实秒跳服务");''',
    "首页副标题")

io.open(J, "w", encoding="utf-8").write(j)
print("APP.JS DATA DONE")
