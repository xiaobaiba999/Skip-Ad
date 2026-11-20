package com.zai.adshield.core;

/**
 * 点击决策证据（v7.9.5 · 纯 Java · 零 Android 依赖 · JVM 可单测）——
 * 「真实世界证据层」的最小结构化证据记录器。
 * ─────────────────────────────────────────────────────────────
 * 用户指令（v7.9.5 第一/第二优先级）：
 *  · 每次准备 CLICK 前记录【轻量级决策证据】——包/Activity/任务ID/代际/
 *    任务状态 · 候选来源（text/contentDescription/resourceId/imageCorner）
 *    · 上下文（adContext/sdkContext/webViewContext/subscriptionContext）
 *    · 几何（position/size/screenRegion/clickable）· 安全（SafetyGuard
 *    裁决 / finalDecision）；
 *  · 点击后的结果闭环——CLICK_DISPATCHED → VERIFYING → 六态终局
 *    （VERIFIED_SUCCESS / RETRY / MISS / FALSE_CLICK / SAFE_BLOCK /
 *    UNKNOWN）——dispatchGesture 成功 ≠ 广告关闭成功。
 *
 * ══ 隐私红线（与用户指令一一对应 · 本模块结构性强制）══
 *  · 绝不保存完整 Accessibility Tree（本模块没有任何树容器/节点引用）；
 *  · 绝不上传原始屏幕截图（本模块零位图/字节引用）；
 *  · 绝不保存账号、手机号、支付信息、聊天内容（候选标签只保留
 *    close/skip 词族成员——sanitizeToken 白名单闸，其余一律空串；
 *    resourceId 只保留 ≤32 字符的开发者资源名，超长一律空串）；
 *  · 默认只保留最小结构化证据：环形缓冲有界（BUFFER_MAX 条），
 *    进程内驻留，不上传原始节点内容。
 *
 * 与 AdTaskCore 同规约：纯函数+有界静态状态，ECJ -1.8 编译 JVM 可测；
 * 服务侧薄接线（AdSkipService execute/markTask* 三路汇合）。
 */
public final class DecisionEvidence {

    private DecisionEvidence() {
    }

    /* ══ 候选来源（字符串枚举——日志/JSON 直接可读）══ */
    public static final String SRC_TEXT = "text";
    public static final String SRC_DESC = "contentDescription";
    public static final String SRC_RESID = "resourceId";
    public static final String SRC_IMAGE = "imageCorner";

    /* ══ 点击结果六态（用户指令词表 · 严格区分 dispatch 成功与广告关闭）══ */
    public static final int OUT_VERIFIED_SUCCESS = 0; // 广告确认关闭（两轮零候选/窗口切换/反馈面板收起）
    public static final int OUT_RETRY = 1;           // 复验仍在场 → 合法重试等待（预算钳制）
    public static final int OUT_MISS = 2;            // 预算用尽未关闭（优雅收手）
    public static final int OUT_FALSE_CLICK = 3;     // 误触（真机样本信号——自动化环境恒 0，绝不虚报）
    public static final int OUT_SAFE_BLOCK = 4;      // SafetyGuard 终闸拦截（宁漏勿误闸在岗证据）
    public static final int OUT_UNKNOWN = 5;         // 无法归类（诚实占位——绝不硬凑）
    public static final String[] OUTCOME_NAMES = {
            "VERIFIED_SUCCESS", "RETRY", "MISS", "FALSE_CLICK", "SAFE_BLOCK", "UNKNOWN",
    };

    /** 环形缓冲容量（有界——防内存膨胀，只留最近若干条供诊断）。 */
    public static final int BUFFER_MAX = 12;
    /** 候选标签白名单长度上限（close/skip 词族本身都远短于此）。 */
    public static final int LABEL_MAX_CHARS = 12;
    /** resourceId 保留上限（开发者资源名；超长一律拒存）。 */
    public static final int RESID_MAX_CHARS = 32;

    /* ══ 证据记录（最小结构化字段——全部基本类型/短字符串）══ */
    public static final class Evidence {
        public long at;             // 记录时刻（诊断排序用）
        public String pkg = "";
        public String act = "";     // activityId
        public String task = "";    // 任务ID（winStart#seq）
        public int gen = -1;        // 扫描代际
        public String st = "";     // 派发时任务状态名
        public String src = SRC_IMAGE; // 候选来源
        public String reason = "";  // 决策 reason（引擎内部词表——非用户内容）
        public String label = "";  // 候选标签（sanitizeToken 白名单后——非词族一律空串）
        public String resid = "";  // resourceId（≤32 字符）
        public boolean adCtx;      // 广告上下文（角标/Dialog 形态/半屏插屏）
        public boolean sdkCtx;    // SDK 广告窗指纹
        public boolean wvCtx;     // WebView 容器在场
        public boolean subCtx;    // 订阅规则路径（rule_sub_gkd*）
        public double cx;          // 归一化中心横坐标（0~1）
        public double cy;          // 归一化中心纵坐标（0~1）
        public double area;        // 归一化面积（0~1）
        public String region = "";// 屏幕区域（TL/TR/BL/BR/MID）
        public boolean clk;        // 候选可点
        public String safe = "";   // SafetyGuard 裁决（OK/BND_* 层名）
        public String fin = "";   // finalDecision（CLICK_DISPATCHED/SAFE_BLOCK）
    }

