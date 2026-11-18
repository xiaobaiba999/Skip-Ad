package com.skipad.power;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import java.util.Locale;

/**
 * 分级动态功耗治理器（v5.4.0）：设备四档 × 电量四态 = 16 档策略矩阵。
 *
 * <p>与 Web 端功耗画像（src/lib/instant-engine.ts POWER_PROFILE）同构：Web 端负责
 * 演练场对照仪表，Android 端负责真机策略执行；两端 16 档矩阵数值一致。
 *
 * <p>与 src/lib/power-governor.ts 的函数映射（TS 为规范源）：
 * detectTier(Context) ↔ classifyDevice(ramGb, cores, socScore)、
 * readBattery(Context) ↔ resolveBattery(level, charging, saver)、
 * currentPolicy() ↔ computePolicy(tier, battery)、matrixPolicy(...) ↔ policyMatrix()。
 *
 * <p><b>红线（永不降级通道）</b>：以下三条通道不参与任何降级——哪怕电量极低档也
 * 保持不低于「中档」参数执行：
 * <ol>
 *   <li>SDK 广告窗 className 指纹短路（零文本广告唯一判据，降级即漏拦）</li>
 *   <li>Dialog 弹窗双信号识别（角标+关闭键，弹窗窗期不缩水）</li>
 *   <li>学习指纹 P0 预拦截通道（窗口附加即断，本身就是零成本通道）</li>
 * </ol>
 * 降级只作用于：DFS 节点数/深度、重试链密度、扫描间隔、弹窗窗预算、学习开关。
 *
 * <p>红线依据：李跳跳类工具的教训是「一刀切省电」导致低端机漏拦率飙升；本方案
 * 按设备能力与电量状态分级，可降级通道与红线通道分离，命中红线场景时策略强制
 * 取 {@link #POLICY_FLOOR}（不低于中档参数）。
 */
public final class PowerGovernor {

    // ───────────────────────── 设备四档 / 电量四态 ─────────────────────────

    /** 设备档位（按内存 + 核数 + SoC 评分） */
    public enum DeviceTier {
        LOW_END("入门"), MID("中端"), HIGH("高端"), FLAGSHIP("旗舰");

        public final String label;

        DeviceTier(String label) {
            this.label = label;
        }
    }

    /** 电量状态 */
    public enum BatteryState {
        CHARGING("充电/满电"), NORMAL("正常 >50%"), LOW("低电 20%~50%"), CRITICAL("极低 <20%");

        public final String label;

        BatteryState(String label) {
            this.label = label;
        }
    }

    // ───────────────────────── 策略 ─────────────────────────

    /** 动态策略（MatchingEngine 与 SkipAdService 消费） */
    public static final class PowerPolicy {
        /** 相邻窗口最小扫描间隔（毫秒；节流防抖） */
        public final int scanIntervalMs;
        /** 前密后疏重试链（窗口切换后按此链重扫，命中即熔断） */
        public final long[] retryChainMs;
        /** DFS 节点数上限（属性缓存收集上限） */
        public final int maxDfsNodes;
        /** DFS 深度上限 */
        public final int maxDfsDepth;
        /** 弹窗识别窗期（毫秒） */
        public final int popupWindowMs;
        /** 学习开关（极低电量关闭学习层，红线通道不受影响） */
        public final boolean learningEnabled;
        /** 内容事件预算（TYPE_WINDOW_CONTENT_CHANGED 每分钟最多处理数） */
        public final int contentEventBudgetPerMin;
        /** 突发弹窗窗预算（每应用每 10 分钟最多开的弹窗识别窗数） */
        public final int burstPopupQuota;

        // ── 红线通道（构造即固定为 true，任何档位不可关闭） ──
        public final boolean sdkFingerprintChannel = true;
        public final boolean popupDualSignalChannel = true;
        public final boolean learnedPreemptChannel = true;

