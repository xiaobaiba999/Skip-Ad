package com.zai.adshield;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.AdDecisionCore.ScanFind;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 开屏广告智能跳过服务（无障碍服务实现 · v3.6.0）
 * ─────────────────────────────────────────────────────────────
 * v3.5.0（二次真机反馈驱动）：点击复验梯子 + 位置学习 + 保活三件套。
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
 *  · 保活新增：onTaskRemoved 即时提醒 + 自检闹钟 + 打开 App 自动拉活重绑。
 *    一个角落候选点并强制 dispatchGesture 真实触点重试；同窗点击 ≥3 次
 *    仍未关 → 返回键兜底（「点了没反应」的最终兜底）。
 *  · 位置学习：手动点击图片跳过键（零文本）也能学——归一化坐标 + 窗口
 *    类名指纹，次启同窗 500ms 直点（教一次就会）。
 *  · 保活：前台常驻通知 + keepOn 持久化 + KeepAliveReceiver 断连提醒——
 *    开启后除非手动关闭不再静默失效（更新/重启后一键恢复）。
 *  · 架构：识别/决策逻辑全部在 AdDecisionCore（纯 Java，JVM 用例必绿）；
 *    本类只负责：
 *  · 事件调度（0ms 首扫 + 前密后疏重试链；点击后转入复验调度，不熔断）
 *  · 决策执行：performAction(ACTION_CLICK) 失效自动降级 dispatchGesture 手势点击
 *    （v3.4.0 关键：许多应用拦截 performAction，但拦不住真实触点；
 *    v3.5.0 复验重试一律直接手势真实触点）
 *  · 多窗口扫描：getRootInActiveWindow 之外遍历 getWindows()（弹窗/覆盖层）
 *  · 坐标级点击兜底执行（L6，决策核心判定后执行）
 *  · 链式跳转回弹 / 同包落地页弹回 / 学习 / 统计 / 能耗画像
 *
 * 真机反馈修复（2026-11：微博/喜马拉雅等主流应用仍拦截失败）：
 *  根因：第一方图片开屏——窗口类名是应用自己的 Activity（不匹配 SDK 指纹）、
 *  跳过按钮是纯图片（零文本零 desc）、倒计时烧在图里 → v3.3.0 零信号不出手。
 *  对策：L4 角落无字小可点控件点击（图片跳过键正解）+ L6 坐标级点击兜底
 *  （dispatchGesture 真实触点，点击标准跳过位 88%宽/7%高，防误点六重约束）。
 *
 * 合规边界（沿用）：只代点「跳过/关闭」类控件、返回键温和关闭广告窗，
 * 不碰网络层、不终止任何进程与服务（「拦精灵」案司法边界）。
 */
public class AdSkipService extends AccessibilityService {

    public static volatile AdSkipService instance;

    private static final String PREFS = "adshield";
    private static final String KEY_STATE = "state";
    private static final String KEY_KEEPON = "keepOn";

    private static final int NOTIF_KEEP = 1;   // 前台保活常驻通知
    public static final int NOTIF_ALERT = 2;   // 服务断开提醒（KeepAliveReceiver 发，服务连上即撤）

    private static final long WINDOW_MS = 12000;       // 开屏时间窗
    private static final long COOLDOWN_MS = 1200;      // 同一应用两次自动点击最小间隔
    private static final long LEARN_WINDOW_MS = 30000; // 手动点击学习宽时间窗
    private static final int MAX_RULES_PER_APP = 8;
    private static final int MAX_LOG = 60;

    /* 极速链：相对窗口起点的扫描节奏（前密后疏，命中即熔断） */
    private static final long[] SCAN_AT =
            {0, 120, 240, 400, 650, 1000, 1500, 2200, 3200, 4500, 6000, 8000, 10500};
    /* 返回键重试：每个窗口最多直断次数（个别广告拦截第一次返回键） */
    private static final int BACK_RETRY_MAX = 2;
    /* 广告特征有效期（链式回弹依据） */
    private static final long CHAIN_GUARD_MS = 6000;
    /* 同包落地页弹回：仅限广告判定后此时长内拉起的 WebView */
    private static final long LANDING_GUARD_MS = 3000;
    /* 弹窗窗期与冷却 */
    private static final long POPUP_WINDOW_MS = 10000;
    private static final long POPUP_COOLDOWN_MS = 2500;
    private static final long POPUP_SCAN_THROTTLE_MS = 700;
    private static final int BURST_COUNT = 3;
    private static final long BURST_WIN_MS = 1500;
    /* 多窗口扫描上限（能耗） */
    private static final int MAX_WINDOWS_SCAN = 3;

    /* 能耗治理：内存聚合 + 延迟落盘 */
    private static final long FLUSH_DELAY_MS = 15000;
    private boolean flushScheduled = false;

    /* 能耗画像（开机累计，学习中心展示） */
    public long energyEvents = 0;
    public long energyScans = 0;
    public long energyBacks = 0;
    public long energyGestures = 0;
    public long popupBlocks = 0;
    public long chainRebounds = 0;

