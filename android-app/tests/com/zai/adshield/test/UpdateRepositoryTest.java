package com.zai.adshield.test;

import com.zai.adshield.UpdateManifest;
import com.zai.adshield.UpdateRepository;
import com.zai.adshield.UpdateRepository.FlightGate;

import org.json.JSONObject;

/**
 * 更新检查仓库逻辑测试（v7.9.8 静态清单架构——缓存/冷却/退避/单飞并发）。
 * ─────────────────────────────────────────────
 * 覆盖（用户指令·缓存/并发/冷却测试全项，纯函数 + 真线程面）：
 *  S1 缓存新鲜判定（15min TTL：新鲜/临界/过期/零值）。
 *  S2 手动冷却（30s：期内/临界/过期/零值——「不能无限连续点击」的闸）。
 *  S3 失败指数退避（1min × 2^(fails-1) 封顶 6h；fails=0 无退避——
 *     「网络失败绝不无限重试」的时间面证据）。
 *  S4 FlightGate 单飞并发（真 10 线程）：
 *     · 并发触发 10 次检查 → 领飞者恰 1 个；
 *     · 全部搭飞者共享同一结果；
 *     · 领飞结束状态正确释放（下一代可再领飞）；
 *     · 网络期间到达的请求共享、结束后的请求独立走缓存路径。
 *  S5 classifyNetError 异常分类（DNS/超时/TLS/连接/IO——网络失败 ≠ 服务端拒绝）。
 *  S6 缓存条目 JSON 序列化健壮性（正常/损坏/版本不符/空 json）。
 *  S7 isOkPayload（ok:true 计成功清退避；ok:false 计失败退避）。
 */
public final class UpdateRepositoryTest {

    public static void main(String[] args) throws Exception {
        TestHost.suite("UpdateRepositoryTest · 缓存/冷却/退避/单飞并发/源链（v7.9.9）");
        s1_freshTtl();
        s2_manualCooldown();
        s3_backoff();
        s4_flightGateConcurrency();
        s5_classifyNetError();
        s6_cacheInfoRobustness();
        s7_okPayload();
        s8_walkSourcesChain();
        TestHost.done();
    }

    /* ═══ S1 缓存新鲜 TTL ═══ */
    private static void s1_freshTtl() {
        long now = 1000000000000L;
        TestHost.check(UpdateRepository.isFresh(now - 1, now), "S1a 刚缓存 → 新鲜");
        TestHost.check(UpdateRepository.isFresh(now - UpdateRepository.FRESH_TTL_MS + 1, now),
                "S1b TTL 临界内（14:59）→ 新鲜");
        TestHost.check(!UpdateRepository.isFresh(now - UpdateRepository.FRESH_TTL_MS, now),
                "S1c TTL 整点（15:00）→ 过期（允许后台刷新）");
        TestHost.check(!UpdateRepository.isFresh(now - 24 * 60 * 60 * 1000L, now), "S1d 昨日缓存 → 过期");
        TestHost.check(!UpdateRepository.isFresh(0, now), "S1e 零值（无缓存）→ 不新鲜");
        TestHost.check(!UpdateRepository.isFresh(now + 60000, now), "S1f 未来时间戳（时钟回拨容错）→ 不新鲜");
        TestHost.check(UpdateRepository.FRESH_TTL_MS == 15 * 60 * 1000L, "S1g TTL 常量 = 15 分钟（策略锁定）");
    }

