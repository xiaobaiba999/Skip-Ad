package com.zai.adshield.tests;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;

/**
 * 内嵌规则引擎 JVM 单测（v3.10.0 · 发版前必绿）
 * ─────────────────────────────────────────────────────────────
 * 用例来源：issue#10/#11/#12 三轮真机反馈 + 网络好用规则内嵌验证：
 *  · R1~R6 主路径：Keep/喜马拉雅/B站/微博/穿山甲/英文 Skip —— 0ms 即中
 *  · R7~R12 防误点红线：诱导长文案/CTA 大按钮/不可见/营销话术/普通主页/下载按钮
 *  · R13~R17 结构：空树/深层嵌套/desc 形态/倒计时解析/精确优先
 *  · R18~R28 v3.10.0 新增：倒计时形态正则（超 8 字）/大小写归一/
 *    分应用规则表/SDK 窗图片关闭键（含边缘带与非 SDK 窗否决）/
 *    诱导词否决/launcher 黑名单扩充/SDK 窗指纹扩充/短包含上限
 */
public class RuleEngineTest {

    static int pass = 0, fail = 0;

    static Ctx splashCtx() {
        Ctx c = new Ctx();
        c.screenW = FakeNode.SW;
        c.screenH = FakeNode.SH;
        c.splashLive = true;
        c.elapsedMs = 0; // 0ms：即时出手（窗口事件一到的首扫）
        return c;
    }

    static Hit match(FakeNode root) {
        return RuleEngine.match(root, splashCtx());
    }

    static Hit matchPkg(FakeNode root, String pkg) {
        Ctx c = splashCtx();
        c.pkg = pkg;
        return RuleEngine.match(root, c);
    }

