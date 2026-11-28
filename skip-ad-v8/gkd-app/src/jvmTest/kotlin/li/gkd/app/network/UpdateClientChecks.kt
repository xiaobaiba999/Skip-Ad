package li.gkd.app.network

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import li.gkd.app.logging.LogUtils
import li.gkd.app.storage.appStorage

/** Real transport and decoding through the production client, inside the isolated app lifecycle. */
suspend fun assertUpdateClient(directory: File) {
    directory.mkdirs()
    val bytes = ByteArray(262_144) { (it % 251).toByte() }
    val finishStreaming = CountDownLatch(1)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/version.json") { request ->
        val body =
            """{"versionCode":2,"versionName":"2","downloadUrl":"gkd.apk","fileSize":${bytes.size}}"""
                .toByteArray()
        request.responseHeaders.add("Content-Type", "application/json")
        request.sendResponseHeaders(200, body.size.toLong())
        request.responseBody.use { it.write(body) }
    }
    server.createContext("/gkd.apk") { request ->
        request.sendResponseHeaders(200, bytes.size.toLong())
        request.responseBody.use { it.write(bytes) }
    }
    for (status in listOf(403, 404, 503)) {
        server.createContext("/error-$status") { request ->
            val body = "download failed".toByteArray()
            request.sendResponseHeaders(status, body.size.toLong())
            request.responseBody.use { it.write(body) }
        }
    }
    server.createContext("/stream.apk") { request ->
        request.sendResponseHeaders(200, bytes.size.toLong())
        request.responseBody.use { output ->
            output.write(bytes, 0, bytes.size / 2)
            output.flush()
            check(finishStreaming.await(10, TimeUnit.SECONDS))
            output.write(bytes, bytes.size / 2, bytes.size / 2)
        }
    }
    server.start()
    try {
        val url = "http://127.0.0.1:${server.address.port}/version.json"
        // HTTP error bodies must fail before creating an installable-looking APK.
        // v8.1.1：断点续传引擎以 expectSuccess=false 自行解析状态码 →
        // DownloadHttpStatusException（摘要「HTTP N」与旧口径一致）。
        for (status in listOf(403, 404, 503)) {
            val failedFile = directory.resolve("error-$status.apk")
            val error =
                assertFailsWith<IOException> {
                    UpdateClient.download(
                        URI(url).resolve("/error-$status").toString(),
                        failedFile,
                        0,
                        null,
                    ) {}
                }
            assertTrue(error.message.orEmpty().startsWith("HTTP $status"))
            assertEquals("HTTP $status", networkFailureSummary(error))
            assertFalse(failedFile.exists())
            assertFalse(File(failedFile.path + ".part").exists(), "HTTP 错误不得残留 .part")
            assertFalse(File(failedFile.path + ".part.meta").exists(), "HTTP 错误不得残留 .meta")
            logNetworkFailure("http-status-log-check-$status", error)
        }
        logNetworkFailure(
            "transport-log-check",
            java.net.UnknownHostException("secret-host-log-check"),
        )
        logNetworkFailure("storage-log-check", java.io.IOException("disk-log-check"))
        LogUtils.flush()
        val logText = appStorage().log.listFiles().orEmpty().joinToString("\n") { it.readText() }
        assertTrue(logText.contains("http-status-log-check-403: HTTP 403"))
        assertFalse(logText.contains("secret-host-log-check"))
        assertTrue(logText.contains("java.io.IOException: disk-log-check"))
        val file = directory.resolve("download.apk")
        var progress = 0f
        UpdateClient.download(
            URI(url).resolve("/gkd.apk").toString(),
            file,
            bytes.size.toLong(),
            null,
        ) {
            progress = it
        }
        assertTrue(bytes.contentEquals(file.readBytes()))
        assertEquals(1f, progress)
        // The first half must reach disk while the server is still withholding the second half.
        // v8.1.1：断点续传引擎写入 .part（原子落位——半成品绝不冒充正式档）；
        // 增量落盘仍可观测（.part 长度增长），不整包入内存。
        coroutineScope {
            val streamedFile = directory.resolve("stream.apk")
            val streamedPart = File(streamedFile.path + ".part")
            val download =
                async(Dispatchers.IO) {
                    UpdateClient.download(
                        URI(url).resolve("/stream.apk").toString(),
                        streamedFile,
                        bytes.size.toLong(),
                        null,
                    ) {}
                }
            try {
                withTimeout(5_000) {
                    while (streamedPart.length() == 0L) delay(10)
                }
                assertFalse(download.isCompleted)
                assertFalse(streamedFile.exists(), "完成前不得出现正式档（原子落位）")
            } finally {
                finishStreaming.countDown()
            }
            download.await()
            assertTrue(bytes.contentEquals(streamedFile.readBytes()))
        }
    } finally {
        finishStreaming.countDown()
        server.stop(0)
        directory.deleteRecursively()
    }
}
