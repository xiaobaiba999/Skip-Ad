package com.zai.adshield.core;

import org.json.JSONObject;

/**
 * v8.0.0 日志事件工厂（纯 Java · 零 Android 依赖 · JVM 可测）。
 * ─────────────────────────────────────────────
 * 职责：把服务侧接线点的原始值（pkg/状态名/计数）装配成 LogPrivacyFilter
 * 白名单严格可接受的结构化事件——字段名与枚举值与中转服务 validate.ts
 * 一字不差（错值在封批时会被剥离计数，等于丢数据；本工厂从源头保证形值）。
 *
 * 纪律：
 *  · 只产白名单字段（type/at/pkg/activity/枚举/整数界/短文本）；
 *  · text 字段先过 LogPrivacyFilter.isAllowedCandidateText 词族门
 *    （非 close/skip 词族一律不带——不留待服务端剥离）；
 *  · Android org.json 的 put 声明 checked JSONException——本工厂内部
 *    兜底（null 键永不出现，异常结构性不可达；万一发生返回 null，
 *    调用方 tapEvent/appendEvent 均已 null-safe）；
 *  · 全静态纯函数——服务侧一行调用，任何异常不影响点击主链。
 */
public final class LogEvents {

    private LogEvents() {
    }

    /** 事件时刻（ms epoch——服务端白名单界 [1e12, 4.1e12]）。 */
    private static long now() {
        long t = System.currentTimeMillis();
        return t < 1_000_000_000_000L ? 1_000_000_000_000L : t;
    }

    /* ═══ AD_TASK：任务终局（taskState 终态枚举 + 六态终局 outcome）═══ */

