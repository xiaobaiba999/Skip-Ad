package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.HumanTap;
import com.zai.adshield.core.TapPoint;
import com.zai.adshield.test.TestHost.SimNode;

import java.util.ArrayList;

/**
 * v6.0.0 1:1 人手模拟引擎矩阵测试（用户实锤：部分用户设备开屏广告跳过
 * 时因为设备类型、分辨率或者其他原因影响，导致多点、误点的情况，体验
 * 非常不好——1 比 1 模拟人在不同设备上操作使用卫士，解决这个 bug）。
 *
 * <p>矩阵：24 类真实设备几何（与 TapPointMatrixTest 同源——直板 720p~
 * 1440p · 16:9 遗留 · 折叠内外屏 · 平板 3:2/4:3 · 分屏半窗 · 刘海/挖孔 ·
 * 超宽 QHD · 正方 · 21:9 · 横屏）× 7 档设备密度（1.0/1.5/2.0/2.6/3.0/
 * 3.5/4.0——ldpi~xxxhdpi 全域）。人手引擎五要素在任意组合上全部成立：</p>
 *
 * <ol>
 * <li>S1 人手域参数：tapSpec 起始延迟 26~56ms · 首击时长 95~130ms ·
 *   重试时长 115~160ms（更用力）· ±2px 微抖——200 种子 × 6 attempt 全域内；
 *   时长方差 ≥10 档（反机器人指纹：千次一模一样签名会被广告 SDK 无声吞掉
 *   =「点了没反应」→复验→「多点」级联的根因）；同种子同参数（可复现）；</li>
 * <li>S2 抖动不出界：pickDp 落点 ±2px 抖动后仍在标签可见域内（任意几何 ×
 *   密度 × 轮换档——安全内缩 ≥3px 恒大于抖动 2px，绝不出目标面）；</li>
 * <li>S3 人手复验节奏：首击 500ms 单针（替代 450/900 双针机器枪），二击后
 *   1.4s/2.4s/3.6s/5.2s/7.0s 渐进放缓（人会越等越慢），封顶 7s 单调不降；</li>
 * <li>S4 轨迹去重：同坐标 ±8dp（密度缩放）邻域内绝不重复盲刺——五轮换位
 *   全部落在小按钮 ±8dp 内 → 整候选拒点换下一候选（同坐标机器枪根治）；</li>
 * <li>S5 dp 密度归一：安全域常量按设备密度缩放（160dpi 与 480dpi 行为一致
 *   ——「设备类型、分辨率原因」的触点漂移根治）；钳制域 [1,4] 全防线；</li>
 * <li>S6 全链路落点：标准开屏树 walk→decide 出手 → tapRect+pickDp（密度
 *   缩放）→ 落点在跳过键可见域内且距边 ≥ 密度内缩，±2px 抖动仍在域内
 *   ——1:1 人手模拟在任意设备几何上的终极断言；</li>
 * <li>S7 人手耐心预算：MAX_CLICKS_PER_WIN == HUMAN_PATIENCE == 6（人的耐心
 *   ——6 下没关优雅收手等倒计时，绝不 12 连点机器枪）。</li>
 * </ol>
 */
public final class HumanTapMatrixTest {

    /* ── 24 类设备几何（与 TapPointMatrixTest 同源）── */
    static final String[][] GEOM = {
            {"720p 直板 20:9", "720", "1650"},
            {"1080p 直板 20:9", "1080", "2400"},
            {"1080p 直板 20:9 高", "1080", "2412"},
            {"1220p 直板 20:9", "1220", "2712"},
            {"1440p 直板 20:9", "1440", "3200"},
            {"1260p 直板 19.5:9", "1260", "2844"},
            {"16:9 遗留直板", "1080", "1920"},
            {"16:9 低端遗留", "720", "1280"},
            {"折叠外屏窄 21.3:9", "1080", "2316"},
            {"折叠外屏超窄 22.5:9", "720", "1632"},
            {"折叠内屏近方 1:1.2", "1812", "2176"},
            {"折叠内屏 1.11:1", "2208", "2480"},
            {"折叠内屏大 1.15:1", "1872", "2148"},
            {"平板 16:10", "1600", "2560"},
            {"平板 3:2 偏长", "1752", "2800"},
            {"平板大 4:3", "2048", "2732"},
            {"分屏半窗竖", "1080", "1200"},
            {"分屏半窗横", "540", "2400"},
            {"刘海大缺 19:9", "1080", "2280"},
            {"挖孔屏 20:9", "1080", "2340"},
            {"超宽 QHD 20:9", "1344", "2992"},
            {"正方形态", "1080", "1080"},
            {"超长 21:9", "1080", "2520"},
            {"横屏形态", "2400", "1080"},
    };

