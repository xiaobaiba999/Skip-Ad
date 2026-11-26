package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * 详细引擎日志（v8.0.6 双通道日志体系的“详细”通道）。
 * 简洁通道 = action_log（「应用」触发「规则」）；本表记录引擎全链路技术事件
 * （服务生命周期 / 规则执行与失败原因 / 配置变更 / 订阅状态），通过
 * action_log_id 与简洁条目一一关联（同事务回填）。容量：保留最近 800 条。
 */
@Entity(tableName = "engine_event_log")
data class EngineEventLog(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "ctime") val ctime: Long,
    /** 0=info 1=warn 2=error */
    @ColumnInfo(name = "level") val level: Int = 0,
    /** service_connected / service_disconnected / action_performed / action_failed / subscription_seeded / subscription_reenabled / subscription_state / config_changed */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "app_id") val appId: String? = null,
    @ColumnInfo(name = "action_log_id") val actionLogId: Long? = null,
    @ColumnInfo(name = "message") val message: String,
    /** 紧凑 JSON 附加字段（EngineEventDetail 序列化：actionType/result/reason 等） */
    @ColumnInfo(name = "detail") val detail: String? = null,
) {
    @Dao
    interface EngineEventLogDao {
        @Insert suspend fun insert(vararg objects: EngineEventLog): List<Long>

        @Query("SELECT * FROM engine_event_log ORDER BY id DESC LIMIT :limit")
        suspend fun queryLatest(limit: Int): List<EngineEventLog>

        @Query("SELECT COUNT(*) FROM engine_event_log") fun count(): Flow<Int>

        @Query(
            """
            DELETE FROM engine_event_log
            WHERE id <= (
                    SELECT id FROM engine_event_log ORDER BY id DESC LIMIT 1 OFFSET 800
                )
        """,
        )
        suspend fun deleteKeepLatest(): Int
    }
}
