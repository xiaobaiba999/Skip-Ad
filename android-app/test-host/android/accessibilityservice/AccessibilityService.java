package android.accessibilityservice;

import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * 服务级 JVM 测试桩：AccessibilityService。
 * 窗口环境由测试直接装配（activeRoot / windows），模拟真实系统语义：
 *  · getRootInActiveWindow() 返回当前活动窗根（可置 null——真实冷启动常见）；
 *  · getWindows() 返回全量窗口列表（含异包窗口——launcher/systemui/IME，
 *    与真实系统一致；getWindows() 的窗口顺序 = 装配顺序）。
 */
public abstract class AccessibilityService extends Context {

    // ── 测试装配的窗口环境 ──
    public AccessibilityNodeInfo activeRoot;
    public List<AccessibilityWindowInfo> windows;

    public static final int GLOBAL_ACTION_BACK = 1;

    public abstract void onAccessibilityEvent(AccessibilityEvent event);

    public abstract void onInterrupt();

    protected void onServiceConnected() { }

    public boolean onUnbind(Intent intent) { return false; }

    public void onDestroy() { }

    public void onTaskRemoved(Intent rootIntent) { }

    public AccessibilityNodeInfo getRootInActiveWindow() {
        return activeRoot;
    }

    public List<AccessibilityWindowInfo> getWindows() {
        return windows;
    }

    public boolean performGlobalAction(int action) {
        return true;
    }

    /** 手势派发记录（测试断言「点了哪里」） */
    public float lastGestureX = -1, lastGestureY = -1;
    public int gestureCount = 0;

    public boolean dispatchGesture(GestureDescription gesture,
                                   GestureResultCallback callback, android.os.Handler handler) {
        gestureCount++;
        lastGestureX = gesture.tapX;
        lastGestureY = gesture.tapY;
        if (callback != null) {
            // 同步回调（真实系统 ~50ms；测试里立即完成——复验链全部当场跑）
            callback.onCompleted(gesture);
        }
        return true;
    }

    public static abstract class GestureResultCallback {
        public void onCompleted(GestureDescription gesture) { }
        public void onCancelled(GestureDescription gesture) { }
    }
}
