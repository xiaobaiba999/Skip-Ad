package com.skipad.formfit;

/**
 * 全形态自适应阈值（v5.4.0）：折叠屏 / 平板 / 分屏 / 手机四形态，所有几何阈值
 * 按实际窗口尺寸动态换算，杜绝「按整屏像素写死」导致的误判。
 *
 * <p>与 Web 端 src/lib/formfit.ts 保持 1:1（TS 为规范源；公式同构，CI 金样本集校验）：
 * compute(w, h, factor) ↔ adaptThresholds(geom)、classify(w, h, isSplit) ↔
 * describeForm / FoldState 分类、FormFitPolicy ↔ AdaptiveThresholds。
 *
 * <p>为什么必须按「实际窗口」而非整屏换算：
 * <ul>
 *   <li>分屏：半屏高度只有整屏一半，半屏插屏判定（35%~55%）若按整屏算会把
 *       正常半屏面板误判成广告</li>
 *   <li>折叠屏展开态：方形屏（宽高比 ≈1.0~1.1）的角落区若按长边收缩会偏大，
 *       误吃边缘正常按钮</li>
 *   <li>平板：宽高比 &lt;1.6（4:3 类），同屏面积下按钮普遍更大，跳过键面积上限
 *       必须收紧，否则大按钮营销控件会漏过面积安全阀</li>
 * </ul>
 *
 * <p>输入约定：所有像素均为「实际窗口」坐标系（WindowManager currentWindowMetrics
 * 或 Configuration.windowConfiguration 对应 bounds），由调用方（SkipAdService）
 * 换算后传入；本类不持有 Android 依赖，JVM 可直接单测（全形态 44 项断言）。
 */
public final class FormFitThresholds {

    private FormFitThresholds() {
    }

    // ───────────────────────── 形态分类 ─────────────────────────

    /** 窗口形态 */
    public enum FormFactor {
        PHONE("直板手机"), FOLD_UNFOLDED("折叠屏展开"), TABLET("平板"), SPLIT_SCREEN("分屏");

        public final String label;

        FormFactor(String label) {
            this.label = label;
        }
    }

    /**
     * 形态分类：
     * <pre>
     * isSplitScreen                 → SPLIT_SCREEN（优先级最高，几何按实际窗口）
     * aspect &lt; 1.35                 → FOLD_UNFOLDED（方形内屏特征）
     * min(w,h) ≥ 600dp 且非折叠     → TABLET（调用方传 dp 换算后的 px；见 javadoc 约定）
     * 其余                          → PHONE
     * </pre>
     */
    public static FormFactor classify(int windowW, int windowH, boolean isSplitScreen) {
        if (isSplitScreen) {
            return FormFactor.SPLIT_SCREEN;
        }
        if (windowW <= 0 || windowH <= 0) {
            return FormFactor.PHONE;
        }
        float aspect = (float) Math.max(windowW, windowH) / Math.min(windowW, windowH);
        if (aspect < 1.35f) {
            return FormFactor.FOLD_UNFOLDED;
        }
        if (Math.min(windowW, windowH) >= 600) {
            return FormFactor.TABLET;
        }
        return FormFactor.PHONE;
    }

    // ───────────────────────── 自适应阈值 ─────────────────────────

    /** 全形态阈值包（一次 compute，窗口生命周期内复用） */
    public static final class FormFitPolicy {
        /** 角落区边长（px）：角落图片跳过键（L4）与角落点击判定区 */
        public final int cornerRegionPx;
        /** 跳过键面积下限（占窗口比例；防零面积假节点） */
        public final float skipAreaMinPct;
        /** 跳过键面积上限（面积安全阀；平板收紧） */
        public final float skipAreaMaxPct;
        /** 宽高比下限/上限（形状门，防全屏热区伪装跳过键） */
        public final float skipAspectMin;
        public final float skipAspectMax;
        /** 全屏热区直断下限（≥55% 可点面积才允许热区直断，守护名单豁免除外） */
        public final float hotZoneMinPct;
        /** 半屏插屏高度区间（占实际窗口高度比例） */
        public final float halfScreenMinRatio;
        public final float halfScreenMaxRatio;
        /** 悬浮广告球直径上限（占窗口宽度比例） */
        public final float floatBallMaxDiameterPct;
        /** 形态（调试与日志） */
        public final FormFactor factor;

