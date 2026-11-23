package com.zai.adshield.test;

import com.zai.adshield.core.RearmPlanner;
import com.zai.adshield.core.ComplianceGuard;
import com.zai.adshield.core.AdDecisionCore;

/**
 * ═══════════════════════════════════════════════════════════════
 *  v7.15.1 亮屏/解锁重入目标解析测试（RearmPlannerTest）
 * ═══════════════════════════════════════════════════════════════
 *  真机日志实锤（issue#94 · Xiaomi 2410DPN6CC · v7.15.0 · Android 17）：
 *  息屏十几分钟回来广告拦不住——引擎日志显示「解锁补扫」补的是锁屏自己
 *  的树（「开屏未命中（systemui）树 73 节点」21:45~21:54 六连发，锁屏时钟
 *  被当「倒计时疑似装饰数字」），真实应用的内容事件被前台门前置过滤
 *  全部吞掉（pkg != lastFgPkg）——广告白看。
 *
 *  死因链三层（本套件逐层钉死）：
 *   ① 前台污染：锁屏宿主（com.android.systemui）息屏期间顶掉 lastFgPkg；
 *   ② 补扫走偏：v7.15.0 rearm 只认 lastFgPkg → 补扫锁屏树（真实应用没被扫）；
 *   ③ 引擎致盲：前台追踪不复原 → 真实应用内容事件被前置过滤吞掉。
 *
 *  验证目标（用户口径：亮屏/解锁视作重新进入——窗龄重置 + 节流清空 +
 *  立即主动补扫；目标必须是【真实应用】而不是锁屏）：
 *   ① 锁屏污染校正：ARM_REPAIRED + 目标=真实应用 + repairFg=true；
 *   ② 前台即真实应用：ARM_DIRECT 直连重入；
 *   ③ 桌面/白名单/空前台：SKIP_NO_TARGET（绝不扫描不可见应用）；
 *   ④ 亮屏锁屏在场：SKIP_LOCKED（绝不对锁屏后的隐藏树出手——坐标点击
 *      会落在锁屏上=误触红线）；
 *   ⑤ isLockHostPkg 锁屏宿主族识别（systemui/keyguard/lockscreen +
 *      厂商变体 + 反例）。
 */
public final class RearmPlannerTest {

    /* 真机实锤包名（issue#94 日志原样） */
    private static final String LOCK_HOST = "com.android.systemui";
    private static final String REAL_APP = "com.xunmeng.pinduoduo";

    public static void main(String[] args) {
        TestHost.suite("① 锁屏污染校正（真机实锤场景）");
        testPollutionRepair();

        TestHost.suite("② 前台即真实应用（直连重入）");
        testDirect();

        TestHost.suite("③ 无需补扫场景（桌面/白名单/空前台）");
        testSkips();

        TestHost.suite("④ 亮屏锁屏在场（绝不对锁屏后的隐藏树出手）");
        testLocked();

        TestHost.suite("⑤ isLockHostPkg 锁屏宿主族");
        testLockHostPkg();

        TestHost.done();
    }

    /* ══ ① 死因链完整复现→校正 ══ */
    private static void testPollutionRepair() {
        /* 息屏前在真实应用 → 息屏期间 lastFgPkg 被锁屏顶掉（死因①）；
         * 解锁瞬间（USER_PRESENT）：目标必须校正回真实应用（死因②根除）
         * 并要求修复前台追踪（死因③根除——内容事件不再被前置过滤吞） */
        RearmPlanner.Plan p = RearmPlanner.resolve(
                LOCK_HOST, REAL_APP, false, true, true);
        TestHost.checkEq(RearmPlanner.ARM_REPAIRED, p.action,
                "锁屏污染 → ARM_REPAIRED（补扫目标校正）");
        TestHost.checkEq(REAL_APP, p.target, "目标 = 真实应用（绝不是锁屏宿主）");
        TestHost.check(p.repairFg, "repairFg=true（前台追踪同步修复——引擎致盲根除）");

        /* 厂商锁屏宿主变体（HyperOS/MIUI keyguard 族同样污染） */
        p = RearmPlanner.resolve("com.miui.keyguard", REAL_APP, false, true, true);
        TestHost.checkEq(RearmPlanner.ARM_REPAIRED, p.action, "厂商锁屏宿主（com.miui.keyguard）同样校正");
        TestHost.checkEq(REAL_APP, p.target, "厂商锁屏宿主 → 目标仍是真实应用");

        /* lastApp 也无效（服务刚启动，尚无真实应用前台）→ 无可补扫目标 */
        p = RearmPlanner.resolve(LOCK_HOST, null, false, false, true);
        TestHost.checkEq(RearmPlanner.SKIP_NO_TARGET, p.action,
                "锁屏污染但无真实应用记录 → 无目标（绝不补扫锁屏树）");
        p = RearmPlanner.resolve(LOCK_HOST, "com.miui.home", false, false, true);
        TestHost.checkEq(RearmPlanner.SKIP_NO_TARGET, p.action,
                "锁屏污染但 lastApp 已失格（桌面）→ 无目标");
    }

