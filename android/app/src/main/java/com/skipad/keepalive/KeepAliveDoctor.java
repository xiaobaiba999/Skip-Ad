package com.skipad.keepalive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import com.skipad.log.StructuredLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 全品牌保活体检医生（v5.4.0 · v5.2.0 体检体系升级）。
 *
 * <p>职责边界（严格系统权限规范——本应用是工具不是外挂）：
 * <ul>
 *   <li><b>只做三件事</b>：体检（检测后台限制项）+ 引导（直达对应设置页）+
 *       事件驱动自愈（服务被系统回收后的两态通知）。</li>
 *   <li><b>绝不做的三件事（红线）</b>：
 *       <ol>
 *         <li>不滥用前台服务（无障碍服务本身由系统常驻绑定，无需 FGS 保命）</li>
 *         <li>不用闹钟定时唤醒（AlarmManager 精确闹钟唤醒属于违规保活，厂商与
 *             Google Play 均重点打击——李跳跳出局的直接原因之一）</li>
 *         <li>不用双进程互拉（JobScheduler 轮询互拉同样违规）</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <p>品牌矩阵（12 品牌 + Wear 轻设备）：每品牌输出体检项清单 + 设置页直达 Intent。
 * 服务吊销自愈：两态通知——登记仍在 → 点通知自动拉活；真被吊销 → 直达服务开关页，
 * 文案明示「10 秒确认是系统防诈设计，请手动确认」（绝不自动点系统确认按钮）。
 */
public final class KeepAliveDoctor {

    /** 品牌 */
    public enum Brand {
        XIAOMI("小米"), REDMI("Redmi"), HUAWEI("华为"), HONOR("荣耀"),
        OPPO("OPPO"), VIVO("vivo"), SAMSUNG("三星"), MEIZU("魅族"),
        ONEPLUS("一加"), REALME("真我"), LENOVO("联想"), ZTE("中兴"),
        NUBIA("努比亚"), WEAR_LITE("Wear 轻设备"), OTHER("其他");

        public final String label;

        Brand(String label) {
            this.label = label;
        }
    }

    /** 体检项 */
    public static final class CheckItem {
        /** 稳定键（体检报告/结构化日志主键） */
        public final String key;
        public final String title;
        public final String why;
        /** critical = 不设置则服务大概率被系统回收（Redmi 入门机 10 秒警告三大诱因级） */
        public final boolean critical;

        public CheckItem(String key, String title, String why, boolean critical) {
            this.key = key;
            this.title = title;
            this.why = why;
            this.critical = critical;
        }
    }

    /** 服务登记状态（两态通知依据） */
    public enum RegistrationState {ALIVE, REVOKED}

    private static final String CHANNEL_ID = "skipad_keepalive";
    private static final int NOTIFICATION_ID = 50400;

    /** 无状态工具类；保留实例形态供 SkipAdService 持有（后续可挂载体检缓存） */
    public KeepAliveDoctor() {
    }

    // ───────────────────────── 品牌识别 ─────────────────────────

    /** 按 Build.MANUFACTURER / MODEL 识别品牌 */
    public static Brand detect() {
        String manufacturer = String.valueOf(Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        String model = String.valueOf(Build.MODEL).toUpperCase(Locale.ROOT);
        if (model.contains("WATCH") || model.contains("WEAR")) {
            return Brand.WEAR_LITE;
        }
        if (manufacturer.contains("xiaomi")) {
            return manufacturer.contains("redmi") ? Brand.REDMI : Brand.XIAOMI;
        }
        if (manufacturer.contains("huawei")) {
            return Brand.HUAWEI;
        }
        if (manufacturer.contains("honor") || manufacturer.contains("hihonor")) {
            return Brand.HONOR;
        }
        if (manufacturer.contains("oppo")) {
            return Brand.OPPO;
        }
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            return Brand.VIVO;
        }
        if (manufacturer.contains("samsung")) {
            return Brand.SAMSUNG;
        }
        if (manufacturer.contains("meizu")) {
            return Brand.MEIZU;
        }
        if (manufacturer.contains("oneplus")) {
            return Brand.ONEPLUS;
        }
        if (manufacturer.contains("realme")) {
            return Brand.REALME;
        }
        if (manufacturer.contains("lenovo") || manufacturer.contains("motorola")) {
            return Brand.LENOVO;
        }
        if (manufacturer.contains("nubia")) {
            return Brand.NUBIA;
        }
        if (manufacturer.contains("zte")) {
            return Brand.ZTE;
        }
        return Brand.OTHER;
    }