    public static void main(String[] args) {
        System.out.println("═══ RuleEngine 内嵌规则引擎 JVM 单测（v3.10.0 · 屏幕 1080x2400）═══\n");
        System.out.println("规则库：" + RuleEngine.stats() + "\n");

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
        check("R3a B站「跳过 5」倒计时形态命中", h3 != null && "跳过 5".equals(h3.label),
                "实际: " + (h3 == null ? "null" : h3.label));
        check("R3b 层级为 global_pattern（形态正则）", h3 != null && "global_pattern".equals(h3.appId),
                "实际: " + (h3 == null ? "null" : h3.appId));
        check("R3c 倒计时解析 5s（服务侧门控用）",
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

        // R6 英文 Skip（大小写归一：SKIP/Skip/skip 同一条规则）
        FakeNode en = FakeNode.root("com.example.app.SplashActivity");
        en.add(FakeNode.node("android.widget.TextView", 890, 100, 1020, 170).txt("Skip").click());
        Hit h6 = match(en);
        check("R6a 英文「Skip」精确命中", h6 != null && "global_exact".equals(h6.appId));
        FakeNode en2 = FakeNode.root("com.example.app.SplashActivity");
        en2.add(FakeNode.node("android.widget.TextView", 890, 100, 1020, 170).txt("SKIP").click());
        check("R6b 「SKIP」大小写归一命中", match(en2) != null && "global_exact".equals(match(en2).appId));

        System.out.println("── 防误点红线（绝不乱点·issue#12 整治）──");

        // R7 诱导长文案：含诱导词的包含文案绝不中（长度也超上限）
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

        // R9 不可见（visibleToUser 红线：不可见=陷阱/死区）
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

        // R11 普通主页（信息流）：文本流里偶现「跳过」字样的长文不中（>8 字）
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
        check("R16e 「5 秒后可跳过广告」→5s（v3.10.0 新形态）", AdDecisionCore.parseCountdown("5 秒后可跳过广告") == 5);

        // R17 精确优先于形态：树内「跳过 5」（形态）与「跳过」（精确）并存 → 命中精确「跳过」
        FakeNode both = FakeNode.root("com.example.app.SplashActivity");
        both.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        both.add(FakeNode.node("android.widget.TextView", 700, 100, 840, 170).txt("跳过 5").click());
        both.add(FakeNode.node("android.widget.TextView", 880, 100, 1020, 170).txt("跳过").click());
        Hit h17 = match(both);
        check("R17 精确命中优先于形态/包含命中",
                h17 != null && "global_exact".equals(h17.appId) && "跳过".equals(h17.label),
                "实际: " + (h17 == null ? "null" : h17.appId + "/" + h17.label));

        System.out.println("── v3.10.0 新增：倒计时形态 / 大小写 / 分应用表 ──");

        // R18 倒计时形态超 8 字（旧版 8 字上限拦不住的形态，正则层照中）
        FakeNode cd = FakeNode.root("com.hunantv.imgo.splash.SplashActivity");
        cd.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        cd.add(FakeNode.node("android.widget.TextView", 880, 100, 1040, 180).txt("5 秒后可跳过广告").click());
        Hit h18 = match(cd);
        check("R18a 「5 秒后可跳过广告」（9字）形态命中", h18 != null && "5 秒后可跳过广告".equals(h18.label)
                && "global_pattern".equals(h18.appId),
                "实际: " + (h18 == null ? "null" : h18.appId + "/" + h18.label));
        FakeNode cd2 = FakeNode.root("com.hunantv.imgo.splash.SplashActivity");
        cd2.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        cd2.add(FakeNode.node("android.widget.TextView", 880, 100, 1040, 180).txt("3s后可跳过").click());
        check("R18b 「3s后可跳过」形态命中", match(cd2) != null && "3s后可跳过".equals(match(cd2).label));

        // R19 「跳过广告 5s」形态（P1 尾部带 s/秒）
        FakeNode cd3 = FakeNode.root("com.example.app.SplashActivity");
        cd3.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        cd3.add(FakeNode.node("android.widget.TextView", 880, 100, 1050, 180).txt("跳过广告 5s").click());
        Hit h19 = match(cd3);
        check("R19 「跳过广告 5s」形态命中", h19 != null && "global_pattern".equals(h19.appId),
                "实际: " + (h19 == null ? "null" : h19.appId));

        // R20 大小写归一（英文全族）
        FakeNode sa = FakeNode.root("com.example.app.SplashActivity");
        sa.add(FakeNode.node("android.widget.TextView", 890, 100, 1060, 170).txt("Skip Ad").click());
        check("R20a 「Skip Ad」精确命中", match(sa) != null && "global_exact".equals(match(sa).appId));
        FakeNode sa2 = FakeNode.root("com.example.app.SplashActivity");
        sa2.add(FakeNode.node("android.widget.TextView", 890, 100, 1060, 170).txt("skip this ad").click());
        check("R20b 「skip this ad」小写命中", match(sa2) != null);

        // R21 分应用规则表（28 款：微博/喜马拉雅/B站等真实文案）
        FakeNode wb = FakeNode.root("com.sina.weibo.SplashActivity");
        wb.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        wb.add(FakeNode.node("android.widget.TextView", 880, 100, 1040, 180).txt("跳过广告").click());
        Hit h21a = matchPkg(wb, "com.sina.weibo");
        check("R21a 微博包命中分应用层（app_exact 优先于全局）",
                h21a != null && "app_exact".equals(h21a.appId),
                "实际: " + (h21a == null ? "null" : h21a.appId));
        Hit h21b = matchPkg(wb, "com.unknown.app");
        check("R21b 非表内包走全局精确（global_exact）",
                h21b != null && "global_exact".equals(h21b.appId),
                "实际: " + (h21b == null ? "null" : h21b.appId));
        // 表内包前缀匹配（子进程/渠道包名 com.sina.weibo.xxx）
        Hit h21c = matchPkg(wb, "com.sina.weibo.module.channel.MainTabActivity");
        check("R21c 渠道包名前缀照中（com.sina.weibo.xxx）",
                h21c != null && "app_exact".equals(h21c.appId),
                "实际: " + (h21c == null ? "null" : h21c.appId));

        System.out.println("── v3.10.0 新增：SDK 窗图片关闭键 / 诱导词否决 ──");

        // R22 close 族 vid：仅 SDK 广告窗/学习窗 + 屏幕边缘带
        Ctx sdkCtx = splashCtx();
        sdkCtx.sdkAdWin = true;
        FakeNode ks = FakeNode.root("com.kwad.sdk.splash.presentation.SplashAdActivity");
        ks.add(FakeNode.node("android.view.TextureView", 0, 0, 1080, 2400));
        FakeNode ksClose = FakeNode.node("android.widget.ImageView", 960, 80, 1040, 160);
        ksClose.vid("com.kwad.sdk:id/ksad_splash_close_view");
        ks.add(ksClose);
        Hit h22a = RuleEngine.match(ks, sdkCtx);
        check("R22a SDK 窗 close vid（顶部边缘带）命中 sdk_close_vid",
                h22a != null && "sdk_close_vid".equals(h22a.appId),
                "实际: " + (h22a == null ? "null" : h22a.appId));
        // 非 SDK 窗：绝不中（普通应用页面的 close 控件不碰）
        Ctx normCtx = splashCtx();
        normCtx.sdkAdWin = false;
        check("R22b 非 SDK 窗 close vid 不命中（普通页面绝不乱点）",
                RuleEngine.match(ks, normCtx) == null,
                "实际: " + (RuleEngine.match(ks, normCtx) == null ? "null" : "命中"));
        // 学习窗等同 SDK 窗资格
        Ctx learnCtx = splashCtx();
        learnCtx.learnedCls = true;
        check("R22c 学习窗 close vid 命中（教过的广告窗）",
                RuleEngine.match(ks, learnCtx) != null);
        // 屏幕中央的 close vid 不中（边缘带之外）
        FakeNode ksMid = FakeNode.root("com.kwad.sdk.splash.presentation.SplashAdActivity");
        FakeNode midClose = FakeNode.node("android.widget.ImageView", 500, 1100, 580, 1180);
        midClose.vid("com.kwad.sdk:id/ksad_splash_close_view");
        ksMid.add(midClose);
        check("R22d 屏幕中央 close vid 不命中（关闭键天然在边缘）",
                RuleEngine.match(ksMid, sdkCtx) == null);

        // R23 诱导词否决（≤8 字包含文案含诱导词绝不中）
        FakeNode veto = FakeNode.root("com.example.app.SplashActivity");
        veto.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        veto.add(FakeNode.node("android.widget.TextView", 300, 1900, 800, 2000).txt("点击跳过领取福利").click());
        check("R23a 「点击跳过领取福利」（8字）诱导词否决不中", match(veto) == null);
        FakeNode veto2 = FakeNode.root("com.example.app.SplashActivity");
        veto2.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        veto2.add(FakeNode.node("android.widget.TextView", 300, 1900, 800, 2000).txt("摇一摇跳过详情").click());
        check("R23b 「摇一摇跳过详情」（6字）否决不中", match(veto2) == null);

        // R24 短包含 8 字上限边界：≤8 中、>8 走形态层
        FakeNode ok8 = FakeNode.root("com.example.app.SplashActivity");
        ok8.add(FakeNode.node("android.widget.ImageView", 0, 0, 1080, 2400).click());
        ok8.add(FakeNode.node("android.widget.TextView", 880, 100, 1040, 180).txt("跳过 5秒后可跳").click());
        check("R24a 8 字含「跳过」无诱导词命中", match(ok8) != null,
                "实际: " + (match(ok8) == null ? "null" : match(ok8).label));

        System.out.println("── v3.10.0 新增：launcher 黑名单 / SDK 窗指纹扩充 ──");

        // R25 launcher 黑名单（桌面绝不扫描/学习/回弹）
        check("R25a MIUI 桌面", AdDecisionCore.isLauncherLike("com.miui.home"));
        check("R25b 第三方 lawnchair 桌面（v3.10.0 扩充）", AdDecisionCore.isLauncherLike("com.android.lawnchair"));
        check("R25c trebuchet 桌面（v3.10.0 扩充）", AdDecisionCore.isLauncherLike("org.cyanogenmod.trebuchet"));
        check("R25d 含 launcher 字样的任意桌面", AdDecisionCore.isLauncherLike("com.huawei.android.launcher"));
        check("R25e 微信【绝不】是桌面（issue#12 回弹误伤主犯校验）", !AdDecisionCore.isLauncherLike("com.tencent.mm"));
        check("R25f 普通应用不是桌面", !AdDecisionCore.isLauncherLike("com.sina.weibo"));

        // R26 SDK 广告窗指纹扩充（v3.10.0：小米 zeus / adview / youmi / domob / doubleclick）
        check("R26a 小米 zeus 广告窗", AdDecisionCore.isAdActivityClass("com.miui.zeus.splash.SplashAdActivity"));
        check("R26b adview 广告窗", AdDecisionCore.isAdActivityClass("com.adview.AdActivity"));
        check("R26c youmi 广告窗", AdDecisionCore.isAdActivityClass("com.youmi.ads.SplashActivity"));
        check("R26d domob 广告窗", AdDecisionCore.isAdActivityClass("cn.domob.ui.SplashAdActivity"));
        check("R26e doubleclick 广告窗", AdDecisionCore.isAdActivityClass("com.doubleclick.DclkActivity"));
        check("R26f 穿山甲广告窗（原有）", AdDecisionCore.isAdActivityClass("com.bytedance.sdk.openadsdk.stub.activity.Stub_Standard_Portrait_Activity"));
        check("R26g 普通应用主页【绝不】是广告窗", !AdDecisionCore.isAdActivityClass("com.sina.weibo.SplashActivity"));

        // R27 倒计时门控数值（形态层标签全链路）
        check("R27a 「跳过广告 5s」→5s", AdDecisionCore.parseCountdown("跳过广告 5s") == 5);
        check("R27b 「12s后可跳过」→12s", AdDecisionCore.parseCountdown("12s后可跳过") == 12);

        // R28 规则库规模（发版日志统计口径）
        check("R28a 分应用表 ≥28 款", RuleEngine.stats().contains("分应用表 28"));
        check("R28b 全局精确 ≥19 条", RuleEngine.stats().contains("全局精确 19"));

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
