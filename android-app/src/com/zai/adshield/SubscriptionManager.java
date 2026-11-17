package com.zai.adshield;

import android.content.Context;
import android.content.SharedPreferences;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.Subscription;

/**
 * 订阅管理器（v4.0.0 GKD 订阅融合）
 * ─────────────────────────────────────
 * 双订阅驱动：①内置订阅（APK 常驻，21 款应用 vid 规则）②远程订阅
 * （用户粘贴任意 GKD 订阅链接导入——整个 GKD 社区规则生态可用）。
 * 主引擎五层文案规则仍是第一优先级，订阅规则在其未命中时接力。
 *
 * 设计：
 *  · 编译一次、volatile 快照切换——匹配热路径零锁零解析；
 *  · 远程订阅 24h 自动静默更新（服务心跳时顺带），失败保留旧版本；
 *  · 拉取/解析全在后台线程，任何异常只记 lastError 不影响引擎；
 *  · back 类规则在加载时已丢弃（链式拦截红线），click/clickNode
 *    命中后仍过主引擎安全闸（可见 + 面积 ≤35%）。
 */
public final class SubscriptionManager {

    private SubscriptionManager() {
    }

    static final long UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24h 静默更新

    /** 已编译快照（不可变，热路径只读） */
    static volatile Subscription builtinSub;
    static volatile Subscription remoteSub;
    static volatile String subUrl = "";
    static volatile boolean remoteEnabled = true;
    static volatile long lastUpdateAt = 0;
    static volatile String lastError = "";
    static volatile int remoteHits = 0;

    static boolean loaded = false;

    /* ───────── 加载（服务连接时 + Bridge 调用时） ───────── */

