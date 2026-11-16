package com.zai.adshield;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 开屏广告智能跳过服务（无障碍服务实现 · v3.3.0）
 *
 * 设计依据（2026-11 调研 + 真机反馈复盘）：
 * 1. 市面开屏广告的「跳过」是一个真实控件（TextView/Button，text 含「跳过」），存在于无障碍控件树中，
 *    可通过 text / viewIdResourceName 定位后 performAction(ACTION_CLICK) —— 与 GKD、李跳跳同原理。
 * 2. 「拦精灵」案（2024，芒果TV 诉）判决边界：跳过类软件只能「替用户点击跳过按钮」，
 *    不得直接屏蔽广告本身。本服务严格只代点「跳过/skip/关闭广告/关闭」类控件，不碰网络层。
 *
 * v3.3.0（本轮五项攻坚 · 真机反馈：喜马拉雅类仍跳不了 / 手机一动就跳 / 弹窗关不掉）：
 * · SDK 广告窗识别（className 指纹）：穿山甲/优量汇/快手/AdMob/自有 ad.activity 等广告
 *   Activity 类名分段精确匹配（.ad.activity / AdActivity / openadsdk / kwad …）→
 *   500ms 内无真跳过键 → 返回键温和直断。解决「文案烧在广告图里、无障碍树零文本」
 *   的图片自绘广告（喜马拉雅无倒计时不带任何可读文本的形态）。
 * · className 指纹学习：直断成功即沉淀广告窗类名 → 下次启动 300ms 快断（零扫描零文本依赖）。
 * · 全屏热区广告识别：启动窗内「≥55% 屏幕面积可点热区 + 节点数≤12 + 无输入框」三条件 →
 *   900ms 无真跳过键 → 直断（图片广告全屏皆是点击热区，正常页面不会）。守护名单（银行/
 *   政务/支付）豁免此启发式，只用高精度信号。
 * · 返回键重试：直断后广告窗仍在（个别广告拦截返回键）→ 窗口期内自动补一次（每窗最多 2 次）。
 * · 弹窗广告屏蔽 v2：弹窗窗期独立于开屏窗（v3.2.0 的弹窗扫描被 12s 开屏窗误拦，
 *   应用用一会儿之后弹出的广告永远关不掉——已修）；Dialog/PopupWindow 窗口事件触发；
 *   角标扩充（广告/推广/赞助/AD/Sponsored/Promoted）；营销话术弱信号（Dialog 形态 +
 *   ≥2 条 CTA + 关闭键）；关闭键不可点时 Dialog 形态返回键兜底；内容突发检测
 *   （1.5s 内 ≥3 次内容变化 → 覆盖层弹窗不触发窗口切换事件的形态）。
 * · 链式跳转回弹扩充：目标名单新增微信/QQ/支付宝/美团/唯品会/苏宁/知乎/小红书/B站/
 *   主流浏览器家族；同包 WebView 落地页（广告判定 3s 内拉起）同样弹回。
 *
 * v3.2.0（沿用）：
 * · 极速链：窗口切换 0ms 即首扫 + 前密后疏重试链 [0,120,240,400,650,1000,…]ms，命中即熔断。
 * · 陀螺仪诱导直断：「翻转/摇一摇」文案 0.8s 内仍无真跳过键 → 返回键温和直断广告窗。
 * · 链式跳转回弹：广告宿主被判定后 6s 内被广告目标抢占前台 → 返回键弹回原应用。
 *
 * 智能学习机制（沿用）：
 * · 冷启动：通用规则（开屏窗内寻找小面积「跳过」控件）+ SDK className 指纹（免学习即命中）。
 * · 观察学习：捕获用户手动点击「跳过」事件，沉淀 text/viewId 专属规则，viewId 定向直达。
 * · 安全阀：控件面积上限（防全屏热区误触）、假跳过甄别、点击冷却、时间窗限制、系统包名过滤。
 */
public class AdSkipService extends AccessibilityService {

    public static volatile AdSkipService instance;

    private static final String PREFS = "adshield";
    private static final String KEY_STATE = "state";

    /* 只识别「跳过 / 关闭广告」类控件 —— 合规边界：代点跳过，不屏蔽广告 */
    private static final String[] SKIP_TEXTS = {"跳过", "关闭广告"};
    /* 陀螺仪 / 摇一摇诱导文案（喜马拉雅实测：翻转手机，跳转详情页或第三方应用） */
    private static final String[] GYRO_HINTS = {"翻转", "摇一摇", "摇晃", "晃动", "摇动", "扭一扭", "转动手机", "倾斜手机", "拿起手机"};
    /* 弹窗广告的关闭键文本（精确匹配，≤6 字） */
    private static final String[] CLOSE_TEXTS =
            {"×", "x", "X", "✕", "✖", "╳", "关闭", "close", "不感兴趣", "我知道了", "知道了", "关闭广告", "跳过", "忽略", "暂不"};
    /* v3.3.0 弹窗广告角标（精确/前缀匹配） */
    private static final String[] BADGE_TEXTS = {"广告", "推广", "赞助", "广告推广", "sponsored", "promoted", "sponsor"};
    /* v3.3.0 营销 CTA 弱信号（Dialog 形态 + ≥2 条 + 关闭键 → 判定弹窗广告） */
    private static final String[] CTA_TEXTS =
            {"立即领取", "点击领取", "领取红包", "限时", "秒杀", "恭喜", "马上抢", "立即下载", "立即抢购",
                    "0元领", "0元", "免费领", "点击进入", "立即查看", "开通会员", "领取礼包", "大奖"};
    /* 常见广告跳转目标（链式回弹只针对这些包：电商 / 商店 / 短视频 / 社交 / 生活 / 浏览器） */
    private static final String[] AD_TARGET_HINTS = {
            // 电商 / 商店
            "com.jingdong", "com.taobao", "com.tmall", "com.xunmeng", "com.achievo.vipshop",
            "com.suning.juniper", "com.dangdang", "com.eastmoney", "com.amazon.mShop",
            "com.android.vending", "com.xiaomi.market", "com.huawei.appmarket",
            "com.heytap.market", "com.bbk.appstore", "com.tencent.android.qqdownloader",
            // 短视频 / 内容
            "com.ss.android.ugc", "com.smile.gifmaker", "com.kuaishou", "tv.danmaku.bili",
            "com.zhihu.android", "com.xingin.xhs", "com.dragon.read",
            // 社交 / 生活（广告高频跳转目标）
            "com.tencent.mm", "com.tencent.mobileqq", "com.eg.android.AlipayGphone",
            "com.sankuai.meituan", "com.dianping.v1", "com.sdu.didi.psnger",
            // 浏览器家族（广告落地页最常见宿主）
            "com.android.browser", "com.android.chrome", "com.sec.android.app.sbrowser",
            "com.UCMobile", "com.quark.browser", "com.tencent.mtt", "org.mozilla.firefox",
            "com.microsoft.emmx", "com.heytap.browser", "com.vivo.browser", "com.mi.globalbrowser"
    };
    /* v3.3.0 同包落地页特征（广告判定后 3s 内拉起的 WebView/H5 页 → 弹回） */
    private static final String[] LANDING_HINTS = {"webview", "h5"};
    /* v3.3.0 守护名单（银行/政务/支付）：热区启发式豁免，只用高精度信号；服务永不干预 */
    private static final String[] GUARD_PKG_HINTS = {
            "bank", "icbc", "cmb", "ccb", "psbc", "abchina", "boc", "unionpay",
            "gov", "alipay", "955", "12345"
    };

