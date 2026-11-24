package android.view.accessibility;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务级 JVM 测试桩（v7.15.8 回归基建）：模拟真实 AccessibilityNodeInfo 语义。
 * 关键语义对齐：
 *  · 每节点持有 windowId（与其所属窗口一致——getRootInActiveWindow 与
 *    getWindows() 枚举出的同窗节点 getWindowId() 相同，去重逻辑可验）；
 *  · findAccessibilityNodeInfosByText：BFS 全树 text/desc contains 匹配
 *    （与系统侧 C++ 子树搜索行为同构）；
 *  · performAction(ACTION_CLICK) 记录点击（断言「谁被点了」）。
 */
public class AccessibilityNodeInfo {
    public static final int ACTION_CLICK = 1 << 0;
    public static final int ACTION_BACKWARD = 1 << 1;

    // ── 树结构（测试直接装配）──
    public AccessibilityNodeInfo parent;
    public final List<AccessibilityNodeInfo> children = new ArrayList<AccessibilityNodeInfo>();

    // ── 属性 ──
    public CharSequence text = "";
    public CharSequence contentDescription = "";
    public CharSequence packageName = "";
    public CharSequence className = "";
    public String viewIdResourceName;
    public int windowId;
    public AccessibilityWindowInfo window; // 反向引用（getWindow() 用）
    public boolean clickable, visibleToUser = true, scrollable, editable;
    public android.graphics.Rect boundsInScreen = new android.graphics.Rect();

    // ── 点击记录（测试断言用）──
    public boolean clicked = false;

    public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo n) {
        return n;
    }

    public CharSequence getText() { return text; }
    public CharSequence getContentDescription() { return contentDescription; }
    public CharSequence getPackageName() { return packageName; }
    public CharSequence getClassName() { return className; }
    public String getViewIdResourceName() { return viewIdResourceName; }
    public int getWindowId() { return windowId; }
    public AccessibilityWindowInfo getWindow() { return window; }
    public boolean isClickable() { return clickable; }
    public boolean isVisibleToUser() { return visibleToUser; }
    public boolean isScrollable() { return scrollable; }
    public boolean isEditable() { return editable; }
    public boolean refresh() { return true; }

    public void getBoundsInScreen(android.graphics.Rect r) {
        if (r == null) return;
        r.left = boundsInScreen.left;
        r.top = boundsInScreen.top;
        r.right = boundsInScreen.right;
        r.bottom = boundsInScreen.bottom;
    }

    public int getChildCount() { return children.size(); }
    public AccessibilityNodeInfo getChild(int i) {
        if (i < 0 || i >= children.size()) return null;
        return children.get(i);
    }
    public AccessibilityNodeInfo getParent() { return parent; }

    public boolean performAction(int action) {
        if (action == ACTION_CLICK) clicked = true;
        return true;
    }

    /** BFS 全树搜索：text/desc contains（系统侧子树搜索同构） */
    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String s) {
        List<AccessibilityNodeInfo> out = new ArrayList<AccessibilityNodeInfo>();
        if (s == null || s.length() == 0) return out;
        List<AccessibilityNodeInfo> q = new ArrayList<AccessibilityNodeInfo>();
        q.add(this);
        while (!q.isEmpty() && out.size() < 32) {
            AccessibilityNodeInfo n = q.remove(0);
            if (n == null) continue;
            CharSequence t = n.text == null ? "" : n.text;
            CharSequence d = n.contentDescription == null ? "" : n.contentDescription;
            if (t.toString().contains(s) || d.toString().contains(s)) out.add(n);
            q.addAll(n.children);
        }
        return out;
    }

    /** 测试装配：挂子节点（自动回填 parent/windowId/pkg） */
    public AccessibilityNodeInfo add(AccessibilityNodeInfo c) {
        c.parent = this;
        c.windowId = windowId;
        if (c.packageName == null || c.packageName.length() == 0) c.packageName = packageName;
        children.add(c);
        return this;
    }
}
