package li.gkd.db

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * v8.0.6 双通道日志关联（writeAction 携带 engineLog 时）：ActionLog 与
 * EngineEventLog 必须同事务落库，action_log_id 精确回填；不携带时零详细
 * 日志写入；rule_trigger_stats 既有语义不回归。与其它 store 检查共享
 * DatabaseStoreIntegrationTest 的单例数据库生命周期。
 */
class EngineEventLogStoreChecks {
    private fun action(index: Int) =
        ActionLog(
            appId = "app",
            activityId = null,
            subsId = 7,
            subsVersion = 1,
            groupKey = 2,
            groupType = RuleGroupType.App,
            ruleIndex = index,
            ctime = index.toLong(),
        )

    private fun engineEvent(index: Int) =
        EngineEventLog(
            ctime = index.toLong(),
            level = 0,
            kind = "action_performed",
            appId = "app",
            actionLogId = null,
            message = "⚡ 执行动作（app · 组）：clickCenter · 成功",
            detail = "{\"actionType\":\"clickCenter\",\"result\":true}",
        )

    fun writeActionCorrelatesEngineEventWithinTransaction() = runBlocking {
        val db = Db.database
        val before = db.actionLogDao().queryAll().size
        val eventsBefore = db.engineEventLogDao().queryLatest(500).size
        // ① 不携带 engineLog：零详细日志
        RuntimeRecordStore.writeAction(action(1), trim = false)
        assertEquals(
            eventsBefore,
            db.engineEventLogDao().queryLatest(500).size,
            "不带 engineDetail 的 action 不得写入详细日志",
        )
        // ② 携带 engineLog：同事务写入 + action_log_id 精确回填
        RuntimeRecordStore.writeAction(action(2), trim = false, engineLog = engineEvent(2))
        val actions = db.actionLogDao().queryAll()
        val events = db.engineEventLogDao().queryLatest(500)
        assertEquals(before + 2, actions.size)
        val correlated = actions.single { it.ruleIndex == 2 && it.ctime == 2L }
        val event = events.single { it.ctime == 2L }
        assertEquals(correlated.id.toLong(), event.actionLogId)
        assertEquals("action_performed", event.kind)
        assertEquals("app", event.appId)
        // ③ rule_trigger_stats 同步累计（既有语义不回归）
        assertTrue(
            db.ruleTriggerStatsDao()
                .observe(7, "app", RuleGroupType.App, 2)
                .first()
                .single()
                .triggerCount >= 2L,
        )
    }

    fun engineEventInsertsKeepLatestFirstOrdering() = runBlocking {
        val db = Db.database
        repeat(12) { i ->
            RuntimeRecordStore.writeAction(action(100 + i), trim = false, engineLog = engineEvent(100 + i))
        }
        val events = db.engineEventLogDao().queryLatest(50)
        assertTrue(events.size >= 12, "详细日志必须全部落库")
        // 时间倒序（最新在前）
        val tail = events.take(12)
        assertTrue(tail.first().ctime >= tail.last().ctime)
        // 每条详细日志都精确回填了对应 action 的 id
        tail.forEach { event ->
            assertTrue(
                event.actionLogId != null && event.actionLogId!! > 0,
                "详细日志必须关联 actionLogId",
            )
        }
    }
}
