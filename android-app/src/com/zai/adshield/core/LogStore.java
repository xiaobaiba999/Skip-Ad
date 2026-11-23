package com.zai.adshield.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

/**
 * v8.2.2 简化日志存储（替换 v8.0.0~v8.2.1 的 LogBatchStore 六状态机——
 * v8.2.1 真机实锤「一键上传提示暂无可上传」根因即采集门绑定自动上传开关，
 * 本版采集常开）。
 * ─────────────────────────────────────────────
 * 业务只保留四件事（用户 v8.2.2 指令）：
 *  ① 事件采集常开——appendEvent 无条件落盘（不再有 collectEnabled 门）；
 *  ② 重启保留——单文件 pending-events.json 持久化；
 *  ③ 失败保留——只有服务端确认（HTTP 2xx 且 body ok=true）才消费事件；
 *  ④ 24h 成功配额——ok-times.json 滚动窗口时间戳（只记成功）。
 *
 * 幂等契约（服务端不可变）：每批 ≤48 事件；batch-manifest.json 按序保存
 * 「已 POST 未决」块的 batchId——超时重试复用同 batchId，服务端幂等
 * （ALREADY_ACCEPTED）绝不产生第二个 Issue。manifest 头部始终对应事件
 * 文件头部 48 条：成功/拒收从头消费，天然对齐。
 *
 * ═══ v7.9.8 冻结批次（真机实锤「第一次失败、第二次被服务端拒」根因修复）═══
 * v8.2.2 简化版每次 runUpload 重建 payload：createdAt 取当次时间 + STATS
 * 快照重新生成 + 新事件并入头部块 —— 同 batchId 但 payload 必然变化。
 * 服务端 LogGate 幂等按「batchId + payload 指纹」判定：首次 POST 已被
 * 接受（Issue 已建）但响应丢失（读超时）后，重试的漂移 payload 命中
 * IDEMPOTENCY_CONFLICT 终态拒收 —— 用户真机两连失败的完整链条。
 * 修复：封批时把【batchId + createdAt + 事件数组】整体冻结进 manifest
 * （v8.0.0 LogBatchStore 冻结语义在简化架构下的复归）——重试字节级
 * 复用同一 payload，服务端必然 ALREADY_ACCEPTED 复用原编号。
 *
 * manifest 记录两代格式：
 *  · v2（v7.9.8+）：{batchId, at, createdAt, realCount, events:[…]}；
 *  · v1（v8.2.2~v7.9.7 遗留）：{batchId, at} —— 首次读到时保留 batchId
 *    补冻当前头部事件（旧 payload 从未稳定过，无法复现；若服务端已记
 *    录该 batchId 则维持既有冲突结局，不劣于升级前，诚实记录在案）。
 *
 * 事件上限 2000：溢出丢最旧并清空 manifest（集合已变，旧 batchId 作废；
 * 极端场景下「超时未决块被裁剪」可能造成服务端重复建 Issue——概率极低
 * 且仅损及 Issue 数量，诚实记录在案）。
 *
 * 线程纪律：appendEvent 异步单线程落盘（无障碍热路径零阻塞——v8.0.0
 * 红线保持）；消费/快照方法由 LogUploader 串行调用。
 */
public final class LogStore {

    private static final String PREFS = "logupload";
    private static final String KEY_INSTALL_ID = "installInstanceId";
    private static final String KEY_TOTAL_COLLECTED = "totalCollected";
    private static final String KEY_APPEND_FAIL_AT = "appendFailAt";
    private static final String DIR_NAME = "log-upload";
    private static final String PENDING_FILE = "pending-events.json";
    private static final String MANIFEST_FILE = "batch-manifest.json";
    private static final String OK_TIMES_FILE = "ok-times.json";
    private static final String AUTO_OK_TIMES_FILE = "auto-ok-times.json";
    private static final String REJECTED_FILE = "rejected.json";
    /** 事件缓冲上限（v8.x 400 → 2000：重度用户约 40 天余量，自动上传常态排空）。 */
    static final int EVENT_CAP = 2000;
    /** 服务端契约：单批事件上限（validate.ts 硬界）。 */
    public static final int EVENTS_PER_BATCH = 48;
    /** 拒收检视上限（环形——超出丢最旧，防无限增长）。 */
    private static final int REJECTED_CAP = 50;
    private static final long DAY_MS = 24L * 3600 * 1000;

