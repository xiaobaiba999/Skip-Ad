package com.zai.adshield;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLException;

/**
 * 更新检查统一数据访问层（v7.9.8 更新检查架构改造——所有更新入口的唯一通道）。
 * ─────────────────────────────────────────────
 * 架构（用户指令「客户端不再直接依赖 GitHub REST API」）：
 *   更新元数据只读静态清单（raw.githubusercontent.com main 分支，Fastly CDN，
 *   无匿名 REST API 60 次/小时配额）；APK 仍从 GitHub Release 官方地址下载，
 *   安装前 Downloader SHA256+大小闸校验（安全强度不降）。
 *
 * 职责（防请求风暴的四道闸 + 降级）：
 *  1. 本地缓存：最近一次成功清单原子落盘（json/etag/lastModified/cachedAt/
 *     url）；新鲜期 15 分钟内不联网；网络失败回退最近有效缓存（绝不因过期
 *     删数据）；损坏缓存安全恢复（按无缓存处理并清理）。
 *  2. 并发合并（FlightGate 单飞）：多入口同时触发 → 只有一个真实网络请求，
 *     后续共享同一结果；请求结束正确释放；绝不并发重复请求。
 *  3. 请求冷却：手动检查 30s 冷却（冷却期内复用上次结果——可点击但不重复
 *     联网）；后台自动检查每次进程至多联网一次；失败指数退避（1min 起，
 *     ×2 至 6h 封顶）——网络失败绝不无限重试。
 *  4. ETag/Last-Modified 条件请求：服务器 304 → 直接用本地缓存（省流量与
 *     解析；304 空响应不当 JSON 解析失败）；ETag 只回发给同一 URL（不同域
 *     etag 空间不同）。
 *  5. 受控备用源：主源失败后备用静态地址（GitHub Pages）至多尝试一次——
 *     绝不多地址轮询；无缓存且双源失败 → 如实错误分类。
 *
 * 错误十类（UpdateManifest 常量）：网络连接/域名解析/超时/TLS/HTTP 状态/
 * 清单非法/包名不匹配/版本无效/无缓存/限流/服务端异常——UI 如实转达。
 *
 * 线程：同步阻塞（JSBridge 线程调用；单飞等待者阻塞在 GATE 上——JS 侧
 * 本就 await Promise）。超时上界：7s 连接 + 12s 读取 × 2 源 ≈ 38s。
 * 约束：无 lambda/匿名类/内部类（d8 8.2 兼容；FlightGate 为静态嵌套类）。
 */
public final class UpdateRepository {

    private UpdateRepository() {
    }

    /* ═══ 静态清单地址（唯一更新元数据来源——零 GitHub REST API）═══ */
    public static final String MANIFEST_PRIMARY =
            "https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json";

    /* 受控备用静态地址（GitHub Pages——仓库未启用 Pages 时 404，仅主源
     * 失败后尝试一次；启用方式见 update-manifest.yml）。 */
    public static final String MANIFEST_FALLBACK =
            "https://xiaobaiba999.github.io/Skip-Ad/update/latest.json";

    /* ═══ 策略常量（防风暴参数——全部可调于测试）═══ */
    public static final long FRESH_TTL_MS = 15 * 60 * 1000L;        // 新鲜缓存有效期
    public static final long MANUAL_COOLDOWN_MS = 30 * 1000L;       // 手动联网冷却
    public static final long BACKOFF_BASE_MS = 60 * 1000L;          // 失败退避基值 1min
    public static final long BACKOFF_CAP_MS = 6 * 60 * 60 * 1000L;  // 退避封顶 6h
    static final int CONNECT_MS = 7000;
    static final int READ_MS = 12000;
    static final int MAX_MANIFEST_BYTES = 1024 * 1024;              // 清单上限 1MB（实际 ~2KB）
    private static final String CACHE_FILE = "update_manifest_cache.json";
    private static final String CACHE_TMP = "update_manifest_cache.json.tmp";

