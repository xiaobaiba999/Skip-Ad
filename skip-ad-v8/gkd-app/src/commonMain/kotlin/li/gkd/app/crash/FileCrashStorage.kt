package li.gkd.app.crash

import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json
import li.gkd.app.logging.LogUtils
import li.gkd.app.storage.appStorage

object FileCrashStorage {
    private val directory
        get() = appStorage().crash

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val directories
        get() = listOf(directory, appStorage().crashTemp)

    fun save(record: CrashData) {
        val text = json.encodeToString(record)
        directories.forEach {
            it.mkdirs()
            it.resolve(record.filename).writeText(text)
        }
        trim()
    }

    fun save(record: CrashData, folder: File) {
        folder.mkdirs()
        folder.resolve(record.filename).writeText(json.encodeToString(record))
        folder
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .sortedByDescending { it.name }
            .drop(20)
            .forEach {
                if (!it.delete()) LogUtils.w("删除过期崩溃日志失败: ${it.name}", null)
            }
    }

    fun load(): List<CrashData> = load(directory)

    private fun load(folder: File): List<CrashData> =
        folder
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .mapNotNull { file ->
                try {
                    json.decodeFromString<CrashData>(file.readText()).copy(filename = file.name)
                } catch (e: Exception) {
                    LogUtils.e("解析崩溃日志失败: ${file.name}", e)
                    null
                }
            }
            .sortedByDescending { it.mtime }

    /** Consume startup notices without deleting their archived reports. */
    fun takePending(): List<CrashData> {
        trim()
        val pending = appStorage().crashTemp
        val records = load(pending)
        pending
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .forEach {
                if (!it.delete()) LogUtils.w("删除临时崩溃日志失败: ${it.name}", null)
            }
        val now = System.currentTimeMillis()
        directory
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .forEach { file ->
                if (
                    records.none { it.filename == file.name } &&
                        now - Files.getLastModifiedTime(file.toPath()).toMillis() >
                            30.days.inWholeMilliseconds
                ) {
                    if (!file.delete()) LogUtils.w("删除过期崩溃日志失败: ${file.name}", null)
                }
            }
        return records
    }

    /**
     * v8.0.8：非破坏性启动窥探（不删除 crash/temp pending 文件）。
     * 产品壳主界面不渲染 Compose 崩溃路由，启动即 takePending（读后删）会把
     * 「新崩溃」提醒与 WebView crashDump() 的 pending 数据一并吞掉；
     * 启动展示改用窥探，真正的消费（读后删）留给 H5 上传路径。
     */
    fun peekPending(): List<CrashData> {
        trim()
        return load(appStorage().crashTemp)
    }

    fun delete(record: CrashData): Boolean =
        directories
            .map { folder ->
                val file = folder.resolve(record.filename)
                !file.exists() || file.delete()
            }
            .all { it }

    fun deleteAll(): Boolean =
        directories
            .flatMap { it.listFiles().orEmpty().filter { file -> file.isFile } }
            .map { !it.exists() || it.delete() }
            .all { it }

    fun trim() {
        directories.forEach { folder ->
            folder
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                .sortedByDescending { it.name }
                .drop(20)
                .forEach {
                    if (!it.delete()) LogUtils.w("删除过期崩溃日志失败: ${it.name}", null)
                }
        }
    }
}
