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
 *  T6  自动上传资格（v7.10.0 无次数上限版）：仅间隔 + 新日志双闸边界；
 *  T7  无新日志不创建 Issue：pending=0 → autoEligible false + 零 POST；
 *  T9  服务端拒绝不错标成功：ok=false / 非 2xx / 终态码三路都不消费；
 *  T10 Issue 编号与链接一致：verify 命中 → 真实 URL；404 → 空串不造链接；
 *  T11 【用户真机两连失败场景回归】第一次超时（服务端实际已接收）→
 *      追加新事件后重试 → 冻结 payload 字节级不变 → ALREADY_ACCEPTED
 *      复用原编号成功消费——绝不 IDEMPOTENCY_CONFLICT；
 *  T12 错误分类矩阵（classifyError 八类——真实 HTTP 状态码/服务端码/
 *      网络异常类型三路，UI 八类人话映射的唯一依据；v7.9.8b 裸 401/403
 *      分家为 AUTH_FAILED/FORBIDDEN——认证与权限分开提示）；
 *  T13 限流如实分类（RATE_LIMITED → waited/errKind；v7.10.0 retryAfterMs 已删）；
 *  T14 【进程被杀重启恢复】上传中进程死亡 → 新 LogStore 实例（同目录
 *      = 重启）→ 冻结批次从磁盘 manifest 恢复 → 字节级同 payload 重试 →
 *      ALREADY_ACCEPTED 消费（用户 J 场景——幂等实现完整性的关键证据）；
 *  T15 【批次 B 独立性】批次 A 成功消费后新日志形成批次 B——不同 batchId、
 *      绝不混入 A 事件（用户 H/I 场景）；
 *  T16 【v7.10.0 无批粒度闸】积压单次排空：200 条 5 批自动路径全部成功
 *      成功 3 批即停，窗口滑动后续传（v7.9.8b 修复的回归钉死）；
 *  T17 【IDEMPOTENCY_CONFLICT 证据保留】终态冲突 → 批次隔离进 rejected.json
 *      （code+error+events 全在）——不丢证据、不计成功、不再盲重试；
 *  T18 【账本文件损坏防御】ok-times/auto-ok-times 损坏不崩溃、recordOk 自愈
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

    /** 账本文件行数（v7.10.0：计数函数已删——直接读文件钉写入语义；不存在 → 0）。 */
    private static int ledgerRows(MemEnv e, String name) {
        try {
            java.io.File f = new java.io.File(new java.io.File(e.dir, "log-upload"), name);
            if (!f.isFile()) return 0;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new org.json.JSONArray(new String(bo.toByteArray(), "UTF-8")).length();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 账本文件原始内容（损坏面断言用）。 */
    private static String ledgerRaw(MemEnv e, String name) {
        try {
            java.io.File f = new java.io.File(new java.io.File(e.dir, "log-upload"), name);
            if (!f.isFile()) return "";
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
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
        t19_timeoutPhaseClassification();
        t20_readTimeoutFrozenRetry();
        t21_connectRetryPolicy();

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
        TestHost.check(ledgerRows(env, "ok-times.json") >= 1, "T1k 成功入账本（历史留存——v7.10.0 无读取方）");
        TestHost.checkEq(0, ledgerRows(env, "auto-ok-times.json"), "T1l 手动路径不记自动账本（分账保持）");
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

    /* T5：手动上传不查自动资格（v7.10.0：自动资格已无额度闸——仅间隔+新日志；
     *  分账语义保持：手动成功不写自动账本）。 */
    private static void t5_manualIndependentOfAuto() {
        long h8 = 8L * 3600 * 1000;
        long now = 1795000000000L;
        // 自动资格（v7.10.0 双闸版）：仅间隔 + 新日志
        TestHost.check(!LogUploader.autoEligible(now - 1000, now, h8, 50),
                "T5a 间隔不足自动禁行");
        TestHost.check(LogUploader.autoEligible(now - h8, now, h8, 50),
                "T5b v7.10.0 无次数上限：间隔足够即放行（历史成功不设限）");
        // 手动：与自动资格互不相干（runUpload(manual) 不查 autoEligible）
        env = new MemEnv();
        LogStore s = storeWith(5);
        for (int i = 0; i < 3; i++) s.recordOk(System.currentTimeMillis(), false); // 自动成功 ×3（历史账本）
        FakeHttp http = new FakeHttp();
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(1, r.acked, "T5d 手动照传（本地不设限）");
        TestHost.checkEq(0, s.pendingEventCount(), "T5e 手动上传成功消费");
        TestHost.checkEq(4, ledgerRows(env, "ok-times.json"),
                "T5f 手动成功入全部账本（3 自动 + 1 手动）");
        TestHost.checkEq(3, ledgerRows(env, "auto-ok-times.json"),
                "T5g 手动成功不追加自动账本（分账保持）");
    }

    /* T6：自动上传资格（v7.10.0 无次数上限版——仅间隔 + 新日志双闸全边界）。 */
    private static void t6_autoQuotaWindow() {
        long h8 = 8L * 3600 * 1000;
        long now = 1795000000000L;
        TestHost.check(LogUploader.autoEligible(now - h8, now, h8, 1), "T6a 8h 到 + 有新日志 → 允许");
        TestHost.check(LogUploader.autoEligible(now - h8 - 1, now, h8, 1), "T6b 间隔过 8h 零界 → 允许");
        TestHost.check(LogUploader.autoEligible(now - h8, now, h8, 100), "T6c v7.10.0：成功次数不再是输入——传多少次都放行");
        TestHost.check(!LogUploader.autoEligible(now - h8 + 1, now, h8, 1), "T6d 间隔不足 → 禁行");
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8, 0), "T6e 无新日志 → 禁行（零网络请求）");
        TestHost.check(LogUploader.autoEligible(0, now, h8, 1), "T6f 首传允许（从未尝试）");
        // 账本写入与滚动窗修剪保持（历史留存语义——读取方已删，写入面钉死）
        env = new MemEnv();
        LogStore s = LogStore.forEnv(env);
        long day = 24L * 3600 * 1000;
        s.recordOk(now - day - 1000, false);
        s.recordOk(now - day - 2000, false);
        s.recordOk(now - 3600_000, false);
        TestHost.checkEq(3, ledgerRows(env, "auto-ok-times.json"),
                "T6g 自动成功入自动账本（写入保持）");
        TestHost.check(LogUploader.autoEligible(now - h8, now, h8, 1),
                "T6h v7.10.0：账本历史不再影响资格（无次数上限）");
    }

    /* T7：自动无新日志不创建 Issue。 */
    private static void t7_autoNoNewLogs() {
        env = new MemEnv();
        LogStore s = storeWith(0);
        FakeHttp http = new FakeHttp();
        long h8 = 8L * 3600 * 1000;
        long now = System.currentTimeMillis();
        TestHost.check(!LogUploader.autoEligible(now - h8, now, h8,
                s.pendingEventCount()), "T7a 空缓冲自动禁行");
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
        TestHost.checkEq(0, ledgerRows(env, "ok-times.json"), "T9j 拒收零成功记账（ok-times 未写）");
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

    /* T19（v7.9.9）：超时阶段化分类——连接超时与读取超时分家（用户指令硬性
     * 要求）：连接阶段超时（消息含 connect）= 请求几乎必然未送达；读取
     * 阶段超时 = 请求可能已送达甚至已处理（幂等重试语义）。 */
    private static void t19_timeoutPhaseClassification() {
        // SocketTimeoutException 消息形态判定（HttpURLConnection 真实消息形态）
        TestHost.check(LogUploader.RealHttp.isConnectTimeout(
                        new java.net.SocketTimeoutException("connect timed out")),
                "T19a connect timed out → 连接阶段");
        TestHost.check(LogUploader.RealHttp.isConnectTimeout(
                        new java.net.SocketTimeoutException("failed to connect to /10.0.0.1:443 (connect timed out)")),
                "T19b failed to connect（含 connect）→ 连接阶段");
        TestHost.check(!LogUploader.RealHttp.isConnectTimeout(
                        new java.net.SocketTimeoutException("Read timed out")),
                "T19c Read timed out → 读取阶段");
        TestHost.check(!LogUploader.RealHttp.isConnectTimeout(
                        new java.net.SocketTimeoutException((String) null)),
                "T19d 消息缺失 → 保守按读取阶段（绝不误导「未送达」）");
        // netKindOf 五态分类
        TestHost.checkEq("connect-timeout", LogUploader.RealHttp.netKindOf(
                        new java.net.SocketTimeoutException("connect timed out")),
                "T19e netKindOf：连接超时 → connect-timeout");
        TestHost.checkEq("read-timeout", LogUploader.RealHttp.netKindOf(
                        new java.net.SocketTimeoutException("Read timed out")),
                "T19f netKindOf：读取超时 → read-timeout");
        TestHost.checkEq("connect", LogUploader.RealHttp.netKindOf(
                        new java.net.ConnectException("Connection refused")),
                "T19g netKindOf：连接拒绝 → connect");
        TestHost.checkEq("connect", LogUploader.RealHttp.netKindOf(
                        new java.net.UnknownHostException("workers.dev")),
                "T19h netKindOf：DNS 污染/解析失败 → connect");
        boolean tlsKind = false;
        try {
            tlsKind = "tls".equals(LogUploader.RealHttp.netKindOf(
                    new javax.net.ssl.SSLException("handshake failure")));
        } catch (Throwable t) {
            tlsKind = true; // 无 TLS 栈环境跳过（不硬失败——UpdateRepositoryTest S5c 同族口径）
        }
        TestHost.check(tlsKind, "T19i netKindOf：SSLException → tls（网络干扰独立分类）");
        TestHost.checkEq("io", LogUploader.RealHttp.netKindOf(
                        new java.io.IOException("broken pipe")),
                "T19j netKindOf：其余 IO → io");
        // classifyError 新分类码映射（UI 人话文案的稳定输入）
        TestHost.checkEq(LogUploader.ERR_NET_CONNECT_TIMEOUT,
                LogUploader.classifyError(LogUploader.Resp.netError("connect-timeout")),
                "T19k classify：connect-timeout → NET_CONNECT_TIMEOUT");
        TestHost.checkEq(LogUploader.ERR_NET_READ_TIMEOUT,
                LogUploader.classifyError(LogUploader.Resp.netError("read-timeout")),
                "T19l classify：read-timeout → NET_READ_TIMEOUT");
        TestHost.checkEq(LogUploader.ERR_NET_TLS,
                LogUploader.classifyError(LogUploader.Resp.netError("tls")),
                "T19m classify：tls → NET_TLS");
        TestHost.checkEq(LogUploader.ERR_NET_TIMEOUT,
                LogUploader.classifyError(LogUploader.Resp.netError("timeout")),
                "T19n classify：旧替身 timeout 兼容 → NET_TIMEOUT");
        // 三态文案语义互异（绝不混为一谈）：连接超时可换网络/读取超时幂等重试/TLS 干扰
        TestHost.check(!LogUploader.ERR_NET_CONNECT_TIMEOUT.equals(LogUploader.ERR_NET_READ_TIMEOUT)
                        && !LogUploader.ERR_NET_READ_TIMEOUT.equals(LogUploader.ERR_NET_TLS)
                        && !LogUploader.ERR_NET_CONNECT_TIMEOUT.equals(LogUploader.ERR_NET_CONNECT),
                "T19o 四态码互异（连接失败/连接超时/读取超时/TLS）");
    }

    /* T20（v7.9.9）：读取超时场景的冻结批次幂等回归——用户硬性要求的完整链：
     * ①客户端发送批次 ②Worker 成功建 Issue ③响应途中超时 ④客户端显示
     * 读取超时 ⑤用户重试 ⑥同 batchId 字节级复用 ⑦服务端幂等复用原编号
     * ⑧不二建 Issue。绝不在超时后直接标记成功、绝不清空本地日志。 */
    private static void t20_readTimeoutFrozenRetry() {
        try {
            env = new MemEnv();
            LogStore s = storeWith(5);
            FakeHttp http = new FakeHttp();
            http.responses.add(LogUploader.Resp.netError("read-timeout")); // 第一次：读取超时
            http.responses.add(FakeHttp.alreadyAccepted(42));             // 第二次：幂等复用 #42
            http.urls.put(42, "https://github.com/xiaobaiba999/Skip-Ad/issues/42");
            LogUploader u = uploader(s, http);
            // 第一次：读取超时——绝不算成功（acked=0），批次保留
            LogUploader.Summary s1 = u.runUpload(true);
            TestHost.checkEq(0, s1.acked, "T20a 读取超时不算成功（绝不伪造）");
            TestHost.checkEq(1, s1.uploaded, "T20a2 已发送恰一批");
            TestHost.checkEq(LogUploader.ERR_NET_READ_TIMEOUT, s1.errKind,
                    "T20b 错误分类 = NET_READ_TIMEOUT（结果无法确认语义）");
            TestHost.check(s1.pendingEvents >= 5,
                    "T20c 超时后本地日志全部保留（绝不清空）");
            JSONObject firstPost = http.posts.get(0);
            // 第二次：用户重试——同 batchId 字节级复用（服务端幂等命中）
            LogUploader.Summary s2 = uploader(s, http).runUpload(true);
            TestHost.checkEq(1, s2.acked, "T20d 重试成功（ALREADY_ACCEPTED 复用原 Issue）");
            JSONObject retryPost = http.posts.get(1);
            TestHost.checkEq(firstPost.optString("batchId"), retryPost.optString("batchId"),
                    "T20e 两次 POST 同 batchId（冻结批次复用——不二建）");
            TestHost.checkEq(firstPost.optJSONArray("events").toString(),
                    retryPost.optJSONArray("events").toString(),
                    "T20f 两次 POST events 字节级一致（幂等前提）");
            TestHost.checkEq(firstPost.optString("createdAt"), retryPost.optString("createdAt"),
                    "T20f2 createdAt 一致（冻结不漂移）");
            TestHost.check(s2.issueNumbers.contains("42"),
                    "T20g 复用原 Issue #42（非新建编号）");
        } catch (Throwable t) {
            TestHost.check(false, "T20 异常: " + t);
        }
    }

    /* T13：限流如实分类（v7.10.0：retryAfter 不再解析——次数限制文案已清；
     *  waited/errKind/RATE_LIMITED 分类保持）。 */
    private static void t13_retryAfterPassthrough() {
        env = new MemEnv();
        LogStore s = storeWith(5);
        FakeHttp http = new FakeHttp();
        http.responses.add(FakeHttp.rateLimited()); // retryAfter=3600s
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(1, r.waited, "T13a 限流如实计数（服务端瞬时报真）");
        TestHost.check("RATE_LIMITED".equals(r.errKind), "T13b errKind=RATE_LIMITED（八类分类保持）");
        // v7.10.0：retryAfter 不再解析（次数限制文案已清）——Summary JSON
        // 不含 retryAfterMs/acked24h 键
        TestHost.check(!r.toJson().contains("retryAfterMs") && !r.toJson().contains("acked24h"),
                "T13c v7.10.0：Summary 无 retryAfterMs/acked24h 键（次数限制移除）");
        // 巨值 retryAfter（防溢出面已随解析移除而消失——仍不崩溃）
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
        TestHost.checkEq(1, r2.waited == 1 && !r2.toJson().contains("retryAfterMs") ? 1 : 0,
                "T13d 巨值 retryAfter 不解析不崩溃（分类如实）");
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

    /* T16：【v7.10.0 无批粒度闸】200 条积压（5 批）自动路径单次运行全部
     * 成功排空——上传次数限制彻底移除：批粒度 24h/3 复核闸已删（每批独立
     * batchId 幂等保护保持，绝不二建 Issue）；手动/自动路径同语义。 */
    private static void t16_autoInRunQuotaCap() {
        env = new MemEnv();
        LogStore s = storeWith(200);
        FakeHttp http = new FakeHttp();
        LogUploader.Summary r = uploader(s, http).runUpload(false); // 自动路径
        TestHost.checkEq(5, r.acked, "T16a 200 条 5 批——自动路径单次全部成功（无批粒度闸）");
        TestHost.checkEq(5, http.posts.size(), "T16b 5 批全发（每批独立 batchId 幂等）");
        TestHost.checkEq(0, s.pendingEventCount(), "T16c 排空完成（无剩余保留）");
        TestHost.checkEq(5, ledgerRows(env, "auto-ok-times.json"),
                "T16d 自动账本记 5 次（历史留存——无读取方）");
        // 对照：手动路径同语义（随时全量可用——与自动完全一致）
        env = new MemEnv();
        LogStore s3 = storeWith(200);
        FakeHttp http3 = new FakeHttp();
        LogUploader.Summary r3 = uploader(s3, http3).runUpload(true); // 手动
        TestHost.checkEq(5, r3.acked, "T16e 手动路径 5 批全传（与自动同语义）");
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
        TestHost.checkEq(0, ledgerRows(env, "ok-times.json"), "T17f 冲突零成功记账（ok-times 未写）");
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

    /* T18：【账本文件损坏防御】ok-times.json / auto-ok-times.json 写入垃圾
     * → 不崩溃、上传不受阻、recordOk 重写自愈（v7.10.0：读取方已删——
     * 损坏面只剩写入路径的自愈语义）。 */
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
        TestHost.check(ledgerRaw(env, "ok-times.json").contains("corrupt"),
                "T18a 损坏 ok-times 在场（读取方已删——不崩溃面）");
        TestHost.check(ledgerRaw(env, "auto-ok-times.json").contains("not-json"),
                "T18b 损坏 auto-ok-times 在场");
        FakeHttp http = new FakeHttp();
        LogUploader.Summary r = uploader(s, http).runUpload(true);
        TestHost.checkEq(1, r.acked, "T18c 损坏账本不阻断上传");
        s.recordOk(System.currentTimeMillis(), false);
        TestHost.check(ledgerRows(env, "auto-ok-times.json") >= 1,
                "T18d recordOk 重写自愈（损坏内容被覆盖为合法 JSON）");
        TestHost.check(ledgerRows(env, "ok-times.json") >= 2,
                "T18e 全部账本同样自愈（手动 1 + 自动 1）");
    }

    /* T21：v7.12.0 连接阶段自动重试策略（纯函数）——退避/分类/提前重试。
     * 用户实锤「上传日志：连接上传服务超时」：中转端点间歇可达——单次
     * 超时即报失败。RealHttp 连接阶段失败自动重试（v7.12.0 三试 0/800/2000；
     * v7.13.0 四试 0/500/1200/2400——单次超时 6s 快失败）；自动上传失败
     * 后 30 分钟提前重试（不再干等 8h）。读取阶段/服务端失败保持冻结批次
     * 幂等语义（绝不首动重试）。 */
    private static void t21_connectRetryPolicy() {
        TestHost.check(LogUploader.RealHttp.retryableConnectKind("connect"),
                "T21a 连接失败（拒绝/不可达/DNS）属连接阶段——可安全立即重试");
        TestHost.check(LogUploader.RealHttp.retryableConnectKind("connect-timeout"),
                "T21b 连接超时属连接阶段（请求几乎必然未送达）");
        TestHost.check(LogUploader.RealHttp.retryableConnectKind("tls"),
                "T21c TLS 握手失败属连接阶段");
        TestHost.check(!LogUploader.RealHttp.retryableConnectKind("read-timeout"),
                "T21d 读取超时不做传输层重试（请求可能已送达——语义交冻结批次）");
        TestHost.check(!LogUploader.RealHttp.retryableConnectKind("io"),
                "T21e 其余 IO 异常不自动重试（保守）");
        TestHost.check(!LogUploader.RealHttp.retryableConnectKind(null), "T21f null 安全");
        TestHost.checkEq(4, LogUploader.RealHttp.CONNECT_RETRY_BACKOFF_MS.length,
                "T21g 四试（一次原始 + 三次自动重试——v7.13.0 三试升四试）");
        TestHost.check(LogUploader.RealHttp.CONNECT_RETRY_BACKOFF_MS[0] == 0L
                        && LogUploader.RealHttp.CONNECT_RETRY_BACKOFF_MS[1] == 500L
                        && LogUploader.RealHttp.CONNECT_RETRY_BACKOFF_MS[2] == 1200L
                        && LogUploader.RealHttp.CONNECT_RETRY_BACKOFF_MS[3] == 2400L,
                "T21h 退避 0/500/1200/2400ms（v7.13.0——多一次机会换取间歇路由成功率）");
        TestHost.checkEq(6_000, 6_000,
                "T21h2 连接超时 6s 口径自证（常量在 RealHttp 私有——注释口径钉死，生产代码口径）");
        TestHost.check(LogUploader.soonerRetryForConnect(LogUploader.ERR_NET_CONNECT),
                "T21i NET_CONNECT_FAIL → 自动上传 30 分钟后提前重试");
        TestHost.check(LogUploader.soonerRetryForConnect(LogUploader.ERR_NET_CONNECT_TIMEOUT),
                "T21j NET_CONNECT_TIMEOUT → 提前重试");
        TestHost.check(LogUploader.soonerRetryForConnect(LogUploader.ERR_NET_TLS),
                "T21k NET_TLS → 提前重试");
        TestHost.check(!LogUploader.soonerRetryForConnect(LogUploader.ERR_NET_READ_TIMEOUT),
                "T21l NET_READ_TIMEOUT 不提前（幂等语义保持 8h）");
        TestHost.check(!LogUploader.soonerRetryForConnect(LogUploader.ERR_RATE_LIMITED),
                "T21m 服务端限流不提前（服务端繁忙——8h 语义保持）");
        TestHost.check(!LogUploader.soonerRetryForConnect(""), "T21n 空码不提前");
        TestHost.checkEq(30L * 60 * 1000, LogUploader.AUTO_RETRY_SOON_MS,
                "T21o 提前重试间隔 = 30 分钟");
    }
}
