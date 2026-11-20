package com.zai.adshield;

import android.content.Context;
import android.content.SharedPreferences;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.Subscription;
/**
 * 订阅管理器（v4.0.0 GKD 订阅融合 · v4.2.0 优先级与限流重构 · v7.0.0 离线快照）。
 * ─────────────────────────────────────────
 * 双订阅驱动：①内置订阅（APK 常驻，21 款应用 vid 规则）②远程订阅
 * （用户粘贴任意 GKD 订阅链接导入——整个 GKD 社区规则生态可用）。
 *
 * v7.0.0（用户实锤：订阅市场点击导入时提示「拉取失败，Connection reset」）：
 *  · 八通道镜像链（SubMirrors 纯函数：原址→fastly→gcore→testingcf→ghfast
 *    代理→gh-proxy 代理→raw 直连，每通道同址瞬时故障重试 600ms）；
 *  · 离线快照兜底：三个市场精选源的最新内容随 APK 内置
 *    （assets/subsnap/*.json5）——全通道失败时直接导入快照，导入路径
 *    从此【永不硬失败】；快照导入后 1h 心跳自动重拉最新版；
 *  · 进度回调（ProgressSink）：导入按钮→页内「正在切换镜像 X/Y · host」
 *    实时反馈（Bridge.evaluateJavascript __subProgress）。
 *
 * v4.2.0 优先级决策（issue#31-37）：
 *  · 内置优先、订阅接力；远程订阅按 GKD 语义限流（actionMaximum 默认
 *    1 次/窗 + actionInterval 默认 1000ms）；
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
    /** v7.0.0：离线快照导入后的提前重拉间隔（1h——联网恢复即自动换真源） */
    static final long SNAPSHOT_RETRY_MS = 60 * 60 * 1000L;

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

    /* ═══ v7.9.1 订阅匹配七分类统计（用户实锤：v7.9.0 内置 124 款 124 条命中 0、
     * 远程 932 款 2880 条命中 0——「不得把所有未命中都统计为订阅未命中」）═══
     * 口径：
     *  · notLoaded   订阅未加载（两源全空——BUILTIN_JSON5 解析失败 + 无远程缓存）；
     *  · noCover     应用不匹配（两源都无该包专属规则——匹配器已咨询）；
     *  · noMatch     页面选择器不匹配（有覆盖但本轮无命中：activityIds 限定/
     *                选择器链/形态闸/限流记账任一拦截——上传日志可区分「未覆盖」与「覆盖但不中」）；
     *  · ruleHit     规则匹配成功（候选进入点击轮换）；
     *  · blocked     被安全机制拦截（终闸 boundaryCheck 拒点——宁漏勿误闸在岗证据）；
     *  · clickFail   点击执行失败（双通道派发全失败）；
     *  · clickOk     点击执行成功（派发完成——已出手）。 */
    static final java.util.concurrent.atomic.AtomicInteger subNotLoaded =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subNoCover =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subNoMatch =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subRuleHit =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subBlocked =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subClickFail =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subClickOk =
            new java.util.concurrent.atomic.AtomicInteger();

    /* ══ v7.9.5 GKD 订阅证据链扩展（用户指令七：不追求规则加载条数——统计
     *  consulted / matched / candidateAccepted / clicked / verifiedSuccess /
     *  clickFail / falseClick / blocked 八格证据链；内置探针命中不得导致订阅
     * 不再参与——consulted 独立计数即「每窗首轮无条件咨询」的反饥饿证据）══
     * 口径：
     *  · consulted       matchSources 被咨询次数（含未加载/无覆盖——被咨询
     *                   本身就是参与证据）；
     *  · candidateAccepted 候选被 SafetyGuard 终闸接受（过闸进入派发尝试）；
     *  · verifiedSuccess 点击后任务 VERIFIED_SUCCESS 且本任务出手 reason 为
     *                   订阅规则（点击→广告真关闭的证据链闭环）；
     *  · falseClick      误触（真机样本回传唯一来源——自动化环境恒 0，
     *                   绝不虚报）。 */
    static final java.util.concurrent.atomic.AtomicInteger subConsulted =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subCandAccepted =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subVerifiedSuccess =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger subFalseClick =
            new java.util.concurrent.atomic.AtomicInteger();

    /** v7.9.1：订阅出手结果计数（AdSkipService.execute 回报——reason 为
     *  rule_sub_gkd* 时才计；1=点击执行成功 / 0=双通道全失败 / -1=被安全闸拦截）。 */
    public static void countClickOutcome(String reason, int outcome) {
        if (reason == null || !reason.startsWith("rule_sub_gkd")) return;
        if (outcome > 0) subClickOk.incrementAndGet();
        else if (outcome == 0) subClickFail.incrementAndGet();
        else subBlocked.incrementAndGet();
    }

    /** v7.9.5：候选被安全闸接受（execute 出手前置门全过——进入派发尝试的
     *  订阅候选。matched→candidateAccepted→clicked→verifiedSuccess 链上
     *  的第二格；非订阅 reason 不计）。 */
    public static void countCandAccepted(String reason) {
        if (reason == null || !reason.startsWith("rule_sub_gkd")) return;
        subCandAccepted.incrementAndGet();
    }

    /** v7.9.5：订阅出手的任务 VERIFIED_SUCCESS（AdSkipService.markTaskSuccess
     *  回报——本任务出手 reason 为订阅规则的点击确实关闭了广告的证据链闭环格）。 */
    public static void countVerifiedSuccess(String reason) {
        if (reason == null || !reason.startsWith("rule_sub_gkd")) return;
        subVerifiedSuccess.incrementAndGet();
    }

    /* ═══ v7.9.3 JVM 测试钩子（纯静态 · 零 Android 依赖 · 生产永不调用）══
     * 七分类统计的 L1 断言面（PageScenarioTest S8 段）：
     *  primeForJvm()      —— 只加载内置订阅（与 ensureLoaded 第一段完全相同：
     *                        BUILTIN_JSON5 → builtinSub；remote 保持 null——
     *                        SharedPreferences/Context 一概不碰）；
     *  subStatSnapshot()  —— 七计数器快照（stateJson 的 subStat 段同源数据）。
     * 服务侧行为零影响：ensureLoaded 的幂等闸由 loaded 控制，本钩子不动它；
     * remoteSub/remoteEnabled 只显式置「无远程」基线（matchSources 的
     * notLoaded/noCover 分类面由此驱动）。 */
    public static void primeForJvm() {
        try {
            builtinSub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
        } catch (Throwable e) {
            builtinSub = null;
        }
        remoteSub = null;
        remoteEnabled = false;
    }

    /** 七分类快照：[notLoaded, noCover, noMatch, hit, blocked, clickFail, clickOk]。
     *  v7.9.5 保持七格不变（既有 S8 断言的位置索引零破坏），扩展面走 subStatV2Snapshot。 */
    public static int[] subStatSnapshot() {
        return new int[]{
                subNotLoaded.get(), subNoCover.get(), subNoMatch.get(),
                subRuleHit.get(), subBlocked.get(), subClickFail.get(), subClickOk.get(),
        };
    }

    /** v7.9.5 扩展快照：[consulted, matched, candidateAccepted, clicked,
     *  verifiedSuccess, clickFail, falseClick, blocked]——GKD 证据链八格。 */
    public static int[] subStatV2Snapshot() {
        return new int[]{
                subConsulted.get(), subRuleHit.get(), subCandAccepted.get(),
                subClickOk.get(), subVerifiedSuccess.get(), subClickFail.get(),
                subFalseClick.get(), subBlocked.get(),
        };
    }

    /* ═══ v5.6.0 订阅市场：活跃维护的第三方订阅注册表 ═══
     * v5.8.0 防呆版：只留 gkd-subscription 话题星数前三且当日活跃的源；
     * v7.0.0 三个源的内容随 APK 离线快照内置（SubMirrors.SNAPSHOT_SOURCES
     * 与本表同一套 URL 事实——全通道失败时秒导快照）。 */
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

    /** 从 jsdelivr gh URL 提取 owner/repo */
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
            builtinSub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
        } catch (Throwable e) { // v7.3.0：catch Throwable（深度上限外再兜 Error——任何形态都不许崩服务）
            builtinSub = null;
        }
        try {
            SharedPreferences sp = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE);
            subUrl = sp.getString("subUrl", "");
            remoteEnabled = sp.getBoolean("subEnabled", true);
            lastUpdateAt = sp.getLong("subUpdatedAt", 0);
            String cached = sp.getString("subCache", null);
            if (cached != null && cached.length() > 0) {
                remoteSub = Subscription.parse(cached, 1, 1000);
            }
        } catch (Throwable e) { // v7.3.0：同上（旧 catch(Exception) 接不住 Error——坏缓存丢弃，绝不影响服务启动）
            remoteSub = null;
            lastError = "load: " + safeMsg(e);
        }
        loaded = true;
    }

    /* ───────── 匹配（服务扫描热路径，只读 volatile 快照） ───────── */

    public static RuleEngine.Hit match(String pkg, NodeView root, Ctx ctx) {
        /* v7.1.0 起服务扫描主路径改用 matchSources（两源各贡献一候选进点击
         * 轮换，不再单源短路）——本方法保留给旧调用方兼容。 */
        java.util.ArrayList<RuleEngine.Hit> list = matchSources(pkg, root, ctx);
        return list.isEmpty() ? null : list.get(0);
    }

    /** v7.1.0 两源候选收集（RuleRouter 裁决使用顺序）——用户实锤「内置规则库
     *  和订阅市场规则的使用顺序和命中问题」根治：
     *  · 市场源（用户主动导入·日更）在该应用有专属规则时【优先】；
     *  · 内置源（APK 静态快照·21 款 vid 规则）兑底接力；
     *  · 两源各贡献一个候选进点击轮换（文本候选用尽 → 市场候选 → 内置候选
     *    → …）——单源候选失效不再拖死整轮；
     *  · 源命中计数分别记账（订阅页「命中」+ 使用报告可见）；hit.src 带
     *    「市场·/内置·」源标注（日志与 reasonZh 可诊断）。 */
    public static java.util.ArrayList<RuleEngine.Hit> matchSources(String pkg, NodeView root, Ctx ctx) {
        java.util.ArrayList<RuleEngine.Hit> out = new java.util.ArrayList<RuleEngine.Hit>(2);
        Subscription builtin = builtinSub;
        Subscription remote = remoteEnabled ? remoteSub : null;
        /* v7.9.5 consulted：被咨询即计数（含未加载/无覆盖——订阅参与的
         *  前置证据，与命中与否解耦）。 */
        subConsulted.incrementAndGet();
        /* v7.9.1 七分类统计入口：未加载 / 应用不匹配 / 页面不匹配 / 命中。 */
        if (builtin == null && remote == null) {
            subNotLoaded.incrementAndGet();
            return out;
        }
        boolean remoteCovers = remote != null && remote.covers(pkg);
        boolean builtinCovers = builtin != null && builtin.covers(pkg);
        if (!remoteCovers && !builtinCovers) {
            subNoCover.incrementAndGet();
        }
        String[] order = com.zai.adshield.core.RuleRouter.sourceOrder(
                remoteEnabled, remote != null, remoteCovers,
                builtin != null, builtinCovers);
        for (int i = 0; i < order.length; i++) {
            try {
                boolean isRemote = com.zai.adshield.core.RuleRouter.SRC_REMOTE.equals(order[i]);
                Subscription sub = isRemote ? remote : builtin;
                if (sub == null) continue;
                RuleEngine.Hit h = sub.match(pkg, root, ctx, 400, RUNTIME, isRemote ? "r|" : "b|");
                if (h != null) {
                    subRuleHit.incrementAndGet();
                    if (isRemote) remoteHits++;
                    else builtinHits++;
                    h.src = com.zai.adshield.core.RuleRouter.zh(order[i]) + "·" + (h.src == null ? "" : h.src);
                    out.add(h);
                }
            } catch (Throwable ignored) {
            }
        }
        /* v7.9.1：有覆盖但本轮无命中（activityIds 限定/选择器链/形态闸/限流）——
         * 与 noCover（两源都无该包规则）分开统计。 */
        if (out.isEmpty() && (remoteCovers || builtinCovers)) {
            subNoMatch.incrementAndGet();
        }
        return out;
    }

    /** v7.1.0：该应用的订阅两源覆盖一行诊断（扫描未命中日志用——
     *  使用顺序问题可见化：用户上传日志后直接看到该应用的规则覆盖状态）。
     *  空串 = 两源都未覆盖（无订阅内容——不上噪声）。 */
    public static String coverageLine(String pkg) {
        if (pkg == null || pkg.length() == 0) return "";
        Subscription bi = builtinSub;
        Subscription rm = remoteEnabled ? remoteSub : null;
        boolean bc = bi != null && bi.covers(pkg);
        boolean rc = rm != null && rm.covers(pkg);
        if (!bc && !rc) return "";
        StringBuilder b = new StringBuilder(64);
        b.append("订阅[");
        if (rc) b.append("市场覆盖");
        if (bc) b.append(b.length() > 3 ? "+" : "").append("内置覆盖");
        b.append("]");
        return b.toString();
    }

    /** v5.3.0：订阅（内置或启用的远程）是否覆盖该应用的专属规则 */
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

    /** v7.0.0：镜像切换进度回调（导入按钮→页内「正在切换镜像 X/Y」反馈）。
     *  接口（非匿名内部类）+ lambda 实现——兼容 d8 8.2。 */
    public interface ProgressSink {
        void onProgress(int attempt, int total, String host);
    }

    /** 设置/清空订阅 URL 并立即拉取。返回 JSON 结果 {ok, ...}。
     *  v7.0.0：progress 镜像切换进度回调（可 null——静默更新路径不回 UI）。 */
    public static String setUrl(Context ctx, String url, ProgressSink progress) {
        SharedPreferences sp = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE);
        String u = url == null ? "" : url.trim();
        if (u.length() == 0) {
            // 清空：移除远程订阅
            remoteSub = null;
            subUrl = "";
            lastError = "";
            sp.edit().remove("subUrl").remove("subCache").remove("subSnapshotPending").apply();
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
        return fetch(ctx, u, sp, progress);
    }

    /** 立即更新（订阅页「检查更新」按钮 / 24h 静默更新共用） */
    public static String updateNow(Context ctx) {
        ensureLoaded(ctx);
        if (subUrl == null || subUrl.length() == 0) {
            return "{\"ok\":false,\"error\":\"未添加订阅\"}";
        }
        SharedPreferences sp = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE);
        return fetch(ctx, subUrl, sp, null);
    }

    /** 24h 静默更新（服务心跳顺带调用；无订阅/不到期秒回）。
     *  v7.0.0：离线快照导入后 1 小时即提前重拉（联网恢复后自动换成最新版）。 */
    public static void maybeSilentUpdate(Context ctx) {
        try {
            ensureLoaded(ctx);
            if (subUrl == null || subUrl.length() == 0 || !remoteEnabled) return;
            long now = System.currentTimeMillis();
            SharedPreferences sp = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE);
            boolean snapshotPending = sp.getBoolean("subSnapshotPending", false);
            long interval = snapshotPending ? SNAPSHOT_RETRY_MS : UPDATE_INTERVAL_MS;
            if (now - lastUpdateAt < interval) return;
            fetch(ctx, subUrl, sp, null);
        } catch (Throwable ignored) {
        }
    }

    static String fetch(Context ctx, String url, SharedPreferences sp, ProgressSink progress) {
        /* v7.0.0 Connection reset 终根治（用户实锤：订阅市场点击导入时提示
         * 「拉取失败，Connection reset」——v6.1 五通道链在部分运营商网络下
         * 仍全军覆没）：
         *  ① 八通道镜像链（SubMirrors：原址→fastly→gcore→testingcf→ghfast
         *     代理→gh-proxy 代理→raw 直连；每通道先同址重试 600ms 再换址）；
         *  ② 全通道失败 → 市场精选源离线快照兜底（APK 内置最新内容，
         *     snapshot:true 标注）——导入路径从此【永不硬失败】；
         *  ③ 快照导入后 1h 心跳自动重拉最新版。
         * 进度回调实时上报「正在切换镜像 X/Y · host」。 */
        java.util.List<String> variants = com.zai.adshield.core.SubMirrors.variants(url);
        Throwable lastEx = null;
        for (int i = 0; i < variants.size(); i++) {
            if (progress != null) {
                try {
                    progress.onProgress(i + 1, variants.size(),
                            com.zai.adshield.core.SubMirrors.hostOf(variants.get(i)));
                } catch (Throwable ignored) {
                }
            }
            try {
                return fetchOne(ctx, variants.get(i), url, sp);
            } catch (Throwable e) { // v7.3.0：catch Throwable（StackOverflowError 类已由 Json5 深度上限拦下，此处再兜底防穿透）
                lastEx = e;
            }
        }
        /* v7.0.0 离线快照兜底：市场精选源全通道失败 → 导入 APK 内置快照。
         * 自定义链接无快照不兜底（快照注册表只含三个精选源）。 */
        String asset = com.zai.adshield.core.SubMirrors.snapshotAssetFor(url);
        if (asset != null) {
            try {
                return fetchSnapshot(ctx, asset, url, sp);
            } catch (Throwable e) {
                lastEx = e;
            }
        }
        String raw = lastEx == null ? "网络失败" : safeMsg(lastEx);
        lastError = raw;
        String msg = humanizeNetError(raw);
        String hint = variants.size() > 1
                ? "（已自动重试并切换 " + variants.size() + " 个镜像通道仍失败——多为当前网络对海外源全阻断，可稍后重试或换 Wi-Fi/流量后重试一次）"
                : " · 订阅页「订阅市场」可一键导入活跃维护的订阅";
        return "{\"ok\":false,\"error\":" + jsonStr("拉取失败：" + msg + hint) + "}";
    }

    /** v7.0.0：从 APK assets 读取离线快照并导入。快照导入成功后
     *  subSnapshotPending 置位（1h 心跳自动重拉真源最新版）。 */
    static String fetchSnapshot(Context ctx, String asset, String rememberUrl, SharedPreferences sp) throws Exception {
        java.io.InputStream in = ctx.getAssets().open(asset);
        String body;
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            body = bo.toString("UTF-8");
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
        }
        Subscription parsed = Subscription.parse(body, 1, 1000);
        int apps = parsed.apps.size();
        int rules = parsed.ruleCount();
        if (apps == 0 || rules == 0) throw new IllegalStateException("快照里没有可用的应用规则");
        remoteSub = parsed;           // 原子切换快照
        subUrl = rememberUrl;
        lastError = "";
        lastUpdateAt = System.currentTimeMillis();
        sp.edit().putString("subUrl", rememberUrl)
                .putString("subCache", body)
                .putLong("subUpdatedAt", lastUpdateAt)
                .putBoolean("subSnapshotPending", true)
                .apply();
        return "{\"ok\":true,\"name\":" + jsonStr(parsed.name)
                + ",\"apps\":" + apps + ",\"rules\":" + rules
                + ",\"snapshot\":true}";
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
        // v7.0.0：真源拉取成功 → 清快照待重拉标记（已是最新版）
        sp.edit().putString("subUrl", rememberUrl)
                .putString("subCache", body)
                .putLong("subUpdatedAt", lastUpdateAt)
                .putBoolean("subSnapshotPending", false)
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
        /* v7.9.1 七分类统计（用户指令：订阅未命中必须分列——未加载/应用不
         * 匹配/页面选择器不匹配/命中/被安全闸拦截/点击失败/点击成功）。 */
        b.append(",\"subStat\":{\"notLoaded\":").append(subNotLoaded.get())
                .append(",\"noCover\":").append(subNoCover.get())
                .append(",\"noMatch\":").append(subNoMatch.get())
                .append(",\"hit\":").append(subRuleHit.get())
                .append(",\"blocked\":").append(subBlocked.get())
                .append(",\"clickFail\":").append(subClickFail.get())
                .append(",\"clickOk\":").append(subClickOk.get())
                .append("}");
        /* v7.9.5 GKD 证据链八格（用户指令七：consulted/matched/candidateAccepted/
         *  clicked/verifiedSuccess/clickFail/falseClick/blocked——订阅参与度
         *  与出手-关闭闭环的证据链；falseClick 恒 0 即「自动化环境无真机误触
         *  样本」的诚实表达）。 */
        b.append(",\"subStatV2\":{\"consulted\":").append(subConsulted.get())
                .append(",\"matched\":").append(subRuleHit.get())
                .append(",\"candidateAccepted\":").append(subCandAccepted.get())
                .append(",\"clicked\":").append(subClickOk.get())
                .append(",\"verifiedSuccess\":").append(subVerifiedSuccess.get())
                .append(",\"clickFail\":").append(subClickFail.get())
                .append(",\"falseClick\":").append(subFalseClick.get())
                .append(",\"blocked\":").append(subBlocked.get())
                .append("}");
        b.append(",\"error\":").append(jsonStr(lastError == null ? "" : lastError));
        try {
            boolean snapPending = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE)
                    .getBoolean("subSnapshotPending", false);
            if (rm != null && snapPending) b.append(",\"snapshot\":true");
        } catch (Throwable ignored) {
        }
        b.append("}");
        return b.toString();
    }

    /** 错误消息安全化（null 消息时给类名，不打印敏感内容） */
    static String safeMsg(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.length() == 0) return t.getClass().getSimpleName();
        return m.length() > 120 ? m.substring(0, 120) : m;
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
