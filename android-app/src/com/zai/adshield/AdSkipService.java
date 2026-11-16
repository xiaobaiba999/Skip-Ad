package com.zai.adshield;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 开屏广告智能跳过服务（无障碍服务实现）
 *
 * 设计依据（2026-11 调研）：
 * 1. 市面开屏广告的「跳过」是一个真实控件（TextView/Button，text="跳过"），存在于无障碍控件树中，
 *    可通过 text / viewIdResourceName 定位后 performAction(ACTION_CLICK) —— 与 GKD、李跳跳同原理。
 * 2. 「拦精灵」案（2024，芒果TV 诉）判决边界：跳过类软件只能「替用户点击跳过按钮」，
 *    不得直接屏蔽广告本身。本服务严格只代点「跳过/skip/关闭广告」类控件，不碰网络层。
 *
 * 智能学习机制：
 * - 冷启动：通用规则（在开屏时间窗内，寻找小面积、含「跳过」字样的可点击控件）。
 * - 观察学习：捕获用户手动点击「跳过」的事件（TYPE_VIEW_CLICKED），记录该应用的控件文本与 viewId，
 *   转为该应用的专属规则，下次启动同应用时优先按 viewId 精确命中，更快更稳。
 * - 安全阀：控件面积上限（防「全屏热区/摇一摇」误触）、点击冷却、危险词过滤、时间窗限制。
 */
public class AdSkipService extends AccessibilityService {

    public static volatile AdSkipService instance;

    private static final String PREFS = "adshield";
    private static final String KEY_STATE = "state";

    // 只识别「跳过」类控件 —— 合规边界：代点跳过，不屏蔽广告
    private static final String[] SKIP_TEXTS = {"跳过", "关闭广告"};

    private static final long WINDOW_MS = 12000;   // 开屏时间窗（v3.0.0：3.5s→12s，覆盖延迟跳过键）
    private static final long COOLDOWN_MS = 1500;  // 同一应用两次自动点击的最小间隔
    private static final long LEARN_WINDOW_MS = 30000; // 手动点击学习的宽时间窗
    private static final int MAX_RULES_PER_APP = 8;
    private static final int MAX_LOG = 60;

    /* v3.0.0 能耗治理：内存聚合 + 延迟落盘（合并闪存写入） */
    private static final long FLUSH_DELAY_MS = 15000;
    private boolean flushScheduled = false;

    /* v3.0.0 能耗画像（开机累计，供学习中心「能耗面板」展示） */
    public long energyEvents = 0;   // 处理的无障碍事件数
    public long energyScans = 0;    // 控件树扫描次数

    private final Object lock = new Object();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.HashMap<String, Long> windowAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastClickAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> nextScanAt = new java.util.HashMap<>();

    // 标记自己发起的点击，避免把引擎的点击误学为「用户手动点击」
    private volatile String lastAutoClickPkg;
    private volatile long lastAutoClickAt;

    private int screenWidth;
    private int screenHeight;

    /** 状态快照（含缓存）：Bridge 读 state 时走这里，保证 UI 永远拿到最新内存态 */
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
        appendLog("服务已启动 · 正在观察你的跳过习惯（冷启动使用通用规则）");
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
        if (pkg.equals(getPackageName())) return; // 忽略自身
        if (isSystemNoise(pkg)) return;

