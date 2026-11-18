import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skip-Ad 引擎热路径基准测试 —— Java 参考实现
 * 算法与 Kotlin/JS/Python 版本完全一致（xorshift31 PRNG 保证同一棵树、同一套规则）。
 *
 * 三阶段测量：
 *   BOOT    —— 进程冷启动 + 引擎初始化（规则编译完成后打印 READY 即退出）
 *   COMPILE —— GKD 风格选择器编译吞吐（订阅更新路径）
 *   MATCH   —— 无障碍事件到达后的整树匹配（每事件热路径）
 *
 * 用法： java Bench boot | java Bench bench
 */

public class Bench {

    /* ── 引擎真实规则常量（取自 android-app/src/com/zai/adshield/core/RuleEngine.java）── */
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

    static final String[] TEXT_EXACT = {
            "跳过", "跳过广告", "跳过此广告", "跳过这个广告", "关闭广告", "关闭此广告",
            "点击跳过", "上滑跳过", "跳过提示", "立即跳过",
            "跳过 ad", "跳过ad",
            "skip", "skip ad", "skipad", "skip this ad",
            "跳過", "跳過廣告", "跳過此廣告",
    };

    static final String[] CONTAINS_SHORT = { "跳过", "skip", "跳過" };
    static final String[] DANGEROUS = { "领", "红包", "下载", "查看详情", "立即", "参与", "抽奖", "领取" };
    static final String[] VID_SKIP_KEYS = {
            "skip", "tt_splash_skip_btn", "iv_skip", "count_down", "splash",
    };

    /* 字符串形式（linear 模式 = 引擎现状：String.matches 每调用重编译） */
    static final String P_COUNTDOWN_TAIL_STR = "^(跳过|跳过广告|跳過廣告)\\s*[0-9]+(s|秒)?$";
    static final String P_COUNTDOWN_HEAD_STR = "^[0-9]+\\s*(s|秒)?(后|後)可?(跳过|跳过广告|跳過廣告)$";
    /* 预编译形式（optimized 模式 = v5.6.0 优化） */
    static Pattern patTail, patHead;

    /* ── GKD 风格订阅选择器（编译阶段的测试语料）── */
    static final String[] GKD_SELECTORS = {
            "[text*=\"跳过\"]", "[text*=\"跳过广告\"]", "[vid*=\"skip\"]",
            "[vid=\"tt_splash_skip_btn\"]", "[text=\"跳过广告 3\"]",
            "[text*=\"跳过\" && vid*=\"count_down\"]",
            "[vid*=\"skip\" >n2 [text*=\"广告\"]",
            "[text*=\"跳過廣告\" || text*=\"skip\"]",
            "ImageView <n [vid*=\"splash\"]",
            "[text*=\"关闭广告\" && clickable=true]",
            "[vid*=\"iv_skip\"] >n2 TextView",
            "[text*=\"跳过\"] -n [text*=\"广告\"]",
            "[text*=\"skip\" && clickable=true] >n2 *",
            "[vid*=\"close\" && (text*=\"广告\" || vid*=\"ad\")]",
            "[text*=\"跳过\"][9]", "[text*=\"跳过\"][9,10]",
            "* >n [vid*=\"splash_ad\"]",
            "[text*=\"跳过广告\" >n2 [text*=\"查看详情\"]",
            "[vid*=\"count_down\"] <n3 ImageView",
            "[text*=\"上滑\"] || [vid*=\"slide_skip\"]",
            "[text*=\"跳过\" && visibleToUser=true] >n *",
            "[vid*=\"ad_close\" && text=null]",
            "[text*=\"skip this ad\"] >n2 [clickable=true]",
            "[text*=\"跳過\"][3,8]",
    };

    /* ── 节点视图（对齐引擎 NodeView）── */
    static final class Node {
        String text;      // nullable
        String vid;       // nullable
        String cls;       // 类名
        int l, t, r, b;   // bounds
        boolean clickable;
        Node parent;
        List<Node> children = new ArrayList<Node>();
    }

    /* ── xorshift31 —— 与各语言实现逐位一致（31 位无符号返回值域）── */
    int rngState;
    int rnd() {
        int x = rngState;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        rngState = x;
        return x & 0x7FFFFFFF;
    }
    int rndRange(int mod) { return rnd() % mod; }

