package com.zai.adshield.core;

/**
 * 规则路由器（v7.1.0）——「内置规则库与订阅市场规则的使用顺序」治理纯函数。
 * ─────────────────────────────────────────────────────────────
 * 用户实锤（第十一轮原话）：优化内置规则库和订阅市场规则的使用顺序和命中问题。
 *
 * 旧序的病灶（市场规则被饿死）：
 *  · 主引擎文本规则 → 内置订阅（先匹配即短路）→ 远程订阅（仅在内置
 *    整窗无命中时才轮到）——内置订阅是 APK 静态快照（21 款 vid 规则），
 *    而订阅市场源（Lin-arm 日更·610+ 款）是用户主动导入的信任投票；
 *    同一应用两源都有规则时，内置陈旧规则常年压在市场新规则前面，
 *    市场导入价值被顺序抵消——「导入了怎么还是不跳」的元凶。
 *  · 且单源只贡献一个候选：内置候选失效（点击落空）后没有第二源候选
 *    可轮换，复验换候选换的是同一个源的同一个目标。
 *
 * 新序（本类是唯一裁决处，绝不散落）：
 *  · 市场源在该应用有【专属规则】时优先（用户导入即信任投票；日更源
 *    时效性天然高于内置静态快照）——远程命中仍过全量既有安全闸
 *    （activityIds 强校验 / skip 形态门 / 诱导词否决 / 面积 ≤35% 门 /
 *    validSkipTextShape），风险面零扩张，只动顺序；
 *  · 内置源兜底接力（市场未覆盖该应用/未导入/被停用时仍在岗）；
 *  · 两源各贡献一个候选进点击轮换（文本候选用尽 → 市场候选 → 内置
 *    候选 → …）——单源候选失效不再拖死整轮（命中率直接受益）；
 *  · 主引擎五层文本规则仍最先（探针 0ms 出手不变）——本类只治理
 *    「订阅类」两源之间的顺序，不碰主引擎。
 *  · 学习规则（LearnGovernor）独立预拦截路径，不入本表（与订阅轮换无交集）。
 *
 * 设计红线：纯 Java 零 Android 依赖（JVM 可单测 RuleOrderTest）；
 * 入参全为布尔（加载态/覆盖态/开关态），出参为有序源标识数组。
 */
public final class RuleRouter {

    private RuleRouter() {
    }

    /* ── 源标识（服务侧/测试/日志共用一套 token） ── */
    public static final String SRC_REMOTE = "remote";    // 订阅市场（用户导入·日更）
    public static final String SRC_BUILTIN = "builtin";  // 内置订阅（APK 静态快照）

    /**
     * 两源使用顺序裁决：
     *  · 市场源可用且在该应用有专属规则 → 【市场优先，内置兜底】；
     *  · 市场源可用但该应用无专属规则 → 内置优先（市场全局组仍随后可轮换到）；
     *  · 市场源不可用（未导入/被停用/未加载）→ 仅内置；
     *  · 两源都不可用 → 空数组（服务侧跳过订阅路径）。
     */
    public static String[] sourceOrder(boolean remoteEnabled, boolean remoteLoaded, boolean remoteCoversPkg,
                                       boolean builtinLoaded, boolean builtinCoversPkg) {
        boolean remoteUsable = remoteEnabled && remoteLoaded;
        boolean remoteFirst = remoteUsable && remoteCoversPkg;
        java.util.ArrayList<String> out = new java.util.ArrayList<String>(2);
        if (remoteUsable) {
            if (remoteFirst) out.add(SRC_REMOTE);
            if (builtinLoaded) out.add(SRC_BUILTIN);
            if (!remoteFirst) out.add(SRC_REMOTE);
        } else if (builtinLoaded) {
            out.add(SRC_BUILTIN);
        }
        return out.toArray(new String[0]);
    }

    /** 源显示名（日志/使用报告用）：市场 / 内置。 */
    public static String zh(String src) {
        if (SRC_REMOTE.equals(src)) return "市场";
        if (SRC_BUILTIN.equals(src)) return "内置";
        return src == null ? "" : src;
    }

    /** 治理摘要（服务启动日志一行）。 */
    public static String summary() {
        return "🔗 订阅两源使用顺序（v7.1.0）：主引擎文本规则最前（探针 0ms）"
                + " → 市场源（用户导入·日更·该应用有专属规则时优先）→ 内置源兜底"
                + "——两源各贡献一个候选进点击轮换，单源候选失效不再拖死整轮；"
                + "远程命中仍过全量安全闸（activityIds/形态门/诱导词/面积），风险面零扩张";
    }
}
