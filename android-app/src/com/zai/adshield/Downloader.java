package com.zai.adshield;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import org.json.JSONObject;

import java.io.InputStream;
import java.security.MessageDigest;

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
    /* v6.3.0 安全：安装前完整性校验的期望值（与下载任务一起持久化）。
     * digest = GitHub API asset.digest（"sha256:hex"，HTTPS 获取）；
     * sizeBytes = asset.size——与下载任务同源同存，防错位。 */
    private static final String KEY_DL_SHA = "dl_sha";
    private static final String KEY_DL_SIZE = "dl_size";

    private Downloader() {
    }

    /* ───────── 启动下载 ───────── */

    /** 兼容入口（无校验元数据）——v6.3.0 起新链路一律带 digest/size。 */
    public static String start(Context ctx, String url, String fileName) {
        return start(ctx, url, fileName, "", 0);
    }

    /**
     * 用系统 DownloadManager 下载更新 APK（页内下载，不跳转浏览器）。
     * v6.3.0 安全：digest/size 为 GitHub API（HTTPS）给出的期望值，
     * 安装前由 {@link #install} 逐字节校验（SHA-256 优先，缺失时回退字节数）。
     * @return {ok:true, id} 或 {ok:false, error}
     */
    public static String start(Context ctx, String url, String fileName, String digest, long sizeBytes) {
        try {
            if (url == null || url.length() < 8) return err("下载链接无效");
            /* ═══ v4.5.0 安全：下载源白名单 + 文件名净化 ═══
             * startUpdateDownload 是 JS 桥暴露面——恶意页面（若能突破 WebView
             * URL 白名单）可把系统下载器当任意文件下载器。仅允许 https 且
             * 域名限本仓库发布通道；文件名仅 [A-Za-z0-9._-]（防路径穿越）。
             * v6.3.0 加固：域名从 contains 子串改为 URL 解析后的 host 精确/
             * 后缀匹配——"https://github.com.evil.com/x.apk"、
             * "https://attacker.com/fwd?to=github.com" 类构造 URL 全部拒绝。 */
            String u = url.trim();
            if (!u.startsWith("https://")) return err("下载链接必须为 https");
            if (!hostAllowed(u)) return err("下载链接不在允许的发布通道内");
            String fn = fileName == null ? "adshield.apk"
                    : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
            if (fn.length() > 64) fn = fn.substring(fn.length() - 64);
            if (!fn.toLowerCase().endsWith(".apk")) fn = fn + ".apk";
            url = u;
            fileName = fn;
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
            // v3.3.0：切换下载通道时清理旧任务（避免下载列表/通知堆积）
            if (oldId > 0 && !url.equals(oldUrl)) {
                try { dm.remove(oldId); } catch (Exception ignored) { }
            }
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
                    .putString(KEY_DL_SHA, digest == null ? "" : digest)
                    .putLong(KEY_DL_SIZE, sizeBytes)
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
     * v6.3.0 安全（更新链完整性闸）：拉起安装器前对下载文件流式计算 SHA-256，
     * 与 GitHub API（HTTPS 获取）的 asset digest 逐字节比对；digest 缺失时
     * 回退字节数比对（size 同样来自同一 HTTPS 响应）。不匹配 → 删除下载
     * 文件、清除任务、拒绝安装并给出明确原因——镜像代理投毒 / 代理劫持
     * / 公共 Downloads 目录 TOCTOU 换包全部由这道闸拦下。 */
    public static String install(Context ctx) {
        try {
            android.content.SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long id = sp.getLong(KEY_DL_ID, -1);
            if (id <= 0) return err("尚无下载完成的更新");
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return err("系统下载服务不可用");
            int[] st = statusOf(ctx, id);
            if (st == null || st[0] != DownloadManager.STATUS_SUCCESSFUL) return err("下载尚未完成");
            Uri uri = dm.getUriForDownloadedFile(id);
            if (uri == null) return err("取不到下载文件，可从系统下载通知栏安装");

            /* ═══ v6.3.0 完整性校验（安装前最后一道）═══
             * 期望值与下载任务同源同存（启动下载时写入）：
             *  · digest 存在 → SHA-256 全量比对（流式读取，8KB 缓冲）；
             *  · digest 缺失 → 字节数比对（弱校验，正常 Release 必有 digest）。
             * 校验失败 → dm.remove(id) 删文件 + 清 prefs + 拒绝安装。 */
            String expectSha = sp.getString(KEY_DL_SHA, "");
            long expectSize = sp.getLong(KEY_DL_SIZE, 0);
            String verify = verifyDownloadedFile(ctx, uri, expectSha, expectSize);
            if (verify != null) {
                try { dm.remove(id); } catch (Exception ignored) { }
                sp.edit().remove(KEY_DL_ID).remove(KEY_DL_URL).remove(KEY_DL_FILE)
                        .remove(KEY_DL_SHA).remove(KEY_DL_SIZE).apply();
                return err("安装包校验失败（" + verify + "）已删除，请重新下载");
            }

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

    /* ═══ v6.3.0 安全工具 ═══ */

    /**
     * 发布通道 host 白名单（URL 解析后的 host 精确/后缀匹配）。
     * 允许：github.com · *.github.com · githubusercontent.com 及子域 ·
     * ghfast.top · gh-proxy.com（v3.3.0 镜像通道）。
     * 拒绝一切构造绕过：userinfo 混淆、子串注入、尾点 FQDN。 */
    static boolean hostAllowed(String u) {
        try {
            java.net.URL url = new java.net.URL(u);
            String scheme = url.getProtocol();
            if (scheme == null || !scheme.equalsIgnoreCase("https")) return false;
            String host = url.getHost();
            if (host == null || host.length() == 0) return false;
            String h = host.toLowerCase(java.util.Locale.ROOT);
            if (h.endsWith(".")) h = h.substring(0, h.length() - 1); // 尾点 FQDN 混淆
            return h.equals("github.com") || h.endsWith(".github.com")
                    || h.equals("githubusercontent.com") || h.endsWith(".githubusercontent.com")
                    || h.equals("ghfast.top") || h.endsWith(".ghfast.top")
                    || h.equals("gh-proxy.com") || h.endsWith(".gh-proxy.com");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 下载文件完整性校验：返回 null = 通过；非 null = 失败原因。
     * expectSha 为 "sha256:hex"（GitHub API 官方格式）或空；expectSize 为
     * 期望字节数（0 = 未知）。流式读取（8KB 缓冲）同时计数字节——
     * SHA-256 与字节数双比对（digest 缺失时字节数比对兜底）。 */
    static String verifyDownloadedFile(Context ctx, Uri uri, String expectSha, long expectSize) {
        InputStream in = null;
        try {
            in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return "无法读取下载文件";
            MessageDigest md = expectSha != null && expectSha.startsWith("sha256:")
                    ? MessageDigest.getInstance("SHA-256") : null;
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                if (md != null) md.update(buf, 0, n);
                total += n;
            }
            if (expectSize > 0 && total != expectSize) {
                return "大小不符（实际 " + total + " ≠ 发布 " + expectSize + " 字节）";
            }
            if (md != null) {
                byte[] d = md.digest();
                StringBuilder sb = new StringBuilder(d.length * 2);
                for (int i = 0; i < d.length; i++) {
                    String hx = Integer.toHexString(d[i] & 0xFF);
                    if (hx.length() < 2) sb.append('0');
                    sb.append(hx);
                }
                String actual = "sha256:" + sb.toString();
                if (!actual.equalsIgnoreCase(expectSha)) {
                    return "SHA-256 不符";
                }
            } else if (expectSize <= 0) {
                return "缺少校验元数据"; // digest 与 size 双缺：拒绝（正常 Release 必有 digest）
            }
            return null; // 校验通过
        } catch (Exception e) {
            return "读取失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
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
