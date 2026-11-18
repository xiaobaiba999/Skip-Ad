package com.zai.adshield.test;

import com.zai.adshield.core.RestoreGuard;
import com.zai.adshield.core.RestoreGuard.KeepAliveItem;

/**
 * RestoreGuard 单元测试（v5.2.0 · issue#56）。
 * 覆盖：品牌族归一（近 5 年全品牌）、保活体检项注册表（含安全不变量：
 * 深链候选全部是硬编码系统包，零任意注入面）、登记精确解析（':' 逐段
 * equals 杜绝子串误报）、恢复决策状态机（核心不变量：登记仍在绝不
 * 引导去系统设置看 10 秒警告）、15s 自适应轮询计划、动态启动横幅
 * （根治硬编码 v4.5.0 陈旧横幅）、两态通知文案（预期管理）。
 */
public class RestoreGuardTest {

    /** OEM 系统包白名单——itemsFor 产出的所有候选必须落在其中（安全断言） */
    private static final String[] KNOWN_SYSTEM_PKGS = {
            "com.miui.securitycenter", "com.miui.powerkeeper",
            "com.huawei.systemmanager", "com.hihonor.systemmanager",
            "com.coloros.safecenter", "com.oppo.safe",
            "com.iqoo.secure", "com.vivo.permissionmanager",
            "com.samsung.android.lool", "com.meizu.safe",
    };

    private static boolean inSystemPkgs(String pkg) {
        for (String p : KNOWN_SYSTEM_PKGS) if (p.equals(pkg)) return true;
        return false;
    }

    public static void main(String[] args) {
        TestHost.suite("品牌族归一（近 5 年全品牌 2020-2025）");
        testFamily();

        TestHost.suite("保活体检项注册表（深链候选安全性）");
        testItems();

        TestHost.suite("登记精确解析（':' 逐段 equals）");
        testRegistration();

        TestHost.suite("登记变更遥测");
        testRegChange();

        TestHost.suite("恢复决策状态机（核心不变量）");
        testDecisions();

        TestHost.suite("App 内拉活单步决策");
        testInAppRevive();

        TestHost.suite("15s 自适应轮询计划");
        testPollPlan();

        TestHost.suite("动态启动横幅（v4.5.0 陈旧横幅根治）");
        testStartBanner();

        TestHost.suite("两态通知文案（预期管理）");
        testAlertTexts();

        TestHost.done();
    }

