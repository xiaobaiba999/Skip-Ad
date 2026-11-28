package li.gkd.app.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 下载源返回异常 HTTP 状态（4xx/5xx）——独立类型便于日志摘要（HTTP N）与 UI 分类。 */
class DownloadHttpStatusException(val code: Int) : IOException("HTTP $code（下载源返回异常状态）")

/**
 * v8.1.1 下载失败分类（7.7：不再把所有异常统一显示成「下载失败」）。
 *
 * 沿异常链（含 Ktor/OkHttp 包装层）逐层匹配典型网络故障，给出用户可行动的中文原因；
 * 无匹配时如实给出异常类名。取消（CancellationException）不属于失败，由调用方传播。
 */
object DownloadFailures {
    fun classify(e: Throwable): String {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 6) {
            labelFor(t)?.let { return it }
            t = t.cause
            depth++
        }
        return "网络异常（${e.javaClass.simpleName}）"
    }

    private fun labelFor(t: Throwable): String? {
        val msg = t.message.orEmpty()
        return when {
            t is java.net.UnknownHostException -> "DNS 解析失败（当前网络无法解析下载源域名）"
            t is java.net.ConnectException -> "连接失败（下载源拒绝连接）"
            t is java.net.SocketTimeoutException -> "连接/读取超时（网络链路不通或响应过慢）"
            t is javax.net.ssl.SSLException -> "TLS 安全连接失败"
            t is java.net.SocketException -> "连接被重置（网络链路中断）"
            t is java.io.IOException && msg.contains("unexpected end of stream", ignoreCase = true) ->
                "传输中断（服务器提前断开连接）"
            t is java.io.IOException && msg.contains("crc", ignoreCase = true) -> "传输校验失败（链路数据损坏）"
            else -> null
        }
    }
}

/** 下载源 HEAD 元数据（code/ETag/长度/Range 总长）。 */
class DownloadMeta(
    val code: Int,
    val etag: String?,
    val contentLength: Long,
    val rangeTotal: Long?,
)

/** 一次 HTTP 响应的流式读取器（在 [DownloadSource.get] 作用域内有效）。 */
class ChunkStream internal constructor(
    private val streamer: suspend ((ByteArray, Int) -> Unit) -> Unit,
) {
    /** 逐块读取响应体；网络中断以异常抛出（含服务器提前断开）。 */
    suspend fun forEach(onChunk: (ByteArray, Int) -> Unit) = streamer(onChunk)

    companion object {
        internal fun fromChannel(channel: ByteReadChannel): ChunkStream =
            ChunkStream { onChunk ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n <= 0) break
                    onChunk(buffer, n)
                }
            }
    }
}

/**
 * 可注入下载引擎（生产为 Ktor；测试为脚本化假源——不依赖 MockWebServer，纯 common 可测）。
 */
interface DownloadSource {
    /** GET（可选 Range 起点），把响应元数据 + 流交给 [use]；作用域结束自动关闭连接。 */
    suspend fun <R> get(
        url: String,
        rangeFrom: Long,
        use: suspend (DownloadMeta, ChunkStream) -> R,
    ): R
}

/** 生产引擎：专用下载客户端（无整体超时——大文件传输不由 requestTimeout 掐断）。 */
class KtorDownloadSource(private val client: HttpClient) : DownloadSource {
    override suspend fun <R> get(
        url: String,
        rangeFrom: Long,
        use: suspend (DownloadMeta, ChunkStream) -> R,
    ): R =
        client.prepareGet(url) {
            // 状态码自行解释（4xx/5xx 走失败分类，不抛 Ktor 异常）
            expectSuccess = false
            if (rangeFrom > 0) header(HttpHeaders.Range, "bytes=$rangeFrom-")
            // 防 ISP 透明代理缓存
            header(HttpHeaders.CacheControl, "no-cache")
        }.execute { response ->
            val meta =
                DownloadMeta(
                    code = response.status.value,
                    etag = response.headers[HttpHeaders.ETag],
                    contentLength =
                        response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L,
                    rangeTotal = response.headers[HttpHeaders.ContentRange]?.let(::parseRangeTotal),
                )
            use(meta, ChunkStream.fromChannel(response.bodyAsChannel()))
        }
}

