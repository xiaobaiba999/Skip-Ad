package android.os;

/** 服务级 JVM 测试桩 */
public class PowerManager {
    private boolean interactive = true;
    public void setInteractive(boolean v) { interactive = v; }
    public boolean isInteractive() { return interactive; }
}
