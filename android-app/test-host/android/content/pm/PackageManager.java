package android.content.pm;

/** 服务级 JVM 测试桩 */
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public PackageInfo getPackageInfo(String pkg, int flags) throws NameNotFoundException {
        PackageInfo pi = new PackageInfo();
        pi.versionName = "7.15.7";
        pi.versionCode = 85700;
        return pi;
    }

    public static class NameNotFoundException extends Exception { }
}
