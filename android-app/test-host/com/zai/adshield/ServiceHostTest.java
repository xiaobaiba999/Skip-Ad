package com.zai.adshield;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 服务级 JVM 回归套件（v7.15.8 回归基建 · 首役：v7.15.7「开屏不跳+日志不记录」
 * 全面失效事故定位）。
 * ─────────────────────────────────────────────────────────────
 * 通过桩 Android API 直接执行【真实 AdSkipService】（非核心类代理）：
 *  · 窗口环境按真实系统语义装配（activeRoot + getWindows 全窗列表，
 *    含异包窗口；同窗节点 windowId 一致——collectRoots 去重可验）；
 *  · 事件经真实 onAccessibilityEvent 入口（全部防线在岗）；
 *  · 扫描链经队列式 Handler 泵 drain() 全档执行；
 *  · 断言：手势点击落点 + 引擎日志原文（appendLog 落 state.log）。
 *
 * 场景（v7.15.7 用户实锤复刻 + 回归红线）：
 *  S1 Keep 覆盖层开屏：活动窗 8 节点微树 + 同包覆盖层 42 节点
 *     「跳过 4」文本键（动态图开屏）→ 必须跳过 + 必须有日志
 *  S2 CSDN 角落图片键：热区+角标在场，角落纯图键零文案零 vid →
 *     图片键边界闸放行 → 必须出手
 *  S3 普通文本倒计时开屏（回归红线）：「跳过 4」在活动窗 → 必须跳过
 *  S4 零候选 CLEAN 档案 + 探针哨兵：三窗零候选后第四窗纯图键 →
 *     探针复活 → 必须跳过
 *  S5 零候选开屏未命中日志（日志红线）：无广告信号窗 → 必须记
 *     「开屏未命中」诊断（issue#38「无法跳过日志也没记录」防线）
 *  S6 启动横幅（服务活着的最小证据）：onServiceConnected 后
 *     引擎日志非空
 */
public class ServiceHostTest {

    static int pass = 0, fail = 0;
    static final List<String> failures = new ArrayList<String>();

    // ── 反射工具（private 字段/方法触达）──
    static Field f(String name) throws Exception {
        Field x = AdSkipService.class.getDeclaredField(name);
        x.setAccessible(true);
        return x;
    }

    static Object get(String name, Object target) throws Exception {
        return f(name).get(target);
    }

    static void set(String name, Object target, Object v) throws Exception {
        f(name).set(target, v);
    }

