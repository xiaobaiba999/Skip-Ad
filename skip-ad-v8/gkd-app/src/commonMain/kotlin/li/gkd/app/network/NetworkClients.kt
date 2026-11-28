package li.gkd.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

object NetworkClients {
    // v8.1.1：lazy 槽位可重置——close() 后（DesktopRuntime 关闭/测试顺序/同进程重启）
    // 必须重建，复用已关闭客户端会在新请求上抛 JobCancellationException
    // （Parent job is Completed——全量套件顺序故障根因，非仅测试问题）。
    private var sharedClient = lazy { create() }
    val client: HttpClient
        get() = sharedClient.value

    // v8.0.9：更新清单专用短超时客户端——多源解析最多要串行探测 7 个 @main 源
    // + @tag 探测链 + API 兜底，沿用 15s 整体超时会让弱网下的「已是最新」判定
    // 最坏等待 30s+。清单体量 ≤ 16KB 量级，6s 请求 / 5s 连接 / 5s 读写的
    // 快失败口径与 7.15.14 清单通道（连 6 读 6 / tag 连 3 读 3）同代。
    private var sharedUpdateClient = lazy { createUpdate() }
    val updateClient: HttpClient
        get() = sharedUpdateClient.value

    // v8.1.1：APK 下载专用客户端——【无整体 requestTimeout】。
    // v8.1.0 及以前下载复用 client（requestTimeout 15s 整体上限）：弱网下
    // 3.7MB@250KB/s ≈ 15s 即中途必死且无法续传——「下载到一半失败」的直接
    // 根因之一。大体量传输只保留连接 10s / 读写 20s 的逐段超时，整体时长
    // 不设限（由 UpdateDownloader 停滞闸治理慢死链，而非整体掐断）。
    private var sharedDownloadClient = lazy { createDownload() }
    val downloadClient: HttpClient
        get() = sharedDownloadClient.value

    @Synchronized
    fun close() {
        if (sharedClient.isInitialized()) sharedClient.value.close()
        if (sharedUpdateClient.isInitialized()) sharedUpdateClient.value.close()
        if (sharedDownloadClient.isInitialized()) sharedDownloadClient.value.close()
        // v8.1.1：重建 lazy 槽位——关闭后的实例绝不复用（见上）
        sharedClient = lazy { create() }
        sharedUpdateClient = lazy { createUpdate() }
        sharedDownloadClient = lazy { createDownload() }
    }

    private fun create() =
        HttpClient(OkHttp) {
            // v8.0.8：显式超时兜底——此前未装 HttpTimeout，OkHttp 默认读写超时 10s、
            // 连接超时 10s 且无整体上限；更新检查最多 5 源串行时，弱网下最坏
            // ~50s 才能报「多源均不可达」。15s 上限保证弱网可感知快失败，
            // 单源 10s / 整体 15s 与 7.15.14 网络超时口径一致。
            install(HttpTimeout) {
                requestTimeoutMillis = 15.seconds.inWholeMilliseconds
                connectTimeoutMillis = 10.seconds.inWholeMilliseconds
                socketTimeoutMillis = 10.seconds.inWholeMilliseconds
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    },
                    ContentType.Any,
                )
            }
            engine { clientCacheSize = 0 }
        }

    private fun createDownload() =
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                // 大文件下载：整体时长不设限（停滞由 UpdateDownloader 停滞闸治理）
                requestTimeoutMillis = Long.MAX_VALUE
                connectTimeoutMillis = 10.seconds.inWholeMilliseconds
                socketTimeoutMillis = 20.seconds.inWholeMilliseconds
            }
            engine { clientCacheSize = 0 }
        }

    private fun createUpdate() =
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 6.seconds.inWholeMilliseconds
                connectTimeoutMillis = 5.seconds.inWholeMilliseconds
                socketTimeoutMillis = 5.seconds.inWholeMilliseconds
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    },
                    ContentType.Any,
                )
            }
            engine { clientCacheSize = 0 }
        }
}