    /* ══ 有界状态（进程内驻留 · 零 IO）══ */
    private static final Evidence[] buffer = new Evidence[BUFFER_MAX];
    private static int bufferHead = 0; // 环形写指针
    private static int bufferCount = 0;
    private static final int[] outcomes = new int[6];

    /**
     * 候选标签隐私闸：只有 close/skip 词族成员（且 ≤LABEL_MAX_CHARS）才保留，
     * 其余一律空串——账号/手机号/支付/聊天内容在结构上无法进入证据层。
     */
    public static String sanitizeToken(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.length() == 0 || t.length() > LABEL_MAX_CHARS) return "";
        // 白名单：关闭词族 / 跳过词族（与 AdDecisionCore.CLOSE_TEXTS/SKIP_TEXTS
        // 同源的词根匹配——证据层不引入第二套词表语义，只做成员资格判断）
        String[] closeFamily = AdDecisionCore.CLOSE_TEXTS;
        for (int i = 0; i < closeFamily.length; i++) {
            if (closeFamily[i].equals(t)) return t;
        }
        String[] skipFamily = AdDecisionCore.SKIP_TEXTS;
        for (int i = 0; i < skipFamily.length; i++) {
            if (skipFamily[i].equals(t)) return t;
        }
        return "";
    }

    /** resourceId 隐私闸：开发者资源名 ≤32 字符才保留。 */
    public static String sanitizeResId(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.length() == 0 || t.length() > RESID_MAX_CHARS) return "";
        // 资源名形态校验（字母数字._:$ 与 Android 资源 id 分隔符 / 之外一律
        // 拒——内容文本冒充资源名拒收）
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == ':' || c == '$' || c == '/';
            if (!ok) return "";
        }
        return t;
    }

    /** 屏幕区域（归一化中心）：四角带 + 中部。 */
    public static String regionOf(double cx, double cy) {
        boolean left = cx <= 0.30;
        boolean right = cx >= 0.70;
        boolean top = cy <= 0.22;
        boolean bottom = cy >= 0.78;
        if (top && left) return "TL";
        if (top && right) return "TR";
        if (bottom && left) return "BL";
        if (bottom && right) return "BR";
        return "MID";
    }

    /**
     * 记录一条点击决策证据（finalDecision=CLICK_DISPATCHED）。
     * 服务侧薄接线调用——任何异常由调用方吞掉（证据绝不影响点击链路）。
     */
    public static void recordDecision(String pkg, String act, String task, int gen, String st,
                                      String src, String reason, String rawLabel, String rawResId,
                                      boolean adCtx, boolean sdkCtx, boolean wvCtx, boolean subCtx,
                                      double cx, double cy, double area, boolean clk,
                                      String safe) {
        Evidence e = new Evidence();
        e.at = System.currentTimeMillis();
        e.pkg = nz(pkg);
        e.act = nz(act);
        e.task = nz(task);
        e.gen = gen;
        e.st = nz(st);
        e.src = nz(src);
        e.reason = nz(reason);
        e.label = sanitizeToken(rawLabel);   // 隐私闸：非 close/skip 词族一律空串
        e.resid = sanitizeResId(rawResId);   // 隐私闸：超长/非资源名形态一律空串
        e.adCtx = adCtx;
        e.sdkCtx = sdkCtx;
        e.wvCtx = wvCtx;
        e.subCtx = subCtx;
        e.cx = clamp01(cx);
        e.cy = clamp01(cy);
        e.area = clamp01(area);
        e.region = regionOf(e.cx, e.cy);
        e.clk = clk;
        e.safe = nz(safe);
        e.fin = "CLICK_DISPATCHED";
        buffer[bufferHead] = e;
        bufferHead = (bufferHead + 1) % BUFFER_MAX;
        bufferCount = bufferCount < BUFFER_MAX ? bufferCount + 1 : BUFFER_MAX;
    }

    /** 记录安全闸拦截（finalDecision=SAFE_BLOCK——宁漏勿误闸在岗证据）。 */
    public static void recordSafeBlock(String pkg, String reason, String safeLayer) {
        Evidence e = new Evidence();
        e.at = System.currentTimeMillis();
        e.pkg = nz(pkg);
        e.reason = nz(reason);
        e.src = nz(reason); // 拦截路径来源即 reason（无候选细节时不伪造）
        e.safe = nz(safeLayer);
        e.fin = "SAFE_BLOCK";
        buffer[bufferHead] = e;
        bufferHead = (bufferHead + 1) % BUFFER_MAX;
        bufferCount = bufferCount < BUFFER_MAX ? bufferCount + 1 : BUFFER_MAX;
    }

    /**
     * 记录点击结果终局（六态之一）。
     * FALSE_CLICK 在自动化环境恒 0——它只能由真机样本回传产生
     * （compatibility/samples/ 导入路径），绝不虚报。
     */
    public static void recordOutcome(int outcome) {
        if (outcome < 0 || outcome >= outcomes.length) return;
        outcomes[outcome]++;
    }

    /** 六态计数快照：[VERIFIED_SUCCESS, RETRY, MISS, FALSE_CLICK, SAFE_BLOCK, UNKNOWN]。 */
    public static int[] outcomeSnapshot() {
        return new int[]{
                outcomes[0], outcomes[1], outcomes[2], outcomes[3], outcomes[4], outcomes[5],
        };
    }

    /** 缓冲区当前条数（有界断言用——测试与日志自证）。 */
    public static int bufferSize() {
        return bufferCount;
    }

    /** 最近一条证据（无则 null——日志单行输出用，绝不全量倾倒）。 */
    public static Evidence latest() {
        if (bufferCount == 0) return null;
        int idx = (bufferHead - 1 + BUFFER_MAX) % BUFFER_MAX;
        return buffer[idx];
    }

    /** 最小结构化 JSON 单行（上传日志「点击证据」行——只输出最近一条，不倾倒缓冲）。 */
    public static String toJsonLine() {
        Evidence e = latest();
        StringBuilder b = new StringBuilder(160);
        b.append("{\"ev\":\"click_evidence\"");
        if (e == null) {
            b.append(",\"buf\":").append(bufferCount).append('}');
            return b.toString();
        }
        b.append(",\"pkg\":").append(AdDecisionCoreJson.q(e.pkg))
                .append(",\"act\":").append(AdDecisionCoreJson.q(e.act))
                .append(",\"task\":").append(AdDecisionCoreJson.q(e.task))
                .append(",\"gen\":").append(e.gen)
                .append(",\"st\":").append(AdDecisionCoreJson.q(e.st))
                .append(",\"src\":").append(AdDecisionCoreJson.q(e.src))
                .append(",\"reason\":").append(AdDecisionCoreJson.q(e.reason))
                .append(",\"ctx\":{\"ad\":").append(e.adCtx)
                .append(",\"sdk\":").append(e.sdkCtx)
                .append(",\"wv\":").append(e.wvCtx)
                .append(",\"sub\":").append(e.subCtx).append('}')
                .append(",\"geo\":{\"cx\":").append(round3(e.cx))
                .append(",\"cy\":").append(round3(e.cy))
                .append(",\"area\":").append(round4(e.area))
                .append(",\"reg\":").append(AdDecisionCoreJson.q(e.region))
                .append(",\"clk\":").append(e.clk).append('}')
                .append(",\"safe\":").append(AdDecisionCoreJson.q(e.safe))
                .append(",\"fin\":").append(AdDecisionCoreJson.q(e.fin))
                .append('}');
        return b.toString();
    }

    /** 上传日志「点击结果」计数行（六态——与订阅七分类行同风格）。 */
    public static String outcomeJson() {
        StringBuilder b = new StringBuilder(96);
        b.append("{\"clickOutcomes\":{");
        for (int i = 0; i < OUTCOME_NAMES.length; i++) {
            if (i > 0) b.append(',');
            b.append(AdDecisionCoreJson.q(OUTCOME_NAMES[i])).append(':').append(outcomes[i]);
        }
        b.append("}}");
        return b.toString();
    }

    /** JVM 测试钩子：清零（生产永不调用——环形缓冲与计数器是进程内诊断状态）。 */
    public static void resetForJvm() {
        for (int i = 0; i < BUFFER_MAX; i++) buffer[i] = null;
        bufferHead = 0;
        bufferCount = 0;
        for (int i = 0; i < outcomes.length; i++) outcomes[i] = 0;
    }

    /* ══ 内部工具 ══ */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /* JSON 字符串引号工具（独立小类——避免与 AdDecisionCore 内部工具耦合）。 */
    static final class AdDecisionCoreJson {
        static String q(String s) {
            if (s == null) return "\"\"";
            StringBuilder b = new StringBuilder(s.length() + 8);
            b.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c == '\n') b.append("\\n");
                else if (c == '\r') b.append("\\r");
                else if (c < 0x20) b.append(' ');
                else b.append(c);
            }
            b.append('"');
            return b.toString();
        }
    }
}
