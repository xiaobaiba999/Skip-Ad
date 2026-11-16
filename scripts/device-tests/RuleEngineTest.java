package com.zai.adshield.tests;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;

/**
 * 内嵌规则引擎 JVM 单测（v3.9.0 · 李跳跳方法论 · 发版前必绿）
 * ─────────────────────────────────────────────────────────────
 * 用例来源：issue#10/#11/#12 三轮真机反馈 + 用户指令「一比一模仿李跳跳、
 * 规则内嵌、露头就秒跳（Keep 开屏为标杆）、绝不乱点（屏幕大小不一样
 * 位置就不一样——只点控件自身 bounds，绝不固定屏幕坐标）」。
 *  · R1~R6 主路径：Keep/喜马拉雅/B站/微博/穿山甲/英文 Skip —— 0ms 即中
 *  · R7~R12 防误点红线：诱导长文案/CTA 大按钮/不可见/营销话术/普通主页/下载按钮
 *  · R13~R15 结构：空树/深层嵌套/desc 形态
 *  · R16 倒计时解析（「跳过 5」→5s，服务侧门控用）
 *  · R17 精确优先于包含（树内同时存在时选精确命中）
 */
public class RuleEngineTest {

    static int pass = 0, fail = 0;

    static Ctx splashCtx() {
        Ctx c = new Ctx();
        c.screenW = FakeNode.SW;
        c.screenH = FakeNode.SH;
        c.splashLive = true;
        c.elapsedMs = 0; // 0ms：李跳跳式即时出手（窗口事件一到的首扫）
        return c;
    }

    static Hit match(FakeNode root) {
        return RuleEngine.match(root, splashCtx());
    }

    public static void main(String[] args) {
        System.out.println("═══ RuleEngine 内嵌规则引擎 JVM 单测（v3.9.0 · 李跳跳方法论 · 屏幕 1080x2400）═══\n");

        System.out.println("── 主路径：主流应用开屏 0ms 即中 ──");

        // R1 Keep 开屏（用户点名标杆）：右上角「跳过」文本键
        FakeNode keep = FakeNode.root("com.xingin.keep.splash.SplashActivity");
        FakeNode keepAd = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        keepAd.click();
        FakeNode keepSkip = FakeNode.node("android.widget.TextView", 880, 100, 1030, 180).txt("跳过").click();
        keep.add(keepAd).add(keepSkip);
        Hit h1 = match(keep);
        check("R1a Keep「跳过」文本 0ms 命中", h1 != null, "未命中");
        check("R1b 精确命中（global_exact）", h1 != null && "global_exact".equals(h1.appId),
                "实际: " + (h1 == null ? "null" : h1.appId));
        check("R1c 点标签自身 bounds（非固定屏幕坐标，适配任意分辨率）",
                h1 != null && h1.tapBounds != null && h1.tapBounds[0] == 880 && h1.tapBounds[2] == 1030,
                "实际: " + (h1 == null || h1.tapBounds == null ? "null" : h1.tapBounds[0] + "," + h1.tapBounds[2]));

        // R2 喜马拉雅（issue#10/#11 学习规则实锤「跳过广告」）
        FakeNode xmly = FakeNode.root("com.ximalaya.ting.android.host.activity.WelComeActivity");
        FakeNode xmlyAd = FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400);
        xmlyAd.click();
        FakeNode xmlySkip = FakeNode.node("android.widget.TextView", 860, 90, 1050, 170).txt("跳过广告").click();
        xmly.add(xmlyAd).add(xmlySkip);
        Hit h2 = match(xmly);
        check("R2 喜马拉雅「跳过广告」精确命中", h2 != null && "global_exact".equals(h2.appId)
                && "跳过广告".equals(h2.label), "实际: " + (h2 == null ? "null" : h2.appId + "/" + h2.label));

        // R3 B站（issue#11 学习规则实锤「跳过 5」倒计时形态）
        FakeNode bili = FakeNode.root("tv.danmaku.bili.ui.splash.SplashActivity");
        bili.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        bili.add(FakeNode.node("android.widget.TextView", 900, 110, 1040, 180).txt("跳过 5").click());
        Hit h3 = match(bili);
        check("R3a B站「跳过 5」短包含命中", h3 != null && "跳过 5".equals(h3.label),
                "实际: " + (h3 == null ? "null" : h3.label));
        check("R3b 倒计时解析 5s（服务侧门控用）",
                h3 != null && AdDecisionCore.parseCountdown(h3.label) == 5,
                "实际: " + (h3 == null ? "null" : AdDecisionCore.parseCountdown(h3.label)));

