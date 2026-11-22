package com.zai.adshield.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v8.0.0 日志批次存储（本地持久化待上传队列 · 中转服务通道）。
 * ─────────────────────────────────────────────
 * 状态机： PENDING → UPLOADING → ACKED / FAILED（瞬时故障按退避回 PENDING；
 *          限流等待也回 PENDING 但不耗重试计数；明确拒收 → FAILED 终态保留检视）。
 *          v8.2.1：FAILED 细分 failKind（terminal 服务端明确拒收 / exhausted
 *          重试耗尽）——手动一键上传可复活 exhausted 型（同 batchId 重试，
 *          服务端幂等兑底）；terminal 型绝不复活（重试必然再拒/服务端红线）。
 *
 * 存储形态（StoreEnv 环境 seam——Android 生产实现 + JVM 测试实现同源状态机）：
 *  · installInstanceId：首启随机 UUID v4（SharedPreferences；清数据/卸载自然
 *    重置——不用 IMEI/Android ID/手机号等任何设备敏感标识）；
 *  · 事件缓冲：files/log-upload/pending-events.json（上限 400 条，溢出优先丢
 *    非关键事件——关键安全事件绝不因容量静默丢失计数语义，超限裁剪计数保留）；
 *  · 批次文件：files/log-upload/batch-{batchId}.json（ACK 前绝不删除——
 *    「未经确认不删本地日志」红线；ACK 后压缩为 acked-meta 记录）；
 *  · 保留策略：ACKED 批次保留 3 天元数据（50 条环形；v8.2.1 实装相对最新
 *    ACK 时间的 TTL 裁剪）；FAILED 终态保留 14 天（v8.2.1 实装 failedAt
 *    时问清理——到期在 dueBatches 扫描时删除）；
 *  · 容量上限：待传批次 ≤ 20（超限删最旧非 UPLOADING 态）。
 *
 * 线程纪律：appendEvent 走单线程 executor（调用方=无障碍事件线程零阻塞）；
 *  seal/list/mark 系列由 LogUploadDispatcher 后台线程串行调用（同一时刻仅
 *  一个 dispatcher 线程——并发重复提交由「单执行线程 + 服务端 batchId 幂等」双闸防死）。
 *
 * 采集门（v8.0.0 隐私立场）：未授权（或中转未上线）时 appendEvent 直接丢弃——
 *  未同意上传的用户设备上零新增磁盘写入；内存内 DecisionEvidence 诊断照旧。
 */
public final class LogBatchStore {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_UPLOADING = "UPLOADING";
    public static final String STATE_ACKED = "ACKED";
    public static final String STATE_FAILED = "FAILED";

    /** v8.2.1 FAILED 细分（复活门用）：terminal = 服务端明确拒收/客户端防御
     *  拒绝（重试必然再拒，绝不复活）；exhausted = 网络退避耗尽（手动可复活）。 */
    public static final String FAILKIND_TERMINAL = "terminal";
    public static final String FAILKIND_EXHAUSTED = "exhausted";

    private static final String PREFS = "logupload";
    private static final String KEY_INSTALL_ID = "installInstanceId";
    private static final String DIR_NAME = "log-upload";
    private static final String PENDING_FILE = "pending-events.json";
    private static final String ACKED_META = "acked-meta.json";
    private static final int PENDING_EVENT_CAP = 400;
    private static final int BATCH_CAP = 20;
    private static final long ACKED_META_TTL = 3L * 24 * 3600 * 1000;
    private static final long FAILED_TTL = 14L * 24 * 3600 * 1000;

    /** 关键安全事件类型（容量裁剪时最后丢弃）。 */
    private static final String[] CRITICAL_TYPES = {"CRASH", "SAFE_BLOCK"};
    private static final String[] CRITICAL_OUTCOMES = {"FALSE_CLICK", "DUPLICATE_CLICK", "SAFE_BLOCK", "CRASH"};

    /* ═══ 环境 seam（Android 生产实现——JVM 运行时绝不加载该嵌套类）═══ */

    /** 存储 seam：偏好读写 + 文件根目录（Android 实现 + JVM 测试实现同源状态机）。 */
    public interface StoreEnv {
        String prefGet(String key);

