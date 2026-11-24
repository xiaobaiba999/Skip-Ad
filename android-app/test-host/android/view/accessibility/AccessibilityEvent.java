package android.view.accessibility;

/** 服务级 JVM 测试桩：无障碍事件 */
public class AccessibilityEvent {
    public static final int TYPE_WINDOW_STATE_CHANGED = 1 << 0;
    public static final int TYPE_WINDOW_CONTENT_CHANGED = 1 << 1;
    public static final int TYPE_VIEW_CLICKED = 1 << 2;

    public int eventType;
    public CharSequence packageName;
    public CharSequence className;
    public long eventTime;

    public static AccessibilityEvent obtain() {
        return new AccessibilityEvent();
    }

    public int getEventType() { return eventType; }
    public CharSequence getPackageName() { return packageName; }
    public CharSequence getClassName() { return className; }
    public long getEventTime() { return eventTime; }
    public AccessibilityNodeInfo getSource() { return source; }
    public java.util.List<CharSequence> getText() { return text; }
    public java.util.List<CharSequence> text = new java.util.ArrayList<CharSequence>();
    public AccessibilityNodeInfo source;
}
