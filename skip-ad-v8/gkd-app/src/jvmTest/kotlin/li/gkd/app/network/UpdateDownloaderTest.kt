package li.gkd.app.network

import java.io.File
import java.io.IOException
import java.net.SocketException
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/**
 * v8.1.1 断点续传多源下载器测试（第 7 阶段 7.11 场景 10–27）。
 *
 * 引擎为脚本化假源（不依赖 MockWebServer；Range/ETag/中断/停滞/慢滴全可控，
 * 时钟注入——停滞闸确定性复现）。
 */
class UpdateDownloaderTest {
    private class Script(
        val bytes: ByteArray,
        val etag: String? = "\"sha-etag\"",
        val rangeSupport: Boolean = true,
        /** chunk 索引达到此值时抛 ex（模拟中断/重置） */
        val throwAtChunk: Int = -1,
        val ex: Exception? = null,
        val httpCode: Int? = null,
        /** 每 chunk 后推进的假时钟毫秒（慢滴模拟） */
        val chunkDelayMs: Long = 0,
        /** 该源整体拒绝（连接层异常） */
        val connectError: Exception? = null,
        /** 该源只提供前 N 字节（服务器提前断开） */
        val serveOnlyPrefix: Int = -1,
    )

    /** 脚本化下载引擎：真实实现 Range 语义（206/200/ETag/Content-Range）。 */
    private class FakeSource(
        scripts: Map<String, Script>,
        private val clock: Clock? = null,
    ) : DownloadSource {
        val requests = mutableListOf<Pair<String, Long>>()
        val scriptOf = scripts

        private fun streamer(
            data: ByteArray,
            s: Script,
        ): suspend ((ByteArray, Int) -> Unit) -> Unit =
            { onChunk ->
                var idx = 0
                val chunkSize = 16 * 1024
                var i = 0
                while (i < data.size) {
                    if (s.throwAtChunk == idx) {
                        throw s.ex ?: SocketException("Connection reset")
                    }
                    val n = minOf(chunkSize, data.size - i)
                    onChunk(data.copyOfRange(i, i + n), n)
                    i += n
                    idx++
                    if (s.chunkDelayMs > 0) clock?.tick(s.chunkDelayMs)
                }
            }

        override suspend fun <R> get(
            url: String,
            rangeFrom: Long,
            use: suspend (DownloadMeta, ChunkStream) -> R,
        ): R {
            requests += url to rangeFrom
            val s = scriptOf[url] ?: throw java.net.UnknownHostException(url)
            s.connectError?.let { throw it }
            val resume = rangeFrom > 0
            if (s.httpCode != null) {
                return use(DownloadMeta(s.httpCode, s.etag, 0, null), ChunkStream { _ -> })
            }
            if (resume && !s.rangeSupport) {
                // 服务端忽略 Range → 200 全量
                val data =
                    if (s.serveOnlyPrefix >= 0) {
                        s.bytes.copyOf(minOf(s.serveOnlyPrefix, s.bytes.size))
                    } else {
                        s.bytes
                    }
                return use(
                    DownloadMeta(200, s.etag, data.size.toLong(), null),
                    ChunkStream(streamer(data, s)),
                )
            }
            val from = if (resume) rangeFrom else 0L
            val total = s.bytes.size.toLong()
            val meta =
                DownloadMeta(
                    code = if (resume) 206 else 200,
                    etag = s.etag,
                    contentLength = total - from,
                    rangeTotal = if (resume) total else null,
                )
            val slice =
                if (s.serveOnlyPrefix >= 0) {
                    s.bytes.copyOf(minOf(s.serveOnlyPrefix, s.bytes.size))
                } else {
                    s.bytes.copyOfRange(from.toInt(), s.bytes.size)
                }
            return use(meta, ChunkStream(streamer(slice, s)))
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun tempDir(): File =
        File.createTempFile("dltest", "").let {
            it.delete()
            it.mkdirs()
            it
        }

    private class Clock(var now: Long = 100_000L) {
        fun tick(ms: Long) {
            now += ms
        }
    }

    // ── 场景 24：首选源正常 → 成功落位 ──
    @Test
    fun firstSourceSucceeds() = runBlocking {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes)))
            val file =
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            assertTrue(file.readBytes().contentEquals(bytes))
            assertFalse(File(target.path + ".part").exists(), "成功后 .part 应清除")
            assertFalse(File(target.path + ".part.meta").exists(), "成功后 .meta 应清除")
            assertEquals(1, src.requests.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 10/11：首选源 Connection reset → 自动切换镜像 ──
    @Test
    fun connectionResetOnFirstSourceSwitchesToMirror() = runBlocking {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src =
                FakeSource(
                    mapOf(
                        "https://a.host/x.apk" to
                            Script(bytes, throwAtChunk = 1, ex = SocketException("Connection reset")),
                        "https://b.host/x.apk" to Script(bytes, etag = "\"other-etag\""),
                    ),
                )
            val file =
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk", "https://b.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            assertTrue(file.readBytes().contentEquals(bytes))
            assertEquals(
                listOf(
                    "https://a.host/x.apk" to 0L,
                    "https://b.host/x.apk" to 16384L, // 续传尝试（16KB 断点）
                    "https://b.host/x.apk" to 0L, // ETag 不一致被拒 → 不拼接，从头下载
                ),
                src.requests,
                "ETag 不同（无一致性证据）→ 续传尝试被拒后镜像从头下载，绝不拼接",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 14/15：下载中断 + 同源断点续传（206 + ETag 一致） ──
    @Test
    fun interruptedDownloadResumesFromFrontierOnRetry() = runBlocking {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val script = Script(bytes, throwAtChunk = 2, ex = SocketException("Connection reset"))
            val src = FakeSource(mapOf("https://a.host/x.apk" to script))
            // 第一次：中断 → 全源失败（保留 .part 断点）
            assertFailsWith<Exception> {
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            }
            val part = File(target.path + ".part")
            assertTrue(part.exists() && part.length() > 0, "中断后 .part 保留供续传")
            // 第二次：同 ETag 不中断脚本 → 从断点续传
            val src2 = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes, etag = script.etag)))
            val file =
                UpdateDownloader.download(
                    src2, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            assertTrue(file.readBytes().contentEquals(bytes))
            assertEquals(32768L, src2.requests.first().second, "第二次从断点续传（Range 请求）")
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 18：ETag 不一致 → 不拼接，重新完整下载 ──
    @Test
    fun etagMismatchForcesFreshDownload() = runBlocking {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src =
                FakeSource(
                    mapOf(
                        "https://a.host/x.apk" to
                            Script(bytes, throwAtChunk = 2, ex = IOException("unexpected end of stream")),
                    ),
                )
            assertFailsWith<Exception> {
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            }
            // ETag 变化的同源 → 续传闸拒绝 → 同源重新完整下载
            val src2 = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes, etag = "\"CHANGED\"")))
            val file =
                UpdateDownloader.download(
                    src2, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            assertTrue(file.readBytes().contentEquals(bytes))
            // 首次 Range 尝试（32768）→ ETag 不符被拒 → 重新从 0 完整下载
            assertEquals(32768L, src2.requests.first().second, "断点续传尝试先行")
            assertTrue(src2.requests.any { it.second == 0L }, "ETag 拒绝后必须重新从 0 下载")
            assertEquals(2, src2.requests.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 17：跨源无 ETag 证据 → 不拼接（最终从头下载） ──
    @Test
    fun crossSourceWithoutEtagRestarts() = runBlocking {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src =
                FakeSource(
                    mapOf(
                        "https://a.host/x.apk" to
                            Script(bytes, throwAtChunk = 1, ex = SocketException("reset")),
                    ),
                )
            assertFailsWith<Exception> {
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            }
            // 换镜像源：镜像无 ETag → 不允许拼接 a.host 的断点
            val src2 = FakeSource(mapOf("https://b.host/x.apk" to Script(bytes, etag = null)))
            UpdateDownloader.download(
                src2, listOf("https://b.host/x.apk"), target,
                "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
            )
            assertEquals(0L, src2.requests.last().second, "无 ETag 跨源最终必须从头下载")
            assertTrue(src2.requests.any { it.second > 0L }, "续传尝试先行（被拒后从头）")
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 16：服务端不支持 Range（200 响应）→ 重新完整下载 ──
    @Test
    fun serverIgnoringRangeRestartsFresh() = runBlocking {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes, rangeSupport = false)))
            // 制造断点：先中断一次
            val src1 =
                FakeSource(
                    mapOf(
                        "https://a.host/x.apk" to
                            Script(bytes, throwAtChunk = 1, ex = SocketException("reset")),
                    ),
                )
            assertFailsWith<Exception> {
                UpdateDownloader.download(
                    src1, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            }
            val file =
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            assertTrue(file.readBytes().contentEquals(bytes), "200 忽略 Range 时从头下载且文件正确")
            assertEquals(16384L, src.requests.first().second, "断点续传尝试先行")
            assertTrue(src.requests.any { it.second == 0L }, "服务端忽略 Range → 重新完整下载")
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 12：下载速度停滞（慢滴）→ 停滞闸中止 → 换源 ──
    @Test
    fun slowDripStallSwitchesSource() = runBlocking {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        val dir = tempDir()
        val clock = Clock()
        try {
            val target = dir.resolve("app.apk")
            // a 源慢滴：每 chunk 16KB 但 15s 窗口内 < 64KB（每 chunk +7s）
            val src =
                FakeSource(
                    mapOf(
                        "https://slow.host/x.apk" to Script(bytes, chunkDelayMs = 7_000),
                        "https://fast.host/x.apk" to Script(bytes),
                    ),
                    clock,
                )
            val file =
                UpdateDownloader.download(
                    src,
                    listOf("https://slow.host/x.apk", "https://fast.host/x.apk"),
                    target,
                    "f:1", sha(bytes), bytes.size.toLong(),
                    {}, { _, _ -> },
                    clock = { clock.now },
                )
            assertTrue(file.readBytes().contentEquals(bytes))
            assertTrue(src.requests.any { it.first.contains("fast") }, "停滞后必须切换到快源")
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 19：文件大小不一致 → 完整性失败 ──
    @Test
    fun sizeMismatchFailsIntegrity() = runBlocking {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            // 服务器提前断开 → 文件不足
            val src =
                FakeSource(mapOf("https://a.host/x.apk" to Script(bytes, serveOnlyPrefix = 50_000)))
            val e =
                assertFailsWith<Exception> {
                    UpdateDownloader.download(
                        src, listOf("https://a.host/x.apk"), target,
                        "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                    )
                }
            assertContains(e.message.orEmpty(), "大小不符")
            assertFalse(target.exists(), "不完整文件不得冒充正式档")
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 20：SHA-256 不一致 → 拒绝 + 换源重试 ──
    @Test
    fun shaMismatchRejectedAndNextSourceUsed() = runBlocking {
        val good = ByteArray(100_000) { (it % 251).toByte() }
        val bad = ByteArray(100_000) { (it % 247).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src =
                FakeSource(
                    mapOf(
                        "https://a.host/x.apk" to Script(bad), // 字节错误（大小对但 sha 不对）
                        "https://b.host/x.apk" to Script(good),
                    ),
                )
            val file =
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk", "https://b.host/x.apk"), target,
                    "f:1", sha(good), good.size.toLong(), {}, { _, _ -> },
                )
            assertTrue(file.readBytes().contentEquals(good))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 22：用户取消 → 停止网络任务 + 断点保留 ──
    @Test
    fun cancellationKeepsPartForResume() = runBlocking {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes)))
            var thrown: Throwable? = null
            try {
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(),
                    { p ->
                        if (p.received > 60_000) throw CancellationException("user cancel")
                    },
                    { _, _ -> },
                )
            } catch (e: CancellationException) {
                thrown = e
            }
            assertTrue(thrown is CancellationException, "用户取消以 CancellationException 传播")
            val part = File(target.path + ".part")
            assertTrue(part.exists() && part.length() > 0, "取消后断点保留")
            assertFalse(target.exists(), "取消不产生正式档")
            // 取消后的重试从断点续传（ETag 一致）
            val src2 = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes)))
            val file =
                UpdateDownloader.download(
                    src2, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            assertTrue(file.readBytes().contentEquals(bytes))
            assertTrue(src2.requests.first().second > 0, "重试从断点续传")
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 13/17：源切换通知（UI 状态） ──
    @Test
    fun switchingEventEmittedWithClassifiedReason() = runBlocking {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src =
                FakeSource(
                    mapOf(
                        "https://a.host/x.apk" to
                            Script(bytes, throwAtChunk = 0, ex = SocketException("Connection reset")),
                        "https://b.host/x.apk" to Script(bytes, etag = "\"another\""),
                    ),
                )
            val events = mutableListOf<DownloadProgress>()
            UpdateDownloader.download(
                src, listOf("https://a.host/x.apk", "https://b.host/x.apk"), target,
                "f:1", sha(bytes), bytes.size.toLong(),
                { p -> if (p.switchingTo != null) events.add(p) },
                { _, _ -> },
            )
            assertEquals(1, events.size)
            assertEquals("a.host", events.first().switchingFrom)
            assertEquals("b.host", events.first().switchingTo)
            assertContains(events.first().switchingReason.orEmpty(), "重置")
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 24b：校验中状态发射 + 成功路径完整状态 ──
    @Test
    fun verifyingStateEmittedOnSuccess() = runBlocking {
        val bytes = ByteArray(80_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes)))
            val states = mutableListOf<DownloadProgress>()
            UpdateDownloader.download(
                src, listOf("https://a.host/x.apk"), target,
                "f:1", sha(bytes), bytes.size.toLong(),
                { p -> states.add(p) },
                { _, _ -> },
            )
            assertTrue(states.any { it.verifying }, "校验阶段必须对 UI 可见")
            assertTrue(states.any { !it.verifying && it.received > 0 }, "下载阶段进度可见")
            assertEquals("a.host", states.first().sourceLabel)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 指纹不一致（版本变化）→ 断点作废重新下载 ──
    @Test
    fun fingerprintMismatchDiscardsStalePart() = runBlocking {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val other = ByteArray(100_000) { (it % 199).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes)))
            UpdateDownloader.download(
                src, listOf("https://a.host/x.apk"), target,
                "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
            )
            // 新版本（不同指纹）的 .part 残留：伪造旧断点
            File(target.path + ".part").writeBytes(other)
            File(target.path + ".part.meta")
                .writeText("""{"fingerprint":"f:0","etag":null,"sourceUrl":null,"offset":100000}""")
            val src2 = FakeSource(mapOf("https://a.host/x.apk" to Script(bytes)))
            val file =
                UpdateDownloader.download(
                    src2, listOf("https://a.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            assertEquals(0L, src2.requests.first().second, "指纹不符 → 从头下载（旧断点作废）")
            assertTrue(file.readBytes().contentEquals(bytes))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 场景 10：HTTP 404 → 换源 ──
    @Test
    fun httpErrorSwitchesSource() = runBlocking {
        val bytes = ByteArray(80_000) { (it % 251).toByte() }
        val dir = tempDir()
        try {
            val target = dir.resolve("app.apk")
            val src =
                FakeSource(
                    mapOf(
                        "https://a.host/x.apk" to Script(bytes, httpCode = 404),
                        "https://b.host/x.apk" to Script(bytes),
                    ),
                )
            val file =
                UpdateDownloader.download(
                    src, listOf("https://a.host/x.apk", "https://b.host/x.apk"), target,
                    "f:1", sha(bytes), bytes.size.toLong(), {}, { _, _ -> },
                )
            assertTrue(file.readBytes().contentEquals(bytes))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── 7.7：失败分类器 ──
    @Test
    fun failureClassifierLabels() {
        assertEquals(
            "DNS 解析失败（当前网络无法解析下载源域名）",
            DownloadFailures.classify(java.net.UnknownHostException("bogus.host")),
        )
        assertEquals(
            "连接被重置（网络链路中断）",
            DownloadFailures.classify(SocketException("Connection reset")),
        )
        assertEquals(
            "传输中断（服务器提前断开连接）",
            DownloadFailures.classify(IOException("unexpected end of stream")),
        )
        assertEquals(
            "连接/读取超时（网络链路不通或响应过慢）",
            DownloadFailures.classify(java.net.SocketTimeoutException("timeout")),
        )
        // 包装链也要走到根因
        assertEquals(
            "连接被重置（网络链路中断）",
            DownloadFailures.classify(IOException("wrapped", SocketException("Connection reset"))),
        )
        assertContains(DownloadFailures.classify(IllegalStateException("x")), "网络异常")
    }
}