        void prefPut(String key, String value);

        File filesDir();
    }

    /** Android 生产实现（Context → SharedPreferences + filesDir）。 */
    static final class AndroidEnv implements StoreEnv {
        private final Context ctx;

        AndroidEnv(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public String prefGet(String key) {
            try {
                SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                return sp.getString(key, null);
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public void prefPut(String key, String value) {
            try {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(key, value).apply();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public File filesDir() {
            return ctx.getFilesDir();
        }
    }

    /* ═══ 实例 ═══ */

    private static volatile LogBatchStore instance;

    /** Android 生产入口（单例）。 */
    public static LogBatchStore get(Context ctx) {
        if (instance == null) {
            synchronized (LogBatchStore.class) {
                if (instance == null) instance = new LogBatchStore(new AndroidEnv(ctx.getApplicationContext()));
            }
        }
        return instance;
    }

    /** JVM 测试入口（注入临时目录环境——与 Android 实现共用全部状态机代码）。 */
    public static LogBatchStore forEnv(StoreEnv env) {
        return new LogBatchStore(env);
    }

    private final StoreEnv env;
    /* daemon 单线程 IO executor：JVM 测试与非前台进程退出不被挂起
     *  （线程名 log-store-io——崩溃报告/调试可直接归因）。 */
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "log-store-io");
        t.setDaemon(true);
        return t;
    });

    /* 采集门：未授权（或中转未上线）→ 事件直接丢弃（零磁盘写入）。 */
    private static volatile boolean collectEnabled = false;

    /** 采集开关（AdSkipService 授权门/中转门设置——心跳与开关回调刷新）。 */
    public static void setCollectEnabled(boolean on) {
        collectEnabled = on;
    }

    private LogBatchStore(StoreEnv env) {
        this.env = env;
    }

    /* ── installInstanceId ── */

    public String installInstanceId() {
        String id = env.prefGet(KEY_INSTALL_ID);
        if (!LogUploadPolicy.isValidUuid(id)) {
            id = UUID.randomUUID().toString();
            env.prefPut(KEY_INSTALL_ID, id);
        }
        return id;
    }

    /* ── 事件缓冲（异步追加——无障碍主线程零阻塞）── */

    /** 追加一条结构化事件（字段须先经 LogEvents 白名单构造；内部异步落盘）。 */
    public void appendEvent(final JSONObject event) {
        if (!collectEnabled || event == null) return; // 采集门：未授权零磁盘写入
        io.submit(() -> {
            try {
                synchronized (this) {
                    JSONArray pending = readJsonArray(file(PENDING_FILE));
                    if (pending.length() >= PENDING_EVENT_CAP) {
                        pending.remove(firstNonCritical(pending));
                    }
                    pending.put(event);
                    writeJsonArray(file(PENDING_FILE), pending);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /** JVM 测试钩子：排空异步落盘队列（appendEvent 的 executor FIFO 汇点——
     *  生产永不调用；与 DecisionEvidence.resetForJvm 同规约）。 */
    public void flushForJvm() {
        try {
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            io.submit(done::countDown);
            done.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
    }

    public int pendingEventCount() {
        synchronized (this) {
            return readJsonArray(file(PENDING_FILE)).length();
        }
    }

    /**
     * 封批：缓冲 → 独立批次文件（先经 LogPrivacyFilter 过滤；隐私拒绝 → 批次直接
     * FAILED 终态并保留检视，绝不上传已知脏数据）。返回 batchId 或 null（空批/异常）。
     * v8.0.0 分批上限：单批事件数 ≤ LogPrivacyFilter.MAX_EVENTS（服务端契约上限
     * 48）——超出部分留在缓冲待下轮封批（8h 心跳间隔的堆积不截尾丢数据；
     * 多余批次受 BATCH_CAP=20 容量与最旧先逐出约束——有界保留，绝无静默全丢）。 */
    public synchronized String sealBatch(String appVersion, int versionCode) {
        try {
            JSONArray pending = readJsonArray(file(PENDING_FILE));
            if (pending.length() == 0) return null;
            JSONArray slice = pending;
            JSONArray remainder = null;
            if (pending.length() > LogPrivacyFilter.MAX_EVENTS) {
                slice = new JSONArray();
                remainder = new JSONArray();
                for (int i = 0; i < pending.length(); i++) {
                    if (i < LogPrivacyFilter.MAX_EVENTS) slice.put(pending.opt(i));
                    else remainder.put(pending.opt(i));
                }
            }
            LogPrivacyFilter.Result r = LogPrivacyFilter.sanitizeEvents(slice);
            if (r.privacyRejected) {
                File f = file(PENDING_FILE);
                //noinspection ResultOfMethodCallIgnored
                f.delete(); // 拒绝整批即丢缓冲（危险字段不得留存），退回一个 FAILED 检视记录
                String markerId = UUID.randomUUID().toString();
                writeJson(file("batch-" + markerId + ".json"), failedRec(markerId, 0, r.dangerKey));
                return null;
            }
            if (r.events.length() == 0) {
                // 全部事件无效：缓冲整体作废（绝不反复重封无效数据）；余量保留待下轮
                if (remainder != null) writeJsonArray(file(PENDING_FILE), remainder);
                else {
                    //noinspection ResultOfMethodCallIgnored
                    file(PENDING_FILE).delete();
                }
                return null;
            }

            String batchId = UUID.randomUUID().toString();
            JSONObject rec = new JSONObject();
            rec.put("batchId", batchId);
            rec.put("installInstanceId", installInstanceId());
            rec.put("createdAt", System.currentTimeMillis());
            rec.put("eventCount", r.events.length());
            rec.put("payloadSize", r.events.toString().getBytes(StandardCharsets.UTF_8).length);
            rec.put("appVersion", appVersion);
            rec.put("versionCode", versionCode);
            rec.put("privacyFilterVersion", LogPrivacyFilter.VERSION);
            rec.put("retryCount", 0);
            rec.put("nextRetryAt", 0L);
            rec.put("state", STATE_PENDING);
            rec.put("lastError", "");
            rec.put("events", r.events);
            writeJson(file("batch-" + batchId + ".json"), rec);

            if (remainder != null) writeJsonArray(file(PENDING_FILE), remainder);
            else {
                //noinspection ResultOfMethodCallIgnored
                file(PENDING_FILE).delete();
            }
            enforceBatchCap();
            return batchId;
        } catch (Throwable t) {
            return null;
        }
    }

    /* ── 批次访问（dispatcher 专用——调用线程串行）── */

    public synchronized List<JSONObject> dueBatches(long now, int max) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        try {
            File dir = dir();
            File[] files = dir.listFiles();
            if (files == null) return out;
            List<File> batches = new ArrayList<File>();
            for (File f : files) {
                if (f.getName().startsWith("batch-") && f.getName().endsWith(".json")) batches.add(f);
            }
            // UPLOADING 中断恢复（进程死亡/重启）：视为 PENDING 重排（ACK 前不删红线不破）
            // v8.2.1：FAILED_TTL 到期清理（14 天检视窗实装——与类头保留策略
            // 一致）。计时基准用【墙钟】而非入参 now：now 只表达「到期查询」
            // 语义（测试惯用 Long.MAX_VALUE 表示全部到期——TTL 家政不得被
            // 查询参数驱动误删）。
            long wallNow = System.currentTimeMillis();
            for (File f : batches) {
                try {
                    JSONObject rec = readJson(f);
                    String st = rec.optString("state", STATE_PENDING);
                    if (STATE_UPLOADING.equals(st)) {
                        rec.put("state", STATE_PENDING);
                        rec.put("lastError", "recovered: interrupted UPLOADING");
                        writeJson(f, rec);
                    } else if (STATE_FAILED.equals(st)) {
                        long failedAt = rec.optLong("failedAt", 0L);
                        long basis = failedAt > 0 ? failedAt : f.lastModified();
                        if (wallNow - basis > FAILED_TTL) {
                            //noinspection ResultOfMethodCallIgnored
                            f.delete(); // 14 天检视窗到期（旧版本无 failedAt → 文件 mtime 兑底）
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            for (File f : batches) {
                if (out.size() >= max) break;
                try {
                    JSONObject rec = readJson(f);
                    String st = rec.optString("state", STATE_PENDING);
                    long next = rec.optLong("nextRetryAt", 0L);
                    if (STATE_PENDING.equals(st) && next <= now) out.add(rec);
                } catch (Throwable ignored) {
                }
            }
            out.sort(Comparator.comparingLong(o -> o.optLong("createdAt", 0L)));
            return out;
        } catch (Throwable t) {
            return out;
        }
    }

    public synchronized void markState(String batchId, String state, long nextRetryAt, String lastError) {
        File f = file("batch-" + batchId + ".json");
        try {
            JSONObject rec = readJson(f);
            rec.put("state", state);
            if (nextRetryAt > 0) rec.put("nextRetryAt", nextRetryAt);
            if (lastError != null) rec.put("lastError", lastError);
            if (STATE_UPLOADING.equals(state)) rec.put("retryCount", rec.optInt("retryCount", 0) + 1);
            writeJson(f, rec);
        } catch (Throwable ignored) {
        }
    }

    /** v8.2.1：终态失败写入（markState 的 FAIL 专用封装）——额外落 failKind
     *  （复活门数据源）与 failedAt（14 天检视窗计时基准）。 */
    public synchronized void markFailed(String batchId, String lastError, String failKind) {
        File f = file("batch-" + batchId + ".json");
        try {
            JSONObject rec = readJson(f);
            rec.put("state", STATE_FAILED);
            rec.put("nextRetryAt", 0L);
            rec.put("lastError", lastError);
            rec.put("failKind", failKind);
            rec.put("failedAt", System.currentTimeMillis());
            writeJson(f, rec);
        } catch (Throwable ignored) {
        }
    }

    /** v8.2.1 手动复活（仅 submitNowBlocking 一键上传路径调用——用户明确意图）：
     *  把「重试耗尽型」FAILED 批次重置为 PENDING（retryCount 清零获得全新
     *  6 次尝试窗、nextRetryAt=now 立即到期、同 batchId——服务端幂等兑底
     *  「超时但 Issue 已建」场景必然得到 ALREADY_ACCEPTED，绝不二建）。
     *  终态拒收型（terminal：六个服务端明确拒收码 + 客户端防御拒绝 +
     *  隐私整批拒绝）【绝不复活】——重试必然再拒/服务端红线。
     *  旧版本批次（无 failKind 字段）按 lastError 前缀启发式归类（终态族
     *  前缀清单 deny-list，其余非空视为耗尽型）。返回复活批次数。 */
    public synchronized int reviveExhaustedForManual(long now) {
        int revived = 0;
        try {
            File[] files = dir().listFiles();
            if (files == null) return 0;
            for (File f : files) {
                if (!f.getName().startsWith("batch-") || !f.getName().endsWith(".json")) continue;
                try {
                    JSONObject rec = readJson(f);
                    if (!STATE_FAILED.equals(rec.optString("state", ""))) continue;
                    if (!isRevivableFailure(rec)) continue;
                    rec.put("state", STATE_PENDING);
                    rec.put("retryCount", 0);
                    rec.put("nextRetryAt", now);
                    String prev = rec.optString("lastError", "");
                    rec.put("lastError", "revived(manual): " + (prev.length() > 80 ? prev.substring(0, 80) : prev));
                    rec.put("revivedAt", now);
                    writeJson(f, rec);
                    revived++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return revived;
    }

    /** 复活判定：显式 failKind 优先；旧版本批次按 lastError 前缀启发式
     *  （deny-list：终态拒收族/客户端防御/隐私整批拒绝前缀绝不复活）。 */
    private static boolean isRevivableFailure(JSONObject rec) {
        String kind = rec.optString("failKind", "");
        if (FAILKIND_EXHAUSTED.equals(kind)) return true;
        if (FAILKIND_TERMINAL.equals(kind)) return false;
        String e = rec.optString("lastError", "");
        if (e.length() == 0) return false; // 无法归因 → 保守不复活
        String[] never = {FAIL_LEGACY_PREFIX_0, FAIL_LEGACY_PREFIX_1, FAIL_LEGACY_PREFIX_2,
                FAIL_LEGACY_PREFIX_3, FAIL_LEGACY_PREFIX_4, FAIL_LEGACY_PREFIX_5,
                "client-defense", "PRIVACY_REJECTED(client)"};
        for (String p : never) if (e.startsWith(p)) return false;
        return true; // network / retry-exhausted / TEMPORARY_ERROR / GITHUB_UNAVAILABLE / 未知码耗尽
    }

    private static final String FAIL_LEGACY_PREFIX_0 = "PRIVACY_REJECTED";
    private static final String FAIL_LEGACY_PREFIX_1 = "INVALID_REQUEST";
    private static final String FAIL_LEGACY_PREFIX_2 = "UNAUTHORIZED";
    private static final String FAIL_LEGACY_PREFIX_3 = "IDEMPOTENCY_CONFLICT";
    private static final String FAIL_LEGACY_PREFIX_4 = "PAYLOAD_TOO_LARGE";
    private static final String FAIL_LEGACY_PREFIX_5 = "MANUAL_RECONCILIATION_REQUIRED";

    /** ACK：服务端确认后才调用（issueNumber/receivedAt 入 acked-meta；批次文件删除）。
     *  v8.2.1：实装 ACKED_META_TTL——相对最新 ACK 时间超 3 天的元数据裁剪
     *  （ackedCount24h 只需 24h 窗口，3 天余量充分；50 条环形封顶保持）。 */
    public synchronized void markAcked(String batchId, int issueNumber, long receivedAt) {
        try {
            JSONArray meta = readJsonArray(file(ACKED_META));
            JSONObject m = new JSONObject();
            m.put("batchId", batchId);
            m.put("issueNumber", issueNumber);
            m.put("receivedAt", receivedAt);
            meta.put(m);
            long newest = receivedAt;
            for (int i = 0; i < meta.length(); i++) {
                JSONObject o = meta.optJSONObject(i);
                if (o != null) newest = Math.max(newest, o.optLong("receivedAt", 0L));
            }
            for (int i = meta.length() - 1; i >= 0; i--) {
                JSONObject o = meta.optJSONObject(i);
                if (o != null && newest - o.optLong("receivedAt", 0L) > ACKED_META_TTL) meta.remove(i);
            }
            while (meta.length() > 50) meta.remove(0);
            writeJsonArray(file(ACKED_META), meta);
            //noinspection ResultOfMethodCallIgnored
            file("batch-" + batchId + ".json").delete();
        } catch (Throwable ignored) {
        }
    }

    public synchronized int ackedCount24h(long now) {
        int n = 0;
        try {
            JSONArray meta = readJsonArray(file(ACKED_META));
            for (int i = 0; i < meta.length(); i++) {
                JSONObject m = meta.optJSONObject(i);
                if (m != null && now - m.optLong("receivedAt", 0L) < 24L * 3600 * 1000) n++;
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    /** 概览（设置页状态行 + 检视用——最近失败原因/最近 ACK）。 */
    public synchronized JSONObject overview(long now) {
        try {
            JSONObject o = new JSONObject();
            int pendingN = 0, failedN = 0, acked24 = ackedCount24h(now);
            String lastError = "";
            long lastAckedAt = 0;
            try {
                File[] files = dir().listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (!f.getName().startsWith("batch-") || !f.getName().endsWith(".json")) continue;
                        try {
                            JSONObject rec = readJson(f);
                            String st = rec.optString("state", STATE_PENDING);
                            if (STATE_PENDING.equals(st) || STATE_UPLOADING.equals(st)) pendingN++;
                            else if (STATE_FAILED.equals(st)) {
                                failedN++;
                                if (rec.optString("lastError", "").length() > 0) {
                                    lastError = rec.optString("lastError", "");
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                JSONArray meta = readJsonArray(file(ACKED_META));
                for (int i = 0; i < meta.length(); i++) {
                    JSONObject m = meta.optJSONObject(i);
                    if (m != null && m.optLong("receivedAt", 0L) > lastAckedAt) {
                        lastAckedAt = m.optLong("receivedAt", 0L);
                    }
                }
            } catch (Throwable ignored) {
            }
            o.put("pending", pendingN);
            o.put("failed", failedN);
            o.put("acked24h", acked24);
            o.put("lastAckedAt", lastAckedAt);
            o.put("lastError", lastError);
            return o;
        } catch (Throwable t) {
            JSONObject o = new JSONObject();
            try {
                o.put("pending", 0);
                o.put("failed", 0);
                o.put("acked24h", 0);
                o.put("lastAckedAt", 0L);
                o.put("lastError", "");
            } catch (Throwable ignored) {
            }
            return o;
        }
    }

    /** 清理本地待上传日志（用户主动操作；ACK 历史元数据一并清理）。 */
    public synchronized int clearPending() {
        int n = 0;
        try {
            File dir = dir();
            File[] files = dir.listFiles();
            if (files == null) return 0;
            for (File f : files) {
                if (f.getName().startsWith("batch-")) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    n++;
                }
            }
            //noinspection ResultOfMethodCallIgnored
            file(PENDING_FILE).delete();
        } catch (Throwable ignored) {
        }
        return n;
    }

    /* ── 内部 ── */

    private void enforceBatchCap() {
        try {
            File[] files = dir().listFiles();
            if (files == null) return;
            List<File> batches = new ArrayList<File>();
            for (File f : files) {
                if (f.getName().startsWith("batch-")) batches.add(f);
            }
            if (batches.size() <= BATCH_CAP) return;
            batches.sort(Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < batches.size() - BATCH_CAP; i++) {
                //noinspection ResultOfMethodCallIgnored
                batches.get(i).delete(); // 最旧先删（UPLOADING 态正在单线程处理中，lastModified 新）
            }
        } catch (Throwable ignored) {
        }
    }

    private int firstNonCritical(JSONArray pending) {
        for (int i = 0; i < pending.length(); i++) {
            JSONObject e = pending.optJSONObject(i);
            if (e == null) return i;
            if (!isCritical(e)) return i;
        }
        return 0; // 全关键 → 丢最旧（计数语义在 STATS 事件里，不丢整类）
    }

    private boolean isCritical(JSONObject e) {
        String t = e.optString("type", "");
        for (String c : CRITICAL_TYPES) if (c.equals(t)) return true;
        String o = e.optString("outcome", "");
        for (String c : CRITICAL_OUTCOMES) if (c.equals(o)) return true;
        return false;
    }

    private JSONObject failedRec(String batchId, int eventCount, String dangerKey) throws Exception {
        JSONObject rec = new JSONObject();
        rec.put("batchId", batchId);
        rec.put("installInstanceId", installInstanceId());
        rec.put("createdAt", System.currentTimeMillis());
        rec.put("eventCount", eventCount);
        rec.put("appVersion", "");
        rec.put("versionCode", 0);
        rec.put("privacyFilterVersion", LogPrivacyFilter.VERSION);
        rec.put("retryCount", 0);
        rec.put("nextRetryAt", 0L);
        rec.put("state", STATE_FAILED);
        rec.put("lastError", "PRIVACY_REJECTED(client): " + dangerKey);
        rec.put("failKind", FAILKIND_TERMINAL); // 隐私整批拒绝：危险内容绝不重传
        rec.put("failedAt", System.currentTimeMillis());
        rec.put("events", new JSONArray());
        return rec;
    }

    private File dir() {
        File d = new File(env.filesDir(), DIR_NAME);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private File file(String name) {
        return new File(dir(), name);
    }

    private JSONArray readJsonArray(File f) {
        try {
            if (!f.exists()) return new JSONArray();
            return new JSONArray(readText(f));
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    private JSONObject readJson(File f) throws Exception {
        return new JSONObject(readText(f));
    }

    private String readText(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    private void writeJson(File f, JSONObject o) throws IOException {
        writeText(f, o.toString());
    }

    private void writeJsonArray(File f, JSONArray a) throws IOException {
        writeText(f, a.toString());
    }

    private void writeText(File f, String s) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        } finally {
            try { out.close(); } catch (IOException ignored) { }
        }
    }
}
