package li.gkd.app.network

import kotlinx.serialization.Serializable

/**
 * Update manifest (update/latest.json) maintained by the update-manifest workflow of the source
 * repository. Field layout follows the Skip-Ad Guardian manifest schema.
 */
@Serializable
data class NewVersion(
    val versionName: String = "",
    val versionCode: Int = 0,
    val releaseTag: String? = null,
    val releaseNotes: String? = null,
    val apkUrl: String = "",
    val apkSha256: String? = null,
    val apkSize: Long = 0,
    val packageName: String? = null,
) {
    /**
     * Guards against broken or poisoned manifests before anything is offered to the user:
     * positive version code, non-empty name, HTTPS download URL, and matching package identity.
     */
    fun validated(packageName: String): NewVersion {
        require(versionCode > 0) { "invalid versionCode: $versionCode" }
        require(versionName.isNotEmpty()) { "missing versionName" }
        require(apkUrl.startsWith("https://")) { "apkUrl is not https: $apkUrl" }
        if (!this.packageName.isNullOrEmpty() && this.packageName != packageName) {
            throw IllegalArgumentException("manifest package mismatch: ${this.packageName}")
        }
        return this
    }
}
