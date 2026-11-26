package li.gkd.app.record

import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.appScope
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.data.toAttrInfo
import li.gkd.app.logging.LogUtils
import li.gkd.app.logging.logText
import li.gkd.app.model.ActionResult
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.TopActivity
import li.gkd.app.util.launchLogged

fun addActionLog(
    rule: ResolvedRule,
    topActivity: TopActivity,
    target: AccessibilityNodeInfo,
    actionResult: ActionResult,
) {
    val input =
        ActionRecordInput(
            appId = topActivity.appId,
            activityId = topActivity.activityId,
            subsId = rule.subsItem.id,
            subsVersion = rule.rawSubs.version,
            groupKey = rule.g.group.key,
            groupType = rule.g.group.groupType,
            ruleIndex = rule.index,
            ruleKey = rule.key,
            time = System.currentTimeMillis(),
            // v8.0.6：详细引擎日志（与简洁条目同事务关联，actionLogId 回填）
            engineDetail = buildEngineDetail(rule, topActivity, actionResult),
        )
    appScope.launchLogged { RuntimeRecordRepository.recordAction(input) }
    // Logging must not turn a completed action into a failure.
    try {
        val attributes =
            target.toAttrInfo(0, 0).let {
                it.copy(
                    text = it.text?.let { text -> logText(text) },
                    desc = it.desc?.let { text -> logText(text) },
                )
            }
        LogUtils.i(rule.statusText(), attributes, actionResult)
    } catch (error: Exception) {
        LogUtils.w("Action node log unavailable", error)
    }
}

/** v8.0.6 详细引擎日志：应用名优先（快照缓存），失败原因/动作类型随行 */
private fun buildEngineDetail(
    rule: ResolvedRule,
    topActivity: TopActivity,
    actionResult: ActionResult,
): EngineEventDetail {
    val appName =
        AppInfoRepository.snapshot?.apps?.get(topActivity.appId)?.name
            ?: shortPkg(topActivity.appId)
    val groupName = rule.g.group.name
    val actionType = actionResult.action
    val ok = actionResult.result
    return EngineEventDetail(
        kind = if (ok) "action_performed" else "action_failed",
        level = if (ok) 0 else 2,
        message =
            if (ok) {
                "⚡ 执行动作（$appName · $groupName）：$actionType · 成功"
            } else {
                "❌ 动作失败（$appName · $groupName）：$actionType · ${actionResult.outcomeReason}"
            },
        actionType = actionType,
        result = ok,
        reason = if (ok) null else actionResult.outcomeReason,
    )
}

/** 7.15.14 shortPkg 同语义：包名末段，通用词尾取两段（修「android」歧义） */
private fun shortPkg(pkg: String): String {
    if (!pkg.contains('.')) return pkg
    val parts = pkg.split('.')
    val last = parts.last()
    val generic =
        setOf(
            "android", "app", "main", "ui", "mobile", "phone",
            "client", "core", "sdk", "online", "lite",
        )
    return if (last in generic && parts.size >= 3) parts[parts.size - 2] + "." + last else last
}
