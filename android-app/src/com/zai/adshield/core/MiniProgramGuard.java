package com.zai.adshield.core;

/**
 * 小程序/交易容器守卫（L0 · v5.0.0）——「抖音团购券跳转小程序被拦截」根治。
 * ─────────────────────────────────────────────────────────────
 * 事故机理（用户实锤）：抖音里点团购券 → 拉起小程序/电商容器
 * （MiniAppActivity / CommercializeContainerActivity / PrivilegeDetailActivity
 * / EcPromotionActivity / LynxContainerActivity …）。容器加载页常含
 * 「广告/跳过」类文案，旧版把优惠券页当广告关掉（或点了容器内假「跳过」），
 * 用户被弹回、团购券无法使用。
 *
 * 对策（纵深防御，服务侧事件入口 + 扫描链 + 决策核心三层拦截）：
 *  · 容器内【绝不扫描、绝不点击、绝不学返回键、绝不学位置】——
 *    引擎在小程序里完全「不存在」，用户跳转畅通无阻；
 *  · 检测以窗口类名为主（快路径，零树扫描）：抖音全部小程序/团购容器 +
 *    微信 AppBrandUI + 支付宝 Nebula + QQ 小程序 + 通用 miniapp 标识；
 *  · 节点标识兜底：树内出现 miniapp/appbrand/nebula vid 或「小程序」
 *    标题的容器（类名未知的漏网容器）同样保护。
 *
 * 纯 Java · 零 Android 依赖 · JVM 可单测（与 AdDecisionCore 同规约）。
 * 真机日志佐证：issue#45（v4.5.0）小米 2410DPN6CC 出现
 * MiniAppChildProcessMultiInsActivity0 窗口——「miniapp」标记即命中；
 * issue#51-55（v4.4.0）支付宝 XRiverActivity 容器胶囊「关闭」被代点
 * （v5.1.1 增补 xriver 标记 + 胶囊文案兑底）。
 */
public final class MiniProgramGuard {

    private MiniProgramGuard() {
    }

    /** 抖音（com.ss.android.ugc.aweme 及字节系）小程序/团购/电商容器标记。 */
    static final String[] DOUYIN_MARKERS = {
            "miniapp",            // com.ss.android.ugc.aweme.miniapp.* / MiniAppActivity / MiniAppChildProcess*
            "appbrand",
            "commercialize",      // CommercializeContainerActivity（直播团购/电商容器）
            "privilege",          // PrivilegeDetailActivity（团购券详情）
            "promotion",          // EcPromotionActivity / UnionPromotionActivity
            "unionpromotion",
            "bae",                // com.bytedance.bae.*（小程序运行时）
            "lynx",               // LynxContainerActivity（动态化容器，团购落地页）
            "ecom",               // 电商容器
            "webbrowser",         // 抖音内置浏览器
            "browsercontainer",
    };

    /** 微信小程序容器标记（com.tencent.mm）。 */
    static final String[] WECHAT_MARKERS = {
            "appbrand",           // com.tencent.mm.plugin.appbrand.ui.AppBrandUI
            "wpa",                // WpaWebViewActivity（公众号/客服 webview）
            "webviewuikit",
            "qqmini",
    };

    /** 支付宝小程序/H5 容器标记（com.eg.android.AlipayGphone）。 */
    static final String[] ALIPAY_MARKERS = {
            "nebula",             // com.alipay.mobile.nebulacore.ui.H5Activity
            "miniapp",
            "h5activity",
            "tinycloud",
            /* v5.1.1（issue#51-55 v4.4.0 日志实锤）：XRiverActivity——
             * 支付宝新一代小程序/跨端容器（com.alipay.mobile.xriver），
             * 胶囊「收藏到我的小程序/更多/关闭」的「关闭」被开屏×关闭键
             * 路径代点 = 用户被踢出小程序（与 #45 抖音团购券同类事故） */
            "xriver",
    };

    /** 通用容器标记（任何包都生效——厂商/第三方宿主的小程序容器）。 */
    static final String[] GENERIC_MARKERS = {
            "miniapp",
            "mini_app",
            "miniprogram",
            "mini_program",
            "smallprogram",
            "appbrand",
            "nebula",
            "miniappcontainer",
    };

    /** 小程序宿主包（显式保护名单，命中即套用宿主专属容器表）。 */
    public static final String PKG_DOUYIN = "com.ss.android.ugc.aweme";
    public static final String PKG_KUAISHOU = "com.smile.gifmaker";
    public static final String PKG_WECHAT = "com.tencent.mm";
    public static final String PKG_QQ = "com.tencent.mobileqq";
    public static final String PKG_ALIPAY = "com.eg.android.AlipayGphone";