    public static synchronized void ensureLoaded(Context ctx) {
        if (loaded) return;
        try {
            // 内置订阅（编译失败不可能——随 APK 自测发布）
            builtinSub = Subscription.parse(Subscription.BUILTIN_JSON5);
        } catch (Exception e) {
            builtinSub = null;
        }
        try {
            SharedPreferences sp = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE);
            subUrl = sp.getString("subUrl", "");
            remoteEnabled = sp.getBoolean("subEnabled", true);
            lastUpdateAt = sp.getLong("subUpdatedAt", 0);
            String cached = sp.getString("subCache", null);
            if (cached != null && cached.length() > 0) {
                remoteSub = Subscription.parse(cached);
            }
        } catch (Exception e) {
            lastError = "load: " + e.getMessage();
        }
        loaded = true;
    }

    /* ───────── 匹配（服务扫描热路径，只读 volatile 快照） ─────────
     * 返回 null = 无命中；优先远程订阅（用户主动导入，更精准），
     * 内置订阅兜底。预算 400 节点（订阅规则晚于主引擎出手，
     * 树已被主引擎扫过一遍，浅层快查）。 */

    public static RuleEngine.Hit match(String pkg, NodeView root, Ctx ctx) {
        Subscription remote = remoteEnabled ? remoteSub : null;
        if (remote != null) {
            RuleEngine.Hit h = remote.match(pkg, root, ctx, 400);
            if (h != null) {
                remoteHits++;
                return h;
            }
        }
        Subscription builtin = builtinSub;
        if (builtin != null) {
            return builtin.match(pkg, root, ctx, 400);
        }
        return null;
    }

    public static boolean hasRules() {
        return remoteSub != null || builtinSub != null;
    }

    /* ───────── 远程订阅管理（Bridge 后台线程调用） ───────── */

    /** 设置/清空订阅 URL 并立即拉取。返回 JSON 结果 {ok, ...} */
    public static String setUrl(Context ctx, String url) {
        SharedPreferences sp = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE);
        String u = url == null ? "" : url.trim();
        if (u.length() == 0) {
            // 清空：移除远程订阅
            remoteSub = null;
            subUrl = "";
            lastError = "";
            sp.edit().remove("subUrl").remove("subCache").apply();
            return "{\"ok\":true,\"cleared\":true}";
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return "{\"ok\":false,\"error\":\"链接须以 http(s):// 开头\"}";
        }
        String r = fetch(ctx, u, sp);
        return r;
    }

    /** 立即更新（订阅页「检查更新」按钮 / 24h 静默更新共用） */
    public static String updateNow(Context ctx) {
        ensureLoaded(ctx);
        if (subUrl == null || subUrl.length() == 0) {
            return "{\"ok\":false,\"error\":\"未添加订阅\"}";
        }
        SharedPreferences sp = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE);
        return fetch(ctx, subUrl, sp);
    }

    /** 24h 静默更新（服务心跳顺带调用；无订阅/不到期秒回） */
    public static void maybeSilentUpdate(Context ctx) {
        try {
            ensureLoaded(ctx);
            if (subUrl == null || subUrl.length() == 0 || !remoteEnabled) return;
            long now = System.currentTimeMillis();
            if (now - lastUpdateAt < UPDATE_INTERVAL_MS) return;
            SharedPreferences sp = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE);
            fetch(ctx, subUrl, sp);
        } catch (Throwable ignored) {
        }
    }

    static String fetch(Context ctx, String url, SharedPreferences sp) {
        // v4.1.0 镜像回退链（issue#30：官方订阅链接 404——npmmirror 上
        // @gkd-kit/subscription 的文件路径已从 /files/gkd.json5 迁到
        // /files/dist/gkd.json5，旧链接整体失效）：原始链接失败时自动
        // 依次尝试镜像变体，成功后仍按用户填写的原始 URL 记忆
        java.util.List<String> variants = mirrorVariants(url);
        Exception lastEx = null;
        for (int i = 0; i < variants.size(); i++) {
            try {
                return fetchOne(ctx, variants.get(i), url, sp);
            } catch (Exception e) {
                lastEx = e;
            }
        }
        String msg = lastEx == null ? "网络失败" : (lastEx.getMessage() == null ? "网络失败" : lastEx.getMessage());
        lastError = msg;
        String hint = variants.size() > 1
                ? "（已自动尝试 " + variants.size() + " 个镜像地址）· 点「官方订阅」可一键填入可用链接"
                : " · 点「官方订阅」可一键填入可用链接";
        return "{\"ok\":false,\"error\":" + jsonStr("拉取失败：" + msg + hint) + "}";
    }

    /** 镜像变体列表：原始 URL 打头，后接按域名规则生成的回退地址 */
    static java.util.List<String> mirrorVariants(String url) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        if (url == null || url.length() == 0) return list;
        list.add(url);
        try {
            // ① npmmirror 官方订阅包路径迁移：files/gkd.json5 → files/dist/gkd.json5
            if (url.contains("registry.npmmirror.com/@gkd-kit/subscription")
                    && url.endsWith("/files/gkd.json5")) {
                list.add(url.substring(0, url.length() - "/gkd.json5".length()) + "/dist/gkd.json5");
            }
            // ② GitHub raw / jsdelivr 直连不可达（国内常见）→ 官方订阅切换 npmmirror 镜像
            boolean gkdOfficial = url.contains("gkd-kit/subscription") || url.contains("gkd-kit/subscription@");
            if (gkdOfficial && !url.contains("npmmirror.com")) {
                list.add("https://registry.npmmirror.com/@gkd-kit/subscription/latest/files/dist/gkd.json5");
            }
            // ③ raw.githubusercontent.com 任意仓库 → jsdelivr CDN 兜底
            if (url.startsWith("https://raw.githubusercontent.com/")) {
                String rest = url.substring("https://raw.githubusercontent.com/".length());
                int i1 = rest.indexOf('/');
                int i2 = i1 < 0 ? -1 : rest.indexOf('/', i1 + 1);
                int i3 = i2 < 0 ? -1 : rest.indexOf('/', i2 + 1);
                if (i1 > 0 && i2 > i1 && i3 > i2) {
                    String owner = rest.substring(0, i1);
                    String repo = rest.substring(i1 + 1, i2);
                    String branch = rest.substring(i2 + 1, i3);
                    String path = rest.substring(i3 + 1);
                    list.add("https://cdn.jsdelivr.net/gh/" + owner + "/" + repo + "@" + branch + "/" + path);
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** 单地址拉取；fetchUrl = 实际请求地址，rememberUrl = 记忆/展示用的用户原始地址 */
    static String fetchOne(Context ctx, String fetchUrl, String rememberUrl, SharedPreferences sp) throws Exception {
        String body = Net.httpGetRaw(fetchUrl);
        if (body == null || body.length() < 10) {
            throw new IllegalStateException("订阅内容为空");
        }
        Subscription parsed = Subscription.parse(body);
        int apps = parsed.apps.size();
        int rules = parsed.ruleCount();
        if (apps == 0 || rules == 0) {
            throw new IllegalStateException("订阅里没有可用的应用规则");
        }
        remoteSub = parsed;           // 原子切换快照
        subUrl = rememberUrl;
        lastError = "";
        lastUpdateAt = System.currentTimeMillis();
        sp.edit().putString("subUrl", rememberUrl)
                .putString("subCache", body)
                .putLong("subUpdatedAt", lastUpdateAt)
                .apply();
        return "{\"ok\":true,\"name\":" + jsonStr(parsed.name)
                + ",\"apps\":" + apps + ",\"rules\":" + rules + "}";
    }

    public static void setEnabled(Context ctx, boolean on) {
        ensureLoaded(ctx);
        remoteEnabled = on;
        ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE)
                .edit().putBoolean("subEnabled", on).apply();
    }

    public static boolean isEnabled() {
        return remoteEnabled;
    }

    /* ───────── 状态（订阅页展示） ───────── */

    public static String stateJson(Context ctx) {
        ensureLoaded(ctx);
        StringBuilder b = new StringBuilder(256);
        b.append("{");
        Subscription bi = builtinSub;
        b.append("\"builtinApps\":").append(bi == null ? 0 : bi.apps.size());
        b.append(",\"builtinRules\":").append(bi == null ? 0 : bi.ruleCount());
        Subscription rm = remoteSub;
        b.append(",\"remoteOk\":").append(rm != null);
        if (rm != null) {
            b.append(",\"remoteName\":").append(jsonStr(rm.name));
            b.append(",\"remoteVersion\":").append(rm.version);
            b.append(",\"remoteApps\":").append(rm.apps.size());
            b.append(",\"remoteRules\":").append(rm.ruleCount());
        }
        b.append(",\"remoteEnabled\":").append(remoteEnabled);
        b.append(",\"url\":").append(jsonStr(subUrl == null ? "" : subUrl));
        b.append(",\"updatedAt\":").append(lastUpdateAt);
        b.append(",\"remoteHits\":").append(remoteHits);
        b.append(",\"error\":").append(jsonStr(lastError == null ? "" : lastError));
        b.append("}");
        return b.toString();
    }

    static String jsonStr(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder(s.length() + 8);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else if (c < 0x20) b.append(' ');
            else b.append(c);
        }
        b.append('"');
        return b.toString();
    }
}
