package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.NodeView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * GKD 选择器兼容引擎（v4.0.0 订阅融合 · v4.3.0 全量语法补齐 · https://gkd.li/guide/selector）
 * ─────────────────────────────────────────────────────────────────
 * 目标：直接吃下 GKD 社区订阅里的选择器规则，让整个 GKD 规则生态
 * 可被本应用导入使用；同时保持「绝不乱点」红线。
 *
 * v4.3.0 全量语法补齐（issue#38 实锤：订阅 828 条规则命中 0——GKD 默认
 * 订阅的主力规则全部落在我们的语法盲区上，解析「成功」但永远不匹配）：
 *  · 【globalGroups 主力规则语法】[text*="跳过"][text.length<10]
 *    [visibleToUser=true][(text.length<10&&text*="跳过") || vid*="skip"]
 *    ——attr.length 长度比较、visibleToUser 别名、括号布尔表达式
 *    （(A&&B)||C 嵌套组合，旧解析器把括号块当条件直接扚 ParseException）；
 *  · 【childCount 属性】[childCount=0]——默认订阅 372 处使用；
 *  · 【数字关系符】@View <3 FrameLayout <2 FrameLayout < [vid="fl_third_ad"]
 *    （CSDN 开屏规则）——旧解析器把「3」当标签名，规则静默死亡；
 *    语义：>N 精确 N 层祖先 · >nN ≤N 层 · <N 第 N-1 个直接子节点 ·
 *    <nN 任意直接子节点 · <<N/<<nN 子孙（DFS 序号近似为任意子孙，
 *    保守放宽）· +N/-N 兄弟（序号近似为同向任意兄弟）。
 *
 * 支持语法子集（覆盖开屏/弹窗规则的 99% 写法）：
 *  · 属性选择器：TextView[id='x'][text*='跳过'][clickable=true]
 *    - 标签名：TextView / android.widget.TextView / *（GKD 语义：
 *      无点短名 ≡ name='TextView'||name$='.TextView'）
 *    - 属性：name/id/vid/text/desc/clickable/visible/visibleToUser/
 *      editable/scrollable/childCount/index/depth/w/h + attr.length
 *      · id 带冒号 → 全名精确匹配；不带冒号 → 末段匹配（兼容社区
 *        短 id 写法，跨版本更稳）
 *      · vid 永远是 viewId 末段（本应用扩展，写规则更短）
 *    - 操作符：= != ^= $= *= !^= !*= !$= ~= !~=（正则）> >= < <=
 *    - 布尔组合：[a='x'||b='y']、[a='x'&&b='y']、[!(a='x')]、
 *      [(a&&b)||c] 任意嵌套括号（BExpr 递归下降解析）
 *  · 关系选择器（连接两个属性选择器，强制空格分隔）：
 *      A > B   A 是 B 的祖先      A < B    A 是 B 的直接子节点
 *      A << B  A 是 B 的子孙      A + B    A 是 B 的前置兄弟
 *      A - B   A 是 B 的后置兄弟
 *    （兄弟算子实现为「同父兄弟命中」——不要求紧邻，保守放宽：
 *      兄弟上下文约束已足够强，开屏规则极少依赖精确序号）
 *  · 顶层多选择器：sel1 || sel2（短路，左优先）
 *  · @ 目标标记：@A > B → 目标是 A；缺省取最后一个属性选择器
 *
 * 红线：全部纯 Java 零 Android 依赖（JVM 可单测）；解析失败抛
 * ParseException 由订阅加载侧捕获跳过该规则——绝不影响主引擎。
 */
public final class GkdSelector {