    /* ── 7 档设备密度（ldpi 1.0 ~ xxxhdpi 4.0 全域）── */
    static final float[] DENSITIES = {1.0f, 1.5f, 2.0f, 2.6f, 3.0f, 3.5f, 4.0f};

    public static void main(String[] args) {
        s1_tapSpecDomain();
        s2_jitterContainment();
        s3_retryCadence();
        s4_dedup();
        s5_densityNormalization();
        s6_fullChainLanding();
        s7_patience();
        TestHost.done();
    }

    /* ══ S1：人手域参数（200 种子 × 6 attempt 全域内 + 方差 + 确定性）══ */
    static void s1_tapSpecDomain() {
        TestHost.suite("S1 人手域参数（tapSpec）");
        boolean allInRange = true;
        boolean determinism = true;
        for (long seed = 0; seed < 200; seed++) {
            for (int attempt = 0; attempt < 6; attempt++) {
                int[] s = HumanTap.tapSpec(seed * 0x9E3779B9L + attempt, attempt);
                boolean delayOk = s[0] >= HumanTap.DELAY_MIN
                        && s[0] < HumanTap.DELAY_MIN + HumanTap.DELAY_SPAN;
                int durMin = attempt <= 0 ? HumanTap.DUR_FIRST_MIN : HumanTap.DUR_RETRY_MIN;
                int durSpan = attempt <= 0 ? HumanTap.DUR_FIRST_SPAN : HumanTap.DUR_RETRY_SPAN;
                boolean durOk = s[1] >= durMin && s[1] < durMin + durSpan;
                boolean jitterOk = Math.abs(s[2]) <= HumanTap.JITTER_ABS
                        && Math.abs(s[3]) <= HumanTap.JITTER_ABS;
                boolean humanDur = s[1] >= 80 && s[1] <= 170; // 真人触点时长域（80~170ms）
                if (!delayOk || !durOk || !jitterOk || !humanDur) {
                    allInRange = false;
                }
                // 确定性：同种子同 attempt → 完全相同参数（测试可复现）
                int[] again = HumanTap.tapSpec(seed * 0x9E3779B9L + attempt, attempt);
                if (again[0] != s[0] || again[1] != s[1]
                        || again[2] != s[2] || again[3] != s[3]) {
                    determinism = false;
                }
            }
        }
        TestHost.check(allInRange, "S1a 全域内（200 种子 × 6 attempt：延迟/时长/抖动全在人手域）");
        TestHost.check(determinism, "S1b 确定性（同种子同 attempt 参数完全一致——可复现）");

        // 时长方差 ≥10 档（反机器人指纹核心：千次一模一样=被吞，方差=真人）
        java.util.TreeSet<Integer> firstDurs = new java.util.TreeSet<Integer>();
        java.util.TreeSet<Integer> retryDurs = new java.util.TreeSet<Integer>();
        for (long seed = 0; seed < 120; seed++) {
            firstDurs.add(HumanTap.tapSpec(seed + 42L, 0)[1]);
            retryDurs.add(HumanTap.tapSpec(seed + 42L, 3)[1]);
        }
        TestHost.check(firstDurs.size() >= 10 && retryDurs.size() >= 10,
                "S1c 时长方差（首击 " + firstDurs.size() + " 档 · 重试 " + retryDurs.size()
                        + " 档——反机器人指纹）");
        // 重试更用力：重试域时长中位数 > 首击域
        long firstSum = 0, retrySum = 0;
        for (long seed = 0; seed < 120; seed++) {
            firstSum += HumanTap.tapSpec(seed + 7L, 0)[1];
            retrySum += HumanTap.tapSpec(seed + 7L, 1)[1];
        }
        TestHost.check(retrySum > firstSum, "S1d 重试更用力（重试均值 " + (retrySum / 120)
                + "ms > 首击均值 " + (firstSum / 120) + "ms——人手行为）");
        // 抖动散布：±2px 两个轴都出现非零值（真人落点散布）
        int jxNonZero = 0, jyNonZero = 0;
        for (long seed = 0; seed < 200; seed++) {
            int[] s = HumanTap.tapSpec(seed + 99L, 1);
            if (s[2] != 0) jxNonZero++;
            if (s[3] != 0) jyNonZero++;
        }
        TestHost.check(jxNonZero > 50 && jyNonZero > 50,
                "S1e 抖动散布（jx 非零 " + jxNonZero + "/200 · jy 非零 " + jyNonZero + "/200）");
    }

