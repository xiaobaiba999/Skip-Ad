package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.AdTaskCore;
import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.MiniProgramGuard;
import com.zai.adshield.core.VideoSplashGuard;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * False-Positive 场景测试（v7.9.4 新套件）。
 *
 * 目的不是证明「该点击」，而是证明【绝对不应该点击】——用户指定的
 * 20 类误触红线场景，每类断言：
 *  · decide() 恒 NONE（不点击）；
 *  · 任务面零副作用（无任务 S_NONE 拒绝一切点击提交 → 不进入 CLICKING
 *    → 不触发 HumanTap → 不触发 retry——决策 NONE 则服务侧根本不会
 *    taskBegin/派发，AdTaskCore 层用 S_NONE 真值表锁死该链）。
 *
 * 20 场景（用户目录规格全建齐）：
 *  1 银行页面普通按钮 · 2 政务页面普通按钮 · 3 相机页面 · 4 支付页面
 *  · 5 商品详情页 · 6 商品购买按钮 · 7 收藏按钮 · 8 分享按钮 · 9 评论按钮
 *  · 10 聊天窗口 · 11 更新提示 · 12 权限请求 · 13 会员弹窗 · 14 普通
 *  PopupWindow · 15 普通 Dialog · 16 普通悬浮按钮 · 17 多窗口 ·
 *  18 Launcher · 19 小程序/支付/团购容器 · 20 反馈面板。
 *
 * 特别测试（用户红线）：「关闭 / × / 取消 / 跳过 / 稍后 / 知道了」
 * 孤立词单独出现（无广告语境/无广告形态的普通页面）不得直接构成
 * 点击理由——同时保留对照侧：真实广告语境（角标在场/开屏形态）下
 * 同词照常出手（不得为降误触把识别能力一刀切关闭）。
 *
 * 诚实口径：JVM 仿真树与真机页面同构但非真机验证（L3 另行）；
 * 每条断言对应一类真实误触事故（用户指令：宁可漏掉一个不确定广告，
 * 也不误点一次银行/支付/购买/聊天/权限按钮）。
 */
public final class FalsePositiveScenarioTest {

    public static void main(String[] args) {
        s1_guardPages();            // 场景 1-3：银行/政务/支付
        s2_ecommercePages();        // 场景 4-9：商品详情/购买/收藏/分享/评论
        s3_cameraChatLauncher();   // 场景 3/10/18：相机/聊天/Launcher
        s4_dialogPopups();          // 场景 11-16：更新/权限/会员/弹窗/悬浮
        s5_overlayMultiWindow();   // 场景 16/17：悬浮/多窗口
        s6_miniProgramContainer(); // 场景 19：小程序容器
        s7_feedbackPanel();         // 场景 20：反馈面板
        s8_isolatedWords();         // 特别测试：孤立词红线 + 对照侧
        s9_taskLevelGates();        // 任务面零副作用（S_NONE 真值表）
        TestHost.done();
    }

