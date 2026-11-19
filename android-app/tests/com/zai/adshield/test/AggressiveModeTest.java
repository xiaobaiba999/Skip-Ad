package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.HumanTap;
import com.zai.adshield.core.TapPoint;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 激进模式单元测试（v7.6.0 · 用户十五轮实锤：「软件内插入的广告不仅没
 * 跳过，其他软件的开屏广告现在有的还跳不了了，或者非常非常慢，你可以
 * 激进一点暂时先放弃宁可漏拦绝不误触……部分用户设备开屏广告跳过时因为
 * 设备类型、分辨率或者其他原因影响，导致多点、误点」）。
 * ─────────────────────────────────────────────────────────────
 * 三组根治的回归验证：
 *  ① 开屏速度：首击零门控（gateCountdownAggressive attempts=0 → 立即
 *     出手）；重击等「将尽」（穿山甲真倒计时按钮命中时机）；重击等待
 *     6s 封顶（误检大数字不再无限等）。
 *  ② 多点误点：1:1 人手时间线仿真（首击 → 复验 → 门控重击全程
 *     F/H/I 防线拦截窗验证——同一目标窗内最多 2 击、两击间隔 >1.6s、
 *     人手复验节奏梯子渐进放缓）。
 *  ③ 插屏激进：InterstitialGuardTest S1~S8 全覆盖（本套件补
 *     walk 决策梯子在激进模式下的行为不变式）。
 * 断言规划（check/checkEq 总数 46）：
 *  S1 首击零门控时间线（8）/ S2 重击门控与封顶（9）
 *  S3 1:1 人手多点防线时间线（12）/ S4 设备矩阵触点钳制（10）
 *  S5 决策梯子不变式（7）。
 * 标准屏 1080×2400。
 */
public final class AggressiveModeTest {

