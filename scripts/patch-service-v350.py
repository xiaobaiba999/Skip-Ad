#!/usr/bin/env python3
"""AdSkipService v3.5.0 改造脚本：点击复验 + 位置学习 + 保活（精确文本替换，逐项断言）"""
import io, sys

P = "/home/z/my-project/android-app/src/com/zai/adshield/AdSkipService.java"
src = io.open(P, encoding="utf-8").read()

def rep(old, new, tag, count=1):
    global src
    n = src.count(old)
    if n != count:
        print(f"FAIL [{tag}] 出现 {n} 次（期望 {count}）"); sys.exit(1)
    src = src.replace(old, new)
    print(f"OK   [{tag}]")

# 1. 类头注释 v3.4.0 → v3.5.0
rep(" * 开屏广告智能跳过服务（无障碍服务实现 · v3.4.0）\n * ─────────────────────────────────────────────────────────────\n * v3.4.0 架构：识别/决策逻辑全部下沉到 AdDecisionCore（纯 Java，JVM 可单测，\n * scripts/device-tests 38 用例发版前必绿）；本类只负责：",
    " * 开屏广告智能跳过服务（无障碍服务实现 · v3.5.0）\n * ─────────────────────────────────────────────────────────────\n * v3.5.0（二次真机反馈驱动：日志显示「已点击跳过」但广告纹丝不动，手动点\n * 两次也没学下来——微博/喜马拉雅还是得手动关）：\n *  · 点击复验：点击后不再熔断扫描链——同窗 1.2s 后复扫，广告仍未关则换下\n *    一个角落候选点并强制 dispatchGesture 真实触点重试；同窗点击 ≥3 次\n *    仍未关 → 返回键兜底（「点了没反应」的最终兜底）。\n *  · 位置学习：手动点击图片跳过键（零文本）也能学——归一化坐标 + 窗口\n *    类名指纹，次启同窗 500ms 直点（教一次就会）。\n *  · 保活：前台常驻通知 + keepOn 持久化 + KeepAliveReceiver 断连提醒——\n *    开启后除非手动关闭不再静默失效（更新/重启后一键恢复）。\n *  · 架构：识别/决策逻辑全部在 AdDecisionCore（纯 Java，JVM 48 用例必绿）；\n *    本类只负责：", "类头注释")

rep(" *  · 事件调度（0ms 首扫 + 前密后疏重试链，命中即熔断）\n *  · 决策执行：performAction(ACTION_CLICK) 失效自动降级 dispatchGesture 手势点击\n *    （v3.4.0 关键：许多应用拦截 performAction，但拦不住真实触点）",
    " *  · 事件调度（0ms 首扫 + 前密后疏重试链；点击后转入复验调度，不熔断）\n *  · 决策执行：performAction(ACTION_CLICK) 失效自动降级 dispatchGesture 手势点击\n *    （v3.4.0 关键：许多应用拦截 performAction，但拦不住真实触点；\n *    v3.5.0 复验重试一律直接手势真实触点）", "调度注释")

# 2. 常量：keepOn + 通知 id
rep('    private static final String PREFS = "adshield";\n    private static final String KEY_STATE = "state";',
    '    private static final String PREFS = "adshield";\n    private static final String KEY_STATE = "state";\n    private static final String KEY_KEEPON = "keepOn";\n\n    private static final int NOTIF_KEEP = 1;   // 前台保活常驻通知\n    public static final int NOTIF_ALERT = 2;   // 服务断开提醒（KeepAliveReceiver 发，服务连上即撤）', "常量")

# 3. clickTries 字段
rep("    /* v3.4.0：坐标兜底每次启动一次（新窗口重置） */\n    private final java.util.HashMap<String, Boolean> coordTapUsed = new java.util.HashMap<>();",
    "    /* v3.4.0：坐标兜底每次启动一次（新窗口重置） */\n    private final java.util.HashMap<String, Boolean> coordTapUsed = new java.util.HashMap<>();\n    /* v3.5.0：点击复验——本窗已点击次数（换候选/真实触点重试/返回键兜底的依据） */\n    private final java.util.HashMap<String, Integer> clickTries = new java.util.HashMap<>();", "clickTries 字段")

