package com.zai.adshield.test;

import com.zai.adshield.core.LogStore;
import com.zai.adshield.core.LogUploader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * v8.2.2 简化上传器回归套件（替换 v8.0.0~v8.2.1 LogUploadNowTest +
 * LogUploadPolicyTest 的裁决表部分——真实 runUpload 代码路径 + HTTP 替身）。
 * ─────────────────────────────────────────────────────────
 * 用户十项测试要求的 JVM 覆盖（真机 E2E 单列——本套件不冒充真机结果）：
 *  T1  有新日志时手动上传：分块 POST → 服务端确认 → 事件消费；
 *  T2  没有新日志时手动上传：零 POST + emptyReason 四态区分；
 *  T3  上传失败后重新上传：失败保留 → 重试成功（同 batchId）；
 *  T4  成功后不重复提交：消费后 pending 空 → 再点零 POST；幂等响应
 *      （ALREADY_ACCEPTED 复用编号）也按成功消费；
 *  T5  关闭自动上传后手动仍正常：手动路径不查 autoEligible；
 *  T6  自动一天最多成功 3 次：autoEligible 边界 + ok-times 窗口；
 *  T7  无新日志不创建 Issue：pending=0 → autoEligible false + 零 POST；
 *  T9  服务端拒绝不错标成功：ok=false / 非 2xx / 终态码三路都不消费；
 *  T10 Issue 编号与链接一致：verify 命中 → 真实 URL；404 → 空串不造链接。
 * （T8 重启保留在 LogStoreTest.S2——存储侧职责。）
 */
public class LogUploaderTest {

    /** HTTP 替身：可编程响应序列 + POST/verify 调用记录。 */
    static final class FakeHttp implements LogUploader.Http {
        final List<JSONObject> posts = new ArrayList<JSONObject>();
        final List<Integer> verified = new ArrayList<Integer>();
        /** 每次 POST 的响应（null = 网络异常）；下标耗尽后沿用末位。 */
        final List<LogUploader.Resp> responses = new ArrayList<LogUploader.Resp>();
        /** verifyIssueUrl 结果（按编号映射；未配置返回 null = 404）。 */
        final java.util.Map<Integer, String> urls = new java.util.HashMap<Integer, String>();

        public LogUploader.Resp post(String url, JSONObject body) {
            posts.add(body);
            int idx = posts.size() - 1;
            if (responses.isEmpty()) return ok(101);
            return idx < responses.size() ? responses.get(idx) : responses.get(responses.size() - 1);
        }

        public String verifyIssueUrl(int issueNumber) {
            verified.add(issueNumber);
            String u = urls.get(issueNumber);
            return u != null ? u : null;
        }

        static LogUploader.Resp ok(int issueNumber) {
            JSONObject j = new JSONObject();
            try {
                j.put("ok", true);
                j.put("code", "ACCEPTED");
                j.put("issueNumber", issueNumber);
            } catch (Throwable ignored) {
            }
            return new LogUploader.Resp(200, j);
        }

        static LogUploader.Resp alreadyAccepted(int issueNumber) {
            JSONObject j = new JSONObject();
            try {
                j.put("ok", true);
                j.put("code", "ALREADY_ACCEPTED");
                j.put("issueNumber", issueNumber);
            } catch (Throwable ignored) {
            }
            return new LogUploader.Resp(200, j);
        }

        static LogUploader.Resp rateLimited() {
            JSONObject j = new JSONObject();
            try {
                j.put("ok", false);
                j.put("code", "RATE_LIMITED");
                j.put("retryAfter", 3600);
            } catch (Throwable ignored) {
            }
            return new LogUploader.Resp(429, j);
        }

        static LogUploader.Resp terminal(String code) {
            JSONObject j = new JSONObject();
            try {
                j.put("ok", false);
                j.put("code", code);
                j.put("error", "test");
            } catch (Throwable ignored) {
            }
            return new LogUploader.Resp(422, j);
        }

        /** HTTP 200 但 body ok=false（红线：200 ≠ 成功）。 */
        static LogUploader.Resp http200ButNotOk(String code) {
            JSONObject j = new JSONObject();
            try {
                j.put("ok", false);
                j.put("code", code);
            } catch (Throwable ignored) {
            }
            return new LogUploader.Resp(200, j);
        }
    }

    private static MemEnv env = new MemEnv();

    private static LogStore storeWith(int n) {
        LogStore s = LogStore.forEnv(env);
        for (int i = 0; i < n; i++) s.appendEvent(ev());
        s.awaitIoForJvm();
        return s;
    }

