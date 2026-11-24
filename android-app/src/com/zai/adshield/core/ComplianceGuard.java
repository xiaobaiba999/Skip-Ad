package com.zai.adshield.core;

/**
 * v5.5.0 合规与形态守卫（模块三 + 模块四）——设备分级 · 形态自适应 ·
 * 零扫描扩容 · 动作边界自检 · 权限审计
 * ─────────────────────────────────────────────────────────────
 * 【模块三·性能功耗兼容】
 *  · DeviceTier 设备分级 + 电量联动动态降级：
 *    低端机（RAM ≤4GB）+ 低电量（≤20%）时三级联动降级——扫描预算 ×0.6、
 *    突发检测整体关闭（只保留事件驱动主路径）、复验梯子砍半。
 *    中端机低电量轻度降级；旗舰/正常电量零降级（能力不减）。
 *    性能开销 ≤10% 红线：降级只砍「补扫/突发」这类增益路径，
 *    事件驱动 0ms 首扫与探针前置永不动（命中率红线）。
 *  · FormFactor 折叠屏/平板/分屏形态自适应：
 *    宽屏形态（W/H ≥ 0.75：平板/横屏/折叠展开/分屏半宽）下，
 *    右缘带判定从「绝对 75% 屏宽」改为「相对屏宽」（天然自适应），
 *    悬浮球面积门按「可视窗口面积」而非全屏归一（分屏半屏时
 *    全屏归一会把 10% 误算成 5%）。
 *
 * 【模块四·安全合规】
 *  · isZeroScanScenario 零扫描场景扩容（在既有短信/来电/相机/验证码/
 *    支付页/白名单/聊天主界面之上）：系统设置页（com.android.settings
 *    族——设置里的开关绝不被代点）、输入法窗口（ime 族——候选词/
 *    键盘点击绝不被代点）、锁屏（keyguard 族——锁屏紧急呼叫绝不被
 *    代点）、密码框窗口（password/passwd/PinActivity 族）。这些场景
 *    【绝不扫描、绝不点击、绝不学习】。
 *  · actionBoundaryOk 动作边界自检（execute 前最后闸）：
 *    出手目标必须是「跳过/关闭」语义（跳过/skip/close 族文案或 vid），
 *    且不在零扫描场景、不是系统 UI 控件——三重断言，任何一层不
 *    过=放弃动作（宁可漏拦，绝不误触）。
 *  · privacyAudit 权限使用审计：7 项权限 → 用途/触发时机/是否前台
 *    全量映射（App 内「关于 → 权限使用审计」展示 + 日志上传附带）。
 *
 * 设计红线：纯 Java 零依赖纯函数，JVM 可测（ComplianceGuardTest）。
 */
public final class ComplianceGuard {

    private ComplianceGuard() {
    }

    /* ══════════════ 模块三：设备分级与形态自适应 ══════════════ */

    /** 设备能力等级 */
    public static final int TIER_LOW = 0;    // 低端（RAM ≤4GB）
    public static final int TIER_MID = 1;    // 中端（4GB < RAM ≤8GB）
    public static final int TIER_HIGH = 2;   // 旗舰（RAM >8GB）

    /** 设备分级（RAM MB → 等级） */
    public static int deviceTier(int ramMb) {
        if (ramMb <= 4096) return TIER_LOW;
        if (ramMb <= 8192) return TIER_MID;
        return TIER_HIGH;
    }

    /** 电量降级档：正常(≥30%) / 低电(20~30%) / 极低(≤20%) */
    public static final int BATTERY_NORMAL = 0;
    public static final int BATTERY_LOW = 1;
    public static final int BATTERY_CRITICAL = 2;

    public static int batteryTier(int pct, boolean charging) {
        if (charging) return BATTERY_NORMAL; // 充电中不降级
        if (pct <= 20) return BATTERY_CRITICAL;
        if (pct <= 30) return BATTERY_LOW;
        return BATTERY_NORMAL;
    }

