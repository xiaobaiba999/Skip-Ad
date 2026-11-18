package com.zai.adshield;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * v4.5.0 通知栏快捷开关接收器——「系统开关与 App 开关彻底解耦」的用户核心诉求
 * ─────────────────────────────────────────────────────────────────
 * 设计目标（用户原话）：
 *  「实现在系统外有 app 控制，系统一直打开状态，由开屏卫士控制，卫士关闭就
 *   不再触发广告跳转，打开就是打开了，也不用跳转到系统里重新打开权限。」
 *
 *  · 系统无障碍开关【永不触碰】（代码库零 disableSelf 调用，服务解绑后
 *    onUnbind 保留实例等重绑）——10 秒强制确认警告只存在于「用户自己去
 *    系统设置关掉再开」这一条路，App 的任何开关都不走那条路；
 *  · 卫士开关 = masterOn（App 内一键 / 通知栏一键，两处等价）：
 *    关 → 零扫描零点击零学习（服务保活不受影响，恢复零延迟）；
 *    开 → 立即恢复守护，全程无系统交互；
 *  · 本接收器仅收【显式 Intent】（exported=false 且无 intent-filter），
 *    只有本应用通知栏按钮能触发，第三方应用无法伪造广播。
 *
 * 注：静态内部类/lambda 规约沿用（规避 d8 8.2 匿名类 NPE bug）。
 */
public class MasterToggleReceiver extends BroadcastReceiver {

    public static final String ACTION_TOGGLE_MASTER = "com.zai.adshield.TOGGLE_MASTER";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        // 仅认显式投递（PendingIntent 显式 Intent 允许无 action；带 action 时
        // 必须是本接收器的专属 action——第三方隐式广播一律忽略）
        String act = intent.getAction();
        if (act != null && !ACTION_TOGGLE_MASTER.equals(act)) return;
        // 翻转主开关（服务活着=改内存态即时生效；死了=持久化，重连时载入）
        boolean nowOn = !AdSkipService.masterState(ctx);
        AdSkipService.setMaster(ctx, nowOn);
        // 常驻通知文案/按钮随开关联动（标题：守护中/已暂停）
        AdSkipService.refreshKeepNotification(ctx);
    }
}
