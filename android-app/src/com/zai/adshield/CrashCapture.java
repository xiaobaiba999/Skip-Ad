package com.zai.adshield;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * v5.5.0 崩溃采集（日志上传四类内容之「异常堆栈」的采集端）
 * ─────────────────────────────────────────────────────────────
 * · install()：MainActivity.onCreate 安装全局 UncaughtExceptionHandler，
 *   崩溃堆栈同步写入 cacheDir/last_crash.txt（8KB 截断——崩溃路径上必须
 *   极快完成，小文件同步写可接受），随后交回系统默认 handler（进程照常
 *   终止，绝不吞崩溃、绝不复活——合规红线）。
 * · readAndClear()：Bridge.crashDump() 消费端——「上传日志」时读取并
 *   立即删除（一次性消费，崩溃堆栈不长期留存于设备，隐私最小化）。
 * · 写入内容：线程名 + 异常类型 + 消息 + 堆栈 + 版本/时间头。
 *   堆栈只含类名/方法名/行号（代码结构信息，非用户数据），且上传前
 *   还要过 Net.maskSensitive 二次脱敏。
 * · d8 8.2 规约：不使用匿名内部类（lambda 允许，项目统一风格）。
 * · v7.15.0 崩溃循环自愈：短窗内连续多次崩溃 = 持久化状态病态循环
 *   （用户实锤的「只能重装」类死局的免重装出路）——install 时检测
 *   crash_marks（20 分钟内 ≥3 次）→ 重置易病态的 UI 持久键（state /
 *   提示键）并记入崩溃文件供下次日志上传定位；数据键（白名单/订阅
 *   URL）不动（非崩溃嫌疑面）。
 */
public final class CrashCapture {

    /** 崩溃堆栈留存上限（字节）：够定位根因，又不会拖慢崩溃路径 */
    static final int MAX_BYTES = 8192;

    private static final String FILE_NAME = "last_crash.txt";

    /* ═══ v7.15.0 崩溃循环自愈（免重装出路）═══ */
    private static final String MARKS_NAME = "crash_marks.txt";
    /** 自愈判定窗口：短窗内连续崩溃 = 病态循环 */
    private static final long HEAL_WINDOW_MS = 20 * 60 * 1000L;
    private static final int HEAL_MIN_CRASHES = 3;

    private CrashCapture() {
    }

    /** 安装全局崩溃捕获（MainActivity.onCreate 调用一次） */
    public static void install(final Context ctx) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) -> {
            try {
                save(ctx, t, e);
            } catch (Throwable ignored) {
                // 崩溃捕获自身绝不能抛——捕获失败就静默放弃
            }
            if (prev != null) {
                prev.uncaughtException(t, e); // 交回系统（弹「应用已停止」/写 tombstone）
            } else {
                Runtime.getRuntime().exit(2);
            }
        });
    }

    /** 崩溃堆栈写文件（截 MAX_BYTES）；同步追加崩溃时间戳标记（供自愈判定） */
    static void save(Context ctx, Thread t, Throwable e) {
        try {
            appendMark(ctx);
        } catch (Throwable ignored) {
        }
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(bo, true, "UTF-8");
            ps.println("version=" + com.zai.adshield.MainActivity.versionNameOf(ctx)
                    + " thread=" + t.getName()
                    + " at=" + System.currentTimeMillis());
            ps.println(e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
            e.printStackTrace(ps);
            ps.flush();
            byte[] all = bo.toByteArray();
            int len = Math.min(all.length, MAX_BYTES);
            File f = new File(ctx.getCacheDir(), FILE_NAME);
            FileOutputStream os = new FileOutputStream(f, false);
            try {
                os.write(all, 0, len);
            } finally {
                try { os.close(); } catch (Exception ignored) { }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 读取并清除上次崩溃堆栈（无崩溃/读失败返回空串）——一次性消费 */
    public static String readAndClear(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), FILE_NAME);
            if (!f.exists() || f.length() == 0) return "";
            InputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            try {
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            } finally {
                try { in.close(); } catch (Exception ignored) { }
            }
            try { f.delete(); } catch (Exception ignored) { }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    /** v7.15.0：追加崩溃时间戳标记（一行一数字，自愈判定用） */
    private static void appendMark(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), MARKS_NAME);
            FileOutputStream os = new FileOutputStream(f, true);
            try {
                os.write((System.currentTimeMillis() + "\n").getBytes(StandardCharsets.UTF_8));
            } finally {
                try { os.close(); } catch (Exception ignored) { }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * v7.15.0 崩溃循环自愈：20 分钟内 ≥3 次崩溃 = 持久化状态病态循环。
     * 重置易病态的 UI 持久键（"state" 页面状态 + 提示键），数据键（白名单/
     * 订阅）不动；自愈动作记入崩溃文件（下次日志上传自动附带，可定位根因）；
     * 标记裁剪至最近一次（下一轮重新计数）。
     * @return true = 已执行自愈（MainActivity.onCreate 每次启动调用）
     */
    public static boolean selfHealIfNeeded(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), MARKS_NAME);
            if (!f.exists()) return false;
            InputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            try {
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            } finally {
                try { in.close(); } catch (Exception ignored) { }
            }
            String[] rows = new String(bo.toByteArray(), StandardCharsets.UTF_8).split("\\n");
            long now = System.currentTimeMillis();
            int recent = 0;
            for (String row : rows) {
                try {
                    long ts = Long.parseLong(row.trim());
                    if (now - ts < HEAL_WINDOW_MS) recent++;
                } catch (Exception ignored) {
                }
            }
            // 裁剪标记：只留最近一次（历史崩溃不无限累计误触自愈）
            try {
                FileOutputStream os = new FileOutputStream(f, false);
                try {
                    os.write((now + "\n").getBytes(StandardCharsets.UTF_8));
                } finally {
                    try { os.close(); } catch (Exception ignored) { }
                }
            } catch (Exception ignored) {
            }
            if (recent >= HEAL_MIN_CRASHES) {
                android.content.SharedPreferences sp = ctx.getSharedPreferences(
                        "adshield", Context.MODE_PRIVATE);
                sp.edit().remove("state")
                        .remove("tipDismissUntil").remove("tipPinVer").apply();
                try {
                    FileOutputStream os = new FileOutputStream(
                            new File(ctx.getCacheDir(), FILE_NAME), true);
                    try {
                        os.write(("\nselfheal=1 崩溃循环自愈已重置页面状态（20 分钟内 "
                                + recent + " 次崩溃）\n").getBytes(StandardCharsets.UTF_8));
                    } finally {
                        try { os.close(); } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