    /* ══════════ 场景 1-3 · 守护名单页面 ══════════ */
    static void s1_guardPages() {
        TestHost.suite("FP · S1 银行/政务/支付页面普通按钮零出手");

        // ── 场景 1：银行主界面（可滚轮播 + 长公告 + 功能宫格 + 右上消息图标）──
        SimNode bank = new SimNode();
        bank.cls = "android.widget.FrameLayout";
        bank.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode title = new SimNode("中国工商银行", 60, 80, 400, 160);
        SimNode msg = new SimNode("", 990, 90, 1050, 150);   // 右上消息图标（角落候选真实在树中）
        msg.cls = "android.widget.ImageView";
        msg.clickable = true;
        SimNode balance = new SimNode("可用余额 5,208.66 元", 60, 200, 700, 280);
        SimNode notice = new SimNode("尊敬的客户：为提升服务体验，本行将于本周六凌晨进行系统升级维护，届时部分功能可能出现短暂不可用，敬请提前安排相关业务。", 60, 320, 1020, 560);
        SimNode pager = new SimNode("", 0, 600, 1080, 1400); // 轮播 banner（可滚）
        pager.cls = "androidx.viewpager.widget.ViewPager";
        pager.scrollable = true;
        SimNode grid = new SimNode("", 0, 1450, 1080, 1950);  // 功能宫格
        grid.cls = "android.widget.GridLayout";
        String[] fn = {"转账", "理财", "存款", "缴费", "基金", "贷款", "外汇", "贵金属"};
        for (int i = 0; i < fn.length; i++) {
            SimNode b = new SimNode(fn[i], 80 + (i % 4) * 250, 1470 + (i / 4) * 220,
                    280 + (i % 4) * 250, 1630 + (i / 4) * 220);
            b.clickable = true;
            grid.add(b);
        }
        bank.add(title); bank.add(msg); bank.add(balance); bank.add(notice); bank.add(pager); bank.add(grid);
        Ctx cb = ctxOf("com.icbc", "com.icbc.MainActivity", true, 3500);
        cb.guard = true;
        ScanFind fb = AdDecisionCore.walk(bank, cb);
        TestHost.check(AdDecisionCore.isGuardPkg("com.icbc"), "银行: 守护名单命中（isGuardPkg）");
        TestHost.check(fb.scrollPresent, "银行: 轮播容器可滚特征在场（诚实记录风险面存在）");
        TestHost.check(!fb.corners.isEmpty(), "银行: 右上消息图标确被收集为角落候选（风险面在场）");
        Decision db = AdDecisionCore.decide(fb, cb);
        TestHost.check(noneClick(db), "FP1 银行页面普通按钮零出手（守护名单+内容形态）");

        // ── 场景 2：政务办事页（公告列表可滚 + 功能按钮）──
        SimNode gov = new SimNode();
        gov.cls = "android.widget.FrameLayout";
        gov.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode gTitle = new SimNode("办事大厅", 60, 90, 400, 170);
        SimNode gList = new SimNode("", 0, 200, 1080, 2000);
        gList.cls = "androidx.recyclerview.widget.RecyclerView";
        gList.scrollable = true;
        String[] gv = {"社保查询", "公积金提取", "预约办事", "进度查询", "电子证照", "纳税服务"};
        for (int i = 0; i < gv.length; i++) {
            SimNode row = new SimNode(gv[i], 60, 240 + i * 280, 1020, 400 + i * 280);
            row.clickable = true;
            gList.add(row);
        }
        gov.add(gTitle); gov.add(gList);
        Ctx cg = ctxOf("cn.gov.app.moa", "cn.gov.app.moa.MainActivity", true, 3000);
        cg.guard = true;
        ScanFind fg = AdDecisionCore.walk(gov, cg);
        TestHost.check(AdDecisionCore.isGuardPkg("cn.gov.app.moa"), "政务: 守护名单命中（gov 包名段）");
        TestHost.check(fg.scrollPresent, "政务: 公告列表可滚特征在场");
        Decision dg = AdDecisionCore.decide(fg, cg);
        TestHost.check(noneClick(dg), "FP2 政务页面普通按钮零出手");

        // ── 场景 4：支付确认页（金额 + 密码输入框 + 确认付款按钮）──
        SimNode pay = new SimNode();
        pay.cls = "android.widget.FrameLayout";
        pay.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode pTitle = new SimNode("向商家付款", 60, 100, 500, 180);
        SimNode amount = new SimNode("¥ 128.00", 400, 400, 680, 560);
        SimNode pwd = new SimNode("", 240, 700, 840, 860);   // 密码输入框
        pwd.cls = "android.widget.EditText";
        pwd.editable = true;
        SimNode ok = new SimNode("确认付款", 240, 1100, 840, 1240);
        ok.clickable = true;
        pay.add(pTitle); pay.add(amount); pay.add(pwd); pay.add(ok);
        Ctx cp = ctxOf("com.eg.android.AlipayGphone", "com.alipay.mobile.pay.ui.DefalutPayActivity", true, 2500);
        cp.guard = true;
        ScanFind fp = AdDecisionCore.walk(pay, cp);
        TestHost.check(fp.editPresent, "支付: 密码输入框特征在场（正常业务界面铁证）");
        TestHost.check(AdDecisionCore.isGuardPkg("com.eg.android.AlipayGphone"), "支付: 守护名单命中（alipay）");
        Decision dp = AdDecisionCore.decide(fp, cp);
        TestHost.check(noneClick(dp), "FP4 支付页面确认付款按钮零出手（宁漏勿误红线）");
    }

