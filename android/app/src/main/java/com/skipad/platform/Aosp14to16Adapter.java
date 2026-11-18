package com.skipad.platform;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.lang.reflect.Method;

/**
 * Android 14 ~ 16 高版本适配器（v5.4.0）。
 *
 * <p>适配要点（与 docs/OPTIMIZATION-v5.4.0.md §2.4 一致）：
 * <ul>
 *   <li><b>Android 14（API 34）</b>：accessibilityTool 标记与 isAccessibilityTool 声明。
 *       未声明 isAccessibilityTool 的服务在系统无障碍页被标注「非无障碍工具，
 *       可能收集数据」——本应用是代点工具，必须如实声明为无障碍工具并配合
 *       isAccessibilityTool=true（声明内容真实：只用窗口内容做广告判定，不做
 *       屏幕采集上传）。</li>
 *   <li><b>Android 15（API 35）</b>：前台服务类型 enforcement。dataSync 类 FGS
 *       被严格限流（6 小时上限），需要长时间同步的场景必须申报 specialUse 并在
 *       manifest property 中写明用途说明；无障碍服务本体不受影响（系统绑定
 *       生命周期），受影响的是夜间学习数据同步服务。</li>
 *   <li><b>Android 16（API 36）</b>：预测性返回（Predictive Back）。目标 36 的
 *       应用 onBackPressed 回调不再保证触发；引擎的 GLOBAL_ACTION_BACK 兜底
 *       与应用自身返回手势在时序上可能竞争——适配器保证 back 派发后复验窗口，
 *       不与预测性返回动画冲突。</li>
 *   <li><b>厂商 ROM 权限收紧</b>：MIUI 通知使用权、ColorOS 后台弹出界面权限
 *       检测（缺权时保活两态通知无法弹出，需提前引导）。</li>
 *   <li><b>10 秒安全警告规避红线</b>：系统无障碍开启确认框（Android 13+ 防诈
 *       设计）绝不 auto-click——本适配器所有路径都不触碰系统确认按钮，只引导
 *       用户手动确认。</li>
 * </ul>
 */
public final class Aosp14to16Adapter {

    private Aosp14to16Adapter() {
    }

    // ───────────────────────── Android 14：无障碍工具标记 ─────────────────────────

    /**
     * Android 14 accessibilityTool 标记。
     *
     * <p>规范路径是 XML（res/accessibility_service_config.xml 内
     * {@code android:accessibilityTool="true"}）；本方法提供运行时兜底
     * （ServiceInfo.setAccessibilityTool，API 34+），反射调用以兼容低编译 SDK。
     *
     * @return true = 标记成功（或 API < 34 无需标记）
     */
    public static boolean applyAccessibilityToolMark(AccessibilityServiceInfo info) {
        if (info == null || Build.VERSION.SDK_INT < 34) {
            return Build.VERSION.SDK_INT < 34;
        }
        try {
            Method setter = AccessibilityServiceInfo.class.getMethod("setAccessibilityTool", boolean.class);
            setter.invoke(info, Boolean.TRUE);
            return true;
        } catch (Throwable t) {
            // 编译 SDK < 34 或厂商裁剪：依赖 XML 声明（见 SkipAdService Javadoc 配置样例）
            return false;
        }
    }

