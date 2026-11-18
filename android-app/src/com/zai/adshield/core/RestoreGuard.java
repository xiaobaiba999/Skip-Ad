package com.zai.adshield.core;

/**
 * ─────────────────────────────────────────────────────────────
 *  RestoreGuard · v5.2.0 恢复防护与保活决策核心（纯 Java，零 Android 依赖）
 * ─────────────────────────────────────────────────────────────
 *  issue#56 实锤（Redmi 25060RK16C · Android 16 · HyperOS）：
 *  「每次退出后台重进 / 覆盖安装后点恢复防护，都要进系统强行看
 *   10 秒安全警告；Xiaomi 15 用户则不用」——根因三层：
 *
 *  ① ROM 差异（设备问题的一部分，但可防）：HyperOS（低端 Redmi 更激进）
 *     在清理后台/滑掉最近任务/覆盖安装后，不仅杀进程，还会把服务从
 *     Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 登记里【移除】——
 *     登记一旦被吊销，任何应用都无法编程恢复（BIND_ACCESSIBILITY_SERVICE
 *     签名级保护），只能经系统设置重开，撞上厂商防诈的 10 秒倒计时确认。
 *     Xiaomi 15（HyperOS 2，旗舰策略宽松）登记保留，进程重启即自动重绑。
 *     吊销三大诱因：自启动未授权 / 省电策略受限 / 清理后台——全部有
 *     厂商设置入口，可引导用户一次配置根治。
 *  ② 旧版自身三处放大痛点：
 *     · jump_a11y 通知点按后 500ms 无条件直跳系统设置（不先自动拉活，
 *       登记仍在的用户被白送去看了 10 秒警告/开关开着的设置页）；
 *     · 拉活窗口仅 6s（低端机冷启动 + HyperOS 3 重绑慢 → 误判 need_toggle）；
 *     · MY_PACKAGE_REPLACED 直接假设「系统必然已关闭」发设置导向通知，
 *       从不先自愈（多数 ROM 登记保留，进程起来即重绑）。
 *  ③ 预防缺失：App 内只有电池白名单引导，没有厂商自启动/省电策略直达。
 *
 *  本类是全部决策逻辑的唯一数据源（Android 侧只做 IO 与跳转）：
 *  · 品牌族归一（近 5 年全品牌：Xiaomi/Redmi/POCO·HyperOS、Huawei·
 *    HarmonyOS、Honor·MagicOS、OPPO/realme/OnePlus·ColorOS、vivo/iQOO·
 *    OriginOS、Samsung·OneUI、Meizu·Flyme、ZTE/Lenovo/其他·通用）
 *  · 保活体检项注册表（id/标签/说明/深链候选/extra——候选全部是
 *    硬编码系统包，杜绝任意组件注入）
 *  · 恢复决策状态机：核心不变量——【登记仍在就绝不引导去系统设置】
 *  · 15s 自适应轮询计划（前 2s 每 300ms 快踢，后段每 500ms）
 *  · ENABLED_ACCESSIBILITY_SERVICES 精确解析（':' 分隔逐段 equals，
 *    杜绝子串误报）
 *  · 动态版本横幅（根治硬编码 v4.5.0 陈旧横幅 bug）
 */
public final class RestoreGuard {

    private RestoreGuard() {
    }

    /* ══════════════ 1. 品牌族归一（近 5 年全品牌 2020-2025）══════════════ */

    /** 品牌族常量（值即 Android 侧/页面侧的分发键） */
    public static final String FAMILY_HYPEROS = "hyperos";   // Xiaomi/Redmi/POCO · MIUI 12-14 / HyperOS 1-3
    public static final String FAMILY_HARMONY = "harmony";   // 华为 · EMUI 11+ / HarmonyOS 2-4.x
    public static final String FAMILY_MAGIC = "magic";       // 荣耀 · MagicUI/MagicOS 5-8
    public static final String FAMILY_COLOROS = "coloros";   // OPPO/realme/一加 · ColorOS 7-15
    public static final String FAMILY_ORIGINOS = "originos"; // vivo/iQOO · Funtouch 10+ / OriginOS 1-5
    public static final String FAMILY_ONEUI = "oneui";       // 三星 · OneUI 3-7
    public static final String FAMILY_FLYME = "flyme";       // 魅族 · Flyme 9-10
    public static final String FAMILY_GENERIC = "generic";   // ZTE/联想/其他 —— 无厂商专属入口