    /* ═══ 环境 seam（Android 生产 / JVM 测试同源——v8.0.0 模式沿用）═══ */

    public interface StoreEnv {
        String prefGet(String key);

        void prefPut(String key, String value);

        File filesDir();
    }

    static final class AndroidEnv implements StoreEnv {
        private final Context ctx;

        AndroidEnv(Context ctx) {
            this.ctx = ctx;
        }

        @Override
        public String prefGet(String key) {
            try {
                return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null);
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

    private static volatile LogStore instance;

    public static LogStore get(Context ctx) {
        if (instance == null) {
            synchronized (LogStore.class) {
                if (instance == null) instance = new LogStore(new AndroidEnv(ctx.getApplicationContext()));
            }
        }
        return instance;
    }

    /** JVM 测试入口。 */
    public static LogStore forEnv(StoreEnv env) {
        return new LogStore(env);
    }

    private final StoreEnv env;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "log-store-io");
        t.setDaemon(true);
        return t;
    });

    private LogStore(StoreEnv env) {
        this.env = env;
        migrateLegacy();
    }

    /* ── 旧版遗留清理（v8.0.0~v8.2.1 升级路径）：batch-{uuid}.json 状态机
     * 文件与 acked-meta.json 由本版废弃——删除防孤儿堆积；pending-events.json
     * 格式不变直接继承（升级用户未上传事件无损保留）。
     * v7.9.8b 修复（T14 回归钉死）：旧规则 startsWith("batch-") 会连带删除
     * 本版冻结清单 batch-manifest.json——进程每次重启丢冻结状态，若首 POST
     * 已被服务端接受（Issue 已建），重启后重试换新 batchId = 重复建 Issue，
     * 幂等契约断裂。遗留文件实名 batch-{uuid}.json，清单排除即可。 */
    private void migrateLegacy() {
        try {
            File[] files = dir().listFiles();
            if (files == null) return;
            for (File f : files) {
                String n = f.getName();
                if ((n.startsWith("batch-") && !n.equals(MANIFEST_FILE))
                        || n.equals("acked-meta.json")) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /* ── installInstanceId（沿用 v8.0.0 prefs 键——升级用户身份延续）── */

    public String installInstanceId() {
        String id = env.prefGet(KEY_INSTALL_ID);
        if (!LogUploader.isValidUuid(id)) {
            id = UUID.randomUUID().toString();
            env.prefPut(KEY_INSTALL_ID, id);
        }
        return id;
    }

    /* ── 采集（常开——v8.2.2 根因修复）── */

    /** 追加一条结构化事件（LogEvents 工厂产物；异步落盘，调用方零阻塞）。 */
    public void appendEvent(final JSONObject event) {
        if (event == null) return;
        io.submit(() -> {
            try {
                synchronized (this) {
                    JSONArray pending = readJsonArray(file(PENDING_FILE));
                    boolean trimmed = false;
                    while (pending.length() >= EVENT_CAP) {
                        pending.remove(0);
                        trimmed = true;
                    }
                    pending.put(event);
                    writeJsonArray(file(PENDING_FILE), pending);
                    if (trimmed) {
                        writeJsonArray(file(MANIFEST_FILE), new JSONArray()); // 集合已变：旧 batchId 作废
                    }
                    bumpTotalCollected();
                }
            } catch (Throwable t) {
                try {
                    env.prefPut(KEY_APPEND_FAIL_AT, String.valueOf(System.currentTimeMillis()));
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** JVM 测试钩子：排空异步落盘队列（生产永不调用）。 */
    public void awaitIoForJvm() {
        try {
            io.submit(() -> { }).get();
        } catch (Throwable ignored) {
        }
    }

    /* ── 读取（同步——LogUploader 串行调用）── */

    /* 最近一次 pending 文件读取是否损坏（文件存在且非空但解析失败——
     * 手动上传「读取失败」态判定；空/不存在不算损坏）。 */
    private boolean pendingCorrupt = false;

    private JSONArray readPending() {
        pendingCorrupt = false;
        try {
            File f = file(PENDING_FILE);
            if (!f.exists()) return new JSONArray();
            JSONArray a = readJsonArray(f);
            if (a.length() == 0 && f.length() > 2) pendingCorrupt = true;
            return a;
        } catch (Throwable t) {
            pendingCorrupt = true;
            return new JSONArray();
        }
    }

    /** 待传事件全量快照（顺序即采集顺序；调用方自行分块）。 */
    public synchronized JSONArray pendingEvents() {
        return readPending();
    }

    public synchronized int pendingEventCount() {
        return readPending().length();
    }

    /** pending 文件是否损坏（手动上传四态区分——读取失败）。 */
    public synchronized boolean pendingFileCorrupt() {
        readPending();
        return pendingCorrupt;
    }

    /** 累计采集事件总数（含已上传——手动上传四态区分用：0=从未产生）。 */
    public long totalCollected() {
        try {
            return Long.parseLong(env.prefGet(KEY_TOTAL_COLLECTED) == null ? "0"
                    : env.prefGet(KEY_TOTAL_COLLECTED));
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 最近一次采集写入失败时间（0=无——手动上传「采集失败」态判定）。 */
    public long appendFailAt() {
        try {
            String v = env.prefGet(KEY_APPEND_FAIL_AT);
            return v == null ? 0 : Long.parseLong(v);
        } catch (Throwable t) {
            return 0;
        }
    }

    /* ── manifest（batchId 稳定性 + v7.9.8 冻结 payload——服务端幂等契约）── */

    /** 冻结批次（v2 manifest 记录——重试字节级复用）。 */
    public static final class FrozenBatch {
        public String batchId = "";
        public String createdAt = "";
        public int realCount;      // pending 中真实事件数（events 可能含注入 STATS）
        public JSONArray events = new JSONArray();
    }

    /**
     * 读取头部冻结批次（无头部或不可用 → null）。
     * v2 记录（含 events）直接返回；v1 遗留记录（仅 batchId）返回
     * batchId + 空 events（realCount=-1 标记）——由 sealBatch 补冻。
     */
    public synchronized FrozenBatch headFrozenOrNull() {
        JSONArray m = readJsonArray(file(MANIFEST_FILE));
        if (m.length() == 0) return null;
        JSONObject head = m.optJSONObject(0);
        if (head == null) return null;
        String id = head.optString("batchId", "");
        if (!LogUploader.isValidUuid(id)) return null;
        FrozenBatch fb = new FrozenBatch();
        fb.batchId = id;
        JSONArray ev = head.optJSONArray("events");
        if (ev != null && ev.length() > 0) {
            fb.events = ev;
            fb.realCount = head.optInt("realCount", ev.length());
            fb.createdAt = head.optString("createdAt", "");
            if (fb.createdAt.length() == 0) fb.createdAt = isoOf(head.optLong("at", 0L));
            return fb;
        }
        fb.realCount = -1; // v1 遗留：未冻结 payload
        return fb;
    }

    /**
     * 封批冻结（POST 前调用——先落盘再发请求）：
     *  · 头部已有 v2 冻结记录 → 原样返回（重试不换号不换 payload）；
     *  · 头部是 v1 遗留记录 → 保留 batchId 补冻当前 chunk（一次性迁移）；
     *  · 无头部 → 新 UUID 冻结 chunk。
     * 返回冻结批次；失败返回 null（调用方绝不明知违约外发）。 */
    public synchronized FrozenBatch sealBatch(JSONArray chunk, String createdAt) {
        try {
            if (chunk == null || chunk.length() == 0) return null;
            JSONArray m = readJsonArray(file(MANIFEST_FILE));
            JSONObject head = m.length() > 0 ? m.optJSONObject(0) : null;
            String headId = head == null ? "" : head.optString("batchId", "");
            boolean headFrozen = head != null && head.optJSONArray("events") != null
                    && head.optJSONArray("events").length() > 0;
            if (head != null && headFrozen) {
                FrozenBatch fb = headFrozenOrNull();
                if (fb != null && fb.realCount >= 0) return fb; // 已冻结：字节级复用
            }
            FrozenBatch fb = new FrozenBatch();
            fb.batchId = LogUploader.isValidUuid(headId) ? headId : LogUploader.newBatchId();
            fb.createdAt = createdAt;
            fb.events = chunk;
            fb.realCount = countReal(chunk);
            JSONObject rec = new JSONObject();
            rec.put("batchId", fb.batchId);
            rec.put("at", System.currentTimeMillis());
            rec.put("createdAt", createdAt);
            rec.put("realCount", fb.realCount);
            rec.put("events", chunk);
            if (head != null) m.remove(0); // v1 遗留头部替换为 v2 冻结记录
            m.put(rec);
            writeJsonArray(file(MANIFEST_FILE), m);
            return fb;
        } catch (Throwable t) {
            return null;
        }
    }

    /** chunk 中非 STATS 注入事件数（STATS 事件首键 type=STATS——注入事件
     *  不占 pending 名额，消费时不得从 pending 多删）。 */
    private static int countReal(JSONArray chunk) {
        int n = 0;
        for (int i = 0; i < chunk.length(); i++) {
            JSONObject e = chunk.optJSONObject(i);
            if (e != null && !"STATS".equals(e.optString("type", ""))) n++;
        }
        return n;
    }

    private static String isoOf(long ms) {
        if (ms <= 0) return "";
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.format(new java.util.Date(ms));
        } catch (Throwable t) {
            return "";
        }
    }

    /** 消费 manifest 头部（该块已了结：成功/拒收）+ 删除事件文件头部 n 条。 */
    public synchronized void consumeHead(int nEvents) {
        try {
            JSONArray m = readJsonArray(file(MANIFEST_FILE));
            if (m.length() > 0) m.remove(0);
            writeJsonArray(file(MANIFEST_FILE), m);
            JSONArray pending = readJsonArray(file(PENDING_FILE));
            for (int i = 0; i < nEvents && pending.length() > 0; i++) pending.remove(0);
            writeJsonArray(file(PENDING_FILE), pending);
        } catch (Throwable ignored) {
        }
    }

    /* ── 拒收隔离（服务端终态码——重试必然再拒，保留检视不再自动重试）── */

    public synchronized void rejectHead(int nEvents, String code, String error) {
        try {
            JSONArray pending = readJsonArray(file(PENDING_FILE));
            JSONArray moved = new JSONArray();
            for (int i = 0; i < nEvents && pending.length() > 0; i++) moved.put(pending.remove(0));
            writeJsonArray(file(PENDING_FILE), pending);
            JSONArray m = readJsonArray(file(MANIFEST_FILE));
            if (m.length() > 0) m.remove(0);
            writeJsonArray(file(MANIFEST_FILE), m);
            JSONArray rej = readJsonArray(file(REJECTED_FILE));
            JSONObject rec = new JSONObject();
            rec.put("at", System.currentTimeMillis());
            rec.put("code", code == null ? "" : code);
            rec.put("error", error == null ? "" : error);
            rec.put("events", moved);
            rej.put(rec);
            while (rej.length() > REJECTED_CAP) rej.remove(0);
            writeJsonArray(file(REJECTED_FILE), rej);
        } catch (Throwable ignored) {
        }
    }

    public synchronized int rejectedCount() {
        return readJsonArray(file(REJECTED_FILE)).length();
    }

    /* ── 成功时间账本（只记成功——服务端确认才记账）──
     * v7.10.0 上传次数限制彻底移除：ok-times.json（全部成功）与
     * auto-ok-times.json（仅自动成功）两账本继续留存作历史记录，但
     * 已不再被任何闸门/展示读取（24h/3 自动闸、acked24h 展示、
     * autoEligible 第三重闸均已删除）。服务端 per-minute 瞬时防爆层
     * RATE_LIMITED 仍如实转达（客户端不越权绕过，也不人为设限）。 */

    /** 记录一次成功（全部账本；manual=true 时不记自动账本）。 */
    public synchronized void recordOk(long ts, boolean manual) {
        try {
            JSONArray t = readJsonArray(file(OK_TIMES_FILE));
            t.put(ts);
            JSONArray kept = new JSONArray();
            for (int i = 0; i < t.length(); i++) {
                if (ts - t.optLong(i, 0L) < DAY_MS) kept.put(t.optLong(i, 0L));
            }
            writeJsonArray(file(OK_TIMES_FILE), kept);
            if (!manual) {
                JSONArray a = readJsonArray(file(AUTO_OK_TIMES_FILE));
                a.put(ts);
                JSONArray keptA = new JSONArray();
                for (int i = 0; i < a.length(); i++) {
                    if (ts - a.optLong(i, 0L) < DAY_MS) keptA.put(a.optLong(i, 0L));
                }
                writeJsonArray(file(AUTO_OK_TIMES_FILE), keptA);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 兼容入口：只记全部账本（自动账本由 recordOk(ts,false) 管）。 */
    public synchronized void recordOk(long ts) {
        recordOk(ts, true);
    }

    /* v7.10.0：okCount24h/autoOkCount24h/countInWindow 已随「上传次数限制
     * 彻底移除」专项删除——账本写入保留（recordOk），计数读取方已不存在。
     * 死灰复燃防线见 LogStoreTest S4（反射断言不得回场）。 */

    public synchronized long lastOkAt() {
        JSONArray t = readJsonArray(file(OK_TIMES_FILE));
        long last = 0;
        for (int i = 0; i < t.length(); i++) {
            if (t.optLong(i, 0L) > last) last = t.optLong(i, 0L);
        }
        return last;
    }

    /* ── 概览（设置页状态行 + 手动上传四态区分）── */

    public synchronized JSONObject overview(long now) {
        JSONObject o = new JSONObject();
        try {
            int pendingEvents = readPending().length();
            o.put("pending", (pendingEvents + EVENTS_PER_BATCH - 1) / EVENTS_PER_BATCH);
            o.put("pendingEvents", pendingEvents);
            o.put("readCorrupt", pendingCorrupt);
            o.put("lastAckedAt", lastOkAt());
            o.put("rejected", rejectedCount());
            o.put("totalCollected", totalCollected());
            o.put("appendFailAt", appendFailAt());
        } catch (Throwable ignored) {
        }
        return o;
    }

    /** 清理本地待上传日志（用户主动操作；拒收检视一并清理）。返回清理事件数。 */
    public synchronized int clearPending() {
        int n = 0;
        try {
            n = readJsonArray(file(PENDING_FILE)).length();
            //noinspection ResultOfMethodCallIgnored
            file(PENDING_FILE).delete();
            //noinspection ResultOfMethodCallIgnored
            file(MANIFEST_FILE).delete();
            //noinspection ResultOfMethodCallIgnored
            file(REJECTED_FILE).delete();
        } catch (Throwable ignored) {
        }
        return n;
    }

    /* ── 内部 ── */

    private void bumpTotalCollected() {
        try {
            long n = totalCollected() + 1;
            env.prefPut(KEY_TOTAL_COLLECTED, String.valueOf(n));
        } catch (Throwable ignored) {
        }
    }

    private File dir() {
        File d = new File(env.filesDir(), DIR_NAME);
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        return d;
    }

    private File file(String name) {
        return new File(dir(), name);
    }

    private JSONArray readJsonArray(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            in.close();
            String s = sb.toString().trim();
            if (s.startsWith("[")) return new JSONArray(s);
        } catch (Throwable ignored) {
        }
        return new JSONArray();
    }

    private void writeJsonArray(File f, JSONArray a) {
        try {
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(a.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
            if (!tmp.renameTo(f)) {
                // rename 跨设备失败兜底：直接覆写（非原子窗口极小，事件缓冲可容忍）
                FileOutputStream out2 = new FileOutputStream(f);
                out2.write(a.toString().getBytes(StandardCharsets.UTF_8));
                out2.close();
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Throwable ignored) {
        }
    }

}
