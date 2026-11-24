package android.os;

/** 服务级 JVM 测试桩 */
public class SystemClock {
    public static long elapsedRealtime() { return System.currentTimeMillis(); }
    public static long uptimeMillis() { return System.currentTimeMillis(); }
}
