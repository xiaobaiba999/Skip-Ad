package li.gkd.app.network

import com.sun.net.httpserver.HttpServer
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import li.gkd.app.META

/**
 * v8.0.9 更新解析策略专项测试（2026-11-27 事故：v8.0.7 用户检测不到 v8.0.8）。
 *
 * 独立于应用生命周期——解析策略是纯函数，通道用本地 HTTP 服务器仿真。 矩阵覆盖：
 * - 旧客户端(87100) × 服务端新版(88000)：陈旧 CDN 在前、新鲜源在后 → 必须发现更新
 * - 新客户端(88000) × 服务端同版(88000)：全源一致 → 诚实返回「暂无更新」（versionCode == 当前）
 * - 主源全部不可达 → @tag 不可变键链接管
 * - @main/@tag 全部陈旧/不可达 → GitHub API 兜底接管
 * - 所有源全部不可达 → 抛出失败（绝不伪装「已是最新」）
 */
class UpdateClientResolutionTest {
    private fun manifestJson(versionName: String, versionCode: Int, apkUrl: String) =
        """{"schemaVersion":1,"versionName":"$versionName","versionCode":$versionCode,""" +
            """"releaseTag":"v$versionName","releaseNotes":"notes of $versionName",""" +
            """"publishedAt":"2026-11-27T00:00:00Z","apkUrl":"$apkUrl",""" +
            """"apkFileName":"adshield-v$versionName.apk","apkSha256":"${"0".repeat(64)}",""" +
            """"apkSize":1}"""

    private fun releaseApiJson(versionCode: Int) =
        """{"tag_name":"v9.9.9","name":"release",""" +
            """"body":"## 版本信息\n- versionName：9.9.9\nversionCode: $versionCode\n",""" +
            """"assets":[{"name":"adshield-v9.9.9.apk","size":42,""" +
            """"browser_download_url":"https://example.com/adshield-v9.9.9.apk",""" +
            """"digest":"sha256:${"1".repeat(64)}"}]}"""

    /** 各路径请求计数（早退出/全探测断言用）。 */
    private class Counter {
        val hits = ConcurrentHashMap<String, AtomicInteger>()

        fun hit(path: String) = hits.getOrPut(path) { AtomicInteger() }.incrementAndGet()

        fun count(path: String) = hits[path]?.get() ?: 0
    }

    private class LocalServer {
        val counter = Counter()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val handlers = mutableMapOf<String, (com.sun.net.httpserver.HttpExchange) -> Unit>()
        val base = "http://127.0.0.1:${server.address.port}"

        fun json(path: String, body: String, status: Int = 200) {
            handlers[path] = { exchange ->
                counter.hit(path)
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, if (status == 200) bytes.size.toLong() else -1)
                if (status == 200) exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
        }

        fun start() {
            server.createContext("/") { exchange ->
                val handler =
                    handlers[exchange.requestURI.path]
                        ?: run {
                            counter.hit(exchange.requestURI.path)
                            exchange.sendResponseHeaders(404, -1)
                            exchange.close()
                            return@createContext
                        }
                handler(exchange)
            }
            server.start()
        }

        fun stop() = server.stop(0)
    }

    private fun LocalServer.channel(path: String) =
        UpdateChannel.Manifest("$base$path", "$base$path")