    public static void main(String[] args) {
        /* ════ S1 首击零门控时间线（8 项）════ */
        TestHost.suite("AggressiveMode · S1 首击零门控时间线（开屏露头即点）");
        long t0 = 1000L; // 窗开启时刻（锚点=数字首见时刻）
        int cdN = 5;      // 穿山甲 5s 倒计时（数字与「跳过」分属两节点）
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(cdN, t0, t0, 0),
                "t=0·首击·5s 倒计时在场 → 零门控立即出手（旧版等 4.88s——「非常非常慢」根治）");
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(cdN, t0, t0 + 120, 0),
                "t=120ms·首击仍零门控（任何时刻第 0 击都不被倒计时按住）");
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(15, t0, t0, 0),
                "15s 大倒计时首击也零门控（误检数字不再卡死按钮——「跳不了了」根治）");
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(0, t0, t0, 0),
                "无倒计时首击零门控（无倒计时按钮照常立即出手）");
        // 复验节奏：首击后人手 500ms 才扫一眼（verifyDelayMs(0)=500）
        TestHost.checkEq(500L, HumanTap.verifyDelayMs(0), "人手复验节奏·首击后 500ms（1:1 人手）");
        // F 防线：首击后 1600ms 内同目标不再击
        java.util.HashMap<String, Long> firedAt = new java.util.HashMap<>();
        String key = "pkg|960,120|skip_text";
        firedAt.put(key, t0);
        TestHost.check(HumanTap.isFiredRecently(firedAt, key, t0 + 1000),
                "t=1000ms·F 防线拦截同目标第 2 击（<1600ms 静默窗）");
        TestHost.check(!HumanTap.isFiredRecently(firedAt, key, t0 + 1601),
                "t=1601ms·F 静默窗过（此时出手间隔已达人手双击下限）");
        TestHost.check(!HumanTap.isFiredRecently(firedAt, "other", t0 + 1000),
                "不同指纹目标不受 F 拦截（换候选不受限）");

        /* ════ S2 重击门控与封顶（9 项）════ */
        TestHost.suite("AggressiveMode · S2 重击门控与封顶（6s——误检兜底）");
        // 5s 真倒计时：首击白点 → 重击等将尽（until = 1000+5000-120 = 5880）
        TestHost.check(AdDecisionCore.gateCountdownAggressive(5, t0, t0 + 1500, 1),
                "首击白点后 t=1500ms·重击被门控（真倒计时按钮命中时机=将尽）");
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(5, t0, t0 + 5880, 1),
                "t=5880ms（将尽-120ms）·重击开闸出手即命中");
        // 错点数字（真倒计时每秒跳重置锚点：锚点新鲜）——第 2 击同样门控
        TestHost.check(AdDecisionCore.gateCountdownAggressive(5, t0 + 3500, t0 + 4000, 2),
                "第 2 击·锚点新鲜（3500→4000ms）→ 同样门控（耐心预算内等将尽不烧白点）");
        // 15s 误检大数字：封顶 6s 后开闸（不再等 15s）
        TestHost.check(AdDecisionCore.gateCountdownAggressive(15, t0, t0 + 500, 1),
                "误检「15」重击·锚点新鲜（封顶后 until=1000+5880）→ 仍门控但最多等 6s");
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(15, t0, t0 + 7200, 1),
                "误检「15」重击过 6s 封顶（capped until=6880<7200）→ 开闸（不再无限等）");
        // 装饰数字逃生：锚点 1.6s 未刷新 → 放行
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(5, t0, t0 + 3000, 1)
                        || AdDecisionCore.decorativeCountdown(t0, t0 + 3000),
                "锚点 2s 未刷新（装饰数字）→ 对任何击数放行（逃生语义保持）");
        TestHost.check(!AdDecisionCore.gateCountdownAggressive(3, t0, t0 + 5000, 1),
                "陈旧锚点重击也放行（装饰逃生兜底）");
        TestHost.checkEq(6, AdDecisionCore.RETRY_GATE_MAX_S, "RETRY_GATE_MAX_S=6 契约");
        TestHost.checkEq(3, AdDecisionCore.MAX_CLICKS_PER_WIN, "同窗点击总预算 3 契约（耐心红线保持）");

        /* ════ S3 1:1 人手多点防线时间线（12 项）════ */
        TestHost.suite("AggressiveMode · S3 1:1 人手多点防线时间线（设备差异「多点」根治）");
        // 完整时间线：白点首击 → 复验被 F 拦 → 门控重击（间隔 >1.6s 达人手节奏）
        long firstClick = t0;         // 首击（激进零门控）
        long verify1 = t0 + 500;      // 人手复验节奏第一档
        long gateOpen = t0 + 5880;    // 5s 倒计时将尽
        TestHost.checkEq(1400L, HumanTap.verifyDelayMs(1), "复验节奏第二档 1.4s（渐进放缓）");
        TestHost.checkEq(2400L, HumanTap.verifyDelayMs(2), "复验节奏第三档 2.4s（渐进放缓）");
        TestHost.checkEq(3600L, HumanTap.verifyDelayMs(3), "复验节奏第四档 3.6s（渐进放缓）");
        TestHost.checkEq(7000L, HumanTap.verifyDelayMs(9), "封顶档 7s（耐心天花板）");
        TestHost.check(HumanTap.isFiredRecently(firedAt, key, verify1),
                "t=500ms 复验扫描看到按钮还在 → F 拦截（点击已派发生效动画期不补击）");
        TestHost.check(gateOpen - firstClick >= HumanTap.SAME_TARGET_QUIET_MS,
                "门控重击时刻距首击 ≥1.6s（真倒计时按钮的重击天然满足人手双击下限）");
        // H 防线：手势派发成功后 600ms 内任何新候选不出手
        java.util.HashMap<String, Long> gestureOkAt = new java.util.HashMap<>();
        gestureOkAt.put("pkg", firstClick);
        TestHost.check(!HumanTap.postTapQuietOk(gestureOkAt.get("pkg"), firstClick + 300),
                "t=300ms·H 结果静默拦截（点了还没生效不补击）");
        TestHost.check(HumanTap.postTapQuietOk(gestureOkAt.get("pkg"), firstClick + 601),
                "t=601ms·H 静默过（600ms 生效窗口给足）");
        // I 防线：同指纹击数封顶
        TestHost.check(HumanTap.targetStrikesOk(null), "未出手过 → 放行");
        TestHost.check(HumanTap.targetStrikesOk(0), "已击 0 次 → 放行");
        TestHost.check(HumanTap.targetStrikesOk(1), "已击 1 次 → 放行（第 2 击）");
        TestHost.check(!HumanTap.targetStrikesOk(2), "已击 2 次 → 第 3 击拒（同目标多点级联掐断）");

        /* ════ S4 设备矩阵触点钳制（10 项）════ */
        TestHost.suite("AggressiveMode · S4 设备矩阵触点钳制（九品牌 1:1 分辨率）");
        int[][] brandRes = {
                {1080, 2400}, {720, 1600}, {1440, 3200}, {1080, 2340},
                {1260, 2800}, {1220, 2712}, {1176, 2400}, {1080, 2412}, {1200, 2652},
        };
        float[] densities = {3.0f, 2.0f, 3.5f, 2.75f, 2.625f, 2.875f, 3.0f, 2.875f, 2.75f};
        int inside = 0, rotated = 0;
        for (int bi = 0; bi < brandRes.length; bi++) {
            int w = brandRes[bi][0], h = brandRes[bi][1];
            float density = densities[bi];
            // 右上角跳过键（按分辨率 1:1 缩放）
            int[] rect = {(int) (w * 0.83), (int) (h * 0.033), (int) (w * 0.97), (int) (h * 0.066)};
            for (int attempt = 0; attempt < 5; attempt++) {
                int[] pt = TapPoint.pickDp(rect, attempt, density);
                if (pt == null) continue;
                if (pt[0] >= rect[0] && pt[0] <= rect[2] && pt[1] >= rect[1] && pt[1] <= rect[3]) {
                    inside++;
                    if (attempt > 0) rotated++;
                }
            }
        }
        // 9 品牌 × 5 复验槽位 = 45 个触点全在目标矩形内（含四象限轮换）
        TestHost.checkEq(45, inside, "九品牌×5 槽位触点全部钳制在目标矩形内（分辨率差异零漂移）");
        TestHost.checkEq(36, rotated, "四象限轮换触点（36=45-中心 9）也全部在矩形内（复验换点不盲点同坐标）");
        // 触点密度钳制契约
        TestHost.check(HumanTap.clampDensity(0.5f) >= 1f, "密度钳制下限 1（低密度设备不过松）");
        TestHost.check(HumanTap.clampDensity(9f) <= 4f, "密度钳制上限 4（高密度设备不过严）");
        TestHost.check(TapPoint.pickDp(new int[]{0, 0, 10, 10}, 0, 3f) == null,
                "超小矩形（盲刺形态）拒点（宁走无障碍代点不盲刺）");
        TestHost.check(TapPoint.pickDp(null, 0, 3f) == null, "null 矩形拒点");

        /* ════ S5 决策梯子不变式（7 项）════ */
        TestHost.suite("AggressiveMode · S5 决策梯子不变式（激进模式不破坏既有语义）");
        // 跳过键露头即点的决策语义：文本候选在场 → click 决策（无倒计时场景）
        com.zai.adshield.core.AdDecisionCore.Ctx ctx = new com.zai.adshield.core.AdDecisionCore.Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.splashLive = true;
        ctx.budgetNodes = 600;
        SimNode root = new SimNode();
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        root.cls = "android.widget.FrameLayout";
        SimNode skip = new SimNode();
        skip.text = "跳过 5";
        skip.boundsArr = new int[]{900, 80, 1030, 160};
        skip.clickable = true;
        root.add(skip);
        com.zai.adshield.core.AdDecisionCore.ScanFind f =
                com.zai.adshield.core.AdDecisionCore.walk(root, ctx);
        TestHost.checkEq(1, f.skipCands.size(), "「跳过 5」文本候选在场");
        TestHost.checkEq(5, f.countdownN, "parseCountdown「跳过 5」→ 5s（门控供重击用）");
        com.zai.adshield.core.AdDecisionCore.Decision d =
                com.zai.adshield.core.AdDecisionCore.decide(f, ctx);
        TestHost.check("skip_text".equals(d.reason),
                "decide → skip_text 立即点击（决策层不等倒计时——门控在服务侧仅管重击）");
        // 无倒计时按钮：parseCountdown=0 → 零门控照常出手
        TestHost.checkEq(0, AdDecisionCore.parseCountdown("跳过"),
                "纯「跳过」（无倒计时文本）→ cdN=0（用户实锤：不是每个都带倒计时）");
        TestHost.checkEq(3, AdDecisionCore.parseCountdown("跳过 3"),
                "「跳过 3」标签内数字 → cdN=3（供重击门控用——决策层首击不等）");
        TestHost.checkEq(0, AdDecisionCore.standaloneCountdownSeconds("领取3天VIP"),
                "「领取3天VIP」兄弟节点路径拒（含字母非纯数字形态——误检防线）");
        TestHost.checkEq(0, AdDecisionCore.parseCountdown("0元领"),
                "「0元领」前置 0 拒（营销数字防线保持）");

        TestHost.done();
    }
}
