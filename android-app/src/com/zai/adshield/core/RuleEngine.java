package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.NodeView;

/**
 * 内嵌规则引擎（v3.10.0 · 规则库大扩充 · 全部内嵌零云端）
 * ─────────────────────────────────────────────────────────────
 * 设计红线（绝不乱点）：
 *  · 规则只认【控件属性】（text / desc / viewId），点击的是控件自身
 *    bounds 中心——bounds 由应用按实际屏幕渲染，天然适配任意分辨率，
 *    绝无「固定屏幕坐标」（每台手机屏幕大小不一样，位置必然不一样）；
 *  · 规则内嵌 APK，零网络依赖零云端下发；
 *  · 事件驱动 0ms 出手：窗口/内容一变立即查规则，命中即点——「露头就秒跳」。
 *
 * v3.10.0 规则库五层结构（网络验证好用规则全部内嵌）：
 *  · 第0层 分应用规则表（28 款主流应用真实跳过文案，包名前缀匹配，
 *    精确等值——不受全局长度上限约束）；
 *  · 第1层 全局精确文案：跳过 / 跳过广告 / 跳过此广告 / 跳过这个广告 /
 *    关闭广告 / 点击跳过 / 上滑跳过 / 跳过提示 / 立即跳过 / 跳过 AD /
 *    skip / skip ad / skip this ad / 繁体全族（跳過 / 跳過廣告）——
 *    大小写归一（SKIP/Skip/skip 同一条规则），命中即 0ms 出手；
 *  · 第2层 倒计时形态（严格正则）：跳过5 / 跳过广告 3 / 3s后可跳过 /
 *    5 秒后可跳过广告——形态由「跳过系词 + 纯数字/秒表」构成，任何营销
 *    话术都无法混入（数学上排除诱导文案），且不再受 8 字上限约束
 *    （「5 秒后可跳过广告」9 字照中）；数字交给 parseCountdown 门控，
 *    倒计时将尽才出手，出手即命中；
 *  · 第3层 短包含：文案 ≤8 字且含「跳过 / 跳過 / skip」（「跳过 5」类），
 *    诱导词黑名单一票否决（摇/晃/翻/转/扭/领/福利/详情/下载/开通/注册/
 *    登录/抽奖/红包/进入/查看/立即——「摇一摇跳过广告详情页」类绝不中）；
 *  · 第4层 图片键 vid：skip 族与倒计时控件（任意窗口，穿山甲
 *    tt_splash_skip_btn / 微博 iv_skip 类零文本键的正解）；close/exit/
 *    dismiss 族仅在【SDK 广告窗或学习窗】且【屏幕边缘带】出手（纯图片
 *    关闭键的正解，普通页面绝不误点）。
 *
 * 与决策核心（AdDecisionCore）的关系：本引擎是【主路径】（规则命中秒点），
 * 决策核心退居兜底（关闭键/角落键/热区/陀螺仪等复杂信号场景）。
 * 全部纯 Java 零 Android 依赖，JVM 可单测（RuleEngineTest）。
 */
public final class RuleEngine {

    private RuleEngine() {
    }

    /** 规则命中结果 */
    public static final class Hit {
        public String appId;      // 规则标识（日志/统计用）
        public String label;      // 命中标签（倒计时门控解析用）
        public NodeView clickNode;// 可点祖先（performAction 兜底目标；null = 仅手势）
        public int[] tapBounds;   // 手势点击目标（【标签控件自身】bounds——适配任意分辨率）
        public boolean gestureOnly; // 仅手势（无可点祖先或祖先面积超限——performAction 会点开广告）
        public boolean clickNodeAction; // v4.0.0 订阅规则：clickNode 动作（点控件本身而非坐标）
        public String src;        // v4.0.0 订阅规则来源（订阅/应用名，日志用）
    }

    /* ── 第0层：分应用规则表（主流应用真实跳过文案；包名前缀 + 精确等值） ──
       每行：{ 包名前缀, 文案1, 文案2, ... }——分应用精确命中不受全局 8 字上限约束 */
    static final String[][] APP_TEXT_RULES = {
            { "com.sina.weibo", "跳过广告", "跳过" },
            { "com.ximalaya.ting.android", "跳过广告", "跳过" },
            { "tv.danmaku.bili", "跳过广告", "跳过" },
            { "com.qiyi.video", "跳过广告", "跳过" },
            { "com.youku.phone", "跳过广告", "跳过" },
            { "com.tencent.qqlive", "跳过广告", "跳过" },
            { "com.hunantv.imgo", "跳过广告", "跳过" },
            { "com.gotokeep.keep", "跳过" },
            { "com.xingin.xhs", "跳过" },
            { "com.zhihu.android", "跳过广告" },
            { "com.sankuai.meituan", "跳过广告" },
            { "com.dianping.v1", "跳过广告" },
            { "com.jingdong.app.mall", "跳过" },
            { "com.taobao.taobao", "跳过" },
            { "com.xunmeng.pinduoduo", "跳过" },
            { "com.dragon.read", "跳过广告" },
            { "com.baidu.searchbox", "跳过" },
            { "com.netease.cloudmusic", "跳过" },
            { "com.tencent.qqmusic", "跳过广告" },
            { "com.kugou.android", "跳过" },
            { "com.UCMobile", "跳过" },
            { "com.tencent.mtt", "跳过广告" },
            { "com.douban.frodo", "跳过广告" },
            { "cn.wps.moffice_eng", "跳过广告" },
            { "ctrip.android.view", "跳过" },
            { "com.taobao.trip", "跳过" },
            { "com.autonavi.minimap", "跳过" },
            { "com.sdu.didi.psnger", "跳过" },
    };