        long now = System.currentTimeMillis();
        energyEvents++; // v3.0.0 能耗画像：仅统计有效事件（噪声已前置过滤）

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                // 应用打开 / 弹出新窗口：进入开屏时间窗，稍后扫描跳过按钮
                windowAt.put(pkg, now);
                requestScan(pkg, 350);
                break;
            }
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                // 界面内容变化（延迟出现的跳过按钮等）：窗口期内持续尝试
                if (now - winAt(pkg) < WINDOW_MS) requestScan(pkg, 300);
                break;
            }
            case AccessibilityEvent.TYPE_VIEW_CLICKED: {
                // 智能学习：观察用户手动点了什么
                if (isOwnAutoClick(pkg, now)) return;
                learnFromManualClick(pkg, event, now);
                observeMisclick(pkg, event, now); // v1.2.0：误触观察（只记录不拦截）
                break;
            }
        }
    }

    /* ───────── 基础判断 ───────── */

    private boolean isSystemNoise(String pkg) {
        return pkg.startsWith("com.android.systemui")
                || pkg.equals("android")
                || pkg.contains("inputmethod");
    }

    private long winAt(String pkg) {
        Long t = windowAt.get(pkg);
        return t == null ? 0L : t;
    }

    private boolean isOwnAutoClick(String pkg, long now) {
        return pkg.equals(lastAutoClickPkg) && (now - lastAutoClickAt) < 900;
    }

    /* ───────── 扫描与点击 ───────── */

    private void requestScan(String pkg, long delay) {
        long now = System.currentTimeMillis();
        Long next = nextScanAt.get(pkg);
        if (next != null && now < next) return; // 事件去抖：合并高频内容变化
        nextScanAt.put(pkg, now + delay);
        // lambda 由 d8 脱糖，避免非静态内部类触发 d8 8.2 的 NPE bug
        handler.postDelayed(() -> tryScan(pkg), delay);
    }

    private void tryScan(String pkg) {
        if (instance == null || !learningEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - winAt(pkg) > WINDOW_MS) return; // 超出开屏时间窗不动作
        Long last = lastClickAt.get(pkg);
        if (last != null && now - last < COOLDOWN_MS) return; // 点击冷却
        energyScans++; // v3.0.0 能耗画像：每次控件树扫描计数

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            CharSequence rp = root.getPackageName();
            if (rp == null || !pkg.contentEquals(rp)) return; // 只处理当前活动窗口

            Target t = findSkipTarget(root, pkg);
            if (t != null && click(t.node)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                bumpAuto(pkg, t);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean click(AccessibilityNodeInfo node) {
        try {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Exception e) {
            return false;
        }
    }

    private static class Target {
        AccessibilityNodeInfo node;
        String how;    // 命中方式（学习规则 / 通用规则）
        String label;  // 被点击控件文本
        String ruleId; // 命中的学习规则 viewId（可为空）

        Target(AccessibilityNodeInfo n, String how, String label, String ruleId) {
            this.node = n;
            this.how = how;
            this.label = label;
            this.ruleId = ruleId;
        }
    }

    /** 优先级：① 已学习规则（viewId 精确命中）→ ② 已学习规则（文本）→ ③ 通用规则（文本匹配） */
    private Target findSkipTarget(AccessibilityNodeInfo root, String pkg) {
        JSONObject rules = null;
        try {
            rules = loadState().optJSONObject("rules");
        } catch (Exception ignored) {
        }
        if (rules != null) {
            JSONArray list = rules.optJSONArray(pkg);
            if (list != null) {
                // ① viewId 精确查找（最稳，不受文案改版影响）
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r == null) continue;
                    String vid = r.optString("viewId", "");
                    if (vid.length() == 0) continue;
                    List<AccessibilityNodeInfo> nodes =
                            root.findAccessibilityNodeInfosByViewId(vid);
                    if (nodes != null) {
                        for (AccessibilityNodeInfo n : nodes) {
                            if (isSafeClickable(n)) {
                                return new Target(n, "学习规则·控件ID", vid, vid);
                            }
                        }
                    }
                }
                // ② 学习到的文本按钮
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r == null) continue;
                    String txt = r.optString("text", "");
                    if (txt.length() == 0) continue;
                    List<AccessibilityNodeInfo> nodes =
                            root.findAccessibilityNodeInfosByText(txt);
                    if (nodes != null) {
                        for (AccessibilityNodeInfo n : nodes) {
                            if (isSafeClickable(n)) {
                                return new Target(n, "学习规则·文本「" + txt + "」", txt, r.optString("viewId", ""));
                            }
                        }
                    }
                }
            }
        }
        // ③ 通用规则：遍历控件树找含「跳过」的小面积可点控件
        // v3.0.0 能耗治理：扫描节点预算 600→260（未命中时提前剪枝，覆盖绝大多数开屏树）
        int[] budget = {260};
        return findGeneric(root, 0, budget);
    }

    /** 通用规则 DFS：文本/描述含「跳过」，控件短小、可点击（或祖先可点击） */
    private Target findGeneric(AccessibilityNodeInfo node, int depth, int[] budget) {
        if (node == null || depth > 30 || budget[0] <= 0) return null;
        budget[0]--;
        try {
            String text = cs(node.getText());
            String desc = cs(node.getContentDescription());
            String label = text.length() > 0 ? text : desc;
            if (label.length() > 0 && label.length() <= 14 && matchesSkip(label)) {
                AccessibilityNodeInfo clickable = resolveClickable(node);
                if (clickable != null && isSmallArea(clickable)) {
                    return new Target(clickable, "通用规则·文本「" + label + "」", label, "");
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                Target r = findGeneric(node.getChild(i), depth + 1, budget);
                if (r != null) return r;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean matchesSkip(String s) {
        String lower = s.toLowerCase();
        if (lower.contains("skip") || lower.contains("跳过") ) return true;
        for (String k : SKIP_TEXTS) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    /** 向上找可点击的祖先（最多 3 层）—— 有些「跳过」文字本身不可点，其容器可点 */
    private AccessibilityNodeInfo resolveClickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int i = 0; i < 3 && cur != null; i++) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    /**
     * 面积安全阀：只点击小控件（< 屏幕面积 35% 且宽度 < 屏宽 55%）。
     * 用于防「摇一摇 / 全屏大热区」类广告的误触 —— 引擎永远不点屏幕中央大区域。
     */
    private boolean isSmallArea(AccessibilityNodeInfo node) {
        try {
            if (screenWidth <= 0 || screenHeight <= 0) return true;
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.isEmpty()) return false;
            double area = (double) r.width() * r.height();
            double screen = (double) screenWidth * screenHeight;
            return area / screen < 0.35 && r.width() < screenWidth * 0.55;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSafeClickable(AccessibilityNodeInfo n) {
        return n != null && n.isClickable() && isSmallArea(n);
    }

    private String cs(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    /* ───────── 智能学习：观察手动点击 ───────── */

    private void learnFromManualClick(String pkg, AccessibilityEvent event, long now) {
        if (now - winAt(pkg) > LEARN_WINDOW_MS) return; // 只学习窗口切换附近的点击

        String text = "";
        String viewId = "";
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
                if (text.length() == 0) text = cs(src.getContentDescription());
                viewId = cs(src.getViewIdResourceName());
            }
        } catch (Exception ignored) {
        }
        if (!matchesSkip(text)) return; // 只学习「跳过/skip/关闭广告」类点击

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
                // 合并到已有规则：同 viewId 或同文本 → manual+1
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
                    if (list.length() >= MAX_RULES_PER_APP) return; // 每应用规则数上限
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

    /* ───────── v1.2.0 误触观察：区分「广告诱导跳转」与「用户主动操作」 ───────── */

    /**
     * 只记录、不拦截（无障碍无法取消已发生的事件）：
     * 窗口切换 12s 内点击了「非跳过」控件，且满足以下任一 → 判「疑似误触」：
     *  · 控件面积巨大（> 屏幕面积 35% 且宽 > 55% 屏宽，即全屏热区）
     *  · 点击发生在窗口切换后 700ms 内（快于人类反应，参考 2026-11 调研的误触指纹）
     * 结果写入 stats.misclick 并进入引擎日志，供学习中心展示。
     */
    private void observeMisclick(String pkg, AccessibilityEvent event, long now) {
        try {
            if (screenWidth <= 0 || screenHeight <= 0) return;
            if (now - winAt(pkg) > WINDOW_MS) return;
            AccessibilityNodeInfo src = event.getSource();
            if (src == null) return;
            String label = cs(src.getText());
            if (label.length() == 0) label = cs(src.getContentDescription());
            if (matchesSkip(label)) return; // 跳过类点击在学习通道处理
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

    /* ───────── 统计与日志（JSON 持久化，页面通过 JSBridge 读取） ───────── */

    private boolean learningEnabled() {
        try {
            JSONObject st = loadState();
            return !st.has("learning") || st.optBoolean("learning", true);
        } catch (Exception e) {
            return true;
        }
    }

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

    private void bumpAuto(String pkg, Target t) {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject stats = st.optJSONObject("stats");
                if (stats == null) {
                    stats = new JSONObject();
                    st.put("stats", stats);
                }
                stats.put("auto", stats.optInt("auto", 0) + 1);
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
                a.put("auto", a.optInt("auto", 0) + 1);
                // 命中学习规则时累计该规则成功次数
                if (t.ruleId != null && t.ruleId.length() > 0) {
                    JSONObject rules = st.optJSONObject("rules");
                    JSONArray list = rules == null ? null : rules.optJSONArray(pkg);
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject r = list.optJSONObject(i);
                            if (r != null && t.ruleId.equals(r.optString("viewId", ""))) {
                                r.put("auto", r.optInt("auto", 0) + 1);
                                break;
                            }
                        }
                    }
                }
                saveState(st);
            } catch (Exception ignored) {
            }
        }
        appendLog("⚡ 已自动点击 " + shortPkg(pkg) + " 的「" + t.label + "」（" + t.how + "）");
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

    /* ───────── v3.0.0 内存聚合 + 延迟落盘（能耗治理） ─────────
       原实现每次日志/统计变更即写 SharedPreferences（闪存写入是最主要耗电源）；
       现改为内存态 + 15s 合并落盘，onDestroy / UI 快照读取时立即 flush。 */

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

    /** 只改内存 + 调度合并落盘（15s 内多次写合并为一次闪存写入） */
    private void saveState(org.json.JSONObject st) {
        stateInMemory = st;
        scheduleFlush();
    }

    private void scheduleFlush() {
        synchronized (lock) {
            if (flushScheduled) return;
            flushScheduled = true;
        }
        // lambda 由 d8 脱糖（与 requestScan 同路径，已验证）
        handler.postDelayed(() -> flushNow(), FLUSH_DELAY_MS);
    }

    /** 立即落盘（幂等；onDestroy 与 UI 读取前调用） */
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

    private String shortPkg(String pkg) {
        if (pkg == null) return "";
        int i = pkg.lastIndexOf('.');
        return i >= 0 && pkg.length() - i - 1 > 1 ? pkg.substring(i + 1) : pkg;
    }
}