    /* ══ ② 正常路径：前台即真实应用 ══ */
    private static void testDirect() {
        RearmPlanner.Plan p = RearmPlanner.resolve(
                REAL_APP, REAL_APP, true, true, true);
        TestHost.checkEq(RearmPlanner.ARM_DIRECT, p.action, "前台即真实应用 → ARM_DIRECT");
        TestHost.checkEq(REAL_APP, p.target, "直连目标 = 前台应用本身");
        TestHost.check(!p.repairFg, "直连无需修复前台追踪");

        /* lastApp 与 fg 不同（用户在 A 应用息屏、解锁时已切到 B）：以当前
         * 前台为准——补扫的是屏上真正可见的应用 */
        p = RearmPlanner.resolve("com.tencent.mm", "com.ss.android.ugc.aweme", true, true, true);
        TestHost.checkEq(RearmPlanner.ARM_DIRECT, p.action, "前台已换到 B 应用 → 仍直连当前前台");
        TestHost.checkEq("com.tencent.mm", p.target, "目标 = 当前前台（非息屏前应用）");
    }

    /* ══ ③ 无需补扫：真前台不是广告宿主 ══ */
    private static void testSkips() {
        /* 桌面（真前台——用户解锁后就在桌面，无可拦广告） */
        RearmPlanner.Plan p = RearmPlanner.resolve(
                "com.miui.home", REAL_APP, false, true, true);
        TestHost.checkEq(RearmPlanner.SKIP_NO_TARGET, p.action,
                "前台桌面 → SKIP_NO_TARGET（绝不为「像在干活」而扫描后台应用）");
        /* 前台不是锁屏宿主、只是不可补扫（设置页）→ 不校正、不补扫 */
        p = RearmPlanner.resolve("com.android.settings", REAL_APP, false, true, true);
        TestHost.checkEq(RearmPlanner.SKIP_NO_TARGET, p.action,
                "前台设置页（零扫描宿主但非锁屏）→ 无需补扫（用户此刻就在设置里）");
        /* 空前台（服务刚连上，尚无任何窗口事件） */
        p = RearmPlanner.resolve(null, null, false, false, true);
        TestHost.checkEq(RearmPlanner.SKIP_NO_TARGET, p.action, "空前台 → 无目标");
        /* 空字符串 lastApp 视同空 */
        p = RearmPlanner.resolve(LOCK_HOST, "", false, true, true);
        TestHost.checkEq(RearmPlanner.SKIP_NO_TARGET, p.action, "空字符串 lastApp 视同无记录");
    }

    /* ══ ④ 亮屏而锁屏仍在场（SCREEN_ON 早于解锁）══ */
    private static void testLocked() {
        /* 真机场景：SCREEN_ON 时键 guard 还在屏上——广告在锁屏后面看不见，
         * 出手只会点到锁屏上（误触红线）。一律 SKIP_LOCKED，等 USER_PRESENT */
        RearmPlanner.Plan p = RearmPlanner.resolve(
                REAL_APP, REAL_APP, true, true, false);
        TestHost.checkEq(RearmPlanner.SKIP_LOCKED, p.action,
                "亮屏锁屏在场（即便前台是真实应用）→ SKIP_LOCKED");
        TestHost.check(p.target == null, "SKIP_LOCKED 不给目标（状态都不动）");
        /* 锁屏污染场景同样按兵不动 */
        p = RearmPlanner.resolve(LOCK_HOST, REAL_APP, false, true, false);
        TestHost.checkEq(RearmPlanner.SKIP_LOCKED, p.action,
                "亮屏锁屏在场（前台被污染）→ 同样 SKIP_LOCKED");
        TestHost.check(!p.repairFg, "SKIP_LOCKED 不修复前台（留给解锁瞬间一并处理）");
    }

    /* ══ ⑤ 锁屏宿主族识别 ══ */
    private static void testLockHostPkg() {
        /* 真机实锤主犯 */
        TestHost.check(ComplianceGuard.isLockHostPkg(LOCK_HOST),
                "com.android.systemui ∈ 锁屏宿主");
        /* 厂商变体（大小写不敏感） */
        TestHost.check(ComplianceGuard.isLockHostPkg("com.miui.keyguard"),
                "com.miui.keyguard ∈ 锁屏宿主");
        TestHost.check(ComplianceGuard.isLockHostPkg("com.android.keyguard"),
                "com.android.keyguard ∈ 锁屏宿主");
        TestHost.check(ComplianceGuard.isLockHostPkg("COM.ANDROID.SYSTEMUI"),
                "大小写不敏感（大写变体命中）");
        TestHost.check(ComplianceGuard.isLockHostPkg("com.oppo.lockscreen"),
                "lockscreen 族 ∈ 锁屏宿主");
        /* 反例：真实应用绝不误伤（ReaLcase：拼多多/抖音/QQ/微博/淘宝/输入法非 lock 族） */
        String[] apps = {REAL_APP, "com.tencent.mm", "com.ss.android.ugc.aweme",
                "com.sina.weibo", "com.taobao.taobao", "sg.bpm.pig"};
        for (String a : apps) {
            TestHost.check(!ComplianceGuard.isLockHostPkg(a),
                    "真实应用 " + a + " ∉ 锁屏宿主（零误伤）");
        }
        /* 空值防御 */
        TestHost.check(!ComplianceGuard.isLockHostPkg(null), "null ∉ 锁屏宿主");
        /* 与 isZeroScanScenario 的锁屏分支保持同源（systemui 走零扫描门
         * 正是污染入口——两族判定必须同时命中） */
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.android.systemui", "Keyguard")
                        == ComplianceGuard.isLockHostPkg("com.android.systemui"),
                "systemui 双判定同源（零扫描门 ↔ 锁屏宿主）");
        /* 桌面应用既非锁屏宿主也不可作 rearm 目标（AdDecisionCore 层再拦一道） */
        TestHost.check(!ComplianceGuard.isLockHostPkg("com.miui.home")
                        && AdDecisionCore.isLauncherLike("com.miui.home"),
                "桌面由 launcher 层拦截（不归锁屏宿主族管）");
    }
}
