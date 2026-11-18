package com.zai.adshield.tests;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.NodeView;

import java.util.ArrayList;
import java.util.List;

/**
 * 假节点树构造器（纯 JVM，模拟真实无障碍控件树形态）
 * 屏幕基准：1080 x 2400（主流安卓机）
 */
class FakeNode implements NodeView {
    static final int SW = 1080;
    static final int SH = 2400;

    String text = "", desc = "", cls = "android.view.View";
    boolean clickable, scrollable, editable;
    String viewId = "";
    boolean visible = true;
    int[] bounds;
    FakeNode parent;
    List<FakeNode> children = new ArrayList<FakeNode>();

    static FakeNode root(String cls) {
        FakeNode n = new FakeNode();
        n.cls = cls;
        n.bounds = new int[]{0, 0, SW, SH};
        return n;
    }

    FakeNode add(FakeNode child) {
        child.parent = this;
        children.add(child);
        return this;
    }

    static FakeNode node(String cls, int l, int t, int r, int b) {
        FakeNode n = new FakeNode();
        n.cls = cls;
        n.bounds = new int[]{l, t, r, b};
        return n;
    }

    FakeNode txt(String s) { this.text = s; return this; }
    FakeNode describe(String s) { this.desc = s; return this; }
    FakeNode click() { this.clickable = true; return this; }
    FakeNode scroll() { this.scrollable = true; return this; }
    FakeNode edit() { this.editable = true; return this; }
    FakeNode vid(String s) { this.viewId = s; return this; }
    FakeNode invisible() { this.visible = false; return this; }

    public String text() { return text; }
    public String desc() { return desc; }
    public String cls() { return cls; }
    public boolean clickable() { return clickable; }
    public boolean scrollable() { return scrollable; }
    public boolean editable() { return editable; }
    public int[] bounds() { return bounds; }
    public int childCount() { return children.size(); }
    public NodeView child(int i) { return children.get(i); }
    public NodeView parent() { return parent; }
    public boolean visible() { return visible; }
    public String viewId() { return viewId; }
}