    /* ═══ 进程内状态（全部纯 Java 类型——JVM 可测）═══ */
    private static final Object STATE_LOCK = new Object();
    private static final FlightGate GATE = new FlightGate();
    private static long lastNetAttemptAt = 0L;      // 上次真实联网尝试（成功或失败）
    private static long lastSuccessAt = 0L;         // 上次联网成功（缓存写入）
    private static int consecutiveFails = 0;        // 连续失败次数（指数退避输入）
    private static boolean autoNetDone = false;     // 本进程自动联网检查至多一次
    private static String lastNetResult = null;     // 冷却期内手动复用的最近结果

    /* ═══════════ 入口（MainActivity.Bridge.updateCheck 调用）═══════════ */

    /**
     * 统一更新检查（所有入口——手动按钮/启动自动检查——的唯一通道）。
     *
     * @param ctx                应用 Context（缓存目录/包名）
     * @param currentVersion     当前 versionName
     * @param currentVersionCode 当前 versionCode（versionCode 主判）
     * @param manual             true = 用户主动检查（可绕过新鲜缓存，受 30s 冷却）；
     *                           false = 后台自动检查（优先新鲜缓存；进程内至多联网一次）
     * @return 成功载荷（UpdateManifest.parse 字段 + fromCache/staleCache/cachedAt/netError）
     *         / {ok:false, error, errorCode}（无可用数据时）
     */
    public static String check(Context ctx, String currentVersion, int currentVersionCode, boolean manual) {
        final String expectedPackage = ctx.getPackageName();
        final long now = System.currentTimeMillis();
        final String cacheRaw = readCache(ctx);
        final CacheInfo cache = parseCacheInfo(cacheRaw); // null = 无缓存/损坏

        /* ── 决策（持锁快路径——纯内存判断，零 IO）── */
        synchronized (STATE_LOCK) {
            boolean fresh = cache != null && isFresh(cache.cachedAt, now);
            if (!manual && fresh) {
                return serveCache(cacheRaw, cache, currentVersion, currentVersionCode, false, null);
            }
            if (!manual) {
                // 自动检查：本进程已联网过 / 退避窗内 → 缓存兜底（不联网）
                if (autoNetDone || now < backoffUntil(lastNetAttemptAt, consecutiveFails)) {
                    if (cache != null) {
                        return serveCache(cacheRaw, cache, currentVersion, currentVersionCode, true, null);
                    }
                    return silentNoData("自动检查冷却/退避中且无缓存");
                }
            } else if (now - lastNetAttemptAt < MANUAL_COOLDOWN_MS) {
                // 手动冷却期：不联网——复用最近结果或缓存（可点击、不重复请求）
                if (lastNetResult != null) return lastNetResult;
                if (cache != null) {
                    return serveCache(cacheRaw, cache, currentVersion, currentVersionCode, true, null);
                }
                return UpdateManifest.fail(UpdateManifest.ERR_NO_CACHE, "刚检查过，请稍候再试");
            }
            lastNetAttemptAt = now;
            if (!manual) autoNetDone = true;
        }

        /* ── 单飞（多入口并发 → 一个真实网络请求，其余共享结果）── */
        long gen = GATE.enter();
        if (gen == 0L) {
            // 领飞者：真实联网（任何异常都收敛为失败载荷——绝不悬挂单飞门）
            String result;
            try {
                result = doNetwork(ctx, expectedPackage, currentVersion, currentVersionCode, cache, cacheRaw);
            } catch (Throwable t) {
                result = UpdateManifest.fail(classifyNetError(t), "检查更新失败（" + safeMsg(t) + "）");
            }
            GATE.finish(result);
            synchronized (STATE_LOCK) {
                lastNetResult = result;
                /* 退避计数以「网络是否真实成功」为准：ok:true 但带 netError
                 * （双源失败后缓存兜底）仍是网络失败——计退避，绝不因缓存
                 * 兜底掩盖断网而让每次启动都撞网络（指数退避的诚实语义）。 */
                if (isOkPayload(result) && !hasNetError(result)) {
                    lastSuccessAt = System.currentTimeMillis();
                    consecutiveFails = 0;
                } else {
                    consecutiveFails++;
                }
            }
            return result;
        }
        // 搭飞者：等待领飞结果并共享（该代已结束时自行走缓存路径——罕见竞态）
        String shared = GATE.await(gen);
        if (shared != null) return shared;
        synchronized (STATE_LOCK) {
            if (lastNetResult != null) return lastNetResult;
        }
        if (cache != null) {
            return serveCache(cacheRaw, cache, currentVersion, currentVersionCode, true, null);
        }
        return UpdateManifest.fail(UpdateManifest.ERR_NO_CACHE, "暂时无法检查更新，请稍后重试");
    }