    /* ══ S2：抖动不出界（真实密度配对必须可刺 + 全 7 档压力条件契约）══ */
    static void s2_jitterContainment() {
        TestHost.suite("S2 抖动不出界（24 几何 × 真实密度 × 5 轮换 + 7 档压力）");
        boolean allSafe = true;
        int combos = 0;
        for (String[] g : GEOM) {
            int w = Integer.parseInt(g[1]);
            int h = Integer.parseInt(g[2]);
            // 典型跳过键（右上 11%×3.5%）+ 居中大按钮（两种形态）
            int[][] rects = {
                    {(int) (w * 0.86), (int) (h * 0.03), (int) (w * 0.97), (int) (h * 0.065)},
                    {(int) (w * 0.30), (int) (h * 0.80), (int) (w * 0.70), (int) (h * 0.88)},
            };
            /* ① 真实密度配对（1:1 模拟「人在不同设备上」）：几何档 × 该档
             * 设备真实出货密度——720p 遗留机 1.0~2.0、QHD 旗舰 3.5~4.0。
             * 真实组合上典型跳过键【必须可刺】且 ±2px 九向抖动恒在域内
             *（安全内缩 ≥ 抖动 2px——绝不出目标面）。 */
            for (float density : TestHost.realDensities(w, h)) {
                for (int[] rect : rects) {
                    for (int att = 0; att < TapPoint.ROTATION_SLOTS; att++) {
                        int[] pt = TapPoint.pickDp(rect, att, density);
                        if (pt == null) {
                            allSafe = false; // 真实设备组合上必须可刺
                            continue;
                        }
                        if (!jitterInside(rect, pt)) allSafe = false;
                        combos++;
                    }
                }
            }
            /* ② 全 7 档压力条件契约（含真实世界不存在的组合——720p×xxxhdpi）：
             * 物理亚指腹（dp 短边 < 12dp 盲刺阈）→ 必须【拒点】null（盲刺
             * 拒绝=防误触正确行为，与 S5d 契约一致）；可刺 → 落点+抖动必须安全。 */
            for (float density : DENSITIES) {
                for (int[] rect : rects) {
                    boolean tappable = rect[2] - rect[0] >= HumanTap.minSidePx(density)
                            && rect[3] - rect[1] >= HumanTap.minSidePx(density);
                    for (int att = 0; att < TapPoint.ROTATION_SLOTS; att++) {
                        int[] pt = TapPoint.pickDp(rect, att, density);
                        if (!tappable) {
                            if (pt != null) allSafe = false; // 亚指腹必须拒点
                            continue;
                        }
                        if (pt == null) {
                            allSafe = false; // 可刺必须出点
                            continue;
                        }
                        if (!jitterInside(rect, pt)) allSafe = false;
                    }
                }
            }
        }
        TestHost.check(allSafe, "S2 抖动不出界（真实配对 " + combos + " 组合 ±2px 九向抖动全在域内 · 7 档压力条件契约全绿）");
    }

    /** ±2px 九向抖动恒在 rect 域内（安全内缩 ≥ 抖动——绝不出目标面） */
    static boolean jitterInside(int[] rect, int[] pt) {
        for (int jx = -HumanTap.JITTER_ABS; jx <= HumanTap.JITTER_ABS; jx++) {
            for (int jy = -HumanTap.JITTER_ABS; jy <= HumanTap.JITTER_ABS; jy++) {
                int fx = pt[0] + jx, fy = pt[1] + jy;
                if (fx < rect[0] || fx > rect[2] || fy < rect[1] || fy > rect[3]) return false;
            }
        }
        return true;
    }

