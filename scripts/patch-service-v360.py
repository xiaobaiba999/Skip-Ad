# -*- coding: utf-8 -*-
"""AdSkipService.java v3.6.0 补丁（带断言：任何锚点未命中即失败退出，不写半成品）"""
import sys

P = "/home/z/my-project/android-app/src/com/zai/adshield/AdSkipService.java"
src = open(P, encoding="utf-8").read()
orig = src

def rep(old, new, cnt=1):
    global src
    n = src.count(old)
    if n != cnt:
        print(f"[FAIL] 锚点命中 {n} 次（期望 {cnt}）：{old[:70]!r}...")
        sys.exit(1)
    src = src.replace(old, new)

# ── 1. 头部版本注释 ──
rep(""" * v3.5.0（二次真机反馈驱动：日志显示「已点击跳过」但广告纹丝不动，手动点
 * 两次也没学下来——微博/喜马拉雅还是得手动关）：
 *  · 点击复验：点击后不再熔断扫描链——同窗 1.2s 后复扫，广告仍未关则换下""",
""" * v3.5.0（二次真机反馈驱动）：点击复验梯子 + 位置学习 + 保活三件套。
 * v3.6.0（三次真机反馈：喜马拉雅二次进入被引擎连按两次返回键「模拟人
 * 操作退出」到桌面，上传日志实锤两条毒链）：
 *  · 毒链① 窗口指纹污染：v3.5.0 的 learnClass 在 L2 直断时把【当时窗口】
 *    类名一律沉淀；且广告窗关闭后 SDK 资格 12s 不清，L2 在主页继续出手
 *    → MainActivity/FrameLayout/Dialog 全被学成「广告窗指纹」，此后每次
 *    进入该应用 500ms 内就在主页按返回键 → 喜马拉雅拦返回弹「再按一次
 *    退出」→ 复验扫描再按 → 真退到桌面（用户被「模拟人操作退出」）。
 *    对策：sdkAdWin 只认【当前窗口类名】是 SDK 广告窗（12s TTL 废除）；
 *    learnClass 只沉淀真广告窗类；启动时清理历史毒指纹（只清 cls 规则）。
 *  · 毒链② 根窗口返回键：应用入口窗（冷启动开屏/回前台首窗）按返回
 *    = 退出应用。rootWindow 接线（前台入口窗追踪）：入口窗上 L3/L4.5/L5
 *    返回键全禁（决策核心安全门 + 服务侧双保险），只走点击梯子，用尽
 *    优雅收手（每窗 6 次点击预算）。
 *  · 手势点击改点【原标签控件】坐标（v3.5.0 点可点祖先中心——祖先常是
 *    全屏容器，「已点击跳过但广告纹丝不动」根因之一）。
 *  · 保活新增：onTaskRemoved 即时提醒 + 自检闹钟 + 打开 App 自动拉活重绑。""")

rep(""" *  · 架构：识别/决策逻辑全部在 AdDecisionCore（纯 Java，JVM 48 用例必绿）；""",
""" *  · 架构：识别/决策逻辑全部在 AdDecisionCore（纯 Java，JVM 用例必绿）；""")

# ── 2. 服务实现版本号（已先行应用，跳过） ──

# ── 3. 字段：去掉 adWinUntil / coordTapUsed，加 rootWinCls / gaveUp / lastFgPkg ──
rep("""    private final java.util.HashMap<String, String> winClass = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> adWinUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Boolean> winClsLearned = new java.util.HashMap<>();""",
"""    private final java.util.HashMap<String, String> winClass = new java.util.HashMap<>();
    private final java.util.HashMap<String, Boolean> winClsLearned = new java.util.HashMap<>();
    /* v3.6.0 根窗口安全门：应用入口窗（冷启动开屏/回前台首窗）类名——
     * 入口窗上按返回键 = 退出应用，返回键决策全部禁用 */
    private final java.util.HashMap<String, String> rootWinCls = new java.util.HashMap<>();
    /* v3.6.0：前台包名追踪（判断应用是否【新进入前台】→ 记录入口窗） */
    private volatile String lastFgPkg = null;
    /* v3.6.0：本窗点击梯子已优雅收手（预算用尽后不再动作，新窗口重置） */
    private final java.util.HashMap<String, Boolean> gaveUp = new java.util.HashMap<>();""")

rep("""    /* v3.4.0：坐标兜底每次启动一次（新窗口重置） */
    private final java.util.HashMap<String, Boolean> coordTapUsed = new java.util.HashMap<>();
""", "")

