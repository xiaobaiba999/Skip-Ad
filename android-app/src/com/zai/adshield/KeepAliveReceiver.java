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
import android.os.SystemClock;

import com.zai.adshield.core.RestoreGuard;

/**
 * 保活接收器（v5.2.0 重构）——「能在后台自愈的绝不打扰用户」
 * ─────────────────────────────────────────────────────────
 * v5.2.0（issue#56：Redmi 25060RK16C 每次退后台/覆盖安装都要看 10 秒
 * 系统警告，Xiaomi 15 不用——根因：HyperOS 清理后台/覆盖安装会吊销
 * 无障碍登记；旧版三处放大痛点：通知点按无条件直跳系统设置、拉活窗口
 * 仅 6s、覆盖安装直接假设「系统必然已关闭」）：
 *  · 两态通知（RestoreGuard）：登记仍在 → 「打开卫士自动恢复（无系统
 *    设置、无 10 秒等待）」jump_restore；登记被吊销 → 「直达服务开关」
 *    jump_a11y（文案明确告知 10 秒安全确认属正常防诈设计，预期管理）；
 *  · 踢链自愈（SmartRevive）：自检/覆盖安装发现服务死了但登记仍在 →
 *    广播窗口内（goAsync 预算 ≤8.2s，步骤化单点 finish，绝不广播 ANR）
 *    踢 AMS 三轮再判——能自愈的零打扰；
 *  · 通知冷却：同态 10 分钟内不重发（5 分钟心跳周期不再反复 heads-up）；
 *  · 覆盖安装（MY_PACKAGE_REPLACED）不再预假设已关闭——先踢链，
 *    多数 ROM（如 Xiaomi 15 的 HyperOS 2）登记保留、进程起来即重绑。
 * v3.14.0：ADB 自愈（WRITE_SECURE_SETTINGS）整体移除（用户反馈：要电脑
 *  执行太麻烦）——统一走高优「待恢复」通知两态引导。
 * v5.6.0：零打扰持久模式回归（可选·明示授权）——用户三度反馈「每次
 *  退后台再进/装新版回来开守护都被要求进系统设置 + 强看 10 秒警告」。
 *  仅当用户 ADB 一次性授权 WRITE_SECURE_SETTINGS 后，SmartRevive 在
 *  登记吊销分支先把自己写回登记（幂等保序）再踢链重绑：零系统设置 ·
 *  零 10 秒警告；未授权设备行为与 v5.2.0 完全一致（保守两态通知）。
 * v3.13.0：夜间心跳 30~45 分钟窗口式非精确闹钟（息屏绝不唤醒设备）。
 * v3.9.0：心跳自检间隔 5 分钟（AdSkipService.HEARTBEAT_MS）。
 * v3.8.0：心跳自续期；startService 拉活已移除——无障碍服务受
 *  BIND_ACCESSIBILITY_SERVICE 签名级权限保护，任何应用都不能 bind 它。
 */
