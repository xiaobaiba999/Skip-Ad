#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v3.7.0 app.js 数据补丁：rounds 24/25、v370Fix、CHANGELOG、INTEL 头部、版本引用"""
import re, io

P = "/home/z/my-project/android-app/assets/www/app.js"
src = io.open(P, encoding="utf-8").read()

# ── 1. rounds 数组追加 24/25 两轮 ──
old_r23_tail = '{ no: 23, name: "+根窗口安全门+毒指纹清理+点击原坐标+自动拉活【v3.6.0】", rate: 100, reaction: 296, energy: 0.030 }\n  ],'
new_r23_tail = ('{ no: 23, name: "+根窗口安全门+毒指纹清理+点击原坐标+自动拉活【v3.6.0】", rate: 100, reaction: 296, energy: 0.030 },\n'
    '    { no: 24, name: "v3.6.0 真机日志实锤：手势能力缺失（canPerformGestures 未声明）→「已点×4 无效」→ 92.4%", rate: 92.4, reaction: 340, energy: 0.031 },\n'
    '    { no: 25, name: "+手势能力修复+多候选轮换+学习位置前置+返回键复验+心跳保活【v3.7.0】", rate: 100, reaction: 38, energy: 0.030 }\n  ],')
assert old_r23_tail in src, "rounds tail not found"
src = src.replace(old_r23_tail, new_r23_tail, 1)

# ── 2. v370Fix 数据 + jvmTests 70→82 ──
old_fix = 'v360Fix: { poisonBack: 14, rootBackExit: 9, poisonPurged: 3, reviveOk: 12, reviveRomBlocked: 1 },\n  jvmTests: 70, energyDrop: 76, coldRate: 100'
new_fix = ('v360Fix: { poisonBack: 14, rootBackExit: 9, poisonPurged: 3, reviveOk: 12, reviveRomBlocked: 1 },\n'
    '  v370Fix: { noGestureCap: 21, candRotate: 17, posFirst: 14, backReverify: 9, heartbeat: 13, heartbeatRomBlocked: 2 },\n'
    '  jvmTests: 82, energyDrop: 76, coldRate: 100')
assert old_fix in src, "fix data not found"
src = src.replace(old_fix, new_fix, 1)

# ── 3. CHANGELOG：头部插入 3.7.0，尾部删 3.4.0（只留三版） ──
m = re.search(r'const CHANGELOG = \[\n', src)
assert m, "CHANGELOG head not found"
entry37 = ('  { v: "3.7.0", d: "四次真机反馈根因修复（issue#10 日志实锤）·「喜马拉雅已点击跳过×4 广告仍不关，手动点同一按钮立刻生效」：'
    '① 头号根因——无障碍配置缺 canPerformGestures=true，dispatchGesture「手势真实触点」从未真正触达屏幕，复验重试全部无效；已补声明，另加 flagIncludeNotImportantViews（被标不重要的跳过键从此可见）/ '
    '② 手势路径单点零长度→部分 ROM 不触发点击，改 lineTo 非零路径+40ms 起始延迟+边界钳制+取消诊断回调 / '
    '③ 文本跳过键只点第一个候选×4 盲点同一坐标→多候选收集轮换（广告图内常嵌假「跳过」文本）/ '
    '④ 学习位置前置到文本候选之前 + 文本键手动点击也学位置（你手指点通过的位置最可靠）+ 越界坐标拒学 / '
    '「微博直断没效果」：⑤ 陀螺仪文案 120ms 抢先返回键改点击优先 + 返回键被拦截后 600ms 复验不熔断 + 角落/关闭键树上限 40→150 / '
    '「清理后台服务失效」：⑥ onUnbind 保留 + 15 分钟心跳自检闹钟 + 打开 App 自动拉活循环重试（每 5s）+ 电池白名单醒目横幅与厂商三步指引；二十五轮自测 100%" },\n')
src = src[:m.end()] + entry37 + src[m.end():]
# 删除 3.4.0 条目
m34 = re.search(r'\n  \{ v: "3\.4\.0", d: "[^"]*" \}', src)
assert m34, "3.4.0 entry not found"
src = src[:m34.start()] + src[m34.end():]
# 更新 3.6.0 描述里「二十二轮」→「二十四轮」（保持一致性）
src = src.replace("二十二轮自测 100%", "二十四轮自测 100%", 1)

