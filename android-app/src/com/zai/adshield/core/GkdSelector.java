package com.zai.adshield.core;

import com.zai.adshield.core.AdDecisionCore.NodeView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * GKD 选择器兼容引擎（v4.0.0 订阅融合 · https://gkd.li/guide/selector）
 * ─────────────────────────────────────────────────────────────────
 * 目标：直接吃下 GKD 社区订阅里的选择器规则，让整个 GKD 规则生态
 * 可被本应用导入使用；同时保持「绝不乱点」红线。
 *
 * 支持语法子集（覆盖开屏/弹窗规则的 99% 写法）：
 *  · 属性选择器：TextView[id='x'][text*='跳过'][clickable=true]
 *    - 标签名：TextView / android.widget.TextView / *（GKD 语义：
 *      无点短名 ≡ name='TextView'||name$='.TextView'）
 *    - 属性：name/id/vid/text/desc/clickable/visible/editable/
 *      scrollable/index/depth/w/h
 *      · id 带冒号 → 全名精确匹配；不带冒号 → 末段匹配（兼容社区
 *        短 id 写法，跨版本更稳）
 *      · vid 永远是 viewId 末段（本应用扩展，写规则更短）
 *    - 操作符：= != ^= $= *= !^= !*= !$= ~= !~=（正则）> >= < <=
 *    - 布尔组合：[a='x'||b='y']、[a='x'&&b='y']、[!(a='x')]
 *  · 关系选择器（连接两个属性选择器，强制空格分隔）：
 *      A > B   A 是 B 的祖先      A < B    A 是 B 的直接子节点
 *      A << B  A 是 B 的子孙      A + B    A 是 B 的前置兄弟
 *      A - B   A 是 B 的后置兄弟
 *    （兄弟算子实现为「同父兄弟命中」——无 an+b 元组、不要求紧邻，
 *      保守放宽：兄弟上下文约束已足够强，开屏规则极少依赖精确序号）
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

    /** 单个布尔表达式：attr op 字面量 */
    static final class Cond {
        String attr;
        String op;
        int vType = V_NULL;
        String sv;
        long iv;
        boolean bv;
        Pattern rx; // ~= 预编译

        boolean test(Exe x, NodeView n) {
            String a = attr;
            if ("name".equals(a)) return testName(x, n);
            if ("id".equals(a)) return testId(n.viewId());
            if ("vid".equals(a)) return testStr(vidOf(n.viewId()));
            if ("text".equals(a)) return testStr(nz(n.text()));
            if ("desc".equals(a)) return testStr(nz(n.desc()));
            if ("clickable".equals(a)) return testBool(n.clickable());
            if ("visible".equals(a)) return testBool(n.visible());
            if ("editable".equals(a)) return testBool(n.editable());
            if ("scrollable".equals(a)) return testBool(n.scrollable());
            if ("index".equals(a)) return testInt(x.indexOf(n));
            if ("depth".equals(a)) return testInt(x.depthOf(n));
            if ("w".equals(a)) return testInt(x.widthOf(n));
            if ("h".equals(a)) return testInt(x.heightOf(n));
            return false; // 未知属性：安全侧不匹配
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

    /** 属性选择器：@? tag? bracket*（括号间 AND、括号内 || 、组内 &&） */
    static final class AttrSel {
        String tag;                 // null = *
        boolean at;                 // @ 目标标记
        // brackets[b] = 第 b 个 [] 的 (OR of AND)：括号间 AND（GKD：[a=1][b=1] ≡ [a=1&&b=1]）
        List<List<List<Cond>>> brackets = new ArrayList<List<List<Cond>>>();

        boolean match(Exe x, NodeView n) {
            if (!matchTag(n)) return false;
            for (int b = 0; b < brackets.size(); b++) {
                List<List<Cond>> ors = brackets.get(b);
                boolean ok = false;
                for (int o = 0; o < ors.size() && !ok; o++) {
                    List<Cond> and = ors.get(o);
                    boolean all = true;
                    for (int j = 0; j < and.size(); j++) {
                        if (!and.get(j).test(x, n)) {
                            all = false;
                            break;
                        }
                    }
                    if (all) ok = true;
                }
                if (!ok) return false; // 任一 [] 不满足 → 整个属性选择器不匹配
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

    /** 一条链：attrSels 与 rels 交替（rels[i] 连接 attrSels[i] 与 attrSels[i+1]） */
    static final class Chain {
        List<AttrSel> sels = new ArrayList<AttrSel>();
        List<Character> rels = new ArrayList<Character>(); // '>' '<' '≪'(<<) '+' '-'

        /** 从右往左验证：node 匹配最后一个属性选择器，再逐段向左走关系 */
        NodeView walk(Exe x, NodeView lastNode) {
            int n = sels.size();
            if (n == 0 || !sels.get(n - 1).match(x, lastNode)) return null;
            NodeView target = sels.get(n - 1).at ? lastNode : null;
            NodeView cur = lastNode;
            for (int i = n - 2; i >= 0; i--) {
                char rel = rels.get(i);
                AttrSel left = sels.get(i);
                NodeView next = null;
                if (rel == '>') next = x.findAncestor(cur, left);
                else if (rel == '<') next = x.findDirectChild(cur, left);
                else if (rel == '\u226A') next = x.findDescendant(cur, left);
                else if (rel == '+') next = x.findSibling(cur, left, -1);
                else if (rel == '-') next = x.findSibling(cur, left, +1);
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

        NodeView findAncestor(NodeView from, AttrSel sel) {
            NodeView p = from.parent();
            while (p != null) {
                if (!dec()) return null;
                if (sel.match(this, p)) return p;
                p = p.parent();
            }
            return null;
        }

        NodeView findDirectChild(NodeView of, AttrSel sel) {
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
                if (ch == '<' && i + 1 < n && s.charAt(i + 1) == '<') {
                    c.rels.add('\u226A');
                    i += 2;
                } else {
                    c.rels.add(ch);
                    i++;
                }
                if (c.sels.isEmpty())
                    throw new ParseException("chain starts with rel: " + s);
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
                List<List<Cond>> bracket = parseConds(inner);
                if (!bracket.isEmpty()) a.brackets.add(bracket);
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

    /** [ ... ] 内部 → (OR of AND)：按 || 分组、组内按 && 拆分、支持 !(...) 取反 */
    static List<List<Cond>> parseConds(String inner) {
        List<List<Cond>> orParts = new ArrayList<List<Cond>>();
        List<String> parts = splitTop(inner, "||");
        for (int oi = 0; oi < parts.size(); oi++) {
            String part = parts.get(oi).trim();
            if (part.length() == 0) continue;
            boolean neg = false;
            if (part.startsWith("!(") && part.endsWith(")")) {
                neg = true;
                part = part.substring(2, part.length() - 1);
            } else if (part.startsWith("!")) {
                throw new ParseException("bad negation: [" + inner + "]");
            }
            List<String> ands = splitTop(part, "&&");
            List<Cond> group = new ArrayList<Cond>();
            for (int ai = 0; ai < ands.size(); ai++) {
                Cond cd = parseCond(ands.get(ai).trim());
                if (cd != null) {
                    if (neg) cd = negate(cd);
                    group.add(cd);
                }
            }
            if (!group.isEmpty()) orParts.add(group);
        }
        return orParts;
    }

    /** 取反：交换操作符语义（值不变） */
    static Cond negate(Cond c) {
        Cond r = new Cond();
        r.attr = c.attr;
        r.vType = c.vType;
        r.sv = c.sv;
        r.iv = c.iv;
        r.bv = c.bv;
        r.rx = c.rx;
        r.op = negOp(c.op);
        return r;
    }

    static String negOp(String op) {
        if ("=".equals(op)) return "!=";
        if ("!=".equals(op)) return "=";
        if ("^=".equals(op)) return "!^=";
        if ("$=".equals(op)) return "!$=";
        if ("*=".equals(op)) return "!*=";
        if ("~=".equals(op)) return "!~=";
        if ("!^=".equals(op)) return "^=";
        if ("!$=".equals(op)) return "$=";
        if ("!*=".equals(op)) return "*=";
        if ("!~=".equals(op)) return "~=";
        if (">".equals(op)) return "<=";
        if ("<".equals(op)) return ">=";
        if (">=".equals(op)) return "<";
        if ("<=".equals(op)) return ">";
        return "!=";
    }

    static Cond parseCond(String s) {
        if (s.length() == 0) return null;
        // 定位操作符（属性名 [a-zA-Z_][a-zA-Z0-9_]* 之后）
        int i = 0, n = s.length();
        while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
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
            try {
                c.vType = V_INT;
                c.iv = Long.parseLong(val);
            } catch (NumberFormatException e) {
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
