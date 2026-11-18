package com.skipad.guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 敏感场景守卫（v5.4.0）：12 类敏感场景零扫描。
 *
 * <p>命中任一场景的窗口：引擎不收集节点、不跑探针、不进 DFS、不学习——
 * 「零扫描」是隐私红线，不是性能优化（这些窗口里可能正显示密码/验证码/身份证号，
 * 任何节点读取都不可接受）。
 *
 * <p>12 类场景与信号（与 docs/OPTIMIZATION-v5.4.0.md §3.1 信号表一致）：
 * <ol>
 *   <li>PAYMENT 支付：支付类文本信号（收银台/支付密码/银行卡号）+ 守护应用包名</li>
 *   <li>PASSWORD 密码：hasPasswordField = true（无障碍树存在密码框）</li>
 *   <li>CAPTCHA 验证码：滑块/图形验证/请完成验证</li>
 *   <li>SMS 短信：系统短信应用包名</li>
 *   <li>SYSTEM_SETTINGS 系统设置：com.android.settings 及厂商安全中心</li>
 *   <li>IME 输入法：输入法窗口（windowClass 含 InputMethod）</li>
 *   <li>KEYGUARD 锁屏：SystemUI keyguard 窗</li>
 *   <li>IN_CALL 通话：拨号/来电窗</li>
 *   <li>TRANSFER 转账：转账/汇款/收款人</li>
 *   <li>REALNAME 实名：实名认证/身份证/人脸识别</li>
 *   <li>OTP 动态码：验证码/校验码/动态码（含短信 OTP 输入场景）</li>
 *   <li>PERMISSION_DIALOG 权限对话框：系统授权 Dialog——
 *       含无障碍开启 10 秒确认框，<b>绝不自动点</b>（只引导用户）</li>
 * </ol>
 *
 * <p>与 Web 端 src/lib/sensitive-scenes.ts 同构（TS 为规范源）：
 * detect(pkg, windowClass, hasPasswordField[, windowTexts]) ↔ detectSensitiveScene(input)、
 * Scene ↔ SensitiveScene、isGuardApp ↔ 名单前缀表（GUARD_PKG_PREFIXES ↔ GUARD 常量）。
 */
public final class SensitiveSceneGuard {

    private SensitiveSceneGuard() {
    }

    /** 敏感场景枚举（12 类） */
    public enum Scene {
        PAYMENT, PASSWORD, CAPTCHA, SMS, SYSTEM_SETTINGS, IME,
        KEYGUARD, IN_CALL, TRANSFER, REALNAME, OTP, PERMISSION_DIALOG
    }

    /** 判定结论 */
    public static final class Verdict {
        /** 命中场景（未命中为 null） */
        public final Scene scene;
        /** 零扫描（12 类场景恒为 true；未命中为 false） */
        public final boolean zeroScan;
        /** 系统确认框（10 秒警告类：绝不自动点，只引导） */
        public final boolean systemConfirm;
        /** 判定依据（审计与结构化日志用） */
        public final String reason;

        private Verdict(Scene scene, boolean zeroScan, boolean systemConfirm, String reason) {
            this.scene = scene;
            this.zeroScan = zeroScan;
            this.systemConfirm = systemConfirm;
            this.reason = reason;
        }

        static Verdict none() {
            return new Verdict(null, false, false, "not-sensitive");
        }
    }

    // ───────────────────────── 信号表 ─────────────────────────

    /** 系统设置（含厂商安全中心——保活体检跳转目标，同样零扫描） */
    private static final String[] SETTINGS_PKGS = {
            "com.android.settings",
            "com.miui.securitycenter",
            "com.huawei.systemmanager",
            "com.hihonor.systemmanager",
            "com.coloros.safecenter",
            "com.oppo.safe",
            "com.vivo.permissionmanager",
            "com.iqoo.secure",
            "com.samsung.android.lool",
            "com.lenovo.safecenter",
    };

    /** 短信应用 */
    private static final String[] SMS_PKGS = {
            "com.android.mms", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.miui.sms"
    };

    /** 拨号/通话 */
    private static final String[] DIALER_PKGS = {
            "com.android.dialer", "com.android.incallui",
            "com.samsung.android.incallui", "com.miui.phone", "com.google.android.dialer"
    };