    static final String[] TEXT_POOL = {
            "跳过广告 5", "跳过广告 3", "跳过", "立即领取红包", "限时秒杀·低至1折",
            "精选推荐", "新人专享礼包", "点击查看详情", "上滑解锁更多内容", "领取优惠券",
            "skip ad 5", "Skip", "广告", "立即下载App", "今日热点推荐", "签到领金币",
            null, null, null, null,
    };
    static final String[] VID_POOL = {
            "tt_splash_skip_btn", "iv_skip", "count_down_text", "splash_ad_view",
            "close_btn", "ad_container", "banner_view", null, null, null,
    };
    static final String[] CLS_POOL = {
            "android.widget.TextView", "android.widget.ImageView", "android.widget.Button",
            "android.view.ViewGroup", "android.webkit.WebView",
    };

    Node buildTree(int seed) {
        rngState = seed;
        Node root = new Node();
        root.cls = "android.view.ViewGroup";
        root.l = 0; root.t = 0; root.r = 1080; root.b = 2400;
        int groups = 6;
        for (int g = 0; g < groups; g++) {
            Node grp = new Node();
            grp.cls = CLS_POOL[rndRange(CLS_POOL.length)];
            grp.l = rndRange(1000); grp.t = rndRange(2200);
            grp.r = grp.l + 40 + rndRange(900); grp.b = grp.t + 40 + rndRange(160);
            grp.clickable = (rndRange(2) == 0);
            link(root, grp);
            int n = 5 + rndRange(5);
            for (int i = 0; i < n; i++) {
                Node c = new Node();
                String text = TEXT_POOL[rndRange(TEXT_POOL.length)];
                if (text != null) {
                    if (rndRange(2) == 0 && text.length() >= 4) {
                        text = text + " " + (1 + rndRange(9));
                    }
                    c.text = text;
                }
                String vid = VID_POOL[rndRange(VID_POOL.length)];
                if (vid != null) c.vid = vid + "_" + rndRange(200);
                c.cls = CLS_POOL[rndRange(CLS_POOL.length)];
                c.l = grp.l + rndRange(Math.max(1, grp.r - grp.l - 80));
                c.t = grp.t + rndRange(Math.max(1, grp.b - grp.t - 30));
                int w = 30 + rndRange(700);
                int h = 20 + rndRange(80);
                c.r = c.l + w; c.b = c.t + h;
                c.clickable = (rndRange(4) == 0);
                link(grp, c);
            }
        }
        return root;
    }
    void link(Node p, Node c) { c.parent = p; p.children.add(c); }

    /* ── 引擎热路径：整树匹配（对齐 RuleEngine 文案层 + vid 通道 + 形状门）── */
    String pkg;
    Map<String, String[]> appRuleIndex;   // v5.6.0 候选优化：包名哈希索引
    Set<String> exactSet;                 // v5.6.0 候选优化：精确文案 HashSet
    boolean useOptimized;

    long hits;
    long sink;

