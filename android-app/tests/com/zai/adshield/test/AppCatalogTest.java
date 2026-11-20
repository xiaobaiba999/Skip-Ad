package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.Json5;
import com.zai.adshield.core.Subscription;
import com.zai.adshield.test.TestHost.SimNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 200+ 应用生态兼容性目录 · L1 规则与引擎自动化测试（v7.9.2 新套件）。
 *
 * 数据源：compatibility/apps_catalog.json（233 款 · 12 类配额 ·
 * 包名来源三层严格区分：引擎自证 / 网络核实 / 知识库高置信）。
 *
 * 六段断言面：
 *  S1 目录完整性（规模/配额/包名格式/唯一性/字段合法性）
 *  S2 引擎内置订阅 ↔ 目录双向一致性（119 款全落座 + 规则全可解析）
 *  S3 FeedAdGuard 信息流名单 ↔ 目录全落座（v7.9.2 网易新闻包名漏配此类缺陷的回归闸）
 *  S4 GKD 三源快照解析 + 目录 gkd 覆盖交叉核对
 *  S5 逐应用规则匹配仿真（内置订阅覆盖的每一款目录应用：
 *      开屏窗 + 跳过键 → walk + decide 必须产出点击动作——引擎通用逻辑按包名路由）
 *  S6 v7.9.2 包名校准五项回归（联通/网易新闻/银行互标/剪映-CapCut/飞常准-航班管家）
 *
 * 诚实口径：本套件通过 = 规则加载/包名/引擎通用逻辑断言通过（L1）。
 * 不代表真机广告识别已验证（L3 未开始）。
 */
public final class AppCatalogTest {

    static Map<String, Object> cat;
    static List<Object> appsRaw;
    static final List<App> APPS = new ArrayList<App>();

    static final class App {
        String name, pkg, category, source, priority;
        boolean builtin, feed, gkd;
    }

    public static void main(String[] args) {
        loadCatalog();
        if (appsRaw == null) {
            System.out.println("  ❌ 目录不可加载，套件终止");
            TestHost.done();
            return;
        }

        s1_integrity();
        s2_builtinCrossCheck();
        s3_feedCrossCheck();
        s4_gkdSnapshots();
        s5_perAppSimulation();
        s6_packageFixRegressions();

        TestHost.done();
    }

    /* ═══ 目录加载（android-app 工作目录 / 仓库根两布局兼容）═══ */
    static void loadCatalog() {
        TestHost.suite("L1 · 目录加载与规模");
        String[] candidates = {
                "../compatibility/apps_catalog.json",
                "compatibility/apps_catalog.json",
                "../../compatibility/apps_catalog.json",
        };
        String text = null;
        for (String p : candidates) {
            try {
                File f = new File(p);
                if (f.isFile()) {
                    byte[] b = new byte[(int) f.length()];
                    java.io.FileInputStream fin = new FileInputStream(f);
                    int off = 0, n;
                    while (off < b.length && (n = fin.read(b, off, b.length - off)) > 0) off += n;
                    fin.close();
                    text = new String(b, StandardCharsets.UTF_8);
                    break;
                }
            } catch (IOException ignore) {
            }
        }
        TestHost.check(text != null, "apps_catalog.json 可定位可读取");
        if (text == null) return;
        try {
            cat = Json5.asMap(Json5.parse(text));
            appsRaw = Json5.asList(cat.get("apps"));
        } catch (Exception e) {
            System.out.println("  ❌ 解析异常: " + e);
            return;
        }
        TestHost.check(appsRaw != null && !appsRaw.isEmpty(), "目录解析出 apps 数组（" + (appsRaw == null ? 0 : appsRaw.size()) + " 款）");

        for (Object o : appsRaw) {
            Map<String, Object> m = Json5.asMap(o);
            App a = new App();
            a.name = Json5.str(m, "appName", "");
            a.pkg = Json5.str(m, "packageName", "");
            a.category = Json5.str(m, "category", "");
            a.source = Json5.str(m, "source", "");
            a.priority = Json5.str(m, "priority", "");
            Map<String, Object> cov = Json5.asMap(m.get("subscriptionCoverage"));
            a.builtin = Json5.boolv(cov, "builtin", false);
            a.feed = Json5.boolv(cov, "feed", false);
            a.gkd = Json5.boolv(cov, "gkd", false);
            APPS.add(a);
        }
        TestHost.check(APPS.size() >= 200, "总量 ≥200（实际 " + APPS.size() + "）");
    }