# ── 4. onServiceConnected：版本日志 + 毒指纹清理 ──
rep("""        startKeepAliveForeground();
        cancelAlertNotification();
        appendLog("服务已启动 · v3.5.0 点击复验+位置学习+保活（决策核心 48 用例全绿）");
    }""",
"""        startKeepAliveForeground();
        cancelAlertNotification();
        // v3.6.0：清理 v3.5.0 误学毒指纹（MainActivity/FrameLayout/Dialog 被
        // 学成「广告窗指纹」——用户二次进入被连按返回键退出的根因）
        int purged = purgePoisonFingerprints();
        appendLog("服务已启动 · v3.6.0 根窗口安全门+毒指纹清理+点击原坐标"
                + (purged > 0 ? "｜已清理 " + purged + " 条误学窗口指纹" : ""));
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // v3.6.0：后台清理（滑掉最近任务）时，部分 ROM 会连无障碍服务一起杀。
        // ① 若进程幸存：4s 后自检，服务真死了才发「待恢复」提醒（防误报）
        // ② 自检闹钟（15s）：进程被杀但非 force-stop 的 ROM 上仍可触发提醒
        try {
            if (!prefs().getBoolean(KEY_KEEPON, false)) return;
            handler.postDelayed(() -> {
                if (instance == null) KeepAliveReceiver.notifyAlert(AdSkipService.this);
            }, 4000);
            scheduleSelfCheckAlarm(15000);
        } catch (Throwable ignored) {
        }
    }

    /** v3.6.0：自检闹钟——清理后台后若服务已死（且非 force-stop 状态），发恢复提醒 */
    private void scheduleSelfCheckAlarm(long delayMs) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            Intent it = new Intent(this, KeepAliveReceiver.class);
            it.setAction(KeepAliveReceiver.ACTION_SELF_CHECK);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    this, 7, it,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + delayMs, pi);
        } catch (Throwable ignored) {
        }
    }

    /** v3.6.0：清理误学窗口指纹——只删 cls 规则中非广告窗类的（保留位置/文本规则） */
    private int purgePoisonFingerprints() {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) return 0;
                int purged = 0;
                java.util.Iterator<String> it = rules.keys();
                while (it.hasNext()) {
                    String pkg = it.next();
                    JSONArray list = rules.optJSONArray(pkg);
                    if (list == null) continue;
                    JSONArray kept = new JSONArray();
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject r = list.optJSONObject(i);
                        if (r == null) continue;
                        String cls = r.optString("cls", "");
                        boolean poison = cls.length() > 0
                                && !r.optBoolean("pos", false)
                                && !AdDecisionCore.isAdActivityClass(cls);
                        if (poison) {
                            purged++;
                        } else {
                            kept.put(r);
                        }
                    }
                    if (kept.length() == 0) it.remove();
                    else rules.put(pkg, kept);
                }
                if (purged > 0) saveState(st);
                return purged;
            } catch (Exception e) {
                return 0;
            }
        }
    }""")

# ── 5. TYPE_WINDOW_STATE_CHANGED：入口窗追踪 + 去掉 adWinUntil + OEM 弹窗守卫 ──
rep("""            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                String cls = cs(event.getClassName());
                if (reboundChainJump(pkg, now)) break;
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
                coordTapUsed.put(pkg, Boolean.FALSE); // 新窗口重置坐标兜底
                winClsLearned.remove(pkg);
                if (isDialogClass(cls)) {
                    popupUntil.put(pkg, now + POPUP_WINDOW_MS);
                }
                if (AdDecisionCore.isAdActivityClass(cls)) {
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    adWinUntil.put(pkg, now + WINDOW_MS);
                    appendLog("🎯 SDK 广告窗识别（" + shortPkg(pkg) + "）：" + cls
                            + " → 500ms 内无真跳过键将温和直断");
                } else if (learnedClassHit(pkg, cls)) {
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    adWinUntil.put(pkg, now + WINDOW_MS);
                    winClsLearned.put(pkg, Boolean.TRUE);
                }
                bumpScanChain(pkg);
                break;
            }""",
"""            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                String cls = cs(event.getClassName());
                // v3.6.0 根窗口追踪：新进入前台的应用，其首个窗口 = 入口窗
                //（冷启动开屏 / 从桌面·其他应用回前台的首窗）——入口窗上
                // 按返回键 = 退出应用，安全门据此禁一切返回键兜底
                boolean entering = !pkg.equals(lastFgPkg);
                lastFgPkg = pkg;
                if (reboundChainJump(pkg, now)) break;
                if (reboundLanding(pkg, cls, now)) break;
                // v3.5.0 复验通过：上一窗点过东西且窗口已切走 → 广告已关
                Integer prevTries = clickTries.get(pkg);
                if (prevTries != null && prevTries > 0 && !cls.equals(winClassOf(pkg))) {
                    appendLog("✓ 复验通过（" + shortPkg(pkg) + "）：广告窗已关闭"
                            + (prevTries > 1 ? "（共 " + prevTries + " 次尝试，重试梯子兜底）" : ""));
                    // v3.6.0 毒链① 根治：广告已关，学习窗快断资格同步作废
                    //（防止后续窗口还拿着旧资格按返回键）
                    winClsLearned.remove(pkg);
                }
                windowAt.put(pkg, now);
                winClass.put(pkg, cls);
                if (entering) rootWinCls.put(pkg, cls); // v3.6.0：记录入口窗
                backTries.put(pkg, 0);
                clickTries.put(pkg, 0); // v3.5.0：新窗重置复验计数
                gaveUp.put(pkg, Boolean.FALSE); // v3.6.0：新窗重置收手标记
                winClsLearned.remove(pkg);
                if (isDialogClass(cls) && !isOemUtilityPkg(pkg)) {
                    popupUntil.put(pkg, now + POPUP_WINDOW_MS);
                }
                if (AdDecisionCore.isAdActivityClass(cls)) {
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    appendLog("🎯 SDK 广告窗识别（" + shortPkg(pkg) + "）：" + cls
                            + " → 500ms 内无真跳过键将温和直断");
                } else if (learnedClassHit(pkg, cls)) {
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    winClsLearned.put(pkg, Boolean.TRUE);
                }
                bumpScanChain(pkg);
                break;
            }""")