# 4. onServiceConnected：keepOn + 前台通知
rep('        instance = this;\n        screenWidth = getResources().getDisplayMetrics().widthPixels;\n        screenHeight = getResources().getDisplayMetrics().heightPixels;\n        appendLog("服务已启动 · v3.4.0 角落控件点击 + 坐标级兜底 + 手势降级（决策核心 38 用例全绿）");',
    '        instance = this;\n        screenWidth = getResources().getDisplayMetrics().widthPixels;\n        screenHeight = getResources().getDisplayMetrics().heightPixels;\n        // v3.5.0 保活：服务连上即记「用户要求保持开启」；仅用户在 App 内手动\n        // 关闭保活提醒（Bridge.setKeepOn(false)）才置 false\n        prefs().edit().putBoolean(KEY_KEEPON, true).apply();\n        startKeepAliveForeground();\n        cancelAlertNotification();\n        appendLog("服务已启动 · v3.5.0 点击复验+位置学习+保活（决策核心 48 用例全绿）");', "onServiceConnected")

# 5. 窗口切换：复验通过日志 + clickTries 重置
rep("""                if (reboundChainJump(pkg, now)) break;
                if (reboundLanding(pkg, cls, now)) break;
                windowAt.put(pkg, now);
                winClass.put(pkg, cls);
                backTries.put(pkg, 0);
                coordTapUsed.put(pkg, Boolean.FALSE); // 新窗口重置坐标兜底""",
    """                if (reboundChainJump(pkg, now)) break;
                if (reboundLanding(pkg, cls, now)) break;
                // v3.5.0 复验通过：上一窗点过东西且窗口已切走 → 广告已关
                Integer prevTries = clickTries.get(pkg);
                if (prevTries != null && prevTries > 0 && !cls.equals(winClassOf(pkg))) {
                    appendLog("✓ 复验通过（" + shortPkg(pkg) + "）：广告窗已关闭"
                            + (prevTries > 1 ? "（共 " + prevTries + " 次尝试，重试梯子兜底）" : ""));
                }
                windowAt.put(pkg, now);
                winClass.put(pkg, cls);
                backTries.put(pkg, 0);
                clickTries.put(pkg, 0); // v3.5.0：新窗重置复验计数
                coordTapUsed.put(pkg, Boolean.FALSE); // 新窗口重置坐标兜底""", "窗口切换复验")

# 6. tryScan：ctx 新字段
rep("""            Long lastClick = lastClickAt.get(pkg);
            boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
            Boolean used = coordTapUsed.get(pkg);

            for (int ri = 0; ri < roots.length; ri++) {""",
    """            Long lastClick = lastClickAt.get(pkg);
            boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
            Boolean used = coordTapUsed.get(pkg);
            // v3.5.0：学习位置指纹（手动教过的坐标）与复验次数
            int[] tapHint = learnedPos(pkg);
            Integer triesBox = clickTries.get(pkg);
            int clickAttempts = triesBox == null ? 0 : triesBox.intValue();

            for (int ri = 0; ri < roots.length; ri++) {""", "tryScan ctx 前置")

rep("""                Boolean learned = winClsLearned.get(pkg);
                ctx.learnedCls = learned != null && learned.booleanValue();
                ctx.budgetNodes = splashLive ? 600 : 280; // 开屏窗预算扩容（重树）""",
    """                Boolean learned = winClsLearned.get(pkg);
                ctx.learnedCls = learned != null && learned.booleanValue();
                ctx.clickAttempts = clickAttempts;
                ctx.tapHintX = tapHint[0];
                ctx.tapHintY = tapHint[1];
                ctx.budgetNodes = splashLive ? 600 : 280; // 开屏窗预算扩容（重树）""", "tryScan ctx 字段")

io.open(P, "w", encoding="utf-8").write(src)
print("PHASE-1 DONE")