    /** AD_TASK 终局事件（SUCCESS/FAILED/UNKNOWN 终态——RETRY 非终局不出事件）。 */
    public static JSONObject adTask(String pkg, String act, String taskState, String outcome,
                                    int retryCount, int scanCount, int generation) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "AD_TASK");
            e.put("at", now());
            putStr(e, "pkg", pkg, 96);
            putStr(e, "activity", act, 96);
            putEnum(e, "taskState", taskState);
            putEnum(e, "outcome", outcome);
            e.put("retryCount", clampInt(retryCount, 0, 99));
            e.put("scanCount", clampInt(scanCount, 0, 9_999));
            e.put("generation", clampInt(generation, 0, 1_000_000_000));
            return e;
        } catch (Throwable t) {
            return null; // put 的 checked JSONException 结构性不可达（键全字面量）——兜底
        }
    }

    /* ═══ SAFE_BLOCK：安全闸终拒（宁漏勿误闸在岗证据）═══ */

    public static JSONObject safeBlock(String pkg, String safetyReason) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "SAFE_BLOCK");
            e.put("at", now());
            putStr(e, "pkg", pkg, 96);
            e.put("safetyGuard", "BLOCK");
            putMask(e, "safetyReason", safetyReason, 32);
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /* ═══ CLICK / VERIFY：派发与复验 ═══ */

    /**
     * CLICK 派发事件。candidateSource 传 DecisionEvidence.SRC_* 原值
     * （text/contentDescription/resourceId/imageCorner 小写形态）——本方法
     * 映射为白名单枚举（TEXT/DESC/IMAGE_CORNER/OTHER）；词族内文本才入 text。
     */
    public static JSONObject click(String pkg, String act, String srcLower, String rawText,
                                   boolean dispatched) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "CLICK");
            e.put("at", now());
            putStr(e, "pkg", pkg, 96);
            putStr(e, "activity", act, 96);
            String src = mapSource(srcLower);
            e.put("candidateSource", src);
            e.put("candidateType", candidateTypeOf(src));
            if (rawText != null && LogPrivacyFilter.isAllowedCandidateText(rawText)) {
                putStr(e, "text", rawText.trim(), 16);
            }
            e.put("clickResult", dispatched ? "DISPATCHED" : "FAILED");
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /** VERIFY 复验扫描事件（closed = 扫描零候选——CLOSED 信号；否则 NOT_CLOSED）。 */
    public static JSONObject verify(String pkg, boolean closed) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "VERIFY");
            e.put("at", now());
            putStr(e, "pkg", pkg, 96);
            e.put("verifyResult", closed ? "CLOSED" : "NOT_CLOSED");
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /* ═══ STATS：计数器快照（开机周期累计——分析侧差分解读）═══ */

    /**
     * STATS 快照事件。gkd8 = SubscriptionManager.subStatV2Snapshot()
     * （consulted/matched/candidateAccepted/clicked/verifiedSuccess/clickFail/
     * falseClick/blocked）；task 六态取 DecisionEvidence.outcomeSnapshot()
     * （[VERIFIED_SUCCESS, RETRY, MISS, FALSE_CLICK, SAFE_BLOCK, UNKNOWN] →
     * taskSuccess/taskMiss/taskUnknown——RETRY/FALSE_CLICK/SAFE_BLOCK 有
     * 专用事件面，不入 task 四格口径）。
     * v7.9.8：deviceCode（Build 品牌型号白名单形态码，如 DEV_Xiaomi.2410DPN6CC）
     * 经 errClass 字段上行——服务端白名单内的唯一自由文本位；null/空则省略。
     */
    public static JSONObject stats(int[] gkd8, int taskSuccess, int taskMiss, int taskUnknown,
                                   String deviceCode) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "STATS");
            e.put("at", now());
            if (deviceCode != null && deviceCode.length() > 0) {
                putStr(e, "errClass", deviceCode, 80);
            }
            if (gkd8 != null && gkd8.length >= 8) {
                e.put("gkdConsulted", clampInt(gkd8[0], 0, 1_000_000_000));
                e.put("gkdMatched", clampInt(gkd8[1], 0, 1_000_000_000));
                e.put("gkdCandidateAccepted", clampInt(gkd8[2], 0, 1_000_000_000));
                e.put("gkdClicked", clampInt(gkd8[3], 0, 1_000_000_000));
                e.put("gkdVerifiedSuccess", clampInt(gkd8[4], 0, 1_000_000_000));
                e.put("gkdClickFail", clampInt(gkd8[5], 0, 1_000_000_000));
                e.put("gkdFalseClick", clampInt(gkd8[6], 0, 1_000_000_000));
                e.put("gkdBlocked", clampInt(gkd8[7], 0, 1_000_000_000));
            }
            e.put("taskSuccess", clampInt(taskSuccess, 0, 1_000_000_000));
            e.put("taskMiss", clampInt(taskMiss, 0, 1_000_000_000));
            e.put("taskUnknown", clampInt(taskUnknown, 0, 1_000_000_000));
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /* ═══ SCAN_MISS：扫描未命中结构化诊断（v7.9.8 新增——白名单事件类型
     *  自 v8.0.0 契约就存在但从未使用；零候选窗是「为什么没跳过」的最大
     *  证据空洞：AD_TASK 只覆盖有任务窗，纯零候选窗此前零事件）═══
     *
     * 字段全部在服务端白名单内（绝无新增字段面）：
     *  · pkg/activity —— 应用包与窗口类；
     *  · ui —— SPLASH（开屏扫描）/ INTERSTITIAL（弹窗窗期）；
     *  · errClass —— 稳定失败码（NO_RULE_ZERO_CAND / RULE_MISS_ZERO_CAND /
     *    SILENT_TREE_ZERO_CAND / VIDEO_LIKE_ZERO_CAND）；
     *  · scanCount —— 本窗零候选连续次数；
     *  · intervalMs —— 树静默时长（零内容事件窗 = 窗龄——视频容器铁证）。
     * 隐私：零新增扫描——全部字段来自既有扫描路径已产生的信息；
     * 采集频率同 logScanMiss（每窗一条 + 同包 8s 节流）。 */
    public static JSONObject scanMiss(String pkg, String act, boolean popup, String failCode,
                                      int zeroStreak, long silenceMs) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "SCAN_MISS");
            e.put("at", now());
            putStr(e, "pkg", pkg, 96);
            putStr(e, "activity", act, 96);
            e.put("ui", popup ? "INTERSTITIAL" : "SPLASH");
            putStr(e, "errClass", failCode, 80);
            e.put("scanCount", clampInt(zeroStreak, 0, 9_999));
            e.put("intervalMs", clampInt((int) Math.max(0, Math.min(silenceMs, 600_000L)), 0, 600_000));
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * SCAN_MISS 失败码（纯函数·JVM 可回归）：
     *  · 树零内容事件（SurfaceView/TextureView 视频在放）且窗龄 ≥2s
     *    → VIDEO_LIKE_ZERO_CAND（视频容器静默铁证）；
     *  · 无任何规则/订阅覆盖 → NO_RULE_ZERO_CAND（补规则/订阅可解）；
     *  · 有规则/订阅覆盖但零命中 → RULE_MISS_ZERO_CAND（规则过时或形态
     *    不匹配——需看候选样本）；
     *  · 零内容事件但窗龄尚短 → SILENT_TREE_ZERO_CAND（树静默观察中）。
     */
    public static String scanMissCode(boolean hasContentEvent, long silenceOrElapsedMs,
                                      boolean rulesPresent) {
        if (!hasContentEvent && silenceOrElapsedMs >= 2_000L) return "VIDEO_LIKE_ZERO_CAND";
        if (!hasContentEvent) return "SILENT_TREE_ZERO_CAND";
        return rulesPresent ? "RULE_MISS_ZERO_CAND" : "NO_RULE_ZERO_CAND";
    }

    /* ═══ 错误面（GKD 拉取 / 视频轮询 / 崩溃）═══ */

    /** GKD 订阅拉取异常（errClass = 异常类名——不带消息正文）。 */
    public static JSONObject gkdError(String errClass) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "GKD");
            e.put("at", now());
            putStr(e, "errClass", errClass, 80);
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 视频开屏轮询异常/间隔证据（intervalMs = 轮询间隔）。 */
    public static JSONObject videoPoll(String pkg, long intervalMs) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "VIDEO_POLL");
            e.put("at", now());
            putStr(e, "pkg", pkg, 96);
            e.put("intervalMs", clampInt((int) intervalMs, 0, 600_000));
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 崩溃事件（errClass = 顶层栈帧类名——绝不带堆栈正文/消息文本）。 */
    public static JSONObject crash(String errClass) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "CRASH");
            e.put("at", now());
            putStr(e, "errClass", errClass, 80);
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /* ═══ 内部：字段规约（与 LogPrivacyFilter 白名单一字不差）═══ */

    private static final String[] TASK_STATES = {
            "READY", "CLICKING", "VERIFYING", "RETRY_WAIT", "SUCCESS", "MISS", "FAILED", "UNKNOWN",
    };
    private static final String[] OUTCOMES = {
            "VERIFIED_SUCCESS", "MISS", "FALSE_CLICK", "DUPLICATE_CLICK", "SAFE_BLOCK",
            "NO_AD_OBSERVED", "CRASH", "UNKNOWN",
    };

    private static void putEnum(JSONObject e, String key, String v) throws org.json.JSONException {
        if (v == null) return;
        String[] table = "taskState".equals(key) ? TASK_STATES : OUTCOMES;
        for (String s : table) {
            if (s.equals(v)) {
                e.put(key, v);
                return;
            }
        }
    }

    private static String mapSource(String srcLower) {
        if (srcLower == null) return "OTHER";
        if (srcLower.equals(com.zai.adshield.core.DecisionEvidence.SRC_TEXT)) return "TEXT";
        if (srcLower.equals(com.zai.adshield.core.DecisionEvidence.SRC_DESC)) return "DESC";
        if (srcLower.equals(com.zai.adshield.core.DecisionEvidence.SRC_IMAGE)) return "IMAGE_CORNER";
        return "OTHER"; // resourceId 等无对应枚举——OTHER 兜底（绝不出错值）
    }

    private static String candidateTypeOf(String candidateSource) {
        if ("TEXT".equals(candidateSource)) return "TEXT";
        if ("DESC".equals(candidateSource)) return "DESC";
        if ("IMAGE_CORNER".equals(candidateSource)) return "IMAGE";
        return "OTHER";
    }

    private static void putStr(JSONObject e, String key, String v, int maxLen) throws org.json.JSONException {
        if (v == null) return;
        String s = v.trim();
        if (s.length() == 0 || s.length() > maxLen) return;
        e.put(key, s);
    }

    private static void putMask(JSONObject e, String key, String v, int maxLen) throws org.json.JSONException {
        if (v == null) return;
        String s = LogPrivacyFilter.maskFreeText(v);
        e.put(key, s.length() > maxLen ? s.substring(0, maxLen) : s);
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
