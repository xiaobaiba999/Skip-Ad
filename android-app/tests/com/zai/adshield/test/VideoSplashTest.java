package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.VideoSplashGuard;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * 视频开屏卫士测试（v7.8.0 · 用户十八轮实锤「只要是开屏广告带视频的你都未命中」）。
 * ─────────────────────────────────────────────────────────────
 * 四死因（扫描日志驱动诊断）：
 *  ① 12s 观察窗天花板——视频开屏跳过钮常在视频末（5~15s）才露头，>12s 的按钮
 *    引擎已出窗判 CLEAN「不再扫」；
 *  ② 静默事件源——SurfaceView 视频播放零内容事件，零候选退避（3 次+2.6s）
 *    杀链后零事件零复活；
 *  ③ 预算烧穿——图片键（进度环无数字 cdN=0 门控直通）300ms 起 3 击连发
 *    1 秒烧光预算，按钮真激活时引擎已 gaveUp 收手；
 *  ④ L4 形态确认门——第一方视频开屏窗无角标/无热区/非 SDK 指纹、树 >60
 *    节点（WebView 容器常态 >150）→ 角落候选收集了永不出手。
 *
 * 套件结构（S1~S9）：
 *  S1 视频画像真值表（videoLike 六路）
 *  S2 观察窗延长（windowDeadlineMs：videoLike → 20s；普通 → 12s）
 *  S3 轮询针节拍（pollDue 1.5s + 12~20s 段兜底针数）
 *  S4 白点节流（strikeGapOk 1.5s/击 + 视频预算 5 + 新键预算刷新）
 *  S5 L4 形态门放宽（videoLike + 200 节点树 + 8s 时刻角落键出手）
 *  S6 视频开屏键词典（「5s 跳过」「5秒跳过」无「后」变体 + 整词键回归）
 *  S7 时间线仿真（第一方视频开屏 0~20s：画像→轮询→新键出手三死因对照）
 *  S8 常量契约（10 项全在场）
 *  S9 自适应轮询节拍（v8.0.1 补测：adaptivePollGapMs 三档 + 4 参 pollDue
 *     真值表——低证据降档/终态降档/证据恢复升档的全边界；P3-2 videoPoll
 *     重构的前置钉子：walkDue 针与 miss-streak 的交互必须先在此纯函数面钉死）
 */
public final class VideoSplashTest {