    /** 权限对话框（系统授权 + 无障碍确认；含厂商变体） */
    private static final String[] PERMISSION_PKGS = {
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.lbe.smiui",                     // MIUI 权限
            "com.samsung.android.permission"     // 三星权限
    };
    private static final String[] PERMISSION_CLASSES = {
            "grantables", "GrantPermissionsActivity", "permissioncontroller"
    };

    /** 支付/转账/实名/OTP/验证码 文本信号（短窗口文本扫描在零扫描判定之后才允许） */
    private static final String[] OTP_TEXTS = {"验证码", "校验码", "动态码", "OTP"};
    private static final String[] CAPTCHA_TEXTS = {"图形验证", "滑块验证", "请完成验证", "安全验证"};
    private static final String[] TRANSFER_TEXTS = {"转账", "汇款", "收款人", "转账金额"};
    private static final String[] REALNAME_TEXTS = {"实名认证", "身份证", "人脸识别", "真实姓名"};
    private static final String[] PAYMENT_TEXTS = {
            "收银台", "支付密码", "银行卡号", "有效期", "确认支付", "立即支付", "付款码"
    };

    /** 守护应用（银行/政务/支付）包名前缀——整窗零扫描（广告照拦由守护名单温和策略负责） */
    private static final String[] GUARD_PKG_PREFIXES = {
            "com.eg.android.AlipayGphone",   // 支付宝
            "com.unionpay",                   // 银联
            "com.chinamworld",                // 各大银行手机银行（中金系列）
            "com.android.bank",               // 银行类命名惯例
            "gov.",                           // 政务应用命名惯例
            "com.cgb", "com.cmbchina", "com.icbc", "com.chinamworld.bocmbci"
    };

    // ───────────────────────── 判定入口 ─────────────────────────

    /**
     * 三参判定（事件层快速路径：TYPE_WINDOW_STATE_CHANGED 装配完成即调用，
     * 不读取任何节点内容——只有包名与窗口类）。
     *
     * @param pkg             窗口包名
     * @param windowClass     窗口 className
     * @param hasPasswordField 无障碍树存在密码框（由 Service 侧轻量标记，未探测传 false）
     */
    public static Verdict detect(String pkg, String windowClass, boolean hasPasswordField) {
        return detect(pkg, windowClass, hasPasswordField, null);
    }

    /**
     * 全参判定（含窗口文本信号；文本清单由探针之外的首层浅扫收集，仅用于
     * 支付/转账/实名/OTP 场景确认——这些场景一旦确认即整窗零扫描）。
     */
    public static Verdict detect(String pkg, String windowClass, boolean hasPasswordField,
                                 List<String> windowTexts) {
        if (pkg == null) {
            pkg = "";
        }
        if (windowClass == null) {
            windowClass = "";
        }
        String pkgLc = pkg.toLowerCase();
        String clsLc = windowClass.toLowerCase();

        // 1) 权限对话框（含无障碍 10 秒确认框）——最高优先级：绝不自动点
        if (startsWithAny(pkg, PERMISSION_PKGS) || containsAny(clsLc, PERMISSION_CLASSES)) {
            boolean confirm = isSystemConfirmDialog(pkg, windowClass);
            return new Verdict(Scene.PERMISSION_DIALOG, true, confirm,
                    confirm ? "system-confirm-dialog" : "permission-dialog");
        }
        // 2) 锁屏
        if ("com.android.systemui".equals(pkg) && clsLc.contains("keyguard")) {
            return new Verdict(Scene.KEYGUARD, true, false, "keyguard");
        }
        // 3) 输入法
        if (clsLc.contains("inputmethod")) {
            return new Verdict(Scene.IME, true, false, "ime-window");
        }
        // 4) 密码框在场
        if (hasPasswordField) {
            return new Verdict(Scene.PASSWORD, true, false, "password-field");
        }
        // 5) 通话
        if (startsWithAny(pkg, DIALER_PKGS) || clsLc.contains("incallui")) {
            return new Verdict(Scene.IN_CALL, true, false, "in-call");
        }
        // 6) 短信
        if (startsWithAny(pkg, SMS_PKGS)) {
            return new Verdict(Scene.SMS, true, false, "sms-app");
        }
        // 7) 系统设置/安全中心
        if (startsWithAny(pkg, SETTINGS_PKGS)) {
            return new Verdict(Scene.SYSTEM_SETTINGS, true, false, "system-settings");
        }
        // 8) 守护应用（银行/政务/支付整窗零扫描）
        if (startsWithAny(pkgLc, GUARD_PKG_PREFIXES)) {
            return new Verdict(Scene.PAYMENT, true, false, "guard-app");
        }
        // 9) 文本信号（支付/转账/实名/OTP/验证码）——需要 windowTexts
        if (windowTexts != null && !windowTexts.isEmpty()) {
            Verdict textVerdict = detectByTexts(windowTexts);
            if (textVerdict != null) {
                return textVerdict;
            }
        }
        return Verdict.none();
    }