    /* ═══════════ 网络管线（仅领飞者执行）═══════════ */

    private static String doNetwork(Context ctx, String expectedPackage, String currentVersion,
                                    int currentVersionCode, CacheInfo cache, String cacheRaw) {
        // 主源：条件请求（ETag/Last-Modified 只回发同源缓存）
        HttpOutcome primary = fetchOne(MANIFEST_PRIMARY,
                cache != null && MANIFEST_PRIMARY.equals(cache.url) ? cache : null);
        String result = handleOutcome(ctx, primary, currentVersion, currentVersionCode, null);
        if (result != null) return result;

        // 备用静态地址（受控单次——主源任何失败后至多尝试一次，绝不多址轮询）
        HttpOutcome fallback = fetchOne(MANIFEST_FALLBACK, null);
        result = handleOutcome(ctx, fallback, currentVersion, currentVersionCode, primary);
        if (result != null) return result;

        // 双源失败：主源错误优先（canonical）；有缓存 → 缓存兜底（如实标注陈旧）
        String errorCode = primary != null && primary.errorCode != null
                ? primary.errorCode : (fallback != null && fallback.errorCode != null
                ? fallback.errorCode : UpdateManifest.ERR_NETWORK);
        if (cache != null && cacheRaw != null) {
            return serveCache(cacheRaw, cache, currentVersion, currentVersionCode, true, errorCode);
        }
        return UpdateManifest.fail(errorCode, netErrorText(errorCode));
    }

    /**
     * 单源结果归并：成功（200 有效清单）→ 写缓存返回；304 → 缓存续新鲜返回；
     * 其余失败 → null（调用方决定降级路径）。
     */
    private static String handleOutcome(Context ctx, HttpOutcome o, String currentVersion,
                                        int currentVersionCode, HttpOutcome failedOther) {
        if (o == null) return null;
        if (o.notModified) {
            CacheInfo c = parseCacheInfo(readCache(ctx));
            if (c != null) {
                c.cachedAt = System.currentTimeMillis(); // 内容未变——续新鲜
                writeCache(ctx, buildCacheJson(c));
                return serveCache(buildCacheJson(c), c, currentVersion, currentVersionCode, false, null);
            }
            return null; // 304 但缓存丢失（竞态）→ 按失败处理走降级
        }
        if (o.body != null) {
            String parsed = UpdateManifest.parse(o.body, ctx.getPackageName(), currentVersion, currentVersionCode);
            try {
                JSONObject p = new JSONObject(parsed);
                if (p.optBoolean("ok", false)) {
                    writeCache(ctx, buildCacheJson(new CacheInfo(o.body, o.etag, o.lastModified,
                            System.currentTimeMillis(), o.url)));
                    return parsed; // ok:true —— fromCache:false（联网新得）
                }
                // 清单非法：拒绝使用（不写缓存）——如实报错（不静默换源伪装成功）
                return parsed;
            } catch (Exception e) {
                return UpdateManifest.fail(UpdateManifest.ERR_INVALID, "更新清单解析异常");
            }
        }
        return null; // 网络/HTTP 失败 → 降级路径
    }

    /* ═══════════ HTTP（条件请求 + 异常分类）═══════════ */