    private static void testFamily() {
        // issue#56 主角：Redmi 25060RK16C（HyperOS 3 / Android 16）
        TestHost.checkEq(RestoreGuard.FAMILY_HYPEROS,
                RestoreGuard.familyOf("Xiaomi Communications Co., Ltd.", "Redmi", "25060RK16C"), "Redmi 25060RK16C → HyperOS 族");
        // 对照组：Xiaomi 15（HyperOS 2）
        TestHost.checkEq(RestoreGuard.FAMILY_HYPEROS,
                RestoreGuard.familyOf("Xiaomi Communications Co., Ltd.", "Xiaomi", "15"), "Xiaomi 15 → HyperOS 族");
        TestHost.checkEq(RestoreGuard.FAMILY_HYPEROS,
                RestoreGuard.familyOf("Xiaomi", "POCO", "POCO X6 Pro"), "POCO → HyperOS 族");
        TestHost.checkEq(RestoreGuard.FAMILY_HYPEROS,
                RestoreGuard.familyOf("小米科技", "Xiaomi", "2201123G"), "中文「小米」→ HyperOS 族");
        TestHost.checkEq(RestoreGuard.FAMILY_HARMONY,
                RestoreGuard.familyOf("HUAWEI", "HUAWEI", "ELS-AN00"), "华为 → HarmonyOS 族");
        // 荣耀必须先于华为判断（2021 后独立，manufacturer 含 HONOR）
        TestHost.checkEq(RestoreGuard.FAMILY_MAGIC,
                RestoreGuard.familyOf("HONOR Terminal Co.,LTD", "HONOR", "PGT-AN20"), "荣耀 → MagicOS 族（不误入华为族）");
        TestHost.checkEq(RestoreGuard.FAMILY_COLOROS,
                RestoreGuard.familyOf("OPPO", "OPPO", "PHK110"), "OPPO → ColorOS 族");
        TestHost.checkEq(RestoreGuard.FAMILY_COLOROS,
                RestoreGuard.familyOf("realme", "realme", "RMX3835"), "realme → ColorOS 族");
        TestHost.checkEq(RestoreGuard.FAMILY_COLOROS,
                RestoreGuard.familyOf("OnePlus", "OnePlus", "PHB110"), "一加 → ColorOS 族");
        TestHost.checkEq(RestoreGuard.FAMILY_ORIGINOS,
                RestoreGuard.familyOf("vivo", "vivo", "V2403A"), "vivo → OriginOS 族");
        TestHost.checkEq(RestoreGuard.FAMILY_ORIGINOS,
                RestoreGuard.familyOf("iQOO Mobile", "iQOO", "V2354A"), "iQOO → OriginOS 族");
        TestHost.checkEq(RestoreGuard.FAMILY_ONEUI,
                RestoreGuard.familyOf("samsung", "samsung", "SM-S9280"), "三星 → OneUI 族");
        TestHost.checkEq(RestoreGuard.FAMILY_FLYME,
                RestoreGuard.familyOf("Meizu", "Meizu", "M451Q"), "魅族 → Flyme 族");
        TestHost.checkEq(RestoreGuard.FAMILY_GENERIC,
                RestoreGuard.familyOf("ZTE", "ZTE", "2323B"), "中兴 → 通用族");
        TestHost.checkEq(RestoreGuard.FAMILY_GENERIC,
                RestoreGuard.familyOf("Lenovo", "Lenovo", "L10100"), "联想 → 通用族");
        TestHost.checkEq(RestoreGuard.FAMILY_GENERIC,
                RestoreGuard.familyOf(null, null, null), "空值安全 → 通用族");
        TestHost.checkEq(RestoreGuard.FAMILY_GENERIC,
                RestoreGuard.familyOf("", "", ""), "空串安全 → 通用族");

        // 族标签非空且含中文括号（UI 展示用）
        String[] families = {RestoreGuard.FAMILY_HYPEROS, RestoreGuard.FAMILY_HARMONY,
                RestoreGuard.FAMILY_MAGIC, RestoreGuard.FAMILY_COLOROS, RestoreGuard.FAMILY_ORIGINOS,
                RestoreGuard.FAMILY_ONEUI, RestoreGuard.FAMILY_FLYME, RestoreGuard.FAMILY_GENERIC};
        boolean labelsOk = true;
        for (String f : families) {
            String l = RestoreGuard.familyLabel(f);
            if (l == null || l.length() == 0) labelsOk = false;
        }
        TestHost.check(labelsOk, "全部族标签非空");
        TestHost.check(RestoreGuard.familyLabel(RestoreGuard.FAMILY_HYPEROS).contains("小米"),
                "HyperOS 族标签含「小米系」");
        TestHost.check(RestoreGuard.familyLabel("nonexistent").equals("通用 Android"), "未知族标签回退通用");
    }

