package com.zai.adshield.test;

import com.zai.adshield.core.LogStore;
import com.zai.adshield.core.LogUploader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * v7.9.8 上传器回归套件（真实 runUpload 代码路径 + HTTP 替身）。
 * ─────────────────────────────────────────────────────────
 * 覆盖（真机 E2E 单列——本套件不冒充真机结果）：
 *  T1  有新日志时手动上传：分块 POST → 服务端确认 → 事件消费；
 *  T2  没有新日志时手动上传：零 POST + emptyReason 四态区分；
 *  T3  上传失败后重新上传：失败保留 → 重试字节级复用冻结 payload
 *      （同 batchId + 同 createdAt + 同 events——v7.9.8 冻结幂等核心）；
 *  T4  成功后不重复提交：消费后 pending 空 → 再点零 POST；
 *  T5  手动上传不占自动额度（额度分账——v7.9.8）；
 *  T6  自动一天最多成功 3 次：autoEligible 边界 + 自动账本窗口；
 *  T7  无新日志不创建 Issue：pending=0 → autoEligible false + 零 POST；
 *  T9  服务端拒绝不错标成功：ok=false / 非 2xx / 终态码三路都不消费；
 *  T10 Issue 编号与链接一致：verify 命中 → 真实 URL；404 → 空串不造链接；
 *  T11 【用户真机两连失败场景回归】第一次超时（服务端实际已接收）→
 *      追加新事件后重试 → 冻结 payload 字节级不变 → ALREADY_ACCEPTED
 *      复用原编号成功消费——绝不 IDEMPOTENCY_CONFLICT；
 *  T12 错误分类矩阵（classifyError 八类——真实 HTTP 状态码/服务端码/
 *      网络异常类型三路，UI 八类人话映射的唯一依据；v7.9.8b 裸 401/403
 *      分家为 AUTH_FAILED/FORBIDDEN——认证与权限分开提示）；
 *  T13 限流 retryAfter 透传（RATE_LIMITED → retryAfterMs 毫秒口径）；
 *  T14 【进程被杀重启恢复】上传中进程死亡 → 新 LogStore 实例（同目录
 *      = 重启）→ 冻结批次从磁盘 manifest 恢复 → 字节级同 payload 重试 →
 *      ALREADY_ACCEPTED 消费（用户 J 场景——幂等实现完整性的关键证据）；
 *  T15 【批次 B 独立性】批次 A 成功消费后新日志形成批次 B——不同 batchId、
 *      绝不混入 A 事件（用户 H/I 场景）；
 *  T16 【自动 24h/3 批粒度闸】积压排空也不越 3 批/窗：200 条自动排空只
 *      成功 3 批即停，窗口滑动后续传（v7.9.8b 修复的回归钉死）；
 *  T17 【IDEMPOTENCY_CONFLICT 证据保留】终态冲突 → 批次隔离进 rejected.json
 *      （code+error+events 全在）——不丢证据、不计成功、不再盲重试；
 *  T18 【额度文件损坏防御】ok-times/auto-ok-times 损坏不崩溃、计数归零、
 *      recordOk 重写自愈（用户「损坏的额度文件不会导致应用崩溃」）。
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
        return LogUploader.forTest(s, http, env, "7.9.8", 79800, "17");
    }

    public static void main(String[] args) {
        TestHost.suite("LogUploaderTest");

        t1_manualUploadWithLogs();
        t2_manualUploadEmpty();
        t3_failThenRetryFrozen();
        t4_noDuplicateAfterSuccess();
        t5_manualIndependentOfAuto();
        t6_autoQuotaWindow();
        t7_autoNoNewLogs();
        t9_neverFalseSuccess();
        t10_issueUrlContract();
        t11_userTwoFailureScenario();
        t12_classifyErrorMatrix();
        t13_retryAfterPassthrough();
        t14_restartRecoveryFrozen();
        t15_batchBIndependence();
        t16_autoInRunQuotaCap();
        t17_idempotencyConflictEvidence();
        t18_corruptedQuotaFiles();

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
        TestHost.checkEq(79800, first.optInt("versionCode"), "T1f versionCode 契约");
        TestHost.check(first.has("batchId") && first.has("installInstanceId")
                && first.has("privacyFilterVersion") && first.has("events")
                && first.has("createdAt") && first.has("androidVersion")
                && first.has("appVersion"), "T1g 九键齐备");
        TestHost.checkEq(48, first.optJSONArray("events").length(), "T1h 首块 48 事件（STATS+47）");
        TestHost.checkEq(48, http.posts.get(1).optJSONArray("events").length(), "T1i 第二块 48 事件");
        TestHost.checkEq(5 + 0, http.posts.get(2).optJSONArray("events").length(), "T1j 末块 5 事件");
        TestHost.checkEq(3, s.okCount24h(System.currentTimeMillis()), "T1k 3 次成功入窗（全部账本）");
        TestHost.checkEq(0, s.autoOkCount24h(System.currentTimeMillis()), "T1l 手动路径不记自动账本");
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

    /* T3：失败保留 → 重试字节级复用冻结 payload（v7.9.8 冻结幂等核心）。
     * 失败后追加新事件——冻结块仍原样重发（绝不重构漂移 payload）。 */
    private static void t3_failThenRetryFrozen() {
        env = new MemEnv();
        LogStore s = storeWith(40);
        FakeHttp http = new FakeHttp();
        http.responses.add(null); // 第一次：网络异常
        LogUploader u = uploader(s, http);
        LogUploader.Summary r1 = u.runUpload(true);
        TestHost.checkEq(0, r1.acked, "T3a 网络失败零确认");
        TestHost.checkEq("network", r1.lastError, "T3b 失败原因如实");
        TestHost.checkEq(LogUploader.ERR_NET_IO, r1.errKind, "T3b2 网络层分类（IO）");
        TestHost.checkEq(40, s.pendingEventCount(), "T3c 失败后事件全保留");
        JSONObject firstPost = http.posts.get(0);
        // 失败后追加新事件（旧版会漂移进下一块——v7.9.8 冻结后绝不）
        for (int i = 0; i < 3; i++) s.appendEvent(ev());
        s.awaitIoForJvm();
        FakeHttp ok = new FakeHttp();
        LogUploader.Summary r2 = uploader(s, ok).runUpload(true);
        TestHost.checkEq(2, r2.acked, "T3d 重试成功（冻结块 + 追加块）");
        JSONObject retryPost = ok.posts.get(0);
        TestHost.checkEq(firstPost.optString("batchId"), retryPost.optString("batchId"),
                "T3e 重试同 batchId（幂等契约）");
        TestHost.checkEq(firstPost.optString("createdAt"), retryPost.optString("createdAt"),
                "T3f 重试同 createdAt（v7.9.8 冻结——绝不漂移）");
        TestHost.checkEq(firstPost.optJSONArray("events").toString(),
                retryPost.optJSONArray("events").toString(),
                "T3g 重试同 events（字节级冻结——追加的新事件绝不混入）");
        TestHost.checkEq(0, s.pendingEventCount(), "T3h 成功后清空（含追加块）");
    }

    /* T4：成功后不重复提交。 */
    private static void t4_noDuplicateAfterSuccess() {
        env = new MemEnv();
        LogStore s = storeWith(10);
        FakeHttp http = new FakeHttp();
        LogUploader u = uploader(s, http);
        u.runUpload(true);
        String idConsumed = http.posts.get(0).optString("batchId");
        // 成功后再点：零新 POST
        FakeHttp again = new FakeHttp();
        LogUploader.Summary r2 = uploader(s, again).runUpload(true);
        TestHost.checkEq(0, r2.uploaded, "T4a 消费后无重复提交");
        TestHost.checkEq(0, again.posts.size(), "T4b 零 POST");
        TestHost.check(idConsumed.length() > 0, "T4c 消费块编号留痕");
    }

    /* T5：手动上传不查自动资格 + 不占自动额度（v7.9.8 额度分账）。 */
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
        // 手动：同一 store 自动账本已满仍可 runUpload(manual)（不查 autoEligible）
        env = new MemEnv();
        LogStore s = storeWith(5);
        for (int i = 0; i < 3; i++) s.recordOk(System.currentTimeMillis(), false); // 自动配额满
        FakeHttp http = new FakeHttp();
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(1, r.acked, "T5d 自动配额满 3/3 手动照传（本地不拦）");
        TestHost.checkEq(0, s.pendingEventCount(), "T5e 手动上传成功消费");
        TestHost.checkEq(3, s.autoOkCount24h(System.currentTimeMillis()),
                "T5f 手动成功不追加自动账本（分账保持）");
        TestHost.checkEq(4, s.okCount24h(System.currentTimeMillis()),
                "T5g 全部账本含手动成功（展示口径）");
    }

    /* T6：自动一天最多成功 3 次（autoEligible 三重闸全边界 + 自动账本窗口）。 */
    private static void t6_autoQuotaWindow() {
        long h8 = 8L * 3600 * 1000;
        long now = 1795000000000L;
        TestHost.check(LogUploader.autoEligible(now - h8, now, h8, 1, 0), "T6a 8h 到 + 有新日志 + 0 成功 → 允许");
        TestHost.check(LogUploader.autoEligible(now - h8 - 1, now, h8, 1, 2), "T6b 第 3 次机会（2 成功）");
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8, 1, 3), "T6c 3 成功满 → 禁行");
        TestHost.check(!LogUploader.autoEligible(now - h8 + 1, now, h8, 1, 0), "T6d 间隔不足 → 禁行");
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8, 0, 0), "T6e 无新日志 → 禁行");
        TestHost.check(LogUploader.autoEligible(0, now, h8, 1, 0), "T6f 首传允许（从未尝试）");
        // 窗口滚动：25h 前的自动成功已过期 → 配额腾出
        env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        long day = 24L * 3600 * 1000;
        s.recordOk(now - day - 1000, false);
        s.recordOk(now - day - 2000, false);
        s.recordOk(now - 3600_000, false);
        TestHost.checkEq(1, s.autoOkCount24h(now), "T6g 过期自动成功腾出配额");
        TestHost.check(LogUploader.autoEligible(now - h8, now, h8, 1, s.autoOkCount24h(now)),
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
                s.pendingEventCount(), s.autoOkCount24h(now)), "T7a 空缓冲自动禁行");
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
        TestHost.checkEq(LogUploader.ERR_RELAY_TEMP, r1.errKind, "T9b2 服务端临时错误分类");
        // 路 2：429 RATE_LIMITED → waited 不算成功不消费
        env = new MemEnv();
        LogStore s2 = storeWith(5);
        FakeHttp h2 = new FakeHttp();
        h2.responses.add(FakeHttp.rateLimited());
        LogUploader.Summary r2 = uploader(s2, h2).runUpload(true);
        TestHost.checkEq(0, r2.acked, "T9c 限流不算成功");
        TestHost.checkEq(1, r2.waited, "T9d waited 如实计数");
        TestHost.checkEq(5, s2.pendingEventCount(), "T9e 限流后事件保留");
        TestHost.checkEq(LogUploader.ERR_RATE_LIMITED, r2.errKind, "T9e2 限流分类");
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

    /* T11：【用户真机两连失败场景回归】第一次点击超时（服务端实际已接收并
     * 建 Issue、响应未及返回）→ 期间追加新事件 → 第二次点击：冻结 payload
     * 字节级复用 → 服务端幂等命中 ALREADY_ACCEPTED → 复用原编号成功消费。
     * 旧版（v8.2.2~v7.9.7）此场景必 IDEMPOTENCY_CONFLICT（payload 漂移）。 */
    private static void t11_userTwoFailureScenario() {
        env = new MemEnv();
        LogStore s = storeWith(30);
        // 第一次点击：读超时（RealHttp 45s 后仍超时的场景——服务端实际已处理）
        FakeHttp http1 = new FakeHttp();
        http1.responses.add(LogUploader.Resp.netError("timeout"));
        LogUploader.Summary r1 = uploader(s, http1).runUpload(true);
        TestHost.checkEq(0, r1.acked, "T11a 第一次点击超时零确认（用户看到「上传超时」）");
        TestHost.checkEq(LogUploader.ERR_NET_TIMEOUT, r1.errKind, "T11b 超时分类如实");
        TestHost.checkEq(30, s.pendingEventCount(), "T11c 事件全保留（冻结批次在场）");
        JSONObject click1Body = http1.posts.get(0);
        // 间隔期：新事件追加（漂移诱因——旧版会混入重试块）
        for (int i = 0; i < 8; i++) s.appendEvent(ev());
        s.awaitIoForJvm();
        // 第二次点击：服务端幂等命中（ALREADY_ACCEPTED 复用原编号）
        FakeHttp http2 = new FakeHttp();
        http2.responses.add(FakeHttp.alreadyAccepted(207));
        LogUploader.Summary r2 = uploader(s, http2).runUpload(true);
        TestHost.checkEq(2, r2.acked, "T11d 第二次点击成功（冻结块幂等命中 + 追加块新传）");
        TestHost.check(r2.issueNumbers.startsWith("207"), "T11e 复用原 Issue 编号（绝不二建）");
        JSONObject click2Body = http2.posts.get(0);
        TestHost.checkEq(click1Body.optString("batchId"), click2Body.optString("batchId"),
                "T11f 两击同 batchId");
        TestHost.checkEq(click1Body.optString("createdAt"), click2Body.optString("createdAt"),
                "T11g 两击同 createdAt（冻结——漂移即冲突的旧根因已除）");
        TestHost.checkEq(click1Body.optJSONArray("events").toString(),
                click2Body.optJSONArray("events").toString(),
                "T11h 两击同 events（字节级——追加事件绝不混入冻结块）");
        TestHost.checkEq(0, s.pendingEventCount(), "T11i 事件全部消费");
    }

    /* T12：错误分类矩阵（classifyError——UI 八类人话映射的唯一依据）。 */
    private static void t12_classifyErrorMatrix() {
        // 网络层三分类（真实异常类型映射——RealHttp catch 链同源）
        TestHost.checkEq(LogUploader.ERR_NET_CONNECT, LogUploader.classifyError(LogUploader.Resp.netError("connect")),
                "T12a 连接失败/DNS 污染 → NET_CONNECT_FAIL");
        TestHost.checkEq(LogUploader.ERR_NET_TIMEOUT, LogUploader.classifyError(LogUploader.Resp.netError("timeout")),
                "T12b 超时 → NET_TIMEOUT");
        TestHost.checkEq(LogUploader.ERR_NET_IO, LogUploader.classifyError(LogUploader.Resp.netError("io")),
                "T12c IO 异常 → NET_IO");
        TestHost.checkEq(LogUploader.ERR_NET_IO, LogUploader.classifyError(null),
                "T12d 替身 null（防御）→ NET_IO");
        // HTTP 状态码分类（无 JSON body 的裸状态响应）
        TestHost.checkEq(LogUploader.ERR_RATE_LIMITED, LogUploader.classifyError(new LogUploader.Resp(429, null)),
                "T12e HTTP 429 → RATE_LIMITED");
        TestHost.checkEq("INVALID_REQUEST", LogUploader.classifyError(new LogUploader.Resp(400, null)),
                "T12f HTTP 400 → INVALID_REQUEST");
        TestHost.checkEq("UNAUTHORIZED", LogUploader.classifyError(new LogUploader.Resp(401, json("UNAUTHORIZED"))),
                "T12g 服务端 JSON 码 UNAUTHORIZED（401 带 body）→ 契约码原样透出");
        TestHost.checkEq("AUTH_FAILED", LogUploader.classifyError(new LogUploader.Resp(401, null)),
                "T12h 裸 HTTP 401（无 JSON body）→ AUTH_FAILED（认证失败——v7.9.8b 分家）");
        TestHost.checkEq("FORBIDDEN", LogUploader.classifyError(new LogUploader.Resp(403, null)),
                "T12h2 裸 HTTP 403（无 JSON body）→ FORBIDDEN（权限不足——v7.9.8b 分家）");
        TestHost.checkEq(LogUploader.ERR_RELAY_5XX, LogUploader.classifyError(new LogUploader.Resp(500, null)),
                "T12i HTTP 500 → RELAY_SERVER_ERROR");
        TestHost.checkEq(LogUploader.ERR_RELAY_5XX, LogUploader.classifyError(new LogUploader.Resp(502, null)),
                "T12j HTTP 502 → RELAY_SERVER_ERROR");
        TestHost.checkEq("HTTP_418", LogUploader.classifyError(new LogUploader.Resp(418, null)),
                "T12k 其他 4xx → HTTP_ 前缀码");
        // 服务端 JSON 码优先（有 code 时以服务端口径为准）
        TestHost.checkEq(LogUploader.ERR_RATE_LIMITED,
                LogUploader.classifyError(new LogUploader.Resp(429, json("RATE_LIMITED"))),
                "T12l 服务端码 RATE_LIMITED");
        TestHost.checkEq("PRIVACY_REJECTED",
                LogUploader.classifyError(new LogUploader.Resp(422, json("PRIVACY_REJECTED"))),
                "T12m 终态码原样透出");
        TestHost.checkEq("IDEMPOTENCY_CONFLICT",
                LogUploader.classifyError(new LogUploader.Resp(409, json("IDEMPOTENCY_CONFLICT"))),
                "T12n 幂等冲突终态码透出");
        TestHost.checkEq(LogUploader.ERR_RELAY_TEMP,
                LogUploader.classifyError(new LogUploader.Resp(200, json("TEMPORARY_ERROR"))),
                "T12o 服务端临时错误（可重试）");
        // 2xx 但 body 无 ok=true（协议面异常）
        TestHost.checkEq(LogUploader.ERR_PROTOCOL,
                LogUploader.classifyError(new LogUploader.Resp(200, new JSONObject())),
                "T12p 2xx 空 body → PROTOCOL_UNEXPECTED_BODY");
    }

    private static JSONObject json(String code) {
        JSONObject j = new JSONObject();
        try {
            j.put("ok", false);
            j.put("code", code);
        } catch (Throwable ignored) {
        }
        return j;
    }

    /* T13：限流 retryAfter 透传（服务端秒 → retryAfterMs 毫秒，钳制 ≤24h）。 */
    private static void t13_retryAfterPassthrough() {
        env = new MemEnv();
        LogStore s = storeWith(5);
        FakeHttp http = new FakeHttp();
        http.responses.add(FakeHttp.rateLimited()); // retryAfter=3600s
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(1, r.waited, "T13a 限流如实计数");
        TestHost.checkEq(3600_000L, r.retryAfterMs, "T13b retryAfter 3600s → 3600000ms 透传");
        // 巨值钳制（防溢出/荒谬 UI 展示）
        FakeHttp big = new FakeHttp();
        JSONObject j = new JSONObject();
        try {
            j.put("ok", false);
            j.put("code", "RATE_LIMITED");
            j.put("retryAfter", 999999L);
        } catch (Throwable ignored) {
        }
        big.responses.add(new LogUploader.Resp(429, j));
        env = new MemEnv();
        LogStore s2 = storeWith(5);
        LogUploader.Summary r2 = uploader(s2, big).runUpload(true);
        TestHost.checkEq(24L * 3600 * 1000, r2.retryAfterMs, "T13c retryAfter 巨值钳制 ≤24h");
    }

    /* T14：【进程被杀重启恢复】封批落盘（manifest 持久化）→ 上传中进程死亡 →
     * 新 LogStore 实例（同 env 目录 = 重启后重建）→ 冻结批次恢复 → 字节级
     * 同 payload 重试 → ALREADY_ACCEPTED 幂等消费。若批次只在内存中，此
     * 场景必丢冻结状态（幂等断裂）——本测试钉死磁盘持久化语义。 */
    private static void t14_restartRecoveryFrozen() {
        env = new MemEnv();
        LogStore s1 = storeWith(30);
        FakeHttp http1 = new FakeHttp();
        http1.responses.add(LogUploader.Resp.netError("timeout")); // 超时=进程可能被杀
        LogUploader.Summary r1 = uploader(s1, http1).runUpload(true);
        TestHost.checkEq(0, r1.acked, "T14a 第一次超时零确认");
        JSONObject body1 = http1.posts.get(0);
        // 进程死亡：丢弃实例（内存状态全失）——仅磁盘文件存活
        s1 = null;
        // 重启：同 env（同目录）新实例——冻结批次必须从 batch-manifest.json 恢复
        LogStore s2 = LogStore.forEnv(env);
        TestHost.checkEq(30, s2.pendingEventCount(), "T14b 重启后 pending 无损");
        FakeHttp http2 = new FakeHttp();
        http2.responses.add(FakeHttp.alreadyAccepted(207)); // 服务端：早已接收
        LogUploader.Summary r2 = uploader(s2, http2).runUpload(true);
        TestHost.checkEq(1, r2.acked, "T14c 重启后冻结批次恢复并幂等成功");
        JSONObject body2 = http2.posts.get(0);
        TestHost.checkEq(body1.optString("batchId"), body2.optString("batchId"),
                "T14d 重启前后同 batchId（磁盘恢复非重封）");
        TestHost.checkEq(body1.optString("createdAt"), body2.optString("createdAt"),
                "T14e 重启前后同 createdAt");
        TestHost.checkEq(body1.optJSONArray("events").toString(),
                body2.optJSONArray("events").toString(),
                "T14f 重启前后字节级同 events（幂等契约跨进程存活）");
        TestHost.checkEq(0, s2.pendingEventCount(), "T14g 消费完成");
    }

    /* T15：【批次 B 独立性】批次 A 成功消费后新增日志 → 批次 B 新 batchId、
     * 事件只含新增（绝不混入 A 残留）——「批次 B 不会与批次 A 混合」。 */
    private static void t15_batchBIndependence() {
        env = new MemEnv();
        LogStore s = storeWith(20);
        FakeHttp http1 = new FakeHttp();
        LogUploader.Summary r1 = uploader(s, http1).runUpload(true);
        TestHost.checkEq(1, r1.acked, "T15a 批次 A 成功");
        String batchA = http1.posts.get(0).optString("batchId");
        // 批次 A 消费后新增日志
        for (int i = 0; i < 7; i++) s.appendEvent(ev());
        s.awaitIoForJvm();
        FakeHttp http2 = new FakeHttp();
        LogUploader.Summary r2 = uploader(s, http2).runUpload(true);
        TestHost.checkEq(1, r2.acked, "T15b 批次 B 成功");
        JSONObject bodyB = http2.posts.get(0);
        TestHost.check(!bodyB.optString("batchId").equals(batchA), "T15c 批次 B 新 batchId");
        org.json.JSONArray evB = bodyB.optJSONArray("events");
        int realB = 0;
        for (int i = 0; evB != null && i < evB.length(); i++) {
            if (!"STATS".equals(evB.optJSONObject(i).optString("type", ""))) realB++;
        }
        TestHost.checkEq(7, realB, "T15d 批次 B 真实事件只含新增 7 条（另有 STATS 快照注入）");
        TestHost.checkEq(0, s.pendingEventCount(), "T15e 全部消费");
    }

    /* T16：【自动 24h/3 批粒度闸】200 条积压（5 批）自动排空——第 3 批成功
     * 后即停（autoEligible 只挡 run 起点，v7.9.8b 前单次运行可越 3 批）；
     * 窗口滑动（旧时间戳出窗）后下一次自动运行续传剩余。 */
    private static void t16_autoInRunQuotaCap() {
        env = new MemEnv();
        LogStore s = storeWith(200);
        FakeHttp http = new FakeHttp();
        LogUploader.Summary r = uploader(s, http).runUpload(false); // 自动路径
        TestHost.checkEq(3, r.acked, "T16a 200 条 5 批——自动路径只成功 3 批即停（24h/3）");
        TestHost.checkEq(3, http.posts.size(), "T16b 第 4/5 批未发（批粒度闸）");
        TestHost.checkEq(3, s.autoOkCount24h(System.currentTimeMillis()), "T16c 自动账本=3");
        int remaining = s.pendingEventCount();
        TestHost.check(remaining > 0, "T16d 剩余事件保留待窗口滑动（=" + remaining + "）");
        // 窗口滑动：手动把自动账本时间戳回写为 25h 前（模拟时间流逝出窗）
        try {
            org.json.JSONArray old = new org.json.JSONArray();
            long t25h = System.currentTimeMillis() - 25L * 3600 * 1000;
            old.put(t25h); old.put(t25h); old.put(t25h);
            java.io.File f = new java.io.File(new java.io.File(env.dir, "log-upload"), "auto-ok-times.json");
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(old.toString().getBytes("UTF-8"));
            fo.close();
        } catch (Throwable ignored) {
        }
        TestHost.checkEq(0, s.autoOkCount24h(System.currentTimeMillis()), "T16e 窗口滑动后额度恢复");
        FakeHttp http2 = new FakeHttp();
        LogUploader.Summary r2 = uploader(s, http2).runUpload(false);
        TestHost.checkEq(2, r2.acked, "T16f 续传剩余 2 批（含首块 STATS）");
        TestHost.checkEq(0, s.pendingEventCount(), "T16g 排空完成");
        // 对照：手动路径无批粒度闸（随时全量可用）
        env = new MemEnv();
        LogStore s3 = storeWith(200);
        FakeHttp http3 = new FakeHttp();
        LogUploader.Summary r3 = uploader(s3, http3).runUpload(true); // 手动
        TestHost.checkEq(5, r3.acked, "T16h 手动路径不受 24h/3 闸（5 批全传）");
    }

    /* T17：【IDEMPOTENCY_CONFLICT 证据保留】终态冲突 → rejectHead 隔离：
     * 事件移入 rejected.json（code+error+events 全在——证据不丢）、pending
     * 移出（不再盲重试——重试必然再拒）、不计成功额度。 */
    private static void t17_idempotencyConflictEvidence() {
        env = new MemEnv();
        LogStore s = storeWith(12);
        FakeHttp http = new FakeHttp();
        http.responses.add(FakeHttp.terminal("IDEMPOTENCY_CONFLICT"));
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(0, r.acked, "T17a 冲突不算成功");
        TestHost.checkEq(1, r.rejected, "T17b 冲突块如实隔离计数");
        TestHost.checkEq("IDEMPOTENCY_CONFLICT", r.errKind, "T17c 冲突码透出（不吞码）");
        TestHost.checkEq(0, s.pendingEventCount(), "T17d 冲突块移出 pending（不再盲重试）");
        TestHost.checkEq(1, s.rejectedCount(), "T17e 检视记录在场");
        TestHost.checkEq(0, s.okCount24h(System.currentTimeMillis()), "T17f 冲突不耗成功额度");
        // 证据完整性：rejected.json 保留 code + error + 事件体
        try {
            java.io.File f = new java.io.File(new java.io.File(env.dir, "log-upload"), "rejected.json");
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            org.json.JSONArray rej = new org.json.JSONArray(new String(bo.toByteArray(), "UTF-8"));
            org.json.JSONObject rec = rej.optJSONObject(0);
            TestHost.check(rec != null && "IDEMPOTENCY_CONFLICT".equals(rec.optString("code")),
                    "T17g 证据保留：code 在场");
            TestHost.check(rec != null && "test".equals(rec.optString("error")),
                    "T17h 证据保留：error 文本在场");
            TestHost.check(rec != null && rec.optJSONArray("events") != null
                    && rec.optJSONArray("events").length() == 12,
                    "T17i 证据保留：12 条事件体完整隔离（可检视可追溯）");
        } catch (Throwable t) {
            TestHost.check(false, "T17g 证据读取异常: " + t);
        }
        // 后续运行不再重试冲突块（pending 已空——零 POST）
        FakeHttp http2 = new FakeHttp();
        LogUploader.Summary r2 = uploader(s, http2).runUpload(true);
        TestHost.checkEq(0, http2.posts.size(), "T17j 冲突块不再盲重试（零 POST）");
    }

    /* T18：【额度文件损坏防御】ok-times.json / auto-ok-times.json 写入垃圾
     * → 计数归零（不崩溃）、上传不受阻、recordOk 重写自愈。 */
    private static void t18_corruptedQuotaFiles() {
        env = new MemEnv();
        LogStore s = storeWith(5);
        try {
            java.io.File d = new java.io.File(env.dir, "log-upload");
            java.io.FileOutputStream o1 = new java.io.FileOutputStream(new java.io.File(d, "ok-times.json"));
            o1.write("{corrupt!!!".getBytes("UTF-8"));
            o1.close();
            java.io.FileOutputStream o2 = new java.io.FileOutputStream(new java.io.File(d, "auto-ok-times.json"));
            o2.write("not-json-at-all".getBytes("UTF-8"));
            o2.close();
        } catch (Throwable ignored) {
        }
        TestHost.checkEq(0, s.okCount24h(System.currentTimeMillis()), "T18a 损坏 ok-times 读作零（不崩溃）");
        TestHost.checkEq(0, s.autoOkCount24h(System.currentTimeMillis()), "T18b 损坏 auto-ok-times 读作零");
        FakeHttp http = new FakeHttp();
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(1, r.acked, "T18c 损坏额度文件不阻断上传");
        s.recordOk(System.currentTimeMillis(), false);
        TestHost.checkEq(1, s.autoOkCount24h(System.currentTimeMillis()),
                "T18d recordOk 重写自愈（损坏内容被覆盖）");
        TestHost.checkEq(2, s.okCount24h(System.currentTimeMillis()),
                "T18e 全部账本同样自愈（手动 1 + 自动 1）");
    }
}
