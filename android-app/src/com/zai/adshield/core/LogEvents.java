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
     */
    public static JSONObject stats(int[] gkd8, int taskSuccess, int taskMiss, int taskUnknown) {
        try {
            JSONObject e = new JSONObject();
            e.put("type", "STATS");
            e.put("at", now());
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
