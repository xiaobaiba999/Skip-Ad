package li.gkd.app.network

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import java.util.regex.Pattern
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.error.displayMessage

/**
 * Creates feedback issues in the project repository, ported from the Skip-Ad Guardian client.
 *
 * The built-in credential is stored obfuscated (double XOR+Base64) so that it never appears in
 * plaintext in the APK or logs. Report text is sanitized before submission; failures return a
 * browser prefill link so the user can still submit manually.
 */
object FeedbackUploader {
    private const val IssuesApi = "https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues"
    private const val IssuesPage = "https://github.com/xiaobaiba999/Skip-Ad/issues"

    private const val L2 = "BSg9CSQRIDkgIAEKCT8dJi9XGiU0NCoLBhsUCCIiJAMjNiEOKAQ6NTEhMwEnCl0cHwIzEjQTdlQ="
    private val keyB = "KipShield"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var lastRequestAt = 0L

    sealed interface Result {
        data class Success(val url: String, val number: Int) : Result

        data class Failure(val message: String, val fallbackUrl: String?) : Result
    }

    /** Decodes the built-in token in memory only; never persisted or logged. */
    internal fun builtInToken(): String =
        try {
            val layer1 = xorWith(b64Decode(L2), keyB)
            val token = xorWith(b64Decode(layer1), "SkipAd::2026::obf::k1")
            if (token.length >= 20 && token.all { it.isLetterOrDigit() || it == '_' }) token else ""
        } catch (e: Exception) {
            ""
        }

    private fun b64Decode(value: String) =
        String(java.util.Base64.getDecoder().decode(value), Charsets.UTF_8)

    private fun xorWith(input: String, key: String) =
        buildString {
            input.forEachIndexed { index, c -> append((c.code xor key[index % key.length].code).toChar()) }
        }

    /**
     * Masks sensitive text before submission: phone numbers, ID numbers, emails, and IPv4
     * addresses keep only a safe prefix. Debug information such as package names or view ids
     * is untouched. Identical rules run on both ends of the original Guardian chain.
     */
    fun maskSensitive(text: String): String {
        var out = text
        out = phoneMask.matcher(out).replaceAll("$1****$2")
        out = idMask.matcher(out).replaceAll("$1************$2")
        out = emailMask.matcher(out).replaceAll("***@$1")
        out = ipv4Mask.matcher(out).replaceAll("$1.*.*")
        return out
    }

    private val phoneMask =
        Pattern.compile("(?<![0-9])(1[3-9][0-9])[0-9]{4}([0-9]{4})(?![0-9])")
    private val idMask =
        Pattern.compile("(?<![0-9A-Za-z])([0-9]{3})[0-9]{12}([0-9]{3}[0-9Xx])(?![0-9A-Za-z])")
    private val emailMask = Pattern.compile("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})")
    private val ipv4Mask = Pattern.compile("(?<![0-9])(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}(?![0-9])")

    private fun fallbackUrl(title: String, body: String): String {
        val encodedTitle = java.net.URLEncoder.encode(title, Charsets.UTF_8.name())
        val encodedBody = java.net.URLEncoder.encode(body, Charsets.UTF_8.name())
        return "$IssuesPage/new?title=$encodedTitle&body=$encodedBody"
    }

    /**
     * Creates an issue. Transient network errors retry once; HTTP business errors never retry.
     * A 15 second client-side rate gate protects against accidental duplicate submissions.
     */
    suspend fun createIssue(rawTitle: String, rawBody: String): Result {
        val title = maskSensitive(rawTitle).take(128)
        val body = maskSensitive(rawBody).take(24000)
        val token = builtInToken()
        if (token.isEmpty()) {
            return Result.Failure("内置令牌不可用", fallbackUrl(title, body))
        }
        val now = System.currentTimeMillis()
        if (now - lastRequestAt < 15_000L) {
            return Result.Failure("提交过于频繁，请稍候重试", fallbackUrl(title, body))
        }
        lastRequestAt = now
        var networkError: Exception? = null
        for (attempt in 1..2) {
            try {
                val response =
                    NetworkClients.client.post(IssuesApi) {
                        header("Accept", "application/vnd.github+json")
                        header("Authorization", "Bearer $token")
                        header("User-Agent", "adshield-android")
                        expectSuccess = false
                        setBody(json.encodeToString(IssuePayload.serializer(), IssuePayload(title, body)))
                    }
                val status = response.status.value
                val text = response.bodyAsText()
                if (status in 200..299) {
                    val data = json.parseToJsonElement(text).jsonObject
                    val url = data["html_url"]?.jsonPrimitive?.content.orEmpty()
                    val number = data["number"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    return Result.Success(url, number)
                }
                return when (status) {
                    401 -> Result.Failure("内置令牌无效（401）", fallbackUrl(title, body))
                    403 -> Result.Failure("触发限流或权限不足（403）", fallbackUrl(title, body))
                    else -> Result.Failure("GitHub 返回 $status", fallbackUrl(title, body))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                networkError = e
                if (attempt == 1) delay(600)
            }
        }
        return Result.Failure("网络异常：${networkError?.displayMessage()}", fallbackUrl(title, body))
    }
}

@kotlinx.serialization.Serializable
private data class IssuePayload(val title: String, val body: String)
