package com.zai.adshield;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * v7.15.0 静态更新清单多源链（「检查更新不到新版本」结构性根治）。
 * ─────────────────────────────────────────────────────────────────
 * 历史（三连实锤）：v7.11.0 检测不到 v7.12.0、v7.12.0 检测不到 v7.13.0、
 * v7.13.0 之后复发——单一 jsDelivr @main 清单源在发布后存在【源站级分支
 * 缓存窗口】（TTL 最长 12 小时，边缘 purge 无法清除源站的分支→commit 解析
 * 缓存），期间所有旧客户端一律看到陈旧清单「已是最新」。
 *
 * 本轮结构性根治（用户红线指令：以后的版本禁止再出现检查更新不到新版本）：
 *  · 多源合流，取 versionCode 最大者——任一源活着即可检出新版：
 *    ① raw.githubusercontent.com @main（Fastly CDN，分钟级缓存，分支新鲜）
 *    ② cdn.jsdelivr.net @main（国内可达；可能带 ≤12h 源站陈旧窗口）
 *    ③ @tag 探测链（tag 引用不可变、零源站缓存——发布即新鲜；候选 =
 *       当前版本之后的下一补丁 + 下两个 minor 的四个 tag）
 *    ④ GitHub API /releases/latest（令牌直连，Net.latestRelease 兜底）
 *  · @main 陈旧只代表该源暂时看不到新版，不再阻塞整体结果；@tag 探测链
 *    在发布后第一时间命中新 tag 树内的清单（发布流程保证：清单先于打 tag
 *    提交——tag 树自含本版清单）。
 *  · 结果 10 分钟内存缓存 + synchronized 单飞——手动检查更新高频点击
 *    不产生源风暴。
 *  · 全链仅后台线程调用（v7.15 桥接层异步化——JavaBridge 线程零阻塞）。
 *
 * 约束：同 Net——不使用匿名类/内部类/lambda（d8 8.2 规约）。
 */
public final class UpdateManifest {

    private UpdateManifest() {
    }

    /** 仓库常量（与 Net 同源） */
    private static final String RAW_MAIN = "https://raw.githubusercontent.com/"
            + Net.REPO + "/main/update/latest.json";
    private static final String JSDELIVR_MAIN = "https://cdn.jsdelivr.net/gh/"
            + Net.REPO + "@main/update/latest.json";
    private static final String JSDELIVR_TAG = "https://cdn.jsdelivr.net/gh/"
            + Net.REPO + "@";

    /** 各源超时（秒）：@main 源连 6 读 6；@tag 探测快失败连 3 读 3 */
    private static final int MAIN_CONNECT_S = 6, MAIN_READ_S = 6;
    private static final int TAG_CONNECT_S = 3, TAG_READ_S = 3;

    /** 结果缓存 10 分钟（静态，进程内） */
    private static final long CACHE_MS = 10 * 60 * 1000L;
    private static volatile String cacheJson = null;
    private static volatile long cacheAt = 0L;
    private static volatile int cacheForCode = 0;