    /* ══════════ 场景 4-9 · 电商与内容页面 ══════════ */
    static void s2_ecommercePages() {
        TestHost.suite("FP · S2 电商/内容页普通按钮零出手");

        // ── 场景 5/6：商品详情页（可滚列表 + 长标题 + 购买按钮）──
        SimNode prod = new SimNode();
        prod.cls = "androidx.recyclerview.widget.RecyclerView";
        prod.scrollable = true;
        prod.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode pt = new SimNode("小米商城官方旗舰店正品行货 Redmi Note 14 Pro 5G 智能手机 骁龙处理器 一亿像素 全国联保", 60, 200, 1020, 500);
        SimNode price = new SimNode("¥ 1399", 60, 540, 400, 700);
        SimNode img = new SimNode("", 0, 720, 1080, 1600);
        img.cls = "android.widget.ImageView";
        SimNode cart = new SimNode("加入购物车", 60, 2260, 540, 2400);
        cart.clickable = true;
        SimNode buy = new SimNode("立即购买", 560, 2260, 1020, 2400);
        buy.clickable = true;
        prod.add(pt); prod.add(price); prod.add(img); prod.add(cart); prod.add(buy);
        Ctx cpr = ctxOf("com.taobao.taobao", "com.taobao.tao.detail.activity.DetailActivity", false, 9000);
        ScanFind fpr = AdDecisionCore.walk(prod, cpr);
        TestHost.check(fpr.scrollPresent && fpr.longText, "商品详情: 可滚+长文本特征在场（内容页铁证）");
        Decision dpr = AdDecisionCore.decide(fpr, cpr);
        TestHost.check(noneClick(dpr), "FP5 商品详情页零出手（可滚内容页加购/购买按钮同拒）");

        // ── 场景 6：购买确认弹窗（最小 Dialog + 「立即购买」CTA——最坏情形：弹窗窗期在场）──
        SimNode buyDlg = new SimNode();
        buyDlg.cls = "android.app.Dialog";
        buyDlg.boundsArr = new int[]{60, 900, 1020, 1700};
        SimNode bt = new SimNode("确认订单", 100, 960, 940, 1060);
        SimNode bamt = new SimNode("¥ 1399.00 ×1", 100, 1100, 940, 1200);
        SimNode bbuy = new SimNode("立即购买", 100, 1400, 980, 1560);
        bbuy.clickable = true;
        buyDlg.add(bt); buyDlg.add(bamt); buyDlg.add(bbuy);
        Ctx cbuy = ctxOf("com.taobao.taobao", "com.taobao.tao.detail.activity.DetailActivity", false, 9500);
        cbuy.popupLive = true; // 最坏情形：弹窗窗期在场
        Decision dbuy = AdDecisionCore.decide(AdDecisionCore.walk(buyDlg, cbuy), cbuy);
        TestHost.check(noneClick(dbuy), "FP6 商品购买按钮零出手（CTA 族永不成为点击目标——引擎只点关闭/跳过族）");

        // ── 场景 7/8：文章页右上收藏/分享图标（角落候选真实在树中）──
        SimNode art = new SimNode();
        art.cls = "androidx.recyclerview.widget.RecyclerView";
        art.scrollable = true;
        art.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode body = new SimNode("这是一篇很长很长的正文内容，作者详细论述了移动端广告拦截的工程实践，包括无障碍树扫描、任务状态机、安全闸设计等多个主题，全文约三千字，请慢慢阅读。", 60, 260, 1020, 2000);
        SimNode fav = new SimNode("", 990, 90, 1050, 150);   // 右上收藏图标
        fav.cls = "android.widget.ImageView";
        fav.clickable = true;
        SimNode share = new SimNode("", 900, 90, 960, 150);  // 收藏旁分享图标
        share.cls = "android.widget.ImageView";
        share.clickable = true;
        art.add(body); art.add(fav); art.add(share);
        Ctx ca = ctxOf("com.zhihu.android", "com.zhihu.android.app.ui.activity.QuestionActivity", true, 4000);
        ScanFind fa = AdDecisionCore.walk(art, ca);
        TestHost.check(!fa.corners.isEmpty(), "收藏/分享: 右上图标确被收集为角落候选（风险面在场）");
        TestHost.check(fa.scrollPresent && fa.longText, "收藏/分享: 可滚+长文本特征在场");
        Decision da = AdDecisionCore.decide(fa, ca);
        TestHost.check(noneClick(da), "FP7/8 收藏与分享按钮零出手（无广告形态的角落图标不点）");

        // ── 场景 9：评论区（可滚 + 输入框 + 评论按钮）──
        SimNode cmt = new SimNode();
        cmt.cls = "androidx.recyclerview.widget.RecyclerView";
        cmt.scrollable = true;
        cmt.boundsArr = new int[]{0, 0, 1080, 2200};
        SimNode c1 = new SimNode("写得很好，学到了很多！", 60, 200, 1020, 320);
        SimNode c2 = new SimNode("感谢分享，收藏了。", 60, 360, 1020, 480);
        SimNode cInput = new SimNode("", 60, 2240, 840, 2380);
        cInput.cls = "android.widget.EditText";
        cInput.editable = true;
        SimNode cSend = new SimNode("评论", 860, 2240, 1020, 2380);
        cSend.clickable = true;
        cmt.add(c1); cmt.add(c2); cmt.add(cInput); cmt.add(cSend);
        Ctx cc = ctxOf("com.zhihu.android", "com.zhihu.android.app.ui.activity.CommentActivity", false, 12000);
        ScanFind fc = AdDecisionCore.walk(cmt, cc);
        TestHost.check(fc.scrollPresent && fc.editPresent, "评论: 可滚+输入框特征在场");
        Decision dc = AdDecisionCore.decide(fc, cc);
        TestHost.check(noneClick(dc), "FP9 评论按钮零出手（输入框在场=正常业务界面）");
    }