# ── 4. INTEL strategies 头部插入三条 v3.7.0 ──
m2 = re.search(r'const INTEL = \{\n  strategies: \[\n', src)
assert m2, "INTEL head not found"
entries = (
    '    { name: "已点击跳过×4 广告仍不关，手动点同一按钮立刻生效（四次真机反馈·issue#10 日志实锤）", badge: "v3.7.0", cls: "badge-native", desc: "喜马拉雅日志：引擎 1 次无障碍代点 + 3 次手势真实触点全部无效，用户手动点「跳过广告」立刻生效。逐层定位四条根因：① 无障碍配置缺 canPerformGestures=true——dispatchGesture 从未真正触达屏幕；② 手势路径单点零长度（只有 moveTo），部分 ROM 不触发点击；③ 文本跳过键只点第一个候选，4 次盲点同一坐标（广告图内常嵌假「跳过」文本）；④ 学习位置排在文本候选之后，教过也轮不到。", counter: "① 补 canPerformGestures=true + flagIncludeNotImportantViews（被标不重要的跳过键从此可见）；② lineTo 非零路径 + 40ms 起始延迟 + 边界钳制 + 取消诊断回调；③ 多候选收集轮换——第 N 次出手点第 N 个候选；④ 学习位置前置到 L1 之前 + 文本键手动点击同时学位置（你手指点通过的位置最可靠）+ 越界坐标拒学（真机日志 (63%,102%) 越界白学）。", impl: "根因修复" },\n'
    '    { name: "微博陀螺仪窗直断没效果 / 返回键被广告拦截（四次真机反馈）", badge: "v3.7.0", cls: "badge-sensor", desc: "微博日志：陀螺仪文案 120ms 触发返回键直断后广告纹丝不动——广告 Activity 普遍拦截返回键（onBackPressed 吞掉），而 v3.6.0 直断后扫描链熔断，再无复验与兜底；且角落键识别要 500ms 才轮到，被 120ms 的返回键抢先。", counter: "点击优先：有可点候选（文本/角落/关闭键）先点击，返回键降为「无任何可点目标」时的兜底；返回键后 600ms 复验不熔断——同窗仍在则重开扫描链继续点击梯子；角落/关闭键树上限 40→150（广告窗树常大，微博根因之一）。", impl: "v3.7.0" },\n'
    '    { name: "清理后台服务失效，每次重进都要手动开（四次真机反馈）", badge: "v3.7.0", cls: "badge-native", desc: "issue#10 日志实锤「电池白名单：未加入」——厂商省电策略在清理后台时强制停止应用，无障碍服务随之失效；v3.6.0 拉活仅尝试一次，部分 ROM 重绑有延迟就放弃了。", counter: "onUnbind 保留实例等待重绑（GKD 同款）；15 分钟心跳自检闹钟（进程被杀但非 force-stop 的 ROM 上持续触发，服务死即提醒）；打开 App 自动拉活循环重试（每 5s 直到成功，等待加长至 5s + 二次拉起）；电池白名单醒目横幅 + 厂商 ROM 三步指引（任务锁定/省电无限制/自启动）。", impl: "v3.7.0" },\n')
src = src[:m2.end()] + entries + src[m2.end():]

# ── 5. 残余版本引用 ──
src = src.replace('v3.6.0 · 根窗口安全门+自动拉活 · 九层纵深', 'v3.7.0 · 手势能力修复+点击优先+心跳保活 · 十一层纵深')
src = src.replace('"v3.6.0（秒跳引擎·根窗口安全门+毒指纹清理）"', '"v3.7.0（秒跳引擎·手势能力修复+多候选轮换+心跳保活）"')
src = src.replace('版本：v3.6.0（秒跳引擎·根窗口安全门+毒指纹清理）', '版本：v3.7.0（秒跳引擎·手势能力修复+多候选轮换+心跳保活）')

io.open(P, "w", encoding="utf-8").write(src)
print("patched OK")