    private static final long WINDOW_MS = 12000;       // 开屏时间窗（覆盖延迟跳过键）
    private static final long COOLDOWN_MS = 1200;      // 同一应用两次自动点击的最小间隔
    private static final long LEARN_WINDOW_MS = 30000; // 手动点击学习的宽时间窗
    private static final int MAX_RULES_PER_APP = 8;
    private static final int MAX_LOG = 60;

    /* v3.2.0 极速链：相对窗口起点的扫描节奏（前密后疏，命中即熔断） */
    private static final long[] SCAN_AT =
            {0, 120, 240, 400, 650, 1000, 1500, 2200, 3200, 4500, 6000, 8000, 10500};
    /* 陀螺仪诱导直断时限：识别到翻转文案后，此时长内仍无真跳过键 → 返回键直断 */
    private static final long GYRO_BREAK_MS = 800;
    /* v3.3.0 SDK 广告窗直断时限：className 命中后此时长内仍无真跳过键 → 返回键直断 */
    private static final long CLS_BREAK_MS = 500;
    /* v3.3.0 学习指纹快断时限：className 指纹命中（已沉淀）→ 更快直断 */
    private static final long CLS_LEARNED_BREAK_MS = 300;
    /* v3.3.0 全屏热区直断时限（给真跳过键留出现的余地） */
    private static final long HOT_ZONE_GRACE_MS = 900;
    /* v3.3.0 热区判定：可点热区 ≥55% 屏幕面积 且 窗口节点数 ≤12 且无输入框 */
    private static final double HOT_ZONE_AREA = 0.55;
    private static final int HOT_ZONE_MAX_NODES = 12;
    /* v3.3.0 返回键重试：每个窗口最多直断次数（个别广告拦截第一次返回键） */
    private static final int BACK_RETRY_MAX = 2;
    /* 广告特征有效期：发现跳过键/翻转文案/SDK 广告窗后，链式回弹保护持续此时长 */
    private static final long CHAIN_GUARD_MS = 6000;
    /* 同包落地页弹回：仅限广告判定后此时长内拉起的 WebView */
    private static final long LANDING_GUARD_MS = 3000;
    /* 弹窗广告窗口期与冷却 */
    private static final long POPUP_WINDOW_MS = 10000;
    private static final long POPUP_COOLDOWN_MS = 2500;
    /* v3.3.0 弹窗补扫节流与内容突发检测 */
    private static final long POPUP_SCAN_THROTTLE_MS = 700;
    private static final int BURST_COUNT = 3;
    private static final long BURST_WIN_MS = 1500;

    /* v3.0.0 能耗治理：内存聚合 + 延迟落盘（合并闪存写入） */
    private static final long FLUSH_DELAY_MS = 15000;
    private boolean flushScheduled = false;

    /* v3.0.0/v3.2.0 能耗画像（开机累计，供学习中心「能耗面板」展示） */
    public long energyEvents = 0;   // 处理的无障碍事件数
    public long energyScans = 0;    // 控件树扫描次数
    public long energyBacks = 0;    // 返回键直断/回弹次数
    public long popupBlocks = 0;    // 弹窗广告关闭次数
    public long chainRebounds = 0;  // 链式跳转回弹次数

    private final Object lock = new Object();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.HashMap<String, Long> windowAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastClickAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastPopupAt = new java.util.HashMap<>();
    /* v3.2.0：广告特征有效期（链式回弹依据）与扫描代际（命中即熔断） */
    private final java.util.HashMap<String, Long> adFeatureUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Integer> scanGen = new java.util.HashMap<>();
    /* v3.3.0：窗口类名 / SDK 广告窗有效期 / className 学习快断 / 返回键重试计数 */
    private final java.util.HashMap<String, String> winClass = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> adWinUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Boolean> winClsLearned = new java.util.HashMap<>();
    private final java.util.HashMap<String, Integer> backTries = new java.util.HashMap<>();
    /* v3.3.0：弹窗窗期（独立于开屏窗）/ 弹窗补扫节流 / 内容突发窗口 */
    private final java.util.HashMap<String, Long> popupUntil = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> lastPopupScanAt = new java.util.HashMap<>();
    private final java.util.HashMap<String, long[]> burstWin = new java.util.HashMap<>();
    /* v3.3.0：同包落地页弹回节流 */
    private final java.util.HashMap<String, Long> landingBackAt = new java.util.HashMap<>();

    // 标记自己发起的点击，避免把引擎的点击误学为「用户手动点击」
    private volatile String lastAutoClickPkg;
    private volatile long lastAutoClickAt;

    private int screenWidth;
    private int screenHeight;

    /** 状态快照（含缓存）：Bridge 读 state 时走这里，保证 UI 永远拿到最新内存态 */
    public static String snapshotState(Context ctx) {
        AdSkipService s = instance;
        if (s != null) {
            synchronized (s.lock) {
                try {
                    return s.stateInMemory.toString();
                } catch (Exception ignored) {
                }
            }
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATE, null);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        appendLog("服务已启动 · v3.3.0 SDK广告窗识别 · className指纹学习 · 热区直断 · 弹窗v2 · 回弹扩充");
    }

