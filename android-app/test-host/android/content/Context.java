package android.content;

import java.util.HashMap;
import java.util.Map;

/** 服务级 JVM 测试桩：Context（具体类，全部方法免实现可直接子类化） */
public class Context {
    public static final String POWER_SERVICE = "power";
    public static final String ALARM_SERVICE = "alarm";
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String ACTIVITY_SERVICE = "activity";
    public static final String KEYGUARD_SERVICE = "keyguard";
    public static final int RECEIVER_NOT_EXPORTED = 4;
    public static final int MODE_PRIVATE = 0;

    private final Map<String, Object> services = new HashMap<String, Object>();
    private final Map<String, SharedPreferences> prefs = new HashMap<String, SharedPreferences>();
    public String pkgName = "com.zai.adshield";

    /** 测试注入系统服务 */
    public void putService(String name, Object svc) { services.put(name, svc); }

    public Object getSystemService(String name) { return services.get(name); }

    public String getPackageName() { return pkgName; }

    public Context getApplicationContext() { return this; }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        SharedPreferences p = prefs.get(name);
        if (p == null) { p = new SharedPreferences(); prefs.put(name, p); }
        return p;
    }

    public int checkPermission(String perm, int pid, int uid) {
        return android.content.pm.PackageManager.PERMISSION_DENIED;
    }

    public android.content.pm.PackageManager getPackageManager() {
        return new android.content.pm.PackageManager();
    }

    public android.content.res.Resources getResources() {
        return new android.content.res.Resources();
    }

    public android.content.ContentResolver getContentResolver() {
        return new android.content.ContentResolver();
    }

    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return null;
    }

    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return null;
    }

    public void unregisterReceiver(BroadcastReceiver receiver) { }

    public void startForeground(int id, android.app.Notification notification) { }

    public void stopForeground(int flags) { }

    public void startForeground(int id, android.app.Notification n, int foregroundServiceType) { }

    public ComponentName startService(Intent service) { return null; }

    public boolean deleteSharedPreferences(String name) { return true; }
}