    /* ══ S3：人手复验节奏（首击 500ms 单针 · 渐进放缓 · 单调封顶）══ */
    static void s3_retryCadence() {
        TestHost.suite("S3 人手复验节奏（verifyDelayMs）");
        boolean cadence = HumanTap.verifyDelayMs(0) == 500L
                && HumanTap.verifyDelayMs(1) == 1400L
                && HumanTap.verifyDelayMs(2) == 2400L
                && HumanTap.verifyDelayMs(3) == 3600L
                && HumanTap.verifyDelayMs(4) == 5200L
                && HumanTap.verifyDelayMs(5) == 7000L
                && HumanTap.verifyDelayMs(6) == 7000L
                && HumanTap.verifyDelayMs(99) == 7000L
                && HumanTap.verifyDelayMs(-3) == 500L;
        TestHost.check(cadence, "S3a 节奏阶梯（500 / 1400 / 2400 / 3600 / 5200 / 7000 封顶）");
        boolean monotonic = true;
        long prev = 0;
        for (int attempt = 0; attempt < 12; attempt++) {
            long d = HumanTap.verifyDelayMs(attempt);
            if (d < prev) monotonic = false;
            prev = d;
        }
        TestHost.check(monotonic, "S3b 单调不降（人会越等越慢，绝不倒退）");
        boolean noMachineGun = HumanTap.verifyDelayMs(0) >= 400L && HumanTap.verifyDelayMs(1) >= 1200L;
        TestHost.check(noMachineGun, "S3c 非机器枪（首验 ≥400ms · 二击后 ≥1.2s——450/900 双针连点观感根治）");
    }

    /* ══ S4：轨迹去重（同坐标 ±8dp 绝不重复盲刺）══ */
    static void s4_dedup() {
        TestHost.suite("S4 触点轨迹去重（isDupPoint）");
        ArrayList<int[]> trail = new ArrayList<int[]>();
        trail.add(new int[]{500, 300});
        boolean hit = HumanTap.isDupPoint(trail, 500, 300, 8);
        boolean nearHit = HumanTap.isDupPoint(trail, 505, 296, 8);
        boolean farMiss = !HumanTap.isDupPoint(trail, 520, 300, 8);
        boolean fartherMiss = !HumanTap.isDupPoint(trail, 500, 320, 8);
        boolean emptySafe = !HumanTap.isDupPoint(null, 500, 300, 8)
                && !HumanTap.isDupPoint(new ArrayList<int[]>(), 500, 300, 8);
        TestHost.check(hit && nearHit && farMiss && fartherMiss && emptySafe,
                "S4a 去重判定（±8dp 邻域命中 · 超界放行 · 空轨迹安全）");

        // S4b 密度缩放容差：同 20dp 距离，密度 2 时 8dp→16px 判重，密度 1 时 8px 判新
        ArrayList<int[]> single = new ArrayList<int[]>();
        single.add(new int[]{500, 300});
        boolean denseDup = HumanTap.isDupPoint(single, 512, 300, HumanTap.dupTolPx(2.0f));
        boolean sparseNew = !HumanTap.isDupPoint(single, 512, 300, HumanTap.dupTolPx(1.0f));
        TestHost.check(denseDup && sparseNew, "S4b 密度缩放容差（dupTolPx(2.0)=" + HumanTap.dupTolPx(2.0f)
                + "px 判重 · dupTolPx(1.0)=" + HumanTap.dupTolPx(1.0f) + "px 判新——dp 归一）");

        // S4c 小键轮换收敛（v6.0.0 修正算术）：典型小跳过键 79×58 @density2.0，
        // 四象限中心距几何中心 (w/4, h/4)=(19, 14)px——Chebyshev 距 19px >
        // dupTolPx(2.0)=16px → 五档互为独立可刺点（并非同坐标邻域）。
        // 引擎三重契约：①同坐标 ±8dp 邻域绝不重复盲刺（轨迹去重）；②独立
        // 可刺点封顶 ROTATION_SLOTS=5（轮换有界）；③轮换耗尽（attempt≥5
        // 槽位回卷全部落入已刺邻域）→ 服务侧 adaptiveTapPoint 拒点 null →
        // 换候选/降级无障碍代点——「绝不原地连点」的最终防线。
        int[] smallRect = {619, 49, 698, 107}; // 720p 跳过键 79×58 @density 2.0
        ArrayList<int[]> smallTrail = new ArrayList<int[]>();
        boolean neverDup = true;
        for (int att = 0; att < TapPoint.ROTATION_SLOTS; att++) {
            int[] pt = TapPoint.pickDp(smallRect, att, 2.0f);
            if (pt == null) continue;
            if (HumanTap.isDupPoint(smallTrail, pt[0], pt[1], HumanTap.dupTolPx(2.0f))) {
                neverDup = false; // 采用前必不在已刺邻域（轨迹去重生效）
            } else {
                smallTrail.add(pt);
            }
        }
        // 轮换耗尽：attempt 5..9（槽位回卷 0,1,2,3,4）全部落入已刺邻域
        boolean exhaustedDup = true;
        for (int k = 0; k < TapPoint.ROTATION_SLOTS; k++) {
            int[] pt = TapPoint.pickDp(smallRect, 5 + k, 2.0f);
            if (pt == null || !HumanTap.isDupPoint(smallTrail, pt[0], pt[1], HumanTap.dupTolPx(2.0f))) {
                exhaustedDup = false; // 耗尽后不得再给出邻域外新点
            }
        }
        TestHost.check(neverDup && exhaustedDup && smallTrail.size() >= 1
                        && smallTrail.size() <= TapPoint.ROTATION_SLOTS,
                "S4c 小键轮换收敛（" + smallTrail.size() + " 个独立可刺点 ≤" + TapPoint.ROTATION_SLOTS
                        + " 档封顶 · 同坐标绝不重复 · 轮换耗尽全判重→拒点换候选，绝不原地连点）");
    }