    /** ── 事故复现：v8.0.7 生产逻辑（git tag v8.0.7 UpdateClient.fetchUpdate，逐字复刻）── */
    private suspend fun fetchUpdateV807FirstSuccess(sources: List<String>): NewVersion {
        var lastError: Exception? = null
        for (source in sources) {
            try {
                val manifest =
                    NetworkClients.updateClient
                        .get(source) { expectSuccess = true }
                        .body<NewVersion>()
                return manifest.validated(META.appId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("no update source configured")
    }

    @Test
    fun v807RegressionReproduced() = runBlocking {
        // 场景 = 2026-11-27 线上实录：cdn.jsdelivr(87100 陈旧) → gcore(87100 陈旧)
        // → raw(88000 新鲜)。v8.0.7（87100）用户检查更新。
        val s = LocalServer()
        s.json("/stale-a", manifestJson("8.0.7", 87100, "https://example.com/a.apk"))
        s.json("/stale-b", manifestJson("8.0.7", 87100, "https://example.com/b.apk"))
        s.json("/fresh", manifestJson("8.0.8", 88000, "https://example.com/c.apk"))
        s.start()
        try {
            // v8.0.7 真实逻辑：首个成功源即返回 → 拿到陈旧 87100。
            val v807Result =
                fetchUpdateV807FirstSuccess(
                    listOf("${s.base}/stale-a", "${s.base}/stale-b", "${s.base}/fresh"),
                )
            assertEquals(87100, v807Result.versionCode)
            // 判定处（UpdateStatus.checkUpdate）：87100 <= 87100 → 误判「已是最新」。
            assertTrue(v807Result.versionCode <= 87100, "v8.0.7 客户端必须在此场景下误判为已是最新（缺陷复现）")
        } finally {
            s.stop()
        }
    }

    @Test
    fun staleMainChainDiscoversFromFreshLaterSource() = runBlocking {
        // 同场景，新策略：陈旧源绝不单独构成「已是最新」结论，后续新鲜源接管。
        val s = LocalServer()
        s.json("/stale-a", manifestJson("8.0.7", 87100, "https://example.com/a.apk"))
        s.json("/stale-b", manifestJson("8.0.7", 87100, "https://example.com/b.apk"))
        s.json("/fresh", manifestJson("8.0.8", 88000, "https://example.com/c.apk"))
        s.start()
        try {
            val result =
                UpdateResolution.resolveLatest(
                    currentVersionCode = 87100,
                    mainChannels =
                        listOf(s.channel("/stale-a"), s.channel("/stale-b"), s.channel("/fresh")),
                    tagChannels = emptyList(),
                    apiChannels = emptyList(),
                    fetch = { UpdateClient.fetchChannel(it) },
                )
            assertEquals(88000, result.versionCode)
            assertEquals("8.0.8", result.versionName)
            // 全部主源都已被探测（陈旧源不阻断后续源）。
            assertEquals(
                3,
                s.counter.count("/stale-a") +
                    s.counter.count("/stale-b") +
                    s.counter.count("/fresh"),
            )
        } finally {
            s.stop()
        }
    }

    @Test
    fun unreachablePrimaryFallsBackToBackup() = runBlocking {
        val s = LocalServer()
        s.json("/primary", "unavailable", 503)
        s.json("/backup", manifestJson("8.0.8", 88000, "https://example.com/c.apk"))
        s.start()
        try {
            val result =
                UpdateResolution.resolveLatest(
                    currentVersionCode = 87100,
                    mainChannels = listOf(s.channel("/primary"), s.channel("/backup")),
                    tagChannels = emptyList(),
                    apiChannels = emptyList(),
                    fetch = { UpdateClient.fetchChannel(it) },
                )
            assertEquals(88000, result.versionCode)
        } finally {
            s.stop()
        }
    }

    @Test
    fun tagChainRescuesWhenMainStale() = runBlocking {
        // @main 全部陈旧（88000 ≤ 当前 88000）→ @tag 不可变键链发现 88100。
        val s = LocalServer()
        s.json("/main-stale", manifestJson("8.0.8", 88000, "https://example.com/m.apk"))
        s.json("/tag-v8.0.10", manifestJson("8.0.10", 88100, "https://example.com/t.apk"))
        s.start()
        try {
            val result =
                UpdateResolution.resolveLatest(
                    currentVersionCode = 88000,
                    mainChannels = listOf(s.channel("/main-stale")),
                    tagChannels = listOf(s.channel("/tag-v8.0.10")),
                    apiChannels = emptyList(),
                    fetch = { UpdateClient.fetchChannel(it) },
                )
            assertEquals(88100, result.versionCode)
        } finally {
            s.stop()
        }
    }

    @Test
    fun apiFallbackRescuesWhenStaleOrMissing() = runBlocking {
        val s = LocalServer()
        s.json("/main-stale", manifestJson("8.0.8", 88000, "https://example.com/m.apk"))
        s.json("/api", releaseApiJson(88100))
        s.start()
        try {
            val result =
                UpdateResolution.resolveLatest(
                    currentVersionCode = 88000,
                    mainChannels = listOf(s.channel("/main-stale")),
                    tagChannels = emptyList(),
                    apiChannels = listOf(UpdateChannel.ReleaseApi("api", "${s.base}/api")),
                    fetch = { UpdateClient.fetchChannel(it) },
                )
            assertEquals(88100, result.versionCode)
            assertEquals("v9.9.9", result.releaseTag)
            assertEquals("https://example.com/adshield-v9.9.9.apk", result.apkUrl)
            assertEquals("1".repeat(64), result.apkSha256)
        } finally {
            s.stop()
        }
    }

    @Test
    fun allChannelsFailThrowsInsteadOfFakingLatest() = runBlocking {
        val s = LocalServer()
        s.json("/down-1", "unavailable", 503)
        s.json("/down-2", "unavailable", 503)
        s.start()
        try {
            val error =
                assertFailsWith<ResponseException> {
                    UpdateResolution.resolveLatest(
                        currentVersionCode = 87100,
                        mainChannels = listOf(s.channel("/down-1"), s.channel("/down-2")),
                        tagChannels = listOf(s.channel("/down-2")),
                        apiChannels = listOf(s.channel("/down-2")),
                        fetch = { UpdateClient.fetchChannel(it) },
                    )
                }
            assertTrue(error.response.status.value == 503)
        } finally {
            s.stop()
        }
    }

    @Test
    fun alreadyLatestRequiresProbingEveryChannel() = runBlocking {
        // v8.0.8 客户端 × 8.0.8 服务端：全部通道一致返回当前版本 → 诚实「暂无更新」。
        // 且结论必须建立在全通道探测之上（计数断言），而非首个成功源。
        val s = LocalServer()
        for (path in listOf("/m1", "/m2", "/tag", "/api")) {
            s.json(path, manifestJson("8.0.8", 88000, "https://example.com/x.apk"))
        }
        s.start()
        try {
            val result =
                UpdateResolution.resolveLatest(
                    currentVersionCode = 88000,
                    mainChannels = listOf(s.channel("/m1"), s.channel("/m2")),
                    tagChannels = listOf(s.channel("/tag")),
                    apiChannels = listOf(s.channel("/api")),
                    fetch = { UpdateClient.fetchChannel(it) },
                )
            assertEquals(88000, result.versionCode)
            assertTrue(result.versionCode <= 88000, "必须得出「暂无更新」而非伪装失败")
            for (path in listOf("/m1", "/m2", "/tag", "/api")) {
                assertEquals(1, s.counter.count(path), "$path must be probed exactly once")
            }
        } finally {
            s.stop()
        }
    }

    @Test
    fun earlyExitKeepsFastPathOnDiscovery() = runBlocking {
        // 发现更新立即返回：剩余源零请求（快路径语义与 8.0.x 一致）。
        val s = LocalServer()
        s.json("/update", manifestJson("8.0.9", 88100, "https://example.com/u.apk"))
        s.json("/never", manifestJson("8.0.8", 88000, "https://example.com/n.apk"))
        s.start()
        try {
            val result =
                UpdateResolution.resolveLatest(
                    currentVersionCode = 88000,
                    mainChannels = listOf(s.channel("/update"), s.channel("/never")),
                    tagChannels = listOf(s.channel("/never")),
                    apiChannels = listOf(s.channel("/never")),
                    fetch = { UpdateClient.fetchChannel(it) },
                )
            assertEquals(88100, result.versionCode)
            assertEquals(0, s.counter.count("/never"), "发现更新后不得再探测剩余通道")
        } finally {
            s.stop()
        }
    }

    @Test
    fun candidateTagsCoverNextReleaseSteps() {
        assertEquals(
            listOf("v8.0.10", "v8.1.0", "v8.1.1", "v8.2.0"),
            UpdateResolution.candidateTags("8.0.9"),
        )
        assertEquals(
            listOf("v7.15.15", "v7.16.0", "v7.16.1", "v7.17.0"),
            UpdateResolution.candidateTags("7.15.14"),
        )
        assertTrue(UpdateResolution.candidateTags("garbage").isEmpty())
        assertTrue(UpdateResolution.candidateTags("8").isEmpty())
    }

    @Test
    fun parseGithubReleaseRequiresExplicitVersionCodeLine() {
        val parsed = UpdateClient.parseGithubRelease(releaseApiJson(88100))
        assertEquals(88100, parsed?.versionCode)
        assertEquals("9.9.9", parsed?.versionName)
        assertEquals("1".repeat(64), parsed?.apkSha256)
        assertEquals(42L, parsed?.apkSize)
        // 正文无「versionCode: N」行（7.x 历史正文）→ 通道不可用。
        assertNull(
            UpdateClient.parseGithubRelease(
                """{"tag_name":"v7.15.14","body":"旧版正文","assets":[]}""",
            ),
        )
        // 无 apk 资产 → 通道不可用。
        assertNull(
            UpdateClient.parseGithubRelease(
                """{"tag_name":"v8.0.9","body":"versionCode: 88100\\n","assets":[]}""",
            ),
        )
    }

    @Test
    fun downloadFirstAvailableFallsBackToMirror() = runBlocking {
        val bytes = ByteArray(131_072) { (it % 251).toByte() }
        val sha =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        val s = LocalServer()
        s.json("/gone", "unavailable", 503)
        s.handlers["/mirror.apk"] = { exchange ->
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        s.start()
        val dir =
            File.createTempFile("upd", "").let {
                it.delete()
                it.mkdirs()
                it
            }
        try {
            val file = dir.resolve("download.apk")
            var progress = 0f
            val logged = mutableListOf<String>()
            UpdateClient.downloadFirstAvailableWith(
                listOf("${s.base}/gone", "${s.base}/mirror.apk"),
                file,
                bytes.size.toLong(),
                sha,
                progress = { progress = it },
                logFailure = { op, _ -> logged.add(op) },
            )
            assertContentEquals(bytes, file.readBytes())
            assertEquals(1f, progress)
            assertEquals(listOf("Update download source failed: ${s.base}/gone"), logged)
        } finally {
            s.stop()
            dir.deleteRecursively()
        }
    }

    @Test
    fun downloadFirstAvailableAllFailThrowsAndDeletesFile() = runBlocking {
        val s = LocalServer()
        s.json("/gone-1", "unavailable", 503)
        s.json("/gone-2", "unavailable", 503)
        s.start()
        val dir =
            File.createTempFile("upd", "").let {
                it.delete()
                it.mkdirs()
                it
            }
        try {
            val file = dir.resolve("download.apk")
            val logged = mutableListOf<String>()
            assertFailsWith<Exception> {
                UpdateClient.downloadFirstAvailableWith(
                    listOf("${s.base}/gone-1", "${s.base}/gone-2"),
                    file,
                    0,
                    null,
                    progress = {},
                    logFailure = { op, _ -> logged.add(op) },
                )
            }
            assertTrue(!file.exists(), "失败后不得残留坏档")
            assertEquals(2, logged.size, "全部失败源都要有失败记录（可诊断）")
        } finally {
            s.stop()
            dir.deleteRecursively()
        }
    }
}