    private static JSONObject ev() {
        JSONObject e = new JSONObject();
        try {
            e.put("type", "CLICK");
            e.put("at", 1795000000000L);
            e.put("pkg", "demo.app");
        } catch (Throwable ignored) {
        }
        return e;
    }

    private static LogUploader uploader(LogStore s, FakeHttp http) {
        return LogUploader.forTest(s, http, env, "8.2.2", 82200, "14");
    }

    public static void main(String[] args) {
        TestHost.suite("LogUploaderTest");

        t1_manualUploadWithLogs();
        t2_manualUploadEmpty();
        t3_failThenRetry();
        t4_noDuplicateAfterSuccess();
        t5_manualIndependentOfAuto();
        t6_autoQuotaWindow();
        t7_autoNoNewLogs();
        t9_neverFalseSuccess();
        t10_issueUrlContract();

        TestHost.done();
    }

    /* T1：有新日志 → 分块 POST → 确认 → 消费（首块含 STATS 注入 ≤48）。 */
    private static void t1_manualUploadWithLogs() {
        env = new MemEnv();
        LogStore s = storeWith(100);
        FakeHttp http = new FakeHttp();
        LogUploader u = uploader(s, http);
        LogUploader.Summary r = u.runUpload(true);
        TestHost.checkEq(3, r.acked, "T1a 100 条 → 3 块全部确认（47+48+5，首块含 STATS）");
        TestHost.checkEq(3, http.posts.size(), "T1b POST 次数 = 3");
        TestHost.checkEq(0, s.pendingEventCount(), "T1c 服务端确认后事件全消费");
        TestHost.check(r.issueNumbers.contains("101"), "T1d Issue 编号回显");
        JSONObject first = http.posts.get(0);
        TestHost.checkEq(1, first.optInt("schemaVersion"), "T1e schemaVersion 1 契约");
        TestHost.checkEq(82200, first.optInt("versionCode"), "T1f versionCode 契约");
        TestHost.check(first.has("batchId") && first.has("installInstanceId")
                && first.has("privacyFilterVersion") && first.has("events")
                && first.has("createdAt") && first.has("androidVersion")
                && first.has("appVersion"), "T1g 九键齐备");
        TestHost.checkEq(48, first.optJSONArray("events").length(), "T1h 首块 48 事件（STATS+47）");
        TestHost.checkEq(48, http.posts.get(1).optJSONArray("events").length(), "T1i 第二块 48 事件");
        TestHost.checkEq(5 + 0, http.posts.get(2).optJSONArray("events").length(), "T1j 末块 5 事件");
        TestHost.checkEq(3, s.okCount24h(System.currentTimeMillis()), "T1k 3 次成功入窗");
    }

    /* T2：无新日志 → 零 POST + 四态 emptyReason。 */
    private static void t2_manualUploadEmpty() {
        env = new MemEnv();
        LogStore s = storeWith(0);
        FakeHttp http = new FakeHttp();
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(0, http.posts.size(), "T2a 空缓冲零 POST（不建垃圾 Issue）");
        TestHost.checkEq("never-collected", r.emptyReason, "T2b 从未产生日志——如实区分");

        LogStore s2 = storeWith(3);
        FakeHttp h2 = new FakeHttp();
        LogUploader u2 = uploader(s2, h2);
        u2.runUpload(true); // 先全部上传
        LogUploader.Summary r2 = u2.runUpload(true); // 再点一次
        TestHost.checkEq("all-uploaded", r2.emptyReason, "T2c 已全部上传——如实区分");
        TestHost.checkEq(1, h2.posts.size(), "T2d 第二次点击零 POST");
    }

    /* T3：失败保留 → 重试成功（同 batchId——服务端幂等防二建）。 */
    private static void t3_failThenRetry() {
        env = new MemEnv();
        LogStore s = storeWith(40);
        FakeHttp http = new FakeHttp();
        http.responses.add(null); // 第一次：网络异常
        LogUploader u = uploader(s, http);
        LogUploader.Summary r1 = u.runUpload(true);
        TestHost.checkEq(0, r1.acked, "T3a 网络失败零确认");
        TestHost.checkEq("network", r1.lastError, "T3b 失败原因如实");
        TestHost.checkEq(40, s.pendingEventCount(), "T3c 失败后事件全保留");
        String idAfterFail = s.headBatchIdOrAppend();
        FakeHttp ok = new FakeHttp();
        LogUploader.Summary r2 = uploader(s, ok).runUpload(true);
        TestHost.checkEq(1, r2.acked, "T3d 重试成功");
        TestHost.checkEq(idAfterFail, ok.posts.get(0).optString("batchId"), "T3e 重试同 batchId（幂等契约）");
        TestHost.checkEq(0, s.pendingEventCount(), "T3f 成功后清空");
    }

