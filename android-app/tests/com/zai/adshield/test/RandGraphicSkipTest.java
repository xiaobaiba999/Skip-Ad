package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 泛位置图片跳过键单元测试（v7.15.6 · 用户实锤：Keep/CSDN 动态图开屏——
 * 「跳过按钮每次在不同地方、没有倒计时」的纯图片键旧几何带「顶 14%/
 * 底 18%+左右缘」零收集 = 识别不到且日志无记录的头号根因）。
 * 与 SplashCountdownTest 同规约（纯 Java 零 Android 依赖，ECJ -1.8 编译 JVM 运行）。
 * ─────────────────────────────────────────────────────────────
 * 断言规划（check/checkEq 总数 35）：
 *  S1 泛位置几何 isRandPosControl（7）/ S2 vid 否决 randVidDenied（6）
 *  S3 walk 收集门（7）/ S4 decide L4.7 出手门（10）
 *  S5 hasActionableCand 联动（4）。
 * 标准屏 1080×2400；Keep 动态图开屏典型形态：全屏可点广告图（hotZone）
 * + 居中随机位无字小可点图片键。
 */
public final class RandGraphicSkipTest {

    public static void main(String[] args) {
        /* ════ S1 泛位置几何 isRandPosControl（7 项）════ */
        TestHost.suite("RandGraphicSkip · S1 泛位置几何（任意位置·含居中）");
        Ctx g = new Ctx();
        g.screenW = 1080;
        g.screenH = 2400;
        // 居中小图片键（Keep 随机位典型）：140×120 ≈0.65% 屏 → 收
        TestHost.check(AdDecisionCore.isRandPosControl(new int[]{470, 1050, 610, 1170}, g),
                "居中 140×120 小键 → 收（随机位核心场景）");
        // 右上角落键 → 归角落带，不算泛位置
        TestHost.check(!AdDecisionCore.isRandPosControl(new int[]{900, 80, 1030, 160}, g),
                "右上角落键 → 不算（归角落候选，门更强）");
        // 右缘中部键 → 归中部边带
        TestHost.check(!AdDecisionCore.isRandPosControl(new int[]{820, 1100, 1000, 1240}, g),
                "右缘中部键 → 不算（归边带候选）");
        // 宽超限（400px > 30% 屏宽 324）→ 拒
        TestHost.check(!AdDecisionCore.isRandPosControl(new int[]{340, 1000, 740, 1120}, g),
                "宽 400px 超限 → 拒（CTA 图一般更大）");
        // 高超限（500px > 12% 屏高 288）→ 拒
        TestHost.check(!AdDecisionCore.isRandPosControl(new int[]{490, 900, 590, 1400}, g),
                "高 500px 超限 → 拒");
        // 过小（10px < 24px 最小值）→ 拒
        TestHost.check(!AdDecisionCore.isRandPosControl(new int[]{500, 1000, 510, 1010}, g),
                "10px 过小 → 拒（与角落门同源）");
        // 左下角落带 → 归角落
        TestHost.check(!AdDecisionCore.isRandPosControl(new int[]{40, 2100, 170, 2230}, g),
                "左下角落键 → 不算（归角落候选）");

        /* ════ S2 vid 否决 randVidDenied（6 项）════ */
        TestHost.suite("RandGraphicSkip · S2 vid 否决表（诱导 token 拒收）");
        TestHost.check(AdDecisionCore.randVidDenied("com.keep.keep/btn_download"),
                "vid 含 download → 拒");
        TestHost.check(AdDecisionCore.randVidDenied("com.x/btn_user_avatar"),
                "vid 含 user → 拒");
        TestHost.check(AdDecisionCore.randVidDenied("com.x/redpacket_icon"),
                "vid 含 redpacket → 拒");
        TestHost.check(!AdDecisionCore.randVidDenied(""),
                "vid 空 → 不拒（无名图片键常态）");
        TestHost.check(!AdDecisionCore.randVidDenied("com.x/iv_close"),
                "vid 含 close → 不拒（关闭键语义，isVidSkip 真键分支优先）");
        TestHost.check(!AdDecisionCore.randVidDenied("net.csdn.csdnplus/image_view"),
                "普通 image_view → 不拒");

        /* ════ S3 walk 收集门（7 项）════ */
        TestHost.suite("RandGraphicSkip · S3 walk 收集（开屏窗内·动态图开屏全链）");
        // 树①：Keep 动态图开屏——全屏可点广告图（hotZone）+ 居中随机位无字小键
        Ctx c1 = new Ctx();
        c1.screenW = 1080;
        c1.screenH = 2400;
        c1.splashLive = true;
        c1.budgetNodes = 600;
        SimNode keep1 = new SimNode();
        keep1.boundsArr = new int[]{0, 0, 1080, 2400};
        keep1.cls = "android.widget.FrameLayout";
        SimNode adImg = new SimNode();
        adImg.boundsArr = new int[]{0, 0, 1080, 2400};
        adImg.cls = "android.widget.ImageView";
        adImg.clickable = true; // 全屏热区（动态图广告本体）
        keep1.add(adImg);
        SimNode randBtn = new SimNode();
        randBtn.boundsArr = new int[]{470, 1050, 610, 1170}; // 居中随机位
        randBtn.cls = "android.widget.ImageView";
        randBtn.clickable = true; // 纯图片跳过键（无文本·无倒计时）
        keep1.add(randBtn);
        ScanFind f1 = AdDecisionCore.walk(keep1, c1);
        TestHost.check(f1.hotZone, "全屏可点广告图 → hotZone 强广告形态");
        TestHost.check(f1.randCands.size() == 1,
                "居中随机位无字小键 → randCands 收到 1 个（旧版零收集=识别不到）");
        // 树②：非开屏窗（splashLive=false）→ 不收集
        Ctx c2 = new Ctx();
        c2.screenW = 1080;
        c2.screenH = 2400;
        c2.splashLive = false;
        c2.budgetNodes = 600;
        SimNode keep2 = new SimNode();
        keep2.boundsArr = new int[]{0, 0, 1080, 2400};
        keep2.cls = "android.widget.FrameLayout";
        SimNode adImg2 = new SimNode();
        adImg2.boundsArr = new int[]{0, 0, 1080, 2400};
        adImg2.cls = "android.widget.ImageView";
        adImg2.clickable = true;
        keep2.add(adImg2);
        SimNode randBtn2 = new SimNode();
        randBtn2.boundsArr = new int[]{470, 1050, 610, 1170};
        randBtn2.cls = "android.widget.ImageView";
        randBtn2.clickable = true;
        keep2.add(randBtn2);
        ScanFind f2 = AdDecisionCore.walk(keep2, c2);
        TestHost.check(f2.randCands.isEmpty(),
                "非开屏窗 → 零收集（信息流/主页居中图标绝不入泛位置候选）");
        // 树③：vid 诱导 token → 不收集
        SimNode keep3 = new SimNode();
        keep3.boundsArr = new int[]{0, 0, 1080, 2400};
        keep3.cls = "android.widget.FrameLayout";
        SimNode adImg3 = new SimNode();
        adImg3.boundsArr = new int[]{0, 0, 1080, 2400};
        adImg3.cls = "android.widget.ImageView";
        adImg3.clickable = true;
        keep3.add(adImg3);
        SimNode dlBtn = new SimNode();
        dlBtn.boundsArr = new int[]{470, 1050, 610, 1170};
        dlBtn.cls = "android.widget.ImageView";
        dlBtn.clickable = true;
        dlBtn.vid = "com.x/btn_download"; // 纯图片 CTA（下载钮）
        keep3.add(dlBtn);
        ScanFind f3 = AdDecisionCore.walk(keep3, c1);
        TestHost.check(f3.randCands.isEmpty(),
                "vid 含 download 的居中图键 → 拒收（纯图片 CTA 防线）");
        // 树④：角落键在场 → 入 corners 不入 randCands（分层门控分流）
        SimNode keep4 = new SimNode();
        keep4.boundsArr = new int[]{0, 0, 1080, 2400};
        keep4.cls = "android.widget.FrameLayout";
        SimNode adImg4 = new SimNode();
        adImg4.boundsArr = new int[]{0, 0, 1080, 2400};
        adImg4.cls = "android.widget.ImageView";
        adImg4.clickable = true;
        keep4.add(adImg4);
        SimNode cornerBtn = new SimNode();
        cornerBtn.boundsArr = new int[]{900, 80, 1030, 160}; // 右上角落
        cornerBtn.cls = "android.widget.ImageView";
        cornerBtn.clickable = true;
        keep4.add(cornerBtn);
        ScanFind f4 = AdDecisionCore.walk(keep4, c1);
        TestHost.check(f4.corners.size() == 1 && f4.randCands.isEmpty(),
                "角落键 → corners 1 个·randCands 0（几何带分流不变）");
        // 树⑤：多个随机位键 → 上限 3
        SimNode keep5 = new SimNode();
        keep5.boundsArr = new int[]{0, 0, 1080, 2400};
        keep5.cls = "android.widget.FrameLayout";
        SimNode adImg5 = new SimNode();
        adImg5.boundsArr = new int[]{0, 0, 1080, 2400};
        adImg5.cls = "android.widget.ImageView";
        adImg5.clickable = true;
        keep5.add(adImg5);
        int[][] positions = {{470, 1050, 610, 1170}, {330, 700, 470, 820}, {620, 1500, 760, 1620}, {400, 1800, 540, 1920}};
        for (int[] p : positions) {
            SimNode b = new SimNode();
            b.boundsArr = p;
            b.cls = "android.widget.ImageView";
            b.clickable = true;
            keep5.add(b);
        }
        ScanFind f5 = AdDecisionCore.walk(keep5, c1);
        TestHost.check(f5.randCands.size() == 3,
                "4 个随机位键 → 收满上限 3（RAND_CANDS_MAX 防误点面扩张）");
        // 树⑥：不可点且无可见祖先中转的小图（假键形态）→ resolveClickable null → 不收
        SimNode keep6 = new SimNode();
        keep6.boundsArr = new int[]{0, 0, 1080, 2400};
        keep6.cls = "android.widget.FrameLayout";
        SimNode dead = new SimNode();
        dead.boundsArr = new int[]{470, 1050, 610, 1170};
        dead.cls = "android.widget.ImageView";
        dead.clickable = false;
        keep6.add(dead);
        ScanFind f6 = AdDecisionCore.walk(keep6, c1);
        TestHost.check(f6.randCands.isEmpty(),
                "不可点无祖先中转 → 零收集（死键不点）");

        /* ════ S4 decide L4.7 出手门（10 项）════ */
        TestHost.suite("RandGraphicSkip · S4 decide L4.7（强广告形态才出手）");
        // ① Keep 动态图开屏全链：hotZone + 居中随机键 + 开屏窗 + 300ms → 出手
        Ctx d1 = new Ctx();
        d1.screenW = 1080;
        d1.screenH = 2400;
        d1.splashLive = true;
        d1.elapsedMs = 600;
        d1.clickAttempts = 0;
        Decision r1 = AdDecisionCore.decide(f1, d1);
        TestHost.check(AdDecisionCore.A_CLICK_BOUNDS.equals(r1.action)
                        && "rand_graphic_skip".equals(r1.reason),
                "hotZone+居中随机键+600ms → rand_graphic_skip 出手（Keep 场景根治）");
        TestHost.check(r1.bounds != null && r1.bounds[0] == 470 && r1.bounds[1] == 1050,
                "点击边界=原小键 bounds（点得准）");
        // ② 无强广告形态（无热区/角标/SDK/学习）→ 拒（居中位无树极简兜底）
        Ctx d2 = new Ctx();
        d2.screenW = 1080;
        d2.screenH = 2400;
        d2.splashLive = true;
        d2.elapsedMs = 600;
        SimNode plain = new SimNode();
        plain.boundsArr = new int[]{0, 0, 1080, 2400};
        plain.cls = "android.widget.FrameLayout";
        SimNode plainBtn = new SimNode();
        plainBtn.boundsArr = new int[]{470, 1050, 610, 1170};
        plainBtn.cls = "android.widget.ImageView";
        plainBtn.clickable = true;
        plain.add(plainBtn);
        ScanFind fPlain = AdDecisionCore.walk(plain, c1);
        Decision r2 = AdDecisionCore.decide(fPlain, d2);
        TestHost.check(AdDecisionCore.A_NONE.equals(r2.action),
                "无强广告形态的居中小图键 → A_NONE（普通页面居中图标绝不误点）");
        // ③ 时机过早（<300ms）→ 拒
        Ctx d3 = new Ctx();
        d3.screenW = 1080;
        d3.screenH = 2400;
        d3.splashLive = true;
        d3.elapsedMs = 200;
        Decision r3 = AdDecisionCore.decide(f1, d3);
        TestHost.check(AdDecisionCore.A_NONE.equals(r3.action),
                "200ms 时机未到 → A_NONE（CORNER_MIN_MS 同源）");
        // ④ 时机过晚（>6500ms）→ L4.7 拒，L5 热区返回键兜底接管（既有行为不变）
        Ctx d4 = new Ctx();
        d4.screenW = 1080;
        d4.screenH = 2400;
        d4.splashLive = true;
        d4.elapsedMs = 7000;
        Decision r4 = AdDecisionCore.decide(f1, d4);
        TestHost.check(!"rand_graphic_skip".equals(r4.reason) && "hot_zone".equals(r4.reason),
                "7000ms 过晚 → L4.7 拒 + L5 热区返回键兑底（CORNER_MAX_MS 同源）");
        // ⑤ 可滚动窗（CSDN WebView 形态但有滚动容器）→ 拒（信息流防线）
        ScanFind fScroll = AdDecisionCore.walk(keep1, c1);
        fScroll.scrollPresent = true; // 模拟 WebView 容器标记
        Ctx d5 = new Ctx();
        d5.screenW = 1080;
        d5.screenH = 2400;
        d5.splashLive = true;
        d5.elapsedMs = 600;
        Decision r5 = AdDecisionCore.decide(fScroll, d5);
        TestHost.check(AdDecisionCore.A_NONE.equals(r5.action),
                "scrollPresent → A_NONE（可滚动窗居中键绝不点）");
        // ⑥ 角落键在场时让位（corners 优先，randCands 排后）
        Ctx d6 = new Ctx();
        d6.screenW = 1080;
        d6.screenH = 2400;
        d6.splashLive = true;
        d6.elapsedMs = 600;
        d6.clickAttempts = 0;
        Decision r6 = AdDecisionCore.decide(f4, d6); // f4 = 1 corner + hotZone
        TestHost.check("corner_image_skip".equals(r6.reason),
                "角落键在场 → corner_image_skip 先出手（先点像的）");
        // ⑦ 复验轮换：4 个随机键收 3 个，第 2 击换第 2 个候选
        Ctx d7 = new Ctx();
        d7.screenW = 1080;
        d7.screenH = 2400;
        d7.splashLive = true;
        d7.elapsedMs = 2000;
        d7.clickAttempts = 1;
        Decision r7 = AdDecisionCore.decide(f5, d7); // f5 = 3 randCands
        TestHost.check("rand_graphic_skip".equals(r7.reason)
                        && r7.bounds[0] == 330,
                "第 2 击 → 换第 2 个随机候选（同坐标绝不重复盲刺）");
        // ⑧ 角标强形态（adBadge，无热区）同样可出手
        ScanFind f8 = AdDecisionCore.walk(keep1, c1);
        f8.hotZone = false;
        f8.adBadge = true;
        Ctx d8 = new Ctx();
        d8.screenW = 1080;
        d8.screenH = 2400;
        d8.splashLive = true;
        d8.elapsedMs = 600;
        Decision r8 = AdDecisionCore.decide(f8, d8);
        TestHost.check("rand_graphic_skip".equals(r8.reason),
                "角标在场（无热区）→ 同样出手（强形态四选一）");
        // ⑨ 非开屏窗 → L4.7 整层不生效
        Ctx d9 = new Ctx();
        d9.screenW = 1080;
        d9.screenH = 2400;
        d9.splashLive = false;
        d9.elapsedMs = 600;
        Decision r9 = AdDecisionCore.decide(f1, d9);
        TestHost.check(AdDecisionCore.A_NONE.equals(r9.action),
                "非开屏窗 → L4.7 零出手（弹窗/插屏路径不受影响）");
        // ⑩ 学习窗形态（learnedCls）同样可出手
        ScanFind f10 = AdDecisionCore.walk(keep1, c1);
        f10.hotZone = false;
        Ctx d10 = new Ctx();
        d10.screenW = 1080;
        d10.screenH = 2400;
        d10.splashLive = true;
        d10.elapsedMs = 600;
        d10.learnedCls = true;
        Decision r10 = AdDecisionCore.decide(f10, d10);
        TestHost.check("rand_graphic_skip".equals(r10.reason),
                "学习窗形态 → 同样出手（强形态四选一）");

        /* ════ S5 hasActionableCand 联动（4 项）════ */
        TestHost.suite("RandGraphicSkip · S5 零候选退避联动");
        Ctx hz = new Ctx();
        hz.screenW = 1080;
        hz.screenH = 2400;
        TestHost.check(AdDecisionCore.hasActionableCand(f1, hz),
                "randCands+hotZone → 有可出手候选（退避不触发=持续扫描救晚到键）");
        TestHost.check(!AdDecisionCore.hasActionableCand(fPlain, hz),
                "randCands 无强形态 → 不算可出手（零候选退避照常触发）");
        TestHost.check(AdDecisionCore.hasActionableCand(f8, hz),
                "randCands+角标 → 有可出手候选");
        // Ctx 无屏幕信息时强形态判定仍以 f 信号为准
        ScanFind fHz2 = AdDecisionCore.walk(keep1, c1);
        fHz2.adBadge = false;
        fHz2.hotZone = true;
        TestHost.check(AdDecisionCore.hasActionableCand(fHz2, new Ctx()),
                "randCands+hotZone（空 Ctx）→ 有可出手候选");

        TestHost.done();
    }

    private RandGraphicSkipTest() {
    }
}
