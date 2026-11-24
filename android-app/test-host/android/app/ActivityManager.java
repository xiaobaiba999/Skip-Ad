package android.app;

/** 服务级 JVM 测试桩 */
public class ActivityManager {
    public static class MemoryInfo {
        public long totalMem;
        public long availMem;
        public long threshold;
        public boolean lowMemory;
    }

    public void getMemoryInfo(MemoryInfo out) {
        out.totalMem = 8L * 1024 * 1024 * 1024;
        out.availMem = 4L * 1024 * 1024 * 1024;
        out.lowMemory = false;
    }
}