    /* T4：成功后不重复提交 + ALREADY_ACCEPTED 按成功消费（复用原编号）。 */
    private static void t4_noDuplicateAfterSuccess() {
        env = new MemEnv();
        LogStore s = storeWith(10);
        FakeHttp http = new FakeHttp();
        LogUploader u = uploader(s, http);
        u.runUpload(true);
        String idConsumed = http.posts.get(0).optString("batchId");
        // 重新制造同一块超时未决场景：重试得 ALREADY_ACCEPTED（服务端幂等命中）
        FakeHttp dup = new FakeHttp();
        dup.responses.add(FakeHttp.alreadyAccepted(101));
        LogStore s2 = LogStore.forEnv(env);
        s2.appendEvent(ev());
        s2.awaitIoForJvm();
        LogUploader u2 = uploader(s2, dup);
        // 手工注入未决 manifest 复现「超时但 Issue 已建」：直接用 headBatchIdOrAppend
        String pendingId = s2.headBatchIdOrAppend();
        LogUploader.Summary r = u2.runUpload(true);
        TestHost.checkEq(1, r.acked, "T4a ALREADY_ACCEPTED 计为成功");
        TestHost.check(r.issueNumbers.contains("101"), "T4b 复用原 Issue 编号（绝不二建）");
        TestHost.checkEq(pendingId, dup.posts.get(0).optString("batchId"), "T4c 未决块同号重试");
        // 成功后再点：零新 POST（dup 只安排了一个响应，若重传会错位）
        LogUploader.Summary r2 = u2.runUpload(true);
        TestHost.checkEq(0, r2.uploaded, "T4d 消费后无重复提交");
        TestHost.check(idConsumed.length() > 0, "T4e 消费块编号留痕");
    }

