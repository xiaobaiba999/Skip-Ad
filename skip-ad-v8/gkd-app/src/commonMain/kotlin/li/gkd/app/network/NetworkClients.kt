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
    private val sharedClient = lazy { create() }
    val client: HttpClient
        get() = sharedClient.value

    // v8.0.9：更新清单专用短超时客户端——多源解析最多要串行探测 7 个 @main 源
    // + @tag 探测链 + API 兜底，沿用 15s 整体超时会让弱网下的「已是最新」判定
    // 最坏等待 30s+。清单体量 ≤ 16KB 量级，6s 请求 / 5s 连接 / 5s 读写的
    // 快失败口径与 7.15.14 清单通道（连 6 读 6 / tag 连 3 读 3）同代。
    // APK 下载仍走 client（大体量传输不适用短超时）。
    private val sharedUpdateClient = lazy { createUpdate() }
    val updateClient: HttpClient
        get() = sharedUpdateClient.value

    fun close() {
        if (sharedClient.isInitialized()) sharedClient.value.close()
        if (sharedUpdateClient.isInitialized()) sharedUpdateClient.value.close()
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
