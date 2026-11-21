package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.FusedScan;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.test.TestHost.Counters;
import com.zai.adshield.test.TestHost.CountingNode;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * v8.0.2 双 DFS 专项 JVM 基准（ScanBenchmark · P3-1 前置条件①）。
 * ─────────────────────────────────────────────────────────────────
 * 三形态假树（宽簇/深链/混合）× 三规模（150/400/600 节点）× 双路径：
 *   旧 = RuleEngine.matchAll + AdDecisionCore.walk（两趟独立 DFS）；
 *   新 = AdDecisionCore.walkFused（单遍融合）。
 *
 * 产出（打印为报告证据 · 证据等级 SIMULATED——JVM 无 binder/无真机）：
 *   · 单次扫描耗时（预热后均值，ns → µs）；
 *   · 确定性计数（nodeVisit/textRead/descRead/boundsRead/visibleRead/
 *     childCall≈生产 getChild binder/propertyReads 总次/wrapperAlloc≈NodeAdapter 分配）。
 *
 * 断言（确定性，非计时）：
 *   · 决策一致性：新 find == 旧 find（全字段 key）且新 ruleHits == 旧 matchAll；
 *   · 成本一致性：新 childCall/propertyReads/wrapperAlloc 严格低于旧（单遍 < 两趟）。
 * 计时仅作参考打印，不设硬断言（JVM 抖动下硬断言会闪断）。
 * 真机性能与电量结论：NOT_RUN（本套件不产生真机口径数据）。
 */
public final class ScanBenchmark {

    static final int WARMUP = 300;
    static final int MEASURE = 1000;

    public static void main(String[] args) {
        TestHost.suite("ScanBenchmark · 双 DFS 旧/新路径 JVM 基准（SIMULATED）");
        int[] sizes = {150, 400, 600};
        String[] shapes = {"wide", "deep", "mixed"};
        for (int size : sizes) {
            for (String shape : shapes) {
                runCase(shape, size);
            }
        }
        TestHost.done();
    }

    static void runCase(String shape, int size) {
        SimNode tree = buildTree(shape, size);
        Ctx ctx = baseCtx();
        ctx.budgetNodes = 600;

        // ── 预热（JIT）──
        for (int i = 0; i < WARMUP; i++) {
            RuleEngine.matchAll(tree, ctx, 4);
            AdDecisionCore.walk(tree, ctx);
            AdDecisionCore.walkFused(tree, ctx, true, 4);
        }

        // ── 计时（顺序交错防偏置）──
        long oldNs = 0, newNs = 0;
        for (int i = 0; i < MEASURE; i++) {
            long t0 = System.nanoTime();
            java.util.ArrayList<Hit> h = RuleEngine.matchAll(tree, ctx, 4);
            ScanFind f = AdDecisionCore.walk(tree, ctx);
            long t1 = System.nanoTime();
            FusedScan fs = AdDecisionCore.walkFused(tree, ctx, true, 4);
            long t2 = System.nanoTime();
            oldNs += t1 - t0;
            newNs += t2 - t1;
            if (i == MEASURE - 1) {
                // 决策一致性（末轮对象）
                TestHost.checkEq(key(h), key(fs.ruleHits),
                        shape + "/" + size + " 规则命中一致");
                TestHost.checkEq(findKey(f), findKey(fs.find),
                        shape + "/" + size + " 决策信号一致");
            }
        }

        // ── 确定性计数（独立一趟，不计时）──
        Counters oldC = new Counters();
        RuleEngine.matchAll(new CountingNode(tree, oldC), ctx, 4);
        ScanFind oldCnt = AdDecisionCore.walk(new CountingNode(tree, oldC), ctx);
        Counters newC = new Counters();
        FusedScan fsCnt = AdDecisionCore.walkFused(new CountingNode(tree, newC), ctx, true, 4);
        TestHost.checkEq(oldCnt.nodeCount, fsCnt.find.nodeCount, shape + "/" + size + " 访问节点数一致");

        TestHost.check(newC.childCall < oldC.childCall,
                shape + "/" + size + " childCall 下降 " + oldC.childCall + "→" + newC.childCall);
        TestHost.check(newC.propertyReads() < oldC.propertyReads(),
                shape + "/" + size + " 属性读下降 " + oldC.propertyReads() + "→" + newC.propertyReads());
        TestHost.check(newC.wrapperAlloc < oldC.wrapperAlloc,
                shape + "/" + size + " 包装分配下降 " + oldC.wrapperAlloc + "→" + newC.wrapperAlloc);
        TestHost.check(oldC.childCall - newC.childCall >= fsCnt.find.nodeCount / 2,
                shape + "/" + size + " childCall 降幅 ≥ 访问节点数一半（两趟→单趟）");

        System.out.printf("  [%s/%d] 旧 %.1fµs/scan · 新 %.1fµs/scan · scanCount=1 | nodes %d | child %d→%d | text %d→%d"
                        + " | desc %d→%d | bounds %d→%d | 属性总 %d→%d | 分配 %d→%d%n",
                shape, size,
                oldNs / 1000.0 / MEASURE, newNs / 1000.0 / MEASURE,
                fsCnt.find.nodeCount,
                oldC.childCall, newC.childCall,
                oldC.textRead, newC.textRead,
                oldC.descRead, newC.descRead,
                oldC.boundsRead, newC.boundsRead,
                oldC.propertyReads(), newC.propertyReads(),
                oldC.wrapperAlloc, newC.wrapperAlloc);
    }