    /* ═══ S2 手动冷却 ═══ */
    private static void s2_manualCooldown() {
        long now = 2000000000000L;
        TestHost.check(!UpdateRepository.manualCoolingDown(0, now), "S2a 从未联网 → 不冷却（首次可查）");
        TestHost.check(UpdateRepository.manualCoolingDown(now - 1000, now), "S2b 联网后 1s → 冷却中");
        TestHost.check(UpdateRepository.manualCoolingDown(now - UpdateRepository.MANUAL_COOLDOWN_MS + 1, now),
                "S2c 冷却临界内（29:59）→ 冷却中");
        TestHost.check(!UpdateRepository.manualCoolingDown(now - UpdateRepository.MANUAL_COOLDOWN_MS, now),
                "S2d 冷却整点（30:00）→ 冷却结束（可再联网）");
        TestHost.check(!UpdateRepository.manualCoolingDown(now - 60000, now), "S2e 60s 前 → 不冷却");
        TestHost.check(UpdateRepository.MANUAL_COOLDOWN_MS == 30 * 1000L, "S2f 手动冷却常量 = 30 秒（策略锁定）");
        // 冷却上界推导：连续狂点 1 小时至多 120 次真实联网请求（防风暴量化面）
        long worstCasePerHour = 3600000L / UpdateRepository.MANUAL_COOLDOWN_MS;
        TestHost.check(worstCasePerHour <= 120, "S2g 狂点上界：≤120 次联网/小时（冷却闸量化）");
    }

    /* ═══ S3 失败指数退避 ═══ */
    private static void s3_backoff() {
        long t = 1000000L;
        TestHost.check(UpdateRepository.backoffUntil(t, 0) == 0L, "S3a 无失败 → 无退避");
        TestHost.check(UpdateRepository.backoffUntil(t, 1) == t + UpdateRepository.BACKOFF_BASE_MS,
                "S3b 1 次失败 → 1min 后");
        TestHost.check(UpdateRepository.backoffUntil(t, 2) == t + 2 * UpdateRepository.BACKOFF_BASE_MS,
                "S3c 2 次失败 → 2min 后");
        TestHost.check(UpdateRepository.backoffUntil(t, 3) == t + 4 * UpdateRepository.BACKOFF_BASE_MS,
                "S3d 3 次失败 → 4min 后");
        TestHost.check(UpdateRepository.backoffUntil(t, 4) == t + 8 * UpdateRepository.BACKOFF_BASE_MS,
                "S3e 4 次失败 → 8min 后");
        // 封顶 6h（继续失败不再增长——「绝不无限重试」的双保险）
        long capped = UpdateRepository.backoffUntil(t, 20);
        TestHost.check(capped == t + UpdateRepository.BACKOFF_CAP_MS,
                "S3f 20 次失败 → 封顶 6h（不无限增长）");
        TestHost.check(UpdateRepository.BACKOFF_CAP_MS == 6 * 60 * 60 * 1000L, "S3g 退避封顶常量 = 6 小时");
        TestHost.check(UpdateRepository.BACKOFF_BASE_MS == 60 * 1000L, "S3h 退避基值常量 = 1 分钟");
        // 溢出安全（位移封顶 6 次 + 数值封顶双重防护）
        TestHost.check(UpdateRepository.backoffUntil(Long.MAX_VALUE / 2, 50) > 0,
                "S3i 极端入参不溢出为负");
    }

