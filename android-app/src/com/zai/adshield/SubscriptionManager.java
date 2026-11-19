/**
 * 订阅管理器（v4.0.0 GKD 订阅融合 · v4.2.0 优先级与限流重构）
 * ─────────────────────────────
 * 双订阅驱动：①内置订阅（APK 常驻，21 款应用 vid 规则）②远程订阅
 * （用户粘贴任意 GKD 订阅链接导入——整个 GKD 社区规则生态可用）。
 *
 * v4.2.0 优先级决策（issue#31-37：远程优先被陈旧订阅抢跑——「已停止
 * 维护」的默认订阅在微信主界面连点乱划；而用户自导入的订阅又因只在
 * 开屏路径接驳而「导入后零命中」）：
 *  · 内置优先、订阅接力：内置规则经本引擎全量真机验证先出手；远程
 *    订阅接力，并接入弹窗路径（更新弹窗/青少年模式等社区规则真正生效）；
 *  · 远程订阅按 GKD 语义限流：actionMaximum 默认 1 次/窗 +
 *    actionInterval 默认 1000ms；内置订阅不限次（与主引擎文案规则同权，
 *    由全局 0.9s 点击冷却 + 同窗 6 次预算统一管制）；
 *  · activityIds 强校验 + 无限定规则的 skip 形态门（见 Subscription）。
 *
 * 设计：
 *  · 编译一次、volatile 快照切换——匹配热路径零锁零解析；
 *  · 远程订阅 24h 自动静默更新（服务心跳时顺带），失败保留旧版本；
 *  · 拉取/解析全在后台线程，任何异常只记 lastError 不影响引擎；
 *  · back 类规则在加载时已丢弃（链式拦截红线），click/clickNode
 *    命中后仍过主引擎安全闸（可见 + 面积 ≤35%）。
 */package com.zai.adshield;

import android.content.Context;
import android.content.SharedPreferences;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.Subscription;

