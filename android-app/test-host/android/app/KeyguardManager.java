package android.app;

/** 服务级 JVM 测试桩：锁屏恒不在场（解锁路径不干扰扫描回归） */
public class KeyguardManager {
    public boolean isKeyguardLocked() { return false; }
    public boolean inKeyguardRestrictedInputMode() { return false; }
}