    private final Object lock = new Object();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.HashMap<String, Long> windowAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastClickAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastPopupAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> adFeatureUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Integer> scanGen = new java.util.HashMap<>();
    private final java.util.HashMap<String, String> winClass = new java.util.HashMap<>();
    private final java.util.HashMap<String, Boolean> winClsLearned = new java.util.HashMap<>();
    /* v3.6.0 根窗口安全门：应用入口窗（冷启动开屏/回前台首窗）类名——
     * 入口窗上按返回键 = 退出应用，返回键决策全部禁用 */
    private final java.util.HashMap<String, String> rootWinCls = new java.util.HashMap<>();
    /* v3.6.0：前台包名追踪（判断应用是否【新进入前台】→ 记录入口窗） */
    private volatile String lastFgPkg = null;
    /* v3.6.0：本窗点击梯子已优雅收手（预算用尽后不再动作，新窗口重置） */
    private final java.util.HashMap<String, Boolean> gaveUp = new java.util.HashMap<>();
    private final java.util.HashMap<String, Integer> backTries = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> popupUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastPopupScanAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, long[]> burstWin = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> landingBackAt = new java.util.HashMap<>();
    /* v3.5.0：点击复验——本窗已点击次数（换候选/真实触点重试/返回键兜底的依据） */
    private final java.util.HashMap<String, Integer> clickTries = new java.util.HashMap<>();

    private volatile String lastAutoClickPkg;
    private volatile long lastAutoClickAt;

    private int screenWidth;
    private int screenHeight;

