package android.content;

/** 服务级 JVM 测试桩 */
public abstract class BroadcastReceiver {
    public abstract void onReceive(Context context, Intent intent);
}
