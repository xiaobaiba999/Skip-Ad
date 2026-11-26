package li.gkd.app.network

import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import li.gkd.app.META

object UpdateClient {
    // 更新清单拉源：官方 Skip-Ad 主源（jsdelivr→gcore→raw）+ kilo 历史仓回退（8.0.x 兼容）。
    private val manifestSources =
        listOf(
            AppLinks.StableUpdate,
            AppLinks.StableUpdateGcore,
            AppLinks.StableUpdateRaw,
            AppLinks.StableUpdateLegacyRaw,
            AppLinks.StableUpdateLegacy,
        )

    /**
     * 官方 Skip-Ad 主源（jsdelivr→gcore→raw）+ kilo 历史仓回退（8.0.x 兼容）。
     * Throws the last failure when every source is unreachable.
     */
    suspend fun fetchUpdate(): NewVersion {
        var lastError: Exception? = null
        for (source in manifestSources) {
            try {
                val manifest =
                    NetworkClients.client.get(source) { expectSuccess = true }.body<NewVersion>()
                return manifest.validated(META.appId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                logNetworkFailure("Update manifest fetch failed: $source", e)
            }
        }
        throw lastError ?: IllegalStateException("no update source configured")
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