    /* ── 第1层：全局精确文案（等值即中，大小写归一） ── */
    static final String[] TEXT_EXACT = {
            "跳过", "跳过广告", "跳过此广告", "跳过这个广告", "关闭广告", "关闭此广告",
            "点击跳过", "上滑跳过", "跳过提示", "立即跳过",
            "跳过 ad", "跳过ad",
            "skip", "skip ad", "skipad", "skip this ad",
            "跳過", "跳過廣告", "跳過此廣告",
    };

    /* ── 第2层：倒计时形态（严格正则，营销话术数学上无法混入） ──
       P1：跳过5 / 跳过 5 / 跳过广告3 / 跳过广告 5s / 跳過廣告 5秒
       P2：3s后可跳过 / 5 秒后可跳过广告 / 3秒后可跳过廣告
       注意：广告/廣告是两字词——必须用分组 (广告)? 而非单字类 [广告]? */
    static final String P_COUNTDOWN_TAIL = "^跳[过過](广告|廣告)?\\s*\\d{1,2}\\s*[s秒]?$";
    static final String P_COUNTDOWN_HEAD = "^\\d{1,2}\\s*[s秒]?\\s*后後?\\s*可?\\s*跳[过過](广告|廣告)?$";

    /* ── 第3层：短包含（≤8 字）+ 诱导词黑名单一票否决 ── */
    static final String[] TEXT_SHORT = { "跳过", "跳過", "skip" };
    static final int CONTAINS_MAX_LEN = 8;
    static final String[] DANGEROUS_WORDS = {
            "摇", "晃", "翻", "转", "扭", "领", "福利", "详情", "下载", "开通",
            "注册", "登录", "抽奖", "红包", "进入", "查看", "立即",
    };

    /* ── 第4层：vid 关键段 ──
       SKIP 族 + 倒计时控件：任意窗口；
       CLOSE 族（close/exit/dismiss）：仅 SDK 广告窗/学习窗 + 屏幕边缘带 */
    static final String[] VID_SKIP_KEYS = { "skip" };
    static final String[] VID_CLOSE_KEYS = { "close", "exit", "dismiss" };

    /** 规则匹配主入口：遍历控件树，返回最优命中（null=无规则命中，不动作） */
    public static Hit match(NodeView root, Ctx ctx) {
        if (root == null) return null;
        int[] budget = {ctx.budgetNodes};
        return visit(root, 0, budget, ctx, null);
    }

