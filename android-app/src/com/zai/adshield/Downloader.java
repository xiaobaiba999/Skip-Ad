package com.zai.adshield;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import org.json.JSONObject;

/**
 * 应用内更新下载器（v3.0.0 · 零第三方依赖）
 * ─────────────────────────────────────────────
 * · 下载交给系统 DownloadManager（省电：由系统调度，App 进程可休眠）
 * · 进度采用拉取式：JS 每 800ms 调 updateProgress()（仅页面打开时轮询）
 * · 完成后 getUriForDownloadedFile 取 content:// URI → ACTION_VIEW 安装
 * · 不注册广播接收器（规避 targetSdk 34 的 RECEIVER_EXPORTED 语义与进程常驻）
 *
 * 约束：无 lambda/匿名类/内部类（d8 8.2 兼容）。
 */
public final class Downloader {

    private static final String PREFS = "adshield";
    private static final String KEY_DL_ID = "dl_id";
    private static final String KEY_DL_URL = "dl_url";
    private static final String KEY_DL_FILE = "dl_file";

    private Downloader() {
    }

    /* ───────── 启动下载 ───────── */

    /**
     * 用系统 DownloadManager 下载更新 APK（页内下载，不跳转浏览器）。
     * @return {ok:true, id} 或 {ok:false, error}
     */
    public static String start(Context ctx, String url, String fileName) {
        try {
            if (url == null || url.length() < 8) return err("下载链接无效");
            // 已在下载同一文件 → 幂等返回
            long oldId = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DL_ID, -1);
            String oldUrl = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DL_URL, "");
            if (oldId > 0 && url.equals(oldUrl)) {
                int[] st = statusOf(ctx, oldId);
                if (st != null && st[0] == DownloadManager.STATUS_RUNNING) {
                    JSONObject out = ok();
                    out.put("id", oldId);
                    return out.toString();
                }
            }
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return err("系统下载服务不可用");
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("开屏卫士 更新");
            req.setDescription(fileName);
            req.setMimeType("application/vnd.android.package-archive");
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            long id = dm.enqueue(req);
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_DL_ID, id)
                    .putString(KEY_DL_URL, url)
                    .putString(KEY_DL_FILE, fileName)
                    .apply();
            JSONObject out = ok();
            out.put("id", id);
            return out.toString();
        } catch (Exception e) {
            return err("启动下载失败：" + safe(e));
        }
    }

    /* ───────── 进度查询（JS 拉取式） ───────── */

    /**
     * 查询当前下载状态。
     * @return {ok:true, state: idle|running|paused|done|failed, percent, bytes, total, fileName}
     */
    public static String progress(Context ctx) {
        try {
            long id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DL_ID, -1);
            JSONObject out = ok();
            if (id <= 0) {
                out.put("state", "idle");
                out.put("percent", 0);
                out.put("bytes", 0);
                out.put("total", 0);
                out.put("fileName", "");
                return out.toString();
            }
            int[] st = statusOf(ctx, id);
            if (st == null) {
                out.put("state", "failed");
                out.put("percent", 0);
                out.put("bytes", 0);
                out.put("total", 0);
                return out.toString();
            }
            int status = st[0];
            long soFar = st[1];
            long total = st[2];
            String state;
            if (status == DownloadManager.STATUS_SUCCESSFUL) state = "done";
            else if (status == DownloadManager.STATUS_RUNNING) state = "running";
            else if (status == DownloadManager.STATUS_PAUSED) state = "paused";
            else if (status == DownloadManager.STATUS_PENDING) state = "running";
            else state = "failed";
            out.put("state", state);
            out.put("percent", total > 0 ? (int) (soFar * 100 / total) : 0);
            out.put("bytes", soFar);
            out.put("total", total);
            out.put("fileName", ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DL_FILE, ""));
            return out.toString();
        } catch (Exception e) {
            return err("查询失败：" + safe(e));
        }
    }

    private static int[] statusOf(Context ctx, long id) {
        try {
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return null;
            DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
            Cursor c = dm.query(q);
            if (c == null) return null;
            int[] r = null;
            if (c.moveToFirst()) {
                r = new int[]{
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        (int) c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        (int) c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                };
            }
            c.close();
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    /* ───────── 安装 ───────── */

    /**
     * 拉起系统安装器（content:// URI + FLAG_GRANT_READ_URI_PERMISSION）。
     * Android 8+ 安装未知应用：未授权时引导到授权页（返回 {ok:false, needPermission:true}）。
     */
    public static String install(Context ctx) {
        try {
            long id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DL_ID, -1);
            if (id <= 0) return err("尚无下载完成的更新");
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return err("系统下载服务不可用");
            int[] st = statusOf(ctx, id);
            if (st == null || st[0] != DownloadManager.STATUS_SUCCESSFUL) return err("下载尚未完成");
            Uri uri = dm.getUriForDownloadedFile(id);
            if (uri == null) return err("取不到下载文件，可从系统下载通知栏安装");

            // Android 8+「安装未知应用」授权
            if (android.os.Build.VERSION.SDK_INT >= 26 && !ctx.getPackageManager().canRequestPackageInstalls()) {
                JSONObject out = new JSONObject();
                out.put("ok", false);
                out.put("needPermission", true);
                return out.toString();
            }
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(uri, "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
            return ok().toString();
        } catch (Exception e) {
            // 个别 ROM 对 file:// URI 抛 FileUriExposedException → 引导走系统通知栏
            return err("拉起安装失败（" + safe(e) + "），可点系统下载完成通知安装");
        }
    }

    /** 打开「安装未知应用」授权页（Bridge 调用） */
    public static void openInstallPermission(Context ctx) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                Intent it = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ctx.getPackageName()));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(it);
            }
        } catch (Exception ignored) {
        }
    }

    /* ───────── 工具 ───────── */

    private static JSONObject ok() throws Exception {
        JSONObject o = new JSONObject();
        o.put("ok", true);
        return o;
    }

    private static String err(String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", msg);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    private static String safe(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
