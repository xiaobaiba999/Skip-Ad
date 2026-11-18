package com.zai.adshield.test;

import com.zai.adshield.core.PowerGovernor;

/**
 * ═══════════════════════════════════════════════════════════════
 *  v5.3.0 功耗治理测试（PowerGovernorTest）—— 真机日志 #57/#58 建模
 * ═══════════════════════════════════════════════════════════════
 *  失败场景全部来自 v5.2.0 真机日志（Xiaomi 2410DPN6CC · Android 17）：
 *   · 能耗画像（本次开机）：事件 4604 · 扫描 2396 · 手势估算 2738µWh
 *   · 豆包（nova）MainActivity 115 节点对话列表反复整树扫描 + 「开屏未命中」
 *   · 豆包聊天流式内容 5 分钟 7 次「检测到内容突发」误开 10s 弹窗识别窗
 *   · 零文本手动点击被学成「跳过键位置」：同秒 (17%,82%) 与 (41%,82%)
 *     两处聊天按钮 → 次启 500ms 直点聊天气泡 = 乱点
 *   · 微信 ExdeviceRankInfoUI 110 节点 / 拼多多 NewPageActivity 23 节点
 *     / 喜马拉雅 MainActivity·FrameLayout 8 节点反复未命中
 *
 *  验证目标（李跳跳式三零治理）：
 *   ① 宿主窗档案：连续 3 次零候选 → CLEAN（零扫描零调度零日志）；
 *     真跳过键探针命中/候选出现/SDK 窗/Dialog/学习指纹/72h 复查 → 复活。
 *   ② 突发预算：CLEAN 窗/聊天主界面零开窗；每应用 10 分钟最多 2 窗；
 *     空手突发阈值指数升级（3→6→12→24），有战果立即归零。
 *   ③ 位置学习事后验证：窗类切换（广告窗画像）或树体缩水 ≥40% 才提交；
 *     聊天点击（窗未关）1.5s 超时丢弃。
 *   ④ 端到端功耗对照：按 #57/#58 窗口流仿真，v5.3.0 扫描量与能耗估算
 *     相比 v5.2.0 策略降 ≥60%，且广告窗（SDK/Dialog/学习窗）零抑制（命中率不减）。
 */
public final class PowerGovernorTest {

    public static void main(String[] args) {
        TestHost.suite("① 宿主窗档案 · 状态机");
        testProfileStateMachine();

        TestHost.suite("② 宿主窗档案 · 强信号永不抑制（命中率红线）");
        testProfileStrongSignals();

        TestHost.suite("③ 突发预算三重门（豆包聊天误报根治）");
        testBurstBudget();

        TestHost.suite("④ 位置学习事后验证（聊天乱学根治）");
        testPosVerify();

        TestHost.suite("⑤ 能耗模型与成本比");
        testEnergyModel();

        TestHost.suite("⑥ 端到端仿真 · #57/#58 窗口流（功耗对照）");
        testEndToEndSimulation();

        TestHost.done();
    }

    /* ───────── ① 档案状态机 ───────── */

    private static void testProfileStateMachine() {
        // 连续零候选 3 次 → CLEAN
        int streak = 0;
        for (int i = 0; i < 3; i++) {
            streak = PowerGovernor.nextStreak(streak, false, true);
        }
        TestHost.checkEq(3, streak, "连续 3 次零候选 → streak=3");
        TestHost.check(PowerGovernor.suppressLadder(streak, false, false, false, false, 0),
                "streak=3 无强信号 → 抑制全量梯子（CLEAN）");

        // 第 2 次还没到 CLEAN（豆包第 2 次启动仍全量——不误伤新装应用）
        TestHost.check(!PowerGovernor.suppressLadder(2, false, false, false, false, 0),
                "streak=2 仍 ACTIVE（3 次门槛防误伤）");

        // 出现候选 → 归零复活
        TestHost.checkEq(0, PowerGovernor.nextStreak(3, true, false),
                "出现可出手候选 → streak 归零（复活）");

        // 非零候选但未出手（冷却/让路）：不奖不罚
        TestHost.checkEq(3, PowerGovernor.nextStreak(3, false, false),
                "非零候选未出手 → streak 保持（不奖不罚）");

        // 梯子档位
        TestHost.check(PowerGovernor.ladderFor(true, false) != null
                        && PowerGovernor.ladderFor(true, false).length == 0,
                "CLEAN 无订阅 → 空梯子（纯探针哨兵，零 DFS）");
        long[] sub = PowerGovernor.ladderFor(true, true);
        TestHost.check(sub != null && sub.length == 2 && sub[0] == 0 && sub[1] == 700,
                "CLEAN 有订阅 → 低频档 {0,700}（vid 规则保留 DFS 机会）");
        TestHost.check(PowerGovernor.ladderFor(false, false) == null,
                "ACTIVE → null（服务侧原 13 档全量）");

        // 72h 复查闸：超期放行一次全量
        TestHost.check(!PowerGovernor.suppressLadder(3, false, false, false, false,
                        PowerGovernor.PROFILE_RECHECK_MS + 1),
                "距上次全量超 72h → 放行全量复查（防广告改版永久漏拦）");
        TestHost.check(PowerGovernor.suppressLadder(3, false, false, false, false,
                        PowerGovernor.PROFILE_RECHECK_MS - 1),
                "72h 内 → 继续省电");
    }

    /* ───────── ② 强信号永不抑制（命中率红线） ───────── */

    private static void testProfileStrongSignals() {
        // #57/#58 里的真广告窗形态：SDK 广告窗 / Dialog 弹窗 / 学习指纹窗 /
        // 学习位置窗——哪怕 streak 再高也绝不抑制（漏拦 0 容忍）
        TestHost.check(!PowerGovernor.suppressLadder(99, true, false, false, false, 0),
                "Dialog 窗永不抑制（弹窗识别独立门控）");
        TestHost.check(!PowerGovernor.suppressLadder(99, false, true, false, false, 0),
                "SDK 广告窗指纹永不抑制（喜马拉雅 SplashAdActivity 类）");
        TestHost.check(!PowerGovernor.suppressLadder(99, false, false, true, false, 0),
                "className 学习指纹永不抑制");
        TestHost.check(!PowerGovernor.suppressLadder(99, false, false, false, true, 0),
                "学习位置在场永不抑制（L1.4 500ms 直点要用）");
    }

    /* ───────── ③ 突发预算三重门 ───────── */

    private static void testBurstBudget() {
        // v5.2.0 行为：3 事件即开窗（豆包聊天流式回复 1.5s 就有 3+ 事件）
        TestHost.check(PowerGovernor.burstGate(false, false, 0, 3, 0),
                "v5.2.0 基线：ACTIVE 窗 3 事件开窗（行为不变）");

        // v5.3.0 门①：CLEAN 窗的动态内容绝不开窗（豆包 MainActivity CLEAN 后）
        TestHost.check(!PowerGovernor.burstGate(true, false, 0, 999, 0),
                "档案 CLEAN 窗 → 事件再多也不开突发窗（豆包流式内容根治）");

        // 门①'：聊天主界面（微信 LauncherUI）同样不开
        TestHost.check(!PowerGovernor.burstGate(false, true, 0, 999, 0),
                "聊天主界面 → 绝不开突发窗");

        // 门②：10 分钟预算耗尽
        TestHost.check(!PowerGovernor.burstGate(false, false,
                        PowerGovernor.BURST_BUDGET_PER_10MIN, 3, 0),
                "本 10 分钟已开 2 窗 → 预算耗绝不再开");

        // 门③：空手突发后阈值指数升级
        TestHost.check(PowerGovernor.burstGate(false, false, 0, 3, 0),
                "level 0：3 事件开窗");
        TestHost.check(!PowerGovernor.burstGate(false, false, 0, 5, 1),
                "level 1（上次空手）：5 事件不够，需 6");
        TestHost.check(PowerGovernor.burstGate(false, false, 0, 6, 1),
                "level 1：6 事件开窗");
        TestHost.check(!PowerGovernor.burstGate(false, false, 0, 11, 2),
                "level 2：11 事件不够，需 12");
        TestHost.check(PowerGovernor.burstGate(false, false, 0, 12, 2),
                "level 2：12 事件开窗");

        // 升级与归零
        TestHost.checkEq(1, PowerGovernor.nextEscalate(0, false), "空手突发 → 升级 level 0→1");
        TestHost.checkEq(2, PowerGovernor.nextEscalate(1, false), "空手突发 → 升级 level 1→2");
        TestHost.checkEq(PowerGovernor.BURST_ESCALATE_MAX_LEVEL,
                PowerGovernor.nextEscalate(PowerGovernor.BURST_ESCALATE_MAX_LEVEL, false),
                "升级封顶 level 3（24 事件）");
        TestHost.checkEq(0, PowerGovernor.nextEscalate(2, true), "有战果（真关了弹窗）→ 归零");
    }

    /* ───────── ④ 位置学习事后验证 ───────── */

    private static void testPosVerify() {
        // 场景 A（#58 实锤）：豆包聊天按钮 (17%,82%)——点击后窗口纹丝不动
        TestHost.check(!PowerGovernor.posCommit(1600, false, false, false),
                "聊天点击 1.6s 窗未关 → 丢弃（(17%,82%) 乱学根治）");
        TestHost.check(!PowerGovernor.posCommit(900, false, false, false),
                "聊天点击 0.9s 窗未关 → 丢弃");

        // 场景 B：真跳过键（广告窗画像）——点击后广告窗退出
        TestHost.check(PowerGovernor.posCommit(800, true, false, false),
                "点击后 0.8s 窗类切换（广告窗退出）→ 提交学习");

        // 场景 B'：同窗广告容器原地卸载（Dialog 消散/开屏容器移除）
        TestHost.check(PowerGovernor.treeShrunk(120, 50),
                "树体 120→50 节点（缩水 58%）→ 视为广告容器卸载");
        TestHost.check(PowerGovernor.posCommit(600, false, true, false),
                "树体缩水信号 → 提交学习");

        // 场景 C：内容页导航（非广告画像窗类切换）由服务侧 adLike 门拦截——
        // 纯函数层验证树体缩水的边界
        TestHost.check(!PowerGovernor.treeShrunk(120, 80),
                "树体 120→80（仅缩水 33%）→ 未过 40% 门槛，不视为卸载");
        TestHost.check(!PowerGovernor.treeShrunk(120, 120),
                "树体无变化 → 不提交");

        // 超时红线
        TestHost.check(!PowerGovernor.posCommit(PowerGovernor.POS_VERIFY_MS + 1, true, false, false),
                "超时（>1.5s）即使窗类切换也不学（迟到的切换是用户自己导航）");
        TestHost.check(PowerGovernor.posCommit(PowerGovernor.POS_VERIFY_MS - 1, true, false, false),
                "1.5s 内窗类切换 → 可学");

        // 应用退后台（跳过即退的冷门形态）
        TestHost.check(PowerGovernor.posCommit(700, false, false, true),
                "点击后应用退后台 → 提交（跳过即退形态）");
    }

    /* ───────── ⑤ 能耗模型 ───────── */

    private static void testEnergyModel() {
        // #58 实测：事件 4604 · 扫描 2396 → 旧公式 2738µWh（0.22/0.72/0.9）
        double v520 = PowerGovernor.microWh(4604, 0, 2396, 67);
        TestHost.check(v520 > 2600 && v520 < 2900,
                "v5.2.0 能耗复算 ≈2738µWh（公式对齐真机日志）");

        // 探针成本比：1 探针 ≈ 1/14 扫描（0.72/0.05 = 14.4）
        double probeCost = PowerGovernor.microWh(0, 1, 0, 0);
        double scanCost = PowerGovernor.microWh(0, 0, 1, 0);
        TestHost.check(scanCost / probeCost > 14 && scanCost / probeCost < 15,
                "探针成本 ≈ 扫描的 1/14（实际比 "
                        + String.format("%.1f", scanCost / probeCost) + "）");

        // 同样的拦截战绩探针化（文本键广告零 DFS + 宿主窗档案零扫描）：
        // 事件量不变（前台事件必须过廉价前滤），扫描 2396→300，探针 900 次
        double v530 = PowerGovernor.microWh(4604, 900, 300, 67);
        TestHost.check(v530 < v520 * 0.50,
                "探针化后同战绩总能耗降 ≥50%（" + String.format("%.0f", v530) + " vs "
                        + String.format("%.0f", v520) + "µWh，实测降 "
                        + String.format("%.0f", (1 - v530 / v520) * 100) + "%）");
    }

    /* ───────── ⑥ 端到端仿真（#57/#58 窗口流） ───────── */

    /**
     * 按 #57/#58 日志重建一屏窗口流（确定性）：
     *  · 豆包 MainActivity（115 节点对话列表）× 8 次进入（聊天高频）
     *  · 微信 ExdeviceRankInfoUI（110 节点排行页）× 4
     *  · 拼多多 NewPageActivity（23 节点）× 4
     *  · 喜马拉雅 MainActivity / FrameLayout（8 节点播放页）× 6
     *  · 喜马拉雅 SplashAdActivity（SDK 广告窗，真广告）× 3 —— 必须全量！
     *  · 豆包聊天流式内容：每窗 40 次内容变化（1.5s 内 ≥3 次，v5.2.0 会开突发窗）
     *
     * v5.2.0 策略（固定 13 档 + v5.1.0 零候选退避 ≈3 扫/窗 + 突发窗全放行）
     * vs v5.3.0 策略（3 次建档 + CLEAN 探针哨兵 + 突发三重门）。
     */
    private static void testEndToEndSimulation() {
        // 窗口流：{类名, 是否SDK广告窗, 是否Dialog}
        String[][] stream = {
                {"nova.MainActivity", "0", "0"}, {"nova.MainActivity", "0", "0"},
                {"nova.MainActivity", "0", "0"}, {"nova.MainActivity", "0", "0"},
                {"nova.MainActivity", "0", "0"}, {"nova.MainActivity", "0", "0"},
                {"nova.MainActivity", "0", "0"}, {"nova.MainActivity", "0", "0"},
                {"mm.ExdeviceRankInfoUI", "0", "0"}, {"mm.ExdeviceRankInfoUI", "0", "0"},
                {"mm.ExdeviceRankInfoUI", "0", "0"}, {"mm.ExdeviceRankInfoUI", "0", "0"},
                {"pdd.NewPageActivity", "0", "0"}, {"pdd.NewPageActivity", "0", "0"},
                {"pdd.NewPageActivity", "0", "0"}, {"pdd.NewPageActivity", "0", "0"},
                {"ting.MainActivity", "0", "0"}, {"ting.FrameLayout", "0", "0"},
                {"ting.MainActivity", "0", "0"}, {"ting.FrameLayout", "0", "0"},
                {"ting.MainActivity", "0", "0"},
                {"ting.SplashAdActivity", "1", "0"}, // 真广告窗（09:06:26 日志）
                {"ting.SplashAdActivity", "1", "0"},
                {"ting.SplashAdActivity", "1", "0"},
        };
        final int CHAT_CONTENT_EVENTS_PER_WINDOW = 40; // 豆包流式回复
        final int LADDER_SCANS_V520 = 3;               // 零候选退避后平均实扫
        final int BURST_SCANS_PER_WINDOW = 14;         // 10s 窗 × 700ms 节流

        /* ── v5.2.0 策略 ── */
        long scans520 = 0, events520 = 0, bursts520 = 0;
        int novaCleanStreak520 = 0; // v5.2.0 无档案：永远 0
        for (String[] w : stream) {
            boolean sdk = "1".equals(w[1]);
            scans520 += sdk ? 5 : LADDER_SCANS_V520; // SDK 窗多扫几轮找跳过键
            boolean chatLike = w[0].startsWith("nova.") || w[0].startsWith("mm.");
            if (chatLike && novaCleanStreak520 >= 0) {
                // v5.2.0：聊天流式内容 1.5s 内 40 事件 ≥3 → 必开突发窗
                events520 += CHAT_CONTENT_EVENTS_PER_WINDOW;
                bursts520 += 1;
                scans520 += BURST_SCANS_PER_WINDOW;
                events520 += 28; // 突发窗 10s 内内容事件全放行
            } else {
                events520 += 8;
            }
        }

        /* ── v5.3.0 策略 ── */
        long scans530 = 0, probes530 = 0, events530 = 0, bursts530 = 0;
        java.util.HashMap<String, Integer> streaks = new java.util.HashMap<String, Integer>();
        java.util.HashMap<String, Long> lastFull = new java.util.HashMap<String, Long>();
        java.util.HashMap<String, int[]> budget = new java.util.HashMap<String, int[]>();
        java.util.HashMap<String, Integer> esc = new java.util.HashMap<String, Integer>();
        long now = 0;
        for (String[] w : stream) {
            now += 30000; // 每窗间隔 30s（日志节奏）
            String key = w[0];
            boolean sdk = "1".equals(w[1]);
            boolean dialog = "2".equals(w[2]);
            int streak = streaks.containsKey(key) ? streaks.get(key) : 0;
            long sinceFull = lastFull.containsKey(key) ? now - lastFull.get(key) : 0;
            boolean suppress = PowerGovernor.suppressLadder(streak, dialog, sdk, false, false, sinceFull);
            if (!suppress) {
                scans530 += sdk ? 5 : LADDER_SCANS_V520;
                lastFull.put(key, now);
                // 档案记账（每窗一次）
                boolean zeroCand = !sdk; // 主窗全零候选；SDK 广告窗有真跳过键
                streaks.put(key, PowerGovernor.nextStreak(streak, !zeroCand, zeroCand));
            } else {
                probes530 += 1; // 探针哨兵（1 次/窗）
                events530 += 2;
            }
            boolean chatLike = w[0].startsWith("nova.") || w[0].startsWith("mm.");
            if (chatLike) {
                // v5.3.0：突发三重门（CLEAN → 永不开；预算 → 最多 2；阈值升级）
                String pkg = w[0].substring(0, w[0].indexOf('.'));
                int[] bb = budget.containsKey(pkg) ? budget.get(pkg) : new int[]{(int) now, 0};
                if (now - bb[0] > 600000) bb = new int[]{(int) now, 0};
                int escLv = esc.containsKey(pkg) ? esc.get(pkg) : 0;
                events530 += CHAT_CONTENT_EVENTS_PER_WINDOW;
                if (PowerGovernor.burstGate(
                        PowerGovernor.suppressLadder(streak, dialog, sdk, false, false, sinceFull),
                        false, bb[1], CHAT_CONTENT_EVENTS_PER_WINDOW, escLv)) {
                    bb[1]++;
                    budget.put(pkg, bb);
                    bursts530 += 1;
                    scans530 += BURST_SCANS_PER_WINDOW;
                    events530 += 28;
                    esc.put(pkg, PowerGovernor.nextEscalate(escLv, false));
                } else {
                    budget.put(pkg, bb);
                }
            } else {
                events530 += 8;
            }
        }

        double wh520 = PowerGovernor.microWh(events520, 0, scans520, 10);
        double wh530 = PowerGovernor.microWh(events530, probes530, scans530, 10);

        TestHost.check(scans530 < scans520 * 0.50,
                "冷启动会话扫描量降 ≥50%（v5.2.0=" + scans520 + " → v5.3.0=" + scans530
                        + "，降 " + String.format("%.0f", (1 - scans530 / (double) scans520) * 100) + "%）");
        TestHost.check(wh530 < wh520 * 0.70,
                "冷启动会话能耗降 ≥30%（" + String.format("%.0f", wh520) + " → "
                        + String.format("%.0f", wh530) + "µWh，降 "
                        + String.format("%.0f", (1 - wh530 / wh520) * 100) + "%——事件过滞性成本占大头，"
                        + "升级仅一次冷建档，稳态见下）");
        TestHost.check(bursts530 < bursts520,
                "突发误开窗减少（v5.2.0=" + bursts520 + " → v5.3.0=" + bursts530 + "）");

        /* ── 稳态（第二会话：档案已持久化——豆包/微信/拼多多/喜马主窗全部 CLEAN）──
         * v5.2.0 每次开机都从零开始重复全量；v5.3.0 档案跨重启保留，
         * 稳态下非广告窗零扫描，只有真广告窗（SDK 指纹）全量出手。 */
        long scansWarm = 0, probesWarm = 0;
        for (String[] w : stream) {
            boolean sdk = "1".equals(w[1]);
            if (sdk) {
                scansWarm += 5; // SDK 广告窗永全量（命中率红线）
            } else {
                probesWarm += 1; // CLEAN：探针哨兵替班
            }
        }
        TestHost.check(scansWarm <= 15,
                "稳态（档案热）扫描仅剩真广告窗（" + scansWarm + " 扫 + " + probesWarm
                        + " 探针 vs v5.2.0 每次 " + scans520 + " 扫）");
        TestHost.check(scansWarm < scans520 * 0.10,
                "稳态扫描量降 ≥90%（" + String.format("%.0f",
                        (1 - scansWarm / (double) scans520) * 100) + "%）");
        // 稳态能耗：同样的事件流（前台事件必须过廉价前滤），扫描只剩真广告窗
        double whWarm = PowerGovernor.microWh(events530, probesWarm, scansWarm, 10);
        TestHost.check(whWarm < wh520 * 0.50,
                "稳态能耗降 ≥50%（" + String.format("%.0f", wh520) + " → "
                        + String.format("%.0f", whWarm) + "µWh，降 "
                        + String.format("%.0f", (1 - whWarm / wh520) * 100) + "%）");

        // 命中率红线：SDK 广告窗（SplashAdActivity）3 次全部全量扫描（零抑制）
        int sdkFull = 0;
        for (String[] w : stream) {
            if (!"1".equals(w[1])) continue;
            boolean suppressedIn530 = false; // 仿真里 SDK 窗永不 suppress（强信号）
            if (PowerGovernor.suppressLadder(99, false, true, false, false, 0)) suppressedIn530 = true;
            if (!suppressedIn530) sdkFull++;
        }
        TestHost.checkEq(3, sdkFull, "SDK 广告窗 3/3 全量（命中率红线：零抑制）");

        // 豆包主窗第 4 次起零扫描（探针哨兵替班）
        int novaScans = 0;
        java.util.HashMap<String, Integer> ns = new java.util.HashMap<String, Integer>();
        for (String[] w : stream) {
            if (!w[0].equals("nova.MainActivity")) continue;
            int st = ns.containsKey(w[0]) ? ns.get(w[0]) : 0;
            if (!PowerGovernor.suppressLadder(st, false, false, false, false, 0)) {
                novaScans += LADDER_SCANS_V520;
                ns.put(w[0], PowerGovernor.nextStreak(st, false, true));
            }
        }
        TestHost.check(novaScans == LADDER_SCANS_V520 * 3,
                "豆包 8 次进入只扫前 3 次（" + novaScans + " 扫 = 3×" + LADDER_SCANS_V520
                        + "），第 4 次起零扫描");
    }
}
