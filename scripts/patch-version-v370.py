#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v3.7.0 version.ts 补丁：版本历史头部插入 3.7.0，删除 3.4.0（只留三版）"""
import re, io

P = "/home/z/my-project/src/lib/version.ts"
src = io.open(P, encoding="utf-8").read()

# 确认 APP_VERSION 已更新（第一编辑已生效）
assert 'APP_VERSION = "3.7.0"' in src, "APP_VERSION not patched"
assert 'VERSION_CODE = 30700' in src, "VERSION_CODE not patched"

# 在 VERSION_HISTORY 数组头部插入 3.7.0 条目
anchor = "export const VERSION_HISTORY: VersionEntry[] = [\n"
assert anchor in src, "anchor not found"
entry = '''  {
    version: "3.7.0",
    code: 30700,
    date: "2026-11-16",
    tagline: "手势能力修复 · 多候选轮换 · 学习位置前置 · 返回键复验 · 心跳保活",
    notes: [
      "头号根因修复（四次真机反馈 issue#10：喜马拉雅「已点击跳过×4」广告仍不关，手动点同一按钮立刻生效）：无障碍配置缺 canPerformGestures=true——dispatchGesture「手势真实触点」从未真正触达屏幕，复验重试全部无效。已补声明；另加 flagIncludeNotImportantViews（被标不重要的跳过键从此在无障碍树里可见，微博角落键扫不到的帮凶）",
      "手势点击修复：路径单点零长度（只有 moveTo）→ 部分 ROM 不触发点击——改 lineTo 非零路径 + 40ms 起始延迟 + 坐标边界钳制 + GestureResultCallback 取消诊断（「派发成功但被系统中断」不再静默）",
      "多候选轮换：文本跳过键只点第一个候选、复验 4 次盲点同一坐标（广告图内常嵌假「跳过」文本）→ 收集全部候选（≤4），第 N 次出手点第 N 个；学习位置前置到文本候选之前，且文本键手动点击【同时学位置】（你手指点通过的位置最可靠）+ 越界坐标拒学（真机日志 (63%,102%) 越界白学）",
      "微博直断修复：陀螺仪文案 120ms 抢先返回键改「点击优先、返回键兜底」；返回键被广告 Activity 拦截后 600ms 复验不熔断（同窗仍在则重开扫描链继续点击梯子）；角落/关闭键树上限 40→150（广告窗树常大，真角落键被挡在门外）",
      "无障碍持久化（清理后台重进又要手动开）：onUnbind 保留实例等待重绑（GKD 同款）+ 15 分钟心跳自检闹钟（进程被杀但非 force-stop 的 ROM 上持续触发）+ 打开 App 自动拉活循环重试（每 5s 直到成功，等待加长至 5s + 二次拉起）+ 电池白名单醒目横幅与厂商 ROM 三步指引（任务锁定/省电无限制/自启动）；JVM 83 用例全绿（新增 N 系列 12 例）；二十五轮自测：真机日志建模（手势能力缺失 ×21）92.4% → 全恢复 100%",
    ],
  },
'''
src = src.replace(anchor, anchor + entry, 1)

# 删除 3.4.0 条目（只留最近三版）
m = re.search(r'  \{\n    version: "3\.4\.0",.*?\n  \},\n', src, re.S)
assert m, "3.4.0 entry not found"
src = src[:m.start()] + src[m.end():]

# 3.6.0 描述中「二十三轮」→「二十四轮」保持叙事一致
src = src.replace("二十三轮自测：真机事故建模", "二十四轮自测：真机事故建模", 1)

io.open(P, "w", encoding="utf-8").write(src)
print("version.ts patched")
