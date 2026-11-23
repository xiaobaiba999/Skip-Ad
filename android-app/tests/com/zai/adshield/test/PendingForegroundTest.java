package com.zai.adshield.test;

import com.zai.adshield.core.ReentryGuard;

/**
 * v7.12.0 前台竞速晋升回归套件（回切拦截修复·死因③）。
 * ─────────────────────────────────────────────────────────
 * 死因（用户实锤「切到另一个 app 十几分钟后回来，app 跳出的广告拦截不了
 * · 息屏十几分钟再打开也拦不住」之一）：新前台应用的 TYPE_WINDOW_STATE_
 * CHANGED 与 isActiveWindowOf 竞速——活动窗口尚未切换完成时窗口事件被
 * 整体丢弃且不登记 lastFgPkg → 该应用后续全部内容事件被前台门丢弃 →
 * 回切后的开屏广告整窗漏拦。
 * 对策：丢弃分支登记竞速候选（fgPendingPkg/At/Cls，服务侧）；后续内容
 * 事件经本判定（纯函数）+ 活动窗口复核后按新进入晋升补扫。
 * 覆盖：
 *  S1 窗口期内 + 包一致 → 可晋升；
 *  S2 过期（>3s）→ 不晋升（竞速只发生在切换瞬间）；
 *  S3 包不一致（他包事件）→ 不晋升；
 *  S4 未登记/异常输入（null/空包）→ 不晋升（null 安全）;
 *  S5 边界：恰好 3s（含）→ 可晋升；3s+1ms → 不晋升；
 *  S6 与既有 isEntering 语义共存（互不侵蚀——回切 6s 闸不动）。
 */
public class PendingForegroundTest {

    public static void main(String[] args) {
        TestHost.suite("PendingForegroundTest");

        long now = 1_700_000_000_000L;
        long at = now - 500L;                       // 500ms 前登记的竞速候选
        long win = 3000L;                           // FG_PENDING_WINDOW_MS

        System.out.println("═══ S1 窗口期内 + 包一致 → 可晋升 ═══");
        TestHost.check(ReentryGuard.shouldPromotePending("com.target", at, "com.target", now, win),
                "S1a 竞速候选 500ms 内同包内容事件 → 可晋升");
        TestHost.check(ReentryGuard.shouldPromotePending("com.target", now, "com.target", now, win),
                "S1b 零延迟（同刻内容事件）→ 可晋升（切换动画期的最快形态）");

        System.out.println("═══ S2 过期 → 不晋升 ═══");
        TestHost.check(!ReentryGuard.shouldPromotePending("com.target", at, "com.target", at + 3001L, win),
                "S2a 3s+1ms → 候选作废（竞速只发生在切换瞬间）");
        TestHost.check(!ReentryGuard.shouldPromotePending("com.target", at, "com.target", at + 10_000L, win),
                "S2b 10s（用户实锤的十几分钟场景之外的普通过期）→ 不晋升");

        System.out.println("═══ S3 包不一致 → 不晋升 ═══");
        TestHost.check(!ReentryGuard.shouldPromotePending("com.target", at, "com.other", now, win),
                "S3a 他包内容事件 → 不晋升（候选只认本包）");
        TestHost.check(!ReentryGuard.shouldPromotePending("com.a", at, "com.b", now, win),
                "S3b 候选 A 事件 B → 不晋升（绝不张冠李戴）");

        System.out.println("═══ S4 未登记/异常输入 → null 安全 ═══");
        TestHost.check(!ReentryGuard.shouldPromotePending(null, at, "com.target", now, win),
                "S4a 未登记候选（null）→ 不晋升");
        TestHost.check(!ReentryGuard.shouldPromotePending("com.target", at, null, now, win),
                "S4b 事件包 null → 不晋升");
        TestHost.check(!ReentryGuard.shouldPromotePending("com.target", at, "", now, win),
                "S4c 事件包空串 → 不晋升");
        TestHost.check(!ReentryGuard.shouldPromotePending("com.target", 0L, "com.target", now, win),
                "S4d 登记时刻 0（未登记语义）→ 不晋升");
        TestHost.check(!ReentryGuard.shouldPromotePending(null, 0L, null, now, win),
                "S4e 全空输入 → 不晋升");

        System.out.println("═══ S5 边界：恰好窗口期 ═══");
        TestHost.check(ReentryGuard.shouldPromotePending("com.target", at, "com.target", at + 3000L, win),
                "S5a 恰好 3s（含端点）→ 可晋升（切窗动画最长形态兜得住）");
        TestHost.check(!ReentryGuard.shouldPromotePending("com.target", at, "com.target", at + 3001L, win),
                "S5b 3s+1ms → 不晋升（窗口右开）");

        System.out.println("═══ S6 与既有回切语义共存（零侵蚀）═══");
        TestHost.check(ReentryGuard.isEntering("com.target", "com.other", 0L, now),
                "S6a isEntering 语义保持（不同包 = 新进入——晋升只兜漏网，不改变原判定）");
        TestHost.check(ReentryGuard.isEntering("com.target", "com.target", now - 7000L, now),
                "S6b 同包离开 6s+ → 回切等价新进入（REENTER_GAP 闸不动）");
        TestHost.check(ReentryGuard.entryFullActive(ReentryGuard.entryUntil(now), now),
                "S6c 进入期全量门保持（晋升后 CLEAN 档案让路 4s）");

        TestHost.done();
    }
}