# ── 6. isSystemNoise 后追加 OEM 工具包判定 ──
rep("""    private boolean isSystemNoise(String pkg) {
        return pkg.startsWith("com.android.systemui")
                || pkg.equals("android")
                || pkg.contains("inputmethod")
                || pkg.contains("packageinstaller")
                || pkg.equals("com.android.settings");
    }""",
"""    private boolean isSystemNoise(String pkg) {
        return pkg.startsWith("com.android.systemui")
                || pkg.equals("android")
                || pkg.contains("inputmethod")
                || pkg.contains("packageinstaller")
                || pkg.equals("com.android.settings");
    }

    /** v3.6.0：厂商系统工具包（安全中心/系统管家类）——弹窗突发检测不适用
     *（真机日志：securitycenter/keep 的系统弹窗被误开弹窗识别窗） */
    private boolean isOemUtilityPkg(String pkg) {
        String p = pkg.toLowerCase();
        return p.contains("miui") || p.contains("securitycenter") || p.contains("systemmanager")
                || p.contains("safecenter") || p.contains("permissionmanager") || p.contains("finddevice");
    }""")

rep("        if (!popupShieldEnabled()) return;",
    "        if (!popupShieldEnabled() || isOemUtilityPkg(pkg)) return;")

# ── 7. tryScan：sdkAdWin 绑定当前窗口类名 + rootWindow 接线 ──
rep("""            Long lastClick = lastClickAt.get(pkg);
            boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
            Boolean used = coordTapUsed.get(pkg);
            // v3.5.0：学习位置指纹（手动教过的坐标）与复验次数""",
"""            Long lastClick = lastClickAt.get(pkg);
            boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
            // v3.5.0：学习位置指纹（手动教过的坐标）与复验次数""")

rep("""                ctx.guard = AdDecisionCore.isGuardPkg(pkg);
                ctx.coordTapUsed = used != null && used.booleanValue();
                ctx.sdkAdWin = now < adWinUntilGet(pkg);
                Boolean learned = winClsLearned.get(pkg);
                ctx.learnedCls = learned != null && learned.booleanValue();
                ctx.clickAttempts = clickAttempts;""",
"""                ctx.guard = AdDecisionCore.isGuardPkg(pkg);
                // v3.6.0 毒链① 根治：sdkAdWin 只认【当前窗口类名】是 SDK 广告窗
                //（v3.5.0 的 12s TTL 让主页还拿着 SDK 资格按返回键 → 退出应用）
                ctx.sdkAdWin = AdDecisionCore.isAdActivityClass(winClassOf(pkg));
                Boolean learned = winClsLearned.get(pkg);
                ctx.learnedCls = learned != null && learned.booleanValue();
                // v3.6.0 毒链② 根治：根窗口（入口窗）标记——返回键=退出应用，
                // L3/L4.5/L5 决策核心禁用 + execute() 双保险拦截
                ctx.rootWindow = winClassOf(pkg).equals(rootWinClsOf(pkg));
                ctx.clickAttempts = clickAttempts;""")