    /**
     * 检查更新（多源合流）。返回给页面的 JSON 与历史 latestRelease 同形：
     * {ok, hasRelease, tag, name, notes, publishedAt, url, headlines[],
     *  apkUrl, apkFileName, apkMB, apkSize, apkDigest, apkMirrors[], source}
     * 多源全部失败时 {ok:false, error}——如实报错，绝不伪造「已是最新」。
     *
     * @param versionName 当前版本（如 7.15.0）
     * @param versionCode 当前 versionCode（如 85000）
     */
    public static synchronized String fetchLatestUpdate(String versionName, int versionCode) {
        boolean cacheValid = cacheJson != null
                && cacheForCode == versionCode
                && System.currentTimeMillis() - cacheAt < CACHE_MS;
        if (cacheValid) return cacheJson;

        int[] cur = parse3(versionName);
        Candidate best = null;
        String lastError = "";

        /* ── ①② @main 两源（可能陈旧——但陈旧不阻塞合流）── */
        Candidate raw = fetchManifest(RAW_MAIN, MAIN_CONNECT_S, MAIN_READ_S, "raw@main");
        if (raw != null && raw.valid && raw.versionCode > bestCode(best, versionCode)) best = raw;
        else if (raw == null || !raw.valid) lastError = "raw@main 不可达";

        Candidate jsd = fetchManifest(JSDELIVR_MAIN, MAIN_CONNECT_S, MAIN_READ_S, "jsdelivr@main");
        if (jsd != null && jsd.valid && jsd.versionCode > bestCode(best, versionCode)) best = jsd;
        else if (jsd == null || !jsd.valid) lastError = lastError + " · jsdelivr@main 不可达";

        /* ── ③ @tag 探测链（不可变键零源站缓存——发布后第一时间新鲜）──
         * 仅在前两源没有拿到比当前更新的结果时才探测（拿到了就不必）；
         * 候选集：下一补丁 + 下两个 minor（覆盖正常迭代步长）。 */
        if (bestCode(best, versionCode) <= versionCode && cur != null) {
            String[] tags = candidateTags(cur[0], cur[1], cur[2]);
            for (String tag : tags) {
                Candidate t = fetchManifest(JSDELIVR_TAG + tag + "/update/latest.json",
                        TAG_CONNECT_S, TAG_READ_S, "tag:" + tag);
                if (t != null && t.valid && t.versionCode > bestCode(best, versionCode)) {
                    best = t;
                }
            }
        }

        /* ── ④ GitHub API 兜底（令牌直连——v7.6 时代通道，配额 5000/h 足够）── */
        if (bestCode(best, versionCode) <= versionCode) {
            try {
                String api = Net.latestRelease();
                JSONObject a = new JSONObject(api);
                if (a.optBoolean("ok") && a.optBoolean("hasRelease")) {
                    Candidate c = new Candidate();
                    c.valid = true;
                    c.tag = a.optString("tag", "");
                    c.versionCode = codeFromTag(c.tag);
                    c.notes = a.optString("notes", "");
                    c.publishedAt = a.optString("publishedAt", "");
                    c.apkUrl = a.optString("apkUrl", "");
                    c.apkFileName = a.optString("apkFileName", "adshield.apk");
                    c.apkSize = a.optLong("apkSize", 0);
                    String dg = a.optString("apkDigest", "");
                    c.apkSha256 = dg.startsWith("sha256:") ? dg.substring("sha256:".length()) : dg;
                    c.source = "github-api";
                    JSONArray hl = a.optJSONArray("headlines");
                    if (hl != null) c.headlines = hl;
                    if (c.versionCode > bestCode(best, versionCode)) best = c;
                } else if (!a.optBoolean("ok")) {
                    lastError = lastError + " · GitHub API " + a.optString("error", "失败");
                }
            } catch (Exception e) {
                lastError = lastError + " · GitHub API 异常";
            }
        }

        String out;
        if (best != null && best.valid) {
            out = pageJson(best);
        } else {
            out = "{\"ok\":false,\"error\":\"多源均不可达（" + escJson(lastError) + "）——请稍后重试，"
                    + "也可到 GitHub Releases 页手动查看\"}";
        }
        cacheJson = out;
        cacheAt = System.currentTimeMillis();
        cacheForCode = versionCode;
        return out;
    }

    /* ───────── 候选结构（合流中间体）───────── */

    private static final class Candidate {
        boolean valid = false;
        int versionCode = 0;
        String versionName = "";
        String tag = "";
        String notes = "";
        String publishedAt = "";
        String apkUrl = "";
        String cdnApkUrl = "";
        String apkFileName = "";
        String apkSha256 = "";
        long apkSize = 0;
        String source = "";
        JSONArray headlines = null;
    }

    private static int bestCode(Candidate c, int floor) {
        return (c != null && c.valid && c.versionCode > 0) ? c.versionCode : floor - 1;
    }

    /* ───────── 源 ①②③：静态清单拉取 + 契约校验 ───────── */

    /** 拉取并校验一份静态清单；非 2xx/校验失败/异常返回 valid=false 的占位（或 null） */
    private static Candidate fetchManifest(String url, int connectS, int readS, String source) {
        try {
            String body = httpGet(url, connectS, readS);
            if (body == null) return invalid(source, "HTTP 失败");
            JSONObject m = new JSONObject(body);
            Candidate c = new Candidate();
            c.versionCode = m.optInt("versionCode", 0);
            c.versionName = m.optString("versionName", "");
            c.tag = m.optString("releaseTag", "");
            c.notes = m.optString("releaseNotes", "");
            c.publishedAt = m.optString("publishedAt", "");
            c.apkUrl = m.optString("apkUrl", "");
            c.cdnApkUrl = m.optString("cdnApkUrl", "");
            c.apkFileName = m.optString("apkFileName", "adshield.apk");
            c.apkSha256 = m.optString("apkSha256", "");
            c.apkSize = m.optLong("apkSize", 0);
            c.source = source;
            /* 契约校验：版本三元齐全 + 下载通道 https + 包名一致（清单字段
             * packageName 空视为兼容旧清单）——投毒清单在此拦下 */
            String pkg = m.optString("packageName", "");
            if (c.versionCode <= 0 || c.versionName.length() == 0 || c.tag.length() == 0) {
                return invalid(source, "清单字段缺失");
            }
            if (c.apkUrl.length() > 0 && !c.apkUrl.startsWith("https://")) {
                return invalid(source, "非 https 下载链接");
            }
            if (pkg.length() > 0 && !pkg.equals("com.zai.adshield")) {
                return invalid(source, "包名不匹配");
            }
            try {
                String[] heads = com.zai.adshield.core.ReleaseNotes.headlines(c.notes);
                JSONArray hl = new JSONArray();
                for (String h : heads) hl.put(h);
                c.headlines = hl;
            } catch (Throwable ignored) {
            }
            c.valid = true;
            return c;
        } catch (Exception e) {
            return invalid(source, "网络异常");
        }
    }

