package android.database;

import android.os.Handler;

/** 服务级 JVM 测试桩 */
public abstract class ContentObserver {
    public ContentObserver(Handler handler) { }
    public void onChange(boolean selfChange) { }
}