    /* ══ S5：dp 密度归一（安全域常量按设备密度缩放 + 钳制域防线）══ */
    static void s5_densityNormalization() {
        TestHost.suite("S5 dp 密度归一（minSidePx/edgeInsetPx/dupTolPx/clampDensity）");
        boolean scale = HumanTap.minSidePx(1.0f) == 12
                && HumanTap.minSidePx(1.5f) == 18
                && HumanTap.minSidePx(2.0f) == 24
                && HumanTap.minSidePx(2.6f) == 31
                && HumanTap.minSidePx(3.0f) == 36
                && HumanTap.minSidePx(3.5f) == 42
                && HumanTap.minSidePx(4.0f) == 48
                && HumanTap.edgeInsetPx(1.0f) == 6
                && HumanTap.edgeInsetPx(2.0f) == 12
                && HumanTap.edgeInsetPx(3.0f) == 18
                && HumanTap.edgeInsetPx(4.0f) == 24
                && HumanTap.dupTolPx(1.0f) == 8
                && HumanTap.dupTolPx(2.0f) == 16
                && HumanTap.dupTolPx(4.0f) == 32;
        TestHost.check(scale, "S5a 缩放表（12dp/6dp/8dp × 密度 1.0~4.0 全档）");
        boolean clamp = HumanTap.clampDensity(0.75f) == 1.0f
                && HumanTap.clampDensity(0f) == 1.0f
                && HumanTap.clampDensity(-1f) == 1.0f
                && HumanTap.clampDensity(Float.NaN) == 1.0f
                && HumanTap.clampDensity(1.0f) == 1.0f
                && HumanTap.clampDensity(2.75f) == 2.75f
                && HumanTap.clampDensity(4.0f) == 4.0f
                && HumanTap.clampDensity(5.5f) == 4.0f;
        TestHost.check(clamp, "S5b 钳制域（异常密度全兜底 [1,4]——ldpi 老机保守，xxxhdpi 封顶）");
        // S5c 密度兼容基线：density=1.0 时 pickDp ≡ pick（v5.9 行为完全保留）
        boolean compat = true;
        int[][] probe = {{0, 0, 120, 80}, {600, 40, 700, 100}, {10, 2000, 400, 2080}};
        for (int[] r : probe) {
            for (int att = 0; att < 5; att++) {
                int[] a = TapPoint.pick(r, att);
                int[] b = TapPoint.pickDp(r, att, 1.0f);
                if (a == null || b == null || a[0] != b[0] || a[1] != b[1]) compat = false;
            }
        }
        TestHost.check(compat, "S5c 兼容基线（density=1.0 时 pickDp ≡ pick——v5.9 行为零回归）");
        // S5d 高密度收紧：同小缝矩形，1.0 可点、4.0 拒点（安全域随密度收紧）
        int[] sliver = {100, 100, 130, 145}; // 30×45px：1.0 可点（≥12px）；4.0 拒（<48px）
        TestHost.check(TapPoint.pickDp(sliver, 0, 1.0f) != null
                        && TapPoint.pickDp(sliver, 0, 4.0f) == null,
                "S5d 高密度收紧（30px 缝：density1.0 可点 · density4.0 拒点——盲刺防线随密度收紧）");
    }