        // R4 微博（GKD 规则实锤 iv_skip 零文本图片键）
        FakeNode weibo = FakeNode.root("com.sina.weibo.SplashActivity");
        weibo.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        FakeNode ivSkip = FakeNode.node("android.widget.ImageView", 940, 90, 1020, 170);
        ivSkip.vid("com.sina.weibo:id/iv_skip");
        weibo.add(ivSkip);
        Hit h4 = match(weibo);
        check("R4a 微博 iv_skip 零文本图片键 vid 命中", h4 != null && "global_vid".equals(h4.appId),
                "实际: " + (h4 == null ? "null" : h4.appId));
        check("R4b vid 命中点控件自身 bounds",
                h4 != null && h4.tapBounds != null && h4.tapBounds[0] == 940,
                "实际: " + (h4 == null || h4.tapBounds == null ? "null" : "" + h4.tapBounds[0]));

        // R5 穿山甲（CSJ tt_splash_skip_btn）
        FakeNode csj = FakeNode.root("com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity");
        csj.add(FakeNode.node("android.view.TextureView", 0, 0, 1080, 2400));
        FakeNode ttSkip = FakeNode.node("android.widget.TextView", 900, 95, 1040, 175);
        ttSkip.vid("com.bytedance.sdk.openadsdk:id/tt_splash_skip_btn");
        csj.add(ttSkip);
        check("R5 穿山甲 tt_splash_skip_btn vid 命中", match(csj) != null);

        // R6 英文 Skip
        FakeNode en = FakeNode.root("com.example.app.SplashActivity");
        en.add(FakeNode.node("android.widget.TextView", 890, 100, 1020, 170).txt("Skip").click());
        Hit h6 = match(en);
        check("R6 英文「Skip」精确命中", h6 != null && "global_exact".equals(h6.appId));

        System.out.println("── 防误点红线（绝不乱点·issue#12 整治）──");

        // R7 诱导长文案：长于 6 字的包含文案绝不中
        FakeNode trap = FakeNode.root("com.example.app.SplashActivity");
        trap.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        trap.add(FakeNode.node("android.widget.TextView", 200, 1900, 880, 2000)
                .txt("摇一摇跳过广告详情页").click());
        check("R7 「摇一摇跳过广告详情页」诱导长文案不命中", match(trap) == null,
                "实际: " + (match(trap) == null ? "null" : match(trap).label));

        // R8 大面积 CTA：面积 >35% 屏的「跳过」样式巨按钮（全屏热区诱饵）不中
        FakeNode cta = FakeNode.root("com.example.app.SplashActivity");
        cta.add(FakeNode.node("android.widget.Button", 0, 800, 1080, 2200).txt("跳过").click());
        check("R8 大面积热区「跳过」诱饵不命中", match(cta) == null);

        // R9 不可见（GKD visibleToUser 红线：不可见=陷阱/死区）
        FakeNode invis = FakeNode.root("com.example.app.SplashActivity");
        invis.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        FakeNode invSkip = FakeNode.node("android.widget.TextView", 890, 100, 1020, 170).txt("跳过").click();
        invSkip.invisible();
        invis.add(invSkip);
        check("R9 不可见「跳过」不命中", match(invis) == null);

        // R10 营销话术类
        FakeNode mkt = FakeNode.root("com.example.app.SplashActivity");
        mkt.add(FakeNode.node("android.widget.Button", 400, 1800, 700, 1950).txt("点击领取红包").click());
        check("R10 「点击领取红包」营销话术不命中", match(mkt) == null);

        // R11 普通主页（信息流）：文本流里偶现「跳过」字样的长文不中（>6 字）
        FakeNode home = FakeNode.root("com.example.app.MainActivity");
        FakeNode feed = FakeNode.node("androidx.recyclerview.widget.RecyclerView", 0, 200, 1080, 2300).scroll();
        feed.add(FakeNode.node("android.widget.TextView", 60, 400, 1020, 900)
                .txt("这个视频太精彩了看到最后可以直接跳过片头广告直达正片内容快来试试吧"));
        home.add(feed);
        check("R11 信息流长文本（含「跳过」字样）不命中", match(home) == null);