    private static void testItems() {
        // HyperOS 双项：自启动 + 省电策略（issue#56 两大吊销诱因）
        KeepAliveItem[] miui = RestoreGuard.itemsFor(RestoreGuard.FAMILY_HYPEROS);
        TestHost.checkEq(2, miui.length, "HyperOS 族 2 项（自启动+省电策略）");
        TestHost.checkEq("hyperos_autostart", miui[0].id, "HyperOS 首项为自启动");
        TestHost.checkEq("com.miui.securitycenter", miui[0].candidates[0][0], "MIUI 自启动候选=安全中心");
        TestHost.checkEq("hyperos_battery", miui[1].id, "HyperOS 次项为省电策略");
        TestHost.checkEq("com.miui.powerkeeper", miui[1].candidates[0][0], "MIUI 省电策略候选=电量和性能");
        TestHost.checkEq("miui_pkg", miui[1].extra, "MIUI 省电策略需附 package_name extra");

        // 华为/荣耀/OPPO/vivo/三星/魅族至少一项且候选合法
        String[] families = {RestoreGuard.FAMILY_HARMONY, RestoreGuard.FAMILY_MAGIC,
                RestoreGuard.FAMILY_COLOROS, RestoreGuard.FAMILY_ORIGINOS,
                RestoreGuard.FAMILY_ONEUI, RestoreGuard.FAMILY_FLYME};
        boolean allOk = true;
        for (String f : families) {
            KeepAliveItem[] items = RestoreGuard.itemsFor(f);
            if (items == null || items.length == 0) {
                allOk = false;
                continue;
            }
            for (KeepAliveItem it : items) {
                if (it.candidates == null || it.candidates.length == 0) allOk = false;
                for (String[] c : it.candidates) {
                    if (c.length != 2 || c[0] == null || c[1] == null
                            || c[0].length() == 0 || c[1].length() == 0) allOk = false;
                }
            }
        }
        TestHost.check(allOk, "六大厂商族每项至少 1 个合法候选（包名+Activity 非空）");

        // 安全不变量 1：所有候选包名全部落在 OEM 系统包白名单内（零注入面）
        boolean pkgSafe = true;
        for (String f : families) {
            for (KeepAliveItem it : RestoreGuard.itemsFor(f)) {
                for (String[] c : it.candidates) {
                    if (!inSystemPkgs(c[0])) pkgSafe = false;
                }
            }
        }
        for (KeepAliveItem it : miui) {
            for (String[] c : it.candidates) {
                if (!inSystemPkgs(c[0])) pkgSafe = false;
            }
        }
        TestHost.check(pkgSafe, "深链候选全部是硬编码 OEM 系统包（无任意组件注入）");

        // 安全不变量 2：候选包名绝不含本应用包名（绝不可能跳回自己）
        boolean noSelf = true;
        String[] all = {RestoreGuard.FAMILY_HYPEROS, RestoreGuard.FAMILY_HARMONY,
                RestoreGuard.FAMILY_MAGIC, RestoreGuard.FAMILY_COLOROS, RestoreGuard.FAMILY_ORIGINOS,
                RestoreGuard.FAMILY_ONEUI, RestoreGuard.FAMILY_FLYME};
        for (String f : all) {
            for (KeepAliveItem it : RestoreGuard.itemsFor(f)) {
                for (String[] c : it.candidates) {
                    if (c[0].contains("zai") || c[0].contains("adshield")) noSelf = false;
                }
            }
        }
        TestHost.check(noSelf, "候选包名不含开屏卫士自身");

        // 安全不变量 3：各族体检项 id 全局唯一（页面按 id 直达不歧义）
        java.util.HashSet<String> ids = new java.util.HashSet<String>();
        boolean idUnique = true;
        for (String f : all) {
            for (KeepAliveItem it : RestoreGuard.itemsFor(f)) {
                if (!ids.add(it.id)) idUnique = false;
            }
        }
        TestHost.check(idUnique, "全族体检项 id 全局唯一");

        // ColorOS/vivo 多候选回退链（ROM 版本差异逐个试）
        TestHost.check(RestoreGuard.itemsFor(RestoreGuard.FAMILY_COLOROS)[0].candidates.length >= 3,
                "ColorOS 自启动 ≥3 候选（版本差异回退链）");
        TestHost.check(RestoreGuard.itemsFor(RestoreGuard.FAMILY_ORIGINOS)[0].candidates.length >= 3,
                "vivo/iQOO 自启动 ≥3 候选（版本差异回退链）");

        // 华为/荣耀双候选（EMUI 老入口 + 新版入口）
        TestHost.check(RestoreGuard.itemsFor(RestoreGuard.FAMILY_HARMONY)[0].candidates.length >= 2,
                "华为启动管理 ≥2 候选");

        // generic 族：零厂商项（页面只显示三项自动检测，不显示误导入口）
        TestHost.checkEq(0, RestoreGuard.itemsFor(RestoreGuard.FAMILY_GENERIC).length, "通用族零厂商项");

        // 标签/说明非空（UI 完整性）
        boolean textOk = true;
        for (String f : all) {
            for (KeepAliveItem it : RestoreGuard.itemsFor(f)) {
                if (it.label == null || it.label.length() == 0
                        || it.desc == null || it.desc.length() == 0) textOk = false;
            }
        }
        TestHost.check(textOk, "全部体检项标签与说明非空");
    }

