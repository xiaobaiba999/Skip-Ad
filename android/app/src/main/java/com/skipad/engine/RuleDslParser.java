package com.skipad.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 统一规则 DSL 解析器（v5.4.0）。
 *
 * <p>把 GKD 快照选择器语法解析为 Matcher 链，供 {@link MatchingEngine} 五层规则 DFS
 * 消费。与 Web 端 src/lib/rule-dsl.ts 保持 1:1，双端判定一致性由 CI 金样本集校验：
 * 同一批选择器表达式在 TS / Java 两端解析出的 AST（{@link #describe()} 规范形态）与
 * 匹配结果必须逐条一致（见 docs/GKD-COMPAT.md「互认测试」）。
 *
 * <p>支持语法（GKD 100% 兼容子集）：
 * <ul>
 *   <li>属性选择器：{@code [vid="btn_skip" && text*="跳过"]}；属性操作符 6 种：
 *       {@code =}（相等）、{@code !=}（不等）、{@code *=}（包含）、{@code ^=}（前缀）、
 *       {@code $=}（后缀）、{@code ~=}（正则）</li>
 *   <li>节点属性：vid / id / name / text / desc / clickable / focusable / checkable /
 *       checked / editable / enabled / longClickable / scrollable / visibleToUser /
 *       index / depth（未知属性 → 编译警告 + 条件恒不成立，保守降级）</li>
 *   <li>关系符 5 种（语义：右段节点相对左段节点的位置）：
 *       {@code <<n}（右段是左段的第 n 代祖先；n 缺省为「任意祖先」，n&gt;=1 精确匹配）、
 *       {@code <}（父）、{@code >}（子）、{@code -}（下一兄弟）、{@code +}（上一兄弟）</li>
 *   <li>逻辑组合：{@code &&}（与，优先级高）、{@code ||}（或）</li>
 *   <li>目标标记：{@code @}（规则命中后要点击的节点；缺省目标 = 最后一个选择器节点）</li>
 *   <li>通配段：{@code *}（匹配任意节点，用于只约束关系不约束属性的场景）</li>
 * </ul>
 *
 * <p>示例：{@code [vid="ad_container"] > @[text*="跳过" && clickable]} 表示——
 * 命中「父节点 vid 为 ad_container、自身文本含跳过且可点击」的节点并代点。
 *
 * <p>性能约束：所有匹配均受 {@link MatchBudget} 步数预算约束（来自 PowerGovernor
 * 策略），防止单条复杂订阅规则拖垮扫描主链（李跳跳教训：规则匹配效率必须与规则量解耦）。
 */
public final class RuleDslParser {

    private RuleDslParser() {
    }

    // ───────────────────────────── 常量 ─────────────────────────────

    /** 属性操作符（与 GKD 规范一致，共 6 种） */
    public static final String OP_EQ = "=";
    public static final String OP_NE = "!=";
    public static final String OP_CONTAINS = "*=";
    public static final String OP_STARTS = "^=";
    public static final String OP_ENDS = "$=";
    public static final String OP_REGEX = "~=";

    /** 支持的节点属性清单（与 GKD 快照属性对齐；布尔属性按 "true"/"false" 字符串比较） */
    private static final Set<String> KNOWN_ATTRS = new HashSet<>();

    static {
        KNOWN_ATTRS.add("vid");
        KNOWN_ATTRS.add("id");
        KNOWN_ATTRS.add("name");
        KNOWN_ATTRS.add("text");
        KNOWN_ATTRS.add("desc");
        KNOWN_ATTRS.add("clickable");
        KNOWN_ATTRS.add("longClickable");
        KNOWN_ATTRS.add("enabled");
        KNOWN_ATTRS.add("focusable");
        KNOWN_ATTRS.add("checkable");
        KNOWN_ATTRS.add("checked");
        KNOWN_ATTRS.add("editable");
        KNOWN_ATTRS.add("scrollable");
        KNOWN_ATTRS.add("visibleToUser");
        KNOWN_ATTRS.add("index");
        KNOWN_ATTRS.add("depth");
    }

    // ───────────────────────── 节点抽象（双端同构） ─────────────────────────

    /**
     * 无障碍节点抽象。Android 侧由 AccessibilityNodeInfo 适配（SkipAdService.AndroidNode），
     * 引擎侧由 NodeCache 视图适配（MatchingEngine.CacheNodeView，一次遍历收集后零 IPC 复用），
     * JVM 侧由 CI 金样本集驱动——同一接口保证双端判定同源。
     */
    public interface NodeLike {
        CharSequence text();

        CharSequence desc();

        /** viewIdResourceName 的 entry 段（com.a.b:id/skip → skip） */
        String vid();

        /** 完整 viewIdResourceName（com.a.b:id/skip） */
        String viewId();

        /** 类简名（com.bytedance.ads.Widget → Widget） */
        String name();

        /** 布尔属性；未知返回 null（三态，勿用基本类型） */
        Boolean boolFlag(String flag);

        /** 兄弟序号（父节点内下标，未知返回 -1） */
        int index();

        /** 树深度（根为 0，未知返回 -1） */
        int depth();

        NodeLike parent();

        int childCount();

        NodeLike child(int i);

        /** 屏幕坐标 {left, top, right, bottom}；不可用返回 null 或长度不足 4 的数组 */
        int[] bounds();
    }

    // ───────────────────────────── 解析入口 ─────────────────────────────

    /** 语法错误（含位置信息，导入订阅时可回显给用户） */
    public static final class DslSyntaxException extends Exception {
        DslSyntaxException(String message) {
            super(message);
        }
    }

    /** 编译结果：表达式 + 警告（降级信息）列表 */
    public static final class ParsedRule {
        public final String source;
        public final DslExpression expression;
        public final boolean ok;
        public final List<String> warnings;

        ParsedRule(String source, DslExpression expression, boolean ok, List<String> warnings) {
            this.source = source;
            this.expression = expression;
            this.ok = ok;
            this.warnings = warnings;
        }
    }

    /**
     * 编译一条选择器表达式。语法错误不抛异常而是落入 warnings（导入订阅时逐条降级，
     * 绝不因单条坏规则中断整包导入——GKD 订阅兼容的关键承诺）。
     */
    public static ParsedRule compile(String expr) {
        List<String> warnings = new ArrayList<>();
        if (expr == null) {
            warnings.add("表达式为 null");
            return new ParsedRule("", null, false, warnings);
        }
        String trimmed = expr.trim();
        if (trimmed.isEmpty()) {
            warnings.add("表达式为空");
            return new ParsedRule(expr, null, false, warnings);
        }
        try {
            DslExpression dsl = parse(trimmed);
            validate(dsl, warnings);
            return new ParsedRule(expr, dsl, true, warnings);
        } catch (DslSyntaxException e) {
            warnings.add(e.getMessage());
            return new ParsedRule(expr, null, false, warnings);
        }
    }

    /** 严格解析（硬错误抛异常），供单元测试与 CI 金样本集断言使用 */
    public static DslExpression parse(String expr) throws DslSyntaxException {
        List<String> orParts = splitTopLevel(expr, "||");
        List<List<UnitChain>> orGroups = new ArrayList<>();
        for (String orPart : orParts) {
            List<String> andParts = splitTopLevel(orPart, "&&");
            List<UnitChain> units = new ArrayList<>();
            for (String andPart : andParts) {
                String s = andPart.trim();
                if (s.isEmpty()) {
                    throw new DslSyntaxException("逻辑组合两侧存在空单元: " + expr);
                }
                units.add(parseChain(s));
            }
            orGroups.add(units);
        }
        return new DslExpression(orGroups);
    }

    /** 便捷匹配（编译 + 匹配一步到位，预算 512 步） */
    public static boolean matches(String expr, NodeLike root) {
        ParsedRule pr = compile(expr);
        return pr.ok && pr.expression.match(root, MatchBudget.of(512)).matched;
    }

    /** 静态校验：未知属性 / 正则语法错误 → 警告（条件保守降级为恒不成立） */
    private static void validate(DslExpression dsl, List<String> warnings) {
        for (List<UnitChain> group : dsl.orGroups) {
            for (UnitChain unit : group) {
                for (Segment seg : unit.segments) {
                    for (Condition cond : seg.conditions) {
                        if (!KNOWN_ATTRS.contains(cond.attr)) {
                            warnings.add("未知属性 [" + cond.attr + "]（降级：该条件恒不成立）");
                        }
                        if (OP_REGEX.equals(cond.op) && cond.compiledRegex() == null) {
                            warnings.add("正则语法错误 [" + cond.value + "]（降级：该条件恒不成立）");
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────── AST 数据模型 ─────────────────────────

    /** 表达式：析取范式（|| 的 && 的链） */
    public static final class DslExpression {
        final List<List<UnitChain>> orGroups;

        DslExpression(List<List<UnitChain>> orGroups) {
            this.orGroups = orGroups;
        }

        /**
         * 在以 root 为根的树上匹配。
         *
         * @return 命中时携带目标节点（@ 标记段；无 @ 时取第一个命中单元的目标段）
         */
        public MatchResult match(NodeLike root, MatchBudget budget) {
            if (root == null) {
                return MatchResult.miss();
            }
            for (List<UnitChain> group : orGroups) {
                boolean allMatch = true;
                NodeLike explicitTarget = null;
                NodeLike fallbackTarget = null;
                for (UnitChain unit : group) {
                    NodeLike hit = unit.match(root, budget);
                    if (hit == null) {
                        allMatch = false;
                        break;
                    }
                    if (unit.hasExplicitTarget() && explicitTarget == null) {
                        explicitTarget = hit;
                    } else if (fallbackTarget == null) {
                        fallbackTarget = hit;
                    }
                }
                if (allMatch) {
                    NodeLike target = explicitTarget != null ? explicitTarget : fallbackTarget;
                    return new MatchResult(true, target);
                }
            }
            return MatchResult.miss();
        }

        /** 规范形态描述（CI 金样本集用它断言 TS/Java 双端 AST 一致） */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < orGroups.size(); i++) {
                if (i > 0) {
                    sb.append(" || ");
                }
                List<UnitChain> group = orGroups.get(i);
                for (int j = 0; j < group.size(); j++) {
                    if (j > 0) {
                        sb.append(" && ");
                    }
                    sb.append(group.get(j).describe());
                }
            }
            return sb.toString();
        }
    }

    /** 匹配结果 */
    public static final class MatchResult {
        public final boolean matched;
        public final NodeLike node;

        private MatchResult(boolean matched, NodeLike node) {
            this.matched = matched;
            this.node = node;
        }

        static MatchResult hit(NodeLike node) {
            return new MatchResult(true, node);
        }

        static MatchResult miss() {
            return new MatchResult(false, null);
        }
    }

    /** 匹配步数预算（节点访问次数；由 PowerGovernor 策略注入，防止复杂规则拖垮主链） */
    public static final class MatchBudget {
        public int steps;

        private MatchBudget(int steps) {
            this.steps = steps;
        }

        public static MatchBudget of(int steps) {
            return new MatchBudget(Math.max(1, steps));
        }

        public boolean exhausted() {
            return steps <= 0;
        }

        /** 花费一步；预算耗尽返回 false（调用方应立即剪枝返回） */
        public boolean spend() {
            if (steps <= 0) {
                return false;
            }
            steps--;
            return true;
        }
    }

    /** 关系符（语义：右段节点相对左段节点的位置） */
    public static final class Relation {
        public enum Kind {PARENT, ANCESTOR, CHILD, NEXT_SIBLING, PREV_SIBLING}

        public final Kind kind;
        /** 仅 ANCESTOR 有效：n=0 表示任意祖先（GKD {@code <<} 缺省形态），n>=1 表示精确第 n 代 */
        public final int n;

        private Relation(Kind kind, int n) {
            this.kind = kind;
            this.n = n;
        }

        static Relation parent() {
            return new Relation(Kind.PARENT, 1);
        }

        static Relation ancestor(int n) {
            return new Relation(Kind.ANCESTOR, Math.max(0, n));
        }

        static Relation child() {
            return new Relation(Kind.CHILD, 1);
        }

        static Relation nextSibling() {
            return new Relation(Kind.NEXT_SIBLING, 1);
        }

        static Relation prevSibling() {
            return new Relation(Kind.PREV_SIBLING, 1);
        }

        public String describe() {
            switch (kind) {
                case PARENT:
                    return "<";
                case ANCESTOR:
                    return n <= 0 ? "<<" : "<<" + n;
                case CHILD:
                    return ">";
                case NEXT_SIBLING:
                    return "-";
                case PREV_SIBLING:
                    return "+";
                default:
                    return "?";
            }
        }
    }

    /** 单条属性条件：attr op value */
    public static final class Condition {
        public final String attr;
        public final String op;
        public final String value;
        private Pattern regex; // ~= 懒编译缓存

        Condition(String attr, String op, String value) {
            this.attr = attr;
            this.op = op;
            this.value = value;
        }

        Pattern compiledRegex() {
            if (regex == null) {
                try {
                    regex = Pattern.compile(value);
                } catch (PatternSyntaxException e) {
                    regex = null; // 恒不成立（保守降级），语法错误已在 validate 阶段告警
                }
            }
            return regex;
        }

        boolean matches(NodeLike node) {
            String actual = resolveAttr(node, attr);
            if (actual == null) {
                return false;
            }
            switch (op) {
                case OP_EQ:
                    return looseEquals(actual, value);
                case OP_NE:
                    return !looseEquals(actual, value);
                case OP_CONTAINS:
                    return actual.contains(value);
                case OP_STARTS:
                    return actual.startsWith(value);
                case OP_ENDS:
                    return actual.endsWith(value);
                case OP_REGEX:
                    Pattern p = compiledRegex();
                    return p != null && p.matcher(actual).find();
                default:
                    return false;
            }
        }

        public String describe() {
            return attr + op + "\"" + value + "\"";
        }

        /** 宽松相等：数字按数值比（index/depth）；布尔不区分大小写；其余字符串精确比 */
        private static boolean looseEquals(String a, String b) {
            Long na = tryParseLong(a);
            Long nb = tryParseLong(b);
            if (na != null && nb != null) {
                return na.longValue() == nb.longValue();
            }
            return a.equals(b) || a.equalsIgnoreCase(b);
        }

        private static Long tryParseLong(String s) {
            if (s == null || s.isEmpty()) {
                return null;
            }
            try {
                return Long.valueOf(Long.parseLong(s.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** 选择器段：多个条件的与（或通配 *，通配段不参与条件判定） */
    public static final class Segment {
        public final boolean target; // @ 标记
        public final boolean wildcard;
        public final List<Condition> conditions;

        private Segment(boolean target, boolean wildcard, List<Condition> conditions) {
            this.target = target;
            this.wildcard = wildcard;
            this.conditions = conditions;
        }

        Segment(boolean target, List<Condition> conditions) {
            this(target, false, conditions);
        }

        static Segment wildcard(boolean target) {
            return new Segment(target, true, new ArrayList<Condition>());
        }

        boolean matches(NodeLike node) {
            if (wildcard) {
                return true;
            }
            for (Condition cond : conditions) {
                if (!cond.matches(node)) {
                    return false;
                }
            }
            return true;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (target) {
                sb.append('@');
            }
            if (wildcard) {
                sb.append('*');
                return sb.toString();
            }
            sb.append('[');
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sb.append(" && ");
                }
                sb.append(conditions.get(i).describe());
            }
            sb.append(']');
            return sb.toString();
        }
    }

    /** 选择器链：段 + 关系符交替（段数 = 关系数 + 1） */
    public static final class UnitChain {
        final List<Segment> segments;
        final List<Relation> relations;

        UnitChain(List<Segment> segments, List<Relation> relations) {
            if (segments.size() != relations.size() + 1) {
                throw new IllegalArgumentException("段数与关系数不匹配");
            }
            this.segments = segments;
            this.relations = relations;
        }

        boolean hasExplicitTarget() {
            for (Segment seg : segments) {
                if (seg.target) {
                    return true;
                }
            }
            return false;
        }

        /** 目标段下标：@ 标记段；缺省最后一段 */
        int targetIndex() {
            for (int i = 0; i < segments.size(); i++) {
                if (segments.get(i).target) {
                    return i;
                }
            }
            return segments.size() - 1;
        }

        /**
         * 全树匹配：迭代栈（非递归）遍历，锚定目标段——候选节点先过目标段选择器，
         * 再向两侧按关系符回溯验证（多候选回溯，预算耗尽即剪枝）。
         */
        NodeLike match(NodeLike root, MatchBudget budget) {
            int anchor = targetIndex();
            Deque<NodeLike> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                if (budget.exhausted()) {
                    return null;
                }
                NodeLike n = stack.pop();
                if (n == null || !budget.spend()) {
                    continue;
                }
                if (segments.get(anchor).matches(n)
                        && expandLeft(anchor - 1, n, budget)
                        && expandRight(anchor + 1, n, budget)) {
                    return n;
                }
                for (int i = 0; i < n.childCount(); i++) {
                    NodeLike child = n.child(i);
                    if (child != null) {
                        stack.push(child);
                    }
                }
            }
            return null;
        }

        /** 向左验证：relations.get(segIdx) 连接 segIdx 与 segIdx+1 两段 */
        private boolean expandLeft(int segIdx, NodeLike v, MatchBudget budget) {
            if (segIdx < 0) {
                return true;
            }
            Relation rel = relations.get(segIdx);
            List<NodeLike> candidates = leftCandidates(v, rel, budget);
            for (NodeLike u : candidates) {
                if (u == null || !budget.spend()) {
                    continue;
                }
                if (segments.get(segIdx).matches(u) && expandLeft(segIdx - 1, u, budget)) {
                    return true;
                }
            }
            return false;
        }

        /** 向右验证：relations.get(segIdx - 1) 连接 segIdx-1 与 segIdx 两段 */
        private boolean expandRight(int segIdx, NodeLike v, MatchBudget budget) {
            if (segIdx >= segments.size()) {
                return true;
            }
            Relation rel = relations.get(segIdx - 1);
            List<NodeLike> candidates = rightCandidates(v, rel, budget);
            for (NodeLike w : candidates) {
                if (w == null || !budget.spend()) {
                    continue;
                }
                if (segments.get(segIdx).matches(w) && expandRight(segIdx + 1, w, budget)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 左侧候选：找 u 使 v 是 u 的 rel 节点（v 匹配右段，u 匹配左段）。
         * A < B（B 是 A 的父）→ u ∈ B.children；A > B（B 是 A 的子）→ u = B.parent；
         * A - B（B 是 A 的下一兄弟）→ u 是 B 的上一兄弟；A + B 反之。
         */
        private static List<NodeLike> leftCandidates(NodeLike v, Relation rel, MatchBudget budget) {
            List<NodeLike> out = new ArrayList<>(2);
            switch (rel.kind) {
                case PARENT:
                    appendChildren(out, v, budget);
                    break;
                case CHILD:
                    out.add(v.parent());
                    break;
                case ANCESTOR:
                    if (rel.n <= 0) {
                        appendDescendants(out, v, 0, budget); // 任意深度后代
                    } else {
                        appendDescendants(out, v, rel.n, budget); // 精确 n 层之下
                    }
                    break;
                case NEXT_SIBLING:
                    out.add(siblingOf(v, -1));
                    break;
                case PREV_SIBLING:
                    out.add(siblingOf(v, 1));
                    break;
                default:
                    break;
            }
            return out;
        }

        /** 右侧候选：找 w 使 w 是 v 的 rel 节点 */
        private static List<NodeLike> rightCandidates(NodeLike v, Relation rel, MatchBudget budget) {
            List<NodeLike> out = new ArrayList<>(2);
            switch (rel.kind) {
                case PARENT:
                    out.add(v.parent());
                    break;
                case CHILD:
                    appendChildren(out, v, budget);
                    break;
                case ANCESTOR:
                    if (rel.n <= 0) {
                        appendAncestors(out, v, budget); // 任意祖先
                    } else {
                        NodeLike p = v;
                        for (int i = 0; i < rel.n && p != null; i++) {
                            p = p.parent();
                        }
                        out.add(p);
                    }
                    break;
                case NEXT_SIBLING:
                    out.add(siblingOf(v, 1));
                    break;
                case PREV_SIBLING:
                    out.add(siblingOf(v, -1));
                    break;
                default:
                    break;
            }
            return out;
        }

        private static void appendChildren(Collection<NodeLike> out, NodeLike v, MatchBudget budget) {
            for (int i = 0; i < v.childCount(); i++) {
                if (budget.exhausted()) {
                    return;
                }
                NodeLike c = v.child(i);
                if (c != null) {
                    out.add(c);
                }
            }
        }

        /** depth 为 0 时收集全部严格后代，否则只收集恰好 depth 层之下的节点 */
        private static void appendDescendants(List<NodeLike> out, NodeLike v, int depth, MatchBudget budget) {
            Deque<NodeLike> frontier = new ArrayDeque<>();
            appendChildren(frontier, v, budget);
            int level = 1;
            while (!frontier.isEmpty() && !budget.exhausted()) {
                if (depth > 0 && level == depth) {
                    out.addAll(frontier);
                    return;
                }
                Deque<NodeLike> next = new ArrayDeque<>();
                for (NodeLike n : frontier) {
                    appendChildren(next, n, budget);
                }
                frontier = next;
                level++;
            }
        }

        private static void appendAncestors(List<NodeLike> out, NodeLike v, MatchBudget budget) {
            NodeLike p = v.parent();
            while (p != null && !budget.exhausted()) {
                out.add(p);
                p = p.parent();
            }
        }

        private static NodeLike siblingOf(NodeLike v, int delta) {
            NodeLike p = v.parent();
            if (p == null) {
                return null;
            }
            int idx = v.index();
            if (idx < 0) {
                return null;
            }
            int t = idx + delta;
            if (t < 0 || t >= p.childCount()) {
                return null;
            }
            return p.child(t);
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                if (i > 0) {
                    sb.append(' ').append(relations.get(i - 1).describe()).append(' ');
                }
                sb.append(segments.get(i).describe());
            }
            return sb.toString();
        }
    }

    // ───────────────────────── 解析实现 ─────────────────────────

    /** 顶层切分（跳过引号与方括号内的分隔符） */
    private static List<String> splitTopLevel(String s, String delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuote) {
                cur.append(c);
                if (c == '\\' && i + 1 < s.length()) {
                    cur.append(s.charAt(++i));
                } else if (c == quoteChar) {
                    inQuote = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
                cur.append(c);
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && matchesAt(s, i, delimiter)) {
                parts.add(cur.toString());
                cur.setLength(0);
                i += delimiter.length() - 1;
                continue;
            }
            cur.append(c);
        }
        parts.add(cur.toString());
        return parts;
    }

    private static boolean matchesAt(String s, int i, String token) {
        if (i + token.length() > s.length()) {
            return false;
        }
        return s.regionMatches(i, token, 0, token.length());
    }

    private static UnitChain parseChain(String input) throws DslSyntaxException {
        Cursor c = new Cursor(input);
        List<Segment> segs = new ArrayList<>();
        List<Relation> rels = new ArrayList<>();
        c.skipWs();
        if (c.eof()) {
            throw new DslSyntaxException("空选择器单元: " + input);
        }
        while (true) {
            segs.add(parseSegment(c));
            c.skipWs();
            if (c.eof()) {
                break;
            }
            rels.add(parseRelation(c));
            c.skipWs();
            if (c.eof()) {
                throw new DslSyntaxException("关系符后缺少节点选择器: " + input);
            }
        }
        return new UnitChain(segs, rels);
    }

    private static Segment parseSegment(Cursor c) throws DslSyntaxException {
        boolean target = false;
        if (!c.eof() && c.peek() == '@') {
            target = true;
            c.pos++;
            c.skipWs();
        }
        if (c.eof()) {
            throw new DslSyntaxException("期望选择器段，位置 " + c.pos);
        }
        if (c.peek() == '*') {
            c.pos++;
            return Segment.wildcard(target);
        }
        if (c.peek() != '[') {
            throw new DslSyntaxException("期望 '[' 开始属性选择器，位置 " + c.pos);
        }
        c.pos++;
        List<Condition> conds = new ArrayList<>();
        c.skipWs();
        if (!c.eof() && c.peek() == ']') {
            c.pos++;
            return new Segment(target, conds); // 空段 [] 等价通配（GKD 容错语义）
        }
        while (true) {
            conds.add(parseCondition(c));
            c.skipWs();
            if (!c.eof() && c.peek() == ']') {
                c.pos++;
                break;
            }
            if (matchesAt(c.s, c.pos, "&&")) {
                c.pos += 2;
                c.skipWs();
                continue;
            }
            throw new DslSyntaxException("条件之间需要 '&&'，位置 " + c.pos);
        }
        return new Segment(target, conds);
    }

    private static Condition parseCondition(Cursor c) throws DslSyntaxException {
        int start = c.pos;
        while (c.pos < c.s.length() && isAttrChar(c.s.charAt(c.pos))) {
            c.pos++;
        }
        if (c.pos == start) {
            throw new DslSyntaxException("期望属性名，位置 " + c.pos);
        }
        String attr = c.s.substring(start, c.pos);
        c.skipWs();
        String op = parseOperator(c);
        c.skipWs();
        String value = parseValue(c);
        return new Condition(attr, op, value);
    }

    private static boolean isAttrChar(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                || (ch >= '0' && ch <= '9') || ch == '_';
    }

    private static String parseOperator(Cursor c) throws DslSyntaxException {
        // 两字符操作符优先（防止 "=！" 被误读为 "="）
        String[] ordered = {OP_NE, OP_CONTAINS, OP_STARTS, OP_ENDS, OP_REGEX, OP_EQ};
        for (String op : ordered) {
            if (matchesAt(c.s, c.pos, op)) {
                c.pos += op.length();
                return op;
            }
        }
        throw new DslSyntaxException("未知属性操作符，位置 " + c.pos);
    }

    private static String parseValue(Cursor c) throws DslSyntaxException {
        if (c.eof()) {
            throw new DslSyntaxException("缺少属性值，位置 " + c.pos);
        }
        char q = c.peek();
        if (q == '"' || q == '\'') {
            c.pos++;
            StringBuilder sb = new StringBuilder();
            while (c.pos < c.s.length()) {
                char ch = c.s.charAt(c.pos++);
                if (ch == '\\' && c.pos < c.s.length()) {
                    sb.append(c.s.charAt(c.pos++));
                    continue;
                }
                if (ch == q) {
                    return sb.toString();
                }
                sb.append(ch);
            }
            throw new DslSyntaxException("引号未闭合，位置 " + c.pos);
        }
        int start = c.pos;
        while (c.pos < c.s.length()) {
            char ch = c.s.charAt(c.pos);
            if (ch == ']' || ch == '&' || Character.isWhitespace(ch)) {
                break;
            }
            c.pos++;
        }
        if (c.pos == start) {
            throw new DslSyntaxException("缺少属性值，位置 " + c.pos);
        }
        return c.s.substring(start, c.pos);
    }

    private static Relation parseRelation(Cursor c) throws DslSyntaxException {
        if (c.eof()) {
            throw new DslSyntaxException("期望关系符，位置 " + c.pos);
        }
        char ch = c.peek();
        if (ch == '<') {
            if (c.pos + 1 < c.s.length() && c.s.charAt(c.pos + 1) == '<') {
                c.pos += 2;
                int n = 0; // 缺省 0 = 任意祖先（GKD << 无数字形态）
                int start = c.pos;
                while (c.pos < c.s.length() && Character.isDigit(c.s.charAt(c.pos))) {
                    c.pos++;
                }
                if (c.pos > start) {
                    n = Integer.parseInt(c.s.substring(start, c.pos));
                }
                return Relation.ancestor(n);
            }
            c.pos++;
            return Relation.parent();
        }
        if (ch == '>') {
            c.pos++;
            return Relation.child();
        }
        if (ch == '-') {
            c.pos++;
            return Relation.nextSibling();
        }
        if (ch == '+') {
            c.pos++;
            return Relation.prevSibling();
        }
        throw new DslSyntaxException("期望关系符 <<n / < / > / - / +，位置 " + c.pos);
    }

    private static final class Cursor {
        final String s;
        int pos;

        Cursor(String s) {
            this.s = s;
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        boolean eof() {
            return pos >= s.length();
        }

        char peek() {
            return s.charAt(pos);
        }
    }

    /** 属性取值：null 表示该属性在此节点不可用（条件判 false，保守降级） */
    static String resolveAttr(NodeLike node, String attr) {
        if (node == null) {
            return null;
        }
        switch (attr) {
            case "text":
                return node.text() == null ? null : node.text().toString();
            case "desc":
                return node.desc() == null ? null : node.desc().toString();
            case "vid":
                return node.vid();
            case "id":
                return node.viewId();
            case "name":
                return node.name();
            case "index":
                return node.index() < 0 ? null : String.valueOf(node.index());
            case "depth":
                return node.depth() < 0 ? null : String.valueOf(node.depth());
            default:
                Boolean flag = node.boolFlag(attr);
                return flag == null ? null : (flag ? "true" : "false");
        }
    }

    /** 局部格式化工具（保留给日志/调试输出使用） */
    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