    /**
     * 动态降级后的扫描预算系数（×budget）。
     * 红线：事件驱动首扫与探针前置【永不降级】（系数仅作用于
     * 补扫/梯子档的增益路径）——低端+极低电 = 0.6（预算 350→210），
     * 其余 ≥0.8，旗舰正常电量 1.0（零降级）。
     */
    public static double budgetFactor(int tier, int battTier) {
        if (tier == TIER_LOW && battTier == BATTERY_CRITICAL) return 0.6;
        if (tier == TIER_LOW) return 0.8;
        if (tier == TIER_MID && battTier == BATTERY_CRITICAL) return 0.7;
        if (battTier != BATTERY_NORMAL) return 0.8;
        return 1.0;
    }

    /**
     * 突发检测是否允许（内容突发 → 弹窗识别窗的开销大户）：
     * 低端机或极低电量 → 整体关闭（省下突发窗全量扫描），
     * 只保留事件驱动主路径。中端低电减半频（由调用方按返回值
     * false 跳过奇数轮实现，本函数给出布尔门）。
     */
    public static boolean burstAllowed(int tier, int battTier) {
        if (tier == TIER_LOW || battTier == BATTERY_CRITICAL) return false;
        return true;
    }

    /** 降级文案（诊断日志用） */
    public static String degradeNote(int tier, int battTier) {
        double f = budgetFactor(tier, battTier);
        if (f >= 1.0) return "性能档：全量（零降级）";
        String t = tier == TIER_LOW ? "低端" : tier == TIER_MID ? "中端" : "旗舰";
        String b = battTier == BATTERY_CRITICAL ? "极低电" : "低电";
        return "性能档：" + t + "·" + b + " → 增益路径预算 ×" + f
                + (burstAllowed(tier, battTier) ? "" : " · 突发检测关闭");
    }

    /* ───────── 形态自适应（折叠屏/平板/分屏） ───────── */

