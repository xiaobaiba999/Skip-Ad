package li.gkd.app.network

import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import li.gkd.app.META

/**
 * One update channel. Manifest channels serve update/latest.json; ReleaseApi serves GitHub JSON.
 */
internal sealed interface UpdateChannel {
    val id: String

    data class Manifest(override val id: String, val url: String) : UpdateChannel

    data class ReleaseApi(override val id: String, val url: String) : UpdateChannel
}

/**
 * v8.0.9 多源更新解析策略（结构性回归修复）。
 *
 * 事故（2026-11-27，v8.0.7 用户检测不到 v8.0.8）：v8.0.x 客户端 `fetchUpdate()` 按序取【首个成功源】即返回——v8.0.8 发布后 jsDelivr
 * cdn+gcore 两域对 @main 清单 持续返回陈旧缓存（17h+ 仍 87100），首个成功源的 87100 掩盖了 raw/fastly/testingcf/ kilo 域上的新鲜
 * 88000，全体用户被判「已是最新」。7.15.x 时代的 `UpdateManifest.java` 多源合流架构（取 versionCode 最大者 + @tag 不可变键探测 +
 * GitHub API 兜底）在 v8.0.x 重写时丢失——本轮恢复并传承：
 *
 * - @main 链：按序探测；首个返回 versionCode > 当前的源【立即返回】（快路径）； 任何单源返回 versionCode <= 当前都【绝不】单独构成「已是最新」结论。
 * - @tag 探测链：仅当 @main 全链未发现更新时按序探测下一版本候选 tag 的 jsDelivr
 *
 *     @tag 清单——tag 指向固定 commit、无源站分支缓存，发布即新鲜（发布流程保证 清单先于打 tag 提交，tag 树自含本版清单）。
 * - GitHub Releases API 兜底：匿名 /releases/latest（versionCode 自 Release 正文 「versionCode: N」行提取——显式抬码后
 *   tag 公式不可用）。
 * - 全部通道失败 → 抛出最后一次失败（UI 显示检查失败，绝不伪装「已是最新」）。
 * - 至少一个通道给出有效清单且 versionCode <= 当前 → 诚实的「暂无更新」。
 */
internal object UpdateResolution {
    /** 候选 tag 集（下一补丁 + 下两个 minor——7.15.x 同款步长覆盖；更大跳版由 @main/API 链覆盖）。 */
    fun candidateTags(versionName: String): List<String> {
        val parts = versionName.trim().removePrefix("v").split(".")
        if (parts.size < 2) return emptyList()
        val major = parts[0].toIntOrNull() ?: return emptyList()
        val minor = parts[1].toIntOrNull() ?: return emptyList()
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return listOf(
            "v$major.$minor.${patch + 1}",
            "v$major.${minor + 1}.0",
            "v$major.${minor + 1}.1",
            "v$major.${minor + 2}.0",
        )
    }

    /** @return 版本最高的有效清单：versionCode > current 即被发现的新版本； 全通道均失败时抛出最后一次异常（绝不返回伪造结果）。 */
    suspend fun resolveLatest(
        currentVersionCode: Int,
        mainChannels: List<UpdateChannel>,
        tagChannels: List<UpdateChannel>,
        apiChannels: List<UpdateChannel>,
        fetch: suspend (UpdateChannel) -> NewVersion,
        logFailure: (UpdateChannel, Exception) -> Unit = { _, _ -> },
    ): NewVersion {
        var best: NewVersion? = null
        var lastError: Exception? = null
        for (phase in listOf(mainChannels, tagChannels, apiChannels)) {
            for (channel in phase) {
                try {
                    val manifest = fetch(channel)
                    if (best == null || manifest.versionCode > best!!.versionCode) best = manifest
                    if (manifest.versionCode > currentVersionCode) return manifest
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    logFailure(channel, e)
                }
            }
        }
        return best ?: throw lastError ?: IllegalStateException("no update source configured")
    }
}

object UpdateClient {
    // 更新清单拉源（v8.0.9）：国内可达的 jsDelivr 四域 + kilo 历史域优先（墙内快路径），
    // raw 双仓殿后（墙外快路径/墙内被阻断时由前面的域兜底）。
    // 顺序即探测序：首个返回「有更新」的源立即胜出（快路径语义与 8.0.x 一致）。
    private val manifestSources =
        listOf(
            AppLinks.StableUpdate,
            AppLinks.StableUpdateGcore,
            AppLinks.StableUpdateFastly,
            AppLinks.StableUpdateTestingcf,
            AppLinks.StableUpdateLegacy,
            AppLinks.StableUpdateRaw,
            AppLinks.StableUpdateLegacyRaw,
        )

    // @tag 探测链域（≤2 域快失败——7.15.x 同款预算收敛）。
    private val tagProbeHosts =
        listOf(AppLinks.UpdateTagProbeHostGcore, AppLinks.UpdateTagProbeHostCdn)