    private static void testRegistration() {
        String flat = "com.zai.adshield/com.zai.adshield.AdSkipService";
        String shortName = "com.zai.adshield/.AdSkipService";

        TestHost.checkEq(RestoreGuard.REG_REGISTERED,
                RestoreGuard.registrationState(flat, flat, shortName), "精确 flat 匹配 → 登记");
        TestHost.checkEq(RestoreGuard.REG_REGISTERED,
                RestoreGuard.registrationState(shortName, flat, shortName), "精确短名匹配 → 登记");
        TestHost.checkEq(RestoreGuard.REG_REGISTERED,
                RestoreGuard.registrationState("com.foo/.Bar:" + flat + ":com.baz/.Qux", flat, shortName),
                "多服务混排中间段 → 登记");
        TestHost.checkEq(RestoreGuard.REG_REGISTERED,
                RestoreGuard.registrationState(flat + ":com.foo/.Bar", flat, shortName),
                "多服务混排首段 → 登记");
        TestHost.checkEq(RestoreGuard.REG_REGISTERED,
                RestoreGuard.registrationState("com.foo/.Bar:" + shortName, flat, shortName),
                "多服务混排尾段短名 → 登记");

        // 子串误报绝杀：其它服务 ".AdSkipServiceV2" 包含我们的短串 ".AdSkipService"
        TestHost.checkEq(RestoreGuard.REG_REVOKED,
                RestoreGuard.registrationState("com.zai.adshield/.AdSkipServiceV2", flat, shortName),
                "前缀相似服务（V2）不误报登记（contains 时代的真 bug）");
        TestHost.checkEq(RestoreGuard.REG_REVOKED,
                RestoreGuard.registrationState("com.other.app/com.zai.adshield.AdSkipServiceX", flat, shortName),
                "他包相似组件不误报登记");
        TestHost.checkEq(RestoreGuard.REG_REVOKED,
                RestoreGuard.registrationState("com.foo/.Bar", flat, shortName), "仅其它服务 → 吊销");
        TestHost.checkEq(RestoreGuard.REG_REVOKED,
                RestoreGuard.registrationState(null, flat, shortName), "null → 吊销");
        TestHost.checkEq(RestoreGuard.REG_REVOKED,
                RestoreGuard.registrationState("", flat, shortName), "空串 → 吊销");
        TestHost.checkEq(RestoreGuard.REG_REVOKED,
                RestoreGuard.registrationState(":::", flat, shortName), "纯分隔符 → 吊销");
        // 带空格容错（个别 ROM 序列化含空白）
        TestHost.checkEq(RestoreGuard.REG_REGISTERED,
                RestoreGuard.registrationState(" " + flat + " ", flat, shortName), "trim 容错 → 登记");
        // HyperOS 吊销场景：用户开了别的服务但移除了我们
        TestHost.checkEq(RestoreGuard.REG_REVOKED,
                RestoreGuard.registrationState("com.gkd.li/.Service:com.foo/.Bar", flat, shortName),
                "吊销后只剩他人服务 → 吊销");
    }

