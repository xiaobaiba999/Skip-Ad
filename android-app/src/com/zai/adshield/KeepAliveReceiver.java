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
 * 保活接收器（v3.9.0）——「开启后不手动关闭就一直保持开启」
 * ─────────────────────────────────────────────────────────────
 * v3.9.0：心跳自检间隔 15→5 分钟（AdSkipService.HEARTBEAT_MS）——服务被杀后
 *  最多 5 分钟内发现并自愈/提醒，配合 WRITE_SECURE_SETTINGS 全自动恢复；
 *  「清理后台重进又要手动开」的发现延迟大幅缩短。其余机制沿用 v3.8.0：
 * v3.8.0 三项升级（issue#10 二轮「清理后台重进又要手动开无障碍」）：
 *  · 心跳自续期：每次自检闹钟触发后由接收器排下一轮（v3.7.0 的
 *    setInexactRepeating 可被系统无限推迟）——精确闹钟优先，未授权降级
 *    setAndAllowWhileIdle，心跳链永不断；
 *  · WRITE_SECURE_SETTINGS 自愈（GKD 进阶玩法同款）：adb 一次性授权后，
 *    服务被杀/覆盖安装被系统关闭 → 直接写 Settings.Secure 重绑/写回开启，
 *    全自动无需手动；未授权 → 高优通知引导一步恢复（Android 11+ 直达
 *    本服务详情页，只剩一个开关）；
 *  · startService 拉活已移除——无障碍服务受 BIND_ACCESSIBILITY_SERVICE
 *    签名级权限保护，任何应用（含自己）都不能 start/bind 它，v3.6.0 起
 *    的「自动拉活重绑」实为 SecurityException，从未真正生效。
 */
public class KeepAliveReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ALERT = "keep_alert";
    /** 自检闹钟（服务心跳 + 清理后台后 15s 自检共用此 action） */
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

        if (selfCheck) {
            // v3.8.0：先续期心跳（无论服务死活，心跳链永不中断），再自检
            AdSkipService.scheduleNextHeartbeat(ctx, System.currentTimeMillis() + AdSkipService.HEARTBEAT_MS);
            if (AdSkipService.instance != null) return; // 服务活着，无需动作
            healOrNotify(ctx);
            return;
        }
        if (AdSkipService.instance != null) return;

        if (replaced) {
            // 覆盖安装：系统必然已关闭无障碍服务（Android 安全设计）
            // v3.8.0：授权过 WRITE_SECURE_SETTINGS → 写回开启状态全自动恢复
            healOrNotify(ctx);
            return;
        }
        // 开机：系统要拉起仍启用的无障碍服务，给 8s（goAsync 预算内）再查
        // （注意：匿名内部类会触发 d8 8.2 NPE bug，一律用 lambda）
        final PendingResult result = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (AdSkipService.instance == null) {
                    // v3.8.1：不在此处直接自愈——postDelayed 回调时 onReceive 已返回，
                    // 再调 goAsync() 拿不到广播窗口（自愈轮询线程失去系统保护）；
                    // 改排 2s 后自检闹钟，在全新广播窗口走标准自检/自愈路径
                    AdSkipService.scheduleNextHeartbeat(ctx, System.currentTimeMillis() + 2000);
                }
            } finally {
                if (result != null) result.finish();
            }
        }, 8000);
    }

    /** v3.8.0：服务已死 → 有 WRITE_SECURE_SETTINGS 走系统正道自愈重绑；
     *  无授权 → 高优「待恢复」通知（Android 11+ 点按直达本服务详情页一个开关） */
    private void healOrNotify(final Context ctx) {
        if (!AdSkipService.hasSecureWrite(ctx)) {
            notifyAlert(ctx);
            return;
        }
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                AdSkipService.selfHealBind(ctx);
                // 等系统完成重绑（bind 拉起进程 + onServiceConnected）
                for (int i = 0; i < 16 && AdSkipService.instance == null; i++) {
                    Thread.sleep(300); // ≤4.8s（goAsync 10s 预算内）
                }
                if (AdSkipService.instance == null) notifyAlert(ctx); // 自愈未成 → 提醒兜底
            } catch (Throwable ignored) {
            } finally {
                if (result != null) result.finish();
            }
        }).start();
    }

    /** 高优先级「待恢复」通知：点按打开 App（自动直达本服务无障碍详情页） */
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
                    .setContentText("系统关闭了无障碍服务，点按直达服务开关恢复（Android 11+ 一步）")
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("系统在覆盖安装、重启或深度清理后会关闭无障碍服务，这是 Android 的安全设计（已 adb 授权 WRITE_SECURE_SETTINGS 的设备会全自动恢复）。\n"
                                    + "点按本通知 → 直达「开屏卫士」服务开关 → 打开即恢复（Android 11+ 一步直达；旧系统在无障碍列表中找到「开屏卫士」开启）。学习数据全部保留。"))
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFF10B981);
            nm.notify(AdSkipService.NOTIF_ALERT, b.build());
        } catch (Throwable ignored) {
        }
    }
}