    /* ══════════ 场景 3/10/18 · 相机 / 聊天 / Launcher ══════════ */
    static void s3_cameraChatLauncher() {
        TestHost.suite("FP · S3 相机/聊天/Launcher 零出手");

        // ── 场景 3：相机页面（模式 ViewPager 可滚 + 快门 + 角落图标族）──
        SimNode cam = new SimNode();
        cam.cls = "android.widget.FrameLayout";
        cam.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode vf = new SimNode("", 0, 0, 1080, 1800);      // 取景器
        vf.cls = "android.view.TextureView";
        SimNode flash = new SimNode("", 60, 80, 120, 140);   // 左上闪光灯
        flash.cls = "android.widget.ImageButton";
        flash.clickable = true;
        SimNode flip = new SimNode("", 960, 80, 1020, 140);  // 右上翻转镜头
        flip.cls = "android.widget.ImageButton";
        flip.clickable = true;
        SimNode modes = new SimNode("拍照 录像 人像 夜景", 0, 1850, 1080, 1980); // 模式条
        modes.cls = "androidx.viewpager.widget.ViewPager";
        modes.scrollable = true;
        SimNode shutter = new SimNode("", 440, 2050, 640, 2250); // 快门（底部中央大按钮）
        shutter.cls = "android.widget.ImageButton";
        shutter.clickable = true;
        SimNode gal = new SimNode("", 100, 2070, 220, 2230);   // 左下相册缩略图
        gal.cls = "android.widget.ImageView";
        gal.clickable = true;
        SimNode vid = new SimNode("", 860, 2070, 980, 2230);  // 右下录像钮
        vid.cls = "android.widget.ImageButton";
        vid.clickable = true;
        cam.add(vf); cam.add(flash); cam.add(flip); cam.add(modes); cam.add(shutter); cam.add(gal); cam.add(vid);
        Ctx ccam = ctxOf("com.android.camera", "com.android.camera.Camera", true, 2500);
        ScanFind fcam = AdDecisionCore.walk(cam, ccam);
        TestHost.check(fcam.scrollPresent, "相机: 模式条可滚特征在场（快门期页面保护）");
        TestHost.check(!fcam.corners.isEmpty(), "相机: 闪光/翻转/相册图标确被收集为角落候选（风险面在场）");
        Decision dcam = AdDecisionCore.decide(fcam, ccam);
        TestHost.check(noneClick(dcam), "FP3 相机页面零出手（快门/闪光/翻转绝不能被当成广告角落键点掉）");

        // ── 场景 10：聊天窗口（可滚 + 输入框 + 含孤立「跳过」「关闭」文案的气泡）──
        // 用户红线：聊天气泡里孤立出现的「跳过/关闭」字样绝不是广告跳过键——
        // 开屏窗龄内（splashLive=true）的聊天窗是真实高频场景（点开聊天就进入窗龄计时）。
        SimNode chat = new SimNode();
        chat.cls = "androidx.recyclerview.widget.RecyclerView";
        chat.scrollable = true;
        chat.boundsArr = new int[]{0, 0, 1080, 2200};
        SimNode m1 = new SimNode("你好，在吗？", 60, 200, 640, 320);
        SimNode m2 = new SimNode("跳过", 60, 400, 300, 520);   // 孤立词「跳过」聊天气泡
        m2.clickable = true;                                   // 气泡可点（长按菜单在部分 IM 真实存在）
        SimNode m3 = new SimNode("关闭", 60, 580, 300, 700);   // 孤立词「关闭」聊天气泡
        m3.clickable = true;
        SimNode m4 = new SimNode("好的，明天见！", 440, 760, 1020, 880);
        SimNode input = new SimNode("", 0, 2240, 860, 2380);
        input.cls = "android.widget.EditText";
        input.editable = true;
        SimNode send = new SimNode("发送", 880, 2240, 1040, 2380);
        send.clickable = true;
        chat.add(m1); chat.add(m2); chat.add(m3); chat.add(m4); chat.add(input); chat.add(send);
        Ctx cch = ctxOf("com.tencent.mm", "com.tencent.mm.ui.LauncherUI", true, 3000);
        ScanFind fch = AdDecisionCore.walk(chat, cch);
        TestHost.check(fch.scrollPresent && fch.editPresent, "聊天: 可滚+输入框特征在场（聊天窗铁证）");
        Decision dch = AdDecisionCore.decide(fch, cch);
        TestHost.check(noneClick(dch), "FP10 聊天窗口零出手（孤立「跳过/关闭」气泡不是点击理由——开屏窗龄内同拒）");
        TestHost.check(!FeedAdGuard.isFeedWindow("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"),
                "聊天: LauncherUI 不进信息流扫描（窗级门）");

        // ── 场景 18：Launcher 桌面（可滚 Workspace + 应用图标标签）──
        SimNode lch = new SimNode();
        lch.cls = "android.widget.FrameLayout";
        lch.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode ws = new SimNode("", 0, 0, 1080, 1900);       // 桌面 Workspace（横向翻页）
        ws.cls = "com.miui.home.launcher.Workspace";
        ws.scrollable = true;
        String[] icons = {"微信", "设置", "相机", "相册", "电话", "短信", "浏览器", "应用商店"};
        for (int i = 0; i < icons.length; i++) {
            SimNode icon = new SimNode("", 90 + (i % 4) * 250, 200 + (i / 4) * 400, 210 + (i % 4) * 250, 320 + (i / 4) * 400);
            icon.cls = "android.widget.ImageView";
            icon.clickable = true;
            SimNode label = new SimNode(icons[i], 80 + (i % 4) * 250, 330 + (i / 4) * 400, 220 + (i % 4) * 250, 390 + (i / 4) * 400);
            ws.add(icon); ws.add(label);
        }
        SimNode clock = new SimNode("10:24", 400, 80, 680, 200);
        SimNode search = new SimNode("搜索", 60, 2260, 240, 2380);
        search.clickable = true;
        lch.add(ws); lch.add(clock); lch.add(search);
        Ctx cl = ctxOf("com.miui.home", "com.miui.home.launcher.Launcher", true, 2000);
        ScanFind fl = AdDecisionCore.walk(lch, cl);
        TestHost.check(fl.scrollPresent, "Launcher: Workspace 可滚特征在场（图标墙保护）");
        TestHost.check(fl.skipCands.isEmpty(), "Launcher: 桌面图标标签零跳过候选");
        Decision dl = AdDecisionCore.decide(fl, cl);
        TestHost.check(noneClick(dl), "FP18 Launcher 桌面零出手（桌面图标绝不盲点——issue#12 毒链历史红线）");
    }

