package li.gkd.db

import androidx.room3.withWriteTransaction

object Db {
    private var createDatabase: (() -> AppDb)? = null

    @Volatile private var instance: AppDb? = null

    internal fun initialize(createDatabase: () -> AppDb) {
        this.createDatabase = createDatabase
    }

    internal val database: AppDb
        get() {
            instance?.let { return it }
            return synchronized(this) {
                instance
                    ?: checkNotNull(createDatabase) { "Db is not initialized" }
                        .invoke()
                        .also { instance = it }
            }
        }

    /**
     * v8.0.8：损坏库自愈入口——启动探测发现数据库损坏时，换入重建后的新实例。
     * 与 database getter 同锁，绝不允许两个活跃实例并存。
     */
    internal fun replaceDatabase(newDatabase: AppDb) {
        synchronized(this) {
            instance = newDatabase
        }
    }

    val subsItemDao
        get() = database.subsItemDao()

    val subsAppGroupConfigDao
        get() = database.subsAppGroupConfigDao()

    val subsGlobalGroupConfigDao
        get() = database.subsGlobalGroupConfigDao()

    val snapshotDao
        get() = database.snapshotDao()

    val actionLogDao
        get() = database.actionLogDao()

    val ruleTriggerStatsDao
        get() = database.ruleTriggerStatsDao()

    val subsCategoryConfigDao
        get() = database.subsCategoryConfigDao()

    val activityLogDao
        get() = database.activityLogDao()

    val subsAppConfigDao
        get() = database.subsAppConfigDao()

    val appLastVisitDao
        get() = database.appLastVisitDao()

    val a11yEventLogDao
        get() = database.a11yEventLogDao()

    val engineEventLogDao
        get() = database.engineEventLogDao()

    val matchDiagnosticRecordDao
        get() = database.matchDiagnosticRecordDao()

    fun close() = database.close()

    suspend fun <T> withTransaction(block: suspend () -> T): T = database.withWriteTransaction {
        block()
    }
}