    @Override
    public void onDestroy() {
        flushNow();
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        if (pkg.equals(getPackageName())) return; // 忽略自身
        if (isSystemNoise(pkg)) return;

        long now = System.currentTimeMillis();
        energyEvents++; // 能耗画像：仅统计有效事件（噪声已前置过滤）

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                String cls = cs(event.getClassName());
                // 链式跳转回弹：广告宿主特征期内被广告目标抢占 → 返回键弹回
                if (reboundChainJump(pkg, now)) break;
                // 同包落地页弹回：广告判定 3s 内拉起内置 WebView/H5 落地页 → 返回键弹回
                if (reboundLanding(pkg, cls, now)) break;
                // 新窗口（应用启动 / Activity 切换 / Dialog / PopupWindow）
                windowAt.put(pkg, now);
                winClass.put(pkg, cls);
                backTries.put(pkg, 0); // 新窗口重置返回键重试计数
                winClsLearned.remove(pkg);
                if (isDialogClass(cls)) {
                    // 弹窗窗期开启：Dialog/PopupWindow 形态（独立于开屏 12s 窗）
                    popupUntil.put(pkg, now + POPUP_WINDOW_MS);
                }
                // v3.3.0 SDK 广告窗识别：className 指纹命中 → 标记广告窗（直断 + 回弹依据）
                if (isAdActivityClass(cls)) {
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    adWinUntil.put(pkg, now + WINDOW_MS);
                    appendLog("🎯 SDK 广告窗识别（" + shortPkg(pkg) + "）：" + cls
                            + " → 500ms 内无真跳过键将温和直断（className 指纹，不依赖任何文本）");
                } else if (learnedClassHit(pkg, cls)) {
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    adWinUntil.put(pkg, now + WINDOW_MS);
                    winClsLearned.put(pkg, Boolean.TRUE); // 学习指纹：300ms 快断
                }
                bumpScanChain(pkg);
                break;
            }
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                // 开屏窗内：界面内容变化（延迟出现的跳过按钮等）→ 低摩擦补扫
                if (now - winAt(pkg) < WINDOW_MS) {
                    nudgeScan(pkg, now);
                    break;
                }
                // v3.3.0 弹窗窗期内（Dialog/PopupWindow/已确认弹窗）→ 节流补扫
                if (now < popupUntilGet(pkg)) {
                    nudgePopupScan(pkg, now);
                    break;
                }
                // v3.3.0 内容突发检测：覆盖层弹窗（FrameLayout 叠加）不触发窗口切换事件，
                // 表现为静默窗口期的密集内容变化 → 1.5s 内 ≥3 次 → 开弹窗窗期补扫一次
                detectPopupBurst(pkg, now);
                break;
            }
            case AccessibilityEvent.TYPE_VIEW_CLICKED: {
                // 智能学习：观察用户手动点了什么
                if (isOwnAutoClick(pkg, now)) return;
                learnFromManualClick(pkg, event, now);
                observeMisclick(pkg, event, now); // 误触观察（只记录不拦截）
                break;
            }
        }
    }

    /* ───────── 基础判断 ───────── */

    private boolean isSystemNoise(String pkg) {
        return pkg.startsWith("com.android.systemui")
                || pkg.equals("android")
                || pkg.contains("inputmethod")
                || pkg.contains("packageinstaller")
                || pkg.equals("com.android.settings");
    }

    private long winAt(String pkg) {
        Long t = windowAt.get(pkg);
        return t == null ? 0L : t;
    }

    private long popupUntilGet(String pkg) {
        Long t = popupUntil.get(pkg);
        return t == null ? 0L : t;
    }

    private boolean isOwnAutoClick(String pkg, long now) {
        return pkg.equals(lastAutoClickPkg) && (now - lastAutoClickAt) < 900;
    }

    /** v3.3.0 守护名单（银行/政务/支付）：只影响热区启发式；文本/className 高精度信号照常 */
    private boolean isGuardPkg(String pkg) {
        String p = pkg.toLowerCase();
        for (String k : GUARD_PKG_HINTS) {
            if (p.contains(k)) return true;
        }
        return false;
    }

    /* ───────── v3.3.0 SDK 广告窗识别（className 指纹） ───────── */

    /**
     * 广告 Activity 类名识别：按 . 与 $ 分段后精确/特征匹配，
     * 规避 DownloadActivity 之类的前缀误命中（分段相等而非子串）。
     * 覆盖：穿山甲 stub、优量汇、快手、AdMob、Mintegral、Tanx、百度，
     * 及宿主自有广告模块（.ad.activity / AdActivity / AdvertActivity / SplashAd…）。
     */
    private boolean isAdActivityClass(String cls) {
        if (cls == null || cls.length() < 6) return false;
        String c = cls.toLowerCase();
        if (c.contains("qq.e.ads") || c.contains("gms.ads") || c.contains("bytedance.sdk")
                || c.contains(".ad.activity") || c.contains(".ads.activity")
                || c.contains("baidu.mobads") || c.contains("csjad") || c.contains("unionad")) return true;
        String[] seg = c.split("[.$]");
        for (String s : seg) {
            if (s.length() < 3) continue;
            if (s.equals("adactivity") || s.equals("advertactivity")) return true;
            if (s.contains("openadsdk") || s.contains("adsdk") || s.contains("kwad")
                    || s.contains("gdtadv") || s.contains("admob") || s.contains("sigmob")
                    || s.contains("adcolony") || s.contains("applovin") || s.contains("vungle")
                    || s.contains("mbridge") || s.contains("tanx") || s.contains("mobads")
                    || s.contains("splashad") || s.contains("adsplash") || s.contains("landingpage")
                    || s.contains("adpage") || s.contains("adlanding")) return true;
        }
        return false;
    }

    /** Dialog / PopupWindow 窗口形态 */
    private boolean isDialogClass(String cls) {
        if (cls == null) return false;
        String c = cls.toLowerCase();
        return c.contains("dialog") || c.contains("popup");
    }

    /** 学习到的 className 指纹命中（首启直断成功后沉淀） */
    private boolean learnedClassHit(String pkg, String cls) {
        if (cls == null || cls.length() == 0) return false;
        try {
            JSONObject rules = loadState().optJSONObject("rules");
            if (rules == null) return false;
            JSONArray list = rules.optJSONArray(pkg);
            if (list == null) return false;
            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r != null && cls.equals(r.optString("cls", ""))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** className 指纹学习：直断成功后沉淀（不依赖 text/viewId，纯窗口形态指纹） */
    private void learnClass(String pkg, String cls) {
        if (cls == null || cls.length() == 0) return;
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) {
                    rules = new JSONObject();
                    st.put("rules", rules);
                }
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) {
                    list = new JSONArray();
                    rules.put(pkg, list);
                }
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r != null && cls.equals(r.optString("cls", ""))) {
                        r.put("auto", r.optInt("auto", 0) + 1);
                        r.put("ts", System.currentTimeMillis());
                        saveState(st);
                        return;
                    }
                }
                if (list.length() >= MAX_RULES_PER_APP) return;
                JSONObject r = new JSONObject();
                r.put("text", "");
                r.put("viewId", "");
                r.put("cls", cls);
                r.put("manual", 0);
                r.put("auto", 1);
                r.put("ts", System.currentTimeMillis());
                list.put(r);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    /* ───────── v3.2.0 扫描调度：0ms 首扫 + 前密后疏重试链，命中即熔断 ───────── */

    /** 新窗口：代际 +1 熔断旧链，按节奏表调度整条重试链（0ms 起步） */
    private void bumpScanChain(final String pkg) {
        final long winStart = winAt(pkg);
        Integer g = scanGen.get(pkg);
        final int gen = g == null ? 1 : g + 1;
        scanGen.put(pkg, gen);
        for (int i = 0; i < SCAN_AT.length; i++) {
            final long at = SCAN_AT[i];
            // lambda 由 d8 脱糖（与 v3.0.0 同路径，已验证）
            handler.postDelayed(() -> {
                Integer cur = scanGen.get(pkg);
                if (cur == null || cur.intValue() != gen) return; // 旧链已熔断
                tryScan(pkg, winStart, at);
            }, at);
        }
    }

    /** 开屏窗内内容变化补扫：距窗口起点 150ms 以上才允许（与重试链错峰，防事件洪流） */
    private void nudgeScan(final String pkg, long now) {
        Integer gen = scanGen.get(pkg);
        if (gen == null) {
            bumpScanChain(pkg);
            return;
        }
        final long winStart = winAt(pkg);
        if (now - winStart < 150) return;
        final int g = gen.intValue();
        handler.post(() -> {
            Integer cur = scanGen.get(pkg);
            if (cur == null || cur.intValue() != g) return;
            tryScan(pkg, winStart, -1); // -1 = 补扫（受全局冷却约束）
        });
    }

    /** v3.3.0 弹窗窗期补扫：节流 700ms（弹窗渲染的密集内容变化不再逐条触发扫描） */
    private void nudgePopupScan(final String pkg, long now) {
        Long last = lastPopupScanAt.get(pkg);
        if (last != null && now - last < POPUP_SCAN_THROTTLE_MS) return;
        lastPopupScanAt.put(pkg, now);
        final long winStart = winAt(pkg);
        final int[] g = {scanGen.get(pkg) == null ? 0 : scanGen.get(pkg).intValue()};
        handler.post(() -> {
            if (instance == null) return;
            tryScan(pkg, winStart, -2); // -2 = 弹窗补扫
        });
    }

    /** v3.3.0 内容突发检测：静默窗口期 1.5s 内 ≥3 次内容变化 → 开启弹窗窗期并补扫一次 */
    private void detectPopupBurst(String pkg, long now) {
        if (!popupShieldEnabled()) return;
        long[] w = burstWin.get(pkg);
        if (w == null || now - w[0] > BURST_WIN_MS) {
            burstWin.put(pkg, new long[]{now, 1});
            return;
        }
        w[1]++;
        if (w[1] >= BURST_COUNT) {
            burstWin.remove(pkg);
            popupUntil.put(pkg, now + POPUP_WINDOW_MS);
            appendLog("🪟 检测到内容突发（" + shortPkg(pkg) + "）：疑似覆盖层弹窗 → 开启弹窗识别窗补扫");
            nudgePopupScan(pkg, now);
        }
    }

    /** 命中即熔断：清掉该应用未执行的扫描（代际 +1） */
    private void fuseChain(String pkg) {
        Integer g = scanGen.get(pkg);
        scanGen.put(pkg, g == null ? 1 : g + 1);
    }

    /* ───────── 扫描与决策 ───────── */

    private void tryScan(String pkg, long winStart, long atMs) {
        if (instance == null) return;
        long now = System.currentTimeMillis();
        long elapsed = now - winStart;
        /* v3.3.0：开屏窗与弹窗窗两条独立通道——v3.2.0 把弹窗扫描锁在 12s 开屏窗内，
           应用使用一段时间后弹出的广告永远关不掉；现已解耦 */
        boolean splashLive = learningEnabled() && elapsed <= WINDOW_MS;
        boolean popupLive = popupShieldEnabled() && now < popupUntilGet(pkg) && popupCooldownOK(pkg, now);
        if (!splashLive && !popupLive) return;
        energyScans++; // 能耗画像：每次控件树扫描计数

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            CharSequence rp = root.getPackageName();
            if (rp == null || !pkg.contentEquals(rp)) return; // 只处理当前活动窗口

            /* ① 学习规则优先（viewId 定向直达，零全树扫描；仅开屏窗内） */
            Target t = null;
            ScanFind f = null;
            if (splashLive) {
                t = findLearnedTarget(root, pkg);
                if (t == null) {
                    f = scanCollect(root);
                    t = f.skip;
                }
            }

            boolean adFeature = f != null && (f.gyroHint || f.fakeSkip);
            if (t != null || adFeature) {
                // 广告特征有效期：此窗口被判定为广告（链式回弹依据）
                Long old = adFeatureUntil.get(pkg);
                if (old == null || now > old) adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
            }

            /* ③ 命中真跳过键 → 代点，熔断扫描链（受点击冷却约束） */
            Long lastClick = lastClickAt.get(pkg);
            boolean clickCoolOK = lastClick == null || now - lastClick >= COOLDOWN_MS;
            if (t != null && clickCoolOK && click(t.node)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS); // 跳转可能已在途，保守回弹保护
                bumpAuto(pkg, t);
                fuseChain(pkg);
                return;
            }

            /* 弹窗窗期但无跳过目标 → 需要一次多信号扫描（角标/关闭键/CTA/Dialog 形态） */
            if (f == null && t == null && popupLive) {
                f = scanCollect(root);
            }
            if (f == null) return;

            /* ── 开屏窗内：三类直断（SDK 广告窗 / 全屏热区 / 陀螺仪·假跳过） ── */
            if (splashLive) {
                /* ④ SDK 广告窗直断：className 指纹命中（穿山甲/优量汇/快手/宿主 ad.activity…），
                     不依赖任何文本——文案烧在广告图里的形态在此命中 */
                if (now < adWinUntilGet(pkg) && elapsed >= clsBreakMs(pkg) && backOk(pkg, now)) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    energyBacks++;
                    backTries.put(pkg, triesOf(pkg) + 1);
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    learnClass(pkg, winClassOf(pkg)); // 沉淀窗口类名指纹 → 下次 300ms 快断
                    appendLog("🛡 SDK 广告窗已直断（" + shortPkg(pkg) + "）：" + winClassOf(pkg)
                            + " · " + elapsed + "ms 无真跳过键 → 返回键温和关闭（不碰进程与服务）");
                    bumpStat(pkg, "back");
                    return; // 不熔断：剩余扫描继续观察，广告窗若拦截了返回键会再补一次
                }

                /* ⑤ 全屏热区直断：≥55% 屏幕可点热区 + 节点数≤12 + 无输入框 → 图片广告。
                     守护名单（银行/政务/支付）豁免此启发式，只用高精度信号 */
                if (f.hotZone && !f.hasEditText && f.nodeCount <= HOT_ZONE_MAX_NODES
                        && !isGuardPkg(pkg) && elapsed >= HOT_ZONE_GRACE_MS && backOk(pkg, now)) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    energyBacks++;
                    backTries.put(pkg, triesOf(pkg) + 1);
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    appendLog("🛡 全屏热区广告已直断（" + shortPkg(pkg) + "）：可点热区占屏 "
                            + Math.round(HOT_ZONE_AREA * 100) + "% · 节点 " + f.nodeCount
                            + " · " + elapsed + "ms 无真跳过键 → 返回键温和关闭（零文本依赖）");
                    bumpStat(pkg, "back");
                    return;
                }

                /* ⑥ 陀螺仪诱导直断：识别到「翻转/摇一摇」文案或假跳过热区，
                     GYRO_BREAK_MS 内仍无真跳过键 → 返回键温和直断广告窗 */
                if ((f.gyroHint || f.fakeSkip) && elapsed >= GYRO_BREAK_MS && backOk(pkg, now)) {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    energyBacks++;
                    backTries.put(pkg, triesOf(pkg) + 1);
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                    appendLog("🛡 陀螺仪诱导广告已直断（" + shortPkg(pkg) + "）："
                            + (f.gyroHint ? "检测到「翻转手机/摇一摇」文案" : "检测到假跳过热区")
                            + " · " + elapsed + "ms 无真跳过键 → 返回键温和关闭广告窗"
                            + (triesOf(pkg) >= BACK_RETRY_MAX ? "（重试已达上限）" : ""));
                    bumpStat(pkg, "back");
                    return;
                }
            }

            /* ⑦ 应用内弹窗广告 v2：角标强信号 / Dialog 形态+营销话术弱信号 → 代点关闭 */
            if (popupLive && f.close != null) {
                boolean strongBadge = f.adBadge;                          // 「广告/推广/赞助」角标
                boolean weakCombo = f.dialogForm && f.ctaCount >= 2;      // Dialog + ≥2 条 CTA + 关闭键
                if (strongBadge || weakCombo) {
                    if (click(f.close.node)) {
                        lastClickAt.put(pkg, now);
                        lastAutoClickPkg = pkg;
                        lastAutoClickAt = now;
                        popupBlocks++;
                        lastPopupAt.put(pkg, now);
                        appendLog("🪟 应用内弹窗广告已关闭（" + shortPkg(pkg) + "）："
                                + (strongBadge ? "识别「" + f.badgeLabel + "」角标" : "Dialog 形态 + " + f.ctaCount + " 条营销话术")
                                + " + 关闭键「" + f.close.label + "」→ 已代点");
                        bumpStat(pkg, "popup");
                    } else if (f.dialogForm && f.adBadge && backOk(pkg, now)) {
                        // 关闭键不可点（自绘 dispatchTouchEvent）：Dialog 形态返回键即取消
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        energyBacks++;
                        popupBlocks++;
                        lastPopupAt.put(pkg, now);
                        appendLog("🪟 弹窗广告关闭键不可点 → Dialog 形态返回键取消（" + shortPkg(pkg) + "）");
                        bumpStat(pkg, "popup");
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** SDK 广告窗直断时限：学习指纹 300ms（已知形态）/ 首次命中 500ms */
    private long clsBreakMs(String pkg) {
        Boolean learned = winClsLearned.get(pkg);
        return (learned != null && learned.booleanValue()) ? CLS_LEARNED_BREAK_MS : CLS_BREAK_MS;
    }

    private long adWinUntilGet(String pkg) {
        Long t = adWinUntil.get(pkg);
        return t == null ? 0L : t;
    }

    private String winClassOf(String pkg) {
        String c = winClass.get(pkg);
        return c == null ? "" : c;
    }

    private int triesOf(String pkg) {
        Integer n = backTries.get(pkg);
        return n == null ? 0 : n.intValue();
    }

    /** 返回键节流：全局 600ms 最小间隔 + 每窗口最多 2 次（重试兜底：个别广告拦截第一次返回键） */
    private boolean backOk(String pkg, long now) {
        if (triesOf(pkg) >= BACK_RETRY_MAX) return false;
        return backAllowed(now);
    }

    /** 返回键全局节流：600ms 内最多一次（直断与回弹共用） */
    private long lastBackAt = 0;

    private boolean backAllowed(long now) {
        if (now - lastBackAt < 600) return false;
        lastBackAt = now;
        return true;
    }

    private boolean popupCooldownOK(String pkg, long now) {
        Long t = lastPopupAt.get(pkg);
        return t == null || now - t >= POPUP_COOLDOWN_MS;
    }

    private boolean click(AccessibilityNodeInfo node) {
        try {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Exception e) {
            return false;
        }
    }

    private static class Target {
        AccessibilityNodeInfo node;
        String how;    // 命中方式（学习规则 / 通用规则）
        String label;  // 被点击控件文本
        String ruleId; // 命中的学习规则 viewId（可为空）

        Target(AccessibilityNodeInfo n, String how, String label, String ruleId) {
            this.node = n;
            this.how = how;
            this.label = label;
            this.ruleId = ruleId;
        }
    }

    /** 一次 DFS 的多信号收集结果 */
    private static class ScanFind {
        Target skip;       // 真「跳过」控件（小面积 + 可点，已甄别）
        boolean fakeSkip;  // 疑似假跳过热区（「跳过」字样但大面积/不可点）
        boolean gyroHint;  // 「翻转手机 / 摇一摇」诱导文案
        Target close;      // 弹窗关闭键（×/关闭/不感兴趣/我知道了…）
        boolean adBadge;   // 「广告/推广/赞助」角标（精确匹配）
        String badgeLabel; // v3.3.0 命中的角标文本（日志用）
        // v3.3.0 新信号
        boolean hotZone;     // 全屏可点热区（≥55% 屏幕面积）—— 图片广告特征
        int nodeCount;       // 窗口节点数（热区判定的低节点条件）
        int ctaCount;        // 营销 CTA 命中数（弹窗广告弱信号）
        boolean dialogForm;  // 根节点为 Dialog/PopupWindow 形态
        boolean hasEditText; // 存在输入框（正常业务弹窗特征 → 禁用热区直断）
    }

    /** 优先级：① 已学习规则（viewId 精确命中）→ ② 已学习规则（文本） */
    private Target findLearnedTarget(AccessibilityNodeInfo root, String pkg) {
        JSONObject rules = null;
        try {
            rules = loadState().optJSONObject("rules");
        } catch (Exception ignored) {
        }
        if (rules == null) return null;
        JSONArray list = rules.optJSONArray(pkg);
        if (list == null) return null;
        // ① viewId 精确查找（最稳，不受文案改版影响）
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r == null) continue;
            String vid = r.optString("viewId", "");
            if (vid.length() == 0) continue;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(vid);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    // 学习规则可信度高：面积放宽至 45%（用户亲手点过的控件）
                    if (isSafeClickable(n, 0.45, 0.60)) {
                        return new Target(n, "学习规则·控件ID", vid, vid);
                    }
                }
            }
        }
        // ② 学习到的文本按钮
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r == null) continue;
            String txt = r.optString("text", "");
            if (txt.length() == 0) continue;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(txt);
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    if (isSafeClickable(n, 0.45, 0.60)) {
                        return new Target(n, "学习规则·文本「" + txt + "」", txt, r.optString("viewId", ""));
                    }
                }
            }
        }
        return null;
    }

    /**
     * 通用多信号扫描（DFS，预算 280 节点）：
     * 同一次遍历收集 跳过键 / 假跳过热区 / 翻转文案 / 广告角标 / 关闭键 / 全屏热区 /
     * 节点计数 / 营销话术 / 输入框存在性 —— 比逐项单独遍历省电。
     */
    private ScanFind scanCollect(AccessibilityNodeInfo root) {
        ScanFind f = new ScanFind();
        f.dialogForm = isDialogClass(cs(root.getClassName()));
        int[] budget = {280};
        collect(root, 0, budget, f);
        return f;
    }

    private void collect(AccessibilityNodeInfo node, int depth, int[] budget, ScanFind f) {
        if (node == null || depth > 32 || budget[0] <= 0) return;
        budget[0]--;
        f.nodeCount++;
        try {
            String text = cs(node.getText());
            String desc = cs(node.getContentDescription());
            String label = text.length() > 0 ? text : desc;
            String cls = cs(node.getClassName()).toLowerCase();
            if (cls.contains("edittext")) f.hasEditText = true;

            // v3.3.0 全屏热区检测：可点且 ≥55% 屏幕面积（图片广告整图皆可点击）
            if (!f.hotZone && node.isClickable() && screenWidth > 0 && isHugeArea(node)) {
                f.hotZone = true;
            }

            if (label.length() > 0) {
                if (label.length() <= 14 && matchesSkip(label)) {
                    // 「跳过」字样：小面积可点 = 真跳过键；大面积/不可点 = 图内假跳过热区
                    AccessibilityNodeInfo clickable = resolveClickable(node, 4);
                    if (clickable != null && isSmallArea(clickable)) {
                        if (f.skip == null) {
                            f.skip = new Target(clickable, "通用规则·文本「" + label + "」", label, "");
                        }
                    } else {
                        f.fakeSkip = true; // 假跳过陷阱（喜马拉雅类）
                    }
                } else if (label.length() <= 22 && matchesGyro(label)) {
                    f.gyroHint = true; // 翻转手机 / 摇一摇诱导文案
                } else if (label.length() <= 6 && isCloseText(label)) {
                    AccessibilityNodeInfo clickable = resolveClickable(node, 4);
                    if (clickable != null && isSmallArea(clickable) && f.close == null) {
                        f.close = new Target(clickable, "关闭键·「" + label + "」", label, "");
                    }
                } else if (isAdBadge(label)) {
                    f.adBadge = true; // 「广告/推广/赞助」角标
                    f.badgeLabel = label;
                } else if (label.length() <= 14 && matchesCta(label)) {
                    f.ctaCount++; // 营销 CTA 弱信号（弹窗广告）
                }
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                collect(node.getChild(i), depth + 1, budget, f);
            }
        } catch (Exception ignored) {
        }
    }

    /** v3.3.0 热区判定：控件面积 ≥55% 屏幕（图片广告整图点击热区） */
    private boolean isHugeArea(AccessibilityNodeInfo node) {
        try {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.isEmpty()) return false;
            double area = (double) r.width() * r.height();
            double screen = (double) screenWidth * screenHeight;
            return area / screen >= HOT_ZONE_AREA;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesSkip(String s) {
        String lower = s.toLowerCase();
        if (lower.contains("skip") || lower.contains("跳过")) return true;
        for (String k : SKIP_TEXTS) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    private boolean matchesGyro(String s) {
        for (String k : GYRO_HINTS) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    private boolean isCloseText(String s) {
        String t = s.trim();
        String lower = t.toLowerCase();
        for (String k : CLOSE_TEXTS) {
            if (t.equals(k) || lower.equals(k)) return true;
        }
        return false;
    }

    /** v3.3.0 营销 CTA 匹配（弹窗广告弱信号） */
    private boolean matchesCta(String s) {
        for (String k : CTA_TEXTS) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    /** 「广告」角标：精确短文本（防把正文里出现「广告」二字的文章当角标） */
    private boolean isAdBadge(String label) {
        String t = label.trim();
        String lower = t.toLowerCase();
        if (t.equals("广告") || t.equals("推广") || t.equals("赞助") || t.equals("广告推广")) return true;
        if (lower.equals("ad") || lower.equals("sponsored") || lower.equals("promoted")) return true;
        return t.length() <= 8 && (t.startsWith("广告 ") || t.startsWith("广告·") || t.endsWith(" 广告"));
    }

    /** 向上找可点击的祖先（最多 4 层）—— 跳过文字本身不可点时，其容器往往可点 */
    private AccessibilityNodeInfo resolveClickable(AccessibilityNodeInfo node, int maxUp) {
        AccessibilityNodeInfo cur = node;
        for (int i = 0; i < maxUp && cur != null; i++) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    /**
     * 面积安全阀：只点击小控件（默认 < 屏幕面积 35% 且宽度 < 屏宽 55%）。
     * 防全屏热区 / 摇一摇类广告误触 —— 引擎永远不点屏幕中央大区域。
     */
    private boolean isSmallArea(AccessibilityNodeInfo node) {
        return isSafeClickable(node, 0.35, 0.55);
    }

    private boolean isSafeClickable(AccessibilityNodeInfo n, double areaRatio, double widthRatio) {
        if (n == null || !n.isClickable()) return false;
        try {
            if (screenWidth <= 0 || screenHeight <= 0) return true;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (r.isEmpty()) return false;
            double area = (double) r.width() * r.height();
            double screen = (double) screenWidth * screenHeight;
            return area / screen < areaRatio && r.width() < screenWidth * widthRatio;
        } catch (Exception e) {
            return false;
        }
    }

    /* ───────── 链式跳转回弹（跨应用） ───────── */

    /**
     * 广告宿主 A 的广告特征期内（发现跳过键/翻转文案/SDK 广告窗后 6s），被已知广告跳转目标 B
     * （电商/商店/短视频/社交/浏览器…）抢占前台 → GLOBAL_ACTION_BACK 弹回 A。
     * 无障碍事件均在主线程串行到达，遍历内存 Map 安全。
     */
    private boolean reboundChainJump(String newPkg, long now) {
        try {
            if (!chainReboundEnabled()) return false;
            if (isSystemNoise(newPkg) || isLauncherLike(newPkg)) return false;
            if (!isAdTarget(newPkg)) return false; // 只弹已知广告跳转目标，正常切换零打扰

            String host = null;
            for (Map.Entry<String, Long> e : adFeatureUntil.entrySet()) {
                String k = e.getKey();
                if (k.equals(newPkg)) continue;
                if (now < e.getValue().longValue()) {
                    // 宿主开窗太久 → 用户多半在正常使用，不弹
                    if (now - winAt(k) <= WINDOW_MS + CHAIN_GUARD_MS) {
                        host = k;
                        break;
                    }
                }
            }
            if (host == null) return false;
            if (!backAllowed(now)) return false;

            performGlobalAction(GLOBAL_ACTION_BACK);
            chainRebounds++;
            appendLog("⛓ 链式跳转已弹回：" + shortPkg(host) + " 的广告试图拉起 " + shortPkg(newPkg)
                    + " → 返回键送回原应用（" + (now - winAt(host)) + "ms 内）");
            bumpStat(host, "chain");
            // 弹回后宿主广告窗可能仍在：续一条短链继续处理
            windowAt.put(host, now);
            bumpScanChain(host);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isAdTarget(String pkg) {
        for (String h : AD_TARGET_HINTS) {
            if (pkg.startsWith(h) || pkg.contains(h)) return true;
        }
        return false;
    }

    /* ───────── v3.3.0 同包落地页弹回 ───────── */

    /**
     * 广告判定后 3s 内，同包切换到 WebView/H5 Activity（广告落地页，无 Scheme 事件）→
     * 返回键弹回一次。倒计时归零自动跳、点击热区误触均覆盖。
     */
    private boolean reboundLanding(String pkg, String cls, long now) {
        try {
            if (!chainReboundEnabled()) return false;
            if (cls == null || cls.length() == 0) return false;
            String c = cls.toLowerCase();
            boolean landing = false;
            for (String h : LANDING_HINTS) {
                if (c.contains(h)) {
                    landing = true;
                    break;
                }
            }
            if (!landing) return false;
            Long until = adFeatureUntil.get(pkg);
            if (until == null || now >= until.longValue()) return false;
            long judgedAt = until.longValue() - CHAIN_GUARD_MS;
            if (now - judgedAt > LANDING_GUARD_MS) return false; // 判定 3s 后才开网页 → 用户主动行为，不弹
            Long last = landingBackAt.get(pkg);
            if (last != null && now - last.longValue() < CHAIN_GUARD_MS) return false;
            if (!backAllowed(now)) return false;

            landingBackAt.put(pkg, now);
            performGlobalAction(GLOBAL_ACTION_BACK);
            chainRebounds++;
            appendLog("⛓ 广告落地页已弹回（" + shortPkg(pkg) + "）：广告拉起内置 WebView 落地页 → 返回键送回");
            bumpStat(pkg, "chain");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLauncherLike(String pkg) {
        return pkg.contains("launcher") || pkg.contains("home")
                || pkg.equals("com.miui.home") || pkg.equals("com.huawei.android.launcher");
    }

    /* ───────── 智能学习：观察手动点击 ───────── */

    private void learnFromManualClick(String pkg, AccessibilityEvent event, long now) {
        if (now - winAt(pkg) > LEARN_WINDOW_MS) return; // 只学习窗口切换附近的点击

        String text = "";
        String viewId = "";
        try {
            StringBuilder sb = new StringBuilder();
            if (event.getText() != null) {
                for (CharSequence c : event.getText()) {
                    if (c != null) sb.append(c);
                }
            }
            text = sb.toString().trim();
            AccessibilityNodeInfo src = event.getSource();
            if (src != null) {
                if (text.length() == 0) text = cs(src.getContentDescription());
                viewId = cs(src.getViewIdResourceName());
            }
        } catch (Exception ignored) {
        }
        if (!matchesSkip(text) && !isCloseText(text)) return; // 学习「跳过 / 关闭」类点击

        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) {
                    rules = new JSONObject();
                    st.put("rules", rules);
                }
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) {
                    list = new JSONArray();
                    rules.put(pkg, list);
                }
                boolean merged = false;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r == null) continue;
                    boolean sameId = viewId.length() > 0 && viewId.equals(r.optString("viewId", ""));
                    boolean sameText = text.length() > 0 && text.equals(r.optString("text", ""));
                    if (sameId || sameText) {
                        r.put("manual", r.optInt("manual", 0) + 1);
                        r.put("ts", now);
                        if (r.optString("viewId", "").length() == 0 && viewId.length() > 0) {
                            r.put("viewId", viewId);
                        }
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    if (list.length() >= MAX_RULES_PER_APP) return;
                    JSONObject r = new JSONObject();
                    r.put("text", text);
                    r.put("viewId", viewId);
                    r.put("manual", 1);
                    r.put("auto", 0);
                    r.put("ts", now);
                    list.put(r);
                }
                bumpManualStat(st, pkg);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
        appendLog("已学习：" + shortPkg(pkg) + " 中你手动点击了「" + text + "」→ 下次将自动点击");
    }

    /* ───────── 误触观察：区分「广告诱导跳转」与「用户主动操作」 ───────── */

    private void observeMisclick(String pkg, AccessibilityEvent event, long now) {
        try {
            if (screenWidth <= 0 || screenHeight <= 0) return;
            if (now - winAt(pkg) > WINDOW_MS) return;
            AccessibilityNodeInfo src = event.getSource();
            if (src == null) return;
            String label = cs(src.getText());
            if (label.length() == 0) label = cs(src.getContentDescription());
            if (matchesSkip(label) || isCloseText(label)) return;
            Rect r = new Rect();
            src.getBoundsInScreen(r);
            if (r.isEmpty()) return;
            double area = (double) r.width() * r.height();
            double screen = (double) screenWidth * screenHeight;
            boolean huge = (area / screen > 0.35) && (r.width() > screenWidth * 0.55);
            boolean tooFast = (now - winAt(pkg)) < 700;
            if (!huge && !tooFast) return;
            synchronized (lock) {
                JSONObject st = loadState();
                JSONObject stats = st.optJSONObject("stats");
                if (stats == null) { stats = new JSONObject(); st.put("stats", stats); }
                stats.put("misclick", stats.optInt("misclick", 0) + 1);
                saveState(st);
            }
            appendLog("⚠️ 疑似误触（" + shortPkg(pkg) + "）："
                    + (huge ? "全屏热区控件，轻微触碰即跳转" : "窗口切换后 " + (now - winAt(pkg)) + "ms 内点击，快于人类反应")
                    + " → 已记入观察统计");
        } catch (Exception ignored) {
        }
    }

    /* ───────── 开关 ───────── */

    private boolean learningEnabled() {
        try {
            JSONObject st = loadState();
            return !st.has("learning") || st.optBoolean("learning", true);
        } catch (Exception e) {
            return true;
        }
    }

    /** 应用内弹窗广告屏蔽（默认开） */
    private boolean popupShieldEnabled() {
        try {
            JSONObject st = loadState();
            return !st.has("popupShield") || st.optBoolean("popupShield", true);
        } catch (Exception e) {
            return true;
        }
    }

    /** 链式跳转回弹（默认开） */
    private boolean chainReboundEnabled() {
        try {
            JSONObject st = loadState();
            return !st.has("chainRebound") || st.optBoolean("chainRebound", true);
        } catch (Exception e) {
            return true;
        }
    }

    /* ───────── 统计与日志（JSON 持久化，页面通过 JSBridge 读取） ───────── */

    private void bumpManualStat(JSONObject st, String pkg) {
        try {
            JSONObject stats = st.optJSONObject("stats");
            if (stats == null) {
                stats = new JSONObject();
                st.put("stats", stats);
            }
            stats.put("manual", stats.optInt("manual", 0) + 1);
            JSONObject byApp = stats.optJSONObject("byApp");
            if (byApp == null) {
                byApp = new JSONObject();
                stats.put("byApp", byApp);
            }
            JSONObject a = byApp.optJSONObject(pkg);
            if (a == null) {
                a = new JSONObject();
                byApp.put(pkg, a);
            }
            a.put("manual", a.optInt("manual", 0) + 1);
        } catch (Exception ignored) {
        }
    }

    /** 通用统计：kind = auto / back / popup / chain */
    private void bumpStat(String pkg, String kind) {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject stats = st.optJSONObject("stats");
                if (stats == null) {
                    stats = new JSONObject();
                    st.put("stats", stats);
                }
                stats.put(kind, stats.optInt(kind, 0) + 1);
                JSONObject byApp = stats.optJSONObject("byApp");
                if (byApp == null) {
                    byApp = new JSONObject();
                    stats.put("byApp", byApp);
                }
                JSONObject a = byApp.optJSONObject(pkg);
                if (a == null) {
                    a = new JSONObject();
                    byApp.put(pkg, a);
                }
                a.put(kind, a.optInt(kind, 0) + 1);
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    private void bumpAuto(String pkg, Target t) {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONObject stats = st.optJSONObject("stats");
                if (stats == null) {
                    stats = new JSONObject();
                    st.put("stats", stats);
                }
                stats.put("auto", stats.optInt("auto", 0) + 1);
                JSONObject byApp = stats.optJSONObject("byApp");
                if (byApp == null) {
                    byApp = new JSONObject();
                    stats.put("byApp", byApp);
                }
                JSONObject a = byApp.optJSONObject(pkg);
                if (a == null) {
                    a = new JSONObject();
                    byApp.put(pkg, a);
                }
                a.put("auto", a.optInt("auto", 0) + 1);
                if (t.ruleId != null && t.ruleId.length() > 0) {
                    JSONObject rules = st.optJSONObject("rules");
                    JSONArray list = rules == null ? null : rules.optJSONArray(pkg);
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject r = list.optJSONObject(i);
                            if (r != null && t.ruleId.equals(r.optString("viewId", ""))) {
                                r.put("auto", r.optInt("auto", 0) + 1);
                                break;
                            }
                        }
                    }
                }
                saveState(st);
            } catch (Exception ignored) {
            }
        }
        appendLog("⚡ 已自动点击 " + shortPkg(pkg) + " 的「" + t.label + "」（" + t.how + "）");
    }

    private void appendLog(String msg) {
        synchronized (lock) {
            try {
                JSONObject st = loadState();
                JSONArray log = st.optJSONArray("log");
                if (log == null) {
                    log = new JSONArray();
                    st.put("log", log);
                }
                JSONObject entry = new JSONObject();
                entry.put("t", System.currentTimeMillis());
                entry.put("msg", msg);
                log.put(entry);
                while (log.length() > MAX_LOG) {
                    log.remove(0);
                }
                saveState(st);
            } catch (Exception ignored) {
            }
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /* ───────── 内存聚合 + 延迟落盘（能耗治理） ─────────
       内存态 + 15s 合并落盘，onDestroy / UI 快照读取时立即 flush。 */

    private org.json.JSONObject stateInMemory;

    private org.json.JSONObject loadState() {
        synchronized (lock) {
            if (stateInMemory != null) return stateInMemory;
        }
        try {
            String raw = prefs().getString(KEY_STATE, null);
            stateInMemory = raw != null ? new org.json.JSONObject(raw) : new org.json.JSONObject();
        } catch (Exception e) {
            stateInMemory = new org.json.JSONObject();
        }
        return stateInMemory;
    }

    /** 只改内存 + 调度合并落盘（15s 内多次写合并为一次闪存写入） */
    private void saveState(org.json.JSONObject st) {
        stateInMemory = st;
        scheduleFlush();
    }

    private void scheduleFlush() {
        synchronized (lock) {
            if (flushScheduled) return;
            flushScheduled = true;
        }
        // lambda 由 d8 脱糖（与 v3.0.0 同路径，已验证）
        handler.postDelayed(() -> flushNow(), FLUSH_DELAY_MS);
    }

    /** 立即落盘（幂等；onDestroy 与 UI 读取前调用） */
    public void flushNow() {
        synchronized (lock) {
            flushScheduled = false;
        }
        org.json.JSONObject st = stateInMemory;
        if (st == null) return;
        try {
            prefs().edit().putString(KEY_STATE, st.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private String cs(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    private String shortPkg(String pkg) {
        if (pkg == null) return "";
        int i = pkg.lastIndexOf('.');
        return i >= 0 && pkg.length() - i - 1 > 1 ? pkg.substring(i + 1) : pkg;
    }
}