    /**
     * 多源合流解析（v8.0.9 结构性修复）：任一新鲜源即可发现新版本； 单一 CDN 陈旧缓存不再能让用户被判「已是最新」。 Throws the last failure when
     * every source is unreachable.
     */
    suspend fun fetchUpdate(): NewVersion {
        val mainChannels = manifestSources.map { UpdateChannel.Manifest(it, it) }
        val tagChannels =
            UpdateResolution.candidateTags(META.versionName).flatMap { tag ->
                tagProbeHosts.map { host ->
                    UpdateChannel.Manifest("tag:$tag@$host", AppLinks.stableUpdateTagUrl(host, tag))
                }
            }
        val apiChannels =
            listOf(UpdateChannel.ReleaseApi(AppLinks.GithubReleaseApi, AppLinks.GithubReleaseApi))
        return UpdateResolution.resolveLatest(
            currentVersionCode = META.versionCode,
            mainChannels = mainChannels,
            tagChannels = tagChannels,
            apiChannels = apiChannels,
            fetch = ::fetchChannel,
            logFailure = { channel, e ->
                logNetworkFailure("Update channel failed: ${channel.id}", e)
            },
        )
    }

    /** Real production fetch path for one channel (internal for direct test fidelity). */
    internal suspend fun fetchChannel(channel: UpdateChannel): NewVersion =
        when (channel) {
            is UpdateChannel.Manifest ->
                NetworkClients.updateClient
                    .get(channel.url) {
                        expectSuccess = true
                        // 防御 ISP 透明代理缓存：显式要求不走缓存（OkHttp 本身无响应缓存）。
                        header(HttpHeaders.CacheControl, "no-cache")
                    }
                    .body<NewVersion>()
                    .validated(META.appId)
            is UpdateChannel.ReleaseApi -> {
                val text =
                    NetworkClients.updateClient
                        .get(channel.url) {
                            expectSuccess = true
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            header(HttpHeaders.CacheControl, "no-cache")
                        }
                        .bodyAsText()
                parseGithubRelease(text)?.validated(META.appId)
                    ?: throw IllegalStateException(
                        "release body lacks the explicit versionCode line",
                    )
            }
        }

    /**
     * GitHub Releases API 兜底解析。versionCode 必须来自正文行首「versionCode: N」 （显式抬码后 8.0.9 ≠ 公式 80900，tag
     * 公式不可用；无该行的正文按 7.15.x 同款契约视为不可用通道）。
     */
    internal fun parseGithubRelease(text: String): NewVersion? {
        val root = Json.parseToJsonElement(text).jsonObject
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val body = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val versionCode =
            Regex("(?m)^\\s*versionCode:\\s*(\\d+)\\s*$")
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull() ?: return null
        val asset =
            root["assets"]
                ?.jsonArray
                ?.firstOrNull {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true
                }
                ?.jsonObject ?: return null
        val apkUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
        val digest =
            asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")?.takeIf {
                it.isNotBlank()
            }
        return NewVersion(
            versionName = tag.removePrefix("v"),
            versionCode = versionCode,
            releaseTag = tag,
            releaseNotes = body.takeIf { it.isNotBlank() },
            apkUrl = apkUrl,
            apkSha256 = digest,
            apkSize = size,
        )
    }

    suspend fun download(
        url: String,
        file: File,
        size: Long,
        expectedSha256: String?,
        progress: (Float) -> Unit,
    ) {
        NetworkClients.client
            .prepareGet(url) {
                expectSuccess = true
                onDownload { received, _ -> if (size > 0) progress(received.toFloat() / size) }
            }
            .execute { response -> response.bodyAsChannel().copyAndClose(file.writeChannel()) }
        val expected = expectedSha256?.takeIf { it.isNotBlank() }
        if (expected != null) {
            val actual = file.sha256()
            if (!actual.equals(expected, ignoreCase = true)) {
                file.delete()
                throw IllegalStateException(
                    "update checksum mismatch: expected $expectedSha256, got $actual",
                )
            }
        }
    }

    /**
     * v8.0.9：下载镜像链——首源失败按序回退（github.com Release 资产墙内可达性 不稳定；gcore CDN 仓库 dist 归档与 Release
     * 资产同字节同源），每次尝试 都走同一 SHA-256 校验；全部失败抛最后一次异常（已删档）。
     */
    suspend fun downloadFirstAvailable(
        urls: List<String>,
        file: File,
        size: Long,
        expectedSha256: String?,
        progress: (Float) -> Unit,
    ) =
        downloadFirstAvailableWith(urls, file, size, expectedSha256, progress) { operation, e ->
            logNetworkFailure(operation, e)
        }

    /** 独立可测实现：日志通道注入（不耦合存储生命周期）。 */
    internal suspend fun downloadFirstAvailableWith(
        urls: List<String>,
        file: File,
        size: Long,
        expectedSha256: String?,
        progress: (Float) -> Unit,
        logFailure: (String, Exception) -> Unit,
    ) {
        require(urls.isNotEmpty()) { "no download url provided" }
        var lastError: Exception? = null
        for (url in urls) {
            try {
                download(url, file, size, expectedSha256, progress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                logFailure("Update download source failed: $url", e)
            }
        }
        throw lastError ?: IllegalStateException("no download source available")
    }
}

private fun File.sha256(): String {
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
