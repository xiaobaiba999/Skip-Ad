package li.gkd.app.subscription

import li.gkd.app.subscription.RawSubscription.RawAppGroup
import li.gkd.app.subscription.RawSubscription.RawGlobalApp
import li.gkd.app.subscription.RawSubscription.RawGlobalGroup

/**
 * v8.0.8：订阅内容补丁层（产品层规则治理，引擎语义零改动）。
 *
 * 为什么需要：订阅源由三家社区各自维护、每日远程更新，直接改种子文件会被
 * 下一次同步覆盖。补丁层挂在 RawSubscriptionParser.parse 统一出口——种子加载、
 * 远程更新、用户导入全部经过同一入口，补丁因此跨更新持久生效，且幂等。
 *
 * 补丁 1（订阅 667 · 汽水音乐 · 开屏广告组）：
 * · r0 第二分支是「无文本锚点」的结构匹配（任意小可点击 View），在普通开屏里
 *   会命中广告创意区域（穿山甲互动广告），配合全组共享 actionMaximum:1 直接
 *   耗尽配额 → 真「跳过」文本规则 r1 永不触发（= 无法跳过 + 误点广告区域，
 *   跳转美团投放的落地页）——一因两果，对应本轮用户反馈。
 *   处理：删除 r0 无锚点分支（保留带「第三方应用/扭动手机」文本锚点的分支），
 *   actionMaximum 1→2（即使第一击未生效仍留给 r1 一次机会）。
 * · r1 只匹配 text^="跳过"——穿山甲新形态跳过键常为纯 id 按钮（tt_splash_skip_btn），
 *   无文案时整组无匹配（静默失败）。处理：matches 转 anyMatches，追加该 id 兜底。
 *
 * 补丁 2/3（订阅 86 / 233 · 全局开屏组排除汽水音乐）：
 * 两家全局组对汽水默认生效且 actionMaximum:2，其 text*="跳过" 家族可命中
 * 「跳过领券」类广告文案节点造成二次误触。667 作者已在自家全局组显式禁用
 * 汽水（存在专用应用组）；对齐该意图，跨源同样禁用。
 */
object SubscriptionPatches {
    private const val LUNA_PKG = "com.luna.music"
    private const val PANGLE_SKIP_ID = "tt_splash_skip_btn"

    fun applyTo(subscription: RawSubscription): RawSubscription =
        when (subscription.id) {
            667L -> patch667(subscription)
            86L -> patchGlobalSplashExclusion(subscription)
            233L -> patchGlobalSplashExclusion(subscription)
            else -> subscription
        }

    /** 补丁 1：订阅 667 汽水音乐开屏组（id 锚定 + 组名「开屏广告」+ key=0 双重校验）。 */
    private fun patch667(subscription: RawSubscription): RawSubscription {
        val apps =
            subscription.apps.map { app ->
                if (app.id != LUNA_PKG) {
                    app
                } else {
                    app.copy(
                        groups =
                            app.groups.map { group ->
                                if (group.key == 0 && group.name.startsWith("开屏")) {
                                    patchLunaSplashGroup(group)
                                } else {
                                    group
                                }
                            },
                    )
                }
            }
        if (apps == subscription.apps) return subscription
        return subscription.copy(apps = apps)
    }

    private fun patchLunaSplashGroup(group: RawAppGroup): RawAppGroup {
        var changed = false
        val rules =
            group.rules.map { rule ->
                when (rule.key) {
                    0 -> {
                        // r0：只保留带广告文案锚点的分支（删除无锚点结构分支）
                        val anchored =
                            rule.anyMatches?.filter { it.contains("第三方应用") || it.contains("扭动手机") }
                        if (anchored != null && anchored.isNotEmpty() && anchored.size < (rule.anyMatches?.size ?: 0)) {
                            changed = true
                            rule.copy(anyMatches = anchored)
                        } else {
                            rule
                        }
                    }
                    1 -> {
                        // r1：文本规则 → anyMatches + 穿山甲跳过键 id 兜底
                        val idFallback = "[id$=\"$PANGLE_SKIP_ID\"][visibleToUser=true]"
                        val hasFallback = rule.anyMatches?.any { it.contains(PANGLE_SKIP_ID) } == true
                        val textMatches = rule.matches.orEmpty()
                        if (!hasFallback && textMatches.isNotEmpty()) {
                            changed = true
                            rule.copy(
                                matches = null,
                                anyMatches = textMatches + idFallback,
                            )
                        } else {
                            rule
                        }
                    }
                    else -> rule
                }
            }
        // actionMaximum 1→2：r0 互动广告跳过与 r1 文本跳过共享配额，两次内仍可完成跳过
        val actionMaximum = group.actionMaximum ?: 1
        if (actionMaximum < 2) {
            return group.copy(actionMaximum = 2, rules = rules).also { changed = true }
        }
        if (!changed) return group
        return group.copy(rules = rules)
    }

    /** 补丁 2/3：全局开屏组（key=0 且名字含「开屏」）追加汽水音乐禁用项。 */
    private fun patchGlobalSplashExclusion(subscription: RawSubscription): RawSubscription {
        val globalGroups =
            subscription.globalGroups.map { group ->
                if (group.key == 0 && group.name.startsWith("开屏")) {
                    val excluded =
                        group.apps.orEmpty().any { it.id == LUNA_PKG && it.enable == false }
                    if (excluded) {
                        group
                    } else {
                        group.copy(
                            apps =
                                (group.apps ?: emptyList()) +
                                    RawGlobalApp(
                                        id = LUNA_PKG,
                                        enable = false,
                                        activityIds = null,
                                        excludeActivityIds = null,
                                        versionCode = null,
                                        versionName = null,
                                    ),
                        )
                    }
                } else {
                    group
                }
            }
        if (globalGroups == subscription.globalGroups) return subscription
        return subscription.copy(globalGroups = globalGroups)
    }
}
