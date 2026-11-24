package android.provider;

import android.net.Uri;

/** 服务级 JVM 测试桩 */
public class Settings {
    public static class Secure {
        public static final String ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services";
        public static final String ACCESSIBILITY_ENABLED = "accessibility_enabled";

        public static String getString(android.content.ContentResolver cr, String key) {
            return ""; // 桩：登记表恒空（RestoreGuard 自检路径走「未登记」分支）
        }

        public static boolean putString(android.content.ContentResolver cr, String key, String v) {
            return true;
        }

        public static Uri getUriFor(String key) { return Uri.parse("content://settings/secure/" + key); }
    }
}
