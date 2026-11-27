package li.gkd.app.platform

import java.io.File
import kotlinx.coroutines.flow.Flow
import li.gkd.app.ui.platform.UiHost

sealed interface PackageInstallCapability {
    data object Checking : PackageInstallCapability

    data object Available : PackageInstallCapability

    data object Unavailable : PackageInstallCapability

    data class Failure(val exception: Exception) : PackageInstallCapability
}

expect fun privilegedPackageInstallCapability(): Flow<PackageInstallCapability>

/** Success means the system reported installation success, including any required confirmation. */
expect suspend fun installPackagePrivileged(
    file: File,
    versionCode: Int,
    host: UiHost,
): PlatformResult<Unit>

/**
 * v8.0.9：安装前签名预校验——APK 签名证书必须与当前运行版一致。
 *
 * 系统安装器最终也会拒绝不一致的包，但提前明确报错优于晦涩的系统级失败；
 * 签名不一致抛 [SecurityException]（调用方删档并提示重新检查更新），
 * 读取失败等操作性异常原样抛出。桌面端无安装契约，恒成功返回。
 */
expect suspend fun verifyApkSignature(file: File)
