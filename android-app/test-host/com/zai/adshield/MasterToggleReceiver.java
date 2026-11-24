package com.zai.adshield;

import android.content.Context;

/** 服务级 JVM 测试桩：主开关接收器 */
public class MasterToggleReceiver extends android.content.BroadcastReceiver {
    public static final String ACTION_TOGGLE_MASTER = "com.zai.adshield.TOGGLE_MASTER";

    @Override
    public void onReceive(Context context, android.content.Intent intent) { }
}