    /** isAccessibilityTool 是否已声明（XML 或运行时任一途径） */
    public static boolean isAccessibilityToolDeclared(AccessibilityServiceInfo info) {
        if (info == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Method getter = AccessibilityServiceInfo.class.getMethod("isAccessibilityTool");
                Object v = getter.invoke(info);
                return Boolean.TRUE.equals(v);
            } catch (Throwable ignored) {
                // 反射失败走 XML 标记读取
            }
        }
        // XML 属性兜底读取（accessibilityTool 属性在 ServiceInfo 无直接 getter，
        // 编译期由 XML 声明保证——此处仅运行时探测返回保守值）
        return false;
    }

    // ───────────────────────── Android 15：前台服务类型 ─────────────────────────

    /**
     * Android 15 前台服务类型 enforcement（dataSync → specialUse 申报）。
     *
     * <p>规则：targetSdk 35 起，manifest 中未声明类型的 FGS 直接抛
     * InvalidForegroundServiceTypeException；dataSync 类型累计 6 小时被系统强制
     * 停止。夜间学习数据同步服务改用 specialUse 类型：
     * <pre>
     * &lt;service android:name=".sync.LearningSyncService"
     *          android:foregroundServiceType="specialUse"&gt;
     *   &lt;property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
     *             android:value="用于用户明示同意的夜间学习规则同步（低频、可停止）"/&gt;
     * &lt;/service&gt;
     * </pre>
     *
     * @return true = 已按 API 35 规范启动（低版本走普通 startForeground）
     */
    public static boolean startSpecialUseForeground(Service service, int id,
                                                    android.app.Notification notification) {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                // FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 1073741824
                service.startForeground(id, notification, 1073741824);
                return true;
            } catch (Throwable t) {
                // 未申报 specialUse 类型（manifest 缺失）→ 降级普通启动并记录
                service.startForeground(id, notification);
                return false;
            }
        }
        service.startForeground(id, notification);
        return true;
    }

    // ───────────────────────── Android 16：预测性返回 ─────────────────────────

    /**
     * Android 16 预测性返回下的 GLOBAL_ACTION_BACK 安全时序。
     *
     * <p>适配点：
     * <ul>
     *   <li>back 派发前快照当前窗口键（pkg|class）；</li>
     *   <li>派发 GLOBAL_ACTION_BACK 后延迟 reverifyDelayMs 复验窗口是否切换——
     *       预测性返回动画期间窗口可能未立即出栈，过早复验会误判「没关」而重复
     *       派发（与用户主动返回叠加 = 双返回退出应用事故）；</li>
     *   <li>复验仍在原窗 → 只允许一次补发（每窗最多 2 次，MatchingEngine 约束）。</li>
     * </ul>
     *
     * @param reverify 复验回调（窗口未切换时触发，参数 = 是否补发一次）
     */
    public static boolean performBackSafely(final AccessibilityService svc,
                                            final String windowKeyBefore,
                                            long reverifyDelayMs,
                                            final BackReverify reverify) {
        if (svc == null) {
            return false;
        }
        boolean dispatched = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        if (!dispatched) {
            return false;
        }
        // 预测性返回时序适配：动画期间不判定，延迟复验
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                CharSequence pkg = null;
                try {
                    // 复验：活动窗口包名是否已离开广告窗（getRootInActiveWindow 可能为 null）
                    android.view.accessibility.AccessibilityNodeInfo root = svc.getRootInActiveWindow();
                    pkg = root == null ? null : root.getPackageName();
                } catch (Throwable ignored) {
                    pkg = null;
                }
                boolean stillSame = pkg == null || windowKeyBefore == null
                        || windowKeyBefore.startsWith(String.valueOf(pkg) + "|");
                if (reverify != null) {
                    reverify.onReverify(stillSame);
                }
            }
        }, Math.max(120, reverifyDelayMs));
        return true;
    }

    /** 返回键复验回调（stillSame = 窗口未切换，允许补发一次） */
    public interface BackReverify {
        void onReverify(boolean stillSame);
    }

    // ───────────────────────── 厂商 ROM 权限检测 ─────────────────────────

    /**
     * MIUI 通知使用权检测（MIUI 在标准通知权限外有独立的「通知使用权」
     * 授权体系，缺少时两态通知无法弹出）。
     *
     * @return true = 已授权（非 MIUI 设备恒为 true）
     */
    public static boolean hasMiuiNotificationAccess(Context ctx) {
        String manufacturer = String.valueOf(Build.MANUFACTURER).toLowerCase();
        if (!manufacturer.contains("xiaomi") && !manufacturer.contains("redmi")) {
            return true; // 非 MIUI 设备无此权限体系
        }
        String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                "enabled_notification_listeners");
        return enabled != null && enabled.contains(ctx.getPackageName());
    }

    /**
     * ColorOS 后台弹出界面权限检测（OPPO/一加/真我系）。
     *
     * <p>该权限为 AppOps 项（android:background_start_activity），非标准 API。
     * 用 AppOpsManager 反射检测，失败时保守返回 true（不阻塞主流程，
     * 体检页通过 KeepAliveDoctor 引导用户自查）。
     */
    public static boolean hasColorOsBackgroundPopup(Context ctx) {
        String manufacturer = String.valueOf(Build.MANUFACTURER).toLowerCase();
        boolean colorOsFamily = manufacturer.contains("oppo")
                || manufacturer.contains("oneplus") || manufacturer.contains("realme");
        if (!colorOsFamily) {
            return true;
        }
        try {
            Class<?> appOpsClass = Class.forName("android.app.AppOpsManager");
            Context safeCtx = ctx.getApplicationContext();
            Object appOps = safeCtx.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return true; // 保守放行（体检页兜底）
            }
            java.lang.reflect.Field opStrField = appOpsClass.getField("OPSTR_BACKGROUND_START_ACTIVITY");
            String opStr = (String) opStrField.get(null);
            Method checkOp = appOpsClass.getMethod("checkOpNoThrow",
                    String.class, int.class, String.class);
            int uid = safeCtx.getApplicationInfo().uid;
            Object result = checkOp.invoke(appOps, opStr, uid, safeCtx.getPackageName());
            // MODE_ALLOWED = 0
            return result instanceof Integer && ((Integer) result) == 0;
        } catch (Throwable t) {
            return true; // 反射失败（ROM 裁剪/权限隐藏）：保守放行，体检页引导自查
        }
    }

    /** 通知渠道是否启用（两态通知可见性预检） */
    public static boolean notificationChannelEnabled(Context ctx, String channelId) {
        if (Build.VERSION.SDK_INT < 26) {
            return true;
        }
        android.app.NotificationManager nm = (android.app.NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return false;
        }
        android.app.NotificationChannel channel = nm.getNotificationChannel(channelId);
        return channel != null && channel.getImportance()
                != android.app.NotificationManager.IMPORTANCE_NONE;
    }

    /**
     * 10 秒安全警告规避红线（统一出口）：
     * 本应用任何代码路径绝不 auto-click 系统确认按钮——本方法返回 true 的窗口，
     * 引擎全体静默（零扫描零点击），仅允许通知/引导页指引用户。
     */
    public static boolean isSystemConfirmWindow(Context ctx, String pkg, String windowClass) {
        // 与 SensitiveSceneGuard.isSystemConfirmDialog 同口径（平台层冗余防线：
        // 引擎层漏判时平台层兜底）
        if (pkg == null || windowClass == null) {
            return false;
        }
        boolean settingsOrSystemUi = "com.android.settings".equals(pkg)
                || "com.android.systemui".equals(pkg)
                || pkg.contains("permissioncontroller")
                || pkg.contains("miui.securitycenter");
        boolean dialogShape = windowClass.contains("Dialog")
                || windowClass.contains("dialog") || windowClass.contains("Alert");
        return settingsOrSystemUi && dialogShape;
    }

    /** 无障碍服务是否被系统标记「非无障碍工具」（A14+ 透明化提示，日志用） */
    public static boolean serviceFlaggedAsNonTool(Context ctx) {
        if (Build.VERSION.SDK_INT < 34) {
            return false;
        }
        AccessibilityManager am = (AccessibilityManager)
                ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            return false;
        }
        // TODO: 安装的服务列表（getInstalledAccessibilityServiceList）逐项检查
        // isAccessibilityTool——仅用于「关于」页透明化展示，不影响功能
        return false;
    }

    /** targetSdk 便捷读取（适配分叉用） */
    public static int targetSdk(Context ctx) {
        try {
            return ctx.getApplicationInfo().targetSdkVersion;
        } catch (Throwable t) {
            return Build.VERSION.SDK_INT;
        }
    }

    /** 权限快捷检查（不抛 SecurityException 的 checkPermission） */
    public static boolean hasPermission(Context ctx, String permission) {
        return ctx.checkPermission(permission, android.os.Process.myPid(),
                android.os.Process.myUid()) == PackageManager.PERMISSION_GRANTED;
    }
}