    /** 微信主界面放行（isChatMainUi 已零扫描，这里不重复保护——
     *  AppBrandUI 才是小程序；LauncherUI 是聊天主界面，绝不能因
     *  通用标记误伤正常聊天窗的弹窗关闭路径）。 */
    static final String WECHAT_MAIN_UI = "launcherui";

    /**
     * 当前窗口是否小程序/交易容器（快路径：只看包名 + 窗口类名，零树扫描）。
     * `cls` 传事件 className 或当前窗口类名（winClassOf(pkg)）。
     */
    public static boolean isMiniProgramWindow(String pkg, String cls) {
        return matchReason(pkg, cls) != null;
    }

    /**
     * 同 {@link #isMiniProgramWindow}，命中返回中文原因（日志用），未命中返回 null。
     */
    public static String matchReason(String pkg, String cls) {
        if (pkg == null || cls == null) return null;
        String c = cls.toLowerCase();
        if (c.length() == 0) return null;

        if (PKG_DOUYIN.equals(pkg) || PKG_KUAISHOU.equals(pkg) || pkg.contains("aweme")
                || pkg.contains("smile.gifmaker") || pkg.contains("beacon")) {
            for (String m : DOUYIN_MARKERS) {
                if (c.contains(m)) return "抖音小程序/团购容器(" + m + ")";
            }
        }
        if (PKG_WECHAT.equals(pkg)) {
            // 聊天主界面（LauncherUI）明确放行——不是容器
            if (c.contains(WECHAT_MAIN_UI)) return null;
            for (String m : WECHAT_MARKERS) {
                if (c.contains(m)) return "微信小程序容器(" + m + ")";
            }
        }
        if (PKG_ALIPAY.equals(pkg)) {
            for (String m : ALIPAY_MARKERS) {
                if (c.contains(m)) return "支付宝小程序容器(" + m + ")";
            }
        }
        if (PKG_QQ.equals(pkg)) {
            if (c.contains("miniapp") || c.contains("qqmini")) {
                return "QQ小程序容器";
            }
        }
        // 通用兜底：任何包里的小程序容器标识（「miniapp」子串覆盖
        // MiniAppChildProcessMultiInsActivity0 等 MIUI/厂商变体，issue#45 实锤）
        for (String m : GENERIC_MARKERS) {
            if (c.contains(m)) return "小程序容器(" + m + ")";
        }
        return null;
    }

    /**
     * 节点标识兜底：树内出现小程序标识（vid 含 miniapp/appbrand/nebula，
     * 或「小程序」标题/描述）→ 该树视为容器内（类名未知的漏网容器）。
     * 预算制（budget ≤ 常用 80）——横幅/浅树场景零性能顾虑。
     */
    public static boolean treeHasMiniProgramMarker(AdDecisionCore.NodeView root, int budget) {
        if (root == null || budget <= 0) return false;
        int[] b = {budget};
        return findMarker(root, 0, b) != null;
    }

    private static AdDecisionCore.NodeView findMarker(AdDecisionCore.NodeView n, int depth, int[] budget) {
        if (n == null || depth > 32 || budget[0] <= 0) return null;
        budget[0]--;
        String vid = nz(n.viewId());
        String v = vid.toLowerCase();
        if (v.contains("miniapp") || v.contains("appbrand") || v.contains("mini_program")
                || v.contains("miniprogram") || v.contains("nebula")
                /* v5.1.1：XRiver 容器 vid 标识（类名未知的漏网容器兑底） */
                || v.contains("xriver")) {
            return n;
        }
        String t = nz(n.text());
        String d = nz(n.desc());
        if ("小程序".equals(t) || "小程序".equals(d)) return n;
        /* v5.1.1：小程序胶囊特征文案（issue#51-55 实锤「收藏到我的小程序」
         * 胶囊菜单——精确子串，正常内容页几乎不可能出现该文案）；
         * 微信胶囊「更多/关闭」单独不成立（误伤面大），必须搭配胶囊文案 */
        if (t.contains("收藏到我的小程序") || d.contains("收藏到我的小程序")) return n;
        for (int i = 0; i < n.childCount(); i++) {
            AdDecisionCore.NodeView r = findMarker(n.child(i), depth + 1, budget);
            if (r != null) return r;
        }
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