    private static Verdict detectByTexts(List<String> texts) {
        if (containsText(texts, OTP_TEXTS)) {
            return new Verdict(Scene.OTP, true, false, "otp-text");
        }
        if (containsText(texts, CAPTCHA_TEXTS)) {
            return new Verdict(Scene.CAPTCHA, true, false, "captcha-text");
        }
        if (containsText(texts, TRANSFER_TEXTS)) {
            return new Verdict(Scene.TRANSFER, true, false, "transfer-text");
        }
        if (containsText(texts, REALNAME_TEXTS)) {
            return new Verdict(Scene.REALNAME, true, false, "realname-text");
        }
        if (containsText(texts, PAYMENT_TEXTS)) {
            return new Verdict(Scene.PAYMENT, true, false, "payment-text");
        }
        return null;
    }

    // ───────────────────────── 系统确认框 ─────────────────────────

    /**
     * 无障碍开启 10 秒确认框识别（Android 13+ 系统「要开启 XX 的无障碍服务吗」
     * 防诈确认 Dialog）。合规红线：本应用绝不 auto-click 该确认按钮——
     * 只通过通知/引导页指引用户手动确认（见 KeepAliveDoctor 两态通知）。
     */
    public static boolean isSystemConfirmDialog(String pkg, String windowClass) {
        if (pkg == null || windowClass == null) {
            return false;
        }
        boolean settingsOrSystemUi = "com.android.settings".equals(pkg)
                || "com.android.systemui".equals(pkg)
                || pkg.contains("permissioncontroller");
        boolean dialogShape = windowClass.contains("Dialog")
                || windowClass.contains("dialog")
                || windowClass.contains("Alert");
        return settingsOrSystemUi && dialogShape;
    }

    /** 守护应用判定（银行/政务/支付：广告照拦但服务零干预，仅温和代点） */
    public static boolean isGuardApp(String pkg) {
        return startsWithAny(pkg == null ? "" : pkg.toLowerCase(), GUARD_PKG_PREFIXES);
    }

    /** 敏感场景全清单（设置页/文档展示用） */
    public static List<Scene> allScenes() {
        return Arrays.asList(Scene.values());
    }

    /** 场景中文名（日志/看板） */
    public static String sceneLabel(Scene scene) {
        if (scene == null) {
            return "非敏感";
        }
        switch (scene) {
            case PAYMENT:
                return "支付";
            case PASSWORD:
                return "密码";
            case CAPTCHA:
                return "验证码";
            case SMS:
                return "短信";
            case SYSTEM_SETTINGS:
                return "系统设置";
            case IME:
                return "输入法";
            case KEYGUARD:
                return "锁屏";
            case IN_CALL:
                return "通话";
            case TRANSFER:
                return "转账";
            case REALNAME:
                return "实名";
            case OTP:
                return "动态码";
            case PERMISSION_DIALOG:
                return "权限对话框";
            default:
                return scene.name();
        }
    }

    // ───────────────────────── 工具 ─────────────────────────

    private static boolean startsWithAny(String value, String[] prefixes) {
        if (value == null) {
            return false;
        }
        for (String p : prefixes) {
            if (p != null && value.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String[] tokens) {
        if (value == null) {
            return false;
        }
        for (String t : tokens) {
            if (t != null && value.contains(t.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsText(List<String> texts, String[] keywords) {
        List<String> pool = new ArrayList<>(texts);
        for (String kw : keywords) {
            for (String t : pool) {
                if (t != null && t.contains(kw)) {
                    return true;
                }
            }
        }
        return false;
    }
}
