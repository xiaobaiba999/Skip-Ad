package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore.NodeView;

/**
 * JVM 测试宿主（v5.0.0）：零依赖断言计数跑器 + 模拟控件树。
 * 与 AdDecisionCore 同规约（纯 Java），ECJ 编译后直接运行。
 */
public final class TestHost {

    public static int passed = 0;
    public static int failed = 0;
    public static String currentSuite = "";

    public static void check(boolean cond, String name) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("  ❌ FAIL: " + name);
        }
    }

    public static void checkEq(Object expected, Object actual, String name) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("  ❌ FAIL: " + name + " (expect=" + expected + " actual=" + actual + ")");
        }
    }

    public static void suite(String name) {
        currentSuite = name;
        System.out.println("── " + name + " ──");
    }

    public static void done() {
        System.out.println();
        System.out.println("══════════════════════════════════════");
        System.out.println("  通过 " + passed + " · 失败 " + failed);
        System.out.println("══════════════════════════════════════");
        if (failed > 0) System.exit(1);
    }

    /** 模拟控件节点（测试用 NodeView 实现）。 */
    public static final class SimNode implements NodeView {
        public String text = "";
        public String desc = "";
        public String cls = "android.widget.TextView";
        public String vid = "";
        public boolean clickable;
        public boolean scrollable;
        public boolean editable;
        public boolean visible = true;
        public int[] boundsArr;
        public final java.util.List<SimNode> children = new java.util.ArrayList<SimNode>();
        public SimNode parent;

        public SimNode() {
        }

        public SimNode(String text, int l, int t, int r, int b) {
            this.text = text;
            this.boundsArr = new int[]{l, t, r, b};
        }

        public SimNode add(SimNode c) {
            c.parent = this;
            children.add(c);
            return this;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public String desc() {
            return desc;
        }

        @Override
        public String cls() {
            return cls;
        }

        @Override
        public boolean clickable() {
            return clickable;
        }

        @Override
        public boolean scrollable() {
            return scrollable;
        }

        @Override
        public boolean editable() {
            return editable;
        }

        @Override
        public int[] bounds() {
            return boundsArr;
        }

        @Override
        public int childCount() {
            return children.size();
        }

        @Override
        public NodeView child(int i) {
            return children.get(i);
        }

        @Override
        public NodeView parent() {
            return parent;
        }

        @Override
        public boolean visible() {
            return visible;
        }

        @Override
        public String viewId() {
            return vid;
        }
    }

    /** 1080×2400 标准屏的跳过键模板（右上角小按钮）。 */
    public static SimNode skipButton(String label) {
        SimNode n = new SimNode(label, 920, 80, 1050, 150);
        n.clickable = true;
        return n;
    }
}