    /* ═══ S1 完整性 ═══ */
    static void s1_integrity() {
        TestHost.suite("L1 · 目录完整性（格式/唯一性/配额）");

        HashSet<String> seen = new HashSet<String>();
        int dup = 0, badFmt = 0, noName = 0, badPri = 0, badCat = 0, badSrc = 0;
        for (App a : APPS) {
            if (!a.pkg.matches("[a-zA-Z][a-zA-Z0-9._]*")) badFmt++;
            if (!seen.add(a.pkg)) dup++;
            if (a.name == null || a.name.isEmpty()) noName++;
            if (!a.priority.equals("P0") && !a.priority.equals("P1") && !a.priority.equals("P2")) badPri++;
            if (!CATS.containsKey(a.category)) badCat++;
            if (!a.source.startsWith("engine-builtin-sub")
                    && !a.source.startsWith("web-verified:")
                    && !a.source.equals("knowledge-base")) badSrc++;
        }
        TestHost.check(badFmt == 0, "包名格式全合法（违规 " + badFmt + "）");
        TestHost.check(dup == 0, "包名零重复（重复 " + dup + "）");
        TestHost.check(noName == 0, "应用名非空");
        TestHost.check(badPri == 0, "priority 字段合法（P0/P1/P2）");
        TestHost.check(badCat == 0, "category 字段合法（12 类之一）");
        TestHost.check(badSrc == 0, "source 字段合法（三层来源之一）");

        // 分类配额（用户目录规格：12 类下限）
        Map<String, Integer> quota = new HashMap<String, Integer>();
        quota.put("social-im", 25); quota.put("video", 25); quota.put("news-feed", 20);
        quota.put("ecommerce", 25); quota.put("music-audio", 10); quota.put("browser-search", 10);
        quota.put("tools-system", 20); quota.put("reading", 15); quota.put("games", 15);
        quota.put("education", 15); quota.put("finance-life", 15); quota.put("misc", 10);
        int under = 0;
        for (Map.Entry<String, Integer> e : quota.entrySet()) {
            int c = 0;
            for (App a : APPS) if (a.category.equals(e.getKey())) c++;
            if (c < e.getValue()) under++;
        }
        TestHost.check(under == 0, "12 类配额全达标（不足 " + under + " 类）");
        TestHost.check(APPS.size() >= 232, "本轮基线 ≥232 款（实际 " + APPS.size() + "）");
    }

    /* ═══ S2 内置订阅 ↔ 目录双向一致性 ═══ */
    static void s2_builtinCrossCheck() {
        TestHost.suite("L1 · 内置订阅 ↔ 目录双向一致性");

        Subscription sub = null;
        try {
            sub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
        } catch (Exception e) {
            System.out.println("  ❌ 解析异常: " + e);
        }
        TestHost.check(sub != null, "内置订阅可解析");
        if (sub == null) return;

        HashSet<String> builtinPkgs = new HashSet<String>();
        int rulesTotal = 0;
        for (Subscription.App sa : sub.apps) {
            if (!sa.id.equals("*")) builtinPkgs.add(sa.id);
            rulesTotal += sa.rules.size();
        }
        TestHost.check(builtinPkgs.size() >= 118, "内置订阅规模 ≥118（实际 " + builtinPkgs.size() + "）");
        TestHost.check(rulesTotal >= 118, "内置订阅规则总量 ≥118（实际 " + rulesTotal + "）");

        // 方向①：内置订阅每个包名在目录里都有一款应用（引擎不留「孤儿包」）
        HashSet<String> catalogPkgs = new HashSet<String>();
        for (App a : APPS) catalogPkgs.add(a.pkg);
        List<String> orphans = new ArrayList<String>();
        for (String p : builtinPkgs) if (!catalogPkgs.contains(p)) orphans.add(p);
        TestHost.check(orphans.isEmpty(),
                "内置订阅零孤儿包（目录全落座；孤儿: " + orphans + "）");

        // 方向②：目录声明 builtin 覆盖的每款应用确实在内置订阅里（不虚标）
        int falseBuiltin = 0;
        for (App a : APPS) if (a.builtin && !builtinPkgs.contains(a.pkg)) falseBuiltin++;
        TestHost.check(falseBuiltin == 0, "目录 builtin 标注零虚标（虚标 " + falseBuiltin + "）");

        // v7.9.2 新增四包在场（联通/剪映国内版/飞常准/CapCut 名称校准后的四条）
        TestHost.check(builtinPkgs.contains("com.sinovatech.unicom.ui"), "v7.9.2: 联通真实包名在场");
        TestHost.check(builtinPkgs.contains("com.lemon.lv"), "v7.9.2: 剪映国内版包名在场");
        TestHost.check(builtinPkgs.contains("veryzhun.vzcom"), "v7.9.2: 飞常准真实包名在场");
        TestHost.check(builtinPkgs.contains("com.chinamworld.main"), "v7.9.2: 建行包名在场");
    }

