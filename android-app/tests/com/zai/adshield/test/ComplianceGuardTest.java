package com.zai.adshield.test;

import com.zai.adshield.core.ComplianceGuard;
import com.zai.adshield.core.AdDecisionCore;

/**
 * ═══════════════════════════════════════════════════════════════
 *  v5.5.1 合规守卫测试（ComplianceGuardTest）
 * ═══════════════════════════════════════════════════════════════
 *  ⚠ 本套件在 v5.5.0 的 run-tests.sh 已注册但源码从未提交（发版声明
 *  「599 项断言全绿」实际缺失 124 项）——v5.5.1 补齐并扩展。
 *
 *  失败场景全部来自真机引擎日志（issue#56~#64）：
 *   · issue#64（v5.5.0 · Xiaomi 2410DPN6CC）：小米下载管理设置页
 *     （com.android.providers.downloads.ui · DownloadSettingActivity）
 *     9 秒被整窗扫描 4 次 + 位置学习误触发——零扫描名单漏网；
 *   · issue#64：微博「内嵌规则·分应用文案命中」被动作边界自检拦截
 *     ——gestureOnly 命中（无可点祖先）语义依据丢失，空 label 误杀；
 *   · issue#56/#57/#64：候选样本越界坐标（播放列表@(16%,150%)、
 *     vid:checkbox@(187%,17%)）与重复样本（关闭@(93%,9%) 双胞胎）。
 *
 *  验证目标：
 *   ① 设备分级 × 电量 16 档矩阵：预算系数只作用于增益路径；
 *   ② 折叠屏/平板/分屏形态自适应：宽屏短边归一；
 *   ③ 零扫描场景：四类既有 + v5.5.1 下载管理/文件选择器增补；
 *   ④ 动作边界三重断言：场景/语义/形态分层可诊断（BND_* 层码）；
 *   ⑤ v5.5.1 label 回落：规则命中文案作为语义依据（零放行面验证）；
 *   ⑥ 样本越界判定（sampleOnScreen）：实锤坐标全数拦截；
 *   ⑦ 权限审计与降级文案。
 */
public final class ComplianceGuardTest {

    /* 1080×2400 标准屏的跳过键模板（右上角小按钮，面积≈0.35%） */
    private static final int[] SKIP_BOUNDS = {920, 80, 1050, 150};
    private static final int SW = 1080, SH = 2400;

    public static void main(String[] args) {
        TestHost.suite("① 设备分级 × 电量矩阵");
        testTierMatrix();

        TestHost.suite("② 形态自适应（折叠屏/平板/分屏）");
        testFormFactor();

        TestHost.suite("③ 零扫描场景（含 v5.5.1 下载管理增补）");
        testZeroScan();

        TestHost.suite("④ 动作边界三重断言 · 层码可诊断");
        testBoundaryLayers();

        TestHost.suite("⑤ v5.5.1 label 回落（gestureOnly 误杀根治）");
        testLabelFallback();

        TestHost.suite("⑥ 样本越界判定（issue 实锤坐标）");
        testSampleOnScreen();

        TestHost.suite("⑦ 权限审计与降级文案");
        testAuditTexts();

        TestHost.done();
    }

    /* ───────── ① 设备分级 × 电量矩阵 ───────── */

