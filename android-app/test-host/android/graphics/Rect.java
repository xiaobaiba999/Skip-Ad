package android.graphics;

/** 服务级 JVM 测试桩 */
public class Rect {
    public int left, top, right, bottom;

    public Rect() { }
    public Rect(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }

    public final int width() { return right - left; }
    public final int height() { return bottom - top; }
    public final int centerX() { return (left + right) >> 1; }
    public final int centerY() { return (top + bottom) >> 1; }
    public boolean isEmpty() { return right <= left || bottom <= top; }
    public boolean contains(int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
    public void set(Rect r) { left = r.left; top = r.top; right = r.right; bottom = r.bottom; }
    public void set(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public void offset(int dx, int dy) { left += dx; top += dy; right += dx; bottom += dy; }
}