    /** 品牌族 → 用户可见名称 */
    public static String familyLabel(String family) {
        if (FAMILY_HYPEROS.equals(family)) return "MIUI/HyperOS（小米系）";
        if (FAMILY_HARMONY.equals(family)) return "HarmonyOS（华为）";
        if (FAMILY_MAGIC.equals(family)) return "MagicOS（荣耀）";
        if (FAMILY_COLOROS.equals(family)) return "ColorOS（OPPO/一加/realme）";
        if (FAMILY_ORIGINOS.equals(family)) return "OriginOS/Funtouch（vivo/iQOO）";
        if (FAMILY_ONEUI.equals(family)) return "OneUI（三星）";
        if (FAMILY_FLYME.equals(family)) return "Flyme（魅族）";
        return "通用 Android";
    }

    /**
     * 品牌族归一：manufacturer/brand/model 任一命中即归族
     * （Build.MANUFACTURER 优先——"Xiaomi Communications Co., Ltd" 等
     *  长串 contains 匹配；大小写不敏感；空值安全）。
     */
    public static String familyOf(String manufacturer, String brand, String model) {
        String m = lower(manufacturer) + " " + lower(brand) + " " + lower(model);
        // 荣耀先于华为判断（2021 后独立，manufacturer 是 HONOR）
        if (m.contains("honor") || m.contains("荣耀")) return FAMILY_MAGIC;
        if (m.contains("huawei") || m.contains("华为")) return FAMILY_HARMONY;
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
                || m.contains("小米") || m.contains("红米")) return FAMILY_HYPEROS;
        if (m.contains("oneplus") || m.contains("realme") || m.contains("oppo")
                || m.contains("一加") || m.contains("欧加")) return FAMILY_COLOROS;
        if (m.contains("iqoo") || m.contains("vivo") || m.contains("步步高")) return FAMILY_ORIGINOS;
        if (m.contains("samsung") || m.contains("三星")) return FAMILY_ONEUI;
        if (m.contains("meizu") || m.contains("魅族")) return FAMILY_FLYME;
        return FAMILY_GENERIC;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT);
    }

    /* ══════════════ 2. 保活体检项注册表（深链候选全部硬编码系统包）══════════════ */

    /** 一条保活体检项：id 稳定不变（Android/页面按 id 直达） */
    public static final class KeepAliveItem {
        public final String id;
        public final String label;
        public final String desc;
        /** 深链候选，每个元素 {包名, Activity}，按优先级排序逐个回退 */
        public final String[][] candidates;
        /** 额外参数类型："miui_pkg" = 需附 package_name extra；null = 无 */
        public final String extra;

        public KeepAliveItem(String id, String label, String desc, String[][] candidates, String extra) {
            this.id = id;
            this.label = label;
            this.desc = desc;
            this.candidates = candidates;
            this.extra = extra;
        }
    }

    /** 品牌族 → 体检项列表（generic 返回空数组——只显示三项自动检测） */
    public static KeepAliveItem[] itemsFor(String family) {
        if (FAMILY_HYPEROS.equals(family)) {
            return new KeepAliveItem[]{
                    new KeepAliveItem("hyperos_autostart", "自启动（防清理后台吊销）",
                            "安全中心 → 应用管理 → 权限 → 自启动：允许开屏卫士",
                            new String[][]{
                                    {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                            }, null),
                    new KeepAliveItem("hyperos_battery", "省电策略 → 无限制",
                            "设置 → 省电与电池 → 应用智能省电：开屏卫士选「无限制」",
                            new String[][]{
                                    {"com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"},
                            }, "miui_pkg"),
            };
        }
        if (FAMILY_HARMONY.equals(family)) {
            return new KeepAliveItem[]{
                    new KeepAliveItem("harmony_autostart", "应用启动管理 → 手动管理",
                            "设置 → 应用和服务 → 应用启动管理：开屏卫士改手动，三项全开",
                            new String[][]{
                                    {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                                    {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
                            }, null),
            };
        }
        if (FAMILY_MAGIC.equals(family)) {
            return new KeepAliveItem[]{
                    new KeepAliveItem("magic_autostart", "应用启动管理 → 手动管理",
                            "设置 → 应用启动管理：开屏卫士改手动管理，三项全开",
                            new String[][]{
                                    {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                                    {"com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"},
                            }, null),
            };
        }
        if (FAMILY_COLOROS.equals(family)) {
            return new KeepAliveItem[]{
                    new KeepAliveItem("coloros_autostart", "自启动 + 允许完全后台行为",
                            "设置 → 应用管理 → 开屏卫士：允许自启动、允许完全后台行为",
                            new String[][]{
                                    {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                                    {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                                    {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                            }, null),
            };
        }
        if (FAMILY_ORIGINOS.equals(family)) {
            return new KeepAliveItem[]{
                    new KeepAliveItem("originos_autostart", "自启动 + 后台高耗电允许",
                            "设置 → 电池 → 后台高耗电：开屏卫士允许；i管家 → 应用启动允许",
                            new String[][]{
                                    {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartHighPowerManager"},
                                    {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                                    {"com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity"},
                            }, null),
            };
        }
        if (FAMILY_ONEUI.equals(family)) {
            return new KeepAliveItem[]{
                    new KeepAliveItem("oneui_battery", "电池 → 不受限 + 从不休眠应用",
                            "设置 → 电池 → 后台使用限制：开屏卫士加入「从不休眠的应用」",
                            new String[][]{
                                    {"com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"},
                            }, null),
            };
        }
        if (FAMILY_FLYME.equals(family)) {
            return new KeepAliveItem[]{
                    new KeepAliveItem("flyme_autostart", "自启动 + 后台管理允许",
                            "设置 → 应用管理 → 开屏卫士：允许自启动、后台运行不限制",
                            new String[][]{
                                    {"com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"},
                            }, null),
            };
        }
        return new KeepAliveItem[0];
    }

    /* ══════════════ 3. 登记精确解析（杜绝子串误报）══════════════ */

    /** 登记状态 */
    public static final int REG_REGISTERED = 0; // 系统仍登记本服务（可自动拉活重绑）
    public static final int REG_REVOKED = 1;    // 登记被系统移除（只能经系统设置重开）

    /**
     * 解析 ENABLED_ACCESSIBILITY_SERVICES（':' 分隔的 ComponentName 列表），
     * 逐段 trim 后与 flattenToString / flattenToShortString 精确 equals。
     * —— 修复 contains 子串误报（如其它服务 ".AdSkipServiceV2" 会包含
     *    我们的短串 ".AdSkipService"，contains 误判成「仍登记」）。
     */
    public static int registrationState(String enabledCsv, String ourFlat, String ourShort) {
        if (enabledCsv == null || enabledCsv.length() == 0) return REG_REVOKED;
        java.util.StringTokenizer st = new java.util.StringTokenizer(enabledCsv, ":");
        while (st.hasMoreTokens()) {
            String tok = st.nextToken().trim();
            if (tok.length() == 0) continue;
            if (tok.equals(ourFlat) || tok.equals(ourShort)) return REG_REGISTERED;
        }
        return REG_REVOKED;
    }

    /** 登记遥测：登记变更分析（打点文案用） */
    public static final int CHANGE_UNRELATED = 0; // 变更与本服务无关（用户开/关了别的服务）
    public static final int CHANGE_REVOKED = 1;   // 本服务登记被系统移除（关键遥测）
    public static final int CHANGE_ADDED = 2;     // 本服务被重新登记

    public static int analyzeRegChange(boolean beforeRegistered, boolean afterRegistered) {
        if (beforeRegistered == afterRegistered) return CHANGE_UNRELATED;
        return afterRegistered ? CHANGE_ADDED : CHANGE_REVOKED;
    }

    /* ══════════════ 4. 恢复决策状态机（核心不变量）══════════════ */

    /** 通知策略（KeepAliveReceiver 侧） */
    public static final int NOTIFY_NONE = 0;          // 无需通知（活着/已自愈）
    public static final int NOTIFY_AUTO_RESTORE = 1;  // 登记仍在：打开卫士自动恢复（绝不提系统设置）
    public static final int NOTIFY_TO_SETTINGS = 2;   // 登记被吊销：直达服务开关（10 秒确认属正常）

    /**
     * 自检/覆盖安装/开机时的一次决策：
     * 活着 → 不打扰；登记被吊销 → 引导系统设置；登记仍在 → 先踢链自愈。
     */
    public static int decideOnCheck(boolean alive, boolean registered) {
        if (alive) return NOTIFY_NONE;
        return registered ? NOTIFY_AUTO_RESTORE : NOTIFY_TO_SETTINGS;
    }

    /**
     * 踢链自愈结束后的决策：仍没绑上但登记还在 → 只引导「打开卫士自动恢复」。
     * 【不变量：registered=true 时永不输出 NOTIFY_TO_SETTINGS】
     */
    public static int decideAfterKicks(boolean alive, boolean registeredAfter) {
        if (alive) return NOTIFY_NONE;
        return registeredAfter ? NOTIFY_AUTO_RESTORE : NOTIFY_TO_SETTINGS;
    }

    /** App 内拉活循环的单步决策 */
    public static final int INAPP_OK_RUNNING = 0;   // 本来就活着
    public static final int INAPP_OK_REVIVED = 1;   // 等到了重绑
    public static final int INAPP_CONTINUE = 2;     // 继续等（窗口未满且登记仍在）
    public static final int INAPP_FAIL_DISABLED = 3;// 中途发现登记被吊销 → 立即停止（去设置是唯一出路）
    public static final int INAPP_FAIL_SLOW = 4;    // 窗口用尽仍登记（绑定极慢 → 引导重试，不跳设置）

    /**
     * @param waitedMs  已等待毫秒
     * @param windowMs  总窗口（v5.2.0 = 15000，低端机冷启动覆盖）
     */
    public static int decideInAppRevive(boolean alive, boolean registered, long waitedMs, long windowMs) {
        if (alive) return waitedMs <= 0 ? INAPP_OK_RUNNING : INAPP_OK_REVIVED;
        if (!registered) return INAPP_FAIL_DISABLED;
        return waitedMs >= windowMs ? INAPP_FAIL_SLOW : INAPP_CONTINUE;
    }

    /* ══════════════ 5. 15s 自适应轮询计划 ══════════════ */

    /**
     * 拉活轮询时刻表（毫秒偏移，含 0）：
     * 前 2s 每 300ms（重绑多发生在这里——快踢抢时间），之后每 500ms。
     * v4.4.0 固定 6s × 500ms 在低端 HyperOS 上不够（issue#56）——
     * 窗口扩到 15s 且前段更密，总踢 33 次。
     */
    public static long[] pollPlan(long windowMs) {
        java.util.ArrayList<Long> at = new java.util.ArrayList<Long>();
        at.add(0L);
        long t = 0;
        while (true) {
            t += (t < 2000) ? 300 : 500;
            if (t > windowMs) break;
            at.add(t);
        }
        long[] out = new long[at.size()];
        for (int i = 0; i < at.size(); i++) out[i] = at.get(i);
        return out;
    }

    /* ══════════════ 6. 动态启动横幅（根治硬编码 v4.5.0 陈旧横幅）══════════════ */

    /**
     * 服务启动横幅：版本号永远取运行时实际值（issue#56 日志实锤
     * v5.1.1 用户横幅仍显示「v4.5.0 乱点根治…」——每次迭代忘了改）。
     */
    public static String startBanner(String version, String ruleStats,
                                     int purgedFp, int purgedPos, int purgedOem, int wlN) {
        StringBuilder b = new StringBuilder();
        b.append("服务已启动 · v").append(version == null || version.length() == 0 ? "?" : version)
                .append(" 秒跳引擎（规则 ").append(ruleStats == null ? "" : ruleStats).append("）");
        if (purgedFp > 0) b.append("｜已清理 ").append(purgedFp).append(" 条误学窗口指纹");
        if (purgedPos > 0) b.append("｜已清理 ").append(purgedPos).append(" 条桌面误学位置规则");
        if (purgedOem > 0) b.append("｜已清理 ").append(purgedOem).append(" 条系统工具/短信误学规则");
        if (wlN > 0) b.append("｜白名单 ").append(wlN).append(" 个应用零打扰");
        return b.toString();
    }

    /* ══════════════ 7. 通知文案（两态，预期管理）══════════════ */

    /** 待恢复通知标题 */
    public static String alertTitle(int mode) {
        return mode == NOTIFY_TO_SETTINGS ? "秒跳引擎待开启（开关被系统关闭）" : "秒跳引擎待恢复";
    }

    /** 待恢复通知正文（点按动作说明） */
    public static String alertText(int mode) {
        return mode == NOTIFY_TO_SETTINGS
                ? "系统在清理后台/覆盖安装后关闭了服务开关。点按直达开关重新开启（系统会弹 10 秒安全确认，属正常防诈设计）"
                : "服务被系统暂时解绑。点按打开卫士自动恢复（无需系统设置、无 10 秒等待）";
    }

    /** 待恢复通知大文本（说明 + 保活指引） */
    public static String alertBigText(int mode, String familyLabel) {
        if (mode == NOTIFY_TO_SETTINGS) {
            return "系统在覆盖安装、深度清理后会关闭无障碍服务，这是 Android 的安全设计。\n"
                    + "点按本通知 → 直达「开屏卫士」服务开关 → 打开即恢复（系统会弹 10 秒安全确认，属正常）。学习数据全部保留。\n"
                    + "防再次关闭：" + familyLabel + " 建议在 App 首页「保活体检」完成 自启动/省电策略 配置。";
        }
        return "服务被系统解绑但开关仍在，打开卫士即可自动恢复。\n"
                + "点按本通知 → 打开开屏卫士 → 自动深度拉活（最长 15 秒），全程不去系统设置。\n"
                + "若常被解绑：" + familyLabel + " 建议完成 App 首页「保活体检」。";
    }

    /* ══════════════ 8. v5.6.0 零打扰持久模式（免系统设置自愈）══════════════
     * 用户实锤（本轮三度反馈）：部分用户「退后台再进/装最新版回来开守护」
     * 每次都被要求进系统设置重开 + 强看 10 秒警告。登记被 ROM 吊销后，
     * 公开 API 无法恢复是事实——但 WRITE_SECURE_SETTINGS（adb 一次性授权）
     * 允许应用直接改 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES：
     * 把自己写回登记 = 系统自动重绑 = 零系统设置 · 零 10 秒警告。
     * 安全边界：
     *  · 权限是 signature|preinstalled|development 级——商店 App 拿不到，
     *    仅用户在 PC/无线 ADB 执行一次 pm grant 才生效（明示授权）；
     *  · 只把【自己的组件】并回登记串，绝不增删他人登记（幂等去重，
     *    保留原顺序，失败原样返回 null 交回保守通知流）；
     *  · 未授权设备零行为变化——通知流/体检卡照旧。纯函数，JVM 可测。 */

    /** 把本服务组件并回 ENABLED_ACCESSIBILITY_SERVICES 登记串（幂等 · 去重 · 保序）。
     *  @return 合并后的新登记串；组件已在串中（无需写）返回原串；入参非法返回 null */
    public static String mergeRegistrationCsv(String currentCsv, String ourFlat) {
        if (ourFlat == null || ourFlat.length() == 0) return null;
        if (currentCsv == null || currentCsv.trim().length() == 0) return ourFlat; // 空表 → 唯一登记
        java.util.StringTokenizer st = new java.util.StringTokenizer(currentCsv, ":");
        StringBuilder keep = new StringBuilder();
        boolean already = false;
        while (st.hasMoreTokens()) {
            String tok = st.nextToken().trim();
            if (tok.length() == 0) continue;            // 丢弃空段（顺手清理）
            if (tok.equals(ourFlat)) { already = true; } // 去重标记
            if (keep.length() > 0) keep.append(':');
            keep.append(tok);
        }
        if (already) return keep.length() == 0 ? ourFlat : keep.toString(); // 已在 → 原样（幂等）
        if (keep.length() > 0) keep.append(':');
        keep.append(ourFlat);
        return keep.toString();
    }

    /** 持久模式引导文案：一次 ADB 授权命令（页面展示/复制用，纯常量） */
    public static String secureGrantCmd(String pkg) {
        return "adb shell pm grant " + (pkg == null || pkg.length() == 0 ? "<pkg>" : pkg)
                + " android.permission.WRITE_SECURE_SETTINGS";
    }

    /** 持久模式说明（零打扰闭环：授权后吊销也能静默回登记） */
    public static String secureModeDesc() {
        return "授权后：升级/清理后台把无障碍登记吊销时，卫士将直接把自己写回系统登记并自动重绑——"
                + "不再进系统设置、不看 10 秒警告。权限仅经你本人 ADB 一次授权生效，商店安装的应用拿不到该权限。";
    }
}