    private static void testTierMatrix() {
        // 分级边界：4GB / 8GB
        TestHost.checkEq(ComplianceGuard.TIER_LOW, ComplianceGuard.deviceTier(4096), "4GB → 低端");
        TestHost.checkEq(ComplianceGuard.TIER_MID, ComplianceGuard.deviceTier(4097), "4GB+1MB → 中端");
        TestHost.checkEq(ComplianceGuard.TIER_HIGH, ComplianceGuard.deviceTier(8193), "8GB+1MB → 旗舰");

        // 电量档：充电永不降级
        TestHost.checkEq(ComplianceGuard.BATTERY_NORMAL, ComplianceGuard.batteryTier(10, true), "充电中 → 正常档");
        TestHost.checkEq(ComplianceGuard.BATTERY_CRITICAL, ComplianceGuard.batteryTier(10, false), "10% 不充电 → 极低");
        TestHost.checkEq(ComplianceGuard.BATTERY_LOW, ComplianceGuard.batteryTier(25, false), "25% → 低电");
        TestHost.checkEq(ComplianceGuard.BATTERY_NORMAL, ComplianceGuard.batteryTier(80, false), "80% → 正常");

        // 预算系数矩阵（性能开销 ≤10% 红线：低端+极低电 = 0.6）
        TestHost.checkEq(0.6, ComplianceGuard.budgetFactor(ComplianceGuard.TIER_LOW, ComplianceGuard.BATTERY_CRITICAL), "低端×极低电 → ×0.6");
        TestHost.checkEq(0.8, ComplianceGuard.budgetFactor(ComplianceGuard.TIER_LOW, ComplianceGuard.BATTERY_NORMAL), "低端×正常电 → ×0.8");
        TestHost.checkEq(0.7, ComplianceGuard.budgetFactor(ComplianceGuard.TIER_MID, ComplianceGuard.BATTERY_CRITICAL), "中端×极低电 → ×0.7");
        TestHost.checkEq(0.8, ComplianceGuard.budgetFactor(ComplianceGuard.TIER_HIGH, ComplianceGuard.BATTERY_LOW), "旗舰×低电 → ×0.8");
        TestHost.checkEq(1.0, ComplianceGuard.budgetFactor(ComplianceGuard.TIER_HIGH, ComplianceGuard.BATTERY_NORMAL), "旗舰×正常电 → ×1.0（零降级）");

        // 突发门：低端/极低电整体关闭（事件驱动主路径不受影响）
        TestHost.check(!ComplianceGuard.burstAllowed(ComplianceGuard.TIER_LOW, ComplianceGuard.BATTERY_NORMAL), "低端 → 突发检测关闭");
        TestHost.check(!ComplianceGuard.burstAllowed(ComplianceGuard.TIER_HIGH, ComplianceGuard.BATTERY_CRITICAL), "极低电 → 突发检测关闭");
        TestHost.check(ComplianceGuard.burstAllowed(ComplianceGuard.TIER_HIGH, ComplianceGuard.BATTERY_NORMAL), "旗舰正常电 → 突发检测放行");

        // 降级文案
        TestHost.check(ComplianceGuard.degradeNote(ComplianceGuard.TIER_HIGH, ComplianceGuard.BATTERY_NORMAL).contains("零降级"),
                "旗舰正常电文案 → 零降级");
        TestHost.check(ComplianceGuard.degradeNote(ComplianceGuard.TIER_LOW, ComplianceGuard.BATTERY_CRITICAL).contains("0.6"),
                "低端极低电文案 → ×0.6");
    }

    /* ───────── ② 形态自适应 ───────── */

    private static void testFormFactor() {
        // 宽屏判定：W/H ≥ 0.75
        TestHost.check(!ComplianceGuard.wideForm(1080, 2400), "1080×2400 竖屏 → 非宽屏");
        TestHost.check(ComplianceGuard.wideForm(2400, 1080), "2400×1080 横屏 → 宽屏");
        TestHost.check(ComplianceGuard.wideForm(1080, 1440), "1080×1440 折叠展开（0.75）→ 宽屏");
        TestHost.check(!ComplianceGuard.wideForm(0, 2400), "宽 0 → 非宽屏（防除零）");

        // 分屏半屏面积归一：竖屏按全屏；宽屏按短边平方（分屏半屏 10% 不被低估成 5%）
        int[] half = {0, 0, 1080, 110}; // 竖屏 1080×2400 上约 4.6% 高
        double v = ComplianceGuard.areaRatioAdaptive(half, 1080, 2400);
        double expect = (1080.0 * 110.0) / (1080.0 * 2400.0);
        TestHost.check(Math.abs(v - expect) < 1e-9, "竖屏 → 全屏归一（精确）");
        // 宽屏形态：2400×1080，同样物理控件按短边(1080)平方归一
        double w = ComplianceGuard.areaRatioAdaptive(half, 2400, 1080);
        double expectW = (1080.0 * 110.0) / (1080.0 * 1080.0);
        TestHost.check(Math.abs(w - expectW) < 1e-9, "宽屏 → 短边平方归一（分屏防低估）");
        // 越界防护
        TestHost.checkEq(0.0, ComplianceGuard.areaRatioAdaptive(null, 1080, 2400), "null bounds → 0");
        TestHost.checkEq(0.0, ComplianceGuard.areaRatioAdaptive(half, 0, 2400), "宽 0 → 0（防除零）");
    }

    /* ───────── ③ 零扫描场景 ───────── */

