package com.zai.adshield;

import android.content.Context;

/** 服务级 JVM 测试桩：保活接收器（仅静态常量 + 通知桩） */
public class KeepAliveReceiver extends android.content.BroadcastReceiver {
    public static final String ACTION_SELF_CHECK = "com.zai.adshield.SELF_CHECK";

    @Override
    public void onReceive(Context context, android.content.Intent intent) { }

    public static void notifyAlert(Context ctx, boolean registered) { }
}