    public static final class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ParseException(String msg) {
            super(msg);
        }
    }

    /* ══════════ 值与条件 ══════════ */

    static final int V_STR = 0, V_INT = 1, V_BOOL = 2, V_NULL = 3;

    /** 单个布尔表达式：attr op 字面量（v4.3.0 支持 attr.length 长度比较） */
    static final class Cond {
        String attr;
        String lenAttr; // v4.3.0：attr.length 形式（text.length<10）——对基础属性字符串取长度比较
        String op;
        int vType = V_NULL;
        String sv;
        long iv;
        boolean bv;
        Pattern rx; // ~= 预编译

        boolean test(Exe x, NodeView n) {
            if (lenAttr != null) return testInt(lenOf(lenAttr, n)); // text.length<10
            String a = attr;
            if ("name".equals(a)) return testName(x, n);
            if ("id".equals(a)) return testId(n.viewId());
            if ("vid".equals(a)) return testStr(vidOf(n.viewId()));
            if ("text".equals(a)) return testStr(nz(n.text()));
            if ("desc".equals(a)) return testStr(nz(n.desc()));
            if ("clickable".equals(a)) return testBool(n.clickable());
            if ("visible".equals(a) || "visibleToUser".equals(a)) return testBool(n.visible()); // v4.3.0：GKD 原生属性名
            if ("editable".equals(a)) return testBool(n.editable());
            if ("scrollable".equals(a)) return testBool(n.scrollable());
            if ("childCount".equals(a)) return testInt(n.childCount()); // v4.3.0：默认订阅 372 处使用
            if ("index".equals(a)) return testInt(x.indexOf(n));
            if ("depth".equals(a)) return testInt(x.depthOf(n));
            if ("w".equals(a)) return testInt(x.widthOf(n));
            if ("h".equals(a)) return testInt(x.heightOf(n));
            return false; // 未知属性：安全侧不匹配
        }

        /** v4.3.0：基础属性的字符串长度（text/desc/id/vid/name） */
        static int lenOf(String attr, NodeView n) {
            String s;
            if ("text".equals(attr)) s = nz(n.text());
            else if ("desc".equals(attr)) s = nz(n.desc());
            else if ("id".equals(attr)) s = nz(n.viewId());
            else if ("vid".equals(attr)) s = vidOf(n.viewId());
            else if ("name".equals(attr)) s = nz(n.cls());
            else s = "";
            return s.length();
        }

        boolean testName(Exe x, NodeView n) {
            String cls = nz(n.cls());
            if ("=".equals(op)) {
                // GKD 语义：短名 ≡ 等值 或 以 .短名 结尾
                if (sv.indexOf('.') >= 0) return cls.equals(sv);
                return cls.equals(sv) || cls.endsWith("." + sv);
            }
            if ("!=".equals(op)) {
                if (sv.indexOf('.') >= 0) return !cls.equals(sv);
                return !cls.equals(sv) && !cls.endsWith("." + sv);
            }
            return testStr(cls);
        }

        boolean testId(String fullId) {
            String id = nz(fullId);
            if ("=".equals(op)) {
                if (sv.indexOf(':') >= 0) return id.equals(sv);   // 全名 com.x:id/y
                return vidOf(id).equals(sv) || id.endsWith(":" + sv) || id.endsWith("/" + sv);
            }
            if ("!=".equals(op)) return !testId(fullId);
            return testStr(id);
        }

        boolean testStr(String s) {
            if (vType == V_NULL) {
                boolean empty = s == null || s.length() == 0;
                return "=".equals(op) ? empty : !empty;
            }
            if (s == null) s = "";
            boolean r;
            if ("=".equals(op)) r = s.equals(sv);
            else if ("^=".equals(op)) r = s.startsWith(sv);
            else if ("$=".equals(op)) r = s.endsWith(sv);
            else if ("*=".equals(op)) r = s.contains(sv);
            else if ("~=".equals(op)) r = rx != null && rx.matcher(s).find();
            else if ("!=".equals(op)) r = !s.equals(sv);
            else if ("!^=".equals(op)) r = !s.startsWith(sv);
            else if ("!$=".equals(op)) r = !s.endsWith(sv);
            else if ("!*=".equals(op)) r = !s.contains(sv);
            else if ("!~=".equals(op)) r = rx == null || !rx.matcher(s).find();
            else return false;
            return r;
        }

        boolean testBool(boolean actual) {
            if (vType == V_NULL) return "!=".equals(op);
            if (vType != V_BOOL) return false;
            if ("=".equals(op)) return actual == bv;
            if ("!=".equals(op)) return actual != bv;
            return false;
        }

        boolean testInt(long actual) {
            if (vType != V_INT) return false;
            if ("=".equals(op)) return actual == iv;
            if ("!=".equals(op)) return actual != iv;
            if (">".equals(op)) return actual > iv;
            if (">=".equals(op)) return actual >= iv;
            if ("<".equals(op)) return actual < iv;
            if ("<=".equals(op)) return actual <= iv;
            return false;
        }
    }

    /** v4.3.0 布尔表达式树：cond 叶子 / and / or / not——支持任意嵌套括号 */
    static final class BExpr {
        static final int T_COND = 0, T_AND = 1, T_OR = 2, T_NOT = 3;
        int type;
        Cond cond;
        final List<BExpr> children = new ArrayList<BExpr>();

        boolean eval(Exe x, NodeView n) {
            if (type == T_COND) return cond != null && cond.test(x, n);
            if (type == T_AND) {
                for (int i = 0; i < children.size(); i++) if (!children.get(i).eval(x, n)) return false;
                return true;
            }
            if (type == T_OR) {
                for (int i = 0; i < children.size(); i++) if (children.get(i).eval(x, n)) return true;
                return false;
            }
            return !children.isEmpty() && !children.get(0).eval(x, n);
        }
    }

    /** 属性选择器：@? tag? bracket*（括号间 AND；括号内任意布尔表达式） */
    static final class AttrSel {
        String tag;                 // null = *
        boolean at;                 // @ 目标标记
        // brackets[b] = 第 b 个 [] 的布尔表达式：括号间 AND（GKD：[a=1][b=1] ≡ [a=1&&b=1]）
        final List<BExpr> brackets = new ArrayList<BExpr>();

        boolean match(Exe x, NodeView n) {
            if (!matchTag(n)) return false;
            for (int b = 0; b < brackets.size(); b++) {
                if (!brackets.get(b).eval(x, n)) return false; // 任一 [] 不满足 → 整个属性选择器不匹配
            }
            return true;
        }

        boolean matchTag(NodeView n) {
            if (tag == null || "*".equals(tag)) return true;
            String cls = nz(n.cls());
            if (tag.indexOf('.') >= 0) return cls.equals(tag);
            return cls.equals(tag) || cls.endsWith("." + tag);
        }
    }

    /** v4.3.0 关系算子：op + 编号（GKD 数字关系符 <3 / >n2 / <<4 …）
     *  num=0 未编号（任意层/任意兄弟）；num>0：> N 精确 N 层祖先、
     *  < N 第 N-1 个直接子节点；n=true 为 n 前缀（≤N 保守放宽）。
     *  <<N（DFS 序号）与 +N/-N（兄弟序号）近似为任意——规则上下文
     *  约束已足够强，近似只会多给候选不会静默死亡（旧解析器把编号
     *  当标签名，规则直接变死规则——issue#38 订阅零命中的元凶之一）。 */
    static final class Rel {
        char op;      // '>' '<' '≪'(<<) '+' '-'
        int num;      // 0 = 未编号
        boolean n;    // n 前缀（nN → ≤N 近似）
    }

    /** 一条链：attrSels 与 rels 交替（rels[i] 连接 attrSels[i] 与 attrSels[i+1]） */
    static final class Chain {
        final List<AttrSel> sels = new ArrayList<AttrSel>();
        final List<Rel> rels = new ArrayList<Rel>();

        /** 从右往左验证：node 匹配最后一个属性选择器，再逐段向左走关系 */
        NodeView walk(Exe x, NodeView lastNode) {
            int n = sels.size();
            if (n == 0 || !sels.get(n - 1).match(x, lastNode)) return null;
            NodeView target = sels.get(n - 1).at ? lastNode : null;
            NodeView cur = lastNode;
            for (int i = n - 2; i >= 0; i--) {
                Rel rel = rels.get(i);
                AttrSel left = sels.get(i);
                NodeView next = null;
                if (rel.op == '>') next = x.findAncestor(cur, left, rel);
                else if (rel.op == '<') next = x.findDirectChild(cur, left, rel);
                else if (rel.op == '\u226A') next = x.findDescendant(cur, left);
                else if (rel.op == '+') next = x.findSibling(cur, left, -1);
                else if (rel.op == '-') next = x.findSibling(cur, left, +1);
                if (next == null) return null;
                if (left.at) target = next;
                cur = next;
            }
            return target != null ? target : lastNode;
        }
    }

    /* ══════════ 匹配执行器（带节点预算） ══════════ */

    static final class Exe {
        final int[] budget;

        Exe(int[] budget) {
            this.budget = budget;
        }

        boolean dec() {
            if (budget[0] <= 0) return false;
            budget[0]--;
            return true;
        }

        /** 祖先查找：未编号任意层；>N 精确 N 层；>nN ≤N 层（v4.3.0 数字关系符） */
        NodeView findAncestor(NodeView from, AttrSel sel, Rel rel) {
            if (rel.num > 0 && !rel.n) {
                NodeView p = from;
                for (int k = 0; k < rel.num; k++) {
                    if (!dec()) return null;
                    p = p.parent();
                    if (p == null) return null;
                }
                return sel.match(this, p) ? p : null;
            }
            int max = (rel.n && rel.num > 0) ? rel.num : Integer.MAX_VALUE;
            NodeView p = from.parent();
            int up = 0;
            while (p != null && up < max) {
                if (!dec()) return null;
                if (sel.match(this, p)) return p;
                p = p.parent();
                up++;
            }
            return null;
        }

        /** 直接子节点查找：未编号任意；<N 第 N-1 个；<nN 任意（v4.3.0） */
        NodeView findDirectChild(NodeView of, AttrSel sel, Rel rel) {
            if (rel.num > 0 && !rel.n) {
                if (!dec()) return null;
                NodeView c = of.child(rel.num - 1);
                return (c != null && sel.match(this, c)) ? c : null;
            }
            int n = of.childCount();
            for (int i = 0; i < n; i++) {
                if (!dec()) return null;
                NodeView c = of.child(i);
                if (c != null && sel.match(this, c)) return c;
            }
            return null;
        }

        NodeView findDescendant(NodeView of, AttrSel sel) {
            int n = of.childCount();
            for (int i = 0; i < n; i++) {
                if (!dec()) return null;
                NodeView c = of.child(i);
                if (c == null) continue;
                if (sel.match(this, c)) return c;
                NodeView r = findDescendant(c, sel);
                if (r != null) return r;
            }
            return null;
        }

        /** 同父兄弟命中（保守放宽：不要求紧邻序号，见类注释） */
        NodeView findSibling(NodeView of, AttrSel sel, int dir) {
            NodeView p = of.parent();
            if (p == null) return null;
            int n = p.childCount();
            for (int i = 0; i < n; i++) {
                if (!dec()) return null;
                NodeView c = p.child(i);
                if (c != null && c != of && sel.match(this, c)) return c;
            }
            return null;
        }

        int indexOf(NodeView node) {
            NodeView p = node.parent();
            if (p == null) return 0;
            int n = p.childCount();
            for (int i = 0; i < n; i++) {
                NodeView c = p.child(i);
                if (c == node) return i;
            }
            return 0;
        }

        int depthOf(NodeView node) {
            int d = 0;
            NodeView p = node.parent();
            while (p != null) {
                d++;
                p = p.parent();
            }
            return d;
        }

        int widthOf(NodeView node) {
            int[] b = node.bounds();
            return b == null || b.length < 4 ? 0 : Math.max(0, b[2] - b[0]);
        }

        int heightOf(NodeView node) {
            int[] b = node.bounds();
            return b == null || b.length < 4 ? 0 : Math.max(0, b[3] - b[1]);
        }
    }

    /* ══════════ 选择器本体：顶层 || 多链 ══════════ */

    public final List<Chain> chains = new ArrayList<Chain>();
    final String raw;

    private GkdSelector(String raw) {
        this.raw = raw;
    }

    public static GkdSelector compile(String s) {
        if (s == null) throw new ParseException("null selector");
        String t = s.trim();
        if (t.length() == 0) throw new ParseException("empty selector");
        GkdSelector g = new GkdSelector(s);
        // 顶层按 || 拆分（尊重引号与方括号）
        List<String> parts = splitTop(t, "||");
        for (int i = 0; i < parts.size(); i++) {
            Chain c = parseChain(parts.get(i));
            if (c.sels.isEmpty()) continue;
            g.chains.add(c);
        }
        if (g.chains.isEmpty()) throw new ParseException("no chain: " + s);
        return g;
    }

    /**
     * 全树找目标节点（从右往左语义：先树内定位末段选择器，再向左验证关系链）。
     * 返回 @ 标记的目标节点（无 @ 取末段节点）；null = 未命中。
     */
    public NodeView findFirst(NodeView root, int budget) {
        if (root == null) return null;
        Exe x = new Exe(new int[]{budget});
        return findFirst(root, x);
    }

    /** v4.2.0：共享预算版本（排除规则等复用同一次扫描的节点预算，不重复建账） */
    NodeView findFirst(NodeView root, Exe x) {
        if (root == null) return null;
        for (int ci = 0; ci < chains.size(); ci++) {
            NodeView r = dfs(chains.get(ci), root, x);
            if (r != null) return r;
        }
        return null;
    }

    /** v4.2.0：在指定节点上验证本选择器的全部链（node 作为末段命中，
     * 递归遍历由调用方的单遍 DFS 负责）——订阅多规则共享一次树遍历的基石 */
    NodeView walkChains(NodeView node, Exe x) {
        for (int ci = 0; ci < chains.size(); ci++) {
            NodeView r = chains.get(ci).walk(x, node);
            if (r != null) return r;
        }
        return null;
    }

    private static NodeView dfs(Chain chain, NodeView node, Exe x) {
        if (node == null || !x.dec()) return null;
        NodeView t = chain.walk(x, node);
        if (t != null) return t;
        int n = node.childCount();
        for (int i = 0; i < n; i++) {
            NodeView r = dfs(chain, node.child(i), x);
            if (r != null) return r;
        }
        return null;
    }

    /* ══════════ 解析 ══════════ */

    static Chain parseChain(String s) {
        Chain c = new Chain();
        int i = 0, n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            if (ch == '>' || ch == '<' || ch == '+' || ch == '-') {
                // v4.3.0 数字关系符：<3 / >n2 / <<4 / +(1,3) ——编号+可选 n 前缀+可选元组
                Rel rel = new Rel();
                rel.op = ch;
                i++;
                if (ch == '<' && i < n && s.charAt(i) == '<') {
                    rel.op = '\u226A';
                    i++;
                }
                if (i < n && s.charAt(i) == 'n' && i + 1 < n && Character.isDigit(s.charAt(i + 1))) {
                    rel.n = true;
                    i++;
                }
                int ds = i;
                while (i < n && Character.isDigit(s.charAt(i))) i++;
                if (i > ds) {
                    try {
                        rel.num = Integer.parseInt(s.substring(ds, i));
                    } catch (NumberFormatException e) {
                        rel.num = 0;
                    }
                }
                // 元组形式 (a1,a2,…)：罕见，取首数字近似（未解析到数字则忽略）
                if (i < n && s.charAt(i) == '(') {
                    int end = findParenEnd(s, i);
                    if (end < 0) throw new ParseException("unclosed rel tuple: " + s);
                    if (rel.num == 0) {
                        int d2 = i + 1;
                        while (d2 < end && !Character.isDigit(s.charAt(d2))) d2++;
                        int de = d2;
                        while (de < end && Character.isDigit(s.charAt(de))) de++;
                        if (de > d2) {
                            try {
                                rel.num = Integer.parseInt(s.substring(d2, de));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    i = end + 1;
                }
                if (c.sels.isEmpty())
                    throw new ParseException("chain starts with rel: " + s);
                c.rels.add(rel);
                continue;
            }
            // 属性选择器
            AttrSel a = new AttrSel();
            if (ch == '@') {
                a.at = true;
                i++;
            }
            StringBuilder tag = new StringBuilder();
            while (i < n) {
                char t = s.charAt(i);
                if (Character.isLetterOrDigit(t) || t == '_' || t == '.' || t == '$' || t == '*') {
                    tag.append(t);
                    i++;
                } else break;
            }
            if (tag.length() > 0 && !"*".equals(tag.toString())) a.tag = tag.toString();
            while (i < n && s.charAt(i) == '[') {
                int close = findBracketEnd(s, i);
                if (close < 0) throw new ParseException("unclosed [: " + s);
                String inner = s.substring(i + 1, close);
                BExpr be = parseBracketExpr(inner);
                if (be != null) a.brackets.add(be);
                i = close + 1;
            }
            if (tag.length() == 0 && a.brackets.isEmpty())
                throw new ParseException("empty attr sel: " + s);
            c.sels.add(a);
            // 关系选择器强制空白分隔（GKD 规范）：下一字符必须是空白或关系符
            if (i < n && !Character.isWhitespace(s.charAt(i))
                    && s.charAt(i) != '>' && s.charAt(i) != '<'
                    && s.charAt(i) != '+' && s.charAt(i) != '-' && s.charAt(i) != '[') {
                throw new ParseException("missing space around rel: " + s);
            }
        }
        if (c.sels.size() != c.rels.size() + 1)
            throw new ParseException("sel/rel mismatch: " + s);
        return c;
    }

    /** v4.3.0：[] 内部 → 布尔表达式树（递归下降：or → and → unary）。
     *  支持任意嵌套括号 (A&&B)||C、取反 !(A||B)——GKD 默认订阅全局
     *  开屏规则的核心语法。 */
    static BExpr parseBracketExpr(String inner) {
        List<String> toks = tokenizeBool(inner);
        if (toks.isEmpty()) return null;
        int[] pos = {0};
        BExpr e = parseOr(toks, pos);
        if (pos[0] < toks.size()) throw new ParseException("bad bool expr: [" + inner + "]");
        return e;
    }

    static BExpr parseOr(List<String> toks, int[] pos) {
        BExpr left = parseAnd(toks, pos);
        while (pos[0] < toks.size() && "||".equals(toks.get(pos[0]))) {
            pos[0]++;
            BExpr right = parseAnd(toks, pos);
            if (left.type != BExpr.T_OR) {
                BExpr o = new BExpr();
                o.type = BExpr.T_OR;
                o.children.add(left);
                left = o;
            }
            left.children.add(right);
        }
        return left;
    }

    static BExpr parseAnd(List<String> toks, int[] pos) {
        BExpr left = parseUnary(toks, pos);
        while (pos[0] < toks.size() && "&&".equals(toks.get(pos[0]))) {
            pos[0]++;
            BExpr right = parseUnary(toks, pos);
            if (left.type != BExpr.T_AND) {
                BExpr o = new BExpr();
                o.type = BExpr.T_AND;
                o.children.add(left);
                left = o;
            }
            left.children.add(right);
        }
        return left;
    }

    static BExpr parseUnary(List<String> toks, int[] pos) {
        while (pos[0] < toks.size() && toks.get(pos[0]).length() == 0) pos[0]++;
        if (pos[0] >= toks.size()) throw new ParseException("bad bool expr: eof");
        String t = toks.get(pos[0]);
        if ("!".equals(t)) {
            pos[0]++;
            BExpr not = new BExpr();
            not.type = BExpr.T_NOT;
            not.children.add(parseUnary(toks, pos));
            return not;
        }
        if ("(".equals(t)) {
            pos[0]++;
            BExpr e = parseOr(toks, pos);
            if (pos[0] >= toks.size() || !")".equals(toks.get(pos[0])))
                throw new ParseException("unbalanced parens");
            pos[0]++;
            return e;
        }
        if (")".equals(t) || "&&".equals(t) || "||".equals(t))
            throw new ParseException("bad bool expr near: " + t);
        pos[0]++;
        Cond cd = parseCond(t);
        BExpr leaf = new BExpr();
        leaf.type = BExpr.T_COND;
        leaf.cond = cd;
        return leaf;
    }

    /** 布尔表达式分词：括号 / && / || / ! 为独立 token；其余（条件串）
     *  整体保留（引号内的括号与操作符绝不切分） */
    static List<String> tokenizeBool(String s) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && i + 1 < n) {
                    cur.append(s.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                cur.append(c);
                continue;
            }
            if (c == '(' || c == ')') {
                flushTok(out, cur);
                out.add(String.valueOf(c));
                continue;
            }
            if (c == '!' && i + 1 < n && s.charAt(i + 1) == '(') {
                flushTok(out, cur);
                out.add("!");
                out.add("(");
                i++;
                continue;
            }
            if (s.startsWith("&&", i)) {
                flushTok(out, cur);
                out.add("&&");
                i++;
                continue;
            }
            if (s.startsWith("||", i)) {
                flushTok(out, cur);
                out.add("||");
                i++;
                continue;
            }
            cur.append(c);
        }
        flushTok(out, cur);
        return out;
    }

    static void flushTok(List<String> out, StringBuilder cur) {
        String t = cur.toString().trim();
        if (t.length() > 0) out.add(t);
        cur.setLength(0);
    }

    static Cond parseCond(String s) {
        if (s.length() == 0) return null;
        // 定位操作符（属性名 [a-zA-Z_][a-zA-Z0-9_.]* 之后；v4.3.0 属性名含 . 支持 text.length）
        int i = 0, n = s.length();
        while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
        if (i == 0) throw new ParseException("bad attr: [" + s + "]");
        String attr = s.substring(0, i);
        String rest = s.substring(i);
        String op = null;
        // 三字符优先
        if (rest.startsWith("!^=")) op = "!^=";
        else if (rest.startsWith("!$=")) op = "!$=";
        else if (rest.startsWith("!*=")) op = "!*=";
        else if (rest.startsWith("!~=")) op = "!~=";
        else if (rest.startsWith("!=")) op = "!=";
        else if (rest.startsWith("^=")) op = "^=";
        else if (rest.startsWith("$=")) op = "$=";
        else if (rest.startsWith("*=")) op = "*=";
        else if (rest.startsWith("~=")) op = "~=";
        else if (rest.startsWith(">=")) op = ">=";
        else if (rest.startsWith("<=")) op = "<=";
        else if (rest.startsWith("=")) op = "=";
        else if (rest.startsWith(">")) op = ">";
        else if (rest.startsWith("<")) op = "<";
        if (op == null) throw new ParseException("no operator: [" + s + "]");
        String val = rest.substring(op.length()).trim();
        Cond c = new Cond();
        c.attr = attr;
        c.op = op;
        // v4.3.0：attr.length → 长度比较（text.length<10）
        if (attr.length() > 7 && attr.endsWith(".length")) {
            c.lenAttr = attr.substring(0, attr.length() - ".length".length());
            attr = c.lenAttr; // 后续合法性校验用基础名
        }
        if (val.equals("null")) {
            c.vType = V_NULL;
        } else if (val.equals("true") || val.equals("false")) {
            c.vType = V_BOOL;
            c.bv = val.equals("true");
        } else if (val.length() >= 2
                && (val.charAt(0) == '\'' || val.charAt(0) == '"' || val.charAt(0) == '`')
                && val.charAt(val.length() - 1) == val.charAt(0)) {
            c.vType = V_STR;
            c.sv = unescape(val.substring(1, val.length() - 1));
        } else {
            // v4.3.0：剥离单位后缀（10px / 5% ——GKD 允许，语义按数值比较）
            int dn = 0;
            while (dn < val.length() && Character.isDigit(val.charAt(dn))) dn++;
            boolean numeric = dn > 0 && (dn == val.length() || !Character.isLetterOrDigit(val.charAt(dn)));
            if (numeric) {
                try {
                    c.vType = V_INT;
                    c.iv = Long.parseLong(val.substring(0, dn));
                } catch (NumberFormatException e) {
                    throw new ParseException("bad value: [" + s + "]");
                }
            } else {
                throw new ParseException("bad value: [" + s + "]");
            }
        }
        if ("~=".equals(op) || "!~=".equals(op)) {
            try {
                c.rx = Pattern.compile(c.sv);
            } catch (PatternSyntaxException e) {
                throw new ParseException("bad regex: " + c.sv);
            }
        }
        return c;
    }

    static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(++i);
                if (d == 'n') b.append('\n');
                else if (d == 'r') b.append('\r');
                else if (d == 't') b.append('\t');
                else b.append(d);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** 顶层拆分（尊重引号；忽略方括号内的分隔符已由引号覆盖，保守处理） */
    static List<String> splitTop(String s, String sep) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        int n = s.length();
        char quote = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && i + 1 < n) {
                    cur.append(s.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                cur.append(c);
                continue;
            }
            if (c == '[') { // 括号内整体保留
                int end = findBracketEnd(s, i);
                if (end < 0) throw new ParseException("unclosed [");
                cur.append(s, i, end + 1);
                i = end;
                continue;
            }
            if (c == '(') { // 圆括号内整体保留
                int end = findParenEnd(s, i);
                if (end < 0) throw new ParseException("unclosed (");
                cur.append(s, i, end + 1);
                i = end;
                continue;
            }
            if (s.startsWith(sep, i)) {
                out.add(cur.toString());
                cur.setLength(0);
                i += sep.length() - 1;
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    static int findBracketEnd(String s, int start) {
        char quote = 0;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') quote = c;
            else if (c == '[') i = findBracketEnd(s, i); // 嵌套容忍
            else if (c == ']') return i;
        }
        return -1;
    }

    static int findParenEnd(String s, int start) {
        char quote = 0;
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') quote = c;
            else if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /* ══════════ 工具 ══════════ */

    static String vidOf(String fullId) {
        if (fullId == null) return "";
        int slash = fullId.lastIndexOf('/');
        int colon = fullId.lastIndexOf(':');
        int cut = Math.max(slash, colon);
        return cut >= 0 ? fullId.substring(cut + 1) : fullId;
    }

    static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    public String raw() {
        return raw;
    }
}