    public static void main(String[] args) {
        /* ════ S1 视频画像真值表（6 项）════ */
        TestHost.suite("VideoSplash · S1 视频画像真值表（窗龄/零候选/静默/SDK 四观测面）");
        TestHost.check(VideoSplashGuard.videoLike(3000, 3, 2000, false),
                "第一方视频容器：窗龄 3s 零候选×3 静默 2s → 画像成立（SurfaceView 在放）");
        TestHost.check(!VideoSplashGuard.videoLike(2000, 3, 2000, false),
                "窗龄 2s < 2.2s → 画像不成立（按钮常态渲染期 0~2s——更早零候选只是没渲染）");
        TestHost.check(!VideoSplashGuard.videoLike(3000, 2, 2000, false),
                "零候选 ×2 < 3 → 画像不成立（退避门槛同源）");
        TestHost.check(!VideoSplashGuard.videoLike(3000, 3, 1000, false),
                "树静默 1s < 1.6s → 画像不成立（树有动静 = 不是静默视频容器）");
        TestHost.check(VideoSplashGuard.videoLike(3500, 3, 500, true),
                "SDK 广告窗 + 零候选×3 窗龄 3.5s → 画像成立（窗类即铁证，静默不要求）");
        TestHost.check(!VideoSplashGuard.videoLike(3000, 3, 500, true),
                "SDK 广告窗窗龄 3s < 3.2s → 画像不成立（SDK 档门槛）");

        /* ════ S2 观察窗延长（5 项）════ */
        TestHost.suite("VideoSplash · S2 观察窗自适应延长（12s→20s 死因①根治）");
        TestHost.checkEq(20000L, VideoSplashGuard.windowDeadlineMs(true),
                "视频画像窗 → 观察窗 20s（视频末露头按钮有人扫）");
        TestHost.checkEq(12000L, VideoSplashGuard.windowDeadlineMs(false),
                "普通开屏窗 → 观察窗 12s 保持（原 WINDOW_MS 契约零变化）");
        TestHost.check(15000 <= VideoSplashGuard.windowDeadlineMs(true),
                "15s 露头按钮：视频窗内仍在观察（旧 12s 封顶 = 字面意义不再扫）");
        TestHost.check(15000 > VideoSplashGuard.windowDeadlineMs(false),
                "15s 露头按钮：普通窗已出窗（对照——死因①复现）");
        TestHost.check(VideoSplashGuard.VIDEO_EXT_WINDOW_MS == 20000, "VIDEO_EXT_WINDOW_MS 常量在场");

        /* ════ S3 轮询针节拍（5 项）════ */
        TestHost.suite("VideoSplash · S3 轮询针节拍（静默事件源兜底——死因②根治）");
        TestHost.check(VideoSplashGuard.pollDue(null, 1000), "无记录 → 立即到期（首针）");
        TestHost.check(!VideoSplashGuard.pollDue(1000L, 2000L), "距上针 1s < 1.5s → 未到期（节流）");
        TestHost.check(VideoSplashGuard.pollDue(1000L, 2500L), "距上针 1.5s ≥ 1.5s → 到期");
        TestHost.checkEq(1500L, VideoSplashGuard.VIDEO_POLL_MS, "轮询针间隔 1.5s 契约");
        // 20s 观察窗内 1.5s/针 → 12~20s 段（固定梯子 18 档止于 12s 的盲区段）≥5 针兜底
        int polls = 0;
        for (long t = 12000; t < 20000; t += VideoSplashGuard.VIDEO_POLL_MS) polls++;
        TestHost.check(polls >= 5, "12~20s 段轮询 ≥5 针（固定梯子盲区兜底——SurfaceView 零事件）");

        /* ════ S4 白点节流 + 预算（8 项——死因③根治）════ */
        TestHost.suite("VideoSplash · S4 白点节流（预算烧穿根治）");
        TestHost.check(VideoSplashGuard.strikeGapOk(null, 1000), "无上击记录 → 放行（首击）");
        TestHost.check(!VideoSplashGuard.strikeGapOk(1000L, 2000L), "距上击 1s < 1.5s → 拒（3 击 1s 烧穿 → 拉长到 ≥1.5s/击）");
        TestHost.check(VideoSplashGuard.strikeGapOk(1000L, 2500L), "距上击 1.5s ≥ 1.5s → 放行");
        TestHost.checkEq(5, VideoSplashGuard.maxClicksFor(true), "视频窗点击预算 5（3→5 覆盖门控期）");
        TestHost.checkEq(3, VideoSplashGuard.maxClicksFor(false), "普通窗预算 3 保持（原 MAX_CLICKS_PER_WIN 契约）");
        TestHost.check(VideoSplashGuard.VIDEO_STRIKE_GAP_MS * VideoSplashGuard.maxClicksFor(true) >= 7000,
                "视频窗 5 击 × 1.5s ≥ 7.5s（门控期预算覆盖——旧 3 击 1s 烧穿死因③）");
        TestHost.check(VideoSplashGuard.budgetRefresh(true, true, true),
                "视频窗 + 新指纹目标 + 文本键 → 预算刷新（视频末露头真按钮 ≠ 白点）");
        TestHost.check(!VideoSplashGuard.budgetRefresh(true, true, false),
                "角落/手势候选（无文本——移动目标换指纹不换按钮）→ 永不刷新（防无限预算）");

        /* ════ S5 L4 形态门放宽（5 项——死因④根治）════ */
        TestHost.suite("VideoSplash · S5 L4 形态门放宽（videoLike 入强广告形态位）");
        // 200 节点树（WebView 视频容器常态 >150）+ 右上角图片跳过键 + 视频画像 → 8s 出手
        SimNode root = new SimNode();
        root.boundsArr = new int[]{0, 0, 1080, 2400};
        root.cls = "android.widget.FrameLayout";
        SimNode corner = new SimNode("", 980, 60, 1040, 120); // 右上角无字小可点控件
        corner.clickable = true;
        root.add(corner);
        for (int i = 0; i < 198; i++) { // 200 节点树（>150 旧 CORNER_TREE_MAX_NODES）
            SimNode filler = new SimNode("", 0, 400 + (i % 40) * 40, 1080, 440 + (i % 40) * 40);
            filler.clickable = false;
            root.add(filler);
        }
        Ctx ctx = new Ctx();
        ctx.screenW = 1080;
        ctx.screenH = 2400;
        ctx.pkg = "com.example.video.app";
        ctx.elapsedMs = 8000;          // 8s：> CORNER_MAX_MS(6500)——视频窗内仍出手
        ctx.videoLike = true;           // 视频画像在岗（零候选连环 + 树静默）
        ctx.splashLive = true;
        ctx.budgetNodes = 600;
        ScanFind f = AdDecisionCore.walk(root, ctx);
        TestHost.check(!f.corners.isEmpty(), "200 节点树角落候选收集（收集面不受视频树规模影响）");
        Decision d = AdDecisionCore.decide(f, ctx);
        TestHost.check("corner_image_skip".equals(d.reason),
                "videoLike + 200 节点树 + 8s 角落键 → 出手（旧 L4：树 >150 拒 + 时机 >6.5s 拒——双重死因④）");
        // 对照：无视频画像同窗同树 → 旧契约拒（树 150 / 时机 6.5s——误触红线不破）
        Ctx ctxPlain = new Ctx();
        ctxPlain.screenW = 1080;
        ctxPlain.screenH = 2400;
        ctxPlain.pkg = "com.example.video.app";
        ctxPlain.elapsedMs = 8000;
        ctxPlain.splashLive = true;
        ctxPlain.budgetNodes = 600;
        ScanFind fPlain = AdDecisionCore.walk(root, ctxPlain);
        Decision dPlain = AdDecisionCore.decide(fPlain, ctxPlain);
        TestHost.check(!"corner_image_skip".equals(dPlain.reason),
                "对照：无画像同窗同树 → 旧契约拒（树 >150 且时机 >6.5s——普通窗误触红线不破）");
        TestHost.checkEq(260, VideoSplashGuard.VIDEO_CORNER_TREE_MAX, "视频窗树上限 260（WebView 容器 >150 常态）");

        /* ════ S6 视频开屏键词典（7 项）════ */
        TestHost.suite("VideoSplash · S6 视频开屏键词典（无「后」变体收编 + 整词键回归）");
        Ctx rctx = new Ctx();
        rctx.screenW = 1080;
        rctx.screenH = 2400;
        rctx.pkg = "com.example.video.app";
        rctx.budgetNodes = 600;
        SimNode probe = new SimNode("", 900, 80, 1030, 160);
        // v7.8.0 P_COUNTDOWN_HEAD「后」改可选：「5s 跳过」「5秒跳过」全中（旧正则「后」必填全拒）
        TestHost.check(RuleEngine.matchText("5s 跳过", new int[]{900, 80, 1030, 160}, probe, rctx) != null
                        && AdDecisionCore.parseCountdown("5s 跳过") == 5,
                "「5s 跳过」命中 + 倒计时 5（v7.7 及以前：无「后」正则拒——视频开屏词典缺口）");
        TestHost.check(RuleEngine.matchText("5秒跳过", new int[]{900, 80, 1030, 160}, probe, rctx) != null
                        && AdDecisionCore.parseCountdown("5秒跳过") == 5,
                "「5秒跳过」命中 + 倒计时 5（无「后」变体）");
        TestHost.check(RuleEngine.matchText("3s后可跳过", new int[]{900, 80, 1030, 160}, probe, rctx) != null,
                "「3s后可跳过」命中（有「后」原形态保持——回归）");
        TestHost.check(RuleEngine.matchText("跳过广告 5", new int[]{900, 80, 1030, 160}, probe, rctx) != null,
                "「跳过广告 5」尾部形态保持（回归）");
        // 视频整词键（v5.7.0 收录——视频开屏常态回归）
        TestHost.check(RuleEngine.matchText("跳过视频", new int[]{900, 80, 1030, 160}, probe, rctx) != null,
                "「跳过视频」整词键（视频开屏/前贴常态）");
        TestHost.check(RuleEngine.matchText("跳过片头", new int[]{900, 80, 1030, 160}, probe, rctx) != null,
                "「跳过片头」整词键保持");
        // 营销话术对照（倒计时形态数学上无法混入营销词）
        TestHost.check(RuleEngine.matchText("立即下载 5 折起", new int[]{100, 1800, 900, 1900}, probe, rctx) == null,
                "「立即下载 5 折起」营销话术拒（诱导词黑名单 + 正则形态）");

        /* ════ S7 时间线仿真（6 项——第一方视频开屏 0~20s 全链路三死因对照）════ */
        TestHost.suite("VideoSplash · S7 第一方视频开屏时间线仿真（0~20s）");
        boolean profileAt26 = false, pollAt15 = false, budgetOk = false;
        for (long t = 0; t <= 20000; t += 500) {
            int zeroCand = (t >= 1500 && t < 14000) ? 3 : 1; // 视频播放期零候选连环
            boolean vLike = VideoSplashGuard.videoLike(t, zeroCand, t >= 2000 ? t : 1500, false);
            if (t == 2500 && vLike) profileAt26 = true; // 2.5s 首个画像成立时刻（≥2.2s 门槛后）
            if (t == 15000) {
                pollAt15 = VideoSplashGuard.pollDue(13500L, 15000L);       // 15s 轮询针到期
                budgetOk = VideoSplashGuard.budgetRefresh(true, true, true); // 探针露头 + 文本新键
            }
        }
        TestHost.check(profileAt26, "2.5s 画像成立（零候选×3 + 静默——旧版此时退避杀链，画像接管）");
        TestHost.check(pollAt15, "15s 轮询针到期（12~20s 段兜底——固定梯子 18 档止于 12s）");
        TestHost.check(budgetOk, "15s 文本新键露头 → 预算刷新出手（gaveUp 复活——死因③对照）");
        TestHost.check(15000 > 12000, "对照·旧版死因①：15s > 12s 出窗（视频末按钮无人扫）");
        TestHost.check(!VideoSplashGuard.strikeGapOk(0L, 500L), "对照·旧版死因③：3 击 500ms 连发烧穿预算");
        TestHost.checkEq(3, VideoSplashGuard.VIDEO_ZERO_CAND_STREAK, "零候选连环门槛 3（与退避同源）");

        /* ════ S8 常量契约（10 项）════ */
        TestHost.suite("VideoSplash · S8 常量契约（v7.8.0 视频开屏卫士参数面）");
        TestHost.checkEq(20000L, VideoSplashGuard.VIDEO_EXT_WINDOW_MS, "观察窗延长 20s");
        TestHost.checkEq(2200L, VideoSplashGuard.VIDEO_PROFILE_MIN_MS, "画像最早评估 2.2s");
        TestHost.checkEq(1600L, VideoSplashGuard.VIDEO_SILENCE_MIN_MS, "树静默阈值 1.6s");
        TestHost.checkEq(3200L, VideoSplashGuard.VIDEO_SDK_MIN_MS, "SDK 档画像门槛 3.2s");
        TestHost.checkEq(1500L, VideoSplashGuard.VIDEO_POLL_MS, "轮询针 1.5s");
        TestHost.checkEq(1500L, VideoSplashGuard.VIDEO_STRIKE_GAP_MS, "白点节流 1.5s/击");
        TestHost.checkEq(5, VideoSplashGuard.VIDEO_MAX_CLICKS, "视频窗预算 5");
        TestHost.checkEq(260, VideoSplashGuard.VIDEO_CORNER_TREE_MAX, "视频窗树上限 260");
        TestHost.checkEq(3, VideoSplashGuard.VIDEO_ZERO_CAND_STREAK, "零候选连环 3");
        TestHost.checkEq(12000L, VideoSplashGuard.windowDeadlineMs(false), "普通窗 12s 保持");

        /* ════ S9 v7.9.0 画像竞速根治（8 项——树慢速构建窗的退避竞速死因）════ */
        TestHost.suite("VideoSplash · S9 画像竞速根治（arm-hint——树慢速构建窗）");
        // 死因复现：WebView/H5 视频容器树慢速构建（内容事件持续到 ~2s）——
        // streak≥3 + 窗龄≥2.6s 的退避杀链早于静默≥1.6s 的全画像成立时刻
        // （内容事件停在 2s 时，静默要到 3.6s 才满 1.6s——链已在 2.6s 被杀）。
        // arm-hint（streak≥3 + 窗龄≥2.2s + 静默≥0.8s）窗免杀链：链活到
        // 3.6s 全画像成立（videoLike 接管观察义务——轮询针在岗）。
        TestHost.check(VideoSplashGuard.videoArmHint(2600, 3, 800),
                "arm-hint 成立（streak 3 + 窗龄 2.6s + 静默 0.8s——退避正要杀链的竞速时刻）");
        TestHost.check(VideoSplashGuard.videoArmHint(2600, 3, 1200),
                "arm-hint 成立（静默 1.2s——距全画像 1.6s 还差 0.4s）");
        TestHost.check(VideoSplashGuard.videoArmHint(2600, 3, 1700),
                "arm-hint 成立（静默 1.7s——全画像同步成立，豁免与 videoLike 重叠无空档）");
        TestHost.check(!VideoSplashGuard.videoArmHint(2600, 3, 500),
                "arm-hint 拒（静默 0.5s < 0.8s——树持续有事件的窗不豁免，退避照杀功耗红线）");
        TestHost.check(!VideoSplashGuard.videoArmHint(2600, 2, 2000),
                "arm-hint 拒（streak 2 < 3——画像预备门槛与全画像同源）");
        TestHost.check(!VideoSplashGuard.videoArmHint(2000, 3, 2000),
                "arm-hint 拒（窗龄 2.0s < 2.2s——按钮常态 0~2s 渲染期内不预备）");
        TestHost.check(VideoSplashGuard.videoArmHint(3000, 4, -1),
                "arm-hint 成立（静默 -1 = 无内容事件记录——SurfaceView 纯静默窗，画像预备放行）");
        // 竞速时间线仿真：树慢速构建窗（内容事件停在 1.8s——arm-hint 介入边界）——
        // 旧版 2.6s 杀链（streak≥3 + 窗龄≥2.6s，无静默豁免）→ 全画像只能靠
        // 固定档自愈（下一档 3220 时静默 1420 <1.6s 仍死；4000 档才活）；
        // v7.9.0 arm-hint 在 2600 竞速时刻（静默 800≥0.8s）挡住杀链 → 链
        // 连续活到 3.4s 全画像成立（videoLike 接管——轮询针在岗零空档）。
        boolean chainAlive = true, oldChainAlive = true, profileAt34 = false;
        long oldFirstProfile = -1;
        for (long t = 2200; t <= 4000; t += 200) {
            int streak = 3; // 梯子密档 + nudge 早已攒满
            long silence = t - 1800; // 内容事件停在 1.8s（树慢速构建）
            // v7.9.0：arm-hint 挡退避杀链（静默 ≥0.8s 的窗不杀）
            boolean kill79 = streak >= 3 && t >= 2600 && !VideoSplashGuard.videoArmHint(t, streak, silence);
            if (kill79) chainAlive = false;
            // v7.8.0 对照：无 arm-hint——streak≥3 + 窗龄≥2.6s 即杀
            boolean kill78 = streak >= 3 && t >= 2600;
            if (kill78) oldChainAlive = false;
            if (VideoSplashGuard.videoLike(t, streak, silence, false)) {
                if (t == 3400) profileAt34 = true;
                if (oldFirstProfile < 0) oldFirstProfile = t;
            }
        }
        TestHost.check(chainAlive, "arm-hint 挡住退避杀链（2.6s 竞速时刻静默 800ms≥0.8s——链连续活）");
        TestHost.check(!oldChainAlive, "对照·v7.8.0 同窗 2.6s 杀链（无静默豁免——链死等固定档自愈）");
        TestHost.check(profileAt34, "3.4s 全画像成立（arm-hint 守链——静默涨满 1.6s 即接管，无空档）");
        TestHost.check(oldFirstProfile >= 3400, "全画像最早 3.4s（静默 1.6s 于 3.4s 涨满——边界精确）");

        /* ════ S10 v7.9.0 轮询针探针词 + 全量复扫（10 项——图片钮/图标钮路径）════ */
        TestHost.suite("VideoSplash · S10 轮询针探针词 10 词 + 每 3 针全量复扫");
        // 用户实锤「广告按钮不是普通 TextView / 关闭按钮只有图标没有文字」：
        // 旧探针 5 词（跳过/跳過/skip/关闭广告/關閉廣告）对图内「×」文本钮、
        // 拉丁 close 钮零命中 → 探针不中 → 全量 tryScan 不拉起 → 12~20s 段漏拦。
        String[] words = VideoSplashGuard.VIDEO_POLL_PROBE_WORDS;
        boolean hasX = false, hasClose = false, hasLatin = false, hasLegacy = false, hasNoBareX = true;
        for (String w : words) {
            if (w.equals("×") || w.equals("✕") || w.equals("✖")) hasX = true;
            if (w.equals("关闭")) hasClose = true;
            if (w.equals("close")) hasLatin = true;
            if (w.equals("跳过") || w.equals("skip")) hasLegacy = true;
            if (w.equals("X") || w.equals("x")) hasNoBareX = false; // 裸拉丁 X contains 噪声面
        }
        TestHost.check(words.length >= 10, "探针词 ≥10（旧 5 词——×/关闭/close/全角家族全收）");
        TestHost.check(hasX, "图内「×/✕/✖」全角家族在场（图片钮最常见可探文本）");
        TestHost.check(hasClose, "「关闭」短词在场（旧版只有「关闭广告」整词）");
        TestHost.check(hasLatin, "拉丁「close」在场（国际 App 视频开屏）");
        TestHost.check(hasLegacy, "旧 5 词保持（跳过/跳過/skip/关闭广告/關閉廣告——回归）");
        TestHost.check(hasNoBareX, "裸「X/x」不收（contains 搜索英文任意含 x 词全命中——噪声面交给全量闸）");
        // 每 3 针一次全量复扫（pollWalkDue——12~20s 段纯图片钮零文字零探针命中，
        // walk 角落候选路径是唯一活路）
        TestHost.check(!VideoSplashGuard.pollWalkDue(0), "第 0 针不全量（首针探针优先——零 DFS 红线）");
        TestHost.check(VideoSplashGuard.pollWalkDue(3), "第 3 针全量复扫（图片钮兜底周期）");
        TestHost.check(!VideoSplashGuard.pollWalkDue(4), "第 4 针不全量（周期内——探针直查）");
        TestHost.check(VideoSplashGuard.pollWalkDue(6), "第 6 针全量复扫（3 针周期稳定）");
        // 功耗契约：全量复扫只在 videoLike 窗（20s 观察窗内 ≤6 次全量；普通窗零轮询零全量）
        int fullScans = 0;
        for (int i = 0; i <= 12; i++) if (VideoSplashGuard.pollWalkDue(i)) fullScans++;
        TestHost.check(fullScans <= 5 && fullScans >= 3, "20s 观察窗内全量复扫 ≤5 次（1.5s×13 针中每 3 针一次——功耗红线）");
        TestHost.checkEq(3, VideoSplashGuard.VIDEO_POLL_WALK_EVERY, "全量复扫周期 3 针（常量契约）");
        TestHost.checkEq(800L, VideoSplashGuard.VIDEO_ARM_SILENCE_MIN_MS, "画像预备静默半阈值 0.8s（常量契约）");

        /* ════ S9 自适应轮询节拍（v8.0.1 补测 · 18 项）════ */
        TestHost.suite("VideoSplash · S9 自适应轮询节拍（v7.9.5 三档真值表——v8.0.1 补测钉死边界）");
        // ── 三档裁决（adaptivePollGapMs）──
        TestHost.checkEq(1500L, VideoSplashGuard.adaptivePollGapMs(false, 0),
                "标准档：非终态 + 零 miss → 1.5s（证据在场/画像刚成立——候选一露头即有安全判断）");
        TestHost.checkEq(1500L, VideoSplashGuard.adaptivePollGapMs(false, 1),
                "标准档：miss ×1 → 1.5s（未达降档门槛）");
        TestHost.checkEq(1500L, VideoSplashGuard.adaptivePollGapMs(false, 3),
                "标准档边界：miss ×3 = 门槛-1 → 仍 1.5s（降档前最后一针）");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(false, 4),
                "低证据降档：miss ×4 = VIDEO_LOW_EVIDENCE_MISS_STREAK → 3s（≈6s 无证据增量——尾段半频）");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(false, 9),
                "低证据保持：miss ×9 → 3s（降档后不回升——除非证据增强重置）");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(true, 0),
                "终态降档：任务已终态 + 零 miss → 3s（广告已关后只观察不出手）");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(true, 9),
                "终态 + 低证据叠加 → 3s（两档同值——终态优先不冲突）");
        TestHost.checkEq(1500L, VideoSplashGuard.adaptivePollGapMs(false, 0),
                "证据恢复升档：miss 重置为 0 → 回 1.5s（探针命中/出手后 streak 清零——与 miss×0 同入口）");
        TestHost.checkEq(4, VideoSplashGuard.VIDEO_LOW_EVIDENCE_MISS_STREAK,
                "低证据门槛常量 =4（≈6s 无增量才降档——不过早降频）");
        // ── 4 参 pollDue 真值表（自适应版到期判定）──
        TestHost.check(VideoSplashGuard.pollDue(null, 1000L, false, 0),
                "4 参首针：无记录 → 立即到期");
        TestHost.check(!VideoSplashGuard.pollDue(1000L, 2400L, false, 3),
                "标准档未到期：距上针 1.4s < 1.5s（streak 3 未降档）");
        TestHost.check(VideoSplashGuard.pollDue(1000L, 2500L, false, 3),
                "标准档到期边界：距上针 1.5s ≥ 1.5s（streak 3 仍标准档）");
        TestHost.check(!VideoSplashGuard.pollDue(1000L, 2500L, false, 4),
                "低证据档未到期：距上针 1.5s < 3s（streak 4 已降档——同一时刻不同档不同结论）");
        TestHost.check(VideoSplashGuard.pollDue(1000L, 4000L, false, 4),
                "低证据档到期边界：距上针 3s ≥ 3s");
        TestHost.check(!VideoSplashGuard.pollDue(1000L, 2500L, true, 0),
                "终态档未到期：距上针 1.5s < 3s（streak 0 但终态优先）");
        TestHost.check(VideoSplashGuard.pollDue(1000L, 4000L, true, 0),
                "终态档到期边界：距上针 3s ≥ 3s");
        // ── walkDue × miss-streak 交互契约（P3-2 videoPoll 重构前置钉子）──
        // walkDue 针（pollIdx%3==0）自身不重置 streak：连续全 miss 时间线下，
        // 第 3 针（首个 walkDue）后 streak=3 仍 1.5s，第 4 针后 streak=4 降 3s——
        // 降档时序与 walkDue 周期（3 针）在同一量级但互不耦合（纯函数各自裁决）。
        TestHost.checkEq(1500L, VideoSplashGuard.adaptivePollGapMs(false, 3),
                "交互契约：首个 walkDue 针（miss=3）后仍 1.5s——walkDue 不加速降档");
        TestHost.checkEq(3000L, VideoSplashGuard.adaptivePollGapMs(false, 4),
                "交互契约：降档发生在 miss=4（与 walkDue 周期无关——重构不得改变此边界）");

        TestHost.done();
    }
}