    private static void testRegChange() {
        TestHost.checkEq(RestoreGuard.CHANGE_UNRELATED,
                RestoreGuard.analyzeRegChange(true, true), "登记持续 → 无关变更");
        TestHost.checkEq(RestoreGuard.CHANGE_UNRELATED,
                RestoreGuard.analyzeRegChange(false, false), "持续吊销 → 无关变更");
        TestHost.checkEq(RestoreGuard.CHANGE_REVOKED,
                RestoreGuard.analyzeRegChange(true, false), "登记被移除 → 吊销遥测");
        TestHost.checkEq(RestoreGuard.CHANGE_ADDED,
                RestoreGuard.analyzeRegChange(false, true), "重新登记 → 恢复遥测");
    }

    private static void testDecisions() {
        // 活着 → 绝不打扰（两种登记态都不该有通知）
        TestHost.checkEq(RestoreGuard.NOTIFY_NONE,
                RestoreGuard.decideOnCheck(true, true), "活着+登记 → 零通知");
        TestHost.checkEq(RestoreGuard.NOTIFY_NONE,
                RestoreGuard.decideOnCheck(true, false), "活着+未登记（瞬态） → 零通知");

        // 登记仍在 → 自动恢复路径（绝不引导系统设置 = 绝不看 10 秒警告）
        TestHost.checkEq(RestoreGuard.NOTIFY_AUTO_RESTORE,
                RestoreGuard.decideOnCheck(false, true), "死了+登记在 → 自动恢复通知");
        TestHost.checkEq(RestoreGuard.NOTIFY_AUTO_RESTORE,
                RestoreGuard.decideAfterKicks(false, true), "踢链后仍死+登记在 → 自动恢复通知");
        // 登记被吊销 → 才允许直达系统设置
        TestHost.checkEq(RestoreGuard.NOTIFY_TO_SETTINGS,
                RestoreGuard.decideOnCheck(false, false), "死了+吊销 → 直达开关");
        TestHost.checkEq(RestoreGuard.NOTIFY_TO_SETTINGS,
                RestoreGuard.decideAfterKicks(false, false), "踢链后仍死+吊销 → 直达开关");
        // 踢链自愈成功 → 零通知零打扰
        TestHost.checkEq(RestoreGuard.NOTIFY_NONE,
                RestoreGuard.decideAfterKicks(true, true), "踢链自愈成功 → 零通知");
        TestHost.checkEq(RestoreGuard.NOTIFY_NONE,
                RestoreGuard.decideAfterKicks(true, false), "踢链后活着（瞬态） → 零通知");

        // ★ 核心不变量穷举：registered=true 的任何组合绝不输出 TO_SETTINGS
        boolean invariant = true;
        for (boolean alive : new boolean[]{true, false}) {
            if (RestoreGuard.decideOnCheck(alive, true) == RestoreGuard.NOTIFY_TO_SETTINGS) invariant = false;
            if (RestoreGuard.decideAfterKicks(alive, true) == RestoreGuard.NOTIFY_TO_SETTINGS) invariant = false;
        }
        TestHost.check(invariant, "★不变量：登记仍在的任何状态绝不引导去系统设置（10 秒警告仅剩真吊销）");
    }

