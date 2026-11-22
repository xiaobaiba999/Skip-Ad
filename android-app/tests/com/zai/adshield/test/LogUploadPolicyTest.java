package com.zai.adshield.test;

import com.zai.adshield.core.LogUploadPolicy;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v8.0.0 上传链路 · 策略裁决套件（JVM 纯函数——响应矩阵可穷举回归）。
 *
 * 覆盖面（与服务端 skip-ad-log-relay 契约一一对应）：
 *  · 200 ACCEPTED / 200 ALREADY_ACCEPTED → ACK（幂等命中绝不二建 Issue）；
 *  · 429 RATE_LIMITED → WAIT（retryAfter 尊重 + 钳制 [30s,24h] + 不耗重试计数）；
 *  · 422 PRIVACY_REJECTED / 400 INVALID_REQUEST / 401 UNAUTHORIZED /
 *    409 IDEMPOTENCY_CONFLICT / 413 PAYLOAD_TOO_LARGE /
 *    422 MANUAL_RECONCILIATION_REQUIRED → FAIL 终态（绝不退避）；
 *  · 网络异常 / TEMPORARY_ERROR / GITHUB_UNAVAILABLE → RETRY（同 batchId 退避）；
 *  · 退避表 30s→480s ×5 + 第 6 次尝试耗尽 → FAIL；
 *  · 请求体构造（schemaVersion 1 顶层九键 + UUID 防御门）；
 *  · batchId 唯一性/UUID v4 校验（重试绝不换号——幂等语义客户端半边）。
 */
public final class LogUploadPolicyTest {

    public static void main(String[] args) {
        TestHost.suite("裁决 P1 · 确认族（200 → ACK）");
        p1_ack();
        TestHost.suite("裁决 P2 · 限流（429 → WAIT 不耗计数）");
        p2_wait();
        TestHost.suite("裁决 P3 · 明确拒收终态（4xx/422 → FAIL）");
        p3_fail();
        TestHost.suite("裁决 P4 · 瞬时故障退避（网络/5xx → RETRY 同 batchId）");
        p4_retry();
        TestHost.suite("裁决 P5 · 重试耗尽（第 6 次 → FAIL 终态）");
        p5_exhaust();
        TestHost.suite("裁决 P6 · 退避表与 WAIT 钳制");
        p6_tables();
        TestHost.suite("裁决 P7 · 请求体构造（schemaVersion 1 契约）");
        p7_body();
        TestHost.suite("裁决 P8 · batchId 幂等语义（UUID 唯一/校验/重试不换号）");
        p8_batchId();
        TestHost.suite("裁决 P9 · v8.2.1 终态拒收判定（复活门单一数据源）");
        p9_terminalRejection();
        TestHost.done();
    }

    static void p1_ack() {
        TestHost.check(LogUploadPolicy.decide(true, "ACCEPTED", 1) == LogUploadPolicy.ACK,
                "P1a 200 ACCEPTED → ACK");
        TestHost.check(LogUploadPolicy.decide(true, "ALREADY_ACCEPTED", 2) == LogUploadPolicy.ACK,
                "P1b 200 ALREADY_ACCEPTED（超时重放幂等命中）→ ACK");
        // 已耗尽尝试数后的确认仍 ACK（确认永远优先——服务端说了算）
        TestHost.check(LogUploadPolicy.decide(true, "ACCEPTED", 99) == LogUploadPolicy.ACK,
                "P1c 耗尽后迟到的确认仍 ACK（不因本地计数否决服务端事实）");
    }

    static void p2_wait() {
        TestHost.check(LogUploadPolicy.decide(false, "RATE_LIMITED", 1) == LogUploadPolicy.WAIT,
                "P2a 429 RATE_LIMITED → WAIT");
        TestHost.check(LogUploadPolicy.decide(false, "RATE_LIMITED", 99) == LogUploadPolicy.WAIT,
                "P2b WAIT 不耗重试计数（被拒 ≠ 失败——任何尝试数下仍 WAIT）");
        TestHost.check(LogUploadPolicy.waitDelayMs(3600) == 3600_000L, "P2c retryAfter=1h 尊重");
        TestHost.check(LogUploadPolicy.waitDelayMs(5) == 30_000L, "P2d retryAfter<30s 钳到 30s");
        TestHost.check(LogUploadPolicy.waitDelayMs(0) == 3_600_000L, "P2e retryAfter 失真（0）回退 1h");
        TestHost.check(LogUploadPolicy.waitDelayMs(-1) == 3_600_000L, "P2f retryAfter 负值回退 1h");
        TestHost.check(LogUploadPolicy.waitDelayMs(100000) == 24L * 3600 * 1000, "P2g retryAfter>24h 钳到 24h");
    }