    private static void testZeroScan() {
        /* 既有四类（v5.5.0） */
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.android.settings", "SettingsActivity"), "系统设置页 → 零扫描");
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.miui.securitycenter", null), "厂商安全中心 → 零扫描");
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.android.systemui", null), "SystemUI → 零扫描");
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.iflytek.inputmethod", null), "输入法 → 零扫描");
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.android.keyguard", null), "锁屏 → 零扫描");
        TestHost.check(!ComplianceGuard.isZeroScanScenario(null, null), "null 包名 → 不放行（保守 false）");
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.bank.app", "com.bank.app.PasswordActivity"), "密码窗（类名）→ 零扫描");

        /* v5.5.1（issue#64 实锤）：小米下载管理设置页漏网根治
         * 日志证据：开屏未命中（downloads.ui）×4 + DownloadSettingActivity
         * 整窗扫描——「.ui」通用尾段使 .settings/systemui 匹配全部漏网 */
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.android.providers.downloads.ui", "android.app.Activity"),
                "v5.5.1 系统下载管理页（issue#64 实锤）→ 零扫描");
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.android.providers.downloads.ui", "com.android.providers.downloads.ui.DownloadSettingActivity"),
                "v5.5.1 DownloadSettingActivity → 零扫描");
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.android.documentsui", "DocumentsActivity"),
                "v5.5.1 系统文件选择器（SAF）→ 零扫描");
        TestHost.check(ComplianceGuard.isZeroScanScenario("Com.Android.Providers.Downloads.UI", null),
                "包名大写归一 → 零扫描（大小写不敏感）");

        /* 反例：普通应用绝不被零扫描误伤（误杀率红线） */
        TestHost.check(!ComplianceGuard.isZeroScanScenario("com.sina.weibo", "SplashActivity"), "微博开屏 → 照常扫描（反例）");
        TestHost.check(!ComplianceGuard.isZeroScanScenario("com.ximalaya.ting.android", "MainActivity"), "喜马拉雅 → 照常扫描（反例）");
        TestHost.check(ComplianceGuard.isZeroScanScenario("com.android.providers.downloads", null),
                "providers.downloads 子串命中（家族全收）");
        // 普通应用带 settings 字样的第三方（如 x.y.settingsui 前缀误伤检查）
        TestHost.check(!ComplianceGuard.isZeroScanScenario("com.example.downloader", "MainActivity"),
                "第三方下载工具（非系统宿主）→ 照常扫描（反例）");
    }

    /* ───────── ④ 动作边界三重断言 · 层码 ───────── */

    private static void testBoundaryLayers() {
        /* 基线：真跳过键全通过 */
        TestHost.checkEq(ComplianceGuard.BND_OK,
                ComplianceGuard.boundaryCheck("跳过广告", "com.sina.weibo:id/tv_skip", SKIP_BOUNDS, SW, SH,
                        "com.sina.weibo", "com.sina.weibo.SplashActivity", false),
                "微博跳过键（文本+vid）→ 通过");

        /* 场景层：零扫描/相机/守护场景先行拒绝 */
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SCENARIO,
                ComplianceGuard.boundaryCheck("跳过", "", SKIP_BOUNDS, SW, SH,
                        "com.android.settings", "Settings$Activity", false),
                "系统设置页出手 → 场景层拒绝");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SCENARIO,
                ComplianceGuard.boundaryCheck("跳过", "", SKIP_BOUNDS, SW, SH,
                        "com.android.camera", "CameraActivity", false),
                "相机场景出手 → 场景层拒绝");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SCENARIO,
                ComplianceGuard.boundaryCheck("关闭", "", SKIP_BOUNDS, SW, SH,
                        "com.eg.android.AlipayGphone", "PayActivity", false),
                "支付守护场景出手 → 场景层拒绝");

        /* 语义层：非跳过/关闭方向一票否决 */
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC,
                ComplianceGuard.boundaryCheck("立即下载", "", SKIP_BOUNDS, SW, SH,
                        "com.example.app", "AdActivity", false),
                "诱导 CTA「立即下载」→ 语义层拒绝");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC,
                ComplianceGuard.boundaryCheck("摇一摇领福利", "", SKIP_BOUNDS, SW, SH,
                        "com.example.app", "AdActivity", false),
                "「摇一摇领福利」→ 语义层拒绝");

        /* 形态层：无 bounds / 超面积 CTA 巨按钮拒绝 */
        TestHost.checkEq(ComplianceGuard.BND_FAIL_FORM,
                ComplianceGuard.boundaryCheck("跳过", "", null, SW, SH,
                        "com.example.app", "AdActivity", false),
                "无 bounds → 形态层拒绝");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_FORM,
                ComplianceGuard.boundaryCheck("跳过", "", new int[]{0, 0, 1080, 1680}, SW, SH,
                        "com.example.app", "AdActivity", false),
                "全屏 CTA 巨按钮（75% 屏）→ 形态层拒绝");

        /* 用户教学豁免语义层（用户意图优先），但场景层不豁免 */
        TestHost.checkEq(ComplianceGuard.BND_OK,
                ComplianceGuard.boundaryCheck("零文本图片键", "", SKIP_BOUNDS, SW, SH,
                        "com.example.app", "AdActivity", true),
                "userTaught 豁免语义层 → 通过");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SCENARIO,
                ComplianceGuard.boundaryCheck("跳过", "", SKIP_BOUNDS, SW, SH,
                        "com.android.settings", "SettingsActivity", true),
                "userTaught 不豁免场景层 → 仍拒绝");

        /* 层码中文名（v5.5.1 可诊断化——旧日志「语义或越界」两可含糊） */
        TestHost.checkEq("通过", ComplianceGuard.boundaryLayerName(ComplianceGuard.BND_OK), "层码 0 → 通过");
        TestHost.check(ComplianceGuard.boundaryLayerName(ComplianceGuard.BND_FAIL_SCENARIO).contains("场景"),
                "层码 1 → 场景断言命名");
        TestHost.check(ComplianceGuard.boundaryLayerName(ComplianceGuard.BND_FAIL_SEMANTIC).contains("语义"),
                "层码 2 → 语义断言命名");
        TestHost.check(ComplianceGuard.boundaryLayerName(ComplianceGuard.BND_FAIL_FORM).contains("形态"),
                "层码 3 → 形态断言命名");

        /* 兼容 API：actionBoundaryOk 与 boundaryCheck 一致 */
        TestHost.check(ComplianceGuard.actionBoundaryOk("跳过", "", SKIP_BOUNDS, SW, SH,
                "com.example.app", "AdActivity", false), "actionBoundaryOk 兼容版 → true");
        TestHost.check(!ComplianceGuard.actionBoundaryOk("立即下载", "", SKIP_BOUNDS, SW, SH,
                "com.example.app", "AdActivity", false), "actionBoundaryOk 兼容版 → false");
    }

    /* ───────── ⑤ v5.5.1 label 回落（issue#64 微博误杀根治） ───────── */

    private static void testLabelFallback() {
        /* issue#64 复盘：微博 gestureOnly 命中（无可点祖先）——d.node=null，
         * 旧代码拿空 label 做语义断言 → 误杀。修复：Decision.label 携带
         * 规则已验证的命中文案（服务侧 execute 回落）。本组断言验证
         * boundaryCheck 的 label 语义通道——即「换源不放宽」红线。 */

        /* 复现旧 bug 形态：空 label + 空 vid → 语义层拒绝（这就是 v5.5.0 的误杀） */
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC,
                ComplianceGuard.boundaryCheck("", "", SKIP_BOUNDS, SW, SH,
                        "com.sina.weibo", "com.sina.weibo.SplashActivity", false),
                "空 label+空 vid（v5.5.0 误杀形态复现）→ 语义层拒绝");

        /* 修复后：规则命中文案「跳过广告」作为语义依据 → 通过 */
        TestHost.checkEq(ComplianceGuard.BND_OK,
                ComplianceGuard.boundaryCheck("跳过广告", "", SKIP_BOUNDS, SW, SH,
                        "com.sina.weibo", "com.sina.weibo.SplashActivity", false),
                "v5.5.1：命中文案「跳过广告」→ 通过（微博漏拦截根治）");

        /* 零放行面验证：非跳过语义的 label 依旧被语义层拒绝 */
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC,
                ComplianceGuard.boundaryCheck("领取福利", "", SKIP_BOUNDS, SW, SH,
                        "com.sina.weibo", "com.sina.weibo.SplashActivity", false),
                "回落通道零放宽：「领取福利」→ 仍拒绝");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC,
                ComplianceGuard.boundaryCheck("确认", "", SKIP_BOUNDS, SW, SH,
                        "com.example.app", "DialogActivity", false),
                "「确认」方向 → 仍拒绝（绝不代点肯定侧）");

        /* 倒计时形态文案也走通道（「跳过 3」类） */
        TestHost.checkEq(ComplianceGuard.BND_OK,
                ComplianceGuard.boundaryCheck("跳过 3", "", SKIP_BOUNDS, SW, SH,
                        "com.example.app", "AdActivity", false),
                "「跳过 3」倒计时形态 → 通过");

        /* 更新弹窗否定侧（拒绝/关闭方向）通过，肯定侧拒绝 */
        TestHost.checkEq(ComplianceGuard.BND_OK,
                ComplianceGuard.boundaryCheck("以后再说", "", SKIP_BOUNDS, SW, SH,
                        "com.example.app", "UpdateDialog", false),
                "更新弹窗否定侧「以后再说」→ 通过");
        TestHost.checkEq(ComplianceGuard.BND_FAIL_SEMANTIC,
                ComplianceGuard.boundaryCheck("立即更新", "", SKIP_BOUNDS, SW, SH,
                        "com.example.app", "UpdateDialog", false),
                "肯定侧「立即更新」→ 拒绝");
    }

    /* ───────── ⑥ 样本越界判定（issue 实锤坐标） ───────── */

    private static void testSampleOnScreen() {
        /* 正常在屏样本 */
        TestHost.check(AdDecisionCore.sampleOnScreen(50, 50), "屏中心 → 在屏");
        TestHost.check(AdDecisionCore.sampleOnScreen(93, 9), "关闭键@(93%,9%) → 在屏");
        TestHost.check(AdDecisionCore.sampleOnScreen(10, 9), "返回@(10%,9%) → 在屏");

        /* 贴边容差（±2%）：贴边键不算越界 */
        TestHost.check(AdDecisionCore.sampleOnScreen(100, 9), "x=100% 贴右缘 → 在屏");
        TestHost.check(AdDecisionCore.sampleOnScreen(102, 50), "x=102% 容差内 → 在屏");
        TestHost.check(AdDecisionCore.sampleOnScreen(0, 0), "原点 → 在屏");
        TestHost.check(AdDecisionCore.sampleOnScreen(-2, 50), "x=-2% 容差内 → 在屏");

        /* issue#57/#64 实锤越界样本全数拦截：
         *  播放列表@(16%,150%)、vid:checkbox@(187%,17%)、返回@(110%,9%) */
        TestHost.check(!AdDecisionCore.sampleOnScreen(16, 150), "播放列表@(16%,150%) → 拦截（issue#57 实锤）");
        TestHost.check(!AdDecisionCore.sampleOnScreen(187, 17), "vid:checkbox@(187%,17%) → 拦截（issue#64 实锤）");
        TestHost.check(!AdDecisionCore.sampleOnScreen(110, 9), "返回@(110%,9%) → 拦截（issue#64 实锤）");
        TestHost.check(!AdDecisionCore.sampleOnScreen(50, 103), "y=103% 越界 → 拦截（issue#57 位置学习拒绝同源）");
        TestHost.check(!AdDecisionCore.sampleOnScreen(103, 102), "双越界 → 拦截");
        TestHost.check(!AdDecisionCore.sampleOnScreen(-5, 50), "x=-5% 超容差 → 拦截");
        TestHost.check(!AdDecisionCore.sampleOnScreen(50, -3), "y=-3% 超容差 → 拦截");

        /* 容差常量暴露（调参与测试共用） */
        TestHost.checkEq(2.0, AdDecisionCore.SAMPLE_TOLERANCE, "容差常量 = 2%");
    }

    /* ───────── ⑦ 权限审计与降级文案 ───────── */

    private static void testAuditTexts() {
        String audit = ComplianceGuard.privacyAudit();
        TestHost.check(audit != null && audit.length() > 100, "权限审计非空（>100 字符）");
        TestHost.check(audit.contains("INTERNET") && audit.contains("POST_NOTIFICATIONS"),
                "审计含 INTERNET / POST_NOTIFICATIONS");
        TestHost.check(audit.contains("无障碍") && audit.contains("BIND_ACCESSIBILITY_SERVICE"),
                "审计含无障碍服务声明");
        TestHost.check(audit.contains("零权限声明") || audit.contains("未申请"), "审计含零权限声明边界");
        TestHost.check(audit.contains("脱敏"), "审计含脱敏说明（日志上传边界）");
    }
}