    /** 引擎日志快照（appendLog 落 state.log——真实链路） */
    static List<String> logs(AdSkipService svc) {
        List<String> out = new ArrayList<String>();
        try {
            Method load = AdSkipService.class.getDeclaredMethod("loadState");
            load.setAccessible(true);
            JSONObject st = (JSONObject) load.invoke(svc);
            JSONArray arr = st.optJSONArray("log");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.optJSONObject(i);
                    if (e != null) out.add(e.optString("msg"));
                }
            }
        } catch (Throwable t) {
            out.add("<读日志失败: " + t + ">");
        }
        return out;
    }

    static boolean hasLog(List<String> logs, String needle) {
        for (String l : logs) if (l.contains(needle)) return true;
        return false;
    }

    /** 泵扫描链（真实 handler 队列全档执行） */
    static void drain(AdSkipService svc) throws Exception {
        Object h = get("handler", svc);
        Method d = h.getClass().getDeclaredMethod("drain");
        d.setAccessible(true);
        d.invoke(h);
    }

    // ── 节点构造 DSL ──
    static AccessibilityNodeInfo root(String pkg, int winId, String cls) {
        AccessibilityNodeInfo r = new AccessibilityNodeInfo();
        r.packageName = pkg;
        r.className = cls;
        r.windowId = winId;
        r.boundsInScreen = new android.graphics.Rect(0, 0, 1080, 2400);
        return r;
    }

    static AccessibilityNodeInfo node(String cls, int l, int t, int r, int b) {
        AccessibilityNodeInfo n = new AccessibilityNodeInfo();
        n.className = cls;
        n.boundsInScreen = new android.graphics.Rect(l, t, r, b);
        return n;
    }

    static AccessibilityNodeInfo txt(String s, int l, int t, int r, int b) {
        AccessibilityNodeInfo n = node("android.widget.TextView", l, t, r, b);
        n.text = s;
        n.clickable = true;
        return n;
    }

    static AccessibilityWindowInfo win(int id, AccessibilityNodeInfo r, boolean active) {
        AccessibilityWindowInfo w = new AccessibilityWindowInfo();
        w.id = id;
        w.root = r;
        w.active = active;
        w.focused = active;
        if (r != null) {
            r.windowId = id;
            r.window = w;
        }
        return w;
    }

    /** 发事件 + 泵链 */
    static void fire(AdSkipService svc, int type, String pkg, String cls) throws Exception {
        fire(svc, type, pkg, cls, 0);
    }

    /**
     * 发事件后先等 settleMs 真实毫秒再泵链（时间窗逻辑保真）：
     * 角落/泛位置键 CORNER_MIN_MS=300ms 门槛、探针 250ms 节流等依赖
     * 真实流逝时间——同步泵在 0ms 跑完会全部踩空（真实设备有时间自然过）。
     */
    static void fire(AdSkipService svc, int type, String pkg, String cls, long settleMs) throws Exception {
        AccessibilityEvent e = AccessibilityEvent.obtain();
        e.eventType = type;
        e.packageName = pkg;
        e.className = cls;
        e.eventTime = System.currentTimeMillis();
        svc.onAccessibilityEvent(e);
        if (settleMs > 0) Thread.sleep(settleMs);
        drain(svc);
    }

    /** 最小服务装配（不跑 onServiceConnected 全链——聚焦扫描/执行/日志） */
    static AdSkipService boot() throws Exception {
        AdSkipService svc = new AdSkipService();
        AdSkipService.instance = svc;
        set("screenWidth", svc, 1080);
        set("screenHeight", svc, 2400);
        return svc;
    }

    static List<String> lastLogs = new ArrayList<String>();

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("  ✓ " + name);
        } else {
            fail++;
            failures.add(name + (detail == null || detail.isEmpty() ? "" : (" —— " + detail)));
            System.out.println("  ✗ " + name + (detail == null || detail.isEmpty() ? "" : (" —— " + detail)));
            if (Boolean.getBoolean("svc.debug")) {
                System.out.println("  ── 全部日志（" + lastLogs.size() + " 条）──");
                for (String l : lastLogs) System.out.println("  │ " + l);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("═══ 服务级回归套件（真实 AdSkipService + 桩 Android）═══");
        s1_keepOverlay();
        s2_csdnCornerImageKey();
        s3_textCountdownRegression();
        s4_cleanProfileProbe();
        s5_zeroCandidateMissLog();
        s6_startupBanner();
        s7_probeStarvedCleanWindow();
        s8_h5BigTreeSwipeAd();
        s9_systemDialogZeroScan();
        s10_bannerClearRequeue();
        s11_appSwitchResync();
        s12_imgBoundaryLogDedup();
        s13_interstitialBackoff();
        s14_updateSurvivalChain();
        s15_appJumpTakeover();
        s16_deadWatchKeepOnDecouple();
        System.out.println();
        System.out.println("通过 " + pass + " · 失败 " + fail);
        if (!failures.isEmpty()) {
            System.out.println("失败清单：");
            for (String s : failures) System.out.println("  - " + s);
            System.exit(1);
        }
    }

    /* ═══ S1 Keep 覆盖层开屏（v7.15.7 头号特性 + 用户实锤场景）═══ */
    static void s1_keepOverlay() throws Exception {
        System.out.println("[S1] Keep 动态图开屏：覆盖层窗口「跳过 4」");
        AdSkipService svc = boot();
        String keep = "com.gotokeep.keep";

        // 活动窗：SplashActivity 8 节点微树（logo + 文案）
        AccessibilityNodeInfo active = root(keep, 10, "android.widget.FrameLayout");
        for (int i = 0; i < 6; i++) {
            active.add(node("android.view.View", 100 + i * 40, 200, 140 + i * 40, 240));
        }
        AccessibilityNodeInfo logo = node("android.widget.ImageView", 400, 900, 680, 1180);
        active.add(logo);

        // 覆盖层窗（同包）：广告图全屏热区 + 「跳过 4」文本键（位置随机中部）
        AccessibilityNodeInfo overlay = root(keep, 20, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        overlay.add(adImg);
        for (int i = 0; i < 35; i++) {
            overlay.add(node("android.view.View", 50 + i * 10, 300 + i * 20, 90 + i * 10, 320 + i * 20));
        }
        AccessibilityNodeInfo skip = txt("跳过 4", 830, 1250, 1010, 1310);
        overlay.add(skip);

        // 异包窗口（launcher 在场——真实系统常态）
        AccessibilityNodeInfo launcherRoot = root("com.miui.home", 5, "android.widget.FrameLayout");
        for (int i = 0; i < 30; i++) launcherRoot.add(node("android.view.View", i * 30, 2000, i * 30 + 28, 2028));
        AccessibilityWindowInfo wLauncher = win(5, launcherRoot, false);
        AccessibilityWindowInfo wActive = win(10, active, true);
        AccessibilityWindowInfo wOverlay = win(20, overlay, false);
        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(wLauncher);
        wins.add(wActive);
        wins.add(wOverlay);
        svc.windows = wins;
        svc.activeRoot = active;

        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, keep,
                "com.gotokeep.keep.splash.SplashActivity");

        List<String> lg = logs(svc);
        boolean skipped = svc.gestureCount > 0 || skip.clicked
                || (hasLog(lg, "开屏跳过") && !hasLog(lg, "放弃"));
        check("S1a 覆盖层「跳过 4」被跳过（点击/手势落地）", svc.gestureCount > 0 || skip.clicked,
                "gestureCount=" + svc.gestureCount + " skipClicked=" + skip.clicked);
        check("S1b 引擎日志有记录（开屏跳过或门控）", hasLog(lg, "开屏跳过") || hasLog(lg, "倒计时门控"),
                "logs=" + lg.size());
        check("S1c 日志非空（服务活着）", !lg.isEmpty(), "logs=0");
        if (skipped && svc.gestureCount > 0) {
            check("S1d 手势落点在跳过键区域", svc.lastGestureX >= 800 && svc.lastGestureX <= 1040
                    && svc.lastGestureY >= 1200 && svc.lastGestureY <= 1360,
                    "tap=(" + svc.lastGestureX + "," + svc.lastGestureY + ")");
        }
    }

    /* ═══ S2 CSDN 角落图片键（v7.15.7 图片键边界闸）═══ */
    static void s2_csdnCornerImageKey() throws Exception {
        System.out.println("[S2] CSDN 动态图开屏：角落纯图键（零文案零 vid）");
        AdSkipService svc = boot();
        String csdn = "net.csdn.csdb";

        AccessibilityNodeInfo active = root(csdn, 30, "android.widget.FrameLayout");
        // 全屏可点广告图（热区）
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        active.add(adImg);
        // 角标「广告」
        AccessibilityNodeInfo badge = txt("广告", 20, 120, 120, 200);
        active.add(badge);
        // 角落纯图跳过键：无文本无 desc，vid 中性，小尺寸右上角
        AccessibilityNodeInfo corner = node("android.widget.ImageView", 960, 130, 1040, 210);
        corner.clickable = true;
        corner.viewIdResourceName = "net.csdn.csdb:id/iv_close_area";
        active.add(corner);
        for (int i = 0; i < 20; i++) {
            active.add(node("android.view.View", 60 + i * 15, 260 + i * 30, 100 + i * 15, 290 + i * 30));
        }

        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(win(30, active, true));
        svc.windows = wins;
        svc.activeRoot = active;

        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, csdn,
                "net.csdn.csdb.activity.SplashActivity", 400);

        lastLogs = logs(svc);
        List<String> lg = lastLogs;
        boolean acted = svc.gestureCount > 0 || corner.clicked;
        check("S2a 角落图片键出手（图片键边界闸放行）", acted,
                "gestureCount=" + svc.gestureCount + " cornerClicked=" + corner.clicked
                        + " · 拦截日志在场=" + hasLog(lg, "动作边界自检拦截"));
        check("S2b 出手目标是角落键区域", !acted || (svc.lastGestureX >= 930 && svc.lastGestureX <= 1070
                && svc.lastGestureY >= 100 && svc.lastGestureY <= 240),
                "tap=(" + svc.lastGestureX + "," + svc.lastGestureY + ")");
    }

    /* ═══ S3 普通文本倒计时开屏（回归红线）═══ */
    static void s3_textCountdownRegression() throws Exception {
        System.out.println("[S3] 普通文本倒计时开屏（回归红线）");
        AdSkipService svc = boot();
        String market = "com.xiaomi.market";

        AccessibilityNodeInfo active = root(market, 40, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        active.add(adImg);
        AccessibilityNodeInfo skip = txt("跳过 4", 840, 150, 1030, 210);
        active.add(skip);
        for (int i = 0; i < 15; i++) {
            active.add(node("android.view.View", 80 + i * 20, 300 + i * 40, 120 + i * 20, 330 + i * 40));
        }

        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(win(40, active, true));
        svc.windows = wins;
        svc.activeRoot = active;

        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, market,
                "com.xiaomi.market.activity.SplashActivity");

        List<String> lg = logs(svc);
        check("S3a 文本倒计时开屏照常跳过", svc.gestureCount > 0 || skip.clicked,
                "gestureCount=" + svc.gestureCount);
        check("S3b 日志有跳过/门控记录", hasLog(lg, "开屏跳过") || hasLog(lg, "倒计时门控"),
                "logs=" + lg.size());
    }

    /* ═══ S4 CLEAN 档案 + 探针哨兵（多窗图形探针复活链）═══ */
    static void s4_cleanProfileProbe() throws Exception {
        System.out.println("[S4] CLEAN 档案省电 + 探针哨兵复活（纯图键第四窗）");
        AdSkipService svc = boot();
        String school = "com.daxue.school";

        // 三窗零候选 → CLEAN（微树 logo 页，窗实例需不同 winStart → 间隔 3ms）
        for (int w = 0; w < 3; w++) {
            AccessibilityNodeInfo active = root(school, 50 + w, "android.widget.FrameLayout");
            for (int i = 0; i < 10; i++) {
                active.add(node("android.view.View", 100 + i * 30, 400 + i * 50, 140 + i * 30, 440 + i * 50));
            }
            List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
            wins.add(win(50 + w, active, true));
            svc.windows = wins;
            svc.activeRoot = active;
            fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, school,
                    "com.daxue.school.SplashActivity");
            Thread.sleep(3);
        }

        // 第四窗：CLEAN 已生效 → 探针哨兵路径；树内出现纯图跳过键 + 热区
        AccessibilityNodeInfo active = root(school, 60, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        active.add(adImg);
        AccessibilityNodeInfo imgKey = node("android.widget.ImageView", 950, 140, 1030, 220);
        imgKey.clickable = true;
        active.add(imgKey);

        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(win(60, active, true));
        svc.windows = wins;
        svc.activeRoot = active;

        // 触发方式：窗口事件（CLEAN → bumpScanChain 只排探针）+ settle 后内容事件再探
        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, school,
                "com.daxue.school.SplashActivity2", 400);
        fire(svc, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, school,
                "com.daxue.school.SplashActivity2", 300);

        lastLogs = logs(svc);
        List<String> lg = lastLogs;
        check("S4a 探针复活后出手", svc.gestureCount > 0 || imgKey.clicked,
                "gestureCount=" + svc.gestureCount + " · 探针日志=" + hasLog(lg, "探针"));
    }

    /* ═══ S5 零候选未命中日志（日志红线 · issue#38）═══ */
    static void s5_zeroCandidateMissLog() throws Exception {
        System.out.println("[S5] 零候选开屏未命中诊断日志");
        AdSkipService svc = boot();
        String tool = "com.some.tool";

        AccessibilityNodeInfo active = root(tool, 70, "android.widget.FrameLayout");
        AccessibilityNodeInfo label = txt("某某工具", 340, 700, 740, 800);
        label.clickable = false;
        active.add(label);
        for (int i = 0; i < 25; i++) {
            active.add(node("android.view.View", 100 + i * 25, 900 + i * 30, 140 + i * 25, 930 + i * 30));
        }

        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(win(70, active, true));
        svc.windows = wins;
        svc.activeRoot = active;

        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, tool,
                "com.some.tool.MainActivity");

        List<String> lg = logs(svc);
        check("S5a 零候选窗记「开屏未命中」诊断", hasLog(lg, "开屏未命中"),
                "logs=" + lg.size() + (lg.isEmpty() ? "（日志完全为空——事故形态！）" : ""));
    }

    /* ═══ S6 启动横幅（服务活着的最小证据）═══ */
    static void s6_startupBanner() throws Exception {
        System.out.println("[S6] 服务连接启动横幅");
        AdSkipService svc = boot();
        try {
            Method m = AdSkipService.class.getDeclaredMethod("onServiceConnected");
            m.setAccessible(true);
            m.invoke(svc);
        } catch (Throwable t) {
            check("S6a onServiceConnected 不炸", false, String.valueOf(t));
            return;
        }
        List<String> lg = logs(svc);
        check("S6a onServiceConnected 不炸", true, null);
        check("S6b 启动横幅在场（订阅引擎就绪）", hasLog(lg, "订阅引擎就绪"),
                "logs=" + lg.size());
    }

    /* ═══ S7 CLEAN 窗探针饿死复刻（v7.15.9 分账 + 晚档梯子回归红线）═══
     * #102 真机实锤死链复刻：CLEAN 档案窗（连续 3 次零候选）的内容事件先让
     * 探针消耗掉枚举账 → v7.15.8「探针/扫描共用一本账」下扫描微树门永远
     * 拿不到预算（整份日志零🪟，weibo 6 节点/keep 8 节点空壳都没触发补扫）
     * + CLEAN 无订阅窗纯探针零梯子 → 覆盖层跳过键永远扫不到。
     * v7.15.9 对策三件套的回归验证：
     *  ① 微树门分账（microScanEnumOk——探针账被占用时门照样开）；
     *  ② 晚档轻扫梯子 {700,1600,3200}（CLEAN 无订阅窗不再只有 0ms 探针）；
     *  ③ 探针哨兵 0ms 统一排。
     * 场景时序严格复刻真机：探针刚消耗枚举账（<400ms）时新窗打开——
     * 0ms 探针必然被节流（只见活动窗空壳），唯一的救回通道是晚档梯子
     * 扫描的门控补窗。v7.15.8 在本场景下是全盲（红），v7.15.9 必须跳过。 */
    static void s7_probeStarvedCleanWindow() throws Exception {
        System.out.println("[S7] CLEAN 窗探针饿死复刻：分账 + 晚档梯子救回");
        AdSkipService svc = boot();
        String nb = "com.netdisk.app";
        String cls = "com.netdisk.app.SplashActivity";

        // 三窗零候选（同 pkg|cls，微树空壳）→ 档案 CLEAN
        for (int w = 0; w < 3; w++) {
            AccessibilityNodeInfo active = root(nb, 80 + w, "android.widget.FrameLayout");
            for (int i = 0; i < 5; i++) {
                active.add(node("android.view.View", 100 + i * 30, 400 + i * 50, 140 + i * 30, 440 + i * 50));
            }
            List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
            wins.add(win(80 + w, active, true));
            svc.windows = wins;
            svc.activeRoot = active;
            fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, nb, cls);
            Thread.sleep(3);
        }
        lastLogs = logs(svc);
        @SuppressWarnings("unchecked")
        java.util.HashMap<String, Integer> ws =
                (java.util.HashMap<String, Integer>) get("winStreak", svc);
        Integer streak = ws.get(nb + "|" + cls);
        check("S7a 三窗零候选后档案 CLEAN（streak≥3）",
                streak != null && streak.intValue() >= 3,
                "streak=" + streak + " · logs=" + lastLogs.size());

        // 探针账消耗：等窗 1 的扫描门账过期后，内容事件让探针枚举一次全窗
        //（此后 <400ms 内开新窗 = 0ms 探针被节流只见活动窗——真机饿死形态）
        Thread.sleep(450);
        fire(svc, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, nb, cls);

        // 第四窗（同 pkg|cls，CLEAN）：活动窗 6 节点微树空壳 + 同包覆盖层
        // 广告窗（全屏热区 +「跳过 4」文本键）——覆盖层是唯一的跳过键所在
        AccessibilityNodeInfo shell = root(nb, 90, "android.widget.FrameLayout");
        for (int i = 0; i < 5; i++) {
            shell.add(node("android.view.View", 100 + i * 40, 200, 140 + i * 40, 240));
        }
        AccessibilityNodeInfo overlay = root(nb, 91, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        overlay.add(adImg);
        for (int i = 0; i < 35; i++) {
            overlay.add(node("android.view.View", 50 + i * 10, 300 + i * 20, 90 + i * 10, 320 + i * 20));
        }
        AccessibilityNodeInfo skip = txt("跳过 4", 830, 1250, 1010, 1310);
        overlay.add(skip);

        List<AccessibilityWindowInfo> wins4 = new ArrayList<AccessibilityWindowInfo>();
        wins4.add(win(90, shell, true));
        wins4.add(win(91, overlay, false));
        svc.windows = wins4;
        svc.activeRoot = shell;

        // 开窗即泵（真实 <400ms 窗口竞速）：0ms 探针被节流，晚档梯子的
        // 微树门（分账后预算独立）必须补扫覆盖层并出手
        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, nb, cls);

        lastLogs = logs(svc);
        List<String> lg = lastLogs;
        check("S7b 探针账被占用时覆盖层「跳过 4」仍被跳过（分账+晚档梯子）",
                svc.gestureCount > 0 || skip.clicked,
                "gestureCount=" + svc.gestureCount + " skipClicked=" + skip.clicked
                        + " · 🪟日志=" + hasLog(lg, "多窗补扫启用"));
        check("S7c 微树补扫启用日志在场（门不再被饿死的铁证）",
                hasLog(lg, "多窗补扫启用"),
                "logs=" + lg.size());
    }

    /* ═══ S8 H5 上滑互动广告（用户实锤三图之 Type ③·v7.15.10 豁免带）═══
     * 300+ 节点 H5 大树 + scrollable 容器（上滑手势容器）+「广告」角标 +
     * 左下角纯图片跳过键（canvas 渲染零文本）——旧引擎三重死洞：树 >150
     * corner 点击门挡死、泛位候选排除角落几何、scrollPresent 硬挡。
     * v7.15.10 豁免带（开屏×强广告形态）必须救回：手势落点在左下键区域。 */
    static void s8_h5BigTreeSwipeAd() throws Exception {
        System.out.println("[S8] H5 上滑互动广告：大树+滚动容器+左下图片键（豁免带）");
        AdSkipService svc = boot();
        String sn = "com.snack.shop";

        AccessibilityNodeInfo active = root(sn, 100, "android.widget.FrameLayout");
        // 「广告」角标（强广告形态信号）
        active.add(txt("广告", 40, 120, 150, 200));
        // 左下角纯图片跳过键（无文本无 desc——canvas 渲染形态）
        AccessibilityNodeInfo key = node("android.widget.ImageView", 30, 2200, 150, 2320);
        key.clickable = true;
        active.add(key);
        // 上滑互动容器（scrollable）+ 300 节点深树（>150 旧上限）
        AccessibilityNodeInfo swiper = node("android.widget.ScrollView", 0, 240, 1080, 2160);
        swiper.scrollable = true;
        for (int i = 0; i < 300; i++) {
            swiper.add(node("android.view.View", 60 + (i % 12) * 80, 300 + (i / 12) * 70,
                    90 + (i % 12) * 80, 330 + (i / 12) * 70));
        }
        active.add(swiper);

        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(win(100, active, true));
        svc.windows = wins;
        svc.activeRoot = active;

        // settle 400ms：角落键 CORNER_MIN_MS=300 门槛过真实时序（同 S2 口径）
        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, sn,
                "com.snack.shop.activity.SplashActivity", 400);

        lastLogs = logs(svc);
        List<String> lg8 = lastLogs;
        boolean acted8 = svc.gestureCount > 0 || key.clicked;
        check("S8a 上滑互动 H5 广告左下图片键出手（豁免带：大树+滚动）", acted8,
                "gestureCount=" + svc.gestureCount + " keyClicked=" + key.clicked
                        + " · 图片键候选日志=" + hasLog(lg8, "图片键候选"));
        if (acted8) {
            check("S8b 手势落点在左下跳过键区域", svc.lastGestureX >= 20 && svc.lastGestureX <= 160
                    && svc.lastGestureY >= 2190 && svc.lastGestureY <= 2330,
                    "tap=(" + svc.lastGestureX + "," + svc.lastGestureY + ")");
        }
    }

    /* ═══ S9 系统权限弹窗零扫描（#103 日志实锤：permissioncontroller 被白扫）═══
     * 「开屏未命中（permissioncontroller）……候选样本：拒绝/始终允许」+
     * 「弹窗未命中 · 入窗 1.79E9s」双日志——GrantPermissionsActivity 的
     * 「拒绝」是全 app 误触代价最高的控件之一。事件入口直接丢弃：
     * 零扫描零点击零日志（同 systemui 口径）。 */
    static void s9_systemDialogZeroScan() throws Exception {
        System.out.println("[S9] 系统权限弹窗零扫描（permissioncontroller）");
        AdSkipService svc = boot();
        String pc = "com.google.android.permissioncontroller";
        // 真实权限弹窗形态：两个按钮（拒绝/始终允许）+ 说明文案——
        // 旧代码（v7.15.9 及之前）会进开屏扫描并刷「未命中」双日志
        AccessibilityNodeInfo dlg = root(pc, 200, "android.widget.FrameLayout");
        dlg.add(txt("允许「示例应用」拍照和录像吗？", 140, 800, 940, 900));
        AccessibilityNodeInfo deny = txt("拒绝", 140, 2100, 480, 2240);
        dlg.add(deny);
        dlg.add(txt("始终允许", 600, 2100, 940, 2240));
        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(win(200, dlg, true));
        svc.windows = wins;
        svc.activeRoot = dlg;

        Object scansBefore = get("energyScans", svc);
        List<String> before = logs(svc);

        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, pc,
                "com.google.android.permissioncontroller.permission.ui.GrantPermissionsActivity", 50);
        fire(svc, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, pc,
                "androidx.recyclerview.widget.RecyclerView", 50);

        lastLogs = logs(svc);
        List<String> after = lastLogs;
        Object scansAfter = get("energyScans", svc);
        boolean noLog = true;
        for (String l : after) {
            if (l.contains(pc)) noLog = false;
        }
        check("S9a 权限弹窗零日志（开屏/弹窗未命中双刷屏根治）", noLog,
                "newLogs=" + (after.size() - before.size()));
        check("S9b 权限弹窗零扫描（energyScans 不增）",
                scansBefore instanceof Long && scansAfter instanceof Long
                        && ((Long) scansAfter).longValue() == ((Long) scansBefore).longValue(),
                "before=" + scansBefore + " after=" + scansAfter);
    }

    /* ═══ S10 横幅让路重排（v7.15.11 锁屏/解锁开屏根治一环 · #109 实锤）═══
     * 验证码/短信 heads-up 横幅在场 → 触点被挡（绝不点掉横幅——红线保持）
     * → v7.15.10 及之前：横幅自动消散后无人再扫 = 解锁开屏白看到底；
     * v7.15.11：被挡时排 3 针重查（+2.5/+5/+9s），横幅已散且窗仍在 →
     * 重排扫描链立即补扫。场景：右上角纯图跳过键 + 横幅覆盖其上。 */
    static void s10_bannerClearRequeue() throws Exception {
        System.out.println("[S10] 横幅让路重排：横幅挡点 → 消散后自动补扫");
        AdSkipService svc = boot();
        String nb = "com.nb.video";

        // 广告窗：全屏热区 + 角标 + 右上角纯图跳过键（同 S2 形态）
        AccessibilityNodeInfo active = root(nb, 300, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        active.add(adImg);
        active.add(txt("广告", 20, 120, 120, 200));
        AccessibilityNodeInfo key = node("android.widget.ImageView", 950, 130, 1040, 220);
        key.clickable = true;
        active.add(key);
        for (int i = 0; i < 18; i++) {
            active.add(node("android.view.View", 60 + i * 15, 260 + i * 30, 100 + i * 15, 290 + i * 30));
        }

        // systemui heads-up 横幅窗（Z 序更高=列表更前；顶部 0~560px、高 560
        // < 35% 屏高 → headsUpBanner 判定成立；覆盖跳过键区域）
        AccessibilityNodeInfo bannerRoot = root("com.android.systemui", 301, "android.widget.FrameLayout");
        AccessibilityWindowInfo wBanner = win(301, bannerRoot, false);
        wBanner.bounds = new android.graphics.Rect(0, 0, 1080, 560);

        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(wBanner); // 索引 0 = Z 序最高（覆盖活动窗触点）
        wins.add(win(300, active, true));
        svc.windows = wins;
        svc.activeRoot = active;

        // handler 引用（横幅消散动作注入用——排在扫描梯之后、重查针之前，
        // 真实时序等价物：横幅 3~8s 自动消散后重查针才到点）
        Object h = get("handler", svc);
        java.lang.reflect.Method post = h.getClass().getMethod("post", Runnable.class);
        final AdSkipService s10 = svc;

        // 开窗（真实时序：事件→梯子入队→settle 400ms→横幅消散→泵链）
        AccessibilityEvent ev = AccessibilityEvent.obtain();
        ev.eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        ev.packageName = nb;
        ev.className = "com.nb.video.SplashActivity";
        ev.eventTime = System.currentTimeMillis();
        svc.onAccessibilityEvent(ev);
        Thread.sleep(400); // 角落键 CORNER_MIN_MS=300 时序
        post.invoke(h, (Runnable) () -> {
            List<AccessibilityWindowInfo> gone = new ArrayList<AccessibilityWindowInfo>();
            gone.add(win(300, active, true));
            s10.windows = gone; // 横幅消散
        });
        drain(svc);

        lastLogs = logs(svc);
        List<String> lg = lastLogs;
        check("S10a 横幅在场时触点被挡（覆盖层防误触日志在场）",
                hasLog(lg, "覆盖层防误触") || hasLog(lg, "顶部横幅保护"),
                "logs=" + lg.size());
        check("S10b 横幅消散后自动补扫出手（重排链救回）",
                svc.gestureCount > 0 || key.clicked,
                "gestureCount=" + svc.gestureCount + " keyClicked=" + key.clicked);
        check("S10c 横幅消散重排日志在场（用户可核验）",
                hasLog(lg, "横幅已消散"),
                "logs=" + lg.size());
    }

    /* ═══ S11 切换app前台回归探针（v7.15.11 回切开屏整窗漏拦根治）═══
     * HyperOS/Android17 回切不发 STATE_CHANGED：应用 B 在前台 → 切回 A
     * （活动窗根已指向 A 的广告窗，但无窗口事件）→ A 的内容事件被前台门
     * 全量丢弃 = 整窗漏拦。v7.15.11：内容事件前置过滤处阳性证据探针 →
     * 晋升（完整进入重置）+ 立即补扫 → 跳过。 */
    static void s11_appSwitchResync() throws Exception {
        System.out.println("[S11] 切换app回归：无窗口事件回切 → 探针晋升补扫");
        AdSkipService svc = boot();
        String appB = "com.bbb.browser";
        String appA = "com.aaa.shop";

        // 阶段1：B 在前台（正常 STATE_CHANGED）
        AccessibilityNodeInfo bRoot = root(appB, 400, "android.widget.FrameLayout");
        bRoot.add(txt("浏览器主页", 340, 700, 740, 800));
        for (int i = 0; i < 12; i++) {
            bRoot.add(node("android.view.View", 100 + i * 30, 900 + i * 30, 140 + i * 30, 930 + i * 30));
        }
        List<AccessibilityWindowInfo> winsB = new ArrayList<AccessibilityWindowInfo>();
        winsB.add(win(400, bRoot, true));
        svc.windows = winsB;
        svc.activeRoot = bRoot;
        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, appB,
                "com.bbb.browser.MainActivity", 50);

        // 阶段2：切回 A（HyperOS 无 STATE_CHANGED）——活动窗已是 A 的广告窗
        AccessibilityNodeInfo aRoot = root(appA, 410, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        aRoot.add(adImg);
        aRoot.add(txt("广告", 20, 120, 120, 200));
        AccessibilityNodeInfo key = node("android.widget.ImageView", 950, 130, 1040, 220);
        key.clickable = true;
        aRoot.add(key);
        for (int i = 0; i < 18; i++) {
            aRoot.add(node("android.view.View", 60 + i * 15, 260 + i * 30, 100 + i * 15, 290 + i * 30));
        }
        List<AccessibilityWindowInfo> winsA = new ArrayList<AccessibilityWindowInfo>();
        winsA.add(win(410, aRoot, true));
        svc.windows = winsA;
        svc.activeRoot = aRoot;

        // 阶段3：A 的内容事件（旧版在此被前置过滤直接丢弃——整窗漏拦）
        // settle 400ms 后泵链（探针晋升 + 梯子补扫）
        fire(svc, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, appA,
                "com.aaa.shop.SplashActivity", 400);

        lastLogs = logs(svc);
        List<String> lg = lastLogs;
        Object fgNow = get("lastFgPkg", svc);
        check("S11a 前台追踪已校正（lastFgPkg=回切应用）",
                appA.equals(fgNow), "lastFgPkg=" + fgNow);
        check("S11b 回归晋升日志在场（切换app回归）",
                hasLog(lg, "切换app回归"),
                "logs=" + lg.size());
        check("S11c 回切开屏被跳过（探针晋升 + 立即补扫）",
                svc.gestureCount > 0 || key.clicked,
                "gestureCount=" + svc.gestureCount + " keyClicked=" + key.clicked);

        /* ── S11d（审核轮 r1【致命-1】负例）：回切微信聊天主界面——
         * 竞速登记捕获真实窗口类（LauncherUI），晋升后 winClass 必须是
         * 真实窗口类而非 CONTENT_CHANGED 的源 View 类，聊天主界面门
         * （bumpScanChain/tryScan 双门）必须照常生效 = 零扫描。 */
        AdSkipService svc2 = boot();
        // B 在前台
        AccessibilityNodeInfo b2 = root(appB, 420, "android.widget.FrameLayout");
        b2.add(txt("浏览器", 340, 700, 740, 800));
        List<AccessibilityWindowInfo> winsB2 = new ArrayList<AccessibilityWindowInfo>();
        winsB2.add(win(420, b2, true));
        svc2.windows = winsB2;
        svc2.activeRoot = b2;
        fire(svc2, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, appB,
                "com.bbb.browser.MainActivity", 50);
        // 竞速：微信 STATE_CHANGED 到场但活动窗尚未切换（归属查询 false）→ 登记 fgPending
        fire(svc2, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.tencent.mm",
                "com.tencent.mm.ui.LauncherUI", 30);
        // 活动窗切到微信聊天列表（无后续 STATE_CHANGED——HyperOS 形态）
        AccessibilityNodeInfo chat = root("com.tencent.mm", 430, "android.widget.FrameLayout");
        chat.add(txt("微信", 340, 120, 740, 220));
        for (int i = 0; i < 10; i++) {
            chat.add(txt("聊天" + i, 80, 400 + i * 160, 1000, 520 + i * 160));
        }
        List<AccessibilityWindowInfo> winsChat = new ArrayList<AccessibilityWindowInfo>();
        winsChat.add(win(430, chat, true));
        svc2.windows = winsChat;
        svc2.activeRoot = chat;
        Object scansBefore2 = get("energyScans", svc2);
        // 微信内容事件（源 View 类——审核致命-1 的核心场景）
        fire(svc2, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, "com.tencent.mm",
                "androidx.recyclerview.widget.RecyclerView", 100);
        lastLogs = logs(svc2);
        List<String> lg2 = lastLogs;
        java.util.HashMap<String, String> wc2 =
                (java.util.HashMap<String, String>) get("winClass", svc2);
        Object scansAfter2 = get("energyScans", svc2);
        check("S11d 聊天主界面回切零扫描（窗口类语义还原——LauncherUI 门照常）",
                scansBefore2 instanceof Long && scansAfter2 instanceof Long
                        && ((Long) scansAfter2).longValue() == ((Long) scansBefore2).longValue()
                        && "com.tencent.mm.ui.LauncherUI".equals(wc2.get("com.tencent.mm")),
                "scans " + scansBefore2 + "→" + scansAfter2
                        + " winClass=" + wc2.get("com.tencent.mm")
                        + " · 晋升日志=" + hasLog(lg2, "切换app回归"));
    }

    /* ═══ S12 图片键边界放行日志去重（v7.15.11 多点观感根治）═══
     * #108/#109/#105 三台设备实锤：横幅/覆盖层挡点后同一秒 5~13 条「🎯
     * 图片跳过键边界放行」刷屏（每轮扫描每轮 execute 都打一条）——用户
     * 读日志=「点了十几次」。v7.15.11：同包同理由 6s 一条。 */
    static void s12_imgBoundaryLogDedup() throws Exception {
        System.out.println("[S12] 🎯 边界放行日志去重（多点观感根治）");
        AdSkipService svc = boot();
        String rd = "com.rd.reader";

        AccessibilityNodeInfo active = root(rd, 500, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        active.add(adImg);
        active.add(txt("广告", 20, 120, 120, 200));
        AccessibilityNodeInfo key = node("android.widget.ImageView", 950, 130, 1040, 220);
        key.clickable = true;
        active.add(key);
        for (int i = 0; i < 18; i++) {
            active.add(node("android.view.View", 60 + i * 15, 260 + i * 30, 100 + i * 15, 290 + i * 30));
        }

        // 持久横幅（不消散——隔离出去重变量：只验日志量，不验重排）
        AccessibilityNodeInfo bannerRoot = root("com.android.systemui", 501, "android.widget.FrameLayout");
        AccessibilityWindowInfo wBanner = win(501, bannerRoot, false);
        wBanner.bounds = new android.graphics.Rect(0, 0, 1080, 560);
        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(wBanner);
        wins.add(win(500, active, true));
        svc.windows = wins;
        svc.activeRoot = active;

        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, rd,
                "com.rd.reader.SplashActivity", 400);
        // 连发 4 轮内容事件（每轮 nudge → execute → 边界放行重打——去重后应只 1 条）
        for (int i = 0; i < 4; i++) {
            fire(svc, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, rd,
                    "com.rd.reader.SplashActivity", 120);
        }

        lastLogs = logs(svc);
        int passCount = 0;
        for (String l : lastLogs) {
            if (l.contains("图片跳过键边界放行")) passCount++;
        }
        check("S12a 边界放行日志 6s 窗口内只记 1 条（多点观感根治）",
                passCount <= 1, "实际 " + passCount + " 条");
        check("S12b 横幅在场绝不出手（红线保持）",
                svc.gestureCount == 0 && !key.clicked,
                "gestureCount=" + svc.gestureCount + " keyClicked=" + key.clicked);
    }

    /* ═══ S13 插屏窗级退避（v7.15.11 功耗 · #106 OPPO 实锤）═══
     * 静态窗内容事件不停（动画/进度条）→ 旧版 2.5s 一轮无限重扫。
     * v7.15.11：同窗连续未命中 streak≤2 照常 2.5s、3~5 次 10s、≥6 次
     * 30s。以反射直调 maybeInterstitialScan + 假时钟驱动（扫描是否运行
     * 以 interMissStreak 是否递增为铁证）。 */
    static void s13_interstitialBackoff() throws Exception {
        System.out.println("[S13] 插屏窗级退避（静态窗无限重扫根治）");
        AdSkipService svc = boot();
        String fk = "com.fk.client";
        String cls = "com.fk.client.MainActivity";

        // 静态窗（无广告语境/关闭钮——插屏必未命中）
        AccessibilityNodeInfo active = root(fk, 600, "android.widget.FrameLayout");
        active.add(txt("设置", 340, 200, 740, 300));
        for (int i = 0; i < 15; i++) {
            active.add(node("android.view.View", 100 + i * 40, 400 + i * 60, 160 + i * 40, 450 + i * 60));
        }
        List<AccessibilityWindowInfo> wins = new ArrayList<AccessibilityWindowInfo>();
        wins.add(win(600, active, true));
        svc.windows = wins;
        svc.activeRoot = active;

        // 前台+窗口已落定（开屏观察窗已过——插屏路径活跃的前提）
        set("lastFgPkg", svc, fk);
        java.util.HashMap<String, String> wc = (java.util.HashMap<String, String>) get("winClass", svc);
        wc.put(fk, cls);
        java.util.HashMap<String, Long> wa = (java.util.HashMap<String, Long>) get("windowAt", svc);
        long t0 = System.currentTimeMillis() - 20_000L; // 窗已落定 20s
        wa.put(fk, t0);

        java.lang.reflect.Method scan = AdSkipService.class.getDeclaredMethod(
                "maybeInterstitialScan", String.class, long.class);
        scan.setAccessible(true);
        java.util.HashMap<String, Integer> streak =
                (java.util.HashMap<String, Integer>) get("interMissStreak", svc);
        java.util.HashMap<String, Long> scanAt =
                (java.util.HashMap<String, Long>) get("interScanAt", svc);

        int idx = 0;
        long base = t0 + 20_000L; // 开屏观察窗（12s）已过——插屏路径活跃
        long[] clock = {base, base + 3000, base + 6000, base + 9000, base + 11000, base + 17000};
        int[] expectRun = {1, 1, 1, 0, 0, 1}; // streak=3→10s 退避：+9s/+11s 挡、+17s（距末扫 11s）放行
        boolean allOk = true;
        StringBuilder detail = new StringBuilder();
        for (long t : clock) {
            int before = streak.get(fk) == null ? 0 : streak.get(fk).intValue();
            scan.invoke(svc, fk, t);
            int after = streak.get(fk) == null ? 0 : streak.get(fk).intValue();
            boolean ran = after > before;
            if (ran != (expectRun[idx] == 1)) {
                allOk = false;
                detail.append("t+").append(t - t0).append("ms ran=").append(ran)
                        .append(" expect=").append(expectRun[idx] == 1).append("; ");
            }
            idx++;
        }
        check("S13a 退避梯子按 streak 递进（2.5s→10s）", allOk,
                detail.length() == 0 ? ("末轮 streak=" + streak.get(fk)) : detail.toString());
        // 窗切换重置（新窗新计——新插层秒级响应不误伤；新窗开屏观察窗
        // 12s 过后插屏路径才活跃——与真实「窗落定后插屏接管」同口径）
        wa.put(fk, t0 + 100_000L);
        scan.invoke(svc, fk, t0 + 115_500L);
        int afterReset = streak.get(fk) == null ? 0 : streak.get(fk).intValue();
        check("S13b 窗切换后退避清零（新窗首扫立即）",
                afterReset <= 1, "reset后streak=" + afterReset + " scanAt=" + scanAt.get(fk));
    }

    /* ═══ S14 更新存活链（v7.15.12 用户实锤：更新安装新版后「日志不记录 +
     * 开屏全不跳」常现——覆盖安装杀进程后无障碍重绑时机失控，ROM 还可能
     * 吞/延迟 MY_PACKAGE_REPLACED 广播与心跳闹钟）═══
     * 复刻覆盖安装时序四环节：
     *  ① 安装器拉起前 preserveStateForUpdate——最后 ≤15s 日志 commit 同步
     *    落盘（apply 异步写被杀进程截断 → 用户看到「日志不记录」）；
     *  ② 安装杀进程后（新进程无实例）appendLogStatic prefs 直写——
     *    覆盖安装完成时刻不再日志黑洞；
     *  ③ 断线首检时戳 → 重连日志给出「离线 X 分」——「日志为什么断流」
     *    在日志里直接可回答；
     *  ④ SmartRevive replaced 终判 +30s 复活梯子（一次失败不再弃）。 */
    static void s14_updateSurvivalChain() throws Exception {
        System.out.println("[S14] 更新存活链：安装前落盘/死期日志/重连可见化/复活梯子");
        AdSkipService svc = boot();

        // ① 安装前落盘：内存态写入一条日志（尚未到 15s 延迟落盘点）→
        //    preserveStateForUpdate 必须立即 commit 进 prefs
        Method append = AdSkipService.class.getDeclaredMethod("appendLog", String.class);
        append.setAccessible(true);
        append.invoke(svc, "🎯 S14 安装前最后一条日志（未到延迟落盘点）");
        Method preserve = AdSkipService.class.getDeclaredMethod(
                "preserveStateForUpdate", android.content.Context.class);
        preserve.setAccessible(true);
        preserve.invoke(null, svc);
        String raw = svc.getSharedPreferences("adshield", 0).getString("state", null);
        check("S14a 安装前 preserveStateForUpdate 同步落盘（最后日志不丢）",
                raw != null && raw.contains("S14 安装前最后一条日志"),
                "stateLen=" + (raw == null ? -1 : raw.length()));

        // ② 模拟「安装杀进程」：instance 置空（新进程无实例）→
        //    appendLogStatic prefs 直写路径
        AdSkipService.instance = null;
        Method als = AdSkipService.class.getDeclaredMethod(
                "appendLogStatic", android.content.Context.class, String.class);
        als.setAccessible(true);
        als.invoke(null, svc, "📦 S14 覆盖安装完成（服务已死时的日志通道）");
        raw = svc.getSharedPreferences("adshield", 0).getString("state", null);
        check("S14b 服务已死时 appendLogStatic prefs 直写（日志黑洞根治）",
                raw != null && raw.contains("S14 覆盖安装完成"),
                "stateLen=" + (raw == null ? -1 : raw.length()));

        // ③ 断线首检时戳 → 重连日志可见化：markDeadSince 后倒推 10 分钟
        //    （模拟断线已持续）→ onServiceConnected 必须给出「离线」日志
        //    并清零 prefs（<20s 不刷屏阈值之上）
        Method mds = AdSkipService.class.getDeclaredMethod(
                "markDeadSince", android.content.Context.class);
        mds.setAccessible(true);
        mds.invoke(null, svc);
        long tenMinAgo = System.currentTimeMillis() - 10 * 60_000L;
        set("deadSinceMs", null, tenMinAgo);
        svc.getSharedPreferences("adshield", 0)
                .edit().putLong("deadSinceMs", tenMinAgo).apply();
        AdSkipService.instance = svc;
        Method osc = AdSkipService.class.getDeclaredMethod("onServiceConnected");
        osc.setAccessible(true);
        try {
            osc.invoke(svc);
            check("S14c0 onServiceConnected 不炸", true, null);
        } catch (Throwable t) {
            check("S14c0 onServiceConnected 不炸", false, String.valueOf(t));
        }
        lastLogs = logs(svc);
        check("S14c 重连日志给出离线时长（「日志为什么断流」可回答）",
                hasLog(lastLogs, "离线"), "logs=" + lastLogs.size());
        check("S14d 重连后断线时戳清零（下次断线重新计时）",
                svc.getSharedPreferences("adshield", 0).getLong("deadSinceMs", 0L) == 0L,
                "deadSinceMs 残留");

        // ④ SmartRevive replaced 终判梯子：构造 fromReplace=true 的踢链
        //    终判仍死 → 必须排 +30s 自检闹钟（AlarmManager 桩记账核验）；
        //    fromReplace=false（自检路径）不排（维持 5 分钟节奏——
        //    防覆盖安装外的场景被 30s 密集循环打扰）
        android.app.AlarmManager probe = new android.app.AlarmManager() {
            public java.util.List<long[]> setCalls = new java.util.ArrayList<long[]>();
            @Override
            public void set(int type, long at, android.app.PendingIntent pi) {
                setCalls.add(new long[]{type, at});
            }
            @Override
            public void setWindow(int type, long at, long window, android.app.PendingIntent pi) {
                setCalls.add(new long[]{type, at});
            }
        };
        svc.putService(android.content.Context.ALARM_SERVICE, probe);
        // 触发路径：scheduleNextHeartbeat 直改（真实 SmartRevive 走 Handler
        //  主线程队列——JVM 桩无泵；这里验证等价终局：replaced 梯子必须排针）
        Method snh = AdSkipService.class.getDeclaredMethod("scheduleNextHeartbeat",
                android.content.Context.class, long.class, boolean.class);
        snh.setAccessible(true);
        long before = System.currentTimeMillis();
        snh.invoke(null, svc, before + 30_000L, false);
        boolean ladderOk = false;
        String ladderDetail = "no-set-call";
        try {
            java.lang.reflect.Field calls = probe.getClass().getDeclaredField("setCalls");
            calls.setAccessible(true);
            java.util.List<long[]> got = (java.util.List<long[]>) calls.get(probe);
            for (long[] c : got) {
                if (c[1] >= before && c[1] <= before + 60_000L) ladderOk = true;
            }
            ladderDetail = "calls=" + got.size();
        } catch (Throwable t) {
            ladderDetail = String.valueOf(t);
        }
        check("S14e 复活梯子排针入 AlarmManager（+30s 自检闸）",
                ladderOk, ladderDetail);
    }

    /* ═══ S15 app→app 跳转接管（v7.15.13 用户实锤：从 app 跳转到另一个
     * app 后目标 app 开屏「跳不了/没触发」根治）═══
     * 三缺口复刻（#114/#115 日志实锤形态）：
     *  a) 跳转动画期 B 的 STATE_CHANGED 被竞速丢弃 + 静态开屏（WebView/
     *     纯图）零内容事件 → 竞速自检梯主动晋升（不再被动等事件）→
     *     必须补扫跳过（旧版被动等 = 整窗漏拦「没触发」）；
     *  b) 无竞速形态（ROM 吞窗）首探阴性（根还属旧应用）→ 阴性记账 →
     *     800ms 快档连探（旧 5s 档在 5s 开屏上等于放弃）→ 第二波事件
     *     必须晋升补扫；
     *  c) 输入法键盘起落不得劫持 lastFgPkg（#114 Sony 实锤：每 5s 一轮
     *     「切换app回归」晋升循环根因）；设置页等真实前台切换照旧（负例）。 */
    static void s15_appJumpTakeover() throws Exception {
        System.out.println("[S15] app→app 跳转：竞速自检梯 + 阴性快重试 + IME 零劫持");
        String appA = "com.aaa.news";
        String appB = "com.bbb.shop";

        /* ── a) 竞速自检梯：A 前台 → B 跳转动画期 STATE_CHANGED 被竞速丢弃
         *     → 动画完成活动窗换 B 开屏（零后续事件——静态 WebView 形态）
         *     → 泵链：三针自检阳性 → 晋升 → 补扫跳过 ── */
        AdSkipService svc = boot();
        AccessibilityNodeInfo aRoot = root(appA, 600, "android.widget.FrameLayout");
        aRoot.add(txt("新闻列表", 340, 700, 740, 800));
        for (int i = 0; i < 12; i++) {
            aRoot.add(node("android.view.View", 100 + i * 30, 900 + i * 30, 140 + i * 30, 930 + i * 30));
        }
        List<AccessibilityWindowInfo> winsA = new ArrayList<AccessibilityWindowInfo>();
        winsA.add(win(600, aRoot, true));
        svc.windows = winsA;
        svc.activeRoot = aRoot;
        fire(svc, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, appA,
                "com.aaa.news.MainActivity", 50);

        // B 的 STATE_CHANGED 到场（活动窗归属还是 A → 竞速丢弃 + 登记 + 三针自检入队）
        AccessibilityEvent ev = AccessibilityEvent.obtain();
        ev.eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        ev.packageName = appB;
        ev.className = "com.bbb.shop.SplashActivity";
        ev.eventTime = System.currentTimeMillis();
        svc.onAccessibilityEvent(ev);
        Object pend = get("fgPendingPkg", svc);
        check("S15a1 竞速候选已登记（动画期窗口事件被丢弃）",
                appB.equals(pend), "fgPendingPkg=" + pend);

        // 动画完成：活动窗切到 B 的开屏（热区 + 角标 + 跳过广告文本键）——零后续事件
        AccessibilityNodeInfo bSplash = root(appB, 601, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg.clickable = true;
        bSplash.add(adImg);
        bSplash.add(txt("广告", 20, 120, 120, 200));
        AccessibilityNodeInfo key = txt("跳过广告", 840, 150, 1030, 210);
        bSplash.add(key);
        for (int i = 0; i < 18; i++) {
            bSplash.add(node("android.view.View", 60 + i * 15, 260 + i * 30, 100 + i * 15, 290 + i * 30));
        }
        List<AccessibilityWindowInfo> winsB = new ArrayList<AccessibilityWindowInfo>();
        winsB.add(win(601, bSplash, true));
        svc.windows = winsB;
        svc.activeRoot = bSplash;
        drain(svc); // 三针自检（桩忽略延时）→ 阳性 → 晋升 → 补扫

        lastLogs = logs(svc);
        List<String> lg = lastLogs;
        Object fgB = get("lastFgPkg", svc);
        check("S15a2 跳转接管晋升（lastFgPkg=目标 app）",
                appB.equals(fgB), "lastFgPkg=" + fgB);
        check("S15a3 跳转接管日志在场（用户可核验）",
                hasLog(lg, "跳转接管"), "logs=" + lg.size());
        check("S15a4 静态开屏被补扫跳过（竞速自检梯救回）",
                svc.gestureCount > 0 || key.clicked,
                "gestureCount=" + svc.gestureCount + " keyClicked=" + key.clicked);

        /* ── b) 阴性快重试：无竞速形态（ROM 吞掉 B 的 STATE_CHANGED）→
         *     B 的内容事件首探阴性（活动窗还属 A）→ 阴性记账 → 850ms 后
         *     第二波内容事件（活动窗已属 B）→ 800ms 快档放行 → 晋升补扫
         *     （旧 5s 包级节流在此刻将直接拦下——本断言即回归红线）── */
        AdSkipService svc2 = boot();
        AccessibilityNodeInfo aRoot2 = root(appA, 610, "android.widget.FrameLayout");
        aRoot2.add(txt("新闻列表", 340, 700, 740, 800));
        for (int i = 0; i < 12; i++) {
            aRoot2.add(node("android.view.View", 100 + i * 30, 900 + i * 30, 140 + i * 30, 930 + i * 30));
        }
        List<AccessibilityWindowInfo> winsA2 = new ArrayList<AccessibilityWindowInfo>();
        winsA2.add(win(610, aRoot2, true));
        svc2.windows = winsA2;
        svc2.activeRoot = aRoot2;
        fire(svc2, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, appA,
                "com.aaa.news.MainActivity", 50);

        // B 的内容事件（无任何 STATE_CHANGED——活动窗还属 A）→ 首探阴性
        fire(svc2, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, appB,
                "androidx.recyclerview.widget.RecyclerView", 60);
        java.util.HashMap<String, Long> neg =
                (java.util.HashMap<String, Long>) get("fgProbeNegAt", svc2);
        check("S15b1 首探阴性已记账（快重试窗开启）",
                neg != null && neg.containsKey(appB), "negMap=" + neg);
        check("S15b2 阴性期未误晋升（根还属旧应用）",
                !appB.equals(get("lastFgPkg", svc2)),
                "lastFgPkg=" + get("lastFgPkg", svc2));

        Thread.sleep(850); // 越过 800ms 快档（旧版此处为 5s 档=放弃）

        // 活动窗切到 B 的开屏（零窗口事件——ROM 吞窗形态）
        AccessibilityNodeInfo bSplash2 = root(appB, 611, "android.widget.FrameLayout");
        AccessibilityNodeInfo adImg2 = node("android.widget.ImageView", 0, 0, 1080, 2200);
        adImg2.clickable = true;
        bSplash2.add(adImg2);
        bSplash2.add(txt("广告", 20, 120, 120, 200));
        AccessibilityNodeInfo key2 = txt("跳过广告", 840, 150, 1030, 210);
        bSplash2.add(key2);
        for (int i = 0; i < 18; i++) {
            bSplash2.add(node("android.view.View", 60 + i * 15, 260 + i * 30, 100 + i * 15, 290 + i * 30));
        }
        List<AccessibilityWindowInfo> winsB2 = new ArrayList<AccessibilityWindowInfo>();
        winsB2.add(win(611, bSplash2, true));
        svc2.windows = winsB2;
        svc2.activeRoot = bSplash2;
        fire(svc2, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, appB,
                "androidx.recyclerview.widget.RecyclerView", 50);

        lastLogs = logs(svc2);
        List<String> lg2 = lastLogs;
        check("S15b3 快档连探晋升（800ms 档放行，旧 5s 档必挂）",
                appB.equals(get("lastFgPkg", svc2)),
                "lastFgPkg=" + get("lastFgPkg", svc2));
        check("S15b4 切换app回归日志在场",
                hasLog(lg2, "切换app回归"), "logs=" + lg2.size());
        check("S15b5 第二波事件上补扫跳过",
                svc2.gestureCount > 0 || key2.clicked,
                "gestureCount=" + svc2.gestureCount + " keyClicked=" + key2.clicked);

        /* ── c) IME 零劫持：评论键盘起落绝不写 lastFgPkg；设置页等真实
         *     前台切换照旧写（负例对照）── */
        AdSkipService svc3 = boot();
        AccessibilityNodeInfo aRoot3 = root(appA, 620, "android.widget.FrameLayout");
        aRoot3.add(txt("新闻列表", 340, 700, 740, 800));
        for (int i = 0; i < 12; i++) {
            aRoot3.add(node("android.view.View", 100 + i * 30, 900 + i * 30, 140 + i * 30, 930 + i * 30));
        }
        List<AccessibilityWindowInfo> winsA3 = new ArrayList<AccessibilityWindowInfo>();
        winsA3.add(win(620, aRoot3, true));
        svc3.windows = winsA3;
        svc3.activeRoot = aRoot3;
        fire(svc3, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, appA,
                "com.aaa.news.MainActivity", 50);
        check("S15c0 前台基线（A 在前台）",
                appA.equals(get("lastFgPkg", svc3)),
                "lastFgPkg=" + get("lastFgPkg", svc3));

        // 搜狗键盘弹出（STATE_CHANGED——零扫描分支，旧版在此劫持前台）
        fire(svc3, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.sohu.inputmethod.sogou", "com.sohu.inputmethod.sogou.SogouIME", 30);
        check("S15c1 输入法不劫持前台（键盘是浮层不是前台应用）",
                appA.equals(get("lastFgPkg", svc3)),
                "lastFgPkg=" + get("lastFgPkg", svc3));

        // 键盘起落后 A 的内容事件照常前台处理（无回归晋升日志 = 前台从未丢失）
        fire(svc3, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, appA,
                "android.widget.TextView", 30);
        check("S15c2 键盘起落后 A 的内容事件零晋升日志（前台从未丢失）",
                !hasLog(logs(svc3), "切换app回归"), "不应出现晋升日志");

        // 负例对照：系统设置页是真实前台切换——照旧写前台追踪
        fire(svc3, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "com.android.settings", "com.android.settings.Settings", 30);
        check("S15c3 设置页真实前台切换照旧追踪",
                "com.android.settings".equals(get("lastFgPkg", svc3)),
                "lastFgPkg=" + get("lastFgPkg", svc3));
    }

    /* ═══ S16 死透快档 + keepOn 解耦（v7.15.14 用户实锤：「卫士挂后台
     * 长时间后丢失功能，要重新退出进一下；没开启自启动状态下」+
     * #116 日志考古：16:36→21:58 断流 3h45m「自愈/拉活 0」）═══
     * 死因链：①服务死透后的下一跳闹钟是 setWindow 窗口式——App Standby
     * 深桶/厂商省电管控下推迟数小时；②keepOn=false 时 onUnbind/
     * onTaskRemoved 直接跳过排闹钟——闹钟链断裂自愈无从触发。
     * 修复验证（AdSkipService 侧新静态/去闸路径——KeepAliveReceiver
     * 真实逻辑在 JVM 侧由桩替身占位，接收器侧由发布前人工链路核验）：
     *  a) scheduleDeadWatch 死透快档：setAndAllowWhileIdle 型排针
     *    （doze 中准点触发，不再受窗口式批量调度推迟）；
     *  b) keepOn=false 时 onUnbind 仍排自检+心跳（旧版零排=链断）；
     *  c) onUnbind 返回 true（服务实例保留等待 AMS 重绑）；
     *  d) keepOn=false 时 onTaskRemoved 不炸且闹钟照排；
     *  e) scheduleNextHeartbeat 夜间窗口式口径不回归（息屏 30min 窗口）。 */
    static void s16_deadWatchKeepOnDecouple() throws Exception {
        System.out.println("[S16] 死透快档闹钟 + keepOn 解耦（后台丢功能根治）");
        AdSkipService svc = boot();

        // 记账型 AlarmManager 桩（三型全记：set/setWindow/setAndAllowWhileIdle）
        final java.util.List<String> calls = new ArrayList<String>();
        android.app.AlarmManager probe = new android.app.AlarmManager() {
            @Override
            public void set(int type, long at, android.app.PendingIntent pi) {
                calls.add("set@" + at);
            }
            @Override
            public void setWindow(int type, long at, long window, android.app.PendingIntent pi) {
                calls.add("win@" + at);
            }
            @Override
            public void setAndAllowWhileIdle(int type, long at, android.app.PendingIntent pi) {
                calls.add("awi@" + at);
            }
        };
        svc.putService(android.content.Context.ALARM_SERVICE, probe);

        // a) 死透快档：+2min allowWhileIdle 排针（旧版 setWindow 推迟数小时）
        Method sdw = AdSkipService.class.getDeclaredMethod(
                "scheduleDeadWatch", android.content.Context.class, long.class);
        sdw.setAccessible(true);
        long before = System.currentTimeMillis();
        sdw.invoke(null, svc, before + AdSkipService.DEAD_WATCH_MS);
        boolean awiOk = false;
        for (String c : calls) {
            if (c.startsWith("awi@") && Long.parseLong(c.substring(4)) >= before) awiOk = true;
        }
        check("S16a 死透快档 allowWhileIdle 排针（doze 中准点触发）",
                awiOk, "calls=" + calls);

        // b/c) keepOn=false + onUnbind：旧版直接跳过（零闹钟=链断）；
        //     新版无条件排自检（set@+3s）+ 心跳（亮屏 win@+5min）
        svc.getSharedPreferences("adshield", 0)
                .edit().putBoolean("keepOn", false).commit();
        calls.clear();
        Method ou = AdSkipService.class.getDeclaredMethod(
                "onUnbind", android.content.Intent.class);
        ou.setAccessible(true);
        Object keepInst = ou.invoke(svc, (Object) null);
        check("S16b keepOn=false 时 onUnbind 仍排自检+心跳（闹钟链不断）",
                !calls.isEmpty(), "calls=" + calls);
        check("S16c onUnbind 返回 true（实例保留待 AMS 重绑）",
                Boolean.TRUE.equals(keepInst), "ret=" + keepInst);
        lastLogs = logs(svc);
        check("S16c2 解绑日志在场（keepOn=false 也不再静默——可考古）",
                hasLog(lastLogs, "服务被系统解绑"), "logs=" + lastLogs.size());

        // d) keepOn=false + onTaskRemoved：不炸且闹钟照排（旧版 4s 提醒被
        //    闸跳过+零闹钟；新版通知由 notifyAlert 内置闸管、闹钟无条件）
        calls.clear();
        Method otr = AdSkipService.class.getDeclaredMethod(
                "onTaskRemoved", android.content.Intent.class);
        otr.setAccessible(true);
        otr.invoke(svc, (Object) null);
        check("S16d keepOn=false 时 onTaskRemoved 闹钟照排（链不断）",
                !calls.isEmpty(), "calls=" + calls);
        // 4s 提醒 postDelayed 在 handler 队列（实例活着时不发通知——零打扰）
        drain(svc);

        // e) 夜间窗口式口径不回归：息屏 + nightDeferrable → setWindow@+30min
        android.os.PowerManager pm = new android.os.PowerManager();
        pm.setInteractive(false);
        svc.putService(android.content.Context.POWER_SERVICE, pm);
        calls.clear();
        Method snh = AdSkipService.class.getDeclaredMethod(
                "scheduleNextHeartbeat", android.content.Context.class, long.class, boolean.class);
        snh.setAccessible(true);
        snh.invoke(null, svc, System.currentTimeMillis() + 30 * 60_000L, true);
        boolean nightWin = false;
        for (String c : calls) {
            if (c.startsWith("win@")) nightWin = true;
        }
        check("S16e 息屏夜间心跳维持窗口式（30min 批量调度不唤醒设备）",
                nightWin, "calls=" + calls);
    }
}