    static Ctx baseCtx() {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = "com.test.app";
        ctx.activityId = "com.test.app.MainActivity";
        ctx.elapsedMs = 500;
        ctx.splashLive = true;
        ctx.budgetNodes = 600;
        return ctx;
    }

    /** 三形态假树（全部零规则命中——对照的是纯遍历成本面） */
    static SimNode buildTree(String shape, int size) {
        SimNode root = new SimNode("", 0, 0, 1080, 2400);
        root.cls = "android.widget.FrameLayout";
        int made = 1;
        SimNode cur = root;
        if ("wide".equals(shape)) {
            int row = 0;
            while (made < size) {
                SimNode cluster = new SimNode("", 0, (row % 8) * 280, 1080, (row % 8) * 280 + 270);
                cluster.cls = "android.widget.LinearLayout";
                root.add(cluster);
                made++;
                for (int k = 0; k < 9 && made < size; k++) {
                    cluster.add(new SimNode("行文本" + row + "-" + k, 20 + k * 110, (row % 8) * 280 + 10, 110 + k * 110, (row % 8) * 280 + 80));
                    made++;
                }
                row++;
            }
        } else if ("deep".equals(shape)) {
            // ≤30 深链段挂在根下（深度≤32 可达）：每段 29 节点 + 链尾文本叶
            SimNode segHead = null;
            int depthInSeg = 0;
            while (made < size) {
                if (segHead == null || depthInSeg >= 29) {
                    segHead = new SimNode("", 0, 0, 1080, 2400);
                    segHead.cls = "android.view.View";
                    root.add(segHead);
                    made++;
                    depthInSeg = 0;
                    cur = segHead;
                }
                SimNode next;
                if (depthInSeg == 28) {
                    next = new SimNode("深链叶子文本" + made, 40, 100 + (made % 20) * 100, 700, 170 + (made % 20) * 100);
                } else {
                    next = new SimNode("", 0, 0, 1080, 2400);
                    next.cls = "android.view.View";
                }
                cur.add(next);
                cur = next;
                made++;
                depthInSeg++;
            }
        } else { // mixed：宽簇挂在链上，每 3 簇回落根（深度≤~12）
            int row = 0;
            int sinceRoot = 0;
            while (made < size) {
                SimNode hub = new SimNode("", 0, (row % 8) * 280, 1080, (row % 8) * 280 + 270);
                hub.cls = "android.widget.LinearLayout";
                cur.add(hub);
                made++;
                for (int k = 0; k < 4 && made < size; k++) {
                    hub.add(new SimNode("混合文本" + row + "-" + k, 20 + k * 200, (row % 8) * 280 + 10, 200 + k * 200, (row % 8) * 280 + 80));
                    made++;
                }
                sinceRoot++;
                cur = (sinceRoot % 3 == 0) ? root : hub; // 每 3 簇回落根，控深度
                row++;
            }
        }
        return root;
    }

    static String key(java.util.List<Hit> hits) {
        StringBuilder sb = new StringBuilder("[");
        for (Hit h : hits) {
            sb.append(h.appId).append('|').append(h.label).append('|')
                    .append(boundsStr(h.tapBounds)).append('|').append(h.gestureOnly).append(';');
        }
        return sb.append(']').toString();
    }

    static String findKey(ScanFind f) {
        return "nodes=" + f.nodeCount + ",skip=" + f.skipCands.size() + ",fake=" + f.fakeCands.size()
                + ",corner=" + f.corners.size() + ",cd=" + f.countdownN + ",long=" + f.longText
                + ",edit=" + f.editPresent + ",scroll=" + f.scrollPresent + ",hot=" + f.hotZone;
    }

    static String boundsStr(int[] b) {
        if (b == null || b.length < 4) return "null";
        return b[0] + "," + b[1] + "," + b[2] + "," + b[3];
    }
}
