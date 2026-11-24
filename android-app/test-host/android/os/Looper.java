package android.os;

/** 服务级 JVM 测试桩 */
public class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return MAIN; }
}