    /* ══════════ 场景 11-16 · 弹窗/对话框/悬浮按钮 ══════════ */
    static void s4_dialogPopups() {
        TestHost.suite("FP · S4 更新/权限/会员/普通弹窗零出手");

        // ── 场景 11：更新提示（单信号「以后再说」——未双确认不点）──
        SimNode upd = new SimNode();
        upd.cls = "android.widget.PopupWindow";
        upd.boundsArr = new int[]{60, 700, 1020, 1800};
        SimNode u1 = new SimNode("发现新版本 v8.0.1，修复若干问题提升体验，建议立即更新。", 100, 780, 940, 1000);
        SimNode u2 = new SimNode("立即更新", 560, 1600, 980, 1700);
        u2.clickable = true;
        SimNode u3 = new SimNode("以后再说", 80, 1600, 500, 1700);
        u3.clickable = true;
        upd.add(u1); upd.add(u2); upd.add(u3);
        Ctx cu = ctxOf("com.example.app", "com.example.app.MainActivity", false, 4000);
        cu.updateDialog = false; // 单信号未确认（服务侧双文案确认才置位）
        ScanFind fu = AdDecisionCore.walk(upd, cu);
        Decision du = AdDecisionCore.decide(fu, cu);
        TestHost.check(noneClick(du), "FP11 更新提示单信号零出手（「以后再说」孤立在场不是点击理由）");

        // ── 场景 12：权限请求对话框（允许/拒绝——系统授权按钮绝不代点）──
        SimNode perm = new SimNode();
        perm.cls = "android.app.Dialog";
        perm.boundsArr = new int[]{60, 800, 1020, 1700};
        SimNode p1 = new SimNode("允许「示例应用」访问你的位置信息吗？", 100, 860, 940, 980);
        SimNode allow = new SimNode("允许", 620, 1400, 980, 1520);
        allow.clickable = true;
        SimNode deny = new SimNode("拒绝", 100, 1400, 460, 1520);
        deny.clickable = true;
        perm.add(p1); perm.add(allow); perm.add(deny);
        // 主断言：弹窗窗期外零出手
        Ctx cpm = ctxOf("com.android.packageinstaller", "com.android.packageinstaller.permission.ui.GrantPermissionsActivity", false, 3000);
        ScanFind fpm = AdDecisionCore.walk(perm, cpm);
        Decision dpm = AdDecisionCore.decide(fpm, cpm);
        TestHost.check(noneClick(dpm), "FP12 权限请求零出手（允许/拒绝均非广告关闭族）");
        // 边界断言：即使弹窗窗期在场（popupLive=true 最坏情形）也无角标/CTA 双信号零出手
        Ctx cpmWorst = ctxOf("com.android.packageinstaller", "com.android.packageinstaller.permission.ui.GrantPermissionsActivity", false, 3000);
        cpmWorst.popupLive = true;
        Decision dpmWorst = AdDecisionCore.decide(AdDecisionCore.walk(perm, cpmWorst), cpmWorst);
        TestHost.check(noneClick(dpmWorst), "FP12b 权限请求最坏情形（弹窗窗期在场）仍零出手");

        // ── 场景 13：会员营销弹窗（CTA 族在场）──
        SimNode vip = new SimNode();
        vip.cls = "android.app.Dialog";
        vip.boundsArr = new int[]{60, 700, 1020, 1900};
        SimNode v1 = new SimNode("开通会员", 300, 760, 780, 860);
        SimNode v2 = new SimNode("立即领取", 300, 900, 780, 1000);
        v2.clickable = true;
        SimNode v3 = new SimNode("限时特惠 每天仅一次", 100, 1060, 940, 1160);
        SimNode vclose = new SimNode("关闭", 960, 720, 1010, 770);
        vclose.clickable = true;
        vip.add(v1); vip.add(v2); vip.add(v3); vip.add(vclose);
        // 主断言：非弹窗广告窗期（会员窗不是广告窗）零出手
        Ctx cv = ctxOf("com.example.app", "com.example.app.MainActivity", false, 6000);
        ScanFind fv = AdDecisionCore.walk(vip, cv);
        TestHost.check(fv.foundClose && fv.ctaCount >= 2, "会员弹窗: CTA 族与关闭键诚实在场（风险面记录）");
        Decision dv = AdDecisionCore.decide(fv, cv);
        TestHost.check(noneClick(dv), "FP13 会员弹窗零出手（会员窗非广告窗期不点）");
        // 边界断言：最坏情形（弹窗窗期在场）也只允许关「关闭」族、绝不对 CTA 出手
        Ctx cvWorst = ctxOf("com.example.app", "com.example.app.MainActivity", false, 6000);
        cvWorst.popupLive = true;
        Decision dvWorst = AdDecisionCore.decide(AdDecisionCore.walk(vip, cvWorst), cvWorst);
        if (dvWorst == null || AdDecisionCore.A_NONE.equals(dvWorst.action)) {
            TestHost.check(true, "FP13b 会员弹窗最坏情形：零出手");
        } else {
            TestHost.check(AdDecisionCore.A_CLOSE.equals(dvWorst.action) && dvWorst.node == vclose,
                    "FP13b 会员弹窗最坏情形：只允许关「关闭」族节点（绝不对「立即领取」CTA 出手）");
        }

        // ── 场景 14：普通 PopupWindow 菜单（收藏/举报/不感兴趣——「不感兴趣」是关闭族词）──
        SimNode menu = new SimNode();
        menu.cls = "android.widget.PopupWindow";
        menu.boundsArr = new int[]{60, 800, 1020, 1600};
        String[] items = {"收藏", "举报", "不感兴趣"};
        for (int i = 0; i < items.length; i++) {
            SimNode it = new SimNode(items[i], 100, 860 + i * 200, 940, 1000 + i * 200);
            it.clickable = true;
            menu.add(it);
        }
        Ctx cm = ctxOf("com.example.app", "com.example.app.MainActivity", false, 5000);
        Decision dm = AdDecisionCore.decide(AdDecisionCore.walk(menu, cm), cm);
        TestHost.check(noneClick(dm), "FP14 普通 PopupWindow 菜单零出手（孤立「不感兴趣」菜单项不是点击理由）");
        Ctx cmWorst = ctxOf("com.example.app", "com.example.app.MainActivity", false, 5000);
        cmWorst.popupLive = true;
        Decision dmWorst = AdDecisionCore.decide(AdDecisionCore.walk(menu, cmWorst), cmWorst);
        TestHost.check(noneClick(dmWorst), "FP14b 普通菜单最坏情形（弹窗窗期在场）仍零出手（无角标无 CTA 双信号）");

        // ── 场景 15：普通表单 Dialog（输入框 + 确定/取消）──
        SimNode form = new SimNode();
        form.cls = "android.app.Dialog";
        form.boundsArr = new int[]{60, 900, 1020, 1700};
        SimNode ft = new SimNode("修改昵称", 100, 960, 940, 1060);
        SimNode fi = new SimNode("", 100, 1100, 940, 1240);
        fi.cls = "android.widget.EditText";
        fi.editable = true;
        SimNode fok = new SimNode("确定", 620, 1300, 980, 1420);
        fok.clickable = true;
        SimNode fc = new SimNode("取消", 100, 1300, 460, 1420);
        fc.clickable = true;
        form.add(ft); form.add(fi); form.add(fok); form.add(fc);
        Ctx cf = ctxOf("com.example.app", "com.example.app.MainActivity", false, 5000);
        cf.popupLive = true; // 最坏情形直接上
        ScanFind ff = AdDecisionCore.walk(form, cf);
        TestHost.check(ff.editPresent, "表单弹窗: 输入框在场（表单铁证）");
        Decision df = AdDecisionCore.decide(ff, cf);
        TestHost.check(noneClick(df), "FP15 普通表单 Dialog 零出手（输入框在场不点确定/取消）");

        // ── 场景 16：普通悬浮按钮 FAB（右缘中部——中部边带仅强广告形态可出手）──
        SimNode page = new SimNode();
        page.cls = "androidx.recyclerview.widget.RecyclerView";
        page.scrollable = true;
        page.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode art = new SimNode("正文内容列表页，承载若干条目，用户正在浏览信息流内容，页面具备可滚动特征，属于正常业务界面。", 60, 200, 1020, 1000);
        SimNode fab = new SimNode("", 940, 2100, 1040, 2200); // 右缘中部悬浮球（strongOnly 候选）
        fab.cls = "android.widget FloatingActionButton";
        fab.clickable = true;
        page.add(art); page.add(fab);
        Ctx cfa = ctxOf("com.example.app", "com.example.app.MainActivity", true, 4000);
        ScanFind ffa = AdDecisionCore.walk(page, cfa);
        TestHost.check(!ffa.corners.isEmpty(), "FAB: 悬浮球确被收集为中部边带候选（风险面在场）");
        TestHost.check(ffa.scrollPresent, "FAB: 内容页可滚特征在场");
        Decision dfa = AdDecisionCore.decide(ffa, cfa);
        TestHost.check(noneClick(dfa), "FP16 普通悬浮按钮零出手（strongOnly 候选无强广告形态不出手）");
    }

