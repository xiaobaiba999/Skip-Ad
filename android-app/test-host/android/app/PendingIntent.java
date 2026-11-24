package android.app;

import android.content.Context;
import android.content.Intent;

/** 服务级 JVM 测试桩 */
public class PendingIntent {
    public static final int FLAG_UPDATE_CURRENT = 0x8000000;
    public static final int FLAG_IMMUTABLE = 0x4000000;

    public static PendingIntent getActivity(Context ctx, int requestCode, Intent it, int flags) {
        return new PendingIntent();
    }
    public static PendingIntent getBroadcast(Context ctx, int requestCode, Intent it, int flags) {
        return new PendingIntent();
    }
    public void cancel() { }
}
