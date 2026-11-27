package li.gkd.db

import android.content.Context
import android.util.Log
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun Db.initialize(context: Context, databasePath: String) {
    val applicationContext = context.applicationContext
    initialize { buildAppDatabase(applicationContext, databasePath) }
    // v8.0.8：损坏库自愈探针（后台 IO，不阻塞启动主线程）。
    // 此前数据库损坏/迁移失败的表现是「App 看似一切正常、就是不跳广告、页面数字全 0」
    // 的静默失效（快照 Failure 被逐层吞掉）。现在启动时以真实查询探测可读性：
    // 失败 → 关闭当前实例 → 损坏文件改名保留（*.corrupt-<ts>，可人工恢复）→ 重建。
    // 重建后内嵌订阅会在下一次订阅初始化时自动重新种子化，无重试循环。
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Db.subsItemDao.queryAll()
        } catch (e: Exception) {
            Log.e("Db", "database unreadable, rebuilding after corruption", e)
            try {
                Db.close()
            } catch (_: Exception) {}
            renameCorruptFiles(databasePath)
            try {
                replaceDatabase(
                    buildAppDatabase(applicationContext, databasePath).also { db ->
                        // 新库立即可读验证（再失败则留给下次启动，不无限重试）
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching { db.subsItemDao().queryAll() }
                        }
                    },
                )
            } catch (e2: Exception) {
                Log.e("Db", "database rebuild failed", e2)
            }
        }
    }
}

private fun buildAppDatabase(context: Context, databasePath: String): AppDb =
    Room.databaseBuilder(context, AppDb::class.java, databasePath)
        .addMigrations(Migration14To15)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** 损坏文件改名保留（不删除——用户本地订阅等数据有机会人工找回）。 */
private fun renameCorruptFiles(databasePath: String) {
    val stamp = System.currentTimeMillis()
    listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
        runCatching {
            val file = File(databasePath + suffix)
            if (file.exists()) {
                file.renameTo(File("$databasePath$suffix.corrupt-$stamp"))
            }
        }
    }
}