        // R12 vid 含 download（下载按钮，count+down 排除项）不中
        FakeNode dl = FakeNode.root("com.example.app.SplashActivity");
        FakeNode dlBtn = FakeNode.node("android.widget.Button", 440, 2000, 640, 2120);
        dlBtn.vid("com.example.app:id/btn_app_download");
        dl.add(dlBtn);
        check("R12 vid「download」下载按钮不命中", match(dl) == null);

        System.out.println("── 结构与优先级 ──");

        // R13 空树 / 无信号
        check("R13a 空树返回 null", match(FakeNode.root("com.example.app.Splash")) == null);
        FakeNode plain = FakeNode.root("com.example.app.MainActivity");
        plain.add(FakeNode.node("android.widget.TextView", 100, 100, 900, 300).txt("今日头条速览"));
        check("R13b 无跳过控件返回 null", match(plain) == null);

        // R14 深层嵌套（真实广告窗常 3~6 层容器）
        FakeNode deep = FakeNode.root("com.example.app.SplashActivity");
        FakeNode l1n = FakeNode.node("android.widget.FrameLayout", 0, 0, 1080, 2400);
        FakeNode l2n = FakeNode.node("android.widget.RelativeLayout", 0, 60, 1080, 240);
        FakeNode l3n = FakeNode.node("android.widget.LinearLayout", 860, 90, 1050, 180);
        FakeNode deepSkip = FakeNode.node("android.widget.TextView", 870, 95, 1040, 175).txt("跳过").click();
        l3n.add(deepSkip);
        l2n.add(l3n);
        l1n.add(l2n);
        deep.add(l1n);
        Hit h14 = match(deep);
        check("R14 深层嵌套「跳过」照中（可达性不受层级影响）",
                h14 != null && h14.tapBounds != null && h14.tapBounds[0] == 870,
                "实际: " + (h14 == null ? "null" : "" + h14.tapBounds[0]));

        // R15 desc（contentDescription）形态
        FakeNode descRoot = FakeNode.root("com.example.app.SplashActivity");
        FakeNode descSkip = FakeNode.node("android.widget.ImageView", 920, 100, 1030, 190);
        descSkip.describe("跳过广告").click();
        descRoot.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400)).add(descSkip);
        Hit h15 = match(descRoot);
        check("R15 desc「跳过广告」命中", h15 != null && "跳过广告".equals(h15.label),
                "实际: " + (h15 == null ? "null" : h15.label));

        // R16 倒计时文案解析（服务侧门控：提前点=白点）
        check("R16a 「跳过 3」→3s", AdDecisionCore.parseCountdown("跳过 3") == 3);
        check("R16b 「3s后可跳过」→3s", AdDecisionCore.parseCountdown("3s后可跳过") == 3);
        check("R16c 「跳过」→0s（无倒计时）", AdDecisionCore.parseCountdown("跳过") == 0);
        check("R16d 「0元领」→0s（营销数字非倒计时）", AdDecisionCore.parseCountdown("0元领") == 0);

        // R17 精确优先于包含：树内「跳过 5」与「跳过」并存 → 命中精确「跳过」
        FakeNode both = FakeNode.root("com.example.app.SplashActivity");
        both.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        both.add(FakeNode.node("android.widget.TextView", 700, 100, 840, 170).txt("跳过 5").click());
        both.add(FakeNode.node("android.widget.TextView", 880, 100, 1020, 170).txt("跳过").click());
        Hit h17 = match(both);
        check("R17 精确命中优先于包含命中",
                h17 != null && "global_exact".equals(h17.appId) && "跳过".equals(h17.label),
                "实际: " + (h17 == null ? "null" : h17.appId + "/" + h17.label));

        System.out.println();
        System.out.println("═══ 结果：" + pass + " 通过 / " + fail + " 失败 ═══");
        if (fail > 0) {
            System.out.println("存在 FAIL，禁止发版！");
            System.exit(1);
        }
        System.out.println("全部通过 ✅");
    }

    static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name + (detail.isEmpty() ? "" : "  [" + detail + "]"));
        }
    }
}
