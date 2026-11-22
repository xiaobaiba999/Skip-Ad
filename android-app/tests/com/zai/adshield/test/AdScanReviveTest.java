package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.PowerGovernor;
import com.zai.adshield.core.RuleEngine;

/**
 * v8.1.0 扫描复活与真机根因修复回归套件（真机反馈「很多 App 跳不过」）。
 * ─────────────────────────────────────────────────────────
 * 覆盖四组修复的可 JVM 回归面（服务侧接线由编译+既有套件回归；真机结论
 * 一律 REAL_DEVICE=NOT_RUN，本套件只钉死纯函数语义）：
 *  S1「习惯学习」开关误门控（根因一）——服务侧接线行为：splashLive 不再
 *     受 learningEnabled() 门控。可测面：RuleEngine 核心跳过词与「关闭」
 *     裸词的分层语义（关闭绝不进 L1 跳过词表——它必须走 L1.6 关闭键
 *     证据门路径，防止后续维护者把它加进 TEXT_EXACT 造成孤立词误点）；
 *  S2 CLEAN 失明修复（根因二）——isCloseText 等值词表（关闭/×/✕ 等）+
 *     复查期 24h 钉死 + CLEAN_STREAK_MIN 钉死；
 *  S3 零候选退避豁免（根因三）——zeroCandExempt 六豁免真值表（原五豁免
 *     语义不变 + 第六豁免 rootWindow && !subCovered）；
 *  S4 探针词与关闭线索的分层——关闭词族 isCloseText 与跳过词族
 *     TEXT_EXACT_SET 互不越界（安全分层红线）。
 */
public class AdScanReviveTest {

    public static void main(String[] args) {
        TestHost.suite("AdScanReviveTest");

        s1_textExactLayering();
        s2_cleanProfileConstants();
        s3_zeroCandExempt();
        s4_closeTextLayering();

        TestHost.done();
    }

    /* S1+S4：跳过词表分层红线——「关闭」裸词绝不进全局跳过精确表。
     * 「关闭」语义由 L1.6 关闭键路径处理（isCloseText 收集 + 角标/顶部区/
     * 广告形态语境门 + ComplianceGuard 终闸）；若进 TEXT_EXACT（L1 跳过
     * 路径），孤立「关闭」在部分语境门宽松窗（SDK 窗/学习窗）会直接出手
     * ——FalsePositiveScenarioTest S8 孤立词红线被击穿。 */
    private static void s1_textExactLayering() {
        TestHost.check(RuleEngine.textExactContains("跳过"), "S1a 跳过词在全局精确表（基线）");
        TestHost.check(RuleEngine.textExactContains("跳过广告"), "S1b 跳过广告在全局精确表（基线）");
        TestHost.check(RuleEngine.textExactContains("skip"), "S1c skip 在全局精确表（基线）");
        TestHost.check(RuleEngine.textExactContains("关闭广告"), "S1d 关闭广告（广告语境词）在全局精确表（基线）");
        TestHost.check(!RuleEngine.textExactContains("关闭"),
                "S1e 关闭【裸词】不在全局跳过精确表——必须走 L1.6 关闭键证据门（v8.1.0 安全分层红线）");
        TestHost.check(!RuleEngine.textExactContains("×"),
                "S1f × 不在全局跳过精确表——同上（关闭词族走关闭键路径）");
        TestHost.check(!RuleEngine.textExactContains("取消"),
                "S1g 取消不在全局跳过精确表（否定词防误点基线）");
    }

    /* S2：CLEAN 档案常量钉死（v8.1.0 由 72h 收紧到 24h——CLEAN 失明窗上限）。
     * CLEAN_STREAK_MIN=3 保持（判定阈值不变——只收紧复查期）。 */
    private static void s2_cleanProfileConstants() {
        TestHost.checkEq(24L * 3600 * 1000, PowerGovernor.PROFILE_RECHECK_MS,
                "S2a CLEAN 复查期 = 24h（v8.1.0 收紧；72h→24h 真机根因之二修复）");
        TestHost.check(PowerGovernor.PROFILE_RECHECK_MS < 72L * 3600 * 1000,
                "S2b 复查期严格小于旧版 72h（防回退钉死）");
        TestHost.checkEq(3, PowerGovernor.CLEAN_STREAK_MIN,
                "S2c CLEAN 判定阈值 = 3 窗零候选（语义不变）");
        // 有订阅覆盖的 CLEAN 窗保留低频梯子（vid 规则探针够不到 → 梯子兜底）
        TestHost.check(PowerGovernor.LADDER_CLEAN_SUB.length == 2
                        && PowerGovernor.LADDER_CLEAN_SUB[0] == 0L,
                "S2d 订阅覆盖 CLEAN 窗保留 {0,700} 低频档（基线不变）");
    }