    // ───────────────────────── 品牌体检矩阵 ─────────────────────────

    /** 各品牌体检清单（矩阵与 docs/OPTIMIZATION-v5.4.0.md §2.3 一致） */
    public static List<CheckItem> checklist(Brand brand) {
        List<CheckItem> items = new ArrayList<>();
        switch (brand) {
            case XIAOMI:
            case REDMI:
                items.add(new CheckItem("miui_autostart", "自启动", "清理后台/重启后服务被杀，强制看 10 秒警告的诱因一", true));
                items.add(new CheckItem("miui_battery", "省电策略 → 无限制", "神隐省电冻结进程，诱因二", true));
                items.add(new CheckItem("miui_clean_whitelist", "清理后台白名单", "一键清理连服务一起杀，诱因三", true));
                items.add(new CheckItem("miui_notification_access", "通知使用权", "MIUI 额外的通知监听授权，缺少时两态通知不显示", false));
                break;
            case HUAWEI:
                items.add(new CheckItem("hw_startup", "启动管理 → 允许自启动/关联启动/后台活动", "EMUI/HarmonyOS 三连开关，任一关闭服务即被回收", true));
                items.add(new CheckItem("hw_battery", "电池优化 → 不优化", "受保护应用机制", true));
                items.add(new CheckItem("hw_lock_recent", "最近任务下拉加锁", "手动上划清理不受锁限制", false));
                break;
            case HONOR:
                items.add(new CheckItem("honor_startup", "启动管理三连开关", "与华为同源（MagicOS）", true));
                items.add(new CheckItem("honor_battery", "电池优化白名单", "", true));
                break;
            case OPPO:
            case REALME:
            case ONEPLUS:
                items.add(new CheckItem("coloros_autostart", "自启动管理", "ColorOS 冷启动管控", true));
                items.add(new CheckItem("coloros_bg_popup", "后台弹出界面权限", "ColorOS 特有：无此权限则吊销自愈通知无法弹出", true));
                items.add(new CheckItem("coloros_battery", "电池 → 允许完全后台行为", "", true));
                break;
            case VIVO:
                items.add(new CheckItem("funtouch_autostart", "自启动", "", true));
                items.add(new CheckItem("funtouch_bg_popup", "后台弹出界面", "FuntouchOS 特有权限", true));
                items.add(new CheckItem("funtouch_lock", "最近任务加锁", "下滑加锁防止一键清理", false));
                break;
            case SAMSUNG:
                items.add(new CheckItem("oneui_sleep_apps", "电池 → 休眠应用 → 从未", "OneUI 默认 3 天未用即深度休眠", true));
                items.add(new CheckItem("oneui_battery_unrestricted", "电池不受限制", "", true));
                break;
            case MEIZU:
                items.add(new CheckItem("flyme_autostart", "自启动", "", true));
                items.add(new CheckItem("flyme_bg_manage", "后台管理 → 允许后台运行", "Flyme 智能后台管控", true));
                break;
            case LENOVO:
                items.add(new CheckItem("zui_autostart", "自启动管理（ZUI）", "", true));
                items.add(new CheckItem("zui_bg_clean", "后台清理白名单", "", false));
                break;
            case ZTE:
            case NUBIA:
                items.add(new CheckItem("myos_autostart", "自启动", "MyOS 后台管控", true));
                items.add(new CheckItem("myos_battery", "电池优化白名单", "", false));
                break;
            case WEAR_LITE:
                items.add(new CheckItem("wear_battery", "电池优化白名单", "Wear 轻设备续航敏感：扫描策略由 PowerGovernor 单独降档（入门档 × 恒 LOW 态）", true));
                break;
            case OTHER:
            default:
                items.add(new CheckItem("generic_battery", "电池优化 → 不优化", "AOSP 标准通道（其余品牌兜底）", true));
                break;
        }
        return items;
    }