    private static Hit visit(NodeView node, int depth, int[] budget, Ctx ctx, Hit best) {
        if (node == null || depth > 32 || budget[0] <= 0) return best;
        budget[0]--;
        try {
            String text = nz(node.text());
            String desc = nz(node.desc());
            String label = text.length() > 0 ? text : desc;
            boolean vis = node.visible();
            int[] b = node.bounds();
            double area = areaRatio(b, ctx);

            /* 文本/描述规则：可见 + 面积 ≤35% 屏（CTA 巨按钮绝不中） */
            if (vis && b != null && label.length() > 0 && area <= AdDecisionCore.SKIP_AREA_MAX) {
                Hit h = matchText(label, b, node, ctx);
                if (h != null) {
                    if (isDefinitive(h.appId)) return h;   // 精确/分应用/vid：最高置信立即返回
                    if (best == null || tierRank(h.appId) > tierRank(best.appId)) best = h;
                }
            }
            /* vid 规则：无文案叶子节点 */
            else if (vis && b != null && label.length() == 0 && node.childCount() == 0
                    && area <= AdDecisionCore.SKIP_AREA_MAX) {
                Hit h = matchVid(node, b, ctx);
                if (h != null) return h; // vid 强信号立即返回
            }

            int n = node.childCount();
            for (int i = 0; i < n; i++) {
                best = visit(node.child(i), depth + 1, budget, ctx, best);
                if (best != null && isDefinitive(best.appId)) {
                    return best; // 已握精确/分应用/vid 命中，提前收工（速度优先）
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    /** 文本规则匹配（第0/1/2/3层） */
    static Hit matchText(String label, int[] b, NodeView node, Ctx ctx) {
        String low = label.toLowerCase();

        /* 第0层：分应用精确（不受长度上限约束） */
        if (ctx.pkg != null) {
            String[][] rows = APP_TEXT_RULES;
            for (String[] row : rows) {
                if (ctx.pkg.startsWith(row[0])) {
                    for (int j = 1; j < row.length; j++) {
                        if (low.equals(row[j].toLowerCase())) {
                            return build("app_exact", label, b, node, ctx);
                        }
                    }
                }
            }
        }

        /* 第1层：全局精确（等值，大小写归一） */
        for (String k : TEXT_EXACT) {
            if (low.equals(k)) return build("global_exact", label, b, node, ctx);
        }

        /* 第2层：倒计时形态（严格正则——仅跳过系词+数字/秒表，任何营销话术无法混入） */
        if (label.matches(P_COUNTDOWN_TAIL) || label.matches(P_COUNTDOWN_HEAD)) {
            return build("global_pattern", label, b, node, ctx);
        }

        /* 第3层：短包含 + 诱导词否决 */
        if (label.length() <= CONTAINS_MAX_LEN && containsShort(low) && !hasDangerousWord(low)) {
            return build("global_contains", label, b, node, ctx);
        }
        return null;
    }

    /** vid 规则匹配（第4层） */
    static Hit matchVid(NodeView node, int[] b, Ctx ctx) {
        String vid = node.viewId();
        if (vid == null || vid.length() < 4) return null;
        String v = vid.toLowerCase();
        String[] seg = v.split("[.$]");
        for (String s : seg) {
            if (s.length() < 3) continue;
            /* skip 族（任意窗口）+ 倒计时控件 */
            for (String k : VID_SKIP_KEYS) {
                if (s.contains(k)) return build("global_vid", "vid:" + vid, b, node, ctx);
            }
            if (s.contains("count") && s.contains("down") && !s.contains("download")) {
                return build("global_vid", "vid:" + vid, b, node, ctx);
            }
        }
        /* close/exit/dismiss 族：仅 SDK 广告窗/学习窗 + 屏幕边缘带 */
        if ((ctx.sdkAdWin || ctx.learnedCls) && edgeZone(b, ctx)) {
            for (String k : VID_CLOSE_KEYS) {
                if (v.contains(k)) return build("sdk_close_vid", "vid:" + vid, b, node, ctx);
            }
        }
        return null;
    }

    static Hit build(String appId, String label, int[] b, NodeView node, Ctx ctx) {
        Hit h = new Hit();
        h.appId = appId;
        h.label = label;
        h.tapBounds = b; // 点标签控件自身中心（非固定屏幕坐标）
        NodeView click = AdDecisionCore.resolveClickable(node, 4);
        h.clickNode = click;
        // 祖先缺失或祖先是巨型容器（performAction=点开广告）→ 仅手势直点标签坐标
        h.gestureOnly = click == null || !AdDecisionCore.isSmallClick(click, ctx);
        return h;
    }

    /** 屏幕边缘带：跳过/关闭键的天然位置（顶部 25% 或右缘带 centerX≥75% 宽） */
    static boolean edgeZone(int[] b, Ctx ctx) {
        if (b == null || b.length < 4 || ctx.screenW <= 0 || ctx.screenH <= 0) return false;
        int cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2;
        if (cy <= ctx.screenH / 4) return true;          // 顶部 25%
        return cx >= ctx.screenW - ctx.screenW / 4;       // 右缘带（centerX ≥75% 宽）
    }

    /** 定信度命中（全树优先立即返回）：精确 / 分应用 / vid */
    static boolean isDefinitive(String appId) {
        return "global_exact".equals(appId) || "app_exact".equals(appId)
                || "global_vid".equals(appId) || "sdk_close_vid".equals(appId);
    }

    /** 候选层优先级：倒计时形态 > 短包含（exact 优先不在此层） */
    static int tierRank(String appId) {
        if ("global_pattern".equals(appId)) return 2;
        return 1; // global_contains
    }

    /** 诱导词黑名单（包含层一票否决） */
    static boolean hasDangerousWord(String low) {
        for (String w : DANGEROUS_WORDS) {
            if (low.contains(w)) return true;
        }
        return false;
    }

    /** 短文案包含匹配（入参已小写） */
    static boolean containsShort(String low) {
        for (String k : TEXT_SHORT) {
            if (low.contains(k.toLowerCase())) return true;
        }
        return false;
    }

    /** 规则库统计（启动日志用） */
    public static String stats() {
        int appTexts = 0;
        for (String[] row : APP_TEXT_RULES) appTexts += row.length - 1;
        return "分应用表 " + APP_TEXT_RULES.length + " 款/" + appTexts + " 条文案"
                + " · 全局精确 " + TEXT_EXACT.length + " 条"
                + " · 倒计时形态 2 类"
                + " · 短包含 " + TEXT_SHORT.length + " 族(≤" + CONTAINS_MAX_LEN + "字,诱导词" + DANGEROUS_WORDS.length + " 否决)"
                + " · vid skip/" + VID_SKIP_KEYS.length + " close/" + VID_CLOSE_KEYS.length;
    }

    static double areaRatio(int[] b, Ctx ctx) {
        if (b == null || ctx.screenW <= 0 || ctx.screenH <= 0) return 0;
        double area = (double) (b[2] - b[0]) * (b[3] - b[1]);
        return area / ((double) ctx.screenW * ctx.screenH);
    }

    static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
