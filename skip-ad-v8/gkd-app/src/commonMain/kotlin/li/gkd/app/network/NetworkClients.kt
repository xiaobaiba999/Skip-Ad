package li.gkd.app.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

object NetworkClients {
    private val sharedClient = lazy { create() }
    val client: HttpClient
        get() = sharedClient.value

    fun close() {
        if (sharedClient.isInitialized()) sharedClient.value.close()
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
}