    /* ══════════ 场景 17 · 多窗口分屏 ══════════ */
    static void s5_overlayMultiWindow() {
        TestHost.suite("FP · S5 多窗口分屏零出手");

        // 分屏另一半的银行内容窗（同屏另一应用不被误扫出手）
        SimNode half = new SimNode();
        half.cls = "android.widget.FrameLayout";
        half.boundsArr = new int[]{0, 0, 1080, 1200};
        SimNode ht = new SimNode("账户总览", 60, 60, 400, 130);
        SimNode hbal = new SimNode("储蓄卡（*6688） 余额 12,345.67", 60, 160, 700, 240);
        SimNode hlist = new SimNode("", 0, 260, 1080, 1150);
        hlist.cls = "androidx.recyclerview.widget.RecyclerView";
        hlist.scrollable = true;
        String[] tx = {"工资代发 +8,000.00", "消费 -128.00", "转账 -500.00"};
        for (int i = 0; i < tx.length; i++) {
            SimNode row = new SimNode(tx[i], 60, 300 + i * 240, 1020, 440 + i * 240);
            hlist.add(row);
        }
        half.add(ht); half.add(hbal); half.add(hlist);
        Ctx ch = ctxOf("com.icbc", "com.icbc.MainActivity", true, 1500);
        ch.guard = true;
        Decision dh = AdDecisionCore.decide(AdDecisionCore.walk(half, ch), ch);
        TestHost.check(noneClick(dh), "FP17 多窗口分屏内容窗零出手（另一半不因分屏被误扫出手）");
    }

