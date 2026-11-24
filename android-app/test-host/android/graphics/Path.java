package android.graphics;

/** 服务级 JVM 测试桩 */
public class Path {
    public float lastX, lastY;
    public Path moveTo(float x, float y) { lastX = x; lastY = y; return this; }
    public Path lineTo(float x, float y) { lastX = x; lastY = y; return this; }
}
