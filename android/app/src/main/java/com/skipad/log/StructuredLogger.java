package com.skipad.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 结构化日志器（v5.4.0）：单行 JSON 落盘 + 级别过滤 + 隐私脱敏 + 滚动文件。
 *
 * <p>与 Web 端 src/lib/structured-log.ts 保持 1:1（同构点，函数映射）：
 * maskValue(key, value) ↔ sanitizeLogData(data)、toJson(...) ↔ formatLog(entry)、
 * StructuredLogger ↔ makeLogger(...)：
 * <ul>
 *   <li>单行 JSON 五字段：{@code {"ts":..,"level":"..","module":"..","event":"..","data":{..}}}</li>
 *   <li>级别：verbose / debug / info / warn / error（release 默认 info）</li>
 *   <li>脱敏：data 内 text/desc/title/content 键截断 32 字符；pkg/package 键保留；
 *       其余字符串键截断 120 字符（脱敏规则两端一致，CI 金样本校验）</li>
 *   <li>滚动：单文件 2MB × 3 个（skipad-0.log ~ skipad-2.log，环形覆写）</li>
 * </ul>
 *
 * <p>JSON 手工构建（无第三方依赖），字段顺序固定——同构校验可直接逐字节比对
 * （时间戳除外）。
 */
public final class StructuredLogger {

    // ───────────────────────── 级别 ─────────────────────────

    public enum Level {
        VERBOSE(0, "verbose"), DEBUG(1, "debug"), INFO(2, "info"),
        WARN(3, "warn"), ERROR(4, "error");

        public final int weight;
        public final String json;

        Level(int weight, String json) {
            this.weight = weight;
            this.json = json;
        }
    }

    /** text/desc/title/content 类键截断上限（与 Web 端一致） */
    public static final int TEXT_TRUNCATE = 32;
    /** 其他字符串值截断上限 */
    public static final int VALUE_TRUNCATE = 120;
    /** 单文件上限 2MB */
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    /** 滚动文件数 3 个 */
    public static final int MAX_FILES = 3;

    // ───────────────────────── 单例 ─────────────────────────

    private static volatile StructuredLogger sInstance;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Object rotateLock = new Object();
    private File dir;                 // 日志目录（未初始化时仅 logcat）
    private Level threshold = Level.INFO;
    private long currentBytes;

    private StructuredLogger() {
    }

    public static StructuredLogger get() {
        if (sInstance == null) {
            synchronized (StructuredLogger.class) {
                if (sInstance == null) {
                    sInstance = new StructuredLogger();
                }
            }
        }
        return sInstance;
    }

    /** 初始化（Service onCreate 调用；传 null 目录则只输出 logcat） */
    public static StructuredLogger init(File logDir, Level releaseThreshold) {
        StructuredLogger l = get();
        l.dir = logDir;
        if (releaseThreshold != null) {
            l.threshold = releaseThreshold;
        }
        if (logDir != null && !logDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            logDir.mkdirs();
        }
        return l;
    }

    public void setThreshold(Level level) {
        if (level != null) {
            threshold = level;
        }
    }

    // ───────────────────────── 日志 API ─────────────────────────

    /** 可变参数键值对：log(module, event, "k1", v1, "k2", v2) */
    public void v(String module, String event, Object... kv) {
        write(Level.VERBOSE, module, event, kv);
    }

    public void d(String module, String event, Object... kv) {
        write(Level.DEBUG, module, event, kv);
    }

    public void i(String module, String event, Object... kv) {
        write(Level.INFO, module, event, kv);
    }

    public void w(String module, String event, Object... kv) {
        write(Level.WARN, module, event, kv);
    }

    public void e(String module, String event, Object... kv) {
        write(Level.ERROR, module, event, kv);
    }

    /** Map 版（引擎事件出口直接传 Map） */
    public void log(Level level, String module, String event, Map<String, Object> data) {
        if (level.weight < threshold.weight) {
            return;
        }
        String line = toJson(System.currentTimeMillis(), level, module, event, data);
        android.util.Log.println(toPriority(level), "SkipAd." + module,
                level == Level.ERROR ? line : compact(line));
        persist(line);
    }