    static void p3_fail() {
        String[] terminal = {
                "PRIVACY_REJECTED", "INVALID_REQUEST", "UNAUTHORIZED",
                "IDEMPOTENCY_CONFLICT", "PAYLOAD_TOO_LARGE", "MANUAL_RECONCILIATION_REQUIRED",
        };
        for (String code : terminal) {
            TestHost.check(LogUploadPolicy.decide(false, code, 1) == LogUploadPolicy.FAIL,
                    "P3 " + code + " → FAIL 终态");
        }
        // MANUAL_RECONCILIATION_REQUIRED 服务端红线：不退避（重试同样 422）、
        // 不换 batchId 重传同一内容（换号在 Dispatcher 侧结构性不存在——重试永远原号）
        TestHost.check(LogUploadPolicy.decide(false, "MANUAL_RECONCILIATION_REQUIRED", 99) == LogUploadPolicy.FAIL,
                "P3b MANUAL 任何尝试数下都 FAIL（无退避路径）");
    }

    static void p4_retry() {
        TestHost.check(LogUploadPolicy.decide(false, null, 1) == LogUploadPolicy.RETRY,
                "P4a 网络异常（无响应体）→ RETRY");
        TestHost.check(LogUploadPolicy.decide(false, "TEMPORARY_ERROR", 1) == LogUploadPolicy.RETRY,
                "P4b TEMPORARY_ERROR → RETRY");
        TestHost.check(LogUploadPolicy.decide(false, "GITHUB_UNAVAILABLE", 2) == LogUploadPolicy.RETRY,
                "P4c GITHUB_UNAVAILABLE → RETRY");
        TestHost.check(LogUploadPolicy.decide(false, "FUTURE_NEW_CODE", 1) == LogUploadPolicy.RETRY,
                "P4d 未知 code → 防御性 RETRY（同 batchId 幂等使重试永远安全）");
        // RETRY 语义核心：同 batchId 重试（buildBody 以存储批次为准——换号只在 ACK/终态后）
        TestHost.check(LogUploadPolicy.decide(false, "TEMPORARY_ERROR", 5) == LogUploadPolicy.RETRY,
                "P4e 第 5 次尝试仍瞬时失败 → RETRY（480s 顶格）");
    }

    static void p5_exhaust() {
        TestHost.check(LogUploadPolicy.decide(false, null, 6) == LogUploadPolicy.FAIL,
                "P5a 第 6 次网络失败 → FAIL 终态（1 首发 + 5 退避）");
        TestHost.check(LogUploadPolicy.decide(false, "TEMPORARY_ERROR", 7) == LogUploadPolicy.FAIL,
                "P5b 超出上限 → FAIL");
        TestHost.check(LogUploadPolicy.MAX_ATTEMPTS == 6, "P5c 上限常量 = 6（1+5）");
    }

    static void p6_tables() {
        long[] expect = {30_000L, 60_000L, 120_000L, 240_000L, 480_000L};
        TestHost.check(LogUploadPolicy.BACKOFF_MS.length == 5, "P6a 退避 5 档");
        for (int i = 0; i < 5; i++) {
            TestHost.check(LogUploadPolicy.retryDelayMs(i + 1) == expect[i],
                    "P6b 退避第 " + (i + 1) + " 次失败后等 " + (expect[i] / 1000) + "s");
        }
        TestHost.check(LogUploadPolicy.retryDelayMs(99) == 480_000L, "P6c 超界钳到 480s 顶格");
        TestHost.check(LogUploadPolicy.retryDelayMs(0) == 30_000L, "P6d 下界钳到 30s");
    }

    static void p7_body() {
        JSONArray events = new JSONArray();
        JSONObject ev = new JSONObject();
        ev.put("type", "AD_TASK");
        events.put(ev);
        String bid = LogUploadPolicy.newBatchId();
        String iid = LogUploadPolicy.newBatchId();
        JSONObject body = LogUploadPolicy.buildBody(bid, iid, "8.0.0", 80000, "14",
                "2026-11-25T12:00:00Z", 1, events);
        TestHost.check(body != null, "P7a 合法入参构造成功");
        TestHost.checkEq(1, body.optInt("schemaVersion"), "P7b schemaVersion=1");
        TestHost.checkEq(bid, body.optString("batchId"), "P7c batchId 原样");
        TestHost.checkEq(iid, body.optString("installInstanceId"), "P7d installInstanceId");
        TestHost.checkEq("8.0.0", body.optString("appVersion"), "P7e appVersion");
        TestHost.checkEq(80000, body.optInt("versionCode"), "P7f versionCode");
        TestHost.checkEq("14", body.optString("androidVersion"), "P7g androidVersion");
        TestHost.checkEq("2026-11-25T12:00:00Z", body.optString("createdAt"), "P7h createdAt ISO");
        TestHost.checkEq(1, body.optInt("privacyFilterVersion"), "P7i privacyFilterVersion");
        TestHost.check(body.optJSONArray("events").length() == 1, "P7j events 透传");
        TestHost.check(body.length() == 9, "P7k 顶层恰九键（白名单全键）");
        // null events → 空数组（空批不上传是上游职责——构造层不炸）
        JSONObject body2 = LogUploadPolicy.buildBody(bid, iid, "8.0.0", 80000, "14",
                "2026-11-25T12:00:00Z", 1, null);
        TestHost.check(body2 != null && body2.optJSONArray("events").length() == 0, "P7l null events → 空数组");
        // 非法 UUID 防御门（服务端 401 面绝不主动撞）
        TestHost.check(LogUploadPolicy.buildBody("not-a-uuid", iid, "8.0.0", 80000, "14",
                "2026-11-25T12:00:00Z", 1, events) == null, "P7m 非法 batchId → null 拒构造");
        TestHost.check(LogUploadPolicy.buildBody(bid, "not-a-uuid", "8.0.0", 80000, "14",
                "2026-11-25T12:00:00Z", 1, events) == null, "P7n 非法 installId → null 拒构造");
    }

