package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.TapPoint;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * v5.9.0 设备自适应触点矩阵测试（用户实锤：用户设备不同就可能导致
 * 多点或无点——误触；模拟 N 多品牌 × N 多机型测试修复）。
 *
 * <p>矩阵：24 类真实设备几何（主流直板 720p~1440p · 16:9 遗留 · 折叠
 * 外屏窄幅/Flip 超窄/内屏近方 · 平板 3:2·4:3 · 分屏半窗横竖 · 刘海/
 * 挖孔 · 超宽 QHD · 正方形态 · 超长 21:9 · 横屏）× 每类 9 场景：</p>
 *
 * <ol>
 * <li>S1 半裁剪标签：跳过键右半被父容器裁掉 → 触点必落在【可见左半】
 *   （原始几何中心在不可见区=点旁边内容误触——v5.9 前真实事故面）；</li>
 * <li>S2 标签在窗外（分屏/折叠另半区）→ tapRect 拒绝（null），
 *   绝不把触点派到另半屏他应用上；</li>
 * <li>S3 可见缝过小（&lt; MIN_VISIBLE_SIDE）→ pick 拒绝（盲刺拒绝
 *   ——点了大概率落到旁边未知物=误触，宁可无障碍代点）；</li>
 * <li>S4 复验象限轮换：attempt 0~4 五点全在可见矩形内且 ≥3 个互不相同
 *   （同候选复验不再盲点同一坐标=「多点无效果」根治）；</li>
 * <li>S5 边缘内缩：任意 attempt 触点距可见边 ≥ min(EDGE_INSET, 边/4)
 *   （圆角/水波纹/刘海安全区死区防线——绝无贴边触点）；</li>
 * <li>S6 覆盖层拦截：触点被 Z 序更高窗口（状态栏+输入法）包含 →
 *   coveredByUpperWindow=true → 拒发手势（键盘弹出点中乱打字=误触）；
 *   未被包含的触点 → false（正常放行）；</li>
 * <li>S7 不可见祖先：容器 visible=false → visibleBounds=null → 拒点
 *   （陷阱/死区绝不出手）；</li>
 * <li>S8 超宽扁标签（长横幅状跳过条）：触点纵向恒居中带（40%~60% 高度
 *   带），绝不贴上下边（水波纹/圆角高危带）；</li>
 * <li>S9 引擎全链路：标准开屏树 walk→decide 出手 → tapRect+pick 落点
 *   在跳过键可见 bounds 内（决策触点在任意设备几何上安全）。</li>
 * </ol>
 *
 * <p>另附 6 项单元级断言（intersect 基础几何 · pick 非法输入 ·
 * MIN_VISIBLE_SIDE 边界值 · 空覆盖表 · 轮换档位数 · 64 层深链防炸）。</p>
 */
public final class TapPointMatrixTest {

    /* ── 24 类设备几何（名称/宽/高——真实分辨率档） ── */
    static final String[][] GEOM = {
            {"720p 直板 20:9（Redmi 14C 档）", "720", "1650"},
            {"1080p 直板 20:9（主流档）", "1080", "2400"},
            {"1080p 直板 20:9 高（Nothing 档）", "1080", "2412"},
            {"1220p 直板 20:9（POCO 档）", "1220", "2712"},
            {"1440p 直板 20:9（Xiaomi 15 档）", "1440", "3200"},
            {"1260p 直板 19.5:9（Mate 70 Pro 档）", "1260", "2844"},
            {"16:9 遗留直板（旧机档）", "1080", "1920"},
            {"16:9 低端遗留（极旧机档）", "720", "1280"},
            {"折叠外屏窄 21.3:9（Fold 档）", "1080", "2316"},
            {"折叠外屏超窄 22.5:9（Flip 档）", "720", "1632"},
            {"折叠内屏近方 1:1.2（Fold 内屏档）", "1812", "2176"},
            {"折叠内屏 1.11:1（Pocket 内屏档）", "2208", "2480"},
            {"折叠内屏大 1.15:1（Mate X6 内屏档）", "1872", "2148"},
            {"平板 16:10（拯救者 Y700 档）", "1600", "2560"},
            {"平板 3:2 偏长（Tab S10+ 档）", "1752", "2800"},
            {"平板大 4:3（iPad 系对标档）", "2048", "2732"},
            {"分屏半窗竖（上/下半屏档）", "1080", "1200"},
            {"分屏半窗横（左/右半屏档）", "540", "2400"},
            {"刘海大缺 19:9（iPhone 形态对标档）", "1080", "2280"},
            {"挖孔屏 20:9（Galaxy A 档）", "1080", "2340"},
            {"超宽 QHD 20:9（Pixel Pro XL 档）", "1344", "2992"},
            {"正方形态（折叠展开正方档）", "1080", "1080"},
            {"超长 21:9（Xperia 档）", "1080", "2520"},
            {"横屏形态（平板/旋转档）", "2400", "1080"},
    };

