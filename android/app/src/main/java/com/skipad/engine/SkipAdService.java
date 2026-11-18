package com.skipad.engine;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.skipad.keepalive.KeepAliveDoctor;
import com.skipad.log.StructuredLogger;
import com.skipad.platform.Aosp14to16Adapter;
import com.skipad.power.PowerGovernor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开屏卫士无障碍服务主类（v5.4.0）。
 *
 * <p>服务配置（res/xml/accessibility_service_config.xml，装配与代码声明保持一致）：
 * <pre>
 * &lt;accessibility-service
 *     android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
 *     android:accessibilityFeedbackType="feedbackGeneric"
 *     android:accessibilityFlags="flagReportViewIds|flagRequestFilterKeyEvents|flagRetrieveInteractiveWindows"
 *     android:canRetrieveWindowContent="true"
 *     android:canPerformGestures="true"
 *     android:accessibilityTool="true"
 *     android:notificationTimeout="50"
 *     android:description="@string/accessibility_description"/&gt;
 * </pre>
 * 要点：
 * <ul>
 *   <li>canRetrieveWindowContent：窗口内容读取（探针/DFS/弹窗双信号的前提）</li>
 *   <li>canPerformGestures：坐标级点击（L6 兜底，v3.7.0 手势能力修复——缺失时
 *       「已点无效」事故复现）</li>
 *   <li>flagReportViewIds：viewId 定向查找（学习指纹通道免全树扫描）</li>
 *   <li>flagRequestFilterKeyEvents：按键事件观测（返回键兜底时序，不拦截用户按键）</li>
 *   <li>accessibilityTool（A14+）：如实声明为无障碍工具，系统页不再标注数据收集警告</li>
 * </ul>
 *
 * <p>事件分发：
 * <ul>
 *   <li>TYPE_WINDOW_STATE_CHANGED → 窗口管线（{@link MatchingEngine#processWindow}），
 *       窗口切换即触发 0ms 首扫 + 前密后疏重试链 [0/120/240/400/650/1000ms]，命中即熔断</li>
 *   <li>TYPE_WINDOW_CONTENT_CHANGED → 内容管线（节流预算制：每分钟预算来自
 *       PowerGovernor 16 档策略；学习窗期/前贴窗期例外放行）</li>
 * </ul>
 *
 * <p>断连自愈：onServiceConnected 重连注册通道（登记在 → 系统自动重绑）；
 * onUnbind → {@link KeepAliveDoctor#handleUnbind} 两态通知（自动拉活 / 直达
 * 开关页 + 10 秒确认说明——绝不自动点系统确认按钮）。
 */
public class SkipAdService extends AccessibilityService
        implements MatchingEngine.NodeGateway, MatchingEngine.EngineListener {

    private static final String TAG_MODULE = "service";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final KeepAliveDoctor doctor = new KeepAliveDoctor();

    private RuleScheduler scheduler;
    private PowerGovernor governor;
    private MatchingEngine engine;

    /** 内容事件预算窗口（每分钟滑动窗口内的已处理计数） */
    private int contentBudgetUsed;
    private long contentBudgetWindowStartMs;

    /** 当前重试链（窗口键 → 未执行的延迟任务数；命中即熔断取消） */
    private final Map<String, List<Runnable>> pendingRetries = new HashMap<>();
    private String activeWindowKey;

    /** 应用启动时间（入口窗判定：冷启动 3.5s 内/回前台首窗禁返回键） */
    private long lastWindowStateChangedAt;

    // ───────────────────────── 生命周期 ─────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        StructuredLogger.init(getFilesDir(), BuildConfigLike.releaseLevel());
        governor = PowerGovernor.init(this);
        scheduler = RuleScheduler.getInstance(this);
        engine = new MatchingEngine(scheduler, governor);
        engine.setListener(this);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            // 事件与能力补齐（XML 为规范源，代码兜底防 ROM 裁剪 XML 属性）
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            // Android 14：无障碍工具标记（红线：声明内容必须真实）
            Aosp14to16Adapter.applyAccessibilityToolMark(info);
            setServiceInfo(info);
        }
        lastWindowStateChangedAt = System.currentTimeMillis();
        StructuredLogger.get().i(TAG_MODULE, "service_connected",
                "sdk", Build.VERSION.SDK_INT, "pkg", getPackageName());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || engine == null) {
            return;
        }
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                onWindowStateChanged(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                onWindowContentChanged(event);
                break;
            default:
                break; // 其余事件类型不订阅（XML 已限定，双保险）
        }
    }

    @Override
    public void onInterrupt() {
        StructuredLogger.get().w(TAG_MODULE, "interrupt");
    }

    @Override
    protected boolean onGesture(int gestureId) {
        return super.onGesture(gestureId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // 服务断连：两态通知（登记在 → 自动拉活；真吊销 → 直达开关页）
        doctor.handleUnbind(getApplicationContext());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cancelAllRetries();
        StructuredLogger.get().w(TAG_MODULE, "destroy");
        super.onDestroy();
    }

    // ───────────────────────── 窗口管线 ─────────────────────────

    /** TYPE_WINDOW_STATE_CHANGED：窗口切换 → 0ms 首扫 + 重试链 */
    private void onWindowStateChanged(AccessibilityEvent event) {
        CharSequence pkgCs = event.getPackageName();
        CharSequence clsCs = event.getClassName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();
        String windowClass = clsCs == null ? "" : clsCs.toString();
        if (pkg.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        lastWindowStateChangedAt = now;

        MatchingEngine.WindowContext ctx = buildWindowContext(pkg, windowClass, now);
        String key = ctx.key();
        if (!key.equals(activeWindowKey)) {
            cancelAllRetries();       // 窗口切换：旧链全熔断
            activeWindowKey = key;
        }
        // 0ms 首扫（极速链起点；敏感零扫描/P0 预拦截在引擎内最先判定）
        scanOnce(ctx);
        // 前密后疏重试链（密度来自 PowerGovernor；命中即熔断由 scanOnce 取消整链）
        scheduleRetryChain(ctx);
    }

    /** 单次扫描（重试链复用同一路径） */
    private void scanOnce(final MatchingEngine.WindowContext ctx) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                MatchingEngine.ScanResult result = engine.processWindow(SkipAdService.this, ctx);
                if (isTerminal(result)) {
                    cancelAllRetries(); // 命中即熔断（含敏感零扫描/CLEAN/预拦截）
                }
                if (result.sdkWindow && result.escalateBackAtMs >= 0) {
                    // SDK 广告窗：重试链 500ms 内未出真跳过键 → 返回键直断（入口窗除外）
                    // 时序由 escalateBackAtMs 与重试链对齐（MatchingEngine.SDK_CUT_ESCALATE_MS）
                    StructuredLogger.get().d(TAG_MODULE, "sdk_window_armed",
                            "window", ctx.key(), "escalateMs", result.escalateBackAtMs);
                }
            }
        });
    }

    /** 前密后疏重试链：[0/120/240/400/650/1000ms]（按策略截断） */
    private void scheduleRetryChain(final MatchingEngine.WindowContext ctx) {
        long[] chain = governor.currentPolicy().retryChainMs;
        List<Runnable> runnables = new ArrayList<>(chain.length);
        for (int i = 1; i < chain.length; i++) { // 第 0 次已在 onWindowStateChanged 直发
            final int index = i;
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    List<Runnable> list = pendingRetries.get(ctx.key());
                    if (list != null) {
                        list.remove(this);
                    }
                    scanOnce(ctx);
                }
            };
            runnables.add(r);
            mainHandler.postDelayed(r, chain[i]);
        }
        pendingRetries.put(ctx.key(), runnables);
    }

    private void cancelAllRetries() {
        for (List<Runnable> list : pendingRetries.values()) {
            for (Runnable r : list) {
                mainHandler.removeCallbacks(r);
            }
        }
        pendingRetries.clear();
    }

    /** 终态判定：命中/预拦截/零扫描/CLEAN 跳过/标注类（不再重扫） */
    private static boolean isTerminal(MatchingEngine.ScanResult r) {
        return !"miss".equals(r.action) && !"none".equals(r.action);
    }

    // ───────────────────────── 内容管线 ─────────────────────────

    /**
     * TYPE_WINDOW_CONTENT_CHANGED：内容管线（节流预算制）。
     *
     * <p>预算 = PowerGovernor 策略 contentEventBudgetPerMin（每分钟滑动窗口）；
     * 学习窗期（1.5s 事后验证）与前贴静候窗期不计预算——这两类必须实时响应。
     * v5.3.0 教训：无预算的流式内容窗口（豆包对话）5 分钟冲掉 7 个突发窗。
     */
    private void onWindowContentChanged(AccessibilityEvent event) {
        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();
        if (pkg.isEmpty() || activeWindowKey == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - contentBudgetWindowStartMs >= 60_000L) {
            contentBudgetWindowStartMs = now;
            contentBudgetUsed = 0;
        }
        boolean budgetAvailable = contentBudgetUsed < governor.currentPolicy().contentEventBudgetPerMin;
        if (!budgetAvailable) {
            return; // 预算耗尽：本分钟内内容事件不再触发扫描（窗口切换事件不受限）
        }
        contentBudgetUsed++;
        // 内容变化复用当前窗口上下文（窗口类取当前激活窗类；探针内判定成本极低）
        CharSequence clsCs = event.getClassName();
        String windowClass = clsCs == null ? "" : clsCs.toString();
        if (windowClass.isEmpty()) {
            windowClass = activeClassOf(activeWindowKey);
        }
        MatchingEngine.WindowContext ctx = buildWindowContext(pkg, windowClass, now);
        // TODO: 学习窗期/前贴静候期放行标记（LearningCenter 状态机驱动），当前按预算制统一节流
        scanOnce(ctx);
    }

    /** 从窗口键 pkg|class 提取 class 段 */
    private static String activeClassOf(String windowKey) {
        if (windowKey == null) {
            return "";
        }
        int i = windowKey.indexOf('|');
        return i < 0 || i + 1 >= windowKey.length() ? "" : windowKey.substring(i + 1);
    }

    // ───────────────────────── 窗口上下文装配 ─────────────────────────

    /** 从事件装配窗口上下文（入口窗/Dialog/守护应用/全形态几何） */
    private MatchingEngine.WindowContext buildWindowContext(String pkg, String windowClass,
                                                            long now) {
        MatchingEngine.WindowContext ctx = new MatchingEngine.WindowContext();
        ctx.pkg = pkg;
        ctx.windowClass = windowClass;
        ctx.eventTimeMs = now;
        ctx.dialogWindow = windowClass.contains("Dialog")
                || windowClass.contains("PopupWindow")
                || windowClass.contains("Alert");
        ctx.entryWindow = now - lastWindowStateChangedAt < 3_500L; // 冷启动开屏窗期
        ctx.guardApp = isGuardApp(pkg);
        Rect frame = windowFrame();
        ctx.windowW = frame == null ? 0 : frame.width();
        ctx.windowH = frame == null ? 0 : frame.height();
        return ctx;
    }

    /** 守护名单（银行/政务/支付）：广告照拦，服务零干预，仅温和代点 */
    private static boolean isGuardApp(String pkg) {
        return com.skipad.guard.SensitiveSceneGuard.isGuardApp(pkg);
    }

    /** 当前窗口几何（全形态：分屏/折叠按实际窗口而非整屏） */
    private Rect windowFrame() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                return getWindow().getBounds();
            }
            // TODO: API < 30 回退 WindowManager.getDefaultDisplay().getRectSize
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ───────────────────────── NodeGateway 实现 ─────────────────────────

    @Override
    public RuleDslParser.NodeLike root() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root == null ? null : new AndroidNode(root);
    }

    @Override
    public List<RuleDslParser.NodeLike> findByText(String keyword) {
        List<RuleDslParser.NodeLike> out = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return out;
        }
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(keyword);
        if (found != null) {
            for (AccessibilityNodeInfo n : found) {
                if (n != null) {
                    out.add(new AndroidNode(n));
                }
            }
        }
        return out;
    }

    @Override
    public List<RuleDslParser.NodeLike> findByViewId(String viewId) {
        List<RuleDslParser.NodeLike> out = new ArrayList<>();
        if (viewId == null) {
            return out;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return out;
        }
        List<AccessibilityNodeInfo> found = null;
        try {
            found = root.findAccessibilityNodeInfosByViewId(viewId);
        } catch (Throwable t) {
            // flagReportViewIds 未生效的 ROM：回退缓存 vid 匹配（引擎侧兜底）
            return out;
        }
        if (found != null) {
            for (AccessibilityNodeInfo n : found) {
                if (n != null) {
                    out.add(new AndroidNode(n));
                }
            }
        }
        return out;
    }

    @Override
    public boolean click(RuleDslParser.NodeLike node) {
        if (!(node instanceof AndroidNode)) {
            return false;
        }
        AccessibilityNodeInfo raw = ((AndroidNode) node).raw();
        if (raw == null) {
            return false;
        }
        if (raw.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true;
        }
        // 点击被吞/不可点 → 坐标级兜底（canPerformGestures 声明保证能力）
        int[] b = node.bounds();
        if (b != null && b.length >= 4) {
            return clickAt((b[0] + b[2]) / 2, (b[1] + b[3]) / 2);
        }
        return false;
    }

    @Override
    public boolean clickAt(int x, int y) {
        if (Build.VERSION.SDK_INT < 24) {
            return false; // GestureDescription 需 API 24+
        }
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 48))
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            StructuredLogger.get().w(TAG_MODULE, "gesture_failed",
                    "err", t.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean globalBack() {
        // Android 16 预测性返回时序适配（back 派发后延迟复验，防双返回事故）
        return Aosp14to16Adapter.performBackSafely(this, activeWindowKey, 180,
                new Aosp14to16Adapter.BackReverify() {
                    @Override
                    public void onReverify(boolean stillSame) {
                        if (stillSame) {
                            StructuredLogger.get().d(TAG_MODULE, "back_reverify_still_same",
                                    "window", activeWindowKey);
                            // 补发由引擎返回键上限约束（每窗最多 2 次），此处仅留痕
                        }
                    }
                });
    }

    // ───────────────────────── EngineListener 实现 ─────────────────────────

    @Override
    public void onEvent(String event, Map<String, Object> data) {
        StructuredLogger.get().d(TAG_MODULE, event,
                "k0", data == null || data.isEmpty() ? "" : String.valueOf(data.keySet().toArray()[0]),
                "size", data == null ? 0 : data.size());
    }

    @Override
    public void onAction(MatchingEngine.ActionReport report) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", report.action);
        data.put("rule", report.ruleName);
        data.put("layer", report.layer);
        data.put("pkg", windowPkgOf(report.windowKey));
        data.put("confidence", report.confidence);
        data.put("engineMs", report.engineMs);
        data.put("preempt", report.preempt);
        data.put("guard", report.guardMode);
        StructuredLogger.get().i(TAG_MODULE, "block_action", data);
        // TODO: 学习中心钩子——非预拦截命中回写 LearningSample（质量模型把关后
        //       沉淀学习指纹；同秒/缩水/窗类/超窗四规则拒绝噪声样本）
    }

    private static String windowPkgOf(String windowKey) {
        if (windowKey == null) {
            return "";
        }
        int i = windowKey.indexOf('|');
        return i <= 0 ? windowKey : windowKey.substring(0, i);
    }

    // ───────────────────────── 节点适配器 ─────────────────────────

    /**
     * AccessibilityNodeInfo → NodeLike 适配器。
     *
     * <p>属性懒缓存：每个属性只读一次（AccessibilityNodeInfo 属性读取是 binder
     * IPC，重复 getText()/getViewIdResourceName() 是李跳跳类工具扫描慢的根因；
     * 引擎侧整树缓存见 MatchingEngine.NodeCache，本类兜底散点访问）。
     */
    static final class AndroidNode implements RuleDslParser.NodeLike {
        private final AccessibilityNodeInfo node;
        private CharSequence text;
        private CharSequence desc;
        private String vid;
        private String viewId;
        private String name;
        private int[] bounds;
        private boolean textRead, descRead, vidRead, nameRead, boundsRead;

        AndroidNode(AccessibilityNodeInfo node) {
            this.node = node;
        }

        AccessibilityNodeInfo raw() {
            return node;
        }

        @Override
        public CharSequence text() {
            if (!textRead) {
                text = node.getText();
                textRead = true;
            }
            return text;
        }

        @Override
        public CharSequence desc() {
            if (!descRead) {
                desc = node.getContentDescription();
                descRead = true;
            }
            return desc;
        }

        @Override
        public String vid() {
            if (!vidRead) {
                String full = viewId();
                int slash = full == null ? -1 : full.lastIndexOf('/');
                vid = slash >= 0 ? full.substring(slash + 1) : full;
                vidRead = true;
            }
            return vid;
        }

        @Override
        public String viewId() {
            if (viewId == null) {
                try {
                    viewId = node.getViewIdResourceName();
                } catch (Throwable t) {
                    viewId = "";
                }
            }
            return viewId == null ? "" : viewId;
        }

        @Override
        public String name() {
            if (!nameRead) {
                CharSequence cls = node.getClassName();
                if (cls == null) {
                    name = "";
                } else {
                    String s = cls.toString();
                    int dot = s.lastIndexOf('.');
                    name = dot >= 0 ? s.substring(dot + 1) : s;
                }
                nameRead = true;
            }
            return name;
        }

        @Override
        public Boolean boolFlag(String flag) {
            // 三态：已知属性返回值，未知返回 null（DSL 条件保守降级）
            if ("clickable".equals(flag)) {
                return node.isClickable();
            }
            if ("longClickable".equals(flag)) {
                return node.isLongClickable();
            }
            if ("enabled".equals(flag)) {
                return node.isEnabled();
            }
            if ("focusable".equals(flag)) {
                return node.isFocusable();
            }
            if ("scrollable".equals(flag)) {
                return node.isScrollable();
            }
            if ("checkable".equals(flag)) {
                return node.isCheckable();
            }
            if ("checked".equals(flag)) {
                return node.isChecked();
            }
            if ("editable".equals(flag)) {
                return node.isEditable();
            }
            if ("visibleToUser".equals(flag)) {
                return node.isVisibleToUser();
            }
            return null;
        }

        @Override
        public int index() {
            // AccessibilityNodeInfo 无兄弟下标 API：由父节点遍历获得（懒求值，低频路径）
            AccessibilityNodeInfo parent = node.getParent();
            if (parent == null) {
                return 0;
            }
            for (int i = 0; i < parent.getChildCount(); i++) {
                AccessibilityNodeInfo c = parent.getChild(i);
                if (c != null && c.equals(node)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int depth() {
            int d = 0;
            AccessibilityNodeInfo p = node.getParent();
            while (p != null && d < 64) {
                d++;
                p = p.getParent();
            }
            return d;
        }

        @Override
        public RuleDslParser.NodeLike parent() {
            AccessibilityNodeInfo p = node.getParent();
            return p == null ? null : new AndroidNode(p);
        }

        @Override
        public int childCount() {
            return node.getChildCount();
        }

        @Override
        public RuleDslParser.NodeLike child(int i) {
            AccessibilityNodeInfo c = node.getChild(i);
            return c == null ? null : new AndroidNode(c);
        }

        @Override
        public int[] bounds() {
            if (!boundsRead) {
                Rect r = new Rect();
                try {
                    node.getBoundsInScreen(r);
                    bounds = new int[]{r.left, r.top, r.right, r.bottom};
                } catch (Throwable t) {
                    bounds = new int[0];
                }
                boundsRead = true;
            }
            return bounds.length >= 4 ? bounds : null;
        }
    }

    // ───────────────────────── 构建变体占位 ─────────────────────────

    /**
     * BuildConfig 桥（避免框架骨架依赖 gradle BuildConfig 生成物）。
     * 工程合入后替换为真实 BuildConfig.DEBUG 判定。
     */
    private static final class BuildConfigLike {
        static StructuredLogger.Level releaseLevel() {
            // TODO: 接入 BuildConfig.DEBUG 后：debug → VERBOSE，release → INFO
            return StructuredLogger.Level.INFO;
        }
    }
}
