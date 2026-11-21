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

    /* ══ v6.0.0 真实密度配对表：设备几何（w,h）→ 该档设备真实出货密度域 ══
     * 1:1 模拟「人在不同设备上操作」= 几何 × 该档真实密度：720p 遗留机
     * 真实密度 1.0~2.0（无 xxxhdpi——720p×4.0 组合在真实世界不存在）；
     * QHD 旗舰真实 3.5~4.0；折叠内屏 ~2.0~2.6；分屏半窗承袭宿主密度。
     * 全 7 档密度的压力交叉（含不真实组合）由各矩阵的条件契约覆盖
     * （可刺→必须安全落点；亚指腹 dp 短边 <12dp→必须拒点）。 */
    private static final Object[][] DENSITY_MAP = {
            {720, 1650, new float[]{1.5f, 2.0f}},          // 720p 直板（Redmi 14C：hdpi~xhdpi）
            {1080, 2400, new float[]{2.0f, 2.6f, 3.0f}},  // 1080p 主流（xhdpi~xxhdpi）
            {1080, 2412, new float[]{2.0f, 2.6f, 3.0f}},  // 1080p 高（Nothing Phone）
            {1220, 2712, new float[]{2.6f, 3.0f, 3.5f}},  // 1220p（POCO：~460ppi）
            {1440, 3200, new float[]{3.5f, 4.0f}},         // 1440p QHD 旗舰（~560ppi）
            {1260, 2844, new float[]{3.0f, 3.5f}},         // 1260p 19.5:9（Mate 70 Pro）
            {1080, 1920, new float[]{2.6f, 3.0f}},         // 16:9 遗留旗舰（420~480dpi）
            {720, 1280, new float[]{1.0f, 1.5f, 2.0f}},   // 16:9 极旧机（mdpi~xhdpi）
            {1080, 2316, new float[]{2.6f, 3.0f}},         // 折叠外屏窄 21.3:9
            {720, 1632, new float[]{1.5f, 2.0f}},          // 折叠外屏超窄 22.5:9（Flip）
            {1812, 2176, new float[]{2.0f, 2.6f}},         // 折叠内屏近方 1:1.2
            {2208, 2480, new float[]{2.0f, 2.6f}},          // 折叠内屏 1.11:1（Pocket）
            {1872, 2148, new float[]{2.0f, 2.6f}},         // 折叠内屏大 1.15:1（Mate X6）
            {1600, 2560, new float[]{2.0f, 2.6f}},         // 平板 16:10（Y700）
            {1752, 2800, new float[]{2.0f, 2.6f}},         // 平板 3:2 偏长（Tab S10+）
            {2048, 2732, new float[]{2.0f, 2.6f}},         // 平板大 4:3（iPad 系对标）
            {1080, 1200, new float[]{2.0f, 2.6f, 3.0f}},   // 分屏半窗竖（承袭宿主 1080p 机）
            {540, 2400, new float[]{2.0f, 2.6f, 3.0f}},    // 分屏半窗横（承袭宿主 1080p 机）
            {1080, 2280, new float[]{2.0f, 2.6f, 3.0f}},   // 刘海大缺 19:9
            {1080, 2340, new float[]{2.0f, 2.6f, 3.0f}},   // 挖孔屏 20:9（Galaxy A）
            {1344, 2992, new float[]{3.5f, 4.0f}},         // 超宽 QHD 20:9（Pixel Pro XL）
            {1080, 1080, new float[]{2.0f, 2.6f}},          // 正方形态（折叠展开）
            {1080, 2520, new float[]{2.6f, 3.0f}},          // 超长 21:9（Xperia）
            {2400, 1080, new float[]{2.0f, 2.6f, 3.0f}},    // 横屏形态（平板/旋转）
    };

    /** 设备几何（w,h）→ 该档设备真实出货密度域；未知几何保守回退 {2.0}。 */
    public static float[] realDensities(int w, int h) {
        for (Object[] row : DENSITY_MAP) {
            if ((Integer) row[0] == w && (Integer) row[1] == h) {
                return (float[]) row[2];
            }
        }
        return new float[]{2.0f};
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

    /* ══ v8.0.2 双 DFS 专项：属性读取/节点访问计数器（性能对照基准用）══
     * CountingNode 包装任意 NodeView，每次属性读/子访问/父访问计数——
     * 旧路径（matchAll+walk 两趟）与新路径（walkFused 单趟）在同一棵
     * 计数树上跑，计数差即真实读取差（JVM 无 binder，计数是确定性证据）。 */
    public static final class Counters {
        public int textRead, descRead, clsRead, visibleRead, boundsRead;
        public int clickableRead, editableRead, scrollableRead, childCountRead;
        public int childCall, parentCall, viewIdRead;
        public int wrapperAlloc;

        public void reset() {
            textRead = descRead = clsRead = visibleRead = boundsRead = 0;
            clickableRead = editableRead = scrollableRead = childCountRead = 0;
            childCall = parentCall = viewIdRead = 0;
            wrapperAlloc = 0;
        }

        public int propertyReads() {
            return textRead + descRead + clsRead + visibleRead + boundsRead
                    + clickableRead + editableRead + scrollableRead + childCountRead + viewIdRead;
        }

        public int total() {
            return propertyReads() + childCall + parentCall;
        }
    }

    /** 计数包装节点：child()/parent() 每次返回新包装（模拟 NodeAdapter 每 getChild 分配）。 */
    public static final class CountingNode implements NodeView {
        public final NodeView inner;
        public final Counters c;

        public CountingNode(NodeView inner, Counters c) {
            this.inner = inner;
            this.c = c;
            c.wrapperAlloc++;
        }

        public String text() {
            c.textRead++;
            return inner.text();
        }

        public String desc() {
            c.descRead++;
            return inner.desc();
        }

        public String cls() {
            c.clsRead++;
            return inner.cls();
        }

        public boolean clickable() {
            c.clickableRead++;
            return inner.clickable();
        }

        public boolean scrollable() {
            c.scrollableRead++;
            return inner.scrollable();
        }

        public boolean editable() {
            c.editableRead++;
            return inner.editable();
        }

        public int[] bounds() {
            c.boundsRead++;
            return inner.bounds();
        }

        public int childCount() {
            c.childCountRead++;
            return inner.childCount();
        }

        public NodeView child(int i) {
            c.childCall++;
            NodeView ch = inner.child(i);
            return ch == null ? null : new CountingNode(ch, c);
        }

        public NodeView parent() {
            c.parentCall++;
            NodeView p = inner.parent();
            return p == null ? null : new CountingNode(p, c);
        }

        public boolean visible() {
            c.visibleRead++;
            return inner.visible();
        }

        public String viewId() {
            c.viewIdRead++;
            return inner.viewId();
        }
    }
}