internal fun parseRangeTotal(header: String): Long? =
    header.trim().substringAfterLast('/').toLongOrNull()?.takeIf { it > 0 }

/** 下载速度长期低于阈值的主动中止（「长时间没有进度」的治理）。 */
class DownloadStallException(
    val windowMs: Long,
    val receivedInWindow: Long,
) : IOException("下载速度过慢（${windowMs / 1000}s 内仅 ${receivedInWindow / 1024}KB），已切换下载源") {
    override val message: String
        get() = super.message ?: ""
}

/** 完整性校验失败（大小或 SHA-256 不匹配——该源提供的字节不可信）。 */
class DownloadIntegrityException(reason: String) : IOException(reason)

/** 下载过程对 UI 的富状态快照。 */
class DownloadProgress(
    val received: Long,
    val total: Long,
    val bytesPerSecond: Long,
    val sourceIndex: Int,
    val sourceCount: Int,
    val sourceLabel: String,
    val resuming: Boolean,
    val switchingFrom: String? = null,
    val switchingTo: String? = null,
    val switchingReason: String? = null,
    val verifying: Boolean = false,
)

/**
 * v8.1.1 断点续传 + 多源自动切换下载器（第 7 阶段核心）。
 *
 * v8.1.0 及以前的缺口（中国大陆弱网事故链）：
 * · 无 Range 续传——下载到一半失败后只能从 0 重来；
 * · requestTimeout 15s 整体上限——弱网下大 APK 中途必死；
 * · 无失败分类——Connection reset / DNS / 超时统一显示「下载失败」；
 * · 无速度停滞检测——挂着的慢连接只能等 socket 超时。
 *
 * 本下载器规则（v8.1.1）：
 * 1. 每源下载写入 target.part + .meta（ETag/来源/偏移/指纹）；
 * 2. 续传安全（跨源不拼接）：仅当 206 + ETag 与 .meta 一致才续传；
 *    无 ETag 时仅同源续传；ETag 不一致/指纹不一致/服务端忽略 Range（200）→ 重新完整下载；
 * 3. 失败自动切换下一源（连接失败/超时/停滞/HTTP 异常/完整性失败）；
 * 4. 完整性闸：大小 + SHA-256 全过才原子落位（rename），校验失败删除不可信字节；
 * 5. 取消（CancellationException）：保留 .part 供下次续传（重试从断点继续）；
 * 6. 停滞闸：15s 窗口不足 64KB 主动中止换源（时钟可注入，测试确定性）。
 */
object UpdateDownloader {
    private const val STALL_WINDOW_MS = 15_000L
    private const val STALL_MIN_BYTES = 64L * 1024
    private const val PROGRESS_EMIT_MS = 250L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Serializable
    private data class PartMeta(
        val fingerprint: String,
        val etag: String? = null,
        val sourceUrl: String? = null,
        val offset: Long = 0,
    )

    /**
     * @param fingerprint 版本指纹（versionCode:sha:size）——.part 跨版本复用的防线
     * @return 校验通过并已原子落位的最终文件
     * @throws CancellationException 用户取消（.part 保留供续传）
     * @throws Exception 最后一个源的失败（已分类，[DownloadFailures.classify]）
     */
    suspend fun download(
        source: DownloadSource,
        urls: List<String>,
        target: File,
        fingerprint: String,
        expectedSha256: String?,
        expectedSize: Long,
        progress: (DownloadProgress) -> Unit,
        logFailure: (String, Exception) -> Unit,
        clock: () -> Long = System::currentTimeMillis,
    ): File {
        require(urls.isNotEmpty()) { "no download url provided" }
        val part = File(target.path + ".part")
        val metaFile = File(target.path + ".part.meta")
        var lastError: Exception? = null
        for (index in urls.indices) {
            val url = urls[index]
            try {
                downloadOne(
                    source, url, index, urls.size, part, metaFile,
                    fingerprint, expectedSha256, expectedSize, progress, clock,
                )
                verify(part, expectedSha256, expectedSize, index, urls.size, url, progress)
                // 完整性全过 → 原子落位（同目录 rename；旧档先移除，绝无半档冒充正式文件）
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                metaFile.delete()
                return target
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                logFailure("Update download source failed: $url", e)
                if (index + 1 < urls.size) {
                    // 通知 UI 正在切换源（含分类原因）
                    val next = urls[index + 1]
                    progress(
                        DownloadProgress(
                            received = part.length(),
                            total = expectedSize,
                            bytesPerSecond = 0,
                            sourceIndex = index,
                            sourceCount = urls.size,
                            sourceLabel = label(url),
                            resuming = false,
                            switchingFrom = label(url),
                            switchingTo = label(next),
                            switchingReason = DownloadFailures.classify(e),
                        ),
                    )
                }
            }
        }
        throw lastError ?: IllegalStateException("no download source available")
    }

