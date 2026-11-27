package li.gkd.app.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import li.gkd.app.record.EngineRateGate

/**
 * v8.0.8 订阅补丁层专项（纯函数，无需数据库/应用环境）：
 * 汽水音乐开屏组修复（无锚点分支删除 / id 兜底 / actionMaximum 提升）、
 * 86·233 全局开屏组排除汽水、幂等性、非目标订阅零改动、有界节流闸。
 */
class SubscriptionPatchesTest {
    @Test
    fun lunaSplashGroupPatchRemovesAnchorlessBranchAndAddsIdFallback() {
        val sub667 =
            RawSubscriptionParser.parse(
                """
                {
                  id: 667, name: 'TestLin', version: 1, author: 't',
                  apps: [{
                    id: 'com.luna.music', name: '汽水音乐',
                    groups: [{
                      key: 0, name: '开屏广告', matchTime: 10000,
                      actionMaximum: 1, resetMatch: 'app', actionMaximumKey: 0, priorityTime: 10000,
                      rules: [
                        { key: 0, fastQuery: true, anyMatches: [
                          '@View[text=null][clickable=true][childCount=0][visibleToUser=true][width<200&&height<200] +(1,2) TextView[index=parent.childCount.minus(1)][childCount=0] <n FrameLayout[childCount>2][text=null][desc=null] >(n+6) [text*="第三方应用"||text*="扭动手机"][visibleToUser=true]',
                          'FrameLayout > FrameLayout[childCount>2][text=null][desc=null] > @View[text=null][clickable=true][childCount=0][visibleToUser=true][width<200&&height<200] +(1,2) TextView[index=parent.childCount.minus(1)][childCount=0][visibleToUser=true]'
                        ] },
                        { key: 1, fastQuery: true, matches: ['[text^="跳过"][text.length<10][visibleToUser=true]'] }
                      ]
                    }]
                  }]
                }
                """.trimIndent(),
            )
        val luna = sub667.apps.first { it.id == "com.luna.music" }
        val group = luna.groups.first { it.key == 0 }
        assertEquals(2, group.actionMaximum, "actionMaximum 提升 1→2（一因两果根治：误点耗尽配额→真跳过永不触发）")
        val r0 = group.rules.first { it.key == 0 }
        assertEquals(1, r0.anyMatches?.size, "r0 只保留带文本锚点分支（删除无锚点结构分支）")
        assertTrue(r0.anyMatches!!.first().contains("第三方应用"), "r0 保留锚点分支")
        val r1 = group.rules.first { it.key == 1 }
        assertEquals(null, r1.matches, "r1 matches 转 anyMatches")
        assertEquals(2, r1.anyMatches?.size, "r1 文本+id 兜底两分支")
        assertTrue(
            r1.anyMatches!!.any { it.contains("tt_splash_skip_btn") },
            "r1 增加穿山甲跳过键 id 兜底",
        )

        // 幂等：补丁后的序列化结果再进解析器，行为不变
        val reparsed = RawSubscriptionParser.parse(SubscriptionJson.toJson5String(sub667))
        val regroup = reparsed.apps.first { it.id == "com.luna.music" }.groups.first { it.key == 0 }
        assertEquals(2, regroup.actionMaximum)
        assertEquals(1, regroup.rules.first { it.key == 0 }.anyMatches?.size)
        assertEquals(2, regroup.rules.first { it.key == 1 }.anyMatches?.size)
    }

    @Test
    fun globalSplashGroupsExcludeLunaForAoguaiAndGanlin() {
        for (subsId in listOf(86L, 233L)) {
            val sub =
                RawSubscriptionParser.parse(
                    """
                    {
                      id: $subsId, name: 'Global', version: 1,
                      globalGroups: [{
                        key: 0, name: '开屏广告', order: -10, matchRoot: true,
                        actionMaximum: 2, matchTime: 9000,
                        rules: [{ matches: '[text*="跳过"][text.length<10]' }],
                        apps: [{ id: 'com.tencent.mm', enable: false }]
                      }]
                    }
                    """.trimIndent(),
                )
            val global = sub.globalGroups.first { it.key == 0 }
            val excluded = global.apps.orEmpty().firstOrNull { it.id == "com.luna.music" }
            assertNotNull(excluded, "sub $subsId 全局开屏组排除汽水")
            assertEquals(false, excluded.enable, "排除项为禁用")
            val reSub = RawSubscriptionParser.parse(SubscriptionJson.toJson5String(sub))
            assertEquals(
                1,
                reSub.globalGroups.first().apps.orEmpty().count { it.id == "com.luna.music" },
                "sub $subsId 幂等（不重复追加）",
            )
        }
    }

    @Test
    fun otherSubscriptionsPassThroughUnchanged() {
        val untouched =
            RawSubscriptionParser.parse(
                "{ id: 999, name: 'Other', version: 1, apps: [{ id: 'com.luna.music', name: '汽水音乐', groups: [{ key: 0, name: '开屏广告', actionMaximum: 1, rules: [{ key: 0, matches: '[text=\"x\"]' }] }] }] }",
            )
        assertEquals(1, untouched.apps.first().groups.first().actionMaximum, "非目标订阅零改动")
        assertEquals(null, untouched.apps.first().groups.first().rules.first().anyMatches)
    }

    @Test
    fun rateGateThrottlesPerKeyWithinInterval() {
        val key = "gate-${System.nanoTime()}"
        assertTrue(EngineRateGate.tryAcquire(key, 60_000), "首次放行")
        assertFalse(EngineRateGate.tryAcquire(key, 60_000), "窗口内节流")
        assertTrue(EngineRateGate.tryAcquire("$key-b", 60_000), "独立 key 不互相影响")
    }
}