    private void write(Level level, String module, String event, Object... kv) {
        if (level.weight < threshold.weight) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                data.put(String.valueOf(kv[i]), kv[i + 1]);
            }
        }
        log(level, module, event, data);
    }

    // ───────────────────────── 脱敏 ─────────────────────────

    /**
     * 隐私脱敏（与 Web 端 maskLogValue 同构）：
     * text/desc/title/content → 截断 32 字符；pkg/package → 保留（统计维度需要）；
     * 其余字符串 → 截断 120 字符。
     */
    static Object maskValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            return value; // 数字/布尔不脱敏
        }
        String s = (String) value;
        if ("pkg".equals(key) || "package".equals(key)) {
            return s; // 包名保留（脱敏全链路白名单）
        }
        if ("text".equals(key) || "desc".equals(key) || "title".equals(key)
                || "content".equals(key)) {
            return truncate(s, TEXT_TRUNCATE);
        }
        return truncate(s, VALUE_TRUNCATE);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ───────────────────────── JSON 构建 ─────────────────────────

    /** 单行 JSON（固定字段顺序：ts/level/module/event/data） */
    String toJson(long ts, Level level, String module, String event, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"ts\":").append(ts);
        sb.append(",\"level\":\"").append(escape(level.json)).append('"');
        sb.append(",\"module\":\"").append(escape(module)).append('"');
        sb.append(",\"event\":\"").append(escape(event)).append('"');
        sb.append(",\"data\":{");
        if (data != null) {
            boolean first = true;
            for (Map.Entry<String, Object> e : data.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escape(e.getKey())).append("\":");
                Object masked = maskValue(e.getKey(), e.getValue());
                appendValue(sb, masked);
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    /** JSON 转义（引号/反斜杠/控制字符 → \\uXXXX） */
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /** logcat 用精简形态（去 data 大字段，保留 event 与前两个键值） */
    private static String compact(String jsonLine) {
        return jsonLine.length() <= 220 ? jsonLine : jsonLine.substring(0, 220) + "...";
    }

    private static int toPriority(Level level) {
        switch (level) {
            case VERBOSE:
                return android.util.Log.VERBOSE;
            case DEBUG:
                return android.util.Log.DEBUG;
            case INFO:
                return android.util.Log.INFO;
            case WARN:
                return android.util.Log.WARN;
            case ERROR:
            default:
                return android.util.Log.ERROR;
        }
    }

    // ───────────────────────── 滚动文件 ─────────────────────────

    /** 当前写入文件（环形 0→1→2→0） */
    private File fileAt(int index) {
        return new File(dir, "skipad-" + index + ".log");
    }

    private void persist(final String line) {
        if (dir == null) {
            return;
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (rotateLock) {
                    File target = fileAt(0);
                    try {
                        rotateIfNeeded();
                        Writer w = new OutputStreamWriter(
                                new FileOutputStream(target, true),
                                Charset.forName("UTF-8"));
                        try {
                            w.write(line);
                            w.write('\n');
                        } finally {
                            w.close();
                        }
                        currentBytes += line.length() + 1;
                    } catch (IOException e) {
                        android.util.Log.w("SkipAd.log", "persist failed: " + e.getMessage());
                    }
                }
            }
        });
    }

    /** 超 2MB 滚动：2→删，1→2，0→1，新开 0 */
    private void rotateIfNeeded() throws IOException {
        File f0 = fileAt(0);
        if (!f0.exists() || f0.length() < MAX_FILE_BYTES) {
            if (currentBytes != f0.length()) {
                currentBytes = f0.exists() ? f0.length() : 0; // 进程重启后校准
            }
            return;
        }
        File f2 = fileAt(2);
        if (f2.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f2.delete();
        }
        File f1 = fileAt(1);
        if (f1.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f1.renameTo(f2);
        }
        //noinspection ResultOfMethodCallIgnored
        f0.renameTo(f1);
        currentBytes = 0;
    }
}