    // ───────────────────────── 设置页直达 ─────────────────────────

    /**
     * 体检项 → 设置页 Intent（厂商专属 ComponentName 直达；失败回退应用详情页）。
     * 所有 Intent 由调用方 try-catch startActivity（目标页不存在时回退，绝不 crash）。
     */
    public static Intent settingsIntent(Context ctx, Brand brand, String itemKey) {
        String pkg = ctx.getPackageName();
        Intent direct = directIntent(brand, itemKey, pkg);
        if (direct != null) {
            return direct;
        }
        // 兜底：应用详情页（含电池/通知/权限入口）
        Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", pkg, null));
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return fallback;
    }

    /** 厂商专属直达（组件名随 ROM 版本变化，startActivity 失败即回退——文档已注明） */
    private static Intent directIntent(Brand brand, String key, String pkg) {
        ComponentName cn = null;
        switch (brand) {
            case XIAOMI:
            case REDMI:
                if ("miui_autostart".equals(key)) {
                    cn = new ComponentName("com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity");
                } else if ("miui_battery".equals(key)) {
                    cn = new ComponentName("com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity");
                }
                break;
            case HUAWEI:
                if ("hw_startup".equals(key)) {
                    cn = new ComponentName("com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
                }
                break;
            case HONOR:
                if ("honor_startup".equals(key)) {
                    cn = new ComponentName("com.hihonor.systemmanager",
                            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
                }
                break;
            case OPPO:
            case REALME:
            case ONEPLUS:
                if ("coloros_autostart".equals(key)) {
                    cn = new ComponentName("com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity");
                } else if ("coloros_bg_popup".equals(key)) {
                    cn = new ComponentName("com.coloros.safecenter",
                            "com.coloros.safecenter.startupapp.StartupAppListActivity");
                }
                break;
            case VIVO:
                if ("funtouch_autostart".equals(key) || "funtouch_bg_popup".equals(key)) {
                    cn = new ComponentName("com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartActivityManager");
                }
                break;
            case SAMSUNG:
                if ("oneui_battery_unrestricted".equals(key)) {
                    cn = new ComponentName("com.samsung.android.lool",
                            "com.samsung.android.sm.battery.ui.BatteryActivity");
                }
                break;
            case MEIZU:
                if ("flyme_autostart".equals(key)) {
                    cn = new ComponentName("com.meizu.safe",
                            "com.meizu.safe.security.SHOW_APPSEC");
                }
                break;
            default:
                break;
        }
        if (cn == null) {
            return null;
        }
        Intent it = new Intent(Intent.ACTION_MAIN);
        it.setComponent(cn);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (cn.getPackageName().contains("powerkeeper")) {
            it.putExtra("package_name", pkg);
            it.putExtra("powerkeeper_hint", "需要允许 " + pkg + " 在后台运行");
        }
        return it;
    }

    // ───────────────────────── 服务吊销自愈（两态通知） ─────────────────────────

    /**
     * 查询本应用无障碍服务登记状态。
     *
     * <p>登记仍在（ALIVE）：进程被杀但系统会在需要时重绑——点通知打开应用主页
     * 即触发拉活（15s 自适应重试）。登记已失（REVOKED）：用户或系统关闭了服务——
     * 直达无障碍开关页，文案明示「重新开启需等待 10 秒并手动确认，这是系统的
     * 防诈设计，属正常流程」（绝不自动点系统确认按钮）。
     */
    public static RegistrationState queryRegistration(Context ctx) {
        AccessibilityManager am = (AccessibilityManager)
                ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            return RegistrationState.REVOKED;
        }
        String flat = ctx.getPackageName() + "/";
        List<android.accessibilityservice.AccessibilityServiceInfo> enabled =
                am.getEnabledAccessibilityServiceList(
                        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (enabled == null) {
            return RegistrationState.REVOKED;
        }
        for (android.accessibilityservice.AccessibilityServiceInfo info : enabled) {
            if (info != null && info.getResolveInfo() != null
                    && info.getResolveInfo().serviceInfo != null
                    && info.getResolveInfo().serviceInfo.name != null
                    && info.getResolveInfo().serviceInfo.name.contains(
                    ctx.getPackageName() + ".engine.SkipAdService")) {
                return RegistrationState.ALIVE;
            }
        }
        if (flat.isEmpty()) {
            return RegistrationState.REVOKED; // 防御（不可达分支，保持判定完备）
        }
        return RegistrationState.REVOKED;
    }

    /**
     * 服务断连入口（Service.onUnbind 调用）：15s 后探测登记状态并发两态通知。
     * 断连打点（v5.2.0 登记移除遥测）：下次日志可直接看到「什么时候被关的」。
     */
    public void handleUnbind(final Context ctx) {
        StructuredLogger.get().w("keepalive", "service_unbind", "pkg", ctx.getPackageName());
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                RegistrationState state = queryRegistration(ctx);
                StructuredLogger.get().i("keepalive", "registration_probe",
                        "state", state.name(), "pkg", ctx.getPackageName());
                notifyTwoState(ctx, state);
            }
        }, 15_000L);
    }

    /** 两态通知（ALIVE：自动拉活引导；REVOKED：直达开关页 + 10 秒确认说明） */
    public void notifyTwoState(Context ctx, RegistrationState state) {
        NotificationManager nm = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "服务保活体检", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("服务断连两态通知：自动拉活引导 / 吊销后直达开关页");
            nm.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx, CHANNEL_ID)
                : new Notification.Builder(ctx);
        boolean alive = state == RegistrationState.ALIVE;
        builder.setAutoCancel(true)
                .setSmallIcon(android.R.drawable.ic_menu_view) // TODO: 替换为品牌 icon
                .setContentTitle(alive ? "开屏卫士：正在自动恢复" : "开屏卫士：无障碍服务已关闭")
                .setContentText(alive
                        ? "服务登记仍在，点击通知打开应用即可完成拉活"
                        : "点击直达无障碍设置开启「开屏卫士」。重新开启需等待 10 秒并手动确认——这是系统的防诈设计，属正常流程，请放心操作");
        PendingIntent pi = alive
                ? PendingIntent.getActivity(ctx, 0,
                ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getActivity(ctx, 0,
                accessibilitySettingsIntent(ctx),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            builder.setContentIntent(pi);
        }
        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /** 无障碍设置页（开关所在；含 10 秒确认流程的入口） */
    public static Intent accessibilitySettingsIntent(Context ctx) {
        Intent it = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return it;
    }

    /** 体检报告（结构化日志 + 设置页展示） */
    public String healthReport(Context ctx) {
        Brand brand = detect();
        List<CheckItem> items = checklist(brand);
        StringBuilder sb = new StringBuilder();
        sb.append("品牌：").append(brand.label)
                .append("（").append(Build.MANUFACTURER).append("）\n");
        for (CheckItem item : items) {
            sb.append(item.critical ? "[必设] " : "[建议] ")
                    .append(item.title).append('\n');
        }
        StructuredLogger.get().i("keepalive", "health_report",
                "brand", brand.name(), "items", items.size());
        return sb.toString();
    }

    /** 全品牌矩阵键（文档与 CI 校验用） */
    public static List<String> brandKeys() {
        return Arrays.asList("xiaomi", "redmi", "huawei", "honor", "oppo", "vivo",
                "samsung", "meizu", "oneplus", "realme", "lenovo", "zte", "nubia", "wear");
    }
}
