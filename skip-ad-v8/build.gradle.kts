import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import li.gkd.gradle.gitInfo
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.cmp) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.remap) apply false
    alias(libs.plugins.codeorigin) apply false
    alias(libs.plugins.littlerobots.version)
}

// 8.0.x 系列自 8.0.7 起 versionCode 跨入 87xxx 段（显式抬码）：旧版 7.15.x 存量
// versionCode 为 85000-86400，旧客户端「检查更新」只按 versionCode 数值比较，
// 80700 < 86400 将导致 v7.15.x 永远发现不了新版本且系统拒绝降级覆盖安装。
// 87100 = 首个高于历史最大 versionCode(86400) 的兼容码；versionName 与
// versionCode 的 7.x 公式(major*10000+minor*1000+patch*100)自 8.0.7 起解耦，
// 发布链(update_manifest.py --version-code)按显式码校验。
// v8.0.9：更新系统根治轮（2026-11-27 事故：v8.0.7 用户检测不到 v8.0.8——
// 多源解析恢复 7.15.x 传承的「多源合流取最大 versionCode + @tag 不可变键
// 探测链 + GitHub API 兜底」架构，单一 CDN 陈旧缓存不再能掩盖新版本）。
// v8.1.0：规则覆盖与诊断轮——内置 667 订阅截断根治（Lin-arm v646 全量）+
// 失败诊断日志体系（13 类分类链）+ 卫士进出残影根治 + 液态柔光玻璃提示重制。
// v8.1.1：日志准确性 + 更新下载链路轮——「已跳过却记为未命中」根治（eventId
// 状态机 + 抑制闸）+ 断点续传多源下载（Range/ETag/停滞闸/失败分类/富进度）+
// 意见反馈双入口（底栏反馈页 + 开屏提示反馈小按钮）+ 玻璃材质五层升级。
extra["gkdVersionName"] = "8.1.1" + project.gitInfo.versionNameSuffix.orEmpty()

extra["gkdVersionCode"] = 88300

object Cfg {
    val compileSdk
        get() = 37

    val buildToolsVersion
        get() = "37.0.0"

    val minSdk
        get() = 26

    val targetSdk
        get() = compileSdk

    val sourceVersion = JavaVersion.VERSION_11
    val targetVersion
        get() = sourceVersion

    val kotlinTargetVersion
        get() = JvmTarget.fromTarget(targetVersion.majorVersion)

    // 统一应用于所有子项目；未依赖对应库的模块允许出现 unresolved opt-in marker 警告。
    val kotlinCompilerArgs =
        listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-XXLanguage:+ExplicitBackingFields",
            "-Xexpect-actual-classes",
        )
}

val androidKmpLibraryPluginId = libs.plugins.android.kotlin.multiplatform.library.get().pluginId
val composeCompilerPluginId = libs.plugins.kotlin.compose.get().pluginId

allprojects {
    plugins.withType<NodeJsPlugin> {
        extensions.configure<NodeJsEnvSpec> {
            download.set(false)
        }
    }
}

subprojects {
    plugins.withId(composeCompilerPluginId) {
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            if (providers.gradleProperty("composeReports").isPresent) {
                reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
            }
            stabilityConfigurationFiles.add(
                rootProject.layout.projectDirectory.file("stability_config.conf"),
            )
        }
    }
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(Cfg.kotlinCompilerArgs)
        }
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(Cfg.kotlinTargetVersion)
        }
    }
    plugins.withType<AppPlugin> {
        extensions.getByType(ApplicationExtension::class.java).apply {
            compileSdk = Cfg.compileSdk
            buildToolsVersion = Cfg.buildToolsVersion
            defaultConfig {
                minSdk = Cfg.minSdk
                targetSdk = Cfg.targetSdk
            }
            compileOptions {
                sourceCompatibility = Cfg.sourceVersion
                targetCompatibility = Cfg.targetVersion
            }
        }
    }
    plugins.withType<LibraryPlugin> {
        extensions.getByType(LibraryExtension::class.java).apply {
            compileSdk = Cfg.compileSdk
            buildToolsVersion = Cfg.buildToolsVersion
            defaultConfig {
                minSdk = Cfg.minSdk
            }
            compileOptions {
                sourceCompatibility = Cfg.sourceVersion
                targetCompatibility = Cfg.targetVersion
            }
        }
    }
    plugins.withId(androidKmpLibraryPluginId) {
        extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .withType(
                KotlinMultiplatformAndroidLibraryTarget::class.java,
            )
            .configureEach {
                compileSdk = Cfg.compileSdk
                buildToolsVersion = Cfg.buildToolsVersion
                minSdk = Cfg.minSdk
            }
    }
}