    private static void testInAppRevive() {
        TestHost.checkEq(RestoreGuard.INAPP_OK_RUNNING,
                RestoreGuard.decideInAppRevive(true, true, 0, 15000), "本来就活着 → OK");
        TestHost.checkEq(RestoreGuard.INAPP_OK_RUNNING,
                RestoreGuard.decideInAppRevive(true, false, 0, 15000), "活着（瞬态未登记） → OK");
        TestHost.checkEq(RestoreGuard.INAPP_OK_REVIVED,
                RestoreGuard.decideInAppRevive(true, true, 1200, 15000), "等到了重绑 → REVIVED");
        TestHost.checkEq(RestoreGuard.INAPP_CONTINUE,
                RestoreGuard.decideInAppRevive(false, true, 0, 15000), "窗口未满+登记在 → 继续");
        TestHost.checkEq(RestoreGuard.INAPP_CONTINUE,
                RestoreGuard.decideInAppRevive(false, true, 14999, 15000), "最后一毫秒仍登记 → 继续");
        TestHost.checkEq(RestoreGuard.INAPP_FAIL_SLOW,
                RestoreGuard.decideInAppRevive(false, true, 15000, 15000), "窗口用尽仍登记 → 绑定慢（不跳设置）");
        TestHost.checkEq(RestoreGuard.INAPP_FAIL_DISABLED,
                RestoreGuard.decideInAppRevive(false, false, 500, 15000), "中途吊销 → 立即停（disabled）");
        TestHost.checkEq(RestoreGuard.INAPP_FAIL_DISABLED,
                RestoreGuard.decideInAppRevive(false, false, 15000, 15000), "窗口用尽且吊销 → disabled");

        // ★ 不变量：registered=true 的输出永不含 FAIL_DISABLED（绝不引导设置）
        boolean invariant = true;
        long[] waits = {0, 1, 7000, 14999, 15000, 20000};
        for (long w : waits) {
            int d = RestoreGuard.decideInAppRevive(false, true, w, 15000);
            if (d == RestoreGuard.INAPP_FAIL_DISABLED) invariant = false;
        }
        TestHost.check(invariant, "★不变量：登记仍在绝不输出 FAIL_DISABLED");
        // ★ 不变量：registered=false 时除 alive 外唯一输出 FAIL_DISABLED
        boolean invariant2 = true;
        for (long w : waits) {
            int d = RestoreGuard.decideInAppRevive(false, false, w, 15000);
            if (d != RestoreGuard.INAPP_FAIL_DISABLED) invariant2 = false;
        }
        TestHost.check(invariant2, "★不变量：吊销态非活着即 FAIL_DISABLED（立即止损不傻等）");
    }

    private static void testPollPlan() {
        long[] plan = RestoreGuard.pollPlan(15000);
        TestHost.checkEq(0L, plan[0], "首针时刻 0（立即踢）");
        boolean monotonic = true;
        for (int i = 1; i < plan.length; i++) {
            if (plan[i] <= plan[i - 1]) monotonic = false;
        }
        TestHost.check(monotonic, "时刻表单调递增");
        TestHost.check(plan[plan.length - 1] <= 15000, "末针不超窗（15s）");
        boolean gapsOk = true;
        for (int i = 1; i < plan.length; i++) {
            long gap = plan[i] - plan[i - 1];
            if (gap != 300 && gap != 500) gapsOk = false;
        }
        TestHost.check(gapsOk, "相邻间隔只取 300/500ms（快踢段+等待段）");
        // 前 2s 每 300ms 快踢（重绑多发期）
        int fastKicks = 0;
        for (long at : plan) if (at <= 2000) fastKicks++;
        TestHost.check(fastKicks >= 7, "前 2s 至少 7 针快踢（" + fastKicks + "）");
        TestHost.check(plan.length >= 30, "总针数 ≥30（覆盖 15s，v4.4.0 仅 12 针/6s）");
        // 边界窗口
        long[] tiny = RestoreGuard.pollPlan(0);
        TestHost.checkEq(1, tiny.length, "零窗仍有首针 0");
        TestHost.checkEq(0L, tiny[0], "零窗首针为 0");
        long[] small = RestoreGuard.pollPlan(900);
        TestHost.check(small.length >= 4, "900ms 窗 ≥4 针");
    }