    /* ══════════ 场景 19 · 小程序/支付/团购容器 ══════════ */
    static void s6_miniProgramContainer() {
        TestHost.suite("FP · S6 小程序/团购容器纵深防御");

        // 容器内即使是「真广告形态树」（跳过键+×+角标俱全）也恒零出手（L0 纵深防御）
        SimNode juicy = new SimNode();
        juicy.cls = "android.widget.FrameLayout";
        juicy.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode img = new SimNode("", 0, 0, 1080, 2100);
        img.cls = "android.widget.ImageView";
        SimNode skip = TestHost.skipButton("跳过广告");
        skip.clickable = true;
        SimNode badge = new SimNode("广告", 60, 2140, 180, 2190);
        SimNode close = new SimNode("×", 1000, 90, 1050, 140);
        close.clickable = true;
        juicy.add(img); juicy.add(skip); juicy.add(badge); juicy.add(close);

        String[][] containers = {
                {"com.tencent.mm", "com.tencent.mm.plugin.appbrand.ui.AppBrandUI"},
                {"com.eg.android.AlipayGphone", "com.alipay.mobile.nebulacore.ui.H5Activity"},
                {"com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.commercialize.CommercializeContainerActivity"},
        };
        for (String[] c : containers) {
            TestHost.check(MiniProgramGuard.isMiniProgramWindow(c[0], c[1]),
                    "容器识别: " + c[1].substring(c[1].length() - 24));
            Ctx ctx = ctxOf(c[0], c[1], true, 3000);
            ScanFind f = AdDecisionCore.walk(juicy, ctx);
            TestHost.check(f.skipCands.isEmpty() && f.corners.isEmpty(),
                    "容器 walk 零候选: " + c[1].substring(c[1].length() - 24));
            Decision d = AdDecisionCore.decide(f, ctx);
            TestHost.check(noneClick(d),
                    "FP19 小程序/团购容器零出手（真广告形态树同拒·L0）: " + c[1].substring(c[1].length() - 24));
        }
    }

    /* ══════════ 场景 20 · 反馈面板 ══════════ */
    static void s7_feedbackPanel() {
        TestHost.suite("FP · S7 反馈面板零出手");

        // 广告已关后弹出的「我为什么会看到广告」面板（含取消/关闭/× 全套关闭族词）
        SimNode sheet = new SimNode();
        sheet.cls = "androidx.recyclerview.widget.RecyclerView";
        sheet.scrollable = true;
        sheet.boundsArr = new int[]{60, 800, 1020, 2200};
        SimNode t = new SimNode("我为什么会看到广告", 100, 840, 900, 910);
        SimNode r = new SimNode("不感兴趣", 100, 960, 900, 1030);
        SimNode cancel = new SimNode("取消", 100, 2000, 500, 2080);
        cancel.clickable = true;
        SimNode close = new SimNode("关闭", 560, 2000, 940, 2080);
        close.clickable = true;
        SimNode x = new SimNode("×", 960, 820, 1010, 870);
        x.clickable = true;
        sheet.add(t); sheet.add(r); sheet.add(cancel); sheet.add(close); sheet.add(x);
        Ctx c = ctxOf("com.tencent.mm", "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI", false, 9000);
        Decision d = AdDecisionCore.decide(AdDecisionCore.walk(sheet, c), c);
        TestHost.check(noneClick(d), "FP20 反馈面板零出手（面板内取消/关闭/× 不构成广告关闭点击理由）");
        // 任务面：FEEDBACK 态不对原广告补刀 + 面板链路终态不复活
        int st = AdTaskCore.onFeedbackSeen(AdTaskCore.S_VERIFYING);
        TestHost.check(!AdTaskCore.clickAllowed(st), "反馈面板: FEEDBACK 期不对原广告补刀");
        st = AdTaskCore.onFeedbackDone(st);
        TestHost.check(AdTaskCore.isTerminal(st) && !AdTaskCore.clickAllowed(st),
                "反馈面板: 面板收起 → SUCCESS 终态不复活原任务");
    }