    /* ══ S6：全链路落点（真实密度配对必须可刺 + 7 档压力条件契约）══ */
    static void s6_fullChainLanding() {
        TestHost.suite("S6 全链路落点（24 几何 × 真实密度 · walk→decide→安全触点）");
        boolean allSafe = true;
        int decidedN = 0;
        for (String[] g : GEOM) {
            int w = Integer.parseInt(g[1]);
            int h = Integer.parseInt(g[2]);
            SimNode root = new SimNode();
            root.boundsArr = new int[]{0, 0, w, h};
            SimNode skipBtn = new SimNode("跳过", (int) (w * 0.86), (int) (h * 0.03),
                    (int) (w * 0.97), (int) (h * 0.065));
            skipBtn.clickable = true;
            root.add(skipBtn);
            Ctx ctx = new Ctx();
            ctx.screenW = w;
            ctx.screenH = h;
            ctx.pkg = "com.human.matrix";
            ctx.activityId = "com.human.matrix.SplashActivity";
            ctx.elapsedMs = 800;
            ctx.splashLive = true;
            ScanFind sf = AdDecisionCore.walk(root, ctx);
            Decision d = AdDecisionCore.decide(sf, ctx);
            if (AdDecisionCore.A_NONE.equals(d.action)) {
                allSafe = false; // 任意几何上「跳过」键必须命中决策
                continue;
            }
            decidedN++;
            /* 遍历密度域：① 真实配对（必须可刺+落点安全+位移追踪）
             * ② 7 档压力条件契约（可刺→必须安全；亚指腹→必须拒点） */
            java.util.ArrayList<Float> dens = new java.util.ArrayList<Float>();
            for (float rd : TestHost.realDensities(w, h)) dens.add(rd);
            for (float sd : DENSITIES) dens.add(sd);
            for (Float densityObj : dens) {
                float density = densityObj;
                int[] rect = TapPoint.tapRect(skipBtn, skipBtn.bounds(), new int[]{0, 0, w, h});
                if (rect == null) {
                    allSafe = false;
                    continue;
                }
                boolean tappable = rect[2] - rect[0] >= HumanTap.minSidePx(density)
                        && rect[3] - rect[1] >= HumanTap.minSidePx(density);
                int[] pt = TapPoint.pickDp(rect, 0, density);
                if (!tappable) {
                    if (pt != null) allSafe = false; // 亚指腹必须拒点（防误触）
                    continue;
                }
                if (pt == null) {
                    allSafe = false;
                    continue;
                }
                boolean inside = pt[0] >= rect[0] && pt[0] <= rect[2]
                        && pt[1] >= rect[1] && pt[1] <= rect[3];
                boolean jitterSafe = (pt[0] - rect[0]) >= HumanTap.JITTER_ABS
                        && (rect[2] - pt[0]) >= HumanTap.JITTER_ABS
                        && (pt[1] - rect[1]) >= HumanTap.JITTER_ABS
                        && (rect[3] - pt[1]) >= HumanTap.JITTER_ABS;
                boolean freshTrackSafe = true; // 人眼追踪：目标动画位移 10px 后触点仍随新 bounds
                int[] shifted = {rect[0] + 10, rect[1] + 6, rect[2] + 10, rect[3] + 6};
                int[] pt2 = TapPoint.pickDp(shifted, 0, density);
                if (pt2 == null || pt2[0] < shifted[0] || pt2[0] > shifted[2]
                        || pt2[1] < shifted[1] || pt2[1] > shifted[3]) {
                    freshTrackSafe = false;
                }
                if (!inside || !jitterSafe || !freshTrackSafe) {
                    allSafe = false;
                }
            }
        }
        TestHost.check(allSafe && decidedN == GEOM.length,
                "S6 全链路（" + decidedN + "/" + GEOM.length + " 几何全决策 · 真实配对+7 档压力条件契约落点安全 + 位移追踪安全）");
    }

    /* ══ S7：人手耐心预算（多点级联根治的预算侧）══ */
    static void s7_patience() {
        TestHost.suite("S7 人手耐心预算");
        TestHost.check(AdDecisionCore.MAX_CLICKS_PER_WIN == HumanTap.HUMAN_PATIENCE
                        && AdDecisionCore.MAX_CLICKS_PER_WIN == 6,
                "S7 耐心预算（MAX_CLICKS_PER_WIN == HUMAN_PATIENCE == 6——12 连点机器枪退役）");
        // 预算 × 节奏联合：6 次出手的时间跨度 ≥ 人手耐心（非机器枪）
        long span = 0;
        for (int attempt = 0; attempt < 6; attempt++) {
            span += HumanTap.verifyDelayMs(attempt);
        }
        TestHost.check(span >= 15000, "S7b 全程跨度（6 击复验总跨度 " + span + "ms ≥ 15s——人手节奏）");
    }
}