    /** 静态嵌套类：单次 HTTP 结果（kind 互斥：notModified / body / errorCode）。 */
    public static final class HttpOutcome {
        final boolean notModified;
        final String body;
        final String etag;
        final String lastModified;
        final String url;
        final String errorCode;

        public HttpOutcome(boolean notModified, String body, String etag, String lastModified,
                            String url, String errorCode) {
            this.notModified = notModified;
            this.body = body;
            this.etag = etag;
            this.lastModified = lastModified;
            this.url = url;
            this.errorCode = errorCode;
        }
    }

    /** 单 URL GET（带同源条件头）。200 → body；304 → notModified；其余 → errorCode。 */
    public static HttpOutcome fetchOne(String url, CacheInfo conditional) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setRequestMethod("GET");
            c.setRequestProperty("Accept", "application/json,*/*");
            c.setRequestProperty("User-Agent", Net.BROWSER_UA); // CDN 友好 UA
            c.setRequestProperty("Accept-Encoding", "identity");
            if (conditional != null) {
                if (conditional.etag != null && conditional.etag.length() > 0) {
                    c.setRequestProperty("If-None-Match", conditional.etag);
                }
                if (conditional.lastModified != null && conditional.lastModified.length() > 0) {
                    c.setRequestProperty("If-Modified-Since", conditional.lastModified);
                }
            }
            int code = c.getResponseCode();
            if (code == 304) {
                return new HttpOutcome(true, null, null, null, url, null);
            }
            if (code >= 200 && code < 300) {
                InputStream in = c.getInputStream();
                String body = readAll(in);
                if (in != null) try { in.close(); } catch (Exception ignored) { }
                return new HttpOutcome(false, body, header(c, "ETag"), header(c, "Last-Modified"),
                        url, null);
            }
            if (UpdateManifest.isRateLimitCode(code)) {
                return new HttpOutcome(false, null, null, null, url, UpdateManifest.ERR_RATE_LIMITED);
            }
            if (code >= 500) {
                return new HttpOutcome(false, null, null, null, url, UpdateManifest.ERR_SERVER);
            }
            return new HttpOutcome(false, null, null, null, url, UpdateManifest.ERR_HTTP);
        } catch (Exception e) {
            return new HttpOutcome(false, null, null, null, url, classifyNetError(e));
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 响应体硬上限（防超大 body 内存 DoS——清单实际 ~2KB）。 */
    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            if (bo.size() > MAX_MANIFEST_BYTES) {
                try { in.close(); } catch (Exception ignored) { }
                throw new IllegalStateException("清单超过 1MB 上限（已中断连接）");
            }
        }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String header(HttpURLConnection c, String name) {
        try {
            String v = c.getHeaderField(name);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    /** 网络异常 → 错误分类码（DNS/超时/TLS/连接——区分「网络失败」与「服务端拒绝」）。 */
    public static String classifyNetError(Throwable t) {
        if (t instanceof UnknownHostException) return UpdateManifest.ERR_DNS;
        if (t instanceof SocketTimeoutException) return UpdateManifest.ERR_TIMEOUT;
        if (t instanceof SSLException) return UpdateManifest.ERR_TLS;
        if (t instanceof ConnectException) return UpdateManifest.ERR_NETWORK;
        if (t instanceof java.io.IOException) return UpdateManifest.ERR_NETWORK;
        return UpdateManifest.ERR_NETWORK;
    }

    /** 错误码 → 人话（与 app.js updateErrText 同源——Java 侧兜底文本）。 */
    public static String netErrorText(String errorCode) {
        if (UpdateManifest.ERR_DNS.equals(errorCode)) return "网络域名解析失败，请检查网络后重试";
        if (UpdateManifest.ERR_TIMEOUT.equals(errorCode)) return "网络请求超时，请稍后重试";
        if (UpdateManifest.ERR_TLS.equals(errorCode)) return "安全连接失败，请稍后重试";
        if (UpdateManifest.ERR_RATE_LIMITED.equals(errorCode)) return "请求过于频繁，请稍后重试";
        if (UpdateManifest.ERR_SERVER.equals(errorCode)) return "更新服务暂时不可用，请稍后重试";
        if (UpdateManifest.ERR_HTTP.equals(errorCode)) return "更新服务返回异常，请稍后重试";
        if (UpdateManifest.ERR_INVALID.equals(errorCode)) return "更新信息格式异常，已忽略本次结果";
        if (UpdateManifest.ERR_PACKAGE.equals(errorCode)) return "更新信息与当前应用不匹配，已拒绝";
        if (UpdateManifest.ERR_VERSION.equals(errorCode)) return "更新信息版本号异常，已忽略";
        return "网络连接失败，请检查网络后重试";
    }

    /* ═══════════ 缓存（原子写 + 损坏恢复 + 绝不删最后有效数据）═══════════ */

    /** 缓存条目（纯数据——静态嵌套类）。 */
    public static final class CacheInfo {
        public String json;
        public String etag;
        public String lastModified;
        public long cachedAt;
        public String url;

        public CacheInfo(String json, String etag, String lastModified, long cachedAt, String url) {
            this.json = json;
            this.etag = etag;
            this.lastModified = lastModified;
            this.cachedAt = cachedAt;
            this.url = url;
        }
    }

    private static final String buildCacheJson(CacheInfo c) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("json", c.json);
            o.put("etag", c.etag == null ? "" : c.etag);
            o.put("lastModified", c.lastModified == null ? "" : c.lastModified);
            o.put("cachedAt", c.cachedAt);
            o.put("url", c.url == null ? "" : c.url);
            return o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static CacheInfo parseCacheInfo(String raw) {
        if (raw == null || raw.length() == 0) return null;
        try {
            JSONObject o = new JSONObject(raw);
            if (o.optInt("v", 0) != 1) return null;
            String json = o.optString("json", "");
            if (json.length() == 0) return null;
            return new CacheInfo(json, o.optString("etag", ""), o.optString("lastModified", ""),
                    o.optLong("cachedAt", 0), o.optString("url", ""));
        } catch (Exception e) {
            return null; // 损坏缓存 → 按无缓存处理（恢复 = 重新联网拉取）
        }
    }

    private static String readCache(Context ctx) {
        FileInputStream in = null;
        try {
            File f = new File(ctx.getFilesDir(), CACHE_FILE);
            if (!f.isFile()) return null;
            in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null; // 读失败按无缓存（下次联网覆盖恢复）
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }

    /** 原子写（tmp + rename——半写文件绝不暴露为有效缓存）。 */
    private static void writeCache(Context ctx, String raw) {
        if (raw == null || raw.length() == 0) return;
        try {
            File dir = ctx.getFilesDir();
            File tmp = new File(dir, CACHE_TMP);
            File dst = new File(dir, CACHE_FILE);
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            if (!tmp.renameTo(dst)) {
                // 极端文件系统（rename 跨句柄失败）→ 直接写目标（保持最后有效语义）
                FileOutputStream d2 = new FileOutputStream(dst);
                d2.write(raw.getBytes(StandardCharsets.UTF_8));
                d2.flush();
                d2.close();
                try { tmp.delete(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
            // 缓存写失败不影响本次结果（内存结果已返回）
        }
    }

    /** 缓存数据 → 页面可用载荷（重新对当前版本做 isNewer 判定 + 缓存标注）。 */
    private static String serveCache(String cacheRaw, CacheInfo cache, String currentVersion,
                                     int currentVersionCode, boolean stale, String netError) {
        String parsed = UpdateManifest.parse(cache.json, null, currentVersion, currentVersionCode);
        try {
            JSONObject p = new JSONObject(parsed);
            if (!p.optBoolean("ok", false)) {
                return UpdateManifest.fail(UpdateManifest.ERR_INVALID, "本地缓存清单异常，请重新检查");
            }
            p.put("fromCache", true);
            p.put("staleCache", stale);
            p.put("cachedAt", cache.cachedAt);
            p.put("checkedAt", System.currentTimeMillis());
            if (netError != null) p.put("netError", netError);
            return p.toString();
        } catch (Exception e) {
            return UpdateManifest.fail(UpdateManifest.ERR_INVALID, "本地缓存清单异常，请重新检查");
        }
    }

    /** 自动检查静默无数据（启动检查失败静默语义——不进错误 UI）。 */
    private static String silentNoData(String reason) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("errorCode", UpdateManifest.ERR_NO_CACHE);
            o.put("error", reason);
            o.put("silent", true);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"errorCode\":\"UPDATE_NO_CACHE\",\"silent\":true}";
        }
    }

    /* ═══════════ 单飞门（并发合并——静态嵌套类，纯 JVM 可测）═══════════ */

    /**
     * FlightGate：同一时刻至多一个领飞者执行网络请求；搭飞者等待并共享该代
     * 结果。enter() 返回 0 = 你是领飞者（完成后必须 finish()）；>0 = 搭飞
     * 代次（await(gen) 返回该代结果；null = 该代已消失——调用方走缓存）。
     */
    public static final class FlightGate {
        private boolean inFlight = false;
        private long startedGen = 0;    // 当前在飞代次（1 起）
        private long completedGen = 0;  // 已完成代次
        private String lastResult = null;

        public synchronized long enter() {
            if (inFlight) return startedGen;
            inFlight = true;
            startedGen = completedGen + 1;
            return 0L;
        }

        public synchronized void finish(String result) {
            lastResult = result;
            completedGen = startedGen;
            inFlight = false;
            notifyAll();
        }

        public synchronized String await(long gen) {
            try {
                while (inFlight && startedGen == gen) wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            if (completedGen == gen) return lastResult;
            return null;
        }
    }

    /* ═══════════ 纯决策函数（JVM 可测）═══════════ */

    /** 缓存新鲜判定（cachedAt 距 now 不足 TTL 且非负——时钟回拨产生的
     *  「未来时间戳」不算新鲜：回滚后宁可按过期走受控刷新（冷却/退避仍
     *  兜底防风暴），绝不因时钟异常永久卡死旧缓存或触发联网风暴）。 */
    public static boolean isFresh(long cachedAt, long now) {
        return cachedAt > 0 && now >= cachedAt && now - cachedAt < FRESH_TTL_MS;
    }

    /** 失败指数退避窗口终点（1min × 2^(fails-1) 持续翻倍，封顶 6h；
     *  fails=0 → 无退避。翻倍在达到封顶后即停——无溢出面。 */
    public static long backoffUntil(long lastAttemptAt, int fails) {
        if (fails <= 0) return 0L;
        long wait = BACKOFF_BASE_MS;
        for (int i = 1; i < fails && wait < BACKOFF_CAP_MS; i++) {
            wait = wait * 2;
        }
        if (wait > BACKOFF_CAP_MS) wait = BACKOFF_CAP_MS;
        return lastAttemptAt + wait;
    }

    /** 手动冷却判定（距上次联网尝试不足 30s）。 */
    public static boolean manualCoolingDown(long lastAttemptAt, long now) {
        return lastAttemptAt > 0 && now - lastAttemptAt < MANUAL_COOLDOWN_MS;
    }

    /** 测试复位（进程内状态清零——仅 JVM 测试用）。 */
    public static void resetStateForTest() {
        synchronized (STATE_LOCK) {
            lastNetAttemptAt = 0;
            lastSuccessAt = 0;
            consecutiveFails = 0;
            autoNetDone = false;
            lastNetResult = null;
        }
    }

    /** 载荷 ok 判定（成功=联网取得有效清单/缓存兜底；失败=退避计数）。 */
    public static boolean isOkPayload(String json) {
        try {
            return new JSONObject(json).optBoolean("ok", false);
        } catch (Exception e) {
            return false;
        }
    }

    /** 载荷是否带 netError（双源网络失败后缓存兜底的标记——退避计数输入）。 */
    public static boolean hasNetError(String json) {
        try {
            JSONObject o = new JSONObject(json);
            return o.has("netError") && o.optString("netError", "").length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }
}