    private static void testStartBanner() {
        // 动态版本注入（issue#56 实锤：v5.1.1 日志横幅仍显示 v4.5.0）
        String b1 = RestoreGuard.startBanner("5.2.0", "分应用表 28 款/35 条文案", 0, 0, 0, 0);
        TestHost.check(b1.contains("v5.2.0"), "横幅含运行时版本 v5.2.0");
        String b2 = RestoreGuard.startBanner("9.9.9", "X", 0, 0, 0, 0);
        TestHost.check(b2.contains("v9.9.9"), "横幅随版本参数变化（永不陈旧）");
        TestHost.check(!b2.contains("v4.5.0"), "横幅不再含硬编码 v4.5.0");
        TestHost.check(b1.contains("规则"), "横幅含规则统计");
        TestHost.check(!b1.contains("清理"), "零误学时不显示清理段");
        TestHost.check(!b1.contains("白名单"), "零白名单时不显示白名单段");

        // 条件段：仅 >0 时出现
        String b3 = RestoreGuard.startBanner("5.2.0", "R", 3, 2, 1, 4);
        TestHost.check(b3.contains("已清理 3 条误学窗口指纹"), "误学指纹段（3）");
        TestHost.check(b3.contains("已清理 2 条桌面误学位置规则"), "桌面误学段（2）");
        TestHost.check(b3.contains("已清理 1 条系统工具/短信误学规则"), "OEM 误学段（1）");
        TestHost.check(b3.contains("白名单 4 个应用零打扰"), "白名单段（4）");

        // 空值防御
        String b4 = RestoreGuard.startBanner(null, null, 0, 0, 0, 0);
        TestHost.check(b4 != null && b4.length() > 0, "null 版本不崩（回退 ?）");
        TestHost.check(b4.contains("?"), "null 版本回退占位符");
    }

    private static void testAlertTexts() {
        // 自动恢复态：绝不提「去设置」，明示无需 10 秒等待
        String auto = RestoreGuard.alertText(RestoreGuard.NOTIFY_AUTO_RESTORE);
        TestHost.check(auto.contains("自动恢复"), "自动恢复文案含「自动恢复」");
        TestHost.check(auto.contains("无需系统设置"), "自动恢复文案明示无需系统设置");
        TestHost.check(auto.contains("10 秒"), "自动恢复文案明示无 10 秒等待");
        TestHost.check(!auto.contains("直达"), "自动恢复文案不含「直达」（不引导设置）");

        // 直达设置态：预期管理（10 秒安全确认属正常）+ 直达开关
        String toSettings = RestoreGuard.alertText(RestoreGuard.NOTIFY_TO_SETTINGS);
        TestHost.check(toSettings.contains("直达"), "吊销文案含「直达」");
        TestHost.check(toSettings.contains("10 秒"), "吊销文案含 10 秒预期管理");
        TestHost.check(toSettings.contains("正常"), "吊销文案明示属正常（防诈设计）");

        // 标题两态可区分
        TestHost.check(!RestoreGuard.alertTitle(RestoreGuard.NOTIFY_AUTO_RESTORE)
                        .equals(RestoreGuard.alertTitle(RestoreGuard.NOTIFY_TO_SETTINGS)),
                "两态标题可区分");
        TestHost.check(RestoreGuard.alertTitle(RestoreGuard.NOTIFY_TO_SETTINGS).contains("待开启"),
                "吊销态标题含「待开启」");
        TestHost.check(RestoreGuard.alertTitle(RestoreGuard.NOTIFY_AUTO_RESTORE).contains("待恢复"),
                "自动恢复态标题含「待恢复」");

        // 大文本：两态都带保活体检指引（防复发）
        String big1 = RestoreGuard.alertBigText(RestoreGuard.NOTIFY_AUTO_RESTORE, "MIUI/HyperOS（小米系）");
        String big2 = RestoreGuard.alertBigText(RestoreGuard.NOTIFY_TO_SETTINGS, "MIUI/HyperOS（小米系）");
        TestHost.check(big1.contains("保活体检"), "自动恢复大文本含保活体检指引");
        TestHost.check(big2.contains("保活体检"), "吊销大文本含保活体检指引");
        TestHost.check(big2.contains("MIUI/HyperOS"), "大文本含品牌族名");
        TestHost.check(big1.contains("15 秒"), "自动恢复大文本明示 15s 深挖");
    }
}