    /* S3：零候选退避豁免六真值表——前五豁免语义与 v7.x 逐字等价，第六豁免
     * v8.1.0 新增（第一方入口窗 + 无订阅覆盖 → 不提前杀链，梯子跑满 12s；
     * 有订阅覆盖不豁免——订阅路径 {0,700} 已保障，避免功耗回退）。 */
    private static void s3_zeroCandExempt() {
        TestHost.check(AdDecisionCore.zeroCandExempt(true, false, false, false, false, false, false),
                "S3a SDK 广告窗类豁免（基线）");
        TestHost.check(AdDecisionCore.zeroCandExempt(false, true, false, false, false, false, false),
                "S3b Dialog 窗豁免（基线）");
        TestHost.check(AdDecisionCore.zeroCandExempt(false, false, true, false, false, false, false),
                "S3c 视频画像豁免（基线）");
        TestHost.check(AdDecisionCore.zeroCandExempt(false, false, false, true, false, false, false),
                "S3d arm-hint 豁免（基线）");
        TestHost.check(AdDecisionCore.zeroCandExempt(false, false, false, false, true, false, false),
                "S3e 学习指纹豁免（基线）");
        TestHost.check(AdDecisionCore.zeroCandExempt(false, false, false, false, false, true, false),
                "S3f 第一方入口窗+无订阅覆盖豁免（v8.1.0 新增——晚出键 3~12s 渲染不再被 2.6s 杀链误杀）");
        TestHost.check(!AdDecisionCore.zeroCandExempt(false, false, false, false, false, true, true),
                "S3g 第一方入口窗+有订阅覆盖【不】豁免（订阅 {0,700} 路径已保障——功耗不回退）");
        TestHost.check(!AdDecisionCore.zeroCandExempt(false, false, false, false, false, false, false),
                "S3h 普通窗零豁免（退避照杀——功耗红线不变）");
        TestHost.check(!AdDecisionCore.zeroCandExempt(false, false, false, false, false, false, true),
                "S3i 非入口窗+有订阅覆盖不豁免（基线语义）");
        // 原内联五豁免语义等价性：SDK 窗 + rootWindow 组合（任一命中即豁免）
        TestHost.check(AdDecisionCore.zeroCandExempt(true, false, false, false, false, false, true),
                "S3j 多豁免叠加（SDK 窗优先命中）");
    }

    /* S4：关闭词族 isCloseText 等值语义（CLEAN 失明修复的「关闭线索」探测
     * 依赖它——contains 噪声由等值门滤除）。 */
    private static void s4_closeTextLayering() {
        TestHost.check(AdDecisionCore.isCloseText("关闭"), "S4a 关闭等值命中（CLEAN 线索词）");
        TestHost.check(AdDecisionCore.isCloseText("×"), "S4b × 等值命中（CLEAN 线索词）");
        TestHost.check(AdDecisionCore.isCloseText("✕"), "S4c ✕ 等值命中（CLEAN 线索词）");
        TestHost.check(AdDecisionCore.isCloseText("x"), "S4d x（小写）等值命中");
        TestHost.check(AdDecisionCore.isCloseText("close"), "S4e close 等值命中");
        TestHost.check(AdDecisionCore.isCloseText("关闭广告"), "S4f 关闭广告等值命中（词族成员）");
        TestHost.check(!AdDecisionCore.isCloseText("关闭应用？"), "S4g contains 噪声不命中（等值门——「关闭应用？」非关闭键）");
        TestHost.check(!AdDecisionCore.isCloseText("暂时关闭不了"), "S4h 长句噪声不命中（等值门）");
        TestHost.check(!AdDecisionCore.isCloseText("跳过广告 查看详情"), "S4i 诱导长文案不命中（诱导词防线）");
        TestHost.check(AdDecisionCore.isCloseText(null) == false, "S4j null 安全");
        TestHost.check(AdDecisionCore.isCloseText("  关闭  "), "S4k 首尾空白容忍（trim 语义）");
    }
}