public class KeepAliveReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ALERT = "keep_alert";
    /** 自检闹钟（服务心跳 + 清理后台后 15s 自检共用此 action） */
    public static final String ACTION_SELF_CHECK = "com.zai.adshield.SELF_CHECK";

    /** v5.2.0：通知冷却（同态 10 分钟）——5 分钟心跳周期不再反复打扰 */
    private static final long NOTIFY_COOLDOWN_MS = 10 * 60 * 1000L;
    private static volatile long lastAutoRestoreNotifyAt = 0L;
    private static volatile long lastSettingsNotifyAt = 0L;

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        if (intent == null) return;
        String act = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(act);
        boolean replaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(act);
        boolean selfCheck = ACTION_SELF_CHECK.equals(act);
        if (!boot && !replaced && !selfCheck) return;

        /* ═══ v7.15.2：维护任务前置（keepOn 闸之上）═══
         * 死因链（issue 实锤：v7.8.0 后再无一条自动日志）：自动上传/订阅
         * 静默更新/每日自动导入三个维护任务只挂在本分支，而旧代码：
         *  ① 服务健康时 onServiceConnected 直接撤心跳 → 本分支永无触发源
         *（仅 MiTV 因 ROM 反复解绑而“意外存活”过——#89 孤例）；
         * ② keepOn 闸在本分支之前 → 用户关过“保活提醒”连维护任务一起断。
         * 修复：①服务存活期改排 30 分钟维护心跳（AdSkipService 侧）；
         * ②维护任务前置到闸外——“保活提醒”开关只管提醒/自愈，诊断性
         * 维护（各任务自带节流：未到期/服务未运行/息屏秒回）不受牵连。
         * 分档续期：活着 30 分钟（维护/存活自检）；死了 5 分钟（快速
         * 自愈提醒）——同一 PendingIntent 天然去重覆盖，不叠加闹钟。 */
        if (selfCheck) {
            boolean alive = AdSkipService.isAlive();
            if (!alive) {
                // v7.15.12：断线首检时戳（重连日志可回答「日志为什么断流」；
                // 进程重启间 prefs 持久——覆盖安装后新进程能续上首检时戳）
                AdSkipService.markDeadSince(ctx);
            }
            long cadence = alive ? AdSkipService.ALIVE_HEARTBEAT_MS : AdSkipService.HEARTBEAT_MS;
            AdSkipService.scheduleNextHeartbeat(ctx, System.currentTimeMillis() + cadence, true);
            // v4.0.0：心跳顺带触发订阅 24h 静默更新（无订阅/未到期秒回，零开销）
            Thread subThread = new Thread(() -> {
                try {
                    SubscriptionManager.maybeSilentUpdate(ctx);
                } catch (Throwable ignored) {
                }
            }, "sub-update");
            subThread.setDaemon(true);
            subThread.start();
            // v7.15.0：心跳顺带触发 id667 每日后台自动导入（未到期/已有源秒回，零开销）
            Thread subImportThread = new Thread(() -> {
                try {
                    SubscriptionManager.maybeAutoImport(ctx);
                } catch (Throwable ignored) {
                }
            }, "sub-auto-import");
            subImportThread.setDaemon(true);
            subImportThread.start();
            // v7.15.5：心跳顺带订阅瘦身评估（市场源零命中规则停用/复评——
            // 纯内存计数 + 统一持久化，30 分钟一档零 prefs 热路径开销）
            Thread subPruneThread = new Thread(() -> {
                try {
                    SubscriptionManager.maybePruneRemoteRules(ctx);
                } catch (Throwable ignored) {
                }
            }, "sub-prune");
            subPruneThread.setDaemon(true);
            subPruneThread.start();
            // v4.2.0：心跳顺带检查自动日志提交（v6.1.0：8h 一日 3 次；v7.15.2
            // 维护前置：未到期/服务未运行/息屏秒回零开销；成功才计时——失败
            // 网络异常下一轮 30 分钟心跳自然重试，绝不夜间耗电）
            try {
                AdSkipService.maybeAutoUploadLog(ctx);
            } catch (Throwable ignored) {
            }
        }

        /* v7.15.12 更新存活链：覆盖安装完成日志（无条件记——无论服务死活，
         * 日志考古锚点：用户反馈「更新后日志不记录」时能直接看到安装时刻；
         * appendLogStatic 在 instance==null 时 prefs 直写，重连后可见）。 */
        if (replaced) {
            AdSkipService.appendLogStatic(ctx, "📦 覆盖安装完成（v" + AdSkipService.versionName(ctx)
                    + "）——服务自检踢链中；若未自动恢复请点通知栏「待恢复」提醒");
        }

        if (!ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE)
                .getBoolean("keepOn", false)) {
            return; // 用户已手动关闭保活提醒（只管提醒/自愈，维护任务已前置闸外）
        }

        /* ═══ v5.2.0 统一智能判定（提醒/自愈面）═══
         * 活着 → 零打扰；死了 → SmartRevive 步骤化踢链（自愈优先），
         * 终判两态通知。开机特殊：系统正在拉起服务，给足 8s 缓冲后
         * 仍死则排 +2s 自检闹钟（全新广播窗口走智能流程——onReceive
         * 返回后排程器不受 goAsync 预算约束，沿用 v3.8.1 验证过的路径）。 */
        if (AdSkipService.isAlive()) return;

        final PendingResult result = goAsync();
        if (boot) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (!AdSkipService.isAlive()) {
                        AdSkipService.scheduleNextHeartbeat(ctx, System.currentTimeMillis() + 2000);
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (result != null) result.finish();
                }
            }, 8000);
            return;
        }
        // 自检 / 覆盖安装：600ms 后启动步骤化踢链（预算 ≤8.2s < 10s 广播窗口）
        if (replaced) {
            // v7.15.12：断线首检时戳（服务死着才走到这里——活着的在上方早退）
            AdSkipService.markDeadSince(ctx);
        }
        new Handler(Looper.getMainLooper()).postDelayed(new SmartRevive(ctx, result, replaced), 600);
    }

    /**
     * v5.2.0 步骤化踢链自愈（静态具名类：规避 d8 8.2 匿名类 NPE bug）。
     * 时间轴（相对构造）：600ms 首查 → 踢1 → +2.5s 查/踢2 → +2.5s 查/踢3
     * → +2.5s 终判两态通知。每条路径恰好一次 finish（绝不广播 ANR）。
     * 步骤语义：alive → 零打扰收尾；吊销 → 直达开关通知（唯一出路）；
     * 仍登记 → 踢 AMS 重绑，三轮踢完仍死才按登记态两态通知。
     * v7.15.12：fromReplace——覆盖安装触发的踢链终判仍死时排 +30s
     * 复活梯子（重绑常在安装后 10~30s 才完成，8.2s 预算内踢不完；
     * 梯子并入 5 分钟心跳循环，绝下「一次失败即弃」）。自检触发的踢链
     * 维持 v5.2.0 节奏（5 分钟心跳已够——清理后台场景重绑预期快）。 */
    private static final class SmartRevive implements Runnable {
        private final Context ctx;
        private final PendingResult result;
        private final boolean fromReplace;
        private final Handler h = new Handler(Looper.getMainLooper());
        private int step = 0;

        SmartRevive(Context ctx, PendingResult result, boolean fromReplace) {
            this.ctx = ctx;
            this.result = result;
            this.fromReplace = fromReplace;
        }

        @Override
        public void run() {
            try {
                if (AdSkipService.isAlive()) {          // 自愈成功（含等待期 AMS 自行重绑）
                    finish();
                    return;
                }
                boolean registered = AdSkipService.registrationPresent(ctx);
                if (!registered) {
                    /* v5.6.0 零打扰持久模式：登记被吊销先尝试免设置自愈
                     * （仅用户 ADB 授权过 WRITE_SECURE_SETTINGS 的设备生效）。
                     * 写回成功 → 当作登记仍在继续踢链重绑（零系统设置 · 零
                     * 10 秒警告）；未授权/失败 → 保守两态通知照旧。 */
                    if (AdSkipService.canSecureSelfHeal(ctx)
                            && AdSkipService.secureSelfHeal(ctx)) {
                        step++;
                        kickA11y(ctx);
                        h.postDelayed(this, 2500);
                        return;
                    }
                    notifyAlert(ctx, false);
                    reviveLadder();
                    finish();
                    return;
                }
                if (step < 3) {                         // 登记仍在：踢一轮再等 2.5s
                    step++;
                    kickA11y(ctx);
                    h.postDelayed(this, 2500);
                    return;
                }
                // 三轮踢完仍死：按此刻登记态两态通知
                notifyAlert(ctx, AdSkipService.registrationPresent(ctx));
                reviveLadder();
                finish();
            } catch (Throwable t) {                     // 任何异常兜底收尾（绝不断链）
                try {
                    notifyAlert(ctx, AdSkipService.registrationPresent(ctx));
                } catch (Throwable ignored) {
                }
                reviveLadder();
                finish();
            }
        }

        private void finish() {
            if (result != null) {
                try {
                    result.finish();
                } catch (Throwable ignored) {
                }
            }
        }

        /** v7.15.12：覆盖安装踢链终判仍死 → +30s 复活梯子（重绑常在
         * 安装后 10~30s 才完成；selfCheck 分支已排的 +5min 被提前到 30s
         * ——同一 PendingIntent 天然去重覆盖不叠加；30s 后并入 5 分钟
         * 心跳循环持续自愈/提醒，绝下「一次失败即弃」）。 */
        private void reviveLadder() {
            if (!fromReplace) return;
            try {
                AdSkipService.scheduleNextHeartbeat(ctx,
                        System.currentTimeMillis() + 30_000L);
            } catch (Throwable ignored) {
            }
        }
    }

    /** v5.2.0：踢 AMS（进程存活时查询已启用服务列表常触发系统重绑） */
    private static void kickA11y(Context ctx) {
        try {
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager)
                            ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                am.getEnabledAccessibilityServiceList(
                        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * v5.2.0 两态高优「待恢复」通知（文案与跳转全部由登记状态决定）：
     *  · registered=true  → 「打开卫士自动恢复」jump_restore（App 内 15s
     *    深挖拉活，全程无系统设置、无 10 秒等待）；
     *  · registered=false → 「直达服务开关」jump_a11y（直达本服务详情页
     *    一步开启；文案明示 10 秒安全确认属厂商防诈设计——预期管理）。
     * 同态 10 分钟冷却（心跳周期不重复打扰）；态切换立即通知。
     */
    static void notifyAlert(Context ctx, boolean registered) {
        try {
            int mode = registered ? RestoreGuard.NOTIFY_AUTO_RESTORE : RestoreGuard.NOTIFY_TO_SETTINGS;
            long now = SystemClock.elapsedRealtime();
            if (registered) {
                if (now - lastAutoRestoreNotifyAt < NOTIFY_COOLDOWN_MS) return;
                lastAutoRestoreNotifyAt = now;
            } else {
                if (now - lastSettingsNotifyAt < NOTIFY_COOLDOWN_MS) return;
                lastSettingsNotifyAt = now;
            }
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ALERT, "服务恢复提醒",
                        NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("服务被系统关闭/解绑时提醒一键恢复");
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(ctx, CHANNEL_ALERT);
            } else {
                b = new Notification.Builder(ctx);
            }
            Intent it = new Intent(ctx, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            it.putExtra(registered ? "jump_restore" : "jump_a11y", true);
            PendingIntent pi = PendingIntent.getActivity(ctx, 2, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String family = RestoreGuard.familyOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL);
            b.setContentTitle(RestoreGuard.alertTitle(mode))
                    .setContentText(RestoreGuard.alertText(mode))
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(RestoreGuard.alertBigText(mode, RestoreGuard.familyLabel(family))))
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setColor(0xFF10B981);
            nm.notify(AdSkipService.NOTIF_ALERT, b.build());
        } catch (Throwable ignored) {
        }
    }
}
