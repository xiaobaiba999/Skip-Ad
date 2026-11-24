package android.app;

/** 服务级 JVM 测试桩 */
public class NotificationManager {
    public static final int IMPORTANCE_MIN = 1;
    public static final int IMPORTANCE_DEFAULT = 3;

    public void createNotificationChannel(NotificationChannel ch) { }
    public void deleteNotificationChannel(String id) { }
    public void notify(int id, Notification n) { }
    public void cancel(int id) { }
}
