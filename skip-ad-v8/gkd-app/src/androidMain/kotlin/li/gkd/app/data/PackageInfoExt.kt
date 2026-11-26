package li.gkd.app.data

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import li.gkd.app.app
import li.gkd.app.model.AppInfo
import li.gkd.app.priv.currentUserId
import li.gkd.app.priv.toHidden
import li.gkd.app.util.AndroidTarget

private val PackageInfo.compatVersionCode: Int
    get() =
        if (AndroidTarget.P) {
            longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") versionCode
        }

private fun PackageInfo.checkHasActivity(): Boolean {
    return app.packageManager.getLaunchIntentForPackage(packageName) != null ||
        app.packageManager
            .queryIntentActivities(
                Intent().setPackage(packageName),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
            .isNotEmpty() ||
        try {
            app.packageManager
                .getPackageInfo(
                    packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_ACTIVITIES,
                )
                .activities
                ?.isNotEmpty() == true
        } catch (_: Throwable) {
            // #1195 packageManager.getPackageInfo android.os.DeadSystemRuntimeException
            true
        }
}

// all->433 isOverlay->354 checkAppHasActivity->271
fun PackageInfo.toAppInfo(
    userId: Int = currentUserId,
    hidden: Boolean? = null,
): AppInfo {
    val isSystem = applicationInfo?.isSystem ?: false
    return AppInfo(
        userId = userId,
        id = packageName,
        versionCode = compatVersionCode,
        versionName = versionName,
        mtime = lastUpdateTime,
        isSystem = isSystem,
        // 8.0.6 修复：单个应用 label 解析失败（坏资源/厂商定制 ApplicationInfo）绝不
        // 连带整个应用列表刷新失败——安全回退包名，白名单页保持可用
        name = safeLabel() ?: packageName,
        hidden = hidden ?: (isSystem && (toHidden.overlayTarget != null || !checkHasActivity())),
    )
}

private fun PackageInfo.safeLabel(): String? =
    try {
        applicationInfo?.loadLabel(app.packageManager)?.toString()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }
