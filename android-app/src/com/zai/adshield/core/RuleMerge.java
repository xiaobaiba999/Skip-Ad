package com.zai.adshield.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v5.5.0 三源规则治理（模块二核心）——优先级权重 · 冲突检测 · 去重合并
 * ─────────────────────────────────────────────────────────────
 * 三源规则（全部 100% GKD 兼容结构）：
 *  · OFFICIAL 官方内嵌（RuleEngine 五层 + 内置订阅）：weight 3——
 *    网络验证过、随 APK 走、离线可用，最可信；
 *  · GKD 社区订阅（SubscriptionManager 拉取）：weight 2——
 *    覆盖广但质量参差（作者水平不一），组级 enable 已过滤；
 *  · LEARNED 学习规则（learnClass/learnPosition 沉淀）：weight 1——
 *    最个性化但单设备样本，须过 LearnGovernor 质量门才准入合并。
 *
 * 冲突定义与裁决（准确率不降红线）：
 *  · 同 pkg + 同规范化 selector + 不同 action → 高 weight 胜出，
 *    低源记 CONFLICT_ACTION（诊断日志可见，不静默丢弃）；
 *  · 同 pkg + 同 selector + 同 action（不同源）→ 合并为一条：
 *    hitCount 累计、来源取高 weight（去重不丢信息）；
 *  · LEARNED 与 OFFICIAL/GKD 完全等价 → 学习规则判 MERGED_SHADOW
 *    （学习命中直接归并到高源，统计不重复计）。
 *
 * 设计红线：纯 Java 零 Android 依赖（JVM 可测 RuleMergeTest）；
 * 入参出参均为 GKD 兼容条目（pkg/selector/action），不发明新格式。
 */
public final class RuleMerge {

    private RuleMerge() {
    }

    /* ── 三源优先级权重（数值越大越可信） ── */
    public static final int W_OFFICIAL = 3;
    public static final int W_GKD = 2;
    public static final int W_LEARNED = 1;

    /** 规则来源标识（Entry.source 取值） */
    public static final String SRC_OFFICIAL = "official";
    public static final String SRC_GKD = "gkd";
    public static final String SRC_LEARNED = "learned";

    /** GKD 兼容动作（与 Subscription 规约一致） */
    public static final int ACT_CLICK_TARGET = 0; // clickTarget：点控件自身
    public static final int ACT_CLICK_CENTER = 1; // clickCenter：点坐标
    public static final int ACT_BACK = 2;         // back：返回键关窗

    /** 合并后的统一规则条目（GKD 兼容字段 + 治理元数据） */
    public static final class Entry {
        public String source;    // official | gkd | learned（合并后取高权重源）
        public String pkg;       // 目标应用包名（""=全局规则）
        public String selector;  // 规范化选择器（GKD 快照串或文本等值键）
        public int action;       // ACT_*：命中后动作
        public int weight;       // source 对应权重（派生字段，合并时用）
        public int hitCount;     // 历史命中数（去重合并时累计）
        public boolean enabled = true;
        public String note = ""; // 冲突/合并备注（诊断日志用）

        public Entry(String source, String pkg, String selector, int action, int hitCount) {
            this.source = source;
            this.pkg = pkg == null ? "" : pkg;
            this.selector = normalize(selector);
            this.action = action;
            this.weight = weightOf(source);
            this.hitCount = hitCount;
        }
    }

    /** 冲突记录（诊断可见，绝不静默裁决） */
    public static final class Conflict {
        public String kind;    // CONFLICT_ACTION | MERGED_SAME | MERGED_SHADOW
        public String pkg;
        public String selector;
        public String winner;  // 胜出/保留源
        public String loser;   // 落败/被并源
        public int winnerAction;
        public int loserAction;

        Conflict(String kind, String pkg, String selector,
                 String winner, String loser, int wa, int la) {
            this.kind = kind;
            this.pkg = pkg;
            this.selector = selector;
            this.winner = winner;
            this.loser = loser;
            this.winnerAction = wa;
            this.loserAction = la;
        }
    }

    /** 合并结果 */
    public static final class Result {
        public List<Entry> kept = new ArrayList<Entry>();
        public List<Conflict> conflicts = new ArrayList<Conflict>();
        public int mergedCount;  // 去重合并掉的条数
        public int droppedCount; // 冲突裁决落败被移除的条数

        /** 治理摘要（启动/诊断日志一行） */
        public String summary() {
            return "三源合并：保留 " + kept.size() + " 条"
                    + " · 合并 " + mergedCount + " · 冲突裁决 " + droppedCount
                    + "（official " + countSrc(kept, SRC_OFFICIAL)
                    + " / gkd " + countSrc(kept, SRC_GKD)
                    + " / learned " + countSrc(kept, SRC_LEARNED) + "）";
        }
    }

    static int countSrc(List<Entry> list, String src) {
        int n = 0;
        for (Entry e : list) if (src.equals(e.source)) n++;
        return n;
    }

    /* ───────────────────────── 治理主入口 ───────────────────────── */

    /**
     * 三源规则合并：去重 → 冲突裁决 → 按权重排序输出。
     * 规则顺序保证【准确率不降】：official > gkd > learned；
     * 同源内按 hitCount 降序（高频规则先匹配——李跳跳式热规则前置）。
     */
    public static Result merge(List<Entry> all) {
        Result r = new Result();
        if (all == null || all.isEmpty()) return r;

        /* 归一键：pkg + '\u0000' + selector + '\u0000' + action
         * 同键 = 完全等价规则（合并）；仅 action 不同 → 冲突组 */
        Map<String, List<Entry>> byTarget = new HashMap<String, List<Entry>>();
        for (Entry e : all) {
            if (e == null || e.selector == null || e.selector.length() == 0 || !e.enabled) continue;
            String key = e.pkg + "\u0000" + e.selector;
            List<Entry> list = byTarget.get(key);
            if (list == null) {
                list = new ArrayList<Entry>();
                byTarget.put(key, list);
            }
            list.add(e);
        }

        for (List<Entry> group : byTarget.values()) {
            if (group.size() == 1) {
                r.kept.add(group.get(0));
                continue;
            }
            /* 组内裁决：weight 降序 → hitCount 降序（稳定排序） */
            try {
                java.util.Collections.sort(group, (a, b) -> {
                    if (a.weight != b.weight) return b.weight - a.weight;
                    return b.hitCount - a.hitCount;
                });
            } catch (Throwable ignored) {
            }
            Entry winner = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                Entry loser = group.get(i);
                if (loser.action != winner.action) {
                    /* 动作冲突：高权重胜出。学习规则与官方/GKD 动作相左
                     * 本就是强噪声信号（学习点到的是广告内诱导键），裁决
                     * 即过滤——误触率只降不升的保障。 */
                    r.conflicts.add(new Conflict("CONFLICT_ACTION",
                            winner.pkg, winner.selector, winner.source, loser.source,
                            winner.action, loser.action));
                    r.droppedCount++;
                    continue;
                }
                /* 等价规则合并：hitCount 累计、来源取高、记 MERGED */
                boolean shadow = SRC_LEARNED.equals(loser.source)
                        && !SRC_LEARNED.equals(winner.source);
                winner.hitCount += loser.hitCount;
                r.conflicts.add(new Conflict(shadow ? "MERGED_SHADOW" : "MERGED_SAME",
                        winner.pkg, winner.selector, winner.source, loser.source,
                        winner.action, loser.action));
                r.mergedCount++;
            }
            r.kept.add(winner);
        }

        /* 输出排序：weight 降序 → hitCount 降序（热规则前置） */
        try {
            java.util.Collections.sort(r.kept, (a, b) -> {
                if (a.weight != b.weight) return b.weight - a.weight;
                return b.hitCount - a.hitCount;
            });
        } catch (Throwable ignored) {
        }
        return r;
    }

    /**
     * 三源规则摘要（服务启动日志一行）：三源规模 + 优先级链。
     * 运行时合并按需（订阅/学习规则变更时），启动只报规模——
     * 内嵌规则是静态表（不走 merge），订阅与学习规则在各自
     * 加载/沉淀路径上已过质量门（组级 enable / LearnGovernor admit）。
     */
    public static String threeSourceSummary(int officialCount, int gkdCount, int learnedCount) {
        return "🔗 三源规则链：official（内嵌+内置订阅）" + officialCount
                + " 条 > gkd（社区订阅）" + gkdCount
                + " 条 > learned（学习沉淀）" + learnedCount
                + " 条——动作冲突高优先级胜出，等价规则合并计数，学习规则须过质量门（score≥0.55）方预拦截";
    }

    /* ───────────────────────── 工具函数 ───────────────────────── */

    public static int weightOf(String source) {
        if (SRC_OFFICIAL.equals(source)) return W_OFFICIAL;
        if (SRC_GKD.equals(source)) return W_GKD;
        return W_LEARNED;
    }

    /**
     * 选择器规范化：空白折叠 + 小写化 + 去尾部序号。
     * GKD 订阅同一控件在不同版本常有 "@TextView1/@TextView2" 尾号差异
     * （布局重排导致 id 游标变化）——去尾号后才能识别为同一目标。
     */
    public static String normalize(String selector) {
        if (selector == null) return "";
        String s = selector.trim().toLowerCase();
        s = s.replaceAll("\\s+", " ");
        /* 尾部游标号：@xxx12 / -id/ctl_3 形态（纯数字后缀 ≥2 位） */
        s = s.replaceAll("(@[a-z_]+)\\d{1,4}$", "$1");
        return s;
    }

    /** 判定两条目是否同一目标（测试与诊断用） */
    public static boolean sameTarget(Entry a, Entry b) {
        return a != null && b != null
                && a.pkg.equals(b.pkg) && a.selector.equals(b.selector);
    }
}