    /* ═══ S3 FeedAdGuard 名单 ↔ 目录一致性 ═══ */
    static void s3_feedCrossCheck() {
        TestHost.suite("L1 · 信息流名单 ↔ 目录一致性");

        int feedApps = 0;
        List<String> feedMissing = new ArrayList<String>();
        for (App a : APPS) {
            if (a.feed) {
                feedApps++;
                if (!FeedAdGuard.isFeedApp(a.pkg)) feedMissing.add(a.pkg);
            }
        }
        TestHost.check(feedApps >= 19, "feed 覆盖目录应用 ≥19（实际 " + feedApps + "）");
        TestHost.check(feedMissing.isEmpty(), "目录 feed 标注与引擎名单零漂移（漂移: " + feedMissing + "）");

        // 反向：引擎 FEED_APPS（透过 isFeedApp 白名单探测法）全落座目录
        // v7.9.2 前死线：com.netease.newsreader（缺 .activity 后缀）在目录里
        // 永远找不到家——此类缺陷由本断言面捕获。
        String[] probe = {
                "com.tencent.mm", "com.sina.weibo", "com.ss.android.article.news",
                "com.ss.android.ugc.aweme", "com.smile.gifmaker", "com.xingin.xhs",
                "tv.danmaku.bili", "com.zhihu.android", "com.baidu.searchbox",
                "com.UCMobile", "com.tencent.mtt", "com.tencent.news",
                "com.netease.newsreader.activity", "com.sina.news", "com.quark.browser",
                "com.snda.wifilocating", "com.hupu.games", "com.cubic.autohome",
                "com.zol.android", "com.xiaomi.market",
        };
        HashSet<String> catalogPkgs = new HashSet<String>();
        for (App a : APPS) catalogPkgs.add(a.pkg);
        List<String> homeless = new ArrayList<String>();
        for (String p : probe) {
            if (FeedAdGuard.isFeedApp(p) && !catalogPkgs.contains(p)) homeless.add(p);
        }
        TestHost.check(homeless.isEmpty(), "引擎信息流名单全落座目录（无家: " + homeless + "）");

        // 网易新闻真实包名必须被引擎名单接纳（v7.9.2 修复回归闸）
        TestHost.check(FeedAdGuard.isFeedApp("com.netease.newsreader.activity"),
                "v7.9.2: 网易新闻真实包名（.activity 后缀）被信息流名单接纳");
        TestHost.check(!FeedAdGuard.isFeedApp("com.netease.newsreader"),
                "v7.9.2: 旧死值包名不再出现于名单");
    }

    /* ═══ S4 GKD 三源快照 ═══ */
    static void s4_gkdSnapshots() {
        TestHost.suite("L1 · GKD 三源快照解析与覆盖");

        String[] snaps = {"ganlin", "linarm", "xnx"};
        int parsed = 0;
        for (String s : snaps) {
            String text = readAsset("assets/subsnap/" + s + ".json5");
            if (text == null) text = readAsset("android-app/assets/subsnap/" + s + ".json5");
            if (text == null) continue;
            try {
                Map<String, Object> m = Json5.asMap(Json5.parse(text));
                TestHost.check(m.get("apps") != null || m.get("categories") != null,
                        "快照可解析: " + s);
                parsed++;
            } catch (Exception e) {
                TestHost.check(false, "快照可解析: " + s + "（异常 " + e + "）");
            }
        }
        TestHost.check(parsed == 3, "三源快照全部可解析（" + parsed + "/3）");

        int gkdCovered = 0;
        for (App a : APPS) if (a.gkd) gkdCovered++;
        TestHost.check(gkdCovered >= 140,
                "目录 gkd 快照覆盖 ≥140（实际 " + gkdCovered + "）");
    }