        FormFitPolicy(int cornerRegionPx, float skipAreaMinPct, float skipAreaMaxPct,
                      float skipAspectMin, float skipAspectMax, float hotZoneMinPct,
                      float halfScreenMinRatio, float halfScreenMaxRatio,
                      float floatBallMaxDiameterPct, FormFactor factor) {
            this.cornerRegionPx = cornerRegionPx;
            this.skipAreaMinPct = skipAreaMinPct;
            this.skipAreaMaxPct = skipAreaMaxPct;
            this.skipAspectMin = skipAspectMin;
            this.skipAspectMax = skipAspectMax;
            this.hotZoneMinPct = hotZoneMinPct;
            this.halfScreenMinRatio = halfScreenMinRatio;
            this.halfScreenMaxRatio = halfScreenMaxRatio;
            this.floatBallMaxDiameterPct = floatBallMaxDiameterPct;
            this.factor = factor;
        }
    }

    /**
     * 阈值换算公式（与 docs/OPTIMIZATION-v5.4.0.md §2.2 表格一致）：
     * <pre>
     * 基准：minSide = min(w, h)，aspect = max(w,h)/min(w,h)
     *
     * cornerRegionPx:
     *   PHONE        = round(0.12 × minSide)          // 基准
     *   FOLD_UNFOLDED= round(0.09 × w)                // 方形屏按宽收缩（防误吃边缘按钮）
     *   TABLET       = round(0.10 × minSide)
     *   SPLIT_SCREEN = round(0.12 × minSide)          // 按实际窗口（非整屏）
     *
     * skipAreaMaxPct（面积安全阀）:
     *   PHONE 0.35 | FOLD_UNFOLDED 0.32 | TABLET 0.28（aspect&lt;1.6 收紧）| SPLIT 0.35（按实际窗口面积）
     * skipAreaMinPct = 0.0005（0.05%）
     * skipAspect ∈ [0.6, 40]（形状门）
     * hotZoneMinPct = 0.55
     * halfScreenRatio ∈ [0.35, 0.55]（按实际窗口高度，分屏防误判关键）
     * floatBallMaxDiameterPct = 0.12（按实际窗口宽度）
     * </pre>
     */
    public static FormFitPolicy compute(int windowW, int windowH, FormFactor factor) {
        if (windowW <= 0 || windowH <= 0) {
            // 防御：几何未知时按直板基准（0 尺寸窗口本就不该进入判定）
            windowW = Math.max(1, windowW);
            windowH = Math.max(1, windowH);
        }
        int minSide = Math.min(windowW, windowH);
        int corner;
        float areaMax;
        switch (factor) {
            case FOLD_UNFOLDED:
                corner = Math.round(0.09f * windowW); // 方形屏按宽收缩
                areaMax = 0.32f;
                break;
            case TABLET:
                corner = Math.round(0.10f * minSide);
                areaMax = 0.28f; // 宽高比<1.6 收紧
                break;
            case SPLIT_SCREEN:
                corner = Math.round(0.12f * minSide); // 按实际窗口
                areaMax = 0.35f;                      // 分母是实际窗口面积
                break;
            case PHONE:
            default:
                corner = Math.round(0.12f * minSide);
                areaMax = 0.35f;
                break;
        }
        return new FormFitPolicy(
                corner,
                0.0005f,
                areaMax,
                0.6f,
                40f,
                0.55f,
                0.35f,
                0.55f,
                0.12f,
                factor);
    }

    // ───────────────────────── 判定 API ─────────────────────────