    /** 状态快照（含缓存）：Bridge 读 state 走这里，UI 永远拿最新内存态 */
    public static String snapshotState(Context ctx) {
        AdSkipService s = instance;
        if (s != null) {
            synchronized (s.lock) {
                try {
                    return s.stateInMemory.toString();
                } catch (Exception ignored) {
                }
            }
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        // v3.5.0 保活：服务连上即记「用户要求保持开启」；仅用户在 App 内手动
        // 关闭保活提醒（Bridge.setKeepOn(false)）才置 false
        prefs().edit().putBoolean(KEY_KEEPON, true).apply();
        startKeepAliveForeground();
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
    }

    @Override
    public void onDestroy() {
        flushNow();
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        if (pkg.equals(getPackageName())) return;
        if (isSystemNoise(pkg)) return;

        long now = System.currentTimeMillis();
        energyEvents++;

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
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
            }
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                if (now - winAt(pkg) < WINDOW_MS) {
                    nudgeScan(pkg, now);
                    break;
                }
                if (now < popupUntilGet(pkg)) {
                    nudgePopupScan(pkg, now);
                    break;
                }
                detectPopupBurst(pkg, now);
                break;
            }
            case AccessibilityEvent.TYPE_VIEW_CLICKED: {
                if (isOwnAutoClick(pkg, now)) return;
                learnFromManualClick(pkg, event, now);
                observeMisclick(pkg, event, now);
                break;
            }
        }
    }

    /* ───────── 基础判断 ───────── */

    private boolean isSystemNoise(String pkg) {
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
    }

    private static boolean isDialogClass(String cls) {
        if (cls == null) return false;
        String c = cls.toLowerCase();
        return c.contains("dialog") || c.contains("popup");
    }

    private long winAt(String pkg) {
        Long t = windowAt.get(pkg);
        return t == null ? 0L : t;
    }

    private long popupUntilGet(String pkg) {
        Long t = popupUntil.get(pkg);
        return t == null ? 0L : t;
    }

    private boolean isOwnAutoClick(String pkg, long now) {
        return pkg.equals(lastAutoClickPkg) && (now - lastAutoClickAt) < 900;
    }

    private String winClassOf(String pkg) {
        String c = winClass.get(pkg);
        return c == null ? "" : c;
    }

    /** v3.6.0：应用入口窗类名（无记录返回空串 = 非入口窗） */
    private String rootWinClsOf(String pkg) {
        String c = rootWinCls.get(pkg);
        return c == null ? "" : c;
    }

    private int triesOf(String pkg) {
        Integer n = backTries.get(pkg);
        return n == null ? 0 : n.intValue();
    }

    /* ───────── AccessibilityNodeInfo → 决策核心适配 ───────── */

    private static final class NodeAdapter implements NodeView {
        final AccessibilityNodeInfo n;

        NodeAdapter(AccessibilityNodeInfo n) {
            this.n = n;
        }

        public String text() {
            return n.getText() == null ? "" : n.getText().toString().trim();
        }

        public String desc() {
            return n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim();
        }

        public String cls() {
            return n.getClassName() == null ? "" : n.getClassName().toString();
        }

        public boolean clickable() {
            return n.isClickable();
        }

        public boolean scrollable() {
            return n.isScrollable();
        }

        public boolean editable() {
            return n.isEditable();
        }

        public int[] bounds() {
            try {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                return new int[]{r.left, r.top, r.right, r.bottom};
            } catch (Exception e) {
                return null;
            }
        }

        public int childCount() {
            return n.getChildCount();
        }

        public NodeView child(int i) {
            AccessibilityNodeInfo c = n.getChild(i);
            return c == null ? null : new NodeAdapter(c);
        }

        public NodeView parent() {
            AccessibilityNodeInfo p = n.getParent();
            return p == null ? null : new NodeAdapter(p);
        }
    }

    /* ───────── 扫描调度：0ms 首扫 + 前密后疏重试链 ───────── */

    private void bumpScanChain(final String pkg) {
        final long winStart = winAt(pkg);
        Integer g = scanGen.get(pkg);
        final int gen = g == null ? 1 : g + 1;
        scanGen.put(pkg, gen);
        for (int i = 0; i < SCAN_AT.length; i++) {
            final long at = SCAN_AT[i];
            handler.postDelayed(() -> {
                Integer cur = scanGen.get(pkg);
                if (cur == null || cur.intValue() != gen) return;
                tryScan(pkg, winStart, at);
            }, at);
        }
    }

    private void nudgeScan(final String pkg, long now) {
        Integer gen = scanGen.get(pkg);
        if (gen == null) {
            bumpScanChain(pkg);
            return;
        }
        final long winStart = winAt(pkg);
        if (now - winStart < 150) return;
        final int g = gen.intValue();
        handler.post(() -> {
            Integer cur = scanGen.get(pkg);
            if (cur == null || cur.intValue() != g) return;
            tryScan(pkg, winStart, -1);
        });
    }

    private void nudgePopupScan(final String pkg, long now) {
        Long last = lastPopupScanAt.get(pkg);
        if (last != null && now - last < POPUP_SCAN_THROTTLE_MS) return;
        lastPopupScanAt.put(pkg, now);
        final long winStart = winAt(pkg);
        handler.post(() -> {
            if (instance == null) return;
            tryScan(pkg, winStart, -2);
        });
    }

    private void detectPopupBurst(String pkg, long now) {
        if (!popupShieldEnabled() || isOemUtilityPkg(pkg)) return;
        long[] w = burstWin.get(pkg);
        if (w == null || now - w[0] > BURST_WIN_MS) {
            burstWin.put(pkg, new long[]{now, 1});
            return;
        }
        w[1]++;
        if (w[1] >= BURST_COUNT) {
            burstWin.remove(pkg);
            popupUntil.put(pkg, now + POPUP_WINDOW_MS);
            appendLog("🪟 检测到内容突发（" + shortPkg(pkg) + "）：疑似覆盖层弹窗 → 开启弹窗识别窗");
            nudgePopupScan(pkg, now);
        }
    }

    private void fuseChain(String pkg) {
        Integer g = scanGen.get(pkg);
        scanGen.put(pkg, g == null ? 1 : g + 1);
    }

    /* ───────── 扫描与决策执行（逻辑全部在 AdDecisionCore） ───────── */

    private void tryScan(String pkg, long winStart, long atMs) {
        if (instance == null) return;
        long now = System.currentTimeMillis();
        long elapsed = now - winStart;
        boolean splashLive = learningEnabled() && elapsed <= WINDOW_MS;
        boolean popupLive = popupShieldEnabled() && now < popupUntilGet(pkg) && popupCooldownOK(pkg, now);
        if (!splashLive && !popupLive) return;

        try {
            /* 多窗口扫描：活动窗口优先，其次 getWindows() 同包窗口（弹窗/覆盖层） */
            AccessibilityNodeInfo[] roots = collectRoots(pkg);
            if (roots.length == 0) return;

            Long lastClick = lastClickAt.get(pkg);
            boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
            // v3.5.0：学习位置指纹（手动教过的坐标）与复验次数
            int[] tapHint = learnedPos(pkg);
            Integer triesBox = clickTries.get(pkg);
            int clickAttempts = triesBox == null ? 0 : triesBox.intValue();

            for (int ri = 0; ri < roots.length; ri++) {
                energyScans++;
                Ctx ctx = new Ctx();
                ctx.screenW = screenWidth;
                ctx.screenH = screenHeight;
                ctx.elapsedMs = Math.max(0, elapsed - ri * 30);
                ctx.splashLive = splashLive;
                ctx.popupLive = popupLive;
                ctx.guard = AdDecisionCore.isGuardPkg(pkg);
                // v3.6.0 毒链① 根治：sdkAdWin 只认【当前窗口类名】是 SDK 广告窗
                //（v3.5.0 的 12s TTL 让主页还拿着 SDK 资格按返回键 → 退出应用）
                ctx.sdkAdWin = AdDecisionCore.isAdActivityClass(winClassOf(pkg));
                Boolean learned = winClsLearned.get(pkg);
                ctx.learnedCls = learned != null && learned.booleanValue();
                // v3.6.0 毒链② 根治：根窗口（入口窗）标记——返回键=退出应用，
                // L3/L4.5/L5 决策核心禁用 + execute() 双保险拦截
                ctx.rootWindow = winClassOf(pkg).equals(rootWinClsOf(pkg));
                ctx.clickAttempts = clickAttempts;
                ctx.tapHintX = tapHint[0];
                ctx.tapHintY = tapHint[1];
                ctx.budgetNodes = splashLive ? 600 : 280; // 开屏窗预算扩容（重树）

                ScanFind f = AdDecisionCore.walk(new NodeAdapter(roots[ri]), ctx);
                if (f.foundSkip || f.gyroHint || f.fakeSkip || f.hotZone || ctx.sdkAdWin || ctx.learnedCls) {
                    Long old = adFeatureUntil.get(pkg);
                    if (old == null || now > old) adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                }
                Decision d = AdDecisionCore.decide(f, ctx);
                if (d.action.equals(AdDecisionCore.A_NONE)) continue;
                if (execute(pkg, d, f, ctx, clickCoolOK, now)) return; // 命中即熔断
            }
        } catch (Exception ignored) {
        }
    }

    /** 收集同包可扫描窗口根节点（活动窗口优先，最多 3 个） */
    private AccessibilityNodeInfo[] collectRoots(String pkg) {
        java.util.ArrayList<AccessibilityNodeInfo> out = new java.util.ArrayList<>();
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            CharSequence rp = active.getPackageName();
            if (rp != null && pkg.contentEquals(rp)) out.add(active);
        }
        if (out.isEmpty()) {
            try {
                List<AccessibilityWindowInfo> wins = getWindows();
                if (wins != null) {
                    for (AccessibilityWindowInfo w : wins) {
                        if (out.size() >= MAX_WINDOWS_SCAN) break;
                        CharSequence wp = w == null ? null : w.getRoot() == null ? null : w.getRoot().getPackageName();
                        if (wp != null && pkg.contentEquals(wp)) {
                            AccessibilityNodeInfo r = w.getRoot();
                            if (r != null) out.add(r);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return out.toArray(new AccessibilityNodeInfo[0]);
    }

    /** 执行决策；返回 true = 已出手（熔断扫描链） */
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
            if (triesOf(pkg) >= BACK_RETRY_MAX || !backAllowed(now)) return false;
            performGlobalAction(GLOBAL_ACTION_BACK);
            energyBacks++;
            backTries.put(pkg, triesOf(pkg) + 1);
            adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
            if (d.reason.startsWith("sdk_window")) learnClass(pkg, winClassOf(pkg));
            if ("click_verify_fail".equals(d.reason)) {
                appendLog("🛡 点击复验失败升级（" + shortPkg(pkg) + "）：同窗 " + ctx.clickAttempts
                        + " 次点击仍未关闭 → 返回键兜底温和关闭（不碰进程与服务）");
            } else {
                appendLog("🛡 广告窗直断（" + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                        + " · " + ctx.elapsedMs + "ms → 返回键温和关闭（不碰进程与服务）");
            }
            bumpStat(pkg, "back");
            fuseChain(pkg);
            return true;
        }
        if (d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) || d.action.equals(AdDecisionCore.A_CLOSE)) {
            if (!clickCoolOK) return false;
            int attempts = triesOfClick(pkg);
            // v3.5.0 复验重试：第 1 次 performAction→手势降级；第 ≥2 次一律手势真实触点
            if (clickNode(d, attempts >= 1)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                clickTries.put(pkg, attempts + 1);
                if (d.action.equals(AdDecisionCore.A_CLOSE)) {
                    popupBlocks++;
                    lastPopupAt.put(pkg, now);
                    appendLog("🪟 弹窗广告已关闭（" + shortPkg(pkg) + "）："
                            + (f.adBadge ? "「" + f.badgeLabel + "」角标" : "Dialog+营销话术")
                            + " + 关闭键「" + f.closeLabel + "」");
                    bumpStat(pkg, "popup");
                } else {
                    if (attempts == 0) {
                        appendLog("⚡ 已点击跳过（" + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                                + "（" + (lastGesture ? "手势点击" : "无障碍代点") + "）→ 1.2s 后复验，未关将换候选重试");
                    } else {
                        appendLog("🔁 复验未关闭 → 第 " + (attempts + 1) + " 次出手（" + shortPkg(pkg) + "）："
                                + reasonZh(d.reason) + "（手势真实触点）");
                    }
                    bumpStat(pkg, "auto");
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                }
                // v3.5.0 关键：点击后【不熔断】扫描链——由后续扫描复验；
                // 广告关掉会触发新窗口事件（计数清零）；仍在则换候选/升级兜底
                return true;
            }
            return false;
        }
        if (d.action.equals(AdDecisionCore.A_TAP_POINT)) {
            if (!clickCoolOK) return false;
            boolean isLearned = "learned_pos".equals(d.reason);
            if (gestureTap(d.x, d.y)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                clickTries.put(pkg, triesOfClick(pkg) + 1);
                energyGestures++;
                if (isLearned) {
                    appendLog("🎯 学习位置点击（" + shortPkg(pkg) + "）：你手动教过的位置指纹 ("
                            + d.x + "," + d.y + ")（手势真实触点）→ 1.2s 后复验");
                    bumpPosRule(pkg);
                } else {
                    appendLog("🎯 坐标级点击兜底（" + shortPkg(pkg) + "）：开屏窗内零信号简单树"
                            + " → 点击标准跳过位 (" + d.x + "," + d.y + ")，本次启动仅一次 → 1.2s 后复验");
                }
                bumpStat(pkg, "auto");
                return true; // 不熔断：交由复验扫描确认
            }
            return false;
        }
        return false;
    }

    private boolean lastGesture = false;

    /** 点击控件：performAction 优先，失效降级 dispatchGesture 手势点击（真实触点）；
     *  v3.5.0：forceGesture=true（复验重试）跳过 performAction 直接真实触点——
     *  performAction 返回 true 也可能被应用无声吞掉，真机「点了没反应」的主因 */
    private boolean clickNode(Decision d, boolean forceGesture) {
        lastGesture = false;
        if (!forceGesture && d.node instanceof NodeAdapter) {
            try {
                if (((NodeAdapter) d.node).n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        if (d.bounds != null) {
            int cx = (d.bounds[0] + d.bounds[2]) / 2;
            int cy = (d.bounds[1] + d.bounds[3]) / 2;
            if (gestureTap(cx, cy)) {
                lastGesture = true;
                energyGestures++;
                return true;
            }
        }
        return false;
    }

    /** dispatchGesture 坐标点击（API 24+；真实触点，performAction 拦不住） */
    private boolean gestureTap(int x, int y) {
        try {
            if (x <= 0 || y <= 0) return false;
            Path p = new Path();
            p.moveTo(x, y);
            GestureDescription gd = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p, 0, 60))
                    .build();
            return dispatchGesture(gd, null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String reasonZh(String r) {
        if (r.equals("skip_text")) return "文本跳过键";
        if (r.equals("sdk_window")) return "SDK 广告窗指纹";
        if (r.equals("sdk_window_learned")) return "学习窗口指纹（快断）";
        if (r.equals("gyro_immediate")) return "翻转/摇一摇文案立即直断";
        if (r.equals("gyro_break")) return "陀螺仪诱导/假跳过直断";
        if (r.equals("corner_image_skip")) return "角落图片跳过键";
        if (r.equals("learned_pos")) return "学习位置（你手动教过）";
        if (r.equals("splash_close")) return "开屏×关闭键";
        if (r.equals("hot_zone")) return "全屏热区自绘广告";
        if (r.equals("coord_fallback")) return "坐标级兜底";
        return r;
    }

    /** 返回键全局节流：600ms 内最多一次 */
    private long lastBackAt = 0;

    private boolean backAllowed(long now) {
        if (now - lastBackAt < 600) return false;
        lastBackAt = now;
        return true;
    }

    private boolean popupCooldownOK(String pkg, long now) {
        Long t = lastPopupAt.get(pkg);
        return t == null || now - t >= POPUP_COOLDOWN_MS;
    }

    /* ───────── 链式跳转回弹（跨应用） ───────── */

    private boolean reboundChainJump(String newPkg, long now) {
        try {
            if (!chainReboundEnabled()) return false;
            if (isSystemNoise(newPkg) || isLauncherLike(newPkg)) return false;
            if (!AdDecisionCore.isAdTarget(newPkg)) return false;

            String host = null;
            for (Map.Entry<String, Long> e : adFeatureUntil.entrySet()) {
                String k = e.getKey();
                if (k.equals(newPkg)) continue;
                if (now < e.getValue().longValue()) {
                    if (now - winAt(k) <= WINDOW_MS + CHAIN_GUARD_MS) {
                        host = k;
                        break;
                    }
                }
            }
            if (host == null) return false;
            if (!backAllowed(now)) return false;

            performGlobalAction(GLOBAL_ACTION_BACK);
            chainRebounds++;
            appendLog("⛓ 链式跳转已弹回：" + shortPkg(host) + " 的广告试图拉起 " + shortPkg(newPkg)
                    + " → 返回键送回原应用（" + (now - winAt(host)) + "ms 内）");
            bumpStat(host, "chain");
            windowAt.put(host, now);
            bumpScanChain(host);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /* ───────── 同包落地页弹回 ───────── */

    private boolean reboundLanding(String pkg, String cls, long now) {
        try {
            if (!chainReboundEnabled()) return false;
            if (cls == null || cls.length() == 0) return false;
            String c = cls.toLowerCase();
            if (!c.contains("webview") && !c.contains("h5")) return false;
            Long until = adFeatureUntil.get(pkg);
            if (until == null || now >= until.longValue()) return false;
            long judgedAt = until.longValue() - CHAIN_GUARD_MS;
            if (now - judgedAt > LANDING_GUARD_MS) return false;
            Long last = landingBackAt.get(pkg);
            if (last != null && now - last.longValue() < CHAIN_GUARD_MS) return false;
            if (!backAllowed(now)) return false;

            landingBackAt.put(pkg, now);
            performGlobalAction(GLOBAL_ACTION_BACK);
            chainRebounds++;
            appendLog("⛓ 广告落地页已弹回（" + shortPkg(pkg) + "）：广告拉起内置 WebView 落地页 → 返回键送回");
            bumpStat(pkg, "chain");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLauncherLike(String pkg) {
        return pkg.contains("launcher") || pkg.contains("home")
                || pkg.equals("com.miui.home") || pkg.equals("com.huawei.android.launcher");
    }

    /* ───────── 智能学习：观察手动点击 + className 指纹 ───────── */

    private void learnFromManualClick(String pkg, AccessibilityEvent event, long now) {
        if (now - winAt(pkg) > LEARN_WINDOW_MS) return;

        String text = "";
        String viewId = "";
        AccessibilityNodeInfo srcNode = null;
        try {
            StringBuilder sb = new StringBuilder();
            if (event.getText() != null) {
                for (CharSequence c : event.getText()) {
                    if (c != null) sb.append(c);
                }
            }
            text = sb.toString().trim();
            AccessibilityNodeInfo src = event.getSource();
            if (src != null) {
                srcNode = src;
                if (text.length() == 0) text = cs(src.getContentDescription());
                viewId = cs(src.getViewIdResourceName());
            }
        } catch (Exception ignored) {
        }
        if (!AdDecisionCore.matchesSkip(text) && !AdDecisionCore.isCloseText(text)) {
            // v3.5.0 位置学习：图片跳过键零文本也要学（真机痛点：「手动记录两次
            // 还是不能跳过」——v3.4.0 只认文本，图片键永远学不到）
            learnPositionFromManualClick(pkg, srcNode, now);
            return;
        }

        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) {
                    rules = new JSONObject();
                    st.put("rules", rules);
                }
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) {
                    list = new JSONArray();
                    rules.put(pkg, list);
                }
                boolean merged = false;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r == null) continue;
                    boolean sameId = viewId.length() > 0 && viewId.equals(r.optString("viewId", ""));
                    boolean sameText = text.length() > 0 && text.equals(r.optString("text", ""));
                    if (sameId || sameText) {
                        r.put("manual", r.optInt("manual", 0) + 1);
                        r.put("ts", now);
                        if (r.optString("viewId", "").length() == 0 && viewId.length() > 0) {
                            r.put("viewId", viewId);
                        }
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    if (list.length() >= MAX_RULES_PER_APP) return;
                    JSONObject r = new JSONObject();
                    r.put("text", text);
                    r.put("viewId", viewId);
                    r.put("manual", 1);
                    r.put("auto", 0);
                    r.put("ts", now);
                    list.put(r);
                }
                bumpManualStat(st, pkg);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
        appendLog("已学习：" + shortPkg(pkg) + " 中你手动点击了「" + text + "」→ 下次将自动点击");
    }

    private boolean learnedClassHit(String pkg, String cls) {
        if (cls == null || cls.length() == 0) return false;
        try {
            JSONObject rules = loadState().optJSONObject("rules");
            if (rules == null) return false;
            JSONArray list = rules.optJSONArray(pkg);
            if (list == null) return false;
            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null) continue;
                if (r.optBoolean("pos", false)) continue; // v3.5.0：位置规则走「点位置」而非快断
                if (cls.equals(r.optString("cls", ""))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void learnClass(String pkg, String cls) {
        if (cls == null || cls.length() == 0) return;
        // v3.6.0 毒链① 断源：只沉淀真正的广告窗类（SDK 广告 Activity）。
        // v3.5.0 无此校验 → MainActivity/FrameLayout/Dialog 全被学成
        // 「广告窗指纹」→ 每次进应用都在主页按返回键 → 退出应用
        if (!AdDecisionCore.isAdActivityClass(cls)) return;
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) {
                    rules = new JSONObject();
                    st.put("rules", rules);
                }
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) {
                    list = new JSONArray();
                    rules.put(pkg, list);
                }
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r != null && cls.equals(r.optString("cls", ""))) {
                        r.put("auto", r.optInt("auto", 0) + 1);
                        r.put("ts", System.currentTimeMillis());
                        saveState(st);
                        return;
                    }
                }
                if (list.length() >= MAX_RULES_PER_APP) return;
                JSONObject r = new JSONObject();
                r.put("text", "");
                r.put("viewId", "");
                r.put("cls", cls);
                r.put("manual", 0);
                r.put("auto", 1);
                r.put("ts", System.currentTimeMillis());
                list.put(r);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    /* ───────── 误触观察 ───────── */

    private void observeMisclick(String pkg, AccessibilityEvent event, long now) {
        try {
            if (screenWidth <= 0 || screenHeight <= 0) return;
            if (now - winAt(pkg) > WINDOW_MS) return;
            AccessibilityNodeInfo src = event.getSource();
            if (src == null) return;
            String label = cs(src.getText());
            if (label.length() == 0) label = cs(src.getContentDescription());
            if (AdDecisionCore.matchesSkip(label) || AdDecisionCore.isCloseText(label)) return;
            Rect r = new Rect();
            src.getBoundsInScreen(r);
            if (r.isEmpty()) return;
            double area = (double) r.width() * r.height();
            double screen = (double) screenWidth * screenHeight;
            boolean huge = (area / screen > 0.35) && (r.width() > screenWidth * 0.55);
            boolean tooFast = (now - winAt(pkg)) < 700;
            if (!huge && !tooFast) return;
            synchronized (lock) {
                JSONObject st = loadState();
                JSONObject stats = st.optJSONObject("stats");
                if (stats == null) { stats = new JSONObject(); st.put("stats", stats); }
                stats.put("misclick", stats.optInt("misclick", 0) + 1);
                saveState(st);
            }
            appendLog("⚠️ 疑似误触（" + shortPkg(pkg) + "）："
                    + (huge ? "全屏热区控件，轻微触碰即跳转" : "窗口切换后 " + (now - winAt(pkg)) + "ms 内点击，快于人类反应")
                    + " → 已记入观察统计");
        } catch (Exception ignored) {
        }
    }

    /* ───────── 开关 ───────── */

    private boolean learningEnabled() {
        try {
            JSONObject st = loadState();
            return !st.has("learning") || st.optBoolean("learning", true);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean popupShieldEnabled() {
        try {
            JSONObject st = loadState();
            return !st.has("popupShield") || st.optBoolean("popupShield", true);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean chainReboundEnabled() {
        try {
            JSONObject st = loadState();
            return !st.has("chainRebound") || st.optBoolean("chainRebound", true);
        } catch (Exception e) {
            return true;
        }
    }

    /* ───────── v3.5.0 位置学习：图片跳过键（零文本）教一次就会 ───────── */

    /**
     * 手动点击小角落/顶部控件（零文本）→ 记录归一化坐标 + 窗口类名指纹。
     * 次启同窗 500ms 手势直点该位置（仅广告形态窗内出手，见决策核心 L1.5 安全门）。
     */
    private void learnPositionFromManualClick(String pkg, AccessibilityNodeInfo src, long now) {
        if (src == null || screenWidth <= 0 || screenHeight <= 0) return;
        if (now - winAt(pkg) > 10000) return; // 仅窗口切换后 10s 内（开屏期）
        try {
            Rect r = new Rect();
            src.getBoundsInScreen(r);
            if (r.isEmpty()) return;
            int[] b = new int[]{r.left, r.top, r.right, r.bottom};
            double area = AdDecisionCore.areaOf(b, screenWidth, screenHeight);
            if (area > AdDecisionCore.POS_LEARN_AREA_MAX) return; // 大热区=CTA，不学
            // v3.6.0：可学形状扩至底部（部分应用跳过键在右下角，真机反馈）
            if (!AdDecisionCore.learnableSkipShape(b, screenWidth, screenHeight)) return;
            final double x = ((b[0] + b[2]) / 2.0) / screenWidth;
            final double y = ((b[1] + b[3]) / 2.0) / screenHeight;
            final String cls = winClassOf(pkg);
            synchronized (lock) {
                try {
                    JSONObject st = loadState();
                    JSONObject rules = st.optJSONObject("rules");
                    if (rules == null) {
                        rules = new JSONObject();
                        st.put("rules", rules);
                    }
                    JSONArray list = rules.optJSONArray(pkg);
                    if (list == null) {
                        list = new JSONArray();
                        rules.put(pkg, list);
                    }
                    boolean merged = false;
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject rr = list.optJSONObject(i);
                        if (rr == null || !rr.optBoolean("pos", false)) continue;
                        double dx = Math.abs(rr.optDouble("x", -1) - x);
                        double dy = Math.abs(rr.optDouble("y", -1) - y);
                        boolean sameCls = cls.length() == 0 || cls.equals(rr.optString("cls", ""));
                        if (dx <= 0.02 && dy <= 0.02 && sameCls) {
                            rr.put("manual", rr.optInt("manual", 0) + 1);
                            rr.put("ts", now);
                            rr.put("cls", cls);
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) {
                        if (list.length() >= MAX_RULES_PER_APP) return;
                        JSONObject rr = new JSONObject();
                        rr.put("pos", 1);
                        rr.put("x", Math.round(x * 1000) / 1000.0);
                        rr.put("y", Math.round(y * 1000) / 1000.0);
                        rr.put("cls", cls);
                        rr.put("text", "");
                        rr.put("viewId", "");
                        rr.put("manual", 1);
                        rr.put("auto", 0);
                        rr.put("ts", now);
                        list.put(rr);
                    }
                    bumpManualStat(st, pkg);
                    saveState(st);
                } catch (Exception ignored) {
                }
            }
            appendLog("📍 位置已学习（" + shortPkg(pkg) + "）：图片跳过键零文本，已记住位置 ("
                    + Math.round(x * 100) + "%," + Math.round(y * 100) + "%) → 下次启动同窗 500ms 直点");
        } catch (Exception ignored) {
        }
    }

    /** 学习位置指纹（当前窗口类名匹配、manual>0、最新优先）；无则 {-1,-1} */
    private int[] learnedPos(String pkg) {
        try {
            String cls = winClassOf(pkg);
            if (cls.length() == 0) return new int[]{-1, -1};
            JSONObject rules = loadState().optJSONObject("rules");
            if (rules == null) return new int[]{-1, -1};
            JSONArray list = rules.optJSONArray(pkg);
            if (list == null) return new int[]{-1, -1};
            JSONObject best = null;
            long bestTs = -1;
            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null || !r.optBoolean("pos", false)) continue;
                if (r.optInt("manual", 0) < 1) continue;
                if (!cls.equals(r.optString("cls", ""))) continue;
                long ts = r.optLong("ts", 0);
                if (ts > bestTs) {
                    bestTs = ts;
                    best = r;
                }
            }
            if (best == null) return new int[]{-1, -1};
            int x = (int) Math.round(best.optDouble("x", -1) * screenWidth);
            int y = (int) Math.round(best.optDouble("y", -1) * screenHeight);
            if (x <= 0 || y <= 0 || x >= screenWidth || y >= screenHeight) return new int[]{-1, -1};
            return new int[]{x, y};
        } catch (Exception ignored) {
            return new int[]{-1, -1};
        }
    }

    /** 学习位置命中沉淀 auto 次数 */
    private void bumpPosRule(String pkg) {
        try {
            String cls = winClassOf(pkg);
            synchronized (lock) {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) return;
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) return;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r == null || !r.optBoolean("pos", false)) continue;
                    if (cls.equals(r.optString("cls", ""))) {
                        r.put("auto", r.optInt("auto", 0) + 1);
                        saveState(st);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private int triesOfClick(String pkg) {
        Integer n = clickTries.get(pkg);
        return n == null ? 0 : n.intValue();
    }

    /* ───────── v3.5.0 保活：前台常驻通知（系统不杀）+ 断连提醒撤销 ───────── */

    private void startKeepAliveForeground() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("keep", "秒跳引擎守护中",
                        NotificationManager.IMPORTANCE_MIN);
                ch.setDescription("服务保持开启，直到你手动关闭");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(this, "keep");
            } else {
                b = new Notification.Builder(this);
            }
            Intent it = new Intent(this, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentTitle("秒跳引擎守护中")
                    .setContentText("保持开启直到你手动关闭 · 点击进入开屏卫士")
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setColor(0xFF10B981);
            Notification notif = b.build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_KEEP, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_KEEP, notif);
            }
        } catch (Throwable ignored) {
        }
    }

    private void cancelAlertNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(NOTIF_ALERT);
        } catch (Exception ignored) {
        }
    }

    /* ───────── 统计与日志 ───────── */

    private void bumpManualStat(JSONObject st, String pkg) {
        try {
            JSONObject stats = st.optJSONObject("stats");
            if (stats == null) {
                stats = new JSONObject();
                st.put("stats", stats);
            }
            stats.put("manual", stats.optInt("manual", 0) + 1);
            JSONObject byApp = stats.optJSONObject("byApp");
            if (byApp == null) {
                byApp = new JSONObject();
                stats.put("byApp", byApp);
            }
            JSONObject a = byApp.optJSONObject(pkg);
            if (a == null) {
                a = new JSONObject();
                byApp.put(pkg, a);
            }
            a.put("manual", a.optInt("manual", 0) + 1);
        } catch (Exception ignored) {
        }
    }

    private void bumpStat(String pkg, String kind) {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject stats = st.optJSONObject("stats");
                if (stats == null) {
                    stats = new JSONObject();
                    st.put("stats", stats);
                }
                stats.put(kind, stats.optInt(kind, 0) + 1);
                JSONObject byApp = stats.optJSONObject("byApp");
                if (byApp == null) {
                    byApp = new JSONObject();
                    stats.put("byApp", byApp);
                }
                JSONObject a = byApp.optJSONObject(pkg);
                if (a == null) {
                    a = new JSONObject();
                    byApp.put(pkg, a);
                }
                a.put(kind, a.optInt(kind, 0) + 1);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    private void appendLog(String msg) {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONArray log = st.optJSONArray("log");
                if (log == null) {
                    log = new JSONArray();
                    st.put("log", log);
                }
                JSONObject entry = new JSONObject();
                entry.put("t", System.currentTimeMillis());
                entry.put("msg", msg);
                log.put(entry);
                while (log.length() > MAX_LOG) {
                    log.remove(0);
                }
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /* ───────── 内存聚合 + 延迟落盘 ───────── */

    private org.json.JSONObject stateInMemory;

    private org.json.JSONObject loadState() {
        synchronized (lock) {
            if (stateInMemory != null) return stateInMemory;
        }
        try {
            String raw = prefs().getString(KEY_STATE, null);
            stateInMemory = raw != null ? new org.json.JSONObject(raw) : new org.json.JSONObject();
        } catch (Exception e) {
            stateInMemory = new org.json.JSONObject();
        }
        return stateInMemory;
    }

    private void saveState(org.json.JSONObject st) {
        stateInMemory = st;
        scheduleFlush();
    }

    private void scheduleFlush() {
        synchronized (lock) {
            if (flushScheduled) return;
            flushScheduled = true;
        }
        handler.postDelayed(() -> flushNow(), FLUSH_DELAY_MS);
    }

    public void flushNow() {
        synchronized (lock) {
            flushScheduled = false;
        }
        org.json.JSONObject st = stateInMemory;
        if (st == null) return;
        try {
            prefs().edit().putString(KEY_STATE, st.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private String cs(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    private String shortPkg(String pkg) {
        if (pkg == null) return "";
        int i = pkg.lastIndexOf('.');
        return i >= 0 && pkg.length() - i - 1 > 1 ? pkg.substring(i + 1) : pkg;
    }
}