/**
 * 订阅管理器（v4.0.0 GKD 订阅融合 · v4.2.0 优先级与限流重构）
 * ─────────────────────────────
 * 双订阅驱动：①内置订阅（APK 常驻，21 款应用 vid 规则）②远程订阅
 * （用户粘贴任意 GKD 订阅链接导入——整个 GKD 社区规则生态可用）。
 *
 * v4.2.0 优先级决策（issue#31-37：远程优先被陈旧订阅抢跑——「已停止
 * 维护」的默认订阅在微信主界面连点乱划；而用户自导入的订阅又因只在
 * 开屏路径接驳而「导入后零命中」）：
 *  · 内置优先、订阅接力：内置规则经本引擎全量真机验证先出手；远程
 *    订阅接力，并接入弹窗路径（更新弹窗/青少年模式等社区规则真正生效）；
 *  · 远程订阅按 GKD 语义限流：actionMaximum 默认 1 次/窗 +
 *    actionInterval 默认 1000ms；内置订阅不限次（与主引擎文案规则同权，
 *    由全局 0.9s 点击冷却 + 同窗 6 次预算统一管制）；
 *  · activityIds 强校验 + 无限定规则的 skip 形态门（见 Subscription）。
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
    static volatile int builtinHits = 0;

    /* v4.2.0 运行时限流记账（GKD actionMaximum/actionInterval）。
     * key = 前缀|包名|规则名|Activity；窗口切换 = key 变化 = 次数自然重置 */
    private static final java.util.HashMap<String, Long> ruleLastFireAt = new java.util.HashMap<>();
    private static final java.util.HashMap<String, Integer> ruleFireCount = new java.util.HashMap<>();

    static final Subscription.Runtime RUNTIME = new Subscription.Runtime() {
        @Override
        public boolean allowFire(String key, int actionMaximum, long actionInterval) {
            long now = System.currentTimeMillis();
            if (actionInterval > 0) {
                Long last = ruleLastFireAt.get(key);
                if (last != null && now - last < actionInterval) return false;
            }
            if (actionMaximum > 0) {
                Integer n = ruleFireCount.get(key);
                if (n != null && n >= actionMaximum) return false;
            }
            return true;
        }

        @Override
        public void onFired(String key) {
            ruleLastFireAt.put(key, System.currentTimeMillis());
            Integer n = ruleFireCount.get(key);
            ruleFireCount.put(key, n == null ? 1 : n + 1);
            // 记账表防膨胀：超过 512 条整体清空（窗口切换自然重建，丢的只是计数）
            if (ruleFireCount.size() > 512) {
                ruleFireCount.clear();
                ruleLastFireAt.clear();
            }
        }
    };

    static boolean loaded = false;

    /* ═══ v5.6.0 订阅市场：活跃维护的第三方订阅注册表 ═══
     * 用户实锤：「订阅模块导入的都是已经停止维护的默认订阅」——
     * 旧默认源 @gkd-kit/subscription（官方，已归档停更）与 AIsouler
     * （2026-02 停止维护）全部下架，换上 gkd-subscription 话题下
     * 按近期 push + 星数 + 非归档师选的活跃源（2026-11-18 快照）：
     *  · Lin-arm/GKD_subscription（⭐7382 · 当日仍在更新）——社区事实标准
     *  · ganlinte/GKD-subscription（⭐935 · 610 APP/1389 规则组）
     *  · MengNianxiaoyao/gkd-subscription（⭐496）
     *  · LenorEric/GK-Lemon-D（轻量精选）· cjy0812/Ling033_GKD_Rules
     *  · xiaobaiba999/skip-ad-subscription——本仓库官方自维护订阅
     *   （subscription-template 构建，与内置规则库同源同步）
     * 所有 URL 全部 jsdelivr CDN（国内可达）+ 仓库可溯源；导入前可预览
     * 仓库主页。建议用户从市场一键导入，不再粘贴死链。 */
    /* v5.8.0 防呆版（用户实锤：订阅市场只保留 3-5 个活跃且 star 多的订阅）：
     * 6→3——只留 gkd-subscription 话题星数前三且当日活跃的源：
     *  · Lin-arm/GKD_subscription（⭐7382 · 日更）——社区事实标准
     *  · ganlinte/GKD-subscription（⭐935 · 610 款 APP/1389 规则组）
     *  · MengNianxiaoyao/gkd-subscription（⭐496）
     * （本仓库官方订阅保留在自定义链接路径，市场只做高星精选） */
    static final String[][] RECOMMENDED_SUBS = {
            {"id667 的 GKD 订阅", "Lin-arm",
                    "https://cdn.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5",
                    "⭐7.4k · 日更——社区事实标准", "日更"},
            {"ganlin 的 GKD 订阅", "ganlinte",
                    "https://cdn.jsdelivr.net/gh/ganlinte/GKD-subscription@main/dist/ganlin_gkd.json5",
                    "⭐935 · 610 款应用", "周更"},
            {"小年侠订阅", "MengNianxiaoyao",
                    "https://cdn.jsdelivr.net/gh/MengNianxiaoyao/gkd-subscription@main/dist/gkd.json5",
                    "⭐496 · 持续维护", "活跃"},
    };
    /** 订阅市场 JSON（页顶卡片渲染用；无 Android 依赖纯拼装） */
    public static String recommendJson() {
        StringBuilder b = new StringBuilder(1024);
        b.append("[");
        for (int i = 0; i < RECOMMENDED_SUBS.length; i++) {
            String[] s = RECOMMENDED_SUBS[i];
            if (i > 0) b.append(',');
            b.append("{\"idx\":").append(i)
                    .append(",\"name\":").append(jsonStr(s[0]))
                    .append(",\"owner\":").append(jsonStr(s[1]))
                    .append(",\"url\":").append(jsonStr(s[2]))
                    .append(",\"desc\":").append(jsonStr(s[3]))
                    .append(",\"badge\":").append(jsonStr(s[4]))
                    .append(",\"repo\":").append(jsonStr("https://github.com/" + repoOf(s[2])))
                    .append('}');
        }
        b.append("]");
        return b.toString();
    }

    /** 从 jsdelivr gh URL 提取 owner/repo（市场卡片「查看仓库」用） */
    static String repoOf(String jsdelivrUrl) {
        try {
            String u = jsdelivrUrl;
            int at = u.indexOf("@main/");
            if (at > 0) {
                String head = u.substring(0, at); // .../gh/owner/repo
                int gh = head.indexOf("/gh/");
                if (gh >= 0) return head.substring(gh + 4);
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /* ───────── 加载（服务连接时 + Bridge 调用时） ───────── */

    public static synchronized void ensureLoaded(Context ctx) {
        if (loaded) return;
        try {
            // 内置订阅（编译失败不可能——随 APK 自测发布）；0/0 = 不限次，
            // 出手节奏由全局点击冷却 + 同窗点击预算管制（与主引擎同权）
            builtinSub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
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
                remoteSub = Subscription.parse(cached, 1, 1000); // GKD 语义默认：1 次/窗 + 1000ms
            }
        } catch (Exception e) {
            lastError = "load: " + e.getMessage();
        }
        loaded = true;
    }

    /* ───────── 匹配（服务扫描热路径，只读 volatile 快照） ─────────
     * v4.2.0 优先级：内置订阅优先（经全量真机验证），远程订阅接力；
     * 弹窗路径同样接驳（社区更新弹窗/青少年模式规则真正生效）。
     * 单遍树扫描 + activityIds 预过滤（微信主界面等场景零树遍历）。 */

    public static RuleEngine.Hit match(String pkg, NodeView root, Ctx ctx) {
        Subscription builtin = builtinSub;
        if (builtin != null) {
            RuleEngine.Hit h = builtin.match(pkg, root, ctx, 400, RUNTIME, "b|");
            if (h != null) {
                builtinHits++;
                return h;
            }
        }
        Subscription remote = remoteEnabled ? remoteSub : null;
        if (remote != null) {
            RuleEngine.Hit h = remote.match(pkg, root, ctx, 400, RUNTIME, "r|");
            if (h != null) {
                remoteHits++;
                return h;
            }
        }
        return null;
    }

    /** v5.3.0：订阅（内置或启用的远程）是否覆盖该应用的专属规则——
     *  宿主窗档案的省电梯子选择用（覆盖 → 低频档 {0,700}，否则纯探针哨兵）。
     *  只读 volatile 快照，扫描热路径零锁。 */
    public static boolean coversPkg(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        Subscription builtin = builtinSub;
        if (builtin != null && builtin.covers(pkg)) return true;
        Subscription remote = remoteEnabled ? remoteSub : null;
        return remote != null && remote.covers(pkg);
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
        /* v4.5.0 安全：订阅源必须 https（明文 http 可被中间人投毒规则——
         * 恶意规则能让引擎点击任意控件）+ 长度上限 */
        if (!u.startsWith("https://")) {
            return "{\"ok\":false,\"error\":\"订阅链接必须为 https://（明文 http 可被投毒，已拒绝）\"}";
        }
        if (u.length() > 512) {
            return "{\"ok\":false,\"error\":\"链接过长\"}";
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
        // v4.1.0 镜像回退链（issue#30）+ v6.1.0 Connection reset 根治（用户实锤：
        // 订阅市场导入提示拉取失败 Connection reset——jsdelivr 单源被运营商
        // 重置时无变体可退）：每个变体先同址重试（httpGetRaw 内 600ms）再换址，
        // 全链 5 变体任一成功即导入成功
        java.util.List<String> variants = mirrorVariants(url);
        Exception lastEx = null;
        for (int i = 0; i < variants.size(); i++) {
            try {
                return fetchOne(ctx, variants.get(i), url, sp);
            } catch (Exception e) {
                lastEx = e;
            }
        }
        String raw = lastEx == null ? "网络失败" : (lastEx.getMessage() == null ? "网络失败" : lastEx.getMessage());
        lastError = raw;
        String msg = humanizeNetError(raw);
        String hint = variants.size() > 1
                ? "（已自动重试并切换 " + variants.size() + " 个镜像通道仍失败——多为当前网络对海外源全阻断，可稍后重试或换 Wi-Fi/流量后重试一次）"
                : " · 订阅页「订阅市场」可一键导入活跃维护的订阅";
        return "{\"ok\":false,\"error\":" + jsonStr("拉取失败：" + msg + hint) + "}";
    }

    /** v6.1.0：网络错误人话化（Connection reset 等术语翻译为用户可理解的
     *  原因说明——「拉取失败：Connection reset」对普通用户零信息量） */
    static String humanizeNetError(String raw) {
        if (raw == null) return "网络异常";
        String low = raw.toLowerCase();
        if (low.contains("reset") || raw.contains("重置")) {
            return "网络连接被运营商/CDN 重置（jsdelivr 类海外源在国内间歇性被干扰，稍后重试或换网络通常可恢复）";
        }
        if (low.contains("timeout") || low.contains("timed out") || raw.contains("超时")) {
            return "连接超时（当前网络访问海外源不稳定，已自动换镜像重试）";
        }
        if (low.contains("unknowhost") || low.contains("unknownhost") || raw.contains("未知主机")) {
            return "域名解析失败（当前网络 DNS 异常，可稍后重试）";
        }
        if (low.contains("404")) {
            return "订阅文件不存在（404——订阅源可能已迁移，可换订阅市场其他源）";
        }
        if (low.contains("403")) {
            return "订阅源拒绝访问（403）";
        }
        return raw;
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
            // ④ v6.1.0 jsdelivr gh URL → 多通道全链（Connection reset 根治：
            //    市场 3 源全在 cdn.jsdelivr.net，被运营商重置时零变体可退——
            //    fastly 接入点（同 CDN 另一接入点，重置概率独立）→ ghfast.top
            //    代理 GitHub raw → gh-proxy.com 代理 → raw 直连；配合
            //    httpGetRaw 同址重试 = 最坏 10 次出手任一成功即导入成功）
            if (url.startsWith("https://cdn.jsdelivr.net/gh/") || url.startsWith("https://fastly.jsdelivr.net/gh/")) {
                String rest = url.substring(url.indexOf("/gh/") + 4);
                int at = rest.indexOf('@');
                int slash = at < 0 ? -1 : rest.indexOf('/', at + 1);
                if (at > 0 && slash > at) {
                    String ownerRepo = rest.substring(0, at);      // owner/repo
                    String branch = rest.substring(at + 1, slash); // 分支
                    String path = rest.substring(slash + 1);       // 文件路径
                    list.add("https://fastly.jsdelivr.net/gh/" + ownerRepo + "@" + branch + "/" + path);
                    list.add("https://ghfast.top/https://github.com/" + ownerRepo + "/raw/" + branch + "/" + path);
                    list.add("https://gh-proxy.com/https://github.com/" + ownerRepo + "/raw/" + branch + "/" + path);
                    list.add("https://raw.githubusercontent.com/" + ownerRepo + "/" + branch + "/" + path);
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
        Subscription parsed = Subscription.parse(body, 1, 1000); // GKD 语义默认：1 次/窗 + 1000ms
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
        b.append(",\"builtinHits\":").append(builtinHits);
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