    static void p8_batchId() {
        String a = LogUploadPolicy.newBatchId();
        String b = LogUploadPolicy.newBatchId();
        TestHost.check(!a.equals(b), "P8a 两次生成不同 batchId（强唯一）");
        TestHost.check(LogUploadPolicy.isValidUuid(a) && LogUploadPolicy.isValidUuid(b),
                "P8b 生成即 UUID v4 合法");
        TestHost.check(LogUploadPolicy.isValidUuid("00000000-0000-4000-8000-000000000000"),
                "P8c 标准 v4 形态接受");
        TestHost.check(!LogUploadPolicy.isValidUuid("00000000-0000-4000-8000-00000000000"),
                "P8d 缺一位拒绝");
        TestHost.check(!LogUploadPolicy.isValidUuid("00000000-0000-4000-7000-000000000000"),
                "P8e v4 变体位错拒绝（version nibble）");
        TestHost.check(!LogUploadPolicy.isValidUuid("00000000-0000-4000-8000-00000000000G"),
                "P8f 非十六进制拒绝");
        TestHost.check(!LogUploadPolicy.isValidUuid(null) && !LogUploadPolicy.isValidUuid(""),
                "P8g null/空拒绝");
        TestHost.check(!LogUploadPolicy.isValidUuid("0a0b0c0d-0e0f-4a0b-8c0d-0e0f0a0b0c0d".toUpperCase()),
                "P8h 大写拒绝（服务端小写规约——客户端源头保证）");
    }

    /* P9：v8.2.1 终态拒收判定（isTerminalRejection——Dispatcher 复活门的
     * failKind 单一数据源；与 decide() FAIL 分支同表）。 */
    static void p9_terminalRejection() {
        TestHost.check(LogUploadPolicy.isTerminalRejection("PRIVACY_REJECTED"), "P9a PRIVACY_REJECTED 终态");
        TestHost.check(LogUploadPolicy.isTerminalRejection("INVALID_REQUEST"), "P9b INVALID_REQUEST 终态");
        TestHost.check(LogUploadPolicy.isTerminalRejection("UNAUTHORIZED"), "P9c UNAUTHORIZED 终态");
        TestHost.check(LogUploadPolicy.isTerminalRejection("IDEMPOTENCY_CONFLICT"), "P9d IDEMPOTENCY_CONFLICT 终态");
        TestHost.check(LogUploadPolicy.isTerminalRejection("PAYLOAD_TOO_LARGE"), "P9e PAYLOAD_TOO_LARGE 终态");
        TestHost.check(LogUploadPolicy.isTerminalRejection("MANUAL_RECONCILIATION_REQUIRED"), "P9f MANUAL_RECONCILIATION_REQUIRED 终态（服务端红线）");
        TestHost.check(!LogUploadPolicy.isTerminalRejection("TEMPORARY_ERROR"), "P9g TEMPORARY_ERROR 非终态（耗尽型可复活）");
        TestHost.check(!LogUploadPolicy.isTerminalRejection("GITHUB_UNAVAILABLE"), "P9h GITHUB_UNAVAILABLE 非终态");
        TestHost.check(!LogUploadPolicy.isTerminalRejection(null), "P9i null（网络异常）非终态");
        TestHost.check(!LogUploadPolicy.isTerminalRejection("RATE_LIMITED"), "P9j RATE_LIMITED 非终态（WAIT 族——限流不是失败）");
        TestHost.check(!LogUploadPolicy.isTerminalRejection(""), "P9k 空串非终态（防御）");
        TestHost.check(!LogUploadPolicy.isTerminalRejection("ACCEPTED"), "P9l 确认族码非终态（防御——不该走到 FAIL 分支）");
    }
}