    String matchTree(Node root, boolean optimized) {
        List<Node> stack = new ArrayList<Node>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Node n = stack.remove(stack.size() - 1);
            if (n.text != null) {
                String low = n.text.trim().toLowerCase(Locale.ROOT);
                String hit = matchText(n, low, optimized);
                if (hit != null) return hit;
            }
            if (n.vid != null) {
                String v = n.vid.toLowerCase(Locale.ROOT);
                for (int i = 0; i < VID_SKIP_KEYS.length; i++) {
                    if (v.contains(VID_SKIP_KEYS[i])) { if (shapeGate(n, true)) return "vid:" + n.vid; }
                }
            }
            for (int i = n.children.size() - 1; i >= 0; i--) stack.add(n.children.get(i));
        }
        return null;
    }

    String matchText(Node n, String low, boolean optimized) {
        // 第0层：分应用规则
        if (optimized) {
            String[] row = pkg != null ? appRuleIndex.get(pkg) : null;
            if (row != null) {
                for (int i = 1; i < row.length; i++) {
                    if (low.equals(row[i])) return "app:" + row[i];
                }
            }
        } else {
            for (int ri = 0; ri < APP_TEXT_RULES.length; ri++) {
                String[] row = APP_TEXT_RULES[ri];
                if (pkg.startsWith(row[0])) {
                    for (int i = 1; i < row.length; i++) {
                        if (low.equals(row[i])) return "app:" + row[i];
                    }
                }
            }
        }
        // 第1层：全局精确文案
        if (optimized) {
            if (exactSet.contains(low)) { if (shapeGate(n, false)) return "exact:" + low; }
        } else {
            for (int i = 0; i < TEXT_EXACT.length; i++) {
                if (low.equals(TEXT_EXACT[i])) { if (shapeGate(n, false)) return "exact:" + low; }
            }
        }
        // 第2层：倒计时形态（linear=引擎现状 String.matches 每调用重编译；optimized=预编译）
        if (optimized) {
            if (patTail.matcher(low).matches()) { if (shapeGate(n, false)) return "countdown:" + low; }
            if (patHead.matcher(low).matches()) { if (shapeGate(n, false)) return "countdown:" + low; }
        } else {
            if (low.matches(P_COUNTDOWN_TAIL_STR)) { if (shapeGate(n, false)) return "countdown:" + low; }
            if (low.matches(P_COUNTDOWN_HEAD_STR)) { if (shapeGate(n, false)) return "countdown:" + low; }
        }
        // 第3层：短包含 + 危险词黑名单
        if (low.length() <= 8) {
            boolean cont = false;
            for (int i = 0; i < CONTAINS_SHORT.length; i++) if (low.contains(CONTAINS_SHORT[i])) { cont = true; break; }
            if (cont) {
                for (int i = 0; i < DANGEROUS.length; i++) if (low.contains(DANGEROUS[i])) return null;
                if (shapeGate(n, false)) return "contains:" + low;
            }
        }
        return null;
    }

    boolean shapeGate(Node n, boolean relax) {
        int w = n.r - n.l;
        double lim = relax ? 0.70 : 0.55;
        if (w > lim * 1080) return false;
        if (n.parent == null) return true;
        List<Node> sib = n.parent.children;
        if (sib.size() >= 4) {
            int same = 0;
            int nw = n.r - n.l;
            for (int i = 0; i < sib.size(); i++) {
                Node s = sib.get(i);
                if (Math.abs((s.r - s.l) - nw) <= 2 && Math.abs(s.l - n.l) <= 2) same++;
            }
            if (same >= 4) return false;
        }
        return true;
    }

    void buildOptIndex() {
        appRuleIndex = new HashMap<String, String[]>();
        for (int i = 0; i < APP_TEXT_RULES.length; i++) {
            appRuleIndex.put(APP_TEXT_RULES[i][0], APP_TEXT_RULES[i]);
        }
        exactSet = new HashSet<String>();
        for (int i = 0; i < TEXT_EXACT.length; i++) exactSet.add(TEXT_EXACT[i]);
        patTail = Pattern.compile(P_COUNTDOWN_TAIL_STR);
        patHead = Pattern.compile(P_COUNTDOWN_HEAD_STR);
    }

    /* ── 订阅选择器编译（GkdSelector.compile 对齐：分词 + 括号布尔 + 关系符）── */
    static final class Sel {
        String raw;
        List<String[]> conds = new ArrayList<String[]>(); // {attr, op, value}
        String rel = ""; int relN = 1;
        List<Integer> posList = new ArrayList<Integer>();
    }

    List<Sel> compileSelectors(String[] sources, int rounds) {
        List<Sel> out = new ArrayList<Sel>();
        for (int r = 0; r < rounds; r++) {
            out.clear();
            for (int s = 0; s < sources.length; s++) {
                out.add(compileOne(sources[s]));
            }
        }
        return out;
    }

    Sel compileOne(String src) {
        Sel sel = new Sel();
        sel.raw = src;
        int i = 0;
        while (i < src.length()) {
            char ch = src.charAt(i);
            if (ch == '[') {
                int close = src.indexOf(']', i);
                if (close < 0) break;
                String inner = src.substring(i + 1, close);
                for (String part : splitLogical(inner)) {
                    String[] kv = splitCond(part.trim());
                    if (kv != null) sel.conds.add(kv);
                }
                i = close + 1;
            } else if (ch == '>' || ch == '<' || ch == '-') {
                int j = i + 1;
                while (j < src.length() && Character.isDigit(src.charAt(j))) j++;
                sel.rel = String.valueOf(ch);
                sel.relN = j > i + 1 ? Integer.parseInt(src.substring(i + 1, j)) : 1;
                i = j;
            } else if (ch == ' ') {
                i++;
            } else {
                int j = i;
                while (j < src.length() && src.charAt(j) != ' ' && src.charAt(j) != '['
                        && src.charAt(j) != '>' && src.charAt(j) != '<' && src.charAt(j) != '-') j++;
                if (j > i) sel.posList.add(0);
                i = j;
            }
        }
        return sel;
    }

    List<String> splitLogical(String s) {
        List<String> parts = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (depth == 0 && c == '&' && i + 1 < s.length() && s.charAt(i + 1) == '&') {
                parts.add(cur.toString()); cur.setLength(0); i++; continue;
            }
            if (depth == 0 && c == '|' && i + 1 < s.length() && s.charAt(i + 1) == '|') {
                parts.add(cur.toString()); cur.setLength(0); i++; continue;
            }
            cur.append(c);
        }
        parts.add(cur.toString());
        return parts;
    }

    String[] splitCond(String s) {
        int eq = s.indexOf('=');
        if (eq < 0) return new String[] { s, "exists", "" };
        String attr = s.substring(0, eq).trim();
        String rest = s.substring(eq);
        String op = "==";
        if (rest.startsWith("=")) op = "==";
        else if (rest.startsWith("*")) op = "*=";
        else if (rest.startsWith("!")) op = "!=";
        int q1 = s.indexOf('"');
        int q2 = s.lastIndexOf('"');
        String value = q1 >= 0 && q2 > q1 ? s.substring(q1 + 1, q2) : "";
        return new String[] { attr, op, value };
    }

    /* ── 计时与输出 ── */
    static long nowNs() { return System.nanoTime(); }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "bench";
        Bench b = new Bench();
        b.pkg = "com.sankuai.meituan";

        long t0 = nowNs();
        List<Sel> sels = b.compileSelectors(GKD_SELECTORS, 1); // 预热 1 轮
        b.compileSelectors(GKD_SELECTORS, 5);                 // 预热 5 轮
        long m0c = nowNs();
        b.compileSelectors(GKD_SELECTORS, 50);                 // 测量 50 轮
        long m1c = nowNs();
        sels = b.compileSelectors(GKD_SELECTORS, 1);
        long t1 = nowNs();
        long selSink = 0;
        for (Sel s : sels) selSink += s.raw.length() + s.conds.size() + s.relN + s.posList.size();
        long compileUs = (m1c - m0c) / 50 / 1000;

        if (mode.equals("boot")) {
            System.out.println("READY");
            return;
        }

        Node tree = b.buildTree(20261118);

        int COLD = 1000, WARMUP = 2000, ITERS = 20000;

        /* 冷段（含 JIT 编译惩罚）——线性版 */
        long c0 = nowNs();
        for (int i = 0; i < COLD; i++) {
            String h = b.matchTree(tree, false);
            if (h != null) b.sink += h.length();
        }
        long c1 = nowNs();
        long firstLinearNs = (c1 - c0) / COLD;

        /* 预热 → 稳态 ——线性版 */
        for (int i = 0; i < WARMUP; i++) {
            String h = b.matchTree(tree, false);
            if (h != null) b.sink += h.length();
        }
        b.hits = 0;
        long s0 = nowNs();
        for (int i = 0; i < ITERS; i++) {
            String h = b.matchTree(tree, false);
            if (h != null) { b.hits++; b.sink += h.hashCode() + h.length(); }
        }
        long s1 = nowNs();

        /* v5.6.0 候选优化（哈希索引/HashSet）对照 */
        b.buildOptIndex();
        for (int i = 0; i < WARMUP; i++) {
            String h = b.matchTree(tree, true);
            if (h != null) b.sink += h.length();
        }
        b.hits = 0;
        long o0 = nowNs();
        for (int i = 0; i < ITERS; i++) {
            String h = b.matchTree(tree, true);
            if (h != null) { b.hits++; b.sink += h.hashCode() + h.length(); }
        }
        long o1 = nowNs();

        System.out.println("{\"lang\":\"java\",\"compile_us\":" + compileUs
                + ",\"sel_sink\":" + selSink
                + ",\"match_linear_first1000_ns\":" + firstLinearNs
                + ",\"match_linear_ns\":" + String.format(Locale.ROOT, "%.1f", (double)(s1 - s0) / ITERS)
                + ",\"match_optimized_ns\":" + String.format(Locale.ROOT, "%.1f", (double)(o1 - o0) / ITERS)
                + ",\"hits\":" + b.hits + ",\"sink\":" + (b.sink % 1000003)
                + ",\"rss_kb\":" + rssKb() + "}");
    }

    static long rssKb() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("/proc/self/status"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    br.close();
                    return Long.parseLong(line.replaceAll("[^0-9]", ""));
                }
            }
            br.close();
        } catch (Exception ignore) { }
        return -1;
    }
}