    /* ══════════ 特别测试 · 孤立词红线 + 对照侧 ══════════ */
    static void s8_isolatedWords() {
        TestHost.suite("FP · S8 孤立词红线（关闭/×/取消/跳过/稍后/知道了）");

        // ── 红线侧 A：普通设置页（非开屏窗、非弹窗窗期）上的孤立词按钮 ──
        String[] words = {"关闭", "×", "取消", "跳过", "稍后", "知道了"};
        for (String w : words) {
            SimNode page = new SimNode();
            page.cls = "android.widget.FrameLayout";
            page.boundsArr = new int[]{0, 0, 1080, 2400};
            SimNode t = new SimNode("设置", 60, 90, 400, 170);
            SimNode row = new SimNode("账号与安全", 60, 260, 1020, 400);
            row.clickable = true;
            SimNode wb = new SimNode(w, 860, 260, 1020, 400); // 页面上的孤立词按钮
            wb.clickable = true;
            SimNode row2 = new SimNode("通知管理", 60, 440, 1020, 580);
            row2.clickable = true;
            page.add(t); page.add(row); page.add(wb); page.add(row2);
            Ctx c = ctxOf("com.example.app", "com.example.app.SettingsActivity", false, 9000);
            Decision d = AdDecisionCore.decide(AdDecisionCore.walk(page, c), c);
            TestHost.check(noneClick(d), "孤立词红线·" + w + ": 普通页面单独出现零出手");
        }

        // ── 红线侧 B：开屏窗龄内的内容形态页（滚动列表行里出现孤立「跳过/关闭」）──
        // （用户红线最强形态：不是靠「过了 12s」兜底，而是内容形态本身拒绝）
        for (String w : new String[]{"跳过", "关闭", "×"}) {
            SimNode page = new SimNode();
            page.cls = "androidx.recyclerview.widget.RecyclerView";
            page.scrollable = true;
            page.boundsArr = new int[]{0, 0, 1080, 2400};
            SimNode row1 = new SimNode("推荐精选内容第一条", 60, 200, 1020, 320);
            SimNode rowW = new SimNode(w, 60, 400, 320, 520); // 列表行内的孤立词
            rowW.clickable = true;
            SimNode row2 = new SimNode("推荐精选内容第二条", 60, 600, 1020, 720);
            page.add(row1); page.add(rowW); page.add(row2);
            Ctx c = ctxOf("com.example.app", "com.example.app.MainActivity", true, 2500);
            Decision d = AdDecisionCore.decide(AdDecisionCore.walk(page, c), c);
            TestHost.check(noneClick(d), "孤立词红线·" + w + ": 开屏窗龄内内容形态页零出手");
        }

        // ── 对照侧（不得为降误触一刀切关闭识别能力——用户指令八）──
        // 真广告语境下同词照常出手：
        // ① 开屏形态窗 + 文本跳过键 → skip_text 出手（产品核心能力零回退）
        SimNode splash = new SimNode();
        splash.cls = "android.widget.FrameLayout";
        splash.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode img = new SimNode("", 0, 0, 1080, 2100);
        img.cls = "android.widget.ImageView";
        SimNode skip = TestHost.skipButton("跳过");
        skip.clickable = true;
        splash.add(img); splash.add(skip);
        Ctx cs = ctxOf("com.example.app", "com.example.app.SplashActivity", true, 600);
        Decision ds = AdDecisionCore.decide(AdDecisionCore.walk(splash, cs), cs);
        TestHost.check(!noneClick(ds), "对照·跳过键: 真开屏形态窗照常出手（识别能力零回退）");
        // ② 角标在场 + × 关闭键 → badge_close 出手（语境使然非孤立词）
        SimNode badgeWin = new SimNode();
        badgeWin.cls = "android.widget.FrameLayout";
        badgeWin.boundsArr = new int[]{0, 0, 1080, 2400};
        SimNode bimg = new SimNode("", 0, 0, 1080, 2100);
        bimg.cls = "android.widget.ImageView";
        SimNode badge = new SimNode("广告", 60, 2140, 180, 2190);
        SimNode x = new SimNode("×", 1000, 90, 1050, 140);
        x.clickable = true;
        badgeWin.add(bimg); badgeWin.add(badge); badgeWin.add(x);
        Ctx cb = ctxOf("com.example.app", "com.example.app.MainActivity", true, 800);
        Decision db = AdDecisionCore.decide(AdDecisionCore.walk(badgeWin, cb), cb);
        TestHost.check(!noneClick(db), "对照·角标+×: 广告语境在场照常出手（词义需语境证据支撑）");
    }

    /* ══════════ 任务面零副作用（决策 NONE ⇒ 无任务无点击无人手无重试）══════════ */
    static void s9_taskLevelGates() {
        TestHost.suite("FP · S9 决策 NONE ⇒ 任务面零副作用");

        // 无任务（S_NONE）真值表：不创建广告任务 → 不进入 CLICKING →
        // 不触发 HumanTap → 不触发 retry（服务侧 taskBegin 只在决策
        // 非 NONE 时发生——纯函数面用 S_NONE 真值表锁死该链）
        TestHost.check(!AdTaskCore.clickAllowed(AdTaskCore.S_NONE),
                "任务面: S_NONE 拒绝一切点击提交（不创建任务·不进入 CLICKING）");
        TestHost.check(AdTaskCore.onDispatch(AdTaskCore.S_NONE) == AdTaskCore.S_NONE,
                "任务面: S_NONE 派发被拒（无 HumanTap 触发路径）");
        TestHost.check(!AdTaskCore.retryGuardsOk(false, false, false, false, false),
                "任务面: 零证据五要件全空不重试（无广告在场/无候选/无语义）");
        TestHost.check(AdTaskCore.onVerifyAdStill(AdTaskCore.S_NONE, 0, 3) == AdTaskCore.S_NONE,
                "任务面: 无任务不复验不重试（RETRY 状态不无中生有）");
        TestHost.check(!AdTaskCore.zeroVerifyConfirmed(null, 1000L, false),
                "任务面: 无首零时刻不判关（复验针不无中生有）");
    }

    /* ══════════ 工具 ══════════ */
    static boolean noneClick(Decision d) {
        return d == null || AdDecisionCore.A_NONE.equals(d.action);
    }

    static Ctx ctxOf(String pkg, String activity, boolean splash, long elapsed) {
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = pkg;
        ctx.activityId = activity;
        ctx.elapsedMs = elapsed;
        ctx.splashLive = splash;
        return ctx;
    }
}
