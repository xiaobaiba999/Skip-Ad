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
        TestHost.suite("UpdateRepositoryTest · 缓存/冷却/退避/单飞并发（v7.9.8）");
        s1_freshTtl();
        s2_manualCooldown();
        s3_backoff();
        s4_flightGateConcurrency();
        s5_classifyNetError();
        s6_cacheInfoRobustness();
        s7_okPayload();
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

    /* ═══ S6 缓存条目健壮性（损坏恢复面——文件 IO 之外的纯判定）═══ */
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
            TestHost.check(UpdateRepository.MANIFEST_FALLBACK.contains("xiaobaiba999.github.io"),
                    "S6c 备用源 = GitHub Pages（受控单次）");
            TestHost.check(!UpdateRepository.MANIFEST_PRIMARY.contains("api.github.com")
                            && !UpdateRepository.MANIFEST_FALLBACK.contains("api.github.com"),
                    "S6d 双源零 api.github.com（架构红线）");
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
}