    private static Candidate invalid(String source, String why) {
        Candidate c = new Candidate();
        c.valid = false;
        c.source = source;
        return c;
    }

    /* ───────── 输出（页面同形 JSON + 中国下载镜像链）───────── */

    private static String pageJson(Candidate c) {
        try {
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("hasRelease", true);
            out.put("tag", c.tag);
            out.put("name", "Skip-Ad v" + c.versionName);
            out.put("notes", c.notes);
            out.put("publishedAt", c.publishedAt);
            out.put("url", "https://github.com/" + Net.REPO + "/releases/tag/" + c.tag);
            out.put("source", c.source);
            out.put("versionName", c.versionName);
            out.put("versionCode", c.versionCode);
            if (c.headlines != null) out.put("headlines", c.headlines);
            out.put("apkUrl", c.apkUrl);
            out.put("apkFileName", c.apkFileName);
            out.put("apkMB", Math.round(c.apkSize / 1048576.0 * 100) / 100.0);
            out.put("apkSize", c.apkSize);
            /* digest：清单的裸 hex → Downloader 认的 "sha256:hex" 官方格式 */
            out.put("apkDigest", c.apkSha256.length() > 0
                    ? ("sha256:" + c.apkSha256) : "");
            /* 中国大陆下载兜底镜像链：gcore CDN（仓库 dist 直出，全日可达）→
             * ghfast.top 代理 → gh-proxy.com 代理（Release 资产加速） */
            JSONArray mirrors = new JSONArray();
            if (c.cdnApkUrl.startsWith("https://")) mirrors.put(c.cdnApkUrl);
            if (c.apkUrl.startsWith("https://")) {
                mirrors.put("https://ghfast.top/" + c.apkUrl);
                mirrors.put("https://gh-proxy.com/" + c.apkUrl);
            }
            out.put("apkMirrors", mirrors);
            return out.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"清单解析失败\"}";
        }
    }

    /* ───────── 版本工具 ───────── */

    /** "7.15.0" → [7,15,0]；不合规返回 null */
    static int[] parse3(String v) {
        try {
            String s = String.valueOf(v).trim().replaceFirst("^v", "");
            String[] p = s.split("\\.");
            if (p.length < 2) return null;
            int a = Integer.parseInt(p[0]);
            int b = Integer.parseInt(p[1]);
            int c = p.length >= 3 ? Integer.parseInt(p[2]) : 0;
            return new int[]{a, b, c};
        } catch (Exception e) {
            return null;
        }
    }

    /** tag "v7.15.0" → versionCode（7*10000+15*1000+0*100=85000；解析失败 0） */
    static int codeFromTag(String tag) {
        int[] p = parse3(tag);
        if (p == null || p[0] <= 0) return 0;
        return p[0] * 10000 + p[1] * 1000 + p[2] * 100;
    }

    /** 候选 tag 集（下一补丁 + 下两个 minor 的四个变体——覆盖正常迭代步长） */
    static String[] candidateTags(int major, int minor, int patch) {
        return new String[]{
                "v" + major + "." + minor + "." + (patch + 1),
                "v" + major + "." + (minor + 1) + ".0",
                "v" + major + "." + (minor + 1) + ".1",
                "v" + major + "." + (minor + 2) + ".0",
        };
    }

    /* ───────── 底层 HTTP（独立超时参数；UA 同 Net 通道约定）───────── */

    private static final int MAX_BODY_BYTES = 256 * 1024;

    private static String httpGet(String url, int connectS, int readS) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectS * 1000);
            c.setReadTimeout(readS * 1000);
            c.setRequestMethod("GET");
            boolean githubApi = url.contains("api.github.com");
            c.setRequestProperty("Accept", githubApi
                    ? "application/vnd.github+json" : "application/json,*/*");
            c.setRequestProperty("User-Agent", githubApi
                    ? "adshield-android" : Net.BROWSER_UA);
            c.setInstanceFollowRedirects(true); // 清单通道允许合法重定向（无攻击面：只读 + 契约校验）
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > MAX_BODY_BYTES) {
                    try { in.close(); } catch (Exception ignored) { }
                    return null; // 清单 ≤ 16KB 量级；超大体视为异常响应拒收
                }
            }
            try { in.close(); } catch (Exception ignored) { }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String escJson(String s) {
        return String.valueOf(s == null ? "" : s)
                .replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