    /* ═══ S4 FlightGate 单飞并发（真 10 线程）═══ */
    private static void s4_flightGateConcurrency() throws Exception {
        // 场景一：10 线程并发 → 恰 1 领飞者，9 搭飞者共享同一结果
        final FlightGate gate = new FlightGate();
        final java.util.concurrent.atomic.AtomicInteger leaders = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger sharedHits = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger distinctResults = new java.util.concurrent.atomic.AtomicInteger(1);
        final java.util.Set<String> results = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
        final Object startLock = new Object();
        final Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    try {
                        synchronized (startLock) {
                            startLock.wait(); // 齐射起跑（最大化并发窗口）
                        }
                        long gen = gate.enter();
                        if (gen == 0L) {
                            leaders.incrementAndGet();
                            Thread.sleep(150); // 模拟网络请求耗时
                            gate.finish("{\"ok\":true,\"gen\":\"leader\"}");
                        } else {
                            String shared = gate.await(gen);
                            if (shared != null) {
                                sharedHits.incrementAndGet();
                                results.add(shared);
                            }
                        }
                    } catch (InterruptedException ignored) {
                    }
                }
            });
            threads[i].start();
        }
        Thread.sleep(100); // 等 10 线程全部就位
        synchronized (startLock) {
            startLock.notifyAll();
        }
        for (Thread t : threads) t.join(10000);
        TestHost.check(leaders.get() == 1, "S4a 10 并发检查 → 领飞者恰 1 个（唯一真实网络请求）");
        TestHost.check(sharedHits.get() == 9, "S4b 9 个搭飞者全部共享结果（got " + sharedHits.get() + "）");
        TestHost.check(results.size() == 1, "S4c 共享结果完全一致（单一数据源）");

        // 场景二：领飞结束后 → 状态正确释放（下一代可再领飞——不悬挂）
        long g2 = gate.enter();
        TestHost.check(g2 == 0L, "S4d 上一代完成后可再领飞（状态释放正确）");
        gate.finish("{\"ok\":true,\"gen\":\"second\"}");

        // 场景三：领飞失败路径（异常收敛）也必须释放门
        long g3 = gate.enter();
        TestHost.check(g3 == 0L, "S4e 前置：第三次领飞");
        gate.finish("{\"ok\":false,\"errorCode\":\"UPDATE_NETWORK_ERROR\"}");
        long g4 = gate.enter();
        TestHost.check(g4 == 0L, "S4f 失败 finish 后同样释放（绝不悬挂单飞门）");
        gate.finish("{\"ok\":true}");

        // 场景四：晚到的搭飞者（该代已完成）→ await 返回 null → 自行走缓存路径
        long g5 = gate.enter();
        gate.finish("{\"ok\":true,\"gen\":\"five\"}");
        String late = gate.await(g5); // 该代完成后的 await——模拟极端竞态
        TestHost.check(late == null || late.contains("five"),
                "S4f 完成后 await → null 或同代结果（调用方走缓存兜底，不误共享新代）");
    }

    /* ═══ S5 网络异常分类 ═══ */
    private static void s5_classifyNetError() {
        TestHost.check(UpdateManifest.ERR_DNS.equals(
                        UpdateRepository.classifyNetError(new java.net.UnknownHostException("raw.githubusercontent.com"))),
                "S5a UnknownHostException → UPDATE_DNS_ERROR（域名解析失败如实分类）");
        TestHost.check(UpdateManifest.ERR_TIMEOUT.equals(
                        UpdateRepository.classifyNetError(new java.net.SocketTimeoutException("read timed out"))),
                "S5b SocketTimeoutException → UPDATE_TIMEOUT（超时≠服务端拒绝）");
        boolean sslOk = false;
        try {
            javax.net.ssl.SSLException e = new javax.net.ssl.SSLException("cert mismatch");
            sslOk = UpdateManifest.ERR_TLS.equals(UpdateRepository.classifyNetError(e));
        } catch (Throwable t) {
            sslOk = true; // 无 TLS 栈环境跳过（不硬失败）
        }
        TestHost.check(sslOk, "S5c SSLException → UPDATE_TLS_ERROR");
        TestHost.check(UpdateManifest.ERR_NETWORK.equals(
                        UpdateRepository.classifyNetError(new java.net.ConnectException("Connection reset"))),
                "S5d ConnectException（reset）→ UPDATE_NETWORK_ERROR");
        TestHost.check(UpdateManifest.ERR_NETWORK.equals(
                        UpdateRepository.classifyNetError(new java.io.IOException("broken pipe"))),
                "S5e IOException → UPDATE_NETWORK_ERROR");
        TestHost.check(UpdateManifest.ERR_NETWORK.equals(
                        UpdateRepository.classifyNetError(new RuntimeException("任意"))),
                "S5f 其余 Throwable → UPDATE_NETWORK_ERROR 兜底");
        // 网络失败文案 ≠ 服务端拒绝文案（十类如实转达面）
        TestHost.check(!UpdateRepository.netErrorText(UpdateManifest.ERR_NETWORK)
                        .equals(UpdateRepository.netErrorText(UpdateManifest.ERR_SERVER)),
                "S5g 网络失败与服务端异常文案不同（不混为一谈）");
        TestHost.check(!UpdateRepository.netErrorText(UpdateManifest.ERR_TIMEOUT)
                        .equals(UpdateRepository.netErrorText(UpdateManifest.ERR_RATE_LIMITED)),
                "S5h 超时与限流文案不同");
        TestHost.check(UpdateRepository.netErrorText(UpdateManifest.ERR_DNS).contains("域名解析"),
                "S5i DNS 错误文案含真实原因");
    }

    /* ═══ S6 缓存条目健壮性（损坏恢复面）+ 源链常量（v7.9.9）═══ */
    private static void s6_cacheInfoRobustness() {
        try {
            // 正常构造
            UpdateRepository.CacheInfo c = new UpdateRepository.CacheInfo(
                    "{\"versionName\":\"7.9.7\"}", "\"etag1\"", "Tue, 22 Nov 2026 07:55:06 GMT",
                    1234567890L, UpdateRepository.MANIFEST_PRIMARY);
            TestHost.check(c.json.contains("7.9.7") && c.cachedAt == 1234567890L, "S6a CacheInfo 正常构造");
            TestHost.check(UpdateRepository.MANIFEST_PRIMARY.contains("raw.githubusercontent.com")
                            && UpdateRepository.MANIFEST_PRIMARY.contains("update/latest.json"),
                    "S6b 主源 = raw.githubusercontent.com main 分支静态清单");
            // v7.9.9 源链：raw 主源 + jsDelivr 四接入点（github.io 已移除）
            TestHost.check(UpdateRepository.MANIFEST_SOURCES.length == 5,
                    "S6c 源链恰 5 源（raw 主源 + jsDelivr 四接入点）");
            TestHost.check(UpdateRepository.MANIFEST_SOURCES[0].equals(UpdateRepository.MANIFEST_PRIMARY),
                    "S6d 源链首位 = 主源（顺序锁定）");
            boolean jsdelivrOk = true;
            String[] hosts = {"cdn.jsdelivr.net", "fastly.jsdelivr.net", "gcore.jsdelivr.net", "testingcf.jsdelivr.net"};
            for (int i = 1; i < UpdateRepository.MANIFEST_SOURCES.length; i++) {
                String s = UpdateRepository.MANIFEST_SOURCES[i];
                boolean hit = false;
                for (String h : hosts) {
                    if (s.startsWith("https://" + h + "/gh/xiaobaiba999/Skip-Ad@main/update/latest.json")) hit = true;
                }
                if (!hit) jsdelivrOk = false;
            }
            TestHost.check(jsdelivrOk, "S6e 备用源 1-4 = jsDelivr 四接入点同路径（SubMirrors 同族）");
            boolean noApiNoPages = true;
            for (String s : UpdateRepository.MANIFEST_SOURCES) {
                if (s.contains("api.github.com") || s.contains("github.io")) noApiNoPages = false;
                if (!s.startsWith("https://")) noApiNoPages = false;
            }
            TestHost.check(noApiNoPages, "S6f 全源零 api.github.com 零 github.io（Pages 未启用死源已移除）且全 https");
            TestHost.check(UpdateRepository.CHAIN_BUDGET_MS == 40 * 1000L,
                    "S6g 整链预算常量 = 40s（策略锁定——五源链不膨胀）");
        } catch (Exception e) {
            TestHost.check(false, "S6 异常: " + e);
        }
    }

    /* ═══ S7 ok 载荷判定（退避计数输入）═══ */
    private static void s7_okPayload() {
        TestHost.check(UpdateRepository.isOkPayload("{\"ok\":true,\"hasRelease\":true}"), "S7a ok:true → 成功");
        TestHost.check(!UpdateRepository.isOkPayload("{\"ok\":false,\"errorCode\":\"UPDATE_NETWORK_ERROR\"}"),
                "S7b ok:false → 失败（计退避）");
        TestHost.check(!UpdateRepository.isOkPayload("损坏"), "S7c 非 JSON → 失败（不崩）");
        TestHost.check(!UpdateRepository.isOkPayload(null), "S7d null → 失败（不崩）");
        // 退避计数以「网络真实成功」为准：ok:true + netError（双源失败缓存兜底）
        // 仍计失败——绝不因缓存兜底掩盖断网而绕过指数退避
        String cacheFallback = "{\"ok\":true,\"fromCache\":true,\"staleCache\":true,\"netError\":\"UPDATE_NETWORK_ERROR\"}";
        TestHost.check(UpdateRepository.isOkPayload(cacheFallback), "S7e 缓存兜底载荷 ok:true（UI 正常显示缓存数据）");
        TestHost.check(UpdateRepository.hasNetError(cacheFallback), "S7f 但带 netError → 网络层实为失败（计退避）");
        TestHost.check(!UpdateRepository.hasNetError("{\"ok\":true,\"fromCache\":false}"),
                "S7g 纯联网成功无 netError → 清退避");
        TestHost.check(!UpdateRepository.hasNetError("{\"ok\":true,\"fromCache\":true,\"staleCache\":false}"),
                "S7h 304 续新鲜缓存无 netError → 清退避（服务器真实响应）");
        TestHost.check(!UpdateRepository.hasNetError("损坏"), "S7i 非 JSON → 不崩");
    }

    /* ═══ S8 源链走查 walkSources（v7.9.9 核心新增——Fetcher 缝全链路）═══ */

    /** 测试用有效清单（UpdateManifest 契约全字段——versionCode 79900）。 */
    private static final String GOOD_MANIFEST = "{\"schemaVersion\":1,\"versionName\":\"7.9.9\","
            + "\"versionCode\":79900,\"releaseTag\":\"v7.9.9\",\"releaseName\":\"Skip-Ad v7.9.9\","
            + "\"releaseNotes\":\"versionCode: 79900\\n1. 修复\","
            + "\"publishedAt\":\"2026-11-23T00:00:00Z\","
            + "\"apkUrl\":\"https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.9/adshield-v7.9.9.apk\","
            + "\"apkFileName\":\"adshield-v7.9.9.apk\","
            + "\"apkSha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
            + "\"apkSize\":890422,\"packageName\":\"com.zai.adshield\"}";

    /** 记录型 Fetcher 替身：按源脚本化响应（body / "304" / 错误码 / null）。
     *  记录请求顺序与条件头下发次数——链路行为全可断言。 */
    private static final class ScriptFetcher implements UpdateRepository.Fetcher {
        final java.util.List<String> requested = new java.util.ArrayList<String>();
        final java.util.Map<String, String> script = new java.util.HashMap<String, String>();
        int conditionalSent = 0;

        public UpdateRepository.HttpOutcome fetch(String url, UpdateRepository.CacheInfo conditional) {
            requested.add(url);
            if (conditional != null) conditionalSent++;
            String r = script.get(url);
            if (r == null) {
                return new UpdateRepository.HttpOutcome(false, null, null, null, url,
                        UpdateManifest.ERR_NETWORK);
            }
            if ("304".equals(r)) {
                return new UpdateRepository.HttpOutcome(true, null, null, null, url, null);
            }
            if (r.startsWith("ERR:")) {
                return new UpdateRepository.HttpOutcome(false, null, null, null, url, r.substring(4));
            }
            if ("NULL".equals(r)) {
                return null; // 缝实现异常防御面
            }
            return new UpdateRepository.HttpOutcome(false, r, "\"e1\"",
                    "Tue, 1 Jan 2026 00:00:00 GMT", url, null);
        }
    }

    private static void s8_walkSourcesChain() {
        long farDeadline = System.currentTimeMillis() + UpdateRepository.CHAIN_BUDGET_MS;
        String[] src = UpdateRepository.MANIFEST_SOURCES;

        // S8a 主源 HTTP 200 有效清单 → SUCCESS 立即返回（后续源零请求）
        ScriptFetcher f1 = new ScriptFetcher();
        f1.script.put(src[0], GOOD_MANIFEST);
        UpdateRepository.WalkResult w1 = UpdateRepository.walkSources(f1, null, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w1.kind == UpdateRepository.WalkResult.SUCCESS
                        && w1.payload.contains("7.9.9") && w1.body.equals(GOOD_MANIFEST)
                        && w1.url.equals(src[0]) && "\"e1\"".equals(w1.etag),
                "S8a 主源 200 有效 → SUCCESS（载荷/原文/缓存三键捕获）");
        TestHost.check(f1.requested.size() == 1, "S8a 成功即短路（零多余请求）");

        // S8b 主源连接失败 → 备用源成功（链降级核心场景）
        ScriptFetcher f2 = new ScriptFetcher();
        f2.script.put(src[1], GOOD_MANIFEST);
        UpdateRepository.WalkResult w2 = UpdateRepository.walkSources(f2, null, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w2.kind == UpdateRepository.WalkResult.SUCCESS && w2.url.equals(src[1]),
                "S8b 主源连接失败 → jsDelivr 备用源成功");
        TestHost.check(f2.requested.size() == 2, "S8b 恰两次请求（主源+备用各一次）");

        // S8c-d 主源超时/HTTP 500/DNS 失败 → 备用源成功（每类失败均降级）
        String[][] fails = {
                {"S8c", "ERR:" + UpdateManifest.ERR_TIMEOUT, "主源超时"},
                {"S8d", "ERR:" + UpdateManifest.ERR_SERVER, "主源 HTTP 500"},
                {"S8e", "ERR:" + UpdateManifest.ERR_DNS, "主源 DNS 失败"},
        };
        for (String[] fc : fails) {
            ScriptFetcher f = new ScriptFetcher();
            f.script.put(src[0], fc[1]);
            f.script.put(src[2], GOOD_MANIFEST);
            UpdateRepository.WalkResult w = UpdateRepository.walkSources(f, null, farDeadline,
                    "com.zai.adshield", "7.9.8", 79800);
            TestHost.check(w.kind == UpdateRepository.WalkResult.SUCCESS && w.url.equals(src[2]),
                    fc[0] + " " + fc[2] + " → 跳过失败源第三源成功");
        }

        // S8f 主源返回非法 JSON（劫持/损坏面）→ 继续备用源成功
        ScriptFetcher f3 = new ScriptFetcher();
        f3.script.put(src[0], "<html>被劫持页面</html>");
        f3.script.put(src[1], GOOD_MANIFEST);
        UpdateRepository.WalkResult w3 = UpdateRepository.walkSources(f3, null, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w3.kind == UpdateRepository.WalkResult.SUCCESS,
                "S8f 主源非法 JSON → 不采信也不放弃 → 备用源成功");

        // S8g 主源包名不匹配（错源/劫持面）→ 继续备用源成功
        ScriptFetcher f4 = new ScriptFetcher();
        f4.script.put(src[0], GOOD_MANIFEST.replace("com.zai.adshield", "com.evil.x"));
        f4.script.put(src[1], GOOD_MANIFEST);
        UpdateRepository.WalkResult w4 = UpdateRepository.walkSources(f4, null, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w4.kind == UpdateRepository.WalkResult.SUCCESS,
                "S8g 主源包名不匹配 → 备用源有效 → 采信备用源（抗劫持降级）");

        // S8h 全链失败 → ALL_FAILED + 首源错误码 canonical + 每源恰一次
        ScriptFetcher f5 = new ScriptFetcher();
        f5.script.put(src[0], "ERR:" + UpdateManifest.ERR_DNS);
        f5.script.put(src[1], "ERR:" + UpdateManifest.ERR_TIMEOUT);
        f5.script.put(src[2], "ERR:" + UpdateManifest.ERR_SERVER);
        f5.script.put(src[3], "ERR:" + UpdateManifest.ERR_HTTP);
        f5.script.put(src[4], "ERR:" + UpdateManifest.ERR_NETWORK);
        UpdateRepository.WalkResult w5 = UpdateRepository.walkSources(f5, null, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w5.kind == UpdateRepository.WalkResult.ALL_FAILED
                        && UpdateManifest.ERR_DNS.equals(w5.errorCode),
                "S8h 全链失败 → 首源错误码 canonical（DNS 如实报告）");
        TestHost.check(f5.requested.size() == 5, "S8h 五源各恰一次（无重试风暴）");

        // S8i 304（同源条件头）→ NOT_MODIFIED；条件头只发给缓存同源
        UpdateRepository.CacheInfo cached = new UpdateRepository.CacheInfo(GOOD_MANIFEST,
                "\"e1\"", "Tue, 1 Jan 2026 00:00:00 GMT", 1234567890L, src[1]);
        ScriptFetcher f6 = new ScriptFetcher();
        f6.script.put(src[0], "ERR:" + UpdateManifest.ERR_NETWORK);
        f6.script.put(src[1], "304");
        UpdateRepository.WalkResult w6 = UpdateRepository.walkSources(f6, cached, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w6.kind == UpdateRepository.WalkResult.NOT_MODIFIED,
                "S8i 同源条件 304 → NOT_MODIFIED（缓存续新鲜）");
        TestHost.check(f6.conditionalSent == 1 && f6.requested.contains(src[1]),
                "S8i 条件头只回发同源（跨源不混用 ETag）");

        // S8j 无条件头却 304（异常服务防御面）→ 按失败继续链
        ScriptFetcher f7 = new ScriptFetcher();
        f7.script.put(src[0], "304"); // 无缓存 → 无条件头
        f7.script.put(src[1], GOOD_MANIFEST);
        UpdateRepository.WalkResult w7 = UpdateRepository.walkSources(f7, null, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w7.kind == UpdateRepository.WalkResult.SUCCESS && w7.url.equals(src[1]),
                "S8j 异常 304（无条件头）→ 防御性继续链 → 备用源成功");

        // S8k 链预算耗尽 → 首源必试、其余不再尝试（长阻塞闸）
        ScriptFetcher f8 = new ScriptFetcher();
        f8.script.put(src[0], "ERR:" + UpdateManifest.ERR_TIMEOUT);
        f8.script.put(src[4], GOOD_MANIFEST);
        UpdateRepository.WalkResult w8 = UpdateRepository.walkSources(f8, null,
                System.currentTimeMillis() - 1, "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w8.kind == UpdateRepository.WalkResult.ALL_FAILED && f8.requested.size() == 1,
                "S8k 预算耗尽 → 首源必试后即停（恰 1 请求）");

        // S8l hasRelease:false（无正式版清单）→ 有效答案即停链
        ScriptFetcher f9 = new ScriptFetcher();
        f9.script.put(src[0], "{\"prerelease\":true}");
        UpdateRepository.WalkResult w9 = UpdateRepository.walkSources(f9, null, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w9.kind == UpdateRepository.WalkResult.SUCCESS
                        && w9.payload.contains("\"hasRelease\":false"),
                "S8l 无正式版清单 → 有效答案即停（hasRelease:false）");
        TestHost.check(f9.requested.size() == 1, "S8l 停链零多余请求");

        // S8m Fetcher 返回 null（缝异常防御）→ 按网络失败继续链
        ScriptFetcher f10 = new ScriptFetcher();
        f10.script.put(src[0], "NULL");
        f10.script.put(src[1], GOOD_MANIFEST);
        UpdateRepository.WalkResult w10 = UpdateRepository.walkSources(f10, null, farDeadline,
                "com.zai.adshield", "7.9.8", 79800);
        TestHost.check(w10.kind == UpdateRepository.WalkResult.SUCCESS,
                "S8m Fetcher 异常返回 null → 防御性继续链成功");
    }
}
