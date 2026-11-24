package android.app;

/** 服务级 JVM 测试桩 */
public class AlarmManager {
    public static final int RTC = 1;

    public void set(int type, long at, PendingIntent pi) { }
    public void setWindow(int type, long at, long window, PendingIntent pi) { }
    public void setExact(int type, long at, PendingIntent pi) { }
    public void setAndAllowWhileIdle(int type, long at, PendingIntent pi) { }
    public void cancel(PendingIntent pi) { }
}
