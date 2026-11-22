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
 */
public final class CrashCapture {

    /** 崩溃堆栈留存上限（字节）：够定位根因，又不会拖慢崩溃路径 */
    static final int MAX_BYTES = 8192;

    private static final String FILE_NAME = "last_crash.txt";

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

    /** 崩溃堆栈写文件（截 MAX_BYTES） */
    static void save(Context ctx, Thread t, Throwable e) {
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
            /* v8.0.0 上传事件埋点（尽力而为）：CRASH 事件仅含顶层栈帧类名
             *  ——堆栈正文/消息文本绝不入事件（白名单外）。崩溃路径上进程
             *  即将死亡，executor 刷盘大概率赶不上（诚实口径：此事件多数
             *  丢失；幸存路径靠 readAndClear 侧补点）。 */
            try {
                com.zai.adshield.core.LogStore.get(ctx).appendEvent(
                        com.zai.adshield.core.LogEvents.crash(topFrameClass(e)));
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    /** v8.0.0：崩溃顶层栈帧类名（无堆栈取异常类名——绝不带消息/堆栈正文）。 */
    static String topFrameClass(Throwable e) {
        try {
            StackTraceElement[] st = e.getStackTrace();
            if (st != null && st.length > 0 && st[0].getClassName() != null) {
                return st[0].getClassName();
            }
        } catch (Throwable ignored) {
        }
        return e.getClass().getName();
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
            /* v8.0.0 上传事件埋点：崩溃幸存补点（手动导出时消费 last_crash.txt
             *  ——崩溃路径上的 save 埋点大概率赶不上进程死亡，此处是 CRASH
             *  事件真正可靠的入口；仅类名，无堆栈正文）。 */
            try {
                String head = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                int firstLineEnd = head.indexOf('\n');
                // 首行是 version/thread/at 元数据——第二行是「异常类名: 消息」；仅取类名
                if (firstLineEnd > 0) {
                    int secondLineEnd = head.indexOf('\n', firstLineEnd + 1);
                    String second = secondLineEnd > firstLineEnd
                            ? head.substring(firstLineEnd + 1, secondLineEnd)
                            : head.substring(firstLineEnd + 1);
                    int colon = second.indexOf(": ");
                    if (colon > 0) second = second.substring(0, colon);
                    second = second.trim();
                    if (second.length() > 0) {
                        com.zai.adshield.core.LogStore.get(ctx).appendEvent(
                                com.zai.adshield.core.LogEvents.crash(second));
                    }
                }
            } catch (Throwable ignored) {
            }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }
}
