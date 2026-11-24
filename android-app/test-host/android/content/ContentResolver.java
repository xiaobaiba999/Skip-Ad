package android.content;

import android.net.Uri;

/** 服务级 JVM 测试桩 */
public class ContentResolver {
    public void registerContentObserver(Uri uri, boolean notifyForDescendants,
                                        android.database.ContentObserver observer) { }
    public void unregisterContentObserver(android.database.ContentObserver observer) { }
}
