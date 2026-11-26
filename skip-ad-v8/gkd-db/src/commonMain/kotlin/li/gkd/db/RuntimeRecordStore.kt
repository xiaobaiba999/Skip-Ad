package li.gkd.db

import androidx.room3.withWriteTransaction
import li.gkd.db.Db.database

/** Each write includes its retention step in the same database transaction. */
object RuntimeRecordStore {
    suspend fun writeVisits(records: List<AppLastVisit>, trim: Boolean) {
        database.withWriteTransaction {
            database.appLastVisitDao().insert(*records.toTypedArray())
            if (trim) database.appLastVisitDao().deleteKeepLatest()
        }
    }

    suspend fun writeActivities(records: List<ActivityLog>, trim: Boolean) {
        database.withWriteTransaction {
            database.activityLogDao().insert(*records.toTypedArray())
            if (trim) database.activityLogDao().deleteKeepLatest()
        }
    }

    suspend fun writeAction(record: ActionLog, trim: Boolean, engineLog: EngineEventLog? = null) {
        database.withWriteTransaction {
            val ids = database.actionLogDao().insert(record)
            val stats = database.ruleTriggerStatsDao()
            stats.insertIfAbsent(
                RuleTriggerStats(
                    record.subsId,
                    record.groupType,
                    record.groupKey,
                    record.appId,
                    0,
                    record.ctime,
                ),
            )
            stats.increment(
                record.subsId,
                record.groupType,
                record.groupKey,
                record.appId,
                record.ctime,
            )
            // v8.0.6：详细引擎日志与简洁条目同事务关联（actionLogId 回填）
            engineLog?.let { log ->
                database.engineEventLogDao().insert(log.copy(actionLogId = ids.firstOrNull()))
                if (trim) database.engineEventLogDao().deleteKeepLatest()
            }
            if (trim) database.actionLogDao().deleteKeepLatest()
        }
    }
}
