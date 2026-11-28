package li.gkd.app.record

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.json.Json
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
    eventId: String? = null,
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
            // v8.1.1：eventId 随行（同一次触发的结果确认行可精确串联）
            engineDetail = buildEngineDetail(rule, topActivity, actionResult, eventId),
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

/**
 * v8.0.8：动作执行失败记录（修复 v8.0.7 及以前 failed 分支不可达的缺陷——
 * queryRules 仅在 result=true 时回调 completed，失败结果此前完全无日志）。
 * 只写详细通道（无 action_log 行，不污染触发统计语义），带组名/规则名/原因/耗时。
 */
fun logActionFailure(
    rule: ResolvedRule,
    topActivity: TopActivity,
    actionResult: ActionResult,
    eventId: String? = null,
) {
    val detail =
        buildEngineDetail(rule, topActivity, actionResult, eventId)
    EngineEventLogWriter.log(
        kind = detail.kind,
        message = detail.message,
        level = detail.level,
        appId = topActivity.appId,
        detail = runCatching {
            Json.encodeToString(EngineEventDetail.serializer(), detail)
        }.getOrNull(),
    )
}

/** v8.0.6 详细引擎日志：应用名优先（快照缓存），失败原因/动作类型随行；v8.0.8 补规则名与耗时；v8.1.0 补订阅来源与失败细分；v8.1.1 补 eventId */
private fun buildEngineDetail(
    rule: ResolvedRule,
    topActivity: TopActivity,
    actionResult: ActionResult,
    eventId: String? = null,
): EngineEventDetail {
    val appName =
        AppInfoRepository.snapshot?.apps?.get(topActivity.appId)?.name
            ?: shortPkg(topActivity.appId)
    val groupName = rule.g.group.name
    val ruleName = rule.rule.name?.takeIf { it.isNotBlank() }
    val actionType = actionResult.action
    val ok = actionResult.result
    return EngineEventDetail(
        kind = if (ok) "action_performed" else "action_failed",
        level = if (ok) 0 else 2,
        message =
            if (ok) {
                "⚡ 执行动作（$appName · $groupName · ${ruleName ?: "规则"}）：$actionType · 成功"
            } else {
                "❌ 动作失败（$appName · $groupName · ${ruleName ?: "规则"}）：$actionType · ${actionResult.outcomeReason}"
            },
        actionType = actionType,
        result = ok,
        durationMs = actionResult.durationMs,
        reason = if (ok) null else actionResult.outcomeReason,
        // v8.1.0：订阅来源与版本（失败行与成功行对齐，服务端统计可直接按订阅分组）
        subsId = rule.subsItem.id,
        subsVersion = rule.rawSubs.version,
        // v8.1.1：事件关联 ID（可选——旧调用方不传则省略）
        eventId = eventId,
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