    /** 节点是否落在四角角落区（角落图片跳过键候选） */
    public static boolean inCornerRegion(int[] nodeBounds, FormFitPolicy p, int winW, int winH) {
        if (invalidBounds(nodeBounds) || winW <= 0 || winH <= 0) {
            return false;
        }
        int c = p.cornerRegionPx;
        boolean nearLeft = nodeBounds[0] <= c;
        boolean nearRight = nodeBounds[2] >= winW - c;
        boolean nearTop = nodeBounds[1] <= c;
        boolean nearBottom = nodeBounds[3] >= winH - c;
        return (nearLeft || nearRight) && (nearTop || nearBottom);
    }

    /**
     * 形状门（李跳跳式，全形态换算）：候选跳过键必须同时过——
     * <ol>
     *   <li>面积 ∈ [skipAreaMinPct, skipAreaMaxPct]（面积安全阀，防全屏热区）</li>
     *   <li>宽高比 ∈ [skipAspectMin, skipAspectMax]（防横幅热区/细条伪装）</li>
     * </ol>
     */
    public static boolean shapeGate(int[] nodeBounds, FormFitPolicy p, int winW, int winH) {
        if (invalidBounds(nodeBounds) || winW <= 0 || winH <= 0) {
            return false;
        }
        long w = Math.max(0, nodeBounds[2] - nodeBounds[0]);
        long h = Math.max(0, nodeBounds[3] - nodeBounds[1]);
        if (w <= 0 || h <= 0) {
            return false;
        }
        double areaPct = (w * (double) h) / ((double) winW * winH);
        if (areaPct < p.skipAreaMinPct || areaPct > p.skipAreaMaxPct) {
            return false;
        }
        float aspect = (float) w / (float) h;
        return aspect >= p.skipAspectMin && aspect <= p.skipAspectMax;
    }

    /**
     * 半屏插屏形态判定（优量汇 HalfScreenExpressAd / 穿山甲 TTHalfScreenAd）：
     * 高度占实际窗口 35%~55% 且底部锚定（底边贴窗口下沿）。
     */
    public static boolean isHalfScreenAnchored(int[] windowBounds, FormFitPolicy p,
                                               int hostW, int hostH) {
        if (invalidBounds(windowBounds) || hostH <= 0) {
            return false;
        }
        long h = windowBounds[3] - windowBounds[1];
        if (h <= 0) {
            return false;
        }
        float ratio = h / (float) hostH;
        boolean bottomAnchored = windowBounds[3] >= hostH - Math.round(hostH * 0.02f);
        return ratio >= p.halfScreenMinRatio && ratio <= p.halfScreenMaxRatio && bottomAnchored;
    }

    /** 悬浮广告球尺寸判定（直径 ≤12% 窗宽的小尺寸 overlay 窗） */
    public static boolean isFloatBallSized(int[] windowBounds, FormFitPolicy p, int hostW) {
        if (invalidBounds(windowBounds) || hostW <= 0) {
            return false;
        }
        int w = windowBounds[2] - windowBounds[0];
        int h = windowBounds[3] - windowBounds[1];
        int diameter = Math.max(w, h);
        return diameter > 0 && diameter <= Math.round(hostW * p.floatBallMaxDiameterPct);
    }

    /** 全屏热区直断资格（≥55% 可点面积 + 节点 ≤12 + 无输入框，守护名单豁免） */
    public static boolean hotZoneEligible(int[] nodeBounds, FormFitPolicy p, int winW, int winH) {
        if (invalidBounds(nodeBounds) || winW <= 0 || winH <= 0) {
            return false;
        }
        long w = Math.max(0, nodeBounds[2] - nodeBounds[0]);
        long h = Math.max(0, nodeBounds[3] - nodeBounds[1]);
        double areaPct = (w * (double) h) / ((double) winW * winH);
        return areaPct >= p.hotZoneMinPct;
    }

    private static boolean invalidBounds(int[] b) {
        return b == null || b.length < 4 || b[2] <= b[0] || b[3] <= b[1];
    }
}