    /** 宽屏形态判定：W/H ≥ 0.75（平板/横屏/折叠展开/分屏半宽） */
    public static boolean wideForm(int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) return false;
        return (double) screenW / screenH >= 0.75;
    }

    /**
     * 形态自适应的「可视窗口面积」归一：分屏/折叠态下无障碍节点
     * bounds 是窗口坐标系，用全屏归一会把面积低估一半（半屏 10%
     * 误算 5%）——宽屏形态按短边平方近似可视区域。
     */
    public static double areaRatioAdaptive(int[] b, int screenW, int screenH) {
        if (b == null || screenW <= 0 || screenH <= 0) return 0;
        double ref = wideForm(screenW, screenH)
                ? (double) Math.min(screenW, screenH) * Math.min(screenW, screenH)
                : (double) screenW * screenH;
        double area = (double) (b[2] - b[0]) * (b[3] - b[1]);
        return area / ref;
    }

    /* ══════════════ 模块四：零扫描扩容 + 动作边界 + 权限审计 ══════════════ */

    /**
     * 零扫描场景扩容（模块四核心）：
     * 既有（AdDecisionCore/isSmsOverlayPkg/isCameraPkg/验证码横幅/支付页
     * 文案/白名单/聊天主界面）之外，新增四类——系统设置页 / 输入法窗口 /
     * 锁屏 / 密码框窗口。这些场景【绝不扫描、绝不点击、绝不学习】。
     *
     * v5.5.1（issue#64 实锤）增补：系统下载管理设置页（com.android.providers.
     * downloads.ui —— 日志里 9 秒被整窗扫描 4 次 + 位置学习误触发，它有
     * 「.ui」通用尾段，.settings/systemui/securitycenter 匹配全部漏网）
     * 与系统文件选择器（documentsui · SAF）——纯系统 UI 宿主绝不承载
     * 广告，扫描是纯功耗、点击是纯风险面（选择器的保存/取消按钮）。
     */
    public static boolean isZeroScanScenario(String pkg, String cls) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        /* 系统设置页（含厂商设置壳）：设置里的开关/条目绝不被代点 */
        if (p.equals("com.android.settings") || p.endsWith(".settings")
                || p.contains("securitycenter") || p.contains("systemui")) {
            return true;
        }
        /* v5.5.1：系统下载管理/文件选择器（issue#64 实锤漏网族） */
        if (p.contains("providers.downloads") || p.contains("documentsui")) {
            return true;
        }
        /* 输入法窗口：候选词/键盘按键绝不被代点（Gboard/搜狗/百度输入法等） */
        if (p.contains(".inputmethod") || p.contains(".ime.") || p.endsWith(".ime")) {
            return true;
        }
        /* 锁屏/系统钥匙串：锁屏上的紧急呼叫/解锁绝不被代点 */
        if (p.contains("keyguard") || p.contains("lockscreen")) {
            return true;
        }
        /* 密码框窗口（银行/支付类常见独立密码页） */
        if (cls != null) {
            String c = cls.toLowerCase();
            if (c.contains("password") || c.contains("passwd")
                    || c.contains("pinactivity") || c.contains("verifyidentity")) {
                return true;
            }
        }
        return false;
    }

    /**
     * v7.15.1：锁屏宿主包（systemui / keyguard / lockscreen 族）。
     * 与 isZeroScanScenario 的锁屏分支同源、语义独立：
     *  · 零扫描门用 isZeroScanScenario（密码框等场景还需类名参与）；
     *  · 亮屏/解锁 rearm（RearmPlanner）用本判定「前台追踪是否被锁屏
     *    污染」——息屏期间锁屏窗把 lastFgPkg 顶掉真实应用（真机日志
     *    实锤：解锁后引擎补扫的是锁屏树 73 节点，真实应用被前台门
     *    盲区吞掉），重入目标必须校正回真实应用。
     * 纯函数，JVM 可测（RearmPlannerTest）。
     */
    public static boolean isLockHostPkg(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        return p.contains("systemui") || p.contains("keyguard") || p.contains("lockscreen");
    }

    /* ───────── 动作边界自检失败层（v5.5.1 可诊断化） ───────── */
    /** 失败层码（issue#64 实锤：旧日志只说「语义或越界」，无法定位是哪层拒绝） */
    public static final int BND_OK = 0;          // 全部通过
    public static final int BND_FAIL_SCENARIO = 1; // 场景断言（零扫描/相机/守护）
    public static final int BND_FAIL_SEMANTIC = 2; // 语义断言（非跳过/关闭方向）
    public static final int BND_FAIL_FORM = 3;      // 形态断言（面积越界/无 bounds）

    /** 失败层中文名（诊断日志用） */
    public static String boundaryLayerName(int layer) {
        switch (layer) {
            case BND_OK: return "通过";
            case BND_FAIL_SCENARIO: return "场景断言（零扫描/相机/守护名单）";
            case BND_FAIL_SEMANTIC: return "语义断言（非跳过/关闭方向）";
            case BND_FAIL_FORM: return "形态断言（面积越界/无bounds）";
            default: return "未知";
        }
    }

    /**
     * 动作边界自检（execute 前最后闸，模块四红线「仅代点原生跳过控件」）：
     *  ① 语义断言：目标 label/vid 必须是跳过/关闭语义（跳过族/close 族/
     *     更新弹窗否定侧——全部是「拒绝/关闭」方向，绝不代点「确认/同意/
     *     立即」方向）；userTaught（用户手动教过的位置=用户意图）豁免；
     *     v5.5.1（issue#64 实锤）：gestureOnly 规则命中（无可点祖先，仅有
     *     手势坐标）时 label 由规则命中标签传入——规则引擎已按「跳过广告」
     *     文案验证过语义，此处只是把语义依据从「控件文本」换到「命中文案」，
     *     绝不新增任何放行面（非跳过文案依旧一票否决）；
     *  ② 场景断言：当前不在零扫描场景（系统设置/输入法/锁屏/密码框/
     *     短信/相机/守护名单）；
     *  ③ 形态断言：目标面积在合理区间（≤35% 屏，CTA 巨按钮绝不中）。
     * 三重断言任一不过 = 放弃动作（宁可漏拦，绝不误触）。
     */
    public static boolean actionBoundaryOk(String label, String vid, int[] bounds,
                                           int screenW, int screenH,
                                           String pkg, String cls, boolean userTaught) {
        return boundaryCheck(label, vid, bounds, screenW, screenH, pkg, cls, userTaught) == BND_OK;
    }

    /**
     * v5.5.1 可诊断版边界自检：返回失败层码（BND_*）。
     * 服务侧 execute 调用后按层码打点——「🚧 动作边界自检拦截」日志
     * 从此能直接回答「哪层拒绝、拒绝的是什么」（issue#64 微博误杀
     * 根因正是旧日志含糊无法定位）。
     */
    public static int boundaryCheck(String label, String vid, int[] bounds,
                                    int screenW, int screenH,
                                    String pkg, String cls, boolean userTaught) {
        /* ② 场景断言（先行：零扫描场景里连语义都免谈） */
        if (isZeroScanScenario(pkg, cls)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isSmsOverlayPkg(pkg) || AdDecisionCore.isCameraPkg(pkg)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isCameraClass(cls)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isGuardPkg(pkg)) return BND_FAIL_SCENARIO;
        /* ① 语义断言（用户教过的位置豁免——用户意图优先） */
        if (!userTaught) {
            String s = label == null ? "" : label.trim().toLowerCase();
            String v = vid == null ? "" : vid.toLowerCase();
            boolean skipSemantics = s.contains("跳过") || s.contains("跳過") || s.contains("skip")
                    || RuleEngine.isUpdateNegative(label == null ? "" : label.trim())
                    || v.contains("skip") || v.contains("close") || v.contains("dismiss")
                    || v.contains("exit") || v.contains("preroll")
                    || s.contains("关闭") || s.contains("close") || s.contains("×");
            if (!skipSemantics) return BND_FAIL_SEMANTIC;
        }
        /* ③ 形态断言 */
        if (bounds == null || bounds.length < 4) return BND_FAIL_FORM;
        double area = areaRatioAdaptive(bounds, screenW, screenH);
        if (area <= 0 || area > AdDecisionCore.SKIP_AREA_MAX) return BND_FAIL_FORM;
        return BND_OK;
    }

    /**
     * v7.1.0 feed 决策专用边界终闸（execute 的 feed_p1_badge/feed_p2_close 分支，
     * FeedAdGuard 移植自 v6.2.0 已验证实现）：场景断言（零扫描/短信/相机/守护）
     * + 语义断言（P1 角标 isDisclosureBadge / P2 中性项 isNeutralCloseItem
     * ——FeedAdGuard 语义）+ 形态断言（面积 ≤35% 屏）。
     * 绝不新增任何非信息流放行面（reason 前缀 feed_ 仅服务侧 feed 路径构造）。
     */
    public static int feedBoundaryCheck(String label, int[] bounds, int screenW, int screenH,
                                        String pkg, String cls) {
        if (isZeroScanScenario(pkg, cls)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isSmsOverlayPkg(pkg) || AdDecisionCore.isCameraPkg(pkg)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isCameraClass(cls)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isGuardPkg(pkg)) return BND_FAIL_SCENARIO;
        String t = label == null ? "" : label.trim();
        /* v7.4.0：P1X 直关钮词（× 家族/关闭——精确等值）进入 feed 终闸语义面：
         * P1X 决策上游已过「广告上下文 + 同行近距」双闸（decideP1XFromCandidates
         * 宁漏勿误三重闸），此处终闸只验形态（面积 ≤35% 屏）——绝不新增任何
         * 非 feed_ 路径的放行面。 */
        if (!FeedAdGuard.isNeutralCloseItem(t) && !FeedAdGuard.isDisclosureBadge(t, bounds, screenW, screenH)
                && !FeedAdGuard.isCloseIconWord(t)) {
            return BND_FAIL_SEMANTIC;
        }
        if (bounds == null || bounds.length < 4) return BND_FAIL_FORM;
        double area = areaRatioAdaptive(bounds, screenW, screenH);
        if (area <= 0 || area > AdDecisionCore.SKIP_AREA_MAX) return BND_FAIL_FORM;
        return BND_OK;
    }

    /**
     * v7.6.0 插屏决策专用边界终闸（execute 的 inter_close 分支）。
     * 死因（用户实锤「软件内插入的广告不仅没跳过」）：v7.5.0
     * INTER_CLOSE_WORDS 收了「知道了/我知道了/残忍离开」等词，但
     * execute() 走通用 boundaryCheck——「知道了」不含跳过/关闭/skip/
     * close/× → BND_FAIL_SEMANTIC 一票否决（终闸杀词：词表收了也白收，
     * 出手永远被最后一道闸打回）。插屏专用终闸：
     *  · 场景断言与 boundaryCheck 同源（零扫描/短信/相机/守护全拒）；
     *  · 语义断言换源 InterstitialGuard.isInterstitialCloseWord（决策上游
     *    已过「广告语境在场 contains + 精确等值 + 形态 ≤30%×≤12%」三闸，
     *    此处复验词表成员身份——只对 inter_close 路径放行，绝无新增面）；
     *  · 形态断言（面积 ≤35% 屏）与 boundaryCheck 同源。
     */
    public static int interBoundaryCheck(String label, int[] bounds, int screenW, int screenH,
                                         String pkg, String cls) {
        if (isZeroScanScenario(pkg, cls)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isSmsOverlayPkg(pkg) || AdDecisionCore.isCameraPkg(pkg)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isCameraClass(cls)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isGuardPkg(pkg)) return BND_FAIL_SCENARIO;
        String t = label == null ? "" : label.trim();
        if (!InterstitialGuard.isInterstitialCloseWord(t)) return BND_FAIL_SEMANTIC;
        if (bounds == null || bounds.length < 4) return BND_FAIL_FORM;
        /* v7.6.0 纵深防御：决策上游 closeFormOk（≤30%×≤12%）终闸复验——
         * 通用面积闸（≤35% 屏）拦不住 400×300（4.6% 屏）的宽扁 CTA——
         * 关闭钮是钮不是横幅，per-dimension 形态闸在这里再拦一道。 */
        if (!InterstitialGuard.closeFormOk(bounds, screenW, screenH)) return BND_FAIL_FORM;
        double area = areaRatioAdaptive(bounds, screenW, screenH);
        if (area <= 0 || area > AdDecisionCore.SKIP_AREA_MAX) return BND_FAIL_FORM;
        return BND_OK;
    }

    /* ───────── v7.15.7 图片跳过键专用终闸（CSDN 语义误杀根治） ───────── */

    /** 图片键形态上限（与 AdDecisionCore 泛位置候选同源——服务侧终闸复验用） */
    private static final double IMG_KEY_AREA_MAX = AdDecisionCore.RAND_AREA_MAX;
    private static final double IMG_KEY_W_MAX = AdDecisionCore.RAND_W_MAX;
    private static final double IMG_KEY_H_MAX = AdDecisionCore.RAND_H_MAX;

    /**
     * v7.15.7 图片跳过键专用边界终闸（execute 的 corner_image_skip /
     * rand_graphic_skip 分支——通用 boundaryCheck 语义断言误杀根治）。
     * 死因（真机日志实锤 #98/#101：CSDN 动态图开屏「角落图片跳过键：
     * 语义断言（非跳过/关闭方向）→ 放弃」反复出现）：这类开屏的跳过键是
     * 【纯图片】（无文本、viewId 无 skip/close token）——通用终闸的语义
     * 断言要求 label/vid 含跳过/关闭字面，纯图键永远凑不齐 = 收得到候选
     * 却永远出不了手（决策层 L4/L4.7 收集+出手门全过，最后一步被杀）。
     * 语义安全换源：决策上游已过「强广告形态（热区/角标/SDK窗/学习窗）
     * 或微树兕底 + 无滚动/无输入框 + vid 诱导 token 否决 + 时窗 300~6500ms」
     * 多重门——此处终闸只复验【场景 + 形态】（零文本语义可查时，形态即
     * 语义：真跳过键是钮不是横幅，面积 ≤6% 屏、宽 ≤30%、高 ≤12% 三重
     * 尺寸门与泛位置候选同源），绝不新增任何非图片键放行面：
     *  · 场景断言：与 boundaryCheck 同源（零扫描/短信/相机/守护全拒）；
     *  · 形态断言：比通用闸（≤35% 屏）严格一档（≤6% 屏 + per-dimension）。
     */
    public static int imageKeyBoundaryCheck(int[] bounds, int screenW, int screenH,
                                            String pkg, String cls) {
        if (isZeroScanScenario(pkg, cls)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isSmsOverlayPkg(pkg) || AdDecisionCore.isCameraPkg(pkg)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isCameraClass(cls)) return BND_FAIL_SCENARIO;
        if (AdDecisionCore.isGuardPkg(pkg)) return BND_FAIL_SCENARIO;
        if (bounds == null || bounds.length < 4) return BND_FAIL_FORM;
        if (screenW <= 0 || screenH <= 0) return BND_FAIL_FORM;
        int w = bounds[2] - bounds[0];
        int h = bounds[3] - bounds[1];
        if (w <= 0 || h <= 0) return BND_FAIL_FORM;
        if (w > screenW * IMG_KEY_W_MAX || h > screenH * IMG_KEY_H_MAX) return BND_FAIL_FORM;
        double area = areaRatioAdaptive(bounds, screenW, screenH);
        if (area <= 0 || area > IMG_KEY_AREA_MAX) return BND_FAIL_FORM;
        return BND_OK;
    }

    /**
     * 权限使用审计（模块四）：7 项权限 → 用途/触发时机全量映射。
     * App 内「关于 → 权限使用审计」展示 + 日志上传自动附带。
     * 返回逐行审计报告（纯函数：权限清单与 Manifest 同步维护）。
     */
    public static String privacyAudit() {
        return "权限使用审计（v5.5.0 · 共 7 项，全部最小化）\n"
                + "  1. INTERNET          —— GitHub Releases 检查更新 / GKD 订阅拉取 / 日志上报（仅用户手动触发）\n"
                + "  2. REQUEST_INSTALL_PACKAGES —— 检查更新后页内直下 APK 的安装确认（仅用户点「安装」时系统弹窗）\n"
                + "  3. POST_NOTIFICATIONS —— 常驻守护通知 / 恢复提醒 / 日志上传结果（可在系统设置关闭）\n"
                + "  4. FOREGROUND_SERVICE(专用) —— 无障碍跳过服务本体（Android 14+ 前台服务类型声明）\n"
                + "  5. RECEIVE_BOOT_COMPLETED —— 开机自检（仅恢复守护状态提醒，不自动拉起未授权服务）\n"
                + "  6. REQUEST_IGNORE_BATTERY_OPTIMIZATIONS —— 电池白名单引导（用户可拒绝，拒绝后仅提醒降频）\n"
                + "  7. 无障碍服务（BIND_ACCESSIBILITY_SERVICE）—— 核心跳过能力（系统设置内授权，App 绝不代开）\n"
                + "零权限声明：定位/通讯录/短信读取/存储/相机/麦克风/剪贴板——全部未申请、永不需要。\n"
                + "数据边界：学习指纹仅存本机；日志上传四类内容（版本/设备/拦截日志/异常堆栈）双层正则脱敏后经用户手动触发上报。";
    }
}
