package com.zai.adshield.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/**
 * v8.0.0 上传策略纯函数（纯 Java · 零 Android 依赖 · JVM 可测）。
 * ─────────────────────────────────────────────
 * 中转服务（skip-ad-log-relay）响应契约的客户端裁决表——LogUploadDispatcher
 * 只做 IO 与状态落盘，一切「下一步」决策都在这里（可穷举、可回归）。
 *
 * 裁决表（与服务端 DEPLOY.md §4 契约一一对应）：
 *  · ok（HTTP 2xx 且 body ok=true）+ ACCEPTED / ALREADY_ACCEPTED → ACK
 *    （ALREADY_ACCEPTED = 超时重放的幂等命中——绝不产生第二个 Issue）；
 *  · RATE_LIMITED → WAIT：尊重服务端 retryAfter（滑窗/预算拒绝），【不消耗
 *    重试计数——被拒 ≠ 失败】；retryAfter 失真时钳制 [30s, 24h]；
 *  · PRIVACY_REJECTED / INVALID_REQUEST / UNAUTHORIZED / IDEMPOTENCY_CONFLICT /
 *    PAYLOAD_TOO_LARGE / MANUAL_RECONCILIATION_REQUIRED → FAIL 终态
 *    （重试同样会被拒；MANUAL_RECONCILIATION_REQUIRED 尤其禁止换 batchId
 *    重传同一内容——服务端明确红线）；
 *  · TEMPORARY_ERROR / GITHUB_UNAVAILABLE / 网络异常 / 未知 code → RETRY：
 *    指数退避 30s→60s→120s→240s→480s（同一 batchId 重试——服务端幂等
 *    兜底「超时但 Issue 已建」场景：重试必然得到 ALREADY_ACCEPTED）；
 *  · 重试耗尽（第 6 次尝试仍瞬时失败）→ FAIL 终态（批次保留 14 天检视；
 *    v8.2.1 手动一键上传可复活重试——同 batchId，服务端幂等兑底）。
 */
public final class LogUploadPolicy {

    private LogUploadPolicy() {
    }

    /* ═══ 裁决动作 ═══ */
    public static final int ACK = 0;   // 服务端确认——删除本地批次，记 acked 元数据
    public static final int WAIT = 1;  // 限流等待——回 PENDING，不耗重试计数
    public static final int RETRY = 2; // 瞬时故障——退避后重试（同 batchId）
    public static final int FAIL = 3;   // 终态失败——保留检视，绝不自动重试

    /** 退避表（index = 已尝试次数-1：第 1 次失败等 30s……第 5 次等 480s）。 */
    public static final long[] BACKOFF_MS = {30_000L, 60_000L, 120_000L, 240_000L, 480_000L};

    /** 总尝试上限（1 次首发 + 5 次退避重试——第 6 次失败转 FAIL 终态）。 */
    public static final int MAX_ATTEMPTS = 6;

    private static final long WAIT_MIN_MS = 30_000L;
    private static final long WAIT_MAX_MS = 24L * 60 * 60 * 1000;
    private static final long WAIT_FALLBACK_MS = 3600_000L;

    /** UUID v4 校验（batchId / installInstanceId 上传前最后防线——服务端 401 面）。 */
    public static boolean isValidUuid(String s) {
        return s != null && s.matches("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    }

    /**
     * 裁决（可穷举入口）。
     *
     * @param ok            HTTP 2xx 且响应体 ok=true（服务端明确确认族）
     * @param code          响应体 code（网络级失败/解析失败传 null）
     * @param attemptsMade  本批次已 POST 次数（含刚失败的这次）
     * @return ACK / WAIT / RETRY / FAIL
     */
    public static int decide(boolean ok, String code, int attemptsMade) {
        if (ok) {
            // 2xx = 服务端确认（ACCEPTED / ALREADY_ACCEPTED；未来新增确认族 code
            // 按 ok=true 语义尊重——服务端 2xx 永远意味着批次已被接受）
            return ACK;
        }
        if (code == null) return attempts(attemptsMade);
        if ("RATE_LIMITED".equals(code)) return WAIT;
        if (isTerminalRejection(code)) {
            return FAIL;
        }
        // TEMPORARY_ERROR / GITHUB_UNAVAILABLE / 未知 code → 同 batchId 退避重试
        return attempts(attemptsMade);
    }

    private static int attempts(int attemptsMade) {
        return attemptsMade >= MAX_ATTEMPTS ? FAIL : RETRY;
    }

    /** v8.2.1：终态拒收判定（FAIL 复活门用）——六个服务端明确拒收码属于
     *  「重试必然再拒」型，手动复活无意义；网络/瞬时码耗尽属于「可重试」型。
     *  与 decide() 的 FAIL 分支单一数据源（同表同步递增）。 */
    public static boolean isTerminalRejection(String code) {
        return "PRIVACY_REJECTED".equals(code) || "INVALID_REQUEST".equals(code)
                || "UNAUTHORIZED".equals(code) || "IDEMPOTENCY_CONFLICT".equals(code)
                || "PAYLOAD_TOO_LARGE".equals(code)
                || "MANUAL_RECONCILIATION_REQUIRED".equals(code);
    }

    /** RETRY 延迟（attemptsMade = 已尝试次数，含刚失败这次）。 */
    public static long retryDelayMs(int attemptsMade) {
        int idx = Math.min(Math.max(attemptsMade, 1), BACKOFF_MS.length) - 1;
        return BACKOFF_MS[idx];
    }

    /** WAIT 延迟（服务端 retryAfter 秒——钳制 [30s, 24h]，失真回退 1h）。 */
    public static long waitDelayMs(long retryAfterSec) {
        if (retryAfterSec <= 0) return WAIT_FALLBACK_MS;
        long ms = retryAfterSec * 1000L;
        if (ms < WAIT_MIN_MS) return WAIT_MIN_MS;
        if (ms > WAIT_MAX_MS) return WAIT_MAX_MS;
        return ms;
    }

    /* ═══ 请求体构造（schemaVersion 1——与服务端 validate.ts 一字不差）═══ */

    /**
     * 构造上传请求体（顶层白名单九键；任何多余键服务端会剥离）。
     * batchId / installInstanceId 非法（非 UUID v4）时返回 null——调用方
     * 直接 FAIL 终态（绝不把明知 401 的请求发出去）。Android org.json 的
     * put 声明 checked JSONException（键全字面量结构性不可达）——内部兜底，
     * 万一发生返回 null（调用方已 null-safe）。
     */
    public static JSONObject buildBody(String batchId, String installInstanceId, String appVersion,
                                       int versionCode, String androidVersion, String createdAt,
                                       int privacyFilterVersion, JSONArray events) {
        if (!isValidUuid(batchId) || !isValidUuid(installInstanceId)) return null;
        try {
            JSONObject b = new JSONObject();
            b.put("schemaVersion", 1);
            b.put("batchId", batchId);
            b.put("installInstanceId", installInstanceId);
            b.put("appVersion", appVersion == null ? "0.0.0" : appVersion);
            b.put("versionCode", versionCode);
            b.put("androidVersion", androidVersion == null ? "" : androidVersion);
            b.put("createdAt", createdAt);
            b.put("privacyFilterVersion", privacyFilterVersion);
            b.put("events", events == null ? new JSONArray() : events);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 新 batchId（UUID v4——重试绝不换号：同批次重试必须复用原 batchId）。 */
    public static String newBatchId() {
        return UUID.randomUUID().toString();
    }
}