# ── 8. execute()：根窗口返回键双保险 + 点击预算收手 ──
rep("""    /** 执行决策；返回 true = 已出手（熔断扫描链） */
    private boolean execute(String pkg, Decision d, ScanFind f, Ctx ctx, boolean clickCoolOK, long now) {
        String how = d.action + "/" + d.reason;
        if (d.action.equals(AdDecisionCore.A_BACK)) {
            if (triesOf(pkg) >= BACK_RETRY_MAX || !backAllowed(now)) return false;""",
"""    /** 执行决策；返回 true = 已出手（熔断扫描链） */
    private boolean execute(String pkg, Decision d, ScanFind f, Ctx ctx, boolean clickCoolOK, long now) {
        String how = d.action + "/" + d.reason;
        // v3.6.0：点击梯子预算——同窗点击 ≥MAX_CLICKS_PER_WIN 即优雅收手
        //（绝不无限重试，更绝不升级返回键；等用户手动跳过，手动点击仍会被学习）
        if ((d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) || d.action.equals(AdDecisionCore.A_CLOSE)
                || d.action.equals(AdDecisionCore.A_TAP_POINT))
                && triesOfClick(pkg) >= AdDecisionCore.MAX_CLICKS_PER_WIN) {
            Boolean done = gaveUp.get(pkg);
            if (done == null || !done.booleanValue()) {
                gaveUp.put(pkg, Boolean.TRUE);
                appendLog("🤲 点击梯子已用尽（" + shortPkg(pkg) + "）：" + AdDecisionCore.MAX_CLICKS_PER_WIN
                        + " 次尝试未关闭广告 → 优雅收手（本窗不再动作；你的手动跳过仍会被学习）");
            }
            return false;
        }
        if (d.action.equals(AdDecisionCore.A_BACK)) {
            // v3.6.0 根窗口安全门（双保险，决策核心已先拦一道）：
            // 入口窗上按返回键 = 退出应用（喜马拉雅「再按一次退出到桌面」事故）
            if (ctx.rootWindow && !ctx.sdkAdWin) {
                appendLog("⛔ 根窗口安全门（" + shortPkg(pkg) + "）：入口窗禁返回键（防退出应用），只走点击梯子");
                return false;
            }
            if (triesOf(pkg) >= BACK_RETRY_MAX || !backAllowed(now)) return false;""")

# ── 9. A_TAP_POINT：去掉 coordTapUsed ──
rep("""        if (d.action.equals(AdDecisionCore.A_TAP_POINT)) {
            if (!clickCoolOK) return false;
            boolean isLearned = "learned_pos".equals(d.reason);
            if (isLearned) coordTapUsed.put(pkg, Boolean.TRUE); // 位置指纹属高置信，直接占位
            if (gestureTap(d.x, d.y)) {""",
"""        if (d.action.equals(AdDecisionCore.A_TAP_POINT)) {
            if (!clickCoolOK) return false;
            boolean isLearned = "learned_pos".equals(d.reason);
            if (gestureTap(d.x, d.y)) {""")

rep("""                clickTries.put(pkg, triesOfClick(pkg) + 1);
                if (!isLearned) coordTapUsed.put(pkg, Boolean.TRUE);
                energyGestures++;""",
"""                clickTries.put(pkg, triesOfClick(pkg) + 1);
                energyGestures++;""")

# ── 10. reboundChainJump：去掉 coordTapUsed 重置 ──
rep("""            bumpStat(host, "chain");
            windowAt.put(host, now);
            coordTapUsed.put(host, Boolean.FALSE);
            bumpScanChain(host);
            return true;""",
"""            bumpStat(host, "chain");
            windowAt.put(host, now);
            bumpScanChain(host);
            return true;""")

# ── 11. learnClass：只沉淀真广告窗类（毒链① 断源） ──
rep("""    private void learnClass(String pkg, String cls) {
        if (cls == null || cls.length() == 0) return;
        synchronized (lock) {""",
"""    private void learnClass(String pkg, String cls) {
        if (cls == null || cls.length() == 0) return;
        // v3.6.0 毒链① 断源：只沉淀真正的广告窗类（SDK 广告 Activity）。
        // v3.5.0 无此校验 → MainActivity/FrameLayout/Dialog 全被学成
        // 「广告窗指纹」→ 每次进应用都在主页按返回键 → 退出应用
        if (!AdDecisionCore.isAdActivityClass(cls)) return;
        synchronized (lock) {""")

# ── 12. 删除 adWinUntilGet 辅助方法（已无引用） ──
rep("""    private long adWinUntilGet(String pkg) {
        Long t = adWinUntil.get(pkg);
        return t == null ? 0L : t;
    }

""", "")

# ── 13. winClassOf 旁补 rootWinClsOf ──
rep("""    private String winClassOf(String pkg) {
        String c = winClass.get(pkg);
        return c == null ? "" : c;
    }""",
"""    private String winClassOf(String pkg) {
        String c = winClass.get(pkg);
        return c == null ? "" : c;
    }

    /** v3.6.0：应用入口窗类名（无记录返回空串 = 非入口窗） */
    private String rootWinClsOf(String pkg) {
        String c = rootWinCls.get(pkg);
        return c == null ? "" : c;
    }""")

open(P, "w", encoding="utf-8").write(src)
print(f"[OK] 13 组替换全部成功，文件已写回（{len(orig)} → {len(src)} 字符）")