    private suspend fun downloadOne(
        source: DownloadSource,
        url: String,
        sourceIndex: Int,
        sourceCount: Int,
        part: File,
        metaFile: File,
        fingerprint: String,
        expectedSha256: String?,
        expectedSize: Long,
        progress: (DownloadProgress) -> Unit,
        clock: () -> Long,
    ) {
        var meta: PartMeta =
            readMeta(metaFile, fingerprint)
                ?: run {
                    part.delete()
                    metaFile.delete()
                    PartMeta(fingerprint = fingerprint)
                }
        // 每源至多一次「续传失效 → 重新完整下载」重试（防循环）
        var restarted = false
        while (true) {
            // 信任前沿 = .meta 记录的字节数；文件超出前沿部分不可证（进程死亡残留）→ 截断
            val offset =
                if (part.exists()) minOf(part.length(), meta.offset) else 0L
            if (part.exists() && part.length() != offset) {
                RandomAccessFile(part, "rw").use { it.setLength(offset) }
            }
            if (meta.offset != offset) {
                meta = meta.copy(offset = offset)
                writeMeta(metaFile, meta)
            }
            var responseEtag: String? = null
            try {
                source.get(url, offset) { m, stream ->
                    responseEtag = m.etag
                    when {
                        offset > 0 && m.code == 206 -> {
                            // 续传有效性三闸：指纹（已验）+ ETag（跨源一致性证据）+ 总长
                            val etagOk = meta.etag == null || m.etag == null || meta.etag == m.etag
                            val sourceOk = m.etag != null || meta.sourceUrl == url
                            val sizeOk =
                                m.rangeTotal == null || expectedSize <= 0 || m.rangeTotal == expectedSize
                            if (!etagOk || !sourceOk || !sizeOk) {
                                throw RestartRequiredException(!etagOk, !sourceOk, !sizeOk)
                            }
                        }
                        offset > 0 && m.code == 200 ->
                            // 服务端忽略 Range → 只能重新完整下载（本响应即全量）
                            throw RestartRequiredException(etagChanged = false, crossSource = false, size = false)
                        m.code / 100 == 2 -> Unit // 全新下载（offset == 0）
                        else -> throw DownloadHttpStatusException(m.code)
                    }
                    streamToPart(
                        part, offset, m, stream,
                        sourceIndex, sourceCount, url, expectedSize, progress, clock,
                    )
                    // 流结束：记录续传锚点（ETag 证据在位）
                    writeMeta(metaFile, PartMeta(fingerprint, m.etag, url, part.length()))
                }
                return
            } catch (e: RestartRequiredException) {
                if (restarted) throw IOException("续传重试后仍不可用（${e.reason()}）")
                restarted = true
                part.delete()
                metaFile.delete()
                meta = PartMeta(fingerprint = fingerprint)
            } catch (e: CancellationException) {
                // 用户取消：保留断点（下次重试从断点继续）
                preserveFrontier(metaFile, fingerprint, meta, responseEtag, url, part)
                throw e
            } catch (e: Exception) {
                // 网络中断/停滞：本次响应已过三闸校验 → 前沿可信，保留断点换源
                preserveFrontier(metaFile, fingerprint, meta, responseEtag, url, part)
                throw e
            }
        }
    }

    /** 中止后把断点前沿写入 .meta（responseEtag 为本次已验证响应的证据）。 */
    private fun preserveFrontier(
        metaFile: File,
        fingerprint: String,
        meta: PartMeta,
        responseEtag: String?,
        url: String,
        part: File,
    ) {
        runCatching {
            // 零前沿无断点可保留——不产生空 .meta（HTTP 错误/连接即拒的路径零残留）
            if (part.length() <= 0L) return
            writeMeta(
                metaFile,
                PartMeta(fingerprint, responseEtag ?: meta.etag, url, part.length()),
            )
        }
    }

