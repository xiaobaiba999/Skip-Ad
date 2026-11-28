package li.gkd.app.record

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.app.launcherAppIdFlow
import li.gkd.app.util.Constants
import li.gkd.db.ActionLog
import li.gkd.db.ActivityLog
import li.gkd.db.AppLastVisit
import li.gkd.db.EngineEventLog
import li.gkd.db.RuntimeRecordStore

/** An application transition captured at the event boundary. */
@Serializable
data class AppVisitInput(
    val oldAppId: String,
    val newAppId: String,
    val time: Long,
)

@Serializable
data class ActionRecordInput(
    val appId: String,
    val activityId: String?,
    val subsId: Long,
    val subsVersion: Int,
    val groupKey: Int,
    val groupType: Int,
    val ruleIndex: Int,
    val ruleKey: Int?,
    val time: Long,
    /** v8.0.6：详细引擎日志（与本次 action 同事务写入，actionLogId 自动关联） */
    val engineDetail: EngineEventDetail? = null,
)

/** v8.0.6：详细引擎日志载体（序列化进 engine_event_log.detail 列） */
@Serializable
data class EngineEventDetail(
    val kind: String,
    val level: Int = 0,
    val message: String,
    val actionType: String? = null,
    val result: Boolean? = null,
    val durationMs: Long? = null,
    val reason: String? = null,
    /** v8.1.0：订阅来源结构化字段（失败行与成功行对齐——不再只埋在 message 文本里） */
    val subsId: Long? = null,
    val subsVersion: Int? = null,
    /**
     * v8.1.1：一次规则触发的唯一关联 ID（匹配→动作→结果确认贯穿）。
     * action_performed / action_failed / splash_dismissed / result_unknown /
     * action_no_effect 行据此精确串联；encodeDefaults=false → 旧行无此字段，
     * 服务端按可选字段解析（新旧格式互兼容）。
     */
    val eventId: String? = null,
)

/**
 * Application-owned record writer. The host supplies the calling coroutine and handles failures.
 * Only partial/failed Activity batches are buffered; visits and actions are never queued for retry.
 */
object RuntimeRecordRepository {
    private const val TRIM_INTERVAL = 100L

    private val writer = Mutex()
    private val activities = mutableListOf<ActivityLog>()
    private var visitCount = 0L
    private var activityCount = 0L
    private var actionCount = 0L

    suspend fun recordVisit(input: AppVisitInput) {
        val currentLauncherAppId = launcherAppIdFlow.value
        fun weighted(appId: String, time: Long) =
            when (appId) {
                META.appId -> time - 120_000
                currentLauncherAppId,
                Constants.systemUiAppId -> time - 60_000
                else -> time
            }

        val records = buildList {
            if (input.oldAppId != input.newAppId) {
                add(AppLastVisit(input.oldAppId, weighted(input.oldAppId, input.time - 1)))
            }
            add(AppLastVisit(input.newAppId, weighted(input.newAppId, input.time)))
        }
        writer.withLock {
            withContext(NonCancellable) {
                RuntimeRecordStore.writeVisits(records, visitCount % TRIM_INTERVAL == 0L)
                visitCount++
            }
        }
    }

    suspend fun recordActivity(appId: String, activityId: String?, time: Long) = writer.withLock {
        activities.add(ActivityLog(ctime = time, appId = appId, activityId = activityId))
        if (activities.size >= 16 || appId == META.appId) flushActivities()
    }

    suspend fun recordAction(input: ActionRecordInput) {
        val record =
            ActionLog(
                appId = input.appId,
                activityId = input.activityId,
                subsId = input.subsId,
                subsVersion = input.subsVersion,
                groupKey = input.groupKey,
                groupType = input.groupType,
                ruleIndex = input.ruleIndex,
                ruleKey = input.ruleKey,
                ctime = input.time,
            )
        val engineLog =
            input.engineDetail?.let { d ->
                EngineEventLog(
                    ctime = input.time,
                    level = d.level,
                    kind = d.kind,
                    appId = input.appId,
                    actionLogId = null, // 由 RuntimeRecordStore 在同事务内回填
                    message = d.message,
                    detail = runCatching { Json.encodeToString(EngineEventDetail.serializer(), d) }.getOrNull(),
                )
            }
        writer.withLock {
            withContext(NonCancellable) {
                RuntimeRecordStore.writeAction(record, (actionCount + 1) % TRIM_INTERVAL == 0L, engineLog)
                actionCount++
            }
        }
    }

    /** Submit the remaining Activity batch. Stop producers before the final flush. */
    suspend fun flush() = writer.withLock { flushActivities() }

    private suspend fun flushActivities() {
        if (activities.isEmpty()) return
        // Finish COMMIT and in-memory acknowledgement together, even if the caller is canceled.
        withContext(NonCancellable) {
            val end = activityCount + activities.size
            val trim =
                activityCount == 0L ||
                    (activityCount - 1) / TRIM_INTERVAL != (end - 1) / TRIM_INTERVAL
            RuntimeRecordStore.writeActivities(activities.toList(), trim)
            activities.clear()
            activityCount = end
        }
    }
}