        PowerPolicy(int scanIntervalMs, long[] retryChainMs, int maxDfsNodes, int maxDfsDepth,
                    int popupWindowMs, boolean learningEnabled,
                    int contentEventBudgetPerMin, int burstPopupQuota) {
            this.scanIntervalMs = scanIntervalMs;
            this.retryChainMs = retryChainMs;
            this.maxDfsNodes = maxDfsNodes;
            this.maxDfsDepth = maxDfsDepth;
            this.popupWindowMs = popupWindowMs;
            this.learningEnabled = learningEnabled;
            this.contentEventBudgetPerMin = contentEventBudgetPerMin;
            this.burstPopupQuota = burstPopupQuota;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "PowerPolicy{scan=%dms chain=%d nodes=%d depth=%d popup=%dms learn=%s budget=%d/min burst=%d}",
                    scanIntervalMs, retryChainMs.length, maxDfsNodes, maxDfsDepth,
                    popupWindowMs, learningEnabled, contentEventBudgetPerMin, burstPopupQuota);
        }
    }

    /** 重试链密度档（前密后疏，越靠后越稀） */
    private static final long[] CHAIN_FULL = {0, 120, 240, 400, 650, 1000};
    private static final long[] CHAIN_MID = {0, 180, 400, 800};
    private static final long[] CHAIN_SPARSE = {0, 240, 650};
    private static final long[] CHAIN_MIN = {0, 650};

    /**
     * 红线兜底策略（不低于中档参数）：红线通道在极低档时的强制下限。
     */
    public static final PowerPolicy POLICY_FLOOR = new PowerPolicy(
            800, CHAIN_MID, 400, 22, 8000, true, 30, 2);

    /**
     * 16 档策略矩阵（4 设备档 × 4 电量态）。矩阵与 docs/OPTIMIZATION-v5.4.0.md
     * §2.1 的表格一一对应，改动需双端（文档+代码）同步。
     */
    private static final PowerPolicy[][] MATRIX = new PowerPolicy[4][4];

    static {
        // 行序：LOW_END / MID / HIGH / FLAGSHIP；列序：CHARGING / NORMAL / LOW / CRITICAL
        MATRIX[0][0] = new PowerPolicy(1000, CHAIN_SPARSE, 280, 18, 8000, true, 12, 1);
        MATRIX[0][1] = new PowerPolicy(1200, CHAIN_SPARSE, 240, 16, 7000, true, 10, 1);
        MATRIX[0][2] = new PowerPolicy(1500, CHAIN_MIN, 200, 16, 6000, false, 8, 1);
        MATRIX[0][3] = new PowerPolicy(2000, CHAIN_MIN, 160, 14, 5000, false, 6, 1);
        MATRIX[1][0] = new PowerPolicy(600, CHAIN_MID, 450, 24, 9000, true, 30, 2);
        MATRIX[1][1] = new PowerPolicy(800, CHAIN_MID, 400, 22, 8000, true, 24, 2);
        MATRIX[1][2] = new PowerPolicy(1000, CHAIN_SPARSE, 320, 20, 7000, true, 18, 2);
        MATRIX[1][3] = new PowerPolicy(1400, CHAIN_MIN, 260, 18, 6000, false, 12, 1);
        MATRIX[2][0] = new PowerPolicy(400, CHAIN_FULL, 650, 28, 10000, true, 60, 2);
        MATRIX[2][1] = new PowerPolicy(500, CHAIN_FULL, 600, 26, 9000, true, 48, 2);
        MATRIX[2][2] = new PowerPolicy(700, CHAIN_MID, 480, 24, 8000, true, 36, 2);
        MATRIX[2][3] = new PowerPolicy(1000, CHAIN_SPARSE, 380, 22, 7000, false, 24, 1);
        MATRIX[3][0] = new PowerPolicy(250, CHAIN_FULL, 800, 32, 10000, true, 90, 2);
        MATRIX[3][1] = new PowerPolicy(350, CHAIN_FULL, 750, 30, 10000, true, 72, 2);
        MATRIX[3][2] = new PowerPolicy(500, CHAIN_FULL, 600, 28, 9000, true, 54, 2);
        MATRIX[3][3] = new PowerPolicy(800, CHAIN_MID, 480, 26, 8000, true, 36, 1);
    }

    // ───────────────────────── 单例 ─────────────────────────

    private static volatile PowerGovernor sInstance;

    private final Context appContext;
    private volatile DeviceTier tier;
    private volatile BatteryState battery;
    private volatile PowerPolicy current;

    private PowerGovernor(Context context) {
        this.appContext = context.getApplicationContext();
        this.tier = detectTier(appContext);
        this.battery = BatteryState.NORMAL;
        this.current = MATRIX[tier.ordinal()][battery.ordinal()];
    }

    public static PowerGovernor getInstance() {
        if (sInstance == null) {
            synchronized (PowerGovernor.class) {
                if (sInstance == null) {
                    // 无 Context 的降级单例（JVM 单测用：策略矩阵本身可测）
                    sInstance = new PowerGovernor(null);
                }
            }
        }
        return sInstance;
    }

    public static PowerGovernor init(Context context) {
        synchronized (PowerGovernor.class) {
            sInstance = new PowerGovernor(context);
        }
        return sInstance;
    }

    // ───────────────────────── 设备档位识别 ─────────────────────────

    /**
     * 设备档位识别（评分制）：
     * <pre>
     * memScore : totalMem &lt;3GB→0 | 3~6GB→1 | 6~8GB→2 | 8~12GB→3 | ≥12GB→4
     * coreScore: ≤4 核→0 | 6 核→1 | ≥8 核→2
     * socScore : 已知旗舰 SoC→3 | 已知中端→1 | 未知→0
     * tier     : total &lt;3→LOW_END | &lt;6→MID | &lt;9→HIGH | ≥9→FLAGSHIP
     * </pre>
     * SoC 识别：API 31+ 用 Build.VERSION.SOC_MODEL / SOC_MANUFACTURER（题目所述
     * Build.SoC 系列字段），低版本回退 Build.HARDWARE 关键词匹配。
     */
    public static DeviceTier detectTier(Context context) {
        int memScore = 0;
        if (context != null) {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long gb = mi.totalMem / (1024L * 1024 * 1024);
                if (gb >= 12) {
                    memScore = 4;
                } else if (gb >= 8) {
                    memScore = 3;
                } else if (gb >= 6) {
                    memScore = 2;
                } else if (gb >= 3) {
                    memScore = 1;
                }
            }
        }
        int cores = Runtime.getRuntime().availableProcessors();
        int coreScore = cores >= 8 ? 2 : (cores >= 6 ? 1 : 0);
        int socScore = socScore();
        int total = memScore + coreScore + socScore;
        if (total >= 9) {
            return DeviceTier.FLAGSHIP;
        }
        if (total >= 6) {
            return DeviceTier.HIGH;
        }
        if (total >= 3) {
            return DeviceTier.MID;
        }
        return DeviceTier.LOW_END;
    }

    /** 旗舰 SoC 关键词（Build.VERSION.SOC_MODEL / Build.HARDWARE 小写匹配） */
    private static final String[] FLAGSHIP_SOC_HINTS = {
            "sm8650", "sm8550", "sm8475", "sm8450", "sm7475",          // 骁龙 8 系/7s+
            "kalama", "pineapple", "sun", "crow", "taro", "lahaina",   // 骁龙板卡代号
            "mt6989", "mt6991", "mt6993", "mt6985",                    // 天玑 9 系
            "exynos2400", "exynos2500", "tensor g3", "tensor g4"
    };
    private static final String[] MID_SOC_HINTS = {
            "sm6450", "sm7450", "sm7675", "sdm778", "sm7325",
            "mt6893", "mt6980", "exynos1480", "tensor g2"
    };

    private static int socScore() {
        String soc = null;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                soc = (Build.VERSION.SOC_MANUFACTURER + " " + Build.VERSION.SOC_MODEL)
                        .toLowerCase(Locale.ROOT);
            } catch (Throwable ignored) {
                soc = null; // 个别 ROM 字段异常
            }
        }
        if (soc == null || soc.trim().isEmpty() || "unknown unknown".equals(soc.trim())) {
            soc = String.valueOf(Build.HARDWARE).toLowerCase(Locale.ROOT);
        }
        for (String hint : FLAGSHIP_SOC_HINTS) {
            if (soc.contains(hint)) {
                return 3;
            }
        }
        for (String hint : MID_SOC_HINTS) {
            if (soc.contains(hint)) {
                return 1;
            }
        }
        return 0;
    }

    // ───────────────────────── 电量状态读取 ─────────────────────────

    /**
     * 电量四态（BatteryManager 粘性广播，零注册开销）：
     * 充电中/已满 → CHARGING；未充电时 ≥50% → NORMAL；20%~50% → LOW；&lt;20% → CRITICAL。
     */
    public static BatteryState readBattery(Context context) {
        if (context == null) {
            return BatteryState.NORMAL;
        }
        Intent sticky = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky == null) {
            return BatteryState.NORMAL;
        }
        int status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        if (charging && plugged) {
            return BatteryState.CHARGING;
        }
        int level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return BatteryState.NORMAL;
        }
        int pct = level * 100 / scale;
        if (pct < 20) {
            return BatteryState.CRITICAL;
        }
        if (pct < 50) {
            return BatteryState.LOW;
        }
        return BatteryState.NORMAL;
    }

    /** 电量变化钩子（Service 注册 ACTION_BATTERY_CHANGED 动态接收器后调用） */
    public void onBatteryChanged() {
        BatteryState next = readBattery(appContext);
        if (next != battery) {
            battery = next;
            refreshPolicy();
        }
    }

    private void refreshPolicy() {
        current = MATRIX[tier.ordinal()][battery.ordinal()];
    }

    // ───────────────────────── 策略出口 ─────────────────────────

    /**
     * 当前策略（16 档矩阵取值）。红线通道字段恒为 true（见 PowerPolicy 定义处
     * 红线注释）；调用方在红线场景（SDK 窗/弹窗窗/学习指纹）应取
     * {@link #redlinePolicy()} 而非本方法，保证极低档下限不低于 POLICY_FLOOR。
     */
    public PowerPolicy currentPolicy() {
        PowerPolicy p = current;
        return p != null ? p : POLICY_FLOOR;
    }

    /**
     * 红线场景策略：取当前档与 POLICY_FLOOR 的较宽者（逐字段取优），
     * 确保指纹/弹窗/预拦截三条通道在任何电量档位下不降级。
     */
    public PowerPolicy redlinePolicy() {
        PowerPolicy cur = currentPolicy();
        return new PowerPolicy(
                Math.min(cur.scanIntervalMs, POLICY_FLOOR.scanIntervalMs),
                cur.retryChainMs.length >= POLICY_FLOOR.retryChainMs.length
                        ? cur.retryChainMs : POLICY_FLOOR.retryChainMs,
                Math.max(cur.maxDfsNodes, POLICY_FLOOR.maxDfsNodes),
                Math.max(cur.maxDfsDepth, POLICY_FLOOR.maxDfsDepth),
                Math.max(cur.popupWindowMs, POLICY_FLOOR.popupWindowMs),
                true, // 学习开关对红线通道不生效（指纹通道本身零成本）
                Math.max(cur.contentEventBudgetPerMin, POLICY_FLOOR.contentEventBudgetPerMin),
                Math.max(cur.burstPopupQuota, POLICY_FLOOR.burstPopupQuota));
    }

    /** 矩阵直取（CI 校验 16 档矩阵数值用，与文档表格逐格对齐） */
    public static PowerPolicy matrixPolicy(DeviceTier tier, BatteryState battery) {
        return MATRIX[tier.ordinal()][battery.ordinal()];
    }

    public DeviceTier tier() {
        return tier;
    }

    public BatteryState battery() {
        return battery;
    }
}