    private class RestartRequiredException(
        val etagChanged: Boolean,
        val crossSource: Boolean,
        val size: Boolean,
    ) : IOException("resume-invalid") {
        fun reason() =
            listOfNotNull(
                "ETag 变化".takeIf { etagChanged },
                "跨源无一致性证据".takeIf { crossSource },
                "Range 总长不符".takeIf { size },
            ).joinToString("·").ifEmpty { "服务端不支持 Range 续传" }
    }

    private suspend fun streamToPart(
        part: File,
        offset: Long,
        meta: DownloadMeta,
        stream: ChunkStream,
        sourceIndex: Int,
        sourceCount: Int,
        url: String,
        expectedSize: Long,
        progress: (DownloadProgress) -> Unit,
        clock: () -> Long,
    ) {
        var received = offset
        var windowStart = clock()
        var windowBytes = 0L
        val streamStart = windowStart
        var lastEmit = 0L
        val totalBytes = meta.rangeTotal ?: expectedSize.takeIf { it > 0 } ?: 0L
        part.parentFile?.mkdirs()
        RandomAccessFile(part, "rw").use { raf ->
            raf.seek(offset)
            stream.forEach { buf, n ->
                raf.write(buf, 0, n)
                received += n
                windowBytes += n
                val now = clock()
                if (now - windowStart >= STALL_WINDOW_MS) {
                    if (windowBytes < STALL_MIN_BYTES) {
                        throw DownloadStallException(now - windowStart, windowBytes)
                    }
                    windowStart = now
                    windowBytes = 0
                }
                if (now - lastEmit >= PROGRESS_EMIT_MS) {
                    lastEmit = now
                    val elapsed = (now - streamStart).coerceAtLeast(1)
                    progress(
                        DownloadProgress(
                            received = received,
                            total = totalBytes,
                            bytesPerSecond = (received - offset) * 1000 / elapsed,
                            sourceIndex = sourceIndex,
                            sourceCount = sourceCount,
                            sourceLabel = label(url),
                            resuming = offset > 0,
                        ),
                    )
                }
            }
        }
    }

    /** 完整性闸：大小 + SHA-256（流式；不整文件读入内存）。 */
    private fun verify(
        part: File,
        expectedSha256: String?,
        expectedSize: Long,
        sourceIndex: Int,
        sourceCount: Int,
        url: String,
        progress: (DownloadProgress) -> Unit,
    ) {
        if (expectedSize > 0 && part.length() != expectedSize) {
            part.delete()
            throw DownloadIntegrityException(
                "文件大小不符（期望 ${expectedSize / 1024}KB，实收 ${part.length() / 1024}KB）",
            )
        }
        val expected = expectedSha256?.takeIf { it.isNotBlank() } ?: return
        progress(
            DownloadProgress(
                received = part.length(),
                total = expectedSize,
                bytesPerSecond = 0,
                sourceIndex = sourceIndex,
                sourceCount = sourceCount,
                sourceLabel = label(url),
                resuming = false,
                verifying = true,
            ),
        )
        val actual = part.sha256Hex()
        if (!actual.equals(expected, ignoreCase = true)) {
            part.delete()
            throw DownloadIntegrityException("SHA-256 校验失败（期望 ${expected.take(12)}…，实收 ${actual.take(12)}…）")
        }
    }

    private fun readMeta(metaFile: File, fingerprint: String): PartMeta? =
        runCatching {
            if (!metaFile.exists()) return null
            val m = json.decodeFromString<PartMeta>(metaFile.readText())
            if (m.fingerprint != fingerprint) null else m
        }.getOrNull()

    private fun writeMeta(metaFile: File, meta: PartMeta) {
        runCatching { metaFile.writeText(json.encodeToString(PartMeta.serializer(), meta)) }
    }

    fun label(url: String): String =
        url.substringAfter("//").substringBefore('/').takeIf { it.isNotEmpty() } ?: url
}

internal fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
