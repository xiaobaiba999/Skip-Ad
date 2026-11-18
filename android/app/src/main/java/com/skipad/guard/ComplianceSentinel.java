package com.skipad.guard;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 合规边界自检哨兵（v5.4.0）：所有引擎动作出手前的最后一道闸。
 *
 * <p>与 Web 端 src/lib/compliance.ts 保持 1:1（TS 为规范源；auditAction 同名同参语义、
 * ACTION_BOUNDARY ↔ ACTION 常量、POSITIVE_CTA ↔ POSITIVE 词库、四大原则 ↔ COMPLIANCE
 * 常量组）；审计事件经 {@link AuditSink} 落结构化日志（供权限透明化审计与看板展示）。
 *
 * <p><b>四大原则</b>（常量对外公开，文档与设置页直接引用）：
 * <ol>
 *   <li>{@link #PRINCIPLE_MINIMAL_INTERVENTION} 最小干预</li>
 *   <li>{@link #PRINCIPLE_BETTER_MISS_THAN_FALSE} 宁漏勿误</li>
 *   <li>{@link #PRINCIPLE_ACTION_TRANSPARENCY} 动作透明</li>
 *   <li>{@link #PRINCIPLE_DATA_MINIMIZATION} 数据最小化</li>
 * </ol>
 */
public final class ComplianceSentinel {

    private ComplianceSentinel() {
    }

    // ───────────────────────── 四大原则 ─────────────────────────

    public static final String PRINCIPLE_MINIMAL_INTERVENTION =
            "最小干预：只处理广告与诱导场景，用户正在浏览的内容零干预";
    public static final String PRINCIPLE_BETTER_MISS_THAN_FALSE =
            "宁漏勿误：证据不足不动作，双信号不全不动手";
    public static final String PRINCIPLE_ACTION_TRANSPARENCY =
            "动作透明：每次代点可解释、可审计、可回滚，审计日志全量落盘";
    public static final String PRINCIPLE_DATA_MINIMIZATION =
            "数据最小化：日志脱敏、同步聚合、不留用户内容，学习数据全链路脱敏";

    // ───────────────────────── 动作边界白名单 ─────────────────────────

    /**
     * ACTION_BOUNDARY 白名单：引擎允许执行的全部动作类型。
     * 白名单之外的动作（传感器挂起/意图拦截等设备级动作）不允许由无障碍通道触发。
     */
    public static final Set<String> ACTION_BOUNDARY = new HashSet<>(Arrays.asList(
            "instant_skip",      // 秒级代点跳过
            "coord_tap",         // 角落控件/坐标级点击
            "popup_close",       // 弹窗代点关闭
            "halfsheet_close",   // 半屏插屏代点关闭
            "decline_tap",       // 诱导弹窗代点消极按钮
            "preroll_skip",      // 前贴到点即跳
            "guard_skip",        // 守护模式温和代点
            "sdk_window_cut",    // SDK 广告窗直断（返回键/关闭）
            "back_fallback",     // 返回键兜底（仅应用内窗）
            "overlay_notice",    // 悬浮窗识别提示（零点击）
            "mark_only",         // 信息流标注（零点击）
            "preempt_block",     // P0 预拦截（学习指纹）
            "zero_scan",         // 敏感零扫描（无动作）
            "clean_skip"         // CLEAN 档案跳过（无动作）
    ));

    /**
     * 积极按钮词库（更新诱导弹窗专项）：目标控件文本命中即拒绝执行——
     * 只允许点消极按钮（以后再说/暂不更新/取消），积极按钮永不触碰。
     */
    public static final Pattern POSITIVE_CTA = Pattern.compile(
            "立即更新|立即抢购|立即下载|立即开通|马上升级|一键安装|立即安装|"
                    + "立即支付|确认支付|立即领取|立即购买|立即体验|开通会员|马上抢");

    /** 消极按钮词库（decline_tap 唯一允许的点击目标） */
    public static final Set<String> NEGATIVE_BUTTONS = new HashSet<>(Arrays.asList(
            "以后再说", "暂不更新", "暂不升级", "取消", "关闭", "残忍拒绝", "下次再说", "暂不"
    ));

    /** 面积安全阀：代点目标面积占窗口比例上限（35%，全形态由 FormFitThresholds 收紧） */
    public static final float AREA_LIMIT_PCT = 0.35f;

    /** 坐标级点击目标面积上限（角落键更小，12%） */
    public static final float COORD_AREA_LIMIT_PCT = 0.12f;

    // ───────────────────────── 审计输入/输出 ─────────────────────────

    /** 动作审计输入 */
    public static final class AuditInput {
        public String action;         // 待执行动作（ACTION_BOUNDARY 内）
        public String targetText;     // 目标控件文本（脱敏前）
        public float areaPct;         // 目标面积占窗口比例（0~1）
        public boolean editable;      // 目标是输入类控件
        public boolean passwordNearby; // 目标附近存在密码框
        public boolean systemConfirm; // 目标是系统确认框（10 秒警告类）
        public String ruleId;         // 来源规则（审计留痕）
    }

    /** 审计结论 */
    public static final class AuditResult {
        public final boolean allowed;
        public final String reason;
        public final String principle; // 拒绝时违反的原则

        private AuditResult(boolean allowed, String reason, String principle) {
            this.allowed = allowed;
            this.reason = reason;
            this.principle = principle;
        }

        static AuditResult allow(String reason) {
            return new AuditResult(true, reason, null);
        }

        static AuditResult reject(String reason, String principle) {
            return new AuditResult(false, reason, principle);
        }
    }

    /** 审计落盘出口（SkipAdService 接 StructuredLogger；单测接内存断言） */
    public interface AuditSink {
        void onAudit(AuditResult result, AuditInput input);
    }

    // ───────────────────────── 审计主逻辑 ─────────────────────────

    /**
     * 动作边界自检清单（顺序执行，任一不过即拒绝并写审计日志）：
     * <ol>
     *   <li>动作类型 ∈ ACTION_BOUNDARY 白名单</li>
     *   <li>系统确认框（10 秒警告类）绝不自动点——只引导用户</li>
     *   <li>目标文本命中积极词库（立即更新/立即抢购/下载/开通…）→ 拒绝</li>
     *   <li>decline_tap 只允许点消极按钮词库内目标</li>
     *   <li>面积安全阀：普通代点 ≤35%，坐标级 ≤12%（全形态阈值取更严者）</li>
     *   <li>目标非输入类控件、附近无密码框</li>
     * </ol>
     */
    public static AuditResult auditAction(AuditInput input, AuditSink sink) {
        AuditResult result = doAudit(input);
        if (sink != null) {
            sink.onAudit(result, input);
        }
        return result;
    }

    private static AuditResult doAudit(AuditInput in) {
        if (in == null || in.action == null) {
            return AuditResult.reject("null-action", PRINCIPLE_BETTER_MISS_THAN_FALSE);
        }
        // 1) 白名单
        if (!ACTION_BOUNDARY.contains(in.action)) {
            return AuditResult.reject("action-out-of-boundary:" + in.action,
                    PRINCIPLE_MINIMAL_INTERVENTION);
        }
        // 零动作类（识别/跳过类）直接放行（无点击行为）
        if ("zero_scan".equals(in.action) || "clean_skip".equals(in.action)
                || "mark_only".equals(in.action) || "overlay_notice".equals(in.action)) {
            return AuditResult.allow("no-click-action");
        }
        // 2) 系统确认框红线
        if (in.systemConfirm) {
            return AuditResult.reject("system-confirm-dialog-never-autoclick",
                    PRINCIPLE_MINIMAL_INTERVENTION);
        }
        // 3) 积极词库（更新诱导弹窗只点消极按钮）
        if (in.targetText != null && POSITIVE_CTA.matcher(in.targetText).find()) {
            return AuditResult.reject("positive-cta-target:" + mask(in.targetText),
                    PRINCIPLE_MINIMAL_INTERVENTION);
        }
        // 4) decline_tap 目标限定
        if ("decline_tap".equals(in.action)
                && (in.targetText == null || !NEGATIVE_BUTTONS.contains(in.targetText.trim()))) {
            return AuditResult.reject("decline-target-not-negative:" + mask(in.targetText),
                    PRINCIPLE_MINIMAL_INTERVENTION);
        }
        // 5) 面积安全阀
        float limit = "coord_tap".equals(in.action) ? COORD_AREA_LIMIT_PCT : AREA_LIMIT_PCT;
        if (in.areaPct > limit) {
            return AuditResult.reject("area-over-limit:" + in.areaPct + ">" + limit,
                    PRINCIPLE_MINIMAL_INTERVENTION);
        }
        // 6) 输入类目标 / 密码邻近
        if (in.editable) {
            return AuditResult.reject("editable-target", PRINCIPLE_MINIMAL_INTERVENTION);
        }
        if (in.passwordNearby) {
            return AuditResult.reject("password-nearby", PRINCIPLE_BETTER_MISS_THAN_FALSE);
        }
        return AuditResult.allow("pass");
    }

    /** 审计日志脱敏（与 StructuredLogger 同口径：text 截断 32 字符） */
    static String mask(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 32 ? text : text.substring(0, 32) + "...";
    }

    /** 动作边界自检清单文本（设置页「权限透明化审计」展示） */
    public static String boundaryChecklist() {
        return "1. 动作类型白名单（" + ACTION_BOUNDARY.size() + " 类），白名单外不执行\n"
                + "2. 系统确认框（含无障碍 10 秒警告）绝不自动点，只引导\n"
                + "3. 积极按钮（立即更新/立即抢购/下载/开通…）永不触碰\n"
                + "4. 更新弹窗只代点消极按钮（以后再说/暂不更新/取消）\n"
                + "5. 面积安全阀：普通代点 ≤35% 窗口面积，坐标级 ≤12%\n"
                + "6. 输入类控件与密码邻近目标一律拒绝\n"
                + "7. 全部审计事件结构化落盘（text 截断 32 字符，pkg 保留）";
    }
}