    /* ═══ S5 逐应用规则匹配仿真 ═══ */
    static void s5_perAppSimulation() {
        TestHost.suite("L1 · 逐应用规则匹配与引擎路由（内置订阅覆盖全量）");

        // 5a. RuleEngine 分应用规则行路由：每款目录应用包名过 rowsFor 不炸，
        //     已知有分应用行的包名必须路由出非空行（引擎↔目录第三向一致性）
        String[] mustHaveRows = {
                "com.sina.weibo", "com.ximalaya.ting.android", "tv.danmaku.bili",
                "com.qiyi.video", "com.youku.phone", "com.tencent.qqlive",
                "com.gotokeep.keep", "com.xingin.xhs", "com.zhihu.android",
                "com.sankuai.meituan", "com.dianping.v1",
        };
        int routed = 0;
        for (String p : mustHaveRows) {
            String[][] rows = com.zai.adshield.core.RuleEngine.rowsFor(p);
            if (rows != null && rows.length > 0) routed++;
        }
        TestHost.check(routed == mustHaveRows.length,
                "分应用规则行路由全命中（" + routed + "/" + mustHaveRows.length + "）");

        // 5b. 逐应用全量：rowsFor 永不返回 null（引擎路由面每包都活着）
        int nullRows = 0;
        for (App a : APPS) {
            if (com.zai.adshield.core.RuleEngine.rowsFor(a.pkg) == null) nullRows++;
        }
        TestHost.check(nullRows == 0, "逐应用 rowsFor 零 null（" + APPS.size() + " 款）");

        // 5c. 开屏窗 + 跳过键仿真：walk + decide 必须产出动作（引擎通用逻辑按包名执行）
        int simulated = 0, hit = 0;
        List<String> misses = new ArrayList<String>();
        for (App a : APPS) {
            if (!a.builtin) continue;
            simulated++;
            Ctx ctx = new Ctx();
            ctx.screenW = 1080;
            ctx.screenH = 2400;
            ctx.pkg = a.pkg;
            ctx.activityId = "com.example.SplashActivity";
            ctx.elapsedMs = 600;
            ctx.splashLive = true;
            ScanFind f = AdDecisionCore.walk(juicyAdTree(), ctx);
            Decision d = AdDecisionCore.decide(f, ctx);
            boolean clicked = d != null
                    && (AdDecisionCore.A_CLICK_BOUNDS.equals(d.action) || AdDecisionCore.A_TAP_POINT.equals(d.action)
                        || AdDecisionCore.A_CLOSE.equals(d.action) || AdDecisionCore.A_BACK.equals(d.action));
            if (clicked) hit++;
            else misses.add(a.name + ":" + a.pkg);
        }
        TestHost.check(simulated >= 118, "仿真覆盖内置订阅全量（" + simulated + " 款）");
        TestHost.check(hit == simulated && misses.isEmpty(),
                "开屏窗+跳过键 逐应用全命中（" + hit + "/" + simulated + "；未命中: "
                        + (misses.isEmpty() ? "无" : misses.subList(0, Math.min(5, misses.size()))) + "）");

        // 反例：同款应用正常主界面窗（非开屏·长文本内容树）零出手（误触红线）
        int guardOk = 0, guardTotal = 0;
        for (App a : APPS) {
            if (!a.builtin) continue;
            guardTotal++;
            Ctx ctx = new Ctx();
            ctx.screenW = 1080;
            ctx.screenH = 2400;
            ctx.pkg = a.pkg;
            ctx.activityId = "com.example.MainActivity";
            ctx.elapsedMs = 5000;
            ctx.splashLive = false;
            ScanFind f = AdDecisionCore.walk(contentTree(), ctx);
            Decision d = AdDecisionCore.decide(f, ctx);
            boolean none = d == null || AdDecisionCore.A_NONE.equals(d.action);
            if (none) guardOk++;
        }
        TestHost.check(guardOk == guardTotal,
                "正常主界面窗逐应用零出手（" + guardOk + "/" + guardTotal + "）");
    }