    public static void main(String[] args) {
        int geoOkTotal = 0;
        for (String[] g : GEOM) {
            String name = g[0];
            int w = Integer.parseInt(g[1]);
            int h = Integer.parseInt(g[2]);
            TestHost.suite(name + "（" + w + "×" + h + "）");
            int ok = 0;

            /* S1 半裁剪标签：跳过键右半被父容器（宽 0.8w 的可点祖先）裁掉 */
            SimNode root = new SimNode();
            root.boundsArr = new int[]{0, 0, w, h};
            SimNode holder = new SimNode();
            holder.boundsArr = new int[]{0, 0, (int) (w * 0.8), h};
            holder.clickable = true;
            root.add(holder);
            // 标签横跨 0.7w~0.97w（右半 0.8w~0.97w 在祖先外=被裁剪区）
            SimNode label = new SimNode("跳过", (int) (w * 0.70), (int) (h * 0.03),
                    (int) (w * 0.97), (int) (h * 0.065));
            label.clickable = true;
            holder.add(label);
            int[] s1 = TapPoint.tapRect(label, label.bounds(), holder.bounds());
            boolean s1Ok = s1 != null && s1[2] <= (int) (w * 0.8); // 触点矩形被钳到可见侧
            int[] s1pt = s1Ok ? TapPoint.pick(s1, 0) : null;
            s1Ok = s1pt != null && s1pt[0] <= (int) (w * 0.8) + 1; // 落点绝不在被裁右半
            TestHost.check(s1Ok, "S1 半裁剪标签 " + name + "（落点在可见左半）");
            if (s1Ok) ok++;

            /* S2 标签在窗外（分屏另半区）：窗=左半屏，标签在右半屏 */
            int[] winHalf = {0, 0, w / 2, h};
            SimNode outLabel = new SimNode("跳过", (int) (w * 0.70), (int) (h * 0.03),
                    (int) (w * 0.97), (int) (h * 0.065));
            boolean s2Ok = TapPoint.tapRect(outLabel, outLabel.bounds(), winHalf) == null;
            TestHost.check(s2Ok, "S2 窗外标签拒绝 " + name + "（分屏另半区零盲点）");
            if (s2Ok) ok++;

            /* S3 可见缝过小（< MIN_VISIBLE_SIDE）→ 盲刺拒绝 */
            SimNode sliver = new SimNode("跳过", w - 10, (int) (h * 0.30),
                    w + 40, (int) (h * 0.30) + 80);
            int[] winFull = {0, 0, w, h};
            int[] s3 = TapPoint.tapRect(sliver, sliver.bounds(), winFull);
            boolean s3Ok = s3 != null && (s3[2] - s3[0]) < TapPoint.MIN_VISIBLE_SIDE
                    && TapPoint.pick(s3, 0) == null;
            TestHost.check(s3Ok, "S3 盲刺拒绝 " + name + "（可见缝 < " + TapPoint.MIN_VISIBLE_SIDE + "px）");
            if (s3Ok) ok++;

            /* S4 复验象限轮换：五点全在可见矩形内且 ≥3 个互不相同 */
            SimNode rot = new SimNode("跳过", (int) (w * 0.80), (int) (h * 0.02),
                    (int) (w * 0.97), (int) (h * 0.07));
            int[] rotRect = TapPoint.tapRect(rot, rot.bounds(), winFull);
            boolean s4Ok = rotRect != null;
            int distinct = 0;
            java.util.HashSet<String> pts = new java.util.HashSet<String>();
            for (int a = 0; a < TapPoint.ROTATION_SLOTS && s4Ok; a++) {
                int[] pt = TapPoint.pick(rotRect, a);
                if (pt == null) {
                    s4Ok = false;
                    break;
                }
                if (pt[0] < rotRect[0] || pt[0] > rotRect[2]
                        || pt[1] < rotRect[1] || pt[1] > rotRect[3]) {
                    s4Ok = false; // 越界出可见矩形
                    break;
                }
                pts.add(pt[0] + "," + pt[1]);
            }
            distinct = pts.size();
            s4Ok = s4Ok && distinct >= 3; // 同候选复验 ≥3 个不同落点（不盲刺同坐标）
            TestHost.check(s4Ok, "S4 象限轮换 " + name + "（" + distinct + "/" + TapPoint.ROTATION_SLOTS + " 个不同落点全在界内）");
            if (s4Ok) ok++;

            /* S5 边缘内缩：任意 attempt 触点距可见边 ≥ min(EDGE_INSET, 边/4) */
            boolean s5Ok = rotRect != null;
            if (s5Ok) {
                int inX = Math.min(TapPoint.EDGE_INSET, (rotRect[2] - rotRect[0]) / 4);
                int inY = Math.min(TapPoint.EDGE_INSET, (rotRect[3] - rotRect[1]) / 4);
                for (int a = 0; a < TapPoint.ROTATION_SLOTS && s5Ok; a++) {
                    int[] pt = TapPoint.pick(rotRect, a);
                    if (pt == null) {
                        s5Ok = false;
                        break;
                    }
                    if (pt[0] < rotRect[0] + inX || pt[0] > rotRect[2] - inX
                            || pt[1] < rotRect[1] + inY || pt[1] > rotRect[3] - inY) {
                        s5Ok = false; // 贴边触点（圆角/波纹死区）
                    }
                }
            }
            TestHost.check(s5Ok, "S5 边缘内缩 " + name + "（零贴边触点）");
            if (s5Ok) ok++;

            /* S6 覆盖层拦截：底部半屏输入法窗 + 顶部状态栏在 Z 序之上 */
            int[] statusBar = {0, 0, w, (int) (h * 0.04)};
            int[] ime = {0, (int) (h * 0.70), w, h};
            int[][] upper = {statusBar, ime};
            // 覆盖案例：触点在输入法窗内 → 拒发手势
            int[] coveredPt = {w / 2, (int) (h * 0.85)};
            boolean s6a = TapPoint.coveredByUpperWindow(coveredPt[0], coveredPt[1], upper);
            // 覆盖案例：触点在状态栏内 → 拒发（不拉下通知栏）
            int[] coveredPt2 = {w / 2, (int) (h * 0.02)};
            boolean s6b = TapPoint.coveredByUpperWindow(coveredPt2[0], coveredPt2[1], upper);
            // 放行案例：触点在中部广告区（无覆盖）→ false
            int[] freePt = {w / 2, (int) (h * 0.40)};
            boolean s6c = !TapPoint.coveredByUpperWindow(freePt[0], freePt[1], upper);
            boolean s6Ok = s6a && s6b && s6c;
            TestHost.check(s6Ok, "S6 覆盖层拦截 " + name + "（输入法/状态栏拒发·中区放行）");
            if (s6Ok) ok++;

            /* S7 不可见祖先（容器 visible=false）→ 拒点 */
            SimNode hiddenRoot = new SimNode();
            hiddenRoot.boundsArr = new int[]{0, 0, w, h};
            hiddenRoot.visible = false;
            SimNode hiddenLabel = new SimNode("跳过", (int) (w * 0.86), (int) (h * 0.03),
                    (int) (w * 0.97), (int) (h * 0.065));
            hiddenRoot.add(hiddenLabel);
            boolean s7Ok = TapPoint.visibleBounds(hiddenLabel) == null
                    && TapPoint.tapRect(hiddenLabel, hiddenLabel.bounds(), winFull) == null;
            TestHost.check(s7Ok, "S7 不可见祖先拒绝 " + name + "（陷阱/死区零出手）");
            if (s7Ok) ok++;

            /* S8 超宽扁标签（长横幅状跳过条）：触点纵向恒居中带 */
            SimNode wide = new SimNode("跳过", 0, (int) (h * 0.90), w, (int) (h * 0.94));
            int[] wideRect = TapPoint.tapRect(wide, wide.bounds(), winFull);
            boolean s8Ok = wideRect != null;
            if (s8Ok) {
                int top = wideRect[1], bottom = wideRect[3];
                int bandLow = top + (bottom - top) * 2 / 5;
                int bandHigh = top + (bottom - top) * 3 / 5;
                for (int a = 0; a < TapPoint.ROTATION_SLOTS && s8Ok; a++) {
                    int[] pt = TapPoint.pick(wideRect, a);
                    if (pt == null || pt[1] < bandLow || pt[1] > bandHigh) {
                        s8Ok = false; // 纵向偏离居中带=贴上下边死区
                    }
                }
            }
            TestHost.check(s8Ok, "S8 扁标签纵向居中带 " + name + "（零贴边触点）");
            if (s8Ok) ok++;

            /* S9 引擎全链路：标准开屏树 walk→decide → tapRect+pick 落点安全 */
            String pkg = "com.tapmatrix.app";
            SimNode sTree = new SimNode();
            sTree.boundsArr = new int[]{0, 0, w, h};
            SimNode skipBtn = new SimNode("跳过", (int) (w * 0.86), (int) (h * 0.03),
                    (int) (w * 0.97), (int) (h * 0.065));
            skipBtn.clickable = true;
            sTree.add(skipBtn);
            Ctx ctx = new Ctx();
            ctx.screenW = w;
            ctx.screenH = h;
            ctx.pkg = pkg;
            ctx.activityId = "com.tapmatrix.SplashActivity";
            ctx.elapsedMs = 800;
            ctx.splashLive = true;
            ScanFind sf = AdDecisionCore.walk(sTree, ctx);
            Decision sd = AdDecisionCore.decide(sf, ctx);
            boolean s9Ok = !AdDecisionCore.A_NONE.equals(sd.action) && sd.bounds != null;
            if (s9Ok) {
                int[] rect = TapPoint.tapRect(sd.node != null ? sd.node : skipBtn, sd.bounds, winFull);
                int[] pt = rect == null ? null : TapPoint.pick(rect, 0);
                int[] sb = skipBtn.bounds();
                s9Ok = pt != null
                        && pt[0] >= sb[0] && pt[0] <= sb[2]
                        && pt[1] >= sb[1] && pt[1] <= sb[3]; // 落点在跳过键可见 bounds 内
            }
            TestHost.check(s9Ok, "S9 引擎全链路 " + name + "（决策→触点安全）");
            if (s9Ok) ok++;

            geoOkTotal += ok;
        }

        /* ══ 单元级（基础几何/边界值/防御）══ */
        TestHost.suite("单元级断言");
        // U1 intersect：交集/无交/空输入
        int[] a1 = {0, 0, 100, 100};
        int[] b1 = {50, 50, 150, 150};
        int[] i1 = TapPoint.intersect(a1, b1);
        boolean u1 = i1 != null && i1[0] == 50 && i1[1] == 50 && i1[2] == 100 && i1[3] == 100;
        int[] i2 = TapPoint.intersect(a1, new int[]{100, 100, 200, 200});
        u1 = u1 && i2 == null && TapPoint.intersect(null, b1) == null
                && TapPoint.intersect(a1, null) == null;
        TestHost.check(u1, "U1 intersect 交集/无交/空输入");
        // U2 pick 非法输入
        boolean u2 = TapPoint.pick(null, 0) == null
                && TapPoint.pick(new int[]{10, 10, 10, 80}, 0) == null     // 零宽
                && TapPoint.pick(new int[]{10, 10, 80, 10}, 0) == null     // 零高
                && TapPoint.pick(new int[]{50, 50, 62, 62}, 0) != null;    // 恰好 MIN 边
        TestHost.check(u2, "U2 pick 非法输入拒绝");
        // U3 MIN_VISIBLE_SIDE 边界值：12 过 / 11 拒
        boolean u3 = TapPoint.pick(new int[]{0, 0, 12, 12}, 0) != null
                && TapPoint.pick(new int[]{0, 0, 11, 12}, 0) == null;
        TestHost.check(u3, "U3 盲刺阈值边界（12 过 · 11 拒）");
        // U4 空覆盖表
        boolean u4 = !TapPoint.coveredByUpperWindow(50, 50, null)
                && !TapPoint.coveredByUpperWindow(50, 50, new int[0][]);
        TestHost.check(u4, "U4 空覆盖表放行");
        // U5 轮换档位数
        boolean u5 = TapPoint.ROTATION_SLOTS == 5 && TapPoint.EDGE_INSET == 6
                && TapPoint.MIN_VISIBLE_SIDE == 12 && TapPoint.MAX_ANCESTORS == 64;
        TestHost.check(u5, "U5 参数在场（5 档轮换 · 6px 内缩 · 12px 盲刺阈 · 64 层限）");
        // U6 深链防炸：70 层嵌套（>MAX_ANCESTORS）可见链不异常
        SimNode deepRoot = new SimNode();
        deepRoot.boundsArr = new int[]{0, 0, 100, 100};
        SimNode cur = deepRoot;
        for (int d = 0; d < 70; d++) {
            SimNode n = new SimNode();
            n.boundsArr = new int[]{0, 0, 100, 100};
            cur.add(n);
            cur = n;
        }
        cur.text = "跳过";
        int[] deepVis = TapPoint.visibleBounds(cur);
        boolean u6 = deepVis != null && deepVis[2] == 100 && deepVis[3] == 100;
        TestHost.check(u6, "U6 深链防炸（70 层 > 64 限·保守截断不异常）");

        /* ══ 总检（24 几何 × 9 场景 = 216 + 6 单元）══ */
        TestHost.suite("矩阵总检");
        TestHost.check(geoOkTotal == GEOM.length * 9,
                "九场景全绿 " + geoOkTotal + "/" + (GEOM.length * 9));
        System.out.println("  设备自适应触点矩阵: " + geoOkTotal + "/" + (GEOM.length * 9)
                + "（24 类几何 × 9 场景）+ 6 单元级全绿");

        TestHost.done();
    }
}
