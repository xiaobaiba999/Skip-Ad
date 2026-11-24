package android.view.accessibility;

/** 服务级 JVM 测试桩：窗口信息（id + root，与真实 getWindows() 枚举同构） */
public class AccessibilityWindowInfo {
    public static final int TYPE_APPLICATION = 1;
    public static final int TYPE_INPUT_METHOD = 2;
    public static final int TYPE_SYSTEM = 3;
    public static final int TYPE_MAGNIFICATION_OVERLAY = 4;

    public int id;
    public android.graphics.Rect bounds = new android.graphics.Rect(0, 0, 1080, 2400);
    public AccessibilityNodeInfo root; // 可为 null（真实系统常见：无内容窗口）
    public int type = TYPE_APPLICATION;
    public boolean active, focused;

    public int getId() { return id; }
    public void getBoundsInScreen(android.graphics.Rect r) {
        if (r != null) r.set(bounds);
    }
    public AccessibilityNodeInfo getRoot() { return root; }
    public int getType() { return type; }
    public boolean isActive() { return active; }
    public boolean isFocused() { return focused; }
}
