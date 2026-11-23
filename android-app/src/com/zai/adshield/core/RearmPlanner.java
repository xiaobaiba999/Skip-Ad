package com.zai.adshield.core;

/**
 * v7.15.1 亮屏/解锁重入目标解析器（纯函数 · JVM 可测）。
 * ─────────────────────────────────────────────────────────────
 * 死因链（真机日志实锤 · issue#94 · Xiaomi 2410DPN6CC · v7.15.0）：
 *  ① 息屏期间锁屏窗（com.android.systemui · Keyguard）走零扫描门
 *     （isZeroScanScenario 在息屏门之前）把 lastFgPkg 顶成锁屏宿主——
 *     「前台包名追踪」被锁屏污染；
 *  ② 亮屏/解锁 rearm 只认 lastFgPkg → 补扫的是锁屏自己的树
 *     （「开屏未命中（systemui）树 73 节点」×6 连发；锁屏时钟被当
 *     「倒计时疑似装饰数字」）；
 *  ③ 解锁后真实应用的前台回归若不伴随 WINDOW_STATE_CHANGED（实测
 *     HyperOS/Android17 常态），内容事件被前台门（pkg != lastFgPkg
 *     前置过滤）全部吞掉——引擎对真实应用彻底致盲，广告白看。
 *
 * 根治口径（用户原话）：亮屏/解锁视作重新进入——窗龄重置（旧窗龄不再
 * 把扫描门判超窗拒扫）+ 节流清空 + 立即主动补扫。本类负责其中
 * 「补扫目标是谁」的纯逻辑决策：
 *  · 前台即真实应用 → 直连重入（ARM_DIRECT）；
 *  · 前台被锁屏宿主污染 → 校正回 lastAppPkg（主路径维护的「最后
 *    一个真实可扫描应用」）并同步修复前台追踪（ARM_REPAIRED）——
 *    解锁瞬间键 guard 正在消散，屏上即将可见的就是它；
 *  · 桌面/白名单/设置等真前台 → 用户此刻就在那里，无需补扫
 *    （SKIP_NO_TARGET——绝不为了「像在干活」而扫描不可见应用）；
 *  · 亮屏而锁屏仍在场（SCREEN_ON 早于解锁）→ 一律 SKIP_LOCKED
 *    （屏上还是锁屏：坐标出手会落在锁屏上=误触红线；重入推迟到
 *    USER_PRESENT——每解锁一次一链的成本语义保持）。
 *
 * 设计红线：纯 Java 零依赖，JVM 可测（RearmPlannerTest）。
 */
public final class RearmPlanner {

    private RearmPlanner() {
    }

    /* ───────── 决策结果码 ───────── */
    /** 前台无可补扫应用（桌面/白名单/零扫描宿主/空前台） */
    public static final int SKIP_NO_TARGET = 0;
    /** 亮屏但锁屏仍在场——重入推迟到 USER_PRESENT */
    public static final int SKIP_LOCKED = 1;
    /** 前台即真实应用——直连重入 */
    public static final int ARM_DIRECT = 2;
    /** 前台被锁屏宿主污染——校正回真实应用并修复前台追踪 */
    public static final int ARM_REPAIRED = 3;

    /** 决策结果（服务侧据此重置/补扫/记日志） */
    public static final class Plan {
        /** 结果码（SKIP_* / ARM_*） */
        public int action = SKIP_NO_TARGET;
        /** ARM_*：要重入补扫的真实应用包名 */
        public String target = null;
        /** ARM_REPAIRED：前台追踪需同步修复（lastFgPkg ← target） */
        public boolean repairFg = false;
    }

    /**
     * 解析亮屏/解锁瞬间的重入目标。
     *
     * @param fg        当前前台追踪 lastFgPkg（可能已被锁屏宿主污染）
     * @param lastApp   主路径维护的「最后一个真实可扫描应用」前台
     *                  （服务侧 lastAppPkg——锁屏/桌面/白名单永不写入）
     * @param fgOk      fg 是否为可补扫应用（服务侧用与主路径同键闸预判：
     *                  非桌面/白名单/系统噪声/零扫描/相机/聊天主界面/小程序容器）
     * @param appOk     lastApp 是否同上
     * @param unlocked  本次是否已解锁（USER_PRESENT，或亮屏即无锁设备）；
     *                  false = 亮屏但锁屏仍在场
     */
    public static Plan resolve(String fg, String lastApp, boolean fgOk, boolean appOk, boolean unlocked) {
        Plan p = new Plan();
        /* 锁屏还在场：屏上无广告可拦（广告在锁屏后面看不见），出手只会
         * 点到锁屏上（坐标点击落在可视表面=误触红线）——状态都不动，
         * 等 USER_PRESENT 真正解锁再重入。 */
        if (!unlocked) {
            p.action = SKIP_LOCKED;
            return p;
        }
        /* 前台即真实应用（正常路径：息屏前就在此应用、息屏期间前台未被污染） */
        if (fgOk) {
            p.action = ARM_DIRECT;
            p.target = fg;
            return p;
        }
        /* 前台被锁屏宿主污染（systemui/keyguard/lockscreen）→ 校正回
         * 最后一个真实应用，并修复前台追踪（解锁后真实应用的内容事件
         * 不再被前台门前置过滤吞掉——引擎致盲的根除点）。 */
        if (lastApp != null && lastApp.length() > 0 && appOk
                && ComplianceGuard.isLockHostPkg(fg)) {
            p.action = ARM_REPAIRED;
            p.target = lastApp;
            p.repairFg = true;
            return p;
        }
        /* 桌面/白名单/设置等真前台：无需补扫（fgOk=false 且非锁屏污染） */
        return p;
    }
}