    /* ═══ S6 v7.9.2 包名校准回归 ═══ */
    static void s6_packageFixRegressions() {
        TestHost.suite("L1 · v7.9.2 包名校准五项回归");

        String b = Subscription.BUILTIN_JSON5;
        TestHost.check(!b.contains("com.sinovatech.unicom.ui.welcome.WelcomeActivity"),
                "联通：Activity 类名误作包名的死条目不复活");
        TestHost.check(b.contains("id: 'com.sinovatech.unicom.ui', name: '中国联通'"),
                "联通：真实包名 com.sinovatech.unicom.ui 在场");

        TestHost.check(b.contains("id: 'com.chinamworld.main', name: '建设银行'"),
                "建行：chinamworld.main ↔ 建设银行 名称对应");
        TestHost.check(b.contains("id: 'com.chinamworld.BOCMB', name: '中国银行'"),
                "中行：BOCMB ↔ 中国银行 名称对应");

        TestHost.check(b.contains("id: 'com.lemon.lvoverseas', name: 'CapCut'"),
                "CapCut：lvoverseas 不再误标「剪映」");
        TestHost.check(b.contains("id: 'com.lemon.lv', name: '剪映'"),
                "剪映国内版：com.lemon.lv 在场");

        TestHost.check(b.contains("id: 'veryzhun.vzcom', name: '飞常准'"),
                "飞常准：真实包名 veryzhun.vzcom 在场");
        TestHost.check(b.contains("id: 'com.flightmanager.view', name: '航班管家'"),
                "航班管家：com.flightmanager.view 名称归位");
    }

    /* ═══ 工具 ═══ */
    static final Map<String, String> CATS = new HashMap<String, String>();
    static {
        CATS.put("social-im", "社交与即时通讯"); CATS.put("video", "短视频与视频播放");
        CATS.put("news-feed", "新闻资讯与信息流"); CATS.put("ecommerce", "电商与购物");
        CATS.put("music-audio", "音乐与音频"); CATS.put("browser-search", "浏览器与搜索");
        CATS.put("tools-system", "工具与系统应用"); CATS.put("reading", "阅读与小说");
        CATS.put("games", "游戏与游戏中心"); CATS.put("education", "学习与教育");
        CATS.put("finance-life", "金融与生活服务"); CATS.put("misc", "其他常用");
    }

    static String readAsset(String p) {
        try {
            File f = new File(p);
            if (!f.isFile()) return null;
            byte[] b = new byte[(int) f.length()];
            FileInputStream fin = new FileInputStream(f);
            int off = 0, n;
            while (off < b.length && (n = fin.read(b, off, b.length - off)) > 0) off += n;
            fin.close();
            return new String(b, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** 开屏广告窗仿真树：跳过键 + 倒计时 + 关闭键 + CTA（真机日志同构）。 */
    static SimNode juicyAdTree() {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode skip = TestHost.skipButton("跳过");
        skip.clickable = true;
        SimNode countdown = new SimNode("跳过 3", 900, 80, 1060, 150);
        SimNode close = new SimNode("×", 1000, 90, 1050, 140);
        close.clickable = true;
        SimNode cta = new SimNode("立即领取", 300, 800, 780, 900);
        cta.clickable = true;
        root.add(skip);
        root.add(countdown);
        root.add(close);
        root.add(cta);
        return root;
    }

    /** 正常主界面仿真树：长文本内容 + 输入框（零出手反例）。 */
    static SimNode contentTree() {
        SimNode root = new SimNode();
        root.cls = "android.widget.FrameLayout";
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode article = new SimNode(
                "这是一段很长的正文内容，用于模拟正常主界面窗。今天的推荐内容已经更新完毕，请享用更多精彩内容。",
                60, 200, 1020, 1900);
        article.scrollable = false;
        SimNode input = new SimNode("", 60, 2280, 1020, 2380);
        input.cls = "android.widget.EditText";
        input.editable = true;
        root.add(article);
        root.add(input);
        return root;
    }
}