    /* T5：手动上传不查自动资格（关闭自动后手动仍正常）。 */
    private static void t5_manualIndependentOfAuto() {
        long h8 = 8L * 3600 * 1000;
        long now = 1795000000000L;
        // 自动：间隔不足 + 配额满 → 一律禁行
        TestHost.check(!LogUploader.autoEligible(now - 1000, now, h8, 50, 0),
                "T5a 间隔不足自动禁行");
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8, 50, 3),
                "T5b 配额满自动禁行");
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8, 50, 99),
                "T5c 异常大计数防御");
        // 手动：同一 store 配额已满仍可 runUpload(manual)（不查 autoEligible）
        env = new MemEnv();
        LogStore s = storeWith(5);
        for (int i = 0; i < 3; i++) s.recordOk(System.currentTimeMillis()); // 配额满
        FakeHttp http = new FakeHttp();
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(1, r.acked, "T5d 配额满 3/3 手动照传（本地不拦）");
        TestHost.checkEq(0, s.pendingEventCount(), "T5e 手动上传成功消费");
    }

    /* T6：自动一天最多成功 3 次（autoEligible 三重闸全边界）。 */
    private static void t6_autoQuotaWindow() {
        long h8 = 8L * 3600 * 1000;
        long now = 1795000000000L;
        TestHost.check(LogUploader.autoEligible(now - h8, now, h8, 1, 0), "T6a 8h 到 + 有新日志 + 0 成功 → 允许");
        TestHost.check(LogUploader.autoEligible(now - h8 - 1, now, h8, 1, 2), "T6b 第 3 次机会（2 成功）");
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8, 1, 3), "T6c 3 成功满 → 禁行");
        TestHost.check(!LogUploader.autoEligible(now - h8 + 1, now, h8, 1, 0), "T6d 间隔不足 → 禁行");
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8, 0, 0), "T6e 无新日志 → 禁行");
        TestHost.check(LogUploader.autoEligible(0, now, h8, 1, 0), "T6f 首传允许（从未尝试）");
        // 窗口滚动：25h 前的成功已过期 → 配额腾出
        env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        long day = 24L * 3600 * 1000;
        s.recordOk(now - day - 1000);
        s.recordOk(now - day - 2000);
        s.recordOk(now - 3600_000);
        TestHost.checkEq(1, s.okCount24h(now), "T6g 过期成功腾出配额");
        TestHost.check(LogUploader.autoEligible(now - h8, now, h8, 1, s.okCount24h(now)),
                "T6h 滚动窗口后允许第 3 次（严格 24h 语义）");
    }

    /* T7：自动无新日志不创建 Issue。 */
    private static void t7_autoNoNewLogs() {
        env = new MemEnv();
        LogStore s = storeWith(0);
        FakeHttp http = new FakeHttp();
        long h8 = 8L * 3600 * 1000;
        long now = System.currentTimeMillis();
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8,
                s.pendingEventCount(), s.okCount24h(now)), "T7a 空缓冲自动禁行");
        // 即便误入 runUpload：零事件零 POST
        LogUploader.Summary r = uploader(s, http).runUpload(false);
        TestHost.checkEq(0, http.posts.size(), "T7b 零 POST（绝不建空 Issue）");
        TestHost.checkEq(0, r.acked, "T7c 零确认");
        TestHost.checkEq("never-collected", r.emptyReason, "T7d 原因如实");
    }

    /* T9：服务端拒绝不错标成功（三路：ok=false / 非 2xx / 终态码）。 */
    private static void t9_neverFalseSuccess() {
        // 路 1：HTTP 200 但 body ok=false（红线：200 ≠ 成功）
        env = new MemEnv();
        LogStore s1 = storeWith(5);
        FakeHttp h1 = new FakeHttp();
        h1.responses.add(FakeHttp.http200ButNotOk("TEMPORARY_ERROR"));
        LogUploader.Summary r1 = uploader(s1, h1).runUpload(true);
        TestHost.checkEq(0, r1.acked, "T9a HTTP 200 但 ok=false 不计成功");
        TestHost.checkEq(5, s1.pendingEventCount(), "T9b 事件保留");
        // 路 2：429 RATE_LIMITED → waited 不算成功不消费
        env = new MemEnv();
        LogStore s2 = storeWith(5);
        FakeHttp h2 = new FakeHttp();
        h2.responses.add(FakeHttp.rateLimited());
        LogUploader.Summary r2 = uploader(s2, h2).runUpload(true);
        TestHost.checkEq(0, r2.acked, "T9c 限流不算成功");
        TestHost.checkEq(1, r2.waited, "T9d waited 如实计数");
        TestHost.checkEq(5, s2.pendingEventCount(), "T9e 限流后事件保留");
        // 路 3：终态码 PRIVACY_REJECTED → 拒收隔离 + 不计成功
        env = new MemEnv();
        LogStore s3 = storeWith(5);
        FakeHttp h3 = new FakeHttp();
        h3.responses.add(FakeHttp.terminal("PRIVACY_REJECTED"));
        LogUploader.Summary r3 = uploader(s3, h3).runUpload(true);
        TestHost.checkEq(0, r3.acked, "T9f 终态拒收不算成功");
        TestHost.checkEq(1, r3.rejected, "T9g rejected 如实计数");
        TestHost.checkEq(0, s3.pendingEventCount(), "T9h 拒收块移出 pending（隔离检视）");
        TestHost.checkEq(1, s3.rejectedCount(), "T9i 检视记录在案");
        TestHost.checkEq(0, s3.okCount24h(System.currentTimeMillis()), "T9j 拒收不耗成功配额");
    }

    /* T10：Issue 编号与链接一致性（验证命中 → 真实 URL；404 → 空串不造链接）。 */
    private static void t10_issueUrlContract() {
        env = new MemEnv();
        LogStore s = storeWith(5);
        FakeHttp http = new FakeHttp();
        http.urls.put(101, "https://github.com/xiaobaiba999/Skip-Ad/issues/101");
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq("101", r.issueNumbers, "T10a 编号回显");
        TestHost.checkEq("https://github.com/xiaobaiba999/Skip-Ad/issues/101", r.issueUrls,
                "T10b 验证命中的真实 URL 一致");
        TestHost.checkEq(1, http.verified.size(), "T10c 验证调用一次");
        // 404 场景：验证未命中 → 空串（UI 只显示编号不造链接）
        env = new MemEnv();
        LogStore s2 = storeWith(5);
        FakeHttp h2 = new FakeHttp(); // urls 空 → verify 返回 null
        LogUploader.Summary r2 = uploader(s2, h2).runUpload(true);
        TestHost.checkEq("101", r2.issueNumbers, "T10d 编号仍回显（服务端已确认）");
        TestHost.checkEq("", r2.issueUrls, "T10e 未验证到 → 空串（绝不拼接未经确认的地址）");
    }
}
