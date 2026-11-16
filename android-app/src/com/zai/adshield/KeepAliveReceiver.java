package com.zai.adshield;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * 保活接收器（v3.6.0）——「开启后不手动关闭就一直保持开启」
 * ─────────────────────────────────────────────────────────────
 * 背景（真机反馈）：App 被后台清理/覆盖安装新版本后，系统会关闭无障碍服务，
 * 每次都得重新去设置里开。Android 出于安全设计禁止应用自行重开无障碍服务
 * （WRITE_SECURE_SETTINGS 需 adb 授权），业界（GKD/李跳跳）同样无法绕过。
 *
 * 本接收器做到系统允许的极限：
 *  · MY_PACKAGE_REPLACED（覆盖安装后）：系统必然已关服务 → 立即发高优先级
 *    通知「秒跳引擎待恢复」，点按直达 App 并自动跳无障碍设置页（两步恢复）；
 *  · BOOT_COMPLETED（重启后）：延迟 8s 等系统拉起已启用的服务，仍未连上
 *    才提醒（服务正常自启则不打扰）；
 *  · SELF_CHECK（v3.6.0 自检闹钟）：AdSkipService.onTaskRemoved 后台清理时
 *    排的自检——进程被杀但非 force-stop 的 ROM 上仍能触发，服务已死即提醒；
 *  · keepOn 偏好控制：用户在 App 内手动关闭保活提醒后不再发。
 * 配合 AdSkipService 的前台常驻通知、onTaskRemoved 即时提醒、
 * 打开 App 自动拉活重绑（tryAutoRevive）与电池优化白名单引导。
 */
public class KeepAliveReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ALERT = "keep_alert";
    /** v3.6.0：清理后台后的延迟自检（onTaskRemoved 排闹钟） */
    public static final String ACTION_SELF_CHECK = "com.zai.adshield.SELF_CHECK";

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        if (intent == null) return;
        String act = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(act);
        boolean replaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(act);
        boolean selfCheck = ACTION_SELF_CHECK.equals(act);
        if (!boot && !replaced && !selfCheck) return;
        if (!ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE)
                .getBoolean("keepOn", false)) {
            return; // 用户已手动关闭保活提醒
        }
        if (AdSkipService.instance != null) return; // 服务活着，无需提醒

        if (replaced) {
            // 覆盖安装：系统必然已关闭服务，立即提醒
            notifyAlert(ctx);
            return;
        }
        // 开机：系统要拉起仍启用的无障碍服务，给 8s（goAsync 预算内）再查
        // （注意：匿名内部类会触发 d8 8.2 NPE bug，一律用 lambda）
        final PendingResult result = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (AdSkipService.instance == null) notifyAlert(ctx);
            } finally {
                if (result != null) result.finish();
            }
        }, 8000);
    }

    /** 高优先级「待恢复」通知：点按打开 App（自动跳无障碍设置）；
     *  v3.6.0 提升为包内可见静态方法——AdSkipService.onTaskRemoved 即时调用 */
    static void notifyAlert(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ALERT, "服务恢复提醒",
                        NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("系统关闭了无障碍服务时提醒一键恢复");
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(ctx, CHANNEL_ALERT);
            } else {
                b = new Notification.Builder(ctx);
            }
            Intent it = new Intent(ctx, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            it.putExtra("jump_a11y", true);
            PendingIntent pi = PendingIntent.getActivity(ctx, 2, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentTitle("秒跳引擎待恢复")
                    .setContentText("系统更新/重启关闭了无障碍服务（Android 安全设计），点按两步恢复")
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("系统在覆盖安装或重启后会关闭无障碍服务，这是 Android 的安全设计，任何应用都无法绕过。\n"
                                    + "点按本通知 → 自动跳转无障碍设置 → 找到「开屏卫士」开启即恢复（学习数据全部保留）。"))
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFF10B981);
            nm.notify(AdSkipService.NOTIF_ALERT, b.build());
        } catch (Throwable ignored) {
        }
    }
}
